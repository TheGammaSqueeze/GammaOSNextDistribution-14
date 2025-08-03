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

#include "guest_signal_action.h"

#include <cerrno>
#include <csignal>
#include <cstring>

#include "berberis/base/bit_util.h"
#include "berberis/base/checks.h"
#include "berberis/base/host_signal.h"
#include "berberis/base/scoped_errno.h"
#include "berberis/guest_os_primitives/guest_signal.h"
#include "berberis/runtime_primitives/host_function_wrapper_impl.h"  // UnwrapHostFunction

// glibc doesn't define SA_RESTORER globally.
#ifndef SA_RESTORER
#define SA_RESTORER 0x04000000
#endif

namespace berberis {

namespace {

bool DoSigaction(int sig, const HostStructSigaction* sa, HostStructSigaction* old_sa, int* error) {
  ScopedErrno scoped_errno;
  if (HostSigaction(sig, sa, old_sa) == 0) {
    return true;
  }
  *error = errno;
  return false;
}

void ConvertHostSigactionToGuest(const HostStructSigaction* host_sa, Guest_sigaction* guest_sa) {
  guest_sa->guest_sa_sigaction = WrapHostSigactionForGuest(*host_sa);

  // We don't support SA_RESTORER flag for non-canonical handlers.  See: b/36458045
  if (bool(host_sa->sa_flags & SA_RESTORER)) {
    // Recognize canonical (kernel-provided) x86 handlers.
    // ATTENTION: kernel tolerates the case when SA_RESTORER is set but sa_restorer is null!
    if (host_sa->sa_restorer) {
      const char* handler = bit_cast<const char*>(host_sa->sa_restorer);
#if defined(__i386__)
      if ((memcmp(handler, "\x58\xb8\x77\x00\x00\x00\xcd\x80", 8) != 0) &&  // x86 sigreturn
          (memcmp(handler, "\xb8\xad\x00\x00\x00\xcd\x80", 7) != 0)) {      // x86 rt_sigreturn
        LOG_ALWAYS_FATAL("Unknown x86 sa_restorer in host sigaction!");
      }
#elif defined(__x86_64__)
      if (memcmp(handler, "\x48\xc7\xc0\x0f\x00\x00\x00\x0f\x05", 9) != 0) {  // x86_64 sigreturn
        LOG_ALWAYS_FATAL("Unknown x86_64 sa_restorer in host sigaction!");
      }
#else
#error "Unknown host arch"
#endif
    }
  }

  guest_sa->sa_flags = host_sa->sa_flags & ~SA_RESTORER;
  ResetSigactionRestorer(guest_sa);
  ConvertToSmallSigset(host_sa->sa_mask, &guest_sa->sa_mask);
}

bool ConvertGuestSigactionToHost(const Guest_sigaction* guest_sa,
                                 GuestSignalAction::host_sa_sigaction_t claimed_host_sa_sigaction,
                                 HostStructSigaction* host_sa) {
  bool claim = false;
  if (guest_sa->sa_flags & SA_SIGINFO) {
    if (guest_sa->guest_sa_sigaction == 0) {
      // It can happen that we are requested to set SIG_DFL (= 0) _sigaction_ (not _handler_)!
      // Don't claim and just keep host responsible for this!
      host_sa->sa_sigaction = nullptr;
    } else if (void* func = UnwrapHostFunction(guest_sa->guest_sa_sigaction)) {
      host_sa->sa_sigaction = reinterpret_cast<GuestSignalAction::host_sa_sigaction_t>(func);
    } else {
      host_sa->sa_sigaction = claimed_host_sa_sigaction;
      claim = true;
    }
  } else if (guest_sa->guest_sa_sigaction == Guest_SIG_DFL) {
    host_sa->sa_handler = SIG_DFL;
  } else if (guest_sa->guest_sa_sigaction == Guest_SIG_IGN) {
    host_sa->sa_handler = SIG_IGN;
  } else if (guest_sa->guest_sa_sigaction == Guest_SIG_ERR) {
    host_sa->sa_handler = SIG_ERR;
  } else {
    void* func = UnwrapHostFunction(guest_sa->guest_sa_sigaction);
    if (func) {
      host_sa->sa_handler = reinterpret_cast<void (*)(int)>(func);
    } else {
      host_sa->sa_sigaction = claimed_host_sa_sigaction;
      claim = true;
    }
  }

  // We don't support SA_RESTORER flag for non-canonical handlers.  See: b/36458045
  if (bool(guest_sa->sa_flags & SA_RESTORER)) {
    CheckSigactionRestorer(guest_sa);
  }

  host_sa->sa_flags = guest_sa->sa_flags & ~SA_RESTORER;
  host_sa->sa_restorer = nullptr;
  if (claim) {
    host_sa->sa_flags |= SA_SIGINFO;
  }

  // ATTENTION: it might seem tempting to run claimed_host_sa_sigaction with all signals blocked.
  // But, guest signal handler should run with current thread signal mask + guest action signal
  // mask, and might expect certain signals to interrupt. If pending signals are disabled, then
  // claimed_host_sa_sigaction executes guest signal handler within, so at that point signal mask
  // should be correct. Unfortunately, if claimed_host_sa_sigaction gets invoked with all signals
  // blocked, there seems to be no way to restore the correct signal mask before running guest
  // signal handler.
  ConvertToBigSigset(guest_sa->sa_mask, &host_sa->sa_mask);

  return claim;
}

}  // namespace

bool GuestSignalAction::Change(int sig,
                               const Guest_sigaction* new_sa,
                               host_sa_sigaction_t claimed_host_sa_sigaction,
                               Guest_sigaction* old_sa,
                               int* error) {
  HostStructSigaction host_sa{};

  Guest_sigaction saved_new_sa{};
  HostStructSigaction* new_host_sa = nullptr;
  bool claim = false;
  if (new_sa) {
    // ATTENTION: new_sa and old_sa might point to the same object!
    // Make a copy of new_sa so we can write to old_sa before all reads of new_sa!
    saved_new_sa = *new_sa;
    new_sa = &saved_new_sa;

    new_host_sa = &host_sa;
    claim = ConvertGuestSigactionToHost(new_sa, claimed_host_sa_sigaction, new_host_sa);
  }

  // Even if we only set new action for already claimed signal, we still need to call host
  // sigaction to update kernel action mask and flags!
  HostStructSigaction* old_host_sa = &host_sa;
  if (!DoSigaction(sig, new_host_sa, old_host_sa, error)) {
    return false;
  }

  if (old_sa) {
    if (IsClaimed()) {
      *old_sa = GetClaimedGuestAction();
    } else {
      ConvertHostSigactionToGuest(old_host_sa, old_sa);
    }
  }

  if (new_sa) {
    if (claim) {
      Claim(new_sa);
    } else {
      Unclaim();
    }
  }

  return true;
}

}  // namespace berberis
