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

// We need 32-bit functions here.  Suppress unconditional use of 64-bit offsets.
// Functions with 64-bit offsets are still available when used with "64" suffix.
//
// Note: this is actually only needed for host build since Android build system
// insists on defining _FILE_OFFSET_BITS=64 for host-host binaries.
//
// _FILE_OFFSET_BITS is NOT defined when we are building target-host binaries.
#ifdef _FILE_OFFSET_BITS
#undef _FILE_OFFSET_BITS
#endif

#include "berberis/kernel_api/fcntl_emulation.h"

#include <fcntl.h>
#include <sys/file.h>

#include <cerrno>

#include "berberis/base/checks.h"
#include "berberis/kernel_api/open_emulation.h"
#include "berberis/kernel_api/tracing.h"

static_assert(F_DUPFD == 0);
static_assert(F_GETFD == 1);
static_assert(F_SETFD == 2);
static_assert(F_GETFL == 3);
static_assert(F_SETFL == 4);
static_assert(F_SETOWN == 8);
static_assert(F_GETOWN == 9);
static_assert(F_SETSIG == 10);
static_assert(F_GETSIG == 11);
static_assert(F_SETOWN_EX == 15);
static_assert(F_GETOWN_EX == 16);
static_assert(F_OWNER_TID == 0);
static_assert(F_OWNER_PID == 1);
static_assert(F_OWNER_PGRP == 2);
static_assert(F_RDLCK == 0);
static_assert(F_WRLCK == 1);
static_assert(F_UNLCK == 2);
#ifdef F_EXLCK
static_assert(F_EXLCK == 4);
#endif
#ifdef F_SHLCK
static_assert(F_SHLCK == 8);
#endif
static_assert(F_SETLEASE == 1024);
static_assert(F_GETLEASE == 1025);
static_assert(F_NOTIFY == 1026);

#if !defined(ANDROID_HOST_MUSL)
static_assert(F_GETLK == 5);
static_assert(F_SETLK == 6);
static_assert(F_SETLKW == 7);
#endif

#define GUEST_F_GETLK 5
#define GUEST_F_SETLK 6
#define GUEST_F_SETLKW 7

#if defined(ANDROID_HOST_MUSL)
// Musl only has a 64-bit flock that it uses for flock and flock64.

struct Guest_flock {
  int16_t l_type;
  int16_t l_whence;
  int32_t l_start;
  int32_t l_len;
  int32_t l_pid;
};

const struct flock64* ConvertGuestFlockToHostFlock64(const Guest_flock* guest,
                                                     struct flock64* host) {
  if (!guest) {
    return nullptr;
  }
  *host = {guest->l_type, guest->l_whence, guest->l_start, guest->l_len, guest->l_pid};
  return host;
}

void ConvertHostFlock64ToGuestFlock(const struct flock64* host, Guest_flock* guest) {
  CHECK_NE(guest, nullptr);
  CHECK_LE(host->l_start, INT32_MAX);
  CHECK_GE(host->l_start, INT32_MIN);
  CHECK_LE(host->l_len, INT32_MAX);
  CHECK_GE(host->l_len, INT32_MIN);
  *guest = {host->l_type,
            host->l_whence,
            static_cast<int32_t>(host->l_start),
            static_cast<int32_t>(host->l_len),
            host->l_pid};
}
#endif

namespace berberis {

int GuestFcntl(int fd, int cmd, long arg_3) {
  auto [processed, result] = GuestFcntlArch(fd, cmd, arg_3);
  if (processed) {
    return result;
  }

  switch (cmd) {
    case F_GETFD:
    case F_GETOWN:
    case F_GETSIG:
    case F_GETLEASE:
      return fcntl(fd, cmd);
    case F_GETFL: {
      auto result = fcntl(fd, cmd);
      if (result < 0) {
        return result;
      }
      return ToGuestOpenFlags(result);
    }
    case F_DUPFD:
    case F_DUPFD_CLOEXEC:
    case F_SETFD:
    case F_SETOWN:
    case F_SETSIG:
    case F_SETLEASE:
    case F_NOTIFY:
    case F_GETOWN_EX:
    case F_SETOWN_EX:
#if defined(F_ADD_SEALS)
    case F_ADD_SEALS:
#endif
#if defined(F_GET_SEALS)
    case F_GET_SEALS:
#endif
    case GUEST_F_SETLK:
    case GUEST_F_SETLKW:
    case GUEST_F_GETLK:
#if defined(ANDROID_HOST_MUSL)
    {
      // Musl only has a 64-bit flock for both flock and flock64, translate flock calls to flock64.
      Guest_flock* guest_flock = reinterpret_cast<Guest_flock*>(arg_3);
      struct flock64 host_flock64;
      // In case of GETLK input flock describes region
      // to check, thus conversion is also required.
      auto result = fcntl(fd,
                          cmd + F_SETLK - GUEST_F_SETLK,
                          ConvertGuestFlockToHostFlock64(guest_flock, &host_flock64));
      if (result == 0 && cmd == GUEST_F_GETLK) {
        // Output contains the result of lock check.
        ConvertHostFlock64ToGuestFlock(&host_flock64, guest_flock);
      }
      return result;
    }
#else
      // struct flock compatibility is checked above.
      return fcntl(fd, cmd, arg_3);
#endif
    case F_SETFL:
      return fcntl(fd, cmd, ToHostOpenFlags(arg_3));
    default:
      KAPI_TRACE("Unknown fcntl command: %d", cmd);
      errno = ENOSYS;
      return -1;
  }
}

}  // namespace berberis
