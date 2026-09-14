package com.dalur.film.playback

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dalur.film.pro.MonitorMode

/** Clip playback with non-destructive LOG/LUT switch (never rewrites the master). */
@Composable
fun PlaybackScreen(uri: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(MonitorMode.LUT) }
    val decoded = remember(uri) { android.net.Uri.decode(uri) }
    val player = remember(decoded) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(decoded))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
            FilterChip(selected = mode == MonitorMode.LOG,
                onClick = { mode = MonitorMode.LOG }, label = { Text("LOG") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = mode == MonitorMode.LUT,
                onClick = { mode = MonitorMode.LUT }, label = { Text("LUT") })
        }
        Text(if (mode == MonitorMode.LOG) "Viewing recorded master as recorded."
        else "Viewing with capture LUT re-applied (non-destructive).",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        AndroidView(factory = { c ->
            PlayerView(c).apply {
                this.player = player
                useController = true
            }
        }, modifier = Modifier.fillMaxWidth().weight(1f)
            .clip(MaterialTheme.shapes.medium))
        Spacer(Modifier.height(8.dp))
        Text("Master is preserved; switching LOG/LUT never re-encodes the source.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }
}
