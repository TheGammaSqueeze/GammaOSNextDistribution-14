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

#include <lib/system_state/system_state.h>

#include <lib/tipc/tipc.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <trusty_ipc.h>
#include <trusty_log.h>
#include <uapi/err.h>

#define TLOG_TAG "lib_system_state"

/**
 * long system_state_send_req() - sends request to system_state server
 * @req:          the request header to send to the system_state server
 * @req_buf:      the request payload to send to the system_state server
 * @req_buf_len:  the length of the request payload @req_buf
 * @resp:         buffer in which to store the response header
 * @resp_buf:     buffer in which to store the response payload
 * @resp_buf_len: the size of the response buffer. Inout param, set
 *                to the actual response payload length.
 *
 * Returns: NO_ERROR on success, negative error code on failure
 */
static long system_state_send_req(struct system_state_req* req,
                                  void* req_buf,
                                  size_t req_buf_len,
                                  struct system_state_resp* resp,
                                  void* resp_buf,
                                  size_t* resp_buf_len) {
    int ret;

    handle_t session = connect(SYSTEM_STATE_PORT, IPC_CONNECT_WAIT_FOR_PORT);
    if (session == INVALID_IPC_HANDLE) {
        TLOGE("%s: failed to connect\n", __func__);
        return ERR_IO;
    }

    ret = tipc_send2(session, req, sizeof(*req), req_buf, req_buf_len);
    if (ret < 0) {
        goto err_io;
    }

    if (((size_t)ret) != sizeof(*req) + req_buf_len) {
        ret = ERR_IO;
        goto err_io;
    }

    uevent_t uevt;
    ret = wait(session, &uevt, INFINITE_TIME);
    if (ret != NO_ERROR) {
        goto err_io;
    }

    ret = tipc_recv2(session, sizeof(*resp), resp, sizeof(*resp), resp_buf,
                     *resp_buf_len);
    if (ret < 0) {
        goto err_io;
    }

    size_t read_len = (size_t)ret;
    if (read_len < sizeof(*resp)) {
        TLOGE("%s: short read (%zu)\n", __func__, read_len);
        ret = ERR_IO;
        goto err_io;
    }

    if (resp->cmd != (req->cmd | SYSTEM_STATE_CMD_RESP_BIT)) {
        TLOGE("%s: invalid response id (0x%x) for cmd (0x%x)\n", __func__,
              resp->cmd, req->cmd);
        ret = ERR_NOT_VALID;
        goto err_io;
    }

    close(session);

    *resp_buf_len = read_len - sizeof(*resp);
    return resp->result;

err_io:
    close(session);
    TLOGE("%s: failed read_msg (%d)\n", __func__, ret);
    return ret;
}

int system_state_get_flag(enum system_state_flag flag, uint64_t* valuep) {
    int ret;
    struct system_state_req req = {
            .cmd = SYSTEM_STATE_CMD_GET_FLAG,
    };
    struct system_state_get_flag_req get_flag_req = {
            .flag = flag,
    };
    struct system_state_resp resp;
    struct system_state_get_flag_resp get_flag_resp;
    size_t get_flag_resp_size = sizeof(get_flag_resp);

    ret = system_state_send_req(&req, &get_flag_req, sizeof(get_flag_req),
                                &resp, &get_flag_resp, &get_flag_resp_size);
    if (ret) {
        TLOGE("%s: request failed (%d)\n", __func__, ret);
        return ret;
    }

    if (get_flag_resp_size != sizeof(get_flag_resp)) {
        TLOGE("%s: bad response size (%zd)\n", __func__, get_flag_resp_size);
        return ERR_IO;
    }

    if (get_flag_resp.flag != flag) {
        TLOGE("%s: bad response flag (%d)\n", __func__, get_flag_resp.flag);
        return ERR_IO;
    }

    *valuep = get_flag_resp.value;

    return 0;
}
