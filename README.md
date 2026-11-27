# 🗺️ Maprog

**Maprog** es una app Android **libre y de código abierto** para descubrir, grabar, seguir y compartir rutas al aire libre: senderismo, bici, carrera, alpinismo, kayak…

Es un **clon funcional de Wikiloc** hecho por **ingeniería inversa con IA**. Se estudió cómo funciona Wikiloc desde fuera (guías, ayuda oficial, reseñas de las tiendas de apps) y se volvió a programar desde cero. No contiene código, logos, iconos ni textos de Wikiloc. Todo el código es propio y está publicado con licencia GPL-3.0.

> Autor: **Iván Parrado** ([@iparsan](https://github.com/iparsan)) · Proyecto de clase, 2026

---

## 📲 Descargar

Ve a **[Releases](../../releases/latest)** y descarga `Maprog.apk` desde el móvil. Al abrirlo, Android pedirá permiso para instalar apps de origen desconocido. Requiere **Android 8.0** o superior.

Cada vez que se sube código a `main`, GitHub Actions compila un APK nuevo automáticamente.

## ✨ Funciones

La app tiene las mismas tres pestañas que Wikiloc: **Explora · Grabar ruta · Perfil**.

| Wikiloc | Maprog | Cómo está hecho |
|---|---|---|
| Buscador de rutas con mapa a pantalla completa | ✅ Explora con vista **lista / mapa** | Supabase (Postgres) + osmdroid |
| Filtros por actividad, distancia, dificultad, circular, autores que sigues | ✅ | Consultas filtradas en la vista `trail_feed` |
| Orden por popularidad, TrailRank y más recientes | ✅ (TrailRank con fórmula propia, 0–100) | Calculado en SQL |
| «Buscar al mover el mapa» | ✅ | Búsqueda por los límites del mapa visible |
| Ficha de ruta: mapa, estadísticas, perfil de elevación, fotos, waypoints, comentarios | ✅ | Compose + Canvas |
| Grabar con GPS en segundo plano | ✅ Tiempo, distancia, desnivel, velocidad, altitud y precisión | Servicio en primer plano + `LocationManager` (sin Google) |
| Botón rojo de pausa, waypoints con foto y tipo, elevación desplegable | ✅ | Cámara del sistema + FileProvider |
| Seguir ruta: track en naranja, posición en azul, aviso al salirse, aviso cerca de waypoint | ✅ Vibra a más de 40 m de la ruta, avisa al llegar e invierte el sentido | Algoritmo de punto más cercano |
| Rutas guardadas sin conexión | ✅ Guardadas en el móvil y caché de mapas de 300 MB | Room (SQLite) |
| Listas para organizar rutas | ✅ | Room |
| Importar y exportar GPX, abrir .gpx desde otras apps | ✅ | Lector y escritor GPX 1.1 propio |
| Cuentas, perfil, seguir a usuarios, aplausos, comentarios | ✅ | Supabase Auth + RLS |
| Subida automática de rutas grabadas sin cobertura | ✅ | Cola de pendientes |
| Mapas topográficos | ✅ OpenTopoMap y OpenStreetMap | Teselas libres |

**Lo que no se ha copiado** porque en Wikiloc es Premium o necesita servicios de pago: identificador de cimas con realidad aumentada, planificador automático de rutas, seguimiento en directo por enlace y envío a relojes Garmin.

## 🏗️ Arquitectura

```
app/src/main/java/com/iparsan/maprog/
├── MaprogApp.kt            Inicio: configuración de mapas, base de datos y notificaciones
├── data/                   Room (SQLite): rutas, puntos, waypoints y listas · Repo
├── recording/              Recorder (estado de la grabación) · RecordingService (GPS)
├── remote/Social.kt        Supabase: cuentas, rutas, fotos, comentarios, aplausos y seguidores
├── util/                   Cálculos (distancia, desnivel, TrailRank), GPX y formatos
└── ui/                     Jetpack Compose
    ├── explore/            Pestaña Explora
    ├── record/             Pestaña Grabar ruta
    ├── profile/            Perfil, otros usuarios y listas
    ├── trail/              Ficha de ruta
    └── components/         Mapa (osmdroid), perfil de elevación y tarjetas
supabase/schema.sql         Tablas, seguridad RLS, vistas y almacenamiento de fotos
```

**Tecnologías:** Kotlin · Jetpack Compose (Material 3) · Room · osmdroid (OpenStreetMap) · Supabase (PostgreSQL, Auth, Storage) · Ktor · Coil · GitHub Actions.

Todas las dependencias son de código abierto, y la app no usa Google Play Services.

### Detalles técnicos interesantes

- **Desnivel con histéresis.** El GPS tiene un error de altura de ±10 m. Si se sumaran todas las subidas y bajadas, el desnivel saldría inflado, así que solo se cuenta un cambio cuando supera 4 m (`TrackMath.stats`).
- **Seguir ruta sin "saltos".** El punto más cercano se busca en una ventana por delante de tu posición, para no confundirse cuando la ruta pasa dos veces por el mismo sitio.
- **Seguridad.** La clave de Supabase que lleva la app es la pública (*anon*). Lo que cada usuario puede leer o modificar lo controlan las políticas **Row Level Security** de Postgres: solo puedes editar tus rutas, tus comentarios y tus fotos.

## 🛠️ Compilar tú mismo

1. Clona el repositorio y ábrelo con Android Studio (Ladybug o posterior, JDK 17).
2. Para usar tu propio servidor: crea un proyecto gratis en [supabase.com](https://supabase.com), ejecuta `supabase/schema.sql` en el *SQL Editor* y pon tu URL y tu *anon key* en `supabase.properties`.
3. Ejecuta `./gradlew assembleDebug`.

Si compilas sin claves de Supabase, la app funciona igual pero solo en local (grabar, seguir, GPX y listas).

## 🔍 Ingeniería inversa

El análisis de la app original, con la estructura de pantallas, el diagrama de navegación y la comparación de funciones, está en [`docs/ingenieria-inversa.md`](docs/ingenieria-inversa.md).

## 📄 Licencia

[GNU GPL v3.0](LICENSE). Puedes usar, estudiar, modificar y redistribuir Maprog, siempre que tus versiones también sean libres.

Mapas © colaboradores de [OpenStreetMap](https://www.openstreetmap.org/copyright) (ODbL) y [OpenTopoMap](https://opentopomap.org) (CC-BY-SA).

*Wikiloc es una marca de Wikiloc Outdoor SL. Maprog es un proyecto educativo independiente, sin relación con Wikiloc.*
