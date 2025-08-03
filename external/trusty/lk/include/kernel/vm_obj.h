/*
 * Copyright (c) 2019 LK Trusty Authors. All Rights Reserved.
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

#include <assert.h>
#include <lk/reflist.h>
#include <sys/types.h>

__BEGIN_CDECLS

struct vmm_obj;

/**
 * struct vmm_obj_ops - Operation on &struct vmm_obj.
 */
struct vmm_obj_ops {
    /**
     * @check_flags: Function to check and optionally modify arch_mmu_flags.
     *
     * Check if permission @arch_mmu_flags are allowed and add other flags if
     * needed.
     *
     * If @obj is read-only and ARCH_MMU_FLAG_PERM_RO is not set, return
     * ERR_ACCESS_DENIED.
     *
     * If @obj require a specific memory type (e.g.
     * ARCH_MMU_FLAG_UNCACHED_DEVICE or ARCH_MMU_FLAG_NS), set those flags.
     *
     * Return 0 on success, error code to be passed to caller on failure.
     */
    int (*check_flags)(struct vmm_obj *obj, uint *arch_mmu_flags);
    /**
     * @get_page: Function to get a page address.
     *
     * Get single page or physically contiguous region at @offset bytes from
     * start of @obj.
     *
     * Return 0 on success, error code to be passed to caller on failure.
     */
    int (*get_page)(struct vmm_obj *obj, size_t offset, paddr_t *paddr,
                    size_t *paddr_size);
    /**
     * @destroy: Function to destroy object.
     *
     * Called after the last reference to @obj has been released.
     */
    void (*destroy)(struct vmm_obj *obj);
};

/**
 * struct vmm_obj - Object mappable by vmm.
 * @obj: Reflist object.
 * @ops: Pointer to &struct vmm_obj_ops.
 */
struct vmm_obj {
    struct obj obj;
    struct vmm_obj_ops *ops;
};

/**
 * vmm_obj_init - Initialize &struct vmm_obj.
 * @obj: Object to initialise and add reference to.
 * @ref: Reference to add.
 * @ops: Pointer to &struct vmm_obj_ops.
 */
static inline __ALWAYS_INLINE void vmm_obj_init(struct vmm_obj *obj,
                                                struct obj_ref *ref,
                                                struct vmm_obj_ops *ops) {
    DEBUG_ASSERT(ops->check_flags);
    DEBUG_ASSERT(ops->get_page);
    DEBUG_ASSERT(ops->destroy);
    obj->ops = ops;
    obj_init(&obj->obj, ref);
}

/**
 * vmm_obj_del_ref - Add a reference to a vmm_obj.
 * @obj: Object to add reference to.
 * @ref: Reference to add.
 */
void vmm_obj_add_ref(struct vmm_obj *obj, struct obj_ref *ref);

/**
 * vmm_obj_del_ref - Remove reference. Destroy object if no references remain.
 * @obj: Object to remove reference from.
 * @ref: Reference to remove.
 */
void vmm_obj_del_ref(struct vmm_obj *obj, struct obj_ref *ref);

/**
 * vmm_obj_has_single_ref - Return whether a &struct vmm_obj has a single
 *                          reference to it.
 * @obj: Object to check for single reference.
 * @ref: Reference to check. Must be a reference to @obj.
 *
 * The result from this function may become invalid if references are added or
 * deleted after this function returns, either by the caller or other threads.
 */
bool vmm_obj_has_only_ref(struct vmm_obj *obj, struct obj_ref *ref);

__END_CDECLS
