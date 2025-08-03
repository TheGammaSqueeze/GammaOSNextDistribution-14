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

#include "chre/target_platform/condition_variable_base.h"
#include "chre/platform/condition_variable.h"

#include "intc.h"

namespace chre {

void ConditionVariableBase::conditionVariablTimerCallback(
    struct rt_timer *rtTimer) {
  if (rtTimer != nullptr) {
    ConditionVariable *cv =
        static_cast<ConditionVariable *>(rtTimer->private_ptr);
    cv->isTimedOut = true;
    cv->notify_one();
  }
}

ConditionVariable::ConditionVariable() {
  semaphoreHandle = xSemaphoreCreateBinaryStatic(&mSemaphoreBuffer);
  if (semaphoreHandle == nullptr) {
    FATAL_ERROR("Failed to create cv semaphore");
  }
}

ConditionVariable::~ConditionVariable() {
  if (semaphoreHandle != NULL) {
    vSemaphoreDelete(semaphoreHandle);
  }
}

void ConditionVariable::notify_one() {
  if (is_in_isr()) {
    BaseType_t xHigherPriorityTaskWoken = pdFALSE;
    xSemaphoreGiveFromISR(semaphoreHandle, &xHigherPriorityTaskWoken);
    portYIELD_FROM_ISR(xHigherPriorityTaskWoken);
  } else {
    xSemaphoreGive(semaphoreHandle);
  }
}

void ConditionVariable::wait(Mutex &mutex) {
  mutex.unlock();
  BaseType_t rc = xSemaphoreTake(semaphoreHandle, portMAX_DELAY);
  mutex.lock();
  if (rc == pdFALSE) {
    LOGE("Semaphore of the condition variable is unavailable.");
  }
}

bool ConditionVariable::wait_for(Mutex &mutex, Nanoseconds timeout) {
  rt_timer_init(&rtSystemTimer,
                /* func= */ nullptr,
                /* data= */ this);
  rt_timer_start(&rtSystemTimer, timeout.toRawNanoseconds(),
                 /* oneShot= */ true);
  wait(mutex);

  taskENTER_CRITICAL();
  if (!isTimedOut) {
    rt_timer_stop(&rtSystemTimer);
  }
  taskEXIT_CRITICAL();
  return isTimedOut;
}
}  // namespace chre
