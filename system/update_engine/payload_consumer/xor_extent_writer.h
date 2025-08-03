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

#ifndef UPDATE_ENGINE_PAYLOAD_CONSUMER_XOR_EXTENT_WRITER_H_
#define UPDATE_ENGINE_PAYLOAD_CONSUMER_XOR_EXTENT_WRITER_H_

#include <vector>

#include "common/utils.h"
#include "update_engine/payload_consumer/block_extent_writer.h"
#include "update_engine/payload_consumer/extent_map.h"

#include <update_engine/update_metadata.pb.h>
#include <libsnapshot/cow_writer.h>

namespace chromeos_update_engine {

// An extent writer that will selectively convert some of the blocks into an XOR
// block. All blocks that appear in |xor_map| will be converted,
class XORExtentWriter : public BlockExtentWriter {
 public:
  XORExtentWriter(const InstallOperation& op,
                  FileDescriptorPtr source_fd,
                  android::snapshot::ICowWriter* cow_writer,
                  const ExtentMap<const CowMergeOperation*>& xor_map,
                  size_t partition_size)
      : src_extents_(op.src_extents()),
        source_fd_(source_fd),
        xor_map_(xor_map),
        cow_writer_(cow_writer),
        partition_size_(partition_size) {
    CHECK(source_fd->IsOpen());
  }
  ~XORExtentWriter() = default;

  // Returns true on success.
  bool WriteExtent(const void* bytes,
                   const Extent& extent,
                   size_t size) override;

 private:
  bool WriteReplaceExtents(const std::vector<Extent>& replace_extents,
                           const Extent& extent,
                           const void* bytes,
                           size_t size);
  bool WriteXorExtent(const uint8_t* bytes,
                      const size_t size,
                      const Extent& xor_ext,
                      const CowMergeOperation* merge_op);
  bool WriteXorCowOp(const uint8_t* bytes,
                     const size_t size,
                     const Extent& xor_ext,
                     size_t src_offset);
  const google::protobuf::RepeatedPtrField<Extent>& src_extents_;
  const FileDescriptorPtr source_fd_;
  const ExtentMap<const CowMergeOperation*>& xor_map_;
  android::snapshot::ICowWriter* cow_writer_;
  std::vector<uint8_t> xor_block_data;
  const size_t partition_size_;
};

}  // namespace chromeos_update_engine

#endif  // UPDATE_ENGINE_PAYLOAD_CONSUMER_XOR_EXTENT_WRITER_H_
