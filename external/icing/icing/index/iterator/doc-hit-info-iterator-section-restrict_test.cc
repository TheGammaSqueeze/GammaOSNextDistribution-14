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

#include "icing/index/iterator/doc-hit-info-iterator-section-restrict.h"

#include <memory>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator-and.h"
#include "icing/index/iterator/doc-hit-info-iterator-test-util.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/term.pb.h"
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

constexpr SectionId kIndexedSectionId0 = 0;
constexpr SectionId kIndexedSectionId1 = 1;

class DocHitInfoIteratorSectionRestrictTest : public ::testing::Test {
 protected:
  DocHitInfoIteratorSectionRestrictTest()
      : test_dir_(GetTestTempDir() + "/icing") {}

  void SetUp() override {
    filesystem_.CreateDirectoryRecursively(test_dir_.c_str());
    document1_ = DocumentBuilder()
                     .SetKey("namespace", "uri1")
                     .SetSchema("email")
                     .Build();
    document2_ = DocumentBuilder()
                     .SetKey("namespace", "uri2")
                     .SetSchema("email")
                     .Build();
    document3_ = DocumentBuilder()
                     .SetKey("namespace", "uri3")
                     .SetSchema("email")
                     .Build();

    indexed_section_0 = "indexedSection0";
    indexed_section_1 = "indexedSection1";
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
                                     .SetName(indexed_section_1)
                                     .SetDataTypeString(TERM_MATCH_EXACT,
                                                        TOKENIZER_PLAIN)
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
  std::string indexed_section_1;
  SchemaProto schema_;
  DocumentProto document1_;
  DocumentProto document2_;
  DocumentProto document3_;
  FakeClock fake_clock_;
};

TEST_F(DocHitInfoIteratorSectionRestrictTest,
       PopulateMatchedTermsStats_IncludesHitWithMatchingSection) {
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

  // Filtering for the indexed section name (which has a section id of 0) should
  // get a result.
  std::unique_ptr<DocHitInfoIterator> section_restrict_iterator =
      DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
          std::move(original_iterator), document_store_.get(),
          schema_store_.get(),
          /*target_sections=*/{indexed_section_0},
          fake_clock_.GetSystemTimeMilliseconds());

  std::vector<TermMatchInfo> matched_terms_stats;
  section_restrict_iterator->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, IsEmpty());

  ICING_EXPECT_OK(section_restrict_iterator->Advance());
  EXPECT_THAT(section_restrict_iterator->doc_hit_info().document_id(),
              Eq(document_id));
  SectionIdMask expected_section_id_mask = 0b00000001;  // hits in sections 0
  EXPECT_EQ(section_restrict_iterator->doc_hit_info().hit_section_ids_mask(),
            expected_section_id_mask);

  section_restrict_iterator->PopulateMatchedTermsStats(&matched_terms_stats);
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map = {{0, 1}};
  EXPECT_THAT(matched_terms_stats, ElementsAre(EqualsTermMatchInfo(
                                       "hi", expected_section_ids_tf_map)));

  EXPECT_FALSE(section_restrict_iterator->Advance().ok());
}

TEST_F(DocHitInfoIteratorSectionRestrictTest, EmptyOriginalIterator) {
  std::unique_ptr<DocHitInfoIterator> original_iterator_empty =
      std::make_unique<DocHitInfoIteratorDummy>();

  std::unique_ptr<DocHitInfoIterator> filtered_iterator =
      DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
          std::move(original_iterator_empty), document_store_.get(),
          schema_store_.get(), /*target_sections=*/std::set<std::string>(),
          fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(filtered_iterator.get()), IsEmpty());
  std::vector<TermMatchInfo> matched_terms_stats;
  filtered_iterator->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, IsEmpty());
}

TEST_F(DocHitInfoIteratorSectionRestrictTest, IncludesHitWithMatchingSection) {
  // Populate the DocumentStore's FilterCache with this document's data
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id,
                             document_store_->Put(document1_));

  SectionIdMask section_id_mask = 1U << kIndexedSectionId0;

  // Create a hit that was found in the indexed section
  std::vector<DocHitInfo> doc_hit_infos = {
      DocHitInfo(document_id, section_id_mask)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  // Filtering for the indexed section name should get a result
  std::unique_ptr<DocHitInfoIterator> section_restrict_iterator =
      DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
          std::move(original_iterator), document_store_.get(),
          schema_store_.get(),
          /*target_sections=*/{indexed_section_0},
          fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(section_restrict_iterator.get()),
              ElementsAre(document_id));
}

TEST_F(DocHitInfoIteratorSectionRestrictTest,
       IncludesHitWithMultipleMatchingSectionsWithMultipleSectionRestricts) {
  // Populate the DocumentStore's FilterCache with this document's data
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id,
                             document_store_->Put(document1_));

  SectionIdMask section_id_mask = 1U << kIndexedSectionId0;
  section_id_mask |= 1U << kIndexedSectionId1;

  // Create a hit that was found in the indexed section
  std::vector<DocHitInfo> doc_hit_infos = {
      DocHitInfo(document_id, section_id_mask)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  // Filter for both target_sections
  std::unique_ptr<DocHitInfoIterator> section_restrict_iterator =
      DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
          std::move(original_iterator), document_store_.get(),
          schema_store_.get(),
          /*target_sections=*/{indexed_section_0, indexed_section_1},
          fake_clock_.GetSystemTimeMilliseconds());

  ICING_ASSERT_OK(section_restrict_iterator->Advance());
  std::vector<SectionId> expected_section_ids = {kIndexedSectionId0,
                                                 kIndexedSectionId1};
  EXPECT_THAT(section_restrict_iterator->doc_hit_info(),
              EqualsDocHitInfo(document_id, expected_section_ids));
}

TEST_F(DocHitInfoIteratorSectionRestrictTest,
       IncludesHitWithMultipleMatchingSectionsWithSingleSectionRestrict) {
  // Populate the DocumentStore's FilterCache with this document's data
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id,
                             document_store_->Put(document1_));

  SectionIdMask section_id_mask = 1U << kIndexedSectionId0;
  section_id_mask |= 1U << kIndexedSectionId1;

  // Create a hit that was found in the indexed section
  std::vector<DocHitInfo> doc_hit_infos = {
      DocHitInfo(document_id, section_id_mask)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  // Filter for both target_sections
  std::unique_ptr<DocHitInfoIterator> section_restrict_iterator =
      DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
          std::move(original_iterator), document_store_.get(),
          schema_store_.get(),
          /*target_sections=*/{indexed_section_1},
          fake_clock_.GetSystemTimeMilliseconds());

  ICING_ASSERT_OK(section_restrict_iterator->Advance());
  std::vector<SectionId> expected_section_ids = {kIndexedSectionId1};
  EXPECT_THAT(section_restrict_iterator->doc_hit_info(),
              EqualsDocHitInfo(document_id, expected_section_ids));
}

TEST_F(DocHitInfoIteratorSectionRestrictTest,
       IncludesHitWithSingleMatchingSectionsWithMultiSectionRestrict) {
  // Populate the DocumentStore's FilterCache with this document's data
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id,
                             document_store_->Put(document1_));

  SectionIdMask section_id_mask = 1U << kIndexedSectionId1;

  // Create a hit that was found in the indexed section
  std::vector<DocHitInfo> doc_hit_infos = {
      DocHitInfo(document_id, section_id_mask)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  // Filter for both target_sections
  std::unique_ptr<DocHitInfoIterator> section_restrict_iterator =
      DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
          std::move(original_iterator), document_store_.get(),
          schema_store_.get(),
          /*target_sections=*/{indexed_section_0, indexed_section_1},
          fake_clock_.GetSystemTimeMilliseconds());

  ICING_ASSERT_OK(section_restrict_iterator->Advance());
  std::vector<SectionId> expected_section_ids = {kIndexedSectionId1};
  EXPECT_THAT(section_restrict_iterator->doc_hit_info(),
              EqualsDocHitInfo(document_id, expected_section_ids));
}

TEST_F(DocHitInfoIteratorSectionRestrictTest, NoMatchingDocumentFilterData) {
  // Create a hit with a document id that doesn't exist in the DocumentStore yet
  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(/*document_id_in=*/0)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  // Filtering for the indexed section name should get a result
  std::unique_ptr<DocHitInfoIterator> section_restrict_iterator =
      DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
          std::move(original_iterator), document_store_.get(),
          schema_store_.get(),
          /*target_sections=*/{""}, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(section_restrict_iterator.get()), IsEmpty());
  std::vector<TermMatchInfo> matched_terms_stats;
  section_restrict_iterator->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, IsEmpty());
}

TEST_F(DocHitInfoIteratorSectionRestrictTest,
       DoesntIncludeHitWithWrongSectionName) {
  // Populate the DocumentStore's FilterCache with this document's data
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id,
                             document_store_->Put(document1_));

  SectionIdMask section_id_mask = 1U << kIndexedSectionId0;

  // Create a hit that was found in the indexed section
  std::vector<DocHitInfo> doc_hit_infos = {
      DocHitInfo(document_id, section_id_mask)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  // Filtering for the indexed section name should get a result
  std::unique_ptr<DocHitInfoIterator> section_restrict_iterator =
      DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
          std::move(original_iterator), document_store_.get(),
          schema_store_.get(),
          /*target_sections=*/{"some_section_name"},
          fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(section_restrict_iterator.get()), IsEmpty());
  std::vector<TermMatchInfo> matched_terms_stats;
  section_restrict_iterator->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, IsEmpty());
}

TEST_F(DocHitInfoIteratorSectionRestrictTest,
       DoesntIncludeHitWithNoSectionIds) {
  // Populate the DocumentStore's FilterCache with this document's data
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id,
                             document_store_->Put(document1_));

  // Create a hit that doesn't exist in any sections, so it shouldn't match any
  // section filters
  std::vector<DocHitInfo> doc_hit_infos = {
      DocHitInfo(document_id, kSectionIdMaskNone)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  std::unique_ptr<DocHitInfoIterator> section_restrict_iterator =
      DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
          std::move(original_iterator), document_store_.get(),
          schema_store_.get(),
          /*target_sections=*/{indexed_section_0},
          fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(section_restrict_iterator.get()), IsEmpty());
  std::vector<TermMatchInfo> matched_terms_stats;
  section_restrict_iterator->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, IsEmpty());
}

TEST_F(DocHitInfoIteratorSectionRestrictTest,
       DoesntIncludeHitWithDifferentSectionId) {
  // Populate the DocumentStore's FilterCache with this document's data
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id,
                             document_store_->Put(document1_));

  // Anything that's not 0, which is the indexed property
  SectionId not_matching_section_id = 2;

  // Create a hit that exists in a different section, so it shouldn't match any
  // section filters
  std::vector<DocHitInfo> doc_hit_infos = {
      DocHitInfo(document_id, UINT64_C(1) << not_matching_section_id)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  std::unique_ptr<DocHitInfoIterator> section_restrict_iterator =
      DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
          std::move(original_iterator), document_store_.get(),
          schema_store_.get(),
          /*target_sections=*/{indexed_section_0},
          fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(section_restrict_iterator.get()), IsEmpty());
  std::vector<TermMatchInfo> matched_terms_stats;
  section_restrict_iterator->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, IsEmpty());
}

TEST_F(DocHitInfoIteratorSectionRestrictTest, GetCallStats) {
  DocHitInfoIterator::CallStats original_call_stats(
      /*num_leaf_advance_calls_lite_index_in=*/2,
      /*num_leaf_advance_calls_main_index_in=*/5,
      /*num_leaf_advance_calls_integer_index_in=*/3,
      /*num_leaf_advance_calls_no_index_in=*/1,
      /*num_blocks_inspected_in=*/4);  // arbitrary value
  auto original_iterator = std::make_unique<DocHitInfoIteratorDummy>();
  original_iterator->SetCallStats(original_call_stats);

  std::unique_ptr<DocHitInfoIterator> section_restrict_iterator =
      DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
          std::move(original_iterator), document_store_.get(),
          schema_store_.get(),
          /*target_sections=*/{""}, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(section_restrict_iterator->GetCallStats(),
              Eq(original_call_stats));
}

TEST_F(DocHitInfoIteratorSectionRestrictTest,
       TrimSectionRestrictIterator_TwoLayer) {
  // Populate the DocumentStore's FilterCache with this document's data
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document1_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document2_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id3,
                             document_store_->Put(document3_));

  // 0 is the indexed property
  SectionId matching_section_id = 0;
  // Anything that's not 0, which is the indexed property
  SectionId not_matching_section_id = 2;

  // Build an iterator tree like:
  //                  AND
  //             /           \
  //    [1, 1],[2, 2]        [3, 2]
  std::vector<DocHitInfo> left_infos = {
      DocHitInfo(document_id1, 1U << matching_section_id),
      DocHitInfo(document_id2, 1U << not_matching_section_id)};
  std::vector<DocHitInfo> right_infos = {
      DocHitInfo(document_id3, 1U << not_matching_section_id)};

  std::unique_ptr<DocHitInfoIterator> left_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(left_infos);
  std::unique_ptr<DocHitInfoIterator> right_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(right_infos, "term", 10);
  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorAnd>(std::move(left_iterator),
                                              std::move(right_iterator));

  // After applying section restriction:
  //                  AND
  //             /           \
  //        Restrict       Restrict
  //           |               |
  //    [1, 1],[2, 2]        [3, 2]
  std::unique_ptr<DocHitInfoIterator> section_restrict_iterator =
      DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
          std::move(original_iterator), document_store_.get(),
          schema_store_.get(), {indexed_section_0},
          fake_clock_.GetSystemTimeMilliseconds());

  // The trimmed tree.
  //          Restrict
  //             |
  //       [1, 1],[2, 2]
  ICING_ASSERT_OK_AND_ASSIGN(
      DocHitInfoIterator::TrimmedNode node,
      std::move(*section_restrict_iterator).TrimRightMostNode());

  EXPECT_THAT(GetDocumentIds(node.iterator_.get()), ElementsAre(document_id1));
  EXPECT_THAT(node.term_, Eq("term"));
  EXPECT_THAT(node.term_start_index_, Eq(10));
  EXPECT_THAT(node.target_section_, Eq(indexed_section_0));
}

TEST_F(DocHitInfoIteratorSectionRestrictTest, TrimSectionRestrictIterator) {
  // Populate the DocumentStore's FilterCache with this document's data
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document1_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document2_));

  // 0 is the indexed property
  SectionId matching_section_id = 0;
  // Anything that's not 0, which is the indexed property
  SectionId not_matching_section_id = 2;

  // Build an interator tree like:
  //                Restrict
  //                   |
  //             [1, 1],[2, 2]
  std::vector<DocHitInfo> doc_infos = {
      DocHitInfo(document_id1, 1U << matching_section_id),
      DocHitInfo(document_id2, 1U << not_matching_section_id)};
  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_infos, "term", 10);

  std::unique_ptr<DocHitInfoIterator> section_restrict_iterator =
      DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
          std::move(original_iterator), document_store_.get(),
          schema_store_.get(), {indexed_section_0},
          fake_clock_.GetSystemTimeMilliseconds());

  // The trimmed tree has null iterator but has target section.
  ICING_ASSERT_OK_AND_ASSIGN(
      DocHitInfoIterator::TrimmedNode node,
      std::move(*section_restrict_iterator).TrimRightMostNode());

  EXPECT_THAT(node.iterator_, testing::IsNull());
  EXPECT_THAT(node.term_, Eq("term"));
  EXPECT_THAT(node.term_start_index_, Eq(10));
  EXPECT_THAT(node.target_section_, Eq(indexed_section_0));
}

}  // namespace

}  // namespace lib
}  // namespace icing
