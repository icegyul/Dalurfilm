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
import androidx.compose.ui.graphics.Brush
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
                .background(
                    if (selected) Brush.radialGradient(
                        colors = listOf(Color(0xFFFDFBF6), Color(0xFFE8DCC8)),
                    ) else Brush.radialGradient(
                        colors = listOf(Color(0xFF35353D), Color(0xFF16161A)),
                    )
                )
                .border(
                    1.dp,
                    if (selected) Color(0xFFE8DCC8) else Color(0xFFF5F2EA).copy(alpha = 0.18f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = desc,
                tint = if (selected) Color(0xFF0B0B0D) else Color(0xFFF5F2EA),
                modifier = Modifier.size(size * 0.46f))
        }
        if (label != null) {
            Spacer(Modifier.height(3.dp))
            Text(label,
                color = if (selected) Color(0xFFE8DCC8) else Color(0xFFF5F2EA).copy(alpha = 0.6f),
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
                    .background(
                        if (mode != 0) Brush.radialGradient(
                            colors = listOf(Color(0xFFFDFBF6), Color(0xFFE8DCC8)),
                        ) else Brush.radialGradient(
                            colors = listOf(Color(0xFF35353D), Color(0xFF16161A)),
                        )
                    )
                    .border(
                        1.dp,
                        if (mode != 0) Color(0xFFE8DCC8) else Color(0xFFF5F2EA).copy(alpha = 0.18f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Grid3x3, contentDescription = "grid",
                    tint = if (mode != 0) Color(0xFF0B0B0D) else Color(0xFFF5F2EA),
                    modifier = Modifier.size(s * 0.46f))
            }
            Spacer(Modifier.height(3.dp))
            Text("그리드",
                color = if (mode != 0) Color(0xFFE8DCC8) else Color(0xFFF5F2EA).copy(alpha = 0.6f),
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

/**
 * 레퍼런스 이미지의 대각선 캐스케이드 배치. (dx, dy) = 셔터 중심 기준
 * 왼쪽(dx)·위(dy)로 떨어진 거리. baseX/baseY = 컨테이너 우하단 모서리에서
 * 셔터 중심까지의 거리. 순수 dp 오프셋이라 겹침 없이 정확히 배치할 수 있다.
 */
private fun cascadeOffsetDp(
    baseX: androidx.compose.ui.unit.Dp,
    baseY: androidx.compose.ui.unit.Dp,
    dx: androidx.compose.ui.unit.Dp,
    dy: androidx.compose.ui.unit.Dp,
    btnSize: androidx.compose.ui.unit.Dp,
): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> {
    val x = -(baseX + dx) + btnSize / 2
    val y = -(baseY + dy) + btnSize / 2
    return Pair(x, y)
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.FanSlot(
    dx: androidx.compose.ui.unit.Dp,
    dy: androidx.compose.ui.unit.Dp,
    baseX: androidx.compose.ui.unit.Dp,
    baseY: androidx.compose.ui.unit.Dp,
    size: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    val (offX, offY) = cascadeOffsetDp(baseX, baseY, dx, dy, size)
    androidx.compose.foundation.layout.Box(Modifier.align(Alignment.BottomEnd).offset(x = offX, y = offY)) {
        content()
    }
}

/**
 * 영화 포스터 문법의 세로 썸네일 — 실제 포스터 이미지가 있으면 크롭해 채우고,
 * 없으면 필름 팔레트 색 위에 하단 스크림 + 타이포로 "포스터처럼" 보이게 한다.
 * Film Poster Stack과 Film Simulation 라디얼 버튼이 이 하나의 얼굴을 공유한다.
 */
@Composable
private fun PosterFace(
    recipe: com.dalur.film.shared.FilmRecipe?,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Box(modifier.background(
        recipe?.let { com.dalur.film.ui.components.previewColor(it) } ?: Color(0xFF26262C)
    )) {
        if (!recipe?.posterUrl.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = recipe?.posterUrl, contentDescription = recipe?.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        } else {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                        startY = 0.35f, endY = Float.POSITIVE_INFINITY,
                    )
                )
            )
            Text(
                (recipe?.name ?: "NO FILM").uppercase().take(10),
                color = Color(0xFFF5F2EA),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
            )
        }
    }
}

/** Film Simulation 라디얼 버튼 — 영화 포스터 문법의 세로 썸네일 + 라벨. */
@Composable
private fun FilmSimRadialButton(
    recipe: com.dalur.film.shared.FilmRecipe?,
    selected: Boolean,
    posterW: androidx.compose.ui.unit.Dp,
    posterH: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(2.dp),
    ) {
        PosterFace(
            recipe,
            Modifier.size(posterW, posterH).clip(RoundedCornerShape(8.dp))
                .border(
                    if (selected) 1.5.dp else 0.75.dp,
                    if (selected) Color(0xFFE8DCC8) else Color(0xFFF5F2EA).copy(alpha = 0.35f),
                    RoundedCornerShape(8.dp),
                ),
        )
        Spacer(Modifier.height(3.dp))
        Text("필름", color = if (selected) Color(0xFFE8DCC8) else Color(0xFFF5F2EA).copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
                // Film Simulation 진입은 카메라 화면에 큰 피커를 펼치지 않는다 —
                // Film Poster Stack을 누르면 기존 Film Market(필름 탭)으로 이동한다.
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
                    val shutterSize = if (land) 58.dp else 78.dp
                    val onCam = current == Tab.Camera.route

                    if (onCam) {
                        // DALUR film Radial Camera UI — 셔터를 중심으로 한 부채꼴 메뉴.
                        // 바깥 1차: 맵 → 사진 → 동영상 → 반전 (순서 고정, 반전은 카메라
                        // 전환 아이콘이며 설정 톱니바퀴를 대신하지 않는다).
                        // 안쪽 2차(핵심): 가이드 → 그리드 → 필름 시뮬레이션.
                        // 필름 시뮬레이션은 영화 포스터 문법의 세로 썸네일로 표시하고
                        // 누르면 기존 Film Market(필름 탭)으로 이동한다 — 카메라 화면에서
                        // 직접 큰 피커를 펼치지 않는다.
                        val sel = recipes.firstOrNull { it.id == easy.filmId }
                        val stackFilms = remember(pickableFilms, sel) {
                            val others = pickableFilms.filter { it.id != sel?.id }.take(2)
                            (listOf(sel) + others).filterNotNull().reversed()
                        }
                        val shutterEnd = 20.dp
                        val shutterBottom = 36.dp
                        val baseX = shutterEnd + shutterSize / 2
                        val baseY = shutterBottom + shutterSize / 2
                        androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxWidth().height(shutterSize + 250.dp),
                        ) {
                            // 셔터 뒤 은은한 다크 글로우 — 순수 검정 배경 대신
                            // 클러스터 전체에 깊이감을 주는 radial gradient.
                            FanSlot(0.dp, 40.dp, baseX, baseY, 300.dp) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.size(300.dp).clip(CircleShape).background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF3A3A44).copy(alpha = 0.65f),
                                                Color(0xFF201F24).copy(alpha = 0.25f),
                                                Color.Transparent,
                                            ),
                                        )
                                    )
                                )
                            }
                            // 왼쪽 아래: 최근 사진첩 + Film Poster Stack (별도 UI 요소).
                            androidx.compose.foundation.layout.Row(
                                Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                val lastCapture = captures.maxByOrNull { it.timestampMillis }
                                androidx.compose.foundation.layout.Box(
                                    Modifier.size(58.dp, 78.dp).clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF26262C))
                                        .border(1.dp, Color(0xFFF5F2EA).copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                        .clickable { lastCapture?.let { nav.navigate("playback?uri=${it.mediaUri}") } },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (lastCapture != null) {
                                        coil.compose.AsyncImage(
                                            model = lastCapture.mediaUri, contentDescription = "최근사진",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                    } else {
                                        Icon(Icons.Filled.PhotoLibrary, "사진첩",
                                            tint = Color(0xFFF5F2EA).copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                // Film Poster Stack — 현재 필름이 맨 앞, 뒤로 살짝씩 겹침.
                                androidx.compose.foundation.layout.Box(
                                    Modifier.width(52.dp + 7.dp * (stackFilms.size - 1)).height(76.dp),
                                ) {
                                    stackFilms.forEachIndexed { i, r ->
                                        val isFront = i == stackFilms.lastIndex
                                        PosterFace(
                                            r,
                                            Modifier.align(Alignment.BottomStart)
                                                .offset(x = 7.dp * i, y = -(4.dp * i))
                                                .size(52.dp, 76.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                    if (isFront) 1.5.dp else 0.75.dp,
                                                    if (isFront) Color(0xFFE8DCC8) else Color(0xFFF5F2EA).copy(alpha = 0.3f),
                                                    RoundedCornerShape(8.dp))
                                                .then(if (isFront) Modifier.clickable {
                                                    nav.navigate(Tab.Films.route) { launchSingleTop = true }
                                                } else Modifier),
                                        )
                                    }
                                    if (stackFilms.isEmpty()) {
                                        androidx.compose.foundation.layout.Box(
                                            Modifier.align(Alignment.BottomStart).size(52.dp, 76.dp)
                                                .clip(RoundedCornerShape(8.dp)).background(Color(0xFF26262C))
                                                .border(0.75.dp, Color(0xFFF5F2EA).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                .clickable { nav.navigate(Tab.Films.route) { launchSingleTop = true } },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Filled.Movie, "필름 시뮬레이션",
                                                tint = Color(0xFFF5F2EA).copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }

                            // 우하단 셔터를 기준으로 한 대각선 캐스케이드 — 레퍼런스
                            // 이미지와 동일한 지그재그 순서. dx=왼쪽 거리, dy=위쪽 거리.
                            val step = if (land) 0.72f else 1f
                            fun s(v: Int) = (v * step).dp
                            val photoSize = circle * 1.12f

                            // 바깥 1차: 맵 → 사진 → 동영상 → 반전 (순서 고정).
                            FanSlot(s(126), s(96), baseX, baseY, circle) {
                                NavCircle(Tab.Map.icon, "맵", current == Tab.Map.route, circle, "맵") {
                                    nav.navigate(Tab.Map.route) { launchSingleTop = true }
                                }
                            }
                            FanSlot(s(66), s(140), baseX, baseY, photoSize) {
                                NavCircle(Icons.Filled.PhotoCamera, "사진", easy.mode == CaptureMode.PHOTO, photoSize, "사진") {
                                    vm.setMode(CaptureMode.PHOTO)
                                }
                            }
                            FanSlot(s(30), s(184), baseX, baseY, circle) {
                                NavCircle(Icons.Filled.Videocam, "동영상", easy.mode == CaptureMode.VIDEO, circle, "동영상") {
                                    vm.setMode(CaptureMode.VIDEO)
                                }
                            }
                            FanSlot(s(4), s(224), baseX, baseY, circle) {
                                NavCircle(Icons.Filled.Cameraswitch, "반전", false, circle, "반전") { vm.switchLens() }
                            }

                            // 안쪽 2차(핵심): 가이드 → 그리드 → 필름 시뮬레이션 — 셔터와 가장 가깝다.
                            // (그리드·포스터가 겹치지 않도록 포스터는 옆으로 충분히 띄운다.)
                            FanSlot(s(28), s(104), baseX, baseY, circle) {
                                NavCircle(Icons.Filled.CenterFocusWeak, "가이드", easy.guideOn, circle, "가이드") { vm.toggleGuide() }
                            }
                            FanSlot(s(64), s(56), baseX, baseY, circle) {
                                GridCircle(vm, easy.gridMode, circle)
                            }
                            val filmSize = if (land) 40.dp to 55.dp else 50.dp to 68.dp
                            FanSlot(s(132), s(10), baseX, baseY, filmSize.first) {
                                FilmSimRadialButton(sel, sel != null, filmSize.first, filmSize.second) {
                                    nav.navigate(Tab.Films.route) { launchSingleTop = true }
                                }
                            }

                            // 셔터 뒤 비비드 레드 글로우 — 버튼마다 은은한 그라데이션을
                            // 주는 것과 같은 문법으로 셔터도 halo를 가진다.
                            FanSlot(0.dp, 0.dp, baseX, baseY, shutterSize + 46.dp) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.size(shutterSize + 46.dp).clip(CircleShape).background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFFE5484D).copy(alpha = 0.45f),
                                                Color.Transparent,
                                            ),
                                        )
                                    )
                                )
                            }
                            // 셔터 — 우하단, 캐스케이드의 중심. 얇은 크림 링 + 비비드 레드 그라데이션.
                            androidx.compose.foundation.layout.Box(
                                Modifier.align(Alignment.BottomEnd)
                                    .padding(end = shutterEnd, bottom = shutterBottom)
                                    .size(shutterSize)
                                    .border(3.dp, Color(0xFFF5F2EA), CircleShape)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(Color(0xFFFF6259), Color(0xFFD8383F)),
                                        )
                                    )
                                    .clickable { vm.requestCapture() }
                            )
                        }
                    } else {
                        // 카메라 탭이 아닐 때는 기존 심플 내비 바 유지.
                        androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
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
                                    NavCircle(Icons.Filled.PhotoCamera, "photo", false, circle, "사진") {
                                        nav.navigate(Tab.Camera.route) { launchSingleTop = true }
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
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(shutterSize)
                                    .border(3.dp, Color(0xFFF5F2EA), CircleShape)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF5F2EA))
                                    .clickable {
                                        nav.navigate(Tab.Camera.route) { launchSingleTop = true }
                                        vm.requestCapture()
                                    }
                            )
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
