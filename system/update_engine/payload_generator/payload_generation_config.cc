//
// Copyright (C) 2015 The Android Open Source Project
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

#include "update_engine/payload_generator/payload_generation_config.h"

#include <algorithm>
#include <charconv>
#include <utility>

#include <android-base/parseint.h>
#include <base/logging.h>
#include <base/strings/string_number_conversions.h>
#include <brillo/strings/string_utils.h>
#include <libsnapshot/cow_format.h>

#include "bsdiff/constants.h"
#include "payload_consumer/payload_constants.h"
#include "update_engine/common/utils.h"
#include "update_engine/payload_generator/boot_img_filesystem.h"
#include "update_engine/payload_generator/delta_diff_generator.h"
#include "update_engine/payload_generator/delta_diff_utils.h"
#include "update_engine/payload_generator/erofs_filesystem.h"
#include "update_engine/payload_generator/ext2_filesystem.h"
#include "update_engine/payload_generator/mapfile_filesystem.h"
#include "update_engine/payload_generator/raw_filesystem.h"
#include "update_engine/payload_generator/squashfs_filesystem.h"
#include "update_engine/update_metadata.pb.h"

using std::string;

namespace chromeos_update_engine {

bool PostInstallConfig::IsEmpty() const {
  return !run && path.empty() && filesystem_type.empty() && !optional;
}

bool VerityConfig::IsEmpty() const {
  return hash_tree_data_extent.num_blocks() == 0 &&
         hash_tree_extent.num_blocks() == 0 && hash_tree_algorithm.empty() &&
         hash_tree_salt.empty() && fec_data_extent.num_blocks() == 0 &&
         fec_extent.num_blocks() == 0 && fec_roots == 0;
}

void VerityConfig::Clear() {
  hash_tree_data_extent.Clear();
  hash_tree_extent.Clear();
  hash_tree_algorithm.clear();
  hash_tree_salt.clear();
  fec_data_extent.Clear();
  fec_extent.Clear();
  fec_roots = 0;
}

bool PartitionConfig::ValidateExists() const {
  TEST_AND_RETURN_FALSE(!path.empty());
  TEST_AND_RETURN_FALSE(utils::FileExists(path.c_str()));
  TEST_AND_RETURN_FALSE(size > 0);
  // The requested size is within the limits of the file.
  TEST_AND_RETURN_FALSE(static_cast<off_t>(size) <=
                        utils::FileSize(path.c_str()));
  return true;
}

bool PartitionConfig::OpenFilesystem() {
  if (path.empty())
    return true;
  fs_interface.reset();
  if (diff_utils::IsExtFilesystem(path)) {
    fs_interface = Ext2Filesystem::CreateFromFile(path);
    // TODO(deymo): The delta generator algorithm doesn't support a block size
    // different than 4 KiB. Remove this check once that's fixed. b/26972455
    if (fs_interface) {
      TEST_AND_RETURN_FALSE(fs_interface->GetBlockSize() == kBlockSize);
      return true;
    }
  }
  fs_interface = ErofsFilesystem::CreateFromFile(path, erofs_compression_param);
  if (fs_interface) {
    TEST_AND_RETURN_FALSE(fs_interface->GetBlockSize() == kBlockSize);
    return true;
  }

  if (!mapfile_path.empty()) {
    fs_interface = MapfileFilesystem::CreateFromFile(path, mapfile_path);
    if (fs_interface) {
      TEST_AND_RETURN_FALSE(fs_interface->GetBlockSize() == kBlockSize);
      return true;
    }
  }

  fs_interface = BootImgFilesystem::CreateFromFile(path);
  if (fs_interface) {
    TEST_AND_RETURN_FALSE(fs_interface->GetBlockSize() == kBlockSize);
    return true;
  }

  fs_interface = SquashfsFilesystem::CreateFromFile(path,
                                                    /*extract_deflates=*/true);
  if (fs_interface) {
    TEST_AND_RETURN_FALSE(fs_interface->GetBlockSize() == kBlockSize);
    return true;
  }

  // Fall back to a RAW filesystem.
  TEST_AND_RETURN_FALSE(size % kBlockSize == 0);
  fs_interface = RawFilesystem::Create(
      "<" + name + "-partition>", kBlockSize, size / kBlockSize);
  return true;
}

bool ImageConfig::ValidateIsEmpty() const {
  return partitions.empty();
}

bool ImageConfig::LoadImageSize() {
  for (PartitionConfig& part : partitions) {
    if (part.path.empty())
      continue;
    part.size = utils::FileSize(part.path);
  }
  return true;
}

bool ImageConfig::LoadPostInstallConfig(const brillo::KeyValueStore& store) {
  bool found_postinstall = false;
  for (PartitionConfig& part : partitions) {
    bool run_postinstall{};
    if (!store.GetBoolean("RUN_POSTINSTALL_" + part.name, &run_postinstall) ||
        !run_postinstall)
      continue;
    found_postinstall = true;
    part.postinstall.run = true;
    store.GetString("POSTINSTALL_PATH_" + part.name, &part.postinstall.path);
    store.GetString("FILESYSTEM_TYPE_" + part.name,
                    &part.postinstall.filesystem_type);
    store.GetBoolean("POSTINSTALL_OPTIONAL_" + part.name,
                     &part.postinstall.optional);
  }
  if (!found_postinstall) {
    LOG(ERROR) << "No valid postinstall config found.";
    return false;
  }
  return true;
}

bool ImageConfig::LoadDynamicPartitionMetadata(
    const brillo::KeyValueStore& store) {
  auto metadata = std::make_unique<DynamicPartitionMetadata>();
  string buf;
  if (!store.GetString("super_partition_groups", &buf)) {
    LOG(ERROR) << "Dynamic partition info missing super_partition_groups.";
    return false;
  }
  auto group_names = brillo::string_utils::Split(buf, " ");
  for (const auto& group_name : group_names) {
    DynamicPartitionGroup* group = metadata->add_groups();
    group->set_name(group_name);
    if (!store.GetString("super_" + group_name + "_group_size", &buf) &&
        !store.GetString(group_name + "_size", &buf)) {
      LOG(ERROR) << "Missing super_" << group_name + "_group_size or "
                 << group_name << "_size.";
      return false;
    }

    uint64_t max_size{};
    if (!base::StringToUint64(buf, &max_size)) {
      LOG(ERROR) << "Group size for " << group_name << " = " << buf
                 << " is not an integer.";
      return false;
    }
    group->set_size(max_size);

    if (store.GetString("super_" + group_name + "_partition_list", &buf) ||
        store.GetString(group_name + "_partition_list", &buf)) {
      auto partition_names = brillo::string_utils::Split(buf, " ");
      for (const auto& partition_name : partition_names) {
        group->add_partition_names()->assign(partition_name);
      }
    }
  }

  bool snapshot_enabled = false;
  store.GetBoolean("virtual_ab", &snapshot_enabled);
  metadata->set_snapshot_enabled(snapshot_enabled);
  bool vabc_enabled = false;
  if (store.GetBoolean("virtual_ab_compression", &vabc_enabled) &&
      vabc_enabled) {
    LOG(INFO) << "Target build supports VABC";
    metadata->set_vabc_enabled(vabc_enabled);
  }
  // We use "gz" compression by default for VABC.
  if (metadata->vabc_enabled()) {
    std::string compression_method;
    if (store.GetString("virtual_ab_compression_method", &compression_method)) {
      LOG(INFO) << "Using VABC compression method '" << compression_method
                << "'";
    } else {
      LOG(INFO) << "No VABC compression method specified. Defaulting to 'gz'";
      compression_method = "gz";
    }
    metadata->set_vabc_compression_param(compression_method);
    std::string cow_version;
    if (!store.GetString("virtual_ab_cow_version", &cow_version)) {
      metadata->set_cow_version(android::snapshot::kCowVersionManifest);
    } else {
      uint32_t cow_version_num{};
      android::base::ParseUint(cow_version, &cow_version_num);
      metadata->set_cow_version(cow_version_num);
    }
    std::string compression_factor;
    if (store.GetString("virtual_ab_compression_factor", &compression_factor)) {
      LOG(INFO) << "Using VABC compression factor " << compression_factor;
    } else {
      LOG(INFO) << "No compression factor specified. Defaulting to 4k";
      compression_factor = "4096";
    }
    size_t compression_factor_value{};
    android::base::ParseUint(compression_factor, &compression_factor_value);
    metadata->set_compression_factor(compression_factor_value);
  }
  dynamic_partition_metadata = std::move(metadata);
  return true;
}

bool ImageConfig::ValidateDynamicPartitionMetadata() const {
  if (dynamic_partition_metadata == nullptr) {
    LOG(ERROR) << "dynamic_partition_metadata is not loaded.";
    return false;
  }

  for (const auto& group : dynamic_partition_metadata->groups()) {
    uint64_t sum_size = 0;
    for (const auto& partition_name : group.partition_names()) {
      auto partition_config = std::find_if(partitions.begin(),
                                           partitions.end(),
                                           [&partition_name](const auto& e) {
                                             return e.name == partition_name;
                                           });

      if (partition_config == partitions.end()) {
        LOG(ERROR) << "Cannot find partition " << partition_name
                   << " which is in " << group.name() << "_partition_list";
        return false;
      }
      sum_size += partition_config->size;
    }

    if (sum_size > group.size()) {
      LOG(ERROR) << "Sum of sizes in " << group.name() << "_partition_list is "
                 << sum_size << ", which is greater than " << group.name()
                 << "_size (" << group.size() << ")";
      return false;
    }
  }
  return true;
}

PayloadVersion::PayloadVersion(uint64_t major_version, uint32_t minor_version) {
  major = major_version;
  minor = minor_version;
}

bool PayloadVersion::Validate() const {
  TEST_AND_RETURN_FALSE(major == kBrilloMajorPayloadVersion);
  TEST_AND_RETURN_FALSE(minor == kFullPayloadMinorVersion ||
                        minor == kSourceMinorPayloadVersion ||
                        minor == kOpSrcHashMinorPayloadVersion ||
                        minor == kBrotliBsdiffMinorPayloadVersion ||
                        minor == kPuffdiffMinorPayloadVersion ||
                        minor == kVerityMinorPayloadVersion ||
                        minor == kPartialUpdateMinorPayloadVersion ||
                        minor == kZucchiniMinorPayloadVersion ||
                        minor == kLZ4DIFFMinorPayloadVersion);
  return true;
}

bool PayloadVersion::OperationAllowed(InstallOperation::Type operation) const {
  switch (operation) {
    // Full operations:
    case InstallOperation::REPLACE:
    case InstallOperation::REPLACE_BZ:
      // These operations were included in the original payload format.
    case InstallOperation::REPLACE_XZ:
      // These operations are included minor version 3 or newer and full
      // payloads.
      return true;

    case InstallOperation::ZERO:
    case InstallOperation::DISCARD:
      // The implementation of these operations had a bug in earlier versions
      // that prevents them from being used in any payload. We will enable
      // them for delta payloads for now.
      return minor >= kBrotliBsdiffMinorPayloadVersion;

    case InstallOperation::SOURCE_COPY:
    case InstallOperation::SOURCE_BSDIFF:
      return minor >= kSourceMinorPayloadVersion;

    case InstallOperation::BROTLI_BSDIFF:
      return minor >= kBrotliBsdiffMinorPayloadVersion;

    case InstallOperation::PUFFDIFF:
      return minor >= kPuffdiffMinorPayloadVersion;

    case InstallOperation::ZUCCHINI:
      return minor >= kZucchiniMinorPayloadVersion;
    case InstallOperation::LZ4DIFF_BSDIFF:
    case InstallOperation::LZ4DIFF_PUFFDIFF:
      return minor >= kLZ4DIFFMinorPayloadVersion;

    case InstallOperation::MOVE:
    case InstallOperation::BSDIFF:
      NOTREACHED();
  }
  return false;
}

bool PayloadVersion::IsDeltaOrPartial() const {
  return minor != kFullPayloadMinorVersion;
}

bool PayloadGenerationConfig::Validate() const {
  TEST_AND_RETURN_FALSE(version.Validate());
  TEST_AND_RETURN_FALSE(version.IsDeltaOrPartial() ==
                        (is_delta || is_partial_update));
  if (is_delta) {
    for (const PartitionConfig& part : source.partitions) {
      if (!part.path.empty()) {
        TEST_AND_RETURN_FALSE(part.ValidateExists());
        TEST_AND_RETURN_FALSE(part.size % block_size == 0);
      }
      // Source partition should not have postinstall or verity config.
      TEST_AND_RETURN_FALSE(part.postinstall.IsEmpty());
      TEST_AND_RETURN_FALSE(part.verity.IsEmpty());
    }

  } else {
    // All the "source" image fields must be empty for full payloads.
    TEST_AND_RETURN_FALSE(source.ValidateIsEmpty());
  }

  // In all cases, the target image must exists.
  for (const PartitionConfig& part : target.partitions) {
    TEST_AND_RETURN_FALSE(part.ValidateExists());
    TEST_AND_RETURN_FALSE(part.size % block_size == 0);
    if (version.minor < kVerityMinorPayloadVersion)
      TEST_AND_RETURN_FALSE(part.verity.IsEmpty());
  }

  if (version.minor < kPartialUpdateMinorPayloadVersion) {
    TEST_AND_RETURN_FALSE(!is_partial_update);
  }

  TEST_AND_RETURN_FALSE(hard_chunk_size == -1 ||
                        hard_chunk_size % block_size == 0);
  TEST_AND_RETURN_FALSE(soft_chunk_size % block_size == 0);

  TEST_AND_RETURN_FALSE(rootfs_partition_size % block_size == 0);

  return true;
}

void PayloadGenerationConfig::ParseCompressorTypes(
    const std::string& compressor_types) {
  auto types = brillo::string_utils::Split(compressor_types, ":");
  CHECK_LE(types.size(), 2UL)
      << "Only two compressor types are allowed: bz2 and brotli";
  CHECK_GT(types.size(), 0UL) << "Please pass in at least 1 valid compressor. "
                                 "Allowed values are bz2 and brotli.";
  compressors.clear();
  for (const auto& type : types) {
    if (type == "bz2") {
      compressors.emplace_back(bsdiff::CompressorType::kBZ2);
    } else if (type == "brotli") {
      compressors.emplace_back(bsdiff::CompressorType::kBrotli);
    } else {
      LOG(FATAL) << "Unknown compressor type: " << type;
    }
  }
}

bool PayloadGenerationConfig::OperationEnabled(
    InstallOperation::Type op) const noexcept {
  if (!version.OperationAllowed(op)) {
    return false;
  }
  switch (op) {
    case InstallOperation::ZUCCHINI:
      return enable_zucchini;
    case InstallOperation::LZ4DIFF_BSDIFF:
    case InstallOperation::LZ4DIFF_PUFFDIFF:
      return enable_lz4diff;
    case InstallOperation::PUFFDIFF:
      return enable_puffdiff;
    default:
      return true;
  }
}

CompressionAlgorithm PartitionConfig::ParseCompressionParam(
    std::string_view param) {
  CompressionAlgorithm algo;
  auto algo_name = param;
  const auto pos = param.find_first_of(',');
  if (pos != std::string::npos) {
    algo_name = param.substr(0, pos);
  }
  if (algo_name == "lz4") {
    algo.set_type(CompressionAlgorithm::LZ4);
    CHECK_EQ(pos, std::string::npos)
        << "Invalid compression param " << param
        << ", compression level not supported for lz4";
  } else if (algo_name == "lz4hc") {
    algo.set_type(CompressionAlgorithm::LZ4HC);
    if (pos != std::string::npos) {
      const auto level = param.substr(pos + 1);
      int level_num = 0;
      const auto [ptr, ec] =
          std::from_chars(level.data(), level.data() + level.size(), level_num);
      CHECK_EQ(ec, std::errc()) << "Failed to parse compression level " << level
                                << ", compression param: " << param;
      algo.set_level(level_num);
    } else {
      LOG(FATAL) << "Unrecognized compression type: " << algo_name
                 << ", param: " << param;
    }
  }
  return algo;
}

}  // namespace chromeos_update_engine
