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

#include "icing/index/index-processor.h"

#include <cstdint>
#include <limits>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/absl_ports/str_join.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/file/portable-file-backed-proto-log.h"
#include "icing/index/data-indexing-handler.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/hit/hit.h"
#include "icing/index/index.h"
#include "icing/index/integer-section-indexing-handler.h"
#include "icing/index/iterator/doc-hit-info-iterator-test-util.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/numeric/integer-index.h"
#include "icing/index/numeric/numeric-index.h"
#include "icing/index/term-indexing-handler.h"
#include "icing/index/term-property-id.h"
#include "icing/join/qualified-id-join-index-impl-v1.h"
#include "icing/join/qualified-id-join-index.h"
#include "icing/join/qualified-id-join-indexing-handler.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/legacy/index/icing-mock-filesystem.h"
#include "icing/portable/platform.h"
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
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/random-string.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/transform/normalizer-factory.h"
#include "icing/transform/normalizer.h"
#include "icing/util/crc32.h"
#include "icing/util/tokenized-document.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {

namespace {

constexpr std::string_view kIpsumText =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nulla convallis "
    "scelerisque orci quis hendrerit. Sed augue turpis, sodales eu gravida "
    "nec, scelerisque nec leo. Maecenas accumsan interdum commodo. Aliquam "
    "mattis sapien est, sit amet interdum risus dapibus sed. Maecenas leo "
    "erat, fringilla in nisl a, venenatis gravida metus. Phasellus venenatis, "
    "orci in aliquet mattis, lectus sapien volutpat arcu, sed hendrerit ligula "
    "arcu nec mauris. Integer dolor mi, rhoncus eget gravida et, pulvinar et "
    "nunc. Aliquam ac sollicitudin nisi. Vivamus sit amet urna vestibulum, "
    "tincidunt eros sed, efficitur nisl. Fusce non neque accumsan, sagittis "
    "nisi eget, sagittis turpis. Ut pulvinar nibh eu purus feugiat faucibus. "
    "Donec tellus nulla, tincidunt vel lacus id, bibendum fermentum turpis. "
    "Nullam ultrices sed nibh vitae aliquet. Ut risus neque, consectetur "
    "vehicula posuere vitae, convallis eu lorem. Donec semper augue eu nibh "
    "placerat semper.";

// schema types
constexpr std::string_view kFakeType = "FakeType";
constexpr std::string_view kNestedType = "NestedType";

// Indexable properties and section Id. Section Id is determined by the
// lexicographical order of indexable property path.
constexpr std::string_view kExactProperty = "exact";
constexpr std::string_view kIndexableIntegerProperty = "indexableInteger";
constexpr std::string_view kPrefixedProperty = "prefixed";
constexpr std::string_view kRepeatedProperty = "repeated";
constexpr std::string_view kRfc822Property = "rfc822";
constexpr std::string_view kSubProperty = "submessage";  // submessage.nested
constexpr std::string_view kNestedProperty = "nested";   // submessage.nested
// TODO (b/246964044): remove ifdef guard when url-tokenizer is ready for export
// to Android.
#ifdef ENABLE_URL_TOKENIZER
constexpr std::string_view kUrlExactProperty = "urlExact";
constexpr std::string_view kUrlPrefixedProperty = "urlPrefixed";
#endif  // ENABLE_URL_TOKENIZER
constexpr std::string_view kVerbatimExactProperty = "verbatimExact";
constexpr std::string_view kVerbatimPrefixedProperty = "verbatimPrefixed";

constexpr SectionId kExactSectionId = 0;
constexpr SectionId kIndexableIntegerSectionId = 1;
constexpr SectionId kPrefixedSectionId = 2;
constexpr SectionId kRepeatedSectionId = 3;
constexpr SectionId kRfc822SectionId = 4;
constexpr SectionId kNestedSectionId = 5;  // submessage.nested
#ifdef ENABLE_URL_TOKENIZER
constexpr SectionId kUrlExactSectionId = 6;
constexpr SectionId kUrlPrefixedSectionId = 7;
constexpr SectionId kVerbatimExactSectionId = 8;
constexpr SectionId kVerbatimPrefixedSectionId = 9;
#else   // !ENABLE_URL_TOKENIZER
constexpr SectionId kVerbatimExactSectionId = 6;
constexpr SectionId kVerbatimPrefixedSectionId = 7;
#endif  // ENABLE_URL_TOKENIZER

// Other non-indexable properties.
constexpr std::string_view kUnindexedProperty1 = "unindexed1";
constexpr std::string_view kUnindexedProperty2 = "unindexed2";

constexpr DocumentId kDocumentId0 = 0;
constexpr DocumentId kDocumentId1 = 1;

using Cardinality = PropertyConfigProto::Cardinality;
using DataType = PropertyConfigProto::DataType;
using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::IsTrue;
using ::testing::SizeIs;
using ::testing::Test;

#ifdef ENABLE_URL_TOKENIZER
constexpr StringIndexingConfig::TokenizerType::Code TOKENIZER_URL =
    StringIndexingConfig::TokenizerType::URL;
#endif  // ENABLE_URL_TOKENIZER

class IndexProcessorTest : public Test {
 protected:
  void SetUp() override {
    if (!IsCfStringTokenization() && !IsReverseJniTokenization()) {
      ICING_ASSERT_OK(
          // File generated via icu_data_file rule in //icing/BUILD.
          icu_data_file_helper::SetUpICUDataFile(
              GetTestFilePath("icing/icu.dat")));
    }

    base_dir_ = GetTestTempDir() + "/index_processor_test";
    ASSERT_THAT(filesystem_.CreateDirectoryRecursively(base_dir_.c_str()),
                IsTrue());

    index_dir_ = base_dir_ + "/index";
    integer_index_dir_ = base_dir_ + "/integer_index";
    qualified_id_join_index_dir_ = base_dir_ + "/qualified_id_join_index";
    schema_store_dir_ = base_dir_ + "/schema_store";
    doc_store_dir_ = base_dir_ + "/doc_store";

    Index::Options options(index_dir_, /*index_merge_size=*/1024 * 1024,
                           /*lite_index_sort_at_indexing=*/true,
                           /*lite_index_sort_size=*/1024 * 8);
    ICING_ASSERT_OK_AND_ASSIGN(
        index_, Index::Create(options, &filesystem_, &icing_filesystem_));

    ICING_ASSERT_OK_AND_ASSIGN(
        integer_index_,
        IntegerIndex::Create(
            filesystem_, integer_index_dir_,
            IntegerIndex::kDefaultNumDataThresholdForBucketSplit,
            /*pre_mapping_fbv=*/false));

    ICING_ASSERT_OK_AND_ASSIGN(qualified_id_join_index_,
                               QualifiedIdJoinIndexImplV1::Create(
                                   filesystem_, qualified_id_join_index_dir_,
                                   /*pre_mapping_fbv=*/false,
                                   /*use_persistent_hash_map=*/false));

    language_segmenter_factory::SegmenterOptions segmenter_options(ULOC_US);
    ICING_ASSERT_OK_AND_ASSIGN(
        lang_segmenter_,
        language_segmenter_factory::Create(std::move(segmenter_options)));

    ICING_ASSERT_OK_AND_ASSIGN(
        normalizer_,
        normalizer_factory::Create(
            /*max_term_byte_size=*/std::numeric_limits<int32_t>::max()));

    ASSERT_TRUE(
        filesystem_.CreateDirectoryRecursively(schema_store_dir_.c_str()));
    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    SchemaProto schema =
        SchemaBuilder()
            .AddType(
                SchemaTypeConfigBuilder()
                    .SetType(kFakeType)
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kExactProperty)
                                     .SetDataTypeString(TERM_MATCH_EXACT,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kPrefixedProperty)
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kUnindexedProperty1)
                                     .SetDataType(TYPE_STRING)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kUnindexedProperty2)
                                     .SetDataType(TYPE_BYTES)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kRepeatedProperty)
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_REPEATED))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kVerbatimExactProperty)
                                     .SetDataTypeString(TERM_MATCH_EXACT,
                                                        TOKENIZER_VERBATIM)
                                     .SetCardinality(CARDINALITY_REPEATED))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kVerbatimPrefixedProperty)
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_VERBATIM)
                                     .SetCardinality(CARDINALITY_REPEATED))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kRfc822Property)
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_RFC822)
                                     .SetCardinality(CARDINALITY_REPEATED))
#ifdef ENABLE_URL_TOKENIZER
                    .AddProperty(
                        PropertyConfigBuilder()
                            .SetName(kUrlExactProperty)
                            .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_URL)
                            .SetCardinality(CARDINALITY_REPEATED))
                    .AddProperty(
                        PropertyConfigBuilder()
                            .SetName(kUrlPrefixedProperty)
                            .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_URL)
                            .SetCardinality(CARDINALITY_REPEATED))
#endif  // ENABLE_URL_TOKENIZER
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kIndexableIntegerProperty)
                                     .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                     .SetCardinality(CARDINALITY_REPEATED))
                    .AddProperty(
                        PropertyConfigBuilder()
                            .SetName(kSubProperty)
                            .SetDataTypeDocument(
                                kNestedType, /*index_nested_properties=*/true)
                            .SetCardinality(CARDINALITY_OPTIONAL)))
            .AddType(
                SchemaTypeConfigBuilder()
                    .SetType(kNestedType)
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kNestedProperty)
                                     .SetDataTypeString(TERM_MATCH_PREFIX,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL)))
            .Build();
    ICING_ASSERT_OK(schema_store_->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    ASSERT_TRUE(filesystem_.CreateDirectoryRecursively(doc_store_dir_.c_str()));
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
    doc_store_ = std::move(create_result.document_store);

    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<TermIndexingHandler> term_indexing_handler,
        TermIndexingHandler::Create(
            &fake_clock_, normalizer_.get(), index_.get(),
            /*build_property_existence_metadata_hits=*/true));
    ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<IntegerSectionIndexingHandler>
                                   integer_section_indexing_handler,
                               IntegerSectionIndexingHandler::Create(
                                   &fake_clock_, integer_index_.get()));
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<QualifiedIdJoinIndexingHandler>
            qualified_id_join_indexing_handler,
        QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                               qualified_id_join_index_.get()));
    std::vector<std::unique_ptr<DataIndexingHandler>> handlers;
    handlers.push_back(std::move(term_indexing_handler));
    handlers.push_back(std::move(integer_section_indexing_handler));
    handlers.push_back(std::move(qualified_id_join_indexing_handler));

    index_processor_ =
        std::make_unique<IndexProcessor>(std::move(handlers), &fake_clock_);

    mock_icing_filesystem_ = std::make_unique<IcingMockFilesystem>();
  }

  void TearDown() override {
    index_processor_.reset();
    doc_store_.reset();
    schema_store_.reset();
    normalizer_.reset();
    lang_segmenter_.reset();
    qualified_id_join_index_.reset();
    integer_index_.reset();
    index_.reset();

    filesystem_.DeleteDirectoryRecursively(base_dir_.c_str());
  }

  std::unique_ptr<IcingMockFilesystem> mock_icing_filesystem_;

  Filesystem filesystem_;
  IcingFilesystem icing_filesystem_;
  FakeClock fake_clock_;
  std::string base_dir_;
  std::string index_dir_;
  std::string integer_index_dir_;
  std::string qualified_id_join_index_dir_;
  std::string schema_store_dir_;
  std::string doc_store_dir_;

  std::unique_ptr<Index> index_;
  std::unique_ptr<NumericIndex<int64_t>> integer_index_;
  std::unique_ptr<QualifiedIdJoinIndex> qualified_id_join_index_;
  std::unique_ptr<LanguageSegmenter> lang_segmenter_;
  std::unique_ptr<Normalizer> normalizer_;
  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<DocumentStore> doc_store_;

  std::unique_ptr<IndexProcessor> index_processor_;
};

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

TEST_F(IndexProcessorTest, NoTermMatchTypeContent) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kUnindexedProperty1), "foo bar baz")
          .AddBytesProperty(std::string(kUnindexedProperty2),
                            "attachment bytes")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));
}

TEST_F(IndexProcessorTest, NoValidContent) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), "?...!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));
}

TEST_F(IndexProcessorTest, OneDoc) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), "hello world")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("hello", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  std::vector<DocHitInfoTermFrequencyPair> hits =
      GetHitsWithTermFrequency(std::move(itr));
  std::unordered_map<SectionId, Hit::TermFrequency> expectedMap{
      {kExactSectionId, 1}};
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expectedMap)));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator(
               "hello", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
               1U << kPrefixedSectionId, TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());
}

TEST_F(IndexProcessorTest, MultipleDocs) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), "hello world")
          .AddStringProperty(std::string(kPrefixedProperty), "good night moon!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  std::string coffeeRepeatedString = "coffee";
  for (int i = 0; i < Hit::kMaxTermFrequency + 1; i++) {
    coffeeRepeatedString += " coffee";
  }

  document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/2")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), coffeeRepeatedString)
          .AddStringProperty(std::string(kPrefixedProperty),
                             "mr. world world wide")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId1),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId1));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("world", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  std::vector<DocHitInfoTermFrequencyPair> hits =
      GetHitsWithTermFrequency(std::move(itr));
  std::unordered_map<SectionId, Hit::TermFrequency> expectedMap1{
      {kPrefixedSectionId, 2}};
  std::unordered_map<SectionId, Hit::TermFrequency> expectedMap2{
      {kExactSectionId, 1}};
  EXPECT_THAT(
      hits, ElementsAre(
                EqualsDocHitInfoWithTermFrequency(kDocumentId1, expectedMap1),
                EqualsDocHitInfoWithTermFrequency(kDocumentId0, expectedMap2)));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator(
               "world", /*term_start_index=*/0, /*unnormalized_term_length=*/0,
               1U << kPrefixedSectionId, TermMatchType::EXACT_ONLY));
  hits = GetHitsWithTermFrequency(std::move(itr));
  std::unordered_map<SectionId, Hit::TermFrequency> expectedMap{
      {kPrefixedSectionId, 2}};
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId1, expectedMap)));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("coffee", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  hits = GetHitsWithTermFrequency(std::move(itr));
  expectedMap = {{kExactSectionId, Hit::kMaxTermFrequency}};
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId1, expectedMap)));
}

TEST_F(IndexProcessorTest, DocWithNestedProperty) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), "hello world")
          .AddDocumentProperty(
              std::string(kSubProperty),
              DocumentBuilder()
                  .SetKey("icing", "nested_type/1")
                  .SetSchema(std::string(kNestedType))
                  .AddStringProperty(std::string(kNestedProperty),
                                     "rocky raccoon")
                  .Build())
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("rocky", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kNestedSectionId})));
}

TEST_F(IndexProcessorTest, DocWithRepeatedProperty) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), "hello world")
          .AddStringProperty(std::string(kRepeatedProperty), "rocky",
                             "italian stallion")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("italian", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kRepeatedSectionId})));
}

// TODO(b/196771754) This test is disabled on Android because it takes too long
// to generate all of the unique terms and the test times out. Try storing these
// unique terms in a file that the test can read from.
#ifndef __ANDROID__

TEST_F(IndexProcessorTest, HitBufferExhaustedTest) {
  // Testing has shown that adding ~600,000 hits will fill up the hit buffer.
  std::vector<std::string> unique_terms_ = GenerateUniqueTerms(200000);
  std::string content = absl_ports::StrJoin(unique_terms_, " ");

  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), content)
          .AddStringProperty(std::string(kPrefixedProperty), content)
          .AddStringProperty(std::string(kRepeatedProperty), content)
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED,
                       testing::HasSubstr("Hit buffer is full!")));
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));
}

TEST_F(IndexProcessorTest, LexiconExhaustedTest) {
  // Testing has shown that adding ~300,000 terms generated this way will
  // fill up the lexicon.
  std::vector<std::string> unique_terms_ = GenerateUniqueTerms(300000);
  std::string content = absl_ports::StrJoin(unique_terms_, " ");

  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), content)
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));
}

#endif  // __ANDROID__

TEST_F(IndexProcessorTest, TooLongTokens) {
  // Only allow the tokens of length four, truncating "hello", "world" and
  // "night".
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<Normalizer> normalizer,
                             normalizer_factory::Create(
                                 /*max_term_byte_size=*/4));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<TermIndexingHandler> term_indexing_handler,
      TermIndexingHandler::Create(
          &fake_clock_, normalizer.get(), index_.get(),
          /*build_property_existence_metadata_hits=*/true));
  std::vector<std::unique_ptr<DataIndexingHandler>> handlers;
  handlers.push_back(std::move(term_indexing_handler));

  index_processor_ =
      std::make_unique<IndexProcessor>(std::move(handlers), &fake_clock_);

  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), "hello world")
          .AddStringProperty(std::string(kPrefixedProperty), "good night moon!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  // "good" should have been indexed normally.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("good", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kPrefixedSectionId})));

  // "night" should not have been.
  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("night", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());

  // "night" should have been truncated to "nigh".
  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("nigh", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kPrefixedSectionId})));
}

TEST_F(IndexProcessorTest, NonPrefixedContentPrefixQuery) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), "best rocky movies")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/2")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPrefixedProperty), "rocky raccoon")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId1),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId1));

  // Only document_id 1 should surface in a prefix query for "Rock"
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("rock", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId1, std::vector<SectionId>{kPrefixedSectionId})));
}

TEST_F(IndexProcessorTest, TokenNormalization) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), "ALL UPPER CASE")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/2")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), "all lower case")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId1),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId1));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("case", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(kDocumentId1,
                                   std::vector<SectionId>{kExactSectionId}),
                  EqualsDocHitInfo(kDocumentId0,
                                   std::vector<SectionId>{kExactSectionId})));
}

TEST_F(IndexProcessorTest, OutOfOrderDocumentIds) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), "ALL UPPER CASE")
          .AddInt64Property(std::string(kIndexableIntegerProperty), 123)
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId1),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId1));

  ICING_ASSERT_OK_AND_ASSIGN(int64_t index_element_size,
                             index_->GetElementsSize());
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 integer_index_crc,
                             integer_index_->UpdateChecksums());

  // Indexing a document with document_id <= last_added_document_id should cause
  // a failure.
  document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/2")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), "all lower case")
          .AddInt64Property(std::string(kIndexableIntegerProperty), 456)
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  // Verify that both index_ and integer_index_ are unchanged.
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId1));
  EXPECT_THAT(index_->GetElementsSize(), IsOkAndHolds(index_element_size));
  EXPECT_THAT(integer_index_->last_added_document_id(), Eq(kDocumentId1));
  EXPECT_THAT(integer_index_->UpdateChecksums(),
              IsOkAndHolds(integer_index_crc));

  // As should indexing a document document_id == last_added_document_id.
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  // Verify that both index_ and integer_index_ are unchanged.
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId1));
  EXPECT_THAT(index_->GetElementsSize(), IsOkAndHolds(index_element_size));
  EXPECT_THAT(integer_index_->last_added_document_id(), Eq(kDocumentId1));
  EXPECT_THAT(integer_index_->UpdateChecksums(),
              IsOkAndHolds(integer_index_crc));
}

TEST_F(IndexProcessorTest, OutOfOrderDocumentIdsInRecoveryMode) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<TermIndexingHandler> term_indexing_handler,
      TermIndexingHandler::Create(
          &fake_clock_, normalizer_.get(), index_.get(),
          /*build_property_existence_metadata_hits=*/true));
  ICING_ASSERT_OK_AND_ASSIGN(std::unique_ptr<IntegerSectionIndexingHandler>
                                 integer_section_indexing_handler,
                             IntegerSectionIndexingHandler::Create(
                                 &fake_clock_, integer_index_.get()));
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler>
          qualified_id_join_indexing_handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));
  std::vector<std::unique_ptr<DataIndexingHandler>> handlers;
  handlers.push_back(std::move(term_indexing_handler));
  handlers.push_back(std::move(integer_section_indexing_handler));
  handlers.push_back(std::move(qualified_id_join_indexing_handler));

  IndexProcessor index_processor(std::move(handlers), &fake_clock_,
                                 /*recovery_mode=*/true);

  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), "ALL UPPER CASE")
          .AddInt64Property(std::string(kIndexableIntegerProperty), 123)
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor.IndexDocument(tokenized_document, kDocumentId1),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId1));

  ICING_ASSERT_OK_AND_ASSIGN(int64_t index_element_size,
                             index_->GetElementsSize());
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 integer_index_crc,
                             integer_index_->UpdateChecksums());

  // Indexing a document with document_id <= last_added_document_id in recovery
  // mode should not get any error, but IndexProcessor should still ignore it
  // and index data should remain unchanged.
  document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/2")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), "all lower case")
          .AddInt64Property(std::string(kIndexableIntegerProperty), 456)
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor.IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  // Verify that both index_ and integer_index_ are unchanged.
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId1));
  EXPECT_THAT(index_->GetElementsSize(), IsOkAndHolds(index_element_size));
  EXPECT_THAT(integer_index_->last_added_document_id(), Eq(kDocumentId1));
  EXPECT_THAT(integer_index_->UpdateChecksums(),
              IsOkAndHolds(integer_index_crc));

  // As should indexing a document document_id == last_added_document_id.
  EXPECT_THAT(index_processor.IndexDocument(tokenized_document, kDocumentId1),
              IsOk());
  // Verify that both index_ and integer_index_ are unchanged.
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId1));
  EXPECT_THAT(index_->GetElementsSize(), IsOkAndHolds(index_element_size));
  EXPECT_THAT(integer_index_->last_added_document_id(), Eq(kDocumentId1));
  EXPECT_THAT(integer_index_->UpdateChecksums(),
              IsOkAndHolds(integer_index_crc));
}

TEST_F(IndexProcessorTest, NonAsciiIndexing) {
  language_segmenter_factory::SegmenterOptions segmenter_options(
      ULOC_SIMPLIFIED_CHINESE);
  ICING_ASSERT_OK_AND_ASSIGN(
      lang_segmenter_,
      language_segmenter_factory::Create(std::move(segmenter_options)));

  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty),
                             "你好，世界！你好：世界。“你好”世界？")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("你好", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  kDocumentId0, std::vector<SectionId>{kExactSectionId})));
}

TEST_F(IndexProcessorTest,
       LexiconFullIndexesSmallerTokensReturnsResourceExhausted) {
  // This is the maximum token length that an empty lexicon constructed for a
  // lite index with merge size of 1MiB can support.
  constexpr int kMaxTokenLength = 16777217;
  // Create a string "ppppppp..." with a length that is too large to fit into
  // the lexicon.
  std::string enormous_string(kMaxTokenLength + 1, 'p');
  DocumentProto document_one =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty),
                             absl_ports::StrCat(enormous_string, " foo"))
          .AddStringProperty(std::string(kPrefixedProperty), "bar baz")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document_one));
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));
}

TEST_F(IndexProcessorTest, IndexingDocAutomaticMerge) {
  // Create the index with a smaller index_merge_size - merging every time we
  // add 101 documents. This will result in a small LiteIndex, which will be
  // easier to fill up. The LiteIndex itself will have a size larger than the
  // index_merge_size because it adds extra buffer to ensure that it always has
  // room to fit whatever document will trigger the merge.
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kExactProperty), kIpsumText)
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  Index::Options options(index_dir_,
                         /*index_merge_size=*/document.ByteSizeLong() * 100,
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/64);
  ICING_ASSERT_OK_AND_ASSIGN(
      index_, Index::Create(options, &filesystem_, &icing_filesystem_));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<TermIndexingHandler> term_indexing_handler,
      TermIndexingHandler::Create(
          &fake_clock_, normalizer_.get(), index_.get(),
          /*build_property_existence_metadata_hits=*/true));
  std::vector<std::unique_ptr<DataIndexingHandler>> handlers;
  handlers.push_back(std::move(term_indexing_handler));

  index_processor_ =
      std::make_unique<IndexProcessor>(std::move(handlers), &fake_clock_);

  DocumentId doc_id = 0;
  // Have determined experimentally that indexing 3373 documents with this text
  // will cause the LiteIndex to fill up. Further indexing will fail unless the
  // index processor properly merges the LiteIndex into the MainIndex and
  // empties the LiteIndex.
  constexpr int kNumDocsLiteIndexExhaustion = 3373;
  for (; doc_id < kNumDocsLiteIndexExhaustion; ++doc_id) {
    EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, doc_id),
                IsOk());
    EXPECT_THAT(index_->last_added_document_id(), Eq(doc_id));
  }
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, doc_id),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(doc_id));
}

TEST_F(IndexProcessorTest, IndexingDocMergeFailureResets) {
  // 1. Setup a mock filesystem to fail to grow the main index.
  auto open_write_lambda = [this](const char* filename) {
    std::string main_lexicon_suffix =
        "/main-lexicon.prop." +
        std::to_string(GetHasHitsInPrefixSectionPropertyId());
    std::string filename_string(filename);
    if (filename_string.length() >= main_lexicon_suffix.length() &&
        filename_string.substr(
            filename_string.length() - main_lexicon_suffix.length(),
            main_lexicon_suffix.length()) == main_lexicon_suffix) {
      return -1;
    }
    return this->filesystem_.OpenForWrite(filename);
  };
  ON_CALL(*mock_icing_filesystem_, OpenForWrite)
      .WillByDefault(open_write_lambda);

  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPrefixedProperty), kIpsumText)
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));

  // 2. Recreate the index with the mock filesystem and a merge size that will
  // only allow one document to be added before requiring a merge.
  Index::Options options(index_dir_,
                         /*index_merge_size=*/document.ByteSizeLong(),
                         /*lite_index_sort_at_indexing=*/true,
                         /*lite_index_sort_size=*/16);
  ICING_ASSERT_OK_AND_ASSIGN(
      index_,
      Index::Create(options, &filesystem_, mock_icing_filesystem_.get()));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<TermIndexingHandler> term_indexing_handler,
      TermIndexingHandler::Create(
          &fake_clock_, normalizer_.get(), index_.get(),
          /*build_property_existence_metadata_hits=*/true));
  std::vector<std::unique_ptr<DataIndexingHandler>> handlers;
  handlers.push_back(std::move(term_indexing_handler));

  index_processor_ =
      std::make_unique<IndexProcessor>(std::move(handlers), &fake_clock_);

  // 3. Index one document. This should fit in the LiteIndex without requiring a
  // merge.
  DocumentId doc_id = 0;
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, doc_id),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(doc_id));

  // 4. Add one more document to trigger a merge, which should fail and result
  // in a Reset.
  ++doc_id;
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, doc_id),
              StatusIs(libtextclassifier3::StatusCode::DATA_LOSS));
  EXPECT_THAT(index_->last_added_document_id(), Eq(kInvalidDocumentId));

  // 5. Indexing a new document should succeed.
  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, doc_id),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(doc_id));
}

TEST_F(IndexProcessorTest, ExactVerbatimProperty) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kVerbatimExactProperty),
                             "Hello, world!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(tokenized_document.num_string_tokens(), Eq(1));

  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("Hello, world!", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  std::vector<DocHitInfoTermFrequencyPair> hits =
      GetHitsWithTermFrequency(std::move(itr));
  std::unordered_map<SectionId, Hit::TermFrequency> expectedMap{
      {kVerbatimExactSectionId, 1}};

  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expectedMap)));
}

TEST_F(IndexProcessorTest, PrefixVerbatimProperty) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kVerbatimPrefixedProperty),
                             "Hello, world!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(tokenized_document.num_string_tokens(), Eq(1));

  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  // We expect to match the document we indexed as "Hello, w" is a prefix
  // of "Hello, world!"
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("Hello, w", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  std::vector<DocHitInfoTermFrequencyPair> hits =
      GetHitsWithTermFrequency(std::move(itr));
  std::unordered_map<SectionId, Hit::TermFrequency> expectedMap{
      {kVerbatimPrefixedSectionId, 1}};

  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expectedMap)));
}

TEST_F(IndexProcessorTest, VerbatimPropertyDoesntMatchSubToken) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kVerbatimPrefixedProperty),
                             "Hello, world!")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(tokenized_document.num_string_tokens(), Eq(1));

  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("world", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  std::vector<DocHitInfo> hits = GetHits(std::move(itr));

  // We should not have hits for term "world" as the index processor should
  // create a sole token "Hello, world! for the document.
  EXPECT_THAT(hits, IsEmpty());
}

// Some phrases that should match exactly to RFC822 tokens. We normalize the
// tokens, so the case of the string property shouldn't matter.
TEST_F(IndexProcessorTest, Rfc822PropertyExact) {
  DocumentProto document = DocumentBuilder()
                               .SetKey("icing", "fake_type/1")
                               .SetSchema(std::string(kFakeType))
                               .AddStringProperty(std::string(kRfc822Property),
                                                  "<AlexSav@GOOGLE.com>")
                               .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(tokenized_document.num_string_tokens(), Eq(7));

  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  std::unordered_map<SectionId, Hit::TermFrequency> expected_map{
      {kRfc822SectionId, 2}};

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("alexsav", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  std::vector<DocHitInfoTermFrequencyPair> hits =
      GetHitsWithTermFrequency(std::move(itr));
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expected_map)));

  expected_map = {{kRfc822SectionId, 1}};

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("com", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  hits = GetHitsWithTermFrequency(std::move(itr));
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expected_map)));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("alexsav@google.com", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  hits = GetHitsWithTermFrequency(std::move(itr));
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expected_map)));
}

TEST_F(IndexProcessorTest, Rfc822PropertyExactShouldNotReturnPrefix) {
  DocumentProto document = DocumentBuilder()
                               .SetKey("icing", "fake_type/1")
                               .SetSchema(std::string(kFakeType))
                               .AddStringProperty(std::string(kRfc822Property),
                                                  "<AlexSav@GOOGLE.com>")
                               .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(tokenized_document.num_string_tokens(), Eq(7));

  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  std::unordered_map<SectionId, Hit::TermFrequency> expected_map{
      {kRfc822SectionId, 2}};

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("alexsa", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  std::vector<DocHitInfo> hits = GetHits(std::move(itr));
  EXPECT_THAT(hits, IsEmpty());
}

// Some prefixes of generated RFC822 tokens.
#ifdef ENABLE_RFC822_PROPERTY_PREFIX_TEST
// ENABLE_RFC822_PROPERTY_PREFIX_TEST won't be defined, so this test will not be
// compiled.
// TODO(b/250648165): Remove #ifdef to enable this test after fixing the
//                    indeterministic behavior of prefix query term frequency in
//                    lite index.
//
TEST_F(IndexProcessorTest, Rfc822PropertyPrefix) {
  DocumentProto document = DocumentBuilder()
                               .SetKey("icing", "fake_type/1")
                               .SetSchema(std::string(kFakeType))
                               .AddStringProperty(std::string(kRfc822Property),
                                                  "<alexsav@google.com>")
                               .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(tokenized_document.num_string_tokens(), Eq(7));

  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  std::unordered_map<SectionId, Hit::TermFrequency> expected_map{
      {kRfc822SectionId, 1}};

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("alexsav@", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  std::vector<DocHitInfoTermFrequencyPair> hits =
      GetHitsWithTermFrequency(std::move(itr));
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expected_map)));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("goog", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::PREFIX));
  hits = GetHitsWithTermFrequency(std::move(itr));
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expected_map)));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("ale", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::PREFIX));
  hits = GetHitsWithTermFrequency(std::move(itr));
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expected_map)));
}
#endif  // ENABLE_RFC822_PROPERTY_PREFIX_TEST

TEST_F(IndexProcessorTest, Rfc822PropertyNoMatch) {
  DocumentProto document = DocumentBuilder()
                               .SetKey("icing", "fake_type/1")
                               .SetSchema(std::string(kFakeType))
                               .AddStringProperty(std::string(kRfc822Property),
                                                  "<alexsav@google.com>")
                               .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(tokenized_document.num_string_tokens(), Eq(7));

  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  std::unordered_map<SectionId, Hit::TermFrequency> expect_map{{}};

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("abc.xyz", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  std::vector<DocHitInfo> hits = GetHits(std::move(itr));

  EXPECT_THAT(hits, IsEmpty());
}

#ifdef ENABLE_URL_TOKENIZER
TEST_F(IndexProcessorTest, ExactUrlProperty) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kUrlExactProperty),
                             "http://www.google.com")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(tokenized_document.num_string_tokens(), Eq(7));

  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("google", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  std::vector<DocHitInfoTermFrequencyPair> hits =
      GetHitsWithTermFrequency(std::move(itr));
  std::unordered_map<SectionId, Hit::TermFrequency> expected_map{
      {kUrlExactSectionId, 1}};
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expected_map)));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("http", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  hits = GetHitsWithTermFrequency(std::move(itr));
  expected_map = {{kUrlExactSectionId, 1}};
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expected_map)));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("www.google.com", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  hits = GetHitsWithTermFrequency(std::move(itr));
  expected_map = {{kUrlExactSectionId, 1}};
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expected_map)));

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("http://www.google.com", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  hits = GetHitsWithTermFrequency(std::move(itr));
  expected_map = {{kUrlExactSectionId, 1}};
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expected_map)));
}

TEST_F(IndexProcessorTest, ExactUrlPropertyDoesNotMatchPrefix) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kUrlExactProperty),
                             "https://mail.google.com/calendar/render")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(tokenized_document.num_string_tokens(), Eq(8));

  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("co", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::EXACT_ONLY));
  std::vector<DocHitInfoTermFrequencyPair> hits =
      GetHitsWithTermFrequency(std::move(itr));
  EXPECT_THAT(hits, IsEmpty());

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("mail.go", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  hits = GetHitsWithTermFrequency(std::move(itr));
  EXPECT_THAT(hits, IsEmpty());

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("mail.google.com", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::EXACT_ONLY));
  hits = GetHitsWithTermFrequency(std::move(itr));
  EXPECT_THAT(hits, IsEmpty());
}

TEST_F(IndexProcessorTest, PrefixUrlProperty) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kUrlPrefixedProperty),
                             "http://www.google.com")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(tokenized_document.num_string_tokens(), Eq(7));

  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  // "goo" is a prefix of "google" and "google.com"
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("goo", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  std::vector<DocHitInfoTermFrequencyPair> hits =
      GetHitsWithTermFrequency(std::move(itr));
  std::unordered_map<SectionId, Hit::TermFrequency> expected_map{
      {kUrlPrefixedSectionId, 1}};
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expected_map)));

  // "http" is a prefix of "http" and "http://www.google.com"
  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("http", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::PREFIX));
  hits = GetHitsWithTermFrequency(std::move(itr));
  expected_map = {{kUrlPrefixedSectionId, 1}};
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expected_map)));

  // "www.go" is a prefix of "www.google.com"
  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("www.go", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::PREFIX));
  hits = GetHitsWithTermFrequency(std::move(itr));
  expected_map = {{kUrlPrefixedSectionId, 1}};
  EXPECT_THAT(hits, ElementsAre(EqualsDocHitInfoWithTermFrequency(
                        kDocumentId0, expected_map)));
}

TEST_F(IndexProcessorTest, PrefixUrlPropertyNoMatch) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kUrlPrefixedProperty),
                             "https://mail.google.com/calendar/render")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  EXPECT_THAT(tokenized_document.num_string_tokens(), Eq(8));

  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());
  EXPECT_THAT(index_->last_added_document_id(), Eq(kDocumentId0));

  // no token starts with "gle", so we should have no hits
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      index_->GetIterator("gle", /*term_start_index=*/0,
                          /*unnormalized_term_length=*/0, kSectionIdMaskAll,
                          TermMatchType::PREFIX));
  std::vector<DocHitInfoTermFrequencyPair> hits =
      GetHitsWithTermFrequency(std::move(itr));
  EXPECT_THAT(hits, IsEmpty());

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("w.goo", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::PREFIX));
  hits = GetHitsWithTermFrequency(std::move(itr));
  EXPECT_THAT(hits, IsEmpty());

  // tokens have separators removed, so no hits here
  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator(".com", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::PREFIX));
  hits = GetHitsWithTermFrequency(std::move(itr));
  EXPECT_THAT(hits, IsEmpty());

  ICING_ASSERT_OK_AND_ASSIGN(
      itr, index_->GetIterator("calendar/render", /*term_start_index=*/0,
                               /*unnormalized_term_length=*/0,
                               kSectionIdMaskAll, TermMatchType::PREFIX));
  hits = GetHitsWithTermFrequency(std::move(itr));
  EXPECT_THAT(hits, IsEmpty());
}
#endif  // ENABLE_URL_TOKENIZER

TEST_F(IndexProcessorTest, IndexableIntegerProperty) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddInt64Property(std::string(kIndexableIntegerProperty), 1, 2, 3, 4,
                            5)
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  // Expected to have 1 integer section.
  EXPECT_THAT(tokenized_document.integer_sections(), SizeIs(1));

  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      integer_index_->GetIterator(kIndexableIntegerProperty, /*key_lower=*/1,
                                  /*key_upper=*/5, *doc_store_, *schema_store_,
                                  fake_clock_.GetSystemTimeMilliseconds()));

  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(
          kDocumentId0, std::vector<SectionId>{kIndexableIntegerSectionId})));
}

TEST_F(IndexProcessorTest, IndexableIntegerPropertyNoMatch) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddInt64Property(std::string(kIndexableIntegerProperty), 1, 2, 3, 4,
                            5)
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  // Expected to have 1 integer section.
  EXPECT_THAT(tokenized_document.integer_sections(), SizeIs(1));

  EXPECT_THAT(index_processor_->IndexDocument(tokenized_document, kDocumentId0),
              IsOk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      integer_index_->GetIterator(kIndexableIntegerProperty, /*key_lower=*/-1,
                                  /*key_upper=*/0, *doc_store_, *schema_store_,
                                  fake_clock_.GetSystemTimeMilliseconds()));

  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());
}

}  // namespace

}  // namespace lib
}  // namespace icing
