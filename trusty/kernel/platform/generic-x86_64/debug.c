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

#include <arch/x86.h>
#include <arch/x86/mmu.h>
#include <err.h>
#include <kernel/mutex.h>

#define UART_REGISTER_THR 0 /* WO Transmit Holding Register */
#define UART_REGISTER_LSR 5 /* R/W Line Status Register */

typedef union {
    struct {
        uint8_t dr : 1;
        uint8_t oe : 1;
        uint8_t pe : 1;
        uint8_t fe : 1;
        uint8_t bi : 1;
        uint8_t thre : 1;
        uint8_t temt : 1;
        uint8_t fifoe : 1;
    } bits;
    uint8_t data;
} uart_lsr_t;

static spin_lock_t dputc_spin_lock = 0;

static uint8_t serial_io_get_reg(uint64_t base_addr, uint32_t reg_id) {
    return inp((uint16_t)base_addr + (uint16_t)reg_id);
}

static void serial_io_set_reg(uint64_t base_addr,
                              uint32_t reg_id,
                              uint8_t val) {
    outp((uint16_t)base_addr + (uint16_t)reg_id, val);
}

static void uart_putc(char c) {
    uart_lsr_t lsr;

    do {
        lsr.data = serial_io_get_reg(TARGET_SERIAL_IO_BASE, UART_REGISTER_LSR);
    } while (!lsr.bits.thre);

    serial_io_set_reg(TARGET_SERIAL_IO_BASE, UART_REGISTER_THR, c);
}

void platform_dputc(char c) {
    spin_lock_saved_state_t state;
    spin_lock_save(&dputc_spin_lock, &state, SPIN_LOCK_FLAG_INTERRUPTS);
    uart_putc(c);
    spin_unlock_restore(&dputc_spin_lock, state, SPIN_LOCK_FLAG_INTERRUPTS);
}

/* Do not accept any input */
int platform_dgetc(char* c, bool wait) {
    return ERR_NOT_SUPPORTED;
}
