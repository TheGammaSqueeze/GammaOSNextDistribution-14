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

#include "annotator/strip-unpaired-brackets.h"

#include <iterator>

#include "utils/base/logging.h"
#include "utils/utf8/unicodetext.h"

namespace libtextclassifier3 {

CodepointSpan StripUnpairedBrackets(
    const UnicodeText::const_iterator& span_begin,
    const UnicodeText::const_iterator& span_end, CodepointSpan span,
    const UniLib& unilib) {
  if (span_begin == span_end || !span.IsValid() || span.IsEmpty()) {
    return span;
  }

  UnicodeText::const_iterator begin = span_begin;
  const UnicodeText::const_iterator end = span_end;
  const char32 begin_char = *begin;
  const char32 paired_begin_char = unilib.GetPairedBracket(begin_char);
  if (paired_begin_char != begin_char) {
    if (!unilib.IsOpeningBracket(begin_char) ||
        std::find(begin, end, paired_begin_char) == end) {
      ++begin;
      ++span.first;
    }
  }

  if (span.first == span.second) {
    return span;
  }

  const char32 end_char = *std::prev(end);
  const char32 paired_end_char = unilib.GetPairedBracket(end_char);
  if (paired_end_char != end_char) {
    if (!unilib.IsClosingBracket(end_char) ||
        std::find(begin, end, paired_end_char) == end) {
      --span.second;
    }
  }

  // Should not happen, but let's make sure.
  if (span.first > span.second) {
    TC3_LOG(WARNING) << "Inverse indices result: " << span.first << ", "
                     << span.second;
    span.second = span.first;
  }

  return span;
}

CodepointSpan StripUnpairedBrackets(const UnicodeText& context,
                                    CodepointSpan span, const UniLib& unilib) {
  if (!span.IsValid() || span.IsEmpty()) {
    return span;
  }
  const UnicodeText span_text = UnicodeText::Substring(
      context, span.first, span.second, /*do_copy=*/false);
  return StripUnpairedBrackets(span_text.begin(), span_text.end(), span,
                               unilib);
}

CodepointSpan StripUnpairedBrackets(const std::string& context,
                                    CodepointSpan span, const UniLib& unilib) {
  return StripUnpairedBrackets(UTF8ToUnicodeText(context, /*do_copy=*/false),
                               span, unilib);
}

}  // namespace libtextclassifier3
