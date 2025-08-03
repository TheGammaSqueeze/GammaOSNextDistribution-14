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

#include <app_mgmt_port_consts.h>
#include <app_mgmt_test.h>
#include <inttypes.h>
#include <lib/tipc/tipc.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <trusty_ipc.h>
#include <uapi/err.h>

#define TLOG_TAG "port-waiter-srv"

int main(void) {
    int rc;
    handle_t shandle = INVALID_IPC_HANDLE;
    handle_t phandle = INVALID_IPC_HANDLE;
    handle_t chandle = INVALID_IPC_HANDLE;
    struct uevent uevt;
    uuid_t peer_uuid;

    rc = connect(START_PORT, IPC_CONNECT_WAIT_FOR_PORT);
    if (rc < 0) {
        TLOGI("Failed (%d) to connect to start port\n", rc);
        goto err_connect;
    }
    shandle = (handle_t)rc;

    rc = port_create(PORT_WAITER_PORT, 1, 1, IPC_PORT_ALLOW_TA_CONNECT);
    if (rc < 0) {
        TLOGI("Failed (%d) to create port-waiter port\n", rc);
        goto err_port_create;
    }
    phandle = (handle_t)rc;

    rc = wait(phandle, &uevt, INFINITE_TIME);
    if (rc != NO_ERROR || !(uevt.event & IPC_HANDLE_POLL_READY)) {
        TLOGI("Port wait failed(%d) event:%d handle:%d\n", rc, uevt.event,
              phandle);
        goto err_wait_accept;
    }

    rc = accept(uevt.handle, &peer_uuid);
    if (rc == ERR_CHANNEL_CLOSED) {
        /* client already closed connection, nothing to do */
        goto err_channel_closed;
    }
    if (rc < 0) {
        TLOGI("Accept failed %d\n", rc);
        goto err_accept;
    }

    chandle = (handle_t)rc;

    uint8_t cmd = CMD_EXIT;
    rc = tipc_send1(shandle, &cmd, sizeof(cmd));
    if (rc != (int)sizeof(cmd)) {
        TLOGI("Failed (%d) to send exit command\n", rc);
        goto err_send_cmd;
    }

    rc = wait(shandle, &uevt, INFINITE_TIME);
    if (rc != NO_ERROR || !(uevt.event & IPC_HANDLE_POLL_MSG)) {
        TLOGI("Port wait failed(%d) event:%d handle:%d\n", rc, uevt.event,
              shandle);
        goto err_wait_resp;
    }

    uint8_t rsp;
    rc = tipc_recv1(shandle, sizeof(rsp), &rsp, sizeof(rsp));
    if (rc != (int)sizeof(rsp)) {
        TLOGI("Failed (%d) to receive exit response\n", rc);
        goto err_recv_resp;
    }

    TLOGI("Received exit response: %" PRIu8 "\n", rsp);

    rc = tipc_send1(chandle, &rsp, sizeof(rsp));
    if (rc != (int)sizeof(rsp)) {
        TLOGI("Failed (%d) to send exit response\n", rc);
        goto err_send_resp;
    }

    close(chandle);
    close(phandle);
    close(shandle);

    return 0;

err_send_resp:
err_recv_resp:
err_wait_resp:
err_send_cmd:
    close(chandle);
err_accept:
err_channel_closed:
err_wait_accept:
    close(phandle);
err_port_create:
    close(shandle);
err_connect:
    return rc;
}
