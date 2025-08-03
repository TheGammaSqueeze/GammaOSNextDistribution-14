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

#ifndef LIBTEXTCLASSIFIER_ANNOTATOR_DATETIME_GRAMMAR_PARSER_H_
#define LIBTEXTCLASSIFIER_ANNOTATOR_DATETIME_GRAMMAR_PARSER_H_

#include <string>
#include <vector>

#include "annotator/datetime/datetime-grounder.h"
#include "annotator/datetime/parser.h"
#include "annotator/model_generated.h"
#include "annotator/types.h"
#include "utils/base/statusor.h"
#include "utils/grammar/analyzer.h"
#include "utils/i18n/locale-list.h"
#include "utils/utf8/unicodetext.h"

namespace libtextclassifier3 {

// Parses datetime expressions in the input and resolves them to actual absolute
// time.
class GrammarDatetimeParser : public DatetimeParser {
 public:
  explicit GrammarDatetimeParser(const grammar::Analyzer& analyzer,
                                 const DatetimeGrounder& datetime_grounder,
                                 const float target_classification_score,
                                 const float priority_score,
                                 ModeFlag enabled_modes);

  // Parses the dates in 'input' and fills result. Makes sure that the results
  // do not overlap.
  // If 'anchor_start_end' is true the extracted results need to start at the
  // beginning of 'input' and end at the end of it.
  StatusOr<std::vector<DatetimeParseResultSpan>> Parse(
      const std::string& input, int64 reference_time_ms_utc,
      const std::string& reference_timezone, const LocaleList& locale_list,
      ModeFlag mode, AnnotationUsecase annotation_usecase,
      bool anchor_start_end) const override;

  // Same as above but takes UnicodeText.
  StatusOr<std::vector<DatetimeParseResultSpan>> Parse(
      const UnicodeText& input, int64 reference_time_ms_utc,
      const std::string& reference_timezone, const LocaleList& locale_list,
      ModeFlag mode, AnnotationUsecase annotation_usecase,
      bool anchor_start_end) const override;

 private:
  const grammar::Analyzer& analyzer_;
  const DatetimeGrounder& datetime_grounder_;
  const float target_classification_score_;
  const float priority_score_;
  const ModeFlag enabled_modes_;
};

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ANNOTATOR_DATETIME_GRAMMAR_PARSER_H_
