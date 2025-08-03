/*
 * Copyright (c) 2015, Google Inc. All rights reserved
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
#include <err.h>

.syntax unified

/**
 * int mmutest_arch_rodata_pnx(void) - Test that rodata section is mapped pnx
 *
 * Returns ERR_FAULT if rodata is not executable.
 * Return 0 if rodata is executable.
 */
.section .rodata
.align 2
FUNCTION(mmutest_arch_rodata_pnx)
	set_fault_handler	.Lmmutest_fault
	mov	r0, #0

	bx	lr

/**
 * int mmutest_arch_data_pnx(void) - Test that data section is mapped pnx
 *
 * Returns ERR_FAULT if data is not executable.
 * Return 0 if data is executable.
 */
.section .data
.align 2
FUNCTION(mmutest_arch_data_pnx)
	set_fault_handler	.Lmmutest_fault
	mov	r0, #0

	bx	lr
.section .text

/**
 * int mmutest_arch_rodata_ro(void) - Test that rodata section is mapped read-only
 *
 * Returns ERR_FAULT if rodata is not writeable
 * Returns 1 if write to rodata is silently dropped
 * Returns 0 if rodata is writable
 */
FUNCTION(mmutest_arch_rodata_ro)
	ldr	r0, =.Ltest_rodata_long
	mov	r1, #0

	set_fault_handler	.Lmmutest_fault
	str	r1, [r0]

	ldr	r0, [r0]
	bx	lr

.section .rodata
.Ltest_rodata_long:
	.long	0x1

.section .text

/**
 * int mmutest_arch_store_uint32(uint32_t *ptr, bool user) - Test if ptr is writeable
 * @ptr:  Memory location to test
 * @user: Use unpriviledged store
 *
 * Returns ERR_FAULT if ptr is not writeable
 * Returns ERR_GENERIC if ptr is not readable
 * Returns 2 if write does not fault, but data is lost on readback from memory
 * Returns 1 if write does not fault, but data is lost on readback from cache
 * Returns 0 if ptr is writable
 */
FUNCTION(mmutest_arch_store_uint32)
	cmp	r1, #0

	set_fault_handler	.Lmmutest_setup_fault
	ldreq	r1, [r0]

	set_fault_handler	.Lmmutest_setup_fault
	ldrtne	r1, [r0]

	mvn	r1, r1

	set_fault_handler	.Lmmutest_fault
	streq	r1, [r0]

	set_fault_handler	.Lmmutest_fault
	strtne	r1, [r0]

	dmb

	ldr	r2, [r0]
	cmp	r1, r2
	movne	r0, #1
	bxne	lr

	push	{r0, r1, r2, lr}
	mov	r1, #4
	blx	arch_clean_invalidate_cache_range
	pop	{r0, r1, r2, lr}

	ldr	r2, [r0]
	cmp	r1, r2
	moveq	r0, #0
	movne	r0, #2

	bx	lr

/**
 * int mmutest_arch_nop(int ret) - Return ret
 *
 * Returns ret if run from executable page.
 * Does not return if run from non-executable page.
 */
FUNCTION(mmutest_arch_nop)
	bx	lr
FUNCTION(mmutest_arch_nop_end)

.Lmmutest_setup_fault:
	mov	r0, #ERR_GENERIC
	bx	lr

.Lmmutest_fault:
	mov	r0, #ERR_FAULT
	bx	lr
