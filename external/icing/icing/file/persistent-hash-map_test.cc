// Copyright (C) 2022 Google LLC
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

#include "icing/file/persistent-hash-map.h"

#include <cstring>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/file-backed-vector.h"
#include "icing/file/filesystem.h"
#include "icing/file/persistent-storage.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"
#include "icing/util/crc32.h"

using ::testing::Contains;
using ::testing::Eq;
using ::testing::Gt;
using ::testing::HasSubstr;
using ::testing::IsEmpty;
using ::testing::IsTrue;
using ::testing::Key;
using ::testing::Lt;
using ::testing::Ne;
using ::testing::Not;
using ::testing::Pair;
using ::testing::Pointee;
using ::testing::SizeIs;
using ::testing::UnorderedElementsAre;

namespace icing {
namespace lib {

namespace {

using Bucket = PersistentHashMap::Bucket;
using Crcs = PersistentStorage::Crcs;
using Entry = PersistentHashMap::Entry;
using Info = PersistentHashMap::Info;
using Options = PersistentHashMap::Options;

static constexpr int32_t kCorruptedValueOffset = 3;
static constexpr int32_t kTestInitNumBuckets = 1;

class PersistentHashMapTest : public ::testing::TestWithParam<bool> {
 protected:
  void SetUp() override {
    base_dir_ = GetTestTempDir() + "/icing";
    ASSERT_THAT(filesystem_.CreateDirectoryRecursively(base_dir_.c_str()),
                IsTrue());

    working_path_ = base_dir_ + "/persistent_hash_map_test";
  }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(base_dir_.c_str());
  }

  std::vector<char> Serialize(int val) {
    std::vector<char> ret(sizeof(val));
    memcpy(ret.data(), &val, sizeof(val));
    return ret;
  }

  libtextclassifier3::StatusOr<int> GetValueByKey(
      PersistentHashMap* persistent_hash_map, std::string_view key) {
    int val;
    ICING_RETURN_IF_ERROR(persistent_hash_map->Get(key, &val));
    return val;
  }

  std::unordered_map<std::string, int> GetAllKeyValuePairs(
      PersistentHashMap::Iterator&& iter) {
    std::unordered_map<std::string, int> kvps;

    while (iter.Advance()) {
      int val;
      memcpy(&val, iter.GetValue(), sizeof(val));
      kvps.emplace(iter.GetKey(), val);
    }
    return kvps;
  }

  Filesystem filesystem_;
  std::string base_dir_;
  std::string working_path_;
};

TEST_P(PersistentHashMapTest, OptionsInvalidValueTypeSize) {
  Options options(/*value_type_size_in=*/sizeof(int));
  ASSERT_TRUE(options.IsValid());

  options.value_type_size = -1;
  EXPECT_FALSE(options.IsValid());

  options.value_type_size = 0;
  EXPECT_FALSE(options.IsValid());

  options.value_type_size = PersistentHashMap::kMaxValueTypeSize + 1;
  EXPECT_FALSE(options.IsValid());
}

TEST_P(PersistentHashMapTest, OptionsInvalidMaxNumEntries) {
  Options options(/*value_type_size_in=*/sizeof(int));
  ASSERT_TRUE(options.IsValid());

  options.max_num_entries = -1;
  EXPECT_FALSE(options.IsValid());

  options.max_num_entries = 0;
  EXPECT_FALSE(options.IsValid());

  options.max_num_entries = Entry::kMaxNumEntries + 1;
  EXPECT_FALSE(options.IsValid());
}

TEST_P(PersistentHashMapTest, OptionsInvalidMaxLoadFactorPercent) {
  Options options(/*value_type_size_in=*/sizeof(int));
  ASSERT_TRUE(options.IsValid());

  options.max_load_factor_percent = -1;
  EXPECT_FALSE(options.IsValid());

  options.max_load_factor_percent = 0;
  EXPECT_FALSE(options.IsValid());
}

TEST_P(PersistentHashMapTest, OptionsInvalidAverageKVByteSize) {
  Options options(/*value_type_size_in=*/sizeof(int));
  ASSERT_TRUE(options.IsValid());

  options.average_kv_byte_size = -1;
  EXPECT_FALSE(options.IsValid());

  options.average_kv_byte_size = 0;
  EXPECT_FALSE(options.IsValid());
}

TEST_P(PersistentHashMapTest, OptionsInvalidInitNumBuckets) {
  Options options(/*value_type_size_in=*/sizeof(int));
  ASSERT_TRUE(options.IsValid());

  options.init_num_buckets = -1;
  EXPECT_FALSE(options.IsValid());

  options.init_num_buckets = 0;
  EXPECT_FALSE(options.IsValid());

  options.init_num_buckets = Bucket::kMaxNumBuckets + 1;
  EXPECT_FALSE(options.IsValid());

  // not 2's power
  options.init_num_buckets = 3;
  EXPECT_FALSE(options.IsValid());
}

TEST_P(PersistentHashMapTest, OptionsNumBucketsRequiredExceedsMaxNumBuckets) {
  Options options(/*value_type_size_in=*/sizeof(int));
  ASSERT_TRUE(options.IsValid());

  options.max_num_entries = Entry::kMaxNumEntries;
  options.max_load_factor_percent = 30;
  EXPECT_FALSE(options.IsValid());
}

TEST_P(PersistentHashMapTest,
       OptionsEstimatedNumKeyValuePairExceedsStorageMaxSize) {
  Options options(/*value_type_size_in=*/sizeof(int));
  ASSERT_TRUE(options.IsValid());

  options.max_num_entries = 1 << 20;
  options.average_kv_byte_size = 1 << 20;
  ASSERT_THAT(static_cast<int64_t>(options.max_num_entries) *
                  options.average_kv_byte_size,
              Gt(PersistentHashMap::kMaxKVTotalByteSize));
  EXPECT_FALSE(options.IsValid());
}

TEST_P(PersistentHashMapTest, InvalidWorkingPath) {
  EXPECT_THAT(PersistentHashMap::Create(
                  filesystem_, "/dev/null/persistent_hash_map_test",
                  Options(/*value_type_size_in=*/sizeof(int))),
              StatusIs(libtextclassifier3::StatusCode::INTERNAL));
}

TEST_P(PersistentHashMapTest, CreateWithInvalidOptionsShouldFail) {
  Options invalid_options(/*value_type_size_in=*/-1);
  invalid_options.pre_mapping_fbv = GetParam();
  ASSERT_FALSE(invalid_options.IsValid());

  EXPECT_THAT(
      PersistentHashMap::Create(filesystem_, working_path_, invalid_options),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(PersistentHashMapTest, InitializeNewFiles) {
  {
    ASSERT_FALSE(filesystem_.DirectoryExists(working_path_.c_str()));

    Options options(/*value_type_size_in=*/sizeof(int));
    options.pre_mapping_fbv = GetParam();
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_,
                                  std::move(options)));
    EXPECT_THAT(persistent_hash_map, Pointee(IsEmpty()));

    ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
  }

  // Metadata file should be initialized correctly for both info and crcs
  // sections.
  const std::string metadata_file_path = absl_ports::StrCat(
      working_path_, "/", PersistentHashMap::kFilePrefix, ".m");
  ScopedFd metadata_sfd(filesystem_.OpenForWrite(metadata_file_path.c_str()));
  ASSERT_TRUE(metadata_sfd.is_valid());

  // Check info section
  Info info;
  ASSERT_TRUE(filesystem_.PRead(metadata_sfd.get(), &info, sizeof(Info),
                                PersistentHashMap::kInfoMetadataFileOffset));
  EXPECT_THAT(info.magic, Eq(Info::kMagic));
  EXPECT_THAT(info.value_type_size, Eq(sizeof(int)));
  EXPECT_THAT(info.max_load_factor_percent,
              Eq(Options::kDefaultMaxLoadFactorPercent));
  EXPECT_THAT(info.num_deleted_entries, Eq(0));
  EXPECT_THAT(info.num_deleted_key_value_bytes, Eq(0));

  // Check crcs section
  Crcs crcs;
  ASSERT_TRUE(filesystem_.PRead(metadata_sfd.get(), &crcs, sizeof(Crcs),
                                PersistentHashMap::kCrcsMetadataFileOffset));
  // # of elements in bucket_storage should be 1, so it should have non-zero
  // all storages crc value.
  EXPECT_THAT(crcs.component_crcs.storages_crc, Ne(0));
  EXPECT_THAT(crcs.component_crcs.info_crc,
              Eq(Crc32(std::string_view(reinterpret_cast<const char*>(&info),
                                        sizeof(Info)))
                     .Get()));
  EXPECT_THAT(crcs.all_crc,
              Eq(Crc32(std::string_view(
                           reinterpret_cast<const char*>(&crcs.component_crcs),
                           sizeof(Crcs::ComponentCrcs)))
                     .Get()));
}

TEST_P(PersistentHashMapTest, InitializeNewFilesWithCustomInitNumBuckets) {
  int custom_init_num_buckets = 128;

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/custom_init_num_buckets,
              /*pre_mapping_fbv=*/GetParam())));
  EXPECT_THAT(persistent_hash_map->num_buckets(), Eq(custom_init_num_buckets));
}

TEST_P(PersistentHashMapTest,
       InitializeNewFilesWithInitNumBucketsSmallerThanNumBucketsRequired) {
  int init_num_buckets = 65536;

  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/1,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/init_num_buckets,
              /*pre_mapping_fbv=*/GetParam())));
  EXPECT_THAT(persistent_hash_map->num_buckets(), Eq(init_num_buckets));
}

TEST_P(PersistentHashMapTest, InitNumBucketsShouldNotAffectExistingFiles) {
  Options options(/*value_type_size_in=*/sizeof(int));
  options.pre_mapping_fbv = GetParam();

  int original_init_num_buckets = 4;
  {
    options.init_num_buckets = original_init_num_buckets;
    ASSERT_TRUE(options.IsValid());

    // Create new persistent hash map
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));
    EXPECT_THAT(persistent_hash_map->num_buckets(),
                Eq(original_init_num_buckets));

    ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
  }

  // Set new init_num_buckets.
  options.init_num_buckets = 8;
  ASSERT_TRUE(options.IsValid());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(filesystem_, working_path_, options));
  // # of buckets should still be the original value.
  EXPECT_THAT(persistent_hash_map->num_buckets(),
              Eq(original_init_num_buckets));
}

TEST_P(PersistentHashMapTest,
       InitializationShouldFailWithoutPersistToDiskOrDestruction) {
  Options options(/*value_type_size_in=*/sizeof(int));
  options.pre_mapping_fbv = GetParam();

  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(filesystem_, working_path_, options));

  // Put some key value pairs.
  ICING_ASSERT_OK(persistent_hash_map->Put("a", Serialize(1).data()));
  ICING_ASSERT_OK(persistent_hash_map->Put("b", Serialize(2).data()));
  ICING_ASSERT_OK(persistent_hash_map->Put("c", Serialize(3).data()));
  // Call Delete() to change PersistentHashMap metadata info
  // (num_deleted_entries)
  ICING_ASSERT_OK(persistent_hash_map->Delete("c"));

  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(2)));
  ASSERT_THAT(GetValueByKey(persistent_hash_map.get(), "a"), IsOkAndHolds(1));
  ASSERT_THAT(GetValueByKey(persistent_hash_map.get(), "b"), IsOkAndHolds(2));

  // Without calling PersistToDisk, checksums will not be recomputed or synced
  // to disk, so initializing another instance on the same files should fail.
  EXPECT_THAT(PersistentHashMap::Create(filesystem_, working_path_, options),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
}

TEST_P(PersistentHashMapTest, InitializationShouldSucceedWithPersistToDisk) {
  Options options(/*value_type_size_in=*/sizeof(int));
  options.pre_mapping_fbv = GetParam();

  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map1,
      PersistentHashMap::Create(filesystem_, working_path_, options));

  // Put some key value pairs.
  ICING_ASSERT_OK(persistent_hash_map1->Put("a", Serialize(1).data()));
  ICING_ASSERT_OK(persistent_hash_map1->Put("b", Serialize(2).data()));
  ICING_ASSERT_OK(persistent_hash_map1->Put("c", Serialize(3).data()));
  // Call Delete() to change PersistentHashMap metadata info
  // (num_deleted_entries)
  ICING_ASSERT_OK(persistent_hash_map1->Delete("c"));

  ASSERT_THAT(persistent_hash_map1, Pointee(SizeIs(2)));
  ASSERT_THAT(GetValueByKey(persistent_hash_map1.get(), "a"), IsOkAndHolds(1));
  ASSERT_THAT(GetValueByKey(persistent_hash_map1.get(), "b"), IsOkAndHolds(2));

  // After calling PersistToDisk, all checksums should be recomputed and synced
  // correctly to disk, so initializing another instance on the same files
  // should succeed, and we should be able to get the same contents.
  ICING_EXPECT_OK(persistent_hash_map1->PersistToDisk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map2,
      PersistentHashMap::Create(filesystem_, working_path_, options));
  EXPECT_THAT(persistent_hash_map2, Pointee(SizeIs(2)));
  EXPECT_THAT(GetValueByKey(persistent_hash_map2.get(), "a"), IsOkAndHolds(1));
  EXPECT_THAT(GetValueByKey(persistent_hash_map2.get(), "b"), IsOkAndHolds(2));
}

TEST_P(PersistentHashMapTest, InitializationShouldSucceedAfterDestruction) {
  Options options(/*value_type_size_in=*/sizeof(int));
  options.pre_mapping_fbv = GetParam();

  {
    // Create new persistent hash map
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));
    ICING_ASSERT_OK(persistent_hash_map->Put("a", Serialize(1).data()));
    ICING_ASSERT_OK(persistent_hash_map->Put("b", Serialize(2).data()));
    ICING_ASSERT_OK(persistent_hash_map->Put("c", Serialize(3).data()));
    // Call Delete() to change PersistentHashMap metadata info
    // (num_deleted_entries)
    ICING_ASSERT_OK(persistent_hash_map->Delete("c"));

    ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(2)));
    ASSERT_THAT(GetValueByKey(persistent_hash_map.get(), "a"), IsOkAndHolds(1));
    ASSERT_THAT(GetValueByKey(persistent_hash_map.get(), "b"), IsOkAndHolds(2));
  }

  {
    // The previous instance went out of scope and was destructed. Although we
    // didn't call PersistToDisk explicitly, the destructor should invoke it and
    // thus initializing another instance on the same files should succeed, and
    // we should be able to get the same contents.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));
    EXPECT_THAT(persistent_hash_map, Pointee(SizeIs(2)));
    EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "a"), IsOkAndHolds(1));
    EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "b"), IsOkAndHolds(2));
  }
}

TEST_P(PersistentHashMapTest,
       InitializeExistingFilesWithDifferentMagicShouldFail) {
  Options options(/*value_type_size_in=*/sizeof(int));
  options.pre_mapping_fbv = GetParam();

  {
    // Create new persistent hash map
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));
    ICING_ASSERT_OK(persistent_hash_map->Put("a", Serialize(1).data()));

    ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
  }

  {
    // Manually change kMagic and update checksum
    const std::string metadata_file_path = absl_ports::StrCat(
        working_path_, "/", PersistentHashMap::kFilePrefix, ".m");
    ScopedFd metadata_sfd(filesystem_.OpenForWrite(metadata_file_path.c_str()));
    ASSERT_TRUE(metadata_sfd.is_valid());

    Crcs crcs;
    ASSERT_TRUE(filesystem_.PRead(metadata_sfd.get(), &crcs, sizeof(Crcs),
                                  PersistentHashMap::kCrcsMetadataFileOffset));

    Info info;
    ASSERT_TRUE(filesystem_.PRead(metadata_sfd.get(), &info, sizeof(Info),
                                  PersistentHashMap::kInfoMetadataFileOffset));

    // Manually change magic and update checksums.
    info.magic += kCorruptedValueOffset;
    crcs.component_crcs.info_crc = info.ComputeChecksum().Get();
    crcs.all_crc = crcs.component_crcs.ComputeChecksum().Get();
    ASSERT_TRUE(filesystem_.PWrite(metadata_sfd.get(),
                                   PersistentHashMap::kCrcsMetadataFileOffset,
                                   &crcs, sizeof(Crcs)));
    ASSERT_TRUE(filesystem_.PWrite(metadata_sfd.get(),
                                   PersistentHashMap::kInfoMetadataFileOffset,
                                   &info, sizeof(Info)));
  }

  {
    // Attempt to create the persistent hash map with different magic. This
    // should fail.
    libtextclassifier3::StatusOr<std::unique_ptr<PersistentHashMap>>
        persistent_hash_map_or =
            PersistentHashMap::Create(filesystem_, working_path_, options);
    EXPECT_THAT(persistent_hash_map_or,
                StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
    EXPECT_THAT(persistent_hash_map_or.status().error_message(),
                HasSubstr("PersistentHashMap header magic mismatch"));
  }
}

TEST_P(PersistentHashMapTest,
       InitializeExistingFilesWithDifferentValueTypeSizeShouldFail) {
  {
    // Create new persistent hash map
    Options options(/*value_type_size_in=*/sizeof(int));
    options.pre_mapping_fbv = GetParam();
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));
    ICING_ASSERT_OK(persistent_hash_map->Put("a", Serialize(1).data()));

    ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
  }

  {
    // Attempt to create the persistent hash map with different value type size.
    // This should fail.
    ASSERT_THAT(sizeof(char), Ne(sizeof(int)));

    Options options(/*value_type_size_in=*/sizeof(char));
    options.pre_mapping_fbv = GetParam();
    libtextclassifier3::StatusOr<std::unique_ptr<PersistentHashMap>>
        persistent_hash_map_or =
            PersistentHashMap::Create(filesystem_, working_path_, options);
    EXPECT_THAT(persistent_hash_map_or,
                StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
    EXPECT_THAT(persistent_hash_map_or.status().error_message(),
                HasSubstr("Incorrect value type size"));
  }
}

TEST_P(PersistentHashMapTest,
       InitializeExistingFilesWithMaxNumEntriesSmallerThanSizeShouldFail) {
  Options options(/*value_type_size_in=*/sizeof(int));
  options.pre_mapping_fbv = GetParam();

  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(filesystem_, working_path_, options));
  ICING_ASSERT_OK(persistent_hash_map->Put("a", Serialize(1).data()));
  ICING_ASSERT_OK(persistent_hash_map->Put("b", Serialize(2).data()));

  ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());

  {
    // Attempt to create the persistent hash map with max num entries smaller
    // than the current size. This should fail.
    options.max_num_entries = 1;
    ASSERT_TRUE(options.IsValid());

    EXPECT_THAT(PersistentHashMap::Create(filesystem_, working_path_, options),
                StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  }

  // Delete 1 kvp.
  ICING_ASSERT_OK(persistent_hash_map->Delete("a"));
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(1)));
  ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());

  {
    // Attempt to create the persistent hash map with max num entries:
    // - Not smaller than current # of active kvps.
    // - Smaller than # of all inserted kvps (regardless of activeness).
    // This should fail.
    options.max_num_entries = 1;
    ASSERT_TRUE(options.IsValid());

    EXPECT_THAT(PersistentHashMap::Create(filesystem_, working_path_, options),
                StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  }
}

TEST_P(PersistentHashMapTest, InitializeExistingFilesWithWrongAllCrc) {
  Options options(/*value_type_size_in=*/sizeof(int));
  options.pre_mapping_fbv = GetParam();

  {
    // Create new persistent hash map
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));
    ICING_ASSERT_OK(persistent_hash_map->Put("a", Serialize(1).data()));

    ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
  }

  const std::string metadata_file_path = absl_ports::StrCat(
      working_path_, "/", PersistentHashMap::kFilePrefix, ".m");
  ScopedFd metadata_sfd(filesystem_.OpenForWrite(metadata_file_path.c_str()));
  ASSERT_TRUE(metadata_sfd.is_valid());

  Crcs crcs;
  ASSERT_TRUE(filesystem_.PRead(metadata_sfd.get(), &crcs, sizeof(Crcs),
                                PersistentHashMap::kCrcsMetadataFileOffset));

  // Manually corrupt all_crc
  crcs.all_crc += kCorruptedValueOffset;
  ASSERT_TRUE(filesystem_.PWrite(metadata_sfd.get(),
                                 PersistentHashMap::kCrcsMetadataFileOffset,
                                 &crcs, sizeof(Crcs)));
  metadata_sfd.reset();

  {
    // Attempt to create the persistent hash map with metadata containing
    // corrupted all_crc. This should fail.
    libtextclassifier3::StatusOr<std::unique_ptr<PersistentHashMap>>
        persistent_hash_map_or =
            PersistentHashMap::Create(filesystem_, working_path_, options);
    EXPECT_THAT(persistent_hash_map_or,
                StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
    EXPECT_THAT(persistent_hash_map_or.status().error_message(),
                HasSubstr("Invalid all crc"));
  }
}

TEST_P(PersistentHashMapTest,
       InitializeExistingFilesWithCorruptedInfoShouldFail) {
  Options options(/*value_type_size_in=*/sizeof(int));
  options.pre_mapping_fbv = GetParam();

  {
    // Create new persistent hash map
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));
    ICING_ASSERT_OK(persistent_hash_map->Put("a", Serialize(1).data()));

    ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
  }

  const std::string metadata_file_path = absl_ports::StrCat(
      working_path_, "/", PersistentHashMap::kFilePrefix, ".m");
  ScopedFd metadata_sfd(filesystem_.OpenForWrite(metadata_file_path.c_str()));
  ASSERT_TRUE(metadata_sfd.is_valid());

  Info info;
  ASSERT_TRUE(filesystem_.PRead(metadata_sfd.get(), &info, sizeof(Info),
                                PersistentHashMap::kInfoMetadataFileOffset));

  // Modify info, but don't update the checksum. This would be similar to
  // corruption of info.
  info.num_deleted_entries += kCorruptedValueOffset;
  ASSERT_TRUE(filesystem_.PWrite(metadata_sfd.get(),
                                 PersistentHashMap::kInfoMetadataFileOffset,
                                 &info, sizeof(Info)));
  {
    // Attempt to create the persistent hash map with info that doesn't match
    // its checksum and confirm that it fails.
    libtextclassifier3::StatusOr<std::unique_ptr<PersistentHashMap>>
        persistent_hash_map_or =
            PersistentHashMap::Create(filesystem_, working_path_, options);
    EXPECT_THAT(persistent_hash_map_or,
                StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
    EXPECT_THAT(persistent_hash_map_or.status().error_message(),
                HasSubstr("Invalid info crc"));
  }
}

TEST_P(PersistentHashMapTest,
       InitializeExistingFilesWithCorruptedBucketStorage) {
  Options options(/*value_type_size_in=*/sizeof(int));
  options.pre_mapping_fbv = GetParam();

  {
    // Create new persistent hash map
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));
    ICING_ASSERT_OK(persistent_hash_map->Put("a", Serialize(1).data()));

    ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
  }

  {
    // Update bucket storage manually.
    const std::string bucket_storage_file_path = absl_ports::StrCat(
        working_path_, "/", PersistentHashMap::kFilePrefix, ".b");
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<Bucket>> bucket_storage,
        FileBackedVector<Bucket>::Create(
            filesystem_, bucket_storage_file_path,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
    ICING_ASSERT_OK_AND_ASSIGN(Crc32 old_crc,
                               bucket_storage->ComputeChecksum());
    ICING_ASSERT_OK(bucket_storage->Append(Bucket()));
    ICING_ASSERT_OK(bucket_storage->PersistToDisk());
    ICING_ASSERT_OK_AND_ASSIGN(Crc32 new_crc,
                               bucket_storage->ComputeChecksum());
    ASSERT_THAT(old_crc, Not(Eq(new_crc)));
  }

  {
    // Attempt to create the persistent hash map with metadata containing
    // corrupted bucket_storage_crc. This should fail.
    libtextclassifier3::StatusOr<std::unique_ptr<PersistentHashMap>>
        persistent_hash_map_or =
            PersistentHashMap::Create(filesystem_, working_path_, options);
    EXPECT_THAT(persistent_hash_map_or,
                StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
    EXPECT_THAT(persistent_hash_map_or.status().error_message(),
                HasSubstr("Invalid storages crc"));
  }
}

TEST_P(PersistentHashMapTest,
       InitializeExistingFilesWithCorruptedEntryStorage) {
  Options options(/*value_type_size_in=*/sizeof(int));
  options.pre_mapping_fbv = GetParam();

  {
    // Create new persistent hash map
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));
    ICING_ASSERT_OK(persistent_hash_map->Put("a", Serialize(1).data()));

    ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
  }

  {
    // Update entry storage manually.
    const std::string entry_storage_file_path = absl_ports::StrCat(
        working_path_, "/", PersistentHashMap::kFilePrefix, ".e");
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<Entry>> entry_storage,
        FileBackedVector<Entry>::Create(
            filesystem_, entry_storage_file_path,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
    ICING_ASSERT_OK_AND_ASSIGN(Crc32 old_crc, entry_storage->ComputeChecksum());
    ICING_ASSERT_OK(entry_storage->Append(
        Entry(/*key_value_index=*/-1, /*next_entry_index=*/-1)));
    ICING_ASSERT_OK(entry_storage->PersistToDisk());
    ICING_ASSERT_OK_AND_ASSIGN(Crc32 new_crc, entry_storage->ComputeChecksum());
    ASSERT_THAT(old_crc, Not(Eq(new_crc)));
  }

  {
    // Attempt to create the persistent hash map with metadata containing
    // corrupted entry_storage_crc. This should fail.
    libtextclassifier3::StatusOr<std::unique_ptr<PersistentHashMap>>
        persistent_hash_map_or =
            PersistentHashMap::Create(filesystem_, working_path_, options);
    EXPECT_THAT(persistent_hash_map_or,
                StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
    EXPECT_THAT(persistent_hash_map_or.status().error_message(),
                HasSubstr("Invalid storages crc"));
  }
}

TEST_P(PersistentHashMapTest,
       InitializeExistingFilesWithCorruptedKeyValueStorage) {
  Options options(/*value_type_size_in=*/sizeof(int));
  options.pre_mapping_fbv = GetParam();

  {
    // Create new persistent hash map
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));
    ICING_ASSERT_OK(persistent_hash_map->Put("a", Serialize(1).data()));

    ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
  }

  {
    // Update kv storage manually.
    const std::string kv_storage_file_path = absl_ports::StrCat(
        working_path_, "/", PersistentHashMap::kFilePrefix, ".k");
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<char>> kv_storage,
        FileBackedVector<char>::Create(
            filesystem_, kv_storage_file_path,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
    ICING_ASSERT_OK_AND_ASSIGN(Crc32 old_crc, kv_storage->ComputeChecksum());
    ICING_ASSERT_OK(kv_storage->Append('z'));
    ICING_ASSERT_OK(kv_storage->PersistToDisk());
    ICING_ASSERT_OK_AND_ASSIGN(Crc32 new_crc, kv_storage->ComputeChecksum());
    ASSERT_THAT(old_crc, Not(Eq(new_crc)));
  }

  {
    // Attempt to create the persistent hash map with metadata containing
    // corrupted kv_storage_crc. This should fail.
    libtextclassifier3::StatusOr<std::unique_ptr<PersistentHashMap>>
        persistent_hash_map_or =
            PersistentHashMap::Create(filesystem_, working_path_, options);
    EXPECT_THAT(persistent_hash_map_or,
                StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
    EXPECT_THAT(persistent_hash_map_or.status().error_message(),
                HasSubstr("Invalid storages crc"));
  }
}

TEST_P(PersistentHashMapTest,
       InitializeExistingFilesAllowDifferentMaxLoadFactorPercent) {
  Options options(
      /*value_type_size_in=*/sizeof(int),
      /*max_num_entries_in=*/Entry::kMaxNumEntries,
      /*max_load_factor_percent_in=*/Options::kDefaultMaxLoadFactorPercent,
      /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
      /*init_num_buckets_in=*/kTestInitNumBuckets,
      /*pre_mapping_fbv=*/GetParam());

  {
    // Create new persistent hash map
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));
    ICING_ASSERT_OK(persistent_hash_map->Put("a", Serialize(1).data()));
    ICING_ASSERT_OK(persistent_hash_map->Put("b", Serialize(2).data()));

    ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(2)));
    ASSERT_THAT(GetValueByKey(persistent_hash_map.get(), "a"), IsOkAndHolds(1));
    ASSERT_THAT(GetValueByKey(persistent_hash_map.get(), "b"), IsOkAndHolds(2));

    ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
  }

  {
    // Set new max_load_factor_percent.
    options.max_load_factor_percent = 200;
    ASSERT_TRUE(options.IsValid());
    ASSERT_THAT(options.max_load_factor_percent,
                Ne(Options::kDefaultMaxLoadFactorPercent));

    // Attempt to create the persistent hash map with different max load factor
    // percent. This should succeed and metadata should be modified correctly.
    // Also verify all entries should remain unchanged.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));

    EXPECT_THAT(persistent_hash_map, Pointee(SizeIs(2)));
    EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "a"), IsOkAndHolds(1));
    EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "b"), IsOkAndHolds(2));

    ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
  }

  const std::string metadata_file_path = absl_ports::StrCat(
      working_path_, "/", PersistentHashMap::kFilePrefix, ".m");
  ScopedFd metadata_sfd(filesystem_.OpenForWrite(metadata_file_path.c_str()));
  ASSERT_TRUE(metadata_sfd.is_valid());

  Info info;
  ASSERT_TRUE(filesystem_.PRead(metadata_sfd.get(), &info, sizeof(Info),
                                PersistentHashMap::kInfoMetadataFileOffset));
  EXPECT_THAT(info.max_load_factor_percent,
              Eq(options.max_load_factor_percent));

  // Also should update crcs correctly. We test it by creating instance again
  // and make sure it won't get corrupted crcs/info errors.
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));

    ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
  }
}

TEST_P(PersistentHashMapTest,
       InitializeExistingFilesWithDifferentMaxLoadFactorPercentShouldRehash) {
  Options options(
      /*value_type_size_in=*/sizeof(int),
      /*max_num_entries_in=*/Entry::kMaxNumEntries,
      /*max_load_factor_percent_in=*/Options::kDefaultMaxLoadFactorPercent,
      /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
      /*init_num_buckets_in=*/kTestInitNumBuckets,
      /*pre_mapping_fbv=*/GetParam());

  double prev_loading_percent;
  int prev_num_buckets;
  {
    // Create new persistent hash map
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));
    ICING_ASSERT_OK(persistent_hash_map->Put("a", Serialize(1).data()));
    ICING_ASSERT_OK(persistent_hash_map->Put("b", Serialize(2).data()));
    ICING_ASSERT_OK(persistent_hash_map->Put("c", Serialize(3).data()));

    ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(3)));
    ASSERT_THAT(GetValueByKey(persistent_hash_map.get(), "a"), IsOkAndHolds(1));
    ASSERT_THAT(GetValueByKey(persistent_hash_map.get(), "b"), IsOkAndHolds(2));
    ASSERT_THAT(GetValueByKey(persistent_hash_map.get(), "c"), IsOkAndHolds(3));

    prev_loading_percent = persistent_hash_map->size() * 100.0 /
                           persistent_hash_map->num_buckets();
    prev_num_buckets = persistent_hash_map->num_buckets();
    ASSERT_THAT(prev_loading_percent,
                Not(Gt(Options::kDefaultMaxLoadFactorPercent)));

    ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
  }

  {
    // Set greater max_load_factor_percent.
    options.max_load_factor_percent = 150;
    ASSERT_TRUE(options.IsValid());
    ASSERT_THAT(options.max_load_factor_percent, Gt(prev_loading_percent));

    // Attempt to create the persistent hash map with max load factor greater
    // than previous loading. There should be no rehashing and # of buckets
    // should remain the same.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));

    EXPECT_THAT(persistent_hash_map->num_buckets(), Eq(prev_num_buckets));

    ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
  }

  {
    // Set smaller max_load_factor_percent.
    options.max_load_factor_percent = 50;
    ASSERT_TRUE(options.IsValid());
    ASSERT_THAT(options.max_load_factor_percent, Lt(prev_loading_percent));

    // Attempt to create the persistent hash map with max load factor smaller
    // than previous loading. There should be rehashing since the loading
    // exceeds the limit.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<PersistentHashMap> persistent_hash_map,
        PersistentHashMap::Create(filesystem_, working_path_, options));

    // After changing max_load_factor_percent, there should be rehashing and the
    // new loading should not be greater than the new max load factor.
    EXPECT_THAT(persistent_hash_map->size() * 100.0 /
                    persistent_hash_map->num_buckets(),
                Not(Gt(options.max_load_factor_percent)));
    EXPECT_THAT(persistent_hash_map->num_buckets(), Ne(prev_num_buckets));

    EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "a"), IsOkAndHolds(1));
    EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "b"), IsOkAndHolds(2));
    EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "c"), IsOkAndHolds(3));

    ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
  }
}

TEST_P(PersistentHashMapTest, PutAndGet) {
  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv=*/GetParam())));

  EXPECT_THAT(persistent_hash_map, Pointee(IsEmpty()));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-youtube.com"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  ICING_EXPECT_OK(
      persistent_hash_map->Put("default-google.com", Serialize(100).data()));
  ICING_EXPECT_OK(
      persistent_hash_map->Put("default-youtube.com", Serialize(50).data()));

  EXPECT_THAT(persistent_hash_map, Pointee(SizeIs(2)));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com"),
              IsOkAndHolds(100));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-youtube.com"),
              IsOkAndHolds(50));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "key-not-exist"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  ICING_ASSERT_OK(persistent_hash_map->PersistToDisk());
}

TEST_P(PersistentHashMapTest, PutShouldOverwriteValueIfKeyExists) {
  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv=*/GetParam())));

  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com", Serialize(100).data()));
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(1)));
  ASSERT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com"),
              IsOkAndHolds(100));

  ICING_EXPECT_OK(
      persistent_hash_map->Put("default-google.com", Serialize(200).data()));
  EXPECT_THAT(persistent_hash_map, Pointee(SizeIs(1)));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com"),
              IsOkAndHolds(200));

  ICING_EXPECT_OK(
      persistent_hash_map->Put("default-google.com", Serialize(300).data()));
  EXPECT_THAT(persistent_hash_map, Pointee(SizeIs(1)));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com"),
              IsOkAndHolds(300));
}

TEST_P(PersistentHashMapTest, ShouldRehash) {
  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv=*/GetParam())));

  int original_num_buckets = persistent_hash_map->num_buckets();
  // Insert 100 key value pairs. There should be rehashing so the loading of
  // hash map doesn't exceed max_load_factor.
  for (int i = 0; i < 100; ++i) {
    std::string key = "default-google.com-" + std::to_string(i);
    ICING_ASSERT_OK(persistent_hash_map->Put(key, &i));
    ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(i + 1)));

    EXPECT_THAT(persistent_hash_map->size() * 100.0 /
                    persistent_hash_map->num_buckets(),
                Not(Gt(Options::kDefaultMaxLoadFactorPercent)));
  }
  EXPECT_THAT(persistent_hash_map->num_buckets(), Ne(original_num_buckets));

  // After rehashing, we should still be able to get all inserted entries.
  for (int i = 0; i < 100; ++i) {
    std::string key = "default-google.com-" + std::to_string(i);
    EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), key), IsOkAndHolds(i));
  }
}

TEST_P(PersistentHashMapTest, GetOrPutShouldPutIfKeyDoesNotExist) {
  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv=*/GetParam())));

  ASSERT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  int val = 1;
  EXPECT_THAT(persistent_hash_map->GetOrPut("default-google.com", &val),
              IsOk());
  EXPECT_THAT(val, Eq(1));
  EXPECT_THAT(persistent_hash_map, Pointee(SizeIs(1)));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com"),
              IsOkAndHolds(1));
}

TEST_P(PersistentHashMapTest, GetOrPutShouldGetIfKeyExists) {
  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv_in=*/GetParam())));

  ASSERT_THAT(
      persistent_hash_map->Put("default-google.com", Serialize(1).data()),
      IsOk());
  ASSERT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com"),
              IsOkAndHolds(1));

  int val = 2;
  EXPECT_THAT(persistent_hash_map->GetOrPut("default-google.com", &val),
              IsOk());
  EXPECT_THAT(val, Eq(1));
  EXPECT_THAT(persistent_hash_map, Pointee(SizeIs(1)));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com"),
              IsOkAndHolds(1));
}

TEST_P(PersistentHashMapTest, Delete) {
  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv_in=*/GetParam())));

  // Delete a non-existing key should get NOT_FOUND error
  EXPECT_THAT(persistent_hash_map->Delete("default-google.com"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com", Serialize(100).data()));
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-youtube.com", Serialize(50).data()));
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(2)));

  // Delete an existing key should succeed
  ICING_EXPECT_OK(persistent_hash_map->Delete("default-google.com"));
  EXPECT_THAT(persistent_hash_map, Pointee(SizeIs(1)));
  // The deleted key should not be found.
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  // Other key should remain unchanged and available
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-youtube.com"),
              IsOkAndHolds(50));

  // Insert back the deleted key. Should get new value
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com", Serialize(200).data()));
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(2)));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com"),
              IsOkAndHolds(200));

  // Delete again
  ICING_EXPECT_OK(persistent_hash_map->Delete("default-google.com"));
  EXPECT_THAT(persistent_hash_map, Pointee(SizeIs(1)));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  // Other keys should remain unchanged and available
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-youtube.com"),
              IsOkAndHolds(50));
}

TEST_P(PersistentHashMapTest, DeleteMultiple) {
  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv_in=*/GetParam())));

  std::unordered_map<std::string, int> existing_keys;
  std::unordered_set<std::string> deleted_keys;
  // Insert 100 key value pairs
  for (int i = 0; i < 100; ++i) {
    std::string key = "default-google.com-" + std::to_string(i);
    ICING_ASSERT_OK(persistent_hash_map->Put(key, &i));
    existing_keys[key] = i;
  }
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(existing_keys.size())));

  // Delete several keys.
  // Simulate with std::unordered_map and verify.
  std::vector<int> delete_target_ids{3, 4, 6, 9, 13, 18, 24, 31, 39, 48, 58};
  for (const int delete_target_id : delete_target_ids) {
    std::string key = "default-google.com-" + std::to_string(delete_target_id);
    ASSERT_THAT(existing_keys, Contains(Key(key)));
    ASSERT_THAT(GetValueByKey(persistent_hash_map.get(), key),
                IsOkAndHolds(existing_keys[key]));
    ICING_EXPECT_OK(persistent_hash_map->Delete(key));

    existing_keys.erase(key);
    deleted_keys.insert(key);
  }

  // Deleted keys should not be found.
  for (const std::string& deleted_key : deleted_keys) {
    EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), deleted_key),
                StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  }
  // Other keys should remain unchanged and available
  for (const auto& [existing_key, existing_value] : existing_keys) {
    EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), existing_key),
                IsOkAndHolds(existing_value));
  }
  // Verify by iterator as well
  EXPECT_THAT(GetAllKeyValuePairs(persistent_hash_map->GetIterator()),
              Eq(existing_keys));
}

TEST_P(PersistentHashMapTest, DeleteBucketHeadElement) {
  // Create new persistent hash map
  // Set max_load_factor_percent as 1000. Load factor percent is calculated as
  // 100 * num_keys / num_buckets. Therefore, with 1 bucket (the initial # of
  // buckets in an empty PersistentHashMap) and a max_load_factor_percent of
  // 1000, we would allow the insertion of up to 10 keys before rehashing.
  // Preventing rehashing makes it much easier to test collisions.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/1000,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv_in=*/GetParam())));

  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-0", Serialize(0).data()));
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-1", Serialize(1).data()));
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-2", Serialize(2).data()));
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(3)));
  ASSERT_THAT(persistent_hash_map->num_buckets(), Eq(1));

  // Delete the head element of the bucket. Note that in our implementation, the
  // last added element will become the head element of the bucket.
  ICING_ASSERT_OK(persistent_hash_map->Delete("default-google.com-2"));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com-0"),
              IsOkAndHolds(0));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com-1"),
              IsOkAndHolds(1));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com-2"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_P(PersistentHashMapTest, DeleteBucketIntermediateElement) {
  // Create new persistent hash map
  // Set max_load_factor_percent as 1000. Load factor percent is calculated as
  // 100 * num_keys / num_buckets. Therefore, with 1 bucket (the initial # of
  // buckets in an empty PersistentHashMap) and a max_load_factor_percent of
  // 1000, we would allow the insertion of up to 10 keys before rehashing.
  // Preventing rehashing makes it much easier to test collisions.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/1000,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv_in=*/GetParam())));

  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-0", Serialize(0).data()));
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-1", Serialize(1).data()));
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-2", Serialize(2).data()));
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(3)));
  ASSERT_THAT(persistent_hash_map->num_buckets(), Eq(1));

  // Delete any intermediate element of the bucket.
  ICING_ASSERT_OK(persistent_hash_map->Delete("default-google.com-1"));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com-0"),
              IsOkAndHolds(0));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com-1"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com-2"),
              IsOkAndHolds(2));
}

TEST_P(PersistentHashMapTest, DeleteBucketTailElement) {
  // Create new persistent hash map
  // Set max_load_factor_percent as 1000. Load factor percent is calculated as
  // 100 * num_keys / num_buckets. Therefore, with 1 bucket (the initial # of
  // buckets in an empty PersistentHashMap) and a max_load_factor_percent of
  // 1000, we would allow the insertion of up to 10 keys before rehashing.
  // Preventing rehashing makes it much easier to test collisions.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/1000,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv_in=*/GetParam())));

  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-0", Serialize(0).data()));
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-1", Serialize(1).data()));
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-2", Serialize(2).data()));
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(3)));
  ASSERT_THAT(persistent_hash_map->num_buckets(), Eq(1));

  // Delete the last element of the bucket. Note that in our implementation, the
  // first added element will become the tail element of the bucket.
  ICING_ASSERT_OK(persistent_hash_map->Delete("default-google.com-0"));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com-0"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com-1"),
              IsOkAndHolds(1));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com-2"),
              IsOkAndHolds(2));
}

TEST_P(PersistentHashMapTest, DeleteBucketOnlySingleElement) {
  // Create new persistent hash map
  // Set max_load_factor_percent as 1000. Load factor percent is calculated as
  // 100 * num_keys / num_buckets. Therefore, with 1 bucket (the initial # of
  // buckets in an empty PersistentHashMap) and a max_load_factor_percent of
  // 1000, we would allow the insertion of up to 10 keys before rehashing.
  // Preventing rehashing makes it much easier to test collisions.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/1000,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv_in=*/GetParam())));

  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com", Serialize(100).data()));
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(1)));

  // Delete the only single element of the bucket.
  ICING_ASSERT_OK(persistent_hash_map->Delete("default-google.com"));
  ASSERT_THAT(persistent_hash_map, Pointee(IsEmpty()));
  EXPECT_THAT(GetValueByKey(persistent_hash_map.get(), "default-google.com"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_P(PersistentHashMapTest, OperationsWhenReachingMaxNumEntries) {
  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/1,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv_in=*/GetParam())));

  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com", Serialize(100).data()));
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(1)));

  // Put new key should fail.
  EXPECT_THAT(
      persistent_hash_map->Put("default-youtube.com", Serialize(50).data()),
      StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  // Modify existing key should succeed.
  EXPECT_THAT(
      persistent_hash_map->Put("default-google.com", Serialize(200).data()),
      IsOk());

  // Put after delete should still fail. See the comment in
  // PersistentHashMap::Insert for more details.
  ICING_ASSERT_OK(persistent_hash_map->Delete("default-google.com"));
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(0)));
  EXPECT_THAT(
      persistent_hash_map->Put("default-youtube.com", Serialize(50).data()),
      StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST_P(PersistentHashMapTest, ShouldFailIfKeyContainsTerminationCharacter) {
  // Create new persistent hash map
  Options options(/*value_type_size_in=*/sizeof(int));
  options.pre_mapping_fbv = GetParam();
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(filesystem_, working_path_, options));

  const char invalid_key[] = "a\0bc";
  std::string_view invalid_key_view(invalid_key, 4);

  int val = 1;
  EXPECT_THAT(persistent_hash_map->Put(invalid_key_view, &val),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(persistent_hash_map->GetOrPut(invalid_key_view, &val),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(persistent_hash_map->Get(invalid_key_view, &val),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(persistent_hash_map->Delete(invalid_key_view),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_P(PersistentHashMapTest, EmptyHashMapIterator) {
  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv_in=*/GetParam())));

  EXPECT_FALSE(persistent_hash_map->GetIterator().Advance());
}

TEST_P(PersistentHashMapTest, Iterator) {
  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv_in=*/GetParam())));

  std::unordered_map<std::string, int> kvps;
  // Insert 100 key value pairs
  for (int i = 0; i < 100; ++i) {
    std::string key = "default-google.com-" + std::to_string(i);
    ICING_ASSERT_OK(persistent_hash_map->Put(key, &i));
    kvps.emplace(key, i);
  }
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(kvps.size())));

  EXPECT_THAT(GetAllKeyValuePairs(persistent_hash_map->GetIterator()),
              Eq(kvps));
}

TEST_P(PersistentHashMapTest, IteratorAfterDeletingFirstKeyValuePair) {
  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv_in=*/GetParam())));

  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-0", Serialize(0).data()));
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-1", Serialize(1).data()));
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-2", Serialize(2).data()));
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(3)));

  // Delete the first key value pair.
  ICING_ASSERT_OK(persistent_hash_map->Delete("default-google.com-0"));
  EXPECT_THAT(GetAllKeyValuePairs(persistent_hash_map->GetIterator()),
              UnorderedElementsAre(Pair("default-google.com-1", 1),
                                   Pair("default-google.com-2", 2)));
}

TEST_P(PersistentHashMapTest, IteratorAfterDeletingIntermediateKeyValuePair) {
  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv_in=*/GetParam())));

  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-0", Serialize(0).data()));
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-1", Serialize(1).data()));
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-2", Serialize(2).data()));
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(3)));

  // Delete any intermediate key value pair.
  ICING_ASSERT_OK(persistent_hash_map->Delete("default-google.com-1"));
  EXPECT_THAT(GetAllKeyValuePairs(persistent_hash_map->GetIterator()),
              UnorderedElementsAre(Pair("default-google.com-0", 0),
                                   Pair("default-google.com-2", 2)));
}

TEST_P(PersistentHashMapTest, IteratorAfterDeletingLastKeyValuePair) {
  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv_in=*/GetParam())));

  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-0", Serialize(0).data()));
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-1", Serialize(1).data()));
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-2", Serialize(2).data()));
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(3)));

  // Delete the last key value pair.
  ICING_ASSERT_OK(persistent_hash_map->Delete("default-google.com-2"));
  EXPECT_THAT(GetAllKeyValuePairs(persistent_hash_map->GetIterator()),
              UnorderedElementsAre(Pair("default-google.com-0", 0),
                                   Pair("default-google.com-1", 1)));
}

TEST_P(PersistentHashMapTest, IteratorAfterDeletingAllKeyValuePairs) {
  // Create new persistent hash map
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<PersistentHashMap> persistent_hash_map,
      PersistentHashMap::Create(
          filesystem_, working_path_,
          Options(
              /*value_type_size_in=*/sizeof(int),
              /*max_num_entries_in=*/Entry::kMaxNumEntries,
              /*max_load_factor_percent_in=*/
              Options::kDefaultMaxLoadFactorPercent,
              /*average_kv_byte_size_in=*/Options::kDefaultAverageKVByteSize,
              /*init_num_buckets_in=*/kTestInitNumBuckets,
              /*pre_mapping_fbv_in=*/GetParam())));

  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-0", Serialize(0).data()));
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-1", Serialize(1).data()));
  ICING_ASSERT_OK(
      persistent_hash_map->Put("default-google.com-2", Serialize(2).data()));
  ASSERT_THAT(persistent_hash_map, Pointee(SizeIs(3)));

  // Delete all key value pairs.
  ICING_ASSERT_OK(persistent_hash_map->Delete("default-google.com-0"));
  ICING_ASSERT_OK(persistent_hash_map->Delete("default-google.com-1"));
  ICING_ASSERT_OK(persistent_hash_map->Delete("default-google.com-2"));
  ASSERT_THAT(persistent_hash_map, Pointee(IsEmpty()));
  EXPECT_FALSE(persistent_hash_map->GetIterator().Advance());
}

INSTANTIATE_TEST_SUITE_P(PersistentHashMapTest, PersistentHashMapTest,
                         testing::Values(true, false));

}  // namespace

}  // namespace lib
}  // namespace icing
