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

#ifndef ICING_FILE_PERSISTENT_HASH_MAP_H_
#define ICING_FILE_PERSISTENT_HASH_MAP_H_

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/file/file-backed-vector.h"
#include "icing/file/filesystem.h"
#include "icing/file/memory-mapped-file.h"
#include "icing/file/persistent-storage.h"
#include "icing/util/crc32.h"

namespace icing {
namespace lib {

// Low level persistent hash map.
// It supports variant length serialized key + fixed length serialized value.
// Key and value can be any type, but callers should serialize key/value by
// themselves and pass raw bytes into the hash map, and the serialized key
// should not contain termination character '\0'.
class PersistentHashMap : public PersistentStorage {
 public:
  // For iterating through persistent hash map. The order is not guaranteed.
  //
  // Not thread-safe.
  //
  // Change in underlying persistent hash map invalidates iterator.
  class Iterator {
   public:
    // Advance to the next entry.
    //
    // Returns:
    //   True on success, otherwise false.
    bool Advance();

    int32_t GetIndex() const { return curr_kv_idx_; }

    // Get the key.
    //
    // REQUIRES: The preceding call for Advance() is true.
    std::string_view GetKey() const {
      return std::string_view(map_->kv_storage_->array() + curr_kv_idx_,
                              curr_key_len_);
    }

    // Get the memory mapped address of the value.
    //
    // REQUIRES: The preceding call for Advance() is true.
    const void* GetValue() const {
      return static_cast<const void*>(map_->kv_storage_->array() +
                                      curr_kv_idx_ + curr_key_len_ + 1);
    }

   private:
    explicit Iterator(const PersistentHashMap* map)
        : map_(map), curr_kv_idx_(0), curr_key_len_(0) {}

    // Does not own
    const PersistentHashMap* map_;

    int32_t curr_kv_idx_;
    int32_t curr_key_len_;

    friend class PersistentHashMap;
  };

  // Metadata file layout: <Crcs><Info>
  static constexpr int32_t kCrcsMetadataFileOffset = 0;
  static constexpr int32_t kInfoMetadataFileOffset =
      static_cast<int32_t>(sizeof(Crcs));

  struct Info {
    static constexpr int32_t kMagic = 0x653afd7b;

    int32_t magic;
    int32_t value_type_size;
    int32_t max_load_factor_percent;
    int32_t num_deleted_entries;
    int32_t num_deleted_key_value_bytes;

    Crc32 ComputeChecksum() const {
      return Crc32(
          std::string_view(reinterpret_cast<const char*>(this), sizeof(Info)));
    }
  } __attribute__((packed));
  static_assert(sizeof(Info) == 20, "");

  static constexpr int32_t kMetadataFileSize = sizeof(Crcs) + sizeof(Info);
  static_assert(kMetadataFileSize == 32, "");

  // Bucket
  class Bucket {
   public:
    // Absolute max # of buckets allowed. Since we're using FileBackedVector to
    // store buckets, add some static_asserts to ensure numbers here are
    // compatible with FileBackedVector.
    static constexpr int32_t kMaxNumBuckets = 1 << 24;

    explicit Bucket(int32_t head_entry_index = Entry::kInvalidIndex)
        : head_entry_index_(head_entry_index) {}

    // For FileBackedVector
    bool operator==(const Bucket& other) const {
      return head_entry_index_ == other.head_entry_index_;
    }

    int32_t head_entry_index() const { return head_entry_index_; }
    void set_head_entry_index(int32_t head_entry_index) {
      head_entry_index_ = head_entry_index;
    }

   private:
    int32_t head_entry_index_;
  } __attribute__((packed));
  static_assert(sizeof(Bucket) == 4, "");
  static_assert(sizeof(Bucket) == FileBackedVector<Bucket>::kElementTypeSize,
                "Bucket type size is inconsistent with FileBackedVector "
                "element type size");
  static_assert(Bucket::kMaxNumBuckets <=
                    (FileBackedVector<Bucket>::kMaxFileSize -
                     FileBackedVector<Bucket>::Header::kHeaderSize) /
                        FileBackedVector<Bucket>::kElementTypeSize,
                "Max # of buckets cannot fit into FileBackedVector");

  // Entry
  class Entry {
   public:
    // Absolute max # of entries allowed. Since we're using FileBackedVector to
    // store entries, add some static_asserts to ensure numbers here are
    // compatible with FileBackedVector.
    //
    // Still the actual max # of entries are determined by key-value storage,
    // since length of the key varies and affects # of actual key-value pairs
    // that can be stored.
    static constexpr int32_t kMaxNumEntries = 1 << 23;
    static constexpr int32_t kMaxIndex = kMaxNumEntries - 1;
    static constexpr int32_t kInvalidIndex = -1;

    explicit Entry(int32_t key_value_index, int32_t next_entry_index)
        : key_value_index_(key_value_index),
          next_entry_index_(next_entry_index) {}

    bool operator==(const Entry& other) const {
      return key_value_index_ == other.key_value_index_ &&
             next_entry_index_ == other.next_entry_index_;
    }

    int32_t key_value_index() const { return key_value_index_; }
    void set_key_value_index(int32_t key_value_index) {
      key_value_index_ = key_value_index;
    }

    int32_t next_entry_index() const { return next_entry_index_; }
    void set_next_entry_index(int32_t next_entry_index) {
      next_entry_index_ = next_entry_index;
    }

   private:
    int32_t key_value_index_;
    int32_t next_entry_index_;
  } __attribute__((packed));
  static_assert(sizeof(Entry) == 8, "");
  static_assert(sizeof(Entry) == FileBackedVector<Entry>::kElementTypeSize,
                "Entry type size is inconsistent with FileBackedVector "
                "element type size");
  static_assert(Entry::kMaxNumEntries <=
                    (FileBackedVector<Entry>::kMaxFileSize -
                     FileBackedVector<Entry>::Header::kHeaderSize) /
                        FileBackedVector<Entry>::kElementTypeSize,
                "Max # of entries cannot fit into FileBackedVector");

  // Key-value serialized type
  static constexpr int32_t kMaxKVTotalByteSize = 1 << 28;
  static constexpr int32_t kMaxKVIndex = kMaxKVTotalByteSize - 1;
  static constexpr int32_t kInvalidKVIndex = -1;
  static_assert(sizeof(char) == FileBackedVector<char>::kElementTypeSize,
                "Char type size is inconsistent with FileBackedVector element "
                "type size");
  static_assert(kMaxKVTotalByteSize <=
                    FileBackedVector<char>::kMaxFileSize -
                        FileBackedVector<char>::Header::kHeaderSize,
                "Max total byte size of key value pairs cannot fit into "
                "FileBackedVector");

  static constexpr int32_t kMaxValueTypeSize = 1 << 10;

  struct Options {
    static constexpr int32_t kDefaultMaxLoadFactorPercent = 100;
    static constexpr int32_t kDefaultAverageKVByteSize = 32;
    static constexpr int32_t kDefaultInitNumBuckets = 1 << 13;

    explicit Options(
        int32_t value_type_size_in,
        int32_t max_num_entries_in = Entry::kMaxNumEntries,
        int32_t max_load_factor_percent_in = kDefaultMaxLoadFactorPercent,
        int32_t average_kv_byte_size_in = kDefaultAverageKVByteSize,
        int32_t init_num_buckets_in = kDefaultInitNumBuckets,
        bool pre_mapping_fbv_in = false)
        : value_type_size(value_type_size_in),
          max_num_entries(max_num_entries_in),
          max_load_factor_percent(max_load_factor_percent_in),
          average_kv_byte_size(average_kv_byte_size_in),
          init_num_buckets(init_num_buckets_in),
          pre_mapping_fbv(pre_mapping_fbv_in) {}

    bool IsValid() const;

    // (fixed) size of the serialized value type for hash map.
    int32_t value_type_size;

    // Max # of entries, default Entry::kMaxNumEntries.
    int32_t max_num_entries;

    // Percentage of the max loading for the hash map. If load_factor_percent
    // exceeds max_load_factor_percent, then rehash will be invoked (and # of
    // buckets will be doubled).
    //   load_factor_percent = 100 * num_keys / num_buckets
    //
    // Note that load_factor_percent exceeding 100 is considered valid.
    int32_t max_load_factor_percent;

    // Average byte size of a key value pair. It is used to estimate kv_storage_
    // pre_mapping_mmap_size.
    int32_t average_kv_byte_size;

    // Initial # of buckets for the persistent hash map. It should be 2's power.
    // It is used when creating new persistent hash map and ignored when
    // creating the instance from existing files.
    int32_t init_num_buckets;

    // Flag indicating whether memory map max possible file size for underlying
    // FileBackedVector before growing the actual file size.
    bool pre_mapping_fbv;
  };

  static constexpr WorkingPathType kWorkingPathType =
      WorkingPathType::kDirectory;
  static constexpr std::string_view kFilePrefix = "persistent_hash_map";

  // Creates a new PersistentHashMap to read/write/delete key value pairs.
  //
  // filesystem: Object to make system level calls
  // working_path: Specifies the working path for PersistentStorage.
  //               PersistentHashMap uses working path as working directory and
  //               all related files will be stored under this directory. It
  //               takes full ownership and of working_path_, including
  //               creation/deletion. It is the caller's responsibility to
  //               specify correct working path and avoid mixing different
  //               persistent storages together under the same path. Also the
  //               caller has the ownership for the parent directory of
  //               working_path_, and it is responsible for parent directory
  //               creation/deletion. See PersistentStorage for more details
  //               about the concept of working_path.
  // options: Options instance.
  //
  // Returns:
  //   INVALID_ARGUMENT_ERROR if any value in options is invalid.
  //   FAILED_PRECONDITION_ERROR if the file checksum doesn't match the stored
  //                             checksum or any other inconsistency.
  //   INTERNAL_ERROR on I/O errors.
  //   Any FileBackedVector errors.
  static libtextclassifier3::StatusOr<std::unique_ptr<PersistentHashMap>>
  Create(const Filesystem& filesystem, std::string working_path,
         Options options);

  // Deletes PersistentHashMap under working_path.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  static libtextclassifier3::Status Discard(const Filesystem& filesystem,
                                            std::string working_path) {
    return PersistentStorage::Discard(filesystem, working_path,
                                      kWorkingPathType);
  }

  ~PersistentHashMap() override;

  // Update a key value pair. If key does not exist, then insert (key, value)
  // into the storage. Otherwise overwrite the value into the storage.
  //
  // REQUIRES: the buffer pointed to by value must be of value_size()
  //
  // Returns:
  //   OK on success
  //   RESOURCE_EXHAUSTED_ERROR if # of entries reach options_.max_num_entries
  //   INVALID_ARGUMENT_ERROR if the key is invalid (i.e. contains '\0')
  //   INTERNAL_ERROR on I/O error or any data inconsistency
  //   Any FileBackedVector errors
  libtextclassifier3::Status Put(std::string_view key, const void* value);

  // If key does not exist, then insert (key, next_value) into the storage.
  // Otherwise, copy the hash map value into next_value.
  //
  // REQUIRES: the buffer pointed to by next_value must be of value_size()
  //
  // Returns:
  //   OK on success
  //   INVALID_ARGUMENT_ERROR if the key is invalid (i.e. contains '\0')
  //   INTERNAL_ERROR on I/O error or any data inconsistency
  //   Any FileBackedVector errors
  libtextclassifier3::Status GetOrPut(std::string_view key, void* next_value);

  // Get the value by key from the storage. If key exists, then copy the hash
  // map value into into value buffer. Otherwise, return NOT_FOUND_ERROR.
  //
  // REQUIRES: the buffer pointed to by value must be of value_size()
  //
  // Returns:
  //   OK on success
  //   NOT_FOUND_ERROR if the key doesn't exist
  //   INVALID_ARGUMENT_ERROR if the key is invalid (i.e. contains '\0')
  //   INTERNAL_ERROR on I/O error or any data inconsistency
  //   Any FileBackedVector errors
  libtextclassifier3::Status Get(std::string_view key, void* value) const;

  // Delete the key value pair from the storage. If key doesn't exist, then do
  // nothing and return NOT_FOUND_ERROR.
  //
  // Returns:
  //   OK on success
  //   NOT_FOUND_ERROR if the key doesn't exist
  //   INVALID_ARGUMENT_ERROR if the key is invalid (i.e. contains '\0')
  //   INTERNAL_ERROR on I/O error or any data inconsistency
  //   Any FileBackedVector errors
  libtextclassifier3::Status Delete(std::string_view key);

  Iterator GetIterator() const { return Iterator(this); }

  // Calculates and returns the disk usage (metadata + 3 storages total file
  // size) in bytes.
  //
  // Returns:
  //   Disk usage on success
  //   INTERNAL_ERROR on I/O error
  libtextclassifier3::StatusOr<int64_t> GetDiskUsage() const;

  // Returns the total file size of the all the elements held in the persistent
  // hash map. File size is in bytes. This excludes the size of any internal
  // metadata, i.e. crcs/info of persistent hash map, file backed vector's
  // header.
  //
  // Returns:
  //   File size on success
  //   INTERNAL_ERROR on I/O error
  libtextclassifier3::StatusOr<int64_t> GetElementsSize() const;

  int32_t size() const {
    return entry_storage_->num_elements() - info().num_deleted_entries;
  }

  bool empty() const { return size() == 0; }

  int32_t num_buckets() const { return bucket_storage_->num_elements(); }

 private:
  struct EntryIndexPair {
    int32_t target_entry_index;
    int32_t prev_entry_index;

    explicit EntryIndexPair(int32_t target_entry_index_in,
                            int32_t prev_entry_index_in)
        : target_entry_index(target_entry_index_in),
          prev_entry_index(prev_entry_index_in) {}
  };

  explicit PersistentHashMap(
      const Filesystem& filesystem, std::string&& working_path,
      Options&& options, MemoryMappedFile&& metadata_mmapped_file,
      std::unique_ptr<FileBackedVector<Bucket>> bucket_storage,
      std::unique_ptr<FileBackedVector<Entry>> entry_storage,
      std::unique_ptr<FileBackedVector<char>> kv_storage)
      : PersistentStorage(filesystem, std::move(working_path),
                          kWorkingPathType),
        options_(std::move(options)),
        metadata_mmapped_file_(std::make_unique<MemoryMappedFile>(
            std::move(metadata_mmapped_file))),
        bucket_storage_(std::move(bucket_storage)),
        entry_storage_(std::move(entry_storage)),
        kv_storage_(std::move(kv_storage)),
        is_info_dirty_(false),
        is_storage_dirty_(false) {}

  static libtextclassifier3::StatusOr<std::unique_ptr<PersistentHashMap>>
  InitializeNewFiles(const Filesystem& filesystem, std::string&& working_path,
                     Options&& options);

  static libtextclassifier3::StatusOr<std::unique_ptr<PersistentHashMap>>
  InitializeExistingFiles(const Filesystem& filesystem,
                          std::string&& working_path, Options&& options);

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

  // Computes and returns all storages checksum. Checksums of bucket_storage_,
  // entry_storage_ and kv_storage_ will be combined together by XOR.
  //
  // Returns:
  //   - Crc of all storages on success
  //   - INTERNAL_ERROR if any data inconsistency
  libtextclassifier3::StatusOr<Crc32> ComputeStoragesChecksum(
      bool force) override;

  // Find the index of the target entry (that contains the key) from a bucket
  // (specified by bucket index). Also return the previous entry index, since
  // Delete() needs it to update the linked list and head entry index. The
  // caller should specify the desired bucket index.
  //
  // Returns:
  //   std::pair<int32_t, int32_t>: target entry index and previous entry index
  //                                on success. If not found, then target entry
  //                                index will be Entry::kInvalidIndex
  //   INTERNAL_ERROR if any content inconsistency
  //   Any FileBackedVector errors
  libtextclassifier3::StatusOr<EntryIndexPair> FindEntryIndexByKey(
      int32_t bucket_idx, std::string_view key) const;

  // Copy the hash map value of the entry into value buffer.
  //
  // REQUIRES: entry_idx should be valid.
  // REQUIRES: the buffer pointed to by value must be of value_size()
  //
  // Returns:
  //   OK on success
  //   Any FileBackedVector errors
  libtextclassifier3::Status CopyEntryValue(int32_t entry_idx,
                                            void* value) const;

  // Insert a new key value pair into a bucket (specified by the bucket index).
  // The caller should specify the desired bucket index and make sure that the
  // key is not present in the hash map before calling.
  //
  // Returns:
  //   OK on success
  //   Any FileBackedVector errors
  libtextclassifier3::Status Insert(int32_t bucket_idx, std::string_view key,
                                    const void* value);

  // Rehash function. If force_rehash is true or the hash map loading is greater
  // than max_load_factor, then it will rehash all keys.
  //
  // Returns:
  //   OK on success
  //   INTERNAL_ERROR on I/O error or any data inconsistency
  //   Any FileBackedVector errors
  libtextclassifier3::Status RehashIfNecessary(bool force_rehash);

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

  std::unique_ptr<MemoryMappedFile> metadata_mmapped_file_;

  // Storages
  std::unique_ptr<FileBackedVector<Bucket>> bucket_storage_;
  std::unique_ptr<FileBackedVector<Entry>> entry_storage_;
  std::unique_ptr<FileBackedVector<char>> kv_storage_;

  bool is_info_dirty_;
  bool is_storage_dirty_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_PERSISTENT_HASH_MAP_H_
