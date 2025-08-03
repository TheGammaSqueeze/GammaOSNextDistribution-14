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

#include "update_engine/payload_consumer/vabc_partition_writer.h"

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include <android-base/properties.h>
#include <brillo/secure_blob.h>
#include <libsnapshot/cow_writer.h>

#include "update_engine/common/cow_operation_convert.h"
#include "update_engine/common/utils.h"
#include "update_engine/payload_consumer/extent_map.h"
#include "update_engine/payload_consumer/file_descriptor.h"
#include "update_engine/payload_consumer/install_plan.h"
#include "update_engine/payload_consumer/snapshot_extent_writer.h"
#include "update_engine/payload_consumer/xor_extent_writer.h"
#include "update_engine/payload_generator/extent_ranges.h"
#include "update_engine/payload_generator/extent_utils.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {
// Expected layout of COW file:
// === Beginning of Cow Image ===
// All Source Copy Operations
// ========== Label 0 ==========
// Operation 0 in PartitionUpdate
// ========== Label 1 ==========
// Operation 1 in PartitionUpdate
// ========== label 2 ==========
// Operation 2 in PartitionUpdate
// ========== label 3 ==========
// .
// .
// .

// When resuming, pass |next_op_index_| as label to
// |InitializeWithAppend|.
// For example, suppose we finished writing SOURCE_COPY, and we finished writing
// operation 2 completely. Update is suspended when we are half way through
// operation 3.
// |cnext_op_index_| would be 3, so we pass 3 as
// label to |InitializeWithAppend|. The CowWriter will retain all data before
// label 3, Which contains all operation 2's data, but none of operation 3's
// data.

using android::snapshot::ICowWriter;
using ::google::protobuf::RepeatedPtrField;

// Compute XOR map, a map from dst extent to corresponding merge operation
static ExtentMap<const CowMergeOperation*, ExtentLess> ComputeXorMap(
    const RepeatedPtrField<CowMergeOperation>& merge_ops) {
  ExtentMap<const CowMergeOperation*, ExtentLess> xor_map;
  for (const auto& merge_op : merge_ops) {
    if (merge_op.type() == CowMergeOperation::COW_XOR) {
      xor_map.AddExtent(merge_op.dst_extent(), &merge_op);
    }
  }
  return xor_map;
}

VABCPartitionWriter::VABCPartitionWriter(
    const PartitionUpdate& partition_update,
    const InstallPlan::Partition& install_part,
    DynamicPartitionControlInterface* dynamic_control,
    size_t block_size)
    : partition_update_(partition_update),
      install_part_(install_part),
      dynamic_control_(dynamic_control),
      block_size_(block_size),
      executor_(block_size),
      verified_source_fd_(block_size, install_part.source_path) {
  for (const auto& cow_op : partition_update_.merge_operations()) {
    if (cow_op.type() != CowMergeOperation::COW_COPY) {
      continue;
    }
    copy_blocks_.AddExtent(cow_op.dst_extent());
  }
  LOG(INFO) << "Partition `" << partition_update.partition_name() << "` has "
            << copy_blocks_.blocks() << " copy blocks";
}

bool VABCPartitionWriter::ProcessSourceCopyOperation(
    const InstallOperation& operation,
    const size_t block_size,
    const ExtentRanges& copy_blocks,
    const FileDescriptorPtr& source_fd,
    android::snapshot::ICowWriter* cow_writer,
    bool sequence_op_supported) {
  // COPY ops are already handled during Init(), no need to do actual work, but
  // we still want to verify that all blocks contain expected data.
  TEST_AND_RETURN_FALSE(source_fd != nullptr);
  std::vector<CowOperation> converted;

  const auto& src_extents = operation.src_extents();
  const auto& dst_extents = operation.dst_extents();
  BlockIterator it1{src_extents};
  BlockIterator it2{dst_extents};
  const bool userSnapshots = android::base::GetBoolProperty(
      "ro.virtual_ab.userspace.snapshots.enabled", false);
  // For devices not supporting XOR, sequence op is not supported, so all COPY
  // operations are written up front in strict merge order.
  while (!it1.is_end() && !it2.is_end()) {
    const auto src_block = *it1;
    const auto dst_block = *it2;
    ++it1;
    ++it2;
    if (src_block == dst_block) {
      continue;
    }
    if (copy_blocks.ContainsBlock(dst_block)) {
      if (sequence_op_supported) {
        push_back(&converted, {CowOperation::CowCopy, src_block, dst_block, 1});
      }
    } else {
      push_back(&converted,
                {CowOperation::CowReplace, src_block, dst_block, 1});
    }
  }
  std::vector<uint8_t> buffer;
  for (const auto& cow_op : converted) {
    if (cow_op.op == CowOperation::CowCopy) {
      if (userSnapshots) {
        cow_writer->AddCopy(
            cow_op.dst_block, cow_op.src_block, cow_op.block_count);
      } else {
        // Add blocks in reverse order, because snapused specifically prefers
        // this ordering. Since we already eliminated all self-overlapping
        // SOURCE_COPY during delta generation, this should be safe to do.
        for (size_t i = cow_op.block_count; i > 0; i--) {
          TEST_AND_RETURN_FALSE(cow_writer->AddCopy(cow_op.dst_block + i - 1,
                                                    cow_op.src_block + i - 1));
        }
      }
      continue;
    }
    buffer.resize(block_size * cow_op.block_count);
    ssize_t bytes_read = 0;
    TEST_AND_RETURN_FALSE(utils::ReadAll(source_fd,
                                         buffer.data(),
                                         block_size * cow_op.block_count,
                                         cow_op.src_block * block_size,
                                         &bytes_read));
    if (bytes_read <= 0 || static_cast<size_t>(bytes_read) != buffer.size()) {
      LOG(ERROR) << "source_fd->Read failed: " << bytes_read
                 << "\ncow op: " << cow_op.op;
      return false;
    }
    TEST_AND_RETURN_FALSE(cow_writer->AddRawBlocks(
        cow_op.dst_block, buffer.data(), buffer.size()));
  }
  return true;
}

bool VABCPartitionWriter::DoesDeviceSupportsXor() {
  return dynamic_control_->GetVirtualAbCompressionXorFeatureFlag().IsEnabled();
}

bool VABCPartitionWriter::WriteAllCopyOps() {
  const bool userSnapshots = android::base::GetBoolProperty(
      "ro.virtual_ab.userspace.snapshots.enabled", false);
  for (const auto& cow_op : partition_update_.merge_operations()) {
    if (cow_op.type() != CowMergeOperation::COW_COPY) {
      continue;
    }
    if (cow_op.dst_extent() == cow_op.src_extent()) {
      continue;
    }
    if (userSnapshots) {
      TEST_AND_RETURN_FALSE(cow_op.src_extent().num_blocks() != 0);
      TEST_AND_RETURN_FALSE(
          cow_writer_->AddCopy(cow_op.dst_extent().start_block(),
                               cow_op.src_extent().start_block(),
                               cow_op.src_extent().num_blocks()));
    } else {
      // Add blocks in reverse order, because snapused specifically prefers
      // this ordering. Since we already eliminated all self-overlapping
      // SOURCE_COPY during delta generation, this should be safe to do.
      for (size_t i = cow_op.src_extent().num_blocks(); i > 0; i--) {
        TEST_AND_RETURN_FALSE(
            cow_writer_->AddCopy(cow_op.dst_extent().start_block() + i - 1,
                                 cow_op.src_extent().start_block() + i - 1));
      }
    }
  }
  return true;
}

bool VABCPartitionWriter::Init(const InstallPlan* install_plan,
                               bool source_may_exist,
                               size_t next_op_index) {
  if (dynamic_control_->GetVirtualAbCompressionXorFeatureFlag().IsEnabled()) {
    xor_map_ = ComputeXorMap(partition_update_.merge_operations());
    if (xor_map_.size() > 0) {
      LOG(INFO) << "Virtual AB Compression with XOR is enabled";
    } else {
      LOG(INFO) << "Device supports Virtual AB compression with XOR, but OTA "
                   "package does not.";
    }
  } else {
    LOG(INFO) << "Virtual AB Compression with XOR is disabled.";
  }
  TEST_AND_RETURN_FALSE(install_plan != nullptr);
  if (source_may_exist && install_part_.source_size > 0) {
    TEST_AND_RETURN_FALSE(!install_part_.source_path.empty());
    TEST_AND_RETURN_FALSE(verified_source_fd_.Open());
  }
  std::optional<std::string> source_path;
  if (!install_part_.source_path.empty()) {
    // TODO(zhangkelvin) Make |source_path| a std::optional<std::string>
    source_path = install_part_.source_path;
  }

  // ===== Resume case handling code goes here ====
  // It is possible that the SOURCE_COPY are already written but
  // |next_op_index_| is still 0. In this case we discard previously written
  // SOURCE_COPY, and start over.
  std::optional<uint64_t> label;
  if (install_plan->is_resume && next_op_index > 0) {
    LOG(INFO) << "Resuming update on partition `"
              << partition_update_.partition_name() << "` op index "
              << next_op_index;
    label = {next_op_index};
  }

  cow_writer_ =
      dynamic_control_->OpenCowWriter(install_part_.name, source_path, label);
  TEST_AND_RETURN_FALSE(cow_writer_ != nullptr);

  if (label) {
    return true;
  }

  // ==============================================
  if (!partition_update_.merge_operations().empty()) {
    if (IsXorEnabled()) {
      LOG(INFO) << "VABC XOR enabled for partition "
                << partition_update_.partition_name();
    }
    // When merge sequence is present in COW, snapuserd will merge blocks in
    // order specified by the merge seuqnece op. Hence we have the freedom of
    // writing COPY operations out of order. Delay processing of copy ops so
    // that update_engine can be more responsive in progress updates.
    if (DoesDeviceSupportsXor()) {
      LOG(INFO) << "Snapuserd supports XOR and merge sequence, writing merge "
                   "sequence and delay writing COPY operations";
      TEST_AND_RETURN_FALSE(WriteMergeSequence(
          partition_update_.merge_operations(), cow_writer_.get()));
    } else {
      LOG(INFO) << "Snapuserd does not support merge sequence, writing all "
                   "COPY operations up front, this may take few "
                   "minutes.";
      TEST_AND_RETURN_FALSE(WriteAllCopyOps());
    }
    cow_writer_->AddLabel(0);
  }
  return true;
}

bool VABCPartitionWriter::WriteMergeSequence(
    const RepeatedPtrField<CowMergeOperation>& merge_sequence,
    ICowWriter* cow_writer) {
  std::vector<uint32_t> blocks_merge_order;
  for (const auto& merge_op : merge_sequence) {
    const auto& dst_extent = merge_op.dst_extent();
    const auto& src_extent = merge_op.src_extent();
    // In place copy are basically noops, they do not need to be "merged" at
    // all, don't include them in merge sequence.
    if (merge_op.type() == CowMergeOperation::COW_COPY &&
        merge_op.src_extent() == merge_op.dst_extent()) {
      continue;
    }

    const bool extent_overlap =
        ExtentRanges::ExtentsOverlap(src_extent, dst_extent);
    // TODO(193863443) Remove this check once this feature
    // lands on all pixel devices.
    const bool is_ascending = android::base::GetBoolProperty(
        "ro.virtual_ab.userspace.snapshots.enabled", false);

    // If this is a self-overlapping op and |dst_extent| comes after
    // |src_extent|, we must write in reverse order for correctness.
    //
    // If this is self-overlapping op and |dst_extent| comes before
    // |src_extent|, we must write in ascending order for correctness.
    //
    // If this isn't a self overlapping op, write block in ascending order
    // if userspace snapshots are enabled
    if (extent_overlap) {
      if (dst_extent.start_block() <= src_extent.start_block()) {
        for (size_t i = 0; i < dst_extent.num_blocks(); i++) {
          blocks_merge_order.push_back(dst_extent.start_block() + i);
        }
      } else {
        for (int i = dst_extent.num_blocks() - 1; i >= 0; i--) {
          blocks_merge_order.push_back(dst_extent.start_block() + i);
        }
      }
    } else {
      if (is_ascending) {
        for (size_t i = 0; i < dst_extent.num_blocks(); i++) {
          blocks_merge_order.push_back(dst_extent.start_block() + i);
        }
      } else {
        for (int i = dst_extent.num_blocks() - 1; i >= 0; i--) {
          blocks_merge_order.push_back(dst_extent.start_block() + i);
        }
      }
    }
  }
  return cow_writer->AddSequenceData(blocks_merge_order.size(),
                                     blocks_merge_order.data());
}

std::unique_ptr<ExtentWriter> VABCPartitionWriter::CreateBaseExtentWriter() {
  return std::make_unique<SnapshotExtentWriter>(cow_writer_.get());
}

[[nodiscard]] bool VABCPartitionWriter::PerformZeroOrDiscardOperation(
    const InstallOperation& operation) {
  for (const auto& extent : operation.dst_extents()) {
    TEST_AND_RETURN_FALSE(
        cow_writer_->AddZeroBlocks(extent.start_block(), extent.num_blocks()));
  }
  return true;
}

[[nodiscard]] bool VABCPartitionWriter::PerformSourceCopyOperation(
    const InstallOperation& operation, ErrorCode* error) {
  auto source_fd = verified_source_fd_.ChooseSourceFD(operation, error);

  return ProcessSourceCopyOperation(operation,
                                    block_size_,
                                    copy_blocks_,
                                    source_fd,
                                    cow_writer_.get(),
                                    DoesDeviceSupportsXor());
}

bool VABCPartitionWriter::PerformReplaceOperation(const InstallOperation& op,
                                                  const void* data,
                                                  size_t count) {
  // Setup the ExtentWriter stack based on the operation type.
  std::unique_ptr<ExtentWriter> writer = CreateBaseExtentWriter();

  return executor_.ExecuteReplaceOperation(op, std::move(writer), data);
}

bool VABCPartitionWriter::PerformDiffOperation(
    const InstallOperation& operation,
    ErrorCode* error,
    const void* data,
    size_t count) {
  FileDescriptorPtr source_fd =
      verified_source_fd_.ChooseSourceFD(operation, error);
  TEST_AND_RETURN_FALSE(source_fd != nullptr);
  TEST_AND_RETURN_FALSE(source_fd->IsOpen());

  std::unique_ptr<ExtentWriter> writer =
      IsXorEnabled() ? std::make_unique<XORExtentWriter>(
                           operation,
                           source_fd,
                           cow_writer_.get(),
                           xor_map_,
                           partition_update_.old_partition_info().size())
                     : CreateBaseExtentWriter();
  return executor_.ExecuteDiffOperation(
      operation, std::move(writer), source_fd, data, count);
}

void VABCPartitionWriter::CheckpointUpdateProgress(size_t next_op_index) {
  // No need to call fsync/sync, as CowWriter flushes after a label is added
  // added.
  // if cow_writer_ failed, that means Init() failed. This function shouldn't be
  // called if Init() fails.
  TEST_AND_RETURN(cow_writer_ != nullptr);
  cow_writer_->AddLabel(next_op_index);
}

[[nodiscard]] bool VABCPartitionWriter::FinishedInstallOps() {
  // Add a hardcoded magic label to indicate end of all install ops. This label
  // is needed by filesystem verification, don't remove.
  TEST_AND_RETURN_FALSE(cow_writer_ != nullptr);
  TEST_AND_RETURN_FALSE(cow_writer_->AddLabel(kEndOfInstallLabel));
  TEST_AND_RETURN_FALSE(cow_writer_->Finalize());

  auto cow_reader = cow_writer_->OpenReader();
  TEST_AND_RETURN_FALSE(cow_reader);
  TEST_AND_RETURN_FALSE(cow_reader->VerifyMergeOps());
  return true;
}

VABCPartitionWriter::~VABCPartitionWriter() {
  Close();
}

int VABCPartitionWriter::Close() {
  if (cow_writer_) {
    LOG(INFO) << "Finalizing " << partition_update_.partition_name()
              << " COW image";
    if (!cow_writer_->Finalize()) {
      return -errno;
    }
    cow_writer_ = nullptr;
  }
  return 0;
}

}  // namespace chromeos_update_engine
