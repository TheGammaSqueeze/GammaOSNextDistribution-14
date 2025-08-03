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

#include "icing/icing-search-engine.h"

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/annotate.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/mutex.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/destructible-file.h"
#include "icing/file/file-backed-proto.h"
#include "icing/file/filesystem.h"
#include "icing/file/version-util.h"
#include "icing/index/data-indexing-handler.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/index-processor.h"
#include "icing/index/index.h"
#include "icing/index/integer-section-indexing-handler.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/numeric/integer-index.h"
#include "icing/index/term-indexing-handler.h"
#include "icing/index/term-metadata.h"
#include "icing/jni/jni-cache.h"
#include "icing/join/join-children-fetcher.h"
#include "icing/join/join-processor.h"
#include "icing/join/qualified-id-join-index-impl-v1.h"
#include "icing/join/qualified-id-join-index-impl-v2.h"
#include "icing/join/qualified-id-join-index.h"
#include "icing/join/qualified-id-join-indexing-handler.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/performance-configuration.h"
#include "icing/portable/endian.h"
#include "icing/proto/debug.pb.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/initialize.pb.h"
#include "icing/proto/internal/optimize.pb.h"
#include "icing/proto/logging.pb.h"
#include "icing/proto/optimize.pb.h"
#include "icing/proto/persist.pb.h"
#include "icing/proto/reset.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/scoring.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/status.pb.h"
#include "icing/proto/storage.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/proto/usage.pb.h"
#include "icing/query/advanced_query_parser/lexer.h"
#include "icing/query/query-features.h"
#include "icing/query/query-processor.h"
#include "icing/query/query-results.h"
#include "icing/query/suggestion-processor.h"
#include "icing/result/page-result.h"
#include "icing/result/projection-tree.h"
#include "icing/result/projector.h"
#include "icing/result/result-adjustment-info.h"
#include "icing/result/result-retriever-v2.h"
#include "icing/result/result-state-manager.h"
#include "icing/schema/schema-store.h"
#include "icing/scoring/advanced_scoring/score-expression.h"
#include "icing/scoring/priority-queue-scored-document-hits-ranker.h"
#include "icing/scoring/scored-document-hit.h"
#include "icing/scoring/scored-document-hits-ranker.h"
#include "icing/scoring/scoring-processor.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/transform/normalizer-factory.h"
#include "icing/util/clock.h"
#include "icing/util/data-loss.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"
#include "icing/util/tokenized-document.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {

namespace {

constexpr std::string_view kDocumentSubfolderName = "document_dir";
constexpr std::string_view kIndexSubfolderName = "index_dir";
constexpr std::string_view kIntegerIndexSubfolderName = "integer_index_dir";
constexpr std::string_view kQualifiedIdJoinIndexSubfolderName =
    "qualified_id_join_index_dir";
constexpr std::string_view kSchemaSubfolderName = "schema_dir";
constexpr std::string_view kSetSchemaMarkerFilename = "set_schema_marker";
constexpr std::string_view kInitMarkerFilename = "init_marker";
constexpr std::string_view kOptimizeStatusFilename = "optimize_status";

// The maximum number of unsuccessful initialization attempts from the current
// state that we will tolerate before deleting all data and starting from a
// fresh state.
constexpr int kMaxUnsuccessfulInitAttempts = 5;

// A pair that holds namespace and type.
struct NamespaceTypePair {
  std::string namespace_;
  std::string type;

  bool operator==(const NamespaceTypePair& other) const {
    return namespace_ == other.namespace_ && type == other.type;
  }
};

struct NamespaceTypePairHasher {
  std::size_t operator()(const NamespaceTypePair& pair) const {
    return std::hash<std::string>()(pair.namespace_) ^
           std::hash<std::string>()(pair.type);
  }
};

libtextclassifier3::Status ValidateResultSpec(
    const DocumentStore* document_store, const ResultSpecProto& result_spec) {
  if (result_spec.num_per_page() < 0) {
    return absl_ports::InvalidArgumentError(
        "ResultSpecProto.num_per_page cannot be negative.");
  }
  if (result_spec.num_total_bytes_per_page_threshold() <= 0) {
    return absl_ports::InvalidArgumentError(
        "ResultSpecProto.num_total_bytes_per_page_threshold cannot be "
        "non-positive.");
  }
  if (result_spec.max_joined_children_per_parent_to_return() < 0) {
    return absl_ports::InvalidArgumentError(
        "ResultSpecProto.max_joined_children_per_parent_to_return cannot be "
        "negative.");
  }
  if (result_spec.num_to_score() <= 0) {
    return absl_ports::InvalidArgumentError(
        "ResultSpecProto.num_to_score cannot be non-positive.");
  }
  // Validate ResultGroupings.
  std::unordered_set<int32_t> unique_entry_ids;
  ResultSpecProto::ResultGroupingType result_grouping_type =
      result_spec.result_group_type();
  for (const ResultSpecProto::ResultGrouping& result_grouping :
       result_spec.result_groupings()) {
    if (result_grouping.max_results() <= 0) {
      return absl_ports::InvalidArgumentError(
          "Cannot specify a result grouping with max results <= 0.");
    }
    for (const ResultSpecProto::ResultGrouping::Entry& entry :
         result_grouping.entry_groupings()) {
      const std::string& name_space = entry.namespace_();
      const std::string& schema = entry.schema();
      auto entry_id_or = document_store->GetResultGroupingEntryId(
          result_grouping_type, name_space, schema);
      if (!entry_id_or.ok()) {
        continue;
      }
      int32_t entry_id = entry_id_or.ValueOrDie();
      if (unique_entry_ids.find(entry_id) != unique_entry_ids.end()) {
        return absl_ports::InvalidArgumentError(
            "Entry Ids must be unique across result groups.");
      }
      unique_entry_ids.insert(entry_id);
    }
  }
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status ValidateSearchSpec(
    const SearchSpecProto& search_spec,
    const PerformanceConfiguration& configuration) {
  if (search_spec.query().size() > configuration.max_query_length) {
    return absl_ports::InvalidArgumentError(
        absl_ports::StrCat("SearchSpecProto.query is longer than the maximum "
                           "allowed query length: ",
                           std::to_string(configuration.max_query_length)));
  }
  // Check that no unknown features have been enabled in the search spec.
  std::unordered_set<Feature> query_features_set = GetQueryFeaturesSet();
  for (const Feature feature : search_spec.enabled_features()) {
    if (query_features_set.find(feature) == query_features_set.end()) {
      return absl_ports::InvalidArgumentError(
          absl_ports::StrCat("Unknown feature in "
                             "SearchSpecProto.enabled_features: ",
                             feature));
    }
  }
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status ValidateSuggestionSpec(
    const SuggestionSpecProto& suggestion_spec,
    const PerformanceConfiguration& configuration) {
  if (suggestion_spec.prefix().empty()) {
    return absl_ports::InvalidArgumentError(
        absl_ports::StrCat("SuggestionSpecProto.prefix is empty!"));
  }
  if (suggestion_spec.scoring_spec().scoring_match_type() ==
      TermMatchType::UNKNOWN) {
    return absl_ports::InvalidArgumentError(
        absl_ports::StrCat("SuggestionSpecProto.term_match_type is unknown!"));
  }
  if (suggestion_spec.num_to_return() <= 0) {
    return absl_ports::InvalidArgumentError(absl_ports::StrCat(
        "SuggestionSpecProto.num_to_return must be positive."));
  }
  if (suggestion_spec.prefix().size() > configuration.max_query_length) {
    return absl_ports::InvalidArgumentError(
        absl_ports::StrCat("SuggestionSpecProto.prefix is longer than the "
                           "maximum allowed prefix length: ",
                           std::to_string(configuration.max_query_length)));
  }
  return libtextclassifier3::Status::OK;
}

bool IsV2QualifiedIdJoinIndexEnabled(const IcingSearchEngineOptions& options) {
  return options.use_new_qualified_id_join_index() &&
         options.document_store_namespace_id_fingerprint();
}

libtextclassifier3::StatusOr<std::unique_ptr<QualifiedIdJoinIndex>>
CreateQualifiedIdJoinIndex(const Filesystem& filesystem,
                           std::string qualified_id_join_index_dir,
                           const IcingSearchEngineOptions& options) {
  if (IsV2QualifiedIdJoinIndexEnabled(options)) {
    // V2
    return QualifiedIdJoinIndexImplV2::Create(
        filesystem, std::move(qualified_id_join_index_dir),
        options.pre_mapping_fbv());
  } else {
    // V1
    // TODO(b/275121148): deprecate this part after rollout v2.
    return QualifiedIdJoinIndexImplV1::Create(
        filesystem, std::move(qualified_id_join_index_dir),
        options.pre_mapping_fbv(), options.use_persistent_hash_map());
  }
}

// Document store files are in a standalone subfolder for easier file
// management. We can delete and recreate the subfolder and not touch/affect
// anything else.
std::string MakeDocumentDirectoryPath(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kDocumentSubfolderName);
}

// Makes a temporary folder path for the document store which will be used
// during full optimization.
std::string MakeDocumentTemporaryDirectoryPath(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kDocumentSubfolderName,
                            "_optimize_tmp");
}

// Index files are in a standalone subfolder because for easier file management.
// We can delete and recreate the subfolder and not touch/affect anything
// else.
std::string MakeIndexDirectoryPath(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kIndexSubfolderName);
}

// Working path for integer index. Integer index is derived from
// PersistentStorage and it will take full ownership of this working path,
// including creation/deletion. See PersistentStorage for more details about
// working path.
std::string MakeIntegerIndexWorkingPath(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kIntegerIndexSubfolderName);
}

// Working path for qualified id join index. It is derived from
// PersistentStorage and it will take full ownership of this working path,
// including creation/deletion. See PersistentStorage for more details about
// working path.
std::string MakeQualifiedIdJoinIndexWorkingPath(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kQualifiedIdJoinIndexSubfolderName);
}

// SchemaStore files are in a standalone subfolder for easier file management.
// We can delete and recreate the subfolder and not touch/affect anything
// else.
std::string MakeSchemaDirectoryPath(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kSchemaSubfolderName);
}

std::string MakeSetSchemaMarkerFilePath(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kSetSchemaMarkerFilename);
}

std::string MakeInitMarkerFilePath(const std::string& base_dir) {
  return absl_ports::StrCat(base_dir, "/", kInitMarkerFilename);
}

void TransformStatus(const libtextclassifier3::Status& internal_status,
                     StatusProto* status_proto) {
  StatusProto::Code code;
  if (!internal_status.ok()) {
    ICING_LOG(WARNING) << "Error: " << internal_status.error_code()
                       << ", Message: " << internal_status.error_message();
  }
  switch (internal_status.CanonicalCode()) {
    case libtextclassifier3::StatusCode::OK:
      code = StatusProto::OK;
      break;
    case libtextclassifier3::StatusCode::DATA_LOSS:
      code = StatusProto::WARNING_DATA_LOSS;
      break;
    case libtextclassifier3::StatusCode::INVALID_ARGUMENT:
      code = StatusProto::INVALID_ARGUMENT;
      break;
    case libtextclassifier3::StatusCode::NOT_FOUND:
      code = StatusProto::NOT_FOUND;
      break;
    case libtextclassifier3::StatusCode::FAILED_PRECONDITION:
      code = StatusProto::FAILED_PRECONDITION;
      break;
    case libtextclassifier3::StatusCode::ABORTED:
      code = StatusProto::ABORTED;
      break;
    case libtextclassifier3::StatusCode::INTERNAL:
      // TODO(b/147699081): Cleanup our internal use of INTERNAL since it
      // doesn't match with what it *should* indicate as described in
      // go/icing-library-apis.
      code = StatusProto::INTERNAL;
      break;
    case libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED:
      // TODO(b/147699081): Note that we don't detect all cases of OUT_OF_SPACE
      // (e.g. if the document log is full). And we use RESOURCE_EXHAUSTED
      // internally to indicate other resources are exhausted (e.g.
      // DocHitInfos) - although none of these are exposed through the API.
      // Consider separating the two cases out more clearly.
      code = StatusProto::OUT_OF_SPACE;
      break;
    case libtextclassifier3::StatusCode::ALREADY_EXISTS:
      code = StatusProto::ALREADY_EXISTS;
      break;
    case libtextclassifier3::StatusCode::CANCELLED:
      [[fallthrough]];
    case libtextclassifier3::StatusCode::UNKNOWN:
      [[fallthrough]];
    case libtextclassifier3::StatusCode::DEADLINE_EXCEEDED:
      [[fallthrough]];
    case libtextclassifier3::StatusCode::PERMISSION_DENIED:
      [[fallthrough]];
    case libtextclassifier3::StatusCode::OUT_OF_RANGE:
      [[fallthrough]];
    case libtextclassifier3::StatusCode::UNIMPLEMENTED:
      [[fallthrough]];
    case libtextclassifier3::StatusCode::UNAVAILABLE:
      [[fallthrough]];
    case libtextclassifier3::StatusCode::UNAUTHENTICATED:
      // Other internal status codes aren't supported externally yet. If it
      // should be supported, add another switch-case above.
      ICING_LOG(ERROR) << "Internal status code "
                       << internal_status.error_code()
                       << " not supported in the external API";
      code = StatusProto::UNKNOWN;
      break;
  }
  status_proto->set_code(code);
  status_proto->set_message(internal_status.error_message());
}

libtextclassifier3::Status RetrieveAndAddDocumentInfo(
    const DocumentStore* document_store, DeleteByQueryResultProto& result_proto,
    std::unordered_map<NamespaceTypePair,
                       DeleteByQueryResultProto::DocumentGroupInfo*,
                       NamespaceTypePairHasher>& info_map,
    DocumentId document_id) {
  ICING_ASSIGN_OR_RETURN(DocumentProto document,
                         document_store->Get(document_id));
  NamespaceTypePair key = {document.namespace_(), document.schema()};
  auto iter = info_map.find(key);
  if (iter == info_map.end()) {
    auto entry = result_proto.add_deleted_documents();
    entry->set_namespace_(std::move(document.namespace_()));
    entry->set_schema(std::move(document.schema()));
    entry->add_uris(std::move(document.uri()));
    info_map[key] = entry;
  } else {
    iter->second->add_uris(std::move(document.uri()));
  }
  return libtextclassifier3::Status::OK;
}

bool ShouldRebuildIndex(const OptimizeStatsProto& optimize_stats,
                        float optimize_rebuild_index_threshold) {
  int num_invalid_documents = optimize_stats.num_deleted_documents() +
                              optimize_stats.num_expired_documents();
  return num_invalid_documents >= optimize_stats.num_original_documents() *
                                      optimize_rebuild_index_threshold;
}

// Useful method to get RankingStrategy if advanced scoring is enabled. When the
// "RelevanceScore" function is used in the advanced scoring expression,
// RankingStrategy will be treated as RELEVANCE_SCORE in order to prepare the
// necessary information needed for calculating relevance score.
libtextclassifier3::StatusOr<ScoringSpecProto::RankingStrategy::Code>
GetRankingStrategyFromScoringSpec(const ScoringSpecProto& scoring_spec) {
  if (scoring_spec.advanced_scoring_expression().empty()) {
    return scoring_spec.rank_by();
  }
  // TODO(b/261474063) The Lexer will be called again when creating the
  // AdvancedScorer instance. Consider refactoring the code to allow the Lexer
  // to be called only once.
  Lexer lexer(scoring_spec.advanced_scoring_expression(),
              Lexer::Language::SCORING);
  ICING_ASSIGN_OR_RETURN(std::vector<Lexer::LexerToken> lexer_tokens,
                         lexer.ExtractTokens());
  for (const Lexer::LexerToken& token : lexer_tokens) {
    if (token.type == Lexer::TokenType::FUNCTION_NAME &&
        token.text == RelevanceScoreFunctionScoreExpression::kFunctionName) {
      return ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE;
    }
  }
  return ScoringSpecProto::RankingStrategy::NONE;
}

}  // namespace

IcingSearchEngine::IcingSearchEngine(const IcingSearchEngineOptions& options,
                                     std::unique_ptr<const JniCache> jni_cache)
    : IcingSearchEngine(options, std::make_unique<Filesystem>(),
                        std::make_unique<IcingFilesystem>(),
                        std::make_unique<Clock>(), std::move(jni_cache)) {}

IcingSearchEngine::IcingSearchEngine(
    IcingSearchEngineOptions options,
    std::unique_ptr<const Filesystem> filesystem,
    std::unique_ptr<const IcingFilesystem> icing_filesystem,
    std::unique_ptr<Clock> clock, std::unique_ptr<const JniCache> jni_cache)
    : options_(std::move(options)),
      filesystem_(std::move(filesystem)),
      icing_filesystem_(std::move(icing_filesystem)),
      clock_(std::move(clock)),
      jni_cache_(std::move(jni_cache)) {
  ICING_VLOG(1) << "Creating IcingSearchEngine in dir: " << options_.base_dir();
}

IcingSearchEngine::~IcingSearchEngine() {
  if (initialized_) {
    if (PersistToDisk(PersistType::FULL).status().code() != StatusProto::OK) {
      ICING_LOG(ERROR)
          << "Error persisting to disk in IcingSearchEngine destructor";
    }
  }
}

InitializeResultProto IcingSearchEngine::Initialize() {
  // This method does both read and write so we need a writer lock. Using two
  // locks (reader and writer) has the chance to be interrupted during
  // switching.
  absl_ports::unique_lock l(&mutex_);
  return InternalInitialize();
}

void IcingSearchEngine::ResetMembers() {
  schema_store_.reset();
  document_store_.reset();
  language_segmenter_.reset();
  normalizer_.reset();
  index_.reset();
  integer_index_.reset();
  qualified_id_join_index_.reset();
}

libtextclassifier3::Status IcingSearchEngine::CheckInitMarkerFile(
    InitializeStatsProto* initialize_stats) {
  // Check to see if the marker file exists and if we've already passed our max
  // number of init attempts.
  std::string marker_filepath = MakeInitMarkerFilePath(options_.base_dir());
  bool file_exists = filesystem_->FileExists(marker_filepath.c_str());
  int network_init_attempts = 0;
  int host_init_attempts = 0;

  // Read the number of previous failed init attempts from the file. If it
  // fails, then just assume the value is zero (the most likely reason for
  // failure would be non-existence because the last init was successful
  // anyways).
  std::unique_ptr<ScopedFd> marker_file_fd = std::make_unique<ScopedFd>(
      filesystem_->OpenForWrite(marker_filepath.c_str()));
  libtextclassifier3::Status status;
  if (file_exists &&
      filesystem_->PRead(marker_file_fd->get(), &network_init_attempts,
                         sizeof(network_init_attempts), /*offset=*/0)) {
    host_init_attempts = GNetworkToHostL(network_init_attempts);
    if (host_init_attempts > kMaxUnsuccessfulInitAttempts) {
      // We're tried and failed to init too many times. We need to throw
      // everything out and start from scratch.
      ResetMembers();
      marker_file_fd.reset();

      // Delete the entire base directory.
      if (!filesystem_->DeleteDirectoryRecursively(
              options_.base_dir().c_str())) {
        return absl_ports::InternalError("Failed to delete icing base dir!");
      }

      // Create the base directory again and reopen marker file.
      if (!filesystem_->CreateDirectoryRecursively(
              options_.base_dir().c_str())) {
        return absl_ports::InternalError("Failed to create icing base dir!");
      }

      marker_file_fd = std::make_unique<ScopedFd>(
          filesystem_->OpenForWrite(marker_filepath.c_str()));

      status = absl_ports::DataLossError(
          "Encountered failed initialization limit. Cleared all data.");
      host_init_attempts = 0;
    }
  }

  // Use network_init_attempts here because we might have set host_init_attempts
  // to 0 if it exceeded the max threshold.
  initialize_stats->set_num_previous_init_failures(
      GNetworkToHostL(network_init_attempts));

  ++host_init_attempts;
  network_init_attempts = GHostToNetworkL(host_init_attempts);
  // Write the updated number of attempts before we get started.
  if (!filesystem_->PWrite(marker_file_fd->get(), /*offset=*/0,
                           &network_init_attempts,
                           sizeof(network_init_attempts)) ||
      !filesystem_->DataSync(marker_file_fd->get())) {
    return absl_ports::InternalError(
        "Failed to write and sync init marker file");
  }

  return status;
}

InitializeResultProto IcingSearchEngine::InternalInitialize() {
  ICING_VLOG(1) << "Initializing IcingSearchEngine in dir: "
                << options_.base_dir();

  // Measure the latency of the initialization process.
  std::unique_ptr<Timer> initialize_timer = clock_->GetNewTimer();

  InitializeResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();
  InitializeStatsProto* initialize_stats =
      result_proto.mutable_initialize_stats();
  if (initialized_) {
    // Already initialized.
    result_status->set_code(StatusProto::OK);
    initialize_stats->set_latency_ms(
        initialize_timer->GetElapsedMilliseconds());
    initialize_stats->set_num_documents(document_store_->num_documents());
    return result_proto;
  }

  // Now go ahead and try to initialize.
  libtextclassifier3::Status status = InitializeMembers(initialize_stats);
  if (status.ok() || absl_ports::IsDataLoss(status)) {
    // We successfully initialized. We should delete the init marker file to
    // indicate a successful init.
    std::string marker_filepath = MakeInitMarkerFilePath(options_.base_dir());
    if (!filesystem_->DeleteFile(marker_filepath.c_str())) {
      status = absl_ports::InternalError("Failed to delete init marker file!");
    } else {
      initialized_ = true;
    }
  }
  TransformStatus(status, result_status);
  initialize_stats->set_latency_ms(initialize_timer->GetElapsedMilliseconds());
  return result_proto;
}

libtextclassifier3::Status IcingSearchEngine::InitializeMembers(
    InitializeStatsProto* initialize_stats) {
  ICING_RETURN_ERROR_IF_NULL(initialize_stats);

  // Make sure the base directory exists
  if (!filesystem_->CreateDirectoryRecursively(options_.base_dir().c_str())) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "Could not create directory: ", options_.base_dir()));
  }

  // Check to see if the marker file exists and if we've already passed our max
  // number of init attempts.
  libtextclassifier3::Status status = CheckInitMarkerFile(initialize_stats);
  if (!status.ok() && !absl_ports::IsDataLoss(status)) {
    return status;
  }

  // Do version and flags compatibility check
  // Read version file, determine the state change and rebuild derived files if
  // needed.
  const std::string index_dir = MakeIndexDirectoryPath(options_.base_dir());
  ICING_ASSIGN_OR_RETURN(
      IcingSearchEngineVersionProto stored_version_proto,
      version_util::ReadVersion(
          *filesystem_, /*version_file_dir=*/options_.base_dir(), index_dir));
  version_util::VersionInfo stored_version_info =
      version_util::GetVersionInfoFromProto(stored_version_proto);
  version_util::StateChange version_state_change =
      version_util::GetVersionStateChange(stored_version_info);

  // Construct icing's current version proto based on the current code version
  IcingSearchEngineVersionProto current_version_proto;
  current_version_proto.set_version(version_util::kVersion);
  current_version_proto.set_max_version(
      std::max(stored_version_info.max_version, version_util::kVersion));
  version_util::AddEnabledFeatures(options_, &current_version_proto);

  // Step 1: If versions are incompatible, migrate schema according to the
  // version state change.
  if (version_state_change != version_util::StateChange::kCompatible) {
    ICING_RETURN_IF_ERROR(SchemaStore::MigrateSchema(
        filesystem_.get(), MakeSchemaDirectoryPath(options_.base_dir()),
        version_state_change, version_util::kVersion));
  }

  // Step 2: Discard derived files that need to be rebuilt
  version_util::DerivedFilesRebuildResult required_derived_files_rebuild =
      version_util::CalculateRequiredDerivedFilesRebuild(stored_version_proto,
                                                         current_version_proto);
  ICING_RETURN_IF_ERROR(DiscardDerivedFiles(required_derived_files_rebuild));

  // Step 3: update version files. We need to update both the V1 and V2
  // version files.
  ICING_RETURN_IF_ERROR(version_util::WriteV1Version(
      *filesystem_, /*version_file_dir=*/options_.base_dir(),
      version_util::GetVersionInfoFromProto(current_version_proto)));
  ICING_RETURN_IF_ERROR(version_util::WriteV2Version(
      *filesystem_, /*version_file_dir=*/options_.base_dir(),
      std::make_unique<IcingSearchEngineVersionProto>(
          std::move(current_version_proto))));

  ICING_RETURN_IF_ERROR(InitializeSchemaStore(initialize_stats));

  // TODO(b/156383798) : Resolve how to specify the locale.
  language_segmenter_factory::SegmenterOptions segmenter_options(
      ULOC_US, jni_cache_.get());
  TC3_ASSIGN_OR_RETURN(language_segmenter_, language_segmenter_factory::Create(
                                                std::move(segmenter_options)));

  TC3_ASSIGN_OR_RETURN(normalizer_,
                       normalizer_factory::Create(options_.max_token_length()));

  std::string marker_filepath =
      MakeSetSchemaMarkerFilePath(options_.base_dir());

  libtextclassifier3::Status index_init_status;
  if (absl_ports::IsNotFound(schema_store_->GetSchema().status())) {
    // The schema was either lost or never set before. Wipe out the doc store
    // and index directories and initialize them from scratch.
    const std::string doc_store_dir =
        MakeDocumentDirectoryPath(options_.base_dir());
    const std::string integer_index_dir =
        MakeIntegerIndexWorkingPath(options_.base_dir());
    const std::string qualified_id_join_index_dir =
        MakeQualifiedIdJoinIndexWorkingPath(options_.base_dir());
    if (!filesystem_->DeleteDirectoryRecursively(doc_store_dir.c_str()) ||
        !filesystem_->DeleteDirectoryRecursively(index_dir.c_str()) ||
        !IntegerIndex::Discard(*filesystem_, integer_index_dir).ok() ||
        !QualifiedIdJoinIndex::Discard(*filesystem_,
                                       qualified_id_join_index_dir)
             .ok()) {
      return absl_ports::InternalError(absl_ports::StrCat(
          "Could not delete directories: ", index_dir, ", ", integer_index_dir,
          ", ", qualified_id_join_index_dir, " and ", doc_store_dir));
    }
    ICING_ASSIGN_OR_RETURN(
        bool document_store_derived_files_regenerated,
        InitializeDocumentStore(
            /*force_recovery_and_revalidate_documents=*/false,
            initialize_stats));
    index_init_status = InitializeIndex(
        document_store_derived_files_regenerated, initialize_stats);
    if (!index_init_status.ok() && !absl_ports::IsDataLoss(index_init_status)) {
      return index_init_status;
    }
  } else if (filesystem_->FileExists(marker_filepath.c_str())) {
    // If the marker file is still around then something wonky happened when we
    // last tried to set the schema.
    //
    // Since we're going to rebuild all indices in this case, the return value
    // of InitializeDocumentStore (document_store_derived_files_regenerated) is
    // unused.
    ICING_RETURN_IF_ERROR(InitializeDocumentStore(
        /*force_recovery_and_revalidate_documents=*/true, initialize_stats));

    // We're going to need to build the index from scratch. So just delete its
    // directory now.
    // Discard index directory and instantiate a new one.
    Index::Options index_options(index_dir, options_.index_merge_size(),
                                 options_.lite_index_sort_at_indexing(),
                                 options_.lite_index_sort_size());
    if (!filesystem_->DeleteDirectoryRecursively(index_dir.c_str()) ||
        !filesystem_->CreateDirectoryRecursively(index_dir.c_str())) {
      return absl_ports::InternalError(
          absl_ports::StrCat("Could not recreate directory: ", index_dir));
    }
    ICING_ASSIGN_OR_RETURN(index_,
                           Index::Create(index_options, filesystem_.get(),
                                         icing_filesystem_.get()));

    // Discard integer index directory and instantiate a new one.
    std::string integer_index_dir =
        MakeIntegerIndexWorkingPath(options_.base_dir());
    ICING_RETURN_IF_ERROR(
        IntegerIndex::Discard(*filesystem_, integer_index_dir));
    ICING_ASSIGN_OR_RETURN(
        integer_index_,
        IntegerIndex::Create(*filesystem_, std::move(integer_index_dir),
                             options_.integer_index_bucket_split_threshold(),
                             options_.pre_mapping_fbv()));

    // Discard qualified id join index directory and instantiate a new one.
    std::string qualified_id_join_index_dir =
        MakeQualifiedIdJoinIndexWorkingPath(options_.base_dir());
    ICING_RETURN_IF_ERROR(QualifiedIdJoinIndex::Discard(
        *filesystem_, qualified_id_join_index_dir));
    ICING_ASSIGN_OR_RETURN(
        qualified_id_join_index_,
        CreateQualifiedIdJoinIndex(
            *filesystem_, std::move(qualified_id_join_index_dir), options_));

    std::unique_ptr<Timer> restore_timer = clock_->GetNewTimer();
    IndexRestorationResult restore_result = RestoreIndexIfNeeded();
    index_init_status = std::move(restore_result.status);
    // DATA_LOSS means that we have successfully initialized and re-added
    // content to the index. Some indexed content was lost, but otherwise the
    // index is in a valid state and can be queried.
    if (!index_init_status.ok() && !absl_ports::IsDataLoss(index_init_status)) {
      return index_init_status;
    }

    // Delete the marker file to indicate that everything is now in sync with
    // whatever changes were made to the schema.
    filesystem_->DeleteFile(marker_filepath.c_str());

    initialize_stats->set_index_restoration_latency_ms(
        restore_timer->GetElapsedMilliseconds());
    initialize_stats->set_index_restoration_cause(
        InitializeStatsProto::SCHEMA_CHANGES_OUT_OF_SYNC);
    initialize_stats->set_integer_index_restoration_cause(
        InitializeStatsProto::SCHEMA_CHANGES_OUT_OF_SYNC);
    initialize_stats->set_qualified_id_join_index_restoration_cause(
        InitializeStatsProto::SCHEMA_CHANGES_OUT_OF_SYNC);
  } else if (version_state_change != version_util::StateChange::kCompatible) {
    ICING_ASSIGN_OR_RETURN(bool document_store_derived_files_regenerated,
                           InitializeDocumentStore(
                               /*force_recovery_and_revalidate_documents=*/true,
                               initialize_stats));
    index_init_status = InitializeIndex(
        document_store_derived_files_regenerated, initialize_stats);
    if (!index_init_status.ok() && !absl_ports::IsDataLoss(index_init_status)) {
      return index_init_status;
    }

    initialize_stats->set_schema_store_recovery_cause(
        InitializeStatsProto::VERSION_CHANGED);
    initialize_stats->set_document_store_recovery_cause(
        InitializeStatsProto::VERSION_CHANGED);
    initialize_stats->set_index_restoration_cause(
        InitializeStatsProto::VERSION_CHANGED);
    initialize_stats->set_integer_index_restoration_cause(
        InitializeStatsProto::VERSION_CHANGED);
    initialize_stats->set_qualified_id_join_index_restoration_cause(
        InitializeStatsProto::VERSION_CHANGED);
  } else {
    ICING_ASSIGN_OR_RETURN(
        bool document_store_derived_files_regenerated,
        InitializeDocumentStore(
            /*force_recovery_and_revalidate_documents=*/false,
            initialize_stats));
    index_init_status = InitializeIndex(
        document_store_derived_files_regenerated, initialize_stats);
    if (!index_init_status.ok() && !absl_ports::IsDataLoss(index_init_status)) {
      return index_init_status;
    }

    // Set recovery cause to FEATURE_FLAG_CHANGED according to the calculated
    // required_derived_files_rebuild
    if (required_derived_files_rebuild
            .needs_document_store_derived_files_rebuild) {
      initialize_stats->set_document_store_recovery_cause(
          InitializeStatsProto::FEATURE_FLAG_CHANGED);
    }
    if (required_derived_files_rebuild
            .needs_schema_store_derived_files_rebuild) {
      initialize_stats->set_schema_store_recovery_cause(
          InitializeStatsProto::FEATURE_FLAG_CHANGED);
    }
    if (required_derived_files_rebuild.needs_term_index_rebuild) {
      initialize_stats->set_index_restoration_cause(
          InitializeStatsProto::FEATURE_FLAG_CHANGED);
    }
    if (required_derived_files_rebuild.needs_integer_index_rebuild) {
      initialize_stats->set_integer_index_restoration_cause(
          InitializeStatsProto::FEATURE_FLAG_CHANGED);
    }
    if (required_derived_files_rebuild.needs_qualified_id_join_index_rebuild) {
      initialize_stats->set_qualified_id_join_index_restoration_cause(
          InitializeStatsProto::FEATURE_FLAG_CHANGED);
    }
  }

  if (status.ok()) {
    status = index_init_status;
  }

  result_state_manager_ = std::make_unique<ResultStateManager>(
      performance_configuration_.max_num_total_hits, *document_store_);

  return status;
}

libtextclassifier3::Status IcingSearchEngine::InitializeSchemaStore(
    InitializeStatsProto* initialize_stats) {
  ICING_RETURN_ERROR_IF_NULL(initialize_stats);

  const std::string schema_store_dir =
      MakeSchemaDirectoryPath(options_.base_dir());
  // Make sure the sub-directory exists
  if (!filesystem_->CreateDirectoryRecursively(schema_store_dir.c_str())) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Could not create directory: ", schema_store_dir));
  }
  ICING_ASSIGN_OR_RETURN(
      schema_store_, SchemaStore::Create(filesystem_.get(), schema_store_dir,
                                         clock_.get(), initialize_stats));

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<bool> IcingSearchEngine::InitializeDocumentStore(
    bool force_recovery_and_revalidate_documents,
    InitializeStatsProto* initialize_stats) {
  ICING_RETURN_ERROR_IF_NULL(initialize_stats);

  const std::string document_dir =
      MakeDocumentDirectoryPath(options_.base_dir());
  // Make sure the sub-directory exists
  if (!filesystem_->CreateDirectoryRecursively(document_dir.c_str())) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Could not create directory: ", document_dir));
  }
  ICING_ASSIGN_OR_RETURN(
      DocumentStore::CreateResult create_result,
      DocumentStore::Create(
          filesystem_.get(), document_dir, clock_.get(), schema_store_.get(),
          force_recovery_and_revalidate_documents,
          options_.document_store_namespace_id_fingerprint(),
          options_.pre_mapping_fbv(), options_.use_persistent_hash_map(),
          options_.compression_level(), initialize_stats));
  document_store_ = std::move(create_result.document_store);

  return create_result.derived_files_regenerated;
}

libtextclassifier3::Status IcingSearchEngine::InitializeIndex(
    bool document_store_derived_files_regenerated,
    InitializeStatsProto* initialize_stats) {
  ICING_RETURN_ERROR_IF_NULL(initialize_stats);

  const std::string index_dir = MakeIndexDirectoryPath(options_.base_dir());
  // Make sure the sub-directory exists
  if (!filesystem_->CreateDirectoryRecursively(index_dir.c_str())) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Could not create directory: ", index_dir));
  }
  Index::Options index_options(index_dir, options_.index_merge_size(),
                               options_.lite_index_sort_at_indexing(),
                               options_.lite_index_sort_size());

  // Term index
  InitializeStatsProto::RecoveryCause index_recovery_cause;
  auto index_or =
      Index::Create(index_options, filesystem_.get(), icing_filesystem_.get());
  if (!index_or.ok()) {
    if (!filesystem_->DeleteDirectoryRecursively(index_dir.c_str()) ||
        !filesystem_->CreateDirectoryRecursively(index_dir.c_str())) {
      return absl_ports::InternalError(
          absl_ports::StrCat("Could not recreate directory: ", index_dir));
    }

    index_recovery_cause = InitializeStatsProto::IO_ERROR;

    // Try recreating it from scratch and re-indexing everything.
    ICING_ASSIGN_OR_RETURN(index_,
                           Index::Create(index_options, filesystem_.get(),
                                         icing_filesystem_.get()));
  } else {
    // Index was created fine.
    index_ = std::move(index_or).ValueOrDie();
    // If a recover does have to happen, then it must be because the index is
    // out of sync with the document store.
    index_recovery_cause = InitializeStatsProto::INCONSISTENT_WITH_GROUND_TRUTH;
  }

  // Integer index
  std::string integer_index_dir =
      MakeIntegerIndexWorkingPath(options_.base_dir());
  InitializeStatsProto::RecoveryCause integer_index_recovery_cause;
  auto integer_index_or =
      IntegerIndex::Create(*filesystem_, integer_index_dir,
                           options_.integer_index_bucket_split_threshold(),
                           options_.pre_mapping_fbv());
  if (!integer_index_or.ok()) {
    ICING_RETURN_IF_ERROR(
        IntegerIndex::Discard(*filesystem_, integer_index_dir));

    integer_index_recovery_cause = InitializeStatsProto::IO_ERROR;

    // Try recreating it from scratch and re-indexing everything.
    ICING_ASSIGN_OR_RETURN(
        integer_index_,
        IntegerIndex::Create(*filesystem_, std::move(integer_index_dir),
                             options_.integer_index_bucket_split_threshold(),
                             options_.pre_mapping_fbv()));
  } else {
    // Integer index was created fine.
    integer_index_ = std::move(integer_index_or).ValueOrDie();
    // If a recover does have to happen, then it must be because the index is
    // out of sync with the document store.
    integer_index_recovery_cause =
        InitializeStatsProto::INCONSISTENT_WITH_GROUND_TRUTH;
  }

  // Qualified id join index
  std::string qualified_id_join_index_dir =
      MakeQualifiedIdJoinIndexWorkingPath(options_.base_dir());
  InitializeStatsProto::RecoveryCause qualified_id_join_index_recovery_cause;
  if (document_store_derived_files_regenerated &&
      IsV2QualifiedIdJoinIndexEnabled(options_)) {
    // V2 qualified id join index depends on document store derived files, so we
    // have to rebuild it from scratch if
    // document_store_derived_files_regenerated is true.
    ICING_RETURN_IF_ERROR(QualifiedIdJoinIndex::Discard(
        *filesystem_, qualified_id_join_index_dir));

    ICING_ASSIGN_OR_RETURN(
        qualified_id_join_index_,
        CreateQualifiedIdJoinIndex(
            *filesystem_, std::move(qualified_id_join_index_dir), options_));

    qualified_id_join_index_recovery_cause =
        InitializeStatsProto::DEPENDENCIES_CHANGED;
  } else {
    auto qualified_id_join_index_or = CreateQualifiedIdJoinIndex(
        *filesystem_, qualified_id_join_index_dir, options_);
    if (!qualified_id_join_index_or.ok()) {
      ICING_RETURN_IF_ERROR(QualifiedIdJoinIndex::Discard(
          *filesystem_, qualified_id_join_index_dir));

      qualified_id_join_index_recovery_cause = InitializeStatsProto::IO_ERROR;

      // Try recreating it from scratch and rebuild everything.
      ICING_ASSIGN_OR_RETURN(
          qualified_id_join_index_,
          CreateQualifiedIdJoinIndex(
              *filesystem_, std::move(qualified_id_join_index_dir), options_));
    } else {
      // Qualified id join index was created fine.
      qualified_id_join_index_ =
          std::move(qualified_id_join_index_or).ValueOrDie();
      // If a recover does have to happen, then it must be because the index is
      // out of sync with the document store.
      qualified_id_join_index_recovery_cause =
          InitializeStatsProto::INCONSISTENT_WITH_GROUND_TRUTH;
    }
  }

  std::unique_ptr<Timer> restore_timer = clock_->GetNewTimer();
  IndexRestorationResult restore_result = RestoreIndexIfNeeded();
  if (restore_result.index_needed_restoration ||
      restore_result.integer_index_needed_restoration ||
      restore_result.qualified_id_join_index_needed_restoration) {
    initialize_stats->set_index_restoration_latency_ms(
        restore_timer->GetElapsedMilliseconds());

    if (restore_result.index_needed_restoration) {
      initialize_stats->set_index_restoration_cause(index_recovery_cause);
    }
    if (restore_result.integer_index_needed_restoration) {
      initialize_stats->set_integer_index_restoration_cause(
          integer_index_recovery_cause);
    }
    if (restore_result.qualified_id_join_index_needed_restoration) {
      initialize_stats->set_qualified_id_join_index_restoration_cause(
          qualified_id_join_index_recovery_cause);
    }
  }
  return restore_result.status;
}

SetSchemaResultProto IcingSearchEngine::SetSchema(
    const SchemaProto& new_schema, bool ignore_errors_and_delete_documents) {
  return SetSchema(SchemaProto(new_schema), ignore_errors_and_delete_documents);
}

SetSchemaResultProto IcingSearchEngine::SetSchema(
    SchemaProto&& new_schema, bool ignore_errors_and_delete_documents) {
  ICING_VLOG(1) << "Setting new Schema";

  SetSchemaResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();

  absl_ports::unique_lock l(&mutex_);
  ScopedTimer timer(clock_->GetNewTimer(), [&result_proto](int64_t t) {
    result_proto.set_latency_ms(t);
  });
  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return result_proto;
  }

  auto lost_previous_schema_or = LostPreviousSchema();
  if (!lost_previous_schema_or.ok()) {
    TransformStatus(lost_previous_schema_or.status(), result_status);
    return result_proto;
  }
  bool lost_previous_schema = lost_previous_schema_or.ValueOrDie();

  std::string marker_filepath =
      MakeSetSchemaMarkerFilePath(options_.base_dir());
  // Create the marker file indicating that we are going to apply a schema
  // change. No need to write anything to the marker file - its existence is the
  // only thing that matters. The marker file is used to indicate if we
  // encountered a crash or a power loss while updating the schema and other
  // files. So set it up to be deleted as long as we return from this function.
  DestructibleFile marker_file(marker_filepath, filesystem_.get());

  auto set_schema_result_or = schema_store_->SetSchema(
      std::move(new_schema), ignore_errors_and_delete_documents,
      options_.allow_circular_schema_definitions());
  if (!set_schema_result_or.ok()) {
    TransformStatus(set_schema_result_or.status(), result_status);
    return result_proto;
  }
  SchemaStore::SetSchemaResult set_schema_result =
      std::move(set_schema_result_or).ValueOrDie();

  for (const std::string& deleted_type :
       set_schema_result.schema_types_deleted_by_name) {
    result_proto.add_deleted_schema_types(deleted_type);
  }

  for (const std::string& incompatible_type :
       set_schema_result.schema_types_incompatible_by_name) {
    result_proto.add_incompatible_schema_types(incompatible_type);
  }

  for (const std::string& new_type :
       set_schema_result.schema_types_new_by_name) {
    result_proto.add_new_schema_types(std::move(new_type));
  }

  for (const std::string& compatible_type :
       set_schema_result.schema_types_changed_fully_compatible_by_name) {
    result_proto.add_fully_compatible_changed_schema_types(
        std::move(compatible_type));
  }

  bool index_incompatible =
      !set_schema_result.schema_types_index_incompatible_by_name.empty();
  for (const std::string& index_incompatible_type :
       set_schema_result.schema_types_index_incompatible_by_name) {
    result_proto.add_index_incompatible_changed_schema_types(
        std::move(index_incompatible_type));
  }

  bool join_incompatible =
      !set_schema_result.schema_types_join_incompatible_by_name.empty();
  for (const std::string& join_incompatible_type :
       set_schema_result.schema_types_join_incompatible_by_name) {
    result_proto.add_join_incompatible_changed_schema_types(
        std::move(join_incompatible_type));
  }

  libtextclassifier3::Status status;
  if (set_schema_result.success) {
    if (lost_previous_schema) {
      // No previous schema to calculate a diff against. We have to go through
      // and revalidate all the Documents in the DocumentStore
      status = document_store_->UpdateSchemaStore(schema_store_.get());
      if (!status.ok()) {
        TransformStatus(status, result_status);
        return result_proto;
      }
    } else if (!set_schema_result.old_schema_type_ids_changed.empty() ||
               !set_schema_result.schema_types_incompatible_by_id.empty() ||
               !set_schema_result.schema_types_deleted_by_id.empty()) {
      status = document_store_->OptimizedUpdateSchemaStore(schema_store_.get(),
                                                           set_schema_result);
      if (!status.ok()) {
        TransformStatus(status, result_status);
        return result_proto;
      }
    }

    if (lost_previous_schema || index_incompatible) {
      // Clears search indices
      status = ClearSearchIndices();
      if (!status.ok()) {
        TransformStatus(status, result_status);
        return result_proto;
      }
    }

    if (lost_previous_schema || join_incompatible) {
      // Clears join indices
      status = ClearJoinIndices();
      if (!status.ok()) {
        TransformStatus(status, result_status);
        return result_proto;
      }
    }

    if (lost_previous_schema || index_incompatible || join_incompatible) {
      IndexRestorationResult restore_result = RestoreIndexIfNeeded();
      // DATA_LOSS means that we have successfully re-added content to the
      // index. Some indexed content was lost, but otherwise the index is in a
      // valid state and can be queried.
      if (!restore_result.status.ok() &&
          !absl_ports::IsDataLoss(restore_result.status)) {
        TransformStatus(status, result_status);
        return result_proto;
      }
    }

    result_status->set_code(StatusProto::OK);
  } else {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("Schema is incompatible.");
  }

  return result_proto;
}

GetSchemaResultProto IcingSearchEngine::GetSchema() {
  GetSchemaResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();

  absl_ports::shared_lock l(&mutex_);
  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return result_proto;
  }

  auto schema_or = schema_store_->GetSchema();
  if (!schema_or.ok()) {
    TransformStatus(schema_or.status(), result_status);
    return result_proto;
  }

  result_status->set_code(StatusProto::OK);
  *result_proto.mutable_schema() = *std::move(schema_or).ValueOrDie();
  return result_proto;
}

GetSchemaTypeResultProto IcingSearchEngine::GetSchemaType(
    std::string_view schema_type) {
  GetSchemaTypeResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();

  absl_ports::shared_lock l(&mutex_);
  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return result_proto;
  }

  auto type_config_or = schema_store_->GetSchemaTypeConfig(schema_type);
  if (!type_config_or.ok()) {
    TransformStatus(type_config_or.status(), result_status);
    return result_proto;
  }

  result_status->set_code(StatusProto::OK);
  *result_proto.mutable_schema_type_config() = *(type_config_or.ValueOrDie());
  return result_proto;
}

PutResultProto IcingSearchEngine::Put(const DocumentProto& document) {
  return Put(DocumentProto(document));
}

PutResultProto IcingSearchEngine::Put(DocumentProto&& document) {
  ICING_VLOG(1) << "Writing document to document store";

  PutResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();
  PutDocumentStatsProto* put_document_stats =
      result_proto.mutable_put_document_stats();
  ScopedTimer put_timer(clock_->GetNewTimer(), [put_document_stats](int64_t t) {
    put_document_stats->set_latency_ms(t);
  });

  // Lock must be acquired before validation because the DocumentStore uses
  // the schema file to validate, and the schema could be changed in
  // SetSchema() which is protected by the same mutex.
  absl_ports::unique_lock l(&mutex_);
  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return result_proto;
  }

  auto tokenized_document_or = TokenizedDocument::Create(
      schema_store_.get(), language_segmenter_.get(), std::move(document));
  if (!tokenized_document_or.ok()) {
    TransformStatus(tokenized_document_or.status(), result_status);
    return result_proto;
  }
  TokenizedDocument tokenized_document(
      std::move(tokenized_document_or).ValueOrDie());

  auto document_id_or = document_store_->Put(
      tokenized_document.document(), tokenized_document.num_string_tokens(),
      put_document_stats);
  if (!document_id_or.ok()) {
    TransformStatus(document_id_or.status(), result_status);
    return result_proto;
  }
  DocumentId document_id = document_id_or.ValueOrDie();

  auto data_indexing_handlers_or = CreateDataIndexingHandlers();
  if (!data_indexing_handlers_or.ok()) {
    TransformStatus(data_indexing_handlers_or.status(), result_status);
    return result_proto;
  }
  IndexProcessor index_processor(
      std::move(data_indexing_handlers_or).ValueOrDie(), clock_.get());

  auto index_status = index_processor.IndexDocument(
      tokenized_document, document_id, put_document_stats);
  // Getting an internal error from the index could possibly mean that the index
  // is broken. Try to rebuild them to recover.
  if (absl_ports::IsInternal(index_status)) {
    ICING_LOG(ERROR) << "Got an internal error from the index. Trying to "
                        "rebuild the index!\n"
                     << index_status.error_message();
    index_status = ClearAllIndices();
    if (index_status.ok()) {
      index_status = RestoreIndexIfNeeded().status;
      if (!index_status.ok()) {
        ICING_LOG(ERROR) << "Failed to reindex documents after a failure of "
                            "indexing a document.";
      }
    } else {
      ICING_LOG(ERROR)
          << "Failed to clear indices after a failure of indexing a document.";
    }
  }

  if (!index_status.ok()) {
    // If we encountered a failure or cannot resolve an internal error while
    // indexing this document, then mark it as deleted.
    int64_t current_time_ms = clock_->GetSystemTimeMilliseconds();
    libtextclassifier3::Status delete_status =
        document_store_->Delete(document_id, current_time_ms);
    if (!delete_status.ok()) {
      // This is pretty dire (and, hopefully, unlikely). We can't roll back the
      // document that we just added. Wipeout the whole index.
      ICING_LOG(ERROR) << "Cannot delete the document that is failed to index. "
                          "Wiping out the whole Icing search engine.";
      ResetInternal();
    }
  }

  TransformStatus(index_status, result_status);
  return result_proto;
}

GetResultProto IcingSearchEngine::Get(const std::string_view name_space,
                                      const std::string_view uri,
                                      const GetResultSpecProto& result_spec) {
  GetResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();

  absl_ports::shared_lock l(&mutex_);
  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return result_proto;
  }

  auto document_or = document_store_->Get(name_space, uri);
  if (!document_or.ok()) {
    TransformStatus(document_or.status(), result_status);
    return result_proto;
  }

  DocumentProto document = std::move(document_or).ValueOrDie();
  std::unique_ptr<ProjectionTree> type_projection_tree;
  std::unique_ptr<ProjectionTree> wildcard_projection_tree;
  for (const SchemaStore::ExpandedTypePropertyMask& type_field_mask :
       schema_store_->ExpandTypePropertyMasks(
           result_spec.type_property_masks())) {
    if (type_field_mask.schema_type == document.schema()) {
      type_projection_tree = std::make_unique<ProjectionTree>(type_field_mask);
    } else if (type_field_mask.schema_type ==
               SchemaStore::kSchemaTypeWildcard) {
      wildcard_projection_tree =
          std::make_unique<ProjectionTree>(type_field_mask);
    }
  }

  // Apply projection
  if (type_projection_tree != nullptr) {
    projector::Project(type_projection_tree->root().children, &document);
  } else if (wildcard_projection_tree != nullptr) {
    projector::Project(wildcard_projection_tree->root().children, &document);
  }

  result_status->set_code(StatusProto::OK);
  *result_proto.mutable_document() = std::move(document);
  return result_proto;
}

ReportUsageResultProto IcingSearchEngine::ReportUsage(
    const UsageReport& usage_report) {
  ReportUsageResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();

  absl_ports::unique_lock l(&mutex_);
  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return result_proto;
  }

  libtextclassifier3::Status status =
      document_store_->ReportUsage(usage_report);
  TransformStatus(status, result_status);
  return result_proto;
}

GetAllNamespacesResultProto IcingSearchEngine::GetAllNamespaces() {
  GetAllNamespacesResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();

  absl_ports::shared_lock l(&mutex_);
  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return result_proto;
  }

  std::vector<std::string> namespaces = document_store_->GetAllNamespaces();

  for (const std::string& namespace_ : namespaces) {
    result_proto.add_namespaces(namespace_);
  }

  result_status->set_code(StatusProto::OK);
  return result_proto;
}

DeleteResultProto IcingSearchEngine::Delete(const std::string_view name_space,
                                            const std::string_view uri) {
  ICING_VLOG(1) << "Deleting document from doc store";

  DeleteResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();

  absl_ports::unique_lock l(&mutex_);
  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return result_proto;
  }

  DeleteStatsProto* delete_stats = result_proto.mutable_delete_stats();
  delete_stats->set_delete_type(DeleteStatsProto::DeleteType::SINGLE);

  std::unique_ptr<Timer> delete_timer = clock_->GetNewTimer();
  // TODO(b/216487496): Implement a more robust version of TC_RETURN_IF_ERROR
  // that can support error logging.
  int64_t current_time_ms = clock_->GetSystemTimeMilliseconds();
  libtextclassifier3::Status status =
      document_store_->Delete(name_space, uri, current_time_ms);
  if (!status.ok()) {
    LogSeverity::Code severity = ERROR;
    if (absl_ports::IsNotFound(status)) {
      severity = DBG;
    }
    ICING_LOG(severity) << status.error_message()
                        << "Failed to delete Document. namespace: "
                        << name_space << ", uri: " << uri;
    TransformStatus(status, result_status);
    return result_proto;
  }

  result_status->set_code(StatusProto::OK);
  delete_stats->set_latency_ms(delete_timer->GetElapsedMilliseconds());
  delete_stats->set_num_documents_deleted(1);
  return result_proto;
}

DeleteByNamespaceResultProto IcingSearchEngine::DeleteByNamespace(
    const std::string_view name_space) {
  ICING_VLOG(1) << "Deleting namespace from doc store";

  DeleteByNamespaceResultProto delete_result;
  StatusProto* result_status = delete_result.mutable_status();
  absl_ports::unique_lock l(&mutex_);
  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return delete_result;
  }

  DeleteStatsProto* delete_stats = delete_result.mutable_delete_stats();
  delete_stats->set_delete_type(DeleteStatsProto::DeleteType::NAMESPACE);

  std::unique_ptr<Timer> delete_timer = clock_->GetNewTimer();
  // TODO(b/216487496): Implement a more robust version of TC_RETURN_IF_ERROR
  // that can support error logging.
  DocumentStore::DeleteByGroupResult doc_store_result =
      document_store_->DeleteByNamespace(name_space);
  if (!doc_store_result.status.ok()) {
    ICING_LOG(ERROR) << doc_store_result.status.error_message()
                     << "Failed to delete Namespace: " << name_space;
    TransformStatus(doc_store_result.status, result_status);
    return delete_result;
  }

  result_status->set_code(StatusProto::OK);
  delete_stats->set_latency_ms(delete_timer->GetElapsedMilliseconds());
  delete_stats->set_num_documents_deleted(doc_store_result.num_docs_deleted);
  return delete_result;
}

DeleteBySchemaTypeResultProto IcingSearchEngine::DeleteBySchemaType(
    const std::string_view schema_type) {
  ICING_VLOG(1) << "Deleting type from doc store";

  DeleteBySchemaTypeResultProto delete_result;
  StatusProto* result_status = delete_result.mutable_status();
  absl_ports::unique_lock l(&mutex_);
  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return delete_result;
  }

  DeleteStatsProto* delete_stats = delete_result.mutable_delete_stats();
  delete_stats->set_delete_type(DeleteStatsProto::DeleteType::SCHEMA_TYPE);

  std::unique_ptr<Timer> delete_timer = clock_->GetNewTimer();
  // TODO(b/216487496): Implement a more robust version of TC_RETURN_IF_ERROR
  // that can support error logging.
  DocumentStore::DeleteByGroupResult doc_store_result =
      document_store_->DeleteBySchemaType(schema_type);
  if (!doc_store_result.status.ok()) {
    ICING_LOG(ERROR) << doc_store_result.status.error_message()
                     << "Failed to delete SchemaType: " << schema_type;
    TransformStatus(doc_store_result.status, result_status);
    return delete_result;
  }

  result_status->set_code(StatusProto::OK);
  delete_stats->set_latency_ms(delete_timer->GetElapsedMilliseconds());
  delete_stats->set_num_documents_deleted(doc_store_result.num_docs_deleted);
  return delete_result;
}

DeleteByQueryResultProto IcingSearchEngine::DeleteByQuery(
    const SearchSpecProto& search_spec, bool return_deleted_document_info) {
  ICING_VLOG(1) << "Deleting documents for query " << search_spec.query()
                << " from doc store";

  DeleteByQueryResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();

  absl_ports::unique_lock l(&mutex_);
  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return result_proto;
  }

  DeleteByQueryStatsProto* delete_stats =
      result_proto.mutable_delete_by_query_stats();
  delete_stats->set_query_length(search_spec.query().length());
  delete_stats->set_num_namespaces_filtered(
      search_spec.namespace_filters_size());
  delete_stats->set_num_schema_types_filtered(
      search_spec.schema_type_filters_size());

  ScopedTimer delete_timer(clock_->GetNewTimer(), [delete_stats](int64_t t) {
    delete_stats->set_latency_ms(t);
  });
  libtextclassifier3::Status status =
      ValidateSearchSpec(search_spec, performance_configuration_);
  if (!status.ok()) {
    TransformStatus(status, result_status);
    return result_proto;
  }

  std::unique_ptr<Timer> component_timer = clock_->GetNewTimer();
  // Gets unordered results from query processor
  auto query_processor_or = QueryProcessor::Create(
      index_.get(), integer_index_.get(), language_segmenter_.get(),
      normalizer_.get(), document_store_.get(), schema_store_.get(),
      clock_.get());
  if (!query_processor_or.ok()) {
    TransformStatus(query_processor_or.status(), result_status);
    delete_stats->set_parse_query_latency_ms(
        component_timer->GetElapsedMilliseconds());
    return result_proto;
  }
  std::unique_ptr<QueryProcessor> query_processor =
      std::move(query_processor_or).ValueOrDie();

  int64_t current_time_ms = clock_->GetSystemTimeMilliseconds();
  auto query_results_or = query_processor->ParseSearch(
      search_spec, ScoringSpecProto::RankingStrategy::NONE, current_time_ms);
  if (!query_results_or.ok()) {
    TransformStatus(query_results_or.status(), result_status);
    delete_stats->set_parse_query_latency_ms(
        component_timer->GetElapsedMilliseconds());
    return result_proto;
  }
  QueryResults query_results = std::move(query_results_or).ValueOrDie();
  delete_stats->set_parse_query_latency_ms(
      component_timer->GetElapsedMilliseconds());

  ICING_VLOG(2) << "Deleting the docs that matched the query.";
  int num_deleted = 0;
  // A map used to group deleted documents.
  // From the (namespace, type) pair to a list of uris.
  std::unordered_map<NamespaceTypePair,
                     DeleteByQueryResultProto::DocumentGroupInfo*,
                     NamespaceTypePairHasher>
      deleted_info_map;

  component_timer = clock_->GetNewTimer();
  while (query_results.root_iterator->Advance().ok()) {
    ICING_VLOG(3) << "Deleting doc "
                  << query_results.root_iterator->doc_hit_info().document_id();
    ++num_deleted;
    if (return_deleted_document_info) {
      status = RetrieveAndAddDocumentInfo(
          document_store_.get(), result_proto, deleted_info_map,
          query_results.root_iterator->doc_hit_info().document_id());
      if (!status.ok()) {
        TransformStatus(status, result_status);
        delete_stats->set_document_removal_latency_ms(
            component_timer->GetElapsedMilliseconds());
        return result_proto;
      }
    }
    status = document_store_->Delete(
        query_results.root_iterator->doc_hit_info().document_id(),
        current_time_ms);
    if (!status.ok()) {
      TransformStatus(status, result_status);
      delete_stats->set_document_removal_latency_ms(
          component_timer->GetElapsedMilliseconds());
      return result_proto;
    }
  }
  delete_stats->set_document_removal_latency_ms(
      component_timer->GetElapsedMilliseconds());
  int term_count = 0;
  for (const auto& section_and_terms : query_results.query_terms) {
    term_count += section_and_terms.second.size();
  }
  delete_stats->set_num_terms(term_count);

  if (num_deleted > 0) {
    result_proto.mutable_status()->set_code(StatusProto::OK);
  } else {
    result_proto.mutable_status()->set_code(StatusProto::NOT_FOUND);
    result_proto.mutable_status()->set_message(
        "No documents matched the query to delete by!");
  }
  delete_stats->set_num_documents_deleted(num_deleted);
  return result_proto;
}

PersistToDiskResultProto IcingSearchEngine::PersistToDisk(
    PersistType::Code persist_type) {
  ICING_VLOG(1) << "Persisting data to disk";

  PersistToDiskResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();

  absl_ports::unique_lock l(&mutex_);
  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return result_proto;
  }

  auto status = InternalPersistToDisk(persist_type);
  TransformStatus(status, result_status);
  return result_proto;
}

// Optimizes Icing's storage
//
// Steps:
// 1. Flush data to disk.
// 2. Copy data needed to a tmp directory.
// 3. Swap current directory and tmp directory.
OptimizeResultProto IcingSearchEngine::Optimize() {
  ICING_VLOG(1) << "Optimizing icing storage";

  OptimizeResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();

  absl_ports::unique_lock l(&mutex_);
  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return result_proto;
  }

  OptimizeStatsProto* optimize_stats = result_proto.mutable_optimize_stats();
  ScopedTimer optimize_timer(
      clock_->GetNewTimer(),
      [optimize_stats](int64_t t) { optimize_stats->set_latency_ms(t); });

  // Flushes data to disk before doing optimization
  auto status = InternalPersistToDisk(PersistType::FULL);
  if (!status.ok()) {
    TransformStatus(status, result_status);
    return result_proto;
  }

  int64_t before_size = filesystem_->GetDiskUsage(options_.base_dir().c_str());
  optimize_stats->set_storage_size_before(
      Filesystem::SanitizeFileSize(before_size));

  // TODO(b/143646633): figure out if we need to optimize index and doc store
  // at the same time.
  std::unique_ptr<Timer> optimize_doc_store_timer = clock_->GetNewTimer();
  libtextclassifier3::StatusOr<DocumentStore::OptimizeResult>
      optimize_result_or = OptimizeDocumentStore(optimize_stats);
  optimize_stats->set_document_store_optimize_latency_ms(
      optimize_doc_store_timer->GetElapsedMilliseconds());

  if (!optimize_result_or.ok() &&
      !absl_ports::IsDataLoss(optimize_result_or.status())) {
    // The status now is either ABORTED_ERROR or INTERNAL_ERROR.
    // If ABORTED_ERROR, Icing should still be working.
    // If INTERNAL_ERROR, we're having IO errors or other errors that we can't
    // recover from.
    TransformStatus(optimize_result_or.status(), result_status);
    return result_proto;
  }

  // The status is either OK or DATA_LOSS. The optimized document store is
  // guaranteed to work, so we update index according to the new document store.
  std::unique_ptr<Timer> optimize_index_timer = clock_->GetNewTimer();
  auto doc_store_optimize_result_status = optimize_result_or.status();
  bool should_rebuild_index =
      !optimize_result_or.ok() ||
      optimize_result_or.ValueOrDie().should_rebuild_index ||
      ShouldRebuildIndex(*optimize_stats,
                         options_.optimize_rebuild_index_threshold());
  if (!should_rebuild_index) {
    // At this point should_rebuild_index is false, so it means
    // optimize_result_or.ok() is true and therefore it is safe to call
    // ValueOrDie.
    DocumentStore::OptimizeResult optimize_result =
        std::move(optimize_result_or).ValueOrDie();

    optimize_stats->set_index_restoration_mode(
        OptimizeStatsProto::INDEX_TRANSLATION);
    libtextclassifier3::Status index_optimize_status =
        index_->Optimize(optimize_result.document_id_old_to_new,
                         document_store_->last_added_document_id());
    if (!index_optimize_status.ok()) {
      ICING_LOG(WARNING) << "Failed to optimize index. Error: "
                         << index_optimize_status.error_message();
      should_rebuild_index = true;
    }

    libtextclassifier3::Status integer_index_optimize_status =
        integer_index_->Optimize(optimize_result.document_id_old_to_new,
                                 document_store_->last_added_document_id());
    if (!integer_index_optimize_status.ok()) {
      ICING_LOG(WARNING) << "Failed to optimize integer index. Error: "
                         << integer_index_optimize_status.error_message();
      should_rebuild_index = true;
    }

    libtextclassifier3::Status qualified_id_join_index_optimize_status =
        qualified_id_join_index_->Optimize(
            optimize_result.document_id_old_to_new,
            optimize_result.namespace_id_old_to_new,
            document_store_->last_added_document_id());
    if (!qualified_id_join_index_optimize_status.ok()) {
      ICING_LOG(WARNING)
          << "Failed to optimize qualified id join index. Error: "
          << qualified_id_join_index_optimize_status.error_message();
      should_rebuild_index = true;
    }
  }
  // If we received a DATA_LOSS error from OptimizeDocumentStore, we have a
  // valid document store, but it might be the old one or the new one. So throw
  // out the index data and rebuild from scratch.
  // Also rebuild index if DocumentStore::OptimizeInto hints to do so.
  // Likewise, if Index::Optimize failed, then attempt to recover the index by
  // rebuilding from scratch.
  // If ShouldRebuildIndex() returns true, we will also rebuild the index for
  // better performance.
  if (should_rebuild_index) {
    optimize_stats->set_index_restoration_mode(
        OptimizeStatsProto::FULL_INDEX_REBUILD);
    ICING_LOG(WARNING) << "Clearing the entire index!";

    libtextclassifier3::Status index_clear_status = ClearAllIndices();
    if (!index_clear_status.ok()) {
      status = absl_ports::Annotate(
          absl_ports::InternalError("Failed to clear index."),
          index_clear_status.error_message());
      TransformStatus(status, result_status);
      optimize_stats->set_index_restoration_latency_ms(
          optimize_index_timer->GetElapsedMilliseconds());
      return result_proto;
    }

    IndexRestorationResult index_restoration_status = RestoreIndexIfNeeded();
    // DATA_LOSS means that we have successfully re-added content to the index.
    // Some indexed content was lost, but otherwise the index is in a valid
    // state and can be queried.
    if (!index_restoration_status.status.ok() &&
        !absl_ports::IsDataLoss(index_restoration_status.status)) {
      status = absl_ports::Annotate(
          absl_ports::InternalError(
              "Failed to reindex documents after optimization."),
          index_restoration_status.status.error_message());

      TransformStatus(status, result_status);
      optimize_stats->set_index_restoration_latency_ms(
          optimize_index_timer->GetElapsedMilliseconds());
      return result_proto;
    }
  }
  optimize_stats->set_index_restoration_latency_ms(
      optimize_index_timer->GetElapsedMilliseconds());

  // Read the optimize status to get the time that we last ran.
  std::string optimize_status_filename =
      absl_ports::StrCat(options_.base_dir(), "/", kOptimizeStatusFilename);
  FileBackedProto<OptimizeStatusProto> optimize_status_file(
      *filesystem_, optimize_status_filename);
  auto optimize_status_or = optimize_status_file.Read();
  int64_t current_time = clock_->GetSystemTimeMilliseconds();
  if (optimize_status_or.ok()) {
    // If we have trouble reading the status or this is the first time that
    // we've ever run, don't set this field.
    optimize_stats->set_time_since_last_optimize_ms(
        current_time - optimize_status_or.ValueOrDie()
                           ->last_successful_optimize_run_time_ms());
  }

  // Update the status for this run and write it.
  auto optimize_status = std::make_unique<OptimizeStatusProto>();
  optimize_status->set_last_successful_optimize_run_time_ms(current_time);
  auto write_status = optimize_status_file.Write(std::move(optimize_status));
  if (!write_status.ok()) {
    ICING_LOG(ERROR) << "Failed to write optimize status:\n"
                     << write_status.error_message();
  }

  // Flushes data to disk after doing optimization
  status = InternalPersistToDisk(PersistType::FULL);
  if (!status.ok()) {
    TransformStatus(status, result_status);
    return result_proto;
  }

  int64_t after_size = filesystem_->GetDiskUsage(options_.base_dir().c_str());
  optimize_stats->set_storage_size_after(
      Filesystem::SanitizeFileSize(after_size));

  TransformStatus(doc_store_optimize_result_status, result_status);
  return result_proto;
}

GetOptimizeInfoResultProto IcingSearchEngine::GetOptimizeInfo() {
  ICING_VLOG(1) << "Getting optimize info from IcingSearchEngine";

  GetOptimizeInfoResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();

  absl_ports::shared_lock l(&mutex_);
  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return result_proto;
  }

  // Read the optimize status to get the time that we last ran.
  std::string optimize_status_filename =
      absl_ports::StrCat(options_.base_dir(), "/", kOptimizeStatusFilename);
  FileBackedProto<OptimizeStatusProto> optimize_status_file(
      *filesystem_, optimize_status_filename);
  auto optimize_status_or = optimize_status_file.Read();
  int64_t current_time = clock_->GetSystemTimeMilliseconds();

  if (optimize_status_or.ok()) {
    // If we have trouble reading the status or this is the first time that
    // we've ever run, don't set this field.
    result_proto.set_time_since_last_optimize_ms(
        current_time - optimize_status_or.ValueOrDie()
                           ->last_successful_optimize_run_time_ms());
  }

  // Get stats from DocumentStore
  auto doc_store_optimize_info_or = document_store_->GetOptimizeInfo();
  if (!doc_store_optimize_info_or.ok()) {
    TransformStatus(doc_store_optimize_info_or.status(), result_status);
    return result_proto;
  }
  DocumentStore::OptimizeInfo doc_store_optimize_info =
      doc_store_optimize_info_or.ValueOrDie();
  result_proto.set_optimizable_docs(doc_store_optimize_info.optimizable_docs);

  if (doc_store_optimize_info.optimizable_docs == 0) {
    // Can return early since there's nothing to calculate on the index side
    result_proto.set_estimated_optimizable_bytes(0);
    result_status->set_code(StatusProto::OK);
    return result_proto;
  }

  // Get stats from Index.
  auto index_elements_size_or = index_->GetElementsSize();
  if (!index_elements_size_or.ok()) {
    TransformStatus(index_elements_size_or.status(), result_status);
    return result_proto;
  }
  int64_t index_elements_size = index_elements_size_or.ValueOrDie();

  // TODO(b/259744228): add stats for integer index

  // Sum up the optimizable sizes from DocumentStore and Index
  result_proto.set_estimated_optimizable_bytes(
      index_elements_size * doc_store_optimize_info.optimizable_docs /
          doc_store_optimize_info.total_docs +
      doc_store_optimize_info.estimated_optimizable_bytes);

  result_status->set_code(StatusProto::OK);
  return result_proto;
}

StorageInfoResultProto IcingSearchEngine::GetStorageInfo() {
  StorageInfoResultProto result;
  absl_ports::shared_lock l(&mutex_);
  if (!initialized_) {
    result.mutable_status()->set_code(StatusProto::FAILED_PRECONDITION);
    result.mutable_status()->set_message(
        "IcingSearchEngine has not been initialized!");
    return result;
  }

  int64_t index_size = filesystem_->GetDiskUsage(options_.base_dir().c_str());
  result.mutable_storage_info()->set_total_storage_size(
      Filesystem::SanitizeFileSize(index_size));
  *result.mutable_storage_info()->mutable_document_storage_info() =
      document_store_->GetStorageInfo();
  *result.mutable_storage_info()->mutable_schema_store_storage_info() =
      schema_store_->GetStorageInfo();
  *result.mutable_storage_info()->mutable_index_storage_info() =
      index_->GetStorageInfo();
  // TODO(b/259744228): add stats for integer index
  result.mutable_status()->set_code(StatusProto::OK);
  return result;
}

DebugInfoResultProto IcingSearchEngine::GetDebugInfo(
    DebugInfoVerbosity::Code verbosity) {
  DebugInfoResultProto debug_info;
  StatusProto* result_status = debug_info.mutable_status();
  absl_ports::shared_lock l(&mutex_);
  if (!initialized_) {
    debug_info.mutable_status()->set_code(StatusProto::FAILED_PRECONDITION);
    debug_info.mutable_status()->set_message(
        "IcingSearchEngine has not been initialized!");
    return debug_info;
  }

  // Index
  *debug_info.mutable_debug_info()->mutable_index_info() =
      index_->GetDebugInfo(verbosity);

  // TODO(b/259744228): add debug info for integer index

  // Document Store
  libtextclassifier3::StatusOr<DocumentDebugInfoProto> document_debug_info =
      document_store_->GetDebugInfo(verbosity);
  if (!document_debug_info.ok()) {
    TransformStatus(document_debug_info.status(), result_status);
    return debug_info;
  }
  *debug_info.mutable_debug_info()->mutable_document_info() =
      std::move(document_debug_info).ValueOrDie();

  // Schema Store
  libtextclassifier3::StatusOr<SchemaDebugInfoProto> schema_debug_info =
      schema_store_->GetDebugInfo();
  if (!schema_debug_info.ok()) {
    TransformStatus(schema_debug_info.status(), result_status);
    return debug_info;
  }
  *debug_info.mutable_debug_info()->mutable_schema_info() =
      std::move(schema_debug_info).ValueOrDie();

  result_status->set_code(StatusProto::OK);
  return debug_info;
}

libtextclassifier3::Status IcingSearchEngine::InternalPersistToDisk(
    PersistType::Code persist_type) {
  if (persist_type == PersistType::LITE) {
    return document_store_->PersistToDisk(persist_type);
  }
  ICING_RETURN_IF_ERROR(schema_store_->PersistToDisk());
  ICING_RETURN_IF_ERROR(document_store_->PersistToDisk(PersistType::FULL));
  ICING_RETURN_IF_ERROR(index_->PersistToDisk());
  ICING_RETURN_IF_ERROR(integer_index_->PersistToDisk());
  ICING_RETURN_IF_ERROR(qualified_id_join_index_->PersistToDisk());

  return libtextclassifier3::Status::OK;
}

SearchResultProto IcingSearchEngine::Search(
    const SearchSpecProto& search_spec, const ScoringSpecProto& scoring_spec,
    const ResultSpecProto& result_spec) {
  if (search_spec.use_read_only_search()) {
    return SearchLockedShared(search_spec, scoring_spec, result_spec);
  } else {
    return SearchLockedExclusive(search_spec, scoring_spec, result_spec);
  }
}

SearchResultProto IcingSearchEngine::SearchLockedShared(
    const SearchSpecProto& search_spec, const ScoringSpecProto& scoring_spec,
    const ResultSpecProto& result_spec) {
  std::unique_ptr<Timer> overall_timer = clock_->GetNewTimer();

  // Only acquire an overall read-lock for this implementation. Finer-grained
  // locks are implemented around code paths that write changes to Icing's data
  // members.
  absl_ports::shared_lock l(&mutex_);
  int64_t lock_acquisition_latency = overall_timer->GetElapsedMilliseconds();

  SearchResultProto result_proto =
      InternalSearch(search_spec, scoring_spec, result_spec);

  result_proto.mutable_query_stats()->set_lock_acquisition_latency_ms(
      lock_acquisition_latency);
  result_proto.mutable_query_stats()->set_latency_ms(
      overall_timer->GetElapsedMilliseconds());
  return result_proto;
}

SearchResultProto IcingSearchEngine::SearchLockedExclusive(
    const SearchSpecProto& search_spec, const ScoringSpecProto& scoring_spec,
    const ResultSpecProto& result_spec) {
  std::unique_ptr<Timer> overall_timer = clock_->GetNewTimer();

  // Acquire the overall write-lock for this locked implementation.
  absl_ports::unique_lock l(&mutex_);
  int64_t lock_acquisition_latency = overall_timer->GetElapsedMilliseconds();

  SearchResultProto result_proto =
      InternalSearch(search_spec, scoring_spec, result_spec);

  result_proto.mutable_query_stats()->set_lock_acquisition_latency_ms(
      lock_acquisition_latency);
  result_proto.mutable_query_stats()->set_latency_ms(
      overall_timer->GetElapsedMilliseconds());
  return result_proto;
}

SearchResultProto IcingSearchEngine::InternalSearch(
    const SearchSpecProto& search_spec, const ScoringSpecProto& scoring_spec,
    const ResultSpecProto& result_spec) {
  SearchResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();

  QueryStatsProto* query_stats = result_proto.mutable_query_stats();
  query_stats->set_is_first_page(true);
  query_stats->set_requested_page_size(result_spec.num_per_page());

  // TODO(b/305098009): deprecate search-related flat fields in query_stats.
  query_stats->set_num_namespaces_filtered(
      search_spec.namespace_filters_size());
  query_stats->set_num_schema_types_filtered(
      search_spec.schema_type_filters_size());
  query_stats->set_query_length(search_spec.query().length());
  query_stats->set_ranking_strategy(scoring_spec.rank_by());

  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return result_proto;
  }
  index_->PublishQueryStats(query_stats);

  libtextclassifier3::Status status =
      ValidateResultSpec(document_store_.get(), result_spec);
  if (!status.ok()) {
    TransformStatus(status, result_status);
    return result_proto;
  }
  status = ValidateSearchSpec(search_spec, performance_configuration_);
  if (!status.ok()) {
    TransformStatus(status, result_status);
    return result_proto;
  }

  const JoinSpecProto& join_spec = search_spec.join_spec();
  std::unique_ptr<JoinChildrenFetcher> join_children_fetcher;
  std::unique_ptr<ResultAdjustmentInfo> child_result_adjustment_info;
  int64_t current_time_ms = clock_->GetSystemTimeMilliseconds();
  if (!join_spec.parent_property_expression().empty() &&
      !join_spec.child_property_expression().empty()) {
    query_stats->set_is_join_query(true);
    QueryStatsProto::SearchStats* child_search_stats =
        query_stats->mutable_child_search_stats();

    // Process child query
    QueryScoringResults nested_query_scoring_results = ProcessQueryAndScore(
        join_spec.nested_spec().search_spec(),
        join_spec.nested_spec().scoring_spec(),
        join_spec.nested_spec().result_spec(),
        /*join_children_fetcher=*/nullptr, current_time_ms, child_search_stats);
    if (!nested_query_scoring_results.status.ok()) {
      TransformStatus(nested_query_scoring_results.status, result_status);
      return result_proto;
    }

    JoinProcessor join_processor(document_store_.get(), schema_store_.get(),
                                 qualified_id_join_index_.get(),
                                 current_time_ms);
    // Building a JoinChildrenFetcher where child documents are grouped by
    // their joinable values.
    libtextclassifier3::StatusOr<JoinChildrenFetcher> join_children_fetcher_or =
        join_processor.GetChildrenFetcher(
            search_spec.join_spec(),
            std::move(nested_query_scoring_results.scored_document_hits));
    if (!join_children_fetcher_or.ok()) {
      TransformStatus(join_children_fetcher_or.status(), result_status);
      return result_proto;
    }
    join_children_fetcher = std::make_unique<JoinChildrenFetcher>(
        std::move(join_children_fetcher_or).ValueOrDie());

    // Assign child's ResultAdjustmentInfo.
    child_result_adjustment_info = std::make_unique<ResultAdjustmentInfo>(
        join_spec.nested_spec().search_spec(),
        join_spec.nested_spec().scoring_spec(),
        join_spec.nested_spec().result_spec(), schema_store_.get(),
        std::move(nested_query_scoring_results.query_terms));
  }

  // Process parent query
  QueryStatsProto::SearchStats* parent_search_stats =
      query_stats->mutable_parent_search_stats();
  QueryScoringResults query_scoring_results = ProcessQueryAndScore(
      search_spec, scoring_spec, result_spec, join_children_fetcher.get(),
      current_time_ms, parent_search_stats);
  // TODO(b/305098009): deprecate search-related flat fields in query_stats.
  query_stats->set_num_terms(parent_search_stats->num_terms());
  query_stats->set_parse_query_latency_ms(
      parent_search_stats->parse_query_latency_ms());
  query_stats->set_scoring_latency_ms(
      parent_search_stats->scoring_latency_ms());
  query_stats->set_num_documents_scored(
      parent_search_stats->num_documents_scored());
  if (!query_scoring_results.status.ok()) {
    TransformStatus(query_scoring_results.status, result_status);
    return result_proto;
  }

  // Returns early for empty result
  if (query_scoring_results.scored_document_hits.empty()) {
    result_status->set_code(StatusProto::OK);
    return result_proto;
  }

  // Construct parent's result adjustment info.
  auto parent_result_adjustment_info = std::make_unique<ResultAdjustmentInfo>(
      search_spec, scoring_spec, result_spec, schema_store_.get(),
      std::move(query_scoring_results.query_terms));

  std::unique_ptr<ScoredDocumentHitsRanker> ranker;
  if (join_children_fetcher != nullptr) {
    std::unique_ptr<Timer> join_timer = clock_->GetNewTimer();
    // Join 2 scored document hits
    JoinProcessor join_processor(document_store_.get(), schema_store_.get(),
                                 qualified_id_join_index_.get(),
                                 current_time_ms);
    libtextclassifier3::StatusOr<std::vector<JoinedScoredDocumentHit>>
        joined_result_document_hits_or = join_processor.Join(
            join_spec, std::move(query_scoring_results.scored_document_hits),
            *join_children_fetcher);
    if (!joined_result_document_hits_or.ok()) {
      TransformStatus(joined_result_document_hits_or.status(), result_status);
      return result_proto;
    }
    std::vector<JoinedScoredDocumentHit> joined_result_document_hits =
        std::move(joined_result_document_hits_or).ValueOrDie();

    query_stats->set_join_latency_ms(join_timer->GetElapsedMilliseconds());

    std::unique_ptr<Timer> component_timer = clock_->GetNewTimer();
    // Ranks results
    ranker = std::make_unique<
        PriorityQueueScoredDocumentHitsRanker<JoinedScoredDocumentHit>>(
        std::move(joined_result_document_hits),
        /*is_descending=*/scoring_spec.order_by() ==
            ScoringSpecProto::Order::DESC);
    query_stats->set_ranking_latency_ms(
        component_timer->GetElapsedMilliseconds());
  } else {
    // Non-join query
    std::unique_ptr<Timer> component_timer = clock_->GetNewTimer();
    // Ranks results
    ranker = std::make_unique<
        PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
        std::move(query_scoring_results.scored_document_hits),
        /*is_descending=*/scoring_spec.order_by() ==
            ScoringSpecProto::Order::DESC);
    query_stats->set_ranking_latency_ms(
        component_timer->GetElapsedMilliseconds());
  }

  std::unique_ptr<Timer> component_timer = clock_->GetNewTimer();
  // CacheAndRetrieveFirstPage and retrieves the document protos and snippets if
  // requested
  auto result_retriever_or =
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get());
  if (!result_retriever_or.ok()) {
    TransformStatus(result_retriever_or.status(), result_status);
    query_stats->set_document_retrieval_latency_ms(
        component_timer->GetElapsedMilliseconds());
    return result_proto;
  }
  std::unique_ptr<ResultRetrieverV2> result_retriever =
      std::move(result_retriever_or).ValueOrDie();

  libtextclassifier3::StatusOr<std::pair<uint64_t, PageResult>>
      page_result_info_or = result_state_manager_->CacheAndRetrieveFirstPage(
          std::move(ranker), std::move(parent_result_adjustment_info),
          std::move(child_result_adjustment_info), result_spec,
          *document_store_, *result_retriever, current_time_ms);
  if (!page_result_info_or.ok()) {
    TransformStatus(page_result_info_or.status(), result_status);
    query_stats->set_document_retrieval_latency_ms(
        component_timer->GetElapsedMilliseconds());
    return result_proto;
  }
  std::pair<uint64_t, PageResult> page_result_info =
      std::move(page_result_info_or).ValueOrDie();

  // Assembles the final search result proto
  result_proto.mutable_results()->Reserve(
      page_result_info.second.results.size());

  int32_t child_count = 0;
  for (SearchResultProto::ResultProto& result :
       page_result_info.second.results) {
    child_count += result.joined_results_size();
    result_proto.mutable_results()->Add(std::move(result));
  }

  result_status->set_code(StatusProto::OK);
  if (page_result_info.first != kInvalidNextPageToken) {
    result_proto.set_next_page_token(page_result_info.first);
  }

  query_stats->set_document_retrieval_latency_ms(
      component_timer->GetElapsedMilliseconds());
  query_stats->set_num_results_returned_current_page(
      result_proto.results_size());

  query_stats->set_num_joined_results_returned_current_page(child_count);

  query_stats->set_num_results_with_snippets(
      page_result_info.second.num_results_with_snippets);
  return result_proto;
}

IcingSearchEngine::QueryScoringResults IcingSearchEngine::ProcessQueryAndScore(
    const SearchSpecProto& search_spec, const ScoringSpecProto& scoring_spec,
    const ResultSpecProto& result_spec,
    const JoinChildrenFetcher* join_children_fetcher, int64_t current_time_ms,
    QueryStatsProto::SearchStats* search_stats) {
  search_stats->set_num_namespaces_filtered(
      search_spec.namespace_filters_size());
  search_stats->set_num_schema_types_filtered(
      search_spec.schema_type_filters_size());
  search_stats->set_query_length(search_spec.query().length());
  search_stats->set_ranking_strategy(scoring_spec.rank_by());

  std::unique_ptr<Timer> component_timer = clock_->GetNewTimer();

  // Gets unordered results from query processor
  auto query_processor_or = QueryProcessor::Create(
      index_.get(), integer_index_.get(), language_segmenter_.get(),
      normalizer_.get(), document_store_.get(), schema_store_.get(),
      clock_.get());
  if (!query_processor_or.ok()) {
    search_stats->set_parse_query_latency_ms(
        component_timer->GetElapsedMilliseconds());
    return QueryScoringResults(std::move(query_processor_or).status(),
                               /*query_terms_in=*/{},
                               /*scored_document_hits_in=*/{});
  }
  std::unique_ptr<QueryProcessor> query_processor =
      std::move(query_processor_or).ValueOrDie();

  auto ranking_strategy_or = GetRankingStrategyFromScoringSpec(scoring_spec);
  libtextclassifier3::StatusOr<QueryResults> query_results_or;
  if (ranking_strategy_or.ok()) {
    query_results_or = query_processor->ParseSearch(
        search_spec, ranking_strategy_or.ValueOrDie(), current_time_ms,
        search_stats);
  } else {
    query_results_or = ranking_strategy_or.status();
  }
  search_stats->set_parse_query_latency_ms(
      component_timer->GetElapsedMilliseconds());
  if (!query_results_or.ok()) {
    return QueryScoringResults(std::move(query_results_or).status(),
                               /*query_terms_in=*/{},
                               /*scored_document_hits_in=*/{});
  }
  QueryResults query_results = std::move(query_results_or).ValueOrDie();

  // Set SearchStats related to QueryResults.
  int term_count = 0;
  for (const auto& section_and_terms : query_results.query_terms) {
    term_count += section_and_terms.second.size();
  }
  search_stats->set_num_terms(term_count);

  if (query_results.features_in_use.count(kNumericSearchFeature)) {
    search_stats->set_is_numeric_query(true);
  }

  component_timer = clock_->GetNewTimer();
  // Scores but does not rank the results.
  libtextclassifier3::StatusOr<std::unique_ptr<ScoringProcessor>>
      scoring_processor_or = ScoringProcessor::Create(
          scoring_spec, document_store_.get(), schema_store_.get(),
          current_time_ms, join_children_fetcher);
  if (!scoring_processor_or.ok()) {
    return QueryScoringResults(std::move(scoring_processor_or).status(),
                               std::move(query_results.query_terms),
                               /*scored_document_hits_in=*/{});
  }
  std::unique_ptr<ScoringProcessor> scoring_processor =
      std::move(scoring_processor_or).ValueOrDie();
  std::vector<ScoredDocumentHit> scored_document_hits =
      scoring_processor->Score(
          std::move(query_results.root_iterator), result_spec.num_to_score(),
          &query_results.query_term_iterators, search_stats);
  search_stats->set_scoring_latency_ms(
      component_timer->GetElapsedMilliseconds());

  return QueryScoringResults(libtextclassifier3::Status::OK,
                             std::move(query_results.query_terms),
                             std::move(scored_document_hits));
}

SearchResultProto IcingSearchEngine::GetNextPage(uint64_t next_page_token) {
  SearchResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();

  QueryStatsProto* query_stats = result_proto.mutable_query_stats();
  query_stats->set_is_first_page(false);
  std::unique_ptr<Timer> overall_timer = clock_->GetNewTimer();
  // ResultStateManager has its own writer lock, so here we only need a reader
  // lock for other components.
  absl_ports::shared_lock l(&mutex_);
  query_stats->set_lock_acquisition_latency_ms(
      overall_timer->GetElapsedMilliseconds());
  if (!initialized_) {
    result_status->set_code(StatusProto::FAILED_PRECONDITION);
    result_status->set_message("IcingSearchEngine has not been initialized!");
    return result_proto;
  }

  auto result_retriever_or =
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get());
  if (!result_retriever_or.ok()) {
    TransformStatus(result_retriever_or.status(), result_status);
    return result_proto;
  }
  std::unique_ptr<ResultRetrieverV2> result_retriever =
      std::move(result_retriever_or).ValueOrDie();

  int64_t current_time_ms = clock_->GetSystemTimeMilliseconds();
  libtextclassifier3::StatusOr<std::pair<uint64_t, PageResult>>
      page_result_info_or = result_state_manager_->GetNextPage(
          next_page_token, *result_retriever, current_time_ms);
  if (!page_result_info_or.ok()) {
    if (absl_ports::IsNotFound(page_result_info_or.status())) {
      // NOT_FOUND means an empty result.
      result_status->set_code(StatusProto::OK);
    } else {
      // Real error, pass up.
      TransformStatus(page_result_info_or.status(), result_status);
    }
    return result_proto;
  }

  std::pair<uint64_t, PageResult> page_result_info =
      std::move(page_result_info_or).ValueOrDie();
  query_stats->set_requested_page_size(
      page_result_info.second.requested_page_size);

  // Assembles the final search result proto
  result_proto.mutable_results()->Reserve(
      page_result_info.second.results.size());

  int32_t child_count = 0;
  for (SearchResultProto::ResultProto& result :
       page_result_info.second.results) {
    child_count += result.joined_results_size();
    result_proto.mutable_results()->Add(std::move(result));
  }

  result_status->set_code(StatusProto::OK);
  if (page_result_info.first != kInvalidNextPageToken) {
    result_proto.set_next_page_token(page_result_info.first);
  }

  // The only thing that we're doing is document retrieval. So document
  // retrieval latency and overall latency are the same and can use the same
  // timer.
  query_stats->set_document_retrieval_latency_ms(
      overall_timer->GetElapsedMilliseconds());
  query_stats->set_latency_ms(overall_timer->GetElapsedMilliseconds());
  query_stats->set_num_results_returned_current_page(
      result_proto.results_size());
  query_stats->set_num_results_with_snippets(
      page_result_info.second.num_results_with_snippets);
  query_stats->set_num_joined_results_returned_current_page(child_count);

  return result_proto;
}

void IcingSearchEngine::InvalidateNextPageToken(uint64_t next_page_token) {
  absl_ports::shared_lock l(&mutex_);
  if (!initialized_) {
    ICING_LOG(ERROR) << "IcingSearchEngine has not been initialized!";
    return;
  }
  result_state_manager_->InvalidateResultState(next_page_token);
}

libtextclassifier3::StatusOr<DocumentStore::OptimizeResult>
IcingSearchEngine::OptimizeDocumentStore(OptimizeStatsProto* optimize_stats) {
  // Gets the current directory path and an empty tmp directory path for
  // document store optimization.
  const std::string current_document_dir =
      MakeDocumentDirectoryPath(options_.base_dir());
  const std::string temporary_document_dir =
      MakeDocumentTemporaryDirectoryPath(options_.base_dir());
  if (!filesystem_->DeleteDirectoryRecursively(
          temporary_document_dir.c_str()) ||
      !filesystem_->CreateDirectoryRecursively(
          temporary_document_dir.c_str())) {
    return absl_ports::AbortedError(absl_ports::StrCat(
        "Failed to create a tmp directory: ", temporary_document_dir));
  }

  // Copies valid document data to tmp directory
  libtextclassifier3::StatusOr<DocumentStore::OptimizeResult>
      optimize_result_or = document_store_->OptimizeInto(
          temporary_document_dir, language_segmenter_.get(), optimize_stats);

  // Handles error if any
  if (!optimize_result_or.ok()) {
    filesystem_->DeleteDirectoryRecursively(temporary_document_dir.c_str());
    return absl_ports::Annotate(
        absl_ports::AbortedError("Failed to optimize document store"),
        optimize_result_or.status().error_message());
  }

  // result_state_manager_ depends on document_store_. So we need to reset it at
  // the same time that we reset the document_store_.
  result_state_manager_.reset();
  document_store_.reset();

  // When swapping files, always put the current working directory at the
  // second place because it is renamed at the latter position so we're less
  // vulnerable to errors.
  if (!filesystem_->SwapFiles(temporary_document_dir.c_str(),
                              current_document_dir.c_str())) {
    ICING_LOG(ERROR) << "Failed to swap files";

    // Ensures that current directory is still present.
    if (!filesystem_->CreateDirectoryRecursively(
            current_document_dir.c_str())) {
      // Can't even create the old directory. Mark as uninitialized and return
      // INTERNAL.
      initialized_ = false;
      return absl_ports::InternalError(
          "Failed to create file directory for document store");
    }

    // Tries to rebuild document store if swapping fails, to avoid leaving the
    // system in the broken state for future operations.
    auto create_result_or = DocumentStore::Create(
        filesystem_.get(), current_document_dir, clock_.get(),
        schema_store_.get(), /*force_recovery_and_revalidate_documents=*/false,
        options_.document_store_namespace_id_fingerprint(),
        options_.pre_mapping_fbv(), options_.use_persistent_hash_map(),
        options_.compression_level(), /*initialize_stats=*/nullptr);
    // TODO(b/144458732): Implement a more robust version of
    // TC_ASSIGN_OR_RETURN that can support error logging.
    if (!create_result_or.ok()) {
      // Unable to create DocumentStore from the old file. Mark as uninitialized
      // and return INTERNAL.
      initialized_ = false;
      ICING_LOG(ERROR) << "Failed to create document store instance";
      return absl_ports::Annotate(
          absl_ports::InternalError("Failed to create document store instance"),
          create_result_or.status().error_message());
    }
    document_store_ = std::move(create_result_or.ValueOrDie().document_store);
    result_state_manager_ = std::make_unique<ResultStateManager>(
        performance_configuration_.max_num_total_hits, *document_store_);

    // Potential data loss
    // TODO(b/147373249): Find a way to detect true data loss error
    return absl_ports::DataLossError(
        "Failed to optimize document store, there might be data loss");
  }

  // Recreates the doc store instance
  auto create_result_or = DocumentStore::Create(
      filesystem_.get(), current_document_dir, clock_.get(),
      schema_store_.get(), /*force_recovery_and_revalidate_documents=*/false,
      options_.document_store_namespace_id_fingerprint(),
      options_.pre_mapping_fbv(), options_.use_persistent_hash_map(),
      options_.compression_level(), /*initialize_stats=*/nullptr);
  if (!create_result_or.ok()) {
    // Unable to create DocumentStore from the new file. Mark as uninitialized
    // and return INTERNAL.
    initialized_ = false;
    return absl_ports::InternalError(
        "Document store has been optimized, but a valid document store "
        "instance can't be created");
  }
  DocumentStore::CreateResult create_result =
      std::move(create_result_or).ValueOrDie();
  document_store_ = std::move(create_result.document_store);
  result_state_manager_ = std::make_unique<ResultStateManager>(
      performance_configuration_.max_num_total_hits, *document_store_);

  // Deletes tmp directory
  if (!filesystem_->DeleteDirectoryRecursively(
          temporary_document_dir.c_str())) {
    ICING_LOG(ERROR) << "Document store has been optimized, but it failed to "
                        "delete temporary file directory";
  }

  // Since we created new (optimized) document store with correct PersistToDisk
  // call, we shouldn't have data loss or regenerate derived files. Therefore,
  // if we really encounter any of these situations, then return DataLossError
  // to let the caller rebuild index.
  if (create_result.data_loss != DataLoss::NONE ||
      create_result.derived_files_regenerated) {
    return absl_ports::DataLossError(
        "Unexpected data loss or derived files regenerated for new document "
        "store");
  }

  return optimize_result_or;
}

IcingSearchEngine::IndexRestorationResult
IcingSearchEngine::RestoreIndexIfNeeded() {
  DocumentId last_stored_document_id =
      document_store_->last_added_document_id();
  if (last_stored_document_id == index_->last_added_document_id() &&
      last_stored_document_id == integer_index_->last_added_document_id() &&
      last_stored_document_id ==
          qualified_id_join_index_->last_added_document_id()) {
    // No need to recover.
    return {libtextclassifier3::Status::OK, false, false, false};
  }

  if (last_stored_document_id == kInvalidDocumentId) {
    // Document store is empty but index is not. Clear the index.
    return {ClearAllIndices(), false, false, false};
  }

  // Truncate indices first.
  auto truncate_result_or = TruncateIndicesTo(last_stored_document_id);
  if (!truncate_result_or.ok()) {
    return {std::move(truncate_result_or).status(), false, false, false};
  }
  TruncateIndexResult truncate_result =
      std::move(truncate_result_or).ValueOrDie();

  if (truncate_result.first_document_to_reindex > last_stored_document_id) {
    // Nothing to restore. Just return.
    return {libtextclassifier3::Status::OK, false, false, false};
  }

  auto data_indexing_handlers_or = CreateDataIndexingHandlers();
  if (!data_indexing_handlers_or.ok()) {
    return {data_indexing_handlers_or.status(),
            truncate_result.index_needed_restoration,
            truncate_result.integer_index_needed_restoration,
            truncate_result.qualified_id_join_index_needed_restoration};
  }
  // By using recovery_mode for IndexProcessor, we're able to replay documents
  // from smaller document id and it will skip documents that are already been
  // indexed.
  IndexProcessor index_processor(
      std::move(data_indexing_handlers_or).ValueOrDie(), clock_.get(),
      /*recovery_mode=*/true);

  ICING_VLOG(1) << "Restoring index by replaying documents from document id "
                << truncate_result.first_document_to_reindex
                << " to document id " << last_stored_document_id;
  libtextclassifier3::Status overall_status;
  for (DocumentId document_id = truncate_result.first_document_to_reindex;
       document_id <= last_stored_document_id; ++document_id) {
    libtextclassifier3::StatusOr<DocumentProto> document_or =
        document_store_->Get(document_id);

    if (!document_or.ok()) {
      if (absl_ports::IsInvalidArgument(document_or.status()) ||
          absl_ports::IsNotFound(document_or.status())) {
        // Skips invalid and non-existing documents.
        continue;
      } else {
        // Returns other errors
        return {document_or.status(), truncate_result.index_needed_restoration,
                truncate_result.integer_index_needed_restoration,
                truncate_result.qualified_id_join_index_needed_restoration};
      }
    }
    DocumentProto document(std::move(document_or).ValueOrDie());

    libtextclassifier3::StatusOr<TokenizedDocument> tokenized_document_or =
        TokenizedDocument::Create(schema_store_.get(),
                                  language_segmenter_.get(),
                                  std::move(document));
    if (!tokenized_document_or.ok()) {
      return {tokenized_document_or.status(),
              truncate_result.index_needed_restoration,
              truncate_result.integer_index_needed_restoration,
              truncate_result.qualified_id_join_index_needed_restoration};
    }
    TokenizedDocument tokenized_document(
        std::move(tokenized_document_or).ValueOrDie());

    libtextclassifier3::Status status =
        index_processor.IndexDocument(tokenized_document, document_id);
    if (!status.ok()) {
      if (!absl_ports::IsDataLoss(status)) {
        // Real error. Stop recovering and pass it up.
        return {status, truncate_result.index_needed_restoration,
                truncate_result.integer_index_needed_restoration,
                truncate_result.qualified_id_join_index_needed_restoration};
      }
      // FIXME: why can we skip data loss error here?
      // Just a data loss. Keep trying to add the remaining docs, but report the
      // data loss when we're done.
      overall_status = status;
    }
  }

  return {overall_status, truncate_result.index_needed_restoration,
          truncate_result.integer_index_needed_restoration,
          truncate_result.qualified_id_join_index_needed_restoration};
}

libtextclassifier3::StatusOr<bool> IcingSearchEngine::LostPreviousSchema() {
  auto status_or = schema_store_->GetSchema();
  if (status_or.ok()) {
    // Found a schema.
    return false;
  }

  if (!absl_ports::IsNotFound(status_or.status())) {
    // Any other type of error
    return status_or.status();
  }

  // We know: We don't have a schema now.
  //
  // We know: If no documents have been added, then the last_added_document_id
  // will be invalid.
  //
  // So: If documents have been added before and we don't have a schema now,
  // then that means we must have had a schema at some point. Since we wouldn't
  // accept documents without a schema to validate them against.
  return document_store_->last_added_document_id() != kInvalidDocumentId;
}

libtextclassifier3::StatusOr<std::vector<std::unique_ptr<DataIndexingHandler>>>
IcingSearchEngine::CreateDataIndexingHandlers() {
  std::vector<std::unique_ptr<DataIndexingHandler>> handlers;

  // Term index handler
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<TermIndexingHandler> term_indexing_handler,
      TermIndexingHandler::Create(
          clock_.get(), normalizer_.get(), index_.get(),
          options_.build_property_existence_metadata_hits()));
  handlers.push_back(std::move(term_indexing_handler));

  // Integer index handler
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<IntegerSectionIndexingHandler>
                             integer_section_indexing_handler,
                         IntegerSectionIndexingHandler::Create(
                             clock_.get(), integer_index_.get()));
  handlers.push_back(std::move(integer_section_indexing_handler));

  // Qualified id join index handler
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler>
          qualified_id_join_indexing_handler,
      QualifiedIdJoinIndexingHandler::Create(
          clock_.get(), document_store_.get(), qualified_id_join_index_.get()));
  handlers.push_back(std::move(qualified_id_join_indexing_handler));

  return handlers;
}

libtextclassifier3::StatusOr<IcingSearchEngine::TruncateIndexResult>
IcingSearchEngine::TruncateIndicesTo(DocumentId last_stored_document_id) {
  // Attempt to truncate term index.
  // TruncateTo ensures that the index does not hold any data that is not
  // present in the ground truth. If the document store lost some documents,
  // TruncateTo will ensure that the index does not contain any hits from those
  // lost documents. If the index does not contain any hits for documents with
  // document id greater than last_stored_document_id, then TruncateTo will have
  // no effect.
  ICING_RETURN_IF_ERROR(index_->TruncateTo(last_stored_document_id));

  // Get last indexed document id for term index after truncating.
  DocumentId term_index_last_added_document_id =
      index_->last_added_document_id();
  DocumentId first_document_to_reindex =
      (term_index_last_added_document_id != kInvalidDocumentId)
          ? term_index_last_added_document_id + 1
          : kMinDocumentId;
  bool index_needed_restoration =
      (last_stored_document_id != term_index_last_added_document_id);

  // Attempt to truncate integer index.
  bool integer_index_needed_restoration = false;
  DocumentId integer_index_last_added_document_id =
      integer_index_->last_added_document_id();
  if (integer_index_last_added_document_id == kInvalidDocumentId ||
      last_stored_document_id > integer_index_last_added_document_id) {
    // If last_stored_document_id is greater than
    // integer_index_last_added_document_id, then we only have to replay docs
    // starting from integer_index_last_added_document_id + 1. Also use std::min
    // since we might need to replay even smaller doc ids for term index.
    integer_index_needed_restoration = true;
    if (integer_index_last_added_document_id != kInvalidDocumentId) {
      first_document_to_reindex = std::min(
          first_document_to_reindex, integer_index_last_added_document_id + 1);
    } else {
      first_document_to_reindex = kMinDocumentId;
    }
  } else if (last_stored_document_id < integer_index_last_added_document_id) {
    // Clear the entire integer index if last_stored_document_id is smaller than
    // integer_index_last_added_document_id, because there is no way to remove
    // data with doc_id > last_stored_document_id from integer index and we have
    // to rebuild.
    ICING_RETURN_IF_ERROR(integer_index_->Clear());

    // Since the entire integer index is discarded, we start to rebuild it by
    // setting first_document_to_reindex to kMinDocumentId.
    integer_index_needed_restoration = true;
    first_document_to_reindex = kMinDocumentId;
  }

  // Attempt to truncate qualified id join index
  bool qualified_id_join_index_needed_restoration = false;
  DocumentId qualified_id_join_index_last_added_document_id =
      qualified_id_join_index_->last_added_document_id();
  if (qualified_id_join_index_last_added_document_id == kInvalidDocumentId ||
      last_stored_document_id >
          qualified_id_join_index_last_added_document_id) {
    // If last_stored_document_id is greater than
    // qualified_id_join_index_last_added_document_id, then we only have to
    // replay docs starting from (qualified_id_join_index_last_added_document_id
    // + 1). Also use std::min since we might need to replay even smaller doc
    // ids for other components.
    qualified_id_join_index_needed_restoration = true;
    if (qualified_id_join_index_last_added_document_id != kInvalidDocumentId) {
      first_document_to_reindex =
          std::min(first_document_to_reindex,
                   qualified_id_join_index_last_added_document_id + 1);
    } else {
      first_document_to_reindex = kMinDocumentId;
    }
  } else if (last_stored_document_id <
             qualified_id_join_index_last_added_document_id) {
    // Clear the entire qualified id join index if last_stored_document_id is
    // smaller than qualified_id_join_index_last_added_document_id, because
    // there is no way to remove data with doc_id > last_stored_document_id from
    // join index efficiently and we have to rebuild.
    ICING_RETURN_IF_ERROR(qualified_id_join_index_->Clear());

    // Since the entire qualified id join index is discarded, we start to
    // rebuild it by setting first_document_to_reindex to kMinDocumentId.
    qualified_id_join_index_needed_restoration = true;
    first_document_to_reindex = kMinDocumentId;
  }

  return TruncateIndexResult(first_document_to_reindex,
                             index_needed_restoration,
                             integer_index_needed_restoration,
                             qualified_id_join_index_needed_restoration);
}

libtextclassifier3::Status IcingSearchEngine::DiscardDerivedFiles(
    const version_util::DerivedFilesRebuildResult& rebuild_result) {
  if (!rebuild_result.IsRebuildNeeded()) {
    return libtextclassifier3::Status::OK;
  }

  if (schema_store_ != nullptr || document_store_ != nullptr ||
      index_ != nullptr || integer_index_ != nullptr ||
      qualified_id_join_index_ != nullptr) {
    return absl_ports::FailedPreconditionError(
        "Cannot discard derived files while having valid instances");
  }

  // Schema store
  if (rebuild_result.needs_schema_store_derived_files_rebuild) {
    ICING_RETURN_IF_ERROR(SchemaStore::DiscardDerivedFiles(
        filesystem_.get(), options_.base_dir()));
  }

  // Document store
  if (rebuild_result.needs_document_store_derived_files_rebuild) {
    ICING_RETURN_IF_ERROR(DocumentStore::DiscardDerivedFiles(
        filesystem_.get(), options_.base_dir()));
  }

  // Term index
  if (rebuild_result.needs_term_index_rebuild) {
    if (!filesystem_->DeleteDirectoryRecursively(
            MakeIndexDirectoryPath(options_.base_dir()).c_str())) {
      return absl_ports::InternalError("Failed to discard index");
    }
  }

  // Integer index
  if (rebuild_result.needs_integer_index_rebuild) {
    if (!filesystem_->DeleteDirectoryRecursively(
            MakeIntegerIndexWorkingPath(options_.base_dir()).c_str())) {
      return absl_ports::InternalError("Failed to discard integer index");
    }
  }

  // Qualified id join index
  if (rebuild_result.needs_qualified_id_join_index_rebuild) {
    if (!filesystem_->DeleteDirectoryRecursively(
            MakeQualifiedIdJoinIndexWorkingPath(options_.base_dir()).c_str())) {
      return absl_ports::InternalError(
          "Failed to discard qualified id join index");
    }
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status IcingSearchEngine::ClearSearchIndices() {
  ICING_RETURN_IF_ERROR(index_->Reset());
  ICING_RETURN_IF_ERROR(integer_index_->Clear());
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status IcingSearchEngine::ClearJoinIndices() {
  return qualified_id_join_index_->Clear();
}

libtextclassifier3::Status IcingSearchEngine::ClearAllIndices() {
  ICING_RETURN_IF_ERROR(ClearSearchIndices());
  ICING_RETURN_IF_ERROR(ClearJoinIndices());
  return libtextclassifier3::Status::OK;
}

ResetResultProto IcingSearchEngine::Reset() {
  absl_ports::unique_lock l(&mutex_);
  return ResetInternal();
}

ResetResultProto IcingSearchEngine::ResetInternal() {
  ICING_VLOG(1) << "Resetting IcingSearchEngine";

  ResetResultProto result_proto;
  StatusProto* result_status = result_proto.mutable_status();

  initialized_ = false;
  ResetMembers();
  if (!filesystem_->DeleteDirectoryRecursively(options_.base_dir().c_str())) {
    result_status->set_code(StatusProto::INTERNAL);
    return result_proto;
  }

  if (InternalInitialize().status().code() != StatusProto::OK) {
    // We shouldn't hit the following Initialize errors:
    //   NOT_FOUND: all data was cleared, we aren't expecting anything
    //   DATA_LOSS: all data was cleared, we aren't expecting anything
    //   RESOURCE_EXHAUSTED: just deleted files, shouldn't run out of space
    //
    // We can't tell if Initialize failed and left Icing in an inconsistent
    // state or if it was a temporary I/O error. Group everything under INTERNAL
    // to be safe.
    //
    // TODO(b/147699081): Once Initialize returns the proper ABORTED/INTERNAL
    // status code, we can just propagate it up from here.
    result_status->set_code(StatusProto::INTERNAL);
    return result_proto;
  }

  result_status->set_code(StatusProto::OK);
  return result_proto;
}

SuggestionResponse IcingSearchEngine::SearchSuggestions(
    const SuggestionSpecProto& suggestion_spec) {
  // TODO(b/146008613) Explore ideas to make this function read-only.
  absl_ports::unique_lock l(&mutex_);
  SuggestionResponse response;
  StatusProto* response_status = response.mutable_status();
  if (!initialized_) {
    response_status->set_code(StatusProto::FAILED_PRECONDITION);
    response_status->set_message("IcingSearchEngine has not been initialized!");
    return response;
  }

  libtextclassifier3::Status status =
      ValidateSuggestionSpec(suggestion_spec, performance_configuration_);
  if (!status.ok()) {
    TransformStatus(status, response_status);
    return response;
  }

  // Create the suggestion processor.
  auto suggestion_processor_or = SuggestionProcessor::Create(
      index_.get(), integer_index_.get(), language_segmenter_.get(),
      normalizer_.get(), document_store_.get(), schema_store_.get(),
      clock_.get());
  if (!suggestion_processor_or.ok()) {
    TransformStatus(suggestion_processor_or.status(), response_status);
    return response;
  }
  std::unique_ptr<SuggestionProcessor> suggestion_processor =
      std::move(suggestion_processor_or).ValueOrDie();

  // Run suggestion based on given SuggestionSpec.
  int64_t current_time_ms = clock_->GetSystemTimeMilliseconds();
  libtextclassifier3::StatusOr<std::vector<TermMetadata>> terms_or =
      suggestion_processor->QuerySuggestions(suggestion_spec, current_time_ms);
  if (!terms_or.ok()) {
    TransformStatus(terms_or.status(), response_status);
    return response;
  }

  // Convert vector<TermMetaData> into final SuggestionResponse proto.
  for (TermMetadata& term : terms_or.ValueOrDie()) {
    SuggestionResponse::Suggestion suggestion;
    suggestion.set_query(std::move(term.content));
    response.mutable_suggestions()->Add(std::move(suggestion));
  }
  response_status->set_code(StatusProto::OK);
  return response;
}

}  // namespace lib
}  // namespace icing
