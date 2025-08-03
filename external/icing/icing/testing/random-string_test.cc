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

#include "icing/testing/random-string.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;

namespace icing {
namespace lib {

namespace {

TEST(RandomStringTest, GenerateUniqueTerms) {
  EXPECT_THAT(GenerateUniqueTerms(0), IsEmpty());
  EXPECT_THAT(GenerateUniqueTerms(1), ElementsAre("a"));
  EXPECT_THAT(GenerateUniqueTerms(4), ElementsAre("a", "b", "c", "d"));
  EXPECT_THAT(GenerateUniqueTerms(29),
              ElementsAre("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k",
                          "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v",
                          "w", "x", "y", "z", "aa", "ba", "ca"));
  EXPECT_THAT(GenerateUniqueTerms(56),
              ElementsAre("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k",
                          "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v",
                          "w", "x", "y", "z", "aa", "ba", "ca", "da", "ea",
                          "fa", "ga", "ha", "ia", "ja", "ka", "la", "ma", "na",
                          "oa", "pa", "qa", "ra", "sa", "ta", "ua", "va", "wa",
                          "xa", "ya", "za", "ab", "bb", "cb", "db"));
  EXPECT_THAT(GenerateUniqueTerms(56).at(54), Eq("cb"));
  EXPECT_THAT(GenerateUniqueTerms(26 * 26 * 26).at(26), Eq("aa"));
  EXPECT_THAT(GenerateUniqueTerms(26 * 26 * 26).at(26 * 27), Eq("aaa"));
  EXPECT_THAT(GenerateUniqueTerms(26 * 26 * 26).at(26 * 27 - 6), Eq("uz"));
  EXPECT_THAT(GenerateUniqueTerms(26 * 26 * 26).at(26 * 27 + 5), Eq("faa"));
}

}  // namespace

}  // namespace lib
}  // namespace icing
