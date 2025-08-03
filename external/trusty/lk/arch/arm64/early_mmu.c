/*
 * Copyright (c) 2021 Google Inc. All rights reserved
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

#include <arch/arm64/mmu.h>
#include <arch/ops.h>
#include <assert.h>
#include <kernel/vm.h>
#include <lk/compiler.h>
#include <panic.h>
#include <sys/types.h>

static uint get_aspace_flags(void) {
    uint aspace_flags = ARCH_ASPACE_FLAG_KERNEL;

#ifdef KERNEL_BTI_ENABLED
    if (arch_bti_supported()) {
        aspace_flags |= ARCH_ASPACE_FLAG_BTI;
    }
#endif

    return aspace_flags;
}

/* the main translation table */
pte_t arm64_kernel_translation_table[MMU_KERNEL_PAGE_TABLE_ENTRIES_TOP]
    __ALIGNED(MMU_KERNEL_PAGE_TABLE_ENTRIES_TOP * 8);

static void* early_mmu_paddr_to_kvaddr(paddr_t paddr) {
    return (void*)paddr;
}

static int alloc_page_table(paddr_t* paddrp, uint page_size_shift) {
    const size_t size = 1UL << page_size_shift;
    paddr_t paddr = (paddr_t)boot_alloc_memalign(size, size);
    *paddrp = paddr;
    return 0;
}

static void free_page_table(void* vaddr,
                            paddr_t paddr,
                            uint page_size_shift) {
    /* If we get here then we can't boot, so halt */
    panic("reached free_page_table during early boot\n");
}

/*
 * Override paddr_to_kvaddr since it's implemented in kernel/vm.c
 * and we don't want to change that.
 */
#define paddr_to_kvaddr early_mmu_paddr_to_kvaddr
#define EARLY_MMU
#include "mmu.inc"
#undef paddr_to_kvaddr

void arch_mmu_map_early(vaddr_t vaddr,
                        paddr_t paddr,
                        size_t size,
                        uint flags) {
    pte_t attr;
    bool res = mmu_flags_to_pte_attr(get_aspace_flags(), flags, &attr);
    ASSERT(res);
    const uintptr_t vaddr_top_mask = ~0UL << MMU_KERNEL_SIZE_SHIFT;
    ASSERT((vaddr & vaddr_top_mask) == vaddr_top_mask);
    int ret = arm64_mmu_map_pt(vaddr, vaddr ^ vaddr_top_mask, paddr, size, attr,
                               MMU_KERNEL_TOP_SHIFT, MMU_KERNEL_PAGE_SIZE_SHIFT,
                               arm64_kernel_translation_table,
                               MMU_ARM64_GLOBAL_ASID, false);
    ASSERT(!ret);
}

void arm64_early_mmu_init(ulong ram_size, uintptr_t* relr_start,
                          uintptr_t* relr_end, paddr_t kernel_paddr) {
    const uintptr_t kernel_initial_vaddr = KERNEL_BASE + KERNEL_LOAD_OFFSET;
    uintptr_t virt_offset = kernel_initial_vaddr - kernel_paddr;
    update_relocation_entries(relr_start, relr_end, virt_offset);

    /* Relocate the kernel to its physical address */
    relocate_kernel(relr_start, relr_end, kernel_initial_vaddr, kernel_paddr);

    vm_assign_initial_dynamic(kernel_paddr, ram_size);
    vaddr_t kernel_final_vaddr =
        aslr_randomize_kernel_base(kernel_initial_vaddr);
    vm_map_initial_mappings();

    /* Relocate the kernel to its final virtual address */
    relocate_kernel(relr_start, relr_end, kernel_paddr, kernel_final_vaddr);
}
