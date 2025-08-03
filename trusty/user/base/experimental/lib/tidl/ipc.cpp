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

#include <lib/tidl/android-base/unique_fd.h>
#include <trusty_ipc.h>
#include <uapi/err.h>

namespace tidl {
namespace ipc {

int connect(const char* path,
            uint32_t flags,
            android::base::unique_fd& out_fd) {
    int rc = ::connect(path, flags);
    if (rc < 0) {
        return rc;
    }

    out_fd.reset(static_cast<handle_t>(rc));
    return NO_ERROR;
}

int send(handle_t chan,
         const void* buf,
         size_t len,
         handle_t* handles,
         uint32_t num_handles) {
    struct iovec iov = {
            .iov_base = (void*)buf,
            .iov_len = len,
    };
    ipc_msg_t msg = {
            .num_iov = 1,
            .iov = &iov,
            .num_handles = num_handles,
            .handles = handles,
    };
    return send_msg(chan, &msg);
}

int recv(handle_t chan,
         size_t min_sz,
         void* buf,
         size_t buf_sz,
         handle_t* handles,
         uint32_t num_handles) {
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
                .num_iov = 1,
                .iov = &iov,
                .num_handles = num_handles,
                .handles = handles,
        };
        rc = read_msg(chan, msg_inf.id, 0, &msg);
    }

    put_msg(chan, msg_inf.id);
    return rc;
}

int send(handle_t chan,
         const void* hdr,
         size_t hdr_len,
         const void* payload1,
         size_t payload1_len,
         handle_t* handles,
         uint32_t num_handles) {
    struct iovec iovs[] = {
            {
                    .iov_base = (void*)hdr,
                    .iov_len = hdr_len,
            },
            {
                    .iov_base = (void*)payload1,
                    .iov_len = payload1_len,
            },
    };
    ipc_msg_t msg = {
            .num_iov = static_cast<uint32_t>(countof(iovs)),
            .iov = iovs,
            .num_handles = num_handles,
            .handles = handles,
    };
    return send_msg(chan, &msg);
}

int recv(handle_t chan,
         size_t min_sz,
         void* buf1,
         size_t buf1_sz,
         void* buf2,
         size_t buf2_sz,
         handle_t* handles,
         uint32_t num_handles) {
    int rc;
    ipc_msg_info_t msg_inf;

    rc = get_msg(chan, &msg_inf);
    if (rc)
        return rc;

    if (msg_inf.len < min_sz || (msg_inf.len > (buf1_sz + buf2_sz))) {
        /* unexpected msg size: buffer too small or too big */
        rc = ERR_BAD_LEN;
    } else {
        struct iovec iovs[] = {
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
                .num_iov = static_cast<uint32_t>(countof(iovs)),
                .iov = iovs,
                .num_handles = num_handles,
                .handles = handles,
        };
        rc = read_msg(chan, msg_inf.id, 0, &msg);
    }

    put_msg(chan, msg_inf.id);
    return rc;
}

int send(handle_t chan,
         const void* hdr,
         size_t hdr_len,
         const void* payload1,
         size_t payload1_len,
         const void* payload2,
         size_t payload2_len,
         handle_t* handles,
         uint32_t num_handles) {
    struct iovec iovs[] = {
            {
                    .iov_base = (void*)hdr,
                    .iov_len = hdr_len,
            },
            {
                    .iov_base = (void*)payload1,
                    .iov_len = payload1_len,
            },
            {
                    .iov_base = (void*)payload2,
                    .iov_len = payload2_len,
            },
    };
    ipc_msg_t msg = {
            .num_iov = static_cast<uint32_t>(countof(iovs)),
            .iov = iovs,
            .num_handles = num_handles,
            .handles = handles,
    };
    return send_msg(chan, &msg);
}

int recv(handle_t chan,
         size_t min_sz,
         void* buf1,
         size_t buf1_sz,
         void* buf2,
         size_t buf2_sz,
         void* buf3,
         size_t buf3_sz,
         handle_t* handles,
         uint32_t num_handles) {
    int rc;
    ipc_msg_info_t msg_inf;

    rc = get_msg(chan, &msg_inf);
    if (rc)
        return rc;

    if (msg_inf.len < min_sz || (msg_inf.len > (buf1_sz + buf2_sz + buf3_sz))) {
        /* unexpected msg size: buffer too small or too big */
        rc = ERR_BAD_LEN;
    } else {
        struct iovec iovs[] = {
                {
                        .iov_base = buf1,
                        .iov_len = buf1_sz,
                },
                {
                        .iov_base = buf2,
                        .iov_len = buf2_sz,
                },
                {
                        .iov_base = buf3,
                        .iov_len = buf3_sz,
                },
        };
        ipc_msg_t msg = {
                .num_iov = static_cast<uint32_t>(countof(iovs)),
                .iov = iovs,
                .num_handles = num_handles,
                .handles = handles,
        };
        rc = read_msg(chan, msg_inf.id, 0, &msg);
    }

    put_msg(chan, msg_inf.id);
    return rc;
}

int wait_for_msg(handle_t chan) {
    uevent_t event = UEVENT_INITIAL_VALUE(event);
    int rc = wait(chan, &event, INFINITE_TIME);
    if (rc != NO_ERROR) {
        return rc;
    }

    if (event.event & IPC_HANDLE_POLL_HUP) {
        return ERR_CHANNEL_CLOSED;
    }
    if (!(event.event & IPC_HANDLE_POLL_MSG)) {
        return ERR_IO;
    }

    return NO_ERROR;
}

}  // namespace ipc
}  // namespace tidl
