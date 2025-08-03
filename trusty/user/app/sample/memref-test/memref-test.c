/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <inttypes.h>
#include <lk/macros.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>

#include <lib/tipc/tipc.h>
#include <lib/unittest/unittest.h>

#include <trusty/memref.h>
#include <trusty/sys/mman.h>
#include <trusty/time.h>
#include <trusty_unittest.h>

#include <lender.h>
#include <lender_consts.h>

#define TLOG_TAG "memref-test"

#define PAGE_SIZE 0x1000

static __attribute__((aligned(PAGE_SIZE))) char bss_page[PAGE_SIZE];
static __attribute__((aligned(PAGE_SIZE))) const char ro_page[PAGE_SIZE] = {1};

static int lender_connect(handle_t* chan) {
    return tipc_connect(chan, LENDER_PORT);
}

static int lender_command(handle_t chan, enum lender_command cmd) {
    struct lender_msg msg = {
            .cmd = cmd,
    };
    int rc = tipc_send1(chan, &msg, sizeof(msg));
    if (rc != sizeof(msg)) {
        TLOGE("Failed to send command (%d)\n", rc);
        return -1;
    }

    return 0;
}

static int lender_recv_handle(handle_t chan, handle_t* out) {
    struct uevent evt;
    int rc = wait(chan, &evt, INFINITE_TIME);
    if (rc) {
        return rc;
    }

    struct ipc_msg msg = {
            .iov = NULL,
            .num_iov = 0,
            .handles = out,
            .num_handles = 1,
    };

    struct ipc_msg_info msg_inf;
    rc = get_msg(chan, &msg_inf);
    if (rc) {
        return rc;
    }

    rc = read_msg(chan, msg_inf.id, 0, &msg);
    put_msg(chan, msg_inf.id);
    return rc;
}

static int lender_lend_bss(handle_t chan, handle_t* memref) {
    int rc = lender_command(chan, LENDER_LEND_BSS);
    if (rc) {
        return rc;
    }

    return lender_recv_handle(chan, memref);
}

int lender_read_bss(handle_t chan, size_t offset, size_t size, char* buf) {
    struct lender_msg message = {.cmd = LENDER_READ_BSS,
                                 .region = {
                                         .offset = offset,
                                         .size = size,
                                 }};

    int rc = tipc_send1(chan, &message, sizeof(message));
    if (rc != (int)sizeof(message)) {
        return -1;
    }

    struct uevent evt;
    rc = wait(chan, &evt, INFINITE_TIME);
    if (rc) {
        return rc;
    }

    rc = tipc_recv1(chan, size, buf, size);
    if (rc != (int)size) {
        return -1;
    }

    return 0;
}

int lender_write_bss(handle_t chan,
                     size_t offset,
                     size_t size,
                     const char* buf) {
    struct lender_msg message = {
            .cmd = LENDER_WRITE_BSS,
            .region =
                    {
                            .offset = offset,
                            .size = size,
                    },
    };

    int rc = tipc_send2(chan, &message, sizeof(message), buf, size);
    if (rc != (int)(sizeof(message) + size)) {
        return -1;
    }

    struct uevent evt;
    rc = wait(chan, &evt, INFINITE_TIME);
    if (rc) {
        return rc;
    }
    return tipc_recv1(chan, 0, NULL, 0);
}

int lender_suicide(handle_t chan) {
    int rc = lender_command(chan, LENDER_SUICIDE);
    if (rc) {
        return rc;
    }

    close(chan);
    return 0;
}

#define MM_RW (MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE)

TEST(memref, self_map) {
    volatile char* out = NULL;
    handle_t mref = memref_create(bss_page, PAGE_SIZE, MM_RW);
    ASSERT_GT(mref, 0);
    out = mmap(0, PAGE_SIZE, MM_RW, 0, mref, 0);
    ASSERT_NE((void*)out, MAP_FAILED);

    *out = 3;

    EXPECT_EQ(bss_page[0], 3);

    EXPECT_EQ(0, munmap((void*)out, PAGE_SIZE));

test_abort:
    close(mref);
    bss_page[0] = 0;
}

TEST(memref, dup_map) {
    volatile char* mbuf = NULL;
    volatile char* dbuf = NULL;
    handle_t dref = INVALID_IPC_HANDLE;
    handle_t mref = memref_create(bss_page, PAGE_SIZE, MM_RW);
    ASSERT_GT(mref, 0);

    dref = dup(mref);
    ASSERT_GT(dref, 0);

    mbuf = mmap(0, PAGE_SIZE, MM_RW, 0, mref, 0);
    ASSERT_NE((void*)mbuf, MAP_FAILED);

    dbuf = mmap(0, PAGE_SIZE, MM_RW, 0, dref, 0);
    ASSERT_NE((void*)dbuf, MAP_FAILED);

    dbuf[0] = 42;
    EXPECT_EQ(bss_page[0], 42);
    EXPECT_EQ(mbuf[0], 42);

test_abort:
    if (dbuf && dbuf != MAP_FAILED) {
        munmap((void*)dbuf, PAGE_SIZE);
    }
    if (mbuf && mbuf != MAP_FAILED) {
        munmap((void*)mbuf, PAGE_SIZE);
    }
    close(dref);
    close(mref);
    bss_page[0] = 0;
}

#define EXPECT_CLOSE(e) \
{ \
    handle_t rc = e; \
    EXPECT_LT(rc, 0); \
    close(rc); \
}

TEST(memref, creation) {
    /* should fail due to bad alignment */
    EXPECT_CLOSE(memref_create(bss_page + 1, PAGE_SIZE, MM_RW));
    /* should fail due to non-page size */
    EXPECT_CLOSE(memref_create(bss_page, PAGE_SIZE - 1, MM_RW));
    /* should fail due to out-of-bounds size */
    EXPECT_CLOSE(memref_create(bss_page, 10 * PAGE_SIZE, MM_RW));
    /* should fail due to exec */
    EXPECT_CLOSE(memref_create(bss_page, PAGE_SIZE,
                               MM_RW | MMAP_FLAG_PROT_EXEC));
    /* should fail due to garbage prot */
    EXPECT_CLOSE(memref_create(bss_page, PAGE_SIZE, 0xF000));
    /* should fail due to rw perms on a ro page */
    EXPECT_CLOSE(memref_create((void*)ro_page, PAGE_SIZE, MM_RW));
    /* should succeed, ro perms on a rw page */
    handle_t mref = memref_create((void*)bss_page, PAGE_SIZE,
                                  MMAP_FLAG_PROT_READ);
    EXPECT_GT(mref, 0);
    close(mref);
}

static void test_recv_ref(bool do_dup) {
    handle_t chan = INVALID_IPC_HANDLE;
    handle_t remote_memref = INVALID_IPC_HANDLE;
    handle_t dup_memref = INVALID_IPC_HANDLE;
    handle_t mmap_memref;
    volatile char* out = NULL;
    char remote_buf[1] = {0};
    ASSERT_EQ(lender_connect(&chan), 0);
    int rc = lender_lend_bss(chan, &remote_memref);
    ASSERT_EQ(rc, 0);

    if (do_dup) {
        dup_memref = dup(remote_memref);
        ASSERT_GE(dup_memref, 0);
        mmap_memref = dup_memref;
    } else {
        mmap_memref = remote_memref;
    }

    out = mmap(0, PAGE_SIZE, MM_RW, 0, mmap_memref, 0);
    ASSERT_NE((void*)out, NULL);

    *out = 1;
    EXPECT_EQ(0, lender_read_bss(chan, 0, sizeof(remote_buf), remote_buf));
    EXPECT_EQ(1, remote_buf[0]);

    remote_buf[0] = 42;
    EXPECT_EQ(0, lender_write_bss(chan, 1, sizeof(remote_buf), remote_buf));
    EXPECT_EQ(out[0], 1);
    EXPECT_EQ(out[1], 42);

    out[0] = 0;
    out[1] = 0;

    EXPECT_EQ(0, munmap((void*)out, PAGE_SIZE));

test_abort:
    /* double close or INVALID_HANDLE should not cause an issue */
    close(dup_memref);
    close(remote_memref);
    close(chan);
}

TEST(memref, recv_ref) {
    test_recv_ref(false);
}

TEST(memref, dup_ref) {
    test_recv_ref(true);
}

/*
 * Tries to cause an unintended memref leak.
 * Many iterations are needed to make the leak surface in the form
 * of the lender application failing to reset itself.
 *
 * Ideally keep this test at the end so that if it OOMs the VM, the suite
 * will have already produced all other results.
 */
const int leak_creation_iters = 1000;
TEST(memref, leak_creation) {
    handle_t chan = INVALID_IPC_HANDLE;
    handle_t memref = INVALID_IPC_HANDLE;
    for (int i = 0; i < leak_creation_iters; i++) {
        ASSERT_EQ(0, lender_connect(&chan));
        ASSERT_EQ(0, lender_lend_bss(chan, &memref));
        close(memref);
        ASSERT_EQ(0, lender_suicide(chan));
        close(chan);
    }

test_abort:
    /* double closes should be safe */
    close(memref);
    close(chan);
}

PORT_TEST(memref, "com.android.memref.test")
