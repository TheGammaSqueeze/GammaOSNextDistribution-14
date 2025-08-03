/*
 * Copyright (C) 2016 The Android Open Source Project
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
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <trusty/time.h>
#include <trusty_ipc.h>
#include <trusty_log.h>
#include <uapi/err.h>

#define TLOG_TAG "spinner-app"
#define TEST_CTRL_PORT "com.android.trusty.spinner"

#define MSEC 1000000ULL

static int _delay(int64_t ns_delay) {
    int rc;
    int64_t curr;
    int64_t start;

    rc = trusty_gettime(0, &start);
    curr = start;
    while (!rc && curr < start + ns_delay)
        rc = trusty_gettime(0, &curr);
    return rc;
}

int main(void) {
    int rc;
    handle_t hport;
    uuid_t peer_uuid;

    TLOGD("Starting spinner test app!!!\n");

    /* create control port and wait on it */
    rc = port_create(TEST_CTRL_PORT, 1, 1024, IPC_PORT_ALLOW_NS_CONNECT);
    if (rc < 0) {
        TLOGI("failed (%d) to create ctrl port\n", rc);
        return rc;
    }
    hport = (handle_t)rc;

    /* and just wait forever on control port  */
    for (;;) {
        uevent_t uevt;
        int rc = wait(hport, &uevt, -1);
        if (rc == NO_ERROR) {
            if (uevt.event & IPC_HANDLE_POLL_READY) {
                /* got connection request */
                rc = accept(uevt.handle, &peer_uuid);
                if (rc >= 0) {
                    handle_t ctrl_chan = (handle_t)rc;

                    trusty_nanosleep(0, 0, 2 * 1000 * MSEC);
                    for (;;) {
                        rc = _delay(2 * 1000 * MSEC);
                        if (rc < 0)
                            break;

                        rc = wait(ctrl_chan, &uevt, 0);
                        if (rc == ERR_CHANNEL_CLOSED) {
                            break;
                        }
                        if (uevt.event & IPC_HANDLE_POLL_HUP) {
                            break;
                        }
                    }
                    close(ctrl_chan);
                    continue;
                }
            }
        }
        if (rc < 0)
            break;
    }
    return rc;
}
