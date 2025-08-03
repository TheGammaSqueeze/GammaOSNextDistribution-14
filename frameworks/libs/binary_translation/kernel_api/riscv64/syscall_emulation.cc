/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <fcntl.h>  // AT_FDCWD, AT_SYMLINK_NOFOLLOW
#include <linux/unistd.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <cerrno>

#include "berberis/base/macros.h"
#include "berberis/base/scoped_errno.h"
#include "berberis/base/struct_check.h"
#include "berberis/base/tracing.h"
#include "berberis/guest_state/guest_state.h"
#include "berberis/kernel_api/main_executable_real_path_emulation.h"
#include "berberis/kernel_api/syscall_emulation_common.h"
#include "berberis/kernel_api/tracing.h"
#include "berberis/runtime_primitives/runtime_library.h"

#include "epoll_emulation.h"
#include "guest_types.h"
#include "runtime_bridge.h"

namespace berberis {

namespace {

void ConvertHostStatToGuest(const struct stat& host_stat, Guest_stat* guest_stat) {
  guest_stat->st_dev = host_stat.st_dev;
  guest_stat->st_ino = host_stat.st_ino;
  guest_stat->st_mode = host_stat.st_mode;
  guest_stat->st_nlink = host_stat.st_nlink;
  guest_stat->st_uid = host_stat.st_uid;
  guest_stat->st_gid = host_stat.st_gid;
  guest_stat->st_rdev = host_stat.st_rdev;
  guest_stat->st_size = host_stat.st_size;
  guest_stat->st_blksize = host_stat.st_blksize;
  guest_stat->st_blocks = host_stat.st_blocks;
  guest_stat->st_blocks = host_stat.st_blocks;
  guest_stat->st_atim = host_stat.st_atim;
  guest_stat->st_mtim = host_stat.st_mtim;
  guest_stat->st_ctim = host_stat.st_ctim;
}

int FstatatForGuest(int dirfd, const char* path, struct stat* buf, int flags) {
  const char* real_path = nullptr;
  if ((flags & AT_SYMLINK_NOFOLLOW) == 0) {
    real_path = TryReadLinkToMainExecutableRealPath(path);
  }
  return syscall(__NR_newfstatat, dirfd, real_path ? real_path : path, buf, flags);
}

long RunGuestSyscall___NR_execveat(long arg_1, long arg_2, long arg_3, long arg_4, long arg_5) {
  UNUSED(arg_1, arg_2, arg_3, arg_4, arg_5);
  KAPI_TRACE("unimplemented syscall __NR_execveat");
  errno = ENOSYS;
  return -1;
}

long RunGuestSyscall___NR_fadvise64(long arg_1, long arg_2, long arg_3, long arg_4) {
  // on 64-bit architectures, sys_fadvise64 and sys_fadvise64_64 are equal.
  return syscall(__NR_fadvise64, arg_1, arg_2, arg_3, arg_4);
}

long RunGuestSyscall___NR_fstat(long arg_1, long arg_2) {
  struct stat host_stat;
  long result = syscall(__NR_fstat, arg_1, &host_stat);
  if (result != -1) {
    ConvertHostStatToGuest(host_stat, bit_cast<Guest_stat*>(arg_2));
  }
  return result;
}

long RunGuestSyscall___NR_ioctl(long arg_1, long arg_2, long arg_3) {
  // TODO(b/128614662): translate!
  KAPI_TRACE("unimplemented ioctl 0x%lx, running host syscall as is", arg_2);
  return syscall(__NR_ioctl, arg_1, arg_2, arg_3);
}

long RunGuestSyscall___NR_newfstatat(long arg_1, long arg_2, long arg_3, long arg_4) {
  struct stat host_stat;
  int result = FstatatForGuest(static_cast<int>(arg_1),       // dirfd
                               bit_cast<const char*>(arg_2),  // path
                               &host_stat,
                               static_cast<int>(arg_4));  // flags
  if (result != -1) {
    ConvertHostStatToGuest(host_stat, bit_cast<Guest_stat*>(arg_3));
  }
  return result;
}

long RunGuestSyscall___NR_riscv_flush_icache(long arg_1, long arg_2, long arg_3) {
  static constexpr uint64_t kFlagsLocal = 1UL;
  static constexpr uint64_t kFlagsAll = kFlagsLocal;

  // ATTENTION: On RISC-V, arg_2 is the address range end, not the address range size.
  auto start = bit_cast<GuestAddr>(arg_1);
  auto end = bit_cast<GuestAddr>(arg_2);
  auto flags = bit_cast<uint64_t>(arg_3);
  if (end < start || (flags & ~kFlagsAll) != 0) {
    errno = EINVAL;
    return -1;
  }

  // Ignore kFlagsLocal because we do not have a per-thread cache to clear.
  TRACE("icache flush: [0x%lx, 0x%lx)", start, end);
  InvalidateGuestRange(start, end);
  return 0;
}

// RunGuestSyscallImpl.
#include "gen_syscall_emulation_riscv64_to_x86_64-inl.h"

}  // namespace

long RunGuestSyscall(long syscall_nr,
                     long arg0,
                     long arg1,
                     long arg2,
                     long arg3,
                     long arg4,
                     long arg5) {
  ScopedErrno scoped_errno;

  // RISCV Linux takes arguments in a0-a5 and syscall number in a7.
  long result = RunGuestSyscallImpl(syscall_nr, arg0, arg1, arg2, arg3, arg4, arg5);
  // The result is returned in a0.
  if (result == -1) {
    return -errno;
  } else {
    return result;
  }
}

void RunKernelSyscall(ThreadState* state) {
  RunGuestSyscall(state->cpu.x[A7],
                  state->cpu.x[A0],
                  state->cpu.x[A1],
                  state->cpu.x[A2],
                  state->cpu.x[A3],
                  state->cpu.x[A4],
                  state->cpu.x[A5]);
}

}  // namespace berberis
