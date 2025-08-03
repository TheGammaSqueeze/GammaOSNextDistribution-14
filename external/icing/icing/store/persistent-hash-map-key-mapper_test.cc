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

#include "icing/store/persistent-hash-map-key-mapper.h"

#include <string>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/store/document-id.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

class PersistentHashMapKeyMapperTest : public testing::Test {
 protected:
  void SetUp() override { base_dir_ = GetTestTempDir() + "/key_mapper"; }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(base_dir_.c_str());
  }

  std::string base_dir_;
  Filesystem filesystem_;
};

TEST_F(PersistentHashMapKeyMapperTest, InvalidBaseDir) {
  EXPECT_THAT(PersistentHashMapKeyMapper<DocumentId>::Create(
                  filesystem_, "/dev/null", /*pre_mapping_fbv=*/false),
              StatusIs(libtextclassifier3::StatusCode::INTERNAL));
}

}  // namespace

}  // namespace lib
}  // namespace icing
