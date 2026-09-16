package com.dalur.film.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dalur.film.DalurApp
import com.dalur.film.R
import kotlinx.coroutines.launch

/**
 * 전문가 메뉴: 우상단 설정 버튼으로 열리는 설정 허브.
 * Blackmagic/Final Cut Camera 스타일로 환경설정·도구·오디오 3개 탭에 기능을 분류한다.
 */
@Composable
fun ExpertMenuSheet(
    vm: CameraViewModel,
    onClose: () -> Unit,
    onOpenCapability: () -> Unit,
    onRequestMic: () -> Unit,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as DalurApp
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val easy by vm.easy.collectAsState()
    val intensity by app.settings.filmIntensity.collectAsState(0.85f)
    val reducedMotion by app.settings.reducedMotion.collectAsState(false)
    val mapStyle by app.settings.mapStyle.collectAsState("")
    val guideDefaultOn by app.settings.guideDefaultOn.collectAsState(false)
    val gridDefaultMode by app.settings.gridDefaultMode.collectAsState(1)
    val hasMic = ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    var tab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("환경설정", "도구", "오디오")

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))
        .padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("전문가 메뉴", style = MaterialTheme.typography.titleMedium,
                color = scheme.onBackground)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("닫기") }
        }
        Spacer(Modifier.height(8.dp))
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            tabTitles.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i },
                    text = { Text(title) })
            }
        }
        Spacer(Modifier.height(4.dp))

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            when (tab) {
                0 -> PreferencesTab(vm, easy, app, scope, scheme, intensity, reducedMotion,
                    mapStyle, guideDefaultOn, gridDefaultMode, onClose, onOpenCapability)
                1 -> ToolsTab(vm, easy)
                2 -> AudioTab(scheme, hasMic, onRequestMic)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))) {
                Text("확인")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---- 환경설정: 촬영 기본값 + 가이드·그리드 + 앱 ----
@Composable
private fun PreferencesTab(
    vm: CameraViewModel,
    easy: EasyUiState,
    app: DalurApp,
    scope: kotlinx.coroutines.CoroutineScope,
    scheme: androidx.compose.material3.ColorScheme,
    intensity: Float,
    reducedMotion: Boolean,
    mapStyle: String,
    guideDefaultOn: Boolean,
    gridDefaultMode: Int,
    onClose: () -> Unit,
    onOpenCapability: () -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("필름 강도", color = scheme.onSurface, modifier = Modifier.weight(1f))
                Text("${(intensity * 100).toInt()}%",
                    color = scheme.primary, style = MaterialTheme.typography.labelSmall)
            }
            Slider(value = intensity,
                onValueChange = { scope.launch { app.settings.setFilmIntensity(it) } })
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("플래시 (사진)", color = scheme.onSurface)
                    Text(if (easy.flashOn) "켜짐" else "꺼짐",
                        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = easy.flashOn, onCheckedChange = { vm.toggleFlash() })
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    Text(stringResource(R.string.settings_guide_grid_section),
        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(4.dp))
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_guide_default), color = scheme.onSurface,
                    modifier = Modifier.weight(1f))
                Switch(checked = guideDefaultOn,
                    onCheckedChange = { scope.launch { app.settings.setGuideDefaultOn(it) } })
            }
            Text(stringResource(R.string.settings_guide_default_desc),
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_grid_default), color = scheme.onSurface)
            Spacer(Modifier.height(4.dp))
            val gridOptions = listOf(
                R.string.grid_off, R.string.grid_thirds, R.string.grid_16x9,
                R.string.grid_9x16, R.string.grid_shorts, R.string.grid_4x3)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                gridOptions.forEachIndexed { i, res ->
                    FilterChip(selected = gridDefaultMode == i,
                        onClick = { scope.launch { app.settings.setGridDefaultMode(i) } },
                        label = { Text(stringResource(res)) })
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    Text("앱", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(4.dp))
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("움임 줄이기", color = scheme.onSurface, modifier = Modifier.weight(1f))
                Switch(checked = reducedMotion,
                    onCheckedChange = { scope.launch { app.settings.setReducedMotion(it) } })
            }
            Text("지도 타일: $mapStyle",
                color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onClose(); onOpenCapability() },
                modifier = Modifier.fillMaxWidth()) {
                Text("기기 성능·진단 보기")
            }
        }
    }
}

// ---- 도구: PRO 패널 (코덱·모니터·노출·USB) ----
@Composable
private fun ToolsTab(vm: CameraViewModel, easy: EasyUiState) {
    // Opening this tab IS the selection — no separate toggle to fight through.
    // (Still flips easy.isPro so the in-camera fps/ISO/shutter readout comes on too.)
    androidx.compose.runtime.LaunchedEffect(Unit) { if (!easy.isPro) vm.setPro(true) }
    Spacer(Modifier.height(8.dp))
    ProCameraPanel(vm)
}

// ---- 오디오: 마이크 권한/상태 ----
@Composable
private fun AudioTab(scheme: androidx.compose.material3.ColorScheme, hasMic: Boolean, onRequestMic: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Card(shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("마이크", color = scheme.onSurface)
                Text(if (hasMic) "허용됨 — 비디오 모드에서 L/R 미터 표시"
                    else "미허용 — 비디오 미터·녹음음이 안 들어감",
                    color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (!hasMic) {
                OutlinedButton(onClick = onRequestMic) { Text("허용") }
            }
        }
    }
}
