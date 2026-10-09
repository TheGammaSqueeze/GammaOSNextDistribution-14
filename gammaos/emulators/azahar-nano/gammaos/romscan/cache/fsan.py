import struct, collections
from vkch import entries
fs=[pl for t,_,pl in entries('0004000000053F00_fs_vd_hs.vkch') if t==4]
print(len(fs),'fs configs, payload',len(fs[0]))
def split(p):
    c=p[8:]
    fb=c[0:32]; tx=c[32:168]; li=c[168:244]; pt=c[244:272]
    return dict(fb=fb, tex_raw=tx[0:4], border=tx[4:16], tev=tx[16:112], wrap=tx[112:136], light=li, proctex=pt)
parts=[split(p) for p in fs]
for k in parts[0]:
    print(f'{k:8s} distinct {len(set(x[k] for x in parts))}')
# how many FS configs remain distinct if only tev+fb+tex_raw considered
for keys in (('tev',),('tev','fb'),('tev','fb','tex_raw'),('tev','fb','tex_raw','light'),('tev','fb','tex_raw','light','wrap'),('tev','fb','tex_raw','light','wrap','border')):
    print(keys, len(set(tuple(x[k] for k in keys) for x in parts)))
lights=collections.Counter(struct.unpack_from('<I',x['light'],0)[0]&1 for x in parts); print('lighting enabled counts',lights)
print('border nonzero', sum(1 for x in parts if any(x['border'])))
print('fog modes', collections.Counter((struct.unpack_from('<I',x['tex_raw'])[0]>>12)&7 for x in parts))
