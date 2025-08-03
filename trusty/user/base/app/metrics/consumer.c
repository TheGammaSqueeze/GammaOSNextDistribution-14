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

#define TLOG_TAG "metrics-consumer"
#include "consumer.h"

#include <android/frameworks/stats/atoms.h>
#include <android/trusty/stats/ports.h>
#include <interface/metrics/consumer.h>
#include <lib/tipc/tipc.h>
#include <lib/tipc/tipc_srv.h>
#include <metrics_consts.h>
#include <stddef.h>
#include <string.h>
#include <trusty_log.h>
#include <uapi/err.h>

const char TRUSTY_DOMAIN[] = "google.android.trusty";

static enum metrics_error broadcast_event(uint32_t cmd,
                                          uint8_t* msg,
                                          size_t msg_len) {
    size_t offset = sizeof(struct metrics_req);
    enum metrics_error status = METRICS_NO_ERROR;
    int rc;
    switch (cmd) {
    case METRICS_CMD_REPORT_CRASH: {
        if (msg_len < offset + sizeof(struct metrics_report_crash_req)) {
            TLOGE("metrics message too small: msg_len(%zu)\n", msg_len);
            break;
        }
        struct metrics_report_crash_req* crash_args =
                (struct metrics_report_crash_req*)(msg + offset);
        offset += sizeof(struct metrics_report_crash_req);

        if (msg_len < offset + crash_args->app_id_len) {
            TLOGE("metrics_report_crash message too small: msg_len(%zu)\n",
                  msg_len);
            break;
        }

        char* app_id_ptr = (char*)(msg + offset);
        TLOGD("metrics_report_crash: app_id=\"%.*s\" crash_reason=0x%08x\n",
              crash_args->app_id_len, app_id_ptr, crash_args->crash_reason);
        struct stats_trusty_app_crashed atom = {
                .reverse_domain_name = TRUSTY_DOMAIN,
                .reverse_domain_name_len = sizeof(TRUSTY_DOMAIN) - 1,
                .app_id = app_id_ptr,
                .app_id_len = crash_args->app_id_len,
                .crash_reason = (int32_t)crash_args->crash_reason,
        };
        if ((rc = stats_trusty_app_crashed_report(
                     METRICS_ISTATS_PORT, sizeof(METRICS_ISTATS_PORT), atom)) !=
            NO_ERROR) {
            TLOGE("stats_trusty_app_crashed_report failed (%d)\n", rc);
        }
        break;
    }

    case METRICS_CMD_REPORT_EVENT_DROP: {
        struct stats_trusty_error atom = {
                .reverse_domain_name = TRUSTY_DOMAIN,
                .reverse_domain_name_len = sizeof(TRUSTY_DOMAIN) - 1,
                .error_code = TRUSTY_ERROR_KERNEL_EVENT_DROP,
                .app_id = "",
                .app_id_len = 0L,
                .client_app_id = "",
                .client_app_id_len = 0L,
        };
        if ((rc = stats_trusty_error_report(
                     METRICS_ISTATS_PORT, sizeof(METRICS_ISTATS_PORT), atom)) !=
            NO_ERROR) {
            TLOGE("stats_trusty_error_report failed (%d)\n", rc);
        }
        break;
    }
    default:
        status = METRICS_ERR_UNKNOWN_CMD;
        break;
    }
    return status;
}

static int on_message(const struct tipc_port* port, handle_t chan, void* ctx) {
    int rc;
    struct metrics_req req;
    struct metrics_resp resp;
    uint32_t cmd;
    uint8_t msg[METRICS_MAX_MSG_SIZE];
    size_t msg_len;

    memset(msg, 0, sizeof(msg));
    rc = tipc_recv1(chan, sizeof(req), msg, sizeof(msg));
    if (rc < 0) {
        TLOGE("failed (%d) to receive metrics event\n", rc);
        return rc;
    }
    msg_len = rc;
    cmd = ((struct metrics_req*)msg)->cmd;
    resp.cmd = (cmd | METRICS_CMD_RESP_BIT);
    resp.status = broadcast_event(cmd, msg, msg_len);
    rc = tipc_send1(chan, &resp, sizeof(resp));
    if (rc < 0) {
        TLOGE("failed (%d) to send metrics event response\n", rc);
        return rc;
    }

    if ((size_t)rc != sizeof(resp)) {
        TLOGE("unexpected number of bytes sent: %d\n", rc);
        return ERR_BAD_LEN;
    }

    return NO_ERROR;
}

int add_metrics_consumer_service(struct tipc_hset* hset) {
    static const struct uuid kernel_uuid = UUID_KERNEL_VALUE;
    static const struct uuid* allowed_uuids[] = {
            &kernel_uuid,
    };
    static struct tipc_port_acl port_acl = {
            .flags = IPC_PORT_ALLOW_TA_CONNECT,
            .uuids = allowed_uuids,
            .uuid_num = countof(allowed_uuids),
    };
    static struct tipc_port port = {
            .name = METRICS_CONSUMER_PORT,
            .msg_max_size = METRICS_MAX_MSG_SIZE,
            .msg_queue_len = 1,
            .acl = &port_acl,
    };
    static struct tipc_srv_ops ops = {
            .on_message = on_message,
    };

    return tipc_add_service(hset, &port, 1, 0, &ops);
}
