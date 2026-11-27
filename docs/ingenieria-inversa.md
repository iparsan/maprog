# Ingeniería inversa de Wikiloc

## 1. Método

La ingeniería inversa se hizo **de caja negra**: se estudió cómo se comporta la app desde fuera, sin descompilarla ni copiar nada de ella. Las fuentes fueron:

- La **ayuda oficial** de Wikiloc (help.wikiloc.com), por ejemplo «How to navigate a trail from the App».
- **Guías de usuarios**, como Travesía Pirenaica («Cómo usar Wikiloc», 2025), el tutorial de J. Barrado, ADSLZone y Leer del Viaje.
- Las **fichas y reseñas** en App Store y Google Play, que sirven para saber qué funciones valora la gente y qué le molesta.

Con esa información, una IA (Claude) ayudó a sacar la lista de pantallas, flujos y funciones, a diseñar la arquitectura y a escribir el código desde cero.

## 2. Estructura de pantallas de Wikiloc

```
                 ┌──────────────── Barra inferior ────────────────┐
                 │  Explora        Grabar ruta          Perfil     │
                 └──────┬──────────────┬───────────────────┬──────┘
                        │              │                   │
   Buscador + mapa/lista + filtros    Selector de actividad     Tus rutas
                        │             «Empieza a grabar»        Rutas guardadas
                Ficha de ruta ──────▶ Grabando:                 Listas
   (mapa, estadísticas, perfil,        tiempo · distancia ·     Siguiendo
    fotos, waypoints, comentarios,     desnivel                 Ajustes
    «Seguir ruta», Guardar,           ⏸ pausa · waypoint ·
    compartir, aplaudir)               elevación · Finalizar
                        │                    │
                        └── «Seguir ruta» ───┘ (track naranja, avisos al salirse)
```

## 3. Funciones observadas y decisión

| Función de Wikiloc | Free/Premium | ¿En Maprog? |
|---|---|---|
| Buscar rutas por texto y en el mapa | Free | Sí |
| Filtros: actividad, distancia, dificultad, circular | Free | Sí |
| Filtro «solo autores que sigo» | Premium | Sí (gratis) |
| Ordenar por popularidad, TrailRank o fecha | Free | Sí (TrailRank propio) |
| Ficha con mapa, perfil, estadísticas, fotos, waypoints y comentarios | Free | Sí |
| Grabar ruta con GPS en segundo plano | Free | Sí |
| Waypoints con tipo, foto y descripción | Free | Sí |
| Seguir una ruta con avisos | Premium | Sí (gratis) |
| Rutas y mapas sin conexión | Premium / Free | Rutas sí, mapas con caché |
| Listas | Free | Sí |
| Exportar o enviar GPX | Premium | Sí (gratis) |
| Seguir a usuarios, aplaudir, comentar | Free | Sí |
| Live Tracking (posición en directo por enlace) | Premium | No |
| Planificador de rutas | Premium (web) | No |
| Identificador de cimas con realidad aumentada | Premium | No |
| Envío a Garmin o Apple Watch | Free/Premium | No (se puede usar el GPX) |

## 4. Diferencias de diseño a propósito

- **Nombre, logo, colores y textos propios.** Copiar la marca no es ingeniería inversa, sería plagio.
- **TrailRank:** Wikiloc no publica la fórmula. La de Maprog es pública y está en `TrackMath.trailRank` y en la vista SQL `trail_feed`.
- **Mapas libres** de OpenStreetMap y OpenTopoMap en lugar de los mapas propios de Wikiloc.
- **Sin muros de pago:** las funciones Premium que se han replicado son gratis en Maprog.

## 5. Problemas encontrados

- **Ruido del GPS en la altitud.** Inflaba el desnivel. Se resolvió con un filtro de histéresis de 4 m.
- **Rutas que pasan dos veces por el mismo sitio.** Al seguirlas, la posición "saltaba". Se resolvió buscando en una ventana de puntos por delante.
- **Seguridad de la base de datos.** La clave pública va dentro del APK, así que la protección se hace con políticas RLS en Postgres.
- **Compilar sin ordenador con Android Studio.** El APK lo compila GitHub Actions en cada cambio.
