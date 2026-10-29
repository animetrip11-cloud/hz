#!/usr/bin/env python3
"""Deterministic fixtures; no third-party dependencies. Never bundled as a library."""
from pathlib import Path
import struct, zlib, zipfile, hashlib
root=Path(__file__).resolve().parent.parent/'test-documents'
root.mkdir(exist_ok=True)
def png(w,h,seed):
    def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    # Seven-segment large numeral, on alternating colored panels.
    segs={1:'bc',2:'abged',10:'abcdef'}[seed]
    lines={'a':(1,1,4,1),'b':(4,1,4,4),'c':(4,4,4,7),'d':(1,7,4,7),'e':(1,4,1,7),'f':(1,1,1,4),'g':(1,4,4,4)}
    data=bytearray()
    for y in range(h):
        data.append(0)
        for x in range(w):
            px=x*6/w;py=y*9/h
            black=any(min(a,c)-.15<=px<=max(a,c)+.15 and min(b,d)-.15<=py<=max(b,d)+.15 for key,(a,b,c,d) in lines.items() if key in segs)
            data.extend((20,25,32) if black else ((242,244,247) if seed!=2 else (230,90,122)))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(data))+chunk(b'IEND',b'')
def archive(path,items):
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as z:
        for name,data in items:
            i=zipfile.ZipInfo(name,(2026,1,1,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;z.writestr(i,data)
archive(root/'Тест Том 2.cbz',[('10.png',png(600,900,10)),('2.png',png(600,1500,2)),('1.png',png(600,900,1)),('chapter 2/1.png',png(900,600,1)),('__MACOSX/._1.png',b'ignore'),('readme.txt',b'not a page')])
archive(root/'empty.cbz',[('readme.txt',b'no pages')])
(root/'damaged.cbz').write_bytes(b'PK\x03\x04broken')
# Minimal valid multi-page PDF, labels are genuine text, mixed page sizes.
objects=[]
def obj(s):objects.append(s.encode() if isinstance(s,str) else s);return len(objects)
obj('<< /Type /Catalog /Pages 2 0 R >>')
obj('<< /Type /Pages /Kids [3 0 R 5 0 R 7 0 R] /Count 3 >>')
for i in range(3):
    w,h=[(400,600),(400,1000),(600,400)][i]
    obj(f'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 {w} {h}] /Resources << /Font << /F1 9 0 R >> >> /Contents {4+i*2} 0 R >>')
    content=f'0.95 0.96 0.97 rg 0 0 {w} {h} re f 0.1 0.12 0.15 rg BT /F1 110 Tf 70 {h//2} Td ({i+1}) Tj ET'.encode()
    obj(b'<< /Length '+str(len(content)).encode()+b' >>\nstream\n'+content+b'\nendstream')
obj('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>')
pdf=bytearray(b'%PDF-1.4\n');offsets=[0]
for i,o in enumerate(objects,1):offsets.append(len(pdf));pdf+=f'{i} 0 obj\n'.encode()+o+b'\nendobj\n'
xref=len(pdf);pdf+=f'xref\n0 {len(offsets)}\n0000000000 65535 f \n'.encode()
for n in offsets[1:]:pdf+=f'{n:010d} 00000 n \n'.encode()
pdf+=f'trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n'.encode()
(root/'Тест Том 1.pdf').write_bytes(pdf)
# Long webtoon: 600 x 24000, three color bands to verify independent region decoding.
def long_chunk(kind,data):
    return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
long_rows=b''.join(b'\0'+bytes((242,244,247) if y%600<8 else [(230,90,122),(80,180,130),(70,130,220)][y//8000])*600 for y in range(24000))
long_png=b'\x89PNG\r\n\x1a\n'+long_chunk(b'IHDR',struct.pack('>IIBBBBB',600,24000,8,2,0,0,0))+long_chunk(b'IDAT',zlib.compress(long_rows))+long_chunk(b'IEND',b'')
archive(root/'Long-strip.cbz',[('1.png',long_png)])
(root/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(root.iterdir()) if p.name!='SHA256SUMS'))
