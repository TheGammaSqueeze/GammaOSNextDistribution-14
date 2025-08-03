/*
 * Copyright 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define TLOG_TAG "secure_fb_service"

#include <assert.h>
#include <interface/secure_fb/secure_fb.h>
#include <lib/secure_fb/srv/dev.h>
#include <lib/secure_fb/srv/srv.h>
#include <lib/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/compiler.h>
#include <lk/macros.h>
#include <stdlib.h>
#include <string.h>
#include <trusty_ipc.h>
#include <trusty_log.h>
#include <uapi/err.h>

struct secure_fb_ctx {
    secure_fb_handle_t session;
    const struct secure_fb_impl_ops* ops;
};

/* UUID: {7dee2364-c036-425b-b086-df0f6c233c1b} */
static const struct uuid confirmationui_uuid = {
        0x7dee2364,
        0xc036,
        0x425b,
        {0xb0, 0x86, 0xdf, 0x0f, 0x6c, 0x23, 0x3c, 0x1b},
};

/* UUID: {e181673c-7d7b-4b98-b962-5a3e6d59d855} */
static const struct uuid secure_fb_test_uuid = {
        0xe181673c,
        0x7d7b,
        0x4b98,
        {0xb9, 0x62, 0x5a, 0x3e, 0x6d, 0x59, 0xd8, 0x55},
};

/*
 * TODO: We'll need to configure ACL in a device-specific manner, but right now
 * we know/control all the clients of this service.
 */
static const struct uuid* allowed_uuids[] = {
        &confirmationui_uuid,
        &secure_fb_test_uuid,
};

static int secure_fb_check_impl_ops(const struct secure_fb_impl_ops* ops) {
    if (!ops->init || !ops->get_fbs || !ops->display_fb || !ops->release) {
        TLOGE("NULL ops pointers\n");
        return ERR_INVALID_ARGS;
    }
    return NO_ERROR;
}

static int secure_fb_on_connect(const struct tipc_port* port,
                                handle_t chan,
                                const struct uuid* peer,
                                void** ctx_p) {
    struct secure_fb_ctx* ctx = malloc(sizeof(*ctx));
    if (ctx == NULL) {
        TLOGE("Memory allocation failed.\n");
        return ERR_NO_MEMORY;
    }

    ctx->ops = (const struct secure_fb_impl_ops*)port->priv;
    assert(ctx->ops);
    ctx->session = ctx->ops->init();
    if (ctx->session == NULL) {
        TLOGE("Driver initialization failed.\n");
        free(ctx);
        return ERR_GENERIC;
    }

    *ctx_p = ctx;
    return NO_ERROR;
}

static void secure_fb_on_channel_cleanup(void* _ctx) {
    struct secure_fb_ctx* ctx = (struct secure_fb_ctx*)_ctx;
    if (ctx->session != NULL) {
        ctx->ops->release(ctx->session);
    }
    free(ctx);
}

static int handle_get_fbs_req(handle_t chan, struct secure_fb_ctx* ctx) {
    int rc;
    struct secure_fb_impl_buffers buffers;
    struct secure_fb_resp hdr;
    struct secure_fb_get_fbs_resp args;
    struct secure_fb_desc fbs[SECURE_FB_MAX_FBS];
    size_t fbs_len;
    secure_fb_handle_t session = ctx->session;

    rc = ctx->ops->get_fbs(session, &buffers);
    if (rc != SECURE_FB_ERROR_OK) {
        TLOGE("Failed secure_fb_impl_get_fbs() (%d)\n", rc);

        /* Ensure nothing is returned, but continue to send error code */
        buffers.num_fbs = 0;
        buffers.num_handles = 0;
    }

    hdr.cmd = SECURE_FB_CMD_GET_FBS | SECURE_FB_CMD_RESP_BIT;
    hdr.status = rc;

    args.num_fbs = buffers.num_fbs;

    fbs_len = sizeof(fbs[0]) * args.num_fbs;
    memcpy(fbs, buffers.fbs, fbs_len);

    struct iovec iovs[] = {
            {
                    .iov_base = &hdr,
                    .iov_len = sizeof(hdr),
            },
            {
                    .iov_base = &args,
                    .iov_len = sizeof(args),
            },
            {
                    .iov_base = fbs,
                    .iov_len = fbs_len,
            },
    };
    ipc_msg_t msg = {
            .num_iov = countof(iovs),
            .iov = iovs,
            .num_handles = buffers.num_handles,
            .handles = buffers.handles,
    };
    rc = send_msg(chan, &msg);
    if (rc != (int)(sizeof(hdr) + sizeof(args) + fbs_len)) {
        TLOGE("Failed to send SECURE_FB_CMD_GET_FBS response (%d)\n", rc);
        if (rc >= 0) {
            return ERR_BAD_LEN;
        }
    }

    return NO_ERROR;
}

static int handle_display_fb(handle_t chan,
                             struct secure_fb_display_fb_req* display_fb,
                             struct secure_fb_ctx* ctx) {
    int rc;
    struct secure_fb_resp hdr;
    secure_fb_handle_t session = ctx->session;

    rc = ctx->ops->display_fb(session, display_fb->buffer_id);
    if (rc != SECURE_FB_ERROR_OK) {
        TLOGE("Failed secure_fb_impl_display_fb() (%d)\n", rc);

        /* Continue to return error code */
    }

    hdr.cmd = SECURE_FB_CMD_DISPLAY_FB | SECURE_FB_CMD_RESP_BIT;
    hdr.status = rc;

    rc = tipc_send1(chan, &hdr, sizeof(hdr));
    if (rc != (int)sizeof(hdr)) {
        TLOGE("Failed to send SECURE_FB_CMD_DISPLAY_FB response (%d)\n", rc);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        return rc;
    }

    return NO_ERROR;
}

static int secure_fb_on_message(const struct tipc_port* port,
                                handle_t chan,
                                void* _ctx) {
    int rc;
    struct {
        struct secure_fb_req hdr;
        union {
            struct secure_fb_display_fb_req display_fb;
        };
    } req;
    struct secure_fb_ctx* ctx = (struct secure_fb_ctx*)_ctx;

    rc = tipc_recv1(chan, sizeof(req.hdr), &req, sizeof(req));
    if (rc < 0) {
        TLOGE("Failed to read command %d\n", rc);
        return ERR_BAD_LEN;
    }

    switch (req.hdr.cmd) {
    case SECURE_FB_CMD_GET_FBS:
        if (rc != (int)sizeof(req.hdr)) {
            TLOGE("Failed to read SECURE_FB_CMD_GET_FBS request (%d)\n", rc);
            return ERR_BAD_LEN;
        }
        return handle_get_fbs_req(chan, ctx);

    case SECURE_FB_CMD_DISPLAY_FB:
        if (rc != (int)(sizeof(req.hdr) + sizeof(req.display_fb))) {
            TLOGE("Failed to read SECURE_FB_CMD_DISPLAY_FB request (%d)\n", rc);
            return ERR_BAD_LEN;
        }
        return handle_display_fb(chan, &req.display_fb, ctx);

    case SECURE_FB_CMD_RELEASE:
        if (rc != (int)sizeof(req.hdr)) {
            TLOGE("Failed to read SECURE_FB_CMD_RELEASE request (%d)\n", rc);
            return ERR_BAD_LEN;
        }
        ctx->ops->release(ctx->session);
        ctx->session = NULL;
        return NO_ERROR;

    default:
        TLOGW("Received unknown command %x\n", req.hdr.cmd);
        return ERR_CMD_UNKNOWN;
    }

    return NO_ERROR;
}

int add_secure_fb_service(struct tipc_hset* hset,
                          const struct secure_fb_impl_ops* impl_ops,
                          uint32_t num_ops) {
    int rc;
    uint32_t i;
    char* port_name_base;

    if (!hset || !impl_ops) {
        TLOGE("NULL pointer arguments\n");
        return ERR_INVALID_ARGS;
    }
    if (num_ops > SECURE_FB_MAX_INST) {
        TLOGE("Number of instance exceeds the limitation\n");
        return ERR_INVALID_ARGS;
    }
    for (i = 0; i < num_ops; ++i) {
        if ((rc = secure_fb_check_impl_ops(&impl_ops[i])) != NO_ERROR) {
            TLOGE("Failed to check impl ops\n");
            return rc;
        }
    }

    static struct tipc_port_acl acl = {
            .flags = IPC_PORT_ALLOW_TA_CONNECT,
            .uuids = allowed_uuids,
            .uuid_num = countof(allowed_uuids),
    };

    static struct tipc_srv_ops ops = {
            .on_connect = secure_fb_on_connect,
            .on_message = secure_fb_on_message,
            .on_channel_cleanup = secure_fb_on_channel_cleanup,
    };

    struct tipc_port* port = calloc(num_ops, sizeof(struct tipc_port));
    if (port == NULL) {
        TLOGE("Memory allocation failed.\n");
        rc = ERR_NO_MEMORY;
        goto fail_port_alloc;
    }
    port_name_base =
            calloc(num_ops, sizeof(char) * SECURE_FB_MAX_PORT_NAME_SIZE);
    if (port_name_base == NULL) {
        TLOGE("Memory allocation failed.\n");
        rc = ERR_NO_MEMORY;
        goto fail_port_name_alloc;
    }

    for (i = 0; i < num_ops; ++i) {
        char* port_name = port_name_base + SECURE_FB_MAX_PORT_NAME_SIZE * i;
        port[i].name = port_name;
        port[i].msg_max_size = 1024;
        port[i].msg_queue_len = 1;
        port[i].acl = &acl;
        port[i].priv = (void*)&impl_ops[i];

        int n = sprintf(port_name, "%s.%d", SECURE_FB_PORT_NAME, i);
        if (n != SECURE_FB_MAX_PORT_NAME_SIZE - 1) {
            TLOGE("Failed to create port name\n");
            rc = ERR_BAD_LEN;
            goto fail;
        }
    }
    /*
     * The secure display is a limited resource. This means only one client
     * can have an open session at a time.
     */
    rc = tipc_add_service(hset, port, num_ops, num_ops, &ops);
    if (rc) {
        TLOGE("Failed to add tipc service\n");
        goto fail;
    }
    return rc;

fail:
    free(port_name_base);
fail_port_name_alloc:
    free(port);
fail_port_alloc:
    return rc;
}
