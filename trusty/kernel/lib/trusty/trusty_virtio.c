/*
 * Copyright (c) 2015, Google, Inc. All rights reserved
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
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <trace.h>

#include <arch/arch_ops.h>
#include <inttypes.h>
#include <kernel/event.h>
#include <kernel/mutex.h>
#include <kernel/spinlock.h>
#include <kernel/vm.h>
#include <lib/binary_search_tree.h>
#include <lk/init.h>
#include <lk/reflist.h>

#include <remoteproc/remoteproc.h>
#include "trusty_virtio.h"

#define LOCAL_TRACE 0

/*
 *  FW resource version expected by us
 */
#define VIRTIO_FW_RSC_VER 1

enum {
    VIRTIO_BUS_STATE_UNINITIALIZED = 0,
    VIRTIO_BUS_STATE_IDLE,
    VIRTIO_BUS_STATE_ACTIVATING,
    VIRTIO_BUS_STATE_ACTIVE,
    VIRTIO_BUS_STATE_DEACTIVATING,
};

struct trusty_virtio_bus {
    uint vdev_cnt;
    uint next_dev_id;
    size_t descr_size;
    volatile int state;
    struct list_node vdev_list;
    struct bst_node node;
    ext_mem_client_id_t client_id;
    struct obj refobj;
    /*
     * This is a reference to refobj in the same virtio bus and gets deleted
     * after the first VIRTIO_STOP or a failed GET_DESCR SMC. It's needed to
     * ensure that refobj has at least one reference even if there are no
     * pending virtio SMCs and should only be deleted when a VM exits. After
     * it's deleted, no further SMCs can get references to the bus.
     */
    struct obj_ref tree_node_ref;
    /*
     * The last reference to the bus may get dropped from an interrupt-free
     * context which can't free the bus so this event is used to signal that the
     * bus may be freed.
     */
    event_t free_bus_event;
};

static spin_lock_t virtio_buses_tree_lock = SPIN_LOCK_INITIAL_VALUE;
static struct bst_root virtio_buses_tree = BST_ROOT_INITIAL_VALUE;

static int compare_client_ids(struct bst_node* a, struct bst_node* b) {
    DEBUG_ASSERT(a);
    DEBUG_ASSERT(b);
    struct trusty_virtio_bus* bus_a =
            containerof(a, struct trusty_virtio_bus, node);
    struct trusty_virtio_bus* bus_b =
            containerof(b, struct trusty_virtio_bus, node);
    ext_mem_client_id_t id_a = bus_a->client_id;
    ext_mem_client_id_t id_b = bus_b->client_id;
    if (id_a < id_b) {
        return 1;
    } else if (id_a > id_b) {
        return -1;
    } else {
        return 0;
    }
}

static void signal_client_bus_free(struct obj* obj) {
    struct trusty_virtio_bus* vb =
            containerof_null_safe(obj, struct trusty_virtio_bus, refobj);
    DEBUG_ASSERT(vb);
    /*
     * This function may be called with interrupts disabled, so signal that the
     * bus may be freed instead of freeing it directly here.
     */
    event_signal(&vb->free_bus_event, false);
}

static void release_bus_ref_locked(struct trusty_virtio_bus* vb,
                                   struct obj_ref* ref) {
    DEBUG_ASSERT(vb);
    DEBUG_ASSERT(ref);
    obj_del_ref(&vb->refobj, ref, signal_client_bus_free);
}

static void release_bus_ref(struct trusty_virtio_bus* vb, struct obj_ref* ref) {
    DEBUG_ASSERT(ref);
    DEBUG_ASSERT(vb);
    spin_lock_saved_state_t state;
    spin_lock_irqsave(&virtio_buses_tree_lock, state);

    release_bus_ref_locked(vb, ref);

    spin_unlock_irqrestore(&virtio_buses_tree_lock, state);
}

static struct trusty_virtio_bus* alloc_new_bus(ext_mem_client_id_t client_id,
                                               struct obj_ref* ref) {
    DEBUG_ASSERT(ref);
    struct trusty_virtio_bus* new_bus = (struct trusty_virtio_bus*)calloc(
            1, sizeof(struct trusty_virtio_bus));
    if (!new_bus) {
        return NULL;
    }
    new_bus->state = VIRTIO_BUS_STATE_UNINITIALIZED;
    new_bus->vdev_list =
            (struct list_node)LIST_INITIAL_VALUE(new_bus->vdev_list);
    new_bus->node = (struct bst_node)BST_NODE_INITIAL_VALUE;
    new_bus->client_id = client_id;
    obj_ref_init(&new_bus->tree_node_ref);
    /*
     * Initialize the refobj with the caller's reference and only add
     * tree_node_ref after we've added the bus to the tree
     */
    obj_init(&new_bus->refobj, ref);
    event_init(&new_bus->free_bus_event, 0, EVENT_FLAG_AUTOUNSIGNAL);
    return new_bus;
}

static status_t create_new_bus(ext_mem_client_id_t client_id,
                               struct trusty_virtio_bus** vb,
                               struct obj_ref* ref) {
    DEBUG_ASSERT(vb);
    DEBUG_ASSERT(ref);
    struct obj_ref tmp_ref = OBJ_REF_INITIAL_VALUE(tmp_ref);
    struct trusty_virtio_bus* new_bus = alloc_new_bus(client_id, &tmp_ref);
    if (!new_bus) {
        LTRACEF("Could not allocate memory for virtio bus for client %" PRId64
                "\n",
                client_id);
        return ERR_NO_MEMORY;
    }
    spin_lock_saved_state_t state;
    spin_lock_irqsave(&virtio_buses_tree_lock, state);

    bool inserted =
            bst_insert(&virtio_buses_tree, &new_bus->node, compare_client_ids);

    if (inserted) {
        /* Add tree_node_ref if the bus was inserted */
        obj_add_ref(&new_bus->refobj, &new_bus->tree_node_ref);
        /* Transfer the local reference to the parameter */
        obj_ref_transfer(ref, &tmp_ref);
    } else {
        /* If the bus was not inserted delete the caller's reference */
        release_bus_ref_locked(new_bus, &tmp_ref);
    }
    spin_unlock_irqrestore(&virtio_buses_tree_lock, state);

    if (!inserted) {
        DEBUG_ASSERT(!obj_has_ref(&new_bus->refobj));
        free(new_bus);
        return ERR_ALREADY_EXISTS;
    }
    *vb = new_bus;
    return NO_ERROR;
}

static struct trusty_virtio_bus* get_client_bus_locked(
        ext_mem_client_id_t client_id) {
    struct trusty_virtio_bus bus = {
            .node = BST_NODE_INITIAL_VALUE,
            .client_id = client_id,
    };
    struct bst_node* node =
            bst_search(&virtio_buses_tree, &bus.node, compare_client_ids);
    return containerof_null_safe(node, struct trusty_virtio_bus, node);
}

static struct trusty_virtio_bus* get_client_bus(ext_mem_client_id_t client_id,
                                                struct obj_ref* ref) {
    DEBUG_ASSERT(ref);
    spin_lock_saved_state_t state;
    spin_lock_irqsave(&virtio_buses_tree_lock, state);

    struct trusty_virtio_bus* vb = get_client_bus_locked(client_id);
    if (vb) {
        obj_add_ref(&vb->refobj, ref);
    }
    spin_unlock_irqrestore(&virtio_buses_tree_lock, state);

    return vb;
}

/*
 * Frees the client bus if it's in the virtio tree and deletes a reference to
 * the bus held by the caller.
 */
static void remove_client_bus(struct trusty_virtio_bus* vb,
                              struct obj_ref* ref) {
    DEBUG_ASSERT(vb);
    DEBUG_ASSERT(ref);

    spin_lock_saved_state_t state;
    spin_lock_irqsave(&virtio_buses_tree_lock, state);
    /*
     * Check if the bus is still in the tree or if another call to
     * remove_client_bus beat us
     */
    bool bus_in_tree = obj_ref_active(&vb->tree_node_ref);

    if (bus_in_tree) {
        /*
         * Remove the bus from the virtio tree to prevent further calls to
         * get_client_bus_locked from succeeding
         */
        bst_delete(&virtio_buses_tree, &vb->node);
        release_bus_ref_locked(vb, &vb->tree_node_ref);
    }
    release_bus_ref_locked(vb, ref);
    /*
     * If there are other calls to remove_client_bus, we need to drop this lock
     * before waiting on the free bus event because they may delete other
     * references to the bus
     */
    spin_unlock_irqrestore(&virtio_buses_tree_lock, state);
    if (bus_in_tree) {
        /* Blocks until the last reference to the bus is dropped */
        event_wait(&vb->free_bus_event);
        /*
         * Only the first call to remove_client_bus will find the bus in the
         * tree and end up freeing the bus
         */
        DEBUG_ASSERT(!obj_has_ref(&vb->refobj));
        free(vb);
    }
}

static mutex_t virtio_bus_notifier_lock =
        MUTEX_INITIAL_VALUE(virtio_bus_notifier_lock);
static struct list_node virtio_bus_notifier_list =
        LIST_INITIAL_VALUE(virtio_bus_notifier_list);

void trusty_virtio_register_bus_notifier(struct trusty_virtio_bus_notifier* n) {
    mutex_acquire(&virtio_bus_notifier_lock);
    list_add_tail(&virtio_bus_notifier_list, &n->node);
    mutex_release(&virtio_bus_notifier_lock);
}

static status_t on_create_virtio_bus(struct trusty_virtio_bus* vb) {
    DEBUG_ASSERT(vb);
    status_t ret = NO_ERROR;
    struct trusty_virtio_bus_notifier* n;
    mutex_acquire(&virtio_bus_notifier_lock);
    list_for_every_entry(&virtio_bus_notifier_list, n,
                         struct trusty_virtio_bus_notifier, node) {
        if (!n->on_create) {
            continue;
        }
        ret = n->on_create(vb);
        if (ret != NO_ERROR) {
            LTRACEF("call to on_create notifier failed (%d)\n", ret);
            goto on_create_err;
        }
    }
on_create_err:
    mutex_release(&virtio_bus_notifier_lock);
    return ret;
}

static status_t map_descr(ext_mem_client_id_t client_id,
                          ext_mem_obj_id_t buf_id,
                          void** buf_va,
                          ns_size_t sz,
                          uint buf_mmu_flags) {
    return ext_mem_map_obj_id(vmm_get_kernel_aspace(), "virtio", client_id,
                              buf_id, 0, 0, round_up(sz, PAGE_SIZE), buf_va,
                              PAGE_SIZE_SHIFT, 0, buf_mmu_flags);
}

static status_t unmap_descr(void* va, size_t sz) {
    return vmm_free_region(vmm_get_kernel_aspace(), (vaddr_t)va);
}

static status_t validate_vdev(struct vdev* vd) {
    /* check parameters */
    if (!vd) {
        LTRACEF("vd = %p\n", vd);
        return ERR_INVALID_ARGS;
    }

    /* check vdev_ops */
    if (!vd->ops) {
        LTRACEF("vd = %p: missing vdev ops\n", vd);
        return ERR_INVALID_ARGS;
    }

    /* all vdev_ops required */
    const struct vdev_ops* ops = vd->ops;
    if (!ops->descr_sz || !ops->get_descr || !ops->probe || !ops->reset ||
        !ops->kick_vqueue) {
        LTRACEF("vd = %p: misconfigured vdev ops\n", vd);
        return ERR_INVALID_ARGS;
    }

    return NO_ERROR;
}

/*
 *     Register virtio device
 */
status_t virtio_register_device(struct trusty_virtio_bus* vb, struct vdev* vd) {
    status_t ret = ERR_BAD_STATE;
    DEBUG_ASSERT(vb);

    if (vb->state == VIRTIO_BUS_STATE_UNINITIALIZED) {
        ret = validate_vdev(vd);
        if (ret == NO_ERROR) {
            vb->vdev_cnt++;
            vd->devid = vb->next_dev_id++;
            list_add_tail(&vb->vdev_list, &vd->node);
        }
    }
    return ret;
}

/*
 *
 */
static void finalize_vdev_registry(struct trusty_virtio_bus* vb) {
    DEBUG_ASSERT(vb);

    if (vb->state == VIRTIO_BUS_STATE_UNINITIALIZED) {
        struct vdev* vd;
        uint32_t offset =
                sizeof(struct resource_table) + sizeof(uint32_t) * vb->vdev_cnt;

        /*
         * go through the list of vdev and calculate
         * total descriptor size and offset withing descriptor
         * buffer
         */
        list_for_every_entry(&vb->vdev_list, vd, struct vdev, node) {
            vd->descr_offset = offset;
            offset += vd->ops->descr_sz(vd);
        }
        vb->descr_size = offset;
        vb->state = VIRTIO_BUS_STATE_IDLE;
    }
}

/*
 * Retrieve device description to be shared with NS side
 */
ssize_t virtio_get_description(ext_mem_client_id_t client_id,
                               ext_mem_obj_id_t buf_id,
                               ns_size_t buf_sz,
                               uint buf_mmu_flags) {
    status_t ret;
    struct vdev* vd;
    struct trusty_virtio_bus* vb = NULL;
    struct obj_ref tmp_ref = OBJ_REF_INITIAL_VALUE(tmp_ref);

    LTRACEF("descr_buf: %u bytes @ 0x%" PRIx64 "\n", buf_sz, buf_id);

    ret = create_new_bus(client_id, &vb, &tmp_ref);
    if (ret == ERR_ALREADY_EXISTS) {
        LTRACEF("Client %" PRId64 " may only call the VIRTIO_GET_DESCR once\n",
                client_id);
        return ERR_NOT_ALLOWED;
    } else if (ret != NO_ERROR) {
        LTRACEF("Could not create virtio bus for client %" PRId64 "\n",
                client_id);
        return ret;
    }

    /* on_create notifiers must only be called if virtio bus is uninitialized */
    if (vb->state == VIRTIO_BUS_STATE_UNINITIALIZED) {
        ret = on_create_virtio_bus(vb);
        /* If on_create notifiers failed remove the new virtio bus */
        if (ret != NO_ERROR) {
            goto err_failed_on_create;
        }
    }
    /*
     * finalize_vdev_registry in the first call to this function switches the
     * bus state to idle so it should never be uninitialized after this point
     */
    finalize_vdev_registry(vb);
    ASSERT(vb->state != VIRTIO_BUS_STATE_UNINITIALIZED);

    if ((size_t)buf_sz < vb->descr_size) {
        LTRACEF("buffer (%zu bytes) is too small (%zu needed)\n",
                (size_t)buf_sz, vb->descr_size);
        ret = ERR_NOT_ENOUGH_BUFFER;
        goto err_buffer;
    }

    /* map in NS memory */
    void* va = NULL;
    ret = map_descr(client_id, buf_id, &va, vb->descr_size, buf_mmu_flags);
    if (ret != NO_ERROR) {
        LTRACEF("failed (%d) to map in descriptor buffer\n", (int)ret);
        goto err_failed_map;
    }
    memset(va, 0, vb->descr_size);

    /* build resource table */
    uint32_t vdev_idx = 0;
    struct resource_table* descr = (struct resource_table*)va;

    descr->ver = VIRTIO_FW_RSC_VER;
    descr->num = vb->vdev_cnt;

    list_for_every_entry(&vb->vdev_list, vd, struct vdev, node) {
        DEBUG_ASSERT(vd->descr_offset <= vb->descr_size);
        DEBUG_ASSERT(vd->descr_offset + vd->ops->descr_sz(vd) <=
                     vb->descr_size);
        DEBUG_ASSERT(vdev_idx < vb->vdev_cnt);

        descr->offset[vdev_idx++] = vd->descr_offset;
        vd->client_id = client_id;
        vd->ops->get_descr(vd, (uint8_t*)descr + vd->descr_offset);
    }

    unmap_descr(va, vb->descr_size);

    release_bus_ref(vb, &tmp_ref);

    return vb->descr_size;

err_failed_map:
err_buffer:
err_failed_on_create:
    remove_client_bus(vb, &tmp_ref);
    return ret;
}

/*
 * Called by NS side to finalize device initialization
 */
status_t virtio_start(ext_mem_client_id_t client_id,
                      ext_mem_obj_id_t ns_descr_id,
                      ns_size_t descr_sz,
                      uint descr_mmu_flags) {
    status_t ret;
    int oldstate;
    void* descr_va;
    void* ns_descr_va = NULL;
    struct vdev* vd;
    struct obj_ref tmp_ref = OBJ_REF_INITIAL_VALUE(tmp_ref);
    struct trusty_virtio_bus* vb = get_client_bus(client_id, &tmp_ref);
    if (!vb) {
        LTRACEF("Could not get virtio bus for client %" PRId64 "\n", client_id);
        return ERR_BAD_STATE;
    }

    LTRACEF("%u bytes @ 0x%" PRIx64 "\n", descr_sz, ns_descr_id);

    list_for_every_entry(&vb->vdev_list, vd, struct vdev, node) {
        if (client_id != vd->client_id) {
            LTRACEF("mismatched client id 0x%" PRIx64 " != 0x%" PRIx64 "\n",
                    client_id, vd->client_id);
            ret = ERR_INVALID_ARGS;
            goto err_invalid_args;
        }
    }

    oldstate = atomic_cmpxchg(&vb->state, VIRTIO_BUS_STATE_IDLE,
                              VIRTIO_BUS_STATE_ACTIVATING);

    if (oldstate != VIRTIO_BUS_STATE_IDLE) {
        /* bus should be in initializing state */
        LTRACEF("unexpected state state (%d)\n", oldstate);
        ret = ERR_BAD_STATE;
        goto err_bad_state;
    }

    if ((size_t)descr_sz != vb->descr_size) {
        LTRACEF("unexpected descriptor size (%zd vs. %zd)\n", (size_t)descr_sz,
                vb->descr_size);
        ret = ERR_INVALID_ARGS;
        goto err_bad_params;
    }

    descr_va = malloc(descr_sz);
    if (!descr_va) {
        LTRACEF("not enough memory to store descr\n");
        ret = ERR_NO_MEMORY;
        goto err_alloc_descr;
    }

    /* TODO: map read-only */
    ret = map_descr(client_id, ns_descr_id, &ns_descr_va, vb->descr_size,
                    descr_mmu_flags);
    if (ret != NO_ERROR) {
        LTRACEF("failed (%d) to map in descriptor buffer\n", ret);
        goto err_map_in;
    }

    /* copy descriptor out of NS memory before parsing it */
    memcpy(descr_va, ns_descr_va, descr_sz);

    /* handle it */
    list_for_every_entry(&vb->vdev_list, vd, struct vdev, node) {
        vd->ops->probe(vd, (void*)((uint8_t*)descr_va + vd->descr_offset));
    }

    unmap_descr(ns_descr_va, vb->descr_size);
    free(descr_va);

    vb->state = VIRTIO_BUS_STATE_ACTIVE;
    release_bus_ref(vb, &tmp_ref);

    return NO_ERROR;

err_map_in:
    free(descr_va);
err_alloc_descr:
err_bad_params:
    vb->state = oldstate;
err_bad_state:
err_invalid_args:
    release_bus_ref(vb, &tmp_ref);
    return ret;
}

status_t virtio_stop(ext_mem_client_id_t client_id,
                     ext_mem_obj_id_t descr_id,
                     ns_size_t descr_sz,
                     uint descr_mmu_flags) {
    status_t ret;
    int oldstate;
    struct vdev* vd;
    struct obj_ref tmp_ref = OBJ_REF_INITIAL_VALUE(tmp_ref);
    struct trusty_virtio_bus* vb = get_client_bus(client_id, &tmp_ref);
    if (!vb) {
        LTRACEF("Could not get virtio bus for client %" PRId64 "\n", client_id);
        return ERR_BAD_STATE;
    }

    LTRACEF("%u bytes @ 0x%" PRIx64 "\n", descr_sz, descr_id);

    list_for_every_entry(&vb->vdev_list, vd, struct vdev, node) {
        if (client_id != vd->client_id) {
            LTRACEF("mismatched client id 0x%" PRIx64 " != 0x%" PRIx64 "\n",
                    client_id, vd->client_id);
            ret = ERR_INVALID_ARGS;
            goto err_invalid_args;
        }
    }

    oldstate = atomic_cmpxchg(&vb->state, VIRTIO_BUS_STATE_ACTIVE,
                              VIRTIO_BUS_STATE_DEACTIVATING);

    if (oldstate != VIRTIO_BUS_STATE_ACTIVE) {
        ret = ERR_BAD_STATE;
        goto err_bad_state;
    }

    /* reset all devices */
    list_for_every_entry(&vb->vdev_list, vd, struct vdev, node) {
        vd->ops->reset(vd);
    }

    vb->state = VIRTIO_BUS_STATE_IDLE;
    remove_client_bus(vb, &tmp_ref);

    return NO_ERROR;

err_bad_state:
    /* Remove the bus even if it was not in the active state */
    remove_client_bus(vb, &tmp_ref);
    return ret;

err_invalid_args:
    release_bus_ref(vb, &tmp_ref);
    return ret;
}

/*
 *  Reset virtio device with specified device id
 */
status_t virtio_device_reset(ext_mem_client_id_t client_id, uint devid) {
    struct vdev* vd;
    status_t ret = ERR_NOT_FOUND;
    struct obj_ref tmp_ref = OBJ_REF_INITIAL_VALUE(tmp_ref);
    struct trusty_virtio_bus* vb = get_client_bus(client_id, &tmp_ref);
    if (!vb) {
        LTRACEF("Could not get virtio bus for client %" PRId64 "\n", client_id);
        return ERR_BAD_STATE;
    }

    LTRACEF("dev=%d\n", devid);

    if (vb->state != VIRTIO_BUS_STATE_ACTIVE) {
        ret = ERR_BAD_STATE;
        goto err_bad_state;
    }

    list_for_every_entry(&vb->vdev_list, vd, struct vdev, node) {
        if (vd->devid == devid) {
            ret = vd->ops->reset(vd);
            break;
        }
    }
err_bad_state:
    release_bus_ref(vb, &tmp_ref);
    return ret;
}

/*
 *  Kick vq for virtio device with specified device id
 */
status_t virtio_kick_vq(ext_mem_client_id_t client_id, uint devid, uint vqid) {
    struct vdev* vd;
    status_t ret = ERR_NOT_FOUND;
    struct obj_ref tmp_ref = OBJ_REF_INITIAL_VALUE(tmp_ref);
    struct trusty_virtio_bus* vb = get_client_bus(client_id, &tmp_ref);
    if (!vb) {
        LTRACEF("Could not get virtio bus for client %" PRId64 "\n", client_id);
        return ERR_BAD_STATE;
    }

#if WITH_CHATTY_LTRACE
    LTRACEF("dev=%d\n", devid);
#endif

    if (vb->state != VIRTIO_BUS_STATE_ACTIVE) {
        ret = ERR_BAD_STATE;
        goto err_bad_state;
    }

    list_for_every_entry(&vb->vdev_list, vd, struct vdev, node) {
        if (vd->devid == devid) {
            ret = vd->ops->kick_vqueue(vd, vqid);
            break;
        }
    }
err_bad_state:
    release_bus_ref(vb, &tmp_ref);
    return ret;
}
