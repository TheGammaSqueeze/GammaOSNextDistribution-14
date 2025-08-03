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

#include <algorithm>
#include <optional>
#include <vector>

#include "update_engine/common/utils.h"
#include "update_engine/payload_consumer/xor_extent_writer.h"
#include "update_engine/payload_generator/extent_ranges.h"
#include "update_engine/payload_generator/extent_utils.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {
bool XORExtentWriter::WriteXorCowOp(const uint8_t* bytes,
                                    const size_t size,
                                    const Extent& xor_ext,
                                    const size_t src_offset) {
  xor_block_data.resize(BlockSize() * xor_ext.num_blocks());
  const auto src_block = src_offset / BlockSize();
  ssize_t bytes_read = 0;
  TEST_AND_RETURN_FALSE_ERRNO(utils::PReadAll(source_fd_,
                                              xor_block_data.data(),
                                              xor_block_data.size(),
                                              src_offset,
                                              &bytes_read));
  if (bytes_read != static_cast<ssize_t>(xor_block_data.size())) {
    LOG(ERROR) << "bytes_read: " << bytes_read << ", expected to read "
               << xor_block_data.size() << " at block " << src_block
               << " offset " << src_offset % BlockSize();
    return false;
  }

  std::transform(xor_block_data.cbegin(),
                 xor_block_data.cbegin() + xor_block_data.size(),
                 bytes,
                 xor_block_data.begin(),
                 std::bit_xor<unsigned char>{});
  TEST_AND_RETURN_FALSE(cow_writer_->AddXorBlocks(xor_ext.start_block(),
                                                  xor_block_data.data(),
                                                  xor_block_data.size(),
                                                  src_block,
                                                  src_offset % BlockSize()));
  return true;
}

bool XORExtentWriter::WriteXorExtent(const uint8_t* bytes,
                                     const size_t size,
                                     const Extent& xor_ext,
                                     const CowMergeOperation* merge_op) {
  const auto src_block = merge_op->src_extent().start_block() +
                         xor_ext.start_block() -
                         merge_op->dst_extent().start_block();
  const auto read_end_offset =
      (src_block + xor_ext.num_blocks()) * BlockSize() + merge_op->src_offset();
  const auto is_out_of_bound_read =
      read_end_offset > partition_size_ && partition_size_ != 0;
  const auto oob_bytes =
      is_out_of_bound_read ? read_end_offset - partition_size_ : 0;
  if (is_out_of_bound_read) {
    if (oob_bytes >= BlockSize()) {
      LOG(ERROR) << "XOR op overflowed source partition by more than "
                 << BlockSize() << ", " << xor_ext << ", " << merge_op
                 << ", out of bound bytes: " << oob_bytes
                 << ", partition size: " << partition_size_;
      return false;
    }
    if (oob_bytes > merge_op->src_offset()) {
      LOG(ERROR) << "XOR op overflowed source offset, out of bound bytes: "
                 << oob_bytes << ", source offset: " << merge_op->src_offset();
    }
    Extent non_oob_extent =
        ExtentForRange(xor_ext.start_block(), xor_ext.num_blocks() - 1);
    if (non_oob_extent.num_blocks() > 0) {
      TEST_AND_RETURN_FALSE(
          WriteXorCowOp(bytes,
                        BlockSize() * non_oob_extent.num_blocks(),
                        non_oob_extent,
                        src_block * BlockSize() + merge_op->src_offset()));
    }
    const Extent last_block =
        ExtentForRange(xor_ext.start_block() + xor_ext.num_blocks() - 1, 1);
    TEST_AND_RETURN_FALSE(
        WriteXorCowOp(bytes + (xor_ext.num_blocks() - 1) * BlockSize(),
                      BlockSize(),
                      last_block,
                      (src_block + xor_ext.num_blocks() - 1) * BlockSize()));
    return true;
  }
  TEST_AND_RETURN_FALSE(WriteXorCowOp(
      bytes, size, xor_ext, src_block * BlockSize() + merge_op->src_offset()));
  return true;
}

// Returns true on success.
bool XORExtentWriter::WriteExtent(const void* bytes,
                                  const Extent& extent,
                                  const size_t size) {
  const auto xor_extents = xor_map_.GetIntersectingExtents(extent);
  for (const auto& xor_ext : xor_extents) {
    const auto merge_op_opt = xor_map_.Get(xor_ext);
    if (!merge_op_opt.has_value()) {
      // If a file in the target build contains duplicate blocks, e.g.
      // [120503-120514], [120503-120503], we can end up here. If that's the
      // case then there's no bug, just some annoying edge cases.
      LOG(ERROR)
          << xor_ext
          << " isn't in XOR map but it's returned by GetIntersectingExtents(), "
             "this is either a bug inside GetIntersectingExtents, or some "
             "duplicate blocks are present in target build. OTA extent: "
          << extent;
      return false;
    }

    const auto merge_op = merge_op_opt.value();
    TEST_AND_RETURN_FALSE(merge_op->has_src_extent());
    TEST_AND_RETURN_FALSE(merge_op->has_dst_extent());
    if (!ExtentContains(extent, xor_ext)) {
      LOG(ERROR) << "CowXor merge op extent should be completely inside "
                    "InstallOp's extent. merge op extent: "
                 << xor_ext << " InstallOp extent: " << extent;
      return false;
    }
    if (!ExtentContains(merge_op->dst_extent(), xor_ext)) {
      LOG(ERROR) << "CowXor op extent should be completely inside "
                    "xor_map's extent. merge op extent: "
                 << xor_ext << " xor_map extent: " << merge_op->dst_extent();
      return false;
    }
    const auto i = xor_ext.start_block() - extent.start_block();
    const auto dst_block_data =
        static_cast<const unsigned char*>(bytes) + i * BlockSize();
    if (!WriteXorExtent(dst_block_data,
                        xor_ext.num_blocks() * BlockSize(),
                        xor_ext,
                        merge_op)) {
      LOG(ERROR) << "Failed to write XOR extent " << xor_ext;
      return false;
    }
  }
  const auto replace_extents = xor_map_.GetNonIntersectingExtents(extent);
  return WriteReplaceExtents(replace_extents, extent, bytes, size);
}

bool XORExtentWriter::WriteReplaceExtents(
    const std::vector<Extent>& replace_extents,
    const Extent& extent,
    const void* bytes,
    size_t size) {
  const uint64_t new_block_start = extent.start_block();
  for (const auto& ext : replace_extents) {
    if (ext.start_block() + ext.num_blocks() >
        extent.start_block() + extent.num_blocks()) {
      LOG(ERROR) << "CowReplace merge op extent should be completely inside "
                    "InstallOp's extent. merge op extent: "
                 << ext << " InstallOp extent: " << extent;
      return false;
    }
    const auto i = ext.start_block() - new_block_start;
    const auto dst_block_data =
        static_cast<const unsigned char*>(bytes) + i * BlockSize();
    TEST_AND_RETURN_FALSE(cow_writer_->AddRawBlocks(
        ext.start_block(), dst_block_data, ext.num_blocks() * BlockSize()));
  }
  return true;
}

}  // namespace chromeos_update_engine
