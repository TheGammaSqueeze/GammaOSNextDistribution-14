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

#ifndef ICING_ICING_SEARCH_ENGINE_H_
#define ICING_ICING_SEARCH_ENGINE_H_

#include <cstdint>
#include <memory>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/mutex.h"
#include "icing/absl_ports/thread_annotations.h"
#include "icing/file/filesystem.h"
#include "icing/file/version-util.h"
#include "icing/index/data-indexing-handler.h"
#include "icing/index/index.h"
#include "icing/index/numeric/numeric-index.h"
#include "icing/jni/jni-cache.h"
#include "icing/join/join-children-fetcher.h"
#include "icing/join/qualified-id-join-index.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/performance-configuration.h"
#include "icing/proto/debug.pb.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/initialize.pb.h"
#include "icing/proto/logging.pb.h"
#include "icing/proto/optimize.pb.h"
#include "icing/proto/persist.pb.h"
#include "icing/proto/reset.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/scoring.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/storage.pb.h"
#include "icing/proto/usage.pb.h"
#include "icing/query/query-terms.h"
#include "icing/result/result-state-manager.h"
#include "icing/schema/schema-store.h"
#include "icing/scoring/scored-document-hit.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/transform/normalizer.h"
#include "icing/util/clock.h"

namespace icing {
namespace lib {

// TODO(cassiewang) Top-level comments and links to design-doc.
class IcingSearchEngine {
 public:
  // Note: It is only required to provide a pointer to a valid instance of
  // JniCache if this instance needs to perform reverse-jni calls. Users on
  // Linux and iOS should always provide a nullptr.
  explicit IcingSearchEngine(
      const IcingSearchEngineOptions& options,
      std::unique_ptr<const JniCache> jni_cache = nullptr);

  // Calculates integrity checks and persists files to disk.
  ~IcingSearchEngine();

  // Loads & verifies the contents previously indexed from disk and gets ready
  // to handle read/write requests.
  //
  // WARNING: This is expected to be fast if Icing had a clean shutdown.
  // Otherwise, it can take longer as it runs integrity checks and attempts
  // to bring the index to a consistent state. If the data on disk is not
  // consistent, it restores the state when PersistToDisk() was last called.
  //
  // TODO(cassiewang): We shouldn't return NOT_FOUND here, this is a symptom
  // of some other error. We should return a broader error group, i.e. data
  // inconsistency or something
  //
  // Returns:
  //   OK on success
  //   DATA_LOSS if encountered any inconsistencies in data and had to restore
  //     its state back to the last time PersistToDisk was called. Or if any
  //     persisted data was lost and could not be recovered.
  //   INTERNAL if any internal state was left in an inconsistent. The instance
  //     of IcingSearchEngine is unusable if this happens. It's recommended to
  //     clear the underlying directory provided in
  //     IcingSearchEngineOptions.base_dir and reinitialize.
  //   RESOURCE_EXHAUSTED if not enough storage space
  //   NOT_FOUND if missing some internal data
  InitializeResultProto Initialize() ICING_LOCKS_EXCLUDED(mutex_);

  // Specifies the schema to be applied on all Documents that are already
  // stored as well as future documents. A schema can be 'invalid' and/or
  // 'incompatible'. These are two independent concepts.
  //
  // An 'invalid' schema is one that is not constructed properly. For example,
  // a PropertyConfigProto is missing the property name field. A schema can be
  // 'invalid' even if there is no previously existing schema.
  //
  // An 'incompatible' schema is one that is incompatible with a previously
  // existing schema. If there is no previously existing schema, then a new
  // schema cannot be incompatible. An incompatible schema is one that
  // invalidates pre-existing data. For example, a previously OPTIONAL field is
  // now REQUIRED in the new schema, and pre-existing data is considered invalid
  // against the new schema now.
  //
  // Default behavior will not allow a new schema to be set if it is invalid or
  // incompatible.
  //
  // The argument 'ignore_errors_and_delete_documents' can be set to true to
  // force set an incompatible schema. In that case, documents that are
  // invalidated by the new schema would be deleted from Icing. This cannot be
  // used to force set an invalid schema.
  //
  // This schema is persisted to disk and used across multiple instances.
  // So, callers should only have to call this if the schema changed.
  // However, calling it multiple times with the same schema is a no-op.
  //
  // On some errors, Icing will keep using the older schema, but on
  // INTERNAL_ERROR, it is undefined to continue using Icing.
  //
  // Returns:
  //   OK on success
  //   ALREADY_EXISTS if 'new_schema' contains multiple definitions of the same
  //     type or contains a type that has multiple properties with the same
  //     name.
  //   INVALID_ARGUMENT if 'new_schema' is invalid
  //   FAILED_PRECONDITION if 'new_schema' is incompatible, or IcingSearchEngine
  //     has not been initialized yet.
  //   INTERNAL_ERROR if Icing failed to store the new schema or upgrade
  //     existing data based on the new schema. Using Icing beyond this error is
  //     undefined and may cause crashes.
  //   DATA_LOSS_ERROR if 'new_schema' requires the index to be rebuilt and an
  //     IO error leads to some documents being excluded from the index. These
  //     documents will still be retrievable via Get, but won't match queries.
  //
  // TODO(cassiewang) Figure out, document (and maybe even enforce) the best
  // way ordering of calls between Initialize() and SetSchema(), both when
  // the caller is creating an instance of IcingSearchEngine for the first
  // time and when the caller is reinitializing an existing index on disk.
  SetSchemaResultProto SetSchema(
      SchemaProto&& new_schema, bool ignore_errors_and_delete_documents = false)
      ICING_LOCKS_EXCLUDED(mutex_);

  // This function makes a copy of the schema and calls SetSchema(SchemaProto&&
  // new_schema, bool ignore_errors_and_delete_documents)
  //
  // NOTE: It's recommended to call SetSchema(SchemaProto&& new_schema, bool
  // ignore_errors_and_delete_documents) directly to avoid a copy if the caller
  // can make an rvalue SchemaProto.
  SetSchemaResultProto SetSchema(const SchemaProto& new_schema,
                                 bool ignore_errors_and_delete_documents =
                                     false) ICING_LOCKS_EXCLUDED(mutex_);

  // Get Icing's current copy of the schema.
  //
  // Returns:
  //   SchemaProto on success
  //   NOT_FOUND if a schema has not been set yet
  //   FAILED_PRECONDITION IcingSearchEngine has not been initialized yet.
  //   INTERNAL_ERROR on IO error
  GetSchemaResultProto GetSchema() ICING_LOCKS_EXCLUDED(mutex_);

  // Get Icing's copy of the SchemaTypeConfigProto of name schema_type
  //
  // Returns:
  //   SchemaTypeConfigProto on success
  //   FAILED_PRECONDITION if a schema has not been set yet, IcingSearchEngine
  //     has not been initialized yet.
  //   NOT_FOUND if there is no SchemaTypeConfig of schema_type in the
  //     SchemaProto
  //   INTERNAL_ERROR on IO error
  GetSchemaTypeResultProto GetSchemaType(std::string_view schema_type)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Puts the document into icing search engine so that it's stored and
  // indexed. Documents are automatically written to disk, callers can also
  // call PersistToDisk() to flush changes immediately.
  //
  // Returns:
  //   OK on success
  //   OUT_OF_SPACE if exceeds maximum number of allowed documents
  //   FAILED_PRECONDITION if a schema has not been set yet, IcingSearchEngine
  //     has not been initialized yet.
  //   NOT_FOUND if there is no SchemaTypeConfig in the SchemaProto that matches
  //     the document's schema
  //   DATA_LOSS if an IO error occurs while merging document into the index and
  //     the index is lost. These documents will still be retrievable via Get,
  //     but won't match queries.
  //   INTERNAL_ERROR on IO error
  PutResultProto Put(DocumentProto&& document) ICING_LOCKS_EXCLUDED(mutex_);

  // This function makes a copy of document and calls Put(DocumentProto&&
  // document).
  //
  // NOTE: It's recommended to call Put(DocumentProto&& document) directly to
  // avoid a copy if the caller can make an rvalue DocumentProto.
  PutResultProto Put(const DocumentProto& document)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Finds and returns the document identified by the given key (namespace +
  // uri)
  //
  // Returns:
  //   The document found on success
  //   NOT_FOUND if the key doesn't exist or doc has been deleted
  //   FAILED_PRECONDITION IcingSearchEngine has not been initialized yet
  //   INTERNAL_ERROR on IO error
  GetResultProto Get(std::string_view name_space, std::string_view uri,
                     const GetResultSpecProto& result_spec);

  // Reports usage. The corresponding usage scores of the specified document in
  // the report will be updated.
  //
  // Returns:
  //   OK on success
  //   NOT_FOUND if the [namesapce + uri] key in the report doesn't exist
  //   INTERNAL_ERROR on I/O errors.
  ReportUsageResultProto ReportUsage(const UsageReport& usage_report);

  // Returns all the namespaces that have at least one valid document in it.
  //
  // Returns:
  //   All namespaces on success
  GetAllNamespacesResultProto GetAllNamespaces();

  // Deletes the Document specified by the given namespace / uri pair from the
  // search engine. Delete changes are automatically applied to disk, callers
  // can also call PersistToDisk() to flush changes immediately.
  //
  // NOTE: Space is not reclaimed for deleted documents until Optimize() is
  // called.
  //
  // Returns:
  //   OK on success
  //   NOT_FOUND if no document exists with namespace, uri
  //   FAILED_PRECONDITION IcingSearchEngine has not been initialized yet
  //   INTERNAL_ERROR on IO error
  DeleteResultProto Delete(std::string_view name_space, std::string_view uri)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Deletes all Documents belonging to the specified namespace from the search
  // engine. Delete changes are automatically applied to disk, callers can also
  // call PersistToDisk() to flush changes immediately.
  //
  // NOTE: Space is not reclaimed for deleted documents until Optimize() is
  // called.
  //
  // Returns:
  //   OK on success
  //   NOT_FOUND if namespace doesn't exist
  //   FAILED_PRECONDITION IcingSearchEngine has not been initialized yet
  //   INTERNAL_ERROR on IO error
  DeleteByNamespaceResultProto DeleteByNamespace(std::string_view name_space)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Deletes all Documents belonging to the specified type from the search
  // engine. Delete changes are automatically applied to disk, callers can also
  // call PersistToDisk() to flush changes immediately.
  //
  // NOTE: Space is not reclaimed for deleted documents until Optimize() is
  // called.
  //
  // Returns:
  //   OK on success
  //   NOT_FOUND if schema type doesn't exist
  //   FAILED_PRECONDITION IcingSearchEngine has not been initialized yet
  //   INTERNAL_ERROR on IO error
  DeleteBySchemaTypeResultProto DeleteBySchemaType(std::string_view schema_type)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Deletes all Documents that match the query specified in search_spec. Delete
  // changes are automatically applied to disk, callers can also call
  // PersistToDisk() to flush changes immediately.
  //
  // NOTE: Space is not reclaimed for deleted documents until Optimize() is
  // called.
  //
  // Returns:
  //   OK on success
  //   NOT_FOUND if the query doesn't match any documents
  //   FAILED_PRECONDITION IcingSearchEngine has not been initialized yet
  //   INTERNAL_ERROR on IO error
  DeleteByQueryResultProto DeleteByQuery(
      const SearchSpecProto& search_spec,
      bool return_deleted_document_info = false) ICING_LOCKS_EXCLUDED(mutex_);

  // Retrieves, scores, ranks, and returns the results according to the specs.
  // Results can be empty. If there're multiple pages of results,
  // SearchResultProto.next_page_token will be set to a non-zero token and can
  // be used to fetch more pages via GetNextPage() method. Clients should call
  // InvalidateNextPageToken() after they get the pages they need to release
  // result cache in memory. Please refer to each proto file for spec
  // definitions.
  //
  // Returns a SearchResultProto with status:
  //   OK with results on success
  //   INVALID_ARGUMENT if any of specs is invalid
  //   ABORTED if failed to perform search but existing data is not affected
  //   FAILED_PRECONDITION IcingSearchEngine has not been initialized yet
  //   INTERNAL_ERROR on any other errors
  SearchResultProto Search(const SearchSpecProto& search_spec,
                           const ScoringSpecProto& scoring_spec,
                           const ResultSpecProto& result_spec)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Retrieves, scores, ranks and returns the suggested query string according
  // to the specs. Results can be empty.
  //
  // Returns a SuggestionResponse with status:
  //   OK with results on success
  //   INVALID_ARGUMENT if any of specs is invalid
  //   FAILED_PRECONDITION IcingSearchEngine has not been initialized yet
  //   INTERNAL_ERROR on any other errors
  SuggestionResponse SearchSuggestions(
      const SuggestionSpecProto& suggestion_spec) ICING_LOCKS_EXCLUDED(mutex_);

  // Fetches the next page of results of a previously executed query. Results
  // can be empty if next-page token is invalid. Invalid next page tokens are
  // tokens that are either zero or were previously passed to
  // InvalidateNextPageToken. If there are pages of results remaining after the
  // one retrieved by this call, SearchResultProto.next_page_token will be
  // set to a non-zero token and can be used to fetch more pages via
  // GetNextPage() method.
  //
  // Returns a SearchResultProto with status:
  //   OK with results on success
  //   ABORTED if failed to get results but existing data is not affected
  //   FAILED_PRECONDITION IcingSearchEngine has not been initialized yet
  //   INTERNAL_ERROR on any other errors
  SearchResultProto GetNextPage(uint64_t next_page_token)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Invalidates the next-page token so that no more results of the related
  // query can be returned.
  void InvalidateNextPageToken(uint64_t next_page_token)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Makes sure that every update/delete received till this point is flushed
  // to disk. If the app crashes after a call to PersistToDisk(), Icing
  // would be able to fully recover all data written up to this point.
  //
  // If persist_type is PersistType::LITE, then only the ground truth will be
  // synced. This should be relatively lightweight to do (order of microseconds)
  // and ensures that there will be no data loss. At worst, Icing may need to
  // recover internal data structures by replaying the document log upon the
  // next startup. Clients should call PersistToDisk(LITE) after each batch of
  // mutations.
  //
  // If persist_type is PersistType::FULL, then all internal data structures in
  // Icing will be synced. This is a heavier operation (order of milliseconds).
  // It ensures that Icing will not need to recover internal data structures
  // upon the next startup. Clients should call PersistToDisk(FULL) before their
  // process dies.
  //
  // NOTE: It is not necessary to call PersistToDisk() to read back data
  // that was recently written. All read APIs will include the most recent
  // updates/deletes regardless of the data being flushed to disk.
  //
  // Returns:
  //   OK on success
  //   FAILED_PRECONDITION IcingSearchEngine has not been initialized yet
  //   INTERNAL on I/O error
  PersistToDiskResultProto PersistToDisk(PersistType::Code persist_type)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Allows Icing to run tasks that are too expensive and/or unnecessary to be
  // executed in real-time, but are useful to keep it fast and be
  // resource-efficient. This method purely optimizes the internal files and
  // has no functional impact on what gets accepted/returned.
  //
  // WARNING: This method is CPU and IO intensive and depending on the
  // contents stored, it can take from a few seconds to a few minutes.
  // This call also blocks all read/write operations on Icing.
  //
  // SUGGESTION: Assuming the client has no restrictions on their side, it's
  // recommended to call this method about once every 24 hours when the
  // device is idle and charging. It can also be called when the system needs
  // to free up extra disk-space.
  //
  // Returns:
  //   OK on success
  //   ABORTED_ERROR if optimization is aborted due to non-fatal errors before
  //                 actual modifications are made.
  //   DATA_LOSS_ERROR on errors that could potentially cause data loss,
  //                   IcingSearchEngine is still functioning.
  //   INTERNAL_ERROR on any IO errors or other unrecoverable errors. Continued
  //                  use of Icing is undefined.
  //                  Clients could clear and reinitialize IcingSearchEngine.
  //   FAILED_PRECONDITION IcingSearchEngine has not been initialized yet
  OptimizeResultProto Optimize() ICING_LOCKS_EXCLUDED(mutex_);

  // Returns potential size and document savings if Optimize were called.
  //
  // Returns:
  //   OK on success
  //   FAILED_PRECONDITION if IcingSearchEngine has not been initialized yet
  //   INTERNAL_ERROR on IO error
  GetOptimizeInfoResultProto GetOptimizeInfo() ICING_LOCKS_EXCLUDED(mutex_);

  // Calculates the StorageInfo for Icing.
  //
  // If an IO error occurs while trying to calculate the value for a field, then
  // that field will be set to -1.
  StorageInfoResultProto GetStorageInfo() ICING_LOCKS_EXCLUDED(mutex_);

  // Get debug information for Icing.
  DebugInfoResultProto GetDebugInfo(DebugInfoVerbosity::Code verbosity)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Clears all data from Icing and re-initializes. Clients DO NOT need to call
  // Initialize again.
  //
  // Returns:
  //   OK on success
  //   ABORTED_ERROR if failed to delete underlying files
  //   INTERNAL_ERROR if internal state is no longer consistent
  ResetResultProto Reset() ICING_LOCKS_EXCLUDED(mutex_);

  // Disallow copy and move.
  IcingSearchEngine(const IcingSearchEngine&) = delete;
  IcingSearchEngine& operator=(const IcingSearchEngine&) = delete;

 protected:
  IcingSearchEngine(IcingSearchEngineOptions options,
                    std::unique_ptr<const Filesystem> filesystem,
                    std::unique_ptr<const IcingFilesystem> icing_filesystem,
                    std::unique_ptr<Clock> clock,
                    std::unique_ptr<const JniCache> jni_cache = nullptr);

 private:
  const IcingSearchEngineOptions options_;
  const std::unique_ptr<const Filesystem> filesystem_;
  const std::unique_ptr<const IcingFilesystem> icing_filesystem_;
  bool initialized_ ICING_GUARDED_BY(mutex_) = false;

  // Abstraction for accessing time values.
  const std::unique_ptr<const Clock> clock_;

  // Provides key thresholds that affects the running time and memory of major
  // components in Icing search engine.
  const PerformanceConfiguration performance_configuration_;

  // Used to manage pagination state of query results. Even though
  // ResultStateManager has its own reader-writer lock, mutex_ must still be
  // acquired first in order to adhere to the global lock ordering:
  //   1. mutex_
  //   2. result_state_manager_.lock_
  std::unique_ptr<ResultStateManager> result_state_manager_
      ICING_GUARDED_BY(mutex_);

  // Used to provide reader and writer locks
  absl_ports::shared_mutex mutex_;

  // Stores and processes the schema
  std::unique_ptr<SchemaStore> schema_store_ ICING_GUARDED_BY(mutex_);

  // Used to store all valid documents
  std::unique_ptr<DocumentStore> document_store_ ICING_GUARDED_BY(mutex_);

  std::unique_ptr<const LanguageSegmenter> language_segmenter_
      ICING_GUARDED_BY(mutex_);

  std::unique_ptr<const Normalizer> normalizer_ ICING_GUARDED_BY(mutex_);

  // Storage for all hits of string contents from the document store.
  std::unique_ptr<Index> index_ ICING_GUARDED_BY(mutex_);

  // Storage for all hits of numeric contents from the document store.
  std::unique_ptr<NumericIndex<int64_t>> integer_index_
      ICING_GUARDED_BY(mutex_);

  // Storage for all join qualified ids from the document store.
  std::unique_ptr<QualifiedIdJoinIndex> qualified_id_join_index_
      ICING_GUARDED_BY(mutex_);

  // Pointer to JNI class references
  const std::unique_ptr<const JniCache> jni_cache_;

  // Resets all members that are created during Initialize.
  void ResetMembers() ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Resets all members that are created during Initialize, deletes all
  // underlying files and initializes a fresh index.
  ResetResultProto ResetInternal() ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Checks for the existence of the init marker file. If the failed init count
  // exceeds kMaxUnsuccessfulInitAttempts, all data is deleted and the index is
  // initialized from scratch. The updated count (original failed init count + 1
  // ) is written to the marker file.
  //
  // RETURNS
  //   OK on success
  //   INTERNAL if an IO error occurs while trying to update the marker file.
  libtextclassifier3::Status CheckInitMarkerFile(
      InitializeStatsProto* initialize_stats)
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Helper method to do the actual work to persist data to disk. We need this
  // separate method so that other public methods don't need to call
  // PersistToDisk(). Public methods calling each other may cause deadlock
  // issues.
  libtextclassifier3::Status InternalPersistToDisk(
      PersistType::Code persist_type) ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Helper method to the actual work to Initialize. We need this separate
  // method so that other public methods don't need to call Initialize(). Public
  // methods calling each other may cause deadlock issues.
  InitializeResultProto InternalInitialize()
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Helper method to initialize member variables.
  //
  // Returns:
  //   OK on success
  //   FAILED_PRECONDITION if initialize_stats is null
  //   RESOURCE_EXHAUSTED if the index runs out of storage
  //   NOT_FOUND if some Document's schema type is not in the SchemaStore
  //   INTERNAL on any I/O errors
  libtextclassifier3::Status InitializeMembers(
      InitializeStatsProto* initialize_stats)
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Do any initialization/recovery necessary to create a SchemaStore instance.
  //
  // Returns:
  //   OK on success
  //   FAILED_PRECONDITION if initialize_stats is null
  //   INTERNAL on I/O error
  libtextclassifier3::Status InitializeSchemaStore(
      InitializeStatsProto* initialize_stats)
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Do any initialization/recovery necessary to create a DocumentStore
  // instance.
  //
  // See comments on DocumentStore::Create for explanation of
  // force_recovery_and_revalidate_documents.
  //
  // Returns:
  //   On success, a boolean flag indicating whether derived files of the
  //     document store have been regenerated or not. If true, any other
  //     components depending on them should also be rebuilt if true.
  //   FAILED_PRECONDITION if initialize_stats is null
  //   INTERNAL on I/O error
  libtextclassifier3::StatusOr<bool> InitializeDocumentStore(
      bool force_recovery_and_revalidate_documents,
      InitializeStatsProto* initialize_stats)
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Do any initialization/recovery necessary to create term index, integer
  // index, and qualified id join index instances.
  //
  // If document_store_derived_files_regenerated is true, then we have to
  // rebuild qualified id join index since NamespaceIds were reassigned.
  //
  // Returns:
  //   OK on success
  //   FAILED_PRECONDITION if initialize_stats is null
  //   RESOURCE_EXHAUSTED if the index runs out of storage
  //   NOT_FOUND if some Document's schema type is not in the SchemaStore
  //   INTERNAL on I/O error
  libtextclassifier3::Status InitializeIndex(
      bool document_store_derived_files_regenerated,
      InitializeStatsProto* initialize_stats)
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Implementation of IcingSearchEngine::Search that only grabs the overall
  // read-lock, allowing for parallel non-exclusive operations.
  // This implementation is used if search_spec.use_read_only_search is true.
  SearchResultProto SearchLockedShared(const SearchSpecProto& search_spec,
                                       const ScoringSpecProto& scoring_spec,
                                       const ResultSpecProto& result_spec)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Implementation of IcingSearchEngine::Search that requires the overall
  // write lock. No other operations of any kind can be executed in parallel if
  // this version is used.
  // This implementation is used if search_spec.use_read_only_search is false.
  SearchResultProto SearchLockedExclusive(const SearchSpecProto& search_spec,
                                          const ScoringSpecProto& scoring_spec,
                                          const ResultSpecProto& result_spec)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Helper method for the actual work to Search. We need this separate
  // method to manage locking for Search.
  SearchResultProto InternalSearch(const SearchSpecProto& search_spec,
                                   const ScoringSpecProto& scoring_spec,
                                   const ResultSpecProto& result_spec)
      ICING_SHARED_LOCKS_REQUIRED(mutex_);

  // Processes query and scores according to the specs. It is a helper function
  // (called by Search) to process and score normal query and the nested child
  // query for join search.
  //
  // Returns a QueryScoringResults
  //   OK on success with a vector of ScoredDocumentHits,
  //      SectionRestrictQueryTermsMap, and other stats fields for logging.
  //   Any other errors when processing the query or scoring
  struct QueryScoringResults {
    libtextclassifier3::Status status;
    SectionRestrictQueryTermsMap query_terms;
    std::vector<ScoredDocumentHit> scored_document_hits;

    explicit QueryScoringResults(
        libtextclassifier3::Status status_in,
        SectionRestrictQueryTermsMap&& query_terms_in,
        std::vector<ScoredDocumentHit>&& scored_document_hits_in)
        : status(std::move(status_in)),
          query_terms(std::move(query_terms_in)),
          scored_document_hits(std::move(scored_document_hits_in)) {}
  };
  QueryScoringResults ProcessQueryAndScore(
      const SearchSpecProto& search_spec, const ScoringSpecProto& scoring_spec,
      const ResultSpecProto& result_spec,
      const JoinChildrenFetcher* join_children_fetcher, int64_t current_time_ms,
      QueryStatsProto::SearchStats* search_stats)
      ICING_SHARED_LOCKS_REQUIRED(mutex_);

  // Many of the internal components rely on other components' derived data.
  // Check that everything is consistent with each other so that we're not
  // using outdated derived data in some parts of our system.
  //
  // NOTE: this method can be called only at startup time or after
  // PersistToDisk(), otherwise the check could fail due to any changes that are
  // not persisted.
  //
  // Returns:
  //   OK on success
  //   NOT_FOUND if missing header file
  //   INTERNAL_ERROR on any IO errors or if header is inconsistent
  libtextclassifier3::Status CheckConsistency()
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Discards derived data that requires rebuild based on rebuild_result.
  //
  // Returns:
  //   OK on success
  //   FAILED_PRECONDITION_ERROR if those instances are valid (non nullptr)
  //   INTERNAL_ERROR on any I/O errors
  libtextclassifier3::Status DiscardDerivedFiles(
      const version_util::DerivedFilesRebuildResult& rebuild_result)
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Repopulates derived data off our ground truths.
  //
  // Returns:
  //   OK on success
  //   INTERNAL_ERROR on any IO errors
  libtextclassifier3::Status RegenerateDerivedFiles(
      InitializeStatsProto* initialize_stats = nullptr,
      bool log_document_store_stats = false)
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Optimizes the DocumentStore by removing any unneeded documents (i.e.
  // deleted, expired, etc.) from the filesystem storage.
  //
  // NOTE: This may leave the DocumentStore in an invalid/uncreated state. Users
  // would need call Initialize() to reinitialize everything into a valid state.
  //
  // Returns:
  //   On success, OptimizeResult which contains a vector mapping from old
  //   document id to new document id and another vector mapping from old
  //   namespace id to new namespace id. A value of kInvalidDocumentId indicates
  //   that the old document id has been deleted.
  //   ABORTED_ERROR if any error happens before the actual optimization, the
  //                 original document store should be still available
  //   DATA_LOSS_ERROR on errors that could potentially cause data loss,
  //                   document store is still available
  //   INTERNAL_ERROR on any IO errors or other errors that we can't recover
  //                  from
  libtextclassifier3::StatusOr<DocumentStore::OptimizeResult>
  OptimizeDocumentStore(OptimizeStatsProto* optimize_stats)
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Helper method to restore missing document data in index_, integer_index_,
  // and qualified_id_join_index_. All documents will be reindexed. This does
  // not clear the index, so it is recommended to call ClearAllIndices,
  // ClearSearchIndices, or ClearJoinIndices first if needed.
  //
  // Returns:
  //   On success, OK and a bool indicating whether or not restoration was
  //     needed.
  //   DATA_LOSS, if an error during index merging caused us to lose indexed
  //     data in the main index. Despite the data loss, this is still considered
  //     a successful run and needed_restoration will be set to true.
  //   RESOURCE_EXHAUSTED if the index fills up before finishing indexing
  //   NOT_FOUND if some Document's schema type is not in the SchemaStore
  //   INTERNAL_ERROR on any IO errors
  struct IndexRestorationResult {
    libtextclassifier3::Status status;
    bool index_needed_restoration;
    bool integer_index_needed_restoration;
    bool qualified_id_join_index_needed_restoration;
  };
  IndexRestorationResult RestoreIndexIfNeeded()
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // If we lost the schema during a previous failure, it may "look" the same as
  // not having a schema set before: we don't have a schema proto file. So do
  // some extra checks to differentiate between having-lost the schema, and
  // never having a schema before. This may determine if we need to do extra
  // recovery steps.
  //
  // Returns:
  //   bool indicating if we had a schema and unintentionally lost it
  //   INTERNAL_ERROR on I/O error
  libtextclassifier3::StatusOr<bool> LostPreviousSchema()
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Helper method to create all types of data indexing handlers to index term,
  // integer, and join qualified ids.
  libtextclassifier3::StatusOr<
      std::vector<std::unique_ptr<DataIndexingHandler>>>
  CreateDataIndexingHandlers() ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Helper method to discard parts of (term, integer, qualified id join)
  // indices if they contain data for document ids greater than
  // last_stored_document_id.
  //
  // REQUIRES: last_stored_document_id is valid (!= kInvalidDocumentId). Note:
  //   if we want to truncate everything in the index, then please call
  //   ClearSearchIndices/ClearJoinIndices/ClearAllIndices instead.
  //
  // Returns:
  //   On success, a DocumentId indicating the first document to start for
  //     reindexing and 2 bool flags indicating whether term or integer index
  //     needs restoration.
  //   INTERNAL on any I/O errors
  struct TruncateIndexResult {
    DocumentId first_document_to_reindex;
    bool index_needed_restoration;
    bool integer_index_needed_restoration;
    bool qualified_id_join_index_needed_restoration;

    explicit TruncateIndexResult(
        DocumentId first_document_to_reindex_in,
        bool index_needed_restoration_in,
        bool integer_index_needed_restoration_in,
        bool qualified_id_join_index_needed_restoration_in)
        : first_document_to_reindex(first_document_to_reindex_in),
          index_needed_restoration(index_needed_restoration_in),
          integer_index_needed_restoration(integer_index_needed_restoration_in),
          qualified_id_join_index_needed_restoration(
              qualified_id_join_index_needed_restoration_in) {}
  };
  libtextclassifier3::StatusOr<TruncateIndexResult> TruncateIndicesTo(
      DocumentId last_stored_document_id)
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Helper method to discard search (term, integer) indices.
  //
  // Returns:
  //   OK on success
  //   INTERNAL_ERROR on any I/O errors
  libtextclassifier3::Status ClearSearchIndices()
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Helper method to discard join (qualified id) indices.
  //
  // Returns:
  //   OK on success
  //   INTERNAL_ERROR on any I/O errors
  libtextclassifier3::Status ClearJoinIndices()
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Helper method to discard all search and join indices.
  //
  // Returns:
  //   OK on success
  //   INTERNAL_ERROR on any I/O errors
  libtextclassifier3::Status ClearAllIndices()
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_ICING_SEARCH_ENGINE_H_
