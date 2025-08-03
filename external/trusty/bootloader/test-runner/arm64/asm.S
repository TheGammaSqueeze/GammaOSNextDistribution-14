/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

.text

#define MAX_CPU_COUNT (4)
#define STACK_SIZE (4096)

#define GICD_BASE (0x08000000)
#define GICD_SGIR (0xf00)

.macro get_cpu_num, reg
    /* Get cpu number */
    mrs \reg, mpidr_el1
    bic \reg, \reg, #(1<<24) /* Clear MT */
    bic \reg, \reg, #(3<<30) /* Clear RES1 and U */
.endm

.macro get_secondary_cpu_count, reg
    mrs \reg, S3_1_C11_C0_2  /*l2ctlr_el1*/
    lsr \reg, \reg, #24
.endm

.globl _start
_start:
    /* Set exception vector base */
    adr x0, _start

    mrs x1, CurrentEL
    and x1, x1, #0xc
    cmp x1, #0x8
    bne vbar_setup_not_el2
    msr vbar_el2, x0
    b vbar_setup_done
vbar_setup_not_el2:
    msr vbar_el1, x0

vbar_setup_done:
    get_cpu_num x0

    /* Setup stack */
    mov x1, #STACK_SIZE
    mul x1, x1, x0
    adr x2, stack
    add x2, x2, x1
    adr x1, stack_space_end
    cmp x2, x1
    bhi error

    adr x1, cpu_ready
    strb w0, [x1, x0] /* cpu_ready[cpuid] = cpuid */
    dmb st
    sev
    mov sp, x2

    /* Jump to c-code */
    bl boot_arm64
    /* fall-through */

error:
    mov x0, #0x18
    adr x1, exit_params
    /* fall-through */

semihosting:
    hlt 0xf000
    ret

exit_params:
    .quad 0x20026 /* ADP_Stopped_ApplicationExit */
    .quad 2 /* exit code */

/*
 * Catch exceptions (except sync-sp_el0 as we don't use that mode and it would
 * jump to our entry point) and exit in case there is a bug.
 */
.macro exception, addr
.org \addr
    b error
.endm

exception 0x080
exception 0x100
exception 0x180
exception 0x200
exception 0x280
exception 0x300
exception 0x380
exception 0x400
exception 0x480
exception 0x500
exception 0x580
exception 0x600
exception 0x680
exception 0x700
exception 0x780

.globl trusty_idle
trusty_idle:
    // x1 = event_poll
    adr x0, skip_cpu0_wfi // x0 = &skip_cpu0_wfi
    get_cpu_num x2 // x2 = cpunum
    cbz x2, cpu0_idle // if cpunum == 0 goto cpu0_idle

    // skip_cpu0_wfi = cpunum (any value non-0 would work)
    stlr x2, [x0]

#if GIC_VERSION > 2
    // Send int 0 to cpu 0 to take it out of wfi
    ldr x4, =(0 << 24) | 1
    msr icc_sgi1r_el1, x4
#else
    // Send int 15 to cpu 0 to take it out of wfi
    ldr x3, =GICD_BASE
    ldr x4, =0x1800f
    str x4, [x3, GICD_SGIR]
#endif

    cbnz x1, no_wfi // also use event_poll to skip wfi on secondary cpus
    b wfi

cpu0_idle:
    cbz x1, wfi // only clear and skip event_poll wfi
    ldaxr x3, [x0] // x1 = skip_cpu0_wfi
    stxr w4, xzr, [x0] // skip_cpu0_wfi = 0
    cbnz x3, no_wfi
wfi:
    wfi
no_wfi:
    ret

#if GIC_VERSION > 2
.globl trusty_local_irq_disable
trusty_local_irq_disable:
    /*
     * Clear doorbell interrupt from trusty or ipi sent from secondary cores in
     * trusty_idle above.
     */
    mrs x0, icc_iar1_el1
    cmp x0, #1020
    b.hs .trusty_local_irq_disable_done
    msr icc_eoir1_el1, x0
    b trusty_local_irq_disable
.trusty_local_irq_disable_done:
    ret
#endif

.globl arch_start_secondary_cpus
arch_start_secondary_cpus:
    sub sp, sp, #16
    mov x1, #1
    get_secondary_cpu_count x2
    cmp x2, #MAX_CPU_COUNT
    blo .cpu_count_ok
    mov x2, #(MAX_CPU_COUNT - 1)
.cpu_count_ok:
    stp x1, x2, [sp]
start_secondary_cpu_loop:
    ldr x0, =0xC4000003 /* psci CPU_ON */
    adr x2, _start
    mov x3, #0
    smc 0
    cbnz x0, .start_secondary_cpu_failed
    ldp x1, x2, [sp]
    add x1, x1, #1
    stp x1, x2, [sp]
    cmp x1, x2
    bls start_secondary_cpu_loop
    add sp, sp, #16

    mov x1, #0
.wait_for_secondary_cpu_loop:
    wfe
    dmb ld
    adr x4, cpu_ready
    ldrb w3, [x4, x1]
    cmp x3, x1
    b.ne .wait_for_secondary_cpu_loop
    add x1, x1, #1
    cmp x1, x2
    bls .wait_for_secondary_cpu_loop

    mov x0, 0

.start_secondary_cpu_failed:
    ret

.data
    .space STACK_SIZE
stack:
    .space STACK_SIZE * (MAX_CPU_COUNT - 1)
stack_space_end:

cpu_ready:
    .space MAX_CPU_COUNT

    .align 3
skip_cpu0_wfi:
    .long 0
