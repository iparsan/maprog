package com.iparsan.maprog.util

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Lectura y escritura de archivos GPX 1.1 (el formato estándar que usan Wikiloc, Garmin, etc.). */
object Gpx {

    data class Parsed(val name: String?, val description: String?, val points: List<TrackPt>, val waypoints: List<Wpt>)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun write(name: String, description: String, activity: String, points: List<TrackPt>, waypoints: List<Wpt>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<gpx version="1.1" creator="Maprog" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        sb.append("  <metadata><name>").append(esc(name)).append("</name>")
        if (description.isNotBlank()) sb.append("<desc>").append(esc(description)).append("</desc>")
        sb.append("</metadata>\n")
        for (w in waypoints) {
            sb.append("  <wpt lat=\"${w.lat}\" lon=\"${w.lon}\"><ele>${w.ele}</ele><name>")
                .append(esc(w.name)).append("</name>")
            if (w.description.isNotBlank()) sb.append("<desc>").append(esc(w.description)).append("</desc>")
            sb.append("<type>").append(esc(w.type)).append("</type></wpt>\n")
        }
        sb.append("  <trk><name>").append(esc(name)).append("</name><type>").append(esc(activity)).append("</type><trkseg>\n")
        for (p in points) {
            sb.append("    <trkpt lat=\"${p.lat}\" lon=\"${p.lon}\"><ele>${"%.1f".format(Locale.US, p.ele)}</ele>")
            if (p.time > 0) sb.append("<time>").append(Fmt.dateIso(p.time)).append("</time>")
            sb.append("</trkpt>\n")
        }
        sb.append("  </trkseg></trk>\n</gpx>\n")
        return sb.toString()
    }

    private val isoFormats = listOf("yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ssXXX")

    private fun parseTime(s: String): Long {
        for (f in isoFormats) {
            try {
                val df = SimpleDateFormat(f, Locale.US)
                df.timeZone = TimeZone.getTimeZone("UTC")
                return df.parse(s.trim())?.time ?: continue
            } catch (_: Exception) { }
        }
        return 0L
    }

    /** Lee tracks (trkpt), rutas (rtept) y waypoints (wpt) de un GPX. */
    fun parse(input: InputStream): Parsed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        val points = mutableListOf<TrackPt>()
        val wpts = mutableListOf<Wpt>()
        var name: String? = null
        var desc: String? = null

        var curTag: String? = null
        var inPoint: String? = null
        var lat = 0.0; var lon = 0.0; var ele = 0.0; var time = 0L
        var pName = ""; var pDesc = ""; var pType = "generic"
        val stack = ArrayDeque<String>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.substringAfter(':')
                    stack.addLast(tag)
                    curTag = tag
                    if (tag == "trkpt" || tag == "rtept" || tag == "wpt") {
                        inPoint = tag
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                        ele = 0.0; time = 0L; pName = ""; pDesc = ""; pType = "generic"
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    if (text.isNotBlank()) {
                        val parent = stack.getOrNull(stack.size - 2)
                        when {
                            inPoint != null && curTag == "ele" -> ele = text.trim().toDoubleOrNull() ?: 0.0
                            inPoint != null && curTag == "time" -> time = parseTime(text)
                            inPoint == "wpt" && curTag == "name" -> pName = text.trim()
                            inPoint == "wpt" && curTag == "desc" -> pDesc = text.trim()
                            inPoint == "wpt" && curTag == "type" -> pType = text.trim().lowercase()
                            inPoint == null && curTag == "name" && name == null && (parent == "metadata" || parent == "trk" || parent == "rte") -> name = text.trim()
                            inPoint == null && curTag == "desc" && desc == null -> desc = text.trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.substringAfter(':')
                    if (tag == inPoint) {
                        if (tag == "wpt") {
                            val known = WaypointTypes.all.any { it.first == pType }
                            wpts.add(Wpt(lat, lon, ele, pName.ifBlank { "Punto" }, if (known) pType else "generic", pDesc))
                        } else {
                            points.add(TrackPt(lat, lon, ele, time))
                        }
                        inPoint = null
                    }
                    if (stack.isNotEmpty()) stack.removeLast()
                    curTag = stack.lastOrNull()
                }
            }
            event = parser.next()
        }
        return Parsed(name, desc, points, wpts)
    }
}
