/*
 * Copyright (C) 2014-2015 The Android Open Source Project
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
#include <stdlib.h>
#include <string.h>
#include <trusty/time.h>
#include <trusty_ipc.h>
#include <uapi/err.h>

#define TLOG_LVL TLOG_LVL_INFO
#define TLOG_TAG "ipc-unittest-main"
#include <trusty_unittest.h>

#include <app/ipc_unittest/common.h>
#include <ipc_unittest_uuid_consts.h>
#include <lib/tipc/tipc.h>
#include <lib/unittest/unittest.h>

/* base of valid handle range */
static handle_t handle_base;

/* offset from handle_base of the first handle that is not used */
static unsigned int first_free_handle_index;

static const uuid_t srv_app_uuid = IPC_UNITTEST_SRV_APP_UUID;
static const uintptr_t COOKIE_BASE = 100;

#define ABORT_IF(_cond, lbl) \
    {                        \
        if (_cond) {         \
            goto lbl;        \
        }                    \
    }

#define ABORT_IF_NOT_OK(lbl) ABORT_IF((HasFailure()), lbl)

#define EXPECT_GT_ZERO(val, args...) EXPECT_GT(val, 0, ##args)
#define EXPECT_GE_ZERO(val, args...) EXPECT_GE(val, 0, ##args)

/*
 * TODO(b/294212953): Fix the inherent issue and enable tests
 * With source based code coverage for unit test enabled, the
 * coverage aggregator and potentially the coverage daemon
 * have extra handles that upset the calculation of the first
 * free handle, so disable these tests.
 */
#if UNITTEST_COVERAGE
#define DISABLED_WITH_COVERAGE(name) DISABLED_##name
#else
#define DISABLED_WITH_COVERAGE(name) name
#endif

/****************************************************************************/

/*
 *  Fill specified buffer with incremental pattern
 */
static void fill_test_buf(uint8_t* buf, size_t cnt, uint8_t seed) {
    if (!buf || !cnt)
        return;

    for (; cnt > 0; cnt--) {
        *buf++ = seed++;
    }
}

/*
 *  Local wrapper on top of async connect that provides
 *  synchronos connect with timeout.
 */
int sync_connect(const char* path, unsigned int timeout) {
    int rc;
    uevent_t evt;
    handle_t chan;

    rc = connect(path, IPC_CONNECT_ASYNC | IPC_CONNECT_WAIT_FOR_PORT);
    if (rc >= 0) {
        chan = (handle_t)rc;
        rc = wait(chan, &evt, timeout);
        if (rc == 0) {
            rc = ERR_BAD_STATE;
            if (evt.handle == chan) {
                if (evt.event & IPC_HANDLE_POLL_READY)
                    return chan;

                if (evt.event & IPC_HANDLE_POLL_HUP)
                    rc = ERR_CHANNEL_CLOSED;
            }
        }
        close(chan);
    }
    return rc;
}

/****************************************************************************/

/*
 *  wait on handle negative test
 */
TEST(ipc, DISABLED_WITH_COVERAGE(wait_negative)) {
    int rc;
    uevent_t event;
    uint32_t timeout = 1000;  // 1 sec

    /* waiting on invalid handle. */
    rc = wait(INVALID_IPC_HANDLE, &event, timeout);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "wait on invalid handle");

    /*
     *   call wait on an invalid (out of range) handle
     *
     *   check handling of the following cases:
     *     - handle is on the upper boundary of valid handle range
     *     - handle is above of the upper boundary of valid handle range
     *     - handle is below of valid handle range
     *
     *   in all cases, the expected result is ERR_BAD_HANDLE error.
     */
    rc = wait(handle_base + MAX_USER_HANDLES, &event, timeout);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "wait on invalid handle");

    rc = wait(handle_base + MAX_USER_HANDLES + 1, &event, timeout);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "wait on invalid handle");

    rc = wait(handle_base - 1, &event, timeout);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "wait on invalid handle");

    /* waiting on non-existing handle that is in valid range. */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        rc = wait(handle_base + i, &event, timeout);
        EXPECT_EQ(ERR_NOT_FOUND, rc, "wait on invalid handle");
    }
}

/*
 *  Close handle unittest
 */
TEST(ipc, DISABLED_WITH_COVERAGE(close_handle_negative)) {
    int rc;

    /* closing an invalid (negative value) handle. */
    rc = close(INVALID_IPC_HANDLE);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "closing invalid handle");

    /*
     *   call close on an invalid (out of range) handle
     *
     *   check handling of the following cases:
     *     - handle is on the upper boundary of valid handle range
     *     - handle is above of the upper boundary of valid handle range
     *     - handle is below of valid handle range
     *
     *   in all cases, the expected result is ERR_BAD_HANDLE error.
     */
    rc = close(handle_base + MAX_USER_HANDLES);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "closing invalid handle");

    rc = close(handle_base + MAX_USER_HANDLES + 1);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "closing invalid handle");

    rc = close(handle_base - 1);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "closing invalid handle");

    /* closing non-existing handle that is in valid range. */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        rc = close(handle_base + i);
        EXPECT_EQ(ERR_NOT_FOUND, rc, "closing invalid handle");
    }
}

/*
 *  Set cookie negative unittest
 */
TEST(ipc, DISABLED_WITH_COVERAGE(set_cookie_negative)) {
    int rc;

    /* set cookie for invalid (negative value) handle. */
    rc = set_cookie(INVALID_IPC_HANDLE, (void*)0x1BEEF);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "set cookie for invalid handle");

    /*
     *   calling set cookie for an invalid (out of range) handle
     *
     *   check handling of the following cases:
     *     - handle is on the upper boundary of valid handle range
     *     - handle is above of the upper boundary of valid handle range
     *     - handle is below of valid handle range
     *
     *   in all cases, the expected result is ERR_BAD_HANDLE error.
     */
    rc = set_cookie(handle_base + MAX_USER_HANDLES, (void*)0x2BEEF);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "set cookie for invalid handle");

    rc = set_cookie(handle_base + MAX_USER_HANDLES + 1, (void*)0x2BEEF);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "set cookie for invalid handle");

    rc = set_cookie(handle_base - 1, (void*)0x2BEEF);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "set cookie for invalid handle");

    /* set cookie for non-existing handle that is in valid range. */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        rc = set_cookie(handle_base + i, (void*)0x3BEEF);
        EXPECT_EQ(ERR_NOT_FOUND, rc, "set cookie for invalid handle");
    }
}

/*
 *  Duplicate handle unittest
 */
TEST(ipc, DISABLED_WITH_COVERAGE(dup_negative)) {
    int rc;

    /* duplicating an invalid (negative value) handle. */
    rc = dup(INVALID_IPC_HANDLE);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "duplicating invalid handle");

    /*
     *   call dup on an invalid (out of range) handle
     *
     *   check handling of the following cases:
     *     - handle is on the upper boundary of valid handle range
     *     - handle is above of the upper boundary of valid handle range
     *     - handle is below of valid handle range
     *
     *   in all cases, the expected result is ERR_BAD_HANDLE error.
     */
    rc = dup(handle_base + MAX_USER_HANDLES);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "duplicating invalid handle");

    rc = dup(handle_base + MAX_USER_HANDLES + 1);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "duplicating invalid handle");

    rc = dup(handle_base - 1);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "duplicating invalid handle");

    /* duplicating non-existing handle that is in valid range. */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        rc = dup(handle_base + i);
        EXPECT_EQ(ERR_NOT_FOUND, rc, "duplicating invalid handle");
    }
}

/****************************************************************************/

/*
 *  Port create unittest
 */
TEST(ipc, port_create_negative) {
    int rc;
    char path[MAX_PORT_PATH_LEN + 16];

    /* create port with empty path */
    path[0] = '\0';
    rc = port_create(path, 2, 64, 0);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "empty path srv");

    /* create port with zero buffers */
    sprintf(path, "%s.port", SRV_PATH_BASE);
    rc = port_create(path, 0, 64, 0);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "no buffers");

    /* create port with zero buffer size */
    sprintf(path, "%s.port", SRV_PATH_BASE);
    rc = port_create(path, 2, 0, 0);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "zero buf size");

    /* create port with large number of buffers */
    sprintf(path, "%s.port", SRV_PATH_BASE);
    rc = port_create(path, MAX_PORT_BUF_NUM * 100, 64, 0);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "large buf num");

    /* create port with large buffer size */
    sprintf(path, "%s.port", SRV_PATH_BASE);
    rc = port_create(path, 2, MAX_PORT_BUF_SIZE * 100, 0);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "large buf size");

    /* create port with path oversized name */
    int len = sprintf(path, "%s.port", SRV_PATH_BASE);
    for (size_t i = len; i < sizeof(path); i++)
        path[i] = 'a';
    path[sizeof(path) - 1] = '\0';
    rc = port_create(path, 2, MAX_PORT_BUF_SIZE, 0);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "path is too long");
    rc = close((handle_t)rc);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "close port");
}

TEST(ipc, DISABLED_WITH_COVERAGE(port_create)) {
    int rc;
    unsigned int i;
    char path[MAX_PORT_PATH_LEN];
    handle_t ports[MAX_USER_HANDLES];

    /* create maximum number of ports */
    for (i = first_free_handle_index; i < MAX_USER_HANDLES - 1; i++) {
        sprintf(path, "%s.port.%s%d", SRV_PATH_BASE, "test", i);
        rc = port_create(path, 2, MAX_PORT_BUF_SIZE, 0);
        EXPECT_GT_ZERO(rc, "create ports");
        ports[i] = (handle_t)rc;

        /* create a new port that collide with an existing port */
        rc = port_create(path, 2, MAX_PORT_BUF_SIZE, 0);
        EXPECT_EQ(ERR_ALREADY_EXISTS, rc, "create existing port");
    }

    /* create one more that should succeed */
    sprintf(path, "%s.port.%s%d", SRV_PATH_BASE, "test", i);
    rc = port_create(path, 2, MAX_PORT_BUF_SIZE, 0);
    EXPECT_GT_ZERO(rc, "create ports");
    ports[i] = (handle_t)rc;

    /* but creating colliding port should fail with different
       error code because we actually exceeded max number of
       handles instead of colliding with an existing path */
    rc = port_create(path, 2, MAX_PORT_BUF_SIZE, 0);
    EXPECT_EQ(ERR_NO_RESOURCES, rc, "create existing port");

    sprintf(path, "%s.port.%s%d", SRV_PATH_BASE, "test", MAX_USER_HANDLES);
    rc = port_create(path, 2, MAX_PORT_BUF_SIZE, 0);
    EXPECT_EQ(ERR_NO_RESOURCES, rc, "max ports");

    /* close them all  */
    for (i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        /* close a valid port  */
        rc = close(ports[i]);
        EXPECT_EQ(NO_ERROR, rc, "closing port");

        /* close previously closed port. It should fail! */
        rc = close(ports[i]);
        EXPECT_EQ(ERR_NOT_FOUND, rc, "closing closed port");

        ports[i] = INVALID_IPC_HANDLE;
    }
}

/*
 *
 */
TEST(ipc, DISABLED_WITH_COVERAGE(wait_on_port)) {
    int rc;
    uevent_t event;
    char path[MAX_PORT_PATH_LEN];
    handle_t ports[MAX_USER_HANDLES];

    /* create maximum number of ports */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        sprintf(path, "%s.port.%s%d", SRV_PATH_BASE, "test", i);
        rc = port_create(path, 2, MAX_PORT_BUF_SIZE, 0);
        EXPECT_GT_ZERO(rc, "max ports");
        ports[i] = (handle_t)rc;

        rc = set_cookie(ports[i], (void*)(COOKIE_BASE + i));
        EXPECT_EQ(NO_ERROR, rc, "set cookie on port");
    }

    /* wait on each individual port */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        /* wait with zero timeout */
        rc = wait(ports[i], &event, 0);
        EXPECT_EQ(ERR_TIMED_OUT, rc, "zero timeout");

        /* wait with non-zero timeout */
        rc = wait(ports[i], &event, 100);
        EXPECT_EQ(ERR_TIMED_OUT, rc, "non-zero timeout");
    }

    /* wait on all ports with zero timeout */
    rc = wait_any(&event, 0);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "zero timeout");

    /* wait on all ports with non-zero timeout*/
    rc = wait_any(&event, 100);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "non-zero timeout");

    /* close them all */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        /* close a valid port  */
        rc = close(ports[i]);
        EXPECT_EQ(NO_ERROR, rc, "closing closed port");
        ports[i] = INVALID_IPC_HANDLE;
    }
}

/****************************************************************************/

/*
 *  Connect unittests
 */
TEST(ipc, connect_negative) {
    int rc;
    char path[MAX_PORT_PATH_LEN + 16] = "";

    /* try to connect to port with an empty name */
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "empty path");

    /* try to connect to non-existing port  */
    sprintf(path, "%s.conn.%s", SRV_PATH_BASE, "blah-blah");
    rc = connect(path, 0);
    EXPECT_EQ(ERR_NOT_FOUND, rc, "non-existing path");

    /* try to connect to non-existing port  */
    sprintf(path, "%s.conn.%s", SRV_PATH_BASE, "blah-blah");
    rc = connect(path, IPC_CONNECT_ASYNC);
    EXPECT_EQ(ERR_NOT_FOUND, rc, "non-existing path");

    /* try to connect to port with very long name */
    int len = sprintf(path, "%s.conn.", SRV_PATH_BASE);
    for (size_t i = len; i < sizeof(path); i++)
        path[i] = 'a';
    path[sizeof(path) - 1] = '\0';
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "long path");

    rc = close((handle_t)rc);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "close channel");
}

TEST(ipc, DISABLED_WITH_COVERAGE(connect_close)) {
    int rc;
    char path[MAX_PORT_PATH_LEN];
    handle_t chans[16];

    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "datasink");

    for (unsigned int j = first_free_handle_index; j < MAX_USER_HANDLES; j++) {
        /* do several iterations to make sure we are not
           not loosing handles */
        for (unsigned int i = 0; i < countof(chans); i++) {
            rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
            EXPECT_GT_ZERO(rc, "connect/close");
            chans[i] = (handle_t)rc;
        }

        for (unsigned int i = 0; i < countof(chans); i++) {
            rc = close(chans[i]);
            EXPECT_EQ(NO_ERROR, rc, "connect/close");
        }
    }
}

static void run_connect_close_by_peer_test(const char* test) {
    int rc;
    char path[MAX_PORT_PATH_LEN];
    uevent_t event;
    handle_t chans[16];
    unsigned int chan_cnt = 0;

    /*
     * open up to 16 connection to specified test port which would
     * close them all in a different way:
     */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, test);
    for (unsigned int i = 0; i < countof(chans); i++) {
        rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);

        /*
         * depending on task scheduling connect might return real
         * handle that will be closed later or it might return
         * ERR_CHANNEL_CLOSED error if the channel has already been
         * closed. Both cases are correct and must be handled.
         */
        if (rc >= 0) {
            /* got real handle */
            chans[i] = (handle_t)rc;

            /* attach cookie for returned channel */
            rc = set_cookie((handle_t)rc, (void*)(COOKIE_BASE + i));
            EXPECT_EQ(NO_ERROR, rc, "%s", test);

            chan_cnt++;
        } else {
            /* could be already closed channel */
            EXPECT_EQ(ERR_CHANNEL_CLOSED, rc, "%s", test);
        }

        /* check if any channels are closed */
        while ((rc = wait_any(&event, 0)) == NO_ERROR) {
            EXPECT_EQ(IPC_HANDLE_POLL_HUP, event.event, "%s", test);
            uintptr_t idx = (uintptr_t)event.cookie - COOKIE_BASE;
            EXPECT_EQ(chans[idx], event.handle, "%s", test);
            EXPECT_GT(countof(chans), idx, "%s", test);
            if (idx < countof(chans)) {
                rc = close(chans[idx]);
                EXPECT_EQ(NO_ERROR, rc, "%s", test);
                chans[idx] = INVALID_IPC_HANDLE;
            }
            chan_cnt--;
        }
    }

    /* wait until all channels are closed */
    while (chan_cnt) {
        rc = wait_any(&event, 10000);
        EXPECT_EQ(NO_ERROR, rc, "%s", test);
        EXPECT_EQ(IPC_HANDLE_POLL_HUP, event.event, "%s", test);

        uintptr_t idx = (uintptr_t)event.cookie - COOKIE_BASE;
        EXPECT_GT(countof(chans), idx, "%s", test);
        EXPECT_EQ(chans[idx], event.handle, "%s", test);
        if (idx < countof(chans)) {
            rc = close(chans[idx]);
            EXPECT_EQ(NO_ERROR, rc, "%s", test);
            chans[idx] = INVALID_IPC_HANDLE;
        }
        chan_cnt--;
    }

    EXPECT_EQ(0, chan_cnt, "%s", test);
}

TEST(ipc, connect_close_by_peer_1) {
    run_connect_close_by_peer_test("closer1");
}

TEST(ipc, connect_close_by_peer_2) {
    run_connect_close_by_peer_test("closer2");
}

TEST(ipc, connect_close_by_peer_3) {
    run_connect_close_by_peer_test("closer3");
}

TEST(ipc, async_connect) {
    int rc;
    handle_t chan;
    uevent_t event;
    uuid_t peer_uuid = UUID_INITIAL_VALUE(peer_uuid);
    char path[MAX_PORT_PATH_LEN];

    sprintf(path, "%s.main.%s", SRV_PATH_BASE, "async");

    /* connect to non existing port synchronously without wait_for_port */
    rc = connect(path, 0);
    EXPECT_EQ(ERR_NOT_FOUND, rc, "async");
    rc = close((handle_t)rc);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "async");

    /* connect to non existing port asynchronously without wait_for_port */
    rc = connect(path, IPC_CONNECT_ASYNC);
    EXPECT_EQ(ERR_NOT_FOUND, rc, "async");
    rc = close((handle_t)rc);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "async");

    /* connect to non existing port asynchronously with wait_for_port */
    rc = connect(path, IPC_CONNECT_ASYNC | IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "async");
    if (rc >= 0) {
        chan = (handle_t)rc;

        /* wait on channel */
        rc = wait(chan, &event, 1000);
        EXPECT_EQ(ERR_TIMED_OUT, rc, "async");

        /* and close it */
        rc = close(chan);
        EXPECT_EQ(NO_ERROR, rc, "async");
    }

    /* connect to non-existing port asynchronously with wait_for_port */
    rc = connect(path, IPC_CONNECT_ASYNC | IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "async");
    chan = (handle_t)rc;

    if (rc >= 0) {
        handle_t port;
        uint32_t exp_event;

        /* wait on channel for connect */
        rc = wait(chan, &event, 100);
        EXPECT_EQ(ERR_TIMED_OUT, rc, "async");

        /* now create port */
        rc = port_create(path, 1, 64, IPC_PORT_ALLOW_TA_CONNECT);
        EXPECT_GT_ZERO(rc, "async");
        if (rc >= 0) {
            port = (handle_t)rc;

            /* and wait for incomming connections */
            exp_event = IPC_HANDLE_POLL_READY;
            rc = wait(port, &event, 1000);
            EXPECT_EQ(NO_ERROR, rc, "async");
            EXPECT_EQ(exp_event, event.event, "async");

            if (rc == NO_ERROR) {
                handle_t srv_chan;

                /* got one, accept it */
                rc = accept(port, &peer_uuid);
                EXPECT_GT_ZERO(rc, "async");
                srv_chan = (handle_t)rc;

                /* and close it */
                close(srv_chan);

                /* now wait on original chan:
                 * there should be READY and HUP events
                 */
                exp_event = IPC_HANDLE_POLL_READY | IPC_HANDLE_POLL_HUP;
                rc = wait(chan, &event, 1000);
                EXPECT_EQ(NO_ERROR, rc, "async");
                EXPECT_EQ(exp_event, event.event, "async");
            }
            close(port);
        }
        close(chan);
    }
}

TEST(ipc, connect_selfie) {
    int rc;
    uuid_t peer_uuid = UUID_INITIAL_VALUE(peer_uuid);
    uuid_t zero_uuid = UUID_INITIAL_VALUE(zero_uuid);
    char path[MAX_PORT_PATH_LEN];
    uint32_t connect_timeout = 1000;  // 1 sec

    /* Try to connect to port that we register ourself.
       It is not very useful scenario, just to make sure that
       nothing bad is happening */
    sprintf(path, "%s.main.%s", SRV_PATH_BASE, "selfie");
    rc = port_create(path, 2, MAX_PORT_BUF_SIZE, IPC_PORT_ALLOW_TA_CONNECT);
    EXPECT_GT_ZERO(rc, "selfie");

    if (rc >= 0) {
        handle_t test_port = rc;

        /* Since we are single threaded we will always timeout
         * at wait_any for synchronous connect as can't accept.
         */

        /* with non-zero timeout  */
        rc = sync_connect(path, connect_timeout);
        EXPECT_EQ(ERR_TIMED_OUT, rc, "selfie sync");

        /* with zero timeout */
        rc = sync_connect(path, 0);
        EXPECT_EQ(ERR_TIMED_OUT, rc, "selfie sync");

        /* since we did not call wait on port yet we have
         * 2 connection requests pending (attached to port)
         * teared down by peer (us), an now removed from the
         * port - there should be no events waiting.
         */
        uevent_t event;
        EXPECT_EQ(ERR_TIMED_OUT, wait_any(&event, 0));

        /* retry using a couple of closed async connections */
        rc = connect(path, IPC_CONNECT_ASYNC);
        EXPECT_GT(rc, 0, "selfie async");

        rc = close(rc);
        EXPECT_EQ(0, rc, "selfie async");

        rc = connect(path, IPC_CONNECT_ASYNC);
        EXPECT_GT(rc, 0, "selfie async");

        rc = close(rc);
        EXPECT_EQ(0, rc, "selfie async");

        EXPECT_EQ(ERR_TIMED_OUT, wait_any(&event, 0));

        /* retry using accept and sync connection */
        rc = sync_connect(path, connect_timeout);
        EXPECT_EQ(ERR_TIMED_OUT, rc, "selfie async");

        rc = sync_connect(path, 0);
        EXPECT_EQ(ERR_TIMED_OUT, rc, "selfie async");

        /* try accepting a pending connection */
        rc = accept(test_port, &peer_uuid);
        EXPECT_EQ(ERR_NO_MSG, rc, "accept");

        rc = memcmp(&peer_uuid, &zero_uuid, sizeof(zero_uuid));
        EXPECT_EQ(0, rc, "accept");

        /* retry with a pending then closed async connection */
        rc = connect(path, IPC_CONNECT_ASYNC);
        EXPECT_GT(rc, 0, "selfie async");

        rc = close(rc);
        EXPECT_EQ(0, rc, "selfie async");

        /* try accepting a pending connection, that was already closed */
        rc = accept(test_port, &peer_uuid);
        EXPECT_EQ(ERR_NO_MSG, rc, "accept async");

        /* retry with a pending async connection */
        rc = connect(path, IPC_CONNECT_ASYNC);
        EXPECT_GT(rc, 0, "selfie async");

        handle_t client_port = rc;

        unsigned int exp_event = IPC_HANDLE_POLL_READY;

        int rc = wait_any(&event, INFINITE_TIME);
        EXPECT_EQ(NO_ERROR, rc, "wait on port");
        EXPECT_EQ(test_port, event.handle, "wait on port");
        EXPECT_EQ(exp_event, event.event, "wait on port");

        if (rc == NO_ERROR && (event.event & IPC_HANDLE_POLL_READY)) {
            /* close the connection */
            rc = close(client_port);
            EXPECT_EQ(0, rc, "selfie async");

            /* we had a pending connection, but it is already closed */
            rc = accept(test_port, &peer_uuid);
            EXPECT_EQ(ERR_NO_MSG, rc, "accept");

            rc = memcmp(&peer_uuid, &zero_uuid, sizeof(zero_uuid));
            EXPECT_EQ(0, rc, "accept")
        }

        /* add couple connections back and destroy them along with port */
        rc = sync_connect(path, 0);
        EXPECT_EQ(ERR_TIMED_OUT, rc, "selfie");

        rc = sync_connect(path, 0);
        EXPECT_EQ(ERR_TIMED_OUT, rc, "selfie");

        /* close selfie port  */
        rc = close(test_port);
        EXPECT_EQ(NO_ERROR, rc, "close selfie");
    }
}

TEST(ipc, connect_access) {
    int rc;
    char path[MAX_PORT_PATH_LEN];

    /* open connection to NS only accessible service */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "ns_only");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);

    /* It is expected to fail */
    EXPECT_EQ(ERR_ACCESS_DENIED, rc, "connect to ns_only");

    if (rc >= 0)
        close((handle_t)rc);

    /* open connection to TA only accessible service */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "ta_only");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);

    /* it is expected to succeed */
    EXPECT_GT_ZERO(rc, "connect to ta_only");

    if (rc >= 0)
        close((handle_t)rc);
}

/****************************************************************************/

/*
 *  Accept negative test
 */
TEST(ipc, DISABLED_WITH_COVERAGE(accept_negative)) {
    int rc, rc1;
    char path[MAX_PORT_PATH_LEN];
    handle_t chan;
    uuid_t peer_uuid = UUID_INITIAL_VALUE(peer_uuid);
    uuid_t zero_uuid = UUID_INITIAL_VALUE(zero_uuid);

    /* accept on invalid (negative value) handle */
    rc = accept(INVALID_IPC_HANDLE, &peer_uuid);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "accept on invalid handle");

    rc1 = memcmp(&peer_uuid, &zero_uuid, sizeof(zero_uuid));
    EXPECT_EQ(0, rc1, "accept")

    /*
     *   calling accept on an invalid (out of range) handle
     *
     *   check handling of the following cases:
     *     - handle is on the upper boundary of valid handle range
     *     - handle is above of the upper boundary of valid handle range
     *     - handle is below of valid handle range
     *
     *   in all cases, the expected result is ERR_BAD_HANDLE error.
     */
    rc = accept(handle_base + MAX_USER_HANDLES, &peer_uuid);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "accept on invalid handle");

    rc = accept(handle_base + MAX_USER_HANDLES + 1, &peer_uuid);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "accept on invalid handle");

    rc = accept(handle_base - 1, &peer_uuid);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "accept on invalid handle");

    rc1 = memcmp(&peer_uuid, &zero_uuid, sizeof(zero_uuid));
    EXPECT_EQ(0, rc1, "accept")

    /* accept on non-existing handle that is in valid range */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        rc = accept(handle_base + i, &peer_uuid);
        EXPECT_EQ(ERR_NOT_FOUND, rc, "accept on invalid handle");

        rc1 = memcmp(&peer_uuid, &zero_uuid, sizeof(zero_uuid));
        EXPECT_EQ(0, rc1, "accept")
    }

    /* connect to datasink service */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "datasink");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to datasink");
    chan = (handle_t)rc;

    /* call accept on channel handle which is an invalid operation */
    rc = accept(chan, &peer_uuid);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "accept on channel");

    rc1 = memcmp(&peer_uuid, &zero_uuid, sizeof(zero_uuid));
    EXPECT_EQ(0, rc1, "accept")

    rc = close(chan);
    EXPECT_EQ(NO_ERROR, rc, "close channnel")
}

/*
 * Disabled per b/140836874 - believed to be a race in the test code, not
 * in IPC.
 */
TEST(ipc, DISABLED_accept) {
    int rc, rc1;
    uevent_t event;
    char path[MAX_PORT_PATH_LEN];
    handle_t ports[MAX_USER_HANDLES];
    uuid_t peer_uuid = UUID_INITIAL_VALUE(peer_uuid);
    uuid_t zero_uuid = UUID_INITIAL_VALUE(zero_uuid);

    /* create maximum number of ports */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        sprintf(path, "%s.port.accept%d", SRV_PATH_BASE, i);
        rc = port_create(path, 2, MAX_PORT_BUF_SIZE, IPC_PORT_ALLOW_TA_CONNECT);
        EXPECT_GT_ZERO(rc, "max ports");
        ports[i] = (handle_t)rc;

        rc = set_cookie(ports[i], (void*)(COOKIE_BASE + ports[i]));
        EXPECT_EQ(NO_ERROR, rc, "set cookie on port");
    }

    /* poke connect service to initiate connections to us */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "connect");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    if (rc >= 0)
        close((handle_t)rc);

    /* handle incoming connections */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        rc = wait_any(&event, 1000);
        EXPECT_EQ(NO_ERROR, rc, "accept test");
        EXPECT_EQ(IPC_HANDLE_POLL_READY, event.event, "accept test");

        /* check port cookie */
        void* exp_cookie = (void*)(COOKIE_BASE + event.handle);
        EXPECT_EQ(exp_cookie, event.cookie, "accept test");

        /* accept connection - should fail because we do not
           have any room for handles */
        rc = accept(event.handle, &peer_uuid);
        EXPECT_EQ(ERR_NO_RESOURCES, rc, "accept test");

        /* check peer uuid */
        rc1 = memcmp(&peer_uuid, &zero_uuid, sizeof(zero_uuid));
        EXPECT_EQ(0, rc1, "accept test")
    }

    /* free 1 handle  so we have room and repeat test */
    rc = close(ports[first_free_handle_index]);
    EXPECT_EQ(NO_ERROR, 0, "close accept test");
    ports[2] = INVALID_IPC_HANDLE;

    /* poke connect service to initiate connections to us */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "connect");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    if (rc >= 0)
        close((handle_t)rc);

    /* handle incoming connections */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES - 1;
         i++) {
        rc = wait_any(&event, 3000);
        EXPECT_EQ(NO_ERROR, rc, "accept test");
        EXPECT_EQ(IPC_HANDLE_POLL_READY, event.event, "accept test");

        /* check port cookie */
        void* exp_cookie = (void*)(COOKIE_BASE + event.handle);
        EXPECT_EQ(exp_cookie, event.cookie, "accept test");

        rc = accept(event.handle, &peer_uuid);
        EXPECT_EQ(handle_base + first_free_handle_index, rc, "accept test");

        /* check peer uuid */
        rc1 = memcmp(&peer_uuid, &srv_app_uuid, sizeof(srv_app_uuid));
        EXPECT_EQ(0, rc1, "accept test")

        rc = close((handle_t)rc);
        EXPECT_EQ(NO_ERROR, rc, "accept test");
    }

    /* close them all */
    for (unsigned int i = first_free_handle_index + 1; i < MAX_USER_HANDLES;
         i++) {
        /* close a valid port  */
        rc = close(ports[i]);
        EXPECT_EQ(NO_ERROR, rc, "close port");
        ports[i] = INVALID_IPC_HANDLE;
    }
}

/****************************************************************************/

TEST(ipc, DISABLED_WITH_COVERAGE(get_msg_negative)) {
    int rc;
    ipc_msg_info_t inf;
    handle_t port;
    handle_t chan;
    char path[MAX_PORT_PATH_LEN];

    /* get_msg on invalid (negative value) handle. */
    rc = get_msg(INVALID_IPC_HANDLE, &inf);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "get_msg on invalid handle");

    /*
     *   calling get_msg on an invalid (out of range) handle
     *
     *   check handling of the following cases:
     *     - handle is on the upper boundary of valid handle range
     *     - handle is above of the upper boundary of valid handle range
     *     - handle is below of valid handle range
     *
     *   in all cases, the expected result is ERR_BAD_HANDLE error.
     */
    rc = get_msg(handle_base + MAX_USER_HANDLES, &inf);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "get_msg on invalid handle");

    rc = get_msg(handle_base + MAX_USER_HANDLES + 1, &inf);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "get_msg on invalid handle");

    rc = get_msg(handle_base - 1, &inf);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "get_msg on invalid handle");

    /* get_msg on non-existing handle that is in valid range. */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        rc = get_msg(handle_base + i, &inf);
        EXPECT_EQ(ERR_NOT_FOUND, rc, "get_msg on invalid handle");
    }

    /* calling get_msg on port handle should fail
       because get_msg is only applicable to channels */
    sprintf(path, "%s.main.%s", SRV_PATH_BASE, "datasink");
    rc = port_create(path, 2, MAX_PORT_BUF_SIZE, IPC_PORT_ALLOW_TA_CONNECT);
    EXPECT_GT_ZERO(rc, "create datasink port");
    port = (handle_t)rc;

    rc = get_msg(port, &inf);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "get_msg on port");
    close(port);

    /* call get_msg on channel that do not have any pending messages */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "datasink");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to datasink");
    chan = (handle_t)rc;

    rc = get_msg(chan, &inf);
    EXPECT_EQ(ERR_NO_MSG, rc, "get_msg on empty channel");

    rc = close(chan);
    EXPECT_EQ(NO_ERROR, rc, "close channnel");
}

TEST(ipc, DISABLED_WITH_COVERAGE(put_msg_negative)) {
    int rc;
    handle_t port;
    handle_t chan;
    char path[MAX_PORT_PATH_LEN];

    /* put_msg on invalid (negative value) handle */
    rc = put_msg(INVALID_IPC_HANDLE, 0);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "put_msg on invalid handle");

    /*
     *   calling put_msg on an invalid (out of range) handle
     *
     *   check handling of the following cases:
     *     - handle is on the upper boundary of valid handle range
     *     - handle is above of the upper boundary of valid handle range
     *     - handle is below of valid handle range
     *
     *   in all cases, the expected result is ERR_BAD_HANDLE error.
     */
    rc = put_msg(handle_base + MAX_USER_HANDLES, 0);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "put_msg on invalid handle");

    rc = put_msg(handle_base + MAX_USER_HANDLES + 1, 0);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "put_msg on invalid handle");

    rc = put_msg(handle_base - 1, 0);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "put_msg on invalid handle");

    /* put_msg on non-existing handle that is in valid range */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        rc = put_msg(handle_base + i, 0);
        EXPECT_EQ(ERR_NOT_FOUND, rc, "put_msg on invalid handle");
    }

    /* calling put_msg on port handle should fail
       because put_msg is only applicable to channels */
    sprintf(path, "%s.main.%s", SRV_PATH_BASE, "datasink");
    rc = port_create(path, 2, MAX_PORT_BUF_SIZE, IPC_PORT_ALLOW_TA_CONNECT);
    EXPECT_GT_ZERO(rc, "create datasink port");
    port = (handle_t)rc;

    rc = put_msg(port, 0);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "put_msg on port");
    rc = close(port);
    EXPECT_EQ(NO_ERROR, rc, "close port");

    /* call put_msg on channel that do not have any pending messages */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "datasink");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to datasink");
    chan = (handle_t)rc;

    rc = put_msg(chan, 0);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "put_msg on empty channel");
    rc = close(chan);
    EXPECT_EQ(NO_ERROR, rc, "close channel");
}

/*
 *  Send 10000 messages to datasink service
 */
TEST(ipc, send_msg) {
    int rc;
    handle_t chan;
    char path[MAX_PORT_PATH_LEN];
    uint8_t buf0[64];
    uint8_t buf1[64];
    struct iovec iov[2];
    ipc_msg_t msg;

    /* prepare test buffer */
    fill_test_buf(buf0, sizeof(buf0), 0x55);
    fill_test_buf(buf1, sizeof(buf1), 0x44);

    iov[0].iov_base = buf0;
    iov[0].iov_len = sizeof(buf0);
    iov[1].iov_base = buf1;
    iov[1].iov_len = sizeof(buf1);
    msg.num_handles = 0;
    msg.handles = NULL;
    msg.num_iov = 2;
    msg.iov = iov;

    /* open connection to datasink service */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "datasink");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to datasink");

    if (rc >= 0) {
        chan = (handle_t)rc;
        for (unsigned int i = 0; i < 10000; i++) {
            rc = send_msg(chan, &msg);
            if (rc == ERR_NOT_ENOUGH_BUFFER) { /* wait for room */
                uevent_t uevt;
                unsigned int exp_event = IPC_HANDLE_POLL_SEND_UNBLOCKED;
                rc = wait(chan, &uevt, 1000);
                EXPECT_EQ(NO_ERROR, rc, "waiting for space");
                EXPECT_EQ(chan, uevt.handle, "waiting for space");
                EXPECT_EQ(exp_event, uevt.event, "waiting for space");
            } else {
                EXPECT_EQ(64, rc, "send_msg bulk")
            }
            if (HasFailure()) {
                TLOGI("%s: abort (rc = %d) test\n", __func__, rc);
                break;
            }
        }
        rc = close(chan);
        EXPECT_EQ(NO_ERROR, rc, "close channel");
    }
}

TEST(ipc, DISABLED_WITH_COVERAGE(send_msg_negative)) {
    int rc;
    handle_t port;
    handle_t chan;
    char path[MAX_PORT_PATH_LEN];
    uint8_t buf[64];
    struct iovec iov[2];
    ipc_msg_t msg;

    /* init msg to empty message */
    memset(&msg, 0, sizeof(msg));

    /* send_msg on invalid (negative value) handle */
    rc = send_msg(INVALID_IPC_HANDLE, &msg);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "send_msg on invalid handle");

    /* calling send_msg with NULL msg should fail for any handle */
    rc = send_msg(INVALID_IPC_HANDLE, NULL);
    EXPECT_EQ(ERR_FAULT, rc, "send_msg on NULL msg");

    /*
     *   calling send_msg on an invalid (out of range) handle
     *
     *   check handling of the following cases:
     *     - handle is on the upper boundary of valid handle range
     *     - handle is above of the upper boundary of valid handle range
     *     - handle is below of valid handle range
     *
     *   in all cases, the expected result is ERR_BAD_HANDLE error.
     */
    rc = send_msg(handle_base + MAX_USER_HANDLES, &msg);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "send_msg on invalid handle");

    rc = send_msg(handle_base + MAX_USER_HANDLES + 1, &msg);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "send_msg on invalid handle");

    rc = send_msg(handle_base - 1, &msg);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "send_msg on invalid handle");

    /* calling send_msg with NULL msg should fail for any handle */
    rc = send_msg(MAX_USER_HANDLES, NULL);
    EXPECT_EQ(ERR_FAULT, rc, "send_msg on NULL msg");

    /* send_msg on non-existing handle that is in valid range */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        rc = send_msg(handle_base + i, &msg);
        EXPECT_EQ(ERR_NOT_FOUND, rc, "send on invalid handle");

        /* calling send_msg with NULL msg should fail for any handle */
        rc = send_msg(handle_base + i, NULL);
        EXPECT_EQ(ERR_FAULT, rc, "send_msg on NULL msg");
    }

    /* calling send_msg on port handle should fail
       because send_msg is only applicable to channels */
    sprintf(path, "%s.main.%s", SRV_PATH_BASE, "datasink");
    rc = port_create(path, 2, MAX_PORT_BUF_SIZE, IPC_PORT_ALLOW_TA_CONNECT);
    EXPECT_GT_ZERO(rc, "create datasink port");
    port = (handle_t)rc;

    rc = send_msg(port, &msg);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "send_msg on port");
    close(port);

    /* open connection to datasink service */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "datasink");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to datasink");
    chan = (handle_t)rc;

    /* send message with handles pointing to NULL */
    msg.num_handles = 1;
    msg.handles = NULL;
    rc = send_msg(chan, &msg);
    EXPECT_EQ(ERR_FAULT, rc, "sending handles");

    /* reset handles */
    msg.num_handles = 0;
    msg.handles = NULL;

    /* set num_iov to non zero but keep iov ptr NULL */
    msg.num_iov = 1;
    msg.iov = NULL;
    rc = send_msg(chan, &msg);
    EXPECT_EQ(ERR_FAULT, rc, "sending bad iovec array");

    /*  send msg with iovec with bad base ptr */
    iov[0].iov_len = sizeof(buf) / 2;
    iov[0].iov_base = NULL;
    iov[1].iov_len = sizeof(buf) / 2;
    iov[1].iov_base = NULL;
    msg.num_iov = 2;
    msg.iov = iov;
    rc = send_msg(chan, &msg);
    EXPECT_EQ(ERR_FAULT, rc, "sending bad iovec");

    /*  send msg with iovec with bad base ptr */
    iov[0].iov_len = sizeof(buf) / 2;
    iov[0].iov_base = buf;
    iov[1].iov_len = sizeof(buf) / 2;
    iov[1].iov_base = NULL;
    msg.num_iov = 2;
    msg.iov = iov;
    rc = send_msg(chan, &msg);
    EXPECT_EQ(ERR_FAULT, rc, "sending bad iovec");

    rc = close(chan);
    EXPECT_EQ(NO_ERROR, rc, "close channel");
}

TEST(ipc, DISABLED_WITH_COVERAGE(read_msg_negative)) {
    int rc;
    handle_t port;
    handle_t chan;
    uevent_t uevt;
    char path[MAX_PORT_PATH_LEN];
    uint8_t tx_buf[64];
    uint8_t rx_buf[64];
    ipc_msg_info_t inf;
    ipc_msg_t tx_msg;
    struct iovec tx_iov;
    ipc_msg_t rx_msg;
    struct iovec rx_iov[2];

    /* init msg to empty message */
    memset(&rx_msg, 0, sizeof(rx_msg));
    memset(&tx_msg, 0, sizeof(tx_msg));

    /* read_msg on invalid (negative value) handle */
    rc = read_msg(INVALID_IPC_HANDLE, 0, 0, &rx_msg);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "read_msg on invalid handle");

    rc = read_msg(INVALID_IPC_HANDLE, 0, 0, NULL);
    EXPECT_EQ(ERR_FAULT, rc, "read_msg on invalid handle");

    /*
     *   calling read_msg on an invalid (out of range) handle
     *
     *   check handling of the following cases:
     *     - handle is on the upper boundary of valid handle range
     *     - handle is above of the upper boundary of valid handle range
     *     - handle is below of valid handle range
     *
     *   in all cases, the expected result is ERR_BAD_HANDLE error.
     */
    rc = read_msg(handle_base + MAX_USER_HANDLES, 0, 0, &rx_msg);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "read_msg on bad handle");

    rc = read_msg(handle_base + MAX_USER_HANDLES + 1, 0, 0, &rx_msg);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "read_msg on bad handle");

    rc = read_msg(handle_base - 1, 0, 0, &rx_msg);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "read_msg on bad handle");

    /* calling read_msg with NULL msg should fail for any handle */
    rc = read_msg(handle_base + MAX_USER_HANDLES, 0, 0, NULL);
    EXPECT_EQ(ERR_FAULT, rc, "read_msg on NULL msg");

    /* send_msg on non-existing handle that is in valid range */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        rc = read_msg(handle_base + i, 0, 0, &rx_msg);
        EXPECT_EQ(ERR_NOT_FOUND, rc, "read_msg on non existing handle");

        /* calling send_msg with NULL msg should fail for any handle */
        rc = read_msg(handle_base + i, 0, 0, NULL);
        EXPECT_EQ(ERR_FAULT, rc, "read_msg on NULL msg");
    }

    /* calling read_msg on port handle should fail
       because read_msg is only applicable to channels */
    sprintf(path, "%s.main.%s", SRV_PATH_BASE, "datasink");
    rc = port_create(path, 2, MAX_PORT_BUF_SIZE, IPC_PORT_ALLOW_TA_CONNECT);
    EXPECT_GT_ZERO(rc, "create datasink port");
    port = (handle_t)rc;

    rc = read_msg(port, 0, 0, &rx_msg);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "read_msg on port");
    close(port);

    /* open connection to echo service */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "echo");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to datasink");
    chan = (handle_t)rc;

    /* NULL msg on valid channel */
    rc = read_msg(chan, 0, 0, NULL);
    EXPECT_EQ(ERR_FAULT, rc, "read_msg on NULL msg");

    /* read_msg on invalid msg id */
    rc = read_msg(chan, 0, 0, &rx_msg);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "read_msg on invalid msg id");

    rc = read_msg(chan, 1000, 0, &rx_msg);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "read_msg on invalid msg id");

    /* send a message to echo service */
    memset(tx_buf, 0x55, sizeof(tx_buf));
    tx_iov.iov_base = tx_buf;
    tx_iov.iov_len = sizeof(tx_buf);
    tx_msg.num_iov = 1;
    tx_msg.iov = &tx_iov;
    tx_msg.num_handles = 0;
    tx_msg.handles = NULL;

    rc = send_msg(chan, &tx_msg);
    EXPECT_EQ(64, rc, "sending msg to echo");

    /* and wait for response */
    rc = wait(chan, &uevt, 1000);
    EXPECT_EQ(NO_ERROR, rc, "waiting on echo response");
    EXPECT_EQ(chan, uevt.handle, "wait on channel");

    rc = get_msg(chan, &inf);
    EXPECT_EQ(NO_ERROR, rc, "getting echo msg");
    EXPECT_EQ(sizeof(tx_buf), inf.len, "echo message reply length");

    /* now we have valid message with valid id */

    rx_iov[0].iov_len = sizeof(rx_buf) / 2;
    rx_iov[1].iov_len = sizeof(rx_buf) / 2;

    /* read message with invalid iovec array */
    rx_msg.iov = NULL;
    rx_msg.num_iov = 2;
    rc = read_msg(chan, inf.id, 0, &rx_msg);
    EXPECT_EQ(ERR_FAULT, rc, "read with invalid iovec array");

    /* read with invalid iovec entry */
    rx_iov[0].iov_base = NULL;
    rx_iov[1].iov_base = NULL;
    rx_msg.iov = rx_iov;
    rc = read_msg(chan, inf.id, 0, &rx_msg);
    EXPECT_EQ(ERR_FAULT, rc, "read with invalid iovec");

    rx_iov[0].iov_base = rx_buf;
    rx_iov[1].iov_base = NULL;
    rc = read_msg(chan, inf.id, 0, &rx_msg);
    EXPECT_EQ(ERR_FAULT, rc, "read with invalid iovec");

    rx_iov[0].iov_base = rx_buf;
    rx_iov[1].iov_base = rx_buf + sizeof(rx_buf) / 2;

    /* read with invalid offset with valid iovec array */
    rc = read_msg(chan, inf.id, inf.len + 1, &rx_msg);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "read with invalid offset");

    /* cleanup */
    rc = put_msg(chan, inf.id);
    EXPECT_EQ(NO_ERROR, rc, "putting echo msg");

    rc = close(chan);
    EXPECT_EQ(NO_ERROR, rc, "close channel");
}

TEST(ipc, end_to_end_msg) {
    int rc;
    handle_t chan;
    uevent_t uevt;
    char path[MAX_PORT_PATH_LEN];
    uint8_t tx_buf[64];
    uint8_t rx_buf[64];
    ipc_msg_info_t inf;
    ipc_msg_t tx_msg;
    struct iovec tx_iov;
    ipc_msg_t rx_msg;
    struct iovec rx_iov;

    tx_iov.iov_base = tx_buf;
    tx_iov.iov_len = sizeof(tx_buf);
    tx_msg.num_iov = 1;
    tx_msg.iov = &tx_iov;
    tx_msg.num_handles = 0;
    tx_msg.handles = NULL;

    rx_iov.iov_base = rx_buf;
    rx_iov.iov_len = sizeof(rx_buf);
    rx_msg.num_iov = 1;
    rx_msg.iov = &rx_iov;
    rx_msg.num_handles = 0;
    rx_msg.handles = NULL;

    memset(tx_buf, 0x55, sizeof(tx_buf));
    memset(rx_buf, 0xaa, sizeof(rx_buf));

    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "echo");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to echo");

    if (rc >= 0) {
        unsigned int tx_cnt = 0;
        unsigned int rx_cnt = 0;

        chan = (handle_t)rc;

        /* send 10000 messages synchronously, waiting for reply
           for each one
         */
        tx_cnt = 10000;
        while (tx_cnt) {
            /* send a message */
            rc = send_msg(chan, &tx_msg);
            EXPECT_EQ(64, rc, "sending msg to echo");

            /* wait for response */
            rc = wait(chan, &uevt, 1000);
            EXPECT_EQ(NO_ERROR, rc, "waiting on echo response");
            EXPECT_EQ(chan, uevt.handle, "wait on channel");

            /* get a reply */
            rc = get_msg(chan, &inf);
            EXPECT_EQ(NO_ERROR, rc, "getting echo msg");

            /* read reply data */
            rc = read_msg(chan, inf.id, 0, &rx_msg);
            EXPECT_EQ(64, rc, "reading echo msg");

            /* discard reply */
            rc = put_msg(chan, inf.id);
            EXPECT_EQ(NO_ERROR, rc, "putting echo msg");

            tx_cnt--;
        }

        /* send/receive 10000 messages asynchronously. */
        rx_cnt = tx_cnt = 10000;
        while (tx_cnt || rx_cnt) {
            /* send messages until all buffers are full */
            while (tx_cnt) {
                rc = send_msg(chan, &tx_msg);
                if (rc == ERR_NOT_ENOUGH_BUFFER)
                    break; /* no more space */
                EXPECT_EQ(64, rc, "sending msg to echo");
                if (rc != 64)
                    goto abort_test;
                tx_cnt--;
            }

            /* wait for reply msg or room */
            rc = wait(chan, &uevt, 1000);
            EXPECT_EQ(NO_ERROR, rc, "waiting for reply");
            EXPECT_EQ(chan, uevt.handle, "wait on channel");

            /* drain all messages */
            while (rx_cnt) {
                /* get a reply */
                rc = get_msg(chan, &inf);
                if (rc == ERR_NO_MSG)
                    break; /* no more messages  */

                EXPECT_EQ(NO_ERROR, rc, "getting echo msg");

                /* read reply data */
                rc = read_msg(chan, inf.id, 0, &rx_msg);
                EXPECT_EQ(64, rc, "reading echo msg");

                /* discard reply */
                rc = put_msg(chan, inf.id);
                EXPECT_EQ(NO_ERROR, rc, "putting echo msg");

                rx_cnt--;
            }

            if (HasFailure())
                break;
        }

    abort_test:
        EXPECT_EQ(0, tx_cnt, "tx_cnt");
        EXPECT_EQ(0, rx_cnt, "rx_cnt");

        rc = close(chan);
        EXPECT_EQ(NO_ERROR, rc, "close channel");
    }
}

/****************************************************************************/

TEST(ipc, hset_create) {
    handle_t hset1;
    handle_t hset2;

    hset1 = handle_set_create();
    EXPECT_GE_ZERO((int)hset1, "create handle set1");

    hset2 = handle_set_create();
    EXPECT_GE_ZERO((int)hset2, "create handle set2");

    close(hset1);
    close(hset2);
}

TEST(ipc, hset_add_mod_del) {
    int rc;
    handle_t hset1;
    handle_t hset2;

    hset1 = handle_set_create();
    EXPECT_GE_ZERO((int)hset1, "create handle set1");

    hset2 = handle_set_create();
    EXPECT_GE_ZERO((int)hset2, "create handle set2");

    ABORT_IF_NOT_OK(abort_test);

    uevent_t evt = {
            .handle = hset2,
            .event = ~0U,
            .cookie = NULL,
    };

    /* add handle to handle set */
    rc = handle_set_ctrl(hset1, HSET_ADD, &evt);
    EXPECT_EQ(0, rc, "hset add");

    /* modify handle attributes in handle set */
    rc = handle_set_ctrl(hset1, HSET_MOD, &evt);
    EXPECT_EQ(0, rc, "hset mod");

    /* remove handle from handle set */
    rc = handle_set_ctrl(hset1, HSET_DEL, &evt);
    EXPECT_EQ(0, rc, "hset del");

abort_test:
    close(hset1);
    close(hset2);
}

TEST(ipc, hset_add_self) {
    int rc;
    handle_t hset1;
    handle_t hset1_dup;

    hset1 = handle_set_create();
    EXPECT_GE_ZERO((int)hset1, "create handle set1");

    hset1_dup = dup(hset1);
    EXPECT_GE_ZERO((int)hset1_dup, "duplicate handle set1");

    ABORT_IF_NOT_OK(abort_test);

    uevent_t evt = {
            .handle = hset1,
            .event = ~0U,
            .cookie = NULL,
    };

    /* add handle to handle set */
    rc = handle_set_ctrl(hset1, HSET_ADD, &evt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "hset add self");

    /* add handle to handle set */
    evt.handle = hset1_dup;
    rc = handle_set_ctrl(hset1, HSET_ADD, &evt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "hset add duplicate of self");

abort_test:
    close(hset1);
    close(hset1_dup);
}

TEST(ipc, hset_add_loop) {
    int rc;
    handle_t hset1;
    handle_t hset2;
    handle_t hset3;

    hset1 = handle_set_create();
    EXPECT_GE_ZERO((int)hset1, "create handle set1");

    hset2 = handle_set_create();
    EXPECT_GE_ZERO((int)hset2, "create handle set2");

    hset3 = handle_set_create();
    EXPECT_GE_ZERO((int)hset3, "create handle set3");

    ABORT_IF_NOT_OK(abort_test);

    uevent_t evt = {
            .handle = hset2,
            .event = ~0U,
            .cookie = NULL,
    };

    /* add hset2 to hset1 */
    rc = handle_set_ctrl(hset1, HSET_ADD, &evt);
    EXPECT_EQ(0, rc, "add hset2 to hset1");

    /* add hset3 to hset2 */
    evt.handle = hset3;
    rc = handle_set_ctrl(hset2, HSET_ADD, &evt);
    EXPECT_EQ(0, rc, "add hset3 to hset2");

    /* add hset1 to hset3 */
    evt.handle = hset1;
    rc = handle_set_ctrl(hset3, HSET_ADD, &evt);
    EXPECT_EQ(ERR_INVALID_ARGS, rc, "add hset1 to hset3");

abort_test:
    close(hset2);
    close(hset1);
    close(hset3);
}

TEST(ipc, hset_add_duplicate) {
    int rc;
    handle_t hset1;
    handle_t hset2;
    handle_t hset2_dup;

    hset1 = handle_set_create();
    EXPECT_GE_ZERO((int)hset1, "create handle set1");

    hset2 = handle_set_create();
    EXPECT_GE_ZERO((int)hset2, "create handle set2");

    hset2_dup = dup(hset2);
    EXPECT_GE_ZERO((int)hset2_dup, "duplicate handle set2");

    ABORT_IF_NOT_OK(abort_test);

    uevent_t evt = {
            .handle = hset2,
            .event = ~0U,
            .cookie = NULL,
    };

    /* add hset2 to hset1 */
    rc = handle_set_ctrl(hset1, HSET_ADD, &evt);
    EXPECT_EQ(0, rc, "add hset2 to hset1");

    /* add hset2 to hset1 again */
    rc = handle_set_ctrl(hset1, HSET_ADD, &evt);
    EXPECT_EQ(ERR_ALREADY_EXISTS, rc, "add hset2 to hset1");

    /* add duplicate of hset2 to hset1 */
    evt.handle = hset2_dup;
    rc = handle_set_ctrl(hset1, HSET_ADD, &evt);
    EXPECT_EQ(0, rc, "add hset2 duplicate to hset1");

abort_test:
    close(hset1);
    close(hset2);
    close(hset2_dup);
}

TEST(ipc, hset_wait_on_empty_set) {
    int rc;
    uevent_t evt;
    handle_t hset1;

    hset1 = handle_set_create();
    EXPECT_GE_ZERO((int)hset1, "create hset");

    ABORT_IF_NOT_OK(abort_test);

    /* wait with zero timeout */
    rc = wait(hset1, &evt, 0);
    EXPECT_EQ(ERR_NOT_FOUND, rc, "wait on empty hset");

    /* wait with non-zero timeout */
    rc = wait(hset1, &evt, 100);
    EXPECT_EQ(ERR_NOT_FOUND, rc, "wait on empty hset");

    close(hset1);

abort_test:;
}

TEST(ipc, hset_wait_on_non_empty_set) {
    int rc;
    handle_t hset1;
    handle_t hset2;

    hset1 = handle_set_create();
    EXPECT_GE_ZERO((int)hset1, "create handle set1");

    hset2 = handle_set_create();
    EXPECT_GE_ZERO((int)hset2, "create handle set2");

    ABORT_IF_NOT_OK(abort_test);

    uevent_t evt = {
            .handle = hset2,
            .event = ~0U,
            .cookie = NULL,
    };

    /* add hset2 to hset1 */
    rc = handle_set_ctrl(hset1, HSET_ADD, &evt);
    EXPECT_EQ(0, rc, "add hset2 to hset1");

    /* wait with zero timeout on hset1 */
    rc = wait(hset1, &evt, 0);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on non-empty hset");

    /* wait with non-zero timeout on hset1 */
    rc = wait(hset1, &evt, 100);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on non-empty hset");

abort_test:
    close(hset1);
    close(hset2);
}

TEST(ipc, hset_wait_on_duplicate_in_set) {
    int rc;
    handle_t hset1;
    handle_t hset2;
    handle_t hset2_dup;

    hset1 = handle_set_create();
    EXPECT_GE_ZERO((int)hset1, "create handle set1");

    hset2 = handle_set_create();
    EXPECT_GE_ZERO((int)hset2, "create handle set2");

    hset2_dup = dup(hset2);
    EXPECT_GE_ZERO((int)hset2_dup, "duplicate handle set2");

    ABORT_IF_NOT_OK(abort_test);

    uevent_t evt = {
            .handle = hset2,
            .event = ~0U,
            .cookie = NULL,
    };

    /* add hset2 to hset1 */
    rc = handle_set_ctrl(hset1, HSET_ADD, &evt);
    EXPECT_EQ(NO_ERROR, rc, "add hset2 to hset1");

    /* add hset2_dup to hset1 */
    evt.handle = hset2_dup;
    rc = handle_set_ctrl(hset1, HSET_ADD, &evt);
    EXPECT_EQ(NO_ERROR, rc, "add hset2_dup to hset1");

    /* wait with zero timeout on hset1 */
    rc = wait(hset1, &evt, 0);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on non-empty hset");

    /* wait with non-zero timeout on hset1 */
    rc = wait(hset1, &evt, 100);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on non-empty hset");

    /* remove handle from handle set */
    evt.handle = hset2;
    rc = handle_set_ctrl(hset1, HSET_DEL, &evt);
    EXPECT_EQ(NO_ERROR, rc, "hset del");

    /* wait with zero timeout on hset1 */
    rc = wait(hset1, &evt, 0);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on non-empty hset");

    /* wait with non-zero timeout on hset1 */
    rc = wait(hset1, &evt, 100);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on non-empty hset");

    /* remove duplicate from handle set */
    evt.handle = hset2_dup;
    rc = handle_set_ctrl(hset1, HSET_DEL, &evt);
    EXPECT_EQ(NO_ERROR, rc, "hset del");

    /* wait with zero timeout */
    rc = wait(hset1, &evt, 0);
    EXPECT_EQ(ERR_NOT_FOUND, rc, "wait on empty hset");

    /* wait with non-zero timeout */
    rc = wait(hset1, &evt, 100);
    EXPECT_EQ(ERR_NOT_FOUND, rc, "wait on empty hset");

abort_test:
    close(hset1);
    close(hset2);
    close(hset2_dup);
}

TEST(ipc, DISABLED_WITH_COVERAGE(dup_no_resources)) {
    int rc;
    handle_t hsets[MAX_USER_HANDLES];

    /* create maximum number of handle sets */
    for (unsigned int i = first_free_handle_index; i < MAX_USER_HANDLES; i++) {
        hsets[i] = handle_set_create();
        EXPECT_GE_ZERO((int)hsets[i], "create handle set");
    }

    rc = dup(hsets[first_free_handle_index]);
    EXPECT_EQ(ERR_NO_RESOURCES, rc, "no more handles");

    /* free 1 handle so we have room and repeat test */
    rc = close(hsets[first_free_handle_index]);
    EXPECT_EQ(NO_ERROR, rc, "close one set");
    hsets[first_free_handle_index] = INVALID_IPC_HANDLE;

    /* the only free handle here should be first_free_handle_index */
    rc = dup(hsets[first_free_handle_index + 1]);
    EXPECT_EQ(handle_base + first_free_handle_index, rc);
    close(rc);

    /* close them all */
    for (unsigned int i = first_free_handle_index + 1; i < MAX_USER_HANDLES;
         i++) {
        /* close a valid set */
        rc = close(hsets[i]);
        EXPECT_EQ(NO_ERROR, rc, "close set");
        hsets[i] = INVALID_IPC_HANDLE;
    }
}

/*
 * Disabled per b/140836874 - believed to be a race in the test code, not
 * in IPC.
 */
TEST(ipc, DISABLED_hset_add_chan) {
    int rc;
    uevent_t evt;
    handle_t hset1;
    handle_t hset2;
    handle_t chan1;
    handle_t chan2;
    void* cookie1 = (void*)"cookie1";
    void* cookie2 = (void*)"cookie2";
    void* cookie11 = (void*)"cookie11";
    void* cookie12 = (void*)"cookie12";
    void* cookie21 = (void*)"cookie21";
    void* cookie22 = (void*)"cookie22";
    void* cookiehs2 = (void*)"cookiehs2";
    uint8_t buf0[64];
    struct iovec iov;
    ipc_msg_t msg;

    /* prepare test buffer */
    fill_test_buf(buf0, sizeof(buf0), 0x55);

    chan1 = connect(SRV_PATH_BASE ".srv.echo", IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO((int)chan1, "connect to echo chan1");

    rc = set_cookie(chan1, cookie1);
    EXPECT_EQ(0, rc, "cookie1");

    chan2 = connect(SRV_PATH_BASE ".srv.echo", IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO((int)chan2, "connect to echo chan2");

    rc = set_cookie(chan2, cookie2);
    EXPECT_EQ(0, rc, "cookie2");

    /* send message over chan1 and chan2 */
    iov.iov_base = buf0;
    iov.iov_len = sizeof(buf0);
    msg.num_handles = 0;
    msg.handles = NULL;
    msg.num_iov = 1;
    msg.iov = &iov;

    rc = send_msg(chan1, &msg);
    EXPECT_EQ(64, rc, "send over chan1");

    rc = send_msg(chan2, &msg);
    EXPECT_EQ(64, rc, "send over chan2");

    hset1 = handle_set_create();
    EXPECT_GE_ZERO((int)hset1, "create handle set1");

    hset2 = handle_set_create();
    EXPECT_GE_ZERO((int)hset2, "create handle set2");

    ABORT_IF_NOT_OK(abort_test);

    /* chan1 to hset2 */
    evt.handle = chan1;
    evt.event = ~0U;
    evt.cookie = cookie12;
    rc = handle_set_ctrl(hset2, HSET_ADD, &evt);
    EXPECT_EQ(0, rc, "add hset2 to hset1");

    /* chan2 to hset2 */
    evt.handle = chan2;
    evt.event = ~0U;
    evt.cookie = cookie22;
    rc = handle_set_ctrl(hset2, HSET_ADD, &evt);
    EXPECT_EQ(0, rc, "add hset2 to hset1");

    /* add hset2 to hset1 */
    evt.handle = hset2;
    evt.event = ~0U;
    evt.cookie = cookiehs2;
    rc = handle_set_ctrl(hset1, HSET_ADD, &evt);
    EXPECT_EQ(0, rc, "add hset2 to hset1");

    /* chan1 to hset1 */
    evt.handle = chan1;
    evt.event = ~0U;
    evt.cookie = cookie11;
    rc = handle_set_ctrl(hset1, HSET_ADD, &evt);
    EXPECT_EQ(0, rc, "add hset2 to hset1");

    /* chan2 to hset1 */
    evt.handle = chan2;
    evt.event = ~0U;
    evt.cookie = cookie21;
    rc = handle_set_ctrl(hset1, HSET_ADD, &evt);
    EXPECT_EQ(0, rc, "add hset2 to hset1");

    /* wait on chan1 directly */
    rc = wait(chan1, &evt, 1000);
    EXPECT_EQ(0, rc, "wait on chan1");
    EXPECT_EQ(chan1, evt.handle, "event.handle");
    EXPECT_EQ(cookie1, evt.cookie, "event.cookie");

    /* wait on chan2 directly */
    rc = wait(chan2, &evt, 1000);
    EXPECT_EQ(0, rc, "wait on chan2");
    EXPECT_EQ(chan2, evt.handle, "event.handle");
    EXPECT_EQ(cookie2, evt.cookie, "event.cookie");

    /* wait on hset1 */
    rc = wait(hset1, &evt, 1000);
    EXPECT_EQ(0, rc, "wait on hset1");
    EXPECT_EQ(hset2, evt.handle, "event.handle");
    EXPECT_EQ(cookiehs2, evt.cookie, "event.cookie");

    /* wait on hset1 again (chan1 should be ready) */
    rc = wait(hset1, &evt, 1000);
    EXPECT_EQ(0, rc, "wait on hset1");
    EXPECT_EQ(chan1, evt.handle, "event.handle");
    EXPECT_EQ(cookie11, evt.cookie, "event.cookie");

    /* wait on hset1 again (chan2 should be ready) */
    rc = wait(hset1, &evt, 1000);
    EXPECT_EQ(0, rc, "wait on hset1");
    EXPECT_EQ(chan2, evt.handle, "event.handle");
    EXPECT_EQ(cookie21, evt.cookie, "event.cookie");

    /* wait on hset1 again (hset2 should be ready) */
    rc = wait(hset1, &evt, 1000);
    EXPECT_EQ(0, rc, "wait on hset1");
    EXPECT_EQ(hset2, evt.handle, "event.handle");
    EXPECT_EQ(cookiehs2, evt.cookie, "event.cookie");

    /* wait on hset2 (chan1 should be ready) */
    rc = wait(hset2, &evt, 1000);
    EXPECT_EQ(0, rc, "wait on hset2");
    EXPECT_EQ(chan1, evt.handle, "event.handle");
    EXPECT_EQ(cookie12, evt.cookie, "event.cookie");

    /* wait on hset2 again (chan2 should be ready) */
    rc = wait(hset2, &evt, 1000);
    EXPECT_EQ(0, rc, "wait on hset2");
    EXPECT_EQ(chan2, evt.handle, "event.handle");
    EXPECT_EQ(cookie22, evt.cookie, "event.cookie");

    /* wait on hset2 again (chan1 should be ready) */
    rc = wait(hset2, &evt, 1000);
    EXPECT_EQ(0, rc, "wait on chan1");
    EXPECT_EQ(chan1, evt.handle, "event.handle");
    EXPECT_EQ(cookie12, evt.cookie, "event.cookie");

abort_test:
    close(chan1);
    close(chan2);
    close(hset1);
    close(hset2);
}

TEST(ipc, hset_event_mask) {
    int rc;
    uevent_t evt;
    handle_t hset1;
    handle_t chan1;
    void* cookie1 = (void*)"cookie1";
    void* cookie11 = (void*)"cookie11";
    uint8_t buf0[64];
    struct iovec iov;
    ipc_msg_t msg;

    /* prepare test buffer */
    fill_test_buf(buf0, sizeof(buf0), 0x55);

    chan1 = connect(SRV_PATH_BASE ".srv.echo", IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO((int)chan1, "connect to echo");

    rc = set_cookie(chan1, cookie1);
    EXPECT_EQ(0, rc, "cookie1");

    /* send message over chan1 and chan2 */
    iov.iov_base = buf0;
    iov.iov_len = sizeof(buf0);
    msg.num_handles = 0;
    msg.handles = NULL;
    msg.num_iov = 1;
    msg.iov = &iov;

    rc = send_msg(chan1, &msg);
    EXPECT_EQ(64, rc, "send over chan1");

    hset1 = handle_set_create();
    EXPECT_GE_ZERO((int)hset1, "create handle set1");

    ABORT_IF_NOT_OK(abort_test);

    /* chan1 to hset1 */
    evt.handle = chan1;
    evt.event = ~0U;
    evt.cookie = cookie11;
    rc = handle_set_ctrl(hset1, HSET_ADD, &evt);
    EXPECT_EQ(0, rc, "add chan1 to hset1");

    /* wait of chan1 handle */
    rc = wait(chan1, &evt, 100);
    EXPECT_EQ(0, rc, "wait on chan1");
    EXPECT_EQ(chan1, evt.handle, "event.handle");
    EXPECT_EQ(cookie1, evt.cookie, "event.cookie");

    /* wait on hset1 (should get chan1) */
    rc = wait(hset1, &evt, 100);
    EXPECT_EQ(0, rc, "wait on hset1");
    EXPECT_EQ(chan1, evt.handle, "event.handle");
    EXPECT_EQ(cookie11, evt.cookie, "event.cookie");

    /* mask off chan1 in hset1 */
    evt.handle = chan1;
    evt.event = 0;
    evt.cookie = cookie11;
    rc = handle_set_ctrl(hset1, HSET_MOD, &evt);
    EXPECT_EQ(0, rc, "mod chan1 in hset1");

    /* wait on hset1 (should get chan1) */
    rc = wait(hset1, &evt, 100);
    EXPECT_EQ(ERR_TIMED_OUT, rc, "wait on hset1");

    /* unmask off chan1 in hset1 */
    evt.handle = chan1;
    evt.event = ~0U;
    evt.cookie = cookie11;
    rc = handle_set_ctrl(hset1, HSET_MOD, &evt);
    EXPECT_EQ(0, rc, "mod chan1 in hset1");

    /* wait on hset1 (should get chan1) */
    rc = wait(hset1, &evt, 100);
    EXPECT_EQ(0, rc, "wait on hset1");
    EXPECT_EQ(chan1, evt.handle, "event.handle");
    EXPECT_EQ(cookie11, evt.cookie, "event.cookie");

abort_test:
    close(chan1);
    close(hset1);
}

/****************************************************************************/

TEST(ipc, send_handle) {
    int rc;
    struct iovec iov;
    ipc_msg_t msg;
    handle_t hchan1;
    handle_t hchan2;
    uint8_t buf0[64];
    char path[MAX_PORT_PATH_LEN];

    /* prepare test buffer */
    fill_test_buf(buf0, sizeof(buf0), 0x55);

    /* open connection to datasink service */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "datasink");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to datasink");
    ABORT_IF_NOT_OK(err_connect1);
    hchan1 = (handle_t)rc;

    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to datasink");
    ABORT_IF_NOT_OK(err_connect2);
    hchan2 = (handle_t)rc;

    /* send hchan2 handle over hchan1 connection */
    iov.iov_base = buf0;
    iov.iov_len = sizeof(buf0);
    msg.iov = &iov;
    msg.num_iov = 1;
    msg.handles = &hchan2;
    msg.num_handles = 1;

    /* send and wait a bit */
    rc = send_msg(hchan1, &msg);
    EXPECT_EQ(64, rc, "send handle");
    trusty_nanosleep(0, 0, 100 * MSEC);

    /* send it again and close it */
    rc = send_msg(hchan1, &msg);
    EXPECT_EQ(64, rc, "send handle");
    rc = close(hchan2);
    EXPECT_EQ(NO_ERROR, rc, "close chan2");

err_connect2:
    rc = close(hchan1);
    EXPECT_EQ(NO_ERROR, rc, "close chan1");
err_connect1:;
}

TEST(ipc, send_handle_negative) {
    int rc;
    ipc_msg_t msg;
    handle_t hchan;
    handle_t hsend[10];
    char path[MAX_PORT_PATH_LEN];

    /* open connection to datasink service */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "datasink");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to datasink");
    ABORT_IF_NOT_OK(err_connect);
    hchan = (handle_t)rc;

    for (unsigned int i = 0; i < countof(hsend); i++)
        hsend[i] = hchan;

    /* send 8 copies of yourself to datasync (should be fine) */
    msg.iov = NULL;
    msg.num_iov = 0;
    msg.handles = &hsend[0];
    msg.num_handles = 8;
    rc = send_msg(hchan, &msg);
    EXPECT_EQ(0, rc, "send handle");

    /* send 8 copies of yourself to datasync with handle poiting to NULL*/
    msg.iov = NULL;
    msg.num_iov = 0;
    msg.handles = NULL;
    msg.num_handles = 8;
    rc = send_msg(hchan, &msg);
    EXPECT_EQ(ERR_FAULT, rc, "send handle");

    /* call with invalid handle should return ERR_FAULT */
    msg.handles = (handle_t*)0x100;
    msg.num_handles = 8;
    rc = send_msg(hchan, &msg);
    EXPECT_EQ(ERR_FAULT, rc, "send handle");

    /* send more than 8, should fail */
    msg.handles = &hsend[0];
    msg.num_handles = 10;
    rc = send_msg(hchan, &msg);
    EXPECT_EQ(ERR_TOO_BIG, rc, "send handle");

    rc = close(hchan);
    EXPECT_EQ(NO_ERROR, rc, "close chan");
err_connect:;
}

TEST(ipc, recv_handle) {
    int rc;
    handle_t hchan1;
    handle_t hchan2;
    handle_t hrecv[2];
    uint8_t buf0[64];
    struct iovec iov;
    ipc_msg_t msg;
    uevent_t evt;
    ipc_msg_info_t inf;
    char path[MAX_PORT_PATH_LEN];

    /* prepare test buffer */
    fill_test_buf(buf0, sizeof(buf0), 0x55);

    /* open connection to echo service */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "echo");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to echo");
    ABORT_IF_NOT_OK(err_connect1);
    hchan1 = (handle_t)rc;

    /* open second connection to echo service */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "echo");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to echo");
    ABORT_IF_NOT_OK(err_connect2);
    hchan2 = (handle_t)rc;

    /* send message with handle */
    iov.iov_base = buf0;
    iov.iov_len = sizeof(buf0);
    msg.iov = &iov;
    msg.num_iov = 1;
    msg.handles = &hchan2;
    msg.num_handles = 1;

    rc = send_msg(hchan1, &msg);
    EXPECT_EQ(64, rc, "send_handle");

    /* wait for reply */
    rc = wait(hchan1, &evt, 1000);
    EXPECT_EQ(0, rc, "wait for reply");
    EXPECT_EQ(hchan1, evt.handle, "event.handle");

    /* get reply message */
    rc = get_msg(hchan1, &inf);
    EXPECT_EQ(NO_ERROR, rc, "getting echo reply");
    EXPECT_EQ(sizeof(buf0), inf.len, "reply len");
    EXPECT_EQ(1, inf.num_handles, "reply num_handles");

    /* read reply data and no handles */
    hrecv[0] = INVALID_IPC_HANDLE;
    hrecv[1] = INVALID_IPC_HANDLE;
    msg.handles = &hrecv[0];
    msg.num_handles = 0;
    rc = read_msg(hchan1, inf.id, 0, &msg);
    EXPECT_EQ(64, rc, "reading echo reply");

    rc = close(hrecv[0]);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "close reply handle");

    rc = close(hrecv[1]);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "close reply handle");

    /* read reply data and 1 handle */
    hrecv[0] = INVALID_IPC_HANDLE;
    hrecv[1] = INVALID_IPC_HANDLE;
    msg.handles = &hrecv[0];
    msg.num_handles = 1;
    rc = read_msg(hchan1, inf.id, 0, &msg);
    EXPECT_EQ(64, rc, "reading echo reply");

    rc = close(hrecv[0]);
    EXPECT_EQ(0, rc, "close reply handle");

    rc = close(hrecv[1]);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "close reply handle");

    /* read reply data and 2 handles (second one should be invalid) */
    hrecv[0] = INVALID_IPC_HANDLE;
    hrecv[1] = INVALID_IPC_HANDLE;
    msg.handles = &hrecv[0];
    msg.num_handles = 2;
    rc = read_msg(hchan1, inf.id, 0, &msg);
    EXPECT_EQ(64, rc, "reading echo reply");

    rc = close(hrecv[0]);
    EXPECT_EQ(0, rc, "close reply handle");

    rc = close(hrecv[1]);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "close reply handle");

    /* read 1 handle with no data */
    hrecv[0] = INVALID_IPC_HANDLE;
    hrecv[1] = INVALID_IPC_HANDLE;
    msg.num_iov = 0;
    msg.handles = &hrecv[0];
    msg.num_handles = 1;
    rc = read_msg(hchan1, inf.id, 0, &msg);
    EXPECT_EQ(0, rc, "reading echo reply");

    EXPECT_EQ(INVALID_IPC_HANDLE, hrecv[1], "reading echo reply");

    /* read same handle for the second time */
    msg.handles = &hrecv[1];
    msg.num_handles = 1;
    rc = read_msg(hchan1, inf.id, 0, &msg);
    EXPECT_EQ(0, rc, "reading echo reply");

    rc = close(hrecv[0]);
    EXPECT_EQ(0, rc, "close reply handle");

    rc = close(hrecv[1]);
    EXPECT_EQ(0, rc, "close reply handle");

    /* discard reply */
    rc = put_msg(hchan1, inf.id);
    EXPECT_EQ(NO_ERROR, rc, "putting echo reply");

    close(hchan2);
    EXPECT_EQ(NO_ERROR, rc, "close chan2");
err_connect2:
    close(hchan1);
    EXPECT_EQ(NO_ERROR, rc, "close chan1");
err_connect1:;
}

TEST(ipc, recv_handle_negative) {
    int rc;
    handle_t hchan1;
    ipc_msg_t msg;
    uevent_t evt;
    ipc_msg_info_t inf;
    char path[MAX_PORT_PATH_LEN];

    /* open connection to echo service */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "echo");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to echo");
    ABORT_IF_NOT_OK(err_connect1);
    hchan1 = (handle_t)rc;

    /* send message with handle attached */
    msg.iov = NULL;
    msg.num_iov = 0;
    msg.handles = &hchan1;
    msg.num_handles = 1;

    rc = send_msg(hchan1, &msg);
    EXPECT_EQ(0, rc, "send_handle");

    /* wait for reply */
    rc = wait(hchan1, &evt, 1000);
    EXPECT_EQ(0, rc, "wait for reply");
    EXPECT_EQ(hchan1, evt.handle, "event.handle");

    /* get reply message */
    rc = get_msg(hchan1, &inf);
    EXPECT_EQ(NO_ERROR, rc, "getting echo reply");
    EXPECT_EQ(0, inf.len, "reply len");
    EXPECT_EQ(1, inf.num_handles, "reply num_handles");

    /* read reply data with handles pointing to NULL */
    msg.handles = NULL;
    msg.num_handles = 1;
    rc = read_msg(hchan1, inf.id, 0, &msg);
    EXPECT_EQ(ERR_FAULT, rc, "reading echo reply");

    /* read reply data and bad handle ptr */
    msg.handles = (handle_t*)0x100;
    msg.num_handles = 1;
    rc = read_msg(hchan1, inf.id, 0, &msg);
    EXPECT_EQ(ERR_FAULT, rc, "reading echo reply");

    /* discard reply */
    rc = put_msg(hchan1, inf.id);
    EXPECT_EQ(NO_ERROR, rc, "putting echo reply");

    rc = close(hchan1);
    EXPECT_EQ(NO_ERROR, rc, "close chan1");
err_connect1:;
}

TEST(ipc, send_handle_bulk) {
    int rc;
    struct iovec iov;
    ipc_msg_t msg;
    handle_t hchan1;
    handle_t hchan2;
    uint8_t buf0[64];
    char path[MAX_PORT_PATH_LEN];

    /* prepare test buffer */
    fill_test_buf(buf0, sizeof(buf0), 0x55);

    /* open connection to datasink service */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "datasink");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to datasink");
    ABORT_IF_NOT_OK(err_connect1);
    hchan1 = (handle_t)rc;

    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to datasink");
    ABORT_IF_NOT_OK(err_connect2);
    hchan2 = (handle_t)rc;

    /* send hchan2 handle over hchan1 connection */
    iov.iov_base = buf0;
    iov.iov_len = sizeof(buf0);
    msg.iov = &iov;
    msg.num_iov = 1;
    msg.handles = &hchan2;
    msg.num_handles = 1;

    for (unsigned int i = 0; (i < 10000) && !HasFailure(); i++) {
        while (!HasFailure()) {
            rc = send_msg(hchan1, &msg);
            if (rc == ERR_NOT_ENOUGH_BUFFER) { /* wait for room */
                uevent_t uevt;
                unsigned int exp_event = IPC_HANDLE_POLL_SEND_UNBLOCKED;
                rc = wait(hchan1, &uevt, 10000);
                EXPECT_EQ(NO_ERROR, rc, "waiting for space");
                EXPECT_EQ(hchan1, uevt.handle, "waiting for space");
                EXPECT_EQ(exp_event, uevt.event, "waiting for space");
            } else {
                EXPECT_EQ(64, rc, "send_msg bulk");
                break;
            }
        }
    }
    rc = close(hchan2);
    EXPECT_EQ(NO_ERROR, rc, "close chan2");

    /* repeat the same while closing handle after sending it */
    for (unsigned int i = 0; (i < 10000) && !HasFailure(); i++) {
        rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
        EXPECT_GT_ZERO(rc, "connect to datasink");
        ABORT_IF_NOT_OK(err_connect2);
        hchan2 = (handle_t)rc;

        /* send hchan2 handle over hchan1 connection */
        iov.iov_base = buf0;
        iov.iov_len = sizeof(buf0);
        msg.iov = &iov;
        msg.num_iov = 1;
        msg.handles = &hchan2;
        msg.num_handles = 1;

        while (!HasFailure()) {
            rc = send_msg(hchan1, &msg);
            if (rc == ERR_NOT_ENOUGH_BUFFER) { /* wait for room */
                uevent_t uevt;
                unsigned int exp_event = IPC_HANDLE_POLL_SEND_UNBLOCKED;
                rc = wait(hchan1, &uevt, 10000);
                EXPECT_EQ(NO_ERROR, rc, "waiting for space");
                EXPECT_EQ(hchan1, uevt.handle, "waiting for space");
                EXPECT_EQ(exp_event, uevt.event, "waiting for space");
            } else {
                EXPECT_EQ(64, rc, "send_msg bulk");
                break;
            }
        }
        rc = close(hchan2);
        EXPECT_EQ(NO_ERROR, rc, "close chan2");
    }

err_connect2:
    rc = close(hchan1);
    EXPECT_EQ(NO_ERROR, rc, "close chan1");
err_connect1:;
}

TEST(ipc, echo_handle_bulk) {
    int rc;
    handle_t hchan1;
    handle_t hchan2;
    handle_t hrecv;
    uint8_t buf0[64];
    struct iovec iov;
    ipc_msg_t msg;
    uevent_t evt;
    ipc_msg_info_t inf;
    char path[MAX_PORT_PATH_LEN];

    /* prepare test buffer */
    fill_test_buf(buf0, sizeof(buf0), 0x55);

    /* open connection to echo service */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "echo");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to echo");
    ABORT_IF_NOT_OK(err_connect1);
    hchan1 = (handle_t)rc;

    /* open second connection to echo service */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "echo");
    rc = connect(path, IPC_CONNECT_WAIT_FOR_PORT);
    EXPECT_GT_ZERO(rc, "connect to echo");
    ABORT_IF_NOT_OK(err_connect2);
    hchan2 = (handle_t)rc;

    /* send the same handle 10000 times */
    for (unsigned int i = 0; (i < 10000) && !HasFailure(); i++) {
        /* send message with handle */
        iov.iov_base = buf0;
        iov.iov_len = sizeof(buf0);
        msg.iov = &iov;
        msg.num_iov = 1;
        msg.handles = &hchan2;
        msg.num_handles = 1;

        while (!HasFailure()) {
            rc = send_msg(hchan1, &msg);
            EXPECT_EQ(64, rc, "send_handle");
            if (rc == ERR_NOT_ENOUGH_BUFFER) { /* wait for room */
                uevent_t uevt;
                unsigned int exp_event = IPC_HANDLE_POLL_SEND_UNBLOCKED;
                rc = wait(hchan1, &uevt, 10000);
                EXPECT_EQ(NO_ERROR, rc, "waiting for space");
                EXPECT_EQ(hchan1, uevt.handle, "waiting for space");
                EXPECT_EQ(exp_event, uevt.event, "waiting for space");
            } else {
                EXPECT_EQ(64, rc, "send_msg bulk");
                break;
            }
        }

        /* wait for reply */
        rc = wait(hchan1, &evt, 1000);
        EXPECT_EQ(0, rc, "wait for reply");
        EXPECT_EQ(hchan1, evt.handle, "event.handle");

        /* get reply message */
        rc = get_msg(hchan1, &inf);
        EXPECT_EQ(NO_ERROR, rc, "getting echo reply");
        EXPECT_EQ(sizeof(buf0), inf.len, "reply len");
        EXPECT_EQ(1, inf.num_handles, "reply num_handles");

        /* read reply data and 1 handle */
        hrecv = INVALID_IPC_HANDLE;
        msg.handles = &hrecv;
        msg.num_handles = 1;
        rc = read_msg(hchan1, inf.id, 0, &msg);
        EXPECT_EQ(64, rc, "reading echo reply");

        /* discard reply */
        rc = put_msg(hchan1, inf.id);
        EXPECT_EQ(NO_ERROR, rc, "putting echo reply");

        /* close received handle */
        rc = close(hrecv);
        EXPECT_EQ(0, rc, "close reply handle");
    }

    rc = close(hchan2);
    EXPECT_EQ(NO_ERROR, rc, "close chan2");
err_connect2:
    rc = close(hchan1);
    EXPECT_EQ(NO_ERROR, rc, "close chan1");
err_connect1:;
}

/*
 * TIPC wrapper libtrary tests
 */
TEST(ipc, tipc_connect) {
    int rc;
    handle_t h = INVALID_IPC_HANDLE;
    char path[MAX_PORT_PATH_LEN];

    /* Make tipc_connect fail and check if handle is unchanged */
    rc = tipc_connect(&h, NULL);
    EXPECT_EQ(ERR_FAULT, rc, "failed tipc_connect");
    EXPECT_EQ(INVALID_IPC_HANDLE, h, "failed tipc_connect")

    rc = close(h);
    EXPECT_EQ(ERR_BAD_HANDLE, rc, "close failed tipc_connect");

    /* Normal case: should succeed */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "echo");
    rc = tipc_connect(&h, path);
    EXPECT_EQ(0, rc, "tipc_connect");
    EXPECT_GT_ZERO(h, "tipc_connect");

    rc = close(h);
    EXPECT_EQ(0, rc, "close tipc_connect");
}

TEST(ipc, tipc_send_recv_1) {
    int rc;
    uint8_t tx_buf[64];
    uint8_t rx_buf[64];
    char path[MAX_PORT_PATH_LEN];
    handle_t h = INVALID_IPC_HANDLE;
    struct uevent evt = UEVENT_INITIAL_VALUE(evt);

    /* open connection to echo service */
    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "echo");
    rc = tipc_connect(&h, path);
    EXPECT_EQ(0, rc, "tipc_connect");
    EXPECT_GT_ZERO(h, "tipc_connect");

    /* send/receive zero length message */
    rc = tipc_send1(h, NULL, 0);
    EXPECT_EQ(0, rc, "tipc_send_one");

    rc = wait(h, &evt, INFINITE_TIME);
    EXPECT_EQ(0, rc, "wait for reply");

    rc = tipc_recv1(h, 0, rx_buf, sizeof(rx_buf));
    EXPECT_EQ(0, rc, "tipc_recv_one");

    /* send/receive normal message: should succeed */
    memset(tx_buf, 0x55, sizeof(tx_buf));
    memset(rx_buf, 0xaa, sizeof(rx_buf));

    rc = tipc_send1(h, tx_buf, sizeof(tx_buf) / 2);
    EXPECT_EQ(sizeof(tx_buf) / 2, (size_t)rc, "tipc_send1");

    rc = wait(h, &evt, INFINITE_TIME);
    EXPECT_EQ(0, rc, "wait for reply");

    rc = tipc_recv1(h, 0, rx_buf, sizeof(rx_buf));
    EXPECT_EQ(sizeof(tx_buf) / 2, (size_t)rc, "tipc_recv1");

    rc = memcmp(tx_buf, rx_buf, sizeof(tx_buf) / 2);
    EXPECT_EQ(0, rc, "memcmp");

    /*
     * send and then receive into small buffer: test should fail and
     * message should be discarded
     */
    memset(tx_buf, 0x55, sizeof(tx_buf));
    memset(rx_buf, 0xaa, sizeof(rx_buf));

    rc = tipc_send1(h, tx_buf, sizeof(tx_buf));
    EXPECT_EQ(sizeof(tx_buf), (size_t)rc, "tipc_send1");

    rc = wait(h, &evt, INFINITE_TIME);
    EXPECT_EQ(0, rc, "wait for reply");

    rc = tipc_recv1(h, 0, rx_buf, sizeof(rx_buf) / 2);
    EXPECT_EQ(ERR_BAD_LEN, rc, "tipc_recv1");

    /*
     * send and then receive message shorter them minimum: should fail
     */
    memset(tx_buf, 0x55, sizeof(tx_buf));
    memset(rx_buf, 0xaa, sizeof(rx_buf));

    rc = tipc_send1(h, tx_buf, sizeof(tx_buf) / 2);
    EXPECT_EQ(sizeof(tx_buf) / 2, (size_t)rc, "tipc_send1");

    rc = wait(h, &evt, INFINITE_TIME);
    EXPECT_EQ(0, rc, "wait for reply");

    rc = tipc_recv1(h, sizeof(rx_buf), rx_buf, sizeof(rx_buf));
    EXPECT_EQ(ERR_BAD_LEN, rc, "tipc_recv1");

    /* clean up */
    rc = close(h);
    EXPECT_EQ(0, rc, "close tipc_connect");
}

TEST(ipc, tipc_send_recv_hdr_payload) {
    int rc;
    uint8_t tx_hdr[32];
    uint8_t tx_buf[64];
    uint8_t rx_hdr[32];
    uint8_t rx_buf[64];
    char path[MAX_PORT_PATH_LEN];
    handle_t h = INVALID_IPC_HANDLE;
    struct uevent evt = UEVENT_INITIAL_VALUE(evt);

    sprintf(path, "%s.srv.%s", SRV_PATH_BASE, "echo");
    rc = tipc_connect(&h, path);
    EXPECT_EQ(0, rc, "tipc_connect");
    EXPECT_GT_ZERO(h, "tipc_connect");

    /* send/receive normal message: should succeed */
    memset(tx_hdr, 0xaa, sizeof(tx_hdr));
    memset(tx_buf, 0x55, sizeof(tx_buf));
    memset(rx_hdr, 0x55, sizeof(rx_hdr));
    memset(rx_buf, 0xaa, sizeof(rx_buf));

    rc = tipc_send2(h, tx_hdr, sizeof(tx_hdr), tx_buf, sizeof(tx_buf) / 2);
    EXPECT_EQ(sizeof(tx_hdr) + sizeof(tx_buf) / 2, (size_t)rc, "tipc_send_two");

    rc = wait(h, &evt, INFINITE_TIME);
    EXPECT_EQ(0, rc, "wait for reply");

    rc = tipc_recv_hdr_payload(h, rx_hdr, sizeof(rx_hdr), rx_buf,
                               sizeof(rx_buf));
    EXPECT_EQ(sizeof(tx_hdr) + sizeof(tx_buf) / 2, (size_t)rc,
              "tipc_recv_hdr_payload");

    rc = memcmp(tx_hdr, rx_hdr, sizeof(tx_hdr));
    EXPECT_EQ(0, rc, "memcmp");

    rc = memcmp(tx_buf, rx_buf, sizeof(tx_buf) / 2);
    EXPECT_EQ(0, rc, "memcmp");

    /* send/receive message with zero length header */
    memset(tx_hdr, 0xaa, sizeof(tx_hdr));
    memset(tx_buf, 0x55, sizeof(tx_buf));
    memset(rx_hdr, 0xaa, sizeof(rx_hdr));
    memset(rx_buf, 0xaa, sizeof(rx_buf));

    rc = tipc_send2(h, NULL, 0, tx_buf, sizeof(tx_buf) / 2);
    EXPECT_EQ(sizeof(tx_buf) / 2, (size_t)rc, "tipc_send_two");

    rc = wait(h, &evt, INFINITE_TIME);
    EXPECT_EQ(0, rc, "wait for reply");

    rc = tipc_recv_hdr_payload(h, NULL, 0, rx_buf, sizeof(rx_buf));
    EXPECT_EQ(sizeof(tx_buf) / 2, (size_t)rc, "tipc_recv_hdr_payload");

    rc = memcmp(tx_buf, rx_buf, sizeof(tx_buf) / 2);
    EXPECT_EQ(0, rc, "memcmp");

    /* send/receive message with zero length payload */
    memset(tx_hdr, 0xaa, sizeof(tx_hdr));
    memset(tx_buf, 0x55, sizeof(tx_buf));
    memset(rx_hdr, 0xaa, sizeof(rx_hdr));
    memset(rx_buf, 0xaa, sizeof(rx_buf));

    rc = tipc_send2(h, tx_hdr, sizeof(tx_hdr), NULL, 0);
    EXPECT_EQ(sizeof(tx_hdr), (size_t)rc, "tipc_send_two");

    rc = wait(h, &evt, INFINITE_TIME);
    EXPECT_EQ(0, rc, "wait for reply");

    rc = tipc_recv_hdr_payload(h, rx_hdr, sizeof(rx_hdr), rx_buf,
                               sizeof(rx_buf));
    EXPECT_EQ(sizeof(tx_hdr), (size_t)rc, "tipc_recv_hdr_payload");

    rc = memcmp(tx_hdr, rx_hdr, sizeof(tx_hdr));
    EXPECT_EQ(0, rc, "memcmp");

    /*
     * send/receive header into larger buffer: should fail as more data expected
     */
    memset(tx_hdr, 0xaa, sizeof(tx_hdr));
    memset(tx_buf, 0x55, sizeof(tx_buf));
    memset(rx_hdr, 0xaa, sizeof(rx_hdr));
    memset(rx_buf, 0xaa, sizeof(rx_buf));

    rc = tipc_send2(h, tx_hdr, sizeof(tx_hdr) / 2, NULL, 0);
    EXPECT_EQ(sizeof(tx_hdr) / 2, (size_t)rc, "tipc_send_two");

    rc = wait(h, &evt, INFINITE_TIME);
    EXPECT_EQ(0, rc, "wait for reply");

    rc = tipc_recv_hdr_payload(h, rx_hdr, sizeof(rx_hdr), NULL, 0);
    EXPECT_EQ(ERR_BAD_LEN, rc, "tipc_recv_hdr_payload");

    /*
     * send/receive message into short buffer: should fail as bigger buffer
     * is expected
     */
    memset(tx_hdr, 0xaa, sizeof(tx_hdr));
    memset(tx_buf, 0x55, sizeof(tx_buf));
    memset(rx_hdr, 0xaa, sizeof(rx_hdr));
    memset(rx_buf, 0xaa, sizeof(rx_buf));

    rc = tipc_send2(h, tx_hdr, sizeof(tx_hdr), tx_buf, sizeof(tx_buf));
    EXPECT_EQ(sizeof(tx_hdr) + sizeof(tx_buf), (size_t)rc, "tipc_send_two");

    rc = wait(h, &evt, INFINITE_TIME);
    EXPECT_EQ(0, rc, "wait for reply");

    rc = tipc_recv_hdr_payload(h, rx_hdr, sizeof(rx_hdr), rx_buf,
                               sizeof(rx_buf) / 2);
    EXPECT_EQ(ERR_BAD_LEN, rc, "tipc_recv_hdr_payload");

    rc = close(h);
    EXPECT_EQ(0, rc, "close tipc_connect");
}

/****************************************************************************/

TEST(ipc, dup_is_different) {
    handle_t hset1;
    handle_t hset1_dup;

    hset1 = handle_set_create();
    EXPECT_GE_ZERO((int)hset1, "create handle set1");

    hset1_dup = dup(hset1);
    EXPECT_GE_ZERO((int)hset1_dup, "duplicate handle set1");

    ABORT_IF_NOT_OK(abort_test);

    EXPECT_NE(hset1, hset1_dup, "duplicate is different");

abort_test:
    close(hset1);
    close(hset1_dup);
}

/****************************************************************************/

static void kernel_wait_any_bug_workaround(void) {
    int ret;
    uevent_t event;

    /* HACK: clear stuck event on handleset */
    ret = wait_any(&event, 0);
    if (ret == 0) {
        TLOGI("retry ret %d, event handle %d, event 0x%x\n", ret, event.handle,
              event.event);
        ret = wait(event.handle, &event, 0);
        TLOGI("nested ret %d, event handle %d, event 0x%x\n", ret, event.handle,
              event.event);
    }
}

static bool run_test(struct unittest* test) {
    handle_base = (handle_t)USER_BASE_HANDLE;

    /*
     * HACK: We know a connection was made after _port_handle was created to
     * trigger the test, so we need to add two to _port_handle to get the first
     * free handle index.
     */
    first_free_handle_index = test->_port_handle + 2 - handle_base;
    TLOGI("first_free_handle_index: %d\n", first_free_handle_index);

    kernel_wait_any_bug_workaround();

    return RUN_ALL_TESTS();
}

/*
 *  Application entry point
 */
int main(void) {
    struct unittest ipc_unittest = {
            .port_name = SRV_PATH_BASE ".ctrl",
            .run_test = run_test,
    };
    struct unittest* unittest = &ipc_unittest;

    TLOGD("Welcome to IPC unittest!!!\n");

    return unittest_main(&unittest, 1);
}
