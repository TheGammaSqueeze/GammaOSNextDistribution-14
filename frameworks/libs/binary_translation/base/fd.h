/*
 * Copyright (C) 2021 The Android Open Source Project
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

// File operations without libc. Most important is not touching thread-local errno.

#ifndef BERBERIS_BASE_FD_H_
#define BERBERIS_BASE_FD_H_

#include <linux/unistd.h>
#include <sys/mman.h>
#include <unistd.h>

#include "berberis/base/bit_util.h"
#include "berberis/base/logging.h"
#include "berberis/base/raw_syscall.h"

// glibc in prebuilts does not have memfd_create
#if defined(__linux__) && !defined(__NR_memfd_create)
#if defined(__x86_64__)
#define __NR_memfd_create 319
#elif defined(__i386__)
#define __NR_memfd_create 356
#endif  // defined(__i386__)
#define MFD_CLOEXEC 0x0001U
#endif  // defined(__linux__) && !defined(__NR_memfd_create)

namespace berberis {

inline int CreateMemfdOrDie(const char* name) {
  // Use MFD_CLOEXEC to avoid leaking the file descriptor to child processes.
  int fd = static_cast<int>(RawSyscall(__NR_memfd_create, bit_cast<long>(name), MFD_CLOEXEC));
  CHECK(fd >= 0);
  return fd;
}

inline void FtruncateOrDie(int fd, off64_t size) {
  // Call libc instead of syscall because we want 64 version and do not want to
  // do ifdefs for 32/64/glibc/bionic in order to get the correct one.
  CHECK_EQ(ftruncate64(fd, size), 0);
}

inline void WriteFullyOrDie(int fd, const void* data, size_t size) {
  auto* curr = reinterpret_cast<const uint8_t*>(data);
  auto* end = curr + size;
  while (curr < end) {
    auto written = RawSyscall(__NR_write, fd, bit_cast<long>(curr), end - curr);
    // It is not clear if write syscall can return 0 when writing more than 0 bytes.
    if (written >= 0) {
      curr += written;
    } else {
      CHECK(written == -EINTR);
    }
  }
}

inline void CloseUnsafe(int fd) {
  RawSyscall(__NR_close, fd);
}

}  // namespace berberis

#endif  // BERBERIS_BASE_FD_H_
