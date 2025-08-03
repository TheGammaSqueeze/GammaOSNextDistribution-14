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

#include "icing/tokenization/rfc822-tokenizer.h"

#include <memory>
#include <string>
#include <string_view>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {
namespace {
using ::testing::ElementsAre;
using ::testing::IsEmpty;

TEST(Rfc822TokenizerTest, StartingState) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string text = "a@g.c";
  auto token_iterator = rfc822_tokenizer.Tokenize(text).ValueOrDie();

  ASSERT_THAT(token_iterator->GetTokens(), IsEmpty());
  ASSERT_TRUE(token_iterator->Advance());
  ASSERT_THAT(token_iterator->GetTokens(), Not(IsEmpty()));
}

TEST(Rfc822TokenizerTest, EmptyMiddleToken) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();

  std::string s("<alex>,,<tom>");

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(s),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "<alex>"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "alex"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "alex"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "alex"),
          EqualsToken(Token::Type::RFC822_TOKEN, "<tom>"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "tom"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "tom"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "tom"))));
}

TEST(Rfc822TokenizerTest, Simple) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();

  std::string_view s("<你alex@google.com>");

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(s),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "<你alex@google.com>"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "你alex"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "你alex@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "你alex"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"))));
}

TEST(Rfc822TokenizerTest, Small) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();

  std::string s = "\"a\"";

  EXPECT_THAT(rfc822_tokenizer.TokenizeAll(s),
              IsOkAndHolds(ElementsAre(
                  EqualsToken(Token::Type::RFC822_TOKEN, "a"),
                  EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "a"),
                  EqualsToken(Token::Type::RFC822_ADDRESS, "a"),
                  EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "a"))));

  s = "\"a\", \"b\"";

  EXPECT_THAT(rfc822_tokenizer.TokenizeAll(s),
              IsOkAndHolds(ElementsAre(
                  EqualsToken(Token::Type::RFC822_TOKEN, "a"),
                  EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "a"),
                  EqualsToken(Token::Type::RFC822_ADDRESS, "a"),
                  EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "a"),
                  EqualsToken(Token::Type::RFC822_TOKEN, "b"),
                  EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "b"),
                  EqualsToken(Token::Type::RFC822_ADDRESS, "b"),
                  EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "b"))));

  s = "(a)";

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(s),
      IsOkAndHolds(ElementsAre(EqualsToken(Token::Type::RFC822_TOKEN, "(a)"),
                               EqualsToken(Token::Type::RFC822_COMMENT, "a"))));
}

TEST(Rfc822TokenizerTest, PB) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();

  std::string_view s("peanut (comment) butter, <alex@google.com>");

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(s),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "peanut"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "peanut"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "peanut"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "peanut"),
          EqualsToken(Token::Type::RFC822_TOKEN, "comment"),
          EqualsToken(Token::Type::RFC822_COMMENT, "comment"),
          EqualsToken(Token::Type::RFC822_TOKEN, "butter"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "butter"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "butter"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "butter"),
          EqualsToken(Token::Type::RFC822_TOKEN, "<alex@google.com>"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "alex"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "alex@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "alex"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"))));
}

TEST(Rfc822TokenizerTest, NoBrackets) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();

  std::string_view s("alex@google.com");

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(s),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "alex@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "alex"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "alex@google.com"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "alex"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"))));
}

TEST(Rfc822TokenizerTest, TwoAddresses) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();

  std::string_view s("<你alex@google.com>; <alexsav@gmail.com>");

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(s),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "<你alex@google.com>"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "你alex"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "你alex@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "你alex"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),
          EqualsToken(Token::Type::RFC822_TOKEN, "<alexsav@gmail.com>"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "alexsav"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "gmail.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "alexsav@gmail.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "alexsav"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "gmail"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"))));
}

TEST(Rfc822TokenizerTest, Comment) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();

  std::string_view s("(a comment) <alex@google.com>");
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(s),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN,
                      "(a comment) <alex@google.com>"),
          EqualsToken(Token::Type::RFC822_COMMENT, "a"),
          EqualsToken(Token::Type::RFC822_COMMENT, "comment"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "alex"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "alex@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "alex"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"))));
}

TEST(Rfc822TokenizerTest, NameAndComment) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();

  std::string_view s("\"a name\" also a name <alex@google.com>");
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(s),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN,
                      "\"a name\" also a name <alex@google.com>"),
          EqualsToken(Token::Type::RFC822_NAME, "a"),
          EqualsToken(Token::Type::RFC822_NAME, "name"),
          EqualsToken(Token::Type::RFC822_NAME, "also"),
          EqualsToken(Token::Type::RFC822_NAME, "a"),
          EqualsToken(Token::Type::RFC822_NAME, "name"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "alex"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "alex@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "alex"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"))));
}

// Test from tokenizer_test.cc.
TEST(Rfc822TokenizerTest, Rfc822SanityCheck) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();

  std::string addr1("A name (A comment) <address@domain.com>");
  std::string addr2(
      "\"(Another name)\" (A different comment) "
      "<bob-loblaw@foo.bar.com>");
  std::string addr3("<no.at.sign.present>");
  std::string addr4("<double@at@signs.present>");
  std::string rfc822 = addr1 + ", " + addr2 + ", " + addr3 + ", " + addr4;
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(rfc822),
      IsOkAndHolds(ElementsAre(

          EqualsToken(Token::Type::RFC822_TOKEN, addr1),
          EqualsToken(Token::Type::RFC822_NAME, "A"),
          EqualsToken(Token::Type::RFC822_NAME, "name"),
          EqualsToken(Token::Type::RFC822_COMMENT, "A"),
          EqualsToken(Token::Type::RFC822_COMMENT, "comment"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "address"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "domain.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "address@domain.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "address"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "domain"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),

          EqualsToken(Token::Type::RFC822_TOKEN, addr2),
          EqualsToken(Token::Type::RFC822_NAME, "Another"),
          EqualsToken(Token::Type::RFC822_NAME, "name"),
          EqualsToken(Token::Type::RFC822_COMMENT, "A"),
          EqualsToken(Token::Type::RFC822_COMMENT, "different"),
          EqualsToken(Token::Type::RFC822_COMMENT, "comment"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "bob-loblaw"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "foo.bar.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "bob-loblaw@foo.bar.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "bob"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "loblaw"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "foo"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "bar"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),

          EqualsToken(Token::Type::RFC822_TOKEN, addr3),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "no.at.sign.present"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "no.at.sign.present"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "no"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "at"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "sign"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "present"),

          EqualsToken(Token::Type::RFC822_TOKEN, addr4),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "double@at"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "signs.present"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "double@at@signs.present"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "double"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "at"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "signs"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "present"))));
}

// Tests from rfc822 converter.
TEST(Rfc822TokenizerTest, SimpleRfcText) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string test_string =
      "foo@google.com,bar@google.com,baz@google.com,foo+hello@google.com,baz@"
      "corp.google.com";

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(test_string),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "foo@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "foo"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "foo@google.com"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "foo"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),

          EqualsToken(Token::Type::RFC822_TOKEN, "bar@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "bar"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "bar@google.com"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "bar"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),

          EqualsToken(Token::Type::RFC822_TOKEN, "baz@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "baz"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "baz@google.com"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "baz"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),

          EqualsToken(Token::Type::RFC822_TOKEN, "foo+hello@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "foo"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "hello"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "foo+hello@google.com"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "foo+hello"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),

          EqualsToken(Token::Type::RFC822_TOKEN, "baz@corp.google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "baz"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "corp"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "baz@corp.google.com"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "baz"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "corp.google.com"))));
}

TEST(Rfc822TokenizerTest, ComplicatedRfcText) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string test_string =
      R"raw("Weird, But&(Also)\\Valid" Name (!With, "an" \\odd\\ cmt too¡) <Foo B(a)r,Baz@g.co>
      <easy@google.com>)raw";

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(test_string),
      IsOkAndHolds(ElementsAre(
          EqualsToken(
              Token::Type::RFC822_TOKEN,
              R"raw("Weird, But&(Also)\\Valid" Name (!With, "an" \\odd\\ cmt too¡) <Foo B(a)r,Baz@g.co>)raw"),
          EqualsToken(Token::Type::RFC822_NAME, "Weird"),
          EqualsToken(Token::Type::RFC822_NAME, "But"),
          EqualsToken(Token::Type::RFC822_NAME, "Also"),
          EqualsToken(Token::Type::RFC822_NAME, "Valid"),
          EqualsToken(Token::Type::RFC822_NAME, "Name"),
          EqualsToken(Token::Type::RFC822_COMMENT, "With"),
          EqualsToken(Token::Type::RFC822_COMMENT, "an"),
          EqualsToken(Token::Type::RFC822_COMMENT, "odd"),
          EqualsToken(Token::Type::RFC822_COMMENT, "cmt"),
          EqualsToken(Token::Type::RFC822_COMMENT, "too"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "Foo B(a)r,Baz"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "g.co"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "Foo B(a)r,Baz@g.co"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "Foo"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "B"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "a"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "r"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "Baz"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "g"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "co"),
          EqualsToken(Token::Type::RFC822_TOKEN, "<easy@google.com>"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "easy"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "easy@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "easy"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"))));
}

TEST(Rfc822TokenizerTest, FromHtmlBugs) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  // This input used to cause HTML parsing exception. We don't do HTML parsing
  // any more (b/8388100) so we are just checking that it does not crash and
  // that it retains the input.

  // http://b/8988210. Put crashing string "&\r" x 100 into name and comment
  // field of rfc822 token.

  std::string s("\"");
  for (int i = 0; i < 100; i++) {
    s.append("&\r");
  }
  s.append("\" (");
  for (int i = 0; i < 100; i++) {
    s.append("&\r");
  }
  s.append(") <foo@google.com>");

  // It shouldn't change anything
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(s),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, s),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "foo"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "foo@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "foo"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"))));
}

TEST(Rfc822TokenizerTest, EmptyComponentsTest) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  EXPECT_THAT(rfc822_tokenizer.TokenizeAll(""),
              IsOkAndHolds(testing::IsEmpty()));

  // Name is considered the address if address is empty.
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll("name<>"),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "name"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "name"))));

  // Empty name and address means that there is no token.
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll("(a long comment with nothing else)"),
      IsOkAndHolds(
          ElementsAre(EqualsToken(Token::Type::RFC822_TOKEN,
                                  "(a long comment with nothing else)"),
                      EqualsToken(Token::Type::RFC822_COMMENT, "a"),
                      EqualsToken(Token::Type::RFC822_COMMENT, "long"),
                      EqualsToken(Token::Type::RFC822_COMMENT, "comment"),
                      EqualsToken(Token::Type::RFC822_COMMENT, "with"),
                      EqualsToken(Token::Type::RFC822_COMMENT, "nothing"),
                      EqualsToken(Token::Type::RFC822_COMMENT, "else"))));

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll("name ()"),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "name"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "name"))));

  EXPECT_THAT(rfc822_tokenizer.TokenizeAll(R"((comment) "")"),
              IsOkAndHolds(ElementsAre(
                  EqualsToken(Token::Type::RFC822_TOKEN, "(comment) \"\""),
                  EqualsToken(Token::Type::RFC822_COMMENT, "comment"))));
}

TEST(Rfc822TokenizerTest, NameTest) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();

  // Name spread between address or comment.
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll("peanut <address> butter"),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "peanut <address> butter"),
          EqualsToken(Token::Type::RFC822_NAME, "peanut"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "address"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "address"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "address"),
          EqualsToken(Token::Type::RFC822_NAME, "butter"))));

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll("peanut (comment) butter"),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "peanut"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "peanut"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "peanut"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "peanut"),
          EqualsToken(Token::Type::RFC822_TOKEN, "comment"),
          EqualsToken(Token::Type::RFC822_COMMENT, "comment"),
          EqualsToken(Token::Type::RFC822_TOKEN, "butter"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "butter"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "butter"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "butter"))));

  // Dropping quotes when they're not needed.
  std::string s = R"(peanut <address> "butter")";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(s),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, s),
          EqualsToken(Token::Type::RFC822_NAME, "peanut"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "address"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "address"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "address"),
          EqualsToken(Token::Type::RFC822_NAME, "butter"))));

  s = R"(peanut "butter")";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(s),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "peanut"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "peanut"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "peanut"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "peanut"),
          EqualsToken(Token::Type::RFC822_TOKEN, "butter"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "butter"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "butter"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "butter"))));
  // Adding quotes when they are needed.
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll("ple@se quote this <addr>"),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "ple@se quote this <addr>"),
          EqualsToken(Token::Type::RFC822_NAME, "ple"),
          EqualsToken(Token::Type::RFC822_NAME, "se"),
          EqualsToken(Token::Type::RFC822_NAME, "quote"),
          EqualsToken(Token::Type::RFC822_NAME, "this"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "addr"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "addr"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "addr"))));
}

TEST(Rfc822TokenizerTest, CommentEscapeTest) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  // '(', ')', '\\' chars should be escaped. All other escaped chars should be
  // unescaped.
  EXPECT_THAT(rfc822_tokenizer.TokenizeAll(R"((co\)mm\\en\(t))"),
              IsOkAndHolds(ElementsAre(
                  EqualsToken(Token::Type::RFC822_TOKEN, R"((co\)mm\\en\(t))"),
                  EqualsToken(Token::Type::RFC822_COMMENT, "co"),
                  EqualsToken(Token::Type::RFC822_COMMENT, "mm"),
                  EqualsToken(Token::Type::RFC822_COMMENT, "en"),
                  EqualsToken(Token::Type::RFC822_COMMENT, "t"))));

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(R"((c\om\ment) name)"),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, R"(c\om\ment)"),
          EqualsToken(Token::Type::RFC822_COMMENT, R"(c\om\ment)"),
          EqualsToken(Token::Type::RFC822_TOKEN, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "name"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "name"))));

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(R"((co(m\))ment) name)"),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, R"(co(m\))ment)"),
          EqualsToken(Token::Type::RFC822_COMMENT, "co"),
          EqualsToken(Token::Type::RFC822_COMMENT, "m"),
          EqualsToken(Token::Type::RFC822_COMMENT, "ment"),
          EqualsToken(Token::Type::RFC822_TOKEN, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "name"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "name"))));
}

TEST(Rfc822TokenizerTest, QuoteEscapeTest) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  // All names that include non-alphanumeric chars must be quoted and have '\\'
  // and '"' chars escaped.
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(R"(n\\a\me <addr>)"),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, R"(n\\a\me <addr>)"),
          EqualsToken(Token::Type::RFC822_NAME, "n"),
          EqualsToken(Token::Type::RFC822_NAME, "a"),
          EqualsToken(Token::Type::RFC822_NAME, "me"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "addr"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "addr"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "addr"))));

  // Names that are within quotes should have all characters blindly unescaped.
  // When a name is made into an address, it isn't re-escaped.
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(R"("n\\a\m\"e")"),
      // <n\am"e>
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, R"(n\\a\m\"e)"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "n"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "a\\m"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "e"),
          EqualsToken(Token::Type::RFC822_ADDRESS, R"(n\\a\m\"e)"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, R"(n\\a\m\"e)"))));
}

TEST(Rfc822TokenizerTest, UnterminatedComponentTest) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll("name (comment"),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "name"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "name"),
          EqualsToken(Token::Type::RFC822_TOKEN, "comment"),
          EqualsToken(Token::Type::RFC822_COMMENT, "comment"))));

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(R"(half of "the name)"),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "half"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "half"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "half"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "half"),
          EqualsToken(Token::Type::RFC822_TOKEN, "of"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "of"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "of"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "of"),
          EqualsToken(Token::Type::RFC822_TOKEN, "the name"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "the"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "the name"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "the name"))));

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(R"("name\)"),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "name"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "name"))));

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(R"(name (comment\)"),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "name"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "name"),
          EqualsToken(Token::Type::RFC822_TOKEN, "comment"),
          EqualsToken(Token::Type::RFC822_COMMENT, "comment"))));

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(R"(<addr> "name\)"),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "<addr> \"name\\"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "addr"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "addr"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "addr"),
          EqualsToken(Token::Type::RFC822_NAME, "name"))));

  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(R"(name (comment\))"),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "name"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "name"),
          EqualsToken(Token::Type::RFC822_TOKEN, "comment"),
          EqualsToken(Token::Type::RFC822_COMMENT, "comment"))));
}

TEST(Rfc822TokenizerTest, Tokenize) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();

  std::string text =
      R"raw("Berg" (home) <berg\@google.com>, tom\@google.com (work))raw";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN,
                      R"("Berg" (home) <berg\@google.com>)"),
          EqualsToken(Token::Type::RFC822_NAME, "Berg"),
          EqualsToken(Token::Type::RFC822_COMMENT, "home"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "berg\\"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "berg\\@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "berg"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),
          EqualsToken(Token::Type::RFC822_TOKEN, "tom\\@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "tom"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "tom\\@google.com"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "tom\\@google.com"),
          EqualsToken(Token::Type::RFC822_TOKEN, "work"),
          EqualsToken(Token::Type::RFC822_COMMENT, "work"))));

  text = R"raw(Foo Bar (something) <foo\@google.com>, )raw"
         R"raw(blah\@google.com (something))raw";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN,
                      "Foo Bar (something) <foo\\@google.com>"),
          EqualsToken(Token::Type::RFC822_NAME, "Foo"),
          EqualsToken(Token::Type::RFC822_NAME, "Bar"),
          EqualsToken(Token::Type::RFC822_COMMENT, "something"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "foo\\"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "foo\\@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "foo"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),
          EqualsToken(Token::Type::RFC822_TOKEN, "blah\\@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "blah"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "blah\\@google.com"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "blah\\@google.com"),
          EqualsToken(Token::Type::RFC822_TOKEN, "something"),
          EqualsToken(Token::Type::RFC822_COMMENT, "something"))));
}

TEST(Rfc822TokenizerTest, EdgeCases) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();

  // Text to trigger the scenario where you have a non-alphabetic followed
  // by a \ followed by non alphabetic to end an in-address token.
  std::string text = R"raw(<be.\&rg@google.com>)raw";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN,
                      R"raw(<be.\&rg@google.com>)raw"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "be.\\&rg"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "be.\\&rg@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "be"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "rg"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"))));

  // A \ followed by an alphabetic shouldn't end the token.
  text = "<a\\lex@google.com>";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "<a\\lex@google.com>"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "a\\lex"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "a\\lex@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "a\\lex"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"))));

  // \\ or \" in a quoted section.
  text = R"("al\\ex@goo\"<idk>gle.com")";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, R"(al\\ex@goo\"<idk>gle.com)"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "al"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "ex"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "goo"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "idk"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "gle"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),
          EqualsToken(Token::Type::RFC822_ADDRESS,
                      R"(al\\ex@goo\"<idk>gle.com)"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "al\\\\ex"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "goo\\\"<idk>gle.com"))));

  text = "<alex@google.com";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "<alex@google.com"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "alex"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "alex@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "alex"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"))));
}

TEST(Rfc822TokenizerTest, NumberInAddress) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string text = "<3alex@google.com>";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "<3alex@google.com>"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "3alex"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "3alex@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "3alex"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"))));
}

TEST(Rfc822TokenizerTest, DoubleQuoteDoubleSlash) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string text = R"("alex\"")";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "alex"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "alex"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "alex"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "alex"))));

  text = R"("alex\\\a")";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, R"(alex\\\a)"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "alex"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "a"),
          EqualsToken(Token::Type::RFC822_ADDRESS, R"(alex\\\a)"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, R"(alex\\\a)"))));
}

TEST(Rfc822TokenizerTest, TwoEmails) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string text = "tjbarron@google.com alexsav@google.com";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "tjbarron@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "tjbarron"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "tjbarron@google.com"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "tjbarron"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"),
          EqualsToken(Token::Type::RFC822_TOKEN, "alexsav@google.com"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "alexsav"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "alexsav@google.com"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "alexsav"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"))));
}

TEST(Rfc822TokenizerTest, BackSlashes) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string text = R"("\name")";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "name"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "name"))));

  text = R"("name@foo\@gmail")";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "name@foo\\@gmail"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "name"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "foo"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "gmail"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "name@foo\\@gmail"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "name"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "foo\\@gmail"))));
}

TEST(Rfc822TokenizerTest, BigWhitespace) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string text = "\"quoted\"              <address>";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, text),
          EqualsToken(Token::Type::RFC822_NAME, "quoted"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "address"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "address"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "address"))));
}

TEST(Rfc822TokenizerTest, AtSignFirst) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string text = "\"@foo\"";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "foo"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "foo"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "foo"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "foo"))));
}

TEST(Rfc822TokenizerTest, SlashThenUnicode) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string text = R"("quoted\你cjk")";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "quoted\\你cjk"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST,
                      "quoted\\你cjk"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "quoted\\你cjk"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "quoted\\你cjk"))));
}

TEST(Rfc822TokenizerTest, AddressEmptyAddress) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string text = "<address> <> Name";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, text),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "address"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "address"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "address"),
          EqualsToken(Token::Type::RFC822_NAME, "Name"))));
}

TEST(Rfc822TokenizerTest, ProperComment) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string text = "(comment)alex@google.com";
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "comment)alex@google.com"),
          EqualsToken(Token::Type::RFC822_COMMENT, "comment"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "alex"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "google"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "com"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "alex@google.com"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "alex"),
          EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "google.com"))));
}

TEST(Rfc822TokenizerTest, SmallNameToEmail) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string text = "a@g.c,b@g.c";
  EXPECT_THAT(rfc822_tokenizer.TokenizeAll(text),
              IsOkAndHolds(ElementsAre(
                  EqualsToken(Token::Type::RFC822_TOKEN, "a@g.c"),
                  EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "a"),
                  EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "g"),
                  EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "c"),
                  EqualsToken(Token::Type::RFC822_ADDRESS, "a@g.c"),
                  EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "a"),
                  EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "g.c"),
                  EqualsToken(Token::Type::RFC822_TOKEN, "b@g.c"),
                  EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "b"),
                  EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "g"),
                  EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "c"),
                  EqualsToken(Token::Type::RFC822_ADDRESS, "b@g.c"),
                  EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "b"),
                  EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "g.c"))));

  text = "a\\\\@g.c";
  EXPECT_THAT(rfc822_tokenizer.TokenizeAll(text),
              IsOkAndHolds(ElementsAre(
                  EqualsToken(Token::Type::RFC822_TOKEN, "a\\\\@g.c"),
                  EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "a"),
                  EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "g"),
                  EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_HOST, "c"),
                  EqualsToken(Token::Type::RFC822_ADDRESS, "a\\\\@g.c"),
                  EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "a"),
                  EqualsToken(Token::Type::RFC822_HOST_ADDRESS, "g.c"))));
}

TEST(Rfc822TokenizerTest, AtSignLast) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string_view text("<alex@>, tim@");
  EXPECT_THAT(
      rfc822_tokenizer.TokenizeAll(text),
      IsOkAndHolds(ElementsAre(
          EqualsToken(Token::Type::RFC822_TOKEN, "<alex@>"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "alex"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "alex@"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "alex"),
          EqualsToken(Token::Type::RFC822_TOKEN, "tim"),
          EqualsToken(Token::Type::RFC822_ADDRESS_COMPONENT_LOCAL, "tim"),
          EqualsToken(Token::Type::RFC822_ADDRESS, "tim"),
          EqualsToken(Token::Type::RFC822_LOCAL_ADDRESS, "tim"))));
}

TEST(Rfc822TokenizerTest, Commas) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string text = ",,,,,,,,,,,,,,,,,,,,,,,,,,;";
  EXPECT_THAT(rfc822_tokenizer.TokenizeAll(text), IsOkAndHolds(IsEmpty()));
}

TEST(Rfc822TokenizerTest, ResetToTokenStartingAfter) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string text = "a@g.c,b@g.c";
  auto token_iterator = rfc822_tokenizer.Tokenize(text).ValueOrDie();
  ASSERT_TRUE(token_iterator->Advance());
  ASSERT_TRUE(token_iterator->Advance());

  ASSERT_TRUE(token_iterator->ResetToTokenStartingAfter(-1));
  EXPECT_THAT(token_iterator->GetTokens().at(0).text, "a@g.c");

  ASSERT_TRUE(token_iterator->ResetToTokenStartingAfter(5));
  EXPECT_THAT(token_iterator->GetTokens().at(0).text, "b@g.c");

  ASSERT_FALSE(token_iterator->ResetToTokenStartingAfter(6));
}

TEST(Rfc822TokenizerTest, ResetToTokenEndingBefore) {
  Rfc822Tokenizer rfc822_tokenizer = Rfc822Tokenizer();
  std::string text = "a@g.c,b@g.c";
  auto token_iterator = rfc822_tokenizer.Tokenize(text).ValueOrDie();
  token_iterator->Advance();

  ASSERT_TRUE(token_iterator->ResetToTokenEndingBefore(5));
  EXPECT_THAT(token_iterator->GetTokens().at(0).text, "a@g.c");

  ASSERT_FALSE(token_iterator->ResetToTokenEndingBefore(4));
}

}  // namespace
}  // namespace lib
}  // namespace icing
