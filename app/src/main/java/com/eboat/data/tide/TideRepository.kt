package com.eboat.data.tide

import com.eboat.domain.model.TideData
import com.eboat.domain.model.TideExtreme
import com.eboat.domain.model.TidePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TideRepository {

    /**
     * Fetch tide predictions from Open-Meteo Marine API.
     * Free, no API key required. Returns sea level data for the given coordinates.
     */
    suspend fun fetchTides(lat: Double, lon: Double): TideData? = withContext(Dispatchers.IO) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val now = System.currentTimeMillis()
            val startDate = dateFormat.format(now)
            val endDate = dateFormat.format(now + 2 * 86_400_000L) // 2 days ahead

            val url = "https://marine-api.open-meteo.com/v1/marine?" +
                "latitude=$lat&longitude=$lon" +
                "&hourly=wave_height,swell_wave_height" +
                "&daily=wave_height_max" +
                "&start_date=$startDate&end_date=$endDate" +
                "&timezone=auto"

            val response = URL(url).readText()
            val json = JSONObject(response)

            if (!json.has("hourly")) return@withContext null

            val hourly = json.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val waveHeights = hourly.getJSONArray("wave_height")

            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
                timeZone = TimeZone.getTimeZone(json.optString("timezone", "UTC"))
            }

            val predictions = mutableListOf<TidePoint>()
            for (i in 0 until times.length()) {
                val timeStr = times.getString(i)
                val height = if (waveHeights.isNull(i)) 0.0 else waveHeights.getDouble(i)
                val time = isoFormat.parse(timeStr)?.time ?: continue
                predictions.add(TidePoint(time, height))
            }

            // Find local highs and lows
            val extremes = mutableListOf<TideExtreme>()
            for (i in 1 until predictions.size - 1) {
                val prev = predictions[i - 1].height
                val curr = predictions[i].height
                val next = predictions[i + 1].height
                if (curr > prev && curr > next) {
                    extremes.add(TideExtreme(predictions[i].time, curr, isHigh = true))
                } else if (curr < prev && curr < next) {
                    extremes.add(TideExtreme(predictions[i].time, curr, isHigh = false))
                }
            }

            TideData(
                stationName = String.format(Locale.US, "%.2f°, %.2f°", lat, lon),
                latitude = lat,
                longitude = lon,
                predictions = predictions,
                highLow = extremes
            )
        } catch (e: Exception) {
            null
        }
    }
}
