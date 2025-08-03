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

#include <cstddef>
#include <tuple>
#include <utility>

#include "berberis/kernel_api/exec_emulation.h"
#include "berberis/kernel_api/fcntl_emulation.h"
#include "berberis/kernel_api/sys_ptrace_emulation.h"

namespace berberis {

std::pair<const char*, size_t> GetGuestPlatformVarPrefixWithSize() {
  static constexpr char kGuestPlatformVarPrefix[] = "BERBERIS_GUEST_";
  return {kGuestPlatformVarPrefix, sizeof(kGuestPlatformVarPrefix) - 1};
}

std::tuple<bool, int> GuestFcntlArch(int, int, long) {
  return {false, -1};
}

std::tuple<bool, int> PtraceForGuestArch(int, pid_t, void*, void*) {
  return {false, -1};
}

}  // namespace berberis
