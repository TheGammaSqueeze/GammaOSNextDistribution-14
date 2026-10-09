import struct, sys
def entries(path):
    d=open(path,'rb').read(); i=0; out=[]
    while i+16<=len(d):
        ver,flags,typ,_,size,eid=struct.unpack_from('<BBBBIQ',d,i)
        if ver!=0x42 or size<32: break
        payload=d[i+16:i+size-16]
        if flags&1:
            import zstandard; payload=zstandard.ZstdDecompressor().decompress(payload, max_output_size=8<<20)
        out.append((typ,eid,payload)); i+=size
    return out
if __name__=='__main__':
    import collections
    for p in sys.argv[1:]:
        e=entries(p); c=collections.Counter((t,len(pl)) for t,_,pl in e)
        print(p,len(e),sorted(c.items()))
