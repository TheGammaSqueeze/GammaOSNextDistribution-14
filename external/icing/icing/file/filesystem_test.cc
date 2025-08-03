// Copyright (C) 2019 Google LLC
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

// Test for Filesystem class and utils.

#include "icing/file/filesystem.h"

#include <algorithm>
#include <cstdint>
#include <string>
#include <unordered_set>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/testing/tmp-directory.h"

#if defined(__APPLE__)
#include <TargetConditionals.h>
#endif

using std::sort;
using std::vector;
using ::testing::Eq;
using ::testing::Ge;
using ::testing::Gt;
using ::testing::Le;
using ::testing::Ne;
using ::testing::UnorderedElementsAre;
using ::testing::UnorderedElementsAreArray;

namespace icing {
namespace lib {

namespace {
// Create some test files in the specified directory.  "test data" plus the
// relative path name + "\n" is written to each.
void CreateTestFiles(const vector<std::string>& file_names,
                     const std::string& append_dir) {
  Filesystem filesystem;
  for (const std::string& one_file_name : file_names) {
    // Write the filename to the file
    std::string one_file_path = append_dir + "/" + one_file_name;
    int fd = filesystem.OpenForWrite(one_file_path.c_str());
    ASSERT_THAT(fd, Gt(0));
    std::string test_data = "test data " + one_file_name + "\n";
    EXPECT_TRUE(
        filesystem.Write(fd, test_data.c_str(), strlen(test_data.c_str())));
    EXPECT_THAT(close(fd), Eq(0));
  }
}
}  // namespace

// Indicates if the file system supports Sparse Files.
// 'Sparse files' are essentially pre-allocated files of big sizes which do not
// yet use any blocks. A few tests validate that disk-usage is accounted
// correctly in those cases as zero.
// However, on HFS+ file system sparse files are not supported.
// The new AFS supports sparse files, but as of 2017-09 all simulators in prod
// are running on MacOS using HFS+.
bool FileSystemSupportsSparseFiles() {
#ifdef TARGET_IPHONE_SIMULATOR
  return false;
#else
  return true;
#endif
}

class FilesystemTest : public testing::Test {
 protected:
  void SetUp() override {
    temp_dir_ = GetTestTempDir() + "/icing_filesystem";
    Filesystem filesystem;
    ASSERT_TRUE(filesystem.CreateDirectoryRecursively(temp_dir_.c_str()));
  }

  void TearDown() override {
    Filesystem filesystem;
    EXPECT_TRUE(filesystem.DeleteDirectoryRecursively(temp_dir_.c_str()));
  }

  // Write junk data of given size to the given file descriptor
  void WriteJunk(int fd, size_t size) {
    const int kBufLen = 1024;
    int buf[kBufLen];
    for (int i = 0; i < kBufLen; ++i) {
      buf[i] = i;
    }
    const int kBufSize = kBufLen * sizeof(int);

    Filesystem filesystem;
    for (size_t i = 0; i < size / kBufSize; ++i) {
      EXPECT_TRUE(filesystem.Write(fd, buf, kBufSize));
    }
    if (size % kBufSize) {
      EXPECT_TRUE(filesystem.Write(fd, buf, size % kBufSize));
    }
  }

  std::string temp_dir_;
};

TEST_F(FilesystemTest, Names) {
  const std::string filename("/foo/bar/README.txt");
  Filesystem filesystem;

  std::string basename = filesystem.GetBasename(filename.c_str());
  EXPECT_THAT(basename, Eq("README.txt"));

  std::string dirname = filesystem.GetDirname(filename.c_str());
  EXPECT_THAT(dirname, Eq("/foo/bar"));

  basename = filesystem.GetBasename(dirname.c_str());
  EXPECT_THAT(basename, Eq("bar"));

  dirname = filesystem.GetDirname(dirname.c_str());
  EXPECT_THAT(dirname, Eq("/foo"));

  basename = filesystem.GetBasename(dirname.c_str());
  EXPECT_THAT(basename, Eq("foo"));

  dirname = filesystem.GetDirname(dirname.c_str());
  EXPECT_THAT(dirname, Eq(""));
}

TEST_F(FilesystemTest, OneLetter) {
  Filesystem filesystem;

  const std::string basename = filesystem.GetDirname("a");
  EXPECT_THAT(basename, Eq(""));

  const std::string dirname = filesystem.GetDirname("a");
  EXPECT_THAT(dirname, Eq(""));
}

TEST_F(FilesystemTest, Directory) {
  Filesystem filesystem;

  const std::string foo_str = temp_dir_ + "/foo";
  const std::string bar_str = foo_str + "/bar";
  const char* foo_dir = foo_str.c_str();
  const char* bar_dir = bar_str.c_str();

  EXPECT_TRUE(filesystem.CreateDirectory(foo_dir));
  EXPECT_TRUE(filesystem.DirectoryExists(foo_dir));
  EXPECT_TRUE(filesystem.DeleteDirectory(foo_dir));
  EXPECT_FALSE(filesystem.DirectoryExists(foo_dir));

  EXPECT_FALSE(filesystem.CreateDirectory(bar_dir));
  EXPECT_FALSE(filesystem.DirectoryExists(foo_dir));
  EXPECT_FALSE(filesystem.DirectoryExists(bar_dir));
  EXPECT_TRUE(filesystem.CreateDirectoryRecursively(bar_dir));
  EXPECT_TRUE(filesystem.DirectoryExists(foo_dir));
  EXPECT_TRUE(filesystem.DirectoryExists(bar_dir));

  EXPECT_FALSE(filesystem.DeleteDirectory(foo_dir));
  EXPECT_TRUE(filesystem.DeleteDirectoryRecursively(foo_dir));
  EXPECT_FALSE(filesystem.DirectoryExists(foo_dir));
  EXPECT_FALSE(filesystem.DirectoryExists(bar_dir));

  // Deleting a non-existing directory returns true.
  EXPECT_TRUE(filesystem.DeleteDirectory(foo_dir));
  EXPECT_TRUE(filesystem.DeleteDirectoryRecursively(foo_dir));
}

TEST_F(FilesystemTest, FSync) {
  Filesystem filesystem;
  const std::string foo_file = temp_dir_ + "/foo_file";
  int fd = filesystem.OpenForWrite(foo_file.c_str());
  ASSERT_THAT(fd, Ne(-1));
  EXPECT_TRUE(filesystem.DataSync(fd));
  close(fd);
}

TEST_F(FilesystemTest, Truncate) {
  Filesystem filesystem;
  const std::string foo_file = temp_dir_ + "/foo_file";
  const char* filename = foo_file.c_str();
  int fd = filesystem.OpenForWrite(filename);
  ASSERT_THAT(fd, Ne(-1));
  char data[10000] = {0};  // Zero-init to satisfy msan.
  EXPECT_TRUE(filesystem.Write(fd, data, sizeof(data)));
  close(fd);
  EXPECT_THAT(filesystem.GetFileSize(filename), Eq(sizeof(data)));
  EXPECT_TRUE(filesystem.Truncate(filename, sizeof(data) / 2));
  EXPECT_THAT(filesystem.GetFileSize(filename), Eq(sizeof(data) / 2));
  EXPECT_TRUE(filesystem.Truncate(filename, 0));
  EXPECT_THAT(filesystem.GetFileSize(filename), Eq(0u));
}

TEST_F(FilesystemTest, GetMatchingFiles) {
  Filesystem filesystem;
  const std::string foo_dir = temp_dir_ + "/foo";
  const std::string glob = foo_dir + "/p_*_q";
  vector<std::string> matches;

  // Non existing directory
  EXPECT_TRUE(filesystem.GetMatchingFiles(glob.c_str(), &matches));
  EXPECT_THAT(matches.size(), Eq(0u));

  // Existing directory
  matches.clear();
  ASSERT_TRUE(filesystem.CreateDirectoryRecursively(foo_dir.c_str()));
  EXPECT_TRUE(filesystem.GetMatchingFiles(glob.c_str(), &matches));
  EXPECT_THAT(matches.size(), Eq(0u));

  // With some files
  matches.clear();
  const int files_size = 4;
  const char* files[files_size] = {"p_1_q", "p_2_q", "p_3", "4_q"};
  for (size_t i = 0; i < files_size; ++i) {
    ScopedFd file(filesystem.OpenForWrite((foo_dir + "/" + files[i]).c_str()));
  }
  const int good_size = 2;
  const std::string good[good_size] = {foo_dir + "/p_1_q", foo_dir + "/p_2_q"};
  vector<std::string> expected(good, good + good_size);
  EXPECT_TRUE(filesystem.GetMatchingFiles(glob.c_str(), &matches));
  sort(matches.begin(), matches.end());
  EXPECT_THAT(matches, Eq(expected));
}

TEST_F(FilesystemTest, IncrementByOrSetInvalid) {
  int64_t to_increment = 1;
  Filesystem::IncrementByOrSetInvalid(2, &to_increment);
  EXPECT_THAT(to_increment, Eq(3));

  Filesystem::IncrementByOrSetInvalid(Filesystem::kBadFileSize, &to_increment);
  EXPECT_THAT(to_increment, Eq(Filesystem::kBadFileSize));

  to_increment = Filesystem::kBadFileSize;
  Filesystem::IncrementByOrSetInvalid(2, &to_increment);
  EXPECT_THAT(to_increment, Eq(Filesystem::kBadFileSize));
}

TEST_F(FilesystemTest, GetDiskUsage) {
  Filesystem filesystem;
  const std::string foo_dir = temp_dir_ + "/foo";

  const int64_t kCluster = 4096;  // at least the anticipated fs cluster

  ASSERT_TRUE(filesystem.CreateDirectoryRecursively(foo_dir.c_str()));

  // Grow a sparse file, and then append to it.
  const std::string filename = foo_dir + "/myfile";
  // Size to expand the sparse file to.
  const int64_t kExpandedSize = 100 * kCluster - 5;
  // Actual data to write to the file.
  const int64_t kJunkSize = 5 * kCluster - 10;

  EXPECT_TRUE(filesystem.Truncate(filename.c_str(), kExpandedSize));
  ScopedFd fd(filesystem.OpenForWrite(filename.c_str()));
  WriteJunk(*fd, kJunkSize);

  int64_t size = filesystem.GetDiskUsage(*fd);
  EXPECT_THAT(size, Ge(kJunkSize));
  if (FileSystemSupportsSparseFiles()) {
    EXPECT_THAT(size, Le(kExpandedSize));
  }
}

TEST_F(FilesystemTest, GetDiskUsagePath) {
  Filesystem filesystem;
  const std::string foo_dir = temp_dir_ + "/foo";

  const int64_t kCluster = 4096;  // at least the anticipated fs cluster

  // Non-existing
  {
    EXPECT_THAT(filesystem.GetDiskUsage(foo_dir.c_str()),
                Eq(Filesystem::kBadFileSize));
  }

  // A single directory
  {
    ASSERT_TRUE(filesystem.CreateDirectoryRecursively(foo_dir.c_str()));
    int64_t size = filesystem.GetDiskUsage(foo_dir.c_str());
    EXPECT_THAT(size, Ge(0 * kCluster));
    EXPECT_THAT(size, Le(1 * kCluster));
  }

  // Nested directories
  const std::string bar_dir = foo_dir + "/bar";
  {
    ASSERT_TRUE(filesystem.CreateDirectoryRecursively(bar_dir.c_str()));
    int64_t size = filesystem.GetDiskUsage(bar_dir.c_str());
    EXPECT_THAT(size, Ge(0 * kCluster));
    EXPECT_THAT(size, Le(2 * kCluster));
  }

  // Two regular files
  const std::string reg1 = bar_dir + "/f1";
  const std::string reg2 = bar_dir + "/f2";
  {
    {
      ScopedFd f1(filesystem.OpenForWrite(reg1.c_str()));
      ScopedFd f2(filesystem.OpenForWrite(reg2.c_str()));
      WriteJunk(*f1, 5 * kCluster - 10);
      WriteJunk(*f2, 8 * kCluster - 10);
    }
    int64_t size = filesystem.GetDiskUsage(foo_dir.c_str());
    EXPECT_THAT(size, Ge(13 * kCluster));
    EXPECT_THAT(size, Le(15 * kCluster));
  }

  // Two sparse files
  const std::string sparse1 = foo_dir + "/s1";
  const std::string sparse2 = foo_dir + "/s2";
  {
    EXPECT_TRUE(filesystem.Truncate(sparse1.c_str(), 100 * kCluster - 5));
    EXPECT_TRUE(filesystem.Truncate(sparse2.c_str(), 200 * kCluster - 123));
    int64_t size = filesystem.GetDiskUsage(foo_dir.c_str());
    EXPECT_THAT(size, Ge(13 * kCluster));
    if (FileSystemSupportsSparseFiles()) {
      EXPECT_THAT(size, Le(17 * kCluster));
    } else {
      EXPECT_THAT(size, Le(313 * kCluster));
    }
  }

  // Some junk in the sparse files
  {
    {
      ScopedFd f1(filesystem.OpenForWrite(sparse1.c_str()));
      ScopedFd f2(filesystem.OpenForWrite(sparse2.c_str()));
      WriteJunk(*f1, 5 * kCluster - 10);
      WriteJunk(*f2, 8 * kCluster - 10);
    }
    int64_t size = filesystem.GetDiskUsage(foo_dir.c_str());
    EXPECT_THAT(size, Ge(26 * kCluster));
    if (FileSystemSupportsSparseFiles()) {
      EXPECT_THAT(size, Le(30 * kCluster));
    } else {
      EXPECT_THAT(size, Le(313 * kCluster));
    }
  }
}

// TODO(b/112435354): Add test case for original (non-recursive) ListDirectory()

// Tests ListDirectory() with recursive dir search, with no exclusions
// (simple test).
TEST_F(FilesystemTest, ListDirectoryRecursiveSimple) {
  Filesystem filesystem;
  const std::string append_dir = temp_dir_ + "/append_test";
  const std::string dir1_name = "dir1";
  const std::string dir1_path = append_dir + "/" + dir1_name;
  vector<std::string> some_files = {"file1", "file2", dir1_name + "/file3"};

  // Make sure there is no pre-existing test-dir structure
  ASSERT_TRUE(filesystem.DeleteDirectoryRecursively(append_dir.c_str()));

  // Setup a test-dir structure
  ASSERT_TRUE(filesystem.CreateDirectoryRecursively(
      dir1_path.c_str()));  // deepest path for test
  CreateTestFiles(some_files, append_dir);

  // Call the ListDirectory API with recursive dir-search, no exclusions.
  vector<std::string> result;
  EXPECT_TRUE(filesystem.ListDirectory(append_dir.c_str(), /*exclude=*/{},
                                       /*recursive=*/true, &result));

  // Verify that all files are returned, and no extras.
  EXPECT_THAT(result, UnorderedElementsAre(some_files[0], some_files[1],
                                           dir1_name, some_files[2]));

  // Clean up
  ASSERT_TRUE(filesystem.DeleteDirectoryRecursively(append_dir.c_str()));
}

// Tests ListDirectory() with recursive dir search, with exclusions.
// This test is similar in structure to ListDirectory_recursive_simple, but with
// exclusions.
TEST_F(FilesystemTest, ListDirectoryRecursiveExclude) {
  Filesystem filesystem;
  const std::string append_dir = temp_dir_ + "/append_test";
  const std::string dir1_name = "dir1";
  const std::string dir1_path = append_dir + "/" + dir1_name;
  vector<std::string> some_files = {"file1", "file2", dir1_name + "/file3"};

  // Make sure there is no pre-existing test-dir structure
  ASSERT_TRUE(filesystem.DeleteDirectoryRecursively(append_dir.c_str()));

  // Setup a test-dir structure
  ASSERT_TRUE(filesystem.CreateDirectoryRecursively(
      dir1_path.c_str()));  // deepest path for test
  CreateTestFiles(some_files, append_dir);

  // Call the ListDirectory API with recursive dir-search, but exclude dir1.
  vector<std::string> result;
  std::unordered_set<std::string> exclude;
  bool success = filesystem.ListDirectory(append_dir.c_str(),
                                          /*exclude=*/{dir1_name.c_str()},
                                          /*recursive=*/true, &result);

  // Verify that all files are returned, and no extras.
  EXPECT_TRUE(success);
  EXPECT_THAT(result, UnorderedElementsAre(some_files[0], some_files[1]));

  // Clean up
  ASSERT_TRUE(filesystem.DeleteDirectoryRecursively(append_dir.c_str()));
}

TEST_F(FilesystemTest, ReadWrite) {
  Filesystem filesystem;
  const std::string foo_file = temp_dir_ + "/foo_file";
  int fd = filesystem.OpenForWrite(foo_file.c_str());
  const std::string data = "hello world";
  EXPECT_TRUE(filesystem.Write(fd, data.c_str(), strlen(data.c_str())));

  std::string hello;
  hello.resize(strlen("hello"));
  EXPECT_TRUE(filesystem.Read(foo_file.c_str(), &hello[0], strlen("hello")));
  EXPECT_THAT(hello, Eq("hello"));

  // Read starts from wherever file offset is at the moment.
  filesystem.SetPosition(fd, 0);
  hello.clear();
  hello.resize(strlen("hello"));
  EXPECT_TRUE(filesystem.Read(fd, &hello[0], strlen("hello")));
  EXPECT_THAT(hello, Eq("hello"));

  // Shouldn't need to move file offset anymore since file offset gets updated
  // after the read.
  std::string world;
  world.resize(strlen(" world"));
  EXPECT_TRUE(filesystem.Read(fd, &world[0], strlen(" world")));
  EXPECT_THAT(world, Eq(" world"));

  // PRead should not be dependent on the file offset
  world.clear();
  world.resize(strlen(" world"));
  EXPECT_TRUE(
      filesystem.PRead(fd, &world[0], strlen(" world"), strlen("hello")));
  EXPECT_THAT(world, Eq(" world"));

  hello.clear();
  hello.resize(strlen("hello"));
  EXPECT_TRUE(
      filesystem.PRead(foo_file.c_str(), &hello[0], strlen("hello"), 0));
  EXPECT_THAT(hello, Eq("hello"));
}

TEST_F(FilesystemTest, CopyDirectory) {
  Filesystem filesystem;

  // File structure:
  // <temp_dir>/
  //   src_dir/
  //     file1
  //     file2
  //     sub_dir/
  //       file3
  const std::string src_dir = temp_dir_ + "/src_dir";
  const std::string sub_dir = "sub_dir";
  const std::string sub_dir_path = src_dir + "/" + sub_dir;
  vector<std::string> some_files = {"file1", "file2", sub_dir + "/file3"};

  // Make sure there is no pre-existing test-dir structure
  ASSERT_TRUE(filesystem.DeleteDirectoryRecursively(src_dir.c_str()));

  // Setup a test-dir structure
  ASSERT_TRUE(filesystem.CreateDirectoryRecursively(
      sub_dir_path.c_str()));  // deepest path for test
  CreateTestFiles(some_files, src_dir);

  const std::string dst_dir = temp_dir_ + "/dst_dir";
  EXPECT_TRUE(filesystem.CopyDirectory(src_dir.c_str(), dst_dir.c_str(),
                                       /*recursive=*/true));

  vector<std::string> src_dir_files;
  EXPECT_TRUE(filesystem.ListDirectory(src_dir.c_str(), /*exclude=*/{},
                                       /*recursive=*/true, &src_dir_files));

  vector<std::string> dst_dir_files;
  EXPECT_TRUE(filesystem.ListDirectory(dst_dir.c_str(), /*exclude=*/{},
                                       /*recursive=*/true, &dst_dir_files));

  EXPECT_THAT(dst_dir_files, UnorderedElementsAreArray(src_dir_files));

  // Clean up
  ASSERT_TRUE(filesystem.DeleteDirectoryRecursively(src_dir.c_str()));
  ASSERT_TRUE(filesystem.DeleteDirectoryRecursively(dst_dir.c_str()));
}

}  // namespace lib
}  // namespace icing
