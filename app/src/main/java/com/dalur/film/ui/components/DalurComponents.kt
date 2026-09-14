package com.dalur.film.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
                label = { Text(c, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

/** Horizontal carousel film card: tint swatch + name + subtitle, tap to select. */
@Composable
fun FilmCarouselCard(
    modifier: Modifier = Modifier,
    name: String,
    subtitle: String,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit
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
        Box(
            Modifier.fillMaxWidth().height(64.dp).background(
                Brush.horizontalGradient(
                    listOf(tint, tint.copy(alpha = 0.55f), MaterialTheme.colorScheme.surfaceVariant)
                )
            )
        )
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(name, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(1.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
