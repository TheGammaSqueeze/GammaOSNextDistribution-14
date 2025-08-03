/*
 * Copyright (c) 2013, Google Inc. All rights reserved.
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
#include <platform/vexpress-a15.h>
#include <platform/gic.h>
#if WITH_LIB_SM
#include <lib/sm/monitor.h>
#endif

#define GICC_CTLR               (0x0000)
#define GICC_IAR                (0x000c)
#define GICC_EOIR               (0x0010)

.section ".text"

FUNCTION(platform_secondary_entry)
	/* clear wakeup sgi */
	ldr	r4, =GICBASE(0) + GICC_OFFSET
	ldr	r5, [r4, #GICC_IAR]
	str	r5, [r4, #GICC_EOIR]

	b	_start


#if WITH_LIB_SM

FUNCTION(platform_mon_initial_ns_return)
	/* figure out our cpu number */
	mrc	p15, 0, ip, c0, c0, 5 /* read MPIDR */

	/* mask off the bottom 12 bits to test cluster number:cpu number */
	ubfx	ip, ip, #0, #12

	/* if we're not cpu 0:0, wait for ns reset addr */
	cmp	ip, #0
	moveq	lr, #~0
	beq	mon_initial_ns_return

	ldr	r4, =SECONDARY_BOOT_ADDR
	ldr	r5, =platform_secondary_entry_paddr
	ldr	r5, [r5]
	dsb
1:
	wfi
	ldr	lr, [r4]
	cmp	lr, r5
	beq	1b
	tst	lr, lr
	beq	1b

	b	mon_initial_ns_return
#endif

.ltorg

