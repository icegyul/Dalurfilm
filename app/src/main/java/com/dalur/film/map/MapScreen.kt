package com.dalur.film.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dalur.film.R
import com.dalur.film.camera.CameraViewModel
import com.dalur.film.journey.JourneysContent
import com.dalur.film.shared.CaptureMetadata
import com.dalur.film.shared.orderCapturesChronologically
import com.dalur.film.ui.components.DalurHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: CameraViewModel, onPlay: (String) -> Unit) {
    val ctx = LocalContext.current
    val captures by vm.allCaptures.collectAsState()
    val scope = rememberCoroutineScope()
    var mapError by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }
    var controller by remember { mutableStateOf<MapOverlayController?>(null) }
    var mapView by remember { mutableStateOf<org.maplibre.android.maps.MapView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // MapView needs the host lifecycle (without onStart/onResume the map stays black).
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView?.onStart()
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_STOP -> mapView?.onStop()
                Lifecycle.Event.ON_DESTROY -> runCatching { mapView?.onDestroy() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            runCatching { mapView?.onDestroy() }
        }
    }

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
            title = stringResource(R.string.map_title),
            subtitle = "${gpsCaptures.size} located · ${captures.size - gpsCaptures.size} without location (saved anyway, never invented)."
        )
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = tab == 0, onClick = { tab = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 3), label = { Text(stringResource(R.string.map_tab_map)) })
            SegmentedButton(selected = tab == 1, onClick = { tab = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 3), label = { Text("Journeys") })
            SegmentedButton(selected = tab == 2, onClick = { tab = 2 },
                shape = SegmentedButtonDefaults.itemShape(2, 3), label = { Text("날씨") })
        }
        Spacer(Modifier.height(8.dp))
        if (tab == 0) {
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
                                mapView = this
                                getMapAsync { map ->
                                    try {
                                        map.addOnMapClickListener { _ -> selectedId = null; true }
                                        map.setOnMarkerClickListener { m ->
                                            selectedId = m.title; true
                                        }
                                        // Dark base map matching the app theme (free CARTO tiles,
                                        // no key; attribution: © OpenStreetMap contributors © CARTO).
                                        // Configurable via Settings → map style URL.
                                        map.setStyle("https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json") {
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
        } else if (tab == 1) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                JourneysContent(vm, onPlay)
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                EarthZoomWeatherTab(vm)
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
                val lats = pts.map { it.latitude }
                val lngs = pts.map { it.longitude }
                val span = maxOf(lats.max() - lats.min(), lngs.max() - lngs.min())
                if (span < 0.06) {
                    // Points are close: fixed district zoom (구·동 레벨) instead
                    // of fitting bounds (which zooms in too far).
                    val center = org.maplibre.android.geometry.LatLng(
                        (lats.max() + lats.min()) / 2,
                        (lngs.max() + lngs.min()) / 2)
                    map.easeCamera(
                        org.maplibre.android.camera.CameraUpdateFactory
                            .newLatLngZoom(center, 13.5), 800)
                } else {
                    val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                        .includes(pts).build()
                    map.easeCamera(
                        org.maplibre.android.camera.CameraUpdateFactory
                            .newLatLngBounds(bounds, 80), 800)
                }
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