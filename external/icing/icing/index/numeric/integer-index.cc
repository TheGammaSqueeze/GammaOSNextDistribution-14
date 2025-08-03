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

#include "icing/index/numeric/integer-index.h"

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
#include "icing/file/memory-mapped-file.h"
#include "icing/index/iterator/doc-hit-info-iterator-section-restrict.h"
#include "icing/index/numeric/doc-hit-info-iterator-numeric.h"
#include "icing/index/numeric/integer-index-storage.h"
#include "icing/index/numeric/posting-list-integer-index-serializer.h"
#include "icing/store/document-id.h"
#include "icing/util/crc32.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

// Helper function to get the file name of metadata.
std::string GetMetadataFileName() {
  return absl_ports::StrCat(IntegerIndex::kFilePrefix, ".m");
}

// Helper function to get the file path of metadata according to the given
// working directory.
std::string GetMetadataFilePath(std::string_view working_path) {
  return absl_ports::StrCat(working_path, "/", GetMetadataFileName());
}

constexpr std::string_view kWildcardPropertyIndexFileName =
    "wildcard_property_index";

constexpr std::string_view kWildcardPropertyStorageFileName =
    "wildcard_property_storage";

std::string GetWildcardPropertyStorageFilePath(std::string_view working_path) {
  return absl_ports::StrCat(working_path, "/",
                            kWildcardPropertyStorageFileName);
}

// Helper function to get the sub working (directory) path of
// IntegerIndexStorage according to the given working directory and property
// path.
std::string GetPropertyIndexStoragePath(std::string_view working_path,
                                        std::string_view property_path) {
  return absl_ports::StrCat(working_path, "/", property_path);
}

// Helper function to get all existing property paths by listing all
// directories.
libtextclassifier3::StatusOr<std::vector<std::string>>
GetAllExistingPropertyPaths(const Filesystem& filesystem,
                            const std::string& working_path) {
  std::vector<std::string> property_paths;
  std::unordered_set<std::string> excludes = {
      GetMetadataFileName(), std::string(kWildcardPropertyStorageFileName)};
  if (!filesystem.ListDirectory(working_path.c_str(), excludes,
                                /*recursive=*/false, &property_paths)) {
    return absl_ports::InternalError("Failed to list directory");
  }
  return property_paths;
}

libtextclassifier3::StatusOr<IntegerIndex::PropertyToStorageMapType>
GetPropertyIntegerIndexStorageMap(
    const Filesystem& filesystem, const std::string& working_path,
    PostingListIntegerIndexSerializer* posting_list_serializer,
    int32_t num_data_threshold_for_bucket_split, bool pre_mapping_fbv) {
  ICING_ASSIGN_OR_RETURN(std::vector<std::string> property_paths,
                         GetAllExistingPropertyPaths(filesystem, working_path));

  IntegerIndex::PropertyToStorageMapType property_to_storage_map;
  for (const std::string& property_path : property_paths) {
    if (property_path == kWildcardPropertyIndexFileName) {
      continue;
    }
    std::string storage_working_path =
        GetPropertyIndexStoragePath(working_path, property_path);
    ICING_ASSIGN_OR_RETURN(
        std::unique_ptr<IntegerIndexStorage> storage,
        IntegerIndexStorage::Create(
            filesystem, storage_working_path,
            IntegerIndexStorage::Options(num_data_threshold_for_bucket_split,
                                         pre_mapping_fbv),
            posting_list_serializer));
    property_to_storage_map.insert(
        std::make_pair(property_path, std::move(storage)));
  }

  return property_to_storage_map;
}

// RETURNS:
//   - On success, an unordered_set representing the list of property paths
//     stored in the WildcardPropertyStorage managed by property_storage
//   - INTERNAL_ERROR on any failure to successfully read the underlying proto.
libtextclassifier3::StatusOr<std::unordered_set<std::string>> CreatePropertySet(
    const FileBackedProto<WildcardPropertyStorage>& property_storage) {
  std::unordered_set<std::string> wildcard_properties_set;
  auto wildcard_properties_or = property_storage.Read();
  if (!wildcard_properties_or.ok()) {
    if (absl_ports::IsNotFound(wildcard_properties_or.status())) {
      return wildcard_properties_set;
    }
    return wildcard_properties_or.status();
  }

  const WildcardPropertyStorage* wildcard_properties =
      wildcard_properties_or.ValueOrDie();
  wildcard_properties_set.reserve(wildcard_properties->property_entries_size());
  for (const std::string& property : wildcard_properties->property_entries()) {
    wildcard_properties_set.insert(property);
  }
  return wildcard_properties_set;
}

}  // namespace

libtextclassifier3::Status IntegerIndex::Editor::IndexAllBufferedKeys() && {
  integer_index_.SetDirty();

  auto iter = integer_index_.property_to_storage_map_.find(property_path_);
  IntegerIndexStorage* target_storage = nullptr;
  // 1. Check if this property already has its own individual index.
  if (iter != integer_index_.property_to_storage_map_.end()) {
    target_storage = iter->second.get();
    // 2. Check if this property was added to wildcard storage.
  } else if (integer_index_.wildcard_properties_set_.find(property_path_) !=
             integer_index_.wildcard_properties_set_.end()) {
    target_storage = integer_index_.wildcard_index_storage_.get();
    // 3. Check if we've reach the limit of individual property storages.
  } else if (integer_index_.property_to_storage_map_.size() >=
             kMaxPropertyStorages) {
    // 3a. Create the wildcard storage if it doesn't exist.
    if (integer_index_.wildcard_index_storage_ == nullptr) {
      ICING_ASSIGN_OR_RETURN(
          integer_index_.wildcard_index_storage_,
          IntegerIndexStorage::Create(
              integer_index_.filesystem_,
              GetPropertyIndexStoragePath(integer_index_.working_path_,
                                          kWildcardPropertyIndexFileName),
              IntegerIndexStorage::Options(num_data_threshold_for_bucket_split_,
                                           pre_mapping_fbv_),
              integer_index_.posting_list_serializer_.get()));
    }
    ICING_RETURN_IF_ERROR(
        integer_index_.AddPropertyToWildcardStorage(property_path_));
    target_storage = integer_index_.wildcard_index_storage_.get();
    // 4. Create a new individual storage for this new property.
  } else {
    ICING_ASSIGN_OR_RETURN(
        std::unique_ptr<IntegerIndexStorage> new_storage,
        IntegerIndexStorage::Create(
            integer_index_.filesystem_,
            GetPropertyIndexStoragePath(integer_index_.working_path_,
                                        property_path_),
            IntegerIndexStorage::Options(num_data_threshold_for_bucket_split_,
                                         pre_mapping_fbv_),
            integer_index_.posting_list_serializer_.get()));
    target_storage = new_storage.get();
    integer_index_.property_to_storage_map_.insert(
        std::make_pair(property_path_, std::move(new_storage)));
  }

  return target_storage->AddKeys(document_id_, section_id_,
                                 std::move(seen_keys_));
}

/* static */ libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndex>>
IntegerIndex::Create(const Filesystem& filesystem, std::string working_path,
                     int32_t num_data_threshold_for_bucket_split,
                     bool pre_mapping_fbv) {
  if (!filesystem.FileExists(GetMetadataFilePath(working_path).c_str())) {
    // Discard working_path if metadata file is missing, and reinitialize.
    if (filesystem.DirectoryExists(working_path.c_str())) {
      ICING_RETURN_IF_ERROR(Discard(filesystem, working_path));
    }
    return InitializeNewFiles(filesystem, std::move(working_path),
                              num_data_threshold_for_bucket_split,
                              pre_mapping_fbv);
  }
  return InitializeExistingFiles(filesystem, std::move(working_path),
                                 num_data_threshold_for_bucket_split,
                                 pre_mapping_fbv);
}

IntegerIndex::~IntegerIndex() {
  if (!PersistToDisk().ok()) {
    ICING_LOG(WARNING)
        << "Failed to persist integer index to disk while destructing "
        << working_path_;
  }
}

libtextclassifier3::StatusOr<std::unique_ptr<DocHitInfoIterator>>
IntegerIndex::GetIterator(std::string_view property_path, int64_t key_lower,
                          int64_t key_upper,
                          const DocumentStore& document_store,
                          const SchemaStore& schema_store,
                          int64_t current_time_ms) const {
  std::string property_path_str(property_path);
  auto iter = property_to_storage_map_.find(property_path_str);
  if (iter != property_to_storage_map_.end()) {
    return iter->second->GetIterator(key_lower, key_upper);
  }

  if (wildcard_properties_set_.find(property_path_str) !=
      wildcard_properties_set_.end()) {
    ICING_ASSIGN_OR_RETURN(
        std::unique_ptr<DocHitInfoIterator> delegate,
        wildcard_index_storage_->GetIterator(key_lower, key_upper));
    std::set<std::string> property_paths = {std::move(property_path_str)};
    return DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
        std::move(delegate), &document_store, &schema_store,
        std::move(property_paths), current_time_ms);
  }

  // Return an empty iterator.
  return std::make_unique<DocHitInfoIteratorNumeric<int64_t>>(
      /*numeric_index_iter=*/nullptr);
}

libtextclassifier3::Status IntegerIndex::AddPropertyToWildcardStorage(
    const std::string& property_path) {
  SetDirty();

  WildcardPropertyStorage wildcard_properties;
  wildcard_properties.mutable_property_entries()->Reserve(
      wildcard_properties_set_.size());
  for (const std::string& property_path : wildcard_properties_set_) {
    wildcard_properties.add_property_entries(property_path);
  }
  ICING_RETURN_IF_ERROR(wildcard_property_storage_->Write(
      std::make_unique<WildcardPropertyStorage>(
          std::move(wildcard_properties))));

  wildcard_properties_set_.insert(property_path);
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status IntegerIndex::Optimize(
    const std::vector<DocumentId>& document_id_old_to_new,
    DocumentId new_last_added_document_id) {
  std::string temp_working_path = working_path_ + "_temp";
  ICING_RETURN_IF_ERROR(Discard(filesystem_, temp_working_path));

  DestructibleDirectory temp_working_path_ddir(&filesystem_,
                                               std::move(temp_working_path));
  if (!temp_working_path_ddir.is_valid()) {
    return absl_ports::InternalError(
        "Unable to create temp directory to build new integer index");
  }

  {
    // Transfer all indexed data from current integer index to new integer
    // index. Also PersistToDisk and destruct the instance after finishing, so
    // we can safely swap directories later.
    ICING_ASSIGN_OR_RETURN(
        std::unique_ptr<IntegerIndex> new_integer_index,
        Create(filesystem_, temp_working_path_ddir.dir(),
               num_data_threshold_for_bucket_split_, pre_mapping_fbv_));
    ICING_RETURN_IF_ERROR(
        TransferIndex(document_id_old_to_new, new_integer_index.get()));
    new_integer_index->set_last_added_document_id(new_last_added_document_id);
    ICING_RETURN_IF_ERROR(new_integer_index->PersistToDisk());
  }

  // Destruct current storage instances to safely swap directories.
  metadata_mmapped_file_.reset();
  property_to_storage_map_.clear();
  wildcard_index_storage_.reset();
  wildcard_property_storage_.reset();
  if (!filesystem_.SwapFiles(temp_working_path_ddir.dir().c_str(),
                             working_path_.c_str())) {
    return absl_ports::InternalError(
        "Unable to apply new integer index due to failed swap");
  }

  // Reinitialize the integer index.
  std::string metadata_file_path = GetMetadataFilePath(working_path_);
  ICING_ASSIGN_OR_RETURN(
      MemoryMappedFile metadata_mmapped_file,
      MemoryMappedFile::Create(filesystem_, metadata_file_path,
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               /*max_file_size=*/kMetadataFileSize,
                               /*pre_mapping_file_offset=*/0,
                               /*pre_mapping_mmap_size=*/kMetadataFileSize));
  if (metadata_mmapped_file.available_size() != kMetadataFileSize) {
    return absl_ports::InternalError(
        "Invalid metadata file size after Optimize");
  }
  metadata_mmapped_file_ =
      std::make_unique<MemoryMappedFile>(std::move(metadata_mmapped_file));

  // Recreate all of the data structures tracking the wildcard storage.
  std::string wildcard_property_path =
      GetWildcardPropertyStorageFilePath(working_path_);
  wildcard_property_storage_ =
      std::make_unique<FileBackedProto<WildcardPropertyStorage>>(
          filesystem_, wildcard_property_path);

  ICING_ASSIGN_OR_RETURN(wildcard_properties_set_,
                         CreatePropertySet(*wildcard_property_storage_));
  if (!wildcard_properties_set_.empty()) {
    ICING_ASSIGN_OR_RETURN(
        wildcard_index_storage_,
        IntegerIndexStorage::Create(
            filesystem_,
            GetPropertyIndexStoragePath(working_path_,
                                        kWildcardPropertyIndexFileName),
            IntegerIndexStorage::Options(num_data_threshold_for_bucket_split_,
                                         pre_mapping_fbv_),
            posting_list_serializer_.get()));
  }

  // Initialize all existing integer index storages.
  ICING_ASSIGN_OR_RETURN(
      property_to_storage_map_,
      GetPropertyIntegerIndexStorageMap(
          filesystem_, working_path_, posting_list_serializer_.get(),
          num_data_threshold_for_bucket_split_, pre_mapping_fbv_));

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status IntegerIndex::Clear() {
  SetDirty();

  // Step 1: clear property_to_storage_map_.
  property_to_storage_map_.clear();
  wildcard_index_storage_.reset();

  // Step 2: delete all IntegerIndexStorages. It is safe because there is no
  //         active IntegerIndexStorage after clearing the map.
  ICING_ASSIGN_OR_RETURN(
      std::vector<std::string> property_paths,
      GetAllExistingPropertyPaths(filesystem_, working_path_));
  for (const std::string& property_path : property_paths) {
    ICING_RETURN_IF_ERROR(IntegerIndexStorage::Discard(
        filesystem_,
        GetPropertyIndexStoragePath(working_path_, property_path)));
  }

  // Step 3: Delete the wildcard property storage
  std::string wildcard_property_path =
      GetWildcardPropertyStorageFilePath(working_path_);
  if (filesystem_.FileExists(wildcard_property_path.c_str()) ||
      !filesystem_.DeleteFile(wildcard_property_path.c_str())) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "Unable to delete file at path ", wildcard_property_path));
  }

  info().last_added_document_id = kInvalidDocumentId;
  return libtextclassifier3::Status::OK;
}

/* static */ libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndex>>
IntegerIndex::InitializeNewFiles(const Filesystem& filesystem,
                                 std::string&& working_path,
                                 int32_t num_data_threshold_for_bucket_split,
                                 bool pre_mapping_fbv) {
  // Create working directory.
  if (!filesystem.CreateDirectoryRecursively(working_path.c_str())) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Failed to create directory: ", working_path));
  }

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

  std::string wildcard_property_path =
      GetWildcardPropertyStorageFilePath(working_path);
  auto wildcard_property_storage =
      std::make_unique<FileBackedProto<WildcardPropertyStorage>>(
          filesystem, wildcard_property_path);

  // Create instance.
  auto new_integer_index = std::unique_ptr<IntegerIndex>(new IntegerIndex(
      filesystem, std::move(working_path),
      std::make_unique<PostingListIntegerIndexSerializer>(),
      std::make_unique<MemoryMappedFile>(std::move(metadata_mmapped_file)),
      /*property_to_storage_map=*/{}, std::move(wildcard_property_storage),
      /*wildcard_properties_set=*/{}, /*wildcard_index_storage=*/nullptr,
      num_data_threshold_for_bucket_split, pre_mapping_fbv));

  // Initialize info content by writing mapped memory directly.
  Info& info_ref = new_integer_index->info();
  info_ref.magic = Info::kMagic;
  info_ref.last_added_document_id = kInvalidDocumentId;
  info_ref.num_data_threshold_for_bucket_split =
      num_data_threshold_for_bucket_split;
  // Initialize new PersistentStorage. The initial checksums will be computed
  // and set via InitializeNewStorage.
  ICING_RETURN_IF_ERROR(new_integer_index->InitializeNewStorage());

  return new_integer_index;
}

/* static */ libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndex>>
IntegerIndex::InitializeExistingFiles(
    const Filesystem& filesystem, std::string&& working_path,
    int32_t num_data_threshold_for_bucket_split, bool pre_mapping_fbv) {
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

  auto posting_list_serializer =
      std::make_unique<PostingListIntegerIndexSerializer>();

  // Initialize all existing integer index storages.
  ICING_ASSIGN_OR_RETURN(
      PropertyToStorageMapType property_to_storage_map,
      GetPropertyIntegerIndexStorageMap(
          filesystem, working_path, posting_list_serializer.get(),
          num_data_threshold_for_bucket_split, pre_mapping_fbv));

  std::string wildcard_property_path =
      GetWildcardPropertyStorageFilePath(working_path);
  auto wildcard_property_storage =
      std::make_unique<FileBackedProto<WildcardPropertyStorage>>(
          filesystem, wildcard_property_path);

  ICING_ASSIGN_OR_RETURN(
      std::unordered_set<std::string> wildcard_properties_set,
      CreatePropertySet(*wildcard_property_storage));

  std::unique_ptr<IntegerIndexStorage> wildcard_index_storage;
  if (!wildcard_properties_set.empty()) {
    ICING_ASSIGN_OR_RETURN(
        wildcard_index_storage,
        IntegerIndexStorage::Create(
            filesystem,
            GetPropertyIndexStoragePath(working_path,
                                        kWildcardPropertyIndexFileName),
            IntegerIndexStorage::Options(num_data_threshold_for_bucket_split,
                                         pre_mapping_fbv),
            posting_list_serializer.get()));
  }

  // Create instance.
  auto integer_index = std::unique_ptr<IntegerIndex>(new IntegerIndex(
      filesystem, std::move(working_path), std::move(posting_list_serializer),
      std::make_unique<MemoryMappedFile>(std::move(metadata_mmapped_file)),
      std::move(property_to_storage_map), std::move(wildcard_property_storage),
      std::move(wildcard_properties_set), std::move(wildcard_index_storage),
      num_data_threshold_for_bucket_split, pre_mapping_fbv));
  // Initialize existing PersistentStorage. Checksums will be validated.
  ICING_RETURN_IF_ERROR(integer_index->InitializeExistingStorage());

  // Validate magic.
  if (integer_index->info().magic != Info::kMagic) {
    return absl_ports::FailedPreconditionError("Incorrect magic value");
  }

  // If num_data_threshold_for_bucket_split mismatches, then return error to let
  // caller rebuild.
  if (integer_index->info().num_data_threshold_for_bucket_split !=
      num_data_threshold_for_bucket_split) {
    return absl_ports::FailedPreconditionError(
        "Mismatch num_data_threshold_for_bucket_split");
  }

  return integer_index;
}

libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndexStorage>>
IntegerIndex::TransferIntegerIndexStorage(
    const std::vector<DocumentId>& document_id_old_to_new,
    const IntegerIndexStorage* old_storage, const std::string& property_path,
    IntegerIndex* new_integer_index) const {
  std::string new_storage_working_path = GetPropertyIndexStoragePath(
      new_integer_index->working_path_, property_path);
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<IntegerIndexStorage> new_storage,
      IntegerIndexStorage::Create(
          new_integer_index->filesystem_, new_storage_working_path,
          IntegerIndexStorage::Options(num_data_threshold_for_bucket_split_,
                                       pre_mapping_fbv_),
          new_integer_index->posting_list_serializer_.get()));

  ICING_RETURN_IF_ERROR(
      old_storage->TransferIndex(document_id_old_to_new, new_storage.get()));

  if (new_storage->num_data() == 0) {
    new_storage.reset();
    ICING_RETURN_IF_ERROR(
        IntegerIndexStorage::Discard(filesystem_, new_storage_working_path));
  }
  return new_storage;
}

libtextclassifier3::Status IntegerIndex::TransferWildcardStorage(
    IntegerIndex* new_integer_index) const {
  auto property_storage = std::make_unique<WildcardPropertyStorage>();
  property_storage->mutable_property_entries()->Reserve(
      wildcard_properties_set_.size());
  for (const std::string& property : wildcard_properties_set_) {
    property_storage->add_property_entries(property);
  }

  ICING_RETURN_IF_ERROR(new_integer_index->wildcard_property_storage_->Write(
      std::move(property_storage)));
  new_integer_index->wildcard_properties_set_ = wildcard_properties_set_;
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status IntegerIndex::TransferIndex(
    const std::vector<DocumentId>& document_id_old_to_new,
    IntegerIndex* new_integer_index) const {
  // Transfer over the integer index storages
  std::unique_ptr<IntegerIndexStorage> new_storage;
  for (const auto& [property_path, old_storage] : property_to_storage_map_) {
    ICING_ASSIGN_OR_RETURN(
        new_storage,
        TransferIntegerIndexStorage(document_id_old_to_new, old_storage.get(),
                                    property_path, new_integer_index));
    if (new_storage != nullptr) {
      new_integer_index->property_to_storage_map_.insert(
          {property_path, std::move(new_storage)});
    }
  }
  if (wildcard_index_storage_ != nullptr) {
    ICING_ASSIGN_OR_RETURN(
        new_storage,
        TransferIntegerIndexStorage(
            document_id_old_to_new, wildcard_index_storage_.get(),
            std::string(kWildcardPropertyIndexFileName), new_integer_index));
    if (new_storage != nullptr) {
      new_integer_index->wildcard_index_storage_ = std::move(new_storage);

      // The only time we need to copy over the list of properties using
      // wildcard storage is if wildcard_index_storage and new_storage are both
      // non-null. Otherwise, the new wildcard index storage won't have any
      // data.
      ICING_RETURN_IF_ERROR(TransferWildcardStorage(new_integer_index));
    }
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status IntegerIndex::PersistStoragesToDisk(bool force) {
  if (!force && !is_storage_dirty()) {
    return libtextclassifier3::Status::OK;
  }

  for (auto& [_, storage] : property_to_storage_map_) {
    ICING_RETURN_IF_ERROR(storage->PersistToDisk());
  }
  // No need to persist wildcard_properties_storage_. All calls to
  // FileBackedProto::Write are fully written through at the time of the call.
  if (wildcard_index_storage_) {
    ICING_RETURN_IF_ERROR(wildcard_index_storage_->PersistToDisk());
  }
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status IntegerIndex::PersistMetadataToDisk(bool force) {
  if (!force && !is_info_dirty() && !is_storage_dirty()) {
    return libtextclassifier3::Status::OK;
  }

  // Changes should have been applied to the underlying file when using
  // MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, but call msync() as an
  // extra safety step to ensure they are written out.
  return metadata_mmapped_file_->PersistToDisk();
}

libtextclassifier3::StatusOr<Crc32> IntegerIndex::ComputeInfoChecksum(
    bool force) {
  if (!force && !is_info_dirty()) {
    return Crc32(crcs().component_crcs.info_crc);
  }

  return info().ComputeChecksum();
}

libtextclassifier3::StatusOr<Crc32> IntegerIndex::ComputeStoragesChecksum(
    bool force) {
  if (!force && !is_storage_dirty()) {
    return Crc32(crcs().component_crcs.storages_crc);
  }

  // XOR all crcs of all storages. Since XOR is commutative and associative,
  // the order doesn't matter.
  uint32_t storages_checksum = 0;
  for (auto& [property_path, storage] : property_to_storage_map_) {
    ICING_ASSIGN_OR_RETURN(Crc32 storage_crc, storage->UpdateChecksums());
    storage_crc.Append(property_path);

    storages_checksum ^= storage_crc.Get();
  }

  if (wildcard_index_storage_ != nullptr) {
    ICING_ASSIGN_OR_RETURN(Crc32 storage_crc,
                           wildcard_index_storage_->UpdateChecksums());
    storages_checksum ^= storage_crc.Get();
  }

  ICING_ASSIGN_OR_RETURN(Crc32 wildcard_properties_crc,
                         wildcard_property_storage_->ComputeChecksum());
  storages_checksum ^= wildcard_properties_crc.Get();

  return Crc32(storages_checksum);
}

}  // namespace lib
}  // namespace icing
