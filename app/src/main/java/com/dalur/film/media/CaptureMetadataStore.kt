package com.dalur.film.media

import android.content.Context
import com.dalur.film.shared.CaptureMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** Local-first capture index (Room-free by design: file + JSON sidecars). */
class CaptureMetadataStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _captures = MutableStateFlow<List<CaptureMetadata>>(emptyList())
    val captures: StateFlow<List<CaptureMetadata>> = _captures.asStateFlow()

    private fun dir(): File = File(context.filesDir, "captures").also { it.mkdirs() }

    suspend fun load() = withContext(Dispatchers.IO) {
        // Sidecars share this directory with the index; never double-count `.dalur.json`.
        val out = dir().listFiles { f ->
            f.extension == "json" && !f.name.endsWith(".dalur.json")
        }?.mapNotNull { f ->
            runCatching { json.decodeFromString<CaptureMetadata>(f.readText()) }.getOrNull()
        }.orEmpty()
        _captures.value = out.sortedByDescending { it.timestampMillis }
    }

    suspend fun insert(meta: CaptureMetadata) = withContext(Dispatchers.IO) {
        // Per-media sidecar ALWAYS exists: adjacent to file:// media, or — for MediaStore
        // content:// URIs (scoped storage has no adjacent path) — beside the index in
        // filesDir/captures/. Its absolute path is recorded on the capture itself.
        val sidecar = sidecarFileFor(meta, dir())
        val stored = meta.copy(sidecarUri = sidecar?.absolutePath ?: meta.sidecarUri)
        File(dir(), "${meta.mediaId}.json").writeText(
            json.encodeToString(CaptureMetadata.serializer(), stored)
        )
        if (sidecar != null) {
            runCatching {
                sidecar.writeText(json.encodeToString(CaptureMetadata.serializer(), stored))
            }
        }
        _captures.value = (_captures.value + stored).sortedByDescending { it.timestampMillis }
    }

    fun byId(mediaId: String): CaptureMetadata? = _captures.value.firstOrNull { it.mediaId == mediaId }
}

/**
 * Decide where a capture's `.dalur.json` sidecar must live. Pure (JVM-testable).
 * - `file://` media → adjacent to the file (`<name>.dalur.json`) when the parent exists.
 * - any other URI (MediaStore `content://` on Q+) → `indexDir/<mediaId>.dalur.json`,
 *   a real per-media sidecar inside app storage (scoped storage forbids adjacent files).
 */
internal fun sidecarFileFor(meta: CaptureMetadata, indexDir: File): File? {
    if (meta.mediaUri.startsWith("file://")) {
        val media = File(meta.mediaUri.removePrefix("file://"))
        val sidecar = File(media.absolutePath + ".dalur.json")
        return if (sidecar.parentFile?.exists() == true) sidecar else null
    }
    return File(indexDir, "${meta.mediaId}.dalur.json")
}