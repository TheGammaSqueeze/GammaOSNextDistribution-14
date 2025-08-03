/*
 * Copyright (c) 2009 Corey Tabaka
 * Copyright (c) 2015-2018 Intel Corporation
 * Copyright (c) 2016 Travis Geiselbrecht
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
#include <assert.h>
#include <dev/interrupt/x86_interrupts.h>
#include <dev/timer/x86_pit.h>
#include <kernel/vm.h>
#include <string.h>

#ifdef WITH_KERNEL_VM
struct mmu_initial_mapping mmu_initial_mappings[] = {
        /*
         * This entry will be used in pmm arena
         * structure member size will be updated in bootstrap code.
         */
        {
                .phys = MEMBASE + KERNEL_LOAD_OFFSET,
                .virt = KERNEL_BASE + KERNEL_LOAD_OFFSET,
                .size = MEMSIZE,
                .flags = MMU_INITIAL_MAPPING_FLAG_DYNAMIC,
                .name = "ram",
        },
        {0, 0, 0, 0, 0}};

static pmm_arena_t ram_arena = {
        .name = "ram",
        .base = MEMBASE,
        .size = 0,
        .priority = 1,
        .flags = PMM_ARENA_FLAG_KMAP,
};

void platform_init_mmu_mappings(void) {
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
#endif

void platform_early_init(void) {
    /* initialize the interrupt controller */
    x86_init_interrupts();

    /* initialize the timer */
    x86_init_pit();
}

void platform_init(void) {}
