package com.fotoorden.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fotoorden.app.ui.AlbumsScreen
import com.fotoorden.app.ui.AnalysisBanner
import com.fotoorden.app.ui.CleanupKind
import com.fotoorden.app.ui.CleanupListScreen
import com.fotoorden.app.ui.CleanupScreen
import com.fotoorden.app.ui.FotoOrdenTheme
import com.fotoorden.app.ui.PhotoBrowser
import com.fotoorden.app.ui.TrashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FotoOrdenTheme { App() } }
    }
}

private enum class Access { NONE, PARTIAL, FULL }

private fun permissionsToAsk(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun accessLevel(ctx: Context): Access {
    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val full = if (Build.VERSION.SDK_INT >= 33) has(Manifest.permission.READ_MEDIA_IMAGES)
    else has(Manifest.permission.READ_EXTERNAL_STORAGE)
    return when {
        full -> Access.FULL
        Build.VERSION.SDK_INT >= 34 && has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.PARTIAL
        else -> Access.NONE
    }
}

private sealed interface Detail {
    data class AlbumDetail(val path: String) : Detail
    data class Cleanup(val kind: CleanupKind) : Detail
}

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Fotos", Icons.Outlined.PhotoLibrary),
    Tab("Álbumes", Icons.Outlined.Collections),
    Tab("Limpieza", Icons.Outlined.CleaningServices),
    Tab("Papelera", Icons.Outlined.Delete),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun App(vm: GalleryViewModel = viewModel()) {
    val ctx = LocalContext.current
    var access by remember { mutableStateOf(accessLevel(ctx)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { access = accessLevel(ctx) }

    LifecycleResumeEffect(Unit) {
        access = accessLevel(ctx)
        if (access != Access.NONE) vm.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(access) { if (access != Access.NONE) vm.refresh() }

    // Diálogos del sistema para aprobar mover / borrar
    val approval by vm.approval.collectAsStateWithLifecycle()
    val approvalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> vm.onApprovalResult(result.resultCode == Activity.RESULT_OK) }
    LaunchedEffect(approval) {
        val req = approval
        if (req != null && !req.launched) {
            req.launched = true
            approvalLauncher.launch(IntentSenderRequest.Builder(req.sender).build())
        }
    }

    if (access == Access.NONE) {
        PermissionScreen { permissionLauncher.launch(permissionsToAsk()) }
        return
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    val pick by vm.pickForAlbum.collectAsStateWithLifecycle()
    BackHandler(enabled = detail != null) { detail = null }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (detail == null && pick == null) {
                NavigationBar {
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner).consumeWindowInsets(inner)) {
            when (val d = detail) {
                is Detail.AlbumDetail -> {
                    val albums by vm.albums.collectAsStateWithLifecycle()
                    val album = albums.find { it.relativePath == d.path }
                    if (album == null) {
                        LaunchedEffect(Unit) { detail = null }
                    } else {
                        PhotoBrowser(vm, album.name, album.photos, onBack = { detail = null })
                    }
                }
                is Detail.Cleanup -> CleanupListScreen(vm, d.kind) { detail = null }
                null -> when (tab) {
                    0 -> PhotosTab(vm, partial = access == Access.PARTIAL) {
                        permissionLauncher.launch(permissionsToAsk())
                    }
                    1 -> AlbumsScreen(
                        vm,
                        onOpen = { detail = Detail.AlbumDetail(it.relativePath) },
                        onCreate = { name -> vm.pickForAlbum.value = name; tab = 0 },
                    )
                    2 -> CleanupScreen(vm) { detail = Detail.Cleanup(it) }
                    else -> TrashScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun PhotosTab(vm: GalleryViewModel, partial: Boolean, onAskFullAccess: () -> Unit) {
    val photos by vm.photos.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val pick by vm.pickForAlbum.collectAsStateWithLifecycle()

    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    PhotoBrowser(
        vm = vm,
        title = "Mis fotos · ${photos.size}",
        photos = photos,
        pickForAlbum = pick,
        onPickFinished = { vm.pickForAlbum.value = null },
        emptyText = "No hay fotos en el móvil",
        top = {
            Column {
                if (partial) {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Solo ves las fotos que elegiste al dar permiso. Para ordenarlas todas, da acceso completo.")
                            OutlinedButton(onClick = onAskFullAccess, modifier = Modifier.padding(top = 8.dp)) {
                                Text("Dar acceso a todas")
                            }
                        }
                    }
                }
                progress?.let { AnalysisBanner(it) }
            }
        },
    )
}

@Composable
private fun PermissionScreen(onAsk: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Ordena tus fotos", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Para clasificar tus fotos (personas, documentos, paisajes…) y encontrar las que sobran, " +
                "la app necesita acceso a tu galería. Todo se analiza dentro del móvil.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAsk) { Text("Permitir acceso a las fotos") }
    }
}
