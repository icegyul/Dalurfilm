package com.dalur.film.film

import com.dalur.film.shared.FilmRecipe
import com.dalur.film.shared.FilmSupportReport
import kotlin.math.pow

/**
 * Maps a [FilmRecipe] to a GPUImage Plus rule string for live preview and to a
 * CPU approximation used by the Journey exporter when GPU preview is unavailable.
 *
 * HONESTY CONTRACT (no fake PASS): the rule string only ever contains tokens the
 * CGE rule engine can actually execute. Every recipe component outside that set is
 * classified NOT_SUPPORTED in the returned [FilmSupportReport] and is NEVER injected
 * into the rule string. The capture metadata records report + filmApplied so the
 * index/sidecar can state exactly what ran.
 *
 * CGE-supported tokens (per the engine reference): brightness / contrast / saturation /
 * monochrome / sharpen / blur / whitebalance / shadowhighlight / hsv / hsl / level /
 * exposure / colorbalance / lut (512x512 PNG only). The DALUR pipeline uses only:
 * exposure, contrast, saturation, whitebalance (Kelvin + tint[0..5], 1 = neutral).
 *
 * NOT_SUPPORTED by design (emitted nowhere, reported honestly):
 *   - lut (recipes ship `.cube`; the engine needs a 512x512 PNG lookup — no LUT PNG is
 *     vendored), grain, halation, bloom (no CGE token exist),
 *   - vignette (CGE takes a range token `@vignette low|mid|high`, not the recipe's float),
 *   - chromaticAberration / lightLeak (no CGE token),
 *   - tone highlights/shadows/fade/blacks/whites and color.hsl (CGE shapes differ from
 *     the recipe values, so mapping would be invented).
 *
 * GPUImage Plus is used ONLY as a filter pipeline (rule-string effects). Camera
 * control stays in CameraX/Camera2 per the source-pack integration policy.
 */
object FilmEngine {

    /**
     * Full plan for a capture: the exact rule string to run and the honest support report.
     */
    fun plan(recipe: FilmRecipe, intensity: Float = recipe.intensity): FilmApplyResult {
        val applied = mutableListOf<String>()
        val notSupported = mutableListOf<String>()
        val parts = mutableListOf<String>()

        val k = intensity.coerceIn(0f, 1f)
        if (k > 0.001f) {
            // Tone — exposure/contrast supported.
            val t = recipe.tone
            if (t.exposure != 0f) {
                parts += "@adjust exposure ${t.exposure * k}"
                applied += "exposure"
            }
            if (t.contrast != 0f) {
                parts += "@adjust contrast ${1f + t.contrast * k}"
                applied += "contrast"
            }
            // Color — saturation + whitebalance (Kelvin + tint in CGE [0,5] range, 1 = neutral).
            val c = recipe.color
            if (c.saturation != 0f) {
                parts += "@adjust saturation ${1f + c.saturation * k}"
                applied += "saturation"
            }
            if (c.temperature != 0f || c.tint != 0f) {
                // Positive recipe temperature = warmer → lower Kelvin (4500K warm … 8500K cool).
                val kelvin = 6500f - (c.temperature * k) * 2000f
                // Recipe tint -1..1 → CGE tint [0,5]; 0 stays neutral (1).
                val tint = 1f - c.tint * k
                parts += "@adjust whitebalance $kelvin $tint"
                applied += "whitebalance"
            }
        }

        // ---- NOT_SUPPORTED inventory (never emitted; reported verbatim) ----
        if (recipe.lut != null) notSupported += "lut"
        val e = recipe.effects
        if (e.grain > 0f) notSupported += "grain"
        if (e.halation > 0f) notSupported += "halation"
        if (e.bloom > 0f) notSupported += "bloom"
        if (e.vignette > 0f) notSupported += "vignette"
        if (e.chromaticAberration > 0f) notSupported += "chromaticAbberation"
        if ((e.lightLeak?.strength ?: 0f) > 0f) notSupported += "lightLeak"
        val t = recipe.tone
        if (t.highlights > 0f) notSupported += "highlights"
        if (t.shadows > 0f) notSupported += "shadows"
        if (t.fade > 0f) notSupported += "fade"
        if (t.blacks > 0f) notSupported += "blacks"
        if (t.whites > 0f) notSupported += "whites"
        if (recipe.color.hsl.isNotEmpty()) notSupported += "hsl"

        return FilmApplyResult(
            rule = parts.joinToString(" "),
            report = FilmSupportReport(
                applied = applied.sorted(),
                notSupported = notSupported.sorted()
            )
        )
    }

    /** Build a GPUImage Plus rule string (only supported tokens — see [plan]). */
    fun ruleString(recipe: FilmRecipe, intensity: Float = recipe.intensity): String =
        plan(recipe, intensity).rule

    /** Support report for the recipe at the given intensity. */
    fun supportReport(recipe: FilmRecipe, intensity: Float = recipe.intensity): FilmSupportReport =
        plan(recipe, intensity).report

    /** CPU approximation of the SUPPORTED component subset for a single sRGB pixel
     *  (exporter fallback). Unsupported components are deliberately ignored — never faked. */
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

data class FilmApplyResult(
    /** Rule string containing only executable CGE tokens; empty when there is nothing supported. */
    val rule: String,
    val report: FilmSupportReport
)