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

#include <cstdint>
#include <limits>
#include <memory>
#include <string>
#include <utility>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/icing-search-engine.h"
#include "icing/portable/endian.h"
#include "icing/portable/equals-proto.h"
#include "icing/portable/platform.h"
#include "icing/schema-builder.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/jni-test-helpers.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::icing::lib::portable_equals_proto::EqualsProto;
using ::testing::Eq;

// For mocking purpose, we allow tests to provide a custom Filesystem.
class TestIcingSearchEngine : public IcingSearchEngine {
 public:
  TestIcingSearchEngine(const IcingSearchEngineOptions& options,
                        std::unique_ptr<const Filesystem> filesystem,
                        std::unique_ptr<const IcingFilesystem> icing_filesystem,
                        std::unique_ptr<Clock> clock,
                        std::unique_ptr<JniCache> jni_cache)
      : IcingSearchEngine(options, std::move(filesystem),
                          std::move(icing_filesystem), std::move(clock),
                          std::move(jni_cache)) {}
};

std::string GetTestBaseDir() { return GetTestTempDir() + "/icing"; }

class IcingSearchEngineBackwardsCompatibilityTest : public testing::Test {
 protected:
  void SetUp() override {
    filesystem_.CreateDirectoryRecursively(GetTestBaseDir().c_str());
  }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(GetTestBaseDir().c_str());
  }

  const Filesystem* filesystem() const { return &filesystem_; }

 private:
  Filesystem filesystem_;
};

ScoringSpecProto GetDefaultScoringSpec() {
  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);
  return scoring_spec;
}

std::string GetTestDataDir(std::string_view test_subdir) {
  if (IsAndroidX86()) {
    return GetTestFilePath(
        absl_ports::StrCat("icing/testdata/", test_subdir,
                           "/icing_search_engine_android_x86"));
  } else if (IsAndroidArm()) {
    return GetTestFilePath(
        absl_ports::StrCat("icing/testdata/", test_subdir,
                           "/icing_search_engine_android_arm"));
  } else if (IsIosPlatform()) {
    return GetTestFilePath(absl_ports::StrCat("icing/testdata/",
                                              test_subdir,
                                              "/icing_search_engine_ios"));
  } else {
    return GetTestFilePath(absl_ports::StrCat("icing/testdata/",
                                              test_subdir,
                                              "/icing_search_engine_linux"));
  }
}

TEST_F(IcingSearchEngineBackwardsCompatibilityTest,
       MigrateToPortableFileBackedProtoLog) {
  // Copy the testdata files into our IcingSearchEngine directory
  std::string dir_without_portable_log = GetTestDataDir("not_portable_log");

  // Create dst directory that we'll initialize the IcingSearchEngine over.
  std::string base_dir = GetTestBaseDir() + "_migrate";
  ASSERT_THAT(filesystem()->DeleteDirectoryRecursively(base_dir.c_str()), true);
  ASSERT_THAT(filesystem()->CreateDirectoryRecursively(base_dir.c_str()), true);

  ASSERT_TRUE(filesystem()->CopyDirectory(dir_without_portable_log.c_str(),
                                          base_dir.c_str(),
                                          /*recursive=*/true));

  IcingSearchEngineOptions icing_options;
  icing_options.set_base_dir(base_dir);

  IcingSearchEngine icing(icing_options, GetTestJniCache());
  InitializeResultProto init_result = icing.Initialize();
  EXPECT_THAT(init_result.status(), ProtoIsOk());

  // Since there will be version change, the recovery cause will be
  // VERSION_CHANGED.
  EXPECT_THAT(init_result.initialize_stats().document_store_data_status(),
              Eq(InitializeStatsProto::NO_DATA_LOSS));
  EXPECT_THAT(init_result.initialize_stats().document_store_recovery_cause(),
              Eq(InitializeStatsProto::VERSION_CHANGED));
  EXPECT_THAT(init_result.initialize_stats().schema_store_recovery_cause(),
              Eq(InitializeStatsProto::VERSION_CHANGED));
  EXPECT_THAT(init_result.initialize_stats().index_restoration_cause(),
              Eq(InitializeStatsProto::VERSION_CHANGED));

  // Set up schema, this is the one used to validate documents in the testdata
  // files. Do not change unless you're also updating the testdata files.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("email")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("subject")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Make sure our schema is still the same as we expect. If not, there's
  // definitely no way we're getting the documents back that we expect.
  GetSchemaResultProto expected_get_schema_result_proto;
  expected_get_schema_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_schema_result_proto.mutable_schema() = schema;
  ASSERT_THAT(icing.GetSchema(), EqualsProto(expected_get_schema_result_proto));

  // These are the documents that are stored in the testdata files. Do not
  // change unless you're also updating the testdata files.
  DocumentProto document1 = DocumentBuilder()
                                .SetKey("namespace1", "uri1")
                                .SetSchema("email")
                                .SetCreationTimestampMs(10)
                                .AddStringProperty("subject", "foo")
                                .AddStringProperty("body", "bar")
                                .Build();

  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace1", "uri2")
                                .SetSchema("email")
                                .SetCreationTimestampMs(20)
                                .SetScore(321)
                                .AddStringProperty("body", "baz bat")
                                .Build();

  DocumentProto document3 = DocumentBuilder()
                                .SetKey("namespace2", "uri1")
                                .SetSchema("email")
                                .SetCreationTimestampMs(30)
                                .SetScore(123)
                                .AddStringProperty("subject", "phoo")
                                .Build();

  // Document 1 and 3 were put normally, and document 2 was deleted in our
  // testdata files.
  EXPECT_THAT(icing
                  .Get(document1.namespace_(), document1.uri(),
                       GetResultSpecProto::default_instance())
                  .document(),
              EqualsProto(document1));
  EXPECT_THAT(icing
                  .Get(document2.namespace_(), document2.uri(),
                       GetResultSpecProto::default_instance())
                  .status(),
              ProtoStatusIs(StatusProto::NOT_FOUND));
  EXPECT_THAT(icing
                  .Get(document3.namespace_(), document3.uri(),
                       GetResultSpecProto::default_instance())
                  .document(),
              EqualsProto(document3));

  // Searching for "foo" should get us document1.
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("foo");

  SearchResultProto expected_document1;
  expected_document1.mutable_status()->set_code(StatusProto::OK);
  *expected_document1.mutable_results()->Add()->mutable_document() = document1;

  SearchResultProto actual_results =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(actual_results,
              EqualsSearchResultIgnoreStatsAndScores(expected_document1));

  // Searching for "baz" would've gotten us document2, except it got deleted.
  // Make sure that it's cleared from our index too.
  search_spec.set_query("baz");

  SearchResultProto expected_no_documents;
  expected_no_documents.mutable_status()->set_code(StatusProto::OK);

  actual_results = icing.Search(search_spec, GetDefaultScoringSpec(),
                                ResultSpecProto::default_instance());
  EXPECT_THAT(actual_results,
              EqualsSearchResultIgnoreStatsAndScores(expected_no_documents));

  // Searching for "phoo" should get us document3.
  search_spec.set_query("phoo");

  SearchResultProto expected_document3;
  expected_document3.mutable_status()->set_code(StatusProto::OK);
  *expected_document3.mutable_results()->Add()->mutable_document() = document3;

  actual_results = icing.Search(search_spec, GetDefaultScoringSpec(),
                                ResultSpecProto::default_instance());
  EXPECT_THAT(actual_results,
              EqualsSearchResultIgnoreStatsAndScores(expected_document3));
}

TEST_F(IcingSearchEngineBackwardsCompatibilityTest, MigrateToLargerScale) {
  // Copy the testdata files into our IcingSearchEngine directory
  std::string test_data_dir = GetTestDataDir("icing_scale_migration");

  // Create dst directory that we'll initialize the IcingSearchEngine over.
  std::string base_dir = GetTestBaseDir() + "_migrate";
  ASSERT_THAT(filesystem()->DeleteDirectoryRecursively(base_dir.c_str()), true);
  ASSERT_THAT(filesystem()->CreateDirectoryRecursively(base_dir.c_str()), true);

  ASSERT_TRUE(filesystem()->CopyDirectory(test_data_dir.c_str(),
                                          base_dir.c_str(),
                                          /*recursive=*/true));

  IcingSearchEngineOptions icing_options;
  icing_options.set_base_dir(base_dir);

  IcingSearchEngine icing(icing_options, GetTestJniCache());
  InitializeResultProto init_result = icing.Initialize();
  EXPECT_THAT(init_result.status(), ProtoIsOk());

  // Since there will be version change, the recovery cause will be
  // VERSION_CHANGED.
  EXPECT_THAT(init_result.initialize_stats().document_store_data_status(),
              Eq(InitializeStatsProto::NO_DATA_LOSS));
  EXPECT_THAT(init_result.initialize_stats().document_store_recovery_cause(),
              Eq(InitializeStatsProto::VERSION_CHANGED));
  EXPECT_THAT(init_result.initialize_stats().schema_store_recovery_cause(),
              Eq(InitializeStatsProto::VERSION_CHANGED));
  EXPECT_THAT(init_result.initialize_stats().index_restoration_cause(),
              Eq(InitializeStatsProto::VERSION_CHANGED));

  // Verify that the schema stored in the index matches the one that we expect.
  // Do not change unless you're also updating the testdata files.
  SchemaProto expected_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("email")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("subject")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Make sure our schema is still the same as we expect. If not, there's
  // definitely no way we're getting the documents back that we expect.
  GetSchemaResultProto expected_get_schema_result_proto;
  expected_get_schema_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_schema_result_proto.mutable_schema() = expected_schema;
  ASSERT_THAT(icing.GetSchema(), EqualsProto(expected_get_schema_result_proto));

  // These are the documents that are stored in the testdata files. Do not
  // change unless you're also updating the testdata files.
  DocumentProto expected_document1 = DocumentBuilder()
                                         .SetKey("namespace1", "uri1")
                                         .SetSchema("email")
                                         .SetCreationTimestampMs(10)
                                         .AddStringProperty("subject", "foo")
                                         .AddStringProperty("body", "bar")
                                         .Build();

  DocumentProto expected_deleted_document2 =
      DocumentBuilder()
          .SetKey("namespace1", "uri2")
          .SetSchema("email")
          .SetCreationTimestampMs(20)
          .SetScore(321)
          .AddStringProperty("body", "baz bat")
          .Build();

  DocumentProto expected_document3 = DocumentBuilder()
                                         .SetKey("namespace2", "uri1")
                                         .SetSchema("email")
                                         .SetCreationTimestampMs(30)
                                         .SetScore(123)
                                         .AddStringProperty("subject", "phoo")
                                         .Build();

  // Document 1 and 3 were put normally, and document 2 was deleted in our
  // testdata files.
  EXPECT_THAT(
      icing
          .Get(expected_document1.namespace_(), expected_document1.uri(),
               GetResultSpecProto::default_instance())
          .document(),
      EqualsProto(expected_document1));
  EXPECT_THAT(icing
                  .Get(expected_deleted_document2.namespace_(),
                       expected_deleted_document2.uri(),
                       GetResultSpecProto::default_instance())
                  .status(),
              ProtoStatusIs(StatusProto::NOT_FOUND));
  EXPECT_THAT(
      icing
          .Get(expected_document3.namespace_(), expected_document3.uri(),
               GetResultSpecProto::default_instance())
          .document(),
      EqualsProto(expected_document3));

  // Searching for "foo" should get us document1.
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("foo");

  SearchResultProto expected_document1_search;
  expected_document1_search.mutable_status()->set_code(StatusProto::OK);
  *expected_document1_search.mutable_results()->Add()->mutable_document() =
      expected_document1;

  SearchResultProto actual_results =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                  expected_document1_search));

  // Searching for "baz" would've gotten us document2, except it got deleted.
  // Make sure that it's cleared from our index too.
  search_spec.set_query("baz");

  SearchResultProto expected_no_documents;
  expected_no_documents.mutable_status()->set_code(StatusProto::OK);

  actual_results = icing.Search(search_spec, GetDefaultScoringSpec(),
                                ResultSpecProto::default_instance());
  EXPECT_THAT(actual_results,
              EqualsSearchResultIgnoreStatsAndScores(expected_no_documents));

  // Searching for "phoo" should get us document3.
  search_spec.set_query("phoo");

  SearchResultProto expected_document3_search;
  expected_document3_search.mutable_status()->set_code(StatusProto::OK);
  *expected_document3_search.mutable_results()->Add()->mutable_document() =
      expected_document3;

  actual_results = icing.Search(search_spec, GetDefaultScoringSpec(),
                                ResultSpecProto::default_instance());
  EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                  expected_document3_search));
}

TEST_F(IcingSearchEngineBackwardsCompatibilityTest,
       MigrateToAppendOnlySchemaStorage) {
  // Copy the testdata files into our IcingSearchEngine directory
  std::string test_data_dir = GetTestDataDir("blob_schema_store");

  // Create dst directory that we'll initialize the IcingSearchEngine over.
  std::string base_dir = GetTestBaseDir() + "_migrate";
  ASSERT_THAT(filesystem()->DeleteDirectoryRecursively(base_dir.c_str()), true);
  ASSERT_THAT(filesystem()->CreateDirectoryRecursively(base_dir.c_str()), true);

  ASSERT_TRUE(filesystem()->CopyDirectory(test_data_dir.c_str(),
                                          base_dir.c_str(),
                                          /*recursive=*/true));

  IcingSearchEngineOptions icing_options;
  icing_options.set_base_dir(base_dir);

  IcingSearchEngine icing(icing_options, GetTestJniCache());
  InitializeResultProto init_result = icing.Initialize();
  EXPECT_THAT(init_result.status(), ProtoIsOk());

  // Since there will be version change, the recovery cause will be
  // VERSION_CHANGED.
  EXPECT_THAT(init_result.initialize_stats().document_store_data_status(),
              Eq(InitializeStatsProto::NO_DATA_LOSS));
  EXPECT_THAT(init_result.initialize_stats().document_store_recovery_cause(),
              Eq(InitializeStatsProto::VERSION_CHANGED));
  // TODO: create enum code for legacy schema store recovery after schema store
  // change is made.
  EXPECT_THAT(init_result.initialize_stats().schema_store_recovery_cause(),
              Eq(InitializeStatsProto::VERSION_CHANGED));
  EXPECT_THAT(init_result.initialize_stats().index_restoration_cause(),
              Eq(InitializeStatsProto::VERSION_CHANGED));

  // Verify that the schema stored in the index matches the one that we expect.
  // Do not change unless you're also updating the testdata files.
  SchemaProto expected_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("email")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("subject")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("transaction")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("unindexedStringProperty")
                                        .SetDataType(TYPE_STRING)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("unindexedIntegerProperty")
                                        .SetDataType(TYPE_INT64)
                                        .SetCardinality(CARDINALITY_OPTIONAL))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("indexableIntegerProperty")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("stringExactProperty")
                                        .SetDataTypeString(TERM_MATCH_EXACT,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REPEATED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("stringPrefixProperty")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  GetSchemaResultProto expected_get_schema_result_proto;
  expected_get_schema_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_schema_result_proto.mutable_schema() = expected_schema;
  ASSERT_THAT(icing.GetSchema(), EqualsProto(expected_get_schema_result_proto));

  // These are the documents that are stored in the testdata files. Do not
  // change unless you're also updating the testdata files.
  DocumentProto expected_document1 = DocumentBuilder()
                                         .SetKey("namespace1", "uri1")
                                         .SetSchema("email")
                                         .SetCreationTimestampMs(10)
                                         .AddStringProperty("subject", "foo")
                                         .AddStringProperty("body", "bar")
                                         .Build();

  DocumentProto expected_document2 = DocumentBuilder()
                                         .SetKey("namespace2", "uri1")
                                         .SetSchema("email")
                                         .SetCreationTimestampMs(20)
                                         .SetScore(123)
                                         .AddStringProperty("subject", "phoo")
                                         .Build();

  DocumentProto expected_document3 =
      DocumentBuilder()
          .SetKey("namespace3", "uri3")
          .SetSchema("transaction")
          .SetCreationTimestampMs(30)
          .SetScore(123)
          .AddStringProperty("stringExactProperty", "foo")
          .AddInt64Property("indexableIntegerProperty", 10)
          .Build();

  EXPECT_THAT(
      icing
          .Get(expected_document1.namespace_(), expected_document1.uri(),
               GetResultSpecProto::default_instance())
          .document(),
      EqualsProto(expected_document1));
  EXPECT_THAT(
      icing
          .Get(expected_document2.namespace_(), expected_document2.uri(),
               GetResultSpecProto::default_instance())
          .document(),
      EqualsProto(expected_document2));
  EXPECT_THAT(
      icing
          .Get(expected_document3.namespace_(), expected_document3.uri(),
               GetResultSpecProto::default_instance())
          .document(),
      EqualsProto(expected_document3));

  // Searching for "foo" should get us document1 and not document3 due to the
  // schema type filter.
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("foo");
  search_spec.add_schema_type_filters("email");

  SearchResultProto expected_document1_search;
  expected_document1_search.mutable_status()->set_code(StatusProto::OK);
  *expected_document1_search.mutable_results()->Add()->mutable_document() =
      expected_document1;

  SearchResultProto actual_results =
      icing.Search(search_spec, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                  expected_document1_search));

  // Searching for "phoo" should get us document2.
  search_spec.set_query("phoo");

  SearchResultProto expected_document2_search;
  expected_document2_search.mutable_status()->set_code(StatusProto::OK);
  *expected_document2_search.mutable_results()->Add()->mutable_document() =
      expected_document2;

  actual_results = icing.Search(search_spec, GetDefaultScoringSpec(),
                                ResultSpecProto::default_instance());
  EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                  expected_document2_search));

  // Searching for "foo" should get us both document 1 and document3 now that
  // schema type 'transaction' has been added to the schema filter.
  search_spec.set_query("foo");
  search_spec.add_schema_type_filters("transaction");

  SearchResultProto expected_document_1_and_3_search;
  expected_document_1_and_3_search.mutable_status()->set_code(StatusProto::OK);
  *expected_document_1_and_3_search.mutable_results()
       ->Add()
       ->mutable_document() = expected_document3;
  *expected_document_1_and_3_search.mutable_results()
       ->Add()
       ->mutable_document() = expected_document1;

  actual_results = icing.Search(search_spec, GetDefaultScoringSpec(),
                                ResultSpecProto::default_instance());
  EXPECT_THAT(actual_results, EqualsSearchResultIgnoreStatsAndScores(
                                  expected_document_1_and_3_search));
}

}  // namespace
}  // namespace lib
}  // namespace icing
