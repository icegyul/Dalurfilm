package com.dalur.film.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    recipeId.contains("sunset") || recipeId.contains("summer") -> Color(0xFFFFB36B)
    recipeId.contains("rain") || recipeId.contains("night") -> Color(0xFF6BA8FF)
    recipeId.contains("mono") -> Color(0xFF9A9A9A)
    recipeId.contains("cinema") -> Color(0xFFFF9D5C)
    recipeId.contains("soft") -> Color(0xFFE8DCC8)
    recipeId.contains("memory") -> Color(0xFFD9B98A)
    else -> Color(0xFFFFD9A8)
}
