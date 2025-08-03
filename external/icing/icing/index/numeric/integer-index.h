// Copyright (C) 2023 Google LLC
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

#ifndef ICING_INDEX_NUMERIC_INTEGER_INDEX_H_
#define ICING_INDEX_NUMERIC_INTEGER_INDEX_H_

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_map>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/file/file-backed-proto.h"
#include "icing/file/filesystem.h"
#include "icing/file/memory-mapped-file.h"
#include "icing/index/numeric/integer-index-storage.h"
#include "icing/index/numeric/numeric-index.h"
#include "icing/index/numeric/posting-list-integer-index-serializer.h"
#include "icing/index/numeric/wildcard-property-storage.pb.h"
#include "icing/schema/schema-store.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/util/crc32.h"

namespace icing {
namespace lib {

// IntegerIndex: a wrapper class for managing IntegerIndexStorage (a lower level
// persistent storage class for indexing and searching contents of integer type
// sections in documents) instances for different property paths.
// We separate indexable integer data from different properties into different
// storages, and IntegerIndex manages and handles indexable integer data
// appropriately to their corresponding IntegerIndexStorage instance according
// to the given property path.
class IntegerIndex : public NumericIndex<int64_t> {
 public:
  using PropertyToStorageMapType =
      std::unordered_map<std::string, std::unique_ptr<IntegerIndexStorage>>;

  // Maximum number of individual property storages that this index will allow
  // before falling back to placing hits for any new properties into the
  // 'wildcard' storage.
  static constexpr int kMaxPropertyStorages = 32;

  static constexpr int32_t kDefaultNumDataThresholdForBucketSplit =
      IntegerIndexStorage::kDefaultNumDataThresholdForBucketSplit;

  struct Info {
    static constexpr int32_t kMagic = 0x5d8a1e8a;

    int32_t magic;
    DocumentId last_added_document_id;
    int32_t num_data_threshold_for_bucket_split;

    Crc32 ComputeChecksum() const {
      return Crc32(
          std::string_view(reinterpret_cast<const char*>(this), sizeof(Info)));
    }
  } __attribute__((packed));
  static_assert(sizeof(Info) == 12, "");

  // Metadata file layout: <Crcs><Info>
  static constexpr int32_t kCrcsMetadataFileOffset = 0;
  static constexpr int32_t kInfoMetadataFileOffset =
      static_cast<int32_t>(sizeof(Crcs));
  static constexpr int32_t kMetadataFileSize = sizeof(Crcs) + sizeof(Info);
  static_assert(kMetadataFileSize == 24, "");

  static constexpr WorkingPathType kWorkingPathType =
      WorkingPathType::kDirectory;
  static constexpr std::string_view kFilePrefix = "integer_index";

  // Creates a new IntegerIndex instance to index integers. If any of the
  // underlying file is missing, then delete the whole working_path and
  // (re)initialize with new ones. Otherwise initialize and create the instance
  // by existing files.
  //
  // filesystem: Object to make system level calls
  // working_path: Specifies the working path for PersistentStorage.
  //               IntegerIndex uses working path as working directory and all
  //               related files will be stored under this directory. See
  //               PersistentStorage for more details about the concept of
  //               working_path.
  // num_data_threshold_for_bucket_split: see IntegerIndexStorage::Options for
  //                                      more details.
  // pre_mapping_fbv: flag indicating whether memory map max possible file size
  //                  for underlying FileBackedVector before growing the actual
  //                  file size.
  //
  // Returns:
  //   - FAILED_PRECONDITION_ERROR if the file checksum doesn't match the stored
  //                               checksum.
  //   - INTERNAL_ERROR on I/O errors.
  //   - Any FileBackedVector/MemoryMappedFile errors.
  static libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndex>> Create(
      const Filesystem& filesystem, std::string working_path,
      int32_t num_data_threshold_for_bucket_split, bool pre_mapping_fbv);

  // Deletes IntegerIndex under working_path.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  static libtextclassifier3::Status Discard(const Filesystem& filesystem,
                                            const std::string& working_path) {
    return PersistentStorage::Discard(filesystem, working_path,
                                      kWorkingPathType);
  }

  ~IntegerIndex() override;

  // Returns an Editor instance for adding new records into integer index for a
  // given property, DocumentId and SectionId. See Editor for more details.
  std::unique_ptr<typename NumericIndex<int64_t>::Editor> Edit(
      std::string_view property_path, DocumentId document_id,
      SectionId section_id) override {
    return std::make_unique<Editor>(property_path, document_id, section_id,
                                    *this, num_data_threshold_for_bucket_split_,
                                    pre_mapping_fbv_);
  }

  // Returns a DocHitInfoIterator for iterating through all docs which have the
  // specified (integer) property contents in range [query_key_lower,
  // query_key_upper].
  // When iterating through all relevant doc hits, it:
  // - Merges multiple SectionIds of doc hits with same DocumentId into a single
  //   SectionIdMask and constructs DocHitInfo.
  // - Returns DocHitInfo in descending DocumentId order.
  //
  // Returns:
  //   - On success: a DocHitInfoIterator instance
  //   - NOT_FOUND_ERROR if the given property_path doesn't exist
  //   - Any IntegerIndexStorage errors
  libtextclassifier3::StatusOr<std::unique_ptr<DocHitInfoIterator>> GetIterator(
      std::string_view property_path, int64_t key_lower, int64_t key_upper,
      const DocumentStore& document_store, const SchemaStore& schema_store,
      int64_t current_time_ms) const override;

  // Reduces internal file sizes by reclaiming space and ids of deleted
  // documents. Integer index will convert all data (hits) to the new document
  // ids and regenerate all index files. If all data in a property path are
  // completely deleted, then the underlying storage will be discarded as well.
  //
  // - document_id_old_to_new: a map for converting old document id to new
  //   document id.
  // - new_last_added_document_id: will be used to update the last added
  //                               document id in the integer index.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on IO error
  libtextclassifier3::Status Optimize(
      const std::vector<DocumentId>& document_id_old_to_new,
      DocumentId new_last_added_document_id) override;

  // Clears all integer index data by discarding all existing storages, and set
  // last_added_document_id to kInvalidDocumentId.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::Status Clear() override;

  DocumentId last_added_document_id() const override {
    return info().last_added_document_id;
  }

  void set_last_added_document_id(DocumentId document_id) override {
    SetInfoDirty();

    Info& info_ref = info();
    if (info_ref.last_added_document_id == kInvalidDocumentId ||
        document_id > info_ref.last_added_document_id) {
      info_ref.last_added_document_id = document_id;
    }
  }

  int num_property_indices() const override {
    return property_to_storage_map_.size() +
           ((wildcard_index_storage_ == nullptr) ? 0 : 1);
  }

 private:
  class Editor : public NumericIndex<int64_t>::Editor {
   public:
    explicit Editor(std::string_view property_path, DocumentId document_id,
                    SectionId section_id, IntegerIndex& integer_index,
                    int32_t num_data_threshold_for_bucket_split,
                    bool pre_mapping_fbv)
        : NumericIndex<int64_t>::Editor(property_path, document_id, section_id),
          integer_index_(integer_index),
          num_data_threshold_for_bucket_split_(
              num_data_threshold_for_bucket_split),
          pre_mapping_fbv_(pre_mapping_fbv) {}

    ~Editor() override = default;

    libtextclassifier3::Status BufferKey(int64_t key) override {
      seen_keys_.push_back(key);
      return libtextclassifier3::Status::OK;
    }

    libtextclassifier3::Status IndexAllBufferedKeys() && override;

   private:
    // Vector for caching all seen keys. Since IntegerIndexStorage::AddKeys
    // sorts and dedupes keys, we can just simply use vector here and move it to
    // AddKeys().
    std::vector<int64_t> seen_keys_;

    IntegerIndex& integer_index_;  // Does not own.

    int32_t num_data_threshold_for_bucket_split_;

    // Flag indicating whether memory map max possible file size for underlying
    // FileBackedVector before growing the actual file size.
    bool pre_mapping_fbv_;
  };

  explicit IntegerIndex(
      const Filesystem& filesystem, std::string&& working_path,
      std::unique_ptr<PostingListIntegerIndexSerializer>
          posting_list_serializer,
      std::unique_ptr<MemoryMappedFile> metadata_mmapped_file,
      PropertyToStorageMapType&& property_to_storage_map,
      std::unique_ptr<FileBackedProto<WildcardPropertyStorage>>
          wildcard_property_storage,
      std::unordered_set<std::string> wildcard_properties_set,
      std::unique_ptr<icing::lib::IntegerIndexStorage> wildcard_index_storage,
      int32_t num_data_threshold_for_bucket_split, bool pre_mapping_fbv)
      : NumericIndex<int64_t>(filesystem, std::move(working_path),
                              kWorkingPathType),
        posting_list_serializer_(std::move(posting_list_serializer)),
        metadata_mmapped_file_(std::move(metadata_mmapped_file)),
        property_to_storage_map_(std::move(property_to_storage_map)),
        wildcard_property_storage_(std::move(wildcard_property_storage)),
        wildcard_properties_set_(std::move(wildcard_properties_set)),
        wildcard_index_storage_(std::move(wildcard_index_storage)),
        num_data_threshold_for_bucket_split_(
            num_data_threshold_for_bucket_split),
        pre_mapping_fbv_(pre_mapping_fbv),
        is_info_dirty_(false),
        is_storage_dirty_(false) {}

  static libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndex>>
  InitializeNewFiles(const Filesystem& filesystem, std::string&& working_path,
                     int32_t num_data_threshold_for_bucket_split,
                     bool pre_mapping_fbv);

  static libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndex>>
  InitializeExistingFiles(const Filesystem& filesystem,
                          std::string&& working_path,
                          int32_t num_data_threshold_for_bucket_split,
                          bool pre_mapping_fbv);

  // Adds the property path to the list of properties using wildcard storage.
  // This will both update the in-memory list (wildcard_properties_set_) and
  // the persistent list (wilcard_property_storage_).
  //
  // RETURNS:
  //   - OK on success
  //   - INTERNAL_ERROR if unable to successfully persist updated properties
  //     list in wildcard_property_storage_.
  libtextclassifier3::Status AddPropertyToWildcardStorage(
      const std::string& property_path);

  // Transfers integer index data from the current integer index to
  // new_integer_index.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error. This could potentially leave the storages
  //     in an invalid state and the caller should handle it properly (e.g.
  //     discard and rebuild)
  libtextclassifier3::Status TransferIndex(
      const std::vector<DocumentId>& document_id_old_to_new,
      IntegerIndex* new_integer_index) const;

  // Transfers integer index data from old_storage to new_integer_index.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error. This could potentially leave the storages
  //     in an invalid state and the caller should handle it properly (e.g.
  //     discard and rebuild)
  libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndexStorage>>
  TransferIntegerIndexStorage(
      const std::vector<DocumentId>& document_id_old_to_new,
      const IntegerIndexStorage* old_storage, const std::string& property_path,
      IntegerIndex* new_integer_index) const;

  // Transfers the persistent and in-memory list of properties using the
  // wildcard storage from old_storage to new_integer_index.
  //
  // RETURNS:
  //   - OK on success
  //   - INTERNAL_ERROR if unable to successfully persist updated properties
  //     list in new_integer_index.
  libtextclassifier3::Status TransferWildcardStorage(
      IntegerIndex* new_integer_index) const;

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

  // Computes and returns all storages checksum. Checksums of (storage_crc,
  // property_path) for all existing property paths will be combined together by
  // XOR.
  //
  // Returns:
  //   - Crc of all storages on success
  //   - INTERNAL_ERROR if any data inconsistency
  libtextclassifier3::StatusOr<Crc32> ComputeStoragesChecksum(
      bool force) override;

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

  std::unique_ptr<PostingListIntegerIndexSerializer> posting_list_serializer_;

  std::unique_ptr<MemoryMappedFile> metadata_mmapped_file_;

  // Property path to integer index storage map.
  PropertyToStorageMapType property_to_storage_map_;

  // Persistent list of properties that have added content to
  // wildcard_index_storage_.
  std::unique_ptr<FileBackedProto<WildcardPropertyStorage>>
      wildcard_property_storage_;

  // In-memory list of properties that have added content to
  // wildcard_index_storage_.
  std::unordered_set<std::string> wildcard_properties_set_;

  // The index storage that is used once we have already created
  // kMaxPropertyStorages in property_to_storage_map.
  std::unique_ptr<icing::lib::IntegerIndexStorage> wildcard_index_storage_;

  int32_t num_data_threshold_for_bucket_split_;

  // Flag indicating whether memory map max possible file size for underlying
  // FileBackedVector before growing the actual file size.
  bool pre_mapping_fbv_;

  bool is_info_dirty_;
  bool is_storage_dirty_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_NUMERIC_INTEGER_INDEX_H_
