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

#include "icing/file/persistent-hash-map.h"

#include <cstdint>
#include <cstring>
#include <memory>
#include <string>
#include <string_view>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/file-backed-vector.h"
#include "icing/file/memory-mapped-file.h"
#include "icing/file/persistent-storage.h"
#include "icing/util/crc32.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

// Helper function to check if there is no termination character '\0' in the
// key.
libtextclassifier3::Status ValidateKey(std::string_view key) {
  if (key.find('\0') != std::string_view::npos) {  // NOLINT
    return absl_ports::InvalidArgumentError(
        "Key cannot contain termination character '\\0'");
  }
  return libtextclassifier3::Status::OK;
}

// Helper function to convert the key to bucket index by hash.
//
// Returns:
//   int32_t: A valid bucket index with range [0, num_buckets - 1].
//   INTERNAL_ERROR if num_buckets == 0
libtextclassifier3::StatusOr<int32_t> HashKeyToBucketIndex(
    std::string_view key, int32_t num_buckets) {
  if (num_buckets == 0) {
    return absl_ports::InternalError("Should not have empty bucket");
  }
  return static_cast<int32_t>(std::hash<std::string_view>()(key) % num_buckets);
}

// The following 4 methods are helper functions to get the correct path of
// metadata/bucket/entry/key-value storages, according to the given working
// directory path.
std::string GetMetadataFilePath(std::string_view working_path) {
  return absl_ports::StrCat(working_path, "/", PersistentHashMap::kFilePrefix,
                            ".m");
}

std::string GetBucketStorageFilePath(std::string_view working_path) {
  return absl_ports::StrCat(working_path, "/", PersistentHashMap::kFilePrefix,
                            ".b");
}

std::string GetEntryStorageFilePath(std::string_view working_path) {
  return absl_ports::StrCat(working_path, "/", PersistentHashMap::kFilePrefix,
                            ".e");
}

std::string GetKeyValueStorageFilePath(std::string_view working_path) {
  return absl_ports::StrCat(working_path, "/", PersistentHashMap::kFilePrefix,
                            ".k");
}

// Calculates how many buckets we need given num_entries and
// max_load_factor_percent. Round it up to 2's power.
//
// REQUIRES: 0 < num_entries <= Entry::kMaxNumEntries &&
//           max_load_factor_percent > 0
int32_t CalculateNumBucketsRequired(int32_t num_entries,
                                    int32_t max_load_factor_percent) {
  // Calculate ceil(num_entries * 100 / max_load_factor_percent)
  int32_t num_entries_100 = num_entries * 100;
  int32_t num_buckets_required =
      num_entries_100 / max_load_factor_percent +
      (num_entries_100 % max_load_factor_percent == 0 ? 0 : 1);
  if ((num_buckets_required & (num_buckets_required - 1)) != 0) {
    // not 2's power
    return 1 << (32 - __builtin_clz(num_buckets_required));
  }
  return num_buckets_required;
}

}  // namespace

bool PersistentHashMap::Options::IsValid() const {
  if (!(value_type_size > 0 && value_type_size <= kMaxValueTypeSize &&
        max_num_entries > 0 && max_num_entries <= Entry::kMaxNumEntries &&
        max_load_factor_percent > 0 && average_kv_byte_size > 0 &&
        init_num_buckets > 0 && init_num_buckets <= Bucket::kMaxNumBuckets)) {
    return false;
  }

  // We've ensured (static_assert) that storing kMaxNumBuckets buckets won't
  // exceed FileBackedVector::kMaxFileSize, so only need to verify # of buckets
  // required won't exceed kMaxNumBuckets.
  if (CalculateNumBucketsRequired(max_num_entries, max_load_factor_percent) >
      Bucket::kMaxNumBuckets) {
    return false;
  }

  // Verify # of key value pairs can fit into kv_storage.
  if (average_kv_byte_size > kMaxKVTotalByteSize / max_num_entries) {
    return false;
  }

  // Verify init_num_buckets is 2's power. Requiring init_num_buckets to be 2^n
  // guarantees that num_buckets will eventually grow to be exactly
  // max_num_buckets since CalculateNumBucketsRequired rounds it up to 2^n.
  if ((init_num_buckets & (init_num_buckets - 1)) != 0) {
    return false;
  }

  return true;
}

/* static */ libtextclassifier3::StatusOr<std::unique_ptr<PersistentHashMap>>
PersistentHashMap::Create(const Filesystem& filesystem,
                          std::string working_path, Options options) {
  if (!options.IsValid()) {
    return absl_ports::InvalidArgumentError(
        "Invalid PersistentHashMap options");
  }

  if (!filesystem.FileExists(GetMetadataFilePath(working_path).c_str()) ||
      !filesystem.FileExists(GetBucketStorageFilePath(working_path).c_str()) ||
      !filesystem.FileExists(GetEntryStorageFilePath(working_path).c_str()) ||
      !filesystem.FileExists(
          GetKeyValueStorageFilePath(working_path).c_str())) {
    // Discard working_path if any of them is missing, and reinitialize.
    if (filesystem.DirectoryExists(working_path.c_str())) {
      ICING_RETURN_IF_ERROR(Discard(filesystem, working_path));
    }
    return InitializeNewFiles(filesystem, std::move(working_path),
                              std::move(options));
  }
  return InitializeExistingFiles(filesystem, std::move(working_path),
                                 std::move(options));
}

PersistentHashMap::~PersistentHashMap() {
  if (!PersistToDisk().ok()) {
    ICING_LOG(WARNING)
        << "Failed to persist hash map to disk while destructing "
        << working_path_;
  }
}

libtextclassifier3::Status PersistentHashMap::Put(std::string_view key,
                                                  const void* value) {
  SetDirty();

  ICING_RETURN_IF_ERROR(ValidateKey(key));
  ICING_ASSIGN_OR_RETURN(
      int32_t bucket_idx,
      HashKeyToBucketIndex(key, bucket_storage_->num_elements()));

  ICING_ASSIGN_OR_RETURN(EntryIndexPair idx_pair,
                         FindEntryIndexByKey(bucket_idx, key));
  if (idx_pair.target_entry_index == Entry::kInvalidIndex) {
    // If not found, then insert new key value pair.
    return Insert(bucket_idx, key, value);
  }

  // Otherwise, overwrite the value.
  ICING_ASSIGN_OR_RETURN(const Entry* entry,
                         entry_storage_->Get(idx_pair.target_entry_index));

  int32_t kv_len = key.length() + 1 + info().value_type_size;
  int32_t value_offset = key.length() + 1;
  ICING_ASSIGN_OR_RETURN(
      typename FileBackedVector<char>::MutableArrayView mutable_kv_arr,
      kv_storage_->GetMutable(entry->key_value_index(), kv_len));
  // It is the same key and value_size is fixed, so we can directly overwrite
  // serialized value.
  mutable_kv_arr.SetArray(value_offset, reinterpret_cast<const char*>(value),
                          info().value_type_size);

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status PersistentHashMap::GetOrPut(std::string_view key,
                                                       void* next_value) {
  ICING_RETURN_IF_ERROR(ValidateKey(key));
  ICING_ASSIGN_OR_RETURN(
      int32_t bucket_idx,
      HashKeyToBucketIndex(key, bucket_storage_->num_elements()));

  ICING_ASSIGN_OR_RETURN(EntryIndexPair idx_pair,
                         FindEntryIndexByKey(bucket_idx, key));
  if (idx_pair.target_entry_index == Entry::kInvalidIndex) {
    // If not found, then insert new key value pair.
    SetDirty();
    return Insert(bucket_idx, key, next_value);
  }

  // Otherwise, copy the hash map value into next_value.
  return CopyEntryValue(idx_pair.target_entry_index, next_value);
}

libtextclassifier3::Status PersistentHashMap::Get(std::string_view key,
                                                  void* value) const {
  ICING_RETURN_IF_ERROR(ValidateKey(key));
  ICING_ASSIGN_OR_RETURN(
      int32_t bucket_idx,
      HashKeyToBucketIndex(key, bucket_storage_->num_elements()));

  ICING_ASSIGN_OR_RETURN(EntryIndexPair idx_pair,
                         FindEntryIndexByKey(bucket_idx, key));
  if (idx_pair.target_entry_index == Entry::kInvalidIndex) {
    return absl_ports::NotFoundError(absl_ports::StrCat(
        "Key not found in PersistentHashMap ", working_path_));
  }

  return CopyEntryValue(idx_pair.target_entry_index, value);
}

libtextclassifier3::Status PersistentHashMap::Delete(std::string_view key) {
  SetDirty();

  ICING_RETURN_IF_ERROR(ValidateKey(key));
  ICING_ASSIGN_OR_RETURN(
      int32_t bucket_idx,
      HashKeyToBucketIndex(key, bucket_storage_->num_elements()));

  ICING_ASSIGN_OR_RETURN(EntryIndexPair idx_pair,
                         FindEntryIndexByKey(bucket_idx, key));
  if (idx_pair.target_entry_index == Entry::kInvalidIndex) {
    return absl_ports::NotFoundError(absl_ports::StrCat(
        "Key not found in PersistentHashMap ", working_path_));
  }

  ICING_ASSIGN_OR_RETURN(
      typename FileBackedVector<Entry>::MutableView mutable_target_entry,
      entry_storage_->GetMutable(idx_pair.target_entry_index));
  if (idx_pair.prev_entry_index == Entry::kInvalidIndex) {
    // If prev_entry_idx is Entry::kInvalidIndex, then target_entry must be the
    // head element of the entry linked list, and we have to update
    // bucket->head_entry_index_.
    //
    // Before: target_entry (head) -> next_entry -> ...
    // After: next_entry (head) -> ...
    ICING_ASSIGN_OR_RETURN(
        typename FileBackedVector<Bucket>::MutableView mutable_bucket,
        bucket_storage_->GetMutable(bucket_idx));
    if (mutable_bucket.Get().head_entry_index() !=
        idx_pair.target_entry_index) {
      return absl_ports::InternalError(
          "Bucket head entry index is inconsistent with the actual entry linked"
          "list head. This shouldn't happen");
    }
    mutable_bucket.Get().set_head_entry_index(
        mutable_target_entry.Get().next_entry_index());
  } else {
    // Otherwise, connect prev_entry and next_entry, to remove target_entry from
    // the entry linked list.
    //
    // Before: ... -> prev_entry -> target_entry -> next_entry -> ...
    // After: ... -> prev_entry -> next_entry -> ...
    ICING_ASSIGN_OR_RETURN(
        typename FileBackedVector<Entry>::MutableView mutable_prev_entry,
        entry_storage_->GetMutable(idx_pair.prev_entry_index));
    mutable_prev_entry.Get().set_next_entry_index(
        mutable_target_entry.Get().next_entry_index());
  }

  // Zero out the key value bytes. It is necessary for iterator to iterate
  // through kv_storage and handle deleted keys properly.
  int32_t kv_len = key.length() + 1 + info().value_type_size;
  ICING_RETURN_IF_ERROR(kv_storage_->Set(
      mutable_target_entry.Get().key_value_index(), kv_len, '\0'));

  // Invalidate target_entry
  mutable_target_entry.Get().set_key_value_index(kInvalidKVIndex);
  mutable_target_entry.Get().set_next_entry_index(Entry::kInvalidIndex);

  ++(info().num_deleted_entries);

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<int64_t> PersistentHashMap::GetDiskUsage() const {
  ICING_ASSIGN_OR_RETURN(int64_t bucket_storage_disk_usage,
                         bucket_storage_->GetDiskUsage());
  ICING_ASSIGN_OR_RETURN(int64_t entry_storage_disk_usage,
                         entry_storage_->GetDiskUsage());
  ICING_ASSIGN_OR_RETURN(int64_t kv_storage_disk_usage,
                         kv_storage_->GetDiskUsage());

  int64_t total = bucket_storage_disk_usage + entry_storage_disk_usage +
                  kv_storage_disk_usage;
  Filesystem::IncrementByOrSetInvalid(
      filesystem_.GetDiskUsage(GetMetadataFilePath(working_path_).c_str()),
      &total);

  if (total < 0 || total == Filesystem::kBadFileSize) {
    return absl_ports::InternalError(
        "Failed to get disk usage of PersistentHashMap");
  }
  return total;
}

libtextclassifier3::StatusOr<int64_t> PersistentHashMap::GetElementsSize()
    const {
  ICING_ASSIGN_OR_RETURN(int64_t bucket_storage_elements_size,
                         bucket_storage_->GetElementsFileSize());
  ICING_ASSIGN_OR_RETURN(int64_t entry_storage_elements_size,
                         entry_storage_->GetElementsFileSize());
  ICING_ASSIGN_OR_RETURN(int64_t kv_storage_elements_size,
                         kv_storage_->GetElementsFileSize());
  return bucket_storage_elements_size + entry_storage_elements_size +
         kv_storage_elements_size;
}

/* static */ libtextclassifier3::StatusOr<std::unique_ptr<PersistentHashMap>>
PersistentHashMap::InitializeNewFiles(const Filesystem& filesystem,
                                      std::string&& working_path,
                                      Options&& options) {
  // PersistentHashMap uses working_path as working directory path.
  // Create working directory.
  if (!filesystem.CreateDirectory(working_path.c_str())) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to create directory: ", working_path));
  }

  int32_t max_num_buckets_required =
      std::max(options.init_num_buckets,
               CalculateNumBucketsRequired(options.max_num_entries,
                                           options.max_load_factor_percent));

  // Initialize bucket_storage
  int32_t pre_mapping_mmap_size = sizeof(Bucket) * max_num_buckets_required;
  int32_t max_file_size =
      pre_mapping_mmap_size + FileBackedVector<Bucket>::Header::kHeaderSize;
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<FileBackedVector<Bucket>> bucket_storage,
      FileBackedVector<Bucket>::Create(
          filesystem, GetBucketStorageFilePath(working_path),
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, max_file_size,
          options.pre_mapping_fbv ? pre_mapping_mmap_size : 0));

  // Initialize entry_storage
  pre_mapping_mmap_size = sizeof(Entry) * options.max_num_entries;
  max_file_size =
      pre_mapping_mmap_size + FileBackedVector<Entry>::Header::kHeaderSize;
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<FileBackedVector<Entry>> entry_storage,
      FileBackedVector<Entry>::Create(
          filesystem, GetEntryStorageFilePath(working_path),
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, max_file_size,
          options.pre_mapping_fbv ? pre_mapping_mmap_size : 0));

  // Initialize kv_storage
  pre_mapping_mmap_size =
      options.average_kv_byte_size * options.max_num_entries;
  max_file_size =
      pre_mapping_mmap_size + FileBackedVector<char>::Header::kHeaderSize;
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<FileBackedVector<char>> kv_storage,
      FileBackedVector<char>::Create(
          filesystem, GetKeyValueStorageFilePath(working_path),
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, max_file_size,
          options.pre_mapping_fbv ? pre_mapping_mmap_size : 0));

  // Initialize buckets.
  ICING_RETURN_IF_ERROR(bucket_storage->Set(
      /*idx=*/0, /*len=*/options.init_num_buckets, Bucket()));
  ICING_RETURN_IF_ERROR(bucket_storage->PersistToDisk());

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
  auto new_persistent_hash_map =
      std::unique_ptr<PersistentHashMap>(new PersistentHashMap(
          filesystem, std::move(working_path), std::move(options),
          std::move(metadata_mmapped_file), std::move(bucket_storage),
          std::move(entry_storage), std::move(kv_storage)));
  // Initialize info content by writing mapped memory directly.
  Info& info_ref = new_persistent_hash_map->info();
  info_ref.magic = Info::kMagic;
  info_ref.value_type_size = new_persistent_hash_map->options_.value_type_size;
  info_ref.max_load_factor_percent =
      new_persistent_hash_map->options_.max_load_factor_percent;
  info_ref.num_deleted_entries = 0;
  info_ref.num_deleted_key_value_bytes = 0;
  // Initialize new PersistentStorage. The initial checksums will be computed
  // and set via InitializeNewStorage.
  ICING_RETURN_IF_ERROR(new_persistent_hash_map->InitializeNewStorage());

  return new_persistent_hash_map;
}

/* static */ libtextclassifier3::StatusOr<std::unique_ptr<PersistentHashMap>>
PersistentHashMap::InitializeExistingFiles(const Filesystem& filesystem,
                                           std::string&& working_path,
                                           Options&& options) {
  // Initialize metadata file
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

  int32_t max_num_buckets_required = CalculateNumBucketsRequired(
      options.max_num_entries, options.max_load_factor_percent);

  // Initialize bucket_storage
  int32_t pre_mapping_mmap_size = sizeof(Bucket) * max_num_buckets_required;
  int32_t max_file_size =
      pre_mapping_mmap_size + FileBackedVector<Bucket>::Header::kHeaderSize;
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<FileBackedVector<Bucket>> bucket_storage,
      FileBackedVector<Bucket>::Create(
          filesystem, GetBucketStorageFilePath(working_path),
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, max_file_size,
          options.pre_mapping_fbv ? pre_mapping_mmap_size : 0));

  // Initialize entry_storage
  pre_mapping_mmap_size = sizeof(Entry) * options.max_num_entries;
  max_file_size =
      pre_mapping_mmap_size + FileBackedVector<Entry>::Header::kHeaderSize;
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<FileBackedVector<Entry>> entry_storage,
      FileBackedVector<Entry>::Create(
          filesystem, GetEntryStorageFilePath(working_path),
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, max_file_size,
          options.pre_mapping_fbv ? pre_mapping_mmap_size : 0));

  // Initialize kv_storage
  pre_mapping_mmap_size =
      options.average_kv_byte_size * options.max_num_entries;
  max_file_size =
      pre_mapping_mmap_size + FileBackedVector<char>::Header::kHeaderSize;
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<FileBackedVector<char>> kv_storage,
      FileBackedVector<char>::Create(
          filesystem, GetKeyValueStorageFilePath(working_path),
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, max_file_size,
          options.pre_mapping_fbv ? pre_mapping_mmap_size : 0));

  // Create instance.
  auto persistent_hash_map =
      std::unique_ptr<PersistentHashMap>(new PersistentHashMap(
          filesystem, std::move(working_path), std::move(options),
          std::move(metadata_mmapped_file), std::move(bucket_storage),
          std::move(entry_storage), std::move(kv_storage)));
  // Initialize existing PersistentStorage. Checksums will be validated.
  ICING_RETURN_IF_ERROR(persistent_hash_map->InitializeExistingStorage());

  // Validate other values of info and options.
  // Current # of entries should not exceed options_.max_num_entries
  // We compute max_file_size of 3 storages by options_.max_num_entries. Since
  // we won't recycle space of deleted entries (and key-value bytes), they're
  // still occupying space in storages. Even if # of "active" entries doesn't
  // exceed options_.max_num_entries, the new kvp to be inserted still
  // potentially exceeds max_file_size.
  // Therefore, we should use entry_storage_->num_elements() instead of # of
  // "active" entries
  // (i.e. entry_storage_->num_elements() - info_ptr->num_deleted_entries) to
  // check. This feature avoids storages being grown extremely large when there
  // are many Delete() and Put() operations.
  if (persistent_hash_map->entry_storage_->num_elements() >
      persistent_hash_map->options_.max_num_entries) {
    return absl_ports::FailedPreconditionError(
        "Current # of entries exceeds max num entries");
  }

  // Magic should be the same.
  if (persistent_hash_map->info().magic != Info::kMagic) {
    return absl_ports::FailedPreconditionError(
        "PersistentHashMap header magic mismatch");
  }

  // Value type size should be consistent.
  if (persistent_hash_map->options_.value_type_size !=
      persistent_hash_map->info().value_type_size) {
    return absl_ports::FailedPreconditionError("Incorrect value type size");
  }

  // Allow max_load_factor_percent_ change.
  if (persistent_hash_map->options_.max_load_factor_percent !=
      persistent_hash_map->info().max_load_factor_percent) {
    ICING_VLOG(2) << "Changing max_load_factor_percent from "
                  << persistent_hash_map->info().max_load_factor_percent
                  << " to "
                  << persistent_hash_map->options_.max_load_factor_percent;

    persistent_hash_map->SetInfoDirty();
    persistent_hash_map->info().max_load_factor_percent =
        persistent_hash_map->options_.max_load_factor_percent;
    ICING_RETURN_IF_ERROR(
        persistent_hash_map->RehashIfNecessary(/*force_rehash=*/false));

    ICING_RETURN_IF_ERROR(persistent_hash_map->PersistToDisk());
  }

  return persistent_hash_map;
}

libtextclassifier3::Status PersistentHashMap::PersistStoragesToDisk(
    bool force) {
  if (!force && !is_storage_dirty()) {
    return libtextclassifier3::Status::OK;
  }

  ICING_RETURN_IF_ERROR(bucket_storage_->PersistToDisk());
  ICING_RETURN_IF_ERROR(entry_storage_->PersistToDisk());
  ICING_RETURN_IF_ERROR(kv_storage_->PersistToDisk());
  is_storage_dirty_ = false;
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status PersistentHashMap::PersistMetadataToDisk(
    bool force) {
  // We can skip persisting metadata to disk only if both info and storage are
  // clean.
  if (!force && !is_info_dirty() && !is_storage_dirty()) {
    return libtextclassifier3::Status::OK;
  }

  // Changes should have been applied to the underlying file when using
  // MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, but call msync() as an
  // extra safety step to ensure they are written out.
  ICING_RETURN_IF_ERROR(metadata_mmapped_file_->PersistToDisk());
  is_info_dirty_ = false;
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<Crc32> PersistentHashMap::ComputeInfoChecksum(
    bool force) {
  if (!force && !is_info_dirty()) {
    return Crc32(crcs().component_crcs.info_crc);
  }

  return info().ComputeChecksum();
}

libtextclassifier3::StatusOr<Crc32> PersistentHashMap::ComputeStoragesChecksum(
    bool force) {
  if (!force && !is_storage_dirty()) {
    return Crc32(crcs().component_crcs.storages_crc);
  }

  // Compute crcs
  ICING_ASSIGN_OR_RETURN(Crc32 bucket_storage_crc,
                         bucket_storage_->ComputeChecksum());
  ICING_ASSIGN_OR_RETURN(Crc32 entry_storage_crc,
                         entry_storage_->ComputeChecksum());
  ICING_ASSIGN_OR_RETURN(Crc32 kv_storage_crc, kv_storage_->ComputeChecksum());

  return Crc32(bucket_storage_crc.Get() ^ entry_storage_crc.Get() ^
               kv_storage_crc.Get());
}

libtextclassifier3::StatusOr<PersistentHashMap::EntryIndexPair>
PersistentHashMap::FindEntryIndexByKey(int32_t bucket_idx,
                                       std::string_view key) const {
  // Iterate all entries in the bucket, compare with key, and return the entry
  // index if exists.
  ICING_ASSIGN_OR_RETURN(const Bucket* bucket,
                         bucket_storage_->Get(bucket_idx));

  int32_t prev_entry_idx = Entry::kInvalidIndex;
  int32_t curr_entry_idx = bucket->head_entry_index();
  while (curr_entry_idx != Entry::kInvalidIndex) {
    ICING_ASSIGN_OR_RETURN(const Entry* entry,
                           entry_storage_->Get(curr_entry_idx));
    if (entry->key_value_index() == kInvalidKVIndex) {
      ICING_LOG(ERROR) << "Got an invalid key value index in the persistent "
                          "hash map bucket. This shouldn't happen";
      return absl_ports::InternalError("Unexpected invalid key value index");
    }
    ICING_ASSIGN_OR_RETURN(const char* kv_arr,
                           kv_storage_->Get(entry->key_value_index()));
    if (key.compare(kv_arr) == 0) {
      return EntryIndexPair(curr_entry_idx, prev_entry_idx);
    }

    prev_entry_idx = curr_entry_idx;
    curr_entry_idx = entry->next_entry_index();
  }

  return EntryIndexPair(curr_entry_idx, prev_entry_idx);
}

libtextclassifier3::Status PersistentHashMap::CopyEntryValue(
    int32_t entry_idx, void* value) const {
  ICING_ASSIGN_OR_RETURN(const Entry* entry, entry_storage_->Get(entry_idx));

  ICING_ASSIGN_OR_RETURN(const char* kv_arr,
                         kv_storage_->Get(entry->key_value_index()));
  int32_t value_offset = strlen(kv_arr) + 1;
  memcpy(value, kv_arr + value_offset, info().value_type_size);

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status PersistentHashMap::Insert(int32_t bucket_idx,
                                                     std::string_view key,
                                                     const void* value) {
  SetDirty();

  // If entry_storage_->num_elements() + 1 exceeds options_.max_num_entries,
  // then return error.
  // We compute max_file_size of 3 storages by options_.max_num_entries. Since
  // we won't recycle space of deleted entries (and key-value bytes), they're
  // still occupying space in storages. Even if # of "active" entries (i.e.
  // size()) doesn't exceed options_.max_num_entries, the new kvp to be inserted
  // still potentially exceeds max_file_size.
  // Therefore, we should use entry_storage_->num_elements() instead of size()
  // to check. This feature avoids storages being grown extremely large when
  // there are many Delete() and Put() operations.
  if (entry_storage_->num_elements() > options_.max_num_entries - 1) {
    return absl_ports::ResourceExhaustedError("Cannot insert new entry");
  }

  ICING_ASSIGN_OR_RETURN(
      typename FileBackedVector<Bucket>::MutableView mutable_bucket,
      bucket_storage_->GetMutable(bucket_idx));

  // Append new key value.
  int32_t new_kv_idx = kv_storage_->num_elements();
  int32_t kv_len = key.size() + 1 + info().value_type_size;
  int32_t value_offset = key.size() + 1;
  ICING_ASSIGN_OR_RETURN(
      typename FileBackedVector<char>::MutableArrayView mutable_new_kv_arr,
      kv_storage_->Allocate(kv_len));
  mutable_new_kv_arr.SetArray(/*idx=*/0, key.data(), key.size());
  mutable_new_kv_arr.SetArray(/*idx=*/key.size(), "\0", 1);
  mutable_new_kv_arr.SetArray(/*idx=*/value_offset,
                              reinterpret_cast<const char*>(value),
                              info().value_type_size);

  // Append new entry.
  int32_t new_entry_idx = entry_storage_->num_elements();
  ICING_RETURN_IF_ERROR(entry_storage_->Append(
      Entry(new_kv_idx, mutable_bucket.Get().head_entry_index())));
  mutable_bucket.Get().set_head_entry_index(new_entry_idx);

  return RehashIfNecessary(/*force_rehash=*/false);
}

libtextclassifier3::Status PersistentHashMap::RehashIfNecessary(
    bool force_rehash) {
  int32_t new_num_bucket = bucket_storage_->num_elements();
  while (new_num_bucket <= Bucket::kMaxNumBuckets / 2 &&
         size() > static_cast<int64_t>(new_num_bucket) *
                      info().max_load_factor_percent / 100) {
    new_num_bucket *= 2;
  }

  if (!force_rehash && new_num_bucket == bucket_storage_->num_elements()) {
    return libtextclassifier3::Status::OK;
  }

  SetDirty();

  // Resize and reset buckets.
  ICING_RETURN_IF_ERROR(
      bucket_storage_->Set(0, new_num_bucket, Bucket(Entry::kInvalidIndex)));

  // Iterate all key value pairs in kv_storage, rehash and insert.
  Iterator iter = GetIterator();
  int32_t entry_idx = 0;
  while (iter.Advance()) {
    ICING_ASSIGN_OR_RETURN(int32_t bucket_idx,
                           HashKeyToBucketIndex(iter.GetKey(), new_num_bucket));
    ICING_ASSIGN_OR_RETURN(FileBackedVector<Bucket>::MutableView mutable_bucket,
                           bucket_storage_->GetMutable(bucket_idx));

    // Update entry and bucket.
    ICING_RETURN_IF_ERROR(entry_storage_->Set(
        entry_idx,
        Entry(iter.GetIndex(), mutable_bucket.Get().head_entry_index())));
    mutable_bucket.Get().set_head_entry_index(entry_idx);

    ++entry_idx;
  }

  // Since there will be some deleted entries, after rehashing entry_storage_
  // # of vector elements may be greater than the actual # of entries.
  // Therefore, we have to truncate entry_storage_ to the correct size.
  if (entry_idx < entry_storage_->num_elements()) {
    ICING_RETURN_IF_ERROR(entry_storage_->TruncateTo(entry_idx));
  }

  info().num_deleted_entries = 0;

  return libtextclassifier3::Status::OK;
}

bool PersistentHashMap::Iterator::Advance() {
  // Jump over the current key value pair before advancing to the next valid
  // key value pair. In the first round (after construction), curr_key_len_
  // is 0, so don't jump over anything.
  if (curr_key_len_ != 0) {
    curr_kv_idx_ += curr_key_len_ + 1 + map_->info().value_type_size;
    curr_key_len_ = 0;
  }

  // By skipping null chars, we will be automatically handling deleted entries
  // (which are zeroed out during deletion).
  for (const char* curr_kv_ptr = map_->kv_storage_->array() + curr_kv_idx_;
       curr_kv_idx_ < map_->kv_storage_->num_elements();
       ++curr_kv_ptr, ++curr_kv_idx_) {
    if (*curr_kv_ptr != '\0') {
      curr_key_len_ = strlen(curr_kv_ptr);
      return true;
    }
  }
  return false;
}

}  // namespace lib
}  // namespace icing
