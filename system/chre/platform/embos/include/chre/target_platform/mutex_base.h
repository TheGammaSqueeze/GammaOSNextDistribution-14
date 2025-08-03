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

#ifndef CHRE_PLATFORM_EMBOS_MUTEX_BASE_H_
#define CHRE_PLATFORM_EMBOS_MUTEX_BASE_H_

#include "RTOS.h"

namespace chre {

/**
 * The EmbOS implementation of MutexBase.
 *
 * Note that the current implementation is aimed at EmbOS v4.22.
 * A 'resource semaphore' is used to implement the mutex. It is not safe to
 * do any Mutex operations from within an ISR.
 */
class MutexBase {
 protected:
  OS_RSEMA mResourceSemaphore;
};

}  // namespace chre

#endif  // CHRE_PLATFORM_EMBOS_MUTEX_BASE_H_
