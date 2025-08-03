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

#include "chre/platform/version.h"
#include "chre/util/macros.h"

#ifndef CHRE_VERSION_STRING
#define CHRE_VERSION_STRING "undefined"
#endif  // CHRE_VERSION_STRING

namespace chre {

//! The CHRE version string is placed in its own section so that it can be
//! removed before hashing to check whether there has been a change in the
//! compiled binary.
extern "C" DLL_EXPORT const char _chreVersionString[]
    __attribute__((section(".unstable_id"))) __attribute__((aligned(8))) =
        CHRE_VERSION_STRING;

const char *getChreVersionString() {
  return _chreVersionString;
}

}  // namespace chre
