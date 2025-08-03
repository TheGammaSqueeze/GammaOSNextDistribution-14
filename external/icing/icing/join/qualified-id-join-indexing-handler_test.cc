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

#include "icing/join/qualified-id-join-indexing-handler.h"

#include <memory>
#include <string>
#include <string_view>
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
#include "icing/join/document-id-to-join-info.h"
#include "icing/join/qualified-id-join-index-impl-v2.h"
#include "icing/join/qualified-id-join-index.h"
#include "icing/join/qualified-id.h"
#include "icing/portable/platform.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/schema-builder.h"
#include "icing/schema/joinable-property.h"
#include "icing/schema/schema-store.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/store/namespace-fingerprint-identifier.h"
#include "icing/store/namespace-id.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/test-data.h"
#include "icing/testing/tmp-directory.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/util/status-macros.h"
#include "icing/util/tokenized-document.h"
#include "unicode/uloc.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::IsTrue;
using ::testing::NotNull;

// Schema type for referenced documents: ReferencedType
static constexpr std::string_view kReferencedType = "ReferencedType";
static constexpr std::string_view kPropertyName = "name";

// Joinable properties and joinable property id. Joinable property id is
// determined by the lexicographical order of joinable property path.
// Schema type with joinable property: FakeType
static constexpr std::string_view kFakeType = "FakeType";
static constexpr std::string_view kPropertyQualifiedId = "qualifiedId";

// Schema type with nested joinable properties: NestedType
static constexpr std::string_view kNestedType = "NestedType";
static constexpr std::string_view kPropertyNestedDoc = "nested";
static constexpr std::string_view kPropertyQualifiedId2 = "qualifiedId2";

class QualifiedIdJoinIndexingHandlerTest : public ::testing::Test {
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
                               QualifiedIdJoinIndexImplV2::Create(
                                   filesystem_, qualified_id_join_index_dir_,
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
                              /*namespace_id_fingerprint=*/true,
                              /*pre_mapping_fbv=*/false,
                              /*use_persistent_hash_map=*/false,
                              PortableFileBackedProtoLog<
                                  DocumentWrapper>::kDeflateCompressionLevel,
                              /*initialize_stats=*/nullptr));
    doc_store_ = std::move(create_result.document_store);

    // Get FakeType related ids.
    ICING_ASSERT_OK_AND_ASSIGN(fake_type_id_,
                               schema_store_->GetSchemaTypeId(kFakeType));
    ICING_ASSERT_OK_AND_ASSIGN(
        const JoinablePropertyMetadata* metadata1,
        schema_store_->GetJoinablePropertyMetadata(
            fake_type_id_, std::string(kPropertyQualifiedId)));
    ASSERT_THAT(metadata1, NotNull());
    fake_type_joinable_property_id_ = metadata1->id;

    // Get NestedType related ids.
    ICING_ASSERT_OK_AND_ASSIGN(nested_type_id_,
                               schema_store_->GetSchemaTypeId(kNestedType));
    ICING_ASSERT_OK_AND_ASSIGN(
        const JoinablePropertyMetadata* metadata2,
        schema_store_->GetJoinablePropertyMetadata(
            nested_type_id_,
            absl_ports::StrCat(kPropertyNestedDoc, ".", kPropertyQualifiedId)));
    ASSERT_THAT(metadata2, NotNull());
    nested_type_nested_joinable_property_id_ = metadata2->id;
    ICING_ASSERT_OK_AND_ASSIGN(
        const JoinablePropertyMetadata* metadata3,
        schema_store_->GetJoinablePropertyMetadata(
            nested_type_id_, std::string(kPropertyQualifiedId2)));
    ASSERT_THAT(metadata3, NotNull());
    nested_type_joinable_property_id_ = metadata3->id;
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

  std::unique_ptr<QualifiedIdJoinIndexImplV2> qualified_id_join_index_;
  std::unique_ptr<LanguageSegmenter> lang_segmenter_;
  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<DocumentStore> doc_store_;

  // FakeType related ids.
  SchemaTypeId fake_type_id_;
  JoinablePropertyId fake_type_joinable_property_id_;

  // NestedType related ids.
  SchemaTypeId nested_type_id_;
  JoinablePropertyId nested_type_nested_joinable_property_id_;
  JoinablePropertyId nested_type_joinable_property_id_;
};

libtextclassifier3::StatusOr<
    std::vector<QualifiedIdJoinIndexImplV2::JoinDataType>>
GetJoinData(const QualifiedIdJoinIndexImplV2& index,
            SchemaTypeId schema_type_id,
            JoinablePropertyId joinable_property_id) {
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<QualifiedIdJoinIndex::JoinDataIteratorBase> iter,
      index.GetIterator(schema_type_id, joinable_property_id));

  std::vector<QualifiedIdJoinIndexImplV2::JoinDataType> result;
  while (iter->Advance().ok()) {
    result.push_back(iter->GetCurrent());
  }

  return result;
}

TEST_F(QualifiedIdJoinIndexingHandlerTest, CreationWithNullPointerShouldFail) {
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

TEST_F(QualifiedIdJoinIndexingHandlerTest, HandleJoinableProperty) {
  // Create and put referenced (parent) document. Get its document id and
  // namespace id.
  DocumentProto referenced_document =
      DocumentBuilder()
          .SetKey("pkg$db/ns", "ref_type/1")
          .SetSchema(std::string(kReferencedType))
          .AddStringProperty(std::string(kPropertyName), "one")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId ref_doc_id,
                             doc_store_->Put(referenced_document));
  ICING_ASSERT_OK_AND_ASSIGN(
      NamespaceId ref_doc_ns_id,
      doc_store_->GetNamespaceId(referenced_document.namespace_()));
  NamespaceFingerprintIdentifier ref_doc_ns_fingerprint_id(
      /*namespace_id=*/ref_doc_ns_id, /*target_str=*/referenced_document.uri());
  ASSERT_THAT(doc_store_->GetDocumentId(ref_doc_ns_fingerprint_id),
              IsOkAndHolds(ref_doc_id));

  // Create and put (child) document. Also tokenize it.
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyQualifiedId),
                             "pkg$db/ns#ref_type/1")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId doc_id, doc_store_->Put(document));
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document)));

  // Handle document.
  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kInvalidDocumentId));
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));
  EXPECT_THAT(
      handler->Handle(tokenized_document, doc_id, /*recovery_mode=*/false,
                      /*put_document_stats=*/nullptr),
      IsOk());

  // Verify the state of qualified_id_join_index_ after Handle().
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(), Eq(doc_id));
  // (kFakeType, kPropertyQualifiedId) should contain
  // [(doc_id, ref_doc_ns_fingerprint_id)].
  EXPECT_THAT(
      GetJoinData(*qualified_id_join_index_, /*schema_type_id=*/fake_type_id_,
                  /*joinable_property_id=*/fake_type_joinable_property_id_),
      IsOkAndHolds(
          ElementsAre(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/doc_id,
              /*join_info=*/ref_doc_ns_fingerprint_id))));
}

TEST_F(QualifiedIdJoinIndexingHandlerTest, HandleNestedJoinableProperty) {
  // Create and put referenced (parent) document1. Get its document id and
  // namespace id.
  DocumentProto referenced_document1 =
      DocumentBuilder()
          .SetKey("pkg$db/ns", "ref_type/1")
          .SetSchema(std::string(kReferencedType))
          .AddStringProperty(std::string(kPropertyName), "one")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId ref_doc_id1,
                             doc_store_->Put(referenced_document1));
  ICING_ASSERT_OK_AND_ASSIGN(
      NamespaceId ref_doc_ns_id1,
      doc_store_->GetNamespaceId(referenced_document1.namespace_()));
  NamespaceFingerprintIdentifier ref_doc_ns_fingerprint_id1(
      /*namespace_id=*/ref_doc_ns_id1,
      /*target_str=*/referenced_document1.uri());
  ASSERT_THAT(doc_store_->GetDocumentId(ref_doc_ns_fingerprint_id1),
              IsOkAndHolds(ref_doc_id1));

  // Create and put referenced (parent) document2. Get its document id and
  // namespace id.
  DocumentProto referenced_document2 =
      DocumentBuilder()
          .SetKey("pkg$db/ns", "ref_type/2")
          .SetSchema(std::string(kReferencedType))
          .AddStringProperty(std::string(kPropertyName), "two")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId ref_doc_id2,
                             doc_store_->Put(referenced_document2));
  ICING_ASSERT_OK_AND_ASSIGN(
      NamespaceId ref_doc_ns_id2,
      doc_store_->GetNamespaceId(referenced_document2.namespace_()));
  NamespaceFingerprintIdentifier ref_doc_ns_fingerprint_id2(
      /*namespace_id=*/ref_doc_ns_id2,
      /*target_str=*/referenced_document2.uri());
  ASSERT_THAT(doc_store_->GetDocumentId(ref_doc_ns_fingerprint_id2),
              IsOkAndHolds(ref_doc_id2));

  // Create and put (child) document:
  // - kPropertyNestedDoc.kPropertyQualifiedId refers to referenced_document2.
  // - kPropertyQualifiedId2 refers to referenced_document1.
  //
  // Also tokenize it.
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
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId doc_id,
                             doc_store_->Put(nested_document));
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                nested_document));

  // Handle nested_document.
  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kInvalidDocumentId));
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));
  EXPECT_THAT(
      handler->Handle(tokenized_document, doc_id, /*recovery_mode=*/false,
                      /*put_document_stats=*/nullptr),
      IsOk());

  // Verify the state of qualified_id_join_index_ after Handle().
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(), Eq(doc_id));
  // (kFakeType, kPropertyQualifiedId) should contain nothing.
  EXPECT_THAT(
      GetJoinData(*qualified_id_join_index_, /*schema_type_id=*/fake_type_id_,
                  /*joinable_property_id=*/fake_type_joinable_property_id_),
      IsOkAndHolds(IsEmpty()));
  // (kNestedType, kPropertyNestedDoc.kPropertyQualifiedId) should contain
  // [(doc_id, ref_doc_ns_fingerprint_id2)].
  EXPECT_THAT(
      GetJoinData(
          *qualified_id_join_index_, /*schema_type_id=*/nested_type_id_,
          /*joinable_property_id=*/nested_type_nested_joinable_property_id_),
      IsOkAndHolds(
          ElementsAre(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/doc_id,
              /*join_info=*/ref_doc_ns_fingerprint_id2))));
  // (kNestedType, kPropertyQualifiedId2) should contain
  // [(doc_id, ref_doc_ns_fingerprint_id1)].
  EXPECT_THAT(
      GetJoinData(*qualified_id_join_index_, /*schema_type_id=*/nested_type_id_,
                  /*joinable_property_id=*/nested_type_joinable_property_id_),
      IsOkAndHolds(
          ElementsAre(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/doc_id,
              /*join_info=*/ref_doc_ns_fingerprint_id1))));
}

TEST_F(QualifiedIdJoinIndexingHandlerTest,
       HandleShouldSkipInvalidFormatQualifiedId) {
  static constexpr std::string_view kInvalidFormatQualifiedId =
      "invalid_format_qualified_id";
  ASSERT_THAT(QualifiedId::Parse(kInvalidFormatQualifiedId),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));

  // Create and put (child) document with an invalid format referenced qualified
  // id. Also tokenize it.
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyQualifiedId),
                             std::string(kInvalidFormatQualifiedId))
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId doc_id, doc_store_->Put(document));
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));

  // Handle document. Should ignore invalid format qualified id.
  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kInvalidDocumentId));
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));
  EXPECT_THAT(
      handler->Handle(tokenized_document, doc_id, /*recovery_mode=*/false,
                      /*put_document_stats=*/nullptr),
      IsOk());

  // Verify the state of qualified_id_join_index_ after Handle(). Index data
  // should remain unchanged since there is no valid qualified id, but
  // last_added_document_id should be updated.
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(), Eq(doc_id));
  // (kFakeType, kPropertyQualifiedId) should contain nothing.
  EXPECT_THAT(
      GetJoinData(*qualified_id_join_index_, /*schema_type_id=*/fake_type_id_,
                  /*joinable_property_id=*/fake_type_joinable_property_id_),
      IsOkAndHolds(IsEmpty()));
}

TEST_F(QualifiedIdJoinIndexingHandlerTest,
       HandleShouldSkipNonExistingNamespace) {
  static constexpr std::string_view kUnknownNamespace = "UnknownNamespace";
  // Create and put (child) document which references to a parent qualified id
  // with an unknown namespace. Also tokenize it.
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(
              std::string(kPropertyQualifiedId),
              absl_ports::StrCat(kUnknownNamespace, "#", "ref_type/1"))
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId doc_id, doc_store_->Put(document));
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document)));

  // Handle document.
  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kInvalidDocumentId));
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));
  EXPECT_THAT(
      handler->Handle(tokenized_document, doc_id, /*recovery_mode=*/false,
                      /*put_document_stats=*/nullptr),
      IsOk());

  // Verify the state of qualified_id_join_index_ after Handle().
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(), Eq(doc_id));
  // (kFakeType, kPropertyQualifiedId) should be empty since
  // "UnknownNamespace#ref_type/1" should be skipped.
  EXPECT_THAT(
      GetJoinData(*qualified_id_join_index_, /*schema_type_id=*/fake_type_id_,
                  /*joinable_property_id=*/fake_type_joinable_property_id_),
      IsOkAndHolds(IsEmpty()));
}

TEST_F(QualifiedIdJoinIndexingHandlerTest, HandleShouldSkipEmptyQualifiedId) {
  // Create and put (child) document without any qualified id. Also tokenize it.
  DocumentProto document = DocumentBuilder()
                               .SetKey("icing", "fake_type/1")
                               .SetSchema(std::string(kFakeType))
                               .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId doc_id, doc_store_->Put(document));
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                document));
  ASSERT_THAT(tokenized_document.qualified_id_join_properties(), IsEmpty());

  // Handle document.
  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(kInvalidDocumentId));
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));
  EXPECT_THAT(
      handler->Handle(tokenized_document, doc_id, /*recovery_mode=*/false,
                      /*put_document_stats=*/nullptr),
      IsOk());

  // Verify the state of qualified_id_join_index_ after Handle(). Index data
  // should remain unchanged since there is no qualified id, but
  // last_added_document_id should be updated.
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(), Eq(doc_id));
  // (kFakeType, kPropertyQualifiedId) should contain nothing.
  EXPECT_THAT(
      GetJoinData(*qualified_id_join_index_, /*schema_type_id=*/fake_type_id_,
                  /*joinable_property_id=*/fake_type_joinable_property_id_),
      IsOkAndHolds(IsEmpty()));
}

TEST_F(QualifiedIdJoinIndexingHandlerTest,
       HandleInvalidDocumentIdShouldReturnInvalidArgumentError) {
  // Create and put referenced (parent) document. Get its document id and
  // namespace id.
  DocumentProto referenced_document =
      DocumentBuilder()
          .SetKey("pkg$db/ns", "ref_type/1")
          .SetSchema(std::string(kReferencedType))
          .AddStringProperty(std::string(kPropertyName), "one")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId ref_doc_id,
                             doc_store_->Put(referenced_document));
  ICING_ASSERT_OK_AND_ASSIGN(
      NamespaceId ref_doc_ns_id,
      doc_store_->GetNamespaceId(referenced_document.namespace_()));
  NamespaceFingerprintIdentifier ref_doc_ns_fingerprint_id(
      /*namespace_id=*/ref_doc_ns_id, /*target_str=*/referenced_document.uri());
  ASSERT_THAT(doc_store_->GetDocumentId(ref_doc_ns_fingerprint_id),
              IsOkAndHolds(ref_doc_id));

  // Create and put (child) document. Also tokenize it.
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyQualifiedId),
                             "pkg$db/ns#ref_type/1")
          .Build();
  ICING_ASSERT_OK(doc_store_->Put(document));
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document)));

  qualified_id_join_index_->set_last_added_document_id(ref_doc_id);
  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(ref_doc_id));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));

  // Handling document with kInvalidDocumentId should cause a failure.
  EXPECT_THAT(
      handler->Handle(tokenized_document, kInvalidDocumentId,
                      /*recovery_mode=*/false, /*put_document_stats=*/nullptr),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  // Verify the state of qualified_id_join_index_ after Handle(). Both index
  // data and last_added_document_id should remain unchanged.
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(ref_doc_id));
  // (kFakeType, kPropertyQualifiedId) should contain nothing.
  EXPECT_THAT(
      GetJoinData(*qualified_id_join_index_, /*schema_type_id=*/fake_type_id_,
                  /*joinable_property_id=*/fake_type_joinable_property_id_),
      IsOkAndHolds(IsEmpty()));

  // Recovery mode should get the same result.
  EXPECT_THAT(
      handler->Handle(tokenized_document, kInvalidDocumentId,
                      /*recovery_mode=*/false, /*put_document_stats=*/nullptr),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(ref_doc_id));
  // (kFakeType, kPropertyQualifiedId) should contain nothing.
  EXPECT_THAT(
      GetJoinData(*qualified_id_join_index_, /*schema_type_id=*/fake_type_id_,
                  /*joinable_property_id=*/fake_type_joinable_property_id_),
      IsOkAndHolds(IsEmpty()));
}

TEST_F(QualifiedIdJoinIndexingHandlerTest,
       HandleOutOfOrderDocumentIdShouldReturnInvalidArgumentError) {
  // Create and put referenced (parent) document. Get its document id and
  // namespace id.
  DocumentProto referenced_document =
      DocumentBuilder()
          .SetKey("pkg$db/ns", "ref_type/1")
          .SetSchema(std::string(kReferencedType))
          .AddStringProperty(std::string(kPropertyName), "one")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId ref_doc_id,
                             doc_store_->Put(referenced_document));
  ICING_ASSERT_OK_AND_ASSIGN(
      NamespaceId ref_doc_ns_id,
      doc_store_->GetNamespaceId(referenced_document.namespace_()));
  NamespaceFingerprintIdentifier ref_doc_ns_fingerprint_id(
      /*namespace_id=*/ref_doc_ns_id, /*target_str=*/referenced_document.uri());
  ASSERT_THAT(doc_store_->GetDocumentId(ref_doc_ns_fingerprint_id),
              IsOkAndHolds(ref_doc_id));

  // Create and put (child) document. Also tokenize it.
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyQualifiedId),
                             "pkg$db/ns#ref_type/1")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId doc_id, doc_store_->Put(document));
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document)));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));

  // Handling document with document_id == last_added_document_id should cause a
  // failure.
  qualified_id_join_index_->set_last_added_document_id(doc_id);
  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(), Eq(doc_id));
  EXPECT_THAT(
      handler->Handle(tokenized_document, doc_id, /*recovery_mode=*/false,
                      /*put_document_stats=*/nullptr),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  // Verify the state of qualified_id_join_index_ after Handle(). Both index
  // data and last_added_document_id should remain unchanged.
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(), Eq(doc_id));
  // (kFakeType, kPropertyQualifiedId) should contain nothing.
  EXPECT_THAT(
      GetJoinData(*qualified_id_join_index_, /*schema_type_id=*/fake_type_id_,
                  /*joinable_property_id=*/fake_type_joinable_property_id_),
      IsOkAndHolds(IsEmpty()));

  // Handling document with document_id < last_added_document_id should cause a
  // failure.
  qualified_id_join_index_->set_last_added_document_id(doc_id + 1);
  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(doc_id + 1));
  EXPECT_THAT(
      handler->Handle(tokenized_document, doc_id, /*recovery_mode=*/false,
                      /*put_document_stats=*/nullptr),
      StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
  // Verify the state of qualified_id_join_index_ after Handle(). Both index
  // data and last_added_document_id should remain unchanged.
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(doc_id + 1));
  // (kFakeType, kPropertyQualifiedId) should contain nothing.
  EXPECT_THAT(
      GetJoinData(*qualified_id_join_index_, /*schema_type_id=*/fake_type_id_,
                  /*joinable_property_id=*/fake_type_joinable_property_id_),
      IsOkAndHolds(IsEmpty()));
}

TEST_F(QualifiedIdJoinIndexingHandlerTest,
       HandleRecoveryModeShouldIndexDocsGtLastAddedDocId) {
  // Create and put referenced (parent) document. Get its document id and
  // namespace id.
  DocumentProto referenced_document =
      DocumentBuilder()
          .SetKey("pkg$db/ns", "ref_type/1")
          .SetSchema(std::string(kReferencedType))
          .AddStringProperty(std::string(kPropertyName), "one")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId ref_doc_id,
                             doc_store_->Put(referenced_document));
  ICING_ASSERT_OK_AND_ASSIGN(
      NamespaceId ref_doc_ns_id,
      doc_store_->GetNamespaceId(referenced_document.namespace_()));
  NamespaceFingerprintIdentifier ref_doc_ns_fingerprint_id(
      /*namespace_id=*/ref_doc_ns_id, /*target_str=*/referenced_document.uri());
  ASSERT_THAT(doc_store_->GetDocumentId(ref_doc_ns_fingerprint_id),
              IsOkAndHolds(ref_doc_id));

  // Create and put (child) document. Also tokenize it.
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyQualifiedId),
                             "pkg$db/ns#ref_type/1")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId doc_id, doc_store_->Put(document));
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document)));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));

  // Handle document with document_id > last_added_document_id in recovery mode.
  // The handler should index this document and update last_added_document_id.
  qualified_id_join_index_->set_last_added_document_id(doc_id - 1);
  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(doc_id - 1));
  EXPECT_THAT(
      handler->Handle(tokenized_document, doc_id, /*recovery_mode=*/true,
                      /*put_document_stats=*/nullptr),
      IsOk());
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(), Eq(doc_id));
  EXPECT_THAT(
      GetJoinData(*qualified_id_join_index_, /*schema_type_id=*/fake_type_id_,
                  /*joinable_property_id=*/fake_type_joinable_property_id_),
      IsOkAndHolds(
          ElementsAre(DocumentIdToJoinInfo<NamespaceFingerprintIdentifier>(
              /*document_id=*/doc_id,
              /*join_info=*/ref_doc_ns_fingerprint_id))));
}

TEST_F(QualifiedIdJoinIndexingHandlerTest,
       HandleRecoveryModeShouldIgnoreDocsLeLastAddedDocId) {
  // Create and put referenced (parent) document. Get its document id and
  // namespace id.
  DocumentProto referenced_document =
      DocumentBuilder()
          .SetKey("pkg$db/ns", "ref_type/1")
          .SetSchema(std::string(kReferencedType))
          .AddStringProperty(std::string(kPropertyName), "one")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId ref_doc_id,
                             doc_store_->Put(referenced_document));
  ICING_ASSERT_OK_AND_ASSIGN(
      NamespaceId ref_doc_ns_id,
      doc_store_->GetNamespaceId(referenced_document.namespace_()));
  NamespaceFingerprintIdentifier ref_doc_ns_fingerprint_id(
      /*namespace_id=*/ref_doc_ns_id, /*target_str=*/referenced_document.uri());
  ASSERT_THAT(doc_store_->GetDocumentId(ref_doc_ns_fingerprint_id),
              IsOkAndHolds(ref_doc_id));

  // Create and put (child) document. Also tokenize it.
  DocumentProto document =
      DocumentBuilder()
          .SetKey("icing", "fake_type/1")
          .SetSchema(std::string(kFakeType))
          .AddStringProperty(std::string(kPropertyQualifiedId),
                             "pkg$db/ns#ref_type/1")
          .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId doc_id, doc_store_->Put(document));
  ICING_ASSERT_OK_AND_ASSIGN(
      TokenizedDocument tokenized_document,
      TokenizedDocument::Create(schema_store_.get(), lang_segmenter_.get(),
                                std::move(document)));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<QualifiedIdJoinIndexingHandler> handler,
      QualifiedIdJoinIndexingHandler::Create(&fake_clock_, doc_store_.get(),
                                             qualified_id_join_index_.get()));

  // Handle document with document_id == last_added_document_id in recovery
  // mode. We should not get any error, but the handler should ignore the
  // document, so both index data and last_added_document_id should remain
  // unchanged.
  qualified_id_join_index_->set_last_added_document_id(doc_id);
  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(), Eq(doc_id));
  EXPECT_THAT(
      handler->Handle(tokenized_document, doc_id, /*recovery_mode=*/true,
                      /*put_document_stats=*/nullptr),
      IsOk());
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(), Eq(doc_id));
  // (kFakeType, kPropertyQualifiedId) should contain nothing.
  EXPECT_THAT(
      GetJoinData(*qualified_id_join_index_, /*schema_type_id=*/fake_type_id_,
                  /*joinable_property_id=*/fake_type_joinable_property_id_),
      IsOkAndHolds(IsEmpty()));

  // Handle document with document_id < last_added_document_id in recovery mode.
  // We should not get any error, but the handler should ignore the document, so
  // both index data and last_added_document_id should remain unchanged.
  qualified_id_join_index_->set_last_added_document_id(doc_id + 1);
  ASSERT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(doc_id + 1));
  EXPECT_THAT(
      handler->Handle(tokenized_document, doc_id, /*recovery_mode=*/true,
                      /*put_document_stats=*/nullptr),
      IsOk());
  EXPECT_THAT(qualified_id_join_index_->last_added_document_id(),
              Eq(doc_id + 1));
  // (kFakeType, kPropertyQualifiedId) should contain nothing.
  EXPECT_THAT(
      GetJoinData(*qualified_id_join_index_, /*schema_type_id=*/fake_type_id_,
                  /*joinable_property_id=*/fake_type_joinable_property_id_),
      IsOkAndHolds(IsEmpty()));
}

}  // namespace

}  // namespace lib
}  // namespace icing
