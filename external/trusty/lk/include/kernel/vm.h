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

/* some assembly #defines, need to match the structure below */
#if IS_64BIT
#define __MMU_INITIAL_MAPPING_PHYS_OFFSET 0
#define __MMU_INITIAL_MAPPING_VIRT_OFFSET 8
#define __MMU_INITIAL_MAPPING_SIZE_OFFSET 16
#define __MMU_INITIAL_MAPPING_FLAGS_OFFSET 24
#define __MMU_INITIAL_MAPPING_SIZE        40
#else
#define __MMU_INITIAL_MAPPING_PHYS_OFFSET 0
#define __MMU_INITIAL_MAPPING_VIRT_OFFSET 4
#define __MMU_INITIAL_MAPPING_SIZE_OFFSET 8
#define __MMU_INITIAL_MAPPING_FLAGS_OFFSET 12
#define __MMU_INITIAL_MAPPING_SIZE        20
#endif

/* flags for initial mapping struct */
#define MMU_INITIAL_MAPPING_TEMPORARY     (0x1)
#define MMU_INITIAL_MAPPING_FLAG_UNCACHED (0x2)
#define MMU_INITIAL_MAPPING_FLAG_DEVICE   (0x4)
#define MMU_INITIAL_MAPPING_FLAG_DYNAMIC  (0x8)  /* entry has to be patched up by platform_reset */

#ifndef ASSEMBLY

#include <sys/types.h>
#include <stdint.h>
#include <compiler.h>
#include <list.h>
#include <stdlib.h>
#include <arch.h>
#include <arch/mmu.h>
#include <arch/tbi.h>
#include <kernel/vm_obj.h>
#include <lib/binary_search_tree.h>
#include <lk/reflist.h>

__BEGIN_CDECLS

static inline uintptr_t page_align(uintptr_t p) {
    return align(p, PAGE_SIZE);
}

#define IS_PAGE_ALIGNED(x) IS_ALIGNED(x, PAGE_SIZE)

struct mmu_initial_mapping {
    paddr_t phys;
    vaddr_t virt;
    size_t  size;
    unsigned int flags;
    const char *name;
};

/* Assert that the assembly macros above match this struct. */
STATIC_ASSERT(__offsetof(struct mmu_initial_mapping, phys) == __MMU_INITIAL_MAPPING_PHYS_OFFSET);
STATIC_ASSERT(__offsetof(struct mmu_initial_mapping, virt) == __MMU_INITIAL_MAPPING_VIRT_OFFSET);
STATIC_ASSERT(__offsetof(struct mmu_initial_mapping, size) == __MMU_INITIAL_MAPPING_SIZE_OFFSET);
STATIC_ASSERT(__offsetof(struct mmu_initial_mapping, flags) == __MMU_INITIAL_MAPPING_FLAGS_OFFSET);
STATIC_ASSERT(sizeof(struct mmu_initial_mapping) == __MMU_INITIAL_MAPPING_SIZE);

/* Platform or target must fill out one of these to set up the initial memory map
 * for kernel and enough IO space to boot.
 */
extern struct mmu_initial_mapping mmu_initial_mappings[];

/* core per page structure */
typedef struct vm_page {
    struct list_node node;

    uint flags : 8;
    uint ref : 24;
} vm_page_t;

#define VM_PAGE_FLAG_NONFREE  (0x1)
#define VM_PAGE_FLAG_RESERVED (0x2)

/* kernel address space */
#ifndef KERNEL_ASPACE_BASE
#define KERNEL_ASPACE_BASE ((vaddr_t)0x80000000UL)
#endif
#ifndef KERNEL_ASPACE_SIZE
#define KERNEL_ASPACE_SIZE ((vaddr_t)0x80000000UL)
#endif

STATIC_ASSERT(KERNEL_ASPACE_BASE + (KERNEL_ASPACE_SIZE - 1) > KERNEL_ASPACE_BASE);

static inline bool is_kernel_address(vaddr_t va)
{
    va = arch_adjusted_vaddr(va, true);
    return (va >= (vaddr_t)KERNEL_ASPACE_BASE && va <= ((vaddr_t)KERNEL_ASPACE_BASE + ((vaddr_t)KERNEL_ASPACE_SIZE - 1)));
}

/* user address space, defaults to below kernel space with a 16MB guard gap on either side */
#ifndef USER_ASPACE_BASE
#define USER_ASPACE_BASE ((vaddr_t)0x01000000UL)
#endif
#ifndef USER_ASPACE_SIZE
#define USER_ASPACE_SIZE ((vaddr_t)KERNEL_ASPACE_BASE - USER_ASPACE_BASE - 0x01000000UL)
#endif

STATIC_ASSERT(USER_ASPACE_BASE + (USER_ASPACE_SIZE - 1) > USER_ASPACE_BASE);

static inline bool is_user_address(vaddr_t va)
{
    va = arch_adjusted_vaddr(va, false);
    return (va >= USER_ASPACE_BASE && va <= (USER_ASPACE_BASE + (USER_ASPACE_SIZE - 1)));
}

/* physical allocator */
typedef struct pmm_arena {
    struct list_node node;
    const char *name;

    uint flags;
    uint priority;

    paddr_t base;
    vaddr_t kvaddr;
    size_t  size;

    size_t free_count;
    /* A subset of free pages that can only be allocated using PMM_ALLOC_FLAG_FROM_RESERVED */
    size_t reserved_count;

    struct vm_page *page_array;
    struct list_node free_list;
} pmm_arena_t;

#define PMM_ARENA_FLAG_KMAP (0x1) /* this arena is already mapped and useful for kallocs */

/* Add a pre-filled memory arena to the physical allocator. */
status_t pmm_add_arena(pmm_arena_t *arena);

/**
 * pmm_add_arena_late_etc() - Add memory arena
 * @arena: pointer to pre-filled &struct arena
 * @reserve_at_beg: number of bytes that needs to be reserved
 *  (marked as in use) at the beginning of arena memory region
 * @reserve_at_end: number of bytes that needs to be reserved
 * (marked as in use) at the end of arena memory region
 *
 * Return: 0 on success, negative err otherwise
 */
status_t pmm_add_arena_late_etc(pmm_arena_t *arena,
                                size_t reserve_at_beg,
                                size_t reserve_at_end);

/* Add a pre-filled arena during late (post vm) stage of boot */
static status_t pmm_add_arena_late(pmm_arena_t *arena) {
    return pmm_add_arena_late_etc(arena, 0, 0);
}

/* Optional flags passed to pmm_alloc */
#define PMM_ALLOC_FLAG_KMAP (1U << 0)
#define PMM_ALLOC_FLAG_CONTIGUOUS (1U << 1)
#define PMM_ALLOC_FLAG_FROM_RESERVED (1U << 2)
#define PMM_ALLOC_FLAG_NO_CLEAR (1U << 3)
#define PMM_ALLOC_FLAG_ALLOW_TAGGED (1U << 4)

struct res_group;

/**
 * pmm_alloc_from_res_group - Allocate and clear @count pages of physical memory.
 * @objp:       Pointer to returned vmm_obj (untouched if return code is not 0).
 * @ref:        Reference to add to *@objp (untouched if return code is not 0).
 * @res_group:  The resource group to use to track this allocation (if not NULL).
 * @count:      Number of pages to allocate. Must be greater than 0.
 * @flags:      Bitmask to optionally restrict allocation to areas that are
 *              already mapped in the kernel, PMM_ALLOC_FLAG_KMAP (e.g for
 *              kernel heap and page tables) and/or to allocate a single
 *              physically contiguous range, PMM_ALLOC_FLAG_CONTIGUOUS.
 * @align_log2: Alignment needed for contiguous allocation, 0 otherwise.
 *
 * Allocate and initialize a vmm_obj that tracks the allocated pages.
 *
 * Return: 0 on success, ERR_NO_MEMORY if there is not enough memory free to
 *         allocate the vmm_obj or the requested page count.
 */
status_t pmm_alloc_from_res_group(struct vmm_obj **objp, struct obj_ref* ref, struct res_group* res_group, uint count,
                   uint32_t flags, uint8_t align_log2);

/**
 * pmm_alloc - Allocate and clear @count pages of physical memory.
 * @objp:       Pointer to returned vmm_obj (untouched if return code is not 0).
 * @ref:        Reference to add to *@objp (untouched if return code is not 0).
 * @count:      Number of pages to allocate. Must be greater than 0.
 * @flags:      Bitmask to optionally restrict allocation to areas that are
 *              already mapped in the kernel, PMM_ALLOC_FLAG_KMAP (e.g for
 *              kernel heap and page tables) and/or to allocate a single
 *              physically contiguous range, PMM_ALLOC_FLAG_CONTIGUOUS.
 * @align_log2: Alignment needed for contiguous allocation, 0 otherwise.
 *
 * Allocate and initialize a vmm_obj that tracks the allocated pages.
 *
 * Return: 0 on success, ERR_NO_MEMORY if there is not enough memory free to
 *         allocate the vmm_obj or the requested page count.
 *
 * Same as `pmm_alloc_from_res_group` above, but with res_group set to `NULL`.
 */
static inline status_t pmm_alloc(struct vmm_obj **objp, struct obj_ref* ref, uint count,
                   uint32_t flags, uint8_t align_log2) {
    return pmm_alloc_from_res_group(objp, ref, NULL, count, flags, align_log2);
}

/* Allocate a specific range of physical pages, adding to the tail of the passed list.
 * The list must be initialized.
 * Returns the number of pages allocated.
 * NOTE: This function does not clear the allocated pages
 */
size_t pmm_alloc_range(paddr_t address, uint count, struct list_node *list) __WARN_UNUSED_RESULT;

/* Free a list of physical pages.
 * Returns the number of pages freed.
 */
size_t pmm_free(struct list_node *list);

/* Helper routine for the above. */
size_t pmm_free_page(vm_page_t *page);

/* Allocate and clear a run of contiguous pages, aligned on log2 byte boundary (0-31)
 * If the optional physical address pointer is passed, return the address.
 * If the optional list is passed, append the allocate page structures to the tail of the list.
 */
size_t pmm_alloc_contiguous(uint count, uint8_t align_log2, paddr_t *pa, struct list_node *list);

/* Allocate and clear a run of pages out of the kernel area and return the pointer in kernel space.
 * If the optional list is passed, append the allocate page structures to the tail of the list.
 */
void *pmm_alloc_kpages(uint count, struct list_node *list);

/* Helper routine for pmm_alloc_kpages. */
static inline void *pmm_alloc_kpage(void) { return pmm_alloc_kpages(1, NULL); }

size_t pmm_free_kpages(void *ptr, uint count);

/* assign physical addresses and sizes to the dynamic entries in the initial
 * mappings
 */
void vm_assign_initial_dynamic(paddr_t kernel_start, size_t ram_size);

/* map the initial mappings */
void vm_map_initial_mappings(void);

/* physical to virtual */
void *paddr_to_kvaddr(paddr_t pa);

/* a hint as to which virtual addresses will be returned by pmm_alloc_kpages */
void *kvaddr_get_range(size_t* size_return);

/* virtual to physical */
paddr_t vaddr_to_paddr(void *va);

/* vm_page_t to physical address */
paddr_t vm_page_to_paddr(const vm_page_t *page);

/* paddr to vm_page_t */
vm_page_t *paddr_to_vm_page(paddr_t addr);

/* virtual allocator */
typedef struct vmm_aspace {
    struct list_node node;
    /* used for allocations with VMM_FLAG_QUOTA set */
    struct res_group* quota_res_group;
    struct obj_ref quota_res_group_ref;
    char name[32];

    uint flags;

    vaddr_t base;
    size_t  size;

    struct bst_root regions;

    arch_aspace_t arch_aspace;
} vmm_aspace_t;

#define VMM_ASPACE_FLAG_KERNEL 0x1
#define VMM_ASPACE_FLAG_BTI    0x2

/**
 * struct vmm_obj_slice - range of memory backed by a &struct vmm_obj
 * @obj:     backing object for the slice
 * @obj_ref: reference to keep the backing object alive
 * @offset:  offset in bytes into the object at which the slice begins
 * @size:    number of bytes in the slice
 *
 * &struct vmm_obj_slice is intended to represent a particular range of
 * memory in a backing object for those cases where something other than
 * the entire backing object will be used.
 *
 * Must be initialized with vmm_obj_slice_init() or
 * VMM_OBJ_SLICE_INITIAL_VALUE.
 */
struct vmm_obj_slice {
    struct vmm_obj *obj;
    struct obj_ref obj_ref;
    size_t offset;
    size_t size;
};

#define VMM_OBJ_SLICE_INITIAL_VALUE(slice)                 \
    {                                                      \
        .obj = NULL,                                       \
        .obj_ref = OBJ_REF_INITIAL_VALUE((slice).obj_ref), \
        .offset = 0,                                       \
        .size = 0,                                         \
    }

/**
 * vmm_obj_slice_init() - initializes a &struct vmm_obj_slice
 * @slice: slice to initialize
 */
void vmm_obj_slice_init(struct vmm_obj_slice *slice);

/**
 * vmm_obj_slice_bind() - bind a vmm_obj_slice to a particular vmm_obj
 * @slice:  Slice to bind (should be initialized and unused).
 * @obj:    vmm_obj to bind the slice to
 * @offset: Starting offset into the vmm_obj
 * @size:   Size of the slice.
 *
 * Attaches a subrange of a particular &struct vmm_obj to the slice.
 * The caller is responsible for validating the offset and size.
 */
void vmm_obj_slice_bind(struct vmm_obj_slice *slice, struct vmm_obj *obj,
                        size_t offset, size_t size);

/**
 * vmm_obj_slice_release() - release reference held by a &struct vmm_obj_slice
 * @slice: slice to release
 *
 * Releases any resource attached to the slice.
 *
 * Note: This assumes that a non-NULL obj implies the obj_ref field is
 *       releasable. This invariant will hold if you have used the API to
 *       interact with the slice, but if you have updated a field manually,
 *       it is the responsiblity of the caller to ensure this holds.
 */
void vmm_obj_slice_release(struct vmm_obj_slice *slice);

typedef struct vmm_region {
    struct bst_node node;
    char name[32];

    uint flags;
    uint arch_mmu_flags;

    vaddr_t base;

    struct vmm_obj_slice obj_slice;
} vmm_region_t;

#define VMM_REGION_FLAG_RESERVED 0x1
#define VMM_REGION_FLAG_PHYSICAL 0x2
#define VMM_REGION_FLAG_INTERNAL_MASK 0xffff

/* grab a handle to the kernel address space */
extern vmm_aspace_t _kernel_aspace;
static inline vmm_aspace_t *vmm_get_kernel_aspace(void)
{
    return &_kernel_aspace;
}

/* virtual to container address space */
struct vmm_aspace *vaddr_to_aspace(void *ptr);

/**
 * vmm_lock_aspace() - Lock an address space so memory mapping can't change
 * @aspace: The address space to lock
 *
 * Prevents changes to address space. Current implementation will lock all
 * address spaces.
 */
void vmm_lock_aspace(vmm_aspace_t *aspace);

/**
 * vmm_unlock_aspace() - Release lock on address space
 * @aspace: The address space to unlock
 */
void vmm_unlock_aspace(vmm_aspace_t *aspace);


/**
 * vmm_find_spot() - Finds a gap of the requested size in the address space
 * @aspace: The address space to locate a gap in
 * @size:   How large of a gap is sought
 * @out:    Output parameter for the base of the gap
 *
 * Finds a gap of size @size in @aspace, and outputs its address. If ASLR is
 * active, this location will be randomized.
 *
 * This function *DOES NOT* actually allocate anything, it merely locates a
 * prospective location. It is intended for use in situations where a larger
 * gap than an individual mapping is required, such as in the case of the ELF
 * loader (where text, rodata, and data are all separate mappings, but must
 * have fixed relative offsets).
 *
 * The address returned is suitable for use with vmm_alloc() and similar
 * functions with the VMM_FLAG_VALLOC_SPECIFIC flag.
 *
 * On ARM32, this function assumes the request is for *secure* memory
 * for the purposes of region compatiblity.
 *
 * Return: Whether a spot was successfully located
 */
bool vmm_find_spot(vmm_aspace_t *aspace, size_t size, vaddr_t *out);

/* reserve a chunk of address space to prevent allocations from that space */
status_t vmm_reserve_space(vmm_aspace_t *aspace, const char *name, size_t size, vaddr_t vaddr);

/* allocate a region of memory backed by vmm_obj */
status_t vmm_alloc_obj(vmm_aspace_t *aspace, const char *name,
                       struct vmm_obj *obj, size_t offset, size_t size,
                       void **ptr, uint8_t align_log2, uint vmm_flags,
                       uint arch_mmu_flags);

/* allocate a region of virtual space that maps a physical piece of address space.
   the physical pages that back this are not allocated from the pmm. */
status_t vmm_alloc_physical_etc(vmm_aspace_t *aspace, const char *name, size_t size, void **ptr, uint8_t align_log2, paddr_t *paddr, uint paddr_count, uint vmm_flags, uint arch_mmu_flags);

/* allocate a region of virtual space that maps a physical piece of address space.
   the physical pages that back this are not allocated from the pmm. */
static inline status_t vmm_alloc_physical(vmm_aspace_t *aspace, const char *name, size_t size, void **ptr, uint8_t align_log2, paddr_t paddr, uint vmm_flags, uint arch_mmu_flags)
{
    return vmm_alloc_physical_etc(aspace, name, size, ptr, align_log2,
                                  &paddr, 1, vmm_flags, arch_mmu_flags);
}

/* allocate a region of memory backed by newly allocated contiguous physical memory  */
status_t vmm_alloc_contiguous(vmm_aspace_t *aspace, const char *name, size_t size, void **ptr, uint8_t align_log2, uint vmm_flags, uint arch_mmu_flags);

/* allocate a region of memory, but do not back it by physical memory  */
status_t vmm_alloc_no_physical(vmm_aspace_t *aspace, const char *name, size_t size, void **ptr, uint8_t align_log2, uint vmm_flags, uint arch_mmu_flags);

/* allocate a region of memory backed by newly allocated physical memory */
status_t vmm_alloc(vmm_aspace_t *aspace, const char *name, size_t size, void **ptr, uint8_t align_log2, uint vmm_flags, uint arch_mmu_flags);

/**
 * vmm_find_region() - find a region in which specified virtual address resides
 * @aspace: address space to look for @vaddr in
 * @vaddr:  base virtual address to look for
 *
 * Must be called after vmm_lock_aspace(). The returned pointer is only valid
 * until vmm_unlock_aspace() is called.
 *
 * Return: region struct or %NULL if @vaddr is not mapped in @aspace
 */
vmm_region_t* vmm_find_region(const vmm_aspace_t* aspace, vaddr_t vaddr);

/**
 * vmm_get_obj() - Acquire a slice from a chunk of an &struct aspace
 * @aspace: address space to extract from
 * @vaddr:  base virtual address the slice should start at
 * @size:   desired slice size
 * @slice:  output parameter for the result slice, must not be null, should be
 *          initialized
 *
 * Locates the &struct vmm_obj backing a particular address range within
 * @aspace, and returns a slice representing it if possible. If the range
 * is unmapped, has no vmm_obj backing, or spans multiple backing slices,
 * an error will be returned.
 *
 * On success, @slice will be updated to refer to a subrange of the backing
 * slice for the supplied virtual address range. On failure, @slice will be
 * untouched.
 *
 * Return: Status code; any value other than NO_ERROR is a failure.
 */
status_t vmm_get_obj(const vmm_aspace_t *aspace, vaddr_t vaddr, size_t size,
                     struct vmm_obj_slice *slice);

#define VMM_FREE_REGION_FLAG_EXPAND 0x1

/* Unmap previously allocated region and free physical memory pages backing it (if any).
   If flags is 0, va and size must match entire region.
   If flags is VMM_FREE_REGION_FLAG_EXPAND, free entire region containin [va, va+size-1] */
status_t vmm_free_region_etc(vmm_aspace_t *aspace, vaddr_t va, size_t size, uint32_t flags);

/* Unmap previously allocated region and free physical memory pages backing it (if any).
   va can be anywhere in region. */
status_t vmm_free_region(vmm_aspace_t *aspace, vaddr_t va);

/* For the above region creation routines. Allocate virtual space at the passed in pointer. */
#define VMM_FLAG_VALLOC_SPECIFIC 0x10000

/*
 * Disable default guard page before region. Can be used with
 * VMM_FLAG_VALLOC_SPECIFIC if two regions need to be created with no gap.
 */
#define VMM_FLAG_NO_START_GUARD 0x20000

/*
 * Disable default guard page before region. Can be used with
 * VMM_FLAG_VALLOC_SPECIFIC if two regions need to be created with no gap.
 */
#define VMM_FLAG_NO_END_GUARD 0x40000

/*
 * Do not allocate physical memory, only reserve the address range.
 */
#define VMM_FLAG_NO_PHYSICAL 0x80000

/*
 * Count this allocation towards the app's memory usage quota.
 */
#define VMM_FLAG_QUOTA 0x100000


/**
 * vmm_create_aspace_with_quota() - Allocate a new address space with a size limit.
 * @aspace: a pointer to set to the new aspace.
 * @name: the name of the new aspace.
 * @size: the size limit of the new aspace, 0 meaning no limit.
 * @flags: the flags of the new aspace.
 */
status_t vmm_create_aspace_with_quota(vmm_aspace_t **aspace, const char *name, size_t size, uint flags);

static inline status_t vmm_create_aspace(vmm_aspace_t** _aspace,
                           const char* name,
                           uint flags) {
    return vmm_create_aspace_with_quota(_aspace, name, 0, flags);
}

/* destroy everything in the address space */
status_t vmm_free_aspace(vmm_aspace_t *aspace);

/* internal routine by the scheduler to swap mmu contexts */
void vmm_context_switch(vmm_aspace_t *oldspace, vmm_aspace_t *newaspace);

/* set the current user aspace as active on the current thread.
   NULL is a valid argument, which unmaps the current user address space */
void vmm_set_active_aspace(vmm_aspace_t *aspace);

/**
 * vmm_get_address_description() - get a descriptive name for the given address
 * @vaddr: the address to get a descriptive name for
 * @name: a place to store the name
 * @name_size: the size of the output buffer
 *
 * Gets a descriptive name for the given address and returns it in the 'name'
 * buffer, up to 'name_size' bytes including the null terminator.
 * If the address falls inside of a region, the name of the region will be
 * returned. If the address is not in a region, but falls just before and/or
 * after another region, it will return a string indicating the distance in
 * bytes from those region(s).
 * If the address is not in or adjacent to a region, the description will say
 * "<no region>", and if the region cannot be determined due to the vmm_lock
 * being held, the returned description will say "<unavailable>".
 */
void vmm_get_address_description(vaddr_t vaddr, char *name, size_t name_size);

#define VMM_MAX_ADDRESS_DESCRIPTION_SIZE \
    ((sizeof(((vmm_region_t*)0)->name) - 1) * 2 + \
     sizeof("NNNN bytes after ") + \
     sizeof(", NNNN bytes before ") - 1)

/**
 * update_relocation_entries() - Update all entries in the relocation table
 *                               by subtracting a given value from each one.
 * @relr_start: start of the relocation list.
 * @relr_end: end of the relocation list.
 * @reloc_delta: Value to subtract from each relocation entry.
 *
 * Iterates through all entries in the relocation table starting at @relr_start
 * and subtracts @reloc_delta from each entry that encodes an absolute pointer.
 * This is currently called to update the table emitted by the linker with
 * kernel virtual addresses into a table containing physical addresses, so the
 * subtractions should never underflow if @reloc_delta is the positive
 * difference between the kernel's virtual and physical addresses.
 */
void update_relocation_entries(uintptr_t* relr_start, uintptr_t* relr_end,
                               uintptr_t reloc_delta);
/**
 * relocate_kernel() - Apply the given list of relocations to the kernel.
 * @relr_start: start of the relocation list.
 * @relr_end: end of the relocation list.
 * @old_base: current base address of the kernel.
 * @new_base: target base address to relocate the kernel to.
 *
 * This function applies the given list of relative relocations to the kernel,
 * moving the base of the kernel from @old_base to @new_base.
 */
void relocate_kernel(uintptr_t* relr_start, uintptr_t* relr_end,
                     uintptr_t old_base, uintptr_t new_base);

/* allocate a buffer in early boot memory of the given size and alignment */
void *boot_alloc_memalign(size_t len, size_t alignment) __MALLOC;

/* allocate a buffer in early boot memory of the given size and an 8 byte
 * alignment
 */
void *boot_alloc_mem(size_t len) __MALLOC;

#ifdef KERNEL_BASE_ASLR
/* select a random address for KERNEL_BASE_ASLR */
vaddr_t aslr_randomize_kernel_base(vaddr_t kernel_base);
#else
static inline vaddr_t aslr_randomize_kernel_base(vaddr_t kernel_base) {
    return kernel_base;
}
#endif

__END_CDECLS

#endif // !ASSEMBLY
