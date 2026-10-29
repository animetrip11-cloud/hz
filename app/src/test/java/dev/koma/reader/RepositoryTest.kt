package dev.koma.reader

import android.app.Application
import android.net.Uri
import androidx.room.Room
import dev.koma.reader.core.*
import dev.koma.reader.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.zip.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
class RepositoryTest {
    private lateinit var repo:Repository
    @Before fun setup() { val c=RuntimeEnvironment.getApplication();c.deleteDatabase("koma.db");File(c.filesDir,"manga").deleteRecursively();repo=Repository(c) }
    @After fun close() { repo.db.close() }
    @Test fun positionsBookmarksAndIndependentHistorySurviveDatabaseReopen()=runBlocking {
        val h="a".repeat(64);repo.dao.put(Work("w","История"));repo.dao.put(Volume(h,"w","Том","a.pdf","PDF",12,10,0))
        repo.dao.position(h,7,.42f);repo.dao.put(Bookmark(h,7,"Сцена"));repo.dao.put(Bookmark(h,7,"Новая заметка"));repo.open(h);repo.dao.clearHistory()
        repo.db.close();repo=Repository(RuntimeEnvironment.getApplication())
        assertEquals(7,repo.dao.volume(h)!!.page);assertEquals(.42f,repo.dao.volume(h)!!.offset);assertEquals(1,repo.dao.allBookmarks().size);assertEquals("Новая заметка",repo.dao.allBookmarks()[0].note)
    }
    @Test fun restoreMergePreservesLocalAndRelinksByHash()=runBlocking {
        val hash="b".repeat(64)
        val b=Backup(works=listOf(Work("w","Серия")),volumes=listOf(Volume(hash,"w","Том","a.cbz","CBZ",3,100,0,page=2)),bookmarks=listOf(Bookmark(hash,2,"Сцена")),preferences=Preferences())
        repo.restore(codec.encodeToString(b));assertFalse(repo.dao.volume(hash)!!.available)
        repo.dao.position(hash,1,.2f);repo.restore(codec.encodeToString(b));assertEquals(1,repo.dao.volume(hash)!!.page)
        assertEquals(1,repo.dao.allBookmarks().size)
        val before=repo.dao.allVolumes();runCatching {repo.restore("{broken}")};assertEquals(before,repo.dao.allVolumes())
        runCatching {repo.restore(codec.encodeToString(b.copy(schemaVersion=99)))};assertEquals(before,repo.dao.allVolumes())
        val export=codec.decodeFromString<Backup>(repo.export());validateBackup(export);assertEquals(1,export.volumes.single().page)
    }
    @Test fun removingVolumeOnlyRemovesItsRelatedData()=runBlocking {
        repo.dao.put(Work("w","Серия"));val a=Volume("a".repeat(64),"w","A","a.pdf","PDF",3,10,0);val b=a.copy(hash="b".repeat(64),order=1)
        listOf(a,b).forEach {repo.dao.put(it);repo.dao.put(Bookmark(it.hash,1));repo.open(it.hash);repo.file(it.hash).writeText("local copy")}
        repo.delete(a);assertNull(repo.dao.volume(a.hash));assertFalse(repo.file(a.hash).exists());assertNotNull(repo.dao.volume(b.hash));assertEquals(listOf(b.hash),repo.dao.allBookmarks().map {it.hash})
    }
    @Test fun duplicateBytesDifferentNamesAndRestoreReattachment()=runBlocking {
        val c=RuntimeEnvironment.getApplication()
        val first=File(c.cacheDir,"One.cbz")
        first.writeBytes(javaClass.classLoader!!.getResourceAsStream("Тест Том 2.cbz")!!.readBytes())
        val second=File(c.cacheDir,"Renamed.cbz");first.copyTo(second,overwrite=true)
        val items=repo.prepare(listOf(Uri.fromFile(first)),null){_,_->}
        assertNull(items.single().error)
        val hash=items.single().hash
        repo.commit(items){}
        val duplicate=repo.prepare(listOf(Uri.fromFile(second)),null){_,_->}.single()
        assertEquals(hash,duplicate.duplicate!!.hash)
        repo.dao.position(hash,2,.3f);val saved=repo.dao.volume(hash)!!
        repo.file(hash).delete();repo.dao.put(saved.copy(available=false))
        val relink=repo.prepare(listOf(Uri.fromFile(second)),null){_,_->}
        assertNull(relink.single().duplicate);repo.commit(relink){}
        assertEquals(2,repo.dao.volume(hash)!!.page);assertEquals(.3f,repo.dao.volume(hash)!!.offset);assertTrue(repo.dao.volume(hash)!!.available)
        first.writeBytes(javaClass.classLoader!!.getResourceAsStream("empty.cbz")!!.readBytes())
        val invalid=repo.prepare(listOf(Uri.fromFile(first)),null){_,_->}.single()
        assertEquals("error_empty",invalid.error)
        assertEquals(1,repo.dao.allVolumes().size)
    }

    @Test fun sameNameDifferentContentIsNotDuplicate()=runBlocking {
        val c=RuntimeEnvironment.getApplication();val f=File(c.cacheDir,"Same.cbz")
        f.writeBytes(javaClass.classLoader!!.getResourceAsStream("Тест Том 2.cbz")!!.readBytes())
        val first=repo.prepare(listOf(Uri.fromFile(f)),null){_,_->};repo.commit(first){}
        val png=ZipFile(f).use {zip->zip.getInputStream(zip.getEntry("1.png")).use {it.readBytes()}}
        ZipOutputStream(f.outputStream()).use {zip->zip.putNextEntry(ZipEntry("1.png"));zip.write(png);zip.closeEntry()}
        val second=repo.prepare(listOf(Uri.fromFile(f)),null){_,_->}
        assertNull(second.single().error);assertNull(second.single().duplicate);assertNotEquals(first.single().hash,second.single().hash)
        repo.commit(second){};assertEquals(2,repo.dao.allVolumes().size)
    }
    @Test fun cancellationRemovesPartialCopyAndPreservesOriginal()=runBlocking {
        val c=RuntimeEnvironment.getApplication();val f=File(c.cacheDir,"Cancel.cbz")
        val original=javaClass.classLoader!!.getResourceAsStream("Тест Том 2.cbz")!!.readBytes();f.writeBytes(original)
        val job=launch {
            repo.prepare(listOf(Uri.fromFile(f)),null){_,_->this.cancel()}
        }
        job.join()
        assertTrue(job.isCancelled);assertArrayEquals(original,f.readBytes());assertTrue(repo.dao.allVolumes().isEmpty())
        assertTrue(File(c.filesDir,"staging").listFiles().isNullOrEmpty())
    }

    @Test fun importingOriginalDoesNotCreatePermanentOrTemporaryMangaCopy()=runBlocking {
        val c=RuntimeEnvironment.getApplication();val original=File(c.cacheDir,"external.cbz")
        original.writeBytes(javaClass.classLoader!!.getResourceAsStream("Тест Том 2.cbz")!!.readBytes())
        val bytes=original.readBytes();val items=repo.prepare(listOf(Uri.fromFile(original)),null){_,_->}
        assertNull(items.single().error);assertTrue(repo.files.listFiles().isNullOrEmpty())
        assertTrue(File(c.filesDir,"staging").listFiles().isNullOrEmpty())
        repo.commit(items){}
        val v=repo.dao.volume(items.single().hash)!!
        assertEquals(Uri.fromFile(original).toString(),v.sourceUri);assertTrue(repo.canRead(v));assertFalse(repo.file(v.hash).exists())
        repo.db.close();repo=Repository(c);repo.initialize()
        assertTrue(repo.canRead(repo.dao.volume(v.hash)!!))
        repo.openSource(repo.dao.volume(v.hash)!!).use {assertEquals(4,it.count)}
        repo.delete(repo.dao.volume(v.hash)!!)
        assertArrayEquals(bytes,original.readBytes())
    }
    @Test fun linkingLegacyCopyKeepsAllReadingDataAndOnlyThenRemovesCopy()=runBlocking {
        val c=RuntimeEnvironment.getApplication();val original=File(c.cacheDir,"legacy-original.cbz")
        original.writeBytes(javaClass.classLoader!!.getResourceAsStream("Тест Том 2.cbz")!!.readBytes())
        val hash=MessageDigest.getInstance("SHA-256").digest(original.readBytes()).joinToString(""){"%02x".format(it)}
        repo.dao.put(Work("legacy","Моё название",overrides=codec.encodeToString(Overrides(mode="vertical"))))
        val legacy=Volume(hash,"legacy","Мой том","old.cbz","CBZ",4,original.length(),7,page=2,offset=.45f,completed=true)
        repo.dao.put(legacy);repo.dao.put(Bookmark(hash,2,"Моя заметка"));repo.open(hash);original.copyTo(repo.file(hash))
        val items=repo.prepare(listOf(Uri.fromFile(original)),null){_,_->}
        assertNull(items.single().duplicate);assertTrue(repo.file(hash).exists())
        // A failed commit cannot delete a good private copy.
        runCatching {repo.commit(listOf(items.single().copy(uri=Uri.fromFile(File(c.cacheDir,"absent")).toString()))) {}}
        assertTrue(repo.file(hash).exists());assertNull(repo.dao.volume(hash)!!.sourceUri)
        repo.commit(items){}
        val linked=repo.dao.volume(hash)!!
        assertEquals(legacy.copy(sourceUri=Uri.fromFile(original).toString()),linked)
        assertFalse(repo.file(hash).exists());assertTrue(original.exists())
        assertEquals("Моя заметка",repo.dao.allBookmarks().single().note)
        assertEquals("Моё название",repo.dao.work("legacy")!!.title)
        assertEquals("vertical",codec.decodeFromString<Overrides>(repo.dao.work("legacy")!!.overrides!!).mode)
    }
    @Test fun inaccessibleOriginalKeepsProgressAndBackupDoesNotExportDeviceUri()=runBlocking {
        val c=RuntimeEnvironment.getApplication();val original=File(c.cacheDir,"missing-later.cbz")
        original.writeBytes(javaClass.classLoader!!.getResourceAsStream("Тест Том 2.cbz")!!.readBytes())
        val items=repo.prepare(listOf(Uri.fromFile(original)),null){_,_->};repo.commit(items){}
        val hash=items.single().hash;repo.dao.position(hash,2,.6f);repo.dao.put(Bookmark(hash,2,"Сохранено"))
        assertNull(codec.decodeFromString<Backup>(repo.export()).volumes.single().sourceUri)
        original.delete();repo.db.close();repo=Repository(c);repo.initialize()
        assertFalse(repo.dao.volume(hash)!!.available);assertEquals(2,repo.dao.volume(hash)!!.page);assertEquals("Сохранено",repo.dao.allBookmarks().single().note)
    }

}
