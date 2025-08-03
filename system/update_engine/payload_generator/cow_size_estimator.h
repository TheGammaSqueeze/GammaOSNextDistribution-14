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
#include <cstddef>
#include <string>

#include <libsnapshot/cow_writer.h>
#include <update_engine/update_metadata.pb.h>

#include "update_engine/payload_consumer/file_descriptor.h"

namespace chromeos_update_engine {
// Given file descriptor to the target image, and list of
// operations, estimate the size of COW image if the operations are applied on
// Virtual AB Compression enabled device. This is intended to be used by update
// generators to put an estimate cow size in OTA payload. When installing an OTA
// update, libsnapshot will take this estimate as a hint to allocate spaces.
// If |xor_enabled| is true, then |source_fd| must be non-null.
android::snapshot::CowSizeInfo EstimateCowSizeInfo(
    FileDescriptorPtr source_fd,
    FileDescriptorPtr target_fd,
    const google::protobuf::RepeatedPtrField<InstallOperation>& operations,
    const google::protobuf::RepeatedPtrField<CowMergeOperation>&
        merge_operations,
    const size_t block_size,
    std::string compression,
    const size_t partition_size,
    bool xor_enabled,
    uint32_t cow_version,
    uint64_t compression_factor);

// Convert InstallOps to CowOps and apply the converted cow op to |cow_writer|
bool CowDryRun(
    FileDescriptorPtr source_fd,
    FileDescriptorPtr target_fd,
    const google::protobuf::RepeatedPtrField<InstallOperation>& operations,
    const google::protobuf::RepeatedPtrField<CowMergeOperation>&
        merge_operations,
    size_t block_size,
    android::snapshot::ICowWriter* cow_writer,
    size_t partition_size,
    bool xor_enabled);

}  // namespace chromeos_update_engine
