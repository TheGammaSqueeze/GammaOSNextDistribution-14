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

#define TLOG_TAG "dlmalloc_app"

#include <assert.h>
#include <lib/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/err_ptr.h>
#include <stdlib.h>
#include <string.h>
#include <trusty_log.h>
#include <uapi/err.h>

#include <dlmalloc_app.h>
#include <dlmalloc_consts.h>

#define ARR_SIZE 10

static struct tipc_port_acl dlmalloc_port_acl = {
        .flags = IPC_PORT_ALLOW_TA_CONNECT,
        .uuid_num = 0,
        .uuids = NULL,
        .extra_data = NULL,
};

static struct tipc_port dlmalloc_port = {
        .name = DLMALLOC_TEST_SRV_PORT,
        .msg_max_size = sizeof(struct dlmalloc_test_msg),
        .msg_queue_len = 1,
        .acl = &dlmalloc_port_acl,
        .priv = NULL,
};

/*
 * To make sure the variable isn't optimized away.
 */
static void touch(volatile void* a) {
    *(reinterpret_cast<volatile char*>(a)) =
            *(reinterpret_cast<volatile char*>(a));
}

/*
 * In addition to touching arr, it is memset with fill_char
 * and printed as a check that arr points to valid writable memory.
 */
static void touch_and_print(char* arr, const char fill_char) {
    touch(arr);
    memset(arr, fill_char, ARR_SIZE - 1);
    arr[ARR_SIZE - 1] = '\0';
    TLOGI("arr = %s\n", arr);
}

static int dlmalloc_on_message(const struct tipc_port* port,
                               handle_t chan,
                               void* ctx) {
    struct dlmalloc_test_msg msg;

    int ret = tipc_recv1(chan, sizeof(msg), &msg, sizeof(msg));
    if (ret < 0) {
        TLOGE("Failed to receive message (%d)\n", ret);
        return ret;
    } else if (ret != sizeof(msg)) {
        TLOGE("Bad response length\n");
        return ERR_BAD_LEN;
    }

    switch (msg.cmd) {
    /*
     * DLMALLOC_TEST_NOP test checks that the internal testing machinery
     * is working properly even when no dlmalloc functions are called.
     * Since some of the tests are expected to crash the server, we
     * need to make sure the server isn't just always crashing.
     */
    case DLMALLOC_TEST_NOP: {
        TLOGI("nop\n");
        break;
    }
    /*
     * DLMALLOC_TEST_ONE_MALLOC tests that a single call to malloc and free
     * works as intended.
     */
    case DLMALLOC_TEST_ONE_MALLOC: {
        TLOGI("one malloc\n");
        char* arr = reinterpret_cast<char*>(malloc(ARR_SIZE));
        touch_and_print(arr, 'a');
        free(arr);
        break;
    }
    /*
     * Similar to DLMALLOC_TEST_ONE_MALLOC, DLMALLOC_TEST_ONE_CALLOC tests that
     * a single call to calloc and free works as intended.
     */
    case DLMALLOC_TEST_ONE_CALLOC: {
        TLOGI("one calloc\n");
        char* arr = reinterpret_cast<char*>(calloc(ARR_SIZE, 1));
        touch_and_print(arr, 'a');
        free(arr);
        break;
    }
    /* Tests that a single call to realloc works. */
    case DLMALLOC_TEST_ONE_REALLOC: {
        TLOGI("one realloc\n");
        char* arr = reinterpret_cast<char*>(malloc(ARR_SIZE));
        touch_and_print(arr, 'a');
        arr = reinterpret_cast<char*>(realloc(arr, 2 * ARR_SIZE));
        touch_and_print(arr + ARR_SIZE - 1, 'b');
        TLOGI("arr = %s\n", arr);
        free(arr);
        break;
    }
    /*
     * DLMALLOC_TEST_MANY_MALLOC performs a series of allocations and
     * deallocations to test (1) that deallocated chunks can be
     * reused, and (2) that dlmalloc can service various different
     * sizes of allocations requests. We know chunks are reused
     * because this app has 2.1MB bytes of heap memory and at least
     * 3MB bytes are malloc-ed by DLMALLOC_TEST_MANY_MALLOC.
     */
    case DLMALLOC_TEST_MANY_MALLOC: {
        TLOGI("many malloc\n");
        for (int i = 0; i < 3000; ++i) {
            char* arr = reinterpret_cast<char*>(malloc(1000 + i));
            touch(arr);
            snprintf(arr, ARR_SIZE, "(%d)!", i);
            TLOGI("arr = %s\n", arr);
            free(arr);
        }
        break;
    }
    /* Tests that a single allocation with new and delete works. */
    case DLMALLOC_TEST_ONE_NEW: {
        TLOGI("one new\n");
        int* foo = new int(37);
        touch(foo);
        TLOGI("*foo = %d\n", *foo);
        delete foo;
        break;
    }
    /* Tests that a single allocation with new[] and delete[] works. */
    case DLMALLOC_TEST_ONE_NEW_ARR: {
        TLOGI("one new arr\n");
        char* arr = new char[ARR_SIZE];
        touch_and_print(arr, 'a');
        delete[] arr;
        break;
    }
    /* Tests that dlmalloc can service allocation requests using both malloc and
     * new. */
    case DLMALLOC_TEST_MALLOC_AND_NEW: {
        TLOGI("malloc and new\n");
        char* arr1 = reinterpret_cast<char*>(malloc(ARR_SIZE));
        touch_and_print(arr1, 'a');
        char* arr2 = new char[ARR_SIZE];
        touch_and_print(arr2, 'b');
        free(arr1);
        delete[] arr2;
        break;
    }
    /*
     * Test which attempts to free a chunk twice should crash.
     */
    case DLMALLOC_TEST_DOUBLE_FREE: {
        TLOGI("double free\n");
        char* arr = reinterpret_cast<char*>(malloc(ARR_SIZE));
        touch_and_print(arr, 'a');
        free(arr);
        free(arr);
        break;
    }
    /*
     * Test which attempts to realloc a freed chunk should crash.
     */
    case DLMALLOC_TEST_REALLOC_AFTER_FREE: {
        TLOGI("realloc after free\n");
        char* arr = reinterpret_cast<char*>(malloc(ARR_SIZE));
        touch_and_print(arr, 'a');
        free(arr);
        arr = reinterpret_cast<char*>(realloc(arr, 2 * ARR_SIZE));
        /* touch arr so realloc is not optimized away */
        touch(arr);
        break;
    }
    /*
     * Allocates a chunk with new and deallocates it with free,
     * it should crash the server.
     */
    case DLMALLOC_TEST_DEALLOC_TYPE_MISMATCH: {
        TLOGI("dealloc type mismatch\n");
        char* arr = new char[ARR_SIZE];
        touch_and_print(arr, 'a');
        free(arr);
        break;
    }

    case DLMALLOC_TEST_ALLOC_LARGE: {
        TLOGI("alloc 1.5MB\n");
        char* arr = reinterpret_cast<char*>(malloc(1500000));
        touch(arr);
        free(arr);
        break;
    }

    default:
        TLOGE("Bad command: %d\n", msg.cmd);
        msg.cmd = DLMALLOC_TEST_BAD_CMD;
    }
    /*
     * We echo the incoming command in the case where the app
     * runs the test without crashing. This is effectively saying "did
     * not crash when executing command X."
     */
    ret = tipc_send1(chan, &msg, sizeof(msg));
    if (ret < 0 || ret != sizeof(msg)) {
        TLOGE("Failed to send message (%d)\n", ret);
        return ret < 0 ? ret : ERR_IO;
    }

    return 0;
}

static struct tipc_srv_ops dlmalloc_ops = {
        .on_message = dlmalloc_on_message,
};

int main(void) {
    struct tipc_hset* hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("Failed to create handle set (%d)\n", PTR_ERR(hset));
        return PTR_ERR(hset);
    }

    int rc = tipc_add_service(hset, &dlmalloc_port, 1, 1, &dlmalloc_ops);
    if (rc < 0) {
        TLOGE("Failed to add service (%d)\n", rc);
        return rc;
    }

    /* if app exits, kernel will log that */
    return tipc_run_event_loop(hset);
}
