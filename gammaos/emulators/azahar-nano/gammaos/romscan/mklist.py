import struct, sys
exec(open('tevmat.py').read().split("print('ROM materials")[0])
def pack(stages):
    words=[]
    for st,(src,mod,ops,scl) in enumerate(stages):
        srcs=[(src>>(4*k))&0xF for k in (0,1,2)]+[(src>>(16+4*k))&0xF for k in (0,1,2)]
        if st==0:
            for k in (0,1):
                if srcs[k]==0xF: srcs[k]=srcs[2]
            for k in (3,4):
                if srcs[k]==0xF: srcs[k]=srcs[5]
        cop=ops&0xF; aop=(ops>>16)&0xF
        cm=[(mod>>(4*k))&0xF for k in (0,1,2)]; am=[(mod>>(12+4*k))&7 for k in (0,1,2)]
        cs=scl&3; as_=(scl>>16)&3
        cmul=(1<<cs) if cs<3 else 1; amul=(1<<as_) if as_<3 else 1
        orig_cs1=src&0xF; orig_as1=(src>>16)&0xF
        passthru = cop==0 and aop==0 and orig_cs1==0xF and orig_as1==0xF and cm[0]==0 and am[0]==0 and cmul==1 and amul==1
        wa=srcs[0]|srcs[1]<<4|srcs[2]<<8|srcs[3]<<12|srcs[4]<<16|srcs[5]<<20|cop<<24|aop<<28
        wb=cm[0]|cm[1]<<4|cm[2]<<8|am[0]<<12|am[1]<<15|am[2]<<18|cs<<21|as_<<23|((0 if passthru else 1)<<27)
        words+= [wa,wb]
    return words
out=open(sys.argv[1],'wb'); n=0
for m in mats:
    stages=[struct.unpack_from('<4I',m,16*k) for k in range(6)]
    w=pack(stages)
    for variant in (0,16):   # unlit, lit (flat texture 0, vertex depth on)
        out.write(struct.pack('<13I',variant,*w)); n+=1
print('entries',n)
