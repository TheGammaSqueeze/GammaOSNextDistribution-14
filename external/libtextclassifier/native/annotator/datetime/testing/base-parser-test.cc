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

#include "annotator/datetime/testing/base-parser-test.h"

#include <memory>
#include <string>
#include <vector>

#include "utils/i18n/locale-list.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

using std::vector;
using testing::ElementsAreArray;

namespace libtextclassifier3 {

bool DateTimeParserTest::HasNoResult(const std::string& text,
                                     bool anchor_start_end,
                                     const std::string& timezone,
                                     AnnotationUsecase annotation_usecase) {
  StatusOr<std::vector<DatetimeParseResultSpan>> results_status =
      DatetimeParserForTests()->Parse(
          text, 0, timezone, LocaleList::ParseFrom(/*locale_tags=*/""),
          ModeFlag_ANNOTATION, annotation_usecase, anchor_start_end);
  if (!results_status.ok()) {
    TC3_LOG(ERROR) << text;
    TC3_CHECK(false);
  }
  return results_status.ValueOrDie().empty();
}

bool DateTimeParserTest::ParsesCorrectly(
    const std::string& marked_text, const vector<int64>& expected_ms_utcs,
    DatetimeGranularity expected_granularity,
    vector<vector<DatetimeComponent>> datetime_components,
    bool anchor_start_end, const std::string& timezone,
    const std::string& locales, AnnotationUsecase annotation_usecase) {
  const UnicodeText marked_text_unicode =
      UTF8ToUnicodeText(marked_text, /*do_copy=*/false);
  auto brace_open_it =
      std::find(marked_text_unicode.begin(), marked_text_unicode.end(), '{');
  auto brace_end_it =
      std::find(marked_text_unicode.begin(), marked_text_unicode.end(), '}');
  TC3_CHECK(brace_open_it != marked_text_unicode.end());
  TC3_CHECK(brace_end_it != marked_text_unicode.end());

  std::string text;
  text +=
      UnicodeText::UTF8Substring(marked_text_unicode.begin(), brace_open_it);
  text += UnicodeText::UTF8Substring(std::next(brace_open_it), brace_end_it);
  text += UnicodeText::UTF8Substring(std::next(brace_end_it),
                                     marked_text_unicode.end());

  StatusOr<std::vector<DatetimeParseResultSpan>> results_status =
      DatetimeParserForTests()->Parse(
          text, 0, timezone, LocaleList::ParseFrom(locales),
          ModeFlag_ANNOTATION, annotation_usecase, anchor_start_end);
  if (!results_status.ok()) {
    TC3_LOG(ERROR) << text;
    TC3_CHECK(false);
  }
  // const std::vector<DatetimeParseResultSpan>& results =
  //     results_status.ValueOrDie();
  if (results_status.ValueOrDie().empty()) {
    TC3_LOG(ERROR) << "No results.";
    return false;
  }

  const int expected_start_index =
      std::distance(marked_text_unicode.begin(), brace_open_it);
  // The -1 below is to account for the opening bracket character.
  const int expected_end_index =
      std::distance(marked_text_unicode.begin(), brace_end_it) - 1;

  std::vector<DatetimeParseResultSpan> filtered_results;
  for (const DatetimeParseResultSpan& result : results_status.ValueOrDie()) {
    if (SpansOverlap(result.span, {expected_start_index, expected_end_index})) {
      filtered_results.push_back(result);
    }
  }
  std::vector<DatetimeParseResultSpan> expected{
      {{expected_start_index, expected_end_index},
       {},
       /*target_classification_score=*/1.0,
       /*priority_score=*/1.0}};
  expected[0].data.resize(expected_ms_utcs.size());
  for (int i = 0; i < expected_ms_utcs.size(); i++) {
    expected[0].data[i] = {expected_ms_utcs[i], expected_granularity,
                           datetime_components[i]};
  }

  const bool matches =
      testing::Matches(ElementsAreArray(expected))(filtered_results);
  if (!matches) {
    TC3_LOG(ERROR) << "Expected: " << expected[0];
    if (filtered_results.empty()) {
      TC3_LOG(ERROR) << "But got no results.";
    }
    TC3_LOG(ERROR) << "Actual: " << filtered_results[0];
  }

  return matches;
}

bool DateTimeParserTest::ParsesCorrectly(
    const std::string& marked_text, const int64 expected_ms_utc,
    DatetimeGranularity expected_granularity,
    vector<vector<DatetimeComponent>> datetime_components,
    bool anchor_start_end, const std::string& timezone,
    const std::string& locales, AnnotationUsecase annotation_usecase) {
  return ParsesCorrectly(marked_text, vector<int64>{expected_ms_utc},
                         expected_granularity, datetime_components,
                         anchor_start_end, timezone, locales,
                         annotation_usecase);
}

bool DateTimeParserTest::ParsesCorrectlyGerman(
    const std::string& marked_text, const vector<int64>& expected_ms_utcs,
    DatetimeGranularity expected_granularity,
    vector<vector<DatetimeComponent>> datetime_components) {
  return ParsesCorrectly(marked_text, expected_ms_utcs, expected_granularity,
                         datetime_components,
                         /*anchor_start_end=*/false,
                         /*timezone=*/"Europe/Zurich", /*locales=*/"de");
}

bool DateTimeParserTest::ParsesCorrectlyGerman(
    const std::string& marked_text, const int64 expected_ms_utc,
    DatetimeGranularity expected_granularity,
    vector<vector<DatetimeComponent>> datetime_components) {
  return ParsesCorrectly(marked_text, expected_ms_utc, expected_granularity,
                         datetime_components,
                         /*anchor_start_end=*/false,
                         /*timezone=*/"Europe/Zurich", /*locales=*/"de");
}

bool DateTimeParserTest::ParsesCorrectlyChinese(
    const std::string& marked_text, const int64 expected_ms_utc,
    DatetimeGranularity expected_granularity,
    vector<vector<DatetimeComponent>> datetime_components) {
  return ParsesCorrectly(marked_text, expected_ms_utc, expected_granularity,
                         datetime_components,
                         /*anchor_start_end=*/false,
                         /*timezone=*/"Europe/Zurich", /*locales=*/"zh");
}

}  // namespace libtextclassifier3
