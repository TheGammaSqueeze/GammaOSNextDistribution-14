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

#include "icing/file/destructible-directory.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::Eq;

TEST(DestructibleDirectoryTest, DeletesDirectoryProperly) {
  Filesystem filesystem;
  std::string dir_path = GetTestTempDir() + "/dir1";
  std::string file_path = dir_path + "/file1";

  {
    // 1. Create a file in the directory.
    ASSERT_TRUE(filesystem.CreateDirectoryRecursively(dir_path.c_str()));
    ScopedFd sfd(filesystem.OpenForWrite(file_path.c_str()));
    ASSERT_TRUE(sfd.is_valid());
    int i = 127;
    ASSERT_TRUE(filesystem.Write(sfd.get(), &i, sizeof(i)));
  }

  {
    // 2. Open the directory with a DestructibleDirectory
    DestructibleDirectory destructible(&filesystem, dir_path);
    EXPECT_TRUE(destructible.is_valid());
    EXPECT_THAT(destructible.dir(), Eq(dir_path));
  }

  // 3. Ensure that the file and directory don't exist.
  EXPECT_FALSE(filesystem.FileExists(file_path.c_str()));
  EXPECT_FALSE(filesystem.DirectoryExists(dir_path.c_str()));
}

TEST(DestructibleDirectoryTest, MoveAssignDeletesDirectoryProperly) {
  Filesystem filesystem;
  std::string filepath1 = GetTestTempDir() + "/dir1";
  std::string filepath2 = GetTestTempDir() + "/dir2";

  // 1. Create dir1
  DestructibleDirectory destructible1(&filesystem, filepath1);
  ASSERT_TRUE(destructible1.is_valid());
  ASSERT_TRUE(filesystem.DirectoryExists(filepath1.c_str()));

  {
    // 2. Create dir2
    DestructibleDirectory destructible2(&filesystem, filepath2);
    ASSERT_TRUE(destructible2.is_valid());

    // Move assign destructible2 into destructible1
    destructible1 = std::move(destructible2);
  }

  // 3. dir1 shouldn't exist because it was destroyed when destructible1 was
  // move assigned to.
  EXPECT_FALSE(filesystem.DirectoryExists(filepath1.c_str()));

  // 4. dir2 should still exist because it moved into destructible1 from
  // destructible2.
  EXPECT_TRUE(filesystem.DirectoryExists(filepath2.c_str()));
}

TEST(DestructibleDirectoryTest, MoveConstructionDeletesDirectoryProperly) {
  Filesystem filesystem;
  std::string filepath1 = GetTestTempDir() + "/dir1";

  // 1. Create destructible1, it'll be reconstructed soon anyways.
  std::unique_ptr<DestructibleDirectory> destructible1;
  {
    // 2. Create file1
    DestructibleDirectory destructible2(&filesystem, filepath1);
    ASSERT_TRUE(destructible2.is_valid());

    // Move construct destructible1 from destructible2
    destructible1 =
        std::make_unique<DestructibleDirectory>(std::move(destructible2));
  }

  // 3. dir1 should still exist because it moved into destructible1 from
  // destructible2.
  EXPECT_TRUE(destructible1->is_valid());
  EXPECT_TRUE(filesystem.DirectoryExists(filepath1.c_str()));

  {
    // 4. Move construct destructible3 from destructible1
    DestructibleDirectory destructible3(std::move(*destructible1));
    EXPECT_TRUE(destructible3.is_valid());
  }

  // 5. dir1 shouldn't exist because it was destroyed when destructible3 was
  // destroyed.
  EXPECT_FALSE(filesystem.DirectoryExists(filepath1.c_str()));
}

}  // namespace

}  // namespace lib
}  // namespace icing
