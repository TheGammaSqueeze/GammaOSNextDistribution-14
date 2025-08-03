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

#include "icing/index/integer-section-indexing-handler.h"

#include <limits>
#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/numeric/integer-index.h"
#include "icing/index/numeric/numeric-index.h"
#include "icing/portable/platform.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
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
#include "icing/util/tokenized-document.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::IsTrue;

// Indexable properties (section) and section id. Section id is determined by
// the lexicographical order of indexable property paths.
// Schema type with indexable properties: FakeType
// Section id = 0: "body"
// Section id = 1: "timestamp"
// Section id = 2: "title"
static constexpr std::string_view kFakeType = "FakeType";
static constexpr std::string_view kPropertyBody = "body";
static constexpr std::string_view kPropertyTimestamp = "timestamp";
static constexpr std::string_view kPropertyTitle = "title";

static constexpr SectionId kSectionIdTimestamp = 1;

// Schema type with nested indexable properties: NestedType
// Section id = 0: "name"
// Section id = 1: "nested.body"
// Section id = 2: "nested.timestamp"
// Section id = 3: "nested.title"
// Section id = 4: "price"
static constexpr std::string_view kNestedType = "NestedType";
static constexpr std::string_view kPropertyName = "name";
static constexpr std::string_view kPropertyNestedDoc = "nested";
static constexpr std::string_view kPropertyPrice = "price";

static constexpr SectionId kSectionIdNestedTimestamp = 2;
static constexpr SectionId kSectionIdPrice = 4;

class IntegerSectionIndexingHandlerTest : public ::testing::Test {
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

    integer_index_working_path_ = base_dir_ + "/integer_index";
    schema_store_dir_ = base_dir_ + "/schema_store";
    document_store_dir_ = base_dir_ + "/document_store";

    ICING_ASSERT_OK_AND_ASSIGN(
        integer_index_,
        IntegerIndex::Create(filesystem_, integer_index_working_path_,
                             /*num_data_threshold_for_bucket_split=*/65536,
                             /*pre_mapping_fbv=*/false));

    language_segmenter_factory::SegmenterOptions segmenter_options(ULOC_US);
    ICING_ASSERT_OK_AND_ASSIGN(
        lang_segmenter_,
        language_segmenter_factory::Create(std::move(segmenter_options)));

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
                                     .SetDataTypeString(TERM_MATCH_EXACT,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kPropertyBody)
                                     .SetDataTypeString(TERM_MATCH_EXACT,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL))
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kPropertyTimestamp)
                                     .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
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
                                     .SetName(kPropertyPrice)
                                     .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
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
    lang_segmenter_.reset();
    integer_index_.reset();

    filesystem_.DeleteDirectoryRecursively(base_dir_.c_str());
  }

  Filesystem filesystem_;
  FakeClock fake_clock_;
  std::string base_dir_;
  std::string integer_index_working_path_;
  std::string schema_store_dir_;
  std::string document_store_dir_;

  std::unique_ptr<NumericIndex<int64_t>> integer_index_;
  std::unique_ptr<LanguageSegmenter> lang_segmenter_;
  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<DocumentStore> document_store_;
};

std::vector<DocHitInfo> GetHits(std::unique_ptr<DocHitInfoIterator> iterator) {
  std::vector<DocHitInfo> infos;
  while (iterator->Advance().ok()) {
    infos.push_back(iterator->doc_hit_info());
  }
  return infos;
}

TEST_F(IntegerSectionIndexingHandlerTest, CreationWithNullPointerShouldFail) {
  EXPECT_THAT(IntegerSectionIndexingHandler::Create(/*clock=*/nullptr,
                                                    integer_index_.get()),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));

  EXPECT_THAT(IntegerSectionIndexingHandler::Create(&fake_clock_,
                                                    /*integer_index=*/nullptr),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
}

TEST_F(IntegerSectionIndexingHandlerTest, HandleIntegerSection) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyTitle), "title")
          .AddStringProperty(std::string(kPropertyBody), "body")
          .AddInt64Property(std::string(kPropertyTimestamp), 123)
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id,
      document_store_->Put(tokenized_document.document()));

  ASSERT_THAT(integer_index_->last_added_document_id(), Eq(kInvalidDocumentId));
  // Handle document.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<IntegerSectionIndexingHandler> handler,
      IntegerSectionIndexingHandler::Create(&fake_clock_,
                                            integer_index_.get()));
  EXPECT_THAT(
      handler->Handle(tokenized_document, document_id, /*recovery_mode=*/false,
                      /*put_document_stats=*/nullptr),
      IsOk());
  EXPECT_THAT(integer_index_->last_added_document_id(), Eq(document_id));

  // Query "timestamp".
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      integer_index_->GetIterator(
          kPropertyTimestamp, /*key_lower=*/std::numeric_limits<int64_t>::min(),
          /*key_upper=*/std::numeric_limits<int64_t>::max(), *document_store_,
          *schema_store_, fake_clock_.GetSystemTimeMilliseconds()));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  document_id, std::vector<SectionId>{kSectionIdTimestamp})));
}

TEST_F(IntegerSectionIndexingHandlerTest, HandleNestedIntegerSection) {
  DocumentProto nested_document =
      DocumentBuilder()
          .SetKey("icing", "nested_type/1")
          .SetSchema(std::string(kNestedType))
          .AddDocumentProperty(
              std::string(kPropertyNestedDoc),
              DocumentBuilder()
                  .SetKey("icing", "nested_fake_type/1")
                  .SetSchema(std::string(kFakeType))
                  .AddStringProperty(std::string(kPropertyTitle),
                                     "nested title")
                  .AddStringProperty(std::string(kPropertyBody), "nested body")
                  .AddInt64Property(std::string(kPropertyTimestamp), 123)
                  .Build())
          .AddInt64Property(std::string(kPropertyPrice), 456)
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(nested_document)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id,
      document_store_->Put(tokenized_document.document()));

  ASSERT_THAT(integer_index_->last_added_document_id(), Eq(kInvalidDocumentId));
  // Handle nested_document.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<IntegerSectionIndexingHandler> handler,
      IntegerSectionIndexingHandler::Create(&fake_clock_,
                                            integer_index_.get()));
  EXPECT_THAT(
      handler->Handle(tokenized_document, document_id, /*recovery_mode=*/false,
                      /*put_document_stats=*/nullptr),
      IsOk());
  EXPECT_THAT(integer_index_->last_added_document_id(), Eq(document_id));

  // Query "nested.timestamp".
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      integer_index_->GetIterator(
          "nested.timestamp", /*key_lower=*/std::numeric_limits<int64_t>::min(),
          /*key_upper=*/std::numeric_limits<int64_t>::max(), *document_store_,
          *schema_store_, fake_clock_.GetSystemTimeMilliseconds()));
  EXPECT_THAT(
      GetHits(std::move(itr)),
      ElementsAre(EqualsDocHitInfo(
          document_id, std::vector<SectionId>{kSectionIdNestedTimestamp})));

  // Query "price".
  ICING_ASSERT_OK_AND_ASSIGN(
      itr,
      integer_index_->GetIterator(
          kPropertyPrice, /*key_lower=*/std::numeric_limits<int64_t>::min(),
          /*key_upper=*/std::numeric_limits<int64_t>::max(), *document_store_,
          *schema_store_, fake_clock_.GetSystemTimeMilliseconds()));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  document_id, std::vector<SectionId>{kSectionIdPrice})));

  // Query "timestamp". Should get empty result.
  ICING_ASSERT_OK_AND_ASSIGN(
      itr,
      integer_index_->GetIterator(
          kPropertyTimestamp, /*key_lower=*/std::numeric_limits<int64_t>::min(),
          /*key_upper=*/std::numeric_limits<int64_t>::max(), *document_store_,
          *schema_store_, fake_clock_.GetSystemTimeMilliseconds()));
  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());
}

TEST_F(IntegerSectionIndexingHandlerTest, HandleShouldSkipEmptyIntegerSection) {
  // Create a FakeType document without "timestamp".
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyTitle), "title")
          .AddStringProperty(std::string(kPropertyBody), "body")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id,
      document_store_->Put(tokenized_document.document()));

  ASSERT_THAT(integer_index_->last_added_document_id(), Eq(kInvalidDocumentId));
  // Handle document. Index data should remain unchanged since there is no
  // indexable integer, but last_added_document_id should be updated.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<IntegerSectionIndexingHandler> handler,
      IntegerSectionIndexingHandler::Create(&fake_clock_,
                                            integer_index_.get()));
  EXPECT_THAT(
      handler->Handle(tokenized_document, document_id, /*recovery_mode=*/false,
                      /*put_document_stats=*/nullptr),
      IsOk());
  EXPECT_THAT(integer_index_->last_added_document_id(), Eq(document_id));

  // Query "timestamp". Should get empty result.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      integer_index_->GetIterator(
          kPropertyTimestamp, /*key_lower=*/std::numeric_limits<int64_t>::min(),
          /*key_upper=*/std::numeric_limits<int64_t>::max(), *document_store_,
          *schema_store_, fake_clock_.GetSystemTimeMilliseconds()));
  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());
}

TEST_F(IntegerSectionIndexingHandlerTest,
       HandleInvalidDocumentIdShouldReturnInvalidArgumentError) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyTitle), "title")
          .AddStringProperty(std::string(kPropertyBody), "body")
          .AddInt64Property(std::string(kPropertyTimestamp), 123)
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document)));
  ICING_ASSERT_OK(document_store_->Put(tokenized_document.document()));

  static constexpr DocumentId kCurrentDocumentId = 3;
  integer_index_->set_last_added_document_id(kCurrentDocumentId);
  ASSERT_THAT(integer_index_->last_added_document_id(), Eq(kCurrentDocumentId));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<IntegerSectionIndexingHandler> handler,
      IntegerSectionIndexingHandler::Create(&fake_clock_,
                                            integer_index_.get()));

  // Handling document with kInvalidDocumentId should cause a failure, and both
  // index data and last_added_document_id should remain unchanged.
  EXPECT_THAT(
      handler->Handle(tokenized_document, kInvalidDocumentId,
                      /*recovery_mode=*/false, /*put_document_stats=*/nullptr),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(integer_index_->last_added_document_id(), Eq(kCurrentDocumentId));

  // Query "timestamp". Should get empty result.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      integer_index_->GetIterator(
          kPropertyTimestamp, /*key_lower=*/std::numeric_limits<int64_t>::min(),
          /*key_upper=*/std::numeric_limits<int64_t>::max(), *document_store_,
          *schema_store_, fake_clock_.GetSystemTimeMilliseconds()));
  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());

  // Recovery mode should get the same result.
  EXPECT_THAT(
      handler->Handle(tokenized_document, kInvalidDocumentId,
                      /*recovery_mode=*/true, /*put_document_stats=*/nullptr),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(integer_index_->last_added_document_id(), Eq(kCurrentDocumentId));

  // Query "timestamp". Should get empty result.
  ICING_ASSERT_OK_AND_ASSIGN(
      itr,
      integer_index_->GetIterator(
          kPropertyTimestamp, /*key_lower=*/std::numeric_limits<int64_t>::min(),
          /*key_upper=*/std::numeric_limits<int64_t>::max(), *document_store_,
          *schema_store_, fake_clock_.GetSystemTimeMilliseconds()));
  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());
}

TEST_F(IntegerSectionIndexingHandlerTest,
       HandleOutOfOrderDocumentIdShouldReturnInvalidArgumentError) {
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyTitle), "title")
          .AddStringProperty(std::string(kPropertyBody), "body")
          .AddInt64Property(std::string(kPropertyTimestamp), 123)
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id,
      document_store_->Put(tokenized_document.document()));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<IntegerSectionIndexingHandler> handler,
      IntegerSectionIndexingHandler::Create(&fake_clock_,
                                            integer_index_.get()));

  // Handling document with document_id == last_added_document_id should cause a
  // failure, and both index data and last_added_document_id should remain
  // unchanged.
  integer_index_->set_last_added_document_id(document_id);
  ASSERT_THAT(integer_index_->last_added_document_id(), Eq(document_id));
  EXPECT_THAT(
      handler->Handle(tokenized_document, document_id, /*recovery_mode=*/false,
                      /*put_document_stats=*/nullptr),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(integer_index_->last_added_document_id(), Eq(document_id));

  // Query "timestamp". Should get empty result.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      integer_index_->GetIterator(
          kPropertyTimestamp, /*key_lower=*/std::numeric_limits<int64_t>::min(),
          /*key_upper=*/std::numeric_limits<int64_t>::max(), *document_store_,
          *schema_store_, fake_clock_.GetSystemTimeMilliseconds()));
  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());

  // Handling document with document_id < last_added_document_id should cause a
  // failure, and both index data and last_added_document_id should remain
  // unchanged.
  integer_index_->set_last_added_document_id(document_id + 1);
  ASSERT_THAT(integer_index_->last_added_document_id(), Eq(document_id + 1));
  EXPECT_THAT(
      handler->Handle(tokenized_document, document_id, /*recovery_mode=*/false,
                      /*put_document_stats=*/nullptr),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(integer_index_->last_added_document_id(), Eq(document_id + 1));

  // Query "timestamp". Should get empty result.
  ICING_ASSERT_OK_AND_ASSIGN(
      itr,
      integer_index_->GetIterator(
          kPropertyTimestamp, /*key_lower=*/std::numeric_limits<int64_t>::min(),
          /*key_upper=*/std::numeric_limits<int64_t>::max(), *document_store_,
          *schema_store_, fake_clock_.GetSystemTimeMilliseconds()));
  EXPECT_THAT(GetHits(std::move(itr)), IsEmpty());
}

TEST_F(IntegerSectionIndexingHandlerTest,
       HandleRecoveryModeShouldIgnoreDocsLELastAddedDocId) {
  DocumentProto document1 =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyTitle), "title one")
          .AddStringProperty(std::string(kPropertyBody), "body one")
          .AddInt64Property(std::string(kPropertyTimestamp), 123)
          .Build();
  DocumentProto document2 =
      DocumentBuilder()
          .SetKey("icing", "fake_type/2")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyTitle), "title two")
          .AddStringProperty(std::string(kPropertyBody), "body two")
          .AddInt64Property(std::string(kPropertyTimestamp), 456)
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document1,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document1)));
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document2,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document2)));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store_->Put(tokenized_document1.document()));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store_->Put(tokenized_document2.document()));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<IntegerSectionIndexingHandler> handler,
      IntegerSectionIndexingHandler::Create(&fake_clock_,
                                            integer_index_.get()));

  // Handle document with document_id > last_added_document_id in recovery mode.
  // The handler should index this document and update last_added_document_id.
  EXPECT_THAT(
      handler->Handle(tokenized_document1, document_id1, /*recovery_mode=*/true,
                      /*put_document_stats=*/nullptr),
      IsOk());
  EXPECT_THAT(integer_index_->last_added_document_id(), Eq(document_id1));

  // Query "timestamp".
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> itr,
      integer_index_->GetIterator(
          kPropertyTimestamp, /*key_lower=*/std::numeric_limits<int64_t>::min(),
          /*key_upper=*/std::numeric_limits<int64_t>::max(), *document_store_,
          *schema_store_, fake_clock_.GetSystemTimeMilliseconds()));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  document_id1, std::vector<SectionId>{kSectionIdTimestamp})));

  // Handle document with document_id == last_added_document_id in recovery
  // mode. We should not get any error, but the handler should ignore the
  // document, so both index data and last_added_document_id should remain
  // unchanged.
  integer_index_->set_last_added_document_id(document_id2);
  ASSERT_THAT(integer_index_->last_added_document_id(), Eq(document_id2));
  EXPECT_THAT(
      handler->Handle(tokenized_document2, document_id2, /*recovery_mode=*/true,
                      /*put_document_stats=*/nullptr),
      IsOk());
  EXPECT_THAT(integer_index_->last_added_document_id(), Eq(document_id2));

  // Query "timestamp". Should not get hits for document2.
  ICING_ASSERT_OK_AND_ASSIGN(
      itr,
      integer_index_->GetIterator(
          kPropertyTimestamp, /*key_lower=*/std::numeric_limits<int64_t>::min(),
          /*key_upper=*/std::numeric_limits<int64_t>::max(), *document_store_,
          *schema_store_, fake_clock_.GetSystemTimeMilliseconds()));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  document_id1, std::vector<SectionId>{kSectionIdTimestamp})));

  // Handle document with document_id < last_added_document_id in recovery mode.
  // We should not get any error, but the handler should ignore the document, so
  // both index data and last_added_document_id should remain unchanged.
  integer_index_->set_last_added_document_id(document_id2 + 1);
  ASSERT_THAT(integer_index_->last_added_document_id(), Eq(document_id2 + 1));
  EXPECT_THAT(
      handler->Handle(tokenized_document2, document_id2, /*recovery_mode=*/true,
                      /*put_document_stats=*/nullptr),
      IsOk());
  EXPECT_THAT(integer_index_->last_added_document_id(), Eq(document_id2 + 1));

  // Query "timestamp". Should not get hits for document2.
  ICING_ASSERT_OK_AND_ASSIGN(
      itr,
      integer_index_->GetIterator(
          kPropertyTimestamp, /*key_lower=*/std::numeric_limits<int64_t>::min(),
          /*key_upper=*/std::numeric_limits<int64_t>::max(), *document_store_,
          *schema_store_, fake_clock_.GetSystemTimeMilliseconds()));
  EXPECT_THAT(GetHits(std::move(itr)),
              ElementsAre(EqualsDocHitInfo(
                  document_id1, std::vector<SectionId>{kSectionIdTimestamp})));
}

}  // namespace

}  // namespace lib
}  // namespace icing
