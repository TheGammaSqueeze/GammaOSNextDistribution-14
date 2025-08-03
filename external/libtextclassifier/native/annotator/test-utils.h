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

#ifndef LIBTEXTCLASSIFIER_ANNOTATOR_TEST_UTILS_H_
#define LIBTEXTCLASSIFIER_ANNOTATOR_TEST_UTILS_H_

#include "annotator/types.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {

using ::testing::Value;

MATCHER_P3(IsAnnotatedSpan, start, end, best_class, "") {
  const std::string first_result = arg.classification.empty()
                                       ? "<INVALID RESULTS>"
                                       : arg.classification[0].collection;
  return Value(arg.span, CodepointSpan(start, end)) &&
         Value(first_result, best_class);
}

MATCHER_P(IsAnnotationWithType, best_class, "") {
  const std::string first_result = arg.classification.empty()
                                       ? "<INVALID RESULTS>"
                                       : arg.classification[0].collection;
  return Value(first_result, best_class);
}

MATCHER_P2(IsDateResult, time_ms_utc, granularity, "") {
  return Value(arg.collection, "date") &&
         Value(arg.datetime_parse_result.time_ms_utc, time_ms_utc) &&
         Value(arg.datetime_parse_result.granularity, granularity);
}

MATCHER_P2(IsDatetimeResult, time_ms_utc, granularity, "") {
  return Value(arg.collection, "datetime") &&
         Value(arg.datetime_parse_result.time_ms_utc, time_ms_utc) &&
         Value(arg.datetime_parse_result.granularity, granularity);
}

MATCHER_P3(IsDurationSpan, start, end, duration_ms, "") {
  if (arg.classification.empty()) {
    return false;
  }
  return ExplainMatchResult(IsAnnotatedSpan(start, end, "duration"), arg,
                            result_listener) &&
         arg.classification[0].duration_ms == duration_ms;
}

MATCHER_P4(IsDatetimeSpan, start, end, time_ms_utc, granularity, "") {
  if (arg.classification.empty()) {
    return false;
  }
  return ExplainMatchResult(IsAnnotatedSpan(start, end, "datetime"), arg,
                            result_listener) &&
         arg.classification[0].datetime_parse_result.time_ms_utc ==
             time_ms_utc &&
         arg.classification[0].datetime_parse_result.granularity == granularity;
}

MATCHER_P2(IsBetween, low, high, "") { return low < arg && arg < high; }

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ANNOTATOR_TEST_UTILS_H_
