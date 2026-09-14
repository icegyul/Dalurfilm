package com.dalur.film.shared

import kotlinx.serialization.Serializable

/** Runtime capability matrix. Drives all capability-gated UI. */
@Serializable
data class CapabilityReport(
    val generatedAtMillis: Long,
    val deviceModel: String,
    val androidSdk: Int,
    val cameras: List<CameraCapability> = emptyList(),
    val hevcSupported: Boolean = false,
    val hevc10BitSupported: Boolean = false,
    val hdrSupported: Boolean = false,
    val rawSupported: Boolean = false,
    /** Genuine Log/flat profile id, or null when unsupported. NEVER faked. */
    val logProfile: String? = null,
    val logSupported: Boolean = false,
    val logUnsupportedReason: String? = null,
    val proResSupported: Boolean = false, // always false on Android; iOS-only gate
    val manualIsoSupported: Boolean = false,
    val manualShutterSupported: Boolean = false,
    val manualFocusSupported: Boolean = false,
    val stabilizationSupported: Boolean = false,
    val usbMassStorageSupported: Boolean = false,
    val usbStorageVolumes: List<UsbVolume> = emptyList(),
    /** External monitor/recorder output is a SEPARATE capability from SSD recording. */
    val externalMonitorOutputSupported: Boolean = false,
    val supportedResolutions: List<String> = emptyList(),
    val supportedFrameRates: List<Int> = emptyList(),
    val notes: List<String> = emptyList()
)

@Serializable
data class CameraCapability(
    val cameraId: String,
    val lensFacing: String, // back | front | external
    val logicalLenses: List<String> = emptyList(),
    val maxZoomRatio: Float = 1f,
    val flashSupported: Boolean = false
)

@Serializable
data class UsbVolume(
    val label: String,
    val rootPath: String,
    val freeBytes: Long,
    val totalBytes: Long,
    val writable: Boolean
)

@Serializable
data class ExportPreset(
    val id: String, // e.g. NINE_SIXTEEN_1080P_HEVC
    val ratio: String, // 9:16 | 1:1 | 16:9
    val width: Int,
    val height: Int,
    val codec: String, // HEVC | H264
    val bitrateMbps: Int,
    val fps: Int = 30
)

val DALUR_EXPORT_PRESETS = listOf(
    ExportPreset("NINE_SIXTEEN_1080P_HEVC", "9:16", 1080, 1920, "HEVC", 12),
    ExportPreset("NINE_SIXTEEN_1080P_H264", "9:16", 1080, 1920, "H264", 12),
    ExportPreset("SQUARE_1080P_HEVC", "1:1", 1080, 1080, "HEVC", 10),
    ExportPreset("SQUARE_1080P_H264", "1:1", 1080, 1080, "H264", 10),
    ExportPreset("SIXTEEN_NINE_1080P_HEVC", "16:9", 1920, 1080, "HEVC", 12),
    ExportPreset("SIXTEEN_NINE_1080P_H264", "16:9", 1920, 1080, "H264", 12)
)

/** Pick the best export preset the device can actually encode. */
fun pickExportPreset(
    ratio: String,
    hevcSupported: Boolean,
    presets: List<ExportPreset> = DALUR_EXPORT_PRESETS
): ExportPreset {
    val candidates = presets.filter { it.ratio == ratio }
    require(candidates.isNotEmpty()) { "unknown ratio $ratio" }
    return if (hevcSupported) candidates.first { it.codec == "HEVC" }
    else candidates.first { it.codec == "H264" }
}
