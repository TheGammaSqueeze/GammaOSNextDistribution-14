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

#include "utils/grammar/parsing/lexer.h"

namespace libtextclassifier3::grammar {

Symbol::Type Lexer::GetSymbolType(const UnicodeText::const_iterator& it) const {
  if (unilib_.IsPunctuation(*it)) {
    return Symbol::Type::TYPE_PUNCTUATION;
  } else if (unilib_.IsDigit(*it)) {
    return Symbol::Type::TYPE_DIGITS;
  } else {
    return Symbol::Type::TYPE_TERM;
  }
}

void Lexer::AppendTokenSymbols(const StringPiece value, int match_offset,
                               const CodepointSpan codepoint_span,
                               std::vector<Symbol>* symbols) const {
  // Possibly split token.
  UnicodeText token_unicode = UTF8ToUnicodeText(value.data(), value.size(),
                                                /*do_copy=*/false);
  int next_match_offset = match_offset;
  auto token_end = token_unicode.end();
  auto it = token_unicode.begin();
  Symbol::Type type = GetSymbolType(it);
  CodepointIndex sub_token_start = codepoint_span.first;
  while (it != token_end) {
    auto next = std::next(it);
    int num_codepoints = 1;
    Symbol::Type next_type;
    while (next != token_end) {
      next_type = GetSymbolType(next);
      if (type == Symbol::Type::TYPE_PUNCTUATION || next_type != type) {
        break;
      }
      ++next;
      ++num_codepoints;
    }
    symbols->emplace_back(
        type, CodepointSpan{sub_token_start, sub_token_start + num_codepoints},
        /*match_offset=*/next_match_offset,
        /*lexeme=*/
        StringPiece(it.utf8_data(), next.utf8_data() - it.utf8_data()));
    next_match_offset = sub_token_start + num_codepoints;
    it = next;
    type = next_type;
    sub_token_start = next_match_offset;
  }
}

}  // namespace libtextclassifier3::grammar
