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

#include "icing/index/iterator/doc-hit-info-iterator-filter.h"

#include <limits>
#include <memory>
#include <string>
#include <string_view>
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

libtextclassifier3::StatusOr<DocumentStore::CreateResult> CreateDocumentStore(
    const Filesystem* filesystem, const std::string& base_dir,
    const Clock* clock, const SchemaStore* schema_store) {
  return DocumentStore::Create(
      filesystem, base_dir, clock, schema_store,
      /*force_recovery_and_revalidate_documents=*/false,
      /*namespace_id_fingerprint=*/false, /*pre_mapping_fbv=*/false,
      /*use_persistent_hash_map=*/false,
      PortableFileBackedProtoLog<DocumentWrapper>::kDeflateCompressionLevel,
      /*initialize_stats=*/nullptr);
}

class DocHitInfoIteratorDeletedFilterTest : public ::testing::Test {
 protected:
  DocHitInfoIteratorDeletedFilterTest()
      : test_dir_(GetTestTempDir() + "/icing") {}

  void SetUp() override {
    filesystem_.CreateDirectoryRecursively(test_dir_.c_str());
    test_document1_ =
        DocumentBuilder().SetKey("icing", "email/1").SetSchema("email").Build();
    test_document2_ =
        DocumentBuilder().SetKey("icing", "email/2").SetSchema("email").Build();
    test_document3_ =
        DocumentBuilder().SetKey("icing", "email/3").SetSchema("email").Build();

    SchemaProto schema =
        SchemaBuilder()
            .AddType(SchemaTypeConfigBuilder().SetType("email"))
            .Build();
    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, test_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store_->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult create_result,
        CreateDocumentStore(&filesystem_, test_dir_, &fake_clock_,
                            schema_store_.get()));
    document_store_ = std::move(create_result.document_store);
  }

  void TearDown() override {
    // Destroy objects before the whole directory is removed because they
    // persist data in the destructor.
    document_store_.reset();
    schema_store_.reset();
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<DocumentStore> document_store_;
  FakeClock fake_clock_;
  const Filesystem filesystem_;
  const std::string test_dir_;
  DocumentProto test_document1_;
  DocumentProto test_document2_;
  DocumentProto test_document3_;
  DocHitInfoIteratorFilter::Options options_;
};

TEST_F(DocHitInfoIteratorDeletedFilterTest, EmptyOriginalIterator) {
  ICING_ASSERT_OK(document_store_->Put(test_document1_));

  std::unique_ptr<DocHitInfoIterator> original_iterator_empty =
      std::make_unique<DocHitInfoIteratorDummy>();

  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator_empty), document_store_.get(),
      schema_store_.get(), options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator), IsEmpty());
}

TEST_F(DocHitInfoIteratorDeletedFilterTest, DeletedDocumentsAreFiltered) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(test_document1_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(test_document2_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id3,
                             document_store_->Put(test_document3_));
  // Deletes test document 2
  ICING_ASSERT_OK(document_store_->Delete(
      test_document2_.namespace_(), test_document2_.uri(),
      fake_clock_.GetSystemTimeMilliseconds()));

  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(document_id1),
                                           DocHitInfo(document_id2),
                                           DocHitInfo(document_id3)};
  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator),
              ElementsAre(document_id1, document_id3));
}

TEST_F(DocHitInfoIteratorDeletedFilterTest, NonExistingDocumentsAreFiltered) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(test_document1_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(test_document2_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id3,
                             document_store_->Put(test_document3_));

  // Document ids 7, 8, 9 are not existing
  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(document_id1),
                                           DocHitInfo(document_id2),
                                           DocHitInfo(document_id3),
                                           DocHitInfo(7),
                                           DocHitInfo(8),
                                           DocHitInfo(9)};
  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator),
              ElementsAre(document_id1, document_id2, document_id3));
}

TEST_F(DocHitInfoIteratorDeletedFilterTest, NegativeDocumentIdIsIgnored) {
  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(-1)};
  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(filtered_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST_F(DocHitInfoIteratorDeletedFilterTest, InvalidDocumentIdIsIgnored) {
  // kInvalidDocumentId should be skipped.
  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(kInvalidDocumentId)};
  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(filtered_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

TEST_F(DocHitInfoIteratorDeletedFilterTest, GreaterThanMaxDocumentIdIsIgnored) {
  // Document ids that are greater than the max value is invalid and should be
  // skipped.
  DocumentId invalid_greater_than_max = kMaxDocumentId + 2;
  std::vector<DocHitInfo> doc_hit_infos = {
      DocHitInfo(invalid_greater_than_max)};
  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(filtered_iterator.Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
}

class DocHitInfoIteratorNamespaceFilterTest : public ::testing::Test {
 protected:
  DocHitInfoIteratorNamespaceFilterTest()
      : test_dir_(GetTestTempDir() + "/icing") {}

  void SetUp() override {
    filesystem_.CreateDirectoryRecursively(test_dir_.c_str());
    document1_namespace1_ = DocumentBuilder()
                                .SetKey(namespace1_, "email/1")
                                .SetSchema("email")
                                .Build();
    document2_namespace1_ = DocumentBuilder()
                                .SetKey(namespace1_, "email/2")
                                .SetSchema("email")
                                .Build();
    document1_namespace2_ = DocumentBuilder()
                                .SetKey(namespace2_, "email/1")
                                .SetSchema("email")
                                .Build();
    document1_namespace3_ = DocumentBuilder()
                                .SetKey(namespace3_, "email/1")
                                .SetSchema("email")
                                .Build();

    SchemaProto schema =
        SchemaBuilder()
            .AddType(SchemaTypeConfigBuilder().SetType("email"))
            .Build();
    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, test_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store_->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult create_result,
        CreateDocumentStore(&filesystem_, test_dir_, &fake_clock_,
                            schema_store_.get()));
    document_store_ = std::move(create_result.document_store);
  }

  void TearDown() override {
    // Destroy objects before the whole directory is removed because they
    // persist data in the destructor.
    document_store_.reset();
    schema_store_.reset();
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<DocumentStore> document_store_;
  FakeClock fake_clock_;
  const Filesystem filesystem_;
  const std::string test_dir_;
  const std::string namespace1_ = "namespace1";
  const std::string namespace2_ = "namespace2";
  const std::string namespace3_ = "namespace3";
  DocumentProto document1_namespace1_;
  DocumentProto document2_namespace1_;
  DocumentProto document1_namespace2_;
  DocumentProto document1_namespace3_;
  DocHitInfoIteratorFilter::Options options_;
};

TEST_F(DocHitInfoIteratorNamespaceFilterTest, EmptyOriginalIterator) {
  std::unique_ptr<DocHitInfoIterator> original_iterator_empty =
      std::make_unique<DocHitInfoIteratorDummy>();

  options_.namespaces = std::vector<std::string_view>{};
  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator_empty), document_store_.get(),
      schema_store_.get(), options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator), IsEmpty());
}

TEST_F(DocHitInfoIteratorNamespaceFilterTest,
       NonexistentNamespacesReturnsEmpty) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document1_namespace1_));

  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(document_id1)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  options_.namespaces = std::vector<std::string_view>{"nonexistent_namespace"};
  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator), IsEmpty());
}

TEST_F(DocHitInfoIteratorNamespaceFilterTest, NoNamespacesReturnsAll) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document1_namespace1_));

  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(document_id1)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  options_.namespaces = std::vector<std::string_view>{};
  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator), ElementsAre(document_id1));
}

TEST_F(DocHitInfoIteratorNamespaceFilterTest,
       FilterOutExistingDocumentFromDifferentNamespace) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document1_namespace1_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document2_namespace1_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id3,
                             document_store_->Put(document1_namespace2_));

  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(document_id1),
                                           DocHitInfo(document_id2),
                                           DocHitInfo(document_id3)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  options_.namespaces = std::vector<std::string_view>{namespace1_};
  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator),
              ElementsAre(document_id1, document_id2));
}

TEST_F(DocHitInfoIteratorNamespaceFilterTest, FilterForMultipleNamespacesOk) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document1_namespace1_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document2_namespace1_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id3,
                             document_store_->Put(document1_namespace2_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id4,
                             document_store_->Put(document1_namespace3_));

  std::vector<DocHitInfo> doc_hit_infos = {
      DocHitInfo(document_id1), DocHitInfo(document_id2),
      DocHitInfo(document_id3), DocHitInfo(document_id4)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  options_.namespaces = std::vector<std::string_view>{namespace1_, namespace3_};
  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator),
              ElementsAre(document_id1, document_id2, document_id4));
}

class DocHitInfoIteratorSchemaTypeFilterTest : public ::testing::Test {
 protected:
  static constexpr std::string_view kSchema1 = "email";
  static constexpr std::string_view kSchema2 = "message";
  static constexpr std::string_view kSchema3 = "person";
  static constexpr std::string_view kSchema4 = "artist";
  static constexpr std::string_view kSchema5 = "emailMessage";

  DocHitInfoIteratorSchemaTypeFilterTest()
      : test_dir_(GetTestTempDir() + "/icing") {}

  void SetUp() override {
    filesystem_.CreateDirectoryRecursively(test_dir_.c_str());
    document1_schema1_ = DocumentBuilder()
                             .SetKey("namespace", "1")
                             .SetSchema(std::string(kSchema1))
                             .Build();
    document2_schema2_ = DocumentBuilder()
                             .SetKey("namespace", "2")
                             .SetSchema(std::string(kSchema2))
                             .Build();
    document3_schema3_ = DocumentBuilder()
                             .SetKey("namespace", "3")
                             .SetSchema(std::string(kSchema3))
                             .Build();
    document4_schema1_ = DocumentBuilder()
                             .SetKey("namespace", "4")
                             .SetSchema(std::string(kSchema1))
                             .Build();

    SchemaProto schema =
        SchemaBuilder()
            .AddType(SchemaTypeConfigBuilder().SetType(kSchema1))
            .AddType(SchemaTypeConfigBuilder().SetType(kSchema2))
            .AddType(SchemaTypeConfigBuilder().SetType(kSchema3))
            .AddType(SchemaTypeConfigBuilder().SetType(kSchema4).AddParentType(
                kSchema3))
            .AddType(SchemaTypeConfigBuilder()
                         .SetType(std::string(kSchema5))
                         .AddParentType(kSchema1)
                         .AddParentType(kSchema2))
            .Build();
    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, test_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store_->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult create_result,
        CreateDocumentStore(&filesystem_, test_dir_, &fake_clock_,
                            schema_store_.get()));
    document_store_ = std::move(create_result.document_store);
  }

  void TearDown() override {
    // Destroy objects before the whole directory is removed because they
    // persist data in the destructor.
    document_store_.reset();
    schema_store_.reset();
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<DocumentStore> document_store_;
  FakeClock fake_clock_;
  const Filesystem filesystem_;
  const std::string test_dir_;
  DocumentProto document1_schema1_;
  DocumentProto document2_schema2_;
  DocumentProto document3_schema3_;
  DocumentProto document4_schema1_;
  DocHitInfoIteratorFilter::Options options_;
};

TEST_F(DocHitInfoIteratorSchemaTypeFilterTest, EmptyOriginalIterator) {
  std::unique_ptr<DocHitInfoIterator> original_iterator_empty =
      std::make_unique<DocHitInfoIteratorDummy>();

  options_.schema_types = std::vector<std::string_view>{};
  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator_empty), document_store_.get(),
      schema_store_.get(), options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator), IsEmpty());
}

TEST_F(DocHitInfoIteratorSchemaTypeFilterTest,
       NonexistentSchemaTypeReturnsEmpty) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document1_schema1_));

  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(document_id1)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  options_.schema_types =
      std::vector<std::string_view>{"nonexistent_schema_type"};
  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator), IsEmpty());
}

TEST_F(DocHitInfoIteratorSchemaTypeFilterTest, NoSchemaTypesReturnsAll) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document1_schema1_));

  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(document_id1)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  options_.schema_types = std::vector<std::string_view>{};
  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator), ElementsAre(document_id1));
}

TEST_F(DocHitInfoIteratorSchemaTypeFilterTest,
       FilterOutExistingDocumentFromDifferentSchemaTypes) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document1_schema1_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document2_schema2_));

  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(document_id1),
                                           DocHitInfo(document_id2)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  options_.schema_types = std::vector<std::string_view>{kSchema1};
  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator), ElementsAre(document_id1));
}

TEST_F(DocHitInfoIteratorSchemaTypeFilterTest, FilterForMultipleSchemaTypesOk) {
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document1_schema1_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document2_schema2_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id3,
                             document_store_->Put(document3_schema3_));

  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(document_id1),
                                           DocHitInfo(document_id2),
                                           DocHitInfo(document_id3)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  options_.schema_types = std::vector<std::string_view>{kSchema2, kSchema3};
  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator),
              ElementsAre(document_id2, document_id3));
}

TEST_F(DocHitInfoIteratorSchemaTypeFilterTest,
       FilterForSchemaTypePolymorphismOk) {
  // Add some irrelevant documents.
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store_->Put(document1_schema1_));
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id2,
                             document_store_->Put(document2_schema2_));

  // Create a person document and an artist document, where the artist should be
  // able to be interpreted as a person by polymorphism.
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId person_document_id,
      document_store_->Put(DocumentBuilder()
                               .SetKey("namespace", "person")
                               .SetSchema("person")
                               .Build()));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId artist_document_id,
      document_store_->Put(DocumentBuilder()
                               .SetKey("namespace", "artist")
                               .SetSchema("artist")
                               .Build()));

  std::vector<DocHitInfo> doc_hit_infos = {
      DocHitInfo(document_id1), DocHitInfo(document_id2),
      DocHitInfo(person_document_id), DocHitInfo(artist_document_id)};

  // Filters for the "person" type should also include the "artist" type.
  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);
  options_.schema_types = {"person"};
  DocHitInfoIteratorFilter filtered_iterator_1(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());
  EXPECT_THAT(GetDocumentIds(&filtered_iterator_1),
              ElementsAre(person_document_id, artist_document_id));

  // Filters for the "artist" type should not include the "person" type.
  original_iterator = std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);
  options_.schema_types = {"artist"};
  DocHitInfoIteratorFilter filtered_iterator_2(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());
  EXPECT_THAT(GetDocumentIds(&filtered_iterator_2),
              ElementsAre(artist_document_id));
}

TEST_F(DocHitInfoIteratorSchemaTypeFilterTest,
       FilterForSchemaTypeMultipleParentPolymorphismOk) {
  // Create an email and a message document.
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId email_document_id,
      document_store_->Put(DocumentBuilder()
                               .SetKey("namespace", "email")
                               .SetSchema("email")
                               .Build()));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId message_document_id,
      document_store_->Put(DocumentBuilder()
                               .SetKey("namespace", "message")
                               .SetSchema("message")
                               .Build()));

  // Create a emailMessage document, which the should be able to be interpreted
  // as both an email and a message by polymorphism.
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId email_message_document_id,
      document_store_->Put(DocumentBuilder()
                               .SetKey("namespace", "emailMessage")
                               .SetSchema("emailMessage")
                               .Build()));

  std::vector<DocHitInfo> doc_hit_infos = {
      DocHitInfo(email_document_id), DocHitInfo(message_document_id),
      DocHitInfo(email_message_document_id)};

  // Filters for the "email" type should also include the "emailMessage" type.
  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);
  options_.schema_types = std::vector<std::string_view>{"email"};
  DocHitInfoIteratorFilter filtered_iterator_1(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());
  EXPECT_THAT(GetDocumentIds(&filtered_iterator_1),
              ElementsAre(email_document_id, email_message_document_id));

  // Filters for the "message" type should also include the "emailMessage" type.
  original_iterator = std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);
  options_.schema_types = std::vector<std::string_view>{"message"};
  DocHitInfoIteratorFilter filtered_iterator_2(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());
  EXPECT_THAT(GetDocumentIds(&filtered_iterator_2),
              ElementsAre(message_document_id, email_message_document_id));

  // Filters for a irrelevant type should return nothing.
  original_iterator = std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);
  options_.schema_types = std::vector<std::string_view>{"person"};
  DocHitInfoIteratorFilter filtered_iterator_3(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());
  EXPECT_THAT(GetDocumentIds(&filtered_iterator_3), IsEmpty());
}

class DocHitInfoIteratorExpirationFilterTest : public ::testing::Test {
 protected:
  DocHitInfoIteratorExpirationFilterTest()
      : test_dir_(GetTestTempDir() + "/icing") {}

  void SetUp() override {
    filesystem_.CreateDirectoryRecursively(test_dir_.c_str());

    SchemaProto schema =
        SchemaBuilder()
            .AddType(SchemaTypeConfigBuilder().SetType(email_schema_))
            .Build();
    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, test_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store_->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult create_result,
        CreateDocumentStore(&filesystem_, test_dir_, &fake_clock_,
                            schema_store_.get()));
    document_store_ = std::move(create_result.document_store);
  }

  void TearDown() override {
    // Destroy objects before the whole directory is removed because they
    // persist data in the destructor.
    document_store_.reset();
    schema_store_.reset();
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<DocumentStore> document_store_;
  FakeClock fake_clock_;
  const Filesystem filesystem_;
  const std::string test_dir_;
  const std::string email_schema_ = "email";
  DocHitInfoIteratorFilter::Options options_;
};

TEST_F(DocHitInfoIteratorExpirationFilterTest, TtlZeroIsntFilteredOut) {
  // Arbitrary value
  fake_clock_.SetSystemTimeMilliseconds(100);

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentStore::CreateResult create_result,
      CreateDocumentStore(&filesystem_, test_dir_, &fake_clock_,
                          schema_store_.get()));
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  // Insert a document
  DocumentProto document = DocumentBuilder()
                               .SetKey("namespace", "1")
                               .SetSchema(email_schema_)
                               .SetCreationTimestampMs(0)
                               .SetTtlMs(0)
                               .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store->Put(document));

  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(document_id1)};
  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator), ElementsAre(document_id1));
}

TEST_F(DocHitInfoIteratorExpirationFilterTest, BeforeTtlNotFilteredOut) {
  // Arbitrary value, but must be less than document's creation_timestamp + ttl
  fake_clock_.SetSystemTimeMilliseconds(50);

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentStore::CreateResult create_result,
      CreateDocumentStore(&filesystem_, test_dir_, &fake_clock_,
                          schema_store_.get()));
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  // Insert a document
  DocumentProto document = DocumentBuilder()
                               .SetKey("namespace", "1")
                               .SetSchema(email_schema_)
                               .SetCreationTimestampMs(1)
                               .SetTtlMs(100)
                               .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store->Put(document));

  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(document_id1)};
  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator), ElementsAre(document_id1));
}

TEST_F(DocHitInfoIteratorExpirationFilterTest, EqualTtlFilteredOut) {
  // Current time is exactly the document's creation_timestamp + ttl
  fake_clock_.SetSystemTimeMilliseconds(150);

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentStore::CreateResult create_result,
      CreateDocumentStore(&filesystem_, test_dir_, &fake_clock_,
                          schema_store_.get()));
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  // Insert a document
  DocumentProto document = DocumentBuilder()
                               .SetKey("namespace", "1")
                               .SetSchema(email_schema_)
                               .SetCreationTimestampMs(50)
                               .SetTtlMs(100)
                               .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store->Put(document));

  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(document_id1)};
  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator), IsEmpty());
}

TEST_F(DocHitInfoIteratorExpirationFilterTest, PastTtlFilteredOut) {
  // Arbitrary value, but must be greater than the document's
  // creation_timestamp + ttl
  fake_clock_.SetSystemTimeMilliseconds(151);

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentStore::CreateResult create_result,
      CreateDocumentStore(&filesystem_, test_dir_, &fake_clock_,
                          schema_store_.get()));
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  // Insert a document
  DocumentProto document = DocumentBuilder()
                               .SetKey("namespace", "1")
                               .SetSchema(email_schema_)
                               .SetCreationTimestampMs(50)
                               .SetTtlMs(100)
                               .Build();
  ICING_ASSERT_OK_AND_ASSIGN(DocumentId document_id1,
                             document_store->Put(document));

  std::vector<DocHitInfo> doc_hit_infos = {DocHitInfo(document_id1)};
  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store.get(), schema_store_.get(),
      options_, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator), IsEmpty());
}

class DocHitInfoIteratorFilterTest : public ::testing::Test {
 protected:
  DocHitInfoIteratorFilterTest() : test_dir_(GetTestTempDir() + "/icing") {}

  void SetUp() override {
    filesystem_.CreateDirectoryRecursively(test_dir_.c_str());
    document1_namespace1_schema1_ = DocumentBuilder()
                                        .SetKey(namespace1_, "1")
                                        .SetSchema(schema1_)
                                        .SetCreationTimestampMs(100)
                                        .SetTtlMs(100)
                                        .Build();
    document2_namespace1_schema1_ = DocumentBuilder()
                                        .SetKey(namespace1_, "2")
                                        .SetSchema(schema1_)
                                        .SetCreationTimestampMs(100)
                                        .SetTtlMs(100)
                                        .Build();
    document3_namespace2_schema1_ = DocumentBuilder()
                                        .SetKey(namespace2_, "3")
                                        .SetSchema(schema1_)
                                        .SetCreationTimestampMs(100)
                                        .SetTtlMs(100)
                                        .Build();
    document4_namespace1_schema2_ = DocumentBuilder()
                                        .SetKey(namespace1_, "4")
                                        .SetSchema(schema2_)
                                        .SetCreationTimestampMs(100)
                                        .SetTtlMs(100)
                                        .Build();
    document5_namespace1_schema1_ = DocumentBuilder()
                                        .SetKey(namespace1_, "5")
                                        .SetSchema(schema1_)
                                        .SetCreationTimestampMs(1)
                                        .SetTtlMs(100)
                                        .Build();

    SchemaProto schema =
        SchemaBuilder()
            .AddType(SchemaTypeConfigBuilder().SetType(schema1_))
            .AddType(SchemaTypeConfigBuilder().SetType(schema2_))
            .Build();
    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, test_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store_->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult create_result,
        CreateDocumentStore(&filesystem_, test_dir_, &fake_clock_,
                            schema_store_.get()));
    document_store_ = std::move(create_result.document_store);
  }

  void TearDown() override {
    // Destroy objects before the whole directory is removed because they
    // persist data in the destructor.
    document_store_.reset();
    schema_store_.reset();
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<DocumentStore> document_store_;
  FakeClock fake_clock_;
  const Filesystem filesystem_;
  const std::string test_dir_;
  const std::string namespace1_ = "namespace1";
  const std::string namespace2_ = "namespace2";
  const std::string schema1_ = "email";
  const std::string schema2_ = "message";
  DocumentProto document1_namespace1_schema1_;
  DocumentProto document2_namespace1_schema1_;
  DocumentProto document3_namespace2_schema1_;
  DocumentProto document4_namespace1_schema2_;
  DocumentProto document5_namespace1_schema1_;
};

TEST_F(DocHitInfoIteratorFilterTest, CombineAllFiltersOk) {
  // Filters out document5 since it's expired
  fake_clock_.SetSystemTimeMilliseconds(199);

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentStore::CreateResult create_result,
      CreateDocumentStore(&filesystem_, test_dir_, &fake_clock_,
                          schema_store_.get()));
  std::unique_ptr<DocumentStore> document_store =
      std::move(create_result.document_store);

  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store->Put(document1_namespace1_schema1_));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store->Put(document2_namespace1_schema1_));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id3,
      document_store->Put(document3_namespace2_schema1_));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id4,
      document_store->Put(document4_namespace1_schema2_));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id5,
      document_store->Put(document5_namespace1_schema1_));

  // Deletes document2, causing it to be filtered out
  ICING_ASSERT_OK(
      document_store->Delete(document2_namespace1_schema1_.namespace_(),
                             document2_namespace1_schema1_.uri(),
                             fake_clock_.GetSystemTimeMilliseconds()));

  std::vector<DocHitInfo> doc_hit_infos = {
      DocHitInfo(document_id1), DocHitInfo(document_id2),
      DocHitInfo(document_id3), DocHitInfo(document_id4),
      DocHitInfo(document_id5)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  DocHitInfoIteratorFilter::Options options;

  // Filters out document3 by namespace
  options.namespaces = std::vector<std::string_view>{namespace1_};

  // Filters out document4 by schema type
  options.schema_types = std::vector<std::string_view>{schema1_};

  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store.get(), schema_store_.get(),
      options, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocumentIds(&filtered_iterator), ElementsAre(document_id1));
}

TEST_F(DocHitInfoIteratorFilterTest, SectionIdMasksArePopulatedCorrectly) {
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store_->Put(document1_namespace1_schema1_));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store_->Put(document2_namespace1_schema1_));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id3,
      document_store_->Put(document3_namespace2_schema1_));

  SectionIdMask section_id_mask1 = 0b01001001;  // hits in sections 0, 3, 6
  SectionIdMask section_id_mask2 = 0b10010010;  // hits in sections 1, 4, 7
  SectionIdMask section_id_mask3 = 0b00100100;  // hits in sections 2, 5
  std::vector<SectionId> section_ids1 = {0, 3, 6};
  std::vector<SectionId> section_ids2 = {1, 4, 7};
  std::vector<SectionId> section_ids3 = {2, 5};
  std::vector<DocHitInfo> doc_hit_infos = {
      DocHitInfo(document_id1, section_id_mask1),
      DocHitInfo(document_id2, section_id_mask2),
      DocHitInfo(document_id3, section_id_mask3)};

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorDummy>(doc_hit_infos);

  DocHitInfoIteratorFilter::Options options;
  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(GetDocHitInfos(&filtered_iterator),
              ElementsAre(EqualsDocHitInfo(document_id1, section_ids1),
                          EqualsDocHitInfo(document_id2, section_ids2),
                          EqualsDocHitInfo(document_id3, section_ids3)));
}

TEST_F(DocHitInfoIteratorFilterTest, GetCallStats) {
  DocHitInfoIterator::CallStats original_call_stats(
      /*num_leaf_advance_calls_lite_index_in=*/2,
      /*num_leaf_advance_calls_main_index_in=*/5,
      /*num_leaf_advance_calls_integer_index_in=*/3,
      /*num_leaf_advance_calls_no_index_in=*/1,
      /*num_blocks_inspected_in=*/4);  // arbitrary value
  auto original_iterator = std::make_unique<DocHitInfoIteratorDummy>();
  original_iterator->SetCallStats(original_call_stats);

  DocHitInfoIteratorFilter::Options options;
  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options, fake_clock_.GetSystemTimeMilliseconds());

  EXPECT_THAT(filtered_iterator.GetCallStats(), Eq(original_call_stats));
}

TEST_F(DocHitInfoIteratorFilterTest, TrimFilterIterator) {
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id1,
      document_store_->Put(document1_namespace1_schema1_));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id2,
      document_store_->Put(document2_namespace1_schema1_));
  ICING_ASSERT_OK_AND_ASSIGN(
      DocumentId document_id3,
      document_store_->Put(document3_namespace2_schema1_));

  // Build an interator tree like:
  //                Filter
  //                   |
  //                  AND
  //             /           \
  //          {1, 3}         {2}
  std::vector<DocHitInfo> left_vector = {DocHitInfo(document_id1),
                                         DocHitInfo(document_id3)};
  std::vector<DocHitInfo> right_vector = {DocHitInfo(document_id2)};

  std::unique_ptr<DocHitInfoIterator> left_iter =
      std::make_unique<DocHitInfoIteratorDummy>(left_vector);
  std::unique_ptr<DocHitInfoIterator> right_iter =
      std::make_unique<DocHitInfoIteratorDummy>(right_vector, "term", 10);

  std::unique_ptr<DocHitInfoIterator> original_iterator =
      std::make_unique<DocHitInfoIteratorAnd>(std::move(left_iter),
                                              std::move(right_iter));

  DocHitInfoIteratorFilter::Options options;
  // Filters out document3 by namespace
  options.namespaces = std::vector<std::string_view>{namespace1_};
  DocHitInfoIteratorFilter filtered_iterator(
      std::move(original_iterator), document_store_.get(), schema_store_.get(),
      options, fake_clock_.GetSystemTimeMilliseconds());

  // The trimmed tree.
  //          Filter
  //             |
  //          {1, 3}
  ICING_ASSERT_OK_AND_ASSIGN(DocHitInfoIterator::TrimmedNode trimmed_node,
                             std::move(filtered_iterator).TrimRightMostNode());
  EXPECT_THAT(trimmed_node.term_, Eq("term"));
  EXPECT_THAT(trimmed_node.term_start_index_, Eq(10));
  EXPECT_THAT(GetDocumentIds(trimmed_node.iterator_.get()),
              ElementsAre(document_id1));
}

}  // namespace

}  // namespace lib
}  // namespace icing
