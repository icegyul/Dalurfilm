package com.dalur.film.guide

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.dalur.film.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dalur.film.camera.CameraViewModel
import com.dalur.film.camera.CaptureMode
import com.dalur.film.shared.CaptureMetadata
import com.dalur.film.ui.components.FailureState
import com.dalur.film.ui.components.ModeSideButton
import com.dalur.film.ui.components.StereoBars
import com.dalur.film.camera.StereoMeter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shot scales, ordered near → far. Ordinal doubles as distance rank. */
enum class ShotScale(val labelRes: Int, val tipRes: Int) {
    CLOSE_UP(R.string.shot_close_up, R.string.shot_close_up_tip),
    BUST(R.string.shot_bust, R.string.shot_bust_tip),
    KNEE(R.string.shot_knee, R.string.shot_knee_tip),
    FULL(R.string.shot_full, R.string.shot_full_tip),
    EXTREME_FULL(R.string.shot_extreme_full, R.string.shot_extreme_full_tip),
}

/** Face-height / frame-height → shot scale. Heuristic for on-device coaching. */
fun shotScaleForFaceRatio(ratio: Float): ShotScale = when {
    ratio >= 0.45f -> ShotScale.CLOSE_UP
    ratio >= 0.18f -> ShotScale.BUST
    ratio >= 0.08f -> ShotScale.KNEE
    ratio >= 0.04f -> ShotScale.FULL
    else -> ShotScale.EXTREME_FULL
}

@OptIn(ExperimentalGetImage::class)
class ShotAnalyzer(
    private val detector: FaceDetector,
    // GUIDE Phase 1: was just a height ratio for ShotScale; now the full
    // FaceMetrics so Headroom/SubjectPosition/LookRoom can be derived from
    // the SAME analyzer pass, no second analyzer bound.
    private val onFace: (FaceMetrics?) -> Unit,
) : ImageAnalysis.Analyzer {
    private var lastMs = 0L
    override fun analyze(proxy: ImageProxy) {
        val now = SystemClock.uptimeMillis()
        val frameW: Int
        val frameH: Int
        val media = proxy.image
        if (now - lastMs < 400 || media == null) {
            proxy.close()
            return
        }
        lastMs = now
        // Capture dimensions now; the success listener runs after close().
        val rotated = proxy.imageInfo.rotationDegrees % 180 != 0
        frameW = if (rotated) proxy.height else proxy.width
        frameH = if (rotated) proxy.width else proxy.height
        try {
            val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
            detector.process(input)
                .addOnSuccessListener { faces ->
                    val biggest = faces.maxByOrNull { it.boundingBox.height() }
                    onFace(biggest?.let { f ->
                        val box = f.boundingBox
                        FaceMetrics(
                            heightRatio = box.height().toFloat() / frameH,
                            centerXRatio = box.exactCenterX() / frameW,
                            topRatio = box.top.toFloat() / frameH,
                            yawDegrees = f.headEulerAngleY,
                        )
                    })
                }
                .addOnFailureListener { onFace(null) }
                .addOnCompleteListener { proxy.close() }
        } catch (_: Exception) {
            onFace(null)
            proxy.close()
        }
    }
}

@Composable
fun GuideScreen(vm: CameraViewModel, onOpenPlayback: (String) -> Unit) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val easy by vm.easy.collectAsState()
    val scheme = MaterialTheme.colorScheme

    var hasCamera by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted -> hasCamera = granted }
    LaunchedEffect(Unit) {
        if (!hasCamera) permLauncher.launch(Manifest.permission.CAMERA)
    }
    // Photo / video mode (same side-button UX as the camera tab).
    var guideMode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var hasMic by remember { mutableStateOf(false) }
    fun refreshMic() {
        hasMic = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { refreshMic() }
    LaunchedEffect(Unit) { refreshMic() }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableStateOf(0L) }
    var recordStartMs by remember { mutableStateOf(0L) }

    // ---- Stereo mic meter (video mode; survives record start via retries) ----
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
    LaunchedEffect(guideMode, hasMic) {
        if (guideMode == CaptureMode.VIDEO && hasMic) {
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
    LaunchedEffect(guideMode) {
        if (guideMode == CaptureMode.VIDEO && !hasMic) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ---- Subject detection state ----
    var detectorOk by remember { mutableStateOf(true) }
    val detector = remember {
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
    DisposableEffect(Unit) {
        onDispose { runCatching { detector?.close() } }
    }
    var detected by remember { mutableStateOf<ShotScale?>(null) }
    var target by remember { mutableStateOf(ShotScale.BUST) }
    var auto by remember { mutableStateOf(true) }
    // 180도 법칙 세팅: 몇 대·이 기기는 몇 번째 (세션 내 유지)
    var camCount by remember { mutableStateOf(1) }
    var thisCam by remember { mutableStateOf(1) }
    var show180 by remember { mutableStateOf(false) }
    var lastUri by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Recommendation first: follow the detected scale until the user overrides.
    LaunchedEffect(detected, auto) {
        if (auto && detectorOk) detected?.let { target = it }
    }

    // ---- CameraX: preview + photo + analysis ----
    // COMPATIBLE (TextureView) avoids a known SurfaceView black-screen bug on some
    // OEMs once a Recorder/VideoCapture surface is also bound (see EasyCameraScreen).
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { runCatching { analysisExecutor.shutdown() } } }

    LaunchedEffect(hasCamera, detector, detectorOk) {
        if (!hasCamera) return@LaunchedEffect
        try {
            val provider = withContext(Dispatchers.IO) {
                ProcessCameraProvider.getInstance(ctx).get()
            }
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            val recorder = Recorder.Builder()
                .setQualitySelector(vm.qualitySelector()).build()
            videoCapture = VideoCapture.withOutput(recorder)
            provider.unbindAll()
            if (detector != null && detectorOk) {
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also {
                        it.setAnalyzer(analysisExecutor,
                            ShotAnalyzer(detector) { metrics ->
                                detected = metrics?.let { shotScaleForFaceRatio(it.heightRatio) }
                            })
                    }
                provider.bindToLifecycle(lifecycle, selector, preview,
                    imageCapture!!, videoCapture!!, analysis)
            } else {
                provider.bindToLifecycle(lifecycle, selector, preview,
                    imageCapture!!, videoCapture!!)
                detectorOk = false
            }
            error = null
        } catch (e: Exception) {
            if (e is IllegalStateException && e.message?.contains("ML") == true) {
                detectorOk = false // recognizer missing → manual-only guide
            } else {
                error = "Camera unavailable: ${e.message}"
            }
        }
    }

    fun takePhoto() {
        val ic = imageCapture ?: run { error = "Camera is starting, try again."; return }
        scope.launch(Dispatchers.IO) {
            try {
                val mediaId = UUID.randomUUID().toString().take(8)
                val outFile = File(ctx.cacheDir, "DALUR_GUIDE_${System.currentTimeMillis()}_$mediaId.jpg")
                val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                val executor: Executor = ContextCompat.getMainExecutor(ctx)
                ic.takePicture(opts, executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val savedUri = saveGuideMedia(ctx, outFile, true)
                                val app = ctx.applicationContext as com.dalur.film.DalurApp
                                app.captures.insert(CaptureMetadata(
                                    mediaId = mediaId,
                                    timestampMillis = System.currentTimeMillis(),
                                    locationUnavailable = true,
                                    mediaType = "photo",
                                    mediaUri = savedUri,
                                    colorProfile = "SDR",
                                ))
                                withContext(Dispatchers.Main) { lastUri = savedUri }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { error = "Save failed: ${e.message}" }
                            }
                        }
                    }
                    override fun onError(e: ImageCaptureException) {
                        scope.launch(Dispatchers.Main) { error = "Capture failed: ${e.message}" }
                    }
                })
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { error = "Capture failed: ${e.message}" }
            }
        }
    }

    // Recording timer
    LaunchedEffect(isRecording) {
        while (isRecording) {
            kotlinx.coroutines.delay(500)
            recordSeconds = (System.currentTimeMillis() - recordStartMs) / 1000
        }
    }

    fun toggleVideo() {
        val vc = videoCapture
        if (isRecording) {
            activeRecording?.stop()
            activeRecording = null
            isRecording = false
            return
        }
        if (vc == null) {
            error = "Video is starting, try again."
            return
        }
        if (!hasMic) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            error = "Microphone permission is needed for video sound."
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val mediaId = UUID.randomUUID().toString().take(8)
                val ts = System.currentTimeMillis()
                val extDir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
                    ?: ctx.cacheDir
                val outFile = File(extDir, "DALUR_GUIDE_${ts}_$mediaId.mp4")
                val opts = FileOutputOptions.Builder(outFile).build()
                val pending = vc.output.prepareRecording(ctx, opts).withAudioEnabled()
                val rec = pending.start(ContextCompat.getMainExecutor(ctx)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            recordStartMs = System.currentTimeMillis()
                            recordSeconds = 0
                            isRecording = true
                        }
                        is VideoRecordEvent.Finalize -> {
                            scope.launch(Dispatchers.IO) {
                                if (event.hasError()) {
                                    withContext(Dispatchers.Main) {
                                        isRecording = false
                                        error = "Recording failed: ${event.error}"
                                    }
                                } else {
                                    val savedUri = saveGuideMedia(ctx, outFile, false)
                                    val rep = vm.capabilityReport.value
                                    val app = ctx.applicationContext as com.dalur.film.DalurApp
                                    app.captures.insert(CaptureMetadata(
                                        mediaId = mediaId,
                                        timestampMillis = ts,
                                        locationUnavailable = true,
                                        mediaType = "video",
                                        mediaUri = savedUri,
                                        codec = if (rep?.hevcSupported == true) "HEVC" else "H264",
                                        colorProfile = "SDR",
                                        filmApplied = false,
                                        filmError = "NOT_SUPPORTED: guide video has no film pipeline",
                                    ))
                                    withContext(Dispatchers.Main) {
                                        isRecording = false
                                        lastUri = savedUri
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }
                activeRecording = rec
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { error = "Recording failed: ${e.message}" }
            }
        }
    }

    // ---- Coaching decision ----
    // Distance rank: CLOSE_UP(0) … EXTREME_FULL(4). target farther than detected → step back.
    val coach: Coach = when {
        !detectorOk -> Coach(null, ctx.getString(R.string.coach_unsupported), null)
        detected == null -> Coach(null, ctx.getString(R.string.coach_show_face), Icons.Filled.PersonSearch)
        target.ordinal > detected!!.ordinal ->
            Coach(ShotScale.values()[target.ordinal], ctx.getString(R.string.coach_step_back), Icons.Filled.KeyboardDoubleArrowDown)
        target.ordinal < detected!!.ordinal ->
            Coach(ShotScale.values()[target.ordinal], ctx.getString(R.string.coach_come_closer), Icons.Filled.KeyboardDoubleArrowUp)
        else -> Coach(target, ctx.getString(R.string.coach_perfect), Icons.Filled.CheckCircle)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
        if (!hasCamera) {
            FailureState(title = "Camera permission needed",
                body = "Guide needs the camera to see the subject.",
                action = "Grant camera") { permLauncher.launch(Manifest.permission.CAMERA) }
            return@Box
        }
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        // Target silhouette + thirds
        SilhouetteGuide(target)
        GuideThirds()
        // 16:9 record frame in video mode
        if (guideMode == CaptureMode.VIDEO) GuideSixteenNine()

        // Top HUD
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(104.dp)
            .background(Brush.verticalGradient(
                listOf(Color(0xFF0B0B0D).copy(alpha = 0.72f), Color.Transparent))))
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.coach_title), style = MaterialTheme.typography.titleMedium,
                    color = scheme.onBackground)
                Spacer(Modifier.weight(1f))
                AssistChip(onClick = { auto = true },
                    label = { Text(if (!detectorOk) stringResource(R.string.coach_unsupported_short)
                        else stringResource(R.string.coach_detected, detected?.let { stringResource(it.labelRes) } ?: stringResource(R.string.coach_no_face))) })
                IconButton(onClick = { show180 = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Settings, stringResource(R.string.guide_180), tint = scheme.onBackground)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.guide_goal, stringResource(target.labelRes), stringResource(target.tipRes)),
                style = MaterialTheme.typography.labelSmall, color = scheme.primary)
            Text(shoot180Summary(camCount, thisCam, ctx),
                style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            if (isRecording) {
                Text("● REC %02d:%02d".format(recordSeconds / 60, recordSeconds % 60),
                    style = MaterialTheme.typography.labelSmall, color = scheme.error,
                    fontWeight = FontWeight.Bold)
            }
        }

        // Stereo audio meter: video mode only, centered under the title row.
        if (guideMode == CaptureMode.VIDEO && hasMic && meterOk) {
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .padding(top = 108.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                StereoBars(l = meterL, r = meterR)
                meterWarn?.let {
                    Text(it, Modifier.padding(top = 4.dp),
                        color = scheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                }
            }
        }

        // 180도 세팅 오버레이
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

        error?.let {
            Card(Modifier.align(Alignment.Center).padding(16.dp)) {
                Text(it, Modifier.padding(12.dp))
            }
        }

        // Bottom deck: coaching banner + target select + 앞으로/뒤로/셔터
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xFF0B0B0D).copy(alpha = 0.9f))))
            .padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Coaching banner
            Row(Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E1E24))
                .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                coach.icon?.let {
                    Icon(it, null, tint = scheme.primary, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(coach.message,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = scheme.onBackground)
            }
            Spacer(Modifier.height(8.dp))
            // Target chips: 추천 + 5 scales
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = auto, onClick = { auto = true }, label = { Text(stringResource(R.string.recommend)) })
                ShotScale.values().forEach { s ->
                    FilterChip(selected = !auto && target == s,
                        onClick = { auto = false; target = s }, label = { Text(stringResource(s.labelRes)) })
                }
            }
            Spacer(Modifier.height(6.dp))
            // 앞으로 · VIDEO · shutter · PHOTO · 뒤로 (single shutter line)
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                GuideStepButton(label = stringResource(R.string.guide_step_forward), iconUp = true, compact = true,
                    enabled = target.ordinal > 0) {
                    auto = false
                    target = ShotScale.values()[target.ordinal - 1]
                }
                Spacer(Modifier.width(6.dp))
                ModeSideButton(
                    selected = guideMode == CaptureMode.VIDEO,
                    onClick = { guideMode = CaptureMode.VIDEO },
                    icon = { Icon(Icons.Filled.Videocam, "video", modifier = Modifier.size(22.dp)) }
                )
                Spacer(Modifier.width(6.dp))
                val ring = if (guideMode == CaptureMode.VIDEO) scheme.error
                    else scheme.onBackground
                Box(Modifier.size(72.dp).clip(CircleShape)
                    .clickable {
                        if (guideMode == CaptureMode.PHOTO) takePhoto() else toggleVideo()
                    },
                    contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(72.dp)) {
                        drawCircle(ring, radius = size.minDimension / 2,
                            style = Stroke(width = 6f))
                        drawCircle(ring, radius = size.minDimension / 2 - 14f)
                    }
                    if (isRecording) {
                        Icon(Icons.Filled.Stop, "stop",
                            tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
                Spacer(Modifier.width(6.dp))
                ModeSideButton(
                    selected = guideMode == CaptureMode.PHOTO,
                    onClick = { guideMode = CaptureMode.PHOTO },
                    icon = { Icon(Icons.Filled.PhotoCamera, "photo", modifier = Modifier.size(22.dp)) }
                )
                Spacer(Modifier.width(6.dp))
                GuideStepButton(label = stringResource(R.string.guide_step_back), iconUp = false, compact = true,
                    enabled = target.ordinal < ShotScale.values().lastIndex) {
                    auto = false
                    target = ShotScale.values()[target.ordinal + 1]
                }
            }
            lastUri?.let {
                TextButton2(stringResource(R.string.guide_view_last)) { onOpenPlayback(android.net.Uri.encode(it)) }
            }
        }
        // Kino-style REC frame hugging the rounded screen (top-left REC stays as is).
        if (isRecording) {
            Box(Modifier.fillMaxSize()
                .padding(1.dp)
                .border(4.dp, Color(0xFFE5484D), RoundedCornerShape(28.dp)))
        }
    }
}

private data class Coach(
    val scale: ShotScale?,
    val message: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
)

@Composable
private fun GuideStepButton(label: String, iconUp: Boolean, enabled: Boolean,
    compact: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(if (compact) 56.dp else 72.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E24))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp)) {
        Icon(if (iconUp) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            label, tint = if (enabled) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(if (compact) 20.dp else 24.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (enabled) scheme.onBackground else scheme.onSurfaceVariant)
    }
}

@Composable
private fun TextButton2(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        androidx.compose.material3.TextButton(onClick = onClick) {
            Text(label)
        }
    }
}

/** Faint thirds grid over the guide frame. */
@Composable
fun GuideThirds() {
    Canvas(Modifier.fillMaxSize()) {
        val line = Color.White.copy(alpha = 0.22f)
        val w3 = size.width / 3f
        val h3 = size.height / 3f
        drawLine(line, Offset(w3, 0f), Offset(w3, size.height), strokeWidth = 1f)
        drawLine(line, Offset(2 * w3, 0f), Offset(2 * w3, size.height), strokeWidth = 1f)
        drawLine(line, Offset(0f, h3), Offset(size.width, h3), strokeWidth = 1f)
        drawLine(line, Offset(0f, 2 * h3), Offset(size.width, 2 * h3), strokeWidth = 1f)
    }
}

/** Centered 16:9 record frame for guide video mode. */
@Composable
private fun GuideSixteenNine() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frameH = maxWidth * 16f / 9f
        val side = maxWidth * 0.02f
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(frameH)
                .padding(horizontal = side)
                .border(1.dp, Color.White.copy(alpha = 0.55f))) {
                Text("16:9", Modifier.align(Alignment.TopEnd).padding(6.dp),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Stylized person silhouette per target scale, centered, + headroom line.
 *  When [shorts] is true, everything is drawn inside the centered vertical
 *  9:16 safe frame (same geometry as the 9:16 grid). [safeZone] instead fits the
 *  silhouette inside the Shorts/Reels/TikTok UI-safe rect (see [ShortsSafety]) so
 *  headroom/feet don't land under the top bar, caption band, or action rail. */
@Composable
fun SilhouetteGuide(scale: ShotScale, shorts: Boolean = false, safeZone: Boolean = false) {
    val measurer = rememberTextMeasurer()
    val headroomText = stringResource(R.string.headroom)
    Canvas(Modifier.fillMaxSize()) {
        val fill = Color.White.copy(alpha = 0.28f)
        val edge = Color.White.copy(alpha = 0.65f)
        val gold = Color(0xFFE8DCC8)
        val fullW = size.width
        val fullH = size.height
        // Shorts: same fitted 9:16 rect as the grid (fit inside tall screens).
        val w = if (shorts) minOf(fullW, fullH * 9f / 16f) else fullW
        val h = if (shorts) w * 16f / 9f else fullH
        val dx = (fullW - w) / 2f
        val dy = (fullH - h) / 2f
        // safeZone: same basis as ShortsSafety (full screen, not fitted-in) and the
        // same 4%/11%/82%/66% inset as its safe rect.
        val ox = if (safeZone) fullW * 0.04f else 0f
        val oy = if (safeZone) fullH * 0.11f else 0f
        val ow = if (safeZone) fullW * 0.82f else w
        val oh = if (safeZone) fullH * 0.66f else h
        withTransform({
            translate(left = if (safeZone) ox else dx, top = if (safeZone) oy else dy)
            clipRect(left = 0f, top = 0f, right = ow, bottom = oh)
        }) {
            val w = ow
            val h = oh
            drawSilhouette(scale, w, h, fill, edge)
            // Headroom: "머리는 여기까지" dashed gold line at head top.
            val headTop = headTopFor(scale, w, h)
            drawLine(gold, Offset(0f, headTop), Offset(w, headTop), strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
            drawText(measurer, headroomText,
                topLeft = Offset(16f, headTop + 10f),
                style = androidx.compose.ui.text.TextStyle(
                    color = gold, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/** Head-top Y for each scale (must match [drawSilhouette] numbers). */
private fun DrawScope.headTopFor(scale: ShotScale, w: Float, h: Float): Float = when (scale) {
    ShotScale.EXTREME_FULL -> h * 0.30f - w * 0.06f
    ShotScale.FULL -> h * 0.13f - w * 0.09f
    ShotScale.KNEE -> h * 0.17f - w * 0.13f
    ShotScale.BUST -> h * 0.32f - w * 0.19f
    ShotScale.CLOSE_UP -> h * 0.44f - w * 0.30f
}

private fun DrawScope.drawSilhouette(
    scale: ShotScale, w: Float, h: Float, fill: Color, edge: Color,
) {
    val cx = w / 2f
    when (scale) {
        ShotScale.EXTREME_FULL -> {
            val hr = w * 0.06f
            person(hr, cx, h * 0.30f, w * 0.11f, h * 0.34f, w * 0.07f, h * 0.55f,
                h * 0.75f, armsTo = h * 0.36f, fill, edge)
        }
        ShotScale.FULL -> {
            val hr = w * 0.09f
            person(hr, cx, h * 0.13f, w * 0.17f, h * 0.20f, w * 0.11f, h * 0.50f,
                h * 0.93f, armsTo = h * 0.52f, fill, edge)
        }
        ShotScale.KNEE -> {
            val hr = w * 0.13f
            person(hr, cx, h * 0.17f, w * 0.25f, h * 0.28f, w * 0.16f, h * 0.62f,
                h * 0.98f, armsTo = h * 0.60f, fill, edge)
        }
        ShotScale.BUST -> {
            val hr = w * 0.19f
            person(hr, cx, h * 0.32f, w * 0.36f, h * 0.52f, w * 0.28f, h * 1.02f,
                h * 1.02f, armsTo = null, fill, edge)
        }
        ShotScale.CLOSE_UP -> {
            val hr = w * 0.30f
            person(hr, cx, h * 0.44f, w * 0.50f, h * 0.80f, w * 0.44f, h * 1.05f,
                h * 1.05f, armsTo = null, fill, edge)
        }
    }
}

/** Head circle + trapezoid torso + optional legs/arms. All Y absolute px. */
private fun DrawScope.person(
    headR: Float, cx: Float, headCy: Float,
    shoulderHalf: Float, shoulderY: Float,
    hipHalf: Float, hipY: Float, feetY: Float,
    armsTo: Float?,
    fill: Color, edge: Color,
) {
    // Head
    drawCircle(fill, radius = headR, center = Offset(cx, headCy))
    drawCircle(edge, radius = headR, center = Offset(cx, headCy), style = Stroke(width = 3f))
    // Torso trapezoid (shoulders → hips)
    val torso = Path().apply {
        moveTo(cx - shoulderHalf, shoulderY)
        lineTo(cx + shoulderHalf, shoulderY)
        lineTo(cx + hipHalf, hipY)
        lineTo(cx - hipHalf, hipY)
        close()
    }
    drawPath(torso, fill)
    drawPath(torso, edge, style = Stroke(width = 3f))
    // Legs (only when feet extend past hips, i.e. knee/full)
    if (feetY > hipY + 4f) {
        val legW = hipHalf * 0.42f
        listOf(cx - hipHalf * 0.5f, cx + hipHalf * 0.5f).forEach { lx ->
            drawRoundRect(fill, topLeft = Offset(lx - legW, hipY),
                size = androidx.compose.ui.geometry.Size(legW * 2f, feetY - hipY),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(legW))
        }
    }
    // Arms
    armsTo?.let { ay ->
        drawLine(edge, Offset(cx - shoulderHalf, shoulderY + 6f),
            Offset(cx - shoulderHalf - headR * 0.5f, ay), strokeWidth = 8f)
        drawLine(edge, Offset(cx + shoulderHalf, shoulderY + 6f),
            Offset(cx + shoulderHalf + headR * 0.5f, ay), strokeWidth = 8f)
    }
}

private fun saveGuideMedia(ctx: Context, file: File, isPhoto: Boolean): String {
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
        android.net.Uri.fromFile(file).toString()
    }
}
