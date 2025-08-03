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
#include <arch.h>
#include <arch/arm.h>
#include <arch/arm/mmu.h>
#include <debug.h>
#include <dev/interrupt/arm_gic.h>
#include <dev/timer/arm_generic.h>
#include <err.h>
#include <kernel/thread.h>
#include <kernel/vm.h>
#include <lk/init.h>
#include <platform.h>
#include <platform/gic.h>
#include <platform/interrupts.h>
#include <platform/vexpress-a15.h>
#include <reg.h>
#include <string.h>
#include "platform_p.h"

#if WITH_LIB_KMAP
#include <lib/kmap.h>
#endif

#if WITH_SMP
void platform_secondary_entry(void);
paddr_t platform_secondary_entry_paddr;
#endif

/* initial memory mappings. parsed by start.S */
struct mmu_initial_mapping mmu_initial_mappings[] = {
        /* Mark next entry as dynamic as it might be updated
           by platform_reset code to specify actual size and
           location of RAM to use */
        {.phys = MEMBASE + KERNEL_LOAD_OFFSET,
         .virt = KERNEL_BASE + KERNEL_LOAD_OFFSET,
         .size = MEMSIZE,
         .flags = MMU_INITIAL_MAPPING_FLAG_DYNAMIC,
         .name = "ram"},

        {.phys = REGISTER_BANK_0_PADDR,
         .virt = REGISTER_BANK_0_VADDR,
         .size = 0x00100000,
         .flags = MMU_INITIAL_MAPPING_FLAG_DEVICE,
         .name = "bank-0"},

        {.phys = REGISTER_BANK_1_PADDR,
         .virt = REGISTER_BANK_1_VADDR,
         .size = 0x00100000,
         .flags = MMU_INITIAL_MAPPING_FLAG_DEVICE,
         .name = "bank-1"},

        {.phys = REGISTER_BANK_2_PADDR,
         .virt = REGISTER_BANK_2_VADDR,
         .size = 0x00100000,
         .flags = MMU_INITIAL_MAPPING_FLAG_DEVICE,
         .name = "bank-2"},

        /* null entry to terminate the list */
        {0}};

static pmm_arena_t ram_arena = {.name = "ram",
                                .base = MEMBASE + KERNEL_LOAD_OFFSET,
                                .size = MEMSIZE,
                                .flags = PMM_ARENA_FLAG_KMAP};

static uint32_t get_cpuid(void) {
    u_int cpuid;

    __asm__ volatile("mrc p15, 0, %0, c0, c0, 5 @ read MPIDR\n" : "=r"(cpuid));

    return cpuid & 0xF;
}

void platform_init_mmu_mappings(void) {
    if (get_cpuid())
        return;

    /* go through mmu_initial_mapping to find dynamic entry
     * matching ram_arena (by name) and adjust it.
     */
    struct mmu_initial_mapping* m = mmu_initial_mappings;
    for (size_t i = 0; i < countof(mmu_initial_mappings); i++, m++) {
        if (!(m->flags & MMU_INITIAL_MAPPING_FLAG_DYNAMIC))
            continue;

        if (strcmp(m->name, ram_arena.name) == 0) {
            /* update ram_arena */
            ram_arena.base = m->phys;
            ram_arena.size = m->size;
            ram_arena.flags = PMM_ARENA_FLAG_KMAP;

            break;
        }
    }
    pmm_add_arena(&ram_arena);
}

static uint32_t read_mpidr(void) {
    int mpidr;
    __asm__ volatile("mrc		p15, 0, %0, c0, c0, 5" : "=r"(mpidr));
    return mpidr;
}

void platform_early_init(void) {
    /* initialize the interrupt controller */
    arm_gic_init();

    /* initialize the timer block */
    arm_generic_timer_init(ARM_GENERIC_TIMER_INT, 0);

#if WITH_SMP
    dprintf(INFO, "Booting secondary CPUs. Main CPU MPIDR = %x\n",
            read_mpidr());
    platform_secondary_entry_paddr = vaddr_to_paddr(platform_secondary_entry);
    writel(platform_secondary_entry_paddr, SECONDARY_BOOT_ADDR);
    arm_gic_sgi(0, ARM_GIC_SGI_FLAG_TARGET_FILTER_NOT_SENDER, 0);
#endif
}

#if WITH_SMP

#define GICC_CTLR (GICC_OFFSET + 0x0000)
#define GICC_IAR (GICC_OFFSET + 0x000c)
#define GICC_EOIR (GICC_OFFSET + 0x0010)

static void platform_secondary_init(uint level) {
    u_int val;
    dprintf(INFO, "Booted secondary CPU, MPIDR = %x\n", read_mpidr());
    val = *REG32(GICBASE(0) + GICC_IAR);
    if (val)
        dprintf(INFO, "bad interrupt number on secondary CPU: %x\n", val);
    *REG32(GICBASE(0) + GICC_EOIR) = val & 0x3ff;
}

LK_INIT_HOOK_FLAGS(vexpress_a15,
                   platform_secondary_init,
                   LK_INIT_LEVEL_PLATFORM,
                   LK_INIT_FLAG_SECONDARY_CPUS);
#endif

void platform_init(void) {}
