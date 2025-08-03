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

#include "icing/query/advanced_query_parser/parser.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/query/advanced_query_parser/abstract-syntax-tree-test-utils.h"
#include "icing/query/advanced_query_parser/abstract-syntax-tree.h"
#include "icing/query/advanced_query_parser/lexer.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::ElementsAreArray;
using ::testing::IsNull;

TEST(ParserTest, EmptyQuery) {
  std::vector<Lexer::LexerToken> lexer_tokens;
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());
  EXPECT_THAT(tree_root, IsNull());
}

TEST(ParserTest, EmptyScoring) {
  std::vector<Lexer::LexerToken> lexer_tokens;
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeScoring(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserTest, SingleTerm) {
  std::string_view query = "foo";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query, Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  // member
  //   |
  //  text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { text, member }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("foo", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember)));
}

TEST(ParserTest, ImplicitAnd) {
  std::string_view query = "foo bar";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::TEXT},
      {"bar", query.substr(4, 3), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //      AND
  //     /   \
  // member  member
  //   |       |
  //  text    text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { text, member, text, member, AND }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("foo", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("bar", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("AND", NodeType::kNaryOperator)));
}

TEST(ParserTest, Or) {
  std::string_view query = "foo OR bar";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::TEXT},
      {"", query.substr(4, 2), Lexer::TokenType::OR},
      {"bar", query.substr(7, 3), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //      OR
  //     /   \
  // member  member
  //   |       |
  //  text    text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { text, member, text, member, OR }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("foo", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("bar", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("OR", NodeType::kNaryOperator)));
}

TEST(ParserTest, And) {
  std::string_view query = "foo AND bar";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::TEXT},
      {"", query.substr(4, 3), Lexer::TokenType::AND},
      {"bar", query.substr(8, 4), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //      AND
  //     /   \
  // member  member
  //   |       |
  //  text    text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { text, member, text, member, AND }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("foo", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("bar", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("AND", NodeType::kNaryOperator)));
}

TEST(ParserTest, Not) {
  std::string_view query = "NOT foo";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"", query.substr(0, 3), Lexer::TokenType::NOT},
      {"foo", query.substr(4, 3), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //  NOT
  //   |
  // member
  //   |
  //  text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { text, member, NOT }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("foo", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("NOT", NodeType::kUnaryOperator)));
}

TEST(ParserTest, Minus) {
  std::string_view query = "-foo";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"", query.substr(0, 1), Lexer::TokenType::MINUS},
      {"foo", query.substr(1, 3), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //  MINUS
  //   |
  // member
  //   |
  //  text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { text, member, MINUS }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("foo", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("MINUS", NodeType::kUnaryOperator)));
}

TEST(ParserTest, Has) {
  std::string_view query = "subject:foo";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"subject", query.substr(0, 7), Lexer::TokenType::TEXT},
      {":", query.substr(7, 1), Lexer::TokenType::COMPARATOR},
      {"foo", query.substr(8, 3), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //        :
  //      /   \
  // member  member
  //   |       |
  //  text    text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { text, member, text, member, binaryOp }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("subject", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("foo", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo(":", NodeType::kNaryOperator)));
}

TEST(ParserTest, HasNested) {
  std::string_view query = "sender.name:foo";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"sender", query.substr(0, 6), Lexer::TokenType::TEXT},
      {"", query.substr(6, 1), Lexer::TokenType::DOT},
      {"name", query.substr(7, 4), Lexer::TokenType::TEXT},
      {":", query.substr(11, 1), Lexer::TokenType::COMPARATOR},
      {"foo", query.substr(12, 3), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //        :
  //      /   \
  //   member  member
  //   /   \     |
  // text text  text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { text, text, member, text, member, binaryOp }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("sender", NodeType::kText),
                          EqualsNodeInfo("name", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("foo", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo(":", NodeType::kNaryOperator)));
}

TEST(ParserTest, EmptyFunction) {
  std::string_view query = "foo()";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", query.substr(3, 1), Lexer::TokenType::LPAREN},
      {"", query.substr(4, 1), Lexer::TokenType::RPAREN}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //    function
  //       |
  // function_name
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { function_name, function }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("foo", NodeType::kFunctionName),
                          EqualsNodeInfo("", NodeType::kFunction)));
}

TEST(ParserTest, FunctionSingleArg) {
  std::string_view query = "foo(\"bar\")";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", query.substr(3, 1), Lexer::TokenType::LPAREN},
      {"bar", query.substr(5, 3), Lexer::TokenType::STRING},
      {"", query.substr(8, 1), Lexer::TokenType::RPAREN}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //           function
  //           /     \
  // function_name  string
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { function_name, string, function }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("foo", NodeType::kFunctionName),
                          EqualsNodeInfo("bar", NodeType::kString),
                          EqualsNodeInfo("", NodeType::kFunction)));
}

TEST(ParserTest, FunctionMultiArg) {
  std::string_view query = "foo(\"bar\", \"baz\")";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", query.substr(3, 1), Lexer::TokenType::LPAREN},
      {"bar", query.substr(5, 3), Lexer::TokenType::STRING},
      {"", query.substr(9, 1), Lexer::TokenType::COMMA},
      {"baz", query.substr(12, 3), Lexer::TokenType::STRING},
      {"", query.substr(16, 1), Lexer::TokenType::RPAREN}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //                function
  //              /    |    \
  // function_name  string  string
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { function_name, string, string, function }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("foo", NodeType::kFunctionName),
                          EqualsNodeInfo("bar", NodeType::kString),
                          EqualsNodeInfo("baz", NodeType::kString),
                          EqualsNodeInfo("", NodeType::kFunction)));
}

TEST(ParserTest, FunctionNested) {
  std::string_view query = "foo(bar())";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", query.substr(3, 1), Lexer::TokenType::LPAREN},
      {"bar", query.substr(4, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", query.substr(7, 1), Lexer::TokenType::LPAREN},
      {"", query.substr(8, 1), Lexer::TokenType::RPAREN},
      {"", query.substr(9, 1), Lexer::TokenType::RPAREN}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //          function
  //          /      \
  // function_name  function
  //                    |
  //              function_name
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { function_name, function_name, function, function }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("foo", NodeType::kFunctionName),
                          EqualsNodeInfo("bar", NodeType::kFunctionName),
                          EqualsNodeInfo("", NodeType::kFunction),
                          EqualsNodeInfo("", NodeType::kFunction)));
}

TEST(ParserTest, FunctionWithTrailingSequence) {
  std::string_view query = "foo() OR bar";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", query.substr(3, 1), Lexer::TokenType::LPAREN},
      {"", query.substr(4, 1), Lexer::TokenType::RPAREN},
      {"", query.substr(6, 2), Lexer::TokenType::OR},
      {"bar", query.substr(9, 3), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //             OR
  //          /      \
  //     function   member
  //        |         |
  //  function_name  text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { function_name, function, text, member, OR }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("foo", NodeType::kFunctionName),
                          EqualsNodeInfo("", NodeType::kFunction),
                          EqualsNodeInfo("bar", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("OR", NodeType::kNaryOperator)));
}

TEST(ParserTest, Composite) {
  std::string_view query = "foo OR (bar baz)";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::TEXT},
      {"", query.substr(4, 2), Lexer::TokenType::OR},
      {"", query.substr(7, 1), Lexer::TokenType::LPAREN},
      {"bar", query.substr(8, 3), Lexer::TokenType::TEXT},
      {"baz", query.substr(12, 3), Lexer::TokenType::TEXT},
      {"", query.substr(15, 1), Lexer::TokenType::RPAREN}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //             OR
  //          /      \
  //     member      AND
  //       |        /   \
  //     text    member member
  //                |     |
  //              text   text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { text, member, text, member, text, member, AND, OR }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("foo", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("bar", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("baz", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("AND", NodeType::kNaryOperator),
                          EqualsNodeInfo("OR", NodeType::kNaryOperator)));
}

TEST(ParserTest, CompositeWithTrailingSequence) {
  std::string_view query = "(bar baz) OR foo";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"", query.substr(0, 1), Lexer::TokenType::LPAREN},
      {"bar", query.substr(1, 3), Lexer::TokenType::TEXT},
      {"baz", query.substr(5, 3), Lexer::TokenType::TEXT},
      {"", query.substr(8, 1), Lexer::TokenType::RPAREN},
      {"", query.substr(10, 2), Lexer::TokenType::OR},
      {"foo", query.substr(13, 3), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //             OR
  //          /      \
  //       AND      member
  //      /   \       |
  //  member member  text
  //    |     |
  //  text   text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { text, member, text, member, AND, text, member, OR }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("bar", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("baz", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("AND", NodeType::kNaryOperator),
                          EqualsNodeInfo("foo", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("OR", NodeType::kNaryOperator)));
}

TEST(ParserTest, Complex) {
  std::string_view query = R"(foo bar:baz OR pal("bat"))";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::TEXT},
      {"bar", query.substr(4, 3), Lexer::TokenType::TEXT},
      {":", query.substr(7, 1), Lexer::TokenType::COMPARATOR},
      {"baz", query.substr(8, 3), Lexer::TokenType::TEXT},
      {"", query.substr(12, 2), Lexer::TokenType::OR},
      {"pal", query.substr(15, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", query.substr(18, 1), Lexer::TokenType::LPAREN},
      {"bat", query.substr(20, 3), Lexer::TokenType::STRING},
      {"", query.substr(24, 1), Lexer::TokenType::RPAREN}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //               AND
  //            /        \
  //     member            OR
  //       |          /         \
  //     text      :             function
  //              / \            /       \
  //         member member  function_name string
  //           |       |
  //          text    text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  // SimpleVisitor ordering
  //   { text, member, text, member, text, member, :, function_name, string,
  //     function, OR, AND }
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("foo", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("bar", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("baz", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo(":", NodeType::kNaryOperator),
                          EqualsNodeInfo("pal", NodeType::kFunctionName),
                          EqualsNodeInfo("bat", NodeType::kString),
                          EqualsNodeInfo("", NodeType::kFunction),
                          EqualsNodeInfo("OR", NodeType::kNaryOperator),
                          EqualsNodeInfo("AND", NodeType::kNaryOperator)));
}

TEST(ParserTest, InvalidHas) {
  std::string_view query = "foo:";  // No right hand operand to :
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::TEXT},
      {":", query.substr(3, 1), Lexer::TokenType::COMPARATOR}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserTest, InvalidComposite) {
  std::string_view query = "(foo bar";  // No terminating RPAREN
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"", query.substr(0, 1), Lexer::TokenType::LPAREN},
      {"foo", query.substr(1, 3), Lexer::TokenType::TEXT},
      {"bar", query.substr(5, 3), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserTest, InvalidMember) {
  std::string_view query = "foo.";  // DOT must have succeeding TEXT
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::TEXT},
      {"", query.substr(3, 1), Lexer::TokenType::DOT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserTest, InvalidOr) {
  std::string_view query = "foo OR";  // No right hand operand to OR
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::TEXT},
      {"", query.substr(3, 2), Lexer::TokenType::OR}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserTest, InvalidAnd) {
  std::string_view query = "foo AND";  // No right hand operand to AND
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::TEXT},
      {"", query.substr(4, 3), Lexer::TokenType::AND}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserTest, InvalidNot) {
  std::string_view query = "NOT";  // No right hand operand to NOT
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"", query.substr(0, 3), Lexer::TokenType::NOT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserTest, InvalidMinus) {
  std::string_view query = "-";  // No right hand operand to -
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"", query.substr(0, 1), Lexer::TokenType::MINUS}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserTest, InvalidFunctionCallNoRparen) {
  std::string_view query = "foo(";  // No terminating RPAREN
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", query.substr(3, 0), Lexer::TokenType::LPAREN}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserTest, InvalidFunctionCallNoLparen) {
  std::string_view query =
      "foo bar";  // foo labeled FUNCTION_NAME despite no LPAREN
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::FUNCTION_NAME},
      {"bar", query.substr(4, 3), Lexer::TokenType::FUNCTION_NAME}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserTest, InvalidFunctionArgsHangingComma) {
  std::string_view query = R"(foo("bar",))";  // no valid arg following COMMA
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"foo", query.substr(0, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", query.substr(3, 1), Lexer::TokenType::LPAREN},
      {"bar", query.substr(5, 3), Lexer::TokenType::STRING},
      {"", query.substr(9, 1), Lexer::TokenType::COMMA},
      {"", query.substr(10, 1), Lexer::TokenType::RPAREN}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserTest, ScoringPlus) {
  std::string_view scoring_exp = "1 + 1 + 1";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"1", scoring_exp.substr(0, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(2, 1), Lexer::TokenType::PLUS},
      {"1", scoring_exp.substr(4, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(6, 1), Lexer::TokenType::PLUS},
      {"1", scoring_exp.substr(8, 1), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeScoring());

  // Expected AST:
  //           PLUS
  //     /      |       \
  // member   member   member
  //   |        |        |
  //  text     text     text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("PLUS", NodeType::kNaryOperator)));
}

TEST(ParserTest, ScoringMinus) {
  std::string_view scoring_exp = "1 - 1 - 1";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"1", scoring_exp.substr(0, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(2, 1), Lexer::TokenType::MINUS},
      {"1", scoring_exp.substr(4, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(6, 1), Lexer::TokenType::MINUS},
      {"1", scoring_exp.substr(8, 1), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeScoring());

  // Expected AST:
  //          MINUS
  //     /      |       \
  // member   member   member
  //   |        |        |
  //  text     text     text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("MINUS", NodeType::kNaryOperator)));
}

TEST(ParserTest, ScoringUnaryMinus) {
  std::string_view scoring_exp = "1 + -1 + 1";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"1", scoring_exp.substr(0, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(2, 1), Lexer::TokenType::PLUS},
      {"", scoring_exp.substr(4, 1), Lexer::TokenType::MINUS},
      {"1", scoring_exp.substr(5, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(7, 1), Lexer::TokenType::PLUS},
      {"1", scoring_exp.substr(9, 1), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeScoring());

  // Expected AST:
  //          PLUS
  //     /      |      \
  // member   MINUS   member
  //   |        |        |
  //  text    member    text
  //            |
  //           text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("MINUS", NodeType::kUnaryOperator),
                          EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("PLUS", NodeType::kNaryOperator)));
}

TEST(ParserTest, ScoringPlusMinus) {
  std::string_view scoring_exp = "11 + 12 - 13 + 14";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"11", scoring_exp.substr(0, 2), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(3, 1), Lexer::TokenType::PLUS},
      {"12", scoring_exp.substr(5, 2), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(6, 1), Lexer::TokenType::MINUS},
      {"13", scoring_exp.substr(8, 2), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(11, 1), Lexer::TokenType::PLUS},
      {"14", scoring_exp.substr(13, 2), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeScoring());

  // Expected AST:
  //                          PLUS
  //                       /        \
  //                MINUS          member
  //             /         \          |
  //       PLUS           member     text
  //     /      \            |
  // member    member       text
  //   |         |
  //  text      text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("11", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("12", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("PLUS", NodeType::kNaryOperator),
                          EqualsNodeInfo("13", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("MINUS", NodeType::kNaryOperator),
                          EqualsNodeInfo("14", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("PLUS", NodeType::kNaryOperator)));
}

TEST(ParserTest, ScoringTimes) {
  std::string_view scoring_exp = "1 * 1 * 1";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"1", scoring_exp.substr(0, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(2, 1), Lexer::TokenType::TIMES},
      {"1", scoring_exp.substr(4, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(6, 1), Lexer::TokenType::TIMES},
      {"1", scoring_exp.substr(8, 1), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeScoring());

  // Expected AST:
  //           TIMES
  //     /      |       \
  // member   member   member
  //   |        |        |
  //  text     text     text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("TIMES", NodeType::kNaryOperator)));
}

TEST(ParserTest, ScoringDiv) {
  std::string_view scoring_exp = "1 / 1 / 1";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"1", scoring_exp.substr(0, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(2, 1), Lexer::TokenType::DIV},
      {"1", scoring_exp.substr(4, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(6, 1), Lexer::TokenType::DIV},
      {"1", scoring_exp.substr(8, 1), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeScoring());

  // Expected AST:
  //           DIV
  //     /      |       \
  // member   member   member
  //   |        |        |
  //  text     text     text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("DIV", NodeType::kNaryOperator)));
}

TEST(ParserTest, ScoringTimesDiv) {
  std::string_view scoring_exp = "11 / 12 * 13 / 14 / 15";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"11", scoring_exp.substr(0, 2), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(3, 1), Lexer::TokenType::DIV},
      {"12", scoring_exp.substr(5, 2), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(8, 1), Lexer::TokenType::TIMES},
      {"13", scoring_exp.substr(10, 2), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(13, 1), Lexer::TokenType::DIV},
      {"14", scoring_exp.substr(15, 2), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(18, 1), Lexer::TokenType::DIV},
      {"15", scoring_exp.substr(20, 2), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeScoring());

  // Expected AST:
  //                                DIV
  //                        /        |         \
  //                 TIMES          member    member
  //             /          \        |           |
  //        DIV           member    text        text
  //     /      \            |
  // member    member       text
  //   |         |
  //  text      text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("11", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("12", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("DIV", NodeType::kNaryOperator),
                          EqualsNodeInfo("13", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("TIMES", NodeType::kNaryOperator),
                          EqualsNodeInfo("14", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("15", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("DIV", NodeType::kNaryOperator)));
}

TEST(ParserTest, ComplexScoring) {
  std::string_view scoring_exp = "1 + pow((2 * sin(3)), 4) + -5 / 6";
  // With parentheses in function arguments.
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"1", scoring_exp.substr(0, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(2, 1), Lexer::TokenType::PLUS},
      {"pow", scoring_exp.substr(4, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", scoring_exp.substr(7, 1), Lexer::TokenType::LPAREN},
      {"", scoring_exp.substr(8, 1), Lexer::TokenType::LPAREN},
      {"2", scoring_exp.substr(9, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(11, 1), Lexer::TokenType::TIMES},
      {"sin", scoring_exp.substr(13, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", scoring_exp.substr(16, 1), Lexer::TokenType::LPAREN},
      {"3", scoring_exp.substr(17, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(18, 1), Lexer::TokenType::RPAREN},
      {"", scoring_exp.substr(19, 1), Lexer::TokenType::RPAREN},
      {"", scoring_exp.substr(20, 1), Lexer::TokenType::COMMA},
      {"4", scoring_exp.substr(22, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(23, 1), Lexer::TokenType::RPAREN},
      {"", scoring_exp.substr(25, 1), Lexer::TokenType::PLUS},
      {"", scoring_exp.substr(27, 1), Lexer::TokenType::MINUS},
      {"5", scoring_exp.substr(28, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(30, 1), Lexer::TokenType::DIV},
      {"6", scoring_exp.substr(32, 1), Lexer::TokenType::TEXT},
  };
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeScoring());
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  std::vector<NodeInfo> node = visitor.nodes();
  EXPECT_THAT(node,
              ElementsAre(EqualsNodeInfo("1", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("pow", NodeType::kFunctionName),
                          EqualsNodeInfo("2", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("sin", NodeType::kFunctionName),
                          EqualsNodeInfo("3", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("", NodeType::kFunction),
                          EqualsNodeInfo("TIMES", NodeType::kNaryOperator),
                          EqualsNodeInfo("4", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("", NodeType::kFunction),
                          EqualsNodeInfo("5", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("MINUS", NodeType::kUnaryOperator),
                          EqualsNodeInfo("6", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("DIV", NodeType::kNaryOperator),
                          EqualsNodeInfo("PLUS", NodeType::kNaryOperator)));

  scoring_exp = "1 + pow(2 * sin(3), 4) + -5 / 6";
  // Without parentheses in function arguments.
  lexer_tokens = {
      {"1", scoring_exp.substr(0, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(2, 1), Lexer::TokenType::PLUS},
      {"pow", scoring_exp.substr(4, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", scoring_exp.substr(7, 1), Lexer::TokenType::LPAREN},
      {"2", scoring_exp.substr(8, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(10, 1), Lexer::TokenType::TIMES},
      {"sin", scoring_exp.substr(12, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", scoring_exp.substr(15, 1), Lexer::TokenType::LPAREN},
      {"3", scoring_exp.substr(16, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(17, 1), Lexer::TokenType::RPAREN},
      {"", scoring_exp.substr(18, 1), Lexer::TokenType::COMMA},
      {"4", scoring_exp.substr(20, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(21, 1), Lexer::TokenType::RPAREN},
      {"", scoring_exp.substr(23, 1), Lexer::TokenType::PLUS},
      {"", scoring_exp.substr(25, 1), Lexer::TokenType::MINUS},
      {"5", scoring_exp.substr(26, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(28, 1), Lexer::TokenType::DIV},
      {"6", scoring_exp.substr(30, 1), Lexer::TokenType::TEXT},
  };
  parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(tree_root, parser.ConsumeScoring());
  visitor = SimpleVisitor();
  tree_root->Accept(&visitor);
  EXPECT_THAT(visitor.nodes(), ElementsAreArray(node));
}

TEST(ParserTest, ScoringMemberFunction) {
  std::string_view scoring_exp = "this.CreationTimestamp()";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"this", scoring_exp.substr(0, 4), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(4, 1), Lexer::TokenType::DOT},
      {"CreationTimestamp", scoring_exp.substr(5, 17),
       Lexer::TokenType::FUNCTION_NAME},
      {"", scoring_exp.substr(22, 1), Lexer::TokenType::LPAREN},
      {"", scoring_exp.substr(23, 1), Lexer::TokenType::RPAREN}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeScoring());

  // Expected AST:
  //       member
  //     /        \
  //  text     function
  //               |
  //          function_name
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  EXPECT_THAT(
      visitor.nodes(),
      ElementsAre(EqualsNodeInfo("this", NodeType::kText),
                  EqualsNodeInfo("CreationTimestamp", NodeType::kFunctionName),
                  EqualsNodeInfo("", NodeType::kFunction),
                  EqualsNodeInfo("", NodeType::kMember)));
}

TEST(ParserTest, QueryMemberFunction) {
  std::string_view query = "this.foo()";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"this", query.substr(0, 4), Lexer::TokenType::TEXT},
      {"", query.substr(4, 1), Lexer::TokenType::DOT},
      {"foo", query.substr(5, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", query.substr(8, 1), Lexer::TokenType::LPAREN},
      {"", query.substr(9, 1), Lexer::TokenType::RPAREN}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //       member
  //     /        \
  //  text     function
  //               |
  //          function_name
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("this", NodeType::kText),
                          EqualsNodeInfo("foo", NodeType::kFunctionName),
                          EqualsNodeInfo("", NodeType::kFunction),
                          EqualsNodeInfo("", NodeType::kMember)));
}

TEST(ParserTest, ScoringComplexMemberFunction) {
  std::string_view scoring_exp = "a.b.fun(c, d)";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"a", scoring_exp.substr(0, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(1, 1), Lexer::TokenType::DOT},
      {"b", scoring_exp.substr(2, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(3, 1), Lexer::TokenType::DOT},
      {"fun", scoring_exp.substr(4, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", scoring_exp.substr(7, 1), Lexer::TokenType::LPAREN},
      {"c", scoring_exp.substr(8, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(9, 1), Lexer::TokenType::COMMA},
      {"d", scoring_exp.substr(11, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(12, 1), Lexer::TokenType::RPAREN}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeScoring());

  // Expected AST:
  //                member
  //         /        |          \
  //  text          text         function
  //                        /        |       \
  //               function_name   member    member
  //                                 |         |
  //                                text      text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("a", NodeType::kText),
                          EqualsNodeInfo("b", NodeType::kText),
                          EqualsNodeInfo("fun", NodeType::kFunctionName),
                          EqualsNodeInfo("c", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("d", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("", NodeType::kFunction),
                          EqualsNodeInfo("", NodeType::kMember)));
}

TEST(ParserTest, QueryComplexMemberFunction) {
  std::string_view query = "this.abc.fun(def, ghi)";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"this", query.substr(0, 4), Lexer::TokenType::TEXT},
      {"", query.substr(4, 1), Lexer::TokenType::DOT},
      {"abc", query.substr(5, 3), Lexer::TokenType::TEXT},
      {"", query.substr(8, 1), Lexer::TokenType::DOT},
      {"fun", query.substr(9, 3), Lexer::TokenType::FUNCTION_NAME},
      {"", query.substr(12, 1), Lexer::TokenType::LPAREN},
      {"def", query.substr(13, 3), Lexer::TokenType::TEXT},
      {"", query.substr(16, 1), Lexer::TokenType::COMMA},
      {"ghi", query.substr(17, 3), Lexer::TokenType::TEXT},
      {"", query.substr(20, 1), Lexer::TokenType::RPAREN}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());

  // Expected AST:
  //                member
  //         /        |          \
  //  text          text         function
  //                        /        |       \
  //               function_name   member    member
  //                                 |         |
  //                                text      text
  SimpleVisitor visitor;
  tree_root->Accept(&visitor);
  EXPECT_THAT(visitor.nodes(),
              ElementsAre(EqualsNodeInfo("this", NodeType::kText),
                          EqualsNodeInfo("abc", NodeType::kText),
                          EqualsNodeInfo("fun", NodeType::kFunctionName),
                          EqualsNodeInfo("def", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("ghi", NodeType::kText),
                          EqualsNodeInfo("", NodeType::kMember),
                          EqualsNodeInfo("", NodeType::kFunction),
                          EqualsNodeInfo("", NodeType::kMember)));
}

TEST(ParserTest, InvalidScoringToken) {
  std::string_view scoring_exp = "1 + NOT 1";
  std::vector<Lexer::LexerToken> lexer_tokens = {
      {"1", scoring_exp.substr(0, 1), Lexer::TokenType::TEXT},
      {"", scoring_exp.substr(2, 1), Lexer::TokenType::PLUS},
      {"", scoring_exp.substr(4, 3), Lexer::TokenType::NOT},
      {"1", scoring_exp.substr(8, 1), Lexer::TokenType::TEXT}};
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeScoring(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

}  // namespace

}  // namespace lib
}  // namespace icing
