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

#include <scs_test_app_consts.h>
#include <stdbool.h>
#include <trusty_ipc.h>
#include <trusty_log.h>

#define TLOG_TAG "userscs-test-app"

/*
 * This app doesn't do anything. We simply use it to test the
 * shadow call stack size from the userscstest kernel app.
 */
int main(void) {
    int rc;
    uevent_t uevt;
    handle_t handle;

    rc = port_create(PORT_NAME, 1, 1, IPC_PORT_ALLOW_TA_CONNECT);
    if (rc < 0) {
        TLOGE("failed (%d) to create port for userscs test app\n", rc);
        return rc;
    }
    handle = (handle_t)rc;

    /* wait infinitely long absent any messages */
    rc = wait(handle, &uevt, INFINITE_TIME);

    return 1; /* shouldn't get here */
}
