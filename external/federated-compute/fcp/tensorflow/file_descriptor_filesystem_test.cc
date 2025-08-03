/*
 * Copyright 2023 Google LLC
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
 
#include "fcp/tensorflow/file_descriptor_filesystem.h"

#include <fcntl.h>

#include <memory>
#include <vector>

#include "absl/strings/str_cat.h"
#include "absl/strings/string_view.h"
#include "android-base/file.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "tensorflow/core/lib/core/status_test_util.h"

namespace tensorflow {
namespace fcp {
namespace {

using ::android::base::ReadFileToString;
using ::testing::TempDir;

static constexpr char kBadFdPath[] = "fd:///10000";
static constexpr char kFileContent[] = "abcdefgh";
static constexpr int kFileContentLen = 8;

class FileDescriptorFileSystemTest : public ::testing::Test {
 protected:
  void TearDown() override {
    if (fd_ != -1) {
      ASSERT_NE(-1, close(fd_));
    }
  }

  // Writes contents to a temp file and sets fd_path_ to point to it. The opened
  // file descriptor is closed automatically in TearDown(). To be called at most
  // once per test.
  void CreateAndOpenFdForTest(std::string contents) {
    ASSERT_EQ(-1, fd_);  // prevent accidental double-open
    file_name_ = TempDir() + "/fdtest";
    android::base::WriteStringToFile(contents, file_name_);

    fd_ = open(file_name_.c_str(), O_RDONLY);
    ASSERT_NE(-1, fd_);

    fd_path_ = absl::StrCat("fd:///", fd_);
  }

  void CreateTempFdForTest() {
    ASSERT_EQ(-1, fd_);  // prevent accidental double-open

    file_name_ = TempDir() + "/fdtest";
    android::base::WriteStringToFile("", file_name_);

    fd_ = open(file_name_.c_str(), O_WRONLY);
    ASSERT_NE(-1, fd_);

    fd_path_ = absl::StrCat("fd:///", fd_);
  }

  FileDescriptorFileSystem fd_fs_;
  string fd_path_;
  string file_name_;

 private:
  int fd_ = -1;
};

TEST_F(FileDescriptorFileSystemTest, WritableFile) {
  CreateTempFdForTest();

  std::unique_ptr<WritableFile> file;
  TF_ASSERT_OK(fd_fs_.NewWritableFile(fd_path_, &file));
  TF_ASSERT_OK(file->Append(kFileContent));
  TF_ASSERT_OK(file->Close());

  std::string actual_content;
  ASSERT_TRUE(ReadFileToString(file_name_, &actual_content));
  EXPECT_EQ(kFileContent, actual_content);
}

TEST_F(FileDescriptorFileSystemTest, WritableFileFailsOnInvalidFd) {
  std::unique_ptr<WritableFile> file;
  EXPECT_FALSE(fd_fs_.NewWritableFile(kBadFdPath, &file).ok());
}

TEST_F(FileDescriptorFileSystemTest, MalformedPathReturnsInvalidArgument) {
  FileStatistics stats;
  EXPECT_EQ(fd_fs_.Stat("fd://0", &stats).code(), error::INVALID_ARGUMENT);
  EXPECT_EQ(fd_fs_.Stat("fd://authority/0", &stats).code(),
            error::INVALID_ARGUMENT);
  EXPECT_EQ(fd_fs_.Stat("fd:///not-a-number", &stats).code(),
            error::INVALID_ARGUMENT);
}

TEST_F(FileDescriptorFileSystemTest, NewRandomAccessFile) {
  CreateAndOpenFdForTest(kFileContent);

  std::unique_ptr<RandomAccessFile> file;
  TF_ASSERT_OK(fd_fs_.NewRandomAccessFile(fd_path_, &file));
  StringPiece content;
  char scratch[kFileContentLen];
  TF_ASSERT_OK(file->Read(0, kFileContentLen, &content, scratch));

  EXPECT_EQ(kFileContent, content);
}

TEST_F(FileDescriptorFileSystemTest,
       NewRandomAccessFileFailsOnRequestMoreBytes) {
  CreateAndOpenFdForTest(kFileContent);

  std::unique_ptr<RandomAccessFile> file;
  TF_ASSERT_OK(fd_fs_.NewRandomAccessFile(fd_path_, &file));
  StringPiece content;
  char scratch[kFileContentLen];
  auto status = file->Read(0, kFileContentLen + 2, &content, scratch);
  EXPECT_EQ(status.code(), error::OUT_OF_RANGE);
  EXPECT_EQ(status.error_message(),
            "Read fewer bytes than requested. Total read bytes 8");
}

TEST_F(FileDescriptorFileSystemTest, NewRandomAccessFileFailsOnInvalidFd) {
  std::unique_ptr<RandomAccessFile> file;
  EXPECT_FALSE(fd_fs_.NewRandomAccessFile(kBadFdPath, &file).ok());
}

TEST_F(FileDescriptorFileSystemTest, NewRandomAccessFileFailsOnDirectoryFd) {
  int dir_fd = open(TempDir().c_str(), O_RDONLY | O_DIRECTORY);
  ASSERT_NE(-1, dir_fd);
  string dir_fd_path = absl::StrCat("fd:///", dir_fd);

  std::unique_ptr<RandomAccessFile> file;
  EXPECT_FALSE(fd_fs_.NewRandomAccessFile(dir_fd_path, &file).ok());

  close(dir_fd);
}

TEST_F(FileDescriptorFileSystemTest, GetMatchingPaths) {
  CreateAndOpenFdForTest(kFileContent);

  std::vector<string> paths;
  TF_EXPECT_OK(fd_fs_.GetMatchingPaths(fd_path_, &paths));

  ASSERT_EQ(1, paths.size());
  EXPECT_EQ(fd_path_, paths.at(0));
}

TEST_F(FileDescriptorFileSystemTest, GetMatchingPathsReturnsEmptyOnBadFd) {
  std::vector<string> paths;
  TF_EXPECT_OK(fd_fs_.GetMatchingPaths(kBadFdPath, &paths));
  EXPECT_TRUE(paths.empty());
}

TEST_F(FileDescriptorFileSystemTest, Stat) {
  CreateAndOpenFdForTest(kFileContent);

  FileStatistics stats;
  TF_EXPECT_OK(fd_fs_.Stat(fd_path_, &stats));

  EXPECT_EQ(kFileContentLen, stats.length);
  EXPECT_GT(stats.mtime_nsec, 0);
  EXPECT_FALSE(stats.is_directory);
}

TEST_F(FileDescriptorFileSystemTest, StatFailsOnBadFd) {
  FileStatistics stats;
  EXPECT_FALSE(fd_fs_.Stat(kBadFdPath, &stats).ok());
}

TEST_F(FileDescriptorFileSystemTest, GetFileSize) {
  CreateAndOpenFdForTest(kFileContent);

  uint64 size;
  TF_EXPECT_OK(fd_fs_.GetFileSize(fd_path_, &size));

  EXPECT_EQ(kFileContentLen, size);
}

TEST_F(FileDescriptorFileSystemTest, GetFileSizeFailsOnBadFd) {
  uint64 size;
  EXPECT_FALSE(fd_fs_.GetFileSize(kBadFdPath, &size).ok());
}

TEST_F(FileDescriptorFileSystemTest,
       NewReadOnlyMemoryRegionFromFileReturnsUnimplemented) {
  std::unique_ptr<ReadOnlyMemoryRegion> region;
  EXPECT_EQ(fd_fs_.NewReadOnlyMemoryRegionFromFile(kBadFdPath, &region).code(),
            error::UNIMPLEMENTED);
}

TEST_F(FileDescriptorFileSystemTest, FileExistsReturnsUnimplemented) {
  EXPECT_EQ(fd_fs_.FileExists(kBadFdPath).code(), error::UNIMPLEMENTED);
}

TEST_F(FileDescriptorFileSystemTest, GetChildrenReturnsUnimplemented) {
  std::vector<string> paths;
  EXPECT_EQ(fd_fs_.GetChildren(kBadFdPath, &paths).code(),
            error::UNIMPLEMENTED);
}

}  // anonymous namespace
}  // namespace fcp
}  // namespace tensorflow
