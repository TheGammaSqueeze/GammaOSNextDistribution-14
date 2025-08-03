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

#include <assert.h>
#include <err.h>
#include <lib/trusty/event.h>
#include <lib/trusty/handle_set.h>
#include <lib/trusty/ipc_msg.h>
#include <lib/trusty/uuid.h>
#include <lib/unittest/unittest.h>

#include <trace.h>

#define EXPECTED_TO_FAIL_TIMEOUT_MSEC 1000
#define NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC (30000)

#define MAX_EVT_CNT 100
#define TEST_EVS_NAME1 "com.android.trusty.test-uevent-source1"
#define TEST_EVS_NAME2 "com.android.trusty.test-uevent-source2"
#define TEST_EVT_NAME1 "com.android.trusty.test-uevent-client1"
#define TEST_EVT_NAME2 "com.android.trusty.test-uevent-client2"

typedef struct uirq {
    struct handle* hset;
    struct handle* hevts;
    struct handle* hevt1;
    struct handle* hevt2;
    struct handle_ref hevts_ref;
    struct handle_ref hevt1_ref;
    struct handle_ref hevt2_ref;
} uirq_t;

TEST_F_SETUP(uirq) {
    memset(_state, 0, sizeof(*_state));
}

TEST_F_TEARDOWN(uirq) {
    if (_state->hevts_ref.handle) {
        handle_set_detach_ref(&_state->hevts_ref);
    }

    if (_state->hevt1_ref.handle) {
        handle_set_detach_ref(&_state->hevt1_ref);
    }

    if (_state->hevt2_ref.handle) {
        handle_set_detach_ref(&_state->hevt2_ref);
    }

    if (_state->hevts) {
        handle_close(_state->hevts);
    }

    if (_state->hevt1) {
        handle_close(_state->hevt1);
    }

    if (_state->hevt2) {
        handle_close(_state->hevt2);
    }

    if (_state->hset) {
        handle_close(_state->hset);
    }
}

TEST_F(uirq, event_source_create_invalid) {
    int rc;

    rc = event_source_create(NULL, NULL, NULL, NULL, 0, 0, &_state->hevts);
    ASSERT_EQ(ERR_INVALID_ARGS, rc);

    rc = event_source_create("", NULL, NULL, NULL, 0, 0, &_state->hevts);
    ASSERT_EQ(ERR_INVALID_ARGS, rc);
test_abort:;
}

TEST_F(uirq, event_source_create_close) {
    int rc;
    uint32_t cnt;

    /* create/close named event 10000 times */
    for (cnt = 0; cnt < 10000; cnt++) {
        rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                                 &_state->hevts);
        ASSERT_EQ(0, rc);

        /* then close it */
        handle_close(_state->hevts);
        _state->hevts = NULL;
    }
test_abort:;
}

TEST_F(uirq, event_source_create_existing) {
    int rc;
    struct handle* h = NULL;

    /* create named event */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                             &_state->hevts);
    ASSERT_EQ(0, rc);

    /* then publish it */
    rc = event_source_publish(_state->hevts);
    ASSERT_EQ(0, rc);

    /* try to create event with the same name again */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0, &h);
    ASSERT_EQ(0, rc);

    /* then publish it */
    rc = event_source_publish(h);
    EXPECT_EQ(ERR_ALREADY_EXISTS, rc);

    if (h) {
        handle_close(h);
    }

test_abort:;
}

TEST_F(uirq, event_source_wait_no_clients) {
    int rc;
    uint32_t uevt;

    /* create named event */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                             &_state->hevts);
    ASSERT_EQ(0, rc);

    rc = handle_wait(_state->hevts, &uevt, EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on created event");

    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "signal event");

    /* event without clients becomes ready */
    rc = handle_wait(_state->hevts, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on signaled event");

    /* do it again: it should time out */
    rc = handle_wait(_state->hevts, &uevt, EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on created event");

    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "signal event");

    rc = handle_wait(_state->hevts, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on created event");

test_abort:;
}

TEST_F(uirq, event_client_open_invalid) {
    int rc;
    struct uuid cid = zero_uuid;

    /* open named event with NULL name */
    rc = event_source_open(&cid, NULL, 0, 0, &_state->hevt1);
    ASSERT_EQ(ERR_INVALID_ARGS, rc);

    /* open named event with empty name */
    rc = event_source_open(&cid, "", 1, 0, &_state->hevt1);
    ASSERT_EQ(ERR_INVALID_ARGS, rc);

    /* open non-existing named event */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt1);
    ASSERT_EQ(ERR_NOT_FOUND, rc);

test_abort:;
}

TEST_F(uirq, event_client_open_close) {
    int rc;
    struct uuid cid = zero_uuid;

    /* create named event source */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                             &_state->hevts);
    ASSERT_EQ(0, rc);

    rc = event_source_publish(_state->hevts);
    ASSERT_EQ(0, rc);

    /* open/close the same named event 10000 times */
    for (int i = 0; i < 10000; i++) {
        rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1,
                               0, &_state->hevt1);
        ASSERT_EQ(0, rc);

        handle_close(_state->hevt1);
        _state->hevt1 = NULL;
    }

test_abort:;
}

TEST_F(uirq, event_client_wait_single) {
    int rc;
    uint32_t uevt;
    struct handle_ref ref;
    struct uuid cid = zero_uuid;

    /* create handle set */
    _state->hset = handle_set_create();

    /* create named event source */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                             &_state->hevts);
    ASSERT_EQ(0, rc);

    rc = event_source_publish(_state->hevts);
    ASSERT_EQ(0, rc);

    /* open the same named event */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt1);
    ASSERT_EQ(0, rc);

    /* wait on unsignaled event: should timeout */
    rc = handle_wait(_state->hevt1, &uevt, EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on client");

    /* signal event */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /* wait on signaled event: should return as it is signaled  */
    rc = handle_wait(_state->hevt1, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client");

    /* wait on event source: should timeout as we have not notified */
    rc = handle_wait(_state->hevts, &uevt, EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on source");

    /* notify that event is handled to put source into handled state */
    rc = event_client_notify_handled(_state->hevt1);
    EXPECT_EQ(0, rc, "notify event handled");

    /* wait on event source again: should return as event is handled */
    rc = handle_wait(_state->hevts, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on source");

    /* Check handle sets: add both events to handle set */
    memset(&_state->hevts_ref, 0, sizeof(_state->hevts_ref));
    _state->hevts_ref.handle = _state->hevts;
    _state->hevts_ref.emask = ~0U;
    _state->hevts_ref.cookie = NULL;
    rc = handle_set_attach(_state->hset, &_state->hevts_ref);
    ASSERT_EQ(0, rc);

    memset(&_state->hevt1_ref, 0, sizeof(_state->hevt1_ref));
    _state->hevt1_ref.handle = _state->hevt1;
    _state->hevt1_ref.emask = ~0U;
    _state->hevt1_ref.cookie = NULL;
    rc = handle_set_attach(_state->hset, &_state->hevt1_ref);
    ASSERT_EQ(0, rc);

    /* trigger and handle event MAX_EVT_CNT times */
    uint32_t cnt = MAX_EVT_CNT;
    rc = event_source_signal(_state->hevts);
    ASSERT_EQ(0, rc, "notify event signaled");
    while (cnt) {
        rc = handle_set_wait(_state->hset, &ref,
                             NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
        ASSERT_EQ(0, rc, "wait any");

        if (ref.handle) {
            /* dec ref obtained by handle_set_wait */
            handle_decref(ref.handle);
        }

        if (ref.handle == _state->hevt1) {
            cnt--;
            rc = event_client_notify_handled(_state->hevt1);
            ASSERT_EQ(0, rc, "notify event handled");
        } else if (ref.handle == _state->hevts) {
            rc = event_source_signal(_state->hevts);
            ASSERT_EQ(0, rc, "notify event signaled");
        } else {
            break;
        }
    }
    EXPECT_EQ(0, cnt, "event counter");

test_abort:;
}

TEST_F(uirq, event_client_wait_multiple) {
    int rc;
    struct handle_ref ref;
    struct uuid cid = zero_uuid;

    /* create handle set */
    _state->hset = handle_set_create();

    /* create named event source */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                             &_state->hevts);
    ASSERT_EQ(0, rc);

    rc = event_source_publish(_state->hevts);
    ASSERT_EQ(0, rc);

    /* open named event */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt1);
    ASSERT_EQ(0, rc);

    /* open the same named event */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt2);
    ASSERT_EQ(0, rc);

    /* Add all handles to handle set */
    memset(&_state->hevts_ref, 0, sizeof(_state->hevts_ref));
    _state->hevts_ref.handle = _state->hevts;
    _state->hevts_ref.emask = ~0U;
    _state->hevts_ref.cookie = NULL;
    rc = handle_set_attach(_state->hset, &_state->hevts_ref);
    ASSERT_EQ(0, rc);

    memset(&_state->hevt1_ref, 0, sizeof(_state->hevt1_ref));
    _state->hevt1_ref.handle = _state->hevt1;
    _state->hevt1_ref.emask = ~0U;
    _state->hevt1_ref.cookie = NULL;
    rc = handle_set_attach(_state->hset, &_state->hevt1_ref);
    ASSERT_EQ(0, rc);

    memset(&_state->hevt2_ref, 0, sizeof(_state->hevt2_ref));
    _state->hevt2_ref.handle = _state->hevt2;
    _state->hevt2_ref.emask = ~0U;
    _state->hevt2_ref.cookie = NULL;
    rc = handle_set_attach(_state->hset, &_state->hevt2_ref);
    ASSERT_EQ(0, rc);

    /* trigger and handle event MAX_EVT_CNT times */
    uint32_t evts_cnt = 0;
    uint32_t evt1_cnt = 0;
    uint32_t evt2_cnt = 0;

    rc = event_source_signal(_state->hevts);
    ASSERT_EQ(0, rc, "notify event signaled");
    evts_cnt++;
    for (;;) {
        rc = handle_set_wait(_state->hset, &ref,
                             NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
        ASSERT_EQ(0, rc, "wait any");

        if (ref.handle) {
            /* dec ref obtained by handle_set_wait */
            handle_decref(ref.handle);
        }

        if (ref.handle == _state->hevts) {
            if (evts_cnt == MAX_EVT_CNT)
                break;
            rc = event_source_signal(_state->hevts);
            ASSERT_EQ(0, rc, "notify event signaled");
            evts_cnt++;
        } else if (ref.handle == _state->hevt1) {
            evt1_cnt++;
            rc = event_client_notify_handled(_state->hevt1);
            ASSERT_EQ(0, rc, "notify event handled");
        } else if (ref.handle == _state->hevt2) {
            evt2_cnt++;
            rc = event_client_notify_handled(_state->hevt2);
            ASSERT_EQ(0, rc, "notify event handled");
        } else {
            break;
        }
    }
    EXPECT_EQ(MAX_EVT_CNT, evts_cnt, "evts count");
    EXPECT_EQ(MAX_EVT_CNT, evt1_cnt, "evt1 count");
    EXPECT_EQ(MAX_EVT_CNT, evt2_cnt, "evt2 count");

test_abort:;
}

TEST_F(uirq, event_client_open_signaled) {
    int rc;
    uint32_t uevt;
    struct uuid cid = zero_uuid;

    /* create named event source */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                             &_state->hevts);
    ASSERT_EQ(0, rc);

    rc = event_source_publish(_state->hevts);
    ASSERT_EQ(0, rc);

    /* open the named event */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt1);
    ASSERT_EQ(0, rc);

    /* signal event */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /* open named event again */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt2);
    ASSERT_EQ(0, rc);

    /* wait on client 1: should be signaled */
    rc = handle_wait(_state->hevt1, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client1");

    /* wait on client 2: should timeout as it should not be signaled */
    rc = handle_wait(_state->hevt2, &uevt, EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on source");

    /* wait on event source: should timeout: event is not handled */
    rc = handle_wait(_state->hevts, &uevt, EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on source");

    /* notify that event1 is handled: to put it in unsignaled state  */
    rc = event_client_notify_handled(_state->hevt1);
    EXPECT_EQ(0, rc, "notify event handled");

    /* wait on event source again: should return as event is handled */
    rc = handle_wait(_state->hevts, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on source");

    /* signal event again: now both events must ack */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /* wait on client 1: should be signaled */
    rc = handle_wait(_state->hevt1, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client1");

    /* wait on client 2: should be signaled */
    rc = handle_wait(_state->hevt2, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client2");

    /* notify that event1 is handled */
    rc = event_client_notify_handled(_state->hevt1);
    EXPECT_EQ(0, rc, "notify event handled");

    /* notify that event2 is handled */
    rc = event_client_notify_handled(_state->hevt2);
    EXPECT_EQ(0, rc, "notify event handled");

    /* wait on event source again: should be handled */
    rc = handle_wait(_state->hevts, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on source");

test_abort:;
}

TEST_F(uirq, event_source_resignal_signaled) {
    int rc;
    uint32_t uevt;
    struct uuid cid = zero_uuid;

    /* create named event source */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                             &_state->hevts);
    ASSERT_EQ(0, rc);

    rc = event_source_publish(_state->hevts);
    ASSERT_EQ(0, rc);

    /* open the named event */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt1);
    ASSERT_EQ(0, rc);

    /* open named event again */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt2);
    ASSERT_EQ(0, rc);

    /* signal event */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /* signal event again */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /* wait on client 1: should be signaled */
    rc = handle_wait(_state->hevt1, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client1");

    /* wait on client 2: should be signaled */
    rc = handle_wait(_state->hevt2, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client2");

    /* notify that event1 is handled */
    rc = event_client_notify_handled(_state->hevt1);
    EXPECT_EQ(0, rc, "notify event handled");

    /* notify that event2 is handled */
    rc = event_client_notify_handled(_state->hevt2);
    EXPECT_EQ(0, rc, "notify event handled");

    /* wait on event source: should be handled */
    rc = handle_wait(_state->hevts, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on source");

test_abort:;
}

TEST_F(uirq, event_source_resignal_notified) {
    int rc;
    uint32_t uevt;
    struct uuid cid = zero_uuid;

    /* create named event source */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                             &_state->hevts);
    ASSERT_EQ(0, rc);

    rc = event_source_publish(_state->hevts);
    ASSERT_EQ(0, rc);

    /* open the named event */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt1);
    ASSERT_EQ(0, rc);

    /* open named event again */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt2);
    ASSERT_EQ(0, rc);

    /* signal event */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /* wait on client 1: to put it into notifieed state */
    rc = handle_wait(_state->hevt1, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client1");

    /* signal event again */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /* notify that event1 is handled: it should enter signaled state */
    rc = event_client_notify_handled(_state->hevt1);
    EXPECT_EQ(0, rc, "notify event handled");

    /* wait on client 2: should be signaled */
    rc = handle_wait(_state->hevt2, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client2");

    /* notify that event2 is handled to put it into unsignaled state */
    rc = event_client_notify_handled(_state->hevt2);
    EXPECT_EQ(0, rc, "notify event handled");

    /* wait on event source: should timeout as event1 stil signaled */
    rc = handle_wait(_state->hevts, &uevt, EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on source");

    /* wait on client 1: should be signaled */
    rc = handle_wait(_state->hevt1, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client1");

    /* notify that event1 is handled to put it into unsignaled state */
    rc = event_client_notify_handled(_state->hevt1);
    EXPECT_EQ(0, rc, "notify event handled");

    /* wait on event source: should be handled */
    rc = handle_wait(_state->hevts, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on source");

test_abort:;
}

TEST_F(uirq, event_source_resignal_handled) {
    int rc;
    uint32_t uevt;
    struct uuid cid = zero_uuid;

    /* create named event source */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                             &_state->hevts);
    ASSERT_EQ(0, rc);

    rc = event_source_publish(_state->hevts);
    ASSERT_EQ(0, rc);

    /* open the named event */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt1);
    ASSERT_EQ(0, rc);

    /* open named event again */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt2);
    ASSERT_EQ(0, rc);

    /* signal event */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /* wait on client 1: to put it into notified state */
    rc = handle_wait(_state->hevt1, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client1");

    /* notify that event1 is handled to put it into unsignaled state  */
    rc = event_client_notify_handled(_state->hevt1);
    EXPECT_EQ(0, rc, "notify event handled");

    /* signal event again to put event 1 into signaled state */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /* wait on client 2: should be in signaled state */
    rc = handle_wait(_state->hevt2, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client2");

    /* notify that event2 is handled to put it into unsignaled state */
    rc = event_client_notify_handled(_state->hevt2);
    EXPECT_EQ(0, rc, "notify event handled");

    /* wait on event source: should timeout as event1 still signaled  */
    rc = handle_wait(_state->hevts, &uevt, EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on source");

    /* wait on client 1: to put it into notified state */
    rc = handle_wait(_state->hevt1, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client1");

    /* notify that event1 is handled to put it into unsignaled state */
    rc = event_client_notify_handled(_state->hevt1);
    EXPECT_EQ(0, rc, "notify event handled");

    /* wait on event source: should be in handled */
    rc = handle_wait(_state->hevts, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on source");

test_abort:;
}

TEST_F(uirq, event_source_close) {
    int rc;
    uint32_t uevt;
    struct uuid cid = zero_uuid;

    /* create named event source */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                             &_state->hevts);
    ASSERT_EQ(0, rc);

    rc = event_source_publish(_state->hevts);
    ASSERT_EQ(0, rc);

    /* open the named event */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt1);
    ASSERT_EQ(0, rc);

    /* close sevent source */
    handle_close(_state->hevts);
    _state->hevts = NULL;

    /* wait on closed client: should return HUP event  */
    rc = handle_wait(_state->hevt1, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on source");
    EXPECT_EQ(1, !!(uevt & IPC_HANDLE_POLL_HUP));

    /* Invoke notify on client with closed source */
    rc = event_client_notify_handled(_state->hevt1);
    EXPECT_EQ(ERR_CHANNEL_CLOSED, rc, "notify event handled");

test_abort:;
}

TEST_F(uirq, event_client_close_signaled) {
    int rc;
    uint32_t uevt;
    struct uuid cid = zero_uuid;

    /* create named event source */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                             &_state->hevts);
    ASSERT_EQ(0, rc);

    rc = event_source_publish(_state->hevts);
    ASSERT_EQ(0, rc);

    /* open the named event */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt1);
    ASSERT_EQ(0, rc);

    /* signal event */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /* close signaled event */
    handle_close(_state->hevt1);
    _state->hevt1 = NULL;

    /* wait on event source: should not block */
    rc = handle_wait(_state->hevts, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on source");

test_abort:;
}

TEST_F(uirq, event_client_close_notified) {
    int rc;
    uint32_t uevt;
    struct uuid cid = zero_uuid;

    /* create named event source */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                             &_state->hevts);
    ASSERT_EQ(0, rc);

    rc = event_source_publish(_state->hevts);
    ASSERT_EQ(0, rc);

    /* open the named event */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt1);
    ASSERT_EQ(0, rc);

    /* signal event */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /* wait on client 1: to put it into notified state */
    rc = handle_wait(_state->hevt1, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client1");

    /* close client  */
    handle_close(_state->hevt1);
    _state->hevt1 = NULL;

    /* wait on event source: should not block */
    rc = handle_wait(_state->hevts, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on source");

test_abort:;
}

TEST_F(uirq, event_client_close_notified_signaled) {
    int rc;
    uint32_t uevt;
    struct uuid cid = zero_uuid;

    /* create named event source */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                             &_state->hevts);
    ASSERT_EQ(0, rc);

    rc = event_source_publish(_state->hevts);
    ASSERT_EQ(0, rc);

    /* open the named event */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt1);
    ASSERT_EQ(0, rc);

    /* signal event */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /* wait on client 1: to put it into notified state */
    rc = handle_wait(_state->hevt1, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client1");

    /* signal event to put it intonotified signaled state */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /* close client */
    handle_close(_state->hevt1);
    _state->hevt1 = NULL;

    /* wait on event source: should not block */
    rc = handle_wait(_state->hevts, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on source");

test_abort:;
}

TEST_F(uirq, event_client_ack_unread) {
    int rc;
    uint32_t uevt;
    struct uuid cid = zero_uuid;

    /* create named event source */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                             &_state->hevts);
    ASSERT_EQ(0, rc);

    rc = event_source_publish(_state->hevts);
    ASSERT_EQ(0, rc);

    /* open the named event */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt1);
    ASSERT_EQ(0, rc);

    /* signal event */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /*
     * Notify that event1 is handled: should error because we need to wait on
     * event first
     */
    rc = event_client_notify_handled(_state->hevt1);
    EXPECT_EQ(ERR_BAD_STATE, rc, "notify event handled");

    /* wait on client 1: to put it into notifird state */
    rc = handle_wait(_state->hevt1, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client1");

    /* notify that event1 is handled: should be OK */
    rc = event_client_notify_handled(_state->hevt1);
    EXPECT_EQ(0, rc, "notify event handled");

test_abort:;
}

TEST_F(uirq, event_client_wait_on_notified_signaled) {
    int rc;
    uint32_t uevt;
    struct uuid cid = zero_uuid;

    /* create named event source */
    rc = event_source_create(TEST_EVS_NAME1, NULL, NULL, NULL, 0, 0,
                             &_state->hevts);
    ASSERT_EQ(0, rc);

    rc = event_source_publish(_state->hevts);
    ASSERT_EQ(0, rc);

    /* open the named event */
    rc = event_source_open(&cid, TEST_EVS_NAME1, strlen(TEST_EVS_NAME1) + 1, 0,
                           &_state->hevt1);
    ASSERT_EQ(0, rc);

    /* signal event */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /* wait on client 1: should be signaled: enters notified state */
    rc = handle_wait(_state->hevt1, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client1");

    /* signal event again to put is into notified signaled state */
    rc = event_source_signal(_state->hevts);
    EXPECT_EQ(0, rc, "notify event signaled");

    /* wait on client 1: should timeout as it is in notified signaled state */
    rc = handle_wait(_state->hevt1, &uevt, EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on client1");

    /*
     * notify that event1 is handled: shoudl be OK: puts event into signaled
     * state
     */
    rc = event_client_notify_handled(_state->hevt1);
    EXPECT_EQ(0, rc, "notify event handled");

    /* wait on client 1: should be signaled as it is in signaled state */
    rc = handle_wait(_state->hevt1, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on client1");

    /* notify that event1 is handled to put source into handled state  */
    rc = event_client_notify_handled(_state->hevt1);
    EXPECT_EQ(0, rc, "notify event handled");

    /* wait on event source: should be in handled state */
    rc = handle_wait(_state->hevts, &uevt, NOT_EXPECTED_TO_FAIL_TIMEOUT_MSEC);
    EXPECT_EQ(0, rc, "wait on source");

test_abort:;
}

PORT_TEST(uirq, "com.android.kernel.uirq-unittest");
