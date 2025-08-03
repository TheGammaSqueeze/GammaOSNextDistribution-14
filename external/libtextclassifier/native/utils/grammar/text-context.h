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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_TEXT_CONTEXT_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_TEXT_CONTEXT_H_

#include <vector>

#include "annotator/types.h"
#include "utils/i18n/locale.h"
#include "utils/utf8/unicodetext.h"

namespace libtextclassifier3::grammar {

// Input to the parser.
struct TextContext {
  // Returns a view on a span of the text.
  const UnicodeText Span(const CodepointSpan& span) const {
    return text.Substring(codepoints[span.first], codepoints[span.second],
                          /*do_copy=*/false);
  }

  // The input text.
  UnicodeText text;

  // Pre-enumerated codepoints for fast substring extraction.
  std::vector<UnicodeText::const_iterator> codepoints;

  // The tokenized input text.
  std::vector<Token> tokens;

  // Locales of the input text.
  std::vector<Locale> locales;

  // Text annotations.
  std::vector<AnnotatedSpan> annotations;

  // The span of tokens to consider.
  TokenSpan context_span;
};

};  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_TEXT_CONTEXT_H_
