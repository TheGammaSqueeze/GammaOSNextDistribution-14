/*
 * Copyright (c) 2023, Google Inc. All rights reserved
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

#define X86_CR4_SMAP            0x00200000 /* SMAP protection enabling */

.macro copy_from_anywhere_loop with_smap, fault_label
.Lcopy_from_anywhere_loop\@:
.if \with_smap
	stac
.endif
	set_fault_handler	\fault_label
	movb	(%rsi), %cl
.if \with_smap
	clac
.endif

	inc	%rsi
	movb	%cl, (%rdi)
	inc	%rdi
	dec	%rdx
	ja	.Lcopy_from_anywhere_loop\@
.endm

/* status_t copy_from_anywhere(void *kdest, user_addr_t usrc, size_t len) */
FUNCTION(copy_from_anywhere)
	test	%rdx, %rdx
	jz	.Lcopy_from_anywhere_done
	movq	%cr4, %r8
	test	$X86_CR4_SMAP, %r8
	jz	.Lcopy_from_anywhere_without_smap
	copy_from_anywhere_loop 1, .Lcopy_from_anywhere_with_smap_fault
.Lcopy_from_anywhere_done:
	xor	%eax, %eax
	ret

.Lcopy_from_anywhere_without_smap:
	copy_from_anywhere_loop 0, .Lcopy_from_anywhere_without_smap_fault
	xor	%eax, %eax
	ret

.Lcopy_from_anywhere_with_smap_fault:
	clac
.Lcopy_from_anywhere_without_smap_fault:
.Lcopy_from_anywhere_fault_loop:
	movb	$0, (%rdi)
	inc	%rdi
	dec	%rdx
	ja	.Lcopy_from_anywhere_fault_loop
	mov	$ERR_FAULT, %eax
	ret
