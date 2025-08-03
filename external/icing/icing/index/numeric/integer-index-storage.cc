// Copyright (C) 2022 Google LLC
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

#include "icing/index/numeric/integer-index-storage.h"

#include <algorithm>
#include <cstdint>
#include <functional>
#include <iterator>
#include <limits>
#include <memory>
#include <queue>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/file-backed-vector.h"
#include "icing/file/filesystem.h"
#include "icing/file/memory-mapped-file.h"
#include "icing/file/posting_list/flash-index-storage.h"
#include "icing/file/posting_list/posting-list-identifier.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/numeric/doc-hit-info-iterator-numeric.h"
#include "icing/index/numeric/integer-index-bucket-util.h"
#include "icing/index/numeric/integer-index-data.h"
#include "icing/index/numeric/numeric-index.h"
#include "icing/index/numeric/posting-list-integer-index-accessor.h"
#include "icing/index/numeric/posting-list-integer-index-serializer.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/util/crc32.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

// Helper function to flush data between [it_start, it_end) into posting list(s)
// and return posting list id.
// Note: it will sort data between [it_start, it_end) by basic hit value, so the
// caller should be aware that the data order will be changed after calling this
// function.
libtextclassifier3::StatusOr<PostingListIdentifier> FlushDataIntoPostingLists(
    FlashIndexStorage* flash_index_storage,
    PostingListIntegerIndexSerializer* posting_list_serializer,
    const std::vector<IntegerIndexData>::iterator& it_start,
    const std::vector<IntegerIndexData>::iterator& it_end) {
  if (it_start == it_end) {
    return PostingListIdentifier::kInvalid;
  }

  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<PostingListIntegerIndexAccessor> new_pl_accessor,
      PostingListIntegerIndexAccessor::Create(flash_index_storage,
                                              posting_list_serializer));

  std::sort(it_start, it_end);
  for (auto it = it_end - 1; it >= it_start; --it) {
    ICING_RETURN_IF_ERROR(new_pl_accessor->PrependData(*it));
  }

  PostingListAccessor::FinalizeResult result =
      std::move(*new_pl_accessor).Finalize();
  if (!result.status.ok()) {
    return result.status;
  }
  if (!result.id.is_valid()) {
    return absl_ports::InternalError("Fail to flush data into posting list(s)");
  }
  return result.id;
}

// The following 4 methods are helper functions to get the correct file path of
// metadata/sorted_buckets/unsorted_buckets/flash_index_storage, according to
// the given working directory.
std::string GetMetadataFilePath(std::string_view working_path) {
  return absl_ports::StrCat(working_path, "/", IntegerIndexStorage::kFilePrefix,
                            ".m");
}

std::string GetSortedBucketsFilePath(std::string_view working_path) {
  return absl_ports::StrCat(working_path, "/", IntegerIndexStorage::kFilePrefix,
                            ".s");
}

std::string GetUnsortedBucketsFilePath(std::string_view working_path) {
  return absl_ports::StrCat(working_path, "/", IntegerIndexStorage::kFilePrefix,
                            ".u");
}

std::string GetFlashIndexStorageFilePath(std::string_view working_path) {
  return absl_ports::StrCat(working_path, "/", IntegerIndexStorage::kFilePrefix,
                            ".f");
}

}  // namespace

// We add (BasicHits, key) into a bucket in DocumentId descending and SectionId
// ascending order. When doing range query, we may access buckets and want to
// return BasicHits to callers sorted by DocumentId. Therefore, this problem is
// actually "merge K sorted lists".
// To implement this algorithm via priority_queue, we create this wrapper class
// to store PostingListIntegerIndexAccessor for iterating through the posting
// list chain.
// - Non-relevant (i.e. not in range [key_lower, key_upper]) will be skipped.
// - Relevant BasicHits will be returned.
class BucketPostingListIterator {
 public:
  class Comparator {
   public:
    // REQUIRES: 2 BucketPostingListIterator* instances (lhs, rhs) should be
    //   valid, i.e. the preceding AdvanceAndFilter() succeeded.
    bool operator()(const BucketPostingListIterator* lhs,
                    const BucketPostingListIterator* rhs) const {
      // std::priority_queue is a max heap and we should return BasicHits in
      // DocumentId descending order.
      // - BucketPostingListIterator::operator< should have the same order as
      //   DocumentId.
      // - BasicHit encodes inverted document id and BasicHit::operator<
      //   compares the encoded raw value directly.
      // - Therefore, BucketPostingListIterator::operator< should compare
      //   BasicHit reversely.
      // - This will make priority_queue return buckets in DocumentId
      //   descending and SectionId ascending order.
      // - Whatever direction we sort SectionId by (or pop by priority_queue)
      //   doesn't matter because all hits for the same DocumentId will be
      //   merged into a single DocHitInfo.
      return rhs->GetCurrentBasicHit() < lhs->GetCurrentBasicHit();
    }
  };

  explicit BucketPostingListIterator(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor)
      : pl_accessor_(std::move(pl_accessor)),
        should_retrieve_next_batch_(true) {}

  struct AdvanceAndFilterResult {
    libtextclassifier3::Status status = libtextclassifier3::Status::OK;
    int32_t num_advance_calls = 0;
    int32_t num_blocks_inspected = 0;
  };
  // Advances to the next relevant data. The posting list of a bucket contains
  // keys within range [bucket.key_lower, bucket.key_upper], but some of them
  // may be out of [query_key_lower, query_key_upper], so when advancing we have
  // to filter out those non-relevant keys.
  //
  // Returns:
  //   AdvanceAndFilterResult. status will be:
  //   - OK on success
  //   - RESOURCE_EXHAUSTED_ERROR if reaching the end (i.e. no more relevant
  //     data)
  //   - Any other PostingListIntegerIndexAccessor errors
  AdvanceAndFilterResult AdvanceAndFilter(int64_t query_key_lower,
                                          int64_t query_key_upper) {
    AdvanceAndFilterResult result;
    // Move curr_ until reaching a relevant data (i.e. key in range
    // [query_key_lower, query_key_upper])
    do {
      if (!should_retrieve_next_batch_) {
        ++curr_;
        should_retrieve_next_batch_ =
            curr_ >= cached_batch_integer_index_data_.cend();
      }
      if (should_retrieve_next_batch_) {
        auto status = GetNextDataBatch();
        if (!status.ok()) {
          result.status = std::move(status);
          return result;
        }
        ++result.num_blocks_inspected;
        should_retrieve_next_batch_ = false;
      }
      ++result.num_advance_calls;
    } while (curr_->key() < query_key_lower || curr_->key() > query_key_upper);

    return result;
  }

  const BasicHit& GetCurrentBasicHit() const { return curr_->basic_hit(); }

 private:
  // Gets next batch of data from the posting list chain, caches in
  // cached_batch_integer_index_data_, and sets curr_ to the begin of the cache.
  libtextclassifier3::Status GetNextDataBatch() {
    auto cached_batch_integer_index_data_or = pl_accessor_->GetNextDataBatch();
    if (!cached_batch_integer_index_data_or.ok()) {
      ICING_LOG(WARNING)
          << "Fail to get next batch data from posting list due to: "
          << cached_batch_integer_index_data_or.status().error_message();
      return std::move(cached_batch_integer_index_data_or).status();
    }

    cached_batch_integer_index_data_ =
        std::move(cached_batch_integer_index_data_or).ValueOrDie();
    curr_ = cached_batch_integer_index_data_.cbegin();

    if (cached_batch_integer_index_data_.empty()) {
      return absl_ports::ResourceExhaustedError("End of iterator");
    }

    return libtextclassifier3::Status::OK;
  }

  std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor_;
  std::vector<IntegerIndexData> cached_batch_integer_index_data_;
  std::vector<IntegerIndexData>::const_iterator curr_;
  bool should_retrieve_next_batch_;
};

// Wrapper class to iterate through IntegerIndexStorage to get relevant data.
// It uses multiple BucketPostingListIterator instances from different candidate
// buckets and merges all relevant BasicHits from these buckets by
// std::priority_queue in DocumentId descending order. Also different SectionIds
// of the same DocumentId will be merged into SectionIdMask and returned as a
// single DocHitInfo.
class IntegerIndexStorageIterator : public NumericIndex<int64_t>::Iterator {
 public:
  explicit IntegerIndexStorageIterator(
      int64_t query_key_lower, int64_t query_key_upper,
      std::vector<std::unique_ptr<BucketPostingListIterator>>&& bucket_pl_iters)
      : NumericIndex<int64_t>::Iterator(query_key_lower, query_key_upper),
        num_advance_calls_(0),
        num_blocks_inspected_(0) {
    std::vector<BucketPostingListIterator*> bucket_pl_iters_raw_ptrs;
    for (std::unique_ptr<BucketPostingListIterator>& bucket_pl_itr :
         bucket_pl_iters) {
      // Before adding BucketPostingListIterator* into the priority queue, we
      // have to advance the bucket iterator to the first valid data since the
      // priority queue needs valid data to compare the order.
      // Note: it is possible that the bucket iterator fails to advance for the
      // first round, because data could be filtered out by [query_key_lower,
      // query_key_upper]. In this case, just discard the iterator.
      BucketPostingListIterator::AdvanceAndFilterResult
          advance_and_filter_result =
              bucket_pl_itr->AdvanceAndFilter(query_key_lower, query_key_upper);
      if (advance_and_filter_result.status.ok()) {
        bucket_pl_iters_raw_ptrs.push_back(bucket_pl_itr.get());
        bucket_pl_iters_.push_back(std::move(bucket_pl_itr));
      }
      num_advance_calls_ += advance_and_filter_result.num_advance_calls;
      num_blocks_inspected_ += advance_and_filter_result.num_blocks_inspected;
    }

    pq_ = std::priority_queue<BucketPostingListIterator*,
                              std::vector<BucketPostingListIterator*>,
                              BucketPostingListIterator::Comparator>(
        comparator_, std::move(bucket_pl_iters_raw_ptrs));
  }

  ~IntegerIndexStorageIterator() override = default;

  // Advances to the next DocHitInfo. Note: several BucketPostingListIterator
  // instances may be advanced if they point to data with the same DocumentId.
  //
  // Returns:
  //   - OK on success
  //   - RESOURCE_EXHAUSTED_ERROR if reaching the end (i.e. no more relevant
  //     data)
  //   - Any BucketPostingListIterator errors
  libtextclassifier3::Status Advance() override;

  DocHitInfo GetDocHitInfo() const override { return doc_hit_info_; }

  int32_t GetNumAdvanceCalls() const override { return num_advance_calls_; }

  int32_t GetNumBlocksInspected() const override {
    return num_blocks_inspected_;
  }

 private:
  BucketPostingListIterator::Comparator comparator_;

  // We have to fetch and pop the top BucketPostingListIterator from
  // std::priority_queue to perform "merge K sorted lists algorithm".
  // - Since std::priority_queue::pop() doesn't return the top element, we have
  //   to call top() and pop() together.
  // - std::move the top() element by const_cast is not an appropriate way
  //   because it introduces transient unstable state for std::priority_queue.
  // - We don't want to copy BucketPostingListIterator, either.
  // - Therefore, add bucket_pl_iters_ for the ownership of all
  //   BucketPostingListIterator instances and std::priority_queue uses the raw
  //   pointer. So when calling top(), we can simply copy the raw pointer via
  //   top() and avoid transient unstable state.
  std::vector<std::unique_ptr<BucketPostingListIterator>> bucket_pl_iters_;
  std::priority_queue<BucketPostingListIterator*,
                      std::vector<BucketPostingListIterator*>,
                      BucketPostingListIterator::Comparator>
      pq_;

  DocHitInfo doc_hit_info_;

  int32_t num_advance_calls_;
  int32_t num_blocks_inspected_;
};

libtextclassifier3::Status IntegerIndexStorageIterator::Advance() {
  if (pq_.empty()) {
    return absl_ports::ResourceExhaustedError("End of iterator");
  }

  DocumentId document_id = pq_.top()->GetCurrentBasicHit().document_id();
  doc_hit_info_ = DocHitInfo(document_id);
  // Merge sections with same document_id into a single DocHitInfo
  while (!pq_.empty() &&
         pq_.top()->GetCurrentBasicHit().document_id() == document_id) {
    BucketPostingListIterator* bucket_itr = pq_.top();
    pq_.pop();

    libtextclassifier3::Status advance_status;
    do {
      doc_hit_info_.UpdateSection(
          bucket_itr->GetCurrentBasicHit().section_id());
      BucketPostingListIterator::AdvanceAndFilterResult
          advance_and_filter_result =
              bucket_itr->AdvanceAndFilter(key_lower_, key_upper_);
      advance_status = std::move(advance_and_filter_result.status);
      num_advance_calls_ += advance_and_filter_result.num_advance_calls;
      num_blocks_inspected_ += advance_and_filter_result.num_blocks_inspected;
    } while (advance_status.ok() &&
             bucket_itr->GetCurrentBasicHit().document_id() == document_id);
    if (advance_status.ok()) {
      pq_.push(bucket_itr);
    }
  }

  return libtextclassifier3::Status::OK;
}

bool IntegerIndexStorage::Options::IsValid() const {
  if (num_data_threshold_for_bucket_split <=
      kMinNumDataThresholdForBucketSplit) {
    return false;
  }

  if (!HasCustomInitBuckets()) {
    return true;
  }

  // Verify if the range of buckets are disjoint and the range union is
  // [INT64_MIN, INT64_MAX].
  std::vector<Bucket> buckets;
  buckets.reserve(custom_init_sorted_buckets.size() +
                  custom_init_unsorted_buckets.size());
  buckets.insert(buckets.end(), custom_init_sorted_buckets.begin(),
                 custom_init_sorted_buckets.end());
  buckets.insert(buckets.end(), custom_init_unsorted_buckets.begin(),
                 custom_init_unsorted_buckets.end());
  if (buckets.empty()) {
    return false;
  }
  std::sort(buckets.begin(), buckets.end());
  int64_t prev_upper = std::numeric_limits<int64_t>::min();
  for (int i = 0; i < buckets.size(); ++i) {
    // key_lower should not be greater than key_upper and init bucket should
    // have invalid posting list identifier.
    if (buckets[i].key_lower() > buckets[i].key_upper() ||
        buckets[i].posting_list_identifier().is_valid()) {
      return false;
    }

    // Previous upper bound should not be INT64_MAX since it is not the last
    // bucket.
    if (prev_upper == std::numeric_limits<int64_t>::max()) {
      return false;
    }

    int64_t expected_lower =
        (i == 0 ? std::numeric_limits<int64_t>::min() : prev_upper + 1);
    if (buckets[i].key_lower() != expected_lower) {
      return false;
    }

    prev_upper = buckets[i].key_upper();
  }

  return prev_upper == std::numeric_limits<int64_t>::max();
}

/* static */ libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndexStorage>>
IntegerIndexStorage::Create(
    const Filesystem& filesystem, std::string working_path, Options options,
    PostingListIntegerIndexSerializer* posting_list_serializer) {
  if (!options.IsValid()) {
    return absl_ports::InvalidArgumentError(
        "Invalid IntegerIndexStorage options");
  }

  if (!filesystem.FileExists(GetMetadataFilePath(working_path).c_str()) ||
      !filesystem.FileExists(GetSortedBucketsFilePath(working_path).c_str()) ||
      !filesystem.FileExists(
          GetUnsortedBucketsFilePath(working_path).c_str()) ||
      !filesystem.FileExists(
          GetFlashIndexStorageFilePath(working_path).c_str())) {
    // Discard working_path if any of them is missing, and reinitialize.
    if (filesystem.DirectoryExists(working_path.c_str())) {
      ICING_RETURN_IF_ERROR(Discard(filesystem, working_path));
    }
    return InitializeNewFiles(filesystem, std::move(working_path),
                              std::move(options), posting_list_serializer);
  }
  return InitializeExistingFiles(filesystem, std::move(working_path),
                                 std::move(options), posting_list_serializer);
}

IntegerIndexStorage::~IntegerIndexStorage() {
  if (!PersistToDisk().ok()) {
    ICING_LOG(WARNING)
        << "Failed to persist hash map to disk while destructing "
        << working_path_;
  }
}

class IntegerIndexStorageComparator {
 public:
  bool operator()(const IntegerIndexStorage::Bucket& lhs, int64_t rhs) const {
    return lhs.key_upper() < rhs;
  }
} kComparator;

libtextclassifier3::Status IntegerIndexStorage::AddKeys(
    DocumentId document_id, SectionId section_id,
    std::vector<int64_t>&& new_keys) {
  if (new_keys.empty()) {
    return libtextclassifier3::Status::OK;
  }

  SetDirty();

  std::sort(new_keys.begin(), new_keys.end());

  // Dedupe
  auto last = std::unique(new_keys.begin(), new_keys.end());
  new_keys.erase(last, new_keys.end());

  if (static_cast<int32_t>(new_keys.size()) >
      std::numeric_limits<int32_t>::max() - info().num_data) {
    return absl_ports::ResourceExhaustedError(
        "# of keys in this integer index storage exceed the limit");
  }

  // When adding keys into a bucket, we potentially split it into 2 new buckets
  // and one of them will be added into the unsorted bucket array.
  // When handling keys belonging to buckets in the unsorted bucket array, we
  // don't have to (and must not) handle these newly split buckets. Therefore,
  // collect all newly split buckets in another vector and append them into the
  // unsorted bucket array after adding all keys.
  std::vector<Bucket> new_buckets;

  // Binary search range of the sorted bucket array.
  const Bucket* sorted_bucket_arr_begin = sorted_buckets_->array();
  const Bucket* sorted_bucket_arr_end =
      sorted_buckets_->array() + sorted_buckets_->num_elements();

  // Step 1: handle keys belonging to buckets in the sorted bucket array. Skip
  //         keys belonging to the unsorted bucket array and deal with them in
  //         the next step.
  // - Iterate through new_keys by it_start.
  // - Binary search (std::lower_bound comparing key with bucket.key_upper()) to
  //   find the first bucket in the sorted bucket array with key_upper is not
  //   smaller than (>=) the key.
  // - Skip (and advance it_start) all keys smaller than the target bucket's
  //   key_lower. It means these keys belong to buckets in the unsorted bucket
  //   array and we will deal with them later.
  // - Find it_end such that all keys within range [it_start, it_end) belong to
  //   the target bucket.
  // - Batch add keys within range [it_start, it_end) into the target bucket.
  auto it_start = new_keys.cbegin();
  while (it_start != new_keys.cend() &&
         sorted_bucket_arr_begin < sorted_bucket_arr_end) {
    // Use std::lower_bound to find the first bucket in the sorted bucket array
    // with key_upper >= *it_start.
    const Bucket* target_bucket = std::lower_bound(
        sorted_bucket_arr_begin, sorted_bucket_arr_end, *it_start, kComparator);
    if (target_bucket >= sorted_bucket_arr_end) {
      // Keys in range [it_start, new_keys.cend()) are greater than all sorted
      // buckets' key_upper, so we can end step 1. In fact, they belong to
      // buckets in the unsorted bucket array and we will deal with them in
      // step 2.
      break;
    }

    // Sequential instead of binary search to advance it_start and it_end for
    // several reasons:
    // - Eventually we have to iterate through all keys within range [it_start,
    //   it_end) and add them into the posting list, so binary search doesn't
    //   improve the overall time complexity.
    // - Binary search may jump to far-away indices, which potentially
    //   downgrades the cache performance.

    // After binary search, we've ensured *it_start <=
    // target_bucket->key_upper(), but it is still possible that *it_start (and
    // the next several keys) is still smaller than target_bucket->key_lower(),
    // so we have to skip them. In fact, they belong to buckets in the unsorted
    // bucket array.
    //
    // For example:
    // - sorted bucket array: [(INT_MIN, 0), (1, 5), (100, 300), (301, 550)]
    // - unsorted bucket array: [(550, INT_MAX), (6, 99)]
    // - new_keys: [10, 20, 40, 102, 150, 200, 500, 600]
    // std::lower_bound (target = 10) will get target_bucket = (100, 300), but
    // we have to skip 10, 20, 40 because they are smaller than 100 (the
    // bucket's key_lower). We should move it_start pointing to key 102.
    while (it_start != new_keys.cend() &&
           *it_start < target_bucket->key_lower()) {
      ++it_start;
    }

    // Locate it_end such that all keys within range [it_start, it_end) belong
    // to target_bucket and all keys outside this range don't belong to
    // target_bucket.
    //
    // For example (continue above), we should locate it_end to point to key
    // 500.
    auto it_end = it_start;
    while (it_end != new_keys.cend() && *it_end <= target_bucket->key_upper()) {
      ++it_end;
    }

    // Now, keys within range [it_start, it_end) belong to target_bucket, so
    // construct IntegerIndexData and add them into the bucket's posting list.
    if (it_start != it_end) {
      ICING_ASSIGN_OR_RETURN(
          FileBackedVector<Bucket>::MutableView mutable_bucket,
          sorted_buckets_->GetMutable(target_bucket -
                                      sorted_buckets_->array()));
      ICING_ASSIGN_OR_RETURN(
          std::vector<Bucket> round_new_buckets,
          AddKeysIntoBucketAndSplitIfNecessary(
              document_id, section_id, it_start, it_end, mutable_bucket));
      new_buckets.insert(new_buckets.end(), round_new_buckets.begin(),
                         round_new_buckets.end());
    }

    it_start = it_end;
    sorted_bucket_arr_begin = target_bucket + 1;
  }

  // Step 2: handle keys belonging to buckets in the unsorted bucket array. They
  //         were skipped in step 1.
  // For each bucket in the unsorted bucket array, find [it_start, it_end) such
  // that all keys within this range belong to the bucket and add them.
  // - Binary search (std::lower_bound comparing bucket.key_lower() with key) to
  //   find it_start.
  // - Sequential advance (start from it_start) to find it_end. Same reason as
  //   above for choosing sequential advance instead of binary search.
  // - Add keys within range [it_start, it_end) into the bucket.
  for (int32_t i = 0; i < unsorted_buckets_->num_elements(); ++i) {
    ICING_ASSIGN_OR_RETURN(FileBackedVector<Bucket>::MutableView mutable_bucket,
                           unsorted_buckets_->GetMutable(i));
    auto it_start = std::lower_bound(new_keys.cbegin(), new_keys.cend(),
                                     mutable_bucket.Get().key_lower());
    if (it_start == new_keys.cend()) {
      continue;
    }

    // Sequential advance instead of binary search to find the correct position
    // of it_end for the same reasons mentioned above in step 1.
    auto it_end = it_start;
    while (it_end != new_keys.cend() &&
           *it_end <= mutable_bucket.Get().key_upper()) {
      ++it_end;
    }

    // Now, key within range [it_start, it_end) belong to the bucket, so
    // construct IntegerIndexData and add them into the bucket's posting list.
    if (it_start != it_end) {
      ICING_ASSIGN_OR_RETURN(
          std::vector<Bucket> round_new_buckets,
          AddKeysIntoBucketAndSplitIfNecessary(
              document_id, section_id, it_start, it_end, mutable_bucket));
      new_buckets.insert(new_buckets.end(), round_new_buckets.begin(),
                         round_new_buckets.end());
    }
  }

  // Step 3: append new buckets into the unsorted bucket array.
  if (!new_buckets.empty()) {
    ICING_ASSIGN_OR_RETURN(
        typename FileBackedVector<Bucket>::MutableArrayView mutable_new_arr,
        unsorted_buckets_->Allocate(new_buckets.size()));
    mutable_new_arr.SetArray(/*idx=*/0, new_buckets.data(), new_buckets.size());
  }

  // Step 4: sort and merge the unsorted bucket array into the sorted bucket
  //         array if the length of the unsorted bucket array exceeds the
  //         threshold.
  if (unsorted_buckets_->num_elements() > kUnsortedBucketsLengthThreshold) {
    ICING_RETURN_IF_ERROR(SortBuckets());
  }

  info().num_data += new_keys.size();

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<std::unique_ptr<DocHitInfoIterator>>
IntegerIndexStorage::GetIterator(int64_t query_key_lower,
                                 int64_t query_key_upper) const {
  if (query_key_lower > query_key_upper) {
    return absl_ports::InvalidArgumentError(
        "key_lower should not be greater than key_upper");
  }

  std::vector<std::unique_ptr<BucketPostingListIterator>> bucket_pl_iters;

  // Sorted bucket array
  const Bucket* sorted_bucket_arr_begin = sorted_buckets_->array();
  const Bucket* sorted_bucket_arr_end =
      sorted_buckets_->array() + sorted_buckets_->num_elements();
  for (const Bucket* bucket =
           std::lower_bound(sorted_bucket_arr_begin, sorted_bucket_arr_end,
                            query_key_lower, kComparator);
       bucket < sorted_bucket_arr_end && bucket->key_lower() <= query_key_upper;
       ++bucket) {
    if (!bucket->posting_list_identifier().is_valid()) {
      continue;
    }

    ICING_ASSIGN_OR_RETURN(
        std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor,
        PostingListIntegerIndexAccessor::CreateFromExisting(
            flash_index_storage_.get(), posting_list_serializer_,
            bucket->posting_list_identifier()));
    bucket_pl_iters.push_back(
        std::make_unique<BucketPostingListIterator>(std::move(pl_accessor)));
  }

  // Unsorted bucket array
  for (int32_t i = 0; i < unsorted_buckets_->num_elements(); ++i) {
    ICING_ASSIGN_OR_RETURN(const Bucket* bucket, unsorted_buckets_->Get(i));
    if (query_key_upper < bucket->key_lower() ||
        query_key_lower > bucket->key_upper() ||
        !bucket->posting_list_identifier().is_valid()) {
      // Skip bucket whose range doesn't overlap with [query_key_lower,
      // query_key_upper] or posting_list_identifier is invalid.
      continue;
    }

    ICING_ASSIGN_OR_RETURN(
        std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor,
        PostingListIntegerIndexAccessor::CreateFromExisting(
            flash_index_storage_.get(), posting_list_serializer_,
            bucket->posting_list_identifier()));
    bucket_pl_iters.push_back(
        std::make_unique<BucketPostingListIterator>(std::move(pl_accessor)));
  }

  return std::make_unique<DocHitInfoIteratorNumeric<int64_t>>(
      std::make_unique<IntegerIndexStorageIterator>(
          query_key_lower, query_key_upper, std::move(bucket_pl_iters)));
}

libtextclassifier3::Status IntegerIndexStorage::TransferIndex(
    const std::vector<DocumentId>& document_id_old_to_new,
    IntegerIndexStorage* new_storage) const {
  // Discard all pre-existing buckets in new_storage since we will append newly
  // merged buckets gradually into new_storage.
  if (new_storage->sorted_buckets_->num_elements() > 0) {
    ICING_RETURN_IF_ERROR(new_storage->sorted_buckets_->TruncateTo(0));
  }
  if (new_storage->unsorted_buckets_->num_elements() > 0) {
    ICING_RETURN_IF_ERROR(new_storage->unsorted_buckets_->TruncateTo(0));
  }

  // "Reference sort" the original storage buckets.
  std::vector<std::reference_wrapper<const Bucket>> temp_buckets;
  temp_buckets.reserve(sorted_buckets_->num_elements() +
                       unsorted_buckets_->num_elements());
  temp_buckets.insert(
      temp_buckets.end(), sorted_buckets_->array(),
      sorted_buckets_->array() + sorted_buckets_->num_elements());
  temp_buckets.insert(
      temp_buckets.end(), unsorted_buckets_->array(),
      unsorted_buckets_->array() + unsorted_buckets_->num_elements());
  std::sort(temp_buckets.begin(), temp_buckets.end(),
            [](const std::reference_wrapper<const Bucket>& lhs,
               const std::reference_wrapper<const Bucket>& rhs) -> bool {
              return lhs.get() < rhs.get();
            });

  const int32_t num_data_threshold_for_bucket_merge =
      kNumDataThresholdRatioForBucketMerge *
      new_storage->options_.num_data_threshold_for_bucket_split;
  int64_t curr_key_lower = std::numeric_limits<int64_t>::min();
  int64_t curr_key_upper = std::numeric_limits<int64_t>::min();
  std::vector<IntegerIndexData> accumulated_data;
  for (const std::reference_wrapper<const Bucket>& bucket_ref : temp_buckets) {
    // Read all data from the bucket.
    std::vector<IntegerIndexData> new_data;
    if (bucket_ref.get().posting_list_identifier().is_valid()) {
      ICING_ASSIGN_OR_RETURN(
          std::unique_ptr<PostingListIntegerIndexAccessor> old_pl_accessor,
          PostingListIntegerIndexAccessor::CreateFromExisting(
              flash_index_storage_.get(), posting_list_serializer_,
              bucket_ref.get().posting_list_identifier()));

      ICING_ASSIGN_OR_RETURN(std::vector<IntegerIndexData> batch_old_data,
                             old_pl_accessor->GetNextDataBatch());
      while (!batch_old_data.empty()) {
        for (const IntegerIndexData& old_data : batch_old_data) {
          DocumentId new_document_id =
              old_data.basic_hit().document_id() < document_id_old_to_new.size()
                  ? document_id_old_to_new[old_data.basic_hit().document_id()]
                  : kInvalidDocumentId;
          // Transfer the document id of the hit if the document is not deleted
          // or outdated.
          if (new_document_id != kInvalidDocumentId) {
            new_data.push_back(
                IntegerIndexData(old_data.basic_hit().section_id(),
                                 new_document_id, old_data.key()));
          }
        }
        ICING_ASSIGN_OR_RETURN(batch_old_data,
                               old_pl_accessor->GetNextDataBatch());
      }
    }

    // Decide whether:
    // - Flush accumulated_data and create a new bucket for them.
    // - OR merge new_data into accumulated_data and go to the next round.
    if (!accumulated_data.empty() && accumulated_data.size() + new_data.size() >
                                         num_data_threshold_for_bucket_merge) {
      // TODO(b/259743562): [Optimization 3] adjust upper bound to fit more data
      // from new_data to accumulated_data.
      ICING_RETURN_IF_ERROR(FlushDataIntoNewSortedBucket(
          curr_key_lower, curr_key_upper, std::move(accumulated_data),
          new_storage));

      curr_key_lower = bucket_ref.get().key_lower();
      accumulated_data = std::move(new_data);
    } else {
      // We can just append to accumulated data because
      // FlushDataIntoNewSortedBucket will take care of sorting the contents.
      std::move(new_data.begin(), new_data.end(),
                std::back_inserter(accumulated_data));
    }
    curr_key_upper = bucket_ref.get().key_upper();
  }

  // Add the last round of bucket.
  ICING_RETURN_IF_ERROR(
      FlushDataIntoNewSortedBucket(curr_key_lower, curr_key_upper,
                                   std::move(accumulated_data), new_storage));

  return libtextclassifier3::Status::OK;
}

/* static */ libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndexStorage>>
IntegerIndexStorage::InitializeNewFiles(
    const Filesystem& filesystem, std::string&& working_path, Options&& options,
    PostingListIntegerIndexSerializer* posting_list_serializer) {
  // IntegerIndexStorage uses working_path as working directory path.
  // Create working directory.
  if (!filesystem.CreateDirectory(working_path.c_str())) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to create directory: ", working_path));
  }

  // Initialize sorted_buckets
  int32_t pre_mapping_mmap_size = sizeof(Bucket) * (1 << 10);
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<FileBackedVector<Bucket>> sorted_buckets,
      FileBackedVector<Bucket>::Create(
          filesystem, GetSortedBucketsFilePath(working_path),
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
          FileBackedVector<Bucket>::kMaxFileSize,
          options.pre_mapping_fbv ? pre_mapping_mmap_size : 0));

  // Initialize unsorted_buckets
  pre_mapping_mmap_size = sizeof(Bucket) * kUnsortedBucketsLengthThreshold;
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<FileBackedVector<Bucket>> unsorted_buckets,
      FileBackedVector<Bucket>::Create(
          filesystem, GetUnsortedBucketsFilePath(working_path),
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
          FileBackedVector<Bucket>::kMaxFileSize,
          options.pre_mapping_fbv ? pre_mapping_mmap_size : 0));

  // Initialize flash_index_storage
  ICING_ASSIGN_OR_RETURN(
      FlashIndexStorage flash_index_storage,
      FlashIndexStorage::Create(GetFlashIndexStorageFilePath(working_path),
                                &filesystem, posting_list_serializer));

  if (options.HasCustomInitBuckets()) {
    // Insert custom init buckets.
    std::sort(options.custom_init_sorted_buckets.begin(),
              options.custom_init_sorted_buckets.end());
    ICING_ASSIGN_OR_RETURN(
        typename FileBackedVector<Bucket>::MutableArrayView
            mutable_new_sorted_bucket_arr,
        sorted_buckets->Allocate(options.custom_init_sorted_buckets.size()));
    mutable_new_sorted_bucket_arr.SetArray(
        /*idx=*/0, options.custom_init_sorted_buckets.data(),
        options.custom_init_sorted_buckets.size());

    ICING_ASSIGN_OR_RETURN(typename FileBackedVector<Bucket>::MutableArrayView
                               mutable_new_unsorted_bucket_arr,
                           unsorted_buckets->Allocate(
                               options.custom_init_unsorted_buckets.size()));
    mutable_new_unsorted_bucket_arr.SetArray(
        /*idx=*/0, options.custom_init_unsorted_buckets.data(),
        options.custom_init_unsorted_buckets.size());

    // After inserting buckets, we can clear vectors since there is no need to
    // cache them.
    options.custom_init_sorted_buckets.clear();
    options.custom_init_unsorted_buckets.clear();
  } else {
    // Insert one bucket with range [INT64_MIN, INT64_MAX].
    ICING_RETURN_IF_ERROR(sorted_buckets->Append(Bucket(
        /*key_lower=*/std::numeric_limits<int64_t>::min(),
        /*key_upper=*/std::numeric_limits<int64_t>::max())));
  }
  ICING_RETURN_IF_ERROR(sorted_buckets->PersistToDisk());

  // Initialize metadata file. Create MemoryMappedFile with pre-mapping, and
  // call GrowAndRemapIfNecessary to grow the underlying file.
  ICING_ASSIGN_OR_RETURN(
      MemoryMappedFile metadata_mmapped_file,
      MemoryMappedFile::Create(filesystem, GetMetadataFilePath(working_path),
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               /*max_file_size=*/kMetadataFileSize,
                               /*pre_mapping_file_offset=*/0,
                               /*pre_mapping_mmap_size=*/kMetadataFileSize));
  ICING_RETURN_IF_ERROR(metadata_mmapped_file.GrowAndRemapIfNecessary(
      /*file_offset=*/0, /*mmap_size=*/kMetadataFileSize));

  // Create instance.
  auto new_integer_index_storage =
      std::unique_ptr<IntegerIndexStorage>(new IntegerIndexStorage(
          filesystem, std::move(working_path), std::move(options),
          posting_list_serializer,
          std::make_unique<MemoryMappedFile>(std::move(metadata_mmapped_file)),
          std::move(sorted_buckets), std::move(unsorted_buckets),
          std::make_unique<FlashIndexStorage>(std::move(flash_index_storage))));
  // Initialize info content by writing mapped memory directly.
  Info& info_ref = new_integer_index_storage->info();
  info_ref.magic = Info::kMagic;
  info_ref.num_data = 0;
  // Initialize new PersistentStorage. The initial checksums will be computed
  // and set via InitializeNewStorage.
  ICING_RETURN_IF_ERROR(new_integer_index_storage->InitializeNewStorage());

  return new_integer_index_storage;
}

/* static */ libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndexStorage>>
IntegerIndexStorage::InitializeExistingFiles(
    const Filesystem& filesystem, std::string&& working_path, Options&& options,
    PostingListIntegerIndexSerializer* posting_list_serializer) {
  // Mmap the content of the crcs and info.
  ICING_ASSIGN_OR_RETURN(
      MemoryMappedFile metadata_mmapped_file,
      MemoryMappedFile::Create(filesystem, GetMetadataFilePath(working_path),
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               /*max_file_size=*/kMetadataFileSize,
                               /*pre_mapping_file_offset=*/0,
                               /*pre_mapping_mmap_size=*/kMetadataFileSize));
  if (metadata_mmapped_file.available_size() != kMetadataFileSize) {
    return absl_ports::FailedPreconditionError("Incorrect metadata file size");
  }

  // Initialize sorted_buckets
  int32_t pre_mapping_mmap_size = sizeof(Bucket) * (1 << 10);
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<FileBackedVector<Bucket>> sorted_buckets,
      FileBackedVector<Bucket>::Create(
          filesystem, GetSortedBucketsFilePath(working_path),
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
          FileBackedVector<Bucket>::kMaxFileSize,
          options.pre_mapping_fbv ? pre_mapping_mmap_size : 0));

  // Initialize unsorted_buckets
  pre_mapping_mmap_size = sizeof(Bucket) * kUnsortedBucketsLengthThreshold;
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<FileBackedVector<Bucket>> unsorted_buckets,
      FileBackedVector<Bucket>::Create(
          filesystem, GetUnsortedBucketsFilePath(working_path),
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
          FileBackedVector<Bucket>::kMaxFileSize,
          options.pre_mapping_fbv ? pre_mapping_mmap_size : 0));

  // Initialize flash_index_storage
  ICING_ASSIGN_OR_RETURN(
      FlashIndexStorage flash_index_storage,
      FlashIndexStorage::Create(GetFlashIndexStorageFilePath(working_path),
                                &filesystem, posting_list_serializer));

  // Create instance.
  auto integer_index_storage =
      std::unique_ptr<IntegerIndexStorage>(new IntegerIndexStorage(
          filesystem, std::move(working_path), std::move(options),
          posting_list_serializer,
          std::make_unique<MemoryMappedFile>(std::move(metadata_mmapped_file)),
          std::move(sorted_buckets), std::move(unsorted_buckets),
          std::make_unique<FlashIndexStorage>(std::move(flash_index_storage))));
  // Initialize existing PersistentStorage. Checksums will be validated.
  ICING_RETURN_IF_ERROR(integer_index_storage->InitializeExistingStorage());

  // Validate other values of info and options.
  // Magic should be consistent with the codebase.
  if (integer_index_storage->info().magic != Info::kMagic) {
    return absl_ports::FailedPreconditionError("Incorrect magic value");
  }

  return integer_index_storage;
}

/* static */ libtextclassifier3::Status
IntegerIndexStorage::FlushDataIntoNewSortedBucket(
    int64_t key_lower, int64_t key_upper, std::vector<IntegerIndexData>&& data,
    IntegerIndexStorage* storage) {
  storage->SetDirty();

  if (data.empty()) {
    return storage->sorted_buckets_->Append(Bucket(
        key_lower, key_upper, PostingListIdentifier::kInvalid, /*num_data=*/0));
  }

  ICING_ASSIGN_OR_RETURN(
      PostingListIdentifier pl_id,
      FlushDataIntoPostingLists(storage->flash_index_storage_.get(),
                                storage->posting_list_serializer_, data.begin(),
                                data.end()));

  storage->info().num_data += data.size();
  return storage->sorted_buckets_->Append(
      Bucket(key_lower, key_upper, pl_id, data.size()));
}

libtextclassifier3::Status IntegerIndexStorage::PersistStoragesToDisk(
    bool force) {
  if (!force && !is_storage_dirty()) {
    return libtextclassifier3::Status::OK;
  }

  ICING_RETURN_IF_ERROR(sorted_buckets_->PersistToDisk());
  ICING_RETURN_IF_ERROR(unsorted_buckets_->PersistToDisk());
  if (!flash_index_storage_->PersistToDisk()) {
    return absl_ports::InternalError(
        "Fail to persist FlashIndexStorage to disk");
  }
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status IntegerIndexStorage::PersistMetadataToDisk(
    bool force) {
  // We can skip persisting metadata to disk only if both info and storage are
  // clean.
  if (!force && !is_info_dirty() && !is_storage_dirty()) {
    return libtextclassifier3::Status::OK;
  }

  // Changes should have been applied to the underlying file when using
  // MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, but call msync() as an
  // extra safety step to ensure they are written out.
  return metadata_mmapped_file_->PersistToDisk();
}

libtextclassifier3::StatusOr<Crc32> IntegerIndexStorage::ComputeInfoChecksum(
    bool force) {
  if (!force && !is_info_dirty()) {
    return Crc32(crcs().component_crcs.info_crc);
  }

  return info().ComputeChecksum();
}

libtextclassifier3::StatusOr<Crc32>
IntegerIndexStorage::ComputeStoragesChecksum(bool force) {
  if (!force && !is_storage_dirty()) {
    return Crc32(crcs().component_crcs.storages_crc);
  }

  // Compute crcs
  ICING_ASSIGN_OR_RETURN(Crc32 sorted_buckets_crc,
                         sorted_buckets_->ComputeChecksum());
  ICING_ASSIGN_OR_RETURN(Crc32 unsorted_buckets_crc,
                         unsorted_buckets_->ComputeChecksum());

  // TODO(b/259744228): implement and include flash_index_storage checksum
  return Crc32(sorted_buckets_crc.Get() ^ unsorted_buckets_crc.Get());
}

libtextclassifier3::StatusOr<std::vector<IntegerIndexStorage::Bucket>>
IntegerIndexStorage::AddKeysIntoBucketAndSplitIfNecessary(
    DocumentId document_id, SectionId section_id,
    const std::vector<int64_t>::const_iterator& it_start,
    const std::vector<int64_t>::const_iterator& it_end,
    FileBackedVector<Bucket>::MutableView& mutable_bucket) {
  int32_t num_data_in_bucket = mutable_bucket.Get().num_data();
  int32_t num_new_data = std::distance(it_start, it_end);
  if (mutable_bucket.Get().key_lower() < mutable_bucket.Get().key_upper() &&
      num_new_data + num_data_in_bucket >
          options_.num_data_threshold_for_bucket_split) {
    // Split bucket.

    // 1. Read all data and free all posting lists.
    std::vector<IntegerIndexData> all_data;
    if (mutable_bucket.Get().posting_list_identifier().is_valid()) {
      ICING_ASSIGN_OR_RETURN(
          std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor,
          PostingListIntegerIndexAccessor::CreateFromExisting(
              flash_index_storage_.get(), posting_list_serializer_,
              mutable_bucket.Get().posting_list_identifier()));
      ICING_ASSIGN_OR_RETURN(all_data, pl_accessor->GetAllDataAndFree());
    }

    // 2. Append all new data.
    all_data.reserve(all_data.size() + num_new_data);
    for (auto it = it_start; it != it_end; ++it) {
      all_data.push_back(IntegerIndexData(section_id, document_id, *it));
    }

    // 3. Run bucket splitting algorithm to decide new buckets and dispatch
    //    data.
    //    - # of data in a full bucket =
    //      options_.num_data_threshold_for_bucket_split.
    //    - Bucket splitting logic will be invoked if adding new data
    //      (num_new_data >= 1) into a full bucket.
    //    - In order to achieve good (amortized) time complexity, we want # of
    //      data in new buckets to be around half_of_threshold (i.e.
    //      options_.num_data_threshold_for_bucket_split / 2).
    //    - Using half_of_threshold as the cutoff threshold will cause splitting
    //      buckets with [half_of_threshold, half_of_threshold, num_new_data]
    //      data, which is not ideal because num_new_data is usually small.
    //    - Thus, we pick (half_of_threshold + kNumDataAfterSplitAdjustment) as
    //      the cutoff threshold to avoid over-splitting. It can tolerate
    //      num_new_data up to (2 * kNumDataAfterSplitAdjustment) and
    //      split only 2 buckets (instead of 3) with
    //      [half_of_threshold + kNumDataAfterSplitAdjustment,
    //       half_of_threshold + (kNumDataAfterSplitAdjustment - num_new_data)].
    int32_t cutoff_threshold =
        options_.num_data_threshold_for_bucket_split / 2 +
        kNumDataAfterSplitAdjustment;
    std::vector<integer_index_bucket_util::DataRangeAndBucketInfo>
        new_bucket_infos = integer_index_bucket_util::Split(
            all_data, mutable_bucket.Get().key_lower(),
            mutable_bucket.Get().key_upper(), cutoff_threshold);
    if (new_bucket_infos.empty()) {
      ICING_LOG(WARNING)
          << "No buckets after splitting. This should not happen.";
      return absl_ports::InternalError("Split error");
    }

    // 4. Flush data and create new buckets.
    std::vector<Bucket> new_buckets;
    for (int i = 0; i < new_bucket_infos.size(); ++i) {
      int32_t num_data_in_new_bucket =
          std::distance(new_bucket_infos[i].start, new_bucket_infos[i].end);
      ICING_ASSIGN_OR_RETURN(
          PostingListIdentifier pl_id,
          FlushDataIntoPostingLists(
              flash_index_storage_.get(), posting_list_serializer_,
              new_bucket_infos[i].start, new_bucket_infos[i].end));
      if (i == 0) {
        // Reuse mutable_bucket
        mutable_bucket.Get().set_key_lower(new_bucket_infos[i].key_lower);
        mutable_bucket.Get().set_key_upper(new_bucket_infos[i].key_upper);
        mutable_bucket.Get().set_posting_list_identifier(pl_id);
        mutable_bucket.Get().set_num_data(num_data_in_new_bucket);
      } else {
        new_buckets.push_back(Bucket(new_bucket_infos[i].key_lower,
                                     new_bucket_infos[i].key_upper, pl_id,
                                     num_data_in_new_bucket));
      }
    }

    return new_buckets;
  }

  // Otherwise, we don't need to split bucket. Just simply add all new data into
  // the bucket.
  std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor;
  if (mutable_bucket.Get().posting_list_identifier().is_valid()) {
    ICING_ASSIGN_OR_RETURN(
        pl_accessor, PostingListIntegerIndexAccessor::CreateFromExisting(
                         flash_index_storage_.get(), posting_list_serializer_,
                         mutable_bucket.Get().posting_list_identifier()));
  } else {
    ICING_ASSIGN_OR_RETURN(
        pl_accessor, PostingListIntegerIndexAccessor::Create(
                         flash_index_storage_.get(), posting_list_serializer_));
  }

  for (auto it = it_start; it != it_end; ++it) {
    ICING_RETURN_IF_ERROR(pl_accessor->PrependData(
        IntegerIndexData(section_id, document_id, *it)));
  }

  PostingListAccessor::FinalizeResult result =
      std::move(*pl_accessor).Finalize();
  if (!result.status.ok()) {
    return result.status;
  }
  if (!result.id.is_valid()) {
    return absl_ports::InternalError("Fail to flush data into posting list(s)");
  }

  mutable_bucket.Get().set_posting_list_identifier(result.id);
  // We've already verified num_new_data won't exceed the limit of the entire
  // storage, so it is safe to add to the counter of the bucket.
  mutable_bucket.Get().set_num_data(num_data_in_bucket + num_new_data);

  return std::vector<Bucket>();
}

libtextclassifier3::Status IntegerIndexStorage::SortBuckets() {
  if (unsorted_buckets_->num_elements() == 0) {
    return libtextclassifier3::Status::OK;
  }

  int32_t sorted_len = sorted_buckets_->num_elements();
  int32_t unsorted_len = unsorted_buckets_->num_elements();
  if (sorted_len > FileBackedVector<Bucket>::kMaxNumElements - unsorted_len) {
    return absl_ports::OutOfRangeError(
        "Sorted buckets length exceeds the limit after merging");
  }

  ICING_RETURN_IF_ERROR(sorted_buckets_->Allocate(unsorted_len));

  // Sort unsorted_buckets_.
  ICING_RETURN_IF_ERROR(
      unsorted_buckets_->Sort(/*begin_idx=*/0, /*end_idx=*/unsorted_len));

  // Merge unsorted_buckets_ into sorted_buckets_ and clear unsorted_buckets_.
  // Note that we could have used std::sort + std::inplace_merge, but it is more
  // complicated to deal with FileBackedVector SetDirty logic, so implement our
  // own merging with FileBackedVector methods.
  //
  // Merge buckets from back. This could save some iterations and avoid setting
  // dirty for unchanged elements of the original sorted segments.
  // For example, we can avoid setting dirty for elements [1, 2, 3, 5] for the
  // following sorted/unsorted data:
  // - sorted: [1, 2, 3, 5, 8, 13, _, _, _, _)]
  // - unsorted: [6, 10, 14, 15]
  int32_t sorted_write_idx = sorted_len + unsorted_len - 1;
  int32_t sorted_curr_idx = sorted_len - 1;
  int32_t unsorted_curr_idx = unsorted_len - 1;
  while (unsorted_curr_idx >= 0) {
    if (sorted_curr_idx >= 0 && unsorted_buckets_->array()[unsorted_curr_idx] <
                                    sorted_buckets_->array()[sorted_curr_idx]) {
      ICING_RETURN_IF_ERROR(sorted_buckets_->Set(
          sorted_write_idx, sorted_buckets_->array()[sorted_curr_idx]));
      --sorted_curr_idx;

    } else {
      ICING_RETURN_IF_ERROR(sorted_buckets_->Set(
          sorted_write_idx, unsorted_buckets_->array()[unsorted_curr_idx]));
      --unsorted_curr_idx;
    }
    --sorted_write_idx;
  }

  ICING_RETURN_IF_ERROR(unsorted_buckets_->TruncateTo(0));

  return libtextclassifier3::Status::OK;
}

}  // namespace lib
}  // namespace icing
