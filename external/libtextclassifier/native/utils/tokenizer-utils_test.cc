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

#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {
namespace {

TEST(TokenizerUtilTest, TokenizeOnSpace) {
  std::vector<Token> tokens =
      TokenizeOnSpace("Where is Jörg Borg located? Maybe in Zürich ...");

  EXPECT_EQ(tokens.size(), 9);

  EXPECT_EQ(tokens[0].value, "Where");
  EXPECT_EQ(tokens[0].start, 0);
  EXPECT_EQ(tokens[0].end, 5);

  EXPECT_EQ(tokens[1].value, "is");
  EXPECT_EQ(tokens[1].start, 6);
  EXPECT_EQ(tokens[1].end, 8);

  EXPECT_EQ(tokens[2].value, "Jörg");
  EXPECT_EQ(tokens[2].start, 9);
  EXPECT_EQ(tokens[2].end, 13);

  EXPECT_EQ(tokens[3].value, "Borg");
  EXPECT_EQ(tokens[3].start, 14);
  EXPECT_EQ(tokens[3].end, 18);

  EXPECT_EQ(tokens[4].value, "located?");
  EXPECT_EQ(tokens[4].start, 19);
  EXPECT_EQ(tokens[4].end, 27);

  EXPECT_EQ(tokens[5].value, "Maybe");
  EXPECT_EQ(tokens[5].start, 28);
  EXPECT_EQ(tokens[5].end, 33);

  EXPECT_EQ(tokens[6].value, "in");
  EXPECT_EQ(tokens[6].start, 34);
  EXPECT_EQ(tokens[6].end, 36);

  EXPECT_EQ(tokens[7].value, "Zürich");
  EXPECT_EQ(tokens[7].start, 37);
  EXPECT_EQ(tokens[7].end, 43);

  EXPECT_EQ(tokens[8].value, "...");
  EXPECT_EQ(tokens[8].start, 44);
  EXPECT_EQ(tokens[8].end, 47);
}

TEST(TokenizerUtilTest, TokenizeOnDelimiters) {
  std::vector<Token> tokens = TokenizeOnDelimiters(
      "This   might be čomplíčateď?!: Oder?", {' ', '?', '!'});

  EXPECT_EQ(tokens.size(), 6);

  EXPECT_EQ(tokens[0].value, "This");
  EXPECT_EQ(tokens[0].start, 0);
  EXPECT_EQ(tokens[0].end, 4);

  EXPECT_EQ(tokens[1].value, "might");
  EXPECT_EQ(tokens[1].start, 7);
  EXPECT_EQ(tokens[1].end, 12);

  EXPECT_EQ(tokens[2].value, "be");
  EXPECT_EQ(tokens[2].start, 13);
  EXPECT_EQ(tokens[2].end, 15);

  EXPECT_EQ(tokens[3].value, "čomplíčateď");
  EXPECT_EQ(tokens[3].start, 16);
  EXPECT_EQ(tokens[3].end, 27);

  EXPECT_EQ(tokens[4].value, ":");
  EXPECT_EQ(tokens[4].start, 29);
  EXPECT_EQ(tokens[4].end, 30);

  EXPECT_EQ(tokens[5].value, "Oder");
  EXPECT_EQ(tokens[5].start, 31);
  EXPECT_EQ(tokens[5].end, 35);
}

TEST(TokenizerUtilTest, TokenizeOnDelimitersKeepNoSpace) {
  std::vector<Token> tokens = TokenizeOnDelimiters(
      "This   might be čomplíčateď?!: Oder?", {' ', '?', '!'},
      /* create_tokens_for_non_space_delimiters =*/true);

  EXPECT_EQ(tokens.size(), 9);

  EXPECT_EQ(tokens[0].value, "This");
  EXPECT_EQ(tokens[0].start, 0);
  EXPECT_EQ(tokens[0].end, 4);

  EXPECT_EQ(tokens[1].value, "might");
  EXPECT_EQ(tokens[1].start, 7);
  EXPECT_EQ(tokens[1].end, 12);

  EXPECT_EQ(tokens[2].value, "be");
  EXPECT_EQ(tokens[2].start, 13);
  EXPECT_EQ(tokens[2].end, 15);

  EXPECT_EQ(tokens[3].value, "čomplíčateď");
  EXPECT_EQ(tokens[3].start, 16);
  EXPECT_EQ(tokens[3].end, 27);

  EXPECT_EQ(tokens[4].value, "?");
  EXPECT_EQ(tokens[4].start, 27);
  EXPECT_EQ(tokens[4].end, 28);

  EXPECT_EQ(tokens[5].value, "!");
  EXPECT_EQ(tokens[5].start, 28);
  EXPECT_EQ(tokens[5].end, 29);

  EXPECT_EQ(tokens[6].value, ":");
  EXPECT_EQ(tokens[6].start, 29);
  EXPECT_EQ(tokens[6].end, 30);

  EXPECT_EQ(tokens[7].value, "Oder");
  EXPECT_EQ(tokens[7].start, 31);
  EXPECT_EQ(tokens[7].end, 35);

  EXPECT_EQ(tokens[8].value, "?");
  EXPECT_EQ(tokens[8].start, 35);
  EXPECT_EQ(tokens[8].end, 36);
}

TEST(TokenizerUtilTest, SimpleEnglishWithPunctuation) {
  absl::string_view input = "I am fine, thanks!";

  std::vector<Token> tokens =
      TokenizeOnWhiteSpacePunctuationAndChineseLetter(input);

  EXPECT_THAT(tokens, testing::ElementsAreArray(
                          {Token{"I", 0, 1}, Token{"am", 2, 4},
                           Token{"fine", 5, 9}, Token{",", 9, 10},
                           Token{"thanks", 11, 17}, Token{"!", 17, 18}}));
}

TEST(TokenizerUtilTest, InputDoesNotEndWithDelimiter) {
  absl::string_view input = "Good! Cool";

  std::vector<Token> tokens =
      TokenizeOnWhiteSpacePunctuationAndChineseLetter(input);

  EXPECT_THAT(tokens,
              testing::ElementsAreArray({Token{"Good", 0, 4}, Token{"!", 4, 5},
                                         Token{"Cool", 6, 10}}));
}

TEST(TokenizerUtilTest, OnlySpace) {
  absl::string_view input = "  \t";

  std::vector<Token> tokens =
      TokenizeOnWhiteSpacePunctuationAndChineseLetter(input);

  ASSERT_TRUE(tokens.empty());
}

TEST(TokenizerUtilTest, Punctuation) {
  absl::string_view input = "!-/:-@[-`{-~";

  std::vector<Token> tokens =
      TokenizeOnWhiteSpacePunctuationAndChineseLetter(input);

  EXPECT_THAT(tokens,
              testing::ElementsAreArray(
                  {Token{"!", 0, 1}, Token{"-", 1, 2}, Token{"/", 2, 3},
                   Token{":", 3, 4}, Token{"-", 4, 5}, Token{"@", 5, 6},
                   Token{"[", 6, 7}, Token{"-", 7, 8}, Token{"`", 8, 9},
                   Token{"{", 9, 10}, Token{"-", 10, 11}, Token{"~", 11, 12}}));
}

TEST(TokenizerUtilTest, ChineseCharacters) {
  absl::string_view input = "你好嗎三個字";

  std::vector<Token> tokens =
      TokenizeOnWhiteSpacePunctuationAndChineseLetter(input);

  EXPECT_THAT(tokens,
              testing::ElementsAreArray(
                  {Token{"你", 0, 1}, Token{"好", 1, 2}, Token{"嗎", 2, 3},
                   Token{"三", 3, 4}, Token{"個", 4, 5}, Token{"字", 5, 6}}));
}
}  // namespace
}  // namespace libtextclassifier3
