package com.iparsan.maprog.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    private val es = Locale("es", "ES")

    fun km(m: Double): String = if (m < 1000) "${m.toInt()} m" else String.format(es, "%.2f km", m / 1000)
    fun m(m: Double): String = "${m.toInt()} m"
    fun speed(kmh: Double): String = String.format(es, "%.1f km/h", kmh)

    fun duration(s: Long): String {
        val h = s / 3600
        val mi = (s % 3600) / 60
        val se = s % 60
        return if (h > 0) String.format(es, "%d:%02d:%02d", h, mi, se) else String.format(es, "%02d:%02d", mi, se)
    }

    fun hoursMinutes(s: Long): String {
        val h = s / 3600
        val mi = (s % 3600) / 60
        return if (h > 0) "${h} h ${mi} min" else "$mi min"
    }

    fun date(ms: Long): String = SimpleDateFormat("d MMM yyyy", es).format(Date(ms))
    fun dateIso(ms: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(Date(ms))
}
