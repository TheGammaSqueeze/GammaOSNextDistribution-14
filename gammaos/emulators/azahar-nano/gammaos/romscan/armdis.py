import struct, sys
from capstone import *
d=open('x/code.bin','rb').read(); BASE=0x100000
md=Cs(CS_ARCH_ARM, CS_MODE_ARM); md.detail=False
def users(pool):
    out=[]
    for a in range(max(BASE,pool-4096),pool,4):
        ins=d[a-BASE:a-BASE+4]
        for i in md.disasm(ins,a):
            if i.mnemonic.startswith('ldr') and '[pc' in i.op_str:
                try:
                    off=int(i.op_str.split('#')[1].rstrip(']'),0)
                except Exception: continue
                if a+8+off==pool: out.append(a)
    return out
def dump(a,b):
    for i in md.disasm(d[a-BASE:b-BASE],a):
        print(f"{i.address:#08x}: {i.mnemonic:8s} {i.op_str}")
if __name__=='__main__':
    pool=int(sys.argv[1],16); u=users(pool); print('users of',hex(pool),[hex(x) for x in u])
    for x in u[:1]:
        dump(x-0x80,x+0x100)
