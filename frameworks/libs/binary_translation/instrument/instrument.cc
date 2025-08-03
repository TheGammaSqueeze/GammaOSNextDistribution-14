// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include <link.h>
#include <signal.h>
#include <sys/types.h>

#include "berberis/base/tracing.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/instrument/crash.h"
#include "berberis/instrument/exec.h"
#include "berberis/instrument/guest_call.h"
#include "berberis/instrument/guest_thread.h"
#include "berberis/instrument/loader.h"
#include "berberis/instrument/trampolines.h"

namespace berberis {

void OnConsistentLinkMap(const struct link_map* link) {
  int i = 0;
  for (; link; link = link->l_next) {
    TRACE("link_map[%d]: %p %s", i++, reinterpret_cast<void*>(link->l_addr), link->l_name);
  }
}

OnExecInsnFunc GetOnExecInsn([[maybe_unused]] GuestAddr pc) {
  return nullptr;
}

OnTrampolineFunc GetOnTrampolineCall([[maybe_unused]] const char* name) {
  return nullptr;
}

OnTrampolineFunc GetOnTrampolineReturn([[maybe_unused]] const char* name) {
  return nullptr;
}

void OnWrappedGuestCall([[maybe_unused]] ThreadState* state,
                        [[maybe_unused]] GuestAddr function_addr) {}

void OnWrappedGuestReturn([[maybe_unused]] ThreadState* state,
                          [[maybe_unused]] GuestAddr function_addr) {}

void OnSyscall([[maybe_unused]] ThreadState* state, [[maybe_unused]] long number) {}

void OnSyscallReturn([[maybe_unused]] ThreadState* state, [[maybe_unused]] long number) {}

void OnCrash([[maybe_unused]] int sig,
             [[maybe_unused]] siginfo_t* info,
             [[maybe_unused]] void* context) {}

void OnInsertGuestThread([[maybe_unused]] pid_t tid, [[maybe_unused]] GuestThread* thread) {}

void OnRemoveGuestThread([[maybe_unused]] pid_t tid, [[maybe_unused]] GuestThread* thread) {}

}  // namespace berberis
