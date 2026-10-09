import struct, glob, collections
regs_in_mat=collections.Counter(); nmat=0
for f in glob.glob('x/ObjectData/**/*.bcmdl',recursive=True)[:300]:
    d=open(f,'rb').read(); n=len(d)//4; w=struct.unpack('<%dI'%n,d[:n*4])
    for i in range(1,n-5):
        if w[i]!=0x804F00C0: continue
        nmat+=1; seen=set()
        # walk a window around the material: decode command headers (param word before header)
        for q in range(max(1,i-400),min(n-1,i+300)):
            h=w[q]; reg=h&0xFFFF; mask=(h>>16)&0xF; cnt=(h>>20)&0xFF; seq=h>>31
            if reg<0x400 and mask in (0xF,0x3,0x1,0x8,0x7) and cnt<64 and (h&0x7FF00000)>>20==cnt and (h>>16)&0xF0==0:
                for k in range(cnt+1): seen.add(reg+(k if seq else 0))
        for r in seen: regs_in_mat[r]+=1
print('materials',nmat)
for r,c in sorted(regs_in_mat.items()):
    if c>nmat*0.3: print('reg 0x%03x in %d%%'%(r,100*c//nmat))
