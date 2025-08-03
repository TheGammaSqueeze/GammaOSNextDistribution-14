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

#define TLOG_TAG "lib_system_state_server"

#include <lib/system_state_server/system_state_server.h>
#include <lib/tipc/tipc_srv.h>
#include <trusty_log.h>
#include <uapi/err.h>

static int system_state_on_message(const struct tipc_port* port,
                                   handle_t chan,
                                   void* ctx) {
    int ret;
    struct {
        struct system_state_req hdr;
        union {
            struct system_state_get_flag_req get_flag;
        };
    } req;
    size_t req_payload_size;
    struct {
        struct system_state_resp hdr;
        union {
            struct system_state_get_flag_resp get_flag;
        };
    } resp = {};
    size_t resp_payload_size = 0;

    ret = tipc_recv1(chan, sizeof(req.hdr), &req, sizeof(req));
    if (ret < 0) {
        TLOGE("tipc_recv1 failed (%d)\n", ret);
        return ret;
    }
    if ((size_t)ret < sizeof(req.hdr)) {
        TLOGE("request too short (%d)\n", ret);
        return ERR_BAD_LEN;
    }
    req_payload_size = ret - sizeof(req.hdr);
    if (req.hdr.reserved) {
        TLOGE("bad request, reserved not 0, (%d)\n", req.hdr.reserved);
        return ERR_INVALID_ARGS;
    }

    switch (req.hdr.cmd) {
    case SYSTEM_STATE_CMD_GET_FLAG:
        if (req_payload_size != sizeof(req.get_flag)) {
            TLOGE("bad get_flags payload size (%zd)\n", req_payload_size);
            ret = ERR_INVALID_ARGS;
            break;
        }
        ret = system_state_server_get_flag(req.get_flag.flag,
                                           &resp.get_flag.value);
        if (!ret) {
            resp.get_flag.flag = req.get_flag.flag;
            resp_payload_size = sizeof(resp.get_flag);
        }
        break;
    default:
        ret = ERR_CMD_UNKNOWN;
    }
    resp.hdr.cmd = req.hdr.cmd | SYSTEM_STATE_CMD_RESP_BIT;
    resp.hdr.result = ret;
    ret = tipc_send1(chan, &resp, sizeof(resp.hdr) + resp_payload_size);
    if (ret < 0) {
        TLOGE("tipc_send1 failed (%d)\n", ret);
        return ret;
    }
    if ((size_t)ret != sizeof(resp.hdr) + resp_payload_size) {
        TLOGE("bad len (%d) from send_msg()\n", ret);
        return ERR_IO;
    }
    return 0;
}

int add_system_state_service(struct tipc_hset* hset) {
    static struct tipc_port_acl acl = {
            .flags = IPC_PORT_ALLOW_TA_CONNECT,
    };
    static struct tipc_port port = {
            .name = SYSTEM_STATE_PORT,
            .msg_max_size = SYSTEM_STATE_MAX_MESSAGE_SIZE,
            .msg_queue_len = 1,
            .acl = &acl,
    };
    static struct tipc_srv_ops ops = {
            .on_message = system_state_on_message,
    };
    return tipc_add_service(hset, &port, 1, 1, &ops);
}
