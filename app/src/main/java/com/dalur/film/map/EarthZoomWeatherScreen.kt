package com.dalur.film.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dalur.film.R
import com.dalur.film.camera.CameraViewModel
import com.dalur.film.weather.EarthusWeatherRepository
import com.dalur.film.weather.WeatherResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "지구에서 줌인해서 내 동네 날씨" 탭.
 *
 * [EarthusWeatherRepository]가 실제 earthus `/v1/weather`를 호출한다 (키는
 * secrets.properties -> BuildConfig, 커밋되지 않음). 문제 생기면 여기 딱
 * 한 줄(`EarthusWeatherRepository()` -> `MockWeatherRepository()`)만 바꿔서
 * 화면/애니메이션을 다시 가짜 데이터로 확인할 수 있다.
 *
 * MapLibre Native Android(11.12.2)는 globe projection을 아직 지원하지 않는다
 * (2026-09 기준 로드맵에만 있음) — 그래서 진짜 3D 지구본 대신: 원형으로 자른
 * 지구 텍스처를 잠깐 보여준 뒤, 평면 지도가 전세계 줌에서 사용자 위치(시/동
 * 레벨)까지 날아가는 것으로 같은 느낌을 낸다.
 */
@Composable
fun EarthZoomWeatherTab(vm: CameraViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { EarthusWeatherRepository() }

    var hasLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted -> hasLocation = granted }
    LaunchedEffect(Unit) { if (!hasLocation) permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    var showEarthIntro by remember { mutableStateOf(true) }
    var target by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var weather by remember { mutableStateOf<WeatherResult?>(null) }
    var mapLibreMap by remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }
    var mapView by remember { mutableStateOf<org.maplibre.android.maps.MapView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 이 탭이 살아있는 동안만 그리도록 — onStart/onResume 없이는 화면이 검게
    // 나온다 (지도 탭과 같은 이유), onPause/onStop/onDestroy 없이는 탭을
    // 벗어나도 GL 리소스가 안 풀린다.
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView?.onStart()
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_STOP -> mapView?.onStop()
                Lifecycle.Event.ON_DESTROY -> runCatching { mapView?.onDestroy() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            runCatching { mapView?.onDestroy() }
        }
    }

    // 위치 확보 → 날씨 조회. 최소 1.2초는 지구 인트로를 보여줘서 화면이
    // 깜빡이는 느낌 없이 "줌인하는 연출"처럼 느껴지게 한다.
    LaunchedEffect(hasLocation) {
        if (!hasLocation) return@LaunchedEffect
        val introMinMs = launch { delay(1200) }
        val loc = runCatching { vm.locationTracker.lastFix() }.getOrNull()
        val lat = loc?.latitude ?: 37.5665   // 위치 못 얻으면 서울시청 — 데모가 멈추지 않게.
        val lon = loc?.longitude ?: 126.9780
        target = lat to lon
        weather = repo.fetchWeather(lat, lon)
        introMinMs.join()
        showEarthIntro = false
    }

    // 지도 스타일이 준비되고 목적지가 정해지면, 전세계 줌(2)에서 목적지
    // 줌(13.5)까지 날아간다.
    LaunchedEffect(mapLibreMap, target) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val (lat, lon) = target ?: return@LaunchedEffect
        map.moveCamera(
            org.maplibre.android.camera.CameraUpdateFactory
                .newLatLngZoom(org.maplibre.android.geometry.LatLng(0.0, 0.0), 1.2))
        delay(150) // 첫 프레임이 세계 줌으로 자리잡을 시간.
        map.easeCamera(
            org.maplibre.android.camera.CameraUpdateFactory
                .newLatLngZoom(org.maplibre.android.geometry.LatLng(lat, lon), 13.5),
            2200)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { c ->
                org.maplibre.android.maps.MapView(c).apply {
                    onCreate(null)
                    // 이 시점에 이미 컴포지션 중이라는 건 호스트가 최소 STARTED
                    // 상태라는 뜻 — 바로 시작해도 안전하다 (검은 화면 방지).
                    onStart()
                    onResume()
                    mapView = this
                    getMapAsync { map ->
                        map.setStyle("https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json") {
                            mapLibreMap = map
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showEarthIntro,
            exit = fadeOut(tween(500)),
            modifier = Modifier.fillMaxSize(),
        ) {
            EarthIntro()
        }

        // 날씨 카드 — 인트로가 끝난 뒤에만.
        AnimatedVisibility(
            visible = !showEarthIntro,
            enter = fadeIn(tween(400)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
        ) {
            WeatherCard(weather)
        }
    }
}

@Composable
private fun EarthIntro() {
    val infinite = rememberInfiniteTransition(label = "earth-spin")
    val rotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "earth-spin-angle",
    )
    Box(Modifier.fillMaxSize().background(Color(0xFF04070C)), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(220.dp).clip(CircleShape)
                .background(Color(0xFF04070C)),
        ) {
            Image(
                painter = painterResource(R.drawable.earth_globe),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().rotate(rotation),
            )
            // 구 형태처럼 보이게 하는 라디얼 음영 — 평면 텍스처를 원으로만
            // 잘라놓으면 밋밋해서, 가장자리를 어둡게 눌러준다.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Color.Transparent, Color(0xCC000000)),
                        radius = 260f,
                    )
                )
            )
        }
        Text("earthus",
            color = Color(0xFF83D8FF), fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp))
    }
}

@Composable
private fun WeatherCard(result: WeatherResult?) {
    Card(shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14202B).copy(alpha = 0.94f))) {
        Column(Modifier.padding(16.dp)) {
            when (result) {
                null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("날씨 불러오는 중…", color = Color(0xFFA8BAC6))
                    }
                }
                is WeatherResult.Success -> {
                    val o = result.data.observed
                    if (o != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${o.tempC.toInt()}°", color = Color(0xFFEDF5FA),
                                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(o.stationName, color = Color(0xFFEDF5FA), fontWeight = FontWeight.Bold)
                                Text("습도 ${o.humidityPct.toInt()}% · 바람 ${o.windMs}m/s · 강수 ${o.rainMm ?: 0.0}mm",
                                    color = Color(0xFFA8BAC6), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(result.data.sourceNote, color = Color(0xFF6E828F),
                        style = MaterialTheme.typography.labelSmall)
                }
                is WeatherResult.ApiError -> Text("날씨 API 오류 (${result.code}): ${result.message}",
                    color = Color(0xFFE5484D))
                is WeatherResult.NetworkError -> Text("네트워크 오류: ${result.message}",
                    color = Color(0xFFE5484D))
            }
        }
    }
}
