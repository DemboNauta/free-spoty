# FreeSpoty

App Android para reproducir música localmente y, en próximas fases, descargar audio
desde YouTube (vía NewPipeExtractor) e importar playlists desde URLs públicas de
Spotify y YouTube. Pensada para uso personal.

> Esta app extrae contenido de servicios de terceros con sus propios términos. Su uso
> queda bajo responsabilidad de quien la compila y ejecuta.

## Estado

### Fase 1 — MVP local ✅
- Estructura Android moderna: Kotlin + Jetpack Compose + Material 3.
- Reproductor con `androidx.media3` (`ExoPlayer` + `MediaSession`).
  - **Controles en pantalla de bloqueo y notificación** (igual que Spotify), gracias a
    `MediaSessionService`.
  - Audio focus + auto-pausa al desconectar auriculares.
- Escaneo de música local vía `MediaStore`.
- Base de datos local con Room.
- UI: Inicio, Playlists (crear / detalle), Reproductor full screen, mini-player.

### Fase 2 — Streaming + importación ✅
- Búsqueda en YouTube vía `NewPipeExtractor` (sin API key, sin backend).
- Reproducción por **streaming** directo en ExoPlayer; la URL de stream se resuelve
  on-demand cuando una pista remota entra a la cola.
- **Importación de playlists desde URL pública**:
  - **Spotify**: la URL pública se traduce al endpoint `embed/playlist/{id}`, se parsea
    el JSON SSR (`__NEXT_DATA__`) para obtener los tracks (título + artista), y cada
    uno se empareja con su mejor resultado de YouTube. Sin OAuth.
  - **YouTube**: las playlists se leen directamente con NewPipeExtractor.
- "Añadir a playlist" desde resultados de búsqueda.

### Fase 3 — Descargas offline ✅
- Descarga vía `WorkManager` + OkHttp a `filesDir/downloads/{trackId}.m4a`.
- Tabla `downloads` con progreso, estado (QUEUED/RUNNING/COMPLETED/FAILED) y errores.
- Al completarse la descarga, la pista se marca `DOWNLOADED` y el `MusicRepository` la
  reproduce desde el archivo local automáticamente — sin lógica especial en la UI.
- Pantalla **Descargas** con progreso en vivo, cancelar, eliminar.
- Botón de descarga en pantalla del reproductor y junto a cada resultado de búsqueda.

## Cómo compilar

Requisitos:

- Android Studio Iguana o superior.
- JDK 17.
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
├── network/                 # NewPipeDownloader (OkHttp)
├── data/
│   ├── db/                  # Room: tracks, playlists, downloads
│   ├── scanner/             # LocalMusicScanner (MediaStore)
│   ├── source/              # YouTubeSource (search/stream/playlist)
│   ├── importer/            # PlaylistImporter + SpotifyPlaylistScraper
│   ├── download/            # DownloadManager + DownloadWorker
│   └── repository/          # MusicRepository
├── player/
│   ├── PlayerService.kt     # MediaSessionService (notif + lockscreen)
│   └── PlayerController.kt  # MediaController + stream resolution
└── ui/
    ├── theme/               # Compose theme (dark green)
    ├── components/          # MiniPlayer, TrackItem
    ├── navigation/          # NavHost + bottom nav
    └── screens/             # home, playlists, player, search, downloads
```

## Por qué Media3 y MediaSession

El sistema Android observa las `MediaSession` activas y automáticamente:

- Muestra controles en la pantalla bloqueada.
- Publica la notificación de reproducción.
- Maneja botones Bluetooth, Android Auto y Google Assistant.

Por eso `PlayerService` extiende `MediaSessionService` y la UI nunca habla con `ExoPlayer`
directamente, sino con un `MediaController` que vive en el proceso de la app y se
comunica con el servicio.
