/*
 * Copyright (c) 2021, Google Inc. All rights reserved
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

/* status_t copy_from_anywhere(void *kdest, user_addr_t usrc, size_t len) */
FUNCTION(copy_from_anywhere)
	cbz	x2, .Lcopy_from_anywhere_done
.Lcopy_from_anywhere_loop:
	set_fault_handler	.Lcopy_from_anywhere_tryuser
	ldrb	w9, [x1]

.Lcopy_from_anywhere_continue:
	add	x1, x1, #1
	strb	w9, [x0], #1
	subs	x2, x2, #1
	b.hi	.Lcopy_from_anywhere_loop
.Lcopy_from_anywhere_done:
	mov	x0, #0
	ret

.Lcopy_from_anywhere_tryuser:
	set_fault_handler	.Lcopy_from_anywhere_fault
	ldtrb	w9, [x1]
	b .Lcopy_from_anywhere_continue

.Lcopy_from_anywhere_fault:
	strb	wzr, [x0], #1
	subs	x2, x2, #1
	b.hi	.Lcopy_from_anywhere_fault
	mov	x0, #ERR_FAULT
	ret

/* int tag_for_addr_(vaddr_t addr) */
FUNCTION(tag_for_addr_)
.arch_extension memtag
	set_fault_handler	.Ltag_for_addr__fault
	ldg	x0, [x0]
	ubfx	x0, x0, #56, #4
	ret

.Ltag_for_addr__fault:
	mov	x0, #ERR_FAULT
	ret
