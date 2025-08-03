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

#include "icing/scoring/ranker.h"

#include <algorithm>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/scoring/scored-document-hit.h"
#include "icing/util/logging.h"

namespace icing {
namespace lib {

namespace {
// For all the heap manipulations in this file, we use a vector to represent the
// heap. The element at index 0 is the root node. For any node at index i, its
// left child node is at 2 * i + 1, its right child node is at 2 * i + 2.

// Helper function to wrap the heapify algorithm, it heapifies the target
// subtree node in place.
// TODO(b/152934343) refactor the heapify function and making it into a class.
void Heapify(
    std::vector<ScoredDocumentHit>* scored_document_hits,
    int target_subtree_root_index,
    const ScoredDocumentHitComparator& scored_document_hit_comparator) {
  const int heap_size = scored_document_hits->size();
  if (target_subtree_root_index >= heap_size) {
    return;
  }

  // Initializes subtree root as the current best node.
  int best = target_subtree_root_index;
  // If we represent a heap in an array/vector, indices of left and right
  // children can be calculated.
  const int left = target_subtree_root_index * 2 + 1;
  const int right = target_subtree_root_index * 2 + 2;

  // If left child is better than current best
  if (left < heap_size &&
      scored_document_hit_comparator(scored_document_hits->at(left),
                                     scored_document_hits->at(best))) {
    best = left;
  }

  // If right child is better than current best
  if (right < heap_size &&
      scored_document_hit_comparator(scored_document_hits->at(right),
                                     scored_document_hits->at(best))) {
    best = right;
  }

  // If the best is not the subtree root, swap and continue heapifying the lower
  // level subtree
  if (best != target_subtree_root_index) {
    std::swap(scored_document_hits->at(best),
              scored_document_hits->at(target_subtree_root_index));
    Heapify(scored_document_hits, best, scored_document_hit_comparator);
  }
}

// Heapify the given term vector from top to bottom. Call it after add or
// replace an element at the front of the vector.
void HeapifyTermDown(std::vector<TermMetadata>& scored_terms,
                     int target_subtree_root_index) {
  int heap_size = scored_terms.size();
  if (target_subtree_root_index >= heap_size) {
    return;
  }

  // Initializes subtree root as the current minimum node.
  int min = target_subtree_root_index;
  // If we represent a heap in an array/vector, indices of left and right
  // children can be calculated as such.
  const int left = target_subtree_root_index * 2 + 1;
  const int right = target_subtree_root_index * 2 + 2;

  // If left child is smaller than current minimum.
  if (left < heap_size &&
      scored_terms.at(left).score < scored_terms.at(min).score) {
    min = left;
  }

  // If right child is smaller than current minimum.
  if (right < heap_size &&
      scored_terms.at(right).score < scored_terms.at(min).score) {
    min = right;
  }

  // If the minimum is not the subtree root, swap and continue heapifying the
  // lower level subtree.
  if (min != target_subtree_root_index) {
    std::swap(scored_terms.at(min), scored_terms.at(target_subtree_root_index));
    HeapifyTermDown(scored_terms, min);
  }
}

// Heapify the given term vector from bottom to top. Call it after add an
// element at the end of the vector.
void HeapifyTermUp(std::vector<TermMetadata>& scored_terms,
                   int target_subtree_child_index) {
  // If we represent a heap in an array/vector, indices of root can be
  // calculated as such.
  const int root = (target_subtree_child_index + 1) / 2 - 1;

  // If the current child is smaller than the root, swap and continue heapifying
  // the upper level subtree
  if (root >= 0 && scored_terms.at(target_subtree_child_index).score <
                       scored_terms.at(root).score) {
    std::swap(scored_terms.at(root),
              scored_terms.at(target_subtree_child_index));
    HeapifyTermUp(scored_terms, root);
  }
}

TermMetadata PopRootTerm(std::vector<TermMetadata>& scored_terms) {
  if (scored_terms.empty()) {
    // Return an invalid TermMetadata as a sentinel value.
    return TermMetadata(/*content_in=*/"", /*hit_count_in=*/-1);
  }

  // Steps to extract root from heap:
  // 1. copy out root
  TermMetadata root = scored_terms.at(0);
  const size_t last_node_index = scored_terms.size() - 1;
  // 2. swap root and the last node
  std::swap(scored_terms.at(0), scored_terms.at(last_node_index));
  // 3. remove last node
  scored_terms.pop_back();
  // 4. heapify root
  HeapifyTermDown(scored_terms, /*target_subtree_root_index=*/0);
  return root;
}

}  // namespace

void BuildHeapInPlace(
    std::vector<ScoredDocumentHit>* scored_document_hits,
    const ScoredDocumentHitComparator& scored_document_hit_comparator) {
  const int heap_size = scored_document_hits->size();
  // Since we use a vector to represent the heap, [size / 2 - 1] is the index
  // of the parent node of the last node.
  for (int subtree_root_index = heap_size / 2 - 1; subtree_root_index >= 0;
       subtree_root_index--) {
    Heapify(scored_document_hits, subtree_root_index,
            scored_document_hit_comparator);
  }
}

void PushToTermHeap(TermMetadata term, int number_to_return,
                    std::vector<TermMetadata>& scored_terms_heap) {
  if (scored_terms_heap.size() < number_to_return) {
    scored_terms_heap.push_back(std::move(term));
    // We insert at end, so we should heapify bottom up.
    HeapifyTermUp(scored_terms_heap, scored_terms_heap.size() - 1);
  } else if (scored_terms_heap.at(0).score < term.score) {
    scored_terms_heap.at(0) = std::move(term);
    // We insert at root, so we should heapify top down.
    HeapifyTermDown(scored_terms_heap, /*target_subtree_root_index=*/0);
  }
}

libtextclassifier3::StatusOr<ScoredDocumentHit> PopNextTopResultFromHeap(
    std::vector<ScoredDocumentHit>* scored_document_hits_heap,
    const ScoredDocumentHitComparator& scored_document_hit_comparator) {
  if (scored_document_hits_heap->empty()) {
    // An invalid ScoredDocumentHit
    return absl_ports::ResourceExhaustedError("Heap is empty");
  }

  // Steps to extract root from heap:
  // 1. copy out root
  ScoredDocumentHit root = scored_document_hits_heap->at(0);
  const size_t last_node_index = scored_document_hits_heap->size() - 1;
  // 2. swap root and the last node
  std::swap(scored_document_hits_heap->at(0),
            scored_document_hits_heap->at(last_node_index));
  // 3. remove last node
  scored_document_hits_heap->pop_back();
  // 4. heapify root
  Heapify(scored_document_hits_heap, /*target_subtree_root_index=*/0,
          scored_document_hit_comparator);
  return root;
}

std::vector<ScoredDocumentHit> PopTopResultsFromHeap(
    std::vector<ScoredDocumentHit>* scored_document_hits_heap, int num_results,
    const ScoredDocumentHitComparator& scored_document_hit_comparator) {
  std::vector<ScoredDocumentHit> scored_document_hit_result;
  int result_size = std::min(
      num_results, static_cast<int>(scored_document_hits_heap->size()));
  while (result_size-- > 0) {
    libtextclassifier3::StatusOr<ScoredDocumentHit> next_best_document_hit_or =
        PopNextTopResultFromHeap(scored_document_hits_heap,
                                 scored_document_hit_comparator);
    if (next_best_document_hit_or.ok()) {
      scored_document_hit_result.push_back(
          std::move(next_best_document_hit_or).ValueOrDie());
    } else {
      ICING_VLOG(1) << next_best_document_hit_or.status().error_message();
    }
  }
  return scored_document_hit_result;
}

std::vector<TermMetadata> PopAllTermsFromHeap(
    std::vector<TermMetadata>& scored_terms_heap) {
  std::vector<TermMetadata> top_term_result;
  top_term_result.reserve(scored_terms_heap.size());
  while (!scored_terms_heap.empty()) {
    top_term_result.push_back(PopRootTerm(scored_terms_heap));
  }
  return top_term_result;
}

}  // namespace lib
}  // namespace icing
