/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define TLOG_TAG "hwrng_srv"

#include <assert.h>
#include <inttypes.h>
#include <lk/list.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <uapi/err.h>

#include <hwcrypto/hwrng_dev.h>
#include <interface/hwrng/hwrng.h>
#include <lib/tipc/tipc.h>
#include <trusty_log.h>

#define HWRNG_SRV_NAME HWRNG_PORT
#define MAX_HWRNG_MSG_SIZE 4096

struct hwrng_chan_ctx {
    struct tipc_event_handler evt_handler;
    struct list_node node;
    handle_t chan;
    size_t req_size;
    bool send_blocked;
};

static void hwrng_port_handler(const uevent_t* ev, void* priv);
static void hwrng_chan_handler(const uevent_t* ev, void* priv);

static handle_t hwrng_port = INVALID_IPC_HANDLE;

static struct tipc_event_handler hwrng_port_evt_handler = {
        .proc = hwrng_port_handler,
};

static uint8_t rng_data[MAX_HWRNG_MSG_SIZE];

static struct list_node hwrng_req_list = LIST_INITIAL_VALUE(hwrng_req_list);

/****************************************************************************/

/*
 *  Hexdump content of memory region
 */
static void _hexdump8(const void* ptr, size_t len) {
    uintptr_t address = (uintptr_t)ptr;
    size_t count;
    size_t i;

    for (count = 0; count < len; count += 16) {
        fprintf(stderr, "0x%08" PRIxPTR ": ", address);
        for (i = 0; i < MIN(len - count, 16); i++) {
            fprintf(stderr, "0x%02hhx ", *(const uint8_t*)(address + i));
        }
        fprintf(stderr, "\n");
        address += 16;
    }
}

/*
 * Close specified HWRNG service channel
 */
static void hwrng_close_chan(struct hwrng_chan_ctx* ctx) {
    close(ctx->chan);

    if (list_in_list(&ctx->node))
        list_delete(&ctx->node);

    free(ctx);
}

/*
 * Handle HWRNG request queue
 */
static bool hwrng_handle_req_queue(void) {
    int rc;
    struct hwrng_chan_ctx* ctx;
    struct hwrng_chan_ctx* temp;

    /* service channels */
    bool need_more = false;

    /* for all pending requests */
    list_for_every_entry_safe(&hwrng_req_list, ctx, temp, struct hwrng_chan_ctx,
                              node) {
        if (ctx->send_blocked)
            continue; /* cant service it rignt now */

        size_t len = ctx->req_size;

        if (len > MAX_HWRNG_MSG_SIZE)
            len = MAX_HWRNG_MSG_SIZE;

        /* get hwrng data */
        rc = hwrng_dev_get_rng_data(rng_data, len);
        if (rc != NO_ERROR) {
            TLOGE("failed (%d) to get hwrng data\n", rc);
            hwrng_close_chan(ctx);
            continue;
        }

        /* send reply */
        rc = tipc_send1(ctx->chan, rng_data, len);
        if (rc < 0) {
            if (rc == ERR_NOT_ENOUGH_BUFFER) {
                /* mark it as send_blocked */
                ctx->send_blocked = true;
            } else {
                /* just close HWRNG request channel */
                TLOGE("failed (%d) to send_reply\n", rc);
                hwrng_close_chan(ctx);
            }
            continue;
        }

        ctx->req_size -= len;

        if (ctx->req_size == 0) {
            /* remove it from pending list */
            list_delete(&ctx->node);
        } else {
            need_more = true;
        }
    }

    return need_more;
}

/*
 * Check if we can handle request queue
 */
static void hwrng_kick_req_queue(void) {
    hwrng_handle_req_queue();
}

/*
 *  Read and queue HWRNG request message
 */
static int hwrng_chan_handle_msg(struct hwrng_chan_ctx* ctx) {
    int rc;
    struct hwrng_req req;

    assert(ctx);

    /* read request */
    rc = tipc_recv1(ctx->chan, sizeof(req), &req, sizeof(req));
    if (rc < 0) {
        TLOGE("failed (%d) to receive msg for chan %d\n", rc, ctx->chan);
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

    return 0;
}

/*
 *  Channel handler where HWRNG requests are coming from
 */
static void hwrng_chan_handler(const uevent_t* ev, void* priv) {
    struct hwrng_chan_ctx* ctx = priv;

    assert(ctx);
    assert(ev->handle == ctx->chan);

    tipc_handle_chan_errors(ev);

    if (ev->event & IPC_HANDLE_POLL_HUP) {
        hwrng_close_chan(ctx);
    } else {
        if (ev->event & IPC_HANDLE_POLL_SEND_UNBLOCKED) {
            ctx->send_blocked = false;
        }

        if (ev->event & IPC_HANDLE_POLL_MSG) {
            int rc = hwrng_chan_handle_msg(ctx);
            if (rc) {
                hwrng_close_chan(ctx);
            }
        }
    }

    /* kick state machine */
    hwrng_kick_req_queue();
}

/*
 * Port were HWRNG requests are coming from
 */
static void hwrng_port_handler(const uevent_t* ev, void* priv) {
    uuid_t peer_uuid;

    tipc_handle_port_errors(ev);

    if (ev->event & IPC_HANDLE_POLL_READY) {
        handle_t chan;

        /* incoming connection: accept it */
        int rc = accept(ev->handle, &peer_uuid);
        if (rc < 0) {
            TLOGE("failed (%d) to accept on port %d\n", rc, ev->handle);
            return;
        }
        chan = (handle_t)rc;

        /* allocate state */
        struct hwrng_chan_ctx* ctx = calloc(1, sizeof(*ctx));
        if (!ctx) {
            TLOGE("failed to alloc state for chan %d\n", chan);
            close(chan);
            return;
        }

        /* init channel state */
        ctx->evt_handler.priv = ctx;
        ctx->evt_handler.proc = hwrng_chan_handler;
        ctx->chan = chan;

        /* attach channel handler */
        rc = set_cookie(chan, &ctx->evt_handler);
        if (rc) {
            TLOGE("failed (%d) to set_cookie on chan %d\n", rc, chan);
            free(ctx);
            close(chan);
            return;
        }
    }
}

/*
 *  Initialize HWRNG services
 */
int hwrng_start_service(void) {
    int rc;

    TLOGD("Start HWRNG service\n");

    /* create HWRNG port */
    rc = port_create(HWRNG_SRV_NAME, 1, MAX_HWRNG_MSG_SIZE,
                     IPC_PORT_ALLOW_TA_CONNECT);
    if (rc < 0) {
        TLOGE("Failed (%d) to create port '%s'\n", rc, HWRNG_SRV_NAME);
        goto err_port_create;
    }

    hwrng_port = (handle_t)rc;
    set_cookie(hwrng_port, &hwrng_port_evt_handler);

    rc = hwrng_dev_init();
    if (rc != NO_ERROR) {
        TLOGE("Failed (%d) to initialize HWRNG device\n", rc);
        goto err_hwrng_dev_init;
    }

    return NO_ERROR;

err_hwrng_dev_init:
    close(hwrng_port);
err_port_create:
    return rc;
}
