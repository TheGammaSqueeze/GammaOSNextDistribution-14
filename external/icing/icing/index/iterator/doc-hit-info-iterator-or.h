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

#ifndef ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_OR_H_
#define ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_OR_H_

#include <cstdint>
#include <memory>
#include <string>
#include <utility>

#include "icing/index/iterator/doc-hit-info-iterator.h"

namespace icing {
namespace lib {

// Given n iterators, will decide what the fastest Or-iterator implementation
// will be.
std::unique_ptr<DocHitInfoIterator> CreateOrIterator(
    std::vector<std::unique_ptr<DocHitInfoIterator>> iterators);

// Iterate over a logical OR of two child iterators.
class DocHitInfoIteratorOr : public DocHitInfoIterator {
 public:
  explicit DocHitInfoIteratorOr(std::unique_ptr<DocHitInfoIterator> left_it,
                                std::unique_ptr<DocHitInfoIterator> right_it);

  libtextclassifier3::StatusOr<TrimmedNode> TrimRightMostNode() && override;

  libtextclassifier3::Status Advance() override;

  CallStats GetCallStats() const override {
    return left_->GetCallStats() + right_->GetCallStats();
  }

  std::string ToString() const override;

  void MapChildren(const ChildrenMapper &mapper) override {
    left_ = mapper(std::move(left_));
    right_ = mapper(std::move(right_));
  }

  void PopulateMatchedTermsStats(
      std::vector<TermMatchInfo> *matched_terms_stats,
      SectionIdMask filtering_section_mask = kSectionIdMaskAll) const override {
    if (doc_hit_info_.document_id() == kInvalidDocumentId) {
      // Current hit isn't valid, return.
      return;
    }
    current_->PopulateMatchedTermsStats(matched_terms_stats,
                                        filtering_section_mask);
    // If equal, then current_ == left_. Combine with results from right_.
    if (left_document_id_ == right_document_id_) {
      right_->PopulateMatchedTermsStats(matched_terms_stats,
                                        filtering_section_mask);
    }
  }

 private:
  std::unique_ptr<DocHitInfoIterator> left_;
  std::unique_ptr<DocHitInfoIterator> right_;
  // Pointer to the chosen iterator that points to the current doc_hit_info_. If
  // both left_ and right_ point to the same docid, then chosen_ == left.
  // chosen_ does not own the iterator it points to.
  DocHitInfoIterator *current_;
  DocumentId left_document_id_ = kMaxDocumentId;
  DocumentId right_document_id_ = kMaxDocumentId;
};

// Iterate over a logical OR of multiple child iterators.
//
// NOTE: DocHitInfoIteratorOr is a faster alternative to OR exactly 2 iterators.
class DocHitInfoIteratorOrNary : public DocHitInfoIterator {
 public:
  explicit DocHitInfoIteratorOrNary(
      std::vector<std::unique_ptr<DocHitInfoIterator>> iterators);

  libtextclassifier3::StatusOr<TrimmedNode> TrimRightMostNode() && override;

  libtextclassifier3::Status Advance() override;

  CallStats GetCallStats() const override;

  std::string ToString() const override;

  void MapChildren(const ChildrenMapper &mapper) override {
    for (int i = 0; i < iterators_.size(); ++i) {
      iterators_[i] = mapper(std::move(iterators_[i]));
    }
  }

  void PopulateMatchedTermsStats(
      std::vector<TermMatchInfo> *matched_terms_stats,
      SectionIdMask filtering_section_mask = kSectionIdMaskAll) const override {
    if (doc_hit_info_.document_id() == kInvalidDocumentId) {
      // Current hit isn't valid, return.
      return;
    }
    for (size_t i = 0; i < current_iterators_.size(); i++) {
      current_iterators_.at(i)->PopulateMatchedTermsStats(
          matched_terms_stats, filtering_section_mask);
    }
  }

 private:
  std::vector<std::unique_ptr<DocHitInfoIterator>> iterators_;
  // Pointers to the iterators that point to the current doc_hit_info_.
  // current_iterators_ does not own the iterators it points to.
  std::vector<DocHitInfoIterator *> current_iterators_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_OR_H_
