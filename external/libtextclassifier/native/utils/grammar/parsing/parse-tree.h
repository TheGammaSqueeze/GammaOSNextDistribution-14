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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_PARSE_TREE_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_PARSE_TREE_H_

#include <functional>
#include <vector>

#include "annotator/types.h"
#include "utils/grammar/semantics/expression_generated.h"
#include "utils/grammar/types.h"
#include "utils/strings/stringpiece.h"

namespace libtextclassifier3::grammar {

// Represents a parse tree for a match that was found for a nonterminal.
struct ParseTree {
  enum class Type : int8 {
    // Default, untyped match.
    kDefault = 0,

    // An assertion match (see: AssertionNode).
    kAssertion = 1,

    // A value mapping match (see: MappingNode).
    kMapping = 2,

    // An exclusion match (see: ExclusionNode).
    kExclusion = 3,

    // A match for an annotation (see: AnnotationNode).
    kAnnotation = 4,

    // A match for a semantic annotation (see: SemanticExpressionNode).
    kExpression = 5,
  };

  explicit ParseTree() = default;
  explicit ParseTree(const Nonterm lhs, const CodepointSpan& codepoint_span,
                     const int match_offset, const Type type)
      : lhs(lhs),
        type(type),
        codepoint_span(codepoint_span),
        match_offset(match_offset) {}

  // For binary rule matches:  rhs1 != NULL and rhs2 != NULL
  //      unary rule matches:  rhs1 == NULL and rhs2 != NULL
  //   terminal rule matches:  rhs1 != NULL and rhs2 == NULL
  //           custom leaves:  rhs1 == NULL and rhs2 == NULL
  bool IsInteriorNode() const { return rhs2 != nullptr; }
  bool IsLeaf() const { return !rhs2; }

  bool IsBinaryRule() const { return rhs1 && rhs2; }
  bool IsUnaryRule() const { return !rhs1 && rhs2; }
  bool IsTerminalRule() const { return rhs1 && !rhs2; }
  bool HasLeadingWhitespace() const {
    return codepoint_span.first != match_offset;
  }

  const ParseTree* unary_rule_rhs() const { return rhs2; }

  // Used in singly-linked queue of matches for processing.
  ParseTree* next = nullptr;

  // Nonterminal we found a match for.
  Nonterm lhs = kUnassignedNonterm;

  // Type of the match.
  Type type = Type::kDefault;

  // The span in codepoints.
  CodepointSpan codepoint_span;

  // The begin codepoint offset used during matching.
  // This is usually including any prefix whitespace.
  int match_offset;

  union {
    // The first sub match for binary rules.
    const ParseTree* rhs1 = nullptr;

    // The terminal, for terminal rules.
    const char* terminal;
  };
  // First or second sub-match for interior nodes.
  const ParseTree* rhs2 = nullptr;
};

// Node type to keep track of associated values.
struct MappingNode : public ParseTree {
  explicit MappingNode(const Nonterm arg_lhs,
                       const CodepointSpan arg_codepoint_span,
                       const int arg_match_offset, const int64 arg_value)
      : ParseTree(arg_lhs, arg_codepoint_span, arg_match_offset,
                  Type::kMapping),
        id(arg_value) {}
  // The associated id or value.
  int64 id;
};

// Node type to keep track of assertions.
struct AssertionNode : public ParseTree {
  explicit AssertionNode(const Nonterm arg_lhs,
                         const CodepointSpan arg_codepoint_span,
                         const int arg_match_offset, const bool arg_negative)
      : ParseTree(arg_lhs, arg_codepoint_span, arg_match_offset,
                  Type::kAssertion),
        negative(arg_negative) {}
  // If true, the assertion is negative and will be valid if the input doesn't
  // match.
  bool negative;
};

// Node type to define exclusions.
struct ExclusionNode : public ParseTree {
  explicit ExclusionNode(const Nonterm arg_lhs,
                         const CodepointSpan arg_codepoint_span,
                         const int arg_match_offset,
                         const Nonterm arg_exclusion_nonterm)
      : ParseTree(arg_lhs, arg_codepoint_span, arg_match_offset,
                  Type::kExclusion),
        exclusion_nonterm(arg_exclusion_nonterm) {}
  // The nonterminal that denotes matches to exclude from a successful match.
  // So the match is only valid if there is no match of `exclusion_nonterm`
  // spanning the same text range.
  Nonterm exclusion_nonterm;
};

// Match to represent an annotator annotated span in the grammar.
struct AnnotationNode : public ParseTree {
  explicit AnnotationNode(const Nonterm arg_lhs,
                          const CodepointSpan arg_codepoint_span,
                          const int arg_match_offset,
                          const ClassificationResult* arg_annotation)
      : ParseTree(arg_lhs, arg_codepoint_span, arg_match_offset,
                  Type::kAnnotation),
        annotation(arg_annotation) {}
  const ClassificationResult* annotation;
};

// Node type to represent an associated semantic expression.
struct SemanticExpressionNode : public ParseTree {
  explicit SemanticExpressionNode(const Nonterm arg_lhs,
                                  const CodepointSpan arg_codepoint_span,
                                  const int arg_match_offset,
                                  const SemanticExpression* arg_expression)
      : ParseTree(arg_lhs, arg_codepoint_span, arg_match_offset,
                  Type::kExpression),
        expression(arg_expression) {}
  const SemanticExpression* expression;
};

// Utility functions for parse tree traversal.

// Does a preorder traversal, calling `node_fn` on each node.
// `node_fn` is expected to return whether to continue expanding a node.
void Traverse(const ParseTree* root,
              const std::function<bool(const ParseTree*)>& node_fn);

// Does a preorder traversal, selecting all nodes where `pred_fn` returns true.
std::vector<const ParseTree*> SelectAll(
    const ParseTree* root,
    const std::function<bool(const ParseTree*)>& pred_fn);

// Retrieves all nodes of a given type.
template <typename T>
const std::vector<const T*> SelectAllOfType(const ParseTree* root,
                                            const ParseTree::Type type) {
  std::vector<const T*> result;
  Traverse(root, [&result, type](const ParseTree* node) {
    if (node->type == type) {
      result.push_back(static_cast<const T*>(node));
    }
    return true;
  });
  return result;
}

}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_PARSING_PARSE_TREE_H_
