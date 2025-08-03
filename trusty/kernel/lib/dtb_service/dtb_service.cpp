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
#include <lib/dtb_service/dtb_service.h>
#include <lib/ktipc/ktipc.h>
#include <lib/shared/binder_discover/binder_discover.h>
#include <lib/shared/device_tree/service/device_tree_service.h>
#include <lib/trusty/ipc.h>
#include <lib/vmm_obj_service/vmm_obj_service.h>
#include <libfdt.h>
#include <lk/trace.h>
#include <string.h>

#define LOCAL_TRACE (0)

/* UUID: 185b4dbc-8935-4a1e-89ee-df027b89bc7a */
const static struct uuid device_tree_service_uuid = {
        0x185b4dbc,
        0x8935,
        0x4a1e,
        {0x89, 0xee, 0xdf, 0x02, 0x7b, 0x89, 0xbc, 0x7a},
};

const static struct uuid* dtb_service_uuids[] = {
        &device_tree_service_uuid,
};

const static struct ktipc_port_acl dtb_service_port_acl = {
        .flags = IPC_PORT_ALLOW_TA_CONNECT,
        .uuid_num = countof(dtb_service_uuids),
        .uuids = dtb_service_uuids,
        .extra_data = NULL,
};

static int dtb_service_add_user(const void* dtb,
                                size_t dtb_size,
                                const char* dtb_port,
                                struct ktipc_server* server) {
    int rc;
    vmm_aspace_t* kas = vmm_get_kernel_aspace();
    struct vmm_obj_slice slice = VMM_OBJ_SLICE_INITIAL_VALUE(slice);

    uint64_t aligned_size = round_up(dtb_size, PAGE_SIZE);
    void* dtb_copy;
    rc = vmm_alloc(kas, "dtb copy", aligned_size, &dtb_copy, PAGE_SIZE_SHIFT, 0,
                   ARCH_MMU_FLAG_PERM_NO_EXECUTE);
    if (rc != NO_ERROR) {
        TRACEF("error allocating memory (%d)\n", rc);
        goto err_alloc;
    }

    /*
     * We need to make a copy because calling memref_create_from_aspace
     * directly on dtb returns ERR_OUT_OF_RANGE because there is no backing
     * vmm_obj for the kernel image
     */
    rc = fdt_move(dtb, dtb_copy, aligned_size);
    if (rc) {
        TRACEF("failed (%d) to move fdt\n", rc);
        goto err_fdt_move;
    }

    rc = vmm_get_obj(kas, (vaddr_t)dtb_copy, aligned_size, &slice);
    if (rc < 0) {
        TRACEF("failed (%d) to get vmm_obj\n", rc);
        goto err_get_obj;
    }

    struct vmm_obj_service* srv;
    rc = vmm_obj_service_create_ro(dtb_port, &dtb_service_port_acl, slice.obj,
                                   slice.offset, slice.size, &srv);
    if (rc < 0) {
        TRACEF("failed (%d) to create vmm_obj_service\n", rc);
        goto err_create_service;
    }

    rc = vmm_obj_service_add(srv, server);
    if (rc < 0) {
        TRACEF("error (%d) adding new service\n", rc);
        goto err_add_service;
    }

    /* vmm_obj_service_create_ro incremented the reference count of slice.obj */
    vmm_obj_slice_release(&slice);
    /* We can free the allocation now that we have the slice */
    vmm_free_region(kas, (vaddr_t)dtb_copy);
    return NO_ERROR;

err_add_service:
    vmm_obj_service_destroy(&srv);
err_create_service:
    vmm_obj_slice_release(&slice);
err_get_obj:
err_fdt_move:
    vmm_free_region(kas, (vaddr_t)dtb_copy);
err_alloc:
    return rc;
}

int dtb_service_add(const void* dtb,
                    size_t dtb_size,
                    const char* dt_port,
                    const char* dtb_port,
                    struct ktipc_server* server) {
    if (!dtb) {
        TRACEF("invalid dtb pointer\n");
        return ERR_INVALID_ARGS;
    }
    if (!dtb_size) {
        TRACEF("invalid dtb size\n");
        return ERR_INVALID_ARGS;
    }
    if (fdt_check_full(dtb, dtb_size)) {
        TRACEF("invalid dtb contents");
        return ERR_INVALID_ARGS;
    }
    if (!dt_port) {
        TRACEF("invalid kernel port name\n");
        return ERR_INVALID_ARGS;
    }
    if (!dtb_port) {
        TRACEF("invalid user port name\n");
        return ERR_INVALID_ARGS;
    }
    if (!server) {
        TRACEF("invalid server pointer\n");
        return ERR_INVALID_ARGS;
    }

    auto dt = android::sp<com::android::trusty::device_tree::DeviceTree>::make(
            static_cast<const unsigned char*>(dtb), dtb_size);
    int err = binder_discover_add_service(dt_port, dt);
    if (err != android::OK) {
        TRACEF("error adding service (%d)\n", err);
        return ERR_GENERIC;
    }

    int rc = dtb_service_add_user(dtb, dtb_size, dtb_port, server);
    if (rc < 0) {
        binder_discover_remove_service(dt_port);
        return rc;
    }

    return NO_ERROR;
}
