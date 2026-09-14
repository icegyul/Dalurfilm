package com.dalur.film.film

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dalur.film.DalurApp
import com.dalur.film.camera.CameraViewModel
import com.dalur.film.shared.FilmRecipe
import com.dalur.film.ui.components.DalurHeader
import com.dalur.film.ui.components.FilmCategoryRow
import com.dalur.film.ui.components.filmTint
import kotlinx.coroutines.launch

@Composable
fun FilmsScreen(vm: CameraViewModel) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as DalurApp
    val recipes by vm.filmRecipes.collectAsState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<FilmRecipe?>(null) }
    var intensity by remember { mutableStateOf(0.85f) }
    val categories = remember(recipes) { filmCategoryTabs(recipes) }
    var activeCategory by remember { mutableStateOf("All") }
    val visible = remember(recipes, activeCategory) { filmsForCategory(recipes, activeCategory) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        DalurHeader(
            title = "Films",
            subtitle = "DALUR-original recipes. Tap to preview live in Camera."
        )
        Spacer(Modifier.height(12.dp))
        FilmCategoryRow(
            categories = categories,
            selected = activeCategory,
            onSelect = { activeCategory = it }
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            items(visible) { r ->
                val selected = editing?.id == r.id
                ElevatedCard(
                    onClick = {
                        editing = r; intensity = r.intensity
                        vm.selectFilm(r.id)
                    },
                    modifier = Modifier.fillMaxWidth().then(
                        if (selected) Modifier.border(
                            2.dp, MaterialTheme.colorScheme.primary,
                            MaterialTheme.shapes.medium)
                        else Modifier
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column {
                        FilmSwatch(r.id)
                        Column(Modifier.padding(12.dp)) {
                            Text(r.name, style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(2.dp))
                            Text("v${r.version} · ${(r.intensity * 100).toInt()}%",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(r.description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                    }
                }
            }
        }
        editing?.let { r ->
            Spacer(Modifier.height(12.dp))
            Card(shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp)) {
                    Text("Preview: ${r.name}", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(value = intensity, onValueChange = { intensity = it },
                            modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text("${(intensity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Row {
                        Button(onClick = {
                            val cur = editing ?: return@Button
                            scope.launch { app.films.save(cur.copy(intensity = intensity)) }
                        }) { Text("Save") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { editing = null }) { Text("Close") }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            val cur = editing ?: return@IconButton
                            scope.launch { app.films.duplicate(cur) }
                        }) {
                            Icon(Icons.Filled.ContentCopy, "duplicate")
                        }
                        IconButton(onClick = {
                            val cur = editing ?: return@IconButton
                            scope.launch { app.films.resetToBundled(cur.id) }
                        }) {
                            Icon(Icons.Filled.Refresh, "reset")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Easy: Warm/Cool · Bright/Dark · Grain · Glow map to the recipe tone/color/effects. " +
                        "PRO adds curves, HSL, halation, bloom, vignette, LUT strength.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun FilmSwatch(id: String) {
    val c = filmTint(id)
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().height(110.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        c,
                        c.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
    )
}
