package com.dalur.film.weather

import kotlinx.coroutines.delay

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
                    humidityPct = humidity,
                    windMs = wind,
                    windDirDeg = (seed * 3) % 360,
                    rainMm = if (seed % 7 == 0) 1.5 else 0.0,
                ),
                forecast = ForecastWeather(
                    stationId = "MOCK-${(seed % 100)}",
                    stationName = "가데이터 관측소",
                    distanceKm = 1.0 + (seed % 5) * 0.4,
                    baseKst = "202609161100",
                    hourly = listOf(
                        ForecastHour("202609161300", temp.toInt() + 1, 20, 1, 0),
                        ForecastHour("202609161400", temp.toInt() + 2, 30, 3, 0),
                        ForecastHour("202609161500", temp.toInt() + 1, 40, 3, 1),
                    ),
                    daily = mapOf("20260917" to DailyRange(temp.toInt() - 4, temp.toInt() + 5)),
                ),
                sourceNote = "가짜 데이터(mock) — earthus API 키 발급 전까지의 미리보기입니다. " +
                    "실제 연동 시 기상청(공공누리 제1유형) 출처로 교체됩니다.",
            )
        )
    }
}
