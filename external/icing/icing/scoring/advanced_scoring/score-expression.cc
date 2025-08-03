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

#include "icing/scoring/advanced_scoring/score-expression.h"

#include <numeric>
#include <vector>

#include "icing/absl_ports/canonical_errors.h"

namespace icing {
namespace lib {

namespace {

libtextclassifier3::Status CheckChildrenNotNull(
    const std::vector<std::unique_ptr<ScoreExpression>>& children) {
  for (const auto& child : children) {
    ICING_RETURN_ERROR_IF_NULL(child);
  }
  return libtextclassifier3::Status::OK;
}

}  // namespace

libtextclassifier3::StatusOr<std::unique_ptr<ScoreExpression>>
OperatorScoreExpression::Create(
    OperatorType op, std::vector<std::unique_ptr<ScoreExpression>> children) {
  if (children.empty()) {
    return absl_ports::InvalidArgumentError(
        "OperatorScoreExpression must have at least one argument.");
  }
  ICING_RETURN_IF_ERROR(CheckChildrenNotNull(children));

  bool children_all_constant_double = true;
  for (const auto& child : children) {
    if (child->type() != ScoreExpressionType::kDouble) {
      return absl_ports::InvalidArgumentError(
          "Operators are only supported for double type.");
    }
    if (!child->is_constant_double()) {
      children_all_constant_double = false;
    }
  }
  if (op == OperatorType::kNegative) {
    if (children.size() != 1) {
      return absl_ports::InvalidArgumentError(
          "Negative operator must have only 1 argument.");
    }
  }
  std::unique_ptr<ScoreExpression> expression =
      std::unique_ptr<OperatorScoreExpression>(
          new OperatorScoreExpression(op, std::move(children)));
  if (children_all_constant_double) {
    // Because all of the children are constants, this expression does not
    // depend on the DocHitInto or query_it that are passed into it.
    return ConstantScoreExpression::Create(
        expression->eval(DocHitInfo(), /*query_it=*/nullptr));
  }
  return expression;
}

libtextclassifier3::StatusOr<double> OperatorScoreExpression::eval(
    const DocHitInfo& hit_info, const DocHitInfoIterator* query_it) const {
  // The Create factory guarantees that an operator will have at least one
  // child.
  ICING_ASSIGN_OR_RETURN(double res, children_.at(0)->eval(hit_info, query_it));

  if (op_ == OperatorType::kNegative) {
    return -res;
  }

  for (int i = 1; i < children_.size(); ++i) {
    ICING_ASSIGN_OR_RETURN(double v, children_.at(i)->eval(hit_info, query_it));
    switch (op_) {
      case OperatorType::kPlus:
        res += v;
        break;
      case OperatorType::kMinus:
        res -= v;
        break;
      case OperatorType::kTimes:
        res *= v;
        break;
      case OperatorType::kDiv:
        res /= v;
        break;
      case OperatorType::kNegative:
        return absl_ports::InternalError("Should never reach here.");
    }
    if (!std::isfinite(res)) {
      return absl_ports::InvalidArgumentError(
          "Got a non-finite value while evaluating operator score expression.");
    }
  }
  return res;
}

const std::unordered_map<std::string, MathFunctionScoreExpression::FunctionType>
    MathFunctionScoreExpression::kFunctionNames = {
        {"log", FunctionType::kLog}, {"pow", FunctionType::kPow},
        {"max", FunctionType::kMax}, {"min", FunctionType::kMin},
        {"len", FunctionType::kLen}, {"sum", FunctionType::kSum},
        {"avg", FunctionType::kAvg}, {"sqrt", FunctionType::kSqrt},
        {"abs", FunctionType::kAbs}, {"sin", FunctionType::kSin},
        {"cos", FunctionType::kCos}, {"tan", FunctionType::kTan}};

const std::unordered_set<MathFunctionScoreExpression::FunctionType>
    MathFunctionScoreExpression::kVariableArgumentsFunctions = {
        FunctionType::kMax, FunctionType::kMin, FunctionType::kLen,
        FunctionType::kSum, FunctionType::kAvg};

libtextclassifier3::StatusOr<std::unique_ptr<ScoreExpression>>
MathFunctionScoreExpression::Create(
    FunctionType function_type,
    std::vector<std::unique_ptr<ScoreExpression>> args) {
  if (args.empty()) {
    return absl_ports::InvalidArgumentError(
        "Math functions must have at least one argument.");
  }
  ICING_RETURN_IF_ERROR(CheckChildrenNotNull(args));

  // Received a list type in the function argument.
  if (args.size() == 1 && args[0]->type() == ScoreExpressionType::kDoubleList) {
    // Only certain functions support list type.
    if (kVariableArgumentsFunctions.count(function_type) > 0) {
      return std::unique_ptr<MathFunctionScoreExpression>(
          new MathFunctionScoreExpression(function_type, std::move(args)));
    }
    return absl_ports::InvalidArgumentError(absl_ports::StrCat(
        "Received an unsupported list type argument in the math function."));
  }

  bool args_all_constant_double = true;
  for (const auto& child : args) {
    if (child->type() != ScoreExpressionType::kDouble) {
      return absl_ports::InvalidArgumentError(
          "Got an invalid type for the math function. Should expect a double "
          "type argument.");
    }
    if (!child->is_constant_double()) {
      args_all_constant_double = false;
    }
  }
  switch (function_type) {
    case FunctionType::kLog:
      if (args.size() != 1 && args.size() != 2) {
        return absl_ports::InvalidArgumentError(
            "log must have 1 or 2 arguments.");
      }
      break;
    case FunctionType::kPow:
      if (args.size() != 2) {
        return absl_ports::InvalidArgumentError("pow must have 2 arguments.");
      }
      break;
    case FunctionType::kSqrt:
      if (args.size() != 1) {
        return absl_ports::InvalidArgumentError("sqrt must have 1 argument.");
      }
      break;
    case FunctionType::kAbs:
      if (args.size() != 1) {
        return absl_ports::InvalidArgumentError("abs must have 1 argument.");
      }
      break;
    case FunctionType::kSin:
      if (args.size() != 1) {
        return absl_ports::InvalidArgumentError("sin must have 1 argument.");
      }
      break;
    case FunctionType::kCos:
      if (args.size() != 1) {
        return absl_ports::InvalidArgumentError("cos must have 1 argument.");
      }
      break;
    case FunctionType::kTan:
      if (args.size() != 1) {
        return absl_ports::InvalidArgumentError("tan must have 1 argument.");
      }
      break;
    // Functions that support variable length arguments
    case FunctionType::kMax:
      [[fallthrough]];
    case FunctionType::kMin:
      [[fallthrough]];
    case FunctionType::kLen:
      [[fallthrough]];
    case FunctionType::kSum:
      [[fallthrough]];
    case FunctionType::kAvg:
      break;
  }
  std::unique_ptr<ScoreExpression> expression =
      std::unique_ptr<MathFunctionScoreExpression>(
          new MathFunctionScoreExpression(function_type, std::move(args)));
  if (args_all_constant_double) {
    // Because all of the arguments are constants, this expression does not
    // depend on the DocHitInto or query_it that are passed into it.
    return ConstantScoreExpression::Create(
        expression->eval(DocHitInfo(), /*query_it=*/nullptr));
  }
  return expression;
}

libtextclassifier3::StatusOr<double> MathFunctionScoreExpression::eval(
    const DocHitInfo& hit_info, const DocHitInfoIterator* query_it) const {
  std::vector<double> values;
  if (args_.at(0)->type() == ScoreExpressionType::kDoubleList) {
    ICING_ASSIGN_OR_RETURN(values, args_.at(0)->eval_list(hit_info, query_it));
  } else {
    for (const auto& child : args_) {
      ICING_ASSIGN_OR_RETURN(double v, child->eval(hit_info, query_it));
      values.push_back(v);
    }
  }

  double res = 0;
  switch (function_type_) {
    case FunctionType::kLog:
      if (values.size() == 1) {
        res = log(values[0]);
      } else {
        // argument 0 is log base
        // argument 1 is the value
        res = log(values[1]) / log(values[0]);
      }
      break;
    case FunctionType::kPow:
      res = pow(values[0], values[1]);
      break;
    case FunctionType::kMax:
      if (values.empty()) {
        return absl_ports::InvalidArgumentError(
            "Got an empty parameter set in max function");
      }
      res = *std::max_element(values.begin(), values.end());
      break;
    case FunctionType::kMin:
      if (values.empty()) {
        return absl_ports::InvalidArgumentError(
            "Got an empty parameter set in min function");
      }
      res = *std::min_element(values.begin(), values.end());
      break;
    case FunctionType::kLen:
      res = values.size();
      break;
    case FunctionType::kSum:
      res = std::reduce(values.begin(), values.end());
      break;
    case FunctionType::kAvg:
      if (values.empty()) {
        return absl_ports::InvalidArgumentError(
            "Got an empty parameter set in avg function.");
      }
      res = std::reduce(values.begin(), values.end()) / values.size();
      break;
    case FunctionType::kSqrt:
      res = sqrt(values[0]);
      break;
    case FunctionType::kAbs:
      res = abs(values[0]);
      break;
    case FunctionType::kSin:
      res = sin(values[0]);
      break;
    case FunctionType::kCos:
      res = cos(values[0]);
      break;
    case FunctionType::kTan:
      res = tan(values[0]);
      break;
  }
  if (!std::isfinite(res)) {
    return absl_ports::InvalidArgumentError(
        "Got a non-finite value while evaluating math function score "
        "expression.");
  }
  return res;
}

const std::unordered_map<std::string,
                         DocumentFunctionScoreExpression::FunctionType>
    DocumentFunctionScoreExpression::kFunctionNames = {
        {"documentScore", FunctionType::kDocumentScore},
        {"creationTimestamp", FunctionType::kCreationTimestamp},
        {"usageCount", FunctionType::kUsageCount},
        {"usageLastUsedTimestamp", FunctionType::kUsageLastUsedTimestamp}};

libtextclassifier3::StatusOr<std::unique_ptr<DocumentFunctionScoreExpression>>
DocumentFunctionScoreExpression::Create(
    FunctionType function_type,
    std::vector<std::unique_ptr<ScoreExpression>> args,
    const DocumentStore* document_store, double default_score,
    int64_t current_time_ms) {
  if (args.empty()) {
    return absl_ports::InvalidArgumentError(
        "Document-based functions must have at least one argument.");
  }
  ICING_RETURN_IF_ERROR(CheckChildrenNotNull(args));

  if (args[0]->type() != ScoreExpressionType::kDocument) {
    return absl_ports::InvalidArgumentError(
        "The first parameter of document-based functions must be \"this\".");
  }
  switch (function_type) {
    case FunctionType::kDocumentScore:
      [[fallthrough]];
    case FunctionType::kCreationTimestamp:
      if (args.size() != 1) {
        return absl_ports::InvalidArgumentError(
            "DocumentScore/CreationTimestamp must have 1 argument.");
      }
      break;
    case FunctionType::kUsageCount:
      [[fallthrough]];
    case FunctionType::kUsageLastUsedTimestamp:
      if (args.size() != 2 || args[1]->type() != ScoreExpressionType::kDouble) {
        return absl_ports::InvalidArgumentError(
            "UsageCount/UsageLastUsedTimestamp must have 2 arguments. The "
            "first argument should be \"this\", and the second argument "
            "should be the usage type.");
      }
      break;
  }
  return std::unique_ptr<DocumentFunctionScoreExpression>(
      new DocumentFunctionScoreExpression(function_type, std::move(args),
                                          document_store, default_score,
                                          current_time_ms));
}

libtextclassifier3::StatusOr<double> DocumentFunctionScoreExpression::eval(
    const DocHitInfo& hit_info, const DocHitInfoIterator* query_it) const {
  switch (function_type_) {
    case FunctionType::kDocumentScore:
      [[fallthrough]];
    case FunctionType::kCreationTimestamp: {
      ICING_ASSIGN_OR_RETURN(DocumentAssociatedScoreData score_data,
                             document_store_.GetDocumentAssociatedScoreData(
                                 hit_info.document_id()),
                             default_score_);
      if (function_type_ == FunctionType::kDocumentScore) {
        return static_cast<double>(score_data.document_score());
      }
      return static_cast<double>(score_data.creation_timestamp_ms());
    }
    case FunctionType::kUsageCount:
      [[fallthrough]];
    case FunctionType::kUsageLastUsedTimestamp: {
      ICING_ASSIGN_OR_RETURN(double raw_usage_type,
                             args_[1]->eval(hit_info, query_it));
      int usage_type = (int)raw_usage_type;
      if (usage_type < 1 || usage_type > 3 || raw_usage_type != usage_type) {
        return absl_ports::InvalidArgumentError(
            "Usage type must be an integer from 1 to 3");
      }
      std::optional<UsageStore::UsageScores> usage_scores =
          document_store_.GetUsageScores(hit_info.document_id(),
                                         current_time_ms_);
      if (!usage_scores) {
        // If there's no UsageScores entry present for this doc, then just
        // treat it as a default instance.
        usage_scores = UsageStore::UsageScores();
      }
      if (function_type_ == FunctionType::kUsageCount) {
        if (usage_type == 1) {
          return usage_scores->usage_type1_count;
        } else if (usage_type == 2) {
          return usage_scores->usage_type2_count;
        } else {
          return usage_scores->usage_type3_count;
        }
      }
      if (usage_type == 1) {
        return usage_scores->usage_type1_last_used_timestamp_s * 1000.0;
      } else if (usage_type == 2) {
        return usage_scores->usage_type2_last_used_timestamp_s * 1000.0;
      } else {
        return usage_scores->usage_type3_last_used_timestamp_s * 1000.0;
      }
    }
  }
}

libtextclassifier3::StatusOr<
    std::unique_ptr<RelevanceScoreFunctionScoreExpression>>
RelevanceScoreFunctionScoreExpression::Create(
    std::vector<std::unique_ptr<ScoreExpression>> args,
    Bm25fCalculator* bm25f_calculator, double default_score) {
  if (args.size() != 1) {
    return absl_ports::InvalidArgumentError(
        "relevanceScore must have 1 argument.");
  }
  ICING_RETURN_IF_ERROR(CheckChildrenNotNull(args));

  if (args[0]->type() != ScoreExpressionType::kDocument) {
    return absl_ports::InvalidArgumentError(
        "relevanceScore must take \"this\" as its argument.");
  }
  return std::unique_ptr<RelevanceScoreFunctionScoreExpression>(
      new RelevanceScoreFunctionScoreExpression(bm25f_calculator,
                                                default_score));
}

libtextclassifier3::StatusOr<double>
RelevanceScoreFunctionScoreExpression::eval(
    const DocHitInfo& hit_info, const DocHitInfoIterator* query_it) const {
  if (query_it == nullptr) {
    return default_score_;
  }
  return static_cast<double>(
      bm25f_calculator_.ComputeScore(query_it, hit_info, default_score_));
}

libtextclassifier3::StatusOr<
    std::unique_ptr<ChildrenRankingSignalsFunctionScoreExpression>>
ChildrenRankingSignalsFunctionScoreExpression::Create(
    std::vector<std::unique_ptr<ScoreExpression>> args,
    const JoinChildrenFetcher* join_children_fetcher) {
  if (args.size() != 1) {
    return absl_ports::InvalidArgumentError(
        "childrenRankingSignals must have 1 argument.");
  }
  ICING_RETURN_IF_ERROR(CheckChildrenNotNull(args));

  if (args[0]->type() != ScoreExpressionType::kDocument) {
    return absl_ports::InvalidArgumentError(
        "childrenRankingSignals must take \"this\" as its argument.");
  }
  if (join_children_fetcher == nullptr) {
    return absl_ports::InvalidArgumentError(
        "childrenRankingSignals must only be used with join, but "
        "JoinChildrenFetcher "
        "is not provided.");
  }
  return std::unique_ptr<ChildrenRankingSignalsFunctionScoreExpression>(
      new ChildrenRankingSignalsFunctionScoreExpression(
          *join_children_fetcher));
}

libtextclassifier3::StatusOr<std::vector<double>>
ChildrenRankingSignalsFunctionScoreExpression::eval_list(
    const DocHitInfo& hit_info, const DocHitInfoIterator* query_it) const {
  ICING_ASSIGN_OR_RETURN(
      std::vector<ScoredDocumentHit> children_hits,
      join_children_fetcher_.GetChildren(hit_info.document_id()));
  std::vector<double> children_scores;
  children_scores.reserve(children_hits.size());
  for (const ScoredDocumentHit& child_hit : children_hits) {
    children_scores.push_back(child_hit.score());
  }
  return std::move(children_scores);
}

libtextclassifier3::StatusOr<
    std::unique_ptr<PropertyWeightsFunctionScoreExpression>>
PropertyWeightsFunctionScoreExpression::Create(
    std::vector<std::unique_ptr<ScoreExpression>> args,
    const DocumentStore* document_store, const SectionWeights* section_weights,
    int64_t current_time_ms) {
  if (args.size() != 1) {
    return absl_ports::InvalidArgumentError(
        "propertyWeights must have 1 argument.");
  }
  ICING_RETURN_IF_ERROR(CheckChildrenNotNull(args));

  if (args[0]->type() != ScoreExpressionType::kDocument) {
    return absl_ports::InvalidArgumentError(
        "propertyWeights must take \"this\" as its argument.");
  }
  return std::unique_ptr<PropertyWeightsFunctionScoreExpression>(
      new PropertyWeightsFunctionScoreExpression(
          document_store, section_weights, current_time_ms));
}

libtextclassifier3::StatusOr<std::vector<double>>
PropertyWeightsFunctionScoreExpression::eval_list(
    const DocHitInfo& hit_info, const DocHitInfoIterator*) const {
  std::vector<double> weights;
  SectionIdMask sections = hit_info.hit_section_ids_mask();
  SchemaTypeId schema_type_id = GetSchemaTypeId(hit_info.document_id());

  while (sections != 0) {
    SectionId section_id = __builtin_ctzll(sections);
    sections &= ~(UINT64_C(1) << section_id);
    weights.push_back(section_weights_.GetNormalizedSectionWeight(
        schema_type_id, section_id));
  }
  return weights;
}

SchemaTypeId PropertyWeightsFunctionScoreExpression::GetSchemaTypeId(
    DocumentId document_id) const {
  auto filter_data_optional =
      document_store_.GetAliveDocumentFilterData(document_id, current_time_ms_);
  if (!filter_data_optional) {
    // This should never happen. The only failure case for
    // GetAliveDocumentFilterData is if the document_id is outside of the range
    // of allocated document_ids, which shouldn't be possible since we're
    // getting this document_id from the posting lists.
    ICING_LOG(WARNING) << "No document filter data for document ["
                       << document_id << "]";
    return kInvalidSchemaTypeId;
  }
  return filter_data_optional.value().schema_type_id();
}

}  // namespace lib
}  // namespace icing
