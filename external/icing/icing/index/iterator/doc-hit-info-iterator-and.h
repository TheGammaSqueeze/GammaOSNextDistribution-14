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

#ifndef ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_AND_H_
#define ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_AND_H_

#include <cstdint>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"

namespace icing {
namespace lib {

// Given n iterators, will decide what the fastest And-iterator implementation
// will be.
std::unique_ptr<DocHitInfoIterator> CreateAndIterator(
    std::vector<std::unique_ptr<DocHitInfoIterator>> iterators);

// Iterate over a logical AND of two child iterators.
class DocHitInfoIteratorAnd : public DocHitInfoIterator {
 public:
  // Set the shorter iterator to short_it to get performance benefits
  // for when an underlying iterator has a more efficient AdvanceTo.
  explicit DocHitInfoIteratorAnd(std::unique_ptr<DocHitInfoIterator> short_it,
                                 std::unique_ptr<DocHitInfoIterator> long_it);
  libtextclassifier3::Status Advance() override;

  libtextclassifier3::StatusOr<TrimmedNode> TrimRightMostNode() && override;

  CallStats GetCallStats() const override {
    return short_->GetCallStats() + long_->GetCallStats();
  }

  std::string ToString() const override;

  void MapChildren(const ChildrenMapper& mapper) override {
    short_ = mapper(std::move(short_));
    long_ = mapper(std::move(long_));
  }

  void PopulateMatchedTermsStats(
      std::vector<TermMatchInfo>* matched_terms_stats,
      SectionIdMask filtering_section_mask = kSectionIdMaskAll) const override {
    if (doc_hit_info_.document_id() == kInvalidDocumentId) {
      // Current hit isn't valid, return.
      return;
    }
    short_->PopulateMatchedTermsStats(matched_terms_stats,
                                      filtering_section_mask);
    long_->PopulateMatchedTermsStats(matched_terms_stats,
                                     filtering_section_mask);
  }

 private:
  std::unique_ptr<DocHitInfoIterator> short_;
  std::unique_ptr<DocHitInfoIterator> long_;
};

// Iterate over a logical AND of multiple child iterators.
// NOTE: DocHitInfoIteratorAnd is a faster alternative to AND exactly 2
// iterators.
class DocHitInfoIteratorAndNary : public DocHitInfoIterator {
 public:
  explicit DocHitInfoIteratorAndNary(
      std::vector<std::unique_ptr<DocHitInfoIterator>> iterators);

  libtextclassifier3::Status Advance() override;

  libtextclassifier3::StatusOr<TrimmedNode> TrimRightMostNode() && override;

  CallStats GetCallStats() const override;

  std::string ToString() const override;

  void MapChildren(const ChildrenMapper& mapper) override {
    for (int i = 0; i < iterators_.size(); ++i) {
      iterators_[i] = mapper(std::move(iterators_[i]));
    }
  }

  void PopulateMatchedTermsStats(
      std::vector<TermMatchInfo>* matched_terms_stats,
      SectionIdMask filtering_section_mask = kSectionIdMaskAll) const override {
    if (doc_hit_info_.document_id() == kInvalidDocumentId) {
      // Current hit isn't valid, return.
      return;
    }
    for (size_t i = 0; i < iterators_.size(); ++i) {
      iterators_.at(i)->PopulateMatchedTermsStats(matched_terms_stats,
                                                  filtering_section_mask);
    }
  }

 private:
  std::vector<std::unique_ptr<DocHitInfoIterator>> iterators_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_AND_H_
