// Copyright (C) 2021 Google LLC
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

#include "icing/file/destructible-file.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

TEST(DestructibleFileTest, DeletesFileProperly) {
  Filesystem filesystem;
  std::string filepath1 = GetTestTempDir() + "/file1";

  {
    // 1. Create the file
    ScopedFd sfd(filesystem.OpenForWrite(filepath1.c_str()));
    ASSERT_TRUE(sfd.is_valid());
    int i = 127;
    ASSERT_TRUE(filesystem.Write(sfd.get(), &i, sizeof(i)));
  }

  {
    // 2. Open with a Destructible file.
    DestructibleFile destructible(filepath1, &filesystem);
    ASSERT_TRUE(destructible.is_valid());
  }

  // 3. Ensure that the file doesn't exist.
  EXPECT_FALSE(filesystem.FileExists(filepath1.c_str()));
}

TEST(DestructibleFileTest, MoveAssignDeletesFileProperly) {
  Filesystem filesystem;
  std::string filepath1 = GetTestTempDir() + "/file1";
  std::string filepath2 = GetTestTempDir() + "/file2";

  // 1. Create file1
  DestructibleFile destructible1(filepath1, &filesystem);
  ASSERT_TRUE(destructible1.is_valid());
  int i = 127;
  ASSERT_TRUE(filesystem.Write(destructible1.get_fd(), &i, sizeof(i)));

  {
    // 2. Create file2
    DestructibleFile destructible2(filepath2, &filesystem);
    ASSERT_TRUE(destructible2.is_valid());
    i = 458;
    ASSERT_TRUE(filesystem.Write(destructible2.get_fd(), &i, sizeof(i)));

    // Move assign destructible2 into destructible1
    destructible1 = std::move(destructible2);
  }

  // 3. file1 shouldn't exist because it was destroyed when destructible1 was
  // move assigned to.
  EXPECT_FALSE(filesystem.FileExists(filepath1.c_str()));

  // 4. file2 should still exist because it moved into destructible1 from
  // destructible2.
  EXPECT_TRUE(filesystem.FileExists(filepath2.c_str()));
}

TEST(DestructibleFileTest, MoveConstructionDeletesFileProperly) {
  Filesystem filesystem;
  std::string filepath1 = GetTestTempDir() + "/file1";

  // 1. Create destructible1, it'll be reconstructed soon anyways.
  std::unique_ptr<DestructibleFile> destructible1;
  {
    // 2. Create file1
    DestructibleFile destructible2(filepath1, &filesystem);
    ASSERT_TRUE(destructible2.is_valid());
    int i = 458;
    ASSERT_TRUE(filesystem.Write(destructible2.get_fd(), &i, sizeof(i)));

    // Move construct destructible1 from destructible2
    destructible1 =
        std::make_unique<DestructibleFile>(std::move(destructible2));
  }

  // 3. file1 should still exist because it moved into destructible1 from
  // destructible2.
  ASSERT_TRUE(destructible1->is_valid());
  EXPECT_TRUE(filesystem.FileExists(filepath1.c_str()));

  {
    // 4. Move construct destructible3 from destructible1
    DestructibleFile destructible3(std::move(*destructible1));
    ASSERT_TRUE(destructible3.is_valid());
  }

  // 5. file1 shouldn't exist because it was destroyed when destructible3 was
  // destroyed.
  EXPECT_FALSE(filesystem.FileExists(filepath1.c_str()));
}

}  // namespace

}  // namespace lib
}  // namespace icing
