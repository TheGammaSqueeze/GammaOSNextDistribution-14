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

#include <cstdint>
#include <limits>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "testing/base/public/benchmark.h"
#include "gmock/gmock.h"
#include "third_party/absl/flags/flag.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/index/data-indexing-handler.h"
#include "icing/index/index-processor.h"
#include "icing/index/index.h"
#include "icing/index/integer-section-indexing-handler.h"
#include "icing/index/numeric/integer-index.h"
#include "icing/index/numeric/numeric-index.h"
#include "icing/index/term-indexing-handler.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/schema/schema-store.h"
#include "icing/store/document-id.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/transform/normalizer-factory.h"
#include "icing/transform/normalizer.h"
#include "icing/util/clock.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"
#include "icing/util/tokenized-document.h"
#include "unicode/uloc.h"

// Run on a Linux workstation:
//    $ blaze build -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/index:index-processor_benchmark
//
//    $ blaze-bin/icing/index/index-processor_benchmark
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
//    //icing/index:index-processor_benchmark
//
//    $ adb push blaze-bin/icing/index/index-processor_benchmark
//    /data/local/tmp/
//
//    $ adb shell /data/local/tmp/index-processor_benchmark
//    --benchmark_filter=all
//    --adb

// Flag to tell the benchmark that it'll be run on an Android device via adb,
// the benchmark will set up data files accordingly.
ABSL_FLAG(bool, adb, false, "run benchmark via ADB on an Android device");

namespace icing {
namespace lib {

namespace {

using ::testing::IsTrue;

// Creates a fake type config with 10 properties (p0 - p9)
void CreateFakeTypeConfig(SchemaTypeConfigProto* type_config) {
  type_config->set_schema_type("Fake_Type");

  for (int i = 0; i < 10; i++) {
    auto property = type_config->add_properties();
    property->set_property_name(
        IcingStringUtil::StringPrintf("p%d", i));  //  p0 - p9
    property->set_data_type(PropertyConfigProto::DataType::STRING);
    property->set_cardinality(PropertyConfigProto::Cardinality::OPTIONAL);
    property->mutable_string_indexing_config()->set_term_match_type(
        TermMatchType::EXACT_ONLY);
    property->mutable_string_indexing_config()->set_tokenizer_type(
        StringIndexingConfig::TokenizerType::PLAIN);
  }
}

DocumentProto CreateDocumentWithOneProperty(int content_length) {
  return DocumentBuilder()
      .SetKey("icing", "fake/1")
      .SetSchema("Fake_Type")
      .AddStringProperty("p0", std::string(content_length, 'A'))
      .Build();
}

DocumentProto CreateDocumentWithTenProperties(int content_length) {
  int property_length = content_length / 10;
  return DocumentBuilder()
      .SetKey("icing", "fake/1")
      .SetSchema("Fake_Type")
      .AddStringProperty("p0", std::string(property_length, 'A'))
      .AddStringProperty("p1", std::string(property_length, 'B'))
      .AddStringProperty("p2", std::string(property_length, 'C'))
      .AddStringProperty("p3", std::string(property_length, 'D'))
      .AddStringProperty("p4", std::string(property_length, 'E'))
      .AddStringProperty("p5", std::string(property_length, 'F'))
      .AddStringProperty("p6", std::string(property_length, 'G'))
      .AddStringProperty("p7", std::string(property_length, 'H'))
      .AddStringProperty("p8", std::string(property_length, 'I'))
      .AddStringProperty("p9", std::string(property_length, 'J'))
      .Build();
}

DocumentProto CreateDocumentWithDiacriticLetters(int content_length) {
  std::string content;
  while (content.length() < content_length) {
    content.append("àáâãā");
  }
  return DocumentBuilder()
      .SetKey("icing", "fake/1")
      .SetSchema("Fake_Type")
      .AddStringProperty("p0", content)
      .Build();
}

DocumentProto CreateDocumentWithHiragana(int content_length) {
  std::string content;
  while (content.length() < content_length) {
    content.append("あいうえお");
  }
  return DocumentBuilder()
      .SetKey("icing", "fake/1")
      .SetSchema("Fake_Type")
      .AddStringProperty("p0", content)
      .Build();
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

std::unique_ptr<SchemaStore> CreateSchemaStore(const Filesystem& filesystem,
                                               const Clock* clock,
                                               const std::string& base_dir) {
  std::string schema_store_dir = base_dir + "/schema_store_test";
  filesystem.CreateDirectoryRecursively(schema_store_dir.c_str());

  std::unique_ptr<SchemaStore> schema_store =
      SchemaStore::Create(&filesystem, schema_store_dir, clock).ValueOrDie();

  SchemaProto schema;
  CreateFakeTypeConfig(schema.add_types());
  auto set_schema_status = schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false);

  if (!set_schema_status.ok()) {
    ICING_LOG(ERROR) << set_schema_status.status().error_message();
  }

  return schema_store;
}

libtextclassifier3::StatusOr<std::vector<std::unique_ptr<DataIndexingHandler>>>
CreateDataIndexingHandlers(const Clock* clock, const Normalizer* normalizer,
                           Index* index, NumericIndex<int64_t>* integer_index) {
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<TermIndexingHandler> term_indexing_handler,
      TermIndexingHandler::Create(
          clock, normalizer, index,
          /*build_property_existence_metadata_hits=*/true));
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<IntegerSectionIndexingHandler>
          integer_section_indexing_handler,
      IntegerSectionIndexingHandler::Create(clock, integer_index));

  std::vector<std::unique_ptr<DataIndexingHandler>> handlers;
  handlers.push_back(std::move(term_indexing_handler));
  handlers.push_back(std::move(integer_section_indexing_handler));
  return handlers;
}

void CleanUp(const Filesystem& filesystem, const std::string& base_dir) {
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
}

void BM_IndexDocumentWithOneProperty(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  IcingFilesystem icing_filesystem;
  Filesystem filesystem;
  std::string base_dir = GetTestTempDir() + "/index_processor_benchmark";
  std::string index_dir = base_dir + "/index_test/";
  std::string integer_index_dir = base_dir + "/integer_index_test/";

  CleanUp(filesystem, base_dir);
  ASSERT_THAT(filesystem.CreateDirectoryRecursively(base_dir.c_str()),
              IsTrue());

  std::unique_ptr<Index> index =
      CreateIndex(icing_filesystem, filesystem, index_dir);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      IntegerIndex::Create(filesystem, integer_index_dir,
                           IntegerIndex::kDefaultNumDataThresholdForBucketSplit,
                           /*pre_mapping_fbv=*/true));
  language_segmenter_factory::SegmenterOptions options(ULOC_US);
  std::unique_ptr<LanguageSegmenter> language_segmenter =
      language_segmenter_factory::Create(std::move(options)).ValueOrDie();
  std::unique_ptr<Normalizer> normalizer = CreateNormalizer();
  Clock clock;
  std::unique_ptr<SchemaStore> schema_store =
      CreateSchemaStore(filesystem, &clock, base_dir);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<std::unique_ptr<DataIndexingHandler>> handlers,
      CreateDataIndexingHandlers(&clock, normalizer.get(), index.get(),
                                 integer_index.get()));
  auto index_processor =
      std::make_unique<IndexProcessor>(std::move(handlers), &clock);

  DocumentProto input_document = CreateDocumentWithOneProperty(state.range(0));
  TokenizedDocument tokenized_document(std::move(
      TokenizedDocument::Create(schema_store.get(), language_segmenter.get(),
                                input_document)
          .ValueOrDie()));

  DocumentId document_id = 0;
  for (auto _ : state) {
    ICING_ASSERT_OK(
        index_processor->IndexDocument(tokenized_document, document_id++));
  }

  index_processor.reset();
  schema_store.reset();
  normalizer.reset();
  language_segmenter.reset();
  integer_index.reset();
  index.reset();

  CleanUp(filesystem, base_dir);
}
BENCHMARK(BM_IndexDocumentWithOneProperty)
    ->Arg(1000)
    ->Arg(2000)
    ->Arg(4000)
    ->Arg(8000)
    ->Arg(16000)
    ->Arg(32000)
    ->Arg(64000)
    ->Arg(128000)
    ->Arg(256000)
    ->Arg(384000)
    ->Arg(512000)
    ->Arg(1024000)
    ->Arg(2048000)
    ->Arg(4096000);

void BM_IndexDocumentWithTenProperties(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  IcingFilesystem icing_filesystem;
  Filesystem filesystem;
  std::string base_dir = GetTestTempDir() + "/index_processor_benchmark";
  std::string index_dir = base_dir + "/index_test/";
  std::string integer_index_dir = base_dir + "/integer_index_test/";

  CleanUp(filesystem, base_dir);
  ASSERT_THAT(filesystem.CreateDirectoryRecursively(base_dir.c_str()),
              IsTrue());

  std::unique_ptr<Index> index =
      CreateIndex(icing_filesystem, filesystem, index_dir);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      IntegerIndex::Create(filesystem, integer_index_dir,
                           IntegerIndex::kDefaultNumDataThresholdForBucketSplit,
                           /*pre_mapping_fbv=*/true));
  language_segmenter_factory::SegmenterOptions options(ULOC_US);
  std::unique_ptr<LanguageSegmenter> language_segmenter =
      language_segmenter_factory::Create(std::move(options)).ValueOrDie();
  std::unique_ptr<Normalizer> normalizer = CreateNormalizer();
  Clock clock;
  std::unique_ptr<SchemaStore> schema_store =
      CreateSchemaStore(filesystem, &clock, base_dir);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<std::unique_ptr<DataIndexingHandler>> handlers,
      CreateDataIndexingHandlers(&clock, normalizer.get(), index.get(),
                                 integer_index.get()));
  auto index_processor =
      std::make_unique<IndexProcessor>(std::move(handlers), &clock);

  DocumentProto input_document =
      CreateDocumentWithTenProperties(state.range(0));
  TokenizedDocument tokenized_document(std::move(
      TokenizedDocument::Create(schema_store.get(), language_segmenter.get(),
                                input_document)
          .ValueOrDie()));

  DocumentId document_id = 0;
  for (auto _ : state) {
    ICING_ASSERT_OK(
        index_processor->IndexDocument(tokenized_document, document_id++));
  }

  index_processor.reset();
  schema_store.reset();
  normalizer.reset();
  language_segmenter.reset();
  integer_index.reset();
  index.reset();

  CleanUp(filesystem, base_dir);
}
BENCHMARK(BM_IndexDocumentWithTenProperties)
    ->Arg(1000)
    ->Arg(2000)
    ->Arg(4000)
    ->Arg(8000)
    ->Arg(16000)
    ->Arg(32000)
    ->Arg(64000)
    ->Arg(128000)
    ->Arg(256000)
    ->Arg(384000)
    ->Arg(512000)
    ->Arg(1024000)
    ->Arg(2048000)
    ->Arg(4096000);

void BM_IndexDocumentWithDiacriticLetters(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  IcingFilesystem icing_filesystem;
  Filesystem filesystem;
  std::string base_dir = GetTestTempDir() + "/index_processor_benchmark";
  std::string index_dir = base_dir + "/index_test/";
  std::string integer_index_dir = base_dir + "/integer_index_test/";

  CleanUp(filesystem, base_dir);
  ASSERT_THAT(filesystem.CreateDirectoryRecursively(base_dir.c_str()),
              IsTrue());

  std::unique_ptr<Index> index =
      CreateIndex(icing_filesystem, filesystem, index_dir);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      IntegerIndex::Create(filesystem, integer_index_dir,
                           IntegerIndex::kDefaultNumDataThresholdForBucketSplit,
                           /*pre_mapping_fbv=*/true));
  language_segmenter_factory::SegmenterOptions options(ULOC_US);
  std::unique_ptr<LanguageSegmenter> language_segmenter =
      language_segmenter_factory::Create(std::move(options)).ValueOrDie();
  std::unique_ptr<Normalizer> normalizer = CreateNormalizer();
  Clock clock;
  std::unique_ptr<SchemaStore> schema_store =
      CreateSchemaStore(filesystem, &clock, base_dir);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<std::unique_ptr<DataIndexingHandler>> handlers,
      CreateDataIndexingHandlers(&clock, normalizer.get(), index.get(),
                                 integer_index.get()));
  auto index_processor =
      std::make_unique<IndexProcessor>(std::move(handlers), &clock);

  DocumentProto input_document =
      CreateDocumentWithDiacriticLetters(state.range(0));
  TokenizedDocument tokenized_document(std::move(
      TokenizedDocument::Create(schema_store.get(), language_segmenter.get(),
                                input_document)
          .ValueOrDie()));

  DocumentId document_id = 0;
  for (auto _ : state) {
    ICING_ASSERT_OK(
        index_processor->IndexDocument(tokenized_document, document_id++));
  }

  index_processor.reset();
  schema_store.reset();
  normalizer.reset();
  language_segmenter.reset();
  integer_index.reset();
  index.reset();

  CleanUp(filesystem, base_dir);
}
BENCHMARK(BM_IndexDocumentWithDiacriticLetters)
    ->Arg(1000)
    ->Arg(2000)
    ->Arg(4000)
    ->Arg(8000)
    ->Arg(16000)
    ->Arg(32000)
    ->Arg(64000)
    ->Arg(128000)
    ->Arg(256000)
    ->Arg(384000)
    ->Arg(512000)
    ->Arg(1024000)
    ->Arg(2048000)
    ->Arg(4096000);

void BM_IndexDocumentWithHiragana(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  IcingFilesystem icing_filesystem;
  Filesystem filesystem;
  std::string base_dir = GetTestTempDir() + "/index_processor_benchmark";
  std::string index_dir = base_dir + "/index_test/";
  std::string integer_index_dir = base_dir + "/integer_index_test/";

  CleanUp(filesystem, base_dir);
  ASSERT_THAT(filesystem.CreateDirectoryRecursively(base_dir.c_str()),
              IsTrue());

  std::unique_ptr<Index> index =
      CreateIndex(icing_filesystem, filesystem, index_dir);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      IntegerIndex::Create(filesystem, integer_index_dir,
                           IntegerIndex::kDefaultNumDataThresholdForBucketSplit,
                           /*pre_mapping_fbv=*/true));
  language_segmenter_factory::SegmenterOptions options(ULOC_US);
  std::unique_ptr<LanguageSegmenter> language_segmenter =
      language_segmenter_factory::Create(std::move(options)).ValueOrDie();
  std::unique_ptr<Normalizer> normalizer = CreateNormalizer();
  Clock clock;
  std::unique_ptr<SchemaStore> schema_store =
      CreateSchemaStore(filesystem, &clock, base_dir);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<std::unique_ptr<DataIndexingHandler>> handlers,
      CreateDataIndexingHandlers(&clock, normalizer.get(), index.get(),
                                 integer_index.get()));
  auto index_processor =
      std::make_unique<IndexProcessor>(std::move(handlers), &clock);

  DocumentProto input_document = CreateDocumentWithHiragana(state.range(0));
  TokenizedDocument tokenized_document(std::move(
      TokenizedDocument::Create(schema_store.get(), language_segmenter.get(),
                                input_document)
          .ValueOrDie()));

  DocumentId document_id = 0;
  for (auto _ : state) {
    ICING_ASSERT_OK(
        index_processor->IndexDocument(tokenized_document, document_id++));
  }

  index_processor.reset();
  schema_store.reset();
  normalizer.reset();
  language_segmenter.reset();
  integer_index.reset();
  index.reset();

  CleanUp(filesystem, base_dir);
}
BENCHMARK(BM_IndexDocumentWithHiragana)
    ->Arg(1000)
    ->Arg(2000)
    ->Arg(4000)
    ->Arg(8000)
    ->Arg(16000)
    ->Arg(32000)
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
