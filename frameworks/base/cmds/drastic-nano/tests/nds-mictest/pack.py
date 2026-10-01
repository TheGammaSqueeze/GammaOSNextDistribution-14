#!/usr/bin/env python3
"""Pack arm9.bin + arm7.bin into an NDS ROM (header, minimal FNT/FAT).

usage: pack.py arm9.bin arm7.bin out.nds [logo-source.nds]

The Nintendo logo is not included here: pass any DS ROM you own as the last argument
to copy its logo bytes (0xC0..0x15B), which some loaders check. Without it the logo
area is left zero (drastic-nano does not need it).
"""
import struct, sys

def crc16(data, crc=0xFFFF):
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return crc & 0xFFFF

def align(n, a):
    return (n + a - 1) // a * a

arm9 = open(sys.argv[1], "rb").read()
arm7 = open(sys.argv[2], "rb").read()
out = sys.argv[3]
logo = None
if len(sys.argv) > 4:
    logo = open(sys.argv[4], "rb").read(0x200)[0xC0:0x15C]

ARM9_OFF = 0x8000                     # past the secure area, so nothing tries to decrypt it
ARM9_RAM = 0x02000000
arm7_off = align(ARM9_OFF + len(arm9), 0x200)
ARM7_RAM = 0x037F8000
fnt_off = align(arm7_off + len(arm7), 0x200)
fnt = struct.pack("<IHH", 8, 0xF000, 1) + b"\x00"   # root directory only, no files
fat_off = align(fnt_off + len(fnt), 0x200)
fat = b""
end = align(fat_off + max(len(fat), 8), 0x200)

h = bytearray(0x4000)
h[0x000:0x00C] = b"GAMMAOS MIC "
h[0x00C:0x010] = b"GMIC"
h[0x010:0x012] = b"00"
h[0x014] = max(0, (end - 1).bit_length() - 17)       # device capacity: 128 KB << n
struct.pack_into("<IIII", h, 0x020, ARM9_OFF, ARM9_RAM, ARM9_RAM, len(arm9))
struct.pack_into("<IIII", h, 0x030, arm7_off, ARM7_RAM, ARM7_RAM, len(arm7))
struct.pack_into("<IIII", h, 0x040, fnt_off, len(fnt), fat_off, len(fat))
struct.pack_into("<II", h, 0x060, 0x00586000, 0x001808F8)   # ROM control (normal, KEY1)
struct.pack_into("<H", h, 0x06E, 0x0D7E)                     # secure transfer timeout
struct.pack_into("<II", h, 0x080, end, 0x4000)               # used ROM size, header size
if logo:
    h[0x0C0:0x15C] = logo
struct.pack_into("<H", h, 0x15C, 0xCF56)                     # logo CRC of the real logo
struct.pack_into("<H", h, 0x15E, crc16(h[0:0x15E]))

rom = bytearray(end)
rom[0:0x4000] = h
rom[ARM9_OFF:ARM9_OFF + len(arm9)] = arm9
rom[arm7_off:arm7_off + len(arm7)] = arm7
rom[fnt_off:fnt_off + len(fnt)] = fnt
open(out, "wb").write(rom)
print("%s: %d bytes (arm9 %d at 0x%x, arm7 %d at 0x%x)" % (out, len(rom), len(arm9), ARM9_OFF, len(arm7), arm7_off))
