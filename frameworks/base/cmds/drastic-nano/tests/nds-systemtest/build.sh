#!/bin/bash
# Build the DS system test ROM with arm-none-eabi-gcc (no devkitPro needed).
# usage: build.sh [out.nds] [logo-source.nds]
set -e
cd "$(dirname "$0")"
OUT="${1:-gammaos-systest.nds}"
B=build; mkdir -p $B
CF="-O2 -marm -ffreestanding -fno-builtin -nostdlib -Wall -Wextra"
arm-none-eabi-gcc $CF -mcpu=arm946e-s -c crt0_arm9.s -o $B/crt0_arm9.o
arm-none-eabi-gcc $CF -mcpu=arm946e-s -c arm9.c -o $B/arm9.o
arm-none-eabi-gcc $CF -mcpu=arm946e-s -T arm9.ld $B/crt0_arm9.o $B/arm9.o -lgcc -o $B/arm9.elf
arm-none-eabi-gcc $CF -mcpu=arm7tdmi -c crt0_arm7.s -o $B/crt0_arm7.o
arm-none-eabi-gcc $CF -mcpu=arm7tdmi -c arm7.c -o $B/arm7.o
arm-none-eabi-gcc $CF -mcpu=arm7tdmi -T arm7.ld $B/crt0_arm7.o $B/arm7.o -lgcc -o $B/arm7.elf
arm-none-eabi-objcopy -O binary $B/arm9.elf $B/arm9.bin
arm-none-eabi-objcopy -O binary $B/arm7.elf $B/arm7.bin
python3 pack.py $B/arm9.bin $B/arm7.bin "$OUT" $2
