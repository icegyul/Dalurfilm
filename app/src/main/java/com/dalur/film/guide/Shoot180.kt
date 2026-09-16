package com.dalur.film.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.dalur.film.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 180도 법칙: A·B를 잇는 가상선을 넘지 않는다. 넘으면 좌우가 바뀐다. */
enum class CamRole(val labelRes: Int, val hintRes: Int) {
    SINGLE_A(R.string.role_single_a, R.string.role_single_a_tip),
    SINGLE_B(R.string.role_single_b, R.string.role_single_b_tip),
    TWO_SHOT(R.string.role_two, R.string.role_two_tip),
}

data class CamSlot(val index: Int, val x: Float, val role: CamRole)

/** 카메라 대수별 기본 배치. 전부 가상선 같은 쪽(아래)에 둔다. */
fun slotsFor(count: Int): List<CamSlot> = when (count.coerceIn(1, 3)) {
    1 -> listOf(CamSlot(1, 0.5f, CamRole.TWO_SHOT))
    2 -> listOf(CamSlot(1, 0.28f, CamRole.SINGLE_A),
        CamSlot(2, 0.72f, CamRole.SINGLE_B))
    else -> listOf(CamSlot(1, 0.20f, CamRole.SINGLE_A),
        CamSlot(2, 0.50f, CamRole.TWO_SHOT),
        CamSlot(3, 0.80f, CamRole.SINGLE_B))
}

fun shootRole(count: Int, thisCam: Int): CamRole =
    slotsFor(count).firstOrNull { it.index == thisCam }?.role ?: CamRole.TWO_SHOT

fun shoot180Summary(count: Int, thisCam: Int, ctx: android.content.Context): String {
    val role = ctx.getString(shootRole(count, thisCam).labelRes)
    return if (count <= 1) ctx.getString(R.string.shoot180_solo)
    else ctx.getString(R.string.shoot180_multi, count, thisCam, role)
}

@Composable
fun Shoot180Setup(
    camCount: Int,
    thisCam: Int,
    onCount: (Int) -> Unit,
    onThisCam: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.shoot180_title), style = MaterialTheme.typography.titleMedium,
            color = scheme.onBackground)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.shoot180_rule),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.shoot180_how_many), style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..3).forEach { n ->
                FilterChip(selected = camCount == n, onClick = { onCount(n) },
                    label = { Text(stringResource(R.string.shoot180_n_cams, n)) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.shoot180_which), style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..camCount).forEach { n ->
                FilterChip(selected = thisCam == n, onClick = { onThisCam(n) },
                    label = { Text(stringResource(R.string.shoot180_nth, n)) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Shoot180Diagram(camCount = camCount, thisCam = thisCam,
            modifier = Modifier.fillMaxWidth().weight(1f)
                .clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.shoot180_this, thisCam, stringResource(shootRole(camCount, thisCam).labelRes)),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(shootRole(camCount, thisCam).hintRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.shoot180_ok)) }
    }
}

@Composable
fun Shoot180Diagram(camCount: Int, thisCam: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val warnText = stringResource(R.string.shoot180_warn)
    Canvas(modifier.background(Color(0xFF141417))) {
        val w = size.width
        val h = size.height
        val lineY = h * 0.30f
        val ax = w * 0.36f
        val bx = w * 0.64f
        val headR = w * 0.055f

        // Forbidden side wash (above the line)
        drawRect(Color(0xFFE5484D).copy(alpha = 0.10f),
            topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, lineY))
        // Imaginary line (red dashed)
        drawLine(Color(0xFFE5484D), Offset(0f, lineY), Offset(w, lineY), strokeWidth = 4f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        // A / B heads
        listOf(ax to "A", bx to "B").forEach { (x, name) ->
            drawCircle(Color(0xFFF5F2EA), radius = headR, center = Offset(x, lineY - headR * 1.6f))
            drawCircle(Color(0xFF9A958A), radius = headR,
                center = Offset(x, lineY - headR * 1.6f), style = Stroke(width = 3f))
            drawCircle(Color(0xFFF5F2EA), radius = headR * 0.45f,
                center = Offset(x, lineY + headR * 1.1f))
            drawText(measurer, name, topLeft = Offset(x - 11f, lineY - headR * 1.6f - 16f),
                style = androidx.compose.ui.text.TextStyle(
                    color = Color(0xFF0B0B0D), fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        }
        // Valid-side arc
        drawArc(Color(0xFF9A958A), startAngle = 20f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(w * 0.08f, lineY - h * 0.06f),
            size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.55f),
            style = Stroke(width = 3f))
        // Warning text
        drawText(measurer, warnText,
            topLeft = Offset(16f, 16f),
            style = androidx.compose.ui.text.TextStyle(
                color = Color(0xFFE5484D), fontSize = 15.sp))
        // Cameras
        slotsFor(camCount).forEach { slot ->
            val cx = slot.x * w
            val cy = h * 0.72f
            val selected = slot.index == thisCam
            val body = if (selected) Color(0xFFE8DCC8) else Color(0xFF3A3A44)
            val ink = if (selected) Color(0xFF1A1712) else Color(0xFFF5F2EA)
            // body
            drawRoundRect(body, topLeft = Offset(cx - 34f, cy - 26f),
                size = androidx.compose.ui.geometry.Size(68f, 62f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f))
            // lens
            drawCircle(ink, radius = 15f, center = Offset(cx, cy + 5f))
            // number above body
            drawText(measurer, "${slot.index}", topLeft = Offset(cx - 11f, cy - 62f),
                style = androidx.compose.ui.text.TextStyle(
                    color = Color(0xFFF5F2EA), fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            // aim line toward subjects
            drawLine(body, Offset(cx, cy - 26f), Offset(cx, lineY + 10f), strokeWidth = 2f)
            if (selected) {
                withTransform({}) {
                    drawCircle(Color(0xFFE8DCC8), radius = 46f,
                        center = Offset(cx, cy + 5f), style = Stroke(width = 3f))
                }
            }
        }
    }
}
