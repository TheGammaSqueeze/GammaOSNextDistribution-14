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

#include "vqueue.h"

#include <assert.h>
#include <err.h>
#include <lib/sm.h>
#include <stddef.h>
#include <stdlib.h>
#include <sys/types.h>
#include <trace.h>

#include <arch/arch_ops.h>
#include <kernel/vm.h>

#include <lib/trusty/uio.h>

#include <virtio/virtio_ring.h>

#define LOCAL_TRACE 0

#define VQ_LOCK_FLAGS SPIN_LOCK_FLAG_INTERRUPTS

int vqueue_init(struct vqueue* vq,
                uint32_t id,
                ext_mem_client_id_t client_id,
                ext_mem_obj_id_t shared_mem_id,
                uint num,
                ulong align,
                void* priv,
                vqueue_cb_t notify_cb,
                vqueue_cb_t kick_cb) {
    status_t ret;
    void* vptr = NULL;

    DEBUG_ASSERT(vq);

    vq->vring_sz = vring_size(num, align);
    ret = ext_mem_map_obj_id(vmm_get_kernel_aspace(), "vqueue", client_id,
                             shared_mem_id, 0, 0,
                             round_up(vq->vring_sz, PAGE_SIZE), &vptr,
                             PAGE_SIZE_SHIFT, 0, ARCH_MMU_FLAG_PERM_NO_EXECUTE);
    if (ret != NO_ERROR) {
        LTRACEF("cannot map vring (%d)\n", ret);
        return (int)ret;
    }

    vring_init(&vq->vring, num, vptr, align);

    vq->id = id;
    vq->priv = priv;
    vq->notify_cb = notify_cb;
    vq->kick_cb = kick_cb;
    vq->vring_addr = (vaddr_t)vptr;

    event_init(&vq->avail_event, false, 0);

    return NO_ERROR;
}

void vqueue_destroy(struct vqueue* vq) {
    vaddr_t vring_addr;
    spin_lock_saved_state_t state;

    DEBUG_ASSERT(vq);

    spin_lock_save(&vq->slock, &state, VQ_LOCK_FLAGS);
    vring_addr = vq->vring_addr;
    vq->vring_addr = (vaddr_t)NULL;
    vq->vring_sz = 0;
    spin_unlock_restore(&vq->slock, state, VQ_LOCK_FLAGS);

    vmm_free_region(vmm_get_kernel_aspace(), vring_addr);
}

void vqueue_signal_avail(struct vqueue* vq) {
    spin_lock_saved_state_t state;

    spin_lock_save(&vq->slock, &state, VQ_LOCK_FLAGS);
    if (vq->vring_addr)
        vq->vring.used->flags |= VRING_USED_F_NO_NOTIFY;
    event_signal(&vq->avail_event, false);
    spin_unlock_restore(&vq->slock, state, VQ_LOCK_FLAGS);
}

/* The other side of virtio pushes buffers into our avail ring, and pulls them
 * off our used ring. We do the reverse. We take buffers off the avail ring,
 * and put them onto the used ring.
 */

static int _vqueue_get_avail_buf_locked(struct vqueue* vq,
                                        struct vqueue_buf* iovbuf) {
    uint16_t next_idx;
    struct vring_desc* desc;

    DEBUG_ASSERT(vq);
    DEBUG_ASSERT(iovbuf);

    if (!vq->vring_addr) {
        /* there is no vring - return an error */
        return ERR_CHANNEL_CLOSED;
    }

    /* the idx counter is free running, so check that it's no more
     * than the ring size away from last time we checked... this
     * should *never* happen, but we should be careful. */
    uint16_t avail_cnt;
    __builtin_sub_overflow(vq->vring.avail->idx, vq->last_avail_idx,
                           &avail_cnt);
    if (unlikely(avail_cnt > (uint16_t)vq->vring.num)) {
        /* such state is not recoverable */
        panic("vq %u: new avail idx out of range (old %u new %u)\n", vq->id,
              vq->last_avail_idx, vq->vring.avail->idx);
    }

    if (vq->last_avail_idx == vq->vring.avail->idx) {
        event_unsignal(&vq->avail_event);
        vq->vring.used->flags &= ~VRING_USED_F_NO_NOTIFY;
        smp_mb();
        if (vq->last_avail_idx == vq->vring.avail->idx) {
            /* no buffers left */
            return ERR_NOT_ENOUGH_BUFFER;
        }
        vq->vring.used->flags |= VRING_USED_F_NO_NOTIFY;
        event_signal(&vq->avail_event, false);
    }
    smp_rmb();

    next_idx = vq->vring.avail->ring[vq->last_avail_idx % vq->vring.num];
    __builtin_add_overflow(vq->last_avail_idx, 1, &vq->last_avail_idx);

    if (unlikely(next_idx >= vq->vring.num)) {
        /* index of the first descriptor in chain is out of range.
           vring is in non recoverable state: we cannot even return
           an error to the other side */
        panic("vq %u: head out of range %u (max %u)\n", vq->id, next_idx,
              vq->vring.num);
    }

    iovbuf->head = next_idx;
    iovbuf->in_iovs.used = 0;
    iovbuf->in_iovs.len = 0;
    iovbuf->out_iovs.used = 0;
    iovbuf->out_iovs.len = 0;

    do {
        struct vqueue_iovs* iovlist;

        if (unlikely(next_idx >= vq->vring.num)) {
            /* Descriptor chain is in invalid state.
             * Abort message handling, return an error to the
             * other side and let it deal with it.
             */
            LTRACEF("vq %p: head out of range %u (max %u)\n", vq, next_idx,
                    vq->vring.num);
            return ERR_NOT_VALID;
        }

        desc = &vq->vring.desc[next_idx];
        if (desc->flags & VRING_DESC_F_WRITE)
            iovlist = &iovbuf->out_iovs;
        else
            iovlist = &iovbuf->in_iovs;

        if (iovlist->used < iovlist->cnt) {
            /* .iov_base will be set when we map this iov */
            iovlist->iovs[iovlist->used].iov_len = desc->len;
            iovlist->shared_mem_id[iovlist->used] =
                    (ext_mem_obj_id_t)desc->addr;
            assert(iovlist->shared_mem_id[iovlist->used] == desc->addr);
            iovlist->used++;
            iovlist->len += desc->len;
        } else {
            return ERR_TOO_BIG;
        }

        /* go to the next entry in the descriptor chain */
        next_idx = desc->next;
    } while (desc->flags & VRING_DESC_F_NEXT);

    return NO_ERROR;
}

int vqueue_get_avail_buf(struct vqueue* vq, struct vqueue_buf* iovbuf) {
    spin_lock_saved_state_t state;

    spin_lock_save(&vq->slock, &state, VQ_LOCK_FLAGS);
    int ret = _vqueue_get_avail_buf_locked(vq, iovbuf);
    spin_unlock_restore(&vq->slock, state, VQ_LOCK_FLAGS);
    return ret;
}

struct vqueue_mem_obj {
    ext_mem_client_id_t client_id;
    ext_mem_obj_id_t id;
    void* iov_base;
    size_t size;
    struct bst_node node;
};

static struct vqueue_mem_obj* vqueue_mem_obj_from_bst_node(
        struct bst_node* node) {
    return containerof(node, struct vqueue_mem_obj, node);
}

static int vqueue_mem_obj_cmp(struct bst_node* a_bst, struct bst_node* b_bst) {
    struct vqueue_mem_obj* a = vqueue_mem_obj_from_bst_node(a_bst);
    struct vqueue_mem_obj* b = vqueue_mem_obj_from_bst_node(b_bst);

    return a->id < b->id ? 1 : a->id > b->id ? -1 : 0;
}

static void vqueue_mem_obj_initialize(struct vqueue_mem_obj* obj,
                                      ext_mem_client_id_t client_id,
                                      ext_mem_obj_id_t id,
                                      void* iov_base,
                                      size_t size) {
    obj->client_id = client_id;
    obj->id = id;
    obj->iov_base = iov_base;
    obj->size = size;
    bst_node_initialize(&obj->node);
}

static bool vqueue_mem_insert(struct bst_root* objs,
                              struct vqueue_mem_obj* obj) {
    return bst_insert(objs, &obj->node, vqueue_mem_obj_cmp);
}

static struct vqueue_mem_obj* vqueue_mem_lookup(struct bst_root* objs,
                                                ext_mem_obj_id_t id) {
    struct vqueue_mem_obj ref_obj;
    ref_obj.id = id;
    return bst_search_type(objs, &ref_obj, vqueue_mem_obj_cmp,
                           struct vqueue_mem_obj, node);
}

static inline void vqueue_mem_delete(struct bst_root* objs,
                                     struct vqueue_mem_obj* obj) {
    bst_delete(objs, &obj->node);
}

int vqueue_map_iovs(ext_mem_client_id_t client_id,
                    struct vqueue_iovs* vqiovs,
                    u_int flags,
                    struct vqueue_mapped_list* mapped_list) {
    uint i;
    int ret;
    size_t size;
    struct vqueue_mem_obj* obj;

    DEBUG_ASSERT(vqiovs);
    DEBUG_ASSERT(vqiovs->shared_mem_id);
    DEBUG_ASSERT(vqiovs->iovs);
    DEBUG_ASSERT(vqiovs->used <= vqiovs->cnt);

    for (i = 0; i < vqiovs->used; i++) {
        /* see if it's already been mapped */
        mutex_acquire(&mapped_list->lock);
        obj = vqueue_mem_lookup(&mapped_list->list, vqiovs->shared_mem_id[i]);
        mutex_release(&mapped_list->lock);

        if (obj && obj->client_id == client_id &&
            vqiovs->iovs[i].iov_len <= obj->size) {
            LTRACEF("iov restored %s id= %lu (base= %p, size= %lu)\n",
                    mapped_list->in_direction ? "IN" : "OUT",
                    (unsigned long)vqiovs->shared_mem_id[i], obj->iov_base,
                    (unsigned long)obj->size);
            vqiovs->iovs[i].iov_base = obj->iov_base;
            continue; /* use the previously mapped */
        } else if (obj) {
            /* otherwise, we need to drop old mapping and remap  */
            TRACEF("iov needs remapped for id= %lu\n",
                   (unsigned long)vqiovs->shared_mem_id[i]);
            mutex_acquire(&mapped_list->lock);
            vqueue_mem_delete(&mapped_list->list, obj);
            mutex_release(&mapped_list->lock);
            free(obj);
        }

        /* allocate since it may be reused instead of unmapped after use */
        obj = calloc(1, sizeof(struct vqueue_mem_obj));
        if (unlikely(!obj)) {
            TRACEF("calloc failure for vqueue_mem_obj for iov\n");
            ret = ERR_NO_MEMORY;
            goto err;
        }

        /* map it */
        vqiovs->iovs[i].iov_base = NULL;
        size = round_up(vqiovs->iovs[i].iov_len, PAGE_SIZE);
        ret = ext_mem_map_obj_id(vmm_get_kernel_aspace(), "vqueue-buf",
                                 client_id, vqiovs->shared_mem_id[i], 0, 0,
                                 size, &vqiovs->iovs[i].iov_base,
                                 PAGE_SIZE_SHIFT, 0, flags);
        if (ret) {
            free(obj);
            goto err;
        }

        vqueue_mem_obj_initialize(obj, client_id, vqiovs->shared_mem_id[i],
                                  vqiovs->iovs[i].iov_base, size);

        mutex_acquire(&mapped_list->lock);
        if (unlikely(!vqueue_mem_insert(&mapped_list->list, obj)))
            panic("Unhandled duplicate entry in ext_mem for iov\n");
        mutex_release(&mapped_list->lock);

        LTRACEF("iov saved %s id= %lu (base= %p, size= %lu)\n",
                mapped_list->in_direction ? "IN" : "OUT",
                (unsigned long)vqiovs->shared_mem_id[i],
                vqiovs->iovs[i].iov_base, (unsigned long)size);
    }

    return NO_ERROR;

err:
    while (i) {
        i--;
        vmm_free_region(vmm_get_kernel_aspace(),
                        (vaddr_t)vqiovs->iovs[i].iov_base);
        vqiovs->iovs[i].iov_base = NULL;
    }
    return ret;
}

void vqueue_unmap_iovs(struct vqueue_iovs* vqiovs,
                       struct vqueue_mapped_list* mapped_list) {
    struct vqueue_mem_obj* obj;

    DEBUG_ASSERT(vqiovs);
    DEBUG_ASSERT(vqiovs->shared_mem_id);
    DEBUG_ASSERT(vqiovs->iovs);
    DEBUG_ASSERT(vqiovs->used <= vqiovs->cnt);

    for (uint i = 0; i < vqiovs->used; i++) {
        /* base is expected to be set */
        DEBUG_ASSERT(vqiovs->iovs[i].iov_base);
        vmm_free_region(vmm_get_kernel_aspace(),
                        (vaddr_t)vqiovs->iovs[i].iov_base);
        vqiovs->iovs[i].iov_base = NULL;

        /* remove from list since it has been unmapped */
        mutex_acquire(&mapped_list->lock);
        obj = vqueue_mem_lookup(&mapped_list->list, vqiovs->shared_mem_id[i]);
        if (obj) {
            LTRACEF("iov removed %s id= %lu (base= %p, size= %lu)\n",
                    mapped_list->in_direction ? "IN" : "OUT",
                    (unsigned long)vqiovs->shared_mem_id[i],
                    vqiovs->iovs[i].iov_base,
                    (unsigned long)vqiovs->iovs[i].iov_len);
            vqueue_mem_delete(&mapped_list->list, obj);
            free(obj);
        } else {
            TRACEF("iov mapping not found for id= %lu (base= %p, size= %lu)\n",
                   (unsigned long)vqiovs->shared_mem_id[i],
                   vqiovs->iovs[i].iov_base,
                   (unsigned long)vqiovs->iovs[i].iov_len);
        }
        mutex_release(&mapped_list->lock);
    }
}

int vqueue_unmap_memid(ext_mem_obj_id_t id,
                       struct vqueue_mapped_list* mapped_list[],
                       int list_cnt) {
    struct vqueue_mapped_list* mapped;
    struct vqueue_mem_obj* obj;
    struct vqueue_iovs fake_vqiovs;
    ext_mem_obj_id_t fake_shared_mem_id[1];
    struct iovec_kern fake_iovs[1];

    /* determine which list this entry is in */
    for (int i = 0; i < list_cnt; i++) {
        mapped = mapped_list[i];
        obj = vqueue_mem_lookup(&mapped->list, id);
        if (obj)
            break;
        mapped = NULL;
    }

    if (mapped) {
        /* fake a vqueue_iovs struct to use common interface */
        memset(&fake_vqiovs, 0, sizeof(fake_vqiovs));
        fake_vqiovs.iovs = fake_iovs;
        fake_vqiovs.shared_mem_id = fake_shared_mem_id;
        fake_vqiovs.used = 1;
        fake_vqiovs.cnt = 1;
        fake_vqiovs.iovs[0].iov_base = obj->iov_base;
        fake_vqiovs.iovs[0].iov_len = obj->size;
        fake_vqiovs.shared_mem_id[0] = id;

        /* unmap */
        vqueue_unmap_iovs(&fake_vqiovs, mapped);

        return NO_ERROR;
    }

    return ERR_NOT_FOUND;
}

static int _vqueue_add_buf_locked(struct vqueue* vq,
                                  struct vqueue_buf* buf,
                                  uint32_t len) {
    struct vring_used_elem* used;

    DEBUG_ASSERT(vq);
    DEBUG_ASSERT(buf);

    if (!vq->vring_addr) {
        /* there is no vring - return an error */
        return ERR_CHANNEL_CLOSED;
    }

    if (buf->head >= vq->vring.num) {
        /* this would probable mean corrupted vring */
        LTRACEF("vq %p: head (%u) out of range (%u)\n", vq, buf->head,
                vq->vring.num);
        return ERR_NOT_VALID;
    }

    used = &vq->vring.used->ring[vq->vring.used->idx % vq->vring.num];
    used->id = buf->head;
    used->len = len;
    smp_wmb();
    __builtin_add_overflow(vq->vring.used->idx, 1, &vq->vring.used->idx);
    return NO_ERROR;
}

int vqueue_add_buf(struct vqueue* vq, struct vqueue_buf* buf, uint32_t len) {
    spin_lock_saved_state_t state;

    spin_lock_save(&vq->slock, &state, VQ_LOCK_FLAGS);
    int ret = _vqueue_add_buf_locked(vq, buf, len);
    spin_unlock_restore(&vq->slock, state, VQ_LOCK_FLAGS);
    return ret;
}
