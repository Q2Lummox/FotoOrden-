package com.fotoorden.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fotoorden.app.GalleryViewModel
import com.fotoorden.app.data.Album
import com.fotoorden.app.data.Category
import com.fotoorden.app.data.Photo
import com.fotoorden.app.data.PhotoInfo
import com.fotoorden.app.data.PhotoSection

/**
 * Pantalla genérica para ver una lista de fotos con:
 * filtro por categoría, agrupación por meses, selección múltiple
 * (pulsación larga), y acciones en bloque: mover a álbum, compartir, borrar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoBrowser(
    vm: GalleryViewModel,
    title: String,
    photos: List<Photo>,
    onBack: (() -> Unit)? = null,
    showCategoryFilter: Boolean = true,
    customSections: List<PhotoSection>? = null,
    initialSelection: Set<Long> = emptySet(),
    badge: (Photo) -> String? = { null },
    pickForAlbum: String? = null,
    onPickFinished: () -> Unit = {},
    emptyText: String = "No hay fotos aquí",
    top: @Composable () -> Unit = {},
) {
    val infos by vm.infos.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var filter by rememberSaveable { mutableStateOf<Category?>(null) }
    var selected by remember(initialSelection) { mutableStateOf(initialSelection) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var showMove by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Si alguna foto seleccionada desaparece (borrada, movida fuera), se quita de la selección
    LaunchedEffect(photos) {
        val ids = photos.mapTo(HashSet()) { it.id }
        if (selected.any { it !in ids }) selected = selected.filterTo(HashSet()) { it in ids }
    }

    val counts: Map<Category?, Int> = remember(photos, infos) {
        val m = HashMap<Category?, Int>()
        m[null] = photos.size
        for (p in photos) infos[p.id]?.category?.let { m[it] = (m[it] ?: 0) + 1 }
        m
    }
    val filtered = remember(photos, infos, filter) {
        if (filter == null) photos else photos.filter { infos[it.id]?.category == filter }
    }
    val sections = remember(filtered, customSections) {
        if (customSections != null) {
            val ids = filtered.mapTo(HashSet()) { it.id }
            customSections
                .map { s -> s.copy(photos = s.photos.filter { it.id in ids }) }
                .filter { it.photos.isNotEmpty() }
        } else monthSections(filtered)
    }
    val flat = remember(sections) { sections.flatMap { it.photos } }
    val selectedPhotos = remember(selected, photos) { photos.filter { it.id in selected } }
    val selectionMode = selected.isNotEmpty() || pickForAlbum != null

    fun toggle(p: Photo) {
        selected = if (p.id in selected) selected - p.id else selected + p.id
    }

    BackHandler(enabled = selectionMode) {
        if (selected.isNotEmpty()) selected = emptySet() else onPickFinished()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when {
                                pickForAlbum != null && selected.isEmpty() -> "Elige fotos para «$pickForAlbum»"
                                selectionMode -> "${selected.size} seleccionada${if (selected.size == 1) "" else "s"}"
                                else -> title
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selected.isNotEmpty()) {
                            Text(
                                formatSize(ctx, selectedPhotos.sumOf { it.size }),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            selected = emptySet()
                            if (pickForAlbum != null) onPickFinished()
                        }) { Icon(Icons.Filled.Close, "Cancelar") }
                    } else if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                    }
                },
                actions = {
                    if (flat.isNotEmpty()) {
                        val allSelected = flat.all { it.id in selected }
                        IconButton(onClick = {
                            val ids = flat.map { it.id }
                            selected = if (allSelected) selected - ids.toSet() else selected + ids
                        }) {
                            Icon(
                                if (allSelected) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                                if (allSelected) "Quitar selección" else "Seleccionar todas",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (pickForAlbum != null) {
                BottomAppBar {
                    Button(
                        onClick = {
                            vm.moveToNewAlbum(selectedPhotos, pickForAlbum)
                            selected = emptySet()
                            onPickFinished()
                        },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Text("Crear «$pickForAlbum» con ${selected.size} fotos", maxLines = 1)
                    }
                }
            } else if (selected.isNotEmpty()) {
                BottomAppBar {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        BarAction(Icons.Outlined.PhotoAlbum, "Mover a álbum") { showMove = true }
                        BarAction(Icons.Outlined.Share, "Compartir") {
                            if (selectedPhotos.size > 100) vm.say("Se comparten solo las 100 primeras")
                            sharePhotos(ctx, selectedPhotos)
                        }
                        BarAction(Icons.Outlined.Delete, "Borrar") { confirmDelete = true }
                    }
                }
            }
        },
    ) { inner ->
        Column(Modifier.padding(inner)) {
            if (photos.isEmpty()) {
                top()
                EmptyState(emptyText)
            } else {
                PhotoGrid(
                    sections = sections,
                    selected = selected,
                    selectionMode = selectionMode,
                    onTap = { p -> if (selectionMode) toggle(p) else viewerIndex = flat.indexOf(p) },
                    onLongPress = { p -> toggle(p) },
                    onToggleSection = { s ->
                        val ids = s.photos.map { it.id }
                        selected = if (ids.all { it in selected }) selected - ids.toSet() else selected + ids
                    },
                    badge = badge,
                    header = {
                        item(key = "top", span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                top()
                                if (showCategoryFilter && counts.size > 1) {
                                    Spacer(Modifier.height(4.dp))
                                    CategoryChips(counts, filter) { filter = it }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    viewerIndex?.let { idx ->
        if (idx in flat.indices) {
            PhotoViewer(
                photos = flat,
                start = idx,
                infos = infos,
                onDismiss = { viewerIndex = null },
                onShare = { sharePhotos(ctx, listOf(it)) },
                onMove = {
                    viewerIndex = null
                    selected = setOf(it.id)
                    showMove = true
                },
                onDelete = {
                    viewerIndex = null
                    vm.trash(listOf(it))
                },
            )
        }
    }

    if (showMove) {
        MoveToAlbumDialog(
            albums = albums.filter { it.canReceive },
            count = selected.size,
            onDismiss = { showMove = false },
            onPick = { album ->
                showMove = false
                vm.moveTo(selectedPhotos, album.relativePath, album.name)
                selected = emptySet()
            },
            onNew = { name ->
                showMove = false
                vm.moveToNewAlbum(selectedPhotos, name)
                selected = emptySet()
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar ${selected.size} foto${if (selected.size == 1) "" else "s"}?") },
            text = {
                Text(
                    "Irán a la papelera y liberarás ${formatSize(ctx, selectedPhotos.sumOf { it.size })}. " +
                        "Podrás recuperarlas durante 30 días desde la pestaña Papelera."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.trash(selectedPhotos)
                    selected = emptySet()
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun MoveToAlbumDialog(
    albums: List<Album>,
    count: Int,
    onDismiss: () -> Unit,
    onPick: (Album) -> Unit,
    onNew: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover $count foto${if (count == 1) "" else "s"} a…") },
        text = {
            Column {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Álbum nuevo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(enabled = newName.isNotBlank(), onClick = { onNew(newName) }) {
                            Icon(Icons.Filled.Check, "Crear")
                        }
                    },
                )
                if (albums.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("O elige uno que ya existe:", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(albums, key = { it.relativePath }) { a ->
                            ListItem(
                                headlineContent = { Text(a.name) },
                                supportingContent = { Text("${a.count} fotos") },
                                leadingContent = {
                                    AsyncImage(
                                        model = a.cover.uri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                                    )
                                },
                                modifier = Modifier.clickable { onPick(a) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = newName.isNotBlank(), onClick = { onNew(newName) }) { Text("Crear álbum") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Visor a pantalla completa: deslizar para cambiar de foto, pellizcar o doble toque para ampliar. */
@Composable
fun PhotoViewer(
    photos: List<Photo>,
    start: Int,
    infos: Map<Long, PhotoInfo>,
    onDismiss: () -> Unit,
    onShare: (Photo) -> Unit,
    onMove: (Photo) -> Unit,
    onDelete: (Photo) -> Unit,
) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = start) { photos.size }
        var zoomed by remember { mutableStateOf(false) }
        val current = photos.getOrNull(pager.currentPage)

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !zoomed,
                key = { photos[it].id },
            ) { page ->
                ZoomableImage(photos[page], onZoomChange = { zoomed = it })
            }

            if (current != null) {
                val cat = infos[current.id]?.category?.label
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .statusBarsPadding()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Cerrar", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(formatDate(current.dateTaken), color = Color.White, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(current.bucket, formatSize(ctx, current.size), cat).joinToString(" · "),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .navigationBarsPadding()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ViewerAction(Icons.Outlined.Share, "Compartir") { onShare(current) }
                    ViewerAction(Icons.Outlined.PhotoAlbum, "Mover") { onMove(current) }
                    ViewerAction(Icons.Outlined.Delete, "Borrar") { onDelete(current) }
                }
            }
        }
    }
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Color.White)
            Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ZoomableImage(photo: Photo, onZoomChange: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(scale > 1f) { onZoomChange(scale > 1f) }

    AsyncImage(
        model = photo.uri,
        contentDescription = photo.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        // Solo gestionamos el gesto si hay dos dedos o ya está ampliada;
                        // si no, se deja pasar para deslizar entre fotos.
                        if (event.changes.size >= 2 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + event.calculatePan() else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
