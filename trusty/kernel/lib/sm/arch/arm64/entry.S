/*
 * Copyright (c) 2013, Google Inc. All rights reserved
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
#include <lib/sm/monitor.h>
#include <lib/sm/smcall.h>
#include <lib/sm/sm_err.h>

#include <kernel/vm.h>

FUNCTION(sm_sched_nonsecure)
	push	x1, x30
	mov	x1, x0

	ldr	x0, =SMC_SC_NS_RETURN
	mov	x2, xzr
	mov	x3, xzr
	smc	#0

	pop	x9, x30
	stp	w0, w1, [x9], #8
	stp	w2, w3, [x9], #8
	str	x7, [x9], #8
	ret

FUNCTION(platform_early_halt)
	/* Disable interrupts and FIQs */
	msr	daifset, #3

.Lloop:
	ldr	x0, =SMC_SC_NS_RETURN
	ldr	x1, =SM_ERR_PANIC
	mov	x2, xzr
	mov	x3, xzr
	smc	#0

	b	.Lloop
