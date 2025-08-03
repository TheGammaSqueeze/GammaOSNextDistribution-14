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

#ifndef BERBERIS_GUEST_OS_PRIMITIVES_SCOPED_SIGNAL_BLOCKER_H_
#define BERBERIS_GUEST_OS_PRIMITIVES_SCOPED_SIGNAL_BLOCKER_H_

#include <signal.h>

#include "berberis/base/host_signal.h"

namespace berberis {

// Disable signals for scope.
// Might be called recursively.
// ATTENTION: Don't call (pthread_)sigmask while inside guarded scope!
class ScopedSignalBlocker {
 public:
  ScopedSignalBlocker() {
    HostSigset mask;
    HostSigfillset(&mask);
    Init(&mask);
  }
  ScopedSignalBlocker(const ScopedSignalBlocker&) = delete;
  ScopedSignalBlocker& operator=(const ScopedSignalBlocker&) = delete;

  explicit ScopedSignalBlocker(const HostSigset* mask) { Init(mask); }

  ~ScopedSignalBlocker() { RTSigprocmaskSyscallOrDie(SIG_SETMASK, &old_mask_, nullptr); }

  [[nodiscard]] const HostSigset* old_mask() const { return &old_mask_; }

 private:
  void Init(const HostSigset* mask) { RTSigprocmaskSyscallOrDie(SIG_BLOCK, mask, &old_mask_); }

  HostSigset old_mask_;
};

}  // namespace berberis

#endif  // BERBERIS_GUEST_OS_PRIMITIVES_SCOPED_SIGNAL_BLOCKER_H_