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

#include "berberis/kernel_api/open_emulation.h"

#include <fcntl.h>

#include "berberis/kernel_api/main_executable_real_path_emulation.h"

namespace berberis {

int OpenatForGuest(int dirfd, const char* path, int guest_flags, mode_t mode) {
  int host_flags = ToHostOpenFlags(guest_flags);

  const char* real_path = nullptr;
  if ((host_flags & AT_SYMLINK_NOFOLLOW) == 0) {
    real_path = TryReadLinkToMainExecutableRealPath(path);
  }

  return openat(dirfd, real_path ? real_path : path, host_flags, mode);
}

int OpenForGuest(const char* path, int flags, mode_t mode) {
  return OpenatForGuest(AT_FDCWD, path, flags, mode);
}

}  // namespace berberis
