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

#include "icing/index/term-indexing-handler.h"

#include <cstdint>
#include <limits>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/file/portable-file-backed-proto-log.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/hit/hit.h"
#include "icing/index/index.h"
#include "icing/index/iterator/doc-hit-info-iterator-test-util.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/property-existence-indexing-handler.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/portable/platform.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/document_wrapper.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/schema-builder.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/transform/normalizer-factory.h"
#include "icing/transform/normalizer.h"
#include "icing/util/tokenized-document.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::IsFalse;
using ::testing::IsTrue;
using ::testing::Test;

// Schema type with indexable properties and section Id.
// Section Id is determined by the lexicographical order of indexable property
// path.
// Section id = 0: body
// Section id = 1: title
constexpr std::string_view kFakeType = "FakeType";
constexpr std::string_view kPropertyBody = "body";
constexpr std::string_view kPropertyTitle = "title";

constexpr SectionId kSectionIdBody = 0;
constexpr SectionId kSectionIdTitle = 1;

// Schema type with nested indexable properties and section Id.
// Section id = 0: "name"
// Section id = 1: "nested.body"
// Section id = 3: "nested.title"
// Section id = 4: "subject"
constexpr std::string_view kNestedType = "NestedType";
constexpr std::string_view kPropertyName = "name";
constexpr std::string_view kPropertyNestedDoc = "nested";
constexpr std::string_view kPropertySubject = "subject";

constexpr SectionId kSectionIdNestedBody = 1;

class TermIndexingHandlerTest : public Test {
 protected:
  void SetUp() override {
    if (!IsCfStringTokenization() && !IsReverseJniTokenization()) {
      ICING_ASSERT_OK(
          // File generated via icu_data_file rule in //icing/BUILD.
          icu_data_file_helper::SetUpICUDataFile(
              GetTestFilePath("icing/icu.dat")));
    }

    base_dir_ = GetTestTempDir() + "/icing_test";
    ASSERT_THAT(filesystem_.CreateDirectoryRecursively(base_dir_.c_str()),
                IsTrue());

    index_dir_ = base_dir_ + "/index";
    schema_store_dir_ = base_dir_ + "/schema_store";
    document_store_dir_ = base_dir_ + "/document_store";

    language_segmenter_factory::SegmenterOptions segmenter_options(ULOC_US);
    ICING_ASSERT_OK_AND_ASSIGN(
        lang_segmenter_,
        language_segmenter_factory::Create(std::move(segmenter_options)));

    ICING_ASSERT_OK_AND_ASSIGN(
        normalizer_,
        normalizer_factory::Create(
            /*max_term_byte_size=*/std::numeric_limits<int32_t>::max()));

    ASSERT_THAT(
        filesystem_.CreateDirectoryRecursively(schema_store_dir_.c_str()),
        IsTrue());
    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    SchemaProto schema =
        SchemaBuilder()
            .AddType(
                SchemaTypeConfigBuilder()
                    .SetType(kFakeType)
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kPropertyTitle)
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kPropertyBody)
                                     .SetDataTypeString(TERM_MATCH_EXACT,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL)))
            .AddType(
                SchemaTypeConfigBuilder()
                    .SetType(kNestedType)
                    .AddProperty(
                        PropertyConfigBuilder()
                            .SetName(kPropertyNestedDoc)
                            .SetDataTypeDocument(
                                kFakeType, /*index_nested_properties=*/true)
                            .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kPropertySubject)
                                     .SetDataTypeString(TERM_MATCH_EXACT,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kPropertyName)
                                     .SetDataTypeString(TERM_MATCH_EXACT,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL)))
            .Build();
    ICING_ASSERT_OK(schema_store_->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    ASSERT_TRUE(
        filesystem_.CreateDirectoryRecursively(document_store_dir_.c_str()));
    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult doc_store_create_result,
        DocumentStore::Create(&filesystem_, document_store_dir_, &fake_clock_,
                              schema_store_.get(),
                              /*force_recovery_and_revalidate_documents=*/false,
                              /*namespace_id_fingerprint=*/false,
                              /*pre_mapping_fbv=*/false,
                              /*use_persistent_hash_map=*/false,
                              PortableFileBackedProtoLog<
                                  DocumentWrapper>::kDeflateCompressionLevel,
                              /*initialize_stats=*/nullptr));
    document_store_ = std::move(doc_store_create_result.document_store);
  }

  void TearDown() override {
    document_store_.reset();
    schema_store_.reset();
    normalizer_.reset();
    lang_segmenter_.reset();

    filesystem_.DeleteDirectoryRecursively(base_dir_.c_str());
  }

  Filesystem filesystem_;
  IcingFilesystem icing_filesystem_;
  FakeClock fake_clock_;
  std::string base_dir_;
  std::string index_dir_;
  std::string schema_store_dir_;
  std::string document_store_dir_;

  std::unique_ptr<LanguageSegmenter> lang_segmenter_;
  std::unique_ptr<Normalizer> normalizer_;
  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<DocumentStore> document_store_;
};

libtextclassifier3::StatusOr<std::unique_ptr<DocHitInfoIterator>>
QueryExistence(Index* index, std::string_view property_path) {
  return index->GetIterator(
      absl_ports::StrCat(kPropertyExistenceTokenPrefix, property_path),
      /*term_start_index=*/0,
      /*unnormalized_term_length=*/0, kSectionIdMaskAll,
      TermMatchType::EXACT_ONLY,
      /*need_hit_term_frequency=*/false);
}

std::vector<DocHitInfo> GetHits(std::unique_ptr<DocHitInfoIterator> iterator) {
  std::vector<DocHitInfo> infos;
  while (iterator->Advance().ok()) {
    infos.push_back(iterator->doc_hit_info());
  }
  return infos;
}

std::vector<DocHitInfoTermFrequencyPair> GetHitsWithTermFrequency(
    std::unique_ptr<DocHitInfoIterator> iterator) {
  std::vector<DocHitInfoTermFrequencyPair> infos;
  while (iterator->Advance().ok()) {
    std::vector<TermMatchInfo> matched_terms_stats;
    iterator->PopulateMatchedTermsStats(&matched_terms_stats);
    for (const TermMatchInfo& term_match_info : matched_terms_stats) {
      infos.push_back(DocHitInfoTermFrequencyPair(
          iterator->doc_hit_info(), term_match_info.term_frequencies));
    }
  }
  return infos;
}

TEST_F(TermIndexingHandlerTest, HandleBothStringSectionAndPropertyExistence) {
  Index::Options options(index_dir_, /*index_merge_size=*/1024 * 1024,
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/1024 * 8);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Index> index,
      Index::Create(options, &filesystem_, &icing_filesystem_));

  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyTitle), "foo")
          .AddStringProperty(std::string(kPropertyBody), "")
          .Build();

  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document)));

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id,
      document_store_->Put(tokenized_document.document()));

  EXPECT_THAT(index->last_added_document_id(), Eq(kInvalidDocumentId));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<TermIndexingHandler> handler,
      TermIndexingHandler::Create(
          &fake_clock_, normalizer_.get(), index.get(),
          /*build_property_existence_metadata_hits=*/true));
  EXPECT_THAT(
      handler->Handle(tokenized_document, document_id, /*recovery_mode=*/false,
                      /*put_document_stats=*/nullptr),
      IsOk());

  EXPECT_THAT(index->last_added_document_id(), Eq(document_id));

  // Query 'foo'
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index->GetIterator("foo", /*term_start_index=*/0,
                         /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                         TermMatchType::EXACT_ONLY));
  std::vector<DocHitInfoTermFrequencyPair> hits =
      GetHitsWithTermFrequency(std::move(itr));
  std::unordered_map<SectionId, Hit::TermFrequency> expected_map{
      {kSectionIdTitle, 1}};
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        document_id, expected_map)));

  // Query for "title" property existence.
  ICING_ASSERT_OK_AND_ASSIGN(itr, QueryExistence(index.get(), kPropertyTitle));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(document_id, std::vector<SectionId>{0})));

  // Query for "body" property existence.
  ICING_ASSERT_OK_AND_ASSIGN(itr, QueryExistence(index.get(), kPropertyBody));
  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());
}

TEST_F(TermIndexingHandlerTest,
       HandleIntoLiteIndex_sortInIndexingNotTriggered) {
  Index::Options options(index_dir_, /*index_merge_size=*/1024 * 1024,
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/1024 * 8);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Index> index,
      Index::Create(options, &filesystem_, &icing_filesystem_));

  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyTitle), "foo")
          .AddStringProperty(std::string(kPropertyBody), "foo bar baz")
          .Build();

  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document)));

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id,
      document_store_->Put(tokenized_document.document()));

  EXPECT_THAT(index->last_added_document_id(), Eq(kInvalidDocumentId));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<TermIndexingHandler> handler,
      TermIndexingHandler::Create(
          &fake_clock_, normalizer_.get(), index.get(),
          /*build_property_existence_metadata_hits=*/true));
  EXPECT_THAT(
      handler->Handle(tokenized_document, document_id, /*recovery_mode=*/false,
                      /*put_document_stats=*/nullptr),
      IsOk());

  EXPECT_THAT(index->last_added_document_id(), Eq(document_id));

  // Query 'foo'
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index->GetIterator("foo", /*term_start_index=*/0,
                         /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                         TermMatchType::EXACT_ONLY));
  std::vector<DocHitInfoTermFrequencyPair> hits =
      GetHitsWithTermFrequency(std::move(itr));
  std::unordered_map<SectionId, Hit::TermFrequency> expected_map{
      {kSectionIdTitle, 1}, {kSectionIdBody, 1}};
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        document_id, expected_map)));

  // Query 'foo' with sectionId mask that masks all results
  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index->GetIterator("foo", /*term_start_index=*/0,
                              /*unnormalized_term_length=*/0, 1U << 2,
                              TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());
}

TEST_F(TermIndexingHandlerTest, HandleIntoLiteIndex_sortInIndexingTriggered) {
  // Create the LiteIndex with a smaller sort threshold. At 64 bytes we sort the
  // HitBuffer after inserting 8 hits
  Index::Options options(index_dir_,
                         /*index_merge_size=*/1024 * 1024,
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/64);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Index> index,
      Index::Create(options, &filesystem_, &icing_filesystem_));

  DocumentProto document0 =
      DocumentBuilder()
          .SetKey("icing", "fake_type/0")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyTitle), "foo foo foo")
          .AddStringProperty(std::string(kPropertyBody), "foo bar baz")
          .Build();
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyTitle), "bar baz baz")
          .AddStringProperty(std::string(kPropertyBody), "foo foo baz")
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("icing", "nested_type/0")
          .SetSchema(std::string(kNestedType))
          .AddDocumentProperty(std::string(kPropertyNestedDoc), document1)
          .AddStringProperty(std::string(kPropertyName), "qux")
          .AddStringProperty(std::string(kPropertySubject), "bar bar")
          .Build();

  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document0,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document0)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id0,
      document_store_->Put(tokenized_document0.document()));

  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document1,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store_->Put(tokenized_document1.document()));

  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document2,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store_->Put(tokenized_document2.document()));
  EXPECT_THAT(index->last_added_document_id(), Eq(kInvalidDocumentId));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<TermIndexingHandler> handler,
      TermIndexingHandler::Create(
          &fake_clock_, normalizer_.get(), index.get(),
          /*build_property_existence_metadata_hits=*/true));

  // Handle doc0 and doc1. The LiteIndex should sort and merge after adding
  // these
  EXPECT_THAT(handler->Handle(tokenized_document0, document_id0,
                              /*recovery_mode=*/false,
                              /*put_document_stats=*/nullptr),
              IsOk());
  EXPECT_THAT(handler->Handle(tokenized_document1, document_id1,
                              /*recovery_mode=*/false,
                              /*put_document_stats=*/nullptr),
              IsOk());
  EXPECT_THAT(index->last_added_document_id(), Eq(document_id1));
  EXPECT_THAT(index->LiteIndexNeedSort(), IsFalse());

  // Handle doc2. The LiteIndex should have an unsorted portion after adding
  EXPECT_THAT(handler->Handle(tokenized_document2, document_id2,
                              /*recovery_mode=*/false,
                              /*put_document_stats=*/nullptr),
              IsOk());
  EXPECT_THAT(index->last_added_document_id(), Eq(document_id2));

  // Hits in the hit buffer:
  // <term>: {(docId, sectionId, term_freq)...}
  // foo: {(0, kSectionIdTitle, 3); (0, kSectionIdBody, 1);
  //       (1, kSectionIdBody, 2);
  //       (2, kSectionIdNestedBody, 2)}
  // bar: {(0, kSectionIdBody, 1);
  //       (1, kSectionIdTitle, 1);
  //       (2, kSectionIdNestedTitle, 1); (2, kSectionIdSubject, 2)}
  // baz: {(0, kSectionIdBody, 1);
  //       (1, kSectionIdTitle, 2); (1, kSectionIdBody, 1),
  //       (2, kSectionIdNestedTitle, 2); (2, kSectionIdNestedBody, 1)}
  // qux: {(2, kSectionIdName, 1)}

  // Query 'foo'
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index->GetIterator("foo", /*term_start_index=*/0,
                         /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                         TermMatchType::EXACT_ONLY));

  // Advance the iterator and verify that we're returning hits in the correct
  // order (i.e. in descending order of DocId)
  ASSERT_THAT(itr->Advance(), IsOk());
  EXPECT_THAT(itr->doc_hit_info().document_id(), Eq(2));
  EXPECT_THAT(itr->doc_hit_info().hit_section_ids_mask(),
              Eq(1U << kSectionIdNestedBody));
  std::vector<TermMatchInfo> matched_terms_stats;
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map2 = {{kSectionIdNestedBody, 2}};
  itr->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, ElementsAre(EqualsTermMatchInfo(
                                       "foo", expected_section_ids_tf_map2)));

  ASSERT_THAT(itr->Advance(), IsOk());
  EXPECT_THAT(itr->doc_hit_info().document_id(), Eq(1));
  EXPECT_THAT(itr->doc_hit_info().hit_section_ids_mask(),
              Eq(1U << kSectionIdBody));
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map1 = {{kSectionIdBody, 2}};
  matched_terms_stats.clear();
  itr->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, ElementsAre(EqualsTermMatchInfo(
                                       "foo", expected_section_ids_tf_map1)));

  ASSERT_THAT(itr->Advance(), IsOk());
  EXPECT_THAT(itr->doc_hit_info().document_id(), Eq(0));
  EXPECT_THAT(itr->doc_hit_info().hit_section_ids_mask(),
              Eq(1U << kSectionIdTitle | 1U << kSectionIdBody));
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map0 = {{kSectionIdTitle, 3},
                                      {kSectionIdBody, 1}};
  matched_terms_stats.clear();
  itr->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, ElementsAre(EqualsTermMatchInfo(
                                       "foo", expected_section_ids_tf_map0)));
}

TEST_F(TermIndexingHandlerTest, HandleIntoLiteIndex_enableSortInIndexing) {
  // Create the LiteIndex with a smaller sort threshold. At 64 bytes we sort the
  // HitBuffer after inserting 8 hits
  Index::Options options(index_dir_,
                         /*index_merge_size=*/1024 * 1024,
                         /*lite_index_sort_at_indexing=*/false,
                         /*lite_index_sort_size=*/64);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Index> index,
      Index::Create(options, &filesystem_, &icing_filesystem_));

  DocumentProto document0 =
      DocumentBuilder()
          .SetKey("icing", "fake_type/0")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyTitle), "foo foo foo")
          .AddStringProperty(std::string(kPropertyBody), "foo bar baz")
          .Build();
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyTitle), "bar baz baz")
          .AddStringProperty(std::string(kPropertyBody), "foo foo baz")
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("icing", "nested_type/0")
          .SetSchema(std::string(kNestedType))
          .AddDocumentProperty(std::string(kPropertyNestedDoc), document1)
          .AddStringProperty(std::string(kPropertyName), "qux")
          .AddStringProperty(std::string(kPropertySubject), "bar bar")
          .Build();

  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document0,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document0)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id0,
      document_store_->Put(tokenized_document0.document()));

  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document1,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store_->Put(tokenized_document1.document()));

  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document2,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store_->Put(tokenized_document2.document()));
  EXPECT_THAT(index->last_added_document_id(), Eq(kInvalidDocumentId));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<TermIndexingHandler> handler,
      TermIndexingHandler::Create(
          &fake_clock_, normalizer_.get(), index.get(),
          /*build_property_existence_metadata_hits=*/true));

  // Handle all docs
  EXPECT_THAT(handler->Handle(tokenized_document0, document_id0,
                              /*recovery_mode=*/false,
                              /*put_document_stats=*/nullptr),
              IsOk());
  EXPECT_THAT(handler->Handle(tokenized_document1, document_id1,
                              /*recovery_mode=*/false,
                              /*put_document_stats=*/nullptr),
              IsOk());
  EXPECT_THAT(handler->Handle(tokenized_document2, document_id2,
                              /*recovery_mode=*/false,
                              /*put_document_stats=*/nullptr),
              IsOk());
  EXPECT_THAT(index->last_added_document_id(), Eq(document_id2));

  // We've disabled sorting during indexing so the HitBuffer's unsorted section
  // should exceed the sort threshold. PersistToDisk and reinitialize the
  // LiteIndex with sort_at_indexing=true.
  ASSERT_THAT(index->PersistToDisk(), IsOk());
  options = Index::Options(index_dir_,
                           /*index_merge_size=*/1024 * 1024,
                           /*lite_index_sort_at_indexing=*/true,
                           /*lite_index_sort_size=*/64);
  ICING_ASSERT_OK_AND_ASSIGN(
      index, Index::Create(options, &filesystem_, &icing_filesystem_));

  // Verify that the HitBuffer has been sorted after initializing with
  // sort_at_indexing enabled.
  EXPECT_THAT(index->LiteIndexNeedSort(), IsFalse());

  // Hits in the hit buffer:
  // <term>: {(docId, sectionId, term_freq)...}
  // foo: {(0, kSectionIdTitle, 3); (0, kSectionIdBody, 1);
  //       (1, kSectionIdBody, 2);
  //       (2, kSectionIdNestedBody, 2)}
  // bar: {(0, kSectionIdBody, 1);
  //       (1, kSectionIdTitle, 1);
  //       (2, kSectionIdNestedTitle, 1); (2, kSectionIdSubject, 2)}
  // baz: {(0, kSectionIdBody, 1);
  //       (1, kSectionIdTitle, 2); (1, kSectionIdBody, 1),
  //       (2, kSectionIdNestedTitle, 2); (2, kSectionIdNestedBody, 1)}
  // qux: {(2, kSectionIdName, 1)}

  // Query 'foo'
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index->GetIterator("foo", /*term_start_index=*/0,
                         /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                         TermMatchType::EXACT_ONLY));

  // Advance the iterator and verify that we're returning hits in the correct
  // order (i.e. in descending order of DocId)
  ASSERT_THAT(itr->Advance(), IsOk());
  EXPECT_THAT(itr->doc_hit_info().document_id(), Eq(2));
  EXPECT_THAT(itr->doc_hit_info().hit_section_ids_mask(),
              Eq(1U << kSectionIdNestedBody));
  std::vector<TermMatchInfo> matched_terms_stats;
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map2 = {{kSectionIdNestedBody, 2}};
  itr->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, ElementsAre(EqualsTermMatchInfo(
                                       "foo", expected_section_ids_tf_map2)));

  ASSERT_THAT(itr->Advance(), IsOk());
  EXPECT_THAT(itr->doc_hit_info().document_id(), Eq(1));
  EXPECT_THAT(itr->doc_hit_info().hit_section_ids_mask(),
              Eq(1U << kSectionIdBody));
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map1 = {{kSectionIdBody, 2}};
  matched_terms_stats.clear();
  itr->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, ElementsAre(EqualsTermMatchInfo(
                                       "foo", expected_section_ids_tf_map1)));

  ASSERT_THAT(itr->Advance(), IsOk());
  EXPECT_THAT(itr->doc_hit_info().document_id(), Eq(0));
  EXPECT_THAT(itr->doc_hit_info().hit_section_ids_mask(),
              Eq(1U << kSectionIdTitle | 1U << kSectionIdBody));
  std::unordered_map<SectionId, Hit::TermFrequency>
      expected_section_ids_tf_map0 = {{kSectionIdTitle, 3},
                                      {kSectionIdBody, 1}};
  matched_terms_stats.clear();
  itr->PopulateMatchedTermsStats(&matched_terms_stats);
  EXPECT_THAT(matched_terms_stats, ElementsAre(EqualsTermMatchInfo(
                                       "foo", expected_section_ids_tf_map0)));
}

}  // namespace

}  // namespace lib
}  // namespace icing
