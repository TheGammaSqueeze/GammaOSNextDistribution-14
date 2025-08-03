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

#include "icing/index/iterator/doc-hit-info-iterator-or.h"

#include <cstdint>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/store/document-id.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

// When combining Or iterators, n-ary operator has better performance when
// number of operands > 2 according to benchmark cl/243321264
constexpr int kBinaryOrIteratorPerformanceThreshold = 2;

}  // namespace

std::unique_ptr<DocHitInfoIterator> CreateOrIterator(
    std::vector<std::unique_ptr<DocHitInfoIterator>> iterators) {
  if (iterators.size() == 1) {
    return std::move(iterators.at(0));
  }

  std::unique_ptr<DocHitInfoIterator> iterator;
  if (iterators.size() == kBinaryOrIteratorPerformanceThreshold) {
    iterator = std::make_unique<DocHitInfoIteratorOr>(std::move(iterators[0]),
                                                      std::move(iterators[1]));
  } else {
    // If the vector is too small, the OrNary iterator can handle it and return
    // an error on the Advance call
    iterator = std::make_unique<DocHitInfoIteratorOrNary>(std::move(iterators));
  }

  return iterator;
}

DocHitInfoIteratorOr::DocHitInfoIteratorOr(
    std::unique_ptr<DocHitInfoIterator> left_it,
    std::unique_ptr<DocHitInfoIterator> right_it)
    : left_(std::move(left_it)), right_(std::move(right_it)) {}

libtextclassifier3::StatusOr<DocHitInfoIterator::TrimmedNode>
DocHitInfoIteratorOr::TrimRightMostNode() && {
  // Trim the whole OR iterator. Only keep the prefix of the right iterator.
  //
  // The OR operator has higher priority, it is not possible that we have an
  // unfinished prefix in the nested iterator right-most child we need to search
  // suggestion for.
  //
  // eg: `foo OR (bar baz)` is not valid for search suggestion since there is no
  // unfinished last term to be filled.
  //
  // If we need to trim a OR iterator for search suggestion, the right child
  // must be the last term. We don't need left side information to
  // generate suggestion for the right side.
  ICING_ASSIGN_OR_RETURN(TrimmedNode trimmed_right,
                         std::move(*right_).TrimRightMostNode());
  trimmed_right.iterator_ = nullptr;
  return trimmed_right;
}

libtextclassifier3::Status DocHitInfoIteratorOr::Advance() {
  // Cache the document_id of the left iterator for comparison to the right.
  DocumentId orig_left_document_id = left_document_id_;

  // Advance the left iterator if necessary.
  if (left_document_id_ != kInvalidDocumentId) {
    if (right_document_id_ == kInvalidDocumentId ||
        left_document_id_ >= right_document_id_) {
      if (left_->Advance().ok()) {
        left_document_id_ = left_->doc_hit_info().document_id();
      } else {
        left_document_id_ = kInvalidDocumentId;
      }
    }
  }

  // Advance the right iterator if necessary, by comparing to the original
  // left document_id (not the one which may have been updated).
  if (right_document_id_ != kInvalidDocumentId) {
    if (orig_left_document_id == kInvalidDocumentId ||
        right_document_id_ >= orig_left_document_id) {
      if (right_->Advance().ok()) {
        right_document_id_ = right_->doc_hit_info().document_id();
      } else {
        right_document_id_ = kInvalidDocumentId;
      }
    }
  }

  // Done, we either found a match or we reached the end of potential
  // DocHitInfos
  if (left_document_id_ == kInvalidDocumentId &&
      right_document_id_ == kInvalidDocumentId) {
    // Reached the end, set these to invalid values and return
    doc_hit_info_ = DocHitInfo(kInvalidDocumentId);
    return absl_ports::ResourceExhaustedError(
        "No more DocHitInfos in iterator");
  }

  // Now chose the best one that is not invalid.
  DocHitInfoIterator* chosen;
  if (left_document_id_ == kInvalidDocumentId) {
    chosen = right_.get();
  } else if (right_document_id_ == kInvalidDocumentId) {
    chosen = left_.get();
  } else if (left_document_id_ < right_document_id_) {
    chosen = right_.get();
  } else {
    chosen = left_.get();
  }
  current_ = chosen;

  doc_hit_info_ = chosen->doc_hit_info();

  // If equal, combine.
  if (left_document_id_ == right_document_id_) {
    doc_hit_info_.MergeSectionsFrom(
        right_->doc_hit_info().hit_section_ids_mask());
  }

  return libtextclassifier3::Status::OK;
}

std::string DocHitInfoIteratorOr::ToString() const {
  return absl_ports::StrCat("(", left_->ToString(), " OR ", right_->ToString(),
                            ")");
}

DocHitInfoIteratorOrNary::DocHitInfoIteratorOrNary(
    std::vector<std::unique_ptr<DocHitInfoIterator>> iterators)
    : iterators_(std::move(iterators)) {}

libtextclassifier3::StatusOr<DocHitInfoIterator::TrimmedNode>
DocHitInfoIteratorOrNary::TrimRightMostNode() && {
  // Trim the whole OR iterator.
  //
  // The OR operator has higher priority, it is not possible that we have an
  // unfinished prefix in the nested iterator right-most child we need to search
  // suggestion for.
  //
  // eg: `foo OR (bar baz)` is not valid for search suggestion since there is no
  // unfinished last term to be filled.
  //
  // If we need to trim a OR iterator for search suggestion, the right-most
  // child must be the last term. We don't need left side information to
  // generate suggestion for the right side.
  ICING_ASSIGN_OR_RETURN(TrimmedNode trimmed_right,
                         std::move(*iterators_.back()).TrimRightMostNode());
  trimmed_right.iterator_ = nullptr;
  return trimmed_right;
}

libtextclassifier3::Status DocHitInfoIteratorOrNary::Advance() {
  current_iterators_.clear();
  if (iterators_.size() < 2) {
    return absl_ports::InvalidArgumentError(
        "Not enough iterators to OR together");
  }

  if (doc_hit_info_.document_id() == 0) {
    // 0 is the smallest (last) DocumentId, can't advance further. Reset to
    // invalid values and return directly
    doc_hit_info_ = DocHitInfo(kInvalidDocumentId);
    return absl_ports::ResourceExhaustedError(
        "No more DocHitInfos in iterator");
  }
  // The maximum possible doc id for the current Advance() call.
  const DocumentId next_document_id_max = doc_hit_info_.document_id() - 1;
  doc_hit_info_ = DocHitInfo(kInvalidDocumentId);
  DocumentId next_document_id = kInvalidDocumentId;
  // Go through the iterators and try to find the maximum document_id that is
  // equal to or smaller than next_document_id_max
  for (const auto& iterator : iterators_) {
    if (iterator->doc_hit_info().document_id() > next_document_id_max) {
      // Advance the iterator until its value is equal to or smaller than
      // next_document_id_max
      if (!AdvanceTo(iterator.get(), next_document_id_max).ok()) {
        continue;
      }
    }
    // Now iterator->get_document_id() <= next_document_id_max
    if (next_document_id == kInvalidDocumentId) {
      next_document_id = iterator->doc_hit_info().document_id();
    } else {
      next_document_id =
          std::max(next_document_id, iterator->doc_hit_info().document_id());
    }
  }
  if (next_document_id == kInvalidDocumentId) {
    // None of the iterators had a next document_id, reset to invalid values and
    // return
    doc_hit_info_ = DocHitInfo(kInvalidDocumentId);
    return absl_ports::ResourceExhaustedError(
        "No more DocHitInfos in iterator");
  }

  // Found the next hit DocumentId, now calculate the section info.
  for (const auto& iterator : iterators_) {
    if (iterator->doc_hit_info().document_id() == next_document_id) {
      current_iterators_.push_back(iterator.get());
      if (doc_hit_info_.document_id() == kInvalidDocumentId) {
        doc_hit_info_ = iterator->doc_hit_info();
      } else {
        doc_hit_info_.MergeSectionsFrom(
            iterator->doc_hit_info().hit_section_ids_mask());
      }
    }
  }
  return libtextclassifier3::Status::OK;
}

DocHitInfoIterator::CallStats DocHitInfoIteratorOrNary::GetCallStats() const {
  CallStats call_stats;
  for (const auto& iter : iterators_) {
    call_stats += iter->GetCallStats();
  }
  return call_stats;
}

std::string DocHitInfoIteratorOrNary::ToString() const {
  std::string ret = "(";

  for (int i = 0; i < iterators_.size(); ++i) {
    absl_ports::StrAppend(&ret, iterators_.at(i)->ToString());
    if (i != iterators_.size() - 1) {
      // Not the last element in vector
      absl_ports::StrAppend(&ret, " OR ");
    }
  }

  absl_ports::StrAppend(&ret, ")");
  return ret;
}

}  // namespace lib
}  // namespace icing
