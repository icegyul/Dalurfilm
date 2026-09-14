package com.dalur.film.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dalur.film.camera.CameraViewModel
import com.dalur.film.shared.CaptureMetadata
import com.dalur.film.shared.orderCapturesChronologically
import com.dalur.film.ui.components.DalurHeader
import kotlinx.coroutines.launch

@Composable
fun MapScreen(vm: CameraViewModel) {
    val ctx = LocalContext.current
    val captures by vm.allCaptures.collectAsState()
    val scope = rememberCoroutineScope()
    var mapError by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var controller by remember { mutableStateOf<MapOverlayController?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            try { (ctx.applicationContext as com.dalur.film.DalurApp).captures.load() }
            catch (_: Exception) {}
        }
    }

    val gpsCaptures = remember(captures) {
        orderCapturesChronologically(captures).filter { it.gps != null }
    }

    // Refresh whenever new located captures arrive (fixes stale-map on new capture).
    LaunchedEffect(gpsCaptures, controller) {
        controller?.render(gpsCaptures) { msg -> mapError = msg }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        DalurHeader(
            title = "Map",
            subtitle = "${gpsCaptures.size} located · ${captures.size - gpsCaptures.size} without location (saved anyway, never invented)."
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (mapError != null) {
                Card { Text(mapError!!, Modifier.padding(16.dp)) }
            } else {
                AndroidView(
                    factory = { c ->
                        try {
                            org.maplibre.android.MapLibre.getInstance(c)
                            org.maplibre.android.maps.MapView(c).apply {
                                onCreate(null)
                                getMapAsync { map ->
                                    try {
                                        map.addOnMapClickListener { _ -> selectedId = null; true }
                                        map.setOnMarkerClickListener { m ->
                                            selectedId = m.title; true
                                        }
                                        // Configurable style; demo endpoint default, attribution per provider.
                                        map.setStyle("https://demotiles.maplibre.org/style.json") {
                                            try {
                                                // Store the controller only once the style is live so
                                                // later renders (new captures) affect a ready map.
                                                controller = MapOverlayController(map)
                                            } catch (e: Exception) {
                                                mapError = "Map overlay failed: ${e.message}"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        mapError = "Map style failed: ${e.message}"
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            mapError = "Map unavailable: ${e.message}"
                            // Return a plain view so compose still has content.
                            android.view.View(c)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.height(140.dp)) {
            items(gpsCaptures.takeLast(20)) { cap ->
                ListItem(
                    headlineContent = { Text("${cap.mediaType} · ${cap.mediaId}") },
                    supportingContent = {
                        Text("${cap.gps!!.latitude}, ${cap.gps!!.longitude} · ${cap.filmRecipeId ?: "no film"}")
                    },
                    modifier = Modifier.clickable { selectedId = cap.mediaId }
                )
            }
        }
        selectedId?.let { id ->
            val cap = gpsCaptures.firstOrNull { it.mediaId == id }
            if (cap != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${cap.mediaType} · ${cap.timestampMillis}",
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Film: ${cap.filmRecipeId ?: "—"}  Codec: ${cap.codec ?: "—"}  " +
                            "Profile: ${cap.colorProfile ?: "—"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/**
 * Owns the MapLibre annotations for the capture route. Re-rendering clears the
 * previous markers/polyline first, so [render] is idempotent and safe to call
 * whenever the capture list grows.
 */
private class MapOverlayController(private val map: org.maplibre.android.maps.MapLibreMap) {
    private val markers = mutableListOf<org.maplibre.android.annotations.Marker>()
    private val polylines = mutableListOf<org.maplibre.android.annotations.Polyline>()

    fun render(captures: List<CaptureMetadata>, onError: (String) -> Unit) {
        try {
            markers.forEach { runCatching { map.removeMarker(it) } }
            polylines.forEach { runCatching { map.removePolyline(it) } }
            markers.clear()
            polylines.clear()
            if (captures.size >= 2) {
                val pts = captures.map { cap ->
                    val g = requireNotNull(cap.gps)
                    org.maplibre.android.geometry.LatLng(g.latitude, g.longitude)
                }
                polylines += map.addPolyline(
                    org.maplibre.android.annotations.PolylineOptions()
                        .addAll(pts).width(4f))
                val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                    .includes(pts).build()
                map.easeCamera(
                    org.maplibre.android.camera.CameraUpdateFactory
                        .newLatLngBounds(bounds, 80), 800)
            } else if (captures.size == 1) {
                val g = captures.first().gps!!
                map.easeCamera(
                    org.maplibre.android.camera.CameraUpdateFactory
                        .newLatLngZoom(
                            org.maplibre.android.geometry.LatLng(
                                g.latitude, g.longitude), 13.0), 800)
            }
            captures.forEach { cap ->
                val g = requireNotNull(cap.gps)
                markers += map.addMarker(
                    org.maplibre.android.annotations.MarkerOptions()
                        .position(org.maplibre.android.geometry.LatLng(g.latitude, g.longitude))
                        .title(cap.mediaId)
                        .snippet(cap.filmRecipeId ?: cap.mediaType))
            }
        } catch (e: Exception) {
            onError("Map overlay failed: ${e.message}")
        }
    }
}