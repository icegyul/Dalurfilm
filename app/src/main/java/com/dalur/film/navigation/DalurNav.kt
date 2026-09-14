package com.dalur.film.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dalur.film.camera.CameraViewModel
import com.dalur.film.camera.EasyCameraScreen
import com.dalur.film.diagnostics.CapabilityScreen
import com.dalur.film.film.FilmsScreen
import com.dalur.film.journey.JourneyPlayerScreen
import com.dalur.film.journey.JourneysScreen
import com.dalur.film.map.MapScreen
import com.dalur.film.playback.PlaybackScreen
import com.dalur.film.settings.SettingsScreen

sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Camera : Tab("camera", "Camera", Icons.Filled.PhotoCamera)
    data object Films : Tab("films", "Films", Icons.Filled.Palette)
    data object Map : Tab("map", "Map", Icons.Filled.Map)
    data object Journeys : Tab("journeys", "Journeys", Icons.Filled.Movie)
}

@Composable
fun DalurNav(factory: ViewModelProvider.Factory) {
    val nav = rememberNavController()
    val vm: CameraViewModel = viewModel(factory = factory)
    val tabs = listOf(Tab.Camera, Tab.Films, Tab.Map, Tab.Journeys)
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                val backStack by nav.currentBackStackEntryAsState()
                val current = backStack?.destination?.route
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = { nav.navigate(t.route) { launchSingleTop = true } },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = Tab.Camera.route, Modifier.padding(pad)) {
            composable(Tab.Camera.route) {
                EasyCameraScreen(vm,
                    onOpenSettings = { nav.navigate("settings") },
                    onOpenCapability = { nav.navigate("capability") },
                    onOpenPlayback = { uri -> nav.navigate("playback?uri=$uri") })
            }
            composable(Tab.Films.route) { FilmsScreen(vm) }
            composable(Tab.Map.route) { MapScreen(vm) }
            composable(Tab.Journeys.route) {
                JourneysScreen(vm, onPlay = { id -> nav.navigate("journey/$id") })
            }
            composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
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
