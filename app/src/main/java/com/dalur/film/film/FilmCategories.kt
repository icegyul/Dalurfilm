package com.dalur.film.film

import com.dalur.film.shared.FilmRecipe

/**
 * Film-library browsing categories. Determined from the recipe `category` field
 * (DALUR bundles are fully categorized; user/local recipes default to "General").
 * Tab order is a fixed signature order first, then any extra categories A–Z.
 */
val FILM_CATEGORY_ORDER: List<String> = listOf(
    "Signature", "Vivid", "People", "Natural", "Classic", "Low Light", "Special"
)

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