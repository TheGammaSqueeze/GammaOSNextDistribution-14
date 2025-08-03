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

.arch_extension pauth
.section .text

/**
 * int pactest_autia(uint64_t address, uint64_t modifier, uint64_t* result)
 *  - Function to test autia instruction (instruction A-key).
 *
 * This checks the passed address is authenticated with the modifier and IA key,
 * and returns the pointer in *result if FEAT_FPAC is not implemented.
 *
 * Returns ERR_FAULT if the PAC instruction faults (FEAT_FPAC).
 * Return 0 if the PAC check does not fault, in which case *result is updated.
 */
FUNCTION(pactest_autia)
set_fault_handler .Lpactest_fault
    autia x0, x1
    str x0, [x2]
    mov	x0, #0
    ret

/**
 * int pactest_autib(uint64_t address, uint64_t modifier, uint64_t* result)
 *  - Function to test autib instruction (instruction B-key).
 *
 * This checks the passed address is authenticated with the modifier and IB key,
 * and returns the pointer in *result if FEAT_FPAC is not implemented.
 *
 * Returns ERR_FAULT if the PAC instruction faults (FEAT_FPAC).
 * Return 0 if the PAC check does not fault, in which case *result is updated.
 */
FUNCTION(pactest_autib)
set_fault_handler .Lpactest_fault
    autib x0, x1
    str x0, [x2]
    mov	x0, #0
    ret


/**
 * int pactest_autda(uint64_t address, uint64_t modifier, uint64_t* result)
 *  - Function to test autda instruction (data A-key).
 *
 * This checks the passed address is authenticated with the modifier and DA key,
 * and returns the pointer in *result if FEAT_FPAC is not implemented.
 *
 * Returns ERR_FAULT if the PAC instruction faults (FEAT_FPAC).
 * Return 0 if the PAC check does not fault, in which case *result is updated.
 */
FUNCTION(pactest_autda)
set_fault_handler .Lpactest_fault
    autda x0, x1
    str x0, [x2]
    mov	x0, #0
    ret

/**
 * int pactest_autdb(uint64_t address, uint64_t modifier, uint64_t* result)
 *  - Function to test autdb instruction (data B-key).
 *
 * This checks the passed address is authenticated with the modifier and DB key,
 * and returns the pointer in *result if FEAT_FPAC is not implemented.
 *
 * Returns ERR_FAULT if the PAC instruction faults (FEAT_FPAC).
 * Return 0 if the PAC check does not fault, in which case *result is updated.
 */
FUNCTION(pactest_autdb)
set_fault_handler .Lpactest_fault
    autdb x0, x1
    str x0, [x2]
    mov	x0, #0
    ret

.Lpactest_fault:
    bti jc
    mov x0, #ERR_FAULT
    ret
