//
// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#ifndef UPDATE_ENGINE_LZ4DIFF_LZ4DIFF_FORMAT_H_
#define UPDATE_ENGINE_LZ4DIFF_LZ4DIFF_FORMAT_H_

#include <string_view>
#include <vector>

#include <lz4diff/lz4diff.pb.h>

namespace chromeos_update_engine {

using Blob = std::vector<unsigned char>;

// Format of LZ4diff patch:
// struct lz4diff_header {
//     char magic[8] = kLz4diffMagic;
//     uint32_t version;
//     uint32_t pb_header_size;         // size of protobuf message
//     char pf_header[pb_header_size];
// }

constexpr std::string_view kLz4diffMagic = "LZ4DIFF";

// 8 bytes magic + 4 bytes version + 4 bytes pb_header_size
constexpr size_t kLz4diffHeaderSize = 8 + 4 + 4;

constexpr uint32_t kLz4diffVersion = 1;

struct CompressedBlock {
  constexpr CompressedBlock() : CompressedBlock(0, 0, 0) {}
  constexpr CompressedBlock(uint64_t offset,
                            uint64_t length,
                            uint64_t uncompressed_length)
      : uncompressed_offset(offset),
        compressed_length(length),
        uncompressed_length(uncompressed_length) {}
  constexpr bool IsCompressed() const noexcept {
    return compressed_length < uncompressed_length;
  }
  uint64_t uncompressed_offset;
  uint64_t compressed_length;
  uint64_t uncompressed_length;
};

struct CompressedFile {
  // Extents in this array should be in range [0, file_size]. It represents
  // which bytes inside this file are compressed. Useful for compressed file
  // systems like EROFS.
  std::vector<CompressedBlock> blocks;
  CompressionAlgorithm algo;
  // Whether the EROFS zero padding feature is enabled
  bool zero_padding_enabled{};
};

}  // namespace chromeos_update_engine

#endif
