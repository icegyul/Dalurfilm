package com.dalur.film.weather

import com.dalur.film.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Thin seam between the UI and earthus's `/v1/weather` endpoint. Only
 * [MockWeatherRepository] exists today — earthus's Lambda Function URL and
 * an issued x-api-key aren't wired in yet (see EARTHUS v2_APP/prototype/
 * developers.html). When those arrive, add EarthusWeatherRepository here
 * implementing the same interface; nothing in MapScreen needs to change
 * beyond which implementation gets constructed.
 */
interface WeatherRepository {
    suspend fun fetchWeather(lat: Double, lon: Double): WeatherResult
}

/**
 * Deterministic-ish fake data so the UI can be built and demoed before a
 * real key exists. Never claims to be live — [WeatherResponse.sourceNote]
 * says so explicitly, and the UI must show that note as-is (same honesty
 * rule as the rest of this app's capability-gated Pro panel).
 */
class MockWeatherRepository : WeatherRepository {
    override suspend fun fetchWeather(lat: Double, lon: Double): WeatherResult {
        delay(900) // 실제 네트워크 왕복처럼 느껴지도록 — 로딩 UI도 같이 검증된다.
        // 위/경도로 그럴듯한 값을 살짝 흔들어서, 위치가 바뀌면 카드도 바뀌는 것처럼 보이게.
        val seed = ((lat * 131 + lon * 37).toInt() and 0xFF)
        val temp = 18.0 + (seed % 12)
        val humidity = 40 + (seed % 40)
        val wind = 1.0 + (seed % 5)
        return WeatherResult.Success(
            WeatherResponse(
                query = WeatherQuery(lat, lon),
                observed = ObservedWeather(
                    stationId = "MOCK-${(seed % 100)}",
                    stationName = "가데이터 관측소",
                    distanceKm = 1.0 + (seed % 5) * 0.4,
                    observedAt = "202609161200",
                    tempC = temp,
                    humidityPct = humidity.toDouble(),
                    windMs = wind,
                    windDirDeg = ((seed * 3) % 360).toDouble(),
                    rainMm = if (seed % 7 == 0) 1.5 else 0.0,
                ),
                forecast = ForecastWeather(
                    stationId = "MOCK-${(seed % 100)}",
                    stationName = "가데이터 관측소",
                    distanceKm = 1.0 + (seed % 5) * 0.4,
                    baseKst = "202609161100",
                    hourly = listOf(
                        ForecastHour("202609161300", temp + 1, 20.0, 1, 0),
                        ForecastHour("202609161400", temp + 2, 30.0, 3, 0),
                        ForecastHour("202609161500", temp + 1, 40.0, 3, 1),
                    ),
                    daily = mapOf("20260917" to DailyRange(temp - 4, temp + 5)),
                ),
                sourceNote = "가짜 데이터(mock) — earthus API 키 발급 전까지의 미리보기입니다. " +
                    "실제 연동 시 기상청(공공누리 제1유형) 출처로 교체됩니다.",
            )
        )
    }
}

/**
 * Real earthus `/v1/weather` client. Base URL and key come from
 * [BuildConfig] (populated at build time from the gitignored
 * secrets.properties — see app/build.gradle.kts) so no key ever lands in
 * committed source. If secrets.properties is missing, both fields build as
 * empty strings and every call fails fast with [WeatherResult.NetworkError]
 * instead of silently hitting a broken URL.
 */
class EarthusWeatherRepository(
    private val baseUrl: String = BuildConfig.EARTHUS_BASE_URL,
    private val apiKey: String = BuildConfig.EARTHUS_API_KEY,
) : WeatherRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchWeather(lat: Double, lon: Double): WeatherResult {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            return WeatherResult.NetworkError(
                "earthus 설정 없음 — secrets.properties에 EARTHUS_BASE_URL/EARTHUS_API_KEY를 채워주세요.")
        }
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("$baseUrl/v1/weather?lat=${
                    String.format(Locale.US, "%.6f", lat)
                }&lon=${String.format(Locale.US, "%.6f", lon)}")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("x-api-key", apiKey)
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                when (code) {
                    200 -> WeatherResult.Success(json.decodeFromString(WeatherResponse.serializer(), body))
                    400 -> WeatherResult.ApiError(400, "잘못된 좌표")
                    401 -> WeatherResult.ApiError(401, "API 키 없음")
                    403 -> WeatherResult.ApiError(403, "유효하지 않은 API 키")
                    429 -> WeatherResult.ApiError(429, "일일 호출 한도 초과")
                    503 -> WeatherResult.ApiError(503, "캐시 준비 전 — 잠시 후 다시 시도")
                    else -> WeatherResult.ApiError(code, "알 수 없는 오류")
                }
            } catch (e: IOException) {
                WeatherResult.NetworkError(e.message ?: "network error")
            } catch (e: Exception) {
                WeatherResult.NetworkError(e.message ?: "parse error")
            } finally {
                conn?.disconnect()
            }
        }
    }
}
