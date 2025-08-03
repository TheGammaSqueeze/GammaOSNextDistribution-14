/*
 * Copyright (c) 2016, Google, Inc. All rights reserved
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

#include "tipc_dev_ql.h"

#include <assert.h>
#include <compiler.h>
#include <err.h>
#include <inttypes.h>
#include <kernel/mutex.h>
#include <kernel/vm.h>
#include <lib/sm/sm_err.h>
#include <lib/trusty/handle.h>
#include <lib/trusty/handle_set.h>
#include <lib/trusty/ipc.h>
#include <lib/trusty/ipc_msg.h>
#include <list.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <trace.h>

#define LOCAL_TRACE 0

/*
 *  Max number of sync tipc devices
 */
#define QL_TIPC_DEV_MAX_NUM 2

/*
 * Max number of opened channels supported
 */
#define QL_TIPC_ADDR_MAX_NUM 32

/*
 * Local addresses base
 */
#define QL_TIPC_ADDR_BASE 32

/*
 * Maximum service name size
 */
#define TIPC_MAX_SRV_NAME_LEN (256)

struct tipc_ept {
    struct handle* chan;
    struct handle_ref* ref;
    uint64_t cookie;
};

struct ql_tipc_dev {
    struct list_node node;
    struct handle* handle_set;
    bool in_use; /* protected by @_dev_list_lock */

    uint ns_mmu_flags;
    ns_size_t ns_sz;
    ext_mem_client_id_t client_id;
    ext_mem_obj_id_t buf_id;
    void* ns_va;
    const uuid_t* uuid;

    unsigned long inuse[BITMAP_NUM_WORDS(QL_TIPC_ADDR_MAX_NUM)];
    struct tipc_ept epts[QL_TIPC_ADDR_MAX_NUM];
};

struct tipc_cmd_hdr {
    uint16_t opcode;
    uint16_t flags;
    uint32_t status;
    uint32_t handle;
    uint32_t payload_len;
};

struct tipc_event {
    uint32_t event;
    uint32_t handle;
    uint64_t cookie;
};

struct tipc_wait_req {
    uint64_t reserved;
};

struct tipc_connect_req {
    uint64_t cookie;
    uint64_t reserved;
    uint8_t name[0];
};

#ifdef SPIN_LOCK_FLAG_IRQ_FIQ
#define SLOCK_FLAGS SPIN_LOCK_FLAG_IRQ_FIQ
#else
#define SLOCK_FLAGS SPIN_LOCK_FLAG_INTERRUPTS
#endif

static uint _dev_cnt;
/*
 * @_dev_list is only modified from stdcalls with _dev_list_lock held. It can
 * be read from any context with @_dev_list_lock held and from stdcalls without
 * the @_dev_list_lock held.
 */
static struct list_node _dev_list = LIST_INITIAL_VALUE(_dev_list);
/* @_dev_list_lock protects @_dev_list and @struct ql_tipc_dev->in_use */
static spin_lock_t _dev_list_lock = SPIN_LOCK_INITIAL_VALUE;

static inline uint addr_to_slot(uint32_t addr) {
    return (uint)(addr - QL_TIPC_ADDR_BASE);
}

static inline uint32_t slot_to_addr(uint slot) {
    return (uint32_t)(slot + QL_TIPC_ADDR_BASE);
}

static uint32_t alloc_local_addr(struct ql_tipc_dev* dev,
                                 struct handle* chan,
                                 uint64_t cookie) {
    int slot = bitmap_ffz(dev->inuse, QL_TIPC_ADDR_MAX_NUM);
    if (slot >= 0) {
        bitmap_set(dev->inuse, slot);
        dev->epts[slot].chan = chan;
        dev->epts[slot].cookie = cookie;
        return slot_to_addr(slot);
    }
    return 0;
}

static struct tipc_ept* ept_lookup(struct ql_tipc_dev* dev, uint32_t local) {
    uint slot = addr_to_slot(local);
    if (slot < QL_TIPC_ADDR_MAX_NUM) {
        if (bitmap_test(dev->inuse, slot)) {
            return &dev->epts[slot];
        }
    }
    return NULL;
}

static uint32_t ept_to_addr(struct ql_tipc_dev* dev, struct tipc_ept* ept) {
    return slot_to_addr(ept - dev->epts);
}

static void free_local_addr(struct ql_tipc_dev* dev, uint32_t local) {
    uint slot = addr_to_slot(local);

    if (slot < QL_TIPC_ADDR_MAX_NUM) {
        bitmap_clear(dev->inuse, slot);
        dev->epts[slot].chan = NULL;
        dev->epts[slot].cookie = 0;
    }
}

static struct ql_tipc_dev* dev_lookup(ext_mem_client_id_t client_id,
                                      ext_mem_obj_id_t buf_id) {
    struct ql_tipc_dev* dev;

    list_for_every_entry(&_dev_list, dev, struct ql_tipc_dev, node) {
        if (dev->client_id == client_id && dev->buf_id == buf_id) {
            return dev;
        }
    }
    return NULL;
}

static struct ql_tipc_dev* dev_acquire(ext_mem_client_id_t client_id,
                                       ext_mem_obj_id_t buf_id) {
    struct ql_tipc_dev* dev;
    spin_lock_saved_state_t state;

    spin_lock_save(&_dev_list_lock, &state, SLOCK_FLAGS);
    dev = dev_lookup(client_id, buf_id);
    if (dev) {
        if (dev->in_use) {
            TRACEF("0x%" PRIx64 ": device in use by another cpu\n", buf_id);
            dev = NULL;
        } else {
            dev->in_use = true;
        }
    }
    spin_unlock_restore(&_dev_list_lock, state, SLOCK_FLAGS);

    return dev;
}

static void dev_release(struct ql_tipc_dev* dev) {
    spin_lock_saved_state_t state;

    spin_lock_save(&_dev_list_lock, &state, SLOCK_FLAGS);
    DEBUG_ASSERT(dev->in_use);
    dev->in_use = false;
    spin_unlock_restore(&_dev_list_lock, state, SLOCK_FLAGS);
}

static long dev_create(ext_mem_client_id_t client_id,
                       ext_mem_obj_id_t buf_id,
                       ns_size_t buf_sz,
                       uint buf_mmu_flags) {
    status_t res;
    struct ql_tipc_dev* dev;
    spin_lock_saved_state_t state;

    dev = dev_lookup(client_id, buf_id);
    if (dev) {
        LTRACEF("0x%" PRIx64 ": device already exists\n", buf_id);
        return SM_ERR_INVALID_PARAMETERS;
    }

    if (!buf_sz) {
        LTRACEF("zero size shared buffer specified\n");
        return SM_ERR_INVALID_PARAMETERS;
    }

    if (buf_sz & (PAGE_SIZE - 1)) {
        LTRACEF("shared buffer size is not page aligned: 0x%x\n", buf_sz);
        return SM_ERR_INVALID_PARAMETERS;
    }

    if (_dev_cnt >= QL_TIPC_DEV_MAX_NUM) {
        LTRACEF("max number of devices reached: %d\n", _dev_cnt);
        return SM_ERR_NOT_ALLOWED;
    }

    dev = calloc(1, sizeof(*dev));
    if (!dev) {
        LTRACEF("out of memory creating sync tipc dev\n");
        return SM_ERR_INTERNAL_FAILURE;
    }

    dev->uuid = &zero_uuid;

    list_clear_node(&dev->node);
    dev->handle_set = handle_set_create();
    if (!dev->handle_set) {
        LTRACEF("out of memory creating handle_set\n");
        free(dev);
        return SM_ERR_INTERNAL_FAILURE;
    }

    /* map shared buffer into address space */
    dev->client_id = client_id;
    dev->buf_id = buf_id;
    dev->ns_sz = buf_sz;
    dev->ns_mmu_flags = buf_mmu_flags;
    res = ext_mem_map_obj_id(vmm_get_kernel_aspace(), "tipc", client_id, buf_id,
                             0, 0, round_up(buf_sz, PAGE_SIZE), &dev->ns_va,
                             PAGE_SIZE_SHIFT, 0, buf_mmu_flags);
    if (res != NO_ERROR) {
        LTRACEF("failed (%d) to map shared buffer\n", res);
        free(dev);
        return SM_ERR_INTERNAL_FAILURE;
    }

    spin_lock_save(&_dev_list_lock, &state, SLOCK_FLAGS);
    list_add_head(&_dev_list, &dev->node);
    spin_unlock_restore(&_dev_list_lock, state, SLOCK_FLAGS);
    _dev_cnt++;

    LTRACEF("tipc dev: %u bytes @ 0x%" PRIx64 ":0x%" PRIx64
            " (%p) (flags=0x%x)\n",
            dev->ns_sz, dev->client_id, dev->buf_id, dev->ns_va,
            dev->ns_mmu_flags);

    return 0;
}

static void dev_shutdown(struct ql_tipc_dev* dev) {
    spin_lock_saved_state_t state;

    DEBUG_ASSERT(dev);
    DEBUG_ASSERT(dev->ns_va);
    DEBUG_ASSERT(dev->in_use);

    /* remove from list */
    spin_lock_save(&_dev_list_lock, &state, SLOCK_FLAGS);
    list_delete(&dev->node);
    spin_unlock_restore(&_dev_list_lock, state, SLOCK_FLAGS);
    _dev_cnt--;

    /* unmap shared region */
    vmm_free_region(vmm_get_kernel_aspace(), (vaddr_t)dev->ns_va);
    dev->ns_va = NULL;

    /* close all channels */
    for (uint slot = 0; slot < countof(dev->epts); slot++) {
        struct tipc_ept* ept = &dev->epts[slot];

        if (!bitmap_test(dev->inuse, slot))
            continue;

        if (!ept->chan)
            continue;

        handle_set_detach_ref(ept->ref);
        handle_decref(ept->chan);
        free(ept->ref);
        ept->ref = NULL;
        handle_set_cookie(ept->chan, NULL);
        handle_close(ept->chan);
    }
    free(dev);
}

static long set_status(struct ql_tipc_dev* dev, int cmd, int err, size_t len) {
    struct tipc_cmd_hdr* ns_hdr = dev->ns_va;

    ns_hdr->status = (err < 0) ? 1 : 0;
    ns_hdr->payload_len = len;
    ns_hdr->opcode = cmd | QL_TIPC_DEV_RESP;

    smp_wmb();
    return err;
}

static int dev_connect(struct ql_tipc_dev* dev,
                       void* ns_payload,
                       size_t ns_payload_len) {
    int rc;
    uint32_t local = 0;
    struct handle* chan = NULL;
    int opcode = QL_TIPC_DEV_CONNECT;
    struct handle_ref* ref;
    struct tipc_cmd_hdr* ns_hdr = dev->ns_va;
    struct {
        struct tipc_connect_req hdr;
        uint8_t body[TIPC_MAX_SRV_NAME_LEN + 1];
    } req;

    if (ns_payload_len <= sizeof(req.hdr))
        return set_status(dev, opcode, ERR_INVALID_ARGS, 0);

    if (ns_payload_len - sizeof(req.hdr) >= sizeof(req.body))
        return set_status(dev, opcode, ERR_INVALID_ARGS, 0);

    /* copy out and zero terminate */
    memcpy(&req, ns_payload, ns_payload_len);
    req.body[ns_payload_len - sizeof(req.hdr)] = 0;

    /* open ipc channel */
    rc = ipc_port_connect_async(dev->uuid, (const char*)req.body,
                                ns_payload_len - sizeof(req.hdr), 0, &chan);
    if (rc != NO_ERROR) {
        TRACEF("failed to open ipc channel: %d\n", rc);
        return set_status(dev, opcode, rc, 0);
    }

    /* allocate slot */
    local = alloc_local_addr(dev, chan, req.hdr.cookie);
    if (local == 0) {
        TRACEF("failed to alloc local address\n");
        handle_close(chan);
        chan = NULL;
        return set_status(dev, opcode, ERR_NO_RESOURCES, 0);
    }

    LTRACEF("new handle: 0x%x\n", local);
    handle_set_cookie(chan, ept_lookup(dev, local));

    ref = calloc(1, sizeof(*ref));
    if (!ref) {
        rc = ERR_NO_MEMORY;
        goto err_alloc_ref;
    }

    handle_incref(chan);
    ref->handle = chan;
    ref->emask = ~0U;
    ref->cookie = ept_lookup(dev, local);
    ref->id = local;

    rc = handle_set_attach(dev->handle_set, ref);
    if (rc) {
        goto err_handle_set_attach;
    }
    ept_lookup(dev, local)->ref = ref;

    ns_hdr->handle = local;

    return set_status(dev, opcode, 0, 0);

err_handle_set_attach:
    handle_decref(chan);
    free(ref);
err_alloc_ref:
    free_local_addr(dev, local);
    handle_close(chan);
    chan = NULL;
    return set_status(dev, opcode, rc, 0);
}

static long dev_disconnect(struct ql_tipc_dev* dev, uint32_t target) {
    struct tipc_ept* ept;
    int opcode = QL_TIPC_DEV_DISCONNECT;

    ept = ept_lookup(dev, target);
    if (!ept || !ept->chan)
        return SM_ERR_INVALID_PARAMETERS;

    handle_set_detach_ref(ept->ref);
    handle_decref(ept->chan);
    free(ept->ref);
    ept->ref = NULL;
    handle_set_cookie(ept->chan, NULL);
    handle_close(ept->chan);
    free_local_addr(dev, target);

    return set_status(dev, opcode, 0, 0);
}

static long dev_send(struct ql_tipc_dev* dev,
                     void* ns_data,
                     size_t ns_sz,
                     uint32_t target) {
    int opcode = QL_TIPC_DEV_SEND;
    struct tipc_ept* ept = ept_lookup(dev, target);
    if (!ept || !ept->chan)
        return set_status(dev, opcode, ERR_INVALID_ARGS, 0);

    struct ipc_msg_kern msg = {
            .iov =
                    (struct iovec_kern[]){
                            [0] = {.iov_base = ns_data, .iov_len = ns_sz},
                    },
            .num_iov = 1,
            .num_handles = 0};

    return set_status(dev, opcode, ipc_send_msg(ept->chan, &msg), 0);
}

static long dev_recv(struct ql_tipc_dev* dev, uint32_t target) {
    int rc;
    int opcode = QL_TIPC_DEV_RECV;
    struct tipc_ept* ept = ept_lookup(dev, target);
    if (!ept || !ept->chan)
        return set_status(dev, opcode, ERR_INVALID_ARGS, 0);

    struct ipc_msg_info mi;
    rc = ipc_get_msg(ept->chan, &mi);
    if (rc < 0)
        return set_status(dev, opcode, rc, 0);

    struct ipc_msg_kern msg = {
            .iov =
                    (struct iovec_kern[]){
                            [0] = {.iov_base = dev->ns_va +
                                               sizeof(struct tipc_cmd_hdr),
                                   .iov_len = dev->ns_sz -
                                              sizeof(struct tipc_cmd_hdr)},
                    },
            .num_iov = 1,
            .num_handles = 0};

    rc = ipc_read_msg(ept->chan, mi.id, 0, &msg);
    ipc_put_msg(ept->chan, mi.id);

    if (rc < 0)
        return set_status(dev, opcode, rc, 0);
    if (rc < (int)mi.len)
        return set_status(dev, opcode, ERR_BAD_LEN, 0);

    return set_status(dev, opcode, rc, mi.len);
}

static long dev_has_event(struct ql_tipc_dev* dev,
                          void* ns_data,
                          size_t ns_sz,
                          uint32_t target) {
    bool* ready = (bool*)((uint8_t*)dev->ns_va + sizeof(struct tipc_cmd_hdr));
    *ready = handle_set_ready(dev->handle_set);
    return set_status(dev, QL_TIPC_DEV_FC_HAS_EVENT, 0, sizeof(*ready));
}

static long dev_get_event(struct ql_tipc_dev* dev,
                          void* ns_data,
                          size_t ns_sz,
                          uint32_t target)

{
    int rc;
    struct handle* chan;
    struct tipc_wait_req req;
    uint32_t chan_event = 0;
    struct tipc_ept* ept = NULL;
    int opcode = QL_TIPC_DEV_GET_EVENT;
    struct tipc_event* evt = (struct tipc_event*)((uint8_t*)dev->ns_va +
                                                  sizeof(struct tipc_cmd_hdr));

    if (ns_sz < sizeof(req))
        return set_status(dev, opcode, ERR_INVALID_ARGS, 0);

    if (target) {
        /* wait on specific handle */
        ept = ept_lookup(dev, target);
        if (!ept || !ept->chan)
            return set_status(dev, opcode, ERR_INVALID_ARGS, 0);

        chan = ept->chan;
        rc = handle_wait(chan, &chan_event, 0);
        if (rc == ERR_TIMED_OUT) {
            /* no events return an empty event */
            evt->handle = 0;
            evt->event = 0;
            evt->cookie = 0;
        } else if (rc < 0) {
            /* only possible if something is corrupted or somebody is
             * already waiting on the same handle
             */
            panic("%s: couldn't wait for handle events (%d)\n", __func__, rc);
        } else {
            /* got an event: return it */
            evt->handle = target;
            evt->event = chan_event;
            evt->cookie = ept->cookie;
        }
    } else {
        struct handle_ref hsevt;
        /* wait for event with 0-timeout */
        rc = handle_set_wait(dev->handle_set, &hsevt, 0);
        if (rc == ERR_NOT_FOUND) {
            /* no handles left */
            return set_status(dev, opcode, rc, 0);
        }

        if (rc < 0) {
            if (rc == ERR_TIMED_OUT) {
                /* no events: return an empty event */
                evt->handle = 0;
                evt->event = 0;
                evt->cookie = 0;
            } else {
                /* only possible if somebody else is waiting
                on the same handle which should never happen */
                panic("%s: couldn't wait for handle events (%d)\n", __func__,
                      rc);
            }
        } else {
            /* got an event: return it */
            ept = hsevt.cookie;

            evt->handle = ept_to_addr(dev, ept);
            evt->event = hsevt.emask;
            evt->cookie = ept->cookie;

            /* drop ref obtained by handle_set_wait */
            handle_decref(hsevt.handle);
        }
    }

    return set_status(dev, opcode, 0, sizeof(*evt));
}

static long dev_handle_fc_cmd(struct ql_tipc_dev* dev,
                              const struct tipc_cmd_hdr* cmd,
                              void* ns_payload) {
    DEBUG_ASSERT(dev);
    switch (cmd->opcode) {
    case QL_TIPC_DEV_FC_HAS_EVENT:
        return dev_has_event(dev, ns_payload, cmd->payload_len, cmd->handle);

    default:
        LTRACEF("0x%x: unhandled cmd\n", cmd->opcode);
        return set_status(dev, cmd->opcode, ERR_NOT_SUPPORTED, 0);
    }
}

static long dev_handle_cmd(struct ql_tipc_dev* dev,
                           const struct tipc_cmd_hdr* cmd,
                           void* ns_payload) {
    DEBUG_ASSERT(dev);

    switch (cmd->opcode) {
    case QL_TIPC_DEV_SEND:
        return dev_send(dev, ns_payload, cmd->payload_len, cmd->handle);

    case QL_TIPC_DEV_RECV:
        return dev_recv(dev, cmd->handle);

    case QL_TIPC_DEV_GET_EVENT:
        return dev_get_event(dev, ns_payload, cmd->payload_len, cmd->handle);

    case QL_TIPC_DEV_CONNECT:
        return dev_connect(dev, ns_payload, cmd->payload_len);

    case QL_TIPC_DEV_DISCONNECT:
        return dev_disconnect(dev, cmd->handle);

    default:
        LTRACEF("0x%x: unhandled cmd\n", cmd->opcode);
        return set_status(dev, cmd->opcode, ERR_NOT_SUPPORTED, 0);
    }
}

long ql_tipc_create_device(ext_mem_client_id_t client_id,
                           ext_mem_obj_id_t buf_id,
                           ns_size_t buf_sz,
                           uint buf_mmu_flags) {
    return dev_create(client_id, buf_id, buf_sz, buf_mmu_flags);
}

long ql_tipc_shutdown_device(ext_mem_client_id_t client_id,
                             ext_mem_obj_id_t buf_id) {
    struct ql_tipc_dev* dev = dev_acquire(client_id, buf_id);
    if (!dev) {
        LTRACEF("0x%" PRIx64 ": device not found\n", buf_id);
        return SM_ERR_INVALID_PARAMETERS;
    }
    dev_shutdown(dev);
    return 0;
}

long ql_tipc_handle_cmd(ext_mem_client_id_t client_id,
                        ext_mem_obj_id_t buf_id,
                        ns_size_t cmd_sz,
                        bool is_fc) {
    long ret = SM_ERR_INVALID_PARAMETERS;
    struct tipc_cmd_hdr cmd_hdr;

    /* lookup device */
    struct ql_tipc_dev* dev = dev_acquire(client_id, buf_id);
    if (!dev) {
        TRACEF("0x%" PRIx64 ": device not found\n", buf_id);
        goto err_not_found;
    }

    /* check for minimum size */
    if (cmd_sz < sizeof(cmd_hdr)) {
        TRACEF("message is too short (%zu)\n", (size_t)cmd_sz);
        goto err_invalid;
    }

    /* copy out command header */
    memcpy(&cmd_hdr, dev->ns_va, sizeof(cmd_hdr));

    /* check for consistency */
    if (cmd_hdr.payload_len != (cmd_sz - sizeof(cmd_hdr))) {
        TRACEF("malformed command\n");
        goto err_invalid;
    }

    if (is_fc) {
        ret = dev_handle_fc_cmd(dev, &cmd_hdr, dev->ns_va + sizeof(cmd_hdr));
    } else {
        ret = dev_handle_cmd(dev, &cmd_hdr, dev->ns_va + sizeof(cmd_hdr));
    }
err_invalid:
    dev_release(dev);
err_not_found:
    return ret;
}
