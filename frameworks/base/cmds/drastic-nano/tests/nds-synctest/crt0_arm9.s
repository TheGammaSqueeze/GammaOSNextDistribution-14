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
    msr   cpsr_c, #0xd2          @ IRQ mode
    ldr   sp, =0x0237e000
    msr   cpsr_c, #0xd3          @ SVC mode
    ldr   sp, =0x0237f000
    msr   cpsr_c, #0xdf          @ SYS mode
    ldr   sp, =0x02380000
    ldr   r0, =__bss_start
    ldr   r1, =__bss_end
    mov   r2, #0
1:  cmp   r0, r1
    strlo r2, [r0], #4
    blo   1b
    bl    main9
2:  b     2b
