package com.fotoorden.app

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fotoorden.app.analysis.ImageMath
import com.fotoorden.app.analysis.PhotoAnalyzer
import com.fotoorden.app.data.Album
import com.fotoorden.app.data.AnalysisCache
import com.fotoorden.app.data.AnalysisProgress
import com.fotoorden.app.data.Photo
import com.fotoorden.app.data.PhotoInfo
import com.fotoorden.app.data.PhotoRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Petición del sistema que el usuario debe aprobar (mover / borrar fotos). */
class ApprovalRequest(val sender: IntentSender, val result: CompletableDeferred<Boolean>) {
    var launched = false
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PhotoRepository(app)
    private val cache = AnalysisCache(app)
    private val analyzer = PhotoAnalyzer(app)

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _infos = MutableStateFlow<Map<Long, PhotoInfo>>(emptyMap())
    val infos: StateFlow<Map<Long, PhotoInfo>> = _infos.asStateFlow()

    private val _progress = MutableStateFlow<AnalysisProgress?>(null)
    val progress: StateFlow<AnalysisProgress?> = _progress.asStateFlow()

    private val _trash = MutableStateFlow<List<Photo>>(emptyList())
    val trash: StateFlow<List<Photo>> = _trash.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _approval = MutableStateFlow<ApprovalRequest?>(null)
    val approval: StateFlow<ApprovalRequest?> = _approval.asStateFlow()

    /** Umbral de nitidez por debajo del cual una foto se considera borrosa. */
    val blurThreshold = MutableStateFlow(50f)

    /** Si no es null, el usuario está eligiendo fotos para un álbum nuevo con este nombre. */
    val pickForAlbum = MutableStateFlow<String?>(null)

    val albums: StateFlow<List<Album>> = _photos
        .map { list ->
            list.groupBy { it.relativePath }
                .map { (path, ps) -> Album(ps.first().bucket, path, ps) }
                .sortedByDescending { it.cover.dateTaken }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val duplicateGroups: StateFlow<List<List<Photo>>> = combine(_photos, _infos) { p, i -> p to i }
        .debounce(1500)
        .mapLatest { (p, i) -> ImageMath.findDuplicateGroups(p, i) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var analysisJob: Job? = null
    private val cachedInfos = viewModelScope.async(Dispatchers.IO) { cache.load() }
    private var cacheLoaded = false
    private val flushMutex = Mutex()

    // ---------------------------------------------------------------- carga

    fun refresh() {
        viewModelScope.launch {
            val cached = cachedInfos.await()
            if (!cacheLoaded) {
                cacheLoaded = true
                _infos.value = cached
            }
            _photos.value = withContext(Dispatchers.IO) { repo.loadPhotos() }
            _loaded.value = true
            startAnalysisIfNeeded()
        }
    }

    fun loadTrash() {
        viewModelScope.launch {
            _trash.value = withContext(Dispatchers.IO) { repo.loadTrash() }
        }
    }

    // ------------------------------------------------------------- análisis

    fun startAnalysisIfNeeded() {
        if (analysisJob?.isActive == true) return
        val known = _infos.value
        val pending = _photos.value.filter { it.id !in known }
        if (pending.isEmpty()) return

        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            val total = pending.size
            val done = AtomicInteger(0)
            val buffer = ConcurrentHashMap<Long, PhotoInfo>()
            val permits = Semaphore(3)
            _progress.value = AnalysisProgress(0, total)

            coroutineScope {
                for (photo in pending) {
                    launch {
                        permits.withPermit {
                            val info = try {
                                analyzer.analyze(photo)
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                PhotoInfo.FAILED
                            }
                            buffer[photo.id] = info
                            val d = done.incrementAndGet()
                            _progress.value = AnalysisProgress(d, total)
                            if (d % 24 == 0) flush(buffer)
                        }
                    }
                }
            }
            flush(buffer)
            _progress.value = null

            // Compacta la caché quitando fotos que ya no existen
            val ids = _photos.value.mapTo(HashSet()) { it.id }
            val current = _infos.value.filterKeys { it in ids }
            withContext(Dispatchers.IO) { cache.rewrite(current) }
        }
    }

    private suspend fun flush(buffer: ConcurrentHashMap<Long, PhotoInfo>) = flushMutex.withLock {
        if (buffer.isEmpty()) return@withLock
        val batch = HashMap<Long, PhotoInfo>()
        for (k in buffer.keys.toList()) buffer.remove(k)?.let { batch[k] = it }
        _infos.update { it + batch }
        withContext(Dispatchers.IO) { cache.append(batch) }
    }

    // ---------------------------------------------------------- aprobaciones

    private suspend fun requestApproval(sender: IntentSender): Boolean {
        val req = ApprovalRequest(sender, CompletableDeferred())
        _approval.value = req
        return req.result.await()
    }

    fun onApprovalResult(ok: Boolean) {
        val req = _approval.value ?: return
        _approval.value = null
        req.result.complete(ok)
    }

    // -------------------------------------------------------------- acciones

    fun moveTo(photos: List<Photo>, relativePath: String, albumName: String) {
        val toMove = photos.filter { it.relativePath != relativePath }
        if (toMove.isEmpty()) {
            say("Esas fotos ya están en «$albumName»")
            return
        }
        viewModelScope.launch {
            var moved = 0
            var failed = 0
            for (chunk in toMove.chunked(CHUNK)) {
                val ok = requestApproval(repo.writeRequest(chunk.map { it.uri }).intentSender)
                if (!ok) { say("Operación cancelada"); break }
                withContext(Dispatchers.IO) {
                    chunk.forEach { if (repo.move(it.uri, relativePath)) moved++ else failed++ }
                }
            }
            if (moved > 0) say("${plural(moved)} movida${if (moved == 1) "" else "s"} a «$albumName»")
            if (failed > 0) say("No se pudieron mover ${plural(failed)}")
            refresh()
        }
    }

    fun moveToNewAlbum(photos: List<Photo>, name: String) {
        val clean = PhotoRepository.sanitize(name)
        if (clean.isBlank()) { say("Escribe un nombre para el álbum"); return }
        moveTo(photos, PhotoRepository.newAlbumPath(clean), clean)
    }

    fun trash(photos: List<Photo>) {
        viewModelScope.launch {
            var count = 0
            for (chunk in photos.map { it.uri }.chunked(CHUNK)) {
                val ok = requestApproval(repo.trashRequest(chunk, true).intentSender)
                if (!ok) { say("Operación cancelada"); break }
                count += chunk.size
            }
            if (count > 0) say("${plural(count)} a la papelera. Puedes recuperarlas durante 30 días.")
            refresh()
            loadTrash()
        }
    }

    fun restore(photos: List<Photo>) {
        viewModelScope.launch {
            var count = 0
            for (chunk in photos.chunked(CHUNK)) {
                val ok = requestApproval(repo.trashRequest(chunk.map { it.uri }, false).intentSender)
                if (!ok) { say("Operación cancelada"); break }
                count += chunk.size
            }
            if (count > 0) say("${plural(count)} recuperada${if (count == 1) "" else "s"}")
            refresh()
            loadTrash()
        }
    }

    fun deleteForever(photos: List<Photo>) {
        viewModelScope.launch {
            var count = 0
            for (chunk in photos.chunked(CHUNK)) {
                val ok = requestApproval(repo.deleteRequest(chunk.map { it.uri }).intentSender)
                if (!ok) { say("Operación cancelada"); break }
                count += chunk.size
            }
            if (count > 0) say("${plural(count)} eliminada${if (count == 1) "" else "s"} para siempre")
            refresh()
            loadTrash()
        }
    }

    fun say(msg: String) {
        _messages.tryEmit(msg)
    }

    private fun plural(n: Int) = if (n == 1) "1 foto" else "$n fotos"

    companion object {
        private const val CHUNK = 500
    }
}
