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

#define TLOG_TAG "metrics-test-crasher"

#include <lib/tipc/tipc_srv.h>
#include <lk/err_ptr.h>
#include <metrics_test_crasher_consts.h>
#include <stdlib.h>
#include <trusty_log.h>
#include <uapi/err.h>

static struct tipc_port_acl crasher_port_acl = {
        .flags = IPC_PORT_ALLOW_NS_CONNECT,
};

static struct tipc_port crasher_port = {
        .name = METRICS_TEST_CRAHSER_PORT,
        .msg_max_size = 1024,
        .msg_queue_len = 1,
        .acl = &crasher_port_acl,
};

static int on_message(const struct tipc_port* port, handle_t chan, void* ctx) {
    /* Exit on any message received. */
    exit(EXIT_FAILURE);
    return 0;
}

static struct tipc_srv_ops crasher_ops = {
        .on_message = on_message,
};

int main(void) {
    int rc;
    struct tipc_hset* hset;

    hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        return PTR_ERR(hset);
    }

    rc = tipc_add_service(hset, &crasher_port, 1, 0, &crasher_ops);
    if (rc != NO_ERROR) {
        return rc;
    }

    return tipc_run_event_loop(hset);
}
