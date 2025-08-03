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
#include <inttypes.h>
#include <kernel/vm.h>
#include <lib/extmem/extmem.h>
#include <sys/types.h>
#include <trace.h>

#define LOCAL_TRACE 0

static struct ext_mem_obj* ext_mem_obj_from_vmm_obj(struct vmm_obj* vmm_obj) {
    return containerof(vmm_obj, struct ext_mem_obj, vmm_obj);
}

static struct ext_mem_obj* ext_mem_obj_from_bst_node(struct bst_node* node) {
    return containerof(node, struct ext_mem_obj, node);
}

static int ext_mem_obj_cmp(struct bst_node* a_bst, struct bst_node* b_bst) {
    struct ext_mem_obj* a = ext_mem_obj_from_bst_node(a_bst);
    struct ext_mem_obj* b = ext_mem_obj_from_bst_node(b_bst);

    return a->id < b->id ? 1 : a->id > b->id ? -1 : 0;
}

void ext_mem_obj_initialize(struct ext_mem_obj* obj,
                            struct obj_ref* ref,
                            ext_mem_obj_id_t id,
                            uint64_t tag,
                            struct vmm_obj_ops* ops,
                            uint arch_mmu_flags,
                            size_t page_run_count) {
    obj->id = id;
    obj->tag = tag;
    obj->match_tag = 0;
    obj->vmm_obj.ops = ops;
    obj->arch_mmu_flags = arch_mmu_flags;
    obj->page_run_count = page_run_count;
    obj_init(&obj->vmm_obj.obj, ref);
    bst_node_initialize(&obj->node);
}

bool ext_mem_insert(struct bst_root* objs, struct ext_mem_obj* obj) {
    return bst_insert(objs, &obj->node, ext_mem_obj_cmp);
}

struct ext_mem_obj* ext_mem_lookup(struct bst_root* objs, ext_mem_obj_id_t id) {
    struct ext_mem_obj ref_obj;
    ref_obj.id = id;
    return bst_search_type(objs, &ref_obj, ext_mem_obj_cmp, struct ext_mem_obj,
                           node);
}

void ext_mem_obj_set_match_tag(struct vmm_obj* obj, uint64_t match_tag) {
    struct ext_mem_obj* ext_obj = ext_mem_obj_from_vmm_obj(obj);

    ext_obj->match_tag = match_tag;
}

int ext_mem_obj_check_flags(struct vmm_obj* obj, uint* arch_mmu_flags) {
    struct ext_mem_obj* ext_obj = ext_mem_obj_from_vmm_obj(obj);

    LTRACEF("obj 0x%" PRIx64 ", obj arch_mmu_flags 0x%x, arch_mmu_flags 0x%x\n",
            ext_obj->id, ext_obj->arch_mmu_flags, *arch_mmu_flags);

    if (ext_obj->match_tag != ext_obj->tag) {
        TRACEF("WARNING: tag mismatch: 0x%" PRIx64 " != 0x%" PRIx64 "\n",
               ext_obj->match_tag, ext_obj->tag);
        return ERR_ACCESS_DENIED;
    }

    if (!(*arch_mmu_flags & ARCH_MMU_FLAG_PERM_RO) &&
        (ext_obj->arch_mmu_flags & ARCH_MMU_FLAG_PERM_RO)) {
        TRACEF("rw access denied. arch_mmu_flags=0x%x, ext_obj->flags=0x%x\n",
               *arch_mmu_flags, ext_obj->arch_mmu_flags);
        return ERR_ACCESS_DENIED;
    }

    if (!(*arch_mmu_flags & ARCH_MMU_FLAG_PERM_NO_EXECUTE) &&
        (ext_obj->arch_mmu_flags & ARCH_MMU_FLAG_PERM_NO_EXECUTE)) {
        TRACEF("exec access denied. arch_mmu_flags=0x%x, ext_obj->flags=0x%x\n",
               *arch_mmu_flags, ext_obj->arch_mmu_flags);
        return ERR_ACCESS_DENIED;
    }

    /*
     * Memory types must be consistent with external mappings, so don't allow
     * the caller to specify them.
     */
    if (*arch_mmu_flags & ARCH_MMU_FLAG_CACHE_MASK) {
        TRACEF("cache attributes should come from vmm_obj, not from caller\n");
        return ERR_INVALID_ARGS;
    }

    if (*arch_mmu_flags & ARCH_MMU_FLAG_NS) {
        TRACEF("ARCH_MMU_FLAG_NS should come from vmm_obj, not from caller\n");
        return ERR_INVALID_ARGS;
    }

    *arch_mmu_flags |= ext_obj->arch_mmu_flags;

    return 0;
}

int ext_mem_obj_get_page(struct vmm_obj* obj,
                         size_t offset,
                         paddr_t* paddr,
                         size_t* paddr_size) {
    struct ext_mem_obj* ext_obj = ext_mem_obj_from_vmm_obj(obj);
    size_t index;
    size_t page_offset;

    LTRACEF("offset %zd page_run_count %zd\n", offset, ext_obj->page_run_count);

    page_offset = offset;
    index = 0;
    while (index < ext_obj->page_run_count &&
           ext_obj->page_runs[index].size <= page_offset) {
        page_offset -= ext_obj->page_runs[index].size;
        index++;
    }

    if (index >= ext_obj->page_run_count) {
        TRACEF("offset %zd out of range index %zd >= %zd\n", offset, index,
               ext_obj->page_run_count);
        return ERR_OUT_OF_RANGE;
    }

    *paddr = ext_obj->page_runs[index].paddr + page_offset;
    *paddr_size = ext_obj->page_runs[index].size - page_offset;
    LTRACEF("offset %zd, index %zd/%zd -> paddr 0x%" PRIxPADDR ", size %zu\n",
            offset, index, ext_obj->page_run_count, *paddr, *paddr_size);

    return 0;
}

status_t ext_mem_map_obj_id(vmm_aspace_t* aspace,
                            const char* name,
                            ext_mem_client_id_t client_id,
                            ext_mem_obj_id_t mem_obj_id,
                            uint64_t tag,
                            size_t offset,
                            size_t size,
                            void** ptr,
                            uint8_t align_log2,
                            uint vmm_flags,
                            uint arch_mmu_flags) {
    status_t err;
    struct vmm_obj* vmm_obj = NULL;
    struct obj_ref vmm_obj_ref = OBJ_REF_INITIAL_VALUE(vmm_obj_ref);

    DEBUG_ASSERT(IS_PAGE_ALIGNED(size));

    err = ext_mem_get_vmm_obj(client_id, mem_obj_id, tag, size + offset,
                              &vmm_obj, &vmm_obj_ref);
    if (err) {
        TRACEF("failed to get object, 0x%" PRIx64 ":0x%" PRIx64
               ", to map for %s\n",
               client_id, mem_obj_id, name);
        return err;
    }

    /* If tag is not 0, match_tag must be set before the object can be mapped */
    ext_mem_obj_set_match_tag(vmm_obj, tag);

    err = vmm_alloc_obj(aspace, name, vmm_obj, offset, size, ptr, align_log2,
                        vmm_flags, arch_mmu_flags);
    vmm_obj_del_ref(vmm_obj, &vmm_obj_ref);
    if (err) {
        TRACEF("failed to map object, 0x%" PRIx64 ":0x%" PRIx64 ", for %s\n",
               client_id, mem_obj_id, name);
        return err;
    }
    LTRACEF("mapped 0x%" PRIx64 ":0x%" PRIx64 " at %p\n", client_id, mem_obj_id,
            *ptr);
    return err;
}
