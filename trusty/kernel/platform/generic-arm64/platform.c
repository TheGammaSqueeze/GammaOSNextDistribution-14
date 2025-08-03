/*
 * Copyright (c) 2015 Google Inc. All rights reserved
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

#include <debug.h>
#include <dev/interrupt/arm_gic.h>
#include <dev/timer/arm_generic.h>
#include <inttypes.h>
#include <kernel/vm.h>
#include <lk/init.h>
#include <platform/gic.h>
#include <string.h>
#include <sys/types.h>

#include "smc.h"

#define ARM_GENERIC_TIMER_INT_CNTV 27
#define ARM_GENERIC_TIMER_INT_CNTPS 29
#define ARM_GENERIC_TIMER_INT_CNTP 30

#define ARM_GENERIC_TIMER_INT_SELECTED(timer) ARM_GENERIC_TIMER_INT_##timer
#define XARM_GENERIC_TIMER_INT_SELECTED(timer) \
    ARM_GENERIC_TIMER_INT_SELECTED(timer)
#define ARM_GENERIC_TIMER_INT \
    XARM_GENERIC_TIMER_INT_SELECTED(TIMER_ARM_GENERIC_SELECTED)

#if GIC_VERSION <= 2
#define GICC_SIZE (0x1000)
#define GICD_SIZE (0x1000)
#define GICR_SIZE (0)
#else
#define GICC_SIZE (0x10000)
#define GICD_SIZE (0x10000)
#if GIC_VERSION < 4
#define GICR_SIZE (0x20000 * SMP_MAX_CPUS)
#else
#define GICR_SIZE (0x40000 * SMP_MAX_CPUS)
#endif
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

        /* null entry to terminate the list */
        {0, 0, 0, 0, 0}};

static pmm_arena_t ram_arena = {.name = "ram",
                                .base = MEMBASE + KERNEL_LOAD_OFFSET,
                                .size = MEMSIZE,
                                .flags = PMM_ARENA_FLAG_KMAP};

void platform_init_mmu_mappings(void) {
    /* go through mmu_initial_mapping to find dynamic entry
     * matching ram_arena (by name) and adjust it.
     */
    struct mmu_initial_mapping* m = mmu_initial_mappings;
    for (uint i = 0; i < countof(mmu_initial_mappings); i++, m++) {
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

static paddr_t generic_arm64_get_reg_base(int reg) {
#if ARCH_ARM64
    return generic_arm64_smc(SMC_FC64_GET_REG_BASE, reg, 0, 0);
#else
    return generic_arm64_smc(SMC_FC_GET_REG_BASE, reg, 0, 0);
#endif
}

static void platform_after_vm_init(uint level) {
    paddr_t gicc = generic_arm64_get_reg_base(SMC_GET_GIC_BASE_GICC);
    paddr_t gicd = generic_arm64_get_reg_base(SMC_GET_GIC_BASE_GICD);
    paddr_t gicr = generic_arm64_get_reg_base(SMC_GET_GIC_BASE_GICR);

    dprintf(INFO,
            "gicc 0x%" PRIxPADDR ", gicd 0x%" PRIxPADDR ", gicr 0x%" PRIxPADDR
            "\n",
            gicc, gicd, gicr);

    /* initialize the interrupt controller */
    struct arm_gic_init_info init_info = {
            .gicc_paddr = gicc,
            .gicc_size = GICC_SIZE,
            .gicd_paddr = gicd,
            .gicd_size = GICD_SIZE,
            .gicr_paddr = gicr,
            .gicr_size = GICR_SIZE,
    };
    arm_gic_init_map(&init_info);

    /* initialize the timer block */
    arm_generic_timer_init(ARM_GENERIC_TIMER_INT, 0);
}

LK_INIT_HOOK(platform_after_vm, platform_after_vm_init, LK_INIT_LEVEL_VM + 1);
