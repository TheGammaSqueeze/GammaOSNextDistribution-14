/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <vector>
#include <stdint.h>
#include <system/graphics.h>

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace yuv {

size_t NV21size(size_t width, size_t height);

android_ycbcr NV21init(size_t width, size_t height, void* data);

android_ycbcr toNV21Shallow(size_t width, size_t height, const android_ycbcr& ycbcr,
                            std::vector<uint8_t>* data);

}  // namespace yuv
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
