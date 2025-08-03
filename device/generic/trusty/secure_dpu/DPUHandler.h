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

#pragma once

#include "trusty/secure_dpu/SecureDpu.h"
#include <android-base/result.h>
#include <string>
#include <trusty/tipc.h>

#include <BufferAllocator/BufferAllocatorWrapper.h>

namespace android {
namespace trusty {
namespace secure_dpu {

class DPUHandler {
  private:
    static constexpr const int kInvalidFd = -1;

    int dpu_handle_;
    BufferAllocator* buf_allocator_;

    android::base::Result<void> HandleStartSecureDisplay();
    android::base::Result<void> HandleStopSecureDisplay();
    android::base::Result<void> AllocateBuffer(size_t req_buffer_len, size_t* allocated_buffer_len,
                                               int* buf_fd);
    android::base::Result<void> HandleAllocateBuffer(const secure_dpu_allocate_buffer_req* req);
    android::base::Result<void> HandleCmd(const void* in_buf, const size_t in_size);

  public:
    DPUHandler();
    ~DPUHandler();
    android::base::Result<void> Init(std::string device_name);
    android::base::Result<void> Handle();
};

}  // namespace secure_dpu
}  // namespace trusty
}  // namespace android
