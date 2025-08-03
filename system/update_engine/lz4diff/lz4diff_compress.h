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

#ifndef UPDATE_ENGINE_LZ4DIFF_LZ4DIFF_COMPRESS_H_
#define UPDATE_ENGINE_LZ4DIFF_LZ4DIFF_COMPRESS_H_

#include "lz4diff_format.h"
#include <string_view>

namespace chromeos_update_engine {

using SinkFunc = std::function<size_t(const uint8_t*, size_t)>;

// |TryCompressBlob| and |TryDecompressBlob| are inverse function of each other.
// One compresses data into fixed size output chunks, one decompresses fixed
// size blocks.
// The |TryCompressBlob| routine is supposed to mimic how EROFS compresses input
// files when creating an EROFS image. After calling |TryCompressBlob|, LZ4DIFF
// will compare the re-compressed blob and EROFS's ground truth blob, and
// generate a BSDIFF patch between them if there's mismatch. Therefore, it is OK
// that |TryCompressBlob| produces slightly different output than mkfs.erofs, so
// as long as |TryCompressBlob| exhibits consistne bebavior across platforms.
Blob TryCompressBlob(std::string_view blob,
                     const std::vector<CompressedBlock>& block_info,
                     const bool zero_padding_enabled,
                     const CompressionAlgorithm compression_algo);
bool TryCompressBlob(std::string_view blob,
                     const std::vector<CompressedBlock>& block_info,
                     const bool zero_padding_enabled,
                     const CompressionAlgorithm compression_algo,
                     const SinkFunc& sink);

Blob TryDecompressBlob(std::string_view blob,
                       const std::vector<CompressedBlock>& block_info,
                       const bool zero_padding_enabled);
Blob TryDecompressBlob(const Blob& blob,
                       const std::vector<CompressedBlock>& block_info,
                       const bool zero_padding_enabled);

std::ostream& operator<<(std::ostream& out, const CompressedBlockInfo& info);

std::ostream& operator<<(std::ostream& out, const CompressedBlock& block);

}  // namespace chromeos_update_engine

#endif
