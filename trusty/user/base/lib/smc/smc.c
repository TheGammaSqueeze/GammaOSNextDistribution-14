/*
 * Copyright (C) 2019 The Android Open Source Project
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

#define TLOG_TAG "smc-ipc"

#include <lib/smc/smc_ipc.h>
#include <lib/tipc/tipc.h>
#include <trusty_log.h>
#include <uapi/err.h>

int smc_read_response(handle_t channel, struct smc_msg* msg) {
    int rc;
    uevent_t event;
    int32_t err;
    size_t msg_len = sizeof(struct smc_msg);
    size_t resp_len = sizeof(struct smc_response);
    STATIC_ASSERT(sizeof(struct smc_response) ==
                  sizeof(struct smc_msg) + sizeof(err));

    rc = wait(channel, &event, INFINITE_TIME);
    if (rc != NO_ERROR) {
        TLOGD("%s: failed (%d) waiting for response\n", __func__, rc);
        goto err;
    }

    rc = tipc_recv2(channel, msg_len, msg, msg_len, &err, sizeof(err));
    if (rc != (int)resp_len) {
        TLOGD("%s: failed (%d) to read message. Expected to read %zu bytes.\n",
              __func__, rc, msg_len);
        if (rc >= 0)
            rc = ERR_BAD_LEN;
    } else if (err != NO_ERROR) {
        TLOGE("%s: smc call failed (%d).\n", __func__, rc);
        rc = err;
    } else {
        rc = msg_len; /* success - return number of bytes in smc_msg */
    }

err:
    return rc;
}

int smc_send_request(handle_t channel, struct smc_msg* msg) {
    size_t msg_len = sizeof(struct smc_msg);
    int rc = tipc_send1(channel, msg, msg_len);
    if (rc != (int)msg_len) {
        TLOGD("%s: failed (%d) to send message. Expected to send %zu bytes.\n",
              __func__, rc, msg_len);
        if (rc >= 0)
            rc = ERR_BAD_LEN;
    }
    return rc;
}
