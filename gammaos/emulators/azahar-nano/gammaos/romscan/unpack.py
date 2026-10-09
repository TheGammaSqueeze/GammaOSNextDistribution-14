import struct, os, collections
from romfs import files, rd
def yaz0(d):
    assert d[:4]==b'Yaz0'; size=struct.unpack_from('>I',d,4)[0]
    out=bytearray(); i=16
    while len(out)<size:
        code=d[i]; i+=1
        for b in range(8):
            if len(out)>=size: break
            if code&(0x80>>b): out.append(d[i]); i+=1
            else:
                b1,b2=d[i],d[i+1]; i+=2
                dist=((b1&0xF)<<8|b2)+1; n=b1>>4
                if n==0: n=d[i]+0x12; i+=1
                else: n+=2
                for _ in range(n): out.append(out[-dist])
    return bytes(out)
def sarc(d):
    bom=d[6:8]; e='<' if bom==b'\xff\xfe' else '>'
    hdr=struct.unpack_from(e+'H',d,4)[0]; data_off=struct.unpack_from(e+'I',d,0xC)[0]
    sfat=hdr; n=struct.unpack_from(e+'H',d,sfat+6)[0]
    sfnt=sfat+0xC+n*16; names=sfnt+8
    out=[]
    for k in range(n):
        h,attr,s,en=struct.unpack_from(e+'IIII',d,sfat+0xC+k*16)
        name=''
        if attr&0x01000000:
            no=names+(attr&0xFFFF)*4; name=d[no:d.index(b'\0',no)].decode('ascii','replace')
        out.append((name,d[data_off+s:data_off+en]))
    return out
def narc(d):
    hs=struct.unpack_from('<H',d,0xC)[0]; o=hs
    sec={}
    while o<len(d):
        m=d[o:o+4]; sz=struct.unpack_from('<I',d,o+4)[0]; sec[m]=o; o+=sz
        if sz==0: break
    fat=sec[b'BTAF']; n=struct.unpack_from('<H',d,fat+8)[0]
    ents=[struct.unpack_from('<II',d,fat+12+8*i) for i in range(n)]
    fnt=sec[b'BTNF']; img=sec[b'GMIF']+8
    names=[]
    # root directory only: subtable offset at fnt+8
    sub=fnt+8+struct.unpack_from('<I',d,fnt+8)[0]
    i=sub
    def walk(off,prefix):
        while True:
            l=d[off]; off+=1
            if l==0: break
            if l&0x80:
                nm=d[off:off+(l&0x7f)].decode('ascii','replace'); off+=(l&0x7f)
                did=struct.unpack_from('<H',d,off)[0]; off+=2
                walk(fnt+8+struct.unpack_from('<I',d,fnt+8+(did&0xfff)*8)[0], prefix+nm+'/')
            else:
                names.append(prefix+d[off:off+l].decode('ascii','replace')); off+=l
    walk(sub,'')
    return [(names[k] if k<len(names) else f'f{k}', d[img+a:img+b]) for k,(a,b) in enumerate(ents)]
os.makedirs('x',exist_ok=True)
ext=collections.Counter(); magic=collections.Counter(); total=0
for p,o,s in files:
    if not p.endswith('.szs'): continue
    d=rd(o,s)
    try: d=yaz0(d)
    except Exception as ex: continue
    if d[:4]!=b'NARC': magic['non '+d[:4].hex()]+=1; continue
    for name,blob in narc(d):
        e=os.path.splitext(name)[1].lower(); ext[e]+=1; magic[blob[:4]]+=1; total+=1
        dst='x'+p[:-4]+'/'+name
        os.makedirs(os.path.dirname(dst),exist_ok=True); open(dst,'wb').write(blob)
print(total,'inner files')
for e,c in ext.most_common(25): print(f'{e:10s} {c}')
print([ (k,v) for k,v in magic.most_common(20)])
