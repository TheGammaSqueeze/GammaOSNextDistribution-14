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

#include <atomic>
#include <memory>
#include <unordered_set>

#include "DrmDevice.h"
#include "aemu/base/Compiler.h"
#include "aemu/base/ManagedDescriptor.hpp"

namespace gfxstream {
namespace magma {

class DrmDevice;

// Wraps a linux DRM (GEM) buffer.
class DrmBuffer {
   public:
    ~DrmBuffer();
    DISALLOW_COPY_AND_ASSIGN(DrmBuffer);
    DrmBuffer(DrmBuffer&&) noexcept;
    DrmBuffer& operator=(DrmBuffer&&) = delete;

    // Creates a new buffer using the provided device. The device must remain valid for the lifetime
    // of the buffer.
    static std::unique_ptr<DrmBuffer> create(DrmDevice& device, uint32_t context_id, uint64_t size);

    // Returns the gem handle for the buffer.
    uint32_t getHandle();

    // Returns the allocated size of the buffer.
    uint64_t size();

    // Returns the host address for the mapped buffer.
    void* map();

    // Returns the host-guest shared buffer ID.
    uint64_t getId();

   private:
    DrmBuffer(DrmDevice& device);

    DrmDevice& mDevice;
    uint32_t mContextId;
    uint32_t mGemHandle;
    uint64_t mSize;

    static std::atomic_uint64_t mIdNext;
    void* mHva = nullptr;
    uint64_t mId = 0;
};

}  // namespace magma
}  // namespace gfxstream
