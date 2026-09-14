package com.dalur.film.pro

/** LOG VIEW <-> LUT VIEW monitor state. Never baked into the PRO master. */
enum class MonitorMode { LOG, LUT }

data class LutMonitorState(
    val mode: MonitorMode = MonitorMode.LUT,
    val recipeId: String? = null,
    val recipeVersion: Int? = null,
    val lutHash: String? = null,
    val intensity: Float = 0.85f
)
