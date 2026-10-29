package dev.koma.reader
import dev.koma.reader.core.*
import dev.koma.reader.data.codec
import org.junit.Assert.*
import org.junit.Test
class CoreTest {
    @Test fun naturalPathsAndServiceEntries() {
        val paths=listOf("10.png","2.png","1.png","chapter 10/1.webp","chapter 2/10.jpg","chapter 2/2.jpg","__MACOSX/1.jpg",".hidden/1.png","../1.png","x.txt","A.png","a.png")
        assertEquals(listOf("1.png","2.png","10.png","A.png","a.png","chapter 2/2.jpg","chapter 2/10.jpg","chapter 10/1.webp"),paths.filter(::isPage).sortedWith(NaturalOrder))
    }
    @Test fun arbitrarilyLargeNumbersAndStableTies() {
        assertTrue(NaturalOrder.compare("vol99999999999999999999","vol100000000000000000000")<0)
        val names=listOf("01.png","1.png","A2.jpg","a2.jpg")
        assertEquals(names.sortedWith(NaturalOrder),names.reversed().sortedWith(NaturalOrder))
    }
    @Test fun overrideInheritanceDoesNotOverwriteOtherFields() {
        val overrides=Overrides(mode="vertical")
        assertEquals(ReadingSettings(mode="vertical",brightness=.4f),overrides.resolve(ReadingSettings(brightness=.4f)))
        assertEquals("ltr",Overrides().resolve(ReadingSettings(mode="ltr")).mode)
        assertEquals("vertical",overrides.resolve(ReadingSettings(mode="ltr")).mode)
    }
    @Test fun groupingIsEditableSuggestion() { assertEquals("Манга",suggestedWork("Манга Том 10.cbz"));assertEquals("Story",suggestedWork("Story vol 2.pdf")) }
    @Test fun backupRoundTripAndValidation() {
        val b=Backup(works=listOf(Work("w","История")),volumes=listOf(Volume("a".repeat(64),"w","Том 1","a.pdf","PDF",10,123,0,page=5,offset=.3f)),bookmarks=listOf(Bookmark("a".repeat(64),5,"Заметка")),preferences=Preferences(reading=ReadingSettings(mode="ltr")))
        val restored=codec.decodeFromString<Backup>(codec.encodeToString(b));validateBackup(restored);assertEquals(b,restored)
        assertThrows(IllegalArgumentException::class.java){validateBackup(b.copy(schemaVersion=2))}
        assertThrows(IllegalArgumentException::class.java){validateBackup(b.copy(volumes=listOf(b.volumes[0].copy(page=10))))}
        assertThrows(IllegalArgumentException::class.java){validateBackup(b.copy(bookmarks=listOf(b.bookmarks[0].copy(page=-1))))}
    }
}
