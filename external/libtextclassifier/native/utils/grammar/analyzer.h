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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_ANALYZER_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_ANALYZER_H_

#include "utils/base/arena.h"
#include "utils/base/statusor.h"
#include "utils/grammar/evaluated-derivation.h"
#include "utils/grammar/parsing/parser.h"
#include "utils/grammar/semantics/composer.h"
#include "utils/grammar/text-context.h"
#include "utils/i18n/locale.h"
#include "utils/tokenizer.h"
#include "utils/utf8/unilib.h"

namespace libtextclassifier3::grammar {

// An analyzer that parses and semantically evaluates an input text with a
// grammar.
class Analyzer {
 public:
  explicit Analyzer(const UniLib* unilib, const RulesSet* rules_set);
  explicit Analyzer(const UniLib* unilib, const RulesSet* rules_set,
                    const Tokenizer* tokenizer);

  // Parses and evaluates an input.
  StatusOr<std::vector<EvaluatedDerivation>> Parse(
      const TextContext& input, UnsafeArena* arena,
      bool deduplicate_derivations = true) const;

  StatusOr<std::vector<EvaluatedDerivation>> Parse(
      const UnicodeText& text, const std::vector<Locale>& locales,
      UnsafeArena* arena, bool deduplicate_derivations = true) const;

  // Pre-processes an input text for parsing.
  TextContext BuildTextContextForInput(
      const UnicodeText& text, const std::vector<Locale>& locales = {}) const;

  const Parser& parser() const { return parser_; }

 private:
  std::unique_ptr<Tokenizer> owned_tokenizer_;
  const Tokenizer* tokenizer_;
  Parser parser_;
  SemanticComposer semantic_evaluator_;
};

}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_ANALYZER_H_
