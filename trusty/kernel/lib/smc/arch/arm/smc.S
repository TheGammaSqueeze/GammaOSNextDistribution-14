/*
 * Copyright (c) 2020, Google Inc. All rights reserved
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

FUNCTION(smc8)
    /* Save stack location where r3-r7 smc arguments are stored */
    mov     r12, sp

    /* Save original r4-r7 values as caller expects these to be preserved */
    push    {r4-r7}

    /* Save return value pointer and return address */
    push    {r0, lr}

    /* arm abi shifts arguments when returning a struct, shift them back */
    mov     r0, r1
    mov     r1, r2
    mov     r2, r3

    /* Load stack based arguments */
    ldmia   r12, {r3-r7}

    smc     #0

    /* Restore return address and get return value pointer */
    pop     {r12, lr}

    /* Copy 8-register smc return value to struct smc_ret8 return value */
    stmia   r12, {r0-r7}

    /* Restore original r4-r7 values */
    pop     {r4-r7}

    /* Return */
    bx      lr
