package com.dalur.film.journey

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dalur.film.camera.CameraViewModel
import com.dalur.film.shared.*
import com.dalur.film.ui.components.DalurHeader
import com.dalur.film.ui.components.DalurSectionLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun JourneysContent(vm: CameraViewModel, onPlay: (String) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as com.dalur.film.DalurApp
    val captures by vm.allCaptures.collectAsState()
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf("MEMORY") }
    var ratio by remember { mutableStateOf("9:16") }
    var status by remember { mutableStateOf("") }

    val gps = remember(captures) {
        orderCapturesChronologically(captures).filter { it.gps != null }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        DalurHeader(
            title = "Journeys",
            subtitle = "Built only from DALUR captures. No sign-in, no Google Timeline."
        )
        Spacer(Modifier.height(12.dp))
        DalurSectionLabel("Style")
        Row(Modifier.padding(vertical = 4.dp)) {
            listOf("MEMORY", "CINEMA", "POSTCARD", "JOURNAL").forEach { s ->
                FilterChip(selected = style == s, onClick = { style = s },
                    label = { Text(s) }, modifier = Modifier.padding(end = 6.dp))
            }
        }
        DalurSectionLabel("Aspect")
        Row(Modifier.padding(vertical = 4.dp)) {
            listOf("9:16", "1:1", "16:9").forEach { r ->
                FilterChip(selected = ratio == r, onClick = { ratio = r },
                    label = { Text(r) }, modifier = Modifier.padding(end = 6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            scope.launch {
                if (gps.isEmpty()) { status = "Shoot some located captures first."; return@launch }
                val caps = app.capabilities.report.value
                val preset = pickExportPreset(ratio, caps?.hevcSupported == true)
                val j = journeyRouteFromCaptures(
                    UUID.randomUUID().toString().take(8), "$style journey",
                    gps, style, preset.id, System.currentTimeMillis())
                JourneyStore.save(ctx, j)
                status = "Journey ${j.id} ready · ${j.routePoints.size} stops · ${preset.id}"
                onPlay(j.id)
            }
        }, enabled = gps.isNotEmpty()) {
            Text("Preview journey (${gps.size} stops)")
        }
        if (status.isNotEmpty()) Text(status,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(JourneyStore.list(ctx)) { j ->
                ElevatedCard(onClick = { onPlay(j.id) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(j.title, color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text("${j.stylePreset} · ${j.exportPreset} · ${j.routePoints.size} stops",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun JourneyPlayerScreen(vm: CameraViewModel, journeyId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val journey = remember(journeyId) { JourneyStore.load(ctx, journeyId) }
    var step by remember { mutableStateOf(0) }
    var exporting by remember { mutableStateOf(false) }
    var exportMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (journey == null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Journey not found.", color = MaterialTheme.colorScheme.onBackground)
            Button(onClick = onBack) { Text("Back") }
        }
        return
    }
    val timeline = remember(journey) { JourneyTimeline.build(journey) }
    // Signature reveal animation state
    val pulse = rememberInfiniteTransition()
    val scale by pulse.animateFloat(0.92f, 1.04f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse))

    // Auto-advance through the timeline (respects reduced motion via longer holds).
    LaunchedEffect(journeyId) {
        for (i in timeline.indices) {
            step = i
            val hold = when (val s = timeline[i]) {
                is JourneySegment.Reveal -> (s.holdSec * 1000).toLong()
                is JourneySegment.Route -> (s.durationSec * 1000).toLong()
                JourneySegment.Overview -> 2200L
            }
            delay(hold.coerceIn(600L, 4500L))
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Text(journey.title, color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text("${step + 1}/${timeline.size}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
        }
        // Route progress
        LinearProgressIndicator(
            progress = { (step + 1).toFloat() / timeline.size },
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        // Stage: route dot pulse + photo card rise/fade/scale
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            when (val s = timeline.getOrNull(step)) {
                is JourneySegment.Route -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size((18 * scale).dp).background(MaterialTheme.colorScheme.primary,
                            androidx.compose.foundation.shape.CircleShape))
                        Spacer(Modifier.height(8.dp))
                        Text("travelling…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is JourneySegment.Reveal -> {
                    Card(Modifier.fillMaxWidth(0.82f)) {
                        Column(Modifier.padding(16.dp)) {
                            Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary,
                                androidx.compose.foundation.shape.CircleShape))
                            Spacer(Modifier.height(8.dp))
                            Text(s.mediaId, style = MaterialTheme.typography.titleMedium)
                            Text("${journey.title} · ${s.style}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            // Subtle film treatment bar
                            Box(Modifier.fillMaxWidth().height(64.dp)
                                .background(MaterialTheme.colorScheme.background,
                                    RoundedCornerShape(12.dp)))
                        }
                    }
                }
                JourneySegment.Overview, null -> {
                    Text("Full route · ${journey.routePoints.size} moments",
                        color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            OutlinedButton(onClick = onBack) { Text("Close") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                exporting = true
                scope.launch {
                    exportMsg = try {
                        val out = JourneyExporter.exportMp4(ctx, journey)
                        "Exported: $out"
                    } catch (e: Exception) {
                        "Export failed: ${e.message}"
                    }
                    exporting = false
                }
            }) { Text(if (exporting) "Exporting…" else "Export MP4") }
        }
        if (exportMsg.isNotEmpty()) Text(exportMsg,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }
}
