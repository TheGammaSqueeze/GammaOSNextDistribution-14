/*
 * Copyright (C) 2024 The Android Open Source Project
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


#include <android-base/logging.h>
#include <android-base/unique_fd.h>
#include <android-base/file.h>
#include <sys/sendfile.h>

#include "aconfigd_util.h"

using ::android::base::Result;
using ::android::base::Error;
using ::android::base::ErrnoError;

namespace android {
namespace aconfigd {

/// Copy file
Result<void> CopyFile(const std::string& src, const std::string& dst) {
  android::base::unique_fd src_fd(
      TEMP_FAILURE_RETRY(open(src.c_str(), O_RDONLY | O_NOFOLLOW | O_CLOEXEC)));
  if (src_fd == -1) {
    return ErrnoError() << "open() failed for " << src;
  }

  android::base::unique_fd dst_fd(TEMP_FAILURE_RETRY(
      open(dst.c_str(), O_WRONLY | O_CREAT | O_NOFOLLOW | O_TRUNC | O_CLOEXEC, 0644)));
  if (dst_fd == -1) {
    return ErrnoError() << "open() failed for " << dst;
  }

  struct stat st;
  if (fstat(src_fd.get(), &st) == -1) {
    return ErrnoError() << "fstat() failed";
  }
  auto len = st.st_size;

  if (sendfile(dst_fd, src_fd, nullptr, len) == -1) {
    return ErrnoError() << "sendfile() failed";
  }

  return {};
}

/// Get a file's timestamp
Result<int> GetFileTimeStamp(const std::string& file) {
  struct stat st;
  int result = stat(file.c_str(), &st);
  if (result == -1) {
    return ErrnoError() << "stat() failed";
  }
  return static_cast<int>(st.st_mtim.tv_sec);
}

bool FileExists(const std::string& file) {
  struct stat st;
  return stat(file.c_str(), &st) == 0 ? true : false;
}

} // namespace aconfig
} // namespace android
