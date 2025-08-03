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

#include <app_mgmt_port_consts.h>
#include <app_mgmt_test.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <trusty_ipc.h>
#include <uapi/err.h>

#define TLOG_TAG "never-start-srv"

int main(void) {
    int rc;
    handle_t phandle;
    handle_t chandle;
    uevent_t uevt;

    TLOGE("ERROR: Starting server that should never start\n");

    rc = port_create(NEVER_START_PORT, 1, 1, IPC_PORT_ALLOW_TA_CONNECT);
    if (rc < 0) {
        TLOGI("failed (%d) to create ctrl port\n", rc);
        return rc;
    }
    phandle = (handle_t)rc;

    rc = wait(phandle, &uevt, -1);
    if (rc != NO_ERROR || !(uevt.event & IPC_HANDLE_POLL_READY)) {
        TLOGI("Port wait failed(%d) event:%d handle:%d\n", rc, uevt.event,
              phandle);
        return rc;
    }

    chandle = (handle_t)rc;

    rc = close(chandle);
    if (rc < 0) {
        TLOGI("Close failed(%d) handle:%d\n", rc, chandle);
        return rc;
    }

    return 0;
}
