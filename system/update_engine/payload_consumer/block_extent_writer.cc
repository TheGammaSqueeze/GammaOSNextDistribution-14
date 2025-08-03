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

#include "update_engine/payload_consumer/block_extent_writer.h"

#include <stdint.h>

#include <algorithm>

#include "update_engine/common/utils.h"
#include "update_engine/payload_generator/delta_diff_generator.h"
#include "update_engine/payload_generator/extent_ranges.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {

bool BlockExtentWriter::Init(
    const google::protobuf::RepeatedPtrField<Extent>& extents,
    uint32_t block_size) {
  TEST_NE(extents.size(), 0);
  extents_ = extents;
  cur_extent_idx_ = 0;
  buffer_.clear();
  buffer_.reserve(block_size);
  block_size_ = block_size;
  return true;
}

bool BlockExtentWriter::WriteExtent(const void* bytes, const size_t count) {
  const auto& cur_extent = extents_[cur_extent_idx_];
  const auto write_extent =
      ExtentForRange(cur_extent.start_block() + offset_in_extent_ / block_size_,
                     count / kBlockSize);
  offset_in_extent_ += count;
  if (offset_in_extent_ == cur_extent.num_blocks() * block_size_) {
    NextExtent();
  }
  return WriteExtent(bytes, write_extent, block_size_);
}

size_t BlockExtentWriter::ConsumeWithBuffer(const uint8_t* const data,
                                            const size_t count) {
  if (cur_extent_idx_ >= static_cast<size_t>(extents_.size())) {
    if (count > 0) {
      LOG(ERROR) << "Exhausted all blocks, but still have " << count
                 << " bytes pending for write";
    }
    return 0;
  }
  const auto& cur_extent = extents_[cur_extent_idx_];
  const auto cur_extent_size =
      static_cast<size_t>(cur_extent.num_blocks() * block_size_);

  const auto write_size =
      std::min(cur_extent_size - offset_in_extent_, BUFFER_SIZE);
  if (buffer_.empty() && count >= write_size) {
    if (!WriteExtent(data, write_size)) {
      LOG(ERROR) << "WriteExtent(" << cur_extent.start_block() << ", "
                 << write_size << ") failed.";
      // return value is expected to be greater than 0. Return 0 to signal error
      // condition
      return 0;
    }
    return write_size;
  }
  if (buffer_.size() >= write_size) {
    LOG(ERROR)
        << "Data left in buffer should never be >= write_size, otherwise "
           "we should have send that data to CowWriter. Buffer size: "
        << buffer_.size() << " write_size: " << write_size;
  }
  const size_t bytes_to_copy =
      std::min<size_t>(count, write_size - buffer_.size());
  TEST_GT(bytes_to_copy, 0U);

  buffer_.insert(buffer_.end(), data, data + bytes_to_copy);
  TEST_LE(buffer_.size(), write_size);

  if (buffer_.size() == write_size) {
    if (!WriteExtent(buffer_.data(), write_size)) {
      LOG(ERROR) << "WriteExtent(" << cur_extent.start_block() << ", "
                 << cur_extent.num_blocks() << ") failed.";
      return 0;
    }
    buffer_.clear();
  }
  return bytes_to_copy;
}

// Returns true on success.
// This will construct a COW_REPLACE operation and forward it to CowWriter. It
// is important that caller does not perform SOURCE_COPY operation on this
// class, otherwise raw data will be stored. Caller should find ways to use
// COW_COPY whenever possible.
bool BlockExtentWriter::Write(const void* bytes, size_t count) {
  if (count == 0) {
    return true;
  }

  auto data = static_cast<const uint8_t*>(bytes);
  while (count > 0) {
    const auto bytes_written = ConsumeWithBuffer(data, count);
    TEST_AND_RETURN_FALSE(bytes_written > 0);
    data += bytes_written;
    count -= bytes_written;
  }
  return true;
}

bool BlockExtentWriter::NextExtent() {
  cur_extent_idx_++;
  offset_in_extent_ = 0;
  return cur_extent_idx_ < static_cast<size_t>(extents_.size());
}
}  // namespace chromeos_update_engine
