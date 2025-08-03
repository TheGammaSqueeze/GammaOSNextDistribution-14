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

#define TLOG_TAG "hwcrypto_srv"

#include <assert.h>
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <uapi/err.h>

#include <hwcrypto/hwrng_dev.h>
#include <lib/tipc/tipc.h>
#include <trusty_log.h>

#include "hwkey_srv_priv.h"
#include "hwrng_srv_priv.h"

#include "keybox/srv.h"

/*
 *  Dispatch event
 */
static void dispatch_event(const uevent_t* ev) {
    assert(ev);

    if (ev->event == IPC_HANDLE_POLL_NONE) {
        /* not really an event, do nothing */
        TLOGI("got an empty event\n");
        return;
    }

    /* check if we have handler */
    struct tipc_event_handler* handler = ev->cookie;
    if (handler && handler->proc) {
        /* invoke it */
        handler->proc(ev, handler->priv);
        return;
    }

    /* no handler? close it */
    TLOGE("no handler for event (0x%x) with handle %d\n", ev->event,
          ev->handle);

    close(ev->handle);

    return;
}

/*
 *  Main application event loop
 */
int main(void) {
    int rc;
    uevent_t event;

    TLOGD("Initializing\n");

    /* initialize service providers */
    rc = hwrng_start_service();
    if (rc != NO_ERROR) {
        TLOGE("Failed (%d) to initialize HWRNG service\n", rc);
        goto out;
    }
    hwkey_init_srv_provider();

    rc = keybox_start_service();
    if (rc != NO_ERROR) {
        TLOGE("Failed (%d) to initialize Keybox service\n", rc);
        goto out;
    }

    TLOGD("enter main event loop\n");

    /* enter main event loop */
    while (1) {
        event.handle = INVALID_IPC_HANDLE;
        event.event = 0;
        event.cookie = NULL;

        rc = wait_any(&event, INFINITE_TIME);
        if (rc < 0) {
            TLOGE("wait_any failed (%d)\n", rc);
            break;
        }

        if (rc == NO_ERROR) { /* got an event */
            dispatch_event(&event);
        }
    }

out:
    return rc;
}
