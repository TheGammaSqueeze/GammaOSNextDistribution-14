/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef LIBTEXTCLASSIFIER_UTILS_UTF8_UNILIB_TEST_INCLUDE_H_
#define LIBTEXTCLASSIFIER_UTILS_UTF8_UNILIB_TEST_INCLUDE_H_

#include "utils/jvm-test-utils.h"
#include "utils/utf8/unilib.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {
namespace test_internal {

class UniLibTest : public ::testing::Test {
 protected:
  UniLibTest() : unilib_(CreateUniLibForTesting()) {}
  std::unique_ptr<UniLib> unilib_;
};

}  // namespace test_internal
}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_UTILS_UTF8_UNILIB_TEST_INCLUDE_H_
