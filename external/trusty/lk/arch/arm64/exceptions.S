/*
 * Copyright (c) 2014 Travis Geiselbrecht
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
#include <arch/asm_macros.h>

.section .text.boot.vectab
.align 12

#define lr x30
#define regsave_long_offset (30 * 8)
#define regsave_short_offset (20 * 8)

.macro regsave, regsave_offset:req, reset_stack:req, double_fault=0
.if \double_fault
    /*
     * Use elr of original fault instead of new fault to get a more useful
     * stack trace. Report new elr as lr instead.
     */
    mrs     lr, elr_el1
.else
    /* Save x0 and x1 in sp_el1 scratch space so we have some free registers */
    stp     x0, x1, [sp]
    mrs     x1, elr_el1 /* Save elr_el1 early in x1 in case we fault again */
.endif

    /* x0 = new sp_el0. Instructions following regsave may rely on property. */
.if \reset_stack
    mrs     x0, tpidr_el1
    ldr     x0, [x0, 8]
    bic     x0, x0, #0xf /* align stack */
.else
    mrs     x0, sp_el0
.endif

.if \regsave_offset == regsave_long_offset
    push    x28, lr, rsp=x0, prealloc=32
    push    x26, x27, rsp=x0
    push    x24, x25, rsp=x0
    push    x22, x23, rsp=x0
    push    x20, x21, rsp=x0
    push    x18, x19, rsp=x0
.elseif \regsave_offset == regsave_short_offset
    push    x18, lr, rsp=x0, prealloc=32
.else
    .error  "regsave: unsupported regsave_offset, \regsave_offset"
.endif

    push    x16, x17, rsp=x0
    push    x14, x15, rsp=x0
    push    x12, x13, rsp=x0
    push    x10, x11, rsp=x0
    push    x8, x9, rsp=x0
    push    x6, x7, rsp=x0
    push    x4, x5, rsp=x0
    push    x2, x3, rsp=x0

    /*
     * Push entry values of x0 and x1 saved in sp_el1 scratch space. If we fault
     * again below this point, the new iframe will not have the correct values
     * for x2 and x3, but we should still be able to get a stack trace. spsr
     * will also be wrong for double faults as the code below will read the new
     * spsr not the one from the original exception.
     */
    ldp     x2, x3, [sp]
    push    x2, x3, rsp=x0

    mrs     x2, sp_el0
    mrs     x3, spsr_el1
    stp     x2, x3, [x0, #\regsave_offset]
    stp     fp, x1, [x0, #\regsave_offset + 16]
    msr     spsel, #0
    /* instructions following regsave may rely on x0 holding xp to save space */
    mov     sp, x0
    add     fp, sp, #\regsave_offset + 16
#if KERNEL_SCS_ENABLED
.if \reset_stack
    mrs     x18, tpidr_el1
    ldr     x18, [x18, 24] /* thread_t.shadow_stack */
.endif
#endif
.endm

.macro regsave_long, reset_stack, double_fault=0
    regsave regsave_offset=regsave_long_offset, reset_stack=\reset_stack, \
            double_fault=\double_fault
.endm

.macro regsave_short, reset_stack
    regsave regsave_offset=regsave_short_offset, reset_stack=\reset_stack
.endm

.macro regrestore, regsave_offset:req
    mov     lr, sp

    /*
     * Switch to EL1h so a fault after restoring sp_el0 to a user-space stack
     * pointer does not cause the kernel fault handler to run on the user-space
     * stack.
     */
    msr     spsel, #1

    ldp     x0, x2, [lr, #\regsave_offset]
    ldp     fp, x1, [lr, #\regsave_offset + 16]
    msr     sp_el0, x0
    msr     elr_el1, x1
    msr     spsr_el1, x2
    pop     x0, x1, rsp=lr
    pop     x2, x3, rsp=lr
    pop     x4, x5, rsp=lr
    pop     x6, x7, rsp=lr
    pop     x8, x9, rsp=lr
    pop     x10, x11, rsp=lr
    pop     x12, x13, rsp=lr
    pop     x14, x15, rsp=lr
    pop     x16, x17, rsp=lr
.if \regsave_offset == regsave_long_offset
    pop     x18, x19, rsp=lr
    pop     x20, x21, rsp=lr
    pop     x22, x23, rsp=lr
    pop     x24, x25, rsp=lr
    pop     x26, x27, rsp=lr
    ldp     x28, lr, [lr]
.elseif \regsave_offset == regsave_short_offset
    ldp     x18, lr, [lr]
.else
    .error  "regsave: unsupported regsave_offset, \regsave_offset"
.endif
.endm

.macro regrestore_long
    regrestore regsave_offset=regsave_long_offset
.endm

.macro regrestore_short
    regrestore regsave_offset=regsave_short_offset
.endm

.macro invalid_exception, which, reset_stack, double_fault=0
    regsave_long reset_stack=\reset_stack, double_fault=\double_fault
    /* x0 holds sp after regsave */
    mov x1, #\which
    bl  arm64_invalid_exception
.if \reset_stack == 0 /* do we have room for the following instruction? */
    b . /* unreachable: arm64_invalid_exception calls _panic */
.endif
.endm

.macro irq_exception, reset_stack
    regsave_short reset_stack=\reset_stack
#if !ARM_MERGE_FIQ_IRQ
    msr daifclr, #1 /* reenable fiqs once elr and spsr have been saved */
#endif
    /* x0 holds sp after regsave */
    bl  platform_irq
    cbz x0, .Lirq_exception_no_preempt\@
    bl  thread_preempt
.Lirq_exception_no_preempt\@:
#if !ARM_MERGE_FIQ_IRQ
    msr daifset, #1 /* disable fiqs to protect elr and spsr restore */
#endif
    b   arm64_exc_shared_restore_short
.endm

.macro fiq_exception, reset_stack
#if ARM_MERGE_FIQ_IRQ
    irq_exception reset_stack=\reset_stack
#else
    regsave_short reset_stack=\reset_stack
    /* x0 holds sp after regsave */
    bl  platform_fiq
    b  arm64_exc_shared_restore_short
#endif
.endm

FUNCTION(arm64_exception_base)

/* exceptions from current EL, using SP0 */
LOCAL_FUNCTION(arm64_sync_exc_current_el_SP0)
    regsave_long reset_stack=0
    /* x0 holds sp after regsave */
    mov x1, #0 /* from_lower = false */
    b arm64_sync_exc_shared

.org 0x080
LOCAL_FUNCTION(arm64_irq_current_el_SP0)
    irq_exception reset_stack=0

.org 0x100
LOCAL_FUNCTION(arm64_fiq_current_el_SP0)
    fiq_exception reset_stack=0

.org 0x180
LOCAL_FUNCTION(arm64_err_exc_current_el_SP0)
    invalid_exception 3, reset_stack=0

/* exceptions from current EL, using SPx */
.org 0x200
LOCAL_FUNCTION(arm64_sync_exc_current_el_SPx)
    /*
     * This fault will occur of we the kernel stack pointer is not valid when
     * we try to push or pop the iframe. Reset the stack pointer in this case
     * so panic can run. This should work for faults caused by stack overflows,
     * but not if the stack in the thread struct is also bad.
     */
    invalid_exception 0x10, reset_stack=1, double_fault=1

.org 0x280
LOCAL_FUNCTION(arm64_irq_current_el_SPx)
    invalid_exception 0x11, reset_stack=0

.org 0x300
LOCAL_FUNCTION(arm64_fiq_current_el_SPx)
    invalid_exception 0x12, reset_stack=0

.org 0x380
LOCAL_FUNCTION(arm64_err_exc_current_el_SPx)
    invalid_exception 0x13, reset_stack=0

/* exceptions from lower EL, running arm64 */
.org 0x400
LOCAL_FUNCTION(arm64_sync_exc_lower_el_64)
    /*
     * Reset the stack pointer for this and all other exceptions from user-space
     * since the kernel stack should be empty when user-space is running, and we
     * don't want to use the user-space stack, which is the current stack
     * pointer, while in the kernel. The user-space stack pointer will be saved
     * on entry and restored on return.
     */
    regsave_long reset_stack=1
    /* x0 holds sp after regsave */
    mov x1, #1 /* from_lower = true */
    b arm64_sync_exc_shared

.org 0x480
LOCAL_FUNCTION(arm64_irq_lower_el_64)
    irq_exception reset_stack=1

.org 0x500
LOCAL_FUNCTION(arm64_fiq_lower_el_64)
    fiq_exception reset_stack=1

.org 0x580
LOCAL_FUNCTION(arm64_err_exc_lower_el_64)
    invalid_exception 0x23, reset_stack=1

/* exceptions from lower EL, running arm32 */
.org 0x600
LOCAL_FUNCTION(arm64_sync_exc_lower_el_32)
    regsave_long reset_stack=1
    /* x0 holds sp after regsave */
    mov x1, #1 /* from_lower = true */
    b arm64_sync_exc_shared

.org 0x680
LOCAL_FUNCTION(arm64_irq_lower_el_32)
    irq_exception reset_stack=1

.org 0x700
LOCAL_FUNCTION(arm64_fiq_lower_el_32)
    fiq_exception reset_stack=1

.org 0x780
LOCAL_FUNCTION(arm64_err_exc_lower_el_32)
    invalid_exception 0x33, reset_stack=1

LOCAL_FUNCTION(arm64_sync_exc_shared)
    bl arm64_sync_exception /* x0 and x1 must be set by caller */
    regrestore_long
    eret

LOCAL_FUNCTION(arm64_exc_shared_restore_short)
    regrestore_short
    eret

/*
 * void arm64_enter_uspace(ulong arg0, vaddr_t entry_point,
 *                         vaddr_t user_stack_top, vaddr_t shadow_stack_base,
 *                         uint64_t spsr)
 */
FUNCTION(arm64_enter_uspace)
    /*
     * Put input registers (x1-x4) into their destination registers before
     * zeroing them. Register x0 already contains the desired value (arg0).
     */
    mov    x13, x2      /* AArch32 SP_usr = user_stack_top */
    msr    spsel, #1    /* Switch to EL1h before setting a user-space sp */
    msr    sp_el0, x2   /* AArch64 SP_usr = user_stack_top  */
    mov    x18, x3      /* AArch64 shadow stack = shadow_stack_base */
    msr    elr_el1, x1  /* entry_point */
    msr    spsr_el1, x4 /* spsr */

    /* zero remaining registers */
    mov    x1, xzr
    mov    x2, xzr
    mov    x3, xzr
    mov    x4, xzr
    mov    x5, xzr
    mov    x6, xzr
    mov    x7, xzr
    mov    x8, xzr
    mov    x9, xzr
    mov    x10, xzr
    mov    x11, xzr
    mov    x12, xzr
    mov    x14, xzr     /* AArch32 LR_usr */
    mov    x15, xzr
    mov    x16, xzr
    mov    x17, xzr
    mov    x19, xzr
    mov    x20, xzr
    mov    x21, xzr
    mov    x22, xzr
    mov    x23, xzr
    mov    x24, xzr
    mov    x25, xzr
    mov    x26, xzr
    mov    x27, xzr
    mov    x28, xzr
    mov    x29, xzr
    mov    x30, xzr     /* AArch64 LR_usr */

    eret
