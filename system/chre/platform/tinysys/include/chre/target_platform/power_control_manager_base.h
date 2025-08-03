/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef CHRE_PLATFORM_TINYSYS_POWER_CONTROL_MANAGER_BASE_H
#define CHRE_PLATFORM_TINYSYS_POWER_CONTROL_MANAGER_BASE_H

#include <cstddef>
#include "chre/platform/atomic.h"

namespace chre {

class PowerControlManagerBase {
 public:
  /**
   * Updates internal wake/suspend flag and pushes awake/sleep notification
   * to nanoapps that are listening for it.
   *
   * @param awake true if host is awake, otherwise suspended.
   */
  void onHostWakeSuspendEvent(bool awake);

 protected:
  /** True if the host is awake, false otherwise. */
  AtomicBool mHostIsAwake = true;
};

}  // namespace chre

#endif  // CHRE_PLATFORM_TINYSYS_POWER_CONTROL_MANAGER_BASE_H