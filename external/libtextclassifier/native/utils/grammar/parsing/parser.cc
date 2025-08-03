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

#include "utils/grammar/parsing/parser.h"

#include <algorithm>
#include <unordered_map>

#include "utils/grammar/parsing/parse-tree.h"
#include "utils/grammar/rules-utils.h"
#include "utils/grammar/types.h"
#include "utils/zlib/zlib.h"
#include "utils/zlib/zlib_regex.h"

namespace libtextclassifier3::grammar {
namespace {

inline bool CheckMemoryUsage(const UnsafeArena* arena) {
  // The maximum memory usage for matching.
  constexpr int kMaxMemoryUsage = 1 << 20;
  return arena->status().bytes_allocated() <= kMaxMemoryUsage;
}

// Maps a codepoint to include the token padding if it aligns with a token
// start. Whitespace is ignored when symbols are fed to the matcher. Preceding
// whitespace is merged to the match start so that tokens and non-terminals
// appear next to each other without whitespace. For text or regex annotations,
// we therefore merge the whitespace padding to the start if the annotation
// starts at a token.
int MapCodepointToTokenPaddingIfPresent(
    const std::unordered_map<CodepointIndex, CodepointIndex>& token_alignment,
    const int start) {
  const auto it = token_alignment.find(start);
  if (it != token_alignment.end()) {
    return it->second;
  }
  return start;
}

}  // namespace

Parser::Parser(const UniLib* unilib, const RulesSet* rules)
    : unilib_(*unilib),
      rules_(rules),
      lexer_(unilib),
      nonterminals_(rules_->nonterminals()),
      rules_locales_(ParseRulesLocales(rules_)),
      regex_annotators_(BuildRegexAnnotators()) {}

// Uncompresses and build the defined regex annotators.
std::vector<Parser::RegexAnnotator> Parser::BuildRegexAnnotators() const {
  std::vector<RegexAnnotator> result;
  if (rules_->regex_annotator() != nullptr) {
    std::unique_ptr<ZlibDecompressor> decompressor =
        ZlibDecompressor::Instance();
    result.reserve(rules_->regex_annotator()->size());
    for (const RulesSet_::RegexAnnotator* regex_annotator :
         *rules_->regex_annotator()) {
      result.push_back(
          {UncompressMakeRegexPattern(unilib_, regex_annotator->pattern(),
                                      regex_annotator->compressed_pattern(),
                                      rules_->lazy_regex_compilation(),
                                      decompressor.get()),
           regex_annotator->nonterminal()});
    }
  }
  return result;
}

std::vector<Symbol> Parser::SortedSymbolsForInput(const TextContext& input,
                                                  UnsafeArena* arena) const {
  // Whitespace is ignored when symbols are fed to the matcher.
  // For regex matches and existing text annotations we therefore have to merge
  // preceding whitespace to the match start so that tokens and non-terminals
  // appear as next to each other without whitespace. We keep track of real
  // token starts and precending whitespace in `token_match_start`, so that we
  // can extend a match's start to include the preceding whitespace.
  std::unordered_map<CodepointIndex, CodepointIndex> token_match_start;
  for (int i = input.context_span.first + 1; i < input.context_span.second;
       i++) {
    const CodepointIndex token_start = input.tokens[i].start;
    const CodepointIndex prev_token_end = input.tokens[i - 1].end;
    if (token_start != prev_token_end) {
      token_match_start[token_start] = prev_token_end;
    }
  }

  std::vector<Symbol> symbols;
  CodepointIndex match_offset = input.tokens[input.context_span.first].start;

  // Add start symbol.
  if (input.context_span.first == 0 &&
      nonterminals_->start_nt() != kUnassignedNonterm) {
    match_offset = 0;
    symbols.emplace_back(arena->AllocAndInit<ParseTree>(
        nonterminals_->start_nt(), CodepointSpan{0, 0},
        /*match_offset=*/0, ParseTree::Type::kDefault));
  }

  if (nonterminals_->wordbreak_nt() != kUnassignedNonterm) {
    symbols.emplace_back(arena->AllocAndInit<ParseTree>(
        nonterminals_->wordbreak_nt(),
        CodepointSpan{match_offset, match_offset},
        /*match_offset=*/match_offset, ParseTree::Type::kDefault));
  }

  // Add symbols from tokens.
  for (int i = input.context_span.first; i < input.context_span.second; i++) {
    const Token& token = input.tokens[i];
    lexer_.AppendTokenSymbols(token.value, /*match_offset=*/match_offset,
                              CodepointSpan{token.start, token.end}, &symbols);
    match_offset = token.end;

    // Add word break symbol.
    if (nonterminals_->wordbreak_nt() != kUnassignedNonterm) {
      symbols.emplace_back(arena->AllocAndInit<ParseTree>(
          nonterminals_->wordbreak_nt(),
          CodepointSpan{match_offset, match_offset},
          /*match_offset=*/match_offset, ParseTree::Type::kDefault));
    }
  }

  // Add end symbol if used by the grammar.
  if (input.context_span.second == input.tokens.size() &&
      nonterminals_->end_nt() != kUnassignedNonterm) {
    symbols.emplace_back(arena->AllocAndInit<ParseTree>(
        nonterminals_->end_nt(), CodepointSpan{match_offset, match_offset},
        /*match_offset=*/match_offset, ParseTree::Type::kDefault));
  }

  // Add symbols from the regex annotators.
  const CodepointIndex context_start =
      input.tokens[input.context_span.first].start;
  const CodepointIndex context_end =
      input.tokens[input.context_span.second - 1].end;
  for (const RegexAnnotator& regex_annotator : regex_annotators_) {
    std::unique_ptr<UniLib::RegexMatcher> regex_matcher =
        regex_annotator.pattern->Matcher(UnicodeText::Substring(
            input.text, context_start, context_end, /*do_copy=*/false));
    int status = UniLib::RegexMatcher::kNoError;
    while (regex_matcher->Find(&status) &&
           status == UniLib::RegexMatcher::kNoError) {
      const CodepointSpan span{regex_matcher->Start(0, &status) + context_start,
                               regex_matcher->End(0, &status) + context_start};
      symbols.emplace_back(arena->AllocAndInit<ParseTree>(
          regex_annotator.nonterm, span, /*match_offset=*/
          MapCodepointToTokenPaddingIfPresent(token_match_start, span.first),
          ParseTree::Type::kDefault));
    }
  }

  // Add symbols based on annotations.
  if (auto annotation_nonterminals = nonterminals_->annotation_nt()) {
    for (const AnnotatedSpan& annotated_span : input.annotations) {
      const ClassificationResult& classification =
          annotated_span.classification.front();
      if (auto entry = annotation_nonterminals->LookupByKey(
              classification.collection.c_str())) {
        symbols.emplace_back(arena->AllocAndInit<AnnotationNode>(
            entry->value(), annotated_span.span, /*match_offset=*/
            MapCodepointToTokenPaddingIfPresent(token_match_start,
                                                annotated_span.span.first),
            &classification));
      }
    }
  }

  std::stable_sort(
      symbols.begin(), symbols.end(), [](const Symbol& a, const Symbol& b) {
        // Sort by increasing (end, start) position to guarantee the
        // matcher requirement that the tokens are fed in non-decreasing
        // end position order.
        return std::tie(a.codepoint_span.second, a.codepoint_span.first) <
               std::tie(b.codepoint_span.second, b.codepoint_span.first);
      });

  return symbols;
}

void Parser::EmitSymbol(const Symbol& symbol, UnsafeArena* arena,
                        Matcher* matcher) const {
  if (!CheckMemoryUsage(arena)) {
    return;
  }
  switch (symbol.type) {
    case Symbol::Type::TYPE_PARSE_TREE: {
      // Just emit the parse tree.
      matcher->AddParseTree(symbol.parse_tree);
      return;
    }
    case Symbol::Type::TYPE_DIGITS: {
      // Emit <digits> if used by the rules.
      if (nonterminals_->digits_nt() != kUnassignedNonterm) {
        matcher->AddParseTree(arena->AllocAndInit<ParseTree>(
            nonterminals_->digits_nt(), symbol.codepoint_span,
            symbol.match_offset, ParseTree::Type::kDefault));
      }

      // Emit <n_digits> if used by the rules.
      if (nonterminals_->n_digits_nt() != nullptr) {
        const int num_digits =
            symbol.codepoint_span.second - symbol.codepoint_span.first;
        if (num_digits <= nonterminals_->n_digits_nt()->size()) {
          const Nonterm n_digits_nt =
              nonterminals_->n_digits_nt()->Get(num_digits - 1);
          if (n_digits_nt != kUnassignedNonterm) {
            matcher->AddParseTree(arena->AllocAndInit<ParseTree>(
                nonterminals_->n_digits_nt()->Get(num_digits - 1),
                symbol.codepoint_span, symbol.match_offset,
                ParseTree::Type::kDefault));
          }
        }
      }
      break;
    }
    case Symbol::Type::TYPE_TERM: {
      // Emit <uppercase_token> if used by the rules.
      if (nonterminals_->uppercase_token_nt() != 0 &&
          unilib_.IsUpperText(
              UTF8ToUnicodeText(symbol.lexeme, /*do_copy=*/false))) {
        matcher->AddParseTree(arena->AllocAndInit<ParseTree>(
            nonterminals_->uppercase_token_nt(), symbol.codepoint_span,
            symbol.match_offset, ParseTree::Type::kDefault));
      }
      break;
    }
    default:
      break;
  }

  // Emit the token as terminal.
  matcher->AddTerminal(symbol.codepoint_span, symbol.match_offset,
                       symbol.lexeme);

  // Emit <token> if used by rules.
  matcher->AddParseTree(arena->AllocAndInit<ParseTree>(
      nonterminals_->token_nt(), symbol.codepoint_span, symbol.match_offset,
      ParseTree::Type::kDefault));
}

// Parses an input text and returns the root rule derivations.
std::vector<Derivation> Parser::Parse(const TextContext& input,
                                      UnsafeArena* arena) const {
  // Check the tokens, input can be non-empty (whitespace) but have no tokens.
  if (input.tokens.empty()) {
    return {};
  }

  // Select locale matching rules.
  std::vector<const RulesSet_::Rules*> locale_rules =
      SelectLocaleMatchingShards(rules_, rules_locales_, input.locales);

  if (locale_rules.empty()) {
    // Nothing to do.
    return {};
  }

  Matcher matcher(&unilib_, rules_, locale_rules, arena);
  for (const Symbol& symbol : SortedSymbolsForInput(input, arena)) {
    EmitSymbol(symbol, arena, &matcher);
  }
  matcher.Finish();
  return matcher.chart().derivations();
}

}  // namespace libtextclassifier3::grammar
