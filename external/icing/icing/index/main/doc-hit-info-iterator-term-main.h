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

#ifndef ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_TERM_MAIN_H_
#define ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_TERM_MAIN_H_

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/hit/hit.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/main/main-index.h"
#include "icing/index/main/posting-list-hit-accessor.h"
#include "icing/schema/section.h"

namespace icing {
namespace lib {

class DocHitInfoIteratorTermMain : public DocHitInfoLeafIterator {
 public:
  struct DocHitInfoAndTermFrequencyArray {
    DocHitInfo doc_hit_info;
    std::optional<Hit::TermFrequencyArray> term_frequency_array;

    explicit DocHitInfoAndTermFrequencyArray() = default;

    explicit DocHitInfoAndTermFrequencyArray(
        DocHitInfo doc_hit_info_in,
        std::optional<Hit::TermFrequencyArray> term_frequency_array_in)
        : doc_hit_info(std::move(doc_hit_info_in)),
          term_frequency_array(std::move(term_frequency_array_in)) {}
  };

  explicit DocHitInfoIteratorTermMain(MainIndex* main_index,
                                      const std::string& term,
                                      int term_start_index,
                                      int unnormalized_term_length,
                                      SectionIdMask section_restrict_mask,
                                      bool need_hit_term_frequency)
      : term_(term),
        term_start_index_(term_start_index),
        unnormalized_term_length_(unnormalized_term_length),
        posting_list_accessor_(nullptr),
        main_index_(main_index),
        cached_doc_hit_infos_idx_(-1),
        num_advance_calls_(0),
        num_blocks_inspected_(0),
        all_pages_consumed_(false),
        section_restrict_mask_(section_restrict_mask),
        need_hit_term_frequency_(need_hit_term_frequency) {}

  libtextclassifier3::Status Advance() override;

  libtextclassifier3::StatusOr<TrimmedNode> TrimRightMostNode() && override;

  CallStats GetCallStats() const override {
    return CallStats(
        /*num_leaf_advance_calls_lite_index_in=*/0,
        /*num_leaf_advance_calls_main_index_in=*/num_advance_calls_,
        /*num_leaf_advance_calls_integer_index_in=*/0,
        /*num_leaf_advance_calls_no_index_in=*/0,
        /*num_blocks_inspected_in=*/num_blocks_inspected_);
  }

  void PopulateMatchedTermsStats(
      std::vector<TermMatchInfo>* matched_terms_stats,
      SectionIdMask filtering_section_mask = kSectionIdMaskAll) const override {
    if (cached_doc_hit_infos_idx_ == -1 ||
        cached_doc_hit_infos_idx_ >= cached_doc_hit_infos_.size()) {
      // Current hit isn't valid, return.
      return;
    }
    SectionIdMask section_mask =
        doc_hit_info_.hit_section_ids_mask() & filtering_section_mask;
    SectionIdMask section_mask_copy = section_mask;
    std::array<Hit::TermFrequency, kTotalNumSections> section_term_frequencies =
        {Hit::kNoTermFrequency};
    while (section_mask_copy) {
      SectionId section_id = __builtin_ctzll(section_mask_copy);
      if (need_hit_term_frequency_) {
        section_term_frequencies.at(section_id) =
            (*cached_doc_hit_infos_.at(cached_doc_hit_infos_idx_)
                  .term_frequency_array)[section_id];
      }
      section_mask_copy &= ~(UINT64_C(1) << section_id);
    }
    TermMatchInfo term_stats(term_, section_mask,
                             std::move(section_term_frequencies));

    for (const TermMatchInfo& cur_term_stats : *matched_terms_stats) {
      if (cur_term_stats.term == term_stats.term) {
        // Same docId and same term, we don't need to add the term and the term
        // frequency should always be the same
        return;
      }
    }
    matched_terms_stats->push_back(std::move(term_stats));
  }

 protected:
  // Add DocHitInfos corresponding to term_ to cached_doc_hit_infos_.
  virtual libtextclassifier3::Status RetrieveMoreHits() = 0;

  const std::string term_;

  // The start index of the given term in the search query
  int term_start_index_;
  // The length of the given unnormalized term in the search query
  int unnormalized_term_length_;
  // The accessor of the posting list chain for the requested term.
  std::unique_ptr<PostingListHitAccessor> posting_list_accessor_;

  MainIndex* main_index_;
  // Stores hits and optional term frequency arrays retrieved from the index.
  // This may only be a subset of the hits that are present in the index.
  // Current value pointed to by the Iterator is tracked by
  // cached_doc_hit_infos_idx_.
  std::vector<DocHitInfoAndTermFrequencyArray> cached_doc_hit_infos_;
  int cached_doc_hit_infos_idx_;

  int num_advance_calls_;
  int num_blocks_inspected_;
  bool all_pages_consumed_;
  // Mask indicating which sections hits should be considered for.
  // Ex. 0000 0000 0000 0010 means that only hits from section 1 are desired.
  const SectionIdMask section_restrict_mask_;
  const bool need_hit_term_frequency_;

 private:
  // Remaining number of hits including the current hit.
  // Returns -1 if cached_doc_hit_infos_idx_ is invalid.
  int cached_doc_hit_info_count() const {
    if (cached_doc_hit_infos_idx_ == -1 ||
        cached_doc_hit_infos_idx_ >= cached_doc_hit_infos_.size()) {
      return -1;
    }
    return cached_doc_hit_infos_.size() - cached_doc_hit_infos_idx_;
  }
};

class DocHitInfoIteratorTermMainExact : public DocHitInfoIteratorTermMain {
 public:
  explicit DocHitInfoIteratorTermMainExact(MainIndex* main_index,
                                           const std::string& term,
                                           int term_start_index,
                                           int unnormalized_term_length,
                                           SectionIdMask section_restrict_mask,
                                           bool need_hit_term_frequency)
      : DocHitInfoIteratorTermMain(
            main_index, term, term_start_index, unnormalized_term_length,
            section_restrict_mask, need_hit_term_frequency) {}

  std::string ToString() const override;

 protected:
  libtextclassifier3::Status RetrieveMoreHits() override;
};

class DocHitInfoIteratorTermMainPrefix : public DocHitInfoIteratorTermMain {
 public:
  explicit DocHitInfoIteratorTermMainPrefix(MainIndex* main_index,
                                            const std::string& term,
                                            int term_start_index,
                                            int unnormalized_term_length,
                                            SectionIdMask section_restrict_mask,
                                            bool need_hit_term_frequency)
      : DocHitInfoIteratorTermMain(
            main_index, term, term_start_index, unnormalized_term_length,
            section_restrict_mask, need_hit_term_frequency) {}

  std::string ToString() const override;

 protected:
  libtextclassifier3::Status RetrieveMoreHits() override;

 private:
  // Whether or not posting_list_accessor_ holds a posting list chain for
  // 'term' or for a term for which 'term' is a prefix. This is necessary to
  // determine whether to return hits that are not from a prefix section (hits
  // not from a prefix section should only be returned if exact_ is true).
  bool exact_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_TERM_MAIN_H_
