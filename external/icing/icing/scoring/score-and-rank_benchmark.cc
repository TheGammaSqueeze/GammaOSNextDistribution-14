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

#include <algorithm>
#include <cstdint>
#include <limits>
#include <memory>
#include <random>
#include <string>
#include <utility>
#include <vector>

#include "testing/base/public/benchmark.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator-test-util.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/scoring.pb.h"
#include "icing/schema/schema-store.h"
#include "icing/scoring/ranker.h"
#include "icing/scoring/scored-document-hit.h"
#include "icing/scoring/scoring-processor.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"
#include "icing/util/clock.h"

// This is an overall benchmark for ScoringProcessor, Scorer, and Ranker. It
// shows how performance varies when we score different numbers of document
// hits.
//
// Run on a Linux workstation:
//    $ blaze build -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/scoring:score-and-rank_benchmark
//
//    $ blaze-bin/icing/scoring/score-and-rank_benchmark
//    --benchmark_filter=all --benchmark_memory_usage
//
// Run on an Android device:
//    $ blaze build --copt="-DGOOGLE_COMMANDLINEFLAGS_FULL_API=1"
//    --config=android_arm64 -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/scoring:score-and-rank_benchmark
//
//    $ adb push blaze-bin/icing/scoring/score-and-rank_benchmark
//    /data/local/tmp/
//
//    $ adb shell /data/local/tmp/score-and-rank_benchmark
//    --benchmark_filter=all

namespace icing {
namespace lib {

namespace {

SchemaProto CreateSchemaWithEmailType() {
  SchemaProto schema;
  auto type_config = schema.add_types();
  type_config->set_schema_type("Email");
  auto subject = type_config->add_properties();
  subject->set_property_name("subject");
  subject->set_data_type(PropertyConfigProto::DataType::STRING);
  subject->set_cardinality(PropertyConfigProto::Cardinality::OPTIONAL);
  return schema;
}

DocumentProto CreateEmailDocument(int id, int document_score,
                                  uint64_t creation_timestamp_ms) {
  return DocumentBuilder()
      .SetKey("icing", "uri" + std::to_string(id))
      .SetSchema("Email")
      .AddStringProperty("subject", "subject" + std::to_string(id))
      .SetScore(document_score)
      .SetCreationTimestampMs(creation_timestamp_ms)
      .Build();
}

libtextclassifier3::StatusOr<DocumentStore::CreateResult> CreateDocumentStore(
    const Filesystem* filesystem, const std::string& base_dir,
    const Clock* clock, const SchemaStore* schema_store) {
  return DocumentStore::Create(
      filesystem, base_dir, clock, schema_store,
      /*force_recovery_and_revalidate_documents=*/false,
      /*namespace_id_fingerprint=*/false, /*pre_mapping_fbv=*/false,
      /*use_persistent_hash_map=*/false,
      PortableFileBackedProtoLog<DocumentWrapper>::kDeflateCompressionLevel,
      /*initialize_stats=*/nullptr);
}

void BM_ScoreAndRankDocumentHitsByDocumentScore(benchmark::State& state) {
  const std::string base_dir = GetTestTempDir() + "/score_and_rank_benchmark";
  const std::string document_store_dir = base_dir + "/document_store";
  const std::string schema_store_dir = base_dir + "/schema_store";

  // Creates file directories
  Filesystem filesystem;
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
  ASSERT_TRUE(
      filesystem.CreateDirectoryRecursively(document_store_dir.c_str()));
  ASSERT_TRUE(filesystem.CreateDirectoryRecursively(schema_store_dir.c_str()));

  Clock clock;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem, schema_store_dir, &clock));

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentStore::CreateResult create_result,
      CreateDocumentStore(&filesystem, document_store_dir, &clock,
                          schema_store.get()));
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  ICING_ASSERT_OK(schema_store->SetSchema(
      CreateSchemaWithEmailType(), /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(scoring_spec, document_store.get(),
                               schema_store.get(),
                               clock.GetSystemTimeMilliseconds()));
  int num_to_score = state.range(0);
  int num_of_documents = state.range(1);

  std::mt19937 random_generator;
  std::uniform_int_distribution<int> distribution(
      1, std::numeric_limits<int>::max());

  // Puts documents into document store
  std::vector<DocHitInfo> doc_hit_infos;
  for (int i = 0; i < num_of_documents; i++) {
    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentId document_id,
        document_store->Put(CreateEmailDocument(
            /*id=*/i, /*document_score=*/distribution(random_generator),
            /*creation_timestamp_ms=*/1)));
    doc_hit_infos.emplace_back(document_id);
  }

  ScoredDocumentHitComparator scored_document_hit_comparator(
      /*is_descending=*/true);

  for (auto _ : state) {
    // Creates a dummy DocHitInfoIterator with results, we need to pause the
    // timer here so that the cost of copying test data is not included.
    state.PauseTiming();
    std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
        std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);
    state.ResumeTiming();

    std::vector<ScoredDocumentHit> scored_document_hits =
        scoring_processor->Score(std::move(doc_hit_info_iterator),
                                 num_to_score);

    BuildHeapInPlace(&scored_document_hits, scored_document_hit_comparator);
    // Ranks and gets the first page, 20 is a common page size
    std::vector<ScoredDocumentHit> results =
        PopTopResultsFromHeap(&scored_document_hits, /*num_results=*/20,
                              scored_document_hit_comparator);
  }
  // Clean up
  document_store.reset();
  schema_store.reset();
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
}
BENCHMARK(BM_ScoreAndRankDocumentHitsByDocumentScore)
    // num_to_score, num_of_documents in document store
    ->ArgPair(1000, 30000)
    ->ArgPair(3000, 30000)
    ->ArgPair(5000, 30000)
    ->ArgPair(7000, 30000)
    ->ArgPair(9000, 30000)
    ->ArgPair(11000, 30000)
    ->ArgPair(13000, 30000)
    ->ArgPair(15000, 30000)
    ->ArgPair(17000, 30000)
    ->ArgPair(19000, 30000)
    ->ArgPair(21000, 30000)
    ->ArgPair(23000, 30000)
    ->ArgPair(25000, 30000)
    ->ArgPair(27000, 30000)
    ->ArgPair(29000, 30000)
    // Starting from this line, we're trying to see if num_of_documents affects
    // performance
    ->ArgPair(10000, 10000)
    ->ArgPair(10000, 12000)
    ->ArgPair(10000, 14000)
    ->ArgPair(10000, 16000)
    ->ArgPair(10000, 18000)
    ->ArgPair(10000, 20000);

void BM_ScoreAndRankDocumentHitsByCreationTime(benchmark::State& state) {
  const std::string base_dir = GetTestTempDir() + "/score_and_rank_benchmark";
  const std::string document_store_dir = base_dir + "/document_store";
  const std::string schema_store_dir = base_dir + "/schema_store";

  // Creates file directories
  Filesystem filesystem;
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
  ASSERT_TRUE(
      filesystem.CreateDirectoryRecursively(document_store_dir.c_str()));
  ASSERT_TRUE(filesystem.CreateDirectoryRecursively(schema_store_dir.c_str()));

  Clock clock;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem, schema_store_dir, &clock));

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentStore::CreateResult create_result,
      CreateDocumentStore(&filesystem, document_store_dir, &clock,
                          schema_store.get()));
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  ICING_ASSERT_OK(schema_store->SetSchema(
      CreateSchemaWithEmailType(), /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(scoring_spec, document_store.get(),
                               schema_store.get(),
                               clock.GetSystemTimeMilliseconds()));

  int num_to_score = state.range(0);
  int num_of_documents = state.range(1);

  std::mt19937_64 random_generator;
  std::uniform_int_distribution<int64_t> distribution(
      1, std::numeric_limits<int64_t>::max());

  // Puts documents into document store
  std::vector<DocHitInfo> doc_hit_infos;
  for (int i = 0; i < num_of_documents; i++) {
    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentId document_id,
        document_store->Put(CreateEmailDocument(
            /*id=*/i, /*document_score=*/1,
            /*creation_timestamp_ms=*/distribution(random_generator))));
    doc_hit_infos.emplace_back(document_id);
  }

  ScoredDocumentHitComparator scored_document_hit_comparator(
      /*is_descending=*/true);

  for (auto _ : state) {
    // Creates a dummy DocHitInfoIterator with results, we need to pause the
    // timer here so that the cost of copying test data is not included.
    state.PauseTiming();
    std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
        std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);
    state.ResumeTiming();

    std::vector<ScoredDocumentHit> scored_document_hits =
        scoring_processor->Score(std::move(doc_hit_info_iterator),
                                 num_to_score);

    BuildHeapInPlace(&scored_document_hits, scored_document_hit_comparator);
    // Ranks and gets the first page, 20 is a common page size
    std::vector<ScoredDocumentHit> results =
        PopTopResultsFromHeap(&scored_document_hits, /*num_results=*/20,
                              scored_document_hit_comparator);
  }

  // Clean up
  document_store.reset();
  schema_store.reset();
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
}
BENCHMARK(BM_ScoreAndRankDocumentHitsByCreationTime)
    // num_to_score, num_of_documents in document store
    ->ArgPair(1000, 30000)
    ->ArgPair(3000, 30000)
    ->ArgPair(5000, 30000)
    ->ArgPair(7000, 30000)
    ->ArgPair(9000, 30000)
    ->ArgPair(11000, 30000)
    ->ArgPair(13000, 30000)
    ->ArgPair(15000, 30000)
    ->ArgPair(17000, 30000)
    ->ArgPair(19000, 30000)
    ->ArgPair(21000, 30000)
    ->ArgPair(23000, 30000)
    ->ArgPair(25000, 30000)
    ->ArgPair(27000, 30000)
    ->ArgPair(29000, 30000)
    // Starting from this line, we're trying to see if num_of_documents affects
    // performance
    ->ArgPair(10000, 10000)
    ->ArgPair(10000, 12000)
    ->ArgPair(10000, 14000)
    ->ArgPair(10000, 16000)
    ->ArgPair(10000, 18000)
    ->ArgPair(10000, 20000);

void BM_ScoreAndRankDocumentHitsNoScoring(benchmark::State& state) {
  const std::string base_dir = GetTestTempDir() + "/score_and_rank_benchmark";
  const std::string document_store_dir = base_dir + "/document_store";
  const std::string schema_store_dir = base_dir + "/schema_store";

  // Creates file directories
  Filesystem filesystem;
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
  ASSERT_TRUE(
      filesystem.CreateDirectoryRecursively(document_store_dir.c_str()));
  ASSERT_TRUE(filesystem.CreateDirectoryRecursively(schema_store_dir.c_str()));

  Clock clock;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem, schema_store_dir, &clock));

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentStore::CreateResult create_result,
      CreateDocumentStore(&filesystem, document_store_dir, &clock,
                          schema_store.get()));
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  ICING_ASSERT_OK(schema_store->SetSchema(
      CreateSchemaWithEmailType(), /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::NONE);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(scoring_spec, document_store.get(),
                               schema_store.get(),
                               clock.GetSystemTimeMilliseconds()));

  int num_to_score = state.range(0);
  int num_of_documents = state.range(1);

  // Puts documents into document store
  std::vector<DocHitInfo> doc_hit_infos;
  for (int i = 0; i < num_of_documents; i++) {
    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentId document_id,
        document_store->Put(CreateEmailDocument(/*id=*/i, /*document_score=*/1,
                                                /*creation_timestamp_ms=*/1)));
    doc_hit_infos.emplace_back(document_id);
  }

  ScoredDocumentHitComparator scored_document_hit_comparator(
      /*is_descending=*/true);

  for (auto _ : state) {
    // Creates a dummy DocHitInfoIterator with results, we need to pause the
    // timer here so that the cost of copying test data is not included.
    state.PauseTiming();
    std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
        std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);
    state.ResumeTiming();

    std::vector<ScoredDocumentHit> scored_document_hits =
        scoring_processor->Score(std::move(doc_hit_info_iterator),
                                 num_to_score);

    BuildHeapInPlace(&scored_document_hits, scored_document_hit_comparator);
    // Ranks and gets the first page, 20 is a common page size
    std::vector<ScoredDocumentHit> results =
        PopTopResultsFromHeap(&scored_document_hits, /*num_results=*/20,
                              scored_document_hit_comparator);
  }

  // Clean up
  document_store.reset();
  schema_store.reset();
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
}
BENCHMARK(BM_ScoreAndRankDocumentHitsNoScoring)
    // num_to_score, num_of_documents in document store
    ->ArgPair(1000, 30000)
    ->ArgPair(3000, 30000)
    ->ArgPair(5000, 30000)
    ->ArgPair(7000, 30000)
    ->ArgPair(9000, 30000)
    ->ArgPair(11000, 30000)
    ->ArgPair(13000, 30000)
    ->ArgPair(15000, 30000)
    ->ArgPair(17000, 30000)
    ->ArgPair(19000, 30000)
    ->ArgPair(21000, 30000)
    ->ArgPair(23000, 30000)
    ->ArgPair(25000, 30000)
    ->ArgPair(27000, 30000)
    ->ArgPair(29000, 30000)
    // Starting from this line, we're trying to see if num_of_documents affects
    // performance
    ->ArgPair(10000, 10000)
    ->ArgPair(10000, 12000)
    ->ArgPair(10000, 14000)
    ->ArgPair(10000, 16000)
    ->ArgPair(10000, 18000)
    ->ArgPair(10000, 20000);

void BM_ScoreAndRankDocumentHitsByRelevanceScoring(benchmark::State& state) {
  const std::string base_dir = GetTestTempDir() + "/score_and_rank_benchmark";
  const std::string document_store_dir = base_dir + "/document_store";
  const std::string schema_store_dir = base_dir + "/schema_store";

  // Creates file directories
  Filesystem filesystem;
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
  ASSERT_TRUE(
      filesystem.CreateDirectoryRecursively(document_store_dir.c_str()));
  ASSERT_TRUE(filesystem.CreateDirectoryRecursively(schema_store_dir.c_str()));

  Clock clock;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem, schema_store_dir, &clock));

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentStore::CreateResult create_result,
      CreateDocumentStore(&filesystem, document_store_dir, &clock,
                          schema_store.get()));
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  ICING_ASSERT_OK(schema_store->SetSchema(
      CreateSchemaWithEmailType(), /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(scoring_spec, document_store.get(),
                               schema_store.get(),
                               clock.GetSystemTimeMilliseconds()));

  int num_to_score = state.range(0);
  int num_of_documents = state.range(1);

  std::mt19937 random_generator;
  std::uniform_int_distribution<int> distribution(
      1, std::numeric_limits<int>::max());

  SectionId section_id = 0;
  SectionIdMask section_id_mask = 1U << section_id;

  // Puts documents into document store
  std::vector<DocHitInfoTermFrequencyPair> doc_hit_infos;
  for (int i = 0; i < num_of_documents; i++) {
    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentId document_id,
        document_store->Put(CreateEmailDocument(
                                /*id=*/i, /*document_score=*/1,
                                /*creation_timestamp_ms=*/1),
                            /*num_tokens=*/10));
    DocHitInfoTermFrequencyPair doc_hit =
        DocHitInfo(document_id, section_id_mask);
    // Set five matches for term "foo" for each document hit.
    doc_hit.UpdateSection(section_id, /*hit_term_frequency=*/5);
    doc_hit_infos.push_back(doc_hit);
  }

  ScoredDocumentHitComparator scored_document_hit_comparator(
      /*is_descending=*/true);

  for (auto _ : state) {
    // Creates a dummy DocHitInfoIterator with results, we need to pause the
    // timer here so that the cost of copying test data is not included.
    state.PauseTiming();
    std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
        std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");
    // Create a query term iterator that assigns the document hits to term
    // "foo".
    std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>
        query_term_iterators;
    query_term_iterators["foo"] =
        std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");
    state.ResumeTiming();

    std::vector<ScoredDocumentHit> scored_document_hits =
        scoring_processor->Score(std::move(doc_hit_info_iterator), num_to_score,
                                 &query_term_iterators);

    BuildHeapInPlace(&scored_document_hits, scored_document_hit_comparator);
    // Ranks and gets the first page, 20 is a common page size
    std::vector<ScoredDocumentHit> results =
        PopTopResultsFromHeap(&scored_document_hits, /*num_results=*/20,
                              scored_document_hit_comparator);
  }

  // Clean up
  document_store.reset();
  schema_store.reset();
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
}
BENCHMARK(BM_ScoreAndRankDocumentHitsByRelevanceScoring)
    // num_to_score, num_of_documents in document store
    ->ArgPair(1000, 30000)
    ->ArgPair(3000, 30000)
    ->ArgPair(5000, 30000)
    ->ArgPair(7000, 30000)
    ->ArgPair(9000, 30000)
    ->ArgPair(11000, 30000)
    ->ArgPair(13000, 30000)
    ->ArgPair(15000, 30000)
    ->ArgPair(17000, 30000)
    ->ArgPair(19000, 30000)
    ->ArgPair(21000, 30000)
    ->ArgPair(23000, 30000)
    ->ArgPair(25000, 30000)
    ->ArgPair(27000, 30000)
    ->ArgPair(29000, 30000)
    // Starting from this line, we're trying to see if num_of_documents affects
    // performance
    ->ArgPair(10000, 10000)
    ->ArgPair(10000, 12000)
    ->ArgPair(10000, 14000)
    ->ArgPair(10000, 16000)
    ->ArgPair(10000, 18000)
    ->ArgPair(10000, 20000);

}  // namespace

}  // namespace lib
}  // namespace icing
