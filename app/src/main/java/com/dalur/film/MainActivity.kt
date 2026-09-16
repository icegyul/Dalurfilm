package com.dalur.film

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.dalur.film.camera.CameraViewModel
import com.dalur.film.navigation.DalurNav
import com.dalur.film.ui.theme.DalurTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var cameraViewModel: CameraViewModel
    // Read synchronously from onKeyDown (can't suspend there); kept in sync
    // with the DataStore setting by the collector below. Defaults to the
    // same true the setting itself defaults to, so a press before the first
    // collection still behaves like most native camera apps expect.
    @Volatile private var volumeShutterEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DalurApp
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CameraViewModel(
                    applicationContext, app.films, app.captures, app.capabilities, app.settings
                ) as T
            }
        }
        // Same ViewModelStoreOwner (this Activity) + same factory as DalurNav's
        // viewModel(factory) call below, so both resolve to one shared instance —
        // the volume-key handler and the on-screen shutter drive the same relay.
        cameraViewModel = ViewModelProvider(this, factory)[CameraViewModel::class.java]
        lifecycleScope.launch {
            app.settings.volumeShutter.collect { volumeShutterEnabled = it }
        }
        setContent {
            DalurTheme {
                DalurNav(factory)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumeShutterEnabled &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            cameraViewModel.requestCapture()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
