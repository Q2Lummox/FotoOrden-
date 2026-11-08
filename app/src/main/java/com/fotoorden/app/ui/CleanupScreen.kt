package com.fotoorden.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fotoorden.app.GalleryViewModel
import com.fotoorden.app.analysis.Classifier
import com.fotoorden.app.analysis.ImageMath
import com.fotoorden.app.data.Category
import com.fotoorden.app.data.Photo
import com.fotoorden.app.data.PhotoInfo
import com.fotoorden.app.data.PhotoSection

enum class CleanupKind(val title: String) {
    DUPLICADAS("Fotos repetidas"),
    BORROSAS("Borrosas o vacías"),
    CAPTURAS("Capturas de pantalla"),
    WHATSAPP("Imágenes de WhatsApp"),
    DOCUMENTOS("Documentos y papeles"),
}

fun blurryPhotos(photos: List<Photo>, infos: Map<Long, PhotoInfo>, threshold: Float): List<Photo> =
    photos.filter { p ->
        val i = infos[p.id] ?: return@filter false
        i.sharpness in 0f..threshold &&
            i.category != Category.CAPTURAS && i.category != Category.DOCUMENTOS
    }

fun screenshotPhotos(photos: List<Photo>, infos: Map<Long, PhotoInfo>): List<Photo> =
    photos.filter { infos[it.id]?.category == Category.CAPTURAS || Classifier.isScreenshot(it) }

fun whatsappPhotos(photos: List<Photo>): List<Photo> =
    photos.filter { "whatsapp" in it.relativePath.lowercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(vm: GalleryViewModel, onOpen: (CleanupKind) -> Unit) {
    val ctx = LocalContext.current
    val photos by vm.photos.collectAsStateWithLifecycle()
    val infos by vm.infos.collectAsStateWithLifecycle()
    val groups by vm.duplicateGroups.collectAsStateWithLifecycle()
    val threshold by vm.blurThreshold.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()

    val dupExtra = remember(groups, infos) {
        groups.flatMap { g -> val best = ImageMath.bestOf(g, infos); g.filter { it.id != best.id } }
    }
    val blurry = remember(photos, infos, threshold) { blurryPhotos(photos, infos, threshold) }
    val shots = remember(photos, infos) { screenshotPhotos(photos, infos) }
    val wa = remember(photos) { whatsappPhotos(photos) }
    val docs = remember(photos, infos) { photos.filter { infos[it.id]?.category == Category.DOCUMENTOS } }
    val total = remember(photos) { photos.sumOf { it.size } }

    var canManage by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        canManage = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || MediaStore.canManageMedia(ctx)
        onPauseOrDispose { }
    }

    fun sz(list: List<Photo>) = formatSize(ctx, list.sumOf { it.size })

    Scaffold(topBar = { TopAppBar(title = { Text("Limpieza") }) }) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    "${photos.size} fotos ocupan ${formatSize(ctx, total)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            progress?.let { p -> item { AnalysisBanner(p) } }

            item {
                CleanupCard(
                    icon = Icons.Outlined.ContentCopy,
                    title = CleanupKind.DUPLICADAS.title,
                    value = "${dupExtra.size} de sobra · ${sz(dupExtra)}",
                    detail = "${groups.size} grupos de fotos casi iguales (ráfagas, repetidas). " +
                        "Te dejamos marcada para borrar todas menos la mejor de cada grupo.",
                ) { onOpen(CleanupKind.DUPLICADAS) }
            }
            item {
                CleanupCard(
                    icon = Icons.Outlined.BlurOn,
                    title = CleanupKind.BORROSAS.title,
                    value = "${blurry.size} fotos · ${sz(blurry)}",
                    detail = "Movidas, desenfocadas o fotos del bolsillo.",
                    extra = { BlurSlider(threshold) { vm.blurThreshold.value = it } },
                    onClick = { onOpen(CleanupKind.BORROSAS) },
                )
            }
            item {
                CleanupCard(
                    icon = Icons.Outlined.Screenshot,
                    title = CleanupKind.CAPTURAS.title,
                    value = "${shots.size} capturas · ${sz(shots)}",
                    detail = "Suelen ser lo primero que sobra.",
                ) { onOpen(CleanupKind.CAPTURAS) }
            }
            if (wa.isNotEmpty()) item {
                CleanupCard(
                    icon = Icons.Outlined.Forum,
                    title = CleanupKind.WHATSAPP.title,
                    value = "${wa.size} imágenes · ${sz(wa)}",
                    detail = "Memes, cadenas y fotos recibidas que se guardan solas.",
                ) { onOpen(CleanupKind.WHATSAPP) }
            }
            item {
                CleanupCard(
                    icon = Icons.Outlined.Description,
                    title = CleanupKind.DOCUMENTOS.title,
                    value = "${docs.size} fotos · ${sz(docs)}",
                    detail = "Tickets, papeles, apuntes, fotos de pantallas… Útil para pasar a un álbum o borrar.",
                ) { onOpen(CleanupKind.DOCUMENTOS) }
            }
            if (!canManage) item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.FlashOn, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Modo rápido", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Android pide confirmación cada vez que la app mueve o borra fotos. " +
                                "Si le das este permiso, se hará directamente (lo borrado sigue yendo a la papelera).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                ctx.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_MANAGE_MEDIA,
                                        Uri.parse("package:${ctx.packageName}")
                                    )
                                )
                            }
                        }) { Text("Activar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanupCard(
    icon: ImageVector,
    title: String,
    value: String,
    detail: String,
    extra: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                extra?.invoke()
            }
        }
    }
}

@Composable
fun BlurSlider(value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Menos estricto", style = MaterialTheme.typography.labelSmall)
            Text("Más estricto", style = MaterialTheme.typography.labelSmall)
        }
        Slider(value = value, onValueChange = onChange, valueRange = 10f..200f)
    }
}

/** Lista de fotos de una categoría de limpieza, lista para seleccionar y borrar. */
@Composable
fun CleanupListScreen(vm: GalleryViewModel, kind: CleanupKind, onBack: () -> Unit) {
    val photos by vm.photos.collectAsStateWithLifecycle()
    val infos by vm.infos.collectAsStateWithLifecycle()
    val groups by vm.duplicateGroups.collectAsStateWithLifecycle()
    val threshold by vm.blurThreshold.collectAsStateWithLifecycle()

    when (kind) {
        CleanupKind.DUPLICADAS -> {
            val bestIds = remember(groups, infos) { groups.mapTo(HashSet()) { ImageMath.bestOf(it, infos).id } }
            val all = remember(groups) { groups.flatten() }
            val sections = remember(groups) {
                groups.mapIndexed { i, g -> PhotoSection("g${g.first().id}", "Grupo ${i + 1} · ${g.size} fotos", g) }
            }
            val initial = remember(all, bestIds) { all.map { it.id }.filterTo(HashSet()) { it !in bestIds } }
            PhotoBrowser(
                vm = vm,
                title = kind.title,
                photos = all,
                onBack = onBack,
                showCategoryFilter = false,
                customSections = sections,
                initialSelection = initial,
                badge = { if (it.id in bestIds) "Mejor" else null },
                emptyText = "No hay fotos repetidas. ¡Bien!",
                top = { Hint("Marcadas: todas menos la mejor de cada grupo. Toca una foto para cambiarlo y pulsa Borrar.") },
            )
        }
        CleanupKind.BORROSAS -> {
            val list = remember(photos, infos, threshold) { blurryPhotos(photos, infos, threshold) }
            PhotoBrowser(
                vm = vm, title = kind.title, photos = list, onBack = onBack,
                showCategoryFilter = false,
                emptyText = "No hay fotos borrosas con este nivel",
                top = {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        BlurSlider(threshold) { vm.blurThreshold.value = it }
                    }
                },
            )
        }
        CleanupKind.CAPTURAS -> {
            val list = remember(photos, infos) { screenshotPhotos(photos, infos) }
            PhotoBrowser(vm, kind.title, list, onBack = onBack, showCategoryFilter = false,
                emptyText = "No hay capturas de pantalla")
        }
        CleanupKind.WHATSAPP -> {
            val list = remember(photos) { whatsappPhotos(photos) }
            PhotoBrowser(vm, kind.title, list, onBack = onBack, emptyText = "No hay imágenes de WhatsApp")
        }
        CleanupKind.DOCUMENTOS -> {
            val list = remember(photos, infos) { photos.filter { infos[it.id]?.category == Category.DOCUMENTOS } }
            PhotoBrowser(vm, kind.title, list, onBack = onBack, showCategoryFilter = false,
                emptyText = "No hay documentos")
        }
    }
}

@Composable
fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
