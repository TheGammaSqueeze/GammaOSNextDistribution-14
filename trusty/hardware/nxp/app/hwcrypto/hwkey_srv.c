/*
 * Copyright (C) 2016-2017 The Android Open Source Project
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

#include <assert.h>
#include <lk/compiler.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <trusty_ipc.h>
#include <uapi/err.h>

#include <interface/hwkey/hwkey.h>
#include <nxp_hwcrypto_uuid_consts.h>

#include "common.h"
#include "hwkey_srv_priv.h"

#define TLOG_TAG "hwkey_srv"
#include <trusty_log.h>

#define HWKEY_MAX_PAYLOAD_SIZE 2048

struct hwkey_chan_ctx {
    tipc_event_handler_t evt_handler;
    handle_t chan;
    uuid_t uuid;
};

static void hwkey_port_handler(const uevent_t* ev, void* priv);
static void hwkey_chan_handler(const uevent_t* ev, void* priv);

static tipc_event_handler_t hwkey_port_evt_handler = {
        .proc = hwkey_port_handler,
};

/* Make sure that key_data and reg_data buffers are not crossing page boundary
 * so it is safe to pass them to DMA. An extra byte for req_data buf is used to
 * zero terminate string so it is OK to have it on separate page as it will
 * never be accesed by DMA engine.
 */
static uint8_t key_data[HWKEY_MAX_PAYLOAD_SIZE]
        __attribute__((aligned(HWKEY_MAX_PAYLOAD_SIZE)));
static uint8_t req_data[HWKEY_MAX_PAYLOAD_SIZE + 1]
        __attribute__((aligned(HWKEY_MAX_PAYLOAD_SIZE)));

static unsigned int key_slot_cnt;
static const struct hwkey_keyslot* key_slots;

#if WITH_HWCRYPTO_UNITTEST
/*
 *  Support for hwcrypto unittest keys should be only enabled
 *  to test hwcrypto related APIs
 */

/* UUID of HWCRYPTO_UNITTEST application */
static const uuid_t hwcrypto_unittest_uuid = HWCRYPTO_UNITTEST_APP_UUID;

static uint8_t _unittest_key32[32] = "unittestkeyslotunittestkeyslotun";
static uint32_t get_unittest_key32(const struct hwkey_keyslot* slot,
                                   uint8_t* kbuf,
                                   size_t kbuf_len,
                                   size_t* klen) {
    assert(kbuf);
    assert(klen);
    assert(kbuf_len >= sizeof(_unittest_key32));

    /* just return predefined key */
    memcpy(kbuf, _unittest_key32, sizeof(_unittest_key32));
    *klen = sizeof(_unittest_key32);

    return HWKEY_NO_ERROR;
}

static const struct hwkey_keyslot test_key_slots[] = {
        {
                .uuid = &hwcrypto_unittest_uuid,
                .key_id = "com.android.trusty.hwcrypto.unittest.key32",
                .handler = get_unittest_key32,
        },
};
#endif /* WITH_HWCRYPTO_UNITTEST */

/*
 * Close specified hwkey context
 */
static void hwkey_ctx_close(struct hwkey_chan_ctx* ctx) {
    close(ctx->chan);
    free(ctx);
}

/*
 * Send response message
 */
static int hwkey_send_rsp(struct hwkey_chan_ctx* ctx,
                          struct hwkey_msg* rsp_msg,
                          uint8_t* rsp_data,
                          size_t rsp_data_len) {
    rsp_msg->header.cmd |= HWKEY_RESP_BIT;
    return tipc_send_two_segments(ctx->chan, rsp_msg, sizeof(*rsp_msg),
                                  rsp_data, rsp_data_len);
}

static uint32_t _handle_slots(struct hwkey_chan_ctx* ctx,
                              const char* slot_id,
                              const struct hwkey_keyslot* slots,
                              unsigned int slot_cnt,
                              uint8_t* kbuf,
                              size_t kbuf_len,
                              size_t* klen) {
    if (!slots)
        return HWKEY_ERR_NOT_FOUND;

    for (unsigned int i = 0; i < slot_cnt; i++, slots++) {
        /* check key id */
        if (strcmp(slots->key_id, slot_id))
            continue;

        /* Check if the caller is allowed to get that key */
        if (memcmp(&ctx->uuid, slots->uuid, sizeof(uuid_t)) == 0) {
            if (slots->handler) {
                return slots->handler(slots, kbuf, kbuf_len, klen);
            }
        }
    }
    return HWKEY_ERR_NOT_FOUND;
}

/*
 * Handle get key slot command
 */
static int hwkey_handle_get_keyslot_cmd(struct hwkey_chan_ctx* ctx,
                                        struct hwkey_msg* msg,
                                        const char* slot_id) {
    int rc;
    size_t klen = 0;

    msg->header.status = _handle_slots(ctx, slot_id, key_slots, key_slot_cnt,
                                       key_data, sizeof(key_data), &klen);

#if WITH_HWCRYPTO_UNITTEST
    if (msg->header.status == HWKEY_ERR_NOT_FOUND) {
        /* also search test keys */
        msg->header.status = _handle_slots(ctx, slot_id, test_key_slots,
                                           countof(test_key_slots), key_data,
                                           sizeof(key_data), &klen);
    }
#endif

    rc = hwkey_send_rsp(ctx, msg, key_data, klen);
    if (klen) {
        /* sanitize key buffer */
        memset(key_data, 0, klen);
    }
    return rc;
}

/*
 * Handle Derive key cmd
 */
static int hwkey_handle_derive_key_cmd(struct hwkey_chan_ctx* ctx,
                                       struct hwkey_msg* msg,
                                       const uint8_t* ikm_data,
                                       size_t ikm_len) {
    int rc;
    size_t key_len = sizeof(key_data);

    /* check requested key derivation function */
    if (msg->arg1 == HWKEY_KDF_VERSION_BEST)
        msg->arg1 = HWKEY_KDF_VERSION_1; /* we only support V1 */

    switch (msg->arg1) {
    case HWKEY_KDF_VERSION_1:
        msg->header.status = derive_key_v1(&ctx->uuid, ikm_data, ikm_len,
                                           key_data, &key_len);
        break;

    default:
        TLOGE("%u is unsupported KDF function\n", msg->arg1);
        key_len = 0;
        msg->header.status = HWKEY_ERR_NOT_IMPLEMENTED;
    }

    rc = hwkey_send_rsp(ctx, msg, key_data, key_len);
    if (key_len) {
        /* sanitize key buffer */
        memset(key_data, 0, key_len);
    }
    return rc;
}

/*
 *  Read and queue HWKEY request message
 */
static int hwkey_chan_handle_msg(struct hwkey_chan_ctx* ctx) {
    int rc;
    size_t req_data_len;
    struct hwkey_msg msg;

    rc = tipc_recv_two_segments(ctx->chan, &msg, sizeof(msg), req_data,
                                sizeof(req_data) - 1);
    if (rc < 0) {
        TLOGE("failed (%d) to recv msg from chan %d\n", rc, ctx->chan);
        return rc;
    }

    /* calculate payload length */
    req_data_len = (size_t)rc - sizeof(msg);

    /* handle it */
    switch (msg.header.cmd) {
    case HWKEY_GET_KEYSLOT:
        req_data[req_data_len] = 0; /* force zero termination */
        rc = hwkey_handle_get_keyslot_cmd(ctx, &msg, (const char*)req_data);
        break;

    case HWKEY_DERIVE:
        rc = hwkey_handle_derive_key_cmd(ctx, &msg, req_data, req_data_len);
        memset(req_data, 0, req_data_len); /* sanitize request buffer */
        break;

    default:
        TLOGE("Unsupported request: %d\n", (int)msg.header.cmd);
        msg.header.status = HWKEY_ERR_NOT_IMPLEMENTED;
        rc = hwkey_send_rsp(ctx, &msg, NULL, 0);
    }

    return rc;
}

/*
 *  HWKEY service channel event handler
 */
static void hwkey_chan_handler(const uevent_t* ev, void* priv) {
    struct hwkey_chan_ctx* ctx = priv;

    assert(ctx);
    assert(ev->handle == ctx->chan);

    tipc_handle_chan_errors(ev);

    if (ev->event & IPC_HANDLE_POLL_HUP) {
        /* closed by peer. */
        hwkey_ctx_close(ctx);
        return;
    }

    if (ev->event & IPC_HANDLE_POLL_MSG) {
        int rc = hwkey_chan_handle_msg(ctx);
        if (rc < 0) {
            /* report an error and close channel */
            TLOGE("failed (%d) to handle event on channel %d\n", rc,
                  ev->handle);
            hwkey_ctx_close(ctx);
        }
    }
}

/*
 * HWKEY service port event handler
 */
static void hwkey_port_handler(const uevent_t* ev, void* priv) {
    uuid_t peer_uuid;

    tipc_handle_port_errors(ev);

    if (ev->event & IPC_HANDLE_POLL_READY) {
        /* incoming connection: accept it */
        int rc = accept(ev->handle, &peer_uuid);
        if (rc < 0) {
            TLOGE("failed (%d) to accept on port %d\n", rc, ev->handle);
            return;
        }

        handle_t chan = (handle_t)rc;
        struct hwkey_chan_ctx* ctx = calloc(1, sizeof(*ctx));
        if (!ctx) {
            TLOGE("failed (%d) to allocate context on chan %d\n", rc, chan);
            close(chan);
            return;
        }

        /* init channel state */
        ctx->evt_handler.priv = ctx;
        ctx->evt_handler.proc = hwkey_chan_handler;
        ctx->chan = chan;
        ctx->uuid = peer_uuid;

        rc = set_cookie(chan, &ctx->evt_handler);
        if (rc < 0) {
            TLOGE("failed (%d) to set_cookie on chan %d\n", rc, chan);
            hwkey_ctx_close(ctx);
            return;
        }
    }
}

/*
 *  Install Key slot provider
 */
void hwkey_install_keys(const struct hwkey_keyslot* keys, unsigned int kcnt) {
    assert(key_slots == NULL);
    assert(key_slot_cnt == 0);
    assert(keys && kcnt);

    key_slots = keys;
    key_slot_cnt = kcnt;
}

/*
 *  Initialize HWKEY service
 */
int hwkey_start_service(void) {
    int rc;
    handle_t port;

    TLOGD("Start HWKEY service\n");

    /* Initialize service */
    rc = port_create(HWKEY_PORT, 1,
                     sizeof(struct hwkey_msg) + HWKEY_MAX_PAYLOAD_SIZE,
                     IPC_PORT_ALLOW_TA_CONNECT);
    if (rc < 0) {
        TLOGE("Failed (%d) to create port %s\n", rc, HWKEY_PORT);
        return rc;
    }

    port = (handle_t)rc;
    rc = set_cookie(port, &hwkey_port_evt_handler);
    if (rc) {
        TLOGE("failed (%d) to set_cookie on port %d\n", rc, port);
        close(port);
        return rc;
    }

    return NO_ERROR;
}
