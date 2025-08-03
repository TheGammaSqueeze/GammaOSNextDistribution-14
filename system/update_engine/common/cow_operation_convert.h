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

#ifndef __COW_OPERATION_CONVERT_H
#define __COW_OPERATION_CONVERT_H

#include <vector>

#include <libsnapshot/cow_format.h>

#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {
struct CowOperation {
  enum Type {
    CowCopy = static_cast<int>(android::snapshot::kCowCopyOp),
    CowReplace = static_cast<int>(android::snapshot::kCowReplaceOp),
  };
  Type op{};
  uint64_t src_block{};
  uint64_t dst_block{};
  uint64_t block_count{1};
};

// Convert SOURCE_COPY operations in `operations` list to a list of
// CowOperations according to the merge sequence. This function only converts
// SOURCE_COPY, other operations are ignored. If there's a merge conflict in
// SOURCE_COPY operations, some blocks may be converted to COW_REPLACE instead
// of COW_COPY.

// The list returned does not necessarily preserve the order of
// SOURCE_COPY in `operations`. The only guarantee about ordering in the
// returned list is that if operations are applied in such order, there would be
// no merge conflicts.

// This funnction is intended to be used by delta_performer to perform
// SOURCE_COPY operations on Virtual AB Compression devices.
std::vector<CowOperation> ConvertToCowOperations(
    const ::google::protobuf::RepeatedPtrField<
        ::chromeos_update_engine::InstallOperation>& operations,
    const ::google::protobuf::RepeatedPtrField<CowMergeOperation>&
        merge_operations);

constexpr bool IsConsecutive(const CowOperation& op1, const CowOperation& op2) {
  return op1.op == op2.op && op1.dst_block + op1.block_count == op2.dst_block &&
         op1.src_block + op1.block_count == op2.src_block;
}

void push_back(std::vector<CowOperation>* converted, const CowOperation& op);

}  // namespace chromeos_update_engine
#endif
