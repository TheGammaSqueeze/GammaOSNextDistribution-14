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
#include <lib/sm/monitor.h>

#define CPUID_ARM_GENTIMER_EXT_MASK (0xF << 16)

/*
 *  Reset CNTVOFF register to zero as inittial state is undefined on ARM v7
 *
 *  Must be in Secure Monitor Mode with NS bit set.
 */
FUNCTION(virt_timer_init)
    push    {r0}
    cps     #MODE_MON

    SWITCH_SCR_TO_NONSECURE r0

    mrc    p15, 0, r0, c0, c1, 1                 /* read ID_PFR1 */
    ands   r0, r0, #CPUID_ARM_GENTIMER_EXT_MASK  /* test arch timer bits */
    movne  r0, #0
    mcrrne p15, 4, r0, r0, c14                   /* Reset CNTVOFF to zero */

    SWITCH_SCR_TO_SECURE    r0

    cps    #MODE_SVC
    pop    {r0}
    bx     lr
