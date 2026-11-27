package com.iparsan.maprog.recording

import com.iparsan.maprog.util.TrackMath
import com.iparsan.maprog.util.TrackPt
import com.iparsan.maprog.util.TrackStats
import com.iparsan.maprog.util.Wpt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RecStatus { IDLE, RECORDING, PAUSED }

/** Ruta que se está siguiendo (modo "Seguir ruta"). */
data class FollowTarget(
    val trailId: Long,
    val name: String,
    val points: List<TrackPt>,
    val waypoints: List<Wpt>,
    val cumulative: List<Double> = TrackMath.cumulative(points),
)

data class RecState(
    val status: RecStatus = RecStatus.IDLE,
    val activity: String = "hiking",
    val points: List<TrackPt> = emptyList(),
    val waypoints: List<Wpt> = emptyList(),
    val startedAt: Long = 0L,
    val pausedAt: Long = 0L,
    val pausedTotalMs: Long = 0L,
    val stats: TrackStats = TrackStats(),
    val lastLat: Double? = null,
    val lastLon: Double? = null,
    val lastEle: Double = 0.0,
    val speedKmh: Double = 0.0,
    val accuracyM: Float = 0f,
    val follow: FollowTarget? = null,
    /** índice del punto de la ruta seguida al que has llegado */
    val followIndex: Int = 0,
    val offRouteM: Double = 0.0,
    val nearWaypoint: String? = null,
    val reversed: Boolean = false,
) {
    val active get() = status != RecStatus.IDLE
    fun elapsedMs(now: Long = System.currentTimeMillis()): Long {
        if (startedAt == 0L) return 0
        val end = if (status == RecStatus.PAUSED) pausedAt else now
        return (end - startedAt - pausedTotalMs).coerceAtLeast(0)
    }
    val isOffRoute get() = follow != null && offRouteM > OFF_ROUTE_M

    companion object { const val OFF_ROUTE_M = 40.0 }
}

/**
 * Estado global de la grabación. El servicio en primer plano le pasa las posiciones GPS
 * y la interfaz lo observa. Así la grabación sigue aunque cierres la pantalla.
 */
object Recorder {
    private val _state = MutableStateFlow(RecState())
    val state: StateFlow<RecState> = _state.asStateFlow()

    fun setActivity(key: String) = _state.update { it.copy(activity = key) }

    fun setFollow(target: FollowTarget?) = _state.update { it.copy(follow = target, followIndex = 0, offRouteM = 0.0, reversed = false) }

    fun reverseFollow() = _state.update { s ->
        val f = s.follow ?: return@update s
        s.copy(follow = f.copy(points = f.points.reversed(), cumulative = TrackMath.cumulative(f.points.reversed())), reversed = !s.reversed, followIndex = 0)
    }

    fun start() = _state.update {
        it.copy(status = RecStatus.RECORDING, points = emptyList(), waypoints = emptyList(),
            startedAt = System.currentTimeMillis(), pausedTotalMs = 0, stats = TrackStats())
    }

    fun pause() = _state.update { if (it.status == RecStatus.RECORDING) it.copy(status = RecStatus.PAUSED, pausedAt = System.currentTimeMillis()) else it }

    fun resume() = _state.update {
        if (it.status == RecStatus.PAUSED) it.copy(status = RecStatus.RECORDING, pausedTotalMs = it.pausedTotalMs + (System.currentTimeMillis() - it.pausedAt)) else it
    }

    fun addWaypoint(w: Wpt) = _state.update { it.copy(waypoints = it.waypoints + w) }

    fun reset() { _state.value = RecState(activity = _state.value.activity) }

    /** Llega una posición nueva del GPS. Devuelve eventos para avisar (vibrar) desde el servicio. */
    fun onLocation(lat: Double, lon: Double, ele: Double, accuracy: Float, speedMs: Float, time: Long): FollowEvent? {
        var event: FollowEvent? = null
        _state.update { s ->
            var n = s.copy(lastLat = lat, lastLon = lon, lastEle = ele, accuracyM = accuracy, speedKmh = speedMs * 3.6)
            if (s.status == RecStatus.RECORDING && accuracy < 35f) {
                val p = TrackPt(lat, lon, ele, time)
                val last = s.points.lastOrNull()
                // Descarta saltos mínimos para no llenar el track de ruido estando parado
                if (last == null || TrackMath.distance(last, p) >= 3.0) {
                    val pts = s.points + p
                    n = n.copy(points = pts, stats = TrackMath.stats(pts))
                }
            }
            val f = s.follow
            if (f != null && f.points.isNotEmpty()) {
                // Busca el punto más cercano por delante (ventana) para no "saltar" en rutas que se cruzan
                val (idx, d) = TrackMath.nearest(f.points, lat, lon, from = (s.followIndex - 20).coerceAtLeast(0), window = 400)
                val (gIdx, gD) = if (d > 200) TrackMath.nearest(f.points, lat, lon) else idx to d
                val wasOff = s.isOffRoute
                val nowOff = gD > RecState.OFF_ROUTE_M
                val near = f.waypoints.firstOrNull { TrackMath.distance(it.lat, it.lon, lat, lon) < 30 }?.name
                if (nowOff && !wasOff) event = FollowEvent.OFF_ROUTE
                if (!nowOff && wasOff) event = FollowEvent.BACK_ON_ROUTE
                if (near != null && near != s.nearWaypoint) event = FollowEvent.NEAR_WAYPOINT
                if (gIdx >= f.points.size - 3 && s.followIndex < f.points.size - 3 && !nowOff) event = FollowEvent.FINISHED
                n = n.copy(followIndex = maxOf(if (gD < 200) gIdx else s.followIndex, 0), offRouteM = gD, nearWaypoint = near)
            }
            n
        }
        return event
    }
}

enum class FollowEvent { OFF_ROUTE, BACK_ON_ROUTE, NEAR_WAYPOINT, FINISHED }
