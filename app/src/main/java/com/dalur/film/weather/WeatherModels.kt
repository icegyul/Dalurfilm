package com.dalur.film.weather

import kotlinx.serialization.Serializable

/**
 * Mirrors earthus's ACTUAL `/v1/weather` response (verified against a live
 * call, 2026-09-16 — see below), which differs from the developers.html
 * doc's example in a few places the doc doesn't mention:
 *   - numeric fields the doc shows as ints (humidityPct, windDirDeg, the
 *     hourly `t`/`pop`, daily tmin/tmax) come back as JSON floats in
 *     practice, so they're Double here — an Int field would throw on every
 *     real response.
 *   - rainMm can be null (no precipitation), and a `daily` entry for
 *     "today" can carry only `tmax` with `tmin` absent — both nullable here.
 *   - hourly objects also carry wd/ws/pcp/rh, not in the doc's example;
 *     [Json] is configured with ignoreUnknownKeys so those just pass through
 *     unread rather than crashing the parse.
 * Swapping [MockWeatherRepository] for [EarthusWeatherRepository] is a
 * one-line change in the call site — nothing that reads these models needs
 * to know which one is behind [WeatherRepository].
 */
@Serializable
data class WeatherResponse(
    val query: WeatherQuery,
    val observed: ObservedWeather? = null,
    val forecast: ForecastWeather? = null,
    val sourceNote: String = "",
)

@Serializable
data class WeatherQuery(val lat: Double, val lon: Double)

@Serializable
data class ObservedWeather(
    val stationId: String,
    val stationName: String,
    val distanceKm: Double,
    val observedAt: String, // 실측: "yyyyMMdd HH:mm" KST (문서 예시와 포맷이 다름)
    val tempC: Double,
    val humidityPct: Double,
    val windMs: Double,
    val windDirDeg: Double,
    val rainMm: Double? = null,
)

@Serializable
data class ForecastHour(
    val tm: String,
    val t: Double,
    val pop: Double = 0.0,
    val sky: Int = 0,
    val pty: Int = 0,
)

@Serializable
data class ForecastWeather(
    val stationId: String,
    val stationName: String,
    val distanceKm: Double,
    val baseKst: String,
    val hourly: List<ForecastHour> = emptyList(),
    val daily: Map<String, DailyRange> = emptyMap(),
)

@Serializable
data class DailyRange(val tmin: Double? = null, val tmax: Double? = null)

/** earthus API error shape (400/401/403/429/503) — surfaced as a sealed result
 *  so the UI can show the right message instead of a generic failure. */
sealed class WeatherResult {
    data class Success(val data: WeatherResponse) : WeatherResult()
    data class ApiError(val code: Int, val message: String) : WeatherResult()
    data class NetworkError(val message: String) : WeatherResult()
}
