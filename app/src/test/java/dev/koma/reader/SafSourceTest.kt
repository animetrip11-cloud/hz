package dev.koma.reader

import android.app.Application
import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import dev.koma.reader.data.Repository
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

class TestDocumentsProvider:ContentProvider() {
    lateinit var source:File
    override fun onCreate()=true
    override fun getType(uri:Uri)="application/zip"
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
        val columns=projection ?: arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {addRow(columns.map {if(it==OpenableColumns.DISPLAY_NAME)source.name else source.length()})}
    }
    override fun openFile(uri:Uri,mode:String)=ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY)
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException()
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=throw AssertionError("Original must never be deleted")
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=throw AssertionError("Original must never be changed")
}
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
class SafSourceTest {
    @Test fun contentUriIsRetainedAndArchiveReadThroughDescriptorWithoutCopy()=runBlocking {
        val context=RuntimeEnvironment.getApplication();context.deleteDatabase("koma.db")
        val original=File(context.cacheDir,"provider.cbz")
        javaClass.classLoader!!.getResourceAsStream("Тест Том 2.cbz")!!.use {i->original.outputStream().use {i.copyTo(it)}}
        val provider=TestDocumentsProvider().apply {source=original;attachInfo(context,ProviderInfo().apply {authority="koma.documents.test"})}
        ShadowContentResolver.registerProviderInternal("koma.documents.test",provider)
        val uri=Uri.parse("content://koma.documents.test/book/1")
        val repo=Repository(context)
        try {
            val candidate=repo.prepare(listOf(uri),null){_,_->}.single()
            assertNull(candidate.error)
            repo.commit(listOf(candidate)){}
            val volume=repo.dao.volume(candidate.hash)!!
            assertEquals(uri.toString(),volume.sourceUri)
            assertTrue(context.contentResolver.persistedUriPermissions.any {it.uri==uri && it.isReadPermission})
            assertFalse(repo.file(volume.hash).exists())
            repo.openSource(volume).use {source->assertEquals(4,source.count);assertEquals(600,source.dimensions(0).width);source.render(1,200).recycle()}
            repo.delete(volume)
            assertTrue(original.exists())
        } finally {repo.db.close()}
    }
}
