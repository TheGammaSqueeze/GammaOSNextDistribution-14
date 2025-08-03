/*
 * Copyright (c) 2014 Google Inc. All rights reserved
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
#include <bits.h>
#include <debug.h>
#include <err.h>
#include <kernel/thread.h>
#include <kernel/vm.h>
#include <lib/heap.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <trace.h>
#include <inttypes.h>

#define LOCAL_TRACE 0
#define TRACE_CONTEXT_SWITCH 0

#define ARM64_ASID_BITS (8) /* TODO: Use 16 bit ASIDs when hardware supports it */

STATIC_ASSERT(((long)KERNEL_BASE >> MMU_KERNEL_SIZE_SHIFT) == -1);
STATIC_ASSERT(((long)KERNEL_ASPACE_BASE >> MMU_KERNEL_SIZE_SHIFT) == -1);
STATIC_ASSERT(MMU_KERNEL_SIZE_SHIFT <= 48);
STATIC_ASSERT(MMU_KERNEL_SIZE_SHIFT >= 25);
STATIC_ASSERT(USER_ASPACE_BASE + USER_ASPACE_SIZE <= 1UL << MMU_USER_SIZE_SHIFT);

/* the main translation table */
extern pte_t arm64_kernel_translation_table[];

/* This is explicitly a check for overflows, so don't sanitize it */
__attribute__((no_sanitize("unsigned-integer-overflow")))
static inline bool wrap_check(vaddr_t vaddr, size_t size) {
    return vaddr + size - 1 > vaddr;
}

static uint64_t arch_mmu_asid(arch_aspace_t *aspace)
{
    return aspace->asid & BIT_MASK(ARM64_ASID_BITS);
}

static inline vaddr_t adjusted_vaddr(arch_aspace_t *aspace, vaddr_t vaddr)
{
    return arch_adjusted_vaddr(vaddr, aspace->flags & ARCH_ASPACE_FLAG_KERNEL);
}

static inline bool is_valid_vaddr(arch_aspace_t *aspace, vaddr_t vaddr)
{
    vaddr = adjusted_vaddr(aspace, vaddr);
    return (aspace->size && vaddr >= aspace->base && vaddr <= aspace->base + (aspace->size - 1));
}

/* convert user level mmu flags to flags that go in L1 descriptors */
static bool mmu_flags_to_pte_attr(const uint aspace_flags, const uint flags, pte_t* out_attr)
{
    pte_t attr = MMU_PTE_ATTR_AF;

    DEBUG_ASSERT((aspace_flags & ~ARCH_ASPACE_FLAG_ALL) == 0);

    if (flags & ARCH_MMU_FLAG_TAGGED) {
        if ((flags & ARCH_MMU_FLAG_CACHE_MASK) & ~ARCH_MMU_FLAG_CACHED) {
            /* only normal memory can be tagged */
            return false;
        }
    }
    switch (flags & ARCH_MMU_FLAG_CACHE_MASK) {
        case ARCH_MMU_FLAG_CACHED:
            if (flags & ARCH_MMU_FLAG_TAGGED) {
                attr |= MMU_PTE_ATTR_NORMAL_MEMORY_TAGGED;
            } else {
                attr |= MMU_PTE_ATTR_NORMAL_MEMORY;
            }
            attr |= MMU_PTE_ATTR_SH_INNER_SHAREABLE;
            break;
        case ARCH_MMU_FLAG_UNCACHED:
            attr |= MMU_PTE_ATTR_STRONGLY_ORDERED;
            break;
        case ARCH_MMU_FLAG_UNCACHED_DEVICE:
            attr |= MMU_PTE_ATTR_DEVICE;
            break;
        default:
            /* invalid user-supplied flag */
            DEBUG_ASSERT(0);
            return false;
    }

    switch (flags & (ARCH_MMU_FLAG_PERM_USER | ARCH_MMU_FLAG_PERM_RO)) {
        case 0:
            attr |= MMU_PTE_ATTR_AP_P_RW_U_NA;
            break;
        case ARCH_MMU_FLAG_PERM_RO:
            attr |= MMU_PTE_ATTR_AP_P_RO_U_NA;
            break;
        case ARCH_MMU_FLAG_PERM_USER:
            attr |= MMU_PTE_ATTR_AP_P_RW_U_RW;
            break;
        case ARCH_MMU_FLAG_PERM_USER | ARCH_MMU_FLAG_PERM_RO:
            attr |= MMU_PTE_ATTR_AP_P_RO_U_RO;
            break;
    }

    if (flags & ARCH_MMU_FLAG_PERM_NO_EXECUTE) {
        attr |= MMU_PTE_ATTR_UXN | MMU_PTE_ATTR_PXN;
    } else if (flags & ARCH_MMU_FLAG_PERM_USER) {
        /* User executable page, marked privileged execute never. */
        attr |= MMU_PTE_ATTR_PXN;

        if (aspace_flags & ARCH_ASPACE_FLAG_BTI) {
            attr |= MMU_PTE_ATTR_GP;
        }
    } else {
        /* Privileged executable page, marked user execute never. */
        attr |= MMU_PTE_ATTR_UXN;

        if (aspace_flags & ARCH_ASPACE_FLAG_BTI) {
            attr |= MMU_PTE_ATTR_GP;
        }
    }

    if (flags & ARCH_MMU_FLAG_NS) {
        attr |= MMU_PTE_ATTR_NON_SECURE;
    }

    /* GP bit is undefined/MBZ if BTI is not supported */
    if ((attr & MMU_PTE_ATTR_GP) && !arch_bti_supported()) {
        return false;
    }

    *out_attr = attr;
    return true;
}

#ifndef EARLY_MMU
status_t arch_mmu_query(arch_aspace_t *aspace, vaddr_t vaddr, paddr_t *paddr, uint *flags)
{
    uint index;
    uint index_shift;
    uint page_size_shift;
    pte_t pte;
    pte_t pte_addr;
    uint descriptor_type;
    pte_t *page_table;
    vaddr_t vaddr_rem;

    LTRACEF("aspace %p, vaddr 0x%lx\n", aspace, vaddr);

    DEBUG_ASSERT(aspace);
    DEBUG_ASSERT(aspace->tt_virt);

    DEBUG_ASSERT(is_valid_vaddr(aspace, vaddr));
    if (!is_valid_vaddr(aspace, vaddr))
        return ERR_OUT_OF_RANGE;

    vaddr = adjusted_vaddr(aspace, vaddr);
    /* compute shift values based on if this address space is for kernel or user space */
    if (aspace->flags & ARCH_ASPACE_FLAG_KERNEL) {
        index_shift = MMU_KERNEL_TOP_SHIFT;
        page_size_shift = MMU_KERNEL_PAGE_SIZE_SHIFT;

        vaddr_t kernel_base = ~0UL << MMU_KERNEL_SIZE_SHIFT;
        vaddr_rem = vaddr - kernel_base;

        index = vaddr_rem >> index_shift;
        ASSERT(index < MMU_KERNEL_PAGE_TABLE_ENTRIES_TOP);
    } else {
        index_shift = MMU_USER_TOP_SHIFT;
        page_size_shift = MMU_USER_PAGE_SIZE_SHIFT;

        vaddr_rem = vaddr;
        index = vaddr_rem >> index_shift;
        ASSERT(index < MMU_USER_PAGE_TABLE_ENTRIES_TOP);
    }

    page_table = aspace->tt_virt;

    while (true) {
        index = vaddr_rem >> index_shift;
        vaddr_rem -= (vaddr_t)index << index_shift;
        pte = page_table[index];
        descriptor_type = pte & MMU_PTE_DESCRIPTOR_MASK;
        pte_addr = pte & MMU_PTE_OUTPUT_ADDR_MASK;

        LTRACEF("va 0x%lx, index %d, index_shift %d, rem 0x%lx, pte 0x%" PRIx64 "\n",
                vaddr, index, index_shift, vaddr_rem, pte);

        if (descriptor_type == MMU_PTE_DESCRIPTOR_INVALID)
            return ERR_NOT_FOUND;

        if (descriptor_type == ((index_shift > page_size_shift) ?
                                MMU_PTE_L012_DESCRIPTOR_BLOCK :
                                MMU_PTE_L3_DESCRIPTOR_PAGE)) {
            break;
        }

        if (index_shift <= page_size_shift ||
                descriptor_type != MMU_PTE_L012_DESCRIPTOR_TABLE) {
            PANIC_UNIMPLEMENTED;
        }

        page_table = paddr_to_kvaddr(pte_addr);
        index_shift -= page_size_shift - 3;
    }

    if (paddr)
        *paddr = pte_addr + vaddr_rem;
    if (flags) {
        uint mmu_flags = 0;
        if (pte & MMU_PTE_ATTR_NON_SECURE)
            mmu_flags |= ARCH_MMU_FLAG_NS;
        switch (pte & MMU_PTE_ATTR_ATTR_INDEX_MASK) {
            case MMU_PTE_ATTR_STRONGLY_ORDERED:
                mmu_flags |= ARCH_MMU_FLAG_UNCACHED;
                break;
            case MMU_PTE_ATTR_DEVICE:
                mmu_flags |= ARCH_MMU_FLAG_UNCACHED_DEVICE;
                break;
            case MMU_PTE_ATTR_NORMAL_MEMORY_TAGGED:
                mmu_flags |= ARCH_MMU_FLAG_TAGGED;
                break;
            case MMU_PTE_ATTR_NORMAL_MEMORY:
                mmu_flags |= ARCH_MMU_FLAG_CACHED;
                break;
            default:
                PANIC_UNIMPLEMENTED;
        }
        switch (pte & MMU_PTE_ATTR_AP_MASK) {
            case MMU_PTE_ATTR_AP_P_RW_U_NA:
                break;
            case MMU_PTE_ATTR_AP_P_RW_U_RW:
                mmu_flags |= ARCH_MMU_FLAG_PERM_USER;
                break;
            case MMU_PTE_ATTR_AP_P_RO_U_NA:
                mmu_flags |= ARCH_MMU_FLAG_PERM_RO;
                break;
            case MMU_PTE_ATTR_AP_P_RO_U_RO:
                mmu_flags |= ARCH_MMU_FLAG_PERM_USER | ARCH_MMU_FLAG_PERM_RO;
                break;
        }
        /*
         * Based on whether or not this is a user page, check UXN or PXN
         * bit to determine if it's an executable page.
         */
        if (mmu_flags & ARCH_MMU_FLAG_PERM_USER) {
            DEBUG_ASSERT(pte & MMU_PTE_ATTR_PXN);
            if (pte & MMU_PTE_ATTR_UXN) {
                mmu_flags |= ARCH_MMU_FLAG_PERM_NO_EXECUTE;
            }
        } else {
            DEBUG_ASSERT(pte & MMU_PTE_ATTR_UXN);
            if (pte & MMU_PTE_ATTR_PXN) {
                /* Privileged page, check the PXN bit. */
                mmu_flags |= ARCH_MMU_FLAG_PERM_NO_EXECUTE;
            }
        }
        *flags = mmu_flags;
    }
    LTRACEF("va 0x%lx, paddr 0x%lx, flags 0x%x\n",
            vaddr, paddr ? *paddr : ~0UL, flags ? *flags : ~0U);
    return 0;
}

static int alloc_page_table(paddr_t *paddrp, uint page_size_shift)
{
    size_t size = 1U << page_size_shift;

    LTRACEF("page_size_shift %u\n", page_size_shift);

    if (size >= PAGE_SIZE) {
        size_t count = size / PAGE_SIZE;
        size_t ret = pmm_alloc_contiguous(count, page_size_shift, paddrp, NULL);
        if (ret != count)
            return ERR_NO_MEMORY;
    } else {
        void *vaddr = memalign(size, size);
        if (!vaddr)
            return ERR_NO_MEMORY;
        *paddrp = vaddr_to_paddr(vaddr);
        if (*paddrp == 0) {
            free(vaddr);
            return ERR_NO_MEMORY;
        }
    }

    LTRACEF("allocated 0x%lx\n", *paddrp);
    return 0;
}

static void free_page_table(void *vaddr, paddr_t paddr, uint page_size_shift)
{
    LTRACEF("vaddr %p paddr 0x%lx page_size_shift %u\n", vaddr, paddr, page_size_shift);

    size_t size = 1U << page_size_shift;
    vm_page_t *page;

    if (size >= PAGE_SIZE) {
        page = paddr_to_vm_page(paddr);
        if (!page)
            panic("bad page table paddr 0x%lx\n", paddr);
        pmm_free_page(page);
    } else {
        free(vaddr);
    }
}
#endif /* EARLY_MMU */

static pte_t *arm64_mmu_get_page_table(vaddr_t index, uint page_size_shift, pte_t *page_table)
{
    pte_t pte;
    paddr_t paddr;
    void *vaddr;
    int ret;

    pte = page_table[index];
    switch (pte & MMU_PTE_DESCRIPTOR_MASK) {
        case MMU_PTE_DESCRIPTOR_INVALID:
            ret = alloc_page_table(&paddr, page_size_shift);
            if (ret) {
                TRACEF("failed to allocate page table\n");
                return NULL;
            }
            vaddr = paddr_to_kvaddr(paddr);

            LTRACEF("allocated page table, vaddr %p, paddr 0x%lx\n", vaddr, paddr);
            memset(vaddr, MMU_PTE_DESCRIPTOR_INVALID, 1U << page_size_shift);

            __asm__ volatile("dmb ishst" ::: "memory");

            pte = paddr | MMU_PTE_L012_DESCRIPTOR_TABLE;
            page_table[index] = pte;
            LTRACEF("pte %p[0x%lx] = 0x%" PRIx64 "\n", page_table, index, pte);
            return vaddr;

        case MMU_PTE_L012_DESCRIPTOR_TABLE:
            paddr = pte & MMU_PTE_OUTPUT_ADDR_MASK;
            LTRACEF("found page table 0x%lx\n", paddr);
            return paddr_to_kvaddr(paddr);

        case MMU_PTE_L012_DESCRIPTOR_BLOCK:
            return NULL;

        default:
            PANIC_UNIMPLEMENTED;
    }
}

static bool page_table_is_clear(pte_t *page_table, uint page_size_shift)
{
    int i;
    int count = 1U << (page_size_shift - 3);
    pte_t pte;

    for (i = 0; i < count; i++) {
        pte = page_table[i];
        if (pte != MMU_PTE_DESCRIPTOR_INVALID) {
            LTRACEF("page_table at %p still in use, index %d is 0x%" PRIx64 "\n",
                    page_table, i, pte);
            return false;
        }
    }

    LTRACEF("page table at %p is clear\n", page_table);
    return true;
}

static void arm64_mmu_unmap_pt(vaddr_t vaddr, vaddr_t vaddr_rel,
                               size_t size,
                               uint index_shift, uint page_size_shift,
                               pte_t *page_table, uint asid)
{
    pte_t *next_page_table;
    vaddr_t index;
    size_t chunk_size;
    vaddr_t vaddr_rem;
    vaddr_t block_size;
    vaddr_t block_mask;
    pte_t pte;
    paddr_t page_table_paddr;

    LTRACEF("vaddr 0x%lx, vaddr_rel 0x%lx, size 0x%lx, index shift %d, page_size_shift %d, page_table %p\n",
            vaddr, vaddr_rel, size, index_shift, page_size_shift, page_table);

    while (size) {
        block_size = 1UL << index_shift;
        block_mask = block_size - 1;
        vaddr_rem = vaddr_rel & block_mask;
        chunk_size = MIN(size, block_size - vaddr_rem);
        index = vaddr_rel >> index_shift;

        pte = page_table[index];

        if (index_shift > page_size_shift &&
                (pte & MMU_PTE_DESCRIPTOR_MASK) == MMU_PTE_L012_DESCRIPTOR_TABLE) {
            page_table_paddr = pte & MMU_PTE_OUTPUT_ADDR_MASK;
            next_page_table = paddr_to_kvaddr(page_table_paddr);
            arm64_mmu_unmap_pt(vaddr, vaddr_rem, chunk_size,
                               index_shift - (page_size_shift - 3),
                               page_size_shift,
                               next_page_table, asid);
            if (chunk_size == block_size ||
                    page_table_is_clear(next_page_table, page_size_shift)) {
                LTRACEF("pte %p[0x%lx] = 0 (was page table)\n", page_table, index);
                page_table[index] = MMU_PTE_DESCRIPTOR_INVALID;
                __asm__ volatile("dmb ishst" ::: "memory");
                free_page_table(next_page_table, page_table_paddr, page_size_shift);
            }
        } else if (pte) {
            LTRACEF("pte %p[0x%lx] = 0\n", page_table, index);
            page_table[index] = MMU_PTE_DESCRIPTOR_INVALID;
            CF;
            if (asid == MMU_ARM64_GLOBAL_ASID)
                ARM64_TLBI(vaae1is, vaddr >> 12);
            else
                ARM64_TLBI(vae1is, vaddr >> 12 | (vaddr_t)asid << 48);
        } else {
            LTRACEF("pte %p[0x%lx] already clear\n", page_table, index);
        }
        size -= chunk_size;
        if (!size) {
            break;
        }
        /* Early out avoids a benign overflow. */
        vaddr += chunk_size;
        vaddr_rel += chunk_size;
    }
}

static int arm64_mmu_map_pt(vaddr_t vaddr_in, vaddr_t vaddr_rel_in,
                            paddr_t paddr_in,
                            size_t size_in, pte_t attrs,
                            uint index_shift, uint page_size_shift,
                            pte_t *page_table, uint asid, bool replace)
{
    int ret;
    pte_t *next_page_table;
    vaddr_t index;
    vaddr_t vaddr = vaddr_in;
    vaddr_t vaddr_rel = vaddr_rel_in;
    paddr_t paddr = paddr_in;
    size_t size = size_in;
    size_t chunk_size;
    vaddr_t vaddr_rem;
    vaddr_t block_size;
    vaddr_t block_mask;
    pte_t pte;

    LTRACEF("vaddr 0x%lx, vaddr_rel 0x%lx, paddr 0x%lx, size 0x%lx, attrs 0x%" PRIx64 ", index shift %d, page_size_shift %d, page_table %p\n",
            vaddr, vaddr_rel, paddr, size, attrs,
            index_shift, page_size_shift, page_table);

    if ((vaddr_rel | paddr | size) & ((1UL << page_size_shift) - 1)) {
        TRACEF("not page aligned\n");
        return ERR_INVALID_ARGS;
    }

    while (size) {
        block_size = 1UL << index_shift;
        block_mask = block_size - 1;
        vaddr_rem = vaddr_rel & block_mask;
        chunk_size = MIN(size, block_size - vaddr_rem);
        index = vaddr_rel >> index_shift;

        if (((vaddr_rel | paddr) & block_mask) ||
                (chunk_size != block_size) ||
                (index_shift > MMU_PTE_DESCRIPTOR_BLOCK_MAX_SHIFT)) {
            next_page_table = arm64_mmu_get_page_table(index, page_size_shift,
                              page_table);
            if (!next_page_table)
                goto err;

            ret = arm64_mmu_map_pt(vaddr, vaddr_rem, paddr, chunk_size, attrs,
                                   index_shift - (page_size_shift - 3),
                                   page_size_shift, next_page_table, asid,
                                   replace);
            if (ret)
                goto err;
        } else {
            pte = page_table[index];
            if (!pte && replace) {
                TRACEF("page table entry does not exist yet, index 0x%lx, 0x%" PRIx64 "\n",
                       index, pte);
                goto err;
            }
            if (pte && !replace) {
                TRACEF("page table entry already in use, index 0x%lx, 0x%" PRIx64 "\n",
                       index, pte);
                goto err;
            }

            pte = paddr | attrs;
            if (index_shift > page_size_shift)
                pte |= MMU_PTE_L012_DESCRIPTOR_BLOCK;
            else
                pte |= MMU_PTE_L3_DESCRIPTOR_PAGE;

            LTRACEF("pte %p[0x%lx] = 0x%" PRIx64 "\n", page_table, index, pte);
            page_table[index] = pte;
            if (replace) {
                CF;
                if (asid == MMU_ARM64_GLOBAL_ASID)
                    ARM64_TLBI(vaae1is, vaddr >> 12);
                else
                    ARM64_TLBI(vae1is, vaddr >> 12 | (vaddr_t)asid << 48);
            }
        }
        size -= chunk_size;
        if (!size) {
            break;
        }
        /* Note: early out avoids a benign overflow. */
        vaddr += chunk_size;
        vaddr_rel += chunk_size;
        paddr += chunk_size;
    }

    return 0;

err:
    arm64_mmu_unmap_pt(vaddr_in, vaddr_rel_in, size_in - size,
                       index_shift, page_size_shift, page_table, asid);
    DSB;
    return ERR_GENERIC;
}

#ifndef EARLY_MMU
int arm64_mmu_map(vaddr_t vaddr, paddr_t paddr, size_t size, pte_t attrs,
                  vaddr_t vaddr_base, uint top_size_shift,
                  uint top_index_shift, uint page_size_shift,
                  pte_t *top_page_table, uint asid, bool replace)
{
    int ret;
    vaddr_t vaddr_rel = vaddr - vaddr_base;
    vaddr_t vaddr_rel_max = 1UL << top_size_shift;

    LTRACEF("vaddr 0x%lx, paddr 0x%lx, size 0x%lx, attrs 0x%" PRIx64 ", asid 0x%x\n",
            vaddr, paddr, size, attrs, asid);

    if (vaddr_rel > vaddr_rel_max - size || size > vaddr_rel_max) {
        TRACEF("vaddr 0x%lx, size 0x%lx out of range vaddr 0x%lx, size 0x%lx\n",
               vaddr, size, vaddr_base, vaddr_rel_max);
        return ERR_INVALID_ARGS;
    }

    if (!top_page_table) {
        TRACEF("page table is NULL\n");
        return ERR_INVALID_ARGS;
    }

    ret = arm64_mmu_map_pt(vaddr, vaddr_rel, paddr, size, attrs,
                           top_index_shift, page_size_shift, top_page_table,
                           asid, replace);
    DSB;
    return ret;
}

int arm64_mmu_unmap(vaddr_t vaddr, size_t size,
                    vaddr_t vaddr_base, uint top_size_shift,
                    uint top_index_shift, uint page_size_shift,
                    pte_t *top_page_table, uint asid)
{
    vaddr_t vaddr_rel = vaddr - vaddr_base;
    vaddr_t vaddr_rel_max = 1UL << top_size_shift;

    LTRACEF("vaddr 0x%lx, size 0x%lx, asid 0x%x\n", vaddr, size, asid);

    if (vaddr_rel > vaddr_rel_max - size || size > vaddr_rel_max) {
        TRACEF("vaddr 0x%lx, size 0x%lx out of range vaddr 0x%lx, size 0x%lx\n",
               vaddr, size, vaddr_base, vaddr_rel_max);
        return ERR_INVALID_ARGS;
    }

    if (!top_page_table) {
        TRACEF("page table is NULL\n");
        return ERR_INVALID_ARGS;
    }

    arm64_mmu_unmap_pt(vaddr, vaddr_rel, size,
                       top_index_shift, page_size_shift, top_page_table, asid);
    DSB;
    return 0;
}

static void arm64_tlbflush_if_asid_changed(arch_aspace_t *aspace, asid_t asid)
{
    THREAD_LOCK(state);
    if (asid != arch_mmu_asid(aspace)) {
        TRACEF("asid changed for aspace %p while mapping or unmapping memory, 0x%" PRIxASID " -> 0x%" PRIxASID ", flush all tlbs\n",
               aspace, asid, aspace->asid);
        ARM64_TLBI_NOADDR(vmalle1is);
        DSB;
    }
    THREAD_UNLOCK(state);
}

static int arm64_mmu_map_aspace(arch_aspace_t *aspace, vaddr_t vaddr, paddr_t paddr, size_t count, uint flags,
                 bool replace)
{
    LTRACEF("vaddr 0x%lx paddr 0x%lx count %zu flags 0x%x\n", vaddr, paddr, count, flags);

    DEBUG_ASSERT(aspace);
    DEBUG_ASSERT(aspace->tt_virt);

    DEBUG_ASSERT(is_valid_vaddr(aspace, vaddr));
    if (!is_valid_vaddr(aspace, vaddr))
        return ERR_OUT_OF_RANGE;

    vaddr = adjusted_vaddr(aspace, vaddr);

    /* paddr and vaddr must be aligned */
    DEBUG_ASSERT(IS_PAGE_ALIGNED(vaddr));
    DEBUG_ASSERT(IS_PAGE_ALIGNED(paddr));
    if (!IS_PAGE_ALIGNED(vaddr) || !IS_PAGE_ALIGNED(paddr))
        return ERR_INVALID_ARGS;

    if (paddr & ~MMU_PTE_OUTPUT_ADDR_MASK) {
        return ERR_INVALID_ARGS;
    }

    if (count == 0)
        return NO_ERROR;

    pte_t pte_attr;
    if (!mmu_flags_to_pte_attr(aspace->flags, flags, &pte_attr)) {
        return ERR_INVALID_ARGS;
    }

    int ret;
    if (aspace->flags & ARCH_ASPACE_FLAG_KERNEL) {
        ret = arm64_mmu_map(vaddr, paddr, count * PAGE_SIZE,
                         pte_attr,
                         ~0UL << MMU_KERNEL_SIZE_SHIFT, MMU_KERNEL_SIZE_SHIFT,
                         MMU_KERNEL_TOP_SHIFT, MMU_KERNEL_PAGE_SIZE_SHIFT,
                         aspace->tt_virt, MMU_ARM64_GLOBAL_ASID, replace);
    } else {
        asid_t asid = arch_mmu_asid(aspace);
        ret = arm64_mmu_map(vaddr, paddr, count * PAGE_SIZE,
                         pte_attr | MMU_PTE_ATTR_NON_GLOBAL,
                         0, MMU_USER_SIZE_SHIFT,
                         MMU_USER_TOP_SHIFT, MMU_USER_PAGE_SIZE_SHIFT,
                         aspace->tt_virt, asid, replace);
        arm64_tlbflush_if_asid_changed(aspace, asid);
    }

    return ret;
}

int arch_mmu_map(arch_aspace_t *aspace, vaddr_t vaddr, paddr_t paddr, size_t count, uint flags)
{
    return arm64_mmu_map_aspace(aspace, vaddr, paddr, count, flags, false);
}

int arch_mmu_map_replace(arch_aspace_t *aspace, vaddr_t vaddr, paddr_t paddr, size_t count, uint flags)
{
    return arm64_mmu_map_aspace(aspace, vaddr, paddr, count, flags, true);
}

int arch_mmu_unmap(arch_aspace_t *aspace, vaddr_t vaddr, size_t count)
{
    LTRACEF("vaddr 0x%lx count %zu\n", vaddr, count);

    DEBUG_ASSERT(aspace);
    DEBUG_ASSERT(aspace->tt_virt);

    DEBUG_ASSERT(is_valid_vaddr(aspace, vaddr));

    if (!is_valid_vaddr(aspace, vaddr))
        return ERR_OUT_OF_RANGE;

    vaddr = adjusted_vaddr(aspace, vaddr);

    DEBUG_ASSERT(IS_PAGE_ALIGNED(vaddr));
    if (!IS_PAGE_ALIGNED(vaddr))
        return ERR_INVALID_ARGS;

    int ret;
    if (aspace->flags & ARCH_ASPACE_FLAG_KERNEL) {
        ret = arm64_mmu_unmap(vaddr, count * PAGE_SIZE,
                           ~0UL << MMU_KERNEL_SIZE_SHIFT, MMU_KERNEL_SIZE_SHIFT,
                           MMU_KERNEL_TOP_SHIFT, MMU_KERNEL_PAGE_SIZE_SHIFT,
                           aspace->tt_virt,
                           MMU_ARM64_GLOBAL_ASID);
    } else {
        asid_t asid = arch_mmu_asid(aspace);
        ret = arm64_mmu_unmap(vaddr, count * PAGE_SIZE,
                           0, MMU_USER_SIZE_SHIFT,
                           MMU_USER_TOP_SHIFT, MMU_USER_PAGE_SIZE_SHIFT,
                           aspace->tt_virt, asid);
        arm64_tlbflush_if_asid_changed(aspace, asid);
    }

    return ret;
}

status_t arch_mmu_init_aspace(arch_aspace_t *aspace, vaddr_t base, size_t size, uint flags)
{
    LTRACEF("aspace %p, base 0x%lx, size 0x%zx, flags 0x%x\n", aspace, base, size, flags);

    DEBUG_ASSERT(aspace);

    /* validate that the base + size is sane and doesn't wrap */
    DEBUG_ASSERT(size > PAGE_SIZE);
    DEBUG_ASSERT(wrap_check(base, size));

    aspace->flags = flags;
    if (flags & ARCH_ASPACE_FLAG_KERNEL) {
        /* at the moment we can only deal with address spaces as globally defined */
        DEBUG_ASSERT(base == ~0UL << MMU_KERNEL_SIZE_SHIFT);
        DEBUG_ASSERT(size == 1UL << MMU_KERNEL_SIZE_SHIFT);

        aspace->base = base;
        aspace->size = size;
        aspace->tt_virt = arm64_kernel_translation_table;
        aspace->tt_phys = vaddr_to_paddr(aspace->tt_virt);
    } else {
        size_t page_table_size = MMU_USER_PAGE_TABLE_ENTRIES_TOP * sizeof(pte_t);
        //DEBUG_ASSERT(base >= 0);
        DEBUG_ASSERT(base + size <= 1UL << MMU_USER_SIZE_SHIFT);

        aspace->base = base;
        aspace->size = size;

        pte_t *va = memalign(page_table_size, page_table_size);
        if (!va)
            return ERR_NO_MEMORY;

        aspace->tt_virt = va;
        aspace->tt_phys = vaddr_to_paddr(aspace->tt_virt);

        /* zero the top level translation table */
        memset(aspace->tt_virt, 0, page_table_size);
    }

    LTRACEF("tt_phys 0x%lx tt_virt %p\n", aspace->tt_phys, aspace->tt_virt);

    return NO_ERROR;
}

status_t arch_mmu_destroy_aspace(arch_aspace_t *aspace)
{
    LTRACEF("aspace %p\n", aspace);

    DEBUG_ASSERT(aspace);
    DEBUG_ASSERT((aspace->flags & ARCH_ASPACE_FLAG_KERNEL) == 0);

    // XXX make sure it's not mapped

    free(aspace->tt_virt);

    return NO_ERROR;
}

void arch_mmu_context_switch(arch_aspace_t *aspace)
{
    bool flush_tlb;

    if (TRACE_CONTEXT_SWITCH)
        TRACEF("aspace %p\n", aspace);

    flush_tlb = vmm_asid_activate(aspace, ARM64_ASID_BITS);

    uint64_t tcr;
    uint64_t ttbr;
    if (aspace) {
        DEBUG_ASSERT((aspace->flags & ARCH_ASPACE_FLAG_KERNEL) == 0);

        tcr = MMU_TCR_FLAGS_USER;
        ttbr = (arch_mmu_asid(aspace) << 48) | aspace->tt_phys;
        ARM64_WRITE_SYSREG(ttbr0_el1, ttbr);

    } else {
        tcr = MMU_TCR_FLAGS_KERNEL;
    }

#if KERNEL_PAC_ENABLED
        /* If TBI0 is set, also set TBID0.
         *  TBID0 is RES0 if FEAT_PAuth is not supported, so this must be
         *  conditional.
         */
        if ((tcr & MMU_TCR_TBI0) != 0 && arch_pac_address_supported()) {
            tcr |= MMU_TCR_TBID0;
        }
#endif

    if (TRACE_CONTEXT_SWITCH) {
        if (aspace) {
            TRACEF("ttbr 0x%" PRIx64 ", tcr 0x%" PRIx64 "\n", ttbr, tcr);
        } else {
            TRACEF("tcr 0x%" PRIx64 "\n", tcr);
        }
    }

    ARM64_WRITE_SYSREG(tcr_el1, tcr); /* TODO: only needed when switching between kernel and user threads */

    if (flush_tlb) {
        ARM64_TLBI_NOADDR(vmalle1);
        DSB;
    }
}
#endif /* EARLY_MMU */
