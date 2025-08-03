/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef CHRE_PLATFORM_SHARED_AUTHENTICATION_H_
#define CHRE_PLATFORM_SHARED_AUTHENTICATION_H_

#include <cstddef>

namespace chre {

/**
 * Authenticates the signature of the provided binary. If not provided
 * elsewhere by the platform, this method must ensure that nanoapps are signed
 * appropriately and no corruption has occurred to the binary prior to being
 * loaded. If this method succeeds, CHRE will assume the binary has the same
 * execution privileges as the core framework itself.
 *
 * @param binary Pointer to the binary that should be authenticated.
 * @param appBinaryLen The length of the binary.
 * @param realBinaryStart A non-null pointer that, if this method succeeds, must
 *     be filled with the starting address of the raw binary after any headers
 *     used by the authentication code. This will be passed to the dynamic
 *     loader which will assume the starting address is a valid ELF binary.
 * @return True if the binary passed authentication.
 */
bool authenticateBinary(const void *binary, size_t appBinaryLen,
                        void **realBinaryStart);

}  // namespace chre

#endif  // CHRE_PLATFORM_SHARED_AUTHENTICATION_H_
