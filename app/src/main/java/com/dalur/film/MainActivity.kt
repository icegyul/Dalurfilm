package com.dalur.film

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dalur.film.camera.CameraViewModel
import com.dalur.film.navigation.DalurNav
import com.dalur.film.ui.theme.DalurTheme

class MainActivity : ComponentActivity() {
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
        setContent {
            DalurTheme {
                DalurNav(factory)
            }
        }
    }
}
