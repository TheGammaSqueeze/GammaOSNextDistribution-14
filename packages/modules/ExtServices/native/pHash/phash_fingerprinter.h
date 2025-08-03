/*
 * Copyright 2021 The Android Open Source Project
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

#include <memory>

#include "phash_config.h"

namespace android {

// Class that generates a perceptual hash of an ImageFrame.
// Not thread-safe.
class PhashFingerprinter {
public:
    // Produce the fingerprint of an image frame, which must be 32 by 32
    // grayscale of 1 byte per pixel.
    int64_t GenerateFingerprint(const uint8_t* frame);

private:
    // Buffers to avoid memory allocation:
    int16_t dct_buf_[kImageSize];
};
} // namespace android
