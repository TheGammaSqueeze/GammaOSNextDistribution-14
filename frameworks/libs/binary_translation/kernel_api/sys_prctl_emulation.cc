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

#include "berberis/kernel_api/sys_prctl_emulation.h"

#include <linux/filter.h>
#include <linux/seccomp.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>

#include "berberis/base/bit_util.h"
#include "berberis/base/checks.h"
#include "berberis/kernel_api/syscall_numbers.h"

namespace berberis {

int PrctlForGuest(int option,
                  unsigned long arg2,
                  unsigned long arg3,
                  unsigned long arg4,
                  unsigned long arg5) {
  if (option == PR_SET_SECCOMP) {
    auto prog = bit_cast<struct sock_fprog*>(arg3);
    for (int i = 0; i < prog->len; i++) {
      struct sock_filter& filter = prog->filter[i];
      if (BPF_CLASS(filter.code) != BPF_JMP) {
        continue;
      }
      // TODO(b/110423578): Even if we block host syscall this may not block
      // emulated guest syscall.
      filter.k = ToHostSyscallNumber(filter.k);
      LOG_ALWAYS_FATAL_IF(filter.k == unsigned(-1),
                          "Unsupported guest syscall number in PR_SET_SECCOMP");
    }
  }

  return prctl(option, arg2, arg3, arg4, arg5);
}

}  // namespace berberis
