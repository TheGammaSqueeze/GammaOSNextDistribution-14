// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
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

#include "host/magma/DrmContext.h"

#include <i915_drm.h>
#include <sys/mman.h>

#include <cerrno>
#include <cinttypes>

#include "host-common/logging.h"
#include "host/magma/Connection.h"
#include "host/magma/DrmDevice.h"

namespace gfxstream {
namespace magma {

DrmContext::DrmContext(Connection& connection) : mConnection(connection) {}

DrmContext::DrmContext(DrmContext&& other) noexcept
    : mConnection(other.mConnection), mId(other.mId) {
    other.mId = std::nullopt;
}

DrmContext::~DrmContext() {
    if (!mId) {
        return;
    }
    drm_i915_gem_context_destroy params{.ctx_id = mId.value()};
    int result = mConnection.mDevice.ioctl(DRM_IOCTL_I915_GEM_CONTEXT_DESTROY, &params);
    if (result) {
        ERR("DRM_IOCTL_I915_GEM_CONTEXT_DESTROY(%d) failed: %d", mId.value(), errno);
    }
}

std::unique_ptr<DrmContext> DrmContext::create(Connection& connection) {
    // Create a new GEM context.
    drm_i915_gem_context_create_ext params{};
    int result = connection.mDevice.ioctl(DRM_IOCTL_I915_GEM_CONTEXT_CREATE_EXT, &params);
    if (result) {
        ERR("DRM_IOCTL_I915_GEM_CONTEXT_CREATE_EXT failed: %d", errno);
        return nullptr;
    }

    auto context = std::unique_ptr<DrmContext>(new DrmContext(connection));
    context->mId = params.ctx_id;

    INFO("Created DrmContext id %" PRIu32, context->mId);
    return context;
}

uint32_t DrmContext::getId() { return mId.value(); }

}  // namespace magma
}  // namespace gfxstream
