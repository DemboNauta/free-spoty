package com.freespoty.app.data.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

data class SpotifyTrackMeta(val title: String, val artist: String?)
data class SpotifyPlaylistMeta(val name: String, val tracks: List<SpotifyTrackMeta>)

/**
 * Reads a public Spotify playlist without using the Spotify API. Spotify's embed page
 * (`open.spotify.com/embed/playlist/{id}`) ships server-side rendered JSON inside a
 * `<script id="__NEXT_DATA__">` tag that lists the tracks. The embed page is publicly
 * accessible without auth as long as the playlist itself is public.
 */
class SpotifyPlaylistScraper {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(url: String): SpotifyPlaylistMeta = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(url)
            ?: throw IllegalArgumentException("URL de Spotify no reconocida")
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Spotify devolvió HTTP ${response.code}")
            }
            val html = response.body?.string().orEmpty()
            parseEmbedHtml(html)
        }
    }

    private fun parseEmbedHtml(html: String): SpotifyPlaylistMeta {
        val doc = Jsoup.parse(html)
        val script = doc.selectFirst("script#__NEXT_DATA__")
            ?: error("Spotify cambió su markup; no se pudo leer la playlist.")
        val root = JSONObject(script.data())
        val entity = root.optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("state")
            ?.optJSONObject("data")
            ?.optJSONObject("entity")
            ?: error("No se encontró la entidad de playlist en la respuesta.")

        val name = entity.optString("title").ifBlank { entity.optString("name", "Playlist Spotify") }
        val trackList: JSONArray = entity.optJSONArray("trackList") ?: JSONArray()

        val tracks = (0 until trackList.length()).mapNotNull { i ->
            val t = trackList.optJSONObject(i) ?: return@mapNotNull null
            val title = t.optString("title").ifBlank { return@mapNotNull null }
            val subtitle = t.optString("subtitle").takeIf { it.isNotBlank() }
            SpotifyTrackMeta(title = title, artist = subtitle)
        }

        return SpotifyPlaylistMeta(name = name, tracks = tracks)
    }

    private fun extractPlaylistId(url: String): String? {
        // Supports:
        //   https://open.spotify.com/playlist/{id}
        //   https://open.spotify.com/playlist/{id}?si=...
        //   https://open.spotify.com/embed/playlist/{id}
        //   spotify:playlist:{id}
        val regex = Regex("(?:playlist[/:])([A-Za-z0-9]{16,})")
        return regex.find(url)?.groupValues?.get(1)
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Mobile Safari/537.36"
    }
}
