/*
 * Copyright (c) 2019, Google, Inc. All rights reserved
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
#include <err.h>
#include <kernel/mutex.h>
#include <kernel/usercopy.h>
#include <lib/binary_search_tree.h>
#include <lib/syscall.h>
#include <list.h>
#include <platform/interrupts.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <trace.h>

#define LOCAL_TRACE 0

#include <lib/trusty/event.h>
#include <lib/trusty/handle.h>
#include <lib/trusty/trusty_app.h>
#include <lib/trusty/uctx.h>
#include <lib/trusty/uio.h>

/**
 * enum event_state - event states
 * @EVENT_STATE_UNSIGNALED:
 *    state is an initial state of any event object. An event object leaves
 *    @EVENT_STATE_UNSIGNALED state when it becomes signaled. An event object
 *    might return to @EVENT_STATE_UNSIGNALED state under certain conditions
 *    (see below).
 * @EVENT_STATE_SIGNALED:
 *    state is entered by event source object when event_source_signal() routine
 *    is invoked. The @EVENT_STATE_SIGNALED state is entered by event client
 *    object from @EVENT_STATE_UNSIGNALED state in when event_source_signal()
 *    routine if invoke on event source or from @EVENT_STATE_NOTIFIED_SIGNALED
 *    state when client acknowledges that previously delivered event has been
 *    handled. Waiting on event client object in @EVENT_STATE_SIGNALED state
 *    generates the %IPC_HANDLE_POLL_MSG event for its waiters.
 * @EVENT_STATE_NOTIFIED:
 *    is entered by an event client object from @EVENT_STATE_SIGNALED state
 *    after %IPC_HANDLE_POLL_MSG event has been delivered to the client. In
 *    this state, the event client object stops generating %IPC_HANDLE_POLL_MSG
 *    event for its waiters. The client should handle received event and
 *    acknowledge that by invoking event_client_notify_handled() call on client
 *    event object. Upon receiving such acknowledgment the client event object
 *    is transitioning back to @EVENT_STATE_UNSIGNALED state.
 * @EVENT_STATE_NOTIFIED_SIGNALED:
 *    is entered by an event client object from @EVENT_STATE_NOTIFIED state
 *    when another signal is received which is possible for events sources that
 *    support edge triggering semantic. Receiving acknowledgment for event
 *    client in this state transition event object into @EVENT_STATE_SIGNALED
 *    state which generates new %IPC_HANDLE_POLL_MSG event for its waiters.
 * @EVENT_STATE_HANDLED:
 *    is entered by an event source object from @EVENT_STATE_SIGNALED state
 *    when all registered clients has finished handling an event and
 *    acknowledged that by invoking event_client_notify_handled() routine.
 *    Waiting on event source object when it is in @EVENT_STATE_HANDLED state
 *    generates %IPC_HANDLE_POLL_MSG event for its waiters and transition
 *    object back to @EVENT_STATE_UNSIGNALED state.
 * @EVENT_STATE_CLOSED:
 *    is entered when the last reference to event source object handle goes
 *    away. This state is applicable for both event source and event client
 *    objects. In this state, the %IPC_HANDLE_POLL_HUP event is triggered to
 *    handle waiters.
 */
enum event_state {
    EVENT_STATE_UNSIGNALED = 0,
    EVENT_STATE_SIGNALED,
    EVENT_STATE_NOTIFIED,
    EVENT_STATE_NOTIFIED_SIGNALED,
    EVENT_STATE_HANDLED,
    EVENT_STATE_CLOSED,
};

/**
 * struct event_source - represents event source object
 * @name:        event name
 * @ops:         pointed to @struct event_source_ops
 * @ops_arg:     pointer passes as &priv parameters of all ops callbacks
 * @uuids:       pointer to array of &struct uuid items that are allowed to open
 *               this event source object
 * @uuids_num:   number of items in @uuids array
 * @refobj:      ref object
 * @handle_ref:  self reference from @handle
 * @handle:      embedded @struct handle
 * @tree_node:   tracking @struct bst node
 * @client_list: list of attached clients
 * @client_cnt:  number of attached clients
 * @slock:       spinlock protecting internal state
 * @ack_cnt:     required ack count
 * @state:       event source state
 *
 * Note: the event object internal state and state transitions are protected by
 * two locks: the global mutex (&es_lock) and a spin lock (@slock) private to
 * event source object. The global mutex is held to protect operations related
 * to global event object list (insert, remove and lookup) and ref object.
 * In addition, it is  held to synchronize invocation of &open and &close
 * callbacks which is happening in context of creating and destroying event
 * objects. All other state transitions are protected by the spin lock.
 */
struct event_source {
    const char* name;
    const struct event_source_ops* ops;
    const void* ops_arg;
    const uuid_t* uuids;
    unsigned int uuids_num;

    struct obj refobj;

    /* handle_ref is a self reference when there are
     * outstanding handles out there. It is removed
     * when last handle ref goes away.
     */
    struct obj_ref handle_ref;
    struct handle handle;

    struct bst_node tree_node;
    struct list_node client_list;
    unsigned int client_cnt;

    spin_lock_t slock;

    unsigned int ack_cnt;
    volatile int state;
};

struct event_client {
    struct handle handle;
    struct list_node node;
    struct event_source* es;
    struct obj_ref es_ref;
    volatile int state;
};

#define SLOCK_FLAGS SPIN_LOCK_FLAG_INTERRUPTS

static uint32_t event_source_poll(struct handle* handle,
                                  uint32_t emask,
                                  bool finalize);
static void event_source_destroy(struct handle* handle);

static uint32_t event_client_poll(struct handle* handle,
                                  uint32_t emask,
                                  bool finalize);
static void event_client_destroy(struct handle* handle);

static ssize_t event_client_user_readv(struct handle* h,
                                       user_addr_t iov_uaddr,
                                       uint32_t iov_cnt);
static ssize_t event_client_user_writev(struct handle* handle,
                                        user_addr_t iov_uaddr,
                                        uint32_t iov_cnt);

static mutex_t es_lock = MUTEX_INITIAL_VALUE(es_lock);
static struct bst_root es_tree_root = BST_ROOT_INITIAL_VALUE;

static struct handle_ops event_source_handle_ops = {
        .poll = event_source_poll,
        .destroy = event_source_destroy,
};

static struct handle_ops event_client_handle_ops = {
        .poll = event_client_poll,
        .destroy = event_client_destroy,
        .user_readv = event_client_user_readv,
        .user_writev = event_client_user_writev,
};

/******************************************************************************/

static struct event_source* handle_to_event_source(struct handle* h) {
    ASSERT(h);
    ASSERT(h->ops == &event_source_handle_ops);
    return containerof(h, struct event_source, handle);
}

static int event_source_bst_compare(struct bst_node* a, struct bst_node* b) {
    struct event_source* es_a = containerof(a, struct event_source, tree_node);
    struct event_source* es_b = containerof(b, struct event_source, tree_node);
    return strcmp(es_a->name, es_b->name);
}

static uint32_t event_source_poll(struct handle* h,
                                  uint32_t emask,
                                  bool finalize) {
    int oldstate;
    spin_lock_saved_state_t state;

    struct event_source* es = handle_to_event_source(h);

    spin_lock_save(&es->slock, &state, SLOCK_FLAGS);
    oldstate = es->state;
    if (finalize && (oldstate == EVENT_STATE_HANDLED)) {
        es->state = EVENT_STATE_UNSIGNALED;
    }
    spin_unlock_restore(&es->slock, state, SLOCK_FLAGS);

    if (oldstate == EVENT_STATE_HANDLED) {
        return IPC_HANDLE_POLL_MSG;
    }

    return 0;
}

static void event_source_obj_destroy(struct obj* obj) {
    struct event_source* es = containerof(obj, struct event_source, refobj);
    free(es);
}

static void event_source_destroy(struct handle* h) {
    struct event_client* ec;
    struct event_source* es;
    spin_lock_saved_state_t state;

    /* called when the last reference to handle goes away */

    es = handle_to_event_source(h);

    mutex_acquire(&es_lock);

    /* if event source in global list : remove it */
    if (es->tree_node.rank) {
        bst_delete(&es_tree_root, &es->tree_node);

        /* notify observers that event source is closed */
        if (es->ops)
            es->ops->close(es->ops_arg);
    }

    /* mark all clients still connected as closed */
    spin_lock_save(&es->slock, &state, SLOCK_FLAGS);
    es->state = EVENT_STATE_CLOSED;
    list_for_every_entry(&es->client_list, ec, struct event_client, node) {
        ec->state = EVENT_STATE_CLOSED;
        handle_notify(&ec->handle);
    }
    spin_unlock_restore(&es->slock, state, SLOCK_FLAGS);

    /* clear pointers that should not be accessed past this point */
    es->ops = NULL;
    es->ops_arg = NULL;
    es->uuids = NULL;
    es->name = NULL;

    /* remove self reference */
    obj_del_ref(&es->refobj, &es->handle_ref, event_source_obj_destroy);
    mutex_release(&es_lock);
}

static struct event_source* event_source_lookup_locked(const char* name,
                                                       const uuid_t* uuid,
                                                       struct obj_ref* ref) {
    struct bst_node* tn;
    struct event_source* es;
    struct event_source unused;

    /* only init .name */
    unused.name = name;

    DEBUG_ASSERT(is_mutex_held(&es_lock));

    tn = bst_search(&es_tree_root, &unused.tree_node, event_source_bst_compare);
    if (!tn) {
        /* Object not found */
        return NULL;
    }

    /* Object found: check if we are allowed to connect */
    es = containerof(tn, struct event_source, tree_node);

    if (!es->uuids_num) {
        /* No uuids are configured: allow anybody */
        obj_add_ref(&es->refobj, ref);
        return es;
    }

    /* check client */
    for (uint32_t i = 0; i < es->uuids_num; i++) {
        if (memcmp(uuid, &es->uuids[i], sizeof(*uuid)) == 0) {
            obj_add_ref(&es->refobj, ref);
            return es;
        }
    }

    return NULL;
}

static void event_source_attach_client_locked(struct event_source* es,
                                              struct event_client* ec) {
    spin_lock_saved_state_t state;

    DEBUG_ASSERT(is_mutex_held(&es_lock));
    DEBUG_ASSERT(!spin_lock_held(&es->slock));

    spin_lock_save(&es->slock, &state, SLOCK_FLAGS);

    /* add ref to es and attach client to tracking list */
    ec->es = es;
    obj_add_ref(&es->refobj, &ec->es_ref);
    list_add_tail(&es->client_list, &ec->node);

    /* client starts in EVENT_STATE_UNSIGNALED state */
    ec->state = EVENT_STATE_UNSIGNALED;

    es->client_cnt++;

    spin_unlock_restore(&es->slock, state, SLOCK_FLAGS);

    if (es->client_cnt == 1) {
        /* if first client (invokes open) */
        if (es->ops && es->ops->open) {
            es->ops->open(es->ops_arg);
        }
    }
}

static void event_source_notify_done_slocked(struct event_source* es) {
    DEBUG_ASSERT(spin_lock_held(&es->slock));

    ASSERT(es->ack_cnt > 0);

    /* decrement ack count of event source */
    if (--es->ack_cnt == 0) {
        /* All clients notified */
        es->state = EVENT_STATE_HANDLED;
        handle_notify(&es->handle);

        if (es->ops && es->ops->unmask) {
            es->ops->unmask(es->ops_arg);
        }
    }
}

int event_source_signal(struct handle* h) {
    struct event_client* ec;
    struct event_source* es;
    spin_lock_saved_state_t state;

    es = handle_to_event_source(h);

    spin_lock_save(&es->slock, &state, SLOCK_FLAGS);

    if (es->ops && es->ops->mask) {
        /*
         * If we have mask method we are in "level triggered" mode. It is
         * expected that event should be signaled only if the event source is
         * in EVENT_STATE_UNSIGNALED or EVENT_STATE_HANDLED state.
         */
        ASSERT(es->state == EVENT_STATE_UNSIGNALED ||
               es->state == EVENT_STATE_HANDLED);

        /* mask source */
        es->ops->mask(es->ops_arg);
    }

    if (es->client_cnt) {
        /* we have clients */
        es->ack_cnt = es->client_cnt;
        es->state = EVENT_STATE_SIGNALED;
        list_for_every_entry(&es->client_list, ec, struct event_client, node) {
            if (ec->state == EVENT_STATE_UNSIGNALED) {
                /* enter signaled state and pet handle */
                ec->state = EVENT_STATE_SIGNALED;
                handle_notify(&ec->handle);
            } else if (ec->state == EVENT_STATE_NOTIFIED) {
                /* enter signaled notify state */
                ec->state = EVENT_STATE_NOTIFIED_SIGNALED;
            }
        }
    } else {
        /* no clients: mark source as handled and notify source handle */
        es->state = EVENT_STATE_HANDLED;
        handle_notify(&es->handle);
    }

    spin_unlock_restore(&es->slock, state, SLOCK_FLAGS);

    return NO_ERROR;
}

int event_source_publish(struct handle* h) {
    bool inserted;
    struct event_source* es = handle_to_event_source(h);

    mutex_acquire(&es_lock);
    inserted =
            bst_insert(&es_tree_root, &es->tree_node, event_source_bst_compare);
    mutex_release(&es_lock);

    return inserted ? NO_ERROR : ERR_ALREADY_EXISTS;
}

int event_source_create(const char* name,
                        const struct event_source_ops* ops,
                        const void* ops_arg,
                        const struct uuid* uuids,
                        unsigned int uuids_num,
                        unsigned int flags,
                        struct handle** ph) {
    struct event_source* es;

    if (!name || *name == 0)
        return ERR_INVALID_ARGS;

    es = calloc(1, sizeof(*es));
    if (!es) {
        return ERR_NO_MEMORY;
    }

    es->name = name;

    if (ops) {
        ASSERT(ops->open);
        ASSERT(ops->close);

        /* mask and unmask must be set together */
        ASSERT(!ops->mask == !ops->unmask);
    }

    es->ops = ops;
    es->ops_arg = ops_arg;
    es->uuids = uuids;
    es->uuids_num = uuids_num;

    spin_lock_init(&es->slock);
    list_initialize(&es->client_list);
    bst_node_initialize(&es->tree_node);
    obj_init(&es->refobj, &es->handle_ref);
    handle_init(&es->handle, &event_source_handle_ops);

    *ph = &es->handle;
    return NO_ERROR;
}

int event_source_open(const uuid_t* cid,
                      const char* name,
                      size_t max_name,
                      uint flags,
                      struct handle** ph) {
    int ret;
    struct event_source* es;
    struct event_client* ec = NULL;
    struct obj_ref es_tmp_ref = OBJ_REF_INITIAL_VALUE(es_tmp_ref);

    if (!name) {
        return ERR_INVALID_ARGS;
    }

    size_t len = strnlen(name, max_name);
    if (len == 0 || len >= max_name) {
        /* empty or unterminated string */
        LTRACEF("invalid path specified\n");
        return ERR_INVALID_ARGS;
    }
    /* After this point name is zero terminated */

    mutex_acquire(&es_lock);

    /* lookup event source */
    es = event_source_lookup_locked(name, cid, &es_tmp_ref);
    if (!es) {
        ret = ERR_NOT_FOUND;
        goto err_not_found;
    }

    /* allocate handle and tracking structure */
    ec = calloc(1, sizeof(*ec));
    if (!ec) {
        ret = ERR_NO_MEMORY;
        goto err_alloc;
    }

    obj_ref_init(&ec->es_ref);
    handle_init(&ec->handle, &event_client_handle_ops);

    /* attach it to event source */
    event_source_attach_client_locked(es, ec);

    /* Looks OK */
    handle_incref(&ec->handle);
    *ph = &ec->handle;
    ret = NO_ERROR;

err_attach:
err_alloc:
    obj_del_ref(&es->refobj, &es_tmp_ref, event_source_obj_destroy);
err_not_found:
    mutex_release(&es_lock);

    if (ec) {
        handle_decref(&ec->handle);
    }
    return ret;
}

/******************************************************************************/

static bool handle_is_client(struct handle* handle) {
    ASSERT(handle);
    return likely(handle->ops == &event_client_handle_ops);
}

static uint32_t event_client_poll(struct handle* h,
                                  uint32_t emask,
                                  bool finalize) {
    int oldstate;
    spin_lock_saved_state_t state;

    ASSERT(handle_is_client(h));

    struct event_client* ec = containerof(h, struct event_client, handle);

    spin_lock_save(&ec->es->slock, &state, SLOCK_FLAGS);
    oldstate = ec->state;
    if (finalize && (oldstate == EVENT_STATE_SIGNALED)) {
        ec->state = EVENT_STATE_NOTIFIED;
    }
    spin_unlock_restore(&ec->es->slock, state, SLOCK_FLAGS);

    if (oldstate == EVENT_STATE_CLOSED) {
        return IPC_HANDLE_POLL_HUP;
    }

    if (oldstate == EVENT_STATE_SIGNALED) {
        return IPC_HANDLE_POLL_MSG;
    }

    return 0;
}

static void event_client_notify_done_slocked(struct event_client* ec) {
    struct event_source* es = ec->es;

    /* event source spinlock must be held. Global es_lock is not required */
    DEBUG_ASSERT(spin_lock_held(&es->slock));

    if (ec->state == EVENT_STATE_NOTIFIED_SIGNALED) {
        /* back to signaled state and pet handle */
        ec->state = EVENT_STATE_SIGNALED;
        handle_notify(&ec->handle);
    } else if (ec->state == EVENT_STATE_NOTIFIED) {
        /* back to unsignaled state and update source */
        ec->state = EVENT_STATE_UNSIGNALED;
        event_source_notify_done_slocked(es);
    }
}

static void event_client_destroy(struct handle* h) {
    int oldstate;
    struct event_client* ec;
    struct event_source* es;
    spin_lock_saved_state_t state;

    ASSERT(handle_is_client(h));

    ec = containerof(h, struct event_client, handle);

    mutex_acquire(&es_lock);

    es = ec->es;
    ASSERT(es);

    /* detach client */
    spin_lock_save(&es->slock, &state, SLOCK_FLAGS);

    oldstate = ec->state;
    if (oldstate != EVENT_STATE_CLOSED) {
        /* if source is not closed */
        if (oldstate == EVENT_STATE_SIGNALED ||
            oldstate == EVENT_STATE_NOTIFIED ||
            oldstate == EVENT_STATE_NOTIFIED_SIGNALED) {
            /* then invoke notify done */
            event_source_notify_done_slocked(es);
        }
        ec->state = EVENT_STATE_CLOSED;
    }

    ASSERT(list_in_list(&ec->node));
    list_delete(&ec->node);
    es->client_cnt--;

    spin_unlock_restore(&es->slock, state, SLOCK_FLAGS);

    if (oldstate != EVENT_STATE_CLOSED) {
        if (es->client_cnt == 0) {
            /* last client: invoke close */
            if (es->ops && es->ops->close) {
                es->ops->close(es->ops_arg);
            }
        }
    }

    /* Remove reference to source object */
    obj_del_ref(&es->refobj, &ec->es_ref, event_source_obj_destroy);

    /* free client */
    free(ec);

    mutex_release(&es_lock);
}

int event_client_notify_handled(struct handle* h) {
    int ret = NO_ERROR;
    struct event_client* ec;
    struct event_source* es;
    spin_lock_saved_state_t state;

    if (!handle_is_client(h)) {
        return ERR_INVALID_ARGS;
    }

    ec = containerof(h, struct event_client, handle);
    es = ec->es;

    ASSERT(es);

    spin_lock_save(&es->slock, &state, SLOCK_FLAGS);
    switch (ec->state) {
    case EVENT_STATE_NOTIFIED:
    case EVENT_STATE_NOTIFIED_SIGNALED:
        event_client_notify_done_slocked(ec);
        break;

    case EVENT_STATE_CLOSED:
        ret = ERR_CHANNEL_CLOSED;
        break;

    default:
        ret = ERR_BAD_STATE;
    }
    spin_unlock_restore(&es->slock, state, SLOCK_FLAGS);

    return ret;
}

static ssize_t event_client_user_writev(struct handle* h,
                                        user_addr_t iov_uaddr,
                                        uint32_t iov_cnt) {
    int ret;
    ssize_t len;
    uint32_t cmd;

    DEBUG_ASSERT(h);

    if (iov_cnt != 1) {
        /* we expect exactly one iov here */
        return ERR_INVALID_ARGS;
    }

    len = user_iovec_to_membuf((uint8_t*)&cmd, sizeof(cmd), iov_uaddr, iov_cnt);
    if (len < 0) {
        /* most likely FAULT */
        return (int32_t)len;
    }

    if (len != sizeof(cmd)) {
        /* partial write */
        return ERR_INVALID_ARGS;
    }

    switch (cmd) {
    case EVENT_NOTIFY_CMD_HANDLED:
        ret = event_client_notify_handled(h);
        break;

    default:
        ret = ERR_INVALID_ARGS;
    }

    return ret;
}

static ssize_t event_client_user_readv(struct handle* h,
                                       user_addr_t iov_uaddr,
                                       uint32_t iov_cnt) {
    return 0;
}
