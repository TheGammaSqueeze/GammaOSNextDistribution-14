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

#include "testing/base/public/benchmark.h"
#include "gmock/gmock.h"
#include "third_party/absl/flags/flag.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/result/snippet-retriever.h"
#include "icing/schema-builder.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/random-string.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/transform/normalizer-factory.h"
#include "icing/util/clock.h"
#include "icing/util/logging.h"
#include "unicode/uloc.h"

// Run on a Linux workstation:
//    $ blaze build -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/result:snippet-retriever_benchmark
//
//    $ blaze-bin/icing/result/snippet-retriever_benchmark
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
//    //icing/result:snippet-retriever_benchmark
//
//    $ adb push blaze-bin/icing/result/snippet-retriever_benchmark
//    /data/local/tmp/
//
//    $ adb shell /data/local/tmp/snippet-retriever_benchmark
//    --benchmark_filter=all --adb

// Flag to tell the benchmark that it'll be run on an Android device via adb,
// the benchmark will set up data files accordingly.
ABSL_FLAG(bool, adb, false, "run benchmark via ADB on an Android device");

namespace icing {
namespace lib {

namespace {

using ::testing::SizeIs;

void BM_SnippetOneProperty(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  const std::string base_dir = GetTestTempDir() + "/query_processor_benchmark";
  const std::string schema_dir = base_dir + "/schema";
  Filesystem filesystem;
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
  if (!filesystem.CreateDirectoryRecursively(schema_dir.c_str())) {
    ICING_LOG(ERROR) << "Failed to create test directories";
  }

  language_segmenter_factory::SegmenterOptions options(ULOC_US);
  std::unique_ptr<LanguageSegmenter> language_segmenter =
      language_segmenter_factory::Create(std::move(options)).ValueOrDie();
  std::unique_ptr<Normalizer> normalizer =
      normalizer_factory::Create(
          /*max_term_byte_size=*/std::numeric_limits<int>::max())
          .ValueOrDie();

  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("type1").AddProperty(
              PropertyConfigBuilder()
                  .SetName("prop1")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  Clock clock;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem, schema_dir, &clock));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  auto snippet_retriever =
      SnippetRetriever::Create(schema_store.get(), language_segmenter.get(),
                               normalizer.get())
          .ValueOrDie();

  int num_matches = state.range(0);
  int total_terms = state.range(1);

  std::default_random_engine random;
  std::vector<std::string> language =
      CreateLanguages(/*language_size=*/1000, &random);
  std::uniform_int_distribution<size_t> uniform(0u, language.size() - 1);
  std::uniform_real_distribution<double> uniform_double(0.0, 1.0);

  std::string text;
  int num_actual_matches = 0;
  double match_chance;
  while (total_terms-- > 0) {
    std::string term;
    match_chance = static_cast<double>(num_matches) / total_terms;
    if (uniform_double(random) <= match_chance) {
      --num_matches;
      ++num_actual_matches;
      term = "foo";
    } else {
      term = language.at(uniform(random));
    }
    absl_ports::StrAppend(&text, " ", term);
  }
  DocumentProto document = DocumentBuilder()
                               .SetKey("icing", "uri1")
                               .SetSchema("type1")
                               .AddStringProperty("prop1", text)
                               .Build();
  SectionRestrictQueryTermsMap query_terms = {{"", {"foo"}}};
  ResultSpecProto::SnippetSpecProto snippet_spec;
  snippet_spec.set_num_to_snippet(100000);
  snippet_spec.set_num_matches_per_property(100000);
  snippet_spec.set_max_window_utf32_length(64);

  SectionIdMask section_id_mask = 0x01;
  SnippetProto snippet_proto;
  for (auto _ : state) {
    snippet_proto = snippet_retriever->RetrieveSnippet(
        query_terms, TERM_MATCH_PREFIX, snippet_spec, document,
        section_id_mask);
    ASSERT_THAT(snippet_proto.entries(), SizeIs(1));
    ASSERT_THAT(snippet_proto.entries(0).snippet_matches(),
                SizeIs(num_actual_matches));
  }

  // Destroy the schema store before the whole directory is removed because they
  // persist data in destructor.
  schema_store.reset();
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
}
BENCHMARK(BM_SnippetOneProperty)
    // Arguments: num_matches, total_terms
    ->ArgPair(1, 1)
    ->ArgPair(1, 16)          // single match
    ->ArgPair(2, 16)          // ~10% matches
    ->ArgPair(3, 16)          // ~20% matches
    ->ArgPair(8, 16)          // 50% matches
    ->ArgPair(16, 16)         // 100% matches
    ->ArgPair(1, 128)         // single match
    ->ArgPair(13, 128)        // ~10% matches
    ->ArgPair(26, 128)        // ~20% matches
    ->ArgPair(64, 128)        // 50% matches
    ->ArgPair(128, 128)       // 100% matches
    ->ArgPair(1, 512)         // single match
    ->ArgPair(51, 512)        // ~10% matches
    ->ArgPair(102, 512)       // ~20% matches
    ->ArgPair(256, 512)       // 50% matches
    ->ArgPair(512, 512)       // 100% matches
    ->ArgPair(1, 1024)        // single match
    ->ArgPair(102, 1024)      // ~10% matches
    ->ArgPair(205, 1024)      // ~20% matches
    ->ArgPair(512, 1024)      // 50% matches
    ->ArgPair(1024, 1024)     // 100% matches
    ->ArgPair(1, 4096)        // single match
    ->ArgPair(410, 4096)      // ~10% matches
    ->ArgPair(819, 4096)      // ~20% matches
    ->ArgPair(2048, 4096)     // 50% matches
    ->ArgPair(4096, 4096)     // 100% matches
    ->ArgPair(1, 16384)       // single match
    ->ArgPair(1638, 16384)    // ~10% matches
    ->ArgPair(3277, 16384)    // ~20% matches
    ->ArgPair(8192, 16384)    // 50% matches
    ->ArgPair(16384, 16384);  // 100% matches

void BM_SnippetRfcOneProperty(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  const std::string base_dir = GetTestTempDir() + "/query_processor_benchmark";
  const std::string schema_dir = base_dir + "/schema";
  Filesystem filesystem;
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
  if (!filesystem.CreateDirectoryRecursively(schema_dir.c_str())) {
    ICING_LOG(ERROR) << "Failed to create test directories";
  }

  language_segmenter_factory::SegmenterOptions options(ULOC_US);
  std::unique_ptr<LanguageSegmenter> language_segmenter =
      language_segmenter_factory::Create(std::move(options)).ValueOrDie();
  std::unique_ptr<Normalizer> normalizer =
      normalizer_factory::Create(
          /*max_term_byte_size=*/std::numeric_limits<int>::max())
          .ValueOrDie();

  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("type1").AddProperty(
              PropertyConfigBuilder()
                  .SetName("prop1")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();
  Clock clock;
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem, schema_dir, &clock));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  auto snippet_retriever =
      SnippetRetriever::Create(schema_store.get(), language_segmenter.get(),
                               normalizer.get())
          .ValueOrDie();

  int num_matches = state.range(0);
  int total_terms = state.range(1);

  std::default_random_engine random;
  std::vector<std::string> language =
      CreateLanguages(/*language_size=*/1000, &random);
  std::uniform_int_distribution<size_t> uniform(0u, language.size() - 1);
  std::uniform_real_distribution<double> uniform_double(0.0, 1.0);

  std::string text;
  int num_actual_matches = 0;
  double match_chance;
  while (total_terms-- > 0) {
    std::string term;
    match_chance = static_cast<double>(num_matches) / total_terms;
    if (uniform_double(random) <= match_chance) {
      --num_matches;
      ++num_actual_matches;
      term = "foo@google.com";
    } else {
      term = absl_ports::StrCat(language.at(uniform(random)), "@google.com");
    }
    absl_ports::StrAppend(&text, ",", term);
  }
  DocumentProto document = DocumentBuilder()
                               .SetKey("icing", "uri1")
                               .SetSchema("type1")
                               .AddStringProperty("prop1", text)
                               .Build();
  SectionRestrictQueryTermsMap query_terms = {{"", {"foo"}}};
  ResultSpecProto::SnippetSpecProto snippet_spec;
  snippet_spec.set_num_to_snippet(100000);
  snippet_spec.set_num_matches_per_property(100000);
  snippet_spec.set_max_window_utf32_length(64);

  SectionIdMask section_id_mask = 0x01;
  SnippetProto snippet_proto;
  for (auto _ : state) {
    snippet_proto = snippet_retriever->RetrieveSnippet(
        query_terms, TERM_MATCH_PREFIX, snippet_spec, document,
        section_id_mask);
    ASSERT_THAT(snippet_proto.entries(), SizeIs(1));
    ASSERT_THAT(snippet_proto.entries(0).snippet_matches(),
                SizeIs(num_actual_matches));
  }

  // Destroy the schema store before the whole directory is removed because they
  // persist data in destructor.
  schema_store.reset();
  filesystem.DeleteDirectoryRecursively(base_dir.c_str());
}
BENCHMARK(BM_SnippetRfcOneProperty)
    // Arguments: num_matches, total_terms
    ->ArgPair(1, 1)
    ->ArgPair(1, 16)          // single match
    ->ArgPair(2, 16)          // ~10% matches
    ->ArgPair(3, 16)          // ~20% matches
    ->ArgPair(8, 16)          // 50% matches
    ->ArgPair(16, 16)         // 100% matches
    ->ArgPair(1, 128)         // single match
    ->ArgPair(13, 128)        // ~10% matches
    ->ArgPair(26, 128)        // ~20% matches
    ->ArgPair(64, 128)        // 50% matches
    ->ArgPair(128, 128)       // 100% matches
    ->ArgPair(1, 512)         // single match
    ->ArgPair(51, 512)        // ~10% matches
    ->ArgPair(102, 512)       // ~20% matches
    ->ArgPair(256, 512)       // 50% matches
    ->ArgPair(512, 512)       // 100% matches
    ->ArgPair(1, 1024)        // single match
    ->ArgPair(102, 1024)      // ~10% matches
    ->ArgPair(205, 1024)      // ~20% matches
    ->ArgPair(512, 1024)      // 50% matches
    ->ArgPair(1024, 1024)     // 100% matches
    ->ArgPair(1, 4096)        // single match
    ->ArgPair(410, 4096)      // ~10% matches
    ->ArgPair(819, 4096)      // ~20% matches
    ->ArgPair(2048, 4096)     // 50% matches
    ->ArgPair(4096, 4096)     // 100% matches
    ->ArgPair(1, 16384)       // single match
    ->ArgPair(1638, 16384)    // ~10% matches
    ->ArgPair(3277, 16384)    // ~20% matches
    ->ArgPair(8192, 16384)    // 50% matches
    ->ArgPair(16384, 16384);  // 100% matches

}  // namespace

}  // namespace lib
}  // namespace icing
