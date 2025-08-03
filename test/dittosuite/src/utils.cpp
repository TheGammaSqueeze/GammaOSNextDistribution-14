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

#include <ditto/utils.h>

#include <sys/param.h>
#include <sys/stat.h>

#include <ditto/logger.h>

namespace dittosuite {

int64_t GetFileSize(SyscallInterface& syscall, int fd) {
  struct stat64 sb;
  if (syscall.FStat(fd, &sb) == -1) {
    PLOGF("Error while calling fstat() to get file size");
  }
  return sb.st_size;
}

std::string GetFilePath(SyscallInterface& syscall, int fd) {
  char file_path[PATH_MAX];
  if (syscall.ReadLink("/proc/self/fd/" + std::to_string(fd), file_path, sizeof(file_path)) == -1) {
    PLOGF("Error while calling readlink() to get file path");
  }
  return std::string(file_path);
}

bool FileExists(SyscallInterface& syscall, const std::string& path_name) {
  return syscall.Access(path_name, F_OK) != -1;
}

}  // namespace dittosuite
