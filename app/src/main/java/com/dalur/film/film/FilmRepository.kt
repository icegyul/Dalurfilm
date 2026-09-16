package com.dalur.film.film

import android.content.Context
import com.dalur.film.shared.CubeLut
import com.dalur.film.shared.FilmRecipe
import com.dalur.film.shared.LutRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** On-disk verify cache: asset fingerprint + per-LUT proven hashes. */
@Serializable
private data class VerifyStamp(
    val fp: String = "",
    val verified: Map<String, String> = emptyMap(),
)

/**
 * Local-only film store (Phase 1). Marketplace abstractions are interfaces only;
 * no network, no accounts, no transactions.
 */
class FilmRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _recipes = MutableStateFlow<List<FilmRecipe>>(emptyList())
    val recipes: StateFlow<List<FilmRecipe>> = _recipes.asStateFlow()

    private fun dir(): File = File(context.filesDir, "films").also { it.mkdirs() }

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        // 1) Fast path first: publish local copies instantly.
        val localAll = loadLocal()
        _recipes.value = localAll.sortedBy { it.name }
        val fp = fingerprint()
        // Verified-hash cache: skip re-parsing cubes already proven good.
        // (Hashing 9MB is ~1s; parsing 460k lines is the slow part.)
        val stampFile = File(dir(), ".seed_fp_v2.json")
        val verified: MutableMap<String, String> = try {
            val s = stampFile.readText()
            if (s.contains(fp)) {
                json.decodeFromString<VerifyStamp>(s).verified.toMutableMap()
            } else mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
        fun saveStamp() {
            try {
                stampFile.writeText(
                    json.encodeToString(
                        VerifyStamp.serializer(),
                        VerifyStamp(fp, verified.toMap())))
            } catch (_: Exception) { /* best-effort */ }
        }
        // 2) Verify bundled assets. Semantics unchanged: verified bundled
        //    id+version wins over a local edit. Publishes incrementally so
        //    the UI converges even if the run is interrupted.
        val bundled = mutableListOf<FilmRecipe>()
        val assetRecipes = context.assets.list("recipes")?.toList().orEmpty()
        for ((idx, name) in assetRecipes.withIndex()) {
            if (!name.endsWith(".json")) continue
            try {
                val text = context.assets.open("recipes/$name").bufferedReader().readText()
                val recipe = json.decodeFromString<FilmRecipe>(text)
                val lutRef = recipe.lut ?: continue
                val lutName = lutRef.source.substringAfterLast("/")
                try {
                    val bytes = context.assets.open("luts/$lutName").readBytes()
                    val actual = CubeLut.sha256Hex(bytes)
                    if (!actual.equals(lutRef.hash, ignoreCase = true)) continue
                    if (verified[lutName] != actual) {
                        CubeLut.parse(bytes.toString(Charsets.UTF_8))
                        verified[lutName] = actual
                    }
                } catch (_: Exception) { continue }
                bundled += recipe
                // Keep the disk copy fresh (bundled wins in memory).
                try {
                    File(dir(), "${recipe.id}.v${recipe.version}.json").writeText(text)
                } catch (_: Exception) { /* best-effort */ }
                val bundledKeys = bundled.map { it.id to it.version }.toSet()
                val locals = localAll.filter { (it.id to it.version) !in bundledKeys }
                // Purge orphaned DALUR copies: bundled ids removed from assets must
                // disappear (user customs have creatorType "local" and are kept).
                val bundledIds = bundled.map { it.id }.toSet()
                for (r in localAll) {
                    if (r.creatorType == "dalur" && r.id !in bundledIds) {
                        dir().listFiles { f -> f.name.startsWith("${r.id}.v") }
                            ?.forEach { runCatching { it.delete() } }
                    }
                }
                _recipes.value = (bundled + locals.filter {
                    !(it.creatorType == "dalur" && it.id !in bundledIds)
                }).sortedBy { it.name }
                if (idx % 10 == 9) saveStamp()
            } catch (_: Exception) { /* skip invalid bundled entry */ }
        }
        saveStamp()
    }

    private fun loadLocal(): List<FilmRecipe> =
        dir().listFiles()
            ?.filter { it.name.endsWith(".json") }
            ?.mapNotNull { f ->
                try { json.decodeFromString<FilmRecipe>(f.readText()) }
                catch (_: Exception) { null }
            }.orEmpty()

    /** Cheap asset fingerprint (names + byte lengths) for the verify stamp. */
    private fun fingerprint(): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        for (d in listOf("recipes", "luts")) {
            for (n in context.assets.list(d)?.sorted().orEmpty()) {
                md.update("$d/$n:".toByteArray())
                try {
                    context.assets.openFd("$d/$n").use {
                        md.update(it.length.toString().toByteArray())
                    }
                } catch (_: Exception) {
                    md.update("?".toByteArray())
                }
                md.update(";".toByteArray())
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun importedLutsDir(): File = File(context.filesDir, "imported_luts").also { it.mkdirs() }

    /**
     * Import a user-picked .cube file as a new local recipe (neutral tone/color —
     * nothing invented). Validates it's a real 3D LUT before touching disk.
     *
     * NOTE: the LUT is stored and the recipe is created, but [FilmEngine] does not
     * apply any .cube LUT yet (bundled or imported) — the render engine only takes
     * a 512x512 PNG table and none has been generated. Callers must surface that
     * honestly; this function does not pretend otherwise.
     */
    suspend fun importCubeLut(uri: android.net.Uri, displayName: String): FilmRecipe =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw java.io.IOException("could not open $uri")
            val text = bytes.toString(Charsets.UTF_8)
            val parsed = CubeLut.parse(text) // throws IllegalArgumentException on invalid LUTs
            val hash = CubeLut.sha256Hex(bytes)
            val baseName = displayName.substringBeforeLast(".").ifBlank { "lut" }
            val safeId = ("imported_" + baseName.lowercase().replace(Regex("[^a-z0-9_]"), "_"))
                .take(50) + "_" + hash.take(6)
            val lutFile = File(importedLutsDir(), "$safeId.cube")
            lutFile.writeBytes(bytes)
            val recipe = FilmRecipe(
                id = safeId,
                name = parsed.title?.takeIf { it.isNotBlank() } ?: baseName,
                description = "가져온 LUT ($displayName) · 아직 사진/영상에는 적용되지 않습니다.",
                creatorType = "local",
                category = "General",
                priceTier = "free",
                lut = LutRef(
                    format = "cube",
                    size = parsed.size,
                    source = "local://imported_luts/$safeId.cube",
                    hash = hash,
                    kind = "creative"
                ),
                intensity = 1f
            )
            save(recipe)
            recipe
        }

    suspend fun save(recipe: FilmRecipe) = withContext(Dispatchers.IO) {
        val target = File(dir(), "${recipe.id}.v${recipe.version}.json")
        target.writeText(json.encodeToString(FilmRecipe.serializer(), recipe))
        val others = _recipes.value.filterNot { it.id == recipe.id && it.version == recipe.version }
        _recipes.value = (others + recipe).sortedBy { it.name }
    }

    suspend fun duplicate(recipe: FilmRecipe): FilmRecipe = withContext(Dispatchers.IO) {
        val copy = recipe.copy(
            id = "${recipe.id}_copy_${UUID.randomUUID().toString().take(4)}".lowercase()
                .replace(Regex("[^a-z0-9_]"), "_"),
            version = 1,
            name = "${recipe.name} Copy",
            creatorType = "local"
        )
        save(copy)
        copy
    }

    suspend fun resetToBundled(recipeId: String): FilmRecipe? = withContext(Dispatchers.IO) {
        val base = recipeId.substringBefore("_copy_")
        val asset = try {
            context.assets.open("recipes/$base.json").bufferedReader().readText()
        } catch (_: Exception) { return@withContext null }
        val recipe = json.decodeFromString<FilmRecipe>(asset)
        // Remove local overrides for this id family, restore bundled.
        dir().listFiles { f -> f.name.startsWith(recipeId) }?.forEach { it.delete() }
        save(recipe)
        recipe
    }

    /** Portable recipe package for FUTURE marketplace integration (no network in v1). */
    fun exportPackage(recipe: FilmRecipe): String =
        json.encodeToString(FilmRecipe.serializer(), recipe)
}
