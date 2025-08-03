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

#ifndef ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_TERM_LITE_H_
#define ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_TERM_LITE_H_

#include <cstdint>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/lite/lite-index.h"
#include "icing/index/term-id-codec.h"
#include "icing/schema/section.h"

namespace icing {
namespace lib {

class DocHitInfoIteratorTermLite : public DocHitInfoLeafIterator {
 public:
  explicit DocHitInfoIteratorTermLite(const TermIdCodec* term_id_codec,
                                      LiteIndex* lite_index,
                                      const std::string& term,
                                      int term_start_index,
                                      int unnormalized_term_length,
                                      SectionIdMask section_restrict_mask,
                                      bool need_hit_term_frequency)
      : term_(term),
        term_start_index_(term_start_index),
        unnormalized_term_length_(unnormalized_term_length),
        lite_index_(lite_index),
        cached_hits_idx_(-1),
        term_id_codec_(term_id_codec),
        num_advance_calls_(0),
        section_restrict_mask_(section_restrict_mask),
        need_hit_term_frequency_(need_hit_term_frequency) {}

  libtextclassifier3::Status Advance() override;

  libtextclassifier3::StatusOr<TrimmedNode> TrimRightMostNode() && override;

  CallStats GetCallStats() const override {
    return CallStats(
        /*num_leaf_advance_calls_lite_index_in=*/num_advance_calls_,
        /*num_leaf_advance_calls_main_index_in=*/0,
        /*num_leaf_advance_calls_integer_index_in=*/0,
        /*num_leaf_advance_calls_no_index_in=*/0,
        /*num_blocks_inspected_in=*/0);
  }

  void PopulateMatchedTermsStats(
      std::vector<TermMatchInfo>* matched_terms_stats,
      SectionIdMask filtering_section_mask = kSectionIdMaskAll) const override {
    if (cached_hits_idx_ == -1 || cached_hits_idx_ >= cached_hits_.size()) {
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
            cached_hit_term_frequency_.at(cached_hits_idx_)[section_id];
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
  // Add DocHitInfos corresponding to term_ to cached_hits_.
  //
  // Returns:
  //   - OK, on success
  //   - NOT_FOUND if no term matching term_ was found in the lexicon.
  //   - INVALID_ARGUMENT if unable to properly encode the termid
  virtual libtextclassifier3::Status RetrieveMoreHits() = 0;

  const std::string term_;
  // The start index of the given term in the search query
  int term_start_index_;
  // The length of the given unnormalized term in the search query
  int unnormalized_term_length_;
  LiteIndex* const lite_index_;
  // Stores hits retrieved from the index. This may only be a subset of the hits
  // that are present in the index. Current value pointed to by the Iterator is
  // tracked by cached_hits_idx_.
  std::vector<DocHitInfo> cached_hits_;
  std::vector<Hit::TermFrequencyArray> cached_hit_term_frequency_;
  int cached_hits_idx_;
  const TermIdCodec* term_id_codec_;
  int num_advance_calls_;
  // Mask indicating which sections hits should be considered for.
  // Ex. 0000 0000 0000 0010 means that only hits from section 1 are desired.
  const SectionIdMask section_restrict_mask_;
  const bool need_hit_term_frequency_;
};

class DocHitInfoIteratorTermLiteExact : public DocHitInfoIteratorTermLite {
 public:
  explicit DocHitInfoIteratorTermLiteExact(const TermIdCodec* term_id_codec,
                                           LiteIndex* lite_index,
                                           const std::string& term,
                                           int term_start_index,
                                           int unnormalized_term_length,
                                           SectionIdMask section_id_mask,
                                           bool need_hit_term_frequency)
      : DocHitInfoIteratorTermLite(term_id_codec, lite_index, term,
                                   term_start_index, unnormalized_term_length,
                                   section_id_mask, need_hit_term_frequency) {}

  std::string ToString() const override;

 protected:
  libtextclassifier3::Status RetrieveMoreHits() override;
};

class DocHitInfoIteratorTermLitePrefix : public DocHitInfoIteratorTermLite {
 public:
  explicit DocHitInfoIteratorTermLitePrefix(const TermIdCodec* term_id_codec,
                                            LiteIndex* lite_index,
                                            const std::string& term,
                                            int term_start_index,
                                            int unnormalized_term_length,
                                            SectionIdMask section_id_mask,
                                            bool need_hit_term_frequency)
      : DocHitInfoIteratorTermLite(term_id_codec, lite_index, term,
                                   term_start_index, unnormalized_term_length,
                                   section_id_mask, need_hit_term_frequency) {}

  std::string ToString() const override;

 protected:
  libtextclassifier3::Status RetrieveMoreHits() override;

 private:
  // After retrieving DocHitInfos from the index, a DocHitInfo for docid 1 and
  // "foo" and a DocHitInfo for docid 1 and "fool". These DocHitInfos should be
  // merged.
  void SortDocumentIds();
  void SortAndDedupeDocumentIds();
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_TERM_LITE_H_
