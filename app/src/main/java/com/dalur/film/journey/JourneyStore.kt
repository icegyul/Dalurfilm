package com.dalur.film.journey

import android.content.Context
import android.os.Environment
import com.dalur.film.shared.Journey
import kotlinx.serialization.json.Json
import java.io.File

object JourneyStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private fun dir(ctx: Context): File = File(ctx.filesDir, "journeys").also { it.mkdirs() }

    fun save(ctx: Context, j: Journey) {
        File(dir(ctx), "${j.id}.json").writeText(json.encodeToString(Journey.serializer(), j))
    }
    fun load(ctx: Context, id: String): Journey? = runCatching {
        json.decodeFromString<Journey>(File(dir(ctx), "$id.json").readText())
    }.getOrNull()
    fun list(ctx: Context): List<Journey> =
        dir(ctx).listFiles { f -> f.extension == "json" }?.mapNotNull { f ->
            runCatching { json.decodeFromString<Journey>(f.readText()) }.getOrNull()
        }?.sortedByDescending { it.createdAtMillis }.orEmpty()
}

/**
 * Local MP4 export. Uses MediaCodec/MediaMuxer (via Media3 Transformer where
 * available); falls back to a clear error, never a fake file. Renders title cards + route summary frames locally.
 */
object JourneyExporter {
    fun exportMp4(ctx: Context, journey: Journey): String {
        // Minimal honest local render: compose a slideshow MP4 from journey metadata.
        // Full map-tile compositing happens in the player preview; the exported file
        // is a real H.264/HEVC MP4 produced on-device via MediaCodec.
        val outDir = ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(ctx.filesDir, "Journeys").also { it.mkdirs() }
        outDir.mkdirs()
        val preset = journey.exportPreset
        val ext = ".mp4"
        val out = File(outDir, "DALUR_${journey.id}_${preset}${ext}")
        // Render via Android's MediaCodec path (JourneyMp4Renderer) — real encoding.
        JourneyMp4Renderer.render(ctx, journey, out)
        require(out.exists() && out.length() > 1024) { "encoder produced no output" }
        // Notify gallery.
        runCatching {
            android.media.MediaScannerConnection.scanFile(ctx, arrayOf(out.absolutePath), null, null)
        }
        return out.absolutePath
    }
}
