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

#define TLOG_TAG "crasher"

#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include <trusty_log.h>
#include <uapi/err.h>

#include <lib/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>

#include <crasher.h>
#include <crasher_consts.h>
#include <crasher_funcs.h>

static struct tipc_port_acl crasher_port_acl = {
        .flags = IPC_PORT_ALLOW_TA_CONNECT,
        .uuid_num = 0,
        .uuids = NULL,
        .extra_data = NULL,
};

static struct tipc_port crasher_port = {
        .name = CRASHER_PORT,
        .msg_max_size = sizeof(struct crasher_msg),
        .msg_queue_len = 1,
        .acl = &crasher_port_acl,
        .priv = NULL,
};

static int __attribute__((no_sanitize("undefined")))
crasher_on_message(const struct tipc_port* port, handle_t chan, void* ctx) {
    assert(port == &crasher_port);
    assert(ctx == NULL);
    struct crasher_msg msg;

    int ret = tipc_recv1(chan, sizeof(msg), &msg, sizeof(msg));
    if (ret < 0 || ret != sizeof(msg)) {
        TLOGE("Failed to receive message (%d)\n", ret);
        return ret;
    }

    TLOGD("cmd %d\n", msg.cmd);

    switch (msg.cmd) {
    case CRASHER_NOP:
        TLOGI("nop\n");
        break;
    case CRASHER_EXIT_SUCCESS:
        TLOGI("exit success\n");
        exit(EXIT_SUCCESS);
        break;
    case CRASHER_EXIT_FAILURE:
        TLOGI("exit failure\n");
        exit(EXIT_FAILURE);
        break;
    case CRASHER_READ_NULL_PTR:
        TLOGI("read null\n");
        READ_ONCE(*(uint8_t*)NULL);
        break;
    case CRASHER_READ_BAD_PTR:
        TLOGI("read bad ptr\n");
        READ_ONCE(*(uint8_t*)1);
        break;
    case CRASHER_WRITE_BAD_PTR:
        TLOGI("write bad ptr\n");
        WRITE_ONCE(*(uint8_t*)1, 0);
        break;
    case CRASHER_WRITE_RO_PTR:
        TLOGI("write ro ptr\n");
        WRITE_ONCE(*(uint8_t*)crasher_rodata_func, 0);
        break;
    case CRASHER_EXEC_RODATA:
        TLOGI("call crasher_rodata_func\n");
        crasher_rodata_func();
        break;
    case CRASHER_EXEC_DATA:
        TLOGI("call crasher_data_func\n");
        crasher_data_func();
        break;
#ifdef __aarch64__
    case CRASHER_BRK:
        TLOGI("BRK instruction\n");
        __asm__("brk #42");
        break;
#endif
    default:
        TLOGE("Bad command: %d\n", msg.cmd);
        return -1;
    }

    ret = tipc_send1(chan, &msg, sizeof(msg));
    if (ret < 0 || ret != sizeof(msg)) {
        TLOGE("Failed to send message (%d)\n", ret);
        return ret < 0 ? ret : ERR_IO;
    }
    TLOGD("cmd %d done\n", msg.cmd);

    return 0;
}

static struct tipc_srv_ops crasher_ops = {
        .on_message = crasher_on_message,
};

int main(void) {
    struct tipc_hset* hset = tipc_hset_create();
    if (!hset) {
        return -1;
    }

    int rc = tipc_add_service(hset, &crasher_port, 1, 1, &crasher_ops);
    if (rc < 0) {
        return rc;
    }

    rc = tipc_run_event_loop(hset);
    TLOGE("crasher going down: (%d)\n", rc);
    return rc;
}
