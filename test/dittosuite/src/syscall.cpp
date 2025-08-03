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

#include <ditto/syscall.h>

namespace dittosuite {

Syscall& Syscall::GetSyscall() {
  static Syscall syscall;
  return syscall;
}

int Syscall::Access(const std::string& path_name, int mode) {
  return access(path_name.c_str(), mode);
}

int Syscall::Close(int fd) {
  return close(fd);
}

int Syscall::CloseDir(DIR* dirp) {
  return closedir(dirp);
}

int Syscall::FAdvise(int fd, int64_t offset, int64_t len, int advice) {
  return posix_fadvise64(fd, offset, len, advice);
}

int Syscall::FAllocate(int fd, int mode, int64_t offset, int64_t len) {
  return fallocate64(fd, mode, offset, len);
}

int Syscall::FTruncate(int fd, int64_t length) {
  return ftruncate64(fd, length);
}

int Syscall::FStat(int filedes, struct stat64* buf) {
  return fstat64(filedes, buf);
}

int Syscall::FSync(int fd) {
  return fsync(fd);
}

int Syscall::Open(const std::string& path_name, int flags, int mode) {
  return open(path_name.c_str(), flags, mode);
}

DIR* Syscall::OpenDir(const std::string& name) {
  return opendir(name.c_str());
}

int64_t Syscall::Read(int fd, char* buf, int64_t count, int64_t offset) {
  return pread64(fd, buf, count, offset);
}

struct dirent* Syscall::ReadDir(DIR* dirp) {
  return readdir(dirp);
}

int64_t Syscall::ReadLink(const std::string& path_name, char* buf, int64_t bufsiz) {
  return readlink(path_name.c_str(), buf, bufsiz);
}

void Syscall::Sync() {
  return sync();
}

int Syscall::Unlink(const std::string& path_name) {
  return unlink(path_name.c_str());
}

int64_t Syscall::Write(int fd, char* buf, int64_t count, int64_t offset) {
  return pwrite64(fd, buf, count, offset);
}

}  // namespace dittosuite
