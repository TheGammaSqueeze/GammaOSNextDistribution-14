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

#include "utils/bert_tokenizer.h"

#include <string>
#include <vector>

#include "annotator/types.h"
#include "utils/tokenizer-utils.h"
#include "utils/utf8/unicodetext.h"
#include "utils/utf8/unilib.h"
#include "absl/strings/string_view.h"

namespace libtextclassifier3 {

namespace {

int SafeLookup(const std::vector<int>& vector, int index) {
  if (vector.empty()) {
    return 0;
  }
  index = std::max(index, 0);
  index = std::min(index, static_cast<int>(vector.size()) - 1);
  return vector[index];
}

}  // namespace

FlatHashMapBackedWordpiece::FlatHashMapBackedWordpiece(
    const std::vector<std::string>& vocab)
    : vocab_{vocab} {
  for (int i = 0; i < vocab_.size(); ++i) {
    index_map_[vocab_[i]] = i;
  }
}

LookupStatus FlatHashMapBackedWordpiece::Contains(absl::string_view key,
                                                  bool* value) const {
  *value = index_map_.contains(key);
  return LookupStatus();
}

bool FlatHashMapBackedWordpiece::LookupId(const absl::string_view key,
                                          int* result) const {
  auto it = index_map_.find(key);
  if (it == index_map_.end()) {
    return false;
  }
  *result = it->second;
  return true;
}

bool FlatHashMapBackedWordpiece::LookupWord(int vocab_id,
                                            absl::string_view* result) const {
  if (vocab_id >= vocab_.size() || vocab_id < 0) {
    return false;
  }
  *result = vocab_[vocab_id];
  return true;
}

TokenizerResult BertTokenizer::Tokenize(const std::string& input) {
  return TokenizeIntoWordpieces(input);
}

WordpieceTokenizerResult BertTokenizer::TokenizeIntoWordpieces(
    const std::string& input) {
  std::vector<Token> tokens =
      TokenizeOnWhiteSpacePunctuationAndChineseLetter(input);
  return TokenizeIntoWordpieces(tokens);
}

WordpieceTokenizerResult BertTokenizer::TokenizeSingleToken(
    const std::string& token) {
  const UnicodeText token_unicode = UTF8ToUnicodeText(token, /*do_copy=*/false);
  std::vector<Token> tokens = {
      Token(token, 0, token_unicode.size_codepoints())};
  return TokenizeIntoWordpieces(tokens);
}

WordpieceTokenizerResult BertTokenizer::TokenizeIntoWordpieces(
    const std::vector<Token>& tokens) {
  WordpieceTokenizerResult result;
  std::vector<std::string>& subwords = result.subwords;

  for (int token_index = 0; token_index < tokens.size(); token_index++) {
    const Token& token = tokens[token_index];
    int num_word_pieces = 0;
    std::vector<int> wp_absolute_begin_offset;
    std::vector<int> wp_absolute_end_offset;
    LookupStatus status = WordpieceTokenize(
        token.value, options_.max_bytes_per_token,
        options_.max_chars_per_subtoken, options_.suffix_indicator,
        options_.use_unknown_token, options_.unknown_token,
        options_.split_unknown_chars, &vocab_, &subwords,
        &wp_absolute_begin_offset, &wp_absolute_end_offset, &num_word_pieces);
    const UnicodeText token_unicode =
        UTF8ToUnicodeText(token.value, /*do_copy=*/false);

    std::vector<int> byte_to_codepoint_offsets;
    int byte_to_codepoint_offset = 0;
    for (const auto& it : token_unicode.Codepoints()) {
      byte_to_codepoint_offsets.resize(
          it.utf8_data() + it.utf8_length() - token_unicode.data(),
          byte_to_codepoint_offset++);
    }
    byte_to_codepoint_offsets.push_back(byte_to_codepoint_offset);

    for (const int offset : wp_absolute_begin_offset) {
      result.wp_begin_offset.push_back(
          token.start + SafeLookup(byte_to_codepoint_offsets, offset));
    }
    for (const int offset : wp_absolute_end_offset) {
      result.wp_end_offset.push_back(
          token.start + SafeLookup(byte_to_codepoint_offsets, offset));
    }
    result.row_lengths.push_back(num_word_pieces);

    if (!status.success) {
      return result;
    }
  }

  return result;
}

// This replicates how the original bert_tokenizer from the tflite-support
// library pretokenize text by using regex_split with these default regexes.
// It splits the text on spaces, punctuations and chinese characters and
// output all the tokens except spaces.
// So far, the only difference between this and the original implementation
// we are aware of is that the original regexes has 8 ranges of chinese
// unicodes. We have all these 8 ranges plus two extra ranges.
std::vector<std::string> BertTokenizer::PreTokenize(
    const absl::string_view input) {
  const std::vector<Token> tokens =
      TokenizeOnWhiteSpacePunctuationAndChineseLetter(input);
  std::vector<std::string> token_texts;
  std::transform(tokens.begin(), tokens.end(), std::back_inserter(token_texts),
                 [](Token const& token) { return std::move(token.value); });

  return token_texts;
}

}  // namespace libtextclassifier3
