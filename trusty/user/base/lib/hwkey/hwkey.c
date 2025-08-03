/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <trusty_ipc.h>
#include <uapi/err.h>

#include <lib/hwkey/hwkey.h>
#include <lib/tipc/tipc.h>
#include "interface/hwkey/hwkey.h"

#define LOG_TAG "libhwkey"
#define TLOGE(fmt, ...) \
    fprintf(stderr, "%s: %d: " fmt, LOG_TAG, __LINE__, ##__VA_ARGS__)

/**
 * long hwkey_err_to_tipc_err() - translates hwkey err value to tipc/lk err
 * value
 * @hwkey_err: hwkey err value
 *
 * Returns: enum hwkey_err value
 */
static long hwkey_err_to_tipc_err(enum hwkey_err hwkey_err) {
    switch (hwkey_err) {
    case HWKEY_NO_ERROR:
        return NO_ERROR;
    case HWKEY_ERR_NOT_VALID:
        return ERR_NOT_VALID;
    case HWKEY_ERR_BAD_LEN:
        return ERR_BAD_LEN;
    case HWKEY_ERR_NOT_IMPLEMENTED:
        return ERR_NOT_IMPLEMENTED;
    case HWKEY_ERR_NOT_FOUND:
        return ERR_NOT_FOUND;
    case HWKEY_ERR_ALREADY_EXISTS:
        return ERR_ALREADY_EXISTS;
    default:
        return ERR_GENERIC;
    }
}

/**
 * long send_req() - sends request to hwkey server
 * @session: the hwkey session handle
 * @msg: the request header to send to the hwkey server
 * @req_buf: the request payload to send to the hwkey server
 * @req_buf_len: the length of the request payload @req_buf
 * @rsp_buf: buffer in which to store the response payload
 * @rsp_buf_len: the size of the response buffer. Inout param, set
 *               to the actual response payload length.
 *
 * Returns: NO_ERROR on success, negative error code on failure
 */
static long send_req(hwkey_session_t session,
                     struct hwkey_msg* msg,
                     uint8_t* req_buf,
                     uint32_t req_buf_len,
                     uint8_t* rsp_buf,
                     uint32_t* rsp_buf_len) {
    long rc;

    struct iovec tx_iov[2] = {
            {.iov_base = msg, .iov_len = sizeof(*msg)},
            {.iov_base = req_buf, .iov_len = req_buf_len},
    };
    ipc_msg_t tx_msg = {
            .iov = tx_iov,
            .num_iov = 2,
    };

    rc = send_msg(session, &tx_msg);
    if (rc < 0) {
        goto err_send_fail;
    }

    if (((size_t)rc) != sizeof(*msg) + req_buf_len) {
        rc = ERR_IO;
        goto err_send_fail;
    }

    uevent_t uevt;
    rc = wait(session, &uevt, INFINITE_TIME);
    if (rc != NO_ERROR) {
        goto err_send_fail;
    }

    ipc_msg_info_t inf;
    rc = get_msg(session, &inf);
    if (rc != NO_ERROR) {
        TLOGE("%s: failed to get_msg (%ld)\n", __func__, rc);
        goto err_send_fail;
    }

    if (inf.len > sizeof(*msg) + (size_t)*rsp_buf_len) {
        TLOGE("%s: insufficient output buffer size (%zu > %zu)\n", __func__,
              inf.len - sizeof(*msg), (size_t)*rsp_buf_len);
        rc = ERR_TOO_BIG;
        goto err_get_fail;
    }

    if (inf.len < sizeof(*msg)) {
        TLOGE("%s: short buffer (%zu)\n", __func__, inf.len);
        rc = ERR_NOT_VALID;
        goto err_get_fail;
    }

    uint32_t cmd_sent = msg->header.cmd;

    struct iovec rx_iov[2] = {
            {.iov_base = msg, .iov_len = sizeof(*msg)},
            {.iov_base = rsp_buf, .iov_len = *rsp_buf_len},
    };
    ipc_msg_t rx_msg = {
            .iov = rx_iov,
            .num_iov = 2,
    };

    rc = read_msg(session, inf.id, 0, &rx_msg);
    put_msg(session, inf.id);
    if (rc < 0) {
        goto err_read_fail;
    }

    size_t read_len = (size_t)rc;
    if (read_len != inf.len) {
        // data read in does not match message length
        TLOGE("%s: invalid read length (%zu != %zu)\n", __func__, read_len,
              inf.len);
        rc = ERR_IO;
        goto err_read_fail;
    }

    if (msg->header.cmd != (cmd_sent | HWKEY_RESP_BIT)) {
        TLOGE("%s: invalid response id (0x%x) for cmd (0x%x)\n", __func__,
              msg->header.cmd, cmd_sent);
        return ERR_NOT_VALID;
    }

    *rsp_buf_len = read_len - sizeof(*msg);
    return hwkey_err_to_tipc_err(msg->header.status);

err_get_fail:
    put_msg(session, inf.id);
err_send_fail:
err_read_fail:
    TLOGE("%s: failed read_msg (%ld)\n", __func__, rc);
    return rc;
}

long hwkey_open(void) {
    return connect(HWKEY_PORT, IPC_CONNECT_WAIT_FOR_PORT);
}

long hwkey_get_keyslot_data(hwkey_session_t session,
                            const char* slot_id,
                            uint8_t* data,
                            uint32_t* data_size) {
    if (slot_id == NULL || data == NULL || data_size == NULL ||
        *data_size == 0) {
        return ERR_NOT_VALID;
    }

    struct hwkey_msg msg = {
            .header.cmd = HWKEY_GET_KEYSLOT,
    };

    // TODO: remove const cast when const APIs are available
    return send_req(session, &msg, (uint8_t*)slot_id, strlen(slot_id), data,
                    data_size);
}

long hwkey_derive(hwkey_session_t session,
                  uint32_t* kdf_version,
                  const uint8_t* src,
                  uint8_t* dest,
                  uint32_t buf_size) {
    if (src == NULL || buf_size == 0 || dest == NULL || kdf_version == NULL) {
        // invalid input
        return ERR_NOT_VALID;
    }

    struct hwkey_msg msg = {
            .header.cmd = HWKEY_DERIVE,
            .arg1 = *kdf_version,
    };

    // TODO: remove const cast when const APIs are available
    uint32_t stored_buf_size = buf_size;
    long rc = send_req(session, &msg, (uint8_t*)src, buf_size, dest, &buf_size);

    if (rc == NO_ERROR && stored_buf_size != buf_size) {
        return ERR_BAD_LEN;
    }

    *kdf_version = msg.arg1;

    return rc;
}

long hwkey_derive_versioned(hwkey_session_t session,
                            struct hwkey_versioned_key_options* args) {
    if (args == NULL) {
        TLOGE("Args pointer is null\n");
        return ERR_NOT_VALID;
    }
    if (args->context == NULL && args->context_len != 0) {
        TLOGE("Context pointer is null with non-zero length\n");
        return ERR_NOT_VALID;
    }
    if (args->context != NULL && args->context_len == 0) {
        TLOGE("Context pointer is non-null with zero length\n");
        return ERR_NOT_VALID;
    }
    if (args->key == NULL && args->key_len != 0) {
        TLOGE("Key pointer is null with non-zero length\n");
        return ERR_NOT_VALID;
    }
    if (args->key != NULL && args->key_len == 0) {
        TLOGE("Key pointer is non-null with zero length\n");
        return ERR_NOT_VALID;
    }
    if (args->os_rollback_version < 0 &&
        args->os_rollback_version != HWKEY_ROLLBACK_VERSION_CURRENT) {
        TLOGE("OS rollback version is invalid: %d\n",
              args->os_rollback_version);
        return ERR_NOT_VALID;
    }

    size_t max_payload_len =
            HWKEY_MAX_MSG_SIZE - sizeof(struct hwkey_derive_versioned_msg);
    if (args->context_len > max_payload_len ||
        args->key_len > max_payload_len) {
        return ERR_BAD_LEN;
    }

    uint32_t key_options = args->shared_key ? HWKEY_SHARED_KEY_TYPE
                                            : HWKEY_DEVICE_UNIQUE_KEY_TYPE;

    struct hwkey_derive_versioned_msg req_msg = {
            .header.cmd = HWKEY_DERIVE_VERSIONED,
            .kdf_version = args->kdf_version,
            .rollback_version_source = args->rollback_version_source,
            .rollback_versions[HWKEY_ROLLBACK_VERSION_OS_INDEX] =
                    args->os_rollback_version,
            .key_options = key_options,
            .key_len = args->key_len,
    };

    int rc = tipc_send2(session, &req_msg, sizeof(req_msg), args->context,
                        args->context_len);
    if (rc < 0) {
        return rc;
    }

    if (((size_t)rc) != sizeof(req_msg) + args->context_len) {
        TLOGE("%s: failed to send entire message\n", __func__);
        return ERR_IO;
    }

    uevent_t uevt;
    rc = wait(session, &uevt, INFINITE_TIME);
    if (rc != NO_ERROR) {
        return rc;
    }

    struct hwkey_derive_versioned_msg resp_msg = {0};

    rc = tipc_recv2(session, sizeof(struct hwkey_msg_header), &resp_msg,
                    sizeof(resp_msg), args->key, args->key_len);
    if (rc < 0) {
        return rc;
    }

    if (resp_msg.header.cmd != (req_msg.header.cmd | HWKEY_RESP_BIT)) {
        TLOGE("%s: invalid response id (0x%x) for cmd (0x%x)\n", __func__,
              resp_msg.header.cmd, req_msg.header.cmd);
        return ERR_NOT_VALID;
    }

    if (resp_msg.header.status == HWKEY_NO_ERROR &&
        (size_t)rc != sizeof(resp_msg) + args->key_len) {
        TLOGE("%s: unexpected response length (%zu != %zu)\n", __func__,
              (size_t)rc, sizeof(resp_msg) + args->key_len);
        return ERR_BAD_LEN;
    }

    if (resp_msg.header.status == HWKEY_NO_ERROR) {
        args->kdf_version = resp_msg.kdf_version;
        args->os_rollback_version =
                resp_msg.rollback_versions[HWKEY_ROLLBACK_VERSION_OS_INDEX];
    }

    return hwkey_err_to_tipc_err(resp_msg.header.status);
}

void hwkey_close(hwkey_session_t session) {
    close(session);
}
