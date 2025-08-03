/*
 * Copyright (c) 2019, Google Inc. All rights reserved
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
#include <kernel/event.h>
#include <kernel/thread.h>
#include <lib/trusty/ipc.h>
#include <lk/init.h>
#include <lk/trace.h>
#include <stdbool.h>
#include <stdio.h>

#define LOCAL_TRACE (0)

static struct event busy_test_event =
        EVENT_INITIAL_VALUE(busy_test_event, false, 0);

static void busy_test_connected(struct handle* chandle) {
    int ret;
    uint32_t event;

    LTRACEF("event wait count: %d\n", busy_test_event.wait.count);

    ret = event_signal(&busy_test_event, true);
    if (ret) {
        TRACEF("event_signal failed %d\n", ret);
        goto err;
    }
    ret = handle_wait(chandle, &event, INFINITE_TIME);
    LTRACEF("got channel event (ret=%d): ev=%x\n", ret, event);

err:
    ret = event_unsignal(&busy_test_event);
    if (ret) {
        TRACEF("event_unsignal failed %d\n", ret);
    }
    handle_close(chandle);
}

static int busy_test_server(void* arg) {
    struct handle* phandle = arg;
    struct handle* chandle;
    const uuid_t* unused_uuid_p;
    uint32_t event;
    int ret;

    while (true) {
        ret = handle_wait(phandle, &event, INFINITE_TIME);
        if (ret < 0) {
            TRACEF("handle_wait failed: %d\n", ret);
            break;
        }
        LTRACEF("got port event (ret=%d): ev=%x\n", ret, event);
        if (event & IPC_HANDLE_POLL_READY) {
            /* get connection request */
            ret = ipc_port_accept(phandle, &chandle, &unused_uuid_p);
            LTRACEF("accept returned %d\n", ret);
            if (ret >= 0) {
                busy_test_connected(chandle);
            }
        }
    }
    return 0;
}

static void busy_test_init(uint level) {
    int ret;
    thread_t* thread;
    struct handle* phandle;

    ret = ipc_port_create(&kernel_uuid, "com.android.kernel.busy-test", 1, 1,
                          IPC_PORT_ALLOW_NS_CONNECT, &phandle);
    if (ret) {
        goto err_port_create;
    }

    ret = ipc_port_publish(phandle);
    if (ret) {
        goto err_port_publish;
    }

    thread = thread_create("busy-test-server", busy_test_server, phandle,
                           DEFAULT_PRIORITY, DEFAULT_STACK_SIZE);
    if (!thread) {
        ret = ERR_NO_MEMORY;
        goto err_thread_create;
    }
    thread_resume(thread);
    return;

err_thread_create:
    handle_close(phandle);
err_port_publish:
    handle_close(phandle);
err_port_create:
    TRACEF("Failed to add busy_test: %d\n", ret);
    return;
}

LK_INIT_HOOK(busy_test_init, busy_test_init, LK_INIT_LEVEL_APPS);

static int busy_test_busy_func(void* arg) {
    LTRACEF("cpu %d ready\n", arch_curr_cpu_num());
    while (true) {
        event_wait(&busy_test_event);
    }
    return 0;
}

static void busy_test_cpu_init(uint level) {
    thread_t* thread;
    char thread_name[32];
    uint cpu = arch_curr_cpu_num();
    snprintf(thread_name, sizeof(thread_name), "busy-test-%d", cpu);
    thread = thread_create(thread_name, busy_test_busy_func, NULL, LOW_PRIORITY,
                           DEFAULT_STACK_SIZE);
#if WITH_SMP
    thread->pinned_cpu = cpu;
#endif
    thread_resume(thread);
}

LK_INIT_HOOK_FLAGS(busy_test_cpu_init,
                   busy_test_cpu_init,
                   LK_INIT_LEVEL_APPS,
                   LK_INIT_FLAG_ALL_CPUS);
