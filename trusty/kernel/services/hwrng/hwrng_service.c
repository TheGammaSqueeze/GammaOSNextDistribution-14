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

#define LOCAL_TRACE 0

#include <err.h>
#include <inttypes.h>
#include <lib/ktipc/ktipc.h>
#include <lib/rand/rand.h>
#include <lib/trusty/ipc.h>
#include <lib/trusty/ipc_msg.h>
#include <lib/trusty/uuid.h>
#include <lk/init.h>
#include <lk/list.h>
#include <lk/trace.h>

#include <interface/hwrng/hwrng.h>

#define HWRNG_SRV_NAME HWRNG_PORT
#define MAX_HWRNG_MSG_SIZE 128

struct hwrng_chan_ctx {
    struct list_node node;
    struct handle* chan;
    size_t req_size;
    int error;
    bool send_blocked;
};

static uint8_t rng_data[MAX_HWRNG_MSG_SIZE];

/* If we have data left over from the last request, keep track of it */
static size_t rng_data_avail_count;
static size_t rng_data_avail_pos;

static struct list_node hwrng_req_list = LIST_INITIAL_VALUE(hwrng_req_list);

/*
 * Handle HWRNG request queue
 */
static void hwrng_handle_req_queue(void) {
    int rc;
    struct hwrng_chan_ctx* ctx;
    struct hwrng_chan_ctx* temp;

    /* for all pending requests */
    bool more_requests;
    do {
        more_requests = false;
        list_for_every_entry_safe(&hwrng_req_list, ctx, temp,
                                  struct hwrng_chan_ctx, node) {
            if (ctx->error || ctx->send_blocked)
                continue; /* can't service it right now */

            /*
             * send up to MAX_HWRNG_MSG_SIZE per client at a time,
             * to prevent a single client from starving all the others
             */
            size_t len = MIN(ctx->req_size, MAX_HWRNG_MSG_SIZE);
            if (rng_data_avail_count) {
                /* use leftover data if there is any */
                len = MIN(len, rng_data_avail_count);
            } else {
                /* get hwrng data */
                rand_get_bytes(rng_data, len);
                rng_data_avail_pos = 0;
                rng_data_avail_count = len;
            }

            /* send reply */
            rc = ktipc_send(ctx->chan, rng_data + rng_data_avail_pos, len);
            if (rc < 0) {
                if (rc == ERR_NOT_ENOUGH_BUFFER) {
                    /* mark it as send_blocked */
                    ctx->send_blocked = true;
                } else {
                    TRACEF("%s: failed (%d) to send_reply\n", __func__, rc);
                    ctx->error = rc;
                }
                continue;
            }

            rng_data_avail_pos += len;
            rng_data_avail_count -= len;
            ctx->req_size -= len;

            if (!ctx->req_size) {
                /* remove it from pending list */
                list_delete(&ctx->node);
            } else {
                more_requests = true;
            }
        }
    } while (more_requests);
}

static int hwrng_handle_msg(const struct ktipc_port* port,
                            struct handle* chan,
                            void* ctx_v) {
    int rc;
    struct hwrng_chan_ctx* ctx = ctx_v;
    struct hwrng_req req;

    /* check for an error from a previous send attempt */
    if (ctx->error) {
        return ctx->error;
    }

    /* read request */
    rc = ktipc_recv(chan, sizeof(req), &req, sizeof(req));
    if (rc < 0) {
        TRACEF("%s: failed (%d) to receive msg for chan\n", __func__, rc);
        return rc;
    }

    /* check if we already have request in progress */
    if (list_in_list(&ctx->node)) {
        /* extend it */
        ctx->req_size += req.len;
    } else {
        /* queue it */
        ctx->req_size = req.len;
        list_add_tail(&hwrng_req_list, &ctx->node);
    }

    hwrng_handle_req_queue();

    return ctx->error;
}

static int hwrng_handle_connect(const struct ktipc_port* port,
                                struct handle* chan,
                                const struct uuid* peer,
                                void** ctx_p) {
    struct hwrng_chan_ctx* ctx = calloc(1, sizeof(*ctx));
    if (!ctx) {
        TRACEF("%s: failed to allocate context\n", __func__);
        return ERR_NO_MEMORY;
    }

    ctx->chan = chan;
    *ctx_p = ctx;

    return NO_ERROR;
}

static void hwrng_handle_channel_cleanup(void* ctx_v) {
    struct hwrng_chan_ctx* ctx = ctx_v;

    if (list_in_list(&ctx->node)) {
        list_delete(&ctx->node);
    }

    free(ctx);
}

static int hwrng_handle_send_unblocked(const struct ktipc_port* port,
                                       struct handle* chan,
                                       void* ctx_v) {
    struct hwrng_chan_ctx* ctx = ctx_v;

    if (ctx->error) {
        return ctx->error;
    }

    ctx->send_blocked = false;

    hwrng_handle_req_queue();

    return ctx->error;
}

const static struct ktipc_srv_ops hwrng_srv_ops = {
        .on_connect = hwrng_handle_connect,
        .on_message = hwrng_handle_msg,
        .on_channel_cleanup = hwrng_handle_channel_cleanup,
        .on_send_unblocked = hwrng_handle_send_unblocked,
};

const static struct ktipc_port_acl hwrng_srv_port_acl = {
        .flags = IPC_PORT_ALLOW_TA_CONNECT,
        .uuids = NULL,
        .uuid_num = 0,
        .extra_data = NULL,
};

const static struct ktipc_port hwrng_srv_port = {
        .name = HWRNG_SRV_NAME,
        .uuid = &kernel_uuid,
        .msg_max_size = MAX_HWRNG_MSG_SIZE,
        .msg_queue_len = 1,
        .acl = &hwrng_srv_port_acl,
        .priv = NULL,
};

static struct ktipc_server hwrng_ktipc_server =
        KTIPC_SERVER_INITIAL_VALUE(hwrng_ktipc_server, "hwrng_ktipc_server");

static void hwrng_ktipc_server_init(uint lvl) {
    int rc;

    rc = ktipc_server_start(&hwrng_ktipc_server);
    if (rc < 0) {
        panic("Failed (%d) to start hwrng server\n", rc);
    }

    rc = ktipc_server_add_port(&hwrng_ktipc_server, &hwrng_srv_port,
                               &hwrng_srv_ops);
    if (rc < 0) {
        panic("Failed (%d) to create hwrng service port\n", rc);
    }
}

LK_INIT_HOOK(hwrng_ktipc_server_init,
             hwrng_ktipc_server_init,
             LK_INIT_LEVEL_APPS - 1)
