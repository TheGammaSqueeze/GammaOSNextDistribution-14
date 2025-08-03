/*
 * Copyright 2021, The Android Open Source Project
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

#define TLOG_TAG "hwbcc-srv"

#include <assert.h>
#include <interface/hwbcc/hwbcc.h>
#include <lib/hwbcc/srv/srv.h>
#include <lib/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>
#include <string.h>
#include <trusty_ipc.h>
#include <trusty_log.h>
#include <uapi/err.h>

struct hwbcc_req {
    struct hwbcc_req_hdr hdr;
    struct hwbcc_req_sign_data args;
    uint8_t data_and_aad[HWBCC_MAX_DATA_TO_SIGN_SIZE + HWBCC_MAX_AAD_SIZE];
};
STATIC_ASSERT(sizeof(struct hwbcc_req) ==
              sizeof(struct hwbcc_req_hdr) +
                      sizeof(struct hwbcc_req_sign_data) +
                      HWBCC_MAX_DATA_TO_SIGN_SIZE + HWBCC_MAX_AAD_SIZE);

struct hwbcc_resp {
    struct hwbcc_resp_hdr hdr;
    uint8_t payload[HWBCC_MAX_RESP_PAYLOAD_SIZE];
};
STATIC_ASSERT(sizeof(struct hwbcc_resp) ==
              sizeof(struct hwbcc_resp_hdr) + HWBCC_MAX_RESP_PAYLOAD_SIZE);

/* UUID: {5f902ace-5e5c-4cd8-ae54-87b88c22ddaf} */
static const struct uuid km_uuid = {
        0x5f902ace,
        0x5e5c,
        0x4cd8,
        {0xae, 0x54, 0x87, 0xb8, 0x8c, 0x22, 0xdd, 0xaf},
};

/* UUID: {0e109d31-8bbe-47d6-bb47-e1dd08910e16} */
static const struct uuid hwbcc_test_uuid = {
        0x0e109d31,
        0x8bbe,
        0x47d6,
        {0xbb, 0x47, 0xe1, 0xdd, 0x08, 0x91, 0x0e, 0x16},
};

/* UUID: {67925337-2c03-49ed-9240-d51b6fea3e30} */
static const struct uuid hwbcc_rust_test_uuid = {
        0x67925337,
        0x2c03,
        0x49ed,
        {0x92, 0x40, 0xd5, 0x1b, 0x6f, 0xea, 0x3e, 0x30},
};

/* UUID: {08d3ed40-bde2-448c-a91d75f1989c57ef} */
static const struct uuid widevine_uuid = {
        0x08d3ed40,
        0xbde2,
        0x448c,
        {0xa9, 0x1d, 0x75, 0xf1, 0x98, 0x9c, 0x57, 0xef},
};

/* ZERO UUID to allow connections from non-secure world */
static const struct uuid zero_uuid = UUID_INITIAL_VALUE(zero_uuid);

static const struct uuid* allowed_uuids[] = {
        &km_uuid,       &hwbcc_test_uuid, &hwbcc_rust_test_uuid,
        &widevine_uuid, &zero_uuid,
};

static struct tipc_port_acl acl = {
        .flags = IPC_PORT_ALLOW_TA_CONNECT | IPC_PORT_ALLOW_NS_CONNECT,
        .uuids = allowed_uuids,
        .uuid_num = countof(allowed_uuids),
};

static struct tipc_port port = {
        .name = HWBCC_PORT,
        .msg_max_size =
                MAX(sizeof(struct hwbcc_req), sizeof(struct hwbcc_resp)),
        .msg_queue_len = 1,
        .acl = &acl,
};

static const struct hwbcc_ops* hwbcc_ops;

static int hwbcc_check_ops(const struct hwbcc_ops* ops) {
    if (!ops->init || !ops->close || !ops->sign_key || !ops->get_bcc ||
        !ops->get_dice_artifacts || !ops->ns_deprivilege) {
        TLOGE("NULL ops pointers\n");
        return ERR_INVALID_ARGS;
    }
    return NO_ERROR;
}

static int on_connect(const struct tipc_port* port,
                      handle_t chan,
                      const struct uuid* peer,
                      void** ctx_p) {
    assert(hwbcc_ops);

    hwbcc_session_t s;
    int rc = hwbcc_ops->init(&s, peer);
    if (rc != NO_ERROR) {
        TLOGE("Failed to init HWBCC session: %d\n", rc);
        return rc;
    }

    *ctx_p = s;
    return NO_ERROR;
}

static void on_channel_cleanup(void* ctx) {
    assert(hwbcc_ops);

    hwbcc_session_t s = ctx;
    hwbcc_ops->close(s);
}

static int handle_sign_data(hwbcc_session_t s,
                            handle_t chan,
                            uint32_t test_mode,
                            struct hwbcc_req_sign_data* args,
                            const uint8_t* data_and_aad) {
    int rc;
    struct hwbcc_resp resp = {0};
    size_t payload_size = 0;

    assert(hwbcc_ops);

    rc = hwbcc_ops->sign_key(s, test_mode, args->algorithm, data_and_aad,
                             args->data_size, data_and_aad + args->data_size,
                             args->aad_size, resp.payload, sizeof(resp.payload),
                             &payload_size);
    if (rc != NO_ERROR) {
        TLOGE("HWBCC_CMD_SIGN_DATA failure: %d\n", rc);
    }

    resp.hdr.cmd = HWBCC_CMD_SIGN_DATA | HWBCC_CMD_RESP_BIT;
    resp.hdr.status = rc;
    resp.hdr.payload_size = payload_size;
    rc = tipc_send1(chan, &resp, sizeof(resp.hdr) + payload_size);
    if (rc < 0) {
        return rc;
    }

    if ((size_t)rc != sizeof(resp.hdr) + payload_size) {
        return ERR_BAD_LEN;
    }

    return NO_ERROR;
}

static int handle_get_bcc(hwbcc_session_t s,
                          handle_t chan,
                          uint32_t test_mode) {
    int rc;
    struct hwbcc_resp resp = {0};
    size_t payload_size = 0;

    assert(hwbcc_ops);

    rc = hwbcc_ops->get_bcc(s, test_mode, resp.payload, sizeof(resp.payload),
                            &payload_size);
    if (rc != NO_ERROR) {
        TLOGE("HWBCC_CMD_GET_BCC failure: %d\n", rc);
    }

    resp.hdr.cmd = HWBCC_CMD_GET_BCC | HWBCC_CMD_RESP_BIT;
    resp.hdr.status = rc;
    resp.hdr.payload_size = payload_size;
    rc = tipc_send1(chan, &resp, sizeof(resp.hdr) + payload_size);
    if (rc < 0) {
        return rc;
    }

    if ((size_t)rc != sizeof(resp.hdr) + payload_size) {
        return ERR_BAD_LEN;
    }

    return NO_ERROR;
}

static int handle_get_dice_artifacts(hwbcc_session_t s,
                                     handle_t chan,
                                     uint64_t context) {
    int rc;
    struct hwbcc_resp resp = {0};
    size_t payload_size = 0;

    assert(hwbcc_ops);

    rc = hwbcc_ops->get_dice_artifacts(s, context, resp.payload,
                                       sizeof(resp.payload), &payload_size);

    if (rc != NO_ERROR) {
        TLOGE("HWBCC_CMD_GET_DICE_ARTIFACTS failure: %d\n", rc);
    }

    resp.hdr.cmd = HWBCC_CMD_GET_DICE_ARTIFACTS | HWBCC_CMD_RESP_BIT;
    resp.hdr.status = rc;
    resp.hdr.payload_size = payload_size;

    rc = tipc_send1(chan, &resp, sizeof(resp.hdr) + payload_size);
    if (rc < 0) {
        return rc;
    }

    if ((size_t)rc != sizeof(resp.hdr) + payload_size) {
        return ERR_BAD_LEN;
    }

    return NO_ERROR;
}

static int handle_ns_deprivilege(hwbcc_session_t s, handle_t chan) {
    int rc;
    struct hwbcc_resp resp = {0};

    assert(hwbcc_ops);

    rc = hwbcc_ops->ns_deprivilege(s);

    if (rc != NO_ERROR) {
        TLOGE("HWBCC_CMD_NS_DEPRIVILEGE failure: %d\n", rc);
    }

    resp.hdr.cmd = HWBCC_CMD_NS_DEPRIVILEGE | HWBCC_CMD_RESP_BIT;
    resp.hdr.status = rc;

    rc = tipc_send1(chan, &resp, sizeof(resp.hdr));
    if (rc < 0) {
        return rc;
    }

    if ((size_t)rc != sizeof(resp.hdr)) {
        return ERR_BAD_LEN;
    }

    return NO_ERROR;
}

static int on_message(const struct tipc_port* port, handle_t chan, void* ctx) {
    int rc;
    struct hwbcc_req req;
    hwbcc_session_t s = ctx;

    rc = tipc_recv1(chan, sizeof(req.hdr), &req, sizeof(req));
    if (rc < 0) {
        TLOGE("Failed to read command %d\n", rc);
        return rc;
    }

    switch (req.hdr.cmd) {
    case HWBCC_CMD_SIGN_DATA: {
        if ((size_t)rc < sizeof(req.hdr) + sizeof(req.args)) {
            return ERR_BAD_LEN;
        }

        if (req.args.aad_size > HWBCC_MAX_AAD_SIZE) {
            return ERR_BAD_LEN;
        }

        if (req.args.data_size > HWBCC_MAX_DATA_TO_SIGN_SIZE) {
            return ERR_BAD_LEN;
        }

        if (rc - sizeof(req.hdr) - sizeof(req.args) !=
            req.args.data_size + req.args.aad_size) {
            return ERR_BAD_LEN;
        }

        return handle_sign_data(s, chan, req.hdr.test_mode, &req.args,
                                req.data_and_aad);
    }

    case HWBCC_CMD_GET_BCC:
        if (rc != sizeof(req.hdr)) {
            return ERR_BAD_LEN;
        }

        return handle_get_bcc(s, chan, req.hdr.test_mode);

    case HWBCC_CMD_GET_DICE_ARTIFACTS:
        if (rc != sizeof(req.hdr)) {
            return ERR_BAD_LEN;
        }

        return handle_get_dice_artifacts(s, chan, req.hdr.context);

    case HWBCC_CMD_NS_DEPRIVILEGE:
        if (rc != sizeof(req.hdr)) {
            return ERR_BAD_LEN;
        }

        return handle_ns_deprivilege(s, chan);

    default:
        TLOGE("Received unknown command %x\n", req.hdr.cmd);
        return ERR_CMD_UNKNOWN;
    }

    return NO_ERROR;
}

static struct tipc_srv_ops tipc_ops = {
        .on_connect = on_connect,
        .on_message = on_message,
        .on_channel_cleanup = on_channel_cleanup,
};

/*
 * TODO: Currently we only support one instance of HWBCC service, i.e. this
 * function can only be called once.
 */
int add_hwbcc_service(struct tipc_hset* hset, const struct hwbcc_ops* ops) {
    int rc = hwbcc_check_ops(ops);
    if (rc != NO_ERROR) {
        return rc;
    }
    hwbcc_ops = ops;

    return tipc_add_service(hset, &port, 1, 1, &tipc_ops);
}
