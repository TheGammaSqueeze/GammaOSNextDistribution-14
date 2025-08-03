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

#ifndef UPDATE_ENGINE_SNAPSHOT_EXTENT_WRITER_H_
#define UPDATE_ENGINE_SNAPSHOT_EXTENT_WRITER_H_

#include <cstdint>
#include <vector>

#include <libsnapshot/cow_writer.h>

#include "update_engine/payload_consumer/block_extent_writer.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {

class SnapshotExtentWriter final : public BlockExtentWriter {
 public:
  explicit SnapshotExtentWriter(android::snapshot::ICowWriter* cow_writer)
      : cow_writer_(cow_writer) {}
  bool WriteExtent(const void* bytes,
                   const Extent& extent,
                   size_t block_size) override;

 private:
  android::snapshot::ICowWriter* cow_writer_;
};

}  // namespace chromeos_update_engine

#endif
