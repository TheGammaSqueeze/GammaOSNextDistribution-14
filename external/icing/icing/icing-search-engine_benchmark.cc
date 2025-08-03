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

#include <unistd.h>

#include <fstream>
#include <iostream>
#include <limits>
#include <memory>
#include <numeric>
#include <ostream>
#include <random>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <unordered_set>
#include <vector>

#include "testing/base/public/benchmark.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/icing-search-engine.h"
#include "icing/join/join-processor.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/initialize.pb.h"
#include "icing/proto/persist.pb.h"
#include "icing/proto/reset.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/scoring.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/status.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/query/query-features.h"
#include "icing/schema-builder.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/document-generator.h"
#include "icing/testing/numeric/number-generator.h"
#include "icing/testing/numeric/uniform-distribution-integer-generator.h"
#include "icing/testing/random-string.h"
#include "icing/testing/schema-generator.h"
#include "icing/testing/tmp-directory.h"

// Run on a Linux workstation:
//    $ blaze build -c opt --dynamic_mode=off --copt=-gmlt
//    //icing:icing-search-engine_benchmark
//
//    $ blaze-bin/icing/icing-search-engine_benchmark
//    --benchmark_filter=all --benchmark_memory_usage
//
// Run on an Android device:
//    $ blaze build --copt="-DGOOGLE_COMMANDLINEFLAGS_FULL_API=1"
//    --config=android_arm64 -c opt --dynamic_mode=off --copt=-gmlt
//    //icing:icing-search-engine_benchmark
//
//    $ adb push blaze-bin/icing/icing-search-engine_benchmark
//    /data/local/tmp/
//
//    $ adb shell /data/local/tmp/icing-search-engine_benchmark
//    --benchmark_filter=all

namespace icing {
namespace lib {

namespace {

using ::testing::Eq;
using ::testing::HasSubstr;

// Icing GMSCore has, on average, 17 corpora on a device and 30 corpora at the
// 95th pct. Most clients use a single type. This is a function of Icing's
// constrained type offering. Assume that each package will use 3 types on
// average.
constexpr int kAvgNumNamespaces = 10;
constexpr int kAvgNumTypes = 3;

// ASSUME: Properties will have at most ten properties. Types will be created
// with [1, 10] properties.
constexpr int kMaxNumProperties = 10;

// Based on logs from Icing GMSCore.
constexpr int kAvgDocumentSize = 300;

// ASSUME: ~75% of the document's size comes from it's content.
constexpr float kContentSizePct = 0.7;

constexpr int kLanguageSize = 1000;

// Lite Index size required to fit 128k docs, each doc requires ~64 bytes of
// space in the lite index.
constexpr int kIcingFullIndexSize = 1024 * 1024 * 8;

// Query params
constexpr int kNumPerPage = 10;
constexpr int kNumToSnippet = 10000;
constexpr int kMatchesPerProperty = 1;

std::vector<std::string> CreateNamespaces(int num_namespaces) {
  std::vector<std::string> namespaces;
  while (--num_namespaces >= 0) {
    namespaces.push_back("comgooglepackage" + std::to_string(num_namespaces));
  }
  return namespaces;
}

SearchSpecProto CreateSearchSpec(const std::string& query,
                                 const std::vector<std::string>& namespaces,
                                 TermMatchType::Code match_type) {
  SearchSpecProto search_spec;
  search_spec.set_query(query);
  for (const std::string& name_space : namespaces) {
    search_spec.add_namespace_filters(name_space);
  }
  search_spec.set_term_match_type(match_type);
  return search_spec;
}

ResultSpecProto CreateResultSpec(int num_per_page, int num_to_snippet,
                                 int matches_per_property) {
  ResultSpecProto result_spec;
  result_spec.set_num_per_page(num_per_page);
  result_spec.mutable_snippet_spec()->set_num_to_snippet(num_to_snippet);
  result_spec.mutable_snippet_spec()->set_num_matches_per_property(
      matches_per_property);
  return result_spec;
}

ScoringSpecProto CreateScoringSpec(
    ScoringSpecProto::RankingStrategy::Code ranking_strategy) {
  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(ranking_strategy);
  return scoring_spec;
}

class DestructibleDirectory {
 public:
  explicit DestructibleDirectory(const Filesystem& filesystem,
                                 const std::string& dir)
      : filesystem_(filesystem), dir_(dir) {
    filesystem_.CreateDirectoryRecursively(dir_.c_str());
  }
  ~DestructibleDirectory() {
    filesystem_.DeleteDirectoryRecursively(dir_.c_str());
  }

 private:
  Filesystem filesystem_;
  std::string dir_;
};

std::vector<DocumentProto> GenerateRandomDocuments(
    EvenDistributionTypeSelector* type_selector, int num_docs,
    const std::vector<std::string>& language) {
  std::vector<std::string> namespaces = CreateNamespaces(kAvgNumNamespaces);
  EvenDistributionNamespaceSelector namespace_selector(namespaces);

  std::default_random_engine random;
  UniformDistributionLanguageTokenGenerator<std::default_random_engine>
      token_generator(language, &random);

  DocumentGenerator<
      EvenDistributionNamespaceSelector, EvenDistributionTypeSelector,
      UniformDistributionLanguageTokenGenerator<std::default_random_engine>>
      generator(&namespace_selector, type_selector, &token_generator,
                kAvgDocumentSize * kContentSizePct);

  std::vector<DocumentProto> random_docs;
  random_docs.reserve(num_docs);
  for (int i = 0; i < num_docs; i++) {
    random_docs.push_back(generator.generateDoc());
  }
  return random_docs;
}

std::unique_ptr<NumberGenerator<int64_t>> CreateIntegerGenerator(
    size_t num_documents) {
  // Since the collision # follows poisson distribution with lambda =
  // (num_keys / range), we set the range 10x (lambda = 0.1) to avoid too many
  // collisions.
  //
  // Distribution:
  // - keys in range being picked for 0 times: 90.5%
  // - keys in range being picked for 1 time:  9%
  // - keys in range being picked for 2 times: 0.45%
  // - keys in range being picked for 3 times: 0.015%
  //
  // For example, num_keys = 1M, range = 10M. Then there will be ~904837 unique
  // keys, 45242 keys being picked twice, 1508 keys being picked thrice ...
  return std::make_unique<UniformDistributionIntegerGenerator<int64_t>>(
      /*seed=*/12345, /*range_lower=*/0,
      /*range_upper=*/static_cast<int64_t>(num_documents) * 10 - 1);
}

void BM_IndexLatency(benchmark::State& state) {
  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  std::default_random_engine random;
  int num_types = kAvgNumNamespaces * kAvgNumTypes;
  ExactStringPropertyGenerator property_generator;
  SchemaGenerator<ExactStringPropertyGenerator> schema_generator(
      /*num_properties=*/state.range(1), &property_generator);
  SchemaProto schema = schema_generator.GenerateSchema(num_types);
  EvenDistributionTypeSelector type_selector(schema);

  // Create the index.
  IcingSearchEngineOptions options;
  options.set_base_dir(test_dir);
  options.set_index_merge_size(kIcingFullIndexSize);
  std::unique_ptr<IcingSearchEngine> icing =
      std::make_unique<IcingSearchEngine>(options);

  int num_docs = state.range(0);
  std::vector<std::string> language = CreateLanguages(kLanguageSize, &random);
  const std::vector<DocumentProto> random_docs =
      GenerateRandomDocuments(&type_selector, num_docs, language);
  for (auto _ : state) {
    state.PauseTiming();
    ASSERT_THAT(icing->Reset().status(), ProtoIsOk());
    ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());
    state.ResumeTiming();
    for (const DocumentProto& doc : random_docs) {
      ASSERT_THAT(icing->Put(doc).status(), ProtoIsOk());
    }
  }
}
BENCHMARK(BM_IndexLatency)
    // Arguments: num_indexed_documents, num_sections
    ->ArgPair(1000000, 5);

void BM_QueryLatency(benchmark::State& state) {
  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  std::default_random_engine random;
  int num_types = kAvgNumNamespaces * kAvgNumTypes;
  ExactStringPropertyGenerator property_generator;
  SchemaGenerator<ExactStringPropertyGenerator> schema_generator(
      /*num_properties=*/state.range(1), &property_generator);
  SchemaProto schema = schema_generator.GenerateSchema(num_types);
  EvenDistributionTypeSelector type_selector(schema);

  // Create the index.
  IcingSearchEngineOptions options;
  options.set_base_dir(test_dir);
  options.set_index_merge_size(kIcingFullIndexSize);
  std::unique_ptr<IcingSearchEngine> icing =
      std::make_unique<IcingSearchEngine>(options);

  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());

  int num_docs = state.range(0);
  std::vector<std::string> language = CreateLanguages(kLanguageSize, &random);
  const std::vector<DocumentProto> random_docs =
      GenerateRandomDocuments(&type_selector, num_docs, language);
  for (const DocumentProto& doc : random_docs) {
    ASSERT_THAT(icing->Put(doc).status(), ProtoIsOk());
  }

  SearchSpecProto search_spec = CreateSearchSpec(
      language.at(0), std::vector<std::string>(), TermMatchType::PREFIX);
  ResultSpecProto result_spec = CreateResultSpec(1, 1000000, 1000000);
  ScoringSpecProto scoring_spec =
      CreateScoringSpec(ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP);
  for (auto _ : state) {
    SearchResultProto results = icing->Search(
        search_spec, ScoringSpecProto::default_instance(), result_spec);
  }
}
BENCHMARK(BM_QueryLatency)
    // Arguments: num_indexed_documents, num_sections
    ->ArgPair(1000000, 2);

void BM_IndexThroughput(benchmark::State& state) {
  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  std::default_random_engine random;
  int num_types = kAvgNumNamespaces * kAvgNumTypes;
  ExactStringPropertyGenerator property_generator;
  SchemaGenerator<ExactStringPropertyGenerator> schema_generator(
      /*num_properties=*/state.range(1), &property_generator);
  SchemaProto schema = schema_generator.GenerateSchema(num_types);
  EvenDistributionTypeSelector type_selector(schema);

  // Create the index.
  IcingSearchEngineOptions options;
  options.set_base_dir(test_dir);
  options.set_index_merge_size(kIcingFullIndexSize);
  std::unique_ptr<IcingSearchEngine> icing =
      std::make_unique<IcingSearchEngine>(options);

  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());

  int num_docs = state.range(0);
  std::vector<std::string> language = CreateLanguages(kLanguageSize, &random);
  const std::vector<DocumentProto> random_docs =
      GenerateRandomDocuments(&type_selector, num_docs, language);
  for (auto s : state) {
    for (const DocumentProto& doc : random_docs) {
      ASSERT_THAT(icing->Put(doc).status(), ProtoIsOk());
    }
  }
  state.SetItemsProcessed(state.iterations() * num_docs);
}
BENCHMARK(BM_IndexThroughput)
    // Arguments: num_indexed_documents, num_sections
    ->ArgPair(1, 1)
    ->ArgPair(2, 1)
    ->ArgPair(8, 1)
    ->ArgPair(32, 1)
    ->ArgPair(128, 1)
    ->ArgPair(1 << 10, 1)
    ->ArgPair(1 << 13, 1)
    ->ArgPair(1 << 15, 1)
    ->ArgPair(1 << 17, 1)
    ->ArgPair(1, 5)
    ->ArgPair(2, 5)
    ->ArgPair(8, 5)
    ->ArgPair(32, 5)
    ->ArgPair(128, 5)
    ->ArgPair(1 << 10, 5)
    ->ArgPair(1 << 13, 5)
    ->ArgPair(1 << 15, 5)
    ->ArgPair(1 << 17, 5)
    ->ArgPair(1, 10)
    ->ArgPair(2, 10)
    ->ArgPair(8, 10)
    ->ArgPair(32, 10)
    ->ArgPair(128, 10)
    ->ArgPair(1 << 10, 10)
    ->ArgPair(1 << 13, 10)
    ->ArgPair(1 << 15, 10)
    ->ArgPair(1 << 17, 10);

void BM_MutlipleIndices(benchmark::State& state) {
  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  std::default_random_engine random;
  int num_types = kAvgNumNamespaces * kAvgNumTypes;
  ExactStringPropertyGenerator property_generator;
  RandomSchemaGenerator<std::default_random_engine,
                        ExactStringPropertyGenerator>
      schema_generator(&random, &property_generator);
  SchemaProto schema =
      schema_generator.GenerateSchema(num_types, kMaxNumProperties);
  EvenDistributionTypeSelector type_selector(schema);

  // Create the indices.
  std::vector<std::unique_ptr<IcingSearchEngine>> icings;
  int num_indices = state.range(0);
  for (int i = 0; i < num_indices; ++i) {
    IcingSearchEngineOptions options;
    std::string base_dir = test_dir + "/" + std::to_string(i);
    options.set_base_dir(base_dir);
    options.set_index_merge_size(kIcingFullIndexSize / num_indices);
    auto icing = std::make_unique<IcingSearchEngine>(options);

    ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());
    icings.push_back(std::move(icing));
  }

  // Setup namespace info and language
  std::vector<std::string> namespaces = CreateNamespaces(kAvgNumNamespaces);
  EvenDistributionNamespaceSelector namespace_selector(namespaces);

  std::vector<std::string> language = CreateLanguages(kLanguageSize, &random);
  UniformDistributionLanguageTokenGenerator<std::default_random_engine>
      token_generator(language, &random);

  // Fill the index.
  DocumentGenerator<
      EvenDistributionNamespaceSelector, EvenDistributionTypeSelector,
      UniformDistributionLanguageTokenGenerator<std::default_random_engine>>
      generator(&namespace_selector, &type_selector, &token_generator,
                kAvgDocumentSize * kContentSizePct);
  for (int i = 0; i < state.range(1); ++i) {
    DocumentProto doc = generator.generateDoc();
    PutResultProto put_result;
    if (icings.empty()) {
      ASSERT_THAT(put_result.status().code(), Eq(StatusProto::UNKNOWN));
      continue;
    }
    ASSERT_THAT(icings.at(i % icings.size())->Put(doc).status(), ProtoIsOk());
  }

  // QUERY!
  // Every document has its own namespace as a token. This query that should
  // match 1/kAvgNumNamespace% of all documents.
  const std::string& name_space = namespaces.at(0);
  SearchSpecProto search_spec = CreateSearchSpec(
      /*query=*/name_space, {name_space}, TermMatchType::EXACT_ONLY);
  ResultSpecProto result_spec =
      CreateResultSpec(kNumPerPage, kNumToSnippet, kMatchesPerProperty);
  ScoringSpecProto scoring_spec =
      CreateScoringSpec(ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP);

  int num_results = 0;
  for (auto _ : state) {
    num_results = 0;
    SearchResultProto result;
    if (icings.empty()) {
      ASSERT_THAT(result.status().code(), Eq(StatusProto::UNKNOWN));
      continue;
    }
    result = icings.at(0)->Search(search_spec, scoring_spec, result_spec);
    ASSERT_THAT(result.status(), ProtoIsOk());
    while (!result.results().empty()) {
      num_results += result.results_size();
      if (!icings.empty()) {
        result = icings.at(0)->GetNextPage(result.next_page_token());
      }
      ASSERT_THAT(result.status(), ProtoIsOk());
    }
  }

  // Measure size.
  int64_t disk_usage = filesystem.GetDiskUsage(test_dir.c_str());
  std::cout << "Num results:\t" << num_results << "\t\tDisk Use:\t"
            << disk_usage / 1024.0 << std::endl;
}
BENCHMARK(BM_MutlipleIndices)
    // First argument: num_indices, Second argument: num_total_documents
    // So each index will contain (num_total_documents / num_indices) documents.
    ->ArgPair(0, 0)
    ->ArgPair(0, 1024)
    ->ArgPair(0, 131072)
    ->ArgPair(1, 0)
    ->ArgPair(1, 1)
    ->ArgPair(1, 2)
    ->ArgPair(1, 8)
    ->ArgPair(1, 32)
    ->ArgPair(1, 128)
    ->ArgPair(1, 1024)
    ->ArgPair(1, 8192)
    ->ArgPair(1, 32768)
    ->ArgPair(1, 131072)
    ->ArgPair(2, 0)
    ->ArgPair(2, 1)
    ->ArgPair(2, 2)
    ->ArgPair(2, 8)
    ->ArgPair(2, 32)
    ->ArgPair(2, 128)
    ->ArgPair(2, 1024)
    ->ArgPair(2, 8192)
    ->ArgPair(2, 32768)
    ->ArgPair(2, 131072)
    ->ArgPair(10, 0)
    ->ArgPair(10, 1)
    ->ArgPair(10, 2)
    ->ArgPair(10, 8)
    ->ArgPair(10, 32)
    ->ArgPair(10, 128)
    ->ArgPair(10, 1024)
    ->ArgPair(10, 8192)
    ->ArgPair(10, 32768)
    ->ArgPair(10, 131072);

void BM_SearchNoStackOverflow(benchmark::State& state) {
  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TermMatchType::PREFIX,
                                     StringIndexingConfig::TokenizerType::PLAIN)
                  .SetCardinality(PropertyConfigProto::Cardinality::OPTIONAL)))
          .Build();

  // Create the index.
  IcingSearchEngineOptions options;
  options.set_base_dir(test_dir);
  options.set_index_merge_size(kIcingFullIndexSize);
  std::unique_ptr<IcingSearchEngine> icing =
      std::make_unique<IcingSearchEngine>(options);

  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());

  // Create a document that has the term "foo"
  DocumentProto base_document = DocumentBuilder()
                                    .SetSchema("Message")
                                    .SetNamespace("namespace")
                                    .AddStringProperty("body", "foo")
                                    .Build();

  // Insert a lot of documents with the term "foo"
  int64_t num_docs = state.range(0);
  for (int64_t i = 0; i < num_docs; ++i) {
    DocumentProto document =
        DocumentBuilder(base_document).SetUri(std::to_string(i)).Build();
    ASSERT_THAT(icing->Put(document).status(), ProtoIsOk());
  }

  // Do a query and exclude documents with the term "foo". The way this is
  // currently implemented is that we'll iterate over all the documents in the
  // index, then apply the exclusion check. Since all our documents have "foo",
  // we'll consider it a "miss". Previously with recursion, we would have
  // recursed until we got a success, which would never happen causing us to
  // recurse through all the documents and trigger a stack overflow. With
  // the iterative implementation, we should avoid this.
  SearchSpecProto search_spec;
  search_spec.set_query("-foo");
  search_spec.set_term_match_type(TermMatchType::PREFIX);

  ResultSpecProto result_spec;
  ScoringSpecProto scoring_spec;
  for (auto s : state) {
    icing->Search(search_spec, scoring_spec, result_spec);
  }
}
// For other reasons, we hit a limit when inserting the ~350,000th document. So
// cap the limit to 1 << 18.
BENCHMARK(BM_SearchNoStackOverflow)
    ->Range(/*start=*/1 << 10, /*limit=*/1 << 18);

// Added for b/184373205. Ensure that we can repeatedly put documents even if
// the underlying mmapped areas grow past a few page sizes.
void BM_RepeatedPut(benchmark::State& state) {
  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TermMatchType::PREFIX,
                                     StringIndexingConfig::TokenizerType::PLAIN)
                  .SetCardinality(PropertyConfigProto::Cardinality::OPTIONAL)))
          .Build();

  // Create the index.
  IcingSearchEngineOptions options;
  options.set_base_dir(test_dir);
  options.set_index_merge_size(kIcingFullIndexSize);
  std::unique_ptr<IcingSearchEngine> icing =
      std::make_unique<IcingSearchEngine>(options);

  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());

  // Create a document that has the term "foo"
  DocumentProto base_document = DocumentBuilder()
                                    .SetSchema("Message")
                                    .SetNamespace("namespace")
                                    .AddStringProperty("body", "foo")
                                    .Build();

  // Insert a lot of documents with the term "foo"
  int64_t num_docs = state.range(0);
  for (auto s : state) {
    for (int64_t i = 0; i < num_docs; ++i) {
      DocumentProto document =
          DocumentBuilder(base_document).SetUri("uri").Build();
      ASSERT_THAT(icing->Put(document).status(), ProtoIsOk());
    }
  }
}
// For other reasons, we hit a limit when inserting the ~350,000th document. So
// cap the limit to 1 << 18.
BENCHMARK(BM_RepeatedPut)->Range(/*start=*/100, /*limit=*/1 << 18);

// This is different from BM_RepeatedPut since we're just trying to benchmark
// one Put call, not thousands of them at once.
void BM_Put(benchmark::State& state) {
  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message"))
          .Build();

  // Create the index.
  IcingSearchEngineOptions options;
  options.set_base_dir(test_dir);
  options.set_index_merge_size(kIcingFullIndexSize);
  std::unique_ptr<IcingSearchEngine> icing =
      std::make_unique<IcingSearchEngine>(options);

  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());

  // Create a document
  DocumentProto document = DocumentBuilder()
                               .SetSchema("Message")
                               .SetNamespace("namespace")
                               .SetUri("uri")
                               .Build();

  for (auto s : state) {
    benchmark::DoNotOptimize(icing->Put(document));
  }
}
BENCHMARK(BM_Put);

void BM_Get(benchmark::State& state) {
  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message"))
          .Build();

  // Create the index.
  IcingSearchEngineOptions options;
  options.set_base_dir(test_dir);
  options.set_index_merge_size(kIcingFullIndexSize);
  std::unique_ptr<IcingSearchEngine> icing =
      std::make_unique<IcingSearchEngine>(options);

  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());

  // Create a document
  DocumentProto document = DocumentBuilder()
                               .SetSchema("Message")
                               .SetNamespace("namespace")
                               .SetUri("uri")
                               .Build();

  ASSERT_THAT(icing->Put(document).status(), ProtoIsOk());
  for (auto s : state) {
    benchmark::DoNotOptimize(
        icing->Get("namespace", "uri", GetResultSpecProto::default_instance()));
  }
}
BENCHMARK(BM_Get);

void BM_Delete(benchmark::State& state) {
  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message"))
          .Build();

  // Create the index.
  IcingSearchEngineOptions options;
  options.set_base_dir(test_dir);
  options.set_index_merge_size(kIcingFullIndexSize);
  std::unique_ptr<IcingSearchEngine> icing =
      std::make_unique<IcingSearchEngine>(options);

  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());

  // Create a document
  DocumentProto document = DocumentBuilder()
                               .SetSchema("Message")
                               .SetNamespace("namespace")
                               .SetUri("uri")
                               .Build();

  ASSERT_THAT(icing->Put(document).status(), ProtoIsOk());
  for (auto s : state) {
    state.PauseTiming();
    icing->Put(document);
    state.ResumeTiming();

    benchmark::DoNotOptimize(icing->Delete("namespace", "uri"));
  }
}
BENCHMARK(BM_Delete);

void BM_PutMaxAllowedDocuments(benchmark::State& state) {
  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TermMatchType::PREFIX,
                                     StringIndexingConfig::TokenizerType::PLAIN)
                  .SetCardinality(PropertyConfigProto::Cardinality::OPTIONAL)))
          .Build();

  // Create the index.
  IcingSearchEngineOptions options;
  options.set_base_dir(test_dir);
  options.set_index_merge_size(kIcingFullIndexSize);
  std::unique_ptr<IcingSearchEngine> icing =
      std::make_unique<IcingSearchEngine>(options);

  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());

  // Create a document that has the term "foo"
  DocumentProto base_document = DocumentBuilder()
                                    .SetSchema("Message")
                                    .SetNamespace("namespace")
                                    .AddStringProperty("body", "foo")
                                    .Build();

  // Insert a lot of documents with the term "foo"
  for (auto s : state) {
    for (int64_t i = 0; i <= kMaxDocumentId; ++i) {
      DocumentProto document =
          DocumentBuilder(base_document).SetUri(std::to_string(i)).Build();
      EXPECT_THAT(icing->Put(document).status(), ProtoIsOk());
    }
  }

  DocumentProto document =
      DocumentBuilder(base_document).SetUri("out_of_space_uri").Build();
  PutResultProto put_result_proto = icing->Put(document);
  EXPECT_THAT(put_result_proto.status(),
              ProtoStatusIs(StatusProto::OUT_OF_SPACE));
  EXPECT_THAT(put_result_proto.status().message(),
              HasSubstr("Exceeded maximum number of documents"));
}
BENCHMARK(BM_PutMaxAllowedDocuments);

void BM_QueryWithSnippet(benchmark::State& state) {
  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Create the index.
  IcingSearchEngineOptions options;
  options.set_base_dir(test_dir);
  options.set_index_merge_size(kIcingFullIndexSize);
  std::unique_ptr<IcingSearchEngine> icing =
      std::make_unique<IcingSearchEngine>(options);

  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());

  std::string body = "message body";
  for (int i = 0; i < 100; i++) {
    body = body +
           " invent invention inventory invest investigate investigation "
           "investigator investment nvestor invisible invitation invite "
           "involve involved involvement IraqiI rish island";
  }
  for (int i = 0; i < 50; i++) {
    DocumentProto document = DocumentBuilder()
                                 .SetKey("namespace", "uri" + std::to_string(i))
                                 .SetSchema("Message")
                                 .AddStringProperty("body", body)
                                 .Build();
    ASSERT_THAT(icing->Put(std::move(document)).status(), ProtoIsOk());
  }

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("i");

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(10000);
  result_spec.mutable_snippet_spec()->set_max_window_utf32_length(64);
  result_spec.mutable_snippet_spec()->set_num_matches_per_property(10000);
  result_spec.mutable_snippet_spec()->set_num_to_snippet(10000);

  for (auto s : state) {
    SearchResultProto results = icing->Search(
        search_spec, ScoringSpecProto::default_instance(), result_spec);
  }
}
BENCHMARK(BM_QueryWithSnippet);

void BM_NumericIndexing(benchmark::State& state) {
  int num_documents = state.range(0);
  int num_integers_per_doc = state.range(1);

  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;

  // Create the schema.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Message")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("integer")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  std::unique_ptr<NumberGenerator<int64_t>> integer_generator =
      CreateIntegerGenerator(num_documents);
  std::vector<DocumentProto> documents;
  documents.reserve(num_documents);
  for (int i = 0; i < num_documents; ++i) {
    std::vector<int64_t> integers;
    integers.reserve(num_integers_per_doc);
    for (int j = 0; j < num_integers_per_doc; ++j) {
      integers.push_back(integer_generator->Generate());
    }

    DocumentProto document =
        DocumentBuilder()
            .SetKey("namespace", "uri" + std::to_string(i))
            .SetSchema("Message")
            .AddStringProperty("body", "body hello world")
            .AddInt64Property("integer", integers.begin(), integers.end())
            .Build();
    documents.push_back(std::move(document));
  }

  for (auto s : state) {
    state.PauseTiming();
    // Create the index.
    IcingSearchEngineOptions options;
    options.set_base_dir(test_dir);
    options.set_index_merge_size(kIcingFullIndexSize);
    std::unique_ptr<IcingSearchEngine> icing =
        std::make_unique<IcingSearchEngine>(options);

    ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());
    state.ResumeTiming();

    for (const DocumentProto& document : documents) {
      ASSERT_THAT(icing->Put(document).status(), ProtoIsOk());
    }

    state.PauseTiming();
    icing.reset();
    ASSERT_TRUE(filesystem.DeleteDirectoryRecursively(test_dir.c_str()));
    state.ResumeTiming();
  }
}

BENCHMARK(BM_NumericIndexing)
    // Arguments: num_documents, num_integers_per_doc
    ->ArgPair(1000000, 5);

void BM_NumericExactQuery(benchmark::State& state) {
  int num_documents = state.range(0);
  int num_integers_per_doc = state.range(1);

  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Message")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("integer")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  // Create the index.
  IcingSearchEngineOptions options;
  options.set_base_dir(test_dir);
  options.set_index_merge_size(kIcingFullIndexSize);
  std::unique_ptr<IcingSearchEngine> icing =
      std::make_unique<IcingSearchEngine>(options);

  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());

  std::unique_ptr<NumberGenerator<int64_t>> integer_generator =
      CreateIntegerGenerator(num_documents);
  std::unordered_set<int64_t> chosen_integer_set;
  for (int i = 0; i < num_documents; ++i) {
    std::vector<int64_t> integers;
    integers.reserve(num_integers_per_doc);
    for (int j = 0; j < num_integers_per_doc; ++j) {
      int64_t chosen_int = integer_generator->Generate();
      integers.push_back(chosen_int);
      chosen_integer_set.insert(chosen_int);
    }

    DocumentProto document =
        DocumentBuilder()
            .SetKey("namespace", "uri" + std::to_string(i))
            .SetSchema("Message")
            .AddStringProperty("body", "body hello world")
            .AddInt64Property("integer", integers.begin(), integers.end())
            .Build();
    ASSERT_THAT(icing->Put(std::move(document)).status(), ProtoIsOk());
  }

  SearchSpecProto search_spec;
  search_spec.set_search_type(
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY);
  search_spec.add_enabled_features(std::string(kNumericSearchFeature));

  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(1);

  std::vector<int64_t> chosen_integers(chosen_integer_set.begin(),
                                       chosen_integer_set.end());
  std::uniform_int_distribution<> distrib(0, chosen_integers.size() - 1);
  std::default_random_engine e(/*seed=*/12345);
  for (auto s : state) {
    int64_t exact = chosen_integers[distrib(e)];
    search_spec.set_query("integer == " + std::to_string(exact));

    SearchResultProto results =
        icing->Search(search_spec, scoring_spec, result_spec);
    ASSERT_THAT(results.status(), ProtoIsOk());
    ASSERT_GT(results.results_size(), 0);
    if (results.next_page_token() != kInvalidNextPageToken) {
      icing->InvalidateNextPageToken(results.next_page_token());
    }
  }
}
BENCHMARK(BM_NumericExactQuery)
    // Arguments: num_documents, num_integers_per_doc
    ->ArgPair(1000000, 5);

void BM_NumericRangeQueryAll(benchmark::State& state) {
  int num_documents = state.range(0);
  int num_integers_per_doc = state.range(1);

  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Message")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("integer")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  // Create the index.
  IcingSearchEngineOptions options;
  options.set_base_dir(test_dir);
  options.set_index_merge_size(kIcingFullIndexSize);
  std::unique_ptr<IcingSearchEngine> icing =
      std::make_unique<IcingSearchEngine>(options);

  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());

  std::unique_ptr<NumberGenerator<int64_t>> integer_generator =
      CreateIntegerGenerator(num_documents);
  for (int i = 0; i < num_documents; ++i) {
    std::vector<int64_t> integers;
    integers.reserve(num_integers_per_doc);
    for (int j = 0; j < num_integers_per_doc; ++j) {
      integers.push_back(integer_generator->Generate());
    }

    DocumentProto document =
        DocumentBuilder()
            .SetKey("namespace", "uri" + std::to_string(i))
            .SetSchema("Message")
            .AddStringProperty("body", "body hello world")
            .AddInt64Property("integer", integers.begin(), integers.end())
            .Build();
    ASSERT_THAT(icing->Put(std::move(document)).status(), ProtoIsOk());
  }

  SearchSpecProto search_spec;
  search_spec.set_search_type(
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY);
  search_spec.add_enabled_features(std::string(kNumericSearchFeature));
  search_spec.set_query("integer >= " +
                        std::to_string(std::numeric_limits<int64_t>::min()));

  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(1);

  for (auto s : state) {
    SearchResultProto results =
        icing->Search(search_spec, scoring_spec, result_spec);
    ASSERT_THAT(results.status(), ProtoIsOk());
    ASSERT_GT(results.results_size(), 0);
    if (results.next_page_token() != kInvalidNextPageToken) {
      icing->InvalidateNextPageToken(results.next_page_token());
    }
  }
}
BENCHMARK(BM_NumericRangeQueryAll)
    // Arguments: num_documents, num_integers_per_doc
    ->ArgPair(1000000, 5);

void BM_JoinQueryQualifiedId(benchmark::State& state) {
  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Person")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("firstName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("lastName")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("emailAddress")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Email")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("subject")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("personQualifiedId")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Create the index.
  IcingSearchEngineOptions options;
  options.set_base_dir(test_dir);
  options.set_index_merge_size(kIcingFullIndexSize);
  options.set_document_store_namespace_id_fingerprint(true);
  options.set_use_new_qualified_id_join_index(true);
  std::unique_ptr<IcingSearchEngine> icing =
      std::make_unique<IcingSearchEngine>(options);

  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());

  // Create Person documents (parent)
  static constexpr int kNumPersonDocuments = 1000;
  for (int i = 0; i < kNumPersonDocuments; ++i) {
    std::string person_id = std::to_string(i);
    DocumentProto person =
        DocumentBuilder()
            .SetKey("pkg$db/namespace", "person" + person_id)
            .SetSchema("Person")
            .AddStringProperty("firstName", "first" + person_id)
            .AddStringProperty("lastName", "last" + person_id)
            .AddStringProperty("emailAddress",
                               "person" + person_id + "@gmail.com")
            .Build();
    ASSERT_THAT(icing->Put(std::move(person)).status(), ProtoIsOk());
  }

  // Create Email documents (child)
  static constexpr int kNumEmailDocuments = 1000;
  std::uniform_int_distribution<> distrib(0, kNumPersonDocuments - 1);
  std::default_random_engine e(/*seed=*/12345);
  for (int i = 0; i < kNumEmailDocuments; ++i) {
    std::string email_id = std::to_string(i);
    std::string person_id = std::to_string(distrib(e));
    DocumentProto email =
        DocumentBuilder()
            .SetKey("namespace", "email" + email_id)
            .SetSchema("Email")
            .AddStringProperty("subject", "test subject " + email_id)
            .AddStringProperty("body", "message body")
            .AddStringProperty("personQualifiedId",
                               "pkg$db/namespace#person" + person_id)
            .Build();
    ASSERT_THAT(icing->Put(std::move(email)).status(), ProtoIsOk());
  }

  // Parent SearchSpec
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("firstName:first");

  // JoinSpec
  JoinSpecProto* join_spec = search_spec.mutable_join_spec();
  join_spec->set_parent_property_expression(
      std::string(JoinProcessor::kQualifiedIdExpr));
  join_spec->set_child_property_expression("personQualifiedId");
  join_spec->set_aggregation_scoring_strategy(
      JoinSpecProto::AggregationScoringStrategy::MAX);
  JoinSpecProto::NestedSpecProto* nested_spec =
      join_spec->mutable_nested_spec();
  SearchSpecProto* nested_search_spec = nested_spec->mutable_search_spec();
  nested_search_spec->set_term_match_type(TermMatchType::PREFIX);
  nested_search_spec->set_query("subject:test");
  *nested_spec->mutable_scoring_spec() = ScoringSpecProto::default_instance();
  *nested_spec->mutable_result_spec() = ResultSpecProto::default_instance();

  static constexpr int kNumPerPage = 10;
  ResultSpecProto result_spec;
  result_spec.set_num_per_page(kNumPerPage);
  result_spec.set_max_joined_children_per_parent_to_return(
      std::numeric_limits<int32_t>::max());

  ScoringSpecProto score_spec = ScoringSpecProto::default_instance();

  const auto child_count_reduce_func =
      [](int child_count, const SearchResultProto::ResultProto& result) -> int {
    return child_count + result.joined_results_size();
  };
  for (auto s : state) {
    int total_parent_count = 0;
    int total_child_count = 0;
    SearchResultProto results =
        icing->Search(search_spec, score_spec, result_spec);
    total_parent_count += results.results_size();
    total_child_count +=
        std::reduce(results.results().begin(), results.results().end(), 0,
                    child_count_reduce_func);

    ASSERT_THAT(total_parent_count, Eq(kNumPerPage));
    ASSERT_THAT(total_child_count, ::testing::Ge(0));
  }
}
BENCHMARK(BM_JoinQueryQualifiedId);

void BM_PersistToDisk(benchmark::State& state) {
  // Initialize the filesystem
  std::string test_dir = GetTestTempDir() + "/icing/benchmark";
  Filesystem filesystem;
  DestructibleDirectory ddir(filesystem, test_dir);

  // Create the schema.
  std::default_random_engine random;
  int num_types = kAvgNumNamespaces * kAvgNumTypes;
  ExactStringPropertyGenerator property_generator;
  SchemaGenerator<ExactStringPropertyGenerator> schema_generator(
      /*num_properties=*/state.range(1), &property_generator);
  SchemaProto schema = schema_generator.GenerateSchema(num_types);
  EvenDistributionTypeSelector type_selector(schema);

  // Generate documents.
  int num_docs = state.range(0);
  std::vector<std::string> language = CreateLanguages(kLanguageSize, &random);
  const std::vector<DocumentProto> random_docs =
      GenerateRandomDocuments(&type_selector, num_docs, language);

  for (auto _ : state) {
    state.PauseTiming();
    // Create the index.
    IcingSearchEngineOptions options;
    options.set_base_dir(test_dir);
    options.set_index_merge_size(kIcingFullIndexSize);
    options.set_use_persistent_hash_map(true);
    std::unique_ptr<IcingSearchEngine> icing =
        std::make_unique<IcingSearchEngine>(options);

    ASSERT_THAT(icing->Reset().status(), ProtoIsOk());
    ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());

    for (const DocumentProto& doc : random_docs) {
      ASSERT_THAT(icing->Put(doc).status(), ProtoIsOk());
    }

    state.ResumeTiming();

    ASSERT_THAT(icing->PersistToDisk(PersistType::FULL).status(), ProtoIsOk());

    state.PauseTiming();
    icing.reset();
    ASSERT_TRUE(filesystem.DeleteDirectoryRecursively(test_dir.c_str()));
    state.ResumeTiming();
  }
}
BENCHMARK(BM_PersistToDisk)
    // Arguments: num_indexed_documents, num_sections
    ->ArgPair(1024, 5);

}  // namespace

}  // namespace lib
}  // namespace icing
