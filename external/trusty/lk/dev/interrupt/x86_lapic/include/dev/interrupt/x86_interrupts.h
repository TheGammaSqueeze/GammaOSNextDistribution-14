/*
 * Copyright (c) 2012-2018 LK Trusty Authors. All Rights Reserved.
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

#pragma once

#include <platform/interrupts.h>

#define INT_VECTORS 0x100

#ifndef INT_PIT
#define INT_PIT 0x30
#endif

#define PIC1_BASE_INTR INT_PIT
#define PIC2_CASCADE_INTR (PIC1_BASE_INTR + 2)
#define PIC2_BASE_INTR (PIC1_BASE_INTR + 8)
#define AFTER_PIC_INTR (PIC2_BASE_INTR + 8)
#define PIC1 0x20
#define PIC2 0xA0
#define ICW1 0x11 /* Cascade mode, ICW4 needed */
#define ICW3_PIC1 4
#define ICW3_PIC2 2
#define ICW4 0x5  /* Non buffered mode, 8086 mode */
#define PIC_EOI 0x20

struct int_handler_struct {
    int_handler handler;
    void* arg;
};

extern struct int_handler_struct int_handler_table[INT_VECTORS];

void x86_init_interrupts(void);
enum handler_return default_isr(unsigned int vector);
