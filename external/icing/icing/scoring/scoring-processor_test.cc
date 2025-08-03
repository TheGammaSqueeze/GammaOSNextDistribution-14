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

#include "icing/scoring/scoring-processor.h"

#include <cstdint>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/index/iterator/doc-hit-info-iterator-test-util.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/scoring.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/proto/usage.pb.h"
#include "icing/schema-builder.h"
#include "icing/scoring/scorer-test-utils.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {
using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::Gt;
using ::testing::IsEmpty;
using ::testing::SizeIs;

class ScoringProcessorTest
    : public ::testing::TestWithParam<ScorerTestingMode> {
 protected:
  ScoringProcessorTest()
      : test_dir_(GetTestTempDir() + "/icing"),
        doc_store_dir_(test_dir_ + "/doc_store"),
        schema_store_dir_(test_dir_ + "/schema_store") {}

  void SetUp() override {
    // Creates file directories
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
    filesystem_.CreateDirectoryRecursively(doc_store_dir_.c_str());
    filesystem_.CreateDirectoryRecursively(schema_store_dir_.c_str());

    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult create_result,
        DocumentStore::Create(
            &filesystem_, doc_store_dir_, &fake_clock_, schema_store_.get(),
            /*force_recovery_and_revalidate_documents=*/false,
            /*namespace_id_fingerprint=*/false, /*pre_mapping_fbv=*/false,
            /*use_persistent_hash_map=*/false,
            PortableFileBackedProtoLog<
                DocumentWrapper>::kDeflateCompressionLevel,
            /*initialize_stats=*/nullptr));
    document_store_ = std::move(create_result.document_store);

    // Creates a simple email schema
    SchemaProto test_email_schema =
        SchemaBuilder()
            .AddType(SchemaTypeConfigBuilder()
                         .SetType("email")
                         .AddProperty(
                             PropertyConfigBuilder()
                                 .SetName("subject")
                                 .SetDataTypeString(
                                     TermMatchType::PREFIX,
                                     StringIndexingConfig::TokenizerType::PLAIN)
                                 .SetDataType(TYPE_STRING)
                                 .SetCardinality(CARDINALITY_OPTIONAL))
                         .AddProperty(
                             PropertyConfigBuilder()
                                 .SetName("body")
                                 .SetDataTypeString(
                                     TermMatchType::PREFIX,
                                     StringIndexingConfig::TokenizerType::PLAIN)
                                 .SetDataType(TYPE_STRING)
                                 .SetCardinality(CARDINALITY_OPTIONAL)))
            .Build();
    ICING_ASSERT_OK(schema_store_->SetSchema(
        test_email_schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));
  }

  void TearDown() override {
    document_store_.reset();
    schema_store_.reset();
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  DocumentStore* document_store() { return document_store_.get(); }

  SchemaStore* schema_store() { return schema_store_.get(); }

  const FakeClock& fake_clock() const { return fake_clock_; }

 private:
  const std::string test_dir_;
  const std::string doc_store_dir_;
  const std::string schema_store_dir_;
  Filesystem filesystem_;
  FakeClock fake_clock_;
  std::unique_ptr<DocumentStore> document_store_;
  std::unique_ptr<SchemaStore> schema_store_;
};

constexpr int kDefaultScore = 0;
constexpr int64_t kDefaultCreationTimestampMs = 1571100001111;

DocumentProto CreateDocument(const std::string& name_space,
                             const std::string& uri, int score,
                             int64_t creation_timestamp_ms) {
  return DocumentBuilder()
      .SetKey(name_space, uri)
      .SetSchema("email")
      .SetScore(score)
      .SetCreationTimestampMs(creation_timestamp_ms)
      .Build();
}

libtextclassifier3::StatusOr<
    std::pair<std::vector<DocHitInfo>, std::vector<ScoredDocumentHit>>>
CreateAndInsertsDocumentsWithScores(DocumentStore* document_store,
                                    const std::vector<int>& scores) {
  std::vector<DocHitInfo> doc_hit_infos;
  std::vector<ScoredDocumentHit> scored_document_hits;
  for (int i = 0; i < scores.size(); i++) {
    ICING_ASSIGN_OR_RETURN(DocumentId document_id,
                           document_store->Put(CreateDocument(
                               "icing", "email/" + std::to_string(i),
                               scores.at(i), kDefaultCreationTimestampMs)));
    doc_hit_infos.emplace_back(document_id);
    scored_document_hits.emplace_back(document_id, kSectionIdMaskNone,
                                      scores.at(i));
  }
  return std::pair(doc_hit_infos, scored_document_hits);
}

UsageReport CreateUsageReport(std::string name_space, std::string uri,
                              int64_t timestamp_ms,
                              UsageReport::UsageType usage_type) {
  UsageReport usage_report;
  usage_report.set_document_namespace(name_space);
  usage_report.set_document_uri(uri);
  usage_report.set_usage_timestamp_ms(timestamp_ms);
  usage_report.set_usage_type(usage_type);
  return usage_report;
}

TypePropertyWeights CreateTypePropertyWeights(
    std::string schema_type, std::vector<PropertyWeight> property_weights) {
  TypePropertyWeights type_property_weights;
  type_property_weights.set_schema_type(std::move(schema_type));
  type_property_weights.mutable_property_weights()->Reserve(
      property_weights.size());

  for (PropertyWeight& property_weight : property_weights) {
    *type_property_weights.add_property_weights() = std::move(property_weight);
  }

  return type_property_weights;
}

PropertyWeight CreatePropertyWeight(std::string path, double weight) {
  PropertyWeight property_weight;
  property_weight.set_path(std::move(path));
  property_weight.set_weight(weight);
  return property_weight;
}

TEST_F(ScoringProcessorTest, CreationWithNullDocumentStoreShouldFail) {
  ScoringSpecProto spec_proto;
  EXPECT_THAT(ScoringProcessor::Create(
                  spec_proto, /*document_store=*/nullptr, schema_store(),
                  fake_clock().GetSystemTimeMilliseconds()),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
}

TEST_F(ScoringProcessorTest, CreationWithNullSchemaStoreShouldFail) {
  ScoringSpecProto spec_proto;
  EXPECT_THAT(
      ScoringProcessor::Create(spec_proto, document_store(),
                               /*schema_store=*/nullptr,
                               fake_clock().GetSystemTimeMilliseconds()),
      StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
}

TEST_P(ScoringProcessorTest, ShouldCreateInstance) {
  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE, GetParam());
  ICING_EXPECT_OK(
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));
}

TEST_P(ScoringProcessorTest, ShouldHandleEmptyDocHitIterator) {
  // Creates an empty DocHitInfoIterator
  std::vector<DocHitInfo> doc_hit_infos = {};
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE, GetParam());

  // Creates a ScoringProcessor
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  EXPECT_THAT(scoring_processor->Score(std::move(doc_hit_info_iterator),
                                       /*num_to_score=*/5),
              IsEmpty());
}

TEST_P(ScoringProcessorTest, ShouldHandleNonPositiveNumToScore) {
  // Sets up documents
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store()->Put(CreateDocument("icing", "email/1", /*score=*/1,
                                           kDefaultCreationTimestampMs)));
  DocHitInfo doc_hit_info1(document_id1);

  // Creates a dummy DocHitInfoIterator
  std::vector<DocHitInfo> doc_hit_infos = {doc_hit_info1};
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE, GetParam());

  // Creates a ScoringProcessor
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  EXPECT_THAT(scoring_processor->Score(std::move(doc_hit_info_iterator),
                                       /*num_to_score=*/-1),
              IsEmpty());

  doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);
  EXPECT_THAT(scoring_processor->Score(std::move(doc_hit_info_iterator),
                                       /*num_to_score=*/0),
              IsEmpty());
}

TEST_P(ScoringProcessorTest, ShouldRespectNumToScore) {
  // Sets up documents
  ICING_ASSERT_OK_AND_ASSIGN(
      auto doc_hit_result_pair,
      CreateAndInsertsDocumentsWithScores(document_store(), {1, 2, 3}));
  std::vector<DocHitInfo> doc_hit_infos = std::move(doc_hit_result_pair.first);

  // Creates a dummy DocHitInfoIterator with 3 results
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE, GetParam());

  // Creates a ScoringProcessor
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  EXPECT_THAT(scoring_processor->Score(std::move(doc_hit_info_iterator),
                                       /*num_to_score=*/2),
              SizeIs(2));

  doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);
  EXPECT_THAT(scoring_processor->Score(std::move(doc_hit_info_iterator),
                                       /*num_to_score=*/4),
              SizeIs(3));
}

TEST_P(ScoringProcessorTest, ShouldScoreByDocumentScore) {
  // Creates input doc_hit_infos and expected output scored_document_hits
  ICING_ASSERT_OK_AND_ASSIGN(
      auto doc_hit_result_pair,
      CreateAndInsertsDocumentsWithScores(document_store(), {1, 3, 2}));
  std::vector<DocHitInfo> doc_hit_infos = std::move(doc_hit_result_pair.first);
  std::vector<ScoredDocumentHit> scored_document_hits =
      std::move(doc_hit_result_pair.second);

  // Creates a dummy DocHitInfoIterator with 3 results
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE, GetParam());

  // Creates a ScoringProcessor
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  EXPECT_THAT(scoring_processor->Score(std::move(doc_hit_info_iterator),
                                       /*num_to_score=*/3),
              ElementsAre(EqualsScoredDocumentHit(scored_document_hits.at(0)),
                          EqualsScoredDocumentHit(scored_document_hits.at(1)),
                          EqualsScoredDocumentHit(scored_document_hits.at(2))));
}

TEST_P(ScoringProcessorTest,
       ShouldScoreByRelevanceScore_DocumentsWithDifferentLength) {
  DocumentProto document1 =
      CreateDocument("icing", "email/1", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);
  DocumentProto document2 =
      CreateDocument("icing", "email/2", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);
  DocumentProto document3 =
      CreateDocument("icing", "email/3", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store()->Put(document1, /*num_tokens=*/10));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store()->Put(document2, /*num_tokens=*/100));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id3,
      document_store()->Put(document3, /*num_tokens=*/50));

  DocHitInfoTermFrequencyPair doc_hit_info1 = DocHitInfo(document_id1);
  doc_hit_info1.UpdateSection(/*section_id*/ 0, /*hit_term_frequency=*/1);
  DocHitInfoTermFrequencyPair doc_hit_info2 = DocHitInfo(document_id2);
  doc_hit_info2.UpdateSection(/*section_id*/ 0, /*hit_term_frequency=*/1);
  DocHitInfoTermFrequencyPair doc_hit_info3 = DocHitInfo(document_id3);
  doc_hit_info3.UpdateSection(/*section_id*/ 0, /*hit_term_frequency=*/1);

  SectionId section_id = 0;
  SectionIdMask section_id_mask = UINT64_C(1) << section_id;

  // Creates input doc_hit_infos and expected output scored_document_hits
  std::vector<DocHitInfoTermFrequencyPair> doc_hit_infos = {
      doc_hit_info1, doc_hit_info2, doc_hit_info3};

  // Creates a dummy DocHitInfoIterator with 3 results for the query "foo"
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE, GetParam());

  // Creates a ScoringProcessor
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>
      query_term_iterators;
  query_term_iterators["foo"] =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");
  // Since the three documents all contain the query term "foo" exactly once,
  // the document's length determines the final score. Document shorter than the
  // average corpus length are slightly boosted.
  ScoredDocumentHit expected_scored_doc_hit1(document_id1, section_id_mask,
                                             /*score=*/0.187114);
  ScoredDocumentHit expected_scored_doc_hit2(document_id2, section_id_mask,
                                             /*score=*/0.084904);
  ScoredDocumentHit expected_scored_doc_hit3(document_id3, section_id_mask,
                                             /*score=*/0.121896);
  EXPECT_THAT(
      scoring_processor->Score(std::move(doc_hit_info_iterator),
                               /*num_to_score=*/3, &query_term_iterators),
      ElementsAre(EqualsScoredDocumentHit(expected_scored_doc_hit1),
                  EqualsScoredDocumentHit(expected_scored_doc_hit2),
                  EqualsScoredDocumentHit(expected_scored_doc_hit3)));
}

TEST_P(ScoringProcessorTest,
       ShouldScoreByRelevanceScore_DocumentsWithSameLength) {
  DocumentProto document1 =
      CreateDocument("icing", "email/1", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);
  DocumentProto document2 =
      CreateDocument("icing", "email/2", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);
  DocumentProto document3 =
      CreateDocument("icing", "email/3", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store()->Put(document1, /*num_tokens=*/10));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store()->Put(document2, /*num_tokens=*/10));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id3,
      document_store()->Put(document3, /*num_tokens=*/10));

  DocHitInfoTermFrequencyPair doc_hit_info1 = DocHitInfo(document_id1);
  doc_hit_info1.UpdateSection(/*section_id*/ 0, /*hit_term_frequency=*/1);
  DocHitInfoTermFrequencyPair doc_hit_info2 = DocHitInfo(document_id2);
  doc_hit_info2.UpdateSection(/*section_id*/ 0, /*hit_term_frequency=*/1);
  DocHitInfoTermFrequencyPair doc_hit_info3 = DocHitInfo(document_id3);
  doc_hit_info3.UpdateSection(/*section_id*/ 0, /*hit_term_frequency=*/1);

  SectionId section_id = 0;
  SectionIdMask section_id_mask = UINT64_C(1) << section_id;

  // Creates input doc_hit_infos and expected output scored_document_hits
  std::vector<DocHitInfoTermFrequencyPair> doc_hit_infos = {
      doc_hit_info1, doc_hit_info2, doc_hit_info3};

  // Creates a dummy DocHitInfoIterator with 3 results for the query "foo"
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE, GetParam());

  // Creates a ScoringProcessor
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>
      query_term_iterators;
  query_term_iterators["foo"] =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");
  // Since the three documents all contain the query term "foo" exactly once
  // and they have the same length, they will have the same BM25F scoret.
  ScoredDocumentHit expected_scored_doc_hit1(document_id1, section_id_mask,
                                             /*score=*/0.118455);
  ScoredDocumentHit expected_scored_doc_hit2(document_id2, section_id_mask,
                                             /*score=*/0.118455);
  ScoredDocumentHit expected_scored_doc_hit3(document_id3, section_id_mask,
                                             /*score=*/0.118455);
  EXPECT_THAT(
      scoring_processor->Score(std::move(doc_hit_info_iterator),
                               /*num_to_score=*/3, &query_term_iterators),
      ElementsAre(EqualsScoredDocumentHit(expected_scored_doc_hit1),
                  EqualsScoredDocumentHit(expected_scored_doc_hit2),
                  EqualsScoredDocumentHit(expected_scored_doc_hit3)));
}

TEST_P(ScoringProcessorTest,
       ShouldScoreByRelevanceScore_DocumentsWithDifferentQueryFrequency) {
  DocumentProto document1 =
      CreateDocument("icing", "email/1", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);
  DocumentProto document2 =
      CreateDocument("icing", "email/2", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);
  DocumentProto document3 =
      CreateDocument("icing", "email/3", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store()->Put(document1, /*num_tokens=*/10));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store()->Put(document2, /*num_tokens=*/10));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id3,
      document_store()->Put(document3, /*num_tokens=*/10));

  DocHitInfoTermFrequencyPair doc_hit_info1 = DocHitInfo(document_id1);
  // Document 1 contains the query term "foo" 5 times
  doc_hit_info1.UpdateSection(/*section_id*/ 0, /*hit_term_frequency=*/5);
  DocHitInfoTermFrequencyPair doc_hit_info2 = DocHitInfo(document_id2);
  // Document 1 contains the query term "foo" 1 time
  doc_hit_info2.UpdateSection(/*section_id*/ 0, /*hit_term_frequency=*/1);
  DocHitInfoTermFrequencyPair doc_hit_info3 = DocHitInfo(document_id3);
  // Document 1 contains the query term "foo" 3 times
  doc_hit_info3.UpdateSection(/*section_id*/ 0, /*hit_term_frequency=*/1);
  doc_hit_info3.UpdateSection(/*section_id*/ 1, /*hit_term_frequency=*/2);

  SectionIdMask section_id_mask1 = 0b00000001;
  SectionIdMask section_id_mask2 = 0b00000001;
  SectionIdMask section_id_mask3 = 0b00000011;

  // Creates input doc_hit_infos and expected output scored_document_hits
  std::vector<DocHitInfoTermFrequencyPair> doc_hit_infos = {
      doc_hit_info1, doc_hit_info2, doc_hit_info3};

  // Creates a dummy DocHitInfoIterator with 3 results for the query "foo"
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE, GetParam());

  // Creates a ScoringProcessor
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>
      query_term_iterators;
  query_term_iterators["foo"] =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");
  // Since the three documents all have the same length, the score is decided by
  // the frequency of the query term "foo".
  ScoredDocumentHit expected_scored_doc_hit1(document_id1, section_id_mask1,
                                             /*score=*/0.226674);
  ScoredDocumentHit expected_scored_doc_hit2(document_id2, section_id_mask2,
                                             /*score=*/0.118455);
  ScoredDocumentHit expected_scored_doc_hit3(document_id3, section_id_mask3,
                                             /*score=*/0.196720);
  EXPECT_THAT(
      scoring_processor->Score(std::move(doc_hit_info_iterator),
                               /*num_to_score=*/3, &query_term_iterators),
      ElementsAre(EqualsScoredDocumentHit(expected_scored_doc_hit1),
                  EqualsScoredDocumentHit(expected_scored_doc_hit2),
                  EqualsScoredDocumentHit(expected_scored_doc_hit3)));
}

TEST_P(ScoringProcessorTest,
       ShouldScoreByRelevanceScore_HitTermWithZeroFrequency) {
  DocumentProto document1 =
      CreateDocument("icing", "email/1", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store()->Put(document1, /*num_tokens=*/10));

  // Document 1 contains the term "foo" 0 times in the "subject" property
  DocHitInfoTermFrequencyPair doc_hit_info1 = DocHitInfo(document_id1);
  doc_hit_info1.UpdateSection(/*section_id*/ 0, /*hit_term_frequency=*/0);

  // Creates input doc_hit_infos and expected output scored_document_hits
  std::vector<DocHitInfoTermFrequencyPair> doc_hit_infos = {doc_hit_info1};

  // Creates a dummy DocHitInfoIterator with 1 result for the query "foo"
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE, GetParam());

  // Creates a ScoringProcessor
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>
      query_term_iterators;
  query_term_iterators["foo"] =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");

  SectionIdMask section_id_mask1 = 0b00000001;

  // Since the document hit has zero frequency, expect a score of zero.
  ScoredDocumentHit expected_scored_doc_hit1(document_id1, section_id_mask1,
                                             /*score=*/0.000000);
  EXPECT_THAT(
      scoring_processor->Score(std::move(doc_hit_info_iterator),
                               /*num_to_score=*/1, &query_term_iterators),
      ElementsAre(EqualsScoredDocumentHit(expected_scored_doc_hit1)));
}

TEST_P(ScoringProcessorTest,
       ShouldScoreByRelevanceScore_SameHitFrequencyDifferentPropertyWeights) {
  DocumentProto document1 =
      CreateDocument("icing", "email/1", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);
  DocumentProto document2 =
      CreateDocument("icing", "email/2", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store()->Put(document1, /*num_tokens=*/1));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store()->Put(document2, /*num_tokens=*/1));

  // Document 1 contains the term "foo" 1 time in the "body" property
  SectionId body_section_id = 0;
  DocHitInfoTermFrequencyPair doc_hit_info1 = DocHitInfo(document_id1);
  doc_hit_info1.UpdateSection(body_section_id, /*hit_term_frequency=*/1);

  // Document 2 contains the term "foo" 1 time in the "subject" property
  SectionId subject_section_id = 1;
  DocHitInfoTermFrequencyPair doc_hit_info2 = DocHitInfo(document_id2);
  doc_hit_info2.UpdateSection(subject_section_id, /*hit_term_frequency=*/1);

  // Creates input doc_hit_infos and expected output scored_document_hits
  std::vector<DocHitInfoTermFrequencyPair> doc_hit_infos = {doc_hit_info1,
                                                            doc_hit_info2};

  // Creates a dummy DocHitInfoIterator with 2 results for the query "foo"
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE, GetParam());

  PropertyWeight body_property_weight =
      CreatePropertyWeight(/*path=*/"body", /*weight=*/0.5);
  PropertyWeight subject_property_weight =
      CreatePropertyWeight(/*path=*/"subject", /*weight=*/2.0);
  *spec_proto.add_type_property_weights() = CreateTypePropertyWeights(
      /*schema_type=*/"email", {body_property_weight, subject_property_weight});

  // Creates a ScoringProcessor
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>
      query_term_iterators;
  query_term_iterators["foo"] =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");

  SectionIdMask body_section_id_mask = 1U << body_section_id;
  SectionIdMask subject_section_id_mask = 1U << subject_section_id;

  // We expect document 2 to have a higher score than document 1 as it matches
  // "foo" in the "subject" property, which is weighed higher than the "body"
  // property. Final scores are computed with smoothing applied.
  ScoredDocumentHit expected_scored_doc_hit1(document_id1, body_section_id_mask,
                                             /*score=*/0.053624);
  ScoredDocumentHit expected_scored_doc_hit2(document_id2,
                                             subject_section_id_mask,
                                             /*score=*/0.153094);
  EXPECT_THAT(
      scoring_processor->Score(std::move(doc_hit_info_iterator),
                               /*num_to_score=*/2, &query_term_iterators),
      ElementsAre(EqualsScoredDocumentHit(expected_scored_doc_hit1),
                  EqualsScoredDocumentHit(expected_scored_doc_hit2)));
}

TEST_P(ScoringProcessorTest,
       ShouldScoreByRelevanceScore_WithImplicitPropertyWeight) {
  DocumentProto document1 =
      CreateDocument("icing", "email/1", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);
  DocumentProto document2 =
      CreateDocument("icing", "email/2", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store()->Put(document1, /*num_tokens=*/1));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store()->Put(document2, /*num_tokens=*/1));

  // Document 1 contains the term "foo" 1 time in the "body" property
  SectionId body_section_id = 0;
  DocHitInfoTermFrequencyPair doc_hit_info1 = DocHitInfo(document_id1);
  doc_hit_info1.UpdateSection(body_section_id, /*hit_term_frequency=*/1);

  // Document 2 contains the term "foo" 1 time in the "subject" property
  SectionId subject_section_id = 1;
  DocHitInfoTermFrequencyPair doc_hit_info2 = DocHitInfo(document_id2);
  doc_hit_info2.UpdateSection(subject_section_id, /*hit_term_frequency=*/1);

  // Creates input doc_hit_infos and expected output scored_document_hits
  std::vector<DocHitInfoTermFrequencyPair> doc_hit_infos = {doc_hit_info1,
                                                            doc_hit_info2};

  // Creates a dummy DocHitInfoIterator with 2 results for the query "foo"
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE, GetParam());

  PropertyWeight body_property_weight =
      CreatePropertyWeight(/*path=*/"body", /*weight=*/0.5);
  *spec_proto.add_type_property_weights() = CreateTypePropertyWeights(
      /*schema_type=*/"email", {body_property_weight});

  // Creates a ScoringProcessor
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>
      query_term_iterators;
  query_term_iterators["foo"] =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");

  SectionIdMask body_section_id_mask = 1U << body_section_id;
  SectionIdMask subject_section_id_mask = 1U << subject_section_id;

  // We expect document 2 to have a higher score than document 1 as it matches
  // "foo" in the "subject" property, which is weighed higher than the "body"
  // property. This is because the "subject" property is implictly given a
  // a weight of 1.0, the default weight value. Final scores are computed with
  // smoothing applied.
  ScoredDocumentHit expected_scored_doc_hit1(document_id1, body_section_id_mask,
                                             /*score=*/0.094601);
  ScoredDocumentHit expected_scored_doc_hit2(document_id2,
                                             subject_section_id_mask,
                                             /*score=*/0.153094);
  EXPECT_THAT(
      scoring_processor->Score(std::move(doc_hit_info_iterator),
                               /*num_to_score=*/2, &query_term_iterators),
      ElementsAre(EqualsScoredDocumentHit(expected_scored_doc_hit1),
                  EqualsScoredDocumentHit(expected_scored_doc_hit2)));
}

TEST_P(ScoringProcessorTest,
       ShouldScoreByRelevanceScore_WithDefaultPropertyWeight) {
  DocumentProto document1 =
      CreateDocument("icing", "email/1", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);
  DocumentProto document2 =
      CreateDocument("icing", "email/2", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store()->Put(document1, /*num_tokens=*/1));

  // Document 1 contains the term "foo" 1 time in the "body" property
  SectionId body_section_id = 0;
  DocHitInfoTermFrequencyPair doc_hit_info1 = DocHitInfo(document_id1);
  doc_hit_info1.UpdateSection(body_section_id, /*hit_term_frequency=*/1);

  // Creates input doc_hit_infos and expected output scored_document_hits
  std::vector<DocHitInfoTermFrequencyPair> doc_hit_infos = {doc_hit_info1};

  // Creates a dummy DocHitInfoIterator with 1 result for the query "foo"
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE, GetParam());

  *spec_proto.add_type_property_weights() =
      CreateTypePropertyWeights(/*schema_type=*/"email", {});

  // Creates a ScoringProcessor with no explicit weights set.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  ScoringSpecProto spec_proto_with_weights =
      CreateScoringSpecForRankingStrategy(
          ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE, GetParam());

  PropertyWeight body_property_weight = CreatePropertyWeight(/*path=*/"body",
                                                             /*weight=*/1.0);
  *spec_proto_with_weights.add_type_property_weights() =
      CreateTypePropertyWeights(/*schema_type=*/"email",
                                {body_property_weight});

  // Creates a ScoringProcessor with default weight set for "body" property.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor_with_weights,
      ScoringProcessor::Create(spec_proto_with_weights, document_store(),
                               schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>
      query_term_iterators;
  query_term_iterators["foo"] =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");

  // Create a doc hit iterator
  std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>
      query_term_iterators_scoring_with_weights;
  query_term_iterators_scoring_with_weights["foo"] =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");

  SectionIdMask body_section_id_mask = 1U << body_section_id;

  // We expect document 1 to have the same score whether a weight is explicitly
  // set to 1.0 or implictly scored with the default weight. Final scores are
  // computed with smoothing applied.
  ScoredDocumentHit expected_scored_doc_hit(document_id1, body_section_id_mask,
                                            /*score=*/0.208191);
  EXPECT_THAT(
      scoring_processor->Score(std::move(doc_hit_info_iterator),
                               /*num_to_score=*/1, &query_term_iterators),
      ElementsAre(EqualsScoredDocumentHit(expected_scored_doc_hit)));

  // Restore ownership of doc hit iterator and query term iterator to test.
  doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");
  query_term_iterators["foo"] =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");

  EXPECT_THAT(scoring_processor_with_weights->Score(
                  std::move(doc_hit_info_iterator),
                  /*num_to_score=*/1, &query_term_iterators),
              ElementsAre(EqualsScoredDocumentHit(expected_scored_doc_hit)));
}

TEST_P(ScoringProcessorTest,
       ShouldScoreByRelevanceScore_WithZeroPropertyWeight) {
  DocumentProto document1 =
      CreateDocument("icing", "email/1", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);
  DocumentProto document2 =
      CreateDocument("icing", "email/2", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store()->Put(document1, /*num_tokens=*/1));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store()->Put(document2, /*num_tokens=*/1));

  // Document 1 contains the term "foo" 1 time in the "body" property
  SectionId body_section_id = 0;
  DocHitInfoTermFrequencyPair doc_hit_info1 = DocHitInfo(document_id1);
  doc_hit_info1.UpdateSection(body_section_id, /*hit_term_frequency=*/1);

  // Document 2 contains the term "foo" 1 time in the "subject" property
  SectionId subject_section_id = 1;
  DocHitInfoTermFrequencyPair doc_hit_info2 = DocHitInfo(document_id2);
  doc_hit_info2.UpdateSection(subject_section_id, /*hit_term_frequency=*/1);

  // Creates input doc_hit_infos and expected output scored_document_hits
  std::vector<DocHitInfoTermFrequencyPair> doc_hit_infos = {doc_hit_info1,
                                                            doc_hit_info2};

  // Creates a dummy DocHitInfoIterator with 2 results for the query "foo"
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE, GetParam());

  // Sets property weight for "body" to 0.0.
  PropertyWeight body_property_weight =
      CreatePropertyWeight(/*path=*/"body", /*weight=*/0.0);
  // Sets property weight for "subject" to 1.0.
  PropertyWeight subject_property_weight =
      CreatePropertyWeight(/*path=*/"subject", /*weight=*/1.0);
  *spec_proto.add_type_property_weights() = CreateTypePropertyWeights(
      /*schema_type=*/"email", {body_property_weight, subject_property_weight});

  // Creates a ScoringProcessor
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>
      query_term_iterators;
  query_term_iterators["foo"] =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos, "foo");

  std::vector<ScoredDocumentHit> scored_document_hits =
      scoring_processor->Score(std::move(doc_hit_info_iterator),
                               /*num_to_score=*/2, &query_term_iterators);

  // We expect document1 to have a score of 0.0 as the query term "foo" matches
  // in the "body" property which has a weight of 0.0. This is a result of the
  // weighted term frequency being scaled down to 0.0 for the hit. We expect
  // document2 to have a positive score as the query term "foo" matches in the
  // "subject" property which has a weight of 1.0.
  EXPECT_THAT(scored_document_hits, SizeIs(2));
  EXPECT_THAT(scored_document_hits.at(0).document_id(), Eq(document_id1));
  EXPECT_THAT(scored_document_hits.at(0).score(), Eq(0.0));
  EXPECT_THAT(scored_document_hits.at(1).document_id(), Eq(document_id2));
  EXPECT_THAT(scored_document_hits.at(1).score(), Gt(0.0));
}

TEST_P(ScoringProcessorTest, ShouldScoreByCreationTimestamp) {
  DocumentProto document1 =
      CreateDocument("icing", "email/1", kDefaultScore,
                     /*creation_timestamp_ms=*/1571100001111);
  DocumentProto document2 =
      CreateDocument("icing", "email/2", kDefaultScore,
                     /*creation_timestamp_ms=*/1571100002222);
  DocumentProto document3 =
      CreateDocument("icing", "email/3", kDefaultScore,
                     /*creation_timestamp_ms=*/1571100003333);
  // Intentionally inserts documents in a different order
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store()->Put(document1));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id3,
                             document_store()->Put(document3));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store()->Put(document2));
  DocHitInfo doc_hit_info1(document_id1);
  DocHitInfo doc_hit_info2(document_id2);
  DocHitInfo doc_hit_info3(document_id3);
  ScoredDocumentHit scored_document_hit1(document_id1, kSectionIdMaskNone,
                                         document1.creation_timestamp_ms());
  ScoredDocumentHit scored_document_hit2(document_id2, kSectionIdMaskNone,
                                         document2.creation_timestamp_ms());
  ScoredDocumentHit scored_document_hit3(document_id3, kSectionIdMaskNone,
                                         document3.creation_timestamp_ms());

  // Creates a dummy DocHitInfoIterator with 3 results
  std::vector<DocHitInfo> doc_hit_infos = {doc_hit_info2, doc_hit_info3,
                                           doc_hit_info1};
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP, GetParam());

  // Creates a ScoringProcessor which ranks in descending order
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  EXPECT_THAT(scoring_processor->Score(std::move(doc_hit_info_iterator),
                                       /*num_to_score=*/3),
              ElementsAre(EqualsScoredDocumentHit(scored_document_hit2),
                          EqualsScoredDocumentHit(scored_document_hit3),
                          EqualsScoredDocumentHit(scored_document_hit1)));
}

TEST_P(ScoringProcessorTest, ShouldScoreByUsageCount) {
  DocumentProto document1 =
      CreateDocument("icing", "email/1", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);
  DocumentProto document2 =
      CreateDocument("icing", "email/2", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);
  DocumentProto document3 =
      CreateDocument("icing", "email/3", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);

  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store()->Put(document1));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store()->Put(document2));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id3,
                             document_store()->Put(document3));

  // Report usage for doc1 once and doc2 twice.
  UsageReport usage_report_doc1 = CreateUsageReport(
      /*name_space=*/"icing", /*uri=*/"email/1", /*timestamp_ms=*/0,
      UsageReport::USAGE_TYPE1);
  UsageReport usage_report_doc2 = CreateUsageReport(
      /*name_space=*/"icing", /*uri=*/"email/2", /*timestamp_ms=*/0,
      UsageReport::USAGE_TYPE1);
  ICING_ASSERT_OK(document_store()->ReportUsage(usage_report_doc1));
  ICING_ASSERT_OK(document_store()->ReportUsage(usage_report_doc2));
  ICING_ASSERT_OK(document_store()->ReportUsage(usage_report_doc2));

  DocHitInfo doc_hit_info1(document_id1);
  DocHitInfo doc_hit_info2(document_id2);
  DocHitInfo doc_hit_info3(document_id3);
  ScoredDocumentHit scored_document_hit1(document_id1, kSectionIdMaskNone,
                                         /*score=*/1);
  ScoredDocumentHit scored_document_hit2(document_id2, kSectionIdMaskNone,
                                         /*score=*/2);
  ScoredDocumentHit scored_document_hit3(document_id3, kSectionIdMaskNone,
                                         /*score=*/0);

  // Creates a dummy DocHitInfoIterator with 3 results
  std::vector<DocHitInfo> doc_hit_infos = {doc_hit_info1, doc_hit_info2,
                                           doc_hit_info3};
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::USAGE_TYPE1_COUNT, GetParam());

  // Creates a ScoringProcessor which ranks in descending order
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  EXPECT_THAT(scoring_processor->Score(std::move(doc_hit_info_iterator),
                                       /*num_to_score=*/3),
              ElementsAre(EqualsScoredDocumentHit(scored_document_hit1),
                          EqualsScoredDocumentHit(scored_document_hit2),
                          EqualsScoredDocumentHit(scored_document_hit3)));
}

TEST_P(ScoringProcessorTest, ShouldScoreByUsageTimestamp) {
  DocumentProto document1 =
      CreateDocument("icing", "email/1", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);
  DocumentProto document2 =
      CreateDocument("icing", "email/2", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);
  DocumentProto document3 =
      CreateDocument("icing", "email/3", kDefaultScore,
                     /*creation_timestamp_ms=*/kDefaultCreationTimestampMs);

  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store()->Put(document1));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store()->Put(document2));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id3,
                             document_store()->Put(document3));

  // Report usage for doc1 and doc2.
  UsageReport usage_report_doc1 = CreateUsageReport(
      /*name_space=*/"icing", /*uri=*/"email/1", /*timestamp_ms=*/1000,
      UsageReport::USAGE_TYPE1);
  UsageReport usage_report_doc2 = CreateUsageReport(
      /*name_space=*/"icing", /*uri=*/"email/2", /*timestamp_ms=*/5000,
      UsageReport::USAGE_TYPE1);
  ICING_ASSERT_OK(document_store()->ReportUsage(usage_report_doc1));
  ICING_ASSERT_OK(document_store()->ReportUsage(usage_report_doc2));

  DocHitInfo doc_hit_info1(document_id1);
  DocHitInfo doc_hit_info2(document_id2);
  DocHitInfo doc_hit_info3(document_id3);
  ScoredDocumentHit scored_document_hit1(document_id1, kSectionIdMaskNone,
                                         /*score=*/1000);
  ScoredDocumentHit scored_document_hit2(document_id2, kSectionIdMaskNone,
                                         /*score=*/5000);
  ScoredDocumentHit scored_document_hit3(document_id3, kSectionIdMaskNone,
                                         /*score=*/0);

  // Creates a dummy DocHitInfoIterator with 3 results
  std::vector<DocHitInfo> doc_hit_infos = {doc_hit_info1, doc_hit_info2,
                                           doc_hit_info3};
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::USAGE_TYPE1_LAST_USED_TIMESTAMP,
      GetParam());

  // Creates a ScoringProcessor which ranks in descending order
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  EXPECT_THAT(scoring_processor->Score(std::move(doc_hit_info_iterator),
                                       /*num_to_score=*/3),
              ElementsAre(EqualsScoredDocumentHit(scored_document_hit1),
                          EqualsScoredDocumentHit(scored_document_hit2),
                          EqualsScoredDocumentHit(scored_document_hit3)));
}

TEST_P(ScoringProcessorTest, ShouldHandleNoScores) {
  // Creates input doc_hit_infos and corresponding scored_document_hits
  ICING_ASSERT_OK_AND_ASSIGN(
      auto doc_hit_result_pair,
      CreateAndInsertsDocumentsWithScores(document_store(), {1, 2, 3}));
  std::vector<DocHitInfo> doc_hit_infos = std::move(doc_hit_result_pair.first);
  std::vector<ScoredDocumentHit> scored_document_hits =
      std::move(doc_hit_result_pair.second);

  // Creates a dummy DocHitInfoIterator with 4 results one of which doesn't have
  // a score.
  doc_hit_infos.emplace(doc_hit_infos.begin(), /*document_id_in=*/4,
                        kSectionIdMaskNone);
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  // The document hit without a score will be be assigned the default score 0 in
  // a descending order.
  ScoredDocumentHit scored_document_hit_default =
      ScoredDocumentHit(4, kSectionIdMaskNone, /*score=*/0.0);

  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE, GetParam());

  // Creates a ScoringProcessor which ranks in descending order
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));
  EXPECT_THAT(scoring_processor->Score(std::move(doc_hit_info_iterator),
                                       /*num_to_score=*/4),
              ElementsAre(EqualsScoredDocumentHit(scored_document_hit_default),
                          EqualsScoredDocumentHit(scored_document_hits.at(0)),
                          EqualsScoredDocumentHit(scored_document_hits.at(1)),
                          EqualsScoredDocumentHit(scored_document_hits.at(2))));
}

TEST_P(ScoringProcessorTest, ShouldWrapResultsWhenNoScoring) {
  DocumentProto document1 = CreateDocument("icing", "email/1", /*score=*/1,
                                           kDefaultCreationTimestampMs);
  DocumentProto document2 = CreateDocument("icing", "email/2", /*score=*/2,
                                           kDefaultCreationTimestampMs);
  DocumentProto document3 = CreateDocument("icing", "email/3", /*score=*/3,
                                           kDefaultCreationTimestampMs);

  // Intentionally inserts documents in a different order
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store()->Put(document1));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id3,
                             document_store()->Put(document3));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store()->Put(document2));
  DocHitInfo doc_hit_info1(document_id1);
  DocHitInfo doc_hit_info2(document_id2);
  DocHitInfo doc_hit_info3(document_id3);

  // The expected results should all have the default score 0.
  ScoredDocumentHit scored_document_hit1(document_id1, kSectionIdMaskNone,
                                         kDefaultScore);
  ScoredDocumentHit scored_document_hit2(document_id2, kSectionIdMaskNone,
                                         kDefaultScore);
  ScoredDocumentHit scored_document_hit3(document_id3, kSectionIdMaskNone,
                                         kDefaultScore);

  // Creates a dummy DocHitInfoIterator with 3 results
  std::vector<DocHitInfo> doc_hit_infos = {doc_hit_info2, doc_hit_info3,
                                           doc_hit_info1};
  std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  // A ScoringSpecProto with no scoring strategy
  ScoringSpecProto spec_proto = CreateScoringSpecForRankingStrategy(
      ScoringSpecProto::RankingStrategy::NONE, GetParam());

  // Creates a ScoringProcessor which ranks in descending order
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<ScoringProcessor> scoring_processor,
      ScoringProcessor::Create(spec_proto, document_store(), schema_store(),
                               fake_clock().GetSystemTimeMilliseconds()));

  EXPECT_THAT(scoring_processor->Score(std::move(doc_hit_info_iterator),
                                       /*num_to_score=*/3),
              ElementsAre(EqualsScoredDocumentHit(scored_document_hit2),
                          EqualsScoredDocumentHit(scored_document_hit3),
                          EqualsScoredDocumentHit(scored_document_hit1)));
}

INSTANTIATE_TEST_SUITE_P(ScoringProcessorTest, ScoringProcessorTest,
                         testing::Values(ScorerTestingMode::kNormal,
                                         ScorerTestingMode::kAdvanced));

}  // namespace

}  // namespace lib
}  // namespace icing
