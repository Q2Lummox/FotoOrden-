package com.fotoorden.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fotoorden.app.GalleryViewModel
import com.fotoorden.app.data.Photo
import com.fotoorden.app.data.PhotoSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(vm: GalleryViewModel) {
    val ctx = LocalContext.current
    val trash by vm.trash.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadTrash() }

    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var confirmForever by remember { mutableStateOf<List<Photo>?>(null) }
    LaunchedEffect(trash) {
        val ids = trash.mapTo(HashSet()) { it.id }
        selected = selected.filterTo(HashSet()) { it in ids }
    }
    val selectedPhotos = remember(selected, trash) { trash.filter { it.id in selected } }
    val sections = remember(trash) { listOf(PhotoSection("trash", null, trash)) }
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selected.isNotEmpty()) "${selected.size} seleccionadas"
                        else "Papelera · ${trash.size}"
                    )
                },
                navigationIcon = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Cancelar") }
                    }
                },
                actions = {
                    if (trash.isNotEmpty()) {
                        val all = selected.size == trash.size
                        IconButton(onClick = {
                            selected = if (all) emptySet() else trash.mapTo(HashSet()) { it.id }
                        }) {
                            Icon(if (all) Icons.Outlined.Deselect else Icons.Outlined.SelectAll, "Seleccionar todas")
                        }
                        if (selected.isEmpty()) {
                            TextButton(onClick = { confirmForever = trash }) { Text("Vaciar") }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                BottomAppBar {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        BarAction(Icons.Outlined.RestoreFromTrash, "Recuperar") {
                            vm.restore(selectedPhotos)
                            selected = emptySet()
                        }
                        BarAction(Icons.Outlined.DeleteForever, "Borrar para siempre") {
                            confirmForever = selectedPhotos
                        }
                    }
                }
            }
        },
    ) { inner ->
        Column(Modifier.padding(inner)) {
            if (trash.isEmpty()) {
                EmptyState("La papelera está vacía")
            } else {
                PhotoGrid(
                    sections = sections,
                    selected = selected,
                    selectionMode = true,
                    onTap = { p -> selected = if (p.id in selected) selected - p.id else selected + p.id },
                    onLongPress = { p -> selected = if (p.id in selected) selected - p.id else selected + p.id },
                    onToggleSection = { },
                    badge = { p -> daysLeft(p) },
                    header = {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Hint(
                                "Lo que hay aquí se elimina solo pasados 30 días. " +
                                    "Toca las fotos para elegirlas y recupéralas si te equivocaste."
                            )
                        }
                    },
                )
            }
        }
    }

    confirmForever?.let { list ->
        AlertDialog(
            onDismissRequest = { confirmForever = null },
            title = { Text("¿Eliminar ${list.size} foto${if (list.size == 1) "" else "s"} para siempre?") },
            text = { Text("Liberarás ${formatSize(ctx, list.sumOf { it.size })}. Esto no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForever = null
                    vm.deleteForever(list)
                    selected = emptySet()
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmForever = null }) { Text("Cancelar") } },
        )
    }
}

private fun daysLeft(p: Photo): String? {
    if (p.dateExpires <= 0) return null
    val days = ((p.dateExpires * 1000 - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
    return if (days == 1L) "1 día" else "$days días"
}
