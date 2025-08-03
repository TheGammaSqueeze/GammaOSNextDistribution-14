/*
 * Copyright (c) 2008-2015 Travis Geiselbrecht
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
#include <asm.h>
#include <arch/asm.h>
#include <arch/arm/cores.h>

/* exception handling glue.
 * NOTE: only usable on armv6+ cores
 */

#define TIMESTAMP_IRQ 0

/*
 * Use __thumb__ macro to determine where compiled C/C++ expects frame pointer
 * to be. This assumes that most compiled C/C++ code is either ARM or Thumb.
 *
 * Tracing through functions compiled for different modes is not guaranteed to
 * produce a correct trace, because code compiled for ARM saves r11 in the frame
 * record, while Thumb saves r7. So, if a Thumb function calls an ARM function,
 * the latter will save the wrong register in the frame record, and vice versa.
 * In this case, it's possible that a partial trace will be produced if the
 * frame pointer hasn't been clobbered by the time we generate the trace.
 */
#if defined(__thumb__)
frame_pointer .req r7
#else
frame_pointer .req r11
#endif

/* macros to align and unalign the stack on 8 byte boundary for ABI compliance */
.macro stack_align, tempreg
    /* make sure the stack is aligned */
    mov     \tempreg, sp
    tst     sp, #4
    subeq   sp, #4
    push    { \tempreg }

    /* tempreg holds the original stack */
.endm

.macro stack_restore, tempreg
    /* restore the potentially unaligned stack */
    pop     { \tempreg }
    mov     sp, \tempreg
.endm

/* save and disable the vfp unit */
.macro vfp_save, temp1
    /* save old fpexc */
    vmrs    \temp1, fpexc

    push    { \temp1 }

    /* hard disable the vfp unit */
    bic     \temp1, #(1<<30)
    vmsr    fpexc, \temp1
.endm

/* restore the vfp enable/disable state */
.macro vfp_restore, temp1
    /* restore fpexc */
    pop     { \temp1 }

    vmsr    fpexc, \temp1
.endm

/* Save callee trashed registers.
 * At exit r0 contains a pointer to the register frame.
 */
.macro save
    /* save spsr and r14 onto the svc stack */
    srsdb   #0x13!

    /* switch to svc mode, interrupts disabled */
#if ARM_MERGE_FIQ_IRQ
    cpsid   if,#0x13
#else
    cpsid   i,#0x13
#endif

    /* save frame record */
    push    { frame_pointer }
    mov     frame_pointer, sp

    /* save callee trashed regs and lr */
    push    { r0-r3, r12, lr }

    /* save user space sp/lr */
    sub     sp, #8
    stmia   sp, { r13, r14 }^

#if ARM_WITH_VFP
    /* save and disable the vfp unit */
    vfp_save    r0
#endif

    /* make sure the stack is 8 byte aligned */
    stack_align r0

    /* r0 now holds the pointer to the original iframe (before alignment) */
.endm

.macro save_offset, offset
    sub     lr, \offset
    save
.endm

.macro restore
    /* undo the stack alignment we did before */
    stack_restore r0

#if ARM_WITH_VFP
    /* restore the old state of the vfp unit */
    vfp_restore r0
#endif

    /* restore user space sp/lr */
    ldmia   sp, { r13, r14 }^
    add     sp, #8

    pop     { r0-r3, r12, lr }
    pop     { frame_pointer }

    /* return to whence we came from */
    rfeia   sp!
.endm

/* Save all registers.
 * At exit r0 contains a pointer to the register frame.
 */
.macro saveall
    /* save spsr and r14 onto the svc stack */
    srsdb   #0x13!

    /* switch to svc mode, interrupts disabled */
#if ARM_MERGE_FIQ_IRQ
    cpsid   if,#0x13
#else
    cpsid   i,#0x13
#endif

    /* save frame record */
    push    { frame_pointer }
    mov     frame_pointer, sp

    /* save all regs */
    push    { r0-r12, lr }

    /* save user space sp/lr */
    sub     sp, #8
    stmia   sp, { r13, r14 }^

#if ARM_WITH_VFP
    /* save and disable the vfp unit */
    vfp_save    r0
#endif

    /* make sure the stack is 8 byte aligned */
    stack_align r0

    /* r0 now holds the pointer to the original iframe (before alignment) */
.endm

.macro saveall_offset, offset
    sub     lr, \offset
    saveall
.endm

.macro restoreall
    /* undo the stack alignment we did before */
    stack_restore r0

#if ARM_WITH_VFP
    /* restore the old state of the vfp unit */
    vfp_restore r0
#endif

    /* restore user space sp/lr */
    ldmia   sp, { r13, r14 }^
    add     sp, #8

    pop     { r0-r12, r14 }
    pop     { frame_pointer }

    /* return to whence we came from */
    rfeia   sp!
.endm

FUNCTION(arm_undefined)
    save
    /* r0 now holds pointer to iframe */

    bl      arm_undefined_handler

    restore

#ifndef WITH_LIB_SYSCALL
FUNCTION(arm_syscall)
    saveall
    /* r0 now holds pointer to iframe */

    bl      arm_syscall_handler

    restoreall
#endif

FUNCTION(arm_prefetch_abort)
    saveall_offset #4
    /* r0 now holds pointer to iframe */

    bl      arm_prefetch_abort_handler

    restoreall

FUNCTION(arm_data_abort)
    str     r0, [sp]
    cpsid   i,#0x13       /* supervisor */
    mrc     p15, 0, r0, c13, c0, 4 /* r0 = tpidrprw */
    ldr     r0, [r0]

    /*
     * Reserve some stack space so exception handler at least get a chance to
     * move the stack if it faults again.
     * TODO: adjust to space needed to never trigger a stack overflow in the
     * exception handler?
     */
    add     r0, #8

    cmp     sp, r0
    bls     .Lstack_overflow
    cpsid   i,#0x17       /* abort */
    ldr     r0, [sp]

    saveall_offset #8
    /* r0 now holds pointer to iframe */

    bl      arm_data_abort_handler

    restoreall

.Lstack_overflow:
    add     sp, r0, #0x800 /* stack overflow, move stack back */
    cpsid   i,#0x17       /* abort */
    ldr     r0, [sp]

    saveall_offset #8
    /* r0 now holds pointer to iframe */

    bl      arm_data_abort_handler_stack_overflow
    /* should not return */
    b       .

FUNCTION(arm_reserved)
    b   .

FUNCTION(arm_irq)
#if TIMESTAMP_IRQ
    /* read the cycle count */
    mrc     p15, 0, sp, c9, c13, 0
    str     sp, [pc, #__irq_cycle_count - . - 8]
#endif

    save_offset    #4

    /* r0 now holds pointer to iframe */

    /* track that we're inside an irq handler */
    LOADCONST(r2, __arm_in_handler)
    mov     r1, #1
    str     r1, [r2]

    /* call into higher level code */
    bl  platform_irq

    /* clear the irq handler status */
    LOADCONST(r1, __arm_in_handler)
    mov     r2, #0
    str     r2, [r1]

    /* reschedule if the handler returns nonzero */
    cmp     r0, #0
    blne    thread_preempt

    restore

FUNCTION(arm_fiq)
#if ARM_MERGE_FIQ_IRQ
    mrs     r8, spsr
    tst     r8, #(1<<7)
    beq     arm_irq

    /*
     * IRQ were masked but we got an FIQ. This means FIQs were not masked which
     * is not a state we support (but is entered by the hardware on most
     * exceptions). Mask FIQs in spsr and return.
     */
    sub     lr, #4
    orr     r8, #(1<<6)
    msr     spsr, r8
    eret
#else
    save_offset #4
    /* r0 now holds pointer to iframe */

    bl  platform_fiq

    restore
#endif

.ltorg

#if TIMESTAMP_IRQ
DATA(__irq_cycle_count)
    .word   0
#endif

.data
DATA(__arm_in_handler)
    .word   0

DATA(arm_data_abort_state)
    .skip 4 * SMP_MAX_CPUS
