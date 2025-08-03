/*
 * Copyright (c) 2009 Corey Tabaka
 * Copyright (c) 2018 Intel Corporation
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

#include <arch/arch_ops.h>
#include <arch/x86/descriptor.h>
#include <compiler.h>
#include <debug.h>

/*
 * Descriptors are always 64-bit except TSS (or LDT) Descriptor in 64-bit mode.
 * In 64-bit mode, TSS descriptor is defined as 128 bits, low 64 bits of TSS
 * have same definition as legacy descriptor, high 64 bits contain base address
 * bits 63:32 of descriptor.
 *
 * High 32 bits of base address of descriptor should be set when dealing with
 * TSS descriptor.
 */
typedef union {
    struct {
        uint16_t limit_15_0;
        uint16_t base_15_0;
        uint8_t base_23_16;

        uint8_t type : 4;
        uint8_t s : 1;
        uint8_t dpl : 2;
        uint8_t p : 1;

        uint8_t limit_19_16 : 4;
        uint8_t avl : 1;
        uint8_t reserved_0 : 1;
        uint8_t d_b : 1;
        uint8_t g : 1;

        uint8_t base_31_24;
    } __PACKED legacy;

    struct {
        uint32_t base_32_63;
        uint16_t rsvd_1;
        uint16_t rsvd_2;
    } __PACKED tss_high;
} __PACKED seg_desc_t;

extern seg_desc_t _gdt[];

void set_global_desc(seg_sel_t sel,
        void *base,
        uint32_t limit,
        uint8_t present,
        uint8_t ring,
        uint8_t sys,
        uint8_t type,
        uint8_t gran,
        uint8_t bits)
{
    /* convert selector into index */
    uint16_t index = sel >> 3;

    /* For legacy descriptors and low 32 bit of 64-bit TSS only */
    _gdt[index].legacy.limit_15_0  = limit & 0x0000ffff;
    _gdt[index].legacy.limit_19_16 = (limit & 0x000f0000) >> 16;

    _gdt[index].legacy.base_15_0  = ((uint64_t)base) & 0x0000ffff;
    _gdt[index].legacy.base_23_16 = (((uint64_t)base) & 0x00ff0000) >> 16;
    _gdt[index].legacy.base_31_24 = ((uint64_t)base) >> 24;

    _gdt[index].legacy.type = type & 0x0f;    /* segment type */
    _gdt[index].legacy.p    = (present != 0); /* present */
    _gdt[index].legacy.dpl  = ring & 0x03;    /* descriptor privilege level */
    _gdt[index].legacy.g    = (gran != 0);    /* granularity */
    _gdt[index].legacy.s    = (sys != 0);     /* system / non-system */
    _gdt[index].legacy.d_b  = (bits != 0);    /* 16 / 32 bit */

    /* high bits of 64-bit TSS only */
    if (sel >= TSS_SELECTOR) {
        index = sel >> 3;

        /* update high bits of 64-bit TSS now */
        _gdt[index + 1].tss_high.base_32_63 = (uint64_t)base >> 32;
    }
}

tss_t *get_tss_base(void)
{
    volatile uint cpu = arch_curr_cpu_num();

    if (cpu >= SMP_MAX_CPUS) {
        panic("Invalid CPU ID\n");
    }

    return &system_tss[cpu];
}
