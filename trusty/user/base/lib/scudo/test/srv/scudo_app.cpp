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

#define TLOG_TAG "scudo_app"

#include <assert.h>
#include <lib/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/err_ptr.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <trusty/memref.h>
#include <trusty_log.h>
#include <uapi/err.h>
#include <uapi/mm.h>

#include <scudo_app.h>
#include <scudo_consts.h>

#define ARR_SIZE 10

/*
 * Scudo supports dealloc type mismatch checking. That is, Scudo
 * can be configured to report an error if a chunk is allocated
 * using new but deallocated using free instead of delete, for
 * example. By default, dealloc type mismatch is disabled, but we
 * enable it here to check its functionality in
 * SCUDO_DEALLOC_TYPE_MISMATCH and also to ensure default Scudo
 * options can be overridden.
 */
extern "C" __attribute__((visibility("default"))) const char*
__scudo_default_options() {
    return "dealloc_type_mismatch=true";
}

static int scudo_on_message(const struct tipc_port* port,
                            handle_t chan,
                            void* ctx);

static struct tipc_port_acl scudo_port_acl = {
        .flags = IPC_PORT_ALLOW_TA_CONNECT,
        .uuid_num = 0,
        .uuids = NULL,
        .extra_data = NULL,
};

static struct tipc_port scudo_port = {
        .name = SCUDO_TEST_SRV_PORT,
        .msg_max_size = sizeof(struct scudo_msg),
        .msg_queue_len = 1,
        .acl = &scudo_port_acl,
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
 * Touch all bytes in a string (up to ARR_SIZE).
 * String buffer should be at least ARR_SIZE long.
 */
static void touch_string(char* arr) {
    /* make sure we don't go out of the buffer */
    arr[ARR_SIZE - 1] = '\0';

    while (*arr != '\0') {
        *(reinterpret_cast<volatile char*>(arr)) =
                *(reinterpret_cast<volatile char*>(arr));
        arr++;
    }
}

/*
 * In addition to touching arr, it is memset with fill_char
 * and printed as a check that arr points to valid writable memory.
 */
static void touch_and_print(char* arr, const char fill_char) {
    touch(arr);
    memset(arr, fill_char, ARR_SIZE - 1);
    arr[ARR_SIZE - 1] = '\0';
    TLOG("arr = %s\n", arr);
}

static void* retagged(void* taggedptr) {
    uint64_t tagged = reinterpret_cast<uint64_t>(taggedptr);
    uint64_t tag = tagged & 0x0f00000000000000;
    uint64_t untagged = tagged & 0x00ffffffffffffff;
    uint64_t newtag = (tag + 0x0100000000000000) & 0x0f00000000000000;
    ;
    return reinterpret_cast<void*>(newtag | untagged);
}

int recv_memref_msg(handle_t chan,
                    size_t min_sz,
                    void* buf,
                    size_t buf_sz,
                    int* memref) {
    int rc;
    ipc_msg_info_t msg_inf;

    rc = get_msg(chan, &msg_inf);
    if (rc)
        return rc;

    if (msg_inf.len < min_sz || msg_inf.len > buf_sz ||
        msg_inf.num_handles > 1) {
        /* unexpected msg size: buffer too small or too big */
        rc = ERR_BAD_LEN;
    } else {
        struct iovec iov = {
                .iov_base = buf,
                .iov_len = buf_sz,
        };
        ipc_msg_t msg = {
                .num_iov = 1,
                .iov = &iov,
                .num_handles = msg_inf.num_handles,
                .handles = msg_inf.num_handles ? memref : NULL,
        };
        rc = read_msg(chan, msg_inf.id, 0, &msg);
    }

    put_msg(chan, msg_inf.id);
    return rc;
}

static int scudo_on_message(const struct tipc_port* port,
                            handle_t chan,
                            void* ctx) {
    struct scudo_msg msg;
    int memref = -1;

    int ret = recv_memref_msg(chan, sizeof(msg), &msg, sizeof(msg), &memref);
    if (ret < 0 || ret != sizeof(msg)) {
        TLOGE("Failed to receive message (%d)\n", ret);
        return ret;
    }

    switch (msg.cmd) {
    /*
     * SCUDO_NOP test checks that the internal testing machinery
     * is working properly even when no Scudo functions are called.
     * Since some of the tests are expected to crash the server, we
     * need to make sure the server isn't just always crashing.
     */
    case SCUDO_NOP: {
        TLOGI("nop\n");
        break;
    }
    /*
     * SCUDO_ONE_MALLOC tests that a single call to malloc and free
     * works as intended.
     */
    case SCUDO_ONE_MALLOC: {
        TLOGI("one malloc\n");
        char* arr = reinterpret_cast<char*>(malloc(ARR_SIZE));
        touch_and_print(arr, 'a');
        free(arr);
        break;
    }
    /*
     * Similar to SCUDO_ONE_MALLOC, SCUDO_ONE_CALLOC tests that a
     * single call to calloc and free works as intended.
     */
    case SCUDO_ONE_CALLOC: {
        TLOGI("one calloc\n");
        char* arr = reinterpret_cast<char*>(calloc(ARR_SIZE, 1));
        touch_and_print(arr, 'a');
        free(arr);
        break;
    }
    /* Tests that a single call to realloc works. */
    case SCUDO_ONE_REALLOC: {
        TLOGI("one realloc\n");
        char* arr = reinterpret_cast<char*>(malloc(ARR_SIZE));
        touch_and_print(arr, 'a');
        arr = reinterpret_cast<char*>(realloc(arr, 2 * ARR_SIZE));
        touch_and_print(arr + ARR_SIZE - 1, 'b');
        TLOG("arr = %s\n", arr);
        free(arr);
        break;
    }
    /*
     * SCUDO_MANY_MALLOC performs a series of allocations and
     * deallocations to test (1) that deallocated chunks can be
     * reused, and (2) that Scudo can service various different
     * sizes of allocations requests. We know chunks are reused
     * because this app has 2MB bytes of heap memory and ~10MB
     * bytes are malloc-ed by SCUDO_MANY_MALLOC.
     */
    case SCUDO_MANY_MALLOC: {
        TLOGI("many malloc\n");
        // exercise a few of the smaller size classes in the primary allocator
        for (int i = 0; i < 4000; ++i) {
            char* arr = reinterpret_cast<char*>(malloc(ARR_SIZE + i));
            touch(arr);
            snprintf(arr, ARR_SIZE, "(%d)!", i);
            touch_string(arr);
            if ((i % 100) == 0) {
                TLOG("arr = %s\n", arr);
            }
            free(arr);
        }
        // do some larger allocations to verify the secondary allocator
        // releases memory
        for (int i = 0; i < 20; ++i) {
            TLOG("secondary %d\n", i);
            char* arr = reinterpret_cast<char*>(malloc(128 * 1024));
            touch(arr);
            free(arr);
        }
        break;
    }
    /* Tests that a single allocation with new and delete works. */
    case SCUDO_ONE_NEW: {
        TLOGI("one new\n");
        int* foo = new int(37);
        touch(foo);
        TLOG("*foo = %d\n", *foo);
        delete foo;
        break;
    }
    /* Tests that a single allocation with new[] and delete[] works. */
    case SCUDO_ONE_NEW_ARR: {
        TLOGI("one new arr\n");
        char* arr = new char[ARR_SIZE];
        touch_and_print(arr, 'a');
        delete[] arr;
        break;
    }
    /* Tests that Scudo can service allocation requests using both malloc and
     * new. */
    case SCUDO_MALLOC_AND_NEW: {
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
     * Scudo uses checksummed headers to protect against double-freeing,
     * so this test which attempts to free a chunk twice should crash.
     */
    case SCUDO_DOUBLE_FREE: {
        TLOGI("double free\n");
        char* arr = reinterpret_cast<char*>(malloc(ARR_SIZE));
        touch_and_print(arr, 'a');
        free(arr);
        free(arr);
        break;
    }
    /*
     * Scudo ensures that freed chunks cannot be realloc-ed, so this
     * test which attempts to realloc a freed chunk should crash.
     */
    case SCUDO_REALLOC_AFTER_FREE: {
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
     * When dealloc_type_mismatch is enabled, Scudo ensures that chunks
     * are allocated and deallocated using corresponding functions. Since
     * this test allocates a chunk with new and deallocates it with free,
     * it should crash the server.
     */
    case SCUDO_DEALLOC_TYPE_MISMATCH: {
        TLOGI("dealloc type mismatch\n");
        char* arr = new char[ARR_SIZE];
        touch_and_print(arr, 'a');
        free(arr);
        break;
    }
    /*
     * Similar to SCUDO_DEALLOC_TYPE_MISMATCH, with dealloc_type_mismatch,
     * Scudo should ensure that chunks from memalign() cannot be realloc()'d
     * which could lose alignment.
     */
    case SCUDO_REALLOC_TYPE_MISMATCH: {
        TLOGI("realloc type mismatch\n");
        char* arr = reinterpret_cast<char*>(memalign(32, ARR_SIZE));
        touch_and_print(arr, 'a');
        arr = reinterpret_cast<char*>(realloc(arr, ARR_SIZE * 2));
        break;
    }

    case SCUDO_ALLOC_LARGE: {
        TLOGI("alloc 1.5MB\n");
        char* arr = reinterpret_cast<char*>(malloc(1500000));
        touch(arr);
        free(arr);
        break;
    }

    case SCUDO_TAGGED_MEMREF_SMALL:
    case SCUDO_TAGGED_MEMREF_LARGE: {
        size_t memrefsize = 4096;
        if (msg.cmd == SCUDO_TAGGED_MEMREF_LARGE) {
            memrefsize *= 32;
        }
        TLOGI("tagged memref (%d)\n", memref);
        volatile char* mapped = (volatile char*)mmap(
                0, memrefsize,
                MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE | MMAP_FLAG_PROT_MTE,
                0, memref, 0);
        if (mapped != MAP_FAILED) {
            TLOGI("Tagged memref should have failed\n");
            msg.cmd = SCUDO_TEST_FAIL;
            munmap((void*)mapped, memrefsize);
            close(memref);
            break;
        }

        mapped = (volatile char*)mmap(
                0, memrefsize, MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE, 0,
                memref, 0);
        if (mapped == MAP_FAILED) {
            TLOGI("Untagged mapping failed\n");
            msg.cmd = SCUDO_TEST_FAIL;
            close(memref);
            break;
        }
        *mapped = 0x77;
        munmap((void*)mapped, memrefsize);
        close(memref);
        break;
    }

    case SCUDO_UNTAGGED_MEMREF_SMALL:
    case SCUDO_UNTAGGED_MEMREF_LARGE: {
        size_t memrefsize = 4096;
        if (msg.cmd == SCUDO_UNTAGGED_MEMREF_LARGE) {
            memrefsize *= 32;
        }
        TLOGI("untagged memref (%d)\n", memref);
        volatile char* mapped = (volatile char*)mmap(
                0, memrefsize, MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE, 0,
                memref, 0);

        if (!mapped || *mapped != 0x33) {
            TLOGI("no map or bad data in memref %p: %0x\n", mapped,
                  mapped ? *mapped : 0);
            msg.cmd = SCUDO_TEST_FAIL;
            close(memref);
            break;
        }
        *mapped = 0x77;
        munmap((void*)mapped, memrefsize);
        close(memref);
        break;
    }

    case SCUDO_MEMTAG_MISMATCHED_READ: {
        void* mem = malloc(64);
        char* arr = reinterpret_cast<char*>(mem);
        *arr = 0x33;
        volatile char* retagged_arr =
                3 + reinterpret_cast<char*>(retagged(mem));
        TLOGI("mismatched tag read %016lx %016lx\n", (uint64_t)arr,
              (uint64_t)retagged_arr);
        *arr = *retagged_arr;
        TLOGI("should not be here\n");
        free(mem);
        break;
    }

    case SCUDO_MEMTAG_MISMATCHED_WRITE: {
        void* mem = malloc(64);
        char* arr = reinterpret_cast<char*>(mem);
        *arr = 0x44;
        volatile char* retagged_arr = reinterpret_cast<char*>(retagged(mem));
        TLOGI("mismatched tag write %016lx %016lx\n", (uint64_t)arr,
              (uint64_t)retagged_arr);
        *retagged_arr = *arr;
        TLOGI("should not be here\n");
        free(mem);
        break;
    }

    case SCUDO_MEMTAG_READ_AFTER_FREE: {
        void* mem = malloc(64);
        memset(mem, 64, 0xaa);
        char* arr = reinterpret_cast<char*>(mem);
        free(mem);
        TLOGI("read after free %016lx\n", (uint64_t)arr);
        touch(arr);  // this reads before writing
        TLOGI("should not be here\n");
        break;
    }

    case SCUDO_MEMTAG_WRITE_AFTER_FREE: {
        void* mem = malloc(64);
        memset(mem, 64, 0xbb);
        char* arr = reinterpret_cast<char*>(mem);
        free(mem);
        TLOGI("write after free %016lx\n", (uint64_t)arr);
        *arr = 1;
        TLOGI("should not be here\n");
        break;
    }

    case SCUDO_ALLOC_BENCHMARK: {
        TLOGI("alloc benchmark\n");
        char* arr = reinterpret_cast<char*>(malloc(1500000));
        touch(arr);
        free(arr);
        for (int i = 0; i < 1000; i++) {
            uint num_allocs = rand() % 40 + 1;
            char** arr2 = reinterpret_cast<char**>(
                    malloc(sizeof(char*) * num_allocs));
            for (uint j = 0; j < num_allocs; j++) {
                uint num_allocs_2 = rand() % 64 + 1;
                arr2[j] = reinterpret_cast<char*>(malloc(num_allocs_2));
                touch(arr2[j]);
            }
            for (uint j = 0; j < num_allocs; j++) {
                free(arr2[j]);
            }
            free(arr2);
        }
        break;
    }

    default:
        TLOGE("Bad command: %d\n", msg.cmd);
        msg.cmd = SCUDO_BAD_CMD;
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

static struct tipc_srv_ops scudo_ops = {
        .on_message = scudo_on_message,
};

int main(void) {
    struct tipc_hset* hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("Failed to create handle set (%d)\n", PTR_ERR(hset));
        return PTR_ERR(hset);
    }

    int rc = tipc_add_service(hset, &scudo_port, 1, 1, &scudo_ops);
    if (rc < 0) {
        TLOGE("Failed to add service (%d)\n", rc);
        return rc;
    }

    /* if app exits, kernel will log that */
    return tipc_run_event_loop(hset);
}
