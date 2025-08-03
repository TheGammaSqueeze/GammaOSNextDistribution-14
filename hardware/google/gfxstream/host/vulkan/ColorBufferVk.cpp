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

#include "ColorBufferVk.h"

#include "VkCommonOperations.h"

namespace gfxstream {
namespace vk {

/*static*/
std::unique_ptr<ColorBufferVk> ColorBufferVk::create(uint32_t handle, uint32_t width,
                                                     uint32_t height, GLenum format,
                                                     FrameworkFormat frameworkFormat,
                                                     bool vulkanOnly, uint32_t memoryProperty) {
    if (!createVkColorBuffer(width, height, format, frameworkFormat, handle, vulkanOnly,
                             memoryProperty)) {
        GL_LOG("Failed to create ColorBufferVk:%d", handle);
        return nullptr;
    }

    return std::unique_ptr<ColorBufferVk>(new ColorBufferVk(handle));
}

ColorBufferVk::ColorBufferVk(uint32_t handle) : mHandle(handle) {}

ColorBufferVk::~ColorBufferVk() {
    if (!teardownVkColorBuffer(mHandle)) {
        ERR("Failed to destroy ColorBufferVk:%d", mHandle);
    }
}

bool ColorBufferVk::readToBytes(std::vector<uint8_t>* outBytes) {
    return readColorBufferToBytes(mHandle, outBytes);
}

bool ColorBufferVk::readToBytes(uint32_t x, uint32_t y, uint32_t w, uint32_t h, void* outBytes) {
    return readColorBufferToBytes(mHandle, x, y, w, h, outBytes);
}

bool ColorBufferVk::updateFromBytes(const std::vector<uint8_t>& bytes) {
    return updateColorBufferFromBytes(mHandle, bytes);
}

bool ColorBufferVk::updateFromBytes(uint32_t x, uint32_t y, uint32_t w, uint32_t h,
                                    const void* bytes) {
    return updateColorBufferFromBytes(mHandle, x, y, w, h, bytes);
}

bool ColorBufferVk::importExtMemoryHandle(void* nativeResource, uint32_t type,
                                          bool preserveContent) {
    // TODO: Any need to support preserveContent?
    assert(!preserveContent);
    VK_EXT_MEMORY_HANDLE extMemoryHandle =
        *reinterpret_cast<VK_EXT_MEMORY_HANDLE*>(&nativeResource);
    return importExtMemoryHandleToVkColorBuffer(mHandle, type, extMemoryHandle);
}

}  // namespace vk
}  // namespace gfxstream
