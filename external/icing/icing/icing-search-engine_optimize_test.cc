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

#include <cstdint>
#include <limits>
#include <memory>
#include <string>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/file/mock-filesystem.h"
#include "icing/icing-search-engine.h"
#include "icing/jni/jni-cache.h"
#include "icing/join/join-processor.h"
#include "icing/portable/endian.h"
#include "icing/portable/equals-proto.h"
#include "icing/portable/platform.h"
#include "icing/proto/debug.pb.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/document_wrapper.pb.h"
#include "icing/proto/initialize.pb.h"
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
#include "icing/query/query-features.h"
#include "icing/schema-builder.h"
#include "icing/store/document-log-creator.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/jni-test-helpers.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::icing::lib::portable_equals_proto::EqualsProto;
using ::testing::Eq;
using ::testing::Ge;
using ::testing::Gt;
using ::testing::HasSubstr;
using ::testing::Lt;
using ::testing::Return;

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

// This test is meant to cover all tests relating to
// IcingSearchEngine::Optimize.
class IcingSearchEngineOptimizeTest : public testing::Test {
 protected:
  void SetUp() override {
    if (!IsCfStringTokenization() && !IsReverseJniTokenization()) {
      // If we've specified using the reverse-JNI method for segmentation (i.e.
      // not ICU), then we won't have the ICU data file included to set up.
      // Technically, we could choose to use reverse-JNI for segmentation AND
      // include an ICU data file, but that seems unlikely and our current BUILD
      // setup doesn't do this.
      // File generated via icu_data_file rule in //icing/BUILD.
      std::string icu_data_file_path =
          GetTestFilePath("icing/icu.dat");
      ICING_ASSERT_OK(
          icu_data_file_helper::SetUpICUDataFile(icu_data_file_path));
    }
    filesystem_.CreateDirectoryRecursively(GetTestBaseDir().c_str());
  }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(GetTestBaseDir().c_str());
  }

  const Filesystem* filesystem() const { return &filesystem_; }

 private:
  Filesystem filesystem_;
};

// Non-zero value so we don't override it to be the current time
constexpr int64_t kDefaultCreationTimestampMs = 1575492852000;

IcingSearchEngineOptions GetDefaultIcingOptions() {
  IcingSearchEngineOptions icing_options;
  icing_options.set_base_dir(GetTestBaseDir());
  return icing_options;
}

ScoringSpecProto GetDefaultScoringSpec() {
  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE);
  return scoring_spec;
}

// TODO(b/272145329): create SearchSpecBuilder, JoinSpecBuilder,
// SearchResultProtoBuilder and ResultProtoBuilder for unit tests and build all
// instances by them.

TEST_F(IcingSearchEngineOptimizeTest,
       AllPageTokensShouldBeInvalidatedAfterOptimization) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body one")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "message body two")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::PREFIX);
  search_spec.set_query("message");

  ResultSpecProto result_spec;
  result_spec.set_num_per_page(1);

  // Searches and gets the first page, 1 result
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document2;
  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(search_result_proto.next_page_token(), Gt(kInvalidNextPageToken));
  uint64_t next_page_token = search_result_proto.next_page_token();
  // Since the token is a random number, we don't need to verify
  expected_search_result_proto.set_next_page_token(next_page_token);
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
  // Now document1 is still to be fetched.

  OptimizeResultProto optimize_result_proto;
  optimize_result_proto.mutable_status()->set_code(StatusProto::OK);
  optimize_result_proto.mutable_status()->set_message("");
  OptimizeResultProto actual_result = icing.Optimize();
  actual_result.clear_optimize_stats();
  ASSERT_THAT(actual_result, EqualsProto(optimize_result_proto));

  // Tries to fetch the second page, no results since all tokens have been
  // invalidated during Optimize()
  expected_search_result_proto.clear_results();
  expected_search_result_proto.clear_next_page_token();
  search_result_proto = icing.GetNextPage(next_page_token);
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_F(IcingSearchEngineOptimizeTest, OptimizationShouldRemoveDeletedDocs) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body one")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  IcingSearchEngineOptions icing_options = GetDefaultIcingOptions();

  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::NOT_FOUND);
  expected_get_result_proto.mutable_status()->set_message(
      "Document (namespace, uri1) not found.");
  {
    IcingSearchEngine icing(icing_options, GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());

    // Deletes document1
    ASSERT_THAT(icing.Delete("namespace", "uri1").status(), ProtoIsOk());
    const std::string document_log_path =
        icing_options.base_dir() + "/document_dir/" +
        DocumentLogCreator::GetDocumentLogFilename();
    int64_t document_log_size_before =
        filesystem()->GetFileSize(document_log_path.c_str());
    ASSERT_THAT(icing.Optimize().status(), ProtoIsOk());
    int64_t document_log_size_after =
        filesystem()->GetFileSize(document_log_path.c_str());

    // Validates that document can't be found right after Optimize()
    EXPECT_THAT(
        icing.Get("namespace", "uri1", GetResultSpecProto::default_instance()),
        EqualsProto(expected_get_result_proto));
    // Validates that document is actually removed from document log
    EXPECT_THAT(document_log_size_after, Lt(document_log_size_before));
  }  // Destroys IcingSearchEngine to make sure nothing is cached.

  IcingSearchEngine icing(icing_options, GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(
      icing.Get("namespace", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));
}

TEST_F(IcingSearchEngineOptimizeTest,
       OptimizationShouldDeleteTemporaryDirectory) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  IcingSearchEngineOptions icing_options = GetDefaultIcingOptions();
  IcingSearchEngine icing(icing_options, GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

  // Create a tmp dir that will be used in Optimize() to swap files,
  // this validates that any tmp dirs will be deleted before using.
  const std::string tmp_dir =
      icing_options.base_dir() + "/document_dir_optimize_tmp";

  const std::string tmp_file = tmp_dir + "/file";
  ASSERT_TRUE(filesystem()->CreateDirectory(tmp_dir.c_str()));
  ScopedFd fd(filesystem()->OpenForWrite(tmp_file.c_str()));
  ASSERT_TRUE(fd.is_valid());
  ASSERT_TRUE(filesystem()->Write(fd.get(), "1234", 4));
  fd.reset();

  EXPECT_THAT(icing.Optimize().status(), ProtoIsOk());

  EXPECT_FALSE(filesystem()->DirectoryExists(tmp_dir.c_str()));
  EXPECT_FALSE(filesystem()->FileExists(tmp_file.c_str()));
}

TEST_F(IcingSearchEngineOptimizeTest, GetOptimizeInfoHasCorrectStats) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body one")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace", "uri2")
                                .SetSchema("Message")
                                .AddStringProperty("body", "message body two")
                                .SetCreationTimestampMs(100)
                                .SetTtlMs(500)
                                .Build();

  {
    auto fake_clock = std::make_unique<FakeClock>();
    fake_clock->SetSystemTimeMilliseconds(1000);

    TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                                std::make_unique<Filesystem>(),
                                std::make_unique<IcingFilesystem>(),
                                std::move(fake_clock), GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

    // Just initialized, nothing is optimizable yet.
    GetOptimizeInfoResultProto optimize_info = icing.GetOptimizeInfo();
    EXPECT_THAT(optimize_info.status(), ProtoIsOk());
    EXPECT_THAT(optimize_info.optimizable_docs(), Eq(0));
    EXPECT_THAT(optimize_info.estimated_optimizable_bytes(), Eq(0));
    EXPECT_THAT(optimize_info.time_since_last_optimize_ms(), Eq(0));

    ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());

    // Only have active documents, nothing is optimizable yet.
    optimize_info = icing.GetOptimizeInfo();
    EXPECT_THAT(optimize_info.status(), ProtoIsOk());
    EXPECT_THAT(optimize_info.optimizable_docs(), Eq(0));
    EXPECT_THAT(optimize_info.estimated_optimizable_bytes(), Eq(0));
    EXPECT_THAT(optimize_info.time_since_last_optimize_ms(), Eq(0));

    // Deletes document1
    ASSERT_THAT(icing.Delete("namespace", "uri1").status(), ProtoIsOk());

    optimize_info = icing.GetOptimizeInfo();
    EXPECT_THAT(optimize_info.status(), ProtoIsOk());
    EXPECT_THAT(optimize_info.optimizable_docs(), Eq(1));
    EXPECT_THAT(optimize_info.estimated_optimizable_bytes(), Gt(0));
    EXPECT_THAT(optimize_info.time_since_last_optimize_ms(), Eq(0));
    int64_t first_estimated_optimizable_bytes =
        optimize_info.estimated_optimizable_bytes();

    // Add a second document, but it'll be expired since the time (1000) is
    // greater than the document's creation timestamp (100) + the document's ttl
    // (500)
    ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());

    optimize_info = icing.GetOptimizeInfo();
    EXPECT_THAT(optimize_info.status(), ProtoIsOk());
    EXPECT_THAT(optimize_info.optimizable_docs(), Eq(2));
    EXPECT_THAT(optimize_info.estimated_optimizable_bytes(),
                Gt(first_estimated_optimizable_bytes));
    EXPECT_THAT(optimize_info.time_since_last_optimize_ms(), Eq(0));

    // Optimize
    ASSERT_THAT(icing.Optimize().status(), ProtoIsOk());
  }

  {
    // Recreate with new time
    auto fake_clock = std::make_unique<FakeClock>();
    fake_clock->SetSystemTimeMilliseconds(5000);

    TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                                std::make_unique<Filesystem>(),
                                std::make_unique<IcingFilesystem>(),
                                std::move(fake_clock), GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

    // Nothing is optimizable now that everything has been optimized away.
    GetOptimizeInfoResultProto optimize_info = icing.GetOptimizeInfo();
    EXPECT_THAT(optimize_info.status(), ProtoIsOk());
    EXPECT_THAT(optimize_info.optimizable_docs(), Eq(0));
    EXPECT_THAT(optimize_info.estimated_optimizable_bytes(), Eq(0));
    EXPECT_THAT(optimize_info.time_since_last_optimize_ms(), Eq(4000));
  }
}

TEST_F(IcingSearchEngineOptimizeTest, GetAndPutShouldWorkAfterOptimization) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body one")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "message body two")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace", "uri3")
          .SetSchema("Message")
          .AddStringProperty("body", "message body three")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document4 =
      DocumentBuilder()
          .SetKey("namespace", "uri4")
          .SetSchema("Message")
          .AddStringProperty("body", "message body four")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document5 =
      DocumentBuilder()
          .SetKey("namespace", "uri5")
          .SetSchema("Message")
          .AddStringProperty("body", "message body five")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);

  {
    IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());

    ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(document3).status(), ProtoIsOk());
    ASSERT_THAT(icing.Delete("namespace", "uri2").status(), ProtoIsOk());
    ASSERT_THAT(icing.Optimize().status(), ProtoIsOk());

    // Validates that Get() and Put() are good right after Optimize()
    *expected_get_result_proto.mutable_document() = document1;
    EXPECT_THAT(
        icing.Get("namespace", "uri1", GetResultSpecProto::default_instance()),
        EqualsProto(expected_get_result_proto));
    EXPECT_THAT(
        icing.Get("namespace", "uri2", GetResultSpecProto::default_instance())
            .status()
            .code(),
        Eq(StatusProto::NOT_FOUND));
    *expected_get_result_proto.mutable_document() = document3;
    EXPECT_THAT(
        icing.Get("namespace", "uri3", GetResultSpecProto::default_instance()),
        EqualsProto(expected_get_result_proto));
    EXPECT_THAT(icing.Put(document4).status(), ProtoIsOk());
  }  // Destroys IcingSearchEngine to make sure nothing is cached.

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  *expected_get_result_proto.mutable_document() = document1;
  EXPECT_THAT(
      icing.Get("namespace", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));
  EXPECT_THAT(
      icing.Get("namespace", "uri2", GetResultSpecProto::default_instance())
          .status()
          .code(),
      Eq(StatusProto::NOT_FOUND));
  *expected_get_result_proto.mutable_document() = document3;
  EXPECT_THAT(
      icing.Get("namespace", "uri3", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));
  *expected_get_result_proto.mutable_document() = document4;
  EXPECT_THAT(
      icing.Get("namespace", "uri4", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  EXPECT_THAT(icing.Put(document5).status(), ProtoIsOk());
}

TEST_F(IcingSearchEngineOptimizeTest,
       GetAndPutShouldWorkAfterOptimizationWithEmptyDocuments) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  DocumentProto empty_document1 =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto empty_document2 =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto empty_document3 =
      DocumentBuilder()
          .SetKey("namespace", "uri3")
          .SetSchema("Message")
          .AddStringProperty("body", "")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(std::move(schema)).status(), ProtoIsOk());

  ASSERT_THAT(icing.Put(empty_document1).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(empty_document2).status(), ProtoIsOk());
  ASSERT_THAT(icing.Delete("namespace", "uri2").status(), ProtoIsOk());
  ASSERT_THAT(icing.Optimize().status(), ProtoIsOk());

  // Validates that Get() and Put() are good right after Optimize()
  *expected_get_result_proto.mutable_document() = empty_document1;
  EXPECT_THAT(
      icing.Get("namespace", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));
  EXPECT_THAT(
      icing.Get("namespace", "uri2", GetResultSpecProto::default_instance())
          .status()
          .code(),
      Eq(StatusProto::NOT_FOUND));
  EXPECT_THAT(icing.Put(empty_document3).status(), ProtoIsOk());
}

TEST_F(IcingSearchEngineOptimizeTest, DeleteShouldWorkAfterOptimization) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body one")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "message body two")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  {
    IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
    ASSERT_THAT(icing.Optimize().status(), ProtoIsOk());

    // Validates that Delete() works right after Optimize()
    EXPECT_THAT(icing.Delete("namespace", "uri1").status(), ProtoIsOk());

    GetResultProto expected_get_result_proto;
    expected_get_result_proto.mutable_status()->set_code(
        StatusProto::NOT_FOUND);
    expected_get_result_proto.mutable_status()->set_message(
        "Document (namespace, uri1) not found.");
    EXPECT_THAT(
        icing.Get("namespace", "uri1", GetResultSpecProto::default_instance()),
        EqualsProto(expected_get_result_proto));

    expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
    expected_get_result_proto.mutable_status()->clear_message();
    *expected_get_result_proto.mutable_document() = document2;
    EXPECT_THAT(
        icing.Get("namespace", "uri2", GetResultSpecProto::default_instance()),
        EqualsProto(expected_get_result_proto));
  }  // Destroys IcingSearchEngine to make sure nothing is cached.

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.Delete("namespace", "uri2").status(), ProtoIsOk());

  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::NOT_FOUND);
  expected_get_result_proto.mutable_status()->set_message(
      "Document (namespace, uri1) not found.");
  EXPECT_THAT(
      icing.Get("namespace", "uri1", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  expected_get_result_proto.mutable_status()->set_message(
      "Document (namespace, uri2) not found.");
  EXPECT_THAT(
      icing.Get("namespace", "uri2", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));
}

TEST_F(IcingSearchEngineOptimizeTest, OptimizationFailureUninitializesIcing) {
  // Setup filesystem to fail
  auto mock_filesystem = std::make_unique<MockFilesystem>();
  bool just_swapped_files = false;
  auto create_dir_lambda = [this, &just_swapped_files](const char* dir_name) {
    if (just_swapped_files) {
      // We should fail the first call immediately after swapping files.
      just_swapped_files = false;
      return false;
    }
    return filesystem()->CreateDirectoryRecursively(dir_name);
  };
  ON_CALL(*mock_filesystem, CreateDirectoryRecursively)
      .WillByDefault(create_dir_lambda);

  auto swap_lambda = [&just_swapped_files](const char* first_dir,
                                           const char* second_dir) {
    just_swapped_files = true;
    return false;
  };
  IcingSearchEngineOptions options = GetDefaultIcingOptions();
  ON_CALL(*mock_filesystem, SwapFiles(HasSubstr("document_dir_optimize_tmp"),
                                      HasSubstr("document_dir")))
      .WillByDefault(swap_lambda);
  TestIcingSearchEngine icing(options, std::move(mock_filesystem),
                              std::make_unique<IcingFilesystem>(),
                              std::make_unique<FakeClock>(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());

  // The mocks should cause an unrecoverable error during Optimize - returning
  // INTERNAL.
  ASSERT_THAT(icing.Optimize().status(), ProtoStatusIs(StatusProto::INTERNAL));

  // Ordinary operations should fail safely.
  SchemaProto simple_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("type0").AddProperty(
              PropertyConfigBuilder()
                  .SetName("prop0")
                  .SetDataType(TYPE_STRING)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  DocumentProto simple_doc = DocumentBuilder()
                                 .SetKey("namespace0", "uri0")
                                 .SetSchema("type0")
                                 .AddStringProperty("prop0", "foo")
                                 .Build();

  SearchSpecProto search_spec;
  search_spec.set_query("foo");
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  ResultSpecProto result_spec;
  ScoringSpecProto scoring_spec;
  scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP);

  EXPECT_THAT(icing.SetSchema(simple_schema).status(),
              ProtoStatusIs(StatusProto::FAILED_PRECONDITION));
  EXPECT_THAT(icing.Put(simple_doc).status(),
              ProtoStatusIs(StatusProto::FAILED_PRECONDITION));
  EXPECT_THAT(icing
                  .Get(simple_doc.namespace_(), simple_doc.uri(),
                       GetResultSpecProto::default_instance())
                  .status(),
              ProtoStatusIs(StatusProto::FAILED_PRECONDITION));
  EXPECT_THAT(icing.Search(search_spec, scoring_spec, result_spec).status(),
              ProtoStatusIs(StatusProto::FAILED_PRECONDITION));

  // Reset should get icing back to a safe (empty) and working state.
  EXPECT_THAT(icing.Reset().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(simple_schema).status(), ProtoIsOk());
  EXPECT_THAT(icing.Put(simple_doc).status(), ProtoIsOk());
  EXPECT_THAT(icing
                  .Get(simple_doc.namespace_(), simple_doc.uri(),
                       GetResultSpecProto::default_instance())
                  .status(),
              ProtoIsOk());
  EXPECT_THAT(icing.Search(search_spec, scoring_spec, result_spec).status(),
              ProtoIsOk());
}

TEST_F(IcingSearchEngineOptimizeTest, SetSchemaShouldWorkAfterOptimization) {
  // Creates 3 test schemas
  SchemaProto schema1 =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  SchemaProto schema2 = SchemaProto(schema1);
  *schema2.mutable_types(0)->add_properties() =
      PropertyConfigBuilder()
          .SetName("property2")
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
          .SetCardinality(CARDINALITY_OPTIONAL)
          .Build();

  SchemaProto schema3 = SchemaProto(schema2);
  *schema3.mutable_types(0)->add_properties() =
      PropertyConfigBuilder()
          .SetName("property3")
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
          .SetCardinality(CARDINALITY_OPTIONAL)
          .Build();

  {
    IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing.SetSchema(schema1).status(), ProtoIsOk());
    ASSERT_THAT(icing.Optimize().status(), ProtoIsOk());

    // Validates that SetSchema() works right after Optimize()
    EXPECT_THAT(icing.SetSchema(schema2).status(), ProtoIsOk());
  }  // Destroys IcingSearchEngine to make sure nothing is cached.

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.SetSchema(schema3).status(), ProtoIsOk());
}

TEST_F(IcingSearchEngineOptimizeTest, SearchShouldWorkAfterOptimization) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Message")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REQUIRED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("indexableInteger")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  DocumentProto document =
      DocumentBuilder()
          .SetKey("namespace", "uri")
          .SetSchema("Message")
          .AddStringProperty("body", "message body")
          .AddInt64Property("indexableInteger", 123)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  SearchSpecProto search_spec1;
  search_spec1.set_term_match_type(TermMatchType::PREFIX);
  search_spec1.set_query("m");

  SearchSpecProto search_spec2;
  search_spec2.set_query("indexableInteger == 123");
  search_spec2.set_search_type(
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY);
  search_spec2.add_enabled_features(std::string(kNumericSearchFeature));

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      document;

  {
    IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());
    ASSERT_THAT(icing.Optimize().status(), ProtoIsOk());

    // Validates that Search() works right after Optimize()
    // Term search
    SearchResultProto search_result_proto1 =
        icing.Search(search_spec1, GetDefaultScoringSpec(),
                     ResultSpecProto::default_instance());
    EXPECT_THAT(search_result_proto1, EqualsSearchResultIgnoreStatsAndScores(
                                          expected_search_result_proto));

    // Numeric (integer) search
    SearchResultProto search_result_google::protobuf =
        icing.Search(search_spec2, GetDefaultScoringSpec(),
                     ResultSpecProto::default_instance());
    EXPECT_THAT(search_result_google::protobuf, EqualsSearchResultIgnoreStatsAndScores(
                                          expected_search_result_proto));
  }  // Destroys IcingSearchEngine to make sure nothing is cached.

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());

  // Verify term search
  SearchResultProto search_result_proto1 =
      icing.Search(search_spec1, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto1, EqualsSearchResultIgnoreStatsAndScores(
                                        expected_search_result_proto));

  // Verify numeric (integer) search
  SearchResultProto search_result_google::protobuf =
      icing.Search(search_spec2, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_google::protobuf, EqualsSearchResultIgnoreStatsAndScores(
                                        expected_search_result_proto));
}

TEST_F(IcingSearchEngineOptimizeTest,
       JoinShouldWorkAfterOptimizationDeleteParent) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Person").AddProperty(
              PropertyConfigBuilder()
                  .SetName("name")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Message")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REQUIRED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("senderQualifiedId")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  DocumentProto person1 =
      DocumentBuilder()
          .SetKey("namespace", "person1")
          .SetSchema("Person")
          .AddStringProperty("name", "person one")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto person2 =
      DocumentBuilder()
          .SetKey("namespace", "person2")
          .SetSchema("Person")
          .AddStringProperty("name", "person two")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  DocumentProto message1 =
      DocumentBuilder()
          .SetKey("namespace", "message1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body one")
          .AddStringProperty("senderQualifiedId", "namespace#person1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto message2 =
      DocumentBuilder()
          .SetKey("namespace", "message2")
          .SetSchema("Message")
          .AddStringProperty("body", "message body two")
          .AddStringProperty("senderQualifiedId", "namespace#person1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto message3 =
      DocumentBuilder()
          .SetKey("namespace", "message3")
          .SetSchema("Message")
          .AddStringProperty("body", "message body three")
          .AddStringProperty("senderQualifiedId", "namespace#person2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  // Prepare join search spec to join a query for `name:person` with a child
  // query for `body:message` based on the child's `senderQualifiedId` field.
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_query("name:person");
  JoinSpecProto* join_spec = search_spec.mutable_join_spec();
  join_spec->set_parent_property_expression(
      std::string(JoinProcessor::kQualifiedIdExpr));
  join_spec->set_child_property_expression("senderQualifiedId");
  join_spec->set_aggregation_scoring_strategy(
      JoinSpecProto::AggregationScoringStrategy::COUNT);
  JoinSpecProto::NestedSpecProto* nested_spec =
      join_spec->mutable_nested_spec();
  SearchSpecProto* nested_search_spec = nested_spec->mutable_search_spec();
  nested_search_spec->set_term_match_type(TermMatchType::EXACT_ONLY);
  nested_search_spec->set_query("body:message");
  *nested_spec->mutable_scoring_spec() = GetDefaultScoringSpec();
  *nested_spec->mutable_result_spec() = ResultSpecProto::default_instance();

  ResultSpecProto result_spec = ResultSpecProto::default_instance();
  result_spec.set_max_joined_children_per_parent_to_return(
      std::numeric_limits<int32_t>::max());

  // Person1 is going to be deleted below. Only person2 which is joined with
  // message3 should match the query.
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  SearchResultProto::ResultProto* result_proto =
      expected_search_result_proto.mutable_results()->Add();
  *result_proto->mutable_document() = person2;
  *result_proto->mutable_joined_results()->Add()->mutable_document() = message3;

  {
    IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(person1).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(person2).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(message1).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(message2).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(message3).status(), ProtoIsOk());
    // Delete parent document: person1
    ASSERT_THAT(icing.Delete("namespace", "person1").status(), ProtoIsOk());
    ASSERT_THAT(icing.Optimize().status(), ProtoIsOk());

    // Validates that join search query works right after Optimize()
    SearchResultProto search_result_proto =
        icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
    EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                         expected_search_result_proto));
  }  // Destroys IcingSearchEngine to make sure nothing is cached.

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());

  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_F(IcingSearchEngineOptimizeTest,
       JoinShouldWorkAfterOptimizationDeleteChild) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Person").AddProperty(
              PropertyConfigBuilder()
                  .SetName("name")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Message")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REQUIRED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("senderQualifiedId")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  DocumentProto person1 =
      DocumentBuilder()
          .SetKey("namespace", "person1")
          .SetSchema("Person")
          .AddStringProperty("name", "person one")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto person2 =
      DocumentBuilder()
          .SetKey("namespace", "person2")
          .SetSchema("Person")
          .AddStringProperty("name", "person two")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  DocumentProto message1 =
      DocumentBuilder()
          .SetKey("namespace", "message1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body one")
          .AddStringProperty("senderQualifiedId", "namespace#person1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto message2 =
      DocumentBuilder()
          .SetKey("namespace", "message2")
          .SetSchema("Message")
          .AddStringProperty("body", "message body two")
          .AddStringProperty("senderQualifiedId", "namespace#person1")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto message3 =
      DocumentBuilder()
          .SetKey("namespace", "message3")
          .SetSchema("Message")
          .AddStringProperty("body", "message body three")
          .AddStringProperty("senderQualifiedId", "namespace#person2")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  // Prepare join search spec to join a query for `name:person` with a child
  // query for `body:message` based on the child's `senderQualifiedId` field.
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec.set_query("name:person");
  JoinSpecProto* join_spec = search_spec.mutable_join_spec();
  join_spec->set_parent_property_expression(
      std::string(JoinProcessor::kQualifiedIdExpr));
  join_spec->set_child_property_expression("senderQualifiedId");
  join_spec->set_aggregation_scoring_strategy(
      JoinSpecProto::AggregationScoringStrategy::COUNT);
  JoinSpecProto::NestedSpecProto* nested_spec =
      join_spec->mutable_nested_spec();
  SearchSpecProto* nested_search_spec = nested_spec->mutable_search_spec();
  nested_search_spec->set_term_match_type(TermMatchType::EXACT_ONLY);
  nested_search_spec->set_query("body:message");
  *nested_spec->mutable_scoring_spec() = GetDefaultScoringSpec();
  *nested_spec->mutable_result_spec() = ResultSpecProto::default_instance();

  ResultSpecProto result_spec = ResultSpecProto::default_instance();
  result_spec.set_max_joined_children_per_parent_to_return(
      std::numeric_limits<int32_t>::max());

  // Message1 and message3 are going to be deleted below. Both person1 and
  // person2 should be included even though person2 has no child (since we're
  // doing left join).
  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  SearchResultProto::ResultProto* result_proto1 =
      expected_search_result_proto.mutable_results()->Add();
  *result_proto1->mutable_document() = person1;
  *result_proto1->mutable_joined_results()->Add()->mutable_document() =
      message2;
  SearchResultProto::ResultProto* result_google::protobuf =
      expected_search_result_proto.mutable_results()->Add();
  *result_google::protobuf->mutable_document() = person2;

  {
    IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(person1).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(person2).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(message1).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(message2).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(message3).status(), ProtoIsOk());
    // Delete child documents: message1 and message3
    ASSERT_THAT(icing.Delete("namespace", "message1").status(), ProtoIsOk());
    ASSERT_THAT(icing.Delete("namespace", "message3").status(), ProtoIsOk());
    ASSERT_THAT(icing.Optimize().status(), ProtoIsOk());

    // Validates that join search query works right after Optimize()
    SearchResultProto search_result_proto =
        icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
    EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                         expected_search_result_proto));
  }  // Destroys IcingSearchEngine to make sure nothing is cached.

  IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
  EXPECT_THAT(icing.Initialize().status(), ProtoIsOk());

  SearchResultProto search_result_proto =
      icing.Search(search_spec, GetDefaultScoringSpec(), result_spec);
  EXPECT_THAT(search_result_proto, EqualsSearchResultIgnoreStatsAndScores(
                                       expected_search_result_proto));
}

TEST_F(IcingSearchEngineOptimizeTest,
       IcingShouldWorkFineIfOptimizationIsAborted) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Person").AddProperty(
              PropertyConfigBuilder()
                  .SetName("name")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Message")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REQUIRED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("indexableInteger")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_REQUIRED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("senderQualifiedId")
                                        .SetDataTypeJoinableString(
                                            JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  DocumentProto person =
      DocumentBuilder()
          .SetKey("namespace", "person")
          .SetSchema("Person")
          .AddStringProperty("name", "person")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  DocumentProto message1 =
      DocumentBuilder()
          .SetKey("namespace", "message1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body one")
          .AddInt64Property("indexableInteger", 123)
          .AddStringProperty("senderQualifiedId", "namespace#person")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  {
    // Initializes a normal icing to create files needed
    IcingSearchEngine icing(GetDefaultIcingOptions(), GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(person).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(message1).status(), ProtoIsOk());
  }

  // Creates a mock filesystem in which DeleteDirectoryRecursively() always
  // fails. This will fail IcingSearchEngine::OptimizeDocumentStore() and makes
  // it return ABORTED_ERROR.
  auto mock_filesystem = std::make_unique<MockFilesystem>();
  ON_CALL(*mock_filesystem,
          DeleteDirectoryRecursively(HasSubstr("_optimize_tmp")))
      .WillByDefault(Return(false));

  TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                              std::move(mock_filesystem),
                              std::make_unique<IcingFilesystem>(),
                              std::make_unique<FakeClock>(), GetTestJniCache());
  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  EXPECT_THAT(icing.Optimize().status(), ProtoStatusIs(StatusProto::ABORTED));

  // Now optimization is aborted, we verify that document-related functions
  // still work as expected.

  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_get_result_proto.mutable_document() = message1;
  EXPECT_THAT(icing.Get("namespace", "message1",
                        GetResultSpecProto::default_instance()),
              EqualsProto(expected_get_result_proto));

  DocumentProto message2 =
      DocumentBuilder()
          .SetKey("namespace", "message2")
          .SetSchema("Message")
          .AddStringProperty("body", "message body two")
          .AddInt64Property("indexableInteger", 123)
          .AddStringProperty("senderQualifiedId", "namespace#person")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  EXPECT_THAT(icing.Put(message2).status(), ProtoIsOk());

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      message2;
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      message1;

  // Verify term search
  SearchSpecProto search_spec1;
  search_spec1.set_query("body:m");
  search_spec1.set_term_match_type(TermMatchType::PREFIX);

  SearchResultProto search_result_proto1 =
      icing.Search(search_spec1, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto1, EqualsSearchResultIgnoreStatsAndScores(
                                        expected_search_result_proto));

  // Verify numeric (integer) search
  SearchSpecProto search_spec2;
  search_spec2.set_query("indexableInteger == 123");
  search_spec2.set_search_type(
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY);
  search_spec2.add_enabled_features(std::string(kNumericSearchFeature));

  SearchResultProto search_result_google::protobuf =
      icing.Search(search_spec2, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_google::protobuf, EqualsSearchResultIgnoreStatsAndScores(
                                        expected_search_result_proto));

  // Verify join search: join a query for `name:person` with a child query for
  // `body:message` based on the child's `senderQualifiedId` field.
  SearchSpecProto search_spec3;
  search_spec3.set_term_match_type(TermMatchType::EXACT_ONLY);
  search_spec3.set_query("name:person");
  JoinSpecProto* join_spec = search_spec3.mutable_join_spec();
  join_spec->set_parent_property_expression(
      std::string(JoinProcessor::kQualifiedIdExpr));
  join_spec->set_child_property_expression("senderQualifiedId");
  join_spec->set_aggregation_scoring_strategy(
      JoinSpecProto::AggregationScoringStrategy::COUNT);
  JoinSpecProto::NestedSpecProto* nested_spec =
      join_spec->mutable_nested_spec();
  SearchSpecProto* nested_search_spec = nested_spec->mutable_search_spec();
  nested_search_spec->set_term_match_type(TermMatchType::EXACT_ONLY);
  nested_search_spec->set_query("body:message");
  *nested_spec->mutable_scoring_spec() = GetDefaultScoringSpec();
  *nested_spec->mutable_result_spec() = ResultSpecProto::default_instance();

  ResultSpecProto result_spec3 = ResultSpecProto::default_instance();
  result_spec3.set_max_joined_children_per_parent_to_return(
      std::numeric_limits<int32_t>::max());

  SearchResultProto expected_join_search_result_proto;
  expected_join_search_result_proto.mutable_status()->set_code(StatusProto::OK);
  SearchResultProto::ResultProto* result_proto =
      expected_join_search_result_proto.mutable_results()->Add();
  *result_proto->mutable_document() = person;
  *result_proto->mutable_joined_results()->Add()->mutable_document() = message2;
  *result_proto->mutable_joined_results()->Add()->mutable_document() = message1;

  SearchResultProto search_result_proto3 =
      icing.Search(search_spec3, GetDefaultScoringSpec(), result_spec3);
  EXPECT_THAT(search_result_proto3, EqualsSearchResultIgnoreStatsAndScores(
                                        expected_join_search_result_proto));
}

TEST_F(IcingSearchEngineOptimizeTest,
       OptimizationShouldRecoverIfFileDirectoriesAreMissing) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Message")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REQUIRED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("indexableInteger")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  DocumentProto document =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body")
          .AddInt64Property("indexableInteger", 123)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  // Creates a mock filesystem in which SwapFiles() always fails and deletes the
  // directories. This will fail IcingSearchEngine::OptimizeDocumentStore().
  auto mock_filesystem = std::make_unique<MockFilesystem>();
  ON_CALL(*mock_filesystem, SwapFiles(HasSubstr("document_dir_optimize_tmp"),
                                      HasSubstr("document_dir")))
      .WillByDefault([this](const char* one, const char* two) {
        filesystem()->DeleteDirectoryRecursively(one);
        filesystem()->DeleteDirectoryRecursively(two);
        return false;
      });

  TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                              std::move(mock_filesystem),
                              std::make_unique<IcingFilesystem>(),
                              std::make_unique<FakeClock>(), GetTestJniCache());

  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  // Optimize() fails due to filesystem error
  OptimizeResultProto result = icing.Optimize();
  EXPECT_THAT(result.status(), ProtoStatusIs(StatusProto::WARNING_DATA_LOSS));
  // Should rebuild the index for data loss.
  EXPECT_THAT(result.optimize_stats().index_restoration_mode(),
              Eq(OptimizeStatsProto::FULL_INDEX_REBUILD));

  // Document is not found because original file directory is missing
  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::NOT_FOUND);
  expected_get_result_proto.mutable_status()->set_message(
      "Document (namespace, uri) not found.");
  EXPECT_THAT(
      icing.Get("namespace", "uri", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  DocumentProto new_document =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "new body")
          .AddInt64Property("indexableInteger", 456)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  EXPECT_THAT(icing.Put(new_document).status(), ProtoIsOk());

  SearchSpecProto search_spec1;
  search_spec1.set_query("m");
  search_spec1.set_term_match_type(TermMatchType::PREFIX);

  SearchSpecProto search_spec2;
  search_spec2.set_query("indexableInteger == 123");
  search_spec2.set_search_type(
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY);
  search_spec2.add_enabled_features(std::string(kNumericSearchFeature));

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);

  // Searching old content returns nothing because original file directory is
  // missing
  // Term search
  SearchResultProto search_result_proto1 =
      icing.Search(search_spec1, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto1, EqualsSearchResultIgnoreStatsAndScores(
                                        expected_search_result_proto));

  // Numeric (integer) search
  SearchResultProto search_result_google::protobuf =
      icing.Search(search_spec2, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_google::protobuf, EqualsSearchResultIgnoreStatsAndScores(
                                        expected_search_result_proto));

  // Searching new content returns the new document
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      new_document;
  // Term search
  search_spec1.set_query("n");
  search_result_proto1 = icing.Search(search_spec1, GetDefaultScoringSpec(),
                                      ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto1, EqualsSearchResultIgnoreStatsAndScores(
                                        expected_search_result_proto));

  // Numeric (integer) search
  search_spec2.set_query("indexableInteger == 456");
  search_result_google::protobuf = icing.Search(search_spec2, GetDefaultScoringSpec(),
                                      ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_google::protobuf, EqualsSearchResultIgnoreStatsAndScores(
                                        expected_search_result_proto));
}

TEST_F(IcingSearchEngineOptimizeTest,
       OptimizationShouldRecoverIfDataFilesAreMissing) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Message")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REQUIRED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("indexableInteger")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  DocumentProto document =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body")
          .AddInt64Property("indexableInteger", 123)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  // Creates a mock filesystem in which SwapFiles() always fails and empties the
  // directories. This will fail IcingSearchEngine::OptimizeDocumentStore().
  auto mock_filesystem = std::make_unique<MockFilesystem>();
  ON_CALL(*mock_filesystem, SwapFiles(HasSubstr("document_dir_optimize_tmp"),
                                      HasSubstr("document_dir")))
      .WillByDefault([this](const char* one, const char* two) {
        filesystem()->DeleteDirectoryRecursively(one);
        filesystem()->CreateDirectoryRecursively(one);
        filesystem()->DeleteDirectoryRecursively(two);
        filesystem()->CreateDirectoryRecursively(two);
        return false;
      });

  TestIcingSearchEngine icing(GetDefaultIcingOptions(),
                              std::move(mock_filesystem),
                              std::make_unique<IcingFilesystem>(),
                              std::make_unique<FakeClock>(), GetTestJniCache());

  ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
  ASSERT_THAT(icing.Put(document).status(), ProtoIsOk());

  // Optimize() fails due to filesystem error
  OptimizeResultProto result = icing.Optimize();
  EXPECT_THAT(result.status(), ProtoStatusIs(StatusProto::WARNING_DATA_LOSS));
  // Should rebuild the index for data loss.
  EXPECT_THAT(result.optimize_stats().index_restoration_mode(),
              Eq(OptimizeStatsProto::FULL_INDEX_REBUILD));

  // Document is not found because original files are missing
  GetResultProto expected_get_result_proto;
  expected_get_result_proto.mutable_status()->set_code(StatusProto::NOT_FOUND);
  expected_get_result_proto.mutable_status()->set_message(
      "Document (namespace, uri) not found.");
  EXPECT_THAT(
      icing.Get("namespace", "uri", GetResultSpecProto::default_instance()),
      EqualsProto(expected_get_result_proto));

  DocumentProto new_document =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "new body")
          .AddInt64Property("indexableInteger", 456)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  EXPECT_THAT(icing.Put(new_document).status(), ProtoIsOk());

  SearchSpecProto search_spec1;
  search_spec1.set_query("m");
  search_spec1.set_term_match_type(TermMatchType::PREFIX);

  SearchSpecProto search_spec2;
  search_spec2.set_query("indexableInteger == 123");
  search_spec2.set_search_type(
      SearchSpecProto::SearchType::EXPERIMENTAL_ICING_ADVANCED_QUERY);
  search_spec2.add_enabled_features(std::string(kNumericSearchFeature));

  SearchResultProto expected_search_result_proto;
  expected_search_result_proto.mutable_status()->set_code(StatusProto::OK);

  // Searching old content returns nothing because original files are missing
  // Term search
  SearchResultProto search_result_proto1 =
      icing.Search(search_spec1, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto1, EqualsSearchResultIgnoreStatsAndScores(
                                        expected_search_result_proto));

  // Numeric (integer) search
  SearchResultProto search_result_google::protobuf =
      icing.Search(search_spec2, GetDefaultScoringSpec(),
                   ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_google::protobuf, EqualsSearchResultIgnoreStatsAndScores(
                                        expected_search_result_proto));

  // Searching new content returns the new document
  *expected_search_result_proto.mutable_results()->Add()->mutable_document() =
      new_document;
  // Term search
  search_spec1.set_query("n");
  search_result_proto1 = icing.Search(search_spec1, GetDefaultScoringSpec(),
                                      ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_proto1, EqualsSearchResultIgnoreStatsAndScores(
                                        expected_search_result_proto));

  // Numeric (integer) search
  search_spec2.set_query("indexableInteger == 456");
  search_result_google::protobuf = icing.Search(search_spec2, GetDefaultScoringSpec(),
                                      ResultSpecProto::default_instance());
  EXPECT_THAT(search_result_google::protobuf, EqualsSearchResultIgnoreStatsAndScores(
                                        expected_search_result_proto));
}

TEST_F(IcingSearchEngineOptimizeTest, OptimizeThresholdTest) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Message")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REQUIRED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("indexableInteger")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body one")
          .AddInt64Property("indexableInteger", 1)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace", "uri2")
                                .SetSchema("Message")
                                .AddStringProperty("body", "message body two")
                                .AddInt64Property("indexableInteger", 2)
                                .SetCreationTimestampMs(9000)
                                .SetTtlMs(500)
                                .Build();
  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace", "uri3")
          .SetSchema("Message")
          .AddStringProperty("body", "message body three")
          .AddInt64Property("indexableInteger", 3)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  auto fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetTimerElapsedMilliseconds(5);
  fake_clock->SetSystemTimeMilliseconds(10000);
  IcingSearchEngineOptions options = GetDefaultIcingOptions();
  // Set the threshold to 0.9 to test that the threshold works.
  options.set_optimize_rebuild_index_threshold(0.9);
  auto icing = std::make_unique<TestIcingSearchEngine>(
      options, std::make_unique<Filesystem>(),
      std::make_unique<IcingFilesystem>(), std::move(fake_clock),
      GetTestJniCache());
  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());

  // Add three documents.
  ASSERT_THAT(icing->Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing->Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing->Put(document3).status(), ProtoIsOk());

  // Delete the first document.
  ASSERT_THAT(icing->Delete(document1.namespace_(), document1.uri()).status(),
              ProtoIsOk());
  ASSERT_THAT(icing->PersistToDisk(PersistType::FULL).status(), ProtoIsOk());

  OptimizeStatsProto expected;
  expected.set_latency_ms(5);
  expected.set_document_store_optimize_latency_ms(5);
  expected.set_index_restoration_latency_ms(5);
  expected.set_num_original_documents(3);
  expected.set_num_deleted_documents(1);
  expected.set_num_expired_documents(1);
  expected.set_num_original_namespaces(1);
  expected.set_num_deleted_namespaces(0);
  expected.set_index_restoration_mode(OptimizeStatsProto::INDEX_TRANSLATION);

  // Run Optimize
  OptimizeResultProto result = icing->Optimize();
  // Depending on how many blocks the documents end up spread across, it's
  // possible that Optimize can remove documents without shrinking storage. The
  // first Optimize call will also write the OptimizeStatusProto for the first
  // time which will take up 1 block. So make sure that before_size is no less
  // than after_size - 1 block.
  uint32_t page_size = getpagesize();
  EXPECT_THAT(result.optimize_stats().storage_size_before(),
              Ge(result.optimize_stats().storage_size_after() - page_size));
  result.mutable_optimize_stats()->clear_storage_size_before();
  result.mutable_optimize_stats()->clear_storage_size_after();
  EXPECT_THAT(result.optimize_stats(), EqualsProto(expected));

  fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetTimerElapsedMilliseconds(5);
  fake_clock->SetSystemTimeMilliseconds(20000);
  icing = std::make_unique<TestIcingSearchEngine>(
      options, std::make_unique<Filesystem>(),
      std::make_unique<IcingFilesystem>(), std::move(fake_clock),
      GetTestJniCache());
  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());

  expected = OptimizeStatsProto();
  expected.set_latency_ms(5);
  expected.set_document_store_optimize_latency_ms(5);
  expected.set_index_restoration_latency_ms(5);
  expected.set_num_original_documents(1);
  expected.set_num_deleted_documents(0);
  expected.set_num_expired_documents(0);
  expected.set_num_original_namespaces(1);
  expected.set_num_deleted_namespaces(0);
  expected.set_time_since_last_optimize_ms(10000);
  expected.set_index_restoration_mode(OptimizeStatsProto::INDEX_TRANSLATION);

  // Run Optimize
  result = icing->Optimize();
  EXPECT_THAT(result.optimize_stats().storage_size_before(),
              Eq(result.optimize_stats().storage_size_after()));
  result.mutable_optimize_stats()->clear_storage_size_before();
  result.mutable_optimize_stats()->clear_storage_size_after();
  EXPECT_THAT(result.optimize_stats(), EqualsProto(expected));

  // Delete the last document.
  ASSERT_THAT(icing->Delete(document3.namespace_(), document3.uri()).status(),
              ProtoIsOk());

  expected = OptimizeStatsProto();
  expected.set_latency_ms(5);
  expected.set_document_store_optimize_latency_ms(5);
  expected.set_index_restoration_latency_ms(5);
  expected.set_num_original_documents(1);
  expected.set_num_deleted_documents(1);
  expected.set_num_expired_documents(0);
  expected.set_num_original_namespaces(1);
  expected.set_num_deleted_namespaces(1);
  expected.set_time_since_last_optimize_ms(0);
  // Should rebuild the index since all documents are removed.
  expected.set_index_restoration_mode(OptimizeStatsProto::FULL_INDEX_REBUILD);

  // Run Optimize
  result = icing->Optimize();
  EXPECT_THAT(result.optimize_stats().storage_size_before(),
              Ge(result.optimize_stats().storage_size_after()));
  result.mutable_optimize_stats()->clear_storage_size_before();
  result.mutable_optimize_stats()->clear_storage_size_after();
  EXPECT_THAT(result.optimize_stats(), EqualsProto(expected));
}

TEST_F(IcingSearchEngineOptimizeTest, OptimizeStatsProtoTest) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("Message")
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("body")
                                        .SetDataTypeString(TERM_MATCH_PREFIX,
                                                           TOKENIZER_PLAIN)
                                        .SetCardinality(CARDINALITY_REQUIRED))
                       .AddProperty(PropertyConfigBuilder()
                                        .SetName("indexableInteger")
                                        .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                        .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();

  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body one")
          .AddInt64Property("indexableInteger", 1)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 = DocumentBuilder()
                                .SetKey("namespace", "uri2")
                                .SetSchema("Message")
                                .AddStringProperty("body", "message body two")
                                .AddInt64Property("indexableInteger", 2)
                                .SetCreationTimestampMs(9000)
                                .SetTtlMs(500)
                                .Build();
  DocumentProto document3 =
      DocumentBuilder()
          .SetKey("namespace", "uri3")
          .SetSchema("Message")
          .AddStringProperty("body", "message body three")
          .AddInt64Property("indexableInteger", 3)
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();

  auto fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetTimerElapsedMilliseconds(5);
  fake_clock->SetSystemTimeMilliseconds(10000);
  // Use the default Icing options, so that a change to the default value will
  // require updating this test.
  auto icing = std::make_unique<TestIcingSearchEngine>(
      GetDefaultIcingOptions(), std::make_unique<Filesystem>(),
      std::make_unique<IcingFilesystem>(), std::move(fake_clock),
      GetTestJniCache());
  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());
  ASSERT_THAT(icing->SetSchema(schema).status(), ProtoIsOk());

  // Add three documents.
  ASSERT_THAT(icing->Put(document1).status(), ProtoIsOk());
  ASSERT_THAT(icing->Put(document2).status(), ProtoIsOk());
  ASSERT_THAT(icing->Put(document3).status(), ProtoIsOk());

  // Delete the first document.
  ASSERT_THAT(icing->Delete(document1.namespace_(), document1.uri()).status(),
              ProtoIsOk());
  ASSERT_THAT(icing->PersistToDisk(PersistType::FULL).status(), ProtoIsOk());

  OptimizeStatsProto expected;
  expected.set_latency_ms(5);
  expected.set_document_store_optimize_latency_ms(5);
  expected.set_index_restoration_latency_ms(5);
  expected.set_num_original_documents(3);
  expected.set_num_deleted_documents(1);
  expected.set_num_expired_documents(1);
  expected.set_num_original_namespaces(1);
  expected.set_num_deleted_namespaces(0);
  expected.set_index_restoration_mode(OptimizeStatsProto::FULL_INDEX_REBUILD);

  // Run Optimize
  OptimizeResultProto result = icing->Optimize();
  // Depending on how many blocks the documents end up spread across, it's
  // possible that Optimize can remove documents without shrinking storage. The
  // first Optimize call will also write the OptimizeStatusProto for the first
  // time which will take up 1 block. So make sure that before_size is no less
  // than after_size - 1 block.
  uint32_t page_size = getpagesize();
  EXPECT_THAT(result.optimize_stats().storage_size_before(),
              Ge(result.optimize_stats().storage_size_after() - page_size));
  result.mutable_optimize_stats()->clear_storage_size_before();
  result.mutable_optimize_stats()->clear_storage_size_after();
  EXPECT_THAT(result.optimize_stats(), EqualsProto(expected));

  fake_clock = std::make_unique<FakeClock>();
  fake_clock->SetTimerElapsedMilliseconds(5);
  fake_clock->SetSystemTimeMilliseconds(20000);
  // Use the default Icing options, so that a change to the default value will
  // require updating this test.
  icing = std::make_unique<TestIcingSearchEngine>(
      GetDefaultIcingOptions(), std::make_unique<Filesystem>(),
      std::make_unique<IcingFilesystem>(), std::move(fake_clock),
      GetTestJniCache());
  ASSERT_THAT(icing->Initialize().status(), ProtoIsOk());

  expected = OptimizeStatsProto();
  expected.set_latency_ms(5);
  expected.set_document_store_optimize_latency_ms(5);
  expected.set_index_restoration_latency_ms(5);
  expected.set_num_original_documents(1);
  expected.set_num_deleted_documents(0);
  expected.set_num_expired_documents(0);
  expected.set_num_original_namespaces(1);
  expected.set_num_deleted_namespaces(0);
  expected.set_time_since_last_optimize_ms(10000);
  expected.set_index_restoration_mode(OptimizeStatsProto::FULL_INDEX_REBUILD);

  // Run Optimize
  result = icing->Optimize();
  EXPECT_THAT(result.optimize_stats().storage_size_before(),
              Eq(result.optimize_stats().storage_size_after()));
  result.mutable_optimize_stats()->clear_storage_size_before();
  result.mutable_optimize_stats()->clear_storage_size_after();
  EXPECT_THAT(result.optimize_stats(), EqualsProto(expected));

  // Delete the last document.
  ASSERT_THAT(icing->Delete(document3.namespace_(), document3.uri()).status(),
              ProtoIsOk());

  expected = OptimizeStatsProto();
  expected.set_latency_ms(5);
  expected.set_document_store_optimize_latency_ms(5);
  expected.set_index_restoration_latency_ms(5);
  expected.set_num_original_documents(1);
  expected.set_num_deleted_documents(1);
  expected.set_num_expired_documents(0);
  expected.set_num_original_namespaces(1);
  expected.set_num_deleted_namespaces(1);
  expected.set_time_since_last_optimize_ms(0);
  expected.set_index_restoration_mode(OptimizeStatsProto::FULL_INDEX_REBUILD);

  // Run Optimize
  result = icing->Optimize();
  EXPECT_THAT(result.optimize_stats().storage_size_before(),
              Ge(result.optimize_stats().storage_size_after()));
  result.mutable_optimize_stats()->clear_storage_size_before();
  result.mutable_optimize_stats()->clear_storage_size_after();
  EXPECT_THAT(result.optimize_stats(), EqualsProto(expected));
}

TEST_F(IcingSearchEngineOptimizeTest,
       OptimizationRewritesDocsWithNewCompressionLevel) {
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("Message").AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED)))
          .Build();
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetSchema("Message")
          .AddStringProperty("body", "message body one")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetSchema("Message")
          .AddStringProperty("body", "message body two")
          .SetCreationTimestampMs(kDefaultCreationTimestampMs)
          .Build();
  IcingSearchEngineOptions icing_options = GetDefaultIcingOptions();
  icing_options.set_compression_level(3);
  int64_t document_log_size_compression_3;
  int64_t document_log_size_after_opti_no_compression;
  int64_t document_log_size_after_opti_compression_3;
  const std::string document_log_path =
      icing_options.base_dir() + "/document_dir/" +
      DocumentLogCreator::GetDocumentLogFilename();
  {
    IcingSearchEngine icing(icing_options, GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    ASSERT_THAT(icing.SetSchema(schema).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(document1).status(), ProtoIsOk());
    ASSERT_THAT(icing.Put(document2).status(), ProtoIsOk());
    ASSERT_THAT(icing.PersistToDisk(PersistType::FULL).status(), ProtoIsOk());
    document_log_size_compression_3 =
        filesystem()->GetFileSize(document_log_path.c_str());
  }  // Destroys IcingSearchEngine to make sure nothing is cached.

  // Turn off compression
  icing_options.set_compression_level(0);

  {
    IcingSearchEngine icing(icing_options, GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    // Document log size is the same even after reopening with a different
    // compression level
    ASSERT_EQ(document_log_size_compression_3,
              filesystem()->GetFileSize(document_log_path.c_str()));
    ASSERT_THAT(icing.Optimize().status(), ProtoIsOk());
    document_log_size_after_opti_no_compression =
        filesystem()->GetFileSize(document_log_path.c_str());
    // Document log size is larger after optimizing since optimizing rewrites
    // with the new compression level which is 0 or none
    ASSERT_GT(document_log_size_after_opti_no_compression,
              document_log_size_compression_3);
  }

  // Restore the original compression level
  icing_options.set_compression_level(3);

  {
    IcingSearchEngine icing(icing_options, GetTestJniCache());
    ASSERT_THAT(icing.Initialize().status(), ProtoIsOk());
    // Document log size is the same even after reopening with a different
    // compression level
    ASSERT_EQ(document_log_size_after_opti_no_compression,
              filesystem()->GetFileSize(document_log_path.c_str()));
    ASSERT_THAT(icing.Optimize().status(), ProtoIsOk());
    document_log_size_after_opti_compression_3 =
        filesystem()->GetFileSize(document_log_path.c_str());
    // Document log size should be the same as it was originally
    ASSERT_EQ(document_log_size_after_opti_compression_3,
              document_log_size_compression_3);
  }
}

}  // namespace
}  // namespace lib
}  // namespace icing
