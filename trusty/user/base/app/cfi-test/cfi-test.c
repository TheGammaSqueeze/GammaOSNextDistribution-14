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

#include <lib/tipc/tipc.h>
#include <lib/unittest/unittest.h>
#include <trusty_unittest.h>
#include <uapi/err.h>

#include <cfi-crasher.h>
#include <cfi_crasher_consts.h>

#define TLOG_TAG "cfi-test"

/*
 * Sends command to crasher app. Call crasher_wait after to wait for a
 * reply or channel close. On success, returns zero.
 */
static int crasher_command(handle_t chan, enum crasher_command cmd) {
    int ret;
    struct crasher_msg msg = {
            .cmd = cmd,
    };

    ret = tipc_send1(chan, &msg, sizeof(msg));
    ASSERT_GE(ret, 0);
    ASSERT_EQ(ret, sizeof(msg));

    return 0;

test_abort:
    /* Use ERR_IO to indicate internal error with the test app */
    return ERR_IO;
}

/*
 * This function should always be called after crasher_command since it
 * assumes the test app has already sent a message to the crasher app
 * with the command. In the non-crashing case, returns a positive integer
 * equaling the message command echoed back by the server.
 */
static int crasher_wait(handle_t chan) {
    int ret;
    struct uevent evt;
    ret = wait(chan, &evt, INFINITE_TIME);
    if (ret) { /* error while waiting on channel */
        return ret;
    }
    /*
     * Receiving message and crashing the app should never happen with
     * CFI enabled. If both IPC_HANDLE_POLL_MSG and IPC_HANDLE_POLL_HUP
     * are set, either an internal error occurred or CRASHER_NOT_ENTRY
     * ran with CFI not enabled. We expect exactly one of IPC_HANDLE_POLL_MSG
     * and IPC_HANDLE_POLL_HUP to be set.
     */
    if (evt.event & IPC_HANDLE_POLL_HUP) {
        ASSERT_EQ(evt.event & IPC_HANDLE_POLL_MSG, 0);
        return ERR_CHANNEL_CLOSED;
    }
    ASSERT_NE(evt.event & IPC_HANDLE_POLL_MSG, 0);

    struct crasher_msg msg;
    ret = tipc_recv1(chan, sizeof(msg), &msg, sizeof(msg));
    if (ret < 0) {
        return ret;
    }
    ASSERT_EQ(ret, sizeof(msg));
    /*
     * Return the cmd if we reach this point, that is the app did not crash.
     * If the crasher did not crash, it should echo back the original command,
     * or CRASHER_VOID in the special case where crasher_void executes on
     * the server side.
     */
    return msg.cmd;

test_abort:
    /* Use ERR_IO to indicate internal error with the test app */
    return ERR_IO;
}

typedef struct cfi_crash {
    handle_t chan;
} cfi_crash_t;

TEST_F_SETUP(cfi_crash) {
    _state->chan = INVALID_IPC_HANDLE;
    ASSERT_EQ(tipc_connect(&_state->chan, CFI_CRASHER_PORT), 0);

test_abort:;
}

TEST_F_TEARDOWN(cfi_crash) {
    close(_state->chan);
}

TEST_F(cfi_crash, nop) {
    EXPECT_EQ(crasher_command(_state->chan, CRASHER_NOP), 0);
    EXPECT_EQ(crasher_wait(_state->chan), CRASHER_NOP);
}

TEST_F(cfi_crash, correct) {
    EXPECT_EQ(crasher_command(_state->chan, CRASHER_CORRECT), 0);
    EXPECT_EQ(crasher_wait(_state->chan), CRASHER_CORRECT);
}

TEST_F(cfi_crash, entry) {
    /*
     * CRASHER_ENTRY suppresses the second reply from crasher_void
     * so we only expect one message back at the end of crasher_on_message.
     */
    EXPECT_EQ(crasher_command(_state->chan, CRASHER_ENTRY), 0);
    EXPECT_EQ(crasher_wait(_state->chan), CRASHER_ENTRY);
}

TEST_F(cfi_crash, exclude_wrong_type) {
    EXPECT_EQ(crasher_command(_state->chan, CRASHER_EXCLUDE_WRONG_TYPE), 0);
    EXPECT_EQ(crasher_wait(_state->chan), CRASHER_EXCLUDE_WRONG_TYPE);
}

TEST_F(cfi_crash, exclude_not_entry) {
    /*
     * CRASHER_EXCLUDE_NOT_ENTRY expects one event with a message and a
     * subsequent event indicating the channel is closed.
     */
    EXPECT_EQ(crasher_command(_state->chan, CRASHER_EXCLUDE_NOT_ENTRY), 0);
    EXPECT_EQ(crasher_wait(_state->chan), CRASHER_VOID);
    EXPECT_EQ(crasher_wait(_state->chan), ERR_CHANNEL_CLOSED);
}

TEST_F(cfi_crash, wrong_type) {
    EXPECT_EQ(crasher_command(_state->chan, CRASHER_WRONG_TYPE), 0);
    EXPECT_EQ(crasher_wait(_state->chan), ERR_CHANNEL_CLOSED);
}

TEST_F(cfi_crash, not_entry) {
    EXPECT_EQ(crasher_command(_state->chan, CRASHER_NOT_ENTRY), 0);
    EXPECT_EQ(crasher_wait(_state->chan), ERR_CHANNEL_CLOSED);
}

PORT_TEST(cfi_crash, "com.android.trusty.cfitest")
