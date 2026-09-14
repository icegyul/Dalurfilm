package com.dalur.film.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dalur.film.pro.MonitorMode
import com.dalur.film.recording.RecordTarget
import com.dalur.film.ui.components.DalurSectionLabel

@Composable
fun ProCameraPanel(vm: CameraViewModel) {
    val pro by vm.pro.collectAsState()
    val caps by vm.capabilityReport.collectAsState()
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // ---- TOP HUD (resolution · fps · codec) + codec selector ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${pro.resolution} · ${pro.fps}fps · ${pro.codecLabel}",
                color = scheme.onBackground, style = MaterialTheme.typography.labelMedium)
            val mon = if (pro.monitor.mode == MonitorMode.LOG) "MONITOR: LOG" else
                "MONITOR: LUT ${pro.monitor.recipeId ?: ""}"
            Text(mon, color = scheme.primary, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(6.dp))
        // Codec selector (capability-driven only).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val hevc = caps?.hevcSupported == true
            ProFilterChip(
                selected = pro.codecLabel.startsWith("HEVC"),
                enabled = hevc,
                onClick = { vm.setCodecLabel(if (caps?.hevc10BitSupported == true) "HEVC 10-bit" else "HEVC") },
                label = if (caps?.hevc10BitSupported == true) "HEVC 10-bit" else "HEVC")
            ProFilterChip(
                selected = pro.codecLabel == "H.264",
                onClick = { vm.setCodecLabel("H.264") },
                label = "H.264")
            if (!hevc) {
                Text("  HEVC: Not supported on this device",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(10.dp))

        // ---- MONITOR ----
        DalurSectionLabel("Monitor")
        Row(Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // LOG / LUT toggle (capability-gated: never fake Log)
            val logOk = caps?.logSupported == true
            ProFilterChip(selected = pro.monitor.mode == MonitorMode.LOG, enabled = logOk,
                onClick = { vm.setMonitorMode(MonitorMode.LOG) }, label = "LOG")
            ProFilterChip(selected = pro.monitor.mode == MonitorMode.LUT,
                onClick = { vm.setMonitorMode(MonitorMode.LUT) }, label = "LUT")
            ProFilterChip(selected = pro.histogramOn, onClick = { vm.toggleHistogram() },
                label = "Hist")
            ProFilterChip(selected = pro.zebraOn, onClick = { vm.toggleZebra() },
                label = "Zebra")
            ProFilterChip(selected = pro.guidesOn, onClick = { vm.toggleGuides() },
                label = "Guides")
        }
        Text(
            if (caps?.logSupported == false)
                caps?.logUnsupportedReason ?: "Log is not supported on this device."
            else
                "Master stays ${caps?.logProfile ?: "LOG/flat"}; LUT is monitor-only.",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(10.dp))

        // ---- EXPOSURE (manual controls; disabled with reason when unsupported) ----
        DalurSectionLabel("Exposure")
        val isoOk = caps?.manualIsoSupported == true
        val shutterOk = caps?.manualShutterSupported == true
        Row(Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProAssistChip(enabled = isoOk,
                onClick = { vm.setIso(if (pro.iso == null) 400 else null) },
                label = if (pro.iso == null) "ISO Auto" else "ISO ${pro.iso}")
            ProAssistChip(enabled = shutterOk, onClick = {},
                label = "Shutter ${pro.shutterSec ?: "Auto"}")
            ProAssistChip(onClick = {
                val order = listOf(24, 30, 60)
                vm.setFps(order[(order.indexOf(pro.fps) + 1) % order.size])
            }, label = "${pro.fps} fps")
            ProAssistChip(onClick = {
                vm.setResolution(if (pro.resolution == "1080p") "4K" else "1080p")
            }, label = pro.resolution)
        }
        if (!isoOk || !shutterOk) {
            Text("Not supported on this device: manual " +
                listOfNotNull(
                    if (!isoOk) "ISO" else null,
                    if (!shutterOk) "shutter" else null).joinToString("/"),
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))

        // ---- USB (USB-C external SSD recording, separate from monitor output) ----
        DalurSectionLabel("USB")
        UsbRow(vm)
    }
}

/** Small uniform filter chip used across PRO sections: even spacing, dark fill. */
@Composable
private fun ProFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
        modifier = Modifier.height(28.dp),
        shape = RoundedCornerShape(6.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color(0xFF1E1E24),
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = Color(0xFF141417),
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    )
}

/** Small uniform assist chip matching ProFilterChip: even spacing, dark fill. */
@Composable
private fun ProAssistChip(
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
        modifier = Modifier.height(28.dp),
        shape = RoundedCornerShape(6.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = Color(0xFF1E1E24),
            labelColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = Color(0xFF141417),
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun UsbRow(vm: CameraViewModel) {
    val pro by vm.pro.collectAsState()
    val caps by vm.capabilityReport.collectAsState()
    val vols = caps?.usbStorageVolumes.orEmpty()
    Row(Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ProFilterChip(selected = pro.recordTarget is RecordTarget.Internal,
            onClick = { vm.setRecordTarget(RecordTarget.Internal) },
            label = "Internal")
        if (vols.isEmpty()) {
            ProAssistChip(enabled = false, onClick = {},
                label = "USB SSD: none mounted")
        } else {
            vols.forEach { v ->
                val sel = (pro.recordTarget as? RecordTarget.Usb)?.volume?.rootPath == v.rootPath
                ProFilterChip(selected = sel,
                    onClick = { vm.setRecordTarget(RecordTarget.Usb(v)) },
                    label = "${v.label} ${v.freeBytes / 1_000_000_000}G free")
            }
        }
    }
    Text("External monitor/recorder output: Not supported on this device " +
        "(separate from SSD recording; never conflated).",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall)
}

@Composable
fun HistogramView(bins: IntArray, modifier: Modifier = Modifier) {
    Canvas(modifier.height(36.dp).fillMaxWidth()) {
        if (bins.isEmpty()) return@Canvas
        val max = (bins.maxOrNull() ?: 1).toFloat()
        val bw = size.width / bins.size
        bins.forEachIndexed { i, v ->
            val h = (v / max) * size.height
            drawRect(Color.White.copy(alpha = 0.85f),
                topLeft = Offset(i * bw, size.height - h), size = Size(bw - 1, h))
        }
    }
}
