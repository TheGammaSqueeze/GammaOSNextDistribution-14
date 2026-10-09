import struct, itertools
exec(open('miss57.py').read().split("uses=collections.Counter()")[0])
d=open('x/code.bin','rb').read()
STAGE=[0xC0,0xC8,0xD0,0xD8,0xF0,0xF8]
def scan(a,b):
    out=[]; i=a
    while i<b:
        w=struct.unpack_from('<I',d,i)[0]
        if (w&0xFFFFFF00)==0x804F0000 and (w&0xFF) in STAGE:
            src=struct.unpack_from('<I',d,i-4)[0]; mod,ops,col,scl=struct.unpack_from('<4I',d,i+4)
            out.append((i,w&0xFF,(src,mod,ops,scl))); i+=24
        else: i+=4
    return out
cmds=scan(0x2aec00,0x2aef00)+scan(0x2f0700,0x2f0e00)
stage0=sorted(set(s for a,r,s in cmds if r==0xC0))
others=[(a,r,s) for a,r,s in cmds if r!=0xC0]
# group non-C0 commands into tails: a new tail starts when the address gap is large or the register order restarts
tails=[]; cur={}; last=None; lastreg=-1
for a,r,s in others:
    if last is not None and (a-last>0x30 or r<=lastreg):
        tails.append(cur); cur={}
    cur[r]=s; last=a; lastreg=r
tails.append(cur)
# also every suffix of a tail (the library may start the tail at a later stage)
variants=set()
for t in tails:
    regs=sorted(t)
    for k in range(len(regs)):
        variants.add(tuple((r,t[r]) for r in regs[k:]))
variants.add(())
PASS=(0x0F000F,0,0,0)
gen=set()
for s0 in stage0+[PASS]:
    for v in variants:
        st={0xC0:s0}; st.update(dict(v))
        gen.add(tuple(st.get(r,PASS) for r in STAGE))
print('stage-0 options',len(stage0),'tails',len(tails),'tail variants',len(variants),'generated setups',len(gen))
G=set(norm(t) for t in gen)
print('of the 57 unexplained setups, generated:',sum(1 for t in rest if norm(t) in G))
print('of all 174 used setups, generated:',sum(1 for t in truth if norm(t) in G))
print('union with model+code blocks:',len(set(norm(t) for t in truth) & (G|covered)),'of 174')
left=[t for t in rest if norm(t) not in G]
for t in left[:6]:
    print(' | '.join(f"{['C0','C8','D0','D8','F0','F8'][k]}:{'--' if pt(s) else fmt(s)}" for k,s in enumerate(t)))
    print('   const/scale words:', ' '.join('%08x/%08x'%(s[1],s[3]) for s in t if not pt(s)))
print('table stage0 example mod/scale', ['%08x/%08x'%(s[1],s[3]) for s in stage0[:3]])
def shape(t):  # sources + ops only, pass-through kept
    return tuple('PT' if pt(s) else (s[0],s[2]) for s in t)
GS=set(shape(t) for t in gen)
print('unexplained setups matching a table setup when modifiers/scale are ignored:',sum(1 for t in rest if shape(t) in GS),'of',len(rest))
import collections
mods=collections.Counter()
for t in rest:
    for s in t:
        if not pt(s): mods[s[1]]+=1
print('distinct modifier words in them:',len(mods), mods.most_common(8))
scl=collections.Counter(s[3] for t in rest for s in t if not pt(s)); print('scale words',dict(scl))
