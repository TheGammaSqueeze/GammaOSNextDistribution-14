// Copyright 2019 The Chromium OS Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "brillo/files/safe_fd.h"

#include <fcntl.h>
#include <sys/stat.h>

#include <base/files/file_util.h>
#include <brillo/files/file_util_test.h>
#include <brillo/syslog_logging.h>
#include <gtest/gtest.h>

namespace brillo {

class SafeFDTest : public FileTest {};

TEST_F(SafeFDTest, SafeFD) {
  EXPECT_FALSE(SafeFD().is_valid());
}

TEST_F(SafeFDTest, SafeFD_Move) {
  SafeFD moved_root = std::move(root_);
  EXPECT_FALSE(root_.is_valid());
  ASSERT_TRUE(moved_root.is_valid());

  SafeFD moved_root2(std::move(moved_root));
  EXPECT_FALSE(moved_root.is_valid());
  ASSERT_TRUE(moved_root2.is_valid());
}

TEST_F(SafeFDTest, Root) {
  SafeFD::SafeFDResult result = SafeFD::Root();
  EXPECT_TRUE(result.first.is_valid());
  EXPECT_EQ(result.second, SafeFD::Error::kNoError);
}

TEST_F(SafeFDTest, reset) {
  root_.reset();
  EXPECT_FALSE(root_.is_valid());
}

TEST_F(SafeFDTest, UnsafeReset) {
  int fd =
      HANDLE_EINTR(open(temp_dir_path_.data(),
                        O_NONBLOCK | O_DIRECTORY | O_RDONLY | O_CLOEXEC, 0777));
  ASSERT_GE(fd, 0);

  {
    SafeFD safe_fd;
    safe_fd.UnsafeReset(fd);
    EXPECT_EQ(safe_fd.get(), fd);
  }

  // Verify the file descriptor is closed.
  int result = fcntl(fd, F_GETFD);
  int error = errno;
  EXPECT_EQ(result, -1);
  EXPECT_EQ(error, EBADF);
}

TEST_F(SafeFDTest, Write_Success) {
  std::string random_data = GetRandomSuffix();
  {
    SafeFD::SafeFDResult file = root_.MakeFile(file_path_);
    EXPECT_EQ(file.second, SafeFD::Error::kNoError);
    ASSERT_TRUE(file.first.is_valid());

    EXPECT_EQ(file.first.Write(random_data.data(), random_data.size()),
              SafeFD::Error::kNoError);
  }

  ExpectFileContains(random_data);
  ExpectPermissions(file_path_, SafeFD::kDefaultFilePermissions);
}

TEST_F(SafeFDTest, Write_NotInitialized) {
  SafeFD invalid;
  ASSERT_FALSE(invalid.is_valid());

  std::string random_data = GetRandomSuffix();
  EXPECT_EQ(invalid.Write(random_data.data(), random_data.size()),
            SafeFD::Error::kNotInitialized);
}

TEST_F(SafeFDTest, Write_VerifyTruncate) {
  std::string random_data = GetRandomSuffix();
  ASSERT_TRUE(WriteFile(random_data));

  {
    SafeFD::SafeFDResult file = root_.OpenExistingFile(file_path_);
    EXPECT_EQ(file.second, SafeFD::Error::kNoError);
    ASSERT_TRUE(file.first.is_valid());

    EXPECT_EQ(file.first.Write("", 0), SafeFD::Error::kNoError);
  }

  ExpectFileContains("");
}

TEST_F(SafeFDTest, Write_Failure) {
  std::string random_data = GetRandomSuffix();
  EXPECT_EQ(root_.Write("", 1), SafeFD::Error::kIOError);
}

TEST_F(SafeFDTest, ReadContents_Success) {
  std::string random_data = GetRandomSuffix();
  ASSERT_TRUE(WriteFile(random_data));

  SafeFD::SafeFDResult file = root_.OpenExistingFile(file_path_);
  EXPECT_EQ(file.second, SafeFD::Error::kNoError);
  ASSERT_TRUE(file.first.is_valid());

  auto result = file.first.ReadContents();
  EXPECT_EQ(result.second, SafeFD::Error::kNoError);
  ASSERT_EQ(random_data.size(), result.first.size());
  EXPECT_EQ(memcmp(random_data.data(), result.first.data(), random_data.size()),
            0);
}

TEST_F(SafeFDTest, ReadContents_ExceededMaximum) {
  std::string random_data = GetRandomSuffix();
  ASSERT_TRUE(WriteFile(random_data));

  SafeFD::SafeFDResult file = root_.OpenExistingFile(file_path_);
  EXPECT_EQ(file.second, SafeFD::Error::kNoError);
  ASSERT_TRUE(file.first.is_valid());

  ASSERT_LT(1, random_data.size());
  auto result = file.first.ReadContents(1);
  EXPECT_EQ(result.second, SafeFD::Error::kExceededMaximum);
}

TEST_F(SafeFDTest, ReadContents_NotInitialized) {
  SafeFD invalid;
  ASSERT_FALSE(invalid.is_valid());

  auto result = invalid.ReadContents();
  EXPECT_EQ(result.second, SafeFD::Error::kNotInitialized);
}

TEST_F(SafeFDTest, Read_Success) {
  std::string random_data = GetRandomSuffix();
  ASSERT_TRUE(WriteFile(random_data));

  SafeFD::SafeFDResult file = root_.OpenExistingFile(file_path_);
  EXPECT_EQ(file.second, SafeFD::Error::kNoError);
  ASSERT_TRUE(file.first.is_valid());

  std::vector<char> buffer(random_data.size(), '\0');
  ASSERT_EQ(file.first.Read(buffer.data(), buffer.size()),
            SafeFD::Error::kNoError);
  EXPECT_EQ(memcmp(random_data.data(), buffer.data(), random_data.size()), 0);
}

TEST_F(SafeFDTest, Read_NotInitialized) {
  SafeFD invalid;
  ASSERT_FALSE(invalid.is_valid());

  char to_read;
  EXPECT_EQ(invalid.Read(&to_read, 1), SafeFD::Error::kNotInitialized);
}

TEST_F(SafeFDTest, Read_IOError) {
  std::string random_data = GetRandomSuffix();
  ASSERT_TRUE(WriteFile(random_data));

  SafeFD::SafeFDResult file = root_.OpenExistingFile(file_path_);
  EXPECT_EQ(file.second, SafeFD::Error::kNoError);
  ASSERT_TRUE(file.first.is_valid());

  std::vector<char> buffer(random_data.size() * 2, '\0');
  ASSERT_EQ(file.first.Read(buffer.data(), buffer.size()),
            SafeFD::Error::kIOError);
}

TEST_F(SafeFDTest, OpenExistingFile_Success) {
  std::string data = GetRandomSuffix();
  ASSERT_TRUE(WriteFile(data));
  {
    SafeFD::SafeFDResult file = root_.OpenExistingFile(file_path_);
    EXPECT_EQ(file.second, SafeFD::Error::kNoError);
    ASSERT_TRUE(file.first.is_valid());
  }
  ExpectFileContains(data);
}

TEST_F(SafeFDTest, OpenExistingFile_NotInitialized) {
  SafeFD::SafeFDResult file = SafeFD().OpenExistingFile(file_path_);
  EXPECT_EQ(file.second, SafeFD::Error::kNotInitialized);
  ASSERT_FALSE(file.first.is_valid());
}

TEST_F(SafeFDTest, OpenExistingFile_DoesNotExist) {
  SafeFD::SafeFDResult file = root_.OpenExistingFile(file_path_);
  EXPECT_EQ(file.second, SafeFD::Error::kDoesNotExist);
  ASSERT_FALSE(file.first.is_valid());
}

TEST_F(SafeFDTest, OpenExistingFile_IOError) {
  ASSERT_TRUE(WriteFile(""));
  EXPECT_EQ(chmod(file_path_.value().c_str(), 0000), 0) << strerror(errno);

  SafeFD::SafeFDResult file = root_.OpenExistingFile(file_path_);
  EXPECT_EQ(file.second, SafeFD::Error::kIOError);
  ASSERT_FALSE(file.first.is_valid());
}

TEST_F(SafeFDTest, OpenExistingFile_SymlinkDetected) {
  ASSERT_TRUE(SetupSymlinks());
  ASSERT_TRUE(WriteFile(""));
  SafeFD::SafeFDResult file = root_.OpenExistingFile(symlink_file_path_);
  EXPECT_EQ(file.second, SafeFD::Error::kSymlinkDetected);
  ASSERT_FALSE(file.first.is_valid());
}

TEST_F(SafeFDTest, OpenExistingFile_WrongType) {
  ASSERT_TRUE(SetupSymlinks());
  ASSERT_TRUE(WriteFile(""));
  SafeFD::SafeFDResult file =
      root_.OpenExistingFile(symlink_dir_path_.Append(kFileName));
  EXPECT_EQ(file.second, SafeFD::Error::kWrongType);
  ASSERT_FALSE(file.first.is_valid());
}

TEST_F(SafeFDTest, OpenExistingDir_Success) {
  SafeFD::SafeFDResult dir = root_.OpenExistingDir(temp_dir_.GetPath());
  EXPECT_EQ(dir.second, SafeFD::Error::kNoError);
  ASSERT_TRUE(dir.first.is_valid());
}

TEST_F(SafeFDTest, OpenExistingDir_NotInitialized) {
  SafeFD::SafeFDResult dir = SafeFD().OpenExistingDir(temp_dir_.GetPath());
  EXPECT_EQ(dir.second, SafeFD::Error::kNotInitialized);
  ASSERT_FALSE(dir.first.is_valid());
}

TEST_F(SafeFDTest, OpenExistingDir_DoesNotExist) {
  SafeFD::SafeFDResult dir = root_.OpenExistingDir(sub_dir_path_);
  EXPECT_EQ(dir.second, SafeFD::Error::kDoesNotExist);
  ASSERT_FALSE(dir.first.is_valid());
}

TEST_F(SafeFDTest, OpenExistingDir_IOError) {
  ASSERT_TRUE(WriteFile(""));
  ASSERT_EQ(chmod(sub_dir_path_.value().c_str(), 0000), 0) << strerror(errno);

  SafeFD::SafeFDResult dir = root_.OpenExistingDir(sub_dir_path_);
  EXPECT_EQ(dir.second, SafeFD::Error::kIOError);
  ASSERT_FALSE(dir.first.is_valid());
}

TEST_F(SafeFDTest, OpenExistingDir_WrongType) {
  ASSERT_TRUE(SetupSymlinks());
  SafeFD::SafeFDResult dir = root_.OpenExistingDir(symlink_dir_path_);
  EXPECT_EQ(dir.second, SafeFD::Error::kWrongType);
  ASSERT_FALSE(dir.first.is_valid());
}

TEST_F(SafeFDTest, MakeFile_DoesNotExistSuccess) {
  {
    SafeFD::SafeFDResult file = root_.MakeFile(file_path_);
    EXPECT_EQ(file.second, SafeFD::Error::kNoError);
    ASSERT_TRUE(file.first.is_valid());
  }

  ExpectPermissions(file_path_, SafeFD::kDefaultFilePermissions);
}

TEST_F(SafeFDTest, MakeFile_LeadingSelfDirSuccess) {
  ASSERT_TRUE(SetupSubdir());

  SafeFD::Error err;
  SafeFD dir;
  std::tie(dir, err) = root_.OpenExistingDir(sub_dir_path_);
  ASSERT_EQ(err, SafeFD::Error::kNoError);

  {
    SafeFD file;
    std::tie(file, err) = dir.MakeFile(file_path_.BaseName());
    EXPECT_EQ(err, SafeFD::Error::kNoError);
    ASSERT_TRUE(file.is_valid());
  }

  ExpectPermissions(file_path_, SafeFD::kDefaultFilePermissions);
}

TEST_F(SafeFDTest, MakeFile_ExistsSuccess) {
  std::string data = GetRandomSuffix();
  ASSERT_TRUE(WriteFile(data));
  {
    SafeFD::SafeFDResult file = root_.MakeFile(file_path_);
    EXPECT_EQ(file.second, SafeFD::Error::kNoError);
    ASSERT_TRUE(file.first.is_valid());
  }
  ExpectPermissions(file_path_, SafeFD::kDefaultFilePermissions);
  ExpectFileContains(data);
}

TEST_F(SafeFDTest, MakeFile_IOError) {
  ASSERT_TRUE(SetupSubdir());
  ASSERT_EQ(mkfifo(file_path_.value().c_str(), 0), 0);
  SafeFD::SafeFDResult file = root_.MakeFile(file_path_);
  EXPECT_EQ(file.second, SafeFD::Error::kIOError);
  ASSERT_FALSE(file.first.is_valid());
}

TEST_F(SafeFDTest, MakeFile_SymlinkDetected) {
  ASSERT_TRUE(SetupSymlinks());
  SafeFD::SafeFDResult file = root_.MakeFile(symlink_file_path_);
  EXPECT_EQ(file.second, SafeFD::Error::kSymlinkDetected);
  ASSERT_FALSE(file.first.is_valid());
}

TEST_F(SafeFDTest, MakeFile_WrongType) {
  ASSERT_TRUE(SetupSubdir());
  SafeFD::SafeFDResult file = root_.MakeFile(sub_dir_path_);
  EXPECT_EQ(file.second, SafeFD::Error::kWrongType);
  ASSERT_FALSE(file.first.is_valid());
}

TEST_F(SafeFDTest, MakeFile_WrongGID) {
  ASSERT_TRUE(WriteFile(""));
  ASSERT_EQ(chown(file_path_.value().c_str(), getuid(), 0), 0)
      << strerror(errno);
  {
    SafeFD::SafeFDResult file = root_.MakeFile(file_path_);
    EXPECT_EQ(file.second, SafeFD::Error::kWrongGID);
    ASSERT_FALSE(file.first.is_valid());
  }
}

TEST_F(SafeFDTest, MakeFile_WrongPermissions) {
  ASSERT_TRUE(WriteFile(""));
  ASSERT_EQ(chmod(file_path_.value().c_str(), 0777), 0) << strerror(errno);
  {
    SafeFD::SafeFDResult file = root_.MakeFile(file_path_);
    EXPECT_EQ(file.second, SafeFD::Error::kWrongPermissions);
    ASSERT_FALSE(file.first.is_valid());
  }
  ASSERT_EQ(chmod(file_path_.value().c_str(), SafeFD::kDefaultFilePermissions),
            0)
      << strerror(errno);

  EXPECT_EQ(chmod(sub_dir_path_.value().c_str(), 0777), 0) << strerror(errno);
  {
    SafeFD::SafeFDResult file = root_.MakeFile(file_path_);
    EXPECT_EQ(file.second, SafeFD::Error::kWrongPermissions);
    ASSERT_FALSE(file.first.is_valid());
  }
}

TEST_F(SafeFDTest, MakeDir_DoesNotExistSuccess) {
  {
    SafeFD::SafeFDResult dir = root_.MakeDir(sub_dir_path_);
    EXPECT_EQ(dir.second, SafeFD::Error::kNoError);
    ASSERT_TRUE(dir.first.is_valid());
  }

  ExpectPermissions(sub_dir_path_, SafeFD::kDefaultDirPermissions);
}

TEST_F(SafeFDTest, MakeFile_SingleComponentSuccess) {
  ASSERT_TRUE(SetupSubdir());

  SafeFD::Error err;
  SafeFD dir;
  std::tie(dir, err) = root_.OpenExistingDir(temp_dir_.GetPath());
  ASSERT_EQ(err, SafeFD::Error::kNoError);

  {
    SafeFD subdir;
    std::tie(subdir, err) = dir.MakeDir(base::FilePath(kSubdirName));
    EXPECT_EQ(err, SafeFD::Error::kNoError);
    ASSERT_TRUE(subdir.is_valid());
  }

  ExpectPermissions(sub_dir_path_, SafeFD::kDefaultDirPermissions);
}

TEST_F(SafeFDTest, MakeDir_ExistsSuccess) {
  ASSERT_TRUE(SetupSubdir());
  {
    SafeFD::SafeFDResult dir = root_.MakeDir(sub_dir_path_);
    EXPECT_EQ(dir.second, SafeFD::Error::kNoError);
    ASSERT_TRUE(dir.first.is_valid());
  }

  ExpectPermissions(sub_dir_path_, SafeFD::kDefaultDirPermissions);
}

TEST_F(SafeFDTest, MakeDir_WrongType) {
  ASSERT_TRUE(SetupSymlinks());
  SafeFD::SafeFDResult dir = root_.MakeDir(symlink_dir_path_);
  EXPECT_EQ(dir.second, SafeFD::Error::kWrongType);
  ASSERT_FALSE(dir.first.is_valid());
}

TEST_F(SafeFDTest, MakeDir_WrongGID) {
  ASSERT_TRUE(SetupSubdir());
  ASSERT_EQ(chown(sub_dir_path_.value().c_str(), getuid(), 0), 0)
      << strerror(errno);
  {
    SafeFD::SafeFDResult dir = root_.MakeDir(sub_dir_path_);
    EXPECT_EQ(dir.second, SafeFD::Error::kWrongGID);
    ASSERT_FALSE(dir.first.is_valid());
  }
}

TEST_F(SafeFDTest, MakeDir_WrongPermissions) {
  ASSERT_TRUE(SetupSubdir());
  ASSERT_EQ(chmod(sub_dir_path_.value().c_str(), 0777), 0) << strerror(errno);

  SafeFD::SafeFDResult dir = root_.MakeDir(sub_dir_path_);
  EXPECT_EQ(dir.second, SafeFD::Error::kWrongPermissions);
  ASSERT_FALSE(dir.first.is_valid());
}

TEST_F(SafeFDTest, Link_Success) {
  std::string data = GetRandomSuffix();
  ASSERT_TRUE(WriteFile(data));

  SafeFD::SafeFDResult dir = root_.OpenExistingDir(temp_dir_.GetPath());
  ASSERT_EQ(dir.second, SafeFD::Error::kNoError);

  SafeFD::SafeFDResult subdir = root_.OpenExistingDir(sub_dir_path_);
  ASSERT_EQ(subdir.second, SafeFD::Error::kNoError);

  EXPECT_EQ(dir.first.Link(subdir.first, kFileName, kFileName),
            SafeFD::Error::kNoError);

  SafeFD::SafeFDResult new_file = dir.first.OpenExistingFile(
      base::FilePath(kFileName), O_RDONLY | O_CLOEXEC);
  EXPECT_EQ(new_file.second, SafeFD::Error::kNoError);
  std::pair<std::vector<char>, SafeFD::Error> contents =
      new_file.first.ReadContents();
  EXPECT_EQ(contents.second, SafeFD::Error::kNoError);
  EXPECT_EQ(data.size(), contents.first.size());
  EXPECT_EQ(memcmp(data.data(), contents.first.data(), data.size()), 0);
}

TEST_F(SafeFDTest, Link_NotInitialized) {
  std::string data = GetRandomSuffix();
  ASSERT_TRUE(WriteFile(data));

  SafeFD::SafeFDResult dir = root_.OpenExistingDir(temp_dir_.GetPath());
  ASSERT_EQ(dir.second, SafeFD::Error::kNoError);

  SafeFD::SafeFDResult subdir = root_.OpenExistingDir(sub_dir_path_);
  ASSERT_EQ(subdir.second, SafeFD::Error::kNoError);

  EXPECT_EQ(SafeFD().Link(subdir.first, kFileName, kFileName),
            SafeFD::Error::kNotInitialized);

  EXPECT_EQ(dir.first.Link(SafeFD(), kFileName, kFileName),
            SafeFD::Error::kNotInitialized);
}

TEST_F(SafeFDTest, Link_BadArgument) {
  ASSERT_TRUE(WriteFile(""));

  SafeFD::SafeFDResult dir = root_.OpenExistingDir(temp_dir_.GetPath());
  ASSERT_EQ(dir.second, SafeFD::Error::kNoError);

  SafeFD::SafeFDResult subdir = root_.OpenExistingDir(sub_dir_path_);
  ASSERT_EQ(subdir.second, SafeFD::Error::kNoError);

  EXPECT_EQ(dir.first.Link(subdir.first, "a/a", kFileName),
            SafeFD::Error::kBadArgument);
  EXPECT_EQ(dir.first.Link(subdir.first, ".", kFileName),
            SafeFD::Error::kBadArgument);
  EXPECT_EQ(dir.first.Link(subdir.first, "..", kFileName),
            SafeFD::Error::kBadArgument);
  EXPECT_EQ(dir.first.Link(subdir.first, kFileName, "a/a"),
            SafeFD::Error::kBadArgument);
  EXPECT_EQ(dir.first.Link(subdir.first, kFileName, "."),
            SafeFD::Error::kBadArgument);
  EXPECT_EQ(dir.first.Link(subdir.first, kFileName, ".."),
            SafeFD::Error::kBadArgument);
}

TEST_F(SafeFDTest, Link_IOError) {
  ASSERT_TRUE(SetupSubdir());

  SafeFD::SafeFDResult dir = root_.OpenExistingDir(temp_dir_.GetPath());
  ASSERT_EQ(dir.second, SafeFD::Error::kNoError);

  SafeFD::SafeFDResult subdir = root_.OpenExistingDir(sub_dir_path_);
  ASSERT_EQ(subdir.second, SafeFD::Error::kNoError);

  EXPECT_EQ(dir.first.Link(subdir.first, kFileName, kFileName),
            SafeFD::Error::kIOError);
}

TEST_F(SafeFDTest, Unlink_Success) {
  ASSERT_TRUE(WriteFile(""));

  SafeFD::SafeFDResult subdir = root_.OpenExistingDir(sub_dir_path_);
  ASSERT_EQ(subdir.second, SafeFD::Error::kNoError);

  EXPECT_EQ(subdir.first.Unlink(kFileName), SafeFD::Error::kNoError);
  EXPECT_FALSE(base::PathExists(file_path_));
}

TEST_F(SafeFDTest, Unlink_NotInitialized) {
  ASSERT_TRUE(WriteFile(""));

  EXPECT_EQ(SafeFD().Unlink(kFileName), SafeFD::Error::kNotInitialized);
}

TEST_F(SafeFDTest, Unlink_BadArgument) {
  ASSERT_TRUE(WriteFile(""));

  SafeFD::SafeFDResult subdir = root_.OpenExistingDir(sub_dir_path_);
  ASSERT_EQ(subdir.second, SafeFD::Error::kNoError);

  EXPECT_EQ(subdir.first.Unlink("a/a"), SafeFD::Error::kBadArgument);
  EXPECT_EQ(subdir.first.Unlink("."), SafeFD::Error::kBadArgument);
  EXPECT_EQ(subdir.first.Unlink(".."), SafeFD::Error::kBadArgument);
}

TEST_F(SafeFDTest, Unlink_IOError_Nonexistent) {
  ASSERT_TRUE(SetupSubdir());

  SafeFD::SafeFDResult subdir = root_.OpenExistingDir(sub_dir_path_);
  ASSERT_EQ(subdir.second, SafeFD::Error::kNoError);

  EXPECT_EQ(subdir.first.Unlink(kFileName), SafeFD::Error::kIOError);
}

TEST_F(SafeFDTest, Unlink_IOError_IsADir) {
  ASSERT_TRUE(SetupSubdir());

  SafeFD::SafeFDResult dir = root_.OpenExistingDir(temp_dir_.GetPath());
  ASSERT_EQ(dir.second, SafeFD::Error::kNoError);

  EXPECT_EQ(dir.first.Unlink(kSubdirName), SafeFD::Error::kIOError);
}

TEST_F(SafeFDTest, Rmdir_Recursive_Success) {
  ASSERT_TRUE(WriteFile(""));

  SafeFD::SafeFDResult dir = root_.OpenExistingDir(temp_dir_.GetPath());
  ASSERT_EQ(dir.second, SafeFD::Error::kNoError);

  EXPECT_EQ(dir.first.Rmdir(kSubdirName, true /*recursive*/),
            SafeFD::Error::kNoError);
  EXPECT_FALSE(base::PathExists(file_path_));
  EXPECT_FALSE(base::PathExists(sub_dir_path_));
}

TEST_F(SafeFDTest, Rmdir_Recursive_SuccessMaxRecursion) {
  SafeFD::Error err;
  SafeFD dir;

  // Create directory with the maximum depth.
  std::tie(dir, err) = root_.OpenExistingDir(temp_dir_.GetPath());
  EXPECT_EQ(err, SafeFD::Error::kNoError);
  ASSERT_TRUE(dir.is_valid());
  for (size_t x = 0; x < SafeFD::kDefaultMaxPathDepth; ++x) {
    std::tie(dir, err) = dir.MakeDir(base::FilePath(kSubdirName));
    EXPECT_EQ(err, SafeFD::Error::kNoError);
    ASSERT_TRUE(dir.is_valid());
  }

  // Check if recursive Rmdir succeeds (i.e. there isn't a stack overflow).
  std::tie(dir, err) = root_.OpenExistingDir(temp_dir_.GetPath());
  EXPECT_EQ(err, SafeFD::Error::kNoError);

  EXPECT_EQ(dir.Rmdir(kSubdirName, true /*recursive*/),
            SafeFD::Error::kNoError);
  EXPECT_FALSE(base::PathExists(file_path_));
  EXPECT_FALSE(base::PathExists(sub_dir_path_));
}

TEST_F(SafeFDTest, Rmdir_NotInitialized) {
  ASSERT_TRUE(WriteFile(""));

  EXPECT_EQ(SafeFD().Rmdir(kSubdirName, true /*recursive*/),
            SafeFD::Error::kNotInitialized);
}

TEST_F(SafeFDTest, Rmdir_BadArgument) {
  ASSERT_TRUE(WriteFile(""));

  SafeFD::SafeFDResult dir = root_.OpenExistingDir(temp_dir_.GetPath());
  EXPECT_EQ(dir.second, SafeFD::Error::kNoError);

  EXPECT_EQ(dir.first.Rmdir("a/a"), SafeFD::Error::kBadArgument);
  EXPECT_EQ(dir.first.Rmdir("."), SafeFD::Error::kBadArgument);
  EXPECT_EQ(dir.first.Rmdir(".."), SafeFD::Error::kBadArgument);
}

TEST_F(SafeFDTest, Rmdir_ExceededMaximum) {
  ASSERT_TRUE(SetupSubdir());
  ASSERT_TRUE(base::CreateDirectory(sub_dir_path_.Append(kSubdirName)));

  SafeFD::SafeFDResult dir = root_.OpenExistingDir(temp_dir_.GetPath());
  ASSERT_EQ(dir.second, SafeFD::Error::kNoError);

  EXPECT_EQ(dir.first.Rmdir(kSubdirName, true /*recursive*/, 1),
            SafeFD::Error::kExceededMaximum);
}

TEST_F(SafeFDTest, Rmdir_IOError) {
  SafeFD::SafeFDResult dir = root_.OpenExistingDir(temp_dir_.GetPath());
  ASSERT_EQ(dir.second, SafeFD::Error::kNoError);

  // Dir doesn't exist.
  EXPECT_EQ(dir.first.Rmdir(kSubdirName), SafeFD::Error::kIOError);

  // Dir not empty.
  ASSERT_TRUE(WriteFile(""));
  EXPECT_EQ(dir.first.Rmdir(kSubdirName), SafeFD::Error::kIOError);
}

TEST_F(SafeFDTest, Rmdir_WrongType) {
  ASSERT_TRUE(WriteFile(""));

  SafeFD::SafeFDResult subdir = root_.OpenExistingDir(sub_dir_path_);
  ASSERT_EQ(subdir.second, SafeFD::Error::kNoError);

  EXPECT_EQ(subdir.first.Rmdir(kFileName), SafeFD::Error::kWrongType);
}

TEST_F(SafeFDTest, Rmdir_Recursive_KeepGoing) {
  ASSERT_TRUE(SetupSubdir());

  ASSERT_TRUE(base::CreateDirectory(sub_dir_path_.Append(kSubdirName)));

  // Give us something to iterate over.
  constexpr int kNumSentinel = 25;
  for (int i = 0; i < kNumSentinel; i++) {
    SafeFD::SafeFDResult file =
        root_.MakeFile(sub_dir_path_.Append(GetRandomSuffix()));
    EXPECT_EQ(file.second, SafeFD::Error::kNoError);
    ASSERT_TRUE(file.first.is_valid());
  }

  // Recursively delete with a max level that is too small. Capture errno.
  SafeFD::Error result = root_.Rmdir(kSubdirName, true /*recursive*/,
                                     1 /*max_depth*/, true /*keep_going*/);
  int rmdir_errno = errno;

  EXPECT_EQ(result, SafeFD::Error::kExceededMaximum);

  // If we keep going, the last operation will be the post-order unlink of
  // the top-level directory. This has to fail with ENOTEMPTY since we did
  // not delete the too-deep sub-directories. This particular behavior
  // should not be part of the API contract and this can be relaxed if the
  // implementation is changed.
  EXPECT_EQ(rmdir_errno, ENOTEMPTY);

  // The deep directory must still exist.
  ASSERT_TRUE(
      base::DeleteFile(sub_dir_path_.Append(kSubdirName), false /*recursive*/));

  // We cannot control the iteration order so even if we incorrectly
  // stopped early the directory might still be empty if the deep
  // directories were last in the iteration order. But a non-empty
  // directory is always incorrect.
  ASSERT_TRUE(base::IsDirectoryEmpty(sub_dir_path_));
}

TEST_F(SafeFDTest, Rmdir_Recursive_StopOnError) {
  ASSERT_TRUE(SetupSubdir());

  ASSERT_TRUE(base::CreateDirectory(sub_dir_path_.Append(kSubdirName)));

  // Give us something to iterate over.
  constexpr int kNumSentinel = 25;
  for (int i = 0; i < kNumSentinel; i++) {
    SafeFD::SafeFDResult file =
        root_.MakeFile(sub_dir_path_.Append(GetRandomSuffix()));
    EXPECT_EQ(file.second, SafeFD::Error::kNoError);
    ASSERT_TRUE(file.first.is_valid());
  }

  // Recursively delete with a max level that is too small. Capture errno.
  SafeFD::Error result = root_.Rmdir(kSubdirName, true /*recursive*/,
                                     1 /*max_depth*/, false /*keep_going*/);
  int rmdir_errno = errno;

  EXPECT_EQ(result, SafeFD::Error::kExceededMaximum);

  // If we stop on encountering a too-deep directory, we never actually
  // make any libc calls that encounter errors. This particular behavior
  // should not be part of the API contract and this can be relaxed if the
  // implementation is changed.
  EXPECT_EQ(rmdir_errno, 0);

  // The deep directory must still exist.
  ASSERT_TRUE(
      base::DeleteFile(sub_dir_path_.Append(kSubdirName), false /*recursive*/));
}

}  // namespace brillo
