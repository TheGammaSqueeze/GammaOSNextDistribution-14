/*
 * Copyright (c) 2012 Travis Geiselbrecht
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
#ifndef __PLATFORM_VEXPRESS_A15_H
#define __PLATFORM_VEXPRESS_A15_H

#define REGISTER_BANK_0_PADDR (0x1c000000)
#define REGISTER_BANK_1_PADDR (0x1c100000)
#define REGISTER_BANK_2_PADDR (0x2c000000)

#define REGISTER_BANK_0_VADDR (0x1c000000) /* use identry map for now */
#define REGISTER_BANK_1_VADDR (0x1c100000)
#define REGISTER_BANK_2_VADDR (0x2c000000)

/* hardware base addresses */
#define SECONDARY_BOOT_ADDR (REGISTER_BANK_0_VADDR + 0x10030)

#define UART0 (REGISTER_BANK_0_VADDR + 0x90000)
#define UART1 (REGISTER_BANK_0_VADDR + 0xa0000)
#define UART2 (REGISTER_BANK_0_VADDR + 0xb0000)
#define UART3 (REGISTER_BANK_0_VADDR + 0xc0000)

#define TIMER0 (REGISTER_BANK_1_VADDR + 0x10000)
#define TIMER1 (REGISTER_BANK_1_VADDR + 0x10020)
#define TIMER2 (REGISTER_BANK_1_VADDR + 0x20000)
#define TIMER3 (REGISTER_BANK_1_VADDR + 0x20020)

#define GIC0 (REGISTER_BANK_2_VADDR + 0x00000)
#define GIC1 (REGISTER_BANK_2_VADDR + 0x10000)
#define GIC2 (REGISTER_BANK_2_VADDR + 0x20000)
#define GIC3 (REGISTER_BANK_2_VADDR + 0x30000)
#define GICBASE(n) (GIC0 + (n)*0x10000)

/* interrupts */
#define ARM_GENERIC_TIMER_INT 29
#define TIMER01_INT 34
#define TIMER23_INT 35
#define UART0_INT 37
#define UART1_INT 38
#define UART2_INT 39
#define UART3_INT 40

#define MAX_INT 96

#endif
