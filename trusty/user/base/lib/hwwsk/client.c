/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <lib/hwwsk/client.h>

#include <assert.h>
#include <lib/tipc/tipc.h>
#include <lk/macros.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <sys/mman.h>
#include <uapi/err.h>

#define TLOG_LVL TLOG_LVL_INFO
#define TLOG_TAG "hwwsk-client"
#include <trusty_log.h>

/*
 * Helper to convert error codes
 */
static int hwwsk_err_to_lk_err(uint32_t err) {
    switch (err) {
    case HWWSK_NO_ERROR:
        return NO_ERROR;

    case HWWSK_ERR_GENERIC:
        return ERR_GENERIC;

    case HWWSK_ERR_INVALID_ARGS:
        return ERR_INVALID_ARGS;

    case HWWSK_ERR_BAD_LEN:
        return ERR_BAD_LEN;

    case HWWSK_ERR_NOT_SUPPORTED:
        return ERR_NOT_SUPPORTED;

    default:
        return ERR_GENERIC;
    }
}

static int handle_reply(handle_t chan, void* buf, size_t buf_size) {
    int rc;
    struct uevent evt;
    struct hwwsk_rsp_hdr rsp;

    /* wait for reply */
    wait(chan, &evt, INFINITE_TIME);

    /* read reply */
    rc = tipc_recv2(chan, sizeof(rsp), &rsp, sizeof(rsp), buf, buf_size);
    if (rc < 0) {
        TLOGD("Failed (%d) to read reply\n", rc);
        return rc;
    }

    /* check server reply */
    if (rsp.status != 0) {
        rc = hwwsk_err_to_lk_err(rsp.status);
        TLOGD("Server returned error (%d)\n", rc);
        return rc;
    }

    if (((size_t)rc - sizeof(rsp)) > buf_size) {
        TLOGD("buffer too small (%d)\n", rc);
        return ERR_INVALID_ARGS;
    }

    return (size_t)rc - sizeof(rsp);
}

int hwwsk_generate_key(handle_t hchan,
                       void* buf,
                       size_t buf_sz,
                       uint32_t key_size,
                       uint32_t key_flags,
                       const void* raw_key,
                       size_t raw_key_len) {
    int rc;
    struct {
        struct hwwsk_req_hdr hdr;
        struct hwwsk_generate_key_req req;
    } msg;

    /* fill request  */
    memset(&msg, 0, sizeof(msg));
    msg.hdr.cmd = HWWSK_CMD_GENERATE_KEY;
    msg.hdr.flags = 0;

    msg.req.key_size = key_size;
    msg.req.key_flags = key_flags;

    /* send request */
    rc = tipc_send2(hchan, &msg, sizeof(msg), raw_key, raw_key_len);
    if (rc < 0) {
        TLOGE("Failed (%d) send request\n", rc);
        return rc;
    }

    return handle_reply(hchan, buf, buf_sz);
}

int hwwsk_export_key(handle_t hchan,
                     void* buf,
                     size_t buf_sz,
                     const void* key_blob,
                     size_t key_blob_len) {
    int rc;
    struct {
        struct hwwsk_req_hdr hdr;
    } msg;

    /* fill request header */
    msg.hdr.cmd = HWWSK_CMD_EXPORT_KEY;
    msg.hdr.flags = 0;

    /* send request */
    rc = tipc_send2(hchan, &msg, sizeof(msg), key_blob, key_blob_len);
    if (rc < 0) {
        TLOGE("Failed (%d) send request\n", rc);
        return rc;
    }

    return handle_reply(hchan, buf, buf_sz);
}
