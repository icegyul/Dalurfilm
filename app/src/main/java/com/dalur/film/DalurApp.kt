package com.dalur.film

import android.app.Application
import com.dalur.film.film.FilmRepository
import com.dalur.film.media.CaptureMetadataStore
import com.dalur.film.pro.CapabilityManager
import com.dalur.film.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DalurApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var films: FilmRepository private set
    lateinit var captures: CaptureMetadataStore private set
    lateinit var capabilities: CapabilityManager private set
    lateinit var settings: SettingsStore private set

    override fun onCreate() {
        super.onCreate()
        films = FilmRepository(this)
        captures = CaptureMetadataStore(this)
        capabilities = CapabilityManager(this)
        settings = SettingsStore(this)
        appScope.launch {
            try { films.ensureSeeded() } catch (_: Exception) {}
            try { capabilities.refresh() } catch (_: Exception) {}
        }
    }
}
