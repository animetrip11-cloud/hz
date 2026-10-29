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
import java.io.FileInputStream
import android.graphics.Rect
import android.graphics.BitmapRegionDecoder
import kotlin.math.*

interface PageSource: Closeable {
    val count:Int
    fun dimensions(index:Int):PageDimensions
    fun render(index:Int, width:Int):Bitmap
    fun renderTile(index:Int,width:Int,tile:Int):Bitmap=render(index,width)
    companion object {
        fun open(file:File, format:String):PageSource = if(format=="PDF") PdfPages(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)) else ZipPages(file.inputStream())
        fun open(descriptor:ParcelFileDescriptor,format:String):PageSource = if(format=="PDF")PdfPages(descriptor) else ZipPages(ParcelFileDescriptor.AutoCloseInputStream(descriptor))
    }
}
private class PdfPages(private val descriptor:ParcelFileDescriptor):PageSource {
    private val renderer=try { PdfRenderer(descriptor) } catch(e:Exception) { descriptor.close(); if(e is SecurityException) throw java.io.IOException("password",e); throw e }
    override val count get()=renderer.pageCount
    @Synchronized override fun dimensions(index:Int)=renderer.openPage(index).use { PageDimensions(it.width,it.height) }
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
data class PageDimensions(val width:Int,val height:Int) {
    init {require(width>0 && height>0)}
    val isLong get()=height.toLong()>width.toLong()*3
    val tileHeight get()=(width.toLong()*3/2).coerceIn(1,Int.MAX_VALUE.toLong()).toInt()
    val tiles get()=((height.toLong()+tileHeight-1)/tileHeight).toInt()
    fun top(tile:Int)=(tile.toLong()*tileHeight).coerceAtMost(height.toLong()).toInt()
    fun bottom(tile:Int)=((tile.toLong()+1)*tileHeight).coerceAtMost(height.toLong()).toInt()
}
private class ZipPages(input:FileInputStream):PageSource {
    private val zip=SeekableZip(input.channel,input)
    private val entries=zip.entries
    override val count get()=entries.size
    @Synchronized override fun dimensions(index:Int):PageDimensions {
        val entry=entries[index]
        require(entry.size in 1L..150_000_000L) { "image_size" }
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        zip.stream(entry).use {BitmapFactory.decodeStream(it,null,bounds)}
        return PageDimensions(bounds.outWidth,bounds.outHeight)
    }
    @Synchronized override fun render(index:Int,width:Int):Bitmap {
        val size=dimensions(index)
        var sample=1
        while(size.width.toLong()*size.height/(sample.toLong()*sample)>(if(width<500)500_000L else 4_000_000L) || size.width/sample>width.coerceIn(64,2400)*2) sample*=2
        return zip.stream(entries[index]).use {BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {inSampleSize=sample})} ?: error("image_decode")
    }
    @Suppress("DEPRECATION")
    @Synchronized override fun renderTile(index:Int,width:Int,tile:Int):Bitmap {
        val size=dimensions(index);require(tile in 0 until size.tiles)
        val target=width.coerceIn(64,1600)
        var sample=1
        while(size.width/(sample*2)>=target || size.width.toLong()*(size.bottom(tile)-size.top(tile))/(sample.toLong()*sample)>4_000_000L)sample*=2
        return zip.stream(entries[index]).use {stream->
            val decoder=BitmapRegionDecoder.newInstance(stream,false) ?: error("image_decode")
            try {decoder.decodeRegion(Rect(0,size.top(tile),size.width,size.bottom(tile)),BitmapFactory.Options().apply {inSampleSize=sample}) ?: error("image_decode")}
            finally {decoder.recycle()}
        }
    }
    @Synchronized override fun close()=zip.close()
}
