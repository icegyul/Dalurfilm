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
        val out = dir().listFiles { f -> f.extension == "json" }?.mapNotNull { f ->
            runCatching { json.decodeFromString<CaptureMetadata>(f.readText()) }.getOrNull()
        }.orEmpty()
        _captures.value = out.sortedByDescending { it.timestampMillis }
    }

    suspend fun insert(meta: CaptureMetadata) = withContext(Dispatchers.IO) {
        File(dir(), "${meta.mediaId}.json").writeText(
            json.encodeToString(CaptureMetadata.serializer(), meta)
        )
        // Sidecar next to the media file when the media URI is a DALUR file.
        runCatching {
            val mediaPath = meta.mediaUri.removePrefix("file://")
            val sidecar = File("$mediaPath.dalur.json")
            if (sidecar.parentFile?.exists() == true) {
                sidecar.writeText(json.encodeToString(CaptureMetadata.serializer(), meta))
            }
        }
        _captures.value = (_captures.value + meta).sortedByDescending { it.timestampMillis }
    }

    fun byId(mediaId: String): CaptureMetadata? = _captures.value.firstOrNull { it.mediaId == mediaId }
}
