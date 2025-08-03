// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "icing/tokenization/plain-tokenizer.h"

#include <algorithm>
#include <cstdint>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/util/character-iterator.h"
#include "icing/util/i18n-utils.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {
// Helper function to validate a term.
// A term is valid if:
//   1. it's not empty
//   2. it's not a whitespace
//   3. it's not a punctuation mark
//
// TODO(b/141007791): figure out how we'd like to support special characters
// like "+", "&", "@", "#" in indexing and query tokenizers.
bool IsValidTerm(std::string_view term) {
  if (term.empty()) {
    return false;
  }
  // Gets the first unicode character. We can know what the whole term is by
  // checking only the first character.
  return !i18n_utils::IsWhitespaceAt(term, /*position=*/0) &&
         !i18n_utils::IsPunctuationAt(term, /*position=*/0);
}
}  // namespace

// Plain tokenizer applies its rules to the results from language segmenter. It
// simply filters out invalid terms from language segmenter and returns
// everything else as tokens. Please refer to IsValidTerm() above for what terms
// are valid.
class PlainTokenIterator : public Tokenizer::Iterator {
 public:
  explicit PlainTokenIterator(
      std::unique_ptr<LanguageSegmenter::Iterator> base_iterator)
      : base_iterator_(std::move(base_iterator)) {}

  bool Advance() override {
    bool found_next_valid_term = false;
    while (!found_next_valid_term && base_iterator_->Advance()) {
      current_term_ = base_iterator_->GetTerm();
      found_next_valid_term = IsValidTerm(current_term_);
    }
    return found_next_valid_term;
  }

  std::vector<Token> GetTokens() const override {
    std::vector<Token> result;
    if (!current_term_.empty()) {
      result.push_back(Token(Token::Type::REGULAR, current_term_));
    }
    return result;
  }

  libtextclassifier3::StatusOr<CharacterIterator> CalculateTokenStart()
      override {
    return base_iterator_->CalculateTermStart();
  }

  libtextclassifier3::StatusOr<CharacterIterator> CalculateTokenEndExclusive()
      override {
    return base_iterator_->CalculateTermEndExclusive();
  }

  bool ResetToTokenStartingAfter(int32_t utf32_offset) override {
    if (!base_iterator_->ResetToTermStartingAfterUtf32(utf32_offset).ok()) {
      return false;
    }
    current_term_ = base_iterator_->GetTerm();
    if (!IsValidTerm(current_term_)) {
      // If the current value isn't valid, advance to the next valid value.
      return Advance();
    }
    return true;
  }

  bool ResetToTokenEndingBefore(int32_t utf32_offset) override {
    ICING_ASSIGN_OR_RETURN(
        utf32_offset,
        base_iterator_->ResetToTermEndingBeforeUtf32(utf32_offset), false);
    current_term_ = base_iterator_->GetTerm();
    while (!IsValidTerm(current_term_)) {
      // Haven't found a valid term yet. Retrieve the term prior to this one
      // from the segmenter.
      ICING_ASSIGN_OR_RETURN(
          utf32_offset,
          base_iterator_->ResetToTermEndingBeforeUtf32(utf32_offset), false);
      current_term_ = base_iterator_->GetTerm();
    }
    return true;
  }

  bool ResetToStart() override {
    if (!base_iterator_->ResetToStartUtf32().ok()) {
      return false;
    }
    current_term_ = base_iterator_->GetTerm();
    if (!IsValidTerm(current_term_)) {
      // If the current value isn't valid, advance to the next valid value.
      return Advance();
    }
    return true;
  }

 private:
  std::unique_ptr<LanguageSegmenter::Iterator> base_iterator_;
  std::string_view current_term_;
};

libtextclassifier3::StatusOr<std::unique_ptr<Tokenizer::Iterator>>
PlainTokenizer::Tokenize(std::string_view text) const {
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<LanguageSegmenter::Iterator> base_iterator,
      language_segmenter_.Segment(text));
  return std::make_unique<PlainTokenIterator>(std::move(base_iterator));
}

libtextclassifier3::StatusOr<std::vector<Token>> PlainTokenizer::TokenizeAll(
    std::string_view text) const {
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<Tokenizer::Iterator> iterator,
                         Tokenize(text));
  std::vector<Token> tokens;
  while (iterator->Advance()) {
    std::vector<Token> batch_tokens = iterator->GetTokens();
    tokens.insert(tokens.end(), batch_tokens.begin(), batch_tokens.end());
  }
  return tokens;
}

}  // namespace lib
}  // namespace icing
