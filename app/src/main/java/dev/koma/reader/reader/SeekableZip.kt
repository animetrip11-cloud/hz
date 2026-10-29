package dev.koma.reader.reader

import java.io.Closeable
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/** ZIP/ZIP64 central-directory access over a seekable SAF descriptor, without extraction. */
class SeekableZip(private val channel:FileChannel, private val owner:Closeable):Closeable {
    data class Entry(val name:String,val method:Int,val compressed:Long,val size:Long,val localOffset:Long)
    private fun bytes(offset:Long,length:Int):ByteArray {
        require(offset>=0 && length>=0 && offset<=channel.size()-length) { "zip_bounds" }
        val b=ByteBuffer.allocate(length)
        while(b.hasRemaining())check(channel.read(b,offset+b.position())>0) { "zip_truncated" }
        return b.array()
    }
    private fun ByteArray.u16(i:Int)=ByteBuffer.wrap(this,i,2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 65535
    private fun ByteArray.u32(i:Int)=ByteBuffer.wrap(this,i,4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
    private fun ByteArray.i64(i:Int)=ByteBuffer.wrap(this,i,8).order(ByteOrder.LITTLE_ENDIAN).long.also { require(it>=0) }
    val entries:List<Entry>
    init {
        try {
            val length=channel.size();require(length>=22)
            val tailStart=(length-65557).coerceAtLeast(0);val tail=bytes(tailStart,(length-tailStart).toInt())
            val end=(tail.size-22 downTo 0).firstOrNull { tail.u32(it)==0x06054b50L && it+22+tail.u16(it+20)==tail.size } ?: error("zip_end")
            require(tail.u16(end+4)==0 && tail.u16(end+6)==0) { "zip_multidisk" }
            var count=tail.u16(end+10).toLong();var offset=tail.u32(end+16);var dirSize=tail.u32(end+12)
            if(count==65535L || offset==0xffffffffL || dirSize==0xffffffffL) {
                val locator=bytes(tailStart+end-20,20);require(locator.u32(0)==0x07064b50L && locator.u32(4)==0L && locator.u32(16)==1L)
                val record=bytes(locator.i64(8),56);require(record.u32(0)==0x06064b50L && record.u32(16)==0L && record.u32(20)==0L)
                count=record.i64(32);dirSize=record.i64(40);offset=record.i64(48)
            }
            require(count<=100000 && offset<=length-dirSize)
            val endOffset=offset+dirSize
            entries=buildList {
                repeat(count.toInt()) {
                    val h=bytes(offset,46);require(h.u32(0)==0x02014b50L)
                    val flags=h.u16(8);val method=h.u16(10);val nameLen=h.u16(28);val extraLen=h.u16(30);val commentLen=h.u16(32)
                    val nameBytes=bytes(offset+46,nameLen);val extra=bytes(offset+46+nameLen,extraLen)
                    var size=h.u32(24);var compressed=h.u32(20);var local=h.u32(42);var name=String(nameBytes,if(flags and 2048!=0)Charsets.UTF_8 else charset("CP437"))
                    var p=0
                    while(p+4<=extra.size) {
                        val id=extra.u16(p);val n=extra.u16(p+2);p+=4;require(p+n<=extra.size)
                        if(id==1) {var q=p;fun next():Long {require(q+8<=p+n);val v=extra.i64(q);q+=8;return v};if(size==0xffffffffL)size=next();if(compressed==0xffffffffL)compressed=next();if(local==0xffffffffL)local=next()}
                        if(id==0x7075 && n>=5 && extra[p]==1.toByte()) {val crc=java.util.zip.CRC32().apply {update(nameBytes)};if(crc.value==extra.u32(p+1))name=String(extra,p+5,n-5,Charsets.UTF_8)}
                        p+=n
                    }
                    if(!name.endsWith('/') && dev.koma.reader.core.isPage(name)) {
                        require(flags and 1==0) { "zip_encrypted" };require(method==0 || method==8) { "zip_method" }
                        add(Entry(name,method,compressed,size,local))
                    }
                    offset+=46L+nameLen+extraLen+commentLen;require(offset<=endOffset)
                }
            }.sortedWith {a,b->dev.koma.reader.core.NaturalOrder.compare(a.name,b.name)}
        } catch(e:Throwable) {owner.close();throw e}
    }
    fun stream(entry:Entry):InputStream {
        val h=bytes(entry.localOffset,30);require(h.u32(0)==0x04034b50L)
        val start=entry.localOffset+30+h.u16(26)+h.u16(28)
        require(entry.compressed>=0 && start<=channel.size()-entry.compressed)
        val input=object:InputStream() {
            var position=0L
            override fun read():Int {val b=ByteArray(1);return if(read(b,0,1)<0)-1 else b[0].toInt() and 255}
            override fun read(b:ByteArray,off:Int,len:Int):Int {
                if(len==0)return 0
                if(position>=entry.compressed)return -1
                val n=channel.read(ByteBuffer.wrap(b,off,minOf(len.toLong(),entry.compressed-position).toInt()),start+position)
                if(n<0)throw java.io.EOFException();position+=n;return n
            }
        }
        return if(entry.method==0)input else object:InflaterInputStream(input,Inflater(true),65536) {
            override fun close() {try {super.close()} finally {inf.end()}}
        }
    }
    override fun close()=owner.close()
}
