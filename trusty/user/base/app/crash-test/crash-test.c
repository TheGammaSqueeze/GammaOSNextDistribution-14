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

#include <uapi/err.h>

#include <lib/tipc/tipc.h>
#include <lib/unittest/unittest.h>

#include <trusty/time.h>
#include <trusty_unittest.h>

#include <crasher.h>
#include <crasher_consts.h>

#define TLOG_TAG "crash-test"

#define US2NS(us) ((us) * (1000LL))
#define MS2NS(ms) (US2NS(ms) * 1000LL)
#define S2NS(s) (MS2NS(s) * 1000LL)

static int crasher_connect(handle_t* chan) {
    return tipc_connect(chan, CRASHER_PORT);
}

static int crasher_command(handle_t chan, enum crasher_command cmd) {
    int ret;
    struct uevent evt;
    struct crasher_msg msg = {
            .cmd = cmd,
    };

    ret = tipc_send1(chan, &msg, sizeof(msg));
    ASSERT_GE(ret, 0);
    ASSERT_EQ(ret, sizeof(msg));

    ret = wait(chan, &evt, INFINITE_TIME);
    if (ret) {
        EXPECT_EQ(ret, ERR_CHANNEL_CLOSED);
        return ret;
    }
    if (!(evt.event & IPC_HANDLE_POLL_MSG)) {
        EXPECT_EQ(evt.event, IPC_HANDLE_POLL_HUP);
        if (evt.event & IPC_HANDLE_POLL_HUP) {
            return ERR_CHANNEL_CLOSED;
        }
        return ERR_IO;
    }

    ret = tipc_recv1(chan, sizeof(msg), &msg, sizeof(msg));
    if (ret < 0) {
        EXPECT_EQ(ret, ERR_CHANNEL_CLOSED);
        return ret;
    }
    ASSERT_EQ(ret, sizeof(msg));

    return 0;

test_abort:
    return ret < 0 ? ret : ERR_IO;
}

TEST(crash, connect) {
    handle_t chan = INVALID_IPC_HANDLE;
    ASSERT_EQ(crasher_connect(&chan), 0);
    ASSERT_EQ(crasher_command(chan, CRASHER_NOP), 0);

test_abort:
    close(chan);
}

TEST(crash, exit_success) {
    handle_t chan1 = INVALID_IPC_HANDLE;
    handle_t chan2 = INVALID_IPC_HANDLE;
    int64_t t1, t2;

    ASSERT_EQ(crasher_connect(&chan1), 0);
    trusty_gettime(0, &t1);
    ASSERT_EQ(crasher_command(chan1, CRASHER_EXIT_SUCCESS), ERR_CHANNEL_CLOSED);
    ASSERT_EQ(crasher_connect(&chan2), 0);
    trusty_gettime(0, &t2);
    ASSERT_EQ(crasher_command(chan2, CRASHER_EXIT_SUCCESS), ERR_CHANNEL_CLOSED);
    ASSERT_LT(t2 - t1, S2NS(1));

test_abort:
    close(chan1);
    close(chan2);
}

TEST(crash, exit_failure) {
    handle_t chan1 = INVALID_IPC_HANDLE;
    handle_t chan2 = INVALID_IPC_HANDLE;
    int64_t t1, t2;

    ASSERT_EQ(crasher_connect(&chan1), 0);
    trusty_gettime(0, &t1);
    ASSERT_EQ(crasher_command(chan1, CRASHER_EXIT_FAILURE), ERR_CHANNEL_CLOSED);
    ASSERT_EQ(crasher_connect(&chan2), 0);
    trusty_gettime(0, &t2);
    ASSERT_EQ(crasher_command(chan2, CRASHER_EXIT_FAILURE), ERR_CHANNEL_CLOSED);
    ASSERT_GT(t2 - t1, S2NS(1));

test_abort:
    close(chan1);
    close(chan2);
}

TEST(crash, read_null_ptr) {
    handle_t chan = INVALID_IPC_HANDLE;
    ASSERT_EQ(crasher_connect(&chan), 0);
    ASSERT_EQ(crasher_command(chan, CRASHER_READ_NULL_PTR), ERR_CHANNEL_CLOSED);

test_abort:
    close(chan);
}

#if __aarch64__
TEST(crash, brk_instruction) {
    handle_t chan = INVALID_IPC_HANDLE;
    ASSERT_EQ(crasher_connect(&chan), 0);
    ASSERT_EQ(crasher_command(chan, CRASHER_BRK), ERR_CHANNEL_CLOSED);

test_abort:
    close(chan);
}
#endif

TEST(crash, read_bad_ptr) {
    handle_t chan = INVALID_IPC_HANDLE;
    ASSERT_EQ(crasher_connect(&chan), 0);
    ASSERT_EQ(crasher_command(chan, CRASHER_READ_BAD_PTR), ERR_CHANNEL_CLOSED);

test_abort:
    close(chan);
}

TEST(crash, write_bad_ptr) {
    handle_t chan = INVALID_IPC_HANDLE;
    ASSERT_EQ(crasher_connect(&chan), 0);
    ASSERT_EQ(crasher_command(chan, CRASHER_WRITE_BAD_PTR), ERR_CHANNEL_CLOSED);

test_abort:
    close(chan);
}

TEST(crash, write_ro_ptr) {
    handle_t chan = INVALID_IPC_HANDLE;
    ASSERT_EQ(crasher_connect(&chan), 0);
    ASSERT_EQ(crasher_command(chan, CRASHER_WRITE_RO_PTR), ERR_CHANNEL_CLOSED);

test_abort:
    close(chan);
}

TEST(crash, exec_rodata) {
    handle_t chan = INVALID_IPC_HANDLE;
    ASSERT_EQ(crasher_connect(&chan), 0);
    ASSERT_EQ(crasher_command(chan, CRASHER_EXEC_RODATA), ERR_CHANNEL_CLOSED);

test_abort:
    close(chan);
}

TEST(crash, exec_data) {
    handle_t chan = INVALID_IPC_HANDLE;
    ASSERT_EQ(crasher_connect(&chan), 0);
    ASSERT_EQ(crasher_command(chan, CRASHER_EXEC_DATA), ERR_CHANNEL_CLOSED);

test_abort:
    close(chan);
}

PORT_TEST(crash, "com.android.trusty.crashtest")
