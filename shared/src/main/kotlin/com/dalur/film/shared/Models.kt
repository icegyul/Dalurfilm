package com.dalur.film.shared

import kotlinx.serialization.Serializable

@Serializable
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val heading: Double? = null,
    val accuracyMeters: Float? = null
) {
    init {
        // (0,0) is a valid ocean coordinate but DALUR never invents coordinates:
        // absence is represented by null GpsPoint on the capture, not by zeros.
        require(latitude in -90.0..90.0) { "latitude out of range" }
        require(longitude in -180.0..180.0) { "longitude out of range" }
    }
}

@Serializable
data class CameraSettings(
    val lens: String = "back",
    val exposureCompensation: Float = 0f,
    val iso: Int? = null,
    val shutterSpeedSec: Double? = null,
    val whiteBalanceK: Int? = null,
    val tint: Float? = null,
    val focusDistance: Float? = null,
    val zoomRatio: Float = 1f,
    val stabilization: Boolean = false
)

@Serializable
data class CaptureMetadata(
    val mediaId: String,
    val timestampMillis: Long,
    val gps: GpsPoint? = null,
    val locationUnavailable: Boolean = false,
    val mediaType: String, // photo | video
    val mediaUri: String,
    val filmRecipeId: String? = null,
    val filmRecipeVersion: Int? = null,
    val camera: CameraSettings = CameraSettings(),
    val codec: String? = null, // HEVC | H264
    val colorProfile: String? = null, // SDR | HLG | HDR10 | LOG | FLAT
    val logProfile: String? = null, // genuine device profile id, null when unsupported
    val lutRecipeId: String? = null,
    val lutRecipeVersion: Int? = null,
    val lutHash: String? = null,
    val lutIntensity: Float? = null,
    /** true when the photo pipeline applied supported film tokens; false when it could not.
     *  null when no film was requested (e.g. the "None" recipe, or video where the master
     *  must stay untouched by design). NEVER assumed — reflects what actually ran. */
    val filmApplied: Boolean? = null,
    /** Non-null when applying supported film effects failed; the photo is still saved raw. */
    val filmError: String? = null,
    /** Which recipe components were actually applied vs. NOT_SUPPORTED by the renderer. */
    val filmSupportReport: FilmSupportReport? = null,
    /** Absolute path of the per-media `.dalur.json` sidecar (written by CaptureMetadataStore). */
    val sidecarUri: String? = null
)

/**
 * Honest report of what a film recipe's components SNAPSHOT the device renderer can do.
 * The photo pipeline applies only [applied]; every component in [notSupported] was
 * intentionally NOT injected. Presence here is the source of truth for "film was applied"
 * — it never claims PASS for a component the renderer cannot execute.
 */
@Serializable
data class FilmSupportReport(
    val applied: List<String> = emptyList(),
    val notSupported: List<String> = emptyList()
)

@Serializable
data class JourneyRoutePoint(
    val mediaId: String,
    val timestampMillis: Long,
    val gps: GpsPoint
)

@Serializable
data class Journey(
    val id: String,
    val title: String,
    val mediaIds: List<String>,
    val routePoints: List<JourneyRoutePoint>,
    val createdAtMillis: Long,
    val stylePreset: String = "MEMORY", // MEMORY | CINEMA | POSTCARD | JOURNAL
    val exportPreset: String = "NINE_SIXTEEN_1080P_HEVC" // see ExportPresets
)

/** Chronological ordering used by map route + journey renderer. */
fun orderCapturesChronologically(captures: List<CaptureMetadata>): List<CaptureMetadata> =
    captures.sortedWith(compareBy({ it.timestampMillis }, { it.mediaId }))

fun journeyRouteFromCaptures(
    journeyId: String,
    title: String,
    captures: List<CaptureMetadata>,
    stylePreset: String,
    exportPreset: String,
    createdAtMillis: Long
): Journey {
    val ordered = orderCapturesChronologically(captures).filter { it.gps != null }
    return Journey(
        id = journeyId,
        title = title,
        mediaIds = ordered.map { it.mediaId },
        routePoints = ordered.map {
            JourneyRoutePoint(it.mediaId, it.timestampMillis, requireNotNull(it.gps))
        },
        createdAtMillis = createdAtMillis,
        stylePreset = stylePreset,
        exportPreset = exportPreset
    )
}
