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

#ifndef BERBERIS_GUEST_OS_PRIMITIVES_SCOPED_PENDING_SIGNALS_H_
#define BERBERIS_GUEST_OS_PRIMITIVES_SCOPED_PENDING_SIGNALS_H_

#include "berberis/base/checks.h"
#include "berberis/base/macros.h"
#include "berberis/guest_os_primitives/guest_thread.h"

namespace berberis {

// Can be used when pending signals are disabled or enabled.
class ScopedPendingSignalsEnabler {
 public:
  explicit ScopedPendingSignalsEnabler(GuestThread* thread) : thread_(thread) {
    prev_pending_signals_enabled_ = thread_->TestAndEnablePendingSignals();
  }

  ~ScopedPendingSignalsEnabler() {
    if (!prev_pending_signals_enabled_) {
      CHECK_EQ(true, thread_->ProcessAndDisablePendingSignals());
    }
  }

 private:
  GuestThread* thread_;
  bool prev_pending_signals_enabled_;

  DISALLOW_COPY_AND_ASSIGN(ScopedPendingSignalsEnabler);
};

// Can be used when pending signals are disabled or enabled.
class ScopedPendingSignalsDisabler {
 public:
  explicit ScopedPendingSignalsDisabler(GuestThread* thread) : thread_(thread) {
    prev_pending_signals_enabled_ = thread_->ProcessAndDisablePendingSignals();
  }

  ~ScopedPendingSignalsDisabler() {
    if (prev_pending_signals_enabled_) {
      CHECK_EQ(false, thread_->TestAndEnablePendingSignals());
    }
  }

 private:
  GuestThread* thread_;
  bool prev_pending_signals_enabled_;

  DISALLOW_COPY_AND_ASSIGN(ScopedPendingSignalsDisabler);
};

}  // namespace berberis

#endif  // BERBERIS_GUEST_OS_PRIMITIVES_SCOPED_PENDING_SIGNALS_H_
