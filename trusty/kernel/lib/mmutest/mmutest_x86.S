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

/**
 * int mmutest_arch_rodata_pnx(void) - Test that rodata section is mapped pnx
 *
 * Returns ERR_FAULT if rodata is not executable.
 * Return 0 if rodata is executable.
 */
.section .rodata
	set_fault_handler	.Lmmutest_fault
FUNCTION(mmutest_arch_rodata_pnx)
	xor	%eax, %eax

	ret

/**
 * int mmutest_arch_data_pnx(void) - Test that data section is mapped pnx
 *
 * Returns ERR_FAULT if data is not executable.
 * Return 0 if data is executable.
 */
.section .data
	set_fault_handler	.Lmmutest_fault
FUNCTION(mmutest_arch_data_pnx)
	xor	%eax, %eax

	ret
.section .text

/**
 * int mmutest_arch_rodata_ro(void) - Test that rodata section is mapped read-only
 *
 * Returns ERR_FAULT if rodata is not writable
 * Returns 1 if write to rodata is silently dropped
 * Returns 0 if rodata is writable
 */
FUNCTION(mmutest_arch_rodata_ro)
	leaq	.Ltest_rodata_long(%rip), %rax

	set_fault_handler	.Lmmutest_fault
	movl	$0, (%rax)

	movl	(%rax), %eax
	ret

.section .rodata
	.balign	4
.Ltest_rodata_long:
	.long	0x1

.section .text

/**
 * int mmutest_arch_store_uint32(uint32_t *ptr, bool user) - Test if ptr is writable
 * @ptr:  Memory location to test
 * @user: Use unprivileged store
 *
 * Returns ERR_FAULT if ptr is not writable
 * Returns ERR_GENERIC if ptr is not readable
 * Returns 2 if write does not fault, but data is lost on readback from memory
 * Returns 1 if write does not fault, but data is lost on readback from cache
 * Returns 0 if ptr is writable
 */
FUNCTION(mmutest_arch_store_uint32)
	test	%sil, %sil
	jnz	.Lmmutest_arch_store_uint32_user

	set_fault_handler	.Lmmutest_setup_fault
	movl	(%rdi), %edx
	not	%edx

	set_fault_handler	.Lmmutest_fault
	movl	%edx, (%rdi)
	mfence
	movl	(%rdi), %ecx

	jmp	.Lmmutest_arch_store_uint32_str_done

.Lmmutest_arch_store_uint32_user:
	/* TODO: call helper functions to write then read from user-space */
	movl	$ERR_NOT_IMPLEMENTED, %eax
	ret

.Lmmutest_arch_store_uint32_str_done:

	cmp	%edx, %ecx
	jne	.Lmmutest_arch_store_uint32_cache_read_mismatch

	push	%rdi
	push	%rsi
	push	%rdx
	movq	$4, %rsi
	call	arch_clean_invalidate_cache_range
	pop	%rdx
	pop	%rsi
	pop	%rdi

	test	%sil, %sil
	jnz	.Lmmutest_arch_store_uint32_memory_reload_user

	movl	(%rdi), %ecx
	jmp	.Lmmutest_arch_store_uint32_memory_reload_done

.Lmmutest_arch_store_uint32_memory_reload_user:
	/* TODO: call helper function to read from user-space */

.Lmmutest_arch_store_uint32_memory_reload_done:
	cmp	%edx, %ecx
	jne	.Lmmutest_arch_store_uint32_memory_mismatch

	xor	%eax, %eax
	ret

.Lmmutest_arch_store_uint32_cache_read_mismatch:
	movl	$1, %eax
	ret

.Lmmutest_arch_store_uint32_memory_mismatch:
	movl	$2, %eax
	ret

/**
 * int mmutest_arch_nop(int ret) - Return ret
 *
 * Returns ret if run from executable page.
 * Does not return if run from non-executable page.
 */
FUNCTION(mmutest_arch_nop)
	ret
FUNCTION(mmutest_arch_nop_end)

.Lmmutest_setup_fault:
	movl	$ERR_GENERIC, %eax
	ret

.Lmmutest_fault:
	movl	$ERR_FAULT, %eax
	ret
