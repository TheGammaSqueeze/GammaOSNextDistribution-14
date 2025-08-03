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

#ifndef UPDATE_ENGINE_VERIFIED_SOURCE_FD_H__
#define UPDATE_ENGINE_VERIFIED_SOURCE_FD_H__

#include <cstddef>

#include <string>
#include <utility>

#include <gtest/gtest_prod.h>
#include <update_engine/update_metadata.pb.h>

#include "update_engine/common/error_code.h"
#include "update_engine/payload_consumer/file_descriptor.h"

namespace chromeos_update_engine {

class VerifiedSourceFd {
 public:
  explicit VerifiedSourceFd(size_t block_size, std::string source_path)
      : block_size_(block_size), source_path_(std::move(source_path)) {}
  FileDescriptorPtr ChooseSourceFD(const InstallOperation& operation,
                                   ErrorCode* error);

  [[nodiscard]] bool Open();

 private:
  bool WriteBackCorrectedSourceBlocks(
      const std::vector<unsigned char>& source_data,
      const google::protobuf::RepeatedPtrField<Extent>& extents);
  bool OpenCurrentECCPartition();
  const size_t block_size_;
  const std::string source_path_;
  FileDescriptorPtr source_ecc_fd_;
  FileDescriptorPtr source_fd_;

  friend class PartitionWriterTest;
  FRIEND_TEST(PartitionWriterTest, ChooseSourceFDTest);
  // The total number of operations that failed source hash verification but
  // passed after falling back to the error-corrected |source_ecc_fd_| device.
  uint64_t source_ecc_recovered_failures_{0};

  // Whether opening the current partition as an error-corrected device failed.
  // Used to avoid re-opening the same source partition if it is not actually
  // error corrected.
  bool source_ecc_open_failure_{false};
};
}  // namespace chromeos_update_engine

#endif
