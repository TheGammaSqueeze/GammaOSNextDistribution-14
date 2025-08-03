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

#include <cstdint>
#include <cstring>

#include "berberis/calling_conventions/calling_conventions_riscv64.h"
#include "berberis/guest_abi/guest_arguments.h"
#include "berberis/guest_os_primitives/guest_thread_manager.h"
#include "berberis/guest_os_primitives/scoped_pending_signals.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state_arch.h"
#include "berberis/instrument/guest_call.h"
#include "berberis/runtime_primitives/host_call_frame.h"

namespace berberis {

void RunGuestCall(GuestAddr pc, GuestArgumentBuffer* buf) {
  GuestThread* thread = GetCurrentGuestThread();
  ThreadState* state = thread->state();

  ScopedPendingSignalsEnabler scoped_pending_signals_enabler(thread);

  ScopedHostCallFrame host_call_frame(&state->cpu, pc);

  // Copy argc int and fp_argc float registers for the arguments into the argument buffer.
  memcpy(&(state->cpu.x[A0]), buf->argv, buf->argc * sizeof(buf->argv[0]));
  memcpy(&(state->cpu.f[FA0]), buf->fp_argv, buf->fp_argc * sizeof(buf->fp_argv[0]));

  // sp -= stack_argc
  SetXReg<SP>(state->cpu, GetXReg<SP>(state->cpu) - buf->stack_argc);
  // sp = align_down(sp, ...)
  SetXReg<SP>(
      state->cpu,
      AlignDown(GetXReg<SP>(state->cpu), riscv64::CallingConventions::kStackAlignmentBeforeCall));

  memcpy(ToHostAddr<void>(GetXReg<SP>(state->cpu)), buf->stack_argv, buf->stack_argc);

  if (kInstrumentWrappers) {
    OnWrappedGuestCall(state, pc);
  }

  ExecuteGuestCall(state);

  if (kInstrumentWrappers) {
    OnWrappedGuestReturn(state, pc);
  }

  // Copy resc int and fp_resc float registers for the results from the buffer to the cpu state.
  memcpy(buf->argv, &(state->cpu.x[A0]), buf->resc * sizeof(buf->argv[0]));
  memcpy(buf->fp_argv, &(state->cpu.f[FA0]), buf->fp_resc * sizeof(buf->fp_argv[0]));
}

}  // namespace berberis
