package dev.koma.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import android.net.Uri
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=KomaApplication::class, qualifiers="w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun shot(name:String) {
        val dir=File(requireNotNull(System.getProperty("koma.screenshots"))).apply {mkdirs()}
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    private fun waitText(text:String) { compose.waitUntil(30_000) {compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()} }
    @Test fun cbzImportReadBookmarkAndRecreate() {
        val context=compose.activity.applicationContext
        val repo=(context as KomaApplication).repository
        runBlocking(Dispatchers.IO) {repo.db.clearAllTables();repo.files.listFiles()?.forEach {it.delete()}}
        waitText("Выбрать файлы")
        compose.onNodeWithText("Моя библиотека").assertExists()
        compose.onNodeWithText("Выбрать файлы").assertExists()
        shot("robolectric-01-library")
        compose.onNodeWithText("Закладки",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Пока нет закладок").assertExists()
        shot("robolectric-02-bookmarks")
        compose.onNodeWithText("История",useUnmergedTree=true).performClick()
        compose.onNodeWithText("История пока пуста").assertExists()
        shot("robolectric-03-history")
        compose.onAllNodesWithText("Настройки",useUnmergedTree=true).onFirst().performClick()
        compose.onNodeWithText("Чтение в своём ритме.").assertExists()
        shot("robolectric-04-settings")
        compose.onNodeWithText("Библиотека",useUnmergedTree=true).performClick()
        val cbz=File(context.cacheDir,"Тест Том 2.cbz")
        cbz.writeBytes(javaClass.classLoader!!.getResourceAsStream("Тест Том 2.cbz")!!.readBytes())
        compose.runOnUiThread {compose.activity.incoming=Uri.fromFile(cbz)}
        waitText("Добавить 1 том(а)")
        shot("robolectric-05-import")
        compose.onNodeWithText("Добавить 1 том(а)").performScrollTo().performClick()
        waitText("Моя библиотека")
        compose.onNodeWithText("Тест").performScrollTo().performClick()
        waitText("Тома")
        shot("robolectric-06-work")
        compose.onNodeWithText("Продолжить").performScrollTo().performClick()
        waitText("Страница 1 / 4")
        compose.waitUntil(30_000) {compose.onAllNodesWithContentDescription("Предпросмотр страницы 1").fetchSemanticsNodes().isNotEmpty()}
        compose.onRoot().performTouchInput {swipeLeft()}
        waitText("Страница 2 / 4")
        compose.onNodeWithContentDescription("Закладка текущей страницы").performClick()
        shot("robolectric-07-reader")
        compose.activityRule.scenario.recreate()
        waitText("Страница 2 / 4")
        compose.onNodeWithContentDescription("Назад").performClick()
        waitText("Тома")
        compose.onNodeWithContentDescription("Назад").performClick()
        waitText("Моя библиотека")
        shot("robolectric-08-library-with-volume")
        compose.onNodeWithText("Закладки",useUnmergedTree=true).performClick()
        waitText("Места, к которым хочется вернуться.")
        compose.onNodeWithText("Страница 2 / 4").assertExists()
        shot("robolectric-09-bookmarks-with-volume")
        compose.onNodeWithText("История",useUnmergedTree=true).performClick()
        waitText("Истории, к которым вы возвращаетесь.")
        compose.onNodeWithText("Страница 2 / 4").assertExists()
        shot("robolectric-10-history-with-volume")
        compose.onNodeWithText("Библиотека",useUnmergedTree=true).performClick()
        val long=File(context.cacheDir,"Long-strip.cbz")
        long.writeBytes(javaClass.classLoader!!.getResourceAsStream("Long-strip.cbz")!!.readBytes())
        compose.runOnUiThread {compose.activity.incoming=Uri.fromFile(long)}
        waitText("Добавить 1 том(а)")
        compose.onNodeWithText("Добавить 1 том(а)").performScrollTo().performClick()
        waitText("Моя библиотека")
        compose.onNodeWithText("Long-strip").performScrollTo().performClick()
        waitText("Тома")
        compose.onNodeWithText("Продолжить").performScrollTo().performClick()
        waitText("Страница 1 / 1")
        compose.waitUntil(30_000) {compose.onAllNodesWithContentDescription("Страница 1, участок 1").fetchSemanticsNodes().isNotEmpty()}
        val rootWidth=compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        val tileWidth=compose.onNodeWithContentDescription("Страница 1, участок 1").fetchSemanticsNode().boundsInRoot.width
        org.junit.Assert.assertTrue("Long page fills the viewport width",tileWidth>=rootWidth*.95f)
        shot("robolectric-11-long-cbz-top")
        compose.onRoot().performTouchInput {swipe(center.copy(y=height*.60f),center.copy(y=height*.25f))}
        compose.waitUntil(30_000) {runBlocking(Dispatchers.IO) {repo.dao.allVolumes().first {it.fileName=="Long-strip.cbz"}.offset>0f}}
        compose.waitForIdle()
        val visible=compose.onAllNodes(hasContentDescription("Страница 1, участок ",substring=true)).fetchSemanticsNodes().filter {it.boundsInRoot.height>0}.minBy {it.positionInRoot.y}
        val beforeDescription=visible.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].first()
        val beforeTop=visible.positionInRoot.y
        val saved=runBlocking(Dispatchers.IO) {repo.dao.allVolumes().first {it.fileName=="Long-strip.cbz"}}
        compose.activityRule.scenario.recreate()
        waitText("Страница 1 / 1")
        compose.waitUntil(30_000) {compose.onAllNodesWithContentDescription(beforeDescription).fetchSemanticsNodes().isNotEmpty()}
        compose.waitUntil(30_000) {
            val node=compose.onAllNodesWithContentDescription(beforeDescription).fetchSemanticsNodes().firstOrNull()
            node!=null && kotlin.math.abs(node.positionInRoot.y-beforeTop)<4f
        }
        org.junit.Assert.assertEquals(saved.offset,runBlocking(Dispatchers.IO) {repo.dao.volume(saved.hash)!!.offset},.005f)
        shot("robolectric-12-long-cbz-resumed")
    }

}
