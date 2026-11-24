# PROMEIA MUSIC

**Versión 0.5.0 — prototipo Android nativo / código fuente**

Reproductor de música local con identidad lavanda y menta. Descarga archivos de audio autorizados desde enlaces HTTPS directos y busca videos mediante YouTube Data API, no extrae audio de servicios de streaming, no exige cuenta y funciona sin conexión para la biblioteca local; las consultas en línea y la primera descarga de modelos requieren Internet. Interfaz en español. Android 8.0+ (API 26), compilación y target 35, Java 17. No contiene música, credenciales ni APK compilado.

## Obtener el APK con GitHub (sin Android Studio)

1. Descomprime el ZIP. Dentro encontrarás `app`, `.github`, `docs`, `settings.gradle.kts` y demás archivos. Deben quedar en la raíz del repositorio, **incluyendo la carpeta oculta `.github`**.
2. Crea un repositorio vacío en https://github.com/new. Para subir todos los archivos (incluidos los ocultos), la opción más fiable es Git en una terminal dentro de la carpeta descomprimida:

```sh
git init -b main
git add .
git commit -m "Initial Promeia Music project"
git remote add origin https://github.com/TU_USUARIO/TU_REPOSITORIO.git
git push -u origin main
```

3. En GitHub, abre **Actions > Android CI**. Se activa en push o puedes seleccionar **Run workflow**.
4. Si la ejecución termina correctamente, abre esa ejecución y descarga el artifact **Promeia-Music-debug**. GitHub entrega un ZIP con `app-debug.apk`.
5. Descomprime el artifact, transfiere el APK al Android y autoriza la instalación desde la aplicación que lo abre. Es un APK de desarrollo; no es una publicación firmada para Google Play.

El flujo instala Java 17, SDK 35 y Gradle 8.9, ejecuta pruebas unitarias y `assembleDebug`. No se incluyen los binarios del Gradle Wrapper porque no pudieron obtenerse en el entorno de generación; el CI configura Gradle directamente. Para Android Studio, abre la carpeta raíz y selecciona una instalación local de Gradle 8.9 (o genera el wrapper con `gradle wrapper --gradle-version 8.9` teniendo Gradle instalado). Compilación local: `gradle :app:testDebugUnitTest :app:assembleDebug`. El APK queda en `app/build/outputs/apk/debug/`.

## Uso

- La biblioteca empieza vacía. Usa «Importar canciones» o «Carpeta» para seleccionar solo el audio deseado. No se solicita permiso general de multimedia ni se escanea MediaStore. Se crea una copia independiente.
- Cada canción tiene una tarjeta plegable. Al desplegarla, la misma tarjeta reúne la carátula, el título, el artista, el álbum, la duración y los controles de reproducción y opciones básicas.
- Las opciones de canción se limitan a agregar a una playlist o a la cola, marcar como favorita, compartir, retirar la copia de Promeia o exportar el audio. Ya no se muestra una ficha técnica, estadísticas de escucha ni un editor avanzado de metadatos.
- Las carátulas incrustadas en los archivos se muestran automáticamente. Las portadas de playlists pueden elegirse desde una imagen local; no hay búsqueda en Internet ni edición avanzada de carátulas de canciones.
- Music Match admite M3U extendido y TSV de título/artista exportados por el usuario. Muestra coincidencias y faltantes antes de crear una playlist local. No accede a una cuenta Spotify ni descarga canciones. Ejemplo TSV en `docs/ejemplo-playlist.tsv`.
- La copia JSON conserva los datos básicos de la biblioteca, playlists, favoritos, portadas de playlists y ajustes; no contiene audio. La restauración se realiza por URI y está orientada principalmente al mismo dispositivo.

## Arquitectura

```
app/src/main/java/com/promeia/music/
  MainActivity.kt           conexión al MediaController y migración
  data/Library.kt           Room, importación selectiva SAF, cancelación de escaneos antiguos
  data/MusicStorage.kt      copias privadas y SHA-256
  data/Tools.kt             importación de playlists, coincidencias y backup
  playback/PlaybackService.kt   ExoPlayer, MediaSession y cola persistente
  ui/Promeia.kt             pantallas Compose y controles
```

Consulta `docs/ALCANCE.md` para la matriz de funciones y limitaciones, y `docs/PRUEBAS.md` para la validación manual. El código fue generado y revisado estáticamente, pero no compilado con Android SDK ni ejecutado en un teléfono en el entorno de entrega. Se debe verificar CI, permisos, audio, almacenamiento y compatibilidad real antes de distribuirlo. No se afirma que todas las características del documento maestro estén terminadas.

## Live Lyrics y Download Center

**Promeia Live Lyrics**: abre el reproductor completo y pulsa «Letras y traducción». Puedes importar un archivo LRC/TXT propio o activar la consulta en LRCLIB (envía título, artista, álbum y duración, no audio). La línea activa se actualiza según la posición de reproducción, permite saltar a una línea y ajustar el desfase. La traducción usa Google ML Kit en el teléfono; al elegir idioma descarga el modelo si hace falta. No traduce la voz directamente, no garantiza letras para todo el catálogo y no genera sincronización si solo existe texto plano. Las letras importadas permanecen en el almacenamiento privado; las remotas se consultan de nuevo. Comprueba los derechos y condiciones del proveedor antes de distribuir comercialmente la integración. No se incluyen letras comerciales en este proyecto.

**Promeia Download Center**: abre el icono de nube. La búsqueda en YouTube muestra hasta 20 videos dentro de Promeia con una clave de YouTube Data API v3 (habilita la API en https://console.cloud.google.com/ y configura una clave restringida a esa API; no la subas al repositorio). Los resultados se abren en YouTube, no se descargan. Sin clave, el botón de búsqueda abre YouTube fuera de Promeia. Las consultas se envían a Google. No hay extracción de audio de videos, Y2Mate ni conversión de páginas web.

Para descargar, pega un enlace HTTPS directo a un archivo musical para el que tienes autorización y marca la confirmación. Compatible con MP3, FLAC, WAV, AAC, M4A, OGG, OPUS, WMA y AIFF según el dispositivo y el tipo de contenido servido. Se valida el audio con MediaMetadataRetriever, se limita a 250 MB, se informa el progreso y se eliminan archivos temporales. Se conserva en almacenamiento privado y aparece en Biblioteca > Descargadas; se puede reproducir sin conexión, copiar, compartir, eliminar y agregar a playlists. Las descargas permanecen al cerrar la app pero se pierden al desinstalarla; el backup JSON no contiene audio. Mantén abierta la pantalla durante la descarga. Las descargas anteriores accesibles se intentan migrar a la carpeta privada al actualizar con la misma firma. No existe una fuente universal de descargas autorizadas para todas las canciones comerciales. También puedes importar archivos comprados o propios usando el selector de Android.

La clave de API se guarda en preferencias privadas, no se incluye en el código ni en el backup. Para producción, revisa las restricciones de claves, cuotas, políticas, privacidad y licencias. La búsqueda y la descarga son operaciones independientes. No se han validado contra servicios reales ni en Android en este entorno.

Referencias: https://developers.google.com/youtube/data/reference/rest/v3/search/list ; https://developers.google.com/youtube/terms/developer-policies ; https://developers.google.com/ml-kit/language/translation/android ; https://lrclib.net/docs

## Novedades 0.5.0 — biblioteca privada e interfaz limpia

- Pantalla de inicio con colección personal, botón de importación y tarjetas de canciones recientes. Transiciones de pantalla y carátula, cambio animado de acento y play/pausa, mini reproductor e indicador de importación con entrada/salida suave. Ordenación plegable y navegación Atrás. Tema oscuro, claro o sistema.
- Android copia únicamente archivos de audio seleccionados o los contenidos de una carpeta elegida expresamente. El audio se valida con MediaMetadataRetriever; SHA-256 evita copias idénticas. No se modifica ni elimina el original. El selector de carpetas incluye sus subcarpetas.
- Estructura en almacenamiento privado de la app (relativa a `filesDir`):

```
Promeia Music/Music/
  Audio/       archivos originales copiados, identificados por SHA-256
  Covers/      carátulas originales y personalizadas
  Metadata/    JSON por pista con campos originales y editados
  Lyrics/      letras importadas LRC/TXT
```

Room conserva playlists, favoritos, historial y demás información. Ajustes > Exportar carpeta completa (ZIP) permite guardar una copia de los audios y datos en una ubicación visible elegida por el usuario; incluye `Metadata/library-backup.json`. El ZIP es una exportación, no hay restauración integral automática del ZIP. La restauración JSON anterior sigue disponible y está orientada a la misma biblioteca. Las traducciones se recalculan; las letras obtenidas por Internet no se guardan automáticamente.

La carpeta privada no es una carpeta pública de la raíz del teléfono. Se conserva al cerrar la app o al actualizar con firma compatible; se elimina al desinstalar o borrar datos. Exporta antes de hacerlo.

**Actualización desde 0.4:** la migración elimina del índice los registros antiguos `content://media/` porque la versión previa no distinguía escaneo de selección explícita. Esto puede incluir audios seleccionados anteriormente; sus archivos originales no se borran, pero será necesario importarlos nuevamente y reconstruir las referencias de sus playlists. Se intenta copiar las demás importaciones y descargas anteriores accesibles. Exporta tus datos antes de actualizar.
