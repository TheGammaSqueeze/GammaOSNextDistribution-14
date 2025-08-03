/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "berberis/kernel_api/unistd_emulation.h"

#include <fcntl.h>  // AT_FDCWD, AT_SYMLINK_NOFOLLOW

#include <cerrno>
#include <cstring>  // memcpy

#include "berberis/kernel_api/main_executable_real_path_emulation.h"

namespace berberis {

ssize_t ReadLinkAtForGuest(int dirfd, const char* path, char* buf, size_t buf_size) {
  const char* real_path = TryReadLinkToMainExecutableRealPath(path);
  if (!real_path) {
    return readlinkat(dirfd, path, buf, buf_size);
  }
  // readlink have quite unusual semantic WRT handling of buffer length.  readlink should not add
  // terminating null byte if it doesn't fit in the buffer, so can't use strlcpy. readlink should
  // return the number of bytes placed in buffer thus strncpy doesn't help.
  size_t real_path_len = strlen(real_path);
  if (real_path_len + 1 > buf_size) {
    memcpy(buf, real_path, buf_size);
    return buf_size;
  }
  memcpy(buf, real_path, real_path_len + 1);
  return real_path_len;
}

ssize_t ReadLinkForGuest(const char* path, char* buf, size_t buf_size) {
  return ReadLinkAtForGuest(AT_FDCWD, path, buf, buf_size);
}

}  // namespace berberis
