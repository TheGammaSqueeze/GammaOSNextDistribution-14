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

#ifndef CHRE_UTIL_NANOAPP_STRING_H_
#define CHRE_UTIL_NANOAPP_STRING_H_

#include <cstdint>

#include "chre_api/chre.h"

namespace chre {

/**
 * Copies a null-terminated string from source to destination up to the length
 * of the source string or the destinationBufferSize - 1. Pads with null
 * characters.
 *
 * @param destination               the destination null-terminated string.
 * @param source                    the source null-terminated string.
 * @param destinationBufferSize     the size of the destination buffer.
 */
void copyString(char *destination, const char *source,
                size_t destinationBufferSize);

}  // namespace chre

#endif  // CHRE_UTIL_NANOAPP_STRING_H_
