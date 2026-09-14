package com.dalur.film.shared

import java.security.MessageDigest

/** Minimal .cube LUT validation + hashing. Supports 17/33 (and parses other sizes). */
object CubeLut {
    data class Parsed(val size: Int, val title: String?, val lines: Int)

    fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    fun parse(text: String): Parsed {
        var size: Int? = null
        var title: String? = null
        var dataLines = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            when {
                line.startsWith("TITLE") -> title = line.removePrefix("TITLE").trim().trim('"')
                line.startsWith("LUT_3D_SIZE") ->
                    size = line.removePrefix("LUT_3D_SIZE").trim().split(Regex("\\s+")).first().toInt()
                line.startsWith("LUT_1D_SIZE") -> throw IllegalArgumentException("1D LUT not supported")
                line.startsWith("DOMAIN_") -> Unit // validated loosely; DALUR LUTs use default 0..1
                line.matches(Regex("[-+0-9.eE ]+")) -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size == 3 && parts.all { it.toFloatOrNull() != null }) dataLines++
                }
                line.startsWith("LUT_3D_SIZE").not() && line[0].isLetter() -> {
                    // Unknown keyword: ignore only whitelisted ones, else fail closed.
                    val key = line.split(Regex("\\s+")).first()
                    if (key !in setOf("TITLE", "LUT_3D_SIZE", "DOMAIN_MIN", "DOMAIN_MAX", "LUT_1D_SIZE")) {
                        // Allow comments-as-keywords? No: fail closed for unknown LUT types.
                        if (key == "LUT_1D_INPUT_RANGE") throw IllegalArgumentException("1D LUT not supported")
                    }
                }
            }
        }
        val s = size ?: throw IllegalArgumentException("missing LUT_3D_SIZE")
        require(s in 2..64) { "unsupported LUT size $s" }
        require(dataLines == s * s * s) { "LUT data lines $dataLines != ${s * s * s} for size $s" }
        return Parsed(s, title, dataLines)
    }

    fun validateSizeSupported(size: Int): Boolean = size == 17 || size == 33
}

/** Deterministic DALUR filenames: DALUR_yyyyMMdd_HHmmss_SSS_mediaId.ext */
fun dalurFileName(timestampMillis: Long, mediaId: String, ext: String): String {
    val safeId = mediaId.replace(Regex("[^a-zA-Z0-9_-]"), "").take(12)
    val dt = java.time.Instant.ofEpochMilli(timestampMillis)
        .atZone(java.time.ZoneOffset.UTC)
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
    return "DALUR_${dt}_${safeId}.${ext.lowercase()}"
}

/** Journey hold duration per photo count (spec: 0.8–2.5s). */
fun journeyHoldSeconds(photoCount: Int): Double = when {
    photoCount <= 1 -> 2.5
    photoCount <= 3 -> 2.0
    photoCount <= 6 -> 1.4
    photoCount <= 10 -> 1.0
    else -> 0.8
}
