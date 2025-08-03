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

#ifndef LIBTEXTCLASSIFIER_ANNOTATOR_DATETIME_TESTING_BASE_PARSER_TEST_H_
#define LIBTEXTCLASSIFIER_ANNOTATOR_DATETIME_TESTING_BASE_PARSER_TEST_H_

#include <string>
#include <vector>

#include "annotator/datetime/parser.h"
#include "annotator/datetime/testing/base-parser-test.h"
#include "annotator/types.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {

class DateTimeParserTest : public testing::Test {
 public:
  bool HasNoResult(const std::string& text, bool anchor_start_end = false,
                   const std::string& timezone = "Europe/Zurich",
                   AnnotationUsecase annotation_usecase =
                       AnnotationUsecase_ANNOTATION_USECASE_SMART);

  bool ParsesCorrectly(
      const std::string& marked_text,
      const std::vector<int64>& expected_ms_utcs,
      DatetimeGranularity expected_granularity,
      std::vector<std::vector<DatetimeComponent>> datetime_components,
      bool anchor_start_end = false,
      const std::string& timezone = "Europe/Zurich",
      const std::string& locales = "en-US",
      AnnotationUsecase annotation_usecase =
          AnnotationUsecase_ANNOTATION_USECASE_SMART);

  bool ParsesCorrectly(
      const std::string& marked_text, const int64 expected_ms_utc,
      DatetimeGranularity expected_granularity,
      std::vector<std::vector<DatetimeComponent>> datetime_components,
      bool anchor_start_end = false,
      const std::string& timezone = "Europe/Zurich",
      const std::string& locales = "en-US",
      AnnotationUsecase annotation_usecase =
          AnnotationUsecase_ANNOTATION_USECASE_SMART);

  bool ParsesCorrectlyGerman(
      const std::string& marked_text,
      const std::vector<int64>& expected_ms_utcs,
      DatetimeGranularity expected_granularity,
      std::vector<std::vector<DatetimeComponent>> datetime_components);

  bool ParsesCorrectlyGerman(
      const std::string& marked_text, const int64 expected_ms_utc,
      DatetimeGranularity expected_granularity,
      std::vector<std::vector<DatetimeComponent>> datetime_components);

  bool ParsesCorrectlyChinese(
      const std::string& marked_text, const int64 expected_ms_utc,
      DatetimeGranularity expected_granularity,
      std::vector<std::vector<DatetimeComponent>> datetime_components);

  // Exposes the date time parser for tests and evaluations.
  virtual const DatetimeParser* DatetimeParserForTests() const = 0;
};

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ANNOTATOR_DATETIME_TESTING_BASE_PARSER_TEST_H_
