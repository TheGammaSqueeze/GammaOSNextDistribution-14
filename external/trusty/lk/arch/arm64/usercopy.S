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

/* status_t arch_copy_to_user(user_addr_t udest, const void *ksrc, size_t len) */
FUNCTION(arch_copy_to_user)
	cbz	x2, .Larch_copy_to_user_done
.Larch_copy_to_user_loop:
	ldrb	w9, [x1], #1

	set_fault_handler	.Larch_copy_to_user_fault
	sttrb	w9, [x0]

	add	x0, x0, #1
	subs	x2, x2, #1
	b.hi	.Larch_copy_to_user_loop
.Larch_copy_to_user_done:
	mov	x0, #0
	ret

/* status_t arch_copy_from_user(void *kdest, user_addr_t usrc, size_t len) */
FUNCTION(arch_copy_from_user)
	cbz	x2, .Larch_copy_from_user_done
.Larch_copy_from_user_loop:
	set_fault_handler	.Larch_copy_from_user_fault
	ldtrb	w9, [x1]

	add	x1, x1, #1
	strb	w9, [x0], #1
	subs	x2, x2, #1
	b.hi	.Larch_copy_from_user_loop
.Larch_copy_from_user_done:
	mov	x0, #0
	ret

/* ssize_t arch_strlcpy_from_user(char *kdst, user_addr_t usrc, size_t len) */
FUNCTION(arch_strlcpy_from_user)
	mov	x10, x1
.Larch_strlcpy_from_user_loop:
	set_fault_handler	.Larch_strlcpy_from_user_fault
	ldtrb	w9, [x1]

	cmp	w9, #0
	cinc	x1, x1, ne

	cbz	x2, .Larch_strlcpy_from_user_dst_full
	subs	x2, x2, #1
	csel	w11, w9, wzr, hi
	strb	w11, [x0], #1
.Larch_strlcpy_from_user_dst_full:
	cbnz	w9, .Larch_strlcpy_from_user_loop

	sub	x0, x1, x10
	ret

.Larch_strlcpy_from_user_fault:
	cbz	x2, .Larch_copy_to_user_fault
.Larch_copy_from_user_fault:
	strb	wzr, [x0], #1
	subs	x2, x2, #1
	b.hi	.Larch_copy_from_user_fault
.Larch_copy_to_user_fault:
	mov	x0, #ERR_FAULT
	ret
