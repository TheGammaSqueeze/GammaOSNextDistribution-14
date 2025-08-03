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

#ifndef PLATFORM_SHARED_INCLUDE_CHRE_PLATFORM_SHARED_ASSERT_FUNC_H
#define PLATFORM_SHARED_INCLUDE_CHRE_PLATFORM_SHARED_ASSERT_FUNC_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Performs assertion while logging the filename and line provided.
 *
 * @param filename The filename containing the assertion being fired.
 * @param line The line that contains the assertion being fired.
 */
void chreDoAssert(const char *filename, size_t line);

#ifdef __cplusplus
}
#endif

#define CHRE_ASSERT(condition)               \
  do {                                       \
    if (!(condition)) {                      \
      chreDoAssert(CHRE_FILENAME, __LINE__); \
    }                                        \
  } while (0)

#endif // PLATFORM_SHARED_INCLUDE_CHRE_PLATFORM_SHARED_ASSERT_FUNC_H
