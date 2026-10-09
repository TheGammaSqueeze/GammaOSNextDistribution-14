import struct
f=open('sm3dl.3ds','rb')
ncch=32*0x200
f.seek(ncch); n=f.read(0x200)
exefs_off=struct.unpack_from('<I',n,0x1A0)[0]*0x200
f.seek(ncch+exefs_off); h=f.read(0x200)
for i in range(8):
    name=h[i*16:i*16+8].rstrip(b'\0'); off,size=struct.unpack_from('<II',h,i*16+8)
    if name==b'.code':
        f.seek(ncch+exefs_off+0x200+off); d=f.read(size); break
# exheader flag: code compressed? check sci flag at ncch+0x200+0xD bit0
f.seek(ncch+0x200+0xD); comp=f.read(1)[0]&1
print('code size',len(d),'compressed',comp)
def blz(d):
    # backward LZ (3DS code compression)
    buf_top_and_bottom, off_size_inc = struct.unpack('<II', d[-8:])
    out_len = len(d) + off_size_inc
    out = bytearray(out_len); out[:len(d)] = d
    in_ptr = len(d) - ((buf_top_and_bottom >> 24) & 0xFF)
    stop = len(d) - (buf_top_and_bottom & 0xFFFFFF)
    out_ptr = out_len
    while in_ptr > stop:
        in_ptr -= 1; ctrl = d[in_ptr]
        for b in range(8):
            if in_ptr <= stop: break
            if ctrl & (0x80 >> b):
                in_ptr -= 2; v = d[in_ptr] | d[in_ptr+1] << 8
                ln = ((v >> 12) & 0xF) + 3; disp = (v & 0xFFF) + 3
                for _ in range(ln):
                    out_ptr -= 1; out[out_ptr] = out[out_ptr + disp]
            else:
                in_ptr -= 1; out_ptr -= 1; out[out_ptr] = d[in_ptr]
    return bytes(out)
if comp: d=blz(d)
open('x/code.bin','wb').write(d); print('code out',len(d))
