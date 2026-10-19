    .section .text.start, "ax"
    .global _start
    .arm
_start:
    msr   cpsr_c, #0xd3          @ SVC mode, interrupts off
    @ A loader may hand over with the caches and the protection unit on. The ARM7 writes the
    @ recording straight to main RAM, so the ARM9 must not read it through a stale data cache:
    @ write back and invalidate the whole data cache (4 KB, 4 ways x 32 sets of 32 bytes), drain
    @ the write buffer, then switch the caches and the protection unit off.
    mov   r1, #0
1:  mov   r0, #0
2:  orr   r2, r1, r0, lsl #5
    mcr   p15, 0, r2, c7, c14, 2  @ clean and invalidate data cache line by set/way
    add   r0, r0, #1
    cmp   r0, #32
    blo   2b
    adds  r1, r1, #0x40000000
    bne   1b
    mov   r0, #0
    mcr   p15, 0, r0, c7, c10, 4  @ drain write buffer
    mrc   p15, 0, r0, c1, c0, 0
    bic   r0, r0, #0x1000         @ instruction cache off
    bic   r0, r0, #0x0005         @ data cache and protection unit off
    mcr   p15, 0, r0, c1, c0, 0
    mov   r0, #0
    mcr   p15, 0, r0, c7, c5, 0   @ invalidate instruction cache
    mcr   p15, 0, r0, c7, c6, 0   @ invalidate data cache
    @ Stacks in 16 KB of data TCM at 0x0b000000: main RAM is slow on hardware and every push
    @ and pop of a stack there costs a bus access.
    ldr   r0, =0x0b00000a         @ DTCM base 0x0b000000, size 16 KB
    mcr   p15, 0, r0, c9, c1, 0
    mrc   p15, 0, r0, c1, c0, 0
    orr   r0, r0, #0x10000        @ DTCM on
    bic   r0, r0, #0x20000        @ DTCM normal (not load) mode
    mcr   p15, 0, r0, c1, c0, 0
    msr   cpsr_c, #0xd2          @ IRQ mode
    ldr   sp, =0x0b003c00
    msr   cpsr_c, #0xd3          @ SVC mode
    ldr   sp, =0x0b003e00
    msr   cpsr_c, #0xdf          @ SYS mode
    ldr   sp, =0x0b003b00
    ldr   r0, =__bss_start
    ldr   r1, =__bss_end
    mov   r2, #0
1:  cmp   r0, r1
    strlo r2, [r0], #4
    blo   1b
    bl    main9
2:  b     2b
