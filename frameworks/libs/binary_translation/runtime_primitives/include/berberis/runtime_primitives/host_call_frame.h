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

#ifndef BERBERIS_RUNTIME_PRIMITIVES_HOST_CALL_FRAME_H_
#define BERBERIS_RUNTIME_PRIMITIVES_HOST_CALL_FRAME_H_

#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state_opaque.h"

namespace berberis {

// Set state for calling guest function at given pc (except arguments passing).
// Restore previous state after guest function returns.
//
// Assume we have some meaningful guest state, for example, at trampoline or at signal handler call.
// We want to call nested guest function, for example, callback passed into trampoline or guest
// signal handler. We want to restore the state after guest nested function returns.
//
// Assume guest function to be called conforms to procedure calling standard. In particular, it is
// expected to preserve caller-saved registers, and to return by jumping to a given return address.
//
// Assume we want to allow guest unwinder to unwind to the previous guest state. For that, we should
// only save state into guest accessible memory - namely, into guest stack/registers.
//
// First, we want guest execution to stop when guest function returns. For that, we provide special
// return address that is treated as stop by dispatcher.
//
// Next, parameters are passed afterwards, so at this point we don't know how much stack they will
// need. To restore stack after the call, we need to save current stack pointer in a caller-saved
// register.
//
// Finally, we need to save the registers that are not preserved by guest function.
class ScopedHostCallFrame {
 public:
  ScopedHostCallFrame(CPUState* cpu, GuestAddr pc);
  ~ScopedHostCallFrame();

  static void SetGuestPC(GuestAddr pc) { g_host_call_frame_guest_pc_ = pc; }

 private:
  static GuestAddr g_host_call_frame_guest_pc_;

  CPUState* cpu_;

  // For safety checks!
  GuestAddr stack_pointer_;
  GuestAddr link_register_;
  GuestAddr program_counter_;
};

// Set return address for guest calls. On this address, guest execution will stop.
void InitHostCallFrameGuestPC(GuestAddr pc);

}  // namespace berberis

#endif  // BERBERIS_RUNTIME_PRIMITIVES_HOST_CALL_FRAME_H_
