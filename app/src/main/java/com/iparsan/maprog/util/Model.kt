package com.iparsan.maprog.util

import kotlinx.serialization.Serializable

/** Un punto del track. */
@Serializable
data class TrackPt(val lat: Double, val lon: Double, val ele: Double = 0.0, val time: Long = 0L)

/** Un punto de interés (waypoint) con foto opcional. */
@Serializable
data class Wpt(
    val lat: Double,
    val lon: Double,
    val ele: Double = 0.0,
    val name: String,
    val type: String = "generic",
    val description: String = "",
    val photo: String? = null,
)

/** Actividades, como el selector de Wikiloc (aquí con las más usadas). */
enum class Activity(val key: String, val label: String, val emoji: String) {
    HIKING("hiking", "Senderismo", "🥾"),
    TREKKING("trekking", "Trekking", "🎒"),
    RUNNING("running", "Carrera", "🏃"),
    TRAIL_RUNNING("trail_running", "Trail running", "⛰️"),
    MTB("mtb", "Bicicleta de montaña", "🚵"),
    ROAD_BIKE("road_bike", "Ciclismo", "🚴"),
    WALKING("walking", "Paseo", "🚶"),
    MOUNTAINEERING("mountaineering", "Alpinismo", "🧗"),
    SKI("ski", "Esquí de montaña", "⛷️"),
    SNOWSHOE("snowshoe", "Raquetas de nieve", "❄️"),
    KAYAK("kayak", "Kayak", "🛶"),
    HORSE("horse", "A caballo", "🐎"),
    CAR("car", "Coche / 4x4", "🚙"),
    MOTO("moto", "Moto", "🏍️");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: HIKING
    }
}

/** Dificultad técnica, la misma escala de 5 niveles que usa Wikiloc. */
object Difficulty {
    val labels = listOf("Fácil", "Moderado", "Difícil", "Muy difícil", "Solo expertos")
    fun label(level: Int) = labels.getOrElse(level - 1) { labels[1] }
}

/** Tipos de waypoint para elegir al marcar un punto, como en la pantalla de grabación. */
object WaypointTypes {
    val all = listOf(
        "generic" to "📍 Punto",
        "summit" to "🏔️ Cima",
        "pass" to "⛰️ Collado",
        "viewpoint" to "👁️ Mirador",
        "fountain" to "💧 Fuente",
        "intersection" to "🔀 Cruce",
        "bridge" to "🌉 Puente",
        "river" to "🏞️ Río",
        "lake" to "🌊 Lago",
        "waterfall" to "💦 Cascada",
        "shelter" to "🛖 Refugio",
        "parking" to "🅿️ Aparcamiento",
        "picnic" to "🧺 Merendero",
        "monument" to "🏛️ Monumento",
        "danger" to "⚠️ Peligro",
        "photo" to "📷 Foto",
    )
    fun label(type: String) = all.firstOrNull { it.first == type }?.second ?: "📍 Punto"
    fun emoji(type: String) = label(type).substringBefore(' ')
}
