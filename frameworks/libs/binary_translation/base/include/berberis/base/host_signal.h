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

#ifndef BERBERIS_BASE_HOST_SIGNAL_H_
#define BERBERIS_BASE_HOST_SIGNAL_H_

#include <signal.h>
#include <sys/syscall.h>

#include "berberis/base/checks.h"

namespace berberis {

#if defined(__BIONIC__) && !defined(__LP64__)

using HostSigset = sigset64_t;
using HostStructSigaction = struct sigaction64;

#define HostSigaddset sigaddset64
#define HostSigfillset sigfillset64
#define HostSigaction sigaction64

#else

using HostSigset = sigset_t;
using HostStructSigaction = struct sigaction;

#define HostSigaddset sigaddset
#define HostSigfillset sigfillset
#define HostSigaction sigaction

#endif

// Use syscall to avoid libc (filters out TIMER) or libsigchain (filters out SIGSEGV).
inline void RTSigprocmaskSyscallOrDie(int how, const HostSigset* new_set, HostSigset* old_set) {
  // Note that we cannot use sizeof(HostSigset) as the last argument here since
  // Glibc's sizeof(sigset_t) is 128 bytes, while kernel only supports 8 bytes.
  long res = syscall(SYS_rt_sigprocmask, how, new_set, old_set, _NSIG / 8);
  // CHECK_EQ() formatting doesn't support long.
  CHECK(res == 0);
}

}  // namespace berberis

#endif  // BERBERIS_BASE_HOST_SIGNAL_H_
