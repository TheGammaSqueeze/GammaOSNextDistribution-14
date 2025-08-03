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

#include "utils/tflite/skipgram_finder.h"

#include <cctype>
#include <deque>
#include <string>
#include <vector>

#include "utils/strings/utf8.h"
#include "utils/utf8/unilib-common.h"
#include "absl/container/flat_hash_map.h"
#include "absl/container/flat_hash_set.h"
#include "absl/strings/match.h"
#include "absl/strings/str_split.h"
#include "absl/strings/string_view.h"
#include "tensorflow/lite/string_util.h"

namespace libtextclassifier3 {
namespace {

using ::tflite::StringRef;

void PreprocessToken(std::string& token) {
  size_t in = 0;
  size_t out = 0;
  while (in < token.size()) {
    const char* in_data = token.data() + in;
    const int n = GetNumBytesForUTF8Char(in_data);
    if (n < 0 || n > token.size() - in) {
      // Invalid Utf8 sequence.
      break;
    }
    in += n;
    const char32 r = ValidCharToRune(in_data);
    if (IsPunctuation(r)) {
      continue;
    }
    const char32 rl = ToLower(r);
    char output_buffer[4];
    int encoded_length = ValidRuneToChar(rl, output_buffer);
    if (encoded_length > n) {
      // This is a hack, but there are exactly two unicode characters whose
      // lowercase versions have longer UTF-8 encodings (0x23a to 0x2c65,
      // 0x23e to 0x2c66).  So, to avoid sizing issues, they're not lowercased.
      encoded_length = ValidRuneToChar(r, output_buffer);
    }
    memcpy(token.data() + out, output_buffer, encoded_length);
    out += encoded_length;
  }

  size_t remaining = token.size() - in;
  if (remaining > 0) {
    memmove(token.data() + out, token.data() + in, remaining);
    out += remaining;
  }
  token.resize(out);
}

}  // namespace

void SkipgramFinder::AddSkipgram(const std::string& skipgram, int category) {
  std::vector<std::string> tokens = absl::StrSplit(skipgram, ' ');

  // Store the skipgram in a trie-like structure that uses tokens as the
  // edge labels, instead of characters.  Each node represents a skipgram made
  // from the tokens used to reach the node, and stores the categories the
  // skipgram is associated with.
  TrieNode* cur = &skipgram_trie_;
  for (auto& token : tokens) {
    if (absl::EndsWith(token, ".*")) {
      token.resize(token.size() - 2);
      PreprocessToken(token);
      auto iter = cur->prefix_to_node.find(token);
      if (iter != cur->prefix_to_node.end()) {
        cur = &iter->second;
      } else {
        cur = &cur->prefix_to_node
                   .emplace(std::piecewise_construct,
                            std::forward_as_tuple(token), std::make_tuple<>())
                   .first->second;
      }
      continue;
    }

    PreprocessToken(token);
    auto iter = cur->token_to_node.find(token);
    if (iter != cur->token_to_node.end()) {
      cur = &iter->second;
    } else {
      cur = &cur->token_to_node
                 .emplace(std::piecewise_construct,
                          std::forward_as_tuple(token), std::make_tuple<>())
                 .first->second;
    }
  }
  cur->categories.insert(category);
}

absl::flat_hash_set<int> SkipgramFinder::FindSkipgrams(
    const std::string& input) const {
  std::vector<std::string> tokens = absl::StrSplit(input, ' ');
  std::vector<absl::string_view> sv_tokens;
  sv_tokens.reserve(tokens.size());
  for (auto& token : tokens) {
    PreprocessToken(token);
    sv_tokens.emplace_back(token.data(), token.size());
  }
  return FindSkipgrams(sv_tokens);
}

absl::flat_hash_set<int> SkipgramFinder::FindSkipgrams(
    const std::vector<StringRef>& tokens) const {
  std::vector<absl::string_view> sv_tokens;
  sv_tokens.reserve(tokens.size());
  for (auto& token : tokens) {
    sv_tokens.emplace_back(token.str, token.len);
  }
  return FindSkipgrams(sv_tokens);
}

absl::flat_hash_set<int> SkipgramFinder::FindSkipgrams(
    const std::vector<absl::string_view>& tokens) const {
  absl::flat_hash_set<int> categories;

  // Tracks skipgram prefixes and the index of their last token.
  std::deque<std::pair<int, const TrieNode*>> indices_and_skipgrams;

  for (int token_i = 0; token_i < tokens.size(); token_i++) {
    const absl::string_view& token = tokens[token_i];

    std::vector<absl::string_view> token_prefixes;
    {
      const char* s = token.data();
      int n = token.size();
      while (n > 0) {
        const int rlen = GetNumBytesForUTF8Char(s);
        if (rlen < 0 || rlen > n) {
          // Invalid UTF8.
          break;
        }
        n -= rlen;
        s += rlen;
        token_prefixes.emplace_back(token.data(), token.size() - n);
      }
    }

    // Drop any skipgrams prefixes which would skip more than `max_skip_size_`
    // tokens between the end of the prefix and the current token.
    while (!indices_and_skipgrams.empty()) {
      if (indices_and_skipgrams.front().first + max_skip_size_ + 1 < token_i) {
        indices_and_skipgrams.pop_front();
      } else {
        break;
      }
    }

    // Check if we can form a valid skipgram prefix (or skipgram) by adding
    // the current token to any of the existing skipgram prefixes, or
    // if the current token is a valid skipgram prefix (or skipgram).
    size_t size = indices_and_skipgrams.size();
    for (size_t skipgram_i = 0; skipgram_i <= size; skipgram_i++) {
      const auto& node = skipgram_i < size
                             ? *indices_and_skipgrams[skipgram_i].second
                             : skipgram_trie_;

      auto iter = node.token_to_node.find(token);
      if (iter != node.token_to_node.end()) {
        categories.insert(iter->second.categories.begin(),
                          iter->second.categories.end());
        indices_and_skipgrams.push_back(std::make_pair(token_i, &iter->second));
      }

      for (const auto& token_prefix : token_prefixes) {
        auto iter = node.prefix_to_node.find(token_prefix);
        if (iter != node.prefix_to_node.end()) {
          categories.insert(iter->second.categories.begin(),
                            iter->second.categories.end());
          indices_and_skipgrams.push_back(
              std::make_pair(token_i, &iter->second));
        }
      }
    }
  }

  return categories;
}

}  // namespace libtextclassifier3
