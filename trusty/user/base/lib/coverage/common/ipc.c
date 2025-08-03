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

#define TLOG_TAG "coverage-common-ipc"

#include <lib/coverage/common/ipc.h>
#include <lib/tipc/tipc.h>
#include <trusty_log.h>
#include <uapi/err.h>

int coverage_send(handle_t chan, void* msg, size_t msg_len, handle_t* h) {
    int rc;
    struct iovec iov = {
            .iov_base = msg,
            .iov_len = msg_len,
    };
    struct ipc_msg ipc_msg = {
            .iov = &iov,
            .num_iov = 1,
            .handles = h,
            .num_handles = h ? 1 : 0,
    };

    rc = send_msg(chan, &ipc_msg);
    if (rc != (int)msg_len) {
        TLOGE("failed (%d) to send_msg()\n", rc);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        return rc;
    }

    return NO_ERROR;
}

int coverage_recv(handle_t chan, void* msg, size_t msg_len, handle_t* h) {
    int rc;
    struct ipc_msg_info msg_inf;

    rc = get_msg(chan, &msg_inf);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to get_msg()\n", rc);
        return rc;
    }

    struct iovec iov = {
            .iov_base = msg,
            .iov_len = msg_len,
    };
    struct ipc_msg ipc_msg = {
            .iov = &iov,
            .num_iov = 1,
            .handles = h,
            .num_handles = h ? 1 : 0,
    };

    rc = read_msg(chan, msg_inf.id, 0, &ipc_msg);
    if (rc != (int)msg_len) {
        TLOGE("failed (%d) to read_msg()\n", rc);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        goto out;
    }

    rc = NO_ERROR;
out:
    put_msg(chan, msg_inf.id);
    return rc;
}

int coverage_rpc(handle_t chan,
                 void* req,
                 size_t req_len,
                 handle_t* req_h,
                 void* resp,
                 size_t resp_len,
                 handle_t* resp_h) {
    int rc;
    uevent_t evt;

    rc = coverage_send(chan, req, req_len, req_h);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to send request\n", rc);
        return rc;
    }

    rc = wait(chan, &evt, INFINITE_TIME);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to wait for reply\n", rc);
        return rc;
    }

    rc = coverage_recv(chan, resp, resp_len, resp_h);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to receive response\n", rc);
        return rc;
    }

    return NO_ERROR;
}

int coverage_aggregator_rpc(handle_t chan,
                            struct coverage_aggregator_req* req,
                            handle_t* req_h,
                            struct coverage_aggregator_resp* resp,
                            handle_t* resp_h) {
    int rc = coverage_rpc(chan, req, sizeof(*req), req_h, resp, sizeof(*resp),
                          resp_h);
    if (rc != NO_ERROR) {
        return rc;
    }

    if (resp->hdr.cmd != (req->hdr.cmd | COVERAGE_AGGREGATOR_CMD_RESP_BIT)) {
        TLOGE("cmd 0x%x: unknown command\n", resp->hdr.cmd);
        return ERR_CMD_UNKNOWN;
    }

    return NO_ERROR;
}

int coverage_client_rpc(handle_t chan,
                        struct coverage_client_req* req,
                        handle_t* req_h,
                        struct coverage_client_resp* resp,
                        handle_t* resp_h) {
    int rc = coverage_rpc(chan, req, sizeof(*req), req_h, resp, sizeof(*resp),
                          resp_h);
    if (rc != NO_ERROR) {
        return rc;
    }

    if (resp->hdr.cmd != (req->hdr.cmd | COVERAGE_CLIENT_CMD_RESP_BIT)) {
        TLOGE("cmd 0x%x: unknown command\n", resp->hdr.cmd);
        return ERR_CMD_UNKNOWN;
    }

    return NO_ERROR;
}
