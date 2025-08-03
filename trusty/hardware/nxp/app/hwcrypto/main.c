/*
 * Copyright (C) 2016-2017 The Android Open Source Project
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

#include <assert.h>
#include <inttypes.h>
#include <lk/macros.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <trusty_ipc.h>
#include <uapi/err.h>

#include "caam.h"
#include "common.h"
#include "hwkey_srv_priv.h"
#include "hwrng_srv_priv.h"

#define TLOG_TAG "hwcrypto"
#include <trusty_log.h>

/*
 *  Hexdump content of memory region
 */
void _hexdump8(const void* ptr, size_t len) {
    uintptr_t address = (uintptr_t)ptr;
    size_t count;
    size_t i;

    for (count = 0; count < len; count += 16) {
        fprintf(stderr, "0x%08" PRIxPTR ": ", address);
        for (i = 0; i < MIN(len - count, 16); i++) {
            fprintf(stderr, "0x%02hhx ", *(const uint8_t*)(address + i));
        }
        fprintf(stderr, "\n");
        address += 16;
    }
}

/*
 * Handle common unexpected port events
 */
void tipc_handle_port_errors(const uevent_t* ev) {
    if ((ev->event & IPC_HANDLE_POLL_ERROR) ||
        (ev->event & IPC_HANDLE_POLL_HUP) ||
        (ev->event & IPC_HANDLE_POLL_MSG) ||
        (ev->event & IPC_HANDLE_POLL_SEND_UNBLOCKED)) {
        /* should never happen with port handles */
        TLOGE("error event (0x%x) for port (%d)\n", ev->event, ev->handle);
        abort();
    }
}

/*
 * Handle common unexpected channel events
 */
void tipc_handle_chan_errors(const uevent_t* ev) {
    if ((ev->event & IPC_HANDLE_POLL_ERROR) ||
        (ev->event & IPC_HANDLE_POLL_READY)) {
        /* close it as it is in an error state */
        TLOGE("error event (0x%x) for chan (%d)\n", ev->event, ev->handle);
        abort();
    }
}

/*
 *  Send single buf message
 */
int tipc_send_single_buf(handle_t chan, const void* buf, size_t len) {
    struct iovec iov = {
            .iov_base = (void*)buf,
            .iov_len = len,
    };
    ipc_msg_t msg = {
            .iov = &iov,
            .num_iov = 1,

    };
    return send_msg(chan, &msg);
}

/*
 *  Receive single buf message
 */
int tipc_recv_single_buf(handle_t chan, void* buf, size_t len) {
    int rc;
    ipc_msg_info_t msg_inf;

    rc = get_msg(chan, &msg_inf);
    if (rc)
        return rc;

    if (msg_inf.len != len) {
        /* unexpected msg size */
        rc = ERR_BAD_LEN;
    } else {
        struct iovec iov = {
                .iov_base = buf,
                .iov_len = len,
        };
        ipc_msg_t msg = {
                .iov = &iov,
                .num_iov = 1,
        };
        rc = read_msg(chan, msg_inf.id, 0, &msg);
    }

    put_msg(chan, msg_inf.id);
    return rc;
}

/*
 * Send message consisting of two segments (header and payload)
 */
int tipc_send_two_segments(handle_t chan,
                           const void* hdr,
                           size_t hdr_len,
                           const void* payload,
                           size_t payload_len) {
    struct iovec iovs[2] = {
            {
                    .iov_base = (void*)hdr,
                    .iov_len = hdr_len,
            },
            {
                    .iov_base = (void*)payload,
                    .iov_len = payload_len,
            },
    };
    ipc_msg_t msg = {
            .iov = iovs,
            .num_iov = countof(iovs),
    };
    return send_msg(chan, &msg);
}

/*
 * Receive message consisting of two segments (header and payload).
 */
int tipc_recv_two_segments(handle_t chan,
                           void* hdr,
                           size_t hdr_len,
                           void* payload,
                           size_t payload_len) {
    int rc;
    ipc_msg_info_t msg_inf;

    rc = get_msg(chan, &msg_inf);
    if (rc)
        return rc;

    if (msg_inf.len < hdr_len) {
        /* unexpected msg size */
        rc = ERR_BAD_LEN;
    } else {
        struct iovec iovs[2] = {{
                                        .iov_base = hdr,
                                        .iov_len = hdr_len,
                                },
                                {
                                        .iov_base = payload,
                                        .iov_len = payload_len,
                                }};
        ipc_msg_t msg = {
                .iov = iovs,
                .num_iov = countof(iovs),
        };
        rc = read_msg(chan, msg_inf.id, 0, &msg);
    }

    put_msg(chan, msg_inf.id);
    return rc;
}

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

    rc = init_caam_env();
    if (rc != 0) {
        TLOGE("CAAM init env failed (%d)!\n", rc);
        return rc;
    }

    /* initialize service providers */
    hwrng_init_srv_provider();
    hwkey_init_srv_provider();

    TLOGD("enter main event loop\n");

    /* enter main event loop */
    while (true) {
        event.handle = INVALID_IPC_HANDLE;
        event.event = 0;
        event.cookie = NULL;

        rc = wait_any(&event, -1);
        if (rc < 0) {
            TLOGE("wait_any failed (%d)\n", rc);
            break;
        }

        if (rc == NO_ERROR) { /* got an event */
            dispatch_event(&event);
        }
    }

    return rc;
}
