/*
 * Copyright (c) 2020 Google Inc. All rights reserved
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

#include <arch/defines.h>
#include <arch/mmu.h>
#include <assert.h>
#include <endian.h>
#include <inttypes.h>
#include <kernel/vm.h>
#include <lib/rand/rand.h>
#include <trace.h>

#define LOCAL_TRACE 0

vaddr_t aslr_randomize_kernel_base(vaddr_t kernel_base) {
    STATIC_ASSERT(!(KERNEL_ASPACE_BASE & (PAGE_SIZE - 1)));
    STATIC_ASSERT(!(KERNEL_ASPACE_SIZE & (PAGE_SIZE - 1)));

    struct mmu_initial_mapping* second_mapping = &mmu_initial_mappings[1];
    if (second_mapping->size) {
        LTRACEF("non-kernel mapping phys:0x%" PRIxPADDR " virt:0x%" PRIxVADDR
                " size:%zu\n",
                second_mapping->phys, second_mapping->virt,
                second_mapping->size);
        return kernel_base;
    }

    struct mmu_initial_mapping* kernel_mapping = mmu_initial_mappings;
    kernel_base -= KERNEL_LOAD_OFFSET;
    ASSERT(kernel_mapping->virt == kernel_base);
    ASSERT(kernel_mapping->size);
    ASSERT(!(kernel_mapping->size & (PAGE_SIZE - 1)));

    const size_t aspace_pages = KERNEL_ASPACE_SIZE / PAGE_SIZE;
    size_t kernel_pages = kernel_mapping->size / PAGE_SIZE;
    /* Include 2 guard pages for the kernel */
    kernel_pages += 2;
    ASSERT(kernel_pages <= aspace_pages);

    size_t pick = rand_get_size(aspace_pages - kernel_pages);
    kernel_mapping->virt = KERNEL_ASPACE_BASE + ((1 + pick) * PAGE_SIZE);
    return kernel_mapping->virt + KERNEL_LOAD_OFFSET;
}
