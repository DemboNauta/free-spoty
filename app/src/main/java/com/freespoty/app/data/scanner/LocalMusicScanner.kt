package com.freespoty.app.data.scanner

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.db.entities.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMusicScanner(private val context: Context) {

    suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.IS_MUSIC
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.DURATION} > 10000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (c.moveToNext()) {
                val mediaId = c.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(collection, mediaId)
                val albumId = c.getLong(albumIdCol)
                val artUri = ContentUris.withAppendedId(
                    android.net.Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
                tracks += Track(
                    id = "local-$mediaId",
                    title = c.getString(titleCol) ?: "Sin título",
                    artist = c.getString(artistCol),
                    album = c.getString(albumCol),
                    durationMs = c.getLong(durationCol),
                    uri = contentUri.toString(),
                    artworkUri = artUri.toString(),
                    source = TrackSource.LOCAL
                )
            }
        }
        tracks
    }
}
