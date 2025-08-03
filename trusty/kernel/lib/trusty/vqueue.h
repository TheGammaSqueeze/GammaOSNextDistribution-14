/*
 * Copyright (c) 2013-2014, Google, Inc. All rights reserved
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

#ifndef _LIB_TRUSTY_VQUEUE_H
#define _LIB_TRUSTY_VQUEUE_H

#include <arch/ops.h>
#include <kernel/event.h>
#include <kernel/mutex.h>
#include <lib/extmem/extmem.h>
#include <lib/trusty/uio.h>
#include <stdint.h>
#include <string.h>
#include <sys/types.h>

#include <virtio/virtio_ring.h>

struct vqueue;
typedef int (*vqueue_cb_t)(struct vqueue* vq, void* priv);

struct vqueue {
    uint32_t id;

    struct vring vring;
    vaddr_t vring_addr;
    size_t vring_sz;

    spin_lock_t slock;

    uint16_t last_avail_idx;

    event_t avail_event;

    /* called when the vq is kicked *from* the other side */
    vqueue_cb_t notify_cb;

    /* called when we want to kick the other side */
    vqueue_cb_t kick_cb;

    void* priv;
};

struct vqueue_iovs {
    uint cnt;   /* max number of iovs available */
    uint used;  /* number of iovs currently in use */
    size_t len; /* total length of all used iovs */
    ext_mem_obj_id_t* shared_mem_id;
    struct iovec_kern* iovs;
};

struct vqueue_buf {
    uint16_t head;
    uint16_t padding;
    struct vqueue_iovs in_iovs;
    struct vqueue_iovs out_iovs;
};

struct vqueue_mapped_list {
    struct bst_root list;
    mutex_t lock;
    bool in_direction;
};

int vqueue_init(struct vqueue* vq,
                uint32_t id,
                ext_mem_client_id_t client_id,
                ext_mem_obj_id_t shared_mem_id,
                uint num,
                ulong align,
                void* priv,
                vqueue_cb_t notify_cb,
                vqueue_cb_t kick_cb);

void vqueue_destroy(struct vqueue* vq);

int vqueue_get_avail_buf(struct vqueue* vq, struct vqueue_buf* iovbuf);

int vqueue_map_iovs(ext_mem_client_id_t client_id,
                    struct vqueue_iovs* vqiovs,
                    u_int flags,
                    struct vqueue_mapped_list* mapped_list);
void vqueue_unmap_iovs(struct vqueue_iovs* vqiovs,
                       struct vqueue_mapped_list* mapped_list);

int vqueue_unmap_memid(ext_mem_obj_id_t id,
                       struct vqueue_mapped_list* mapped_list[],
                       int list_cnt);

int vqueue_add_buf(struct vqueue* vq, struct vqueue_buf* buf, uint32_t len);

void vqueue_signal_avail(struct vqueue* vq);

static inline uint32_t vqueue_id(struct vqueue* vq) {
    return vq->id;
}

static inline int vqueue_notify(struct vqueue* vq) {
    if (vq->notify_cb)
        return vq->notify_cb(vq, vq->priv);
    return 0;
}

static inline int vqueue_kick(struct vqueue* vq) {
    if (vq->kick_cb)
        return vq->kick_cb(vq, vq->priv);
    return 0;
}

#endif /* _LIB_TRUSTY_VQUEUE_H */
