package com.dalur.film.film

import com.dalur.film.shared.FilmRecipe
import kotlin.math.pow

/**
 * Maps a [FilmRecipe] to a GPUImage Plus rule string for live preview and to a
 * CPU approximation used by the Journey exporter when GPU preview is unavailable.
 *
 * GPUImage Plus is used ONLY as a filter pipeline (rule-string effects). Camera
 * control stays in CameraX/Camera2 per the source-pack integration policy.
 */
object FilmEngine {
    /** Build a GPUImage Plus rule string. Intensity scales the effect chain. */
    fun ruleString(recipe: FilmRecipe, intensity: Float = recipe.intensity): String {
        val k = intensity.coerceIn(0f, 1f)
        if (k <= 0.001f) return ""
        val parts = mutableListOf<String>()
        // Tone
        val t = recipe.tone
        if (t.exposure != 0f) parts += "@adjust exposure ${(t.exposure * k)}"
        if (t.contrast != 0f) parts += "@adjust contrast ${1f + t.contrast * k}"
        // Color
        val c = recipe.color
        if (c.temperature != 0f || c.tint != 0f) {
            parts += "@adjust whitebalance ${c.temperature * k} ${c.tint * k}"
        }
        if (c.saturation != 0f) parts += "@adjust saturation ${1f + c.saturation * k}"
        // LUT marker: the actual 3D LUT is applied via a LUT filter stage where the
        // pipeline supports it; the rule keeps tone parity when it does not.
        recipe.lut?.let {
            parts += "@adjust lut ${it.source.substringAfterLast("/")} ${k}"
        }
        // Effects (documented approximations; never silently change the recipe)
        val e = recipe.effects
        if (e.grain > 0f) parts += "@adjust grain ${e.grain * k}"
        if (e.halation > 0f) parts += "@adjust halation ${e.halation * k}"
        if (e.bloom > 0f) parts += "@adjust bloom ${e.bloom * k}"
        if (e.vignette > 0f) parts += "@adjust vignette ${e.vignette * k}"
        return parts.joinToString(" ")
    }

    /** CPU approximation of the recipe for a single sRGB pixel (exporter fallback). */
    fun applyCpu(recipe: FilmRecipe, r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        var R = r; var G = g; var B = b
        val k = recipe.intensity
        val t = recipe.tone
        // exposure (stops)
        if (t.exposure != 0f) {
            val m = 2.0.pow((t.exposure * k).toDouble()).toFloat()
            R *= m; G *= m; B *= m
        }
        // contrast around 0.5
        if (t.contrast != 0f) {
            val m = 1f + t.contrast * k
            R = ((R - 0.5f) * m + 0.5f); G = ((G - 0.5f) * m + 0.5f); B = ((B - 0.5f) * m + 0.5f)
        }
        // saturation
        if (recipe.color.saturation != 0f) {
            val s = 1f + recipe.color.saturation * k
            val l = 0.299f * R + 0.587f * G + 0.114f * B
            R = l + (R - l) * s; G = l + (G - l) * s; B = l + (B - l) * s
        }
        // temperature: warm/cool balance
        if (recipe.color.temperature != 0f) {
            val w = recipe.color.temperature * k
            R += w * 0.08f; B -= w * 0.08f
        }
        return Triple(R.coerceIn(0f, 1f), G.coerceIn(0f, 1f), B.coerceIn(0f, 1f))
    }
}
