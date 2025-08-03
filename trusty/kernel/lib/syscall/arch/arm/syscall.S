/*
 * Copyright (c) 2013, Google, Inc. All rights reserved
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

#define MODE_SVC	0x13

.syntax unified

/* ARM syscall ABI
 * ===============
 * Only syscalls with 4 args (max) are currently supported
 * r0-r3 = args
 * r0-r1 = return value (r0 only if 32-bit retval)
 * r12   = syscall number, expected to be trashed.
 * syscalls run with interrupts enabled
 */
FUNCTION (arm_syscall)
	srsdb	sp!, #MODE_SVC
	sub	sp, sp, #8
	stmia	sp, { r13-r14 }^

	cpsie	iaf

	ldr	r14, =nr_syscalls
	ldr	r14, [r14]
	cmp	r12, r14

	ldrlo	r14, =syscall_table
	ldrlo	r14, [r14, r12, lsl#2]
	rsbslo	r12, r14, #1	/* This will set the same flags as the cmp above */
	ldrhs	r14,=sys_undefined
	blx	r14

	cpsid	iaf

	ldmia	sp, { r13-r14 }^
	add	sp, sp, #8
	rfeia	sp!
