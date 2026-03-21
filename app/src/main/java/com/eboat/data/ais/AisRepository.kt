package com.eboat.data.ais

import com.eboat.domain.model.AisTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

class AisRepository {

    private val _targets = MutableStateFlow<Map<String, AisTarget>>(emptyMap())
    val targets: Flow<Map<String, AisTarget>> = _targets.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: Flow<Boolean> = _connected.asStateFlow()

    /**
     * Connect to a TCP NMEA source (e.g. a multiplexer on the boat's WiFi).
     * Typical: host=192.168.x.x, port=10110 or 39150.
     */
    suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        try {
            val socket = Socket(host, port)
            _connected.value = true
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            while (isActive && !socket.isClosed) {
                val line = reader.readLine() ?: break
                val target = NmeaParser.parseAivdm(line) ?: continue
                val current = _targets.value.toMutableMap()
                val existing = current[target.mmsi]
                current[target.mmsi] = if (existing != null) {
                    existing.copy(
                        latitude = if (target.hasPosition) target.latitude else existing.latitude,
                        longitude = if (target.hasPosition) target.longitude else existing.longitude,
                        cog = target.cog,
                        sog = target.sog,
                        heading = target.heading,
                        name = target.name.ifBlank { existing.name },
                        lastUpdate = System.currentTimeMillis()
                    )
                } else target

                // Purge stale targets (>10 min)
                val cutoff = System.currentTimeMillis() - 600_000
                _targets.value = current.filterValues { it.lastUpdate > cutoff }
            }

            socket.close()
            _connected.value = false
        } catch (e: Exception) {
            _connected.value = false
        }
    }
}
