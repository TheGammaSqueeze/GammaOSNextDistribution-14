/*
 * Copyright (c) 2020, Google, Inc. All rights reserved
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
#include <lib/trusty/handle.h>
#include <lib/trusty/memref.h>
#include <lib/trusty/trusty_app.h>

#include <uapi/mm.h>

#include <trace.h>

#include "util.h"

#define LOCAL_TRACE 0

/**
 * struct memref
 * @slice:     &struct vmm_obj_slice which will back a mapping of the memref
 * @handle:    Handle that may be referenced and transferred between userspace
 *             processes.
 * @mmap_prot: Protections to be enforced on the slice beyond what its
 *             check_flags function forces. Should be a mask from the
 *             MMAP_PROT_ family of flags.
 */
struct memref {
    struct vmm_obj_slice slice;
    struct handle handle;
    uint32_t mmap_prot;
};

/* This is only safe to call when the handle is destroyed */
static void memref_destroy(struct memref* memref) {
    LTRACEF("dropping memref\n");
    vmm_obj_slice_release(&memref->slice);
    free(memref);
}

static void memref_handle_destroy(struct handle* memref_handle) {
    DEBUG_ASSERT(memref_handle);
    struct memref* memref = containerof(memref_handle, struct memref, handle);
    memref_destroy(memref);
}

static status_t memref_mmap(struct handle* handle,
                            size_t offset,
                            user_size_t size,
                            uint32_t mmap_prot,
                            user_addr_t* addr) {
    DEBUG_ASSERT(handle);
    DEBUG_ASSERT(addr);

    LTRACEF("entered\n");

    status_t rc;
    struct memref* memref = containerof(handle, struct memref, handle);
    uint arch_mmu_flags = 0;

    if (!IS_PAGE_ALIGNED(offset)) {
        LTRACEF("unaligned offset");
        return ERR_INVALID_ARGS;
    }

    if (!IS_PAGE_ALIGNED(size)) {
        LTRACEF("unaligned size");
        return ERR_INVALID_ARGS;
    }

    if (offset > memref->slice.size) {
        LTRACEF("bad offset\n");
        return ERR_ACCESS_DENIED;
    }

    if (size > memref->slice.size - offset) {
        LTRACEF("bad size\n");
        return ERR_ACCESS_DENIED;
    }

    rc = xlat_flags(memref->mmap_prot, mmap_prot, &arch_mmu_flags);
    if (rc) {
        LTRACEF("xlat_flags failed\n");
        return rc;
    }

    struct trusty_app* app = current_trusty_app();
    assert(app);

    void* vaddr = (void*)(vaddr_t)*addr;

    rc = vmm_alloc_obj(app->aspace, "memref", memref->slice.obj,
                       memref->slice.offset + offset, size, &vaddr, 0, 0,
                       arch_mmu_flags);
    if (rc) {
        LTRACEF("vmm_alloc_obj failed\n");
        return rc;
    }

    *addr = (user_addr_t)((uintptr_t)vaddr);

    LTRACEF("success\n");
    return NO_ERROR;
}

static struct handle_ops memref_handle_ops = {
        .destroy = memref_handle_destroy,
        .mmap = memref_mmap,
};

static struct memref* memref_create(uint32_t mmap_prot) {
    /* defensive zero, this should full initialize */
    struct memref* memref = calloc(1, sizeof(*memref));
    if (!memref) {
        return NULL;
    }

    vmm_obj_slice_init(&memref->slice);
    handle_init_etc(&memref->handle, &memref_handle_ops, 0);
    memref->mmap_prot = mmap_prot;
    return memref;
}

static status_t check_slice(struct vmm_obj_slice *slice, uint32_t mmap_prot) {
    if (!IS_PAGE_ALIGNED(slice->size) || !IS_PAGE_ALIGNED(slice->offset)) {
        LTRACEF("unaligned\n");
        return ERR_INVALID_ARGS;
    }

    uint arch_mmu_flags = 0;
    status_t rc = xlat_flags(mmap_prot, mmap_prot, &arch_mmu_flags);
    if (rc) {
        LTRACEF("xlat_flags failed\n");
        return rc;
    }
    rc = slice->obj->ops->check_flags(slice->obj, &arch_mmu_flags);
    if (rc) {
        LTRACEF("check_flags failed\n");
        return rc;
    }

    return NO_ERROR;
}

status_t memref_create_from_vmm_obj(struct vmm_obj *obj,
                                    size_t offset,
                                    size_t size,
                                    uint32_t mmap_prot,
                                    struct handle** handle) {
    DEBUG_ASSERT(obj);

    struct memref *memref = memref_create(mmap_prot);
    if (!memref) {
        return ERR_NO_MEMORY;
    }

    vmm_obj_slice_bind(&memref->slice, obj, offset, size);

    status_t rc = check_slice(&memref->slice, mmap_prot);
    if (rc) {
        goto err;
    }

    *handle = &memref->handle;

    return NO_ERROR;

err:
    handle_decref(&memref->handle);
    return rc;
}

status_t memref_create_from_aspace(const vmm_aspace_t *aspace,
                                   vaddr_t vaddr,
                                   size_t size,
                                   uint32_t mmap_prot,
                                   struct handle** handle) {
    DEBUG_ASSERT(aspace);
    DEBUG_ASSERT(handle);

    struct memref* memref = memref_create(mmap_prot);
    if (!memref) {
        return ERR_NO_MEMORY;
    }

    status_t rc = vmm_get_obj(aspace, vaddr, size, &memref->slice);
    if (rc) {
        LTRACEF("vmm_get_obj failed: %d\n", rc);
        goto err;
    }

    rc = check_slice(&memref->slice, mmap_prot);
    if (rc) {
        goto err;
    }

    *handle = &memref->handle;

    return NO_ERROR;

err:
    handle_decref(&memref->handle);
    return rc;
}

static bool handle_is_memref(struct handle* handle) {
    return handle->ops == &memref_handle_ops;
}

struct vmm_obj* memref_handle_to_vmm_obj(struct handle* handle) {
    if (handle_is_memref(handle)) {
        return containerof(handle, struct memref, handle)->slice.obj;
    } else {
        return NULL;
    }
}
