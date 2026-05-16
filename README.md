# FreeSpoty

App Android para reproducir música localmente y, en próximas fases, descargar audio
desde YouTube (vía NewPipeExtractor) e importar playlists desde URLs públicas de
Spotify y YouTube. Pensada para uso personal.

> Esta app extrae contenido de servicios de terceros con sus propios términos. Su uso
> queda bajo responsabilidad de quien la compila y ejecuta.

## Estado: Fase 1 (MVP)

Lo que ya funciona:

- Estructura Android moderna: Kotlin + Jetpack Compose + Material 3.
- Reproductor con `androidx.media3` (`ExoPlayer` + `MediaSession`).
  - **Controles en pantalla de bloqueo y notificación** (igual que Spotify), gracias a
    `MediaSessionService`. Play / pause / siguiente / anterior, metadata, artwork.
  - Manejo de audio focus y "becoming noisy" (auriculares desconectados → pausa).
- Escaneo de música local del dispositivo vía `MediaStore`.
- Base de datos local con Room: tracks, playlists y relación tracks↔playlists.
- Pantallas:
  - **Inicio**: lista de canciones locales, tap para reproducir.
  - **Playlists**: crear playlists; abrir detalle y reproducir.
  - **Reproductor a pantalla completa** con slider y controles grandes.
  - **Mini-player** persistente en la parte inferior.
  - Buscar / Descargas (placeholders).
- Permisos: solicita `READ_MEDIA_AUDIO` (Android 13+) y `POST_NOTIFICATIONS`.

## Próximas fases

### Fase 2 — Streaming y búsqueda
- Búsqueda en YouTube usando `NewPipeExtractor`.
- Reproducción por streaming de los resultados (sin descarga aún).
- Importación de playlists:
  - URL pública de Spotify → scraping de metadatos (nombre/artista) → match en YouTube.
  - URL de playlist de YouTube → tracks directos.

### Fase 3 — Descargas offline
- Descargar audio del stream de YouTube a almacenamiento privado de la app.
- Listado de descargas, gestión de espacio.
- Reproducción offline transparente cuando la pista ya está descargada.

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
├── FreeSpotyApp.kt          # Application con AppContainer (DI manual)
├── MainActivity.kt          # Solicita permisos y monta Compose
├── di/AppContainer.kt       # Singletons: DB, Repository, PlayerController
├── data/
│   ├── db/                  # Room: entities, DAOs, AppDatabase
│   ├── scanner/             # LocalMusicScanner (MediaStore)
│   └── repository/          # MusicRepository
├── player/
│   ├── PlayerService.kt     # MediaSessionService (notif + lockscreen)
│   └── PlayerController.kt  # MediaController wrapper para UI
└── ui/
    ├── theme/               # Compose theme (Material 3, dark green)
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
