package com.fotoorden.app.ui

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fotoorden.app.data.AnalysisProgress
import com.fotoorden.app.data.Category
import com.fotoorden.app.data.Photo
import com.fotoorden.app.data.PhotoSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val SPANISH: Locale = Locale.forLanguageTag("es-ES")

fun categoryIcon(c: Category): ImageVector = when (c) {
    Category.PERSONAS -> Icons.Outlined.Face
    Category.DOCUMENTOS -> Icons.Outlined.Description
    Category.PAISAJES -> Icons.Outlined.Landscape
    Category.CAPTURAS -> Icons.Outlined.Screenshot
    Category.COMIDA -> Icons.Outlined.Restaurant
    Category.MASCOTAS -> Icons.Outlined.Pets
    Category.OTRAS -> Icons.Outlined.Image
}

fun formatSize(ctx: Context, bytes: Long): String = Formatter.formatShortFileSize(ctx, bytes)

fun formatDate(ms: Long): String =
    SimpleDateFormat("d 'de' MMMM 'de' yyyy, HH:mm", SPANISH).format(Date(ms))

/** Agrupa fotos por mes: «Noviembre 2026». */
fun monthSections(photos: List<Photo>): List<PhotoSection> {
    val fmt = SimpleDateFormat("LLLL yyyy", SPANISH)
    val keyFmt = SimpleDateFormat("yyyy-MM", SPANISH)
    return photos.groupBy { keyFmt.format(Date(it.dateTaken)) }
        .map { (key, ps) ->
            val title = fmt.format(Date(ps.first().dateTaken))
                .replaceFirstChar { it.titlecase(SPANISH) }
            PhotoSection(key, title, ps)
        }
}

fun sharePhotos(ctx: Context, photos: List<Photo>) {
    if (photos.isEmpty()) return
    val uris = ArrayList(photos.take(100).map { it.uri })
    val intent: Intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    intent.type = "image/*"
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(intent, "Compartir fotos"))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoTile(
    photo: Photo,
    selected: Boolean,
    selectionMode: Boolean,
    badge: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val request = remember(photo.uri) {
        ImageRequest.Builder(ctx).data(photo.uri).size(320).crossfade(true).build()
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = request,
            contentDescription = photo.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (selected) Modifier.padding(10.dp).clip(RoundedCornerShape(8.dp)) else Modifier)
        )
        if (selectionMode) {
            if (selected) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)))
            }
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .padding(4.dp)
                    .size(22.dp)
                    .background(if (selected) Color.White else Color.Black.copy(alpha = 0.25f), CircleShape)
                    .align(Alignment.TopStart)
            )
        }
        if (badge != null) {
            Text(
                badge,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

/**
 * Cuadrícula de fotos por secciones, con selección.
 * [header] se dibuja a lo ancho al principio.
 */
@Composable
fun PhotoGrid(
    sections: List<PhotoSection>,
    selected: Set<Long>,
    selectionMode: Boolean,
    onTap: (Photo) -> Unit,
    onLongPress: (Photo) -> Unit,
    onToggleSection: (PhotoSection) -> Unit,
    modifier: Modifier = Modifier,
    badge: (Photo) -> String? = { null },
    header: LazyGridScope.() -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        header()
        for (section in sections) {
            if (section.title != null) {
                item(key = "h/${section.key}", span = { GridItemSpan(maxLineSpan) }) {
                    val all = section.photos.all { it.id in selected }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onToggleSection(section) }) {
                            Text(if (all && selectionMode) "Quitar" else "Seleccionar")
                        }
                    }
                }
            }
            items(
                count = section.photos.size,
                key = { "p/${section.key}/${section.photos[it].id}" },
            ) { i ->
                val p = section.photos[i]
                PhotoTile(
                    photo = p,
                    selected = p.id in selected,
                    selectionMode = selectionMode,
                    badge = badge(p),
                    onClick = { onTap(p) },
                    onLongClick = { onLongPress(p) },
                )
            }
        }
    }
}

@Composable
fun CategoryChips(
    counts: Map<Category?, Int>,
    current: Category?,
    onSelect: (Category?) -> Unit,
) {
    val options: List<Category?> = listOf<Category?>(null) + Category.entries
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options.filter { it == null || (counts[it] ?: 0) > 0 }) { c ->
            FilterChip(
                selected = current == c,
                onClick = { onSelect(c) },
                label = { Text("${c?.label ?: "Todas"} · ${counts[c] ?: 0}") },
                leadingIcon = if (c != null) {
                    { Icon(categoryIcon(c), null, Modifier.size(18.dp)) }
                } else null,
            )
        }
    }
}

@Composable
fun AnalysisBanner(progress: AnalysisProgress) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Analizando tus fotos… ${progress.done} de ${progress.total}",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Se hace dentro del móvil. Las categorías van apareciendo mientras tanto; " +
                    "si cierras la app, continúa donde lo dejó.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
