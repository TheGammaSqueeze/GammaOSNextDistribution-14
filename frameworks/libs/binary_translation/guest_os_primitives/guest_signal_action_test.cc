/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "gtest/gtest.h"

#include <signal.h>

#include "berberis/guest_os_primitives/guest_signal.h"
#include "berberis/guest_state/guest_addr.h"

#include "guest_signal_action.h"

namespace berberis {

namespace {

class ScopedSignalHandler {
 public:
  ScopedSignalHandler(int sig, void (*handler)(int)) : sig_(sig) {
    struct sigaction act {};
    act.sa_handler = handler;
    sigaction(sig_, &act, &old_act_);
  }

  ScopedSignalHandler(int sig, void (*action)(int, siginfo_t*, void*)) : sig_(sig) {
    struct sigaction act {};
    act.sa_sigaction = action;
    act.sa_flags = SA_SIGINFO;
    sigaction(sig_, &act, &old_act_);
  }

  ~ScopedSignalHandler() { sigaction(sig_, &old_act_, nullptr); }

 private:
  int sig_;
  struct sigaction old_act_;
};

void ClaimedHostSaSigaction(int, siginfo_t*, void*) {}

TEST(GuestSignalActionTest, Smoke) {
  GuestSignalAction action;

  // Ensure address doesn't accidentally coincide with any valid host function :)
  int fake_guest_func;
  const GuestAddr kGuestSaSigaction = ToGuestAddr(&fake_guest_func);

  Guest_sigaction new_sa{};
  new_sa.guest_sa_sigaction = kGuestSaSigaction;
  Guest_sigaction old_sa{};
  int error = 0;

  EXPECT_TRUE(action.Change(SIGUSR1, &new_sa, ClaimedHostSaSigaction, &old_sa, &error));
  EXPECT_EQ(0, error);
  EXPECT_EQ(kGuestSaSigaction, action.GetClaimedGuestAction().guest_sa_sigaction);

  EXPECT_TRUE(action.Change(SIGUSR1, &old_sa, nullptr, &old_sa, &error));
  EXPECT_EQ(0, error);
  EXPECT_EQ(kGuestSaSigaction, old_sa.guest_sa_sigaction);
}

void CustomSignalAction(int, siginfo_t*, void*) {}

TEST(GuestSignalActionTest, ShareNewAndOld) {
  // Start with custom action.
  ScopedSignalHandler scoped_usr1_handler(SIGUSR1, CustomSignalAction);
  const GuestAddr kOrigGuestSaSigaction = ToGuestAddr(CustomSignalAction);

  GuestSignalAction action;

  // Ensure address doesn't accidentally coincide with any valid host function :)
  int fake_guest_func;
  const GuestAddr kNewGuestSaSigaction = ToGuestAddr(&fake_guest_func);

  Guest_sigaction sa{};
  sa.guest_sa_sigaction = kNewGuestSaSigaction;
  int error = 0;

  // Set new action, use the same object for new and old sa.
  EXPECT_TRUE(action.Change(SIGUSR1, &sa, ClaimedHostSaSigaction, &sa, &error));
  EXPECT_EQ(0, error);
  EXPECT_EQ(kOrigGuestSaSigaction, sa.guest_sa_sigaction);

  // Check current action.
  EXPECT_TRUE(action.Change(SIGUSR1, nullptr, nullptr, &sa, &error));
  EXPECT_EQ(0, error);
  EXPECT_EQ(kNewGuestSaSigaction, sa.guest_sa_sigaction);
}

TEST(GuestSignalActionTest, SetDFL) {
  // Start with custom action.
  ScopedSignalHandler scoped_usr1_handler(SIGUSR1, CustomSignalAction);
  const GuestAddr kOrigGuestSaSigaction = ToGuestAddr(CustomSignalAction);

  GuestSignalAction action;

  // Examine current action.

  Guest_sigaction old_sa{};
  int error = 0;

  EXPECT_TRUE(action.Change(SIGUSR1, nullptr, nullptr, &old_sa, &error));
  EXPECT_EQ(0, error);
  EXPECT_EQ(kOrigGuestSaSigaction, old_sa.guest_sa_sigaction);

  // Set SIG_DFL.

  Guest_sigaction new_sa{};
  new_sa.guest_sa_sigaction = Guest_SIG_DFL;

  EXPECT_TRUE(action.Change(SIGUSR1, &new_sa, ClaimedHostSaSigaction, &old_sa, &error));
  EXPECT_EQ(0, error);
  EXPECT_EQ(kOrigGuestSaSigaction, old_sa.guest_sa_sigaction);

  // Restore original action.

  new_sa.guest_sa_sigaction = kOrigGuestSaSigaction;

  EXPECT_TRUE(action.Change(SIGUSR1, &new_sa, ClaimedHostSaSigaction, &old_sa, &error));
  EXPECT_EQ(0, error);
  EXPECT_EQ(Guest_SIG_DFL, old_sa.guest_sa_sigaction);

  // Examine current action.

  EXPECT_TRUE(action.Change(SIGUSR1, nullptr, nullptr, &old_sa, &error));
  EXPECT_EQ(0, error);
  EXPECT_EQ(kOrigGuestSaSigaction, old_sa.guest_sa_sigaction);
}

TEST(GuestSignalActionTest, SetCurr) {
  // Start with custom action.
  ScopedSignalHandler scoped_usr1_handler(SIGUSR1, CustomSignalAction);
  const GuestAddr kOrigGuestSaSigaction = ToGuestAddr(CustomSignalAction);

  GuestSignalAction action;

  // Examine current action.

  Guest_sigaction old_sa{};
  int error = 0;

  EXPECT_TRUE(action.Change(SIGUSR1, nullptr, nullptr, &old_sa, &error));
  EXPECT_EQ(0, error);
  EXPECT_EQ(kOrigGuestSaSigaction, old_sa.guest_sa_sigaction);

  // Set action that is already current.

  EXPECT_TRUE(action.Change(SIGUSR1, &old_sa, ClaimedHostSaSigaction, &old_sa, &error));
  EXPECT_EQ(0, error);
  EXPECT_EQ(kOrigGuestSaSigaction, old_sa.guest_sa_sigaction);
}

TEST(GuestSignalActionTest, SetCurrDFL) {
  // Start with SIG_DFL action - ensure it is actually current!
  ScopedSignalHandler scoped_usr1_handler(SIGUSR1, SIG_DFL);

  GuestSignalAction action;

  // Examine current action.

  Guest_sigaction old_sa{};
  int error = 0;

  EXPECT_TRUE(action.Change(SIGUSR1, nullptr, nullptr, &old_sa, &error));
  EXPECT_EQ(0, error);
  EXPECT_EQ(Guest_SIG_DFL, old_sa.guest_sa_sigaction);

  // Set SIG_DFL that is already current.

  EXPECT_TRUE(action.Change(SIGUSR1, &old_sa, ClaimedHostSaSigaction, &old_sa, &error));
  EXPECT_EQ(0, error);
  EXPECT_EQ(Guest_SIG_DFL, old_sa.guest_sa_sigaction);
}

TEST(GuestSignalActionTest, SetNullAction) {
  // Start with custom action.
  ScopedSignalHandler scoped_usr1_handler(SIGUSR1, CustomSignalAction);

  GuestSignalAction action;

  // Set null sa_sigaction.

  Guest_sigaction new_sa{};
  new_sa.sa_flags = SA_SIGINFO;
  int error = 0;

  EXPECT_TRUE(action.Change(SIGUSR1, &new_sa, ClaimedHostSaSigaction, nullptr, &error));
  EXPECT_EQ(0, error);

  // Examine current action.

  Guest_sigaction old_sa{};

  EXPECT_TRUE(action.Change(SIGUSR1, nullptr, nullptr, &old_sa, &error));
  EXPECT_EQ(0, error);
  EXPECT_EQ(0u, old_sa.guest_sa_sigaction);
}

}  // namespace

}  // namespace berberis
