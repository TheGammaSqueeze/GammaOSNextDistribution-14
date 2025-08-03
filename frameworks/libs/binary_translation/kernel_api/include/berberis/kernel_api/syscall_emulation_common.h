/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef BERBERIS_KERNEL_API_SYSCALL_EMULATION_COMMON_H_
#define BERBERIS_KERNEL_API_SYSCALL_EMULATION_COMMON_H_

#include <sys/syscall.h>
#include <sys/types.h>
#include <unistd.h>

#include <cerrno>

#include "berberis/base/bit_util.h"
#include "berberis/base/macros.h"
#include "berberis/kernel_api/exec_emulation.h"
#include "berberis/kernel_api/fcntl_emulation.h"
#include "berberis/kernel_api/open_emulation.h"
#include "berberis/kernel_api/sys_prctl_emulation.h"
#include "berberis/kernel_api/sys_ptrace_emulation.h"
#include "berberis/kernel_api/tracing.h"
#include "berberis/kernel_api/unistd_emulation.h"

namespace berberis {

inline long RunGuestSyscall___NR_clone3(long arg_1, long arg_2) {
  UNUSED(arg_1, arg_2);
  KAPI_TRACE("unimplemented syscall __NR_clone3");
  errno = ENOSYS;
  return -1;
}

inline long RunGuestSyscall___NR_execve(long arg_1, long arg_2, long arg_3) {
  return static_cast<long>(ExecveForGuest(bit_cast<const char*>(arg_1),     // filename
                                          bit_cast<char* const*>(arg_2),    // argv
                                          bit_cast<char* const*>(arg_3)));  // envp
}

inline long RunGuestSyscall___NR_faccessat(long arg_1, long arg_2, long arg_3) {
  // TODO(b/128614662): translate!
  KAPI_TRACE("unimplemented syscall __NR_faccessat, running host syscall as is");
  return syscall(__NR_faccessat, arg_1, arg_2, arg_3);
}

inline long RunGuestSyscall___NR_fcntl(long arg_1, long arg_2, long arg_3) {
  return GuestFcntl(arg_1, arg_2, arg_3);
}

inline long RunGuestSyscall___NR_openat(long arg_1, long arg_2, long arg_3, long arg_4) {
  return static_cast<long>(OpenatForGuest(static_cast<int>(arg_1),       // dirfd
                                          bit_cast<const char*>(arg_2),  // path
                                          static_cast<int>(arg_3),       // flags
                                          static_cast<mode_t>(arg_4)));  // mode
}

inline long RunGuestSyscall___NR_prctl(long arg_1, long arg_2, long arg_3, long arg_4, long arg_5) {
  return PrctlForGuest(arg_1, arg_2, arg_3, arg_4, arg_5);
}

inline long RunGuestSyscall___NR_ptrace(long arg_1, long arg_2, long arg_3, long arg_4) {
  return static_cast<long>(PtraceForGuest(static_cast<int>(arg_1),    // request
                                          static_cast<pid_t>(arg_2),  // pid
                                          bit_cast<void*>(arg_3),     // addr
                                          bit_cast<void*>(arg_4)));   // data
}

inline long RunGuestSyscall___NR_readlinkat(long arg_1, long arg_2, long arg_3, long arg_4) {
  return static_cast<long>(ReadLinkAtForGuest(static_cast<int>(arg_1),       // dirfd
                                              bit_cast<const char*>(arg_2),  // path
                                              bit_cast<char*>(arg_3),        // buf
                                              bit_cast<size_t>(arg_4)));     // buf_size
}

inline long RunGuestSyscall___NR_rt_sigreturn(long) {
  KAPI_TRACE("unsupported syscall __NR_rt_sigaction");
  errno = ENOSYS;
  return -1;
}

inline long RunGuestSyscall___NR_statx(long arg_1, long arg_2, long arg_3, long arg_4, long arg_5) {
#if defined(__NR_statx)
  // TODO(b/128614662): add struct statx layout checkers.
  return syscall(__NR_statx, arg_1, arg_2, arg_3, arg_4, arg_5);
#else
  UNUSED(arg_1, arg_2, arg_3, arg_4, arg_5);
  errno = ENOSYS;
  return -1;
#endif
}

long RunUnknownGuestSyscall(long guest_nr,
                            long arg_1,
                            long arg_2,
                            long arg_3,
                            long arg_4,
                            long arg_5,
                            long arg_6) {
  UNUSED(arg_1, arg_2, arg_3, arg_4, arg_5, arg_6);
  KAPI_TRACE("unknown syscall %ld", guest_nr);
  errno = ENOSYS;
  return -1;
}

}  // namespace berberis

#endif  // BERBERIS_KERNEL_API_SYSCALL_EMULATION_COMMON_H_
