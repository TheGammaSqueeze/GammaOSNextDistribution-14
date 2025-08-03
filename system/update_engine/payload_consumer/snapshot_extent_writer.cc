//
// Copyright (C) 2020 The Android Open Source Project
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

#include "update_engine/payload_consumer/snapshot_extent_writer.h"

#include <algorithm>
#include <cstdint>

#include <libsnapshot/cow_writer.h>

#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {

bool SnapshotExtentWriter::WriteExtent(const void* bytes,
                                       const Extent& extent,
                                       size_t block_size) {
  return cow_writer_->AddRawBlocks(
      extent.start_block(), bytes, extent.num_blocks() * block_size);
}

}  // namespace chromeos_update_engine
