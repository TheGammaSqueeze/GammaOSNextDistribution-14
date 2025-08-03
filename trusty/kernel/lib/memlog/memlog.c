/*
 * Copyright (c) 2015 Google, Inc.
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

#include <arch/ops.h>
#include <debug.h>
#include <err.h>
#include <kernel/thread.h>
#include <kernel/vm.h>
#include <lib/io.h>
#include <lib/sm.h>
#include <lib/sm/sm_err.h>
#include <lib/sm/smcall.h>
#include <list.h>
#include <lk/init.h>
#include <platform.h>
#include <pow2.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "trusty-log.h"

#define LOG_LOCK_FLAGS SPIN_LOCK_FLAG_IRQ_FIQ

struct memlog {
    struct log_rb* rb;
    size_t rb_sz;

    ext_mem_client_id_t client_id;
    ext_mem_obj_id_t buf_id;
    size_t buf_sz;

    print_callback_t cb;
    struct list_node entry;
};

static spin_lock_t log_lock;
static struct list_node log_list = LIST_INITIAL_VALUE(log_list);

static struct memlog* memlog_get_by_id(ext_mem_client_id_t client_id,
                                       ext_mem_obj_id_t buf_id) {
    struct memlog* log;
    list_for_every_entry(&log_list, log, struct memlog, entry) {
        if (log->client_id == client_id && log->buf_id == buf_id) {
            return log;
        }
    }

    return NULL;
}

static uint32_t lower_pow2(uint32_t v) {
    return 1u << (31 - __builtin_clz(v));
}

static void __memlog_write(struct memlog* log, const char* str, size_t len) {
    size_t i;
    uint32_t log_offset;
    struct log_rb* rb = log->rb;

    log_offset = rb->alloc;

    __builtin_add_overflow(rb->alloc, len, &rb->alloc);

    /* Updates to alloc should be visible before the data is written. */
    wmb();

    for (i = 0; i < len; i++) {
        uint32_t offset;
        __builtin_add_overflow(log_offset, i, &offset);
        offset &= (log->rb_sz - 1);
        volatile char* ptr = &rb->data[offset];
        *ptr = str[i];
    }
}

static void memlog_write(struct memlog* log, const char* str, size_t len) {
    size_t i;
    const int chunk_size = 128;
    size_t rem;
    spin_lock_saved_state_t state;

    spin_lock_save(&log_lock, &state, LOG_LOCK_FLAGS);
    for (i = 0; i < len / chunk_size; i++) {
        __memlog_write(log, &str[i * chunk_size], chunk_size);
    }
    rem = len - i * chunk_size;
    if (rem)
        __memlog_write(log, &str[i * chunk_size], rem);
    spin_unlock_restore(&log_lock, state, LOG_LOCK_FLAGS);
}

/* Signal that the buffered data is ready to read. */
static void memlog_commit(struct memlog* log) {
    spin_lock_saved_state_t state;
    spin_lock_save(&log_lock, &state, LOG_LOCK_FLAGS);

    /*
     * Updates to the data should be visible before put is written.
     * Arguably the existing spinlock implementations should take care of the
     * ordering, but spinlocks for a non-SMP version of Trusty would not be
     * required to use barriers. This code needs a barrier, however, because it
     * is synchonizing with code that runs outside of Trusty, possibly on a
     * different processor. (Even if Trusty itself is non-SMP.)
     */
    wmb();

    log->rb->put = log->rb->alloc;

    spin_unlock_restore(&log_lock, state, LOG_LOCK_FLAGS);
}

static status_t map_rb(ext_mem_client_id_t client_id,
                       ext_mem_obj_id_t mem_obj_id,
                       size_t sz,
                       vaddr_t* va) {
    return ext_mem_map_obj_id(vmm_get_kernel_aspace(), "logmem", client_id,
                              mem_obj_id, 0, 0, sz, (void**)va, PAGE_SIZE_SHIFT,
                              0, ARCH_MMU_FLAG_PERM_NO_EXECUTE);
}

static ext_mem_obj_id_t args_get_id(struct smc32_args* args) {
    return (((uint64_t)args->params[1] << 32) | args->params[0]);
}

static size_t args_get_sz(struct smc32_args* args) {
    return (size_t)args->params[2];
}

void memlog_print_callback(print_callback_t* cb, const char* str, size_t len) {
    struct memlog* log = containerof(cb, struct memlog, cb);
    memlog_write(log, str, len);
}

void memlog_commit_callback(print_callback_t* cb) {
    struct memlog* log = containerof(cb, struct memlog, cb);
    memlog_commit(log);
}

static long memlog_add(ext_mem_client_id_t client_id,
                       ext_mem_obj_id_t buf_id,
                       size_t sz) {
    struct memlog* log;
    vaddr_t va;
    long status;
    status_t result;
    struct log_rb* rb;

    if (!IS_PAGE_ALIGNED(sz) || sz == 0) {
        return SM_ERR_INVALID_PARAMETERS;
    }

    log = malloc(sizeof(*log));
    if (!log) {
        return SM_ERR_INTERNAL_FAILURE;
    }
    memset(log, 0, sizeof(*log));
    log->client_id = client_id;
    log->buf_id = buf_id;
    log->buf_sz = sz;

    result = map_rb(client_id, buf_id, sz, &va);
    if (result != NO_ERROR) {
        status = SM_ERR_INTERNAL_FAILURE;
        goto error_failed_to_map;
    }
    rb = (struct log_rb*)va;
    log->rb = rb;
    log->rb_sz = lower_pow2(log->buf_sz - offsetof(struct log_rb, data));

    rb->sz = log->rb_sz;
    rb->alloc = 0;
    rb->put = 0;

    list_add_head(&log_list, &log->entry);

    log->cb.print = memlog_print_callback;
    log->cb.commit = memlog_commit_callback;
    register_print_callback(&log->cb);
    return 0;

error_failed_to_map:
    free(log);
    return status;
}

static long memlog_rm(ext_mem_client_id_t client_id, ext_mem_obj_id_t buf_id) {
    struct memlog* log;
    status_t result;

    log = memlog_get_by_id(client_id, buf_id);
    if (!log) {
        return SM_ERR_INVALID_PARAMETERS;
    }
    unregister_print_callback(&log->cb);
    list_delete(&log->entry);
    result = vmm_free_region(vmm_get_kernel_aspace(), (vaddr_t)log->rb);
    free(log);
    if (result != NO_ERROR) {
        return SM_ERR_INTERNAL_FAILURE;
    }
    return 0;
}

static long memlog_stdcall(struct smc32_args* args) {
    switch (args->smc_nr) {
    case SMC_SC_SHARED_LOG_VERSION:
        return TRUSTY_LOG_API_VERSION;
    case SMC_SC_SHARED_LOG_ADD:
        return memlog_add(args->client_id, args_get_id(args),
                          args_get_sz(args));
    case SMC_SC_SHARED_LOG_RM:
        return memlog_rm(args->client_id, args_get_id(args));
    default:
        return SM_ERR_UNDEFINED_SMC;
    }
    return 0;
}

static struct smc32_entity log_sm_entity = {
        .stdcall_handler = memlog_stdcall,
};

static void memlog_init(uint level) {
    int err;

    err = sm_register_entity(SMC_ENTITY_LOGGING, &log_sm_entity);
    if (err) {
        printf("trusty error register entity: %d\n", err);
    }
}
LK_INIT_HOOK(memlog, memlog_init, LK_INIT_LEVEL_APPS);
