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

#include "icing/index/iterator/doc-hit-info-iterator-and.h"

#include <string>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator-test-util.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/testing/common-matchers.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;

TEST(CreateAndIteratorTest, And) {
  // Basic test that we can create a working And iterator. Further testing of
  // the And iterator should be done separately below.
  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(10)};
  std::unique_ptr<DocHitInfoIterator> first_iter =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);
  std::unique_ptr<DocHitInfoIterator> second_iter =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  std::vector<std::unique_ptr<DocHitInfoIterator>> iterators;
  iterators.push_back(std::move(first_iter));
  iterators.push_back(std::move(second_iter));
  std::unique_ptr<DocHitInfoIterator> and_iter =
      CreateAndIterator(std::move(iterators));

  EXPECT_THAT(GetDocumentIds(and_iter.get()), ElementsAre(10));
}

TEST(CreateAndIteratorTest, AndNary) {
  // Basic test that we can create a working AndNary iterator. Further testing
  // of the AndNary iterator should be done separately below.
  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(10)};
  std::vector<std::unique_ptr<DocHitInfoIterator>> iterators;
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos));
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos));
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos));
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos));

  std::unique_ptr<DocHitInfoIterator> and_iter =
      CreateAndIterator(std::move(iterators));

  EXPECT_THAT(GetDocumentIds(and_iter.get()), ElementsAre(10));
}

TEST(DocHitInfoIteratorAndTest, Initialize) {
  DocHitInfoIteratorAnd and_iter(std::make_unique<DocHitInfoIteratorDummy>(),
                                 std::make_unique<DocHitInfoIteratorDummy>());

  // We start out with invalid values
  EXPECT_THAT(and_iter.doc_hit_info(),
              EqualsDocHitInfo(kInvalidDocumentId, std::vector<SectionId>{}));
}

TEST(DocHitInfoIteratorAndTest, GetCallStats) {
  DocHitInfoIterator::CallStats first_iter_call_stats(
      /*num_leaf_advance_calls_lite_index_in=*/2,
      /*num_leaf_advance_calls_main_index_in=*/5,
      /*num_leaf_advance_calls_integer_index_in=*/3,
      /*num_leaf_advance_calls_no_index_in=*/1,
      /*num_blocks_inspected_in=*/4);  // arbitrary value
  auto first_iter = std::make_unique<DocHitInfoIteratorDummy>();
  first_iter->SetCallStats(first_iter_call_stats);

  DocHitInfoIterator::CallStats second_iter_call_stats(
      /*num_leaf_advance_calls_lite_index_in=*/6,
      /*num_leaf_advance_calls_main_index_in=*/2,
      /*num_leaf_advance_calls_integer_index_in=*/10,
      /*num_leaf_advance_calls_no_index_in=*/3,
      /*num_blocks_inspected_in=*/7);  // arbitrary value
  auto second_iter = std::make_unique<DocHitInfoIteratorDummy>();
  second_iter->SetCallStats(second_iter_call_stats);

  DocHitInfoIteratorAnd and_iter(std::move(first_iter), std::move(second_iter));

  EXPECT_THAT(and_iter.GetCallStats(),
              Eq(first_iter_call_stats + second_iter_call_stats));
}

TEST(DocHitInfoIteratorAndTest, AdvanceNoOverlap) {
  std::vector<DocHitInfo> first_vector = {DocHitInfo(10), DocHitInfo(9)};

  std::vector<DocHitInfo> second_vector = {DocHitInfo(8), DocHitInfo(7)};

  std::unique_ptr<DocHitInfoIterator> first_iter =
      std::make_unique<DocHitInfoIteratorDummy>(first_vector);
  std::unique_ptr<DocHitInfoIterator> second_iter =
      std::make_unique<DocHitInfoIteratorDummy>(second_vector);
  DocHitInfoIteratorAnd and_iter(std::move(first_iter), std::move(second_iter));

  EXPECT_THAT(GetDocumentIds(&and_iter), IsEmpty());
}

TEST(DocHitInfoIteratorAndTest, Advance) {
  std::vector<DocHitInfo> first_vector = {DocHitInfo(10), DocHitInfo(8),
                                          DocHitInfo(6),  DocHitInfo(4),
                                          DocHitInfo(2),  DocHitInfo(0)};

  std::vector<DocHitInfo> second_vector = {DocHitInfo(8), DocHitInfo(4),
                                           DocHitInfo(0)};

  std::unique_ptr<DocHitInfoIterator> first_iter =
      std::make_unique<DocHitInfoIteratorDummy>(first_vector);
  std::unique_ptr<DocHitInfoIterator> second_iter =
      std::make_unique<DocHitInfoIteratorDummy>(second_vector);
  DocHitInfoIteratorAnd and_iter(std::move(first_iter), std::move(second_iter));

  EXPECT_THAT(GetDocumentIds(&and_iter), ElementsAre(8, 4, 0));
}

TEST(DocHitInfoIteratorAndTest, AdvanceNestedIterators) {
  std::vector<DocHitInfo> first_vector = {
      DocHitInfo(10), DocHitInfo(9), DocHitInfo(8), DocHitInfo(7),
      DocHitInfo(6),  DocHitInfo(5), DocHitInfo(4), DocHitInfo(3),
      DocHitInfo(2),  DocHitInfo(1), DocHitInfo(0)};

  std::vector<DocHitInfo> second_vector = {DocHitInfo(10), DocHitInfo(8),
                                           DocHitInfo(6),  DocHitInfo(4),
                                           DocHitInfo(2),  DocHitInfo(0)};

  std::vector<DocHitInfo> third_vector = {DocHitInfo(10), DocHitInfo(7),
                                          DocHitInfo(6), DocHitInfo(2),
                                          DocHitInfo(1)};

  std::unique_ptr<DocHitInfoIterator> first_iter =
      std::make_unique<DocHitInfoIteratorDummy>(first_vector);
  std::unique_ptr<DocHitInfoIterator> second_iter =
      std::make_unique<DocHitInfoIteratorDummy>(second_vector);
  std::unique_ptr<DocHitInfoIterator> third_iter =
      std::make_unique<DocHitInfoIteratorDummy>(third_vector);

  std::unique_ptr<DocHitInfoIterator> inner_iter =
      std::make_unique<DocHitInfoIteratorAnd>(std::move(first_iter),
                                              std::move(second_iter));
  std::unique_ptr<DocHitInfoIterator> outer_iter =
      std::make_unique<DocHitInfoIteratorAnd>(std::move(inner_iter),
                                              std::move(third_iter));

  EXPECT_THAT(GetDocumentIds(outer_iter.get()), ElementsAre(10, 6, 2));
}

TEST(DocHitInfoIteratorAndTest, TrimAndIterator) {
  std::vector<DocHitInfo> left_vector = {DocHitInfo(3), DocHitInfo(2)};
  std::vector<DocHitInfo> right_vector = {DocHitInfo(1), DocHitInfo(0)};

  std::unique_ptr<DocHitInfoIterator> left_iter =
      std::make_unique<DocHitInfoIteratorDummy>(left_vector);
  std::unique_ptr<DocHitInfoIterator> right_iter =
      std::make_unique<DocHitInfoIteratorDummy>(right_vector, "term", 10);

  std::unique_ptr<DocHitInfoIterator> iter =
      std::make_unique<DocHitInfoIteratorAnd>(std::move(left_iter),
                                              std::move(right_iter));

  ICING_ASSERT_OK_AND_ASSIGN(DocHitInfoIterator::TrimmedNode trimmed_node,
                             std::move(*iter).TrimRightMostNode());
  EXPECT_THAT(trimmed_node.term_, Eq("term"));
  EXPECT_THAT(trimmed_node.term_start_index_, Eq(10));
  EXPECT_THAT(GetDocumentIds(trimmed_node.iterator_.get()), ElementsAre(3, 2));
}

TEST(DocHitInfoIteratorAndTest, TrimAndIterator_TwoLayer) {
  // Build an interator tree like:
  //
  //            AND
  //          /     \
  //     first         AND
  //        |        /      \
  //    {0, 1}    second  third
  //                 |      |
  //                {1}    {0}
  std::vector<DocHitInfo> first_vector = {DocHitInfo(1), DocHitInfo(0)};
  std::vector<DocHitInfo> second_vector = {DocHitInfo(1)};
  std::vector<DocHitInfo> third_vector = {DocHitInfo(0)};

  std::unique_ptr<DocHitInfoIterator> first_iter =
      std::make_unique<DocHitInfoIteratorDummy>(first_vector);
  std::unique_ptr<DocHitInfoIterator> second_iter =
      std::make_unique<DocHitInfoIteratorDummy>(second_vector);
  std::unique_ptr<DocHitInfoIterator> third_iter =
      std::make_unique<DocHitInfoIteratorDummy>(third_vector, "term", 10);

  std::unique_ptr<DocHitInfoIterator> nested_iter =
      std::make_unique<DocHitInfoIteratorAnd>(std::move(second_iter),
                                              std::move(third_iter));
  std::unique_ptr<DocHitInfoIterator> iter =
      std::make_unique<DocHitInfoIteratorAnd>(std::move(first_iter),
                                              std::move(nested_iter));

  // The third_iter is trimmed.
  //          AND
  //      /        \
  //    first    second
  //      |         |
  //   {0, 1}       {1}
  ICING_ASSERT_OK_AND_ASSIGN(DocHitInfoIterator::TrimmedNode trimmed_node,
                             std::move(*iter).TrimRightMostNode());
  EXPECT_THAT(GetDocumentIds(trimmed_node.iterator_.get()), ElementsAre(1));
  EXPECT_THAT(trimmed_node.term_, Eq("term"));
  EXPECT_THAT(trimmed_node.term_start_index_, Eq(10));
}

TEST(DocHitInfoIteratorAndNaryTest, TrimAndNaryIterator) {
  std::vector<DocHitInfo> first_vector = {DocHitInfo(2), DocHitInfo(1),
                                          DocHitInfo(0)};
  std::vector<DocHitInfo> second_vector = {DocHitInfo(2), DocHitInfo(1)};
  std::vector<DocHitInfo> third_vector = {DocHitInfo(2)};

  std::vector<std::unique_ptr<DocHitInfoIterator>> iterators;
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>(first_vector));
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>(second_vector));
  iterators.push_back(
      std::make_unique<DocHitInfoIteratorDummy>(third_vector, "term", 10));

  std::unique_ptr<DocHitInfoIterator> iter =
      std::make_unique<DocHitInfoIteratorAndNary>(std::move(iterators));

  // The third iterator is trimmed
  ICING_ASSERT_OK_AND_ASSIGN(DocHitInfoIterator::TrimmedNode trimmed_node,
                             std::move(*iter).TrimRightMostNode());
  EXPECT_THAT(trimmed_node.term_, Eq("term"));
  EXPECT_THAT(trimmed_node.term_start_index_, Eq(10));
  EXPECT_THAT(GetDocumentIds(trimmed_node.iterator_.get()), ElementsAre(2, 1));
}

TEST(DocHitInfoIteratorAndNaryTest, TrimAndNaryIterator_TwoLayer) {
  std::vector<DocHitInfo> first_vector = {DocHitInfo(3), DocHitInfo(2),
                                          DocHitInfo(1), DocHitInfo(0)};
  std::vector<DocHitInfo> second_vector = {DocHitInfo(2), DocHitInfo(1),
                                           DocHitInfo(0)};
  std::vector<DocHitInfo> third_vector = {DocHitInfo(1), DocHitInfo(0)};
  std::vector<DocHitInfo> forth_vector = {DocHitInfo(0)};

  // Build nested iterator
  std::unique_ptr<DocHitInfoIterator> third_iter =
      std::make_unique<DocHitInfoIteratorDummy>(third_vector);
  std::unique_ptr<DocHitInfoIterator> forth_iter =
      std::make_unique<DocHitInfoIteratorDummy>(forth_vector, "term", 10);
  std::unique_ptr<DocHitInfoIterator> nested_iter =
      std::make_unique<DocHitInfoIteratorAnd>(std::move(third_iter),
                                              std::move(forth_iter));

  // Build outer iterator
  std::vector<std::unique_ptr<DocHitInfoIterator>> iterators;
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>(first_vector));
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>(second_vector));
  iterators.push_back(std::move(nested_iter));
  std::unique_ptr<DocHitInfoIterator> iter =
      std::make_unique<DocHitInfoIteratorAndNary>(std::move(iterators));

  // The forth iterator is trimmed.
  ICING_ASSERT_OK_AND_ASSIGN(DocHitInfoIterator::TrimmedNode trimmed_node,
                             std::move(*iter).TrimRightMostNode());
  EXPECT_THAT(trimmed_node.term_, Eq("term"));
  EXPECT_THAT(trimmed_node.term_start_index_, Eq(10));
  EXPECT_THAT(GetDocumentIds(trimmed_node.iterator_.get()), ElementsAre(1, 0));
}

TEST(DocHitInfoIteratorAndTest, SectionIdMask) {
  // Arbitrary section ids for the documents in the DocHitInfoIterators.
  // Created to test correct section_id_mask behavior.
  SectionIdMask section_id_mask1 = 0b01010101;  // hits in sections 0, 2, 4, 6
  SectionIdMask section_id_mask2 = 0b00000110;  // hits in sections 1, 2
  SectionIdMask mask_ored_result = 0b01010111;

  std::vector<DocHitInfo> first_vector = {DocHitInfo(4, section_id_mask1)};
  std::vector<DocHitInfo> second_vector = {DocHitInfo(4, section_id_mask2)};

  auto first_iter = std::make_unique<DocHitInfoIteratorDummy>(first_vector);
  first_iter->set_hit_section_ids_mask(section_id_mask1);

  auto second_iter = std::make_unique<DocHitInfoIteratorDummy>(second_vector);
  second_iter->set_hit_section_ids_mask(section_id_mask2);

  DocHitInfoIteratorAnd and_iter(std::move(first_iter), std::move(second_iter));

  ICING_EXPECT_OK(and_iter.Advance());
  EXPECT_THAT(and_iter.doc_hit_info().hit_section_ids_mask(),
              Eq(mask_ored_result));
}

TEST(DocHitInfoIteratorAndTest, PopulateMatchedTermsStats) {
  {
    // Arbitrary section ids for the documents in the DocHitInfoIterators.
    // Created to test correct section_id_mask behavior.
    DocHitInfoTermFrequencyPair doc_hit_info1 = DocHitInfo(4);
    doc_hit_info1.UpdateSection(/*section_id=*/0, /*hit_term_frequency=*/1);
    doc_hit_info1.UpdateSection(/*section_id=*/2, /*hit_term_frequency=*/2);
    doc_hit_info1.UpdateSection(/*section_id=*/4, /*hit_term_frequency=*/3);
    doc_hit_info1.UpdateSection(/*section_id=*/6, /*hit_term_frequency=*/4);

    SectionIdMask section_id_mask1 = 0b01010101;  // hits in sections 0, 2, 4, 6
    std::unordered_map<SectionId, Hit::TermFrequency>
        expected_section_ids_tf_map1 = {{0, 1}, {2, 2}, {4, 3}, {6, 4}};

    DocHitInfoTermFrequencyPair doc_hit_info2 = DocHitInfo(4);
    doc_hit_info2.UpdateSection(/*section_id=*/1, /*hit_term_frequency=*/2);
    doc_hit_info2.UpdateSection(/*section_id=*/2, /*hit_term_frequency=*/6);

    SectionIdMask section_id_mask2 = 0b00000110;  // hits in sections 1, 2
    std::unordered_map<SectionId, Hit::TermFrequency>
        expected_section_ids_tf_map2 = {{1, 2}, {2, 6}};

    std::vector<DocHitInfoTermFrequencyPair> first_vector = {doc_hit_info1};
    std::vector<DocHitInfoTermFrequencyPair> second_vector = {doc_hit_info2};

    auto first_iter =
        std::make_unique<DocHitInfoIteratorDummy>(first_vector, "hi");
    first_iter->set_hit_section_ids_mask(section_id_mask1);

    auto second_iter =
        std::make_unique<DocHitInfoIteratorDummy>(second_vector, "hello");
    second_iter->set_hit_section_ids_mask(section_id_mask2);

    DocHitInfoIteratorAnd and_iter(std::move(first_iter),
                                   std::move(second_iter));
    std::vector<TermMatchInfo> matched_terms_stats;
    and_iter.PopulateMatchedTermsStats(&matched_terms_stats);
    EXPECT_THAT(matched_terms_stats, IsEmpty());

    ICING_EXPECT_OK(and_iter.Advance());
    EXPECT_THAT(and_iter.doc_hit_info().document_id(), Eq(4));

    and_iter.PopulateMatchedTermsStats(&matched_terms_stats);
    EXPECT_THAT(
        matched_terms_stats,
        ElementsAre(
            EqualsTermMatchInfo("hi", expected_section_ids_tf_map1),
            EqualsTermMatchInfo("hello", expected_section_ids_tf_map2)));

    EXPECT_FALSE(and_iter.Advance().ok());
  }
  {
    // Arbitrary section ids for the documents in the DocHitInfoIterators.
    // Created to test correct section_id_mask behavior.
    DocHitInfoTermFrequencyPair doc_hit_info1 = DocHitInfo(4);
    doc_hit_info1.UpdateSection(/*section_id=*/0, /*hit_term_frequency=*/1);
    doc_hit_info1.UpdateSection(/*section_id=*/2, /*hit_term_frequency=*/2);

    SectionIdMask section_id_mask1 = 0b00000101;  // hits in sections 0, 2
    std::unordered_map<SectionId, Hit::TermFrequency>
        expected_section_ids_tf_map1 = {{0, 1}, {2, 2}};

    std::vector<DocHitInfoTermFrequencyPair> first_vector = {doc_hit_info1};
    std::vector<DocHitInfoTermFrequencyPair> second_vector = {doc_hit_info1};

    auto first_iter =
        std::make_unique<DocHitInfoIteratorDummy>(first_vector, "hi");
    first_iter->set_hit_section_ids_mask(section_id_mask1);

    auto second_iter =
        std::make_unique<DocHitInfoIteratorDummy>(second_vector, "hi");
    second_iter->set_hit_section_ids_mask(section_id_mask1);

    DocHitInfoIteratorAnd and_iter(std::move(first_iter),
                                   std::move(second_iter));
    std::vector<TermMatchInfo> matched_terms_stats;
    and_iter.PopulateMatchedTermsStats(&matched_terms_stats);
    EXPECT_THAT(matched_terms_stats, IsEmpty());

    ICING_EXPECT_OK(and_iter.Advance());
    EXPECT_THAT(and_iter.doc_hit_info().document_id(), Eq(4));

    and_iter.PopulateMatchedTermsStats(&matched_terms_stats);
    EXPECT_THAT(matched_terms_stats, ElementsAre(EqualsTermMatchInfo(
                                         "hi", expected_section_ids_tf_map1)));

    EXPECT_FALSE(and_iter.Advance().ok());
  }
}

TEST(DocHitInfoIteratorAndTest, PopulateMatchedTermsStats_NoMatchingDocument) {
  DocHitInfoTermFrequencyPair doc_hit_info1 = DocHitInfo(4);
  doc_hit_info1.UpdateSection(/*section_id=*/0, /*hit_term_frequency=*/1);

  DocHitInfoTermFrequencyPair doc_hit_info2 = DocHitInfo(5);
  doc_hit_info2.UpdateSection(/*section_id=*/1, /*hit_term_frequency=*/2);
  doc_hit_info2.UpdateSection(/*section_id=*/2, /*hit_term_frequency=*/6);

  std::vector<DocHitInfoTermFrequencyPair> first_vector = {doc_hit_info1};
  std::vector<DocHitInfoTermFrequencyPair> second_vector = {doc_hit_info2};

  auto first_iter =
      std::make_unique<DocHitInfoIteratorDummy>(first_vector, "hi");
  auto second_iter =
      std::make_unique<DocHitInfoIteratorDummy>(second_vector, "hello");

  DocHitInfoIteratorAnd and_iter(std::move(first_iter), std::move(second_iter));
  std::vector<TermMatchInfo> matched_terms_stats;
  and_iter.PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, IsEmpty());
  EXPECT_FALSE(and_iter.Advance().ok());
}

TEST(DocHitInfoIteratorAndNaryTest, Initialize) {
  std::vector<std::unique_ptr<DocHitInfoIterator>> iterators;
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>());
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>());
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>());
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>());
  DocHitInfoIteratorAndNary and_iter(std::move(iterators));

  // We start out with invalid values
  EXPECT_THAT(and_iter.doc_hit_info(),
              EqualsDocHitInfo(kInvalidDocumentId, std::vector<SectionId>{}));
}

TEST(DocHitInfoIteratorAndNaryTest, InitializeEmpty) {
  // We can initialize it fine even with an empty vector
  std::vector<std::unique_ptr<DocHitInfoIterator>> empty_vector;
  DocHitInfoIteratorAndNary empty_iter(std::move(empty_vector));

  // But it won't be able to advance anywhere
  EXPECT_THAT(empty_iter.Advance(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST(DocHitInfoIteratorAndNaryTest, GetCallStats) {
  DocHitInfoIterator::CallStats first_iter_call_stats(
      /*num_leaf_advance_calls_lite_index_in=*/2,
      /*num_leaf_advance_calls_main_index_in=*/5,
      /*num_leaf_advance_calls_integer_index_in=*/3,
      /*num_leaf_advance_calls_no_index_in=*/1,
      /*num_blocks_inspected_in=*/4);  // arbitrary value
  auto first_iter = std::make_unique<DocHitInfoIteratorDummy>();
  first_iter->SetCallStats(first_iter_call_stats);

  DocHitInfoIterator::CallStats second_iter_call_stats(
      /*num_leaf_advance_calls_lite_index_in=*/6,
      /*num_leaf_advance_calls_main_index_in=*/2,
      /*num_leaf_advance_calls_integer_index_in=*/10,
      /*num_leaf_advance_calls_no_index_in=*/3,
      /*num_blocks_inspected_in=*/7);  // arbitrary value
  auto second_iter = std::make_unique<DocHitInfoIteratorDummy>();
  second_iter->SetCallStats(second_iter_call_stats);

  DocHitInfoIterator::CallStats third_iter_call_stats(
      /*num_leaf_advance_calls_lite_index_in=*/1000,
      /*num_leaf_advance_calls_main_index_in=*/2000,
      /*num_leaf_advance_calls_integer_index_in=*/3000,
      /*num_leaf_advance_calls_no_index_in=*/0,
      /*num_blocks_inspected_in=*/200);  // arbitrary value
  auto third_iter = std::make_unique<DocHitInfoIteratorDummy>();
  third_iter->SetCallStats(third_iter_call_stats);

  DocHitInfoIterator::CallStats fourth_iter_call_stats(
      /*num_leaf_advance_calls_lite_index_in=*/200,
      /*num_leaf_advance_calls_main_index_in=*/400,
      /*num_leaf_advance_calls_integer_index_in=*/100,
      /*num_leaf_advance_calls_no_index_in=*/20,
      /*num_blocks_inspected_in=*/50);  // arbitrary value
  auto fourth_iter = std::make_unique<DocHitInfoIteratorDummy>();
  fourth_iter->SetCallStats(fourth_iter_call_stats);

  std::vector<std::unique_ptr<DocHitInfoIterator>> iterators;
  iterators.push_back(std::move(first_iter));
  iterators.push_back(std::move(second_iter));
  iterators.push_back(std::move(third_iter));
  iterators.push_back(std::move(fourth_iter));
  DocHitInfoIteratorAndNary and_iter(std::move(iterators));

  EXPECT_THAT(and_iter.GetCallStats(),
              Eq(first_iter_call_stats + second_iter_call_stats +
                 third_iter_call_stats + fourth_iter_call_stats));
}

TEST(DocHitInfoIteratorAndNaryTest, Advance) {
  std::vector<DocHitInfo> first_vector = {
      DocHitInfo(10), DocHitInfo(9), DocHitInfo(8), DocHitInfo(7),
      DocHitInfo(6),  DocHitInfo(5), DocHitInfo(4), DocHitInfo(3),
      DocHitInfo(2),  DocHitInfo(1), DocHitInfo(0)};

  std::vector<DocHitInfo> second_vector = {DocHitInfo(10), DocHitInfo(8),
                                           DocHitInfo(6),  DocHitInfo(4),
                                           DocHitInfo(2),  DocHitInfo(0)};

  std::vector<DocHitInfo> third_vector = {DocHitInfo(9), DocHitInfo(6),
                                          DocHitInfo(3), DocHitInfo(0)};

  std::vector<DocHitInfo> fourth_vector = {DocHitInfo(6), DocHitInfo(5),
                                           DocHitInfo(0)};

  std::vector<std::unique_ptr<DocHitInfoIterator>> iterators;
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>(first_vector));
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>(second_vector));
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>(third_vector));
  iterators.push_back(std::make_unique<DocHitInfoIteratorDummy>(fourth_vector));
  DocHitInfoIteratorAndNary and_iter(std::move(iterators));

  EXPECT_THAT(GetDocumentIds(&and_iter), ElementsAre(6, 0));
}

TEST(DocHitInfoIteratorAndNaryTest, SectionIdMask) {
  // Arbitrary section ids for the documents in the DocHitInfoIterators.
  // Created to test correct section_id_mask behavior.
  SectionIdMask section_id_mask1 = 0b01000101;  // hits in sections 0, 2, 6
  SectionIdMask section_id_mask2 = 0b00000110;  // hits in sections 1, 2
  SectionIdMask section_id_mask3 = 0b00001100;  // hits in sections 2, 3
  SectionIdMask section_id_mask4 = 0b00100100;  // hits in sections 2, 5
  SectionIdMask mask_ored_result = 0b01101111;

  std::vector<DocHitInfo> first_vector = {DocHitInfo(4, section_id_mask1)};
  std::vector<DocHitInfo> second_vector = {DocHitInfo(4, section_id_mask2)};
  std::vector<DocHitInfo> third_vector = {DocHitInfo(4, section_id_mask3)};
  std::vector<DocHitInfo> fourth_vector = {DocHitInfo(4, section_id_mask4)};

  auto first_iter = std::make_unique<DocHitInfoIteratorDummy>(first_vector);
  first_iter->set_hit_section_ids_mask(section_id_mask1);

  auto second_iter = std::make_unique<DocHitInfoIteratorDummy>(second_vector);
  second_iter->set_hit_section_ids_mask(section_id_mask2);

  auto third_iter = std::make_unique<DocHitInfoIteratorDummy>(third_vector);
  third_iter->set_hit_section_ids_mask(section_id_mask3);

  auto fourth_iter = std::make_unique<DocHitInfoIteratorDummy>(fourth_vector);
  fourth_iter->set_hit_section_ids_mask(section_id_mask4);

  std::vector<std::unique_ptr<DocHitInfoIterator>> iterators;
  iterators.push_back(std::move(first_iter));
  iterators.push_back(std::move(second_iter));
  iterators.push_back(std::move(third_iter));
  iterators.push_back(std::move(fourth_iter));

  DocHitInfoIteratorAndNary and_iter(std::move(iterators));

  ICING_EXPECT_OK(and_iter.Advance());
  EXPECT_THAT(and_iter.doc_hit_info().hit_section_ids_mask(),
              Eq(mask_ored_result));
}

TEST(DocHitInfoIteratorAndNaryTest, PopulateMatchedTermsStats) {
  // Arbitrary section ids/term frequencies for the documents in the
  // DocHitInfoIterators.
  // For term "hi", document 10 and 8
  DocHitInfoTermFrequencyPair doc_hit_info1_hi = DocHitInfo(10);
  doc_hit_info1_hi.UpdateSection(/*section_id=*/0, /*hit_term_frequency=*/1);
  doc_hit_info1_hi.UpdateSection(/*section_id=*/2, /*hit_term_frequency=*/2);
  doc_hit_info1_hi.UpdateSection(/*section_id=*/6, /*hit_term_frequency=*/4);
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map1_hi = {{0, 1}, {2, 2}, {6, 4}};

  DocHitInfoTermFrequencyPair doc_hit_info2_hi = DocHitInfo(8);
  doc_hit_info2_hi.UpdateSection(/*section_id=*/1, /*hit_term_frequency=*/2);
  doc_hit_info2_hi.UpdateSection(/*section_id=*/2, /*hit_term_frequency=*/6);

  // For term "hello", document 10 and 9
  DocHitInfoTermFrequencyPair doc_hit_info1_hello = DocHitInfo(10);
  doc_hit_info1_hello.UpdateSection(/*section_id=*/0, /*hit_term_frequency=*/2);
  doc_hit_info1_hello.UpdateSection(/*section_id=*/3, /*hit_term_frequency=*/3);
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map1_hello = {{0, 2}, {3, 3}};

  DocHitInfoTermFrequencyPair doc_hit_info2_hello = DocHitInfo(9);
  doc_hit_info2_hello.UpdateSection(/*section_id=*/2, /*hit_term_frequency=*/3);
  doc_hit_info2_hello.UpdateSection(/*section_id=*/3, /*hit_term_frequency=*/2);

  // For term "ciao", document 10 and 9
  DocHitInfoTermFrequencyPair doc_hit_info1_ciao = DocHitInfo(10);
  doc_hit_info1_ciao.UpdateSection(/*section_id=*/0, /*hit_term_frequency=*/2);
  doc_hit_info1_ciao.UpdateSection(/*section_id=*/1, /*hit_term_frequency=*/3);
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map1_ciao = {{0, 2}, {1, 3}};

  DocHitInfoTermFrequencyPair doc_hit_info2_ciao = DocHitInfo(9);
  doc_hit_info2_ciao.UpdateSection(/*section_id=*/3, /*hit_term_frequency=*/3);
  doc_hit_info2_ciao.UpdateSection(/*section_id=*/4, /*hit_term_frequency=*/2);

  std::vector<DocHitInfoTermFrequencyPair> first_vector = {doc_hit_info1_hi,
                                                           doc_hit_info2_hi};
  std::vector<DocHitInfoTermFrequencyPair> second_vector = {
      doc_hit_info1_hello, doc_hit_info2_hello};
  std::vector<DocHitInfoTermFrequencyPair> third_vector = {doc_hit_info1_ciao,
                                                           doc_hit_info2_ciao};

  auto first_iter =
      std::make_unique<DocHitInfoIteratorDummy>(first_vector, "hi");
  auto second_iter =
      std::make_unique<DocHitInfoIteratorDummy>(second_vector, "hello");
  auto third_iter =
      std::make_unique<DocHitInfoIteratorDummy>(third_vector, "ciao");

  std::vector<std::unique_ptr<DocHitInfoIterator>> iterators;
  iterators.push_back(std::move(first_iter));
  iterators.push_back(std::move(second_iter));
  iterators.push_back(std::move(third_iter));

  DocHitInfoIteratorAndNary and_iter(std::move(iterators));
  std::vector<TermMatchInfo> matched_terms_stats;
  and_iter.PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, IsEmpty());

  ICING_EXPECT_OK(and_iter.Advance());
  EXPECT_THAT(and_iter.doc_hit_info().document_id(), Eq(10));

  and_iter.PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(
      matched_terms_stats,
      ElementsAre(
          EqualsTermMatchInfo("hi", expected_section_ids_tf_map1_hi),
          EqualsTermMatchInfo("hello", expected_section_ids_tf_map1_hello),
          EqualsTermMatchInfo("ciao", expected_section_ids_tf_map1_ciao)));

  EXPECT_FALSE(and_iter.Advance().ok());
}

}  // namespace

}  // namespace lib
}  // namespace icing
