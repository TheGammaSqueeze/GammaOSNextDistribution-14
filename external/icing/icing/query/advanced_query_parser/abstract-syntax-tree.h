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

#ifndef ICING_QUERY_ADVANCED_QUERY_PARSER_ABSTRACT_SYNTAX_TREE_H_
#define ICING_QUERY_ADVANCED_QUERY_PARSER_ABSTRACT_SYNTAX_TREE_H_

#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

namespace icing {
namespace lib {

class FunctionNameNode;
class StringNode;
class TextNode;
class MemberNode;
class FunctionNode;
class UnaryOperatorNode;
class NaryOperatorNode;

class AbstractSyntaxTreeVisitor {
 public:
  virtual ~AbstractSyntaxTreeVisitor() = default;

  virtual void VisitFunctionName(const FunctionNameNode* node) = 0;
  virtual void VisitString(const StringNode* node) = 0;
  virtual void VisitText(const TextNode* node) = 0;
  virtual void VisitMember(const MemberNode* node) = 0;
  virtual void VisitFunction(const FunctionNode* node) = 0;
  virtual void VisitUnaryOperator(const UnaryOperatorNode* node) = 0;
  virtual void VisitNaryOperator(const NaryOperatorNode* node) = 0;
};

class Node {
 public:
  virtual ~Node() = default;
  virtual void Accept(AbstractSyntaxTreeVisitor* visitor) const = 0;
};

class TerminalNode : public Node {
 public:
  explicit TerminalNode(std::string value, std::string_view raw_value,
                        bool is_prefix)
      : value_(std::move(value)),
        raw_value_(raw_value),
        is_prefix_(is_prefix) {}

  const std::string& value() const& { return value_; }
  std::string value() && { return std::move(value_); }

  bool is_prefix() const { return is_prefix_; }

  std::string_view raw_value() const { return raw_value_; }

 private:
  std::string value_;
  std::string_view raw_value_;
  bool is_prefix_;
};

class FunctionNameNode : public TerminalNode {
 public:
  explicit FunctionNameNode(std::string value)
      : TerminalNode(std::move(value), /*raw_value=*/"", /*is_prefix=*/false) {}
  void Accept(AbstractSyntaxTreeVisitor* visitor) const override {
    visitor->VisitFunctionName(this);
  }
};

class StringNode : public TerminalNode {
 public:
  explicit StringNode(std::string value, std::string_view raw_value,
                      bool is_prefix = false)
      : TerminalNode(std::move(value), raw_value, is_prefix) {}
  void Accept(AbstractSyntaxTreeVisitor* visitor) const override {
    visitor->VisitString(this);
  }
};

class TextNode : public TerminalNode {
 public:
  explicit TextNode(std::string value, std::string_view raw_value,
                    bool is_prefix = false)
      : TerminalNode(std::move(value), raw_value, is_prefix) {}
  void Accept(AbstractSyntaxTreeVisitor* visitor) const override {
    visitor->VisitText(this);
  }
};

class MemberNode : public Node {
 public:
  explicit MemberNode(std::vector<std::unique_ptr<TextNode>> children,
                      std::unique_ptr<FunctionNode> function)
      : children_(std::move(children)), function_(std::move(function)) {}

  void Accept(AbstractSyntaxTreeVisitor* visitor) const override {
    visitor->VisitMember(this);
  }
  const std::vector<std::unique_ptr<TextNode>>& children() const {
    return children_;
  }
  const FunctionNode* function() const { return function_.get(); }

 private:
  std::vector<std::unique_ptr<TextNode>> children_;
  // This is nullable. When it is not nullptr, this class will represent a
  // function call.
  std::unique_ptr<FunctionNode> function_;
};

class FunctionNode : public Node {
 public:
  explicit FunctionNode(std::unique_ptr<FunctionNameNode> function_name)
      : FunctionNode(std::move(function_name), {}) {}
  explicit FunctionNode(std::unique_ptr<FunctionNameNode> function_name,
                        std::vector<std::unique_ptr<Node>> args)
      : function_name_(std::move(function_name)), args_(std::move(args)) {}

  void Accept(AbstractSyntaxTreeVisitor* visitor) const override {
    visitor->VisitFunction(this);
  }
  const FunctionNameNode* function_name() const { return function_name_.get(); }
  const std::vector<std::unique_ptr<Node>>& args() const { return args_; }

 private:
  std::unique_ptr<FunctionNameNode> function_name_;
  std::vector<std::unique_ptr<Node>> args_;
};

class UnaryOperatorNode : public Node {
 public:
  explicit UnaryOperatorNode(std::string operator_text,
                             std::unique_ptr<Node> child)
      : operator_text_(std::move(operator_text)), child_(std::move(child)) {}

  void Accept(AbstractSyntaxTreeVisitor* visitor) const override {
    visitor->VisitUnaryOperator(this);
  }
  const std::string& operator_text() const { return operator_text_; }
  const Node* child() const { return child_.get(); }

 private:
  std::string operator_text_;
  std::unique_ptr<Node> child_;
};

class NaryOperatorNode : public Node {
 public:
  explicit NaryOperatorNode(std::string operator_text,
                            std::vector<std::unique_ptr<Node>> children)
      : operator_text_(std::move(operator_text)),
        children_(std::move(children)) {}

  void Accept(AbstractSyntaxTreeVisitor* visitor) const override {
    visitor->VisitNaryOperator(this);
  }
  const std::string& operator_text() const { return operator_text_; }
  const std::vector<std::unique_ptr<Node>>& children() const {
    return children_;
  }

 private:
  std::string operator_text_;
  std::vector<std::unique_ptr<Node>> children_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_QUERY_ADVANCED_QUERY_PARSER_ABSTRACT_SYNTAX_TREE_H_
