package com.dalur.film.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Size
import android.widget.Toast
import android.provider.MediaStore
import java.util.concurrent.TimeUnit
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material.icons.filled.Palette
import com.dalur.film.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.dalur.film.film.FilmEngine
import com.dalur.film.shared.CaptureMetadata
import com.dalur.film.shared.GpsPoint
import com.dalur.film.shared.dalurFileName
import com.dalur.film.ui.components.FailureState
import com.dalur.film.ui.components.StereoBars
import com.dalur.film.ui.components.CompactMeter
import com.dalur.film.ui.components.filmTint
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.dalur.film.guide.ShotScale
import com.dalur.film.guide.ShotAnalyzer
import com.dalur.film.guide.shotScaleForFaceRatio
import com.dalur.film.guide.Headroom
import com.dalur.film.guide.SubjectPosition
import com.dalur.film.guide.LookRoom
import com.dalur.film.guide.stableHeadroom
import com.dalur.film.guide.stableSubjectPosition
import com.dalur.film.guide.stableLookRoom
import com.dalur.film.guide.pickHint
import com.dalur.film.guide.CameraAngle
import com.dalur.film.guide.stableCameraAngle
import com.dalur.film.guide.stableDutch
import com.dalur.film.guide.MovementDirection
import com.dalur.film.guide.MovementTracker
import com.dalur.film.guide.Stability
import com.dalur.film.guide.CameraMovement
import com.dalur.film.guide.stableStability
import com.dalur.film.guide.classifyCameraMovement
import com.dalur.film.guide.SilhouetteGuide
import com.dalur.film.guide.Shoot180Setup
import com.dalur.film.guide.shoot180Summary
import com.dalur.film.guide.shootRole
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EasyCameraScreen(
    vm: CameraViewModel,
    onOpenCapability: () -> Unit,
    onOpenPlayback: (String) -> Unit
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as com.dalur.film.DalurApp
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val easy by vm.easy.collectAsState()
    val pro by vm.pro.collectAsState()
    val recipes by vm.filmRecipes.collectAsState()
    val owned by app.settings.ownedRecipes.collectAsState(emptySet())
    val mirrorFrontCamera by app.settings.mirrorFrontCamera.collectAsState(true)
    val showBattery by app.settings.showBattery.collectAsState(true)
    val showStorage by app.settings.showStorage.collectAsState(true)
    val caps by vm.capabilityReport.collectAsState()
    val scheme = MaterialTheme.colorScheme

    var hasCamera by remember { mutableStateOf(false) }
    var hasMic by remember { mutableStateOf(false) }
    var hasLocation by remember { mutableStateOf(false) }
    var cameraDenied by remember { mutableStateOf(false) }
    var showExpert by remember { mutableStateOf(false) }
    var quickSettingsOpen by remember { mutableStateOf(false) }
    // Brief white flash on capture — the only visible confirmation a photo was
    // taken (it saves silently into DCIM/DALUR otherwise, which reads as "nothing
    // happened" even though the file is there).
    var captureFlash by remember { mutableStateOf(false) }
    LaunchedEffect(captureFlash) {
        if (captureFlash) {
            kotlinx.coroutines.delay(120)
            captureFlash = false
        }
    }
    // 필름 선택·그리드 버튼은 하단 메인 바(DalurNav)가 소유한다 — 여기선 안 그린다.
    // HUD param palette: which chip's values are open (null = closed).
    var hudParam by remember { mutableStateOf<String?>(null) }
    // Guide coaching cards start collapsed (one-line bar).
    var guideCardsExpanded by remember { mutableStateOf(false) }
    // Free space for recording (GB, refreshed per entry).
    val freeGb = remember {
        ((ctx.getExternalFilesDir(null)?.usableSpace ?: 0L) / 1_000_000_000L).toInt()
    }
    // ---- Device zoom (buttons above the shutter; ratios the phone supports) ----
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatios by remember { mutableStateOf(listOf(1f)) }
    var currentZoom by remember { mutableStateOf(1f) }
    fun setZoom(r: Float) {
        try {
            boundCamera?.cameraControl?.setZoomRatio(r)
            currentZoom = r
        } catch (_: Exception) { }
    }
    // Pinch-to-zoom on the viewfinder — replaces the removed zoom slider.
    fun onPinchZoom(scaleFactor: Float) {
        val zs = boundCamera?.cameraInfo?.zoomState?.value ?: return
        setZoom((currentZoom * scaleFactor).coerceIn(zs.minZoomRatio, zs.maxZoomRatio))
    }
    // ---- Exposure compensation (EV) — real CameraX control, no Camera2Interop needed ----
    var evRange by remember { mutableStateOf(-2f..2f) }
    fun applyExposure(ev: Float) {
        try {
            val cam = boundCamera ?: return
            val es = cam.cameraInfo.exposureState
            if (!es.isExposureCompensationSupported) return
            val step = es.exposureCompensationStep.toFloat()
            if (step <= 0f) return
            val idx = (ev / step).roundToInt()
                .coerceIn(es.exposureCompensationRange.lower, es.exposureCompensationRange.upper)
            cam.cameraControl.setExposureCompensationIndex(idx)
        } catch (_: Exception) { }
    }
    LaunchedEffect(boundCamera) {
        val es = boundCamera?.cameraInfo?.exposureState ?: return@LaunchedEffect
        if (es.isExposureCompensationSupported) {
            val step = es.exposureCompensationStep.toFloat()
            evRange = (es.exposureCompensationRange.lower * step)..(es.exposureCompensationRange.upper * step)
        }
    }
    LaunchedEffect(pro.exposureComp, boundCamera) { applyExposure(pro.exposureComp) }
    // ---- Integrated person guide (was the Guide tab) ----
    // Initial guideOn/gridMode come from Settings → Guide & Grid; the in-camera
    // buttons still change them per-session without writing back to that default.
    val guideOn = easy.guideOn
    val guideOnRef = rememberUpdatedState(guideOn)
    var guideTarget by remember { mutableStateOf(ShotScale.BUST) }
    var guideAuto by remember { mutableStateOf(true) }
    var guideDetected by remember { mutableStateOf<ShotScale?>(null) }
    // GUIDE Phase 1 signals — Signal layer only (stable facts), no cooldown/
    // priority yet; that Hint layer is Phase 3 (Mini Shot Coach).
    var headroomState by remember { mutableStateOf<Headroom?>(null) }
    var positionState by remember { mutableStateOf<SubjectPosition?>(null) }
    var lookRoomState by remember { mutableStateOf<LookRoom?>(null) }
    // GUIDE Phase 5 — Movement Room. remember{} so the streak survives
    // recomposition but not a fresh camera session.
    val movementTracker = remember { MovementTracker() }
    var movementState by remember { mutableStateOf(MovementDirection.NONE) }
    // Position is a fact, not a correctness judgment (a rule-of-thirds
    // placement is often deliberate) — so it only flashes briefly when it
    // CHANGES, instead of sitting on screen like the Headroom hint does.
    var positionFlashVisible by remember { mutableStateOf(false) }
    LaunchedEffect(positionState) {
        if (positionState != null) {
            positionFlashVisible = true
            kotlinx.coroutines.delay(1500)
            positionFlashVisible = false
        }
    }
    // GUIDE Phase 2 — Look Room. Same "flash on change, not a judgment" rule
    // as Subject Position (facing a certain way isn't a mistake).
    var lookRoomFlashVisible by remember { mutableStateOf(false) }
    LaunchedEffect(lookRoomState) {
        if (lookRoomState != null && lookRoomState != LookRoom.NEUTRAL) {
            lookRoomFlashVisible = true
            kotlinx.coroutines.delay(1500)
            lookRoomFlashVisible = false
        }
    }
    // GUIDE Phase 5 — Movement Room. Same flash pattern; a confirmed
    // direction is itself already debounced by MovementTracker's streak, so
    // this flash just controls how long it stays ON screen once confirmed.
    var movementFlashVisible by remember { mutableStateOf(false) }
    LaunchedEffect(movementState) {
        if (movementState != MovementDirection.NONE) {
            movementFlashVisible = true
            kotlinx.coroutines.delay(1500)
            movementFlashVisible = false
        }
    }
    // GUIDE Phase 4 — Orientation. Fully independent of the face pipeline
    // above (sensor only), so it can't ever black-screen the camera bind if
    // something here throws — everything stays inside runCatching.
    var cameraAngleState by remember { mutableStateOf<CameraAngle?>(null) }
    var isDutch by remember { mutableStateOf(false) }
    // GUIDE Phase 5 — Stability + camera movement. Differentiates the SAME
    // orientation angles Phase 4 reads (one sensor pipeline, not a second
    // raw gyroscope) rather than tracked separately.
    var stabilityState by remember { mutableStateOf<Stability?>(null) }
    var cameraMovement by remember { mutableStateOf(CameraMovement.STATIC) }
    DisposableEffect(guideOn) {
        if (!guideOn) return@DisposableEffect onDispose {}
        val sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)
        var prevAngles: FloatArray? = null
        var prevAnglesMs = 0L
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                runCatching {
                    val rotationMatrix = FloatArray(9)
                    android.hardware.SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    // Remap for a phone held upright (portrait) like a
                    // viewfinder, not lying flat — the standard Android
                    // pattern for "device tilt while held vertically".
                    val remapped = FloatArray(9)
                    android.hardware.SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        android.hardware.SensorManager.AXIS_X,
                        android.hardware.SensorManager.AXIS_Z,
                        remapped)
                    val orientationAngles = FloatArray(3)
                    android.hardware.SensorManager.getOrientation(remapped, orientationAngles)
                    val pitchDegrees = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                    val rollDegrees = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                    cameraAngleState = stableCameraAngle(pitchDegrees, cameraAngleState)
                    isDutch = stableDutch(rollDegrees, isDutch)

                    // Phase 5: rate of change since the last sample (deg/sec).
                    val nowMs = SystemClock.elapsedRealtime()
                    val prev = prevAngles
                    if (prev != null && nowMs > prevAnglesMs) {
                        val dtSec = (nowMs - prevAnglesMs) / 1000f
                        val dAzimuth = Math.toDegrees((orientationAngles[0] - prev[0]).toDouble()).toFloat()
                        val dPitch = Math.toDegrees((orientationAngles[1] - prev[1]).toDouble()).toFloat()
                        val dRoll = Math.toDegrees((orientationAngles[2] - prev[2]).toDouble()).toFloat()
                        val panRate = dAzimuth / dtSec
                        val tiltRate = dPitch / dtSec
                        val rollRate = dRoll / dtSec
                        val combinedRate = kotlin.math.abs(panRate) + kotlin.math.abs(tiltRate) + kotlin.math.abs(rollRate)
                        stabilityState = stableStability(combinedRate, stabilityState)
                        cameraMovement = classifyCameraMovement(panRate, tiltRate)
                    }
                    prevAngles = orientationAngles
                    prevAnglesMs = nowMs
                }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor, accuracy: Int) {}
        }
        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            if (rotationSensor != null) sensorManager?.unregisterListener(listener)
            cameraAngleState = null
            stabilityState = null
            cameraMovement = CameraMovement.STATIC
            isDutch = false
        }
    }
    var detectorOk by remember { mutableStateOf(true) }
    val gridMode = easy.gridMode
    LaunchedEffect(Unit) {
        vm.setGuideOn(app.settings.guideDefaultOn.first())
        vm.setGridMode(app.settings.gridDefaultMode.first())
    }
    // 180도 세팅 (세션 유지)
    var camCount by remember { mutableStateOf(1) }
    var thisCam by remember { mutableStateOf(1) }
    var show180 by remember { mutableStateOf(false) }
    val faceDetector = remember {
        try {
            FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build())
        } catch (_: Exception) {
            detectorOk = false
            null
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { faceDetector?.close() } } }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { runCatching { analysisExecutor.shutdown() } } }
    LaunchedEffect(guideDetected, guideAuto) {
        if (guideAuto && detectorOk) guideDetected?.let { guideTarget = it }
    }

    fun refreshPerms() {
        hasCamera = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        hasMic = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        hasLocation = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        cameraDenied = !hasCamera
    }
    LaunchedEffect(Unit) { refreshPerms() }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshPerms() }

    // ---- Stereo mic meter (video mode only) ----
    var meterL by remember { mutableStateOf(0f) }
    var meterR by remember { mutableStateOf(0f) }
    var meterOk by remember { mutableStateOf(true) }
    var meterWarn by remember { mutableStateOf<String?>(null) }
    var lowSince by remember { mutableStateOf(0L) }
    var hotUntil by remember { mutableStateOf(0L) }
    val warnLoud = stringResource(R.string.meter_too_loud)
    val warnQuiet = stringResource(R.string.meter_too_quiet)
    val meter = remember {
        StereoMeter(
            onLevels = { l, r, clip ->
                meterL = l
                meterR = r
                val now = SystemClock.uptimeMillis()
                if (clip) hotUntil = now + 1500
                val peak = maxOf(l, r)
                if (peak < 0.06f) {
                    if (lowSince == 0L) lowSince = now
                } else {
                    lowSince = 0L
                }
                meterWarn = when {
                    now < hotUntil || peak > 0.98f -> warnLoud
                    lowSince != 0L && now - lowSince > 2000 -> warnQuiet
                    else -> null
                }
            },
            onUnsupported = { meterOk = false }
        )
    }
    LaunchedEffect(easy.mode, hasMic) {
        if (easy.mode == CaptureMode.VIDEO && hasMic) {
            meterOk = true
            meter.start()
        } else {
            meter.stop()
            meterL = 0f
            meterR = 0f
            meterWarn = null
            lowSince = 0L
        }
    }
    DisposableEffect(Unit) { onDispose { meter.stop() } }
    // Mic permission upfront in video mode (meter needs it before recording).
    LaunchedEffect(easy.mode) {
        if (easy.mode == CaptureMode.VIDEO && !hasMic) {
            permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    LaunchedEffect(Unit) {
        val need = mutableListOf(Manifest.permission.CAMERA)
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(need.toTypedArray())
        }
    }

    // CameraX holders
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var recordStartMs by remember { mutableStateOf(0L) }
    // COMPATIBLE (TextureView) avoids a known SurfaceView black-screen bug on some
    // OEMs (esp. Samsung) once a Recorder/VideoCapture surface is also bound —
    // the default PERFORMANCE mode's SurfaceView can end up behind everything.
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    // 전면 카메라 미러링 — 미리보기만 좌우 반전(셀피를 거울처럼 보이게); 저장되는
    // 파일은 건드리지 않는다.
    LaunchedEffect(easy.lensFacing, mirrorFrontCamera) {
        previewView.scaleX =
            if (easy.lensFacing == CameraSelector.LENS_FACING_FRONT && mirrorFrontCamera) -1f else 1f
    }
    // ---- 배터리 잔량 (설정에서 표시 여부 토글) ----
    var batteryPct by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) {
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) batteryPct = (level * 100) / scale
            }
        }
        ctx.registerReceiver(receiver, filter)
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }

    // ---- Tap-to-focus — was auto-focus only, so a tap did nothing but wait. ----
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    fun onTapFocus(x: Float, y: Float) {
        try {
            val cam = boundCamera ?: return
            val point = previewView.meteringPointFactory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(
                point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            cam.cameraControl.startFocusAndMetering(action)
            focusPoint = Offset(x, y)
        } catch (_: Exception) { }
    }
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            kotlinx.coroutines.delay(800)
            focusPoint = null
        }
    }

    // Bind camera. Guide on/off NEVER rebinds (no black flash): the face
    // analyzer stays bound while available and results are gated on guideOn.
    // Resolution change rebinds so the recorder quality really applies.
    LaunchedEffect(hasCamera, easy.lensFacing, easy.mode, easy.flashOn, pro.resolution) {
        if (!hasCamera) return@LaunchedEffect
        var attempt = 0
        while (attempt < 3) {
        try {
            val provider = withContext(Dispatchers.IO) {
                ProcessCameraProvider.getInstance(ctx).get()
            }
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = CameraSelector.Builder()
                .requireLensFacing(easy.lensFacing).build()
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(if (easy.flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .build()
            // Last-resort retry (attempt 2) also drops to FHD: Preview+VideoCapture at
            // 4K exceeds many phones' supported combo and used to leave a black,
            // unbound preview (unbindAll() already ran) with no way back except
            // leaving Video mode. This still tries the user's chosen resolution first.
            val forceFhd = attempt >= 2
            val qs = if (forceFhd)
                QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD))
            else vm.qualitySelector()
            val recorder = Recorder.Builder().setQualitySelector(qs).build()
            videoCapture = VideoCapture.withOutput(recorder)
            provider.unbindAll()
            val uses = mutableListOf<androidx.camera.core.UseCase>()
            if (easy.mode == CaptureMode.PHOTO) uses += imageCapture!!
            else uses += videoCapture!!
            if (faceDetector != null && detectorOk) {
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also {
                        it.setAnalyzer(analysisExecutor,
                            ShotAnalyzer(faceDetector) { metrics ->
                                if (guideOnRef.value) {
                                    val scale = metrics?.let { shotScaleForFaceRatio(it.heightRatio) }
                                    guideDetected = scale
                                    headroomState = metrics?.let { m ->
                                        stableHeadroom(m.topRatio, scale ?: ShotScale.BUST, headroomState)
                                    }
                                    positionState = metrics?.let { m ->
                                        stableSubjectPosition(m.centerXRatio, positionState)
                                    }
                                    lookRoomState = metrics?.let { m ->
                                        stableLookRoom(m.yawDegrees, lookRoomState)
                                    }
                                    movementState = metrics?.let { m ->
                                        movementTracker.update(m.centerXRatio)
                                    } ?: run { movementTracker.reset(); MovementDirection.NONE }
                                } else {
                                    guideDetected = null
                                    headroomState = null
                                    positionState = null
                                    lookRoomState = null
                                    movementTracker.reset()
                                    movementState = MovementDirection.NONE
                                }
                            })
                    }
                uses += analysis
            } else if (faceDetector == null) {
                detectorOk = false
            }
            boundCamera = provider.bindToLifecycle(
                lifecycle, selector, preview, *uses.toTypedArray())
            // Zoom presets from what THIS phone supports.
            try {
                val zs = boundCamera?.cameraInfo?.zoomState?.value
                val lo = zs?.minZoomRatio ?: 1f
                val hi = zs?.maxZoomRatio ?: 1f
                zoomRatios = listOf(0.5f, 1f, 1.5f, 2f, 3f, 5f, 10f)
                    .filter { it >= lo * 0.99f && it <= hi * 1.01f }
                    .ifEmpty { listOf(1f) }
            } catch (_: Exception) { zoomRatios = listOf(1f) }
            currentZoom = 1f
            vm.setError(null)
            break
        } catch (e: Exception) {
            // Rebind races on recomposition are normal — don't scare the user.
            if (e is kotlinx.coroutines.CancellationException) return@LaunchedEffect
            attempt++
            if (attempt >= 3) {
                vm.setError(ctx.getString(R.string.err_camera_unavailable, e.message))
            } else {
                // attempt 1: drop the face-guide analyzer (Preview+VideoCapture+
                // ImageAnalysis exceeds many devices' supported combo).
                // attempt 2: also force FHD (see forceFhd above).
                detectorOk = false
                kotlinx.coroutines.delay(400)
            }
        }
        }
    }

    // Recording timer
    LaunchedEffect(easy.isRecording) {
        while (easy.isRecording) {
            kotlinx.coroutines.delay(500)
            vm.setRecording(true, (System.currentTimeMillis() - recordStartMs) / 1000)
        }
    }

    suspend fun currentFix(): Location? = withContext(Dispatchers.IO) {
        try { vm.locationTracker.lastFix() } catch (_: Exception) { null }
    }

    fun takePhoto() {
        val ic = imageCapture ?: run { vm.setError(ctx.getString(R.string.err_camera_starting)); return }
        captureFlash = true
        scope.launch(Dispatchers.IO) {
            try {
                val mediaId = UUID.randomUUID().toString().take(8)
                val ts = System.currentTimeMillis()
                val recipe = recipes.firstOrNull { it.id == easy.filmId }
                val outFile = File(ctx.cacheDir, dalurFileName(ts, mediaId, "jpg"))
                val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                val executor: Executor = ContextCompat.getMainExecutor(ctx)
                ic.takePicture(opts, executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                // REAL film rendering: GPUImage Plus rule-string filter on the bitmap.
                                // Honest pipeline: only supported CGE tokens are run; everything else is
                                // reported NOT_SUPPORTED on the capture metadata. No silent fallback.
                                val plan = recipe?.let { FilmEngine.plan(it, easy.filmIntensity) }
                                var filmApplied: Boolean? = null
                                var filmError: String? = null
                                if (recipe != null && plan != null) {
                                    if (plan.rule.isBlank()) {
                                        // Nothing supported to execute (recipe leans on NOT_SUPPORTED
                                        // components) — recorded truthfully, no fake PASS.
                                        filmApplied = false
                                    } else {
                                        val err = applyFilmToJpeg(outFile, plan.rule)
                                        if (err == null) filmApplied = true
                                        else {
                                            filmApplied = false
                                            filmError = err
                                        }
                                    }
                                }
                                val savedUri = saveToGallery(ctx, outFile, true, recipe?.name)
                                val fix = currentFix()
                                val meta = CaptureMetadata(
                                    mediaId = mediaId,
                                    timestampMillis = ts,
                                    gps = fix?.let {
                                        GpsPoint(it.latitude, it.longitude,
                                            if (it.hasAltitude()) it.altitude else null,
                                            if (it.hasBearing()) it.bearing.toDouble() else null,
                                            if (it.hasAccuracy()) it.accuracy else null)
                                    },
                                    locationUnavailable = fix == null,
                                    mediaType = "photo",
                                    mediaUri = savedUri,
                                    filmRecipeId = recipe?.id,
                                    filmRecipeVersion = recipe?.version,
                                    codec = null,
                                    colorProfile = "SDR",
                                    lutRecipeId = recipe?.id,
                                    lutRecipeVersion = recipe?.version,
                                    lutHash = recipe?.lut?.hash,
                                    lutIntensity = easy.filmIntensity,
                                    filmApplied = filmApplied,
                                    filmError = filmError,
                                    filmSupportReport = plan?.report
                                )
                                app.captures.insert(meta)
                                if (filmError != null) {
                                    vm.setError(ctx.getString(R.string.err_film_not_applied, filmError))
                                }
                                withContext(Dispatchers.Main) { vm.setLastCapture(savedUri) }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { vm.setError(ctx.getString(R.string.err_save_failed, e.message)) }
                            }
                        }
                    }
                    override fun onError(e: ImageCaptureException) {
                        scope.launch(Dispatchers.Main) { vm.setError(ctx.getString(R.string.err_capture_failed, e.message)) }
                    }
                })
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { vm.setError(ctx.getString(R.string.err_capture_failed, e.message)) }
            }
        }
    }

    fun toggleVideo() {
        val vc = videoCapture
        if (easy.isRecording) {
            activeRecording?.stop()
            activeRecording = null
            vm.setRecording(false)
            return
        }
        if (vc == null) { vm.setError(ctx.getString(R.string.err_video_starting)); return }
        if (!hasMic) {
            permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            vm.setError(ctx.getString(R.string.err_mic_needed))
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val mediaId = UUID.randomUUID().toString().take(8)
                val ts = System.currentTimeMillis()
                val recipe = recipes.firstOrNull { it.id == easy.filmId }
                val extDir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
                    ?: ctx.cacheDir
                val outFile = File(extDir, dalurFileName(ts, mediaId, "mp4"))
                val opts = FileOutputOptions.Builder(outFile).build()
                val pending = vc.output.prepareRecording(ctx, opts)
                    .withAudioEnabled()
                val rec = pending.start(ContextCompat.getMainExecutor(ctx)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            recordStartMs = System.currentTimeMillis()
                            scope.launch(Dispatchers.Main) { vm.setRecording(true, 0) }
                        }
                        is VideoRecordEvent.Finalize -> {
                            scope.launch(Dispatchers.IO) {
                                if (event.hasError()) {
                                    withContext(Dispatchers.Main) {
                                        vm.setRecording(false)
                                        vm.setError(ctx.getString(R.string.err_recording_failed, event.error))
                                    }
                                } else {
                                    val savedUri = saveToGallery(ctx, outFile, false, recipe?.name)
                                    val fix = currentFix()
                                    val rep = caps
                                    // Video film status: CGE filterImage_MultipleEffects is
                                    // frame-only; no real-time or post-processing video path
                                    // exists. Report honestly — never claim film was applied.
                                    val videoPlan = recipe?.let { FilmEngine.plan(it, easy.filmIntensity) }
                                    val meta = CaptureMetadata(
                                        mediaId = mediaId,
                                        timestampMillis = ts,
                                        gps = fix?.let {
                                            GpsPoint(it.latitude, it.longitude,
                                                if (it.hasAltitude()) it.altitude else null,
                                                if (it.hasBearing()) it.bearing.toDouble() else null,
                                                if (it.hasAccuracy()) it.accuracy else null)
                                        },
                                        locationUnavailable = fix == null,
                                        mediaType = "video",
                                        mediaUri = savedUri,
                                        filmRecipeId = recipe?.id,
                                        filmRecipeVersion = recipe?.version,
                                        codec = if (rep?.hevcSupported == true) "HEVC" else "H264",
                                        colorProfile = "SDR",
                                        lutRecipeId = recipe?.id,
                                        lutRecipeVersion = recipe?.version,
                                        lutHash = recipe?.lut?.hash,
                                        lutIntensity = easy.filmIntensity,
                                        filmApplied = false,
                                        filmError = if (recipe != null) "NOT_SUPPORTED: CGE filterImage_MultipleEffects is frame-only; no video pipeline" else null,
                                        filmSupportReport = videoPlan?.report
                                    )
                                    app.captures.insert(meta)
                                    withContext(Dispatchers.Main) {
                                        vm.setRecording(false)
                                        vm.setLastCapture(savedUri)
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }
                activeRecording = rec
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { vm.setError(ctx.getString(R.string.err_recording_failed, e.message)) }
            }
        }
    }

    // The global bottom-bar shutter (DalurNav) has no local camera state, so it
    // just asks; this is the only place that actually knows how to shoot.
    LaunchedEffect(Unit) {
        vm.captureRequests.collect {
            if (easy.mode == CaptureMode.PHOTO) takePhoto() else toggleVideo()
        }
    }

    @Composable
    fun ParamChip(key: String, value: String) {
        // 값+셰브런은 칩 안에, 라벨은 칩 바깥 아래에 (Final Cut Camera 포맷 패널 형태).
        val open = hudParam == key
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (open) Color(0xFF4A4A54) else Color(0xFF32323A))
                    .clickable { hudParam = if (open) null else key }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value, color = Color.White, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.UnfoldMore, null, tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(11.dp))
            }
            Spacer(Modifier.height(2.dp))
            Text(key, color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }

    /** Inline value palette for the open HUD param (RES·FPS·SHUTTER·ISO·WB). */
    @Composable
    fun ParamPalette(mod: Modifier) {
        val p = hudParam ?: return
        fun close() { hudParam = null }
        Column(mod.clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E24))
            .padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(p, style = MaterialTheme.typography.labelSmall,
                color = scheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (p) {
                    "RESOLUTION" -> items(listOf("1080p", "4K")) { v ->
                        FilterChip(selected = pro.resolution == v,
                            onClick = { vm.setResolution(v); close() },
                            label = { Text(v) })
                    }
                    "FRAMERATE" -> items(listOf(24, 30, 60)) { v ->
                        FilterChip(selected = pro.fps == v,
                            onClick = { vm.setFps(v); close() },
                            label = { Text("$v") })
                    }
                    "SHUTTER" -> {
                        val opts = listOf(null to "AUTO", (1/60.0) to "1/60",
                            (1/125.0) to "1/125", (1/250.0) to "1/250",
                            (1/500.0) to "1/500")
                        items(opts) { (v, label) ->
                            FilterChip(selected = pro.shutterSec == v,
                                onClick = { vm.setShutter(v); close() },
                                label = { Text(label) })
                        }
                    }
                    "ISO" -> {
                        val opts = listOf(null to "AUTO", 100 to "100",
                            200 to "200", 400 to "400", 800 to "800",
                            1600 to "1600", 3200 to "3200")
                        items(opts) { (v, label) ->
                            FilterChip(selected = pro.iso == v,
                                onClick = { vm.setIso(v); close() },
                                label = { Text(label) })
                        }
                    }
                    "WB" -> {
                        val opts = listOf(null to "AUTO", 3200 to "3200K",
                            4600 to "4600K", 5600 to "5600K", 6500 to "6500K")
                        items(opts) { (v, label) ->
                            FilterChip(selected = pro.wbKelvin == v,
                                onClick = { vm.setWb(v); close() },
                                label = { Text(label) })
                        }
                    }
                }
            }
        }
    }

    // GUIDE Phase 3 (mini Shot Coach) hint line — Evaluator/Priority step.
    // Each candidate is null unless ITS OWN timing says show-now (Headroom
    // and Stability stay up while the problem holds; Position/Look Room/
    // Movement Room flash briefly on change — see the LaunchedEffect+delay
    // above); pickHint just arbitrates between them in priority order and
    // returns exactly one, or none. Shared by the portrait and landscape
    // coaching cards below so the two don't drift. Null is the default
    // state, not every frame gets a hint.
    //
    // Priority, highest first: Stability (a shaky shot is a real technical
    // defect, ranks above framing) > Headroom > Subject Position > Look Room
    // > Movement Room > Orientation (a deliberate low/high/Dutch angle is a
    // creative choice far more often than a mistake, so it only shows when
    // nothing else has anything to say).
    @Composable
    fun GuideSignalHint() {
        val stabilityHint = if (stabilityState == Stability.SHAKY) stringResource(R.string.hint_shaky) else null
        val headroomHint = when (headroomState) {
            Headroom.TOO_TIGHT -> stringResource(R.string.hint_headroom_too_tight)
            Headroom.TOO_MUCH -> stringResource(R.string.hint_headroom_too_much)
            else -> null
        }
        val positionHint = if (positionFlashVisible) when (positionState) {
            SubjectPosition.LEFT_THIRD -> stringResource(R.string.hint_position_left)
            SubjectPosition.CENTER -> stringResource(R.string.hint_position_center)
            SubjectPosition.RIGHT_THIRD -> stringResource(R.string.hint_position_right)
            null -> null
        } else null
        val lookRoomHint = if (lookRoomFlashVisible) when (lookRoomState) {
            LookRoom.LOOK_LEFT -> stringResource(R.string.hint_look_left)
            LookRoom.LOOK_RIGHT -> stringResource(R.string.hint_look_right)
            else -> null
        } else null
        val movementHint = if (movementFlashVisible) when (movementState) {
            MovementDirection.LEFT -> stringResource(R.string.hint_movement_left)
            MovementDirection.RIGHT -> stringResource(R.string.hint_movement_right)
            MovementDirection.NONE -> null
        } else null
        val orientationHint = when {
            isDutch -> stringResource(R.string.hint_dutch)
            cameraAngleState == CameraAngle.HIGH_ANGLE -> stringResource(R.string.hint_angle_high)
            cameraAngleState == CameraAngle.LOW_ANGLE -> stringResource(R.string.hint_angle_low)
            cameraAngleState == CameraAngle.TOP_DOWN -> stringResource(R.string.hint_angle_top_down)
            cameraAngleState == CameraAngle.EXTREME_LOW -> stringResource(R.string.hint_angle_extreme_low)
            else -> null
        }
        val text = pickHint(listOf(
            stabilityHint, headroomHint, positionHint, lookRoomHint, movementHint, orientationHint))
        if (text != null) {
            Text(text, color = Color(0xFFE8B93A),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }
    }

    // EV ruler (sun icon + value, drag the tick strip to scrub): the one pro
    // control with an actual CameraX hook (setExposureCompensationIndex) and no
    // home elsewhere.
    @Composable
    fun AuxControlRow() {
        val amber = Color(0xFFE8B93A)
        val range = evRange
        val span = (range.endInclusive - range.start).coerceAtLeast(0.01f)
        Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WbSunny, "EV", tint = amber, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(3.dp))
                Text("%+.1f".format(pro.exposureComp),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    color = amber)
            }
            Spacer(Modifier.height(1.dp))
            // 눈금 자체는 얇게 유지하되, 터치 영역은 위아래로 더 넓게 잡아서
            // (26.dp 패딩 상자 안에 12.dp 캔버스) 손가락으로 눌러 잡기 쉽게 한다.
            Box(
                Modifier.fillMaxWidth(0.5f).align(Alignment.CenterHorizontally)
                    .height(26.dp)
                    .pointerInput(range) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaEv = (dragAmount / size.width) * span
                            vm.setExposure((pro.exposureComp + deltaEv).coerceIn(range))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
            Canvas(
                Modifier.fillMaxWidth().height(12.dp)
            ) {
                val tickCount = 25
                val w = size.width
                val h = size.height
                val activeFrac = ((pro.exposureComp - range.start) / span).coerceIn(0f, 1f)
                val activeIndex = (activeFrac * (tickCount - 1)).roundToInt()
                for (i in 0 until tickCount) {
                    val x = i / (tickCount - 1).toFloat() * w
                    val active = i == activeIndex
                    val tickH = if (active) h else h * 0.5f
                    drawLine(
                        color = if (active) amber else Color.White.copy(alpha = 0.35f),
                        start = Offset(x, h / 2f - tickH / 2f),
                        end = Offset(x, h / 2f + tickH / 2f),
                        strokeWidth = if (active) 3f else 1.5f
                    )
                }
            }
            }
        }
    }

    // 그리드 버튼도 필름 선택과 마찬가지로 하단 메인 바(DalurNav)가 소유한다.

    // ---- Cinematic layout: full-bleed viewfinder, floating minimal chrome ----
    // Preview always fills the whole screen; top/bottom controls float over a
    // gradient scrim so the visible frame stays maximal (design: fullscreen +
    // minimal overlay). In VIDEO the 16:9 record frame is drawn centered.
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFF0B0B0D))
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ -> onPinchZoom(zoom) }
            }
            .pointerInput(hasCamera) {
                if (hasCamera) {
                    detectTapGestures(onTap = { offset -> onTapFocus(offset.x, offset.y) })
                }
            }
    ) {
        val landscape = maxWidth > maxHeight
        // Viewfinder layer (full-screen, never shrunk by panels).
        if (hasCamera) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            if (captureFlash) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.85f)))
            }
            // Tap-to-focus ring — was auto-focus only, no feedback that a tap did
            // anything, so people waited for the lens to settle on its own.
            focusPoint?.let { p ->
                val density = LocalDensity.current
                Box(
                    Modifier
                        .offset(
                            x = with(density) { p.x.toDp() } - 32.dp,
                            y = with(density) { p.y.toDp() } - 32.dp)
                        .size(64.dp)
                        .border(1.5.dp, Color(0xFFE8B93A), RoundedCornerShape(4.dp))
                )
            }
            // Live film tint overlay (preview approximation; photo output uses the real GPU filter).
            val recipe = recipes.firstOrNull { it.id == easy.filmId }
            if (recipe != null && easy.filmIntensity > 0.01f) {
                FilmPreviewOverlay(recipeId = recipe.id, alpha = 0.10f * easy.filmIntensity)
            }
            // Person-guide silhouette + headroom (fits the 9:16 frame in shorts modes).
            // (Frame grids live topmost, after the bottom deck, so HUD/deck
            // never bury their borders/labels.)
            if (guideOn) {
                SilhouetteGuide(guideTarget, shorts = gridMode == 3, safeZone = gridMode == 4)
            }
            // Thirds grid.
            if (gridMode == 1) {
                FrameGuides()
            }
        } else {
            FailureState(
                title = "Camera permission needed",
                body = "Allow camera access to shoot. Location and microphone stay optional until you need them.",
                action = "Grant camera"
            ) { permLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }
        }

        // Top floating HUD: slim scrim + single row (REC · FPS/ISO/shutter · icons).
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(96.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B0B0D).copy(alpha = 0.72f), Color.Transparent)
                    )
                )
        )
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (easy.isRecording) {
                StageHud(text = "● REC %02d:%02d".format(
                    easy.recordSeconds / 60, easy.recordSeconds % 60), accent = scheme.error)
            } else {
                Text("DALUR film", style = MaterialTheme.typography.titleMedium,
                    color = scheme.onBackground)
            }
            // Stereo meter beside the logo (video mode, compact).
            if (easy.mode == CaptureMode.VIDEO && hasMic && meterOk) {
                Spacer(Modifier.width(8.dp))
                CompactMeter(l = meterL, r = meterR)
            }
            // Free space for recording (설정에서 표시 끌 수 있음).
            if (showStorage) {
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.free_gb, freeGb),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant, maxLines = 1)
            }
            // 배터리 잔량 (설정에서 표시 끌 수 있음).
            if (showBattery && batteryPct != null) {
                Spacer(Modifier.width(8.dp))
                Text("${batteryPct}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            // PRO의 fps/ISO/셔터는 하단 덱에 이미 전용 줄로 있다(중복 제거) —
            // 이 타이틀 행에 같이 욱여넣으면 폭이 좁은 화면에서 톱니바퀴·
            // 펼침 버튼이 밀려나 탭이 안 되는 문제가 있었다.
            IconButton(onClick = onOpenCapability, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Info, "capability", tint = scheme.onBackground)
            }
            IconButton(onClick = { showExpert = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Settings, "settings", tint = scheme.onBackground)
            }
            // Reveals the RES/FPS/SHUTTER/ISO/WB strip below, iPhone Camera-style
            // (was always-on; now tucked behind the settings gear).
            IconButton(onClick = { quickSettingsOpen = !quickSettingsOpen }, modifier = Modifier.size(36.dp)) {
                Icon(if (quickSettingsOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    "quick settings", tint = scheme.onBackground)
            }
        }
        // RES/FPS/SHUTTER/ISO/WB capsule (tap the settings-gear chevron to reveal) —
        // one rounded strip with a close (X) and confirm (✓) bookend, like iPhone
        // Camera's format panel, instead of a bare row of chips.
        if (quickSettingsOpen) {
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .padding(horizontal = 12.dp).padding(top = 54.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1E1E24).copy(alpha = 0.92f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { quickSettingsOpen = false }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, "close", tint = Color.White)
            }
            LazyRow(Modifier.weight(1f).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { ParamChip("RESOLUTION", pro.resolution) }
                item { ParamChip("FRAMERATE", "${pro.fps} FPS") }
                item {
                    val sv = pro.shutterSec?.let { "1/${(1.0 / it).roundToInt()}" } ?: "AUTO"
                    ParamChip("SHUTTER", sv)
                }
                item { ParamChip("ISO", pro.iso?.toString() ?: "AUTO") }
                item { ParamChip("WB", pro.wbKelvin?.let { "${it}K" } ?: "AUTO") }
            }
            IconButton(onClick = { quickSettingsOpen = false }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Check, "confirm", tint = Color.White)
            }
        }
        }

        // Level warning stays centered under the HUD rows.
        if (easy.mode == CaptureMode.VIDEO && meterWarn != null) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .padding(top = 136.dp),
                contentAlignment = Alignment.Center) {
                Text(meterWarn!!, color = scheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold)
            }
        }

        easy.error?.let {
            Card(Modifier.align(Alignment.Center).padding(16.dp)) {
                Text(it, Modifier.padding(12.dp))
            }
        }

    // Bottom floating deck. Portrait only — landscape uses the right-side strip below.
    // Pro tools live in Settings → 도구 now (one home, not duplicated here);
    // this is just a passive status line for when Pro is on.
        if (!landscape) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xFF0B0B0D).copy(alpha = 0.88f))
                    )
                )
        ) {
            if (easy.isPro) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${pro.resolution} · ${pro.fps}fps · ${pro.codecLabel}",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            // Open HUD param palette, right above the zoom presets.
            if (hudParam != null) {
                ParamPalette(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            }
            AuxControlRow()
            Spacer(Modifier.height(10.dp))
            // Zoom presets (device-supported ratios only), right above the shutter.
            if (zoomRatios.size > 1) {
                Row(Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    zoomRatios.forEach { r ->
                        val sel = r == currentZoom
                        val label = if (r % 1f == 0f) "${r.toInt()}x" else "${r}x"
                        Text(label,
                            Modifier.clickable { setZoom(r) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            color = if (sel) scheme.primary
                            else Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            // 가이드·캠코더·셔터·스틸·그리드 + 사진첩·카메라·필름·지도는 모두
            // 하단 바(DalurNav) 한 덩어리에 있다 — 셔터는 두 줄 가운데 하나뿐.
            // Person-guide coaching card (was the Guide tab).
            if (guideOn) {
                val gdet = guideDetected
                var gmsg = ""
                val gicon = when {
                    !detectorOk -> {
                        gmsg = ctx.getString(R.string.coach_unsupported)
                        Icons.Filled.PersonSearch
                    }
                    gdet == null -> {
                        // 얼굴 없을 때 자막 없이 아이콘만 — 조용한 게 기본 상태.
                        Icons.Filled.PersonSearch
                    }
                    guideTarget.ordinal > gdet.ordinal -> {
                        gmsg = ctx.getString(R.string.coach_step_back)
                        Icons.Filled.KeyboardDoubleArrowDown
                    }
                    guideTarget.ordinal < gdet.ordinal -> {
                        gmsg = ctx.getString(R.string.coach_come_closer)
                        Icons.Filled.KeyboardDoubleArrowUp
                    }
                    else -> {
                        gmsg = ctx.getString(R.string.coach_perfect)
                        Icons.Filled.CheckCircle
                    }
                }
                // 작게 + 우측 정렬 — 하단 바의 "가이드" 버튼이 화면 오른쪽에
                // 있으므로, 그 버튼 바로 위에 오도록 이 카드도 오른쪽에 붙인다.
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.End) {
                Column(Modifier.width(190.dp)) {
                    Row(Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E1E24))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(gicon, null, tint = scheme.primary,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        if (gmsg.isNotBlank()) {
                            Text(gmsg,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onBackground,
                                maxLines = 1,
                                modifier = Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        TextButton(onClick = { guideCardsExpanded = !guideCardsExpanded },
                            contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text(if (guideCardsExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { show180 = true },
                            contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text(stringResource(R.string.guide_180_short, thisCam, camCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.primary)
                        }
                    }
                    GuideSignalHint()
                    if (guideCardsExpanded) {
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(selected = guideAuto,
                                onClick = { guideAuto = true },
                                label = { Text(stringResource(R.string.recommend)) })
                        }
                        items(ShotScale.values()) { s ->
                            FilterChip(
                                selected = !guideAuto && guideTarget == s,
                                onClick = { guideAuto = false; guideTarget = s },
                                label = { Text(stringResource(s.labelRes)) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                guideAuto = false
                                val i = (guideTarget.ordinal - 1).coerceAtLeast(0)
                                guideTarget = ShotScale.values()[i]
                            },
                            enabled = guideTarget.ordinal > 0,
                            modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.step_forward))
                        }
                        OutlinedButton(
                            onClick = {
                                guideAuto = false
                                val i = (guideTarget.ordinal + 1)
                                    .coerceAtMost(ShotScale.values().lastIndex)
                                guideTarget = ShotScale.values()[i]
                            },
                            enabled = guideTarget.ordinal < ShotScale.values().lastIndex,
                            modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.step_back))
                        }
                    }
                    }
                }
                }
            }
            // 필름 선택은 하단 메인 바(DalurNav)에 붙어 있다 — 여기선 아무것도 안 그린다.
        }
        // Landscape: right-side control strip + floating panels (frame stays clear).
        if (landscape) {
            // 노출(5) · 배율줌(4) — bottom-left of the free area, 노출이 위.
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(start = 12.dp, end = 100.dp, bottom = 8.dp)) {
                AuxControlRow()
                if (zoomRatios.size > 1) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        zoomRatios.forEach { r ->
                            val sel = r == currentZoom
                            val label = if (r % 1f == 0f) "${r.toInt()}x" else "${r}x"
                            Text(label,
                                Modifier.clickable { setZoom(r) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                color = if (sel) scheme.primary
                                else Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
            // Guide coaching, bottom-left floating panel.
            if (guideOn) {
                val gdet = guideDetected
                var gmsg = ""
                val gicon = when {
                    !detectorOk -> {
                        gmsg = ctx.getString(R.string.coach_unsupported)
                        Icons.Filled.PersonSearch
                    }
                    gdet == null -> {
                        // 얼굴 없을 때 자막 없이 아이콘만 — 조용한 게 기본 상태.
                        Icons.Filled.PersonSearch
                    }
                    guideTarget.ordinal > gdet.ordinal -> {
                        gmsg = ctx.getString(R.string.coach_step_back)
                        Icons.Filled.KeyboardDoubleArrowDown
                    }
                    guideTarget.ordinal < gdet.ordinal -> {
                        gmsg = ctx.getString(R.string.coach_come_closer)
                        Icons.Filled.KeyboardDoubleArrowUp
                    }
                    else -> {
                        gmsg = ctx.getString(R.string.coach_perfect)
                        Icons.Filled.CheckCircle
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).width(330.dp)
                    .padding(start = 12.dp, end = 12.dp, bottom = 52.dp)) {
                    Row(Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1E1E24))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(gicon, null, tint = scheme.primary,
                            modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        if (gmsg.isNotBlank()) {
                            Text(gmsg,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onBackground,
                                modifier = Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        TextButton(onClick = { guideCardsExpanded = !guideCardsExpanded }) {
                            Text(if (guideCardsExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                                color = scheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { show180 = true }) {
                            Text(stringResource(R.string.guide_180_short, thisCam, camCount),
                                color = scheme.primary)
                        }
                    }
                    GuideSignalHint()
                    if (guideCardsExpanded) {
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(selected = guideAuto,
                                onClick = { guideAuto = true },
                                label = { Text(stringResource(R.string.recommend)) })
                        }
                        items(ShotScale.values()) { s ->
                            FilterChip(
                                selected = !guideAuto && guideTarget == s,
                                onClick = { guideAuto = false; guideTarget = s },
                                label = { Text(stringResource(s.labelRes)) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                guideAuto = false
                                val i = (guideTarget.ordinal - 1).coerceAtLeast(0)
                                guideTarget = ShotScale.values()[i]
                            },
                            enabled = guideTarget.ordinal > 0,
                            modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.step_forward))
                        }
                        OutlinedButton(
                            onClick = {
                                guideAuto = false
                                val i = (guideTarget.ordinal + 1)
                                    .coerceAtMost(ShotScale.values().lastIndex)
                                guideTarget = ShotScale.values()[i]
                            },
                            enabled = guideTarget.ordinal < ShotScale.values().lastIndex,
                            modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.step_back))
                        }
                    }
                    }
                }
            }
            // Open HUD param palette, left floating panel.
            if (hudParam != null) {
                ParamPalette(Modifier.align(Alignment.CenterStart).width(340.dp)
                    .padding(start = 12.dp, end = 12.dp))
            }
            // 가이드·동영상·셔터·사진·그리드·반전은 모두 하단 바(DalurNav) 한
            // 덩어리에 있다 — 여기서 다시 그리면 중복이라 지웠다.
        }
        // Kino-style REC frame: red edge hugging the rounded screen while recording.
        if (easy.isRecording) {
            Box(Modifier.fillMaxSize()
                .padding(1.dp)
                .border(4.dp, Color(0xFFE5484D), RoundedCornerShape(28.dp)))
        }
        // Frame grids, topmost: HUD and bottom deck never bury their borders.
        // (Grid button cycles 0 off · 1 thirds · 2 16:9 · 3 9:16 · 4 shorts-UI · 5 4:3.)
        if (hasCamera) {
            if (gridMode == 2) {
                SixteenNineGuide(recording = easy.isRecording)
            }
            if (gridMode == 3) {
                VerticalNineSixteen()
            }
            if (gridMode == 4) {
                ShortsSafety()
            }
            if (gridMode == 5) {
                FourThreeGuide()
            }
        }
        // 180도 세팅 오버레이.
        if (show180) {
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
                Shoot180Setup(
                    camCount = camCount,
                    thisCam = thisCam,
                    onCount = { n ->
                        camCount = n
                        if (thisCam > n) thisCam = n
                    },
                    onThisCam = { thisCam = it },
                    onClose = { show180 = false },
                )
            }
        }
        // Expert menu overlay (gear button).
        if (showExpert) {
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
                ExpertMenuSheet(
                    vm = vm,
                    onClose = { showExpert = false },
                    onOpenCapability = onOpenCapability,
                    onRequestMic = {
                        permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    },
                )
            }
        }
    }
}

/** Parallel stereo mic meter (VOICE_RECOGNITION). Best-effort: if the device
 *  won't share the mic, [onUnsupported] fires and the UI hides the meter —
 *  never fake bars. Levels are 0..1 mapped from dBFS (-50dB floor). */
class StereoMeter(
    val onLevels: (l: Float, r: Float, clip: Boolean) -> Unit,
    val onUnsupported: () -> Unit,
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ run() }, "stereo-meter").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun run() {
        // Recorder start briefly preempts the mic — retry a few times before
        // giving up, so the bars survive record start instead of vanishing.
        try {
            var attempts = 0
            while (running && attempts < 5) {
                attempts++
                if (runSession()) return
                if (running) {
                    try {
                        Thread.sleep(500)
                    } catch (_: InterruptedException) {
                        return
                    }
                }
            }
            if (running) onUnsupported()
        } catch (_: MeterUnsupported) {
            onUnsupported()
        } catch (_: Exception) {
            if (running) onUnsupported()
        }
    }

    private class MeterUnsupported : Exception()

    /** true = clean stop (user asked). false = session broke, retry if still running. */
    private fun runSession(): Boolean {
        val sr = 44100
        val minBuf = AudioRecord.getMinBufferSize(
            sr, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) throw MeterUnsupported()
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sr, AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { rec.release() }
            return false
        }
        try {
            val buf = ShortArray(minBuf / 2)
            rec.startRecording()
            var lastPush = 0L
            var accL = 0.0
            var accR = 0.0
            var n = 0L
            var clip = false
            while (running) {
                val read = rec.read(buf, 0, buf.size)
                if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                    read == AudioRecord.ERROR_BAD_VALUE) return false
                if (read <= 0) continue
                var i = 0
                while (i + 1 < read) {
                    val sL = buf[i].toInt()
                    val sR = buf[i + 1].toInt()
                    accL += sL * sL.toDouble()
                    accR += sR * sR.toDouble()
                    n++
                    if (sL > 32000 || sL < -32000 || sR > 32000 || sR < -32000) clip = true
                    i += 2
                }
                val now = SystemClock.uptimeMillis()
                if (now - lastPush >= 100 && n > 0) {
                    onLevels(db01(accL / n), db01(accR / n), clip)
                    accL = 0.0
                    accR = 0.0
                    n = 0
                    clip = false
                    lastPush = now
                }
            }
            return true
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
    }

    private fun db01(meanSquare: Double): Float {
        val rms = sqrt(meanSquare) / 32768.0
        if (rms <= 0.00001) return 0f
        return (((20 * log10(rms)).toFloat() + 50f) / 50f).coerceIn(0f, 1f)
    }
}

/** Centered vertical 9:16 safe frame (for Shorts cut-outs), Canvas-drawn.
 *  9:16 rect fitted INSIDE the screen (tall phones are narrower than 16:9). */
@Composable
private fun VerticalNineSixteen() {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val fw = minOf(w, h * 9f / 16f)
        val fh = fw * 16f / 9f
        val left = (w - fw) / 2f
        val top = (h - fh) / 2f
        val dim = Color.Black.copy(alpha = 0.45f)
        // Dim outside the frame (4 rects).
        drawRect(dim, androidx.compose.ui.geometry.Offset(0f, 0f),
            androidx.compose.ui.geometry.Size(w, top))
        drawRect(dim, androidx.compose.ui.geometry.Offset(0f, top + fh),
            androidx.compose.ui.geometry.Size(w, h - top - fh))
        drawRect(dim, androidx.compose.ui.geometry.Offset(0f, top),
            androidx.compose.ui.geometry.Size(left, fh))
        drawRect(dim, androidx.compose.ui.geometry.Offset(left + fw, top),
            androidx.compose.ui.geometry.Size(w - left - fw, fh))
        // Frame border + label.
        drawRect(Color.White.copy(alpha = 0.55f),
            androidx.compose.ui.geometry.Offset(left, top),
            androidx.compose.ui.geometry.Size(fw, fh),
            style = Stroke(width = 2f))
        drawText(measurer, "9:16",
            topLeft = androidx.compose.ui.geometry.Offset(left + fw - 90f, top + 12f),
            style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp))
    }
}

/** Centered 4:3 photo-crop guide (shown as a 3:4 box in portrait), Canvas-drawn.
 *  Shorter than the 9:16 guide, so it dims more top/bottom on a tall screen. */
@Composable
private fun FourThreeGuide() {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val fw = minOf(w, h * 3f / 4f)
        val fh = fw * 4f / 3f
        val left = (w - fw) / 2f
        val top = (h - fh) / 2f
        val dim = Color.Black.copy(alpha = 0.45f)
        // Dim outside the frame (4 rects).
        drawRect(dim, androidx.compose.ui.geometry.Offset(0f, 0f),
            androidx.compose.ui.geometry.Size(w, top))
        drawRect(dim, androidx.compose.ui.geometry.Offset(0f, top + fh),
            androidx.compose.ui.geometry.Size(w, h - top - fh))
        drawRect(dim, androidx.compose.ui.geometry.Offset(0f, top),
            androidx.compose.ui.geometry.Size(left, fh))
        drawRect(dim, androidx.compose.ui.geometry.Offset(left + fw, top),
            androidx.compose.ui.geometry.Size(w - left - fw, fh))
        // Frame border + label.
        drawRect(Color.White.copy(alpha = 0.55f),
            androidx.compose.ui.geometry.Offset(left, top),
            androidx.compose.ui.geometry.Size(fw, fh),
            style = Stroke(width = 2f))
        drawText(measurer, "4:3",
            topLeft = androidx.compose.ui.geometry.Offset(left + fw - 90f, top + 12f),
            style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp))
    }
}

/** Shorts/Reels/TikTok UI safety zones — same two-white-line language as the
 *  16:9 guide: one line marks where the top UI (search/notifications) starts,
 *  one marks where the caption/progress zone starts. Dims outside both. */
@Composable
private fun ShortsSafety() {
    val measurer = rememberTextMeasurer()
    // 상단 HUD(DALUR film 타이틀 등)와 겹치지 않도록 96dp 아래에서 시작.
    val topClear = with(LocalDensity.current) { 104.dp.toPx() }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val topLine = maxOf(h * 0.10f, topClear)
        val bottomLine = h * 0.78f
        val dim = Color.Black.copy(alpha = 0.45f)
        val edge = Color.White
        // Top UI zone: search / notifications.
        drawRect(dim, androidx.compose.ui.geometry.Offset(0f, 0f),
            androidx.compose.ui.geometry.Size(w, topLine))
        // Bottom caption zone: captions / progress bar.
        drawRect(dim, androidx.compose.ui.geometry.Offset(0f, bottomLine),
            androidx.compose.ui.geometry.Size(w, h - bottomLine))
        // The two safety lines.
        drawLine(edge, androidx.compose.ui.geometry.Offset(0f, topLine),
            androidx.compose.ui.geometry.Offset(w, topLine), strokeWidth = 3f)
        drawLine(edge, androidx.compose.ui.geometry.Offset(0f, bottomLine),
            androidx.compose.ui.geometry.Offset(w, bottomLine), strokeWidth = 3f)
        drawText(measurer, "SHORTS · REELS · TIKTOK",
            topLeft = androidx.compose.ui.geometry.Offset(16f, topLine + 10f),
            style = TextStyle(color = edge.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold))
    }
}

/** Minimal stage-readout chip (REC/FPS/ISO/shutter), stepped by stage relevance. */
@Composable
private fun StageHud(text: String, accent: Color = Color(0xFFF5F2EA)) {
    Box(Modifier.clip(RoundedCornerShape(6.dp))
        .background(Color(0xFF1E1E24))
        .padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, color = accent, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FilmPreviewOverlay(recipeId: String, alpha: Float) {
    Box(Modifier.fillMaxSize().background(filmTint(recipeId).copy(alpha = alpha)))
}

@Composable
private fun SixteenNineGuide(recording: Boolean) {
    // 16:9 record frame fitted INSIDE the screen (portrait or landscape).
    // Dims the area outside so WYSIWYG holds in VIDEO mode.
    // Idle = white (was green — too easy to lose against the preview), recording = red.
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Video record area: portrait = full width (9:16 tall), landscape =
        // full height (16:9 wide). Fitted inside, centered.
        val fw: Float
        val fh: Float
        // 16:9 = width:height, so on a portrait screen the frame is a SHORT wide
        // band (height = w*9/16) — was inverted to w*16/9 (taller than the
        // screen itself), which made the "frame" always fill the whole
        // preview and the guide effectively invisible.
        if (w <= h) { fw = w; fh = minOf(h, w * 9f / 16f) } else { fh = h; fw = minOf(w, h * 16f / 9f) }
        val left = (w - fw) / 2f
        val top = (h - fh) / 2f
        val dim = Color.Black.copy(alpha = 0.45f)
        val edge = if (recording) Color(0xFFE5484D) else Color.White
        drawRect(dim, androidx.compose.ui.geometry.Offset(0f, 0f),
            androidx.compose.ui.geometry.Size(w, top))
        drawRect(dim, androidx.compose.ui.geometry.Offset(0f, top + fh),
            androidx.compose.ui.geometry.Size(w, h - top - fh))
        drawRect(dim, androidx.compose.ui.geometry.Offset(0f, top),
            androidx.compose.ui.geometry.Size(left, fh))
        drawRect(dim, androidx.compose.ui.geometry.Offset(left + fw, top),
            androidx.compose.ui.geometry.Size(w - left - fw, fh))
        drawRect(edge,
            androidx.compose.ui.geometry.Offset(left, top),
            androidx.compose.ui.geometry.Size(fw, fh),
            style = Stroke(width = 3f))
        drawText(measurer, "16:9",
            topLeft = androidx.compose.ui.geometry.Offset(left + fw - 90f, top + 12f),
            style = TextStyle(color = edge.copy(alpha = 0.9f), fontSize = 13.sp))
    }
}

@Composable
private fun FrameGuides() {
    // Thirds grid.
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val line = Color.White.copy(alpha = 0.28f)
        val w3 = size.width / 3f
        val h3 = size.height / 3f
        drawLine(line, androidx.compose.ui.geometry.Offset(w3, 0f),
            androidx.compose.ui.geometry.Offset(w3, size.height), strokeWidth = 1f)
        drawLine(line, androidx.compose.ui.geometry.Offset(2 * w3, 0f),
            androidx.compose.ui.geometry.Offset(2 * w3, size.height), strokeWidth = 1f)
        drawLine(line, androidx.compose.ui.geometry.Offset(0f, h3),
            androidx.compose.ui.geometry.Offset(size.width, h3), strokeWidth = 1f)
        drawLine(line, androidx.compose.ui.geometry.Offset(0f, 2 * h3),
            androidx.compose.ui.geometry.Offset(size.width, 2 * h3), strokeWidth = 1f)
    }
}

/** Applies a supported CGE rule string to the captured JPEG in place.
 *  Returns null on success; an error message on failure. NEVER silently returns the
 *  original bitmap — a failed film pass is reported, not hidden. */
private fun applyFilmToJpeg(file: File, rule: String): String? {
    if (rule.isBlank()) return null
    val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        ?: return "could not decode captured JPEG"
    return try {
        // CGENativeLibrary rule-string filtering (GPUImage Plus).
        val out = org.wysaid.nativePort.CGENativeLibrary.filterImage_MultipleEffects(bmp, rule, 1.0f)
        File(file.absolutePath).outputStream().use { os ->
            out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, os)
        }
        if (!out.isRecycled) out.recycle()
        null
    } catch (e: Throwable) {
        "filter failed: ${e.message}"
    } finally {
        if (!bmp.isRecycled) bmp.recycle()
    }
}

private fun saveToGallery(ctx: Context, file: File, isPhoto: Boolean, filmName: String?): String {
    val resolver = ctx.contentResolver
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val coll = if (isPhoto) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isPhoto) "image/jpeg" else "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                if (isPhoto) "DCIM/DALUR" else "Movies/DALUR")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(coll, values)!!
        resolver.openOutputStream(uri)!!.use { os ->
            file.inputStream().use { it.copyTo(os) }
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri.toString()
    } else {
        @Suppress("DEPRECATION")
        android.media.MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), null, null)
        file.toUri().toString()
    }
}
