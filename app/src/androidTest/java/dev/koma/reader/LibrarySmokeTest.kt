package dev.koma.reader

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real PDF import through confirmation, reading, persistence and the four tabs. */
class LibrarySmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun waitText(text:String) { compose.waitUntil(120_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun shot(name:String) {
        compose.waitForIdle()
        val dir=File(compose.activity.getExternalFilesDir(null),"screenshots").apply {mkdirs()}
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { b->File(dir,"$name.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    }
    @Test fun offlineLibraryFlow() {
        val context=compose.activity.applicationContext
        val repo=(context as KomaApplication).repository
        runBlocking(Dispatchers.IO) { repo.db.clearAllTables();repo.files.listFiles()?.forEach {it.delete()};repo.preferences(dev.koma.reader.core.Preferences()) }
        waitText("Моя библиотека")
        compose.onNodeWithText("Выбрать файлы").assertExists()
        shot("01-empty-library")
        val pdf=File(context.cacheDir,"Тест Том 1.pdf")
        InstrumentationRegistry.getInstrumentation().context.assets.open("Тест Том 1.pdf").use {input->pdf.outputStream().use {input.copyTo(it)}}
        compose.activityRule.scenario.onActivity {it.incoming=Uri.fromFile(pdf)}
        waitText("Добавить 1 том(а)")
        shot("02-import")
        compose.onNodeWithText("Добавить 1 том(а)").performScrollTo().performClick()
        waitText("Моя библиотека")
        compose.onNodeWithText("Тест").performScrollTo().performClick()
        waitText("Тома")
        shot("03-work")
        compose.onNodeWithText("Продолжить").performScrollTo().performClick()
        waitText("Страница 1 / 3")
        compose.waitUntil(120_000) { compose.onAllNodesWithContentDescription("Предпросмотр страницы 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().performTouchInput {swipeLeft()}
        waitText("Страница 2 / 3")
        compose.onNodeWithContentDescription("Закладка текущей страницы").performClick()
        shot("04-reader")
        compose.activityRule.scenario.recreate()
        waitText("Страница 2 / 3")
        compose.onNodeWithContentDescription("Назад").performClick()
        waitText("Тома")
        compose.onNodeWithContentDescription("Назад").performClick()
        waitText("Моя библиотека")
        shot("05-library")
        compose.onNodeWithText("Закладки",useUnmergedTree=true).performClick()
        waitText("Места, к которым хочется вернуться.")
        compose.onNodeWithText("Страница 2 / 3").assertExists()
        shot("06-bookmarks")
        compose.onNodeWithText("История",useUnmergedTree=true).performClick()
        waitText("Истории, к которым вы возвращаетесь.")
        compose.onNodeWithText("Страница 2 / 3").assertExists()
        shot("07-history")
        compose.onAllNodesWithText("Настройки",useUnmergedTree=true).onFirst().performClick()
        waitText("Чтение в своём ритме.")
        shot("08-settings")
    }
}
