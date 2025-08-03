/*
 * Copyright (c) 2013-2015, Google Inc. All rights reserved
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
#include <lib/sm/monitor.h>
#include <lib/sm/smcall.h>
#include <lib/sm/sm_err.h>

#include <kernel/vm.h>

/*
 * Monitor stacks:
 *  secure context
 *  non-secure context
 *
 * Context:
 *  mon_lr
 *  usr/sys_sp, usr/sys_lr
 *  und_spsr, und_sp, und_lr
 *  abt_spsr, abt_sp, abt_lr
 *  svc_spsr, svc_sp, svc_lr
 *  irq_spsr, irq_sp, irq_lr
 *  fiq_spsr, fiq_r8-fiq_lr
 *  r4-r11, lr
 *  r0-r3, ip
 *  lr, spsr
 *
 */

#define CONTEXT_SIZE_RETURN (2 * 4)
#define CONTEXT_SIZE_SCRATCH_GEN_REGS ((5 + 9) * 4)
#define CONTEXT_SIZE_MODE_REGS_MON_RETURN ((8 + 3 + 3 + 3 + 3 + 2 + 1) * 4)
#define CONTEXT_SIZE (CONTEXT_SIZE_RETURN + CONTEXT_SIZE_SCRATCH_GEN_REGS + CONTEXT_SIZE_MODE_REGS_MON_RETURN)
#define MONITOR_STACK_SIZE (CONTEXT_SIZE * 2)

.macro SAVE_CONTEXT_RETURN
	srsdb	sp!, #MODE_MON	/* srsfd alias not recognized by current assembler */
.endm

.macro SAVE_CONTEXT_GEN_REGS
	push	{r4-r11, lr}
.endm

.macro SAVE_CONTEXT_SCRATCH_REGS
	push	{r0-r3, ip}
.endm

.macro SAVE_CONTEXT
	SAVE_CONTEXT_RETURN
	SAVE_CONTEXT_SCRATCH_REGS
	SAVE_CONTEXT_GEN_REGS
.endm

.macro SAVE_CONTEXT_MODE_REGS
	mov	r4, sp

	cps	#MODE_FIQ
	mrs	r5, spsr
	stmfd	r4!, {r5, r8-lr}

	cps	#MODE_IRQ
	mrs	r5, spsr
	stmfd	r4!, {r5, sp, lr}

	cps	#MODE_SVC
	mrs	r5, spsr
	stmfd	r4!, {r5, sp, lr}

	cps	#MODE_ABT
	mrs	r5, spsr
	stmfd	r4!, {r5, sp, lr}

	cps	#MODE_UND
	mrs	r5, spsr
	stmfd	r4!, {r5, sp, lr}

	cps	#MODE_SYS
	stmfd	r4!, {sp, lr}

	cps	#MODE_MON
	mov	sp, r4
.endm

.macro SAVE_RETURN_ADDR reg
	push	{\reg}
.endm

.macro RESTORE_RETURN_ADDR reg
	pop	{\reg}
.endm

.macro RESTORE_CONTEXT_MODE_REGS
	mov	r4, sp

	cps	#MODE_SYS
	ldmfd	r4!, {sp, lr}

	cps	#MODE_UND
	ldmfd	r4!, {r5, sp, lr}
	msr	spsr, r5

	cps	#MODE_ABT
	ldmfd	r4!, {r5, sp, lr}
	msr	spsr, r5

	cps	#MODE_SVC
	ldmfd	r4!, {r5, sp, lr}
	msr	spsr, r5

	cps	#MODE_IRQ
	ldmfd	r4!, {r5, sp, lr}
	msr	spsr, r5

	cps	#MODE_FIQ
	ldmfd	r4!, {r5, r8-lr}
	msr	spsr, r5

	cps	#MODE_MON
	mov	sp, r4
.endm

.macro RESTORE_CONTEXT_GEN_REGS
	pop	{r4-r11, lr}
.endm

.macro RESTORE_CONTEXT_SCRATCH_REGS
	pop	{r0-r3, ip}
.endm

.macro RESTORE_CONTEXT_AND_RETURN
	RESTORE_CONTEXT_GEN_REGS
	add	sp, sp, #5 * 4
	rfefd	sp!
.endm

.p2align 5
.globl monitor_vector_table
monitor_vector_table:
	nop				/* RESET	*/
	b	.			/* UNDEF	*/
	b	.Lmon_smcall		/* SWI		*/
	b	.			/* IABORT	*/
	b	.			/* DABORT	*/
	nop				/* reserved	*/
	b	.			/* IRQ		*/
	b	.Lmon_fiq_entry		/* FIQ		*/

/*
 * Called in monitor mode
 * return address in mon-lr
 * ns-return address in svc-lr
 */

FUNCTION(monitor_reset)
	/* figure out our cpu number */
	mrc     p15, 0, r11, c0, c0, 5 /* read MPIDR */

	/* mask off the bottom 12 bits to test cluster number:cpu number */
	ubfx    r11, r11, #0, #12

	/* return if cpu >= SMP_MAX_CPUS */
	cmp	r11, #SMP_MAX_CPUS
	bxge	lr

	/* find cpu specifc stack offset */
	mov	ip, #MONITOR_STACK_SIZE
	mul	r11, r11,ip

	adr	sp, monitor_reset	/* sp = paddr */
	ldr	ip, =monitor_reset	/* ip = vaddr */
	sub	ip, sp, ip		/* ip = phys_offset */
	ldr	sp, =mon_ns_stack_top	/* sp = ns_stack vaddr */
	add	sp, sp, ip		/* sp = ns_stack paddr */

	/* apply per-cpu stack offset */
	sub	sp, sp, r11

	/* Save non-secure return address, mode and registers */
	cps	#MODE_SVC
	msr	spsr_cfsx, #MODE_SVC_IRQ_DISABLED
	SAVE_CONTEXT_RETURN
	cps	#MODE_MON

	SAVE_CONTEXT_SCRATCH_REGS
	SAVE_CONTEXT_GEN_REGS
	SAVE_CONTEXT_MODE_REGS
	ldr	r4, =platform_mon_initial_ns_return
	SAVE_RETURN_ADDR r4

	sub	sp, sp, ip	/* sp = sp vaddr */

	/* Initialize NS mode CPU context registers */
	mrc	p15, 0, r4, c12, c0, 0	/* r4 = VBAR */
	mrc	p15, 0, r5, c2, c0, 0	/* r5 = TTBR0 */
	mrc	p15, 0, r6, c2, c0, 1	/* r6 = TTBR1 */
	mrc	p15, 0, r7, c3, c0, 0	/* r7 = DACR */
	mrc	p15, 0, r8, c1, c0, 0	/* r8 = SCTLR */

	SWITCH_SCR_TO_NONSECURE r9

	mcr	p15, 0, r4, c12, c0, 0	/* VBAR = r4 */
	mcr	p15, 0, r5, c2, c0, 0	/* TTBR0 = r5 */
	mcr	p15, 0, r6, c2, c0, 1	/* TTBR1 = r6 */
	mcr	p15, 0, r7, c3, c0, 0	/* DACR = r7 */
	mcr	p15, 0, r8, c1, c0, 0	/* SCTLR = r8 */

	SWITCH_SCR_TO_SECURE r9

	bx	lr

FUNCTION(monitor_init_mmu_on)
	bx	lr

.Lmon_invalid_ns_return_addr:
	b	.

FUNCTION(mon_initial_ns_return)
	cmp	lr, #~0
	strne	lr, [sp, #CONTEXT_SIZE_SCRATCH_GEN_REGS]
	b	.Lmon_smcall_initial_ns_return

/* direction in flags */
.Lmon_smcall_from_secure:
	adreq	lr, .Lmon_smcall_secure_return
.Lmon_context_switch:
	SAVE_CONTEXT_MODE_REGS
	SAVE_RETURN_ADDR lr

	addeq	sp, sp, #(CONTEXT_SIZE)	/* secure => non-secure */
	subne	sp, sp, #(CONTEXT_SIZE)	/* non-secure => secure */

	RESTORE_RETURN_ADDR lr
	RESTORE_CONTEXT_MODE_REGS
	bx	lr

.Lmon_smcall:
	SAVE_CONTEXT

	mrc	p15, 0, r4, c1, c1, 0	/* r4 = SCR */
	tst	r4, #0x1
	beq	.Lmon_smcall_from_secure

	SWITCH_SCR_TO_SECURE r4
	bl	.Lmon_context_switch
.Lmon_smcall_initial_ns_return:
.weak platform_mon_initial_ns_return
platform_mon_initial_ns_return:
	RESTORE_CONTEXT_GEN_REGS

	ldr	lr, [sp]

	/* Handle SMC_FC_FIQ_EXIT from non-secure */
	cmp	lr, #SMC_FC_FIQ_EXIT
	beq	.Lmon_fiq_exit_return

	SWITCH_SCR_TO_NONSECURE r3
	mov	r0, r1	/* secure os passes the return code in r1 */

	add	sp, sp, #5 * 4
	rfefd	sp!


.Lmon_smcall_secure_return:
	RESTORE_CONTEXT_AND_RETURN

.Lmon_fiq_entry:

	SAVE_CONTEXT

	mrc	p15, 0, r4, c1, c1, 0	/* r4 = SCR */
	tst	r4, #0x1
	beq	.	/* fiqs are not trapped in secure mode */

	ldr	r0, =SMC_FC_FIQ_ENTER

	SWITCH_SCR_TO_SECURE r4
	bl	.Lmon_context_switch
	SWITCH_SCR_TO_NONSECURE r4

	RESTORE_CONTEXT_GEN_REGS
	ldr	lr, [sp, #(5 * 4)]

	cmp	r1, #0	/* secure os passes the return code in r1 */
	subne	lr, lr, #4
	bne	.Lmon_fiq_return

	ldr	r0, [sp, #(6 * 4)] /* load saved spsr */
	mov	r1, lr

	mov	r2, #0x91 /* fiq mode with IRQ disabled */
	str	r2, [sp, #(6 * 4)] /* store spsr */

	mrc	p15, 0, r3, c1, c0, 0	/* r3 = Non-secure SCTLR */
	tst	r3, #(1 << 13) /* Non-secure SCTLR.V */
	ldrne	lr, =0xffff0000
	mrceq	p15, 0, lr, c12, c0, 0 /* lr = VBAR (if not using Hivecs) */
	add	lr, lr, #0x1c /* fiq vector offset */

	SWITCH_SCR_TO_SECURE r2

	/* Set fiq mode lr and spsr */
	cps	#MODE_FIQ
	msr	spsr_cfsx, r0
	mov	lr, r1
	cps	#MODE_MON

	SWITCH_SCR_TO_NONSECURE r2

.Lmon_fiq_return:
	str	lr, [sp, #(5 * 4)]
	RESTORE_CONTEXT_SCRATCH_REGS
	rfefd	sp!

.Lmon_fiq_exit_return:
	/* Retrieve FIQ mode spsr, lr and restore r0 from FIQ mode r12 */
	cps	#MODE_FIQ

	mov	r0, r12 /* restore r0 used for smc number */
	mrs	r1, spsr
	mov	r2, lr

	cps	#MODE_MON

	str	r2, [sp, #(5 * 4)] /* save return addr */

	/* don't allow return to monitor mode */
	and	r2, r1, #0x1f
	cmp	r2, #MODE_MON
	moveq	r1, #MODE_UND

	str	r1, [sp, #(6 * 4)] /* store spsr */

	SWITCH_SCR_TO_NONSECURE r3

	mov	lr, r0
	RESTORE_CONTEXT_SCRATCH_REGS
	mov	r0, lr
	rfefd	sp!

.data
.align 3

/* Monitor stack for primary cpu */
LOCAL_DATA(mon_ns_stack)
	.skip	MONITOR_STACK_SIZE * SMP_MAX_CPUS
LOCAL_DATA(mon_ns_stack_top)
