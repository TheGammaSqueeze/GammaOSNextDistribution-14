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

#include "lz4patch.h"

#include <endian.h>
#include <unistd.h>
#include <fcntl.h>

#include <algorithm>
#include <string_view>

#include <bsdiff/bspatch.h>
#include <bsdiff/memory_file.h>
#include <bsdiff/file.h>
#include <puffin/memory_stream.h>

#include "android-base/strings.h"
#include "lz4diff/lz4diff.h"
#include "lz4diff/lz4diff.pb.h"
#include "lz4diff_compress.h"
#include "lz4diff_format.h"
#include "puffin/puffpatch.h"
#include "update_engine/common/hash_calculator.h"
#include "update_engine/common/utils.h"

namespace chromeos_update_engine {

namespace {

template <typename T>
constexpr void BigEndianToHost(T& t) {
  static_assert(std::is_integral_v<T>);
  static_assert(sizeof(t) == 4 || sizeof(t) == 8 || sizeof(t) == 2);
  if constexpr (sizeof(t) == 4) {
    t = be32toh(t);
  } else if constexpr (sizeof(t) == 8) {
    t = be64toh(t);
  } else if constexpr (sizeof(t) == 2) {
    t = be16toh(t);
  }
}

// In memory representation of an LZ4Diff patch, it's not marked as packed
// because parsing isn't as simple as reinterpret_cast<> any way.
struct Lz4diffPatch {
  char magic[kLz4diffMagic.size()];
  uint32_t version;
  uint32_t pb_header_size;  // size of protobuf message
  Lz4diffHeader pb_header;
  std::string_view inner_patch;
};

// Utility class to interact with puffin API. C++ does not have standard
// Read/Write trait. So everybody invent their own file descriptor wrapper.
class StringViewStream : public puffin::StreamInterface {
 public:
  ~StringViewStream() override = default;

  bool GetSize(uint64_t* size) const override {
    *size = read_memory_.size();
    return true;
  }

  bool GetOffset(uint64_t* offset) const override {
    *offset = offset_;
    return true;
  }

  bool Seek(uint64_t offset) override {
    TEST_AND_RETURN_FALSE(open_);
    uint64_t size;
    GetSize(&size);
    TEST_AND_RETURN_FALSE(offset <= size);
    offset_ = offset;
    return true;
  }

  bool Read(void* buffer, size_t length) override {
    TEST_AND_RETURN_FALSE(open_);
    TEST_AND_RETURN_FALSE(offset_ + length <= read_memory_.size());
    memcpy(buffer, read_memory_.data() + offset_, length);
    offset_ += length;
    return true;
  }

  bool Write(const void* buffer, size_t length) override {
    LOG(ERROR) << "Unsupported operation " << __FUNCTION__;
    return false;
  }

  bool Close() override {
    open_ = false;
    return true;
  }

  constexpr StringViewStream(std::string_view read_memory)
      : read_memory_(read_memory) {
    CHECK(!read_memory.empty());
  }

 private:
  // The memory buffer for reading.
  std::string_view read_memory_;

  // The current offset.
  uint64_t offset_{};
  bool open_{true};
};

bool ParseLz4DifffPatch(std::string_view patch_data, Lz4diffPatch* output) {
  CHECK_NE(output, nullptr);
  if (!android::base::StartsWith(patch_data, kLz4diffMagic)) {
    LOG(ERROR) << "Invalid lz4diff magic: "
               << HexEncode(patch_data.substr(0, kLz4diffMagic.size()))
               << ", expected: " << HexEncode(kLz4diffMagic);
    return false;
  }
  Lz4diffPatch& patch = *output;
  std::memcpy(patch.magic, patch_data.data(), kLz4diffMagic.size());
  std::memcpy(&patch.version,
              patch_data.data() + kLz4diffMagic.size(),
              sizeof(patch.version));
  BigEndianToHost(patch.version);
  if (patch.version != kLz4diffVersion) {
    LOG(ERROR) << "Unsupported lz4diff version: " << patch.version
               << ", supported version: " << kLz4diffVersion;
    return false;
  }
  std::memcpy(&patch.pb_header_size,
              patch_data.data() + kLz4diffMagic.size() + sizeof(patch.version),
              sizeof(patch.pb_header_size));
  BigEndianToHost(patch.pb_header_size);
  TEST_AND_RETURN_FALSE(patch.pb_header.ParseFromArray(
      patch_data.data() + kLz4diffHeaderSize, patch.pb_header_size));
  patch.inner_patch =
      patch_data.substr(kLz4diffHeaderSize + patch.pb_header_size);
  return true;
}

bool bspatch(std::string_view input_data,
             std::string_view patch_data,
             Blob* output) {
  CHECK_NE(output, nullptr);
  output->clear();
  CHECK_GT(patch_data.size(), 0UL);
  int err =
      bsdiff::bspatch(reinterpret_cast<const uint8_t*>(input_data.data()),
                      input_data.size(),
                      reinterpret_cast<const uint8_t*>(patch_data.data()),
                      patch_data.size(),
                      [output](const uint8_t* data, size_t size) -> size_t {
                        output->insert(output->end(), data, data + size);
                        return size;
                      });
  return err == 0;
}

bool puffpatch(std::string_view input_data,
               std::string_view patch_data,
               Blob* output) {
  // Cache size has a big impact on speed of puffpatch, use a default of 5MB to
  // match update_engine behavior.
  static constexpr size_t kPuffPatchCacheSize = 5 * 1024 * 1024;
  return puffin::PuffPatch(std::make_unique<StringViewStream>(input_data),
                           puffin::MemoryStream::CreateForWrite(output),
                           reinterpret_cast<const uint8_t*>(patch_data.data()),
                           patch_data.size(),
                           kPuffPatchCacheSize);
}

std::vector<CompressedBlock> ToCompressedBlockVec(
    const google::protobuf::RepeatedPtrField<CompressedBlockInfo>& rpf) {
  std::vector<CompressedBlock> ret;
  ret.reserve(rpf.size());
  for (const auto& block : rpf) {
    auto& info = ret.emplace_back();
    info.compressed_length = block.compressed_length();
    info.uncompressed_length = block.uncompressed_length();
    info.uncompressed_offset = block.uncompressed_offset();
  }
  return ret;
}

bool HasPosfixPatches(const Lz4diffPatch& patch) {
  for (const auto& info : patch.pb_header.dst_info().block_info()) {
    if (!info.postfix_bspatch().empty()) {
      return true;
    }
  }
  return false;
}

size_t GetCompressedSize(
    const google::protobuf::RepeatedPtrField<CompressedBlockInfo>& info) {
  size_t compressed_size = 0;
  for (const auto& block : info) {
    compressed_size += block.compressed_length();
  }
  return compressed_size;
}

size_t GetDecompressedSize(
    const google::protobuf::RepeatedPtrField<CompressedBlockInfo>& info) {
  size_t decompressed_size = 0;
  for (const auto& block : info) {
    decompressed_size += block.uncompressed_length();
  }
  return decompressed_size;
}

bool ApplyInnerPatch(Blob decompressed_src,
                     const Lz4diffPatch& patch,
                     Blob* decompressed_dst) {
  switch (patch.pb_header.inner_type()) {
    case InnerPatchType::BSDIFF:
      TEST_AND_RETURN_FALSE(bspatch(
          ToStringView(decompressed_src), patch.inner_patch, decompressed_dst));
      break;
    case InnerPatchType::PUFFDIFF:
      TEST_AND_RETURN_FALSE(puffpatch(
          ToStringView(decompressed_src), patch.inner_patch, decompressed_dst));
      break;
    default:
      LOG(ERROR) << "Unsupported patch type: " << patch.pb_header.inner_type();
      return false;
  }
  return true;
}

// TODO(zhangkelvin) Rewrite this in C++ 20 coroutine once that's available.
// Hand coding CPS is not fun.
bool Lz4Patch(std::string_view src_data,
              const Lz4diffPatch& patch,
              const SinkFunc& sink) {
  auto decompressed_src = TryDecompressBlob(
      src_data,
      ToCompressedBlockVec(patch.pb_header.src_info().block_info()),
      patch.pb_header.src_info().zero_padding_enabled());
  TEST_AND_RETURN_FALSE(!decompressed_src.empty());
  Blob decompressed_dst;
  const auto decompressed_dst_size =
      GetDecompressedSize(patch.pb_header.dst_info().block_info());
  decompressed_dst.reserve(decompressed_dst_size);

  ApplyInnerPatch(std::move(decompressed_src), patch, &decompressed_dst);

  if (!HasPosfixPatches(patch)) {
    return TryCompressBlob(
        ToStringView(decompressed_dst),
        ToCompressedBlockVec(patch.pb_header.dst_info().block_info()),
        patch.pb_header.dst_info().zero_padding_enabled(),
        patch.pb_header.dst_info().algo(),
        sink);
  }
  auto postfix_patcher =
      [&sink,
       block_idx = 0,
       &dst_block_info = patch.pb_header.dst_info().block_info()](
          const uint8_t* data, size_t size) mutable -> size_t {
    if (block_idx >= dst_block_info.size()) {
      return sink(data, size);
    }
    const auto& block_info = dst_block_info[block_idx];
    TEST_EQ(size, block_info.compressed_length());
    DEFER { block_idx++; };
    if (block_info.postfix_bspatch().empty()) {
      return sink(data, size);
    }
    if (!block_info.sha256_hash().empty()) {
      Blob actual_hash;
      TEST_AND_RETURN_FALSE(
          HashCalculator::RawHashOfBytes(data, size, &actual_hash));
      if (ToStringView(actual_hash) != block_info.sha256_hash()) {
        LOG(ERROR) << "Block " << block_info
                   << " is corrupted. This usually means the patch generator "
                      "used a different version of LZ4, or an incompatible LZ4 "
                      "patch generator was used, or LZ4 produces different "
                      "output on different platforms. Expected hash: "
                   << HexEncode(block_info.sha256_hash())
                   << ", actual hash: " << HexEncode(actual_hash);
        return 0;
      }
    }
    Blob fixed_block;
    TEST_AND_RETURN_FALSE(
        bspatch(std::string_view(reinterpret_cast<const char*>(data), size),
                block_info.postfix_bspatch(),
                &fixed_block));
    return sink(fixed_block.data(), fixed_block.size());
  };

  return TryCompressBlob(
      ToStringView(decompressed_dst),
      ToCompressedBlockVec(patch.pb_header.dst_info().block_info()),
      patch.pb_header.dst_info().zero_padding_enabled(),
      patch.pb_header.dst_info().algo(),
      postfix_patcher);
}

bool Lz4Patch(std::string_view src_data,
              const Lz4diffPatch& patch,
              Blob* output) {
  Blob blob;
  const auto output_size =
      GetCompressedSize(patch.pb_header.dst_info().block_info());
  blob.reserve(output_size);
  TEST_AND_RETURN_FALSE(Lz4Patch(
      src_data, patch, [&blob](const uint8_t* data, size_t size) -> size_t {
        blob.insert(blob.end(), data, data + size);
        return size;
      }));
  *output = std::move(blob);
  return true;
}

}  // namespace

bool Lz4Patch(std::string_view src_data,
              std::string_view patch_data,
              Blob* output) {
  Lz4diffPatch patch;
  TEST_AND_RETURN_FALSE(ParseLz4DifffPatch(patch_data, &patch));
  return Lz4Patch(src_data, patch, output);
}

bool Lz4Patch(std::string_view src_data,
              std::string_view patch_data,
              const SinkFunc& sink) {
  Lz4diffPatch patch;
  TEST_AND_RETURN_FALSE(ParseLz4DifffPatch(patch_data, &patch));
  return Lz4Patch(src_data, patch, sink);
}

bool Lz4Patch(const Blob& src_data, const Blob& patch_data, Blob* output) {
  return Lz4Patch(ToStringView(src_data), ToStringView(patch_data), output);
}

std::ostream& operator<<(std::ostream& out, const CompressionAlgorithm& info) {
  out << "Algo {type: " << info.Type_Name(info.type());
  if (info.level() != 0) {
    out << ", level: " << info.level();
  }
  out << "}";

  return out;
}

std::ostream& operator<<(std::ostream& out, const CompressionInfo& info) {
  out << "CompressionInfo {block_info: " << info.block_info()
      << ", algo: " << info.algo() << "}";
  return out;
}

std::ostream& operator<<(std::ostream& out, const Lz4diffHeader& header) {
  out << "Lz4diffHeader {src_info: " << header.src_info()
      << ", dst_info: " << header.dst_info() << "}";
  return out;
}

}  // namespace chromeos_update_engine
