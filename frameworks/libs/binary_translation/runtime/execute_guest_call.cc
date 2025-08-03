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

#include "berberis/runtime_primitives/runtime_library.h"

#include <csetjmp>

#include "berberis/base/checks.h"
#include "berberis/base/logging.h"
#include "berberis/base/tracing.h"
#include "berberis/guest_os_primitives/guest_thread.h"
#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/runtime/execute_guest.h"

namespace berberis {

void ExecuteGuestCall(ThreadState* state) {
  CHECK(state);
  auto* thread = GetGuestThread(*state);
  GuestCallExecution guest_call_execution{.parent = thread->guest_call_execution(),
                                          .sp = GetStackRegister(GetCPUState(*state))};

  // ATTENTION: don't save/restore signal mask, this is done by guest!
  sigsetjmp(guest_call_execution.buf, 0);
  // Set current execution for normal flow, or reset it after a longjmp.
  thread->set_guest_call_execution(&guest_call_execution);

  ExecuteGuest(state);

  thread->set_guest_call_execution(guest_call_execution.parent);

  if (guest_call_execution.sp == GetStackRegister(GetCPUState(*state))) {
    return;
  }

  // Stack pointer may not be restored if guest executed a statically linked longjmp.
  // Search parent executions for current sp.
  for (auto* curr = thread->guest_call_execution(); curr; curr = curr->parent) {
    // TODO(b/232598137): It'd be more reliable to also check (stop_pc ==
    // insn_addr) for the matching execution, but currently stop_pc is always the
    // same for all executions.
    if (curr->sp == GetStackRegister(GetCPUState(*state))) {
      TRACE("Detected statically linked longjmp");
      siglongjmp(curr->buf, 1);
    }
  }

  LOG_ALWAYS_FATAL("Guest call didn't restore sp: expected %p, actual %p",
                   ToHostAddr<void>(guest_call_execution.sp),
                   ToHostAddr<void>(GetStackRegister(GetCPUState(*state))));
}

}  // namespace berberis
