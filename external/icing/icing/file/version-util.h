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

#ifndef ICING_FILE_VERSION_UTIL_H_
#define ICING_FILE_VERSION_UTIL_H_

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/filesystem.h"
#include "icing/proto/initialize.pb.h"

namespace icing {
namespace lib {

namespace version_util {

// - Version 0: Android T base. Can be identified only by flash index magic.
// - Version 1: Android U base and M-2023-08.
// - Version 2: M-2023-09, M-2023-11, M-2024-01. Schema is compatible with v1.
//   (There were no M-2023-10, M-2023-12).
// - Version 3: M-2024-02. Schema is compatible with v1 and v2.
// - Version 4: Android V base. Schema is compatible with v1, v2 and v3.
//
// TODO(b/314816301): Bump kVersion to 4 for Android V rollout with v2 version
// detection
// LINT.IfChange(kVersion)
inline static constexpr int32_t kVersion = 4;
// LINT.ThenChange(//depot/google3/icing/schema/schema-store.cc:min_overlay_version_compatibility)
inline static constexpr int32_t kVersionOne = 1;
inline static constexpr int32_t kVersionTwo = 2;
inline static constexpr int32_t kVersionThree = 3;
inline static constexpr int32_t kVersionFour = 4;

// Version at which v2 version file is introduced.
inline static constexpr int32_t kFirstV2Version = kVersionFour;

inline static constexpr int kVersionZeroFlashIndexMagic = 0x6dfba6ae;

inline static constexpr std::string_view kVersionFilenameV1 = "version";
inline static constexpr std::string_view kVersionFilenameV2 = "version2";

struct VersionInfo {
  int32_t version;
  int32_t max_version;

  explicit VersionInfo(int32_t version_in, int32_t max_version_in)
      : version(version_in), max_version(max_version_in) {}

  bool IsValid() const { return version >= 0 && max_version >= 0; }

  bool operator==(const VersionInfo& other) const {
    return version == other.version && max_version == other.max_version;
  }
} __attribute__((packed));
static_assert(sizeof(VersionInfo) == 8, "");

enum class StateChange {
  kUndetermined,
  kCompatible,
  kRollForward,
  kRollBack,
  kUpgrade,
  kVersionZeroUpgrade,
  kVersionZeroRollForward,
};

// Contains information about which derived files need to be rebuild.
//
// These flags only reflect whether each component should be rebuilt, but do not
// handle any dependencies. The caller should handle the dependencies by
// themselves.
// e.g. - qualified id join index depends on document store derived files, but
//        it's possible to have needs_document_store_derived_files_rebuild =
//        true and needs_qualified_id_join_index_rebuild = false.
//      - The caller should know that join index should also be rebuilt in this
//        case even though needs_qualified_id_join_index_rebuild = false.
struct DerivedFilesRebuildResult {
  bool needs_document_store_derived_files_rebuild = false;
  bool needs_schema_store_derived_files_rebuild = false;
  bool needs_term_index_rebuild = false;
  bool needs_integer_index_rebuild = false;
  bool needs_qualified_id_join_index_rebuild = false;

  DerivedFilesRebuildResult() = default;

  explicit DerivedFilesRebuildResult(
      bool needs_document_store_derived_files_rebuild_in,
      bool needs_schema_store_derived_files_rebuild_in,
      bool needs_term_index_rebuild_in, bool needs_integer_index_rebuild_in,
      bool needs_qualified_id_join_index_rebuild_in)
      : needs_document_store_derived_files_rebuild(
            needs_document_store_derived_files_rebuild_in),
        needs_schema_store_derived_files_rebuild(
            needs_schema_store_derived_files_rebuild_in),
        needs_term_index_rebuild(needs_term_index_rebuild_in),
        needs_integer_index_rebuild(needs_integer_index_rebuild_in),
        needs_qualified_id_join_index_rebuild(
            needs_qualified_id_join_index_rebuild_in) {}

  bool IsRebuildNeeded() const {
    return needs_document_store_derived_files_rebuild ||
           needs_schema_store_derived_files_rebuild ||
           needs_term_index_rebuild || needs_integer_index_rebuild ||
           needs_qualified_id_join_index_rebuild;
  }

  bool operator==(const DerivedFilesRebuildResult& other) const {
    return needs_document_store_derived_files_rebuild ==
               other.needs_document_store_derived_files_rebuild &&
           needs_schema_store_derived_files_rebuild ==
               other.needs_schema_store_derived_files_rebuild &&
           needs_term_index_rebuild == other.needs_term_index_rebuild &&
           needs_integer_index_rebuild == other.needs_integer_index_rebuild &&
           needs_qualified_id_join_index_rebuild ==
               other.needs_qualified_id_join_index_rebuild;
  }

  void CombineWithOtherRebuildResultOr(const DerivedFilesRebuildResult& other) {
    needs_document_store_derived_files_rebuild =
        needs_document_store_derived_files_rebuild ||
        other.needs_document_store_derived_files_rebuild;
    needs_schema_store_derived_files_rebuild =
        needs_schema_store_derived_files_rebuild ||
        other.needs_schema_store_derived_files_rebuild;
    needs_term_index_rebuild =
        needs_term_index_rebuild || other.needs_term_index_rebuild;
    needs_integer_index_rebuild =
        needs_integer_index_rebuild || other.needs_integer_index_rebuild;
    needs_qualified_id_join_index_rebuild =
        needs_qualified_id_join_index_rebuild ||
        other.needs_qualified_id_join_index_rebuild;
  }
};

// There are two icing version files:
// 1. V1 version file contains version and max_version info of the existing
//    data.
// 2. V2 version file writes the version information using
//    FileBackedProto<IcingSearchEngineVersionProto>. This contains information
//    about the version's enabled trunk stable features in addition to the
//    version numbers written for V1.
//
// Both version files must be written to maintain backwards compatibility.
inline std::string MakeVersionFilePath(std::string_view version_file_dir,
                                       std::string_view version_file_name) {
  return absl_ports::StrCat(version_file_dir, "/", version_file_name);
}

// Returns a VersionInfo from a given IcingSearchEngineVersionProto.
inline VersionInfo GetVersionInfoFromProto(
    const IcingSearchEngineVersionProto& version_proto) {
  return VersionInfo(version_proto.version(), version_proto.max_version());
}


// Reads the IcingSearchEngineVersionProto from the version files of the
// existing data.
//
// This method reads both the v1 and v2 version files, and returns the v1
// version numbers in the absence of the v2 version file. If there is a mismatch
// between the v1 and v2 version numbers, or if the state is invalid (e.g. flash
// index header file is missing), then an invalid VersionInfo is returned.
//
// RETURNS:
//   - Existing data's IcingSearchEngineVersionProto on success
//   - INTERNAL_ERROR on I/O errors
libtextclassifier3::StatusOr<IcingSearchEngineVersionProto> ReadVersion(
    const Filesystem& filesystem, const std::string& version_file_dir,
    const std::string& index_base_dir);

// Writes the v1 version file. V1 version file is written for all versions and
// contains only Icing's VersionInfo (version number and max_version)
//
// RETURNS:
//   - OK on success
//   - INTERNAL_ERROR on I/O errors
libtextclassifier3::Status WriteV1Version(const Filesystem& filesystem,
                                          const std::string& version_file_dir,
                                          const VersionInfo& version_info);

// Writes the v2 version file. V2 version file writes the version information
// using FileBackedProto<IcingSearchEngineVersionProto>.
//
// REQUIRES: version_proto.version >= kFirstV2Version. We implement v2 version
// checking in kFirstV2Version, so callers will always use a version # greater
// than this.
//
// RETURNS:
//   - OK on success
//   - INTERNAL_ERROR on I/O errors
libtextclassifier3::Status WriteV2Version(
    const Filesystem& filesystem, const std::string& version_file_dir,
    std::unique_ptr<IcingSearchEngineVersionProto> version_proto);

// Deletes Icing's version files from version_file_dir.
//
// Returns:
//   - OK on success
//   - INTERNAL_ERROR on I/O error
libtextclassifier3::Status DiscardVersionFiles(
    const Filesystem& filesystem, std::string_view version_file_dir);

// Determines the change state between the existing data version and the current
// code version.
//
// REQUIRES: curr_version > 0. We implement version checking in version 1, so
//   the callers (except unit tests) will always use a version # greater than 0.
//
// RETURNS: StateChange
StateChange GetVersionStateChange(const VersionInfo& existing_version_info,
                                  int32_t curr_version = kVersion);

// Determines the derived files that need to be rebuilt between Icing's existing
// data based on previous data version and enabled features, and the current
// code version and enabled features.
//
// REQUIRES: curr_version >= kFirstV2Version. We implement v2 version checking
// in kFirstV2Version, so callers will always use a version # greater than this.
//
// RETURNS: DerivedFilesRebuildResult
DerivedFilesRebuildResult CalculateRequiredDerivedFilesRebuild(
    const IcingSearchEngineVersionProto& prev_version_proto,
    const IcingSearchEngineVersionProto& curr_version_proto);

// Determines whether Icing should rebuild all derived files.
// Sometimes it is not required to rebuild derived files when
// roll-forward/upgrading. This function "encodes" upgrade paths and checks if
// the roll-forward/upgrading requires derived files to be rebuilt or not.
//
// REQUIRES: curr_version > 0. We implement version checking in version 1, so
//   the callers (except unit tests) will always use a version # greater than 0.
bool ShouldRebuildDerivedFiles(const VersionInfo& existing_version_info,
                               int32_t curr_version = kVersion);

// Returns the derived files rebuilds required for a given feature.
DerivedFilesRebuildResult GetFeatureDerivedFilesRebuildResult(
    IcingSearchEngineFeatureInfoProto::FlaggedFeatureType feature);

// Constructs the IcingSearchEngineFeatureInfoProto for a given feature.
IcingSearchEngineFeatureInfoProto GetFeatureInfoProto(
    IcingSearchEngineFeatureInfoProto::FlaggedFeatureType feature);

// Populates the enabled_features field for an IcingSearchEngineFeatureInfoProto
// based on icing's initialization flag options.
//
// All enabled features are converted into an IcingSearchEngineFeatureInfoProto
// and returned in IcingSearchEngineVersionProto::enabled_features. A conversion
// should be added for each trunk stable feature flag defined in
// IcingSearchEngineOptions.
void AddEnabledFeatures(const IcingSearchEngineOptions& options,
                        IcingSearchEngineVersionProto* version_proto);

}  // namespace version_util

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_VERSION_UTIL_H_
