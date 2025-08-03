/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef ANDROID_APEXD_APEX_FILE_H_
#define ANDROID_APEXD_APEX_FILE_H_

#include <memory>
#include <string>
#include <vector>

#include <android-base/result.h>
#include <libavb/libavb.h>

#include "apex_manifest.h"

namespace android {
namespace apex {

// Data needed to construct a valid VerityTable
struct ApexVerityData {
  std::unique_ptr<AvbHashtreeDescriptor> desc;
  std::string hash_algorithm;
  std::string salt;
  std::string root_digest;
};

// Manages the content of an APEX package and provides utilities to navigate
// the content.
class ApexFile {
 public:
  static android::base::Result<ApexFile> Open(const std::string& path);

  ApexFile() = delete;
  ApexFile(ApexFile&&) = default;
  ApexFile& operator=(ApexFile&&) = default;

  const std::string& GetPath() const { return apex_path_; }
  const std::optional<uint32_t>& GetImageOffset() const {
    return image_offset_;
  }
  const std::optional<size_t>& GetImageSize() const { return image_size_; }
  const ::apex::proto::ApexManifest& GetManifest() const { return manifest_; }
  const std::string& GetBundledPublicKey() const { return apex_pubkey_; }
  const std::optional<std::string>& GetFsType() const { return fs_type_; }
  android::base::Result<ApexVerityData> VerifyApexVerity(
      const std::string& public_key) const;
  bool IsCompressed() const { return is_compressed_; }
  android::base::Result<void> Decompress(const std::string& output_path) const;

 private:
  ApexFile(const std::string& apex_path,
           const std::optional<uint32_t>& image_offset,
           const std::optional<size_t>& image_size,
           ::apex::proto::ApexManifest manifest, const std::string& apex_pubkey,
           const std::optional<std::string>& fs_type, bool is_compressed)
      : apex_path_(apex_path),
        image_offset_(image_offset),
        image_size_(image_size),
        manifest_(std::move(manifest)),
        apex_pubkey_(apex_pubkey),
        fs_type_(fs_type),
        is_compressed_(is_compressed) {}

  std::string apex_path_;
  std::optional<uint32_t> image_offset_;
  std::optional<size_t> image_size_;
  ::apex::proto::ApexManifest manifest_;
  std::string apex_pubkey_;
  std::optional<std::string> fs_type_;
  bool is_compressed_;
};

}  // namespace apex
}  // namespace android

#endif  // ANDROID_APEXD_APEX_FILE_H_
