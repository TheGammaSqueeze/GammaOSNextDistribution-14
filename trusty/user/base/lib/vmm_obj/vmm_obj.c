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

#define TLOG_TAG "vmm_obj_ipc"

#include <inttypes.h>
#include <lib/tipc/tipc.h>
#include <lib/vmm_obj/vmm_obj.h>
#include <lk/macros.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <trusty_log.h>
#include <uapi/err.h>
#include <uapi/mm.h>

#define PAGE_SIZE getauxval(AT_PAGESZ)

int vmm_obj_map_ro(const char* port, const void** base_out, size_t* size_out) {
    int rc;
    handle_t chan;
    handle_t memref = INVALID_IPC_HANDLE;
    ipc_msg_info_t msg_inf;
    uevent_t evt;
    uint64_t size64;
    size_t size;
    void* base;

    if (!base_out) {
        TLOGE("Unexpected NULL base pointer\n");
        rc = ERR_INVALID_ARGS;
        goto err_null_base_out;
    }
    if (!size_out) {
        TLOGE("Unexpected NULL size pointer\n");
        rc = ERR_INVALID_ARGS;
        goto err_null_size_out;
    }

    rc = tipc_connect(&chan, port);
    if (rc < 0) {
        goto err_connect;
    }

    do {
        rc = wait(chan, &evt, INFINITE_TIME);
        if (rc != NO_ERROR) {
            TLOGE("Failed to wait for reply (%d)\n", rc);
            goto err_wait;
        }
        if (evt.event & IPC_HANDLE_POLL_HUP) {
            TLOGE("Service closed connection\n");
            rc = ERR_CHANNEL_CLOSED;
            goto err_wait;
        }
    } while (!(evt.event & IPC_HANDLE_POLL_MSG));

    rc = get_msg(chan, &msg_inf);
    if (rc) {
        TLOGE("Failed to get message (%d)\n", rc);
        goto err_get;
    }

    if (msg_inf.len != sizeof(size64)) {
        TLOGE("Received message of invalid size (%zd)\n", msg_inf.len);
        rc = ERR_BAD_LEN;
        goto err_msg_len;
    }

    struct iovec iov = {
            .iov_base = &size64,
            .iov_len = sizeof(size64),
    };
    ipc_msg_t msg = {
            .iov = &iov,
            .num_iov = 1,
            .handles = &memref,
            .num_handles = 1,
    };
    rc = read_msg(chan, msg_inf.id, 0, &msg);
    if (rc != (int)sizeof(size64)) {
        TLOGE("Failed to read message (%d)\n", rc);
        if (rc >= 0) {
            rc = ERR_BAD_LEN;
        }
        goto err_read;
    }

    if (memref == INVALID_IPC_HANDLE) {
        TLOGE("Received invalid memref handle\n");
        rc = ERR_BAD_HANDLE;
        goto err_invalid_memref;
    }

    size = (size_t)size64;
    if (size64 != (uint64_t)size) {
        TLOGE("Size too big for size_t (%" PRIu64 ")\n", size64);
        rc = ERR_TOO_BIG;
        goto err_size_too_big;
    }

    size_t aligned_size = round_up(size, PAGE_SIZE);
    base = mmap(0, aligned_size, MMAP_FLAG_PROT_READ, 0, memref, 0);
    if (base == MAP_FAILED) {
        TLOGE("Failed to map device tree blob\n");
        rc = ERR_BAD_HANDLE;
        goto err_mmap;
    }

    *base_out = base;
    *size_out = size;
    rc = NO_ERROR;

err_mmap:
err_size_too_big:
err_invalid_memref:
err_read:
    close(memref);
err_msg_len:
    put_msg(chan, msg_inf.id);
err_get:
err_wait:
    close(chan);
err_connect:
err_null_size_out:
err_null_base_out:
    return rc;
}
