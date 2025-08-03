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

#ifndef ICING_FILE_MOCK_FILESYSTEM_H_
#define ICING_FILE_MOCK_FILESYSTEM_H_

#include <cstdint>

#include "gmock/gmock.h"
#include "icing/file/filesystem.h"

namespace icing {
namespace lib {
using ::testing::_;
using ::testing::A;

class MockFilesystem : public Filesystem {
 public:
  MockFilesystem() {
    // For all methods, we always delegate calls to a real Filesystem instance
    // by default.

    ON_CALL(*this, DeleteFile).WillByDefault([this](const char* file_name) {
      return real_filesystem_.DeleteFile(file_name);
    });

    ON_CALL(*this, DeleteDirectory).WillByDefault([this](const char* dir_name) {
      return real_filesystem_.DeleteDirectory(dir_name);
    });

    ON_CALL(*this, DeleteDirectoryRecursively)
        .WillByDefault([this](const char* dir_name) {
          return real_filesystem_.DeleteDirectoryRecursively(dir_name);
        });

    ON_CALL(*this, CopyFile)
        .WillByDefault([this](const char* src, const char* dst) {
          return real_filesystem_.CopyFile(src, dst);
        });

    ON_CALL(*this, CopyDirectory)
        .WillByDefault(
            [this](const char* src, const char* dst, bool recursive) {
              return real_filesystem_.CopyDirectory(src, dst, recursive);
            });

    ON_CALL(*this, FileExists).WillByDefault([this](const char* file_name) {
      return real_filesystem_.FileExists(file_name);
    });

    ON_CALL(*this, DirectoryExists).WillByDefault([this](const char* dir_name) {
      return real_filesystem_.DirectoryExists(dir_name);
    });

    ON_CALL(*this, GetBasenameIndex)
        .WillByDefault([this](const char* file_name) {
          return real_filesystem_.GetBasenameIndex(file_name);
        });

    ON_CALL(*this, GetBasename).WillByDefault([this](const char* file_name) {
      return real_filesystem_.GetBasename(file_name);
    });

    ON_CALL(*this, GetDirname).WillByDefault([this](const char* file_name) {
      return real_filesystem_.GetDirname(file_name);
    });

    ON_CALL(*this, ListDirectory(_, _))
        .WillByDefault(
            [this](const char* dir_name, std::vector<std::string>* entries) {
              return real_filesystem_.ListDirectory(dir_name, entries);
            });

    ON_CALL(*this, ListDirectory(_, _, _, _))
        .WillByDefault([this](const char* dir_name,
                              const std::unordered_set<std::string>& exclude,
                              bool recursive,
                              std::vector<std::string>* entries) {
          return real_filesystem_.ListDirectory(dir_name, exclude, recursive,
                                                entries);
        });

    ON_CALL(*this, GetMatchingFiles)
        .WillByDefault(
            [this](const char* glob, std::vector<std::string>* matches) {
              return real_filesystem_.GetMatchingFiles(glob, matches);
            });

    ON_CALL(*this, OpenForWrite).WillByDefault([this](const char* file_name) {
      return real_filesystem_.OpenForWrite(file_name);
    });

    ON_CALL(*this, OpenForAppend).WillByDefault([this](const char* file_name) {
      return real_filesystem_.OpenForAppend(file_name);
    });

    ON_CALL(*this, OpenForRead).WillByDefault([this](const char* file_name) {
      return real_filesystem_.OpenForRead(file_name);
    });

    ON_CALL(*this, GetFileSize(A<int>())).WillByDefault([this](int fd) {
      return real_filesystem_.GetFileSize(fd);
    });

    ON_CALL(*this, GetFileSize(A<const char*>()))
        .WillByDefault([this](const char* file_name) {
          return real_filesystem_.GetFileSize(file_name);
        });

    ON_CALL(*this, Truncate(A<int>(), _))
        .WillByDefault([this](int fd, int64_t new_size) {
          return real_filesystem_.Truncate(fd, new_size);
        });

    ON_CALL(*this, Truncate(A<const char*>(), _))
        .WillByDefault([this](const char* filename, int64_t new_size) {
          return real_filesystem_.Truncate(filename, new_size);
        });

    ON_CALL(*this, Grow(A<int>(), _))
        .WillByDefault([this](int fd, int64_t new_size) {
          return real_filesystem_.Grow(fd, new_size);
        });

    ON_CALL(*this, Grow(A<const char*>(), _))
        .WillByDefault([this](const char* filename, int64_t new_size) {
          return real_filesystem_.Grow(filename, new_size);
        });

    ON_CALL(*this, Write(A<int>(), _, _))
        .WillByDefault([this](int fd, const void* data, size_t data_size) {
          return real_filesystem_.Write(fd, data, data_size);
        });

    ON_CALL(*this, Write(A<const char*>(), _, _))
        .WillByDefault(
            [this](const char* filename, const void* data, size_t data_size) {
              return real_filesystem_.Write(filename, data, data_size);
            });

    ON_CALL(*this, PWrite(A<int>(), _, _, _))
        .WillByDefault(
            [this](int fd, off_t offset, const void* data, size_t data_size) {
              return real_filesystem_.PWrite(fd, offset, data, data_size);
            });

    ON_CALL(*this, PWrite(A<const char*>(), _, _, _))
        .WillByDefault([this](const char* filename, off_t offset,
                              const void* data, size_t data_size) {
          return real_filesystem_.PWrite(filename, offset, data, data_size);
        });

    ON_CALL(*this, Read(A<int>(), _, _))
        .WillByDefault([this](int fd, void* buf, size_t buf_size) {
          return real_filesystem_.Read(fd, buf, buf_size);
        });

    ON_CALL(*this, Read(A<const char*>(), _, _))
        .WillByDefault(
            [this](const char* filename, void* buf, size_t buf_size) {
              return real_filesystem_.Read(filename, buf, buf_size);
            });

    ON_CALL(*this, PRead(A<int>(), _, _, _))
        .WillByDefault(
            [this](int fd, void* buf, size_t buf_size, off_t offset) {
              return real_filesystem_.PRead(fd, buf, buf_size, offset);
            });

    ON_CALL(*this, PRead(A<const char*>(), _, _, _))
        .WillByDefault([this](const char* filename, void* buf, size_t buf_size,
                              off_t offset) {
          return real_filesystem_.PRead(filename, buf, buf_size, offset);
        });

    ON_CALL(*this, DataSync).WillByDefault([this](int fd) {
      return real_filesystem_.DataSync(fd);
    });

    ON_CALL(*this, RenameFile)
        .WillByDefault([this](const char* old_name, const char* new_name) {
          return real_filesystem_.RenameFile(old_name, new_name);
        });

    ON_CALL(*this, SwapFiles)
        .WillByDefault([this](const char* one, const char* two) {
          return real_filesystem_.SwapFiles(one, two);
        });

    ON_CALL(*this, CreateDirectory).WillByDefault([this](const char* dir_name) {
      return real_filesystem_.CreateDirectory(dir_name);
    });

    ON_CALL(*this, CreateDirectoryRecursively)
        .WillByDefault([this](const char* dir_name) {
          return real_filesystem_.CreateDirectoryRecursively(dir_name);
        });

    ON_CALL(*this, GetDiskUsage(A<int>())).WillByDefault([this](int fd) {
      return real_filesystem_.GetDiskUsage(fd);
    });

    ON_CALL(*this, GetFileDiskUsage).WillByDefault([this](const char* path) {
      return real_filesystem_.GetFileDiskUsage(path);
    });

    ON_CALL(*this, GetDiskUsage(A<const char*>()))
        .WillByDefault([this](const char* path) {
          return real_filesystem_.GetDiskUsage(path);
        });

    ON_CALL(*this, GetCurrentPosition).WillByDefault([this](int fd) {
      return real_filesystem_.GetCurrentPosition(fd);
    });

    ON_CALL(*this, SetPosition).WillByDefault([this](int fd, int offset) {
      return real_filesystem_.SetPosition(fd, offset);
    });
  }

  MOCK_METHOD(bool, DeleteFile, (const char* file_name), (const));

  MOCK_METHOD(bool, DeleteDirectory, (const char* dir_name), (const));

  MOCK_METHOD(bool, DeleteDirectoryRecursively, (const char* dir_name),
              (const));

  MOCK_METHOD(bool, CopyFile, (const char* src, const char* dst), (const));

  MOCK_METHOD(bool, CopyDirectory,
              (const char* src, const char* dst, bool recursive), (const));

  MOCK_METHOD(bool, FileExists, (const char* file_name), (const));

  MOCK_METHOD(bool, DirectoryExists, (const char* dir_name), (const));

  MOCK_METHOD(int, GetBasenameIndex, (const char* file_name), (const));

  MOCK_METHOD(std::string, GetBasename, (const char* file_name), (const));

  MOCK_METHOD(std::string, GetDirname, (const char* file_name), (const));

  MOCK_METHOD(bool, ListDirectory,
              (const char* dir_name, std::vector<std::string>* entries),
              (const));

  MOCK_METHOD(bool, ListDirectory,
              (const char* dir_name,
               const std::unordered_set<std::string>& exclude, bool recursive,
               std::vector<std::string>* entries),
              (const));

  MOCK_METHOD(bool, GetMatchingFiles,
              (const char* glob, std::vector<std::string>* matches), (const));

  MOCK_METHOD(int, OpenForWrite, (const char* file_name), (const));

  MOCK_METHOD(int, OpenForAppend, (const char* file_name), (const));

  MOCK_METHOD(int, OpenForRead, (const char* file_name), (const));

  MOCK_METHOD(int64_t, GetFileSize, (int fd), (const));

  MOCK_METHOD(int64_t, GetFileSize, (const char* filename), (const));

  MOCK_METHOD(bool, Truncate, (int fd, int64_t new_size), (const));

  MOCK_METHOD(bool, Truncate, (const char* filename, int64_t new_size),
              (const));

  MOCK_METHOD(bool, Grow, (int fd, int64_t new_size), (const));

  MOCK_METHOD(bool, Grow, (const char* filename, int64_t new_size), (const));

  MOCK_METHOD(bool, Write, (int fd, const void* data, size_t data_size),
              (const));

  MOCK_METHOD(bool, Write,
              (const char* filename, const void* data, size_t data_size),
              (const));

  MOCK_METHOD(bool, PWrite,
              (int fd, off_t offset, const void* data, size_t data_size),
              (const));

  MOCK_METHOD(bool, PWrite,
              (const char* filename, off_t offset, const void* data,
               size_t data_size),
              (const));

  MOCK_METHOD(bool, Read, (int fd, void* buf, size_t buf_size), (const));

  MOCK_METHOD(bool, Read, (const char* filename, void* buf, size_t buf_size),
              (const));

  MOCK_METHOD(bool, PRead, (int fd, void* buf, size_t buf_size, off_t offset),
              (const));

  MOCK_METHOD(bool, PRead,
              (const char* filename, void* buf, size_t buf_size, off_t offset),
              (const));

  MOCK_METHOD(bool, DataSync, (int fd), (const));

  MOCK_METHOD(bool, RenameFile, (const char* old_name, const char* new_name),
              (const));

  MOCK_METHOD(bool, SwapFiles, (const char* one, const char* two), (const));

  MOCK_METHOD(bool, CreateDirectory, (const char* dir_name), (const));

  MOCK_METHOD(bool, CreateDirectoryRecursively, (const char* dir_name),
              (const));

  MOCK_METHOD(int64_t, GetDiskUsage, (int fd), (const));

  MOCK_METHOD(int64_t, GetFileDiskUsage, (const char* path), (const));

  MOCK_METHOD(int64_t, GetDiskUsage, (const char* path), (const));

  MOCK_METHOD(int64_t, GetCurrentPosition, (int fd), (const));

  MOCK_METHOD(int64_t, SetPosition, (int fd, int offset), (const));

 private:
  Filesystem real_filesystem_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_MOCK_FILESYSTEM_H_
