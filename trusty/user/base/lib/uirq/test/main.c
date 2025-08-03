/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <stddef.h>
#include <stdio.h>
#include <uapi/err.h>

#include <lib/uirq/uirq.h>
#include <trusty_ipc.h>
#include <trusty_uio.h>

#define TLOG_TAG "uirq-unittest"
#include <trusty_unittest.h>

#define MAX_UIRQ_CNT 100
#define TEST_UIRQ_10MS "test-uirq-10ms"
#define TEST_UIRQ_NO_ACCESS "test-uirq-no-access"
#define TEST_UIRQ_PORT "test-uirq-port"

#define FAIL_TIMEOUT 5000

typedef struct uirq {
    handle_t hset;
    handle_t hevts;
    handle_t hevt1;
    handle_t hevt2;
} uirq_t;

TEST_F_SETUP(uirq) {
    _state->hset = INVALID_IPC_HANDLE;
    _state->hevts = INVALID_IPC_HANDLE;
    _state->hevt1 = INVALID_IPC_HANDLE;
    _state->hevt2 = INVALID_IPC_HANDLE;
}

TEST_F_TEARDOWN(uirq) {
    (void)close(_state->hset);
    (void)close(_state->hevts);
    (void)close(_state->hevt1);
    (void)close(_state->hevt2);
}

TEST_F(uirq, uirq_test_open_no_access) {
    /* open unaccessible uirq event */
    _state->hevt1 = uirq_open(TEST_UIRQ_NO_ACCESS, 0);
    EXPECT_EQ(ERR_NOT_FOUND, _state->hevt1, "open no-access uirq event");

test_abort:;
}

TEST_F(uirq, uirq_test_handle_single) {
    int rc;
    uevent_t uevt;
    uint32_t evt_cnt = 0;

    /* open uirq */
    _state->hevt1 = uirq_open(TEST_UIRQ_10MS, 0);
    ASSERT_GE(_state->hevt1, 0, "open uirq");

    /* handle test uirq event MAX_UIRQ_CNT times */
    while (evt_cnt < MAX_UIRQ_CNT) {
        rc = wait(_state->hevt1, &uevt, FAIL_TIMEOUT);
        ASSERT_EQ(0, rc, "wait for uirq");
        ASSERT_EQ(uevt.handle, _state->hevt1);

        evt_cnt++;
        rc = uirq_ack_handled(_state->hevt1);
        ASSERT_EQ(0, rc, "ack uirq");
    }
    EXPECT_EQ(MAX_UIRQ_CNT, evt_cnt, "evt1  count");

test_abort:;
}

TEST_F(uirq, uirq_test_handle_multiple) {
    int rc;
    uevent_t uevt;
    uint32_t ttl_cnt = 0;
    uint32_t evt1_cnt = 0;
    uint32_t evt2_cnt = 0;

    /* create handle set */
    _state->hset = handle_set_create();
    ASSERT_GE(_state->hset, 0, "create handle set");

    /* open uirq event */
    _state->hevt1 = uirq_open(TEST_UIRQ_10MS, 0);
    ASSERT_GE(_state->hevt1, 0, "open uirq");

    /* open the same uirq event */
    _state->hevt2 = uirq_open(TEST_UIRQ_10MS, 0);
    ASSERT_GE(_state->hevt2, 0, "open uirq");

    /* Add all uirq events to handle set */
    uevt.handle = _state->hevt1;
    uevt.event = ~0U;
    uevt.cookie = NULL;
    rc = handle_set_ctrl(_state->hset, HSET_ADD, &uevt);
    ASSERT_EQ(0, rc);

    uevt.handle = _state->hevt2;
    uevt.event = ~0U;
    uevt.cookie = NULL;
    rc = handle_set_ctrl(_state->hset, HSET_ADD, &uevt);
    ASSERT_EQ(0, rc);

    /* trigger and handle uirq event MAX_UIRQ_CNT times */
    while (ttl_cnt < MAX_UIRQ_CNT * 2) {
        rc = wait(_state->hset, &uevt, FAIL_TIMEOUT);
        ASSERT_EQ(0, rc, "wait for uirq");

        if (uevt.handle == _state->hevt1) {
            evt1_cnt++;
            rc = uirq_ack_handled(_state->hevt1);
            ASSERT_EQ(0, rc, "ack uirq");

        } else if (uevt.handle == _state->hevt2) {
            evt2_cnt++;
            rc = uirq_ack_handled(_state->hevt2);
            ASSERT_EQ(0, rc, "ack uirq");
        } else {
            break;
        }
        ttl_cnt++;
    }
    EXPECT_EQ(MAX_UIRQ_CNT * 2, ttl_cnt, "total count");
    EXPECT_EQ(MAX_UIRQ_CNT, evt1_cnt, "evt1  count");
    EXPECT_EQ(MAX_UIRQ_CNT, evt2_cnt, "evt2  count");

test_abort:;
}

TEST(uirq, invalid_ack_handled) {
    int rc;
    uint32_t cmd;
    handle_t port;

    /* Create port to use it as target for this test */
    rc = port_create(TEST_UIRQ_PORT, 1, 1, IPC_PORT_ALLOW_TA_CONNECT);
    ASSERT_GE(rc, 0);
    port = (handle_t)rc;

    /* ack existing but invalid handle: should return ERR_NOT_SUPPORTED */
    rc = uirq_ack_handled(port);
    EXPECT_EQ(ERR_NOT_SUPPORTED, rc);

    /* use direct write to invalid handle: should return ERR_NOT_SUPPORTED */
    cmd = 1;
    rc = trusty_write(port, &cmd, sizeof(cmd));
    EXPECT_EQ(ERR_NOT_SUPPORTED, rc);

    /* use direct read from invalid handle: should return ERR_NOT_SUPPORTED */
    rc = trusty_read(port, &cmd, sizeof(cmd));
    EXPECT_EQ(ERR_NOT_SUPPORTED, rc);

    close(port);
test_abort:;
}

PORT_TEST(uirq, "com.android.uirq-unittest");
