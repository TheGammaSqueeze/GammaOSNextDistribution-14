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

#include <jni.h>

#include <memory>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "gmock/gmock.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/jni/jni-cache.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/icu-i18n-test-utils.h"
#include "icing/testing/jni-test-helpers.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/util/character-iterator.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {

namespace test_internal {

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;

namespace {

language_segmenter_factory::SegmenterOptions GetSegmenterOptions(
    const std::string& locale, const JniCache* jni_cache) {
  return language_segmenter_factory::SegmenterOptions(locale, jni_cache);
}

// Returns a vector containing all terms retrieved by Advancing on the iterator.
std::vector<std::string_view> GetAllTermsAdvance(
    LanguageSegmenter::Iterator* itr) {
  std::vector<std::string_view> terms;
  while (itr->Advance()) {
    terms.push_back(itr->GetTerm());
  }
  return terms;
}

// Returns a vector containing all terms retrieved by calling ResetAfter with
// the UTF-32 position of the current term start to simulate Advancing on the
// iterator.
std::vector<std::string_view> GetAllTermsResetAfterUtf32(
    LanguageSegmenter::Iterator* itr) {
  std::vector<std::string_view> terms;
  // Calling ResetToTermStartingAfterUtf32 with -1 should get the first term in
  // the sequence.
  bool is_ok = itr->ResetToTermStartingAfterUtf32(-1).ok();
  while (is_ok) {
    terms.push_back(itr->GetTerm());
    // Calling ResetToTermStartingAfterUtf32 with the current position should
    // get the very next term in the sequence.
    CharacterIterator char_itr = itr->CalculateTermStart().ValueOrDie();
    is_ok = itr->ResetToTermStartingAfterUtf32(char_itr.utf32_index()).ok();
  }
  return terms;
}

// Returns a vector containing all terms retrieved by alternating calls to
// Advance and calls to ResetAfter with the UTF-32 position of the current term
// start to simulate Advancing.
std::vector<std::string_view> GetAllTermsAdvanceAndResetAfterUtf32(
    LanguageSegmenter::Iterator* itr) {
  std::vector<std::string_view> terms;
  bool is_ok = itr->Advance();
  while (is_ok) {
    terms.push_back(itr->GetTerm());
    // Alternate between using Advance and ResetToTermAfter.
    if (terms.size() % 2 == 0) {
      is_ok = itr->Advance();
    } else {
      // Calling ResetToTermStartingAfterUtf32 with the current position should
      // get the very next term in the sequence.
      CharacterIterator char_itr = itr->CalculateTermStart().ValueOrDie();
      is_ok = itr->ResetToTermStartingAfterUtf32(char_itr.utf32_index()).ok();
    }
  }
  return terms;
}

// Returns a vector containing all terms retrieved by calling ResetBefore with
// the UTF-32 position of the current term start, starting at the end of the
// text. This vector should be in reverse order of GetAllTerms and missing the
// last term.
std::vector<std::string_view> GetAllTermsResetBeforeUtf32(
    LanguageSegmenter::Iterator* itr) {
  std::vector<std::string_view> terms;
  bool is_ok = itr->ResetToTermEndingBeforeUtf32(1000).ok();
  while (is_ok) {
    terms.push_back(itr->GetTerm());
    // Calling ResetToTermEndingBeforeUtf32 with the current position should get
    // the previous term in the sequence.
    CharacterIterator char_itr = itr->CalculateTermStart().ValueOrDie();
    is_ok = itr->ResetToTermEndingBeforeUtf32(char_itr.utf32_index()).ok();
  }
  return terms;
}

class ReverseJniLanguageSegmenterTest
    : public testing::TestWithParam<const char*> {
 protected:
  static std::string GetLocale() { return GetParam(); }

  std::unique_ptr<const JniCache> jni_cache_ = GetTestJniCache();
};

}  // namespace

TEST_P(ReverseJniLanguageSegmenterTest, EmptyText) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  EXPECT_THAT(language_segmenter->GetAllTerms(""), IsOkAndHolds(IsEmpty()));
}

TEST_P(ReverseJniLanguageSegmenterTest, SimpleText) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  EXPECT_THAT(language_segmenter->GetAllTerms("Hello World"),
              IsOkAndHolds(ElementsAre("Hello", " ", "World")));
}

TEST_P(ReverseJniLanguageSegmenterTest, ASCII_Punctuation) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // ASCII punctuation marks are kept
  EXPECT_THAT(
      language_segmenter->GetAllTerms("Hello, World!!!"),
      IsOkAndHolds(ElementsAre("Hello", ",", " ", "World", "!", "!", "!")));
  EXPECT_THAT(language_segmenter->GetAllTerms("Open-source project"),
              IsOkAndHolds(ElementsAre("Open", "-", "source", " ", "project")));
  EXPECT_THAT(language_segmenter->GetAllTerms("100%"),
              IsOkAndHolds(ElementsAre("100", "%")));
  EXPECT_THAT(language_segmenter->GetAllTerms("A&B"),
              IsOkAndHolds(ElementsAre("A", "&", "B")));
}

TEST_P(ReverseJniLanguageSegmenterTest, ASCII_SpecialCharacter) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // ASCII special characters are kept
  EXPECT_THAT(language_segmenter->GetAllTerms("Pay $1000"),
              IsOkAndHolds(ElementsAre("Pay", " ", "$", "1000")));
  EXPECT_THAT(language_segmenter->GetAllTerms("A+B"),
              IsOkAndHolds(ElementsAre("A", "+", "B")));
  // 0x0009 is the unicode for tab (within ASCII range).
  std::string text_with_tab = absl_ports::StrCat(
      "Hello", UCharToString(0x0009), UCharToString(0x0009), "World");
  EXPECT_THAT(language_segmenter->GetAllTerms(text_with_tab),
              IsOkAndHolds(ElementsAre("Hello", UCharToString(0x0009),
                                       UCharToString(0x0009), "World")));
}

TEST_P(ReverseJniLanguageSegmenterTest, Non_ASCII_Non_Alphabetic) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // Full-width (non-ASCII) punctuation marks and special characters are left
  // out.
  EXPECT_THAT(language_segmenter->GetAllTerms("。？·Hello！×"),
              IsOkAndHolds(ElementsAre("。", "？", "·", "Hello", "！", "×")));
}

TEST_P(ReverseJniLanguageSegmenterTest, Acronym) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  EXPECT_THAT(language_segmenter->GetAllTerms("U.S.𡔖 Bank"),
              IsOkAndHolds(ElementsAre("U.S", ".", "𡔖", " ", "Bank")));
  EXPECT_THAT(language_segmenter->GetAllTerms("I.B.M."),
              IsOkAndHolds(ElementsAre("I.B.M", ".")));
  EXPECT_THAT(language_segmenter->GetAllTerms("I,B,M"),
              IsOkAndHolds(ElementsAre("I", ",", "B", ",", "M")));
  EXPECT_THAT(language_segmenter->GetAllTerms("I B M"),
              IsOkAndHolds(ElementsAre("I", " ", "B", " ", "M")));
}

TEST_P(ReverseJniLanguageSegmenterTest, WordConnector) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // According to unicode word break rules
  // WB6(https://unicode.org/reports/tr29/#WB6),
  // WB7(https://unicode.org/reports/tr29/#WB7), and a few others, some
  // punctuation characters are used as word connecters. That is, words don't
  // break before and after them. Here we just test some that we care about.

  // Word connecters
  EXPECT_THAT(language_segmenter->GetAllTerms("com.google.android"),
              IsOkAndHolds(ElementsAre("com.google.android")));
  EXPECT_THAT(language_segmenter->GetAllTerms("com:google:android"),
              IsOkAndHolds(ElementsAre("com:google:android")));
  EXPECT_THAT(language_segmenter->GetAllTerms("com'google'android"),
              IsOkAndHolds(ElementsAre("com'google'android")));
  EXPECT_THAT(language_segmenter->GetAllTerms("com_google_android"),
              IsOkAndHolds(ElementsAre("com_google_android")));

  // Word connecters can be mixed
  EXPECT_THAT(language_segmenter->GetAllTerms("com.google.android:icing"),
              IsOkAndHolds(ElementsAre("com.google.android:icing")));

  // Connectors that don't have valid terms on both sides of it are not
  // considered connectors.
  EXPECT_THAT(language_segmenter->GetAllTerms(":bar:baz"),
              IsOkAndHolds(ElementsAre(":", "bar:baz")));

  EXPECT_THAT(language_segmenter->GetAllTerms("bar:baz:"),
              IsOkAndHolds(ElementsAre("bar:baz", ":")));

  // Connectors that don't have valid terms on both sides of it are not
  // considered connectors.
  EXPECT_THAT(language_segmenter->GetAllTerms(" :bar:baz"),
              IsOkAndHolds(ElementsAre(" ", ":", "bar:baz")));

  EXPECT_THAT(language_segmenter->GetAllTerms("bar:baz: "),
              IsOkAndHolds(ElementsAre("bar:baz", ":", " ")));

  // Connectors don't connect if one side is an invalid term (？)
  EXPECT_THAT(language_segmenter->GetAllTerms("bar:baz:？"),
              IsOkAndHolds(ElementsAre("bar:baz", ":", "？")));
  EXPECT_THAT(language_segmenter->GetAllTerms("？:bar:baz"),
              IsOkAndHolds(ElementsAre("？", ":", "bar:baz")));
  EXPECT_THAT(language_segmenter->GetAllTerms("3:14"),
              IsOkAndHolds(ElementsAre("3", ":", "14")));
  EXPECT_THAT(language_segmenter->GetAllTerms("私:は"),
              IsOkAndHolds(ElementsAre("私", ":", "は")));
  EXPECT_THAT(language_segmenter->GetAllTerms("我:每"),
              IsOkAndHolds(ElementsAre("我", ":", "每")));
  EXPECT_THAT(language_segmenter->GetAllTerms("เดิน:ไป"),
              IsOkAndHolds(ElementsAre("เดิน:ไป")));

  // Any heading and trailing characters are not connecters
  EXPECT_THAT(language_segmenter->GetAllTerms(".com.google.android."),
              IsOkAndHolds(ElementsAre(".", "com.google.android", ".")));

  // Not word connecters
  EXPECT_THAT(language_segmenter->GetAllTerms("com,google,android"),
              IsOkAndHolds(ElementsAre("com", ",", "google", ",", "android")));
  EXPECT_THAT(language_segmenter->GetAllTerms("com-google-android"),
              IsOkAndHolds(ElementsAre("com", "-", "google", "-", "android")));
  EXPECT_THAT(language_segmenter->GetAllTerms("com+google+android"),
              IsOkAndHolds(ElementsAre("com", "+", "google", "+", "android")));
  EXPECT_THAT(language_segmenter->GetAllTerms("com*google*android"),
              IsOkAndHolds(ElementsAre("com", "*", "google", "*", "android")));
  EXPECT_THAT(language_segmenter->GetAllTerms("com@google@android"),
              IsOkAndHolds(ElementsAre("com", "@", "google", "@", "android")));
  EXPECT_THAT(language_segmenter->GetAllTerms("com^google^android"),
              IsOkAndHolds(ElementsAre("com", "^", "google", "^", "android")));
  EXPECT_THAT(language_segmenter->GetAllTerms("com&google&android"),
              IsOkAndHolds(ElementsAre("com", "&", "google", "&", "android")));
  EXPECT_THAT(language_segmenter->GetAllTerms("com|google|android"),
              IsOkAndHolds(ElementsAre("com", "|", "google", "|", "android")));
  EXPECT_THAT(language_segmenter->GetAllTerms("com/google/android"),
              IsOkAndHolds(ElementsAre("com", "/", "google", "/", "android")));
  EXPECT_THAT(language_segmenter->GetAllTerms("com;google;android"),
              IsOkAndHolds(ElementsAre("com", ";", "google", ";", "android")));
  EXPECT_THAT(
      language_segmenter->GetAllTerms("com\"google\"android"),
      IsOkAndHolds(ElementsAre("com", "\"", "google", "\"", "android")));
}

TEST_P(ReverseJniLanguageSegmenterTest, Apostrophes) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  EXPECT_THAT(language_segmenter->GetAllTerms("It's ok."),
              IsOkAndHolds(ElementsAre("It's", " ", "ok", ".")));
  EXPECT_THAT(language_segmenter->GetAllTerms("He'll be back."),
              IsOkAndHolds(ElementsAre("He'll", " ", "be", " ", "back", ".")));
  EXPECT_THAT(language_segmenter->GetAllTerms("'Hello 'World."),
              IsOkAndHolds(ElementsAre("'", "Hello", " ", "'", "World", ".")));
  EXPECT_THAT(language_segmenter->GetAllTerms("The dogs' bone"),
              IsOkAndHolds(ElementsAre("The", " ", "dogs", "'", " ", "bone")));
  // 0x2019 is the single right quote, should be treated the same as "'"
  std::string token_with_quote =
      absl_ports::StrCat("He", UCharToString(0x2019), "ll");
  std::string text_with_quote =
      absl_ports::StrCat(token_with_quote, " be back.");
  EXPECT_THAT(
      language_segmenter->GetAllTerms(text_with_quote),
      IsOkAndHolds(ElementsAre(token_with_quote, " ", "be", " ", "back", ".")));
}

TEST_P(ReverseJniLanguageSegmenterTest, Parentheses) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));

  EXPECT_THAT(language_segmenter->GetAllTerms("(Hello)"),
              IsOkAndHolds(ElementsAre("(", "Hello", ")")));

  EXPECT_THAT(language_segmenter->GetAllTerms(")Hello("),
              IsOkAndHolds(ElementsAre(")", "Hello", "(")));
}

TEST_P(ReverseJniLanguageSegmenterTest, Quotes) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));

  EXPECT_THAT(language_segmenter->GetAllTerms("\"Hello\""),
              IsOkAndHolds(ElementsAre("\"", "Hello", "\"")));

  EXPECT_THAT(language_segmenter->GetAllTerms("'Hello'"),
              IsOkAndHolds(ElementsAre("'", "Hello", "'")));
}

TEST_P(ReverseJniLanguageSegmenterTest, Alphanumeric) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));

  // Alphanumeric terms are allowed
  EXPECT_THAT(language_segmenter->GetAllTerms("Se7en A4 3a"),
              IsOkAndHolds(ElementsAre("Se7en", " ", "A4", " ", "3a")));
}

TEST_P(ReverseJniLanguageSegmenterTest, Number) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));

  // Alphanumeric terms are allowed
  EXPECT_THAT(
      language_segmenter->GetAllTerms("3.141592653589793238462643383279"),
      IsOkAndHolds(ElementsAre("3.141592653589793238462643383279")));

  EXPECT_THAT(language_segmenter->GetAllTerms("3,456.789"),
              IsOkAndHolds(ElementsAre("3,456.789")));

  EXPECT_THAT(language_segmenter->GetAllTerms("-123"),
              IsOkAndHolds(ElementsAre("-", "123")));
}

TEST_P(ReverseJniLanguageSegmenterTest, FullWidthNumbers) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));

  EXPECT_THAT(language_segmenter->GetAllTerms("０１２３４５６７８９"),
              IsOkAndHolds(ElementsAre("０", "１", "２", "３", "４", "５", "６",
                                       "７", "８", "９")));
}

TEST_P(ReverseJniLanguageSegmenterTest, ContinuousWhitespaces) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // Multiple continuous whitespaces are treated as one.
  const int kNumSeparators = 256;
  std::string text_with_spaces =
      absl_ports::StrCat("Hello", std::string(kNumSeparators, ' '), "World");
  EXPECT_THAT(language_segmenter->GetAllTerms(text_with_spaces),
              IsOkAndHolds(ElementsAre("Hello", " ", "World")));

  // Multiple continuous whitespaces are treated as one. Whitespace at the
  // beginning of the text doesn't affect the results of GetTerm() after the
  // iterator is done.
  text_with_spaces = absl_ports::StrCat(std::string(kNumSeparators, ' '),
                                        "Hello", " ", "World");
  ICING_ASSERT_OK_AND_ASSIGN(auto itr,
                             language_segmenter->Segment(text_with_spaces));
  std::vector<std::string_view> terms;
  while (itr->Advance()) {
    terms.push_back(itr->GetTerm());
  }
  EXPECT_THAT(terms, ElementsAre(" ", "Hello", " ", "World"));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());
}

TEST_P(ReverseJniLanguageSegmenterTest, CJKT) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // CJKT (Chinese, Japanese, Khmer, Thai) are the 4 main languages that don't
  // have whitespaces as word delimiter.

  // Chinese
  EXPECT_THAT(
      language_segmenter->GetAllTerms("我每天走路去上班。"),
      IsOkAndHolds(ElementsAre("我", "每天", "走路", "去", "上班", "。")));
  // Japanese
  EXPECT_THAT(language_segmenter->GetAllTerms("私は毎日仕事に歩いています。"),
              IsOkAndHolds(ElementsAre("私", "は", "毎日", "仕事", "に", "歩",
                                       "い", "てい", "ます", "。")));
  // Khmer
  EXPECT_THAT(language_segmenter->GetAllTerms("ញុំដើរទៅធ្វើការរាល់ថ្ងៃ។"),
              IsOkAndHolds(ElementsAre("ញុំ", "ដើរទៅ", "ធ្វើការ", "រាល់ថ្ងៃ", "។")));
  // Thai
  EXPECT_THAT(
      language_segmenter->GetAllTerms("ฉันเดินไปทำงานทุกวัน"),
      IsOkAndHolds(ElementsAre("ฉัน", "เดิน", "ไป", "ทำงาน", "ทุก", "วัน")));
}

TEST_P(ReverseJniLanguageSegmenterTest, LatinLettersWithAccents) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  EXPECT_THAT(language_segmenter->GetAllTerms("āăąḃḅḇčćç"),
              IsOkAndHolds(ElementsAre("āăąḃḅḇčćç")));
}

TEST_P(ReverseJniLanguageSegmenterTest, WhitespaceSplitLanguages) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // Turkish
  EXPECT_THAT(language_segmenter->GetAllTerms("merhaba dünya"),
              IsOkAndHolds(ElementsAre("merhaba", " ", "dünya")));
  // Korean
  EXPECT_THAT(
      language_segmenter->GetAllTerms("나는 매일 출근합니다."),
      IsOkAndHolds(ElementsAre("나는", " ", "매일", " ", "출근합니다", ".")));
}

TEST_P(ReverseJniLanguageSegmenterTest, MixedLanguages) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  EXPECT_THAT(language_segmenter->GetAllTerms("How are you你好吗お元気ですか"),
              IsOkAndHolds(ElementsAre("How", " ", "are", " ", "you", "你好",
                                       "吗", "お", "元気", "です", "か")));

  EXPECT_THAT(
      language_segmenter->GetAllTerms("나는 California에 산다"),
      IsOkAndHolds(ElementsAre("나는", " ", "California", "에", " ", "산다")));
}

TEST_P(ReverseJniLanguageSegmenterTest, NotCopyStrings) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // Validates that the input strings are not copied
  const std::string text = "Hello World";
  const char* word1_address = text.c_str();
  const char* word2_address = text.c_str() + 6;
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<std::string_view> terms,
                             language_segmenter->GetAllTerms(text));
  ASSERT_THAT(terms, ElementsAre("Hello", " ", "World"));
  const char* word1_result_address = terms.at(0).data();
  const char* word2_result_address = terms.at(2).data();

  // The underlying char* should be the same
  EXPECT_THAT(word1_address, Eq(word1_result_address));
  EXPECT_THAT(word2_address, Eq(word2_result_address));
}

TEST_P(ReverseJniLanguageSegmenterTest, ResetToStartUtf32WordConnector) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kText = "com:google:android is package";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             segmenter->Segment(kText));

  // String:      "com:google:android is package"
  //               ^                 ^^ ^^
  // UTF-8 idx:    0              18 19 21 22
  // UTF-32 idx:   0              18 19 21 22
  auto position_or = itr->ResetToStartUtf32();
  EXPECT_THAT(position_or, IsOk());
  ASSERT_THAT(itr->GetTerm(), Eq("com:google:android"));
}

TEST_P(ReverseJniLanguageSegmenterTest, NewIteratorResetToStartUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kText = "How are you你好吗お元気ですか";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             segmenter->Segment(kText));

  // String:     "How are you你好吗お元気ですか"
  //              ^  ^^  ^^  ^  ^ ^ ^  ^  ^
  // UTF-8 idx:   0  3 4 7 8 11 172023 29 35
  // UTF-32 idx:  0  3 4 7 8 11 131415 17 19
  EXPECT_THAT(itr->ResetToStartUtf32(), IsOkAndHolds(Eq(0)));
  EXPECT_THAT(itr->GetTerm(), Eq("How"));
}

TEST_P(ReverseJniLanguageSegmenterTest, IteratorOneAdvanceResetToStartUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kText = "How are you你好吗お元気ですか";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             segmenter->Segment(kText));

  // String:     "How are you你好吗お元気ですか"
  //              ^  ^^  ^^  ^  ^ ^ ^  ^  ^
  // UTF-8 idx:   0  3 4 7 8 11 172023 29 35
  // UTF-32 idx:  0  3 4 7 8 11 131415 17 19
  ASSERT_TRUE(itr->Advance());  // itr points to 'How'
  EXPECT_THAT(itr->ResetToStartUtf32(), IsOkAndHolds(Eq(0)));
  EXPECT_THAT(itr->GetTerm(), Eq("How"));
}

TEST_P(ReverseJniLanguageSegmenterTest,
       IteratorMultipleAdvancesResetToStartUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kText = "How are you你好吗お元気ですか";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             segmenter->Segment(kText));

  // String:     "How are you你好吗お元気ですか"
  //              ^  ^^  ^^  ^  ^ ^ ^  ^  ^
  // UTF-8 idx:   0  3 4 7 8 11 172023 29 35
  // UTF-32 idx:  0  3 4 7 8 11 131415 17 19
  ASSERT_TRUE(itr->Advance());
  ASSERT_TRUE(itr->Advance());
  ASSERT_TRUE(itr->Advance());
  ASSERT_TRUE(itr->Advance());  // itr points to ' '
  EXPECT_THAT(itr->ResetToStartUtf32(), IsOkAndHolds(Eq(0)));
  EXPECT_THAT(itr->GetTerm(), Eq("How"));
}

TEST_P(ReverseJniLanguageSegmenterTest, IteratorDoneResetToStartUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kText = "How are you你好吗お元気ですか";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             segmenter->Segment(kText));

  // String:     "How are you你好吗お元気ですか"
  //              ^  ^^  ^^  ^  ^ ^ ^  ^  ^
  // UTF-8 idx:   0  3 4 7 8 11 172023 29 35
  // UTF-32 idx:  0  3 4 7 8 11 131415 17 19
  while (itr->Advance()) {
    // Do nothing.
  }
  EXPECT_THAT(itr->ResetToStartUtf32(), IsOkAndHolds(Eq(0)));
  EXPECT_THAT(itr->GetTerm(), Eq("How"));
}

TEST_P(ReverseJniLanguageSegmenterTest, ResetToTermAfterUtf32WordConnector) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kText = "package com:google:android name";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             segmenter->Segment(kText));

  // String:     "package com:google:android name"
  //              ^      ^^                 ^^
  // UTF-8 idx:   0      7 8               26 27
  // UTF-32 idx:  0      7 8               26 27
  auto position_or = itr->ResetToTermStartingAfterUtf32(8);
  EXPECT_THAT(position_or, IsOk());
  EXPECT_THAT(position_or.ValueOrDie(), Eq(26));
  ASSERT_THAT(itr->GetTerm(), Eq(" "));

  position_or = itr->ResetToTermStartingAfterUtf32(7);
  EXPECT_THAT(position_or, IsOk());
  EXPECT_THAT(position_or.ValueOrDie(), Eq(8));
  ASSERT_THAT(itr->GetTerm(), Eq("com:google:android"));
}

TEST_P(ReverseJniLanguageSegmenterTest, ResetToTermAfterUtf32OutOfBounds) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kText = "How are you你好吗お元気ですか";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             segmenter->Segment(kText));

  // String:     "How are you你好吗お元気ですか"
  //              ^  ^^  ^^  ^  ^ ^ ^  ^  ^
  // UTF-8 idx:   0  3 4 7 8 11 172023 29 35
  // UTF-32 idx:  0  3 4 7 8 11 131415 17 19
  ASSERT_THAT(itr->ResetToTermStartingAfterUtf32(7), IsOkAndHolds(Eq(8)));
  ASSERT_THAT(itr->GetTerm(), Eq("you"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(-1), IsOk());
  EXPECT_THAT(itr->GetTerm(), Eq("How"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(21),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(itr->GetTerm(), Eq("How"));
}

// Tests that ResetToTermAfter and Advance produce the same output. With the
// exception of the first term which is inacessible via ResetToTermAfter,
// the stream of terms produced by Advance calls should exacly match the
// terms produced by ResetToTermAfter calls with the current position
// provided as the argument.
TEST_P(ReverseJniLanguageSegmenterTest,
       MixedLanguagesResetToTermAfterUtf32EquivalentToAdvance) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kText = "How are𡔖 you你好吗お元気ですか";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> advance_itr,
      segmenter->Segment(kText));
  std::vector<std::string_view> advance_terms =
      GetAllTermsAdvance(advance_itr.get());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> reset_to_term_itr,
      segmenter->Segment(kText));
  std::vector<std::string_view> reset_terms =
      GetAllTermsResetAfterUtf32(reset_to_term_itr.get());

  EXPECT_THAT(reset_terms, testing::ElementsAreArray(advance_terms));
  EXPECT_THAT(reset_to_term_itr->GetTerm(), Eq(advance_itr->GetTerm()));
}

TEST_P(ReverseJniLanguageSegmenterTest,
       ThaiResetToTermAfterUtf32EquivalentToAdvance) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kThai = "ฉันเดินไปทำงานทุกวัน";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> advance_itr,
      segmenter->Segment(kThai));
  std::vector<std::string_view> advance_terms =
      GetAllTermsAdvance(advance_itr.get());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> reset_to_term_itr,
      segmenter->Segment(kThai));
  std::vector<std::string_view> reset_terms =
      GetAllTermsResetAfterUtf32(reset_to_term_itr.get());

  EXPECT_THAT(reset_terms, testing::ElementsAreArray(advance_terms));
  EXPECT_THAT(reset_to_term_itr->GetTerm(), Eq(advance_itr->GetTerm()));
}

TEST_P(ReverseJniLanguageSegmenterTest,
       KoreanResetToTermAfterUtf32EquivalentToAdvance) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kKorean = "나는 매일 출근합니다.";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> advance_itr,
      segmenter->Segment(kKorean));
  std::vector<std::string_view> advance_terms =
      GetAllTermsAdvance(advance_itr.get());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> reset_to_term_itr,
      segmenter->Segment(kKorean));
  std::vector<std::string_view> reset_terms =
      GetAllTermsResetAfterUtf32(reset_to_term_itr.get());

  EXPECT_THAT(reset_terms, testing::ElementsAreArray(advance_terms));
  EXPECT_THAT(reset_to_term_itr->GetTerm(), Eq(advance_itr->GetTerm()));
}

// Tests that ResetToTermAfter and Advance can be used in conjunction. Just as
// ResetToTermAfter(current_position) can be used to simulate Advance, users
// should be able to mix ResetToTermAfter(current_position) calls and Advance
// calls to mimic calling Advance.
TEST_P(ReverseJniLanguageSegmenterTest,
       MixedLanguagesResetToTermAfterUtf32InteroperableWithAdvance) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kText = "How are𡔖 you你好吗お元気ですか";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> advance_itr,
      segmenter->Segment(kText));
  std::vector<std::string_view> advance_terms =
      GetAllTermsAdvance(advance_itr.get());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> advance_and_reset_itr,
      segmenter->Segment(kText));
  std::vector<std::string_view> advance_and_reset_terms =
      GetAllTermsAdvanceAndResetAfterUtf32(advance_and_reset_itr.get());

  EXPECT_THAT(advance_and_reset_terms,
              testing::ElementsAreArray(advance_terms));
  EXPECT_THAT(advance_and_reset_itr->GetTerm(), Eq(advance_itr->GetTerm()));
}

TEST_P(ReverseJniLanguageSegmenterTest,
       ThaiResetToTermAfterUtf32InteroperableWithAdvance) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kThai = "ฉันเดินไปทำงานทุกวัน";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> advance_itr,
      segmenter->Segment(kThai));
  std::vector<std::string_view> advance_terms =
      GetAllTermsAdvance(advance_itr.get());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> advance_and_reset_itr,
      segmenter->Segment(kThai));
  std::vector<std::string_view> advance_and_reset_terms =
      GetAllTermsAdvanceAndResetAfterUtf32(advance_and_reset_itr.get());

  EXPECT_THAT(advance_and_reset_terms,
              testing::ElementsAreArray(advance_terms));
  EXPECT_THAT(advance_and_reset_itr->GetTerm(), Eq(advance_itr->GetTerm()));
}

TEST_P(ReverseJniLanguageSegmenterTest,
       KoreanResetToTermAfterUtf32InteroperableWithAdvance) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kKorean = "나는 매일 출근합니다.";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> advance_itr,
      segmenter->Segment(kKorean));
  std::vector<std::string_view> advance_terms =
      GetAllTermsAdvance(advance_itr.get());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> advance_and_reset_itr,
      segmenter->Segment(kKorean));
  std::vector<std::string_view> advance_and_reset_terms =
      GetAllTermsAdvanceAndResetAfterUtf32(advance_and_reset_itr.get());

  EXPECT_THAT(advance_and_reset_terms,
              testing::ElementsAreArray(advance_terms));
  EXPECT_THAT(advance_and_reset_itr->GetTerm(), Eq(advance_itr->GetTerm()));
}

TEST_P(ReverseJniLanguageSegmenterTest, MixedLanguagesResetToTermAfterUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> itr,
      language_segmenter->Segment("How are you你好吗お元気ですか"));

  // String:      "How are you你好吗お元気ですか"
  //               ^  ^^  ^^  ^  ^ ^ ^  ^  ^
  // UTF-8 idx:    0  3 4 7 8 11 172023 29 35
  // UTF-32 idx:   0  3 4 7 8 11 131415 17 19
  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(2), IsOkAndHolds(Eq(3)));
  EXPECT_THAT(itr->GetTerm(), Eq(" "));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(10), IsOkAndHolds(Eq(11)));
  EXPECT_THAT(itr->GetTerm(), Eq("你好"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(7), IsOkAndHolds(Eq(8)));
  EXPECT_THAT(itr->GetTerm(), Eq("you"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(18), IsOkAndHolds(Eq(19)));
  EXPECT_THAT(itr->GetTerm(), Eq("か"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(12), IsOkAndHolds(Eq(13)));
  EXPECT_THAT(itr->GetTerm(), Eq("吗"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(0), IsOkAndHolds(Eq(3)));
  EXPECT_THAT(itr->GetTerm(), Eq(" "));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(19),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());
}

TEST_P(ReverseJniLanguageSegmenterTest,
       ContinuousWhitespacesResetToTermAfterUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // Multiple continuous whitespaces are treated as one.
  constexpr std::string_view kTextWithSpace = "Hello          World";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             language_segmenter->Segment(kTextWithSpace));

  // String:      "Hello          World"
  //               ^    ^         ^
  // UTF-8 idx:    0    5         15
  // UTF-32 idx:   0    5         15
  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(0), IsOkAndHolds(Eq(5)));
  EXPECT_THAT(itr->GetTerm(), Eq(" "));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(2), IsOkAndHolds(Eq(5)));
  EXPECT_THAT(itr->GetTerm(), Eq(" "));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(10), IsOkAndHolds(Eq(15)));
  EXPECT_THAT(itr->GetTerm(), Eq("World"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(5), IsOkAndHolds(Eq(15)));
  EXPECT_THAT(itr->GetTerm(), Eq("World"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(15),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(17),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(19),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());
}

TEST_P(ReverseJniLanguageSegmenterTest, ChineseResetToTermAfterUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // CJKT (Chinese, Japanese, Khmer, Thai) are the 4 main languages that
  // don't have whitespaces as word delimiter. Chinese
  constexpr std::string_view kChinese = "我每天走路去上班。";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             language_segmenter->Segment(kChinese));
  // String:       "我每天走路去上班。"
  //                ^ ^  ^   ^^   ^
  // UTF-8 idx:     0 3  9  15 18 24
  // UTF-832 idx:   0 1  3   5 6  8
  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(0), IsOkAndHolds(Eq(1)));
  EXPECT_THAT(itr->GetTerm(), Eq("每天"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(2), IsOkAndHolds(Eq(3)));
  EXPECT_THAT(itr->GetTerm(), Eq("走路"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(7), IsOkAndHolds(Eq(8)));
  EXPECT_THAT(itr->GetTerm(), Eq("。"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(8),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());
}

TEST_P(ReverseJniLanguageSegmenterTest, JapaneseResetToTermAfterUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // Japanese
  constexpr std::string_view kJapanese = "私は毎日仕事に歩いています。";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             language_segmenter->Segment(kJapanese));
  // String:       "私は毎日仕事に歩いています。"
  //                ^ ^ ^  ^  ^ ^ ^ ^  ^  ^
  // UTF-8 idx:     0 3 6  12 18212427 33 39
  // UTF-32 idx:    0 1 2  4  6 7 8 9  11 13
  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(0), IsOkAndHolds(Eq(1)));
  EXPECT_THAT(itr->GetTerm(), Eq("は"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(13),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(3), IsOkAndHolds(Eq(4)));
  EXPECT_THAT(itr->GetTerm(), Eq("仕事"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(12), IsOkAndHolds(Eq(13)));
  EXPECT_THAT(itr->GetTerm(), Eq("。"));
}

TEST_P(ReverseJniLanguageSegmenterTest, KhmerResetToTermAfterUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kKhmer = "ញុំដើរទៅធ្វើការរាល់ថ្ងៃ។";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             language_segmenter->Segment(kKhmer));
  // String:            "ញុំដើរទៅធ្វើការរាល់ថ្ងៃ។"
  //                     ^ ^   ^   ^  ^
  // UTF-8 idx:          0 9   24  45 69
  // UTF-32 idx:         0 3   8   15 23
  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(0), IsOkAndHolds(Eq(3)));
  EXPECT_THAT(itr->GetTerm(), Eq("ដើរទៅ"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(15), IsOkAndHolds(Eq(23)));
  EXPECT_THAT(itr->GetTerm(), Eq("។"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(23),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(6), IsOkAndHolds(Eq(8)));
  EXPECT_THAT(itr->GetTerm(), Eq("ធ្វើការ"));
}

TEST_P(ReverseJniLanguageSegmenterTest, ThaiResetToTermAfterUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // Thai
  constexpr std::string_view kThai = "ฉันเดินไปทำงานทุกวัน";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             language_segmenter->Segment(kThai));
  // String:      "ฉันเดินไปทำงานทุกวัน"
  //               ^ ^  ^ ^    ^ ^
  // UTF-8 idx:    0 9 21 27  42 51
  // UTF-32 idx:   0 3  7 9   14 17
  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(0), IsOkAndHolds(Eq(3)));
  EXPECT_THAT(itr->GetTerm(), Eq("เดิน"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(17),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(6), IsOkAndHolds(Eq(7)));
  EXPECT_THAT(itr->GetTerm(), Eq("ไป"));

  EXPECT_THAT(itr->ResetToTermStartingAfterUtf32(12), IsOkAndHolds(Eq(14)));
  EXPECT_THAT(itr->GetTerm(), Eq("ทุก"));
}

TEST_P(ReverseJniLanguageSegmenterTest, ResetToTermBeforeWordConnectorUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kText = "package name com:google:android!";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             segmenter->Segment(kText));

  // String:      "package name com:google:android!"
  //               ^      ^^   ^^                 ^
  // UTF-8 idx:    0      7 8 12 13               31
  // UTF-32 idx:   0      7 8 12 13               31
  auto position_or = itr->ResetToTermEndingBeforeUtf32(31);
  EXPECT_THAT(position_or, IsOk());
  EXPECT_THAT(position_or.ValueOrDie(), Eq(13));
  ASSERT_THAT(itr->GetTerm(), Eq("com:google:android"));

  position_or = itr->ResetToTermEndingBeforeUtf32(21);
  EXPECT_THAT(position_or, IsOk());
  EXPECT_THAT(position_or.ValueOrDie(), Eq(12));
  ASSERT_THAT(itr->GetTerm(), Eq(" "));
}

TEST_P(ReverseJniLanguageSegmenterTest, ResetToTermBeforeOutOfBoundsUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kText = "How are you你好吗お元気ですか";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             segmenter->Segment(kText));

  // String:      "How are you你好吗お元気ですか"
  //               ^  ^^  ^^  ^  ^ ^ ^  ^  ^
  // UTF-8 idx:    0  3 4 7 8 11 172023 29 35
  // UTF-32 idx:   0  3 4 7 8 11 131415 17 19
  ASSERT_THAT(itr->ResetToTermEndingBeforeUtf32(7), IsOkAndHolds(Eq(4)));
  ASSERT_THAT(itr->GetTerm(), Eq("are"));

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(-1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(itr->GetTerm(), Eq("are"));

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(29), IsOk());
  EXPECT_THAT(itr->GetTerm(), Eq("か"));
}

// Tests that ResetToTermBefore and Advance produce the same output. With the
// exception of the last term which is inacessible via ResetToTermBefore,
// the stream of terms produced by Advance calls should exacly match the
// terms produced by ResetToTermBefore calls with the current position
// provided as the argument (after their order has been reversed).
TEST_P(ReverseJniLanguageSegmenterTest,
       MixedLanguagesResetToTermBeforeEquivalentToAdvanceUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kText = "How are𡔖 you你好吗お元気ですか";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> advance_itr,
      segmenter->Segment(kText));
  std::vector<std::string_view> advance_terms =
      GetAllTermsAdvance(advance_itr.get());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> reset_to_term_itr,
      segmenter->Segment(kText));
  std::vector<std::string_view> reset_terms =
      GetAllTermsResetBeforeUtf32(reset_to_term_itr.get());
  std::reverse(reset_terms.begin(), reset_terms.end());

  EXPECT_THAT(reset_terms, testing::ElementsAreArray(advance_terms));
  EXPECT_THAT(reset_to_term_itr->GetTerm(), IsEmpty());
  EXPECT_THAT(advance_itr->GetTerm(), IsEmpty());
}

TEST_P(ReverseJniLanguageSegmenterTest,
       ThaiResetToTermBeforeEquivalentToAdvanceUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kThai = "ฉันเดินไปทำงานทุกวัน";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> advance_itr,
      segmenter->Segment(kThai));
  std::vector<std::string_view> advance_terms =
      GetAllTermsAdvance(advance_itr.get());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> reset_to_term_itr,
      segmenter->Segment(kThai));
  std::vector<std::string_view> reset_terms =
      GetAllTermsResetBeforeUtf32(reset_to_term_itr.get());
  std::reverse(reset_terms.begin(), reset_terms.end());

  EXPECT_THAT(reset_terms, testing::ElementsAreArray(advance_terms));
  EXPECT_THAT(reset_to_term_itr->GetTerm(), Eq(advance_itr->GetTerm()));
}

TEST_P(ReverseJniLanguageSegmenterTest,
       KoreanResetToTermBeforeEquivalentToAdvanceUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto segmenter, language_segmenter_factory::Create(
                          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kKorean = "나는 매일 출근합니다.";
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> advance_itr,
      segmenter->Segment(kKorean));
  std::vector<std::string_view> advance_terms =
      GetAllTermsAdvance(advance_itr.get());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> reset_to_term_itr,
      segmenter->Segment(kKorean));
  std::vector<std::string_view> reset_terms =
      GetAllTermsResetBeforeUtf32(reset_to_term_itr.get());
  std::reverse(reset_terms.begin(), reset_terms.end());

  EXPECT_THAT(reset_terms, testing::ElementsAreArray(advance_terms));
  EXPECT_THAT(reset_to_term_itr->GetTerm(), Eq(advance_itr->GetTerm()));
}

TEST_P(ReverseJniLanguageSegmenterTest, MixedLanguagesResetToTermBeforeUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<LanguageSegmenter::Iterator> itr,
      language_segmenter->Segment("How are you你好吗お元気ですか"));

  // String:      "How are you你好吗お元気ですか"
  //               ^  ^^  ^^  ^  ^ ^ ^  ^  ^
  // UTF-8 idx:    0  3 4 7 8 11 172023 29 35
  // UTF-32 idx:   0  3 4 7 8 11 131415 17 19
  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(2),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(10), IsOkAndHolds(Eq(7)));
  EXPECT_THAT(itr->GetTerm(), Eq(" "));

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(7), IsOkAndHolds(Eq(4)));
  EXPECT_THAT(itr->GetTerm(), Eq("are"));

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(18), IsOkAndHolds(Eq(15)));
  EXPECT_THAT(itr->GetTerm(), Eq("元気"));

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(12), IsOkAndHolds(Eq(8)));
  EXPECT_THAT(itr->GetTerm(), Eq("you"));

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(0),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(19), IsOkAndHolds(Eq(17)));
  EXPECT_THAT(itr->GetTerm(), Eq("です"));
}

TEST_P(ReverseJniLanguageSegmenterTest,
       ContinuousWhitespacesResetToTermBeforeUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // Multiple continuous whitespaces are treated as one.
  constexpr std::string_view kTextWithSpace = "Hello          World";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             language_segmenter->Segment(kTextWithSpace));

  // String:      "Hello          World"
  //               ^    ^         ^
  // UTF-8 idx:    0    5         15
  // UTF-32 idx:   0    5         15
  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(0),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(2),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(10), IsOkAndHolds(Eq(0)));
  EXPECT_THAT(itr->GetTerm(), Eq("Hello"));

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(5), IsOkAndHolds(Eq(0)));
  EXPECT_THAT(itr->GetTerm(), Eq("Hello"));

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(15), IsOkAndHolds(Eq(5)));
  EXPECT_THAT(itr->GetTerm(), Eq(" "));

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(17), IsOkAndHolds(Eq(5)));
  EXPECT_THAT(itr->GetTerm(), Eq(" "));

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(19), IsOkAndHolds(Eq(5)));
  EXPECT_THAT(itr->GetTerm(), Eq(" "));
}

TEST_P(ReverseJniLanguageSegmenterTest, ChineseResetToTermBeforeUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // CJKT (Chinese, Japanese, Khmer, Thai) are the 4 main languages that
  // don't have whitespaces as word delimiter. Chinese
  constexpr std::string_view kChinese = "我每天走路去上班。";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             language_segmenter->Segment(kChinese));
  // String:      "我每天走路去上班。"
  //               ^ ^  ^   ^^
  // UTF-8 idx:    0 3  9  15 18
  // UTF-32 idx:   0 1  3   5 6
  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(0),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(2), IsOkAndHolds(Eq(0)));
  EXPECT_THAT(itr->GetTerm(), Eq("我"));

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(7), IsOkAndHolds(Eq(5)));
  EXPECT_THAT(itr->GetTerm(), Eq("去"));
}

TEST_P(ReverseJniLanguageSegmenterTest, JapaneseResetToTermBeforeUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // Japanese
  constexpr std::string_view kJapanese = "私は毎日仕事に歩いています。";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             language_segmenter->Segment(kJapanese));
  // String:      "私は毎日仕事に歩いています。"
  //               ^ ^ ^  ^  ^ ^ ^ ^  ^
  // UTF-8 idx:    0 3 6  12 18212427 33
  // UTF-32 idx:   0 1 2  4  6 7 8 9  11
  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(0),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(11), IsOkAndHolds(Eq(9)));
  EXPECT_THAT(itr->GetTerm(), Eq("てい"));

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(3), IsOkAndHolds(Eq(1)));
  EXPECT_THAT(itr->GetTerm(), Eq("は"));
}

TEST_P(ReverseJniLanguageSegmenterTest, KhmerResetToTermBeforeUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  constexpr std::string_view kKhmer = "ញុំដើរទៅធ្វើការរាល់ថ្ងៃ។";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             language_segmenter->Segment(kKhmer));
  // String:      "ញុំដើរទៅធ្វើការរាល់ថ្ងៃ។"
  //               ^ ^   ^   ^
  // UTF-8 idx:    0 9   24  45
  // UTF-32 idx:   0 3   8   15
  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(0),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(16), IsOkAndHolds(Eq(8)));
  EXPECT_THAT(itr->GetTerm(), Eq("ធ្វើការ"));

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(5), IsOkAndHolds(Eq(0)));
  EXPECT_THAT(itr->GetTerm(), Eq("ញុំ"));
}

TEST_P(ReverseJniLanguageSegmenterTest, ThaiResetToTermBeforeUtf32) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // Thai
  constexpr std::string_view kThai = "ฉันเดินไปทำงานทุกวัน";
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<LanguageSegmenter::Iterator> itr,
                             language_segmenter->Segment(kThai));
  // String:      "ฉันเดินไปทำงานทุกวัน"
  //               ^ ^  ^ ^    ^ ^
  // UTF-8 idx:    0 9 21 27  42 51
  // UTF-32 idx:   0 3  7 9   14 17
  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(0),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(itr->GetTerm(), IsEmpty());

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(17), IsOkAndHolds(Eq(14)));
  EXPECT_THAT(itr->GetTerm(), Eq("ทุก"));

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(4), IsOkAndHolds(Eq(0)));
  EXPECT_THAT(itr->GetTerm(), Eq("ฉัน"));

  EXPECT_THAT(itr->ResetToTermEndingBeforeUtf32(11), IsOkAndHolds(Eq(7)));
  EXPECT_THAT(itr->GetTerm(), Eq("ไป"));
}

TEST_P(ReverseJniLanguageSegmenterTest, QuerySyntax) {
  ICING_ASSERT_OK_AND_ASSIGN(
      auto language_segmenter,
      language_segmenter_factory::Create(
          GetSegmenterOptions(GetLocale(), jni_cache_.get())));
  // Validates that the input strings are not copied
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<std::string_view> terms,
      language_segmenter->GetAllTerms(
          "(-term1 OR term2) AND property1.subproperty2:term3"));
  EXPECT_THAT(terms, ElementsAre("(", "-", "term1", " ", "OR", " ", "term2",
                                 ")", " ", "AND", " ", "property1", ".",
                                 "subproperty2", ":", "term3"));
}

INSTANTIATE_TEST_SUITE_P(
    LocaleName, ReverseJniLanguageSegmenterTest,
    testing::Values(ULOC_US, ULOC_UK, ULOC_CANADA, ULOC_CANADA_FRENCH,
                    ULOC_FRANCE, ULOC_GERMANY, ULOC_ITALY, ULOC_JAPAN,
                    ULOC_KOREA, ULOC_SIMPLIFIED_CHINESE,
                    ULOC_TRADITIONAL_CHINESE,
                    "es_ES",        // Spanish
                    "hi_IN",        // Hindi
                    "th_TH",        // Thai
                    "lo_LA",        // Lao
                    "km_KH",        // Khmer
                    "ar_DZ",        // Arabic
                    "ru_RU",        // Russian
                    "pt_PT",        // Portuguese
                    "en_US_POSIX"   // American English (Computer)
                    "wrong_locale"  // Will fall back to ICU default locale
                    ""              // Will fall back to ICU default locale
                    ));

}  // namespace test_internal

}  // namespace lib
}  // namespace icing
