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

// Unit tests for the lexer.

#include "utils/grammar/parsing/lexer.h"

#include <memory>
#include <string>
#include <vector>

#include "annotator/types.h"
#include "utils/jvm-test-utils.h"
#include "utils/tokenizer.h"
#include "utils/utf8/unicodetext.h"
#include "utils/utf8/unilib.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3::grammar {

std::ostream& operator<<(std::ostream& os, const Symbol& symbol) {
  return os << "Symbol(type=" << static_cast<int>(symbol.type) << ", span=("
            << symbol.codepoint_span.first << ", "
            << symbol.codepoint_span.second
            << "), lexeme=" << symbol.lexeme.ToString() << ")";
}

namespace {

using ::testing::DescribeMatcher;
using ::testing::ElementsAre;
using ::testing::ExplainMatchResult;

// Superclass of all tests here.
class LexerTest : public testing::Test {
 protected:
  explicit LexerTest()
      : unilib_(libtextclassifier3::CreateUniLibForTesting()),
        tokenizer_(TokenizationType_ICU, unilib_.get(),
                   /*codepoint_ranges=*/{},
                   /*internal_tokenizer_codepoint_ranges=*/{},
                   /*split_on_script_change=*/false,
                   /*icu_preserve_whitespace_tokens=*/false),
        lexer_(unilib_.get()) {}

  std::vector<Symbol> SymbolsForTokens(const std::vector<Token>& tokens) const {
    std::vector<Symbol> symbols;
    for (const Token& token : tokens) {
      lexer_.AppendTokenSymbols(token.value, token.start,
                                CodepointSpan{token.start, token.end},
                                &symbols);
    }
    return symbols;
  }

  std::unique_ptr<UniLib> unilib_;
  Tokenizer tokenizer_;
  Lexer lexer_;
};

MATCHER_P4(IsSymbol, type, begin, end, terminal,
           "is symbol with type that " +
               DescribeMatcher<Symbol::Type>(type, negation) + ", begin that " +
               DescribeMatcher<int>(begin, negation) + ", end that " +
               DescribeMatcher<int>(end, negation) + ", value that " +
               DescribeMatcher<std::string>(terminal, negation)) {
  return ExplainMatchResult(type, arg.type, result_listener) &&
         ExplainMatchResult(CodepointSpan(begin, end), arg.codepoint_span,
                            result_listener) &&
         ExplainMatchResult(terminal, arg.lexeme.ToString(), result_listener);
}

TEST_F(LexerTest, HandlesSimpleWords) {
  std::vector<Token> tokens = tokenizer_.Tokenize("This is a word");
  EXPECT_THAT(SymbolsForTokens(tokens),
              ElementsAre(IsSymbol(Symbol::Type::TYPE_TERM, 0, 4, "This"),
                          IsSymbol(Symbol::Type::TYPE_TERM, 5, 7, "is"),
                          IsSymbol(Symbol::Type::TYPE_TERM, 8, 9, "a"),
                          IsSymbol(Symbol::Type::TYPE_TERM, 10, 14, "word")));
}

TEST_F(LexerTest, SplitsConcatedLettersAndDigit) {
  std::vector<Token> tokens = tokenizer_.Tokenize("1234This a4321cde");
  EXPECT_THAT(SymbolsForTokens(tokens),
              ElementsAre(IsSymbol(Symbol::Type::TYPE_DIGITS, 0, 4, "1234"),
                          IsSymbol(Symbol::Type::TYPE_TERM, 4, 8, "This"),
                          IsSymbol(Symbol::Type::TYPE_TERM, 9, 10, "a"),
                          IsSymbol(Symbol::Type::TYPE_DIGITS, 10, 14, "4321"),
                          IsSymbol(Symbol::Type::TYPE_TERM, 14, 17, "cde")));
}

TEST_F(LexerTest, SplitsPunctuation) {
  std::vector<Token> tokens = tokenizer_.Tokenize("10/18/2014");
  EXPECT_THAT(SymbolsForTokens(tokens),
              ElementsAre(IsSymbol(Symbol::Type::TYPE_DIGITS, 0, 2, "10"),
                          IsSymbol(Symbol::Type::TYPE_PUNCTUATION, 2, 3, "/"),
                          IsSymbol(Symbol::Type::TYPE_DIGITS, 3, 5, "18"),
                          IsSymbol(Symbol::Type::TYPE_PUNCTUATION, 5, 6, "/"),
                          IsSymbol(Symbol::Type::TYPE_DIGITS, 6, 10, "2014")));
}

TEST_F(LexerTest, SplitsUTF8Punctuation) {
  std::vector<Token> tokens = tokenizer_.Tokenize("电话：0871—6857（曹");
  EXPECT_THAT(
      SymbolsForTokens(tokens),
      ElementsAre(IsSymbol(Symbol::Type::TYPE_TERM, 0, 2, "电话"),
                  IsSymbol(Symbol::Type::TYPE_PUNCTUATION, 2, 3, "："),
                  IsSymbol(Symbol::Type::TYPE_DIGITS, 3, 7, "0871"),
                  IsSymbol(Symbol::Type::TYPE_PUNCTUATION, 7, 8, "—"),
                  IsSymbol(Symbol::Type::TYPE_DIGITS, 8, 12, "6857"),
                  IsSymbol(Symbol::Type::TYPE_PUNCTUATION, 12, 13, "（"),
                  IsSymbol(Symbol::Type::TYPE_TERM, 13, 14, "曹")));
}

TEST_F(LexerTest, HandlesMixedPunctuation) {
  std::vector<Token> tokens = tokenizer_.Tokenize("电话 ：0871—6857（曹");
  EXPECT_THAT(
      SymbolsForTokens(tokens),
      ElementsAre(IsSymbol(Symbol::Type::TYPE_TERM, 0, 2, "电话"),
                  IsSymbol(Symbol::Type::TYPE_PUNCTUATION, 3, 4, "："),
                  IsSymbol(Symbol::Type::TYPE_DIGITS, 4, 8, "0871"),
                  IsSymbol(Symbol::Type::TYPE_PUNCTUATION, 8, 9, "—"),
                  IsSymbol(Symbol::Type::TYPE_DIGITS, 9, 13, "6857"),
                  IsSymbol(Symbol::Type::TYPE_PUNCTUATION, 13, 14, "（"),
                  IsSymbol(Symbol::Type::TYPE_TERM, 14, 15, "曹")));
}

TEST_F(LexerTest, HandlesTokensWithDigits) {
  std::vector<Token> tokens =
      tokenizer_.Tokenize("The.qUIck\n brown2345fox88 \xE2\x80\x94 the");
  EXPECT_THAT(SymbolsForTokens(tokens),
              ElementsAre(IsSymbol(Symbol::Type::TYPE_TERM, 0, 3, "The"),
                          IsSymbol(Symbol::Type::TYPE_PUNCTUATION, 3, 4, "."),
                          IsSymbol(Symbol::Type::TYPE_TERM, 4, 9, "qUIck"),
                          IsSymbol(Symbol::Type::TYPE_TERM, 11, 16, "brown"),
                          IsSymbol(Symbol::Type::TYPE_DIGITS, 16, 20, "2345"),
                          IsSymbol(Symbol::Type::TYPE_TERM, 20, 23, "fox"),
                          IsSymbol(Symbol::Type::TYPE_DIGITS, 23, 25, "88"),
                          IsSymbol(Symbol::Type::TYPE_PUNCTUATION, 26, 27, "—"),
                          IsSymbol(Symbol::Type::TYPE_TERM, 28, 31, "the")));
}

TEST_F(LexerTest, SplitsPlusSigns) {
  std::vector<Token> tokens = tokenizer_.Tokenize("The+2345++the +");
  EXPECT_THAT(SymbolsForTokens(tokens),
              ElementsAre(IsSymbol(Symbol::Type::TYPE_TERM, 0, 3, "The"),
                          IsSymbol(Symbol::Type::TYPE_TERM, 3, 4, "+"),
                          IsSymbol(Symbol::Type::TYPE_DIGITS, 4, 8, "2345"),
                          IsSymbol(Symbol::Type::TYPE_TERM, 8, 9, "+"),
                          IsSymbol(Symbol::Type::TYPE_TERM, 9, 10, "+"),
                          IsSymbol(Symbol::Type::TYPE_TERM, 10, 13, "the"),
                          IsSymbol(Symbol::Type::TYPE_TERM, 14, 15, "+")));
}

}  // namespace
}  // namespace libtextclassifier3::grammar
