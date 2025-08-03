/*
 * Copyright (c) 2022, Google Inc. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#include <err.h>
#include <ktipc_test.h>
#include <lib/ktipc/ktipc.h>
#include <lib/unittest/unittest.h>
#include <lk/init.h>

#define TIMEOUT_MSEC 10000

#define NUM_TEST_CONNECTIONS 100
#define NUM_TEST_MESSAGES 50

static char test_pattern[] = "abcdefghijklmnopqrstuvwxyz!";

static void send_cmd(struct handle* chan,
                     enum ktipc_test_cmd cmd,
                     char* buf,
                     size_t len) {
    struct ktipc_test_req req = {
            .cmd = cmd,
    };
    int rc = ktipc_send(chan, &req, sizeof(req), buf, len);
    ASSERT_GE(rc, 0);
    ASSERT_EQ(sizeof(req) + len, (size_t)rc);

test_abort:;
}

static void wait_for_hup(struct handle* chan) {
    int rc;
    uint32_t event;

    rc = handle_wait(chan, &event, TIMEOUT_MSEC);
    ASSERT_EQ(NO_ERROR, rc);
    ASSERT_NE(0, (event & IPC_HANDLE_POLL_READY));

    if (!(event & IPC_HANDLE_POLL_HUP)) {
        /*
         * Service hasn't closed the channel yet, wait again.
         * We should only get READY once, so one extra wait should do it.
         */
        rc = handle_wait(chan, &event, TIMEOUT_MSEC);
        ASSERT_EQ(NO_ERROR, rc);
    }

    ASSERT_NE(0, (event & IPC_HANDLE_POLL_HUP));

test_abort:;
}

/*
 * Test that the service closes the channel on its end in case on_connect()
 * returns an error. The client should see this as a HUP.
 */
TEST(ktipctest, connecterr) {
    int rc;
    struct handle* chan = NULL;

    rc = ipc_port_connect_async(&kernel_uuid, KTIPC_TEST_SRV_PORT ".connecterr",
                                IPC_PORT_PATH_MAX, IPC_CONNECT_WAIT_FOR_PORT,
                                &chan);
    ASSERT_EQ(NO_ERROR, rc);

    wait_for_hup(chan);
    ASSERT_EQ(false, HasFailure());

test_abort:
    if (chan) {
        handle_decref(chan);
    }
}

/* Test connections to ports blocked by UUID */
TEST(ktipctest, blockedport) {
    int rc;
    struct handle* chan = NULL;

    /*
     * ipc_port_connect_async() returns NO_ERROR for rejected connections,
     * even with IPC_CONNECT_WAIT_FOR_PORT. We check for a HUP below instead.
     */
    rc = ipc_port_connect_async(&kernel_uuid, KTIPC_TEST_SRV_PORT ".blocked",
                                IPC_PORT_PATH_MAX, IPC_CONNECT_WAIT_FOR_PORT,
                                &chan);
    ASSERT_EQ(NO_ERROR, rc);

    wait_for_hup(chan);
    ASSERT_EQ(false, HasFailure());

test_abort:
    if (chan) {
        handle_decref(chan);
    }
}

TEST(ktipctest, echo) {
    int rc;
    struct handle* chan[NUM_TEST_CONNECTIONS] = {
            [0 ...(NUM_TEST_CONNECTIONS - 1)] = NULL,
    };
    uint32_t event;

    for (size_t i = 0; i < NUM_TEST_CONNECTIONS; i++) {
        rc = ipc_port_connect_async(&kernel_uuid, KTIPC_TEST_SRV_PORT,
                                    IPC_PORT_PATH_MAX,
                                    IPC_CONNECT_WAIT_FOR_PORT, &chan[i]);
        ASSERT_EQ(NO_ERROR, rc);

        rc = handle_wait(chan[i], &event, TIMEOUT_MSEC);
        ASSERT_EQ(NO_ERROR, rc);
        ASSERT_NE(0, (event & IPC_HANDLE_POLL_READY));
    }

    const size_t len = countof(test_pattern);
    for (size_t i = 0; i < NUM_TEST_CONNECTIONS; i++) {
        for (size_t j = 0; j < NUM_TEST_MESSAGES; j++) {
            test_pattern[i % len] = (i + j) & 0xff;

            send_cmd(chan[i], KTIPC_TEST_CMD_ECHO, test_pattern, len);
            ASSERT_EQ(false, HasFailure());

            rc = handle_wait(chan[i], &event, TIMEOUT_MSEC);
            ASSERT_EQ(NO_ERROR, rc);
            ASSERT_NE(0, (event & IPC_HANDLE_POLL_MSG));

            char buf[countof(test_pattern)];
            rc = ktipc_recv(chan[i], len, buf, len);
            ASSERT_EQ(len, rc);
            ASSERT_EQ(0, memcmp(buf, test_pattern, len));
        }
    }

test_abort:
    for (size_t i = 0; i < NUM_TEST_CONNECTIONS; i++) {
        if (chan[i]) {
            handle_decref(chan[i]);
        }
    }
}

/* Test that sends and receives 8 buffers at once */
TEST(ktipctest, echo8) {
    int rc;
    struct handle* chan = NULL;
    uint32_t event;

    rc = ipc_port_connect_async(&kernel_uuid, KTIPC_TEST_SRV_PORT,
                                IPC_PORT_PATH_MAX, IPC_CONNECT_WAIT_FOR_PORT,
                                &chan);
    ASSERT_EQ(NO_ERROR, rc);

    rc = handle_wait(chan, &event, TIMEOUT_MSEC);
    ASSERT_EQ(NO_ERROR, rc);
    ASSERT_NE(0, (event & IPC_HANDLE_POLL_READY));

    /*
     * Send different parts of the test pattern from various offsets as 8
     * different buffers so we pass the maximum number of arguments to
     * ktipc_send().
     */
    const size_t len = 4 * 7;
    struct ktipc_test_req req = {
            .cmd = KTIPC_TEST_CMD_ECHO,
    };
    rc = ktipc_send(chan, &req, sizeof(req), test_pattern, 4, test_pattern + 4,
                    4, test_pattern, 2, test_pattern + 2, 4, test_pattern + 6,
                    6, test_pattern, 6, test_pattern + 8, 2);
    ASSERT_GE(rc, 0);
    ASSERT_EQ(sizeof(req) + len, (size_t)rc);

    rc = handle_wait(chan, &event, TIMEOUT_MSEC);
    ASSERT_EQ(NO_ERROR, rc);
    ASSERT_NE(0, (event & IPC_HANDLE_POLL_MSG));

    /*
     * Read the message back into different buffers that add to the same total
     * length. We split the last 4-byte buffer into two smaller ones so that
     * we still get 8 buffers in total, so we test ktipc_recv() with the maximum
     * amount of allowed arguments.
     */
    char buf4[6][4];
    char buf2[2][2];
    rc = ktipc_recv(chan, len, buf4[0], 4, buf4[1], 4, buf4[2], 4, buf4[3], 4,
                    buf4[4], 4, buf4[5], 4, buf2[0], 2, buf2[1], 2);
    ASSERT_EQ(len, rc);
    ASSERT_EQ(0, memcmp(buf4[0], test_pattern + 0, 4));
    ASSERT_EQ(0, memcmp(buf4[1], test_pattern + 4, 4));
    ASSERT_EQ(0, memcmp(buf4[2], test_pattern + 0, 4));
    ASSERT_EQ(0, memcmp(buf4[3], test_pattern + 4, 4));
    ASSERT_EQ(0, memcmp(buf4[4], test_pattern + 8, 4));
    ASSERT_EQ(0, memcmp(buf4[5], test_pattern + 0, 4));
    ASSERT_EQ(0, memcmp(buf2[0], test_pattern + 4, 2));
    ASSERT_EQ(0, memcmp(buf2[1], test_pattern + 8, 2));

test_abort:
    if (chan) {
        handle_decref(chan);
    }
}

TEST(ktipctest, close) {
    int rc;
    struct handle* chan = NULL;
    uint32_t start_close_counter, end_close_counter;
    uint32_t event;

    rc = ipc_port_connect_async(&kernel_uuid, KTIPC_TEST_SRV_PORT,
                                IPC_PORT_PATH_MAX, IPC_CONNECT_WAIT_FOR_PORT,
                                &chan);
    ASSERT_EQ(NO_ERROR, rc);

    rc = handle_wait(chan, &event, TIMEOUT_MSEC);
    ASSERT_EQ(NO_ERROR, rc);
    ASSERT_NE(0, (event & IPC_HANDLE_POLL_READY));

    send_cmd(chan, KTIPC_TEST_CMD_READ_CLOSE_COUNTER, NULL, 0);
    ASSERT_EQ(false, HasFailure());

    rc = handle_wait(chan, &event, TIMEOUT_MSEC);
    ASSERT_EQ(NO_ERROR, rc);
    ASSERT_NE(0, (event & IPC_HANDLE_POLL_MSG));

    rc = ktipc_recv(chan, sizeof(start_close_counter), &start_close_counter,
                    sizeof(start_close_counter));
    ASSERT_EQ(sizeof(start_close_counter), rc);

    /* Close the first channel */
    handle_decref(chan);
    chan = NULL;

    /* Now open a new channel and ask the server to close it */
    rc = ipc_port_connect_async(&kernel_uuid, KTIPC_TEST_SRV_PORT,
                                IPC_PORT_PATH_MAX, IPC_CONNECT_WAIT_FOR_PORT,
                                &chan);
    ASSERT_EQ(NO_ERROR, rc);

    rc = handle_wait(chan, &event, TIMEOUT_MSEC);
    ASSERT_EQ(NO_ERROR, rc);
    ASSERT_NE(0, (event & IPC_HANDLE_POLL_READY));

    send_cmd(chan, KTIPC_TEST_CMD_CLOSE, NULL, 0);
    ASSERT_EQ(false, HasFailure());

    rc = handle_wait(chan, &event, TIMEOUT_MSEC);
    ASSERT_EQ(NO_ERROR, rc);
    ASSERT_NE(0, (event & IPC_HANDLE_POLL_HUP));

    handle_decref(chan);
    chan = NULL;

    /* Read the close counter again; it should be 2 higher than the previous */
    rc = ipc_port_connect_async(&kernel_uuid, KTIPC_TEST_SRV_PORT,
                                IPC_PORT_PATH_MAX, IPC_CONNECT_WAIT_FOR_PORT,
                                &chan);
    ASSERT_EQ(NO_ERROR, rc);

    rc = handle_wait(chan, &event, TIMEOUT_MSEC);
    ASSERT_EQ(NO_ERROR, rc);
    ASSERT_NE(0, (event & IPC_HANDLE_POLL_READY));

    send_cmd(chan, KTIPC_TEST_CMD_READ_CLOSE_COUNTER, NULL, 0);
    ASSERT_EQ(false, HasFailure());

    rc = handle_wait(chan, &event, TIMEOUT_MSEC);
    ASSERT_EQ(NO_ERROR, rc);
    ASSERT_NE(0, (event & IPC_HANDLE_POLL_MSG));

    rc = ktipc_recv(chan, sizeof(end_close_counter), &end_close_counter,
                    sizeof(end_close_counter));
    ASSERT_EQ(sizeof(end_close_counter), rc);
    ASSERT_EQ(start_close_counter + 2, end_close_counter);

test_abort:
    if (chan) {
        handle_decref(chan);
    }
}

TEST(ktipctest, blockedsend) {
    int rc;
    struct handle* chan = NULL;
    uint32_t event;

    rc = ipc_port_connect_async(&kernel_uuid, KTIPC_TEST_SRV_PORT,
                                IPC_PORT_PATH_MAX, IPC_CONNECT_WAIT_FOR_PORT,
                                &chan);
    ASSERT_EQ(NO_ERROR, rc);

    rc = handle_wait(chan, &event, TIMEOUT_MSEC);
    ASSERT_EQ(NO_ERROR, rc);
    ASSERT_NE(0, (event & IPC_HANDLE_POLL_READY));

    /* Send the message twice, second one should get queued */
    const size_t len = countof(test_pattern);
    send_cmd(chan, KTIPC_TEST_CMD_ECHO, test_pattern, len);
    ASSERT_EQ(false, HasFailure());

    rc = handle_wait(chan, &event, TIMEOUT_MSEC);
    ASSERT_EQ(NO_ERROR, rc);
    ASSERT_NE(0, (event & IPC_HANDLE_POLL_MSG));

    /*
     * Send the second message before reading the first reply,
     * should force the service to queue
     */
    send_cmd(chan, KTIPC_TEST_CMD_ECHO, test_pattern, len);
    ASSERT_EQ(false, HasFailure());

    /* Sleep to give the service a chance to run */
    thread_sleep(10);

    char buf[countof(test_pattern)];
    rc = ktipc_recv(chan, len, buf, len);
    ASSERT_EQ(len, rc);
    ASSERT_EQ(0, memcmp(buf, test_pattern, len));

    rc = handle_wait(chan, &event, TIMEOUT_MSEC);
    ASSERT_EQ(NO_ERROR, rc);
    ASSERT_NE(0, (event & IPC_HANDLE_POLL_MSG));

    rc = ktipc_recv(chan, len, buf, len);
    ASSERT_EQ(len, rc);
    ASSERT_EQ(0, memcmp(buf, test_pattern, len));

test_abort:
    if (chan) {
        handle_decref(chan);
    }
}

PORT_TEST(ktipctest, "com.android.kernel.ktipc.test");
