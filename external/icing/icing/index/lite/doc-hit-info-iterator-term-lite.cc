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

#include "icing/index/lite/doc-hit-info-iterator-term-lite.h"

#include <array>
#include <cstddef>
#include <cstdint>
#include <numeric>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/schema/section.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

std::string SectionIdMaskToString(SectionIdMask section_id_mask) {
  std::string mask(kTotalNumSections, '0');
  for (SectionId i = kMaxSectionId; i >= 0; --i) {
    if (section_id_mask & (UINT64_C(1) << i)) {
      mask[kMaxSectionId - i] = '1';
    }
  }
  return mask;
}

}  // namespace

libtextclassifier3::Status DocHitInfoIteratorTermLite::Advance() {
  if (cached_hits_idx_ == -1) {
    libtextclassifier3::Status status = RetrieveMoreHits();
    if (!status.ok()) {
      if (!absl_ports::IsNotFound(status)) {
        // NOT_FOUND is expected to happen (not every term will be in the main
        // index!). Other errors are worth logging.
        ICING_LOG(ERROR)
            << "Encountered unexpected failure while retrieving  hits "
            << status.error_message();
      }
      return absl_ports::ResourceExhaustedError(
          "No more DocHitInfos in iterator");
    }
  } else {
    ++cached_hits_idx_;
  }
  if (cached_hits_idx_ == -1 || cached_hits_idx_ >= cached_hits_.size()) {
    // Nothing more for the iterator to return. Set these members to invalid
    // values.
    doc_hit_info_ = DocHitInfo();
    return absl_ports::ResourceExhaustedError(
        "No more DocHitInfos in iterator");
  }
  ++num_advance_calls_;
  doc_hit_info_ = cached_hits_.at(cached_hits_idx_);
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<DocHitInfoIterator::TrimmedNode>
DocHitInfoIteratorTermLite::TrimRightMostNode() && {
  // Leaf iterator should trim itself.
  DocHitInfoIterator::TrimmedNode node = {nullptr, term_, term_start_index_,
                                          unnormalized_term_length_};
  return node;
}

libtextclassifier3::Status DocHitInfoIteratorTermLiteExact::RetrieveMoreHits() {
  // Exact match only. All hits in lite lexicon are exact.
  ICING_ASSIGN_OR_RETURN(uint32_t tvi, lite_index_->GetTermId(term_));
  ICING_ASSIGN_OR_RETURN(uint32_t term_id,
                         term_id_codec_->EncodeTvi(tvi, TviType::LITE));
  lite_index_->FetchHits(
      term_id, section_restrict_mask_,
      /*only_from_prefix_sections=*/false,
      /*score_by=*/
      SuggestionScoringSpecProto::SuggestionRankingStrategy::NONE,
      /*namespace_checker=*/nullptr, &cached_hits_,
      need_hit_term_frequency_ ? &cached_hit_term_frequency_ : nullptr);
  cached_hits_idx_ = 0;
  return libtextclassifier3::Status::OK;
}

std::string DocHitInfoIteratorTermLiteExact::ToString() const {
  return absl_ports::StrCat(SectionIdMaskToString(section_restrict_mask_), ":",
                            term_);
}

libtextclassifier3::Status
DocHitInfoIteratorTermLitePrefix::RetrieveMoreHits() {
  // Take union of lite terms.
  int term_len = term_.length();
  int terms_matched = 0;
  for (LiteIndex::PrefixIterator it = lite_index_->FindTermPrefixes(term_);
       it.IsValid(); it.Advance()) {
    bool exact_match = strlen(it.GetKey()) == term_len;
    ICING_ASSIGN_OR_RETURN(
        uint32_t term_id,
        term_id_codec_->EncodeTvi(it.GetValueIndex(), TviType::LITE));
    lite_index_->FetchHits(
        term_id, section_restrict_mask_,
        /*only_from_prefix_sections=*/!exact_match,
        /*score_by=*/
        SuggestionScoringSpecProto::SuggestionRankingStrategy::NONE,
        /*namespace_checker=*/nullptr, &cached_hits_,
        need_hit_term_frequency_ ? &cached_hit_term_frequency_ : nullptr);
    ++terms_matched;
  }
  if (terms_matched > 1) {
    SortAndDedupeDocumentIds();
  }
  cached_hits_idx_ = 0;
  return libtextclassifier3::Status::OK;
}

void DocHitInfoIteratorTermLitePrefix::SortDocumentIds() {
  // Re-sort cached document_ids and merge sections.
  if (!need_hit_term_frequency_) {
    // If we don't need to also sort cached_hit_term_frequency_ along with
    // cached_hits_, then just simply sort cached_hits_.
    sort(cached_hits_.begin(), cached_hits_.end());
  } else {
    // Sort cached_hit_term_frequency_ along with cached_hits_.
    std::vector<int> indices(cached_hits_.size());
    std::iota(indices.begin(), indices.end(), 0);
    std::sort(indices.begin(), indices.end(), [this](int i, int j) {
      return cached_hits_[i] < cached_hits_[j];
    });
    // Now indices is a map from sorted index to current index. In other words,
    // the sorted cached_hits_[i] should be the current cached_hits_[indices[i]]
    // for every valid i.
    std::vector<bool> done(indices.size());
    // Apply permutation
    for (int i = 0; i < indices.size(); ++i) {
      if (done[i]) {
        continue;
      }
      done[i] = true;
      int curr = i;
      int next = indices[i];
      // Since every finite permutation is formed by disjoint cycles, we can
      // start with the current element, at index i, and swap the element at
      // this position with whatever element that *should* be here. Then,
      // continue to swap the original element, at its updated positions, with
      // the element that should be occupying that position until the original
      // element has reached *its* correct position. This completes applying the
      // single cycle in the permutation.
      while (next != i) {
        std::swap(cached_hits_[curr], cached_hits_[next]);
        std::swap(cached_hit_term_frequency_[curr],
                  cached_hit_term_frequency_[next]);
        done[next] = true;
        curr = next;
        next = indices[next];
      }
    }
  }
}

void DocHitInfoIteratorTermLitePrefix::SortAndDedupeDocumentIds() {
  SortDocumentIds();
  int idx = 0;
  for (int i = 1; i < cached_hits_.size(); ++i) {
    const DocHitInfo& hit_info = cached_hits_[i];
    DocHitInfo& collapsed_hit_info = cached_hits_[idx];
    if (collapsed_hit_info.document_id() == hit_info.document_id()) {
      SectionIdMask curr_mask = hit_info.hit_section_ids_mask();
      collapsed_hit_info.MergeSectionsFrom(curr_mask);
      if (need_hit_term_frequency_) {
        Hit::TermFrequencyArray& collapsed_term_frequency =
            cached_hit_term_frequency_[idx];
        while (curr_mask) {
          SectionId section_id = __builtin_ctzll(curr_mask);
          collapsed_term_frequency[section_id] =
              cached_hit_term_frequency_[i][section_id];
          curr_mask &= ~(UINT64_C(1) << section_id);
        }
      }
    } else {
      // New document_id.
      ++idx;
      cached_hits_[idx] = hit_info;
      if (need_hit_term_frequency_) {
        cached_hit_term_frequency_[idx] = cached_hit_term_frequency_[i];
      }
    }
  }
  // idx points to last doc hit info.
  cached_hits_.resize(idx + 1);
  if (need_hit_term_frequency_) {
    cached_hit_term_frequency_.resize(idx + 1);
  }
}

std::string DocHitInfoIteratorTermLitePrefix::ToString() const {
  return absl_ports::StrCat(SectionIdMaskToString(section_restrict_mask_), ":",
                            term_, "*");
}

}  // namespace lib
}  // namespace icing
