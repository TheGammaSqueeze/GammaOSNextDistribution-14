/*
 * Copyright (c) 2018, Google, Inc. All rights reserved
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

#include <assert.h>
#include <bits.h>
#include <debug.h>
#include <err.h>
#include <list.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <trace.h>

#include <kernel/event.h>
#include <kernel/mutex.h>
#include <kernel/wait.h>

#include <lib/syscall.h>
#include <lib/trusty/handle.h>
#include <lib/trusty/handle_set.h>
#include <lib/trusty/uctx.h>

#define LOCAL_TRACE 0

#ifdef SPIN_LOCK_FLAG_IRQ_FIQ
#define SLOCK_FLAGS SPIN_LOCK_FLAG_IRQ_FIQ
#else
#define SLOCK_FLAGS SPIN_LOCK_FLAG_INTERRUPTS
#endif

struct handle_set {
    struct mutex mlock;
    struct handle handle;
    struct list_node ref_list;
    struct list_node ready_list;
};

static uint32_t hset_poll(struct handle* handle, uint32_t emask, bool finalize);
static void hset_destroy(struct handle* handle);

static struct handle_ops hset_ops = {
        .poll = hset_poll,
        .destroy = hset_destroy,
};

static struct mutex g_hset_lock = MUTEX_INITIAL_VALUE(g_hset_lock);

static inline bool is_handle_set(struct handle* h) {
    ASSERT(h);
    return h->ops == &hset_ops;
}

static inline struct handle_set* handle_to_handle_set(struct handle* h) {
    ASSERT(is_handle_set(h));
    return containerof(h, struct handle_set, handle);
}

static uint32_t hset_poll(struct handle* h, uint32_t emask, bool finalize) {
    uint32_t event = 0;
    struct handle_set* hset = handle_to_handle_set(h);

    if (!list_is_empty(&hset->ready_list))
        event = IPC_HANDLE_POLL_READY;

    return event & emask;
}

static void hset_detach_ref_locked(struct handle_set* hset,
                                   struct handle_ref* ref) {
    spin_lock_saved_state_t state;

    DEBUG_ASSERT(ref->parent == &hset->handle);

    /* remove from waiter list */
    handle_del_waiter(ref->handle, &ref->waiter);

    /* remove from ready_list */
    spin_lock_save(&hset->handle.slock, &state, SLOCK_FLAGS);
    if (list_in_list(&ref->ready_node))
        list_delete(&ref->ready_node);
    spin_unlock_restore(&hset->handle.slock, state, SLOCK_FLAGS);

    /* remove from handle set list */
    list_delete(&ref->set_node);
    ref->parent = NULL;
    handle_decref(&hset->handle);
}

static void hset_destroy(struct handle* h) {
    struct handle_set* hset = handle_to_handle_set(h);

    LTRACEF("%p\n", h);

    free(hset);
}

static void hset_init(struct handle_set* hset) {
    mutex_init(&hset->mlock);
    list_initialize(&hset->ref_list);
    list_initialize(&hset->ready_list);
    handle_init_etc(&hset->handle, &hset_ops, HANDLE_FLAG_NO_SEND);
}

struct handle* handle_set_create(void) {
    struct handle_set* hset;

    hset = malloc(sizeof(*hset));
    if (!hset)
        return NULL;

    hset_init(hset);

    LTRACEF("%p\n", &hset->handle);

    return &hset->handle;
}

static void hset_waiter_notify(struct handle_waiter* w) {
    ASSERT(w);
    struct handle_ref* ref = containerof(w, struct handle_ref, waiter);

    ASSERT(ref->parent);
    spin_lock(&ref->parent->slock);
    if (!list_in_list(&ref->ready_node)) {
        struct handle_set* hset = handle_to_handle_set(ref->parent);
        list_add_tail(&hset->ready_list, &ref->ready_node);
    }
    handle_notify_waiters_locked(ref->parent);
    spin_unlock(&ref->parent->slock);
}

static int hset_attach_ref(struct handle_set* hset, struct handle_ref* ref) {
    DEBUG_ASSERT(ref->parent == NULL);
    DEBUG_ASSERT(!list_in_list(&ref->set_node));
    DEBUG_ASSERT(!list_in_list(&ref->ready_node));
    DEBUG_ASSERT(!list_in_list(&ref->waiter.node));

    LTRACEF("%p: %p\n", &hset->handle, ref->handle);

    mutex_acquire(&hset->mlock);
    handle_incref(&hset->handle);
    ref->parent = &hset->handle;
    ref->waiter.notify_proc = hset_waiter_notify;
    list_add_tail(&hset->ref_list, &ref->set_node);
    handle_add_waiter(ref->handle, &ref->waiter);
    mutex_release(&hset->mlock);

    if (ref->handle->ops->poll(ref->handle, ~0U, false)) {
        /*
         * TODO: this could be optimized a bit:
         * instead of waking up all clients of this handle
         * we can only wakeup a path that we are attaching to.
         */
        handle_notify(ref->handle);
    }

    return NO_ERROR;
}

static bool hset_find_target(struct handle_set* hset,
                             struct handle_set* target) {
    struct handle_set* child_hset;
    struct handle_ref* ref;

    if (hset == target)
        return true;

    mutex_acquire(&hset->mlock);
    list_for_every_entry(&hset->ref_list, ref, struct handle_ref, set_node) {
        if (!ref->handle)
            continue;

        if (!is_handle_set(ref->handle))
            continue;

        child_hset = handle_to_handle_set(ref->handle);
        if (hset_find_target(child_hset, target))
            goto found;
    }
    mutex_release(&hset->mlock);
    return false;

found:
    mutex_release(&hset->mlock);
    return true;
}

static int hset_attach_hset(struct handle_set* hset, struct handle_ref* ref) {
    ASSERT(ref);
    struct handle_set* new_hset = handle_to_handle_set(ref->handle);

    /* check if it would create a circular references */
    if (hset_find_target(new_hset, hset)) {
        LTRACEF("Would create circular refs\n");
        return ERR_INVALID_ARGS;
    }

    return hset_attach_ref(hset, ref);
}

int handle_set_attach(struct handle* h, struct handle_ref* ref) {
    int ret;
    struct handle_set* hset;

    ASSERT(ref);
    ASSERT(ref->handle);

    hset = handle_to_handle_set(h);
    if (is_handle_set(ref->handle)) {
        mutex_acquire(&g_hset_lock);
        ret = hset_attach_hset(hset, ref);
        mutex_release(&g_hset_lock);
    } else {
        ret = hset_attach_ref(hset, ref);
    }

    return ret;
}

void handle_set_detach_ref(struct handle_ref* ref) {
    ASSERT(ref);

    if (ref->parent) {
        struct handle_set* hset = handle_to_handle_set(ref->parent);
        handle_incref(&hset->handle);
        mutex_acquire(&hset->mlock);
        hset_detach_ref_locked(hset, ref);
        mutex_release(&hset->mlock);
        handle_decref(&hset->handle);
    }
}

void handle_set_update_ref(struct handle_ref* ref,
                           uint32_t emask,
                           void* cookie) {
    ASSERT(ref);

    if (ref->parent) {
        struct handle_set* hset = handle_to_handle_set(ref->parent);
        mutex_acquire(&hset->mlock);
        ref->emask = emask;
        ref->cookie = cookie;
        mutex_release(&hset->mlock);
        handle_notify(ref->handle);
    }
}

static int _hset_do_poll(struct handle_set* hset, struct handle_ref* out) {
    int ret = 0;
    uint32_t event;
    struct handle_ref* ref;
    spin_lock_saved_state_t state;

    mutex_acquire(&hset->mlock);

    if (list_is_empty(&hset->ref_list)) {
        ret = ERR_NOT_FOUND;
        goto err_empty;
    }

    for (;;) {
        spin_lock_save(&hset->handle.slock, &state, SLOCK_FLAGS);
        ref = list_remove_head_type(&hset->ready_list, struct handle_ref,
                                    ready_node);
        spin_unlock_restore(&hset->handle.slock, state, SLOCK_FLAGS);

        if (!ref)
            break;

        event = ref->handle->ops->poll(ref->handle, ref->emask, true);
        if (event) {
            handle_incref(ref->handle);
            out->handle = ref->handle;
            out->id = ref->id;
            out->cookie = ref->cookie;
            out->emask = event;

            /* move it to the end of the queue */
            spin_lock_save(&hset->handle.slock, &state, SLOCK_FLAGS);
            if (!list_in_list(&ref->ready_node))
                list_add_tail(&hset->ready_list, &ref->ready_node);
            spin_unlock_restore(&hset->handle.slock, state, SLOCK_FLAGS);
            ret = 1;
            break;
        }
    }

err_empty:
    mutex_release(&hset->mlock);

    return ret;
}

static int hset_wait(struct handle_set* hset,
                     struct handle_ref* out,
                     lk_time_t timeout) {
    int ret;
    struct handle_event_waiter ew = HANDLE_EVENT_WAITER_INITIAL_VALUE(ew);

    DEBUG_ASSERT(hset);
    DEBUG_ASSERT(out);

    handle_add_waiter(&hset->handle, &ew.waiter);

    do {
        /* poll */
        ret = _hset_do_poll(hset, out);
        if (!ret) {
            /*
             * wait for event if ret is zero,
             * otherwise it is an error or valid event
             */
            ret = event_wait_timeout(&ew.event, timeout);
        }
    } while (!ret);

    if (ret > 0)
        ret = 0;

    handle_del_waiter(&hset->handle, &ew.waiter);
    event_destroy(&ew.event);
    return ret;
}

int handle_set_wait(struct handle* h,
                    struct handle_ref* out,
                    lk_time_t timeout) {
    struct handle_set* hset = handle_to_handle_set(h);
    return hset_wait(hset, out, timeout);
}

bool handle_set_ready(struct handle* h) {
    bool ret;
    spin_lock_saved_state_t state;

    struct handle_set* hset = handle_to_handle_set(h);

    spin_lock_save(&hset->handle.slock, &state, SLOCK_FLAGS);
    ret = !list_is_empty(&hset->ready_list);
    spin_unlock_restore(&hset->handle.slock, state, SLOCK_FLAGS);

    return ret;
}

int handle_ref_wait(const struct handle_ref* in,
                    struct handle_ref* out,
                    lk_time_t timeout) {
    int ret = 0;

    if (!in || !in->handle || !out)
        return ERR_INVALID_ARGS;

    if (is_handle_set(in->handle)) {
        ret = handle_set_wait(in->handle, out, timeout);
    } else {
        uint32_t event;
        ret = handle_wait(in->handle, &event, timeout);
        if (ret == NO_ERROR) {
            handle_incref(in->handle);
            out->handle = in->handle;
            out->cookie = in->cookie;
            out->id = in->id;
            out->emask = event;
        }
    }
    return ret;
}
