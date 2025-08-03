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

#ifndef CHRE_UTIL_HOST_ASSERT_H_
#define CHRE_UTIL_HOST_ASSERT_H_

/**
 * @file
 *
 * Suppplies a CHRE_ASSERT macro for host to use.
 */

#ifdef CHRE_IS_HOST_BUILD

#include <cassert>

/**
 * Provides the CHRE_ASSERT macro based on cassert.
 *
 * @param the condition to check for non-zero.
 */
#ifdef CHRE_ASSERTIONS_ENABLED
#define CHRE_ASSERT(condition) assert(condition)
#else
#define CHRE_ASSERT(condition) ((void)(condition))
#endif  // CHRE_ASSERTIONS_ENABLED

#ifdef __cplusplus
#define CHRE_ASSERT_NOT_NULL(ptr) CHRE_ASSERT((ptr) != nullptr)
#else
#define CHRE_ASSERT_NOT_NULL(ptr) CHRE_ASSERT((ptr) != NULL)
#endif

#ifdef GTEST
// Mocks are not supported in standalone mode. Just skip the statement entirely.
#define EXPECT_CHRE_ASSERT(statement)
#endif  // GTEST

#endif  // CHRE_IS_HOST_BUILD

#endif  // CHRE_UTIL_HOST_ASSERT_H_
