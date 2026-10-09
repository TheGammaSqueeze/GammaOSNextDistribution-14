import struct, glob, collections
from cache.vkch import entries
fs=[p[8:] for t,_,p in entries('cache/0004000000053F00_fs_vd_hs.vkch') if t==4]
def stages(c):
    out=[]
    for st in range(6):
        o=48+st*16
        src,mod,ops,scl=struct.unpack_from('<4I',c,o)
        # pass-through stage
        if (ops&0xF)==0 and ((ops>>16)&0xF)==0 and (src&0xF)==0xF and ((src>>16)&0xF)==0xF and (mod&0xF)==0 and ((mod>>12)&7)==0 and (scl&3)==0 and ((scl>>16)&3)==0: continue
        out.append((src,mod,ops,scl))
    return out
# all ROM file bytes
blob=bytearray()
for f in glob.glob('x/**/*',recursive=True):
    try: blob+=open(f,'rb').read()
    except IsADirectoryError: pass
print('rom data MB',len(blob)/1e6)
uniq=set(); 
for c in fs:
    for s in stages(c): uniq.add(s)
print('distinct active stages',len(uniq))
found=0; found3=0
for (src,mod,ops,scl) in uniq:
    b3=struct.pack('<3I',src,mod,ops)
    if b3 in blob: found3+=1
print('stages whose source/operand/combiner words appear consecutively in ROM data:',found3,'of',len(uniq))
import re
near=0; sw=0
for (src,mod,ops,scl) in uniq:
    bs=struct.pack('<I',src)
    pos=[m.start() for m in re.finditer(re.escape(bs), blob)][:2000]
    if pos: sw+=1
    bo=struct.pack('<I',mod); bp=struct.pack('<I',ops)
    hit=False
    for p in pos:
        w=blob[max(0,p-64):p+64]
        if bo in w and bp in w: hit=True; break
    near+=hit
print('sources word present:',sw,' sources+operands+combiner within 64 bytes:',near,'of',len(uniq))
