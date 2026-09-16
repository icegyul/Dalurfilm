package com.dalur.film.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    val mirrorFrontCamera by app.settings.mirrorFrontCamera.collectAsState(true)
    val showBattery by app.settings.showBattery.collectAsState(true)
    val showStorage by app.settings.showStorage.collectAsState(true)
    val volumeShutter by app.settings.volumeShutter.collectAsState(true)
    val hasMic = ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))
        .padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("전문가 메뉴", style = MaterialTheme.typography.titleMedium,
                color = scheme.onBackground)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("닫기") }
        }
        Spacer(Modifier.height(8.dp))

        // 탭 없이 한 페이지로 쭉 스크롤 — Blackmagic/Final Cut Camera 설정 화면 구조.
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PreferencesTab(vm, easy, app, scope, scheme, intensity, reducedMotion,
                mapStyle, guideDefaultOn, gridDefaultMode, mirrorFrontCamera, showBattery,
                showStorage, volumeShutter, onClose, onOpenCapability)
            ToolsTab(vm, easy)
            AudioTab(scheme, hasMic, onRequestMic)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))) {
                Text("확인")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Reference-design(Blackmagic/Final Cut Camera) 스타일 섹션 헤더: 굵은 소제목 +
 *  그 아래 둥근 카드로 관련 항목을 묶는다. */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun SettingsToggleRow(
    title: String, subtitle: String? = null,
    checked: Boolean, onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ---- 환경설정: 촬영 · 가이드·그리드 · 카메라 · 표시 · 앱 ----
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
    mirrorFrontCamera: Boolean,
    showBattery: Boolean,
    showStorage: Boolean,
    volumeShutter: Boolean,
    onClose: () -> Unit,
    onOpenCapability: () -> Unit,
) {
    Spacer(Modifier.height(8.dp))

    SettingsSection("촬영") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("필름 강도", color = scheme.onSurface, modifier = Modifier.weight(1f))
            Text("${(intensity * 100).toInt()}%",
                color = scheme.primary, style = MaterialTheme.typography.labelSmall)
        }
        Slider(value = intensity,
            onValueChange = { scope.launch { app.settings.setFilmIntensity(it) } })
        SettingsToggleRow("플래시 (사진)", if (easy.flashOn) "켜짐" else "꺼짐",
            checked = easy.flashOn, onCheckedChange = { vm.toggleFlash() })
    }

    SettingsSection(stringResource(R.string.settings_guide_grid_section)) {
        SettingsToggleRow(stringResource(R.string.settings_guide_default),
            stringResource(R.string.settings_guide_default_desc),
            checked = guideDefaultOn,
            onCheckedChange = { scope.launch { app.settings.setGuideDefaultOn(it) } })
        Text(stringResource(R.string.settings_grid_default), color = scheme.onSurface)
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

    // 카메라 — 지금 실제로 동작하는 것만 넣는다 (Nucleus 렌즈 컨트롤·HDMI 출력·
    // 애너모픽 디스퀴즈 같은 전문 리그 장비 기능은 DALUR 범위 밖이라 제외).
    SettingsSection("카메라") {
        SettingsToggleRow("전면 카메라 미러링", "미리보기만 좌우 반전 (저장 파일은 그대로)",
            checked = mirrorFrontCamera,
            onCheckedChange = { scope.launch { app.settings.setMirrorFrontCamera(it) } })
        SettingsToggleRow("볼륨 버튼으로 촬영", "볼륨 위/아래 키가 셔터 역할",
            checked = volumeShutter,
            onCheckedChange = { scope.launch { app.settings.setVolumeShutter(it) } })
    }

    // 표시 — 상단 HUD에 무엇을 보여줄지.
    SettingsSection("표시") {
        SettingsToggleRow("저장 공간 상태 표시", checked = showStorage,
            onCheckedChange = { scope.launch { app.settings.setShowStorage(it) } })
        SettingsToggleRow("배터리 잔량 표시", checked = showBattery,
            onCheckedChange = { scope.launch { app.settings.setShowBattery(it) } })
    }

    SettingsSection("앱") {
        SettingsToggleRow("움임 줄이기", checked = reducedMotion,
            onCheckedChange = { scope.launch { app.settings.setReducedMotion(it) } })
        Text("지도 타일: $mapStyle",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { onClose(); onOpenCapability() },
            modifier = Modifier.fillMaxWidth()) {
            Text("기기 성능·진단 보기")
        }
    }
}

// ---- 도구: PRO 패널 (코덱·모니터·노출·USB) ----
@Composable
private fun ToolsTab(vm: CameraViewModel, easy: EasyUiState) {
    // 이 화면이 열리는 것 자체가 선택이다 — 별도 토글 없음.
    // (여전히 easy.isPro를 켜서 카메라 화면의 fps/ISO/셔터 표시도 같이 켜준다.)
    androidx.compose.runtime.LaunchedEffect(Unit) { if (!easy.isPro) vm.setPro(true) }
    Text("도구", color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    ProCameraPanel(vm)
    Spacer(Modifier.height(16.dp))
}

// ---- 오디오: 마이크 권한/상태 ----
@Composable
private fun AudioTab(scheme: androidx.compose.material3.ColorScheme, hasMic: Boolean, onRequestMic: () -> Unit) {
    SettingsSection("오디오") {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
