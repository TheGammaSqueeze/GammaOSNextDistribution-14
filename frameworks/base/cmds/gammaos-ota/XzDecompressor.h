/*
 * Copyright (C) 2026 GammaOS
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

#ifndef GAMMAOS_OTA_XZ_DECOMPRESSOR_H
#define GAMMAOS_OTA_XZ_DECOMPRESSOR_H

#include <string>
#include <functional>

namespace android {

// Progress callback: (bytes_written, total_expected) -> void
using ProgressCallback = std::function<void(uint64_t bytesWritten, uint64_t totalExpected)>;

class XzDecompressor {
public:
    // Decompress an .img.xz file and write directly to a block device.
    // Returns true on success.
    static bool decompressToBlock(const std::string& xzPath,
                                  const std::string& blockDevPath,
                                  uint64_t expectedSize,
                                  ProgressCallback progress = nullptr);

    // Decompress an .img.xz file to a regular file on disk.
    // Returns true on success. Optional expectedSize + progress for UI feedback.
    static bool decompressToFile(const std::string& xzPath,
                                 const std::string& outPath,
                                 uint64_t expectedSize = 0,
                                 ProgressCallback progress = nullptr);

    // Write a raw image file to a block device.
    // Returns true on success. Optional progress callback.
    static bool writeFileToBlock(const std::string& filePath,
                                  const std::string& blockDevPath,
                                  uint64_t size,
                                  ProgressCallback progress = nullptr);

    // Compute SHA-256 of a file (for pre-flash verification of .img.xz)
    static std::string sha256File(const std::string& path);

    // Compute SHA-256 by reading from a block device (for post-flash verification)
    static std::string sha256BlockDev(const std::string& blockDevPath, uint64_t size);
};

} // namespace android

#endif // GAMMAOS_OTA_XZ_DECOMPRESSOR_H
