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

#ifndef UPDATE_ENGINE_PARTITION_WRITER_H_
#define UPDATE_ENGINE_PARTITION_WRITER_H_

#include <cstdint>
#include <memory>
#include <string>

#include <brillo/secure_blob.h>
#include <gtest/gtest_prod.h>

#include "update_engine/common/dynamic_partition_control_interface.h"
#include "update_engine/payload_consumer/extent_writer.h"
#include "update_engine/payload_consumer/file_descriptor.h"
#include "update_engine/payload_consumer/install_operation_executor.h"
#include "update_engine/payload_consumer/install_plan.h"
#include "update_engine/payload_consumer/partition_writer_interface.h"
#include "update_engine/payload_consumer/verified_source_fd.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {
class PartitionWriter : public PartitionWriterInterface {
 public:
  PartitionWriter(const PartitionUpdate& partition_update,
                  const InstallPlan::Partition& install_part,
                  DynamicPartitionControlInterface* dynamic_control,
                  size_t block_size,
                  bool is_interactive);
  ~PartitionWriter();
  static bool ValidateSourceHash(const brillo::Blob& calculated_hash,
                                 const InstallOperation& operation,
                                 const FileDescriptorPtr source_fd,
                                 ErrorCode* error);
  static bool ValidateSourceHash(const InstallOperation& operation,
                                 const FileDescriptorPtr source_fd,
                                 size_t block_size,
                                 ErrorCode* error);

  // Perform necessary initialization work before InstallOperation can be
  // applied to this partition
  [[nodiscard]] bool Init(const InstallPlan* install_plan,
                          bool source_may_exist,
                          size_t next_op_index) override;

  // |CheckpointUpdateProgress| will be called after SetNextOpIndex(), but it's
  // optional. DeltaPerformer may or may not call this everytime an operation is
  // applied.
  //   |next_op_index| is index of next operation that should be applied.
  // |next_op_index-1| is the last operation that is already applied.
  void CheckpointUpdateProgress(size_t next_op_index) override;

  // Close partition writer, when calling this function there's no guarantee
  // that all |InstallOperations| are sent to |PartitionWriter|. This function
  // will be called even if we are pausing/aborting the update.
  int Close() override;

  // These perform a specific type of operation and return true on success.
  // |error| will be set if source hash mismatch, otherwise |error| might not be
  // set even if it fails.
  [[nodiscard]] bool PerformReplaceOperation(const InstallOperation& operation,
                                             const void* data,
                                             size_t count) override;
  [[nodiscard]] bool PerformZeroOrDiscardOperation(
      const InstallOperation& operation) override;

  [[nodiscard]] bool PerformSourceCopyOperation(
      const InstallOperation& operation, ErrorCode* error) override;
  [[nodiscard]] bool PerformDiffOperation(const InstallOperation& operation,
                                          ErrorCode* error,
                                          const void* data,
                                          size_t count) override;

  // |DeltaPerformer| calls this when all Install Ops are sent to partition
  // writer. No |Perform*Operation| methods will be called in the future, and
  // the partition writer is expected to be closed soon.
  [[nodiscard]] bool FinishedInstallOps() override { return true; }

 private:
  friend class PartitionWriterTest;
  FRIEND_TEST(PartitionWriterTest, ChooseSourceFDTest);

  [[nodiscard]] bool OpenSourcePartition(uint32_t source_slot,
                                         bool source_may_exist);
  FileDescriptorPtr ChooseSourceFD(const InstallOperation& op,
                                   ErrorCode* error);

  [[nodiscard]] std::unique_ptr<ExtentWriter> CreateBaseExtentWriter();

  const PartitionUpdate& partition_update_;
  const InstallPlan::Partition& install_part_;
  DynamicPartitionControlInterface* dynamic_control_;
  // Path to source partition
  std::string source_path_;
  VerifiedSourceFd verified_source_fd_;
  // Path to target partition
  std::string target_path_;
  FileDescriptorPtr target_fd_;
  const bool interactive_;
  const size_t block_size_;

  // This instance handles decompression/bsdfif/puffdiff. It's responsible for
  // constructing data which should be written to target partition, actual
  // "writing" is handled by |PartitionWriter|
  InstallOperationExecutor install_op_executor_;
};

namespace partition_writer {
// Return a PartitionWriter instance for perform InstallOps on this partition.
// Uses VABCPartitionWriter for Virtual AB Compression
std::unique_ptr<PartitionWriterInterface> CreatePartitionWriter(
    const PartitionUpdate& partition_update,
    const InstallPlan::Partition& install_part,
    DynamicPartitionControlInterface* dynamic_control,
    size_t block_size,
    bool is_interactive,
    bool is_dynamic_partition);
}  // namespace partition_writer
}  // namespace chromeos_update_engine

#endif
