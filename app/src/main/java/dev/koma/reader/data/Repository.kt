package dev.koma.reader.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.koma.reader.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.withTransaction
import dev.koma.reader.core.*
import dev.koma.reader.reader.PageSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID

private val Context.settings by preferencesDataStore("settings")
val codec = Json { ignoreUnknownKeys=true; encodeDefaults=true }
data class Candidate(val uri: String, val name: String, val hash: String = "", val format: String = "", val pages: Int = 0, val bytes: Long = 0, val workTitle: String = "", val title: String = "", val existingWork: String? = null, val duplicate: Volume? = null, val error: String? = null)
class Repository(val context: Context) {
    val db=Room.databaseBuilder(context,KomaDatabase::class.java,"koma.db").addMigrations(MIGRATION_1_2).build()
    val dao=db.library()
    val files=File(context.filesDir,"manga").apply { mkdirs() }
    private val staging=File(context.filesDir,"staging").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    private val key=stringPreferencesKey("preferences")
    val preferences=context.settings.data.map { runCatching { codec.decodeFromString<Preferences>(it[key] ?: "{}") }.getOrDefault(Preferences()) }
    suspend fun preferences(value: Preferences) { context.settings.edit { it[key]=codec.encodeToString(value) } }
    private val initialization=Mutex()
    private var initialized=false
    suspend fun initialize()=withContext(Dispatchers.IO) {
        initialization.withLock {
            if(!initialized) {
                val volumes=dao.allVolumes();val known=volumes.map { it.hash }.toSet()
                // A process may have died after rename but before its database transaction.
                files.listFiles()?.filter { it.name !in known }?.forEach { it.delete() }
                volumes.forEach { v->val present=canRead(v);if(v.available!=present)dao.put(v.copy(available=present)) }
                initialized=true
            }
        }
    }
    fun file(hash: String)=File(files,hash)
    fun openSource(v:Volume):PageSource {
        if(v.sourceUri==null)return PageSource.open(file(v.hash),v.format)
        return openUri(Uri.parse(v.sourceUri),v.format)
    }
    private fun openUri(uri:Uri,format:String):PageSource {
        if(uri.scheme=="file")return PageSource.open(File(requireNotNull(uri.path)),format)
        val descriptor=requireNotNull(context.contentResolver.openFileDescriptor(uri,"r")) {"access"}
        return PageSource.open(descriptor,format)
    }
    fun canRead(v:Volume):Boolean = runCatching {
        if(v.sourceUri==null)file(v.hash).isFile else {
            val uri=Uri.parse(v.sourceUri)
            if(uri.scheme=="file")File(requireNotNull(uri.path)).isFile else context.contentResolver.openFileDescriptor(uri,"r")?.use {true} ?: false
        }
    }.getOrDefault(false)
    fun bitmap(v:Volume,page:Int,width:Int)=dev.koma.reader.reader.PageBitmaps.load(v.hash,page,width) {openSource(v)}

    suspend fun prepare(uris: List<Uri>, target: String?, onProgress: (String,Long)->Unit): List<Candidate> = withContext(Dispatchers.IO) {
        initialize()
        val selected=mutableSetOf<String>()
        uris.map { uri ->
            currentCoroutineContext().ensureActive()
            var name=uri.lastPathSegment ?: "document"
            try {
                if(uri.scheme=="file") {name=File(uri.path!!).name}
                else {
                    try {context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}
                    catch(e:SecurityException) {throw java.io.IOException("persistent_access",e)}
                    context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c ->
                        if(c.moveToFirst()) {name=c.getString(0) ?: name}
                    }
                }
                val digest=MessageDigest.getInstance("SHA-256"); var count=0L
                val header=ByteArray(5)
                context.contentResolver.openInputStream(uri).use {input ->
                    requireNotNull(input) {"access"}
                    val buffer=ByteArray(65536)
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val n=input.read(buffer);if(n<0)break
                        if(count<5)System.arraycopy(buffer,0,header,count.toInt(),minOf(n,5-count.toInt()))
                        digest.update(buffer,0,n);count+=n;onProgress(name,count)
                    }
                }
                val hash=digest.digest().joinToString("") {"%02x".format(it)}
                val format=when {
                    String(header,Charsets.US_ASCII).startsWith("%PDF-")->"PDF"
                    header[0]==80.toByte() && header[1]==75.toByte()->"CBZ"
                    else->error("format")
                }
                val pages=try {openUri(uri,format).use {source->if(source.count>0)source.render(0,64).recycle();source.count}}
                    catch(e:java.io.IOException) {throw e}
                    catch(e:IllegalArgumentException) {throw java.io.IOException("seekable",e)}
                require(pages>0) {"empty"}
                val existing=dao.volume(hash)
                val duplicate=existing?.takeIf {it.available && it.sourceUri!=null && canRead(it)}
                require(selected.add(hash)) { "duplicate_selection" }
                Candidate(uri.toString(),name,hash,format,pages,count,suggestedWork(name),name.substringBeforeLast('.'),target,duplicate)
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {Candidate(uri.toString(),name,error=errorKey(e))}
        }.sortedWith { a,b -> NaturalOrder.compare(a.name,b.name) }
    }
    suspend fun commit(items: List<Candidate>, progress: (Int)->Unit) = withContext(Dispatchers.IO) {
        items.filter { it.error==null && it.duplicate==null }.forEachIndexed { index,c ->
            currentCoroutineContext().ensureActive()
            val old=dao.volume(c.hash)
            // Validate the retained document before replacing a legacy private copy.
            openUri(Uri.parse(c.uri),c.format).use {require(it.count==c.pages)}
            try {
                db.withTransaction {
                    if(old!=null) dao.put(old.copy(available=true,bytes=c.bytes,pages=c.pages,page=old.page.coerceIn(0,c.pages-1),sourceUri=c.uri))
                    else {
                        val all=dao.allWorks()
                        val work=all.find { it.id==c.existingWork } ?: all.find { it.title.equals(c.workTitle,true) } ?: Work(UUID.randomUUID().toString(),c.workTitle.trim().ifEmpty { c.title }).also { dao.put(it) }
                        val order=(dao.allVolumes().filter { it.workId==work.id }.maxOfOrNull { it.order } ?: -1)+1
                        dao.put(Volume(c.hash,work.id,c.title,c.name,c.format,c.pages,c.bytes,order,sourceUri=c.uri))
                    }
                }
            } catch(e:Exception) {throw e}
            file(c.hash).delete()
            progress(index+1)
        }
        discardStaging()
    }
    suspend fun discardStaging()=withContext(Dispatchers.IO) { staging.listFiles()?.forEach { it.delete() }; Unit }
    suspend fun open(hash:String) { db.withTransaction { dao.put(History(hash)); val v=dao.volume(hash) ?: return@withTransaction; dao.work(v.workId)?.takeIf { it.status=="new" }?.let { dao.put(it.copy(status="reading")) } } }
    suspend fun delete(v:Volume) { db.withTransaction { dao.removeBookmarks(v.hash); dao.removeHistory(v.hash); dao.removeVolume(v.hash); dao.removeEmptyWork(v.workId) }; withContext(Dispatchers.IO) { file(v.hash).delete() } }
    suspend fun export():String = db.withTransaction { codec.encodeToString(Backup(works=dao.allWorks(),volumes=dao.allVolumes().map {it.copy(sourceUri=null,available=false)},bookmarks=dao.allBookmarks(),preferences=preferences.first())) }
    suspend fun restore(text:String) {
        val b=codec.decodeFromString<Backup>(text); validateBackup(b)
        db.withTransaction {
            val works=dao.allWorks().associateBy { it.id }; val volumes=dao.allVolumes().associateBy { it.hash }; val marks=dao.allBookmarks().map { it.hash to it.page }.toSet()
            b.works.filter { it.id !in works }.forEach { dao.put(it) }
            b.volumes.filter { it.hash !in volumes }.forEach { dao.put(it.copy(available=file(it.hash).exists(),sourceUri=null)) }
            b.bookmarks.filter { (it.hash to it.page) !in marks }.forEach { dao.put(it) }
            if(works.isEmpty() && volumes.isEmpty()) preferences(b.preferences)
        }
    }
    fun errorKey(e:Throwable):String = when {
        e.message=="persistent_access"->"error_persistent"
        e.message=="seekable"->"error_seekable"
        e.message=="password"->"error_password"
        e is SecurityException->"error_access"
        e is java.io.FileNotFoundException->"error_access"
        e.message=="space" || e.message?.contains("ENOSPC")==true->"error_space"
        e.message=="format"->"error_format"
        e.message=="empty"->"error_empty"
        e.message=="duplicate_selection"->"error_duplicate"
        else->"error_damaged"
    }
}
