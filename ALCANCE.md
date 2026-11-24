# Alcance de Promeia Music 0.5.0

Estado: **implementado en código, pendiente de compilación y validación en Android**. Parcial indica que existe una implementación limitada; pendiente indica que aún no se incluye. Se priorizó audio local, seguridad del almacenamiento y datos persistentes.

| Bloque | Estado | Detalle |
|---|---|---|
| 1 Inicio | Implementado | Recientes, álbumes, favoritas, artistas, sugerencias locales por baja escucha, secciones configurables y búsqueda. |
| 2 Reproductor | Implementado / verificar | Controles, seek, ±10 s, aleatorio/repetición, volumen, cola editable, carátula, animación, MediaSession en segundo plano, notificación y controles de sistema. Guarda cola/posición cada 5 s y al salir. Bluetooth, bloqueo y auriculares dependen del sistema y requieren pruebas. |
| 3 Biblioteca | Implementado | Importación selectiva, categorías sencillas, favoritos, ocultas, selección múltiple, ordenar, compartir, excluir carpetas y refrescar. |
| 4 Playlists | Implementado | CRUD, descripción, portada local, favoritas, reordenar hacia arriba, reproducción, duplicar, exportar/importar M3U y compartir texto. Selector único Agregar a playlist. |
| 5 Carátulas | Básico | Muestra la carátula incrustada en cada canción y en el reproductor. Las portadas de playlists pueden elegirse desde una imagen local. Sin búsqueda en línea ni herramientas avanzadas. |
| 6 Información de canciones | Básico | Título, artista, álbum y duración dentro de una tarjeta plegable. Sin editor de metadatos complejos ni ficha técnica del archivo. |
| 7 Búsqueda | Implementado | Instantánea, parcial, categorías y búsquedas guardadas manualmente. Sin predicción avanzada. |
| 8 Internet | Parcial | Carátulas y enlace a fuente. Biografías, fechas de lanzamiento y letras autorizadas pendientes. |
| 9 Music Match / Spotify | Parcial | Importación de archivos M3U o TSV con coincidencias exactas normalizadas, evita ambigüedades y muestra faltantes. OAuth/API Spotify no incluida. Revisar reglas y acceso para una futura integración de transferencia de playlists. |
| 10 Archivos | Parcial | Importar archivos/carpetas por SAF a almacenamiento privado, duplicados exactos SHA-256, tamaño/ruta, exportar y eliminar solo copias privadas. Mover archivos originales no se incluye. Formatos según decodificador, WMA no garantizado. |
| 11 Ecualizador | Parcial | Cinco controles lógicos de bandas mapeados a las bandas del dispositivo, siete preajustes, ajuste personalizado persistente y panel externo si disponible. Balance, amplificador dedicado, normalización y perfiles múltiples pendientes. |
| 12 Personalización | Parcial | Tema oscuro/claro/sistema, paletas, color por carátula en oscuro, secciones inicio y transición visual. Iconos, estilos alternativos, navegación y tamaños configurables pendientes. |
| 13 Estadísticas | Retirado | No existe pestaña de estadísticas ni se registran nuevas reproducciones para generar conteos o tendencias. |
| 14 Adicionales | Parcial | Sleep Mode, favoritos, posición y cola restauradas en pausa, historial, compartir, duración mínima. Crossfade de audio, acciones personalizadas Bluetooth/auriculares y reanudación automática audible pendientes. |
| 15 Backup | Parcial | Copia/restauración JSON por URI, permisos, carpetas, preferencias y limpieza de caché. Exportación ZIP incluye audio, carátulas y JSON; no existe restauración automática del ZIP ni migración integral entre teléfonos; gestión avanzada de carátulas y almacenamiento pendientes. |
| 16 Tecnología | Implementado / verificar | Kotlin, Compose, Media3, Room, WorkManager, API Android, paquetes separados, tests unitarios y Actions. Sin compilación real en el entorno de generación; optimización, migraciones y pruebas instrumentadas pendientes. |
| 17 Smart Playlist | Parcial | Crea snapshot de las 25 más escuchadas; no reglas personalizables o actualización dinámica. Auto Organizer organiza virtualmente, no mueve archivos; Duplicate Finder muestra coincidencias de título/artista y la importación evita duplicados exactos por SHA-256. Dynamic Player, Backup y Sleep Mode según limitaciones anteriores. |

## Decisiones de privacidad y seguridad

- La biblioteca, favoritos, playlists y ajustes permanecen en almacenamiento local; no hay servidores propios, cuentas ni telemetría. La consulta de carátulas envía el texto buscado al proveedor iTunes y solicita imágenes por HTTPS. El proveedor conoce datos técnicos de conexión.
- Las canciones no se descargan de Spotify. En esta versión no se solicita autorización Spotify ni se consume su API. El usuario debe proporcionar un archivo de lista permitido por su fuente. La política de Spotify permite determinados casos de transferencia de metadatos de playlists, pero tiene restricciones de integración y requisitos de Development Mode; la autorización PKCE y revisión de cumplimiento se dejan para una versión posterior.
- Solo se elimina la copia de Promeia después de confirmar; no hay permiso general de multimedia. Los metadatos se superponen en Room, manteniendo los archivos intactos.
- Las copias JSON contienen datos de biblioteca, playlists, favoritos y ajustes; el usuario elige su ubicación. No publicar estos archivos en GitHub.

## Referencias técnicas

- https://developer.android.com/media/implement/playback-app
- https://developer.android.com/reference/android/provider/MediaStore
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow
- https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide
- https://developer.spotify.com/policy

## Actualización 0.3.0
- Live Lyrics: código de lectura LRC/TXT, búsqueda opcional LRCLIB, resaltado y desplazamiento sincronizados, ajuste temporal y salto a línea. Traducción automática ML Kit por línea y selección de idioma. Cobertura limitada a letras existentes y modelos compatibles; no transcripción de voz. Pruebas puras del analizador, integración Android pendiente.
- Discover: búsqueda mediante Jamendo con Client ID configurable, descarga MP3 autorizada, comprobación previa del permiso, almacenamiento privado y registro en Room, reproducción sin conexión y eliminación. Sin Y2Mate/YouTube/Spotify audio. No se garantiza encontrar todas las canciones; descarga en primer plano, sin cola persistente. Integración real pendiente de Client ID y Android.

## Actualización 0.4.0
- Download Center sustituye la búsqueda de Jamendo por YouTube Data API (clave opcional, resultados internos y apertura del video en YouTube) y descarga independiente por enlace HTTPS directo autorizado. No convierte páginas ni videos. Importación SAF desde el mismo apartado. Validación de audio, metadatos, progreso, cancelación, límite 250 MB, registro Room y reproducción local. No garantiza todas las canciones. Las funciones de letras y traducción permanecen. Compilación Android e integración en dispositivo pendientes.

## v0.5.0
Biblioteca de inclusión manual exclusivamente. La migración desde v0.4 puede requerir reimportar archivos MediaStore. Animaciones visuales y diseño limpio, no crossfade de audio. Letras y Download Center de v0.4 se conservan.
