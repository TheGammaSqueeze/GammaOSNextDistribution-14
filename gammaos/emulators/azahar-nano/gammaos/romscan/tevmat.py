import struct, glob, collections
from cache.vkch import entries
STAGE_REGS=[0xC0,0xC8,0xD0,0xD8,0xF0,0xF8]
fs=[p[8:] for t,_,p in entries('cache/0004000000053F00_fs_vd_hs.vkch') if t==4]
truth=set(bytes(c[48:144]) for c in fs)   # 6 stages x (src,mod,ops,scale)
print('ground truth distinct TEV configs',len(truth))
mats=set(); files=0
for f in glob.glob('x/**/*',recursive=True):
    try: d=open(f,'rb').read()
    except IsADirectoryError: continue
    files+=1
    n=len(d)//4
    w=struct.unpack('<%dI'%n,d[:n*4])
    # find stage-0 commands; a material = stage commands for C0..F8 in increasing order nearby
    for i in range(1,n-5):
        if w[i]==0x804F00C0:
            st={}
            j=i
            for k,reg in enumerate(STAGE_REGS):
                hdr=0x804F0000|reg
                # search forward within 64 words for this stage header
                found=None
                for q in range(j,min(n-5,j+64)):
                    if w[q]==hdr: found=q;break
                if found is None: break
                src=w[found-1]; mod,ops,col,scl=w[found+1:found+5]
                st[k]=(src,mod,ops,scl); j=found+1
            if len(st)==6:
                mats.add(b''.join(struct.pack('<4I',*st[k]) for k in range(6)))
print('ROM materials with all 6 stage commands',len(mats))
hit=truth & mats
print('ground truth TEV configs found in ROM materials:',len(hit),'of',len(truth))
miss=truth-mats
def split(b): return [struct.unpack_from('<4I',b,16*k) for k in range(6)]
diffpos=collections.Counter(); close=0
for t in miss:
    ts=split(t); best=None;bd=99
    for m in mats:
        ms=split(m); d=sum(1 for a,b in zip(ts,ms) if a!=b)
        if d<bd: bd=d;best=ms
    if bd<=1:
        close+=1
        for k,(a,b) in enumerate(zip(ts,best)):
            if a!=b:
                for f,name in enumerate(('src','mod','ops','scl')):
                    if a[f]!=b[f]: diffpos[(k,name)]+=1
print('misses',len(miss),'within one stage of a ROM material',close)
print('which field differs:',diffpos.most_common(10))
# how many misses are "simple" (1 active stage) -> likely layout/effect shaders
act=lambda b: sum(1 for s in split(b) if not (s[2]==0 and (s[0]&0xF)==0xF and (s[0]>>16&0xF)==0xF))
print('active stage counts of misses',collections.Counter(act(t) for t in miss))
allstages=set()
for f in glob.glob('x/**/*',recursive=True):
    try: d=open(f,'rb').read()
    except IsADirectoryError: continue
    n=len(d)//4; w=struct.unpack('<%dI'%n,d[:n*4])
    for i in range(1,n-5):
        if (w[i]&0xFFFFFF00)==0x804F0000 and (w[i]&0xFF) in STAGE_REGS:
            allstages.add((w[i-1],)+tuple(w[i+1:i+3])+(w[i+4],))
mstage=collections.Counter()
for t in miss:
    for s in split(t):
        if s[2]==0 and (s[0]&0xF)==0xF and (s[0]>>16&0xF)==0xF: continue
        mstage['present' if s in allstages else 'absent']+=1
print('active stages of missed configs:',dict(mstage))
