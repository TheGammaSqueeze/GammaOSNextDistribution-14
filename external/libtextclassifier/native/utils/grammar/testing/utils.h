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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_TESTING_UTILS_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_TESTING_UTILS_H_

#include <memory>
#include <vector>

#include "utils/base/arena.h"
#include "utils/flatbuffers/reflection.h"
#include "utils/grammar/parsing/derivation.h"
#include "utils/grammar/parsing/parse-tree.h"
#include "utils/grammar/semantics/value.h"
#include "utils/grammar/testing/value_generated.h"
#include "utils/grammar/text-context.h"
#include "utils/i18n/locale.h"
#include "utils/jvm-test-utils.h"
#include "utils/test-data-test-utils.h"
#include "utils/tokenizer.h"
#include "utils/utf8/unilib.h"
#include "gmock/gmock.h"
#include "flatbuffers/base.h"
#include "flatbuffers/flatbuffers.h"

namespace libtextclassifier3::grammar {

inline std::ostream& operator<<(std::ostream& os, const ParseTree* parse_tree) {
  return os << "ParseTree(lhs=" << parse_tree->lhs
            << ", begin=" << parse_tree->codepoint_span.first
            << ", end=" << parse_tree->codepoint_span.second << ")";
}

inline std::ostream& operator<<(std::ostream& os,
                                const Derivation& derivation) {
  return os << "Derivation(rule_id=" << derivation.rule_id << ", "
            << "parse_tree=" << derivation.parse_tree << ")";
}

MATCHER_P3(IsDerivation, rule_id, begin, end,
           "is derivation of rule that " +
               ::testing::DescribeMatcher<int>(rule_id, negation) +
               ", begin that " +
               ::testing::DescribeMatcher<int>(begin, negation) +
               ", end that " + ::testing::DescribeMatcher<int>(end, negation)) {
  return ::testing::ExplainMatchResult(CodepointSpan(begin, end),
                                       arg.parse_tree->codepoint_span,
                                       result_listener) &&
         ::testing::ExplainMatchResult(rule_id, arg.rule_id, result_listener);
}

// A test fixture with common auxiliary test methods.
class GrammarTest : public testing::Test {
 protected:
  explicit GrammarTest()
      : unilib_(CreateUniLibForTesting()),
        arena_(/*block_size=*/16 << 10),
        semantic_values_schema_(
            GetTestFileContent("utils/grammar/testing/value.bfbs")),
        tokenizer_(libtextclassifier3::TokenizationType_ICU, unilib_.get(),
                   /*codepoint_ranges=*/{},
                   /*internal_tokenizer_codepoint_ranges=*/{},
                   /*split_on_script_change=*/false,
                   /*icu_preserve_whitespace_tokens=*/false) {}

  TextContext TextContextForText(const std::string& text) {
    TextContext context;
    context.text = UTF8ToUnicodeText(text);
    context.tokens = tokenizer_.Tokenize(context.text);
    context.codepoints = context.text.Codepoints();
    context.codepoints.push_back(context.text.end());
    context.locales = {Locale::FromBCP47("en")};
    context.context_span.first = 0;
    context.context_span.second = context.tokens.size();
    return context;
  }

  // Creates a semantic expression union.
  template <typename T>
  SemanticExpressionT AsSemanticExpressionUnion(T&& expression) {
    SemanticExpressionT semantic_expression;
    semantic_expression.expression.Set(std::forward<T>(expression));
    return semantic_expression;
  }

  template <typename T>
  OwnedFlatbuffer<SemanticExpression> CreateExpression(T&& expression) {
    return Pack<SemanticExpression>(
        AsSemanticExpressionUnion(std::forward<T>(expression)));
  }

  OwnedFlatbuffer<SemanticExpression> CreateEmptyExpression() {
    return Pack<SemanticExpression>(SemanticExpressionT());
  }

  // Packs a flatbuffer.
  template <typename T>
  OwnedFlatbuffer<T> Pack(const typename T::NativeTableType&& value) {
    flatbuffers::FlatBufferBuilder builder;
    builder.Finish(T::Pack(builder, &value));
    return OwnedFlatbuffer<T>(builder.Release());
  }

  // Creates a test semantic value.
  const SemanticValue* CreateSemanticValue(const TestValueT& value) {
    const std::string value_buffer = PackFlatbuffer<TestValue>(&value);
    return arena_.AllocAndInit<SemanticValue>(
        semantic_values_schema_->objects()->Get(
            TypeIdForName(semantic_values_schema_.get(),
                          "libtextclassifier3.grammar.TestValue")
                .value()),
        StringPiece(arena_.Memdup(value_buffer.data(), value_buffer.size()),
                    value_buffer.size()));
  }

  // Creates a primitive semantic value.
  template <typename T>
  const SemanticValue* CreatePrimitiveSemanticValue(const T value) {
    return arena_.AllocAndInit<SemanticValue>(value);
  }

  std::unique_ptr<SemanticExpressionT> CreateConstExpression(
      const TestValueT& value) {
    ConstValueExpressionT const_value;
    const_value.base_type = reflection::BaseType::Obj;
    const_value.type = TypeIdForName(semantic_values_schema_.get(),
                                     "libtextclassifier3.grammar.TestValue")
                           .value();
    const std::string value_buffer = PackFlatbuffer<TestValue>(&value);
    const_value.value.assign(value_buffer.begin(), value_buffer.end());
    auto semantic_expression = std::make_unique<SemanticExpressionT>();
    semantic_expression->expression.Set(const_value);
    return semantic_expression;
  }

  OwnedFlatbuffer<SemanticExpression> CreateAndPackConstExpression(
      const TestValueT& value) {
    ConstValueExpressionT const_value;
    const_value.base_type = reflection::BaseType::Obj;
    const_value.type = TypeIdForName(semantic_values_schema_.get(),
                                     "libtextclassifier3.grammar.TestValue")
                           .value();
    const std::string value_buffer = PackFlatbuffer<TestValue>(&value);
    const_value.value.assign(value_buffer.begin(), value_buffer.end());
    return CreateExpression(const_value);
  }

  std::unique_ptr<SemanticExpressionT> CreateConstDateExpression(
      const TestDateT& value) {
    ConstValueExpressionT const_value;
    const_value.base_type = reflection::BaseType::Obj;
    const_value.type = TypeIdForName(semantic_values_schema_.get(),
                                     "libtextclassifier3.grammar.TestDate")
                           .value();
    const std::string value_buffer = PackFlatbuffer<TestDate>(&value);
    const_value.value.assign(value_buffer.begin(), value_buffer.end());
    auto semantic_expression = std::make_unique<SemanticExpressionT>();
    semantic_expression->expression.Set(const_value);
    return semantic_expression;
  }

  OwnedFlatbuffer<SemanticExpression> CreateAndPackMergeValuesExpression(
      const std::vector<TestDateT>& values) {
    MergeValueExpressionT merge_expression;
    merge_expression.type = TypeIdForName(semantic_values_schema_.get(),
                                          "libtextclassifier3.grammar.TestDate")
                                .value();
    for (const TestDateT& test_date : values) {
      merge_expression.values.emplace_back(new SemanticExpressionT);
      merge_expression.values.back() = CreateConstDateExpression(test_date);
    }
    return CreateExpression(std::move(merge_expression));
  }

  template <typename T>
  std::unique_ptr<SemanticExpressionT> CreatePrimitiveConstExpression(
      const T value) {
    ConstValueExpressionT const_value;
    const_value.base_type = flatbuffers_base_type<T>::value;
    const_value.value.resize(sizeof(T));
    flatbuffers::WriteScalar(const_value.value.data(), value);
    auto semantic_expression = std::make_unique<SemanticExpressionT>();
    semantic_expression->expression.Set(const_value);
    return semantic_expression;
  }

  template <typename T>
  OwnedFlatbuffer<SemanticExpression> CreateAndPackPrimitiveConstExpression(
      const T value) {
    ConstValueExpressionT const_value;
    const_value.base_type = flatbuffers_base_type<T>::value;
    const_value.value.resize(sizeof(T));
    flatbuffers::WriteScalar(const_value.value.data(), value);
    return CreateExpression(const_value);
  }

  template <>
  OwnedFlatbuffer<SemanticExpression> CreateAndPackPrimitiveConstExpression(
      const StringPiece value) {
    ConstValueExpressionT const_value;
    const_value.base_type = reflection::BaseType::String;
    const_value.value.assign(value.data(), value.data() + value.size());
    return CreateExpression(const_value);
  }

  template <>
  std::unique_ptr<SemanticExpressionT> CreatePrimitiveConstExpression(
      const StringPiece value) {
    ConstValueExpressionT const_value;
    const_value.base_type = reflection::BaseType::String;
    const_value.value.assign(value.data(), value.data() + value.size());
    auto semantic_expression = std::make_unique<SemanticExpressionT>();
    semantic_expression->expression.Set(const_value);
    return semantic_expression;
  }

  const std::unique_ptr<UniLib> unilib_;
  UnsafeArena arena_;
  const OwnedFlatbuffer<reflection::Schema, std::string>
      semantic_values_schema_;
  const Tokenizer tokenizer_;
};

}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_TESTING_UTILS_H_
