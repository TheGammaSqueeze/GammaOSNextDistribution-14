import struct, glob, collections
STAGE_REGS=[0xC0,0xC8,0xD0,0xD8,0xF0,0xF8]
PASS=(0x0F000F, 0, 0, 0)    # pass-through stage: sources prev/prev, modifiers 0, replace, scale 1
def stage_at(w,q): return (w[q-1],w[q+1],w[q+2],w[q+4])
blocks=set()
srcs={'model':[],'code':['x/code.bin']}
srcs['model']=[f for f in glob.glob('x/**/*',recursive=True) if not f.endswith('code.bin')]
found_by=collections.Counter()
per_src={}
for name,files in srcs.items():
    bset=set()
    for f in files:
        try: d=open(f,'rb').read()
        except IsADirectoryError: continue
        n=len(d)//4; w=struct.unpack('<%dI'%n,d[:n*4])
        for i in range(1,n-5):
            if w[i]!=0x804F00C0: continue
            st=[stage_at(w,i)]; j=i
            for reg in STAGE_REGS[1:]:
                q=next((q for q in range(j+1,min(n-5,j+24)) if w[q]==0x804F0000|reg),None)
                if q is None: break
                st.append(stage_at(w,q)); j=q
            # a block of k leading stages; the rest pass-through
            full=st+[PASS]*(6-len(st))
            bset.add(tuple(full))
    per_src[name]=bset
# misses (runtime) packed words -> raw stages via the recorded FS cache instead: use fs truth
from cache.vkch import entries
fs=[p[8:] for t,_,p in entries('cache/0004000000053F00_fs_vd_hs.vkch') if t==4]
truth=set(tuple(struct.unpack_from('<4I',c,48+16*k) for k in range(6)) for c in fs)
def norm(t):  # treat any pass-through stage alike
    out=[]
    for s in t:
        src,mod,ops,scl=s
        pt=(ops&0xF)==0 and (ops>>16&0xF)==0 and (src&0xF)==0xF and (src>>16&0xF)==0xF and (mod&0xF)==0 and (mod>>12&7)==0 and (scl&3)==0 and (scl>>16&3)==0
        out.append('PT' if pt else s)
    return tuple(out)
T=set(norm(t) for t in truth)
M=set(norm(t) for t in per_src['model']); C=set(norm(t) for t in per_src['code'])
print('truth',len(T),'| model blocks',len(M),'covers',len(T&M),'| code blocks',len(C),'covers',len(T&C),'| union covers',len(T&(M|C)))
