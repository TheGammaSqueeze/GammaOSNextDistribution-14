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
#include <optional>
#include <string>
#include <unordered_set>
#include <utility>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/file/posting_list/flash-index-storage-header.h"
#include "icing/portable/equals-proto.h"
#include "icing/proto/initialize.pb.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {
namespace version_util {

namespace {

using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::IsFalse;
using ::testing::IsTrue;

IcingSearchEngineVersionProto MakeTestVersionProto(
    const VersionInfo& version_info,
    const std::unordered_set<
        IcingSearchEngineFeatureInfoProto::FlaggedFeatureType>& features_set) {
  IcingSearchEngineVersionProto version_proto;
  version_proto.set_version(version_info.version);
  version_proto.set_max_version(version_info.max_version);

  auto* enabled_features = version_proto.mutable_enabled_features();
  for (const auto& feature : features_set) {
    enabled_features->Add(GetFeatureInfoProto(feature));
  }
  return version_proto;
}

struct VersionUtilReadVersionTestParam {
  std::optional<VersionInfo> existing_v1_version_info;
  std::optional<VersionInfo> existing_v2_version_info;
  std::optional<int> existing_flash_index_magic;
  VersionInfo expected_version_info;

  explicit VersionUtilReadVersionTestParam(
      std::optional<VersionInfo> existing_v1_version_info_in,
      std::optional<VersionInfo> existing_v2_version_info_in,
      std::optional<int> existing_flash_index_magic_in,
      VersionInfo expected_version_info_in)
      : existing_v1_version_info(std::move(existing_v1_version_info_in)),
        existing_v2_version_info(std::move(existing_v2_version_info_in)),
        existing_flash_index_magic(std::move(existing_flash_index_magic_in)),
        expected_version_info(std::move(expected_version_info_in)) {}
};

class VersionUtilReadVersionTest
    : public ::testing::TestWithParam<VersionUtilReadVersionTestParam> {
 protected:
  void SetUp() override {
    base_dir_ = GetTestTempDir() + "/version_util_test";
    index_path_ = base_dir_ + "/index";

    ASSERT_TRUE(filesystem_.CreateDirectoryRecursively(base_dir_.c_str()));
  }

  void TearDown() override {
    ASSERT_TRUE(filesystem_.DeleteDirectoryRecursively(base_dir_.c_str()));
  }

  const Filesystem& filesystem() const { return filesystem_; }

  Filesystem filesystem_;
  std::string base_dir_;
  std::string index_path_;
};

TEST_P(VersionUtilReadVersionTest, ReadVersion) {
  const VersionUtilReadVersionTestParam& param = GetParam();
  IcingSearchEngineVersionProto dummy_version_proto;

  if (param.existing_v1_version_info.has_value()) {
    ICING_ASSERT_OK(WriteV1Version(filesystem_, base_dir_,
                                   param.existing_v1_version_info.value()));
  }
  if (param.existing_v2_version_info.has_value()) {
    dummy_version_proto = MakeTestVersionProto(
        param.existing_v2_version_info.value(),
        {IcingSearchEngineFeatureInfoProto::FEATURE_HAS_PROPERTY_OPERATOR,
         IcingSearchEngineFeatureInfoProto::UNKNOWN});
    ICING_ASSERT_OK(WriteV2Version(
        filesystem_, base_dir_,
        std::make_unique<IcingSearchEngineVersionProto>(dummy_version_proto)));
  }

  // Prepare flash index file.
  if (param.existing_flash_index_magic.has_value()) {
    HeaderBlock header_block(&filesystem_, /*block_size=*/4096);
    header_block.header()->magic = param.existing_flash_index_magic.value();

    std::string main_index_dir = index_path_ + "/idx/main";
    ASSERT_TRUE(filesystem_.CreateDirectoryRecursively(main_index_dir.c_str()));
    std::string flash_index_file_path = main_index_dir + "/main_index";

    ScopedFd sfd(filesystem_.OpenForWrite(flash_index_file_path.c_str()));
    ASSERT_TRUE(sfd.is_valid());
    ASSERT_TRUE(header_block.Write(sfd.get()));
  }

  ICING_ASSERT_OK_AND_ASSIGN(IcingSearchEngineVersionProto version_proto,
                             ReadVersion(filesystem_, base_dir_, index_path_));
  if (param.existing_v2_version_info.has_value() &&
      param.expected_version_info.version ==
          param.existing_v2_version_info.value().version) {
    EXPECT_THAT(version_proto,
                portable_equals_proto::EqualsProto(dummy_version_proto));
  } else {
    // We're returning the version from v1 version file, or an invalid version.
    // version_proto.enabled_features should be empty in this case.
    EXPECT_THAT(version_proto.version(),
                Eq(param.expected_version_info.version));
    EXPECT_THAT(version_proto.max_version(),
                Eq(param.expected_version_info.max_version));
    EXPECT_THAT(version_proto.enabled_features(), IsEmpty());
  }
}

INSTANTIATE_TEST_SUITE_P(
    VersionUtilReadVersionTest, VersionUtilReadVersionTest,
    testing::Values(
        // - Version file doesn't exist
        // - Flash index doesn't exist
        // - Result: version -1, max_version -1 (invalid)
        VersionUtilReadVersionTestParam(
            /*existing_v1_version_info_in=*/std::nullopt,
            /*existing_v2_version_info_in=*/std::nullopt,
            /*existing_flash_index_magic_in=*/std::nullopt,
            /*expected_version_info_in=*/
            VersionInfo(/*version_in=*/-1, /*max_version=*/-1)),

        // - Version file doesn't exist
        // - Flash index exists with version 0 magic
        // - Result: version 0, max_version 0
        VersionUtilReadVersionTestParam(
            /*existing_v1_version_info_in=*/std::nullopt,
            /*existing_v2_version_info_in=*/std::nullopt,
            /*existing_flash_index_magic_in=*/
            std::make_optional<int>(kVersionZeroFlashIndexMagic),
            /*expected_version_info_in=*/
            VersionInfo(/*version_in=*/0, /*max_version=*/0)),

        // - Version file doesn't exist
        // - Flash index exists with non version 0 magic
        // - Result: version -1, max_version -1 (invalid)
        VersionUtilReadVersionTestParam(
            /*existing_v1_version_info_in=*/std::nullopt,
            /*existing_v2_version_info_in=*/std::nullopt,
            /*existing_flash_index_magic_in=*/
            std::make_optional<int>(kVersionZeroFlashIndexMagic + 1),
            /*expected_version_info_in=*/
            VersionInfo(/*version_in=*/-1, /*max_version=*/-1)),

        // - Version file v1 exists
        // - Flash index doesn't exist
        // - Result: version -1, max_version 1 (invalid)
        VersionUtilReadVersionTestParam(
            /*existing_v1_version_info_in=*/std::make_optional<VersionInfo>(
                /*version_in=*/1, /*max_version=*/1),
            /*existing_v2_version_info_in=*/std::nullopt,
            /*existing_flash_index_magic_in=*/std::nullopt,
            /*expected_version_info_in=*/
            VersionInfo(/*version_in=*/-1, /*max_version=*/1)),

        // - Version file v1 exists: version 1, max_version 1
        // - Flash index exists with version 0 magic
        // - Result: version 0, max_version 1
        VersionUtilReadVersionTestParam(
            /*existing_v1_version_info_in=*/std::make_optional<VersionInfo>(
                /*version_in=*/1, /*max_version=*/1),
            /*existing_v2_version_info_in=*/std::nullopt,
            /*existing_flash_index_magic_in=*/
            std::make_optional<int>(kVersionZeroFlashIndexMagic),
            /*expected_version_info_in=*/
            VersionInfo(/*version_in=*/0, /*max_version=*/1)),

        // - Version file v1 exists: version 2, max_version 3
        // - Flash index exists with version 0 magic
        // - Result: version 0, max_version 3
        VersionUtilReadVersionTestParam(
            /*existing_v1_version_info_in=*/std::make_optional<VersionInfo>(
                /*version_in=*/2, /*max_version=*/3),
            /*existing_v2_version_info_in=*/std::nullopt,
            /*existing_flash_index_magic_in=*/
            std::make_optional<int>(kVersionZeroFlashIndexMagic),
            /*expected_version_info_in=*/
            VersionInfo(/*version_in=*/0, /*max_version=*/3)),

        // - Version file v1 exists: version 1, max_version 1
        // - Flash index exists with non version 0 magic
        // - Result: version 1, max_version 1
        VersionUtilReadVersionTestParam(
            /*existing_v1_version_info_in=*/std::make_optional<VersionInfo>(
                /*version_in=*/1, /*max_version=*/1),
            /*existing_v2_version_info_in=*/std::nullopt,
            /*existing_flash_index_magic_in=*/
            std::make_optional<int>(kVersionZeroFlashIndexMagic + 1),
            /*expected_version_info_in=*/
            VersionInfo(/*version_in=*/1, /*max_version=*/1)),

        // - Version file v1 exists: version 2, max_version 3
        // - Flash index exists with non version 0 magic
        // - Result: version 2, max_version 3
        VersionUtilReadVersionTestParam(
            /*existing_v1_version_info_in=*/std::make_optional<VersionInfo>(
                /*version_in=*/2, /*max_version=*/3),
            /*existing_v2_version_info_in=*/std::nullopt,
            /*existing_flash_index_magic_in=*/
            std::make_optional<int>(kVersionZeroFlashIndexMagic + 1),
            /*expected_version_info_in=*/
            VersionInfo(/*version_in=*/2, /*max_version=*/3)),

        // - Version file v1 exists: version 2, max_version 4
        // - Version file v2 exists: version 4, max_version 4
        // - Flash index exists with non version 0 magic
        // - Result: version -1, max_version 4
        VersionUtilReadVersionTestParam(
            /*existing_v1_version_info_in=*/std::make_optional<VersionInfo>(
                /*version_in=*/2, /*max_version=*/4),
            /*existing_v2_version_info_in=*/
            std::make_optional<VersionInfo>(
                /*version_in=*/4, /*max_version=*/4),
            /*existing_flash_index_magic_in=*/
            std::make_optional<int>(kVersionZeroFlashIndexMagic + 1),
            /*expected_version_info_in=*/
            VersionInfo(/*version_in=*/-1, /*max_version=*/4)),

        // - Version file v1 exists: version 4, max_version 4
        // - Version file v2 exists: version 4, max_version 4
        // - Flash index exists with version 0 magic
        // - Result: version -1, max_version 4
        VersionUtilReadVersionTestParam(
            /*existing_v1_version_info_in=*/std::make_optional<VersionInfo>(
                /*version_in=*/4, /*max_version=*/4),
            /*existing_v2_version_info_in=*/
            std::make_optional<VersionInfo>(
                /*version_in=*/4, /*max_version=*/4),
            /*existing_flash_index_magic_in=*/
            std::make_optional<int>(kVersionZeroFlashIndexMagic),
            /*expected_version_info_in=*/
            VersionInfo(/*version_in=*/-1, /*max_version=*/4)),

        // - Version file v1 exists: version 4, max_version 4
        // - Version file v2 exists: version 4, max_version 4
        // - Flash index exists with non version 0 magic
        // - Result: version 4, max_version 4
        VersionUtilReadVersionTestParam(
            /*existing_v1_version_info_in=*/std::make_optional<VersionInfo>(
                /*version_in=*/4, /*max_version=*/4),
            /*existing_v2_version_info_in=*/
            std::make_optional<VersionInfo>(
                /*version_in=*/4, /*max_version=*/4),
            /*existing_flash_index_magic_in=*/
            std::make_optional<int>(kVersionZeroFlashIndexMagic + 1),
            /*expected_version_info_in=*/
            VersionInfo(/*version_in=*/4, /*max_version=*/4)),

        // - Version file v1 exists: version 4, max_version 4
        // - Version file v2 does not exist
        // - Flash index exists with non version 0 magic
        // - Result: version -1, max_version 4
        VersionUtilReadVersionTestParam(
            /*existing_v1_version_info_in=*/std::make_optional<VersionInfo>(
                /*version_in=*/4, /*max_version=*/4),
            /*existing_v2_version_info_in=*/std::nullopt,
            /*existing_flash_index_magic_in=*/
            std::make_optional<int>(kVersionZeroFlashIndexMagic + 1),
            /*expected_version_info_in=*/
            VersionInfo(/*version_in=*/-1, /*max_version=*/4)),

        // - Version file v1 does not exist
        // - Version file v2 exists: version 4, max_version 4
        // - Flash index exists with non version 0 magic
        // - Result: version -1, max_version -1
        VersionUtilReadVersionTestParam(
            /*existing_v1_version_info_in=*/std::nullopt,
            /*existing_v2_version_info_in=*/
            std::make_optional<VersionInfo>(
                /*version_in=*/4, /*max_version=*/4),
            /*existing_flash_index_magic_in=*/
            std::make_optional<int>(kVersionZeroFlashIndexMagic + 1),
            /*expected_version_info_in=*/
            VersionInfo(/*version_in=*/-1, /*max_version=*/-1)),

        // - Version file v1 doesn't exist
        // - Version file v2 exists: version 4, max_version 4
        // - Flash index doesn't exist
        // - Result: version -1, max_version -1 (invalid since flash index
        //   doesn't exist)
        VersionUtilReadVersionTestParam(
            /*existing_v1_version_info_in=*/std::nullopt,
            /*existing_v2_version_info_in=*/
            std::make_optional<VersionInfo>(
                /*version_in=*/4, /*max_version=*/4),
            /*existing_flash_index_magic_in=*/std::nullopt,
            /*expected_version_info_in=*/
            VersionInfo(/*version_in=*/-1, /*max_version=*/-1))));

struct VersionUtilStateChangeTestParam {
  VersionInfo existing_version_info;
  int32_t curr_version;
  StateChange expected_state_change;

  explicit VersionUtilStateChangeTestParam(VersionInfo existing_version_info_in,
                                           int32_t curr_version_in,
                                           StateChange expected_state_change_in)
      : existing_version_info(std::move(existing_version_info_in)),
        curr_version(curr_version_in),
        expected_state_change(expected_state_change_in) {}
};

class VersionUtilStateChangeTest
    : public ::testing::TestWithParam<VersionUtilStateChangeTestParam> {};

TEST_P(VersionUtilStateChangeTest, GetVersionStateChange) {
  const VersionUtilStateChangeTestParam& param = GetParam();

  EXPECT_THAT(
      GetVersionStateChange(param.existing_version_info, param.curr_version),
      Eq(param.expected_state_change));
}

INSTANTIATE_TEST_SUITE_P(
    VersionUtilStateChangeTest, VersionUtilStateChangeTest,
    testing::Values(
        // - version -1, max_version -1 (invalid)
        // - Current version = 1
        // - Result: undetermined
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(-1, -1),
            /*curr_version_in=*/1,
            /*expected_state_change_in=*/StateChange::kUndetermined),

        // - version -1, max_version 1 (invalid)
        // - Current version = 1
        // - Result: undetermined
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(-1, 1),
            /*curr_version_in=*/1,
            /*expected_state_change_in=*/StateChange::kUndetermined),

        // - version -1, max_version -1 (invalid)
        // - Current version = 2
        // - Result: undetermined
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(-1, -1),
            /*curr_version_in=*/2,
            /*expected_state_change_in=*/StateChange::kUndetermined),

        // - version -1, max_version 1 (invalid)
        // - Current version = 2
        // - Result: undetermined
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(-1, 1),
            /*curr_version_in=*/2,
            /*expected_state_change_in=*/StateChange::kUndetermined),

        // - version 0, max_version 0
        // - Current version = 1
        // - Result: version 0 upgrade
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(0, 0),
            /*curr_version_in=*/1,
            /*expected_state_change_in=*/StateChange::kVersionZeroUpgrade),

        // - version 0, max_version 1
        // - Current version = 1
        // - Result: version 0 roll forward
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(0, 1),
            /*curr_version_in=*/1,
            /*expected_state_change_in=*/StateChange::kVersionZeroRollForward),

        // - version 0, max_version 2
        // - Current version = 1
        // - Result: version 0 roll forward
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(0, 2),
            /*curr_version_in=*/1,
            /*expected_state_change_in=*/StateChange::kVersionZeroRollForward),

        // - version 0, max_version 0
        // - Current version = 2
        // - Result: version 0 upgrade
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(0, 0),
            /*curr_version_in=*/2,
            /*expected_state_change_in=*/StateChange::kVersionZeroUpgrade),

        // - version 0, max_version 1
        // - Current version = 2
        // - Result: version 0 upgrade
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(0, 1),
            /*curr_version_in=*/2,
            /*expected_state_change_in=*/StateChange::kVersionZeroRollForward),

        // - version 0, max_version 2
        // - Current version = 2
        // - Result: version 0 roll forward
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(0, 2),
            /*curr_version_in=*/2,
            /*expected_state_change_in=*/StateChange::kVersionZeroRollForward),

        // - version 1, max_version 1
        // - Current version = 1
        // - Result: compatible
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(1, 1),
            /*curr_version_in=*/1,
            /*expected_state_change_in=*/StateChange::kCompatible),

        // - version 1, max_version 2
        // - Current version = 1
        // - Result: compatible
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(1, 2),
            /*curr_version_in=*/1,
            /*expected_state_change_in=*/StateChange::kCompatible),

        // - version 2, max_version 2
        // - Current version = 1
        // - Result: roll back
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(2, 2),
            /*curr_version_in=*/1,
            /*expected_state_change_in=*/StateChange::kRollBack),

        // - version 2, max_version 3
        // - Current version = 1
        // - Result: roll back
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(2, 3),
            /*curr_version_in=*/1,
            /*expected_state_change_in=*/StateChange::kRollBack),

        // - version 1, max_version 1
        // - Current version = 2
        // - Result: upgrade
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(1, 1),
            /*curr_version_in=*/2,
            /*expected_state_change_in=*/StateChange::kUpgrade),

        // - version 1, max_version 2
        // - Current version = 2
        // - Result: roll forward
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(1, 2),
            /*curr_version_in=*/2,
            /*expected_state_change_in=*/StateChange::kRollForward),

        // - version 1, max_version 2
        // - Current version = 3
        // - Result: roll forward
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(1, 2),
            /*curr_version_in=*/3,
            /*expected_state_change_in=*/StateChange::kRollForward),

        // - version 1, max_version 3
        // - Current version = 2
        // - Result: roll forward
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(1, 3),
            /*curr_version_in=*/2,
            /*expected_state_change_in=*/StateChange::kRollForward),

        // - version 2, max_version 2
        // - Current version = 2
        // - Result: compatible
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(2, 2),
            /*curr_version_in=*/2,
            /*expected_state_change_in=*/StateChange::kCompatible),

        // - version 2, max_version 3
        // - Current version = 2
        // - Result: compatible
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(2, 3),
            /*curr_version_in=*/2,
            /*expected_state_change_in=*/StateChange::kCompatible),

        // - version 3, max_version 3
        // - Current version = 2
        // - Result: rollback
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(3, 3),
            /*curr_version_in=*/2,
            /*expected_state_change_in=*/StateChange::kRollBack),

        // - version 3, max_version 4
        // - Current version = 2
        // - Result: rollback
        VersionUtilStateChangeTestParam(
            /*existing_version_info_in=*/VersionInfo(3, 4),
            /*curr_version_in=*/2,
            /*expected_state_change_in=*/StateChange::kRollBack)));

struct VersionUtilDerivedFilesRebuildTestParam {
  int32_t existing_version;
  int32_t max_version;
  std::unordered_set<IcingSearchEngineFeatureInfoProto::FlaggedFeatureType>
      existing_enabled_features;
  int32_t curr_version;
  std::unordered_set<IcingSearchEngineFeatureInfoProto::FlaggedFeatureType>
      curr_enabled_features;
  DerivedFilesRebuildResult expected_derived_files_rebuild_result;

  explicit VersionUtilDerivedFilesRebuildTestParam(
      int32_t existing_version_in, int32_t max_version_in,
      std::unordered_set<IcingSearchEngineFeatureInfoProto::FlaggedFeatureType>
          existing_enabled_features_in,
      int32_t curr_version_in,
      std::unordered_set<IcingSearchEngineFeatureInfoProto::FlaggedFeatureType>
          curr_enabled_features_in,
      DerivedFilesRebuildResult expected_derived_files_rebuild_result_in)
      : existing_version(existing_version_in),
        max_version(max_version_in),
        existing_enabled_features(std::move(existing_enabled_features_in)),
        curr_version(curr_version_in),
        curr_enabled_features(std::move(curr_enabled_features_in)),
        expected_derived_files_rebuild_result(
            std::move(expected_derived_files_rebuild_result_in)) {}
};

class VersionUtilDerivedFilesRebuildTest
    : public ::testing::TestWithParam<VersionUtilDerivedFilesRebuildTestParam> {
};

TEST_P(VersionUtilDerivedFilesRebuildTest,
       CalculateRequiredDerivedFilesRebuild) {
  const VersionUtilDerivedFilesRebuildTestParam& param = GetParam();

  EXPECT_THAT(CalculateRequiredDerivedFilesRebuild(
                  /*prev_version_proto=*/MakeTestVersionProto(
                      VersionInfo(param.existing_version, param.max_version),
                      param.existing_enabled_features),
                  /*curr_version_proto=*/
                  MakeTestVersionProto(
                      VersionInfo(param.curr_version, param.max_version),
                      param.curr_enabled_features)),
              Eq(param.expected_derived_files_rebuild_result));
}

INSTANTIATE_TEST_SUITE_P(
    VersionUtilDerivedFilesRebuildTest, VersionUtilDerivedFilesRebuildTest,
    testing::Values(
        // - Existing version -1, max_version -1 (invalid)
        // - Existing enabled features = {}
        // - Current version = 4
        // - Current enabled features = {}
        //
        // - Result: rebuild everything
        VersionUtilDerivedFilesRebuildTestParam(
            /*existing_version_in=*/-1, /*max_version_in=*/-1,
            /*existing_enabled_features_in=*/{}, /*curr_version_in=*/4,
            /*curr_enabled_features_in=*/{},
            /*expected_derived_files_rebuild_result_in=*/
            DerivedFilesRebuildResult(
                /*needs_document_store_derived_files_rebuild=*/true,
                /*needs_schema_store_derived_files_rebuild=*/true,
                /*needs_term_index_rebuild=*/true,
                /*needs_integer_index_rebuild=*/true,
                /*needs_qualified_id_join_index_rebuild=*/true)),

        // - Existing version -1, max_version 2 (invalid)
        // - Existing enabled features = {}
        // - Current version = 4
        // - Current enabled features = {}
        //
        // - Result: rebuild everything
        VersionUtilDerivedFilesRebuildTestParam(
            /*existing_version_in=*/-1, /*max_version_in=*/-1,
            /*existing_enabled_features_in=*/{}, /*curr_version_in=*/4,
            /*curr_enabled_features_in=*/{},
            /*expected_derived_files_rebuild_result_in=*/
            DerivedFilesRebuildResult(
                /*needs_document_store_derived_files_rebuild=*/true,
                /*needs_schema_store_derived_files_rebuild=*/true,
                /*needs_term_index_rebuild=*/true,
                /*needs_integer_index_rebuild=*/true,
                /*needs_qualified_id_join_index_rebuild=*/true)),

        // - Existing version 3, max_version 3 (pre v2 version check)
        // - Existing enabled features = {}
        // - Current version = 4
        // - Current enabled features = {}
        //
        // - Result: don't rebuild anything
        VersionUtilDerivedFilesRebuildTestParam(
            /*existing_version_in=*/3, /*max_version_in=*/3,
            /*existing_enabled_features_in=*/{}, /*curr_version_in=*/4,
            /*curr_enabled_features_in=*/{},
            /*expected_derived_files_rebuild_result_in=*/
            DerivedFilesRebuildResult(
                /*needs_document_store_derived_files_rebuild=*/false,
                /*needs_schema_store_derived_files_rebuild=*/false,
                /*needs_term_index_rebuild=*/false,
                /*needs_integer_index_rebuild=*/false,
                /*needs_qualified_id_join_index_rebuild=*/false)),

        // - Existing version 3, max_version 3 (pre v2 version check)
        // - Existing enabled features = {}
        // - Current version = 4
        // - Current enabled features = {FEATURE_HAS_PROPERTY_OPERATOR}
        //
        // - Result: rebuild term index
        VersionUtilDerivedFilesRebuildTestParam(
            /*existing_version_in=*/3, /*max_version_in=*/3,
            /*existing_enabled_features_in=*/{}, /*curr_version_in=*/4,
            /*curr_enabled_features_in=*/
            {IcingSearchEngineFeatureInfoProto::FEATURE_HAS_PROPERTY_OPERATOR},
            /*expected_derived_files_rebuild_result_in=*/
            DerivedFilesRebuildResult(
                /*needs_document_store_derived_files_rebuild=*/false,
                /*needs_schema_store_derived_files_rebuild=*/false,
                /*needs_term_index_rebuild=*/true,
                /*needs_integer_index_rebuild=*/false,
                /*needs_qualified_id_join_index_rebuild=*/false)),

        // - Existing version 4, max_version 4
        // - Existing enabled features = {}
        // - Current version = 4
        // - Current enabled features = {}
        //
        // - Result: don't rebuild anything
        VersionUtilDerivedFilesRebuildTestParam(
            /*existing_version_in=*/4, /*max_version_in=*/4,
            /*existing_enabled_features_in=*/{}, /*curr_version_in=*/4,
            /*curr_enabled_features_in=*/{},
            /*expected_derived_files_rebuild_result_in=*/
            DerivedFilesRebuildResult(
                /*needs_document_store_derived_files_rebuild=*/false,
                /*needs_schema_store_derived_files_rebuild=*/false,
                /*needs_term_index_rebuild=*/false,
                /*needs_integer_index_rebuild=*/false,
                /*needs_qualified_id_join_index_rebuild=*/false)),

        // - Existing version 4, max_version 5
        // - Existing enabled features = {}
        // - Current version = 5
        // - Current enabled features = {}
        //
        // - Result: Rollforward -- rebuild everything
        VersionUtilDerivedFilesRebuildTestParam(
            /*existing_version_in=*/4, /*max_version_in=*/5,
            /*existing_enabled_features_in=*/{}, /*curr_version_in=*/5,
            /*curr_enabled_features_in=*/{},
            /*expected_derived_files_rebuild_result_in=*/
            DerivedFilesRebuildResult(
                /*needs_document_store_derived_files_rebuild=*/true,
                /*needs_schema_store_derived_files_rebuild=*/true,
                /*needs_term_index_rebuild=*/true,
                /*needs_integer_index_rebuild=*/true,
                /*needs_qualified_id_join_index_rebuild=*/true)),

        // - Existing version 5, max_version 5
        // - Existing enabled features = {}
        // - Current version = 4
        // - Current enabled features = {}
        //
        // - Result: Rollback -- rebuild everything
        VersionUtilDerivedFilesRebuildTestParam(
            /*existing_version_in=*/5, /*max_version_in=*/5,
            /*existing_enabled_features_in=*/{}, /*curr_version_in=*/4,
            /*curr_enabled_features_in=*/{},
            /*expected_derived_files_rebuild_result_in=*/
            DerivedFilesRebuildResult(
                /*needs_document_store_derived_files_rebuild=*/true,
                /*needs_schema_store_derived_files_rebuild=*/true,
                /*needs_term_index_rebuild=*/true,
                /*needs_integer_index_rebuild=*/true,
                /*needs_qualified_id_join_index_rebuild=*/true)),

        // - Existing version 4, max_version 4
        // - Existing enabled features = {}
        // - Current version = 4
        // - Current enabled features = {FEATURE_HAS_PROPERTY_OPERATOR}
        //
        // - Result: rebuild term index
        VersionUtilDerivedFilesRebuildTestParam(
            /*existing_version_in=*/4, /*max_version_in=*/4,
            /*existing_enabled_features_in=*/{}, /*curr_version_in=*/4,
            /*curr_enabled_features_in=*/
            {IcingSearchEngineFeatureInfoProto::FEATURE_HAS_PROPERTY_OPERATOR},
            /*expected_derived_files_rebuild_result_in=*/
            DerivedFilesRebuildResult(
                /*needs_document_store_derived_files_rebuild=*/false,
                /*needs_schema_store_derived_files_rebuild=*/false,
                /*needs_term_index_rebuild=*/true,
                /*needs_integer_index_rebuild=*/false,
                /*needs_qualified_id_join_index_rebuild=*/false)),

        // - Existing version 4, max_version 4
        // - Existing enabled features = {FEATURE_HAS_PROPERTY_OPERATOR}
        // - Current version = 4
        // - Current enabled features = {}
        //
        // - Result: rebuild term index
        VersionUtilDerivedFilesRebuildTestParam(
            /*existing_version_in=*/4, /*max_version_in=*/4,
            /*existing_enabled_features_in=*/
            {IcingSearchEngineFeatureInfoProto::FEATURE_HAS_PROPERTY_OPERATOR},
            /*curr_version_in=*/4, /*curr_enabled_features_in=*/{},
            /*expected_derived_files_rebuild_result_in=*/
            DerivedFilesRebuildResult(
                /*needs_document_store_derived_files_rebuild=*/false,
                /*needs_schema_store_derived_files_rebuild=*/false,
                /*needs_term_index_rebuild=*/true,
                /*needs_integer_index_rebuild=*/false,
                /*needs_qualified_id_join_index_rebuild=*/false)),

        // - Existing version 4, max_version 4
        // - Existing enabled features = {UNKNOWN}
        // - Current version = 4
        // - Current enabled features = {FEATURE_HAS_PROPERTY_OPERATOR}
        //
        // - Result: rebuild everything
        VersionUtilDerivedFilesRebuildTestParam(
            /*existing_version_in=*/4, /*max_version_in=*/4,
            /*existing_enabled_features_in=*/
            {IcingSearchEngineFeatureInfoProto::UNKNOWN}, /*curr_version_in=*/4,
            /*curr_enabled_features_in=*/
            {IcingSearchEngineFeatureInfoProto::FEATURE_HAS_PROPERTY_OPERATOR},
            /*expected_derived_files_rebuild_result_in=*/
            DerivedFilesRebuildResult(
                /*needs_document_store_derived_files_rebuild=*/true,
                /*needs_schema_store_derived_files_rebuild=*/true,
                /*needs_term_index_rebuild=*/true,
                /*needs_integer_index_rebuild=*/true,
                /*needs_qualified_id_join_index_rebuild=*/true))));

TEST(VersionUtilTest, ShouldRebuildDerivedFilesUndeterminedVersion) {
  EXPECT_THAT(
      ShouldRebuildDerivedFiles(VersionInfo(-1, -1), /*curr_version=*/1),
      IsTrue());
  EXPECT_THAT(
      ShouldRebuildDerivedFiles(VersionInfo(-1, -1), /*curr_version=*/2),
      IsTrue());
}

TEST(VersionUtilTest, ShouldRebuildDerivedFilesVersionZeroUpgrade) {
  // 0 -> 1
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(0, 0), /*curr_version=*/1),
              IsTrue());

  // 0 -> 2
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(0, 0), /*curr_version=*/2),
              IsTrue());
}

TEST(VersionUtilTest, ShouldRebuildDerivedFilesVersionZeroRollForward) {
  // (1 -> 0), 0 -> 1
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(0, 1), /*curr_version=*/1),
              IsTrue());

  // (1 -> 0), 0 -> 2
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(0, 1), /*curr_version=*/2),
              IsTrue());

  // (2 -> 0), 0 -> 1
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(0, 2), /*curr_version=*/1),
              IsTrue());
}

TEST(VersionUtilTest, ShouldRebuildDerivedFilesRollBack) {
  // 2 -> 1
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(2, 2), /*curr_version=*/1),
              IsTrue());

  // 3 -> 1
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(3, 3), /*curr_version=*/1),
              IsTrue());

  // (3 -> 2), 2 -> 1
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(2, 3), /*curr_version=*/1),
              IsTrue());
}

TEST(VersionUtilTest, ShouldRebuildDerivedFilesRollForward) {
  // (2 -> 1), 1 -> 2
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(1, 2), /*curr_version=*/2),
              IsTrue());

  // (2 -> 1), 1 -> 3
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(1, 2), /*curr_version=*/3),
              IsTrue());

  // (3 -> 1), 1 -> 2
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(1, 3), /*curr_version=*/2),
              IsTrue());
}

TEST(VersionUtilTest, ShouldRebuildDerivedFilesCompatible) {
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(2, 2), /*curr_version=*/2),
              IsFalse());

  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(2, 3), /*curr_version=*/2),
              IsFalse());
}

TEST(VersionUtilTest, Upgrade) {
  // Unlike other state changes, upgrade depends on the actual "encoded path".

  // kVersionOne -> kVersionTwo
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(kVersionOne, kVersionOne),
                                        /*curr_version=*/kVersionTwo),
              IsFalse());

  // kVersionTwo -> kVersionThree
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(kVersionTwo, kVersionTwo),
                                        /*curr_version=*/kVersionThree),
              IsFalse());

  // kVersionOne -> kVersionThree.
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(kVersionOne, kVersionOne),
                                        /*curr_version=*/kVersionThree),
              IsFalse());

  // kVersionThree -> kVersionFour.
  EXPECT_THAT(
      ShouldRebuildDerivedFiles(VersionInfo(kVersionThree, kVersionThree),
                                /*curr_version=*/kVersionFour),
      IsFalse());

  // kVersionTwo -> kVersionFour
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(kVersionTwo, kVersionTwo),
                                        /*curr_version=*/kVersionFour),
              IsFalse());

  // kVersionOne -> kVersionFour.
  EXPECT_THAT(ShouldRebuildDerivedFiles(VersionInfo(kVersionOne, kVersionOne),
                                        /*curr_version=*/kVersionFour),
              IsFalse());
}

TEST(VersionUtilTest, GetFeatureDerivedFilesRebuildResult_unknown) {
  EXPECT_THAT(GetFeatureDerivedFilesRebuildResult(
                  IcingSearchEngineFeatureInfoProto::UNKNOWN),
              Eq(DerivedFilesRebuildResult(
                  /*needs_document_store_derived_files_rebuild=*/true,
                  /*needs_schema_store_derived_files_rebuild=*/true,
                  /*needs_term_index_rebuild=*/true,
                  /*needs_integer_index_rebuild=*/true,
                  /*needs_qualified_id_join_index_rebuild=*/true)));
}

TEST(VersionUtilTest,
     GetFeatureDerivedFilesRebuildResult_featureHasPropertyOperator) {
  EXPECT_THAT(
      GetFeatureDerivedFilesRebuildResult(
          IcingSearchEngineFeatureInfoProto::FEATURE_HAS_PROPERTY_OPERATOR),
      Eq(DerivedFilesRebuildResult(
          /*needs_document_store_derived_files_rebuild=*/false,
          /*needs_schema_store_derived_files_rebuild=*/false,
          /*needs_term_index_rebuild=*/true,
          /*needs_integer_index_rebuild=*/false,
          /*needs_qualified_id_join_index_rebuild=*/false)));
}

class VersionUtilFeatureProtoTest
    : public ::testing::TestWithParam<
          IcingSearchEngineFeatureInfoProto::FlaggedFeatureType> {};

TEST_P(VersionUtilFeatureProtoTest, GetFeatureInfoProto) {
  IcingSearchEngineFeatureInfoProto::FlaggedFeatureType feature_type =
      GetParam();
  DerivedFilesRebuildResult rebuild_result =
      GetFeatureDerivedFilesRebuildResult(feature_type);

  IcingSearchEngineFeatureInfoProto feature_info =
      GetFeatureInfoProto(feature_type);
  EXPECT_THAT(feature_info.feature_type(), Eq(feature_type));

  EXPECT_THAT(feature_info.needs_document_store_rebuild(),
              Eq(rebuild_result.needs_document_store_derived_files_rebuild));
  EXPECT_THAT(feature_info.needs_schema_store_rebuild(),
              Eq(rebuild_result.needs_schema_store_derived_files_rebuild));
  EXPECT_THAT(feature_info.needs_term_index_rebuild(),
              Eq(rebuild_result.needs_term_index_rebuild));
  EXPECT_THAT(feature_info.needs_integer_index_rebuild(),
              Eq(rebuild_result.needs_integer_index_rebuild));
  EXPECT_THAT(feature_info.needs_qualified_id_join_index_rebuild(),
              Eq(rebuild_result.needs_qualified_id_join_index_rebuild));
}

INSTANTIATE_TEST_SUITE_P(
    VersionUtilFeatureProtoTest, VersionUtilFeatureProtoTest,
    testing::Values(
        IcingSearchEngineFeatureInfoProto::UNKNOWN,
        IcingSearchEngineFeatureInfoProto::FEATURE_HAS_PROPERTY_OPERATOR));

}  // namespace

}  // namespace version_util
}  // namespace lib
}  // namespace icing
