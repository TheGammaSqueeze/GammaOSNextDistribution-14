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

#include "icing/store/document-store.h"

#include <cstdint>
#include <limits>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/text_classifier/lib3/utils/hash/farmhash.h"
#include "icing/absl_ports/annotate.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/file-backed-proto-log.h"
#include "icing/file/file-backed-vector.h"
#include "icing/file/filesystem.h"
#include "icing/file/memory-mapped-file.h"
#include "icing/file/portable-file-backed-proto-log.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/proto/debug.pb.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/document_wrapper.pb.h"
#include "icing/proto/logging.pb.h"
#include "icing/proto/optimize.pb.h"
#include "icing/proto/persist.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/storage.pb.h"
#include "icing/proto/usage.pb.h"
#include "icing/schema/schema-store.h"
#include "icing/store/corpus-associated-scoring-data.h"
#include "icing/store/corpus-id.h"
#include "icing/store/document-associated-score-data.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/document-id.h"
#include "icing/store/document-log-creator.h"
#include "icing/store/dynamic-trie-key-mapper.h"
#include "icing/store/namespace-fingerprint-identifier.h"
#include "icing/store/namespace-id.h"
#include "icing/store/persistent-hash-map-key-mapper.h"
#include "icing/store/usage-store.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/util/clock.h"
#include "icing/util/crc32.h"
#include "icing/util/data-loss.h"
#include "icing/util/encode-util.h"
#include "icing/util/fingerprint-util.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"
#include "icing/util/tokenized-document.h"

namespace icing {
namespace lib {

namespace {

// Used in DocumentId mapper to mark a document as deleted
constexpr int64_t kDocDeletedFlag = -1;
constexpr char kDocumentIdMapperFilename[] = "document_id_mapper";
constexpr char kUriHashMapperWorkingPath[] = "uri_mapper";
constexpr char kDocumentStoreHeaderFilename[] = "document_store_header";
constexpr char kScoreCacheFilename[] = "score_cache";
constexpr char kCorpusScoreCache[] = "corpus_score_cache";
constexpr char kFilterCacheFilename[] = "filter_cache";
constexpr char kNamespaceMapperFilename[] = "namespace_mapper";
constexpr char kUsageStoreDirectoryName[] = "usage_store";
constexpr char kCorpusIdMapperFilename[] = "corpus_mapper";

// Determined through manual testing to allow for 4 million uris. 4 million
// because we allow up to 4 million DocumentIds.
constexpr int32_t kUriDynamicTrieKeyMapperMaxSize =
    144 * 1024 * 1024;  // 144 MiB

constexpr int32_t kUriHashKeyMapperMaxNumEntries =
    kMaxDocumentId + 1;  // 1 << 22, 4M
// - Key: namespace_id_str (3 bytes) + fingerprinted_uri (10 bytes) + '\0' (1
//        byte)
// - Value: DocumentId (4 bytes)
constexpr int32_t kUriHashKeyMapperKVByteSize = 13 + 1 + sizeof(DocumentId);

// 384 KiB for a DynamicTrieKeyMapper would allow each internal array to have a
// max of 128 KiB for storage.
constexpr int32_t kNamespaceMapperMaxSize = 3 * 128 * 1024;  // 384 KiB
constexpr int32_t kCorpusMapperMaxSize = 3 * 128 * 1024;     // 384 KiB

DocumentWrapper CreateDocumentWrapper(DocumentProto&& document) {
  DocumentWrapper document_wrapper;
  *document_wrapper.mutable_document() = std::move(document);
  return document_wrapper;
}

std::string MakeHeaderFilename(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kDocumentStoreHeaderFilename);
}

std::string MakeUriHashMapperWorkingPath(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kUriHashMapperWorkingPath);
}

std::string MakeDocumentIdMapperFilename(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kDocumentIdMapperFilename);
}

std::string MakeScoreCacheFilename(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kScoreCacheFilename);
}

std::string MakeCorpusScoreCache(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kCorpusScoreCache);
}

std::string MakeFilterCacheFilename(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kFilterCacheFilename);
}

std::string MakeNamespaceMapperFilename(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kNamespaceMapperFilename);
}

std::string MakeUsageStoreDirectoryName(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kUsageStoreDirectoryName);
}

std::string MakeCorpusMapperFilename(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kCorpusIdMapperFilename);
}

int64_t CalculateExpirationTimestampMs(int64_t creation_timestamp_ms,
                                       int64_t ttl_ms) {
  if (ttl_ms == 0) {
    // Special case where a TTL of 0 indicates the document should never
    // expire. int64_t max, interpreted as seconds since epoch, represents
    // some point in the year 292,277,026,596. So we're probably ok to use
    // this as "never reaching this point".
    return std::numeric_limits<int64_t>::max();
  }

  int64_t expiration_timestamp_ms;
  if (__builtin_add_overflow(creation_timestamp_ms, ttl_ms,
                             &expiration_timestamp_ms)) {
    // Overflow detected. Treat overflow as the same behavior of just int64_t
    // max
    return std::numeric_limits<int64_t>::max();
  }

  return expiration_timestamp_ms;
}

InitializeStatsProto::RecoveryCause GetRecoveryCause(
    const DocumentLogCreator::CreateResult& create_result,
    bool force_recovery_and_revalidate_documents) {
  if (force_recovery_and_revalidate_documents) {
    return InitializeStatsProto::SCHEMA_CHANGES_OUT_OF_SYNC;
  } else if (create_result.log_create_result.has_data_loss()) {
    return InitializeStatsProto::DATA_LOSS;
  } else if (create_result.preexisting_file_version !=
             DocumentLogCreator::kCurrentVersion) {
    return InitializeStatsProto::LEGACY_DOCUMENT_LOG_FORMAT;
  }
  return InitializeStatsProto::NONE;
}

InitializeStatsProto::DocumentStoreDataStatus GetDataStatus(
    DataLoss data_loss) {
  switch (data_loss) {
    case DataLoss::PARTIAL:
      return InitializeStatsProto::PARTIAL_LOSS;
    case DataLoss::COMPLETE:
      return InitializeStatsProto::COMPLETE_LOSS;
    case DataLoss::NONE:
      return InitializeStatsProto::NO_DATA_LOSS;
  }
}

std::unordered_map<NamespaceId, std::string> GetNamespaceIdsToNamespaces(
    const KeyMapper<NamespaceId>* key_mapper) {
  std::unordered_map<NamespaceId, std::string> namespace_ids_to_namespaces;

  std::unique_ptr<typename KeyMapper<NamespaceId>::Iterator> itr =
      key_mapper->GetIterator();
  while (itr->Advance()) {
    namespace_ids_to_namespaces.insert(
        {itr->GetValue(), std::string(itr->GetKey())});
  }
  return namespace_ids_to_namespaces;
}

libtextclassifier3::StatusOr<std::unique_ptr<
    KeyMapper<DocumentId, fingerprint_util::FingerprintStringFormatter>>>
CreateUriMapper(const Filesystem& filesystem, const std::string& base_dir,
                bool pre_mapping_fbv, bool use_persistent_hash_map) {
  std::string uri_hash_mapper_working_path =
      MakeUriHashMapperWorkingPath(base_dir);
  // Due to historic issue, we use document store's base_dir directly as
  // DynamicTrieKeyMapper's working directory for uri mapper.
  // DynamicTrieKeyMapper also creates a subdirectory "key_mapper_dir", so the
  // actual files will be put under "<base_dir>/key_mapper_dir/".
  bool dynamic_trie_key_mapper_dir_exists = filesystem.DirectoryExists(
      absl_ports::StrCat(base_dir, "/key_mapper_dir").c_str());
  bool persistent_hash_map_dir_exists =
      filesystem.DirectoryExists(uri_hash_mapper_working_path.c_str());
  if ((use_persistent_hash_map && dynamic_trie_key_mapper_dir_exists) ||
      (!use_persistent_hash_map && persistent_hash_map_dir_exists)) {
    // Return a failure here so that the caller can properly delete and rebuild
    // this component.
    return absl_ports::FailedPreconditionError("Key mapper type mismatch");
  }

  if (use_persistent_hash_map) {
    return PersistentHashMapKeyMapper<
        DocumentId, fingerprint_util::FingerprintStringFormatter>::
        Create(filesystem, std::move(uri_hash_mapper_working_path),
               pre_mapping_fbv,
               /*max_num_entries=*/kUriHashKeyMapperMaxNumEntries,
               /*average_kv_byte_size=*/kUriHashKeyMapperKVByteSize);
  } else {
    return DynamicTrieKeyMapper<DocumentId,
                                fingerprint_util::FingerprintStringFormatter>::
        Create(filesystem, base_dir, kUriDynamicTrieKeyMapperMaxSize);
  }
}

}  // namespace

std::string DocumentStore::MakeFingerprint(
    NamespaceId namespace_id, std::string_view namespace_,
    std::string_view uri_or_schema) const {
  if (!namespace_id_fingerprint_) {
    // Using a 64-bit fingerprint to represent the key could lead to collisions.
    // But, even with 200K unique keys, the probability of collision is about
    // one-in-a-billion (https://en.wikipedia.org/wiki/Birthday_attack).
    uint64_t fprint = tc3farmhash::Fingerprint64(
        absl_ports::StrCat(namespace_, uri_or_schema));
    return fingerprint_util::GetFingerprintString(fprint);
  } else {
    return NamespaceFingerprintIdentifier(namespace_id, uri_or_schema)
        .EncodeToCString();
  }
}

DocumentStore::DocumentStore(const Filesystem* filesystem,
                             const std::string_view base_dir,
                             const Clock* clock,
                             const SchemaStore* schema_store,
                             bool namespace_id_fingerprint,
                             bool pre_mapping_fbv, bool use_persistent_hash_map,
                             int32_t compression_level)
    : filesystem_(filesystem),
      base_dir_(base_dir),
      clock_(*clock),
      schema_store_(schema_store),
      document_validator_(schema_store),
      namespace_id_fingerprint_(namespace_id_fingerprint),
      pre_mapping_fbv_(pre_mapping_fbv),
      use_persistent_hash_map_(use_persistent_hash_map),
      compression_level_(compression_level) {}

libtextclassifier3::StatusOr<DocumentId> DocumentStore::Put(
    const DocumentProto& document, int32_t num_tokens,
    PutDocumentStatsProto* put_document_stats) {
  return Put(DocumentProto(document), num_tokens, put_document_stats);
}

libtextclassifier3::StatusOr<DocumentId> DocumentStore::Put(
    DocumentProto&& document, int32_t num_tokens,
    PutDocumentStatsProto* put_document_stats) {
  document.mutable_internal_fields()->set_length_in_tokens(num_tokens);
  return InternalPut(std::move(document), put_document_stats);
}

DocumentStore::~DocumentStore() {
  if (initialized_) {
    if (!PersistToDisk(PersistType::FULL).ok()) {
      ICING_LOG(ERROR)
          << "Error persisting to disk in DocumentStore destructor";
    }
  }
}

libtextclassifier3::StatusOr<DocumentStore::CreateResult> DocumentStore::Create(
    const Filesystem* filesystem, const std::string& base_dir,
    const Clock* clock, const SchemaStore* schema_store,
    bool force_recovery_and_revalidate_documents, bool namespace_id_fingerprint,
    bool pre_mapping_fbv, bool use_persistent_hash_map,
    int32_t compression_level, InitializeStatsProto* initialize_stats) {
  ICING_RETURN_ERROR_IF_NULL(filesystem);
  ICING_RETURN_ERROR_IF_NULL(clock);
  ICING_RETURN_ERROR_IF_NULL(schema_store);

  auto document_store = std::unique_ptr<DocumentStore>(new DocumentStore(
      filesystem, base_dir, clock, schema_store, namespace_id_fingerprint,
      pre_mapping_fbv, use_persistent_hash_map, compression_level));
  ICING_ASSIGN_OR_RETURN(
      InitializeResult initialize_result,
      document_store->Initialize(force_recovery_and_revalidate_documents,
                                 initialize_stats));

  CreateResult create_result;
  create_result.document_store = std::move(document_store);
  create_result.data_loss = initialize_result.data_loss;
  create_result.derived_files_regenerated =
      initialize_result.derived_files_regenerated;
  return create_result;
}

/* static */ libtextclassifier3::Status DocumentStore::DiscardDerivedFiles(
    const Filesystem* filesystem, const std::string& base_dir) {
  // Header
  const std::string header_filename = MakeHeaderFilename(base_dir);
  if (!filesystem->DeleteFile(MakeHeaderFilename(base_dir).c_str())) {
    return absl_ports::InternalError("Couldn't delete header file");
  }

  // Document key mapper. Doesn't hurt to delete both dynamic trie and
  // persistent hash map without checking.
  ICING_RETURN_IF_ERROR(
      DynamicTrieKeyMapper<DocumentId>::Delete(*filesystem, base_dir));
  ICING_RETURN_IF_ERROR(PersistentHashMapKeyMapper<DocumentId>::Delete(
      *filesystem, MakeUriHashMapperWorkingPath(base_dir)));

  // Document id mapper
  ICING_RETURN_IF_ERROR(FileBackedVector<int64_t>::Delete(
      *filesystem, MakeDocumentIdMapperFilename(base_dir)));

  // Document associated score cache
  ICING_RETURN_IF_ERROR(FileBackedVector<DocumentAssociatedScoreData>::Delete(
      *filesystem, MakeScoreCacheFilename(base_dir)));

  // Filter cache
  ICING_RETURN_IF_ERROR(FileBackedVector<DocumentFilterData>::Delete(
      *filesystem, MakeFilterCacheFilename(base_dir)));

  // Namespace mapper
  ICING_RETURN_IF_ERROR(DynamicTrieKeyMapper<NamespaceId>::Delete(
      *filesystem, MakeNamespaceMapperFilename(base_dir)));

  // Corpus mapper
  ICING_RETURN_IF_ERROR(DynamicTrieKeyMapper<CorpusId>::Delete(
      *filesystem, MakeCorpusMapperFilename(base_dir)));

  // Corpus associated score cache
  ICING_RETURN_IF_ERROR(FileBackedVector<CorpusAssociatedScoreData>::Delete(
      *filesystem, MakeCorpusScoreCache(base_dir)));

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<DocumentStore::InitializeResult>
DocumentStore::Initialize(bool force_recovery_and_revalidate_documents,
                          InitializeStatsProto* initialize_stats) {
  auto create_result_or =
      DocumentLogCreator::Create(filesystem_, base_dir_, compression_level_);

  // TODO(b/144458732): Implement a more robust version of TC_ASSIGN_OR_RETURN
  // that can support error logging.
  if (!create_result_or.ok()) {
    ICING_LOG(ERROR) << create_result_or.status().error_message()
                     << "\nFailed to initialize DocumentLog.";
    return create_result_or.status();
  }
  DocumentLogCreator::CreateResult create_result =
      std::move(create_result_or).ValueOrDie();

  document_log_ = std::move(create_result.log_create_result.proto_log);
  InitializeStatsProto::RecoveryCause recovery_cause =
      GetRecoveryCause(create_result, force_recovery_and_revalidate_documents);

  bool derived_files_regenerated = false;
  if (recovery_cause != InitializeStatsProto::NONE || create_result.new_file) {
    ICING_LOG(INFO) << "Starting Document Store Recovery with cause="
                    << recovery_cause << ", and create result { new_file="
                    << create_result.new_file << ", preeisting_file_version="
                    << create_result.preexisting_file_version << ", data_loss="
                    << create_result.log_create_result.data_loss
                    << "} and kCurrentVersion="
                    << DocumentLogCreator::kCurrentVersion;
    // We can't rely on any existing derived files. Recreate them from scratch.
    // Currently happens if:
    //   1) This is a new log and we don't have derived files yet
    //   2) Client wanted us to force a regeneration.
    //   3) Log has some data loss, can't rely on existing derived data.
    std::unique_ptr<Timer> document_recovery_timer = clock_.GetNewTimer();
    libtextclassifier3::Status status =
        RegenerateDerivedFiles(force_recovery_and_revalidate_documents);
    if (recovery_cause != InitializeStatsProto::NONE) {
      // Only consider it a recovery if the client forced a recovery or there
      // was data loss. Otherwise, this could just be the first time we're
      // initializing and generating derived files.
      derived_files_regenerated = true;
      if (initialize_stats != nullptr) {
        initialize_stats->set_document_store_recovery_latency_ms(
            document_recovery_timer->GetElapsedMilliseconds());
        initialize_stats->set_document_store_recovery_cause(recovery_cause);
        initialize_stats->set_document_store_data_status(
            GetDataStatus(create_result.log_create_result.data_loss));
      }
    }
    if (!status.ok()) {
      ICING_LOG(ERROR)
          << "Failed to regenerate derived files for DocumentStore";
      return status;
    }
  } else {
    if (!InitializeExistingDerivedFiles().ok()) {
      ICING_LOG(WARNING)
          << "Couldn't find derived files or failed to initialize them, "
             "regenerating derived files for DocumentStore.";
      std::unique_ptr<Timer> document_recovery_timer = clock_.GetNewTimer();
      derived_files_regenerated = true;
      libtextclassifier3::Status status = RegenerateDerivedFiles(
          /*force_recovery_and_revalidate_documents=*/false);
      if (initialize_stats != nullptr) {
        initialize_stats->set_document_store_recovery_cause(
            InitializeStatsProto::IO_ERROR);
        initialize_stats->set_document_store_recovery_latency_ms(
            document_recovery_timer->GetElapsedMilliseconds());
      }
      if (!status.ok()) {
        ICING_LOG(ERROR)
            << "Failed to regenerate derived files for DocumentStore";
        return status;
      }
    }
  }

  initialized_ = true;
  if (initialize_stats != nullptr) {
    initialize_stats->set_num_documents(document_id_mapper_->num_elements());
  }

  InitializeResult initialize_result = {
      .data_loss = create_result.log_create_result.data_loss,
      .derived_files_regenerated = derived_files_regenerated};
  return initialize_result;
}

libtextclassifier3::Status DocumentStore::InitializeExistingDerivedFiles() {
  if (!HeaderExists()) {
    // Without a header, we don't know if things are consistent between each
    // other so the caller should just regenerate everything from ground
    // truth.
    return absl_ports::InternalError("DocumentStore header doesn't exist");
  }

  DocumentStore::Header header;
  if (!filesystem_->Read(MakeHeaderFilename(base_dir_).c_str(), &header,
                         sizeof(header))) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Couldn't read: ", MakeHeaderFilename(base_dir_)));
  }

  if (header.magic !=
      DocumentStore::Header::GetCurrentMagic(namespace_id_fingerprint_)) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "Invalid header kMagic for file: ", MakeHeaderFilename(base_dir_)));
  }

  // TODO(b/144458732): Implement a more robust version of TC_ASSIGN_OR_RETURN
  // that can support error logging.
  auto document_key_mapper_or = CreateUriMapper(
      *filesystem_, base_dir_, pre_mapping_fbv_, use_persistent_hash_map_);
  if (!document_key_mapper_or.ok()) {
    ICING_LOG(ERROR) << document_key_mapper_or.status().error_message()
                     << "Failed to initialize KeyMapper";
    return document_key_mapper_or.status();
  }
  document_key_mapper_ = std::move(document_key_mapper_or).ValueOrDie();

  // TODO(b/144458732): Implement a more robust version of TC_ASSIGN_OR_RETURN
  // that can support error logging.
  auto document_id_mapper_or = FileBackedVector<int64_t>::Create(
      *filesystem_, MakeDocumentIdMapperFilename(base_dir_),
      MemoryMappedFile::READ_WRITE_AUTO_SYNC);
  if (!document_id_mapper_or.ok()) {
    ICING_LOG(ERROR) << document_id_mapper_or.status().error_message()
                     << "Failed to initialize DocumentIdMapper";
    return document_id_mapper_or.status();
  }
  document_id_mapper_ = std::move(document_id_mapper_or).ValueOrDie();

  ICING_ASSIGN_OR_RETURN(score_cache_,
                         FileBackedVector<DocumentAssociatedScoreData>::Create(
                             *filesystem_, MakeScoreCacheFilename(base_dir_),
                             MemoryMappedFile::READ_WRITE_AUTO_SYNC));

  ICING_ASSIGN_OR_RETURN(filter_cache_,
                         FileBackedVector<DocumentFilterData>::Create(
                             *filesystem_, MakeFilterCacheFilename(base_dir_),
                             MemoryMappedFile::READ_WRITE_AUTO_SYNC));

  ICING_ASSIGN_OR_RETURN(
      namespace_mapper_,
      DynamicTrieKeyMapper<NamespaceId>::Create(
          *filesystem_, MakeNamespaceMapperFilename(base_dir_),
          kNamespaceMapperMaxSize));

  ICING_ASSIGN_OR_RETURN(
      usage_store_,
      UsageStore::Create(filesystem_, MakeUsageStoreDirectoryName(base_dir_)));

  auto corpus_mapper_or =
      DynamicTrieKeyMapper<CorpusId,
                           fingerprint_util::FingerprintStringFormatter>::
          Create(*filesystem_, MakeCorpusMapperFilename(base_dir_),
                 kCorpusMapperMaxSize);
  if (!corpus_mapper_or.ok()) {
    return std::move(corpus_mapper_or).status();
  }
  corpus_mapper_ = std::move(corpus_mapper_or).ValueOrDie();

  ICING_ASSIGN_OR_RETURN(corpus_score_cache_,
                         FileBackedVector<CorpusAssociatedScoreData>::Create(
                             *filesystem_, MakeCorpusScoreCache(base_dir_),
                             MemoryMappedFile::READ_WRITE_AUTO_SYNC));

  // Ensure the usage store is the correct size.
  ICING_RETURN_IF_ERROR(
      usage_store_->TruncateTo(document_id_mapper_->num_elements()));

  ICING_ASSIGN_OR_RETURN(Crc32 checksum, ComputeChecksum());
  if (checksum.Get() != header.checksum) {
    return absl_ports::InternalError(
        "Combined checksum of DocStore was inconsistent");
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status DocumentStore::RegenerateDerivedFiles(
    bool revalidate_documents) {
  ICING_RETURN_IF_ERROR(ResetDocumentKeyMapper());
  ICING_RETURN_IF_ERROR(ResetDocumentIdMapper());
  ICING_RETURN_IF_ERROR(ResetDocumentAssociatedScoreCache());
  ICING_RETURN_IF_ERROR(ResetFilterCache());
  ICING_RETURN_IF_ERROR(ResetNamespaceMapper());
  ICING_RETURN_IF_ERROR(ResetCorpusMapper());
  ICING_RETURN_IF_ERROR(ResetCorpusAssociatedScoreCache());

  // Creates a new UsageStore instance. Note that we don't reset the data in
  // usage store here because we're not able to regenerate the usage scores.
  ICING_ASSIGN_OR_RETURN(
      usage_store_,
      UsageStore::Create(filesystem_, MakeUsageStoreDirectoryName(base_dir_)));

  // Iterates through document log
  auto iterator = document_log_->GetIterator();
  auto iterator_status = iterator.Advance();
  libtextclassifier3::StatusOr<int64_t> element_size =
      document_log_->GetElementsFileSize();
  libtextclassifier3::StatusOr<int64_t> disk_usage =
      document_log_->GetDiskUsage();
  if (element_size.ok() && disk_usage.ok()) {
    ICING_VLOG(1) << "Starting recovery of document store. Document store "
                     "elements file size:"
                  << element_size.ValueOrDie()
                  << ", disk usage=" << disk_usage.ValueOrDie();
  }
  while (iterator_status.ok()) {
    ICING_VLOG(2) << "Attempting to read document at offset="
                  << iterator.GetOffset();
    libtextclassifier3::StatusOr<DocumentWrapper> document_wrapper_or =
        document_log_->ReadProto(iterator.GetOffset());

    if (absl_ports::IsNotFound(document_wrapper_or.status())) {
      // The erased document still occupies 1 document id.
      DocumentId new_document_id = document_id_mapper_->num_elements();
      ICING_RETURN_IF_ERROR(ClearDerivedData(new_document_id));
      iterator_status = iterator.Advance();
      continue;
    } else if (!document_wrapper_or.ok()) {
      return document_wrapper_or.status();
    }

    DocumentWrapper document_wrapper =
        std::move(document_wrapper_or).ValueOrDie();
    // Revalidate that this document is still compatible if requested.
    if (revalidate_documents) {
      if (!document_validator_.Validate(document_wrapper.document()).ok()) {
        // Document is no longer valid with the current schema. Mark as
        // deleted
        DocumentId new_document_id = document_id_mapper_->num_elements();
        ICING_RETURN_IF_ERROR(document_log_->EraseProto(iterator.GetOffset()));
        ICING_RETURN_IF_ERROR(ClearDerivedData(new_document_id));
        continue;
      }
    }

    ICING_ASSIGN_OR_RETURN(
        NamespaceId namespace_id,
        namespace_mapper_->GetOrPut(document_wrapper.document().namespace_(),
                                    namespace_mapper_->num_keys()));

    // Updates key mapper and document_id mapper with the new document
    DocumentId new_document_id = document_id_mapper_->num_elements();
    ICING_RETURN_IF_ERROR(document_key_mapper_->Put(
        MakeFingerprint(namespace_id, document_wrapper.document().namespace_(),
                        document_wrapper.document().uri()),
        new_document_id));
    ICING_RETURN_IF_ERROR(
        document_id_mapper_->Set(new_document_id, iterator.GetOffset()));

    SchemaTypeId schema_type_id;
    auto schema_type_id_or =
        schema_store_->GetSchemaTypeId(document_wrapper.document().schema());
    if (absl_ports::IsNotFound(schema_type_id_or.status())) {
      // Didn't find a SchemaTypeId. This means that the DocumentStore and
      // the SchemaStore are out of sync. But DocumentStore can't do
      // anything about it so just ignore this for now. This should be
      // detected/handled by the owner of DocumentStore. Set it to some
      // arbitrary invalid value for now, it'll get updated to the correct
      // ID later.
      schema_type_id = -1;
    } else if (!schema_type_id_or.ok()) {
      // Real error. Pass it up
      return schema_type_id_or.status();
    } else {
      // We're guaranteed that SchemaTypeId is valid now
      schema_type_id = schema_type_id_or.ValueOrDie();
    }

    // Update corpus maps
    std::string corpus =
        MakeFingerprint(namespace_id, document_wrapper.document().namespace_(),
                        document_wrapper.document().schema());
    ICING_ASSIGN_OR_RETURN(
        CorpusId corpusId,
        corpus_mapper_->GetOrPut(corpus, corpus_mapper_->num_keys()));

    ICING_ASSIGN_OR_RETURN(CorpusAssociatedScoreData scoring_data,
                           GetCorpusAssociatedScoreDataToUpdate(corpusId));
    scoring_data.AddDocument(
        document_wrapper.document().internal_fields().length_in_tokens());

    ICING_RETURN_IF_ERROR(
        UpdateCorpusAssociatedScoreCache(corpusId, scoring_data));

    ICING_RETURN_IF_ERROR(UpdateDocumentAssociatedScoreCache(
        new_document_id,
        DocumentAssociatedScoreData(
            corpusId, document_wrapper.document().score(),
            document_wrapper.document().creation_timestamp_ms(),
            document_wrapper.document().internal_fields().length_in_tokens())));

    int64_t expiration_timestamp_ms = CalculateExpirationTimestampMs(
        document_wrapper.document().creation_timestamp_ms(),
        document_wrapper.document().ttl_ms());

    ICING_RETURN_IF_ERROR(UpdateFilterCache(
        new_document_id, DocumentFilterData(namespace_id, schema_type_id,
                                            expiration_timestamp_ms)));
    iterator_status = iterator.Advance();
  }

  if (!absl_ports::IsOutOfRange(iterator_status)) {
    ICING_LOG(WARNING)
        << "Failed to iterate through proto log while regenerating "
           "derived files";
    return absl_ports::Annotate(iterator_status,
                                "Failed to iterate through proto log.");
  }

  // Shrink usage_store_ to the correct size.
  ICING_RETURN_IF_ERROR(
      usage_store_->TruncateTo(document_id_mapper_->num_elements()));

  // Write the header
  ICING_ASSIGN_OR_RETURN(Crc32 checksum, ComputeChecksum());
  ICING_RETURN_IF_ERROR(UpdateHeader(checksum));

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status DocumentStore::ResetDocumentKeyMapper() {
  // Only one type of KeyMapper (either DynamicTrieKeyMapper or
  // PersistentHashMapKeyMapper) will actually exist at any moment, but it is ok
  // to call Delete() for both since Delete() returns OK if any of them doesn't
  // exist.
  // TODO(b/139734457): Replace ptr.reset()->Delete->Create flow with Reset().
  document_key_mapper_.reset();
  // TODO(b/216487496): Implement a more robust version of TC_RETURN_IF_ERROR
  // that can support error logging.
  libtextclassifier3::Status status =
      DynamicTrieKeyMapper<DocumentId>::Delete(*filesystem_, base_dir_);
  if (!status.ok()) {
    ICING_LOG(ERROR) << status.error_message()
                     << "Failed to delete old dynamic trie key mapper";
    return status;
  }
  status = PersistentHashMapKeyMapper<DocumentId>::Delete(
      *filesystem_, MakeUriHashMapperWorkingPath(base_dir_));
  if (!status.ok()) {
    ICING_LOG(ERROR) << status.error_message()
                     << "Failed to delete old persistent hash map key mapper";
    return status;
  }

  // TODO(b/216487496): Implement a more robust version of TC_ASSIGN_OR_RETURN
  // that can support error logging.
  auto document_key_mapper_or = CreateUriMapper(
      *filesystem_, base_dir_, pre_mapping_fbv_, use_persistent_hash_map_);
  if (!document_key_mapper_or.ok()) {
    ICING_LOG(ERROR) << document_key_mapper_or.status().error_message()
                     << "Failed to re-init key mapper";
    return document_key_mapper_or.status();
  }
  document_key_mapper_ = std::move(document_key_mapper_or).ValueOrDie();
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status DocumentStore::ResetDocumentIdMapper() {
  // TODO(b/139734457): Replace ptr.reset()->Delete->Create flow with Reset().
  document_id_mapper_.reset();
  // TODO(b/216487496): Implement a more robust version of TC_RETURN_IF_ERROR
  // that can support error logging.
  libtextclassifier3::Status status = FileBackedVector<int64_t>::Delete(
      *filesystem_, MakeDocumentIdMapperFilename(base_dir_));
  if (!status.ok()) {
    ICING_LOG(ERROR) << status.error_message()
                     << "Failed to delete old document_id mapper";
    return status;
  }
  // TODO(b/216487496): Implement a more robust version of TC_ASSIGN_OR_RETURN
  // that can support error logging.
  auto document_id_mapper_or = FileBackedVector<int64_t>::Create(
      *filesystem_, MakeDocumentIdMapperFilename(base_dir_),
      MemoryMappedFile::READ_WRITE_AUTO_SYNC);
  if (!document_id_mapper_or.ok()) {
    ICING_LOG(ERROR) << document_id_mapper_or.status().error_message()
                     << "Failed to re-init document_id mapper";
    return document_id_mapper_or.status();
  }
  document_id_mapper_ = std::move(document_id_mapper_or).ValueOrDie();
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status DocumentStore::ResetDocumentAssociatedScoreCache() {
  // TODO(b/139734457): Replace ptr.reset()->Delete->Create flow with Reset().
  score_cache_.reset();
  ICING_RETURN_IF_ERROR(FileBackedVector<DocumentAssociatedScoreData>::Delete(
      *filesystem_, MakeScoreCacheFilename(base_dir_)));
  ICING_ASSIGN_OR_RETURN(score_cache_,
                         FileBackedVector<DocumentAssociatedScoreData>::Create(
                             *filesystem_, MakeScoreCacheFilename(base_dir_),
                             MemoryMappedFile::READ_WRITE_AUTO_SYNC));
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status DocumentStore::ResetCorpusAssociatedScoreCache() {
  // TODO(b/139734457): Replace ptr.reset()->Delete->Create flow with Reset().
  corpus_score_cache_.reset();
  ICING_RETURN_IF_ERROR(FileBackedVector<CorpusAssociatedScoreData>::Delete(
      *filesystem_, MakeCorpusScoreCache(base_dir_)));
  ICING_ASSIGN_OR_RETURN(corpus_score_cache_,
                         FileBackedVector<CorpusAssociatedScoreData>::Create(
                             *filesystem_, MakeCorpusScoreCache(base_dir_),
                             MemoryMappedFile::READ_WRITE_AUTO_SYNC));
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status DocumentStore::ResetFilterCache() {
  // TODO(b/139734457): Replace ptr.reset()->Delete->Create flow with Reset().
  filter_cache_.reset();
  ICING_RETURN_IF_ERROR(FileBackedVector<DocumentFilterData>::Delete(
      *filesystem_, MakeFilterCacheFilename(base_dir_)));
  ICING_ASSIGN_OR_RETURN(filter_cache_,
                         FileBackedVector<DocumentFilterData>::Create(
                             *filesystem_, MakeFilterCacheFilename(base_dir_),
                             MemoryMappedFile::READ_WRITE_AUTO_SYNC));
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status DocumentStore::ResetNamespaceMapper() {
  // TODO(b/139734457): Replace ptr.reset()->Delete->Create flow with Reset().
  namespace_mapper_.reset();
  // TODO(b/216487496): Implement a more robust version of TC_RETURN_IF_ERROR
  // that can support error logging.
  libtextclassifier3::Status status = DynamicTrieKeyMapper<NamespaceId>::Delete(
      *filesystem_, MakeNamespaceMapperFilename(base_dir_));
  if (!status.ok()) {
    ICING_LOG(ERROR) << status.error_message()
                     << "Failed to delete old namespace_id mapper";
    return status;
  }
  ICING_ASSIGN_OR_RETURN(
      namespace_mapper_,
      DynamicTrieKeyMapper<NamespaceId>::Create(
          *filesystem_, MakeNamespaceMapperFilename(base_dir_),
          kNamespaceMapperMaxSize));
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status DocumentStore::ResetCorpusMapper() {
  // TODO(b/139734457): Replace ptr.reset()->Delete->Create flow with Reset().
  corpus_mapper_.reset();
  // TODO(b/216487496): Implement a more robust version of TC_RETURN_IF_ERROR
  // that can support error logging.
  libtextclassifier3::Status status = DynamicTrieKeyMapper<CorpusId>::Delete(
      *filesystem_, MakeCorpusMapperFilename(base_dir_));
  if (!status.ok()) {
    ICING_LOG(ERROR) << status.error_message()
                     << "Failed to delete old corpus_id mapper";
    return status;
  }
  auto corpus_mapper_or =
      DynamicTrieKeyMapper<CorpusId,
                           fingerprint_util::FingerprintStringFormatter>::
          Create(*filesystem_, MakeCorpusMapperFilename(base_dir_),
                 kCorpusMapperMaxSize);
  if (!corpus_mapper_or.ok()) {
    return std::move(corpus_mapper_or).status();
  }
  corpus_mapper_ = std::move(corpus_mapper_or).ValueOrDie();
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<Crc32> DocumentStore::ComputeChecksum() const {
  Crc32 total_checksum;

  // TODO(b/144458732): Implement a more robust version of TC_ASSIGN_OR_RETURN
  // that can support error logging.
  auto checksum_or = document_log_->ComputeChecksum();
  if (!checksum_or.ok()) {
    ICING_LOG(ERROR) << checksum_or.status().error_message()
                     << "Failed to compute checksum of DocumentLog";
    return checksum_or.status();
  }
  Crc32 document_log_checksum = std::move(checksum_or).ValueOrDie();

  // TODO(b/144458732): Implement a more robust version of TC_ASSIGN_OR_RETURN
  // that can support error logging.
  checksum_or = document_key_mapper_->ComputeChecksum();
  if (!checksum_or.ok()) {
    ICING_LOG(ERROR) << checksum_or.status().error_message()
                     << "Failed to compute checksum of DocumentKeyMapper";
    return checksum_or.status();
  }
  Crc32 document_key_mapper_checksum = std::move(checksum_or).ValueOrDie();

  // TODO(b/144458732): Implement a more robust version of TC_ASSIGN_OR_RETURN
  // that can support error logging.
  checksum_or = document_id_mapper_->ComputeChecksum();
  if (!checksum_or.ok()) {
    ICING_LOG(ERROR) << checksum_or.status().error_message()
                     << "Failed to compute checksum of DocumentIdMapper";
    return checksum_or.status();
  }
  Crc32 document_id_mapper_checksum = std::move(checksum_or).ValueOrDie();

  // TODO(b/144458732): Implement a more robust version of TC_ASSIGN_OR_RETURN
  // that can support error logging.
  checksum_or = score_cache_->ComputeChecksum();
  if (!checksum_or.ok()) {
    ICING_LOG(ERROR) << checksum_or.status().error_message()
                     << "Failed to compute checksum of score cache";
    return checksum_or.status();
  }
  Crc32 score_cache_checksum = std::move(checksum_or).ValueOrDie();

  // TODO(b/144458732): Implement a more robust version of TC_ASSIGN_OR_RETURN
  // that can support error logging.
  checksum_or = filter_cache_->ComputeChecksum();
  if (!checksum_or.ok()) {
    ICING_LOG(ERROR) << checksum_or.status().error_message()
                     << "Failed to compute checksum of filter cache";
    return checksum_or.status();
  }
  Crc32 filter_cache_checksum = std::move(checksum_or).ValueOrDie();

  // TODO(b/144458732): Implement a more robust version of TC_ASSIGN_OR_RETURN
  // that can support error logging.
  checksum_or = namespace_mapper_->ComputeChecksum();
  if (!checksum_or.ok()) {
    ICING_LOG(ERROR) << checksum_or.status().error_message()
                     << "Failed to compute checksum of namespace mapper";
    return checksum_or.status();
  }
  Crc32 namespace_mapper_checksum = std::move(checksum_or).ValueOrDie();

  // TODO(b/144458732): Implement a more robust version of TC_ASSIGN_OR_RETURN
  // that can support error logging.
  checksum_or = corpus_mapper_->ComputeChecksum();
  if (!checksum_or.ok()) {
    ICING_LOG(ERROR) << checksum_or.status().error_message()
                     << "Failed to compute checksum of corpus mapper";
    return checksum_or.status();
  }
  Crc32 corpus_mapper_checksum = std::move(checksum_or).ValueOrDie();

  // TODO(b/144458732): Implement a more robust version of TC_ASSIGN_OR_RETURN
  // that can support error logging.
  checksum_or = corpus_score_cache_->ComputeChecksum();
  if (!checksum_or.ok()) {
    ICING_LOG(WARNING) << checksum_or.status().error_message()
                       << "Failed to compute checksum of score cache";
    return checksum_or.status();
  }
  Crc32 corpus_score_cache_checksum = std::move(checksum_or).ValueOrDie();

  // NOTE: We purposely don't include usage_store checksum here because we can't
  // regenerate it from ground truth documents. If it gets corrupted, we'll just
  // clear all usage reports, but we shouldn't throw everything else in the
  // document store out.

  total_checksum.Append(std::to_string(document_log_checksum.Get()));
  total_checksum.Append(std::to_string(document_key_mapper_checksum.Get()));
  total_checksum.Append(std::to_string(document_id_mapper_checksum.Get()));
  total_checksum.Append(std::to_string(score_cache_checksum.Get()));
  total_checksum.Append(std::to_string(filter_cache_checksum.Get()));
  total_checksum.Append(std::to_string(namespace_mapper_checksum.Get()));
  total_checksum.Append(std::to_string(corpus_mapper_checksum.Get()));
  total_checksum.Append(std::to_string(corpus_score_cache_checksum.Get()));

  return total_checksum;
}

bool DocumentStore::HeaderExists() {
  if (!filesystem_->FileExists(MakeHeaderFilename(base_dir_).c_str())) {
    return false;
  }

  int64_t file_size =
      filesystem_->GetFileSize(MakeHeaderFilename(base_dir_).c_str());

  // If it's been truncated to size 0 before, we consider it to be a new file
  return file_size != 0 && file_size != Filesystem::kBadFileSize;
}

libtextclassifier3::Status DocumentStore::UpdateHeader(const Crc32& checksum) {
  // Write the header
  DocumentStore::Header header;
  header.magic =
      DocumentStore::Header::GetCurrentMagic(namespace_id_fingerprint_);
  header.checksum = checksum.Get();

  // This should overwrite the header.
  ScopedFd sfd(
      filesystem_->OpenForWrite(MakeHeaderFilename(base_dir_).c_str()));
  if (!sfd.is_valid() ||
      !filesystem_->Write(sfd.get(), &header, sizeof(header)) ||
      !filesystem_->DataSync(sfd.get())) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "Failed to write DocStore header: ", MakeHeaderFilename(base_dir_)));
  }
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<DocumentId> DocumentStore::InternalPut(
    DocumentProto&& document, PutDocumentStatsProto* put_document_stats) {
  std::unique_ptr<Timer> put_timer = clock_.GetNewTimer();
  ICING_RETURN_IF_ERROR(document_validator_.Validate(document));

  if (put_document_stats != nullptr) {
    put_document_stats->set_document_size(document.ByteSizeLong());
  }

  // Copy fields needed before they are moved
  std::string name_space = document.namespace_();
  std::string uri = document.uri();
  std::string schema = document.schema();
  int document_score = document.score();
  int32_t length_in_tokens = document.internal_fields().length_in_tokens();
  int64_t creation_timestamp_ms = document.creation_timestamp_ms();

  // Sets the creation timestamp if caller hasn't specified.
  if (document.creation_timestamp_ms() == 0) {
    creation_timestamp_ms = clock_.GetSystemTimeMilliseconds();
    document.set_creation_timestamp_ms(creation_timestamp_ms);
  }

  int64_t expiration_timestamp_ms =
      CalculateExpirationTimestampMs(creation_timestamp_ms, document.ttl_ms());

  // Update ground truth first
  // TODO(b/144458732): Implement a more robust version of TC_ASSIGN_OR_RETURN
  // that can support error logging.
  auto offset_or =
      document_log_->WriteProto(CreateDocumentWrapper(std::move(document)));
  if (!offset_or.ok()) {
    ICING_LOG(ERROR) << offset_or.status().error_message()
                     << "Failed to write document";
    return offset_or.status();
  }
  int64_t file_offset = std::move(offset_or).ValueOrDie();

  // Get existing document id
  auto old_document_id_or = GetDocumentId(name_space, uri);
  if (!old_document_id_or.ok() &&
      !absl_ports::IsNotFound(old_document_id_or.status())) {
    return absl_ports::InternalError("Failed to read from key mapper");
  }

  // Creates a new document id, updates key mapper and document_id mapper
  DocumentId new_document_id = document_id_mapper_->num_elements();
  if (!IsDocumentIdValid(new_document_id)) {
    return absl_ports::ResourceExhaustedError(
        "Exceeded maximum number of documents. Try calling Optimize to reclaim "
        "some space.");
  }

  // Update namespace maps
  ICING_ASSIGN_OR_RETURN(
      NamespaceId namespace_id,
      namespace_mapper_->GetOrPut(name_space, namespace_mapper_->num_keys()));

  // Updates key mapper and document_id mapper
  ICING_RETURN_IF_ERROR(document_key_mapper_->Put(
      MakeFingerprint(namespace_id, name_space, uri), new_document_id));
  ICING_RETURN_IF_ERROR(document_id_mapper_->Set(new_document_id, file_offset));

  // Update corpus maps
  ICING_ASSIGN_OR_RETURN(CorpusId corpusId,
                         corpus_mapper_->GetOrPut(
                             MakeFingerprint(namespace_id, name_space, schema),
                             corpus_mapper_->num_keys()));

  ICING_ASSIGN_OR_RETURN(CorpusAssociatedScoreData scoring_data,
                         GetCorpusAssociatedScoreDataToUpdate(corpusId));
  scoring_data.AddDocument(length_in_tokens);

  ICING_RETURN_IF_ERROR(
      UpdateCorpusAssociatedScoreCache(corpusId, scoring_data));

  ICING_RETURN_IF_ERROR(UpdateDocumentAssociatedScoreCache(
      new_document_id,
      DocumentAssociatedScoreData(corpusId, document_score,
                                  creation_timestamp_ms, length_in_tokens)));

  ICING_ASSIGN_OR_RETURN(SchemaTypeId schema_type_id,
                         schema_store_->GetSchemaTypeId(schema));

  ICING_RETURN_IF_ERROR(UpdateFilterCache(
      new_document_id, DocumentFilterData(namespace_id, schema_type_id,
                                          expiration_timestamp_ms)));

  if (old_document_id_or.ok()) {
    // The old document exists, copy over the usage scores and delete the old
    // document.
    DocumentId old_document_id = old_document_id_or.ValueOrDie();

    ICING_RETURN_IF_ERROR(
        usage_store_->CloneUsageScores(/*from_document_id=*/old_document_id,
                                       /*to_document_id=*/new_document_id));

    // Delete the old document. It's fine if it's not found since it might have
    // been deleted previously.
    auto delete_status =
        Delete(old_document_id, clock_.GetSystemTimeMilliseconds());
    if (!delete_status.ok() && !absl_ports::IsNotFound(delete_status)) {
      // Real error, pass it up.
      return delete_status;
    }
  }

  if (put_document_stats != nullptr) {
    put_document_stats->set_document_store_latency_ms(
        put_timer->GetElapsedMilliseconds());
  }

  return new_document_id;
}

libtextclassifier3::StatusOr<DocumentProto> DocumentStore::Get(
    const std::string_view name_space, const std::string_view uri,
    bool clear_internal_fields) const {
  // TODO(b/147231617): Make a better way to replace the error message in an
  // existing Status.
  auto document_id_or = GetDocumentId(name_space, uri);
  if (absl_ports::IsNotFound(document_id_or.status())) {
    ICING_VLOG(1) << document_id_or.status().error_message();
    return libtextclassifier3::Status(
        document_id_or.status().CanonicalCode(),
        IcingStringUtil::StringPrintf("Document (%s, %s) not found.",
                                      name_space.data(), uri.data()));
  }
  DocumentId document_id = document_id_or.ValueOrDie();

  // TODO(b/147231617): Make a better way to replace the error message in an
  // existing Status.
  auto status_or = Get(document_id);
  if (absl_ports::IsNotFound(status_or.status())) {
    ICING_LOG(ERROR) << document_id_or.status().error_message();
    return libtextclassifier3::Status(
        status_or.status().CanonicalCode(),
        IcingStringUtil::StringPrintf("Document (%s, %s) not found.",
                                      name_space.data(), uri.data()));
  }
  return status_or;
}

libtextclassifier3::StatusOr<DocumentProto> DocumentStore::Get(
    DocumentId document_id, bool clear_internal_fields) const {
  int64_t current_time_ms = clock_.GetSystemTimeMilliseconds();
  auto document_filter_data_optional_ =
      GetAliveDocumentFilterData(document_id, current_time_ms);
  if (!document_filter_data_optional_) {
    // The document doesn't exist. Let's check if the document id is invalid, we
    // will return InvalidArgumentError. Otherwise we should return NOT_FOUND
    // error.
    if (!IsDocumentIdValid(document_id)) {
      return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
          "Document id '%d' invalid.", document_id));
    }
    return absl_ports::NotFoundError(IcingStringUtil::StringPrintf(
        "Document id '%d' doesn't exist", document_id));
  }

  auto document_log_offset_or = document_id_mapper_->Get(document_id);
  if (!document_log_offset_or.ok()) {
    // Since we've just checked that our document_id is valid a few lines
    // above, there's no reason this should fail and an error should never
    // happen.
    return absl_ports::InternalError("Failed to find document offset.");
  }
  int64_t document_log_offset = *document_log_offset_or.ValueOrDie();

  // TODO(b/144458732): Implement a more robust version of TC_ASSIGN_OR_RETURN
  // that can support error logging.
  auto document_wrapper_or = document_log_->ReadProto(document_log_offset);
  if (!document_wrapper_or.ok()) {
    ICING_LOG(ERROR) << document_wrapper_or.status().error_message()
                     << "Failed to read from document log";
    return document_wrapper_or.status();
  }
  DocumentWrapper document_wrapper =
      std::move(document_wrapper_or).ValueOrDie();
  if (clear_internal_fields) {
    document_wrapper.mutable_document()->clear_internal_fields();
  }

  return std::move(*document_wrapper.mutable_document());
}

libtextclassifier3::StatusOr<DocumentId> DocumentStore::GetDocumentId(
    const std::string_view name_space, const std::string_view uri) const {
  auto namespace_id_or = namespace_mapper_->Get(name_space);
  libtextclassifier3::Status status = namespace_id_or.status();
  if (status.ok()) {
    NamespaceId namespace_id = namespace_id_or.ValueOrDie();
    auto document_id_or = document_key_mapper_->Get(
        MakeFingerprint(namespace_id, name_space, uri));
    status = document_id_or.status();
    if (status.ok()) {
      // Guaranteed to have a DocumentId now
      return document_id_or.ValueOrDie();
    }
  }
  return absl_ports::Annotate(
      status, absl_ports::StrCat(
                  "Failed to find DocumentId by key: ", name_space, ", ", uri));
}

libtextclassifier3::StatusOr<DocumentId> DocumentStore::GetDocumentId(
    const NamespaceFingerprintIdentifier& namespace_fingerprint_identifier)
    const {
  if (!namespace_id_fingerprint_) {
    return absl_ports::FailedPreconditionError(
        "Cannot lookup document id by namespace id + fingerprint without "
        "enabling it on uri_mapper");
  }

  auto document_id_or = document_key_mapper_->Get(
      namespace_fingerprint_identifier.EncodeToCString());
  if (document_id_or.ok()) {
    return document_id_or.ValueOrDie();
  }
  return absl_ports::Annotate(
      std::move(document_id_or).status(),
      "Failed to find DocumentId by namespace id + fingerprint");
}

std::vector<std::string> DocumentStore::GetAllNamespaces() const {
  std::unordered_map<NamespaceId, std::string> namespace_id_to_namespace =
      GetNamespaceIdsToNamespaces(namespace_mapper_.get());

  std::unordered_set<NamespaceId> existing_namespace_ids;
  int64_t current_time_ms = clock_.GetSystemTimeMilliseconds();
  for (DocumentId document_id = 0; document_id < filter_cache_->num_elements();
       ++document_id) {
    // filter_cache_->Get can only fail if document_id is < 0
    // or >= filter_cache_->num_elements. So, this error SHOULD NEVER HAPPEN.
    auto status_or_data = filter_cache_->Get(document_id);
    if (!status_or_data.ok()) {
      ICING_LOG(ERROR)
          << "Error while iterating over filter cache in GetAllNamespaces";
      return std::vector<std::string>();
    }
    const DocumentFilterData* data = status_or_data.ValueOrDie();

    if (GetAliveDocumentFilterData(document_id, current_time_ms)) {
      existing_namespace_ids.insert(data->namespace_id());
    }
  }

  std::vector<std::string> existing_namespaces;
  for (auto itr = existing_namespace_ids.begin();
       itr != existing_namespace_ids.end(); ++itr) {
    existing_namespaces.push_back(namespace_id_to_namespace.at(*itr));
  }
  return existing_namespaces;
}

std::optional<DocumentFilterData> DocumentStore::GetAliveDocumentFilterData(
    DocumentId document_id, int64_t current_time_ms) const {
  if (IsDeleted(document_id)) {
    return std::nullopt;
  }
  return GetNonExpiredDocumentFilterData(document_id, current_time_ms);
}

bool DocumentStore::IsDeleted(DocumentId document_id) const {
  auto file_offset_or = document_id_mapper_->Get(document_id);
  if (!file_offset_or.ok()) {
    // This would only happen if document_id is out of range of the
    // document_id_mapper, meaning we got some invalid document_id. Callers
    // should already have checked that their document_id is valid or used
    // DoesDocumentExist(WithStatus). Regardless, return true since the
    // document doesn't exist.
    return true;
  }
  int64_t file_offset = *file_offset_or.ValueOrDie();
  return file_offset == kDocDeletedFlag;
}

// Returns DocumentFilterData if the document is not expired. Otherwise,
// std::nullopt.
std::optional<DocumentFilterData>
DocumentStore::GetNonExpiredDocumentFilterData(DocumentId document_id,
                                               int64_t current_time_ms) const {
  auto filter_data_or = filter_cache_->GetCopy(document_id);
  if (!filter_data_or.ok()) {
    // This would only happen if document_id is out of range of the
    // filter_cache, meaning we got some invalid document_id. Callers should
    // already have checked that their document_id is valid or used
    // DoesDocumentExist(WithStatus). Regardless, return true since the
    // document doesn't exist.
    return std::nullopt;
  }
  DocumentFilterData document_filter_data = filter_data_or.ValueOrDie();

  // Check if it's past the expiration time
  if (current_time_ms >= document_filter_data.expiration_timestamp_ms()) {
    return std::nullopt;
  }
  return document_filter_data;
}

libtextclassifier3::Status DocumentStore::Delete(
    const std::string_view name_space, const std::string_view uri,
    int64_t current_time_ms) {
  // Try to get the DocumentId first
  auto document_id_or = GetDocumentId(name_space, uri);
  if (!document_id_or.ok()) {
    return absl_ports::Annotate(
        document_id_or.status(),
        absl_ports::StrCat("Failed to delete Document. namespace: ", name_space,
                           ", uri: ", uri));
  }
  return Delete(document_id_or.ValueOrDie(), current_time_ms);
}

libtextclassifier3::Status DocumentStore::Delete(DocumentId document_id,
                                                 int64_t current_time_ms) {
  auto document_filter_data_optional_ =
      GetAliveDocumentFilterData(document_id, current_time_ms);
  if (!document_filter_data_optional_) {
    // The document doesn't exist. We should return InvalidArgumentError if the
    // document id is invalid. Otherwise we should return NOT_FOUND error.
    if (!IsDocumentIdValid(document_id)) {
      return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
          "Document id '%d' invalid.", document_id));
    }
    return absl_ports::NotFoundError(IcingStringUtil::StringPrintf(
        "Document id '%d' doesn't exist", document_id));
  }

  auto document_log_offset_or = document_id_mapper_->Get(document_id);
  if (!document_log_offset_or.ok()) {
    return absl_ports::InternalError("Failed to find document offset.");
  }
  int64_t document_log_offset = *document_log_offset_or.ValueOrDie();

  // Erases document proto.
  ICING_RETURN_IF_ERROR(document_log_->EraseProto(document_log_offset));
  return ClearDerivedData(document_id);
}

libtextclassifier3::StatusOr<NamespaceId> DocumentStore::GetNamespaceId(
    std::string_view name_space) const {
  return namespace_mapper_->Get(name_space);
}

libtextclassifier3::StatusOr<CorpusId> DocumentStore::GetCorpusId(
    const std::string_view name_space, const std::string_view schema) const {
  ICING_ASSIGN_OR_RETURN(NamespaceId namespace_id,
                         namespace_mapper_->Get(name_space));
  return corpus_mapper_->Get(MakeFingerprint(namespace_id, name_space, schema));
}

libtextclassifier3::StatusOr<int32_t> DocumentStore::GetResultGroupingEntryId(
    ResultSpecProto::ResultGroupingType result_group_type,
    const std::string_view name_space, const std::string_view schema) const {
  auto namespace_id = GetNamespaceId(name_space);
  auto schema_type_id = schema_store_->GetSchemaTypeId(schema);
  switch (result_group_type) {
    case ResultSpecProto::NONE:
      return absl_ports::InvalidArgumentError(
          "Cannot group by ResultSpecProto::NONE");
    case ResultSpecProto::SCHEMA_TYPE:
      if (schema_type_id.ok()) {
        return schema_type_id.ValueOrDie();
      }
      break;
    case ResultSpecProto::NAMESPACE:
      if (namespace_id.ok()) {
        return namespace_id.ValueOrDie();
      }
      break;
    case ResultSpecProto::NAMESPACE_AND_SCHEMA_TYPE:
      if (namespace_id.ok() && schema_type_id.ok()) {
        // TODO(b/258715421): Temporary workaround to get a
        //                    ResultGroupingEntryId given the Namespace string
        //                    and Schema string.
        return namespace_id.ValueOrDie() << 16 | schema_type_id.ValueOrDie();
      }
      break;
  }
  return absl_ports::NotFoundError("Cannot generate ResultGrouping Entry Id");
}

libtextclassifier3::StatusOr<int32_t> DocumentStore::GetResultGroupingEntryId(
    ResultSpecProto::ResultGroupingType result_group_type,
    const NamespaceId namespace_id, const SchemaTypeId schema_type_id) const {
  switch (result_group_type) {
    case ResultSpecProto::NONE:
      return absl_ports::InvalidArgumentError(
          "Cannot group by ResultSpecProto::NONE");
    case ResultSpecProto::SCHEMA_TYPE:
      return schema_type_id;
    case ResultSpecProto::NAMESPACE:
      return namespace_id;
    case ResultSpecProto::NAMESPACE_AND_SCHEMA_TYPE:
      // TODO(b/258715421): Temporary workaround to get a ResultGroupingEntryId
      //                    given the Namespace Id and SchemaType Id.
      return namespace_id << 16 | schema_type_id;
  }
  return absl_ports::NotFoundError("Cannot generate ResultGrouping Entry Id");
}

libtextclassifier3::StatusOr<DocumentAssociatedScoreData>
DocumentStore::GetDocumentAssociatedScoreData(DocumentId document_id) const {
  auto score_data_or = score_cache_->GetCopy(document_id);
  if (!score_data_or.ok()) {
    ICING_LOG(ERROR) << " while trying to access DocumentId " << document_id
                     << " from score_cache_";
    return absl_ports::NotFoundError(
        std::move(score_data_or).status().error_message());
  }

  DocumentAssociatedScoreData document_associated_score_data =
      std::move(score_data_or).ValueOrDie();
  return document_associated_score_data;
}

libtextclassifier3::StatusOr<CorpusAssociatedScoreData>
DocumentStore::GetCorpusAssociatedScoreData(CorpusId corpus_id) const {
  auto score_data_or = corpus_score_cache_->GetCopy(corpus_id);
  if (!score_data_or.ok()) {
    return score_data_or.status();
  }

  CorpusAssociatedScoreData corpus_associated_score_data =
      std::move(score_data_or).ValueOrDie();
  return corpus_associated_score_data;
}

libtextclassifier3::StatusOr<CorpusAssociatedScoreData>
DocumentStore::GetCorpusAssociatedScoreDataToUpdate(CorpusId corpus_id) const {
  auto corpus_scoring_data_or = GetCorpusAssociatedScoreData(corpus_id);
  if (corpus_scoring_data_or.ok()) {
    return std::move(corpus_scoring_data_or).ValueOrDie();
  }
  CorpusAssociatedScoreData scoringData;
  // OUT_OF_RANGE is the StatusCode returned when a corpus id is added to
  // corpus_score_cache_ for the first time.
  if (corpus_scoring_data_or.status().CanonicalCode() ==
      libtextclassifier3::StatusCode::OUT_OF_RANGE) {
    return scoringData;
  }
  return corpus_scoring_data_or.status();
}

// TODO(b/273826815): Decide on and adopt a consistent pattern for handling
// NOT_FOUND 'errors' returned by our internal classes.
std::optional<UsageStore::UsageScores> DocumentStore::GetUsageScores(
    DocumentId document_id, int64_t current_time_ms) const {
  std::optional<DocumentFilterData> opt =
      GetAliveDocumentFilterData(document_id, current_time_ms);
  if (!opt) {
    return std::nullopt;
  }
  if (document_id >= usage_store_->num_elements()) {
    return std::nullopt;
  }
  auto usage_scores_or = usage_store_->GetUsageScores(document_id);
  if (!usage_scores_or.ok()) {
    ICING_LOG(ERROR) << "Error retrieving usage for " << document_id << ": "
                     << usage_scores_or.status().error_message();
    return std::nullopt;
  }
  return std::move(usage_scores_or).ValueOrDie();
}

libtextclassifier3::Status DocumentStore::ReportUsage(
    const UsageReport& usage_report) {
  ICING_ASSIGN_OR_RETURN(DocumentId document_id,
                         GetDocumentId(usage_report.document_namespace(),
                                       usage_report.document_uri()));
  // We can use the internal version here because we got our document_id from
  // our internal data structures. We would have thrown some error if the
  // namespace and/or uri were incorrect.
  int64_t current_time_ms = clock_.GetSystemTimeMilliseconds();
  if (!GetAliveDocumentFilterData(document_id, current_time_ms)) {
    // Document was probably deleted or expired.
    return absl_ports::NotFoundError(absl_ports::StrCat(
        "Couldn't report usage on a nonexistent document: (namespace: '",
        usage_report.document_namespace(), "', uri: '",
        usage_report.document_uri(), "')"));
  }

  return usage_store_->AddUsageReport(usage_report, document_id);
}

DocumentStore::DeleteByGroupResult DocumentStore::DeleteByNamespace(
    std::string_view name_space) {
  DeleteByGroupResult result;
  auto namespace_id_or = namespace_mapper_->Get(name_space);
  if (!namespace_id_or.ok()) {
    result.status = absl_ports::Annotate(
        namespace_id_or.status(),
        absl_ports::StrCat("Failed to find namespace: ", name_space));
    return result;
  }
  NamespaceId namespace_id = namespace_id_or.ValueOrDie();
  auto num_deleted_or = BatchDelete(namespace_id, kInvalidSchemaTypeId);
  if (!num_deleted_or.ok()) {
    result.status = std::move(num_deleted_or).status();
    return result;
  }

  result.num_docs_deleted = num_deleted_or.ValueOrDie();
  if (result.num_docs_deleted <= 0) {
    // Treat the fact that no existing documents had this namespace to be the
    // same as this namespace not existing at all.
    result.status = absl_ports::NotFoundError(
        absl_ports::StrCat("Namespace '", name_space, "' doesn't exist"));
    return result;
  }

  return result;
}

DocumentStore::DeleteByGroupResult DocumentStore::DeleteBySchemaType(
    std::string_view schema_type) {
  DeleteByGroupResult result;
  auto schema_type_id_or = schema_store_->GetSchemaTypeId(schema_type);
  if (!schema_type_id_or.ok()) {
    result.status = absl_ports::Annotate(
        schema_type_id_or.status(),
        absl_ports::StrCat("Failed to find schema type. schema_type: ",
                           schema_type));
    return result;
  }
  SchemaTypeId schema_type_id = schema_type_id_or.ValueOrDie();
  auto num_deleted_or = BatchDelete(kInvalidNamespaceId, schema_type_id);
  if (!num_deleted_or.ok()) {
    result.status = std::move(num_deleted_or).status();
    return result;
  }

  result.num_docs_deleted = num_deleted_or.ValueOrDie();
  if (result.num_docs_deleted <= 0) {
    result.status = absl_ports::NotFoundError(absl_ports::StrCat(
        "No documents found with schema type '", schema_type, "'"));
    return result;
  }

  return result;
}

libtextclassifier3::StatusOr<int> DocumentStore::BatchDelete(
    NamespaceId namespace_id, SchemaTypeId schema_type_id) {
  // Tracks if there were any existing documents with this namespace that we
  // will mark as deleted.
  int num_updated_documents = 0;

  // Traverse FilterCache and delete all docs that match namespace_id and
  // schema_type_id.
  int64_t current_time_ms = clock_.GetSystemTimeMilliseconds();
  for (DocumentId document_id = 0; document_id < filter_cache_->num_elements();
       ++document_id) {
    // filter_cache_->Get can only fail if document_id is < 0
    // or >= filter_cache_->num_elements. So, this error SHOULD NEVER HAPPEN.
    ICING_ASSIGN_OR_RETURN(const DocumentFilterData* data,
                           filter_cache_->Get(document_id));

    // Check namespace only when the input namespace id is valid.
    if (namespace_id != kInvalidNamespaceId &&
        (data->namespace_id() == kInvalidNamespaceId ||
         data->namespace_id() != namespace_id)) {
      // The document has already been hard-deleted or isn't from the desired
      // namespace.
      continue;
    }

    // Check schema type only when the input schema type id is valid.
    if (schema_type_id != kInvalidSchemaTypeId &&
        (data->schema_type_id() == kInvalidSchemaTypeId ||
         data->schema_type_id() != schema_type_id)) {
      // The document has already been hard-deleted or doesn't have the
      // desired schema type.
      continue;
    }

    // The document has the desired namespace and schema type, it either
    // exists or has expired.
    libtextclassifier3::Status delete_status =
        Delete(document_id, current_time_ms);
    if (absl_ports::IsNotFound(delete_status)) {
      continue;
    } else if (!delete_status.ok()) {
      // Real error, pass up.
      return delete_status;
    }
    ++num_updated_documents;
  }

  return num_updated_documents;
}

libtextclassifier3::Status DocumentStore::PersistToDisk(
    PersistType::Code persist_type) {
  if (persist_type == PersistType::LITE) {
    // only persist the document log.
    return document_log_->PersistToDisk();
  }
  ICING_RETURN_IF_ERROR(document_log_->PersistToDisk());
  ICING_RETURN_IF_ERROR(document_key_mapper_->PersistToDisk());
  ICING_RETURN_IF_ERROR(document_id_mapper_->PersistToDisk());
  ICING_RETURN_IF_ERROR(score_cache_->PersistToDisk());
  ICING_RETURN_IF_ERROR(filter_cache_->PersistToDisk());
  ICING_RETURN_IF_ERROR(namespace_mapper_->PersistToDisk());
  ICING_RETURN_IF_ERROR(usage_store_->PersistToDisk());
  ICING_RETURN_IF_ERROR(corpus_mapper_->PersistToDisk());
  ICING_RETURN_IF_ERROR(corpus_score_cache_->PersistToDisk());

  // Update the combined checksum and write to header file.
  ICING_ASSIGN_OR_RETURN(Crc32 checksum, ComputeChecksum());
  ICING_RETURN_IF_ERROR(UpdateHeader(checksum));

  return libtextclassifier3::Status::OK;
}

int64_t GetValueOrDefault(const libtextclassifier3::StatusOr<int64_t>& value_or,
                          int64_t default_value) {
  return (value_or.ok()) ? value_or.ValueOrDie() : default_value;
}

DocumentStorageInfoProto DocumentStore::GetMemberStorageInfo() const {
  DocumentStorageInfoProto storage_info;
  storage_info.set_document_log_size(
      GetValueOrDefault(document_log_->GetDiskUsage(), -1));
  storage_info.set_key_mapper_size(
      GetValueOrDefault(document_key_mapper_->GetDiskUsage(), -1));
  storage_info.set_document_id_mapper_size(
      GetValueOrDefault(document_id_mapper_->GetDiskUsage(), -1));
  storage_info.set_score_cache_size(
      GetValueOrDefault(score_cache_->GetDiskUsage(), -1));
  storage_info.set_filter_cache_size(
      GetValueOrDefault(filter_cache_->GetDiskUsage(), -1));
  storage_info.set_namespace_id_mapper_size(
      GetValueOrDefault(namespace_mapper_->GetDiskUsage(), -1));
  storage_info.set_corpus_mapper_size(
      GetValueOrDefault(corpus_mapper_->GetDiskUsage(), -1));
  storage_info.set_corpus_score_cache_size(
      GetValueOrDefault(corpus_score_cache_->GetDiskUsage(), -1));
  return storage_info;
}

DocumentStorageInfoProto DocumentStore::CalculateDocumentStatusCounts(
    DocumentStorageInfoProto storage_info) const {
  int total_num_alive = 0;
  int total_num_expired = 0;
  int total_num_deleted = 0;
  std::unordered_map<NamespaceId, std::string> namespace_id_to_namespace =
      GetNamespaceIdsToNamespaces(namespace_mapper_.get());
  std::unordered_map<std::string, NamespaceStorageInfoProto>
      namespace_to_storage_info;

  int64_t current_time_ms = clock_.GetSystemTimeMilliseconds();
  for (DocumentId document_id = 0;
       document_id < document_id_mapper_->num_elements(); ++document_id) {
    // Check if it's deleted first.
    if (IsDeleted(document_id)) {
      // We don't have the namespace id of hard deleted documents anymore, so
      // we can't add to our namespace storage info.
      ++total_num_deleted;
      continue;
    }

    // At this point, the document is either alive or expired, we can get
    // namespace info for it.
    auto filter_data_or = filter_cache_->Get(document_id);
    if (!filter_data_or.ok()) {
      ICING_VLOG(1) << "Error trying to get filter data for document store "
                       "storage info counts.";
      continue;
    }
    const DocumentFilterData* filter_data = filter_data_or.ValueOrDie();
    auto itr = namespace_id_to_namespace.find(filter_data->namespace_id());
    if (itr == namespace_id_to_namespace.end()) {
      ICING_VLOG(1) << "Error trying to find namespace for document store "
                       "storage info counts.";
      continue;
    }
    const std::string& name_space = itr->second;

    // Always set the namespace, if the NamespaceStorageInfoProto didn't exist
    // before, we'll get back a default instance of it.
    NamespaceStorageInfoProto& namespace_storage_info =
        namespace_to_storage_info[name_space];
    namespace_storage_info.set_namespace_(name_space);

    // Get usage scores
    auto usage_scores_or = usage_store_->GetUsageScores(document_id);
    if (!usage_scores_or.ok()) {
      ICING_VLOG(1) << "Error trying to get usage scores for document store "
                       "storage info counts.";
      continue;
    }
    UsageStore::UsageScores usage_scores = usage_scores_or.ValueOrDie();

    // Update our stats
    if (!GetNonExpiredDocumentFilterData(document_id, current_time_ms)) {
      ++total_num_expired;
      namespace_storage_info.set_num_expired_documents(
          namespace_storage_info.num_expired_documents() + 1);
      if (usage_scores.usage_type1_count > 0) {
        namespace_storage_info.set_num_expired_documents_usage_type1(
            namespace_storage_info.num_expired_documents_usage_type1() + 1);
      }
      if (usage_scores.usage_type2_count > 0) {
        namespace_storage_info.set_num_expired_documents_usage_type2(
            namespace_storage_info.num_expired_documents_usage_type2() + 1);
      }
      if (usage_scores.usage_type3_count > 0) {
        namespace_storage_info.set_num_expired_documents_usage_type3(
            namespace_storage_info.num_expired_documents_usage_type3() + 1);
      }
    } else {
      ++total_num_alive;
      namespace_storage_info.set_num_alive_documents(
          namespace_storage_info.num_alive_documents() + 1);
      if (usage_scores.usage_type1_count > 0) {
        namespace_storage_info.set_num_alive_documents_usage_type1(
            namespace_storage_info.num_alive_documents_usage_type1() + 1);
      }
      if (usage_scores.usage_type2_count > 0) {
        namespace_storage_info.set_num_alive_documents_usage_type2(
            namespace_storage_info.num_alive_documents_usage_type2() + 1);
      }
      if (usage_scores.usage_type3_count > 0) {
        namespace_storage_info.set_num_alive_documents_usage_type3(
            namespace_storage_info.num_alive_documents_usage_type3() + 1);
      }
    }
  }

  for (auto& itr : namespace_to_storage_info) {
    storage_info.mutable_namespace_storage_info()->Add(std::move(itr.second));
  }
  storage_info.set_num_alive_documents(total_num_alive);
  storage_info.set_num_deleted_documents(total_num_deleted);
  storage_info.set_num_expired_documents(total_num_expired);
  return storage_info;
}

DocumentStorageInfoProto DocumentStore::GetStorageInfo() const {
  DocumentStorageInfoProto storage_info = GetMemberStorageInfo();
  int64_t directory_size = filesystem_->GetDiskUsage(base_dir_.c_str());
  if (directory_size != Filesystem::kBadFileSize) {
    storage_info.set_document_store_size(directory_size);
  } else {
    storage_info.set_document_store_size(-1);
  }
  storage_info.set_num_namespaces(namespace_mapper_->num_keys());
  return CalculateDocumentStatusCounts(std::move(storage_info));
}

libtextclassifier3::Status DocumentStore::UpdateSchemaStore(
    const SchemaStore* schema_store) {
  // Update all references to the SchemaStore
  schema_store_ = schema_store;
  document_validator_.UpdateSchemaStore(schema_store);

  int size = document_id_mapper_->num_elements();
  int64_t current_time_ms = clock_.GetSystemTimeMilliseconds();
  for (DocumentId document_id = 0; document_id < size; document_id++) {
    auto document_or = Get(document_id);
    if (absl_ports::IsNotFound(document_or.status())) {
      // Skip nonexistent documents
      continue;
    } else if (!document_or.ok()) {
      // Real error, pass up
      return absl_ports::Annotate(
          document_or.status(),
          IcingStringUtil::StringPrintf(
              "Failed to retrieve Document for DocumentId %d", document_id));
    }

    // Guaranteed to have a document now.
    DocumentProto document = document_or.ValueOrDie();

    // Revalidate that this document is still compatible
    if (document_validator_.Validate(document).ok()) {
      // Update the SchemaTypeId for this entry
      ICING_ASSIGN_OR_RETURN(SchemaTypeId schema_type_id,
                             schema_store_->GetSchemaTypeId(document.schema()));
      ICING_ASSIGN_OR_RETURN(
          typename FileBackedVector<DocumentFilterData>::MutableView
              doc_filter_data_view,
          filter_cache_->GetMutable(document_id));
      doc_filter_data_view.Get().set_schema_type_id(schema_type_id);
    } else {
      // Document is no longer valid with the new SchemaStore. Mark as
      // deleted
      auto delete_status =
          Delete(document.namespace_(), document.uri(), current_time_ms);
      if (!delete_status.ok() && !absl_ports::IsNotFound(delete_status)) {
        // Real error, pass up
        return delete_status;
      }
    }
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status DocumentStore::OptimizedUpdateSchemaStore(
    const SchemaStore* schema_store,
    const SchemaStore::SetSchemaResult& set_schema_result) {
  if (!set_schema_result.success) {
    // No new schema was set, no work to be done
    return libtextclassifier3::Status::OK;
  }

  // Update all references to the SchemaStore
  schema_store_ = schema_store;
  document_validator_.UpdateSchemaStore(schema_store);

  int size = document_id_mapper_->num_elements();
  int64_t current_time_ms = clock_.GetSystemTimeMilliseconds();
  for (DocumentId document_id = 0; document_id < size; document_id++) {
    if (!GetAliveDocumentFilterData(document_id, current_time_ms)) {
      // Skip nonexistent documents
      continue;
    }

    // Guaranteed that the document exists now.
    ICING_ASSIGN_OR_RETURN(const DocumentFilterData* filter_data,
                           filter_cache_->Get(document_id));

    bool delete_document = set_schema_result.schema_types_deleted_by_id.count(
                               filter_data->schema_type_id()) != 0;

    // Check if we need to update the FilterCache entry for this document. It
    // may have been assigned a different SchemaTypeId in the new SchemaStore.
    bool update_filter_cache =
        set_schema_result.old_schema_type_ids_changed.count(
            filter_data->schema_type_id()) != 0;

    // Check if we need to revalidate this document if the type is now
    // incompatible
    bool revalidate_document =
        set_schema_result.schema_types_incompatible_by_id.count(
            filter_data->schema_type_id()) != 0;

    if (update_filter_cache || revalidate_document) {
      ICING_ASSIGN_OR_RETURN(DocumentProto document, Get(document_id));

      if (update_filter_cache) {
        ICING_ASSIGN_OR_RETURN(
            SchemaTypeId schema_type_id,
            schema_store_->GetSchemaTypeId(document.schema()));
        ICING_ASSIGN_OR_RETURN(
            typename FileBackedVector<DocumentFilterData>::MutableView
                doc_filter_data_view,
            filter_cache_->GetMutable(document_id));
        doc_filter_data_view.Get().set_schema_type_id(schema_type_id);
      }
      if (revalidate_document) {
        delete_document = !document_validator_.Validate(document).ok();
      }
    }

    if (delete_document) {
      // Document is no longer valid with the new SchemaStore. Mark as deleted
      auto delete_status = Delete(document_id, current_time_ms);
      if (!delete_status.ok() && !absl_ports::IsNotFound(delete_status)) {
        // Real error, pass up
        return delete_status;
      }
    }
  }

  return libtextclassifier3::Status::OK;
}

// TODO(b/121227117): Implement Optimize()
libtextclassifier3::Status DocumentStore::Optimize() {
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<DocumentStore::OptimizeResult>
DocumentStore::OptimizeInto(const std::string& new_directory,
                            const LanguageSegmenter* lang_segmenter,
                            OptimizeStatsProto* stats) const {
  // Validates directory
  if (new_directory == base_dir_) {
    return absl_ports::InvalidArgumentError(
        "New directory is the same as the current one.");
  }

  ICING_ASSIGN_OR_RETURN(
      auto doc_store_create_result,
      DocumentStore::Create(filesystem_, new_directory, &clock_, schema_store_,
                            /*force_recovery_and_revalidate_documents=*/false,
                            namespace_id_fingerprint_, pre_mapping_fbv_,
                            use_persistent_hash_map_, compression_level_,
                            /*initialize_stats=*/nullptr));
  std::unique_ptr<DocumentStore> new_doc_store =
      std::move(doc_store_create_result.document_store);

  // Writes all valid docs into new document store (new directory)
  int document_cnt = document_id_mapper_->num_elements();
  int num_deleted_documents = 0;
  int num_expired_documents = 0;
  UsageStore::UsageScores default_usage;

  OptimizeResult result;
  result.document_id_old_to_new.resize(document_cnt, kInvalidDocumentId);
  int64_t current_time_ms = clock_.GetSystemTimeMilliseconds();
  for (DocumentId document_id = 0; document_id < document_cnt; document_id++) {
    auto document_or = Get(document_id, /*clear_internal_fields=*/false);
    if (absl_ports::IsNotFound(document_or.status())) {
      if (IsDeleted(document_id)) {
        ++num_deleted_documents;
      } else if (!GetNonExpiredDocumentFilterData(document_id,
                                                  current_time_ms)) {
        ++num_expired_documents;
      }
      continue;
    } else if (!document_or.ok()) {
      // Real error, pass up
      return absl_ports::Annotate(
          document_or.status(),
          IcingStringUtil::StringPrintf(
              "Failed to retrieve Document for DocumentId %d", document_id));
    }

    // Guaranteed to have a document now.
    DocumentProto document_to_keep = std::move(document_or).ValueOrDie();

    libtextclassifier3::StatusOr<DocumentId> new_document_id_or;
    if (document_to_keep.internal_fields().length_in_tokens() == 0) {
      auto tokenized_document_or = TokenizedDocument::Create(
          schema_store_, lang_segmenter, document_to_keep);
      if (!tokenized_document_or.ok()) {
        return absl_ports::Annotate(
            tokenized_document_or.status(),
            IcingStringUtil::StringPrintf(
                "Failed to tokenize Document for DocumentId %d", document_id));
      }
      TokenizedDocument tokenized_document(
          std::move(tokenized_document_or).ValueOrDie());
      new_document_id_or = new_doc_store->Put(
          std::move(document_to_keep), tokenized_document.num_string_tokens());
    } else {
      // TODO(b/144458732): Implement a more robust version of
      // TC_ASSIGN_OR_RETURN that can support error logging.
      new_document_id_or =
          new_doc_store->InternalPut(std::move(document_to_keep));
    }
    if (!new_document_id_or.ok()) {
      ICING_LOG(ERROR) << new_document_id_or.status().error_message()
                       << "Failed to write into new document store";
      return new_document_id_or.status();
    }

    result.document_id_old_to_new[document_id] =
        new_document_id_or.ValueOrDie();

    // Copy over usage scores.
    ICING_ASSIGN_OR_RETURN(UsageStore::UsageScores usage_scores,
                           usage_store_->GetUsageScores(document_id));
    if (!(usage_scores == default_usage)) {
      // If the usage scores for this document are the default (no usage), then
      // don't bother setting it. No need to possibly allocate storage if
      // there's nothing interesting to store.
      DocumentId new_document_id = new_document_id_or.ValueOrDie();
      ICING_RETURN_IF_ERROR(
          new_doc_store->SetUsageScores(new_document_id, usage_scores));
    }
  }

  // Construct namespace_id_old_to_new
  int namespace_cnt = namespace_mapper_->num_keys();
  std::unordered_map<NamespaceId, std::string> old_namespaces =
      GetNamespaceIdsToNamespaces(namespace_mapper_.get());
  if (namespace_cnt != old_namespaces.size()) {
    // This really shouldn't happen. If it really happens, then:
    // - It won't block DocumentStore optimization, so don't return error here.
    // - Instead, write a warning log here and hint the caller to rebuild index.
    ICING_LOG(WARNING) << "Unexpected old namespace count " << namespace_cnt
                       << " vs " << old_namespaces.size();
    result.should_rebuild_index = true;
  } else {
    result.namespace_id_old_to_new.resize(namespace_cnt, kInvalidNamespaceId);
    for (const auto& [old_namespace_id, ns] : old_namespaces) {
      if (old_namespace_id >= result.namespace_id_old_to_new.size()) {
        // This really shouldn't happen. If it really happens, then:
        // - It won't block DocumentStore optimization, so don't return error
        //   here.
        // - Instead, write a warning log here and hint the caller to rebuild
        //   index.
        ICING_LOG(WARNING) << "Found unexpected namespace id "
                           << old_namespace_id << ". Should be in range 0 to "
                           << result.namespace_id_old_to_new.size()
                           << " (exclusive).";
        result.namespace_id_old_to_new.clear();
        result.should_rebuild_index = true;
        break;
      }

      auto new_namespace_id_or = new_doc_store->namespace_mapper_->Get(ns);
      if (!new_namespace_id_or.ok()) {
        if (absl_ports::IsNotFound(new_namespace_id_or.status())) {
          continue;
        }
        // Real error, return it.
        return std::move(new_namespace_id_or).status();
      }

      NamespaceId new_namespace_id = new_namespace_id_or.ValueOrDie();
      // Safe to use bracket to assign given that we've checked the range above.
      result.namespace_id_old_to_new[old_namespace_id] = new_namespace_id;
    }
  }

  if (stats != nullptr) {
    stats->set_num_original_documents(document_cnt);
    stats->set_num_deleted_documents(num_deleted_documents);
    stats->set_num_expired_documents(num_expired_documents);
    stats->set_num_original_namespaces(namespace_cnt);
    stats->set_num_deleted_namespaces(
        namespace_cnt - new_doc_store->namespace_mapper_->num_keys());
  }
  ICING_RETURN_IF_ERROR(new_doc_store->PersistToDisk(PersistType::FULL));
  return result;
}

libtextclassifier3::StatusOr<DocumentStore::OptimizeInfo>
DocumentStore::GetOptimizeInfo() const {
  OptimizeInfo optimize_info;

  // Figure out our ratio of optimizable/total docs.
  int32_t num_documents = document_id_mapper_->num_elements();
  int64_t current_time_ms = clock_.GetSystemTimeMilliseconds();
  for (DocumentId document_id = kMinDocumentId; document_id < num_documents;
       ++document_id) {
    if (!GetAliveDocumentFilterData(document_id, current_time_ms)) {
      ++optimize_info.optimizable_docs;
    }

    ++optimize_info.total_docs;
  }

  if (optimize_info.total_docs == 0) {
    // Can exit early since there's nothing to calculate.
    return optimize_info;
  }

  // Get the total element size.
  //
  // We use file size instead of disk usage here because the files are not
  // sparse, so it's more accurate. Disk usage rounds up to the nearest block
  // size.
  ICING_ASSIGN_OR_RETURN(const int64_t document_log_file_size,
                         document_log_->GetElementsFileSize());
  ICING_ASSIGN_OR_RETURN(const int64_t document_id_mapper_file_size,
                         document_id_mapper_->GetElementsFileSize());
  ICING_ASSIGN_OR_RETURN(const int64_t score_cache_file_size,
                         score_cache_->GetElementsFileSize());
  ICING_ASSIGN_OR_RETURN(const int64_t filter_cache_file_size,
                         filter_cache_->GetElementsFileSize());
  ICING_ASSIGN_OR_RETURN(const int64_t corpus_score_cache_file_size,
                         corpus_score_cache_->GetElementsFileSize());

  // Usage store might be sparse, but we'll still use file size for more
  // accurate counting.
  ICING_ASSIGN_OR_RETURN(const int64_t usage_store_file_size,
                         usage_store_->GetElementsFileSize());

  // We use a combined disk usage and file size for the DynamicTrieKeyMapper
  // because it's backed by a trie, which has some sparse property bitmaps.
  ICING_ASSIGN_OR_RETURN(const int64_t document_key_mapper_size,
                         document_key_mapper_->GetElementsSize());

  // We don't include the namespace_mapper or the corpus_mapper because it's
  // not clear if we could recover any space even if Optimize were called.
  // Deleting 100s of documents could still leave a few documents of a
  // namespace, and then there would be no change.

  int64_t total_size = document_log_file_size + document_key_mapper_size +
                       document_id_mapper_file_size + score_cache_file_size +
                       filter_cache_file_size + corpus_score_cache_file_size +
                       usage_store_file_size;

  optimize_info.estimated_optimizable_bytes =
      total_size * optimize_info.optimizable_docs / optimize_info.total_docs;
  return optimize_info;
}

libtextclassifier3::Status DocumentStore::UpdateCorpusAssociatedScoreCache(
    CorpusId corpus_id, const CorpusAssociatedScoreData& score_data) {
  return corpus_score_cache_->Set(corpus_id, score_data);
}

libtextclassifier3::Status DocumentStore::UpdateDocumentAssociatedScoreCache(
    DocumentId document_id, const DocumentAssociatedScoreData& score_data) {
  return score_cache_->Set(document_id, score_data);
}

libtextclassifier3::Status DocumentStore::UpdateFilterCache(
    DocumentId document_id, const DocumentFilterData& filter_data) {
  return filter_cache_->Set(document_id, filter_data);
}

libtextclassifier3::Status DocumentStore::ClearDerivedData(
    DocumentId document_id) {
  // We intentionally leave the data in key_mapper_ because locating that data
  // requires fetching namespace and uri. Leaving data in key_mapper_ should
  // be fine because the data is hashed.

  ICING_RETURN_IF_ERROR(document_id_mapper_->Set(document_id, kDocDeletedFlag));

  // Resets the score cache entry
  ICING_RETURN_IF_ERROR(UpdateDocumentAssociatedScoreCache(
      document_id, DocumentAssociatedScoreData(kInvalidCorpusId,
                                               /*document_score=*/-1,
                                               /*creation_timestamp_ms=*/-1,
                                               /*length_in_tokens=*/0)));

  // Resets the filter cache entry
  ICING_RETURN_IF_ERROR(UpdateFilterCache(
      document_id, DocumentFilterData(kInvalidNamespaceId, kInvalidSchemaTypeId,
                                      /*expiration_timestamp_ms=*/-1)));

  // Clears the usage scores.
  return usage_store_->DeleteUsageScores(document_id);
}

libtextclassifier3::Status DocumentStore::SetUsageScores(
    DocumentId document_id, const UsageStore::UsageScores& usage_scores) {
  return usage_store_->SetUsageScores(document_id, usage_scores);
}

libtextclassifier3::StatusOr<
    google::protobuf::RepeatedPtrField<DocumentDebugInfoProto::CorpusInfo>>
DocumentStore::CollectCorpusInfo() const {
  google::protobuf::RepeatedPtrField<DocumentDebugInfoProto::CorpusInfo> corpus_info;
  libtextclassifier3::StatusOr<const SchemaProto*> schema_proto_or =
      schema_store_->GetSchema();
  if (!schema_proto_or.ok()) {
    return corpus_info;
  }
  // Maps from CorpusId to the corresponding protocol buffer in the result.
  std::unordered_map<CorpusId, DocumentDebugInfoProto::CorpusInfo*> info_map;
  std::unordered_map<NamespaceId, std::string> namespace_id_to_namespace =
      GetNamespaceIdsToNamespaces(namespace_mapper_.get());
  const SchemaProto* schema_proto = schema_proto_or.ValueOrDie();
  int64_t current_time_ms = clock_.GetSystemTimeMilliseconds();
  for (DocumentId document_id = 0; document_id < filter_cache_->num_elements();
       ++document_id) {
    if (!GetAliveDocumentFilterData(document_id, current_time_ms)) {
      continue;
    }
    ICING_ASSIGN_OR_RETURN(const DocumentFilterData* filter_data,
                           filter_cache_->Get(document_id));
    ICING_ASSIGN_OR_RETURN(const DocumentAssociatedScoreData* score_data,
                           score_cache_->Get(document_id));
    const std::string& name_space =
        namespace_id_to_namespace[filter_data->namespace_id()];
    const std::string& schema =
        schema_proto->types()[filter_data->schema_type_id()].schema_type();
    auto iter = info_map.find(score_data->corpus_id());
    if (iter == info_map.end()) {
      DocumentDebugInfoProto::CorpusInfo* entry = corpus_info.Add();
      entry->set_namespace_(name_space);
      entry->set_schema(schema);
      iter = info_map.insert({score_data->corpus_id(), entry}).first;
    }
    iter->second->set_total_documents(iter->second->total_documents() + 1);
    iter->second->set_total_token(iter->second->total_token() +
                                  score_data->length_in_tokens());
  }
  return corpus_info;
}

libtextclassifier3::StatusOr<DocumentDebugInfoProto>
DocumentStore::GetDebugInfo(int verbosity) const {
  DocumentDebugInfoProto debug_info;
  *debug_info.mutable_document_storage_info() = GetStorageInfo();
  ICING_ASSIGN_OR_RETURN(Crc32 crc, ComputeChecksum());
  debug_info.set_crc(crc.Get());
  if (verbosity > 0) {
    ICING_ASSIGN_OR_RETURN(
        google::protobuf::RepeatedPtrField<DocumentDebugInfoProto::CorpusInfo>
            corpus_info,
        CollectCorpusInfo());
    *debug_info.mutable_corpus_info() = std::move(corpus_info);
  }
  return debug_info;
}

}  // namespace lib
}  // namespace icing
