package dev.koma.reader

import android.app.Application
import dev.koma.reader.reader.PageSource
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LongPageTest {
    @Test fun twentyFourThousandPixelStripRendersVisibleRegionsAtFullWidth() {
        val file=File(RuntimeEnvironment.getApplication().cacheDir,"long.cbz")
        javaClass.classLoader!!.getResourceAsStream("Long-strip.cbz")!!.use {input->file.outputStream().use {input.copyTo(it)}}
        PageSource.open(file,"CBZ").use {source->
            val size=source.dimensions(0)
            assertEquals(600,size.width);assertEquals(24000,size.height);assertTrue(size.isLong);assertEquals(27,size.tiles)
            listOf(0,12,26).forEachIndexed {colorIndex,tile->
                val image=source.renderTile(0,822,tile)
                assertEquals(600,image.width)
                assertEquals(size.bottom(tile)-size.top(tile),image.height)
                assertTrue(image.width.toLong()*image.height<=4_000_000)
                val color=image.getPixel(image.width/2,image.height/2)
                assertEquals(listOf(0xffe65a7a.toInt(),0xff50b482.toInt(),0xff4682dc.toInt())[colorIndex],color)
                image.recycle()
            }
        }
    }
}
