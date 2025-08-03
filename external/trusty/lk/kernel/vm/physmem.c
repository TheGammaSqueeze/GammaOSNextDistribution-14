/*
 * Copyright (c) 2020 LK Trusty Authors. All Rights Reserved.
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

#include <err.h>
#include <kernel/vm.h>
#include <kernel/physmem.h>
#include <trace.h>
#include <inttypes.h>

#define LOCAL_TRACE 0

static int phys_mem_obj_check_flags(struct vmm_obj* obj, uint* arch_mmu_flags);
static int phys_mem_obj_get_page(struct vmm_obj* obj,
                         size_t offset,
                         paddr_t* paddr,
                         size_t* paddr_size);
static void phys_mem_obj_destroy(struct vmm_obj* vmm_obj);

static struct vmm_obj_ops phys_mem_obj_ops = {
        .check_flags = phys_mem_obj_check_flags,
        .get_page = phys_mem_obj_get_page,
        .destroy = phys_mem_obj_destroy,
};

static struct phys_mem_obj* phys_mem_obj_from_vmm_obj(struct vmm_obj* vmm_obj) {
    return containerof(vmm_obj, struct phys_mem_obj, vmm_obj);
}

void phys_mem_obj_dynamic_initialize(struct phys_mem_obj* obj,
                                     struct obj_ref* ref,
                                     paddr_t paddr,
                                     size_t size,
                                     uint arch_mmu_flags,
                                     void (*destroy_fn)(struct phys_mem_obj*)) {

    DEBUG_ASSERT(IS_PAGE_ALIGNED(paddr));
    DEBUG_ASSERT(IS_PAGE_ALIGNED(size));
    DEBUG_ASSERT((arch_mmu_flags & ~(
                  ARCH_MMU_FLAG_CACHE_MASK |
                  ARCH_MMU_FLAG_PERM_RO |
                  ARCH_MMU_FLAG_PERM_NO_EXECUTE |
                  ARCH_MMU_FLAG_NS)) == 0);
    DEBUG_ASSERT(destroy_fn);

    obj->vmm_obj.ops = &phys_mem_obj_ops;
    obj->paddr = paddr;
    obj->size = size;
    obj->arch_mmu_flags = arch_mmu_flags;
    obj->destroy_fn = destroy_fn;
    obj_init(&obj->vmm_obj.obj, ref);
}

static void phys_mem_obj_default_destroy(struct phys_mem_obj* obj) {
    TRACEF("Warning: illegally destroy phys_obj %p\n", obj);
}

void phys_mem_obj_initialize(struct phys_mem_obj* obj,
                             struct obj_ref* ref,
                             paddr_t paddr,
                             size_t size,
                             uint arch_mmu_flags) {
    phys_mem_obj_dynamic_initialize(obj, ref, paddr, size, arch_mmu_flags,
                                    phys_mem_obj_default_destroy);
}

static int phys_mem_obj_check_flags(struct vmm_obj* obj,
                                    uint* arch_mmu_flags) {
    struct phys_mem_obj* phys_obj = phys_mem_obj_from_vmm_obj(obj);

    LTRACEF("obj arch_mmu_flags 0x%x, arch_mmu_flags 0x%x\n",
             phys_obj->arch_mmu_flags, *arch_mmu_flags);

    if (!(*arch_mmu_flags & ARCH_MMU_FLAG_PERM_RO) &&
        (phys_obj->arch_mmu_flags & ARCH_MMU_FLAG_PERM_RO)) {
        TRACEF("rw access denied. arch_mmu_flags=0x%x, phys_obj->flags=0x%x\n",
               *arch_mmu_flags, phys_obj->arch_mmu_flags);
        return ERR_ACCESS_DENIED;
    }

    if (!(*arch_mmu_flags & ARCH_MMU_FLAG_PERM_NO_EXECUTE) &&
        (phys_obj->arch_mmu_flags & ARCH_MMU_FLAG_PERM_NO_EXECUTE)) {
        TRACEF("exec access denied. arch_mmu_flags=0x%x, phys_obj->flags=0x%x\n",
               *arch_mmu_flags, phys_obj->arch_mmu_flags);
        return ERR_ACCESS_DENIED;
    }

    if (*arch_mmu_flags & ARCH_MMU_FLAG_NS) {
        TRACEF("ARCH_MMU_FLAG_NS should come from vmm_obj, not from caller\n");
        return ERR_INVALID_ARGS;
    }

    if (*arch_mmu_flags & ARCH_MMU_FLAG_CACHE_MASK) {
        TRACEF("cache attributes should come from vmm_obj, not from caller\n");
        return ERR_INVALID_ARGS;
    }

    *arch_mmu_flags |= phys_obj->arch_mmu_flags;

    return 0;
}

static int phys_mem_obj_get_page(struct vmm_obj* obj,
                         size_t offset,
                         paddr_t* paddr,
                         size_t* paddr_size) {
    struct phys_mem_obj* phys_obj = phys_mem_obj_from_vmm_obj(obj);

    LTRACEF("offset %zd phys_obj paddr 0x%" PRIxPADDR "\n", offset, phys_obj->paddr);

    if (offset >= phys_obj->size) {
        TRACEF("offset %zd out of range size %zd\n", offset,
               phys_obj->size);
        return ERR_OUT_OF_RANGE;
    }

    *paddr = phys_obj->paddr + offset;
    *paddr_size = phys_obj->size - offset;
    LTRACEF("offset %zd -> paddr 0x%" PRIxPADDR ", size %zu\n", offset,
            *paddr, *paddr_size);

    return 0;
}

static void phys_mem_obj_destroy(struct vmm_obj* vmm_obj) {
    struct phys_mem_obj* obj = containerof(vmm_obj,
                                           struct phys_mem_obj,
                                           vmm_obj);

    DEBUG_ASSERT(obj->destroy_fn);
    obj->destroy_fn(obj);
}
