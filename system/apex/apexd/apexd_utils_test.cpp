/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "apexd_utils.h"

#include <android-base/file.h>
#include <android-base/result-gmock.h>
#include <android-base/result.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <errno.h>
#include <gtest/gtest.h>

#include <filesystem>
#include <fstream>
#include <new>
#include <string>

#include "apexd.h"
#include "apexd_test_utils.h"

namespace android {
namespace apex {
namespace {

namespace fs = std::filesystem;

using android::base::Basename;
using android::base::Join;
using android::base::StringPrintf;
using android::base::testing::Ok;
using ::testing::Not;
using ::testing::UnorderedElementsAre;
using ::testing::UnorderedElementsAreArray;

// TODO(b/170327382): add unit tests for apexd_utils.h

TEST(ApexdUtilTest, DeleteDirContent) {
  TemporaryDir root_dir;
  TemporaryFile child_file_1(root_dir.path);
  TemporaryFile child_file_2(root_dir.path);
  std::string child_dir = StringPrintf("%s/child-dir", root_dir.path);
  CreateDirIfNeeded(child_dir, 0755);
  TemporaryFile nested_file(child_dir);

  auto content = ReadDir(root_dir.path, [](auto _) { return true; });
  ASSERT_RESULT_OK(content);
  ASSERT_EQ(content->size(), 3u);

  auto del_result = DeleteDirContent(root_dir.path);
  ASSERT_RESULT_OK(del_result);
  content = ReadDir(root_dir.path, [](auto _) { return true; });
  ASSERT_RESULT_OK(content);
  ASSERT_EQ(content->size(), 0u);
}

TEST(ApexdUtilTest, FindFirstExistingDirectoryBothExist) {
  TemporaryDir first_dir;
  TemporaryDir second_dir;
  auto result = FindFirstExistingDirectory(first_dir.path, second_dir.path);
  ASSERT_RESULT_OK(result);
  ASSERT_EQ(*result, first_dir.path);
}

TEST(ApexdUtilTest, FindFirstExistingDirectoryOnlyFirstExist) {
  TemporaryDir first_dir;
  auto second_dir = "/data/local/tmp/does/not/exist";
  auto result = FindFirstExistingDirectory(first_dir.path, second_dir);
  ASSERT_RESULT_OK(result);
  ASSERT_EQ(*result, first_dir.path);
}

TEST(ApexdUtilTest, FindFirstExistingDirectoryOnlySecondExist) {
  auto first_dir = "/data/local/tmp/does/not/exist";
  TemporaryDir second_dir;
  auto result = FindFirstExistingDirectory(first_dir, second_dir.path);
  ASSERT_RESULT_OK(result);
  ASSERT_EQ(*result, second_dir.path);
}

TEST(ApexdUtilTest, FindFirstExistingDirectoryNoneExist) {
  auto first_dir = "/data/local/tmp/does/not/exist";
  auto second_dir = "/data/local/tmp/also/does/not/exist";
  auto result = FindFirstExistingDirectory(first_dir, second_dir);
  ASSERT_THAT(result, Not(Ok()));
}

TEST(ApexdUtilTest, FindFirstExistingDirectoryFirstFileSecondDir) {
  TemporaryFile first_file;
  TemporaryDir second_dir;
  auto result = FindFirstExistingDirectory(first_file.path, second_dir.path);
  ASSERT_RESULT_OK(result);
  ASSERT_EQ(*result, second_dir.path);
}

TEST(ApexdUtilTest, FindFirstExistingDirectoryFirstDirSecondFile) {
  TemporaryDir first_dir;
  TemporaryFile second_file;
  auto result = FindFirstExistingDirectory(first_dir.path, second_file.path);
  ASSERT_RESULT_OK(result);
  ASSERT_EQ(*result, first_dir.path);
}

TEST(ApexdUtilTest, FindFirstExistingDirectoryBothFiles) {
  TemporaryFile first_file;
  TemporaryFile second_file;
  auto result = FindFirstExistingDirectory(first_file.path, second_file.path);
  ASSERT_THAT(result, Not(Ok()));
}

TEST(ApexdUtilTest, FindFirstExistingDirectoryFirstFileSecondDoesNotExist) {
  TemporaryFile first_file;
  auto second_dir = "/data/local/tmp/does/not/exist";
  auto result = FindFirstExistingDirectory(first_file.path, second_dir);
  ASSERT_THAT(result, Not(Ok()));
}

TEST(ApexdUtilTest, FindFirstExistingDirectoryFirsDoesNotExistSecondFile) {
  auto first_dir = "/data/local/tmp/does/not/exist";
  TemporaryFile second_file;
  auto result = FindFirstExistingDirectory(first_dir, second_file.path);
  ASSERT_THAT(result, Not(Ok()));
}

TEST(ApexdUtilTest, MoveDir) {
  TemporaryDir from;
  TemporaryDir to;

  TemporaryFile from_1(from.path);
  auto from_subdir = StringPrintf("%s/subdir", from.path);
  if (mkdir(from_subdir.c_str(), 07000) != 0) {
    FAIL() << "Failed to mkdir " << from_subdir << " : " << strerror(errno);
  }
  TemporaryFile from_2(from_subdir);

  auto result = MoveDir(from.path, to.path);
  ASSERT_RESULT_OK(result);
  ASSERT_TRUE(fs::is_empty(from.path));

  std::vector<std::string> content;
  for (const auto& it : fs::recursive_directory_iterator(to.path)) {
    content.push_back(it.path());
  }

  static const std::vector<std::string> expected = {
      StringPrintf("%s/%s", to.path, Basename(from_1.path).c_str()),
      StringPrintf("%s/subdir", to.path),
      StringPrintf("%s/subdir/%s", to.path, Basename(from_2.path).c_str()),
  };
  ASSERT_THAT(content, UnorderedElementsAreArray(expected));
}

TEST(ApexdUtilTest, MoveDirFromIsNotDirectory) {
  TemporaryFile from;
  TemporaryDir to;
  ASSERT_THAT(MoveDir(from.path, to.path), Not(Ok()));
}

TEST(ApexdUtilTest, MoveDirToIsNotDirectory) {
  TemporaryDir from;
  TemporaryFile to;
  TemporaryFile from_1(from.path);
  ASSERT_THAT(MoveDir(from.path, to.path), Not(Ok()));
}

TEST(ApexdUtilTest, MoveDirFromDoesNotExist) {
  TemporaryDir to;
  ASSERT_THAT(MoveDir("/data/local/tmp/does/not/exist", to.path), Not(Ok()));
}

TEST(ApexdUtilTest, MoveDirToDoesNotExist) {
  namespace fs = std::filesystem;

  TemporaryDir from;
  TemporaryFile from_1(from.path);
  auto from_subdir = StringPrintf("%s/subdir", from.path);
  if (mkdir(from_subdir.c_str(), 07000) != 0) {
    FAIL() << "Failed to mkdir " << from_subdir << " : " << strerror(errno);
  }
  TemporaryFile from_2(from_subdir);

  ASSERT_THAT(MoveDir(from.path, "/data/local/tmp/does/not/exist"), Not(Ok()));

  // Check that |from| directory is not empty.
  std::vector<std::string> content;
  for (const auto& it : fs::recursive_directory_iterator(from.path)) {
    content.push_back(it.path());
  }

  ASSERT_THAT(content,
              UnorderedElementsAre(from_1.path, from_subdir, from_2.path));
}

TEST(ApexdUtilTest, FindFilesBySuffix) {
  TemporaryDir td;

  // create files with different suffix
  const std::string first_filename = StringPrintf("%s/first.a", td.path);
  const std::string second_filename = StringPrintf("%s/second.b", td.path);
  const std::string third_filename = StringPrintf("%s/third.c", td.path);
  const std::string fourth_filename = StringPrintf("%s/fourth.c", td.path);

  std::ofstream first_file(first_filename);
  std::ofstream second_file(second_filename);
  std::ofstream third_file(third_filename);
  std::ofstream fourth_file(fourth_filename);

  auto result = FindFilesBySuffix(td.path, {".b", ".c"});
  ASSERT_RESULT_OK(result);
  ASSERT_THAT(*result, UnorderedElementsAre(second_filename, third_filename,
                                            fourth_filename));
}

TEST(ApexdTestUtilsTest, MountNamespaceRestorer) {
  auto original_namespace = GetCurrentMountNamespace();
  ASSERT_RESULT_OK(original_namespace);
  {
    MountNamespaceRestorer restorer;
    // Switch to new mount namespace.
    ASSERT_NE(-1, unshare(CLONE_NEWNS));
    auto current_namespace = GetCurrentMountNamespace();
    ASSERT_RESULT_OK(current_namespace);
    ASSERT_NE(original_namespace, current_namespace);
  }
  // Check that we switched back to the original namespace upon exiting the
  // scope.
  auto current_namespace = GetCurrentMountNamespace();
  ASSERT_RESULT_OK(current_namespace);
  ASSERT_EQ(*original_namespace, *current_namespace);
}

TEST(ApexdTestUtilsTest, SetUpApexTestEnvironment) {
  auto original_apex_mounts = GetApexMounts();
  ASSERT_GT(original_apex_mounts.size(), 0u);
  auto original_dir_content = ReadDir("/apex", [](auto _) { return true; });
  ASSERT_RESULT_OK(original_dir_content);
  {
    MountNamespaceRestorer restorer;
    ASSERT_RESULT_OK(SetUpApexTestEnvironment());
    // Check /apex is apex_mnt_dir.
    char* context;
    ASSERT_GT(getfilecon("/apex", &context), 0);
    EXPECT_EQ(std::string(context), "u:object_r:apex_mnt_dir:s0");
    freecon(context);
    // Check no apexes are mounted in our test environment.
    auto new_apex_mounts = GetApexMounts();
    ASSERT_EQ(new_apex_mounts.size(), 0u);
    // Check that /apex is empty.
    auto dir_content = ReadDir("/apex", [](auto _) { return true; });
    ASSERT_RESULT_OK(dir_content);
    ASSERT_EQ(dir_content->size(), 0u)
        << "Found following entries: " << Join(*dir_content, ',');
    // Check that we can still access /data.
    std::string test_dir = android::base::GetExecutableDirectory();
    ASSERT_TRUE(android::base::StartsWith(test_dir, "/data"));
    TemporaryFile tf(test_dir);
    // Check that we can write.
    ASSERT_TRUE(android::base::WriteStringToFile("secret", tf.path));
    // And check that we can still read it
    std::string content;
    ASSERT_TRUE(android::base::ReadFileToString(tf.path, &content));
    ASSERT_EQ(content, "secret");
  }
  auto apex_mounts = GetApexMounts();
  ASSERT_THAT(apex_mounts, UnorderedElementsAreArray(original_apex_mounts));
  auto apex_dir_content = ReadDir("/apex", [](auto _) { return true; });
  ASSERT_RESULT_OK(apex_dir_content);
  ASSERT_EQ(apex_dir_content->size(), original_dir_content->size());
}

}  // namespace
}  // namespace apex
}  // namespace android
