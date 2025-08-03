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

#include "icing/join/qualified-id-join-index-impl-v2.h"

#include <algorithm>
#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/destructible-directory.h"
#include "icing/file/filesystem.h"
#include "icing/file/posting_list/flash-index-storage.h"
#include "icing/file/posting_list/posting-list-accessor.h"
#include "icing/file/posting_list/posting-list-identifier.h"
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
#include "icing/store/persistent-hash-map-key-mapper.h"
#include "icing/util/crc32.h"
#include "icing/util/encode-util.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

// Set 1M for max # of qualified id entries and 10 bytes for key-value bytes.
// This will take at most 23 MiB disk space and mmap for persistent hash map.
static constexpr int32_t kSchemaJoinableIdToPostingListMapperMaxNumEntries =
    1 << 20;
static constexpr int32_t kSchemaJoinableIdToPostingListMapperAverageKVByteSize =
    10;

inline DocumentId GetNewDocumentId(
    const std::vector<DocumentId>& document_id_old_to_new,
    DocumentId old_document_id) {
  if (old_document_id >= document_id_old_to_new.size()) {
    return kInvalidDocumentId;
  }
  return document_id_old_to_new[old_document_id];
}

inline NamespaceId GetNewNamespaceId(
    const std::vector<NamespaceId>& namespace_id_old_to_new,
    NamespaceId namespace_id) {
  if (namespace_id >= namespace_id_old_to_new.size()) {
    return kInvalidNamespaceId;
  }
  return namespace_id_old_to_new[namespace_id];
}

libtextclassifier3::StatusOr<PostingListIdentifier> GetPostingListIdentifier(
    const KeyMapper<PostingListIdentifier>&
        schema_joinable_id_to_posting_list_mapper,
    const std::string& encoded_schema_type_joinable_property_id_str) {
  auto posting_list_identifier_or =
      schema_joinable_id_to_posting_list_mapper.Get(
          encoded_schema_type_joinable_property_id_str);
  if (!posting_list_identifier_or.ok()) {
    if (absl_ports::IsNotFound(posting_list_identifier_or.status())) {
      // Not found. Return invalid posting list id.
      return PostingListIdentifier::kInvalid;
    }
    // Real error.
    return posting_list_identifier_or;
  }
  return std::move(posting_list_identifier_or).ValueOrDie();
}

libtextclassifier3::StatusOr<std::string> EncodeSchemaTypeJoinablePropertyId(
    SchemaTypeId schema_type_id, JoinablePropertyId joinable_property_id) {
  if (schema_type_id < 0) {
    return absl_ports::InvalidArgumentError("Invalid schema type id");
  }

  if (!IsJoinablePropertyIdValid(joinable_property_id)) {
    return absl_ports::InvalidArgumentError("Invalid joinable property id");
  }

  static constexpr int kEncodedSchemaTypeIdLength = 3;

  // encoded_schema_type_id_str should be 1 to 3 bytes based on the value of
  // schema_type_id.
  std::string encoded_schema_type_id_str =
      encode_util::EncodeIntToCString(schema_type_id);
  // Make encoded_schema_type_id_str to fixed kEncodedSchemaTypeIdLength bytes.
  while (encoded_schema_type_id_str.size() < kEncodedSchemaTypeIdLength) {
    // C string cannot contain 0 bytes, so we append it using 1, just like what
    // we do in encode_util::EncodeIntToCString.
    //
    // The reason that this works is because DecodeIntToString decodes a byte
    // value of 0x01 as 0x00. When EncodeIntToCString returns an encoded
    // schema type id that is less than 3 bytes, it means that the id contains
    // unencoded leading 0x00. So here we're explicitly encoding those bytes as
    // 0x01.
    encoded_schema_type_id_str.push_back(1);
  }

  return absl_ports::StrCat(
      encoded_schema_type_id_str,
      encode_util::EncodeIntToCString(joinable_property_id));
}

std::string GetMetadataFilePath(std::string_view working_path) {
  return absl_ports::StrCat(working_path, "/metadata");
}

std::string GetSchemaJoinableIdToPostingListMapperPath(
    std::string_view working_path) {
  return absl_ports::StrCat(working_path,
                            "/schema_joinable_id_to_posting_list_mapper");
}

std::string GetFlashIndexStorageFilePath(std::string_view working_path) {
  return absl_ports::StrCat(working_path, "/flash_index_storage");
}

}  // namespace

libtextclassifier3::Status
QualifiedIdJoinIndexImplV2::JoinDataIterator::Advance() {
  if (pl_accessor_ == nullptr) {
    return absl_ports::ResourceExhaustedError("End of iterator");
  }

  if (!should_retrieve_next_batch_) {
    // In this case, cached_batch_join_data_ is not empty (contains some data
    // fetched in the previous round), so move curr_ to the next position and
    // check if we have to fetch the next batch.
    //
    // Note: in the 1st round, should_retrieve_next_batch_ is true, so this part
    // will never be executed.
    ++curr_;
    should_retrieve_next_batch_ = curr_ >= cached_batch_join_data_.cend();
  }

  if (should_retrieve_next_batch_) {
    // Fetch next batch if needed.
    ICING_RETURN_IF_ERROR(GetNextDataBatch());
    should_retrieve_next_batch_ = false;
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status
QualifiedIdJoinIndexImplV2::JoinDataIterator::GetNextDataBatch() {
  auto cached_batch_join_data_or = pl_accessor_->GetNextDataBatch();
  if (!cached_batch_join_data_or.ok()) {
    ICING_LOG(WARNING)
        << "Fail to get next batch data from posting list due to: "
        << cached_batch_join_data_or.status().error_message();
    return std::move(cached_batch_join_data_or).status();
  }

  cached_batch_join_data_ = std::move(cached_batch_join_data_or).ValueOrDie();
  curr_ = cached_batch_join_data_.cbegin();

  if (cached_batch_join_data_.empty()) {
    return absl_ports::ResourceExhaustedError("End of iterator");
  }

  return libtextclassifier3::Status::OK;
}

/* static */ libtextclassifier3::StatusOr<
    std::unique_ptr<QualifiedIdJoinIndexImplV2>>
QualifiedIdJoinIndexImplV2::Create(const Filesystem& filesystem,
                                   std::string working_path,
                                   bool pre_mapping_fbv) {
  if (!filesystem.FileExists(GetMetadataFilePath(working_path).c_str()) ||
      !filesystem.DirectoryExists(
          GetSchemaJoinableIdToPostingListMapperPath(working_path).c_str()) ||
      !filesystem.FileExists(
          GetFlashIndexStorageFilePath(working_path).c_str())) {
    // Discard working_path if any file/directory is missing, and reinitialize.
    if (filesystem.DirectoryExists(working_path.c_str())) {
      ICING_RETURN_IF_ERROR(
          QualifiedIdJoinIndex::Discard(filesystem, working_path));
    }
    return InitializeNewFiles(filesystem, std::move(working_path),
                              pre_mapping_fbv);
  }
  return InitializeExistingFiles(filesystem, std::move(working_path),
                                 pre_mapping_fbv);
}

QualifiedIdJoinIndexImplV2::~QualifiedIdJoinIndexImplV2() {
  if (!PersistToDisk().ok()) {
    ICING_LOG(WARNING) << "Failed to persist qualified id join index (v2) to "
                          "disk while destructing "
                       << working_path_;
  }
}

libtextclassifier3::Status QualifiedIdJoinIndexImplV2::Put(
    SchemaTypeId schema_type_id, JoinablePropertyId joinable_property_id,
    DocumentId document_id,
    std::vector<NamespaceFingerprintIdentifier>&&
        ref_namespace_fingerprint_ids) {
  std::sort(ref_namespace_fingerprint_ids.begin(),
            ref_namespace_fingerprint_ids.end());

  // Dedupe.
  auto last = std::unique(ref_namespace_fingerprint_ids.begin(),
                          ref_namespace_fingerprint_ids.end());
  ref_namespace_fingerprint_ids.erase(last,
                                      ref_namespace_fingerprint_ids.end());
  if (ref_namespace_fingerprint_ids.empty()) {
    return libtextclassifier3::Status::OK;
  }

  SetDirty();
  ICING_ASSIGN_OR_RETURN(
      std::string encoded_schema_type_joinable_property_id_str,
      EncodeSchemaTypeJoinablePropertyId(schema_type_id, joinable_property_id));

  ICING_ASSIGN_OR_RETURN(
      PostingListIdentifier posting_list_identifier,
      GetPostingListIdentifier(*schema_joinable_id_to_posting_list_mapper_,
                               encoded_schema_type_joinable_property_id_str));
  std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>> pl_accessor;
  if (posting_list_identifier.is_valid()) {
    ICING_ASSIGN_OR_RETURN(
        pl_accessor,
        PostingListJoinDataAccessor<JoinDataType>::CreateFromExisting(
            flash_index_storage_.get(), posting_list_serializer_.get(),
            posting_list_identifier));
  } else {
    ICING_ASSIGN_OR_RETURN(
        pl_accessor,
        PostingListJoinDataAccessor<JoinDataType>::Create(
            flash_index_storage_.get(), posting_list_serializer_.get()));
  }

  // Prepend join data into posting list.
  for (const NamespaceFingerprintIdentifier& ref_namespace_fingerprint_id :
       ref_namespace_fingerprint_ids) {
    ICING_RETURN_IF_ERROR(pl_accessor->PrependData(
        DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
            document_id, ref_namespace_fingerprint_id)));
  }

  // Finalize the posting list and update mapper.
  PostingListAccessor::FinalizeResult result =
      std::move(*pl_accessor).Finalize();
  if (!result.status.ok()) {
    return result.status;
  }
  if (!result.id.is_valid()) {
    return absl_ports::InternalError("Fail to flush data into posting list(s)");
  }
  ICING_RETURN_IF_ERROR(schema_joinable_id_to_posting_list_mapper_->Put(
      encoded_schema_type_joinable_property_id_str, result.id));

  // Update info.
  info().num_data += ref_namespace_fingerprint_ids.size();

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<
    std::unique_ptr<QualifiedIdJoinIndex::JoinDataIteratorBase>>
QualifiedIdJoinIndexImplV2::GetIterator(
    SchemaTypeId schema_type_id,
    JoinablePropertyId joinable_property_id) const {
  ICING_ASSIGN_OR_RETURN(
      std::string encoded_schema_type_joinable_property_id_str,
      EncodeSchemaTypeJoinablePropertyId(schema_type_id, joinable_property_id));

  ICING_ASSIGN_OR_RETURN(
      PostingListIdentifier posting_list_identifier,
      GetPostingListIdentifier(*schema_joinable_id_to_posting_list_mapper_,
                               encoded_schema_type_joinable_property_id_str));

  if (!posting_list_identifier.is_valid()) {
    return std::make_unique<JoinDataIterator>(nullptr);
  }

  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>> pl_accessor,
      PostingListJoinDataAccessor<JoinDataType>::CreateFromExisting(
          flash_index_storage_.get(), posting_list_serializer_.get(),
          posting_list_identifier));

  return std::make_unique<JoinDataIterator>(std::move(pl_accessor));
}

libtextclassifier3::Status QualifiedIdJoinIndexImplV2::Optimize(
    const std::vector<DocumentId>& document_id_old_to_new,
    const std::vector<NamespaceId>& namespace_id_old_to_new,
    DocumentId new_last_added_document_id) {
  std::string temp_working_path = working_path_ + "_temp";
  ICING_RETURN_IF_ERROR(
      QualifiedIdJoinIndex::Discard(filesystem_, temp_working_path));

  DestructibleDirectory temp_working_path_ddir(&filesystem_,
                                               std::move(temp_working_path));
  if (!temp_working_path_ddir.is_valid()) {
    return absl_ports::InternalError(
        "Unable to create temp directory to build new qualified id join index "
        "(v2)");
  }

  {
    // Transfer all data from the current to new qualified id join index. Also
    // PersistToDisk and destruct the instance after finishing, so we can safely
    // swap directories later.
    ICING_ASSIGN_OR_RETURN(
        std::unique_ptr<QualifiedIdJoinIndexImplV2> new_index,
        Create(filesystem_, temp_working_path_ddir.dir(), pre_mapping_fbv_));
    ICING_RETURN_IF_ERROR(TransferIndex(
        document_id_old_to_new, namespace_id_old_to_new, new_index.get()));
    new_index->set_last_added_document_id(new_last_added_document_id);
    ICING_RETURN_IF_ERROR(new_index->PersistToDisk());
  }

  // Destruct current index's storage instances to safely swap directories.
  // TODO(b/268521214): handle delete propagation storage
  schema_joinable_id_to_posting_list_mapper_.reset();
  flash_index_storage_.reset();

  if (!filesystem_.SwapFiles(temp_working_path_ddir.dir().c_str(),
                             working_path_.c_str())) {
    return absl_ports::InternalError(
        "Unable to apply new qualified id join index (v2) due to failed swap");
  }

  // Reinitialize qualified id join index.
  if (!filesystem_.PRead(GetMetadataFilePath(working_path_).c_str(),
                         metadata_buffer_.get(), kMetadataFileSize,
                         /*offset=*/0)) {
    return absl_ports::InternalError("Fail to read metadata file");
  }
  ICING_ASSIGN_OR_RETURN(
      schema_joinable_id_to_posting_list_mapper_,
      PersistentHashMapKeyMapper<PostingListIdentifier>::Create(
          filesystem_,
          GetSchemaJoinableIdToPostingListMapperPath(working_path_),
          pre_mapping_fbv_,
          /*max_num_entries=*/
          kSchemaJoinableIdToPostingListMapperMaxNumEntries,
          /*average_kv_byte_size=*/
          kSchemaJoinableIdToPostingListMapperAverageKVByteSize));
  ICING_ASSIGN_OR_RETURN(
      FlashIndexStorage flash_index_storage,
      FlashIndexStorage::Create(GetFlashIndexStorageFilePath(working_path_),
                                &filesystem_, posting_list_serializer_.get()));
  flash_index_storage_ =
      std::make_unique<FlashIndexStorage>(std::move(flash_index_storage));

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status QualifiedIdJoinIndexImplV2::Clear() {
  SetDirty();

  schema_joinable_id_to_posting_list_mapper_.reset();
  // Discard and reinitialize schema_joinable_id_to_posting_list_mapper.
  std::string schema_joinable_id_to_posting_list_mapper_path =
      GetSchemaJoinableIdToPostingListMapperPath(working_path_);
  ICING_RETURN_IF_ERROR(
      PersistentHashMapKeyMapper<PostingListIdentifier>::Delete(
          filesystem_, schema_joinable_id_to_posting_list_mapper_path));
  ICING_ASSIGN_OR_RETURN(
      schema_joinable_id_to_posting_list_mapper_,
      PersistentHashMapKeyMapper<PostingListIdentifier>::Create(
          filesystem_,
          std::move(schema_joinable_id_to_posting_list_mapper_path),
          pre_mapping_fbv_,
          /*max_num_entries=*/
          kSchemaJoinableIdToPostingListMapperMaxNumEntries,
          /*average_kv_byte_size=*/
          kSchemaJoinableIdToPostingListMapperAverageKVByteSize));

  // Discard and reinitialize flash_index_storage.
  flash_index_storage_.reset();
  if (!filesystem_.DeleteFile(
          GetFlashIndexStorageFilePath(working_path_).c_str())) {
    return absl_ports::InternalError("Fail to delete flash index storage file");
  }
  ICING_ASSIGN_OR_RETURN(
      FlashIndexStorage flash_index_storage,
      FlashIndexStorage::Create(GetFlashIndexStorageFilePath(working_path_),
                                &filesystem_, posting_list_serializer_.get()));
  flash_index_storage_ =
      std::make_unique<FlashIndexStorage>(std::move(flash_index_storage));

  // TODO(b/268521214): clear delete propagation storage

  info().num_data = 0;
  info().last_added_document_id = kInvalidDocumentId;
  return libtextclassifier3::Status::OK;
}

/* static */ libtextclassifier3::StatusOr<
    std::unique_ptr<QualifiedIdJoinIndexImplV2>>
QualifiedIdJoinIndexImplV2::InitializeNewFiles(const Filesystem& filesystem,
                                               std::string&& working_path,
                                               bool pre_mapping_fbv) {
  // Create working directory.
  if (!filesystem.CreateDirectoryRecursively(working_path.c_str())) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to create directory: ", working_path));
  }

  // Initialize schema_joinable_id_to_posting_list_mapper
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<KeyMapper<PostingListIdentifier>>
          schema_joinable_id_to_posting_list_mapper,
      PersistentHashMapKeyMapper<PostingListIdentifier>::Create(
          filesystem, GetSchemaJoinableIdToPostingListMapperPath(working_path),
          pre_mapping_fbv,
          /*max_num_entries=*/
          kSchemaJoinableIdToPostingListMapperMaxNumEntries,
          /*average_kv_byte_size=*/
          kSchemaJoinableIdToPostingListMapperAverageKVByteSize));

  // Initialize flash_index_storage
  auto posting_list_serializer =
      std::make_unique<PostingListJoinDataSerializer<JoinDataType>>();
  ICING_ASSIGN_OR_RETURN(
      FlashIndexStorage flash_index_storage,
      FlashIndexStorage::Create(GetFlashIndexStorageFilePath(working_path),
                                &filesystem, posting_list_serializer.get()));

  // Create instance.
  auto new_join_index = std::unique_ptr<QualifiedIdJoinIndexImplV2>(
      new QualifiedIdJoinIndexImplV2(
          filesystem, std::move(working_path),
          /*metadata_buffer=*/std::make_unique<uint8_t[]>(kMetadataFileSize),
          std::move(schema_joinable_id_to_posting_list_mapper),
          std::move(posting_list_serializer),
          std::make_unique<FlashIndexStorage>(std::move(flash_index_storage)),
          pre_mapping_fbv));
  // Initialize info content.
  new_join_index->info().magic = Info::kMagic;
  new_join_index->info().num_data = 0;
  new_join_index->info().last_added_document_id = kInvalidDocumentId;
  // Initialize new PersistentStorage. The initial checksums will be computed
  // and set via InitializeNewStorage.
  ICING_RETURN_IF_ERROR(new_join_index->InitializeNewStorage());

  return new_join_index;
}

/* static */ libtextclassifier3::StatusOr<
    std::unique_ptr<QualifiedIdJoinIndexImplV2>>
QualifiedIdJoinIndexImplV2::InitializeExistingFiles(
    const Filesystem& filesystem, std::string&& working_path,
    bool pre_mapping_fbv) {
  // PRead metadata file.
  auto metadata_buffer = std::make_unique<uint8_t[]>(kMetadataFileSize);
  if (!filesystem.PRead(GetMetadataFilePath(working_path).c_str(),
                        metadata_buffer.get(), kMetadataFileSize,
                        /*offset=*/0)) {
    return absl_ports::InternalError("Fail to read metadata file");
  }

  // Initialize schema_joinable_id_to_posting_list_mapper
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<KeyMapper<PostingListIdentifier>>
          schema_joinable_id_to_posting_list_mapper,
      PersistentHashMapKeyMapper<PostingListIdentifier>::Create(
          filesystem, GetSchemaJoinableIdToPostingListMapperPath(working_path),
          pre_mapping_fbv,
          /*max_num_entries=*/
          kSchemaJoinableIdToPostingListMapperMaxNumEntries,
          /*average_kv_byte_size=*/
          kSchemaJoinableIdToPostingListMapperAverageKVByteSize));

  // Initialize flash_index_storage
  auto posting_list_serializer =
      std::make_unique<PostingListJoinDataSerializer<JoinDataType>>();
  ICING_ASSIGN_OR_RETURN(
      FlashIndexStorage flash_index_storage,
      FlashIndexStorage::Create(GetFlashIndexStorageFilePath(working_path),
                                &filesystem, posting_list_serializer.get()));

  // Create instance.
  auto join_index = std::unique_ptr<QualifiedIdJoinIndexImplV2>(
      new QualifiedIdJoinIndexImplV2(
          filesystem, std::move(working_path), std::move(metadata_buffer),
          std::move(schema_joinable_id_to_posting_list_mapper),
          std::move(posting_list_serializer),
          std::make_unique<FlashIndexStorage>(std::move(flash_index_storage)),
          pre_mapping_fbv));
  // Initialize existing PersistentStorage. Checksums will be validated.
  ICING_RETURN_IF_ERROR(join_index->InitializeExistingStorage());

  // Validate magic.
  if (join_index->info().magic != Info::kMagic) {
    return absl_ports::FailedPreconditionError("Incorrect magic value");
  }

  return join_index;
}

libtextclassifier3::Status QualifiedIdJoinIndexImplV2::TransferIndex(
    const std::vector<DocumentId>& document_id_old_to_new,
    const std::vector<NamespaceId>& namespace_id_old_to_new,
    QualifiedIdJoinIndexImplV2* new_index) const {
  std::unique_ptr<KeyMapper<PostingListIdentifier>::Iterator> iter =
      schema_joinable_id_to_posting_list_mapper_->GetIterator();

  // Iterate through all (schema_type_id, joinable_property_id).
  while (iter->Advance()) {
    PostingListIdentifier old_pl_id = iter->GetValue();
    if (!old_pl_id.is_valid()) {
      // Skip invalid posting list id.
      continue;
    }

    // Read all join data from old posting lists and convert to new join data
    // with new document id, namespace id.
    std::vector<JoinDataType> new_join_data_vec;
    ICING_ASSIGN_OR_RETURN(
        std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>>
            old_pl_accessor,
        PostingListJoinDataAccessor<JoinDataType>::CreateFromExisting(
            flash_index_storage_.get(), posting_list_serializer_.get(),
            old_pl_id));
    ICING_ASSIGN_OR_RETURN(std::vector<JoinDataType> batch_old_join_data,
                           old_pl_accessor->GetNextDataBatch());
    while (!batch_old_join_data.empty()) {
      for (const JoinDataType& old_join_data : batch_old_join_data) {
        DocumentId new_document_id = GetNewDocumentId(
            document_id_old_to_new, old_join_data.document_id());
        NamespaceId new_ref_namespace_id = GetNewNamespaceId(
            namespace_id_old_to_new, old_join_data.join_info().namespace_id());

        // Transfer if the document and namespace are not deleted or outdated.
        if (new_document_id != kInvalidDocumentId &&
            new_ref_namespace_id != kInvalidNamespaceId) {
          // We can reuse the fingerprint from old_join_data, since document uri
          // (and its fingerprint) will never change.
          new_join_data_vec.push_back(JoinDataType(
              new_document_id, NamespaceFingerprintIdentifier(
                                   new_ref_namespace_id,
                                   old_join_data.join_info().fingerprint())));
        }
      }
      ICING_ASSIGN_OR_RETURN(batch_old_join_data,
                             old_pl_accessor->GetNextDataBatch());
    }

    if (new_join_data_vec.empty()) {
      continue;
    }

    // NamespaceId order may change, so we have to sort the vector.
    std::sort(new_join_data_vec.begin(), new_join_data_vec.end());

    // Create new posting list in new_index and prepend all new join data into
    // it.
    ICING_ASSIGN_OR_RETURN(
        std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>>
            new_pl_accessor,
        PostingListJoinDataAccessor<JoinDataType>::Create(
            new_index->flash_index_storage_.get(),
            new_index->posting_list_serializer_.get()));
    for (const JoinDataType& new_join_data : new_join_data_vec) {
      ICING_RETURN_IF_ERROR(new_pl_accessor->PrependData(new_join_data));
    }

    // Finalize the posting list and update mapper of new_index.
    PostingListAccessor::FinalizeResult result =
        std::move(*new_pl_accessor).Finalize();
    if (!result.status.ok()) {
      return result.status;
    }
    if (!result.id.is_valid()) {
      return absl_ports::InternalError(
          "Fail to flush data into posting list(s)");
    }
    ICING_RETURN_IF_ERROR(
        new_index->schema_joinable_id_to_posting_list_mapper_->Put(
            iter->GetKey(), result.id));

    // Update info.
    new_index->info().num_data += new_join_data_vec.size();
  }

  // TODO(b/268521214): transfer delete propagation storage

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status QualifiedIdJoinIndexImplV2::PersistMetadataToDisk(
    bool force) {
  if (!force && !is_info_dirty() && !is_storage_dirty()) {
    return libtextclassifier3::Status::OK;
  }

  std::string metadata_file_path = GetMetadataFilePath(working_path_);

  ScopedFd sfd(filesystem_.OpenForWrite(metadata_file_path.c_str()));
  if (!sfd.is_valid()) {
    return absl_ports::InternalError("Fail to open metadata file for write");
  }

  if (!filesystem_.PWrite(sfd.get(), /*offset=*/0, metadata_buffer_.get(),
                          kMetadataFileSize)) {
    return absl_ports::InternalError("Fail to write metadata file");
  }

  if (!filesystem_.DataSync(sfd.get())) {
    return absl_ports::InternalError("Fail to sync metadata to disk");
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status QualifiedIdJoinIndexImplV2::PersistStoragesToDisk(
    bool force) {
  if (!force && !is_storage_dirty()) {
    return libtextclassifier3::Status::OK;
  }

  ICING_RETURN_IF_ERROR(
      schema_joinable_id_to_posting_list_mapper_->PersistToDisk());
  if (!flash_index_storage_->PersistToDisk()) {
    return absl_ports::InternalError(
        "Fail to persist FlashIndexStorage to disk");
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<Crc32>
QualifiedIdJoinIndexImplV2::ComputeInfoChecksum(bool force) {
  if (!force && !is_info_dirty()) {
    return Crc32(crcs().component_crcs.info_crc);
  }

  return info().ComputeChecksum();
}

libtextclassifier3::StatusOr<Crc32>
QualifiedIdJoinIndexImplV2::ComputeStoragesChecksum(bool force) {
  if (!force && !is_storage_dirty()) {
    return Crc32(crcs().component_crcs.storages_crc);
  }

  ICING_ASSIGN_OR_RETURN(
      Crc32 schema_joinable_id_to_posting_list_mapper_crc,
      schema_joinable_id_to_posting_list_mapper_->ComputeChecksum());

  return Crc32(schema_joinable_id_to_posting_list_mapper_crc.Get());
}

}  // namespace lib
}  // namespace icing
