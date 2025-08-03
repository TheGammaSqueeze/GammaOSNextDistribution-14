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

#include <sys/syscall.h>

#include "berberis/base/checks.h"
#include "berberis/base/tracing.h"
#include "berberis/guest_os_primitives/scoped_pending_signals.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state_opaque.h"

namespace berberis {

// ATTENTION: this symbol gets called directly, without PLT. To keep text
// sharable we should prevent preemption of this symbol, so do not export it!
// TODO(b/232598137): may be set default visibility to protected instead?
extern "C" __attribute__((used, __visibility__("hidden"))) void berberis_HandleNoExec(
    ThreadState* state) {
  CHECK(state);
  // We are about to raise SIGSEGV. Let guest handler (if any) run immediately.
  // It's safe since guest state is synchronized here. More context at b/143786256.
  ScopedPendingSignalsDisabler disable_pending_signals(GetGuestThread(*state));
  // LR register is usually useful even if we came here via jump instead of
  // call because compilers rarely use LR for general-purpose calculations.
  CPUState& cpu = GetCPUState(*state);
  TRACE("Trying to execute non-executable code at %p called from %p",
        ToHostAddr<void>(GetInsnAddr(cpu)),
        ToHostAddr<void>(GetLinkRegister(cpu)));
  siginfo_t info{};
  info.si_signo = SIGSEGV;
  info.si_code = SEGV_ACCERR;
  info.si_addr = ToHostAddr<void>(GetInsnAddr(cpu));
  syscall(SYS_rt_tgsigqueueinfo, GetpidSyscall(), GettidSyscall(), SIGSEGV, &info);
}

}  // namespace berberis
