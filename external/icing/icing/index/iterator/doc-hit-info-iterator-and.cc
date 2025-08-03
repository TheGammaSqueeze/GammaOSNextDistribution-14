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

#include "icing/index/iterator/doc-hit-info-iterator-and.h"

#include <cstddef>
#include <cstdint>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

// When combining ANDed iterators, n-ary operator has better performance when
// number of operands > 3 according to benchmark cl/243720660
inline constexpr int kBinaryAndIteratorPerformanceThreshold = 3;

// The minimum number of iterators needed to construct a And iterator. The And
// constructor currently takes 2 iterators.
inline constexpr int kMinBinaryIterators = 2;

}  // namespace

std::unique_ptr<DocHitInfoIterator> CreateAndIterator(
    std::vector<std::unique_ptr<DocHitInfoIterator>> iterators) {
  if (iterators.size() == 1) {
    return std::move(iterators.at(0));
  }

  std::unique_ptr<DocHitInfoIterator> iterator;
  if (iterators.size() <= kBinaryAndIteratorPerformanceThreshold &&
      iterators.size() >= kMinBinaryIterators) {
    // Accumulate the iterators that need to be ANDed together.
    iterator = std::move(iterators.at(iterators.size() - 1));
    for (int i = iterators.size() - 2; i >= 0; --i) {
      std::unique_ptr<DocHitInfoIterator> temp_iterator = std::move(iterator);
      iterator = std::make_unique<DocHitInfoIteratorAnd>(
          /*short_it=*/std::move(iterators[i]),
          /*long_it=*/std::move(temp_iterator));
    }
  } else {
    // If the vector is too small, the AndNary iterator can handle it and return
    // an error on the Advance call
    iterator =
        std::make_unique<DocHitInfoIteratorAndNary>(std::move(iterators));
  }

  return iterator;
}

DocHitInfoIteratorAnd::DocHitInfoIteratorAnd(
    std::unique_ptr<DocHitInfoIterator> short_it,
    std::unique_ptr<DocHitInfoIterator> long_it)
    : short_(std::move(short_it)), long_(std::move(long_it)) {}

libtextclassifier3::Status DocHitInfoIteratorAnd::Advance() {
  // Advance on short first
  if (!short_->Advance().ok()) {
    // Didn't find anything for the first iterator, reset to invalid values and
    // return.
    doc_hit_info_ = DocHitInfo(kInvalidDocumentId);
    return absl_ports::ResourceExhaustedError(
        "No more DocHitInfos in iterator");
  }
  DocumentId short_doc_id = short_->doc_hit_info().document_id();

  // Then AdvanceTo on long
  ICING_ASSIGN_OR_RETURN(DocumentId long_doc_id,
                         AdvanceTo(long_.get(), short_doc_id));

  // Now try to align DocHitInfos by moving one or the other.
  while (short_doc_id != long_doc_id) {
    if (short_doc_id > long_doc_id) {
      ICING_ASSIGN_OR_RETURN(short_doc_id,
                             AdvanceTo(short_.get(), long_doc_id));
    } else {
      ICING_ASSIGN_OR_RETURN(long_doc_id, AdvanceTo(long_.get(), short_doc_id));
    }
  }

  // Guaranteed that short_doc_id and long_doc_id match now
  doc_hit_info_ = short_->doc_hit_info();
  doc_hit_info_.MergeSectionsFrom(long_->doc_hit_info().hit_section_ids_mask());
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<DocHitInfoIterator::TrimmedNode>
DocHitInfoIteratorAnd::TrimRightMostNode() && {
  ICING_ASSIGN_OR_RETURN(TrimmedNode trimmed_long,
                         std::move(*long_).TrimRightMostNode());
  if (trimmed_long.iterator_ == nullptr) {
    trimmed_long.iterator_ = std::move(short_);
  } else {
    trimmed_long.iterator_ = std::make_unique<DocHitInfoIteratorAnd>(
        std::move(short_), std::move(trimmed_long.iterator_));
  }
  return trimmed_long;
}

std::string DocHitInfoIteratorAnd::ToString() const {
  return absl_ports::StrCat("(", short_->ToString(), " AND ", long_->ToString(),
                            ")");
}

DocHitInfoIteratorAndNary::DocHitInfoIteratorAndNary(
    std::vector<std::unique_ptr<DocHitInfoIterator>> iterators)
    : iterators_(std::move(iterators)) {}

libtextclassifier3::Status DocHitInfoIteratorAndNary::Advance() {
  if (iterators_.size() < 2) {
    return absl_ports::InvalidArgumentError(
        "Not enough iterators to AND together");
  }

  // Advance on the first iterator to get a potential hit
  if (!iterators_.at(0)->Advance().ok()) {
    // Didn't find anything for the first iterator, reset to invalid values and
    // return
    doc_hit_info_ = DocHitInfo(kInvalidDocumentId);
    return absl_ports::ResourceExhaustedError(
        "No more DocHitInfos in iterator");
  }
  DocumentId potential_document_id =
      iterators_.at(0)->doc_hit_info().document_id();

  // Our goal is to find the next document_id that exists on all the iterators
  // by advancing the iterators one by one. We start with some
  // "potential_document_id", check if it actually matches the above goal. If
  // yes, return. If not, find the next best "potential" and repeat till we hit
  // the end.

  // Has the current potential_document_id been found in all the iterators?
  bool found_document_id = false;
  while (!found_document_id) {
    for (auto& iterator : iterators_) {
      if (iterator->doc_hit_info().document_id() > potential_document_id) {
        // Advance the current iterator until it's equal to or smaller than the
        // potential hit doc id
        DocumentId unused;
        ICING_ASSIGN_OR_RETURN(
            unused, AdvanceTo(iterator.get(), potential_document_id));
        (void)unused;  // Silence unused warning.
      }

      if (iterator->doc_hit_info().document_id() == potential_document_id) {
        // The potential hit got matched on the iterators so far
        found_document_id = true;
        continue;
      } else if (iterator->doc_hit_info().document_id() <
                 potential_document_id) {
        // This iterator doesn't have potential_document_id as we've gone past
        // it already. Use the current document_id as the new
        // "potential_document_id" and start checking all iterators again.
        found_document_id = false;
        potential_document_id = iterator->doc_hit_info().document_id();
        break;
      }
    }
  }

  // Found a DocumentId which exists in all the iterators
  doc_hit_info_ = iterators_.at(0)->doc_hit_info();

  for (size_t i = 1; i < iterators_.size(); i++) {
    doc_hit_info_.MergeSectionsFrom(
        iterators_.at(i)->doc_hit_info().hit_section_ids_mask());
  }
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<DocHitInfoIterator::TrimmedNode>
DocHitInfoIteratorAndNary::TrimRightMostNode() && {
  ICING_ASSIGN_OR_RETURN(
      TrimmedNode trimmed_right,
      std::move(*iterators_.rbegin()->get()).TrimRightMostNode());
  if (trimmed_right.iterator_ == nullptr) {
    if (iterators_.size() > 2) {
      iterators_.pop_back();
      trimmed_right.iterator_ =
          std::make_unique<DocHitInfoIteratorAndNary>(std::move(iterators_));
    } else if (iterators_.size() == 2) {
      trimmed_right.iterator_ = std::move(iterators_.at(0));
    }
  } else {
    iterators_.at(iterators_.size() - 1) = std::move(trimmed_right.iterator_);
    trimmed_right.iterator_ =
        std::make_unique<DocHitInfoIteratorAndNary>(std::move(iterators_));
  }
  return trimmed_right;
}

DocHitInfoIterator::CallStats DocHitInfoIteratorAndNary::GetCallStats() const {
  CallStats call_stats;
  for (const auto& iter : iterators_) {
    call_stats += iter->GetCallStats();
  }
  return call_stats;
}

std::string DocHitInfoIteratorAndNary::ToString() const {
  std::string ret = "(";

  for (int i = 0; i < iterators_.size(); ++i) {
    if (i == iterators_.size() - 1) {
      // Last element in vector
      absl_ports::StrAppend(&ret, iterators_.at(i)->ToString(), ")");
    } else {
      absl_ports::StrAppend(&ret, iterators_.at(i)->ToString(), " AND ");
    }
  }

  return ret;
}

}  // namespace lib
}  // namespace icing
