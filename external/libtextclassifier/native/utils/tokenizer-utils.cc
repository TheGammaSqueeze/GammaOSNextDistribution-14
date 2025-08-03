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

#include "utils/tokenizer-utils.h"

#include <iterator>

#include "utils/codepoint-range.h"
#include "utils/strings/utf8.h"
#include "utils/utf8/unicodetext.h"
#include "utils/utf8/unilib-common.h"
#include "absl/container/flat_hash_set.h"

namespace libtextclassifier3 {

using libtextclassifier3::Token;

std::vector<Token> TokenizeOnSpace(const std::string& text) {
  return TokenizeOnDelimiters(text, {' '});
}

std::vector<Token> TokenizeOnDelimiters(
    const std::string& text, const absl::flat_hash_set<char32>& delimiters,
    bool create_tokens_for_non_space_delimiters) {
  return TokenizeWithFilter(text, [&](char32 codepoint) {
    bool to_split = delimiters.find(codepoint) != delimiters.end();
    bool to_keep =
        (create_tokens_for_non_space_delimiters) ? codepoint != ' ' : false;
    return FilterResult{to_split, to_keep};
  });
}

std::vector<Token> TokenizeOnWhiteSpacePunctuationAndChineseLetter(
    const absl::string_view text) {
  return TokenizeWithFilter(text, [](char32 codepoint) {
    bool is_whitespace = IsWhitespace(codepoint);
    bool to_split =
        is_whitespace || IsPunctuation(codepoint) || IsChineseLetter(codepoint);
    bool to_keep = !is_whitespace;
    return FilterResult{to_split, to_keep};
  });
}
}  // namespace  libtextclassifier3
