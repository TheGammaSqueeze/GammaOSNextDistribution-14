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

#ifndef CHRE_PLATFORM_FREERTOS_MUTEX_BASE_H_
#define CHRE_PLATFORM_FREERTOS_MUTEX_BASE_H_

#include "chre/platform/fatal_error.h"
#include "chre/platform/log.h"

#include "FreeRTOS.h"
#include "semphr.h"

namespace chre {

/**
 * The FreeRTOS implementation of MutexBase
 */
class MutexBase {
 protected:
  SemaphoreHandle_t mSemaphoreHandle = NULL;

  /**
   * Initialize the mutex handle using a static semaphore
   * to avoid heap allocations
   */
  void initStaticMutex() {
#ifdef CHRE_CREATE_MUTEX_ON_HEAP
    mSemaphoreHandle = xSemaphoreCreateMutex();
#else
    mSemaphoreHandle = xSemaphoreCreateMutexStatic(&mStaticSemaphore);
#endif
    if (mSemaphoreHandle == NULL) {
      FATAL_ERROR("Failed to initialize mutex");
    }
  }

 private:
#ifndef CHRE_CREATE_MUTEX_ON_HEAP
  StaticSemaphore_t mStaticSemaphore;
#endif
};

}  // namespace chre

#endif  // CHRE_PLATFORM_FREERTOS_MUTEX_BASE_H_
