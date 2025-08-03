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
#include <assert.h>
#include <lk/err_ptr.h>
#include <stdio.h>
#include <stdlib.h>
#include <uapi/err.h>

#define TLOG_TAG "libtipc"
#include <trusty_log.h>

#include <lib/tipc/tipc.h>

#include "tipc_priv.h"

int tipc_connect(handle_t* handle_p, const char* port) {
    int rc;

    assert(handle_p);

    rc = connect(port, IPC_CONNECT_WAIT_FOR_PORT);
    if (rc < 0)
        return rc;

    *handle_p = (handle_t)rc;
    return 0;
}

/*
 *  Send single buf message
 */
int tipc_send1(handle_t chan, const void* buf, size_t len) {
    struct iovec iov = {
            .iov_base = (void*)buf,
            .iov_len = len,
    };
    ipc_msg_t msg = {
            .iov = &iov,
            .num_iov = 1,
            .handles = NULL,
            .num_handles = 0,
    };
    return send_msg(chan, &msg);
}

/*
 *  Receive single buf message
 */
int tipc_recv1(handle_t chan, size_t min_sz, void* buf, size_t buf_sz) {
    int rc;
    ipc_msg_info_t msg_inf;

    rc = get_msg(chan, &msg_inf);
    if (rc)
        return rc;

    if (msg_inf.len < min_sz || msg_inf.len > buf_sz) {
        /* unexpected msg size: buffer too small or too big */
        rc = ERR_BAD_LEN;
    } else {
        struct iovec iov = {
                .iov_base = buf,
                .iov_len = buf_sz,
        };
        ipc_msg_t msg = {
                .iov = &iov,
                .num_iov = 1,
                .handles = NULL,
                .num_handles = 0,
        };
        rc = read_msg(chan, msg_inf.id, 0, &msg);
    }

    put_msg(chan, msg_inf.id);
    return rc;
}

/*
 * Send message consisting of two segments (header and payload)
 */
int tipc_send2(handle_t chan,
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
            .handles = NULL,
            .num_handles = 0,
    };
    return send_msg(chan, &msg);
}

/*
 * Receive message consisting of two segments.
 */
int tipc_recv2(handle_t chan,
               size_t min_sz,
               void* buf1,
               size_t buf1_sz,
               void* buf2,
               size_t buf2_sz) {
    int rc;
    ipc_msg_info_t msg_inf;

    rc = get_msg(chan, &msg_inf);
    if (rc)
        return rc;

    if (msg_inf.len < min_sz || (msg_inf.len > (buf1_sz + buf2_sz))) {
        /* unexpected msg size: buffer too small or too big */
        rc = ERR_BAD_LEN;
    } else {
        struct iovec iovs[2] = {
                {
                        .iov_base = buf1,
                        .iov_len = buf1_sz,
                },
                {
                        .iov_base = buf2,
                        .iov_len = buf2_sz,
                },
        };
        ipc_msg_t msg = {
                .iov = iovs,
                .num_iov = countof(iovs),
                .handles = NULL,
                .num_handles = 0,
        };
        rc = read_msg(chan, msg_inf.id, 0, &msg);
    }

    put_msg(chan, msg_inf.id);
    return rc;
}

/*
 * Handle common unexpected port events
 */
void tipc_handle_port_errors(const struct uevent* ev) {
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
void tipc_handle_chan_errors(const struct uevent* ev) {
    if ((ev->event & IPC_HANDLE_POLL_ERROR) ||
        (ev->event & IPC_HANDLE_POLL_READY)) {
        /* should never happen for channel handles */
        TLOGE("error event (0x%x) for chan (%d)\n", ev->event, ev->handle);
        abort();
    }
}

/*
 * Initialize an existing tipc_hset
 */
int tipc_hset_init(struct tipc_hset* hset) {
    int rc;

    assert(!IS_ERR(hset) && hset);

    hset->handle = INVALID_IPC_HANDLE;

    rc = handle_set_create();
    if (rc < 0)
        return rc;

    hset->handle = (handle_t)rc;
    return 0;
}

/*
 * Allocate and initialize new handle set structure
 */
struct tipc_hset* tipc_hset_create(void) {
    struct tipc_hset* hset;

    hset = malloc(sizeof(struct tipc_hset));
    if (!hset)
        return (void*)(uintptr_t)(ERR_NO_MEMORY);

    int rc = tipc_hset_init(hset);
    if (rc < 0) {
        free(hset);
        return (void*)(uintptr_t)(rc);
    }

    return hset;
}

/*
 * Add handle to handle set
 */
int tipc_hset_add_entry(struct tipc_hset* hset,
                        handle_t handle,
                        uint32_t evt_mask,
                        struct tipc_event_handler* evt_handler) {
    struct uevent uevt = {
            .handle = handle,
            .event = evt_mask,
            .cookie = (void*)evt_handler,
    };

    if (IS_ERR(hset) || !hset || !evt_handler)
        return ERR_INVALID_ARGS;

    assert(evt_handler->proc);

    /* attach new entry */
    return handle_set_ctrl(hset->handle, HSET_ADD, &uevt);
}

/*
 * Modify handle set entry
 */
int tipc_hset_mod_entry(struct tipc_hset* hset,
                        handle_t handle,
                        uint32_t evt_mask,
                        struct tipc_event_handler* evt_handler) {
    struct uevent uevt = {
            .handle = handle,
            .event = evt_mask,
            .cookie = (void*)evt_handler,
    };

    if (IS_ERR(hset) || !hset || !evt_handler)
        return ERR_INVALID_ARGS;

    assert(evt_handler->proc);

    /* modify entry */
    return handle_set_ctrl(hset->handle, HSET_MOD, &uevt);
}

/*
 * Remove handle from handle set
 */
int tipc_hset_remove_entry(struct tipc_hset* hset, handle_t h) {
    struct uevent uevt = {
            .handle = h,
            .event = 0,
            .cookie = NULL,
    };

    if (IS_ERR(hset) || !hset)
        return ERR_INVALID_ARGS;

    /* detach entry */
    return handle_set_ctrl(hset->handle, HSET_DEL, &uevt);
}

int tipc_handle_event(struct tipc_hset* hset, uint32_t timeout) {
    int rc;
    struct uevent evt = UEVENT_INITIAL_VALUE(evt);

    if (IS_ERR(hset) || !hset)
        return ERR_INVALID_ARGS;

    /* wait for next event up to specified time */
    rc = wait(hset->handle, &evt, timeout);
    if (rc < 0)
        return rc;

    /* get handler */
    struct tipc_event_handler* handler = evt.cookie;

    /* invoke it */
    handler->proc(&evt, handler->priv);

    return 0;
}

int tipc_run_event_loop(struct tipc_hset* hset) {
    int rc;

    do {
        rc = tipc_handle_event(hset, INFINITE_TIME);
    } while (rc == 0);

    return rc;
}
