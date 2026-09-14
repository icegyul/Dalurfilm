package com.dalur.film.shared

import kotlinx.serialization.Serializable

/** Versioned DALUR Film Recipe. Backward compatible via [version]. */
@Serializable
data class FilmRecipe(
    val id: String,
    val version: Int = 1,
    val name: String,
    val description: String = "",
    val creatorType: String = "dalur", // dalur | local
    /** Browse grouping tab: Signature | Vivid | People | Natural | Classic | Low Light | Special | General. */
    val category: String = "General",
    val lut: LutRef? = null,
    val intensity: Float = 0.85f,
    val tone: Tone = Tone(),
    val color: ColorAdjust = ColorAdjust(),
    val effects: Effects = Effects(),
    val frame: FrameTreatment? = null
) {
    init {
        require(id.matches(Regex("[a-z0-9_]{3,64}"))) { "bad recipe id: $id" }
        require(version >= 1) { "version must be >= 1" }
        require(intensity in 0f..1f) { "intensity out of range" }
    }
}

@Serializable
data class LutRef(
    /** "cube" only in v1. */
    val format: String = "cube",
    val size: Int = 33,
    /** e.g. bundled://luts/dalur_memory_01.cube */
    val source: String,
    /** sha256 hex of the .cube file bytes (lowercase). */
    val hash: String,
    /** creative | technical | unknown */
    val kind: String = "creative"
)

@Serializable
data class Tone(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val fade: Float = 0f,
    val blacks: Float = 0f,
    val whites: Float = 0f
)

@Serializable
data class ColorAdjust(
    val temperature: Float = 0f, // -1..1
    val tint: Float = 0f, // -1..1
    val saturation: Float = 0f, // -1..1 delta
    val hsl: List<HslRow> = emptyList()
)

@Serializable
data class HslRow(val hue: Float, val saturationDelta: Float, val lightnessDelta: Float)

@Serializable
data class Effects(
    val grain: Float = 0.0f,
    val grainSize: Float = 0.5f,
    val halation: Float = 0.0f,
    val bloom: Float = 0.0f,
    val vignette: Float = 0.0f,
    val chromaticAberration: Float = 0.0f,
    val lightLeak: LightLeak? = null
)

@Serializable
data class LightLeak(val strength: Float = 0f, val hue: Float = 0.08f, val position: Float = 0.85f)

@Serializable
data class FrameTreatment(val enabled: Boolean = false, val showDate: Boolean = false)

/** Migrate a recipe JSON map forward. Currently v1 is current; unknown future versions pass through. */
fun migrateRecipe(map: Map<String, Any?>, fromVersion: Int): Map<String, Any?> {
    if (fromVersion < 1) throw IllegalArgumentException("unsupported recipe version $fromVersion")
    return map // v1 stable; future migrations chain here
}
