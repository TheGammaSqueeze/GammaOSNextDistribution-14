/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "path_utils.h"

#include <filesystem>
#include <string>
#include <vector>

#include "aidl/com/android/server/art/BnArtd.h"
#include "android-base/errors.h"
#include "android-base/result.h"
#include "android-base/strings.h"
#include "arch/instruction_set.h"
#include "base/file_utils.h"
#include "base/macros.h"
#include "file_utils.h"
#include "fstab/fstab.h"
#include "oat/oat_file_assistant.h"
#include "runtime_image.h"
#include "service.h"
#include "tools/tools.h"

namespace art {
namespace artd {

namespace {

using ::aidl::com::android::server::art::ArtifactsPath;
using ::aidl::com::android::server::art::DexMetadataPath;
using ::aidl::com::android::server::art::ProfilePath;
using ::aidl::com::android::server::art::RuntimeArtifactsPath;
using ::aidl::com::android::server::art::VdexPath;
using ::android::base::Error;
using ::android::base::Result;
using ::android::base::StartsWith;
using ::android::fs_mgr::Fstab;
using ::android::fs_mgr::FstabEntry;
using ::android::fs_mgr::ReadFstabFromProcMounts;
using ::art::service::ValidateDexPath;
using ::art::service::ValidatePathElement;
using ::art::service::ValidatePathElementSubstring;

using PrebuiltProfilePath = ProfilePath::PrebuiltProfilePath;
using PrimaryCurProfilePath = ProfilePath::PrimaryCurProfilePath;
using PrimaryRefProfilePath = ProfilePath::PrimaryRefProfilePath;
using SecondaryCurProfilePath = ProfilePath::SecondaryCurProfilePath;
using SecondaryRefProfilePath = ProfilePath::SecondaryRefProfilePath;
using TmpProfilePath = ProfilePath::TmpProfilePath;
using WritableProfilePath = ProfilePath::WritableProfilePath;

// Only to be changed for testing.
std::string_view gListRootDir = "/";

}  // namespace

Result<std::string> GetAndroidDataOrError() {
  std::string error_msg;
  std::string result = GetAndroidDataSafe(&error_msg);
  if (!error_msg.empty()) {
    return Error() << error_msg;
  }
  return result;
}

Result<std::string> GetAndroidExpandOrError() {
  std::string error_msg;
  std::string result = GetAndroidExpandSafe(&error_msg);
  if (!error_msg.empty()) {
    return Error() << error_msg;
  }
  return result;
}

Result<std::string> GetArtRootOrError() {
  std::string error_msg;
  std::string result = GetArtRootSafe(&error_msg);
  if (!error_msg.empty()) {
    return Error() << error_msg;
  }
  return result;
}

std::vector<std::string> ListManagedFiles(const std::string& android_data,
                                          const std::string& android_expand) {
  // See `art::tools::Glob` for the syntax.
  std::vector<std::string> patterns = {
      // Profiles for primary dex files.
      android_data + "/misc/profiles/**",
      // Artifacts for primary dex files.
      android_data + "/dalvik-cache/**",
  };

  for (const std::string& data_root : {android_data, android_expand + "/*"}) {
    // Artifacts for primary dex files.
    patterns.push_back(data_root + "/app/*/*/oat/**");

    for (const char* user_dir : {"/user", "/user_de"}) {
      std::string data_dir = data_root + user_dir + "/*/*";
      // Profiles and artifacts for secondary dex files. Those files are in app data directories, so
      // we use more granular patterns to avoid accidentally deleting apps' files.
      std::string secondary_oat_dir = data_dir + "/**/oat";
      for (const char* maybe_tmp_suffix : {"", ".*.tmp"}) {
        patterns.push_back(secondary_oat_dir + "/*.prof" + maybe_tmp_suffix);
        patterns.push_back(secondary_oat_dir + "/*/*.odex" + maybe_tmp_suffix);
        patterns.push_back(secondary_oat_dir + "/*/*.vdex" + maybe_tmp_suffix);
        patterns.push_back(secondary_oat_dir + "/*/*.art" + maybe_tmp_suffix);
      }
      // Runtime image files.
      patterns.push_back(RuntimeImage::GetRuntimeImageDir(data_dir) + "**");
    }
  }

  return tools::Glob(patterns, gListRootDir);
}

std::vector<std::string> ListRuntimeArtifactsFiles(
    const std::string& android_data,
    const std::string& android_expand,
    const RuntimeArtifactsPath& runtime_artifacts_path) {
  // See `art::tools::Glob` for the syntax.
  std::vector<std::string> patterns;

  for (const std::string& data_root : {android_data, android_expand + "/*"}) {
    for (const char* user_dir : {"/user", "/user_de"}) {
      std::string data_dir =
          data_root + user_dir + "/*/" + tools::EscapeGlob(runtime_artifacts_path.packageName);
      patterns.push_back(
          RuntimeImage::GetRuntimeImagePath(data_dir,
                                            tools::EscapeGlob(runtime_artifacts_path.dexPath),
                                            tools::EscapeGlob(runtime_artifacts_path.isa)));
    }
  }

  return tools::Glob(patterns, gListRootDir);
}

Result<void> ValidateRuntimeArtifactsPath(const RuntimeArtifactsPath& runtime_artifacts_path) {
  OR_RETURN(ValidatePathElement(runtime_artifacts_path.packageName, "packageName"));
  OR_RETURN(ValidatePathElement(runtime_artifacts_path.isa, "isa"));
  OR_RETURN(ValidateDexPath(runtime_artifacts_path.dexPath));
  return {};
}

Result<std::string> BuildArtBinPath(const std::string& binary_name) {
  return ART_FORMAT("{}/bin/{}", OR_RETURN(GetArtRootOrError()), binary_name);
}

Result<std::string> BuildOatPath(const ArtifactsPath& artifacts_path) {
  OR_RETURN(ValidateDexPath(artifacts_path.dexPath));

  InstructionSet isa = GetInstructionSetFromString(artifacts_path.isa.c_str());
  if (isa == InstructionSet::kNone) {
    return Errorf("Instruction set '{}' is invalid", artifacts_path.isa);
  }

  std::string error_msg;
  std::string path;
  if (artifacts_path.isInDalvikCache) {
    // Apps' OAT files are never in ART APEX data.
    if (!OatFileAssistant::DexLocationToOatFilename(
            artifacts_path.dexPath, isa, /*deny_art_apex_data_files=*/true, &path, &error_msg)) {
      return Error() << error_msg;
    }
    return path;
  } else {
    if (!OatFileAssistant::DexLocationToOdexFilename(
            artifacts_path.dexPath, isa, &path, &error_msg)) {
      return Error() << error_msg;
    }
    return path;
  }
}

Result<std::string> BuildPrimaryRefProfilePath(
    const PrimaryRefProfilePath& primary_ref_profile_path) {
  OR_RETURN(ValidatePathElement(primary_ref_profile_path.packageName, "packageName"));
  OR_RETURN(ValidatePathElementSubstring(primary_ref_profile_path.profileName, "profileName"));
  return ART_FORMAT("{}/misc/profiles/ref/{}/{}.prof",
                    OR_RETURN(GetAndroidDataOrError()),
                    primary_ref_profile_path.packageName,
                    primary_ref_profile_path.profileName);
}

Result<std::string> BuildPrebuiltProfilePath(const PrebuiltProfilePath& prebuilt_profile_path) {
  OR_RETURN(ValidateDexPath(prebuilt_profile_path.dexPath));
  return prebuilt_profile_path.dexPath + ".prof";
}

Result<std::string> BuildPrimaryCurProfilePath(
    const PrimaryCurProfilePath& primary_cur_profile_path) {
  OR_RETURN(ValidatePathElement(primary_cur_profile_path.packageName, "packageName"));
  OR_RETURN(ValidatePathElementSubstring(primary_cur_profile_path.profileName, "profileName"));
  return ART_FORMAT("{}/misc/profiles/cur/{}/{}/{}.prof",
                    OR_RETURN(GetAndroidDataOrError()),
                    primary_cur_profile_path.userId,
                    primary_cur_profile_path.packageName,
                    primary_cur_profile_path.profileName);
}

Result<std::string> BuildSecondaryRefProfilePath(
    const SecondaryRefProfilePath& secondary_ref_profile_path) {
  OR_RETURN(ValidateDexPath(secondary_ref_profile_path.dexPath));
  std::filesystem::path dex_path(secondary_ref_profile_path.dexPath);
  return ART_FORMAT(
      "{}/oat/{}.prof", dex_path.parent_path().string(), dex_path.filename().string());
}

Result<std::string> BuildSecondaryCurProfilePath(
    const SecondaryCurProfilePath& secondary_cur_profile_path) {
  OR_RETURN(ValidateDexPath(secondary_cur_profile_path.dexPath));
  std::filesystem::path dex_path(secondary_cur_profile_path.dexPath);
  return ART_FORMAT(
      "{}/oat/{}.cur.prof", dex_path.parent_path().string(), dex_path.filename().string());
}

Result<std::string> BuildFinalProfilePath(const TmpProfilePath& tmp_profile_path) {
  const WritableProfilePath& final_path = tmp_profile_path.finalPath;
  switch (final_path.getTag()) {
    case WritableProfilePath::forPrimary:
      return BuildPrimaryRefProfilePath(final_path.get<WritableProfilePath::forPrimary>());
    case WritableProfilePath::forSecondary:
      return BuildSecondaryRefProfilePath(final_path.get<WritableProfilePath::forSecondary>());
      // No default. All cases should be explicitly handled, or the compilation will fail.
  }
  // This should never happen. Just in case we get a non-enumerator value.
  LOG(FATAL) << ART_FORMAT("Unexpected writable profile path type {}",
                           fmt::underlying(final_path.getTag()));
}

Result<std::string> BuildTmpProfilePath(const TmpProfilePath& tmp_profile_path) {
  OR_RETURN(ValidatePathElementSubstring(tmp_profile_path.id, "id"));
  return NewFile::BuildTempPath(OR_RETURN(BuildFinalProfilePath(tmp_profile_path)),
                                tmp_profile_path.id);
}

Result<std::string> BuildDexMetadataPath(const DexMetadataPath& dex_metadata_path) {
  OR_RETURN(ValidateDexPath(dex_metadata_path.dexPath));
  return ReplaceFileExtension(dex_metadata_path.dexPath, "dm");
}

Result<std::string> BuildProfileOrDmPath(const ProfilePath& profile_path) {
  switch (profile_path.getTag()) {
    case ProfilePath::primaryRefProfilePath:
      return BuildPrimaryRefProfilePath(profile_path.get<ProfilePath::primaryRefProfilePath>());
    case ProfilePath::prebuiltProfilePath:
      return BuildPrebuiltProfilePath(profile_path.get<ProfilePath::prebuiltProfilePath>());
    case ProfilePath::primaryCurProfilePath:
      return BuildPrimaryCurProfilePath(profile_path.get<ProfilePath::primaryCurProfilePath>());
    case ProfilePath::secondaryRefProfilePath:
      return BuildSecondaryRefProfilePath(profile_path.get<ProfilePath::secondaryRefProfilePath>());
    case ProfilePath::secondaryCurProfilePath:
      return BuildSecondaryCurProfilePath(profile_path.get<ProfilePath::secondaryCurProfilePath>());
    case ProfilePath::tmpProfilePath:
      return BuildTmpProfilePath(profile_path.get<ProfilePath::tmpProfilePath>());
    case ProfilePath::dexMetadataPath:
      return BuildDexMetadataPath(profile_path.get<ProfilePath::dexMetadataPath>());
      // No default. All cases should be explicitly handled, or the compilation will fail.
  }
  // This should never happen. Just in case we get a non-enumerator value.
  LOG(FATAL) << ART_FORMAT("Unexpected profile path type {}",
                           fmt::underlying(profile_path.getTag()));
}

Result<std::string> BuildVdexPath(const VdexPath& vdex_path) {
  DCHECK(vdex_path.getTag() == VdexPath::artifactsPath);
  return OatPathToVdexPath(OR_RETURN(BuildOatPath(vdex_path.get<VdexPath::artifactsPath>())));
}

bool PathStartsWith(std::string_view path, std::string_view prefix) {
  CHECK(!prefix.empty() && !path.empty() && prefix[0] == '/' && path[0] == '/')
      << ART_FORMAT("path={}, prefix={}", path, prefix);
  android::base::ConsumeSuffix(&prefix, "/");
  return StartsWith(path, prefix) &&
         (path.length() == prefix.length() || path[prefix.length()] == '/');
}

Result<std::vector<FstabEntry>> GetProcMountsEntriesForPath(const std::string& path) {
  Fstab fstab;
  if (!ReadFstabFromProcMounts(&fstab)) {
    return Errorf("Failed to read fstab from /proc/mounts");
  }
  std::vector<FstabEntry> entries;
  for (FstabEntry& entry : fstab) {
    // Ignore swap areas as a swap area doesn't have a meaningful `mount_point` (a.k.a., `fs_file`)
    // field, according to fstab(5). In addition, ignore any other entries whose mount points are
    // not absolute paths, just in case there are other fs types that also have an meaningless mount
    // point.
    if (entry.fs_type == "swap" || !StartsWith(entry.mount_point, '/')) {
      continue;
    }
    if (PathStartsWith(path, entry.mount_point)) {
      entries.push_back(std::move(entry));
    }
  }
  return entries;
}

void TestOnlySetListRootDir(std::string_view root_dir) { gListRootDir = root_dir; }

}  // namespace artd
}  // namespace art
