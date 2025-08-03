/*
 * Copyright (c) 2017 Google Inc. All rights reserved
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
#include <imx-regs.h>
#include <kernel/vm.h>
#include <lk/init.h>
#include <string.h>
#ifdef WITH_TZASC
#include <platform/tzasc.h>
#include <tzasc_regions.h>
#endif

#define ARM_GENERIC_TIMER_INT_CNTV 27
#define ARM_GENERIC_TIMER_INT_CNTPS 29
#define ARM_GENERIC_TIMER_INT_CNTP 30

#define ARM_GENERIC_TIMER_INT_SELECTED(timer) ARM_GENERIC_TIMER_INT_##timer
#define XARM_GENERIC_TIMER_INT_SELECTED(timer) \
    ARM_GENERIC_TIMER_INT_SELECTED(timer)
#define ARM_GENERIC_TIMER_INT \
    XARM_GENERIC_TIMER_INT_SELECTED(TIMER_ARM_GENERIC_SELECTED)

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
        {.phys = SOC_REGS_PHY,
         .virt = SOC_REGS_VIRT,
         .size = SOC_REGS_SIZE,
         .flags = MMU_INITIAL_MAPPING_FLAG_DEVICE,
         .name = "soc"},
        /* null entry to terminate the list */
        {0}};

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

static void generic_arm64_map_regs(const char* name,
                                   vaddr_t vaddr,
                                   paddr_t paddr,
                                   size_t size) {
    status_t ret;
    void* vaddrp = (void*)vaddr;

    ret = vmm_alloc_physical(vmm_get_kernel_aspace(), name, size, &vaddrp, 0,
                             paddr, VMM_FLAG_VALLOC_SPECIFIC,
                             ARCH_MMU_FLAG_UNCACHED_DEVICE);
    if (ret) {
        dprintf(CRITICAL, "%s: failed %d name=%s\n", __func__, ret, name);
    }
}

static void platform_after_vm_init(uint level) {
    generic_arm64_map_regs("gic", GIC_BASE_VIRT, GIC_BASE_PHY, GIC_REG_SIZE);

    /* Initialize the interrupt controller. */
    arm_gic_init();

    /* Initialize the timer block. */
    arm_generic_timer_init(ARM_GENERIC_TIMER_INT, 0);

    /* SOC is mapped in initial mapping */

#ifdef WITH_TZASC
    /* Initialize TZASC. */
    /* TZASC registers are mapped by initial mapping */
    initial_tzasc(tzasc_regions, countof(tzasc_regions));
#endif
}

LK_INIT_HOOK(platform_after_vm, platform_after_vm_init, LK_INIT_LEVEL_VM + 1);

#if WITH_VIRT_TIMER_INIT
extern void virt_timer_init(void);

/* Reset CNTVOFF register to be able to use virt arch timer */
static void platform_per_cpu_virt_timer_init(uint level) {
    virt_timer_init();
}

LK_INIT_HOOK_FLAGS(platform_per_cpu_virt_timer,
                   platform_per_cpu_virt_timer_init,
                   LK_INIT_LEVEL_PLATFORM_EARLY,
                   LK_INIT_FLAG_ALL_CPUS);

#endif
