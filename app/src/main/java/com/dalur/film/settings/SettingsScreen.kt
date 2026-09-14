package com.dalur.film.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dalur.film.DalurApp
import com.dalur.film.camera.CameraViewModel
import com.dalur.film.ui.components.DalurSectionLabel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: CameraViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as DalurApp
    val scope = rememberCoroutineScope()
    val intensity by app.settings.filmIntensity.collectAsState(0.85f)
    val mapStyle by app.settings.mapStyle.collectAsState("https://demotiles.maplibre.org/style.json")
    val reducedMotion by app.settings.reducedMotion.collectAsState(false)
    val easy by vm.easy.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Text("Settings", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(12.dp))
        DalurSectionLabel("Capture")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Film intensity", color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f))
            Text("${(intensity * 100).toInt()}%",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall)
        }
        Slider(value = intensity, onValueChange = { scope.launch { app.settings.setFilmIntensity(it) } })
        Spacer(Modifier.height(8.dp))
        DalurSectionLabel("Pro")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pro controls", color = MaterialTheme.colorScheme.onBackground)
                Text("Manual exposure, LOG/LUT monitor, histogram, SSD target.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = easy.isPro, onCheckedChange = { vm.togglePro() })
        }
        Spacer(Modifier.height(8.dp))
        DalurSectionLabel("Map")
        Text("Map style URL (configurable; attribution per provider)",
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = mapStyle, onValueChange = {},
            readOnly = true, modifier = Modifier.fillMaxWidth())
        Text("Default demo tiles: https://demotiles.maplibre.org/style.json — " +
            "replace with a licensed style for production. © OpenStreetMap contributors.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        DalurSectionLabel("Accessibility")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reduced motion", color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f))
            Switch(checked = reducedMotion,
                onCheckedChange = { scope.launch { app.settings.setReducedMotion(it) } })
        }
        Spacer(Modifier.height(12.dp))
        Text("Storage location, permissions, export quality, haptics, accessibility, " +
            "about, licenses, privacy, terms and diagnostics live here. " +
            "No account, no analytics required for core camera operation.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }
}
