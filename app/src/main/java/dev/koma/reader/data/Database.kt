package dev.koma.reader.data

import androidx.room.*
import dev.koma.reader.core.*
import kotlinx.coroutines.flow.Flow

@Dao interface LibraryDao {
    @Query("SELECT * FROM works") fun works(): Flow<List<Work>>
    @Query("SELECT * FROM volumes ORDER BY `order`, title") fun volumes(): Flow<List<Volume>>
    @Query("SELECT * FROM bookmarks ORDER BY created DESC") fun bookmarks(): Flow<List<Bookmark>>
    @Query("SELECT * FROM history ORDER BY opened DESC") fun history(): Flow<List<History>>
    @Query("SELECT * FROM works") suspend fun allWorks(): List<Work>
    @Query("SELECT * FROM volumes") suspend fun allVolumes(): List<Volume>
    @Query("SELECT * FROM bookmarks") suspend fun allBookmarks(): List<Bookmark>
    @Query("SELECT * FROM volumes WHERE hash=:hash") suspend fun volume(hash: String): Volume?
    @Query("SELECT * FROM works WHERE id=:id") suspend fun work(id: String): Work?
    @Upsert suspend fun put(work: Work)
    @Upsert suspend fun put(volume: Volume)
    @Upsert suspend fun put(bookmark: Bookmark)
    @Upsert suspend fun put(history: History)
    @Query("UPDATE volumes SET page=:page, offset=:offset WHERE hash=:hash") suspend fun position(hash: String, page: Int, offset: Float)
    @Query("DELETE FROM bookmarks WHERE hash=:hash AND page=:page") suspend fun removeBookmark(hash: String, page: Int)
    @Query("DELETE FROM bookmarks WHERE hash=:hash") suspend fun removeBookmarks(hash: String)
    @Query("DELETE FROM history WHERE hash=:hash") suspend fun removeHistory(hash: String)
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("DELETE FROM volumes WHERE hash=:hash") suspend fun removeVolume(hash: String)
    @Query("DELETE FROM works WHERE id=:id AND NOT EXISTS(SELECT 1 FROM volumes WHERE workId=:id)") suspend fun removeEmptyWork(id: String)
}
@Database(entities=[Work::class, Volume::class, Bookmark::class, History::class], version=1, exportSchema=true)
abstract class KomaDatabase: RoomDatabase() { abstract fun library(): LibraryDao }
