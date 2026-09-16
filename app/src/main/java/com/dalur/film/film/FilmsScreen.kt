package com.dalur.film.film

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dalur.film.DalurApp
import com.dalur.film.R
import com.dalur.film.camera.CameraViewModel
import com.dalur.film.shared.FilmRecipe
import com.dalur.film.ui.components.DalurHeader
import com.dalur.film.ui.components.FilmCategoryRow
import com.dalur.film.ui.components.previewColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun priceText(r: FilmRecipe): String =
    if (r.priceTier == "premium") "₩${r.priceKrw ?: 0}" else stringResource(R.string.films_free)

@Composable
fun FilmsScreen(vm: CameraViewModel, onApplyRecipe: (String) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as DalurApp
    val recipes by vm.filmRecipes.collectAsState()
    val owned by app.settings.ownedRecipes.collectAsState(emptySet())
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<FilmRecipe?>(null) }
    var buying by remember { mutableStateOf<FilmRecipe?>(null) }
    var confirmPay by remember { mutableStateOf<FilmRecipe?>(null) }
    val categories = remember(recipes) { filmCategoryTabs(recipes) }
    var activeCategory by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    var ownedOnly by remember { mutableStateOf(false) }
    val visible = remember(recipes, activeCategory, query, ownedOnly) {
        filmsForCategory(recipes, activeCategory)
            .filter { r ->
                query.isBlank() ||
                    r.name.contains(query, ignoreCase = true) ||
                    (r.styleName?.contains(query, ignoreCase = true) == true) ||
                    (r.inspiredBy?.contains(query, ignoreCase = true) == true) ||
                    r.tags.any { it.contains(query, ignoreCase = true) }
            }
            .filter { r ->
                !ownedOnly || r.priceTier != "premium" || r.id in owned
            }
    }
    // Preview base: user's latest photo if any, else a neutral mock scene.
    val captures by vm.allCaptures.collectAsState()
    val sampleUri = remember(captures) {
        captures.firstOrNull { it.mediaType == "photo" }?.mediaUri
    }
    val sample = rememberSampleBitmap(ctx, sampleUri)

    // Import a user's own .cube LUT as a new local recipe. Stored + validated now;
    // FilmEngine doesn't apply any .cube LUT yet (bundled or imported) — the
    // imported recipe's description says so honestly until that lands.
    val importLutLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        var displayName = uri.lastPathSegment ?: "lut.cube"
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) displayName = c.getString(idx)
            }
        } catch (_: Exception) { /* fall back to the uri segment */ }
        scope.launch {
            try {
                val recipe = app.films.importCubeLut(uri, displayName)
                Toast.makeText(ctx,
                    "\"${recipe.name}\" 가져옴 — 아직 실제 촬영엔 적용 안 됨",
                    Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "LUT 가져오기 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun applyRecipe(r: FilmRecipe) {
        // Premium without license goes to purchase (유도); free/owned applies.
        if (r.priceTier == "premium" && r.id !in owned) {
            buying = r
            return
        }
        vm.selectFilm(r.id)
        detail = null
        onApplyRecipe(r.id)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            DalurHeader(
                title = "Films",
                subtitle = stringResource(R.string.films_subtitle)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.films_search_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { importLutLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.Upload, "import LUT (.cube)",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BadgedBox(badge = {
                    if (owned.isNotEmpty()) {
                        Badge { Text("${owned.size}") }
                    }
                }) {
                    IconButton(onClick = { ownedOnly = !ownedOnly }) {
                        Icon(Icons.Filled.ShoppingBag, stringResource(R.string.films_owned_badge),
                            tint = if (ownedOnly) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            FilmCategoryRow(
                categories = categories,
                selected = activeCategory,
                onSelect = { activeCategory = it }
            )
            Spacer(Modifier.height(4.dp))
            Text(if (sample != null) stringResource(R.string.films_preview_mine)
                else stringResource(R.string.films_preview_sample),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(visible) { r ->
                    val isOwned = r.priceTier != "premium" || r.id in owned
                    val hasPoster = !r.posterUrl.isNullOrBlank()
                    // Full-bleed poster card: image fills the whole tile, everything
                    // else rides on a bottom gradient scrim (no separate text panel).
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(0.72f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { detail = r }
                    ) {
                        if (hasPoster) {
                            coil.compose.AsyncImage(
                                model = r.posterUrl, contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else if (sample != null) {
                            Image(sample, contentDescription = null, modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop)
                        } else {
                            MockScene(Modifier.fillMaxSize())
                        }
                        val tint = previewColor(r)
                        val wash = (0.12f + 0.33f * r.intensity).coerceIn(0f, 0.5f) *
                            if (hasPoster) 0.45f else 1f
                        Box(Modifier.fillMaxSize().background(tint.copy(alpha = wash)))
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .background(Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))) {
                            Column(Modifier.padding(12.dp)) {
                                Text(categoryLabel(r.category),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(2.dp))
                                Text(r.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White, maxLines = 1)
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(priceText(r) + if (!isOwned) " · 🔒" else "",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.weight(1f))
                                    // Quick apply (+) — premium without license goes to purchase.
                                    Box(Modifier.size(30.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable { applyRecipe(r) },
                                        contentAlignment = Alignment.Center) {
                                        Text("+", color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- Detail overlay: 설명 + 적용/구매 ----
        detail?.let { r ->
            val isOwned = r.priceTier != "premium" || r.id in owned
            var intensity by remember(r) { mutableStateOf(r.intensity) }
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { detail = null }) { Text(stringResource(R.string.close)) }
                        Spacer(Modifier.weight(1f))
                        Text(r.category.uppercase(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    FilmPreview(recipe = r.copy(intensity = intensity),
                        sample = sample, posterTitle = true, height = 240.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(r.name, style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground)
                    r.styleName?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall)
                    }
                    r.inspiredBy?.let {
                        Spacer(Modifier.height(2.dp))
                        Text("Inspired by $it",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(r.description,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium)
                    if (r.colorDna.isNotEmpty() || r.scenes.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            (if (r.colorDna.isNotEmpty()) "DNA: ${r.colorDna.joinToString(" / ")}" else "") +
                                (if (r.colorDna.isNotEmpty() && r.scenes.isNotEmpty()) "\n" else "") +
                                (if (r.scenes.isNotEmpty()) stringResource(R.string.films_scenes, r.scenes.joinToString(", ")) else ""),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.films_intensity), color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f))
                        Text("${(intensity * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(value = intensity, onValueChange = { intensity = it })
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Button(onClick = { applyRecipe(r) },
                            modifier = Modifier.weight(1f)) {
                            Text(if (isOwned) stringResource(R.string.films_apply) else stringResource(R.string.films_apply_locked))
                        }
                        Spacer(Modifier.width(8.dp))
                        if (isOwned) {
                            AssistChip(onClick = {},
                                label = { Text(if (r.priceTier == "premium") stringResource(R.string.films_owned) else stringResource(R.string.films_free)) })
                        } else {
                            Button(onClick = { buying = r }) {
                                Text(stringResource(R.string.films_buy, priceText(r)))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedButton(onClick = {
                            scope.launch {
                                app.films.duplicate(r.copy(intensity = intensity))
                                detail = null
                            }
                        }) { Text(stringResource(R.string.films_save_mine)) }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            scope.launch { app.films.duplicate(r) }
                        }) {
                            Icon(Icons.Filled.ContentCopy, "duplicate")
                        }
                        IconButton(onClick = {
                            scope.launch { app.films.resetToBundled(r.id) }
                        }) {
                            Icon(Icons.Filled.Refresh, "reset")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.films_disclaimer),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        // ---- Purchase overlay ----
        buying?.let { r ->
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { buying = null }) { Text(stringResource(R.string.close)) }
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.films_buy_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(Modifier.height(12.dp))
                    FilmPreview(recipe = r, sample = sample, posterTitle = true, height = 200.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(r.name, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                    r.styleName?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    Card(shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(16.dp)) {
                            PurchaseRow(stringResource(R.string.films_buy_item), r.name)
                            PurchaseRow(stringResource(R.string.films_buy_license), stringResource(R.string.films_buy_license_value))
                            PurchaseRow(stringResource(R.string.films_buy_price), priceText(r))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.films_testpay_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { confirmPay = r },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.films_buy, priceText(r)))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // ---- Test-pay confirm ----
        confirmPay?.let { r ->
            AlertDialog(
                onDismissRequest = { confirmPay = null },
                title = { Text(stringResource(R.string.films_confirm_title)) },
                text = { Text("${r.name} ${priceText(r)} — " + stringResource(R.string.films_confirm_ask)) },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { app.settings.addOwnedRecipe(r.id) }
                        confirmPay = null
                        buying = null
                    }) { Text(stringResource(R.string.confirm_ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmPay = null }) { Text(stringResource(R.string.confirm_cancel)) }
                }
            )
        }
    }
}

@Composable
private fun PurchaseRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(90.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium)
    }
}

/** Loads the user's latest photo (downsampled) for tint previews. Null when none. */
@Composable
private fun rememberSampleBitmap(ctx: Context, uri: String?): ImageBitmap? {
    var bmp by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bmp = withContext(Dispatchers.IO) {
            if (uri == null) return@withContext null
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                ctx.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it, null, opts)?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        }
    }
    return bmp
}

/**
 * Film preview card image: sample photo (or neutral mock scene) with the
 * recipe tint at its intensity, plus poster-style title overlay.
 */
@Composable
private fun FilmPreview(
    recipe: FilmRecipe,
    sample: ImageBitmap?,
    posterTitle: Boolean = false,
    height: androidx.compose.ui.unit.Dp = 140.dp,
) {
    val tint = previewColor(recipe)
    val hasPoster = !recipe.posterUrl.isNullOrBlank()
    Box(Modifier.fillMaxWidth().height(height)) {
        if (hasPoster) {
            coil.compose.AsyncImage(
                model = recipe.posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (sample != null) {
            Image(sample, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop)
        } else {
            MockScene(Modifier.fillMaxSize())
        }
        // Recipe mood wash (lighter on posters so the artwork stays readable).
        val wash = (0.12f + 0.33f * recipe.intensity).coerceIn(0f, 0.5f) *
            if (hasPoster) 0.45f else 1f
        Box(Modifier.fillMaxSize().background(tint.copy(alpha = wash)))
        // Poster-style title block
        if (posterTitle) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(recipe.name, color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, maxLines = 1)
                    recipe.styleName?.let {
                        Text(it, color = Color(0xFFE8DCC8),
                            style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Neutral grayscale mock scene used when the user has no photo yet. */
@Composable
private fun MockScene(modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0xFF3A3A40))) {
        val w = size.width
        val h = size.height
        // Sky
        drawRect(Brush.verticalGradient(
            listOf(Color(0xFF6E6E76), Color(0xFF3A3A40)),
            startY = 0f, endY = h * 0.62f))
        // Sun
        drawCircle(Color(0xFF9A9A9A), radius = w * 0.09f,
            center = androidx.compose.ui.geometry.Offset(w * 0.74f, h * 0.22f))
        // Mountains
        val ridge = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h * 0.62f)
            lineTo(w * 0.28f, h * 0.34f)
            lineTo(w * 0.52f, h * 0.62f)
            lineTo(w * 0.72f, h * 0.42f)
            lineTo(w, h * 0.62f)
            close()
        }
        drawPath(ridge, Color(0xFF2C2C30))
        // Ground
        drawRect(Color(0xFF232326), topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.62f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.38f))
        // Person
        val cx = w * 0.38f
        drawCircle(Color(0xFF101012), radius = w * 0.055f,
            center = androidx.compose.ui.geometry.Offset(cx, h * 0.52f))
        drawRoundRect(Color(0xFF101012),
            topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.075f, h * 0.60f),
            size = androidx.compose.ui.geometry.Size(w * 0.15f, h * 0.34f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f))
    }
}
