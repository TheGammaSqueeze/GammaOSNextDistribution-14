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

#include "icing/monkey_test/icing-monkey-test-runner.h"

#include <algorithm>
#include <array>
#include <cstdint>
#include <functional>
#include <memory>
#include <random>
#include <string>
#include <utility>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/destructible-directory.h"
#include "icing/icing-search-engine.h"
#include "icing/monkey_test/in-memory-icing-search-engine.h"
#include "icing/monkey_test/monkey-test-generators.h"
#include "icing/monkey_test/monkey-test-util.h"
#include "icing/monkey_test/monkey-tokenized-document.h"
#include "icing/portable/equals-proto.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/initialize.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/scoring.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/status.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/result/result-state-manager.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"
#include "icing/util/logging.h"

namespace icing {
namespace lib {

namespace {

using ::icing::lib::portable_equals_proto::EqualsProto;
using ::testing::Eq;
using ::testing::Le;
using ::testing::Not;
using ::testing::SizeIs;
using ::testing::UnorderedElementsAreArray;

SearchSpecProto GenerateRandomSearchSpecProto(
    MonkeyTestRandomEngine* random,
    MonkeyDocumentGenerator* document_generator) {
  // Get a random token from the language set as a single term query.
  std::string query(document_generator->GetToken());
  std::uniform_int_distribution<> dist(0, 1);
  TermMatchType::Code term_match_type = TermMatchType::EXACT_ONLY;
  if (dist(*random) == 1) {
    term_match_type = TermMatchType::PREFIX;
    // Randomly drop a suffix of query to test prefix query.
    std::uniform_int_distribution<> size_dist(1, query.size());
    query.resize(size_dist(*random));
  }
  // 50% chance of getting a section restriction.
  if (dist(*random) == 1) {
    const SchemaTypeConfigProto& type_config = document_generator->GetType();
    if (type_config.properties_size() > 0) {
      std::uniform_int_distribution<> prop_dist(
          0, type_config.properties_size() - 1);
      query = absl_ports::StrCat(
          type_config.properties(prop_dist(*random)).property_name(), ":",
          query);
    }
  }
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(term_match_type);
  search_spec.set_query(query);
  return search_spec;
}

ScoringSpecProto GenerateRandomScoringSpec(MonkeyTestRandomEngine* random) {
  ScoringSpecProto scoring_spec;

  constexpr std::array<ScoringSpecProto::RankingStrategy::Code, 3>
      ranking_strategies = {
          ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE,
          ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP,
          ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE};

  std::uniform_int_distribution<> dist(0, ranking_strategies.size() - 1);
  scoring_spec.set_rank_by(ranking_strategies[dist(*random)]);
  return scoring_spec;
}

ResultSpecProto::SnippetSpecProto GenerateRandomSnippetSpecProto(
    MonkeyTestRandomEngine* random, const ResultSpecProto& result_spec) {
  ResultSpecProto::SnippetSpecProto snippet_spec;

  std::uniform_int_distribution<> num_to_snippet_dist(
      0, result_spec.num_per_page() * 2);
  snippet_spec.set_num_to_snippet(num_to_snippet_dist(*random));

  std::uniform_int_distribution<> num_matches_per_property_dist(0, 10);
  snippet_spec.set_num_matches_per_property(
      num_matches_per_property_dist(*random));

  std::uniform_int_distribution<> dist(0, 4);
  int random_num = dist(*random);
  // 1/5 chance of getting one of 0 (disabled), 8, 32, 128, 512
  int max_window_utf32_length =
      random_num == 0 ? 0 : (1 << (2 * random_num + 1));
  snippet_spec.set_max_window_utf32_length(max_window_utf32_length);
  return snippet_spec;
}

TypePropertyMask GenerateTypePropertyMask(
    MonkeyTestRandomEngine* random, const SchemaTypeConfigProto& type_config) {
  TypePropertyMask type_property_mask;
  type_property_mask.set_schema_type(type_config.schema_type());
  for (const auto& properties : type_config.properties()) {
    // 25% chance of adding the current property to the mask.
    std::uniform_int_distribution<> dist(0, 3);
    if (dist(*random) == 0) {
      type_property_mask.add_paths(properties.property_name());
    }
  }
  return type_property_mask;
}

ResultSpecProto GenerateRandomResultSpecProto(MonkeyTestRandomEngine* random,
                                              const SchemaProto* schema) {
  std::uniform_int_distribution<> dist(0, 4);
  ResultSpecProto result_spec;
  // 1/5 chance of getting one of 1, 4, 16, 64, 256
  int num_per_page = 1 << (2 * dist(*random));
  result_spec.set_num_per_page(num_per_page);
  *result_spec.mutable_snippet_spec() =
      GenerateRandomSnippetSpecProto(random, result_spec);

  // 1/5 chance of enabling projection.
  if (dist(*random) == 0) {
    for (const SchemaTypeConfigProto& type_config : schema->types()) {
      // 25% chance of adding the current type to the projection.
      std::uniform_int_distribution<> dist(0, 3);
      if (dist(*random) == 0) {
        *result_spec.add_type_property_masks() =
            GenerateTypePropertyMask(random, type_config);
      }
    }
  }
  return result_spec;
}

void SortDocuments(std::vector<DocumentProto>& documents) {
  std::sort(documents.begin(), documents.end(),
            [](const DocumentProto& doc1, const DocumentProto& doc2) {
              if (doc1.namespace_() != doc2.namespace_()) {
                return doc1.namespace_() < doc2.namespace_();
              }
              return doc1.uri() < doc2.uri();
            });
}

}  // namespace

IcingMonkeyTestRunner::IcingMonkeyTestRunner(
    IcingMonkeyTestRunnerConfiguration config)
    : config_(std::move(config)),
      random_(config_.seed),
      in_memory_icing_(std::make_unique<InMemoryIcingSearchEngine>(&random_)),
      schema_generator_(
          std::make_unique<MonkeySchemaGenerator>(&random_, &config_)) {
  ICING_LOG(INFO) << "Monkey test runner started with seed: " << config_.seed;
  std::string dir = GetTestTempDir() + "/icing/monkey";
  filesystem_.DeleteDirectoryRecursively(dir.c_str());
  icing_dir_ = std::make_unique<DestructibleDirectory>(&filesystem_, dir);
}

void IcingMonkeyTestRunner::Run(uint32_t num) {
  ASSERT_TRUE(icing_ != nullptr)
      << "Icing search engine has not yet been created. Please call "
         "Initialize() first";

  uint32_t frequency_sum = 0;
  for (const auto& schedule : config_.monkey_api_schedules) {
    frequency_sum += schedule.second;
  }
  std::uniform_int_distribution<> dist(0, frequency_sum - 1);
  for (; num; --num) {
    int p = dist(random_);
    for (const auto& schedule : config_.monkey_api_schedules) {
      if (p < schedule.second) {
        ASSERT_NO_FATAL_FAILURE(schedule.first(this));
        break;
      }
      p -= schedule.second;
    }
    ICING_LOG(INFO) << "Completed Run #" << num
                    << ". Documents in the in-memory icing: "
                    << in_memory_icing_->GetNumAliveDocuments();
  }
}

SetSchemaResultProto IcingMonkeyTestRunner::SetSchema(SchemaProto&& schema) {
  in_memory_icing_->SetSchema(std::move(schema));
  document_generator_ = std::make_unique<MonkeyDocumentGenerator>(
      &random_, in_memory_icing_->GetSchema(), &config_);
  return icing_->SetSchema(*in_memory_icing_->GetSchema(),
                           /*ignore_errors_and_delete_documents=*/true);
}

void IcingMonkeyTestRunner::Initialize() {
  ASSERT_NO_FATAL_FAILURE(CreateIcingSearchEngine());

  SchemaProto schema = schema_generator_->GenerateSchema();
  ICING_LOG(DBG) << "Schema Generated: " << schema.DebugString();

  ASSERT_THAT(SetSchema(std::move(schema)).status(), ProtoIsOk());
}

void IcingMonkeyTestRunner::DoUpdateSchema() {
  ICING_LOG(INFO) << "Monkey updating schema";

  MonkeySchemaGenerator::UpdateSchemaResult result =
      schema_generator_->UpdateSchema(*in_memory_icing_->GetSchema());
  if (result.is_invalid_schema) {
    SetSchemaResultProto set_schema_result =
        icing_->SetSchema(result.schema,
                          /*ignore_errors_and_delete_documents=*/true);
    ASSERT_THAT(set_schema_result.status(), Not(ProtoIsOk()));
    return;
  }
  ICING_LOG(DBG) << "Updating schema to: " << result.schema.DebugString();
  SetSchemaResultProto icing_set_schema_result =
      SetSchema(std::move(result.schema));
  ASSERT_THAT(icing_set_schema_result.status(), ProtoIsOk());
  ASSERT_THAT(icing_set_schema_result.deleted_schema_types(),
              UnorderedElementsAreArray(result.schema_types_deleted));
  ASSERT_THAT(icing_set_schema_result.incompatible_schema_types(),
              UnorderedElementsAreArray(result.schema_types_incompatible));
  ASSERT_THAT(
      icing_set_schema_result.index_incompatible_changed_schema_types(),
      UnorderedElementsAreArray(result.schema_types_index_incompatible));

  // Update in-memory icing
  for (const std::string& deleted_type : result.schema_types_deleted) {
    ICING_ASSERT_OK(in_memory_icing_->DeleteBySchemaType(deleted_type));
  }
  for (const std::string& incompatible_type :
       result.schema_types_incompatible) {
    ICING_ASSERT_OK(in_memory_icing_->DeleteBySchemaType(incompatible_type));
  }
}

void IcingMonkeyTestRunner::DoGet() {
  InMemoryIcingSearchEngine::PickDocumentResult document =
      in_memory_icing_->RandomPickDocument(/*p_alive=*/0.70, /*p_all=*/0.28,
                                           /*p_other=*/0.02);
  ICING_LOG(INFO) << "Monkey getting namespace: " << document.name_space
                  << ", uri: " << document.uri;
  GetResultProto get_result =
      icing_->Get(document.name_space, document.uri,
                  GetResultSpecProto::default_instance());
  if (document.document.has_value()) {
    ASSERT_THAT(get_result.status(), ProtoIsOk())
        << "Cannot find the document that is supposed to exist.";
    ASSERT_THAT(get_result.document(), EqualsProto(document.document.value()))
        << "The document found does not match with the value in the in-memory "
           "icing.";
  } else {
    // Should expect that no document has been found.
    if (get_result.status().code() != StatusProto::NOT_FOUND) {
      if (get_result.status().code() == StatusProto::OK) {
        FAIL() << "Found a document that is not supposed to be found.";
      }
      FAIL() << "Icing search engine failure (code "
             << get_result.status().code()
             << "): " << get_result.status().message();
    }
  }
}

void IcingMonkeyTestRunner::DoGetAllNamespaces() {
  ICING_LOG(INFO) << "Monkey getting all namespaces";
  GetAllNamespacesResultProto get_result = icing_->GetAllNamespaces();
  ASSERT_THAT(get_result.status(), ProtoIsOk());
  ASSERT_THAT(get_result.namespaces(),
              UnorderedElementsAreArray(in_memory_icing_->GetAllNamespaces()));
}

void IcingMonkeyTestRunner::DoPut() {
  MonkeyTokenizedDocument doc = document_generator_->GenerateDocument();
  ICING_LOG(INFO) << "Monkey document generated, namespace: "
                  << doc.document.namespace_()
                  << ", uri: " << doc.document.uri();
  ICING_LOG(DBG) << doc.document.DebugString();
  in_memory_icing_->Put(doc);
  ASSERT_THAT(icing_->Put(doc.document).status(), ProtoIsOk());
}

void IcingMonkeyTestRunner::DoDelete() {
  InMemoryIcingSearchEngine::PickDocumentResult document =
      in_memory_icing_->RandomPickDocument(/*p_alive=*/0.70, /*p_all=*/0.2,
                                           /*p_other=*/0.1);
  ICING_LOG(INFO) << "Monkey deleting namespace: " << document.name_space
                  << ", uri: " << document.uri;
  DeleteResultProto delete_result =
      icing_->Delete(document.name_space, document.uri);
  if (document.document.has_value()) {
    ICING_ASSERT_OK(
        in_memory_icing_->Delete(document.name_space, document.uri));
    ASSERT_THAT(delete_result.status(), ProtoIsOk())
        << "Cannot delete an existing document.";
  } else {
    // Should expect that no document has been deleted.
    if (delete_result.status().code() != StatusProto::NOT_FOUND) {
      if (delete_result.status().code() == StatusProto::OK) {
        FAIL() << "Deleted a non-existing document without an error.";
      }
      FAIL() << "Icing search engine failure (code "
             << delete_result.status().code()
             << "): " << delete_result.status().message();
    }
  }
}

void IcingMonkeyTestRunner::DoDeleteByNamespace() {
  std::string name_space = document_generator_->GetNamespace();
  ICING_LOG(INFO) << "Monkey deleting namespace: " << name_space;
  DeleteByNamespaceResultProto delete_result =
      icing_->DeleteByNamespace(name_space);
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t num_docs_deleted,
                             in_memory_icing_->DeleteByNamespace(name_space));
  if (num_docs_deleted != 0) {
    ASSERT_THAT(delete_result.status(), ProtoIsOk())
        << "Cannot delete an existing namespace.";
    ASSERT_THAT(delete_result.delete_stats().num_documents_deleted(),
                Eq(num_docs_deleted));
  } else {
    // Should expect that no document has been deleted.
    if (delete_result.status().code() != StatusProto::NOT_FOUND) {
      if (delete_result.status().code() == StatusProto::OK) {
        FAIL() << "Deleted a non-existing namespace without an error.";
      }
      FAIL() << "Icing search engine failure (code "
             << delete_result.status().code()
             << "): " << delete_result.status().message();
    }
  }
}

void IcingMonkeyTestRunner::DoDeleteBySchemaType() {
  std::string schema_type = document_generator_->GetType().schema_type();
  ICING_LOG(INFO) << "Monkey deleting type: " << schema_type;
  DeleteBySchemaTypeResultProto delete_result =
      icing_->DeleteBySchemaType(schema_type);
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t num_docs_deleted,
                             in_memory_icing_->DeleteBySchemaType(schema_type));
  if (num_docs_deleted != 0) {
    ASSERT_THAT(delete_result.status(), ProtoIsOk())
        << "Cannot delete an existing schema type.";
    ASSERT_THAT(delete_result.delete_stats().num_documents_deleted(),
                Eq(num_docs_deleted));
  } else {
    // Should expect that no document has been deleted.
    if (delete_result.status().code() != StatusProto::NOT_FOUND) {
      if (delete_result.status().code() == StatusProto::OK) {
        FAIL() << "Deleted a non-existing schema type without an error.";
      }
      FAIL() << "Icing search engine failure (code "
             << delete_result.status().code()
             << "): " << delete_result.status().message();
    }
  }
}

void IcingMonkeyTestRunner::DoDeleteByQuery() {
  SearchSpecProto search_spec =
      GenerateRandomSearchSpecProto(&random_, document_generator_.get());
  ICING_LOG(INFO) << "Monkey deleting by query: " << search_spec.query();
  DeleteByQueryResultProto delete_result = icing_->DeleteByQuery(search_spec);
  ICING_ASSERT_OK_AND_ASSIGN(uint32_t num_docs_deleted,
                             in_memory_icing_->DeleteByQuery(search_spec));
  if (num_docs_deleted != 0) {
    ASSERT_THAT(delete_result.status(), ProtoIsOk())
        << "Cannot delete documents that matches with the query.";
    ASSERT_THAT(delete_result.delete_by_query_stats().num_documents_deleted(),
                Eq(num_docs_deleted));
  } else {
    // Should expect that no document has been deleted.
    if (delete_result.status().code() != StatusProto::NOT_FOUND) {
      if (delete_result.status().code() == StatusProto::OK) {
        FAIL() << "Deleted documents that should not match with the query "
                  "without an error.";
      }
      FAIL() << "Icing search engine failure (code "
             << delete_result.status().code()
             << "): " << delete_result.status().message();
    }
  }
  ICING_LOG(INFO)
      << delete_result.delete_by_query_stats().num_documents_deleted()
      << " documents deleted by query.";
}

void IcingMonkeyTestRunner::DoSearch() {
  std::unique_ptr<SearchSpecProto> search_spec =
      std::make_unique<SearchSpecProto>(
          GenerateRandomSearchSpecProto(&random_, document_generator_.get()));
  std::unique_ptr<ScoringSpecProto> scoring_spec =
      std::make_unique<ScoringSpecProto>(GenerateRandomScoringSpec(&random_));
  std::unique_ptr<ResultSpecProto> result_spec =
      std::make_unique<ResultSpecProto>(GenerateRandomResultSpecProto(
          &random_, in_memory_icing_->GetSchema()));
  const ResultSpecProto::SnippetSpecProto snippet_spec =
      result_spec->snippet_spec();
  bool is_projection_enabled = !result_spec->type_property_masks().empty();

  ICING_LOG(INFO) << "Monkey searching by query: " << search_spec->query()
                  << ", term_match_type: " << search_spec->term_match_type();
  ICING_VLOG(1) << "search_spec:\n" << search_spec->DebugString();
  ICING_VLOG(1) << "scoring_spec:\n" << scoring_spec->DebugString();
  ICING_VLOG(1) << "result_spec:\n" << result_spec->DebugString();

  ICING_ASSERT_OK_AND_ASSIGN(std::vector<DocumentProto> exp_documents,
                             in_memory_icing_->Search(*search_spec));

  SearchResultProto search_result =
      icing_->Search(*search_spec, *scoring_spec, *result_spec);
  ASSERT_THAT(search_result.status(), ProtoIsOk());

  // Delete all of the specs used in the search. GetNextPage should have no
  // problem because it shouldn't be keeping any references to them.
  search_spec.reset();
  scoring_spec.reset();
  result_spec.reset();

  std::vector<DocumentProto> actual_documents;
  int num_snippeted = 0;
  while (true) {
    for (const SearchResultProto::ResultProto& doc : search_result.results()) {
      actual_documents.push_back(doc.document());
      if (!doc.snippet().entries().empty()) {
        ++num_snippeted;
        for (const SnippetProto::EntryProto& entry : doc.snippet().entries()) {
          ASSERT_THAT(entry.snippet_matches(),
                      SizeIs(Le(snippet_spec.num_matches_per_property())));
        }
      }
    }
    if (search_result.next_page_token() == kInvalidNextPageToken) {
      break;
    }
    search_result = icing_->GetNextPage(search_result.next_page_token());
    ASSERT_THAT(search_result.status(), ProtoIsOk());
  }
  // The maximum number of scored documents allowed in Icing is 30000, in which
  // case we are not able to compare the results with the in-memory Icing.
  if (exp_documents.size() >= 30000) {
    return;
  }
  if (snippet_spec.num_matches_per_property() > 0 && !is_projection_enabled) {
    ASSERT_THAT(num_snippeted,
                Eq(std::min<uint32_t>(exp_documents.size(),
                                      snippet_spec.num_to_snippet())));
  }
  SortDocuments(exp_documents);
  SortDocuments(actual_documents);
  ASSERT_THAT(actual_documents, SizeIs(exp_documents.size()));
  for (int i = 0; i < exp_documents.size(); ++i) {
    if (is_projection_enabled) {
      ASSERT_THAT(actual_documents[i].namespace_(),
                  Eq(exp_documents[i].namespace_()));
      ASSERT_THAT(actual_documents[i].uri(), Eq(exp_documents[i].uri()));
      continue;
    }
    ASSERT_THAT(actual_documents[i], EqualsProto(exp_documents[i]));
  }
  ICING_LOG(INFO) << exp_documents.size() << " documents found by query.";
}

void IcingMonkeyTestRunner::ReloadFromDisk() {
  ICING_LOG(INFO) << "Monkey reloading from disk";
  // Destruct the icing search engine by resetting the unique pointer.
  icing_.reset();
  ASSERT_NO_FATAL_FAILURE(CreateIcingSearchEngine());
}

void IcingMonkeyTestRunner::DoOptimize() {
  ICING_LOG(INFO) << "Monkey doing optimization";
  ASSERT_THAT(icing_->Optimize().status(), ProtoIsOk());
}

void IcingMonkeyTestRunner::CreateIcingSearchEngine() {
  std::uniform_int_distribution<> dist(0, 1);

  bool always_rebuild_index_optimize = dist(random_);
  float optimize_rebuild_index_threshold =
      always_rebuild_index_optimize ? 0.0 : 0.9;

  IcingSearchEngineOptions icing_options;
  icing_options.set_index_merge_size(config_.index_merge_size);
  icing_options.set_base_dir(icing_dir_->dir());
  icing_options.set_optimize_rebuild_index_threshold(
      optimize_rebuild_index_threshold);
  // The method will be called every time when we ReloadFromDisk(), so randomly
  // flip this flag to test document store's compatibility.
  icing_options.set_document_store_namespace_id_fingerprint(
      (bool)dist(random_));
  icing_ = std::make_unique<IcingSearchEngine>(icing_options);
  ASSERT_THAT(icing_->Initialize().status(), ProtoIsOk());
}

}  // namespace lib
}  // namespace icing
