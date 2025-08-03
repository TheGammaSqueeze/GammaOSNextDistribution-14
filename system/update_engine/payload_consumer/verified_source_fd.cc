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
// limi

#include "update_engine/payload_consumer/verified_source_fd.h"

#include <fcntl.h>
#include <sys/stat.h>

#include <memory>
#include <utility>
#include <vector>

#include <base/strings/string_number_conversions.h>
#include <base/strings/string_util.h>
#include <base/strings/stringprintf.h>

#include "update_engine/common/error_code.h"
#include "update_engine/common/hash_calculator.h"
#include "update_engine/common/utils.h"
#include "update_engine/payload_consumer/extent_writer.h"
#include "update_engine/payload_consumer/file_descriptor.h"
#include "update_engine/payload_consumer/file_descriptor_utils.h"
#include "update_engine/payload_consumer/partition_writer.h"
#include "update_engine/update_metadata.pb.h"
#if USE_FEC
#include "update_engine/payload_consumer/fec_file_descriptor.h"
#endif

namespace chromeos_update_engine {
using std::string;

bool VerifiedSourceFd::OpenCurrentECCPartition() {
  // No support for ECC for full payloads.
  // Full payload should not have any opeartion that requires ECC partitions.
  if (source_ecc_fd_)
    return true;

  if (source_ecc_open_failure_)
    return false;

#if USE_FEC
  auto fd = std::make_shared<FecFileDescriptor>();
  if (!fd->Open(source_path_.c_str(), O_RDONLY, 0)) {
    PLOG(ERROR) << "Unable to open ECC source partition " << source_path_;
    source_ecc_open_failure_ = true;
    return false;
  }
  source_ecc_fd_ = fd;
#else
  // No support for ECC compiled.
  source_ecc_open_failure_ = true;
#endif  // USE_FEC

  return !source_ecc_open_failure_;
}

bool VerifiedSourceFd::WriteBackCorrectedSourceBlocks(
    const std::vector<unsigned char>& source_data,
    const google::protobuf::RepeatedPtrField<Extent>& extents) {
  utils::SetBlockDeviceReadOnly(source_path_, false);
  DEFER {
    utils::SetBlockDeviceReadOnly(source_path_, true);
  };
  auto fd = std::make_shared<EintrSafeFileDescriptor>();
  TEST_AND_RETURN_FALSE_ERRNO(fd->Open(source_path_.c_str(), O_RDWR));
  DirectExtentWriter writer(fd);
  TEST_AND_RETURN_FALSE(writer.Init(extents, block_size_));
  TEST_AND_RETURN_FALSE(writer.Write(source_data.data(), source_data.size()));
  return true;
}

FileDescriptorPtr VerifiedSourceFd::ChooseSourceFD(
    const InstallOperation& operation, ErrorCode* error) {
  if (source_fd_ == nullptr) {
    LOG(ERROR) << "ChooseSourceFD fail: source_fd_ == nullptr";
    return nullptr;
  }
  if (error) {
    *error = ErrorCode::kSuccess;
  }
  if (!operation.has_src_sha256_hash()) {
    // When the operation doesn't include a source hash, we attempt the error
    // corrected device first since we can't verify the block in the raw device
    // at this point, but we first need to make sure all extents are readable
    // since the error corrected device can be shorter or not available.
    if (OpenCurrentECCPartition() &&
        fd_utils::ReadAndHashExtents(
            source_ecc_fd_, operation.src_extents(), block_size_, nullptr)) {
      if (error) {
        *error = ErrorCode::kDownloadOperationHashMissingError;
      }
      return source_ecc_fd_;
    }
    return source_fd_;
  }

  brillo::Blob source_hash;
  brillo::Blob expected_source_hash(operation.src_sha256_hash().begin(),
                                    operation.src_sha256_hash().end());
  if (fd_utils::ReadAndHashExtents(
          source_fd_, operation.src_extents(), block_size_, &source_hash) &&
      source_hash == expected_source_hash) {
    return source_fd_;
  }
  if (error) {
    *error = ErrorCode::kDownloadOperationHashMismatch;
  }
  // We fall back to use the error corrected device if the hash of the raw
  // device doesn't match or there was an error reading the source partition.
  if (!OpenCurrentECCPartition()) {
    // The following function call will return false since the source hash
    // mismatches, but we still want to call it so it prints the appropriate
    // log message.
    PartitionWriter::ValidateSourceHash(
        source_hash, operation, source_fd_, error);
    return nullptr;
  }
  LOG(WARNING) << "Source hash from RAW device mismatched: found "
               << base::HexEncode(source_hash.data(), source_hash.size())
               << ", expected "
               << base::HexEncode(expected_source_hash.data(),
                                  expected_source_hash.size());

  std::vector<unsigned char> source_data;
  if (!utils::ReadExtents(
          source_ecc_fd_, operation.src_extents(), &source_data, block_size_)) {
    return nullptr;
  }
  if (!HashCalculator::RawHashOfData(source_data, &source_hash)) {
    return nullptr;
  }
  if (PartitionWriter::ValidateSourceHash(
          source_hash, operation, source_ecc_fd_, error)) {
    source_ecc_recovered_failures_++;
    if (WriteBackCorrectedSourceBlocks(source_data, operation.src_extents())) {
      if (error) {
        *error = ErrorCode::kSuccess;
      }
      return source_fd_;
    }
    return source_ecc_fd_;
  }
  return nullptr;
}

bool VerifiedSourceFd::Open() {
  source_fd_ = std::make_shared<EintrSafeFileDescriptor>();
  if (source_fd_ == nullptr)
    return false;
  if (!source_fd_->Open(source_path_.c_str(), O_RDONLY)) {
    PLOG(ERROR) << "Failed to open " << source_path_;
  }
  return true;
}

}  // namespace chromeos_update_engine
