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

#include "utils/grammar/parsing/matcher.h"

#include <iostream>
#include <limits>

#include "utils/base/endian.h"
#include "utils/base/logging.h"
#include "utils/base/macros.h"
#include "utils/grammar/types.h"
#include "utils/strings/utf8.h"

namespace libtextclassifier3::grammar {
namespace {

// Iterator that just enumerates the bytes in a utf8 text.
struct ByteIterator {
  explicit ByteIterator(StringPiece text)
      : data(text.data()), end(text.data() + text.size()) {}

  inline char Next() {
    TC3_DCHECK(HasNext());
    const char c = data[0];
    data++;
    return c;
  }
  inline bool HasNext() const { return data < end; }

  const char* data;
  const char* end;
};

// Iterator that lowercases a utf8 string on the fly and enumerates the bytes.
struct LowercasingByteIterator {
  LowercasingByteIterator(const UniLib* unilib, StringPiece text)
      : unilib(*unilib),
        data(text.data()),
        end(text.data() + text.size()),
        buffer_pos(0),
        buffer_size(0) {}

  inline char Next() {
    // Queue next character.
    if (buffer_pos >= buffer_size) {
      buffer_pos = 0;

      // Lower-case the next character. The character and its lower-cased
      // counterpart may be represented with a different number of bytes in
      // utf8.
      buffer_size =
          ValidRuneToChar(unilib.ToLower(ValidCharToRune(data)), buffer);
      data += GetNumBytesForUTF8Char(data);
    }
    TC3_DCHECK_LT(buffer_pos, buffer_size);
    return buffer[buffer_pos++];
  }

  inline bool HasNext() const {
    // Either we are not at the end of the data or didn't consume all bytes of
    // the current character.
    return (data < end || buffer_pos < buffer_size);
  }

  const UniLib& unilib;
  const char* data;
  const char* end;

  // Each unicode codepoint can have up to 4 utf8 encoding bytes.
  char buffer[4];
  int buffer_pos;
  int buffer_size;
};

// Searches a terminal match within a sorted table of terminals.
// Using `LowercasingByteIterator` allows to lower-case the query string on the
// fly.
template <typename T>
const char* FindTerminal(T input_iterator, const char* strings,
                         const uint32* offsets, const int num_terminals,
                         int* terminal_index) {
  int left = 0;
  int right = num_terminals;
  int span_size = right - left;
  int match_length = 0;

  // Loop invariant:
  // At the ith iteration, all strings in the range `left` ... `right` match the
  // input on the first `match_length` characters.
  while (true) {
    const unsigned char c =
        static_cast<const unsigned char>(input_iterator.Next());

    // We find the possible range of strings in `left` ... `right` matching the
    // `match_length` + 1 character with two binary searches:
    //    1) `lower_bound` to find the start of the range of matching strings.
    //    2) `upper_bound` to find the non-inclusive end of the range.
    left =
        (std::lower_bound(
             offsets + left, offsets + right, c,
             [strings, match_length](uint32 string_offset, uint32 c) -> bool {
               return static_cast<unsigned char>(
                          strings[string_offset + match_length]) <
                      LittleEndian::ToHost32(c);
             }) -
         offsets);
    right =
        (std::upper_bound(
             offsets + left, offsets + right, c,
             [strings, match_length](uint32 c, uint32 string_offset) -> bool {
               return LittleEndian::ToHost32(c) <
                      static_cast<unsigned char>(
                          strings[string_offset + match_length]);
             }) -
         offsets);
    span_size = right - left;
    if (span_size <= 0) {
      return nullptr;
    }
    ++match_length;

    // By the loop invariant and due to the fact that the strings are sorted,
    // a matching string will be at `left` now.
    if (!input_iterator.HasNext()) {
      const int string_offset = LittleEndian::ToHost32(offsets[left]);
      if (strings[string_offset + match_length] == 0) {
        *terminal_index = left;
        return &strings[string_offset];
      }
      return nullptr;
    }
  }

  // No match found.
  return nullptr;
}

// Finds terminal matches in the terminal rules hash tables.
// In case a match is found, `terminal` will be set to point into the
// terminals string pool.
template <typename T>
const RulesSet_::LhsSet* FindTerminalMatches(
    T input_iterator, const RulesSet* rules_set,
    const RulesSet_::Rules_::TerminalRulesMap* terminal_rules,
    StringPiece* terminal) {
  const int terminal_size = terminal->size();
  if (terminal_size < terminal_rules->min_terminal_length() ||
      terminal_size > terminal_rules->max_terminal_length()) {
    return nullptr;
  }
  int terminal_index;
  if (const char* terminal_match = FindTerminal(
          input_iterator, rules_set->terminals()->data(),
          terminal_rules->terminal_offsets()->data(),
          terminal_rules->terminal_offsets()->size(), &terminal_index)) {
    *terminal = StringPiece(terminal_match, terminal->length());
    return rules_set->lhs_set()->Get(
        terminal_rules->lhs_set_index()->Get(terminal_index));
  }
  return nullptr;
}

// Finds unary rules matches.
const RulesSet_::LhsSet* FindUnaryRulesMatches(const RulesSet* rules_set,
                                               const RulesSet_::Rules* rules,
                                               const Nonterm nonterminal) {
  if (!rules->unary_rules()) {
    return nullptr;
  }
  if (const RulesSet_::Rules_::UnaryRulesEntry* entry =
          rules->unary_rules()->LookupByKey(nonterminal)) {
    return rules_set->lhs_set()->Get(entry->value());
  }
  return nullptr;
}

// Finds binary rules matches.
const RulesSet_::LhsSet* FindBinaryRulesMatches(
    const RulesSet* rules_set, const RulesSet_::Rules* rules,
    const TwoNonterms nonterminals) {
  if (!rules->binary_rules()) {
    return nullptr;
  }

  // Lookup in rules hash table.
  const uint32 bucket_index =
      BinaryRuleHasher()(nonterminals) % rules->binary_rules()->size();

  // Get hash table bucket.
  if (const RulesSet_::Rules_::BinaryRuleTableBucket* bucket =
          rules->binary_rules()->Get(bucket_index)) {
    if (bucket->rules() == nullptr) {
      return nullptr;
    }

    // Check all entries in the chain.
    for (const RulesSet_::Rules_::BinaryRule* rule : *bucket->rules()) {
      if (rule->rhs_first() == nonterminals.first &&
          rule->rhs_second() == nonterminals.second) {
        return rules_set->lhs_set()->Get(rule->lhs_set_index());
      }
    }
  }

  return nullptr;
}

inline void GetLhs(const RulesSet* rules_set, const int lhs_entry,
                   Nonterm* nonterminal, CallbackId* callback, int64* param,
                   int8* max_whitespace_gap) {
  if (lhs_entry > 0) {
    // Direct encoding of the nonterminal.
    *nonterminal = lhs_entry;
    *callback = kNoCallback;
    *param = 0;
    *max_whitespace_gap = -1;
  } else {
    const RulesSet_::Lhs* lhs = rules_set->lhs()->Get(-lhs_entry);
    *nonterminal = lhs->nonterminal();
    *callback = lhs->callback_id();
    *param = lhs->callback_param();
    *max_whitespace_gap = lhs->max_whitespace_gap();
  }
}

}  // namespace

void Matcher::Finish() {
  // Check any pending items.
  ProcessPendingExclusionMatches();
}

void Matcher::QueueForProcessing(ParseTree* item) {
  // Push element to the front.
  item->next = pending_items_;
  pending_items_ = item;
}

void Matcher::QueueForPostCheck(ExclusionNode* item) {
  // Push element to the front.
  item->next = pending_exclusion_items_;
  pending_exclusion_items_ = item;
}

void Matcher::AddTerminal(const CodepointSpan codepoint_span,
                          const int match_offset, StringPiece terminal) {
  TC3_CHECK_GE(codepoint_span.second, last_end_);

  // Finish any pending post-checks.
  if (codepoint_span.second > last_end_) {
    ProcessPendingExclusionMatches();
  }

  last_end_ = codepoint_span.second;
  for (const RulesSet_::Rules* shard : rules_shards_) {
    // Try case-sensitive matches.
    if (const RulesSet_::LhsSet* lhs_set =
            FindTerminalMatches(ByteIterator(terminal), rules_,
                                shard->terminal_rules(), &terminal)) {
      // `terminal` points now into the rules string pool, providing a
      // stable reference.
      ExecuteLhsSet(
          codepoint_span, match_offset,
          /*whitespace_gap=*/(codepoint_span.first - match_offset),
          [terminal](ParseTree* parse_tree) {
            parse_tree->terminal = terminal.data();
            parse_tree->rhs2 = nullptr;
          },
          lhs_set);
    }

    // Try case-insensitive matches.
    if (const RulesSet_::LhsSet* lhs_set = FindTerminalMatches(
            LowercasingByteIterator(&unilib_, terminal), rules_,
            shard->lowercase_terminal_rules(), &terminal)) {
      // `terminal` points now into the rules string pool, providing a
      // stable reference.
      ExecuteLhsSet(
          codepoint_span, match_offset,
          /*whitespace_gap=*/(codepoint_span.first - match_offset),
          [terminal](ParseTree* parse_tree) {
            parse_tree->terminal = terminal.data();
            parse_tree->rhs2 = nullptr;
          },
          lhs_set);
    }
  }
  ProcessPendingSet();
}

void Matcher::AddParseTree(ParseTree* parse_tree) {
  TC3_CHECK_GE(parse_tree->codepoint_span.second, last_end_);

  // Finish any pending post-checks.
  if (parse_tree->codepoint_span.second > last_end_) {
    ProcessPendingExclusionMatches();
  }

  last_end_ = parse_tree->codepoint_span.second;
  QueueForProcessing(parse_tree);
  ProcessPendingSet();
}

void Matcher::ExecuteLhsSet(
    const CodepointSpan codepoint_span, const int match_offset_bytes,
    const int whitespace_gap,
    const std::function<void(ParseTree*)>& initializer_fn,
    const RulesSet_::LhsSet* lhs_set) {
  TC3_CHECK(lhs_set);
  ParseTree* parse_tree = nullptr;
  Nonterm prev_lhs = kUnassignedNonterm;
  for (const int32 lhs_entry : *lhs_set->lhs()) {
    Nonterm lhs;
    CallbackId callback_id;
    int64 callback_param;
    int8 max_whitespace_gap;
    GetLhs(rules_, lhs_entry, &lhs, &callback_id, &callback_param,
           &max_whitespace_gap);

    // Check that the allowed whitespace gap limit is followed.
    if (max_whitespace_gap >= 0 && whitespace_gap > max_whitespace_gap) {
      continue;
    }

    // Handle callbacks.
    switch (static_cast<DefaultCallback>(callback_id)) {
      case DefaultCallback::kAssertion: {
        AssertionNode* assertion_node = arena_->AllocAndInit<AssertionNode>(
            lhs, codepoint_span, match_offset_bytes,
            /*negative=*/(callback_param != 0));
        initializer_fn(assertion_node);
        QueueForProcessing(assertion_node);
        continue;
      }
      case DefaultCallback::kMapping: {
        MappingNode* mapping_node = arena_->AllocAndInit<MappingNode>(
            lhs, codepoint_span, match_offset_bytes, /*id=*/callback_param);
        initializer_fn(mapping_node);
        QueueForProcessing(mapping_node);
        continue;
      }
      case DefaultCallback::kExclusion: {
        // We can only check the exclusion once all matches up to this position
        // have been processed. Schedule and post check later.
        ExclusionNode* exclusion_node = arena_->AllocAndInit<ExclusionNode>(
            lhs, codepoint_span, match_offset_bytes,
            /*exclusion_nonterm=*/callback_param);
        initializer_fn(exclusion_node);
        QueueForPostCheck(exclusion_node);
        continue;
      }
      case DefaultCallback::kSemanticExpression: {
        SemanticExpressionNode* expression_node =
            arena_->AllocAndInit<SemanticExpressionNode>(
                lhs, codepoint_span, match_offset_bytes,
                /*expression=*/
                rules_->semantic_expression()->Get(callback_param));
        initializer_fn(expression_node);
        QueueForProcessing(expression_node);
        continue;
      }
      default:
        break;
    }

    if (prev_lhs != lhs) {
      prev_lhs = lhs;
      parse_tree = arena_->AllocAndInit<ParseTree>(
          lhs, codepoint_span, match_offset_bytes, ParseTree::Type::kDefault);
      initializer_fn(parse_tree);
      QueueForProcessing(parse_tree);
    }

    if (static_cast<DefaultCallback>(callback_id) ==
        DefaultCallback::kRootRule) {
      chart_.AddDerivation(Derivation{parse_tree, /*rule_id=*/callback_param});
    }
  }
}

void Matcher::ProcessPendingSet() {
  while (pending_items_) {
    // Process.
    ParseTree* item = pending_items_;
    pending_items_ = pending_items_->next;

    // Add it to the chart.
    chart_.Add(item);

    // Check unary rules that trigger.
    for (const RulesSet_::Rules* shard : rules_shards_) {
      if (const RulesSet_::LhsSet* lhs_set =
              FindUnaryRulesMatches(rules_, shard, item->lhs)) {
        ExecuteLhsSet(
            item->codepoint_span, item->match_offset,
            /*whitespace_gap=*/
            (item->codepoint_span.first - item->match_offset),
            [item](ParseTree* parse_tree) {
              parse_tree->rhs1 = nullptr;
              parse_tree->rhs2 = item;
            },
            lhs_set);
      }
    }

    // Check binary rules that trigger.
    // Lookup by begin.
    for (Chart<>::Iterator it = chart_.MatchesEndingAt(item->match_offset);
         !it.Done(); it.Next()) {
      const ParseTree* prev = it.Item();
      for (const RulesSet_::Rules* shard : rules_shards_) {
        if (const RulesSet_::LhsSet* lhs_set =
                FindBinaryRulesMatches(rules_, shard, {prev->lhs, item->lhs})) {
          ExecuteLhsSet(
              /*codepoint_span=*/
              {prev->codepoint_span.first, item->codepoint_span.second},
              prev->match_offset,
              /*whitespace_gap=*/
              (item->codepoint_span.first -
               item->match_offset),  // Whitespace gap is the gap
                                     // between the two parts.
              [prev, item](ParseTree* parse_tree) {
                parse_tree->rhs1 = prev;
                parse_tree->rhs2 = item;
              },
              lhs_set);
        }
      }
    }
  }
}

void Matcher::ProcessPendingExclusionMatches() {
  while (pending_exclusion_items_) {
    ExclusionNode* item = pending_exclusion_items_;
    pending_exclusion_items_ = static_cast<ExclusionNode*>(item->next);

    // Check that the exclusion condition is fulfilled.
    if (!chart_.HasMatch(item->exclusion_nonterm, item->codepoint_span)) {
      AddParseTree(item);
    }
  }
}

}  // namespace libtextclassifier3::grammar
