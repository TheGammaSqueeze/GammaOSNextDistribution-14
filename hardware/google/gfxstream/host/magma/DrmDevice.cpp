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

#include "host/magma/DrmDevice.h"

#include <errno.h>
#include <fcntl.h>
#include <i915_drm.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <cstring>
#include <string>

#include "host-common/logging.h"
#include "magma/magma_common_defs.h"

namespace gfxstream {
namespace magma {

// Returns a fd to the first available DRM render node, or -1 if none is found.
static int openFirstRenderNode() {
    constexpr std::string_view kRenderNodePathPrefix = "/dev/dri/renderD";
    constexpr uint32_t kRenderNodeStart = 128;
    constexpr uint32_t kDrmMaxMinor = 15;
    for (uint32_t n = kRenderNodeStart; n < kRenderNodeStart + kDrmMaxMinor; ++n) {
        auto path = std::string(kRenderNodePathPrefix) + std::to_string(n);
        int fd = open(path.c_str(), O_RDWR | O_CLOEXEC);
        if (fd < 0) {
            if (errno != ENOENT) {
                // ENOENT is expected because we're trying all potentially valid paths, but other
                // errors should be logged.
                WARN("render node %s exists but could not be opened - (%d) %s", path.c_str(), errno,
                     strerror(errno));
            }
            continue;
        }
        INFO("opened render node %s", path.c_str());
        return fd;
    }
    return -1;
}

DrmDevice::~DrmDevice() {}

std::unique_ptr<DrmDevice> DrmDevice::create() {
    auto fd = openFirstRenderNode();
    if (fd == -1) {
        ERR("failed to find any render nodes");
        return nullptr;
    }

    std::unique_ptr<DrmDevice> device(new DrmDevice);
    device->mFd = fd;
    return device;
}

int DrmDevice::ioctl(unsigned long request, void* arg) {
    int ret = 0;
    do {
        ret = ::ioctl(mFd.get().value(), request, arg);
    } while (ret == -1 && (errno == EINTR || errno == EAGAIN));
    return ret;
}

std::optional<int> DrmDevice::getParam(int param) {
    int value = 0;
    drm_i915_getparam_t params{.param = param, .value = &value};
    int result = ioctl(DRM_IOCTL_I915_GETPARAM, &params);
    if (result != 0) {
        ERR("DrmDevice::GetParam(%d) failed: (%d) %s", param, errno, strerror(errno));
        return std::nullopt;
    }
    return value;
}

}  // namespace magma
}  // namespace gfxstream
