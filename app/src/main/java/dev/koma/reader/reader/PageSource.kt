package dev.koma.reader.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import dev.koma.reader.core.NaturalOrder
import dev.koma.reader.core.isPage
import java.io.Closeable
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.*

interface PageSource: Closeable {
    val count:Int
    fun render(index:Int, width:Int):Bitmap
    companion object {
        fun open(file:File, format:String):PageSource = if(format=="PDF") PdfPages(file) else ZipPages(file)
    }
}
private class PdfPages(file:File):PageSource {
    private val descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer=try { PdfRenderer(descriptor) } catch(e:Exception) { descriptor.close(); if(e is SecurityException) throw java.io.IOException("password",e); throw e }
    override val count get()=renderer.pageCount
    @Synchronized override fun render(index:Int,width:Int):Bitmap = renderer.openPage(index).use { page ->
        val ratio=page.height.toDouble()/page.width
        val w=min(width.coerceIn(64,2400),sqrt((if(width<500)500_000.0 else 4_000_000.0)/ratio).toInt()).coerceAtLeast(1)
        val h=(w*ratio).toInt().coerceAtLeast(1)
        Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            try { page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) } catch(e:Exception) { bitmap.recycle(); throw e }
        }
    }
    @Synchronized override fun close() { renderer.close(); descriptor.close() }
}
private class ZipPages(file:File):PageSource {
    private val zip=ZipFile(file)
    private val entries=zip.entries().asSequence().filter { !it.isDirectory && isPage(it.name) }.sortedWith { a,b -> NaturalOrder.compare(a.name,b.name) }.toList()
    override val count get()=entries.size
    @Synchronized override fun render(index:Int,width:Int):Bitmap {
        val entry=entries[index]
        require(entry.size in -1L..150_000_000L) { "image_size" }
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        zip.getInputStream(entry).use { BitmapFactory.decodeStream(it,null,bounds) }
        require(bounds.outWidth>0 && bounds.outHeight>0) { "image_decode" }
        var sample=1
        while(bounds.outWidth.toLong()*bounds.outHeight/(sample.toLong()*sample)>(if(width<500)500_000L else 4_000_000L) || bounds.outWidth/sample > width.coerceIn(64,2400)*2) sample*=2
        val options=BitmapFactory.Options().apply { inSampleSize=sample }
        return zip.getInputStream(entry).use { BitmapFactory.decodeStream(it,null,options) } ?: error("image_decode")
    }
    @Synchronized override fun close()=zip.close()
}
