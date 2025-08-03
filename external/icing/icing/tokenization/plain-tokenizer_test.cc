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

#include <string_view>

#include "gmock/gmock.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/portable/platform.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/icu-i18n-test-utils.h"
#include "icing/testing/jni-test-helpers.h"
#include "icing/testing/test-data.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/tokenization/tokenizer-factory.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {
namespace {
using ::testing::ElementsAre;
using ::testing::IsEmpty;

class PlainTokenizerTest : public ::testing::Test {
 protected:
  void SetUp() override {
    if (!IsCfStringTokenization() && !IsReverseJniTokenization()) {
      ICING_ASSERT_OK(
          // File generated via icu_data_file rule in //icing/BUILD.
          icu_data_file_helper::SetUpICUDataFile(
              GetTestFilePath("icing/icu.dat")));
    }
  }

  std::unique_ptr<const JniCache> jni_cache_ = GetTestJniCache();
};

TEST_F(PlainTokenizerTest, CreationWithNullPointerShouldFail) {
  EXPECT_THAT(tokenizer_factory::CreateIndexingTokenizer(
                  StringIndexingConfig::TokenizerType::PLAIN,
                  /*lang_segmenter=*/nullptr),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
}

TEST_F(PlainTokenizerTest, NoTokensBeforeAdvancing) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> plain_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::PLAIN,
                                 language_segmenter.get()));

  constexpr std::string_view kText = "Hello, world!";
  ICING_ASSERT_OK_AND_ASSIGN(auto token_iterator,
                             plain_tokenizer->Tokenize(kText));

  // We should get no tokens if we get the token before advancing.
  EXPECT_THAT(token_iterator->GetTokens(), IsEmpty());
}

TEST_F(PlainTokenizerTest, LastTokenAfterFullyAdvanced) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> plain_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::PLAIN,
                                 language_segmenter.get()));

  constexpr std::string_view kText = "Hello, world!";
  ICING_ASSERT_OK_AND_ASSIGN(auto token_iterator,
                             plain_tokenizer->Tokenize(kText));

  while (token_iterator->Advance()) {}

  // After advance returns false, GetTokens will stay on the last token.
  EXPECT_THAT(token_iterator->GetTokens(),
              ElementsAre(EqualsToken(Token::Type::REGULAR, "!")));
}

TEST_F(PlainTokenizerTest, Simple) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> plain_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::PLAIN,
                                 language_segmenter.get()));

  EXPECT_THAT(plain_tokenizer->TokenizeAll(""), IsOkAndHolds(IsEmpty()));

  EXPECT_THAT(
      plain_tokenizer->TokenizeAll("Hello World"),
      IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "Hello"),
                               EqualsToken(Token::Type::REGULAR, "World"))));

  EXPECT_THAT(
      plain_tokenizer->TokenizeAll(
          "Lorem ipsum dolor sit amet, consectetur adipiscing elit. "
          "Duis efficitur iaculis auctor."),
      IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "Lorem"),
                               EqualsToken(Token::Type::REGULAR, "ipsum"),
                               EqualsToken(Token::Type::REGULAR, "dolor"),
                               EqualsToken(Token::Type::REGULAR, "sit"),
                               EqualsToken(Token::Type::REGULAR, "amet"),
                               EqualsToken(Token::Type::REGULAR, "consectetur"),
                               EqualsToken(Token::Type::REGULAR, "adipiscing"),
                               EqualsToken(Token::Type::REGULAR, "elit"),
                               EqualsToken(Token::Type::REGULAR, "Duis"),
                               EqualsToken(Token::Type::REGULAR, "efficitur"),
                               EqualsToken(Token::Type::REGULAR, "iaculis"),
                               EqualsToken(Token::Type::REGULAR, "auctor"))));
}

TEST_F(PlainTokenizerTest, Whitespace) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> plain_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::PLAIN,
                                 language_segmenter.get()));

  // There're many unicode characters that are whitespaces, here we choose tabs
  // to represent others.

  // 0x0009 is horizontal tab, considered as a whitespace
  std::string text_with_horizontal_tab =
      absl_ports::StrCat("Hello", UCharToString(0x0009), "World");
  EXPECT_THAT(
      plain_tokenizer->TokenizeAll(text_with_horizontal_tab),
      IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "Hello"),
                               EqualsToken(Token::Type::REGULAR, "World"))));

  // 0x000B is vertical tab, considered as a whitespace
  std::string text_with_vertical_tab =
      absl_ports::StrCat("Hello", UCharToString(0x000B), "World");
  EXPECT_THAT(
      plain_tokenizer->TokenizeAll(text_with_vertical_tab),
      IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "Hello"),
                               EqualsToken(Token::Type::REGULAR, "World"))));
}

TEST_F(PlainTokenizerTest, Punctuation) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> plain_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::PLAIN,
                                 language_segmenter.get()));

  // Half-width punctuation marks are filtered out.
  EXPECT_THAT(
      plain_tokenizer->TokenizeAll(
          "Hello, World! Hello: World. \"Hello\" World?"),
      IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "Hello"),
                               EqualsToken(Token::Type::REGULAR, "World"),
                               EqualsToken(Token::Type::REGULAR, "Hello"),
                               EqualsToken(Token::Type::REGULAR, "World"),
                               EqualsToken(Token::Type::REGULAR, "Hello"),
                               EqualsToken(Token::Type::REGULAR, "World"))));

  // Full-width punctuation marks are filtered out.
  std::vector<std::string_view> exp_tokens;
  if (IsCfStringTokenization()) {
    EXPECT_THAT(
        plain_tokenizer->TokenizeAll("你好，世界！你好：世界。“你好”世界？"),
        IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "你"),
                                 EqualsToken(Token::Type::REGULAR, "好"),
                                 EqualsToken(Token::Type::REGULAR, "世界"),
                                 EqualsToken(Token::Type::REGULAR, "你"),
                                 EqualsToken(Token::Type::REGULAR, "好"),
                                 EqualsToken(Token::Type::REGULAR, "世界"),
                                 EqualsToken(Token::Type::REGULAR, "你"),
                                 EqualsToken(Token::Type::REGULAR, "好"),
                                 EqualsToken(Token::Type::REGULAR, "世界"))));
  } else {
    EXPECT_THAT(
        plain_tokenizer->TokenizeAll("你好，世界！你好：世界。“你好”世界？"),
        IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "你好"),
                                 EqualsToken(Token::Type::REGULAR, "世界"),
                                 EqualsToken(Token::Type::REGULAR, "你好"),
                                 EqualsToken(Token::Type::REGULAR, "世界"),
                                 EqualsToken(Token::Type::REGULAR, "你好"),
                                 EqualsToken(Token::Type::REGULAR, "世界"))));
  }
}

TEST_F(PlainTokenizerTest, SpecialCharacters) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> plain_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::PLAIN,
                                 language_segmenter.get()));

  // Right now we don't have special logic for these characters, just output
  // them as tokens.

  EXPECT_THAT(
      plain_tokenizer->TokenizeAll("1+1"),
      IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "1"),
                               EqualsToken(Token::Type::REGULAR, "+"),
                               EqualsToken(Token::Type::REGULAR, "1"))));

  EXPECT_THAT(
      plain_tokenizer->TokenizeAll("$50"),
      IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "$"),
                               EqualsToken(Token::Type::REGULAR, "50"))));
}

TEST_F(PlainTokenizerTest, CJKT) {
  // In plain tokenizer, CJKT characters are handled the same way as non-CJKT
  // characters, just add these tests as sanity checks.
  // Chinese
  language_segmenter_factory::SegmenterOptions options(ULOC_SIMPLIFIED_CHINESE,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> plain_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::PLAIN,
                                 language_segmenter.get()));
  EXPECT_THAT(
      plain_tokenizer->TokenizeAll("我每天走路去上班。"),
      IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "我"),
                               EqualsToken(Token::Type::REGULAR, "每天"),
                               EqualsToken(Token::Type::REGULAR, "走路"),
                               EqualsToken(Token::Type::REGULAR, "去"),
                               EqualsToken(Token::Type::REGULAR, "上班"))));
  // Japanese
  options = language_segmenter_factory::SegmenterOptions(ULOC_JAPANESE,
                                                         jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(plain_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::PLAIN,
                                 language_segmenter.get()));
  if (IsCfStringTokenization()) {
    EXPECT_THAT(
        plain_tokenizer->TokenizeAll("私は毎日仕事に歩いています。"),
        IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "私"),
                                 EqualsToken(Token::Type::REGULAR, "は"),
                                 EqualsToken(Token::Type::REGULAR, "毎日"),
                                 EqualsToken(Token::Type::REGULAR, "仕事"),
                                 EqualsToken(Token::Type::REGULAR, "に"),
                                 EqualsToken(Token::Type::REGULAR, "歩い"),
                                 EqualsToken(Token::Type::REGULAR, "て"),
                                 EqualsToken(Token::Type::REGULAR, "い"),
                                 EqualsToken(Token::Type::REGULAR, "ます"))));
  } else {
    EXPECT_THAT(
        plain_tokenizer->TokenizeAll("私は毎日仕事に歩いています。"),
        IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "私"),
                                 EqualsToken(Token::Type::REGULAR, "は"),
                                 EqualsToken(Token::Type::REGULAR, "毎日"),
                                 EqualsToken(Token::Type::REGULAR, "仕事"),
                                 EqualsToken(Token::Type::REGULAR, "に"),
                                 EqualsToken(Token::Type::REGULAR, "歩"),
                                 EqualsToken(Token::Type::REGULAR, "い"),
                                 EqualsToken(Token::Type::REGULAR, "てい"),
                                 EqualsToken(Token::Type::REGULAR, "ます"))));
  }

  // Khmer
  EXPECT_THAT(
      plain_tokenizer->TokenizeAll("ញុំដើរទៅធ្វើការរាល់ថ្ងៃ។"),
      IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "ញុំ"),
                               EqualsToken(Token::Type::REGULAR, "ដើរទៅ"),
                               EqualsToken(Token::Type::REGULAR, "ធ្វើការ"),
                               EqualsToken(Token::Type::REGULAR, "រាល់ថ្ងៃ"))));
  // Korean
  EXPECT_THAT(plain_tokenizer->TokenizeAll("나는 매일 출근합니다."),
              IsOkAndHolds(ElementsAre(
                  EqualsToken(Token::Type::REGULAR, "나는"),
                  EqualsToken(Token::Type::REGULAR, "매일"),
                  EqualsToken(Token::Type::REGULAR, "출근합니다"))));

  // Thai
  // DIFFERENCE!! Disagreement over how to segment "ทุกวัน" (iOS groups).
  // This difference persists even when locale is set to THAI
  if (IsCfStringTokenization()) {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::vector<Token> tokens,
        plain_tokenizer->TokenizeAll("ฉันเดินไปทำงานทุกวัน"));

    EXPECT_THAT(tokens, ElementsAre(EqualsToken(Token::Type::REGULAR, "ฉัน"),
                                    EqualsToken(Token::Type::REGULAR, "เดิน"),
                                    EqualsToken(Token::Type::REGULAR, "ไป"),
                                    EqualsToken(Token::Type::REGULAR, "ทำงาน"),
                                    EqualsToken(Token::Type::REGULAR, "ทุกวัน")));
  } else {
    EXPECT_THAT(
        plain_tokenizer->TokenizeAll("ฉันเดินไปทำงานทุกวัน"),
        IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "ฉัน"),
                                 EqualsToken(Token::Type::REGULAR, "เดิน"),
                                 EqualsToken(Token::Type::REGULAR, "ไป"),
                                 EqualsToken(Token::Type::REGULAR, "ทำงาน"),
                                 EqualsToken(Token::Type::REGULAR, "ทุก"),
                                 EqualsToken(Token::Type::REGULAR, "วัน"))));
  }
}

TEST_F(PlainTokenizerTest, ResetToTokenStartingAfterSimple) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> plain_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::PLAIN,
                                 language_segmenter.get()));

  constexpr std::string_view kText = "f b";
  auto iterator = plain_tokenizer->Tokenize(kText).ValueOrDie();

  EXPECT_TRUE(iterator->ResetToTokenStartingAfter(0));
  EXPECT_THAT(iterator->GetTokens(),
              ElementsAre(EqualsToken(Token::Type::REGULAR, "b")));

  EXPECT_FALSE(iterator->ResetToTokenStartingAfter(2));
}

TEST_F(PlainTokenizerTest, ResetToTokenEndingBeforeSimple) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> plain_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::PLAIN,
                                 language_segmenter.get()));

  constexpr std::string_view kText = "f b";
  auto iterator = plain_tokenizer->Tokenize(kText).ValueOrDie();

  EXPECT_TRUE(iterator->ResetToTokenEndingBefore(2));
  EXPECT_THAT(iterator->GetTokens(),
              ElementsAre(EqualsToken(Token::Type::REGULAR, "f")));

  EXPECT_FALSE(iterator->ResetToTokenEndingBefore(0));
}

TEST_F(PlainTokenizerTest, ResetToTokenStartingAfter) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> plain_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::PLAIN,
                                 language_segmenter.get()));

  constexpr std::string_view kText = " foo . bar baz.. bat ";
  EXPECT_THAT(
      plain_tokenizer->TokenizeAll(kText),
      IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "foo"),
                               EqualsToken(Token::Type::REGULAR, "bar"),
                               EqualsToken(Token::Type::REGULAR, "baz"),
                               EqualsToken(Token::Type::REGULAR, "bat"))));
  std::vector<std::string> expected_text = {
      "foo",  //  0: " foo . bar"
      "bar",  //  1: "foo . bar "
      "bar",  //  2: "oo . bar b"
      "bar",  //  3: "o . bar ba"
      "bar",  //  4: " . bar baz"
      "bar",  //  5: ". bar baz."
      "bar",  //  6: " bar baz.."
      "baz",  //  7: "bar baz.. b"
      "baz",  //  8: "ar baz.. ba"
      "baz",  //  9: "r baz.. bat"
      "baz",  // 10: " baz.. bat"
      "bat",  // 11: "baz.. bat"
      "bat",  // 12: "az.. bat"
      "bat",  // 13: "z.. bat"
      "bat",  // 14: ".. bat"
      "bat",  // 15: ". bat"
      "bat",  // 16: " bat"
  };

  auto iterator = plain_tokenizer->Tokenize(kText).ValueOrDie();
  EXPECT_TRUE(iterator->Advance());
  EXPECT_THAT(iterator->GetTokens(),
              ElementsAre(EqualsToken(Token::Type::REGULAR, "foo")));
  for (int i = 0; i < kText.length(); ++i) {
    if (i < expected_text.size()) {
      EXPECT_TRUE(iterator->ResetToTokenStartingAfter(i));
      EXPECT_THAT(
          iterator->GetTokens(),
          ElementsAre(EqualsToken(Token::Type::REGULAR, expected_text[i])));
    } else {
      EXPECT_FALSE(iterator->ResetToTokenStartingAfter(i));
    }
  }
}

TEST_F(PlainTokenizerTest, ResetToTokenEndingBefore) {
  language_segmenter_factory::SegmenterOptions options(ULOC_US,
                                                       jni_cache_.get());
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(std::move(options)));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Tokenizer> plain_tokenizer,
                             tokenizer_factory::CreateIndexingTokenizer(
                                 StringIndexingConfig::TokenizerType::PLAIN,
                                 language_segmenter.get()));

  constexpr std::string_view kText = " foo . bar baz.. bat ";
  EXPECT_THAT(
      plain_tokenizer->TokenizeAll(kText),
      IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::REGULAR, "foo"),
                               EqualsToken(Token::Type::REGULAR, "bar"),
                               EqualsToken(Token::Type::REGULAR, "baz"),
                               EqualsToken(Token::Type::REGULAR, "bat"))));
  std::vector<std::string> expected_text = {
      "bat",  // 20: "baz.. bat "
      "baz",  // 19: " baz.. bat"
      "baz",  // 18: "r baz.. ba"
      "baz",  // 17: "ar baz.. b"
      "baz",  // 16: "bar baz.. "
      "baz",  // 15: " bar baz.."
      "baz",  // 14: ". bar baz."
      "bar",  // 13: " . bar baz"
      "bar",  // 12: "o . bar ba"
      "bar",  // 11: "oo . bar b"
      "bar",  // 10: "foo . bar "
      "foo",  //  9: "foo . bar"
      "foo",  //  8: "foo . ba"
      "foo",  //  7: "foo . b"
      "foo",  //  6: "foo . "
      "foo",  //  5: "foo ."
      "foo",  //  4: "foo "
  };

  auto iterator = plain_tokenizer->Tokenize(kText).ValueOrDie();
  EXPECT_TRUE(iterator->Advance());
  EXPECT_THAT(iterator->GetTokens(),
              ElementsAre(EqualsToken(Token::Type::REGULAR, "foo")));
  for (int i = kText.length() - 1; i >= 0; --i) {
    int expected_index = kText.length() - 1 - i;
    if (expected_index < expected_text.size()) {
      EXPECT_TRUE(iterator->ResetToTokenEndingBefore(i));
      EXPECT_THAT(iterator->GetTokens(),
                  ElementsAre(EqualsToken(Token::Type::REGULAR,
                                          expected_text[expected_index])));
    } else {
      EXPECT_FALSE(iterator->ResetToTokenEndingBefore(i));
    }
  }
}

}  // namespace
}  // namespace lib
}  // namespace icing
