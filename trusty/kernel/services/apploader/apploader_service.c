/*
 * Copyright (c) 2020, Google Inc. All rights reserved
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

#include <err.h>
#include <interface/apploader/apploader.h>
#include <interface/apploader/apploader_secure.h>
#include <inttypes.h>
#include <kernel/thread.h>
#include <kernel/vm.h>
#include <lib/ktipc/ktipc.h>
#include <lib/trusty/handle_set.h>
#include <lib/trusty/ipc.h>
#include <lib/trusty/ipc_msg.h>
#include <lib/trusty/memref.h>
#include <lk/err_ptr.h>
#include <lk/init.h>
#include <lk/trace.h>
#include <string.h>
#include <uapi/mm.h>

struct apploader_channel_ctx {
    struct vmm_obj_slice vmm_obj_slice;
};

/* UUID: {081ba88f-f1ee-452e-b5e8-a7e9ef173a97} */
static const struct uuid apploader_user_uuid = {
        0x081ba88f,
        0xf1ee,
        0x452e,
        {0xb5, 0xe8, 0xa7, 0xe9, 0xef, 0x17, 0x3a, 0x97},
};

#if TEST_BUILD
/* UUID: {c549cb7c-f8dc-4063-8661-ef34fb3be6fc} */
static const struct uuid apploader_unittest_uuid = {
        0xc549cb7c,
        0xf8dc,
        0x4063,
        {0x86, 0x61, 0xef, 0x34, 0xfb, 0x3b, 0xe6, 0xfc},
};
#endif

const static struct uuid* apploader_service_uuids[] = {
        &apploader_user_uuid,
#if TEST_BUILD
        &apploader_unittest_uuid,
#endif
};

struct apploader_secure_req {
    struct apploader_secure_header hdr;
    union {
        struct apploader_secure_get_memory_req get_memory_req;
        struct apploader_secure_load_app_req load_app_req;
    };
} __PACKED;

/*
 * Common structure covering all possible apploader messages, only used to
 * determine the maximum message size
 */
union apploader_longest_secure_msg {
    struct apploader_secure_req req;
    struct apploader_secure_resp resp;
} __PACKED;

static int apploader_service_translate_error(status_t rc) {
    switch (rc) {
    case ERR_NO_MEMORY:
        return APPLOADER_ERR_NO_MEMORY;
    case ERR_ALREADY_EXISTS:
        return APPLOADER_ERR_ALREADY_EXISTS;
    default:
        TRACEF("%s: unrecognized error (%d)\n", __func__, rc);
        return APPLOADER_ERR_INTERNAL;
    }
}

static int apploader_service_send_response(struct handle* chan,
                                           uint32_t cmd,
                                           uint32_t error,
                                           struct handle** handles,
                                           uint32_t num_handles) {
    struct apploader_secure_resp resp = {
            .hdr =
                    {
                            .cmd = cmd | APPLOADER_SECURE_RESP_BIT,
                    },
            .error = error,
    };

    int rc =
            ktipc_send_handles(chan, handles, num_handles, &resp, sizeof(resp));
    if (rc != (int)sizeof(resp)) {
        return ERR_BAD_LEN;
    }

    return NO_ERROR;
}

static int apploader_service_handle_cmd_get_memory(
        struct handle* chan,
        struct apploader_channel_ctx* channel_ctx,
        struct apploader_secure_get_memory_req* req) {
    int rc;
    uint32_t resp_error;

    if (channel_ctx->vmm_obj_slice.obj) {
        TRACEF("%s: client already holds a memref\n", __func__);
        resp_error = APPLOADER_ERR_INVALID_CMD;
        goto err_chan_has_memref;
    }

    if (!req->package_size) {
        TRACEF("%s: 0-sized GET_MEMORY request\n", __func__);
        resp_error = APPLOADER_ERR_INVALID_CMD;
        goto err_zero_size;
    }

    uint64_t aligned_size = round_up(req->package_size, PAGE_SIZE);
    LTRACEF("Handling GET_MEMORY command, package size %" PRIu64
            " bytes, %" PRIu64 " aligned\n",
            req->package_size, aligned_size);

    struct vmm_obj* vmm_obj;
    struct obj_ref vmm_obj_ref = OBJ_REF_INITIAL_VALUE(vmm_obj_ref);
    rc = pmm_alloc(&vmm_obj, &vmm_obj_ref, aligned_size / PAGE_SIZE, 0, 0);
    if (rc != NO_ERROR) {
        TRACEF("%s: error (%d) allocating memory\n", __func__, rc);
        resp_error = apploader_service_translate_error(rc);
        goto err_alloc;
    }

    struct handle* memref_handle;
    uint32_t prot_flags = MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE;
    rc = memref_create_from_vmm_obj(vmm_obj, 0, aligned_size, prot_flags,
                                    &memref_handle);
    if (rc != NO_ERROR) {
        TRACEF("%s: error (%d) creating memref\n", __func__, rc);
        resp_error = apploader_service_translate_error(rc);
        goto err_memref_create;
    }

    rc = apploader_service_send_response(chan, APPLOADER_SECURE_CMD_GET_MEMORY,
                                         APPLOADER_NO_ERROR, &memref_handle, 1);
    if (rc < 0) {
        TRACEF("%s: error (%d) sending response\n", __func__, rc);
    } else {
        vmm_obj_slice_bind(&channel_ctx->vmm_obj_slice, vmm_obj, 0,
                           aligned_size);
    }

    handle_decref(memref_handle);
    vmm_obj_del_ref(vmm_obj, &vmm_obj_ref);

    return rc;

err_memref_create:
    vmm_obj_del_ref(vmm_obj, &vmm_obj_ref);
err_alloc:
err_zero_size:
err_chan_has_memref:
    return apploader_service_send_response(
            chan, APPLOADER_SECURE_CMD_GET_MEMORY, resp_error, NULL, 0);
}

static int apploader_service_handle_cmd_load_application(
        struct handle* chan,
        struct apploader_channel_ctx* channel_ctx,
        struct apploader_secure_load_app_req* req) {
    int rc;
    uint32_t resp_error;

    if (!channel_ctx->vmm_obj_slice.obj) {
        TRACEF("%s: invalid handle\n", __func__);
        resp_error = APPLOADER_ERR_INVALID_CMD;
        goto err_invalid_handle;
    }

    if (!vmm_obj_has_only_ref(channel_ctx->vmm_obj_slice.obj,
                              &channel_ctx->vmm_obj_slice.obj_ref)) {
        TRACEF("%s: service not holding single reference to memref\n",
               __func__);
        resp_error = APPLOADER_ERR_INVALID_CMD;
        goto err_invalid_refcount;
    }

    if (req->manifest_start >= req->manifest_end ||
        req->manifest_end > channel_ctx->vmm_obj_slice.size) {
        TRACEF("%s: received invalid manifest offsets: 0x%" PRIx64 "-0x%" PRIx64
               "\n",
               __func__, req->manifest_start, req->manifest_end);
        resp_error = APPLOADER_ERR_INVALID_CMD;
        goto err_invalid_manifest_offsets;
    }

    if (req->img_start >= req->img_end ||
        req->img_end > channel_ctx->vmm_obj_slice.size) {
        TRACEF("%s: received invalid image offsets: 0x%" PRIx64 "-0x%" PRIx64
               "\n",
               __func__, req->img_start, req->img_end);
        resp_error = APPLOADER_ERR_INVALID_CMD;
        goto err_invalid_image_offsets;
    }

    LTRACEF("Handling LOAD_APPLICATION command, package size %zd bytes\n",
            channel_ctx->vmm_obj_slice.size);

    void* va;
    rc = vmm_alloc_obj(vmm_get_kernel_aspace(), "app package",
                       channel_ctx->vmm_obj_slice.obj, 0,
                       channel_ctx->vmm_obj_slice.size, &va, 0, 0,
                       ARCH_MMU_FLAG_PERM_NO_EXECUTE | ARCH_MMU_FLAG_PERM_RO);
    if (rc != NO_ERROR) {
        TRACEF("%s: error (%d) allocation memory for vmm object\n", __func__,
               rc);
        resp_error = apploader_service_translate_error(rc);
        goto err_alloc_app;
    }

    struct trusty_app_img* app_img = calloc(1, sizeof(struct trusty_app_img));
    if (!app_img) {
        TRACEF("%s: error (%d) allocating struct trusty_app_img\n", __func__,
               rc);
        resp_error = APPLOADER_ERR_NO_MEMORY;
        goto err_alloc_app_img;
    }

    if (__builtin_add_overflow((uintptr_t)va, req->manifest_start,
                               &app_img->manifest_start) ||
        __builtin_add_overflow((uintptr_t)va, req->manifest_end,
                               &app_img->manifest_end) ||
        __builtin_add_overflow((uintptr_t)va, req->img_start,
                               &app_img->img_start) ||
        __builtin_add_overflow((uintptr_t)va, req->img_end,
                               &app_img->img_end)) {
        TRACEF("%s: overflow when computing trusty_app pointers\n", __func__);
        resp_error = APPLOADER_ERR_LOADING_FAILED;
        goto err_trusty_app_overflow;
    }

    rc = trusty_app_create_and_start(app_img, APP_FLAGS_LOADABLE);
    if (rc < 0) {
        TRACEF("%s: error (%d) creating Trusty app\n", __func__, rc);
        if (rc == ERR_NOT_VALID) {
            resp_error = APPLOADER_ERR_LOADING_FAILED;
        } else {
            resp_error = apploader_service_translate_error(rc);
        }
        goto err_create_app;
    }

    /* Release the slice to prevent clients from loading the app twice */
    vmm_obj_slice_release(&channel_ctx->vmm_obj_slice);

    return apploader_service_send_response(
            chan, APPLOADER_SECURE_CMD_LOAD_APPLICATION, APPLOADER_NO_ERROR,
            NULL, 0);

err_create_app:
err_trusty_app_overflow:
    free(app_img);
err_alloc_app_img:
    vmm_free_region(vmm_get_kernel_aspace(), (vaddr_t)va);
err_alloc_app:
err_invalid_image_offsets:
err_invalid_manifest_offsets:
err_invalid_refcount:
    vmm_obj_slice_release(&channel_ctx->vmm_obj_slice);
err_invalid_handle:
    return apploader_service_send_response(
            chan, APPLOADER_SECURE_CMD_LOAD_APPLICATION, resp_error, NULL, 0);
}

static int apploader_service_handle_msg(const struct ktipc_port* port,
                                        struct handle* chan,
                                        void* ctx) {
    struct apploader_channel_ctx* channel_ctx = ctx;
    int rc;
    struct apploader_secure_req req;
    rc = ktipc_recv(chan, sizeof(req.hdr), &req, sizeof(req));
    if (rc < 0) {
        TRACEF("%s: failed (%d) to read apploader request\n", __func__, rc);
        return rc;
    }

    size_t cmd_len;
    switch (req.hdr.cmd) {
    case APPLOADER_SECURE_CMD_GET_MEMORY:
        /* Check the message length */
        cmd_len = sizeof(req.hdr) + sizeof(req.get_memory_req);
        if (rc != (int)cmd_len) {
            TRACEF("%s: expected to read %zu bytes, got %d\n", __func__,
                   cmd_len, rc);
            rc = apploader_service_send_response(
                    chan, req.hdr.cmd, APPLOADER_ERR_INVALID_CMD, NULL, 0);
            break;
        }

        rc = apploader_service_handle_cmd_get_memory(chan, channel_ctx,
                                                     &req.get_memory_req);
        break;

    case APPLOADER_SECURE_CMD_LOAD_APPLICATION:
        /* Check the message length */
        cmd_len = sizeof(req.hdr) + sizeof(req.load_app_req);
        if (rc != (int)cmd_len) {
            TRACEF("%s: expected to read %zu bytes, got %d\n", __func__,
                   cmd_len, rc);
            rc = apploader_service_send_response(
                    chan, req.hdr.cmd, APPLOADER_ERR_INVALID_CMD, NULL, 0);
            break;
        }

        rc = apploader_service_handle_cmd_load_application(chan, channel_ctx,
                                                           &req.load_app_req);
        break;

    default:
        TRACEF("%s: received unknown apploader service command: %" PRIu32 "\n",
               __func__, req.hdr.cmd);
        rc = apploader_service_send_response(
                chan, req.hdr.cmd, APPLOADER_ERR_UNKNOWN_CMD, NULL, 0);
        break;
    }

    if (rc < 0) {
        TRACEF("%s: failed to run command (%d)\n", __func__, rc);
    }

    return rc;
}

static int apploader_service_handle_connect(const struct ktipc_port* port,
                                            struct handle* chan,
                                            const struct uuid* peer,
                                            void** ctx_p) {
    struct apploader_channel_ctx* channel_ctx = calloc(1, sizeof(*channel_ctx));
    if (!channel_ctx) {
        TRACEF("%s: failed to allocate apploader_channel_ctx\n", __func__);
        return ERR_NO_MEMORY;
    }

    vmm_obj_slice_init(&channel_ctx->vmm_obj_slice);

    *ctx_p = channel_ctx;

    return NO_ERROR;
}

static void apploader_service_handle_channel_cleanup(void* ctx) {
    struct apploader_channel_ctx* channel_ctx = ctx;

    vmm_obj_slice_release(&channel_ctx->vmm_obj_slice);
    free(channel_ctx);
}

const static struct ktipc_srv_ops apploader_service_ops = {
        .on_connect = apploader_service_handle_connect,
        .on_message = apploader_service_handle_msg,
        .on_channel_cleanup = apploader_service_handle_channel_cleanup,
};

const static struct ktipc_port_acl apploader_service_port_acl = {
        .flags = IPC_PORT_ALLOW_TA_CONNECT,
        .uuids = apploader_service_uuids,
        .uuid_num = countof(apploader_service_uuids),
        .extra_data = NULL,
};

const static struct ktipc_port apploader_service_port = {
        .name = APPLOADER_SECURE_PORT,
        .uuid = &kernel_uuid,
        .msg_max_size = sizeof(union apploader_longest_secure_msg),
        .msg_queue_len = 1,
        .acl = &apploader_service_port_acl,
        .priv = NULL,
};

static struct ktipc_server apploader_ktipc_server =
        KTIPC_SERVER_INITIAL_VALUE(apploader_ktipc_server,
                                   "apploader_ktipc_server");

static void apploader_service_init(uint level) {
    int rc;

    rc = ktipc_server_start(&apploader_ktipc_server);
    if (rc < 0) {
        panic("Failed (%d) to start apploader server\n", rc);
    }

    rc = ktipc_server_add_port(&apploader_ktipc_server, &apploader_service_port,
                               &apploader_service_ops);
    if (rc < 0) {
        panic("Failed (%d) to create apploader port\n", rc);
    }
}

LK_INIT_HOOK(apploader, apploader_service_init, LK_INIT_LEVEL_APPS + 1);
