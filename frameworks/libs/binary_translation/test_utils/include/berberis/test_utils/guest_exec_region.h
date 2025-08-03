/*
 * Copyright (C) 2020 The Android Open Source Project
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


#ifndef BERBERIS_TEST_UTILS_GUEST_EXEC_REGION_H_
#define BERBERIS_TEST_UTILS_GUEST_EXEC_REGION_H_

#include <string.h>
#include <sys/mman.h>

#include "berberis/guest_state/guest_addr.h"
#include "berberis/kernel_api/sys_mman_emulation.h"

namespace berberis {

// ATTENTION: do not free guest exec regions! Otherwise, we'll also need to clean references
// to these regions from translation cache, wrapper cache, etc.
template <typename T, size_t S>
GuestAddr MakeGuestExecRegion(const T (&guest_code)[S]) {
  void* res = MmapForGuest(nullptr,
                           sizeof guest_code,
                           PROT_READ | PROT_WRITE | PROT_EXEC,
                           MAP_PRIVATE | MAP_ANONYMOUS,
                           -1,
                           0);
  memcpy(res, guest_code, sizeof guest_code);
  return ToGuestAddr(res);
}

}  // namespace berberis

#endif  // BERBERIS_TEST_UTILS_GUEST_EXEC_REGION_H_
