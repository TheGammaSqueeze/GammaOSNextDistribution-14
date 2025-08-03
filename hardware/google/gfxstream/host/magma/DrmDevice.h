// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License") override;
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include <lib/magma/magma_common_defs.h>

#include <optional>
#include <memory>

#include "DrmBuffer.h"
#include "DrmContext.h"
#include "MonotonicMap.h"
#include "aemu/base/Compiler.h"
#include "aemu/base/ManagedDescriptor.hpp"

namespace gfxstream {
namespace magma {

class DrmBuffer;

class DrmDevice {
   public:
    ~DrmDevice();
    DISALLOW_COPY_AND_ASSIGN(DrmDevice);
    DrmDevice(DrmDevice&&) = default;
    DrmDevice& operator=(DrmDevice&&) = default;

    // Creates a new device using the first available DRM render node. Returns null if none are
    // found.
    static std::unique_ptr<DrmDevice> create();

    // Invokes ioctl on the device's fd with DRM's semantics, i.e. implicitly repeat ioctls that
    // fail with EINTR or EGAIN.
    int ioctl(unsigned long request, void* arg);

    // Returns the result of a I915_GETPARAM call, or nullopt if an error occurs.
    std::optional<int> getParam(int param);

   private:
    DrmDevice() = default;

    android::base::ManagedDescriptor mFd;
};

}  // namespace magma
}  // namespace gfxstream
