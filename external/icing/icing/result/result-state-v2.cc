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

#include "icing/result/result-state-v2.h"

#include <atomic>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include "icing/proto/search.pb.h"
#include "icing/result/result-adjustment-info.h"
#include "icing/scoring/scored-document-hits-ranker.h"
#include "icing/store/document-store.h"

namespace icing {
namespace lib {

ResultStateV2::ResultStateV2(
    std::unique_ptr<ScoredDocumentHitsRanker> scored_document_hits_ranker_in,
    std::unique_ptr<ResultAdjustmentInfo> parent_adjustment_info,
    std::unique_ptr<ResultAdjustmentInfo> child_adjustment_info,
    const ResultSpecProto& result_spec, const DocumentStore& document_store)
    : scored_document_hits_ranker(std::move(scored_document_hits_ranker_in)),
      num_returned(0),
      parent_adjustment_info_(std::move(parent_adjustment_info)),
      child_adjustment_info_(std::move(child_adjustment_info)),
      num_per_page_(result_spec.num_per_page()),
      num_total_bytes_per_page_threshold_(
          result_spec.num_total_bytes_per_page_threshold()),
      max_joined_children_per_parent_to_return_(
          result_spec.max_joined_children_per_parent_to_return()),
      num_total_hits_(nullptr),
      result_group_type_(result_spec.result_group_type()) {
  for (const ResultSpecProto::ResultGrouping& result_grouping :
       result_spec.result_groupings()) {
    int group_id = group_result_limits.size();
    group_result_limits.push_back(result_grouping.max_results());
    for (const ResultSpecProto::ResultGrouping::Entry& entry :
         result_grouping.entry_groupings()) {
      const std::string& name_space = entry.namespace_();
      const std::string& schema = entry.schema();
      auto entry_id_or = document_store.GetResultGroupingEntryId(
          result_group_type_, name_space, schema);
      if (!entry_id_or.ok()) {
        continue;
      }
      int32_t entry_id = entry_id_or.ValueOrDie();
      entry_id_group_id_map_.insert({entry_id, group_id});
    }
  }
}

ResultStateV2::~ResultStateV2() {
  IncrementNumTotalHits(-1 * scored_document_hits_ranker->size());
}

void ResultStateV2::RegisterNumTotalHits(std::atomic<int>* num_total_hits) {
  // Decrement the original num_total_hits_ before registering a new one.
  IncrementNumTotalHits(-1 * scored_document_hits_ranker->size());
  num_total_hits_ = num_total_hits;
  IncrementNumTotalHits(scored_document_hits_ranker->size());
}

void ResultStateV2::IncrementNumTotalHits(int increment_by) {
  if (num_total_hits_ != nullptr) {
    *num_total_hits_ += increment_by;
  }
}

}  // namespace lib
}  // namespace icing
