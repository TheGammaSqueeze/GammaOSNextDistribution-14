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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_PARSER_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_PARSER_H_

#include <vector>

#include "annotator/types.h"
#include "utils/base/arena.h"
#include "utils/grammar/parsing/derivation.h"
#include "utils/grammar/parsing/lexer.h"
#include "utils/grammar/parsing/matcher.h"
#include "utils/grammar/rules_generated.h"
#include "utils/grammar/text-context.h"
#include "utils/i18n/locale.h"
#include "utils/utf8/unilib.h"

namespace libtextclassifier3::grammar {

// Syntactic parsing pass.
// The parser validates and deduplicates candidates produced by the grammar
// matcher. It augments the parse trees with derivation information for semantic
// evaluation.
class Parser {
 public:
  explicit Parser(const UniLib* unilib, const RulesSet* rules);

  // Parses an input text and returns the root rule derivations.
  std::vector<Derivation> Parse(const TextContext& input,
                                UnsafeArena* arena) const;

 private:
  struct RegexAnnotator {
    std::unique_ptr<UniLib::RegexPattern> pattern;
    Nonterm nonterm;
  };

  // Uncompresses and build the defined regex annotators.
  std::vector<RegexAnnotator> BuildRegexAnnotators() const;

  // Produces symbols for a text input to feed to a matcher.
  // These are symbols for each tokens from the lexer, existing text annotations
  // and regex annotations.
  // The symbols are sorted with increasing end-positions to satisfy the matcher
  // requirements.
  std::vector<Symbol> SortedSymbolsForInput(const TextContext& input,
                                            UnsafeArena* arena) const;

  // Emits a symbol to the matcher.
  void EmitSymbol(const Symbol& symbol, UnsafeArena* arena,
                  Matcher* matcher) const;

  const UniLib& unilib_;
  const RulesSet* rules_;
  const Lexer lexer_;

  // Pre-defined nonterminals.
  const RulesSet_::Nonterminals* nonterminals_;

  // Pre-parsed locales of the rules.
  const std::vector<std::vector<Locale>> rules_locales_;

  std::vector<RegexAnnotator> regex_annotators_;
};

}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_PARSER_H_
