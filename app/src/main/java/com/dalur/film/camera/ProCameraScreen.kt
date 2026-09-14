package com.dalur.film.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
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
        // Top HUD: REC / resolution / fps / codec / monitor / storage / audio
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${pro.resolution} · ${pro.fps}fps · ${pro.codecLabel}",
                color = scheme.onBackground, style = MaterialTheme.typography.labelMedium)
            val mon = if (pro.monitor.mode == MonitorMode.LOG) "MONITOR: LOG" else
                "MONITOR: LUT ${pro.monitor.recipeId ?: ""}"
            Text(mon, color = scheme.primary, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(4.dp))
        DalurSectionLabel("Monitor")
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            // LOG / LUT toggle (capability-gated: never fake Log)
            val logOk = caps?.logSupported == true
            FilterChip(
                selected = pro.monitor.mode == MonitorMode.LOG,
                enabled = logOk,
                onClick = { vm.setMonitorMode(MonitorMode.LOG) },
                label = { Text("LOG") })
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = pro.monitor.mode == MonitorMode.LUT,
                onClick = { vm.setMonitorMode(MonitorMode.LUT) },
                label = { Text("LUT") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = pro.histogramOn, onClick = { vm.toggleHistogram() },
                label = { Text("Hist") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = pro.zebraOn, onClick = { vm.toggleZebra() },
                label = { Text("Zebra") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = pro.guidesOn, onClick = { vm.toggleGuides() },
                label = { Text("Guides") })
        }
        if (caps?.logSupported == false) {
            Text(
                caps?.logUnsupportedReason
                    ?: "Log is not supported on this device.",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        } else {
            Text("Master stays ${caps?.logProfile ?: "LOG/flat"}; LUT is monitor-only.",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(4.dp))
        DalurSectionLabel("Exposure")
        // Manual controls (shown; disabled with reason when unsupported)
        val isoOk = caps?.manualIsoSupported == true
        val shutterOk = caps?.manualShutterSupported == true
        Row(Modifier.fillMaxWidth()) {
            AssistChip(
                enabled = isoOk,
                onClick = { vm.setIso(if (pro.iso == null) 400 else null) },
                label = { Text(if (pro.iso == null) "ISO Auto" else "ISO ${pro.iso}") })
            Spacer(Modifier.width(8.dp))
            AssistChip(
                enabled = shutterOk,
                onClick = {},
                label = { Text("Shutter ${pro.shutterSec ?: "Auto"}") })
            Spacer(Modifier.width(8.dp))
            AssistChip(onClick = {
                val order = listOf(24, 30, 60)
                vm.setFps(order[(order.indexOf(pro.fps) + 1) % order.size])
            }, label = { Text("${pro.fps} fps") })
            Spacer(Modifier.width(8.dp))
            AssistChip(onClick = {
                vm.setResolution(if (pro.resolution == "1080p") "4K" else "1080p")
            }, label = { Text(pro.resolution) })
        }
        if (!isoOk || !shutterOk) {
            Text("Not supported on this device: manual " +
                listOfNotNull(
                    if (!isoOk) "ISO" else null,
                    if (!shutterOk) "shutter" else null).joinToString("/"),
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(4.dp))
        DalurSectionLabel("Motion & codec")
        // Codec selector (capability-driven only)
        Row {
            val hevc = caps?.hevcSupported == true
            FilterChip(selected = pro.codecLabel.startsWith("HEVC"), enabled = hevc,
                onClick = { vm.setCodecLabel(if (caps?.hevc10BitSupported == true) "HEVC 10-bit" else "HEVC") },
                label = { Text(if (caps?.hevc10BitSupported == true) "HEVC 10-bit" else "HEVC") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = pro.codecLabel == "H.264",
                onClick = { vm.setCodecLabel("H.264") }, label = { Text("H.264") })
            if (!hevc) Text("  HEVC: Not supported on this device",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(4.dp))
        DalurSectionLabel("Storage")
        // USB-C external SSD recording (first-class, separate from monitor output)
        UsbRow(vm)
    }
}

@Composable
private fun UsbRow(vm: CameraViewModel) {
    val pro by vm.pro.collectAsState()
    val caps by vm.capabilityReport.collectAsState()
    val vols = caps?.usbStorageVolumes.orEmpty()
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        FilterChip(selected = pro.recordTarget is RecordTarget.Internal,
            onClick = { vm.setRecordTarget(RecordTarget.Internal) },
            label = { Text("Internal") })
        Spacer(Modifier.width(8.dp))
        if (vols.isEmpty()) {
            AssistChip(enabled = false, onClick = {},
                label = { Text("USB SSD: none mounted") })
        } else {
            vols.forEach { v ->
                val sel = (pro.recordTarget as? RecordTarget.Usb)?.volume?.rootPath == v.rootPath
                FilterChip(selected = sel,
                    onClick = { vm.setRecordTarget(RecordTarget.Usb(v)) },
                    label = {
                        val gb = v.freeBytes / 1_000_000_000
                        Text("${v.label} ${gb}G free")
                    })
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
