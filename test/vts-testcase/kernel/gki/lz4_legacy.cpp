/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "lz4_legacy.h"

#include <fcntl.h>
#include <sys/stat.h>

#include <iostream>
#include <vector>

#include <android-base/file.h>
#include <android-base/result.h>
#include <android-base/unique_fd.h>
#include <lz4.h>
#include <storage_literals/storage_literals.h>

using android::base::ErrnoError;
using android::base::Error;
using android::base::ReadFully;
using android::base::unique_fd;
using android::base::WriteFully;
using android::storage_literals::operator""_MiB;

namespace android {

android::base::Result<void> Lz4DecompressLegacy(const char* input,
                                                const char* output) {
  constexpr uint32_t lz4_legacy_magic = 0x184C2102;
  constexpr auto lz4_legacy_block_size = 8_MiB;

  struct stat st_buf {};
  if (stat(input, &st_buf) != 0) {
    return ErrnoError() << "stat(" << input << ")";
  }

  unique_fd ifd(TEMP_FAILURE_RETRY(open(input, O_RDONLY | O_CLOEXEC)));
  if (!ifd.ok()) {
    return ErrnoError() << "open(" << input << ", O_RDONLY)";
  }
  unique_fd ofd(TEMP_FAILURE_RETRY(
      open(output, O_WRONLY | O_CREAT | O_CLOEXEC | O_TRUNC, 0640)));
  if (!ofd.ok()) {
    return ErrnoError() << "open(" << output << ", O_WRONLY | O_CREAT)";
  }

  uint32_t magic{};
  if (!ReadFully(ifd, &magic, sizeof(magic))) {
    return ErrnoError() << "read lz4 magic " << input;
  }
  // Android is little-endian. No need to convert magic.
  if (magic != lz4_legacy_magic) {
    return Error() << "magic is " << std::hex << magic << " but expected "
                   << lz4_legacy_magic;
  }

  std::vector<char> ibuf(LZ4_compressBound(lz4_legacy_block_size));
  std::vector<char> obuf(lz4_legacy_block_size);

  while (true) {
    uint32_t block_size{};
    ssize_t read_bytes =
        TEMP_FAILURE_RETRY(read(ifd.get(), &block_size, sizeof(block_size)));
    if (read_bytes == 0) break;
    if (read_bytes < 0) {
      return ErrnoError() << "read block size";
    }
    if (static_cast<size_t>(read_bytes) != sizeof(block_size)) {
      return Error() << "Cannot read " << sizeof(block_size)
                     << " bytes for block size";
    }

    // Android is little-endian. No need to convert block_size.
    if (block_size == lz4_legacy_magic) {
      return Error() << "Found another lz4 compressed stream";
    }
    if (block_size > ibuf.size()) {
      return Error() << "Block size is " << block_size
                     << " but compress bound is " << ibuf.size();
    }

    if (!ReadFully(ifd, ibuf.data(), block_size)) {
      return ErrnoError() << "read " << block_size << " bytes";
    }

    int decoded_size =
        LZ4_decompress_safe(ibuf.data(), obuf.data(), block_size, obuf.size());
    if (decoded_size < 0) {
      return Error() << "LZ4_decompress_safe returns " << decoded_size;
    }

    if (!WriteFully(ofd, obuf.data(), decoded_size)) {
      return ErrnoError() << "write " << decoded_size << " bytes";
    }
  }

  return {};
}
}  // namespace android
