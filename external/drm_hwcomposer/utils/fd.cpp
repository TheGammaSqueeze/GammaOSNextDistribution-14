/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "fd.h"

namespace android {

static void CloseFd(const int *fd) {
  if (fd != nullptr) {
    if (*fd >= 0)
      close(*fd);

    // NOLINTNEXTLINE(cppcoreguidelines-owning-memory)
    delete fd;
  }
}

auto MakeUniqueFd(int fd) -> UniqueFd {
  if (fd < 0)
    return {nullptr, CloseFd};

  return {new int(fd), CloseFd};
}

auto MakeSharedFd(int fd) -> SharedFd {
  if (fd < 0)
    return {};

  return {new int(fd), CloseFd};
}

auto DupFd(SharedFd const &fd) -> int {
  if (!fd)
    return -1;

  return fcntl(*fd, F_DUPFD_CLOEXEC, 0);
}

}  // namespace android
