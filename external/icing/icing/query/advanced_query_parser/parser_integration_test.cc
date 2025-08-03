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

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/query/advanced_query_parser/abstract-syntax-tree-test-utils.h"
#include "icing/query/advanced_query_parser/abstract-syntax-tree.h"
#include "icing/query/advanced_query_parser/lexer.h"
#include "icing/query/advanced_query_parser/parser.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::ElementsAreArray;
using ::testing::IsNull;
using ::testing::SizeIs;

TEST(ParserIntegrationTest, EmptyQuery) {
  std::string query = "";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
  Parser parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Node> tree_root,
                             parser.ConsumeQuery());
  EXPECT_THAT(tree_root, IsNull());
}

TEST(ParserIntegrationTest, EmptyScoring) {
  std::string query = "";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeScoring(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserIntegrationTest, SingleTerm) {
  std::string query = "foo";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, ImplicitAnd) {
  std::string query = "foo bar";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, Or) {
  std::string query = "foo OR bar";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, And) {
  std::string query = "foo AND bar";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, Not) {
  std::string query = "NOT foo";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, Minus) {
  std::string query = "-foo";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, Has) {
  std::string query = "subject:foo";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, HasNested) {
  std::string query = "sender.name:foo";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, EmptyFunction) {
  std::string query = "foo()";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, FunctionSingleArg) {
  std::string query = "foo(\"bar\")";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, FunctionMultiArg) {
  std::string query = "foo(\"bar\", \"baz\")";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, FunctionNested) {
  std::string query = "foo(bar())";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, FunctionWithTrailingSequence) {
  std::string query = "foo() OR bar";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, Composite) {
  std::string query = "foo OR (bar baz)";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, CompositeWithTrailingSequence) {
  std::string query = "(bar baz) OR foo";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, Complex) {
  std::string query = "foo bar:baz OR pal(\"bat\")";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, InvalidHas) {
  std::string query = "foo:";  //  No right hand operand to :
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserIntegrationTest, InvalidComposite) {
  std::string query = "(foo bar";  // No terminating RPAREN
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserIntegrationTest, InvalidMember) {
  std::string query = "foo.";  // DOT must have succeeding TEXT
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserIntegrationTest, InvalidOr) {
  std::string query = "foo OR";  // No right hand operand to OR
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserIntegrationTest, InvalidAnd) {
  std::string query = "foo AND";  // No right hand operand to AND
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserIntegrationTest, InvalidNot) {
  std::string query = "NOT";  // No right hand operand to NOT
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserIntegrationTest, InvalidMinus) {
  std::string query = "-";  // No right hand operand to -
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserIntegrationTest, InvalidFunctionCallNoRparen) {
  std::string query = "foo(";  // No terminating RPAREN
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserIntegrationTest, InvalidFunctionArgsHangingComma) {
  std::string query = "foo(\"bar\",)";  // no valid arg following COMMA
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserIntegrationTest, ScoringPlus) {
  std::string scoring = "1 + 1 + 1";
  Lexer lexer(scoring, Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, ScoringMinus) {
  std::string scoring = "1 - 1 - 1";
  Lexer lexer(scoring, Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, ScoringUnaryMinus) {
  std::string scoring = "1 + -1 + 1";
  Lexer lexer(scoring, Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, ScoringPlusMinus) {
  std::string scoring = "11 + 12 - 13 + 14";
  Lexer lexer(scoring, Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, ScoringTimes) {
  std::string scoring = "1 * 1 * 1";
  Lexer lexer(scoring, Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, ScoringDiv) {
  std::string scoring = "1 / 1 / 1";
  Lexer lexer(scoring, Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, ScoringTimesDiv) {
  std::string scoring = "11 / 12 * 13 / 14 / 15";
  Lexer lexer(scoring, Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, ComplexScoring) {
  // With parentheses in function arguments.
  std::string scoring = "1 + pow((2 * sin(3)), 4) + -5 / 6";
  Lexer lexer(scoring, Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

  // Without parentheses in function arguments.
  scoring = "1 + pow(2 * sin(3), 4) + -5 / 6";
  lexer = Lexer(scoring, Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(lexer_tokens, lexer.ExtractTokens());
  parser = Parser::Create(std::move(lexer_tokens));
  ICING_ASSERT_OK_AND_ASSIGN(tree_root, parser.ConsumeScoring());
  visitor = SimpleVisitor();
  tree_root->Accept(&visitor);
  EXPECT_THAT(visitor.nodes(), ElementsAreArray(node));
}

TEST(ParserIntegrationTest, ScoringMemberFunction) {
  std::string scoring = "this.CreationTimestamp()";
  Lexer lexer(scoring, Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, QueryMemberFunction) {
  std::string query = "this.foo()";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserIntegrationTest, ScoringComplexMemberFunction) {
  std::string scoring = "a.b.fun(c, d)";
  Lexer lexer(scoring, Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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
  std::string query = "this.abc.fun(def, ghi)";
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
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

TEST(ParserTest, QueryShouldNotStackOverflowAtMaxNumTokens) {
  // query = "(( ... (foo bar) ... ))"
  std::string query;
  for (int i = 0; i < Lexer::kMaxNumTokens / 2 - 1; ++i) {
    query.push_back('(');
  }
  query.append("foo bar");
  for (int i = 0; i < Lexer::kMaxNumTokens / 2 - 1; ++i) {
    query.push_back(')');
  }

  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
  EXPECT_THAT(lexer_tokens, SizeIs(Lexer::kMaxNumTokens));
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(), IsOk());
}

TEST(ParserTest, ScoringShouldNotStackOverflowAtMaxNumTokens) {
  // scoring = "(( ... (-1) ... ))"
  std::string scoring;
  for (int i = 0; i < Lexer::kMaxNumTokens / 2 - 1; ++i) {
    scoring.push_back('(');
  }
  scoring.append("-1");
  for (int i = 0; i < Lexer::kMaxNumTokens / 2 - 1; ++i) {
    scoring.push_back(')');
  }

  Lexer lexer(scoring, Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
  EXPECT_THAT(lexer_tokens, SizeIs(Lexer::kMaxNumTokens));
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeScoring(), IsOk());
}

TEST(ParserTest, InvalidQueryShouldNotStackOverflowAtMaxNumTokens) {
  std::string query;
  for (int i = 0; i < Lexer::kMaxNumTokens; ++i) {
    query.push_back('(');
  }
  Lexer lexer(query, Lexer::Language::QUERY);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
  EXPECT_THAT(lexer_tokens, SizeIs(Lexer::kMaxNumTokens));
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeQuery(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(ParserTest, InvalidScoringShouldNotStackOverflowAtMaxNumTokens) {
  std::string scoring;
  for (int i = 0; i < Lexer::kMaxNumTokens; ++i) {
    scoring.push_back('(');
  }
  Lexer lexer(scoring, Lexer::Language::SCORING);
  ICING_ASSERT_OK_AND_ASSIGN(std::vector<Lexer::LexerToken> lexer_tokens,
                             lexer.ExtractTokens());
  EXPECT_THAT(lexer_tokens, SizeIs(Lexer::kMaxNumTokens));
  Parser parser = Parser::Create(std::move(lexer_tokens));
  EXPECT_THAT(parser.ConsumeScoring(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

}  // namespace

}  // namespace lib
}  // namespace icing
