// Copyright (C) 2023 Google LLC
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

#include "icing/file/version-util.h"

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_set>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/file-backed-proto.h"
#include "icing/file/filesystem.h"
#include "icing/index/index.h"
#include "icing/proto/initialize.pb.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace version_util {

namespace {

libtextclassifier3::StatusOr<VersionInfo> ReadV1VersionInfo(
    const Filesystem& filesystem, const std::string& version_file_dir,
    const std::string& index_base_dir) {
  // 1. Read the version info.
  const std::string v1_version_filepath =
      MakeVersionFilePath(version_file_dir, kVersionFilenameV1);
  VersionInfo existing_version_info(-1, -1);
  if (filesystem.FileExists(v1_version_filepath.c_str()) &&
      !filesystem.PRead(v1_version_filepath.c_str(), &existing_version_info,
                        sizeof(VersionInfo), /*offset=*/0)) {
    return absl_ports::InternalError("Failed to read v1 version file");
  }

  // 2. Check the Index magic to see if we're actually on version 0.
  libtextclassifier3::StatusOr<int> existing_flash_index_magic =
      Index::ReadFlashIndexMagic(&filesystem, index_base_dir);
  if (!existing_flash_index_magic.ok()) {
    if (absl_ports::IsNotFound(existing_flash_index_magic.status())) {
      // Flash index magic doesn't exist. In this case, we're unable to
      // determine the version change state correctly (regardless of the
      // existence of the version file), so invalidate VersionInfo by setting
      // version to -1, but still keep the max_version value read in step 1.
      existing_version_info.version = -1;
      return existing_version_info;
    }
    // Real error.
    return std::move(existing_flash_index_magic).status();
  }
  if (existing_flash_index_magic.ValueOrDie() ==
      kVersionZeroFlashIndexMagic) {
    existing_version_info.version = 0;
    if (existing_version_info.max_version == -1) {
      existing_version_info.max_version = 0;
    }
  }

  return existing_version_info;
}

libtextclassifier3::StatusOr<IcingSearchEngineVersionProto> ReadV2VersionInfo(
    const Filesystem& filesystem, const std::string& version_file_dir) {
  // Read the v2 version file. V2 version file stores the
  // IcingSearchEngineVersionProto as a file-backed proto.
  const std::string v2_version_filepath =
      MakeVersionFilePath(version_file_dir, kVersionFilenameV2);
  FileBackedProto<IcingSearchEngineVersionProto> v2_version_file(
      filesystem, v2_version_filepath);
  ICING_ASSIGN_OR_RETURN(const IcingSearchEngineVersionProto* v2_version_proto,
                         v2_version_file.Read());

  return *v2_version_proto;
}

}  // namespace

libtextclassifier3::StatusOr<IcingSearchEngineVersionProto> ReadVersion(
    const Filesystem& filesystem, const std::string& version_file_dir,
    const std::string& index_base_dir) {
  // 1. Read the v1 version file
  ICING_ASSIGN_OR_RETURN(
      VersionInfo v1_version_info,
      ReadV1VersionInfo(filesystem, version_file_dir, index_base_dir));
  if (!v1_version_info.IsValid()) {
    // This happens if IcingLib's state is invalid (e.g. flash index header file
    // is missing). Return the invalid version numbers in this case.
    IcingSearchEngineVersionProto version_proto;
    version_proto.set_version(v1_version_info.version);
    version_proto.set_max_version(v1_version_info.max_version);
    return version_proto;
  }

  // 2. Read the v2 version file
  auto v2_version_proto = ReadV2VersionInfo(filesystem, version_file_dir);
  if (!v2_version_proto.ok()) {
    if (!absl_ports::IsNotFound(v2_version_proto.status())) {
      // Real error.
      return std::move(v2_version_proto).status();
    }
    // The v2 version file has not been written
    IcingSearchEngineVersionProto version_proto;
    if (v1_version_info.version < kFirstV2Version) {
      // There are two scenarios for this case:
      // 1. It's the first time that we're upgrading from a lower version to a
      //    version >= kFirstV2Version.
      //    - It's expected that the v2 version file has not been written yet in
      //      this case and we return the v1 version numbers instead.
      // 2. We're rolling forward from a version < kFirstV2Version, after
      //    rolling back from a previous version >= kFirstV2Version, and for
      //    some unknown reason we lost the v2 version file in the previous
      //    version.
      //    - e.g. version #4 -> version #1 -> version #4, but we lost the v2
      //      file during version #1.
      //    - This is a rollforward case, but it's still fine to return the v1
      //      version number here as ShouldRebuildDerivedFiles can handle
      //      rollforwards correctly.
      version_proto.set_version(v1_version_info.version);
      version_proto.set_max_version(v1_version_info.max_version);
    } else {
      // Something weird has happened. During last initialization we were
      // already on a version >= kFirstV2Version, so the v2 version file
      // should have been written.
      // Return an invalid version number in this case and trigger rebuilding
      // everything.
      version_proto.set_version(-1);
      version_proto.set_max_version(v1_version_info.max_version);
    }
    return version_proto;
  }

  // 3. Check if versions match. If not, it means that we're rolling forward
  // from a version < kFirstV2Version. In order to trigger rebuilding
  // everything, we return an invalid version number in this case.
  IcingSearchEngineVersionProto v2_version_proto_value =
      std::move(v2_version_proto).ValueOrDie();
  if (v1_version_info.version != v2_version_proto_value.version()) {
    v2_version_proto_value.set_version(-1);
    v2_version_proto_value.mutable_enabled_features()->Clear();
  }

  return v2_version_proto_value;
}

libtextclassifier3::Status WriteV1Version(const Filesystem& filesystem,
                                          const std::string& version_file_dir,
                                          const VersionInfo& version_info) {
  ScopedFd scoped_fd(filesystem.OpenForWrite(
      MakeVersionFilePath(version_file_dir, kVersionFilenameV1).c_str()));
  if (!scoped_fd.is_valid() ||
      !filesystem.PWrite(scoped_fd.get(), /*offset=*/0, &version_info,
                         sizeof(VersionInfo)) ||
      !filesystem.DataSync(scoped_fd.get())) {
    return absl_ports::InternalError("Failed to write v1 version file");
  }
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status WriteV2Version(
    const Filesystem& filesystem, const std::string& version_file_dir,
    std::unique_ptr<IcingSearchEngineVersionProto> version_proto) {
  FileBackedProto<IcingSearchEngineVersionProto> v2_version_file(
      filesystem, MakeVersionFilePath(version_file_dir, kVersionFilenameV2));
  libtextclassifier3::Status v2_write_status =
      v2_version_file.Write(std::move(version_proto));
  if (!v2_write_status.ok()) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "Failed to write v2 version file: ", v2_write_status.error_message()));
  }
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status DiscardVersionFiles(
    const Filesystem& filesystem, std::string_view version_file_dir) {
  if (!filesystem.DeleteFile(
          MakeVersionFilePath(version_file_dir, kVersionFilenameV1).c_str()) ||
      !filesystem.DeleteFile(
          MakeVersionFilePath(version_file_dir, kVersionFilenameV2).c_str())) {
    return absl_ports::InternalError("Failed to discard version files");
  }
  return libtextclassifier3::Status::OK;
}

StateChange GetVersionStateChange(const VersionInfo& existing_version_info,
                                  int32_t curr_version) {
  if (!existing_version_info.IsValid()) {
    return StateChange::kUndetermined;
  }

  if (existing_version_info.version == 0) {
    return (existing_version_info.max_version == existing_version_info.version)
               ? StateChange::kVersionZeroUpgrade
               : StateChange::kVersionZeroRollForward;
  }

  if (existing_version_info.version == curr_version) {
    return StateChange::kCompatible;
  } else if (existing_version_info.version > curr_version) {
    return StateChange::kRollBack;
  } else {  // existing_version_info.version < curr_version
    return (existing_version_info.max_version == existing_version_info.version)
               ? StateChange::kUpgrade
               : StateChange::kRollForward;
  }
}

DerivedFilesRebuildResult CalculateRequiredDerivedFilesRebuild(
    const IcingSearchEngineVersionProto& prev_version_proto,
    const IcingSearchEngineVersionProto& curr_version_proto) {
  // 1. Do version check using version and max_version numbers
  if (ShouldRebuildDerivedFiles(GetVersionInfoFromProto(prev_version_proto),
                                curr_version_proto.version())) {
    return DerivedFilesRebuildResult(
        /*needs_document_store_derived_files_rebuild=*/true,
        /*needs_schema_store_derived_files_rebuild=*/true,
        /*needs_term_index_rebuild=*/true,
        /*needs_integer_index_rebuild=*/true,
        /*needs_qualified_id_join_index_rebuild=*/true);
  }

  // 2. Compare the previous enabled features with the current enabled features
  // and rebuild if there are differences.
  std::unordered_set<IcingSearchEngineFeatureInfoProto::FlaggedFeatureType>
      prev_features;
  for (const auto& feature : prev_version_proto.enabled_features()) {
    prev_features.insert(feature.feature_type());
  }
  std::unordered_set<IcingSearchEngineFeatureInfoProto::FlaggedFeatureType>
      curr_features;
  for (const auto& feature : curr_version_proto.enabled_features()) {
    curr_features.insert(feature.feature_type());
  }
  DerivedFilesRebuildResult result;
  for (const auto& prev_feature : prev_features) {
    // If there is an UNKNOWN feature in the previous feature set (note that we
    // never use UNKNOWN  when writing the version proto), it means that:
    // - The previous version proto contains a feature enum that is only defined
    //   in a newer version.
    // - We've now rolled back to an old version that doesn't understand this
    //   new enum value, and proto serialization defaults it to 0 (UNKNOWN).
    // - In this case we need to rebuild everything.
    if (prev_feature == IcingSearchEngineFeatureInfoProto::UNKNOWN) {
      return DerivedFilesRebuildResult(
          /*needs_document_store_derived_files_rebuild=*/true,
          /*needs_schema_store_derived_files_rebuild=*/true,
          /*needs_term_index_rebuild=*/true,
          /*needs_integer_index_rebuild=*/true,
          /*needs_qualified_id_join_index_rebuild=*/true);
    }
    if (curr_features.find(prev_feature) == curr_features.end()) {
      DerivedFilesRebuildResult required_rebuilds =
          GetFeatureDerivedFilesRebuildResult(prev_feature);
      result.CombineWithOtherRebuildResultOr(required_rebuilds);
    }
  }
  for (const auto& curr_feature : curr_features) {
    if (prev_features.find(curr_feature) == prev_features.end()) {
      DerivedFilesRebuildResult required_rebuilds =
          GetFeatureDerivedFilesRebuildResult(curr_feature);
      result.CombineWithOtherRebuildResultOr(required_rebuilds);
    }
  }
  return result;
}

bool ShouldRebuildDerivedFiles(const VersionInfo& existing_version_info,
                               int32_t curr_version) {
  StateChange state_change =
      GetVersionStateChange(existing_version_info, curr_version);
  switch (state_change) {
    case StateChange::kCompatible:
      return false;
    case StateChange::kUndetermined:
      [[fallthrough]];
    case StateChange::kRollBack:
      [[fallthrough]];
    case StateChange::kRollForward:
      [[fallthrough]];
    case StateChange::kVersionZeroRollForward:
      [[fallthrough]];
    case StateChange::kVersionZeroUpgrade:
      return true;
    case StateChange::kUpgrade:
      break;
  }

  bool should_rebuild = false;
  int32_t existing_version = existing_version_info.version;
  while (existing_version < curr_version) {
    switch (existing_version) {
      case 1: {
        // version 1 -> version 2 upgrade, no need to rebuild
        break;
      }
      case 2: {
        // version 2 -> version 3 upgrade, no need to rebuild
        break;
      }
      case 3: {
        // version 3 -> version 4 upgrade, no need to rebuild
        break;
      }
      default:
        // This should not happen. Rebuild anyway if unsure.
        should_rebuild |= true;
    }
    ++existing_version;
  }
  return should_rebuild;
}

DerivedFilesRebuildResult GetFeatureDerivedFilesRebuildResult(
    IcingSearchEngineFeatureInfoProto::FlaggedFeatureType feature) {
  switch (feature) {
    case IcingSearchEngineFeatureInfoProto::FEATURE_HAS_PROPERTY_OPERATOR: {
      return DerivedFilesRebuildResult(
          /*needs_document_store_derived_files_rebuild=*/false,
          /*needs_schema_store_derived_files_rebuild=*/false,
          /*needs_term_index_rebuild=*/true,
          /*needs_integer_index_rebuild=*/false,
          /*needs_qualified_id_join_index_rebuild=*/false);
    }
    case IcingSearchEngineFeatureInfoProto::UNKNOWN:
      return DerivedFilesRebuildResult(
          /*needs_document_store_derived_files_rebuild=*/true,
          /*needs_schema_store_derived_files_rebuild=*/true,
          /*needs_term_index_rebuild=*/true,
          /*needs_integer_index_rebuild=*/true,
          /*needs_qualified_id_join_index_rebuild=*/true);
  }
}

IcingSearchEngineFeatureInfoProto GetFeatureInfoProto(
    IcingSearchEngineFeatureInfoProto::FlaggedFeatureType feature) {
  IcingSearchEngineFeatureInfoProto info;
  info.set_feature_type(feature);

  DerivedFilesRebuildResult result =
      GetFeatureDerivedFilesRebuildResult(feature);
  info.set_needs_document_store_rebuild(
      result.needs_document_store_derived_files_rebuild);
  info.set_needs_schema_store_rebuild(
      result.needs_schema_store_derived_files_rebuild);
  info.set_needs_term_index_rebuild(result.needs_term_index_rebuild);
  info.set_needs_integer_index_rebuild(result.needs_integer_index_rebuild);
  info.set_needs_qualified_id_join_index_rebuild(
      result.needs_qualified_id_join_index_rebuild);

  return info;
}

void AddEnabledFeatures(const IcingSearchEngineOptions& options,
                        IcingSearchEngineVersionProto* version_proto) {
  auto* enabled_features = version_proto->mutable_enabled_features();
  // HasPropertyOperator feature
  if (options.build_property_existence_metadata_hits()) {
    enabled_features->Add(GetFeatureInfoProto(
        IcingSearchEngineFeatureInfoProto::FEATURE_HAS_PROPERTY_OPERATOR));
  }
}

}  // namespace version_util

}  // namespace lib
}  // namespace icing
