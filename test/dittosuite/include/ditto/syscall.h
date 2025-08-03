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

#include <dirent.h>
#include <fcntl.h>
#include <sys/param.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstdint>
#include <string>

namespace dittosuite {

class SyscallInterface {
 public:
  virtual ~SyscallInterface() {}

  virtual int Access(const std::string& path_name, int mode) = 0;
  virtual int Close(int fd) = 0;
  virtual int CloseDir(DIR* dirp) = 0;
  virtual int FAdvise(int fd, int64_t offset, int64_t len, int advice) = 0;
  virtual int FAllocate(int fd, int mode, int64_t offset, int64_t len) = 0;
  virtual int FTruncate(int fd, int64_t length) = 0;
  virtual int FStat(int filedes, struct stat64* buf) = 0;
  virtual int FSync(int fd) = 0;
  virtual int Open(const std::string& path_name, int flags, int mode) = 0;
  virtual DIR* OpenDir(const std::string& name) = 0;
  virtual int64_t Read(int fd, char* buf, int64_t count, int64_t offset) = 0;
  virtual struct dirent* ReadDir(DIR* dirp) = 0;
  virtual int64_t ReadLink(const std::string& path_name, char* buf, int64_t bufsiz) = 0;
  virtual void Sync() = 0;
  virtual int Unlink(const std::string& path_name) = 0;
  virtual int64_t Write(int fd, char* buf, int64_t count, int64_t offset) = 0;
};

class Syscall : public SyscallInterface {
 public:
  Syscall(Syscall& other) = delete;
  void operator=(const Syscall&) = delete;

  static Syscall& GetSyscall();

  int Access(const std::string& path_name, int mode) override;
  int Close(int fd) override;
  int CloseDir(DIR* dirp) override;
  int FAdvise(int fd, int64_t offset, int64_t len, int advice) override;
  int FAllocate(int fd, int mode, int64_t offset, int64_t len) override;
  int FTruncate(int fd, int64_t length) override;
  int FStat(int filedes, struct stat64* buf) override;
  int FSync(int fd) override;
  int Open(const std::string& path_name, int flags, int mode) override;
  DIR* OpenDir(const std::string& name) override;
  int64_t Read(int fd, char* buf, int64_t count, int64_t offset) override;
  struct dirent* ReadDir(DIR* dirp) override;
  int64_t ReadLink(const std::string& path_name, char* buf, int64_t bufsiz) override;
  void Sync() override;
  int Unlink(const std::string& path_name) override;
  int64_t Write(int fd, char* buf, int64_t count, int64_t offset) override;

 private:
  Syscall(){};
};

}  // namespace dittosuite
