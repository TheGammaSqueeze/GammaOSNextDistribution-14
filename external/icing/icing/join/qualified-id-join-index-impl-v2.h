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

#ifndef ICING_JOIN_QUALIFIED_ID_JOIN_INDEX_IMPL_V2_H_
#define ICING_JOIN_QUALIFIED_ID_JOIN_INDEX_IMPL_V2_H_

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/file/filesystem.h"
#include "icing/file/persistent-storage.h"
#include "icing/file/posting_list/flash-index-storage.h"
#include "icing/file/posting_list/posting-list-identifier.h"
#include "icing/join/doc-join-info.h"
#include "icing/join/document-id-to-join-info.h"
#include "icing/join/posting-list-join-data-accessor.h"
#include "icing/join/posting-list-join-data-serializer.h"
#include "icing/join/qualified-id-join-index.h"
#include "icing/schema/joinable-property.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/document-id.h"
#include "icing/store/key-mapper.h"
#include "icing/store/namespace-fingerprint-identifier.h"
#include "icing/store/namespace-id.h"
#include "icing/util/crc32.h"

namespace icing {
namespace lib {

// QualifiedIdJoinIndexImplV2: a class to maintain join data (DocumentId to
// referenced NamespaceFingerprintIdentifier). It stores join data in posting
// lists and bucketizes them by (schema_type_id, joinable_property_id).
class QualifiedIdJoinIndexImplV2 : public QualifiedIdJoinIndex {
 public:
  using JoinDataType = DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>;

  class JoinDataIterator : public JoinDataIteratorBase {
   public:
    explicit JoinDataIterator(
        std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>> pl_accessor)
        : pl_accessor_(std::move(pl_accessor)),
          should_retrieve_next_batch_(true) {}

    ~JoinDataIterator() override = default;

    // Advances to the next data.
    //
    // Returns:
    //   - OK on success
    //   - RESOURCE_EXHAUSTED_ERROR if reaching the end (i.e. no more relevant
    //     data)
    //   - Any other PostingListJoinDataAccessor errors
    libtextclassifier3::Status Advance() override;

    const JoinDataType& GetCurrent() const override { return *curr_; }

   private:
    // Gets next batch of data from the posting list chain, caches in
    // cached_batch_integer_index_data_, and sets curr_ to the begin of the
    // cache.
    libtextclassifier3::Status GetNextDataBatch();

    std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>> pl_accessor_;
    std::vector<JoinDataType> cached_batch_join_data_;
    std::vector<JoinDataType>::const_iterator curr_;
    bool should_retrieve_next_batch_;
  };

  struct Info {
    static constexpr int32_t kMagic = 0x12d1c074;

    int32_t magic;
    int32_t num_data;
    DocumentId last_added_document_id;

    Crc32 ComputeChecksum() const {
      return Crc32(
          std::string_view(reinterpret_cast<const char*>(this), sizeof(Info)));
    }
  } __attribute__((packed));
  static_assert(sizeof(Info) == 12, "");

  // Metadata file layout: <Crcs><Info>
  static constexpr int32_t kCrcsMetadataBufferOffset = 0;
  static constexpr int32_t kInfoMetadataBufferOffset =
      static_cast<int32_t>(sizeof(Crcs));
  static constexpr int32_t kMetadataFileSize = sizeof(Crcs) + sizeof(Info);
  static_assert(kMetadataFileSize == 24, "");

  static constexpr WorkingPathType kWorkingPathType =
      WorkingPathType::kDirectory;

  // Creates a QualifiedIdJoinIndexImplV2 instance to store join data
  // (DocumentId to referenced NamespaceFingerPrintIdentifier) for future
  // joining search. If any of the underlying file is missing, then delete the
  // whole working_path and (re)initialize with new ones. Otherwise initialize
  // and create the instance by existing files.
  //
  // filesystem: Object to make system level calls
  // working_path: Specifies the working path for PersistentStorage.
  //               QualifiedIdJoinIndexImplV2 uses working path as working
  //               directory and all related files will be stored under this
  //               directory. It takes full ownership and of working_path_,
  //               including creation/deletion. It is the caller's
  //               responsibility to specify correct working path and avoid
  //               mixing different persistent storages together under the same
  //               path. Also the caller has the ownership for the parent
  //               directory of working_path_, and it is responsible for parent
  //               directory creation/deletion. See PersistentStorage for more
  //               details about the concept of working_path.
  // pre_mapping_fbv: flag indicating whether memory map max possible file size
  //                  for underlying FileBackedVector before growing the actual
  //                  file size.
  //
  // Returns:
  //   - FAILED_PRECONDITION_ERROR if the file checksum doesn't match the stored
  //                               checksum
  //   - INTERNAL_ERROR on I/O errors
  //   - Any KeyMapper errors
  static libtextclassifier3::StatusOr<
      std::unique_ptr<QualifiedIdJoinIndexImplV2>>
  Create(const Filesystem& filesystem, std::string working_path,
         bool pre_mapping_fbv);

  // Delete copy and move constructor/assignment operator.
  QualifiedIdJoinIndexImplV2(const QualifiedIdJoinIndexImplV2&) = delete;
  QualifiedIdJoinIndexImplV2& operator=(const QualifiedIdJoinIndexImplV2&) =
      delete;

  QualifiedIdJoinIndexImplV2(QualifiedIdJoinIndexImplV2&&) = delete;
  QualifiedIdJoinIndexImplV2& operator=(QualifiedIdJoinIndexImplV2&&) = delete;

  ~QualifiedIdJoinIndexImplV2() override;

  // v1 only API. Returns UNIMPLEMENTED_ERROR.
  libtextclassifier3::Status Put(
      const DocJoinInfo& doc_join_info,
      std::string_view ref_qualified_id_str) override {
    return absl_ports::UnimplementedError("This API is not supported in V2");
  }

  // v1 only API. Returns UNIMPLEMENTED_ERROR.
  libtextclassifier3::StatusOr<std::string_view> Get(
      const DocJoinInfo& doc_join_info) const override {
    return absl_ports::UnimplementedError("This API is not supported in V2");
  }

  // Puts a list of referenced (parent) NamespaceFingerprintIdentifiers into
  // the join index, given the (child) DocumentId, SchemaTypeId and
  // JoinablePropertyId.
  //
  // Returns:
  //   - OK on success
  //   - INVALID_ARGUMENT_ERROR if schema_type_id, joinable_property_id, or
  //     document_id is invalid
  //   - Any KeyMapper/FlashIndexStorage errors
  libtextclassifier3::Status Put(SchemaTypeId schema_type_id,
                                 JoinablePropertyId joinable_property_id,
                                 DocumentId document_id,
                                 std::vector<NamespaceFingerprintIdentifier>&&
                                     ref_namespace_fingerprint_ids) override;

  // Returns a JoinDataIterator for iterating through all join data of the
  // specified (schema_type_id, joinable_property_id).
  //
  // Returns:
  //   - On success: a JoinDataIterator
  //   - INVALID_ARGUMENT_ERROR if schema_type_id or joinable_property_id is
  //     invalid
  //   - Any KeyMapper/FlashIndexStorage errors
  libtextclassifier3::StatusOr<std::unique_ptr<JoinDataIteratorBase>>
  GetIterator(SchemaTypeId schema_type_id,
              JoinablePropertyId joinable_property_id) const override;

  // Reduces internal file sizes by reclaiming space and ids of deleted
  // documents. Qualified id join index will convert all entries to the new
  // document ids and namespace ids.
  //
  // - document_id_old_to_new: a map for converting old document id to new
  //   document id.
  // - namespace_id_old_to_new: a map for converting old namespace id to new
  //   namespace id.
  // - new_last_added_document_id: will be used to update the last added
  //                               document id in the qualified id join index.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error. This could potentially leave the index in
  //     an invalid state and the caller should handle it properly (e.g. discard
  //     and rebuild)
  libtextclassifier3::Status Optimize(
      const std::vector<DocumentId>& document_id_old_to_new,
      const std::vector<NamespaceId>& namespace_id_old_to_new,
      DocumentId new_last_added_document_id) override;

  // Clears all data and set last_added_document_id to kInvalidDocumentId.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::Status Clear() override;

  bool is_v2() const override { return true; }

  int32_t size() const override { return info().num_data; }

  bool empty() const override { return size() == 0; }

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

 private:
  explicit QualifiedIdJoinIndexImplV2(
      const Filesystem& filesystem, std::string&& working_path,
      std::unique_ptr<uint8_t[]> metadata_buffer,
      std::unique_ptr<KeyMapper<PostingListIdentifier>>
          schema_joinable_id_to_posting_list_mapper,
      std::unique_ptr<PostingListJoinDataSerializer<JoinDataType>>
          posting_list_serializer,
      std::unique_ptr<FlashIndexStorage> flash_index_storage,
      bool pre_mapping_fbv)
      : QualifiedIdJoinIndex(filesystem, std::move(working_path)),
        metadata_buffer_(std::move(metadata_buffer)),
        schema_joinable_id_to_posting_list_mapper_(
            std::move(schema_joinable_id_to_posting_list_mapper)),
        posting_list_serializer_(std::move(posting_list_serializer)),
        flash_index_storage_(std::move(flash_index_storage)),
        pre_mapping_fbv_(pre_mapping_fbv),
        is_info_dirty_(false),
        is_storage_dirty_(false) {}

  static libtextclassifier3::StatusOr<
      std::unique_ptr<QualifiedIdJoinIndexImplV2>>
  InitializeNewFiles(const Filesystem& filesystem, std::string&& working_path,
                     bool pre_mapping_fbv);

  static libtextclassifier3::StatusOr<
      std::unique_ptr<QualifiedIdJoinIndexImplV2>>
  InitializeExistingFiles(const Filesystem& filesystem,
                          std::string&& working_path, bool pre_mapping_fbv);

  // Transfers qualified id join index data from the current to new_index and
  // convert to new document id according to document_id_old_to_new and
  // namespace_id_old_to_new. It is a helper function for Optimize.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::Status TransferIndex(
      const std::vector<DocumentId>& document_id_old_to_new,
      const std::vector<NamespaceId>& namespace_id_old_to_new,
      QualifiedIdJoinIndexImplV2* new_index) const;

  // Flushes contents of metadata file.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::Status PersistMetadataToDisk(bool force) override;

  // Flushes contents of all storages to underlying files.
  //
  // Returns:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::Status PersistStoragesToDisk(bool force) override;

  // Computes and returns Info checksum.
  //
  // Returns:
  //   - Crc of the Info on success
  libtextclassifier3::StatusOr<Crc32> ComputeInfoChecksum(bool force) override;

  // Computes and returns all storages checksum.
  //
  // Returns:
  //   - Crc of all storages on success
  //   - INTERNAL_ERROR if any data inconsistency
  libtextclassifier3::StatusOr<Crc32> ComputeStoragesChecksum(
      bool force) override;

  Crcs& crcs() override {
    return *reinterpret_cast<Crcs*>(metadata_buffer_.get() +
                                    kCrcsMetadataBufferOffset);
  }

  const Crcs& crcs() const override {
    return *reinterpret_cast<const Crcs*>(metadata_buffer_.get() +
                                          kCrcsMetadataBufferOffset);
  }

  Info& info() {
    return *reinterpret_cast<Info*>(metadata_buffer_.get() +
                                    kInfoMetadataBufferOffset);
  }

  const Info& info() const {
    return *reinterpret_cast<const Info*>(metadata_buffer_.get() +
                                          kInfoMetadataBufferOffset);
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

  // Metadata buffer
  std::unique_ptr<uint8_t[]> metadata_buffer_;

  // Persistent KeyMapper for mapping (schema_type_id, joinable_property_id) to
  // PostingListIdentifier.
  std::unique_ptr<KeyMapper<PostingListIdentifier>>
      schema_joinable_id_to_posting_list_mapper_;

  // Posting list related members. Use posting list to store join data
  // (document id to referenced NamespaceFingerprintIdentifier).
  std::unique_ptr<PostingListJoinDataSerializer<JoinDataType>>
      posting_list_serializer_;
  std::unique_ptr<FlashIndexStorage> flash_index_storage_;

  // TODO(b/268521214): add delete propagation storage

  // Flag indicating whether memory map max possible file size for underlying
  // FileBackedVector before growing the actual file size.
  bool pre_mapping_fbv_;

  bool is_info_dirty_;
  bool is_storage_dirty_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_JOIN_QUALIFIED_ID_JOIN_INDEX_IMPL_V2_H_
