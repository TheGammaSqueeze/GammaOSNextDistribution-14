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

#ifndef UPDATE_ENGINE_PARTITION_WRITER_INTERFACE_H_
#define UPDATE_ENGINE_PARTITION_WRITER_INTERFACE_H_

#include <cstdint>
#include <string>

#include <brillo/secure_blob.h>
#include <gtest/gtest_prod.h>

#include "update_engine/payload_consumer/install_plan.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {
class PartitionWriterInterface {
 public:
  virtual ~PartitionWriterInterface() = default;

  // Perform necessary initialization work before InstallOperation can be
  // applied to this partition
  [[nodiscard]] virtual bool Init(const InstallPlan* install_plan,
                                  bool source_may_exist,
                                  size_t next_op_index) = 0;

  // |CheckpointUpdateProgress| will be called after SetNextOpIndex(), but it's
  // optional. DeltaPerformer may or may not call this everytime an operation is
  // applied.
  //   |next_op_index| is index of next operation that should be applied.
  // |next_op_index-1| is the last operation that is already applied.
  virtual void CheckpointUpdateProgress(size_t next_op_index) = 0;

  // Close partition writer, when calling this function there's no guarantee
  // that all |InstallOperations| are sent to |PartitionWriter|. This function
  // will be called even if we are pausing/aborting the update.
  virtual int Close() = 0;

  // These perform a specific type of operation and return true on success.
  // |error| will be set if source hash mismatch, otherwise |error| might not be
  // set even if it fails.
  [[nodiscard]] virtual bool PerformReplaceOperation(
      const InstallOperation& operation, const void* data, size_t count) = 0;
  [[nodiscard]] virtual bool PerformZeroOrDiscardOperation(
      const InstallOperation& operation) = 0;

  [[nodiscard]] virtual bool PerformSourceCopyOperation(
      const InstallOperation& operation, ErrorCode* error) = 0;
  [[nodiscard]] virtual bool PerformDiffOperation(
      const InstallOperation& operation,
      ErrorCode* error,
      const void* data,
      size_t count) = 0;

  // |DeltaPerformer| calls this when all Install Ops are sent to partition
  // writer. No |Perform*Operation| methods will be called in the future, and
  // the partition writer is expected to be closed soon.
  [[nodiscard]] virtual bool FinishedInstallOps() = 0;
};
}  // namespace chromeos_update_engine

#endif
