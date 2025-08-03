/*
 * Copyright (c) 2013-2018, Google, Inc. All rights reserved
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

#define LOCAL_TRACE 0

#include <assert.h>
#include <debug.h>
#include <err.h>
#include <list.h>  // for containerof
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <trace.h>

#include <kernel/event.h>
#include <kernel/wait.h>

#if WITH_TRUSTY_IPC

#include <lib/syscall.h>
#include <lib/trusty/handle.h>

void handle_init_etc(struct handle* handle,
                     struct handle_ops* ops,
                     uint32_t flags) {
    DEBUG_ASSERT(handle);
    DEBUG_ASSERT(ops);
    DEBUG_ASSERT(ops->destroy);

    refcount_init(&handle->refcnt);
    handle->flags = flags;
    handle->ops = ops;
    handle->wait_event = NULL;
    spin_lock_init(&handle->slock);
    handle->cookie = NULL;
    list_clear_node(&handle->hlist_node);
    list_initialize(&handle->waiter_list);
}

static void __handle_destroy_ref(struct refcount* ref) {
    DEBUG_ASSERT(ref);

    struct handle* handle = containerof(ref, struct handle, refcnt);
    handle->ops->destroy(handle);
}

void handle_incref(struct handle* handle) {
    DEBUG_ASSERT(handle);
    refcount_inc(&handle->refcnt);
}

void handle_decref(struct handle* handle) {
    DEBUG_ASSERT(handle);
    refcount_dec(&handle->refcnt, __handle_destroy_ref);
}

void handle_close(struct handle* handle) {
    DEBUG_ASSERT(handle);
    if (handle->ops->shutdown)
        handle->ops->shutdown(handle);
    handle_decref(handle);
}

static int __do_wait(event_t* ev, lk_time_t timeout) {
    int ret;

    LTRACEF("waiting\n");
    ret = event_wait_timeout(ev, timeout);
    LTRACEF("waited\n");
    return ret;
}

static int _prepare_wait_handle(event_t* ev, struct handle* handle) {
    int ret = 0;
    spin_lock_saved_state_t state;

    spin_lock_save(&handle->slock, &state, SPIN_LOCK_FLAG_INTERRUPTS);
    if (unlikely(handle->wait_event)) {
        LTRACEF("someone is already waiting on handle %p?!\n", handle);
        ret = ERR_ALREADY_STARTED;
    } else {
        handle->wait_event = ev;
    }
    spin_unlock_restore(&handle->slock, state, SPIN_LOCK_FLAG_INTERRUPTS);
    return ret;
}

static void _finish_wait_handle(struct handle* handle) {
    spin_lock_saved_state_t state;

    /* clear out our event ptr */
    spin_lock_save(&handle->slock, &state, SPIN_LOCK_FLAG_INTERRUPTS);
    handle->wait_event = NULL;
    spin_unlock_restore(&handle->slock, state, SPIN_LOCK_FLAG_INTERRUPTS);
}

void handle_add_waiter(struct handle* h, struct handle_waiter* w) {
    spin_lock_saved_state_t state;

    spin_lock_save(&h->slock, &state, SPIN_LOCK_FLAG_INTERRUPTS);
    list_add_tail(&h->waiter_list, &w->node);
    spin_unlock_restore(&h->slock, state, SPIN_LOCK_FLAG_INTERRUPTS);
}

void handle_del_waiter(struct handle* h, struct handle_waiter* w) {
    spin_lock_saved_state_t state;

    spin_lock_save(&h->slock, &state, SPIN_LOCK_FLAG_INTERRUPTS);
    list_delete(&w->node);
    spin_unlock_restore(&h->slock, state, SPIN_LOCK_FLAG_INTERRUPTS);
}

int handle_wait(struct handle* handle,
                uint32_t* handle_event,
                lk_time_t timeout) {
    uint32_t event;
    int ret = 0;
    struct handle_event_waiter ew = HANDLE_EVENT_WAITER_INITIAL_VALUE(ew);

    if (!handle || !handle_event)
        return ERR_INVALID_ARGS;

    if (!handle->ops->poll)
        return ERR_NOT_SUPPORTED;

    handle_add_waiter(handle, &ew.waiter);

    while (true) {
        event = handle->ops->poll(handle, ~0U, true);
        if (event)
            break;
        ret = __do_wait(&ew.event, timeout);
        if (ret < 0)
            goto finish_wait;
    }

    *handle_event = event;
    ret = NO_ERROR;

finish_wait:
    handle_del_waiter(handle, &ew.waiter);
    event_destroy(&ew.event);
    return ret;
}

void handle_notify_waiters_locked(struct handle* handle) {
    struct handle_waiter* w;

    list_for_every_entry(&handle->waiter_list, w, struct handle_waiter, node) {
        w->notify_proc(w);
    }
    if (handle->wait_event) {
        LTRACEF("notifying handle %p wait_event %p\n", handle,
                handle->wait_event);
        event_signal(handle->wait_event, false);
    }
}

void handle_notify(struct handle* handle) {
    DEBUG_ASSERT(handle);

    spin_lock_saved_state_t state;
    spin_lock_save(&handle->slock, &state, SPIN_LOCK_FLAG_INTERRUPTS);
    handle_notify_waiters_locked(handle);
    spin_unlock_restore(&handle->slock, state, SPIN_LOCK_FLAG_INTERRUPTS);
}

void handle_list_init(struct handle_list* hlist) {
    DEBUG_ASSERT(hlist);

    *hlist = (struct handle_list)HANDLE_LIST_INITIAL_VALUE(*hlist);
}

void handle_list_add(struct handle_list* hlist, struct handle* handle) {
    DEBUG_ASSERT(hlist);
    DEBUG_ASSERT(handle);
    DEBUG_ASSERT(!list_in_list(&handle->hlist_node));

    handle_incref(handle);
    mutex_acquire(&hlist->lock);
    list_add_tail(&hlist->handles, &handle->hlist_node);
    if (hlist->wait_event) {
        /* somebody is waiting on list */
        _prepare_wait_handle(hlist->wait_event, handle);

        /* call poll to check if it is already signaled */
        uint32_t event = handle->ops->poll(handle, ~0U, false);
        if (event) {
            handle_notify(handle);
        }
    }
    mutex_release(&hlist->lock);
}

static void _handle_list_del_locked(struct handle_list* hlist,
                                    struct handle* handle) {
    DEBUG_ASSERT(hlist);
    DEBUG_ASSERT(handle);
    DEBUG_ASSERT(list_in_list(&handle->hlist_node));

    /* remove item from list */
    list_delete(&handle->hlist_node);

    /* check if somebody is waiting on this handle list */
    if (hlist->wait_event) {
        /* finish waiting */
        _finish_wait_handle(handle);
        if (list_is_empty(&hlist->handles)) {
            /* wakeup waiter if list is now empty */
            event_signal(hlist->wait_event, true);
        }
    }
    handle_decref(handle);
}

void handle_list_del(struct handle_list* hlist, struct handle* handle) {
    DEBUG_ASSERT(hlist);
    DEBUG_ASSERT(handle);

    mutex_acquire(&hlist->lock);
    _handle_list_del_locked(hlist, handle);
    mutex_release(&hlist->lock);
}

void handle_list_delete_all(struct handle_list* hlist) {
    DEBUG_ASSERT(hlist);

    mutex_acquire(&hlist->lock);
    while (!list_is_empty(&hlist->handles)) {
        struct handle* handle;

        handle =
                list_peek_head_type(&hlist->handles, struct handle, hlist_node);
        _handle_list_del_locked(hlist, handle);
    }
    mutex_release(&hlist->lock);
}

/*
 *  Iterate handle list and call finish_wait for each item until the last one
 *  (inclusive) specified by corresponding function parameter. If the last item
 *  is not specified, iterate the whole list.
 */
static void _hlist_finish_wait_locked(struct handle_list* hlist,
                                      struct handle* last) {
    struct handle* handle;
    list_for_every_entry(&hlist->handles, handle, struct handle, hlist_node) {
        _finish_wait_handle(handle);
        if (handle == last)
            break;
    }
}

/*
 *  Iterate handle list and call prepare wait (if required) and poll for each
 *  handle until the ready one is found and return it to caller.
 *  Undo prepare op if ready handle is found or en error occured.
 */
static int _hlist_do_poll_locked(struct handle_list* hlist,
                                 struct handle** handle_ptr,
                                 uint32_t* event_ptr,
                                 bool prepare) {
    int ret = 0;

    DEBUG_ASSERT(hlist->wait_event);

    if (list_is_empty(&hlist->handles))
        return ERR_NOT_FOUND; /* no handles in the list */

    struct handle* next;
    struct handle* last_prep = NULL;
    list_for_every_entry(&hlist->handles, next, struct handle, hlist_node) {
        if (prepare) {
            ret = _prepare_wait_handle(hlist->wait_event, next);
            if (ret)
                break;
            last_prep = next;
        }

        uint32_t event = next->ops->poll(next, ~0U, true);
        if (event) {
            *event_ptr = event;
            *handle_ptr = next;
            ret = 1;
            break;
        }
    }

    if (ret && prepare && last_prep) {
        /* need to undo prepare */
        _hlist_finish_wait_locked(hlist, last_prep);
    }
    return ret;
}

/* fills in the handle that has a pending event. The reference taken by the list
 * is not dropped until the caller has had a chance to process the handle.
 */
int handle_list_wait(struct handle_list* hlist,
                     struct handle** handle_ptr,
                     uint32_t* event_ptr,
                     lk_time_t timeout) {
    int ret;
    event_t ev;

    DEBUG_ASSERT(hlist);
    DEBUG_ASSERT(handle_ptr);
    DEBUG_ASSERT(event_ptr);

    event_init(&ev, false, EVENT_FLAG_AUTOUNSIGNAL);

    *event_ptr = 0;
    *handle_ptr = 0;

    mutex_acquire(&hlist->lock);

    DEBUG_ASSERT(hlist->wait_event == NULL);

    hlist->wait_event = &ev;
    ret = _hlist_do_poll_locked(hlist, handle_ptr, event_ptr, true);
    if (ret < 0)
        goto err_do_poll;

    if (ret == 0) {
        /* no handles ready */
        do {
            mutex_release(&hlist->lock);
            ret = __do_wait(&ev, timeout);
            mutex_acquire(&hlist->lock);

            if (ret < 0)
                break;

            /* poll again */
            ret = _hlist_do_poll_locked(hlist, handle_ptr, event_ptr, false);
        } while (!ret);

        _hlist_finish_wait_locked(hlist, NULL);
    }

    if (ret == 1) {
        struct handle* handle = *handle_ptr;

        handle_incref(handle);

        /* move list head after item we just found */
        list_delete(&hlist->handles);
        list_add_head(&handle->hlist_node, &hlist->handles);

        ret = NO_ERROR;
    }

err_do_poll:
    hlist->wait_event = NULL;
    mutex_release(&hlist->lock);
    event_destroy(&ev);
    return ret;
}

status_t handle_mmap(struct handle* handle,
                     size_t offset,
                     user_size_t size,
                     uint32_t mmap_prot,
                     user_addr_t* addr) {
    LTRACEF("mmap_prot 0x%x\n", mmap_prot);
    if (handle->ops->mmap) {
        return handle->ops->mmap(handle, offset, size, mmap_prot, addr);
    } else {
        return ERR_INVALID_ARGS;
    }
}

#endif /* WITH_TRUSTY_IPC */
