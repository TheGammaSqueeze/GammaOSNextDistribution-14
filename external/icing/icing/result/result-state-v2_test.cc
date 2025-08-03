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

#include "icing/result/result-state-v2.h"

#include <atomic>
#include <cstdint>
#include <limits>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/absl_ports/mutex.h"
#include "icing/file/filesystem.h"
#include "icing/file/portable-file-backed-proto-log.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/document_wrapper.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/scoring/priority-queue-scored-document-hits-ranker.h"
#include "icing/scoring/scored-document-hit.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"
#include "icing/util/clock.h"

namespace icing {
namespace lib {
namespace {

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::Pair;
using ::testing::UnorderedElementsAre;

ResultSpecProto CreateResultSpec(
    int num_per_page, ResultSpecProto::ResultGroupingType result_group_type) {
  ResultSpecProto result_spec;
  result_spec.set_result_group_type(result_group_type);
  result_spec.set_num_per_page(num_per_page);
  return result_spec;
}

class ResultStateV2Test : public ::testing::Test {
 protected:
  void SetUp() override {
    schema_store_base_dir_ = GetTestTempDir() + "/schema_store";
    filesystem_.CreateDirectoryRecursively(schema_store_base_dir_.c_str());
    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, schema_store_base_dir_, &clock_));
    SchemaProto schema;
    schema.add_types()->set_schema_type("Document");
    ICING_ASSERT_OK(schema_store_->SetSchema(
        std::move(schema), /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    doc_store_base_dir_ = GetTestTempDir() + "/document_store";
    filesystem_.CreateDirectoryRecursively(doc_store_base_dir_.c_str());
    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult result,
        DocumentStore::Create(
            &filesystem_, doc_store_base_dir_, &clock_, schema_store_.get(),
            /*force_recovery_and_revalidate_documents=*/false,
            /*namespace_id_fingerprint=*/false, /*pre_mapping_fbv=*/false,
            /*use_persistent_hash_map=*/false,
            PortableFileBackedProtoLog<
                DocumentWrapper>::kDeflateCompressionLevel,
            /*initialize_stats=*/nullptr));
    document_store_ = std::move(result.document_store);

    num_total_hits_ = 0;
  }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(doc_store_base_dir_.c_str());
    filesystem_.DeleteDirectoryRecursively(schema_store_base_dir_.c_str());
  }

  ScoredDocumentHit AddScoredDocument(DocumentId document_id) {
    DocumentProto document;
    document.set_namespace_("namespace");
    document.set_uri(std::to_string(document_id));
    document.set_schema("Document");
    document_store_->Put(std::move(document));
    return ScoredDocumentHit(document_id, kSectionIdMaskNone, /*score=*/1);
  }

  DocumentStore& document_store() { return *document_store_; }

  std::atomic<int>& num_total_hits() { return num_total_hits_; }

  const std::atomic<int>& num_total_hits() const { return num_total_hits_; }

 private:
  Filesystem filesystem_;
  std::string doc_store_base_dir_;
  std::string schema_store_base_dir_;
  Clock clock_;
  std::unique_ptr<DocumentStore> document_store_;
  std::unique_ptr<SchemaStore> schema_store_;
  std::atomic<int> num_total_hits_;
};

TEST_F(ResultStateV2Test, ShouldInitializeValuesAccordingToSpecs) {
  ResultSpecProto result_spec =
      CreateResultSpec(/*num_per_page=*/2, ResultSpecProto::NAMESPACE);
  result_spec.set_num_total_bytes_per_page_threshold(4096);
  result_spec.set_max_joined_children_per_parent_to_return(2048);

  // Adjustment info is not important in this test.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::vector<ScoredDocumentHit>(), /*is_descending=*/true),
      /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
      result_spec, document_store());

  absl_ports::shared_lock l(&result_state.mutex);

  EXPECT_THAT(result_state.num_returned, Eq(0));
  EXPECT_THAT(result_state.num_per_page(), Eq(result_spec.num_per_page()));
  EXPECT_THAT(result_state.num_total_bytes_per_page_threshold(),
              Eq(result_spec.num_total_bytes_per_page_threshold()));
  EXPECT_THAT(result_state.max_joined_children_per_parent_to_return(),
              Eq(result_spec.max_joined_children_per_parent_to_return()));
}

TEST_F(ResultStateV2Test, ShouldInitializeValuesAccordingToDefaultSpecs) {
  ResultSpecProto default_result_spec = ResultSpecProto::default_instance();
  ASSERT_THAT(default_result_spec.num_per_page(), Eq(10));
  ASSERT_THAT(default_result_spec.num_total_bytes_per_page_threshold(),
              Eq(std::numeric_limits<int32_t>::max()));

  // Adjustment info is not important in this test.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::vector<ScoredDocumentHit>(),
          /*is_descending=*/true),
      /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
      default_result_spec, document_store());

  absl_ports::shared_lock l(&result_state.mutex);

  EXPECT_THAT(result_state.num_returned, Eq(0));
  EXPECT_THAT(result_state.num_per_page(),
              Eq(default_result_spec.num_per_page()));
  EXPECT_THAT(result_state.num_total_bytes_per_page_threshold(),
              Eq(default_result_spec.num_total_bytes_per_page_threshold()));
  EXPECT_THAT(
      result_state.max_joined_children_per_parent_to_return(),
      Eq(default_result_spec.max_joined_children_per_parent_to_return()));
}

TEST_F(ResultStateV2Test,
       ShouldConstructNamespaceGroupIdMapAndGroupResultLimitsAccordingToSpecs) {
  // Create 3 docs under namespace1, namespace2, namespace3.
  DocumentProto document1;
  document1.set_namespace_("namespace1");
  document1.set_uri("uri/1");
  document1.set_schema("Document");
  ICING_ASSERT_OK(document_store().Put(std::move(document1)));

  DocumentProto document2;
  document2.set_namespace_("namespace2");
  document2.set_uri("uri/2");
  document2.set_schema("Document");
  ICING_ASSERT_OK(document_store().Put(std::move(document2)));

  DocumentProto document3;
  document3.set_namespace_("namespace3");
  document3.set_uri("uri/3");
  document3.set_schema("Document");
  ICING_ASSERT_OK(document_store().Put(std::move(document3)));

  // Create a ResultSpec that limits "namespace1" to 3 results and limits
  // "namespace2"+"namespace3" to a total of 2 results. Also add
  // "nonexistentNamespace1" and "nonexistentNamespace2" to test the behavior.
  ResultSpecProto::ResultGroupingType result_grouping_type =
      ResultSpecProto::NAMESPACE;
  ResultSpecProto result_spec =
      CreateResultSpec(/*num_per_page=*/5, result_grouping_type);
  ResultSpecProto::ResultGrouping* result_grouping =
      result_spec.add_result_groupings();
  ResultSpecProto::ResultGrouping::Entry* entry =
      result_grouping->add_entry_groupings();
  result_grouping->set_max_results(3);
  entry->set_namespace_("namespace1");
  result_grouping = result_spec.add_result_groupings();
  result_grouping->set_max_results(5);
  entry = result_grouping->add_entry_groupings();
  entry->set_namespace_("nonexistentNamespace2");
  result_grouping = result_spec.add_result_groupings();
  result_grouping->set_max_results(2);
  entry = result_grouping->add_entry_groupings();
  entry->set_namespace_("namespace2");
  entry = result_grouping->add_entry_groupings();
  entry->set_namespace_("namespace3");
  entry = result_grouping->add_entry_groupings();
  entry->set_namespace_("nonexistentNamespace1");

  // Get entry ids.
  ICING_ASSERT_OK_AND_ASSIGN(
      int32_t entry_id1, document_store().GetResultGroupingEntryId(
                             result_grouping_type, "namespace1", "Document"));
  ICING_ASSERT_OK_AND_ASSIGN(
      int32_t entry_id2, document_store().GetResultGroupingEntryId(
                             result_grouping_type, "namespace2", "Document"));
  ICING_ASSERT_OK_AND_ASSIGN(
      int32_t entry_id3, document_store().GetResultGroupingEntryId(
                             result_grouping_type, "namespace3", "Document"));

  // Adjustment info is not important in this test.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::vector<ScoredDocumentHit>(),
          /*is_descending=*/true),
      /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
      result_spec, document_store());

  absl_ports::shared_lock l(&result_state.mutex);

  // "namespace1" should be in group 0, and "namespace2"+"namespace3" should be
  // in group 2.
  // "nonexistentNamespace1" and "nonexistentNamespace2" shouldn't exist.
  EXPECT_THAT(result_state.entry_id_group_id_map(),
              UnorderedElementsAre(Pair(entry_id1, 0), Pair(entry_id2, 2),
                                   Pair(entry_id3, 2)));

  // group_result_limits should contain 3 (at index 0 for group 0), 5 (at index
  // 1 for group 1), 2 (at index 2 for group 2), even though there is no valid
  // namespace in group 1.
  EXPECT_THAT(result_state.group_result_limits, ElementsAre(3, 5, 2));
}

TEST_F(ResultStateV2Test, ShouldUpdateNumTotalHits) {
  std::vector<ScoredDocumentHit> scored_document_hits = {
      AddScoredDocument(/*document_id=*/1),
      AddScoredDocument(/*document_id=*/0),
      AddScoredDocument(/*document_id=*/2),
      AddScoredDocument(/*document_id=*/4),
      AddScoredDocument(/*document_id=*/3)};

  // Adjustment info is not important in this test.
  // Creates a ResultState with 5 ScoredDocumentHits.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits),
          /*is_descending=*/true),
      /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
      CreateResultSpec(/*num_per_page=*/5, ResultSpecProto::NAMESPACE),
      document_store());

  absl_ports::unique_lock l(&result_state.mutex);

  EXPECT_THAT(num_total_hits(), Eq(0));
  result_state.RegisterNumTotalHits(&num_total_hits());
  EXPECT_THAT(num_total_hits(), Eq(5));
  result_state.IncrementNumTotalHits(500);
  EXPECT_THAT(num_total_hits(), Eq(505));
}

TEST_F(ResultStateV2Test, ShouldUpdateNumTotalHitsWhenDestructed) {
  std::vector<ScoredDocumentHit> scored_document_hits1 = {
      AddScoredDocument(/*document_id=*/1),
      AddScoredDocument(/*document_id=*/0),
      AddScoredDocument(/*document_id=*/2),
      AddScoredDocument(/*document_id=*/4),
      AddScoredDocument(/*document_id=*/3)};

  std::vector<ScoredDocumentHit> scored_document_hits2 = {
      AddScoredDocument(/*document_id=*/6),
      AddScoredDocument(/*document_id=*/5)};

  num_total_hits() = 2;
  {
    // Adjustment info is not important in this test.
    // Creates a ResultState with 5 ScoredDocumentHits.
    ResultStateV2 result_state1(
        std::make_unique<
            PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
            std::move(scored_document_hits1),
            /*is_descending=*/true),
        /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
        CreateResultSpec(/*num_per_page=*/5, ResultSpecProto::NAMESPACE),
        document_store());

    absl_ports::unique_lock l(&result_state1.mutex);

    result_state1.RegisterNumTotalHits(&num_total_hits());
    ASSERT_THAT(num_total_hits(), Eq(7));

    {
      // Adjustment info is not important in this test.
      // Creates another ResultState with 2 ScoredDocumentHits.
      ResultStateV2 result_state2(
          std::make_unique<
              PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
              std::move(scored_document_hits2),
              /*is_descending=*/true),
          /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
          CreateResultSpec(/*num_per_page=*/5, ResultSpecProto::NAMESPACE),
          document_store());

      absl_ports::unique_lock l(&result_state2.mutex);

      result_state2.RegisterNumTotalHits(&num_total_hits());
      ASSERT_THAT(num_total_hits(), Eq(9));
    }

    EXPECT_THAT(num_total_hits(), Eq(7));
  }
  EXPECT_THAT(num_total_hits(), Eq(2));
}

TEST_F(ResultStateV2Test, ShouldNotUpdateNumTotalHitsWhenNotRegistered) {
  std::vector<ScoredDocumentHit> scored_document_hits = {
      AddScoredDocument(/*document_id=*/1),
      AddScoredDocument(/*document_id=*/0),
      AddScoredDocument(/*document_id=*/2),
      AddScoredDocument(/*document_id=*/4),
      AddScoredDocument(/*document_id=*/3)};

  // Creates a ResultState with 5 ScoredDocumentHits.
  {
    // Adjustment info is not important in this test.
    ResultStateV2 result_state(
        std::make_unique<
            PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
            std::move(scored_document_hits),
            /*is_descending=*/true),
        /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
        CreateResultSpec(/*num_per_page=*/5, ResultSpecProto::NAMESPACE),
        document_store());

    {
      absl_ports::unique_lock l(&result_state.mutex);

      EXPECT_THAT(num_total_hits(), Eq(0));
      result_state.IncrementNumTotalHits(500);
      EXPECT_THAT(num_total_hits(), Eq(0));
    }
  }
  EXPECT_THAT(num_total_hits(), Eq(0));
}

TEST_F(ResultStateV2Test, ShouldDecrementOriginalNumTotalHitsWhenReregister) {
  std::atomic<int> another_num_total_hits = 11;

  std::vector<ScoredDocumentHit> scored_document_hits = {
      AddScoredDocument(/*document_id=*/1),
      AddScoredDocument(/*document_id=*/0),
      AddScoredDocument(/*document_id=*/2),
      AddScoredDocument(/*document_id=*/4),
      AddScoredDocument(/*document_id=*/3)};

  // Adjustment info is not important in this test.
  // Creates a ResultState with 5 ScoredDocumentHits.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits),
          /*is_descending=*/true),
      /*parent_adjustment_info=*/nullptr, /*child_adjustment_info=*/nullptr,
      CreateResultSpec(/*num_per_page=*/5, ResultSpecProto::NAMESPACE),
      document_store());

  absl_ports::unique_lock l(&result_state.mutex);

  num_total_hits() = 7;
  result_state.RegisterNumTotalHits(&num_total_hits());
  EXPECT_THAT(num_total_hits(), Eq(12));

  result_state.RegisterNumTotalHits(&another_num_total_hits);
  // The original num_total_hits should be decremented after re-registration.
  EXPECT_THAT(num_total_hits(), Eq(7));
  // another_num_total_hits should be incremented after re-registration.
  EXPECT_THAT(another_num_total_hits, Eq(16));

  result_state.IncrementNumTotalHits(500);
  // The original num_total_hits should be unchanged.
  EXPECT_THAT(num_total_hits(), Eq(7));
  // Increment should be done on another_num_total_hits.
  EXPECT_THAT(another_num_total_hits, Eq(516));
}

}  // namespace
}  // namespace lib
}  // namespace icing
