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

#ifndef ART_ARTD_TESTING_H_
#define ART_ARTD_TESTING_H_

// Returns the value of the given `android::base::Result`, or reports the error as a gMock matcher
// mismatch. This is only to be used in a gMock matcher.
#define OR_MISMATCH(expr)                          \
  ({                                               \
    auto&& tmp__ = (expr);                         \
    if (!tmp__.ok()) {                             \
      *result_listener << tmp__.error().message(); \
      return false;                                \
    }                                              \
    std::move(tmp__).value();                      \
  })

// Returns the value of the given `android::base::Result`, or fails the GoogleTest.
#define OR_FAIL(expr)                                   \
  ({                                                    \
    auto&& tmp__ = (expr);                              \
    ASSERT_TRUE(tmp__.ok()) << tmp__.error().message(); \
    std::move(tmp__).value();                           \
  })

#endif  // ART_ARTD_TESTING_H_
