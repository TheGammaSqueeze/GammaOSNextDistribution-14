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

// Utilities for tests.

#ifndef LIBTEXTCLASSIFIER_UTILS_TOKENIZER_UTILS_H_
#define LIBTEXTCLASSIFIER_UTILS_TOKENIZER_UTILS_H_

#include <string>

#include "annotator/types.h"
#include "utils/codepoint-range.h"
#include "utils/strings/utf8.h"
#include "utils/utf8/unicodetext.h"
#include "absl/container/flat_hash_set.h"
#include "absl/strings/string_view.h"

namespace libtextclassifier3 {

struct FilterResult {
  // Whether split on this codepoint.
  bool to_split;
  // If the codepoint is used to split the text, whether to output it as a
  // token.
  bool to_keep;
};

// Returns a list of Tokens for a given input string, by tokenizing on space.
std::vector<Token> TokenizeOnSpace(const std::string& text);

// Returns a list of Tokens for a given input string, by tokenizing on the
// given set of delimiter codepoints.
// If create_tokens_for_non_space_delimiters is true, create tokens for
// delimiters which are not white spaces. For example "This, is" -> {"This",
// ",", "is"}.
std::vector<Token> TokenizeOnDelimiters(
    const std::string& text, const absl::flat_hash_set<char32>& delimiters,
    bool create_tokens_for_non_space_delimiters = false);

// This replicates how the original bert_tokenizer from the tflite-support
// library pretokenize text by using regex_split with these default regexes.
// It splits the text on spaces, punctuations and chinese characters and
// output all the tokens except spaces.
// So far, the only difference between this and the original implementation
// we are aware of is that the original regexes has 8 ranges of chinese
// unicodes. We have all these 8 ranges plus two extra ranges.
std::vector<Token> TokenizeOnWhiteSpacePunctuationAndChineseLetter(
    const absl::string_view text);

// Returns a list of Tokens for a given input string, by tokenizing on the
// given filter function. Caller can control which codepoint to split and
// whether a delimiter should be output as a token.
template <typename FilterFn>
std::vector<Token> TokenizeWithFilter(const absl::string_view input,
                                      FilterFn filter) {
  const UnicodeText input_unicode = UTF8ToUnicodeText(input, /*do_copy=*/false);
  std::vector<Token> tokens;
  UnicodeText::const_iterator start_it = input_unicode.begin();
  int token_start_codepoint = 0;
  int codepoint_idx = 0;

  for (auto it = input_unicode.begin(); it != input_unicode.end(); ++it) {
    const char32 code_point = *it;
    FilterResult filter_result = filter(code_point);
    if (filter_result.to_split) {
      const std::string token_text = UnicodeText::UTF8Substring(start_it, it);
      if (!token_text.empty()) {
        tokens.push_back(
            Token{token_text, token_start_codepoint, codepoint_idx});
      }
      if (filter_result.to_keep) {
        const std::string delimiter =
            UnicodeText::UTF8Substring(it, std::next(it));
        tokens.push_back(Token{delimiter, codepoint_idx, codepoint_idx + 1});
      }
      start_it = std::next(it);
      token_start_codepoint = codepoint_idx + 1;
    }
    codepoint_idx++;
  }
  // Flush the last token if any.
  if (start_it != input_unicode.end()) {
    const std::string token_text =
        UnicodeText::UTF8Substring(start_it, input_unicode.end());
    tokens.push_back(Token{token_text, token_start_codepoint, codepoint_idx});
  }
  return tokens;
}

}  // namespace  libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_UTILS_TOKENIZER_UTILS_H_
