    .section .text.start, "ax"
    .global _start
    .arm
_start:
    msr   cpsr_c, #0xd2          @ IRQ mode, interrupts off
    ldr   sp, =0x0380fd80
    msr   cpsr_c, #0xd3          @ SVC mode
    ldr   sp, =0x0380fe00
    msr   cpsr_c, #0xdf          @ SYS mode
    ldr   sp, =0x0380fd00
    ldr   r0, =__bss_start
    ldr   r1, =__bss_end
    mov   r2, #0
1:  cmp   r0, r1
    strlo r2, [r0], #4
    blo   1b
    bl    main7
2:  b     2b
