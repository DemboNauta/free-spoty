# FreeSpoty — Notas para Claude

App Android (Kotlin + Jetpack Compose) tipo Spotify libre: importa playlists públicas de Spotify/YouTube, reproduce vía streaming desde YouTube (NewPipeExtractor) y descarga offline.

## Build environment

Críticos, no obvios:

- **JDK**: usar `C:\Program Files\Android\Android Studio\jbr` (JBR 21). El JDK 26 del sistema rompe Gradle 8.7. Set antes de cualquier `./gradlew`:
  ```bash
  export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
  ```
- **Android SDK**: `C:\Users\edgar\AppData\Local\Android\Sdk`
  ```bash
  export ANDROID_HOME="C:/Users/edgar/AppData/Local/Android/Sdk"
  ```
- **adb**: no está en PATH. Ruta absoluta: `C:/Users/edgar/AppData/Local/Android/Sdk/platform-tools/adb.exe`

## Comandos compilación

Build limpio (necesario tras cambios KSP-relevantes — el cache de Gradle resucita un bug de duplicate `*_Impl.java`):
```bash
./gradlew assembleDebug --no-daemon --no-configuration-cache --no-build-cache
```

Build incremental normal:
```bash
./gradlew assembleDebug --no-daemon --no-configuration-cache
```

APK queda en `app/build/outputs/apk/debug/app-debug.apk`. Copiado a `FreeSpoty-debug.apk` en root para fácil acceso.

## Instalación en móvil (Xiaomi/MIUI Edgar)

- ADB device: `604272a8` (Xiaomi, MIUI).
- MIUI exige toggle **"Instalar vía USB"** + **"Depuración USB (Ajustes de seguridad)"** en Opciones de desarrollador (la segunda pide SIM activa + Mi Account). Al instalar saldrá popup MIUI pidiendo confirmación — hay que aceptar en pantalla.
- `applicationId` debug: `com.freespoty.app.debug` (sufijo `.debug`).
- Comando:
  ```bash
  "C:/Users/edgar/AppData/Local/Android/Sdk/platform-tools/adb.exe" install -r FreeSpoty-debug.apk
  ```

## Versiones clave (gradle/libs.versions.toml)

- AGP 8.5.0, Kotlin 2.0.0, KSP 2.0.0-1.0.21
- compose BOM 2024.09.02 — **NO bajar a 2024.06**: hay bug `LocalLifecycleOwner not present` (crash al arrancar) que requiere lifecycle 2.8.4 + activity-compose 1.9.2 + BOM ≥ 2024.09.
- NewPipeExtractor `v0.26.1` (jitpack `com.github.TeamNewPipe:NewPipeExtractor`). Versiones < 0.24.6 dan `Could not get ytInitialData`.
- Media3 1.4.1, Room 2.6.1. 1.4+ aporta `ExoPlayer.PreloadConfiguration` (usado en `PlayerService` con 30s target) → pre-buffer agresivo del siguiente item → transición gapless.

## Trampas conocidas YouTube / NewPipeExtractor

1. **Muro de consentimiento EU**: España → YouTube redirige a `consent.youtube.com` y el HTML resultante no tiene `ytInitialData`. Fix en `app/src/main/java/com/freespoty/app/network/NewPipeDownloader.kt`: cookie `SOCS=...` añadida automáticamente en cada petición.
2. **Bot block (`Sign in to confirm that you're not a bot`)**: salta si haces muchos `resolveStream` en paralelo. Por eso:
   - Importer **no** auto-descarga al importar.
   - `DownloadManager.enqueue` encola en cola única `freespoty-download-queue` con `APPEND_OR_REPLACE` → workers serializados, 1 a 1.
   - `PlayerController.playTracks` resuelve **secuencial**: primer batch de 3 antes de play (garantiza siguiente en timeline aunque tracks sean cortos), resto en background uno a uno. Nunca lista entera en paralelo.
   - Si te bloquean: la IP se desbloquea sola en minutos/horas. Cambiar red/VPN acelera.
3. **NewPipe `ensureInitialized()`**: cada método público de `YouTubeSource` debe llamarlo (search, resolveStream, fetchPlaylist). Si no, primer uso de NewPipe sin init → crash silencioso.

## Strings.xml

- `name="import"` es palabra reservada Java → no usar. Renombrado a `action_import`.

## Arquitectura rápida

- **DI**: `AppContainer` (manual, sin Hilt). `FreeSpotyApp.container` accesible global.
- **DB**: Room. Tablas `tracks`, `playlists`, `playlist_tracks`, `downloads`. KSP genera DAOs.
- **Reproducción**: Media3 `MediaController` ↔ `PlayerService` (foreground media session). `PlayerController` envuelve para Compose.
- **Resolución stream**: REMOTE → al play, `MusicRepository.resolvePlayableUri` → `YouTubeSource.resolveStream` → audio URL. DOWNLOADED → archivo local. LOCAL → archivo local.
- **Descarga offline**: `DownloadWorker` (CoroutineWorker WorkManager). Cola serial. Al terminar marca `Track.source = DOWNLOADED` + `Track.uri = file://...` → reproducciones futuras saltan a local.
- **Importer**: `PlaylistImporter` con `appScope: CoroutineScope` (SupervisorJob + IO) inyectado. Spotify scraping via embed page __NEXT_DATA__ JSON. YouTube playlist via NewPipe.
- **Recomendaciones**: `RecommendationEngine` busca por artista en YT. `PlayerController.maybeAutoQueue` añade similares cuando quedan pocas en cola.

## Descarga (UX actual)

- **Sin auto-descarga**. Decisión usuario: streaming realtime por defecto, descargar solo lo que el usuario pida.
- Descarga manual: botón download en cada `TrackItem` + botón "Descargar todas" (icono ↓ en TopAppBar de detalle playlist).
- Worker descarga en background serializado. Cuando completa: `Track.source = DOWNLOADED`, icono `CloudDone` ✓.
- Mini-player visible en todas las pantallas excepto la del player expandido (`Routes.PLAYER`).

## Reproductor (gapless)

- `PlayerService` configura `pauseAtEndOfMediaItems=false` + `PreloadConfiguration(30s)` → pre-buffer agresivo del siguiente item.
- `PlayerController.playTracks` resuelve current + next 2 antes del play, resto secuencial en bg, `addMediaItem` en orden (siguientes append, previos insert al inicio).
- `onPlayerError`: si un item falla (URL stale, bot block, 403) → `seekToNextMediaItem + play`. No pausa.
- `rescueIfEnded`: si current acabó sin siguiente en timeline y luego llega via bg, arranca el nuevo manualmente (ExoPlayer no rearanca solo desde STATE_ENDED).
- **NO** manejar STATE_ENDED en `onPlaybackStateChanged` con seekToNext: dispara doble-skip en transiciones naturales → notification queda desincronizada con player real.

## Indicadores descarga (TrackItem)

- `CloudDone` (✓ azul) = COMPLETED
- Ring progreso o `Downloading` = RUNNING
- `HourglassEmpty` = QUEUED
- `CloudOff` (tachada roja) = FAILED
- Sin icono = sin entrada DownloadEntry (track REMOTE puro nunca tocado)

## Debug / logs útiles

Filtrar por tag:
```bash
adb logcat -s NewPipeDownloader:* PlaylistImporter:* DownloadWorker:*
```

Crash al arrancar:
```bash
adb logcat -d AndroidRuntime:E "*:S" | head -80
```
