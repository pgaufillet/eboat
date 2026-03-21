package com.eboat.data.weather

import com.eboat.domain.model.WeatherData
import com.eboat.domain.model.WeatherForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WeatherRepository {

    /** Fetch weather + marine data for a single point */
    suspend fun fetchForecast(lat: Double, lon: Double): WeatherData? = withContext(Dispatchers.IO) {
        try {
            val weatherUrl = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$lat&longitude=$lon" +
                "&hourly=wind_speed_10m,wind_direction_10m,wind_gusts_10m,surface_pressure,visibility" +
                "&forecast_days=3&timezone=auto"

            val marineUrl = "https://marine-api.open-meteo.com/v1/marine?" +
                "latitude=$lat&longitude=$lon" +
                "&hourly=wave_height,wave_direction,wave_period,swell_wave_height,swell_wave_direction,swell_wave_period" +
                "&forecast_days=3&timezone=auto"

            val weatherJson = JSONObject(URL(weatherUrl).readText())
            val marineJson = try { JSONObject(URL(marineUrl).readText()) } catch (_: Exception) { null }

            val tz = weatherJson.optString("timezone", "UTC")
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
                timeZone = TimeZone.getTimeZone(tz)
            }

            val hourly = weatherJson.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val windSpeed = hourly.getJSONArray("wind_speed_10m")
            val windDir = hourly.getJSONArray("wind_direction_10m")
            val gusts = hourly.getJSONArray("wind_gusts_10m")
            val pressure = hourly.getJSONArray("surface_pressure")
            val visArray = hourly.getJSONArray("visibility")

            val marineHourly = marineJson?.optJSONObject("hourly")
            val waveH = marineHourly?.optJSONArray("wave_height")
            val waveD = marineHourly?.optJSONArray("wave_direction")
            val waveP = marineHourly?.optJSONArray("wave_period")
            val swellH = marineHourly?.optJSONArray("swell_wave_height")
            val swellD = marineHourly?.optJSONArray("swell_wave_direction")
            val swellP = marineHourly?.optJSONArray("swell_wave_period")

            val forecasts = mutableListOf<WeatherForecast>()
            for (i in 0 until times.length()) {
                val time = isoFormat.parse(times.getString(i))?.time ?: continue
                val wsKmh = if (windSpeed.isNull(i)) 0.0 else windSpeed.getDouble(i)
                val gsKmh = if (gusts.isNull(i)) 0.0 else gusts.getDouble(i)
                forecasts.add(WeatherForecast(
                    time = time,
                    windSpeedKnots = wsKmh * 0.539957,
                    windDirectionDeg = if (windDir.isNull(i)) 0.0 else windDir.getDouble(i),
                    gustSpeedKnots = gsKmh * 0.539957,
                    waveHeightM = safeDouble(waveH, i),
                    waveDirectionDeg = safeDouble(waveD, i),
                    wavePeriodS = safeDouble(waveP, i),
                    swellHeightM = safeDouble(swellH, i),
                    swellDirectionDeg = safeDouble(swellD, i),
                    swellPeriodS = safeDouble(swellP, i),
                    pressureHpa = if (pressure.isNull(i)) 1013.0 else pressure.getDouble(i),
                    visibility = if (visArray.isNull(i)) 0.0 else visArray.getDouble(i) / 1000.0
                ))
            }

            WeatherData(lat, lon, forecasts)
        } catch (e: Exception) {
            null
        }
    }

    /** Fetch weather for multiple points in parallel (corridor) */
    suspend fun fetchCorridor(points: List<Pair<Double, Double>>): List<WeatherData> = coroutineScope {
        points.take(30).map { (lat, lon) ->
            async { fetchForecast(lat, lon) }
        }.awaitAll().filterNotNull()
    }

    private fun safeDouble(arr: org.json.JSONArray?, index: Int): Double {
        if (arr == null || index >= arr.length() || arr.isNull(index)) return 0.0
        return arr.getDouble(index)
    }
}
