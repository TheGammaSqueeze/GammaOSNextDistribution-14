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

#ifndef CHRE_PLATFORM_ZEPHYR_CONDITION_VARIABLE_IMPL_H_
#define CHRE_PLATFORM_ZEPHYR_CONDITION_VARIABLE_IMPL_H_

#include "chre/platform/condition_variable.h"

namespace chre {

inline ConditionVariable::ConditionVariable() {
  k_condvar_init(&condvar);
}

inline ConditionVariable::~ConditionVariable() {}

inline void ConditionVariable::notify_one() {
  k_condvar_signal(&condvar);
}

inline void ConditionVariable::wait(Mutex &mutex) {
  k_condvar_wait(&condvar, &(mutex.mutex), K_FOREVER);
}

inline bool ConditionVariable::wait_for(Mutex &mutex, Nanoseconds timeout) {
  return (k_condvar_wait(&condvar, &(mutex.mutex),
                         K_NSEC(timeout.toRawNanoseconds())) == 0);
}

} // namespace chre

#endif  // CHRE_PLATFORM_ZEPHYR_CONDITION_VARIABLE_IMPL_H_
