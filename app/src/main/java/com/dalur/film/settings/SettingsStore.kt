package com.dalur.film.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore("dalur_settings")

class SettingsStore(private val context: Context) {
    private object K {
        val FILM_INTENSITY = floatPreferencesKey("film_intensity")
        val PLAYBACK_LUT = booleanPreferencesKey("playback_lut")
        val EXPORT_CODEC = stringPreferencesKey("export_codec")
        val MAP_STYLE = stringPreferencesKey("map_style")
        val HAPTICS = booleanPreferencesKey("haptics")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val OWNED_RECIPES = stringSetPreferencesKey("owned_recipes")
        val ZOOM_ENABLED = booleanPreferencesKey("zoom_enabled")
        val GUIDE_DEFAULT_ON = booleanPreferencesKey("guide_default_on")
        val GRID_DEFAULT_MODE = intPreferencesKey("grid_default_mode")
        val MIRROR_FRONT_CAMERA = booleanPreferencesKey("mirror_front_camera")
        val SHOW_BATTERY = booleanPreferencesKey("show_battery")
        val SHOW_STORAGE = booleanPreferencesKey("show_storage")
        val VOLUME_SHUTTER = booleanPreferencesKey("volume_shutter")
    }

    val filmIntensity: Flow<Float> = context.ds.data.map { it[K.FILM_INTENSITY] ?: 0.85f }
    val playbackLutFirst: Flow<Boolean> = context.ds.data.map { it[K.PLAYBACK_LUT] ?: true }
    val mapStyle: Flow<String> = context.ds.data.map {
        it[K.MAP_STYLE] ?: "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
    }
    val reducedMotion: Flow<Boolean> = context.ds.data.map { it[K.REDUCED_MOTION] ?: false }
    /** Locally owned (test-purchased) recipe ids. Real entitlements move server-side. */
    val ownedRecipes: Flow<Set<String>> = context.ds.data.map { it[K.OWNED_RECIPES] ?: emptySet() }
    /** Zoom control on/off. Off by default; enabled in Settings → Capture. */
    val zoomEnabled: Flow<Boolean> = context.ds.data.map { it[K.ZOOM_ENABLED] ?: false }
    /** Whether the person guide overlay starts on when Camera opens. Off by default —
     *  first launch shows the plain viewfinder, not the guide. */
    val guideDefaultOn: Flow<Boolean> = context.ds.data.map { it[K.GUIDE_DEFAULT_ON] ?: false }
    /** Default grid mode: 0 off · 1 thirds · 2 16:9 · 3 9:16 · 4 shorts-UI · 5 4:3. */
    val gridDefaultMode: Flow<Int> = context.ds.data.map { it[K.GRID_DEFAULT_MODE] ?: 1 }
    /** Mirror the front-camera PREVIEW only (selfie-mirror feel); the saved
     *  file is never flipped by this — that's a separate, unimplemented
     *  concern (most camera apps save un-mirrored regardless). */
    val mirrorFrontCamera: Flow<Boolean> = context.ds.data.map { it[K.MIRROR_FRONT_CAMERA] ?: true }
    val showBattery: Flow<Boolean> = context.ds.data.map { it[K.SHOW_BATTERY] ?: true }
    val showStorage: Flow<Boolean> = context.ds.data.map { it[K.SHOW_STORAGE] ?: true }
    /** Volume up/down triggers the shutter, like most native camera apps. */
    val volumeShutter: Flow<Boolean> = context.ds.data.map { it[K.VOLUME_SHUTTER] ?: true }

    suspend fun setFilmIntensity(v: Float) {
        context.ds.edit { it[K.FILM_INTENSITY] = v.coerceIn(0f, 1f) }
    }
    suspend fun setMapStyle(url: String) {
        context.ds.edit { it[K.MAP_STYLE] = url }
    }
    suspend fun setReducedMotion(v: Boolean) {
        context.ds.edit { it[K.REDUCED_MOTION] = v }
    }
    suspend fun setZoomEnabled(v: Boolean) {
        context.ds.edit { it[K.ZOOM_ENABLED] = v }
    }
    suspend fun setGuideDefaultOn(v: Boolean) {
        context.ds.edit { it[K.GUIDE_DEFAULT_ON] = v }
    }
    suspend fun setGridDefaultMode(v: Int) {
        context.ds.edit { it[K.GRID_DEFAULT_MODE] = v.coerceIn(0, 5) }
    }
    suspend fun setMirrorFrontCamera(v: Boolean) {
        context.ds.edit { it[K.MIRROR_FRONT_CAMERA] = v }
    }
    suspend fun setShowBattery(v: Boolean) {
        context.ds.edit { it[K.SHOW_BATTERY] = v }
    }
    suspend fun setShowStorage(v: Boolean) {
        context.ds.edit { it[K.SHOW_STORAGE] = v }
    }
    suspend fun setVolumeShutter(v: Boolean) {
        context.ds.edit { it[K.VOLUME_SHUTTER] = v }
    }

    suspend fun addOwnedRecipe(id: String) {
        context.ds.edit { it[K.OWNED_RECIPES] = (it[K.OWNED_RECIPES] ?: emptySet()) + id }
    }
}
