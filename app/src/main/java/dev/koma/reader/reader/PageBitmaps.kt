package dev.koma.reader.reader

import android.graphics.Bitmap
import android.util.LruCache
import java.io.File

/** Retains at most 16 MiB. Eviction never recycles a bitmap still displayed by Compose. */
object PageBitmaps {
    private val cache=object:LruCache<String,Bitmap>(16*1024*1024) {
        override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount
    }
    @Synchronized fun load(file:File,format:String,page:Int,width:Int):Bitmap {
        val key="${file.name}:$page:$width"
        return cache.get(key) ?: PageSource.open(file,format).use { it.render(page,width) }.also { cache.put(key,it) }
    }
    fun clear()=cache.evictAll()
}
