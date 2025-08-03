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

#include <GLES2/gl2.h>

#include <memory>
#include <vector>

#include "FrameworkFormats.h"

namespace gfxstream {
namespace vk {

class ColorBufferVk {
   public:
    static std::unique_ptr<ColorBufferVk> create(uint32_t handle, uint32_t width, uint32_t height,
                                                 GLenum format, FrameworkFormat frameworkFormat,
                                                 bool vulkanOnly, uint32_t memoryProperty);

    ~ColorBufferVk();

    bool readToBytes(std::vector<uint8_t>* outBytes);
    bool readToBytes(uint32_t x, uint32_t y, uint32_t w, uint32_t h, void* outBytes);

    bool updateFromBytes(const std::vector<uint8_t>& bytes);
    bool updateFromBytes(uint32_t x, uint32_t y, uint32_t w, uint32_t h, const void* bytes);

    bool importExtMemoryHandle(void* nativeResource, uint32_t type, bool preserveContent);

   private:
    ColorBufferVk(uint32_t handle);

    const uint32_t mHandle;
};

}  // namespace vk
}  // namespace gfxstream
