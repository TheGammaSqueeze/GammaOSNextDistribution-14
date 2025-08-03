// Copyright 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expresso or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "BufferVk.h"

#include "VkCommonOperations.h"

namespace gfxstream {
namespace vk {

/*static*/
std::unique_ptr<BufferVk> BufferVk::create(uint32_t handle, uint64_t size, bool vulkanOnly) {
    if (!setupVkBuffer(size, handle, vulkanOnly, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) {
        ERR("Failed to create BufferVk:%d", handle);
        return nullptr;
    }

    return std::unique_ptr<BufferVk>(new BufferVk(handle));
}

BufferVk::BufferVk(uint32_t handle) : mHandle(handle) {}

BufferVk::~BufferVk() {
    if (!teardownVkBuffer(mHandle)) {
        ERR("Failed to destroy BufferVk:%d", mHandle);
    }
}

void BufferVk::readToBytes(uint64_t offset, uint64_t size, void* outBytes) {
    readBufferToBytes(mHandle, offset, size, outBytes);
}

bool BufferVk::updateFromBytes(uint64_t offset, uint64_t size, const void* bytes) {
    return updateBufferFromBytes(mHandle, offset, size, bytes);
}

}  // namespace vk
}  // namespace gfxstream