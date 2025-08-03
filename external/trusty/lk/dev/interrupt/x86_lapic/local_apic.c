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
#include <arch/arch_ops.h>
#include <assert.h>
#include <debug.h>
#include <dev/interrupt/local_apic.h>
#include <lk/init.h>
#include <lk/macros.h>
#include <kernel/vm.h>
#include <reg.h>

typedef enum {
    LAPIC_ID_REG            = 0x2,
    LAPIC_EOI               = 0xB,
    LAPIC_SIVR              = 0xF,
    LAPIC_INTR_CMD_REG      = 0x30, /* 64-bits in x2APIC */
    LAPIC_INTR_CMD_HI_REG   = 0x31, /* not available in x2APIC */
    LAPIC_SELF_IPI_REG      = 0x3F  /* not available in xAPIC */
} lapic_reg_id_t;

#define LAPIC_REG_SHIFT 4

#define PAGE_4K_MASK 0xfffULL

#define MSR_APIC_BASE       0x1B
#define LAPIC_ENABLED       (1ULL << 11)
#define LAPIC_X2_ENABLED    (1ULL << 10)
#define LAPIC_BASE_ADDR(base_msr) ((base_msr) & (~PAGE_4K_MASK))

/* deliver status bit 12. 0 idle, 1 send pending. */
#define APIC_DS_BIT         (1ULL << 12)
#define MSR_X2APIC_BASE     0x800

#define APIC_DM_FIXED       0x000
#define APIC_LEVEL_ASSERT   0x4000
#define APIC_DEST_SELF      0x40000

static vaddr_t lapic_base_virtual_addr = 0;
static bool lapic_x2_enabled = false;

static bool lapic_is_x2_enabled(uint64_t value)
{
    return !!(value & LAPIC_X2_ENABLED);
}

static uint32_t lapic_x1_read_reg(lapic_reg_id_t reg_id)
{
    DEBUG_ASSERT(lapic_base_virtual_addr);
    vaddr_t addr = lapic_base_virtual_addr + (reg_id << LAPIC_REG_SHIFT);

    return readl(addr);
}

static void lapic_x1_write_reg(lapic_reg_id_t reg_id, uint32_t value)
{
    DEBUG_ASSERT(lapic_base_virtual_addr);
    vaddr_t addr = lapic_base_virtual_addr + (reg_id << LAPIC_REG_SHIFT);

    writel(value, addr);
}

/* Caller must make sure xAPIC mode. */
static void lapic_x1_wait_for_ipi(void)
{
    uint32_t icr_low;

    do {
        icr_low = lapic_x1_read_reg(LAPIC_INTR_CMD_REG);
    } while (icr_low & APIC_DS_BIT);
}

static uint64_t lapic_x2_read_reg(lapic_reg_id_t reg_id)
{
    return read_msr(MSR_X2APIC_BASE + reg_id);
}

static void lapic_x2_write_reg(lapic_reg_id_t reg_id, uint64_t value)
{
    write_msr(MSR_X2APIC_BASE + reg_id, value);
}

/*
 * Caller must make sure APIC is enabled.
 * Do not use this API to write ICR register, since xAPIC and
 * x2APIC have different definition on ICR.
 */
static void lapic_write_reg(lapic_reg_id_t reg_id, uint32_t value)
{
    DEBUG_ASSERT(LAPIC_INTR_CMD_REG != reg_id);

    if (lapic_x2_enabled) {
        lapic_x2_write_reg(reg_id, value);
    } else {
        lapic_x1_write_reg(reg_id, value);
    }
}

void lapic_eoi(void)
{
    lapic_write_reg(LAPIC_EOI, 1);
}

void lapic_software_disable(void)
{
    lapic_write_reg(LAPIC_SIVR, 0xFF);
}

bool send_self_ipi(uint32_t vector)
{
    uint32_t icr_low;
    uint64_t value = read_msr(MSR_APIC_BASE);

    if (!(value & LAPIC_ENABLED)) {
        return false;
    }

    icr_low = APIC_DEST_SELF | APIC_LEVEL_ASSERT | APIC_DM_FIXED | vector;

    if (lapic_x2_enabled) {
        lapic_x2_write_reg(LAPIC_SELF_IPI_REG, vector);
    } else {
        lapic_x1_wait_for_ipi();
        lapic_x1_write_reg(LAPIC_INTR_CMD_REG, icr_low);
    }

    return true;
}

/* Remap Local APIC instead of hard code mapping */
void local_apic_init(uint level)
{
    status_t ret;
    paddr_t lapic_base_phy_addr;
    uint64_t value = read_msr(MSR_APIC_BASE);

    lapic_base_phy_addr = LAPIC_BASE_ADDR(value);
    ret = vmm_alloc_physical(vmm_get_kernel_aspace(),
            "lapic",
            4096,
            (void **)&lapic_base_virtual_addr,
            PAGE_SIZE_SHIFT,
            lapic_base_phy_addr,
            0,
            ARCH_MMU_FLAG_UNCACHED_DEVICE);

    if (ret) {
        dprintf(CRITICAL, "Failed to allocate memory for Local APIC!\n");
        return;
    }
}

LK_INIT_HOOK(lapic, &local_apic_init, LK_INIT_LEVEL_VM + 1);
