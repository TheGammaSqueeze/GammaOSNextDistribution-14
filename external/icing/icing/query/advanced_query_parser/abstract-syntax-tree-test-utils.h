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

#ifndef ICING_QUERY_ADVANCED_QUERY_PARSER_ABSTRACT_SYNTAX_TREE_TEST_UTILS_H_
#define ICING_QUERY_ADVANCED_QUERY_PARSER_ABSTRACT_SYNTAX_TREE_TEST_UTILS_H_

#include <memory>
#include <string>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/query/advanced_query_parser/abstract-syntax-tree.h"

namespace icing {
namespace lib {

// A visitor that simply collects the nodes and flattens them in left-side
// depth-first order.
enum class NodeType {
  kFunctionName,
  kString,
  kText,
  kMember,
  kFunction,
  kUnaryOperator,
  kNaryOperator
};

struct NodeInfo {
  std::string value;
  NodeType type;

  bool operator==(const NodeInfo& rhs) const {
    return value == rhs.value && type == rhs.type;
  }
};

MATCHER_P2(EqualsNodeInfo, value, type, "") {
  if (arg.value != value || arg.type != type) {
    *result_listener << "(Expected: value=\"" << value
                     << "\", type=" << static_cast<int>(type)
                     << ". Actual: value=\"" << arg.value
                     << "\", type=" << static_cast<int>(arg.type) << ")";
    return false;
  }
  return true;
}

class SimpleVisitor : public AbstractSyntaxTreeVisitor {
 public:
  void VisitFunctionName(const FunctionNameNode* node) override {
    nodes_.push_back({node->value(), NodeType::kFunctionName});
  }
  void VisitString(const StringNode* node) override {
    nodes_.push_back({node->value(), NodeType::kString});
  }
  void VisitText(const TextNode* node) override {
    nodes_.push_back({node->value(), NodeType::kText});
  }
  void VisitMember(const MemberNode* node) override {
    for (const std::unique_ptr<TextNode>& child : node->children()) {
      child->Accept(this);
    }
    if (node->function() != nullptr) {
      node->function()->Accept(this);
    }
    nodes_.push_back({"", NodeType::kMember});
  }
  void VisitFunction(const FunctionNode* node) override {
    node->function_name()->Accept(this);
    for (const std::unique_ptr<Node>& arg : node->args()) {
      arg->Accept(this);
    }
    nodes_.push_back({"", NodeType::kFunction});
  }
  void VisitUnaryOperator(const UnaryOperatorNode* node) override {
    node->child()->Accept(this);
    nodes_.push_back({node->operator_text(), NodeType::kUnaryOperator});
  }
  void VisitNaryOperator(const NaryOperatorNode* node) override {
    for (const std::unique_ptr<Node>& child : node->children()) {
      child->Accept(this);
    }
    nodes_.push_back({node->operator_text(), NodeType::kNaryOperator});
  }

  const std::vector<NodeInfo>& nodes() const { return nodes_; }

 private:
  std::vector<NodeInfo> nodes_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_QUERY_ADVANCED_QUERY_PARSER_ABSTRACT_SYNTAX_TREE_TEST_UTILS_H_
