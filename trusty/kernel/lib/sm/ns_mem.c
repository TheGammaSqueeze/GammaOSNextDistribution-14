/*
 * Copyright (c) 2015 Google Inc. All rights reserved
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

#include <arch/mmu.h>
#include <assert.h>
#include <debug.h>
#include <err.h>
#include <inttypes.h>
#include <lib/sm.h>
#include <trace.h>

#define LOCAL_TRACE 0

/* 48-bit physical address 47:12 */
#define NS_PTE_PHYSADDR_MASK (0xFFFFFFFFF000ULL)
#define NS_PTE_PHYSADDR(pte) ((pte) & (NS_PTE_PHYSADDR_MASK))
#define NS_PTE_ATTR(pte) ((pte) & ~(NS_PTE_PHYSADDR_MASK))

/* Access permissions AP[2:1]
 *	EL0	EL1
 * 00	None	RW
 * 01	RW	RW
 * 10	None	RO
 * 11	RO	RO
 */
#define NS_PTE_AP(pte) (((pte) >> 6) & 0x3)
#define NS_PTE_AP_U_RW(pte) (NS_PTE_AP(pte) == 0x1)
#define NS_PTE_AP_U(pte) (NS_PTE_AP(pte) & 0x1)
#define NS_PTE_AP_RO(pte) (NS_PTE_AP(pte) & 0x2)

/* Shareablility attrs */
#define NS_PTE_ATTR_SHAREABLE(pte) (((pte) >> 8) & 0x3)

/* cache attrs encoded in the top bits 55:49 of the PTE*/
#define NS_PTE_ATTR_MAIR(pte) (((pte) >> 48) & 0xFF)

/* Inner cache attrs MAIR_ATTR_N[3:0] */
#define NS_PTE_ATTR_INNER(pte) ((NS_PTE_ATTR_MAIR(pte)) & 0xF)

/* Outer cache attrs MAIR_ATTR_N[7:4] */
#define NS_PTE_ATTR_OUTER(pte) (((NS_PTE_ATTR_MAIR(pte)) & 0xF0) >> 4)

/* Normal memory */
/* inner and outer write back read/write allocate */
#define NS_MAIR_NORMAL_CACHED_WB_RWA 0xFF
/* inner and outer write back read/write allocate tagged */
#define NS_MAIR_NORMAL_CACHED_WB_RWA_TAGGED 0xF0
/* inner and outer write through read allocate */
#define NS_MAIR_NORMAL_CACHED_WT_RA 0xAA
/* inner and outer wriet back, read allocate */
#define NS_MAIR_NORMAL_CACHED_WB_RA 0xEE
/* uncached */
#define NS_MAIR_NORMAL_UNCACHED 0x44

/* Device memory */
/* nGnRnE (strongly ordered) */
#define NS_MAIR_DEVICE_STRONGLY_ORDERED 0x00
/* nGnRE  (device) */
#define NS_MAIR_DEVICE 0x04
/* GRE */
#define NS_MAIR_DEVICE_GRE 0x0C

/* sharaeble attributes */
#define NS_NON_SHAREABLE 0x0
#define NS_OUTER_SHAREABLE 0x2
#define NS_INNER_SHAREABLE 0x3

#define NS_PTE_ATTR_DEFAULT_CACHED \
    ((uint64_t)NS_MAIR_NORMAL_CACHED_WB_RWA << 48 | NS_INNER_SHAREABLE << 8)

/* helper function to decode ns memory attrubutes  */
status_t sm_decode_ns_memory_attr(struct ns_page_info* pinf,
                                  ns_addr_t* ppa,
                                  uint* pmmu) {
    uint mmu_flags = 0;

    if (!pinf)
        return ERR_INVALID_ARGS;

    LTRACEF("raw=0x%" PRIx64 ": pa=0x%llx: mair=0x%x, sharable=0x%x\n",
            pinf->attr, NS_PTE_PHYSADDR(pinf->attr),
            (uint)NS_PTE_ATTR_MAIR(pinf->attr),
            (uint)NS_PTE_ATTR_SHAREABLE(pinf->attr));

    if (ppa)
        *ppa = (ns_addr_t)NS_PTE_PHYSADDR(pinf->attr);

    if (pmmu) {
        uint64_t attr = NS_PTE_ATTR(pinf->attr);
        if (attr == 0) {
            if (sm_get_api_version() >= TRUSTY_API_VERSION_PHYS_MEM_OBJ) {
                LTRACEF("Unsupported 0 memory attr\n");
                return ERR_NOT_SUPPORTED;
            }
            /*
             * Some existing clients don't pass attibutes and assume cached
             * write-able memory.
             */
            attr = NS_PTE_ATTR_DEFAULT_CACHED;
        }

        /* match settings to mmu flags */
        switch ((uint)NS_PTE_ATTR_MAIR(attr)) {
        /*
         * Normal tagged memory and normal untagged memory can be treated the
         * same here, because we only ever map it as untagged in Trusty. This
         * is enforced in vmm by only allowing pmm objecs to be tagged.
         */
        case NS_MAIR_NORMAL_CACHED_WB_RWA:
        case NS_MAIR_NORMAL_CACHED_WB_RWA_TAGGED:
            mmu_flags |= ARCH_MMU_FLAG_CACHED;
            break;
        case NS_MAIR_NORMAL_UNCACHED:
            mmu_flags |= ARCH_MMU_FLAG_UNCACHED;
            break;
        default:
            LTRACEF("Unsupported memory attr 0x%x\n",
                    (uint)NS_PTE_ATTR_MAIR(attr));
            return ERR_NOT_SUPPORTED;
        }
#if WITH_SMP | WITH_SHAREABLE_CACHE
        if (mmu_flags == ARCH_MMU_FLAG_CACHED) {
            if (NS_PTE_ATTR_SHAREABLE(attr) != NS_INNER_SHAREABLE) {
                LTRACEF("Unsupported sharable attr 0x%x\n",
                        (uint)NS_PTE_ATTR_SHAREABLE(attr));
                return ERR_NOT_SUPPORTED;
            }
        }
#endif
        if (NS_PTE_AP_U(attr))
            mmu_flags |= ARCH_MMU_FLAG_PERM_USER;

        if (NS_PTE_AP_RO(attr))
            mmu_flags |= ARCH_MMU_FLAG_PERM_RO;

        *pmmu = mmu_flags | ARCH_MMU_FLAG_NS | ARCH_MMU_FLAG_PERM_NO_EXECUTE;
    }

    return NO_ERROR;
}
