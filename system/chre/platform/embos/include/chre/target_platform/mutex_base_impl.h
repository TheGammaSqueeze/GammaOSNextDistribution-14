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

#ifndef CHRE_PLATFORM_EMBOS_MUTEX_BASE_IMPL_H_
#define CHRE_PLATFORM_EMBOS_MUTEX_BASE_IMPL_H_

#include "chre/platform/mutex.h"

namespace chre {

inline Mutex::Mutex() {
  OS_CREATERSEMA(&mResourceSemaphore);
}

inline Mutex::~Mutex() {
  OS_DeleteRSema(&mResourceSemaphore);
}

inline void Mutex::lock() {
  OS_Use(&mResourceSemaphore);
}

inline bool Mutex::try_lock() {
  // The return value of OS_Request indicates the availability: a value of 1
  // indicates that the resource was available and is now in use by the calling
  // task.
  return OS_Request(&mResourceSemaphore) == 1;
}

// Note: Calling this function from a task that doesn't own the resource being
// released or if called before a call to OS_Use leads to undefined behavior.
// The EmbOS error handler (if enabled) OS_Error is invoked with code
// `OS_ERR_UNUSE_BEFORE_USE` in the former case, and with code
// `OS_ERR_RESOURCE_OWNER` for the latter.
inline void Mutex::unlock() {
  OS_Unuse(&mResourceSemaphore);
}

}  // namespace chre

#endif  // CHRE_PLATFORM_EMBOS_MUTEX_BASE_IMPL_H_
