package com.dalur.film.film

import android.content.Context
import com.dalur.film.shared.CubeLut
import com.dalur.film.shared.FilmRecipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

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
        val out = mutableListOf<FilmRecipe>()
        // 1) Bundled DALUR-originals from assets.
        val assetRecipes = context.assets.list("recipes")?.toList().orEmpty()
        for (name in assetRecipes) {
            if (!name.endsWith(".json")) continue
            try {
                val text = context.assets.open("recipes/$name").bufferedReader().readText()
                val recipe = json.decodeFromString<FilmRecipe>(text)
                // Validate bundled LUT hash against the shipped .cube.
                val lutRef = recipe.lut ?: continue
                val lutName = lutRef.source.substringAfterLast("/")
                try {
                    val bytes = context.assets.open("luts/$lutName").readBytes()
                    val actual = CubeLut.sha256Hex(bytes)
                    if (!actual.equals(lutRef.hash, ignoreCase = true)) continue
                    CubeLut.parse(bytes.toString(Charsets.UTF_8))
                } catch (_: Exception) { continue }
                out += recipe
                // Persist a copy for user editing (duplicate/edit never mutates the asset).
                val target = File(dir(), "${recipe.id}.v${recipe.version}.json")
                if (!target.exists()) target.writeText(text)
            } catch (_: Exception) { /* skip invalid bundled entry */ }
        }
        // 2) Local user recipes (including duplicates/edits).
        for (f in dir().listFiles()?.toList().orEmpty()) {
            if (!f.name.endsWith(".json")) continue
            try {
                val recipe = json.decodeFromString<FilmRecipe>(f.readText())
                if (out.none { it.id == recipe.id && it.version == recipe.version }) out += recipe
            } catch (_: Exception) { /* skip corrupt local file */ }
        }
        _recipes.value = out.sortedBy { it.name }
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
