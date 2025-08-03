/*
 * Copyright 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "DPUHandler.h"
#include "trusty/secure_dpu/SecureDpu.h"

#include <android-base/logging.h>
#include <errno.h>
#include <poll.h>
#include <stdio.h>
#include <sys/stat.h>
#include <sys/uio.h>
#include <unistd.h>
#include <trusty/tipc.h>

#include <BufferAllocator/BufferAllocatorWrapper.h>

#define countof(arr) (sizeof(arr) / sizeof(arr[0]))

namespace android {
namespace trusty {
namespace secure_dpu {

DPUHandler::DPUHandler() : buf_allocator_(nullptr) {}

DPUHandler::~DPUHandler() {
    tipc_close(dpu_handle_);
    if (buf_allocator_) {
        FreeDmabufHeapBufferAllocator(buf_allocator_);
    }
}

android::base::Result<void> DPUHandler::Init(std::string device_name) {
    buf_allocator_ = CreateDmabufHeapBufferAllocator();
    if (!buf_allocator_) {
        return base::Error() << "Failed to create buffer allocator";
    }

    dpu_handle_ = tipc_connect(device_name.c_str(),
                               SECURE_DPU_PORT_NAME);
    if (dpu_handle_ < 0) {
        return base::Error() << "Failed to connect to: " << device_name;
    }
    return {};
}

android::base::Result<void> DPUHandler::HandleStartSecureDisplay() {
    secure_dpu_resp rsp;
    rsp.cmd = SECURE_DPU_CMD_START_SECURE_DISPLAY | SECURE_DPU_CMD_RESP_BIT;
    rsp.status = SECURE_DPU_ERROR_OK;

    auto write_len = write(dpu_handle_, &rsp, sizeof(rsp));
    if (write_len != sizeof(rsp)) {
        return base::Error() << "Failed to write command";
    }
    return {};
}

android::base::Result<void> DPUHandler::HandleStopSecureDisplay() {
    secure_dpu_resp rsp;
    rsp.cmd = SECURE_DPU_CMD_STOP_SECURE_DISPLAY | SECURE_DPU_CMD_RESP_BIT;
    rsp.status = SECURE_DPU_ERROR_OK;

    auto write_len = write(dpu_handle_, &rsp, sizeof(rsp));
    if (write_len != sizeof(rsp)) {
        return base::Error() << "Failed to write command";
    }
    return {};
}

android::base::Result<void> DPUHandler::AllocateBuffer(size_t req_buffer_len,
                                                       size_t* allocated_buffer_len, int* buf_fd) {
    auto dma_buf_fd =
        DmabufHeapAlloc(buf_allocator_, "system", req_buffer_len, 0, 0 /* legacy align */);
    if (dma_buf_fd < 0) {
        return base::Error() << "Failed to allocate buffer."
                             << " rc = " << dma_buf_fd << " size = " << req_buffer_len;
    }

    *buf_fd = dma_buf_fd;
    *allocated_buffer_len = req_buffer_len;
    return {};
}

android::base::Result<void>
DPUHandler::HandleAllocateBuffer(const secure_dpu_allocate_buffer_req* req) {
    size_t req_buffer_len = static_cast<size_t>(req->buffer_len);
    LOG(DEBUG) << "Requested buffer length: " << req_buffer_len;

    secure_dpu_resp rsp;
    secure_dpu_allocate_buffer_resp msg_rsp;

    iovec iov[] = {
        {
            .iov_base = &rsp,
            .iov_len = sizeof(rsp),
        },
        {
            .iov_base = &msg_rsp,
            .iov_len = sizeof(msg_rsp),
        },
    };
    trusty_shm shm;
    size_t allocated_buffer_len = 0;
    int buf_fd = kInvalidFd;
    auto result = AllocateBuffer(req_buffer_len, &allocated_buffer_len, &buf_fd);
    if (result.ok()) {
        rsp.status = SECURE_DPU_ERROR_OK;
    } else {
        LOG(ERROR) << result.error();
        rsp.status = SECURE_DPU_ERROR_FAIL;
    }

    rsp.cmd = SECURE_DPU_CMD_ALLOCATE_BUFFER | SECURE_DPU_CMD_RESP_BIT;

    msg_rsp.buffer_len = allocated_buffer_len;
    shm.fd = buf_fd;
    shm.transfer = TRUSTY_SHARE;

    auto rc = tipc_send(dpu_handle_, iov, countof(iov), &shm, 1);
    if (buf_fd != kInvalidFd) close(buf_fd);
    if (rc != sizeof(rsp) + sizeof(msg_rsp)) {
        return base::Error() << "Failed to do tipc_send: " << rc;
    }
    return {};
}

android::base::Result<void> DPUHandler::HandleCmd(const void* in_buf, const size_t in_size) {
    if (in_size < sizeof(secure_dpu_req)) {
        return base::Error() << "Invalid payload";
    }
    const secure_dpu_req* req = reinterpret_cast<const secure_dpu_req*>(in_buf);
    switch (req->cmd) {
        case SECURE_DPU_CMD_START_SECURE_DISPLAY: {
            return HandleStartSecureDisplay();
        }
        case SECURE_DPU_CMD_STOP_SECURE_DISPLAY: {
            return HandleStopSecureDisplay();
        }
        case SECURE_DPU_CMD_ALLOCATE_BUFFER: {
            if (in_size != sizeof(secure_dpu_req) + sizeof(secure_dpu_allocate_buffer_req)) {
                return base::Error() << "Invalid payload";
            }
            const secure_dpu_allocate_buffer_req* req_args =
                reinterpret_cast<const secure_dpu_allocate_buffer_req*>((uint8_t*)in_buf +
                                                                        sizeof(secure_dpu_req));
            return HandleAllocateBuffer(req_args);
        }
        default:
            LOG(ERROR) << "Unknown command: " << (uint32_t)req->cmd;
            return base::Error() << "Unknown command";
    }
    return {};
}

android::base::Result<void> DPUHandler::Handle() {
    uint8_t in_buf[SECURE_DPU_MAX_MSG_SIZE];

    auto read_len = read(dpu_handle_, in_buf, sizeof(in_buf));
    if (read_len < 0) {
        return base::Error() << "Failed to read command";
    }
    auto result = HandleCmd(in_buf, read_len);
    if (!result.ok()) {
        return base::Error() << "Failed to handle command. "
                             << "Reason: " << result.error();
    }
    return {};
}

}  // namespace secure_dpu
}  // namespace trusty
}  // namespace android
