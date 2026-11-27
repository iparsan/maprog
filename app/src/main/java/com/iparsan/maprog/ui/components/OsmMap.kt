package com.iparsan.maprog.ui.components

import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iparsan.maprog.ui.theme.PositionBlue
import com.iparsan.maprog.ui.theme.TrackOrange
import com.iparsan.maprog.util.TrackPt
import com.iparsan.maprog.util.WaypointTypes
import com.iparsan.maprog.util.Wpt
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.ScaleBarOverlay

/** Capas de mapa disponibles (todas libres, sin claves). */
enum class MapLayer(val label: String, val source: ITileSource) {
    OSM("OpenStreetMap", TileSourceFactory.MAPNIK),
    TOPO("Topográfico (OpenTopoMap)", TileSourceFactory.OpenTopo),
}

/** Una línea a pintar en el mapa. */
data class MapLine(val points: List<TrackPt>, val color: Int, val width: Float = 10f)

/** Un marcador a pintar en el mapa (salida, llegada, waypoints, rutas del explorador...). */
data class MapPin(
    val lat: Double,
    val lon: Double,
    val title: String,
    val emoji: String = "📍",
    val onClick: (() -> Unit)? = null,
)

/**
 * Mapa de OpenStreetMap (osmdroid) dentro de Compose.
 * Pinta líneas, marcadores, la posición actual y, opcionalmente, ajusta la vista a la ruta.
 */
@Composable
fun OsmMap(
    modifier: Modifier = Modifier,
    layer: MapLayer = MapLayer.TOPO,
    lines: List<MapLine> = emptyList(),
    pins: List<MapPin> = emptyList(),
    myLat: Double? = null,
    myLon: Double? = null,
    fitTo: List<TrackPt>? = null,
    follow: Boolean = false,
    showCompass: Boolean = false,
    initialCenter: GeoPoint = GeoPoint(37.05, -3.35), // Sierra Nevada / Alpujarra por defecto
    initialZoom: Double = 11.0,
    onMapMoved: ((BoundingBox) -> Unit)? = null,
    onMapReady: ((MapView) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { MapState() }

    val map = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            controller.setZoom(initialZoom)
            controller.setCenter(initialCenter)
            overlays.add(ScaleBarOverlay(this).apply { setAlignBottom(true); setCentred(false) })
        }
    }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            map.onDetach()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            onMapReady?.invoke(map)
            if (onMapMoved != null) {
                map.addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean { onMapMoved(map.boundingBox); return false }
                    override fun onZoom(event: ZoomEvent?): Boolean { onMapMoved(map.boundingBox); return false }
                })
            }
            map
        },
        update = { m ->
            if (m.tileProvider.tileSource != layer.source) m.setTileSource(layer.source)

            // Quita las capas dinámicas anteriores y vuelve a pintar
            m.overlays.removeAll(state.dynamic)
            state.dynamic.clear()

            lines.forEach { l ->
                if (l.points.size >= 2) {
                    val pl = Polyline(m).apply {
                        setPoints(l.points.map { GeoPoint(it.lat, it.lon, it.ele) })
                        outlinePaint.color = l.color
                        outlinePaint.strokeWidth = l.width
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        isGeodesic = false
                    }
                    state.dynamic.add(pl)
                }
            }
            pins.forEach { p ->
                val mk = Marker(m).apply {
                    position = GeoPoint(p.lat, p.lon)
                    title = p.title
                    icon = EmojiDrawable(m.context, p.emoji)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    if (p.onClick != null) setOnMarkerClickListener { _, _ -> p.onClick.invoke(); true }
                }
                state.dynamic.add(mk)
            }
            if (myLat != null && myLon != null) {
                val me = Marker(m).apply {
                    position = GeoPoint(myLat, myLon)
                    icon = DotDrawable(PositionBlue.toArgb())
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                    title = "Tú"
                }
                state.dynamic.add(me)
                if (follow) m.controller.animateTo(GeoPoint(myLat, myLon))
            }
            m.overlays.addAll(state.dynamic)

            if (showCompass && state.compass == null) {
                val c = CompassOverlay(m.context, InternalCompassOrientationProvider(m.context), m)
                c.enableCompass()
                m.overlays.add(c)
                state.compass = c
            }

            val fit = fitTo
            if (fit != null && fit.size >= 2 && state.fittedTo !== fit) {
                state.fittedTo = fit
                val box = BoundingBox.fromGeoPoints(fit.map { GeoPoint(it.lat, it.lon) })
                m.post { m.zoomToBoundingBox(box.increaseByScale(1.25f), false) }
            }
            m.invalidate()
        },
    )
}

private class MapState {
    val dynamic = mutableListOf<org.osmdroid.views.overlay.Overlay>()
    var fittedTo: List<TrackPt>? = null
    var compass: CompassOverlay? = null
}

fun trackLine(points: List<TrackPt>, color: Int = TrackOrange.toArgb(), width: Float = 10f) = MapLine(points, color, width)

fun waypointPins(wpts: List<Wpt>, onClick: ((Wpt) -> Unit)? = null) = wpts.map { w ->
    MapPin(w.lat, w.lon, w.name, WaypointTypes.emoji(w.type), onClick?.let { { it(w) } })
}

fun startEndPins(points: List<TrackPt>): List<MapPin> {
    if (points.isEmpty()) return emptyList()
    val s = points.first()
    val e = points.last()
    return listOf(MapPin(s.lat, s.lon, "Salida", "🟢"), MapPin(e.lat, e.lon, "Llegada", "🏁"))
}
