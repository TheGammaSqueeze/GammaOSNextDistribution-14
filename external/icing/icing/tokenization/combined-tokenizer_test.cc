// Copyright (C) 2022 Google LLC
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
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/portable/platform.h"
#include "icing/proto/schema.pb.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/jni-test-helpers.h"
#include "icing/testing/test-data.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/tokenization/tokenizer-factory.h"
#include "icing/tokenization/tokenizer.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;

// This test exists to ensure that the different tokenizers treat different
// segments of text in the same manner.
class CombinedTokenizerTest : public ::testing::Test {
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
        lang_segmenter_,
        language_segmenter_factory::Create(std::move(options)));
  }

  std::unique_ptr<const JniCache> jni_cache_;
  std::unique_ptr<LanguageSegmenter> lang_segmenter_;
};

std::vector<std::string> GetTokenTerms(const std::vector<Token>& tokens) {
  std::vector<std::string> terms;
  terms.reserve(tokens.size());
  for (const Token& token : tokens) {
    if (token.type == Token::Type::REGULAR) {
      terms.push_back(std::string(token.text));
    }
  }
  return terms;
}

}  // namespace

TEST_F(CombinedTokenizerTest, SpecialCharacters) {
  const std::string_view kText = "😊 Hello! Goodbye?";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Tokenizer> indexing_tokenizer,
      tokenizer_factory::CreateIndexingTokenizer(
          StringIndexingConfig::TokenizerType::PLAIN, lang_segmenter_.get()));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Tokenizer> query_tokenizer,
      CreateQueryTokenizer(tokenizer_factory::QueryTokenizerType::RAW_QUERY,
                           lang_segmenter_.get()));

  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Token> indexing_tokens,
                             indexing_tokenizer->TokenizeAll(kText));
  std::vector<std::string> indexing_terms = GetTokenTerms(indexing_tokens);
  EXPECT_THAT(indexing_terms, ElementsAre("😊", "Hello", "Goodbye"));

  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Token> query_tokens,
                             query_tokenizer->TokenizeAll(kText));
  std::vector<std::string> query_terms = GetTokenTerms(query_tokens);
  EXPECT_THAT(query_terms, ElementsAre("😊", "Hello", "Goodbye"));
}

TEST_F(CombinedTokenizerTest, Parentheses) {
  const std::string_view kText = "((paren1)(paren2) (last paren))";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Tokenizer> indexing_tokenizer,
      tokenizer_factory::CreateIndexingTokenizer(
          StringIndexingConfig::TokenizerType::PLAIN, lang_segmenter_.get()));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Tokenizer> query_tokenizer,
      CreateQueryTokenizer(tokenizer_factory::QueryTokenizerType::RAW_QUERY,
                           lang_segmenter_.get()));

  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Token> indexing_tokens,
                             indexing_tokenizer->TokenizeAll(kText));
  std::vector<std::string> indexing_terms = GetTokenTerms(indexing_tokens);
  EXPECT_THAT(indexing_terms, ElementsAre("paren1", "paren2", "last", "paren"));

  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Token> query_tokens,
                             query_tokenizer->TokenizeAll(kText));
  std::vector<std::string> query_terms = GetTokenTerms(query_tokens);
  EXPECT_THAT(query_terms, ElementsAre("paren1", "paren2", "last", "paren"));
}

TEST_F(CombinedTokenizerTest, Negation) {
  const std::string_view kText = "-foo -bar -baz";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Tokenizer> indexing_tokenizer,
      tokenizer_factory::CreateIndexingTokenizer(
          StringIndexingConfig::TokenizerType::PLAIN, lang_segmenter_.get()));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Tokenizer> query_tokenizer,
      CreateQueryTokenizer(tokenizer_factory::QueryTokenizerType::RAW_QUERY,
                           lang_segmenter_.get()));

  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Token> indexing_tokens,
                             indexing_tokenizer->TokenizeAll(kText));
  std::vector<std::string> indexing_terms = GetTokenTerms(indexing_tokens);
  EXPECT_THAT(indexing_terms, ElementsAre("foo", "bar", "baz"));

  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Token> query_tokens,
                             query_tokenizer->TokenizeAll(kText));
  std::vector<std::string> query_terms = GetTokenTerms(query_tokens);
  EXPECT_THAT(query_terms, ElementsAre("foo", "bar", "baz"));
}

// TODO(b/254874614): Handle colon word breaks in ICU 72+
TEST_F(CombinedTokenizerTest, Colons) {
  const std::string_view kText = ":foo: :bar baz:";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Tokenizer> indexing_tokenizer,
      tokenizer_factory::CreateIndexingTokenizer(
          StringIndexingConfig::TokenizerType::PLAIN, lang_segmenter_.get()));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Tokenizer> query_tokenizer,
      CreateQueryTokenizer(tokenizer_factory::QueryTokenizerType::RAW_QUERY,
                           lang_segmenter_.get()));

  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Token> indexing_tokens,
                             indexing_tokenizer->TokenizeAll(kText));
  std::vector<std::string> indexing_terms = GetTokenTerms(indexing_tokens);
  EXPECT_THAT(indexing_terms, ElementsAre("foo", "bar", "baz"));

  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Token> query_tokens,
                             query_tokenizer->TokenizeAll(kText));
  std::vector<std::string> query_terms = GetTokenTerms(query_tokens);
  EXPECT_THAT(query_terms, ElementsAre("foo", "bar", "baz"));
}

// TODO(b/254874614): Handle colon word breaks in ICU 72+
TEST_F(CombinedTokenizerTest, ColonsPropertyRestricts) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Tokenizer> indexing_tokenizer,
      tokenizer_factory::CreateIndexingTokenizer(
          StringIndexingConfig::TokenizerType::PLAIN, lang_segmenter_.get()));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Tokenizer> query_tokenizer,
      CreateQueryTokenizer(tokenizer_factory::QueryTokenizerType::RAW_QUERY,
                           lang_segmenter_.get()));

  if (GetIcuTokenizationVersion() >= 72) {
    // In ICU 72+ and above, ':' are no longer considered word connectors. The
    // query tokenizer should still consider them to be property restricts.
    constexpr std::string_view kText = "foo:bar";
    ICING_ASSERT_OK_AND_ASSIGN(std::vector<Token> indexing_tokens,
                               indexing_tokenizer->TokenizeAll(kText));
    std::vector<std::string> indexing_terms = GetTokenTerms(indexing_tokens);
    EXPECT_THAT(indexing_terms, ElementsAre("foo", "bar"));

    ICING_ASSERT_OK_AND_ASSIGN(std::vector<Token> query_tokens,
                               query_tokenizer->TokenizeAll(kText));
    std::vector<std::string> query_terms = GetTokenTerms(query_tokens);
    EXPECT_THAT(query_terms, ElementsAre("bar"));

    // This difference, however, should only apply to the first ':'. Both should
    // consider a second ':' to be a word break.
    constexpr std::string_view kText2 = "foo:bar:baz";
    ICING_ASSERT_OK_AND_ASSIGN(indexing_tokens,
                               indexing_tokenizer->TokenizeAll(kText2));
    indexing_terms = GetTokenTerms(indexing_tokens);
    EXPECT_THAT(indexing_terms, ElementsAre("foo", "bar", "baz"));

    ICING_ASSERT_OK_AND_ASSIGN(query_tokens,
                               query_tokenizer->TokenizeAll(kText2));
    query_terms = GetTokenTerms(query_tokens);
    EXPECT_THAT(query_terms, ElementsAre("bar", "baz"));
  } else {
    // This is a difference between the two tokenizers. "foo:bar" is a single
    // token to the plain tokenizer because ':' is a word connector. But
    // "foo:bar" is a property restrict to the query tokenizer - so "foo" is the
    // property and "bar" is the only text term.
    constexpr std::string_view kText = "foo:bar";
    ICING_ASSERT_OK_AND_ASSIGN(std::vector<Token> indexing_tokens,
                               indexing_tokenizer->TokenizeAll(kText));
    std::vector<std::string> indexing_terms = GetTokenTerms(indexing_tokens);
    EXPECT_THAT(indexing_terms, ElementsAre("foo:bar"));

    ICING_ASSERT_OK_AND_ASSIGN(std::vector<Token> query_tokens,
                               query_tokenizer->TokenizeAll(kText));
    std::vector<std::string> query_terms = GetTokenTerms(query_tokens);
    EXPECT_THAT(query_terms, ElementsAre("bar"));

    // This difference, however, should only apply to the first ':'. A
    // second ':' should be treated by both tokenizers as a word connector.
    constexpr std::string_view kText2 = "foo:bar:baz";
    ICING_ASSERT_OK_AND_ASSIGN(indexing_tokens,
                               indexing_tokenizer->TokenizeAll(kText2));
    indexing_terms = GetTokenTerms(indexing_tokens);
    EXPECT_THAT(indexing_terms, ElementsAre("foo:bar:baz"));

    ICING_ASSERT_OK_AND_ASSIGN(query_tokens,
                               query_tokenizer->TokenizeAll(kText2));
    query_terms = GetTokenTerms(query_tokens);
    EXPECT_THAT(query_terms, ElementsAre("bar:baz"));
  }
}

TEST_F(CombinedTokenizerTest, Punctuation) {
  const std::string_view kText = "Who? What!? Why & How.";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Tokenizer> indexing_tokenizer,
      tokenizer_factory::CreateIndexingTokenizer(
          StringIndexingConfig::TokenizerType::PLAIN, lang_segmenter_.get()));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Tokenizer> query_tokenizer,
      CreateQueryTokenizer(tokenizer_factory::QueryTokenizerType::RAW_QUERY,
                           lang_segmenter_.get()));

  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Token> indexing_tokens,
                             indexing_tokenizer->TokenizeAll(kText));
  std::vector<std::string> indexing_terms = GetTokenTerms(indexing_tokens);
  EXPECT_THAT(indexing_terms, ElementsAre("Who", "What", "Why", "How"));

  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Token> query_tokens,
                             query_tokenizer->TokenizeAll(kText));
  std::vector<std::string> query_terms = GetTokenTerms(query_tokens);
  EXPECT_THAT(query_terms, ElementsAre("Who", "What", "Why", "How"));
}

}  // namespace lib
}  // namespace icing
