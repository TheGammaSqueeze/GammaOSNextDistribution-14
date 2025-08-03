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

#include "berberis/kernel_api/sys_ptrace_emulation.h"

#include <sys/ptrace.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>

#include "berberis/kernel_api/tracing.h"

namespace berberis {

int PtraceForGuest(int int_request, pid_t pid, void* addr, void* data) {
#if defined(__BIONIC__)
  using RequestType = int;
#elif defined(__GLIBC__)
  using RequestType = enum __ptrace_request;
#elif defined(ANDROID_HOST_MUSL)
  using RequestType = int;
#else
#error "Unsupported libc"
#endif

  auto request = static_cast<RequestType>(int_request);

  auto [processed, result] = PtraceForGuestArch(int_request, pid, addr, data);
  if (processed) {
    return result;
  }

  switch (int_request) {
    case PTRACE_TRACEME:
      return ptrace(PTRACE_TRACEME);
    case PTRACE_INTERRUPT:
    case PTRACE_ATTACH:
      return ptrace(request, pid, 0, 0);
    case PTRACE_SEIZE:
    case PTRACE_DETACH:
    case PTRACE_CONT:
    case PTRACE_SETOPTIONS:
      return ptrace(request, pid, 0, data);
    case PTRACE_PEEKDATA:
    case PTRACE_PEEKTEXT: {
      // ATTENTION: Syscall API for these calls is different from libc wrappers!
      // The syscall stores the requested value at *data, and returns error status
      // as the result.
      return syscall(__NR_ptrace, request, pid, addr, data);
    }
    case PTRACE_POKEDATA:
    case PTRACE_POKETEXT:
      return ptrace(request, pid, addr, data);
    case PTRACE_GETSIGINFO:
      KAPI_TRACE("not implemented: ptrace(PTRACE_GETSIGINFO, ...)");
      errno = EPERM;
      return -1;
    case PTRACE_GETREGSET:
      KAPI_TRACE("not implemented: ptrace(PTRACE_GETREGSET, ...)");
      if (data) {
        // Even in case of error, kernel sets iov_len to amount of data written.
        auto iov = reinterpret_cast<iovec*>(data);
        iov->iov_len = 0;
        errno = EINVAL;
      } else {
        errno = EFAULT;
      }
      return -1;
    case PTRACE_SETREGSET:
      KAPI_TRACE("not implemented: ptrace(PTRACE_SETREGSET, ...)");
      errno = EINVAL;
      return -1;
    default:
      KAPI_TRACE("not implemented: ptrace(0x%x, ...)", request);
      errno = EPERM;
      return -1;
  }
}

}  // namespace berberis
