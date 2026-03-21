package com.eboat.data.ais

import com.eboat.domain.model.AisTarget

/**
 * Simple NMEA AIS parser supporting !AIVDM sentences (message types 1, 2, 3, 5).
 * Decodes 6-bit ASCII armored AIS payloads.
 */
object NmeaParser {

    fun parseAivdm(sentence: String): AisTarget? {
        if (!sentence.startsWith("!AIVDM")) return null
        val parts = sentence.split(",")
        if (parts.size < 6) return null

        // Only handle single-fragment messages for now
        val fragmentCount = parts[1].toIntOrNull() ?: return null
        if (fragmentCount != 1) return null

        val payload = parts[5]
        val bits = decodeSixBit(payload)
        if (bits.length < 38) return null

        val msgType = extractInt(bits, 0, 6)

        return when (msgType) {
            1, 2, 3 -> parsePositionReport(bits)
            else -> null
        }
    }

    private fun parsePositionReport(bits: String): AisTarget? {
        if (bits.length < 168) return null
        val mmsi = extractInt(bits, 8, 30).toString().padStart(9, '0')
        val sog = extractInt(bits, 46, 10) / 10f
        val lon = extractSignedInt(bits, 61, 28) / 600000.0
        val lat = extractSignedInt(bits, 89, 27) / 600000.0
        val cog = extractInt(bits, 116, 12) / 10f
        val heading = extractInt(bits, 128, 9)

        if (lon < -180 || lon > 180 || lat < -90 || lat > 90) return null

        return AisTarget(
            mmsi = mmsi,
            latitude = lat,
            longitude = lon,
            sog = sog,
            cog = cog,
            heading = if (heading == 511) 0 else heading
        )
    }

    private fun decodeSixBit(payload: String): String {
        val sb = StringBuilder()
        for (ch in payload) {
            var v = ch.code - 48
            if (v > 40) v -= 8
            for (bit in 5 downTo 0) {
                sb.append(if ((v shr bit) and 1 == 1) '1' else '0')
            }
        }
        return sb.toString()
    }

    private fun extractInt(bits: String, start: Int, len: Int): Int {
        if (start + len > bits.length) return 0
        return bits.substring(start, start + len).toInt(2)
    }

    private fun extractSignedInt(bits: String, start: Int, len: Int): Int {
        val unsigned = extractInt(bits, start, len)
        return if (bits[start] == '1') unsigned - (1 shl len) else unsigned
    }
}
