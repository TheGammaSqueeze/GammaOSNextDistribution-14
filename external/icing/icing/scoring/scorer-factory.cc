// Copyright (C) 2019 Google LLC
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

#include "icing/scoring/scorer-factory.h"

#include <memory>
#include <unordered_map>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/proto/scoring.pb.h"
#include "icing/scoring/advanced_scoring/advanced-scorer.h"
#include "icing/scoring/bm25f-calculator.h"
#include "icing/scoring/scorer.h"
#include "icing/scoring/section-weights.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

class DocumentScoreScorer : public Scorer {
 public:
  explicit DocumentScoreScorer(const DocumentStore* document_store,
                               double default_score)
      : document_store_(*document_store), default_score_(default_score) {}

  double GetScore(const DocHitInfo& hit_info,
                  const DocHitInfoIterator*) override {
    ICING_ASSIGN_OR_RETURN(
        DocumentAssociatedScoreData score_data,
        document_store_.GetDocumentAssociatedScoreData(hit_info.document_id()),
        default_score_);

    return static_cast<double>(score_data.document_score());
  }

 private:
  const DocumentStore& document_store_;
  double default_score_;
};

class DocumentCreationTimestampScorer : public Scorer {
 public:
  explicit DocumentCreationTimestampScorer(const DocumentStore* document_store,
                                           double default_score)
      : document_store_(*document_store), default_score_(default_score) {}

  double GetScore(const DocHitInfo& hit_info,
                  const DocHitInfoIterator*) override {
    ICING_ASSIGN_OR_RETURN(
        DocumentAssociatedScoreData score_data,
        document_store_.GetDocumentAssociatedScoreData(hit_info.document_id()),
        default_score_);

    return static_cast<double>(score_data.creation_timestamp_ms());
  }

 private:
  const DocumentStore& document_store_;
  double default_score_;
};

class RelevanceScoreScorer : public Scorer {
 public:
  explicit RelevanceScoreScorer(
      std::unique_ptr<SectionWeights> section_weights,
      std::unique_ptr<Bm25fCalculator> bm25f_calculator, double default_score)
      : section_weights_(std::move(section_weights)),
        bm25f_calculator_(std::move(bm25f_calculator)),
        default_score_(default_score) {}

  void PrepareToScore(
      std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>*
          query_term_iterators) override {
    bm25f_calculator_->PrepareToScore(query_term_iterators);
  }

  double GetScore(const DocHitInfo& hit_info,
                  const DocHitInfoIterator* query_it) override {
    if (!query_it) {
      return default_score_;
    }

    return static_cast<double>(
        bm25f_calculator_->ComputeScore(query_it, hit_info, default_score_));
  }

 private:
  std::unique_ptr<SectionWeights> section_weights_;
  std::unique_ptr<Bm25fCalculator> bm25f_calculator_;
  double default_score_;
};

// A scorer which assigns scores to documents based on usage reports.
class UsageScorer : public Scorer {
 public:
  UsageScorer(const DocumentStore* document_store,
              ScoringSpecProto::RankingStrategy::Code ranking_strategy,
              double default_score, int64_t current_time_ms)
      : document_store_(*document_store),
        ranking_strategy_(ranking_strategy),
        default_score_(default_score),
        current_time_ms_(current_time_ms) {}

  double GetScore(const DocHitInfo& hit_info,
                  const DocHitInfoIterator*) override {
    std::optional<UsageStore::UsageScores> usage_scores =
        document_store_.GetUsageScores(hit_info.document_id(),
                                       current_time_ms_);
    if (!usage_scores) {
      // If there's no UsageScores entry present for this doc, then just
      // treat it as a default instance.
      usage_scores = UsageStore::UsageScores();
    }

    switch (ranking_strategy_) {
      case ScoringSpecProto::RankingStrategy::USAGE_TYPE1_COUNT:
        return usage_scores->usage_type1_count;
      case ScoringSpecProto::RankingStrategy::USAGE_TYPE2_COUNT:
        return usage_scores->usage_type2_count;
      case ScoringSpecProto::RankingStrategy::USAGE_TYPE3_COUNT:
        return usage_scores->usage_type3_count;
      case ScoringSpecProto::RankingStrategy::USAGE_TYPE1_LAST_USED_TIMESTAMP:
        return usage_scores->usage_type1_last_used_timestamp_s * 1000.0;
      case ScoringSpecProto::RankingStrategy::USAGE_TYPE2_LAST_USED_TIMESTAMP:
        return usage_scores->usage_type2_last_used_timestamp_s * 1000.0;
      case ScoringSpecProto::RankingStrategy::USAGE_TYPE3_LAST_USED_TIMESTAMP:
        return usage_scores->usage_type3_last_used_timestamp_s * 1000.0;
      default:
        // This shouldn't happen if this scorer is used correctly.
        return default_score_;
    }
  }

 private:
  const DocumentStore& document_store_;
  ScoringSpecProto::RankingStrategy::Code ranking_strategy_;
  double default_score_;
  int64_t current_time_ms_;
};

// A special scorer which does nothing but assigns the default score to each
// document. This is used especially when no scoring is required in a query.
class NoScorer : public Scorer {
 public:
  explicit NoScorer(double default_score) : default_score_(default_score) {}

  double GetScore(const DocHitInfo& hit_info,
                  const DocHitInfoIterator*) override {
    return default_score_;
  }

 private:
  double default_score_;
};

namespace scorer_factory {

libtextclassifier3::StatusOr<std::unique_ptr<Scorer>> Create(
    const ScoringSpecProto& scoring_spec, double default_score,
    const DocumentStore* document_store, const SchemaStore* schema_store,
    int64_t current_time_ms, const JoinChildrenFetcher* join_children_fetcher) {
  ICING_RETURN_ERROR_IF_NULL(document_store);
  ICING_RETURN_ERROR_IF_NULL(schema_store);

  if (!scoring_spec.advanced_scoring_expression().empty() &&
      scoring_spec.rank_by() !=
          ScoringSpecProto::RankingStrategy::ADVANCED_SCORING_EXPRESSION) {
    return absl_ports::InvalidArgumentError(
        "Advanced scoring is not enabled, but the advanced scoring expression "
        "is not empty!");
  }

  switch (scoring_spec.rank_by()) {
    case ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE:
      return std::make_unique<DocumentScoreScorer>(document_store,
                                                   default_score);
    case ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP:
      return std::make_unique<DocumentCreationTimestampScorer>(document_store,
                                                               default_score);
    case ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE: {
      ICING_ASSIGN_OR_RETURN(
          std::unique_ptr<SectionWeights> section_weights,
          SectionWeights::Create(schema_store, scoring_spec));

      auto bm25f_calculator = std::make_unique<Bm25fCalculator>(
          document_store, section_weights.get(), current_time_ms);
      return std::make_unique<RelevanceScoreScorer>(std::move(section_weights),
                                                    std::move(bm25f_calculator),
                                                    default_score);
    }
    case ScoringSpecProto::RankingStrategy::USAGE_TYPE1_COUNT:
      [[fallthrough]];
    case ScoringSpecProto::RankingStrategy::USAGE_TYPE2_COUNT:
      [[fallthrough]];
    case ScoringSpecProto::RankingStrategy::USAGE_TYPE3_COUNT:
      [[fallthrough]];
    case ScoringSpecProto::RankingStrategy::USAGE_TYPE1_LAST_USED_TIMESTAMP:
      [[fallthrough]];
    case ScoringSpecProto::RankingStrategy::USAGE_TYPE2_LAST_USED_TIMESTAMP:
      [[fallthrough]];
    case ScoringSpecProto::RankingStrategy::USAGE_TYPE3_LAST_USED_TIMESTAMP:
      return std::make_unique<UsageScorer>(document_store,
                                           scoring_spec.rank_by(),
                                           default_score, current_time_ms);
    case ScoringSpecProto::RankingStrategy::ADVANCED_SCORING_EXPRESSION:
      if (scoring_spec.advanced_scoring_expression().empty()) {
        return absl_ports::InvalidArgumentError(
            "Advanced scoring is enabled, but the expression is empty!");
      }
      return AdvancedScorer::Create(scoring_spec, default_score, document_store,
                                    schema_store, current_time_ms,
                                    join_children_fetcher);
    case ScoringSpecProto::RankingStrategy::JOIN_AGGREGATE_SCORE:
      // Use join aggregate score to rank. Since the aggregation score is
      // calculated by child documents after joining (in JoinProcessor), we can
      // simply use NoScorer for parent documents.
      [[fallthrough]];
    case ScoringSpecProto::RankingStrategy::NONE:
      return std::make_unique<NoScorer>(default_score);
  }
}

}  // namespace scorer_factory

}  // namespace lib
}  // namespace icing
