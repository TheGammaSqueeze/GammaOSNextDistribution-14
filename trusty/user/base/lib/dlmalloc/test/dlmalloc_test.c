/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <dlmalloc_app.h>
#include <dlmalloc_consts.h>
#include <errno.h>
#include <lib/tipc/tipc.h>
#include <lib/unittest/unittest.h>
#include <stdlib.h>
#include <trusty_unittest.h>
#include <uapi/err.h>

#define TLOG_TAG "dlmalloc_test"

/*
 * Sends command to app and then waits for a
 * reply or channel close. In the non-crashing case, the server
 * should echo back the original command and dlmalloc_srv_rpc returns
 * NO_ERROR.
 */
static void dlmalloc_srv_rpc_expect(handle_t chan,
                                    enum dlmalloc_test_command cmd,
                                    int expected) {
    int ret;
    struct dlmalloc_test_msg msg = {
            .cmd = cmd,
    };

    ret = tipc_send1(chan, &msg, sizeof(msg));
    ASSERT_EQ(ret, sizeof(msg));

    struct uevent evt;
    ret = wait(chan, &evt, INFINITE_TIME);
    ASSERT_EQ(NO_ERROR, ret);

    int event = evt.event & IPC_HANDLE_POLL_MSG;
    if (evt.event & IPC_HANDLE_POLL_HUP) {
        ASSERT_EQ(event, 0);
        ASSERT_EQ(ERR_CHANNEL_CLOSED, expected);
        goto test_abort;
    }
    ASSERT_NE(event, 0);

    ret = tipc_recv1(chan, sizeof(msg), &msg, sizeof(msg));
    ASSERT_EQ(ret, sizeof(msg));
    ASSERT_EQ(msg.cmd, cmd);

test_abort:;
}

static void dlmalloc_srv_rpc(handle_t chan, enum dlmalloc_test_command cmd) {
    dlmalloc_srv_rpc_expect(chan, cmd, NO_ERROR);
}

typedef struct dlmalloc_info {
    handle_t chan;
} dlmalloc_info_t;

TEST_F_SETUP(dlmalloc_info) {
    _state->chan = INVALID_IPC_HANDLE;
    ASSERT_EQ(tipc_connect(&_state->chan, DLMALLOC_TEST_SRV_PORT), 0);

test_abort:;
}

TEST_F_TEARDOWN(dlmalloc_info) {
    close(_state->chan);
}

TEST_F(dlmalloc_info, nop) {
    dlmalloc_srv_rpc(_state->chan, DLMALLOC_TEST_NOP);
}

TEST_F(dlmalloc_info, one_malloc) {
    dlmalloc_srv_rpc(_state->chan, DLMALLOC_TEST_ONE_MALLOC);
}

TEST_F(dlmalloc_info, one_calloc) {
    dlmalloc_srv_rpc(_state->chan, DLMALLOC_TEST_ONE_CALLOC);
}

TEST_F(dlmalloc_info, one_realloc) {
    dlmalloc_srv_rpc(_state->chan, DLMALLOC_TEST_ONE_REALLOC);
}

TEST_F(dlmalloc_info, many_malloc) {
    dlmalloc_srv_rpc(_state->chan, DLMALLOC_TEST_MANY_MALLOC);
}

TEST_F(dlmalloc_info, one_new) {
    dlmalloc_srv_rpc(_state->chan, DLMALLOC_TEST_ONE_NEW);
}

TEST_F(dlmalloc_info, one_new_arr) {
    dlmalloc_srv_rpc(_state->chan, DLMALLOC_TEST_ONE_NEW_ARR);
}

TEST_F(dlmalloc_info, malloc_and_new) {
    dlmalloc_srv_rpc(_state->chan, DLMALLOC_TEST_MALLOC_AND_NEW);
}

TEST_F(dlmalloc_info, double_free) {
    dlmalloc_srv_rpc_expect(_state->chan, DLMALLOC_TEST_DOUBLE_FREE,
                            ERR_CHANNEL_CLOSED);
}

TEST_F(dlmalloc_info, realloc_after_free) {
    dlmalloc_srv_rpc_expect(_state->chan, DLMALLOC_TEST_REALLOC_AFTER_FREE,
                            ERR_CHANNEL_CLOSED);
}

TEST_F(dlmalloc_info, alloc_large) {
    dlmalloc_srv_rpc(_state->chan, DLMALLOC_TEST_ALLOC_LARGE);
}

TEST_F(dlmalloc_info, malloc_loop) {
    for (int i = 0; i < 1024; i++) {
        void* ptr = malloc(4096 * 3);
        ASSERT_NE(0, ptr, "iteration %d", i);
        free(ptr);
    }

test_abort:;
}

#define CLEAR_ERRNO() \
    do {              \
        errno = 0;    \
    } while (0)

TEST_F(dlmalloc_info, malloc_oom) {
    void* ptr = malloc(8192 * 1024);
    ASSERT_EQ(0, ptr);
    /* TODO: ENOMEM */
    CLEAR_ERRNO();

test_abort:;
}

static uintptr_t expected_malloc_alignment(size_t size) {
    /* TODO use ffs? */
    if (size >= 16) {
        return sizeof(void*) * 2;
    } else if (size >= 8) {
        return 8;
    } else if (size >= 4) {
        return 4;
    } else if (size >= 2) {
        return 2;
    } else {
        return 1;
    }
}

TEST_F(dlmalloc_info, malloc_alignment) {
    for (int size = 2; size < 256; size++) {
        const uintptr_t alignment_mask = expected_malloc_alignment(size) - 1;
        void* ptr1 = malloc(size);
        void* ptr2 = malloc(size / 2); /* Try to shake up the alignment. */
        void* ptr3 = malloc(size);

        ASSERT_EQ(0, (uintptr_t)ptr1 & alignment_mask, "size %d / align %zu",
                  size, alignment_mask + 1);
        ASSERT_EQ(0, (uintptr_t)ptr3 & alignment_mask, "size %d / align %zu",
                  size, alignment_mask + 1);

        free(ptr3);
        free(ptr2);
        free(ptr1);
    }
test_abort:;
}

PORT_TEST(dlmalloc_info, "com.android.trusty.dlmalloctest")
