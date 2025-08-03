// Copyright (C) 2021 Google LLC
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

#include <string_view>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/portable/platform.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/jni-test-helpers.h"
#include "icing/testing/test-data.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/tokenization/token.h"
#include "icing/tokenization/tokenizer-factory.h"
#include "icing/util/character-iterator.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {
namespace {
using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;

class VerbatimTokenizerTest : public ::testing::Test {
 protected:
  void SetUp() override {
    if (!IsCfStringTokenization() && !IsReverseJniTokenization()) {
      ICING_ASSERT_OK(
          // File generated via icu_data_file rule in //icing/BUILD.
          icu_data_file_helper::SetUpICUDataFile(
              GetTestFilePath("icing/icu.dat")));
    }

    jni_cache_ = GetTestJniCache();
    language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                         jni_cache_.get());
    ICING_ASSERT_OK_AND_ASSIGN(
        language_segmenter_,
        language_segmenter_factory::Create(std::move(options)));
  }

  std::unique_ptr<const JniCache> jni_cache_;
  std::unique_ptr<LanguageSegmenter> language_segmenter_;
};

TEST_F(VerbatimTokenizerTest, Empty) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> verbatim_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::VERBATIM,
                                 language_segmenter_.get()));

  EXPECT_THAT(verbatim_tokenizer->TokenizeAll(""), IsOkAndHolds(IsEmpty()));
}

TEST_F(VerbatimTokenizerTest, Simple) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> verbatim_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::VERBATIM,
                                 language_segmenter_.get()));

  EXPECT_THAT(
      verbatim_tokenizer->TokenizeAll("foo bar"),
      IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::VERBATIM, "foo bar"))));
}

TEST_F(VerbatimTokenizerTest, Punctuation) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> verbatim_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::VERBATIM,
                                 language_segmenter_.get()));

  EXPECT_THAT(verbatim_tokenizer->TokenizeAll("Hello, world!"),
              IsOkAndHolds(ElementsAre(
                  EqualsToken(Token::Type::VERBATIM, "Hello, world!"))));
}

TEST_F(VerbatimTokenizerTest, NoTokensBeforeAdvancing) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> verbatim_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::VERBATIM,
                                 language_segmenter_.get()));

  constexpr std::string_view kText = "Hello, world!";
  auto token_iterator = verbatim_tokenizer->Tokenize(kText).ValueOrDie();

  // We should get no tokens if we get the token before advancing.
  EXPECT_THAT(token_iterator->GetTokens(), IsEmpty());
}

TEST_F(VerbatimTokenizerTest, ResetToTokenEndingBefore) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> verbatim_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::VERBATIM,
                                 language_segmenter_.get()));

  constexpr std::string_view kText = "Hello, world!";
  auto token_iterator = verbatim_tokenizer->Tokenize(kText).ValueOrDie();

  // Reset to beginning of verbatim of token. We provide an offset of 13 as it
  // is larger than the final index (12) of the verbatim token.
  EXPECT_TRUE(token_iterator->ResetToTokenEndingBefore(13));
  EXPECT_THAT(token_iterator->GetTokens(),
              ElementsAre(EqualsToken(Token::Type::VERBATIM, "Hello, world!")));

  // Ensure our cached character iterator propertly maintains the end of the
  // verbatim token.
  EXPECT_TRUE(token_iterator->ResetToTokenEndingBefore(13));
  EXPECT_THAT(token_iterator->GetTokens(),
              ElementsAre(EqualsToken(Token::Type::VERBATIM, "Hello, world!")));

  // We should not be able to reset with an offset before or within
  // the verbatim token's utf-32 length.
  EXPECT_FALSE(token_iterator->ResetToTokenEndingBefore(0));
  EXPECT_FALSE(token_iterator->ResetToTokenEndingBefore(12));
}

TEST_F(VerbatimTokenizerTest, ResetToTokenStartingAfter) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> verbatim_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::VERBATIM,
                                 language_segmenter_.get()));

  constexpr std::string_view kText = "Hello, world!";
  auto token_iterator = verbatim_tokenizer->Tokenize(kText).ValueOrDie();

  // Get token without resetting
  EXPECT_TRUE(token_iterator->Advance());
  EXPECT_THAT(token_iterator->GetTokens(),
              ElementsAre(EqualsToken(Token::Type::VERBATIM, "Hello, world!")));

  // We expect a sole verbatim token, so it's not possible to reset after the
  // start of the token.
  EXPECT_FALSE(token_iterator->ResetToTokenStartingAfter(1));

  // We expect to be reset to the sole verbatim token when the offset is
  // negative.
  EXPECT_TRUE(token_iterator->ResetToTokenStartingAfter(-1));
  EXPECT_THAT(token_iterator->GetTokens(),
              ElementsAre(EqualsToken(Token::Type::VERBATIM, "Hello, world!")));
}

TEST_F(VerbatimTokenizerTest, ResetToStart) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> verbatim_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::VERBATIM,
                                 language_segmenter_.get()));

  constexpr std::string_view kText = "Hello, world!";
  auto token_iterator = verbatim_tokenizer->Tokenize(kText).ValueOrDie();

  // Get token without resetting
  EXPECT_TRUE(token_iterator->Advance());
  EXPECT_THAT(token_iterator->GetTokens(),
              ElementsAre(EqualsToken(Token::Type::VERBATIM, "Hello, world!")));

  // Retrieve token again after resetting to start
  EXPECT_TRUE(token_iterator->ResetToStart());
  EXPECT_THAT(token_iterator->GetTokens(),
              ElementsAre(EqualsToken(Token::Type::VERBATIM, "Hello, world!")));
}

TEST_F(VerbatimTokenizerTest, CalculateTokenStart) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> verbatim_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::VERBATIM,
                                 language_segmenter_.get()));

  constexpr std::string_view kText = "Hello, world!";
  auto token_iterator = verbatim_tokenizer->Tokenize(kText).ValueOrDie();

  ICING_ASSERT_OK_AND_ASSIGN(CharacterIterator start_character_iterator,
                             token_iterator->CalculateTokenStart());

  // We should retrieve the character 'H', the first character of the token.
  EXPECT_THAT(start_character_iterator.GetCurrentChar(), Eq('H'));
}

TEST_F(VerbatimTokenizerTest, CalculateTokenEnd) {
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> verbatim_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::VERBATIM,
                                 language_segmenter_.get()));

  constexpr std::string_view kText = "Hello, world!";
  auto token_iterator = verbatim_tokenizer->Tokenize(kText).ValueOrDie();

  ICING_ASSERT_OK_AND_ASSIGN(CharacterIterator end_character_iterator,
                             token_iterator->CalculateTokenEndExclusive());

  // We should retrieve the the null character, as the returned character
  // iterator will be set one past the end of the token.
  EXPECT_THAT(end_character_iterator.GetCurrentChar(), Eq('\0'));
}

}  // namespace
}  // namespace lib
}  // namespace icing
