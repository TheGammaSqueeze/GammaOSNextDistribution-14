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

#define TLOG_TAG "receiver"

#include <assert.h>

#include <inttypes.h>
#include <lk/err_ptr.h>
#include <lk/macros.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/auxv.h>
#include <sys/mman.h>

#include <lib/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>
#include <lib/unittest/unittest.h>

#include <trusty/sys/mman.h>
#include <trusty_unittest.h>

/* Number of pages to expect from NS */
static const size_t num_pages = 10;

static struct tipc_port_acl receiver_port_acl = {
        .flags = IPC_PORT_ALLOW_NS_CONNECT,
        .uuid_num = 0,
        .uuids = NULL,
        .extra_data = NULL,
};

static struct tipc_port receiver_port = {
        .name = "com.android.trusty.memref.receiver",
        .msg_max_size = 1,
        .msg_queue_len = 1,
        .acl = &receiver_port_acl,
        .priv = NULL,
};

static int receiver_on_message(const struct tipc_port* port,
                               handle_t chan,
                               void* ctx) {
    assert(port == &receiver_port);
    assert(ctx == NULL);
    handle_t handle;
    int rc;

    struct ipc_msg msg = {
            .iov = NULL,
            .num_iov = 0,
            .handles = &handle,
            .num_handles = 1,
    };

    struct ipc_msg_info msg_inf;
    rc = get_msg(chan, &msg_inf);
    if (rc) {
        return rc;
    }

    if (msg_inf.num_handles != 1) {
        TLOGE("Message had no handles\n");
        return -1;
    }

    rc = read_msg(chan, msg_inf.id, 0, &msg);
    put_msg(chan, msg_inf.id);
    if (rc < 0) {
        TLOGE("Failed to read message\n");
        return rc;
    }

    size_t page_size = getauxval(AT_PAGESZ);

    char* out = mmap(0, page_size * num_pages, PROT_READ | PROT_WRITE, 0,
                     handle, 0);
    if (out == MAP_FAILED) {
        rc = (intptr_t)out;
        TLOGE("Failed to mmap handle\n");
        return rc;
    }

    for (size_t skip = 0; skip < num_pages; skip++) {
        strcpy(&out[skip * page_size], "Hello from Trusty!");
    }

    munmap((void*)out, page_size * num_pages);

    close(handle);

    // Send a message for sync
    char c = 0;
    tipc_send1(chan, &c, 1);

    return rc;
}

static struct tipc_srv_ops receiver_ops = {
        .on_message = receiver_on_message,
};

int main(void) {
    struct tipc_hset* hset = tipc_hset_create();

    if (IS_ERR(hset)) {
        return PTR_ERR(hset);
    }

    int rc = tipc_add_service(hset, &receiver_port, 1, 1, &receiver_ops);
    if (rc < 0) {
        return rc;
    }

    rc = tipc_run_event_loop(hset);
    TLOGE("receiver going down: (%d)\n", rc);
    return rc;
}
