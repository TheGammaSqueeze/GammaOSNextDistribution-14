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

#include <limits>
#include <memory>
#include <vector>

#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/portable/equals-proto.h"
#include "icing/portable/platform.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/result/page-result.h"
#include "icing/result/projection-tree.h"
#include "icing/result/result-adjustment-info.h"
#include "icing/result/result-retriever-v2.h"
#include "icing/result/result-state-v2.h"
#include "icing/schema-builder.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/scoring/priority-queue-scored-document-hits-ranker.h"
#include "icing/scoring/scored-document-hit.h"
#include "icing/store/document-id.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/transform/normalizer-factory.h"
#include "icing/transform/normalizer.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {

namespace {

using ::icing::lib::portable_equals_proto::EqualsProto;
using ::testing::SizeIs;

class ResultRetrieverV2ProjectionTest : public testing::Test {
 protected:
  ResultRetrieverV2ProjectionTest() : test_dir_(GetTestTempDir() + "/icing") {
    filesystem_.CreateDirectoryRecursively(test_dir_.c_str());
  }

  void SetUp() override {
    if (!IsCfStringTokenization() && !IsReverseJniTokenization()) {
      ICING_ASSERT_OK(
          // File generated via icu_data_file rule in //icing/BUILD.
          icu_data_file_helper::SetUpICUDataFile(
              GetTestFilePath("icing/icu.dat")));
    }
    language_segmenter_factory::SegmenterOptions options(ULOC_US);
    ICING_ASSERT_OK_AND_ASSIGN(
        language_segmenter_,
        language_segmenter_factory::Create(std::move(options)));

    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, test_dir_, &fake_clock_));
    ICING_ASSERT_OK_AND_ASSIGN(normalizer_, normalizer_factory::Create(
                                                /*max_term_byte_size=*/10000));

    SchemaProto schema =
        SchemaBuilder()
            .AddType(SchemaTypeConfigBuilder()
                         .SetType("Email")
                         .AddProperty(PropertyConfigBuilder()
                                          .SetName("name")
                                          .SetDataTypeString(TERM_MATCH_PREFIX,
                                                             TOKENIZER_PLAIN)
                                          .SetCardinality(CARDINALITY_OPTIONAL))
                         .AddProperty(PropertyConfigBuilder()
                                          .SetName("body")
                                          .SetDataTypeString(TERM_MATCH_EXACT,
                                                             TOKENIZER_PLAIN)
                                          .SetCardinality(CARDINALITY_OPTIONAL))
                         .AddProperty(
                             PropertyConfigBuilder()
                                 .SetName("sender")
                                 .SetDataTypeDocument(
                                     "Person", /*index_nested_properties=*/true)
                                 .SetCardinality(CARDINALITY_OPTIONAL)))
            .AddType(
                SchemaTypeConfigBuilder()
                    .SetType("Person")
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName("name")
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName("emailAddress")
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL)))
            .AddType(
                SchemaTypeConfigBuilder()
                    .SetType("Artist")
                    .AddParentType("Person")
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName("name")
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName("emailAddress")
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL)))
            .AddType(
                SchemaTypeConfigBuilder()
                    .SetType("Musician")
                    .AddParentType("Artist")
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName("name")
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName("emailAddress")
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL)))
            .AddType(
                SchemaTypeConfigBuilder()
                    .SetType("WithPhone")
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName("phoneNumber")
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName("phoneModel")
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL)))
            .AddType(SchemaTypeConfigBuilder()
                         .SetType("PersonWithPhone")
                         .AddParentType("Person")
                         .AddParentType("WithPhone")
                         .AddProperty(PropertyConfigBuilder()
                                          .SetName("name")
                                          .SetDataTypeString(TERM_MATCH_PREFIX,
                                                             TOKENIZER_PLAIN)
                                          .SetCardinality(CARDINALITY_OPTIONAL))
                         .AddProperty(PropertyConfigBuilder()
                                          .SetName("emailAddress")
                                          .SetDataTypeString(TERM_MATCH_PREFIX,
                                                             TOKENIZER_PLAIN)
                                          .SetCardinality(CARDINALITY_OPTIONAL))
                         .AddProperty(PropertyConfigBuilder()
                                          .SetName("phoneNumber")
                                          .SetDataTypeString(TERM_MATCH_PREFIX,
                                                             TOKENIZER_PLAIN)
                                          .SetCardinality(CARDINALITY_OPTIONAL))
                         .AddProperty(PropertyConfigBuilder()
                                          .SetName("phoneModel")
                                          .SetDataTypeString(TERM_MATCH_PREFIX,
                                                             TOKENIZER_PLAIN)
                                          .SetCardinality(CARDINALITY_OPTIONAL))
                         .Build())
            .Build();
    ASSERT_THAT(schema_store_->SetSchema(
                    schema, /*ignore_errors_and_delete_documents=*/false,
                    /*allow_circular_schema_definitions=*/false),
                IsOk());

    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult create_result,
        DocumentStore::Create(
            &filesystem_, test_dir_, &fake_clock_, schema_store_.get(),
            /*force_recovery_and_revalidate_documents=*/false,
            /*namespace_id_fingerprint=*/false, /*pre_mapping_fbv=*/false,
            /*use_persistent_hash_map=*/false,
            PortableFileBackedProtoLog<
                DocumentWrapper>::kDeflateCompressionLevel,
            /*initialize_stats=*/nullptr));
    document_store_ = std::move(create_result.document_store);
  }

  void TearDown() override {
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  SectionId GetSectionId(const std::string& type, const std::string& property) {
    auto type_id_or = schema_store_->GetSchemaTypeId(type);
    if (!type_id_or.ok()) {
      return kInvalidSectionId;
    }
    SchemaTypeId type_id = type_id_or.ValueOrDie();
    for (SectionId section_id = 0; section_id <= kMaxSectionId; ++section_id) {
      auto metadata_or = schema_store_->GetSectionMetadata(type_id, section_id);
      if (!metadata_or.ok()) {
        break;
      }
      const SectionMetadata* metadata = metadata_or.ValueOrDie();
      if (metadata->path == property) {
        return metadata->id;
      }
    }
    return kInvalidSectionId;
  }

  const Filesystem filesystem_;
  const std::string test_dir_;
  std::unique_ptr<LanguageSegmenter> language_segmenter_;
  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<Normalizer> normalizer_;
  std::unique_ptr<DocumentStore> document_store_;
  FakeClock fake_clock_;
};

SectionIdMask CreateSectionIdMask(const std::vector<SectionId>& section_ids) {
  SectionIdMask mask = 0;
  for (SectionId section_id : section_ids) {
    mask |= (UINT64_C(1) << section_id);
  }
  return mask;
}

SearchSpecProto CreateSearchSpec(TermMatchType::Code match_type) {
  SearchSpecProto search_spec;
  search_spec.set_term_match_type(match_type);
  return search_spec;
}

ScoringSpecProto CreateScoringSpec(bool is_descending_order) {
  ScoringSpecProto scoring_spec;
  scoring_spec.set_order_by(is_descending_order ? ScoringSpecProto::Order::DESC
                                                : ScoringSpecProto::Order::ASC);
  return scoring_spec;
}

ResultSpecProto CreateResultSpec(int num_per_page) {
  ResultSpecProto result_spec;
  result_spec.set_num_per_page(num_per_page);
  return result_spec;
}

TEST_F(ResultRetrieverV2ProjectionTest, ProjectionTopLevelLeadNodeFieldPath) {
  // 1. Add two Email documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document_one));

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document_two));

  // 2. Setup the scored results.
  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "name"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  TypePropertyMask* type_property_mask = result_spec.add_type_property_masks();
  type_property_mask->set_schema_type("Email");
  type_property_mask->add_paths("name");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned results only contain the 'name' property.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  DocumentProto projected_document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Hello World!")
          .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document_one));

  DocumentProto projected_document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Goodnight Moon!")
          .Build();
  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(projected_document_two));
}

TEST_F(ResultRetrieverV2ProjectionTest, ProjectionNestedLeafNodeFieldPath) {
  // 1. Add two Email documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender",
              DocumentBuilder()
                  .SetKey("namespace", "uri1")
                  .SetSchema("Person")
                  .AddStringProperty("name", "Meg Ryan")
                  .AddStringProperty("emailAddress", "shopgirl@aol.com")
                  .Build())
          .AddStringProperty("name", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document_one));

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri2")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Tom Hanks")
                            .AddStringProperty("emailAddress", "ny152@aol.com")
                            .Build())
          .AddStringProperty("name", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document_two));

  // 2. Setup the scored results.
  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "name"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  TypePropertyMask* type_property_mask = result_spec.add_type_property_masks();
  type_property_mask->set_schema_type("Email");
  type_property_mask->add_paths("sender.name");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned results only contain the 'sender.name'
  // property.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  DocumentProto projected_document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty("sender",
                               DocumentBuilder()
                                   .SetKey("namespace", "uri1")
                                   .SetSchema("Person")
                                   .AddStringProperty("name", "Meg Ryan")
                                   .Build())
          .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document_one));

  DocumentProto projected_document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty("sender",
                               DocumentBuilder()
                                   .SetKey("namespace", "uri2")
                                   .SetSchema("Person")
                                   .AddStringProperty("name", "Tom Hanks")
                                   .Build())
          .Build();
  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(projected_document_two));
}

TEST_F(ResultRetrieverV2ProjectionTest, ProjectionIntermediateNodeFieldPath) {
  // 1. Add two Email documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender",
              DocumentBuilder()
                  .SetKey("namespace", "uri1")
                  .SetSchema("Person")
                  .AddStringProperty("name", "Meg Ryan")
                  .AddStringProperty("emailAddress", "shopgirl@aol.com")
                  .Build())
          .AddStringProperty("name", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document_one));

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri2")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Tom Hanks")
                            .AddStringProperty("emailAddress", "ny152@aol.com")
                            .Build())
          .AddStringProperty("name", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document_two));

  // 2. Setup the scored results.
  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "name"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  TypePropertyMask* type_property_mask = result_spec.add_type_property_masks();
  type_property_mask->set_schema_type("Email");
  type_property_mask->add_paths("sender");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned results only contain the 'sender'
  // property and all of the subproperties of 'sender'.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  DocumentProto projected_document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender",
              DocumentBuilder()
                  .SetKey("namespace", "uri1")
                  .SetSchema("Person")
                  .AddStringProperty("name", "Meg Ryan")
                  .AddStringProperty("emailAddress", "shopgirl@aol.com")
                  .Build())
          .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document_one));

  DocumentProto projected_document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri2")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Tom Hanks")
                            .AddStringProperty("emailAddress", "ny152@aol.com")
                            .Build())
          .Build();
  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(projected_document_two));
}

TEST_F(ResultRetrieverV2ProjectionTest, ProjectionMultipleNestedFieldPaths) {
  // 1. Add two Email documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender",
              DocumentBuilder()
                  .SetKey("namespace", "uri1")
                  .SetSchema("Person")
                  .AddStringProperty("name", "Meg Ryan")
                  .AddStringProperty("emailAddress", "shopgirl@aol.com")
                  .Build())
          .AddStringProperty("name", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document_one));

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri2")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Tom Hanks")
                            .AddStringProperty("emailAddress", "ny152@aol.com")
                            .Build())
          .AddStringProperty("name", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document_two));

  // 2. Setup the scored results.
  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "name"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  TypePropertyMask* type_property_mask = result_spec.add_type_property_masks();
  type_property_mask->set_schema_type("Email");
  type_property_mask->add_paths("sender.name");
  type_property_mask->add_paths("sender.emailAddress");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned results only contain the 'sender.name' and
  // 'sender.address' properties.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  DocumentProto projected_document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender",
              DocumentBuilder()
                  .SetKey("namespace", "uri1")
                  .SetSchema("Person")
                  .AddStringProperty("name", "Meg Ryan")
                  .AddStringProperty("emailAddress", "shopgirl@aol.com")
                  .Build())
          .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document_one));

  DocumentProto projected_document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty(
              "sender", DocumentBuilder()
                            .SetKey("namespace", "uri2")
                            .SetSchema("Person")
                            .AddStringProperty("name", "Tom Hanks")
                            .AddStringProperty("emailAddress", "ny152@aol.com")
                            .Build())
          .Build();
  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(projected_document_two));
}

TEST_F(ResultRetrieverV2ProjectionTest, ProjectionEmptyFieldPath) {
  // 1. Add two Email documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document_one));

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document_two));

  // 2. Setup the scored results.
  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "name"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  TypePropertyMask* type_property_mask = result_spec.add_type_property_masks();
  type_property_mask->set_schema_type("Email");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned results contain *no* properties.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  DocumentProto projected_document_one = DocumentBuilder()
                                             .SetKey("namespace", "uri1")
                                             .SetCreationTimestampMs(1000)
                                             .SetSchema("Email")
                                             .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document_one));

  DocumentProto projected_document_two = DocumentBuilder()
                                             .SetKey("namespace", "uri2")
                                             .SetCreationTimestampMs(1000)
                                             .SetSchema("Email")
                                             .Build();
  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(projected_document_two));
}

TEST_F(ResultRetrieverV2ProjectionTest, ProjectionInvalidFieldPath) {
  // 1. Add two Email documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document_one));

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document_two));

  // 2. Setup the scored results.
  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "name"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  TypePropertyMask* type_property_mask = result_spec.add_type_property_masks();
  type_property_mask->set_schema_type("Email");
  type_property_mask->add_paths("nonExistentProperty");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned results contain *no* properties.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  DocumentProto projected_document_one = DocumentBuilder()
                                             .SetKey("namespace", "uri1")
                                             .SetCreationTimestampMs(1000)
                                             .SetSchema("Email")
                                             .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document_one));

  DocumentProto projected_document_two = DocumentBuilder()
                                             .SetKey("namespace", "uri2")
                                             .SetCreationTimestampMs(1000)
                                             .SetSchema("Email")
                                             .Build();
  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(projected_document_two));
}

TEST_F(ResultRetrieverV2ProjectionTest, ProjectionValidAndInvalidFieldPath) {
  // 1. Add two Email documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document_one));

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document_two));

  // 2. Setup the scored results.
  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "name"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  TypePropertyMask* type_property_mask = result_spec.add_type_property_masks();
  type_property_mask->set_schema_type("Email");
  type_property_mask->add_paths("name");
  type_property_mask->add_paths("nonExistentProperty");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned results only contain the 'name' property.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  DocumentProto projected_document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Hello World!")
          .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document_one));

  DocumentProto projected_document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Goodnight Moon!")
          .Build();
  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(projected_document_two));
}

TEST_F(ResultRetrieverV2ProjectionTest, ProjectionMultipleTypesNoWildcards) {
  // 1. Add two documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document_one));

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .AddStringProperty("emailAddress", "ny152@aol.com")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document_two));

  // 2. Setup the scored results.
  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "name"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  TypePropertyMask* type_property_mask = result_spec.add_type_property_masks();
  type_property_mask->set_schema_type("Email");
  type_property_mask->add_paths("name");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      //*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned Email results only contain the 'name'
  // property and the returned Person results have all of their properties.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  DocumentProto projected_document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Hello World!")
          .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document_one));

  DocumentProto projected_document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .AddStringProperty("emailAddress", "ny152@aol.com")
          .Build();
  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(projected_document_two));
}

TEST_F(ResultRetrieverV2ProjectionTest, ProjectionMultipleTypesWildcard) {
  // 1. Add two documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document_one));

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .AddStringProperty("emailAddress", "ny152@aol.com")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document_two));

  // 2. Setup the scored results.
  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "name"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  TypePropertyMask* wildcard_type_property_mask =
      result_spec.add_type_property_masks();
  wildcard_type_property_mask->set_schema_type(
      std::string(SchemaStore::kSchemaTypeWildcard));
  wildcard_type_property_mask->add_paths("name");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned Email results only contain the 'name'
  // property and the returned Person results only contain the 'name' property.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  DocumentProto projected_document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Hello World!")
          .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document_one));

  DocumentProto projected_document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .Build();
  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(projected_document_two));
}

TEST_F(ResultRetrieverV2ProjectionTest,
       ProjectionMultipleTypesWildcardWithOneOverride) {
  // 1. Add two documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document_one));

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .AddStringProperty("emailAddress", "ny152@aol.com")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document_two));

  // 2. Setup the scored results.
  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "name"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  TypePropertyMask* email_type_property_mask =
      result_spec.add_type_property_masks();
  email_type_property_mask->set_schema_type("Email");
  email_type_property_mask->add_paths("body");
  TypePropertyMask* wildcard_type_property_mask =
      result_spec.add_type_property_masks();
  wildcard_type_property_mask->set_schema_type(
      std::string(SchemaStore::kSchemaTypeWildcard));
  wildcard_type_property_mask->add_paths("name");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned Email results only contain the 'body'
  // property and the returned Person results  only contain the 'name' property.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  DocumentProto projected_document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document_one));

  DocumentProto projected_document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .Build();
  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(projected_document_two));
}

TEST_F(ResultRetrieverV2ProjectionTest,
       ProjectionSingleTypesWildcardAndOverride) {
  // 1. Add two documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .AddDocumentProperty(
              "sender",
              DocumentBuilder()
                  .SetKey("namespace", "uri")
                  .SetSchema("Person")
                  .AddStringProperty("name", "Mr. Body")
                  .AddStringProperty("emailAddress", "mr.body123@gmail.com")
                  .Build())
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document_one));

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .AddStringProperty("emailAddress", "ny152@aol.com")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document_two));

  // 2. Setup the scored results.
  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "name"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  TypePropertyMask* email_type_property_mask =
      result_spec.add_type_property_masks();
  email_type_property_mask->set_schema_type("Email");
  email_type_property_mask->add_paths("sender.name");
  TypePropertyMask* wildcard_type_property_mask =
      result_spec.add_type_property_masks();
  wildcard_type_property_mask->set_schema_type(
      std::string(SchemaStore::kSchemaTypeWildcard));
  wildcard_type_property_mask->add_paths("name");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned Email results only contain the 'sender.name'
  // property and the returned Person results only contain the 'name' property.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  DocumentProto projected_document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty("sender",
                               DocumentBuilder()
                                   .SetKey("namespace", "uri")
                                   .SetSchema("Person")
                                   .AddStringProperty("name", "Mr. Body")
                                   .Build())
          .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document_one));

  DocumentProto projected_document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .Build();
  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(projected_document_two));
}

TEST_F(ResultRetrieverV2ProjectionTest,
       ProjectionSingleTypesWildcardAndOverrideNestedProperty) {
  // 1. Add two documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .AddDocumentProperty(
              "sender",
              DocumentBuilder()
                  .SetKey("namespace", "uri")
                  .SetSchema("Person")
                  .AddStringProperty("name", "Mr. Body")
                  .AddStringProperty("emailAddress", "mr.body123@gmail.com")
                  .Build())
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document_one));

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .AddStringProperty("emailAddress", "ny152@aol.com")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document_two));

  // 2. Setup the scored results.
  std::vector<SectionId> hit_section_ids = {GetSectionId("Email", "name"),
                                            GetSectionId("Email", "body")};
  SectionIdMask hit_section_id_mask = CreateSectionIdMask(hit_section_ids);
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, hit_section_id_mask, /*score=*/0},
      {document_id2, hit_section_id_mask, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  TypePropertyMask* email_type_property_mask =
      result_spec.add_type_property_masks();
  email_type_property_mask->set_schema_type("Email");
  email_type_property_mask->add_paths("sender.name");
  TypePropertyMask* wildcard_type_property_mask =
      result_spec.add_type_property_masks();
  wildcard_type_property_mask->set_schema_type(
      std::string(SchemaStore::kSchemaTypeWildcard));
  wildcard_type_property_mask->add_paths("sender");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned Email results only contain the 'sender.name'
  // property and the returned Person results contain no properties.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  DocumentProto projected_document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddDocumentProperty("sender",
                               DocumentBuilder()
                                   .SetKey("namespace", "uri")
                                   .SetSchema("Person")
                                   .AddStringProperty("name", "Mr. Body")
                                   .Build())
          .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document_one));

  DocumentProto projected_document_two = DocumentBuilder()
                                             .SetKey("namespace", "uri2")
                                             .SetCreationTimestampMs(1000)
                                             .SetSchema("Person")
                                             .Build();
  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(projected_document_two));
}

TEST_F(ResultRetrieverV2ProjectionTest, ProjectionJoinDocuments) {
  // 1. Add one Person document
  DocumentProto person_document =
      DocumentBuilder()
          .SetKey("namespace", "Person/1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .AddStringProperty("emailAddress", "ny152@aol.com")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId person_document_id,
                             document_store_->Put(person_document));

  // 2. Add two Email documents
  DocumentProto email_document1 =
      DocumentBuilder()
          .SetKey("namespace", "Email/1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Hello World!")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId email_document_id1,
                             document_store_->Put(email_document1));

  DocumentProto email_document2 =
      DocumentBuilder()
          .SetKey("namespace", "Email/2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("name", "Goodnight Moon!")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId email_document_id2,
                             document_store_->Put(email_document2));

  // 3. Setup the joined scored results.
  std::vector<SectionId> person_hit_section_ids = {
      GetSectionId("Person", "name")};
  std::vector<SectionId> email_hit_section_ids = {
      GetSectionId("Email", "name"), GetSectionId("Email", "body")};
  SectionIdMask person_hit_section_id_mask =
      CreateSectionIdMask(person_hit_section_ids);
  SectionIdMask email_hit_section_id_mask =
      CreateSectionIdMask(email_hit_section_ids);

  ScoredDocumentHit person_scored_doc_hit(
      person_document_id, person_hit_section_id_mask, /*score=*/0);
  ScoredDocumentHit email1_scored_doc_hit(
      email_document_id1, email_hit_section_id_mask, /*score=*/0);
  ScoredDocumentHit email2_scored_doc_hit(
      email_document_id2, email_hit_section_id_mask, /*score=*/0);
  // Create JoinedScoredDocumentHits mapping Person to Email1 and Email2
  std::vector<JoinedScoredDocumentHit> joined_scored_document_hits = {
      JoinedScoredDocumentHit(
          /*final_score=*/0,
          /*parent_scored_document_hit=*/person_scored_doc_hit,
          /*child_scored_document_hits=*/
          {email1_scored_doc_hit, email2_scored_doc_hit})};

  // 4. Create parent ResultSpec with type property mask.
  ResultSpecProto parent_result_spec = CreateResultSpec(/*num_per_page=*/2);
  parent_result_spec.set_max_joined_children_per_parent_to_return(
      std::numeric_limits<int>::max());
  TypePropertyMask* type_property_mask =
      parent_result_spec.add_type_property_masks();
  type_property_mask->set_schema_type("Person");
  type_property_mask->add_paths("name");

  // 5. Create child ResultSpec with type property mask.
  ResultSpecProto child_result_spec;
  type_property_mask = child_result_spec.add_type_property_masks();
  type_property_mask->set_schema_type("Email");
  type_property_mask->add_paths("body");

  // 6. Create ResultState with custom ResultSpecs.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<JoinedScoredDocumentHit>>(
          std::move(joined_scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), parent_result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), child_result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      parent_result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 7. Verify that the returned results:
  //    - Person docs only contain the "name" property.
  //    - Email docs only contain the "body" property.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(1));

  // Check parent doc.
  DocumentProto projected_person_document =
      DocumentBuilder()
          .SetKey("namespace", "Person/1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_person_document));

  // Check child docs.
  ASSERT_THAT(page_result.results.at(0).joined_results(), SizeIs(2));
  // Check Email1
  DocumentProto projected_email_document1 =
      DocumentBuilder()
          .SetKey("namespace", "Email/1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty(
              "body", "Oh what a beautiful morning! Oh what a beautiful day!")
          .Build();
  EXPECT_THAT(page_result.results.at(0).joined_results(0).document(),
              EqualsProto(projected_email_document1));
  // Check Email2
  DocumentProto projected_email_document2 =
      DocumentBuilder()
          .SetKey("namespace", "Email/2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Email")
          .AddStringProperty("body",
                             "Count all the sheep and tell them 'Hello'.")
          .Build();
  EXPECT_THAT(page_result.results.at(0).joined_results(1).document(),
              EqualsProto(projected_email_document2));
}

TEST_F(ResultRetrieverV2ProjectionTest, ProjectionPolymorphism) {
  // 1. Add two documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .AddStringProperty("emailAddress", "ny152@aol.com")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document_one));

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Artist")
          .AddStringProperty("name", "Joe Artist")
          .AddStringProperty("emailAddress", "artist@aol.com")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document_two));

  // 2. Setup the scored results.
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, kSectionIdMaskAll, /*score=*/0},
      {document_id2, kSectionIdMaskAll, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  // Since Artist is a child type of Person, the TypePropertyMask for Person
  // also applies to Artist.
  TypePropertyMask* person_type_property_mask =
      result_spec.add_type_property_masks();
  person_type_property_mask->set_schema_type("Person");
  person_type_property_mask->add_paths("name");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned Person and Artist results only contain the
  // 'name' property.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  DocumentProto projected_document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document_one));

  DocumentProto projected_document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Artist")
          .AddStringProperty("name", "Joe Artist")
          .Build();
  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(projected_document_two));
}

TEST_F(ResultRetrieverV2ProjectionTest, ProjectionTransitivePolymorphism) {
  // 1. Add two documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .AddStringProperty("emailAddress", "ny152@aol.com")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document_one));

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Musician")
          .AddStringProperty("name", "Joe Musician")
          .AddStringProperty("emailAddress", "Musician@aol.com")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document_two));

  // 2. Setup the scored results.
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, kSectionIdMaskAll, /*score=*/0},
      {document_id2, kSectionIdMaskAll, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  // Since Musician is a transitive child type of Person, the TypePropertyMask
  // for Person also applies to Musician.
  TypePropertyMask* person_type_property_mask =
      result_spec.add_type_property_masks();
  person_type_property_mask->set_schema_type("Person");
  person_type_property_mask->add_paths("name");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned Person and Musician results only contain the
  // 'name' property.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  DocumentProto projected_document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document_one));

  DocumentProto projected_document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Musician")
          .AddStringProperty("name", "Joe Musician")
          .Build();
  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(projected_document_two));
}

TEST_F(ResultRetrieverV2ProjectionTest,
       ProjectionPolymorphismChildMissingProperty) {
  // 1. Add an artist document with missing 'emailAddress', which is allowed
  // since 'emailAddress' in the parent type 'Person' is defined as optional.
  DocumentProto document = DocumentBuilder()
                               .SetKey("namespace", "uri")
                               .SetCreationTimestampMs(1000)
                               .SetSchema("Artist")
                               .AddStringProperty("name", "Joe Artist")
                               .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id,
                             document_store_->Put(document));

  // 2. Setup the scored results.
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id, kSectionIdMaskAll, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask for the missing property
  // 'emailAddress' in the Person type. Since Artist is a child type of Person,
  // the TypePropertyMask for Person also applies to Artist.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  TypePropertyMask* person_type_property_mask =
      result_spec.add_type_property_masks();
  person_type_property_mask->set_schema_type("Person");
  person_type_property_mask->add_paths("emailAddress");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned person document does not contain any property,
  // since 'emailAddress' is missing.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(1));
  DocumentProto projected_document = DocumentBuilder()
                                         .SetKey("namespace", "uri")
                                         .SetCreationTimestampMs(1000)
                                         .SetSchema("Artist")
                                         .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document));
}

TEST_F(ResultRetrieverV2ProjectionTest, ProjectionPolymorphismMerge) {
  // 1. Add two documents
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .AddStringProperty("emailAddress", "ny152@aol.com")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document_one));

  DocumentProto document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Artist")
          .AddStringProperty("name", "Joe Artist")
          .AddStringProperty("emailAddress", "artist@aol.com")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document_two));

  // 2. Setup the scored results.
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id1, kSectionIdMaskAll, /*score=*/0},
      {document_id2, kSectionIdMaskAll, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/2);
  TypePropertyMask* person_type_property_mask =
      result_spec.add_type_property_masks();
  person_type_property_mask->set_schema_type("Person");
  person_type_property_mask->add_paths("name");
  // Since Artist is a child type of Person, the TypePropertyMask for Person
  // will be merged to Artist's TypePropertyMask by polymorphism, so that 'name'
  // will also show in Artist's projection results.
  TypePropertyMask* artist_type_property_mask =
      result_spec.add_type_property_masks();
  artist_type_property_mask->set_schema_type("Artist");
  artist_type_property_mask->add_paths("emailAddress");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned Person results only contain the 'name'
  // property and the returned Artist results contain both the 'name' and
  // 'emailAddress' properties.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(2));

  DocumentProto projected_document_one =
      DocumentBuilder()
          .SetKey("namespace", "uri1")
          .SetCreationTimestampMs(1000)
          .SetSchema("Person")
          .AddStringProperty("name", "Joe Fox")
          .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document_one));

  DocumentProto projected_document_two =
      DocumentBuilder()
          .SetKey("namespace", "uri2")
          .SetCreationTimestampMs(1000)
          .SetSchema("Artist")
          .AddStringProperty("name", "Joe Artist")
          .AddStringProperty("emailAddress", "artist@aol.com")
          .Build();
  EXPECT_THAT(page_result.results.at(1).document(),
              EqualsProto(projected_document_two));
}

TEST_F(ResultRetrieverV2ProjectionTest, ProjectionMultipleParentPolymorphism) {
  // 1. Add a document
  DocumentProto document = DocumentBuilder()
                               .SetKey("namespace", "uri")
                               .SetCreationTimestampMs(1000)
                               .SetSchema("PersonWithPhone")
                               .AddStringProperty("name", "name")
                               .AddStringProperty("emailAddress", "email")
                               .AddStringProperty("phoneNumber", "12345")
                               .AddStringProperty("phoneModel", "pixel")
                               .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id,
                             document_store_->Put(document));

  // 2. Setup the scored results.
  std::vector<ScoredDocumentHit> scored_document_hits = {
      {document_id, kSectionIdMaskAll, /*score=*/0}};

  // 3. Create a ResultSpec with type property mask.
  ResultSpecProto result_spec = CreateResultSpec(/*num_per_page=*/1);
  // Since PersonWithPhone is a child type of Person, the TypePropertyMask
  // also applies to PersonWithPhone.
  TypePropertyMask* person_type_property_mask =
      result_spec.add_type_property_masks();
  person_type_property_mask->set_schema_type("Person");
  person_type_property_mask->add_paths("name");
  // Since PersonWithPhone is a child type of WithPhone, the
  // TypePropertyMask also applies to PersonWithPhone.
  TypePropertyMask* with_phone_type_property_mask =
      result_spec.add_type_property_masks();
  with_phone_type_property_mask->set_schema_type("WithPhone");
  with_phone_type_property_mask->add_paths("phoneNumber");

  // 4. Create ResultState with custom ResultSpec.
  ResultStateV2 result_state(
      std::make_unique<
          PriorityQueueScoredDocumentHitsRanker<ScoredDocumentHit>>(
          std::move(scored_document_hits), /*is_descending=*/false),
      /*parent_adjustment_info=*/
      std::make_unique<ResultAdjustmentInfo>(
          CreateSearchSpec(TermMatchType::EXACT_ONLY),
          CreateScoringSpec(/*is_descending_order=*/false), result_spec,
          schema_store_.get(), SectionRestrictQueryTermsMap()),
      /*child_adjustment_info=*/nullptr, result_spec, *document_store_);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ResultRetrieverV2> result_retriever,
      ResultRetrieverV2::Create(document_store_.get(), schema_store_.get(),
                                language_segmenter_.get(), normalizer_.get()));

  // 5. Verify that the returned document only contains the 'name' and the
  // 'phoneNumber' property.
  PageResult page_result =
      result_retriever
          ->RetrieveNextPage(result_state,
                             fake_clock_.GetSystemTimeMilliseconds())
          .first;
  ASSERT_THAT(page_result.results, SizeIs(1));

  DocumentProto projected_document =
      DocumentBuilder()
          .SetKey("namespace", "uri")
          .SetCreationTimestampMs(1000)
          .SetSchema("PersonWithPhone")
          .AddStringProperty("name", "name")
          .AddStringProperty("phoneNumber", "12345")
          .Build();
  EXPECT_THAT(page_result.results.at(0).document(),
              EqualsProto(projected_document));
}

}  // namespace

}  // namespace lib
}  // namespace icing
