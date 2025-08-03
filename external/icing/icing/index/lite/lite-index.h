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

// A small index with continuous updates (doesn't need explicit Flush
// to persiste) but has more possibility for corruption. It can always
// detect corruption reliably.

#ifndef ICING_INDEX_LITE_INDEX_H_
#define ICING_INDEX_LITE_INDEX_H_

#include <cstdint>
#include <iterator>
#include <limits>
#include <memory>
#include <string>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/mutex.h"
#include "icing/absl_ports/thread_annotations.h"
#include "icing/file/filesystem.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/hit/hit.h"
#include "icing/index/lite/lite-index-header.h"
#include "icing/index/lite/lite-index-options.h"
#include "icing/index/lite/term-id-hit-pair.h"
#include "icing/index/term-id-codec.h"
#include "icing/legacy/index/icing-array-storage.h"
#include "icing/legacy/index/icing-dynamic-trie.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/legacy/index/icing-mmapper.h"
#include "icing/proto/debug.pb.h"
#include "icing/proto/scoring.pb.h"
#include "icing/proto/storage.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/store/namespace-id.h"
#include "icing/store/suggestion-result-checker.h"
#include "icing/util/crc32.h"

namespace icing {
namespace lib {

// The LiteIndex is go/thread-compatible. Operations on the same data member
// object interfere with each other, unless they are guaranteed not to mutate
// the object (In the case of LiteIndex, this means all const methods,
// FetchHits and ScoreHits).
class LiteIndex {
 public:
  // An entry in the hit buffer.
  using Options = LiteIndexOptions;

  // Offset for the LiteIndex_Header in the hit buffer mmap.
  static constexpr uint32_t kHeaderFileOffset = 0;

  // Updates checksum of subcomponents.
  ~LiteIndex();

  // Creates lite index from storage. The files will be created if they do not
  // already exist.
  //
  // Returns:
  //  OK on success
  //  DATA_LOSS if the index was corrupted and cleared
  //  INTERNAL on I/O error
  static libtextclassifier3::StatusOr<std::unique_ptr<LiteIndex>> Create(
      const Options& options, const IcingFilesystem* filesystem);

  // Resets all internal members of the index. Returns OK if all operations were
  // successful.
  libtextclassifier3::Status Reset() ICING_LOCKS_EXCLUDED(mutex_);

  // Advises the OS to cache pages in the index, which will be accessed for a
  // query soon.
  void Warm() ICING_LOCKS_EXCLUDED(mutex_);

  // Syncs all modified files in the index to disk.
  //
  // Returns:
  //   OK on success
  //   INTERNAL on I/O error
  libtextclassifier3::Status PersistToDisk() ICING_LOCKS_EXCLUDED(mutex_);

  // Returns term_id if term found, NOT_FOUND otherwise.
  libtextclassifier3::StatusOr<uint32_t> GetTermId(
      const std::string& term) const ICING_LOCKS_EXCLUDED(mutex_);

  // Returns an iterator for all terms for which 'prefix' is a prefix.
  class PrefixIterator {
   public:
    explicit PrefixIterator(const IcingDynamicTrie::Iterator& delegate)
        : delegate_(delegate) {}
    bool IsValid() const { return delegate_.IsValid(); }

    void Advance() { delegate_.Advance(); }

    const char* GetKey() const { return delegate_.GetKey(); }

    uint32_t GetValueIndex() const { return delegate_.GetValueIndex(); }

   private:
    IcingDynamicTrie::Iterator delegate_;
  };

  // WARNING: Subsequent calls to AddHit/InsertTerm may invalidate any
  // previously returned PrefixIterator.
  PrefixIterator FindTermPrefixes(const std::string& prefix) const
      ICING_LOCKS_EXCLUDED(mutex_) {
    absl_ports::shared_lock l(&mutex_);
    return PrefixIterator(IcingDynamicTrie::Iterator(lexicon_, prefix.c_str()));
  }

  // Inserts a term with its properties.
  //
  // Returns:
  //   A value index on success
  //   RESOURCE_EXHAUSTED if lexicon is full or no disk space is available
  libtextclassifier3::StatusOr<uint32_t> InsertTerm(
      const std::string& term, TermMatchType::Code term_match_type,
      NamespaceId namespace_id) ICING_LOCKS_EXCLUDED(mutex_);

  // Updates term properties by setting hasPrefixHits and namespace id of the
  // term.
  //
  // Returns:
  //   OK on success
  //   RESOURCE_EXHAUSTED if no disk space is available
  libtextclassifier3::Status UpdateTermProperties(uint32_t tvi,
                                                  bool hasPrefixHits,
                                                  NamespaceId namespace_id)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Append hit to buffer. term_id must be encoded using the same term_id_codec
  // supplied to the index constructor.
  // RETURNS:
  //  - OK if hit was successfully added
  //  - RESOURCE_EXHAUSTED if hit could not be added (either due to hit buffer
  //    or file system capacity reached).
  libtextclassifier3::Status AddHit(uint32_t term_id, const Hit& hit)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Add all hits with term_id from the sections specified in section_id_mask,
  // skipping hits in non-prefix sections if only_from_prefix_sections is true,
  // to hits_out. If hits_out is nullptr, no hits will be added. The
  // corresponding hit term frequencies will also not be added if
  // term_frequency_out is nullptr.
  //
  // Only those hits which belongs to the given namespaces will be counted and
  // fetched. A nullptr namespace checker will disable this check.
  //
  // Returns the score of hits that would be added to hits_out according the
  // given score_by.
  int FetchHits(
      uint32_t term_id, SectionIdMask section_id_mask,
      bool only_from_prefix_sections,
      SuggestionScoringSpecProto::SuggestionRankingStrategy::Code score_by,
      const SuggestionResultChecker* suggestion_result_checker,
      std::vector<DocHitInfo>* hits_out,
      std::vector<Hit::TermFrequencyArray>* term_frequency_out = nullptr)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Returns the hit count of the term.
  // Only those hits which belongs to the given namespaces will be counted.
  libtextclassifier3::StatusOr<int> ScoreHits(
      uint32_t term_id,
      SuggestionScoringSpecProto::SuggestionRankingStrategy::Code score_by,
      const SuggestionResultChecker* suggestion_result_checker)
      ICING_LOCKS_EXCLUDED(mutex_);

  bool empty() const ICING_LOCKS_EXCLUDED(mutex_) { return size() == 0; }

  uint32_t size() const ICING_LOCKS_EXCLUDED(mutex_) {
    absl_ports::shared_lock l(&mutex_);
    return size_impl();
  }

  bool WantsMerge() const ICING_LOCKS_EXCLUDED(mutex_) {
    absl_ports::shared_lock l(&mutex_);
    return is_full() || size_impl() >= (options_.hit_buffer_want_merge_bytes /
                                        sizeof(TermIdHitPair::Value));
  }

  // Whether or not the HitBuffer's unsorted tail size exceeds the sort
  // threshold.
  bool HasUnsortedHitsExceedingSortThreshold() const
      ICING_LOCKS_EXCLUDED(mutex_) {
    absl_ports::shared_lock l(&mutex_);
    return HasUnsortedHitsExceedingSortThresholdImpl();
  }

  // Sort hits stored in the index.
  void SortHits() ICING_LOCKS_EXCLUDED(mutex_) {
    absl_ports::unique_lock l(&mutex_);
    SortHitsImpl();
  };

  class const_iterator {
    friend class LiteIndex;

   public:
    using iterator_category = std::forward_iterator_tag;
    using value_type = TermIdHitPair;
    using reference = const value_type&;
    using pointer = const value_type*;

    const_iterator() : const_iterator(nullptr, -1, -1) {}

    reference operator*() const { return start_[position_]; }

    pointer operator->() const { return start_ + position_; }

    const_iterator& operator++() {
      if (++position_ >= end_position_) {
        start_ = nullptr;
        position_ = -1;
        end_position_ = -1;
      }
      return *this;
    }

    const_iterator operator++(int) {
      auto tmp = *this;
      ++*this;
      return tmp;
    }

    bool operator!=(const const_iterator& rhs) { return !(*this == rhs); }

    bool operator==(const const_iterator& rhs) {
      return start_ == rhs.start_ && position_ == rhs.position_;
    }

   private:
    explicit const_iterator(const TermIdHitPair* start, int position,
                            int end_position)
        : start_(start), position_(position), end_position_(end_position) {}

    const TermIdHitPair* start_;
    int position_;
    int end_position_;
  };

  const_iterator begin() const ICING_LOCKS_EXCLUDED(mutex_) {
    absl_ports::shared_lock l(&mutex_);
    // If the LiteIndex is empty, just return end().
    return empty_impl()
               ? end()
               : const_iterator(hit_buffer_.array_cast<TermIdHitPair>(), 0,
                                header_->cur_size());
  }

  const_iterator end() const { return const_iterator(); }

  constexpr static uint32_t max_hit_buffer_size() {
    return std::numeric_limits<uint32_t>::max() / sizeof(TermIdHitPair);
  }

  // We keep track of the last added document_id. This is always the largest
  // document_id that has been added because hits can only be added in order of
  // increasing document_id.
  DocumentId last_added_document_id() const ICING_LOCKS_EXCLUDED(mutex_) {
    absl_ports::shared_lock l(&mutex_);
    return header_->last_added_docid();
  }
  void set_last_added_document_id(DocumentId document_id)
      ICING_LOCKS_EXCLUDED(mutex_) {
    absl_ports::unique_lock l(&mutex_);
    header_->set_last_added_docid(document_id);
  }

  // WARNING: Subsequent calls to AddHit/InsertTerm may invalidate the reference
  // returned here.
  const IcingDynamicTrie& lexicon() const { return lexicon_; }

  // Returns debug information for the index in out.
  // verbosity = BASIC, simplest debug information - size of lexicon, hit buffer
  // verbosity = DETAILED, more detailed debug information from the lexicon.
  std::string GetDebugInfo(DebugInfoVerbosity::Code verbosity)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Returns the byte size of all the elements held in the index. This excludes
  // the size of any internal metadata of the index, e.g. the index's header.
  //
  // Returns:
  //   Byte size on success
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<int64_t> GetElementsSize() const
      ICING_LOCKS_EXCLUDED(mutex_);

  // Takes the provided storage_info, populates the fields related to the lite
  // index and returns that storage_info.
  //
  // If an IO error occurs while trying to calculate the value for a field, then
  // that field will be set to -1.
  IndexStorageInfoProto GetStorageInfo(IndexStorageInfoProto storage_info) const
      ICING_LOCKS_EXCLUDED(mutex_);

  // Returns the size of unsorted part of hit_buffer_.
  uint32_t GetHitBufferByteSize() const ICING_LOCKS_EXCLUDED(mutex_) {
    absl_ports::shared_lock l(&mutex_);
    return size_impl() * sizeof(TermIdHitPair::Value);
  }

  // Returns the size of unsorted part of hit_buffer_.
  uint32_t GetHitBufferUnsortedSize() const ICING_LOCKS_EXCLUDED(mutex_) {
    absl_ports::shared_lock l(&mutex_);
    return GetHitBufferUnsortedSizeImpl();
  }

  // Returns the byte size of unsorted part of hit_buffer_.
  uint64_t GetHitBufferUnsortedByteSize() const ICING_LOCKS_EXCLUDED(mutex_) {
    absl_ports::shared_lock l(&mutex_);
    return GetHitBufferUnsortedSizeImpl() * sizeof(TermIdHitPair::Value);
  }

  // Reduces internal file sizes by reclaiming space of deleted documents.
  //
  // This method also sets the last_added_docid of the index to
  // new_last_added_document_id.
  //
  // Returns:
  //   OK on success
  //   INTERNAL_ERROR on IO error, this indicates that the index may be in an
  //                               invalid state and should be cleared.
  libtextclassifier3::Status Optimize(
      const std::vector<DocumentId>& document_id_old_to_new,
      const TermIdCodec* term_id_codec, DocumentId new_last_added_document_id)
      ICING_LOCKS_EXCLUDED(mutex_);

 private:
  static IcingDynamicTrie::RuntimeOptions MakeTrieRuntimeOptions();

  LiteIndex(const Options& options, const IcingFilesystem* filesystem);

  // Initializes lite index from storage. Must be called exactly once after
  // object construction.
  //
  // Returns:
  //  OK on success
  //  DATA_LOSS if the index was corrupted and cleared
  //  INTERNAL on I/O error
  libtextclassifier3::Status Initialize() ICING_LOCKS_EXCLUDED(mutex_);

  bool initialized() const ICING_SHARED_LOCKS_REQUIRED(mutex_) {
    return header_ != nullptr;
  }

  // Check if the hit buffer has reached its capacity.
  bool is_full() const ICING_SHARED_LOCKS_REQUIRED(mutex_);

  // Non-locking implementation for empty().
  bool empty_impl() const ICING_SHARED_LOCKS_REQUIRED(mutex_) {
    return size_impl() == 0;
  }

  // Non-locking implementation for size().
  uint32_t size_impl() const ICING_SHARED_LOCKS_REQUIRED(mutex_) {
    return header_->cur_size();
  }

  // Calculate the checksum of all sub-components of the LiteIndex
  Crc32 ComputeChecksum() ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Sets the computed checksum in the header
  void UpdateChecksum() ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Non-locking implementation for UpdateTermProperties.
  libtextclassifier3::Status UpdateTermPropertiesImpl(uint32_t tvi,
                                                      bool hasPrefixHits,
                                                      NamespaceId namespace_id)
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // We need to sort during querying time when:
  // 1. Sorting at indexing time is not enabled and there is an unsorted tail
  //    section in the HitBuffer.
  // 2. The unsorted tail size exceeds the hit_buffer_sort_threshold, regardless
  //    of whether or not hit_buffer_sort_at_indexing is enabled. This is to
  //    prevent performing sequential search on a large unsorted tail section,
  //    which would result in bad query performance.
  //    This is more of a sanity check. We should not really be encountering
  //    this case.
  bool NeedSortAtQuerying() const ICING_SHARED_LOCKS_REQUIRED(mutex_) {
    return HasUnsortedHitsExceedingSortThresholdImpl() ||
           (!options_.hit_buffer_sort_at_indexing &&
            GetHitBufferUnsortedSizeImpl() > 0);
  }

  // Non-locking implementation for HasUnsortedHitsExceedingSortThresholdImpl().
  bool HasUnsortedHitsExceedingSortThresholdImpl() const
      ICING_SHARED_LOCKS_REQUIRED(mutex_) {
    return GetHitBufferUnsortedSizeImpl() >=
           (options_.hit_buffer_sort_threshold_bytes /
            sizeof(TermIdHitPair::Value));
  }

  // Non-locking implementation for SortHits().
  void SortHitsImpl() ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Calculates and adds the score for a fetched hit to total_score_out, while
  // updating last_document_id (which keeps track of the last added docId so
  // far), and is_last_document_desired (which keeps track of whether that last
  // added docId belongs to the query's desired namespace.)
  //
  // Also appends the hit to hits_out and term_frequency_out if the vectors are
  // not null.
  void ScoreAndAppendFetchedHit(
      const Hit& hit, SectionIdMask section_id_mask,
      bool only_from_prefix_sections,
      SuggestionScoringSpecProto::SuggestionRankingStrategy::Code score_by,
      const SuggestionResultChecker* suggestion_result_checker,
      DocumentId& last_document_id, bool& is_last_document_desired,
      int& total_score_out, std::vector<DocHitInfo>* hits_out,
      std::vector<Hit::TermFrequencyArray>* term_frequency_out) const
      ICING_SHARED_LOCKS_REQUIRED(mutex_);

  // Returns the size of unsorted part of hit_buffer_.
  uint32_t GetHitBufferUnsortedSizeImpl() const
      ICING_SHARED_LOCKS_REQUIRED(mutex_) {
    return header_->cur_size() - header_->searchable_end();
  }

  // File descriptor that points to where the header and hit buffer are written
  // to.
  ScopedFd hit_buffer_fd_ ICING_GUARDED_BY(mutex_);

  // Mmapped region past the header that stores the hits.
  IcingArrayStorage hit_buffer_ ICING_GUARDED_BY(mutex_);

  // Crc checksum of the hits, excludes the header.
  uint32_t hit_buffer_crc_ ICING_GUARDED_BY(mutex_);

  // Trie that maps indexed terms to their term id
  IcingDynamicTrie lexicon_ ICING_GUARDED_BY(mutex_);

  // TODO(b/140437260): Port over to MemoryMappedFile
  // Memory mapped region of the underlying file that reflects the header.
  IcingMMapper header_mmap_ ICING_GUARDED_BY(mutex_);

  // Wrapper around the mmapped header that contains stats on the lite index.
  std::unique_ptr<LiteIndex_Header> header_ ICING_GUARDED_BY(mutex_);

  // Options used to initialize the LiteIndex.
  const Options options_;

  // TODO(b/139087650) Move to icing::Filesystem
  const IcingFilesystem* const filesystem_;

  // Used to provide reader and writer locks
  mutable absl_ports::shared_mutex mutex_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_LITE_INDEX_H_
