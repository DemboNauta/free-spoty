package com.freespoty.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.freespoty.app.data.db.dao.PlaylistDao
import com.freespoty.app.data.db.dao.TrackDao
import com.freespoty.app.data.db.entities.Playlist
import com.freespoty.app.data.db.entities.PlaylistTrackCrossRef
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.db.entities.TrackSource

class Converters {
    @TypeConverter fun sourceToString(s: TrackSource): String = s.name
    @TypeConverter fun stringToSource(s: String): TrackSource = TrackSource.valueOf(s)
}

@Database(
    entities = [Track::class, Playlist::class, PlaylistTrackCrossRef::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
}
