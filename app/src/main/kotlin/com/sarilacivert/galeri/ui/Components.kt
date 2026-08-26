package com.sarilacivert.galeri.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarilacivert.galeri.data.Album
import com.sarilacivert.galeri.data.BitmapLoader
import com.sarilacivert.galeri.data.MediaItem
import com.sarilacivert.galeri.data.MediaRepository

@Composable
fun AsyncThumbnail(
    loader: BitmapLoader,
    uri: Uri,
    modifier: Modifier = Modifier,
    sizePx: Int = 320,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, sizePx) {
        value = loader.thumbnail(uri, sizePx)
    }
    Box(modifier = modifier.background(SurfaceAlt), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
fun AlbumCard(album: Album, loader: BitmapLoader, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column {
            Box {
                AsyncThumbnail(
                    loader = loader,
                    uri = album.coverUri,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.15f),
                    sizePx = 320
                )
                if (album.hasVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Navy800.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Yellow500, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(
                    album.name,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text("${album.count} öğe", color = Yellow400, fontSize = 13.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    album.path.ifBlank { "Diğer" },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
            }
        }
    }
}

@Composable
fun MediaTile(
    item: MediaItem,
    loader: BitmapLoader,
    favorite: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    selectionEnabled: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onBoundsChanged: ((Rect?) -> Unit)? = null
) {
    val key = item.uri.toString()

    DisposableEffect(key) {
        onDispose { onBoundsChanged?.invoke(null) }
    }

    Box(
        modifier = modifier
            .padding(1.dp)
            .aspectRatio(1f)
            .background(SurfaceAlt)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged?.invoke(coordinates.boundsInWindow())
            }
            .clickable {
                if (selectionEnabled && selectionMode) onToggleSelection() else onClick()
            }
    ) {
        AsyncThumbnail(loader, item.uri, Modifier.fillMaxSize(), 240)

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Yellow500.copy(alpha = 0.22f))
            )
            Text(
                "✓",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Navy800.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }

        if (item.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .background(Navy800.copy(alpha = 0.88f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                Text(MediaRepository.formatDuration(item.duration), color = TextPrimary, fontSize = 10.sp)
            }
        }
        if (favorite) {
            Text(
                "★",
                color = Yellow500,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp)
            )
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun LoadingState(text: String = "Yükleniyor…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(text, color = TextSecondary)
        }
    }
}
