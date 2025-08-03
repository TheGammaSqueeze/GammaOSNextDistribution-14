/*
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

#define LOCAL_TRACE 0

/**
 * @file
 * @brief  IPC message management primitives
 * @defgroup ipc IPC
 *
 * Provides low level data structures for managing message
 * areas for the ipc contexts.
 *
 * Also provides user syscall implementations for message
 * send/receive mechanism.
 *
 * @{
 */

#include <assert.h>
#include <err.h>
#include <kernel/usercopy.h>
#include <list.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <trace.h>

#include <lib/syscall.h>

#if WITH_TRUSTY_IPC

#include <lib/trusty/handle.h>
#include <lib/trusty/ipc.h>
#include <lib/trusty/ipc_msg.h>
#include <lib/trusty/trusty_app.h>
#include <lib/trusty/uctx.h>

enum {
    MSG_ITEM_STATE_FREE = 0,
    MSG_ITEM_STATE_FILLED = 1,
    MSG_ITEM_STATE_READ = 2,
};

struct msg_item {
    uint8_t id;
    uint8_t state;
    uint num_handles;
    struct handle* handles[MAX_MSG_HANDLES];
    size_t len;
    struct list_node node;
};

struct ipc_msg_queue {
    struct list_node free_list;
    struct list_node filled_list;
    struct list_node read_list;

    uint num_items;
    size_t item_sz;

    uint8_t* buf;

    /* store the message descriptors in the queue,
     * and the buffer separately. The buffer could
     * eventually move to a separate area that can
     * be mapped into the process directly.
     */
    struct msg_item items[0];
};

/**
 * @brief  Create IPC message queue
 *
 * Stores up-to a predefined number of equal-sized items in a circular
 * buffer (FIFO).
 *
 * @param num_items   Number of messages we need to store.
 * @param item_sz     Size of each message item.
 * @param mq          Pointer where to store the ptr to the newly allocated
 *                    message queue.
 *
 * @return  Returns NO_ERROR on success, ERR_NO_MEMORY on error.
 */
int ipc_msg_queue_create(uint num_items,
                         size_t item_sz,
                         struct ipc_msg_queue** mq) {
    struct ipc_msg_queue* tmp_mq;
    int ret;

    tmp_mq = calloc(1, (sizeof(struct ipc_msg_queue) +
                        num_items * sizeof(struct msg_item)));
    if (!tmp_mq) {
        dprintf(CRITICAL, "cannot allocate memory for message queue\n");
        return ERR_NO_MEMORY;
    }

    tmp_mq->buf = malloc(num_items * item_sz);
    if (!tmp_mq->buf) {
        dprintf(CRITICAL, "cannot allocate memory for message queue buf\n");
        ret = ERR_NO_MEMORY;
        goto err_alloc_buf;
    }

    tmp_mq->num_items = num_items;
    tmp_mq->item_sz = item_sz;
    list_initialize(&tmp_mq->free_list);
    list_initialize(&tmp_mq->filled_list);
    list_initialize(&tmp_mq->read_list);

    for (uint i = 0; i < num_items; i++) {
        tmp_mq->items[i].id = i;
        list_add_tail(&tmp_mq->free_list, &tmp_mq->items[i].node);
    }
    *mq = tmp_mq;
    return 0;

err_alloc_buf:
    free(tmp_mq);
    return ret;
}

void ipc_msg_queue_destroy(struct ipc_msg_queue* mq) {
    /* release handles if any */
    for (uint i = 0; i < mq->num_items; i++) {
        struct msg_item* item = &mq->items[i];
        if (item->num_handles) {
            for (uint j = 0; j < item->num_handles; j++) {
                handle_decref(item->handles[j]);
            }
        }
    }
    free(mq->buf);
    free(mq);
}

bool ipc_msg_queue_is_empty(struct ipc_msg_queue* mq) {
    return list_is_empty(&mq->filled_list);
}

bool ipc_msg_queue_is_full(struct ipc_msg_queue* mq) {
    return list_is_empty(&mq->free_list);
}

static inline uint8_t* msg_queue_get_buf(struct ipc_msg_queue* mq,
                                         struct msg_item* item) {
    return mq->buf + item->id * mq->item_sz;
}

static inline struct msg_item* msg_queue_get_item(struct ipc_msg_queue* mq,
                                                  uint32_t id) {
    return id < mq->num_items ? &mq->items[id] : NULL;
}

static int check_channel(struct handle* chandle) {
    if (unlikely(!chandle))
        return ERR_INVALID_ARGS;

    if (unlikely(!ipc_is_channel(chandle)))
        return ERR_INVALID_ARGS;

    struct ipc_chan* chan = containerof(chandle, struct ipc_chan, handle);

    if (unlikely(!chan->peer))
        return ERR_NOT_READY;

    return NO_ERROR;
}

static ssize_t kern_msg_write_locked(struct ipc_msg_queue* mq,
                                     struct msg_item* item,
                                     const struct ipc_msg_kern* msg) {
    ssize_t ret = NO_ERROR;
    uint8_t* buf = msg_queue_get_buf(mq, item);

    if (msg->num_handles) {
        if (msg->num_handles > MAX_MSG_HANDLES) {
            LTRACEF("sending too many (%u) handles\n", msg->num_handles);
            return ERR_TOO_BIG;
        }

        if (!msg->handles)
            return ERR_INVALID_ARGS;
    }

    /* copy any message body */
    if (likely(msg->num_iov)) {
        ret = kern_iovec_to_membuf(buf, mq->item_sz,
                                   (const struct iovec_kern*)msg->iov,
                                   msg->num_iov);
        if (ret < 0)
            return ret;
    }

    /* copy attached handles */
    for (uint i = 0; i < msg->num_handles; i++) {
        if (!msg->handles[i]) {
            ret = ERR_BAD_HANDLE;
            goto err_bad_handle;
        }

        if (!handle_is_sendable(msg->handles[i])) {
            ret = ERR_NOT_ALLOWED;
            goto err_bad_handle;
        }

        /* grab an additional reference */
        handle_incref(msg->handles[i]);
        item->handles[i] = msg->handles[i];
        item->num_handles++;
    }

    return ret;

err_bad_handle:
    for (uint i = 0; i < item->num_handles; i++) {
        handle_decref(item->handles[i]);
        item->handles[i] = NULL;
    }
    item->num_handles = 0;

    return ret;
}

static ssize_t user_msg_write_locked(struct ipc_msg_queue* mq,
                                     struct msg_item* item,
                                     const struct ipc_msg_user* msg,
                                     struct uctx* uctx) {
    int rc;
    ssize_t ret;
    uint8_t* buf = msg_queue_get_buf(mq, item);

    if (msg->num_handles > MAX_MSG_HANDLES) {
        LTRACEF("sending too many (%u) handles\n", msg->num_handles);
        return ERR_TOO_BIG;
    }

    /* copy message body */
    ret = user_iovec_to_membuf(buf, mq->item_sz, msg->iov, msg->num_iov);
    if (ret < 0)
        return ret;

    if (!msg->num_handles)
        return ret; /* no handles, just return body */

    /* copy handle ids from user space */
    handle_id_t ids[MAX_MSG_HANDLES];

    rc = copy_from_user(&ids, msg->handles,
                        msg->num_handles * sizeof(handle_id_t));
    if (unlikely(rc != NO_ERROR))
        return rc;

    /* Need to send all or none */
    for (uint i = 0; i < msg->num_handles; i++) {
        rc = uctx_handle_get(uctx, ids[i], &item->handles[i]);
        if (unlikely(rc != NO_ERROR)) {
            goto err_get;
        }
        item->num_handles++;

        if (!handle_is_sendable(item->handles[i])) {
            rc = ERR_NOT_ALLOWED;
            goto err_send;
        }
    }

    return ret;

err_send:
err_get:
    for (uint i = 0; i < item->num_handles; i++) {
        handle_decref(item->handles[i]);
        item->handles[i] = NULL;
    }
    item->num_handles = 0;

    return rc;
}

static int msg_write_locked(struct ipc_chan* chan,
                            const void* msg,
                            struct uctx* uctx) {
    ssize_t ret;
    struct msg_item* item;
    struct ipc_chan* peer = chan->peer;

    if (peer->state != IPC_CHAN_STATE_CONNECTED) {
        if (likely(peer->state == IPC_CHAN_STATE_DISCONNECTING))
            return ERR_CHANNEL_CLOSED;
        else
            return ERR_NOT_READY;
    }

    struct ipc_msg_queue* mq = peer->msg_queue;

    item = list_peek_head_type(&mq->free_list, struct msg_item, node);
    if (item == NULL) {
        peer->aux_state |= IPC_CHAN_AUX_STATE_PEER_SEND_BLOCKED;
        return ERR_NOT_ENOUGH_BUFFER;
    }
    if (!list_is_empty(&chan->msg_queue->read_list)) {
        /**
         * Application shall retire read messages (via put_msg api)
         * before sending responses. This not only allows to not waste
         * receive message queue spots, but more importantly prevents
         * a hard-to-debug race condition where incoming messages from linux
         * are silently dropped due to the receive queue being freed
         * only after the response is sent.
         */
        TRACEF("WARNING: sending outgoing messages while incoming messages are in read state is not allowed\n");
        /**
         * todo: return an error after sufficient soak time of the warning log
         * return ERR_NOT_ALLOWED;
         */
    }
    DEBUG_ASSERT(item->state == MSG_ITEM_STATE_FREE);

    item->num_handles = 0;
    item->len = 0;

    if (uctx)
        ret = user_msg_write_locked(mq, item, msg, uctx);
    else
        ret = kern_msg_write_locked(mq, item, msg);

    if (ret < 0)
        return ret;

    item->len = (size_t)ret;
    list_delete(&item->node);
    list_add_tail(&mq->filled_list, &item->node);
    item->state = MSG_ITEM_STATE_FILLED;

    return item->len;
}

/*
 * Check if specified message id is valid, message is in read state
 * and provided offset is within message bounds.
 */
static struct msg_item* msg_check_read_item(struct ipc_msg_queue* mq,
                                            uint32_t msg_id,
                                            uint32_t offset) {
    struct msg_item* item;

    item = msg_queue_get_item(mq, msg_id);
    if (!item) {
        LTRACEF("invalid message id %d\n", msg_id);
        return NULL;
    }

    if (item->state != MSG_ITEM_STATE_READ) {
        LTRACEF("message %d is not in READ state (0x%x)\n", item->id,
                item->state);
        return NULL;
    }

    if (offset > item->len) {
        LTRACEF("invalid offset %d\n", offset);
        return NULL;
    }

    return item;
}

/*
 * Reads the specified message by copying message data into the iov list
 * and associated handles to destination handle array provided by kmsg.
 * The message must have been previously moved to the read list (and thus
 * put into READ state).
 */
static int kern_msg_read_locked(struct ipc_msg_queue* mq,
                                int32_t msg_id,
                                uint32_t offset,
                                struct ipc_msg_kern* kmsg) {
    int ret = 0;
    struct msg_item* item;

    item = msg_check_read_item(mq, msg_id, offset);
    if (!item)
        return ERR_INVALID_ARGS;

    const uint8_t* buf = msg_queue_get_buf(mq, item) + offset;
    size_t bytes_left = item->len - offset;

    if (likely(kmsg->num_iov)) {
        ret = membuf_to_kern_iovec((const struct iovec_kern*)kmsg->iov,
                                   kmsg->num_iov, buf, bytes_left);
        if (ret < 0)
            return ret;
    }

    uint hcnt = MIN(kmsg->num_handles, item->num_handles);
    for (uint i = 0; i < hcnt; i++) {
        handle_incref(item->handles[i]);
        kmsg->handles[i] = item->handles[i];
    }

    return ret;
}

/*
 * Reads the specified message by copying message data to user space (iov list
 * is provided by umsg) and associated handles to destination handle array
 * provided by caller. The message must have been previously moved to the read
 * list (and thus put into READ state).
 */
static int user_msg_read_locked(struct ipc_msg_queue* mq,
                                uint32_t msg_id,
                                uint32_t offset,
                                struct ipc_msg_user* umsg,
                                struct handle** ph,
                                uint* phcnt) {
    int ret;
    struct msg_item* item;

    item = msg_check_read_item(mq, msg_id, offset);
    if (!item)
        return ERR_INVALID_ARGS;

    const uint8_t* buf = msg_queue_get_buf(mq, item) + offset;
    size_t bytes_left = item->len - offset;

    ret = membuf_to_user_iovec(umsg->iov, umsg->num_iov, buf, bytes_left);
    if (ret < 0)
        return ret;

    /* return out handles with additional refs */
    uint hcnt = MIN(umsg->num_handles, item->num_handles);
    for (uint i = 0; i < hcnt; i++) {
        handle_incref(item->handles[i]);
        ph[i] = item->handles[i];
    }
    *phcnt = hcnt;

    return ret;
}

/*
 * Is called to look at the head of the filled messages list. It should be
 * followed by calling msg_get_filled_locked call to actually move message to
 * readable list.
 */
static int msg_peek_next_filled_locked(struct ipc_msg_queue* mq,
                                       struct ipc_msg_info* info) {
    struct msg_item* item;

    item = list_peek_head_type(&mq->filled_list, struct msg_item, node);
    if (!item)
        return ERR_NO_MSG;

    info->len = item->len;
    info->id = item->id;
    info->num_handles = item->num_handles;

    return NO_ERROR;
}

/*
 *  Is called to move top of the queue item to readable list.
 */
static void msg_get_filled_locked(struct ipc_msg_queue* mq) {
    struct msg_item* item;

    item = list_peek_head_type(&mq->filled_list, struct msg_item, node);
    DEBUG_ASSERT(item);

    list_delete(&item->node);
    list_add_tail(&mq->read_list, &item->node);
    item->state = MSG_ITEM_STATE_READ;
}

static int msg_put_read_locked(struct ipc_chan* chan,
                               uint32_t msg_id,
                               struct handle** ph,
                               uint* phcnt) {
    DEBUG_ASSERT(chan);
    DEBUG_ASSERT(chan->msg_queue);
    DEBUG_ASSERT(ph);
    DEBUG_ASSERT(phcnt);

    struct ipc_msg_queue* mq = chan->msg_queue;
    struct msg_item* item = msg_queue_get_item(mq, msg_id);

    if (!item || item->state != MSG_ITEM_STATE_READ)
        return ERR_INVALID_ARGS;

    list_delete(&item->node);

    /* detach handles from table if any */
    for (uint j = 0; j < item->num_handles; j++) {
        ph[j] = item->handles[j];
        item->handles[j] = NULL;
    }
    *phcnt = item->num_handles;
    item->num_handles = 0;

    /* put it on the head since it was just taken off here */
    list_add_head(&mq->free_list, &item->node);
    item->state = MSG_ITEM_STATE_FREE;

    return NO_ERROR;
}

long __SYSCALL sys_send_msg(uint32_t handle_id, user_addr_t user_msg) {
    struct handle* chandle;
    struct ipc_msg_user tmp_msg;
    int ret;
    struct uctx* uctx = current_uctx();

    /* copy message descriptor from user space */
    ret = copy_from_user(&tmp_msg, user_msg, sizeof(struct ipc_msg_user));
    if (unlikely(ret != NO_ERROR))
        return (long)ret;

    /* grab handle */
    ret = uctx_handle_get(uctx, handle_id, &chandle);
    if (unlikely(ret != NO_ERROR))
        return (long)ret;

    ret = check_channel(chandle);
    if (likely(ret == NO_ERROR)) {
        struct ipc_chan* chan = containerof(chandle, struct ipc_chan, handle);
        mutex_acquire(&chan->peer->mlock);
        ret = msg_write_locked(chan, &tmp_msg, uctx);
        mutex_release(&chan->peer->mlock);
        if (ret >= 0) {
            /* and notify target */
            handle_notify(&chan->peer->handle);
        }
    }
    handle_decref(chandle);
    return (long)ret;
}

int ipc_send_msg(struct handle* chandle, struct ipc_msg_kern* msg) {
    int ret;

    if (!msg)
        return ERR_INVALID_ARGS;

    ret = check_channel(chandle);
    if (likely(ret == NO_ERROR)) {
        struct ipc_chan* chan = containerof(chandle, struct ipc_chan, handle);
        mutex_acquire(&chan->peer->mlock);
        ret = msg_write_locked(chan, msg, NULL);
        mutex_release(&chan->peer->mlock);
        if (ret >= 0) {
            handle_notify(&chan->peer->handle);
        }
    }
    return ret;
}

long __SYSCALL sys_get_msg(uint32_t handle_id, user_addr_t user_msg_info) {
    struct handle* chandle;
    struct ipc_msg_info mi_kern;
    int ret;

    /* grab handle */
    ret = uctx_handle_get(current_uctx(), handle_id, &chandle);
    if (ret != NO_ERROR)
        return (long)ret;

    /* check if channel handle is a valid one */
    ret = check_channel(chandle);
    if (likely(ret == NO_ERROR)) {
        struct ipc_chan* chan = containerof(chandle, struct ipc_chan, handle);
        mutex_acquire(&chan->mlock);
        /* peek next filled message */
        ret = msg_peek_next_filled_locked(chan->msg_queue, &mi_kern);
        if (likely(ret == NO_ERROR)) {
            /* copy it to user space */
            struct ipc_msg_info_user mi_user;

            memset(&mi_user, 0, sizeof(mi_user));
            mi_user.len = (user_size_t)mi_kern.len;
            mi_user.id = mi_kern.id;
            mi_user.num_handles = mi_kern.num_handles;
            ret = copy_to_user(user_msg_info, &mi_user, sizeof(mi_user));
            if (likely(ret == NO_ERROR)) {
                /* and make it readable */
                msg_get_filled_locked(chan->msg_queue);
            }
        }
        mutex_release(&chan->mlock);
    }
    handle_decref(chandle);
    return (long)ret;
}

int ipc_get_msg(struct handle* chandle, struct ipc_msg_info* msg_info) {
    int ret;

    /* check if channel handle */
    ret = check_channel(chandle);
    if (likely(ret == NO_ERROR)) {
        struct ipc_chan* chan = containerof(chandle, struct ipc_chan, handle);
        mutex_acquire(&chan->mlock);
        /* peek next filled message */
        ret = msg_peek_next_filled_locked(chan->msg_queue, msg_info);
        if (likely(ret == NO_ERROR)) {
            /* and make it readable */
            msg_get_filled_locked(chan->msg_queue);
        }
        mutex_release(&chan->mlock);
    }
    return ret;
}

long __SYSCALL sys_put_msg(uint32_t handle_id, uint32_t msg_id) {
    struct handle* chandle;

    /* grab handle */
    int ret = uctx_handle_get(current_uctx(), handle_id, &chandle);
    if (unlikely(ret != NO_ERROR))
        return (long)ret;

    /* and put it to rest */
    ret = ipc_put_msg(chandle, msg_id);
    handle_decref(chandle);

    return (long)ret;
}

int ipc_put_msg(struct handle* chandle, uint32_t msg_id) {
    int ret;

    /* check is channel handle is a valid one */
    ret = check_channel(chandle);
    if (unlikely(ret != NO_ERROR))
        return ret;

    struct handle* h[MAX_MSG_HANDLES];
    uint hcnt = 0;
    bool need_notify = false;
    struct ipc_chan* chan = containerof(chandle, struct ipc_chan, handle);
    /* retire message */
    mutex_acquire(&chan->mlock);
    ret = msg_put_read_locked(chan, msg_id, h, &hcnt);
    if (ret == NO_ERROR &&
        (chan->aux_state & IPC_CHAN_AUX_STATE_PEER_SEND_BLOCKED)) {
        chan->aux_state &= ~IPC_CHAN_AUX_STATE_PEER_SEND_BLOCKED;
        need_notify = true;
    }
    mutex_release(&chan->mlock);

    /* drop handle references outside of the lock */
    for (uint i = 0; i < hcnt; i++) {
        handle_decref(h[i]);
    }

    if (need_notify) {
        mutex_acquire(&chan->peer->mlock);
        chan->peer->aux_state |= IPC_CHAN_AUX_STATE_SEND_UNBLOCKED;
        mutex_release(&chan->peer->mlock);
        handle_notify(&chan->peer->handle);
    }
    return ret;
}

static void user_remove_multiple(struct uctx* uctx,
                                 handle_id_t* hids,
                                 uint hcnt) {
    for (uint i = 0; i < hcnt; i++)
        uctx_handle_remove(uctx, hids[i], NULL);
}

static int user_install_multiple(struct uctx* uctx,
                                 struct handle** hptrs,
                                 handle_id_t* hids,
                                 uint hcnt) {
    for (uint i = 0; i < hcnt; i++) {
        int rc = uctx_handle_install(uctx, hptrs[i], &hids[i]);
        if (rc) {
            user_remove_multiple(uctx, hids, i);
            return rc;
        }
    }
    return 0;
}

static int user_return_handles(struct uctx* uctx,
                               user_addr_t uhptrs,
                               struct handle** hptrs,
                               uint hcnt) {
    int rc;
    handle_id_t hids[MAX_MSG_HANDLES];

    if (hcnt > MAX_MSG_HANDLES) {
        LTRACEF("returning too many (%u) handles\n", hcnt);
        return ERR_TOO_BIG;
    }

    /* install handles */
    rc = user_install_multiple(uctx, hptrs, hids, hcnt);
    if (rc < 0)
        return rc;

    /* copy out handle ids */
    rc = copy_to_user(uhptrs, hids, hcnt * sizeof(handle_id_t));
    if (rc < 0) {
        /* remove installed handles in case of error */
        user_remove_multiple(uctx, hids, hcnt);
        return rc;
    }
    return 0;
}

long __SYSCALL sys_read_msg(uint32_t handle_id,
                            uint32_t msg_id,
                            uint32_t offset,
                            user_addr_t user_msg) {
    struct handle* chandle;
    struct ipc_msg_user msg;
    int ret;
    struct uctx* uctx = current_uctx();

    /* get msg descriptor from user space */
    ret = copy_from_user(&msg, user_msg, sizeof(struct ipc_msg_user));
    if (unlikely(ret != NO_ERROR))
        return (long)ret;

    /* grab handle */
    ret = uctx_handle_get(uctx, handle_id, &chandle);
    if (unlikely(ret != NO_ERROR))
        return (long)ret;

    /* check if channel handle is a valid one */
    ret = check_channel(chandle);
    if (ret == NO_ERROR) {
        struct ipc_chan* chan = containerof(chandle, struct ipc_chan, handle);
        struct handle* h[MAX_MSG_HANDLES];
        uint hcnt = 0;

        mutex_acquire(&chan->mlock);
        ret = user_msg_read_locked(chan->msg_queue, msg_id, offset, &msg, h,
                                   &hcnt);
        mutex_release(&chan->mlock);

        if (ret >= 0 && hcnt) {
            /* install into caller handle table and copy them out */
            int rc = user_return_handles(uctx, msg.handles, h, hcnt);
            if (rc < 0) {
                ret = rc;
            }

            /* drop references obtained in user_msg_read_locked */
            for (uint i = 0; i < hcnt; i++)
                handle_decref(h[i]);
        }
    }
    handle_decref(chandle);

    return (long)ret;
}

int ipc_read_msg(struct handle* chandle,
                 uint32_t msg_id,
                 uint32_t offset,
                 struct ipc_msg_kern* msg) {
    int ret;

    if (!msg)
        return ERR_INVALID_ARGS;

    ret = check_channel(chandle);
    if (ret == NO_ERROR) {
        struct ipc_chan* chan = containerof(chandle, struct ipc_chan, handle);
        mutex_acquire(&chan->mlock);
        ret = kern_msg_read_locked(chan->msg_queue, msg_id, offset, msg);
        mutex_release(&chan->mlock);
    }
    return ret;
}

#else /* WITH_TRUSTY_IPC */

long __SYSCALL sys_send_msg(uint32_t handle_id, user_addr_t user_msg) {
    return (long)ERR_NOT_SUPPORTED;
}

long __SYSCALL sys_get_msg(uint32_t handle_id, user_addr_t user_msg_info) {
    return (long)ERR_NOT_SUPPORTED;
}

long __SYSCALL sys_put_msg(uint32_t handle_id, uint32_t msg_id) {
    return (long)ERR_NOT_SUPPORTED;
}

long __SYSCALL sys_read_msg(uint32_t handle_id,
                            uint32_t msg_id,
                            uint32_t offset,
                            user_addr_t user_msg) {
    return (long)ERR_NOT_SUPPORTED;
}

#endif /* WITH_TRUSTY_IPC */
