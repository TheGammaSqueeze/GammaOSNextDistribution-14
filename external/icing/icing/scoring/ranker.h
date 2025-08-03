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

#ifndef ICING_SCORING_RANKER_H_
#define ICING_SCORING_RANKER_H_

#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/index/term-metadata.h"
#include "icing/scoring/scored-document-hit.h"

// Provides functionality to get the top N results from an unsorted vector.
namespace icing {
namespace lib {

// Builds a heap of scored document hits. The same vector is used to store the
// heap structure.
//
// REQUIRED: scored_document_hits is not null.
void BuildHeapInPlace(
    std::vector<ScoredDocumentHit>* scored_document_hits,
    const ScoredDocumentHitComparator& scored_document_hit_comparator);

// Returns the single next top result (i.e. the current root element) from the
// given heap and remove it from the heap. The heap structure will be
// maintained.
//
// Returns:
//   The next top result element on success
//   RESOURCE_EXHAUSTED_ERROR if heap is empty
libtextclassifier3::StatusOr<ScoredDocumentHit> PopNextTopResultFromHeap(
    std::vector<ScoredDocumentHit>* scored_document_hits_heap,
    const ScoredDocumentHitComparator& scored_document_hit_comparator);

// Returns the top num_results results from the given heap and remove those
// results from the heap. An empty vector will be returned if heap is empty.
//
// REQUIRED: scored_document_hits_heap is not null.
std::vector<ScoredDocumentHit> PopTopResultsFromHeap(
    std::vector<ScoredDocumentHit>* scored_document_hits_heap, int num_results,
    const ScoredDocumentHitComparator& scored_document_hit_comparator);

// The heap is a min-heap. So that we can avoid some push operations by
// comparing to the root term, and only pushing if greater than root. The time
// complexity for a single push is O(lgK) which K is the number_to_return.
// REQUIRED: scored_terms_heap is not null.
void PushToTermHeap(TermMetadata term, int number_to_return,
                    std::vector<TermMetadata>& scored_terms_heap);

// Return all terms from the given terms heap. And since the heap is a min-heap,
// the output vector will be increasing order.
// REQUIRED: scored_terms_heap is not null.
std::vector<TermMetadata> PopAllTermsFromHeap(
    std::vector<TermMetadata>& scored_terms_heap);
}  // namespace lib
}  // namespace icing

#endif  // ICING_SCORING_RANKER_H_
