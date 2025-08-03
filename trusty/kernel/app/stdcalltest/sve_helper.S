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

.arch armv8-a+sve


FUNCTION(clobber_sve_asm)
    MOV     x9, x0

    // Clobber Registers
    mov    z0.b, w9
    mov    z1.b, w9
    mov    z2.b, w9
    mov    z3.b, w9
    mov    z4.b, w9
    mov    z5.b, w9
    mov    z6.b, w9
    mov    z7.b, w9
    mov    z8.b, w9
    mov    z9.b, w9
    mov    z10.b, w9
    mov    z11.b, w9
    mov    z12.b, w9
    mov    z13.b, w9
    mov    z14.b, w9
    mov    z15.b, w9
    mov    z16.b, w9
    mov    z17.b, w9
    mov    z18.b, w9
    mov    z19.b, w9
    mov    z20.b, w9
    mov    z21.b, w9
    mov    z22.b, w9
    mov    z23.b, w9
    mov    z24.b, w9
    mov    z25.b, w9
    mov    z26.b, w9
    mov    z27.b, w9
    mov    z28.b, w9
    mov    z29.b, w9
    mov    z30.b, w9
    mov    z31.b, w9

    ptrue   p0.B
    ptrue   p1.B
    ptrue   p2.B
    ptrue   p3.B
    ptrue   p4.B
    ptrue   p5.B
    ptrue   p6.B
    ptrue   p7.B
    ptrue   p8.B
    ptrue   p9.B
    ptrue   p10.B
    ptrue   p11.B
    ptrue   p12.B
    ptrue   p13.B
    ptrue   p14.B
    ptrue   p15.B

    // If sve is available return #0x0 'NO Error'
    mov x0, #0
    ret

FUNCTION(load_sve_asm)
    mov x9, x0 // base address array
    mov x8, #0 // offset
    mov x7, x1 // offset increment

    ST1B    z0.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z1.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z2.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z3.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z4.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z5.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z6.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z7.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z8.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z9.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z10.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z11.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z12.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z13.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z14.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z15.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z16.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z17.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z18.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z19.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z20.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z21.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z22.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z23.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z24.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z25.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z26.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z27.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z28.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z29.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z30.b, p0, [x9, x8]

    ADD     x8, x8, x7
    ST1B    z31.b, p0, [x9, x8]

    mov x0, #0
    ret
