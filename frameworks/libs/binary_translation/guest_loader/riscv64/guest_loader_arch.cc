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

#include <sys/auxv.h>
#include <cstdint>

#include "berberis/base/bit_util.h"  // AlignDown
#include "berberis/calling_conventions/calling_conventions_riscv64.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/kernel_api/exec_emulation.h"  // DemangleGuestEnvp

namespace berberis {

// TODO(b/279068747): Ensure these paths are correct.
// Paths required by guest_loader_impl.h.
const char* kAppProcessPath = "/system/bin/riscv64/app_process64";
const char* kPtInterpPath = "/system/bin/riscv64/linker64";
const char* kVdsoPath = "/system/lib64/riscv64/libnative_bridge_vdso.so";
const char* kProxyPrefix = "libberberis_proxy_";

GuestAddr InitKernelArgs(GuestAddr guest_sp,
                         size_t argc,
                         const char* argv[],
                         char* envp[],
                         GuestAddr linker_base_addr,
                         GuestAddr main_executable_entry_point,
                         GuestAddr phdr,
                         size_t phdr_count,
                         GuestAddr ehdr_vdso,
                         const uint8_t (*random_bytes)[16]) {
  // TODO(b/119329323): Provide meaningful values for disabled arguments.
  const uint64_t auxv[] = {
      // AT_HWCAP,        kRiscv64ValueHwcap,
      // AT_HWCAP2,       kRiscv64ValueHwcap2,
      AT_RANDOM,       ToGuestAddr(random_bytes),
      AT_SECURE,       false,
      AT_BASE,         linker_base_addr,
      AT_PHDR,         phdr,
      AT_PHNUM,        phdr_count,
      AT_ENTRY,        main_executable_entry_point,
      AT_PAGESZ,       static_cast<uint64_t>(sysconf(_SC_PAGESIZE)),
      AT_CLKTCK,       static_cast<uint64_t>(sysconf(_SC_CLK_TCK)),
      AT_SYSINFO_EHDR, ehdr_vdso,
      AT_UID,          getuid(),
      AT_EUID,         geteuid(),
      AT_GID,          getgid(),
      AT_EGID,         getegid(),
      AT_NULL,
  };

  size_t envp_count = 0;  // number of environment variables + nullptr
  while (envp[envp_count++] != nullptr) {
  }

  guest_sp -= sizeof(uint64_t) +               // argc
              sizeof(uint64_t) * (argc + 1) +  // argv + nullptr
              sizeof(uint64_t) * envp_count +  // envp + nullptr
              sizeof(auxv);                    // auxv
  guest_sp = AlignDown(guest_sp, riscv64::CallingConventions::kStackAlignmentBeforeCall);

  uint64_t* curr = ToHostAddr<uint64_t>(guest_sp);

  // argc
  *curr++ = argc;

  // argv
  for (size_t i = 0; i < argc; ++i) {
    *curr++ = reinterpret_cast<uint64_t>(argv[i]);
  }
  *curr++ = kNullGuestAddr;

  // envp
  curr = reinterpret_cast<uint64_t*>(DemangleGuestEnvp(reinterpret_cast<char**>(curr), envp));

  // auxv
  memcpy(curr, auxv, sizeof(auxv));

  return guest_sp;
}

}  // namespace berberis