package com.dalur.film.weather

/**
 * Mirrors earthus's public `/v1/weather` response shape exactly (see
 * EARTHUS v2_APP/prototype/developers.html) so swapping [MockWeatherRepository]
 * for the real HTTP client later is a one-file change — nothing that reads
 * these models needs to know which one is behind [WeatherRepository].
 */
data class WeatherResponse(
    val query: WeatherQuery,
    val observed: ObservedWeather?,
    val forecast: ForecastWeather?,
    val sourceNote: String,
)

data class WeatherQuery(val lat: Double, val lon: Double)

data class ObservedWeather(
    val stationId: String,
    val stationName: String,
    val distanceKm: Double,
    val observedAt: String, // "yyyyMMddHHmm" KST, per the API doc
    val tempC: Double,
    val humidityPct: Int,
    val windMs: Double,
    val windDirDeg: Int,
    val rainMm: Double,
)

data class ForecastHour(val tm: String, val t: Int, val pop: Int, val sky: Int, val pty: Int)

data class ForecastWeather(
    val stationId: String,
    val stationName: String,
    val distanceKm: Double,
    val baseKst: String,
    val hourly: List<ForecastHour>,
    val daily: Map<String, DailyRange>,
)

data class DailyRange(val tmin: Int, val tmax: Int)

/** earthus API error shape (400/401/403/429/503) — surfaced as a sealed result
 *  so the UI can show the right message instead of a generic failure. */
sealed class WeatherResult {
    data class Success(val data: WeatherResponse) : WeatherResult()
    data class ApiError(val code: Int, val message: String) : WeatherResult()
    data class NetworkError(val message: String) : WeatherResult()
}
