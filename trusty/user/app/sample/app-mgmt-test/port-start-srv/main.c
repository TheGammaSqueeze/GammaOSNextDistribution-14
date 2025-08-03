/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include <assert.h>
#include <lib/tipc/tipc.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <trusty/time.h>
#include <trusty_ipc.h>
#include <uapi/err.h>

#define TLOG_TAG "port-start-srv"

/* Handles a cmd from the client and returns true if the server should exit */
static bool handle_cmd(uint8_t cmd, handle_t channel, handle_t* start_port) {
    int rc;
    bool done = false;
    uint8_t rsp = RSP_OK;

    switch (cmd) {
    case CMD_NOP:
        break;
    case CMD_CLOSE_PORT:
        rc = close(*start_port);
        if (rc != NO_ERROR) {
            TLOGI("port close failed: %d\n", rc);
            rsp = RSP_CMD_FAILED;
            done = true;
        }
        break;
    case CMD_OPEN_PORT:
        rc = port_create(START_PORT, 1, MAX_CMD_LEN, IPC_PORT_ALLOW_TA_CONNECT);
        if (rc < 0) {
            TLOGI("failed (%d) to create port: %s\n", rc, START_PORT);
            rsp = RSP_CMD_FAILED;
            done = true;
        } else {
            *start_port = (handle_t)rc;
        }
        break;
    case CMD_EXIT:
        done = true;
        break;
    default:
        TLOGI("Invalid cmd: %d\n", cmd);
        rsp = RSP_INVALID_CMD;
        done = true;
    }

    rc = tipc_send1(channel, &rsp, sizeof(rsp));
    if (rc < 0) {
        TLOGI("Failed to send response: %d \n", rc);
        done = true;
    }

    return done;
}

/* Creates port_name and adds it to hset */
static int prepare_port(const char* port_name, handle_t hset) {
    int rc;
    uevent_t uevt;
    handle_t port;

    assert(port_name);
    assert(hset);

    rc = port_create(port_name, 1, MAX_CMD_LEN, IPC_PORT_ALLOW_TA_CONNECT);
    if (rc < 0) {
        TLOGI("failed (%d) to create port: %s\n", rc, port_name);
        return rc;
    }

    port = (handle_t)rc;

    uevt.handle = port;
    uevt.event = ~0U;
    uevt.cookie = NULL;

    rc = handle_set_ctrl(hset, HSET_ADD, &uevt);
    if (rc < 0) {
        TLOGI("failed (%d) to add port %s to handle set \n", rc, port_name);
        close(port);
        return rc;
    }

    return port;
}

int main(void) {
    int rc;
    uint8_t cmd;
    bool done = false;
    handle_t ctrl_port;
    handle_t start_port;
    handle_t shutdown_port;
    handle_t channel;
    handle_t port_hset;
    handle_t mixed_hset;
    uevent_t uevt;
    uuid_t peer_uuid;

    rc = handle_set_create();
    if (rc < 0) {
        TLOGI("failed (%d) to create port handle set \n", rc);
        return rc;
    }
    port_hset = (handle_t)rc;

    rc = prepare_port(START_PORT, port_hset);
    if (rc < 0) {
        TLOGI("failed(%d) to prepare START_PORT\n", rc);
        goto err_prep_start;
    }
    start_port = (handle_t)rc;

    rc = prepare_port(CTRL_PORT, port_hset);
    if (rc < 0) {
        TLOGI("failed(%d) to prepare CTRL_PORT\n", rc);
        goto err_prep_ctrl;
    }
    ctrl_port = (handle_t)rc;

    rc = handle_set_create();
    if (rc < 0) {
        TLOGI("failed (%d) to create mixed handle set \n", rc);
        goto err_hset_create;
    }
    mixed_hset = (handle_t)rc;

    rc = prepare_port(SHUTDOWN_PORT, mixed_hset);
    if (rc < 0) {
        TLOGI("failed(%d) to prepare CTRL_PORT\n", rc);
        goto err_prep_shutdown;
    }
    shutdown_port = (handle_t)rc;

    rc = wait(port_hset, &uevt, INFINITE_TIME);
    if (rc != NO_ERROR || !(uevt.event & IPC_HANDLE_POLL_READY)) {
        TLOGI("Port wait failed: %d(%d)\n", rc, uevt.event);
        goto err_port_wait;
    }

    rc = accept(uevt.handle, &peer_uuid);
    if (rc < 0) {
        TLOGI("Accept failed %d\n", rc);
        goto err_accept;
    }
    channel = (handle_t)rc;

    uevt.handle = channel;
    uevt.event = ~0U;
    uevt.cookie = NULL;

    rc = handle_set_ctrl(mixed_hset, HSET_ADD, &uevt);
    if (rc < 0) {
        TLOGI("failed (%d) to add channel to mixed handle set \n", rc);
        goto err_hset_ctrl;
    }

    while (!done) {
        rc = wait(mixed_hset, &uevt, INFINITE_TIME);
        if (rc < 0) {
            TLOGI("Channel wait failed: %d\n", uevt.event);
            goto err_channel_wait;
        }

        if (uevt.handle == shutdown_port)
            break;

        if (!(uevt.event & IPC_HANDLE_POLL_MSG)) {
            TLOGI("Received unexpected event %d\n", uevt.event);
            goto err_evt;
        }

        rc = tipc_recv1(channel, sizeof(cmd), &cmd, sizeof(cmd));
        if (rc < 0) {
            TLOGI("recv_cmd failed: %d\n", rc);
            goto err_recv;
        }
        rc = 0;

        done = handle_cmd(cmd, channel, &start_port);
    }

err_recv:
err_evt:
err_channel_wait:
err_hset_ctrl:
    close(channel);
err_accept:
err_port_wait:
    close(shutdown_port);
err_prep_shutdown:
    close(mixed_hset);
err_hset_create:
    close(ctrl_port);
err_prep_ctrl:
    close(start_port);
err_prep_start:
    close(port_hset);

    return rc;
}
