/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef CHRE_PLATFORM_FREERTOS_MUTEX_BASE_IMPL_H_
#define CHRE_PLATFORM_FREERTOS_MUTEX_BASE_IMPL_H_

#include "chre/platform/assert.h"
#include "chre/platform/mutex.h"

namespace chre {

inline Mutex::Mutex() {
  initStaticMutex();
}

inline Mutex::~Mutex() {
  if (mSemaphoreHandle) {
    vSemaphoreDelete(mSemaphoreHandle);
  }
}

inline void Mutex::lock() {
  TickType_t blockForever = portMAX_DELAY;
  CHRE_ASSERT(mSemaphoreHandle != NULL);
  if (pdTRUE != xSemaphoreTake(mSemaphoreHandle, blockForever)) {
    LOGE("Failed to lock mutex");
  }
}

inline bool Mutex::try_lock() {
  TickType_t doNotBlock = static_cast<TickType_t>(0);
  BaseType_t rv = xSemaphoreTake(mSemaphoreHandle, doNotBlock);

  return (rv == pdTRUE) ? true : false;
}

inline void Mutex::unlock() {
  if (pdTRUE != xSemaphoreGive(mSemaphoreHandle)) {
    LOGE("Failed to properly unlock mutex!");
  }
}

}  // namespace chre

#endif  // CHRE_PLATFORM_FREERTOS_MUTEX_BASE_IMPL_H_
