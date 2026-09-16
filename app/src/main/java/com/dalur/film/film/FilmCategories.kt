package com.dalur.film.film

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dalur.film.R
import com.dalur.film.shared.FilmRecipe

/**
 * Film-library browsing categories. Determined from the recipe `category` field
 * (DALUR bundles are fully categorized; user/local recipes default to "General").
 * Tab order is a fixed signature order first, then any extra categories A–Z.
 */
val FILM_CATEGORY_ORDER: List<String> = listOf(
    "Film", "Music Video", "Drama", "Vivid", "Signature", "Natural",
    "Classic", "Cinema", "Low Light", "Special", "People", "General"
)

/** Display label per category (device language). */
@Composable
fun categoryLabel(c: String): String = when (c) {
    "All" -> stringResource(R.string.cat_all)
    "Film" -> stringResource(R.string.cat_film)
    "Music Video" -> stringResource(R.string.cat_mv)
    "Drama" -> stringResource(R.string.cat_drama)
    "Vivid" -> stringResource(R.string.cat_vivid)
    "Signature" -> stringResource(R.string.cat_signature)
    "Natural" -> stringResource(R.string.cat_natural)
    "Classic" -> stringResource(R.string.cat_classic)
    "Cinema" -> stringResource(R.string.cat_cinema)
    "Low Light" -> stringResource(R.string.cat_lowlight)
    "Special" -> stringResource(R.string.cat_special)
    "People" -> stringResource(R.string.cat_people)
    "General" -> stringResource(R.string.cat_general)
    else -> c
}

/** Category tabs: [All] + every category present in [recipes], priority-ordered then A–Z. */
fun filmCategoryTabs(recipes: List<FilmRecipe>): List<String> {
    val present = recipes.map { it.category.ifBlank { "General" } }.toSet()
    val ordered = FILM_CATEGORY_ORDER.filter { it in present }
    val extra = (present - FILM_CATEGORY_ORDER.toSet()).sorted()
    return listOf("All") + ordered + extra
}

/** Recipes shown under a tab: everything for [All], otherwise exact category match. */
fun filmsForCategory(recipes: List<FilmRecipe>, category: String): List<FilmRecipe> =
    if (category == "All") recipes
    else recipes.filter { it.category.ifBlank { "General" } == category }