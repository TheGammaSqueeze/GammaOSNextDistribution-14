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

#ifndef ICING_INDEX_NUMERIC_INTEGER_INDEX_STORAGE_H_
#define ICING_INDEX_NUMERIC_INTEGER_INDEX_STORAGE_H_

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/file/file-backed-vector.h"
#include "icing/file/filesystem.h"
#include "icing/file/memory-mapped-file.h"
#include "icing/file/persistent-storage.h"
#include "icing/file/posting_list/flash-index-storage.h"
#include "icing/file/posting_list/posting-list-identifier.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/numeric/integer-index-data.h"
#include "icing/index/numeric/posting-list-integer-index-serializer.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/util/crc32.h"

namespace icing {
namespace lib {

// IntegerIndexStorage: a class for indexing (persistent storage) and searching
// contents of integer type sections in documents.
// - Accepts new integer contents (a.k.a keys) and adds records (BasicHit, key)
//   into the integer index.
// - Stores records (BasicHit, key) in posting lists and compresses them.
// - Bucketizes these records by key to make range query more efficient and
//   manages them with the corresponding posting lists.
//   - When a posting list reaches the max size and is full, the mechanism of
//     PostingListAccessor is to create another new (max-size) posting list and
//     chain them together.
//   - It will be inefficient if we store all records in the same PL chain. E.g.
//     small range query needs to iterate through the whole PL chain but skips a
//     lot of non-relevant records (whose keys don't belong to the query range).
//   - Therefore, we implement the splitting mechanism to split a full max-size
//     posting list. Also adjust range of the original bucket and add new
//     buckets.
//   - Ranges of all buckets are disjoint and the union of them is [INT64_MIN,
//     INT64_MAX].
//   - Buckets should be sorted, so we can do binary search to find the desired
//     bucket(s). However, we may split a bucket into several buckets, and the
//     cost to insert newly created buckets is high.
//   - Thus, we introduce an unsorted bucket array for newly created buckets,
//     and merge unsorted buckets into the sorted bucket array only if length of
//     the unsorted bucket array exceeds the threshold. This mechanism will
//     reduce # of merging events and amortize the overall cost for bucket order
//     maintenance.
//     Note: some tree data structures (e.g. segment tree, B+ tree) maintain the
//     bucket order more efficiently than the sorted/unsorted bucket array
//     mechanism, but the implementation is more complicated and doesn't improve
//     the performance too much according to our analysis, so currently we
//     choose sorted/unsorted bucket array.
//   - Then we do binary search on the sorted bucket array and sequential search
//     on the unsorted bucket array.
class IntegerIndexStorage : public PersistentStorage {
 public:
  struct Info {
    static constexpr int32_t kMagic = 0x6470e547;

    int32_t magic;
    int32_t num_data;

    Crc32 ComputeChecksum() const {
      return Crc32(
          std::string_view(reinterpret_cast<const char*>(this), sizeof(Info)));
    }
  } __attribute__((packed));
  static_assert(sizeof(Info) == 8, "");

  // Bucket
  class Bucket {
   public:
    // Absolute max # of buckets allowed. Since the absolute max file size of
    // FileBackedVector on 32-bit platform is ~2^28, we can at most have ~13.4M
    // buckets. To make it power of 2, round it down to 2^23. Also since we're
    // using FileBackedVector to store buckets, add some static_asserts to
    // ensure numbers here are compatible with FileBackedVector.
    static constexpr int32_t kMaxNumBuckets = 1 << 23;

    explicit Bucket(int64_t key_lower, int64_t key_upper,
                    PostingListIdentifier posting_list_identifier =
                        PostingListIdentifier::kInvalid,
                    int32_t num_data = 0)
        : key_lower_(key_lower),
          key_upper_(key_upper),
          posting_list_identifier_(posting_list_identifier),
          num_data_(num_data) {}

    bool operator<(const Bucket& other) const {
      return key_lower_ < other.key_lower_;
    }

    // For FileBackedVector
    bool operator==(const Bucket& other) const {
      return key_lower_ == other.key_lower_ && key_upper_ == other.key_upper_ &&
             posting_list_identifier_ == other.posting_list_identifier_;
    }

    int64_t key_lower() const { return key_lower_; }

    int64_t key_upper() const { return key_upper_; }

    void set_key_lower(int64_t key_lower) { key_lower_ = key_lower; }

    void set_key_upper(int64_t key_upper) { key_upper_ = key_upper; }

    PostingListIdentifier posting_list_identifier() const {
      return posting_list_identifier_;
    }
    void set_posting_list_identifier(
        PostingListIdentifier posting_list_identifier) {
      posting_list_identifier_ = posting_list_identifier;
    }

    int32_t num_data() const { return num_data_; }
    void set_num_data(int32_t num_data) { num_data_ = num_data; }

   private:
    int64_t key_lower_;
    int64_t key_upper_;
    PostingListIdentifier posting_list_identifier_;
    int32_t num_data_;
  } __attribute__((packed));
  static_assert(sizeof(Bucket) == 24, "");
  static_assert(sizeof(Bucket) == FileBackedVector<Bucket>::kElementTypeSize,
                "Bucket type size is inconsistent with FileBackedVector "
                "element type size");
  static_assert(Bucket::kMaxNumBuckets <=
                    (FileBackedVector<Bucket>::kMaxFileSize -
                     FileBackedVector<Bucket>::Header::kHeaderSize) /
                        FileBackedVector<Bucket>::kElementTypeSize,
                "Max # of buckets cannot fit into FileBackedVector");

  struct Options {
    // - According to the benchmark result, the more # of buckets, the higher
    //   latency for range query. Therefore, this number cannot be too small to
    //   avoid splitting bucket too aggressively.
    // - We use `num_data_threshold_for_bucket_split / 2 + 5` as the cutoff
    //   threshold after splitting. This number cannot be too small (e.g. 10)
    //   because in this case we will have similar # of data in a single bucket
    //   before and after splitting, which contradicts the purpose of splitting.
    // - For convenience, let's set 64 as the minimum value.
    static constexpr int32_t kMinNumDataThresholdForBucketSplit = 64;

    explicit Options(int32_t num_data_threshold_for_bucket_split_in,
                     bool pre_mapping_fbv_in)
        : num_data_threshold_for_bucket_split(
              num_data_threshold_for_bucket_split_in),
          pre_mapping_fbv(pre_mapping_fbv_in) {}

    explicit Options(std::vector<Bucket> custom_init_sorted_buckets_in,
                     std::vector<Bucket> custom_init_unsorted_buckets_in,
                     int32_t num_data_threshold_for_bucket_split_in,
                     bool pre_mapping_fbv_in)
        : custom_init_sorted_buckets(std::move(custom_init_sorted_buckets_in)),
          custom_init_unsorted_buckets(
              std::move(custom_init_unsorted_buckets_in)),
          num_data_threshold_for_bucket_split(
              num_data_threshold_for_bucket_split_in),
          pre_mapping_fbv(pre_mapping_fbv_in) {}

    bool IsValid() const;

    bool HasCustomInitBuckets() const {
      return !custom_init_sorted_buckets.empty() ||
             !custom_init_unsorted_buckets.empty();
    }

    // Custom buckets when initializing new files. If both are empty, then the
    // initial bucket is (INT64_MIN, INT64_MAX). Usually we only set them in the
    // unit test. Note that all buckets in custom_init_sorted_buckets and
    // custom_init_unsorted_buckets should be disjoint and the range union
    // should be [INT64_MIN, INT64_MAX].
    std::vector<Bucket> custom_init_sorted_buckets;
    std::vector<Bucket> custom_init_unsorted_buckets;

    // Threshold for invoking bucket splitting. If # of data in a bucket exceeds
    // this number after adding new data, then it will invoke bucket splitting
    // logic.
    //
    // Note: num_data_threshold_for_bucket_split should be >=
    //   kMinNumDataThresholdForBucketSplit.
    int32_t num_data_threshold_for_bucket_split;

    // Flag indicating whether memory map max possible file size for underlying
    // FileBackedVector before growing the actual file size.
    bool pre_mapping_fbv;
  };

  // Metadata file layout: <Crcs><Info>
  static constexpr int32_t kCrcsMetadataFileOffset = 0;
  static constexpr int32_t kInfoMetadataFileOffset =
      static_cast<int32_t>(sizeof(Crcs));
  static constexpr int32_t kMetadataFileSize = sizeof(Crcs) + sizeof(Info);
  static_assert(kMetadataFileSize == 20, "");

  static constexpr WorkingPathType kWorkingPathType =
      WorkingPathType::kDirectory;
  static constexpr std::string_view kFilePrefix = "integer_index_storage";

  // Default # of data threshold for bucket splitting during indexing (AddKeys).
  // When # of data in a bucket reaches this number, we will try to split data
  // into multiple buckets according to their keys.
  static constexpr int32_t kDefaultNumDataThresholdForBucketSplit = 65536;

  // # of data threshold for bucket merging during optimization (TransferIndex)
  // = kNumDataThresholdRatioForBucketMerge *
  //   options.num_data_threshold_for_bucket_split
  //
  // If total # data of adjacent buckets exceed this threshold, then flush the
  // accumulated data. Otherwise merge buckets and their data.
  static constexpr double kNumDataThresholdRatioForBucketMerge = 0.7;

  // Length threshold to sort and merge unsorted buckets into sorted buckets. If
  // the length of unsorted_buckets exceed the threshold, then call
  // SortBuckets().
  // TODO(b/259743562): decide if removing unsorted buckets given that we
  //   changed bucket splitting threshold and # of buckets are small now.
  static constexpr int32_t kUnsortedBucketsLengthThreshold = 5;

  // Creates a new IntegerIndexStorage instance to index integers (for a single
  // property). If any of the underlying file is missing, then delete the whole
  // working_path and (re)initialize with new ones. Otherwise initialize and
  // create the instance by existing files.
  //
  // filesystem: Object to make system level calls
  // working_path: Specifies the working path for PersistentStorage.
  //               IntegerIndexStorage uses working path as working directory
  //               and all related files will be stored under this directory. It
  //               takes full ownership and of working_path_, including
  //               creation/deletion. It is the caller's responsibility to
  //               specify correct working path and avoid mixing different
  //               persistent storages together under the same path. Also the
  //               caller has the ownership for the parent directory of
  //               working_path_, and it is responsible for parent directory
  //               creation/deletion. See PersistentStorage for more details
  //               about the concept of working_path.
  // options: Options instance.
  // posting_list_serializer: a PostingListIntegerIndexSerializer instance to
  //                          serialize/deserialize integer index data to/from
  //                          posting lists.
  //
  // Returns:
  //   - INVALID_ARGUMENT_ERROR if any value in options is invalid.
  //   - FAILED_PRECONDITION_ERROR if the file checksum doesn't match the stored
  //                               checksum.
  //   - INTERNAL_ERROR on I/O errors.
  //   - Any FileBackedVector/FlashIndexStorage errors.
  static libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndexStorage>>
  Create(const Filesystem& filesystem, std::string working_path,
         Options options,
         PostingListIntegerIndexSerializer* posting_list_serializer);

  // Deletes IntegerIndexStorage under working_path.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  static libtextclassifier3::Status Discard(const Filesystem& filesystem,
                                            const std::string& working_path) {
    return PersistentStorage::Discard(filesystem, working_path,
                                      kWorkingPathType);
  }

  // Delete copy and move constructor/assignment operator.
  IntegerIndexStorage(const IntegerIndexStorage&) = delete;
  IntegerIndexStorage& operator=(const IntegerIndexStorage&) = delete;

  IntegerIndexStorage(IntegerIndexStorage&&) = delete;
  IntegerIndexStorage& operator=(IntegerIndexStorage&&) = delete;

  ~IntegerIndexStorage() override;

  // Batch adds new keys (of the same DocumentId and SectionId) into the integer
  // index storage.
  // Note that since we separate different property names into different integer
  // index storages, it is impossible to have keys in a single document across
  // multiple sections to add into the same integer index storage.
  //
  // Returns:
  //   - OK on success
  //   - RESOURCE_EXHAUSTED_ERROR if # of integers in this storage exceed
  //     INT_MAX after adding new_keys
  //   - Any FileBackedVector or PostingList errors
  libtextclassifier3::Status AddKeys(DocumentId document_id,
                                     SectionId section_id,
                                     std::vector<int64_t>&& new_keys);

  // Returns a DocHitInfoIteratorNumeric<int64_t> (in DocHitInfoIterator
  // interface type format) for iterating through all docs which have the
  // specified (integer) property contents in range [query_key_lower,
  // query_key_upper].
  // When iterating through all relevant doc hits, it:
  // - Merges multiple SectionIds of doc hits with same DocumentId into a single
  //   SectionIdMask and constructs DocHitInfo.
  // - Returns DocHitInfo in descending DocumentId order.
  //
  // Returns:
  //   - On success: a DocHitInfoIterator(Numeric)
  //   - INVALID_ARGUMENT_ERROR if query_key_lower > query_key_upper
  //   - Any FileBackedVector or PostingList errors
  libtextclassifier3::StatusOr<std::unique_ptr<DocHitInfoIterator>> GetIterator(
      int64_t query_key_lower, int64_t query_key_upper) const;

  // Transfers integer index data from the current storage to new_storage and
  // optimizes buckets (for new_storage only), i.e. merging adjacent buckets if
  // total # of data among them are less than or equal to
  // kNumDataThresholdForBucketMerge.
  //
  // REQUIRES: new_storage should be a newly created storage instance, i.e. not
  //   contain any data. Otherwise, existing data and posting lists won't be
  //   freed and space will be wasted.
  //
  // Returns:
  //   - OK on success
  //   - OUT_OF_RANGE_ERROR if sorted buckets length exceeds the limit after
  //     merging
  //   - INTERNAL_ERROR on IO error
  libtextclassifier3::Status TransferIndex(
      const std::vector<DocumentId>& document_id_old_to_new,
      IntegerIndexStorage* new_storage) const;

  int32_t num_data() const { return info().num_data; }

 private:
  static constexpr int8_t kNumDataAfterSplitAdjustment = 5;

  explicit IntegerIndexStorage(
      const Filesystem& filesystem, std::string&& working_path,
      Options&& options,
      PostingListIntegerIndexSerializer* posting_list_serializer,
      std::unique_ptr<MemoryMappedFile> metadata_mmapped_file,
      std::unique_ptr<FileBackedVector<Bucket>> sorted_buckets,
      std::unique_ptr<FileBackedVector<Bucket>> unsorted_buckets,
      std::unique_ptr<FlashIndexStorage> flash_index_storage)
      : PersistentStorage(filesystem, std::move(working_path),
                          kWorkingPathType),
        options_(std::move(options)),
        posting_list_serializer_(posting_list_serializer),
        metadata_mmapped_file_(std::move(metadata_mmapped_file)),
        sorted_buckets_(std::move(sorted_buckets)),
        unsorted_buckets_(std::move(unsorted_buckets)),
        flash_index_storage_(std::move(flash_index_storage)),
        is_info_dirty_(false),
        is_storage_dirty_(false) {}

  static libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndexStorage>>
  InitializeNewFiles(
      const Filesystem& filesystem, std::string&& working_path,
      Options&& options,
      PostingListIntegerIndexSerializer* posting_list_serializer);

  static libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndexStorage>>
  InitializeExistingFiles(
      const Filesystem& filesystem, std::string&& working_path,
      Options&& options,
      PostingListIntegerIndexSerializer* posting_list_serializer);

  // Flushes data into posting list(s), creates a new bucket with range
  // [key_lower, key_upper], and appends it into sorted buckets for storage.
  // It is a helper function for TransferIndex.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR if fails to write existing data into posting list(s)
  //   - Any FileBackedVector or PostingList errors
  static libtextclassifier3::Status FlushDataIntoNewSortedBucket(
      int64_t key_lower, int64_t key_upper,
      std::vector<IntegerIndexData>&& data, IntegerIndexStorage* storage);

  // Flushes contents of all storages to underlying files.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::Status PersistStoragesToDisk(bool force) override;

  // Flushes contents of metadata file.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::Status PersistMetadataToDisk(bool force) override;

  // Computes and returns Info checksum.
  //
  // Returns:
  //   - Crc of the Info on success
  libtextclassifier3::StatusOr<Crc32> ComputeInfoChecksum(bool force) override;

  // Computes and returns all storages checksum. Checksums of sorted_buckets_,
  // unsorted_buckets_ will be combined together by XOR.
  // TODO(b/259744228): implement and include flash_index_storage checksum
  //
  // Returns:
  //   - Crc of all storages on success
  //   - INTERNAL_ERROR if any data inconsistency
  libtextclassifier3::StatusOr<Crc32> ComputeStoragesChecksum(
      bool force) override;

  // Helper function to add keys in range [it_start, it_end) into the given
  // bucket. It handles the bucket and its corresponding posting list(s) to make
  // searching and indexing efficient.
  //
  // When the (single) posting list of the bucket is full:
  // - If the size of posting list hasn't reached the max size, then just simply
  //   add a new key into it, and PostingListAccessor mechanism will
  //   automatically double the size of the posting list.
  // - Else:
  //   - If the bucket is splittable (i.e. key_lower < key_upper), then split it
  //     into several new buckets with new ranges, and split the data (according
  //     to their keys and the range of new buckets) of the original posting
  //     list into several new posting lists.
  //   - Otherwise, just simply add a new key into it, and PostingListAccessor
  //     mechanism will automatically create a new max size posting list and
  //     chain them.
  //
  // Returns:
  //   - On success: a vector of new Buckets (to add into the unsorted bucket
  //     array later)
  //   - Any FileBackedVector or PostingList errors
  libtextclassifier3::StatusOr<std::vector<Bucket>>
  AddKeysIntoBucketAndSplitIfNecessary(
      DocumentId document_id, SectionId section_id,
      const std::vector<int64_t>::const_iterator& it_start,
      const std::vector<int64_t>::const_iterator& it_end,
      FileBackedVector<Bucket>::MutableView& mutable_bucket);

  // Merges all unsorted buckets into sorted buckets and clears unsorted
  // buckets.
  //
  // Returns:
  //   - OK on success
  //   - OUT_OF_RANGE_ERROR if sorted buckets length exceeds the limit after
  //     merging
  //   - Any FileBackedVector errors
  libtextclassifier3::Status SortBuckets();

  Crcs& crcs() override {
    return *reinterpret_cast<Crcs*>(metadata_mmapped_file_->mutable_region() +
                                    kCrcsMetadataFileOffset);
  }

  const Crcs& crcs() const override {
    return *reinterpret_cast<const Crcs*>(metadata_mmapped_file_->region() +
                                          kCrcsMetadataFileOffset);
  }

  Info& info() {
    return *reinterpret_cast<Info*>(metadata_mmapped_file_->mutable_region() +
                                    kInfoMetadataFileOffset);
  }

  const Info& info() const {
    return *reinterpret_cast<const Info*>(metadata_mmapped_file_->region() +
                                          kInfoMetadataFileOffset);
  }

  void SetInfoDirty() { is_info_dirty_ = true; }
  // When storage is dirty, we have to set info dirty as well. So just expose
  // SetDirty to set both.
  void SetDirty() {
    is_info_dirty_ = true;
    is_storage_dirty_ = true;
  }

  bool is_info_dirty() const { return is_info_dirty_; }
  bool is_storage_dirty() const { return is_storage_dirty_; }

  Options options_;

  PostingListIntegerIndexSerializer* posting_list_serializer_;  // Does not own.

  std::unique_ptr<MemoryMappedFile> metadata_mmapped_file_;
  std::unique_ptr<FileBackedVector<Bucket>> sorted_buckets_;
  std::unique_ptr<FileBackedVector<Bucket>> unsorted_buckets_;
  std::unique_ptr<FlashIndexStorage> flash_index_storage_;

  bool is_info_dirty_;
  bool is_storage_dirty_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_NUMERIC_INTEGER_INDEX_STORAGE_H_
