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

#ifndef LIBTEXTCLASSIFIER_ANNOTATOR_DATETIME_PARSER_H_
#define LIBTEXTCLASSIFIER_ANNOTATOR_DATETIME_PARSER_H_

#include <memory>
#include <string>
#include <vector>

#include "annotator/types.h"
#include "utils/base/statusor.h"
#include "utils/i18n/locale-list.h"
#include "utils/i18n/locale.h"
#include "utils/utf8/unicodetext.h"

namespace libtextclassifier3 {

// Parses datetime expressions in the input and resolves them to actual absolute
// time.
class DatetimeParser {
 public:
  virtual ~DatetimeParser() = default;

  // Parses the dates in 'input' and fills result. Makes sure that the results
  // do not overlap.
  // If 'anchor_start_end' is true the extracted results need to start at the
  // beginning of 'input' and end at the end of it.
  virtual StatusOr<std::vector<DatetimeParseResultSpan>> Parse(
      const std::string& input, int64 reference_time_ms_utc,
      const std::string& reference_timezone, const LocaleList& locale_list,
      ModeFlag mode, AnnotationUsecase annotation_usecase,
      bool anchor_start_end) const = 0;

  // Same as above but takes UnicodeText.
  virtual StatusOr<std::vector<DatetimeParseResultSpan>> Parse(
      const UnicodeText& input, int64 reference_time_ms_utc,
      const std::string& reference_timezone, const LocaleList& locale_list,
      ModeFlag mode, AnnotationUsecase annotation_usecase,
      bool anchor_start_end) const = 0;
};
}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ANNOTATOR_DATETIME_PARSER_H_
