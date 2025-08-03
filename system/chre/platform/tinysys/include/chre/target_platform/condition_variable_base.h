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

#ifndef CHRE_PLATFORM_TINYSYS_CONDITION_VARIABLE_BASE_H_
#define CHRE_PLATFORM_TINYSYS_CONDITION_VARIABLE_BASE_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "FreeRTOS.h"
#include "semphr.h"
#include "sensorhub/rt_timer.h"

#ifdef __cplusplus
}  // extern "C"
#endif

namespace chre {

/**
 * Condition variable implementation based on rt_timer.
 *
 * Condition variable is preferred to run the timer callback in the ISR for less
 * overhead/latency, which is why SystemTimer is not used here because
 * SystemTimer assumes a callback function takes some time to finish so it
 * creates a separate thread to run it.
 */
class ConditionVariableBase {
 protected:
  /** callback function woken up by the timer */
  static void conditionVariablTimerCallback(struct rt_timer *rtTimer);

  /** semaphore implementing the condition variable */
  SemaphoreHandle_t semaphoreHandle;

  /** Buffer used to store state used by the semaphore */
  StaticSemaphore_t mSemaphoreBuffer;

  /** True if wait_for() times out before semaphoreHandle is given */
  bool isTimedOut = false;

  /** settings of the rt_timer */
  struct rt_timer rtSystemTimer {};
};

}  // namespace chre

#endif  // CHRE_PLATFORM_TINYSYS_CONDITION_VARIABLE_BASE_H_