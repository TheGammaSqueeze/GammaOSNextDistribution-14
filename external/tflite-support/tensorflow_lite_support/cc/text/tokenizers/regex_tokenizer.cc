/* Copyright 2020 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

#include "tensorflow_lite_support/cc/text/tokenizers/regex_tokenizer.h"

#include <iostream>
#include <regex>

#include "absl/strings/str_cat.h"
#include "absl/strings/substitute.h"
#include "tensorflow_lite_support/cc/utils/common_utils.h"
namespace tflite {
namespace support {
namespace text {
namespace tokenizer {

namespace {
constexpr char kStart[] = "<START>";
constexpr char kPad[] = "<PAD>";
constexpr char kUnknown[] = "<UNKNOWN>";

void buildIndexTokenMap(
    const absl::node_hash_map<std::string, int>& token_index_map,
    absl::node_hash_map<int, absl::string_view>* index_token_map) {
  for (const auto& token : token_index_map) {
    (*index_token_map)[token.second] = token.first;
  }
}

}  // namespace

RegexTokenizer::RegexTokenizer(const std::string& regex_pattern,
                               const std::string& path_to_vocab)
    : delim_re_{absl::Substitute("($0)", regex_pattern)},
      token_index_map_{utils::LoadVocabAndIndexFromFile(path_to_vocab)} {
  buildIndexTokenMap(token_index_map_, &index_token_map_);
}

RegexTokenizer::RegexTokenizer(const std::string& regex_pattern,
                               const char* vocab_buffer_data,
                               size_t vocab_buffer_size)
    : delim_re_{absl::Substitute("($0)", regex_pattern)},
      token_index_map_{utils::LoadVocabAndIndexFromBuffer(vocab_buffer_data,
                                                          vocab_buffer_size)} {
  buildIndexTokenMap(token_index_map_, &index_token_map_);
}

TokenizerResult RegexTokenizer::Tokenize(const std::string& input) {
  TokenizerResult result;

  // Keep looking for split points until we have reached the end of the input.
  // TODO (ag/17748161): Using smatch here introduces inefficient string copying; optimize if necessary.
  std::string leftover = input;
  std::smatch token;
  while(std::regex_search(leftover, token, delim_re_)) {
    if (token.length() > 0) {
        result.subwords.push_back(token.prefix().str());
    }
    leftover = token.suffix().str();
  }

  // Close the last token.
  if (!leftover.empty()) {
    result.subwords.push_back(leftover);
  }

  return result;
}

bool RegexTokenizer::LookupId(absl::string_view key, int* result) const {
  auto it = token_index_map_.find(key);
  if (it == token_index_map_.end()) {
    return false;
  }
  *result = it->second;
  return true;
}

bool RegexTokenizer::LookupWord(int vocab_id, absl::string_view* result) const {
  auto it = index_token_map_.find(vocab_id);
  if (it == index_token_map_.end()) {
    return false;
  }
  *result = it->second;
  return true;
}

bool RegexTokenizer::GetStartToken(int* start_token) {
  return LookupId(kStart, start_token);
}

bool RegexTokenizer::GetPadToken(int* pad_token) {
  return LookupId(kPad, pad_token);
}

bool RegexTokenizer::GetUnknownToken(int* unknown_token) {
  return LookupId(kUnknown, unknown_token);
}

}  // namespace tokenizer
}  // namespace text
}  // namespace support
}  // namespace tflite
