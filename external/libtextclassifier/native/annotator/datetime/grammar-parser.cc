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

#include "annotator/datetime/grammar-parser.h"

#include <set>
#include <unordered_set>

#include "annotator/datetime/datetime-grounder.h"
#include "annotator/model_generated.h"
#include "annotator/types.h"
#include "utils/grammar/analyzer.h"
#include "utils/grammar/evaluated-derivation.h"
#include "utils/grammar/parsing/derivation.h"

using ::libtextclassifier3::grammar::EvaluatedDerivation;
using ::libtextclassifier3::grammar::datetime::UngroundedDatetime;

namespace libtextclassifier3 {

GrammarDatetimeParser::GrammarDatetimeParser(
    const grammar::Analyzer& analyzer,
    const DatetimeGrounder& datetime_grounder,
    const float target_classification_score, const float priority_score,
    ModeFlag enabled_modes)
    : analyzer_(analyzer),
      datetime_grounder_(datetime_grounder),
      target_classification_score_(target_classification_score),
      priority_score_(priority_score),
      enabled_modes_(enabled_modes) {}

StatusOr<std::vector<DatetimeParseResultSpan>> GrammarDatetimeParser::Parse(
    const std::string& input, const int64 reference_time_ms_utc,
    const std::string& reference_timezone, const LocaleList& locale_list,
    ModeFlag mode, AnnotationUsecase annotation_usecase,
    bool anchor_start_end) const {
  return Parse(UTF8ToUnicodeText(input, /*do_copy=*/false),
               reference_time_ms_utc, reference_timezone, locale_list, mode,
               annotation_usecase, anchor_start_end);
}

StatusOr<std::vector<DatetimeParseResultSpan>> GrammarDatetimeParser::Parse(
    const UnicodeText& input, const int64 reference_time_ms_utc,
    const std::string& reference_timezone, const LocaleList& locale_list,
    ModeFlag mode, AnnotationUsecase annotation_usecase,
    bool anchor_start_end) const {
  if (!(enabled_modes_ & mode)) {
    return std::vector<DatetimeParseResultSpan>();
  }

  std::vector<DatetimeParseResultSpan> results;
  UnsafeArena arena(/*block_size=*/16 << 10);
  std::vector<Locale> locales = locale_list.GetLocales();
  // If the locale list is empty then datetime regex expression will still
  // execute but in grammar based parser the rules are associated with local
  // and engine will not run if the locale list is empty. In an unlikely
  // scenario when locale is not mentioned fallback to en-*.
  if (locales.empty()) {
    locales.emplace_back(Locale::FromBCP47("en"));
  }
  TC3_ASSIGN_OR_RETURN(
      const std::vector<EvaluatedDerivation> evaluated_derivations,
      analyzer_.Parse(input, locales, &arena,
                      /*deduplicate_derivations=*/false));

  std::vector<EvaluatedDerivation> valid_evaluated_derivations;
  for (const EvaluatedDerivation& evaluated_derivation :
       evaluated_derivations) {
    if (evaluated_derivation.value) {
      if (evaluated_derivation.value->Has<flatbuffers::Table>()) {
        const UngroundedDatetime* ungrounded_datetime =
            evaluated_derivation.value->Table<UngroundedDatetime>();
        if (datetime_grounder_.IsValidUngroundedDatetime(ungrounded_datetime)) {
          valid_evaluated_derivations.emplace_back(evaluated_derivation);
        }
      }
    }
  }
  valid_evaluated_derivations =
      grammar::DeduplicateDerivations(valid_evaluated_derivations);
  for (const EvaluatedDerivation& evaluated_derivation :
       valid_evaluated_derivations) {
    if (evaluated_derivation.value) {
      if (evaluated_derivation.value->Has<flatbuffers::Table>()) {
        const UngroundedDatetime* ungrounded_datetime =
            evaluated_derivation.value->Table<UngroundedDatetime>();
        if ((ungrounded_datetime->annotation_usecases() &
             (1 << annotation_usecase)) == 0) {
          continue;
        }
        const StatusOr<std::vector<DatetimeParseResult>>&
            datetime_parse_results = datetime_grounder_.Ground(
                reference_time_ms_utc, reference_timezone,
                locale_list.GetReferenceLocale(), ungrounded_datetime);
        TC3_ASSIGN_OR_RETURN(
            const std::vector<DatetimeParseResult>& parse_datetime,
            datetime_parse_results);
        DatetimeParseResultSpan datetime_parse_result_span;
        datetime_parse_result_span.target_classification_score =
            target_classification_score_;
        datetime_parse_result_span.priority_score = priority_score_;
        datetime_parse_result_span.data.reserve(parse_datetime.size());
        datetime_parse_result_span.data.insert(
            datetime_parse_result_span.data.end(), parse_datetime.begin(),
            parse_datetime.end());
        datetime_parse_result_span.span =
            evaluated_derivation.parse_tree->codepoint_span;

        results.emplace_back(datetime_parse_result_span);
      }
    }
  }
  return results;
}
}  // namespace libtextclassifier3
