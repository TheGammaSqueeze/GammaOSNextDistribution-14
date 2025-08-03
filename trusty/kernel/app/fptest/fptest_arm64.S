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

.altmacro

.macro init_regs val=x0, reg=0
    fmov    d\reg, \val
    add     \val, \val, #1
    fmov    v\reg&.d[1], \val
.if \reg < 31
    add     \val, \val, #1
    init_regs   \val, %(\reg+1)
.endif
.endm

/* clear reserved bits */
.macro fpcrval val, base
    add     \val, \base, #1
    lsl     \val, \val, #22
    bic     \val, \val, #(~0 << 27)
.endm

/* clear reserved bits */
.macro fpsrval val, base
    add     \val, \base, #2
    bic     \val, \val, #(3 << 5)
    bic     \val, \val, #((~0 << 8) ^ (~0 << 27))
.endm

FUNCTION(fptest_arch_init)
    init_regs
    fpcrval     x9, x0
    msr     fpcr, x9
    fpsrval     x9, x0
    msr     fpsr, x9
    ret

.macro check_val errors, val1, val2
    cmp     \val1, \val2
    cinc    \errors, \errors, ne
.endm

.macro check_regs errors, val, tmp, reg=0
    fmov    \tmp, d\reg
    check_val   \errors, \val, \tmp
    add     \val, \val, #1
    fmov    \tmp, v\reg&.d[1]
    check_val   \errors, \val, \tmp
.if \reg < 31
    add     \val, \val, #1
    check_regs  \errors, \val, \tmp, %(\reg+1)
.endif
.endm

FUNCTION(fptest_arch_check_state)
    mov     x9, x0
    mov     x0, #0
    check_regs  x0, x9, x10
    mrs     x10, fpcr
    fpcrval     x11, x9
    check_val   x0, x11, x10
    mrs     x10, fpsr
    fpsrval     x11, x9
    check_val   x0, x11, x10
    ret
