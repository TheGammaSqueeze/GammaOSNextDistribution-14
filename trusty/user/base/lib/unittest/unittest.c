/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <lib/unittest/unittest.h>
#include <lib/coverage/common/cov_shm.h>
#include <interface/line-coverage/aggregator.h>
#include <lib/line-coverage/shm.h>
#include <lib/coverage/common/ipc.h>
#include <lib/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/macros.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <trusty/time.h>
#include <trusty_ipc.h>
#include <uapi/err.h>

#define LOG_TAG "unittest"

#include <lk/trace.h>

#define MAX_PORT_BUF_SIZE 4096 /* max size of per port buffer    */

/*
 * We can't use the normal TLOG functions because they send data through the
 * channel managed by this code.
 */

#define TLOGI(fmt, ...)                                                    \
    do {                                                                   \
        fprintf(stderr, "%s: %d: " fmt, LOG_TAG, __LINE__, ##__VA_ARGS__); \
    } while (0)

static handle_t ipc_printf_handle = INVALID_IPC_HANDLE;

static int send_msg_wait(handle_t handle, struct ipc_msg* msg) {
    int ret;
    struct uevent ev;

    ret = send_msg(handle, msg);
    if (ret != ERR_NOT_ENOUGH_BUFFER) {
        return ret;
    }

    ret = wait(handle, &ev, INFINITE_TIME);
    if (ret < 0) {
        return ret;
    }

    if (ev.event & IPC_HANDLE_POLL_SEND_UNBLOCKED) {
        return send_msg(handle, msg);
    }

    if (ev.event & IPC_HANDLE_POLL_MSG) {
        return ERR_BUSY;
    }

    if (ev.event & IPC_HANDLE_POLL_HUP) {
        return ERR_CHANNEL_CLOSED;
    }

    return ret;
}

uint64_t get_current_time_ns(void) {
    int64_t res;
    trusty_gettime(0, &res);
    return (uint64_t)res;
}

enum test_message_header {
    TEST_PASSED = 0,
    TEST_FAILED = 1,
    TEST_MESSAGE = 2,
    TEST_MESSAGE_HEADER_COUNT = 3,
};

int _vtlog(const char* fmt, va_list args) {
    char buf[256];
    struct iovec tx_iov = {buf, 1};
    ipc_msg_t tx_msg = {1, &tx_iov, 0, NULL};
    va_list ap;
    int ret;
    int slen;

    /* Print to stderr as normal */
    va_copy(ap, args);
    ret = vfprintf(stderr, fmt, ap);
    va_end(ap);
    if (ret < 0) {
        return ret;
    }

    /* Send over IPC */
    if (ipc_printf_handle == INVALID_IPC_HANDLE) {
        return 0;
    }

    va_copy(ap, args);
    /* use filtered version for consistency with stderr logging */
    ret = vsnprintf_filtered(buf + 1, sizeof(buf) - 1, fmt, ap);
    va_end(ap);

    if (ret < 0) {
        return ret;
    }
    slen = MIN(ret, (int)sizeof(buf) - 1 - 1);

    buf[0] = TEST_MESSAGE;
    tx_iov.iov_len = 1 + slen;
    ret = send_msg_wait(ipc_printf_handle, &tx_msg);
    if (ret < 0) {
        return ret;
    }

    return slen;
}

/*
 *  Application entry point
 */
int unittest_main(struct unittest** tests, size_t test_count) {
    int ret;
    handle_t hset;
    uevent_t evt = {
            .event = ~0U,
    };
    struct unittest* test;
    uuid_t unused_uuid;

    ret = handle_set_create();
    if (ret < 0) {
        TLOGI("failed to create handle set: %d\n", ret);
        return ret;
    }
    hset = ret;

    /* create control port and just wait on it */
    for (; test_count; test_count--) {
        test = *tests++;
        ret = port_create(
                test->port_name, 1, MAX_PORT_BUF_SIZE,
                IPC_PORT_ALLOW_NS_CONNECT | IPC_PORT_ALLOW_TA_CONNECT);
        if (ret < 0) {
            TLOGI("failed to create port %s: %d\n", test->port_name, ret);
            return ret;
        }
        test->_port_handle = (handle_t)ret;
        evt.handle = test->_port_handle;
        evt.cookie = test;
        ret = handle_set_ctrl(hset, HSET_ADD, &evt);
        if (ret < 0) {
            TLOGI("failed to add %s to handle set: %d\n", test->port_name, ret);
            return ret;
        }
    }

    setup_mailbox(&((struct tipc_port){ .name = test->port_name }), 1);

    /* and just wait forever for now */
    for (;;) {
        ret = wait(hset, &evt, INFINITE_TIME);
        test = evt.cookie;
        TLOGI("got event (ret=%d): ev=%x handle=%d port=%s\n", ret, evt.event,
              evt.handle, test->port_name);
        if (ret < 0)
            break;
        if (evt.event & IPC_HANDLE_POLL_READY) {
            /* get connection request */
            setup_shm();
            ret = accept(evt.handle, &unused_uuid);
            TLOGI("accept returned %d\n", ret);
            if (ret >= 0) {
                char tx_buffer[1];
                struct iovec tx_iov = {
                        tx_buffer,
                        sizeof(tx_buffer),
                };
                ipc_msg_t tx_msg = {1, &tx_iov, 0, NULL};

                /* then run unittest test */
                ipc_printf_handle = ret;
                tx_buffer[0] = test->run_test(test) ? TEST_PASSED : TEST_FAILED;
                ipc_printf_handle = INVALID_IPC_HANDLE;

                send_msg_wait(ret, &tx_msg);

                /* and close it */
                close(ret);

                dump_shm();
            }
        }
    }

    return ret;
}
