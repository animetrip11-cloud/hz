package dev.koma.reader

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import dev.koma.reader.data.Repository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
class MigrationTest {
    @Test fun installedVersionOneDatabaseUpgradesWithoutLosingLibrary()=runBlocking {
        val c=RuntimeEnvironment.getApplication();c.deleteDatabase("koma.db")
        val schema=JSONObject(javaClass.classLoader!!.getResourceAsStream("dev.koma.reader.data.KomaDatabase/1.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database")
        val path=c.getDatabasePath("koma.db");path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path,null).use {db->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {val entity=entities.getJSONObject(i);db.execSQL(entity.getString("createSql").replace("\u0024{TABLE_NAME}",entity.getString("tableName")))}
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema.getString("identityHash")))
            db.execSQL("INSERT INTO works VALUES('w','Серия','reading',123,NULL)")
            db.execSQL("INSERT INTO volumes VALUES(?, 'w','Том','old.cbz','CBZ',10,100,0,6,0.4,0,1,123)",arrayOf("a".repeat(64)))
            db.execSQL("INSERT INTO bookmarks VALUES(?,6,'Заметка',123)",arrayOf("a".repeat(64)))
            db.execSQL("INSERT INTO history VALUES(?,123)",arrayOf("a".repeat(64)))
            db.version=1
        }
        val repo=Repository(c)
        try {
            val v=repo.dao.volume("a".repeat(64))!!
            assertEquals(6,v.page);assertEquals(.4f,v.offset);assertNull(v.sourceUri)
            assertEquals("Серия",repo.dao.allWorks().single().title)
            assertEquals("Заметка",repo.dao.allBookmarks().single().note)
        } finally {repo.db.close()}
    }
}
