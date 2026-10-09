import struct, os, sys, collections
F='sm3dl.3ds'
f=open(F,'rb')
def rd(o,n): f.seek(o); return f.read(n)
ncch=32*0x200
n=rd(ncch,0x200)
romfs=ncch+struct.unpack_from('<I',n,0x1B0)[0]*0x200
ivfc=rd(romfs,0x60)
assert ivfc[:4]==b'IVFC'
master_size=struct.unpack_from('<I',ivfc,0x08)[0]
l3_bs=1<<struct.unpack_from("<I",ivfc,0x4C)[0]
l3=romfs+((0x60+master_size+l3_bs-1)//l3_bs)*l3_bs
h=rd(l3,0x28)
hs,dho,dhs,dmo,dms,fho,fhs,fmo,fms,fdo=struct.unpack('<10I',h)
dm=rd(l3+dmo,dms); fm=rd(l3+fmo,fms)
files=[]
def walk_dir(off,path):
    parent,sib,child,file,nxt,nl=struct.unpack_from('<6I',dm,off)
    name=dm[off+0x18:off+0x18+nl].decode('utf-16le')
    p=path+'/'+name if off else ''
    fo=file
    while fo!=0xFFFFFFFF:
        par,s,do,ds,nh,fnl=struct.unpack_from('<IIQQII',fm,fo)
        fname=fm[fo+0x20:fo+0x20+fnl].decode('utf-16le')
        files.append((p+'/'+fname,l3+fdo+do,ds)); fo=s
    c=child
    while c!=0xFFFFFFFF:
        walk_dir(c,p); c=struct.unpack_from('<I',dm,c+4)[0]
walk_dir(0,'')
if __name__=='__main__':
    ext=collections.Counter(); sz=collections.Counter()
    for p,o,s in files:
        e=os.path.splitext(p)[1].lower(); ext[e]+=1; sz[e]+=s
    print(len(files),'files')
    for e,c in ext.most_common(30): print(f'{e:12s} {c:6d} {sz[e]/1e6:8.1f} MB')
    open('files.txt','w').write('\n'.join(f'{p}\t{o}\t{s}' for p,o,s in files))
