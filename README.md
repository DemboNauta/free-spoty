# FreeSpoty

App Android (Kotlin + Jetpack Compose) tipo Spotify libre: importa playlists públicas
de Spotify/YouTube, reproduce por streaming desde YouTube (NewPipeExtractor), descarga
para escucha offline y genera recomendaciones a partir de cualquier canción. Pensada
para uso personal.

> Esta app extrae contenido de servicios de terceros con sus propios términos. Su uso
> queda bajo responsabilidad de quien la compila y ejecuta.

## Funcionalidades

### Reproducción
- Reproductor con `androidx.media3` (`ExoPlayer` + `MediaSession`).
  - **Controles en pantalla de bloqueo y notificación** (igual que Spotify), gracias a
    `MediaSessionService`.
  - Audio focus + auto-pausa al desconectar auriculares.
  - **Transiciones gapless**: pre-buffer agresivo del siguiente tema
    (`PreloadConfiguration`) y arranque inmediato — se resuelve solo la pista actual
    antes de darle al play, el resto en background.
  - Si una pista falla (URL caducada, 403…), salta a la siguiente sin pausar.
- **Modos de bucle**: secuencial, aleatorio y **sugerencias** (al acabar la cola,
  encadena canciones similares automáticamente).
- Mini-player visible en todas las pantallas + reproductor a pantalla completa.

### Streaming + importación
- Búsqueda en YouTube vía `NewPipeExtractor` (sin API key, sin backend).
- Reproducción por **streaming** directo en ExoPlayer; la URL de stream se resuelve
  on-demand cuando una pista remota entra a la cola.
- **Importación de playlists desde URL pública**:
  - **Spotify**: la URL pública se traduce al endpoint `embed/playlist/{id}`, se parsea
    el JSON SSR (`__NEXT_DATA__`) para obtener los tracks (título + artista), y cada
    uno se empareja con su mejor resultado de YouTube. Sin OAuth.
  - **YouTube**: las playlists se leen directamente con NewPipeExtractor.
- "Añadir a playlist" desde resultados de búsqueda.

### Recomendaciones
- **YouTube Mix (radio)**: las canciones similares salen del Mix auto-generado de
  YouTube para la canción semilla (`RD<videoId>`) — el algoritmo real de YouTube, no
  solo "más del mismo artista". Búsqueda por artista como fallback, con tope de 2
  pistas por artista para variedad.
- **Descubre** (Inicio): playlists sugeridas según lo que suena; la primera opción es
  siempre el "Mix: \<canción\>" importable como cualquier playlist.

### Descargas offline
- Descarga vía `WorkManager` + OkHttp a `filesDir/downloads/{trackId}.m4a`, en cola
  serializada (1 a 1) para no disparar el anti-bot de YouTube.
- Tabla `downloads` con progreso, estado (QUEUED/RUNNING/COMPLETED/FAILED) y errores.
- Al completarse, la pista se marca `DOWNLOADED` y se reproduce desde el archivo local
  automáticamente; sigue apareciendo en sus playlists. Si el archivo desaparece, la
  app se auto-repara y vuelve a streaming.
- Sin auto-descarga: streaming por defecto, descarga solo lo que pidas (botón por
  pista o "Descargar todas" en el detalle de playlist).
- Pantalla **Descargas** con progreso en vivo, cancelar, eliminar (al eliminar, la
  pista vuelve a streaming sin salir de las playlists).

### Modo infantil
- **YouTube Restricted Mode** activable en Ajustes, protegido con PIN: filtra el
  contenido de búsqueda/streaming.

## Descargar APK

Cada push a `main` dispara la GitHub Action **Build & Release APK**, que publica el
APK de debug como release (`build-N`) en la página de
[Releases](../../releases). Descarga `app-debug.apk` en el móvil y ábrelo (habilita
«Instalar desde fuentes desconocidas» si hace falta).

## Cómo compilar

Requisitos:

- Android Studio Iguana o superior.
- JDK 17–21 (vale el JBR que trae Android Studio; JDKs muy nuevos rompen Gradle 8.7).
- Android SDK 34, build-tools 34.

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`.

## Arquitectura

```
app/src/main/java/com/freespoty/app/
├── FreeSpotyApp.kt          # Application: AppContainer + init NewPipe
├── MainActivity.kt          # Permisos + Compose root
├── di/AppContainer.kt       # Singletons (DI manual)
├── network/                 # NewPipeDownloader (OkHttp, cookie consentimiento EU)
├── data/
│   ├── db/                  # Room: tracks, playlists, playlist_tracks, downloads
│   ├── scanner/             # LocalMusicScanner (MediaStore)
│   ├── source/              # YouTubeSource (search/stream/playlist/mix)
│   ├── importer/            # PlaylistImporter + SpotifyPlaylistScraper
│   ├── download/            # DownloadManager + DownloadWorker
│   ├── recommendation/      # RecommendationEngine (YouTube Mix + artista)
│   ├── preferences/         # AppPreferences (modo infantil, PIN)
│   └── repository/          # MusicRepository
├── player/
│   ├── PlayerService.kt     # MediaSessionService (notif + lockscreen, preload)
│   └── PlayerController.kt  # MediaController + stream resolution + auto-queue
└── ui/
    ├── theme/               # Compose theme (dark green)
    ├── components/          # MiniPlayer, TrackItem
    ├── navigation/          # NavHost + bottom nav
    └── screens/             # home, playlists, player, search, downloads, settings
```

## Por qué Media3 y MediaSession

El sistema Android observa las `MediaSession` activas y automáticamente:

- Muestra controles en la pantalla bloqueada.
- Publica la notificación de reproducción.
- Maneja botones Bluetooth, Android Auto y Google Assistant.

Por eso `PlayerService` extiende `MediaSessionService` y la UI nunca habla con `ExoPlayer`
directamente, sino con un `MediaController` que vive en el proceso de la app y se
comunica con el servicio.
