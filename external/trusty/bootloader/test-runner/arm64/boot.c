/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include <test-runner-arch.h>

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/* QEMU FW_CFG, dtb and kernel load addresses */
#define FW_CFG_BASE 0x09020000
#define DTB_ADDR 0x40000000
#define LOAD_ADDR 0x40080000
#define INITRD_ADDR 0x48000000

#define FW_CFG_KERNEL_SIZE 0x08
#define FW_CFG_KERNEL_DATA 0x11

#define FW_CFG_INITRD_SIZE 0x0b
#define FW_CFG_INITRD_DATA 0x12

#define FW_CFG_DMA_CTL_READ 0x02
#define FW_CFG_DMA_CTL_SELECT 0x08

/* Reverse byte order for 16, 32 or 64 bit registers. Compiles to rev* opcode */
static uint64_t rev(uint64_t val, unsigned bits) {
    uint64_t mask = ~0ULL;
    for (unsigned shift = bits >> 1; shift >= 8; shift >>= 1) {
        mask ^= mask << shift;
        val = ((val >> shift) & mask) | ((val & mask) << shift);
    }
    return val;
}

static uint16_t rev16(uint16_t val) {
    return rev(val, 16);
}

static uint32_t rev32(uint32_t val) {
    return rev(val, 32);
}

static uint64_t rev64(uint64_t val) {
    return rev(val, 64);
}

static bool load_image(uint64_t addr, uint16_t cfg_data, uint16_t cfg_size) {
    volatile uint32_t* cfg_data32 = (void*)FW_CFG_BASE;
    volatile uint16_t* cfg_ctl = (void*)(FW_CFG_BASE + 0x8);
    volatile uint64_t* cfg_dma = (void*)(FW_CFG_BASE + 0x10);

    volatile struct {
        uint32_t control;
        uint32_t length;
        uint64_t address;
    } dma;

    /*
     * Setup dma description to select FW_CFG_KERNEL_DATA item and read from it.
     * The FW_CFG interface is big-endian and the cpu is little-endian so we
     * reverse the byte order.
     *
     * Interface is defined in external/qemu/hw/nvram/fw_cfg.c.
     */
    dma.control =
            rev32(FW_CFG_DMA_CTL_READ | FW_CFG_DMA_CTL_SELECT | cfg_data << 16);

    /*
     * Select FW_CFG_KERNEL_SIZE item and copy it to the dma lenght descriptor.
     * Both are big-endian so byte order reversal is needed.
     */
    *cfg_ctl = rev16(cfg_size);
    dma.length = rev32(*cfg_data32);
    if (!dma.length) {
        /* Return if no image was provided */
        return false;
    }

    /*
     * Set the target address for the DMA. Reverse the byte order of the address
     * since the dma descriptor expects big-endian byte order.
     */
    dma.address = rev64(addr);

    /* Start the dma */
    *cfg_dma = rev64((uint64_t)&dma);
    if (dma.control != 0) {
        /* Return if dma was not successful */
        return false;
    }
    return true;
}

void boot_next(void) {
    typedef void (*entry_func_t)(uint64_t dtb_paddr);

    if (!load_image(LOAD_ADDR, FW_CFG_KERNEL_DATA, FW_CFG_KERNEL_SIZE)) {
        return;
    }
    load_image(INITRD_ADDR, FW_CFG_INITRD_DATA, FW_CFG_INITRD_SIZE);

    /* Jump to the image we just loaded with the dtb address in x0 */
    ((entry_func_t)LOAD_ADDR)(DTB_ADDR);
}
