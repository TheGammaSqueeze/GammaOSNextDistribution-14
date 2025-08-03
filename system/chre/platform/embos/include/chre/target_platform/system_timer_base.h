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

#ifndef CHRE_PLATFORM_EMBOS_SYSTEM_TIMER_BASE_H_
#define CHRE_PLATFORM_EMBOS_SYSTEM_TIMER_BASE_H_

#include "RTOS.h"

namespace chre {

/**
 * @brief EmbOS platform specific system timer.
 *
 * Note that the system timer implementation for this platform is lock free
 * (i.e. no mutual exclusion is provided for setting, invocation or deletion
 * of the user-provided timer expiration callback). This is safe because the
 * only case a lock is needed is when a timer might fire in the midst of it
 * being stopped. In this scenario, if a lock were to be held by the CHRE (or
 * another) thread, it would execute until releasing the lock (possibly setting
 * or canceling the same timer). But since this can only happen prior to a call
 * to OS_StopTimerEx() returning, we know that the callback will be the one
 * provided for the previous timer and not a mismatch.
 *
 * @note
 * 1: This implementation is aimed at EmbOS v4.22.
 * 2: A side effect of this is that there still exists a possible race
 * between getting the status of a timer (via OS_GetTimerStatusEx()) and
 * stopping a timer (via OS_StopTimerEx()) which probably needs guarantees
 * at the OS implementation level - which means that the return value of the
 * system timer cancel call is not guaranteed to always be accurate.
 */
class SystemTimerBase {
 protected:
  OS_TIMER_EX mTimer;

  /**
   * Invokes the user-defined callback on the expiration of a timer.
   *
   * @param instance A pointer to a system timer instance containing the timer
   *        expiration callback.
   */
  static void invokeCallback(void *instance);
};

}  // namespace chre

#endif  // CHRE_PLATFORM_EMBOS_SYSTEM_TIMER_BASE_H_
