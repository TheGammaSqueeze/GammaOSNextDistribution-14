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

#pragma once

#if defined(ANDROID)

#include <cutils/native_handle.h>
#include <stddef.h>
#include <stdint.h>

typedef struct AHardwareBuffer AHardwareBuffer;

namespace gfxstream {

constexpr uint32_t kGlRGB = 0x1907;
constexpr uint32_t kGlRGBA = 0x1908;
constexpr uint32_t kGlRGB565 = 0x8D62;

// Abstraction for gralloc handle conversion
class Gralloc {
   public:
    virtual ~Gralloc() {}

    virtual uint32_t createColorBuffer(void* rcEnc, int width, int height, uint32_t glformat) = 0;

    virtual void acquire(AHardwareBuffer* ahb) = 0;
    virtual void release(AHardwareBuffer* ahb) = 0;

    virtual int allocate(uint32_t width, uint32_t height, uint32_t format, uint64_t usage,
                         AHardwareBuffer** outputAhb) = 0;

    virtual const native_handle_t* getNativeHandle(const AHardwareBuffer* ahb) = 0;

    virtual uint32_t getHostHandle(const native_handle_t* handle) = 0;
    virtual uint32_t getHostHandle(const AHardwareBuffer* handle) = 0;

    virtual int getFormat(const native_handle_t* handle) = 0;
    virtual int getFormat(const AHardwareBuffer* handle) = 0;

    virtual uint32_t getFormatDrmFourcc(const AHardwareBuffer* /*handle*/) {
        // Equal to DRM_FORMAT_INVALID -- see <drm_fourcc.h>
        return 0;
    }
    virtual uint32_t getFormatDrmFourcc(const native_handle_t* /*handle*/) {
        // Equal to DRM_FORMAT_INVALID -- see <drm_fourcc.h>
        return 0;
    }

    virtual size_t getAllocatedSize(const native_handle_t* handle) = 0;
    virtual size_t getAllocatedSize(const AHardwareBuffer* handle) = 0;

    virtual int getId(const AHardwareBuffer* ahb, uint64_t* id) = 0;

    virtual bool treatBlobAsImage() { return false; }
};

Gralloc* createPlatformGralloc(int deviceFd = -1);

}  // namespace gfxstream

#endif  // defined(ANDROID)