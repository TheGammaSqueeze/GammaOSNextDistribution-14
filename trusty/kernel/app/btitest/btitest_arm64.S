/*
 * Copyright (c) 2022, Google Inc. All rights reserved
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
#include "btitest.h"

.section .text

/* Fault handler to return where BTI calls fail.
 * On return from the caught Branch Target exception, PSTATE.BTYPE will still
 * be set according to the last branch taken.  Therefore we need to land on a
 * BTI jc to be compatible with all callers - and avoid another immediate
 * Branch Target exception.
 */
.Lbtitest_callee_fault:
    bti jc
    mov x0, #ERR_FAULT
    ret

/**
 * int btitest_bl(void) - Use bl to call each of the test functions
 *
 * This calls each of the test functions using bl and a relative offset,
 * which should always be allowed.  The functions are expected to all return 0.
 *
 * Returns The first non-zero return code, or 0 if all functions pass.
 */
FUNCTION(btitest_bl)
    push lr, xzr
    bl btitest_callee_nop
    cbnz x0, .Lerror
    bl btitest_callee_bti
    cbnz x0, .Lerror
    bl btitest_callee_bti_c
    cbnz x0, .Lerror
    bl btitest_callee_bti_j
    cbnz x0, .Lerror
    bl btitest_callee_bti_jc
    cbnz x0, .Lerror
    bl btitest_callee_paciasp
    cbnz x0, .Lerror
    bl btitest_callee_pacibsp
.Lerror:
    pop lr, xzr
    ret

/**
 * int btitest_blr(int) - Use blr to call the passed function
 *
 * Returns The called function's return value
 */
FUNCTION(btitest_blr)
    push lr, xzr
    bl get_callee
    blr x0
    pop lr, xzr
    ret

/**
 * int btitest_br(int) - Use br to call the passed function via the x0 register
 *
 * Returns The called function's return value
 */
FUNCTION(btitest_br)
    push lr, xzr
    bl get_callee
    pop lr, xzr
    br x0

/**
 * int btitest_br_x16(int) - Use br to call the passed function via the x16
 *                           register
 *
 * Returns The called function's return value
 */
FUNCTION(btitest_br_x16)
    push lr, xzr
    bl get_callee
    pop lr, xzr
    mov x16, x0
    br x16

/**
 * int btitest_br_x17(int) - Use br to call the passed function via the x17
 *                           register
 *
 * Returns The called function's return value
 */
FUNCTION(btitest_br_x17)
    push lr, xzr
    bl get_callee
    pop lr, xzr
    mov x17, x0
    br x17

/**
 * int btitest_callee_nop(void) - Function with nop instruction
 *
 * Returns ERR_FAULT if the BTI check fails.
 * Return 0 if the BTI check passes.
 */
LOCAL_FUNCTION(btitest_callee_nop)
    set_fault_handler .Lbtitest_callee_fault
    nop
    mov	x0, #0
    ret

/**
 * int btitest_callee_bti(void) - Function with bti instruction
 *
 * Returns ERR_FAULT if the BTI check fails.
 * Return 0 if the BTI check passes.
 */
LOCAL_FUNCTION(btitest_callee_bti)
    set_fault_handler .Lbtitest_callee_fault
    bti
    mov	x0, #0
    ret

/**
 * int btitest_callee_bti_c(void) - Function with bti c instruction
 *
 * Returns ERR_FAULT if the BTI check fails.
 * Return 0 if the BTI check passes.
 */
LOCAL_FUNCTION(btitest_callee_bti_c)
    set_fault_handler .Lbtitest_callee_fault
    bti c
    mov	x0, #0
    ret

/**
 * int btitest_callee_bti_j(void) - Function with bti j instruction
 *
 * Returns ERR_FAULT if the BTI check fails.
 * Return 0 if the BTI check passes.
 */
LOCAL_FUNCTION(btitest_callee_bti_j)
    set_fault_handler .Lbtitest_callee_fault
    bti j
    mov	x0, #0
    ret

/**
 * int btitest_callee_bti_jc(void) - Function with bti jc instruction
 *
 * Returns ERR_FAULT if the BTI check fails.
 * Return 0 if the BTI check passes.
 */
LOCAL_FUNCTION(btitest_callee_bti_jc)
    set_fault_handler .Lbtitest_callee_fault
    bti jc
    mov	x0, #0
    ret

/**
 * int btitest_callee_paciasp(void) - Function with paciasp instruction
 *
 * Returns ERR_FAULT if the BTI check fails.
 * Return 0 if the BTI check passes.
 */
LOCAL_FUNCTION(btitest_callee_paciasp)
    set_fault_handler .Lbtitest_callee_fault
    paciasp
    mov	x0, #0
    autiasp
    ret

/**
 * int btitest_callee_pacibsp(void) - Function with pacibsp instruction
 *
 * Returns ERR_FAULT if the BTI check fails.
 * Return 0 if the BTI check passes.
 */
LOCAL_FUNCTION(btitest_callee_pacibsp)
    set_fault_handler .Lbtitest_callee_fault
    pacibsp
    mov	x0, #0
    autibsp
    ret

/**
 * int btitest_callee_error(void) - Error function which always fails
 *
 * This is valid as any jump target.
 *
 * Returns ERR_INVALID_ARGS unconditionally.
 */
LOCAL_FUNCTION(btitest_callee_error)
    bti jc
    mov	x0, #ERR_INVALID_ARGS
    ret

/**
 * get_callee(int) - Get the address of the callee function.
 *
 * Converts the passed function index into the function address.
 * This is done here to avoid using the C compiler to get function addresses -
 * the compiler may insert veneers or indirection under some situations (e.g.
 * for CFI) and break the specific landing pad instructions needed to test BTI.
 *
 * Return 0 if the index is not recognised, otherwise the function address.
 */
LOCAL_FUNCTION(get_callee)
    cmp w0, #BTITEST_CALLEE_NOP
    bne .Ltry_bti
    adr x0, btitest_callee_nop
    ret
.Ltry_bti:
    cmp w0, #BTITEST_CALLEE_BTI
    bne .Ltry_bti_c
    adr x0, btitest_callee_bti
    ret
.Ltry_bti_c:
    cmp w0, #BTITEST_CALLEE_BTI_C
    bne .Ltry_bti_j
    adr x0, btitest_callee_bti_c
    ret
.Ltry_bti_j:
    cmp w0, #BTITEST_CALLEE_BTI_J
    bne .Ltry_bti_jc
    adr x0, btitest_callee_bti_j
    ret
.Ltry_bti_jc:
    cmp w0, #BTITEST_CALLEE_BTI_JC
    bne .Ltry_paciasp
    adr x0, btitest_callee_bti_jc
    ret
.Ltry_paciasp:
    cmp w0, #BTITEST_CALLEE_PACIASP
    bne .Ltry_pacibsp
    adr x0, btitest_callee_paciasp
    ret
.Ltry_pacibsp:
    cmp w0, #BTITEST_CALLEE_PACIBSP
    bne .Lunknown
    adr x0, btitest_callee_pacibsp
    ret
.Lunknown:
    /* Return error function which unconditionally fails any test */
    adr x0, btitest_callee_error
    ret
