package com.iparsan.maprog.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class TrackStats(
    val distanceM: Double = 0.0,
    val elevGainM: Double = 0.0,
    val elevLossM: Double = 0.0,
    val maxEleM: Double = 0.0,
    val minEleM: Double = 0.0,
    val totalTimeS: Long = 0,
    val movingTimeS: Long = 0,
    val circular: Boolean = false,
) {
    val avgSpeedKmh: Double get() = if (movingTimeS > 0) distanceM / movingTimeS * 3.6 else 0.0
}

object TrackMath {
    private const val R = 6_371_000.0

    fun distance(a: TrackPt, b: TrackPt): Double = distance(a.lat, a.lon, b.lat, b.lon)

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * R * atan2(sqrt(h), sqrt(1 - h))
    }

    /**
     * Desnivel con histéresis: el GPS tiene mucho ruido en altura, así que solo se
     * cuenta una subida o bajada cuando supera [threshold] metros.
     */
    fun stats(points: List<TrackPt>, threshold: Double = 4.0): TrackStats {
        if (points.isEmpty()) return TrackStats()
        var dist = 0.0
        var gain = 0.0
        var loss = 0.0
        var maxE = points[0].ele
        var minE = points[0].ele
        var ref = points[0].ele
        var moving = 0L
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val d = distance(a, b)
            dist += d
            val dt = (b.time - a.time) / 1000
            // Se considera "en movimiento" si va a más de 0,5 km/h
            if (dt in 1..600 && d / dt > 0.14) moving += dt
            maxE = max(maxE, b.ele)
            minE = min(minE, b.ele)
            val diff = b.ele - ref
            if (diff >= threshold) { gain += diff; ref = b.ele }
            else if (diff <= -threshold) { loss += -diff; ref = b.ele }
        }
        val total = if (points.last().time > 0 && points.first().time > 0) (points.last().time - points.first().time) / 1000 else 0
        val circular = points.size > 10 && distance(points.first(), points.last()) < 150
        return TrackStats(dist, gain, loss, maxE, minE, total, if (moving > 0) moving else total, circular)
    }

    /** Distancia acumulada en cada punto (para el perfil de elevación). */
    fun cumulative(points: List<TrackPt>): List<Double> {
        val out = ArrayList<Double>(points.size)
        var acc = 0.0
        points.forEachIndexed { i, p ->
            if (i > 0) acc += distance(points[i - 1], p)
            out.add(acc)
        }
        return out
    }

    /** Índice del punto del track más cercano a una posición y su distancia en metros. */
    fun nearest(points: List<TrackPt>, lat: Double, lon: Double, from: Int = 0, window: Int = Int.MAX_VALUE): Pair<Int, Double> {
        var best = -1
        var bestD = Double.MAX_VALUE
        val end = if (window == Int.MAX_VALUE) points.size else min(points.size, from + window)
        for (i in max(0, from) until end) {
            val d = distance(points[i].lat, points[i].lon, lat, lon)
            if (d < bestD) { bestD = d; best = i }
        }
        return best to bestD
    }

    /** Reduce el número de puntos (para subir rutas largas sin pasarse de tamaño). */
    fun simplify(points: List<TrackPt>, minDistM: Double = 5.0): List<TrackPt> {
        if (points.size < 3) return points
        val out = mutableListOf(points.first())
        for (i in 1 until points.size - 1) {
            if (distance(out.last(), points[i]) >= minDistM) out.add(points[i])
        }
        out.add(points.last())
        return out
    }

    /**
     * TrailRank de Maprog (0–100). Wikiloc no publica su fórmula; esta es nuestra versión:
     * premia rutas bien documentadas (descripción, fotos, waypoints) y valoradas por la comunidad.
     */
    fun trailRank(descriptionLen: Int, photos: Int, waypoints: Int, claps: Int, comments: Int): Int {
        var r = 10
        r += when { descriptionLen > 300 -> 12; descriptionLen > 80 -> 8; descriptionLen > 0 -> 3; else -> 0 }
        r += min(photos, 5) * 3
        r += min(waypoints, 5) * 2
        r += min(claps * 3, 30)
        r += min(comments * 4, 20)
        return min(r, 100).coerceAtLeast(0).let { (it / 1.0).roundToInt() }
    }
}
