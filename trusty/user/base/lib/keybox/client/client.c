/*
 * Copyright (C) 2021 The Android Open Source Project
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
#include <lib/keybox/client/keybox.h>
#include <lib/tipc/tipc.h>
#include <lk/compiler.h>
#include <lk/macros.h>
#include <uapi/err.h>

#define TLOG_TAG "keybox-client"
#include <trusty_log.h>

struct full_keybox_unwrap_request {
    struct keybox_req header;
    struct keybox_unwrap_req unwrap_header;
};

struct full_keybox_unwrap_response {
    struct keybox_resp header;
    struct keybox_unwrap_resp unwrap_header;
};

int keybox_unwrap(const uint8_t* wrapped_keybox,
                  size_t wrapped_keybox_size,
                  uint8_t* unwrapped_keybox,
                  size_t unwrapped_keybox_buf_size,
                  size_t* unwrapped_keybox_size) {
    handle_t chan;
    int rc = tipc_connect(&chan, KEYBOX_PORT);
    if (rc < 0) {
        TLOGE("Failed to connect to %s\n", KEYBOX_PORT);
        return ERR_IO;
    }

    struct full_keybox_unwrap_request req;
    req.header.cmd = KEYBOX_CMD_UNWRAP;
    req.header.reserved = 0;
    req.unwrap_header.wrapped_keybox_len = wrapped_keybox_size;
    rc = tipc_send2(chan, &req, sizeof(req), wrapped_keybox,
                    wrapped_keybox_size);
    if (rc < 0) {
        TLOGE("Unable to send unwrap request: %d\n", rc);
        goto out;
    }

    uevent_t uevt;
    rc = wait(chan, &uevt, INFINITE_TIME);
    if (rc != NO_ERROR) {
        goto out;
    }

    struct full_keybox_unwrap_response rsp;
    rc = tipc_recv2(chan, sizeof(rsp.header), &rsp, sizeof(rsp),
                    unwrapped_keybox, unwrapped_keybox_buf_size);
    if (rc < 0) {
        goto out;
    }

    if (rsp.header.status != KEYBOX_STATUS_SUCCESS) {
        rc = rsp.header.status;
        goto out;
    }

    if ((size_t)rc < sizeof(rsp)) {
        rc = ERR_IO;
        goto out;
    }

    uint64_t computed_size;
    if (__builtin_add_overflow(rsp.unwrap_header.unwrapped_keybox_len,
                               sizeof(rsp), &computed_size)) {
        rc = ERR_IO;
        goto out;
    }

    if (computed_size != (size_t)rc) {
        rc = ERR_IO;
        goto out;
    }

    *unwrapped_keybox_size = rsp.unwrap_header.unwrapped_keybox_len;

    rc = NO_ERROR;

out:
    close(chan);
    return rc;
}
