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

#include "icing/store/key-mapper.h"

#include <memory>
#include <string>
#include <unordered_map>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/store/document-id.h"
#include "icing/store/dynamic-trie-key-mapper.h"
#include "icing/store/persistent-hash-map-key-mapper.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

using ::testing::IsEmpty;
using ::testing::IsTrue;
using ::testing::Pair;
using ::testing::UnorderedElementsAre;

namespace icing {
namespace lib {

namespace {

constexpr int kMaxDynamicTrieKeyMapperSize = 3 * 1024 * 1024;  // 3 MiB

enum class KeyMapperType {
  kDynamicTrie,
  kPersistentHashMap,
};

struct KeyMapperTestParam {
  KeyMapperType key_mapper_type;
  bool pre_mapping_fbv;

  explicit KeyMapperTestParam(KeyMapperType key_mapper_type_in,
                              bool pre_mapping_fbv_in)
      : key_mapper_type(key_mapper_type_in),
        pre_mapping_fbv(pre_mapping_fbv_in) {}
};

class KeyMapperTest : public ::testing::TestWithParam<KeyMapperTestParam> {
 protected:
  void SetUp() override {
    base_dir_ = GetTestTempDir() + "/icing";
    ASSERT_THAT(filesystem_.CreateDirectoryRecursively(base_dir_.c_str()),
                IsTrue());

    working_dir_ = base_dir_ + "/key_mapper";
  }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(base_dir_.c_str());
  }

  libtextclassifier3::StatusOr<std::unique_ptr<KeyMapper<DocumentId>>>
  CreateKeyMapper() {
    const KeyMapperTestParam& param = GetParam();
    switch (param.key_mapper_type) {
      case KeyMapperType::kDynamicTrie:
        return DynamicTrieKeyMapper<DocumentId>::Create(
            filesystem_, working_dir_, kMaxDynamicTrieKeyMapperSize);
      case KeyMapperType::kPersistentHashMap:
        return PersistentHashMapKeyMapper<DocumentId>::Create(
            filesystem_, working_dir_, param.pre_mapping_fbv);
    }
  }

  libtextclassifier3::Status DeleteKeyMapper() {
    const KeyMapperTestParam& param = GetParam();
    switch (param.key_mapper_type) {
      case KeyMapperType::kDynamicTrie:
        return DynamicTrieKeyMapper<DocumentId>::Delete(filesystem_,
                                                        working_dir_);
      case KeyMapperType::kPersistentHashMap:
        return PersistentHashMapKeyMapper<DocumentId>::Delete(filesystem_,
                                                              working_dir_);
    }
  }

  std::string base_dir_;
  std::string working_dir_;
  Filesystem filesystem_;
};

std::unordered_map<std::string, DocumentId> GetAllKeyValuePairs(
    const KeyMapper<DocumentId>* key_mapper) {
  std::unordered_map<std::string, DocumentId> ret;

  std::unique_ptr<typename KeyMapper<DocumentId>::Iterator> itr =
      key_mapper->GetIterator();
  while (itr->Advance()) {
    ret.emplace(itr->GetKey(), itr->GetValue());
  }
  return ret;
}

TEST_P(KeyMapperTest, CreateNewKeyMapper) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<KeyMapper<DocumentId>> key_mapper,
                             CreateKeyMapper());
  EXPECT_THAT(key_mapper->num_keys(), 0);
}

TEST_P(KeyMapperTest, CanUpdateSameKeyMultipleTimes) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<KeyMapper<DocumentId>> key_mapper,
                             CreateKeyMapper());

  ICING_EXPECT_OK(key_mapper->Put("default-google.com", 100));
  ICING_EXPECT_OK(key_mapper->Put("default-youtube.com", 50));

  EXPECT_THAT(key_mapper->Get("default-google.com"), IsOkAndHolds(100));

  ICING_EXPECT_OK(key_mapper->Put("default-google.com", 200));
  EXPECT_THAT(key_mapper->Get("default-google.com"), IsOkAndHolds(200));
  EXPECT_THAT(key_mapper->num_keys(), 2);

  ICING_EXPECT_OK(key_mapper->Put("default-google.com", 300));
  EXPECT_THAT(key_mapper->Get("default-google.com"), IsOkAndHolds(300));
  EXPECT_THAT(key_mapper->num_keys(), 2);
}

TEST_P(KeyMapperTest, GetOrPutOk) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<KeyMapper<DocumentId>> key_mapper,
                             CreateKeyMapper());

  EXPECT_THAT(key_mapper->Get("foo"),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(key_mapper->GetOrPut("foo", 1), IsOkAndHolds(1));
  EXPECT_THAT(key_mapper->Get("foo"), IsOkAndHolds(1));
}

TEST_P(KeyMapperTest, CanPersistToDiskRegularly) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<KeyMapper<DocumentId>> key_mapper,
                             CreateKeyMapper());

  // Can persist an empty DynamicTrieKeyMapper.
  ICING_EXPECT_OK(key_mapper->PersistToDisk());
  EXPECT_THAT(key_mapper->num_keys(), 0);

  // Can persist the smallest DynamicTrieKeyMapper.
  ICING_EXPECT_OK(key_mapper->Put("default-google.com", 100));
  ICING_EXPECT_OK(key_mapper->PersistToDisk());
  EXPECT_THAT(key_mapper->num_keys(), 1);
  EXPECT_THAT(key_mapper->Get("default-google.com"), IsOkAndHolds(100));

  // Can continue to add keys after PersistToDisk().
  ICING_EXPECT_OK(key_mapper->Put("default-youtube.com", 200));
  EXPECT_THAT(key_mapper->num_keys(), 2);
  EXPECT_THAT(key_mapper->Get("default-youtube.com"), IsOkAndHolds(200));

  // Can continue to update the same key after PersistToDisk().
  ICING_EXPECT_OK(key_mapper->Put("default-google.com", 300));
  EXPECT_THAT(key_mapper->Get("default-google.com"), IsOkAndHolds(300));
  EXPECT_THAT(key_mapper->num_keys(), 2);
}

TEST_P(KeyMapperTest, CanUseAcrossMultipleInstances) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<KeyMapper<DocumentId>> key_mapper,
                             CreateKeyMapper());
  ICING_EXPECT_OK(key_mapper->Put("default-google.com", 100));
  ICING_EXPECT_OK(key_mapper->PersistToDisk());

  key_mapper.reset();

  ICING_ASSERT_OK_AND_ASSIGN(key_mapper, CreateKeyMapper());
  EXPECT_THAT(key_mapper->num_keys(), 1);
  EXPECT_THAT(key_mapper->Get("default-google.com"), IsOkAndHolds(100));

  // Can continue to read/write to the KeyMapper.
  ICING_EXPECT_OK(key_mapper->Put("default-youtube.com", 200));
  ICING_EXPECT_OK(key_mapper->Put("default-google.com", 300));
  EXPECT_THAT(key_mapper->num_keys(), 2);
  EXPECT_THAT(key_mapper->Get("default-youtube.com"), IsOkAndHolds(200));
  EXPECT_THAT(key_mapper->Get("default-google.com"), IsOkAndHolds(300));
}

TEST_P(KeyMapperTest, CanDeleteAndRestartKeyMapping) {
  // Can delete even if there's nothing there
  ICING_EXPECT_OK(DeleteKeyMapper());

  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<KeyMapper<DocumentId>> key_mapper,
                             CreateKeyMapper());
  ICING_EXPECT_OK(key_mapper->Put("default-google.com", 100));
  ICING_EXPECT_OK(key_mapper->PersistToDisk());
  ICING_EXPECT_OK(DeleteKeyMapper());

  key_mapper.reset();
  ICING_ASSERT_OK_AND_ASSIGN(key_mapper, CreateKeyMapper());
  EXPECT_THAT(key_mapper->num_keys(), 0);
  ICING_EXPECT_OK(key_mapper->Put("default-google.com", 100));
  EXPECT_THAT(key_mapper->num_keys(), 1);
}

TEST_P(KeyMapperTest, Iterator) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<KeyMapper<DocumentId>> key_mapper,
                             CreateKeyMapper());
  EXPECT_THAT(GetAllKeyValuePairs(key_mapper.get()), IsEmpty());

  ICING_EXPECT_OK(key_mapper->Put("foo", /*value=*/1));
  ICING_EXPECT_OK(key_mapper->Put("bar", /*value=*/2));
  EXPECT_THAT(GetAllKeyValuePairs(key_mapper.get()),
              UnorderedElementsAre(Pair("foo", 1), Pair("bar", 2)));

  ICING_EXPECT_OK(key_mapper->Put("baz", /*value=*/3));
  EXPECT_THAT(
      GetAllKeyValuePairs(key_mapper.get()),
      UnorderedElementsAre(Pair("foo", 1), Pair("bar", 2), Pair("baz", 3)));
}

INSTANTIATE_TEST_SUITE_P(
    KeyMapperTest, KeyMapperTest,
    testing::Values(KeyMapperTestParam(KeyMapperType::kDynamicTrie,
                                       /*pre_mapping_fbv_in=*/true),
                    KeyMapperTestParam(KeyMapperType::kPersistentHashMap,
                                       /*pre_mapping_fbv_in=*/true),
                    KeyMapperTestParam(KeyMapperType::kPersistentHashMap,
                                       /*pre_mapping_fbv_in=*/false)));

}  // namespace

}  // namespace lib
}  // namespace icing
