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

#include <memory>
#include <string>
#include <string_view>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/file/portable-file-backed-proto-log.h"
#include "icing/join/qualified-id-join-index-impl-v1.h"
#include "icing/join/qualified-id-join-index.h"
#include "icing/join/qualified-id-join-indexing-handler.h"
#include "icing/join/qualified-id.h"
#include "icing/portable/platform.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/schema-builder.h"
#include "icing/schema/joinable-property.h"
#include "icing/schema/schema-store.h"
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

using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::IsTrue;

// Schema type for referenced documents: ReferencedType
static constexpr std::string_view kReferencedType = "ReferencedType";
static constexpr std::string_view kPropertyName = "name";

// Joinable properties and joinable property id. Joinable property id is
// determined by the lexicographical order of joinable property path.
// Schema type with joinable property: FakeType
static constexpr std::string_view kFakeType = "FakeType";
static constexpr std::string_view kPropertyQualifiedId = "qualifiedId";

static constexpr JoinablePropertyId kQualifiedIdJoinablePropertyId = 0;

// Schema type with nested joinable properties: NestedType
static constexpr std::string_view kNestedType = "NestedType";
static constexpr std::string_view kPropertyNestedDoc = "nested";
static constexpr std::string_view kPropertyQualifiedId2 = "qualifiedId2";

static constexpr JoinablePropertyId kNestedQualifiedIdJoinablePropertyId = 0;
static constexpr JoinablePropertyId kQualifiedId2JoinablePropertyId = 1;

static constexpr DocumentId kDefaultDocumentId = 3;

// TODO(b/275121148): remove this test after deprecating
// QualifiedIdJoinIndexImplV1.
class QualifiedIdJoinIndexingHandlerV1Test : public ::testing::Test {
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

    qualified_id_join_index_dir_ = base_dir_ + "/qualified_id_join_index";
    schema_store_dir_ = base_dir_ + "/schema_store";
    doc_store_dir_ = base_dir_ + "/doc_store";

    ICING_ASSERT_OK_AND_ASSIGN(qualified_id_join_index_,
                               QualifiedIdJoinIndexImplV1::Create(
                                   filesystem_, qualified_id_join_index_dir_,
                                   /*pre_mapping_fbv=*/false,
                                   /*use_persistent_hash_map=*/false));

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
                    .SetType(kReferencedType)
                    .AddProperty(PropertyConfigBuilder()
                                     .SetName(kPropertyName)
                                     .SetDataTypeString(TERM_MATCH_EXACT,
                                                        TOKENIZER_PLAIN)
                                     .SetCardinality(CARDINALITY_OPTIONAL)))
            .AddType(SchemaTypeConfigBuilder().SetType(kFakeType).AddProperty(
                PropertyConfigBuilder()
                    .SetName(kPropertyQualifiedId)
                    .SetDataTypeJoinableString(JOINABLE_VALUE_TYPE_QUALIFIED_ID)
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
                                     .SetName(kPropertyQualifiedId2)
                                     .SetDataTypeJoinableString(
                                         JOINABLE_VALUE_TYPE_QUALIFIED_ID)
                                     .SetCardinality(CARDINALITY_OPTIONAL)))
            .Build();
    ICING_ASSERT_OK(schema_store_->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    ASSERT_THAT(filesystem_.CreateDirectoryRecursively(doc_store_dir_.c_str()),
                IsTrue());
    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult create_result,
        DocumentStore::Create(&filesystem_, doc_store_dir_, &fake_clock_,
                              schema_store_.get(),
                              /*force_recovery_and_revalidate_documents=*/false,
                              /*namespace_id_fingerprint=*/false,
                              /*pre_mapping_fbv=*/false,
                              /*use_persistent_hash_map=*/false,
                              PortableFileBackedProtoLog<
                                  DocumentWrapper>::kDeflateCompressionLevel,
                              /*initialize_stats=*/nullptr));
    doc_store_ = std::move(create_result.document_store);
  }

  void TearDown() override {
    doc_store_.reset();
    schema_store_.reset();
    lang_segmenter_.reset();
    qualified_id_join_index_.reset();

    filesystem_.DeleteDirectoryRecursively(base_dir_.c_str());
  }

  Filesystem filesystem_;
  FakeClock fake_clock_;
  std::string base_dir_;
  std::string qualified_id_join_index_dir_;
  std::string schema_store_dir_;
  std::string doc_store_dir_;

  std::unique_ptr<QualifiedIdJoinIndex> qualified_id_join_index_;
  std::unique_ptr<LanguageSegmenter> lang_segmenter_;
  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<DocumentStore> doc_store_;
};

TEST_F(QualifiedIdJoinIndexingHandlerV1Test,
       CreationWithNullPointerShouldFail) {
  EXPECT_THAT(
      QualifiedIdJoinIndexingHandler::Create(
          /*clock=*/nullptr, doc_store_.get(), qualified_id_join_index_.get()),
      StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));

  EXPECT_THAT(
      QualifiedIdJoinIndexingHandler::Create(
          &fake_clock_, /*doc_store=*/nullptr, qualified_id_join_index_.get()),
      StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));

  EXPECT_THAT(
      QualifiedIdJoinIndexingHandler::Create(
          &fake_clock_, doc_store_.get(), /*qualified_id_join_index=*/nullptr),
      StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
}

TEST_F(QualifiedIdJoinIndexingHandlerV1Test, HandleJoinableProperty) {
  DocumentProto referenced_document =
      DocumentBuilder()
          .SetKey("pkg$db/ns", "ref_type/1")
          .SetSchema(std::string(kReferencedType))
          .AddStringProperty(std::string(kPropertyName), "one")
          .Build();

  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyQualifiedId),
                             "pkg$db/ns#ref_type/1")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));

  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kInvalidDocumentId));
  // Handle document.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));
  EXPECT_THAT(
      handler->Handle(tokenized_document, kDefaultDocumentId,
                      /*recovery_mode=*/false, /*put_document_stats=*/nullptr),
      IsOk());

  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kDefaultDocumentId));
  EXPECT_THAT(qualified_id_join_index_->Get(DocJoinInfo(
                  kDefaultDocumentId, kQualifiedIdJoinablePropertyId)),
              IsOkAndHolds("pkg$db/ns#ref_type/1"));
}

TEST_F(QualifiedIdJoinIndexingHandlerV1Test, HandleNestedJoinableProperty) {
  DocumentProto referenced_document1 =
      DocumentBuilder()
          .SetKey("pkg$db/ns", "ref_type/1")
          .SetSchema(std::string(kReferencedType))
          .AddStringProperty(std::string(kPropertyName), "one")
          .Build();
  DocumentProto referenced_document2 =
      DocumentBuilder()
          .SetKey("pkg$db/ns", "ref_type/2")
          .SetSchema(std::string(kReferencedType))
          .AddStringProperty(std::string(kPropertyName), "two")
          .Build();

  DocumentProto nested_document =
      DocumentBuilder()
          .SetKey("pkg$db/ns", "nested_type/1")
          .SetSchema(std::string(kNestedType))
          .AddDocumentProperty(
              std::string(kPropertyNestedDoc),
              DocumentBuilder()
                  .SetKey("pkg$db/ns", "nested_fake_type/1")
                  .SetSchema(std::string(kFakeType))
                  .AddStringProperty(std::string(kPropertyQualifiedId),
                                     "pkg$db/ns#ref_type/2")
                  .Build())
          .AddStringProperty(std::string(kPropertyQualifiedId2),
                             "pkg$db/ns#ref_type/1")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                nested_document));

  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kInvalidDocumentId));
  // Handle nested_document.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));
  EXPECT_THAT(handler->Handle(tokenized_document, kDefaultDocumentId,
                              /*recovery_mode=*/false,
                              /*put_document_stats=*/nullptr),
              IsOk());

  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kDefaultDocumentId));
  EXPECT_THAT(qualified_id_join_index_->Get(DocJoinInfo(
                  kDefaultDocumentId, kNestedQualifiedIdJoinablePropertyId)),
              IsOkAndHolds("pkg$db/ns#ref_type/2"));
  EXPECT_THAT(qualified_id_join_index_->Get(DocJoinInfo(
                  kDefaultDocumentId, kQualifiedId2JoinablePropertyId)),
              IsOkAndHolds("pkg$db/ns#ref_type/1"));
}

TEST_F(QualifiedIdJoinIndexingHandlerV1Test,
       HandleShouldSkipInvalidFormatQualifiedId) {
  static constexpr std::string_view kInvalidFormatQualifiedId =
      "invalid_format_qualified_id";
  ASSERT_THAT(QualifiedId::Parse(kInvalidFormatQualifiedId),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyQualifiedId),
                             std::string(kInvalidFormatQualifiedId))
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));

  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kInvalidDocumentId));
  // Handle document. Should ignore invalid format qualified id.
  // Index data should remain unchanged since there is no valid qualified id,
  // but last_added_document_id should be updated.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));
  EXPECT_THAT(
      handler->Handle(tokenized_document, kDefaultDocumentId,
                      /*recovery_mode=*/false, /*put_document_stats=*/nullptr),
      IsOk());
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kDefaultDocumentId));
  EXPECT_THAT(qualified_id_join_index_->Get(DocJoinInfo(
                  kDefaultDocumentId, kQualifiedIdJoinablePropertyId)),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(QualifiedIdJoinIndexingHandlerV1Test, HandleShouldSkipEmptyQualifiedId) {
  // Create a document without any qualified id.
  DocumentProto document = DocumentBuilder()
                               .SetKey("icing", "fake_type/1")
                               .SetSchema(std::string(kFakeType))
                               .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  ASSERT_THAT(tokenized_document.qualified_id_join_properties(), IsEmpty());

  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kInvalidDocumentId));
  // Handle document. Index data should remain unchanged since there is no
  // qualified id, but last_added_document_id should be updated.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));
  EXPECT_THAT(
      handler->Handle(tokenized_document, kDefaultDocumentId,
                      /*recovery_mode=*/false, /*put_document_stats=*/nullptr),
      IsOk());
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kDefaultDocumentId));
  EXPECT_THAT(qualified_id_join_index_->Get(DocJoinInfo(
                  kDefaultDocumentId, kQualifiedIdJoinablePropertyId)),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(QualifiedIdJoinIndexingHandlerV1Test,
       HandleInvalidDocumentIdShouldReturnInvalidArgumentError) {
  DocumentProto referenced_document =
      DocumentBuilder()
          .SetKey("pkg$db/ns", "ref_type/1")
          .SetSchema(std::string(kReferencedType))
          .AddStringProperty(std::string(kPropertyName), "one")
          .Build();

  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyQualifiedId),
                             "pkg$db/ns#ref_type/1")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));

  qualified_id_join_index_->set_last_added_document_id(kDefaultDocumentId);
  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kDefaultDocumentId));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));

  // Handling document with kInvalidDocumentId should cause a failure, and both
  // index data and last_added_document_id should remain unchanged.
  EXPECT_THAT(
      handler->Handle(tokenized_document, kInvalidDocumentId,
                      /*recovery_mode=*/false, /*put_document_stats=*/nullptr),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kDefaultDocumentId));
  EXPECT_THAT(qualified_id_join_index_->Get(DocJoinInfo(
                  kInvalidDocumentId, kQualifiedIdJoinablePropertyId)),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  // Recovery mode should get the same result.
  EXPECT_THAT(
      handler->Handle(tokenized_document, kInvalidDocumentId,
                      /*recovery_mode=*/false, /*put_document_stats=*/nullptr),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kDefaultDocumentId));
  EXPECT_THAT(qualified_id_join_index_->Get(DocJoinInfo(
                  kInvalidDocumentId, kQualifiedIdJoinablePropertyId)),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(QualifiedIdJoinIndexingHandlerV1Test,
       HandleOutOfOrderDocumentIdShouldReturnInvalidArgumentError) {
  DocumentProto referenced_document =
      DocumentBuilder()
          .SetKey("pkg$db/ns", "ref_type/1")
          .SetSchema(std::string(kReferencedType))
          .AddStringProperty(std::string(kPropertyName), "one")
          .Build();

  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyQualifiedId),
                             "pkg$db/ns#ref_type/1")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));

  qualified_id_join_index_->set_last_added_document_id(kDefaultDocumentId);
  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kDefaultDocumentId));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));

  // Handling document with document_id < last_added_document_id should cause a
  // failure, and both index data and last_added_document_id should remain
  // unchanged.
  ASSERT_THAT(IsDocumentIdValid(kDefaultDocumentId - 1), IsTrue());
  EXPECT_THAT(
      handler->Handle(tokenized_document, kDefaultDocumentId - 1,
                      /*recovery_mode=*/false, /*put_document_stats=*/nullptr),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kDefaultDocumentId));
  EXPECT_THAT(qualified_id_join_index_->Get(DocJoinInfo(
                  kDefaultDocumentId, kQualifiedIdJoinablePropertyId)),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  // Handling document with document_id == last_added_document_id should cause a
  // failure, and both index data and last_added_document_id should remain
  // unchanged.
  EXPECT_THAT(
      handler->Handle(tokenized_document, kDefaultDocumentId,
                      /*recovery_mode=*/false, /*put_document_stats=*/nullptr),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kDefaultDocumentId));
  EXPECT_THAT(qualified_id_join_index_->Get(DocJoinInfo(
                  kDefaultDocumentId, kQualifiedIdJoinablePropertyId)),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
}

TEST_F(QualifiedIdJoinIndexingHandlerV1Test,
       HandleRecoveryModeShouldIgnoreDocsLELastAddedDocId) {
  DocumentProto referenced_document =
      DocumentBuilder()
          .SetKey("pkg$db/ns", "ref_type/1")
          .SetSchema(std::string(kReferencedType))
          .AddStringProperty(std::string(kPropertyName), "one")
          .Build();

  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyQualifiedId),
                             "pkg$db/ns#ref_type/1")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));

  qualified_id_join_index_->set_last_added_document_id(kDefaultDocumentId);
  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kDefaultDocumentId));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));

  // Handle document with document_id < last_added_document_id in recovery mode.
  // We should not get any error, but the handler should ignore the document, so
  // both index data and last_added_document_id should remain unchanged.
  ASSERT_THAT(IsDocumentIdValid(kDefaultDocumentId - 1), IsTrue());
  EXPECT_THAT(
      handler->Handle(tokenized_document, kDefaultDocumentId - 1,
                      /*recovery_mode=*/true, /*put_document_stats=*/nullptr),
      IsOk());
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kDefaultDocumentId));
  EXPECT_THAT(qualified_id_join_index_->Get(DocJoinInfo(
                  kDefaultDocumentId, kQualifiedIdJoinablePropertyId)),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  // Handle document with document_id == last_added_document_id in recovery
  // mode. We should not get any error, but the handler should ignore the
  // document, so both index data and last_added_document_id should remain
  // unchanged.
  EXPECT_THAT(
      handler->Handle(tokenized_document, kDefaultDocumentId,
                      /*recovery_mode=*/true, /*put_document_stats=*/nullptr),
      IsOk());
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kDefaultDocumentId));
  EXPECT_THAT(qualified_id_join_index_->Get(DocJoinInfo(
                  kDefaultDocumentId, kQualifiedIdJoinablePropertyId)),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));

  // Handle document with document_id > last_added_document_id in recovery mode.
  // The handler should index this document and update last_added_document_id.
  ASSERT_THAT(IsDocumentIdValid(kDefaultDocumentId + 1), IsTrue());
  EXPECT_THAT(
      handler->Handle(tokenized_document, kDefaultDocumentId + 1,
                      /*recovery_mode=*/true, /*put_document_stats=*/nullptr),
      IsOk());
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kDefaultDocumentId + 1));
  EXPECT_THAT(qualified_id_join_index_->Get(DocJoinInfo(
                  kDefaultDocumentId + 1, kQualifiedIdJoinablePropertyId)),
              IsOkAndHolds("pkg$db/ns#ref_type/1"));
}

}  // namespace

}  // namespace lib
}  // namespace icing
