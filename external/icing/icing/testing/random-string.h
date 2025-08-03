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

#ifndef ICING_TESTING_RANDOM_STRING_H_
#define ICING_TESTING_RANDOM_STRING_H_

#include <algorithm>
#include <random>
#include <string>

namespace icing {
namespace lib {

inline constexpr std::string_view kAlNumAlphabet =
    "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

// Average length of word in English is 4.7 characters.
inline constexpr int kAvgTokenLen = 5;
// Made up value. This results in a fairly reasonable language - the majority of
// generated words are 3-9 characters, ~3% of words are >=20 chars, and the
// longest ones are 27 chars, (roughly consistent with the longest,
// non-contrived English words
// https://en.wikipedia.org/wiki/Longest_word_in_English)
inline constexpr int kTokenStdDev = 7;

template <typename Gen>
std::string RandomString(const std::string_view alphabet, size_t len,
                         Gen* gen) {
  std::uniform_int_distribution<size_t> uniform(0u, alphabet.size() - 1);
  std::string result(len, '\0');
  std::generate(
      std::begin(result), std::end(result),
      [&gen, &alphabet, &uniform]() { return alphabet[uniform(*gen)]; });

  return result;
}

// Creates a vector containing num_words randomly-generated words for use by
// documents.
template <typename Rand>
std::vector<std::string> CreateLanguages(int num_words, Rand* r) {
  std::vector<std::string> language;
  std::normal_distribution<> norm_dist(kAvgTokenLen, kTokenStdDev);
  while (--num_words >= 0) {
    int word_length = 0;
    while (word_length < 1) {
      word_length = std::round(norm_dist(*r));
    }
    language.push_back(RandomString(kAlNumAlphabet, word_length, r));
  }
  return language;
}

// Returns a vector containing num_terms unique terms. Terms are created in
// non-random order starting with "a" to "z" to "aa" to "zz", etc.
std::vector<std::string> GenerateUniqueTerms(int num_terms);

}  // namespace lib
}  // namespace icing

#endif  // ICING_TESTING_RANDOM_STRING_H_
