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

#include "icing/index/iterator/doc-hit-info-iterator-property-in-schema.h"

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator-all-document-id.h"
#include "icing/index/iterator/doc-hit-info-iterator-test-util.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/schema-builder.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;

class DocHitInfoIteratorPropertyInSchemaTest : public ::testing::Test {
 protected:
  DocHitInfoIteratorPropertyInSchemaTest()
      : test_dir_(GetTestTempDir() + "/icing") {}

  void SetUp() override {
    filesystem_.CreateDirectoryRecursively(test_dir_.c_str());
    document1_ = DocumentBuilder()
                     .SetKey("namespace", "uri1")
                     .SetSchema("email")
                     .Build();
    document2_ =
        DocumentBuilder().SetKey("namespace", "uri2").SetSchema("note").Build();

    indexed_section_0 = "indexedSection0";
    unindexed_section_1 = "unindexedSection1";
    not_defined_section_2 = "notDefinedSection2";

    schema_ =
        SchemaBuilder()
            .AddType(
                SchemaTypeConfigBuilder()
                    .SetType("email")
                    // Add an indexed property so we generate section
                    // metadata on it
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(indexed_section_0)
                                     .SetDataTypeString(TERM_MATCH_EXACT,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(unindexed_section_1)
                                     .SetDataType(TYPE_STRING)
                                     .SetCardinality(CARDINALITY_OPTIONAL)))
            .AddType(SchemaTypeConfigBuilder().SetType("note").AddProperty(
                PropertyConfigBuilder()
                    .SetName(unindexed_section_1)
                    .SetDataType(TYPE_STRING)
                    .SetCardinality(CARDINALITY_OPTIONAL)))
            .Build();

    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, test_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store_->SetSchema(
        schema_, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

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
    document_store_.reset();
    schema_store_.reset();
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<DocumentStore> document_store_;
  const Filesystem filesystem_;
  const std::string test_dir_;
  std::string indexed_section_0;
  std::string unindexed_section_1;
  std::string not_defined_section_2;
  SchemaProto schema_;
  DocumentProto document1_;
  DocumentProto document2_;
  FakeClock fake_clock_;
};

TEST_F(DocHitInfoIteratorPropertyInSchemaTest,
       AdvanceToDocumentWithIndexedProperty) {
  // Populate the DocumentStore's FilterCache with this document's data
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id,
                             document_store_->Put(document1_));

  auto original_iterator = std::make_unique<DocHitInfoIteratorAllDocumentId>(
      document_store_->num_documents());

  DocHitInfoIteratorPropertyInSchema property_defined_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      /*target_target_sections=*/{indexed_section_0},
      fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&property_defined_iterator),
              ElementsAre(document_id));

  EXPECT_FALSE(property_defined_iterator.Advance().ok());
}

TEST_F(DocHitInfoIteratorPropertyInSchemaTest,
       AdvanceToDocumentWithUnindexedProperty) {
  // Populate the DocumentStore's FilterCache with this document's data
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id,
                             document_store_->Put(document1_));

  auto original_iterator = std::make_unique<DocHitInfoIteratorAllDocumentId>(
      document_store_->num_documents());

  DocHitInfoIteratorPropertyInSchema property_defined_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      /*target_target_sections=*/{unindexed_section_1},
      fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&property_defined_iterator),
              ElementsAre(document_id));

  EXPECT_FALSE(property_defined_iterator.Advance().ok());
}

TEST_F(DocHitInfoIteratorPropertyInSchemaTest, NoMatchWithUndefinedProperty) {
  ICING_EXPECT_OK(document_store_->Put(document1_));

  auto original_iterator = std::make_unique<DocHitInfoIteratorAllDocumentId>(
      document_store_->num_documents());

  DocHitInfoIteratorPropertyInSchema property_defined_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      /*target_target_sections=*/{not_defined_section_2},
      fake_clock_.GetSystemTimeMilliseconds());
  EXPECT_FALSE(property_defined_iterator.Advance().ok());
}

TEST_F(DocHitInfoIteratorPropertyInSchemaTest,
       CorrectlySetsSectionIdMasksAndPopulatesTermMatchInfo) {
  // Populate the DocumentStore's FilterCache with this document's data
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id,
                             document_store_->Put(document1_));

  // Arbitrary section ids for the documents in the DocHitInfoIterators.
  // Created to test correct section_id_mask behavior.
  SectionIdMask original_section_id_mask = 0b00000101;  // hits in sections 0, 2

  DocHitInfoTermFrequencyPair doc_hit_info1 = DocHitInfo(document_id);
  doc_hit_info1.UpdateSection(/*section_id=*/0, /*hit_term_frequency=*/1);
  doc_hit_info1.UpdateSection(/*section_id=*/2, /*hit_term_frequency=*/2);

  // Create a hit that was found in the indexed section
  std::vector<DocHitInfoTermFrequencyPair> doc_hit_infos = {doc_hit_info1};

  auto original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "hi");
  original_iterator->set_hit_section_ids_mask(original_section_id_mask);

  DocHitInfoIteratorPropertyInSchema property_defined_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      /*target_target_sections=*/{indexed_section_0},
      fake_clock_.GetSystemTimeMilliseconds());

  std::vector<TermMatchInfo> matched_terms_stats;
  property_defined_iterator.PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, IsEmpty());

  ICING_EXPECT_OK(property_defined_iterator.Advance());
  EXPECT_THAT(property_defined_iterator.doc_hit_info().document_id(),
              Eq(document_id));

  // The expected mask is the same as the original mask, since the iterator
  // should treat it as a pass-through.
  SectionIdMask expected_section_id_mask = original_section_id_mask;
  EXPECT_EQ(property_defined_iterator.doc_hit_info().hit_section_ids_mask(),
            expected_section_id_mask);

  property_defined_iterator.PopulateMatchedTermsStats(&matched_terms_stats);
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map = {{0, 1}, {2, 2}};
  EXPECT_THAT(matched_terms_stats, ElementsAre(EqualsTermMatchInfo(
                                       "hi", expected_section_ids_tf_map)));

  EXPECT_FALSE(property_defined_iterator.Advance().ok());
}

TEST_F(DocHitInfoIteratorPropertyInSchemaTest,
       TrimRightMostNodeResultsInError) {
  auto original_iterator = std::make_unique<DocHitInfoIteratorAllDocumentId>(
      document_store_->num_documents());

  DocHitInfoIteratorPropertyInSchema property_defined_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      /*target_target_sections=*/{indexed_section_0},
      fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(std::move(property_defined_iterator).TrimRightMostNode(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(DocHitInfoIteratorPropertyInSchemaTest,
       FindPropertyDefinedByMultipleTypes) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document1_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document2_));
  auto original_iterator = std::make_unique<DocHitInfoIteratorAllDocumentId>(
      document_store_->num_documents());

  DocHitInfoIteratorPropertyInSchema property_defined_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      /*target_target_sections=*/{unindexed_section_1},
      fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&property_defined_iterator),
              ElementsAre(document_id2, document_id1));

  EXPECT_FALSE(property_defined_iterator.Advance().ok());
}

}  // namespace

}  // namespace lib
}  // namespace icing
