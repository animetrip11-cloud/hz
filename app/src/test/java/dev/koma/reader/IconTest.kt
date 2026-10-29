package dev.koma.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconTest {
    @Test fun coloredAndMonochromeKeepThreePanelsInsideCircularSafeArea() {
        val context=RuntimeEnvironment.getApplication()
        for(resource in listOf(R.drawable.ic_launcher_foreground,R.drawable.ic_launcher_mono)) for(size in listOf(24,48,96)) {
            val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
            context.getDrawable(resource)!!.apply {setBounds(0,0,size,size);draw(Canvas(bitmap))}
            val filled=mutableSetOf<Pair<Int,Int>>()
            for(y in 0 until size)for(x in 0 until size) if((bitmap.getPixel(x,y) ushr 24)>127) {
                filled.add(x to y)
                assertTrue("Safe area at $size",hypot(x+.5-size/2,y+.5-size/2)<=size*33.0/108+1)
            }
            assertTrue(filled.size>size)
            var components=0
            while(filled.isNotEmpty()) {
                components++;val queue=java.util.ArrayDeque<Pair<Int,Int>>();queue.add(filled.first());filled.remove(queue.first)
                while(queue.isNotEmpty()) {val(x,y)=queue.removeFirst();for(n in listOf(x-1 to y,x+1 to y,x to y-1,x to y+1))if(filled.remove(n))queue.add(n)}
            }
            assertEquals("Three separate panels at $size",3,components)
            bitmap.recycle()
        }
    }
}
