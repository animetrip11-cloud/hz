package dev.koma.reader.reader

import android.graphics.Bitmap
import android.util.LruCache
import java.io.File

/** Retains at most 16 MiB. Eviction never recycles a bitmap still displayed by Compose. */
object PageBitmaps {
    private val cache=object:LruCache<String,Bitmap>(16*1024*1024) {
        override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount
    }
    @Synchronized fun load(hash:String,page:Int,width:Int,open:()->PageSource):Bitmap {
        val key="$hash:$page:$width"
        return cache.get(key) ?: open().use { it.render(page,width) }.also { cache.put(key,it) }
    }
    fun clear()=cache.evictAll()
}
