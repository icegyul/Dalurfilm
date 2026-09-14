package com.dalur.film.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.dalur.film.film.FilmEngine
import com.dalur.film.film.filmCategoryTabs
import com.dalur.film.film.filmsForCategory
import com.dalur.film.shared.CaptureMetadata
import com.dalur.film.shared.GpsPoint
import com.dalur.film.shared.dalurFileName
import com.dalur.film.ui.components.FailureState
import com.dalur.film.ui.components.FilmCarouselCard
import com.dalur.film.ui.components.FilmCategoryRow
import com.dalur.film.ui.components.filmTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasyCameraScreen(
    vm: CameraViewModel,
    onOpenSettings: () -> Unit,
    onOpenCapability: () -> Unit,
    onOpenPlayback: (String) -> Unit
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val easy by vm.easy.collectAsState()
    val pro by vm.pro.collectAsState()
    val recipes by vm.filmRecipes.collectAsState()
    val caps by vm.capabilityReport.collectAsState()
    val scheme = MaterialTheme.colorScheme

    var hasCamera by remember { mutableStateOf(false) }
    var hasMic by remember { mutableStateOf(false) }
    var hasLocation by remember { mutableStateOf(false) }
    var cameraDenied by remember { mutableStateOf(false) }

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
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    // Bind camera
    LaunchedEffect(hasCamera, easy.lensFacing, easy.mode, easy.flashOn) {
        if (!hasCamera) return@LaunchedEffect
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
            val recorder = Recorder.Builder()
                .setQualitySelector(vm.qualitySelector()).build()
            videoCapture = VideoCapture.withOutput(recorder)
            provider.unbindAll()
            if (easy.mode == CaptureMode.PHOTO) {
                provider.bindToLifecycle(lifecycle, selector, preview, imageCapture!!)
            } else {
                provider.bindToLifecycle(lifecycle, selector, preview, videoCapture!!)
            }
            vm.setError(null)
        } catch (e: Exception) {
            vm.setError("Camera unavailable: ${e.message}")
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
        val ic = imageCapture ?: run { vm.setError("Camera is starting, try again."); return }
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
                                // Resolve app-scoped singletons via context.
                                val app = ctx.applicationContext as com.dalur.film.DalurApp
                                app.captures.insert(meta)
                                if (filmError != null) {
                                    vm.setError("Film not applied: $filmError — photo saved without film.")
                                }
                                withContext(Dispatchers.Main) { vm.setLastCapture(savedUri) }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { vm.setError("Save failed: ${e.message}") }
                            }
                        }
                    }
                    override fun onError(e: ImageCaptureException) {
                        scope.launch(Dispatchers.Main) { vm.setError("Capture failed: ${e.message}") }
                    }
                })
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { vm.setError("Capture failed: ${e.message}") }
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
        if (vc == null) { vm.setError("Video is starting, try again."); return }
        if (!hasMic) {
            permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            vm.setError("Microphone permission is needed for video sound.")
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
                                        vm.setError("Recording failed: ${event.error}")
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
                                    val app = ctx.applicationContext as com.dalur.film.DalurApp
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
                withContext(Dispatchers.Main) { vm.setError("Recording failed: ${e.message}") }
            }
        }
    }

    // ---- Cinematic layout: edge-to-edge viewfinder, minimal overlay controls ----
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        // Viewfinder layer (full-screen).
        if (hasCamera) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            // Live film tint overlay (preview approximation; photo output uses the real GPU filter).
            val recipe = recipes.firstOrNull { it.id == easy.filmId }
            if (recipe != null && easy.filmIntensity > 0.01f) {
                FilmPreviewOverlay(recipeId = recipe.id, alpha = 0.10f * easy.filmIntensity)
            }
            // Frame guides (pro)
            if (easy.isPro) {
                FrameGuides()
            }
        } else {
            FailureState(
                title = "Camera permission needed",
                body = "Allow camera access to shoot. Location and microphone stay optional until you need them.",
                action = "Grant camera"
            ) { permLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }
        }

        // Top HUD panel: opaque dark so the chrome stays readable with no frame bleed-through.
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Color(0xFF0B0B0D))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Top bar: PRO chip + settings + capability
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("DALUR film", style = MaterialTheme.typography.titleMedium,
                    color = scheme.onBackground)
                if (easy.isPro) {
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = onOpenSettings, label = { Text("PRO") })
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenCapability) {
                    Icon(Icons.Filled.Info, "capability", tint = scheme.onBackground)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, "settings", tint = scheme.onBackground)
                }
            }
            // Stage HUD — stepwise exposure: only the stage-relevant readout appears.
            Row(Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (easy.isRecording) {
                    StageHud(text = "● REC %02d:%02d".format(
                        easy.recordSeconds / 60, easy.recordSeconds % 60), accent = scheme.error)
                }
                if (easy.isPro) {
                    StageHud(text = "FPS ${pro.fps}")
                    StageHud(text = "ISO ${pro.iso ?: "AUTO"}")
                    StageHud(text = shutterLabel(pro.shutterSec))
                }
            }
        }

        easy.error?.let {
            Card(Modifier.align(Alignment.Center).padding(16.dp)) {
                Text(it, Modifier.padding(12.dp))
            }
        }

        // Bottom control deck: opaque dark panel, no translucent scrim over the frame.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color(0xFF0B0B0D))
        ) {
            if (easy.isPro) {
                HorizontalDivider(color = scheme.outline)
                ProCameraPanel(vm)
            }
            HorizontalDivider(color = scheme.outline)
            // Mode toggle
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = easy.mode == CaptureMode.PHOTO,
                        onClick = { vm.setMode(CaptureMode.PHOTO) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        label = { Text("Photo") }
                    )
                    SegmentedButton(
                        selected = easy.mode == CaptureMode.VIDEO,
                        onClick = { vm.setMode(CaptureMode.VIDEO) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        label = { Text("Video") }
                    )
                }
            }
            // Film selection: category tabs + horizontal carousel.
            val categories = remember(recipes) { filmCategoryTabs(recipes) }
            var activeCategory by remember { mutableStateOf("All") }
            val visibleFilms = remember(recipes, activeCategory) {
                filmsForCategory(recipes, activeCategory)
            }
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                FilmCategoryRow(
                    categories = categories,
                    selected = activeCategory,
                    onSelect = { activeCategory = it }
                )
                LazyRow(
                    Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        FilmCarouselCard(
                            name = "None",
                            subtitle = "no film",
                            tint = MaterialTheme.colorScheme.surfaceVariant,
                            selected = easy.filmId == null
                        ) { vm.selectFilm(null) }
                    }
                    items(visibleFilms, key = { it.id }) { r ->
                        FilmCarouselCard(
                            name = r.name,
                            subtitle = "${(r.intensity * 100).toInt()}% · ${r.category}",
                            tint = filmTint(r.id),
                            selected = easy.filmId == r.id
                        ) { vm.selectFilm(r.id) }
                    }
                }
            }
            // Shutter row
            val haptics = LocalHapticFeedback.current
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                // Last-shot thumbnail
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceVariant)
                    .border(1.dp, scheme.outline, RoundedCornerShape(14.dp))
                    .clickable(enabled = easy.lastCaptureUri != null) {
                        easy.lastCaptureUri?.let { onOpenPlayback(android.net.Uri.encode(it)) }
                    }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Photo, "last", tint = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                // Shutter / record
                val shutterInner = when {
                    easy.isRecording -> scheme.error
                    easy.mode == CaptureMode.VIDEO -> scheme.error
                    else -> scheme.onBackground
                }
                Box(
                    Modifier.size(80.dp)
                        .border(3.dp, scheme.onBackground, CircleShape)
                        .padding(7.dp)
                        .clip(CircleShape)
                        .background(shutterInner)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (easy.mode == CaptureMode.PHOTO) takePhoto() else toggleVideo()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (easy.mode == CaptureMode.VIDEO && !easy.isRecording) {
                        Box(Modifier.size(26.dp).clip(CircleShape)
                            .background(scheme.background))
                    }
                    if (easy.isRecording) {
                        Icon(Icons.Filled.Stop, "stop",
                            tint = scheme.onError, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.switchLens() }) {
                    Icon(Icons.Filled.Cameraswitch, "switch", tint = scheme.onBackground)
                }
            }
        }
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

private fun shutterLabel(sec: Double?): String {
    if (sec == null) return "SHUTTER AUTO"
    return if (sec >= 1.0) "${sec.roundToInt()}s" else "1/${(1.0 / sec).roundToInt()}"
}

@Composable
private fun FilmPreviewOverlay(recipeId: String, alpha: Float) {
    Box(Modifier.fillMaxSize().background(filmTint(recipeId).copy(alpha = alpha)))
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
