/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <lib/unittest/unittest.h>

#include <lib/trusty/handle_set.h>
#include <lib/trusty/ipc.h>
#include <lib/trusty/ipc_msg.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <uapi/err.h>

#define LOCAL_TRACE (0)

#include <lk/trace.h>

#define MAX_PORT_BUF_SIZE 4096 /* max size of per port buffer */

enum test_message_header {
    TEST_PASSED = 0,
    TEST_FAILED = 1,
    TEST_MESSAGE = 2,
    TEST_MESSAGE_HEADER_COUNT = 3,
};

static struct handle* ipc_printf_handle;
static struct mutex unittest_lock = MUTEX_INITIAL_VALUE(unittest_lock);
static struct handle* unittest_handle_set;
static thread_t* unittest_thread;

uint64_t get_current_time_ns(void) {
    return current_time_ns();
}

static int send_msg_wait(struct handle* handle, struct ipc_msg_kern* msg) {
    int ret;
    uint32_t event;

    ASSERT(is_mutex_held(&unittest_lock));

    ret = ipc_send_msg(handle, msg);
    if (ret != ERR_NOT_ENOUGH_BUFFER) {
        return ret;
    }

    ret = handle_wait(handle, &event, INFINITE_TIME);
    if (ret < 0) {
        return ret;
    }

    if (event & IPC_HANDLE_POLL_SEND_UNBLOCKED) {
        return ipc_send_msg(handle, msg);
    }

    if (event & IPC_HANDLE_POLL_MSG) {
        return ERR_BUSY;
    }

    if (event & IPC_HANDLE_POLL_HUP) {
        return ERR_CHANNEL_CLOSED;
    }

    return ret;
}

/**
 * unittest_printf - Print a message that gets sent back to the client
 * @fmt:    Format string.
 *
 * Print a message that gets sent back to the currently connected client. Should
 * only be called while the run_test function registered with unittest_add runs.
 * The length of the formatted string is limited to 254 bytes.
 *
 * Return: Formatted string length or (negative) error code.
 */
int unittest_printf(const char* fmt, ...) {
    char buf[256];
    struct iovec_kern tx_iov = {buf, 1};
    struct ipc_msg_kern tx_msg = {1, &tx_iov, 0, NULL};
    va_list ap;
    int ret;
    int slen;

    va_start(ap, fmt);
    /* Format string into buf[1...]. buf[0] contains the message header. */
    ret = vsnprintf(buf + 1, sizeof(buf) - 1, fmt, ap);
    va_end(ap);

    if (ret < 0) {
        return ret;
    }

    /*
     * vsnprintf returns the length of the string it would produce if the buffer
     * was big enough. Compute the actual string length by clamping the return
     * value to the largest string that can fit in the buffer.
     */
    slen = MIN(ret, (int)sizeof(buf) - 1 - 1);

    buf[0] = TEST_MESSAGE;
    tx_iov.iov_len = 1 + slen;
    mutex_acquire(&unittest_lock);
    ret = send_msg_wait(ipc_printf_handle, &tx_msg);
    mutex_release(&unittest_lock);
    if (ret < 0) {
        return ret;
    }

    return slen;
}

/**
 * unittest_loop - Thread function handling all kernel unit-tests
 * arg:     Unused thread argument.
 *
 * Wait on handle-set for a client to connect. When a client connects, run the
 * test function for the port the client connected to then sent the test status
 * back to the client. The test function can call unittest_printf to send text
 * back to the client.
 *
 * Return: error code is there was an unexpected error.
 */
static int unittest_loop(void* arg) {
    int ret;
    struct handle* chandle;
    struct handle_ref evt;
    const uuid_t* unused_uuid_p;
    struct unittest* test;

    LTRACEF("waiting for connection\n");
    for (;;) {
        ret = handle_set_wait(unittest_handle_set, &evt, INFINITE_TIME);
        if (ret < 0) {
            TRACEF("handle_set_wait failed: %d\n", ret);
            break;
        }
        test = evt.cookie;
        LTRACEF("got event (ret=%d): ev=%x handle=%p port=%s\n", ret, evt.emask,
                evt.handle, test->port_name);
        if (evt.emask & IPC_HANDLE_POLL_READY) {
            /* get connection request */
            ret = ipc_port_accept(evt.handle, &chandle, &unused_uuid_p);
            LTRACEF("accept returned %d\n", ret);
            if (ret >= 0) {
                char tx_buffer[1];
                struct iovec_kern tx_iov = {
                        tx_buffer,
                        sizeof(tx_buffer),
                };
                struct ipc_msg_kern tx_msg = {1, &tx_iov, 0, NULL};

                /* then run unittest test */
                ipc_printf_handle = chandle;
                tx_buffer[0] = test->run_test(test) ? TEST_PASSED : TEST_FAILED;
                mutex_acquire(&unittest_lock);
                ipc_printf_handle = NULL;

                send_msg_wait(chandle, &tx_msg);
                mutex_release(&unittest_lock);

                /* and close it */
                handle_close(chandle);
            }
        }
    }

    return ret;
}

/**
 * unittest_add_locked - Internal helper function to add a kernel unit-test
 * @test:   See unittest_add.
 *
 * unittest_lock must be locked before calling this.
 *
 * Return: See unittest_add.
 */
static int unittest_add_locked(struct unittest* test) {
    int ret;
    struct handle* phandle;

    ASSERT(is_mutex_held(&unittest_lock));

    if (!unittest_handle_set) {
        unittest_handle_set = handle_set_create();
        if (!unittest_handle_set) {
            ret = ERR_NO_MEMORY;
            goto err_handle_set_create;
        }
    }
    ret = ipc_port_create(&kernel_uuid, test->port_name, 1, MAX_PORT_BUF_SIZE,
                          IPC_PORT_ALLOW_NS_CONNECT | IPC_PORT_ALLOW_TA_CONNECT,
                          &phandle);
    if (ret) {
        goto err_port_create;
    }

    ret = ipc_port_publish(phandle);
    if (ret) {
        goto err_port_publish;
    }
    handle_incref(phandle);
    test->_href.handle = phandle;
    test->_href.emask = ~0U;
    test->_href.cookie = test;
    ret = handle_set_attach(unittest_handle_set, &test->_href);
    if (ret < 0) {
        goto err_handle_set_attach;
    }
    LTRACEF("added port %s handle, %p, to handleset %p\n", test->port_name,
            test->_href.handle, unittest_handle_set);

    if (!unittest_thread) {
        unittest_thread = thread_create("unittest", unittest_loop, NULL,
                                        HIGH_PRIORITY, DEFAULT_STACK_SIZE);
        if (!unittest_thread) {
            ret = ERR_NO_MEMORY;
            goto err_thread_create;
        }
        thread_resume(unittest_thread);
    }
    return 0;

err_thread_create:
    handle_set_detach_ref(&test->_href);
err_handle_set_attach:
    handle_decref(phandle);
err_port_publish:
    handle_close(phandle);
err_port_create:
err_handle_set_create:
    TRACEF("Failed to add unittest: %d\n", ret);
    return ret;
}

/**
 * unittest_add - Add a kernel unit-test
 * @test:   Test descriptor with port name and callback to start the test when
 *          a client connects to the port. @test is used after unittest_add
 *          returns so it must not be a temporary allocation.
 *
 * Creates a port for @test. when the first test is added, create a handle set
 * and thread that will be shared between all tests.
 *
 * Return: 0 if test was added, error-code otherwise.
 */
int unittest_add(struct unittest* test) {
    int ret;

    mutex_acquire(&unittest_lock);
    ret = unittest_add_locked(test);
    mutex_release(&unittest_lock);

    return ret;
}
