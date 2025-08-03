/*
 * Copyright (c) 2013, Google Inc. All rights reserved
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
#ifndef __LIB_SM_MONITOR_H

#define MODE_USR 0x10
#define MODE_FIQ 0x11
#define MODE_IRQ 0x12
#define MODE_SVC 0x13
#define MODE_MON 0x16
#define MODE_ABT 0x17
#define MODE_UND 0x1b
#define MODE_SYS 0x1f
#define MODE_SVC_IRQ_DISABLED 0x93
#define MODE_SVC_IRQ_FIQ_DISABLED 0xd3

/* SCR values for secure and non-secure modes */
#define SM_SCR_NONSECURE 0x5
#define SM_SCR_SECURE 0x0

/* clang-format off */

/* sets SCR.NS bit to 1 (assumes monitor mode) */
.macro SWITCH_SCR_TO_NONSECURE, tmp
	mov	\tmp, #SM_SCR_NONSECURE
	mcr	p15, 0, \tmp, c1, c1, 0
	isb
.endm

/* sets SCR.NS bit to 0 (assumes monitor mode) */
.macro SWITCH_SCR_TO_SECURE, tmp
	mov	\tmp, #SM_SCR_SECURE
	mcr	p15, 0, \tmp, c1, c1, 0
	isb
.endm

/* clang-format on */

#endif /* __LIB_SM_MONITOR_H */
