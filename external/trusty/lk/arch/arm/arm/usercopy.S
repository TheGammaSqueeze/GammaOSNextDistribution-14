/*
 * Copyright (c) 2017, Google Inc. All rights reserved
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

/* status_t arch_copy_to_user(user_addr_t udest, const void *ksrc, size_t len) */
FUNCTION(arch_copy_to_user)
	cmp	r2, #0
	beq	.Larch_copy_to_user_done
.Larch_copy_to_user_loop:
	ldrb	r3, [r1], #1

	set_fault_handler	.Larch_copy_to_user_fault
	strbt	r3, [r0], #1

	subs	r2, r2, #1
	bhi	.Larch_copy_to_user_loop
.Larch_copy_to_user_done:
	mov	r0, #0
	bx	lr

/* status_t arch_copy_from_user(void *kdest, user_addr_t usrc, size_t len) */
FUNCTION(arch_copy_from_user)
	cmp	r2, #0
	beq	.Larch_copy_from_user_done
.Larch_copy_from_user_loop:
	set_fault_handler	.Larch_copy_from_user_fault
	ldrbt	r3, [r1], #1

	strb	r3, [r0], #1
	subs	r2, r2, #1
	bhi	.Larch_copy_from_user_loop
.Larch_copy_from_user_done:
	mov	r0, #0
	bx	lr

/* ssize_t arch_strlcpy_from_user(char *kdst, user_addr_t usrc, size_t len) */
FUNCTION(arch_strlcpy_from_user)
	mov	ip, r1
.Larch_strlcpy_from_user_loop:
	set_fault_handler	.Larch_strlcpy_from_user_fault
	ldrbt	r3, [r1]

	cmp	r3, #0
	addne	r1, r1, #1

	cmp	r2, #0
	beq	.Larch_strlcpy_from_user_dst_full
	subs	r2, r2, #1
	strbeq	r2, [r0], #1
	strbne	r3, [r0], #1
.Larch_strlcpy_from_user_dst_full:
	cmp	r3, #0
	bne	.Larch_strlcpy_from_user_loop

	sub	r0, r1, ip
	bx	lr

.Larch_strlcpy_from_user_fault:
	cmp	r2, #0
	beq	.Larch_copy_to_user_fault
.Larch_copy_from_user_fault:
	mov	r1, #0
	strb	r1, [r0], #1
	subs	r2, r2, #1
	bhi	.Larch_copy_from_user_fault
.Larch_copy_to_user_fault:
	mov	r0, #ERR_FAULT
	bx	lr
