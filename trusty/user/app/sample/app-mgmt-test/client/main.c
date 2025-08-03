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

#include <app_mgmt_port_consts.h>
#include <app_mgmt_test.h>
#include <assert.h>
#include <interface/apploader/apploader.h>
#include <lib/system_state/system_state.h>
#include <lib/tipc/tipc.h>
#include <lib/unittest/unittest.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <trusty/memref.h>
#include <trusty/time.h>
#include <trusty_ipc.h>
#include <trusty_unittest.h>
#include <uapi/err.h>

#define TLOG_TAG "app-mgmt-test-client"
#define PORT_BASE "com.android.appmgmt-unittest.appmngr"

/*
 * These are expected to occur/elapse in passing test cases and as such there is
 * a trade off between the degree of confidence provided by the tests and the
 * runtime of the tests.
 */
#define EXPECTED_TIMEOUT_MS 500
#define UNEXPECTED_TIMEOUT_MS 60000
#define WAIT_FOR_APP_SLEEP_NS 500000000

/*
 * These tests makes use of one client TA (this file), 7 test server TAs, and
 * one malformed (unsigned) TA.
 *
 * boot-start-srv:
 *  - Starts at boot
 *  - Creates BOOT_START_PORT
 *  - Never exits
 *  - Does not restart at exit
 *
 * dev-only-srv:
 *  - Starts at boot
 *  - Tests that this app is only loadable if the unlocked system state flag is
 *    set and that attempts to load it fails verification otherwise.
 *  - Does not restart at exit
 *
 * never-start-srv:
 *  - Should never be started
 *  - Creates NEVER_START_PORT (if ever started i.e. failure case)
 *  - Exits after receiving a connection
 *  - Restarts after exiting
 *
 * restart-srv:
 *  - Starts at boot
 *  - Creates RESTART_PORT
 *  - Exits after receiving a connection
 *  - Restarts after exiting
 *
 * port-start-srv:
 *  - Doesn't start at boot
 *  - Starts on connections to START_PORT
 *  - Creates START_PORT, CTRL_PORT and SHUTDOWN_PORT
 *  - Exits after receiving a CMD_EXIT command on START_PORT or a connection on
 *    SHUTDOWN_PORT
 *  - Does not restart at exit
 *
 * port-start-fail-srv:
 *  - Doesn't start at boot
 *  - Fails to start because it requests almost 4GB heap and stack
 *  - Never runs and thus never exits
 *
 * port-waiter-srv:
 *  - Starts at boot
 *  - Waits on the port of another loadable app that has yet to load
 *  - Starts the second app
 *  - Never exists
 *  - Does not restart at exit
 *
 * unsigned:
 *  - Tests that loading of malformed app leads to verification failure
 */

static bool port_start_srv_running(void) {
    int rc;

    trusty_nanosleep(0, 0, WAIT_FOR_APP_SLEEP_NS);
    rc = connect(CTRL_PORT, IPC_CONNECT_ASYNC);
    close((handle_t)rc);
    return rc >= 0;
}

static void chan_send_cmd(handle_t chan, uint8_t cmd) {
    uint8_t rsp;
    uevent_t uevt;

    if (HasFailure())
        return;

    ASSERT_EQ(sizeof(cmd), tipc_send1(chan, &cmd, sizeof(cmd)));
    ASSERT_EQ(NO_ERROR, wait(chan, &uevt, INFINITE_TIME));
    ASSERT_NE(0, uevt.event & IPC_HANDLE_POLL_MSG);
    ASSERT_EQ(sizeof(rsp), tipc_recv1(chan, sizeof(rsp), &rsp, sizeof(rsp)));
    ASSERT_EQ(RSP_OK, rsp);

test_abort:
    return;
}

typedef enum {
    /* Accepted on connect and served by port-start-srv */
    MAIN_CHAN,

    /* Not accepted on connect. Put on pending list when established */
    PEND_CHAN,

    /* Not accepted on connect. Put on waiting list when established */
    WAIT_CHAN,

    CHAN_COUNT,
} chan_idx_t;

typedef struct {
    handle_t chans[CHAN_COUNT];
} AppMgrPortStart_t;

static void send_cmd(AppMgrPortStart_t* state, chan_idx_t idx, uint8_t cmd) {
    assert(idx < CHAN_COUNT);

    if (HasFailure())
        return;

    chan_send_cmd(state->chans[idx], cmd);

test_abort:
    return;
}

static void establish_unhandled_channel(AppMgrPortStart_t* state,
                                        chan_idx_t idx) {
    int rc;
    uevent_t uevt;
    handle_t chan = INVALID_IPC_HANDLE;

    assert(idx < CHAN_COUNT);

    if (HasFailure())
        return;

    rc = connect(START_PORT, IPC_CONNECT_ASYNC);
    ASSERT_GE(rc, 0);
    chan = (handle_t)rc;

    /* Make sure port-start-srv does not accept the connection */
    ASSERT_EQ(ERR_TIMED_OUT, wait(chan, &uevt, EXPECTED_TIMEOUT_MS));

    state->chans[idx] = chan;

test_abort:
    return;
}

static void close_channel(AppMgrPortStart_t* state, chan_idx_t idx) {
    assert(idx < CHAN_COUNT);

    if (HasFailure())
        return;

    close(state->chans[idx]);
    state->chans[idx] = INVALID_IPC_HANDLE;
}

static void send_exit(AppMgrPortStart_t* state, chan_idx_t idx) {
    assert(idx < CHAN_COUNT);

    if (HasFailure())
        return;

    send_cmd(state, idx, CMD_EXIT);
    close_channel(state, idx);
}

static void wait_and_exit(AppMgrPortStart_t* state, chan_idx_t idx) {
    uevent_t uevt;

    assert(idx < CHAN_COUNT);

    if (HasFailure())
        return;

    ASSERT_EQ(NO_ERROR, wait(state->chans[idx], &uevt, INFINITE_TIME));
    ASSERT_NE(0, IPC_HANDLE_POLL_READY & uevt.event);

    send_exit(state, idx);

test_abort:
    return;
}

static void send_apploader_request(handle_t channel,
                                   uint32_t cmd,
                                   void* req,
                                   size_t req_size,
                                   handle_t handle) {
    if (HasFailure())
        return;

    struct apploader_header hdr = {
            .cmd = cmd,
    };
    struct iovec iov[2] = {{&hdr, sizeof(hdr)}, {req, req_size}};
    ipc_msg_t msg = {
            .iov = iov,
            .num_iov = (req && req_size) ? 2 : 1,
            .handles = &handle,
            .num_handles = (handle != INVALID_IPC_HANDLE) ? 1 : 0,
    };

    int rc;
    rc = send_msg(channel, &msg);
    ASSERT_EQ(rc, (ssize_t)sizeof(hdr) + (ssize_t)req_size);

test_abort:;
}

static uint32_t read_apploader_response(handle_t channel,
                                        uint32_t cmd,
                                        handle_t* handles,
                                        size_t num_handles,
                                        ipc_msg_info_t* msg_inf) {
    int rc;
    struct apploader_resp resp;
    ASSERT_EQ(msg_inf->len, sizeof(resp));

    struct iovec iov = {
            .iov_base = (void*)&resp,
            .iov_len = sizeof(resp),
    };
    ipc_msg_t msg = {
            .iov = &iov,
            .num_iov = 1,
            .handles = handles,
            .num_handles = num_handles,
    };
    rc = read_msg(channel, msg_inf->id, 0, &msg);
    ASSERT_EQ(rc, (ssize_t)sizeof(resp));
    ASSERT_EQ(resp.hdr.cmd, cmd | APPLOADER_RESP_BIT);

    return resp.error;

test_abort:
    return 0;
}

static uint32_t recv_apploader_response(handle_t channel,
                                        uint32_t cmd,
                                        handle_t* handles,
                                        size_t num_handles) {
    int rc;
    struct uevent event;
    ipc_msg_info_t msg_inf;

    if (HasFailure())
        return 0;

    rc = wait(channel, &event, INFINITE_TIME);
    ASSERT_EQ(rc, NO_ERROR);
    ASSERT_NE(event.event & IPC_HANDLE_POLL_MSG, 0);

    rc = get_msg(channel, &msg_inf);
    ASSERT_EQ(rc, NO_ERROR);

    rc = read_apploader_response(channel, cmd, handles, num_handles, &msg_inf);
    put_msg(channel, msg_inf.id);
    return rc;

test_abort:
    return 0;
}

static uint32_t load_app(char* app_begin, char* app_end) {
    int rc;
    handle_t chan = INVALID_IPC_HANDLE;
    handle_t handle = INVALID_IPC_HANDLE;

    rc = connect(APPLOADER_PORT, IPC_CONNECT_WAIT_FOR_PORT);
    ASSERT_GT(rc, 0);
    chan = (handle_t)rc;

    uint64_t page_size = getauxval(AT_PAGESZ);
    ptrdiff_t app_size = app_end - app_begin;
    size_t aligned_app_size = round_up(app_size, page_size);

    handle = memref_create(app_begin, aligned_app_size, MMAP_FLAG_PROT_READ);
    ASSERT_GT(handle, 0);

    struct apploader_load_app_req req = {
            .package_size = app_size,
    };
    send_apploader_request(chan, APPLOADER_CMD_LOAD_APPLICATION, &req,
                           sizeof(req), handle);
    ASSERT_EQ(false, HasFailure());

    uint32_t error;
    error = recv_apploader_response(chan, APPLOADER_CMD_LOAD_APPLICATION, NULL,
                                    0);
    ASSERT_EQ(false, HasFailure());

    /* Wait for a bit for the app to start properly */
    if (error == APPLOADER_NO_ERROR) {
        trusty_nanosleep(0, 0, WAIT_FOR_APP_SLEEP_NS);
    }

    close(handle);
    close(chan);

    return error;

test_abort:
    if (handle > 0) {
        close(handle);
    }
    close(chan);
    return 0;
}

extern char boot_start_app_begin[], boot_start_app_end[];
extern char never_start_app_begin[], never_start_app_end[];
extern char port_start_app_begin[], port_start_app_end[];
extern char port_start_fail_app_begin[], port_start_fail_app_end[];
extern char restart_app_begin[], restart_app_end[];
extern char port_waiter_app_begin[], port_waiter_app_end[];
extern char unsigned_app_begin[], unsigned_app_end[];
extern char dev_only_app_begin[], dev_only_app_end[];

/*
 * Loading an application the a second time should return
 * APPLOADER_ERR_ALREADY_EXISTS. This test should be the first
 * in the file to load boot-start-srv so we test both proper
 * application loading and the second load attempt.
 */
TEST(AppMgrBoot, DoubleLoad) {
    uint32_t error;

    error = load_app(boot_start_app_begin, boot_start_app_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);
    if (error == APPLOADER_ERR_ALREADY_EXISTS) {
        trusty_unittest_printf("[  WARNING ] boot-start-srv already loaded\n");
    }

    error = load_app(boot_start_app_begin, boot_start_app_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(error, APPLOADER_ERR_ALREADY_EXISTS);

test_abort:;
}

/*
 * Start an app that waits on the port of another loadable app that has not
 * been loaded yet, then start the second app. The kernel should correctly wake
 * up the first app after loading the second.
 */
TEST(AppMgrWaitForPort, WaitConnectForPort) {
    int rc;
    handle_t chan = INVALID_IPC_HANDLE;
    struct uevent uevt;
    uint8_t rsp;

    /*
     * port-start-srv should not be running
     * TODO: unload it ourselves when app unloading is supported; until then,
     * we only allow this test to be run once per boot, and it needs to be the
     * first one in the file.
     */
    static bool skip = false;
    if (skip) {
        trusty_unittest_printf("[  SKIPPED ]\n");
        return;
    }
    skip = true;
    ASSERT_EQ(false, port_start_srv_running());

    /* Load port-waiter-srv */
    uint32_t load_error = load_app(port_waiter_app_begin, port_waiter_app_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, load_error == APPLOADER_NO_ERROR ||
                            load_error == APPLOADER_ERR_ALREADY_EXISTS);

    /* Load port-start-srv now, which should wake up port-waiter-srv */
    load_error = load_app(port_start_app_begin, port_start_app_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(load_error, APPLOADER_NO_ERROR);

    /* Connect to port-waiter-srv */
    rc = connect(PORT_WAITER_PORT,
                 IPC_CONNECT_ASYNC | IPC_CONNECT_WAIT_FOR_PORT);
    ASSERT_GE(rc, 0);
    chan = (handle_t)rc;

    ASSERT_EQ(NO_ERROR, wait(chan, &uevt, UNEXPECTED_TIMEOUT_MS));
    ASSERT_NE(0, uevt.event & IPC_HANDLE_POLL_READY);
    if (!(uevt.event & IPC_HANDLE_POLL_MSG)) {
        ASSERT_EQ(NO_ERROR, wait(chan, &uevt, INFINITE_TIME));
        ASSERT_NE(0, uevt.event & IPC_HANDLE_POLL_MSG);
    }
    ASSERT_EQ(sizeof(rsp), tipc_recv1(chan, sizeof(rsp), &rsp, sizeof(rsp)));
    ASSERT_EQ(RSP_OK, rsp);

test_abort:
    close(chan);
}

static void AppMgrPortStart_SetUp(AppMgrPortStart_t* state) {
    int rc;
    uevent_t uevt;
    handle_t chan;

    uint32_t error = load_app(port_start_app_begin, port_start_app_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);

    for (size_t i = 0; i < CHAN_COUNT; i++) {
        state->chans[i] = INVALID_IPC_HANDLE;
    }

    /* Shutdown port-start-srv in case it is running from a previous test */
    rc = connect(SHUTDOWN_PORT, IPC_CONNECT_ASYNC);
    if (rc > 0) {
        /* SHUTDOWN_PORT exists so the srv was running. Wait for it to exit */
        chan = (handle_t)rc;
        rc = wait(chan, &uevt, INFINITE_TIME);
        close(chan);
        ASSERT_GE(rc, 0);
        ASSERT_NE(0, uevt.event & IPC_HANDLE_POLL_HUP);
    }

    /* port-start-srv should not be running */
    ASSERT_EQ(false, port_start_srv_running());

    /* Start and connect to port-start-srv */
    rc = connect(START_PORT, 0);
    ASSERT_GE(rc, 0);

    state->chans[MAIN_CHAN] = (handle_t)rc;

test_abort:
    return;
}

static void AppMgrPortStart_TearDown(AppMgrPortStart_t* state) {
    ASSERT_EQ(false, HasFailure());

    /* port-start-srv should not be running at the end of a test */
    ASSERT_EQ(false, port_start_srv_running());

    for (size_t i = 0; i < CHAN_COUNT; i++) {
        ASSERT_EQ(INVALID_IPC_HANDLE, state->chans[i]);
    }

test_abort:
    for (size_t i = 0; i < CHAN_COUNT; i++) {
        close(state->chans[i]);
    }
}

/* Apps with deferred start should not start at boot */
TEST(AppMgrBoot, BootStartNegative) {
    int rc;

    uint32_t error = load_app(never_start_app_begin, never_start_app_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);

    /* never-start-srv should not be running */
    rc = connect(NEVER_START_PORT, IPC_CONNECT_ASYNC);
    EXPECT_LT(rc, 0);
    close((handle_t)rc);

test_abort:;
}

/* Apps without deferred should start at boot */
TEST(AppMgrBoot, BootStartPositive) {
    int rc;

    uint32_t error = load_app(boot_start_app_begin, boot_start_app_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);

    /* boot-start-srv should be running from boot */
    rc = connect(BOOT_START_PORT, IPC_CONNECT_ASYNC);
    EXPECT_GE(rc, 0);
    close((handle_t)rc);

test_abort:;
}

/* Apps with automatic restart should restart after exiting */
TEST(AppMgrRestart, AppRestartPositive) {
    int rc;
    uevent_t uevt;
    handle_t chan = INVALID_IPC_HANDLE;

    uint32_t error = load_app(restart_app_begin, restart_app_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);

    /* restart-srv should be running from boot or a previous restart */
    rc = connect(RESTART_PORT, IPC_CONNECT_ASYNC | IPC_CONNECT_WAIT_FOR_PORT);
    ASSERT_GE(rc, 0);

    /* Wait for restart-srv to initiate shutdown */
    chan = (handle_t)rc;
    ASSERT_EQ(NO_ERROR, wait(chan, &uevt, INFINITE_TIME));
    ASSERT_NE(0, IPC_HANDLE_POLL_HUP & uevt.event);
    close(chan);

    /* restart-srv should eventually restart */
    rc = connect(RESTART_PORT, IPC_CONNECT_ASYNC | IPC_CONNECT_WAIT_FOR_PORT);
    ASSERT_GE(rc, 0);
    chan = (handle_t)rc;

test_abort:
    close(chan);
}

/*
 * Apps without automatic restart should not restart after exiting
 * Start ports should start an app on connection
 */
TEST(AppMgrRestart, AppRestartNegativePortStartPositive) {
    int rc;
    handle_t chan = INVALID_IPC_HANDLE;

    uint32_t error = load_app(port_start_app_begin, port_start_app_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);

    /* Start and connect to port-start-srv */
    rc = connect(START_PORT, 0);
    ASSERT_GE(rc, 0);
    chan = (handle_t)rc;

    /* Shutdown port-start-srv */
    chan_send_cmd(chan, CMD_EXIT);
    ASSERT_EQ(false, HasFailure());

    /* port-start-srv should not restart */
    ASSERT_EQ(false, port_start_srv_running());

test_abort:
    close(chan);
}

/* Regular ports should not start an app on connection */
TEST(AppMgrPortStartFail, PortStartFail) {
    int rc;
    handle_t chan = INVALID_IPC_HANDLE;
    uevent_t uevt;

    uint32_t error =
            load_app(port_start_fail_app_begin, port_start_fail_app_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);

    /*
     * A connection to START_FAIL_PORT should fail to start the
     * port-start-fail-srv app, but it will first create a handle we can wait on
     */
    rc = connect(START_FAIL_PORT, IPC_CONNECT_ASYNC);
    ASSERT_GE(rc, 0);

    /* Wait for kernel to shut down channel after failing to start app */
    chan = (handle_t)rc;
    ASSERT_EQ(NO_ERROR, wait(chan, &uevt, UNEXPECTED_TIMEOUT_MS));
    ASSERT_NE(0, IPC_HANDLE_POLL_HUP & uevt.event);
    close(chan);

    /* Try again to make sure we get ERR_CANCELLED the second time */
    rc = connect(START_FAIL_PORT, IPC_CONNECT_ASYNC);
    ASSERT_EQ(ERR_CANCELLED, rc);

test_abort:;
}

/* Regular ports should not start an app on connection */
TEST(AppMgrPortStartNegative, PortStartNegative) {
    int rc;

    uint32_t error = load_app(port_start_app_begin, port_start_app_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);

    /* A connection to CTRL_PORT should not start port-start-srv */
    rc = connect(CTRL_PORT, IPC_CONNECT_ASYNC);
    EXPECT_LT(rc, 0);
    close((handle_t)rc);

test_abort:;
}

/* Start ports with closed pending connections should not start an app */
TEST_F(AppMgrPortStart, PortStartPendingNegative) {
    /* Create a pending connection */
    establish_unhandled_channel(_state, PEND_CHAN);

    /* Close the pending connection */
    close_channel(_state, PEND_CHAN);

    /* Close the main channel and shutdown port-start-srv */
    send_exit(_state, MAIN_CHAN);

test_abort:;
}

/* Start ports with pending connections should start an app */
TEST_F(AppMgrPortStart, PortStartPendingPositive) {
    /* Create a pending connection */
    establish_unhandled_channel(_state, PEND_CHAN);

    /* Close the main channel and shutdown port-start-srv */
    send_exit(_state, MAIN_CHAN);

    /*
     * Wait for port-start-srv to restart due to the pending connection and then
     * shut it down
     */
    wait_and_exit(_state, PEND_CHAN);
}

/* Closed connections waiting for a start port should not start an app */
TEST_F(AppMgrPortStart, PortStartWaitingNegative) {
    /* Make port-start-srv close START_PORT */
    send_cmd(_state, MAIN_CHAN, CMD_CLOSE_PORT);

    /* Create a waiting connection */
    establish_unhandled_channel(_state, WAIT_CHAN);

    /* Close the waiting connection */
    close_channel(_state, WAIT_CHAN);

    /* Close the main channel and shutdown port-start-srv */
    send_exit(_state, MAIN_CHAN);
}

/* Connections waiting for a start port should start an app */
TEST_F(AppMgrPortStart, PortStartWaitingPositive) {
    /* Make port-start-srv close START_PORT */
    send_cmd(_state, MAIN_CHAN, CMD_CLOSE_PORT);

    /* Create a waiting connection */
    establish_unhandled_channel(_state, WAIT_CHAN);

    /* Close the main channel and shutdown port-start-srv */
    send_exit(_state, MAIN_CHAN);

    /*
     * Wait for port-start-srv to restart due to the waiting connection and then
     * shut it down
     */
    wait_and_exit(_state, WAIT_CHAN);
}

/*
 * Closed waiting connections that were pending on a start port should not start
 * an app
 */
TEST_F(AppMgrPortStart, PortStartPendingToWaitingNegative) {
    /* Create a pending connection */
    establish_unhandled_channel(_state, PEND_CHAN);

    /*
     * Make port-start-srv close START_PORT (the pending connection becomes
     * waiting)
     */
    send_cmd(_state, MAIN_CHAN, CMD_CLOSE_PORT);

    /* Close the waiting connection */
    close_channel(_state, PEND_CHAN);

    /* Close the main channel and shutdown port-start-srv */
    send_exit(_state, MAIN_CHAN);
}

/*
 * Waiting connections that were pending on a start port should start an app
 */
TEST_F(AppMgrPortStart, PortStartPendingToWaitingPositive) {
    /* Create a pending connection */
    establish_unhandled_channel(_state, PEND_CHAN);

    /*
     * Make port-start-srv close START_PORT (the pending connection becomes
     * waiting)
     */
    send_cmd(_state, MAIN_CHAN, CMD_CLOSE_PORT);

    /* Close the main channel and shutdown port-start-srv */
    send_exit(_state, MAIN_CHAN);

    /*
     * Wait for port-start-srv to restart due to the waiting connection and then
     * shut it down
     */
    wait_and_exit(_state, PEND_CHAN);
}

/*
 * Start ports with closed pending connections that were waiting for the port
 * should not start an app
 */
TEST_F(AppMgrPortStart, PortStartWaitingToPendingNegative) {
    /* Make port-start-srv close START_PORT */
    send_cmd(_state, MAIN_CHAN, CMD_CLOSE_PORT);

    /* Create a waiting connection */
    establish_unhandled_channel(_state, WAIT_CHAN);

    /*
     * Make port-start-srv open START_PORT (the waiting connection becomes
     * pending)
     */
    send_cmd(_state, MAIN_CHAN, CMD_OPEN_PORT);

    /* Close the pending connection */
    close_channel(_state, WAIT_CHAN);

    /* Close the main channel and shutdown port-start-srv */
    send_exit(_state, MAIN_CHAN);
}

/*
 * Start ports with pending connections that were waiting for the port should
 * start an app
 */
TEST_F(AppMgrPortStart, PortStartWaitingToPendingPositive) {
    /* Make port-start-srv close START_PORT */
    send_cmd(_state, MAIN_CHAN, CMD_CLOSE_PORT);

    /* Create a waiting connection */
    establish_unhandled_channel(_state, WAIT_CHAN);

    /*
     * Make port-start-srv open START_PORT (the waiting connection becomes
     * pending)
     */
    send_cmd(_state, MAIN_CHAN, CMD_OPEN_PORT);

    /* Close the main channel and shutdown port-start-srv */
    send_exit(_state, MAIN_CHAN);

    /*
     * Wait for port-start-srv to restart due to the pending connection and then
     * shut it down
     */
    wait_and_exit(_state, WAIT_CHAN);
}

/*
 * Closed connections waiting for a start port with closed pending connections
 * should not start an app
 */
TEST_F(AppMgrPortStart, PortStartPendingWaitingNegative) {
    /* Create a pending connection */
    establish_unhandled_channel(_state, PEND_CHAN);

    /*
     * Make port-start-srv close START_PORT (the pending connection becomes
     * waiting)
     */
    send_cmd(_state, MAIN_CHAN, CMD_CLOSE_PORT);

    /* Create a waiting connection */
    establish_unhandled_channel(_state, WAIT_CHAN);

    /* Close the first waiting connection */
    close_channel(_state, PEND_CHAN);

    /* Close the second waiting connection */
    close_channel(_state, WAIT_CHAN);

    /* Close the main channel and shutdown port-start-srv */
    send_exit(_state, MAIN_CHAN);
}

/*
 * Connections waiting for a start port with pending connections should start
 * an app
 */
TEST_F(AppMgrPortStart, PortStartPendingWaitingPositive) {
    /* Create a pending connection */
    establish_unhandled_channel(_state, PEND_CHAN);

    /*
     * Make port-start-srv close START_PORT (the pending connection becomes
     * waiting)
     */
    send_cmd(_state, MAIN_CHAN, CMD_CLOSE_PORT);

    /* Create a waiting connection */
    establish_unhandled_channel(_state, WAIT_CHAN);

    /* Close the main channel and shutdown port-start-srv */
    send_exit(_state, MAIN_CHAN);

    /*
     * Wait for port-start-srv to restart due to the first waiting connection
     * and then shut it down
     */
    wait_and_exit(_state, PEND_CHAN);

    /*
     * wait for port-start-srv to restart due to the second waiting connection
     * and then shut it down
     */
    wait_and_exit(_state, WAIT_CHAN);
}

/*
 * Connections waiting for a start port with closed pending connections should
 * start an app
 */
TEST_F(AppMgrPortStart, PortStartPendingClosedWaitingPositive) {
    /* Create a pending connection */
    establish_unhandled_channel(_state, PEND_CHAN);

    /*
     * Make port-start-srv close START_PORT (the pending connection becomes
     * waiting)
     */
    send_cmd(_state, MAIN_CHAN, CMD_CLOSE_PORT);

    /* Create a waiting connection */
    establish_unhandled_channel(_state, WAIT_CHAN);

    /* Close the first waiting connection */
    close_channel(_state, PEND_CHAN);

    /* Close the main channel and shutdown port-start-srv */
    send_exit(_state, MAIN_CHAN);

    /*
     * wait for port-start-srv to restart due to the second waiting connection
     * and then shut it down
     */
    wait_and_exit(_state, WAIT_CHAN);
}

/*
 * Start ports with pending connections and with closed connections waiting for
 * the port should start an app
 */
TEST_F(AppMgrPortStart, PortStartPendingWaitingClosedPositive) {
    /* Create a pending connection */
    establish_unhandled_channel(_state, PEND_CHAN);

    /*
     * Make port-start-srv close START_PORT (the pending connection becomes
     * waiting)
     */
    send_cmd(_state, MAIN_CHAN, CMD_CLOSE_PORT);

    /* Create a waiting connection */
    establish_unhandled_channel(_state, WAIT_CHAN);

    /* Close the second waiting connection */
    close_channel(_state, WAIT_CHAN);

    /* Close the main channel and shutdown port-start-srv */
    send_exit(_state, MAIN_CHAN);

    /*
     * wait for port-start-srv to restart due to the first waiting connection
     * and then shut it down
     */
    wait_and_exit(_state, PEND_CHAN);
}

/*
 * Connecting to an app's start port as it terminating should always restart the
 * app
 */
TEST(AppMgrPortStartRacingTerminate, PortStartRacingTerminate) {
    int rc;
    handle_t chan = INVALID_IPC_HANDLE;
    uevent_t uevt;
    uint32_t error = load_app(port_start_app_begin, port_start_app_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                            error == APPLOADER_ERR_ALREADY_EXISTS);

    /* Connect to the start port triggering app start */
    rc = connect(START_PORT, IPC_CONNECT_ASYNC);
    ASSERT_GE(rc, 0);
    chan = (handle_t)rc;

    /* Make sure port-start-srv started successfully */
    ASSERT_EQ(0, wait(chan, &uevt, UNEXPECTED_TIMEOUT_MS));
    ASSERT_NE(0, IPC_HANDLE_POLL_READY & uevt.event);
    ASSERT_EQ(true, port_start_srv_running());

    /* Close the main channel and shutdown port-start-srv */
    chan_send_cmd(chan, CMD_EXIT);
    ASSERT_EQ(false, HasFailure());
    close(chan);

    /* Immediately reconnect to the start port triggering app start */
    rc = connect(START_PORT, IPC_CONNECT_ASYNC);
    ASSERT_GE(rc, 0);
    chan = (handle_t)rc;

    /* Make sure port-start-srv started successfully */
    ASSERT_EQ(0, wait(chan, &uevt, UNEXPECTED_TIMEOUT_MS));
    ASSERT_NE(0, IPC_HANDLE_POLL_READY & uevt.event);
    ASSERT_EQ(true, port_start_srv_running());

    /* Close the main channel and shutdown port-start-srv */
    chan_send_cmd(chan, CMD_EXIT);
    ASSERT_EQ(false, HasFailure());

test_abort:
    close(chan);
}

/* Test loading an unsigned app */
TEST(AppLoader, UnsignedApp) {
    uint32_t error = load_app(unsigned_app_begin, unsigned_app_end);
    ASSERT_EQ(false, HasFailure());
    ASSERT_EQ(APPLOADER_ERR_VERIFICATION_FAILED, error);

test_abort:;
}

/*
 * Dev apploader keys should only be available when the system state service
 * indicates that the system is in an unlocked state. This app is signed with
 * apploader slot 1, which is the dev key and therefore must only load in the
 * unlocked state.
 */
TEST(AppMgrBoot, UnlockedDevLoad) {
    uint32_t error = load_app(dev_only_app_begin, dev_only_app_end);
    ASSERT_EQ(false, HasFailure());

    if (system_state_app_loading_unlocked()) {
        ASSERT_EQ(true, error == APPLOADER_NO_ERROR ||
                                error == APPLOADER_ERR_ALREADY_EXISTS);
    } else {
        EXPECT_EQ(APPLOADER_ERR_VERIFICATION_FAILED, error);
    }

test_abort:;
}

static bool run_appmngr_tests(struct unittest* test) {
    return RUN_ALL_TESTS();
}

static bool run_appmngr_stress_tests(struct unittest* test) {
    while (RUN_ALL_TESTS()) {
    }

    return false;
}

int main(void) {
    static struct unittest appmgmt_unittests[] = {
            {
                    .port_name = PORT_BASE,
                    .run_test = run_appmngr_tests,
            },
            {
                    .port_name = PORT_BASE ".stress",
                    .run_test = run_appmngr_stress_tests,
            },
    };
    struct unittest* unittests[countof(appmgmt_unittests)];

    for (size_t i = 0; i < countof(appmgmt_unittests); i++)
        unittests[i] = &appmgmt_unittests[i];

    return unittest_main(unittests, countof(unittests));
}
