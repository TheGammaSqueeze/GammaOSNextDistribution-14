/*
 * Copyright (c) 2022, Google Inc. All rights reserved
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

#include <err.h>
#include <kernel/vm.h>
#include <lib/ktipc/ktipc.h>
#include <lib/trusty/ipc.h>
#include <lib/trusty/memref.h>
#include <lk/compiler.h>
#include <lk/trace.h>
#include <uapi/mm.h>

struct vmm_obj_service {
    struct ktipc_port port;
    struct handle* memref;
    size_t size;
};

int vmm_obj_service_create_ro(const char* port,
                              struct ktipc_port_acl* acl,
                              struct vmm_obj* obj,
                              size_t offset,
                              size_t size,
                              struct vmm_obj_service** srv_out) {
    int rc;
    struct vmm_obj_service* srv;

    if (!srv_out) {
        TRACEF("vmm_obj_service_create got NULL pointer");
        rc = ERR_INVALID_ARGS;
        goto err_null_srv_out;
    }
    if (!IS_PAGE_ALIGNED(offset)) {
        TRACEF("unaligned offset: %zx\n", offset);
        rc = ERR_INVALID_ARGS;
        goto err_unaligned_offset;
    }
    if (!IS_PAGE_ALIGNED(size)) {
        TRACEF("unaligned size: %zd\n", size);
        rc = ERR_INVALID_ARGS;
        goto err_unaligned_size;
    }
    if (size != (uint64_t)size) {
        TRACEF("size too big: %zd\n", size);
        rc = ERR_TOO_BIG;
        goto err_size_too_big;
    }

    srv = calloc(1, sizeof(*srv));
    if (!srv) {
        TRACEF("failed to allocate vmm_obj_service\n");
        rc = ERR_NO_MEMORY;
        goto err_alloc_srv;
    }

    rc = memref_create_from_vmm_obj(obj, offset, size, MMAP_FLAG_PROT_READ,
                                    &srv->memref);
    if (rc != NO_ERROR) {
        TRACEF("error (%d) creating memref\n", rc);
        goto err_memref_create;
    }

    srv->port.name = port;
    srv->port.uuid = &kernel_uuid;
    srv->port.msg_max_size = sizeof(uint64_t);
    srv->port.msg_queue_len = 1;
    srv->port.acl = acl;
    srv->size = size;

    *srv_out = srv;
    return NO_ERROR;

err_memref_create:
    free(srv);
err_alloc_srv:
err_size_too_big:
err_unaligned_size:
err_unaligned_offset:
err_null_srv_out:
    return rc;
}

void vmm_obj_service_destroy(struct vmm_obj_service** srv) {
    DEBUG_ASSERT(srv);
    if (!*srv) {
        TRACEF("tried to destroy an uninitialized object");
        return;
    }

    handle_decref((*srv)->memref);
    free(*srv);
    *srv = NULL;
}

static int vmm_obj_service_handle_msg(const struct ktipc_port* port,
                                      struct handle* channel,
                                      void* ctx) {
    TRACEF("unexpected message");
    return ERR_NOT_SUPPORTED;
}

static int vmm_obj_service_handle_connect(const struct ktipc_port* port,
                                          struct handle* chan,
                                          const struct uuid* peer,
                                          void** ctx_p) {
    struct vmm_obj_service* srv =
            containerof(port, struct vmm_obj_service, port);
    int rc;

    struct handle* handles[] = {srv->memref};
    uint64_t out_size = (uint64_t)srv->size;
    rc = ktipc_send_handles(chan, handles, countof(handles), &out_size,
                            sizeof(out_size));
    if ((size_t)rc != sizeof(out_size)) {
        TRACEF("failed (%d) to send response\n", rc);
        if (rc >= 0) {
            return ERR_BAD_LEN;
        }
        return rc;
    }

    return NO_ERROR;
}

static void vmm_obj_service_handle_channel_cleanup(void* ctx) {}

const static struct ktipc_srv_ops vmm_obj_service_ops = {
        .on_message = vmm_obj_service_handle_msg,
        .on_connect = vmm_obj_service_handle_connect,
        .on_channel_cleanup = vmm_obj_service_handle_channel_cleanup,
};

int vmm_obj_service_add(struct vmm_obj_service* srv,
                        struct ktipc_server* server) {
    return ktipc_server_add_port(server, &srv->port, &vmm_obj_service_ops);
}
