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

#include "testing/base/public/benchmark.h"
#include "gmock/gmock.h"
#include "third_party/absl/flags/flag.h"
#include "icing/document-builder.h"
#include "icing/index/index.h"
#include "icing/index/numeric/dummy-numeric-index.h"
#include "icing/index/numeric/numeric-index.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/query/query-processor.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/transform/normalizer-factory.h"
#include "icing/util/clock.h"
#include "icing/util/logging.h"
#include "unicode/uloc.h"

// Run on a Linux workstation:
//    $ blaze build -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/query:query-processor_benchmark
//
//    $ blaze-bin/icing/query/query-processor_benchmark
//    --benchmark_filter=all
//
// Run on an Android device:
//    Make target //icing/tokenization:language-segmenter depend on
//    //third_party/icu
//
//    Make target //icing/transform:normalizer depend on
//    //third_party/icu
//
//    $ blaze build --copt="-DGOOGLE_COMMANDLINEFLAGS_FULL_API=1"
//    --config=android_arm64 -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/query:query-processor_benchmark
//
//    $ adb push blaze-bin/icing/query/query-processor_benchmark
//    /data/local/tmp/
//
//    $ adb shell /data/local/tmp/query-processor_benchmark
//    --benchmark_filter=all --adb

// Flag to tell the benchmark that it'll be run on an Android device via adb,
// the benchmark will set up data files accordingly.
ABSL_FLAG(bool, adb, false, "run benchmark via ADB on an Android device");

namespace icing {
namespace lib {

namespace {

void AddTokenToIndex(Index* index, DocumentId document_id, SectionId section_id,
                     TermMatchType::Code term_match_type,
                     const std::string& token) {
  Index::Editor editor =
      index->Edit(document_id, section_id, term_match_type, /*namespace_id=*/0);
  ICING_ASSERT_OK(editor.BufferTerm(token.c_str()));
  ICING_ASSERT_OK(editor.IndexAllBufferedTerms());
}

std::unique_ptr<Index> CreateIndex(const IcingFilesystem& icing_filesystem,
                                   const Filesystem& filesystem,
                                   const std::string& index_dir) {
  Index::Options options(index_dir, /*index_merge_size=*/1024 * 1024 * 10,
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/1024 * 8);
  return Index::Create(options, &filesystem, &icing_filesystem).ValueOrDie();
}

std::unique_ptr<Normalizer> CreateNormalizer() {
  return normalizer_factory::Create(

             /*max_term_byte_size=*/std::numeric_limits<int>::max())
      .ValueOrDie();
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

void BM_QueryOneTerm(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  IcingFilesystem icing_filesystem;
  Filesystem filesystem;
  const std::string base_dir = GetTestTempDir() + "/query_processor_benchmark";
  const std::string index_dir = base_dir + "/index";
  const std::string numeric_index_dir = base_dir + "/numeric_index";
  const std::string schema_dir = base_dir + "/schema";
  const std::string doc_store_dir = base_dir + "/store";

  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
  if (!filesystem.CreateDirectoryRecursively(index_dir.c_str()) ||
      !filesystem.CreateDirectoryRecursively(schema_dir.c_str()) ||
      !filesystem.CreateDirectoryRecursively(doc_store_dir.c_str())) {
    ICING_LOG(ERROR) << "Failed to create test directories";
  }

  std::unique_ptr<Index> index =
      CreateIndex(icing_filesystem, filesystem, index_dir);
  // TODO(b/249829533): switch to use persistent numeric index.
  ICING_ASSERT_OK_AND_ASSIGN(
      auto numeric_index,
      DummyNumericIndex<int64_t>::Create(filesystem, numeric_index_dir));

  language_segmenter_factory::SegmenterOptions options(ULOC_US);
  std::unique_ptr<LanguageSegmenter> language_segmenter =
      language_segmenter_factory::Create(std::move(options)).ValueOrDie();
  std::unique_ptr<Normalizer> normalizer = CreateNormalizer();

  SchemaProto schema;
  auto type_config = schema.add_types();
  type_config->set_schema_type("type1");
  Clock clock;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem, schema_dir, &clock));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  DocumentStore::CreateResult create_result =
      CreateDocumentStore(&filesystem, doc_store_dir, &clock,
                          schema_store.get())
          .ValueOrDie();
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  DocumentId document_id = document_store
                               ->Put(DocumentBuilder()
                                         .SetKey("icing", "type1")
                                         .SetSchema("type1")
                                         .Build())
                               .ValueOrDie();

  const std::string input_string(state.range(0), 'A');
  AddTokenToIndex(index.get(), document_id, /*section_id=*/0,
                  TermMatchType::EXACT_ONLY, input_string);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QueryProcessor> query_processor,
      QueryProcessor::Create(index.get(), numeric_index.get(),
                             language_segmenter.get(), normalizer.get(),
                             document_store.get(), schema_store.get(), &clock));

  SearchSpecProto search_spec;
  search_spec.set_query(input_string);
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);

  for (auto _ : state) {
    QueryResults results =
        query_processor
            ->ParseSearch(search_spec,
                          ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE,
                          clock.GetSystemTimeMilliseconds())
            .ValueOrDie();
    while (results.root_iterator->Advance().ok()) {
      results.root_iterator->doc_hit_info();
    }
  }

  // Destroy document store and schema store before the whole directory is
  // removed because they persist data in destructor.
  document_store.reset();
  schema_store.reset();
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
}
BENCHMARK(BM_QueryOneTerm)
    // The testing numbers are in an ascending order with a fixed interval, that
    // way we can tell if the performance increments are linear, exponential, or
    // something else.
    ->Arg(1000)
    ->Arg(3000)
    ->Arg(5000)
    ->Arg(7000)
    ->Arg(9000)
    ->Arg(11000)
    ->Arg(13000)
    ->Arg(15000)
    ->Arg(17000)
    ->Arg(19000)
    ->Arg(21000)
    ->Arg(23000)
    ->Arg(25000)
    ->Arg(27000)
    ->Arg(29000)
    ->Arg(31000)
    ->Arg(33000)
    ->Arg(35000)
    ->Arg(37000)
    ->Arg(39000)
    ->Arg(41000)
    ->Arg(43000)
    ->Arg(45000)
    ->Arg(47000)
    ->Arg(49000)
    ->Arg(64000)
    ->Arg(128000)
    ->Arg(256000)
    ->Arg(384000)
    ->Arg(512000)
    ->Arg(1024000)
    ->Arg(2048000)
    ->Arg(4096000);

void BM_QueryFiveTerms(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  IcingFilesystem icing_filesystem;
  Filesystem filesystem;
  const std::string base_dir = GetTestTempDir() + "/query_processor_benchmark";
  const std::string index_dir = base_dir + "/index";
  const std::string numeric_index_dir = base_dir + "/numeric_index";
  const std::string schema_dir = base_dir + "/schema";
  const std::string doc_store_dir = base_dir + "/store";

  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
  if (!filesystem.CreateDirectoryRecursively(index_dir.c_str()) ||
      !filesystem.CreateDirectoryRecursively(schema_dir.c_str()) ||
      !filesystem.CreateDirectoryRecursively(doc_store_dir.c_str())) {
    ICING_LOG(ERROR) << "Failed to create test directories";
  }

  std::unique_ptr<Index> index =
      CreateIndex(icing_filesystem, filesystem, index_dir);
  // TODO(b/249829533): switch to use persistent numeric index.
  ICING_ASSERT_OK_AND_ASSIGN(
      auto numeric_index,
      DummyNumericIndex<int64_t>::Create(filesystem, numeric_index_dir));

  language_segmenter_factory::SegmenterOptions options(ULOC_US);
  std::unique_ptr<LanguageSegmenter> language_segmenter =
      language_segmenter_factory::Create(std::move(options)).ValueOrDie();
  std::unique_ptr<Normalizer> normalizer = CreateNormalizer();

  SchemaProto schema;
  auto type_config = schema.add_types();
  type_config->set_schema_type("type1");
  Clock clock;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem, schema_dir, &clock));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  DocumentStore::CreateResult create_result =
      CreateDocumentStore(&filesystem, doc_store_dir, &clock,
                          schema_store.get())
          .ValueOrDie();
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  DocumentId document_id = document_store
                               ->Put(DocumentBuilder()
                                         .SetKey("icing", "type1")
                                         .SetSchema("type1")
                                         .Build())
                               .ValueOrDie();

  int term_length = state.range(0) / 5;

  const std::string input_string_a(term_length, 'A');
  const std::string input_string_b(term_length, 'B');
  const std::string input_string_c(term_length, 'C');
  const std::string input_string_d(term_length, 'D');
  const std::string input_string_e(term_length, 'E');
  AddTokenToIndex(index.get(), document_id, /*section_id=*/0,
                  TermMatchType::EXACT_ONLY, input_string_a);
  AddTokenToIndex(index.get(), document_id, /*section_id=*/1,
                  TermMatchType::EXACT_ONLY, input_string_b);
  AddTokenToIndex(index.get(), document_id, /*section_id=*/2,
                  TermMatchType::EXACT_ONLY, input_string_c);
  AddTokenToIndex(index.get(), document_id, /*section_id=*/3,
                  TermMatchType::EXACT_ONLY, input_string_d);
  AddTokenToIndex(index.get(), document_id, /*section_id=*/4,
                  TermMatchType::EXACT_ONLY, input_string_e);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QueryProcessor> query_processor,
      QueryProcessor::Create(index.get(), numeric_index.get(),
                             language_segmenter.get(), normalizer.get(),
                             document_store.get(), schema_store.get(), &clock));

  const std::string query_string = absl_ports::StrCat(
      input_string_a, " ", input_string_b, " ", input_string_c, " ",
      input_string_d, " ", input_string_e);

  SearchSpecProto search_spec;
  search_spec.set_query(query_string);
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);

  for (auto _ : state) {
    QueryResults results =
        query_processor
            ->ParseSearch(search_spec,
                          ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE,
                          clock.GetSystemTimeMilliseconds())
            .ValueOrDie();
    while (results.root_iterator->Advance().ok()) {
      results.root_iterator->doc_hit_info();
    }
  }

  // Destroy document store and schema store before the whole directory is
  // removed because they persist data in destructor.
  document_store.reset();
  schema_store.reset();
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
}
BENCHMARK(BM_QueryFiveTerms)
    // The testing numbers are in an ascending order with a fixed interval, that
    // way we can tell if the performance increments are linear, exponential, or
    // something else.
    ->Arg(1000)
    ->Arg(3000)
    ->Arg(5000)
    ->Arg(7000)
    ->Arg(9000)
    ->Arg(11000)
    ->Arg(13000)
    ->Arg(15000)
    ->Arg(17000)
    ->Arg(19000)
    ->Arg(21000)
    ->Arg(23000)
    ->Arg(25000)
    ->Arg(27000)
    ->Arg(29000)
    ->Arg(31000)
    ->Arg(33000)
    ->Arg(35000)
    ->Arg(37000)
    ->Arg(39000)
    ->Arg(41000)
    ->Arg(43000)
    ->Arg(45000)
    ->Arg(47000)
    ->Arg(49000)
    ->Arg(64000)
    ->Arg(128000)
    ->Arg(256000)
    ->Arg(384000)
    ->Arg(512000)
    ->Arg(1024000)
    ->Arg(2048000)
    ->Arg(4096000);

void BM_QueryDiacriticTerm(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  IcingFilesystem icing_filesystem;
  Filesystem filesystem;
  const std::string base_dir = GetTestTempDir() + "/query_processor_benchmark";
  const std::string index_dir = base_dir + "/index";
  const std::string numeric_index_dir = base_dir + "/numeric_index";
  const std::string schema_dir = base_dir + "/schema";
  const std::string doc_store_dir = base_dir + "/store";

  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
  if (!filesystem.CreateDirectoryRecursively(index_dir.c_str()) ||
      !filesystem.CreateDirectoryRecursively(schema_dir.c_str()) ||
      !filesystem.CreateDirectoryRecursively(doc_store_dir.c_str())) {
    ICING_LOG(ERROR) << "Failed to create test directories";
  }

  std::unique_ptr<Index> index =
      CreateIndex(icing_filesystem, filesystem, index_dir);
  // TODO(b/249829533): switch to use persistent numeric index.
  ICING_ASSERT_OK_AND_ASSIGN(
      auto numeric_index,
      DummyNumericIndex<int64_t>::Create(filesystem, numeric_index_dir));

  language_segmenter_factory::SegmenterOptions options(ULOC_US);
  std::unique_ptr<LanguageSegmenter> language_segmenter =
      language_segmenter_factory::Create(std::move(options)).ValueOrDie();
  std::unique_ptr<Normalizer> normalizer = CreateNormalizer();

  SchemaProto schema;
  auto type_config = schema.add_types();
  type_config->set_schema_type("type1");
  Clock clock;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem, schema_dir, &clock));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  DocumentStore::CreateResult create_result =
      CreateDocumentStore(&filesystem, doc_store_dir, &clock,
                          schema_store.get())
          .ValueOrDie();
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  DocumentId document_id = document_store
                               ->Put(DocumentBuilder()
                                         .SetKey("icing", "type1")
                                         .SetSchema("type1")
                                         .Build())
                               .ValueOrDie();

  std::string input_string;
  while (input_string.length() < state.range(0)) {
    input_string.append("àáâãā");
  }
  AddTokenToIndex(index.get(), document_id, /*section_id=*/0,
                  TermMatchType::EXACT_ONLY, input_string);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QueryProcessor> query_processor,
      QueryProcessor::Create(index.get(), numeric_index.get(),
                             language_segmenter.get(), normalizer.get(),
                             document_store.get(), schema_store.get(), &clock));

  SearchSpecProto search_spec;
  search_spec.set_query(input_string);
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);

  for (auto _ : state) {
    QueryResults results =
        query_processor
            ->ParseSearch(search_spec,
                          ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE,
                          clock.GetSystemTimeMilliseconds())
            .ValueOrDie();
    while (results.root_iterator->Advance().ok()) {
      results.root_iterator->doc_hit_info();
    }
  }

  // Destroy document store and schema store before the whole directory is
  // removed because they persist data in destructor.
  document_store.reset();
  schema_store.reset();
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
}
BENCHMARK(BM_QueryDiacriticTerm)
    // The testing numbers are in an ascending order with a fixed interval, that
    // way we can tell if the performance increments are linear, exponential, or
    // something else.
    ->Arg(1000)
    ->Arg(3000)
    ->Arg(5000)
    ->Arg(7000)
    ->Arg(9000)
    ->Arg(11000)
    ->Arg(13000)
    ->Arg(15000)
    ->Arg(17000)
    ->Arg(19000)
    ->Arg(21000)
    ->Arg(23000)
    ->Arg(25000)
    ->Arg(27000)
    ->Arg(29000)
    ->Arg(31000)
    ->Arg(33000)
    ->Arg(35000)
    ->Arg(37000)
    ->Arg(39000)
    ->Arg(41000)
    ->Arg(43000)
    ->Arg(45000)
    ->Arg(47000)
    ->Arg(49000)
    ->Arg(64000)
    ->Arg(128000)
    ->Arg(256000)
    ->Arg(384000)
    ->Arg(512000)
    ->Arg(1024000)
    ->Arg(2048000)
    ->Arg(4096000);

void BM_QueryHiragana(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  IcingFilesystem icing_filesystem;
  Filesystem filesystem;
  const std::string base_dir = GetTestTempDir() + "/query_processor_benchmark";
  const std::string index_dir = base_dir + "/index";
  const std::string numeric_index_dir = base_dir + "/numeric_index";
  const std::string schema_dir = base_dir + "/schema";
  const std::string doc_store_dir = base_dir + "/store";

  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
  if (!filesystem.CreateDirectoryRecursively(index_dir.c_str()) ||
      !filesystem.CreateDirectoryRecursively(schema_dir.c_str()) ||
      !filesystem.CreateDirectoryRecursively(doc_store_dir.c_str())) {
    ICING_LOG(ERROR) << "Failed to create test directories";
  }

  std::unique_ptr<Index> index =
      CreateIndex(icing_filesystem, filesystem, index_dir);
  // TODO(b/249829533): switch to use persistent numeric index.
  ICING_ASSERT_OK_AND_ASSIGN(
      auto numeric_index,
      DummyNumericIndex<int64_t>::Create(filesystem, numeric_index_dir));

  language_segmenter_factory::SegmenterOptions options(ULOC_US);
  std::unique_ptr<LanguageSegmenter> language_segmenter =
      language_segmenter_factory::Create(std::move(options)).ValueOrDie();
  std::unique_ptr<Normalizer> normalizer = CreateNormalizer();

  SchemaProto schema;
  auto type_config = schema.add_types();
  type_config->set_schema_type("type1");
  Clock clock;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem, schema_dir, &clock));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  DocumentStore::CreateResult create_result =
      CreateDocumentStore(&filesystem, doc_store_dir, &clock,
                          schema_store.get())
          .ValueOrDie();
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  DocumentId document_id = document_store
                               ->Put(DocumentBuilder()
                                         .SetKey("icing", "type1")
                                         .SetSchema("type1")
                                         .Build())
                               .ValueOrDie();

  std::string input_string;
  while (input_string.length() < state.range(0)) {
    input_string.append("あいうえお");
  }
  AddTokenToIndex(index.get(), document_id, /*section_id=*/0,
                  TermMatchType::EXACT_ONLY, input_string);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QueryProcessor> query_processor,
      QueryProcessor::Create(index.get(), numeric_index.get(),
                             language_segmenter.get(), normalizer.get(),
                             document_store.get(), schema_store.get(), &clock));

  SearchSpecProto search_spec;
  search_spec.set_query(input_string);
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);

  for (auto _ : state) {
    QueryResults results =
        query_processor
            ->ParseSearch(search_spec,
                          ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE,
                          clock.GetSystemTimeMilliseconds())
            .ValueOrDie();
    while (results.root_iterator->Advance().ok()) {
      results.root_iterator->doc_hit_info();
    }
  }

  // Destroy document store and schema store before the whole directory is
  // removed because they persist data in destructor.
  document_store.reset();
  schema_store.reset();
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
}
BENCHMARK(BM_QueryHiragana)
    // The testing numbers are in an ascending order with a fixed interval, that
    // way we can tell if the performance increments are linear, exponential, or
    // something else.
    ->Arg(1000)
    ->Arg(3000)
    ->Arg(5000)
    ->Arg(7000)
    ->Arg(9000)
    ->Arg(11000)
    ->Arg(13000)
    ->Arg(15000)
    ->Arg(17000)
    ->Arg(19000)
    ->Arg(21000)
    ->Arg(23000)
    ->Arg(25000)
    ->Arg(27000)
    ->Arg(29000)
    ->Arg(31000)
    ->Arg(33000)
    ->Arg(35000)
    ->Arg(37000)
    ->Arg(39000)
    ->Arg(41000)
    ->Arg(43000)
    ->Arg(45000)
    ->Arg(47000)
    ->Arg(49000)
    ->Arg(64000)
    ->Arg(128000)
    ->Arg(256000)
    ->Arg(384000)
    ->Arg(512000)
    ->Arg(1024000)
    ->Arg(2048000)
    ->Arg(4096000);
}  // namespace

}  // namespace lib
}  // namespace icing
