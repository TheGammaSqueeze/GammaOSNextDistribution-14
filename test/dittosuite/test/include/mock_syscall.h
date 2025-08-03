// Copyright (C) 2021 The Android Open Source Project
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

#pragma once

#include <gmock/gmock.h>

#include <ditto/syscall.h>

class MockSyscall : public dittosuite::SyscallInterface {
 public:
  static constexpr int kDefaultFileSize = 4096;
  static constexpr int kDefaultFileDescriptor = 4;

  // Set default returns for each syscall (mostly return 0 to indicate a successful call)
  MockSyscall() {
    ON_CALL(*this, Access(::testing::_, ::testing::_)).WillByDefault(::testing::Return(0));
    ON_CALL(*this, Close(::testing::_)).WillByDefault(::testing::Return(0));
    ON_CALL(*this, CloseDir(::testing::_)).WillByDefault(::testing::Return(0));
    ON_CALL(*this, FAdvise(::testing::_, ::testing::_, ::testing::_, ::testing::_))
        .WillByDefault(::testing::Return(0));
    ON_CALL(*this, FAllocate(::testing::_, ::testing::_, ::testing::_, ::testing::_))
        .WillByDefault(::testing::Return(0));
    ON_CALL(*this, FTruncate(::testing::_, ::testing::_)).WillByDefault(::testing::Return(0));
    ON_CALL(*this, FStat(::testing::_, ::testing::_))
        .WillByDefault(::testing::Invoke([](int, struct stat64* buf) {
          buf->st_size = kDefaultFileSize;
          return 0;
        }));
    ON_CALL(*this, FSync(::testing::_)).WillByDefault(::testing::Return(0));
    ON_CALL(*this, Open(::testing::_, ::testing::_, ::testing::_))
        .WillByDefault(::testing::Return(kDefaultFileDescriptor));
    ON_CALL(*this, Read(::testing::_, ::testing::_, ::testing::_, ::testing::_))
        .WillByDefault(::testing::ReturnArg<2>());
    ON_CALL(*this, ReadLink(::testing::_, ::testing::_, ::testing::_))
        .WillByDefault(::testing::ReturnArg<2>());
    ON_CALL(*this, Unlink(::testing::_)).WillByDefault(::testing::Return(0));
    ON_CALL(*this, Write(::testing::_, ::testing::_, ::testing::_, ::testing::_))
        .WillByDefault(::testing::ReturnArg<2>());
  }

  MOCK_METHOD(int, Access, (const std::string& path_name, int mode), (override));
  MOCK_METHOD(int, Close, (int fd), (override));
  MOCK_METHOD(int, CloseDir, (DIR * dirp), (override));
  MOCK_METHOD(int, FAdvise, (int fd, int64_t offset, int64_t len, int advice), (override));
  MOCK_METHOD(int, FAllocate, (int fd, int mode, int64_t offset, int64_t len), (override));
  MOCK_METHOD(int, FTruncate, (int fd, int64_t length), (override));
  MOCK_METHOD(int, FStat, (int filedes, struct stat64* buf), (override));
  MOCK_METHOD(int, FSync, (int fd), (override));
  MOCK_METHOD(int, Open, (const std::string& path_name, int flags, int mode), (override));
  MOCK_METHOD(DIR*, OpenDir, (const std::string& name), (override));
  MOCK_METHOD(int64_t, Read, (int fd, char* buf, int64_t count, int64_t offset), (override));
  MOCK_METHOD(struct dirent*, ReadDir, (DIR * dirp), (override));
  MOCK_METHOD(int64_t, ReadLink, (const std::string& path_name, char* buf, int64_t bufsiz),
              (override));
  MOCK_METHOD(void, Sync, (), (override));
  MOCK_METHOD(int, Unlink, (const std::string& path_name), (override));
  MOCK_METHOD(int64_t, Write, (int fd, char* buf, int64_t count, int64_t offset), (override));
};
