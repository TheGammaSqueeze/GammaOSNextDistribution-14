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

#include "utils/grammar/analyzer.h"

#include "utils/base/status_macros.h"
#include "utils/utf8/unicodetext.h"

namespace libtextclassifier3::grammar {

Analyzer::Analyzer(const UniLib* unilib, const RulesSet* rules_set)
    // TODO(smillius): Add tokenizer options to `RulesSet`.
    : owned_tokenizer_(new Tokenizer(libtextclassifier3::TokenizationType_ICU,
                                     unilib,
                                     /*codepoint_ranges=*/{},
                                     /*internal_tokenizer_codepoint_ranges=*/{},
                                     /*split_on_script_change=*/false,
                                     /*icu_preserve_whitespace_tokens=*/false)),
      tokenizer_(owned_tokenizer_.get()),
      parser_(unilib, rules_set),
      semantic_evaluator_(rules_set->semantic_values_schema() != nullptr
                              ? flatbuffers::GetRoot<reflection::Schema>(
                                    rules_set->semantic_values_schema()->data())
                              : nullptr) {}

Analyzer::Analyzer(const UniLib* unilib, const RulesSet* rules_set,
                   const Tokenizer* tokenizer)
    : tokenizer_(tokenizer),
      parser_(unilib, rules_set),
      semantic_evaluator_(rules_set->semantic_values_schema() != nullptr
                              ? flatbuffers::GetRoot<reflection::Schema>(
                                    rules_set->semantic_values_schema()->data())
                              : nullptr) {}

StatusOr<std::vector<EvaluatedDerivation>> Analyzer::Parse(
    const TextContext& input, UnsafeArena* arena,
    bool deduplicate_derivations) const {
  std::vector<EvaluatedDerivation> result;

  std::vector<Derivation> derivations = parser_.Parse(input, arena);
  if (deduplicate_derivations) {
    derivations = DeduplicateDerivations<Derivation>(derivations);
  }
  // Evaluate each derivation.
  for (const Derivation& derivation : derivations) {
    if (derivation.IsValid()) {
      TC3_ASSIGN_OR_RETURN(const SemanticValue* value,
                           semantic_evaluator_.Eval(input, derivation, arena));
      result.emplace_back(
          EvaluatedDerivation{{/*parse_tree=*/derivation.parse_tree,
                               /*rule_id=*/derivation.rule_id},
                              /*semantic_value=*/value});
    }
  }

  return result;
}

StatusOr<std::vector<EvaluatedDerivation>> Analyzer::Parse(
    const UnicodeText& text, const std::vector<Locale>& locales,
    UnsafeArena* arena, bool deduplicate_derivations) const {
  return Parse(BuildTextContextForInput(text, locales), arena,
               deduplicate_derivations);
}

TextContext Analyzer::BuildTextContextForInput(
    const UnicodeText& text, const std::vector<Locale>& locales) const {
  TextContext context;
  context.text = UnicodeText(text, /*do_copy=*/false);
  context.tokens = tokenizer_->Tokenize(context.text);
  context.codepoints = context.text.Codepoints();
  context.codepoints.push_back(context.text.end());
  context.locales = locales;
  context.context_span.first = 0;
  context.context_span.second = context.tokens.size();
  return context;
}

}  // namespace libtextclassifier3::grammar
