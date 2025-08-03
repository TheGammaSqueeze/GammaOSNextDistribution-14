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

// Generic implementation that relies on guest arch-specific headers. This file must be compiled
// separately for each guest architecture.

#include "berberis/guest_os_primitives/guest_signal.h"

#include "berberis/base/host_signal.h"
#include "berberis/base/tracing.h"
#include "berberis/guest_abi/guest_call.h"
#include "berberis/guest_os_primitives/guest_signal_arch.h"
#include "berberis/guest_os_primitives/guest_thread.h"

#include "guest_context_arch.h"
#include "scoped_signal_blocker.h"

namespace berberis {

void ProcessGuestSignal(GuestThread* thread, const Guest_sigaction* sa, Guest_siginfo_t* info) {
  // ATTENTION: action mask is ADDED to currently blocked signals!
  // Should be no-op if invoked from HandleHostSignal, as it must run under guest action mask!
  HostSigset block_mask;
  ConvertToBigSigset(sa->sa_mask, &block_mask);
  if ((sa->sa_flags & SA_NODEFER) == 0u) {
    HostSigaddset(&block_mask, info->si_signo);
  }
  ScopedSignalBlocker signal_blocker(&block_mask);

  // Save state to ucontext.
  ThreadState* state = thread->state();
  GuestContext ctx;
  ctx.Save(&state->cpu);

  // Switch to alternate stack.
  if (sa->sa_flags & SA_ONSTACK) {
    thread->SwitchToSigAltStack();
  }

  TRACE("delivering signal %d at %p", info->si_signo, ToHostAddr<void>(sa->guest_sa_sigaction));
  // We get here only if guest set a custom signal action, default actions are handled by host.
  CHECK_NE(sa->guest_sa_sigaction, Guest_SIG_DFL);
  CHECK_NE(sa->guest_sa_sigaction, Guest_SIG_IGN);
  CHECK_NE(sa->guest_sa_sigaction, Guest_SIG_ERR);
  // Run guest signal handler. Assume this is
  //   void (*sa_sigaction)(int, siginfo_t*, void*);
  // If this is actually
  //   void (*sa_handler)(int);
  // then extra args will be just ignored.
  GuestCall guest_call;
  guest_call.AddArgInt32(info->si_signo);
  guest_call.AddArgGuestAddr(ToGuestAddr(info));
  guest_call.AddArgGuestAddr(ToGuestAddr(ctx.ptr()));
  guest_call.RunVoid(sa->guest_sa_sigaction);
  TRACE("signal %d delivered", info->si_signo);

  // Restore state from ucontext, it may be updated by the handler.
  ctx.Restore(&state->cpu);
}

}  // namespace berberis
