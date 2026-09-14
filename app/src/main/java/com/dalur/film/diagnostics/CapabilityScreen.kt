package com.dalur.film.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dalur.film.DalurApp
import com.dalur.film.camera.CameraViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Composable
fun CapabilityScreen(vm: CameraViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as DalurApp
    val caps by vm.capabilityReport.collectAsState()
    val scope = rememberCoroutineScope()
    val pretty = remember { Json { prettyPrint = true } }

    LaunchedEffect(Unit) { scope.launch { app.capabilities.refresh() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Text("Capability report", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(8.dp))
        val r = caps
        if (r == null) {
            CircularProgressIndicator()
            return@Column
        }
        LazyColumn(Modifier.weight(1f)) {
            item {
                CapabilityRow("Device", r.deviceModel)
                CapabilityRow("Android SDK", r.androidSdk.toString())
                CapabilityRow("Cameras", r.cameras.joinToString { "${it.lensFacing}#${it.cameraId}" }.ifEmpty { "none" })
                CapabilityRow("HEVC", yesNo(r.hevcSupported))
                CapabilityRow("HEVC 10-bit", yesNo(r.hevc10BitSupported))
                CapabilityRow("HDR", yesNo(r.hdrSupported))
                CapabilityRow("RAW", yesNo(r.rawSupported))
                CapabilityRow("Log", r.logProfile ?: "unsupported")
                if (!r.logSupported) {
                    Text(r.logUnsupportedReason ?: "Log unavailable.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
                CapabilityRow("ProRes", "Not supported on this device (iOS-only)")
                CapabilityRow("Manual ISO", yesNo(r.manualIsoSupported))
                CapabilityRow("Manual shutter", yesNo(r.manualShutterSupported))
                CapabilityRow("Manual focus", yesNo(r.manualFocusSupported))
                CapabilityRow("USB mass storage",
                    if (r.usbMassStorageSupported) r.usbStorageVolumes.joinToString { it.rootPath }
                    else "Not supported on this device")
                CapabilityRow("External monitor output",
                    "Not supported on this device (separate from SSD recording)")
                CapabilityRow("Resolutions", r.supportedResolutions.joinToString())
                CapabilityRow("Frame rates", r.supportedFrameRates.joinToString())
                r.notes.forEach {
                    Text("• $it",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Text("Raw JSON", color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleSmall)
                Text(pretty.encodeToString(
                    com.dalur.film.shared.CapabilityReport.serializer(), r),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = { scope.launch { app.capabilities.refresh() } }) { Text("Re-run detection") }
    }
}

@Composable
private fun CapabilityRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("$k: ", color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyMedium)
        Text(v, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
    }
}

private fun yesNo(b: Boolean) = if (b) "supported" else "Not supported on this device"
