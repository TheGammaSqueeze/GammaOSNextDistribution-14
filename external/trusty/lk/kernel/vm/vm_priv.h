/*
 * Copyright (c) 2014 Travis Geiselbrecht
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
#pragma once

#include <stdint.h>
#include <sys/types.h>
#include <kernel/vm.h>

/* simple boot time allocator state */
extern uintptr_t boot_alloc_start;
extern uintptr_t boot_alloc_end;

/**
 * struct vmm_res_obj - Object reserving address space with no physical backing.
 * @vmm_obj: The underlying vmm object.
 * @regions: The regions mapped inside this object.
 */
struct vmm_res_obj {
    struct vmm_obj vmm_obj;
    struct bst_root regions;
};

void vmm_init_preheap(void);
void vmm_init(void);

/* Reserve a number of pages, ensuring that backing physical memory exists. Reserved pages
 * can only be mapped using PMM_ALLOC_FLAG_FROM_RESERVED.
*/
status_t pmm_reserve_pages(uint count);

/* Unreserve a number of pages. */
void pmm_unreserve_pages(uint count);


/* private interface between pmm and vm */
void *pmm_paddr_to_kvaddr(paddr_t pa);

/* Check whether the given vmm_obj is a pmm whose memory
 * needs to be cleared before mapping.
 */
bool pmm_vmm_is_pmm_that_needs_clear(struct vmm_obj* vmm);

/* Check whether the given vmm_obj is a pmm whose memory
 * is allowed to be mapped tagged.
 */
bool pmm_vmm_is_pmm_that_allows_tagged(struct vmm_obj* vmm);

/* Declare that the memory associated with this vmm_obj
 * (which must be a pmm_obj) was cleared
 */
void pmm_set_cleared(struct vmm_obj* vmm, size_t offset, size_t size);

/* Declare that the memory associated with this vmm_obj
 * (which must be a pmm_obj) has been mapped as tagged
 */
void pmm_set_tagged(struct vmm_obj* vmm);
