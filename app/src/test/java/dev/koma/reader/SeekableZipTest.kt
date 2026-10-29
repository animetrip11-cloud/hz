package dev.koma.reader

import dev.koma.reader.reader.SeekableZip
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.*

class SeekableZipTest {
    @Test fun compressedStoredUnicodeAndNaturalOrderWithoutExtraction() {
        val f=File.createTempFile("seekable",".zip")
        try {
            ZipOutputStream(f.outputStream()).use {z->
                for(name in listOf("10.png","глава 2/1.webp","2.jpg","__MACOSX/._1.png","readme.txt","1.png")) {
                    val data="content:$name".toByteArray();val entry=ZipEntry(name)
                    if(name=="1.png"){entry.method=ZipEntry.STORED;entry.size=data.size.toLong();entry.compressedSize=entry.size;entry.crc=CRC32().apply {update(data)}.value}
                    z.putNextEntry(entry);z.write(data);z.closeEntry()
                }
            }
            val input=f.inputStream()
            SeekableZip(input.channel,input).use {z->
                assertEquals(listOf("1.png","2.jpg","10.png","глава 2/1.webp"),z.entries.map {it.name})
                z.entries.reversed().forEach {e->assertEquals("content:${e.name}",z.stream(e).bufferedReader().use {it.readText()})}
            }
        } finally {f.delete()}
    }
    @Test fun zip64LocalHeaderBeyondFourGiBUsesLongOffsets() {
        val f=File.createTempFile("zip64",".zip")
        fun record(size:Int,block:ByteBuffer.()->Unit)=ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(block).array()
        try {
            val start=0x100000100L;val name="1.png".toByteArray();val data="payload".toByteArray()
            RandomAccessFile(f,"rw").use {r->
                r.seek(start)
                r.write(record(30){putInt(0x04034b50);putShort(45);putShort(0);putShort(0);putInt(0);putInt(0);putInt(data.size);putInt(data.size);putShort(name.size.toShort());putShort(0)})
                r.write(name);r.write(data)
                val central=r.filePointer
                r.write(record(46){putInt(0x02014b50);putShort(45);putShort(45);putShort(0);putShort(0);putInt(0);putInt(0);putInt(data.size);putInt(data.size);putShort(name.size.toShort());putShort(12);putShort(0);putShort(0);putShort(0);putInt(0);putInt(-1)})
                r.write(name);r.write(record(12){putShort(1);putShort(8);putLong(start)})
                val centralSize=r.filePointer-central;val zip64=r.filePointer
                r.write(record(56){putInt(0x06064b50);putLong(44);putShort(45);putShort(45);putInt(0);putInt(0);putLong(1);putLong(1);putLong(centralSize);putLong(central)})
                r.write(record(20){putInt(0x07064b50);putInt(0);putLong(zip64);putInt(1)})
                r.write(record(22){putInt(0x06054b50);putShort(0);putShort(0);putShort(-1);putShort(-1);putInt(-1);putInt(-1);putShort(0)})
            }
            val input=f.inputStream();SeekableZip(input.channel,input).use {z->assertEquals(start,z.entries.single().localOffset);assertEquals("payload",z.stream(z.entries.single()).bufferedReader().use {it.readText()})}
        } finally {f.delete()}
    }
}
