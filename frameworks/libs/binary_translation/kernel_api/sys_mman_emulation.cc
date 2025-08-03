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

#include "berberis/kernel_api/sys_mman_emulation.h"

#include <sys/mman.h>

#include <cerrno>

#include "berberis/base/tracing.h"
#include "berberis/guest_os_primitives/guest_map_shadow.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

namespace {

int ToHostProt(int guest_prot) {
  if (guest_prot & PROT_EXEC) {
    // Guest EXEC should _not_ be host EXEC but should be host READ!
    return (guest_prot & ~PROT_EXEC) | PROT_READ;
  }
  return guest_prot;
}

void UpdateGuestProt(int guest_prot, void* addr, size_t length) {
  GuestAddr guest_addr = ToGuestAddr(addr);
  GuestMapShadow* shadow = GuestMapShadow::GetInstance();
  if (guest_prot & PROT_EXEC) {
    shadow->SetExecutable(guest_addr, length);
  } else {
    shadow->ClearExecutable(guest_addr, length);
  }
}

}  // namespace

// ATTENTION: the order of mmap/mprotect/munmap and SetExecutable/ClearExecutable is essential!
//
// The issue here is that threads might be executing the code being munmap'ed or mprotect'ed.
// SetExecutable/ClearExecutable should flush code cache and notify threads to restart.
// If other thread starts translation after actual mmap/mprotect/munmap but before xbit update,
// it might pick up an already obsolete code.

void* MmapForGuest(void* addr, size_t length, int prot, int flags, int fd, off64_t offset) {
  void* result = mmap64(addr, length, ToHostProt(prot), flags, fd, offset);
  if (result != MAP_FAILED) {
    UpdateGuestProt(prot, result, length);
  }
  return result;
}

int MunmapForGuest(void* addr, size_t length) {
  GuestMapShadow::GetInstance()->ClearExecutable(ToGuestAddr(addr), length);
  return munmap(addr, length);
}

int MprotectForGuest(void* addr, size_t length, int prot) {
  // In b/218772975 the app is scanning "/proc/self/maps" and tries to mprotect
  // mappings for some libraries found there (for unknown reason) effectively removing
  // execution permission. GuestMapShadow is pre-populated with such mappings, so we
  // suppress guest mprotect for them.
  if (GuestMapShadow::GetInstance()->IntersectsWithProtectedMapping(
          addr, static_cast<char*>(addr) + length)) {
    TRACE("Suppressing guest mprotect(%p, %zu) on a mapping protected from guest", addr, length);
    errno = EACCES;
    return -1;
  }

  UpdateGuestProt(prot, addr, length);
  return mprotect(addr, length, ToHostProt(prot));
}

void* MremapForGuest(void* old_addr, size_t old_size, size_t new_size, int flags, void* new_addr) {
  // As we drop xbit for host mmap calls, host mappings might differ from guest
  // mappings, and host mremap might work when guest mremap should not. Check in
  // advance to avoid that. Rules for checks:
  // 1. Shrink without MREMAP_FIXED - always Ok.
  // 2. Shrink with MREMAP_FIXED - needs consistent permissions within new_size.
  // 3. Grow - needs consistent permissions within old_size.
  GuestMapShadow* shadow = GuestMapShadow::GetInstance();
  if (new_size <= old_size) {
    if ((flags & MREMAP_FIXED) &&
        shadow->GetExecutable(ToGuestAddr(old_addr), new_size) == kBitMixed) {
      errno = EFAULT;
      return MAP_FAILED;
    }
  } else {
    if (shadow->GetExecutable(ToGuestAddr(old_addr), old_size) == kBitMixed) {
      errno = EFAULT;
      return MAP_FAILED;
    }
  }

  void* result = mremap(old_addr, old_size, new_size, flags, new_addr);

  if (result != MAP_FAILED) {
    shadow->RemapExecutable(ToGuestAddr(old_addr), old_size, ToGuestAddr(result), new_size);
  }
  return result;
}

}  // namespace berberis
