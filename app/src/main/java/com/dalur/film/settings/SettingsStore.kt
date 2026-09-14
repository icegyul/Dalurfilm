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
    }

    val filmIntensity: Flow<Float> = context.ds.data.map { it[K.FILM_INTENSITY] ?: 0.85f }
    val playbackLutFirst: Flow<Boolean> = context.ds.data.map { it[K.PLAYBACK_LUT] ?: true }
    val mapStyle: Flow<String> = context.ds.data.map {
        it[K.MAP_STYLE] ?: "https://demotiles.maplibre.org/style.json"
    }
    val reducedMotion: Flow<Boolean> = context.ds.data.map { it[K.REDUCED_MOTION] ?: false }

    suspend fun setFilmIntensity(v: Float) {
        context.ds.edit { it[K.FILM_INTENSITY] = v.coerceIn(0f, 1f) }
    }
    suspend fun setMapStyle(url: String) {
        context.ds.edit { it[K.MAP_STYLE] = url }
    }
    suspend fun setReducedMotion(v: Boolean) {
        context.ds.edit { it[K.REDUCED_MOTION] = v }
    }
}
