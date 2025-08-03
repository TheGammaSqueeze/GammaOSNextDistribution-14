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

#ifndef BERBERIS_GUEST_OS_PRIMITIVES_GUEST_SIGNAL_ACTION_H_
#define BERBERIS_GUEST_OS_PRIMITIVES_GUEST_SIGNAL_ACTION_H_

#include <csignal>

#include "berberis/base/checks.h"
#include "berberis/base/macros.h"
#include "berberis/guest_os_primitives/guest_signal.h"
#include "berberis/guest_os_primitives/guest_signal_arch.h"

namespace berberis {

// Signal is 'claimed' when it has guest handler/action. For a claimed signal, actual host action
// is a wrapper that invokes guest code (or suspends handling until region exit).
class GuestSignalAction {
 public:
  using host_sa_sigaction_t = void (*)(int, siginfo_t*, void*);

  constexpr GuestSignalAction()
      : claimed_guest_sa_{.guest_sa_sigaction = Guest_SIG_DFL, .sa_flags = 0, .sa_mask = {}} {}

  bool Change(int sig,
              const Guest_sigaction* new_sa,
              host_sa_sigaction_t claimed_host_sa_sigaction,
              Guest_sigaction* old_sa,
              int* error);

  const Guest_sigaction& GetClaimedGuestAction() const {
    CHECK(IsClaimed());
    return claimed_guest_sa_;
  }

 private:
  bool IsClaimed() const { return claimed_guest_sa_.guest_sa_sigaction != Guest_SIG_DFL; }

  void Claim(const Guest_sigaction* sa) {
    CHECK_NE(Guest_SIG_DFL, sa->guest_sa_sigaction);
    claimed_guest_sa_ = *sa;
  }

  void Unclaim() { claimed_guest_sa_.guest_sa_sigaction = Guest_SIG_DFL; }

  Guest_sigaction claimed_guest_sa_;  // Guest_SIG_DFL when not claimed.

  DISALLOW_COPY_AND_ASSIGN(GuestSignalAction);
};

}  // namespace berberis

#endif  // BERBERIS_GUEST_OS_PRIMITIVES_GUEST_SIGNAL_ACTION_H_
