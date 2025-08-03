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

#ifndef UPDATE_ENGINE_PAYLOAD_GENERATOR_MERGE_SEQUENCE_GENERATOR_H_
#define UPDATE_ENGINE_PAYLOAD_GENERATOR_MERGE_SEQUENCE_GENERATOR_H_

#include <map>
#include <memory>
#include <set>
#include <utility>
#include <vector>

#include "update_engine/payload_generator/annotated_operation.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {
// Constructs CowMergeOperation from src & dst extents
CowMergeOperation CreateCowMergeOperation(const Extent& src_extent,
                                          const Extent& dst_extent,
                                          CowMergeOperation::Type op_type,
                                          uint32_t src_offset = 0);

// Comparator for CowMergeOperation.
bool operator<(const CowMergeOperation& op1, const CowMergeOperation& op2);
bool operator==(const CowMergeOperation& op1, const CowMergeOperation& op2);

std::ostream& operator<<(std::ostream& os,
                         const CowMergeOperation& merge_operation);

// This class takes a list of CowMergeOperations; and sorts them so that no
// read after write will happen by following the sequence. When there is a
// cycle, we will omit some operations in the list. Therefore, the result
// sequence may not contain all blocks in the input list.

template <typename T>
T&& Sort(T&& container) {
  std::sort(container.begin(), container.end());
  return container;
}

class MergeSequenceGenerator {
 public:
  // Creates an object from a list of OTA InstallOperations. Returns nullptr
  // on failure.
  static std::unique_ptr<MergeSequenceGenerator> Create(
      const std::vector<AnnotatedOperation>& aops,
      std::string_view partition_name = "");
  explicit MergeSequenceGenerator(std::vector<CowMergeOperation> transfers,
                                  std::string_view partition_name)
      : operations_(std::move(Sort(transfers))),
        merge_after_(FindDependency(operations_)),
        partition_name_(partition_name) {}
  // Checks that no read after write happens in the given sequence.
  static bool ValidateSequence(const std::vector<CowMergeOperation>& sequence);

  // Generates a merge sequence from |operations_|, puts the result in
  // |sequence|. Returns false on failure.
  bool Generate(std::vector<CowMergeOperation>* sequence) const;

  const std::vector<CowMergeOperation>& GetOperations() const {
    return operations_;
  }
  const std::map<CowMergeOperation, std::set<CowMergeOperation>>&
  GetDependencyMap() const {
    return merge_after_;
  }

 private:
  friend class MergeSequenceGeneratorTest;

  // For a given merge operation, finds all the operations that should merge
  // after myself. Put the result in |merge_after|. |operations| must be sorted
  static std::map<CowMergeOperation, std::set<CowMergeOperation>>
  FindDependency(const std::vector<CowMergeOperation>& operations);
  // The list of CowMergeOperations to sort.
  const std::vector<CowMergeOperation> operations_;
  const std::map<CowMergeOperation, std::set<CowMergeOperation>> merge_after_;
  const std::string_view partition_name_;
};

void SplitSelfOverlapping(const Extent& src_extent,
                          const Extent& dst_extent,
                          std::vector<CowMergeOperation>* sequence);

}  // namespace chromeos_update_engine
#endif
