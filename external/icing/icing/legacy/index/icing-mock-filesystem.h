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

#ifndef ICING_LEGACY_INDEX_ICING_MOCK_FILESYSTEM_H_
#define ICING_LEGACY_INDEX_ICING_MOCK_FILESYSTEM_H_

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

#include "gmock/gmock.h"
#include "icing/legacy/index/icing-filesystem.h"

namespace icing {
namespace lib {
using ::testing::_;
using ::testing::A;

class IcingMockFilesystem : public IcingFilesystem {
 public:
  IcingMockFilesystem() {
    ON_CALL(*this, DeleteFile).WillByDefault([this](const char *file_name) {
      return real_icing_filesystem_.DeleteFile(file_name);
    });

    ON_CALL(*this, DeleteDirectory).WillByDefault([this](const char *dir_name) {
      return real_icing_filesystem_.DeleteDirectory(dir_name);
    });

    ON_CALL(*this, DeleteDirectoryRecursively)
        .WillByDefault([this](const char *dir_name) {
          return real_icing_filesystem_.DeleteDirectoryRecursively(dir_name);
        });

    ON_CALL(*this, FileExists).WillByDefault([this](const char *file_name) {
      return real_icing_filesystem_.FileExists(file_name);
    });

    ON_CALL(*this, DirectoryExists).WillByDefault([this](const char *dir_name) {
      return real_icing_filesystem_.DirectoryExists(dir_name);
    });

    ON_CALL(*this, GetBasenameIndex)
        .WillByDefault([this](const char *file_name) {
          return real_icing_filesystem_.GetBasenameIndex(file_name);
        });

    ON_CALL(*this, GetBasename).WillByDefault([this](const char *file_name) {
      return real_icing_filesystem_.GetBasename(file_name);
    });

    ON_CALL(*this, GetDirname).WillByDefault([this](const char *file_name) {
      return real_icing_filesystem_.GetDirname(file_name);
    });

    ON_CALL(*this, ListDirectory)
        .WillByDefault(
            [this](const char *dir_name, std::vector<std::string> *entries) {
              return real_icing_filesystem_.ListDirectory(dir_name, entries);
            });

    ON_CALL(*this, GetMatchingFiles)
        .WillByDefault(
            [this](const char *glob, std::vector<std::string> *matches) {
              return real_icing_filesystem_.GetMatchingFiles(glob, matches);
            });

    ON_CALL(*this, OpenForWrite).WillByDefault([this](const char *file_name) {
      return real_icing_filesystem_.OpenForWrite(file_name);
    });

    ON_CALL(*this, OpenForAppend).WillByDefault([this](const char *file_name) {
      return real_icing_filesystem_.OpenForAppend(file_name);
    });

    ON_CALL(*this, OpenForRead).WillByDefault([this](const char *file_name) {
      return real_icing_filesystem_.OpenForRead(file_name);
    });

    ON_CALL(*this, GetFileSize(A<int>())).WillByDefault([this](int fd) {
      return real_icing_filesystem_.GetFileSize(fd);
    });

    ON_CALL(*this, GetFileSize(A<const char *>()))
        .WillByDefault([this](const char *filename) {
          return real_icing_filesystem_.GetFileSize(filename);
        });

    ON_CALL(*this, Truncate(A<int>(), _))
        .WillByDefault([this](int fd, uint64_t new_size) {
          return real_icing_filesystem_.Truncate(fd, new_size);
        });

    ON_CALL(*this, Truncate(A<const char *>(), _))
        .WillByDefault([this](const char *filename, uint64_t new_size) {
          return real_icing_filesystem_.Truncate(filename, new_size);
        });

    ON_CALL(*this, Grow).WillByDefault([this](int fd, uint64_t new_size) {
      return real_icing_filesystem_.Grow(fd, new_size);
    });

    ON_CALL(*this, Write)
        .WillByDefault([this](int fd, const void *data, size_t data_size) {
          return real_icing_filesystem_.Write(fd, data, data_size);
        });
    ON_CALL(*this, PWrite)
        .WillByDefault(
            [this](int fd, off_t offset, const void *data, size_t data_size) {
              return real_icing_filesystem_.PWrite(fd, offset, data, data_size);
            });

    ON_CALL(*this, DataSync).WillByDefault([this](int fd) {
      return real_icing_filesystem_.DataSync(fd);
    });

    ON_CALL(*this, RenameFile)
        .WillByDefault([this](const char *old_name, const char *new_name) {
          return real_icing_filesystem_.RenameFile(old_name, new_name);
        });

    ON_CALL(*this, SwapFiles)
        .WillByDefault([this](const char *one, const char *two) {
          return real_icing_filesystem_.SwapFiles(one, two);
        });

    ON_CALL(*this, CreateDirectory).WillByDefault([this](const char *dir_name) {
      return real_icing_filesystem_.CreateDirectory(dir_name);
    });

    ON_CALL(*this, CreateDirectoryRecursively)
        .WillByDefault([this](const char *dir_name) {
          return real_icing_filesystem_.CreateDirectoryRecursively(dir_name);
        });

    ON_CALL(*this, CopyFile)
        .WillByDefault([this](const char *src, const char *dst) {
          return real_icing_filesystem_.CopyFile(src, dst);
        });

    ON_CALL(*this, ComputeChecksum)
        .WillByDefault([this](int fd, uint32_t *checksum, uint64_t offset,
                              uint64_t length) {
          return real_icing_filesystem_.ComputeChecksum(fd, checksum, offset,
                                                        length);
        });

    ON_CALL(*this, GetDiskUsage).WillByDefault([this](const char *path) {
      return real_icing_filesystem_.GetDiskUsage(path);
    });
  }

  MOCK_METHOD(bool, DeleteFile, (const char *file_name), (const, override));

  MOCK_METHOD(bool, DeleteDirectory, (const char *dir_name), (const, override));

  MOCK_METHOD(bool, DeleteDirectoryRecursively, (const char *dir_name),
              (const, override));

  MOCK_METHOD(bool, FileExists, (const char *file_name), (const, override));

  MOCK_METHOD(bool, DirectoryExists, (const char *dir_name), (const, override));

  MOCK_METHOD(int, GetBasenameIndex, (const char *file_name),
              (const, override));

  MOCK_METHOD(std::string, GetBasename, (const char *file_name),
              (const, override));

  MOCK_METHOD(std::string, GetDirname, (const char *file_name),
              (const, override));

  MOCK_METHOD(bool, ListDirectory,
              (const char *dir_name, std::vector<std::string> *entries),
              (const, override));

  MOCK_METHOD(bool, GetMatchingFiles,
              (const char *glob, std::vector<std::string> *matches),
              (const, override));

  MOCK_METHOD(int, OpenForWrite, (const char *file_name), (const, override));

  MOCK_METHOD(int, OpenForAppend, (const char *file_name), (const, override));

  MOCK_METHOD(int, OpenForRead, (const char *file_name), (const, override));

  MOCK_METHOD(uint64_t, GetFileSize, (int fd), (const, override));

  MOCK_METHOD(uint64_t, GetFileSize, (const char *filename), (const, override));

  MOCK_METHOD(bool, Truncate, (int fd, uint64_t new_size), (const, override));

  MOCK_METHOD(bool, Truncate, (const char *filename, uint64_t new_size),
              (const, override));

  MOCK_METHOD(bool, Grow, (int fd, uint64_t new_size), (const, override));

  MOCK_METHOD(bool, Write, (int fd, const void *data, size_t data_size),
              (const, override));
  MOCK_METHOD(bool, PWrite,
              (int fd, off_t offset, const void *data, size_t data_size),
              (const, override));

  MOCK_METHOD(bool, DataSync, (int fd), (const, override));

  MOCK_METHOD(bool, RenameFile, (const char *old_name, const char *new_name),
              (const, override));

  MOCK_METHOD(bool, SwapFiles, (const char *one, const char *two),
              (const, override));

  MOCK_METHOD(bool, CreateDirectory, (const char *dir_name), (const, override));

  MOCK_METHOD(bool, CreateDirectoryRecursively, (const char *dir_name),
              (const, override));

  MOCK_METHOD(bool, CopyFile, (const char *src, const char *dst),
              (const, override));

  MOCK_METHOD(bool, ComputeChecksum,
              (int fd, uint32_t *checksum, uint64_t offset, uint64_t length),
              (const, override));

  MOCK_METHOD(uint64_t, GetDiskUsage, (const char *path), (const, override));

 private:
  IcingFilesystem real_icing_filesystem_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_LEGACY_INDEX_ICING_MOCK_FILESYSTEM_H_
