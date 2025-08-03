/*
 * Copyright 2020 The Android Open Source Project
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

#define LOG_TAG "ImageHashManager"

#include "ImageHashManager.h"
#include <log/log_main.h>
#include "pHash/phash_config.h"
#include "pHash/phash_fingerprinter.h"

#include <errno.h>

namespace android {

int32_t ImageHashManager::generatePHash(const uint8_t* buffer, int32_t width, int32_t height,
                                        int32_t stride, std::array<uint8_t, 8>* outImageHash) {
    if (width != kImageLength || height != kImageLength) {
        ALOGE("Failed to generate phash algorithm. Incorrect buffer size=%dx%d where %d is "
              "expected for width and height",
              width, height, kImageLength);
        return -EINVAL;
    }

    // Phash algorithm expects 1 byte per pixel instead of 4. Because the image is in grayscale,
    // RGB should all be the same and alpha should be 0xFF. The code below is compressing the full
    // buffer into a buffer that has 1 byte per pixel.
    std::array<uint8_t, kImageSize> compressedBuffer;
    size_t index = 0;

    for (int32_t j = 0; j < height; j++) {
        const uint8_t* src = buffer + (stride * j) * 4;
        for (int32_t i = 0; i < width; i++) {
            uint8_t r = *src;
            uint8_t g = *(src + 1);
            uint8_t b = *(src + 2);
            if (r != g || r != b) {
                ALOGE("Failed to generate phash algorithm. Buffer is not in grayscale. r=%02X "
                      "g=%02X b=%02X",
                      r, g, b);
                return -EINVAL;
            }

            compressedBuffer[index] = *src;
            src += 4;
            index++;
        }
    }

    PhashFingerprinter worker;
    const int64_t result = worker.GenerateFingerprint(compressedBuffer.data());
    *reinterpret_cast<int64_t*>(outImageHash->data()) = result;
    return 0;
}

int32_t ImageHashManager::generateHash(std::string hashAlgorithm, const uint8_t* buf,
                                       AHardwareBuffer_Desc bufferDesc,
                                       std::array<uint8_t, 8>* outImageHash) {
    if (hashAlgorithm == "phash") {
        int32_t stride = bufferDesc.stride > 0 ? bufferDesc.stride : bufferDesc.width;
        return generatePHash(buf, bufferDesc.width, bufferDesc.height, stride, outImageHash);
    }

    ALOGE("Failed to generate hash. Invalid hash algorithm sent %s", hashAlgorithm.c_str());
    return -EINVAL;
}

} // namespace android
