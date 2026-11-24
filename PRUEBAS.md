# Plan de validación

Estado al entregar: sin SDK/Gradle disponible en el entorno de generación. No se ha ejecutado CI ni se ha instalado el APK. La revisión de sintaxis no sustituye a la compilación de Gradle.

## Automatizadas
GitHub Actions ejecuta `:app:testDebugUnitTest` y `:app:assembleDebug`. Pruebas unitarias incluyen coincidencia normalizada, ambigüedad, parsing M3U/TSV, metadatos y duración.

## Manuales (Android 8, 11, 13, 15; al menos un teléfono real)

1. Instalar desde APK de CI. Abrir sin permisos; comprobar que no se solicita acceso general a multimedia. Importar un MP3, FLAC, M4A, WAV, OGG y OPUS disponibles legalmente. Verificar archivos inválidos.
2. Reproducir, buscar, avanzar, repetir, aleatorio, cola; apagar pantalla y comprobar notificación, Bluetooth y auriculares. Verificar que una llamada o desconexión de auriculares no cause audio inesperado. Verificar retorno de cola y posición tras cerrar.
3. Crear dos playlists, agregar una canción con el único botón, modificar, duplicar, reordenar y exportar. Importar una lista con faltantes y una coincidencia ambigua. Verificar que no se descarga audio.
4. Probar carátula embebida, local e Internet (incluida ausencia de red), aplicar a álbum, cerrar y volver a abrir, restaurar imagen. Verificar almacenamiento.
5. Editar título, artista y género; comprobar cambios visibles y archivo original sin alterar. Editar género en lote. Ocultar y restaurar canciones. Excluir carpetas.
6. Probar filtros y estadísticas escuchando al menos 10 segundos. Verificar Sleep Mode tras 5 minutos y ecualizador en dispositivo compatible.
7. Hacer copia de seguridad, modificar playlist, restaurar y comprobar ajustes y carátulas. Probar copia de archivo con SAF. Probar eliminación solo sobre una copia descartable y cancelar el diálogo de Promeia.
8. Probar 5.000+ canciones, rotación, memoria y rendimiento; medir pérdida de estadísticas por cierre forzado. Verificar TalkBack, tamaños grandes de texto, tema claro/oscuro y dispositivos de pantalla pequeña.

## Criterio de aceptación
CI verde, pruebas en teléfono, no pérdida de archivos ni reproducción no solicitada. Registrar errores de compilación/dispositivo y corregir antes de una release firmada.

## Live Lyrics (0.3.0)
1. Reproducir una pista y abrir «Letras y traducción». Importar `ejemplo-letra.lrc`; verificar resaltado, desplazamiento, salto a una línea y ajuste de tiempo.
2. Cerrar y abrir; verificar persistencia del archivo. Cambiar canción; verificar que no se muestra la letra anterior. Importar TXT; verificar modo no sincronizado.
3. Activar búsqueda online, probar una pista con coincidencia, una instrumental, una inexistente y modo avión; comprobar mensajes de error. Activar traducción a español desde otro idioma y descargar modelo; probar después sin conexión. La traducción puede no ser literal o exacta.

## Discover (0.3.0)
1. Abrir icono nube, introducir Client ID real, buscar un artista de Jamendo. Verificar resultados, licencia y ausencia de botón de descarga si el artista no permite descargar.
2. Descargar una canción autorizada, verificar progreso, reproducción en Discover y Biblioteca > Descargadas. Cerrar y reabrir y activar modo avión para verificar persistencia y reproducción local. Probar favoritos y playlist.
3. Repetir búsqueda: la canción ya guardada debe ofrecer reproducción, no una copia duplicada. Cancelar otra descarga y verificar que no aparece una entrada incompleta. Probar conexión perdida y Client ID inválido.
4. Copiar/compartir archivo si la licencia lo permite y eliminarlo desde la biblioteca. Comprobar que ya no figura en Descargadas. Confirmar que borrar caché no elimina la música.
5. Compilar y ejecutar CI. No se han ejecutado estas pruebas de integración Android en el entorno de generación.

## Download Center (0.4.0, sustituye las pruebas de Jamendo)
1. Buscar sin clave: abre YouTube. Con clave válida: comprobar resultados y apertura del video. Probar clave inválida, cuota agotada y ausencia de conexión.
2. Usar un HTTPS directo autorizado para MP3 y FLAC; probar descarga, metadatos, modo avión, playlist, letras y reapertura. Repetir el mismo enlace y comprobar que no duplica la entrada.
3. Probar HTML, enlaces YouTube, redirección a dominio bloqueado, archivo corrupto, video, tamaño superior a 250 MB y cancelación. No debe quedar audio inválido.
4. Importar un archivo desde el selector y comprobar biblioteca. Copiar y eliminar una descarga descartable.
5. Actualizar sobre v0.3.0 sin desinstalar con igual firma, verificar descargas anteriores y datos Room. No se ha ejecutado la prueba de dispositivo.

## Privacidad, copia y animaciones (0.5.0)
1. Instalar limpio con audios, grabaciones y videos en el teléfono. La biblioteca debe iniciar vacía y no solicitar permiso general.
2. Seleccionar dos canciones; verificar que solo esas aparezcan, que los metadatos y carátulas se conserven tras reiniciar y que borrar/mover un original no afecte la copia.
3. Reimportar el mismo archivo con otro nombre: comprobar deduplicación SHA-256. Seleccionar una carpeta: comprobar solo los audios de esa carpeta y subcarpetas.
4. Cambiar carátula/metadatos, exportar carpeta ZIP, descomprimir y revisar Audio, Covers, Metadata, Lyrics y library-backup.json. Eliminar una copia y comprobar que el original permanece.
5. Probar cambios entre inicio/biblioteca/playlists/reproductor, mini reproductor, animación de carátula y play/pausa, botón Atrás, ordenación plegable, tema claro/oscuro y escala de fuente grande.
6. Actualizar una instalación 0.4 con firma compatible: verificar migración, ausencia de escaneo y reimportar canciones que procedían de MediaStore.
