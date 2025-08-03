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

#ifndef LIBTEXTCLASSIFIER_ANNOTATOR_ANNOTATOR_TEST_INCLUDE_H_
#define LIBTEXTCLASSIFIER_ANNOTATOR_ANNOTATOR_TEST_INCLUDE_H_

#include <fstream>
#include <string>

#include "annotator/annotator.h"
#include "utils/base/logging.h"
#include "utils/jvm-test-utils.h"
#include "utils/test-data-test-utils.h"
#include "utils/testing/annotator.h"
#include "utils/utf8/unilib.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {
namespace test_internal {

inline std::string GetModelPath() {
  return GetTestDataPath("annotator/test_data/");
}

class TestingAnnotator : public Annotator {
 public:
  TestingAnnotator(
      const UniLib* unilib, const CalendarLib* calendarlib,
      const std::function<void(ModelT*)> model_update_fn = [](ModelT* model) {
      }) {
    owned_buffer_ = CreateEmptyModel(model_update_fn);
    ValidateAndInitialize(libtextclassifier3::ViewModel(owned_buffer_.data(),
                                                        owned_buffer_.size()),
                          unilib, calendarlib);
    AssertIsInitialized();
  }

  static std::unique_ptr<TestingAnnotator> FromUnownedBuffer(
      const char* buffer, int size, const UniLib* unilib = nullptr,
      const CalendarLib* calendarlib = nullptr) {
    // Safe to downcast from Annotator* to TestingAnnotator* because the
    // subclass is not adding any new members.
    return std::unique_ptr<TestingAnnotator>(
        reinterpret_cast<TestingAnnotator*>(
            Annotator::FromUnownedBuffer(buffer, size, unilib, calendarlib)
                .release()));
  }

  using Annotator::ResolveConflicts;

 private:
  void AssertIsInitialized() { ASSERT_TRUE(IsInitialized()); }
};

class AnnotatorTest : public ::testing::TestWithParam<const char*> {
 protected:
  AnnotatorTest()
      : unilib_(CreateUniLibForTesting()),
        calendarlib_(CreateCalendarLibForTesting()) {}

  std::unique_ptr<UniLib> unilib_;
  std::unique_ptr<CalendarLib> calendarlib_;
};

}  // namespace test_internal
}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ANNOTATOR_ANNOTATOR_TEST_INCLUDE_H_
