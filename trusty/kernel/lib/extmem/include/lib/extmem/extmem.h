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

#pragma once

#include <kernel/vm_obj.h>
#include <lib/binary_search_tree.h>
#include <lk/reflist.h>
#include <sys/types.h>

struct vmm_obj;
struct obj_ref;
struct vmm_aspace;

/**
 * typedef ext_mem_client_id_t - External client identifier.
 *
 * Type use to store a 64 bit external client identifier.
 */
typedef uint64_t ext_mem_client_id_t;

/**
 * typedef ext_mem_obj_id_t - External memory identifier.
 *
 * Type use to store a 64 bit external memory identifier. The value is chosen
 * by the specific external memory implementation, but must be unique for the
 * namespace it is used in (@objs argument of ext_mem_insert).
 */
typedef uint64_t ext_mem_obj_id_t;

/**
 * struct ext_mem_page_run - Contiguous region of physical memory.
 * @paddr:  Physical address where region starts.
 * @size:   Number of bytes in region. Should be a multiple of PAGE_SIZE for the
 *          region to be mappable.
 */
struct ext_mem_page_run {
    paddr_t paddr;
    size_t size;
};

/**
 * struct ext_mem_obj - Memory object for external sources.
 * @id:             Unique id used for lookup.
 * @tag:            Metadata used by some systems. Set to 0 if unused.
 * @match_tag:      Metadata used by some systems. Set to 0 if unused.
 *                  Must match @tag before the object can be mapped. An object
 *                  attached to an ipc message can be created before sending it
 *                  to the app but only be mapable after the tag has been
 *                  matched.
 * @vmm_obj:        VMM object.
 * @node:           Search tree node.
 * @arch_mmu_flags: Memory type and required permission flags.
 * @page_run_count: Number of entries in @page_runs.
 * @page_runs:      Array of physically contiguous regions.
 */
struct ext_mem_obj {
    ext_mem_obj_id_t id;
    uint64_t tag;
    uint64_t match_tag;
    struct vmm_obj vmm_obj;
    struct bst_node node;
    uint arch_mmu_flags;
    size_t page_run_count;
    struct ext_mem_page_run page_runs[];
};

/**
 * ext_mem_obj_page_runs_size - Get size of page_runs.
 * @page_run_count: Number if page runs.
 *
 * Calculate size of page_runs array. This can be added by the caller to the
 * size of struct ext_mem_obj, or the size of a struct that embeds struct
 * ext_mem_obj at the end, to get the number of bytes to allocate.
 *
 * Return: Size of ext_mem_obj page_runs array in bytes.
 */
static inline size_t ext_mem_obj_page_runs_size(size_t page_run_count) {
    return sizeof(struct ext_mem_page_run) * page_run_count;
}

/**
 * ext_mem_obj_initialize - Initialize struct ext_mem_obj.
 * @obj:            Object to initialize.
 * @ref:            Initial reference.
 * @id:             Unique id used by ext_mem_insert and ext_mem_lookup.
 * @tag:            Extra metadata used by some systems. Set to 0 if unused.
 * @ops:            Pointer to &struct vmm_obj_ops. @ops->check_flags can point
 *                  directly to ext_mem_obj_check_flags. @ops->get_page can
 *                  point directly to ext_mem_obj_get_page. @ops->destroy must
 *                  point to a function supplied by the caller.
 * @arch_mmu_flags: Memory type and required permission flags.
 * @page_run_count: Number of entries in @page_runs.
 */
void ext_mem_obj_initialize(struct ext_mem_obj* obj,
                            struct obj_ref* ref,
                            ext_mem_obj_id_t id,
                            uint64_t tag,
                            struct vmm_obj_ops* ops,
                            uint arch_mmu_flags,
                            size_t page_run_count);

/**
 * ext_mem_insert - Insert ext_mem_obj.
 * @objs:   Root of search tree to insert @obj into.
 * @obj:    ext_mem_obj to insert.
 *
 * Insert @obj into @objs.
 * Caller is responsible for locking.
 *
 * Return: %true if @obj was inserted. %false if a node with the same id as
 *         @obj->id is already in @objs.
 */
bool ext_mem_insert(struct bst_root* objs, struct ext_mem_obj* obj);

/**
 * ext_mem_delete - Remove ext_mem_obj.
 * @objs:   Root of search tree that contains @obj.
 * @obj:    ext_mem_obj to delete.
 *
 * Delete @obj from @objs.
 * Caller is responsible for locking.
 */
static inline void ext_mem_delete(struct bst_root* objs,
                                  struct ext_mem_obj* obj) {
    bst_delete(objs, &obj->node);
}

/**
 * ext_mem_lookup - Lookup ext_mem_obj by id.
 * @objs:   Root of search tree that might contain and object with id @id.
 * @id:     Id of object to return.
 *
 * Caller is responsible for locking.
 *
 * Return: ext_mem_obj in @objs matching @id, or %NULL if no matching
 *         ext_mem_obj is found.
 */
struct ext_mem_obj* ext_mem_lookup(struct bst_root* objs, ext_mem_obj_id_t id);

/**
 * ext_mem_obj_set_match_tag - Set match tag on ext_mem_obj.
 * @obj:            &ext_mem_obj->vmm_obj.
 * @match_tag:      Set match_tag on @obj. The object can only be mapped if this
 *                  matches the tag used when creating @obj. Pass 0 to return to
 *                  initial state.
 */
void ext_mem_obj_set_match_tag(struct vmm_obj* obj, uint64_t match_tag);

/**
 * ext_mem_obj_check_flags - vmm_obj_ops->check_flags for ext_mem_obj.
 * @obj:            &ext_mem_obj->vmm_obj.
 * @arch_mmu_flags: arch_mmu_flags to check and modify.
 *
 * Compare @arch_mmu_flags against &ext_mem_obj->arch_mmu_flags. Return
 * ERR_ACCESS_DENIED if permissions &ext_mem_obj->arch_mmu_flags are more
 * restrictive than @arch_mmu_flags. Copy memory type flags from
 * &ext_mem_obj->arch_mmu_flags to @arch_mmu_flags.
 *
 * Return: 0 on success, error code on failure.
 */
int ext_mem_obj_check_flags(struct vmm_obj* obj, uint* arch_mmu_flags);

/**
 * ext_mem_obj_get_page - vmm_obj_ops->get_page for ext_mem_obj.
 * @obj:        &ext_mem_obj->vmm_obj.
 * @offset:     Byte offset into @obj.
 * @paddr:      Pointer to return physical address in.
 * @paddr_size: Pointer to return size of physically contiguous region at
 *              @offset.
 *
 * Get single page or physically contiguous region at @offset bytes from
 * start of @obj.
 *
 * Return: 0 on success, error code on failure.
 */
int ext_mem_obj_get_page(struct vmm_obj* obj,
                         size_t offset,
                         paddr_t* paddr,
                         size_t* paddr_size);

/**
 * ext_mem_map_obj_id - Lookup and map external memory object.
 * @aspace:         Pass-through to vmm_alloc_obj.
 * @name:           Pass-through to vmm_alloc_obj.
 * @client_id:      Id of external entity where the memory originated.
 * @mem_obj_id:     Id of shared memory object to lookup and map.
 * @tag:            Tag of the memory. If a non-FF-A object, use 0.
 * @offset:         Pass-through to vmm_alloc_obj.
 * @size:           Pass-through to vmm_alloc_obj.
 * @ptr:            Pass-through to vmm_alloc_obj.
 * @align_log2:     Pass-through to vmm_alloc_obj.
 * @vmm_flags:      Pass-through to vmm_alloc_obj.
 * @arch_mmu_flags: Pass-through to vmm_alloc_obj.
 *
 * Return: 0 on success, negative error code if object could not be mapped.
 */
status_t ext_mem_map_obj_id(struct vmm_aspace* aspace,
                            const char* name,
                            ext_mem_client_id_t client_id,
                            ext_mem_obj_id_t mem_obj_id,
                            uint64_t tag,
                            size_t offset,
                            size_t size,
                            void** ptr,
                            uint8_t align_log2,
                            uint vmm_flags,
                            uint arch_mmu_flags);

/**
 * ext_mem_get_vmm_obj - Lookup shared memory object.
 * @client_id:      Id of external entity where the memory originated.
 * @mem_obj_id:     Id of shared memory object to lookup and return.
 * @tag:            Tag of the memory. If a non-FF-A object, use 0.
 * @size:           Size hint for object. Caller expects an object at least this
 *                  big.
 * @objp:           Pointer to return object in.
 * @obj_ref:        Reference to *@objp.
 *
 * Not provided by ext_mem.
 *
 * Return: 0 on success. ERR_NOT_FOUND if @id does not exist.
 */
status_t ext_mem_get_vmm_obj(ext_mem_client_id_t client_id,
                             ext_mem_obj_id_t mem_obj_id,
                             uint64_t tag,
                             size_t size,
                             struct vmm_obj** objp,
                             struct obj_ref* obj_ref);
