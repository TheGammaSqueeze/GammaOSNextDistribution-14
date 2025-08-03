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

#ifndef ICING_SCORING_ADVANCED_SCORING_SCORE_EXPRESSION_H_
#define ICING_SCORING_ADVANCED_SCORING_SCORE_EXPRESSION_H_

#include <algorithm>
#include <cmath>
#include <memory>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/join/join-children-fetcher.h"
#include "icing/scoring/bm25f-calculator.h"
#include "icing/store/document-store.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

enum class ScoreExpressionType {
  kDouble,
  kDoubleList,
  kDocument  // Only "this" is considered as document type.
};

class ScoreExpression {
 public:
  virtual ~ScoreExpression() = default;

  // Evaluate the score expression to double with the current document.
  //
  // RETURNS:
  //   - The evaluated result as a double on success.
  //   - INVALID_ARGUMENT if a non-finite value is reached while evaluating the
  //                      expression.
  //   - INTERNAL if there are inconsistencies.
  virtual libtextclassifier3::StatusOr<double> eval(
      const DocHitInfo& hit_info, const DocHitInfoIterator* query_it) const {
    if (type() == ScoreExpressionType::kDouble) {
      return absl_ports::UnimplementedError(
          "All ScoreExpressions of type Double must provide their own "
          "implementation of eval!");
    }
    return absl_ports::InternalError(
        "Runtime type error: the expression should never be evaluated to a "
        "double. There must be inconsistencies in the static type checking.");
  }

  virtual libtextclassifier3::StatusOr<std::vector<double>> eval_list(
      const DocHitInfo& hit_info, const DocHitInfoIterator* query_it) const {
    if (type() == ScoreExpressionType::kDoubleList) {
      return absl_ports::UnimplementedError(
          "All ScoreExpressions of type Double List must provide their own "
          "implementation of eval_list!");
    }
    return absl_ports::InternalError(
        "Runtime type error: the expression should never be evaluated to a "
        "double list. There must be inconsistencies in the static type "
        "checking.");
  }

  // Indicate the type to which the current expression will be evaluated.
  virtual ScoreExpressionType type() const = 0;

  // Indicate whether the current expression is a constant double.
  // Returns true if and only if the object is of ConstantScoreExpression type.
  virtual bool is_constant_double() const { return false; }
};

class ThisExpression : public ScoreExpression {
 public:
  static std::unique_ptr<ThisExpression> Create() {
    return std::unique_ptr<ThisExpression>(new ThisExpression());
  }

  ScoreExpressionType type() const override {
    return ScoreExpressionType::kDocument;
  }

 private:
  ThisExpression() = default;
};

class ConstantScoreExpression : public ScoreExpression {
 public:
  static std::unique_ptr<ConstantScoreExpression> Create(
      libtextclassifier3::StatusOr<double> c) {
    return std::unique_ptr<ConstantScoreExpression>(
        new ConstantScoreExpression(c));
  }

  libtextclassifier3::StatusOr<double> eval(
      const DocHitInfo&, const DocHitInfoIterator*) const override {
    return c_;
  }

  ScoreExpressionType type() const override {
    return ScoreExpressionType::kDouble;
  }

  bool is_constant_double() const override { return true; }

 private:
  explicit ConstantScoreExpression(libtextclassifier3::StatusOr<double> c)
      : c_(c) {}

  libtextclassifier3::StatusOr<double> c_;
};

class OperatorScoreExpression : public ScoreExpression {
 public:
  enum class OperatorType { kPlus, kMinus, kNegative, kTimes, kDiv };

  // RETURNS:
  //   - An OperatorScoreExpression instance on success if not simplifiable.
  //   - A ConstantScoreExpression instance on success if simplifiable.
  //   - FAILED_PRECONDITION on any null pointer in children.
  //   - INVALID_ARGUMENT on type errors.
  static libtextclassifier3::StatusOr<std::unique_ptr<ScoreExpression>> Create(
      OperatorType op, std::vector<std::unique_ptr<ScoreExpression>> children);

  libtextclassifier3::StatusOr<double> eval(
      const DocHitInfo& hit_info,
      const DocHitInfoIterator* query_it) const override;

  ScoreExpressionType type() const override {
    return ScoreExpressionType::kDouble;
  }

 private:
  explicit OperatorScoreExpression(
      OperatorType op, std::vector<std::unique_ptr<ScoreExpression>> children)
      : op_(op), children_(std::move(children)) {}

  OperatorType op_;
  std::vector<std::unique_ptr<ScoreExpression>> children_;
};

class MathFunctionScoreExpression : public ScoreExpression {
 public:
  enum class FunctionType {
    kLog,
    kPow,
    kMax,
    kMin,
    kLen,
    kSum,
    kAvg,
    kSqrt,
    kAbs,
    kSin,
    kCos,
    kTan
  };

  static const std::unordered_map<std::string, FunctionType> kFunctionNames;

  static const std::unordered_set<FunctionType> kVariableArgumentsFunctions;

  // RETURNS:
  //   - A MathFunctionScoreExpression instance on success if not simplifiable.
  //   - A ConstantScoreExpression instance on success if simplifiable.
  //   - FAILED_PRECONDITION on any null pointer in args.
  //   - INVALID_ARGUMENT on type errors.
  static libtextclassifier3::StatusOr<std::unique_ptr<ScoreExpression>> Create(
      FunctionType function_type,
      std::vector<std::unique_ptr<ScoreExpression>> args);

  libtextclassifier3::StatusOr<double> eval(
      const DocHitInfo& hit_info,
      const DocHitInfoIterator* query_it) const override;

  ScoreExpressionType type() const override {
    return ScoreExpressionType::kDouble;
  }

 private:
  explicit MathFunctionScoreExpression(
      FunctionType function_type,
      std::vector<std::unique_ptr<ScoreExpression>> args)
      : function_type_(function_type), args_(std::move(args)) {}

  FunctionType function_type_;
  std::vector<std::unique_ptr<ScoreExpression>> args_;
};

class DocumentFunctionScoreExpression : public ScoreExpression {
 public:
  enum class FunctionType {
    kDocumentScore,
    kCreationTimestamp,
    kUsageCount,
    kUsageLastUsedTimestamp,
  };

  static const std::unordered_map<std::string, FunctionType> kFunctionNames;

  // RETURNS:
  //   - A DocumentFunctionScoreExpression instance on success.
  //   - FAILED_PRECONDITION on any null pointer in args.
  //   - INVALID_ARGUMENT on type errors.
  static libtextclassifier3::StatusOr<
      std::unique_ptr<DocumentFunctionScoreExpression>>
  Create(FunctionType function_type,
         std::vector<std::unique_ptr<ScoreExpression>> args,
         const DocumentStore* document_store, double default_score,
         int64_t current_time_ms);

  libtextclassifier3::StatusOr<double> eval(
      const DocHitInfo& hit_info,
      const DocHitInfoIterator* query_it) const override;

  ScoreExpressionType type() const override {
    return ScoreExpressionType::kDouble;
  }

 private:
  explicit DocumentFunctionScoreExpression(
      FunctionType function_type,
      std::vector<std::unique_ptr<ScoreExpression>> args,
      const DocumentStore* document_store, double default_score,
      int64_t current_time_ms)
      : args_(std::move(args)),
        document_store_(*document_store),
        default_score_(default_score),
        function_type_(function_type),
        current_time_ms_(current_time_ms) {}

  std::vector<std::unique_ptr<ScoreExpression>> args_;
  const DocumentStore& document_store_;
  double default_score_;
  FunctionType function_type_;
  int64_t current_time_ms_;
};

class RelevanceScoreFunctionScoreExpression : public ScoreExpression {
 public:
  static constexpr std::string_view kFunctionName = "relevanceScore";

  // RETURNS:
  //   - A RelevanceScoreFunctionScoreExpression instance on success.
  //   - FAILED_PRECONDITION on any null pointer in args.
  //   - INVALID_ARGUMENT on type errors.
  static libtextclassifier3::StatusOr<
      std::unique_ptr<RelevanceScoreFunctionScoreExpression>>
  Create(std::vector<std::unique_ptr<ScoreExpression>> args,
         Bm25fCalculator* bm25f_calculator, double default_score);

  libtextclassifier3::StatusOr<double> eval(
      const DocHitInfo& hit_info,
      const DocHitInfoIterator* query_it) const override;

  ScoreExpressionType type() const override {
    return ScoreExpressionType::kDouble;
  }

 private:
  explicit RelevanceScoreFunctionScoreExpression(
      Bm25fCalculator* bm25f_calculator, double default_score)
      : bm25f_calculator_(*bm25f_calculator), default_score_(default_score) {}

  Bm25fCalculator& bm25f_calculator_;
  double default_score_;
};

class ChildrenRankingSignalsFunctionScoreExpression : public ScoreExpression {
 public:
  static constexpr std::string_view kFunctionName = "childrenRankingSignals";

  // RETURNS:
  //   - A ChildrenRankingSignalsFunctionScoreExpression instance on success.
  //   - FAILED_PRECONDITION on any null pointer in children.
  //   - INVALID_ARGUMENT on type errors.
  static libtextclassifier3::StatusOr<
      std::unique_ptr<ChildrenRankingSignalsFunctionScoreExpression>>
  Create(std::vector<std::unique_ptr<ScoreExpression>> args,
         const JoinChildrenFetcher* join_children_fetcher);

  libtextclassifier3::StatusOr<std::vector<double>> eval_list(
      const DocHitInfo& hit_info,
      const DocHitInfoIterator* query_it) const override;

  ScoreExpressionType type() const override {
    return ScoreExpressionType::kDoubleList;
  }

 private:
  explicit ChildrenRankingSignalsFunctionScoreExpression(
      const JoinChildrenFetcher& join_children_fetcher)
      : join_children_fetcher_(join_children_fetcher) {}
  const JoinChildrenFetcher& join_children_fetcher_;
};

class PropertyWeightsFunctionScoreExpression : public ScoreExpression {
 public:
  static constexpr std::string_view kFunctionName = "propertyWeights";

  // RETURNS:
  //   - A PropertyWeightsFunctionScoreExpression instance on success.
  //   - FAILED_PRECONDITION on any null pointer in children.
  //   - INVALID_ARGUMENT on type errors.
  static libtextclassifier3::StatusOr<
      std::unique_ptr<PropertyWeightsFunctionScoreExpression>>
  Create(std::vector<std::unique_ptr<ScoreExpression>> args,
         const DocumentStore* document_store,
         const SectionWeights* section_weights, int64_t current_time_ms);

  libtextclassifier3::StatusOr<std::vector<double>> eval_list(
      const DocHitInfo& hit_info, const DocHitInfoIterator*) const override;

  ScoreExpressionType type() const override {
    return ScoreExpressionType::kDoubleList;
  }

  SchemaTypeId GetSchemaTypeId(DocumentId document_id) const;

 private:
  explicit PropertyWeightsFunctionScoreExpression(
      const DocumentStore* document_store,
      const SectionWeights* section_weights, int64_t current_time_ms)
      : document_store_(*document_store),
        section_weights_(*section_weights),
        current_time_ms_(current_time_ms) {}
  const DocumentStore& document_store_;
  const SectionWeights& section_weights_;
  int64_t current_time_ms_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCORING_ADVANCED_SCORING_SCORE_EXPRESSION_H_
