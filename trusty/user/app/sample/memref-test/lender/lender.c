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

#define TLOG_TAG "lender"

#include <assert.h>

#include <inttypes.h>
#include <lk/macros.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>

#include <lib/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>
#include <lib/unittest/unittest.h>

#include <trusty/memref.h>
#include <trusty/sys/mman.h>
#include <trusty/time.h>
#include <trusty_unittest.h>

#include <lender.h>
#include <lender_consts.h>

#define PAGE_SIZE 0x1000
#define MAX_WRITE 0x800

static __attribute__((aligned(PAGE_SIZE))) char bss_page[PAGE_SIZE];
static __attribute__((aligned(PAGE_SIZE))) char rw_page[PAGE_SIZE] = {1};

#define MM_RW (MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE)

static handle_t bss_memref = INVALID_IPC_HANDLE;
static handle_t rw_memref = INVALID_IPC_HANDLE;

/*
 * This only creates bss_memref and rw_memref; creating a ro_memref would fail
 * since RO segments are not backed by vmm_objs (b/149849862).
 */
static void init(void) {
    int rc = memref_create(bss_page, PAGE_SIZE, MM_RW);
    if (rc < 0) {
        TLOGE("bss memref create failed: (%d)\n", rc);
        abort();
    }

    bss_memref = rc;

    rc = memref_create(rw_page, PAGE_SIZE, MM_RW);
    if (rc < 0) {
        TLOGE("rw memref create failed: (%d)\n", rc);
        abort();
    }

    rw_memref = rc;
}

static struct tipc_port_acl lender_port_acl = {
        .flags = IPC_PORT_ALLOW_TA_CONNECT,
        .uuid_num = 0,
        .uuids = NULL,
        .extra_data = NULL,
};

static struct tipc_port lender_port = {
        .name = LENDER_PORT,
        .msg_max_size = sizeof(struct lender_msg) + MAX_WRITE,
        .msg_queue_len = 1,
        .acl = &lender_port_acl,
        .priv = NULL,
};

static int lender_lend(handle_t chan, enum lender_command cmd) {
    handle_t to_lend;
    switch (cmd) {
    case LENDER_LEND_BSS:
        to_lend = bss_memref;
        break;
    case LENDER_LEND_RW:
        to_lend = rw_memref;
        break;
    default:
        TLOGE("Unsupported lend type %d\n", cmd);
        return -1;
    }

    if (to_lend == INVALID_IPC_HANDLE) {
        TLOGE("Refusing to lend uninitialized handle.\n");
        return -1;
    }

    struct ipc_msg msg = {
            .iov = NULL,
            .num_iov = 0,
            .handles = &to_lend,
            .num_handles = 1,
    };

    return send_msg(chan, &msg);
}

static int lender_check_region(struct lender_region* region, size_t size) {
    size_t end;
    if (__builtin_add_overflow(region->offset, region->size, &end)) {
        return -1;
    }

    if (end > size) {
        return -1;
    }

    return 0;
}

static int lender_read_bss(handle_t chan, struct lender_region* region) {
    if (lender_check_region(region, PAGE_SIZE) != 0) {
        return -1;
    }

    int rc = tipc_send1(chan, &bss_page[region->offset], region->size);
    if (rc != (int)region->size) {
        return -1;
    }

    return 0;
}

static int lender_write_bss(handle_t chan,
                            struct lender_region* region,
                            const char* data) {
    if (lender_check_region(region, PAGE_SIZE) != 0) {
        return -1;
    }

    memcpy(&bss_page[region->offset], data, region->size);

    return tipc_send1(chan, NULL, 0);
}

static int lender_on_message(const struct tipc_port* port,
                             handle_t chan,
                             void* ctx) {
    assert(port == &lender_port);
    assert(ctx == NULL);
    struct lender_msg msg;
    char data[MAX_WRITE];

    int rc = tipc_recv2(chan, sizeof(msg), &msg, sizeof(msg), data,
                        sizeof(data));
    if (rc < 0) {
        TLOGE("Failed to receive message (%d)\n", rc);
        return rc;
    }

    switch (msg.cmd) {
    case LENDER_LEND_BSS:
    case LENDER_LEND_RW:
        rc = lender_lend(chan, msg.cmd);
        if (rc < 0) {
            return rc;
        }
        break;
    case LENDER_SUICIDE:
        exit(0);
        break;
    case LENDER_READ_BSS:
        rc = lender_read_bss(chan, &msg.region);
        if (rc < 0) {
            return rc;
        }
        break;
    case LENDER_WRITE_BSS:
        if (rc - sizeof(struct lender_msg) != msg.region.size) {
            return -1;
        }
        rc = lender_write_bss(chan, &msg.region, data);
        if (rc < 0) {
            return rc;
        }
        break;
    default:
        TLOGE("Bad command: %d\n", msg.cmd);
        return -1;
    }

    return 0;
}

static struct tipc_srv_ops lender_ops = {
        .on_message = lender_on_message,
};

int main(void) {
    init();

    struct tipc_hset* hset = tipc_hset_create();

    if (!hset) {
        return -1;
    }

    int rc = tipc_add_service(hset, &lender_port, 1, 1, &lender_ops);
    if (rc < 0) {
        return rc;
    }

    rc = tipc_run_event_loop(hset);
    TLOGE("lender going down: (%d)\n", rc);
    return rc;
}
