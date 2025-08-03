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
#include <update_engine/payload_consumer/partition_writer.h>

#include <fcntl.h>
#include <linux/fs.h>
#include <sys/mman.h>

#include <inttypes.h>

#include <algorithm>
#include <initializer_list>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include <base/strings/string_number_conversions.h>
#include <base/strings/string_util.h>
#include <base/strings/stringprintf.h>

#include "update_engine/common/error_code.h"
#include "update_engine/common/utils.h"
#include "update_engine/payload_consumer/cached_file_descriptor.h"
#include "update_engine/payload_consumer/extent_writer.h"
#include "update_engine/payload_consumer/file_descriptor_utils.h"
#include "update_engine/payload_consumer/install_operation_executor.h"
#include "update_engine/payload_consumer/install_plan.h"
#include "update_engine/payload_consumer/mount_history.h"
#include "update_engine/payload_generator/extent_utils.h"

namespace chromeos_update_engine {

namespace {
constexpr uint64_t kCacheSize = 1024 * 1024;  // 1MB

// Discard the tail of the block device referenced by |fd|, from the offset
// |data_size| until the end of the block device. Returns whether the data was
// discarded.

bool DiscardPartitionTail(const FileDescriptorPtr& fd, uint64_t data_size) {
  uint64_t part_size = fd->BlockDevSize();
  if (!part_size || part_size <= data_size)
    return false;

  struct blkioctl_request {
    int number;
    const char* name;
  };
  const std::initializer_list<blkioctl_request> blkioctl_requests = {
      {BLKDISCARD, "BLKDISCARD"},
      {BLKSECDISCARD, "BLKSECDISCARD"},
#ifdef BLKZEROOUT
      {BLKZEROOUT, "BLKZEROOUT"},
#endif
  };
  for (const auto& req : blkioctl_requests) {
    int error = 0;
    if (fd->BlkIoctl(req.number, data_size, part_size - data_size, &error) &&
        error == 0) {
      return true;
    }
    LOG(WARNING) << "Error discarding the last "
                 << (part_size - data_size) / 1024 << " KiB using ioctl("
                 << req.name << ")";
  }
  return false;
}

}  // namespace

// Opens path for read/write. On success returns an open FileDescriptor
// and sets *err to 0. On failure, sets *err to errno and returns nullptr.
FileDescriptorPtr OpenFile(const char* path,
                           int mode,
                           bool cache_writes,
                           int* err) {
  // Try to mark the block device read-only based on the mode. Ignore any
  // failure since this won't work when passing regular files.
  bool read_only = (mode & O_ACCMODE) == O_RDONLY;
  utils::SetBlockDeviceReadOnly(path, read_only);

  FileDescriptorPtr fd(new EintrSafeFileDescriptor());
  if (cache_writes && !read_only) {
    fd = FileDescriptorPtr(new CachedFileDescriptor(fd, kCacheSize));
    LOG(INFO) << "Caching writes.";
  }
  if (!fd->Open(path, mode, 000)) {
    *err = errno;
    PLOG(ERROR) << "Unable to open file " << path;
    return nullptr;
  }
  *err = 0;
  return fd;
}

PartitionWriter::PartitionWriter(
    const PartitionUpdate& partition_update,
    const InstallPlan::Partition& install_part,
    DynamicPartitionControlInterface* dynamic_control,
    size_t block_size,
    bool is_interactive)
    : partition_update_(partition_update),
      install_part_(install_part),
      dynamic_control_(dynamic_control),
      verified_source_fd_(block_size, install_part.source_path),
      interactive_(is_interactive),
      block_size_(block_size),
      install_op_executor_(block_size) {}

PartitionWriter::~PartitionWriter() {
  Close();
}

bool PartitionWriter::OpenSourcePartition(uint32_t source_slot,
                                          bool source_may_exist) {
  source_path_.clear();
  if (!source_may_exist) {
    return true;
  }
  if (install_part_.source_size > 0 && !install_part_.source_path.empty()) {
    source_path_ = install_part_.source_path;
    if (!verified_source_fd_.Open()) {
      LOG(ERROR) << "Unable to open source partition " << install_part_.name
                 << " on slot " << BootControlInterface::SlotName(source_slot)
                 << ", file " << source_path_;
      return false;
    }
  }
  return true;
}

bool PartitionWriter::Init(const InstallPlan* install_plan,
                           bool source_may_exist,
                           size_t next_op_index) {
  const PartitionUpdate& partition = partition_update_;
  uint32_t source_slot = install_plan->source_slot;
  uint32_t target_slot = install_plan->target_slot;
  TEST_AND_RETURN_FALSE(OpenSourcePartition(source_slot, source_may_exist));

  // We shouldn't open the source partition in certain cases, e.g. some dynamic
  // partitions in delta payload, partitions included in the full payload for
  // partial updates. Use the source size as the indicator.

  target_path_ = install_part_.target_path;
  int err{};

  int flags = O_RDWR;
  if (!interactive_)
    flags |= O_DSYNC;

  LOG(INFO) << "Opening " << target_path_ << " partition with"
            << (interactive_ ? "out" : "") << " O_DSYNC";

  target_fd_ = OpenFile(target_path_.c_str(), flags, true, &err);
  if (!target_fd_) {
    LOG(ERROR) << "Unable to open target partition "
               << partition.partition_name() << " on slot "
               << BootControlInterface::SlotName(target_slot) << ", file "
               << target_path_;
    return false;
  }

  LOG(INFO) << "Applying " << partition.operations().size()
            << " operations to partition \"" << partition.partition_name()
            << "\"";

  // Discard the end of the partition, but ignore failures.
  DiscardPartitionTail(target_fd_, install_part_.target_size);

  return true;
}

bool PartitionWriter::PerformReplaceOperation(const InstallOperation& operation,
                                              const void* data,
                                              size_t count) {
  // Setup the ExtentWriter stack based on the operation type.
  std::unique_ptr<ExtentWriter> writer = CreateBaseExtentWriter();
  return install_op_executor_.ExecuteReplaceOperation(
      operation, std::move(writer), data);
}

bool PartitionWriter::PerformZeroOrDiscardOperation(
    const InstallOperation& operation) {
#ifdef BLKZEROOUT
  int request =
      (operation.type() == InstallOperation::ZERO ? BLKZEROOUT : BLKDISCARD);
#else   // !defined(BLKZEROOUT)
  auto writer = CreateBaseExtentWriter();
  return install_op_executor_.ExecuteZeroOrDiscardOperation(operation,
                                                            writer.get());
#endif  // !defined(BLKZEROOUT)

  for (const Extent& extent : operation.dst_extents()) {
    const uint64_t start = extent.start_block() * block_size_;
    const uint64_t length = extent.num_blocks() * block_size_;
    int result = 0;
    if (target_fd_->BlkIoctl(request, start, length, &result) && result == 0) {
      continue;
    }
    // In case of failure, we fall back to writing 0 for the entire operation.
    PLOG(WARNING) << "BlkIoctl failed. Falling back to write 0s for remainder "
                     "of this operation.";
    auto writer = CreateBaseExtentWriter();
    return install_op_executor_.ExecuteZeroOrDiscardOperation(
        operation, std::move(writer));
  }
  return true;
}

bool PartitionWriter::PerformSourceCopyOperation(
    const InstallOperation& operation, ErrorCode* error) {
  // The device may optimize the SOURCE_COPY operation.
  // Being this a device-specific optimization let DynamicPartitionController
  // decide it the operation should be skipped.
  const PartitionUpdate& partition = partition_update_;

  // Invoke ChooseSourceFD with original operation, so that it can properly
  // verify source hashes. Optimized operation might contain a smaller set of
  // extents, or completely empty.
  auto source_fd = ChooseSourceFD(operation, error);
  if (*error != ErrorCode::kSuccess || source_fd == nullptr) {
    LOG(WARNING) << "Source hash mismatch detected for extents "
                 << operation.src_extents() << " on partition "
                 << partition.partition_name() << " @ " << source_path_;

    return false;
  }

  InstallOperation buf;
  const bool should_optimize = dynamic_control_->OptimizeOperation(
      partition.partition_name(), operation, &buf);
  const InstallOperation& optimized = should_optimize ? buf : operation;

  auto writer = CreateBaseExtentWriter();
  return install_op_executor_.ExecuteSourceCopyOperation(
      optimized, std::move(writer), source_fd);
}

bool PartitionWriter::PerformDiffOperation(const InstallOperation& operation,
                                           ErrorCode* error,
                                           const void* data,
                                           size_t count) {
  FileDescriptorPtr source_fd = ChooseSourceFD(operation, error);
  TEST_AND_RETURN_FALSE(source_fd != nullptr);

  auto writer = CreateBaseExtentWriter();
  return install_op_executor_.ExecuteDiffOperation(
      operation, std::move(writer), source_fd, data, count);
}

FileDescriptorPtr PartitionWriter::ChooseSourceFD(
    const InstallOperation& operation, ErrorCode* error) {
  return verified_source_fd_.ChooseSourceFD(operation, error);
}

int PartitionWriter::Close() {
  int err = 0;

  source_path_.clear();

  if (target_fd_ && !target_fd_->Close()) {
    err = errno;
    PLOG(ERROR) << "Error closing target partition";
    if (!err)
      err = 1;
  }
  target_fd_.reset();
  target_path_.clear();

  return -err;
}

void PartitionWriter::CheckpointUpdateProgress(size_t next_op_index) {
  if (target_fd_) {
    target_fd_->Flush();
  }
}

std::unique_ptr<ExtentWriter> PartitionWriter::CreateBaseExtentWriter() {
  return std::make_unique<DirectExtentWriter>(target_fd_);
}

bool PartitionWriter::ValidateSourceHash(const InstallOperation& operation,
                                         const FileDescriptorPtr source_fd,
                                         size_t block_size,
                                         ErrorCode* error) {
  brillo::Blob source_hash;
  TEST_AND_RETURN_FALSE_ERRNO(fd_utils::ReadAndHashExtents(
      source_fd, operation.src_extents(), block_size, &source_hash));
  return ValidateSourceHash(source_hash, operation, source_fd, error);
}

bool PartitionWriter::ValidateSourceHash(const brillo::Blob& calculated_hash,
                                         const InstallOperation& operation,
                                         const FileDescriptorPtr source_fd,
                                         ErrorCode* error) {
  using std::string;
  using std::vector;
  brillo::Blob expected_source_hash(operation.src_sha256_hash().begin(),
                                    operation.src_sha256_hash().end());
  if (calculated_hash != expected_source_hash) {
    LOG(ERROR) << "The hash of the source data on disk for this operation "
               << "doesn't match the expected value. This could mean that the "
               << "delta update payload was targeted for another version, or "
               << "that the source partition was modified after it was "
               << "installed, for example, by mounting a filesystem.";
    LOG(ERROR) << "Expected:   sha256|hex = "
               << base::HexEncode(expected_source_hash.data(),
                                  expected_source_hash.size());
    LOG(ERROR) << "Calculated: sha256|hex = "
               << base::HexEncode(calculated_hash.data(),
                                  calculated_hash.size());

    vector<string> source_extents;
    for (const Extent& ext : operation.src_extents()) {
      source_extents.push_back(
          base::StringPrintf("%" PRIu64 ":%" PRIu64,
                             static_cast<uint64_t>(ext.start_block()),
                             static_cast<uint64_t>(ext.num_blocks())));
    }
    LOG(ERROR) << "Operation source (offset:size) in blocks: "
               << base::JoinString(source_extents, ",");

    // Log remount history if this device is an ext4 partition.
    LogMountHistory(source_fd);
    if (error) {
      *error = ErrorCode::kDownloadStateInitializationError;
    }
    return false;
  }
  return true;
}

}  // namespace chromeos_update_engine
