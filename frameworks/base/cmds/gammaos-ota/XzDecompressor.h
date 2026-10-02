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

#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace android {

// Progress callback: (bytes_written, total_expected) -> void
using ProgressCallback = std::function<void(uint64_t bytesWritten, uint64_t totalExpected)>;

// A run of bytes on a device: where it starts and how long it is.
struct ByteRange {
    std::string device;
    uint64_t offset;
    uint64_t length;
};

class XzDecompressor {
public:
    // Decompress an .img.xz file to a regular file on disk, and make it durable (fsync).
    // The output must come out at exactly expectedSize bytes when that is given.
    // Returns true on success. Optional progress for UI feedback.
    static bool decompressToFile(const std::string& xzPath,
                                 const std::string& outPath,
                                 uint64_t expectedSize = 0,
                                 ProgressCallback progress = nullptr);

    // Write the first `size` bytes of a raw image file to a block device and flush it to the
    // medium. Every write, the final fsync and close are checked: a write the device reports as
    // failed at any point fails the call. When sentSha is given it receives the SHA-256 of the
    // bytes actually sent, so a caller can tell a bad source file from a bad write.
    static bool writeFileToBlock(const std::string& filePath,
                                 const std::string& blockDevPath,
                                 uint64_t size,
                                 ProgressCallback progress = nullptr,
                                 std::string* sentSha = nullptr);

    // Write the first `size` bytes of a file across a list of device ranges, in order (a logical
    // partition's extents on super). Same checks as writeFileToBlock.
    static bool writeFileToRanges(const std::string& filePath,
                                  const std::vector<ByteRange>& ranges,
                                  uint64_t size,
                                  ProgressCallback progress = nullptr,
                                  std::string* sentSha = nullptr);

    // SHA-256 of a file. With size > 0 exactly that many bytes are hashed and a shorter file is an
    // error. The page cache is dropped first and as it goes, so the bytes come from the medium
    // and a multi-GB file does not crowd the page cache. Returns "" on any read error.
    static std::string sha256File(const std::string& path, uint64_t size = 0,
                                  ProgressCallback progress = nullptr);

    // SHA-256 of the first `size` bytes of a block device, read past the page cache (O_DIRECT,
    // or buffered with the cache flushed when the device refuses O_DIRECT). Returns "" on any
    // read error or short read, never a hash of partial data.
    static std::string sha256BlockDev(const std::string& blockDevPath, uint64_t size,
                                      ProgressCallback progress = nullptr);

    // SHA-256 of `size` bytes gathered from a list of ranges, in order, read past the page cache.
    // Used to read a logical partition through the super metadata the way init will map it.
    static std::string sha256Ranges(const std::vector<ByteRange>& ranges, uint64_t size,
                                    ProgressCallback progress = nullptr);
};

} // namespace android

#endif // GAMMAOS_OTA_XZ_DECOMPRESSOR_H
