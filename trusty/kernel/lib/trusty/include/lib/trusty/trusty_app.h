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

#ifndef __LIB_TRUSTY_APP_H
#define __LIB_TRUSTY_APP_H

#include <assert.h>
#include <kernel/physmem.h>
#include <kernel/thread.h>
#include <kernel/usercopy.h>
#include <kernel/vm.h>
#include <lib/trusty/uuid.h>
#include <list.h>
#include <stdbool.h>
#include <sys/types.h>

enum app_state {
    APP_NOT_RUNNING = 0,
    APP_STARTING,
    APP_RUNNING,
    APP_TERMINATING,
    APP_RESTARTING,
    APP_FAILED_TO_START,
};

/**
 * enum app_flags - Flags for Trusty applications
 * @APP_FLAGS_LOADABLE: This is a loadable application.
 * @APP_FLAGS_CREATION_MASK: Flags allowed during app creation.
 */
enum app_flags {
    APP_FLAGS_LOADABLE = (1 << 0),

    APP_FLAGS_CREATION_MASK = APP_FLAGS_LOADABLE,
};

struct manifest_port_entry {
    uint32_t flags;
    uint32_t path_len;
    /* Points to a string within an application's manifest blob */
    const char* path;
    struct list_node node;
};

struct manifest_mmio_entry {
    struct list_node node;
    struct phys_mem_obj phys_mem_obj;
    struct obj_ref phys_mem_obj_self_ref;
    uint32_t id;
};

struct trusty_app_props {
    uuid_t uuid;
    const char* app_name;
    uint32_t mgmt_flags;
    uint32_t min_stack_size;
    uint32_t min_heap_size;
    uint32_t min_shadow_stack_size;
    uint32_t map_io_mem_cnt;
    struct list_node port_entry_list;
    struct list_node mmio_entry_list;
    /* record paddrs mapped by prepare_dma until released by finish_dma */
    struct list_node dma_entry_list;
    int pinned_cpu;
    int priority;
    bool feature_bti;
};

struct trusty_app_img {
    uintptr_t manifest_start;
    uintptr_t manifest_end;
    uintptr_t img_start;
    uintptr_t img_end;
};

/**
 * struct trusty_app_mmio_allowed_range - Allowed MMIO range for loadable apps.
 * @node:  Internal list node, should be initialized to
 *         %LIST_INITIAL_CLEARED_VALUE.
 * @uuid:  The UUID of this application.
 * @start: Start address of this range.
 * @size:  Size of this range.
 */
struct trusty_app_mmio_allowed_range {
    struct list_node node;
    uuid_t uuid;
    paddr_t start;
    size_t size;
};

#define TRUSTY_APP_MMIO_ALLOWED_RANGE(_name, _uuid, _start, _size) \
    static struct trusty_app_mmio_allowed_range _name = {          \
            .node = LIST_INITIAL_CLEARED_VALUE,                    \
            .uuid = _uuid,                                         \
            .start = (_start),                                     \
            .size = (_size),                                       \
    };

/**
 * trusty_app_allow_mmio_range() - Add a new allowed MMIO range
 * @range: Pointer to the range to add.
 *
 * Adds a memory mapping to the internal list of allowed memory ranges.
 * The underlying structure will be added to a permanent linked list,
 * so it must not be a temporary value, e.g., on the stack.
 * The best option is to pass in a structure created with the
 * %TRUSTY_APP_MMIO_ALLOWED_RANGE macro.
 *
 * This function should be called before any loadable app is started,
 * e.g., at init level LK_INIT_LEVEL_APPS - 1.
 */
void trusty_app_allow_mmio_range(struct trusty_app_mmio_allowed_range* range);

struct trusty_app;

struct trusty_thread {
    vaddr_t stack_start;
    size_t stack_size;
#if USER_SCS_SUPPORTED
    vaddr_t shadow_stack_base;
    size_t shadow_stack_size;
#endif
    vaddr_t entry;
    thread_t* thread;
    struct trusty_app* app;
};

struct trusty_app {
    /* corresponds to the order in which the apps were started */
    u_int app_id;
    enum app_state state;
    uint32_t flags;
    lk_time_ns_t min_start_time;
    vmm_aspace_t* aspace;
    bool used_brk;
    vaddr_t start_brk;
    vaddr_t cur_brk;
    vaddr_t end_brk;
    vaddr_t load_bias;
    struct trusty_app_props props;
    struct trusty_app_img app_img;
    struct trusty_thread* thread;
    /* app local storage */
    void** als;
    struct list_node node;
};

void trusty_app_init(void);

/**
 * trusty_app_create_and_start() - Create and start a new Trusty application
 * @app_img: metadata of the application to load.
 * @flags: Creation flags, values from &enum app_flags.
 *
 * Return: ERR_NO_MEMORY if out of memory, ERR_NOT_VALID if
 * the application ELF or manifest are not valid.
 */
status_t trusty_app_create_and_start(struct trusty_app_img* app_img,
                                     uint32_t flags);

/**
 * trusty_app_is_startup_port() - Query if the specified port is a startup port
 * @port_path: path of the port to check.
 *
 * Return: true if @port_path has been registered as a startup port by an
 * application, false otherwise.
 */
bool trusty_app_is_startup_port(const char* port_path);

/**
 * trusty_app_request_start_by_port() - Request that the application (if any)
 * that has registered the given port be started.
 * @port_path: path of the registered port.
 * @uuid: uuid of the application triggering the request.
 *
 * If the application is already running then this function has no effect.
 * Otherwise the application will be started.
 *
 * Return: ERR_NOT_FOUND if no application has registered @port_path or if the
 * port is not accessible to @uuid, ERR_ALREADY_STARTED
 * if the application is already running, ERR_CANCELLED if the application
 * failed to start, or NO_ERROR otherwise.
 */
status_t trusty_app_request_start_by_port(const char* port_path,
                                          const uuid_t* uuid);

void trusty_app_exit(int status) __NO_RETURN;
void trusty_app_crash(uint32_t) __NO_RETURN;
status_t trusty_app_setup_mmio(struct trusty_app* trusty_app,
                               uint32_t mmio_id,
                               user_addr_t* uaddr_p,
                               uint32_t size);
void trusty_app_forall(void (*fn)(struct trusty_app* ta, void* data),
                       void* data);
void trusty_thread_exit(int status);

struct trusty_app_notifier {
    struct list_node node;
    status_t (*startup)(struct trusty_app* app);
    status_t (*shutdown)(struct trusty_app* app);
    status_t (*crash)(struct trusty_app* app, uint32_t reason);
};

/*
 * All app notifiers registration has to be complete before
 * libtrusty is initialized which is happening at LK_INIT_LEVEL_APPS-1
 * init level.
 */
status_t trusty_register_app_notifier(struct trusty_app_notifier* n);

/*
 * All als slots must be allocated before libtrusty is initialized
 * which is happening at LK_INIT_LEVEL_APPS-1 init level.
 */
int trusty_als_alloc_slot(void);

extern uint als_slot_cnt;

static inline void* trusty_als_get(struct trusty_app* app, int slot_id) {
    uint slot = slot_id - 1;
    ASSERT(slot < als_slot_cnt);
    return app->als[slot];
}

static inline void trusty_als_set(struct trusty_app* app,
                                  int slot_id,
                                  void* ptr) {
    uint slot = slot_id - 1;
    ASSERT(slot < als_slot_cnt);
    app->als[slot] = ptr;
}

static inline struct trusty_thread* trusty_thread_get(thread_t* t) {
    return (struct trusty_thread*)thread_tls_get(t, TLS_ENTRY_TRUSTY);
}

static inline struct trusty_thread* current_trusty_thread(void) {
    return (struct trusty_thread*)tls_get(TLS_ENTRY_TRUSTY);
}

static inline struct trusty_app* current_trusty_app(void) {
    struct trusty_thread* trusty_thread = current_trusty_thread();
    if (!trusty_thread) {
        return NULL;
    }
    return trusty_thread->app;
}

/**
 * trusty_app_allow_dma_range() - Mark dma range as allowed.
 * @app: app which should be allowed to use dma range
 * @obj: backing object for the dma range being allowed
 * @offset: offset into the backing object
 * @size: size of the dma range in bytes
 * @vaddr: virtual memory address. Used to tear down all mappings from a single
 *         call to prepare_dma
 * @flags: flags used to map dma range
 *
 * Return: ERR_INVALID_ARGS if range has already been mapped, ERR_NO_MEMORY if
 *         allocation of the bookkeeping structure failed, and NO_ERROR
 *         otherwise.
 */
status_t trusty_app_allow_dma_range(struct trusty_app* app,
                                    struct vmm_obj* obj,
                                    size_t offset,
                                    size_t size,
                                    vaddr_t vaddr,
                                    uint32_t flags);

/**
 * trusty_app_destroy_dma_range() - Remove dma range from allowlist and release
 * the underlying memory.
 * @vaddr: virtual address associated with the dma range
 *
 * Return: NO_ERROR if the ranges were removed and ERR_INVALID_ARGS otherwise
 */
status_t trusty_app_destroy_dma_range(vaddr_t vaddr, size_t size);

/**
 * trusty_app_dma_is_allowed() - Query if the specified physical address
 * was returned by prepare_dma and not released by finalize_dma.
 * @app: app whose dma mappings is being queried
 * @paddr: physical address to check
 *
 * Return: true if @paddr is valid and false otherwise.
 */
bool trusty_app_dma_is_allowed(const struct trusty_app* app, paddr_t paddr);

/**
 * trusty_uuid_dma_is_allowed() - Query if the specified physical address
 * was returned by prepare_dma and not released by finalize_dma.
 * @uuid: uuid of app whose dma mappings is being queried
 * @paddr: physical address to check
 *
 * Return: true if @paddr is valid and false otherwise.
 */
bool trusty_uuid_dma_is_allowed(const struct uuid* uuid, paddr_t paddr);

#endif
