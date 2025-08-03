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

#ifndef UPDATE_ENGINE_BLOCK_EXTENT_WRITER_H_
#define UPDATE_ENGINE_BLOCK_EXTENT_WRITER_H_

#include <cstdint>
#include <vector>

#include "update_engine/payload_consumer/extent_writer.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {

// Cache data upto size of one extent before writing.
class BlockExtentWriter : public chromeos_update_engine::ExtentWriter {
 public:
  static constexpr size_t BUFFER_SIZE = 1024 * 1024;
  BlockExtentWriter() = default;
  ~BlockExtentWriter() = default;
  // Returns true on success.
  bool Init(const google::protobuf::RepeatedPtrField<Extent>& extents,
            uint32_t block_size) override;
  // Returns true on success.
  bool Write(const void* bytes, size_t count) final;
  // Write data for 1 extent. |bytes| will be a pointer which points to data of
  // size |extent.num_blocks()*block_size|. |extent| is the current extent we
  // are writing to.
  virtual bool WriteExtent(const void* bytes,
                           const Extent& extent,
                           size_t block_size) = 0;
  size_t BlockSize() const { return block_size_; }

 private:
  bool WriteExtent(const void* bytes, size_t count);
  bool NextExtent();
  [[nodiscard]] size_t ConsumeWithBuffer(const uint8_t* const bytes,
                                         const size_t count);
  // It's a non-owning pointer, because PartitionWriter owns the CowWruter. This
  // allows us to use a single instance of CowWriter for all operations applied
  // to the same partition.
  google::protobuf::RepeatedPtrField<Extent> extents_;
  size_t cur_extent_idx_{};
  std::vector<uint8_t> buffer_;
  size_t block_size_{};
  size_t offset_in_extent_{};
};

}  // namespace chromeos_update_engine

#endif
