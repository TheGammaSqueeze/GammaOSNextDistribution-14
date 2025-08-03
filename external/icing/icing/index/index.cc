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

#include "icing/index/index.h"

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/filesystem.h"
#include "icing/index/hit/hit.h"
#include "icing/index/iterator/doc-hit-info-iterator-or.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/lite/doc-hit-info-iterator-term-lite.h"
#include "icing/index/lite/lite-index.h"
#include "icing/index/main/doc-hit-info-iterator-term-main.h"
#include "icing/index/main/main-index.h"
#include "icing/index/term-id-codec.h"
#include "icing/index/term-metadata.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/legacy/index/icing-dynamic-trie.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/proto/scoring.pb.h"
#include "icing/proto/storage.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/schema/section.h"
#include "icing/scoring/ranker.h"
#include "icing/store/document-id.h"
#include "icing/store/suggestion-result-checker.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

libtextclassifier3::StatusOr<LiteIndex::Options> CreateLiteIndexOptions(
    const Index::Options& options) {
  if (options.index_merge_size <= 0) {
    return absl_ports::InvalidArgumentError(
        "Requested hit buffer size must be greater than 0.");
  }
  if (options.index_merge_size > LiteIndex::max_hit_buffer_size()) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Requested hit buffer size %d is too large.",
        options.index_merge_size));
  }
  return LiteIndex::Options(options.base_dir + "/idx/lite.",
                            options.index_merge_size,
                            options.lite_index_sort_at_indexing,
                            options.lite_index_sort_size);
}

std::string MakeMainIndexFilepath(const std::string& base_dir) {
  return base_dir + "/idx/main";
}

IcingDynamicTrie::Options GetMainLexiconOptions() {
  // The default values for IcingDynamicTrie::Options is fine for the main
  // lexicon.
  return IcingDynamicTrie::Options();
}

enum class MergeAction { kTakeLiteTerm, kTakeMainTerm, kMergeTerms };

// Merge the TermMetadata from lite index and main index. If the term exists in
// both index, sum up its hit count and push it to the term heap.
// The heap is a min-heap. So that we can avoid some push operation but the time
// complexity is O(NlgK) which N is total number of term and K is num_to_return.
std::vector<TermMetadata> MergeAndRankTermMetadatas(
    std::vector<TermMetadata> lite_term_metadata_list,
    std::vector<TermMetadata> main_term_metadata_list, int num_to_return) {
  std::vector<TermMetadata> merged_term_metadata_heap;
  merged_term_metadata_heap.reserve(
      std::min(lite_term_metadata_list.size() + main_term_metadata_list.size(),
               static_cast<size_t>(num_to_return)));

  auto lite_term_itr = lite_term_metadata_list.begin();
  auto main_term_itr = main_term_metadata_list.begin();
  MergeAction merge_action;
  while (lite_term_itr != lite_term_metadata_list.end() ||
         main_term_itr != main_term_metadata_list.end()) {
    // Get pointers to the next metadatas in each group, if available
    // Determine how to merge.
    if (main_term_itr == main_term_metadata_list.end()) {
      merge_action = MergeAction::kTakeLiteTerm;
    } else if (lite_term_itr == lite_term_metadata_list.end()) {
      merge_action = MergeAction::kTakeMainTerm;
    } else if (lite_term_itr->content < main_term_itr->content) {
      merge_action = MergeAction::kTakeLiteTerm;
    } else if (main_term_itr->content < lite_term_itr->content) {
      merge_action = MergeAction::kTakeMainTerm;
    } else {
      // The next metadatas refer to the same term. Combine them.
      merge_action = MergeAction::kMergeTerms;
    }
    switch (merge_action) {
      case MergeAction::kTakeLiteTerm:
        PushToTermHeap(std::move(*lite_term_itr), num_to_return,
                       merged_term_metadata_heap);
        ++lite_term_itr;
        break;
      case MergeAction::kTakeMainTerm:
        PushToTermHeap(std::move(*main_term_itr), num_to_return,
                       merged_term_metadata_heap);
        ++main_term_itr;
        break;
      case MergeAction::kMergeTerms:
        int total_est_hit_count = lite_term_itr->score + main_term_itr->score;
        PushToTermHeap(TermMetadata(std::move(lite_term_itr->content),
                                    total_est_hit_count),
                       num_to_return, merged_term_metadata_heap);
        ++lite_term_itr;
        ++main_term_itr;
        break;
    }
  }
  // Reverse the list since we pop them from a min heap and we need to return in
  // decreasing order.
  std::vector<TermMetadata> merged_term_metadata_list =
      PopAllTermsFromHeap(merged_term_metadata_heap);
  std::reverse(merged_term_metadata_list.begin(),
               merged_term_metadata_list.end());
  return merged_term_metadata_list;
}

}  // namespace

libtextclassifier3::StatusOr<std::unique_ptr<Index>> Index::Create(
    const Options& options, const Filesystem* filesystem,
    const IcingFilesystem* icing_filesystem) {
  ICING_RETURN_ERROR_IF_NULL(filesystem);
  ICING_RETURN_ERROR_IF_NULL(icing_filesystem);

  ICING_ASSIGN_OR_RETURN(LiteIndex::Options lite_index_options,
                         CreateLiteIndexOptions(options));
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<TermIdCodec> term_id_codec,
      TermIdCodec::Create(
          IcingDynamicTrie::max_value_index(GetMainLexiconOptions()),
          IcingDynamicTrie::max_value_index(
              lite_index_options.lexicon_options)));

  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<LiteIndex> lite_index,
      LiteIndex::Create(lite_index_options, icing_filesystem));
  // Sort the lite index if we've enabled sorting the HitBuffer at indexing
  // time, and there's an unsorted tail exceeding the threshold.
  if (options.lite_index_sort_at_indexing &&
      lite_index->HasUnsortedHitsExceedingSortThreshold()) {
    lite_index->SortHits();
  }

  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<MainIndex> main_index,
      MainIndex::Create(MakeMainIndexFilepath(options.base_dir), filesystem,
                        icing_filesystem));
  return std::unique_ptr<Index>(new Index(options, std::move(term_id_codec),
                                          std::move(lite_index),
                                          std::move(main_index), filesystem));
}

/* static */ libtextclassifier3::StatusOr<int> Index::ReadFlashIndexMagic(
    const Filesystem* filesystem, const std::string& base_dir) {
  return MainIndex::ReadFlashIndexMagic(filesystem,
                                        MakeMainIndexFilepath(base_dir));
}

libtextclassifier3::Status Index::TruncateTo(DocumentId document_id) {
  if (lite_index_->last_added_document_id() != kInvalidDocumentId &&
      lite_index_->last_added_document_id() > document_id) {
    ICING_VLOG(1) << "Clipping to " << document_id
                  << ". Throwing out lite index which is at "
                  << lite_index_->last_added_document_id();
    ICING_RETURN_IF_ERROR(lite_index_->Reset());
  }
  if (main_index_->last_added_document_id() != kInvalidDocumentId &&
      main_index_->last_added_document_id() > document_id) {
    ICING_VLOG(1) << "Clipping to " << document_id
                  << ". Throwing out lite index which is at "
                  << main_index_->last_added_document_id();
    ICING_RETURN_IF_ERROR(main_index_->Reset());
  }
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<std::unique_ptr<DocHitInfoIterator>>
Index::GetIterator(const std::string& term, int term_start_index,
                   int unnormalized_term_length, SectionIdMask section_id_mask,
                   TermMatchType::Code term_match_type,
                   bool need_hit_term_frequency) {
  std::unique_ptr<DocHitInfoIterator> lite_itr;
  std::unique_ptr<DocHitInfoIterator> main_itr;
  switch (term_match_type) {
    case TermMatchType::EXACT_ONLY:
      lite_itr = std::make_unique<DocHitInfoIteratorTermLiteExact>(
          term_id_codec_.get(), lite_index_.get(), term, term_start_index,
          unnormalized_term_length, section_id_mask, need_hit_term_frequency);
      main_itr = std::make_unique<DocHitInfoIteratorTermMainExact>(
          main_index_.get(), term, term_start_index, unnormalized_term_length,
          section_id_mask, need_hit_term_frequency);
      break;
    case TermMatchType::PREFIX:
      lite_itr = std::make_unique<DocHitInfoIteratorTermLitePrefix>(
          term_id_codec_.get(), lite_index_.get(), term, term_start_index,
          unnormalized_term_length, section_id_mask, need_hit_term_frequency);
      main_itr = std::make_unique<DocHitInfoIteratorTermMainPrefix>(
          main_index_.get(), term, term_start_index, unnormalized_term_length,
          section_id_mask, need_hit_term_frequency);
      break;
    default:
      return absl_ports::InvalidArgumentError(
          absl_ports::StrCat("Invalid TermMatchType: ",
                             TermMatchType::Code_Name(term_match_type)));
  }
  return std::make_unique<DocHitInfoIteratorOr>(std::move(lite_itr),
                                                std::move(main_itr));
}

libtextclassifier3::StatusOr<std::vector<TermMetadata>>
Index::FindLiteTermsByPrefix(
    const std::string& prefix,
    SuggestionScoringSpecProto::SuggestionRankingStrategy::Code score_by,
    const SuggestionResultChecker* suggestion_result_checker) {
  // Finds all the terms that start with the given prefix in the lexicon.
  IcingDynamicTrie::Iterator term_iterator(lite_index_->lexicon(),
                                           prefix.c_str());

  std::vector<TermMetadata> term_metadata_list;
  while (term_iterator.IsValid()) {
    uint32_t term_value_index = term_iterator.GetValueIndex();

    ICING_ASSIGN_OR_RETURN(
        uint32_t term_id,
        term_id_codec_->EncodeTvi(term_value_index, TviType::LITE),
        absl_ports::InternalError("Failed to access terms in lexicon."));
    ICING_ASSIGN_OR_RETURN(
        int hit_score,
        lite_index_->ScoreHits(term_id, score_by, suggestion_result_checker));
    if (hit_score > 0) {
      // There is at least one document in the given namespace has this term.
      term_metadata_list.push_back(
          TermMetadata(term_iterator.GetKey(), hit_score));
    }

    term_iterator.Advance();
  }
  return term_metadata_list;
}

libtextclassifier3::StatusOr<std::vector<TermMetadata>>
Index::FindTermsByPrefix(
    const std::string& prefix, int num_to_return,
    TermMatchType::Code scoring_match_type,
    SuggestionScoringSpecProto::SuggestionRankingStrategy::Code rank_by,
    const SuggestionResultChecker* suggestion_result_checker) {
  std::vector<TermMetadata> term_metadata_list;
  if (num_to_return <= 0) {
    return term_metadata_list;
  }
  // Get results from the LiteIndex.
  // TODO(b/250648165) support score term by prefix_hit in lite_index.
  ICING_ASSIGN_OR_RETURN(
      std::vector<TermMetadata> lite_term_metadata_list,
      FindLiteTermsByPrefix(prefix, rank_by, suggestion_result_checker));
  // Append results from the MainIndex.
  ICING_ASSIGN_OR_RETURN(
      std::vector<TermMetadata> main_term_metadata_list,
      main_index_->FindTermsByPrefix(prefix, scoring_match_type, rank_by,
                                     suggestion_result_checker));
  return MergeAndRankTermMetadatas(std::move(lite_term_metadata_list),
                                   std::move(main_term_metadata_list),
                                   num_to_return);
}

IndexStorageInfoProto Index::GetStorageInfo() const {
  IndexStorageInfoProto storage_info;
  int64_t directory_size = filesystem_->GetDiskUsage(options_.base_dir.c_str());
  storage_info.set_index_size(Filesystem::SanitizeFileSize(directory_size));
  storage_info = lite_index_->GetStorageInfo(std::move(storage_info));
  return main_index_->GetStorageInfo(std::move(storage_info));
}

libtextclassifier3::Status Index::Optimize(
    const std::vector<DocumentId>& document_id_old_to_new,
    DocumentId new_last_added_document_id) {
  if (main_index_->last_added_document_id() != kInvalidDocumentId) {
    ICING_RETURN_IF_ERROR(main_index_->Optimize(document_id_old_to_new));
  }
  return lite_index_->Optimize(document_id_old_to_new, term_id_codec_.get(),
                               new_last_added_document_id);
}

libtextclassifier3::Status Index::Editor::BufferTerm(const char* term) {
  // Step 1: See if this term is already in the lexicon
  uint32_t tvi;
  auto tvi_or = lite_index_->GetTermId(term);

  // Step 2: Update the lexicon, either add the term or update its properties
  if (tvi_or.ok()) {
    tvi = tvi_or.ValueOrDie();
    if (seen_tokens_.find(tvi) != seen_tokens_.end()) {
      ICING_VLOG(1) << "Updating term frequency for term " << term;
      if (seen_tokens_[tvi] != Hit::kMaxTermFrequency) {
        ++seen_tokens_[tvi];
      }
      return libtextclassifier3::Status::OK;
    }
    ICING_VLOG(1) << "Term " << term
                  << " is already present in lexicon. Updating.";
    // Already in the lexicon. Just update the properties.
    ICING_RETURN_IF_ERROR(lite_index_->UpdateTermProperties(
        tvi, term_match_type_ == TermMatchType::PREFIX, namespace_id_));
  } else {
    ICING_VLOG(1) << "Term " << term << " is not in lexicon. Inserting.";
    // Haven't seen this term before. Add it to the lexicon.
    ICING_ASSIGN_OR_RETURN(
        tvi, lite_index_->InsertTerm(term, term_match_type_, namespace_id_));
  }
  // Token seen for the first time in the current document.
  seen_tokens_[tvi] = 1;
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status Index::Editor::IndexAllBufferedTerms() {
  for (auto itr = seen_tokens_.begin(); itr != seen_tokens_.end(); itr++) {
    Hit hit(section_id_, document_id_, /*term_frequency=*/itr->second,
            term_match_type_ == TermMatchType::PREFIX);
    ICING_ASSIGN_OR_RETURN(
        uint32_t term_id, term_id_codec_->EncodeTvi(itr->first, TviType::LITE));
    ICING_RETURN_IF_ERROR(lite_index_->AddHit(term_id, hit));
  }
  return libtextclassifier3::Status::OK;
}

}  // namespace lib
}  // namespace icing
