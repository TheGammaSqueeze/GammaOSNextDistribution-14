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

#include "icing/query/advanced_query_parser/lexer.h"

#include <memory>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

using ::testing::ElementsAre;

MATCHER_P2(EqualsLexerToken, text, type, "") {
  const Lexer::LexerToken& actual = arg;
  *result_listener << "actual is {text=" << actual.text
                   << ", type=" << static_cast<int>(actual.type)
                   << "}, but expected was {text=" << text
                   << ", type=" << static_cast<int>(type) << "}.";
  return actual.text == text && actual.type == type;
}

MATCHER_P(EqualsLexerToken, type, "") {
  const Lexer::LexerToken& actual = arg;
  *result_listener << "actual is {text=" << actual.text
                   << ", type=" << static_cast<int>(actual.type)
                   << "}, but expected was {text=(empty), type="
                   << static_cast<int>(type) << "}.";
  return actual.text.empty() && actual.type == type;
}

TEST(LexerTest, SimpleQuery) {
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("foo", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("foo", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("fooAND", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("fooAND", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("ORfoo", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("ORfoo", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("fooANDbar", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens, ElementsAre(EqualsLexerToken("fooANDbar",
                                                   Lexer::TokenType::TEXT)));
}

TEST(LexerTest, PrefixQuery) {
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("foo*", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("foo", Lexer::TokenType::TEXT),
                          EqualsLexerToken("", Lexer::TokenType::STAR)));

  lexer = std::make_unique<Lexer>("fooAND*", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("fooAND", Lexer::TokenType::TEXT),
                          EqualsLexerToken("", Lexer::TokenType::STAR)));

  lexer = std::make_unique<Lexer>("*ORfoo", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("", Lexer::TokenType::STAR),
                          EqualsLexerToken("ORfoo", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("fooANDbar*", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("fooANDbar", Lexer::TokenType::TEXT),
                          EqualsLexerToken("", Lexer::TokenType::STAR)));
}

TEST(LexerTest, SimpleStringQuery) {
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("\"foo\"", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("foo", Lexer::TokenType::STRING)));

  lexer = std::make_unique<Lexer>("\"fooAND\"", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens, ElementsAre(EqualsLexerToken("fooAND",
                                                   Lexer::TokenType::STRING)));

  lexer = std::make_unique<Lexer>("\"ORfoo\"", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("ORfoo", Lexer::TokenType::STRING)));

  lexer = std::make_unique<Lexer>("\"fooANDbar\"", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens, ElementsAre(EqualsLexerToken("fooANDbar",
                                                   Lexer::TokenType::STRING)));
}

TEST(LexerTest, TwoTermQuery) {
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("foo AND bar", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("foo", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::AND),
                          EqualsLexerToken("bar", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("foo && bar", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("foo", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::AND),
                          EqualsLexerToken("bar", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("foo&&bar", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("foo", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::AND),
                          EqualsLexerToken("bar", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("foo OR \"bar\"", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("foo", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::OR),
                          EqualsLexerToken("bar", Lexer::TokenType::STRING)));
}

TEST(LexerTest, QueryWithSpecialSymbol) {
  // With escaping
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("foo\\ \\&\\&bar", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(tokens, ElementsAre(EqualsLexerToken("foo &&bar",
                                                   Lexer::TokenType::TEXT)));
  lexer = std::make_unique<Lexer>("foo\\&\\&bar&&baz", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("foo&&bar", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::AND),
                          EqualsLexerToken("baz", Lexer::TokenType::TEXT)));
  lexer = std::make_unique<Lexer>("foo\\\"", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("foo\"", Lexer::TokenType::TEXT)));

  // With quotation marks
  lexer = std::make_unique<Lexer>("\"foo &&bar\"", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens, ElementsAre(EqualsLexerToken("foo &&bar",
                                                   Lexer::TokenType::STRING)));
  lexer = std::make_unique<Lexer>("\"foo&&bar\"&&baz", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(
      tokens,
      ElementsAre(EqualsLexerToken("foo&&bar", Lexer::TokenType::STRING),
                  EqualsLexerToken(Lexer::TokenType::AND),
                  EqualsLexerToken("baz", Lexer::TokenType::TEXT)));
  lexer = std::make_unique<Lexer>("\"foo\\\"\"", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens, ElementsAre(EqualsLexerToken("foo\\\"",
                                                   Lexer::TokenType::STRING)));
}

TEST(LexerTest, TextInStringShouldBeOriginal) {
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("\"foo\\nbar\"", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(tokens, ElementsAre(EqualsLexerToken("foo\\nbar",
                                                   Lexer::TokenType::STRING)));
}

TEST(LexerTest, QueryWithFunctionCalls) {
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("foo AND fun(bar)", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(
      tokens,
      ElementsAre(EqualsLexerToken("foo", Lexer::TokenType::TEXT),
                  EqualsLexerToken(Lexer::TokenType::AND),
                  EqualsLexerToken("fun", Lexer::TokenType::FUNCTION_NAME),
                  EqualsLexerToken(Lexer::TokenType::LPAREN),
                  EqualsLexerToken("bar", Lexer::TokenType::TEXT),
                  EqualsLexerToken(Lexer::TokenType::RPAREN)));

  // Not a function call
  lexer = std::make_unique<Lexer>("foo AND fun (bar)", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("foo", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::AND),
                          EqualsLexerToken("fun", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::LPAREN),
                          EqualsLexerToken("bar", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::RPAREN)));
}

TEST(LexerTest, QueryWithComparator) {
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("name: foo", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("name", Lexer::TokenType::TEXT),
                          EqualsLexerToken(":", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("foo", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("email.name:foo", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("email", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::DOT),
                          EqualsLexerToken("name", Lexer::TokenType::TEXT),
                          EqualsLexerToken(":", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("foo", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("age > 20", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("age", Lexer::TokenType::TEXT),
                          EqualsLexerToken(">", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("20", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("age>=20", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("age", Lexer::TokenType::TEXT),
                          EqualsLexerToken(">=", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("20", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("age <20", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("age", Lexer::TokenType::TEXT),
                          EqualsLexerToken("<", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("20", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("age<= 20", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("age", Lexer::TokenType::TEXT),
                          EqualsLexerToken("<=", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("20", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("age == 20", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("age", Lexer::TokenType::TEXT),
                          EqualsLexerToken("==", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("20", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("age != 20", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("age", Lexer::TokenType::TEXT),
                          EqualsLexerToken("!=", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("20", Lexer::TokenType::TEXT)));
}

TEST(LexerTest, ComplexQuery) {
  std::unique_ptr<Lexer> lexer = std::make_unique<Lexer>(
      "email.sender: (foo* AND bar OR pow(age, 2)>100) || (-baz foo) && "
      "NOT verbatimSearch(\"hello world\")",
      Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(
      tokens,
      ElementsAre(
          EqualsLexerToken("email", Lexer::TokenType::TEXT),
          EqualsLexerToken(Lexer::TokenType::DOT),
          EqualsLexerToken("sender", Lexer::TokenType::TEXT),
          EqualsLexerToken(":", Lexer::TokenType::COMPARATOR),
          EqualsLexerToken(Lexer::TokenType::LPAREN),
          EqualsLexerToken("foo", Lexer::TokenType::TEXT),
          EqualsLexerToken("", Lexer::TokenType::STAR),
          EqualsLexerToken(Lexer::TokenType::AND),
          EqualsLexerToken("bar", Lexer::TokenType::TEXT),
          EqualsLexerToken(Lexer::TokenType::OR),
          EqualsLexerToken("pow", Lexer::TokenType::FUNCTION_NAME),
          EqualsLexerToken(Lexer::TokenType::LPAREN),
          EqualsLexerToken("age", Lexer::TokenType::TEXT),
          EqualsLexerToken(Lexer::TokenType::COMMA),
          EqualsLexerToken("2", Lexer::TokenType::TEXT),
          EqualsLexerToken(Lexer::TokenType::RPAREN),
          EqualsLexerToken(">", Lexer::TokenType::COMPARATOR),
          EqualsLexerToken("100", Lexer::TokenType::TEXT),
          EqualsLexerToken(Lexer::TokenType::RPAREN),
          EqualsLexerToken(Lexer::TokenType::OR),
          EqualsLexerToken(Lexer::TokenType::LPAREN),
          EqualsLexerToken(Lexer::TokenType::MINUS),
          EqualsLexerToken("baz", Lexer::TokenType::TEXT),
          EqualsLexerToken("foo", Lexer::TokenType::TEXT),
          EqualsLexerToken(Lexer::TokenType::RPAREN),
          EqualsLexerToken(Lexer::TokenType::AND),
          EqualsLexerToken(Lexer::TokenType::NOT),
          EqualsLexerToken("verbatimSearch", Lexer::TokenType::FUNCTION_NAME),
          EqualsLexerToken(Lexer::TokenType::LPAREN),
          EqualsLexerToken("hello world", Lexer::TokenType::STRING),
          EqualsLexerToken(Lexer::TokenType::RPAREN)));
}

TEST(LexerTest, UTF8WhiteSpace) {
  std::unique_ptr<Lexer> lexer = std::make_unique<Lexer>(
      "\xe2\x80\x88"
      "foo"
      "\xe2\x80\x89"
      "\xe2\x80\x89"
      "bar"
      "\xe2\x80\x8a",
      Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("foo", Lexer::TokenType::TEXT),
                          EqualsLexerToken("bar", Lexer::TokenType::TEXT)));
}

TEST(LexerTest, CJKT) {
  std::unique_ptr<Lexer> lexer = std::make_unique<Lexer>(
      "我 && 每天 || 走路 OR 去 -上班", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("我", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::AND),
                          EqualsLexerToken("每天", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::OR),
                          EqualsLexerToken("走路", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::OR),
                          EqualsLexerToken("去", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::MINUS),
                          EqualsLexerToken("上班", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("私&& は ||毎日 AND 仕事 -に 歩い て い ます",
                                  Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("私", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::AND),
                          EqualsLexerToken("は", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::OR),
                          EqualsLexerToken("毎日", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::AND),
                          EqualsLexerToken("仕事", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::MINUS),
                          EqualsLexerToken("に", Lexer::TokenType::TEXT),
                          EqualsLexerToken("歩い", Lexer::TokenType::TEXT),
                          EqualsLexerToken("て", Lexer::TokenType::TEXT),
                          EqualsLexerToken("い", Lexer::TokenType::TEXT),
                          EqualsLexerToken("ます", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("ញុំ&&ដើរទៅ||ធ្វើការ-រាល់ថ្ងៃ",
                                  Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(
      tokens,
      ElementsAre(EqualsLexerToken("ញុំ", Lexer::TokenType::TEXT),
                  EqualsLexerToken(Lexer::TokenType::AND),
                  EqualsLexerToken("ដើរទៅ", Lexer::TokenType::TEXT),
                  EqualsLexerToken(Lexer::TokenType::OR),
                  EqualsLexerToken("ធ្វើការ-រាល់ថ្ងៃ", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>(
      "나는"
      "\xe2\x80\x88"  // White Space
      "매일"
      "\xe2\x80\x89"  // White Space
      "출근합니다",
      Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(
      tokens,
      ElementsAre(EqualsLexerToken("나는", Lexer::TokenType::TEXT),
                  EqualsLexerToken("매일", Lexer::TokenType::TEXT),
                  EqualsLexerToken("출근합니다", Lexer::TokenType::TEXT)));
}

TEST(LexerTest, SyntaxError) {
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("\"foo", Lexer::Language::QUERY);
  EXPECT_THAT(lexer->ExtractTokens(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  lexer = std::make_unique<Lexer>("\"foo\\", Lexer::Language::QUERY);
  EXPECT_THAT(lexer->ExtractTokens(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  lexer = std::make_unique<Lexer>("foo\\", Lexer::Language::QUERY);
  EXPECT_THAT(lexer->ExtractTokens(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

// "!", "=", "&" and "|" should be treated as valid symbols in TEXT, if not
// matched as "!=", "==", "&&", or "||".
TEST(LexerTest, SpecialSymbolAsText) {
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("age=20", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("age=20", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("age !20", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("age", Lexer::TokenType::TEXT),
                          EqualsLexerToken("!20", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("foo& bar", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("foo&", Lexer::TokenType::TEXT),
                          EqualsLexerToken("bar", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("foo | bar", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("foo", Lexer::TokenType::TEXT),
                          EqualsLexerToken("|", Lexer::TokenType::TEXT),
                          EqualsLexerToken("bar", Lexer::TokenType::TEXT)));
}

TEST(LexerTest, ScoringArithmetic) {
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("1 + 2", Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("1", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::PLUS),
                          EqualsLexerToken("2", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("1+2*3/4", Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("1", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::PLUS),
                          EqualsLexerToken("2", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::TIMES),
                          EqualsLexerToken("3", Lexer::TokenType::TEXT),
                          EqualsLexerToken(Lexer::TokenType::DIV),
                          EqualsLexerToken("4", Lexer::TokenType::TEXT)));

  // Arithmetic operators will not be produced in query language.
  lexer = std::make_unique<Lexer>("1 + 2", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("1", Lexer::TokenType::TEXT),
                          EqualsLexerToken("+", Lexer::TokenType::TEXT),
                          EqualsLexerToken("2", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("1+2*3/4", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("1+2", Lexer::TokenType::TEXT),
                          EqualsLexerToken("", Lexer::TokenType::STAR),
                          EqualsLexerToken("3/4", Lexer::TokenType::TEXT)));
}

// Currently, in scoring language, the lexer will view these logic operators as
// TEXTs. In the future, they may be rejected instead.
TEST(LexerTest, LogicOperatorNotInScoring) {
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("1 && 2", Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("1", Lexer::TokenType::TEXT),
                          EqualsLexerToken("&&", Lexer::TokenType::TEXT),
                          EqualsLexerToken("2", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("1&&2", Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("1&&2", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("1&&2 ||3", Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("1&&2", Lexer::TokenType::TEXT),
                          EqualsLexerToken("||3", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("1 AND 2 OR 3 AND NOT 4",
                                  Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("1", Lexer::TokenType::TEXT),
                          EqualsLexerToken("AND", Lexer::TokenType::TEXT),
                          EqualsLexerToken("2", Lexer::TokenType::TEXT),
                          EqualsLexerToken("OR", Lexer::TokenType::TEXT),
                          EqualsLexerToken("3", Lexer::TokenType::TEXT),
                          EqualsLexerToken("AND", Lexer::TokenType::TEXT),
                          EqualsLexerToken("NOT", Lexer::TokenType::TEXT),
                          EqualsLexerToken("4", Lexer::TokenType::TEXT)));
}

TEST(LexerTest, ComparatorNotInScoring) {
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("1 > 2", Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("1", Lexer::TokenType::TEXT),
                          EqualsLexerToken(">", Lexer::TokenType::TEXT),
                          EqualsLexerToken("2", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("1>2", Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("1>2", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("1>2>=3 <= 4:5== 6<7<=8!= 9",
                                  Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("1>2>=3", Lexer::TokenType::TEXT),
                          EqualsLexerToken("<=", Lexer::TokenType::TEXT),
                          EqualsLexerToken("4:5==", Lexer::TokenType::TEXT),
                          EqualsLexerToken("6<7<=8!=", Lexer::TokenType::TEXT),
                          EqualsLexerToken("9", Lexer::TokenType::TEXT)));

  // Comparator should be produced in query language.
  lexer = std::make_unique<Lexer>("1>2>=3 <= 4:5== 6<7<=8!= 9",
                                  Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(tokens, lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("1", Lexer::TokenType::TEXT),
                          EqualsLexerToken(">", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("2", Lexer::TokenType::TEXT),
                          EqualsLexerToken(">=", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("3", Lexer::TokenType::TEXT),
                          EqualsLexerToken("<=", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("4", Lexer::TokenType::TEXT),
                          EqualsLexerToken(":", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("5", Lexer::TokenType::TEXT),
                          EqualsLexerToken("==", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("6", Lexer::TokenType::TEXT),
                          EqualsLexerToken("<", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("7", Lexer::TokenType::TEXT),
                          EqualsLexerToken("<=", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("8", Lexer::TokenType::TEXT),
                          EqualsLexerToken("!=", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("9", Lexer::TokenType::TEXT)));
}

TEST(LexerTest, ComplexScoring) {
  std::unique_ptr<Lexer> lexer = std::make_unique<Lexer>(
      "1/log( (CreationTimestamp(document) + LastUsedTimestamp(document)) / 2 "
      ") * pow(2.3, DocumentScore())",
      Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(
      tokens,
      ElementsAre(
          EqualsLexerToken("1", Lexer::TokenType::TEXT),
          EqualsLexerToken(Lexer::TokenType::DIV),
          EqualsLexerToken("log", Lexer::TokenType::FUNCTION_NAME),
          EqualsLexerToken(Lexer::TokenType::LPAREN),
          EqualsLexerToken(Lexer::TokenType::LPAREN),
          EqualsLexerToken("CreationTimestamp",
                           Lexer::TokenType::FUNCTION_NAME),
          EqualsLexerToken(Lexer::TokenType::LPAREN),
          EqualsLexerToken("document", Lexer::TokenType::TEXT),
          EqualsLexerToken(Lexer::TokenType::RPAREN),
          EqualsLexerToken(Lexer::TokenType::PLUS),
          EqualsLexerToken("LastUsedTimestamp",
                           Lexer::TokenType::FUNCTION_NAME),
          EqualsLexerToken(Lexer::TokenType::LPAREN),
          EqualsLexerToken("document", Lexer::TokenType::TEXT),
          EqualsLexerToken(Lexer::TokenType::RPAREN),
          EqualsLexerToken(Lexer::TokenType::RPAREN),
          EqualsLexerToken(Lexer::TokenType::DIV),
          EqualsLexerToken("2", Lexer::TokenType::TEXT),
          EqualsLexerToken(Lexer::TokenType::RPAREN),
          EqualsLexerToken(Lexer::TokenType::TIMES),
          EqualsLexerToken("pow", Lexer::TokenType::FUNCTION_NAME),
          EqualsLexerToken(Lexer::TokenType::LPAREN),
          EqualsLexerToken("2", Lexer::TokenType::TEXT),
          EqualsLexerToken(Lexer::TokenType::DOT),
          EqualsLexerToken("3", Lexer::TokenType::TEXT),
          EqualsLexerToken(Lexer::TokenType::COMMA),
          EqualsLexerToken("DocumentScore", Lexer::TokenType::FUNCTION_NAME),
          EqualsLexerToken(Lexer::TokenType::LPAREN),
          EqualsLexerToken(Lexer::TokenType::RPAREN),
          EqualsLexerToken(Lexer::TokenType::RPAREN)));
}

// foo:bar:baz is considered an invalid query as proposed in
// http://go/appsearch-advanced-query-impl-plan#bookmark=id.yoeyepokmbc5 ; this
// ensures that the lexer consistently tokenizes colons independently.
TEST(LexerTest, NoAmbiguousTokenizing) {
  // This is an invalid query; the lexer doesn't treat `bar:baz` as one token.
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("foo:bar:baz", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> invalidQueryTokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(invalidQueryTokens,
              ElementsAre(EqualsLexerToken("foo", Lexer::TokenType::TEXT),
                          EqualsLexerToken(":", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("bar", Lexer::TokenType::TEXT),
                          EqualsLexerToken(":", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("baz", Lexer::TokenType::TEXT)));

  lexer = std::make_unique<Lexer>("foo:\"bar:baz\"", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> validQueryTokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(
      validQueryTokens,
      ElementsAre(EqualsLexerToken("foo", Lexer::TokenType::TEXT),
                  EqualsLexerToken(":", Lexer::TokenType::COMPARATOR),
                  EqualsLexerToken("bar:baz", Lexer::TokenType::STRING)));
}

TEST(LexerTest, WhiteSpacesDoNotAffectColonTokenization) {
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("a:b c : d e: f g :h", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("a", Lexer::TokenType::TEXT),
                          EqualsLexerToken(":", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("b", Lexer::TokenType::TEXT),
                          EqualsLexerToken("c", Lexer::TokenType::TEXT),
                          EqualsLexerToken(":", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("d", Lexer::TokenType::TEXT),
                          EqualsLexerToken("e", Lexer::TokenType::TEXT),
                          EqualsLexerToken(":", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("f", Lexer::TokenType::TEXT),
                          EqualsLexerToken("g", Lexer::TokenType::TEXT),
                          EqualsLexerToken(":", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("h", Lexer::TokenType::TEXT)));
}

// For the "bar:baz" part to be treated as a TEXT token in a query like
// foo:bar:baz, an explicit escape is required, so use foo:bar\:baz instead.
TEST(LexerTest, ColonInTextRequiresExplicitEscaping) {
  std::unique_ptr<Lexer> lexer =
      std::make_unique<Lexer>("foo:bar\\:baz", Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> tokens,
                             lexer->ExtractTokens());
  EXPECT_THAT(tokens,
              ElementsAre(EqualsLexerToken("foo", Lexer::TokenType::TEXT),
                          EqualsLexerToken(":", Lexer::TokenType::COMPARATOR),
                          EqualsLexerToken("bar:baz", Lexer::TokenType::TEXT)));
}

TEST(LexerTest, QueryShouldRejectTokensBeyondLimit) {
  std::string query;
  for (int i = 0; i < Lexer::kMaxNumTokens + 1; ++i) {
    query.push_back('(');
  }
  Lexer lexer(query, Lexer::Language::QUERY);
  EXPECT_THAT(lexer.ExtractTokens(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(LexerTest, ScoringShouldRejectTokensBeyondLimit) {
  std::string scoring;
  for (int i = 0; i < Lexer::kMaxNumTokens + 1; ++i) {
    scoring.push_back('(');
  }
  Lexer lexer(scoring, Lexer::Language::SCORING);
  EXPECT_THAT(lexer.ExtractTokens(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

}  // namespace lib
}  // namespace icing
