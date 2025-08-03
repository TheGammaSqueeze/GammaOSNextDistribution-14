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

#include "host/magma/Connection.h"

#include <i915_drm.h>
#include <sys/mman.h>

#include <cerrno>
#include <cinttypes>

#include "host-common/logging.h"
#include "host/magma/DrmDevice.h"

namespace gfxstream {
namespace magma {

Connection::Connection(DrmDevice& device) : mDevice(device) {}

DrmDevice& Connection::getDevice() {
    return mDevice;
}

std::optional<uint32_t> Connection::createContext() {
    auto context = DrmContext::create(*this);
    if (!context) {
        ERR("Failed to create context");
        return std::nullopt;
    }

    auto id = context->getId();
    auto [_, emplaced] = mContexts.emplace(id, std::move(*context));
    if (!emplaced) {
        ERR("GEM produced duplicate context ID %" PRIu32, id);
        return std::nullopt;
    }

    return id;
}

DrmContext* Connection::getContext(uint32_t id) {
    auto it = mContexts.find(id);
    if (it == mContexts.end()) {
        return nullptr;
    }
    return &it->second;
}

}  // namespace magma
}  // namespace gfxstream
