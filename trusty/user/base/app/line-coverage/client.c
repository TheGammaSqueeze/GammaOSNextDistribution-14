/*
 * Copyright (C) 2023 The Android Open Source Project
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

#define TLOG_TAG "coverage-client-srv"

#include "coverage.h"

#include <interface/line-coverage/client.h>
#include <lib/coverage/common/ipc.h>
#include <lib/coverage/common/cov_shm.h>
#include <lib/tipc/tipc_srv.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <trusty_log.h>
#include <uapi/err.h>
#include <lk/list.h>

struct chan_ctx {
    struct coverage_record* record;
    struct list_node* last_sent_node;
};

static void signal_event(struct cov_shm* mailbox, size_t ta_idx, int event) {
    int* app_mailbox = (int*)(mailbox->base) + ta_idx;
    WRITE_ONCE(*app_mailbox, event);
}

static int handle_send_list(handle_t chan,
                       struct line_coverage_client_req* req,
                       struct list_node* coverage_record_list,
                       struct chan_ctx* ctx) {
    int rc;
    uevent_t evt;
    handle_t memref;
    struct line_coverage_client_resp resp;
    struct coverage_record* cur_record;
    memset(&resp, 0, sizeof(struct line_coverage_client_resp));
    struct uuid zero_uuid = {0, 0, 0, { 0 }};
    struct list_node* cur_node = list_next(coverage_record_list, ctx->last_sent_node);

    while(cur_node != NULL) {
        resp.hdr.cmd = LINE_COVERAGE_CLIENT_CMD_SEND_LIST | LINE_COVERAGE_CLIENT_CMD_RESP_BIT;
        cur_record = containerof(cur_node, struct coverage_record, node);
        resp.send_list_args.uuid = cur_record->uuid;

        rc = coverage_send(chan, &resp, sizeof(resp), NULL);
        if (rc != NO_ERROR) {
            TLOGE("failed (%d) to send to list elements\n", rc);
            return rc;
        }

        rc = wait(chan, &evt, INFINITE_TIME);
        rc = coverage_recv(chan, &req, sizeof(req), &memref);
        if (rc != NO_ERROR) {
            TLOGE("failed (%d) to receive response\n", rc);
            return rc;
        }
        ctx->last_sent_node = cur_node;
        cur_node = list_next(coverage_record_list, cur_node);
    }

    resp.hdr.cmd = LINE_COVERAGE_CLIENT_CMD_SEND_LIST | LINE_COVERAGE_CLIENT_CMD_RESP_BIT;
    resp.send_list_args.uuid = zero_uuid;
    rc = coverage_send(chan, &resp, sizeof(resp), NULL);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to send end of list\n", rc);
        return rc;
    }
    return NO_ERROR;
}

static int handle_open(handle_t chan,
                       struct line_coverage_client_req* req,
                       struct list_node* coverage_record_list,
                       struct chan_ctx* ctx) {
    int rc;
    struct line_coverage_client_resp resp;
    struct coverage_record* record;
    char uuid_str[UUID_STR_SIZE];

    uuid_to_str(&req->open_args.uuid, uuid_str);

    record = find_coverage_record(coverage_record_list, &req->open_args.uuid);
    if (!record) {
        TLOGE("coverage record not found for uuid: %s\n", uuid_str);
        return ERR_NOT_FOUND;
    }

    memset(&resp, 0, sizeof(struct line_coverage_client_resp));
    resp.hdr.cmd = req->hdr.cmd | LINE_COVERAGE_CLIENT_CMD_RESP_BIT;
    resp.open_args.record_len = record->record_len;
    rc = coverage_send(chan, &resp, sizeof(resp), NULL);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to reply to open request\n", rc);
        return rc;
    }

    ctx->record = record;
    return NO_ERROR;
}

static int handle_share_record(handle_t chan,
                               struct line_coverage_client_req* req,
                               struct coverage_record* record,
                               handle_t memref,
                               struct cov_shm* mailbox) {
    int rc;
    struct line_coverage_client_resp resp;

    if (memref == INVALID_IPC_HANDLE) {
        TLOGE("invalid memref");
        return ERR_BAD_LEN;
    }
    memset(&resp, 0, sizeof(struct line_coverage_client_resp));
    resp.hdr.cmd = req->hdr.cmd | LINE_COVERAGE_CLIENT_CMD_RESP_BIT;
    rc = coverage_send(chan, &resp, sizeof(resp), NULL);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to reply to share record request\n", rc);
        return rc;
    }

    cov_shm_init(&record->data, memref, NULL, req->share_record_args.shm_len);

    signal_event(mailbox, record->ta_idx, COVERAGE_MAILBOX_RECORD_READY);

    return NO_ERROR;
}

static int on_connect(const struct tipc_port* port,
                      handle_t chan,
                      const struct uuid* peer,
                      void** ctx_p) {
    struct chan_ctx* ctx = calloc(1, sizeof(*ctx));
    if (!ctx) {
        TLOGE("failed to allocate channel context\n");
        return ERR_NO_MEMORY;
    }

    struct srv_state* state = get_srv_state(port);
    ctx->record = NULL;
    ctx->last_sent_node = &state->coverage_record_list;
    *ctx_p = ctx;
    return NO_ERROR;
}

static int on_message(const struct tipc_port* port, handle_t chan, void* _ctx) {
    int rc;
    handle_t memref;
    struct line_coverage_client_req req;
    struct chan_ctx* ctx = (struct chan_ctx*)_ctx;
    struct srv_state* state = get_srv_state(port);

    rc = coverage_recv(chan, &req, sizeof(req), &memref);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to receive coverage client request\n", rc);
        return rc;
    }

    switch (req.hdr.cmd) {
    case LINE_COVERAGE_CLIENT_CMD_SEND_LIST:
        return handle_send_list(chan, &req, &state->coverage_record_list, ctx);

    case LINE_COVERAGE_CLIENT_CMD_OPEN:
        return handle_open(chan, &req, &state->coverage_record_list, ctx);

    case LINE_COVERAGE_CLIENT_CMD_SHARE_RECORD:
        return handle_share_record(chan, &req, ctx->record, memref,
                                   &state->mailbox);

    default:
        TLOGE("command 0x%x: unknown command\n", req.hdr.cmd);
        return ERR_CMD_UNKNOWN;
    }
}

static void on_channel_cleanup(void* _ctx) {
    struct chan_ctx* ctx = (struct chan_ctx*)_ctx;
    free(ctx);
}

int coverage_client_init(struct srv_state* state) {
    static struct tipc_port_acl port_acl = {
            .flags = IPC_PORT_ALLOW_TA_CONNECT | IPC_PORT_ALLOW_NS_CONNECT,
    };
    static struct tipc_port port = {
            .name = LINE_COVERAGE_CLIENT_PORT,
            .msg_max_size = MAX(sizeof(struct line_coverage_client_req),
                                sizeof(struct line_coverage_client_resp)),
            .msg_queue_len = 1,
            .acl = &port_acl,
    };
    static struct tipc_srv_ops ops = {
            .on_connect = on_connect,
            .on_message = on_message,
            .on_channel_cleanup = on_channel_cleanup,
    };
    set_srv_state(&port, state);

    return tipc_add_service(state->hset, &port, 1, 1, &ops);
}
