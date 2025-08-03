/*
 * Copyright (c) 2012-2013, NVIDIA CORPORATION. All rights reserved
 * Copyright (c) 2013, Google, Inc. All rights reserved
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

#include <lib/backtrace/backtrace.h>
#include <lib/trusty/elf.h>
#include <lib/trusty/trusty_app.h>

#include <arch.h>
#include <assert.h>
#include <compiler.h>
#include <debug.h>
#include <err.h>
#include <inttypes.h>
#include <kernel/event.h>
#include <kernel/mutex.h>
#include <kernel/thread.h>
#include <lib/app_manifest/app_manifest.h>
#include <lib/rand/rand.h>
#include <lib/trusty/ipc.h>
#include <lk/init.h>
#include <malloc.h>
#include <platform.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <trace.h>
#include <uapi/mm.h>
#include <version.h>

#define LOCAL_TRACE 0

#define DEFAULT_MGMT_FLAGS APP_MANIFEST_MGMT_FLAGS_NONE

#define TRUSTY_APP_RESTART_TIMEOUT_SUCCESS (10ULL * 1000ULL * 1000ULL)
#define TRUSTY_APP_RESTART_TIMEOUT_FAILURE (5ULL * 1000ULL * 1000ULL * 1000ULL)

#ifdef TRUSTY_APP_STACK_TOP
#error "TRUSTY_APP_STACK_TOP is no longer respected"
#endif

/* Don't allow NULL to be a valid userspace address */
STATIC_ASSERT(USER_ASPACE_BASE != 0);

#ifndef DEFAULT_HEAP_SIZE
#define DEFAULT_HEAP_SIZE (4 * PAGE_SIZE)
#endif

#define PAGE_MASK (PAGE_SIZE - 1)

#undef ELF_64BIT
#if !IS_64BIT || USER_32BIT
#define ELF_64BIT 0
#else
#define ELF_64BIT 1
#endif

#if ELF_64BIT
#define ELF_NHDR Elf64_Nhdr
#define ELF_SHDR Elf64_Shdr
#define ELF_EHDR Elf64_Ehdr
#define ELF_PHDR Elf64_Phdr
#define Elf_Addr Elf64_Addr
#define Elf_Off Elf64_Off
#define Elf_Word Elf64_Word

#define PRIxELF_Off PRIx64
#define PRIuELF_Size PRIu64
#define PRIxELF_Size PRIx64
#define PRIxELF_Addr PRIx64
#define PRIxELF_Flags PRIx64
#else
#define ELF_NHDR Elf32_Nhdr
#define ELF_SHDR Elf32_Shdr
#define ELF_EHDR Elf32_Ehdr
#define ELF_PHDR Elf32_Phdr
#define Elf_Addr Elf32_Addr
#define Elf_Off Elf32_Off
#define Elf_Word Elf32_Word

#define PRIxELF_Off PRIx32
#define PRIuELF_Size PRIu32
#define PRIxELF_Size PRIx32
#define PRIxELF_Addr PRIx32
#define PRIxELF_Flags PRIx32
#endif

static u_int trusty_next_app_id;
static struct list_node trusty_app_list = LIST_INITIAL_VALUE(trusty_app_list);

struct trusty_builtin_app_img {
    intptr_t manifest_start;
    intptr_t manifest_end;
    intptr_t img_start;
    intptr_t img_end;
};

/* These symbols are linker defined and are declared as unsized arrays to
 * prevent compiler(clang) optimizations that break when the list is empty and
 * the symbols alias
 */
extern struct trusty_builtin_app_img __trusty_app_list_start[];
extern struct trusty_builtin_app_img __trusty_app_list_end[];

static bool apps_started;
static mutex_t apps_lock = MUTEX_INITIAL_VALUE(apps_lock);
static struct list_node app_notifier_list =
        LIST_INITIAL_VALUE(app_notifier_list);
uint als_slot_cnt;
static event_t app_mgr_event =
        EVENT_INITIAL_VALUE(app_mgr_event, 0, EVENT_FLAG_AUTOUNSIGNAL);

static struct list_node allowed_mmio_ranges_list =
        LIST_INITIAL_VALUE(allowed_mmio_ranges_list);

#define PRINT_TRUSTY_APP_UUID(level, tid, u)                                                       \
    dprintf((level),                                                                               \
            "trusty_app %d uuid: 0x%08xx 0x%04xx 0x%04xx 0x%02x%02x 0x%02x%02x%02x%02x%02x%02x\n", \
            tid, (u)->time_low, (u)->time_mid, (u)->time_hi_and_version,                           \
            (u)->clock_seq_and_node[0], (u)->clock_seq_and_node[1],                                \
            (u)->clock_seq_and_node[2], (u)->clock_seq_and_node[3],                                \
            (u)->clock_seq_and_node[4], (u)->clock_seq_and_node[5],                                \
            (u)->clock_seq_and_node[6], (u)->clock_seq_and_node[7]);

static bool address_range_within_bounds(const void* range_start,
                                        size_t range_size,
                                        const void* lower_bound,
                                        const void* upper_bound) {
    const void* range_end = range_start + range_size;

    if (upper_bound < lower_bound) {
        LTRACEF("upper bound(%p) is below upper bound(%p)\n", upper_bound,
                lower_bound);
        return false;
    }

    if (range_end < range_start) {
        LTRACEF("Range overflows. start:%p size:%zd end:%p\n", range_start,
                range_size, range_end);
        return false;
    }

    if (range_start < lower_bound) {
        LTRACEF("Range starts(%p) before lower bound(%p)\n", range_start,
                lower_bound);
        return false;
    }

    if (range_end > upper_bound) {
        LTRACEF("Range ends(%p) past upper bound(%p)\n", range_end,
                upper_bound);
        return false;
    }

    return true;
}

static inline bool address_range_within_img(
        const void* range_start,
        size_t range_size,
        const struct trusty_app_img* appimg) {
    return address_range_within_bounds(range_start, range_size,
                                       (const void*)appimg->img_start,
                                       (const void*)appimg->img_end);
}

void trusty_app_allow_mmio_range(struct trusty_app_mmio_allowed_range* range) {
    DEBUG_ASSERT(range);

    if (!range->size) {
        dprintf(CRITICAL, "Allowed mmio range is empty\n");
        return;
    }

    mutex_acquire(&apps_lock);
    list_add_tail(&allowed_mmio_ranges_list, &range->node);
    mutex_release(&apps_lock);
}

/**
 * app_mmio_is_allowed() - Check whether an app is allowed to map a given
 *                         physical memory range.
 * @trusty_app: The application to check.
 * @mmio_start: The start of the physical memory range to map.
 * @mmio_size:  The size of the physical memory range.
 *
 * For security reasons, we do not want to allow any loadable app to map
 * physical memory by default. However, some specific apps need to
 * map device memory, so we maintain an allowlist of per-app ranges that
 * can be mapped. This function checks a given physical memory range for the
 * loadable app at @trusty_app against the allowlist. Each project can add its
 * own allowlist entries using @trusty_app_allow_mmio_range.
 */
static bool app_mmio_is_allowed(struct trusty_app* trusty_app,
                                paddr_t mmio_start,
                                size_t mmio_size) {
    if (!(trusty_app->flags & APP_FLAGS_LOADABLE)) {
        return true;
    }

    DEBUG_ASSERT(mmio_size);
    DEBUG_ASSERT(is_mutex_held(&apps_lock));

    paddr_t mmio_end = mmio_start + (mmio_size - 1);
    const struct trusty_app_mmio_allowed_range* range;
    list_for_every_entry(&allowed_mmio_ranges_list, range,
                         struct trusty_app_mmio_allowed_range, node) {
        DEBUG_ASSERT(range->size);
        paddr_t range_end = range->start + (range->size - 1);
        if (!memcmp(&range->uuid, &trusty_app->props.uuid, sizeof(uuid_t)) &&
            mmio_start >= range->start && mmio_end <= range_end) {
            return true;
        }
    }

    return false;
}

/**
 * struct trusty_app_dma_allowed_range - Prepared dma range for trusty app.
 * @node:  Internal list node, should be initialized to
 *         %LIST_INITIAL_CLEARED_VALUE
 * @app:   Pointer to the trusty app which prepared this range
 * @slice: Represents a virtual memory range prepared for dma. Can be used to
 *         get the physical pages used for dma
 * @vaddr: Virtual memory address. Used to tear down all mappings from a single
 *         call to prepare_dma
 * @flags: Flags used to map dma range
 */
struct trusty_app_dma_allowed_range {
    struct list_node node;
    struct vmm_obj_slice slice;
    vaddr_t vaddr;
    uint32_t flags;
};

status_t trusty_app_allow_dma_range(struct trusty_app* app,
                                    struct vmm_obj* obj,
                                    size_t offset,
                                    size_t size,
                                    vaddr_t vaddr,
                                    uint32_t flags) {
    DEBUG_ASSERT(obj);
    DEBUG_ASSERT(app);
    DEBUG_ASSERT(size);

    /* check that dma range hasn't already been mapped at vaddr */
    const struct trusty_app_dma_allowed_range* check_range;
    list_for_every_entry(&app->props.dma_entry_list, check_range,
                         struct trusty_app_dma_allowed_range, node) {
        if (check_range->vaddr == vaddr)
            return ERR_INVALID_ARGS;
    }

    struct trusty_app_dma_allowed_range* range_list_entry =
            (struct trusty_app_dma_allowed_range*)calloc(
                    1, sizeof(struct trusty_app_dma_allowed_range));
    if (!range_list_entry) {
        return ERR_NO_MEMORY;
    }
    /* range_list_entry->node is already zero-initialized */
    vmm_obj_slice_init(&range_list_entry->slice);
    vmm_obj_slice_bind(&range_list_entry->slice, obj, offset, size);
    range_list_entry->vaddr = vaddr;
    range_list_entry->flags = flags;

    mutex_acquire(&apps_lock);
    list_add_tail(&app->props.dma_entry_list, &range_list_entry->node);
    mutex_release(&apps_lock);

    return NO_ERROR;
}

status_t trusty_app_destroy_dma_range(vaddr_t vaddr, size_t size) {
    status_t ret = ERR_NOT_FOUND;
    struct trusty_app_dma_allowed_range* range;
    struct trusty_app_dma_allowed_range* next_range;
    struct trusty_app* app = current_trusty_app();

    mutex_acquire(&apps_lock);
    list_for_every_entry_safe(&app->props.dma_entry_list, range, next_range,
                              struct trusty_app_dma_allowed_range, node) {
        DEBUG_ASSERT(range->slice.size);
        if (range->vaddr == vaddr && range->slice.size == size) {
            list_delete(&range->node);
            vmm_obj_slice_release(&range->slice);
            free(range);
            ret = NO_ERROR;
            break;
        }
    }

    mutex_release(&apps_lock);

    return ret;
}

/* Must be called with the apps_lock held */
static bool trusty_app_dma_is_allowed_locked(const struct trusty_app* app,
                                             paddr_t paddr) {
    int ret;
    size_t offset;
    const struct trusty_app_dma_allowed_range* range;

    DEBUG_ASSERT(app);
    DEBUG_ASSERT(is_mutex_held(&apps_lock));
    list_for_every_entry(&app->props.dma_entry_list, range,
                         struct trusty_app_dma_allowed_range, node) {
        DEBUG_ASSERT(range->slice.size);
        offset = 0;
        do {
            paddr_t prepared_paddr;
            size_t prepared_paddr_size;
            ret = range->slice.obj->ops->get_page(
                    range->slice.obj, range->slice.offset + offset,
                    &prepared_paddr, &prepared_paddr_size);
            if (ret != NO_ERROR) {
                TRACEF("failed to get pages for paddr 0x%" PRIxPADDR "\n",
                       paddr);
                return false;
            }
            paddr_t prepared_paddr_end =
                    prepared_paddr + (prepared_paddr_size - 1);
            if (paddr >= prepared_paddr && paddr <= prepared_paddr_end) {
                return true;
            }
            offset += MIN(range->slice.size - offset, prepared_paddr_size);
        } while (offset < range->slice.size &&
                 (range->flags & DMA_FLAG_MULTI_PMEM));
    }

    TRACEF("paddr 0x%" PRIxPADDR " is not valid for dma\n", paddr);
    return false;
}

bool trusty_app_dma_is_allowed(const struct trusty_app* app, paddr_t paddr) {
    bool res;
    mutex_acquire(&apps_lock);
    res = trusty_app_dma_is_allowed_locked(app, paddr);
    mutex_release(&apps_lock);
    return res;
}

static void finalize_registration(void) {
    mutex_acquire(&apps_lock);
    apps_started = true;
    mutex_release(&apps_lock);
}

status_t trusty_register_app_notifier(struct trusty_app_notifier* n) {
    status_t ret = NO_ERROR;

    mutex_acquire(&apps_lock);
    if (!apps_started)
        list_add_tail(&app_notifier_list, &n->node);
    else
        ret = ERR_ALREADY_STARTED;
    mutex_release(&apps_lock);
    return ret;
}

int trusty_als_alloc_slot(void) {
    int ret;

    mutex_acquire(&apps_lock);
    if (!apps_started)
        ret = ++als_slot_cnt;
    else
        ret = ERR_ALREADY_STARTED;
    mutex_release(&apps_lock);
    return ret;
}

#if ELF_64BIT
#define ENTER_USPACE_FLAGS 0
#else
#define ENTER_USPACE_FLAGS ARCH_ENTER_USPACE_FLAG_32BIT
#endif

/*
 * Allocate space on the user stack.
 */
static user_addr_t user_stack_alloc(struct trusty_thread* trusty_thread,
                                    user_size_t data_len,
                                    user_size_t align,
                                    user_addr_t* stack_ptr) {
    user_addr_t ptr = round_down(*stack_ptr - data_len, align);
    if (ptr < trusty_thread->stack_start - trusty_thread->stack_size) {
        panic("stack underflow while initializing user space\n");
    }
    *stack_ptr = ptr;
    return ptr;
}

/*
 * Copy data to a preallocated spot on the user stack. This should not fail.
 */
static void copy_to_user_stack(user_addr_t dst_ptr,
                               const void* data,
                               user_size_t data_len) {
    int ret = copy_to_user(dst_ptr, data, data_len);
    if (ret) {
        panic("copy_to_user failed %d\n", ret);
    }
}

/*
 * Allocate space on the user stack and fill it with data.
 */
static user_addr_t add_to_user_stack(struct trusty_thread* trusty_thread,
                                     const void* data,
                                     user_size_t data_len,
                                     user_size_t align,
                                     user_addr_t* stack_ptr) {
    user_addr_t ptr =
            user_stack_alloc(trusty_thread, data_len, align, stack_ptr);
    copy_to_user_stack(ptr, data, data_len);
    return ptr;
}

/* TODO share a common header file. */
#define AT_PAGESZ 6
#define AT_BASE 7
#define AT_RANDOM 25
#define AT_HWCAP2 26
#define HWCAP2_MTE (1 << 18)

/*
 * Pass data to libc on the user stack.
 * Prevent inlining so that the stack allocations inside this function don't get
 * trapped on the kernel stack.
 */
static __NO_INLINE user_addr_t
trusty_thread_write_elf_tables(struct trusty_thread* trusty_thread,
                               user_addr_t* stack_ptr,
                               vaddr_t load_bias) {
    /* Construct the elf tables in reverse order - the stack grows down. */

    /*
     * sixteen random bytes
     */
    uint8_t rand_bytes[16] = {0};
    rand_get_bytes(rand_bytes, sizeof(rand_bytes));
    user_addr_t rand_bytes_addr = add_to_user_stack(
            trusty_thread, rand_bytes, sizeof(rand_bytes), 1, stack_ptr);

    const char* app_name = trusty_thread->app->props.app_name;
    user_addr_t app_name_addr =
            add_to_user_stack(trusty_thread, app_name, strlen(app_name) + 1,
                              sizeof(user_addr_t), stack_ptr);

    bool mte = arch_tagging_enabled();
    /* auxv */
    user_addr_t auxv[] = {
            AT_PAGESZ, PAGE_SIZE,       AT_BASE,   load_bias,
            AT_RANDOM, rand_bytes_addr, AT_HWCAP2, mte ? HWCAP2_MTE : 0,
            0};
    add_to_user_stack(trusty_thread, auxv, sizeof(auxv), sizeof(user_addr_t),
                      stack_ptr);

    /* envp - for layout compatibility, unused */
    user_addr_t envp[] = {
            0,
    };
    add_to_user_stack(trusty_thread, envp, sizeof(envp), sizeof(user_addr_t),
                      stack_ptr);

    /* argv. Only argv [0] and argv [1] (terminator) are set. */
    user_addr_t argv[] = {
            app_name_addr,
            0,
    };
    add_to_user_stack(trusty_thread, argv, sizeof(argv), sizeof(user_addr_t),
                      stack_ptr);

    /* argc. The null terminator is not counted. */
    user_addr_t argc = countof(argv) - 1;
    user_addr_t argc_ptr = add_to_user_stack(trusty_thread, &argc, sizeof(argc),
                                             sizeof(user_addr_t), stack_ptr);

    return argc_ptr;
}

static int trusty_thread_startup(void* arg) {
    struct trusty_thread* trusty_thread = current_trusty_thread();

    vmm_set_active_aspace(trusty_thread->app->aspace);

    user_addr_t stack_ptr = trusty_thread->stack_start;
    user_addr_t elf_tables = trusty_thread_write_elf_tables(
            trusty_thread, &stack_ptr, trusty_thread->app->load_bias);

    thread_sleep_until_ns(trusty_thread->app->min_start_time);

    user_addr_t shadow_stack_base = 0;
#if USER_SCS_SUPPORTED
    shadow_stack_base = trusty_thread->shadow_stack_base;
#endif

    arch_enter_uspace(trusty_thread->entry, stack_ptr, shadow_stack_base,
                      ENTER_USPACE_FLAGS, elf_tables);

    __UNREACHABLE;
}

static status_t trusty_thread_start(struct trusty_thread* trusty_thread) {
    DEBUG_ASSERT(trusty_thread && trusty_thread->thread);

    return thread_resume(trusty_thread->thread);
}

void __NO_RETURN trusty_thread_exit(int retcode) {
    struct trusty_thread* trusty_thread = current_trusty_thread();
    vaddr_t stack_bot;

    ASSERT(trusty_thread);

    stack_bot = trusty_thread->stack_start - trusty_thread->stack_size;

    vmm_free_region(trusty_thread->app->aspace, stack_bot);

#if USER_SCS_SUPPORTED
    if (trusty_thread->shadow_stack_base) {
        /*
         * revert the adjustment of shadow_stack_base to reconstruct pointer
         * returned by vmm_alloc.
         */
        size_t size = trusty_thread->shadow_stack_size;
        size_t adjustment = round_up(size, PAGE_SIZE) - size;
        vmm_free_region(trusty_thread->app->aspace,
                        trusty_thread->shadow_stack_base - adjustment);
    } else {
        DEBUG_ASSERT(trusty_thread->app->props.min_shadow_stack_size == 0);
    }
#endif

    thread_exit(retcode);
}

static struct trusty_thread* trusty_thread_create(
        const char* name,
        vaddr_t entry,
        int priority,
        size_t stack_size,
        size_t shadow_stack_size,
        struct trusty_app* trusty_app) {
    struct trusty_thread* trusty_thread;
    status_t err;
    vaddr_t stack_bot = 0;
    stack_size = round_up(stack_size, PAGE_SIZE);

    trusty_thread = calloc(1, sizeof(struct trusty_thread));
    if (!trusty_thread)
        return NULL;

    err = vmm_alloc(trusty_app->aspace, "stack", stack_size, (void**)&stack_bot,
                    PAGE_SIZE_SHIFT, 0,
                    ARCH_MMU_FLAG_PERM_USER | ARCH_MMU_FLAG_PERM_NO_EXECUTE);
    if (err != NO_ERROR) {
        dprintf(CRITICAL,
                "failed(%d) to create thread stack(0x%" PRIxVADDR
                ") for app %u, %s\n",
                err, stack_bot, trusty_app->app_id, trusty_app->props.app_name);
        goto err_stack;
    }

#if USER_SCS_SUPPORTED
    vaddr_t shadow_stack_base = 0;
    if (shadow_stack_size) {
        err = vmm_alloc(
                trusty_app->aspace, "shadow stack", shadow_stack_size,
                (void**)&shadow_stack_base, PAGE_SIZE_SHIFT, 0,
                ARCH_MMU_FLAG_PERM_USER | ARCH_MMU_FLAG_PERM_NO_EXECUTE);
        if (err != NO_ERROR) {
            dprintf(CRITICAL,
                    "failed(%d) to allocate shadow stack(0x%" PRIxVADDR
                    ") for app %u\n",
                    err, shadow_stack_base, trusty_app->app_id);
            goto err_shadow_stack;
        }
    }
#endif

    trusty_thread->thread = thread_create(name, trusty_thread_startup, NULL,
                                          priority, DEFAULT_STACK_SIZE);
    if (!trusty_thread->thread)
        goto err_thread;

    trusty_thread->app = trusty_app;
    trusty_thread->entry = entry;
    trusty_thread->stack_start = stack_bot + stack_size; /* stack grows down */
    trusty_thread->stack_size = stack_size;
#if USER_SCS_SUPPORTED
    /* make shadow stack hit guard page if too small */
    size_t adjustment =
            round_up(shadow_stack_size, PAGE_SIZE) - shadow_stack_size;

    /* we only make an adjustment iff app has shadow call stacks enabled */
    DEBUG_ASSERT(shadow_stack_size > 0 || adjustment == 0);

    /* shadow stack grows up */
    trusty_thread->shadow_stack_base = shadow_stack_base + adjustment;
    trusty_thread->shadow_stack_size = shadow_stack_size;
#endif
    thread_tls_set(trusty_thread->thread, TLS_ENTRY_TRUSTY,
                   (uintptr_t)trusty_thread);

    int pinned_cpu = trusty_app->props.pinned_cpu;
    if (pinned_cpu != APP_MANIFEST_PINNED_CPU_NONE) {
        thread_set_pinned_cpu(trusty_thread->thread, pinned_cpu);
        dprintf(SPEW, "trusty_app %d, %s pinned to CPU: %u\n",
                trusty_app->app_id, trusty_app->props.app_name, pinned_cpu);
    }

    return trusty_thread;

err_thread:
#if USER_SCS_SUPPORTED
    if (shadow_stack_size) {
        vmm_free_region(trusty_app->aspace, shadow_stack_base);
    }
err_shadow_stack:
#endif
    vmm_free_region(trusty_app->aspace, stack_bot);
err_stack:
    free(trusty_thread);
    return NULL;
}

/* Must be called with the apps_lock held */
static struct manifest_port_entry* find_manifest_port_entry_locked(
        const char* port_path,
        struct trusty_app** app_out) {
    struct trusty_app* app;
    struct manifest_port_entry* entry;

    DEBUG_ASSERT(is_mutex_held(&apps_lock));

    list_for_every_entry(&trusty_app_list, app, struct trusty_app, node) {
        list_for_every_entry(&app->props.port_entry_list, entry,
                             struct manifest_port_entry, node) {
            if (!strncmp(port_path, entry->path, entry->path_len)) {
                if (app_out)
                    *app_out = app;

                return entry;
            }
        }
    }

    return NULL;
}
/* Must be called with the apps_lock held */
static struct trusty_app* trusty_app_find_by_uuid_locked(uuid_t* uuid) {
    struct trusty_app* app;

    DEBUG_ASSERT(is_mutex_held(&apps_lock));

    list_for_every_entry(&trusty_app_list, app, struct trusty_app, node) {
        if (!memcmp(&app->props.uuid, uuid, sizeof(uuid_t)))
            return app;
    }

    return NULL;
}

bool trusty_uuid_dma_is_allowed(const struct uuid* uuid, paddr_t paddr) {
    bool res;
    const struct trusty_app* app;
    mutex_acquire(&apps_lock);
    app = trusty_app_find_by_uuid_locked((struct uuid*)uuid);
    res = trusty_app_dma_is_allowed_locked(app, paddr);
    mutex_release(&apps_lock);
    return res;
}

static status_t get_app_manifest_config_data(struct trusty_app* trusty_app,
                                             char** manifest_data,
                                             size_t* size) {
    struct trusty_app_img* app_img;

    app_img = &trusty_app->app_img;
    if (!app_img->manifest_start) {
        dprintf(CRITICAL, "manifest section header not found\n");
        return ERR_NOT_VALID;
    }

    /* manifest data is embedded in kernel */
    dprintf(SPEW,
            "trusty app manifest: start %p size 0x%08" PRIxPTR " end %p\n",
            (void*)app_img->manifest_start,
            app_img->manifest_end - app_img->manifest_start,
            (void*)app_img->manifest_end);

    *size = app_img->manifest_end - app_img->manifest_start;
    *manifest_data = (char*)app_img->manifest_start;

    return NO_ERROR;
}

static void destroy_app_phys_mem(struct phys_mem_obj* obj) {
    struct manifest_mmio_entry* mmio_entry;
    mmio_entry = containerof(obj, struct manifest_mmio_entry, phys_mem_obj);
    assert(!list_in_list(&mmio_entry->node));
    free(mmio_entry);
}

/**
 * load_app_elf_gnu_property_array() - Load app properties from ELF GNU property
 * array.
 * @trusty_app:  Trusty application, both giving ELF section and props.
 * @offset:      Byte offset of the ELF GNU property array structure.
 * @length:      Length in bytes of the ELF GNU property array.
 * @out:         Out pointer to write the selected bias to. Only valid if the
 *               function returned 0.
 *
 * Return: If nonzero, the ELF is malformed.  Otherwise NO_ERROR.
 */
static status_t load_app_elf_gnu_property_array(struct trusty_app* trusty_app,
                                                Elf_Off offset,
                                                size_t length) {
    const void* elf_start = (void*)trusty_app->app_img.img_start;

    /* Check property array is within the ELF image */
    if (!address_range_within_img(elf_start + offset, length,
                                  &trusty_app->app_img)) {
        return ERR_NOT_VALID;
    }

    /* Walk through the variable length properties */
    while (length >= sizeof(ELF_GnuProp)) {
        const ELF_GnuProp* gp = elf_start + offset;
        Elf_Word gp_size = sizeof(ELF_GnuProp);

        /* Check header is within bounds */
        if (!address_range_within_img(gp, gp_size, &trusty_app->app_img)) {
            return ERR_NOT_VALID;
        }

        /* Update full size and round to either 4 or 8 byte alignment */
        gp_size += gp->pr_datasz;
        gp_size += sizeof(Elf_Word) - 1;
        gp_size &= ~(sizeof(Elf_Word) - 1);

        /* Check access to the full property */
        if (gp_size < sizeof(ELF_GnuProp) ||
            !address_range_within_img(gp, gp_size, &trusty_app->app_img)) {
            return ERR_NOT_VALID;
        }

#ifdef ARCH_ARM64
        /* TODO(mikemcternan): Split into an arch specific function */
        if (gp && gp->pr_type == GNU_PROPERTY_AARCH64_FEATURE_1_AND) {
            /* This property should always have an 32-bit value */
            if (gp->pr_datasz != sizeof(Elf32_Word)) {
                return ERR_NOT_VALID;
            }

            switch (gp->pr_data[0]) {
            case GNU_PROPERTY_AARCH64_FEATURE_1_BTI:
                trusty_app->props.feature_bti = true;
                break;
            default:
                break;
            }
        }
#endif
        if (length <= gp_size) {
            length = 0;
        } else {
            length -= gp_size;
            offset += gp_size;
        }
    }

    return NO_ERROR;
}

static status_t load_app_elf_options(struct trusty_app* trusty_app) {
    const struct trusty_app_img* app_img = &trusty_app->app_img;
    const ELF_EHDR* elf = (ELF_EHDR*)app_img->img_start;

    /* Iterate ELF program headers to find PT_GNU_PROPERTY section */
    for (int i = 0; i < elf->e_phnum; i++) {
        const ELF_PHDR* phdr =
                (const void*)elf + elf->e_phoff + (elf->e_phentsize * i);

        if (!address_range_within_img(phdr, sizeof(ELF_PHDR),
                                      &trusty_app->app_img)) {
            return ERR_NOT_VALID;
        }

        /* Check for a GNU property section */
        if (phdr->p_type == PT_GNU_PROPERTY) {
            const ELF_NHDR* nhdr = (const void*)elf + phdr->p_offset;
            const int nhdr_len = sizeof(ELF_NHDR) + sizeof("GNU");

            if (!address_range_within_img(nhdr, nhdr_len,
                                          &trusty_app->app_img)) {
                return ERR_NOT_VALID;
            }

            if (nhdr->n_namesz == sizeof("GNU") &&
                nhdr->n_type == NT_GNU_PROPERTY_TYPE_0 &&
                strcmp("GNU", (const char*)nhdr + sizeof(ELF_NHDR)) == 0) {
                const Elf_Off n_desc = phdr->p_offset + nhdr_len;

                status_t ret = load_app_elf_gnu_property_array(
                        trusty_app, n_desc, nhdr->n_descsz);
                if (ret != NO_ERROR) {
                    return ret;
                }
            }
        }
    }

    return NO_ERROR;
}

static status_t load_app_config_options(struct trusty_app* trusty_app) {
    char* manifest_data;
    size_t manifest_size;
    uint32_t mmio_arch_mmu_flags;
    uint64_t mmio_size;
    struct manifest_mmio_entry* mmio_entry;
    paddr_t tmp_paddr;
    status_t ret;
    struct manifest_port_entry* entry;
    struct app_manifest_iterator manifest_iter;
    struct app_manifest_config_entry manifest_entry;
    const char* unknown_app_name = "<unknown>";

    /* init default config options before parsing manifest */
    trusty_app->props.app_name = unknown_app_name;
    trusty_app->props.min_heap_size = DEFAULT_HEAP_SIZE;
    trusty_app->props.min_stack_size = DEFAULT_STACK_SIZE;
    /* binary manifest must specify the min shadow stack size */
    trusty_app->props.min_shadow_stack_size = 0;
    trusty_app->props.mgmt_flags = DEFAULT_MGMT_FLAGS;
    trusty_app->props.pinned_cpu = APP_MANIFEST_PINNED_CPU_NONE;
    trusty_app->props.priority = DEFAULT_PRIORITY;

    ret = load_app_elf_options(trusty_app);
    if (ret != NO_ERROR) {
        return ERR_NOT_VALID;
    }

    manifest_data = NULL;
    manifest_size = 0;
    ret = get_app_manifest_config_data(trusty_app, &manifest_data,
                                       &manifest_size);
    if (ret != NO_ERROR) {
        return ERR_NOT_VALID;
    }

    /*
     * Step thru configuration blob.
     *
     * Save off some configuration data while we are here but
     * defer processing of other data until it is needed later.
     */
    ret = app_manifest_iterator_reset(&manifest_iter, manifest_data,
                                      manifest_size);
    if (ret != NO_ERROR) {
        dprintf(CRITICAL, "error parsing manifest for app %u\n",
                trusty_app->app_id);
        return ret;
    }
    while (app_manifest_iterator_next(&manifest_iter, &manifest_entry, &ret)) {
        switch (manifest_entry.key) {
        case APP_MANIFEST_CONFIG_KEY_MIN_STACK_SIZE:
            trusty_app->props.min_stack_size =
                    manifest_entry.value.min_stack_size;
            if (trusty_app->props.min_stack_size == 0) {
                dprintf(CRITICAL,
                        "manifest MIN_STACK_SIZE is 0 of app %u, %s\n",
                        trusty_app->app_id, trusty_app->props.app_name);
                return ERR_NOT_VALID;
            }
            break;
        case APP_MANIFEST_CONFIG_KEY_MIN_HEAP_SIZE:
            trusty_app->props.min_heap_size =
                    manifest_entry.value.min_heap_size;
            break;
        case APP_MANIFEST_CONFIG_KEY_MAP_MEM:
            mmio_arch_mmu_flags = manifest_entry.value.mem_map.arch_mmu_flags;
            mmio_size = round_up(manifest_entry.value.mem_map.size, PAGE_SIZE);
            trusty_app->props.map_io_mem_cnt++;

            if (!IS_PAGE_ALIGNED(manifest_entry.value.mem_map.offset)) {
                dprintf(CRITICAL, "mmio_id %u not page aligned of app %u, %s\n",
                        manifest_entry.value.mem_map.id, trusty_app->app_id,
                        trusty_app->props.app_name);
                return ERR_NOT_VALID;
            }

            if ((paddr_t)manifest_entry.value.mem_map.offset !=
                        manifest_entry.value.mem_map.offset ||
                (size_t)mmio_size != mmio_size) {
                dprintf(CRITICAL,
                        "mmio_id %d address/size too large of app %u, %s\n",
                        manifest_entry.value.mem_map.id, trusty_app->app_id,
                        trusty_app->props.app_name);
                return ERR_NOT_VALID;
            }

            if (!mmio_size ||
                __builtin_add_overflow(manifest_entry.value.mem_map.offset,
                                       mmio_size - 1, &tmp_paddr)) {
                dprintf(CRITICAL, "mmio_id %u bad size of app %u, %s\n",
                        manifest_entry.value.mem_map.id, trusty_app->app_id,
                        trusty_app->props.app_name);
                return ERR_NOT_VALID;
            }

            if (manifest_entry.value.mem_map.arch_mmu_flags &
                        ~(ARCH_MMU_FLAG_CACHE_MASK | ARCH_MMU_FLAG_NS) ||
                ((manifest_entry.value.mem_map.arch_mmu_flags &
                  ARCH_MMU_FLAG_CACHE_MASK) != ARCH_MMU_FLAG_CACHED &&
                 (manifest_entry.value.mem_map.arch_mmu_flags &
                  ARCH_MMU_FLAG_CACHE_MASK) != ARCH_MMU_FLAG_UNCACHED &&
                 (manifest_entry.value.mem_map.arch_mmu_flags &
                  ARCH_MMU_FLAG_CACHE_MASK) != ARCH_MMU_FLAG_UNCACHED_DEVICE)) {
                dprintf(CRITICAL,
                        "mmio_id %u bad arch_mmu_flags 0x%x of app %u, %s\n",
                        manifest_entry.value.mem_map.id,
                        manifest_entry.value.mem_map.arch_mmu_flags,
                        trusty_app->app_id, trusty_app->props.app_name);
                return ERR_NOT_VALID;
            }
            mmio_arch_mmu_flags |= ARCH_MMU_FLAG_PERM_NO_EXECUTE;

            if (!app_mmio_is_allowed(
                        trusty_app,
                        (paddr_t)manifest_entry.value.mem_map.offset,
                        mmio_size)) {
                dprintf(CRITICAL,
                        "mmio_id %u not allowed for loadable app %u, %s\n",
                        manifest_entry.value.mem_map.id, trusty_app->app_id,
                        trusty_app->props.app_name);
                return ERR_NOT_VALID;
            }

            mmio_entry = calloc(1, sizeof(struct manifest_mmio_entry));
            if (!mmio_entry) {
                dprintf(CRITICAL,
                        "Failed to allocate memory for manifest mmio %d of app %u, %s\n",
                        manifest_entry.value.mem_map.id, trusty_app->app_id,
                        trusty_app->props.app_name);
                return ERR_NO_MEMORY;
            }

            phys_mem_obj_dynamic_initialize(&mmio_entry->phys_mem_obj,
                                            &mmio_entry->phys_mem_obj_self_ref,
                                            manifest_entry.value.mem_map.offset,
                                            mmio_size, mmio_arch_mmu_flags,
                                            destroy_app_phys_mem);
            mmio_entry->id = manifest_entry.value.mem_map.id;
            list_add_tail(&trusty_app->props.mmio_entry_list,
                          &mmio_entry->node);

            break;
        case APP_MANIFEST_CONFIG_KEY_MGMT_FLAGS:
            trusty_app->props.mgmt_flags = manifest_entry.value.mgmt_flags;
            break;
        case APP_MANIFEST_CONFIG_KEY_START_PORT:
            if (manifest_entry.value.start_port.name_size > IPC_PORT_PATH_MAX) {
                dprintf(CRITICAL,
                        "manifest port name %s too long:%#" PRIx32
                        " of app %u, %s\n",
                        manifest_entry.value.start_port.name,
                        manifest_entry.value.start_port.name_size,
                        trusty_app->app_id, trusty_app->props.app_name);
                return ERR_NOT_VALID;
            }

            entry = find_manifest_port_entry_locked(
                    manifest_entry.value.start_port.name, NULL);
            if (entry) {
                dprintf(CRITICAL, "Port %s is already registered\n",
                        manifest_entry.value.start_port.name);
                return ERR_ALREADY_EXISTS;
            }

            entry = calloc(1, sizeof(struct manifest_port_entry));
            if (!entry) {
                dprintf(CRITICAL,
                        "Failed to allocate memory for manifest port %s of app %u, %s\n",
                        manifest_entry.value.start_port.name,
                        trusty_app->app_id, trusty_app->props.app_name);
                return ERR_NO_MEMORY;
            }

            entry->flags = manifest_entry.value.start_port.flags;
            entry->path_len = manifest_entry.value.start_port.name_size;
            entry->path = manifest_entry.value.start_port.name;

            list_add_tail(&trusty_app->props.port_entry_list, &entry->node);

            break;
        case APP_MANIFEST_CONFIG_KEY_PINNED_CPU:
            if (manifest_entry.value.pinned_cpu >= SMP_MAX_CPUS) {
                dprintf(CRITICAL,
                        "pinned CPU index %u out of range, app %u, %s\n",
                        manifest_entry.value.pinned_cpu, trusty_app->app_id,
                        trusty_app->props.app_name);
                return ERR_NOT_VALID;
            }

            trusty_app->props.pinned_cpu = manifest_entry.value.pinned_cpu;
            break;
        case APP_MANIFEST_CONFIG_KEY_PRIORITY:
            if (manifest_entry.value.priority < (LOWEST_PRIORITY + 2) ||
                manifest_entry.value.priority > (HIGHEST_PRIORITY - 1)) {
                dprintf(CRITICAL,
                        "priority value %u out of range, app %u, %s\n",
                        manifest_entry.value.priority, trusty_app->app_id,
                        trusty_app->props.app_name);
                return ERR_NOT_VALID;
            }
            trusty_app->props.priority = manifest_entry.value.priority;
            break;
        case APP_MANIFEST_CONFIG_KEY_MIN_SHADOW_STACK_SIZE:
#if !USER_SCS_SUPPORTED
            if (manifest_entry.value.min_shadow_stack_size) {
                dprintf(CRITICAL,
                        "Shadow call stack requested by app %u, %s. Kernel "
                        "was not built to support user shadow call stacks\n",
                        trusty_app->app_id, trusty_app->props.app_name);
                return ERR_NOT_VALID;
            }
#endif
            trusty_app->props.min_shadow_stack_size =
                    manifest_entry.value.min_shadow_stack_size;
            /* min_shadow_stack_size == 0 means app opted out of shadow stack */
            break;
        case APP_MANIFEST_CONFIG_KEY_UUID:
            memcpy(&trusty_app->props.uuid, &manifest_entry.value.uuid,
                   sizeof(uuid_t));
            break;
        case APP_MANIFEST_CONFIG_KEY_APP_NAME:
            trusty_app->props.app_name = manifest_entry.value.app_name;
            break;
        case APP_MANIFEST_CONFIG_KEY_VERSION:
        case APP_MANIFEST_CONFIG_KEY_MIN_VERSION:
        case APP_MANIFEST_CONFIG_KEY_APPLOADER_FLAGS:
            /* Handled by apploader */
            break;
        }
    }
    if (ret != NO_ERROR) {
        dprintf(CRITICAL, "error parsing manifest for app %u\n",
                trusty_app->app_id);
        return ret;
    }
    if (trusty_app->props.app_name == unknown_app_name) {
        dprintf(CRITICAL, "app-name missing for app %u\n", trusty_app->app_id);
        return ERR_NOT_VALID;
    }

    if (trusty_app_find_by_uuid_locked(&trusty_app->props.uuid)) {
        PRINT_TRUSTY_APP_UUID(CRITICAL, trusty_app->app_id,
                              &trusty_app->props.uuid);
        dprintf(CRITICAL, "app already registered\n");
        return ERR_ALREADY_EXISTS;
    }

    PRINT_TRUSTY_APP_UUID(SPEW, trusty_app->app_id, &trusty_app->props.uuid);
    dprintf(SPEW, "trusty_app %u name: %s priority: %u\n", trusty_app->app_id,
            trusty_app->props.app_name, trusty_app->props.priority);

    if (trusty_app->props.feature_bti) {
        const char* status;
#ifndef USER_BTI_DISABLED
        status = arch_bti_supported() ? "enabled"
                                      : "ignored (unsupported by hw)";
#else
        status = "ignored (disabled in kernel)";
#endif
        dprintf(SPEW, "trusty_app %u  bti: %s\n", trusty_app->app_id, status);
    }

    LTRACEF("trusty_app %p: stack_sz=0x%x\n", trusty_app,
            trusty_app->props.min_stack_size);
    LTRACEF("trusty_app %p: heap_sz=0x%x\n", trusty_app,
            trusty_app->props.min_heap_size);
    LTRACEF("trusty_app %p: num_io_mem=%d\n", trusty_app,
            trusty_app->props.map_io_mem_cnt);

    return NO_ERROR;
}

static status_t init_brk(struct trusty_app* trusty_app) {
    status_t status;
    vaddr_t start_brk;
    vaddr_t brk_size;

    /*
     * Make sure the heap is page aligned and page sized.
     * Most user space allocators assume this. Historically, we tried to
     * scavange space at the end of .bss for the heap but this misaligned the
     * heap and caused userspace allocators to behave is subtly unpredictable
     * ways.
     */
    start_brk = 0;
    brk_size = round_up(trusty_app->props.min_heap_size, PAGE_SIZE);

    /* Allocate if needed. */
    if (brk_size > 0) {
        status = vmm_alloc_no_physical(
                trusty_app->aspace, "brk_heap_res", brk_size,
                (void**)&start_brk, PAGE_SIZE_SHIFT, 0,
                ARCH_MMU_FLAG_PERM_USER | ARCH_MMU_FLAG_PERM_NO_EXECUTE);

        if (status != NO_ERROR) {
            dprintf(CRITICAL,
                    "failed(%d) to create heap(0x%" PRIxPTR
                    ") for app %u, %s\n",
                    status, start_brk, trusty_app->app_id,
                    trusty_app->props.app_name);
            return ERR_NO_MEMORY;
        }
    }

    /* Record the location. */
    trusty_app->used_brk = false;
    trusty_app->start_brk = start_brk;
    trusty_app->cur_brk = start_brk;
    trusty_app->end_brk = start_brk + brk_size;

    return NO_ERROR;
}

/**
 * select_load_bias() - Pick a a load bias for an ELF
 * @phdr:      Pre-validated program header array base
 * @num_phdrs: Number of program headers
 * @aspace:    The address space the bias needs to be valid in
 * @out:       Out pointer to write the selected bias to. Only valid if the
 *             function returned 0.
 *
 * This function calculates an offset that can be added to every loadable ELF
 * segment in the image and still result in a legal load address.
 *
 * Return: A status code indicating whether a bias was located. If nonzero,
 *         the bias output may be invalid.
 */
static status_t select_load_bias(ELF_PHDR* phdr,
                                 size_t num_phdrs,
                                 vmm_aspace_t* aspace,
                                 vaddr_t* out) {
    DEBUG_ASSERT(out);
#if ASLR
    vaddr_t low = VADDR_MAX;
    vaddr_t high = 0;
    for (size_t i = 0; i < num_phdrs; i++, phdr++) {
        low = MIN(low, phdr->p_vaddr);
        vaddr_t candidate_high;
        if (!__builtin_add_overflow(phdr->p_vaddr, phdr->p_memsz,
                                    &candidate_high)) {
            high = MAX(high, candidate_high);
        } else {
            dprintf(CRITICAL, "Segment %zu overflows virtual address space\n",
                    i);
            return ERR_NOT_VALID;
        }
    }
    LTRACEF("ELF Segment range: %" PRIxVADDR "->%" PRIxVADDR "\n", low, high);

    DEBUG_ASSERT(high >= low);
    size_t size = round_up(high - low, PAGE_SIZE);
    LTRACEF("Spot size: %zu\n", size);

    vaddr_t spot;
    if (!vmm_find_spot(aspace, size, &spot)) {
        return ERR_NO_MEMORY;
    }
    LTRACEF("Load target: %" PRIxVADDR "\n", spot);

    /*
     * Overflow is acceptable here, since adding the delta to the lowest
     * ELF load address will still return to spot, which was the goal.
     */
    __builtin_sub_overflow(spot, low, out);
#else
    /* If ASLR is disabled, the app is not PIE, use a load bias of 0 */
    *out = 0;
#endif

    LTRACEF("Load bias: %" PRIxVADDR "\n", *out);

    return NO_ERROR;
}

static bool elf_vaddr_mapped(struct trusty_app* trusty_app,
                             size_t vaddr,
                             ssize_t offset) {
    ELF_EHDR* elf_hdr = (ELF_EHDR*)trusty_app->app_img.img_start;
    void* trusty_app_image = (void*)trusty_app->app_img.img_start;
    ELF_PHDR* prg_hdr = (ELF_PHDR*)(trusty_app_image + elf_hdr->e_phoff);
    if (__builtin_add_overflow(vaddr, offset, &vaddr)) {
        return false;
    }
    for (size_t i = 0; i < elf_hdr->e_phnum; i++, prg_hdr++) {
        Elf_Addr end;
        __builtin_add_overflow(prg_hdr->p_vaddr, prg_hdr->p_memsz, &end);
        if (prg_hdr->p_type == PT_LOAD &&
            vaddr >= round_down(prg_hdr->p_vaddr, PAGE_SIZE) &&
            vaddr < round_up(end, PAGE_SIZE)) {
            return true;
        }
    }
    return false;
}

static status_t alloc_address_map(struct trusty_app* trusty_app) {
    ELF_EHDR* elf_hdr = (ELF_EHDR*)trusty_app->app_img.img_start;
    void* trusty_app_image;
    ELF_PHDR* prg_hdr;
    u_int i;
    status_t ret;
    trusty_app_image = (void*)trusty_app->app_img.img_start;

    prg_hdr = (ELF_PHDR*)(trusty_app_image + elf_hdr->e_phoff);

    if (!address_range_within_img(prg_hdr, sizeof(ELF_PHDR) * elf_hdr->e_phnum,
                                  &trusty_app->app_img)) {
        dprintf(CRITICAL, "ELF program headers table out of bounds\n");
        return ERR_NOT_VALID;
    }

    status_t bias_result =
            select_load_bias(prg_hdr, elf_hdr->e_phnum, trusty_app->aspace,
                             &trusty_app->load_bias);
    if (bias_result) {
        return bias_result;
    }

    size_t has_guard_low = 0;
    size_t has_guard_high = 0;

    /* create mappings for PT_LOAD sections */
    for (i = 0; i < elf_hdr->e_phnum; i++, prg_hdr++) {
        /* load_bias uses overflow to lower vaddr if needed */
        Elf_Addr p_vaddr;
        __builtin_add_overflow(prg_hdr->p_vaddr, trusty_app->load_bias,
                               &p_vaddr);

        LTRACEF("trusty_app %d, %s: ELF type 0x%x"
                ", vaddr 0x%08" PRIxELF_Addr ", paddr 0x%08" PRIxELF_Addr
                ", rsize 0x%08" PRIxELF_Size ", msize 0x%08" PRIxELF_Size
                ", flags 0x%08x\n",
                trusty_app->app_id, trusty_app->props.app_name, prg_hdr->p_type,
                p_vaddr, prg_hdr->p_paddr, prg_hdr->p_filesz, prg_hdr->p_memsz,
                prg_hdr->p_flags);

        if (prg_hdr->p_type != PT_LOAD)
            continue;

        if (p_vaddr < USER_ASPACE_BASE) {
            TRACEF("Attempted to load segment beneath user address space\n");
            return ERR_NOT_VALID;
        }

        vaddr_t vaddr = p_vaddr;
        vaddr_t img_kvaddr = (vaddr_t)(trusty_app_image + prg_hdr->p_offset);
        size_t mapping_size;

        if (vaddr & PAGE_MASK) {
            dprintf(CRITICAL,
                    "segment %u load address 0x%" PRIxVADDR
                    " in not page aligned for app %u, %s\n",
                    i, vaddr, trusty_app->app_id, trusty_app->props.app_name);
            return ERR_NOT_VALID;
        }

        if (img_kvaddr & PAGE_MASK) {
            dprintf(CRITICAL,
                    "segment %u image address 0x%" PRIxVADDR
                    " in not page aligned for app %u, %s\n",
                    i, img_kvaddr, trusty_app->app_id,
                    trusty_app->props.app_name);
            return ERR_NOT_VALID;
        }

        uint vmm_flags = VMM_FLAG_VALLOC_SPECIFIC;
        if (elf_vaddr_mapped(trusty_app, prg_hdr->p_vaddr,
                             -(ssize_t)PAGE_SIZE)) {
            vmm_flags |= VMM_FLAG_NO_START_GUARD;
        } else {
            has_guard_low++;
        }
        if (elf_vaddr_mapped(trusty_app, prg_hdr->p_vaddr + prg_hdr->p_memsz,
                             PAGE_SIZE)) {
            vmm_flags |= VMM_FLAG_NO_END_GUARD;
        } else {
            has_guard_high++;
        }

        uint arch_mmu_flags = ARCH_MMU_FLAG_PERM_USER;
        if (!(prg_hdr->p_flags & PF_X)) {
            arch_mmu_flags += ARCH_MMU_FLAG_PERM_NO_EXECUTE;
        }

        if (prg_hdr->p_flags & PF_W) {
            paddr_t upaddr;
            void* load_kvaddr;
            size_t copy_size;
            size_t file_size;
            mapping_size = round_up(prg_hdr->p_memsz, PAGE_SIZE);

            if (!address_range_within_img((void*)img_kvaddr, prg_hdr->p_filesz,
                                          &trusty_app->app_img)) {
                dprintf(CRITICAL, "ELF Program segment %u out of bounds\n", i);
                return ERR_NOT_VALID;
            }

            ret = vmm_alloc(trusty_app->aspace, "elfseg", mapping_size,
                            (void**)&vaddr, PAGE_SIZE_SHIFT, vmm_flags,
                            arch_mmu_flags);

            if (ret != NO_ERROR) {
                dprintf(CRITICAL,
                        "failed(%d) to allocate data segment(0x%" PRIxVADDR
                        ") %u for app %u, %s\n",
                        ret, vaddr, i, trusty_app->app_id,
                        trusty_app->props.app_name);
                return ret;
            }

            ASSERT(vaddr == p_vaddr);

            file_size = prg_hdr->p_filesz;
            while (file_size > 0) {
                ret = arch_mmu_query(&trusty_app->aspace->arch_aspace, vaddr,
                                     &upaddr, NULL);
                if (ret != NO_ERROR) {
                    dprintf(CRITICAL, "Could not copy data segment: %d\n", ret);
                    return ret;
                }

                load_kvaddr = paddr_to_kvaddr(upaddr);
                ASSERT(load_kvaddr);
                copy_size = MIN(file_size, PAGE_SIZE);
                memcpy(load_kvaddr, (void*)img_kvaddr, copy_size);
                file_size -= copy_size;
                vaddr += copy_size;
                img_kvaddr += copy_size;
            }

        } else {
            mapping_size = round_up(prg_hdr->p_filesz, PAGE_SIZE);

            if (!address_range_within_img((void*)img_kvaddr, mapping_size,
                                          &trusty_app->app_img)) {
                dprintf(CRITICAL, "ELF Program segment %u out of bounds\n", i);
                return ERR_NOT_VALID;
            }
            if (mapping_size != round_up(prg_hdr->p_memsz, PAGE_SIZE)) {
                dprintf(CRITICAL, "ELF Program segment %u bad memsz\n", i);
                return ERR_NOT_VALID;
            }

            paddr_t* paddr_arr =
                    calloc(mapping_size / PAGE_SIZE, sizeof(paddr_t));
            if (!paddr_arr) {
                dprintf(CRITICAL,
                        "Failed to allocate physical address array\n");
                return ERR_NO_MEMORY;
            }

            for (size_t j = 0; j < mapping_size / PAGE_SIZE; j++) {
                paddr_arr[j] =
                        vaddr_to_paddr((void*)(img_kvaddr + PAGE_SIZE * j));
                DEBUG_ASSERT(paddr_arr[j] && !(paddr_arr[j] & PAGE_MASK));
            }

            arch_mmu_flags += ARCH_MMU_FLAG_PERM_RO;
            ret = vmm_alloc_physical_etc(
                    trusty_app->aspace, "elfseg", mapping_size, (void**)&vaddr,
                    PAGE_SIZE_SHIFT, paddr_arr, mapping_size / PAGE_SIZE,
                    vmm_flags, arch_mmu_flags);
            if (ret != NO_ERROR) {
                dprintf(CRITICAL,
                        "failed(%d) to map RO segment(0x%" PRIxVADDR
                        ") %u for app %u, %s\n",
                        ret, vaddr, i, trusty_app->app_id,
                        trusty_app->props.app_name);
                free(paddr_arr);
                return ret;
            }

            ASSERT(vaddr == p_vaddr);
            free(paddr_arr);
        }

        LTRACEF("trusty_app %d, %s: load vaddr 0x%08" PRIxVADDR
                ", paddr 0x%08" PRIxVADDR
                ", rsize 0x%08zx, msize 0x%08" PRIxELF_Size
                ", access r%c%c, flags 0x%x\n",
                trusty_app->app_id, trusty_app->props.app_name, vaddr,
                vaddr_to_paddr((void*)vaddr), mapping_size, prg_hdr->p_memsz,
                arch_mmu_flags & ARCH_MMU_FLAG_PERM_RO ? '-' : 'w',
                arch_mmu_flags & ARCH_MMU_FLAG_PERM_NO_EXECUTE ? '-' : 'x',
                arch_mmu_flags);
    }

    ASSERT(has_guard_low);
    ASSERT(has_guard_high);
    ASSERT(has_guard_low == has_guard_high);

    ret = init_brk(trusty_app);
    if (ret != NO_ERROR) {
        dprintf(CRITICAL,
                "failed to load trusty_app: trusty_app heap creation error\n");
        return ret;
    }

    dprintf(SPEW,
            "trusty_app %d, %s: brk:  start 0x%08" PRIxPTR " end 0x%08" PRIxPTR
            "\n",
            trusty_app->app_id, trusty_app->props.app_name,
            trusty_app->start_brk, trusty_app->end_brk);
    dprintf(SPEW, "trusty_app %d, %s: entry 0x%08" PRIxELF_Addr "\n",
            trusty_app->app_id, trusty_app->props.app_name, elf_hdr->e_entry);

    return NO_ERROR;
}

static bool has_waiting_connection(struct trusty_app* app) {
    struct manifest_port_entry* entry;

    /*
     * Don't hold the apps lock when calling into other subsystems with calls
     * that may grab additional locks.
     */
    DEBUG_ASSERT(!is_mutex_held(&apps_lock));

    list_for_every_entry(&app->props.port_entry_list, entry,
                         struct manifest_port_entry, node) {
        if (ipc_connection_waiting_for_port(entry->path, entry->flags)) {
            return true;
        }
    }

    return false;
}

static void kill_waiting_connections(struct trusty_app* app) {
    struct manifest_port_entry* entry;

    /*
     * Don't hold the apps lock when calling into other subsystems with calls
     * that may grab additional locks.
     */
    DEBUG_ASSERT(!is_mutex_held(&apps_lock));

    list_for_every_entry(&app->props.port_entry_list, entry,
                         struct manifest_port_entry, node) {
        ipc_remove_connection_waiting_for_port(entry->path, entry->flags);
    }
}

/* Must be called with the apps_lock held */
static status_t request_app_start_locked(struct trusty_app* app) {
    DEBUG_ASSERT(is_mutex_held(&apps_lock));

    switch (app->state) {
    case APP_NOT_RUNNING:
        app->state = APP_STARTING;
        event_signal(&app_mgr_event, false);
        return NO_ERROR;
    case APP_STARTING:
    case APP_RUNNING:
    case APP_RESTARTING:
        return ERR_ALREADY_STARTED;
    case APP_TERMINATING:
        /*
         * We got a new connection while terminating, change the state so
         * app_mgr_handle_terminating can restart the app.
         */
        app->state = APP_RESTARTING;
        return ERR_ALREADY_STARTED;
    case APP_FAILED_TO_START:
        /* The app failed to start so it shouldn't accept new connections. */
        return ERR_CANCELLED;
        /*
         * There is no default case here because we want the compiler to warn us
         * if we forget a state (controlled by the -Wswitch option which is
         * included in -Wall). Whenever someone adds a new state without
         * handling it here, they should get a compiler error.
         */
    }
}

/*
 * Create a trusty_app from its memory image and add it to the global list of
 * apps. Returns the created app in out_trusty_app if not NULL.
 */
static status_t trusty_app_create(struct trusty_app_img* app_img,
                                  struct trusty_app** out_trusty_app,
                                  uint32_t flags) {
    ELF_EHDR* ehdr;
    struct trusty_app* trusty_app;
    status_t ret;
    struct manifest_port_entry* port_entry;
    struct manifest_port_entry* tmp_port_entry;
    struct manifest_mmio_entry* mmio_entry;
    struct manifest_mmio_entry* tmp_mmio_entry;

    DEBUG_ASSERT(!(flags & ~(uint32_t)APP_FLAGS_CREATION_MASK));

    if (app_img->img_start & PAGE_MASK || app_img->img_end & PAGE_MASK) {
        dprintf(CRITICAL,
                "app image is not page aligned start 0x%" PRIxPTR
                " end 0x%" PRIxPTR "\n",
                app_img->img_start, app_img->img_end);
        return ERR_NOT_VALID;
    }

    dprintf(SPEW, "trusty_app: start %p size 0x%08" PRIxPTR " end %p\n",
            (void*)app_img->img_start, app_img->img_end - app_img->img_start,
            (void*)app_img->img_end);

    trusty_app = (struct trusty_app*)calloc(1, sizeof(struct trusty_app));
    if (!trusty_app) {
        dprintf(CRITICAL,
                "trusty_app: failed to allocate memory for trusty app\n");
        return ERR_NO_MEMORY;
    }
    list_initialize(&trusty_app->props.port_entry_list);
    list_initialize(&trusty_app->props.mmio_entry_list);
    list_initialize(&trusty_app->props.dma_entry_list);

    ehdr = (ELF_EHDR*)app_img->img_start;
    if (!address_range_within_img(ehdr, sizeof(ELF_EHDR), app_img)) {
        dprintf(CRITICAL, "trusty_app_create: ELF header out of bounds\n");
        ret = ERR_NOT_VALID;
        goto err_hdr;
    }

    if (strncmp((char*)ehdr->e_ident, ELFMAG, SELFMAG)) {
        dprintf(CRITICAL, "trusty_app_create: ELF header not found\n");
        ret = ERR_NOT_VALID;
        goto err_hdr;
    }

    trusty_app->app_id = trusty_next_app_id++;
    trusty_app->app_img = *app_img;
    trusty_app->state = APP_NOT_RUNNING;
    trusty_app->flags |= flags;

    mutex_acquire(&apps_lock);

    ret = load_app_config_options(trusty_app);
    if (ret == NO_ERROR) {
        list_add_tail(&trusty_app_list, &trusty_app->node);
    }

    mutex_release(&apps_lock);

    if (ret == NO_ERROR) {
        if (out_trusty_app) {
            /*
             * TODO: this returns an app pointer without holding the lock; the
             * app might get unloaded while the caller holds this pointer, so
             * we need to handle this case correctly
             */
            *out_trusty_app = trusty_app;
        }

        return ret;
    }

    dprintf(CRITICAL, "manifest processing failed(%d)\n", ret);

err_load:
    list_for_every_entry_safe(&trusty_app->props.port_entry_list, port_entry,
                              tmp_port_entry, struct manifest_port_entry,
                              node) {
        list_delete(&port_entry->node);
        free(port_entry);
    }
    list_for_every_entry_safe(&trusty_app->props.mmio_entry_list, mmio_entry,
                              tmp_mmio_entry, struct manifest_mmio_entry,
                              node) {
        list_delete(&mmio_entry->node);
        vmm_obj_del_ref(&mmio_entry->phys_mem_obj.vmm_obj,
                        &mmio_entry->phys_mem_obj_self_ref);
    }
err_hdr:
    free(trusty_app);
    return ret;
}

status_t trusty_app_create_and_start(struct trusty_app_img* app_img,
                                     uint32_t flags) {
    status_t ret;
    struct trusty_app* trusty_app;

    ret = trusty_app_create(app_img, &trusty_app, flags);
    if (ret != NO_ERROR) {
        return ret;
    }

    /* Loadable apps with deferred_start might have clients waiting for them */
    if (!(trusty_app->props.mgmt_flags &
          APP_MANIFEST_MGMT_FLAGS_DEFERRED_START) ||
        has_waiting_connection(trusty_app)) {
        mutex_acquire(&apps_lock);
        ret = request_app_start_locked(trusty_app);
        mutex_release(&apps_lock);

        /*
         * Since we drop apps_lock between trusty_app_create and here,
         * it is possible for another thread to race us and start the
         * app from trusty_app_request_start_by_port before we
         * reacquire the lock. In that case, request_app_start_locked
         * returns ERR_ALREADY_STARTED here. We treat this case as a
         * success and return NO_ERROR since the application is
         * running and we don't want the kernel service to
         * free its memory.
         */
        if (ret == ERR_ALREADY_STARTED) {
            ret = NO_ERROR;
        }
    }

    return ret;
}

status_t trusty_app_setup_mmio(struct trusty_app* trusty_app,
                               uint32_t mmio_id,
                               user_addr_t* uaddr_p,
                               uint32_t map_size) {
    status_t ret;
    struct manifest_mmio_entry* mmio_entry;

    /* Should only be called on the currently running app */
    DEBUG_ASSERT(trusty_app == current_trusty_app());

    ASSERT(uaddr_p);
    void* va = (void*)(uintptr_t)(*uaddr_p);

    list_for_every_entry(&trusty_app->props.mmio_entry_list, mmio_entry,
                         struct manifest_mmio_entry, node) {
        if (mmio_entry->id != mmio_id) {
            continue;
        }

        map_size = round_up(map_size, PAGE_SIZE);

        ret = vmm_alloc_obj(
                trusty_app->aspace, "mmio", &mmio_entry->phys_mem_obj.vmm_obj,
                0, map_size, &va, 0, 0,
                ARCH_MMU_FLAG_PERM_USER | ARCH_MMU_FLAG_PERM_NO_EXECUTE);
        if (ret == NO_ERROR) {
            *uaddr_p = (user_addr_t)(uintptr_t)va;
            DEBUG_ASSERT((void*)(uintptr_t)(*uaddr_p) == va);
        }
        return ret;
    }

    return ERR_NOT_FOUND;
}

static status_t trusty_app_start(struct trusty_app* trusty_app) {
    char name[32];
    struct trusty_thread* trusty_thread;
    struct trusty_app_notifier* n;
    ELF_EHDR* elf_hdr;
    uint flags = 0;
    int ret;

    DEBUG_ASSERT(trusty_app->state == APP_STARTING);

    snprintf(name, sizeof(name), "trusty_app_%d_%08x-%04x-%04x",
             trusty_app->app_id, trusty_app->props.uuid.time_low,
             trusty_app->props.uuid.time_mid,
             trusty_app->props.uuid.time_hi_and_version);

#ifndef USER_BTI_DISABLED
    if (trusty_app->props.feature_bti && arch_bti_supported()) {
        flags |= VMM_ASPACE_FLAG_BTI;
    }
#endif

    ret = vmm_create_aspace_with_quota(&trusty_app->aspace, name,
                                       trusty_app->props.min_heap_size, flags);
    if (ret != NO_ERROR) {
        dprintf(CRITICAL, "Failed(%d) to allocate address space for %s\n", ret,
                name);
        goto err_aspace;
    }

    ret = alloc_address_map(trusty_app);
    if (ret != NO_ERROR) {
        dprintf(CRITICAL, "failed(%d) to load address map for %s\n", ret, name);
        goto err_map;
    }

    /* attach als_cnt */
    trusty_app->als = calloc(1, als_slot_cnt * sizeof(void*));
    if (!trusty_app->als) {
        dprintf(CRITICAL, "failed to allocate local storage for %s\n", name);
        ret = ERR_NO_MEMORY;
        /* alloc_address_map gets cleaned up by destroying the address space */
        goto err_alloc;
    }

    /* call all registered startup notifiers */
    list_for_every_entry(&app_notifier_list, n, struct trusty_app_notifier,
                         node) {
        if (!n->startup)
            continue;

        ret = n->startup(trusty_app);
        if (ret != NO_ERROR) {
            dprintf(CRITICAL, "failed(%d) to invoke startup notifier for %s\n",
                    ret, name);
            goto err_notifier;
        }
    }

    elf_hdr = (ELF_EHDR*)trusty_app->app_img.img_start;
    vaddr_t entry;
    __builtin_add_overflow(elf_hdr->e_entry, trusty_app->load_bias, &entry);
    trusty_thread = trusty_thread_create(
            name, entry, trusty_app->props.priority,
            trusty_app->props.min_stack_size,
            trusty_app->props.min_shadow_stack_size, trusty_app);

    if (!trusty_thread) {
        dprintf(CRITICAL, "failed to allocate trusty thread for %s\n", name);
        ret = ERR_NO_MEMORY;
        goto err_thread;
    }

    trusty_app->thread = trusty_thread;

    trusty_app->state = APP_RUNNING;
    ret = trusty_thread_start(trusty_app->thread);

    ASSERT(ret == NO_ERROR);

    return ret;

err_thread:
err_notifier:
    /* n points to failed notifier, or NULL if all were called successfully */
    if (n != NULL) {
        n = list_prev_type(&app_notifier_list, &n->node,
                           struct trusty_app_notifier, node);
    } else {
        n = list_peek_tail_type(&app_notifier_list, struct trusty_app_notifier,
                                node);
    }

    while (n != NULL) {
        if (!n->shutdown)
            continue;

        if (n->shutdown(trusty_app) != NO_ERROR)
            panic("failed to invoke shutdown notifier for %s\n", name);

        n = list_prev_type(&app_notifier_list, &n->node,
                           struct trusty_app_notifier, node);
    }

    free(trusty_app->als);
    trusty_app->als = NULL;
err_alloc:
err_map:
    vmm_free_aspace(trusty_app->aspace);
    trusty_app->aspace = NULL;
err_aspace:
    return ret;
}

static void __NO_RETURN trusty_app_exit_etc(int status,
                                            uint32_t crash_reason,
                                            bool report_crash) {
    status_t ret;
    struct trusty_app* app;
    struct trusty_app_notifier* notifier;
    lk_time_ns_t restart_timeout;

    app = current_trusty_app();

    DEBUG_ASSERT(app->state == APP_RUNNING);

    LTRACEF("exiting app %u, %s...\n", app->app_id, app->props.app_name);

    if (status) {
        TRACEF("%s: exited with exit code %d\n", app->aspace->name, status);
        if (!(app->props.mgmt_flags &
              APP_MANIFEST_MGMT_FLAGS_NON_CRITICAL_APP)) {
            panic("Unclean exit from critical app\n");
        }
        dump_backtrace();
        dprintf(ALWAYS, "%s\n", lk_version);
        restart_timeout = TRUSTY_APP_RESTART_TIMEOUT_FAILURE;
    } else {
        restart_timeout = TRUSTY_APP_RESTART_TIMEOUT_SUCCESS;
    }
    app->min_start_time = current_time_ns() + restart_timeout;

    list_for_every_entry(&app_notifier_list, notifier,
                         struct trusty_app_notifier, node) {
        if (!notifier->shutdown)
            continue;

        ret = notifier->shutdown(app);
        if (ret != NO_ERROR)
            panic("shutdown notifier failed(%d) for app %u, %s\n", ret,
                  app->app_id, app->props.app_name);
    }
    if (report_crash) {
        list_for_every_entry(&app_notifier_list, notifier,
                             struct trusty_app_notifier, node) {
            if (!notifier->crash) {
                continue;
            }

            ret = notifier->crash(app, crash_reason);
            if (ret != NO_ERROR) {
                panic("crash notifier failed(%d) for app %u, %s\n", ret,
                      app->app_id, app->props.app_name);
            }
        }
    }

    free(app->als);
    app->als = NULL;
    mutex_acquire(&apps_lock);
    app->state = APP_TERMINATING;
    mutex_release(&apps_lock);

    event_signal(&app_mgr_event, false);
    trusty_thread_exit(status);
}

void trusty_app_exit(int status) {
    /* Report exits with non-zero status as crashes */
    trusty_app_exit_etc(status, (uint32_t)status, !!status);
}

void trusty_app_crash(uint32_t reason) {
    trusty_app_exit_etc(1 /*EXIT_FAILURE*/, reason, true);
}

static status_t app_mgr_handle_starting(struct trusty_app* app) {
    status_t ret;

    DEBUG_ASSERT(is_mutex_held(&apps_lock));
    DEBUG_ASSERT(app->state == APP_STARTING);

    LTRACEF("starting app %u, %s\n", app->app_id, app->props.app_name);

    ret = trusty_app_start(app);

    if (ret != NO_ERROR) {
        /*
         * Drop the lock to call into ipc to kill waiting connections.
         * We put the app in the APP_FAILED_TO_START state so no new
         * connections are accepted and also to prevent it from being removed.
         */
        app->state = APP_FAILED_TO_START;

        mutex_release(&apps_lock);
        kill_waiting_connections(app);
        mutex_acquire(&apps_lock);

        DEBUG_ASSERT(app->state == APP_FAILED_TO_START);
    }
    return ret;
}

static status_t app_mgr_handle_terminating(struct trusty_app* app) {
    status_t ret;
    int retcode;
    bool restart_app;

    DEBUG_ASSERT(is_mutex_held(&apps_lock));
    DEBUG_ASSERT(app->state == APP_TERMINATING || app->state == APP_RESTARTING);

    LTRACEF("waiting for app %u, %s to exit\n", app->app_id,
            app->props.app_name);

    ret = thread_join(app->thread->thread, &retcode, INFINITE_TIME);
    ASSERT(ret == NO_ERROR);
    free(app->thread);
    app->thread = NULL;
    ret = vmm_free_aspace(app->aspace);
    app->aspace = NULL;

    /*
     * Panic if app exited with dma active. An unclean exit from a critical app
     * will already have panic'ed the kernel so this check will only detect when
     * critical apps exit cleanly with dma active and when non-critical apps
     * exit for any reason with dma active.
     */
    if (!list_is_empty(&app->props.dma_entry_list)) {
        mutex_release(&apps_lock);
        panic("%s: exited(%d) with dma active\n", app->props.app_name, retcode);
    }

    if (app->props.mgmt_flags & APP_MANIFEST_MGMT_FLAGS_RESTART_ON_EXIT) {
        restart_app = true;
    } else if (app->state == APP_TERMINATING) {
        /*
         * Drop the lock to call into ipc to check for connections. This is safe
         * since the app is in the APP_TERMINATING state so it cannot be
         * removed. We don't need to do this in APP_RESTARTING since that state
         * already marks that a connection is pending. If the app is marked
         * restart-on-exit, then we also go ahead with the restart.
         */
        mutex_release(&apps_lock);
        restart_app = has_waiting_connection(app);
        /*
         * We might get a new connection after has_waiting_connection returns
         * false. In that case, request_app_start_locked should change the state
         * to APP_RESTARTING
         */
        mutex_acquire(&apps_lock);
    } else {
        restart_app = false;
    }

    DEBUG_ASSERT(app->state == APP_TERMINATING || app->state == APP_RESTARTING);
    if (app->state == APP_RESTARTING) {
        restart_app = true;
    }

    if (restart_app) {
        app->state = APP_STARTING;
        event_signal(&app_mgr_event, false);
    } else {
        app->state = APP_NOT_RUNNING;
    }

    return ret;
}

static int app_mgr(void* arg) {
    status_t ret;
    struct trusty_app* app;

    while (true) {
        LTRACEF("app manager waiting for events\n");
        event_wait(&app_mgr_event);

        mutex_acquire(&apps_lock);

        list_for_every_entry(&trusty_app_list, app, struct trusty_app, node) {
            switch (app->state) {
            case APP_TERMINATING:
            case APP_RESTARTING:
                ret = app_mgr_handle_terminating(app);
                if (ret != NO_ERROR)
                    panic("failed(%d) to terminate app %u, %s\n", ret,
                          app->app_id, app->props.app_name);
                break;
            case APP_NOT_RUNNING:
                break;
            case APP_STARTING:
                ret = app_mgr_handle_starting(app);
                if (ret != NO_ERROR) {
                    if (!(app->props.mgmt_flags &
                          APP_MANIFEST_MGMT_FLAGS_NON_CRITICAL_APP)) {
                        panic("failed(%d) to start app %u, %s\n", ret,
                              app->app_id, app->props.app_name);
                    }
                    TRACEF("failed(%d) to start app %u, %s\n", ret, app->app_id,
                           app->props.app_name);
                }
                break;
            case APP_RUNNING:
                break;
            case APP_FAILED_TO_START:
                break;
            default:
                panic("unknown state %u for app %u, %s\n", app->state,
                      app->app_id, app->props.app_name);
            }
        }

        mutex_release(&apps_lock);
    }
}

static void app_mgr_init(void) {
    status_t err;
    thread_t* app_mgr_thread;

    LTRACEF("Creating app manager thread\n");
    app_mgr_thread = thread_create("app manager", &app_mgr, NULL,
                                   DEFAULT_PRIORITY, DEFAULT_STACK_SIZE);

    if (!app_mgr_thread)
        panic("Failed to create app manager thread\n");

    err = thread_resume(app_mgr_thread);
    if (err != NO_ERROR)
        panic("Failed to start app manager thread\n");
}

bool trusty_app_is_startup_port(const char* port_path) {
    struct manifest_port_entry* entry;

    mutex_acquire(&apps_lock);
    entry = find_manifest_port_entry_locked(port_path, NULL);
    mutex_release(&apps_lock);

    return entry != NULL;
}

status_t trusty_app_request_start_by_port(const char* port_path,
                                          const uuid_t* uuid) {
    struct manifest_port_entry* entry;
    struct trusty_app* owner = NULL;
    status_t ret;

    mutex_acquire(&apps_lock);

    entry = find_manifest_port_entry_locked(port_path, &owner);

    if (!owner || ipc_port_check_access(entry->flags, uuid) != NO_ERROR) {
        ret = ERR_NOT_FOUND;
    } else {
        ret = request_app_start_locked(owner);
    }

    mutex_release(&apps_lock);

    return ret;
}

/**
 * prel_to_abs_ptr() - Convert a position-relative value to an absolute.
 * @ptr: Pointer to a pointer-sized position-relative value.
 * @result: Pointer to the location for the result.
 *
 * Return: %true in case of success, %false for overflow.
 */
static inline bool prel_to_abs_ptr(const intptr_t* ptr, uintptr_t* result) {
    return !__builtin_add_overflow((uintptr_t)ptr, *ptr, result);
}

void trusty_app_init(void) {
    struct trusty_builtin_app_img* builtin_app_img;

    finalize_registration();

    app_mgr_init();

    for (builtin_app_img = __trusty_app_list_start;
         builtin_app_img != __trusty_app_list_end; builtin_app_img++) {
        struct trusty_app_img app_img;
        if (!prel_to_abs_ptr(&builtin_app_img->manifest_start,
                             &app_img.manifest_start) ||
            !prel_to_abs_ptr(&builtin_app_img->manifest_end,
                             &app_img.manifest_end) ||
            !prel_to_abs_ptr(&builtin_app_img->img_start, &app_img.img_start) ||
            !prel_to_abs_ptr(&builtin_app_img->img_end, &app_img.img_end)) {
            panic("Invalid builtin function entry\n");
        }

        if (trusty_app_create(&app_img, NULL, 0) != NO_ERROR)
            panic("Failed to create builtin apps\n");
    }
}

/* rather export trusty_app_list?  */
void trusty_app_forall(void (*fn)(struct trusty_app* ta, void* data),
                       void* data) {
    struct trusty_app* ta;

    if (fn == NULL)
        return;

    mutex_acquire(&apps_lock);
    list_for_every_entry(&trusty_app_list, ta, struct trusty_app, node)
            fn(ta, data);
    mutex_release(&apps_lock);
}

static void start_apps(uint level) {
    struct trusty_app* trusty_app;

    mutex_acquire(&apps_lock);
    list_for_every_entry(&trusty_app_list, trusty_app, struct trusty_app,
                         node) {
        if (trusty_app->props.mgmt_flags &
            APP_MANIFEST_MGMT_FLAGS_DEFERRED_START)
            continue;

        request_app_start_locked(trusty_app);
    }
    mutex_release(&apps_lock);
}

LK_INIT_HOOK(libtrusty_apps, start_apps, LK_INIT_LEVEL_APPS + 1);
