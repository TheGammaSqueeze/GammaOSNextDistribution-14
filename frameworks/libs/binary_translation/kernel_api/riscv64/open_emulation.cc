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

// Documentation says that to get access to the constants used below one
// must include these three files.  In reality it looks as if all constants
// are defined by <fcntl.h>, but we include all three as described in docs.
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>

#include "berberis/kernel_api/tracing.h"

#define GUEST_O_DIRECTORY 00040000
#define GUEST_O_NOFOLLOW 00100000
#define GUEST_O_DIRECT 00200000
#define GUEST_O_LARGEFILE 00400000

namespace berberis {

#if !defined(__i386__) && !defined(__x86_64__)
#error Currently open flags conversion is only supported on x86
#endif

// Glibc doesn't expose __O_SYNC
#if !defined(__O_SYNC)

#if defined(__BIONIC__)
#error __O_SYNC undefined in bionic
#endif

#define __O_SYNC 04000000

#endif

// Musl defines an O_SEARCH flag an includes it in O_ACCMODE,
// bionic and glibc don't.
#ifndef O_SEARCH
#define O_SEARCH 0
#endif

static_assert((O_ACCMODE & ~O_SEARCH) == 00000003);

// These flags should have the same value on all architectures.
static_assert(O_CREAT == 00000100);
static_assert(O_EXCL == 00000200);
static_assert(O_NOCTTY == 00000400);
static_assert(O_TRUNC == 00001000);
static_assert(O_APPEND == 00002000);
static_assert(O_NONBLOCK == 00004000);
static_assert(O_DSYNC == 00010000);
static_assert(FASYNC == 00020000);
static_assert(O_NOATIME == 01000000);
static_assert(O_CLOEXEC == 02000000);
static_assert(__O_SYNC == 04000000);
static_assert(O_SYNC == (O_DSYNC | __O_SYNC));
static_assert(O_PATH == 010000000);

namespace {

const int kCompatibleOpenFlags = O_ACCMODE | O_CREAT | O_EXCL | O_NOCTTY | O_TRUNC | O_APPEND |
                                 O_NONBLOCK | O_DSYNC | FASYNC | O_NOATIME | O_CLOEXEC | __O_SYNC |
                                 O_PATH;

}  // namespace

int ToHostOpenFlags(int guest_flags) {
  const int kIncompatibleGuestOpenFlags =
      GUEST_O_DIRECTORY | GUEST_O_NOFOLLOW | GUEST_O_DIRECT | GUEST_O_LARGEFILE;

  int unknown_guest_flags = guest_flags & ~(kCompatibleOpenFlags | kIncompatibleGuestOpenFlags);
  if (unknown_guest_flags) {
    KAPI_TRACE("Unsupported guest open flags: original=0x%x unsupported=0x%x",
               guest_flags,
               unknown_guest_flags);
  }

  int host_flags = guest_flags & ~kIncompatibleGuestOpenFlags;

  if (guest_flags & GUEST_O_DIRECTORY) {
    host_flags |= O_DIRECTORY;
  }
  if (guest_flags & GUEST_O_NOFOLLOW) {
    host_flags |= O_NOFOLLOW;
  }
  if (guest_flags & GUEST_O_DIRECT) {
    host_flags |= O_DIRECT;
  }
  if (guest_flags & GUEST_O_LARGEFILE) {
    host_flags |= O_LARGEFILE;
  }

  return host_flags;
}

int ToGuestOpenFlags(int host_flags) {
  const int kIncompatibleHostOpenFlags = O_DIRECTORY | O_NOFOLLOW | O_DIRECT | O_LARGEFILE;

  int unknown_host_flags = host_flags & ~(kCompatibleOpenFlags | kIncompatibleHostOpenFlags);
  if (unknown_host_flags) {
    KAPI_TRACE("Unsupported host open flags: original=0x%x unsupported=0x%x",
               host_flags,
               unknown_host_flags);
  }

  int guest_flags = host_flags & ~kIncompatibleHostOpenFlags;

  if (host_flags & O_DIRECTORY) {
    guest_flags |= GUEST_O_DIRECTORY;
  }
  if (host_flags & O_NOFOLLOW) {
    guest_flags |= GUEST_O_NOFOLLOW;
  }
  if (host_flags & O_DIRECT) {
    guest_flags |= GUEST_O_DIRECT;
  }
  if (host_flags & O_LARGEFILE) {
    guest_flags |= GUEST_O_LARGEFILE;
  }

  return guest_flags;
}

}  // namespace berberis
