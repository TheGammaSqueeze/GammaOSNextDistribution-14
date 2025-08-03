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

#include <atomic>
#include <csignal>
#include <mutex>

#if defined(__BIONIC__)
#include <platform/bionic/reserved_signals.h>
#endif

#include "berberis/base/checks.h"
#include "berberis/base/config_globals.h"
#include "berberis/base/tracing.h"
#include "berberis/guest_os_primitives/guest_signal.h"
#include "berberis/guest_os_primitives/guest_thread.h"
#include "berberis/guest_os_primitives/syscall_numbers.h"
#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/runtime_primitives/recovery_code.h"

#include "guest_signal_action.h"
#include "guest_thread_manager_impl.h"  // AttachCurrentThread, DetachCurrentThread
#include "scoped_signal_blocker.h"

// Glibc didn't define this macro for i386 and x86_64 at the moment of adding
// its use below. This condition still stands though.
#ifndef SI_FROMKERNEL
#define SI_FROMKERNEL(siptr) ((siptr)->si_code > 0)
#endif

namespace berberis {

namespace {

// Execution cannot proceed until the next pending signals check for _kernel_ sent
// synchronious signals: the faulty instruction will be executed again, leading
// to the infinite recursion. So crash immediately to simplify debugging.
//
// Note that a _user_ sent signal which is typically synchronious, such as SIGSEGV,
// can continue until pending signals check.
bool IsPendingSignalWithoutRecoveryCodeFatal(siginfo_t* info) {
  switch (info->si_signo) {
    case SIGSEGV:
    case SIGBUS:
    case SIGILL:
    case SIGFPE:
      return SI_FROMKERNEL(info);
    default:
      return false;
  }
}

GuestSignalAction g_signal_actions[Guest__KERNEL__NSIG];
std::mutex g_signal_actions_guard_mutex;

const Guest_sigaction* FindSignalHandler(int signal) {
  CHECK_GT(signal, 0);
  CHECK_LE(signal, Guest__KERNEL__NSIG);
  std::lock_guard<std::mutex> lock(g_signal_actions_guard_mutex);
  return &g_signal_actions[signal - 1].GetClaimedGuestAction();
}

// Can be interrupted by another HandleHostSignal!
void HandleHostSignal(int sig, siginfo_t* info, void* context) {
  TRACE("handle host signal %d", sig);

  bool attached;
  GuestThread* thread = AttachCurrentThread(false, &attached);

  // If pending signals are enabled, just add this signal to currently pending.
  // If pending signals are disabled, run handlers for currently pending signals
  // and for this signal now. While running the handlers, enable nested signals
  // to be pending.
  bool prev_pending_signals_enabled = thread->TestAndEnablePendingSignals();
  thread->SetSignalFromHost(*info);
  if (!prev_pending_signals_enabled) {
    CHECK_EQ(GetResidence(*thread->state()), kOutsideGeneratedCode);
    thread->ProcessAndDisablePendingSignals();
    if (attached) {
      DetachCurrentThread();
    }
  } else {
    // We can't make signals pendings as we need to detach the thread!
    CHECK(!attached);

#if defined(__i386__)
    constexpr size_t kHostRegIP = REG_EIP;
#elif defined(__x86_64__)
    constexpr size_t kHostRegIP = REG_RIP;
#else
#error "Unknown host arch"
#endif

    // Run recovery code to restore precise context and exit generated code.
    ucontext_t* ucontext = reinterpret_cast<ucontext_t*>(context);
    uintptr_t addr = ucontext->uc_mcontext.gregs[kHostRegIP];
    uintptr_t recovery_addr = FindRecoveryCode(addr, thread->state());

    if (recovery_addr) {
      if (!IsConfigFlagSet(kAccurateSigsegv)) {
        // We often get asynchronious signals at instructions with recovery code.
        // This is okay when the recovery is accurate, but highly fragile with inaccurate recovery.
        if (!IsPendingSignalWithoutRecoveryCodeFatal(info)) {
          TRACE("Skipping imprecise context recovery for non-fatal signal");
          TRACE("Guest signal handler suspended, continue");
          return;
        }
        TRACE(
            "Imprecise context at recovery, only guest pc is in sync."
            " Other registers may be stale.");
      }
      ucontext->uc_mcontext.gregs[kHostRegIP] = recovery_addr;
      TRACE("guest signal handler suspended, run recovery for host pc %p at host pc %p",
            reinterpret_cast<void*>(addr),
            reinterpret_cast<void*>(recovery_addr));
    } else {
      // Failed to find recovery code.
      // Translated code should be arranged to continue till
      // the next pending signals check unless it's fatal.
      if (IsPendingSignalWithoutRecoveryCodeFatal(info)) {
        LOG_ALWAYS_FATAL("Cannot process signal %d", sig);
      }
      TRACE("guest signal handler suspended, continue");
    }
  }
}

bool IsReservedSignal(int signal) {
  switch (signal) {
    // Disallow guest action for SIGABRT to simplify debugging (b/32167022).
    case SIGABRT:
#if defined(__BIONIC__)
    // Disallow overwriting the host profiler handler from guest code. Otherwise
    // guest __libc_init_profiling_handlers() would install its own handler, which
    // is not yet supported for guest code (at least need a proxy for
    // heapprofd_client.so) and fundamentally cannot be supported for host code.
    // TODO(b/167966989): Instead intercept __libc_init_profiling_handlers.
    case BIONIC_SIGNAL_PROFILER:
#endif
      return true;
  }
  return false;
}

}  // namespace

// Can be interrupted by another SetSignal!
void GuestThread::SetSignalFromHost(const siginfo_t& host_info) {
  siginfo_t* guest_info = pending_signals_.AllocSignal();

  // Convert host siginfo to guest.
  *guest_info = host_info;
  switch (host_info.si_signo) {
    case SIGILL:
    case SIGFPE: {
      guest_info->si_addr = ToHostAddr<void>(GetInsnAddr(GetCPUState(*state_)));
      break;
    }
    case SIGSYS: {
      guest_info->si_syscall = ToGuestSyscallNumber(host_info.si_syscall);
      break;
    }
  }

  // This is never interrupted by code that clears queue or status,
  // so the order in which to set them is not important.
  pending_signals_.EnqueueSignal(guest_info);
  // Check that pending signals are not disabled and mark them as present.
  uint8_t old_status = GetPendingSignalsStatusAtomic(*state_).exchange(kPendingSignalsPresent,
                                                                       std::memory_order_relaxed);
  CHECK_NE(kPendingSignalsDisabled, old_status);
}

bool GuestThread::SigAltStack(const stack_t* ss, stack_t* old_ss, int* error) {
  // The following code is not reentrant!
  ScopedSignalBlocker signal_blocker;

  if (old_ss) {
    if (sig_alt_stack_) {
      old_ss->ss_sp = sig_alt_stack_;
      old_ss->ss_size = sig_alt_stack_size_;
      old_ss->ss_flags = IsOnSigAltStack() ? SS_ONSTACK : 0;
    } else {
      old_ss->ss_sp = nullptr;
      old_ss->ss_size = 0;
      old_ss->ss_flags = SS_DISABLE;
    }
  }
  if (ss) {
    if (sig_alt_stack_ && IsOnSigAltStack()) {
      *error = EPERM;
      return false;
    }
    if (ss->ss_flags == SS_DISABLE) {
      sig_alt_stack_ = nullptr;
      sig_alt_stack_size_ = 0;
      return true;
    }
    if (ss->ss_flags != 0) {
      *error = EINVAL;
      return false;
    }
    if (ss->ss_size < GetGuest_MINSIGSTKSZ()) {
      *error = ENOMEM;
      return false;
    }
    sig_alt_stack_ = ss->ss_sp;
    sig_alt_stack_size_ = ss->ss_size;
  }
  return true;
}

void GuestThread::SwitchToSigAltStack() {
  if (sig_alt_stack_ && !IsOnSigAltStack()) {
    // TODO(b/289563835): Try removing `- 16` while ensuring app compatibility.
    // Reliable context on why we use `- 16` here seems to be lost.
    SetStackRegister(GetCPUState(*state_), ToGuestAddr(sig_alt_stack_) + sig_alt_stack_size_ - 16);
  }
}

bool GuestThread::IsOnSigAltStack() const {
  CHECK_NE(sig_alt_stack_, nullptr);
  const char* ss_start = static_cast<const char*>(sig_alt_stack_);
  const char* ss_curr = ToHostAddr<const char>(GetStackRegister(GetCPUState(*state_)));
  return ss_curr >= ss_start && ss_curr < ss_start + sig_alt_stack_size_;
}

void GuestThread::ProcessPendingSignals() {
  for (;;) {
    // Process pending signals while present.
    uint8_t status = GetPendingSignalsStatusAtomic(*state_).load(std::memory_order_acquire);
    CHECK_NE(kPendingSignalsDisabled, status);
    if (status == kPendingSignalsEnabled) {
      return;
    }
    ProcessPendingSignalsImpl();
  }
}

bool GuestThread::ProcessAndDisablePendingSignals() {
  for (;;) {
    // If pending signals are not present, cas should disable them.
    // Otherwise, process pending signals and try again.
    uint8_t old_status = kPendingSignalsEnabled;
    if (GetPendingSignalsStatusAtomic(*state_).compare_exchange_weak(
            old_status, kPendingSignalsDisabled, std::memory_order_acq_rel)) {
      return true;
    }
    if (old_status == kPendingSignalsDisabled) {
      return false;
    }
    ProcessPendingSignalsImpl();
  }
}

bool GuestThread::TestAndEnablePendingSignals() {
  // If pending signals are disabled, cas should mark them enabled.
  // Otherwise, pending signals are already enabled.
  uint8_t old_status = kPendingSignalsDisabled;
  return !GetPendingSignalsStatusAtomic(*state_).compare_exchange_strong(
      old_status, kPendingSignalsEnabled, std::memory_order_acq_rel);
}

// Return if another iteration is needed.
// ATTENTION: Can be interrupted by SetSignal!
void GuestThread::ProcessPendingSignalsImpl() {
  // Clear pending signals status and queue.
  // ATTENTION: It is important to change status before the queue!
  // Otherwise if interrupted by SetSignal, we might end up with
  // no pending signals status but with non-empty queue!
  GetPendingSignalsStatusAtomic(*state_).store(kPendingSignalsEnabled, std::memory_order_relaxed);

  siginfo_t* signal_info;
  while ((signal_info = pending_signals_.DequeueSignalUnsafe())) {
    const Guest_sigaction* sa = FindSignalHandler(signal_info->si_signo);
    ProcessGuestSignal(this, sa, signal_info);
    pending_signals_.FreeSignal(signal_info);
  }
}

bool SetGuestSignalHandler(int signal,
                           const Guest_sigaction* act,
                           Guest_sigaction* old_act,
                           int* error) {
  if (signal < 1 || signal > Guest__KERNEL__NSIG) {
    *error = EINVAL;
    return false;
  }

  if (act && IsReservedSignal(signal)) {
    TRACE("sigaction for reserved signal %d not set", signal);
    act = nullptr;
  }

  std::lock_guard<std::mutex> lock(g_signal_actions_guard_mutex);
  return g_signal_actions[signal - 1].Change(signal, act, HandleHostSignal, old_act, error);
}

}  // namespace berberis
