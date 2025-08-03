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

#include "chre/platform/system_timer.h"
#include "chre/platform/fatal_error.h"
#include "chre/platform/log.h"
#include "chre/util/time.h"

namespace chre {

void SystemTimerBase::rtTimerCallback(struct rt_timer *rtTimer) {
  if (rtTimer != nullptr) {
    SystemTimer *systemTimer = static_cast<SystemTimer *>(rtTimer->private_ptr);
    BaseType_t xHigherPriorityTaskWoken = pdFALSE;
    vTaskNotifyGiveFromISR(systemTimer->mCallbackRunnerHandle,
                           &xHigherPriorityTaskWoken);
    portYIELD_FROM_ISR(xHigherPriorityTaskWoken);
  }
}

void SystemTimerBase::callbackRunner(void *context) {
  SystemTimer *systemTimer = static_cast<SystemTimer *>(context);
  if (systemTimer == nullptr) {
    FATAL_ERROR("Null System Timer");
  }
  while (true) {
    ulTaskNotifyTake(pdTRUE, portMAX_DELAY);
    SystemTimerCallback *callback = systemTimer->mCallback;
    if (callback != nullptr) {
      callback(systemTimer->mData);
    }
  }
}

SystemTimer::SystemTimer() {
  // Initialize the rtSystemTimer struct.
  // The timer's callback and the private data won't be changed through the
  // lifetime so init() should only be run once. The creation of the callback
  // runner thread is delayed to the call of init().
  rt_timer_init(&rtSystemTimer, /* func= */ rtTimerCallback, /* data= */ this);
}

SystemTimer::~SystemTimer() {
  // cancel an existing timer if any
  cancel();
  // Delete the callback runner thread if it was created
  if (mCallbackRunnerHandle != nullptr) {
    vTaskDelete(mCallbackRunnerHandle);
    mCallbackRunnerHandle = nullptr;
  }
}

bool SystemTimer::init() {
  if (mInitialized) {
    return true;
  }
  BaseType_t xReturned = xTaskCreate(
      callbackRunner, kTaskName, kStackDepthWords,
      /* pvParameters= */ this, kTaskPriority, &mCallbackRunnerHandle);
  if (xReturned == pdPASS) {
    mInitialized = true;
    return true;
  }
  LOGE("Failed to create the callback runner thread");
  return false;
}

bool SystemTimer::set(SystemTimerCallback *callback, void *data,
                      Nanoseconds delay) {
  if (!mInitialized) {
    LOGW("Timer is not initialized");
    return false;
  }
  cancel();
  mCallback = callback;
  mData = data;
  rt_timer_start(&rtSystemTimer, delay.toRawNanoseconds(),
                 /* oneShot= */ true);
  return true;
}

bool SystemTimer::cancel() {
  // TODO(b/254708051): This usage of critical section is pending confirmation.
  taskENTER_CRITICAL();
  if (isActive()) {
    rt_timer_stop(&rtSystemTimer);
  }
  taskEXIT_CRITICAL();
  return true;
}

bool SystemTimer::isActive() {
  return rt_timer_active(&rtSystemTimer);
}

}  // namespace chre