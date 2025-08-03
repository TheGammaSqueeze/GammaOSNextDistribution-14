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

#include "lz4diff.h"
#include "lz4diff_compress.h"

#include <bsdiff/bsdiff.h>
#include <bsdiff/constants.h>
#include <bsdiff/patch_writer_factory.h>
#include <bsdiff/patch_writer.h>
#include <puffin/common.h>
#include <puffin/puffdiff.h>
#include <lz4.h>
#include <lz4hc.h>

#include "update_engine/common/utils.h"
#include "update_engine/common/hash_calculator.h"
#include "update_engine/payload_generator/deflate_utils.h"
#include "update_engine/payload_generator/delta_diff_generator.h"
#include "lz4diff/lz4diff.pb.h"
#include "lz4diff_format.h"

namespace chromeos_update_engine {

bool StoreDstCompressedFileInfo(std::string_view recompressed_blob,
                                std::string_view target_blob,
                                const CompressedFile& dst_file_info,
                                Lz4diffHeader* output) {
  *output->mutable_dst_info()->mutable_algo() = dst_file_info.algo;
  output->mutable_dst_info()->set_zero_padding_enabled(
      dst_file_info.zero_padding_enabled);
  const auto& block_info = dst_file_info.blocks;
  auto& dst_block_info = *output->mutable_dst_info()->mutable_block_info();
  dst_block_info.Clear();
  size_t offset = 0;
  for (const auto& block : block_info) {
    auto& pb_block = *dst_block_info.Add();
    pb_block.set_uncompressed_offset(block.uncompressed_offset);
    pb_block.set_uncompressed_length(block.uncompressed_length);
    pb_block.set_compressed_length(block.compressed_length);
    CHECK_LT(offset, recompressed_blob.size());
    auto s1 = recompressed_blob.substr(offset, block.compressed_length);
    auto s2 = target_blob.substr(offset, block.compressed_length);
    if (s1 != s2) {
      ScopedTempFile patch;
      int err =
          bsdiff::bsdiff(reinterpret_cast<const unsigned char*>(s1.data()),
                         s1.size(),
                         reinterpret_cast<const unsigned char*>(s2.data()),
                         s2.size(),
                         patch.path().c_str(),
                         nullptr);
      CHECK_EQ(err, 0);
      LOG(WARNING) << "Recompress Postfix patch size: "
                   << utils::FileSize(patch.path());
      std::string patch_content;
      TEST_AND_RETURN_FALSE(utils::ReadFile(patch.path(), &patch_content));
      pb_block.set_postfix_bspatch(std::move(patch_content));
    }
    // Include recompressed blob hash, so we can determine if the device
    // produces same compressed output
    Blob recompressed_blob_hash;
    TEST_AND_RETURN_FALSE(HashCalculator::RawHashOfBytes(
        s1.data(), s1.length(), &recompressed_blob_hash));
    pb_block.set_sha256_hash(recompressed_blob_hash.data(),
                             recompressed_blob_hash.size());

    offset += block.compressed_length;
  }
  return true;
}

template <typename Blob>
static bool TryBsdiff(Blob src, Blob dst, Blob* output) noexcept {
  static constexpr auto kLz4diffDefaultBrotliQuality = 9;
  CHECK_NE(output, nullptr);
  ScopedTempFile patch;

  Blob bsdiff_delta;
  bsdiff::BsdiffPatchWriter patch_writer(patch.path(),
                                         {bsdiff::CompressorType::kBrotli},
                                         kLz4diffDefaultBrotliQuality);
  TEST_AND_RETURN_FALSE(0 == bsdiff::bsdiff(src.data(),
                                            src.size(),
                                            dst.data(),
                                            dst.size(),
                                            &patch_writer,
                                            nullptr));

  TEST_AND_RETURN_FALSE(utils::ReadFile(patch.path(), &bsdiff_delta));
  TEST_AND_RETURN_FALSE(!bsdiff_delta.empty());
  *output = std::move(bsdiff_delta);
  return true;
}

bool TryFindDeflates(puffin::Buffer data,
                     std::vector<puffin::BitExtent>* deflates) {
  if (puffin::LocateDeflatesInZipArchive(data, deflates)) {
    return true;
  }
  deflates->clear();
  if (puffin::LocateDeflatesInGzip(data, deflates)) {
    return true;
  }
  deflates->clear();
  return false;
}

static bool ConstructLz4diffPatch(Blob inner_patch,
                                  const Lz4diffHeader& header,
                                  Blob* output) {
  Blob patch(kLz4diffHeaderSize);
  std::memcpy(patch.data(), kLz4diffMagic.data(), kLz4diffMagic.size());
  *reinterpret_cast<uint32_t*>(patch.data() + kLz4diffMagic.size()) =
      htobe32(kLz4diffVersion);

  std::string serialized_pb;
  TEST_AND_RETURN_FALSE(header.SerializeToString(&serialized_pb));
  *reinterpret_cast<uint32_t*>(patch.data() + kLz4diffMagic.size() + 4) =
      htobe32(serialized_pb.size());
  patch.insert(patch.end(), serialized_pb.begin(), serialized_pb.end());
  patch.insert(patch.end(), inner_patch.begin(), inner_patch.end());

  *output = std::move(patch);
  return true;
}

static bool TryPuffdiff(puffin::Buffer src,
                        puffin::Buffer dst,
                        Blob* output) noexcept {
  CHECK_NE(output, nullptr);
  std::vector<puffin::BitExtent> src_deflates;
  TEST_AND_RETURN_FALSE(TryFindDeflates(src, &src_deflates));
  std::vector<puffin::BitExtent> dst_deflates;
  TEST_AND_RETURN_FALSE(TryFindDeflates(dst, &dst_deflates));
  if (src_deflates.empty() || dst_deflates.empty()) {
    return false;
  }

  Blob puffdiff_delta;
  ScopedTempFile temp_file("puffdiff-delta.XXXXXX");
  // Perform PuffDiff operation.
  TEST_AND_RETURN_FALSE(puffin::PuffDiff(
      src, dst, src_deflates, dst_deflates, temp_file.path(), &puffdiff_delta));
  TEST_AND_RETURN_FALSE(!puffdiff_delta.empty());

  *output = std::move(puffdiff_delta);
  return true;
}

static void StoreSrcCompressedFileInfo(const CompressedFile& src_file_info,
                                       Lz4diffHeader* header) {
  *header->mutable_src_info()->mutable_algo() = src_file_info.algo;
  header->mutable_src_info()->set_zero_padding_enabled(
      src_file_info.zero_padding_enabled);
  auto& src_blocks = *header->mutable_src_info()->mutable_block_info();
  src_blocks.Clear();
  for (const auto& block : src_file_info.blocks) {
    auto& block_info = *src_blocks.Add();
    block_info.set_uncompressed_length(block.uncompressed_length);
    block_info.set_uncompressed_offset(block.uncompressed_offset);
    block_info.set_compressed_length(block.compressed_length);
  }
  return;
}

bool Lz4Diff(std::string_view src,
             std::string_view dst,
             const CompressedFile& src_file_info,
             const CompressedFile& dst_file_info,
             Blob* output,
             InstallOperation::Type* op_type) noexcept {
  const auto& src_block_info = src_file_info.blocks;
  const auto& dst_block_info = dst_file_info.blocks;

  auto decompressed_src = TryDecompressBlob(
      src, src_block_info, src_file_info.zero_padding_enabled);
  auto decompressed_dst = TryDecompressBlob(
      dst, dst_block_info, dst_file_info.zero_padding_enabled);
  if (decompressed_src.empty() || decompressed_dst.empty()) {
    LOG(ERROR) << "Failed to decompress input data";
    return false;
  }

  Lz4diffHeader header;
  // BSDIFF isn't supposed to fail, so return error if BSDIFF failed.
  Blob patch_data;
  TEST_AND_RETURN_FALSE(
      TryBsdiff(decompressed_src, decompressed_dst, &patch_data));
  header.set_inner_type(InnerPatchType::BSDIFF);
  if (op_type) {
    *op_type = InstallOperation::LZ4DIFF_BSDIFF;
  }
  // PUFFDIFF might fail, as the input data might not be deflate compressed.

  Blob puffdiff_delta;
  if (TryPuffdiff(decompressed_src, decompressed_dst, &puffdiff_delta) &&
      puffdiff_delta.size() < patch_data.size()) {
    patch_data = std::move(puffdiff_delta);
    header.set_inner_type(InnerPatchType::PUFFDIFF);
    if (op_type) {
      *op_type = InstallOperation::LZ4DIFF_PUFFDIFF;
    }
  }
  // Free up memory used by |decompressed_src| , as we don't need it anymore.
  decompressed_src = {};

  auto recompressed_blob = TryCompressBlob(ToStringView(decompressed_dst),
                                           dst_block_info,
                                           dst_file_info.zero_padding_enabled,
                                           dst_file_info.algo);
  TEST_AND_RETURN_FALSE(recompressed_blob.size() > 0);

  StoreSrcCompressedFileInfo(src_file_info, &header);
  StoreDstCompressedFileInfo(
      ToStringView(recompressed_blob), dst, dst_file_info, &header);
  return ConstructLz4diffPatch(std::move(patch_data), header, output);
}

bool Lz4Diff(const Blob& src,
             const Blob& dst,
             const CompressedFile& src_file_info,
             const CompressedFile& dst_file_info,
             Blob* output,
             InstallOperation::Type* op_type) noexcept {
  return Lz4Diff(ToStringView(src),
                 ToStringView(dst),
                 src_file_info,
                 dst_file_info,
                 output,
                 op_type);
}

}  // namespace chromeos_update_engine
