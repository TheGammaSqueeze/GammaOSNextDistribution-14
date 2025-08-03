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

#include "berberis/runtime_primitives/recovery_code.h"

#include <cstdint>
#include <initializer_list>
#include <utility>

#include "berberis/base/checks.h"
#include "berberis/base/forever_map.h"
#include "berberis/base/tracing.h"
#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/runtime_primitives/code_pool.h"

namespace berberis {

namespace {

ForeverMap<uintptr_t, uintptr_t> g_recovery_map;
bool g_recovery_map_initialized_ = false;

uintptr_t FindExtraRecoveryCodeUnsafe(uintptr_t fault_addr) {
  CHECK(g_recovery_map_initialized_);
  auto it = g_recovery_map.find(fault_addr);
  if (it != g_recovery_map.end()) {
    return it->second;
  }
  return 0;
}

}  // namespace

void InitExtraRecoveryCodeUnsafe(
    std::initializer_list<std::pair<uintptr_t, uintptr_t>> fault_recovery_pairs) {
  CHECK(!g_recovery_map_initialized_);
  for (auto pair : fault_recovery_pairs) {
    g_recovery_map[pair.first] = pair.second;
  }
  g_recovery_map_initialized_ = true;
}

uintptr_t FindRecoveryCode(uintptr_t fault_addr, ThreadState* state) {
  uintptr_t recovery_addr;
  CHECK(state);
  // Only look up in CodePool if we are inside generated code (interrupted by a
  // signal). If a signal interrupts CodePool::Add then calling FindRecoveryCode
  // in this state can cause deadlock.
  if (GetResidence(*state) == kInsideGeneratedCode) {
    // TODO(b/228188293): we might need to traverse all code pool instances.
    recovery_addr = GetDefaultCodePoolInstance()->FindRecoveryCode(fault_addr);
    if (recovery_addr) {
      return recovery_addr;
    }
  }
  // Extra recovery code is in read-only mode after the init, so we don't need mutexes.
  // Note, that we cannot simply add extra recovery code to CodePool, since these
  // fault addresses may be outside of generated code (e.g. interpreter).
  recovery_addr = FindExtraRecoveryCodeUnsafe(fault_addr);
  if (recovery_addr) {
    TRACE("found recovery address outside of code pool");
  }
  return recovery_addr;
}

}  // namespace berberis
