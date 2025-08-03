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

#include "icing/index/main/main-index-merger.h"

#include <cstdint>
#include <cstring>
#include <memory>
#include <unordered_map>

#include "icing/absl_ports/canonical_errors.h"
#include "icing/file/posting_list/index-block.h"
#include "icing/index/lite/term-id-hit-pair.h"
#include "icing/index/term-id-codec.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

class HitSelector {
 public:
  // Returns whether or not term_id_hit_pair has the same term_id, document_id
  // and section_id as the previously selected hits.
  bool IsEquivalentHit(const TermIdHitPair& term_id_hit_pair) {
    return prev_.term_id() == term_id_hit_pair.term_id() &&
           prev_.hit().document_id() == term_id_hit_pair.hit().document_id() &&
           prev_.hit().section_id() == term_id_hit_pair.hit().section_id();
  }

  // Merges term_id_hit_pair with previously added hits.
  void SelectIfBetter(const TermIdHitPair& term_id_hit_pair) {
    if (term_id_hit_pair.hit().is_prefix_hit()) {
      SelectPrefixHitIfBetter(term_id_hit_pair);
    } else {
      SelectExactHitIfBetter(term_id_hit_pair);
    }
    prev_ = term_id_hit_pair;
  }

  // Adds all valid, selected hits to hits starting at position pos in hits.
  // Returns the offset in hits after the position of the last added hit.
  // This function may add between 0-2 hits depending on whether the HitSelector
  // holds both a valid exact hit and a valid prefix hit, one of those or none.
  size_t InsertSelectedHits(size_t pos, std::vector<TermIdHitPair>* hits) {
    // Given the prefix/exact hits for a given term+docid+sectionid, push needed
    // hits into hits array at offset pos. Return new pos.
    if (best_prefix_hit_.hit().is_valid() && best_exact_hit_.hit().is_valid()) {
      (*hits)[pos++] = best_exact_hit_;
      const Hit& prefix_hit = best_prefix_hit_.hit();
      // The prefix hit has score equal to the sum of the scores, capped at
      // kMaxTermFrequency.
      Hit::TermFrequency final_term_frequency = std::min(
          static_cast<int>(Hit::kMaxTermFrequency),
          prefix_hit.term_frequency() + best_exact_hit_.hit().term_frequency());
      best_prefix_hit_ = TermIdHitPair(
          best_prefix_hit_.term_id(),
          Hit(prefix_hit.section_id(), prefix_hit.document_id(),
              final_term_frequency, prefix_hit.is_in_prefix_section(),
              prefix_hit.is_prefix_hit()));
      (*hits)[pos++] = best_prefix_hit_;
      // Ensure sorted.
      if (best_prefix_hit_.hit() < best_exact_hit_.hit()) {
        std::swap((*hits)[pos - 1], (*hits)[pos - 2]);
      }
    } else if (best_prefix_hit_.hit().is_valid()) {
      (*hits)[pos++] = best_prefix_hit_;
    } else if (best_exact_hit_.hit().is_valid()) {
      (*hits)[pos++] = best_exact_hit_;
    }

    return pos;
  }

  void Reset() {
    best_prefix_hit_ = TermIdHitPair();
    best_exact_hit_ = TermIdHitPair();
    prev_ = TermIdHitPair();
  }

 private:
  void SelectPrefixHitIfBetter(const TermIdHitPair& term_id_hit_pair) {
    if (!best_prefix_hit_.hit().is_valid()) {
      best_prefix_hit_ = term_id_hit_pair;
    } else {
      const Hit& hit = term_id_hit_pair.hit();
      // Create a new prefix hit with term_frequency as the sum of the term
      // frequencies. The term frequency is capped at kMaxTermFrequency.
      Hit::TermFrequency final_term_frequency = std::min(
          static_cast<int>(Hit::kMaxTermFrequency),
          hit.term_frequency() + best_prefix_hit_.hit().term_frequency());
      best_prefix_hit_ = TermIdHitPair(
          term_id_hit_pair.term_id(),
          Hit(hit.section_id(), hit.document_id(), final_term_frequency,
              best_prefix_hit_.hit().is_in_prefix_section(),
              best_prefix_hit_.hit().is_prefix_hit()));
    }
  }

  void SelectExactHitIfBetter(const TermIdHitPair& term_id_hit_pair) {
    if (!best_exact_hit_.hit().is_valid()) {
      best_exact_hit_ = term_id_hit_pair;
    } else {
      const Hit& hit = term_id_hit_pair.hit();
      // Create a new exact hit with term_frequency as the sum of the term
      // frequencies. The term frequency is capped at kMaxHitScore.
      Hit::TermFrequency final_term_frequency = std::min(
          static_cast<int>(Hit::kMaxTermFrequency),
          hit.term_frequency() + best_exact_hit_.hit().term_frequency());
      best_exact_hit_ = TermIdHitPair(
          term_id_hit_pair.term_id(),
          Hit(hit.section_id(), hit.document_id(), final_term_frequency,
              best_exact_hit_.hit().is_in_prefix_section(),
              best_exact_hit_.hit().is_prefix_hit()));
    }
  }

  TermIdHitPair best_prefix_hit_;
  TermIdHitPair best_exact_hit_;
  TermIdHitPair prev_;
};

class HitComparator {
 public:
  explicit HitComparator(
      const TermIdCodec& term_id_codec,
      const std::unordered_map<uint32_t, int>& main_tvi_to_block_index)
      : term_id_codec_(&term_id_codec),
        main_tvi_to_block_index_(&main_tvi_to_block_index) {}

  bool operator()(const TermIdHitPair& lhs, const TermIdHitPair& rhs) const {
    // Primary sort by index block. This acheives two things:
    // 1. It reduces the number of flash writes by grouping together new hits
    // for terms whose posting lists might share the same index block.
    // 2. More importantly, this ensures that newly added backfill branch points
    // will be populated first (because all newly added terms have an invalid
    // block index of 0) before any new hits are added to the postings lists
    // that they backfill from.
    int lhs_index_block = GetIndexBlock(lhs.term_id());
    int rhs_index_block = GetIndexBlock(rhs.term_id());
    if (lhs_index_block == rhs_index_block) {
      // Secondary sort by term_id and hit.
      return lhs.value() < rhs.value();
    }
    return lhs_index_block < rhs_index_block;
  }

 private:
  int GetIndexBlock(uint32_t term_id) const {
    auto term_info_or = term_id_codec_->DecodeTermInfo(term_id);
    if (!term_info_or.ok()) {
      ICING_LOG(WARNING)
          << "Unable to decode term-info during merge. This shouldn't happen.";
      return kInvalidBlockIndex;
    }
    TermIdCodec::DecodedTermInfo term_info =
        std::move(term_info_or).ValueOrDie();
    auto itr = main_tvi_to_block_index_->find(term_info.tvi);
    if (itr == main_tvi_to_block_index_->end()) {
      return kInvalidBlockIndex;
    }
    return itr->second;
  }

  const TermIdCodec* term_id_codec_;
  const std::unordered_map<uint32_t, int>* main_tvi_to_block_index_;
};

// A helper function to dedupe hits stored in hits. Suppose that the lite index
// contained a single document with two hits in a single prefix section: "foot"
// and "fool". When expanded, there would be four hits:
// {"fo", docid0, sectionid0}
// {"fo", docid0, sectionid0}
// {"foot", docid0, sectionid0}
// {"fool", docid0, sectionid0}
//
// The first two are duplicates of each other. So, this function will dedupe
// and shrink hits to be:
// {"fo", docid0, sectionid0}
// {"foot", docid0, sectionid0}
// {"fool", docid0, sectionid0}
//
// When two or more prefix hits are duplicates, merge into one hit with term
// frequency as the sum of the term frequencies. If there is both an exact and
// prefix hit for the same term, keep the exact hit as it is, update the prefix
// hit so that its term frequency is the sum of the term frequencies.
void DedupeHits(
    std::vector<TermIdHitPair>* hits, const TermIdCodec& term_id_codec,
    const std::unordered_map<uint32_t, int>& main_tvi_to_block_index) {
  // Now all terms are grouped together and all hits for a term are sorted.
  // Merge equivalent hits into one.
  std::sort(hits->begin(), hits->end(),
            HitComparator(term_id_codec, main_tvi_to_block_index));
  size_t current_offset = 0;
  HitSelector hit_selector;
  for (const TermIdHitPair& term_id_hit_pair : *hits) {
    if (!hit_selector.IsEquivalentHit(term_id_hit_pair)) {
      // We've reached a new hit. Insert the previously selected hits that we
      // had accumulated and reset to add this new hit.
      current_offset = hit_selector.InsertSelectedHits(current_offset, hits);
      hit_selector.Reset();
    }
    // Update best exact and prefix hit.
    hit_selector.SelectIfBetter(term_id_hit_pair);
  }

  // Push last.
  current_offset = hit_selector.InsertSelectedHits(current_offset, hits);

  hits->resize(current_offset);
}

// Based on experiments with full prefix expansion, the multiplier
// is ~4x.
constexpr int kAvgPrefixesPerTerm = 4;

}  // namespace

libtextclassifier3::StatusOr<std::vector<TermIdHitPair>>
MainIndexMerger::TranslateAndExpandLiteHits(
    const LiteIndex& lite_index, const TermIdCodec& term_id_codec,
    const MainIndex::LexiconMergeOutputs& lexicon_merge_outputs) {
  std::vector<TermIdHitPair> hits;
  if (lite_index.empty()) {
    return hits;
  }
  // Reserve enough space for the average number of prefixes per term and the
  // terms themselves.
  hits.reserve(lite_index.size() * (kAvgPrefixesPerTerm + 1));

  // Translate lite tvis to main tvis.
  for (const TermIdHitPair& term_id_hit_pair : lite_index) {
    uint32_t cur_term_id = term_id_hit_pair.term_id();
    ICING_ASSIGN_OR_RETURN(TermIdCodec::DecodedTermInfo cur_decoded_term,
                           term_id_codec.DecodeTermInfo(cur_term_id));
    Hit hit(term_id_hit_pair.hit());

    // 1. Translate and push original.
    auto itr =
        lexicon_merge_outputs.other_tvi_to_main_tvi.find(cur_decoded_term.tvi);
    if (itr == lexicon_merge_outputs.other_tvi_to_main_tvi.cend()) {
      // b/37273773
      return absl_ports::InternalError(IcingStringUtil::StringPrintf(
          "Trying to translate lite tvi %u that was never added to the lexicon",
          cur_decoded_term.tvi));
    }
    ICING_ASSIGN_OR_RETURN(uint32_t term_id,
                           term_id_codec.EncodeTvi(itr->second, TviType::MAIN));
    hits.emplace_back(term_id, hit);

    // 2. Expand hits in prefix sections.
    if (hit.is_in_prefix_section()) {
      // Hit was in a prefix section. Push prefixes. Turn on prefix bit.
      auto itr_prefixes =
          lexicon_merge_outputs.other_tvi_to_prefix_main_tvis.find(
              cur_decoded_term.tvi);
      if (itr_prefixes ==
          lexicon_merge_outputs.other_tvi_to_prefix_main_tvis.end()) {
        ICING_VLOG(1) << "No necessary prefix expansion for "
                      << cur_decoded_term.tvi;
        continue;
      }
      // The tvis of all prefixes of this hit's term that appear in the main
      // lexicon are between [prefix_tvis_buf[offset],
      // prefix_tvis_buf[offset+len]).
      size_t offset = itr_prefixes->second.first;
      size_t len = itr_prefixes->second.second;
      size_t offset_end_exclusive = offset + len;
      Hit prefix_hit(hit.section_id(), hit.document_id(), hit.term_frequency(),
                     /*is_in_prefix_section=*/true, /*is_prefix_hit=*/true);
      for (; offset < offset_end_exclusive; ++offset) {
        // Take the tvi (in the main lexicon) of each prefix term.
        uint32_t prefix_main_tvi =
            lexicon_merge_outputs.prefix_tvis_buf[offset];
        // Convert it to a term_id.
        ICING_ASSIGN_OR_RETURN(
            uint32_t prefix_term_id,
            term_id_codec.EncodeTvi(prefix_main_tvi, TviType::MAIN));
        // Create add an element for this prefix TermId and prefix Hit to hits.
        hits.emplace_back(prefix_term_id, prefix_hit);
      }
    }
  }
  // 3. Remove any duplicate hits.
  DedupeHits(&hits, term_id_codec,
             lexicon_merge_outputs.main_tvi_to_block_index);
  return hits;
}

}  // namespace lib
}  // namespace icing
