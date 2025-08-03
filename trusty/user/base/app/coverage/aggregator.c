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

#define TLOG_TAG "coverage-aggregator-srv"

#include "coverage.h"

#include <interface/coverage/aggregator.h>
#include <lib/coverage/common/ipc.h>
#include <lib/coverage/common/cov_shm.h>
#include <lib/tipc/tipc_srv.h>
#include <stdlib.h>
#include <trusty_log.h>
#include <uapi/err.h>

static size_t ta_idx = 0;

static int handle_register(handle_t chan,
                           struct coverage_aggregator_req* req,
                           struct coverage_record* record,
                           struct cov_shm* mailbox) {
    int rc;
    struct coverage_aggregator_resp resp;

    resp.hdr.cmd = req->hdr.cmd | COVERAGE_AGGREGATOR_CMD_RESP_BIT;
    resp.register_args.idx = record->idx;
    resp.register_args.mailbox_len = mailbox->len;

    rc = coverage_send(chan, &resp, sizeof(resp), &mailbox->memref);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to send mailbox memref\n", rc);
        return rc;
    }

    record->record_len = req->register_args.record_len;
    return NO_ERROR;
}

static int handle_get_record(handle_t chan,
                             struct coverage_aggregator_req* req,
                             struct coverage_record* record) {
    int rc;
    struct coverage_aggregator_resp resp;

    if (record->data.memref == INVALID_IPC_HANDLE) {
        return ERR_NOT_READY;
    }

    resp.hdr.cmd = req->hdr.cmd | COVERAGE_AGGREGATOR_CMD_RESP_BIT;
    resp.get_record_args.shm_len = record->data.len;

    rc = coverage_send(chan, &resp, sizeof(resp), &record->data.memref);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to send coverage record memref\n", rc);
        return rc;
    }

    return NO_ERROR;
}

static int on_connect(const struct tipc_port* port,
                      handle_t chan,
                      const struct uuid* peer,
                      void** ctx_p) {
    struct coverage_record* record;
    char uuid_str[UUID_STR_SIZE];
    struct srv_state* state = get_srv_state(port);

    uuid_to_str(peer, uuid_str);
    TLOGI("App with UUID: %s connected to coverage aggregator\n", uuid_str);

    record = find_coverage_record(&state->coverage_record_list, peer);
    if (record) {
        *ctx_p = record;
        return NO_ERROR;
    }

    record = calloc(1, sizeof(*record));
    if (!record) {
        TLOGE("failed to allocate coverage record\n");
        return ERR_NO_MEMORY;
    }

    record->uuid = *peer;
    record->idx = ta_idx++;
    list_add_tail(&state->coverage_record_list, &record->node);

    *ctx_p = record;
    return NO_ERROR;
}

static int on_message(const struct tipc_port* port, handle_t chan, void* ctx) {
    int rc;
    struct coverage_aggregator_req req;
    struct coverage_record* record = (struct coverage_record*)ctx;
    struct srv_state* state = get_srv_state(port);

    rc = coverage_recv(chan, &req, sizeof(req), NULL);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to receive coverage aggregator request\n", rc);
        return rc;
    }

    switch (req.hdr.cmd) {
    case COVERAGE_AGGREGATOR_CMD_REGISTER:
        return handle_register(chan, &req, record, &state->mailbox);

    case COVERAGE_AGGREGATOR_CMD_GET_RECORD:
        return handle_get_record(chan, &req, record);

    default:
        TLOGE("cmd 0x%x: unknown command\n", req.hdr.cmd);
        return ERR_CMD_UNKNOWN;
    }
}

/* lib/tipc mandates we have this function. However, there is no work to do. */
static void on_channel_cleanup(void* ctx) {}

int coverage_aggregator_init(struct srv_state* state) {
    static struct tipc_port_acl port_acl = {
            .flags = IPC_PORT_ALLOW_TA_CONNECT,
    };
    static struct tipc_port port = {
            .name = COVERAGE_AGGREGATOR_PORT,
            .msg_max_size = MAX(sizeof(struct coverage_aggregator_req),
                                sizeof(struct coverage_aggregator_resp)),
            .msg_queue_len = 1,
            .acl = &port_acl,
    };
    static struct tipc_srv_ops ops = {
            .on_connect = on_connect,
            .on_message = on_message,
            .on_channel_cleanup = on_channel_cleanup,
    };

    set_srv_state(&port, state);

    return tipc_add_service(state->hset, &port, 1, MAX_NUM_APPS, &ops);
}
