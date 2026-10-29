package dev.koma.reader.core

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable @Entity(tableName="works")
data class Work(@PrimaryKey val id: String, val title: String, val status: String = "new", val added: Long = System.currentTimeMillis(), val overrides: String? = null)
@Serializable @Entity(tableName="volumes")
data class Volume(@PrimaryKey val hash: String, val workId: String, val title: String, val fileName: String, val format: String, val pages: Int, val bytes: Long, val order: Int, val page: Int = 0, val offset: Float = 0f, val completed: Boolean = false, val available: Boolean = true, val added: Long = System.currentTimeMillis())
@Serializable @Entity(tableName="bookmarks", primaryKeys=["hash", "page"])
data class Bookmark(val hash: String, val page: Int, val note: String = "", val created: Long = System.currentTimeMillis())
@Serializable @Entity(tableName="history")
data class History(@PrimaryKey val hash: String, val opened: Long = System.currentTimeMillis())
@Serializable
data class ReadingSettings(val mode: String = "rtl", val fitWidth: Boolean = false, val keepAwake: Boolean = true, val volumeKeys: Boolean = false, val brightness: Float = -1f)
@Serializable
data class Overrides(val mode: String? = null, val fitWidth: Boolean? = null, val keepAwake: Boolean? = null, val volumeKeys: Boolean? = null, val brightness: Float? = null) {
    fun resolve(base: ReadingSettings) = ReadingSettings(mode ?: base.mode, fitWidth ?: base.fitWidth, keepAwake ?: base.keepAwake, volumeKeys ?: base.volumeKeys, brightness ?: base.brightness)
}
@Serializable
data class Preferences(val reading: ReadingSettings = ReadingSettings(), val grid: Boolean = true, val sort: String = "recent", val status: String = "all", val format: String = "all")
@Serializable
data class Backup(val schemaVersion: Int = 1, val works: List<Work>, val volumes: List<Volume>, val bookmarks: List<Bookmark>, val preferences: Preferences)

object NaturalOrder : Comparator<String> {
    private val tokens = Regex("[0-9]+|[^0-9]+")
    override fun compare(a: String, b: String): Int {
        val x=tokens.findAll(a.lowercase(Locale.ROOT)).map { it.value }.toList()
        val y=tokens.findAll(b.lowercase(Locale.ROOT)).map { it.value }.toList()
        for (i in 0 until minOf(x.size,y.size)) {
            val p=x[i]; val q=y[i]
            val c=if(p[0].isDigit() && q[0].isDigit()) {
                val pn=p.trimStart('0').ifEmpty { "0" }; val qn=q.trimStart('0').ifEmpty { "0" }
                pn.length.compareTo(qn.length).takeIf { it!=0 } ?: pn.compareTo(qn)
            } else p.compareTo(q)
            if(c!=0) return c
        }
        return x.size.compareTo(y.size).takeIf { it!=0 } ?: a.compareTo(b)
    }
}
fun isPage(path: String): Boolean {
    val parts=path.replace('\\','/').split('/')
    return parts.none { it.startsWith('.') || it=="__MACOSX" || it==".." } && path.substringAfterLast('.').lowercase(Locale.ROOT) in setOf("jpg","jpeg","png","webp")
}
fun suggestedWork(name: String): String = name.substringBeforeLast('.').replace(Regex("(?iu)[ _.-]*(том|vol(?:ume)?|v)[ _.-]*[0-9]+.*$"), "").trim().ifEmpty { name.substringBeforeLast('.') }
fun validateBackup(b: Backup) {
    require(b.schemaVersion==1) { "backup_version" }
    require(b.works.size<=100000 && b.volumes.size<=100000 && b.bookmarks.size<=1000000)
    require(b.works.map { it.id }.distinct().size==b.works.size)
    require(b.volumes.map { it.hash }.distinct().size==b.volumes.size)
    val ids=b.works.map { it.id }.toSet(); val vols=b.volumes.associateBy { it.hash }
    b.works.forEach { require(it.id.isNotBlank() && it.title.isNotBlank() && it.status in setOf("new","reading","done","later")) }
    b.volumes.forEach { require(it.workId in ids && it.hash.matches(Regex("[a-f0-9]{64}")) && it.pages>0 && it.page in 0 until it.pages && it.offset.isFinite() && it.offset in 0f..1f && it.bytes>=0 && it.format in setOf("PDF","CBZ")) }
    b.bookmarks.forEach { require(it.page in 0 until (vols[it.hash]?.pages ?: 0)) }
    b.works.forEach { w-> w.overrides?.let { raw->
        val o=dev.koma.reader.data.codec.decodeFromString<Overrides>(raw)
        require(o.mode==null || o.mode in setOf("rtl","ltr","vertical"))
        require(o.brightness==null || (o.brightness.isFinite() && o.brightness in -1f..1f))
    } }
    require(b.preferences.reading.mode in setOf("rtl","ltr","vertical"))
    require(b.preferences.reading.brightness.isFinite() && b.preferences.reading.brightness in -1f..1f)
}
