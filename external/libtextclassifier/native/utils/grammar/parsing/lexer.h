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

// A lexer that (splits) and classifies tokens.
//
// Any whitespace gets absorbed into the token that follows them in the text.
// For example, if the text contains:
//
//      ...hello                       there        world...
//              |                      |            |
//              offset=16              39           52
//
// then the output will be:
//
//      "hello" [?, 16)
//      "there" [16, 44)      <-- note "16" NOT "39"
//      "world" [44, ?)       <-- note "44" NOT "52"
//
// This makes it appear to the Matcher as if the tokens are adjacent.

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_LEXER_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_LEXER_H_

#include <vector>

#include "annotator/types.h"
#include "utils/grammar/parsing/parse-tree.h"
#include "utils/grammar/types.h"
#include "utils/strings/stringpiece.h"
#include "utils/utf8/unicodetext.h"
#include "utils/utf8/unilib.h"

namespace libtextclassifier3::grammar {

// A lexical symbol with an identified meaning that represents raw tokens,
// token categories or predefined text matches.
// It is the unit fed to the grammar matcher.
struct Symbol {
  // The type of the lexical symbol.
  enum class Type {
    // A raw token.
    TYPE_TERM,

    // A symbol representing a string of digits.
    TYPE_DIGITS,

    // Punctuation characters.
    TYPE_PUNCTUATION,

    // A predefined parse tree.
    TYPE_PARSE_TREE
  };

  explicit Symbol() = default;

  // Constructs a symbol of a given type with an anchor in the text.
  Symbol(const Type type, const CodepointSpan codepoint_span,
         const int match_offset, StringPiece lexeme)
      : type(type),
        codepoint_span(codepoint_span),
        match_offset(match_offset),
        lexeme(lexeme) {}

  // Constructs a symbol from a pre-defined parse tree.
  explicit Symbol(ParseTree* parse_tree)
      : type(Type::TYPE_PARSE_TREE),
        codepoint_span(parse_tree->codepoint_span),
        match_offset(parse_tree->match_offset),
        parse_tree(parse_tree) {}

  // The type of the symbol.
  Type type;

  // The span in the text as codepoint offsets.
  CodepointSpan codepoint_span;

  // The match start offset (including preceding whitespace) as codepoint
  // offset.
  int match_offset;

  // The symbol text value.
  StringPiece lexeme;

  // The predefined parse tree.
  ParseTree* parse_tree;
};

class Lexer {
 public:
  explicit Lexer(const UniLib* unilib) : unilib_(*unilib) {}

  // Processes a single token.
  // Splits a token into classified symbols.
  void AppendTokenSymbols(const StringPiece value, int match_offset,
                          const CodepointSpan codepoint_span,
                          std::vector<Symbol>* symbols) const;

 private:
  // Gets the type of a character.
  Symbol::Type GetSymbolType(const UnicodeText::const_iterator& it) const;

  const UniLib& unilib_;
};

}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_LEXER_H_
