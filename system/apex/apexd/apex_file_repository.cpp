/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "apex_file_repository.h"

#include <android-base/file.h>
#include <android-base/properties.h>
#include <android-base/result.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <microdroid/metadata.h>

#include <unordered_map>

#include "apex_constants.h"
#include "apex_file.h"
#include "apexd_utils.h"
#include "apexd_verity.h"

using android::base::EndsWith;
using android::base::Error;
using android::base::GetProperty;
using android::base::Result;

namespace android {
namespace apex {

std::string ConsumeApexPackageSuffix(const std::string& path) {
  std::string_view path_view(path);
  android::base::ConsumeSuffix(&path_view, kApexPackageSuffix);
  android::base::ConsumeSuffix(&path_view, kCompressedApexPackageSuffix);
  return std::string(path_view);
}

std::string GetApexSelectFilenameFromProp(
    const std::vector<std::string>& prefixes, const std::string& apex_name) {
  for (const std::string& prefix : prefixes) {
    const std::string& filename = GetProperty(prefix + apex_name, "");
    if (filename != "") {
      return ConsumeApexPackageSuffix(filename);
    }
  }
  return "";
}

Result<void> ApexFileRepository::ScanBuiltInDir(const std::string& dir) {
  LOG(INFO) << "Scanning " << dir << " for pre-installed ApexFiles";
  if (access(dir.c_str(), F_OK) != 0 && errno == ENOENT) {
    LOG(WARNING) << dir << " does not exist. Skipping";
    return {};
  }

  Result<std::vector<std::string>> all_apex_files = FindFilesBySuffix(
      dir, {kApexPackageSuffix, kCompressedApexPackageSuffix});
  if (!all_apex_files.ok()) {
    return all_apex_files.error();
  }

  // TODO(b/179248390): scan parallelly if possible
  for (const auto& file : *all_apex_files) {
    LOG(INFO) << "Found pre-installed APEX " << file;
    Result<ApexFile> apex_file = ApexFile::Open(file);
    if (!apex_file.ok()) {
      return Error() << "Failed to open " << file << " : " << apex_file.error();
    }

    const std::string& name = apex_file->GetManifest().name();

    // Check if this APEX name is treated as a multi-install APEX.
    //
    // Note: apexd is a oneshot service which runs at boot, but can be restarted
    // when needed (such as staging an APEX update). If a multi-install select
    // property changes between boot and when apexd restarts, the LOG messages
    // below will report the version that will be activated on next reboot,
    // which may differ from the currently-active version.
    std::string select_filename = GetApexSelectFilenameFromProp(
        multi_install_select_prop_prefixes_, name);
    if (!select_filename.empty()) {
      std::string path;
      if (!android::base::Realpath(apex_file->GetPath(), &path)) {
        LOG(ERROR) << "Unable to resolve realpath of APEX with path "
                   << apex_file->GetPath();
        continue;
      }
      if (enforce_multi_install_partition_ &&
          !android::base::StartsWith(path, "/vendor/apex/")) {
        LOG(ERROR) << "Multi-install APEX " << path
                   << " can only be preinstalled on /vendor/apex/.";
        continue;
      }

      auto& keys = multi_install_public_keys_[name];
      keys.insert(apex_file->GetBundledPublicKey());
      if (keys.size() > 1) {
        LOG(ERROR) << "Multi-install APEXes for " << name
                   << " have different public keys.";
        // If any versions of a multi-installed APEX differ in public key,
        // then no version should be installed.
        if (auto it = pre_installed_store_.find(name);
            it != pre_installed_store_.end()) {
          pre_installed_store_.erase(it);
        }
        continue;
      }

      if (ConsumeApexPackageSuffix(android::base::Basename(path)) ==
          select_filename) {
        LOG(INFO) << "Found APEX at path " << path << " for multi-install APEX "
                  << name;
        // Add the APEX file to the store if its filename matches the property.
        pre_installed_store_.emplace(name, std::move(*apex_file));
      } else {
        LOG(INFO) << "Skipping APEX at path " << path
                  << " because it does not match expected multi-install"
                  << " APEX property for " << name;
      }

      continue;
    }

    auto it = pre_installed_store_.find(name);
    if (it == pre_installed_store_.end()) {
      pre_installed_store_.emplace(name, std::move(*apex_file));
    } else if (it->second.GetPath() != apex_file->GetPath()) {
      auto level = base::FATAL;
      if (ignore_duplicate_apex_definitions_) {
        level = base::INFO;
      }
      // On some development (non-REL) builds the VNDK apex could be in /vendor.
      // When testing CTS-on-GSI on these builds, there would be two VNDK apexes
      // in the system, one in /system and one in /vendor.
      static constexpr char kVndkApexModuleNamePrefix[] = "com.android.vndk.";
      static constexpr char kPlatformVersionCodenameProperty[] =
          "ro.build.version.codename";
      if (android::base::StartsWith(name, kVndkApexModuleNamePrefix) &&
          GetProperty(kPlatformVersionCodenameProperty, "REL") != "REL") {
        level = android::base::INFO;
      }
      LOG(level) << "Found two apex packages " << it->second.GetPath()
                 << " and " << apex_file->GetPath()
                 << " with the same module name " << name;
    } else if (it->second.GetBundledPublicKey() !=
               apex_file->GetBundledPublicKey()) {
      LOG(FATAL) << "Public key of apex package " << it->second.GetPath()
                 << " (" << name << ") has unexpectedly changed";
    }
  }
  multi_install_public_keys_.clear();
  return {};
}

ApexFileRepository& ApexFileRepository::GetInstance() {
  static ApexFileRepository instance;
  return instance;
}

android::base::Result<void> ApexFileRepository::AddPreInstalledApex(
    const std::vector<std::string>& prebuilt_dirs) {
  for (const auto& dir : prebuilt_dirs) {
    if (auto result = ScanBuiltInDir(dir); !result.ok()) {
      return result.error();
    }
  }
  return {};
}

Result<int> ApexFileRepository::AddBlockApex(
    const std::string& metadata_partition) {
  CHECK(!block_disk_path_.has_value())
      << "AddBlockApex() can't be called twice.";

  auto metadata_ready = WaitForFile(metadata_partition, kBlockApexWaitTime);
  if (!metadata_ready.ok()) {
    LOG(ERROR) << "Error waiting for metadata_partition : "
               << metadata_ready.error();
    return {};
  }

  // TODO(b/185069443) consider moving the logic to find disk_path from
  // metadata_partition to its own library
  LOG(INFO) << "Scanning " << metadata_partition << " for host apexes";
  if (access(metadata_partition.c_str(), F_OK) != 0 && errno == ENOENT) {
    LOG(WARNING) << metadata_partition << " does not exist. Skipping";
    return {};
  }

  std::string metadata_realpath;
  if (!android::base::Realpath(metadata_partition, &metadata_realpath)) {
    LOG(WARNING) << "Can't get realpath of " << metadata_partition
                 << ". Skipping";
    return {};
  }

  std::string_view metadata_path_view(metadata_realpath);
  if (!android::base::ConsumeSuffix(&metadata_path_view, "1")) {
    LOG(WARNING) << metadata_realpath << " is not a first partition. Skipping";
    return {};
  }

  block_disk_path_ = std::string(metadata_path_view);

  // Read the payload metadata.
  // "metadata" can be overridden by microdroid_manager. To ensure that
  // "microdroid" is started with the same/unmodified set of host APEXes,
  // microdroid stores APEXes' pubkeys in its encrypted instance disk. Next
  // time, microdroid checks if there's pubkeys in the instance disk and use
  // them to activate APEXes. Microdroid_manager passes pubkeys in instance.img
  // via the following file.
  if (auto exists = PathExists("/apex/vm-payload-metadata");
      exists.ok() && *exists) {
    metadata_realpath = "/apex/vm-payload-metadata";
    LOG(INFO) << "Overriding metadata to " << metadata_realpath;
  }
  auto metadata = android::microdroid::ReadMetadata(metadata_realpath);
  if (!metadata.ok()) {
    LOG(WARNING) << "Failed to load metadata from " << metadata_realpath
                 << ". Skipping: " << metadata.error();
    return {};
  }

  int ret = 0;

  // subsequent partitions are APEX archives.
  static constexpr const int kFirstApexPartition = 2;
  for (int i = 0; i < metadata->apexes_size(); i++) {
    const auto& apex_config = metadata->apexes(i);

    const std::string apex_path =
        *block_disk_path_ + std::to_string(i + kFirstApexPartition);

    auto apex_ready = WaitForFile(apex_path, kBlockApexWaitTime);
    if (!apex_ready.ok()) {
      return Error() << "Error waiting for apex file : " << apex_ready.error();
    }

    auto apex_file = ApexFile::Open(apex_path);
    if (!apex_file.ok()) {
      return Error() << "Failed to open " << apex_path << " : "
                     << apex_file.error();
    }

    // When metadata specifies the public key of the apex, it should match the
    // bundled key. Otherwise we accept it.
    if (apex_config.public_key() != "" &&
        apex_config.public_key() != apex_file->GetBundledPublicKey()) {
      return Error() << "public key doesn't match: " << apex_path;
    }

    const std::string& name = apex_file->GetManifest().name();

    // When metadata specifies the manifest name and version of the apex, it
    // should match what we see in the manifest.
    if (apex_config.manifest_name() != "" &&
        apex_config.manifest_name() != name) {
      return Error() << "manifest name doesn't match: " << apex_path;
    }

    if (apex_config.manifest_version() != 0 &&
        apex_config.manifest_version() != apex_file->GetManifest().version()) {
      return Error() << "manifest version doesn't match: " << apex_path;
    }

    BlockApexOverride overrides;

    // A block device doesn't have an inherent timestamp, so it is carried in
    // the metadata.
    if (int64_t last_update_seconds = apex_config.last_update_seconds();
        last_update_seconds != 0) {
      overrides.last_update_seconds = last_update_seconds;
    }

    // When metadata specifies the root digest of the apex, it should be used
    // when activating the apex. So we need to keep it.
    if (auto root_digest = apex_config.root_digest(); root_digest != "") {
      overrides.block_apex_root_digest =
          BytesToHex(reinterpret_cast<const uint8_t*>(root_digest.data()),
                     root_digest.size());
    }

    if (overrides.last_update_seconds.has_value() ||
        overrides.block_apex_root_digest.has_value()) {
      block_apex_overrides_.emplace(apex_path, std::move(overrides));
    }

    // Depending on whether the APEX was a factory version in the host or not,
    // put it to different stores.
    auto& store = apex_config.is_factory() ? pre_installed_store_ : data_store_;
    // We want "uniqueness" in each store.
    if (auto it = store.find(name); it != store.end()) {
      return Error() << "duplicate of " << name << " found in "
                     << it->second.GetPath();
    }
    store.emplace(name, std::move(*apex_file));

    ret++;
  }
  return {ret};
}

// TODO(b/179497746): AddDataApex should not concern with filtering out invalid
//   apex.
Result<void> ApexFileRepository::AddDataApex(const std::string& data_dir) {
  LOG(INFO) << "Scanning " << data_dir << " for data ApexFiles";
  if (access(data_dir.c_str(), F_OK) != 0 && errno == ENOENT) {
    LOG(WARNING) << data_dir << " does not exist. Skipping";
    return {};
  }

  Result<std::vector<std::string>> active_apex =
      FindFilesBySuffix(data_dir, {kApexPackageSuffix});
  if (!active_apex.ok()) {
    return active_apex.error();
  }

  // TODO(b/179248390): scan parallelly if possible
  for (const auto& file : *active_apex) {
    LOG(INFO) << "Found updated apex " << file;
    Result<ApexFile> apex_file = ApexFile::Open(file);
    if (!apex_file.ok()) {
      LOG(ERROR) << "Failed to open " << file << " : " << apex_file.error();
      continue;
    }

    const std::string& name = apex_file->GetManifest().name();
    if (!HasPreInstalledVersion(name)) {
      LOG(ERROR) << "Skipping " << file << " : no preinstalled apex";
      // Ignore data apex without corresponding pre-installed apex
      continue;
    }

    std::string select_filename = GetApexSelectFilenameFromProp(
        multi_install_select_prop_prefixes_, name);
    if (!select_filename.empty()) {
      LOG(WARNING) << "APEX " << name << " is a multi-installed APEX."
                   << " Any updated version in /data will always overwrite"
                   << " the multi-installed preinstalled version, if possible.";
    }

    auto pre_installed_public_key = GetPublicKey(name);
    if (!pre_installed_public_key.ok() ||
        apex_file->GetBundledPublicKey() != *pre_installed_public_key) {
      // Ignore data apex if public key doesn't match with pre-installed apex
      LOG(ERROR) << "Skipping " << file
                 << " : public key doesn't match pre-installed one";
      continue;
    }

    if (EndsWith(apex_file->GetPath(), kDecompressedApexPackageSuffix)) {
      LOG(WARNING) << "Skipping " << file
                   << " : Non-decompressed APEX should not have "
                   << kDecompressedApexPackageSuffix << " suffix";
      continue;
    }

    auto it = data_store_.find(name);
    if (it == data_store_.end()) {
      data_store_.emplace(name, std::move(*apex_file));
      continue;
    }

    const auto& existing_version = it->second.GetManifest().version();
    const auto new_version = apex_file->GetManifest().version();
    // If multiple data apexs are preset, select the one with highest version
    bool prioritize_higher_version = new_version > existing_version;
    // For same version, non-decompressed apex gets priority
    if (prioritize_higher_version) {
      it->second = std::move(*apex_file);
    }
  }
  return {};
}

// TODO(b/179497746): remove this method when we add api for fetching ApexFile
//  by name
Result<const std::string> ApexFileRepository::GetPublicKey(
    const std::string& name) const {
  auto it = pre_installed_store_.find(name);
  if (it == pre_installed_store_.end()) {
    // Special casing for APEXes backed by block devices, i.e. APEXes in VM.
    // Inside a VM, we fall back to find the key from data_store_. This is
    // because an APEX is put to either pre_installed_store_ or data_store,
    // depending on whether it was a factory APEX or not in the host.
    it = data_store_.find(name);
    if (it != data_store_.end() && IsBlockApex(it->second)) {
      return it->second.GetBundledPublicKey();
    }
    return Error() << "No preinstalled apex found for package " << name;
  }
  return it->second.GetBundledPublicKey();
}

// TODO(b/179497746): remove this method when we add api for fetching ApexFile
//  by name
Result<const std::string> ApexFileRepository::GetPreinstalledPath(
    const std::string& name) const {
  auto it = pre_installed_store_.find(name);
  if (it == pre_installed_store_.end()) {
    return Error() << "No preinstalled data found for package " << name;
  }
  return it->second.GetPath();
}

// TODO(b/179497746): remove this method when we add api for fetching ApexFile
//  by name
Result<const std::string> ApexFileRepository::GetDataPath(
    const std::string& name) const {
  auto it = data_store_.find(name);
  if (it == data_store_.end()) {
    return Error() << "No data apex found for package " << name;
  }
  return it->second.GetPath();
}

std::optional<std::string> ApexFileRepository::GetBlockApexRootDigest(
    const std::string& path) const {
  auto it = block_apex_overrides_.find(path);
  if (it == block_apex_overrides_.end()) {
    return std::nullopt;
  }
  return it->second.block_apex_root_digest;
}

std::optional<int64_t> ApexFileRepository::GetBlockApexLastUpdateSeconds(
    const std::string& path) const {
  auto it = block_apex_overrides_.find(path);
  if (it == block_apex_overrides_.end()) {
    return std::nullopt;
  }
  return it->second.last_update_seconds;
}

bool ApexFileRepository::HasPreInstalledVersion(const std::string& name) const {
  return pre_installed_store_.find(name) != pre_installed_store_.end();
}

bool ApexFileRepository::HasDataVersion(const std::string& name) const {
  return data_store_.find(name) != data_store_.end();
}

// ApexFile is considered a decompressed APEX if it is located in decompression
// dir
bool ApexFileRepository::IsDecompressedApex(const ApexFile& apex) const {
  return apex.GetPath().starts_with(decompression_dir_);
}

bool ApexFileRepository::IsPreInstalledApex(const ApexFile& apex) const {
  auto it = pre_installed_store_.find(apex.GetManifest().name());
  if (it == pre_installed_store_.end()) {
    return false;
  }
  return it->second.GetPath() == apex.GetPath() || IsDecompressedApex(apex);
}

bool ApexFileRepository::IsBlockApex(const ApexFile& apex) const {
  return block_disk_path_.has_value() &&
         apex.GetPath().starts_with(*block_disk_path_);
}

std::vector<ApexFileRef> ApexFileRepository::GetPreInstalledApexFiles() const {
  std::vector<ApexFileRef> result;
  result.reserve(pre_installed_store_.size());
  for (const auto& it : pre_installed_store_) {
    result.emplace_back(std::cref(it.second));
  }
  return std::move(result);
}

std::vector<ApexFileRef> ApexFileRepository::GetDataApexFiles() const {
  std::vector<ApexFileRef> result;
  result.reserve(data_store_.size());
  for (const auto& it : data_store_) {
    result.emplace_back(std::cref(it.second));
  }
  return std::move(result);
}

// Group pre-installed APEX and data APEX by name
std::unordered_map<std::string, std::vector<ApexFileRef>>
ApexFileRepository::AllApexFilesByName() const {
  // Collect all apex files
  std::vector<ApexFileRef> all_apex_files;
  auto pre_installed_apexs = GetPreInstalledApexFiles();
  auto data_apexs = GetDataApexFiles();
  std::move(pre_installed_apexs.begin(), pre_installed_apexs.end(),
            std::back_inserter(all_apex_files));
  std::move(data_apexs.begin(), data_apexs.end(),
            std::back_inserter(all_apex_files));

  // Group them by name
  std::unordered_map<std::string, std::vector<ApexFileRef>> result;
  for (const auto& apex_file_ref : all_apex_files) {
    const ApexFile& apex_file = apex_file_ref.get();
    const std::string& package_name = apex_file.GetManifest().name();
    if (result.find(package_name) == result.end()) {
      result[package_name] = std::vector<ApexFileRef>{};
    }
    result[package_name].emplace_back(apex_file_ref);
  }

  return std::move(result);
}

ApexFileRef ApexFileRepository::GetDataApex(const std::string& name) const {
  auto it = data_store_.find(name);
  CHECK(it != data_store_.end());
  return std::cref(it->second);
}

ApexFileRef ApexFileRepository::GetPreInstalledApex(
    const std::string& name) const {
  auto it = pre_installed_store_.find(name);
  CHECK(it != pre_installed_store_.end());
  return std::cref(it->second);
}

}  // namespace apex
}  // namespace android
