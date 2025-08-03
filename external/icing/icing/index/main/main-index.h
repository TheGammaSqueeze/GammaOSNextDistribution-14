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

#ifndef ICING_INDEX_MAIN_MAIN_INDEX_H_
#define ICING_INDEX_MAIN_MAIN_INDEX_H_

#include <memory>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/file/filesystem.h"
#include "icing/file/posting_list/flash-index-storage.h"
#include "icing/index/lite/term-id-hit-pair.h"
#include "icing/index/main/posting-list-hit-accessor.h"
#include "icing/index/main/posting-list-hit-serializer.h"
#include "icing/index/term-id-codec.h"
#include "icing/index/term-metadata.h"
#include "icing/legacy/index/icing-dynamic-trie.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/proto/debug.pb.h"
#include "icing/proto/scoring.pb.h"
#include "icing/proto/storage.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/store/namespace-id.h"
#include "icing/store/suggestion-result-checker.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

class MainIndex {
 public:
  // RETURNS:
  //  - valid instance of MainIndex, on success.
  //  - INTERNAL error if unable to create the lexicon or flash storage.
  static libtextclassifier3::StatusOr<std::unique_ptr<MainIndex>> Create(
      const std::string& index_directory, const Filesystem* filesystem,
      const IcingFilesystem* icing_filesystem);

  // Reads magic from existing flash index storage file header. We need this
  // during Icing initialization phase to determine the version.
  //
  // RETURNS:
  //   - On success, a valid magic.
  //   - NOT_FOUND if the flash index doesn't exist.
  //   - INTERNAL on I/O error.
  static libtextclassifier3::StatusOr<int> ReadFlashIndexMagic(
      const Filesystem* filesystem, const std::string& index_directory);

  // Get a PostingListHitAccessor that holds the posting list chain for 'term'.
  //
  // RETURNS:
  //  - On success, a valid PostingListHitAccessor
  //  - NOT_FOUND if term is not present in the main index.
  libtextclassifier3::StatusOr<std::unique_ptr<PostingListHitAccessor>>
  GetAccessorForExactTerm(const std::string& term);

  // Get a PostingListHitAccessor for 'prefix'.
  //
  // RETURNS:
  //  - On success, a result containing a valid PostingListHitAccessor.
  //  - NOT_FOUND if neither 'prefix' nor any terms for which 'prefix' is a
  //    prefix are present in the main index.
  struct GetPrefixAccessorResult {
    // A PostingListHitAccessor that holds the posting list chain for the term
    // that best represents 'prefix' in the main index.
    std::unique_ptr<PostingListHitAccessor> accessor;
    // True if the returned posting list chain is for 'prefix' or false if the
    // returned posting list chain is for a term for which 'prefix' is a prefix.
    bool exact;

    explicit GetPrefixAccessorResult(
        std::unique_ptr<PostingListHitAccessor> accessor_in, bool exact_in)
        : accessor(std::move(accessor_in)), exact(exact_in) {}
  };
  libtextclassifier3::StatusOr<GetPrefixAccessorResult>
  GetAccessorForPrefixTerm(const std::string& prefix);

  // Finds terms with the given prefix in the given result checker. The
  // input prefix must be normalized, otherwise inaccurate results may be
  // returned. If scoring_match_type is EXACT, only exact hit will be counted
  // and it is PREFIX, both prefix and exact hits will be counted. Results are
  // not sorted specifically and are in lexigraphical order. Number of results
  // are no more than 'num_to_return'.
  //
  // Returns:
  //   A list of TermMetadata on success
  //   INTERNAL_ERROR if failed to access term data.
  libtextclassifier3::StatusOr<std::vector<TermMetadata>> FindTermsByPrefix(
      const std::string& prefix, TermMatchType::Code scoring_match_type,
      SuggestionScoringSpecProto::SuggestionRankingStrategy::Code score_by,
      const SuggestionResultChecker* suggestion_result_checker);

  struct LexiconMergeOutputs {
    // Maps from main_lexicon tvi for new branching point to the main_lexicon
    // tvi for posting list whose hits must be backfilled.
    std::unordered_map<uint32_t, uint32_t> backfill_map;

    // Maps from lexicon tvis to main_lexicon tvis.
    std::unordered_map<uint32_t, uint32_t> other_tvi_to_main_tvi;

    // Maps from main lexicon tvi to the block index. Tvis with no entry do not
    // have an allocated posting list.
    std::unordered_map<uint32_t, int> main_tvi_to_block_index;

    // Maps from the lexicon tvi to the beginning position in
    // prefix_tvis_buf and the length.
    std::unordered_map<uint32_t, std::pair<int, int>>
        other_tvi_to_prefix_main_tvis;

    // Stores tvis that are mapped to by other_tvi_to_prefix_tvis.
    std::vector<uint32_t> prefix_tvis_buf;
  };

  // Merge the lexicon into the main lexicon and populate the data
  // structures necessary to translate lite tvis to main tvis, track backfilling
  // and expanding lite terms to prefix terms.
  //
  // RETURNS:
  //   - OK on success
  //   - INTERNAL on IO error while writing to the main lexicon.
  libtextclassifier3::StatusOr<LexiconMergeOutputs> MergeLexicon(
      const IcingDynamicTrie& other_lexicon) {
    // Backfill branch points need to be added first so that the backfill_map
    // can be correctly populated.
    ICING_ASSIGN_OR_RETURN(LexiconMergeOutputs outputs,
                           AddBackfillBranchPoints(other_lexicon));
    ICING_ASSIGN_OR_RETURN(outputs,
                           AddTerms(other_lexicon, std::move(outputs)));
    // Non-backfill branch points need to be added last so that the mapping of
    // newly added terms to prefix terms can be correctly populated (prefix
    // terms might be branch points between two new terms or between a
    // pre-existing term and a new term).
    ICING_ASSIGN_OR_RETURN(outputs,
                           AddBranchPoints(other_lexicon, std::move(outputs)));
    return outputs;
  }

  // Add hits to the main index and backfill from existing posting lists to new
  // backfill branch points.
  //
  // The backfill_map maps from main_lexicon tvi for a newly added branching
  // point to the main_lexicon tvi for the posting list whose hits must be
  // backfilled. backfill_map should be populated as part of LexiconMergeOutputs
  // in MergeLexicon and be blindly passed to this function.
  //
  // RETURNS:
  //  - OK on success
  //  - INVALID_ARGUMENT if one of the elements in the lite index has a term_id
  //  exceeds the max TermId, is not valid or is not less than pre-existing hits
  //  in the main index.
  //  - INTERNAL_ERROR if unable to mmap necessary IndexBlocks
  //  - RESOURCE_EXHAUSTED error if unable to grow the index
  libtextclassifier3::Status AddHits(
      const TermIdCodec& term_id_codec,
      std::unordered_map<uint32_t, uint32_t>&& backfill_map,
      std::vector<TermIdHitPair>&& hits, DocumentId last_added_document_id);

  libtextclassifier3::Status PersistToDisk() {
    if (main_lexicon_->Sync() && flash_index_storage_->PersistToDisk()) {
      return libtextclassifier3::Status::OK;
    }
    return absl_ports::InternalError("Unable to sync main index components.");
  }

  DocumentId last_added_document_id() const {
    return flash_index_storage_->get_last_indexed_docid();
  }

  libtextclassifier3::Status Reset() {
    ICING_RETURN_IF_ERROR(flash_index_storage_->Reset());
    main_lexicon_->Clear();
    return libtextclassifier3::Status::OK;
  }

  void Warm() { main_lexicon_->Warm(); }

  // Returns:
  //  - elements size of lexicon and index, on success
  //  - INTERNAL on IO error
  libtextclassifier3::StatusOr<int64_t> GetElementsSize() const;

  // Takes the provided storage_info, populates the fields related to the main
  // index and returns that storage_info.
  //
  // If an IO error occurs while trying to calculate the value for a field, then
  // that field will be set to -1.
  IndexStorageInfoProto GetStorageInfo(
      IndexStorageInfoProto storage_info) const;

  // Returns debug information for the main index in out.
  // verbosity = BASIC, simplest debug information - just the lexicon
  // verbosity = DETAILED, more detailed debug information including raw
  // postings lists.
  std::string GetDebugInfo(DebugInfoVerbosity::Code verbosity) const;

  // Reduces internal file sizes by reclaiming space of deleted documents.
  //
  // This method will update the last_added_docid of the index to the largest
  // document id that still appears in the index after compaction.
  //
  // Returns:
  //   OK on success
  //   INTERNAL_ERROR on IO error, this indicates that the index may be in an
  //                               invalid state and should be cleared.
  libtextclassifier3::Status Optimize(
      const std::vector<DocumentId>& document_id_old_to_new);

 private:
  explicit MainIndex(const std::string& index_directory,
                     const Filesystem* filesystem,
                     const IcingFilesystem* icing_filesystem);

  libtextclassifier3::Status Init();

  // Helpers for merging the lexicon
  // Add all 'backfill' branch points. Backfill branch points are prefix
  // branch points that are a prefix of terms that existed in the lexicon
  // to the merge.
  //
  // For example, if the main lexicon only contains "foot" and is then merged
  // with a lite lexicon containing only "fool", then a backfill branch point
  // for "foo" will be added to contain prefix hits from both the pre-existing
  // posting list for "foot" and the new posting list for "fool".
  //
  // Populates LexiconMergeOutputs.backfill_map
  //
  // RETURNS:
  //   - OK on success
  //   - INTERNAL on IO error while writing to the main lexicon.
  libtextclassifier3::StatusOr<LexiconMergeOutputs> AddBackfillBranchPoints(
      const IcingDynamicTrie& other_lexicon);

  // Add all terms from the lexicon.
  //
  // Populates LexiconMergeOutputs.other_tvi_to_main_tvi
  //
  // RETURNS:
  //   - OK on success
  //   - INTERNAL on IO error while writing to the main lexicon.
  libtextclassifier3::StatusOr<LexiconMergeOutputs> AddTerms(
      const IcingDynamicTrie& other_lexicon, LexiconMergeOutputs&& outputs);

  // Add all branch points for terms added from the lexicon.
  // For example, if the main lexicon is empty and is then merged with a
  // lexicon containing only "foot" and "fool", then a branch point for "foo"
  // will be added to contain prefix hits from both "foot" and "fool".
  //
  // Populates LexiconMergeOutputs.other_tvi_to_prefix_main_tvis and
  // LexiconMergeOutputs.prefix_tvis_buf;
  //
  // RETURNS:
  //   - OK on success
  //   - INTERNAL on IO error while writing to the main lexicon.
  libtextclassifier3::StatusOr<LexiconMergeOutputs> AddBranchPoints(
      const IcingDynamicTrie& other_lexicon, LexiconMergeOutputs&& outputs);

  // Copies all properties from old_tvi in the other lexicon to the new_tvi in
  // the main lexicon.
  // Returns true on success, false if an IO error is encountered.
  bool CopyProperties(const IcingDynamicTrie::PropertyReadersAll& prop_reader,
                      const IcingDynamicTrie& other_lexicon, uint32_t other_tvi,
                      uint32_t new_main_tvi);

  // Add all hits between [hit_elements, hit_elements + len) to main_index,
  // updating the entry in the main lexicon at trie_value_index to point to the
  // resulting posting list. Hits are sorted in descending document id order, so
  // they should be to posting lists in reverse (starting at hit_elements
  // + len - 1) and working backwards. Therefore, hit_elements must be in sorted
  // order.
  //
  // trie_value_index may point to a valid posting list id if there is a
  // pre-existing posting list to append to.
  //
  // If backfill_posting_list_id is valid, then the hits from the posting list
  // identified by backfill_posting_list_id should be added to the new posting
  // list before the hits in hit_elements.
  //
  // RETURNS:
  //  - OK on success
  //  - INVALID_ARGUMENT if posting_list_id stored at trie_value_index is valid
  //  but points out of bounds in the IndexBlock referred to by
  //  id.block_index(), if one of the hits from [hit_elements,hit_elements+len)
  //  is not valid, or if one of the hits from [hit_elements,hit_elements+len)
  //  is not less than the previously added hits.
  //  - INTERNAL_ERROR if posting_list_id stored at trie_value_index is valid
  //  but points to an invalid block index or if unable to mmap the IndexBlock.
  //  - RESOURCE_EXHAUSTED error if unable to grow the index to allocate a new
  //  posting list.
  libtextclassifier3::Status AddHitsForTerm(
      uint32_t tvi, PostingListIdentifier backfill_posting_list_id,
      const TermIdHitPair* hit_elements, size_t len);

  // Adds all prefix hits or hits from prefix sections present on the posting
  // list identified by backfill_posting_list_id to hit_accum.
  //
  // RETURNS:
  //  - OK, on success
  //  - INVALID_ARGUMENT if backfill_posting_list_id points out of bounds in the
  //  IndexBlock referred to by id.block_index()
  //  - INTERNAL_ERROR if unable to mmap the block identified by
  //  backfill_posting_list_id or if the posting list identified by
  //  backfill_posting_list_id has been corrupted.
  //  - RESOURCE_EXHAUSTED error if unable to grow the index to allocate a new
  //  posting list.
  libtextclassifier3::Status AddPrefixBackfillHits(
      PostingListIdentifier backfill_posting_list_id,
      PostingListHitAccessor* hit_accum);

  // Transfer hits from old_pl_accessor to new_index for term.
  //
  // Returns:
  //   largest document id added to the translated posting list, on success
  //   INTERNAL_ERROR on IO error
  static libtextclassifier3::StatusOr<DocumentId> TransferAndAddHits(
      const std::vector<DocumentId>& document_id_old_to_new, const char* term,
      PostingListHitAccessor& old_pl_accessor, MainIndex* new_index);

  // Transfer hits from the current main index to new_index.
  //
  // Returns:
  //   OK on success
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::Status TransferIndex(
      const std::vector<DocumentId>& document_id_old_to_new,
      MainIndex* new_index);

  std::string base_dir_;
  const Filesystem* filesystem_;
  const IcingFilesystem* icing_filesystem_;
  std::unique_ptr<PostingListHitSerializer> posting_list_hit_serializer_;
  std::unique_ptr<FlashIndexStorage> flash_index_storage_;
  std::unique_ptr<IcingDynamicTrie> main_lexicon_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_MAIN_MAIN_INDEX_H_
