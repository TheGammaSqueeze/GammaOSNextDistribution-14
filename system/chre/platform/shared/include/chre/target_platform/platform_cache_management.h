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

#ifndef CHRE_PLATFORM_SHARED_CACHE_MANAGEMENT_H_
#define CHRE_PLATFORM_SHARED_CACHE_MANAGEMENT_H_

#include <cinttypes>

namespace chre {

/**
 * Invalidates and/or cleans the system instruction and data caches.
 *
 * When nanoapps in CHRE are loaded dynamically, the data and
 * executable segments are parsed and relocated into memory. This operation
 * can cause cached instructions and data to be invalid when we start executing
 * the newly loaded nanoapp's instructions, and could possibly lead to an
 * exception. To support custom dynamic loading implementations (for example,
 * when it's not part of the underlying OS/system), the platform needs to
 * implement (or provide an empty stub for) the following method to invalidate
 * and/or clean the system data and instruction caches.
 *
 * If either one of the address or span parameter is zero (0), the function
 * should wipe the entire system cache if it is supported by the platform.
 *
 * @param address The target memory address to invalidate cache.
 * @param span Span of target memory to invalidate.
 */
void wipeSystemCaches(uintptr_t address = 0, uint32_t span = 0);

}  // namespace chre

#endif  // CHRE_PLATFORM_SHARED_CACHE_MANAGEMENT_H_
