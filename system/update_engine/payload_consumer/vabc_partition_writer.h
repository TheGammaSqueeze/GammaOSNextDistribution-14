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

#ifndef UPDATE_ENGINE_VABC_PARTITION_WRITER_H_
#define UPDATE_ENGINE_VABC_PARTITION_WRITER_H_

#include <map>
#include <memory>
#include <string>
#include <vector>

#include <libsnapshot/cow_writer.h>

#include "update_engine/payload_consumer/extent_map.h"
#include "update_engine/payload_consumer/install_operation_executor.h"
#include "update_engine/payload_consumer/install_plan.h"
#include "update_engine/payload_consumer/partition_writer_interface.h"
#include "update_engine/payload_consumer/verified_source_fd.h"
#include "update_engine/payload_generator/extent_ranges.h"

namespace chromeos_update_engine {
class VABCPartitionWriter final : public PartitionWriterInterface {
 public:
  static bool ProcessSourceCopyOperation(
      const InstallOperation& operation,
      const size_t block_size,
      const ExtentRanges& copy_blocks,
      const FileDescriptorPtr& source_fd,
      android::snapshot::ICowWriter* cow_writer,
      bool sequence_op_supported);

  VABCPartitionWriter(const PartitionUpdate& partition_update,
                      const InstallPlan::Partition& install_part,
                      DynamicPartitionControlInterface* dynamic_control,
                      size_t block_size);
  [[nodiscard]] bool Init(const InstallPlan* install_plan,
                          bool source_may_exist,
                          size_t next_op_index) override;
  ~VABCPartitionWriter() override;

  // Only ZERO and SOURCE_COPY InstallOperations are treated special by VABC
  // Partition Writer. These operations correspond to COW_ZERO and COW_COPY. All
  // other operations just get converted to COW_REPLACE.
  [[nodiscard]] bool PerformZeroOrDiscardOperation(
      const InstallOperation& operation) override;
  [[nodiscard]] bool PerformSourceCopyOperation(
      const InstallOperation& operation, ErrorCode* error) override;

  [[nodiscard]] bool PerformReplaceOperation(const InstallOperation& operation,
                                             const void* data,
                                             size_t count) override;

  [[nodiscard]] bool PerformDiffOperation(const InstallOperation& operation,
                                          ErrorCode* error,
                                          const void* data,
                                          size_t count) override;

  void CheckpointUpdateProgress(size_t next_op_index) override;

  [[nodiscard]] bool FinishedInstallOps() override;
  int Close() override;
  // Send merge sequence data to cow writer
  static bool WriteMergeSequence(
      const ::google::protobuf::RepeatedPtrField<CowMergeOperation>& merge_ops,
      android::snapshot::ICowWriter* cow_writer);

 private:
  [[nodiscard]] bool DoesDeviceSupportsXor();
  bool IsXorEnabled() const noexcept { return xor_map_.size() > 0; }
  [[nodiscard]] bool WriteAllCopyOps();
  std::unique_ptr<android::snapshot::ICowWriter> cow_writer_;

  [[nodiscard]] std::unique_ptr<ExtentWriter> CreateBaseExtentWriter();

  const PartitionUpdate& partition_update_;
  const InstallPlan::Partition& install_part_;
  DynamicPartitionControlInterface* const dynamic_control_;
  // Path to source partition
  const std::string source_path_;

  const size_t block_size_;
  InstallOperationExecutor executor_;
  VerifiedSourceFd verified_source_fd_;
  ExtentMap<const CowMergeOperation*, ExtentLess> xor_map_;
  ExtentRanges copy_blocks_;
};

}  // namespace chromeos_update_engine

#endif
