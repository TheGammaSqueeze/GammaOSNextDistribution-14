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
#include <kernel/vm.h>
#include "vm_priv.h"

#include <assert.h>
#include <trace.h>
#include <err.h>
#include <string.h>
#include <lk/init.h>
#include <lib/console.h>
#include <arch/mmu.h>
#include <kernel/thread.h>
#include <debug.h>
#include <inttypes.h>

#define LOCAL_TRACE 0

extern int _start;
extern int _end;
extern char __code_start;
extern char __rodata_start;
extern char __data_start;

/* mark the physical pages backing a range of virtual as in use.
 * allocate the physical pages and throw them away */
static void mark_pages_in_use(vaddr_t va, size_t len)
{
    LTRACEF("va 0x%" PRIxVADDR ", len 0x%zx\n", va, len);

    struct list_node list;
    list_initialize(&list);

    /* make sure we are inclusive of all of the pages in the address range */
    len = page_align(len + (va & (PAGE_SIZE - 1)));
    va = round_down(va, PAGE_SIZE);

    LTRACEF("aligned va 0x%" PRIxVADDR ", len 0x%zx\n", va, len);

    for (size_t offset = 0; offset < len; offset += PAGE_SIZE) {
        uint flags;
        paddr_t pa;

        status_t err = arch_mmu_query(&vmm_get_kernel_aspace()->arch_aspace, va + offset, &pa, &flags);
        if (err >= 0) {
            //LTRACEF("va 0x%lx, pa 0x%lx, flags 0x%x, err %d\n", va + offset, pa, flags, err);

            /* allocate the range, throw the results away */
            if (pmm_alloc_range(pa, 1, &list) != 1) {
              panic("Could not alloc pa 0x%" PRIxPADDR "\n", pa);
            }
        } else {
            panic("Could not find pa for va 0x%" PRIxVADDR "\n", va);
        }
    }
}

static void vm_init_preheap(uint level)
{
    LTRACE_ENTRY;

    /* allow the vmm a shot at initializing some of its data structures */
    vmm_init_preheap();

    /* mark all of the kernel pages in use */
    LTRACEF("marking all kernel pages as used\n");
    mark_pages_in_use((vaddr_t)&_start, ((uintptr_t)&_end - (uintptr_t)&_start));

    /* the boot time allocator should not be used after this */
    uintptr_t alloc_start = boot_alloc_start;
    uintptr_t alloc_end = boot_alloc_end;
    boot_alloc_start = 0;
    boot_alloc_end = 0;

    /* mark the physical pages used by the boot time allocator */
    if (alloc_end != alloc_start) {
        LTRACEF("marking boot alloc used from 0x%" PRIxPTR " to 0x%" PRIxPTR "\n", alloc_start, alloc_end);

        /*
         * if _end is not page aligned, the kernel and the boot time allocator
         * may share a page. Do not mark this page a second time.
         */
        ASSERT(alloc_start == (uintptr_t)&_end);
        alloc_start = page_align(alloc_start);

        /*
         * aligning start could move it past end. In this case, the data is in a
         * single page and it has already been marked.
         */
        if (alloc_start < alloc_end) {
            mark_pages_in_use(alloc_start, alloc_end - alloc_start);
        }
    }
}

static void vm_init_postheap(uint level)
{
    LTRACE_ENTRY;

    vmm_init();

    /* create vmm regions to cover what is already there from the initial mapping table */
    struct mmu_initial_mapping *map = mmu_initial_mappings;
    while (map->size > 0) {
        if (!(map->flags & MMU_INITIAL_MAPPING_TEMPORARY)) {
            vmm_reserve_space(vmm_get_kernel_aspace(), map->name, map->size, map->virt);
        }

        map++;
    }
}

void vm_assign_initial_dynamic(paddr_t kernel_start, size_t ram_size)
{
    for (struct mmu_initial_mapping *m = mmu_initial_mappings; m->size; m++) {
        if (m->flags & MMU_INITIAL_MAPPING_FLAG_DYNAMIC) {
            m->phys = kernel_start;
            m->size = ram_size;
        }
    }
}

void vm_map_initial_mappings(void)
{
    for (struct mmu_initial_mapping *m = mmu_initial_mappings; m->size; m++) {
        paddr_t paddr = m->phys;
        vaddr_t vaddr = m->virt;
        size_t mapping_size = m->size;
        for (;;) {
            size_t size = mapping_size;
            uint flags;
            if (m->flags & MMU_INITIAL_MAPPING_FLAG_UNCACHED) {
                flags = ARCH_MMU_FLAG_UNCACHED | ARCH_MMU_FLAG_PERM_NO_EXECUTE;
            } else if (m->flags & MMU_INITIAL_MAPPING_FLAG_DEVICE) {
                flags = ARCH_MMU_FLAG_UNCACHED_DEVICE | ARCH_MMU_FLAG_PERM_NO_EXECUTE;
            } else {
                /* Determine the segment in which the memory resides and set appropriate
                 *  attributes.  In order to handle offset kernels, the following rules are
                 *  implemented below:
                 *      KERNEL_BASE    to __code_start             -read/write (see note below)
                 *      __code_start   to __rodata_start (.text)   -read only
                 *      __rodata_start to __data_start   (.rodata) -read only, execute never
                 *      __data_start   to .....          (.data)   -read/write
                 *
                 *  The space below __code_start is presently left as read/write (same as .data)
                 *   mainly as a workaround for the raspberry pi boot process.  Boot vectors for
                 *   secondary CPUs are in this area and need to be updated by cpu0 once the system
                 *   is ready to boot the secondary processors.
                 *   TODO: handle this via mmu_initial_mapping entries, which may need to be
                 *         extended with additional flag types
                 */
                flags = ARCH_MMU_FLAG_CACHED;
                if (paddr < (paddr_t)&__code_start) {
                    /* If page is below  the entry point (_start) mark as kernel data */
                    size = (paddr_t)&__code_start - paddr;
                    flags |= ARCH_MMU_FLAG_PERM_NO_EXECUTE;
                } else if (paddr < (paddr_t)&__rodata_start) {
                    size = (paddr_t)&__rodata_start - paddr;
                    flags |= ARCH_MMU_FLAG_PERM_RO;
                } else if (paddr < (paddr_t)&__data_start) {
                    size = (paddr_t)&__data_start - paddr;
                    flags |= ARCH_MMU_FLAG_PERM_RO;
                    flags |= ARCH_MMU_FLAG_PERM_NO_EXECUTE;
                } else {
                    ASSERT(paddr < (paddr_t)&_end);
                    ASSERT(((paddr_t)&_end - paddr) <= mapping_size);
                    flags |= ARCH_MMU_FLAG_PERM_NO_EXECUTE;
                }
            }

            ASSERT(size <= mapping_size);
            arch_mmu_map_early(vaddr, paddr, size, flags);

            mapping_size -= size;
            if (!mapping_size) {
                break;
            }
            paddr += size;
            vaddr += size;
        }
    }
}

void *kvaddr_get_range(size_t* size_return)
{
    *size_return = mmu_initial_mappings->size;
    return (void*)mmu_initial_mappings->virt;
}

void *paddr_to_kvaddr(paddr_t pa)
{
    /* slow path to do reverse lookup */
    struct mmu_initial_mapping *map = mmu_initial_mappings;
    while (map->size > 0) {
        if (!(map->flags & MMU_INITIAL_MAPPING_TEMPORARY) &&
                pa >= map->phys &&
                pa <= map->phys + map->size - 1) {
            return (void *)(map->virt + (pa - map->phys));
        }
        map++;
    }
    return pmm_paddr_to_kvaddr(pa);
}

paddr_t vaddr_to_paddr(void *ptr)
{
    vmm_aspace_t *aspace = vaddr_to_aspace(ptr);
    if (!aspace)
        return (paddr_t)NULL;

    paddr_t pa;
    status_t rc = arch_mmu_query(&aspace->arch_aspace, (vaddr_t)ptr, &pa, NULL);
    if (rc)
        return (paddr_t)NULL;

    return pa;
}

vmm_aspace_t *vaddr_to_aspace(void *ptr)
{
    if (is_kernel_address((vaddr_t)ptr)) {
        return vmm_get_kernel_aspace();
    } else if (is_user_address((vaddr_t)ptr)) {
        return get_current_thread()->aspace;
    } else {
        return NULL;
    }
}

static int cmd_vm(int argc, const cmd_args *argv)
{
    if (argc < 2) {
notenoughargs:
        printf("not enough arguments\n");
usage:
        printf("usage:\n");
        printf("%s phys2virt <address>\n", argv[0].str);
        printf("%s virt2phys <address>\n", argv[0].str);
        printf("%s map <phys> <virt> <count> <flags>\n", argv[0].str);
        printf("%s unmap <virt> <count>\n", argv[0].str);
        return ERR_GENERIC;
    }

    if (!strcmp(argv[1].str, "phys2virt")) {
        if (argc < 3) goto notenoughargs;

        void *ptr = paddr_to_kvaddr((paddr_t)argv[2].u);
        printf("paddr_to_kvaddr returns %p\n", ptr);
    } else if (!strcmp(argv[1].str, "virt2phys")) {
        if (argc < 3) goto notenoughargs;

        vmm_aspace_t *aspace = vaddr_to_aspace((void *)argv[2].u);
        if (!aspace) {
            printf("ERROR: outside of any address space\n");
            return -1;
        }

        paddr_t pa;
        uint flags;
        status_t err = arch_mmu_query(&aspace->arch_aspace, argv[2].u, &pa, &flags);
        printf("arch_mmu_query returns %d\n", err);
        if (err >= 0) {
            printf("\tpa 0x%" PRIxPADDR ", flags 0x%x\n", pa, flags);
        }
    } else if (!strcmp(argv[1].str, "map")) {
        if (argc < 6) goto notenoughargs;

        vmm_aspace_t *aspace = vaddr_to_aspace((void *)argv[2].u);
        if (!aspace) {
            printf("ERROR: outside of any address space\n");
            return -1;
        }

        int err = arch_mmu_map(&aspace->arch_aspace, argv[3].u, argv[2].u, argv[4].u, argv[5].u);
        printf("arch_mmu_map returns %d\n", err);
    } else if (!strcmp(argv[1].str, "unmap")) {
        if (argc < 4) goto notenoughargs;

        vmm_aspace_t *aspace = vaddr_to_aspace((void *)argv[2].u);
        if (!aspace) {
            printf("ERROR: outside of any address space\n");
            return -1;
        }

        int err = arch_mmu_unmap(&aspace->arch_aspace, argv[2].u, argv[3].u);
        printf("arch_mmu_unmap returns %d\n", err);
    } else {
        printf("unknown command\n");
        goto usage;
    }

    return NO_ERROR;
}

STATIC_COMMAND_START
#if LK_DEBUGLEVEL > 0
STATIC_COMMAND("vm", "vm commands", &cmd_vm)
#endif
STATIC_COMMAND_END(vm);

LK_INIT_HOOK(vm_preheap, &vm_init_preheap, LK_INIT_LEVEL_HEAP - 1);
LK_INIT_HOOK(vm, &vm_init_postheap, LK_INIT_LEVEL_VM);
