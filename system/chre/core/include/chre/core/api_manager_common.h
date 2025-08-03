/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef CHRE_CORE_API_MANAGER_COMMON_H_
#define CHRE_CORE_API_MANAGER_COMMON_H_

#include <cstddef>

#include "chre_api/chre/common.h"

namespace chre {

//! The number of chre error types.
//! NOTE: This value must be updated whenever the last value in chreError
//! changes.
static constexpr size_t CHRE_ERROR_SIZE =
    chreError::CHRE_ERROR_DESTINATION_NOT_FOUND + 1;

}  // namespace chre

#endif  // CHRE_CORE_API_MANAGER_COMMON_H_
