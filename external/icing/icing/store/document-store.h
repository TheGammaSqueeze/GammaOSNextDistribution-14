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

#ifndef ICING_STORE_DOCUMENT_STORE_H_
#define ICING_STORE_DOCUMENT_STORE_H_

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/file/file-backed-proto-log.h"
#include "icing/file/file-backed-vector.h"
#include "icing/file/filesystem.h"
#include "icing/file/portable-file-backed-proto-log.h"
#include "icing/proto/debug.pb.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/document_wrapper.pb.h"
#include "icing/proto/logging.pb.h"
#include "icing/proto/optimize.pb.h"
#include "icing/proto/persist.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/storage.pb.h"
#include "icing/proto/usage.pb.h"
#include "icing/schema/schema-store.h"
#include "icing/store/corpus-associated-scoring-data.h"
#include "icing/store/corpus-id.h"
#include "icing/store/document-associated-score-data.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/document-id.h"
#include "icing/store/key-mapper.h"
#include "icing/store/namespace-fingerprint-identifier.h"
#include "icing/store/namespace-id.h"
#include "icing/store/usage-store.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/util/clock.h"
#include "icing/util/crc32.h"
#include "icing/util/data-loss.h"
#include "icing/util/document-validator.h"
#include "icing/util/fingerprint-util.h"

namespace icing {
namespace lib {

// Provides storage interfaces for documents.
class DocumentStore {
 public:
  struct Header {
    static int32_t GetCurrentMagic(bool namespace_id_fingerprint) {
      return namespace_id_fingerprint ? kNewMagic : kOldMagic;
    }

    // Holds the magic as a quick sanity check against file corruption.
    int32_t magic;

    // Checksum of the DocumentStore's sub-component's checksums.
    uint32_t checksum;

   private:
    static constexpr int32_t kOldMagic = 0x746f7265;
    static constexpr int32_t kNewMagic = 0x1b99c8b0;
  };

  struct OptimizeInfo {
    // The estimated size in bytes of the optimizable docs. We don't track the
    // size of each document, so we estimate by taking the size of the entire
    // DocumentStore and dividing that by the total number of documents we have.
    // So we end up with an average document size.
    int64_t estimated_optimizable_bytes = 0;

    // Number of total documents the DocumentStore tracks.
    int32_t total_docs = 0;

    // Number of optimizable (deleted + expired) docs the DocumentStore tracks.
    int32_t optimizable_docs = 0;
  };

  struct DeleteByGroupResult {
    // Status representing whether or not the operation succeeded. See the
    // comments above the function that returns this result to determine what
    // possible statuses could be returned.
    libtextclassifier3::Status status;

    int num_docs_deleted = 0;
  };

  struct CreateResult {
    // A successfully initialized document store.
    std::unique_ptr<DocumentStore> document_store;

    // The data status after initializing from a previous state. Data loss can
    // happen if the file is corrupted or some previously added data was
    // unpersisted. This may be used to signal that any derived data off of the
    // document store may need to be regenerated.
    DataLoss data_loss;

    // A boolean flag indicating if derived files of the document store have
    // been regenerated or not. This is usually a signal for callers to detect
    // if any id assignment has changed (e.g. NamespaceId).
    bool derived_files_regenerated;
  };

  // Not copyable
  DocumentStore(const DocumentStore&) = delete;
  DocumentStore& operator=(const DocumentStore&) = delete;

  // Persists and updates checksum of subcomponents.
  ~DocumentStore();

  // Factory method to create, initialize, and return a DocumentStore. The base
  // directory is used to persist document store files. If document store was
  // previously initialized with this directory, it will reload the files saved
  // by the last instance.
  //
  // force_recovery_and_revalidate_documents=true will pre-emptively throw out
  // the derived files and validate each document while recreating them. This
  // can be used to indicate that the schema (and type ids) may have changed and
  // those changes might not have been applied to the document store.
  //
  // If initialize_stats is present, the fields related to DocumentStore will be
  // populated.
  //
  // Does not take any ownership, and all pointers except initialize_stats must
  // refer to valid objects that outlive the one constructed.
  //
  // TODO(cassiewang): Consider returning a status indicating that derived files
  // were regenerated. This may be helpful in logs.
  //
  // Returns:
  //   A DocumentStore::CreateResult on success
  //   FAILED_PRECONDITION on any null pointer input
  //   INTERNAL_ERROR on IO error
  static libtextclassifier3::StatusOr<DocumentStore::CreateResult> Create(
      const Filesystem* filesystem, const std::string& base_dir,
      const Clock* clock, const SchemaStore* schema_store,
      bool force_recovery_and_revalidate_documents,
      bool namespace_id_fingerprint, bool pre_mapping_fbv,
      bool use_persistent_hash_map, int32_t compression_level,
      InitializeStatsProto* initialize_stats);

  // Discards all derived data in the document store.
  //
  // Returns:
  //   OK on success or nothing to discard
  //   INTERNAL_ERROR on any I/O errors
  static libtextclassifier3::Status DiscardDerivedFiles(
      const Filesystem* filesystem, const std::string& base_dir);

  // Returns the maximum DocumentId that the DocumentStore has assigned. If
  // there has not been any DocumentIds assigned, i.e. the DocumentStore is
  // empty, then kInvalidDocumentId is returned. This does not filter out
  // DocumentIds of deleted or expired documents.
  DocumentId last_added_document_id() const {
    if (document_id_mapper_->num_elements() == 0) {
      return kInvalidDocumentId;
    }
    return document_id_mapper_->num_elements() - 1;
  }

  // Returns the number of documents. The result does not filter out DocumentIds
  // of deleted or expired documents.
  int num_documents() const { return document_id_mapper_->num_elements(); }

  // Puts the document into document store.
  //
  // If put_document_stats is present, the fields related to DocumentStore will
  // be populated.
  //
  // Returns:
  //   A newly generated document id on success
  //   RESOURCE_EXHAUSED if exceeds maximum number of allowed documents
  //   FAILED_PRECONDITION if schema hasn't been set yet
  //   NOT_FOUND if the schema_type or a property config of the document doesn't
  //     exist in schema
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<DocumentId> Put(
      const DocumentProto& document, int32_t num_tokens = 0,
      PutDocumentStatsProto* put_document_stats = nullptr);
  libtextclassifier3::StatusOr<DocumentId> Put(
      DocumentProto&& document, int32_t num_tokens = 0,
      PutDocumentStatsProto* put_document_stats = nullptr);

  // Finds and returns the document identified by the given key (namespace +
  // uri). If 'clear_internal_fields' is true, document level data that's
  // generated internally by DocumentStore is cleared.
  //
  // Returns:
  //   The document found on success
  //   NOT_FOUND if the key doesn't exist or document has been deleted
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<DocumentProto> Get(
      std::string_view name_space, std::string_view uri,
      bool clear_internal_fields = true) const;

  // Finds and returns the document identified by the given document id. If
  // 'clear_internal_fields' is true, document level data that's generated
  // internally by DocumentStore is cleared.
  //
  // Returns:
  //   The document found on success
  //   INVALID_ARGUMENT if document_id is less than 0 or greater than the
  //                    maximum value
  //   NOT_FOUND if the document doesn't exist or has been deleted
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<DocumentProto> Get(
      DocumentId document_id, bool clear_internal_fields = true) const;

  // Returns all namespaces which have at least 1 active document (not deleted
  // or expired). Order of namespaces is undefined.
  std::vector<std::string> GetAllNamespaces() const;

  // Deletes the document identified by the given namespace and uri. The
  // document proto will be erased immediately.
  //
  // NOTE:
  //    Space is not reclaimed for deleted documents until Optimize() is
  //    called.
  //
  // Returns:
  //   OK on success
  //   NOT_FOUND if no document exists with namespace, uri
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::Status Delete(std::string_view name_space,
                                    std::string_view uri,
                                    int64_t current_time_ms);

  // Deletes the document identified by the given document_id. The document
  // proto will be erased immediately.
  //
  // NOTE:
  //    Space is not reclaimed for deleted documents until Optimize() is
  //    called.
  //
  // Returns:
  //   OK on success
  //   NOT_FOUND if the document doesn't exist (i.e. deleted or expired)
  //   INTERNAL_ERROR on IO error
  //   INVALID_ARGUMENT if document_id is invalid.
  libtextclassifier3::Status Delete(DocumentId document_id,
                                    int64_t current_time_ms);

  // Returns the NamespaceId of the string namespace
  //
  // Returns:
  //   NamespaceId on success
  //   NOT_FOUND if the namespace doesn't exist
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<NamespaceId> GetNamespaceId(
      std::string_view name_space) const;

  // Helper method to find a DocumentId that is associated with the given
  // namespace and uri.
  //
  // NOTE: The DocumentId may refer to a invalid document (deleted
  // or expired). Callers can call DoesDocumentExist(document_id) to ensure it
  // refers to a valid Document.
  //
  // Returns:
  //   A DocumentId on success
  //   NOT_FOUND if the key doesn't exist
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<DocumentId> GetDocumentId(
      std::string_view name_space, std::string_view uri) const;

  // Helper method to find a DocumentId that is associated with the given
  // NamespaceFingerprintIdentifier.
  //
  // NOTE: The DocumentId may refer to a invalid document (deleted
  // or expired). Callers can call DoesDocumentExist(document_id) to ensure it
  // refers to a valid Document.
  //
  // Returns:
  //   A DocumentId on success
  //   NOT_FOUND if the key doesn't exist
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<DocumentId> GetDocumentId(
      const NamespaceFingerprintIdentifier& namespace_fingerprint_identifier)
      const;

  // Returns the CorpusId associated with the given namespace and schema.
  //
  // Returns:
  //   A CorpusId on success
  //   NOT_FOUND if the key doesn't exist
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<CorpusId> GetCorpusId(
      const std::string_view name_space, const std::string_view schema) const;

  // Returns the ResultGroupingEntryId associated with the given namespace
  // and schema.
  //
  // NOTE: ResultGroupingEntryIds that are generated by calls with different
  // ResultGroupingTypes should not be compared. Returned ResultGroupingEntryIds
  // are only guarenteed to be unique within their own ResultGroupingType.
  //
  // Returns:
  //   A ResultGroupingEntryId on success
  //   NOT_FOUND if the key doesn't exist
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<int32_t> GetResultGroupingEntryId(
      ResultSpecProto::ResultGroupingType result_group_type,
      const std::string_view name_space, const std::string_view schema) const;

  // Returns the ResultGrouping Entry Id associated with the given NamespaceId
  // and SchemaTypeId
  //
  // NOTE: ResultGroupingEntryIds that are generated by calls with different
  // ResultGroupingTypes should not be compared. Returned ResultGroupingEntryIds
  // are only guarenteed to be unique within their own ResultGroupingType.
  //
  // Returns:
  //   A ResultGroupingEntryId on success
  //   NOT_FOUND if the key doesn't exist
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<int32_t> GetResultGroupingEntryId(
      ResultSpecProto::ResultGroupingType result_group_type,
      const NamespaceId namespace_id, const SchemaTypeId schema_type_id) const;

  // Returns the DocumentAssociatedScoreData of the document specified by the
  // DocumentId.
  //
  // Returns:
  //   DocumentAssociatedScoreData on success
  //   NOT_FOUND if the document or the score data is not found
  libtextclassifier3::StatusOr<DocumentAssociatedScoreData>
  GetDocumentAssociatedScoreData(DocumentId document_id) const;

  // Returns the CorpusAssociatedScoreData of the corpus specified by the
  // corpus_id.
  //
  // NOTE: This does not check if the corpus exists and will return the
  // CorpusAssociatedScoreData of the corpus even if all documents belonging to
  // that corpus have been deleted.
  //
  // Returns:
  //   CorpusAssociatedScoreData on success
  //   OUT_OF_RANGE if corpus_id is negative or exceeds previously seen
  //                CorpusIds
  libtextclassifier3::StatusOr<CorpusAssociatedScoreData>
  GetCorpusAssociatedScoreData(CorpusId corpus_id) const;

  // Gets the document filter data if a document exists. Otherwise, will get a
  // false optional.
  //
  // Existence means it hasn't been deleted and it hasn't expired yet.
  //
  // Returns:
  //   True:DocumentFilterData  if the given document exists.
  //   False                    if the given document doesn't exist.
  std::optional<DocumentFilterData> GetAliveDocumentFilterData(
      DocumentId document_id, int64_t current_time_ms) const;

  // Gets the usage scores of a document.
  //
  // Returns:
  //   UsageScores on success
  //   nullopt if there are no usage scores stored for the requested docid.
  std::optional<UsageStore::UsageScores> GetUsageScores(
      DocumentId document_id, int64_t current_time_ms) const;

  // Reports usage. The corresponding usage scores of the specified document in
  // the report will be updated.
  //
  // Returns:
  //   OK on success
  //   NOT_FOUND if the [namesapce + uri] key in the report doesn't exist
  //   INTERNAL_ERROR on I/O errors.
  libtextclassifier3::Status ReportUsage(const UsageReport& usage_report);

  // Deletes all documents belonging to the given namespace. The documents will
  // be erased immediately.
  //
  // NOTE:
  //    Space is not reclaimed for deleted documents until Optimize() is
  //    called.
  //
  // Returns:
  //   OK on success
  //   NOT_FOUND if namespace doesn't exist
  //   INTERNAL_ERROR on IO error
  DeleteByGroupResult DeleteByNamespace(std::string_view name_space);

  // Deletes all documents belonging to the given schema type. The documents
  // will be erased immediately.
  //
  // NOTE:
  //    Space is not reclaimed for deleted documents until Optimize() is
  //    called.
  //
  // Returns:
  //   OK on success
  //   NOT_FOUND if schema_type doesn't exist
  //   INTERNAL_ERROR on IO error
  DeleteByGroupResult DeleteBySchemaType(std::string_view schema_type);

  // Syncs all the data and metadata changes to disk.
  //
  // Returns:
  //   OK on success
  //   INTERNAL on I/O error
  libtextclassifier3::Status PersistToDisk(PersistType::Code persist_type);

  // Calculates the StorageInfo for the Document Store.
  //
  // If an IO error occurs while trying to calculate the value for a field, then
  // that field will be set to -1.
  DocumentStorageInfoProto GetStorageInfo() const;

  // Update any derived data off of the SchemaStore with the new SchemaStore.
  // This may include pointers, SchemaTypeIds, etc.
  //
  // NOTE: This function may delete documents. A document may be invalidated by
  // the new SchemaStore, such as failing validation or having its schema type
  // deleted from the schema.
  //
  // This is best used if the caller is unsure about what's changed in the
  // SchemaStore, and wants to update all information no matter what. If the
  // caller does know what has changed, then it's recommended to call
  // OptimizedUpdateSchemaStore.
  //
  // Returns;
  //   OK on success
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::Status UpdateSchemaStore(const SchemaStore* schema_store);

  // Performs the same funtionality as UpdateSchemaStore, but this can be more
  // optimized in terms of less disk reads and less work if we know exactly
  // what's changed between the old and new SchemaStore.
  //
  // Returns;
  //   OK on success
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::Status OptimizedUpdateSchemaStore(
      const SchemaStore* schema_store,
      const SchemaStore::SetSchemaResult& set_schema_result);

  // Reduces internal file sizes by reclaiming space of deleted documents and
  // regenerating derived files.
  //
  // NOTE: The tasks in this method are too expensive to be executed in
  // real-time. The caller should decide how frequently and when to call this
  // method based on device usage.
  //
  // Returns:
  //   OK on success
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::Status Optimize();

  struct OptimizeResult {
    // A vector that maps old document id to new document id.
    std::vector<DocumentId> document_id_old_to_new;

    // A vector that maps old namespace id to new namespace id. Will be empty if
    // should_rebuild_index is set to true.
    std::vector<NamespaceId> namespace_id_old_to_new;

    // A boolean flag that hints the caller (usually IcingSearchEngine) if it
    // should rebuild index instead of adopting the id changes via the 2 vectors
    // above. It will be set to true if finding any id inconsistency.
    bool should_rebuild_index = false;
  };
  // Copy data from current base directory into a new directory. Any outdated or
  // deleted data won't be copied. During the process, document/namespace ids
  // will be reassigned so any files / classes that are based on old
  // document/namespace ids may be outdated.
  //
  // stats will be set if non-null.
  //
  // NOTE: The tasks in this method are too expensive to be executed in
  // real-time. The caller should decide how frequently and when to call this
  // method based on device usage.
  //
  // Returns:
  //   OptimizeResult which contains a vector mapping from old document id to
  //   new document id and another vector mapping from old namespace id to new
  //   namespace id, on success
  //   INVALID_ARGUMENT if new_directory is same as current base directory
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<OptimizeResult> OptimizeInto(
      const std::string& new_directory, const LanguageSegmenter* lang_segmenter,
      OptimizeStatsProto* stats = nullptr) const;

  // Calculates status for a potential Optimize call. Includes how many docs
  // there are vs how many would be optimized away. And also includes an
  // estimated size gains, in bytes, if Optimize were called.
  //
  // Returns:
  //   OptimizeInfo on success
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<OptimizeInfo> GetOptimizeInfo() const;

  // Computes the combined checksum of the document store - includes the ground
  // truth and all derived files.
  //
  // Returns:
  //   Combined checksum on success
  //   INTERNAL_ERROR on compute error
  libtextclassifier3::StatusOr<Crc32> ComputeChecksum() const;

  // Get debug information for the document store.
  // verbosity <= 0, simplest debug information
  // verbosity > 0, also return the total number of documents and tokens in each
  // (namespace, schema type) pair.
  //
  // Returns:
  //   DocumentDebugInfoProto on success
  //   INTERNAL_ERROR on IO errors, crc compute error
  libtextclassifier3::StatusOr<DocumentDebugInfoProto> GetDebugInfo(
      int verbosity) const;

 private:
  // Use DocumentStore::Create() to instantiate.
  explicit DocumentStore(const Filesystem* filesystem,
                         std::string_view base_dir, const Clock* clock,
                         const SchemaStore* schema_store,
                         bool namespace_id_fingerprint, bool pre_mapping_fbv,
                         bool use_persistent_hash_map,
                         int32_t compression_level);

  const Filesystem* const filesystem_;
  const std::string base_dir_;
  const Clock& clock_;

  // Handles the ground truth schema and all of the derived data off of the
  // schema
  const SchemaStore* schema_store_;

  // Used to validate incoming documents
  DocumentValidator document_validator_;

  // Whether to use namespace id or namespace name to build up fingerprint for
  // document_key_mapper_ and corpus_mapper_.
  bool namespace_id_fingerprint_;

  // Flag indicating whether memory map max possible file size for underlying
  // FileBackedVector before growing the actual file size.
  bool pre_mapping_fbv_;

  // Flag indicating whether use persistent hash map as the key mapper (if
  // false, then fall back to dynamic trie key mapper). Note: we only use
  // persistent hash map for uri mapper if it is true.
  bool use_persistent_hash_map_;

  const int32_t compression_level_;

  // A log used to store all documents, it serves as a ground truth of doc
  // store. key_mapper_ and document_id_mapper_ can be regenerated from it.
  std::unique_ptr<PortableFileBackedProtoLog<DocumentWrapper>> document_log_;

  // Key (namespace + uri) to DocumentId mapping
  std::unique_ptr<
      KeyMapper<DocumentId, fingerprint_util::FingerprintStringFormatter>>
      document_key_mapper_;

  // DocumentId to file offset mapping
  std::unique_ptr<FileBackedVector<int64_t>> document_id_mapper_;

  // A cache of document associated scores. The ground truth of the scores is
  // DocumentProto stored in document_log_. This cache contains:
  //   - CorpusId
  //   - Document score
  //   - Document creation timestamp in seconds
  //   - Document length in number of tokens
  std::unique_ptr<FileBackedVector<DocumentAssociatedScoreData>> score_cache_;

  // A cache of data, indexed by DocumentId, used to filter documents. Currently
  // contains:
  //   - NamespaceId
  //   - SchemaTypeId
  //   - Expiration timestamp in seconds
  std::unique_ptr<FileBackedVector<DocumentFilterData>> filter_cache_;

  // A cache of corpus associated scores. The ground truth of the scores is
  // DocumentProto stored in document_log_. This cache contains:
  //   - Number of documents belonging to the corpus score
  //   - The sum of the documents' lengths, in number of tokens.
  std::unique_ptr<FileBackedVector<CorpusAssociatedScoreData>>
      corpus_score_cache_;

  // Maps namespaces to a densely-assigned unique id. Namespaces are assigned an
  // id when the first document belonging to that namespace is added to the
  // DocumentStore. Namespaces may be removed from the mapper during compaction.
  std::unique_ptr<KeyMapper<NamespaceId>> namespace_mapper_;

  // Maps a corpus, i.e. a (namespace, schema type) pair, to a densely-assigned
  // unique id. A coprus is assigned an
  // id when the first document belonging to that corpus is added to the
  // DocumentStore. Corpus ids may be removed from the mapper during compaction.
  std::unique_ptr<
      KeyMapper<CorpusId, fingerprint_util::FingerprintStringFormatter>>
      corpus_mapper_;

  // A storage class that caches all usage scores. Usage scores are not
  // considered as ground truth. Usage scores are associated with document ids
  // so they need to be updated when document ids change.
  std::unique_ptr<UsageStore> usage_store_;

  // Used internally to indicate whether the class has been initialized. This is
  // to guard against cases where the object has been created, but Initialize
  // fails in the constructor. If we have successfully exited the constructor,
  // then this field can be ignored. Clients of DocumentStore should not need to
  // worry about this field.
  bool initialized_ = false;

  struct InitializeResult {
    DataLoss data_loss;

    // A boolean flag indicating if derived files of the document store have
    // been regenerated or not. This is usually a signal for callers to detect
    // if any id assignment has changed (e.g. NamespaceId).
    bool derived_files_regenerated;
  };
  libtextclassifier3::StatusOr<InitializeResult> Initialize(
      bool force_recovery_and_revalidate_documents,
      InitializeStatsProto* initialize_stats);

  // Creates sub-components and verifies the integrity of each sub-component.
  // This assumes that the the underlying files already exist, and will return
  // an error if it doesn't find what it's expecting.
  //
  // Returns an error if subcomponents failed to initialize successfully.
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::Status InitializeExistingDerivedFiles();

  // Re-generates all files derived from the ground truth: the document log.
  //
  // revalidate_documents=true will also cause each document to be revalidated
  // the schema as it is read out of the document log.
  //
  // NOTE: if this function fails, the only thing we can do is to retry it until
  // it succeeds or prevent the initialization of a DocumentStore. The
  // DocumentStore object wouldn't work reliably if this fails.
  //
  // Steps:
  //   1. Delete all derived files.
  //   2. Iterate through document log, put data into new key mapper and
  //   document_id
  //      mapper.
  //   3. Create header and store the updated combined checksum
  libtextclassifier3::Status RegenerateDerivedFiles(bool revalidate_documents);

  // Resets the unique_ptr to the document_key_mapper, deletes the underlying
  // file, and re-creates a new instance of the document_key_mapper .
  //
  // Returns OK or any IO errors.
  libtextclassifier3::Status ResetDocumentKeyMapper();

  // Resets the unique_ptr to the document_id_mapper, deletes the underlying
  // file, and re-creates a new instance of the document_id_mapper.
  //
  // Returns OK or any IO errors.
  libtextclassifier3::Status ResetDocumentIdMapper();

  // Resets the unique_ptr to the score_cache, deletes the underlying file, and
  // re-creates a new instance of the score_cache.
  //
  // Returns OK or any IO errors.
  libtextclassifier3::Status ResetDocumentAssociatedScoreCache();

  // Resets the unique_ptr to the corpus_score_cache, deletes the underlying
  // file, and re-creates a new instance of the corpus_score_cache.
  //
  // Returns OK or any IO errors.
  libtextclassifier3::Status ResetCorpusAssociatedScoreCache();

  // Resets the unique_ptr to the filter_cache, deletes the underlying file, and
  // re-creates a new instance of the filter_cache.
  //
  // Returns OK or any IO errors.
  libtextclassifier3::Status ResetFilterCache();

  // Resets the unique_ptr to the namespace_mapper, deletes the underlying file,
  // and re-creates a new instance of the namespace_mapper.
  //
  // Returns OK or any IO errors.
  libtextclassifier3::Status ResetNamespaceMapper();

  // Resets the unique_ptr to the corpus_mapper, deletes the underlying file,
  // and re-creates a new instance of the corpus_mapper.
  //
  // Returns OK or any IO errors.
  libtextclassifier3::Status ResetCorpusMapper();

  // Checks if the header exists already. This does not create the header file
  // if it doesn't exist.
  bool HeaderExists();

  // Update, replace and persist the header file. Creates the header file if it
  // doesn't exist.
  //
  // Returns:
  //   OK on success
  //   INTERNAL on I/O error
  libtextclassifier3::Status UpdateHeader(const Crc32& checksum);

  libtextclassifier3::StatusOr<DocumentId> InternalPut(
      DocumentProto&& document,
      PutDocumentStatsProto* put_document_stats = nullptr);

  // Helper function to do batch deletes. Documents with the given
  // "namespace_id" and "schema_type_id" will be deleted. If callers don't need
  // to specify the namespace or schema type, pass in kInvalidNamespaceId or
  // kInvalidSchemaTypeId. The document protos with their derived data will be
  // erased / cleared immediately.
  //
  // NOTE: Space is not reclaimed in the derived files until Optimize() is
  // called.
  //
  // Returns:
  //   Number of documents that were actually updated to be deleted
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::StatusOr<int> BatchDelete(NamespaceId namespace_id,
                                                SchemaTypeId schema_type_id);

  // Returns the CorpusAssociatedScoreData of the corpus specified by the
  // corpus_id.
  //
  // If the corpus_id has never been seen before, it returns a
  // CorpusAssociatedScoreData with properties set to default values.
  //
  // NOTE: This does not check if the corpus exists and will return the
  // CorpusAssociatedScoreData of the corpus even if all documents belonging to
  // that corpus have been deleted.
  //
  // Returns:
  //   CorpusAssociatedScoreData on success
  libtextclassifier3::StatusOr<CorpusAssociatedScoreData>
  GetCorpusAssociatedScoreDataToUpdate(CorpusId corpus_id) const;

  // Check if a document exists. Existence means it hasn't been deleted and it
  // hasn't expired yet.
  //
  // Returns:
  //   OK if the document exists
  //   INVALID_ARGUMENT if document_id is less than 0 or greater than the
  //                    maximum value
  //   NOT_FOUND if the document doesn't exist (i.e. deleted or expired)
  //   INTERNAL_ERROR on IO error
  libtextclassifier3::Status DoesDocumentExistWithStatus(
      DocumentId document_id) const;

  // Checks if a document has been deleted
  //
  // This is for internal-use only because we assume that the document_id is
  // already valid. If you're unsure if the document_id is valid, use
  // DoesDocumentExist(document_id) instead, which will perform those additional
  // checks.
  bool IsDeleted(DocumentId document_id) const;

  // Checks if a document has expired.
  //
  // This is for internal-use only because we assume that the document_id is
  // already valid. If you're unsure if the document_id is valid, use
  // DoesDocumentExist(document_id) instead, which will perform those additional
  // checks.

  // Returns:
  //   True:DocumentFilterData  if the given document isn't expired.
  //   False                    if the given doesn't document is expired.
  std::optional<DocumentFilterData> GetNonExpiredDocumentFilterData(
      DocumentId document_id, int64_t current_time_ms) const;

  // Updates the entry in the score cache for document_id.
  libtextclassifier3::Status UpdateDocumentAssociatedScoreCache(
      DocumentId document_id, const DocumentAssociatedScoreData& score_data);

  // Updates the entry in the corpus score cache for corpus_id.
  libtextclassifier3::Status UpdateCorpusAssociatedScoreCache(
      CorpusId corpus_id, const CorpusAssociatedScoreData& score_data);

  // Updates the entry in the filter cache for document_id.
  libtextclassifier3::Status UpdateFilterCache(
      DocumentId document_id, const DocumentFilterData& filter_data);

  // Helper method to clear the derived data of a document
  libtextclassifier3::Status ClearDerivedData(DocumentId document_id);

  // Sets usage scores for the given document.
  libtextclassifier3::Status SetUsageScores(
      DocumentId document_id, const UsageStore::UsageScores& usage_scores);

  // Returns:
  //   - on success, a DocumentStorageInfoProto with the fields relating to the
  //     size of Document Store member variables populated.
  //   - INTERNAL on failure to get file size
  DocumentStorageInfoProto GetMemberStorageInfo() const;

  // Returns:
  //   - on success, the storage_info that was passed in but with the number of
  //     alive, deleted and expired documents also set.
  //   - OUT_OF_RANGE, this should never happen. This could only be returned if
  //     the document_id_mapper somehow became larger than the filter cache.
  DocumentStorageInfoProto CalculateDocumentStatusCounts(
      DocumentStorageInfoProto storage_info) const;

  // Returns:
  //   - on success, a RepeatedPtrField for CorpusInfo collected.
  //   - OUT_OF_RANGE, this should never happen.
  libtextclassifier3::StatusOr<
      google::protobuf::RepeatedPtrField<DocumentDebugInfoProto::CorpusInfo>>
  CollectCorpusInfo() const;

  // Build fingerprint for the keys of document_key_mapper_ and corpus_mapper_.
  // Note that namespace_id_fingerprint_ controls the way that a fingerprint is
  // built.
  std::string MakeFingerprint(NamespaceId namespace_id,
                              std::string_view namespace_,
                              std::string_view uri_or_schema) const;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_STORE_DOCUMENT_STORE_H_
