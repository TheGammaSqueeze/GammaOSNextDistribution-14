/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "chre/platform/assert.h"

#include <cassert>

#include "chre/platform/linux/expect_assert.h"

#ifdef GTEST
MockAssert *gMockAssert;
#endif  // GTEST

void chreDoAssert(const char *filename, size_t line) {
  LOGE("Assertion failure at %s:%zu", filename, line);
#ifdef GTEST
  if (gMockAssert != nullptr) {
    gMockAssert->doAssert();
  } else
#endif  // GTEST
  {
#ifdef NDEBUG
    // Directly abort the program if assert is a no-op so that gtests can
    // work properly.
    abort();
#else
    assert(false);
#endif  // NDEBUG
  }
}
