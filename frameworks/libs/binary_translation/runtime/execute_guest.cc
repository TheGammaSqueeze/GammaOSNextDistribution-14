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

#include "berberis/runtime/execute_guest.h"

#include "berberis/base/checks.h"
#include "berberis/base/config.h"
#include "berberis/base/tracing.h"
#include "berberis/guest_os_primitives/guest_thread.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/runtime_primitives/runtime_library.h"
#include "berberis/runtime_primitives/translation_cache.h"

namespace berberis {

void ExecuteGuest(ThreadState* state) {
  CHECK(state);
  GuestThread* thread = GetGuestThread(*state);
  CHECK(thread);
  CHECK_EQ(state, thread->state());

  TranslationCache* cache = TranslationCache::GetInstance();

  for (;;) {
    auto pc = GetInsnAddr(GetCPUState(*state));

    if (ArePendingSignalsPresent(*state)) {
      thread->ProcessPendingSignals();
      // Signal handler can modify control flow, e.g. to recover from segfault.
      if (pc != GetInsnAddr(GetCPUState(*state))) {
        TRACE("PC modified by signal handler: old=%p new=%p",
              ToHostAddr<void>(pc),
              ToHostAddr<void>(GetInsnAddr(GetCPUState(*state))));
        pc = GetInsnAddr(GetCPUState(*state));
      }
    }

    auto code = cache->GetHostCodePtr(pc)->load();
    if (code == kEntryStop) {
      break;
    }

    if (config::kTraceGeneratedCode) {
      TRACE("RunGeneratedCode @ 0x%zx", pc);
    }

    // ATTENTION: this should be the only place to run translated code!
    berberis_RunGeneratedCode(state, code);
  }
}

}  // namespace berberis
