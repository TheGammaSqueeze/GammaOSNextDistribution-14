/*
 * Copyright (c) 2018 Intel Corporation
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

#define SYSCALL_ENTRY_SIZE    0x08

FUNCTION (x86_syscall)
    /* switch to kernel gs */
    swapgs

    sti
    pushq %rbx /* Ring 3 return address */
    pushq %rbp /* Ring 3 RSP */

    pushq %r15
    pushq %r14
    pushq %r13
    pushq %r12
    pushq %r11
    pushq %r10
    pushq %r9
    pushq %r8
    pushq %rcx
    pushq %rdx
    pushq %rbx
    pushq %rbp
    pushq %rsi
    pushq %rdi

    movq %rdx,%r9
    /*
     * Arranging params to pass to sysenter handling
     * syscall table is long. Multiplication to find offset.
     * Multiplier different for 32 and 64
     */
    movq $SYSCALL_ENTRY_SIZE, %r15
    /* Result is stored in RAX. Before multiplication RAX contains syscall No. */

    mul %r15
    movq $syscall_table, %r14
    add %r14, %rax
    movq %r9,%rdx
    call *(%rax)

    popq %rdi
    popq %rsi
    popq %rbp
    popq %rbx
    popq %rdx
    popq %rcx
    popq %r8
    popq %r9
    popq %r10
    popq %r11
    popq %r12
    popq %r13
    popq %r14
    popq %r15

    popq %rcx /* pop RSP */
    popq %rdx /* pop RIP */

    /*
     * switch to user gs
     * Have to make sure there is no interrupt triggered between swapgs and sysexit instructions,
     * if this happens, GS.base is switch to user level GS.base, but current context is still in
     * kernel level, it leads to get global states failure on current processor.
     */
    cli
    swapgs
    sti

    sysexitq
