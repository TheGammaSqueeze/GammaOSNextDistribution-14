import struct
exec(open('tevmat.py').read().split("print('ROM materials")[0])
def norm(c):
    c=bytearray(c); c[4:32]=bytes(28); c[144:168]=bytes(24); return bytes(c)
keys=[norm(c) for c in fs]            # recording order ~ play order
seen=[];s=set()
for k in keys:
    if k not in s: s.add(k); seen.append(k)
N=len(seen)
for frac in (0.1,0.2,0.3,0.5):
    early=seen[:int(N*frac)]; later=seen[int(N*frac):]
    ctx=set(k[:48]+k[144:] for k in early)          # everything except the 6 TEV stages
    pred=set(c[:48]+t+c[48:] for c in ctx for t in mats)
    hit=sum(1 for k in later if (k[:48]+k[48:144]+k[144:]) in set(p[:48]+p[48:144]+p[144:] for p in []) )
    predset=set(c[:48]+t+c[48:] for c in ctx for t in mats)
    hit=sum(1 for k in later if k in predset)
    print(f'learn from first {int(frac*100)}% ({len(early)} shaders): contexts {len(ctx)}, predicted {len(predset)}, later shaders predicted {hit} of {len(later)} ({100*hit/max(1,len(later)):.0f}%)')
