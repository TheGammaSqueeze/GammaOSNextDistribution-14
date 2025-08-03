/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define TLOG_TAG "boot_done_client"

#include <assert.h>
#include <lib/tipc/tipc.h>
#include <string.h>
#include <trusty_log.h>
#include <uapi/err.h>

#include <BpBootDone.h>
#include <lib/tidl/lk_strerror.h>

/**
 * boot_done_connect()
 * @param boot_done reference to the proxy instance
 *
 * Return: 0 on success, or an error code < 0 on failure.
 */
int boot_done_connect(BpBootDone& boot_done) {
    int rc;
    rc = boot_done.connect(IBootDone::PORT, IPC_CONNECT_WAIT_FOR_PORT);
    if (rc < 0) {
        TLOGE("Failed to connect to %s: %d\n", IBootDone::PORT, rc);
        return rc;
    }
    return rc;
}

/**
 * boot_done_set_boot_done()
 *
 * @return: 0 on success, or an error code < 0 on failure.
 */
extern "C" int boot_done_set_boot_done(void) {
    int rc;
    BpBootDone boot_done;
    rc = boot_done_connect(boot_done);
    if (rc < 0) {
        return rc;
    }

    rc = boot_done.set_boot_done();
    if (rc != NO_ERROR) {
        TLOGE("set_boot_done failed - %s(%d).\n", lk_strerror(rc), rc);
        return rc;
    }
    return rc;
}
