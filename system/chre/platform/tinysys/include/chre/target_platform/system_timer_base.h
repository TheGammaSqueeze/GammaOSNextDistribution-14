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

#ifndef CHRE_PLATFORM_TINYSYS_SYSTEM_TIMER_BASE_H_
#define CHRE_PLATFORM_TINYSYS_SYSTEM_TIMER_BASE_H_

#include <cinttypes>
#include <csignal>
#include <ctime>

#ifdef __cplusplus
extern "C" {
#endif

#include "sensorhub/rt_timer.h"
#include "task.h"

#ifdef __cplusplus
}  // extern "C"
#endif

namespace chre {

class SystemTimerBase {
 protected:
  /** Stack size (in words) of the timer callback runner task */
  static constexpr uint32_t kStackDepthWords = 0x200;  // 2K stack size

  /** Priority of the callback runner task */
  static constexpr UBaseType_t kTaskPriority = tskIDLE_PRIORITY + 4;

  /** Name of the callback runner task */
  static constexpr char kTaskName[] = "ChreTimerCbRunner";

  /**
   * Callback function woken up by the timer, which in turn wakes up the
   * callback runner task
   */
  static void rtTimerCallback(struct rt_timer *timer);

  /**
   * The callback runner task that blocks until it gets woken up by the timer
   * callback.
   *
   * This task runs the actual callback set by the user as the timer callback is
   * running from the ISR.
   *
   * @param context a raw pointer that will be casted to a pointer to
   * SystemTimer.
   */

  static void callbackRunner(void *context);

  /** A FreeRTOS task handle holding the callback runner task */
  TaskHandle_t mCallbackRunnerHandle = nullptr;

  /** Tracks whether the timer has been initialized correctly. */
  bool mInitialized = false;

  /** The properties of the timer including callback, data, etc. */
  struct rt_timer rtSystemTimer;
};
}  // namespace chre
#endif  // CHRE_PLATFORM_TINYSYS_SYSTEM_TIMER_BASE_H_