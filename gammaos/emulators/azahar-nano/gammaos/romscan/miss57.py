import struct, collections
exec(open('partial.py').read().split("print('truth'")[0])
from cache.vkch import entries
fs=[p[8:] for t,_,p in entries('cache/0004000000053F00_fs_vd_hs.vkch') if t==4]
truth=set(tuple(struct.unpack_from('<4I',c,48+16*k) for k in range(6)) for c in fs)
def pt(s):
    src,mod,ops,scl=s
    return (ops&0xF)==0 and (ops>>16&0xF)==0 and (src&0xF)==0xF and (src>>16&0xF)==0xF and (mod&0xF)==0 and (mod>>12&7)==0 and (scl&3)==0 and (scl>>16&3)==0
def active(t): return [s for s in t if not pt(s)]
mats=list(per_src['model'])+list(per_src['code'])
covered=set(norm(t) for t in mats)
matact=[active(m) for m in mats]
miss=[t for t in truth if norm(t) not in covered]
def inserted(a): return any(len(a)==len(ma)+1 and any(a[:i]+a[i+1:]==ma for i in range(len(a))) for ma in matact)
rest=[t for t in miss if not inserted(active(t))]
SRC={0:'prim',1:'pfrag',2:'sfrag',3:'tex0',4:'tex1',5:'tex2',6:'tex3',0xd:'buf',0xe:'const',0xf:'prev'}
OP={0:'rep',1:'mod',2:'add',3:'adds',4:'lerp',5:'sub',6:'dot',7:'dota',8:'muladd',9:'addmul'}
def fmt(s):
    src,mod,ops,scl=s
    c=[SRC.get((src>>4*k)&0xF,'?') for k in range(3)]; a=[SRC.get((src>>16+4*k)&0xF,'?') for k in range(3)]
    return f"C:{OP.get(ops&0xF)}({','.join(c)}) A:{OP.get(ops>>16&0xF)}({','.join(a)})"
uses=collections.Counter()
for t in rest:
    for s in active(t):
        src=s[0]
        for k in (0,4,8,16,20,24): uses[SRC.get((src>>k)&0xF)]+=1
print('rest',len(rest),'source use',dict(uses))
for t in sorted(rest,key=lambda t:len(active(t)))[:12]:
    print(len(active(t)),' | '.join(fmt(s) for s in active(t)))
