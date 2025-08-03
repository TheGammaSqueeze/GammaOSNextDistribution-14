/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef CHRE_HOST_HAL_ERROR_H_
#define CHRE_HOST_HAL_ERROR_H_

#include <cinttypes>

namespace android::chre {

enum class HalError {
  SUCCESS = 0,

  // Hal service errors
  OPERATION_FAILED = -1,
  INVALID_RESULT = -2,
  INVALID_ARGUMENT = -3,

  // Hal client errors
  BINDER_CONNECTION_FAILED = -100,
  BINDER_DISCONNECTED = -101,
  NULL_CONTEXT_HUB_FROM_BINDER = -102,
  LINK_DEATH_RECIPIENT_FAILED = -103,
  CALLBACK_REGISTRATION_FAILED = -104,
};

}  // namespace android::chre
#endif  // CHRE_HOST_HAL_ERROR_H_