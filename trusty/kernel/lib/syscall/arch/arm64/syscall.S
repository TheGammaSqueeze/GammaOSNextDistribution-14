/*
 * Copyright (c) 2014, Google, Inc. All rights reserved
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

#include <arch/asm_macros.h>
#include <asm.h>

#define DAIF_MASK_IAF 0x7

/* ARM syscall ABI
 * ===============
 * Only syscalls with 4 args (max) are currently supported
 * r0-r3/x0-x3 = args
 * r0-r1/x0    = return value (r0 only if 32-bit retval)
 * r12/x12     = syscall number, expected to be trashed.
 * syscalls run with interrupts enabled
 */
FUNCTION (arm64_syscall)
	push	x0, x30
	ldr	w12, [x0, #(12 << 3)]

	msr	daifclr, #DAIF_MASK_IAF

	adrl	x14, nr_syscalls
	ldr	x14, [x14]
	cmp	x12, x14
	b.hs	.Lundefined
	adrl	x14, syscall_table
	ldr	x14, [x14, x12, lsl#3]
	cbnz	x14, .Ldefined
.Lundefined:
	adrl	x14, sys_undefined
.Ldefined:
	ldp	x2, x3, [x0, #16]
	ldp	x0, x1, [x0]
	blr	x14

	msr	daifset, #DAIF_MASK_IAF

	pop	x1, x30

	str	x0, [x1, 0]

	ret
