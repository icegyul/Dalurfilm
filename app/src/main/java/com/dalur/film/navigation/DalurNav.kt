package com.dalur.film.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dalur.film.R
import com.dalur.film.camera.CameraViewModel
import com.dalur.film.camera.CaptureMode
import com.dalur.film.camera.EasyCameraScreen
import com.dalur.film.diagnostics.CapabilityScreen
import com.dalur.film.film.FilmsScreen
import com.dalur.film.journey.JourneyPlayerScreen
import com.dalur.film.map.MapScreen
import com.dalur.film.playback.PlaybackScreen

sealed class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    data object Camera : Tab("camera", R.string.tab_camera, Icons.Filled.PhotoCamera)
    data object Films : Tab("films", R.string.tab_films, Icons.Filled.Palette)
    data object Map : Tab("map", R.string.tab_map, Icons.Filled.Map)
    // Guide dropped from the tab bar — it's already the guide toggle next to the
    // shutter in Camera, so a separate tab was a duplicate destination.
}

/** 참고 디자인(pro 카메라 캡슐): 어두운 원 + 그 아래 작은 라벨. */
@Composable
private fun NavCircle(
    icon: ImageVector,
    desc: String,
    selected: Boolean,
    size: androidx.compose.ui.unit.Dp,
    label: String? = null,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(size).clip(CircleShape)
                .background(if (selected) Color(0xFFF5F2EA) else Color(0xFF26262C)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = desc,
                tint = if (selected) Color(0xFF0B0B0D) else Color(0xFFF5F2EA),
                modifier = Modifier.size(size * 0.46f))
        }
        if (label != null) {
            Spacer(Modifier.height(3.dp))
            Text(label,
                color = if (selected) Color(0xFFF5F2EA) else Color(0xFFF5F2EA).copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1)
        }
    }
}

/** 윗줄(카메라 제어) 원 — 아랫줄 내비 원보다 한 치수 작게. */
@Composable
private fun CamCircle(icon: ImageVector, desc: String, selected: Boolean, size: androidx.compose.ui.unit.Dp, label: String? = null, onClick: () -> Unit) {
    NavCircle(icon, desc, selected, size * 0.82f, label, onClick)
}

/** 그리드: 탭하면 다음 모드, 길게 누르면 모드 선택. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GridCircle(vm: CameraViewModel, mode: Int, size: androidx.compose.ui.unit.Dp) {
    var menu by remember { mutableStateOf(false) }
    val s = size * 0.82f
    androidx.compose.foundation.layout.Box {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = { vm.cycleGrid() }, onLongClick = { menu = true })
                .padding(horizontal = 2.dp, vertical = 2.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(s).clip(CircleShape)
                    .background(if (mode != 0) Color(0xFFF5F2EA) else Color(0xFF26262C)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Grid3x3, contentDescription = "grid",
                    tint = if (mode != 0) Color(0xFF0B0B0D) else Color(0xFFF5F2EA),
                    modifier = Modifier.size(s * 0.46f))
            }
            Spacer(Modifier.height(3.dp))
            Text("그리드",
                color = if (mode != 0) Color(0xFFF5F2EA) else Color(0xFFF5F2EA).copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val labels = listOf("끄기", "3분할", "16:9", "9:16", "숏츠 안전", "4:3")
            labels.forEachIndexed { i, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        if (mode == i) Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                    },
                    onClick = { vm.setGridMode(i); menu = false },
                )
            }
        }
    }
}

@Composable
fun DalurNav(factory: ViewModelProvider.Factory) {
    val nav = rememberNavController()
    val vm: CameraViewModel = viewModel(factory = factory)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val app = ctx.applicationContext as com.dalur.film.DalurApp
    val easy by vm.easy.collectAsState()
    val captures by vm.allCaptures.collectAsState()
    val recipes by vm.filmRecipes.collectAsState()
    val owned by app.settings.ownedRecipes.collectAsState(emptySet())
    // 카메라에서 고를 수 있는 건 무료 + 구매한 것뿐.
    val pickableFilms = remember(recipes, owned) {
        recipes.filter { it.priceTier != "premium" || it.id in owned }
    }
    var filmStripOpen by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            // Circular bar: cream track, black-filled circle for the active tab,
            // shutter anchored in the middle (works from any tab — jumps to
            // Camera and fires vm.requestCapture(), which EasyCameraScreen relays
            // to its real takePhoto()/toggleVideo()).
            // Brand palette: navy/black #0B0B0D, cream #F5F2EA, tan accent #E8DCC8.
            val backStack by nav.currentBackStackEntryAsState()
            val current = backStack?.destination?.route
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier.fillMaxWidth()
            ) {
                val land = maxWidth > maxHeight
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = if (land) 6.dp else 12.dp)
                ) {
                // 필름 레시피 슬라이드 패널 — 하단 바 위로 슬라이드되어 뜬다
                // (참고: Play it 앱의 RECENT MATCHES 패널, 가운데 버튼 위로 펼쳐짐).
                // 이름·카테고리 없이 포스터만 — 디테일은 필름 탭에서.
                if (current == Tab.Camera.route) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = filmStripOpen,
                        enter = androidx.compose.animation.expandVertically(
                            expandFrom = Alignment.Bottom) + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically(
                            shrinkTowards = Alignment.Bottom) + androidx.compose.animation.fadeOut(),
                    ) {
                        androidx.compose.foundation.layout.Column(
                            Modifier.fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF0B0B0D))
                                .padding(12.dp),
                        ) {
                            androidx.compose.foundation.lazy.LazyRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item {
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFF26262C))
                                            .then(if (easy.filmId == null)
                                                Modifier.border(2.dp, Color(0xFFF5F2EA), RoundedCornerShape(14.dp))
                                                else Modifier)
                                            .clickable { vm.selectFilm(null); filmStripOpen = false },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.NotInterested, "no film",
                                            tint = Color(0xFFF5F2EA), modifier = Modifier.size(22.dp))
                                    }
                                }
                                items(pickableFilms.size) { i ->
                                    val r = pickableFilms[i]
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
                                            .background(com.dalur.film.ui.components.previewColor(r))
                                            .then(if (easy.filmId == r.id)
                                                Modifier.border(2.dp, Color(0xFFF5F2EA), RoundedCornerShape(14.dp))
                                                else Modifier)
                                            .clickable { vm.selectFilm(r.id); filmStripOpen = false },
                                    ) {
                                        if (!r.posterUrl.isNullOrBlank()) {
                                            coil.compose.AsyncImage(
                                                model = r.posterUrl, contentDescription = r.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth()
                ) {
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF0B0B0D))
                        .padding(horizontal = 10.dp, vertical = if (land) 6.dp else 10.dp),
                ) {
                    val circle = if (land) 40.dp else 46.dp
                    val shutterSize = if (land) 58.dp else 72.dp
                    val onCam = current == Tab.Camera.route

                    // 층 순서(아래에서 위로): 1 필름 · 2 메인메뉴 · 3 카메라 제어.
                    // 배율줌(4)·노출(5)은 이 바 위 프리뷰 오버레이가 그린다.
                    // 셔터는 2·3층을 가로지르는 "하나"의 큰 원.
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
                            // 3층: 앞뒤반전 · 동영상 · (셔터) · 그리드 · 가이드
                            if (onCam) {
                                androidx.compose.foundation.layout.Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    androidx.compose.foundation.layout.Row(
                                        Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CamCircle(Icons.Filled.Cameraswitch, "front/back", false, circle, "반전") { vm.switchLens() }
                                        CamCircle(Icons.Filled.Videocam, "video",
                                            easy.mode == CaptureMode.VIDEO, circle, "동영상") { vm.setMode(CaptureMode.VIDEO) }
                                    }
                                    Spacer(Modifier.width(shutterSize + 12.dp))
                                    androidx.compose.foundation.layout.Row(
                                        Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        GridCircle(vm, easy.gridMode, circle)
                                        CamCircle(Icons.Filled.Person, "guide", easy.guideOn, circle, "가이드") { vm.toggleGuide() }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            // 2층 메인메뉴: 사진첩 · 사진 · (셔터) · 필름 · 지도
                            androidx.compose.foundation.layout.Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.foundation.layout.Row(
                                    Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    NavCircle(Icons.Filled.PhotoLibrary, "gallery", false, circle, "사진첩") {
                                        captures.maxByOrNull { it.timestampMillis }?.let {
                                            nav.navigate("playback?uri=${it.mediaUri}")
                                        }
                                    }
                                    // 카메라 탭 버튼 자리에 스틸카메라(사진 모드) 버튼.
                                    // 다른 탭에서 눌러도 카메라로 돌아오게 한다.
                                    NavCircle(Icons.Filled.PhotoCamera, "photo",
                                        onCam && easy.mode == CaptureMode.PHOTO, circle, "사진") {
                                        if (!onCam) nav.navigate(Tab.Camera.route) { launchSingleTop = true }
                                        vm.setMode(CaptureMode.PHOTO)
                                    }
                                }
                                Spacer(Modifier.width(shutterSize + 12.dp))
                                androidx.compose.foundation.layout.Row(
                                    Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    NavCircle(Tab.Films.icon, stringResource(Tab.Films.labelRes),
                                        current == Tab.Films.route, circle, stringResource(Tab.Films.labelRes)) {
                                        nav.navigate(Tab.Films.route) { launchSingleTop = true }
                                    }
                                    NavCircle(Tab.Map.icon, stringResource(Tab.Map.labelRes),
                                        current == Tab.Map.route, circle, stringResource(Tab.Map.labelRes)) {
                                        nav.navigate(Tab.Map.route) { launchSingleTop = true }
                                    }
                                }
                            }
                        }
                        // 2·3층을 덮는 하나뿐인 셔터.
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(shutterSize)
                                .border(3.dp, Color(0xFFF5F2EA), CircleShape)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (easy.isRecording || easy.mode == CaptureMode.VIDEO) Color(0xFFE5484D)
                                    else Color(0xFFF5F2EA))
                                .clickable {
                                    if (!onCam) nav.navigate(Tab.Camera.route) { launchSingleTop = true }
                                    vm.requestCapture()
                                }
                        )
                        // 필름 손잡이 — 셔터 위, 카드 윗변에 걸치는 작은 필.
                        // 누르면 위 패널이 슬라이드되어 뜬다 (참고: Play it 앱 FAB).
                        if (onCam) {
                            val sel = recipes.firstOrNull { it.id == easy.filmId }
                            androidx.compose.foundation.layout.Row(
                                Modifier.align(Alignment.TopCenter)
                                    .offset(y = (-11).dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF0B0B0D))
                                    .border(2.dp, Color(0xFFF5F2EA), RoundedCornerShape(50))
                                    .clickable { filmStripOpen = !filmStripOpen }
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.size(12.dp).clip(RoundedCornerShape(3.dp))
                                        .background(sel?.let { com.dalur.film.ui.components.previewColor(it) }
                                            ?: Color(0xFF3A3A42)))
                                Spacer(Modifier.width(4.dp))
                                Icon(if (filmStripOpen) Icons.Filled.KeyboardArrowDown
                                    else Icons.Filled.KeyboardArrowUp, "film strip",
                                    tint = Color(0xFFF5F2EA), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                }
            }
            }
            }
    ) { pad ->
        NavHost(nav, startDestination = Tab.Camera.route, Modifier.padding(pad)) {
            composable(Tab.Camera.route) {
                EasyCameraScreen(vm,
                    onOpenCapability = { nav.navigate("capability") },
                    onOpenPlayback = { uri -> nav.navigate("playback?uri=$uri") })
            }
            composable(Tab.Films.route) {
                FilmsScreen(vm, onApplyRecipe = { nav.navigate("camera") })
            }
            composable(Tab.Map.route) {
                MapScreen(vm, onPlay = { id -> nav.navigate("journey/$id") })
            }
            composable("capability") { CapabilityScreen(vm, onBack = { nav.popBackStack() }) }
            composable("journey/{id}") { e ->
                JourneyPlayerScreen(vm, journeyId = e.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
            }
            composable("playback?uri={uri}") { e ->
                PlaybackScreen(uri = e.arguments?.getString("uri") ?: "", onBack = { nav.popBackStack() })
            }
        }
    }
}
