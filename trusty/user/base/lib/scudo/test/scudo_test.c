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
#include <stdlib.h>
#include <sys/auxv.h>
#include <trusty/memref.h>
#include <trusty_unittest.h>
#include <uapi/err.h>
#include <uapi/mm.h>

#include <scudo_app.h>
#include <scudo_consts.h>

#define TLOG_TAG "scudo_test"

#ifndef HWCAP2_MTE
#define HWCAP2_MTE (1 << 18)
#endif

#define PAGE_SIZE getauxval(AT_PAGESZ)

int send_memref_msg(handle_t chan,
                    const void* buf,
                    size_t len,
                    handle_t memref) {
    struct iovec iov = {
            .iov_base = (void*)buf,
            .iov_len = len,
    };
    ipc_msg_t msg = {
            .iov = &iov,
            .num_iov = 1,
            .handles = memref < 0 ? NULL : &memref,
            .num_handles = memref < 0 ? 0 : 1,
    };
    return send_msg(chan, &msg);
}

/*
 * Sends command to app and then waits for a
 * reply or channel close. In the non-crashing case, the server
 * should echo back the original command and scudo_srv_rpc returns
 * NO_ERROR.
 */
static int scudo_srv_rpc_memref(handle_t chan,
                                enum scudo_command cmd,
                                int memref) {
    int ret;
    struct scudo_msg msg = {
            .cmd = cmd,
    };

    ret = send_memref_msg(chan, &msg, sizeof(msg), memref);
    ASSERT_GE(ret, 0);
    ASSERT_EQ(ret, sizeof(msg));

    struct uevent evt;
    ret = wait(chan, &evt, INFINITE_TIME);
    if (ret) {
        /* error while waiting on channel */
        return ret;
    }

    if (evt.event & IPC_HANDLE_POLL_HUP) {
        ASSERT_EQ(evt.event & IPC_HANDLE_POLL_MSG, 0);
        return ERR_CHANNEL_CLOSED;
    }
    ASSERT_NE(evt.event & IPC_HANDLE_POLL_MSG, 0);

    ret = tipc_recv1(chan, sizeof(msg), &msg, sizeof(msg));
    if (ret < 0) {
        return ret;
    }
    ASSERT_EQ(ret, sizeof(msg));
    if (msg.cmd == cmd) {
        return NO_ERROR;
    }
    return msg.cmd;

test_abort:
    /* Use ERR_IO to indicate internal error with the test app */
    return ERR_IO;
}

static int scudo_srv_rpc(handle_t chan, enum scudo_command cmd) {
    return scudo_srv_rpc_memref(chan, cmd, -1);
}

typedef struct scudo_info {
    handle_t chan;
} scudo_info_t;

static bool has_mte(void) {
    return getauxval(AT_HWCAP2) & HWCAP2_MTE;
}

TEST_F_SETUP(scudo_info) {
    _state->chan = INVALID_IPC_HANDLE;
    ASSERT_EQ(tipc_connect(&_state->chan, SCUDO_TEST_SRV_PORT), 0);

test_abort:;
}

TEST_F_TEARDOWN(scudo_info) {
    close(_state->chan);
}

TEST_F(scudo_info, nop) {
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_NOP), NO_ERROR);
}

TEST_F(scudo_info, one_malloc) {
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_ONE_MALLOC), NO_ERROR);
}

TEST_F(scudo_info, one_calloc) {
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_ONE_CALLOC), NO_ERROR);
}

TEST_F(scudo_info, one_realloc) {
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_ONE_REALLOC), NO_ERROR);
}

TEST_F(scudo_info, many_malloc) {
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_MANY_MALLOC), NO_ERROR);
}

TEST_F(scudo_info, one_new) {
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_ONE_NEW), NO_ERROR);
}

TEST_F(scudo_info, one_new_arr) {
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_ONE_NEW_ARR), NO_ERROR);
}

TEST_F(scudo_info, malloc_and_new) {
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_MALLOC_AND_NEW), NO_ERROR);
}

TEST_F(scudo_info, double_free) {
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_DOUBLE_FREE),
              ERR_CHANNEL_CLOSED);
}

TEST_F(scudo_info, realloc_after_free) {
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_REALLOC_AFTER_FREE),
              ERR_CHANNEL_CLOSED);
}

TEST_F(scudo_info, dealloc_type_mismatch) {
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_DEALLOC_TYPE_MISMATCH),
              ERR_CHANNEL_CLOSED);
}

TEST_F(scudo_info, realloc_type_mismatch) {
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_REALLOC_TYPE_MISMATCH),
              ERR_CHANNEL_CLOSED);
}

TEST_F(scudo_info, alloc_large) {
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_ALLOC_LARGE), NO_ERROR);
}

TEST_F(scudo_info, mte_tagged_memref_small) {
    if (!has_mte()) {
        trusty_unittest_printf("[  SKIPPED ] MTE is not available\n");
        return;
    }
    int ref = -1;
    void* mem = memalign(PAGE_SIZE, PAGE_SIZE);
    ASSERT_NE(mem, NULL);
    memset(mem, 0x33, PAGE_SIZE);
    ref = memref_create(
            mem, PAGE_SIZE,
            MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE | MMAP_FLAG_PROT_MTE);
    ASSERT_GT(ref, 0);
    printf("created memref %d for %p\n", ref, mem);
    int rc = scudo_srv_rpc_memref(_state->chan, SCUDO_TAGGED_MEMREF_SMALL, ref);
    EXPECT_EQ(rc, NO_ERROR);
    EXPECT_EQ(*((volatile char*)mem), 0x77);
test_abort:
    close(ref);
    free(mem);
}

TEST_F(scudo_info, mte_tagged_memref_large) {
    if (!has_mte()) {
        trusty_unittest_printf("[  SKIPPED ] MTE is not available\n");
        return;
    }
    int ref = -1;
    void* mem = memalign(PAGE_SIZE, PAGE_SIZE * 32);
    ASSERT_NE(mem, NULL);
    memset(mem, 0x33, PAGE_SIZE);
    ref = memref_create(
            mem, PAGE_SIZE * 32,
            MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE | MMAP_FLAG_PROT_MTE);
    ASSERT_GT(ref, 0);
    printf("created memref %d for %p\n", ref, mem);
    int rc = scudo_srv_rpc_memref(_state->chan, SCUDO_TAGGED_MEMREF_LARGE, ref);
    EXPECT_EQ(rc, NO_ERROR);
    EXPECT_EQ(*((volatile char*)mem), 0x77);
test_abort:
    close(ref);
    free(mem);
}

TEST_F(scudo_info, mte_untagged_memref_small) {
    int ref = -1;
    void* mem = memalign(PAGE_SIZE, PAGE_SIZE);
    ASSERT_NE(mem, NULL);
    memset(mem, 0x33, PAGE_SIZE);
    ref = memref_create(mem, PAGE_SIZE,
                        MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE);
    ASSERT_GT(ref, 0);
    printf("created memref %d for %p\n", ref, mem);
    int rc = scudo_srv_rpc_memref(_state->chan, SCUDO_UNTAGGED_MEMREF_SMALL,
                                  ref);
    EXPECT_EQ(rc, NO_ERROR);
    EXPECT_EQ(*((volatile char*)mem), 0x77);
test_abort:
    close(ref);
    free(mem);
}

TEST_F(scudo_info, mte_untagged_memref_large) {
    int ref = -1;
    void* mem = memalign(PAGE_SIZE, PAGE_SIZE * 32);
    ASSERT_NE(mem, NULL);
    memset(mem, 0x33, PAGE_SIZE);
    ref = memref_create(mem, PAGE_SIZE * 32,
                        MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE);
    ASSERT_GT(ref, 0);
    printf("created memref %d for %p\n", ref, mem);
    int rc = scudo_srv_rpc_memref(_state->chan, SCUDO_UNTAGGED_MEMREF_LARGE,
                                  ref);
    EXPECT_EQ(rc, NO_ERROR);
    EXPECT_EQ(*((volatile char*)mem), 0x77);
test_abort:
    close(ref);
    free(mem);
}

TEST_F(scudo_info, mte_mismatched_tag_read) {
    if (!has_mte()) {
        trusty_unittest_printf("[  SKIPPED ] MTE is not available\n");
        return;
    }
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_MEMTAG_MISMATCHED_READ),
              ERR_CHANNEL_CLOSED);
}

TEST_F(scudo_info, mte_mismatched_tag_write) {
    if (!has_mte()) {
        trusty_unittest_printf("[  SKIPPED ] MTE is not available\n");
        return;
    }
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_MEMTAG_MISMATCHED_WRITE),
              ERR_CHANNEL_CLOSED);
}

TEST_F(scudo_info, mte_memtag_read_after_free) {
    if (!has_mte()) {
        trusty_unittest_printf("[  SKIPPED ] MTE is not available\n");
        return;
    }
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_MEMTAG_READ_AFTER_FREE),
              ERR_CHANNEL_CLOSED);
}

TEST_F(scudo_info, mte_memtag_write_after_free) {
    if (!has_mte()) {
        trusty_unittest_printf("[  SKIPPED ] MTE is not available\n");
        return;
    }
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_MEMTAG_WRITE_AFTER_FREE),
              ERR_CHANNEL_CLOSED);
}

TEST_F(scudo_info, alloc_benchmark) {
    EXPECT_EQ(scudo_srv_rpc(_state->chan, SCUDO_ALLOC_BENCHMARK), NO_ERROR);
}

PORT_TEST(scudo_info, "com.android.trusty.scudotest")
