package com.dalur.film.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dalur.film.film.FilmRepository
import com.dalur.film.gps.LocationTracker
import com.dalur.film.media.CaptureMetadataStore
import com.dalur.film.pro.CapabilityManager
import com.dalur.film.pro.LutMonitorState
import com.dalur.film.pro.MonitorMode
import com.dalur.film.recording.RecordTarget
import com.dalur.film.settings.SettingsStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class EasyUiState(
    val mode: CaptureMode = CaptureMode.PHOTO,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val zoomRatio: Float = 1f,
    val flashOn: Boolean = false,
    val filmId: String? = null,
    val filmIntensity: Float = 0.85f,
    val isPro: Boolean = false,
    val isRecording: Boolean = false,
    val recordSeconds: Long = 0,
    val lastCaptureUri: String? = null,
    val error: String? = null
)

enum class CaptureMode { PHOTO, VIDEO }

data class ProUiState(
    val iso: Int? = null,
    val shutterSec: Double? = null,
    val exposureComp: Float = 0f,
    val wbKelvin: Int? = null,
    val tint: Float = 0f,
    val focusDistance: Float? = null,
    val fps: Int = 30,
    val resolution: String = "1080p",
    val codecLabel: String = "H.264",
    val monitor: LutMonitorState = LutMonitorState(),
    val histogramOn: Boolean = true,
    val zebraOn: Boolean = false,
    val peakingOn: Boolean = false,
    val guidesOn: Boolean = true,
    val audioLevel: Float = 0f,
    val storageFreeText: String = "",
    val recordTarget: RecordTarget = RecordTarget.Internal
)

class CameraViewModel(
    private val appContext: Context,
    private val films: FilmRepository,
    private val captures: CaptureMetadataStore,
    private val capabilities: CapabilityManager,
    private val settings: SettingsStore
) : ViewModel() {
    val locationTracker = LocationTracker(appContext)

    private val _easy = MutableStateFlow(EasyUiState())
    val easy: StateFlow<EasyUiState> = _easy.asStateFlow()

    private val _pro = MutableStateFlow(ProUiState())
    val pro: StateFlow<ProUiState> = _pro.asStateFlow()

    val filmRecipes = films.recipes
    val capabilityReport = capabilities.report
    val allCaptures = captures.captures

    init {
        viewModelScope.launch {
            settings.filmIntensity.collect { k ->
                _easy.update { it.copy(filmIntensity = k) }
                _pro.update { it.copy(monitor = it.monitor.copy(intensity = k)) }
            }
        }
    }

    fun setMode(m: CaptureMode) { _easy.update { it.copy(mode = m, error = null) } }
    fun togglePro() { _easy.update { it.copy(isPro = !it.isPro) } }
    fun switchLens() {
        _easy.update {
            it.copy(
                lensFacing = if (it.lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            )
        }
    }
    fun setZoom(r: Float) { _easy.update { it.copy(zoomRatio = r.coerceIn(1f, 8f)) } }
    fun toggleFlash() { _easy.update { it.copy(flashOn = !it.flashOn) } }
    fun selectFilm(id: String?) {
        _easy.update { it.copy(filmId = id) }
        _pro.update { it.copy(monitor = it.monitor.copy(recipeId = id)) }
    }
    fun setRecording(rec: Boolean, seconds: Long = 0) {
        _easy.update { it.copy(isRecording = rec, recordSeconds = seconds) }
    }
    fun setLastCapture(uri: String?) { _easy.update { it.copy(lastCaptureUri = uri) } }
    fun setError(msg: String?) { _easy.update { it.copy(error = msg) } }

    // ---- PRO setters ----
    fun setMonitorMode(m: MonitorMode) { _pro.update { it.copy(monitor = it.monitor.copy(mode = m)) } }
    fun setIso(v: Int?) { _pro.update { it.copy(iso = v) } }
    fun setShutter(v: Double?) { _pro.update { it.copy(shutterSec = v) } }
    fun setExposure(v: Float) { _pro.update { it.copy(exposureComp = v) } }
    fun setWb(v: Int?) { _pro.update { it.copy(wbKelvin = v) } }
    fun setFps(v: Int) { _pro.update { it.copy(fps = v) } }
    fun setResolution(v: String) { _pro.update { it.copy(resolution = v) } }
    fun setCodecLabel(v: String) { _pro.update { it.copy(codecLabel = v) } }
    fun toggleHistogram() { _pro.update { it.copy(histogramOn = !it.histogramOn) } }
    fun toggleZebra() { _pro.update { it.copy(zebraOn = !it.zebraOn) } }
    fun togglePeaking() { _pro.update { it.copy(peakingOn = !it.peakingOn) } }
    fun toggleGuides() { _pro.update { it.copy(guidesOn = !it.guidesOn) } }
    fun setRecordTarget(t: RecordTarget) { _pro.update { it.copy(recordTarget = t) } }

    fun qualitySelector(): QualitySelector = when (_pro.value.resolution) {
        "4K" -> QualitySelector.from(Quality.UHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.UHD))
        else -> QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD))
    }
}
