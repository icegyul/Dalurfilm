package com.dalur.film.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dalur.film.film.categoryLabel
import com.dalur.film.shared.FilmRecipe

/** Screen header: title + supporting line. Used by every tab for a consistent voice. */
@Composable
fun DalurHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** Micro section label, e.g. "MONITOR", "EXPOSURE". */
@Composable
fun DalurSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
}

/** Full-area permission/availability fallback with a single action. */
@Composable
fun FailureState(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { Text(action) }
    }
}

/**
 * Deterministic tint per recipe id.
 * Preview-only approximation; rendered output uses the real GPU filter.
 */
fun filmTint(recipeId: String): Color = when {
    recipeId.contains("neon") || recipeId.contains("midnight") -> Color(0xFFB14AED)
    recipeId.contains("dust") || recipeId.contains("empire") -> Color(0xFFD9A066)
    recipeId.contains("stage") -> Color(0xFF7B6CFF)
    recipeId.contains("garden") -> Color(0xFFF2B8C6)
    recipeId.contains("forest") -> Color(0xFF4E9B6A)
    recipeId.contains("platform") -> Color(0xFF4E7E8A)
    recipeId.contains("drive") -> Color(0xFFFF9D45)
    recipeId.contains("red") -> Color(0xFFC44536)
    recipeId.contains("chrome") -> Color(0xFF2A9D8F)
    recipeId.contains("vivid") || recipeId.contains("landscape") || recipeId.contains("street") ->
        Color(0xFFFF8C42)
    recipeId.contains("skin") -> Color(0xFFE9B98D)
    recipeId.contains("natural") || recipeId.contains("daylight") -> Color(0xFFF4E9CD)
    recipeId.contains("classic") -> Color(0xFFD9A066)
    recipeId.contains("indoor") -> Color(0xFF7FB2E5)
    recipeId.contains("sunset") || recipeId.contains("summer") -> Color(0xFFFFB36B)
    recipeId.contains("rain") || recipeId.contains("night") -> Color(0xFF6BA8FF)
    recipeId.contains("mono") -> Color(0xFF9A9A9A)
    recipeId.contains("cinema") -> Color(0xFFFF9D5C)
    recipeId.contains("soft") -> Color(0xFFE8DCC8)
    recipeId.contains("memory") -> Color(0xFFD9B98A)
    else -> Color(0xFFFFD9A8)
}

/** Horizontally scrolling category chip row used to filter the film carousel. */
@Composable
fun FilmCategoryRow(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(categories, key = { it }) { c ->
            FilterChip(
                selected = c == selected,
                onClick = { onSelect(c) },
                label = { Text(categoryLabel(c), maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

/** Horizontal carousel film card: poster/tint image + title overlay + subtitle. */
@Composable
fun FilmCarouselCard(
    modifier: Modifier = Modifier,
    name: String,
    subtitle: String,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
    posterUrl: String? = null,
) {
    Column(
        modifier
            .width(116.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().height(110.dp)) {
            if (posterUrl != null) {
                coil.compose.AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(tint, tint.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.surfaceVariant)
                    )
                ))
            }
            // Light mood wash so the card reads as the recipe look.
            Box(Modifier.fillMaxSize()
                .background(tint.copy(alpha = if (posterUrl != null) 0.12f else 0.25f)))
            // Title overlay (same poster feel as the Films grid).
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))) {
                Text(name, Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Two thin horizontal L/R bars. Green normally, red past clipping range. */
@Composable
fun StereoBars(l: Float, r: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MeterRow("L", l)
        Spacer(Modifier.height(3.dp))
        MeterRow("R", r)
    }
}

/** Compact L/R bars for the title row (small): thin labels, bars touching,
 *  with a reference line marking the "sounds stable up to here" level. */
@Composable
fun CompactMeter(l: Float, r: Float) {
    Column {
        MeterRow("L", l, barWidth = 56.dp, barHeight = 4.dp,
            labelBold = false, refLevel = 0.7f)
        MeterRow("R", r, barWidth = 56.dp, barHeight = 4.dp,
            labelBold = false, refLevel = 0.7f)
    }
}

@Composable
private fun MeterRow(
    label: String,
    level: Float,
    barWidth: androidx.compose.ui.unit.Dp = 120.dp,
    barHeight: androidx.compose.ui.unit.Dp = 6.dp,
    labelBold: Boolean = true,
    refLevel: Float? = null,
) {
    val hot = level > 0.9f
    val bar = if (hot) Color(0xFFE5484D) else Color(0xFF4CAF50)
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 고정 너비 — "L"과 "R" 글자 폭이 달라서 막대 시작점이 어긋나면
        // 기준선도 서로 다른 위치로 보인다. 폭을 고정해 막대를 나란히 맞춘다.
        Text(label, color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (labelBold) FontWeight.Bold else FontWeight.Light,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(12.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.width(barWidth).height(barHeight)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxWidth(level.coerceIn(0f, 1f)).height(barHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(bar))
            // Reference line: the level up to which the signal reads as stable.
            refLevel?.let { ref ->
                Box(Modifier.fillMaxHeight().width(1.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = barWidth * ref.coerceIn(0f, 1f))
                    .background(Color.White.copy(alpha = 0.9f)))
            }
        }
    }
}

/** Preview wash for a recipe: authored previewTint hex when present, else id tint. */
fun previewColor(recipe: FilmRecipe): Color {
    recipe.previewTint?.let { hex ->
        runCatching {
            val h = hex.removePrefix("#")
            require(h.length == 6)
            Color(
                red = h.substring(0, 2).toInt(16) / 255f,
                green = h.substring(2, 4).toInt(16) / 255f,
                blue = h.substring(4, 6).toInt(16) / 255f,
            )
        }.getOrNull()?.let { return it }
    }
    return filmTint(recipe.id)
}

/** Approximate color for a Color-DNA name (dots in the film sheet). */
fun dnaColor(name: String): Color {
    val n = name.lowercase()
    return when {
        "cyan" in n -> Color(0xFF35C4DC)
        "magenta" in n -> Color(0xFFE0409E)
        "purple" in n -> Color(0xFF9B4DFF)
        "pink" in n -> Color(0xFFF2A9C6)
        "coral" in n -> Color(0xFFFF9D85)
        "red" in n -> Color(0xFFE0483E)
        "orange" in n -> Color(0xFFFF8A3D)
        "amber" in n || "gold" in n -> Color(0xFFD8A94B)
        "yellow" in n -> Color(0xFFE3B93C)
        "olive" in n -> Color(0xFF7B744E)
        "green" in n -> Color(0xFF4E9B6A)
        "mint" in n -> Color(0xFF9AD6C2)
        "teal" in n -> Color(0xFF4E7E8A)
        "steel blue" in n -> Color(0xFF7E97A8)
        "blue" in n -> Color(0xFF3E6FE0)
        "brown" in n -> Color(0xFF8A6E4E)
        "beige" in n || "cream" in n -> Color(0xFFD9C9A8)
        "silver" in n -> Color(0xFFB8B8C0)
        "gray" in n || "grey" in n -> Color(0xFF9A9A9A)
        "white" in n -> Color(0xFFF5F5F5)
        "black" in n -> Color(0xFF1A1A1A)
        else -> Color(0xFF8A8A8A)
    }
}
@Composable
fun ModeSideButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(48.dp)
            .border(
                2.dp,
                if (selected) scheme.primary else scheme.outline,
                CircleShape
            )
            .clip(CircleShape)
            .background(if (selected) scheme.primary.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (selected) scheme.primary else scheme.onBackground,
            content = icon
        )
    }
}
