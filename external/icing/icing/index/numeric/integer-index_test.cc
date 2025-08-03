// Copyright (C) 2022 Google LLC
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

#include "icing/index/numeric/integer-index.h"

#include <cstdint>
#include <limits>
#include <memory>
#include <string>
#include <string_view>
#include <type_traits>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/numeric/dummy-numeric-index.h"
#include "icing/index/numeric/integer-index-storage.h"
#include "icing/index/numeric/numeric-index.h"
#include "icing/index/numeric/posting-list-integer-index-serializer.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/schema-builder.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::ElementsAre;
using ::testing::ElementsAreArray;
using ::testing::Eq;
using ::testing::HasSubstr;
using ::testing::IsEmpty;
using ::testing::IsFalse;
using ::testing::IsTrue;
using ::testing::Lt;

using Crcs = PersistentStorage::Crcs;
using Info = IntegerIndex::Info;

static constexpr int32_t kCorruptedValueOffset = 3;
constexpr static std::string_view kDefaultTestPropertyPath = "test.property";

constexpr SectionId kDefaultSectionId = 0;

template <typename T>
class NumericIndexIntegerTest : public ::testing::Test {
 protected:
  void SetUp() override {
    base_dir_ = GetTestTempDir() + "/icing";
    ASSERT_THAT(filesystem_.CreateDirectoryRecursively(base_dir_.c_str()),
                IsTrue());

    working_path_ = base_dir_ + "/numeric_index_integer_test";
    std::string schema_dir = base_dir_ + "/schema_test";

    ASSERT_TRUE(filesystem_.CreateDirectoryRecursively(schema_dir.c_str()));
    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_, SchemaStore::Create(&filesystem_, schema_dir, &clock_));

    std::string document_store_dir = base_dir_ + "/doc_store_test";
    ASSERT_TRUE(
        filesystem_.CreateDirectoryRecursively(document_store_dir.c_str()));
    ICING_ASSERT_OK_AND_ASSIGN(
        DocumentStore::CreateResult doc_store_create_result,
        DocumentStore::Create(
            &filesystem_, document_store_dir, &clock_, schema_store_.get(),
            /*force_recovery_and_revalidate_documents=*/false,
            /*namespace_id_fingerprint=*/false, /*pre_mapping_fbv=*/false,
            /*use_persistent_hash_map=*/false,
            PortableFileBackedProtoLog<
                DocumentWrapper>::kDeflateCompressionLevel,
            /*initialize_stats=*/nullptr));
    doc_store_ = std::move(doc_store_create_result.document_store);
  }

  void TearDown() override {
    doc_store_.reset();
    schema_store_.reset();
    filesystem_.DeleteDirectoryRecursively(base_dir_.c_str());
  }

  template <typename UnknownIntegerIndexType>
  libtextclassifier3::StatusOr<std::unique_ptr<NumericIndex<int64_t>>>
  CreateIntegerIndex() {
    return absl_ports::InvalidArgumentError("Unknown type");
  }

  template <>
  libtextclassifier3::StatusOr<std::unique_ptr<NumericIndex<int64_t>>>
  CreateIntegerIndex<DummyNumericIndex<int64_t>>() {
    return DummyNumericIndex<int64_t>::Create(filesystem_, working_path_);
  }

  template <>
  libtextclassifier3::StatusOr<std::unique_ptr<NumericIndex<int64_t>>>
  CreateIntegerIndex<IntegerIndex>() {
    return IntegerIndex::Create(
        filesystem_, working_path_, /*num_data_threshold_for_bucket_split=*/
        IntegerIndexStorage::kDefaultNumDataThresholdForBucketSplit,
        /*pre_mapping_fbv=*/false);
  }

  template <typename NotIntegerIndexType>
  bool is_integer_index() const {
    return false;
  }

  template <>
  bool is_integer_index<IntegerIndex>() const {
    return true;
  }

  libtextclassifier3::StatusOr<std::vector<DocumentId>> CompactDocStore() {
    std::string document_store_dir = base_dir_ + "/doc_store_test";
    std::string document_store_compact_dir =
        base_dir_ + "/doc_store_compact_test";
    if (!filesystem_.CreateDirectoryRecursively(
            document_store_compact_dir.c_str())) {
      return absl_ports::InternalError("Unable to create compact directory");
    }
    ICING_ASSIGN_OR_RETURN(
        DocumentStore::OptimizeResult doc_store_optimize_result,
        doc_store_->OptimizeInto(document_store_compact_dir, nullptr));

    doc_store_.reset();
    if (!filesystem_.SwapFiles(document_store_dir.c_str(),
                               document_store_compact_dir.c_str())) {
      return absl_ports::InternalError("Unable to swap directories.");
    }
    if (!filesystem_.DeleteDirectoryRecursively(
            document_store_compact_dir.c_str())) {
      return absl_ports::InternalError("Unable to delete compact directory");
    }

    ICING_ASSIGN_OR_RETURN(
        DocumentStore::CreateResult doc_store_create_result,
        DocumentStore::Create(
            &filesystem_, document_store_dir, &clock_, schema_store_.get(),
            /*force_recovery_and_revalidate_documents=*/false,
            /*namespace_id_fingerprint=*/false, /*pre_mapping_fbv=*/false,
            /*use_persistent_hash_map=*/false,
            PortableFileBackedProtoLog<
                DocumentWrapper>::kDeflateCompressionLevel,
            /*initialize_stats=*/nullptr));
    doc_store_ = std::move(doc_store_create_result.document_store);
    return std::move(doc_store_optimize_result.document_id_old_to_new);
  }

  libtextclassifier3::StatusOr<std::vector<DocHitInfo>> Query(
      const NumericIndex<int64_t>* integer_index,
      std::string_view property_path, int64_t key_lower, int64_t key_upper) {
    ICING_ASSIGN_OR_RETURN(
        std::unique_ptr<DocHitInfoIterator> iter,
        integer_index->GetIterator(property_path, key_lower, key_upper,
                                   *doc_store_, *schema_store_,
                                   clock_.GetSystemTimeMilliseconds()));

    std::vector<DocHitInfo> result;
    while (iter->Advance().ok()) {
      result.push_back(iter->doc_hit_info());
    }
    return result;
  }

  Filesystem filesystem_;
  std::string base_dir_;
  std::string working_path_;
  std::unique_ptr<SchemaStore> schema_store_;
  std::unique_ptr<DocumentStore> doc_store_;
  Clock clock_;
};

void Index(NumericIndex<int64_t>* integer_index, std::string_view property_path,
           DocumentId document_id, SectionId section_id,
           std::vector<int64_t> keys) {
  std::unique_ptr<NumericIndex<int64_t>::Editor> editor =
      integer_index->Edit(property_path, document_id, section_id);

  for (const auto& key : keys) {
    ICING_EXPECT_OK(editor->BufferKey(key));
  }
  ICING_EXPECT_OK(std::move(*editor).IndexAllBufferedKeys());
}

using TestTypes = ::testing::Types<DummyNumericIndex<int64_t>, IntegerIndex>;
TYPED_TEST_SUITE(NumericIndexIntegerTest, TestTypes);

TYPED_TEST(NumericIndexIntegerTest, SetLastAddedDocumentId) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  EXPECT_THAT(integer_index->last_added_document_id(), Eq(kInvalidDocumentId));

  constexpr DocumentId kDocumentId = 100;
  integer_index->set_last_added_document_id(kDocumentId);
  EXPECT_THAT(integer_index->last_added_document_id(), Eq(kDocumentId));

  constexpr DocumentId kNextDocumentId = 123;
  integer_index->set_last_added_document_id(kNextDocumentId);
  EXPECT_THAT(integer_index->last_added_document_id(), Eq(kNextDocumentId));
}

TYPED_TEST(
    NumericIndexIntegerTest,
    SetLastAddedDocumentIdShouldIgnoreNewDocumentIdNotGreaterThanTheCurrent) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  constexpr DocumentId kDocumentId = 123;
  integer_index->set_last_added_document_id(kDocumentId);
  ASSERT_THAT(integer_index->last_added_document_id(), Eq(kDocumentId));

  constexpr DocumentId kNextDocumentId = 100;
  ASSERT_THAT(kNextDocumentId, Lt(kDocumentId));
  integer_index->set_last_added_document_id(kNextDocumentId);
  // last_added_document_id() should remain unchanged.
  EXPECT_THAT(integer_index->last_added_document_id(), Eq(kDocumentId));
}

TYPED_TEST(NumericIndexIntegerTest, SingleKeyExactQuery) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
        kDefaultSectionId, /*keys=*/{1});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        kDefaultSectionId, /*keys=*/{3});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        kDefaultSectionId, /*keys=*/{2});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/3,
        kDefaultSectionId, /*keys=*/{0});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/4,
        kDefaultSectionId, /*keys=*/{4});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/5,
        kDefaultSectionId, /*keys=*/{2});

  int64_t query_key = 2;
  std::vector<SectionId> expected_sections = {kDefaultSectionId};
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/query_key, /*key_upper=*/query_key),
              IsOkAndHolds(ElementsAre(
                  EqualsDocHitInfo(/*document_id=*/5, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/2, expected_sections))));
}

TYPED_TEST(NumericIndexIntegerTest, SingleKeyRangeQuery) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
        kDefaultSectionId, /*keys=*/{1});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        kDefaultSectionId, /*keys=*/{3});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        kDefaultSectionId, /*keys=*/{2});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/3,
        kDefaultSectionId, /*keys=*/{0});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/4,
        kDefaultSectionId, /*keys=*/{4});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/5,
        kDefaultSectionId, /*keys=*/{2});

  std::vector<SectionId> expected_sections = {kDefaultSectionId};
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/1, /*key_upper=*/3),
              IsOkAndHolds(ElementsAre(
                  EqualsDocHitInfo(/*document_id=*/5, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/2, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/1, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/0, expected_sections))));
}

TYPED_TEST(NumericIndexIntegerTest, WildcardStorageQuery) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  // This test sets its schema assuming that max property storages == 32.
  ASSERT_THAT(IntegerIndex::kMaxPropertyStorages, Eq(32));

  PropertyConfigProto int_property_config =
      PropertyConfigBuilder()
          .SetName("otherProperty1")
          .SetCardinality(CARDINALITY_REPEATED)
          .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
          .Build();
  // Create a schema with two types:
  // - TypeA has 34 properties:
  //     'desiredProperty', 'otherProperty'*, 'undesiredProperty'
  // - TypeB has 2 properties: 'anotherProperty', 'desiredProperty'
  // 1. The 32 'otherProperty's will consume all of the individual storages
  // 2. TypeA.desiredProperty and TypeB.anotherProperty will both be assigned
  //    SectionId = 0 for their respective types.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("TypeA")
                       .AddProperty(int_property_config)
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty2"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty3"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty4"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty5"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty6"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty7"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty8"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty9"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty10"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty11"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty12"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty13"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty14"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty15"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty16"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty17"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty18"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty19"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty20"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty21"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty22"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty23"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty24"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty25"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty26"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty27"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty28"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty29"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty30"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty31"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty32"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("desiredProperty"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("undesiredProperty")))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("TypeB")
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("anotherProperty"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("desiredProperty")))
          .Build();
  ICING_ASSERT_OK(this->schema_store_->SetSchema(
      schema,
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Put 11 docs of "TypeA" into the document store.
  DocumentProto doc =
      DocumentBuilder().SetKey("ns1", "uri0").SetSchema("TypeA").Build();
  ICING_ASSERT_OK(this->doc_store_->Put(doc));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri1").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri2").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri3").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri4").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri5").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri6").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri7").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri8").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri9").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri10").Build()));

  // Put 5 docs of "TypeB" into the document store.
  doc = DocumentBuilder(doc).SetUri("uri11").SetSchema("TypeB").Build();
  ICING_ASSERT_OK(this->doc_store_->Put(doc));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri12").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri13").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri14").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri15").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri16").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri17").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri18").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri19").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri20").Build()));

  // Ids are assigned alphabetically, so the property ids are:
  // TypeA.desiredProperty = 0
  // TypeA.otherPropertyN = N
  // TypeA.undesiredProperty = 33
  // TypeB.anotherProperty = 0
  // TypeB.desiredProperty = 1
  SectionId typea_desired_prop_id = 0;
  SectionId typea_undesired_prop_id = 33;
  SectionId typeb_another_prop_id = 0;
  SectionId typeb_desired_prop_id = 1;

  // Index numeric content for other properties to force our property into the
  // wildcard storage.
  std::string other_property_path = "otherProperty";
  for (int i = 1; i <= IntegerIndex::kMaxPropertyStorages; ++i) {
    Index(integer_index.get(),
          absl_ports::StrCat(other_property_path, std::to_string(i)),
          /*document_id=*/0, /*section_id=*/i, /*keys=*/{i});
  }

  // Index numeric content for TypeA.desiredProperty
  std::string desired_property = "desiredProperty";
  Index(integer_index.get(), desired_property, /*document_id=*/0,
        typea_desired_prop_id, /*keys=*/{1});
  Index(integer_index.get(), desired_property, /*document_id=*/1,
        typea_desired_prop_id, /*keys=*/{3});
  Index(integer_index.get(), desired_property, /*document_id=*/2,
        typea_desired_prop_id, /*keys=*/{2});
  Index(integer_index.get(), desired_property, /*document_id=*/3,
        typea_desired_prop_id, /*keys=*/{0});
  Index(integer_index.get(), desired_property, /*document_id=*/4,
        typea_desired_prop_id, /*keys=*/{4});
  Index(integer_index.get(), desired_property, /*document_id=*/5,
        typea_desired_prop_id, /*keys=*/{2});

  // Index the same numeric content for TypeA.undesiredProperty
  std::string undesired_property = "undesiredProperty";
  Index(integer_index.get(), undesired_property, /*document_id=*/6,
        typea_undesired_prop_id, /*keys=*/{3});
  Index(integer_index.get(), undesired_property, /*document_id=*/7,
        typea_undesired_prop_id, /*keys=*/{2});
  Index(integer_index.get(), undesired_property, /*document_id=*/8,
        typea_undesired_prop_id, /*keys=*/{0});
  Index(integer_index.get(), undesired_property, /*document_id=*/9,
        typea_undesired_prop_id, /*keys=*/{4});
  Index(integer_index.get(), undesired_property, /*document_id=*/10,
        typea_undesired_prop_id, /*keys=*/{2});

  // Index the same numeric content for TypeB.anotherProperty
  std::string another_property = "anotherProperty";
  Index(integer_index.get(), another_property, /*document_id=*/11,
        typeb_another_prop_id, /*keys=*/{3});
  Index(integer_index.get(), another_property, /*document_id=*/12,
        typeb_another_prop_id, /*keys=*/{2});
  Index(integer_index.get(), another_property, /*document_id=*/13,
        typeb_another_prop_id, /*keys=*/{0});
  Index(integer_index.get(), another_property, /*document_id=*/14,
        typeb_another_prop_id, /*keys=*/{4});
  Index(integer_index.get(), another_property, /*document_id=*/15,
        typeb_another_prop_id, /*keys=*/{2});

  // Finally, index the same numeric content for TypeB.desiredProperty
  Index(integer_index.get(), desired_property, /*document_id=*/16,
        typeb_desired_prop_id, /*keys=*/{3});
  Index(integer_index.get(), desired_property, /*document_id=*/17,
        typeb_desired_prop_id, /*keys=*/{2});
  Index(integer_index.get(), desired_property, /*document_id=*/18,
        typeb_desired_prop_id, /*keys=*/{0});
  Index(integer_index.get(), desired_property, /*document_id=*/19,
        typeb_desired_prop_id, /*keys=*/{4});
  Index(integer_index.get(), desired_property, /*document_id=*/20,
        typeb_desired_prop_id, /*keys=*/{2});

  if (this->template is_integer_index<TypeParam>()) {
    EXPECT_THAT(integer_index->num_property_indices(), Eq(33));
  } else {
    EXPECT_THAT(integer_index->num_property_indices(), Eq(35));
  }

  // Only the hits for 'desired_prop_id' should be returned.
  std::vector<SectionId> expected_sections_typea = {typea_desired_prop_id};
  std::vector<SectionId> expected_sections_typeb = {typeb_desired_prop_id};
  EXPECT_THAT(
      this->Query(integer_index.get(), desired_property,
                  /*key_lower=*/2, /*key_upper=*/2),
      IsOkAndHolds(ElementsAre(
          EqualsDocHitInfo(/*document_id=*/20, expected_sections_typeb),
          EqualsDocHitInfo(/*document_id=*/17, expected_sections_typeb),
          EqualsDocHitInfo(/*document_id=*/5, expected_sections_typea),
          EqualsDocHitInfo(/*document_id=*/2, expected_sections_typea))));

  EXPECT_THAT(
      this->Query(integer_index.get(), desired_property,
                  /*key_lower=*/1, /*key_upper=*/3),
      IsOkAndHolds(ElementsAre(
          EqualsDocHitInfo(/*document_id=*/20, expected_sections_typeb),
          EqualsDocHitInfo(/*document_id=*/17, expected_sections_typeb),
          EqualsDocHitInfo(/*document_id=*/16, expected_sections_typeb),
          EqualsDocHitInfo(/*document_id=*/5, expected_sections_typea),
          EqualsDocHitInfo(/*document_id=*/2, expected_sections_typea),
          EqualsDocHitInfo(/*document_id=*/1, expected_sections_typea),
          EqualsDocHitInfo(/*document_id=*/0, expected_sections_typea))));
}

TYPED_TEST(NumericIndexIntegerTest, EmptyResult) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
        kDefaultSectionId, /*keys=*/{1});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        kDefaultSectionId, /*keys=*/{3});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        kDefaultSectionId, /*keys=*/{2});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/3,
        kDefaultSectionId, /*keys=*/{0});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/4,
        kDefaultSectionId, /*keys=*/{4});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/5,
        kDefaultSectionId, /*keys=*/{2});

  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/10, /*key_upper=*/10),
              IsOkAndHolds(IsEmpty()));
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/100, /*key_upper=*/200),
              IsOkAndHolds(IsEmpty()));
}

TYPED_TEST(NumericIndexIntegerTest,
           NonExistingPropertyPathShouldReturnEmptyResult) {
  constexpr std::string_view kAnotherPropertyPath = "another_property";

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
        kDefaultSectionId, /*keys=*/{1});

  EXPECT_THAT(this->Query(integer_index.get(), kAnotherPropertyPath,
                          /*key_lower=*/100, /*key_upper=*/200),
              IsOkAndHolds(IsEmpty()));
}

TYPED_TEST(NumericIndexIntegerTest,
           MultipleKeysShouldMergeAndDedupeDocHitInfo) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  // Construct several documents with mutiple keys under the same section.
  // Range query [1, 3] will find hits with same (DocumentId, SectionId) for
  // mutiple times. For example, (2, kDefaultSectionId) will be found twice
  // (once for key = 1 and once for key = 3).
  // Test if the iterator dedupes correctly.
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
        kDefaultSectionId, /*keys=*/{-1000, 0});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        kDefaultSectionId, /*keys=*/{-100, 0, 1, 2, 3, 4, 5});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        kDefaultSectionId, /*keys=*/{3, 1});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/3,
        kDefaultSectionId, /*keys=*/{4, 1});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/4,
        kDefaultSectionId, /*keys=*/{1, 6});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/5,
        kDefaultSectionId, /*keys=*/{2, 100});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/6,
        kDefaultSectionId, /*keys=*/{1000, 2});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/7,
        kDefaultSectionId, /*keys=*/{4, -1000});

  std::vector<SectionId> expected_sections = {kDefaultSectionId};
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/1, /*key_upper=*/3),
              IsOkAndHolds(ElementsAre(
                  EqualsDocHitInfo(/*document_id=*/6, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/5, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/4, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/3, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/2, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/1, expected_sections))));
}

TYPED_TEST(NumericIndexIntegerTest, EdgeNumericValues) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
        kDefaultSectionId, /*keys=*/{0});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        kDefaultSectionId, /*keys=*/{-100});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        kDefaultSectionId, /*keys=*/{-80});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/3,
        kDefaultSectionId, /*keys=*/{std::numeric_limits<int64_t>::max()});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/4,
        kDefaultSectionId, /*keys=*/{std::numeric_limits<int64_t>::min()});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/5,
        kDefaultSectionId, /*keys=*/{200});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/6,
        kDefaultSectionId, /*keys=*/{100});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/7,
        kDefaultSectionId, /*keys=*/{std::numeric_limits<int64_t>::max()});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/8,
        kDefaultSectionId, /*keys=*/{0});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/9,
        kDefaultSectionId, /*keys=*/{std::numeric_limits<int64_t>::min()});

  std::vector<SectionId> expected_sections = {kDefaultSectionId};

  // Negative key
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/-100, /*key_upper=*/-70),
              IsOkAndHolds(ElementsAre(
                  EqualsDocHitInfo(/*document_id=*/2, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/1, expected_sections))));

  // INT64_MAX key
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/std::numeric_limits<int64_t>::max(),
                          /*key_upper=*/std::numeric_limits<int64_t>::max()),
              IsOkAndHolds(ElementsAre(
                  EqualsDocHitInfo(/*document_id=*/7, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/3, expected_sections))));

  // INT64_MIN key
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/std::numeric_limits<int64_t>::min(),
                          /*key_upper=*/std::numeric_limits<int64_t>::min()),
              IsOkAndHolds(ElementsAre(
                  EqualsDocHitInfo(/*document_id=*/9, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/4, expected_sections))));

  // Key = 0
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/0, /*key_upper=*/0),
              IsOkAndHolds(ElementsAre(
                  EqualsDocHitInfo(/*document_id=*/8, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/0, expected_sections))));

  // All keys from INT64_MIN to INT64_MAX
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/std::numeric_limits<int64_t>::min(),
                          /*key_upper=*/std::numeric_limits<int64_t>::max()),
              IsOkAndHolds(ElementsAre(
                  EqualsDocHitInfo(/*document_id=*/9, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/8, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/7, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/6, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/5, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/4, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/3, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/2, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/1, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/0, expected_sections))));
}

TYPED_TEST(NumericIndexIntegerTest,
           MultipleSectionsShouldMergeSectionsAndDedupeDocHitInfo) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  // Construct several documents with mutiple numeric sections.
  // Range query [1, 3] will find hits with same DocumentIds but multiple
  // different SectionIds. For example, there will be 2 hits (1, 0), (1, 1) for
  // DocumentId=1.
  // Test if the iterator merges multiple sections into a single SectionIdMask
  // correctly.
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
        /*section_id=*/2, /*keys=*/{0});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
        /*section_id=*/1, /*keys=*/{1});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
        /*section_id=*/0, /*keys=*/{-1});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        /*section_id=*/2, /*keys=*/{2});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        /*section_id=*/1, /*keys=*/{1});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        /*section_id=*/0, /*keys=*/{4});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        /*section_id=*/5, /*keys=*/{3});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        /*section_id=*/4, /*keys=*/{2});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        /*section_id=*/3, /*keys=*/{5});

  EXPECT_THAT(
      this->Query(integer_index.get(), kDefaultTestPropertyPath,
                  /*key_lower=*/1,
                  /*key_upper=*/3),
      IsOkAndHolds(ElementsAre(
          EqualsDocHitInfo(/*document_id=*/2, std::vector<SectionId>{4, 5}),
          EqualsDocHitInfo(/*document_id=*/1, std::vector<SectionId>{1, 2}),
          EqualsDocHitInfo(/*document_id=*/0, std::vector<SectionId>{1}))));
}

TYPED_TEST(NumericIndexIntegerTest, NonRelevantPropertyShouldNotBeIncluded) {
  constexpr std::string_view kNonRelevantProperty = "non_relevant_property";

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
        kDefaultSectionId, /*keys=*/{1});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        kDefaultSectionId, /*keys=*/{3});
  Index(integer_index.get(), kNonRelevantProperty, /*document_id=*/2,
        kDefaultSectionId, /*keys=*/{2});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/3,
        kDefaultSectionId, /*keys=*/{0});
  Index(integer_index.get(), kNonRelevantProperty, /*document_id=*/4,
        kDefaultSectionId, /*keys=*/{4});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/5,
        kDefaultSectionId, /*keys=*/{2});

  std::vector<SectionId> expected_sections = {kDefaultSectionId};
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/1, /*key_upper=*/3),
              IsOkAndHolds(ElementsAre(
                  EqualsDocHitInfo(/*document_id=*/5, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/1, expected_sections),
                  EqualsDocHitInfo(/*document_id=*/0, expected_sections))));
}

TYPED_TEST(NumericIndexIntegerTest,
           RangeQueryKeyLowerGreaterThanKeyUpperShouldReturnError) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
        kDefaultSectionId, /*keys=*/{1});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        kDefaultSectionId, /*keys=*/{3});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        kDefaultSectionId, /*keys=*/{2});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/3,
        kDefaultSectionId, /*keys=*/{0});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/4,
        kDefaultSectionId, /*keys=*/{4});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/5,
        kDefaultSectionId, /*keys=*/{2});

  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/3, /*key_upper=*/1),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TYPED_TEST(NumericIndexIntegerTest, Optimize) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        kDefaultSectionId, /*keys=*/{1});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        kDefaultSectionId, /*keys=*/{3});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/3,
        kDefaultSectionId, /*keys=*/{2});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/5,
        kDefaultSectionId, /*keys=*/{0});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/8,
        kDefaultSectionId, /*keys=*/{4});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/13,
        kDefaultSectionId, /*keys=*/{2});

  // Delete doc id = 3, 5, compress and keep the rest.
  std::vector<DocumentId> document_id_old_to_new(14, kInvalidDocumentId);
  document_id_old_to_new[1] = 0;
  document_id_old_to_new[2] = 1;
  document_id_old_to_new[8] = 2;
  document_id_old_to_new[13] = 3;

  DocumentId new_last_added_document_id = 3;
  EXPECT_THAT(integer_index->Optimize(document_id_old_to_new,
                                      new_last_added_document_id),
              IsOk());
  EXPECT_THAT(integer_index->last_added_document_id(),
              Eq(new_last_added_document_id));

  // Verify index and query API still work normally after Optimize().
  std::vector<SectionId> expected_sections = {kDefaultSectionId};
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/1, /*key_upper=*/1),
              IsOkAndHolds(ElementsAre(
                  EqualsDocHitInfo(/*document_id=*/0, expected_sections))));
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/3, /*key_upper=*/3),
              IsOkAndHolds(ElementsAre(
                  EqualsDocHitInfo(/*document_id=*/1, expected_sections))));
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/0, /*key_upper=*/0),
              IsOkAndHolds(IsEmpty()));
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/4, /*key_upper=*/4),
              IsOkAndHolds(ElementsAre(
                  EqualsDocHitInfo(/*document_id=*/2, expected_sections))));
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/2, /*key_upper=*/2),
              IsOkAndHolds(ElementsAre(
                  EqualsDocHitInfo(/*document_id=*/3, expected_sections))));

  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/5,
        kDefaultSectionId, /*keys=*/{123});
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/123, /*key_upper=*/123),
              IsOkAndHolds(ElementsAre(
                  EqualsDocHitInfo(/*document_id=*/5, expected_sections))));
}

TYPED_TEST(NumericIndexIntegerTest, OptimizeMultiplePropertyPaths) {
  constexpr std::string_view kPropertyPath1 = "prop1";
  constexpr SectionId kSectionId1 = 0;
  constexpr std::string_view kPropertyPath2 = "prop2";
  constexpr SectionId kSectionId2 = 1;

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  // Doc id = 1: insert 2 data for "prop1", "prop2"
  Index(integer_index.get(), kPropertyPath2, /*document_id=*/1, kSectionId2,
        /*keys=*/{1});
  Index(integer_index.get(), kPropertyPath1, /*document_id=*/1, kSectionId1,
        /*keys=*/{2});

  // Doc id = 2: insert 1 data for "prop1".
  Index(integer_index.get(), kPropertyPath1, /*document_id=*/2, kSectionId1,
        /*keys=*/{3});

  // Doc id = 3: insert 2 data for "prop2"
  Index(integer_index.get(), kPropertyPath2, /*document_id=*/3, kSectionId2,
        /*keys=*/{4});

  // Doc id = 5: insert 3 data for "prop1", "prop2"
  Index(integer_index.get(), kPropertyPath2, /*document_id=*/5, kSectionId2,
        /*keys=*/{1});
  Index(integer_index.get(), kPropertyPath1, /*document_id=*/5, kSectionId1,
        /*keys=*/{2});

  // Doc id = 8: insert 1 data for "prop2".
  Index(integer_index.get(), kPropertyPath2, /*document_id=*/8, kSectionId2,
        /*keys=*/{3});

  // Doc id = 13: insert 1 data for "prop1".
  Index(integer_index.get(), kPropertyPath1, /*document_id=*/13, kSectionId1,
        /*keys=*/{4});

  // Delete doc id = 3, 5, compress and keep the rest.
  std::vector<DocumentId> document_id_old_to_new(14, kInvalidDocumentId);
  document_id_old_to_new[1] = 0;
  document_id_old_to_new[2] = 1;
  document_id_old_to_new[8] = 2;
  document_id_old_to_new[13] = 3;

  DocumentId new_last_added_document_id = 3;
  EXPECT_THAT(integer_index->Optimize(document_id_old_to_new,
                                      new_last_added_document_id),
              IsOk());
  EXPECT_THAT(integer_index->last_added_document_id(),
              Eq(new_last_added_document_id));

  // Verify index and query API still work normally after Optimize().
  // Key = 1
  EXPECT_THAT(this->Query(integer_index.get(), kPropertyPath1, /*key_lower=*/1,
                          /*key_upper=*/1),
              IsOkAndHolds(IsEmpty()));
  EXPECT_THAT(this->Query(integer_index.get(), kPropertyPath2, /*key_lower=*/1,
                          /*key_upper=*/1),
              IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
                  /*document_id=*/0, std::vector<SectionId>{kSectionId2}))));

  // key = 2
  EXPECT_THAT(this->Query(integer_index.get(), kPropertyPath1, /*key_lower=*/2,
                          /*key_upper=*/2),
              IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
                  /*document_id=*/0, std::vector<SectionId>{kSectionId1}))));
  EXPECT_THAT(this->Query(integer_index.get(), kPropertyPath2, /*key_lower=*/2,
                          /*key_upper=*/2),
              IsOkAndHolds(IsEmpty()));

  // key = 3
  EXPECT_THAT(this->Query(integer_index.get(), kPropertyPath1, /*key_lower=*/3,
                          /*key_upper=*/3),
              IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
                  /*document_id=*/1, std::vector<SectionId>{kSectionId1}))));
  EXPECT_THAT(this->Query(integer_index.get(), kPropertyPath2, /*key_lower=*/3,
                          /*key_upper=*/3),
              IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
                  /*document_id=*/2, std::vector<SectionId>{kSectionId2}))));

  // key = 4
  EXPECT_THAT(this->Query(integer_index.get(), kPropertyPath1, /*key_lower=*/4,
                          /*key_upper=*/4),
              IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
                  /*document_id=*/3, std::vector<SectionId>{kSectionId1}))));
  EXPECT_THAT(this->Query(integer_index.get(), kPropertyPath2, /*key_lower=*/4,
                          /*key_upper=*/4),
              IsOkAndHolds(IsEmpty()));
}

TYPED_TEST(NumericIndexIntegerTest, OptimizeShouldDiscardEmptyPropertyStorage) {
  constexpr std::string_view kPropertyPath1 = "prop1";
  constexpr SectionId kSectionId1 = 0;
  constexpr std::string_view kPropertyPath2 = "prop2";
  constexpr SectionId kSectionId2 = 1;

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  // Doc id = 1: insert 2 data for "prop1", "prop2"
  Index(integer_index.get(), kPropertyPath2, /*document_id=*/1, kSectionId2,
        /*keys=*/{1});
  Index(integer_index.get(), kPropertyPath1, /*document_id=*/1, kSectionId1,
        /*keys=*/{2});

  // Doc id = 2: insert 1 data for "prop1".
  Index(integer_index.get(), kPropertyPath1, /*document_id=*/2, kSectionId1,
        /*keys=*/{3});

  // Doc id = 3: insert 2 data for "prop2"
  Index(integer_index.get(), kPropertyPath2, /*document_id=*/3, kSectionId2,
        /*keys=*/{4});

  // Delete doc id = 1, 3, compress and keep the rest.
  std::vector<DocumentId> document_id_old_to_new(4, kInvalidDocumentId);
  document_id_old_to_new[2] = 0;

  DocumentId new_last_added_document_id = 0;
  EXPECT_THAT(integer_index->Optimize(document_id_old_to_new,
                                      new_last_added_document_id),
              IsOk());
  EXPECT_THAT(integer_index->last_added_document_id(),
              Eq(new_last_added_document_id));

  // All data in "prop2" as well as the underlying storage should be deleted, so
  // when querying "prop2", we should get empty result.
  EXPECT_THAT(this->Query(integer_index.get(), kPropertyPath2,
                          /*key_lower=*/std::numeric_limits<int64_t>::min(),
                          /*key_upper=*/std::numeric_limits<int64_t>::max()),
              IsOkAndHolds(IsEmpty()));
  if (std::is_same_v<IntegerIndex, TypeParam>) {
    std::string prop2_storage_working_path =
        absl_ports::StrCat(this->working_path_, "/", kPropertyPath2);
    EXPECT_THAT(
        this->filesystem_.DirectoryExists(prop2_storage_working_path.c_str()),
        IsFalse());
  }

  // Verify we can still index and query for "prop2".
  Index(integer_index.get(), kPropertyPath2, /*document_id=*/100, kSectionId2,
        /*keys=*/{123});
  EXPECT_THAT(this->Query(integer_index.get(), kPropertyPath2,
                          /*key_lower=*/123, /*key_upper=*/123),
              IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
                  /*document_id=*/100, std::vector<SectionId>{kSectionId2}))));
}

TYPED_TEST(NumericIndexIntegerTest, OptimizeOutOfRangeDocumentId) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        kDefaultSectionId, /*keys=*/{1});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        kDefaultSectionId, /*keys=*/{3});

  // Create document_id_old_to_new with size = 2. Optimize should handle out of
  // range DocumentId properly.
  std::vector<DocumentId> document_id_old_to_new(2, kInvalidDocumentId);

  EXPECT_THAT(integer_index->Optimize(
                  document_id_old_to_new,
                  /*new_last_added_document_id=*/kInvalidDocumentId),
              IsOk());
  EXPECT_THAT(integer_index->last_added_document_id(), Eq(kInvalidDocumentId));

  // Verify all data are discarded after Optimize().
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/std::numeric_limits<int64_t>::min(),
                          /*key_upper=*/std::numeric_limits<int64_t>::max()),
              IsOkAndHolds(IsEmpty()));
}

TYPED_TEST(NumericIndexIntegerTest, OptimizeDeleteAll) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        kDefaultSectionId, /*keys=*/{1});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        kDefaultSectionId, /*keys=*/{3});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/3,
        kDefaultSectionId, /*keys=*/{2});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/5,
        kDefaultSectionId, /*keys=*/{0});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/8,
        kDefaultSectionId, /*keys=*/{4});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/13,
        kDefaultSectionId, /*keys=*/{2});

  // Delete all documents.
  std::vector<DocumentId> document_id_old_to_new(14, kInvalidDocumentId);

  EXPECT_THAT(integer_index->Optimize(
                  document_id_old_to_new,
                  /*new_last_added_document_id=*/kInvalidDocumentId),
              IsOk());
  EXPECT_THAT(integer_index->last_added_document_id(), Eq(kInvalidDocumentId));

  // Verify all data are discarded after Optimize().
  EXPECT_THAT(this->Query(integer_index.get(), kDefaultTestPropertyPath,
                          /*key_lower=*/std::numeric_limits<int64_t>::min(),
                          /*key_upper=*/std::numeric_limits<int64_t>::max()),
              IsOkAndHolds(IsEmpty()));
}

TYPED_TEST(NumericIndexIntegerTest, Clear) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<NumericIndex<int64_t>> integer_index,
      this->template CreateIntegerIndex<TypeParam>());

  Index(integer_index.get(), /*property_path=*/"A", /*document_id=*/0,
        kDefaultSectionId, /*keys=*/{1});
  Index(integer_index.get(), /*property_path=*/"B", /*document_id=*/1,
        kDefaultSectionId, /*keys=*/{3});
  integer_index->set_last_added_document_id(1);

  ASSERT_THAT(integer_index->last_added_document_id(), Eq(1));
  ASSERT_THAT(
      this->Query(integer_index.get(), /*property_path=*/"A", /*key_lower=*/1,
                  /*key_upper=*/1),
      IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
          /*document_id=*/0, std::vector<SectionId>{kDefaultSectionId}))));
  ASSERT_THAT(
      this->Query(integer_index.get(), /*property_path=*/"B", /*key_lower=*/3,
                  /*key_upper=*/3),
      IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
          /*document_id=*/1, std::vector<SectionId>{kDefaultSectionId}))));

  // After resetting, last_added_document_id should be set to
  // kInvalidDocumentId, and the previous added keys should be deleted.
  ICING_ASSERT_OK(integer_index->Clear());
  EXPECT_THAT(integer_index->last_added_document_id(), Eq(kInvalidDocumentId));
  EXPECT_THAT(
      this->Query(integer_index.get(), /*property_path=*/"A", /*key_lower=*/1,
                  /*key_upper=*/1),
      IsOkAndHolds(IsEmpty()));
  EXPECT_THAT(
      this->Query(integer_index.get(), /*property_path=*/"B", /*key_lower=*/3,
                  /*key_upper=*/3),
      IsOkAndHolds(IsEmpty()));

  // Integer index should be able to work normally after Clear().
  Index(integer_index.get(), /*property_path=*/"A", /*document_id=*/3,
        kDefaultSectionId, /*keys=*/{123});
  Index(integer_index.get(), /*property_path=*/"B", /*document_id=*/4,
        kDefaultSectionId, /*keys=*/{456});
  integer_index->set_last_added_document_id(4);

  EXPECT_THAT(integer_index->last_added_document_id(), Eq(4));
  EXPECT_THAT(
      this->Query(integer_index.get(), /*property_path=*/"A", /*key_lower=*/123,
                  /*key_upper=*/123),
      IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
          /*document_id=*/3, std::vector<SectionId>{kDefaultSectionId}))));
  EXPECT_THAT(
      this->Query(integer_index.get(), /*property_path=*/"B", /*key_lower=*/456,
                  /*key_upper=*/456),
      IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
          /*document_id=*/4, std::vector<SectionId>{kDefaultSectionId}))));
}

struct IntegerIndexTestParam {
  int32_t num_data_threshold_for_bucket_split;
  bool pre_mapping_fbv;

  explicit IntegerIndexTestParam(int32_t num_data_threshold_for_bucket_split_in,
                                 bool pre_mapping_fbv_in)
      : num_data_threshold_for_bucket_split(
            num_data_threshold_for_bucket_split_in),
        pre_mapping_fbv(pre_mapping_fbv_in) {}
};

// Tests for persistent integer index only
class IntegerIndexTest
    : public NumericIndexIntegerTest<IntegerIndex>,
      public ::testing::WithParamInterface<IntegerIndexTestParam> {};

TEST_P(IntegerIndexTest, InvalidWorkingPath) {
  EXPECT_THAT(
      IntegerIndex::Create(filesystem_, "/dev/null/integer_index_test",
                           GetParam().num_data_threshold_for_bucket_split,
                           GetParam().pre_mapping_fbv),
      StatusIs(libtextclassifier3::StatusCode::INTERNAL));
}

TEST_P(IntegerIndexTest, InitializeNewFiles) {
  {
    ASSERT_FALSE(filesystem_.DirectoryExists(working_path_.c_str()));
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<IntegerIndex> integer_index,
        IntegerIndex::Create(filesystem_, working_path_,
                             GetParam().num_data_threshold_for_bucket_split,
                             GetParam().pre_mapping_fbv));

    ICING_ASSERT_OK(integer_index->PersistToDisk());
  }

  // Metadata file should be initialized correctly for both info and crcs
  // sections.
  const std::string metadata_file_path =
      absl_ports::StrCat(working_path_, "/", IntegerIndex::kFilePrefix, ".m");
  ScopedFd metadata_sfd(filesystem_.OpenForWrite(metadata_file_path.c_str()));
  ASSERT_TRUE(metadata_sfd.is_valid());

  // Check info section
  Info info;
  ASSERT_TRUE(filesystem_.PRead(metadata_sfd.get(), &info, sizeof(Info),
                                IntegerIndex::kInfoMetadataFileOffset));
  EXPECT_THAT(info.magic, Eq(Info::kMagic));
  EXPECT_THAT(info.last_added_document_id, Eq(kInvalidDocumentId));
  EXPECT_THAT(info.num_data_threshold_for_bucket_split,
              Eq(GetParam().num_data_threshold_for_bucket_split));

  // Check crcs section
  Crcs crcs;
  ASSERT_TRUE(filesystem_.PRead(metadata_sfd.get(), &crcs, sizeof(Crcs),
                                IntegerIndex::kCrcsMetadataFileOffset));
  // There are no storages initially, so storages_crc should be 0.
  EXPECT_THAT(crcs.component_crcs.storages_crc, Eq(0));
  EXPECT_THAT(crcs.component_crcs.info_crc,
              Eq(Crc32(std::string_view(reinterpret_cast<const char*>(&info),
                                        sizeof(Info)))
                     .Get()));
  EXPECT_THAT(crcs.all_crc,
              Eq(Crc32(std::string_view(
                           reinterpret_cast<const char*>(&crcs.component_crcs),
                           sizeof(Crcs::ComponentCrcs)))
                     .Get()));
}

TEST_P(IntegerIndexTest,
       InitializationShouldFailWithoutPersistToDiskOrDestruction) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<IntegerIndex> integer_index,
      IntegerIndex::Create(filesystem_, working_path_,
                           GetParam().num_data_threshold_for_bucket_split,
                           GetParam().pre_mapping_fbv));

  // Insert some data.
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
        /*section_id=*/20, /*keys=*/{0, 100, -100});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        /*section_id=*/2, /*keys=*/{3, -1000, 500});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        /*section_id=*/15, /*keys=*/{-6, 321, 98});

  // Without calling PersistToDisk, checksums will not be recomputed or synced
  // to disk, so initializing another instance on the same files should fail.
  EXPECT_THAT(
      IntegerIndex::Create(filesystem_, working_path_,
                           GetParam().num_data_threshold_for_bucket_split,
                           GetParam().pre_mapping_fbv),
      StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
}

TEST_P(IntegerIndexTest, InitializationShouldSucceedWithPersistToDisk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<IntegerIndex> integer_index1,
      IntegerIndex::Create(filesystem_, working_path_,
                           GetParam().num_data_threshold_for_bucket_split,
                           GetParam().pre_mapping_fbv));

  // Insert some data.
  Index(integer_index1.get(), kDefaultTestPropertyPath, /*document_id=*/0,
        /*section_id=*/20, /*keys=*/{0, 100, -100});
  Index(integer_index1.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        /*section_id=*/2, /*keys=*/{3, -1000, 500});
  Index(integer_index1.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        /*section_id=*/15, /*keys=*/{-6, 321, 98});
  integer_index1->set_last_added_document_id(2);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::vector<DocHitInfo> doc_hit_info_vec,
      Query(integer_index1.get(), kDefaultTestPropertyPath,
            /*key_lower=*/std::numeric_limits<int64_t>::min(),
            /*key_upper=*/std::numeric_limits<int64_t>::max()));

  // After calling PersistToDisk, all checksums should be recomputed and synced
  // correctly to disk, so initializing another instance on the same files
  // should succeed, and we should be able to get the same contents.
  ICING_EXPECT_OK(integer_index1->PersistToDisk());

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<IntegerIndex> integer_index2,
      IntegerIndex::Create(filesystem_, working_path_,
                           GetParam().num_data_threshold_for_bucket_split,
                           GetParam().pre_mapping_fbv));
  EXPECT_THAT(integer_index2->last_added_document_id(), Eq(2));
  EXPECT_THAT(Query(integer_index2.get(), kDefaultTestPropertyPath,
                    /*key_lower=*/std::numeric_limits<int64_t>::min(),
                    /*key_upper=*/std::numeric_limits<int64_t>::max()),
              IsOkAndHolds(ElementsAreArray(doc_hit_info_vec.begin(),
                                            doc_hit_info_vec.end())));
}

TEST_P(IntegerIndexTest, InitializationShouldSucceedAfterDestruction) {
  std::vector<DocHitInfo> doc_hit_info_vec;
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<IntegerIndex> integer_index,
        IntegerIndex::Create(filesystem_, working_path_,
                             GetParam().num_data_threshold_for_bucket_split,
                             GetParam().pre_mapping_fbv));

    // Insert some data.
    Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
          /*section_id=*/20, /*keys=*/{0, 100, -100});
    Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
          /*section_id=*/2, /*keys=*/{3, -1000, 500});
    Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
          /*section_id=*/15, /*keys=*/{-6, 321, 98});
    integer_index->set_last_added_document_id(2);
    ICING_ASSERT_OK_AND_ASSIGN(
        doc_hit_info_vec,
        Query(integer_index.get(), kDefaultTestPropertyPath,
              /*key_lower=*/std::numeric_limits<int64_t>::min(),
              /*key_upper=*/std::numeric_limits<int64_t>::max()));
  }

  {
    // The previous instance went out of scope and was destructed. Although we
    // didn't call PersistToDisk explicitly, the destructor should invoke it and
    // thus initializing another instance on the same files should succeed, and
    // we should be able to get the same contents.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<IntegerIndex> integer_index,
        IntegerIndex::Create(filesystem_, working_path_,
                             GetParam().num_data_threshold_for_bucket_split,
                             GetParam().pre_mapping_fbv));
    EXPECT_THAT(integer_index->last_added_document_id(), Eq(2));
    EXPECT_THAT(Query(integer_index.get(), kDefaultTestPropertyPath,
                      /*key_lower=*/std::numeric_limits<int64_t>::min(),
                      /*key_upper=*/std::numeric_limits<int64_t>::max()),
                IsOkAndHolds(ElementsAreArray(doc_hit_info_vec.begin(),
                                              doc_hit_info_vec.end())));
  }
}

TEST_P(IntegerIndexTest, InitializeExistingFilesWithWrongAllCrcShouldFail) {
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<IntegerIndex> integer_index,
        IntegerIndex::Create(filesystem_, working_path_,
                             GetParam().num_data_threshold_for_bucket_split,
                             GetParam().pre_mapping_fbv));
    // Insert some data.
    Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
          /*section_id=*/20, /*keys=*/{0, 100, -100});
    Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
          /*section_id=*/2, /*keys=*/{3, -1000, 500});
    Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
          /*section_id=*/15, /*keys=*/{-6, 321, 98});

    ICING_ASSERT_OK(integer_index->PersistToDisk());
  }

  const std::string metadata_file_path =
      absl_ports::StrCat(working_path_, "/", IntegerIndex::kFilePrefix, ".m");
  ScopedFd metadata_sfd(filesystem_.OpenForWrite(metadata_file_path.c_str()));
  ASSERT_TRUE(metadata_sfd.is_valid());

  Crcs crcs;
  ASSERT_TRUE(filesystem_.PRead(metadata_sfd.get(), &crcs, sizeof(Crcs),
                                IntegerIndex::kCrcsMetadataFileOffset));

  // Manually corrupt all_crc
  crcs.all_crc += kCorruptedValueOffset;
  ASSERT_TRUE(filesystem_.PWrite(metadata_sfd.get(),
                                 IntegerIndexStorage::kCrcsMetadataFileOffset,
                                 &crcs, sizeof(Crcs)));
  metadata_sfd.reset();

  {
    // Attempt to create the integer index with metadata containing corrupted
    // all_crc. This should fail.
    libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndex>>
        integer_index_or =
            IntegerIndex::Create(filesystem_, working_path_,
                                 GetParam().num_data_threshold_for_bucket_split,
                                 GetParam().pre_mapping_fbv);
    EXPECT_THAT(integer_index_or,
                StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
    EXPECT_THAT(integer_index_or.status().error_message(),
                HasSubstr("Invalid all crc"));
  }
}

TEST_P(IntegerIndexTest, InitializeExistingFilesWithCorruptedInfoShouldFail) {
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<IntegerIndex> integer_index,
        IntegerIndex::Create(filesystem_, working_path_,
                             GetParam().num_data_threshold_for_bucket_split,
                             GetParam().pre_mapping_fbv));
    // Insert some data.
    Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
          /*section_id=*/20, /*keys=*/{0, 100, -100});
    Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
          /*section_id=*/2, /*keys=*/{3, -1000, 500});
    Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
          /*section_id=*/15, /*keys=*/{-6, 321, 98});

    ICING_ASSERT_OK(integer_index->PersistToDisk());
  }

  const std::string metadata_file_path =
      absl_ports::StrCat(working_path_, "/", IntegerIndex::kFilePrefix, ".m");
  ScopedFd metadata_sfd(filesystem_.OpenForWrite(metadata_file_path.c_str()));
  ASSERT_TRUE(metadata_sfd.is_valid());

  Info info;
  ASSERT_TRUE(filesystem_.PRead(metadata_sfd.get(), &info, sizeof(Info),
                                IntegerIndex::kInfoMetadataFileOffset));

  // Modify info, but don't update the checksum. This would be similar to
  // corruption of info.
  info.last_added_document_id += kCorruptedValueOffset;
  ASSERT_TRUE(filesystem_.PWrite(metadata_sfd.get(),
                                 IntegerIndex::kInfoMetadataFileOffset, &info,
                                 sizeof(Info)));
  metadata_sfd.reset();

  {
    // Attempt to create the integer index with info that doesn't match its
    // checksum and confirm that it fails.
    libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndex>>
        integer_index_or =
            IntegerIndex::Create(filesystem_, working_path_,
                                 GetParam().num_data_threshold_for_bucket_split,
                                 GetParam().pre_mapping_fbv);
    EXPECT_THAT(integer_index_or,
                StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
    EXPECT_THAT(integer_index_or.status().error_message(),
                HasSubstr("Invalid info crc"));
  }
}

TEST_P(IntegerIndexTest,
       InitializeExistingFilesWithCorruptedStoragesShouldFail) {
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<IntegerIndex> integer_index,
        IntegerIndex::Create(filesystem_, working_path_,
                             GetParam().num_data_threshold_for_bucket_split,
                             GetParam().pre_mapping_fbv));
    // Insert some data.
    Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
          /*section_id=*/20, /*keys=*/{0, 100, -100});
    Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
          /*section_id=*/2, /*keys=*/{3, -1000, 500});
    Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
          /*section_id=*/15, /*keys=*/{-6, 321, 98});

    ICING_ASSERT_OK(integer_index->PersistToDisk());
  }

  {
    // Corrupt integer index storage for kDefaultTestPropertyPath manually.
    PostingListIntegerIndexSerializer posting_list_integer_index_serializer;
    std::string storage_working_path =
        absl_ports::StrCat(working_path_, "/", kDefaultTestPropertyPath);
    ASSERT_TRUE(filesystem_.DirectoryExists(storage_working_path.c_str()));

    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<IntegerIndexStorage> storage,
        IntegerIndexStorage::Create(
            filesystem_, std::move(storage_working_path),
            IntegerIndexStorage::Options(
                GetParam().num_data_threshold_for_bucket_split,
                GetParam().pre_mapping_fbv),
            &posting_list_integer_index_serializer));
    ICING_ASSERT_OK(storage->AddKeys(/*document_id=*/3, /*section_id=*/4,
                                     /*new_keys=*/{3, 4, 5}));

    ICING_ASSERT_OK(storage->PersistToDisk());
  }

  {
    // Attempt to create the integer index with corrupted storages. This should
    // fail.
    libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndex>>
        integer_index_or =
            IntegerIndex::Create(filesystem_, working_path_,
                                 GetParam().num_data_threshold_for_bucket_split,
                                 GetParam().pre_mapping_fbv);
    EXPECT_THAT(integer_index_or,
                StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
    EXPECT_THAT(integer_index_or.status().error_message(),
                HasSubstr("Invalid storages crc"));
  }
}

TEST_P(
    IntegerIndexTest,
    InitializeExistingFilesWithMismatchNumDataThresholdForBucketSplitShouldFail) {
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<IntegerIndex> integer_index,
        IntegerIndex::Create(filesystem_, working_path_,
                             GetParam().num_data_threshold_for_bucket_split,
                             GetParam().pre_mapping_fbv));
    // Insert some data.
    Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
          /*section_id=*/20, /*keys=*/{0, 100, -100});
    Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
          /*section_id=*/2, /*keys=*/{3, -1000, 500});
    Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
          /*section_id=*/15, /*keys=*/{-6, 321, 98});

    ICING_ASSERT_OK(integer_index->PersistToDisk());
  }

  {
    // Attempt to create the integer index with different
    // num_data_threshold_for_bucket_split. This should fail.
    libtextclassifier3::StatusOr<std::unique_ptr<IntegerIndex>>
        integer_index_or = IntegerIndex::Create(
            filesystem_, working_path_,
            GetParam().num_data_threshold_for_bucket_split + 1,
            GetParam().pre_mapping_fbv);
    EXPECT_THAT(integer_index_or,
                StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
    EXPECT_THAT(integer_index_or.status().error_message(),
                HasSubstr("Mismatch num_data_threshold_for_bucket_split"));
  }
}

TEST_P(IntegerIndexTest, WildcardStoragePersistenceQuery) {
  // This test sets its schema assuming that max property storages == 32.
  ASSERT_THAT(IntegerIndex::kMaxPropertyStorages, Eq(32));

  PropertyConfigProto int_property_config =
      PropertyConfigBuilder()
          .SetName("otherProperty1")
          .SetCardinality(CARDINALITY_REPEATED)
          .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
          .Build();
  // Create a schema with two types:
  // - TypeA has 34 properties:
  //     'desiredProperty', 'otherProperty'*, 'undesiredProperty'
  // - TypeB has 2 properties: 'anotherProperty', 'desiredProperty'
  // 1. The 32 'otherProperty's will consume all of the individual storages
  // 2. TypeA.desiredProperty and TypeB.anotherProperty will both be assigned
  //    SectionId = 0 for their respective types.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("TypeA")
                       .AddProperty(int_property_config)
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty2"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty3"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty4"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty5"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty6"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty7"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty8"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty9"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty10"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty11"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty12"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty13"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty14"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty15"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty16"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty17"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty18"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty19"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty20"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty21"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty22"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty23"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty24"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty25"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty26"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty27"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty28"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty29"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty30"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty31"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty32"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("desiredProperty"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("undesiredProperty")))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("TypeB")
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("anotherProperty"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("desiredProperty")))
          .Build();
  ICING_ASSERT_OK(this->schema_store_->SetSchema(
      schema,
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Ids are assigned alphabetically, so the property ids are:
  // TypeA.desiredProperty = 0
  // TypeA.otherPropertyN = N
  // TypeA.undesiredProperty = 33
  // TypeB.anotherProperty = 0
  // TypeB.desiredProperty = 1
  SectionId typea_desired_prop_id = 0;
  SectionId typea_undesired_prop_id = 33;
  SectionId typeb_another_prop_id = 0;
  SectionId typeb_desired_prop_id = 1;
  std::string desired_property = "desiredProperty";
  std::string undesired_property = "undesiredProperty";
  std::string another_property = "anotherProperty";

  // Put 11 docs of "TypeA" into the document store.
  DocumentProto doc =
      DocumentBuilder().SetKey("ns1", "uri0").SetSchema("TypeA").Build();
  ICING_ASSERT_OK(this->doc_store_->Put(doc));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri1").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri2").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri3").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri4").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri5").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri6").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri7").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri8").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri9").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri10").Build()));

  // Put 10 docs of "TypeB" into the document store.
  doc = DocumentBuilder(doc).SetUri("uri11").SetSchema("TypeB").Build();
  ICING_ASSERT_OK(this->doc_store_->Put(doc));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri12").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri13").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri14").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri15").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri16").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri17").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri18").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri19").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri20").Build()));

  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<IntegerIndex> integer_index,
        IntegerIndex::Create(filesystem_, working_path_,
                             GetParam().num_data_threshold_for_bucket_split,
                             GetParam().pre_mapping_fbv));

    // Index numeric content for other properties to force our property into the
    // wildcard storage.
    std::string other_property_path = "otherProperty";
    for (int i = 1; i <= IntegerIndex::kMaxPropertyStorages; ++i) {
      Index(integer_index.get(),
            absl_ports::StrCat(other_property_path, std::to_string(i)),
            /*document_id=*/0, /*section_id=*/i, /*keys=*/{i});
    }

    // Index numeric content for TypeA.desiredProperty
    Index(integer_index.get(), desired_property, /*document_id=*/0,
          typea_desired_prop_id, /*keys=*/{1});
    Index(integer_index.get(), desired_property, /*document_id=*/1,
          typea_desired_prop_id, /*keys=*/{3});
    Index(integer_index.get(), desired_property, /*document_id=*/2,
          typea_desired_prop_id, /*keys=*/{2});
    Index(integer_index.get(), desired_property, /*document_id=*/3,
          typea_desired_prop_id, /*keys=*/{0});
    Index(integer_index.get(), desired_property, /*document_id=*/4,
          typea_desired_prop_id, /*keys=*/{4});
    Index(integer_index.get(), desired_property, /*document_id=*/5,
          typea_desired_prop_id, /*keys=*/{2});

    // Index the same numeric content for TypeA.undesiredProperty
    Index(integer_index.get(), undesired_property, /*document_id=*/6,
          typea_undesired_prop_id, /*keys=*/{3});
    Index(integer_index.get(), undesired_property, /*document_id=*/7,
          typea_undesired_prop_id, /*keys=*/{2});
    Index(integer_index.get(), undesired_property, /*document_id=*/8,
          typea_undesired_prop_id, /*keys=*/{0});
    Index(integer_index.get(), undesired_property, /*document_id=*/9,
          typea_undesired_prop_id, /*keys=*/{4});
    Index(integer_index.get(), undesired_property, /*document_id=*/10,
          typea_undesired_prop_id, /*keys=*/{2});

    // Index the same numeric content for TypeB.undesiredProperty
    Index(integer_index.get(), another_property, /*document_id=*/11,
          typeb_another_prop_id, /*keys=*/{3});
    Index(integer_index.get(), another_property, /*document_id=*/12,
          typeb_another_prop_id, /*keys=*/{2});
    Index(integer_index.get(), another_property, /*document_id=*/13,
          typeb_another_prop_id, /*keys=*/{0});
    Index(integer_index.get(), another_property, /*document_id=*/14,
          typeb_another_prop_id, /*keys=*/{4});
    Index(integer_index.get(), another_property, /*document_id=*/15,
          typeb_another_prop_id, /*keys=*/{2});

    // Finally, index the same numeric content for TypeB.desiredProperty
    Index(integer_index.get(), desired_property, /*document_id=*/16,
          typeb_desired_prop_id, /*keys=*/{3});
    Index(integer_index.get(), desired_property, /*document_id=*/17,
          typeb_desired_prop_id, /*keys=*/{2});
    Index(integer_index.get(), desired_property, /*document_id=*/18,
          typeb_desired_prop_id, /*keys=*/{0});
    Index(integer_index.get(), desired_property, /*document_id=*/19,
          typeb_desired_prop_id, /*keys=*/{4});
    Index(integer_index.get(), desired_property, /*document_id=*/20,
          typeb_desired_prop_id, /*keys=*/{2});
  }

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<IntegerIndex> integer_index,
      IntegerIndex::Create(filesystem_, working_path_,
                           GetParam().num_data_threshold_for_bucket_split,
                           GetParam().pre_mapping_fbv));

  EXPECT_THAT(integer_index->num_property_indices(), Eq(33));

  // Only the hits for 'desired_prop_id' should be returned.
  std::vector<SectionId> expected_sections_typea = {typea_desired_prop_id};
  std::vector<SectionId> expected_sections_typeb = {typeb_desired_prop_id};
  EXPECT_THAT(
      Query(integer_index.get(), desired_property,
            /*key_lower=*/2, /*key_upper=*/2),
      IsOkAndHolds(ElementsAre(
          EqualsDocHitInfo(/*document_id=*/20, expected_sections_typeb),
          EqualsDocHitInfo(/*document_id=*/17, expected_sections_typeb),
          EqualsDocHitInfo(/*document_id=*/5, expected_sections_typea),
          EqualsDocHitInfo(/*document_id=*/2, expected_sections_typea))));

  EXPECT_THAT(
      Query(integer_index.get(), desired_property,
            /*key_lower=*/1, /*key_upper=*/3),
      IsOkAndHolds(ElementsAre(
          EqualsDocHitInfo(/*document_id=*/20, expected_sections_typeb),
          EqualsDocHitInfo(/*document_id=*/17, expected_sections_typeb),
          EqualsDocHitInfo(/*document_id=*/16, expected_sections_typeb),
          EqualsDocHitInfo(/*document_id=*/5, expected_sections_typea),
          EqualsDocHitInfo(/*document_id=*/2, expected_sections_typea),
          EqualsDocHitInfo(/*document_id=*/1, expected_sections_typea),
          EqualsDocHitInfo(/*document_id=*/0, expected_sections_typea))));
}

TEST_P(IntegerIndexTest,
       IntegerIndexShouldWorkAfterOptimizeAndReinitialization) {
  constexpr std::string_view kPropertyPath1 = "prop1";
  constexpr SectionId kSectionId1 = 0;
  constexpr std::string_view kPropertyPath2 = "prop2";
  constexpr SectionId kSectionId2 = 1;

  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<IntegerIndex> integer_index,
        IntegerIndex::Create(filesystem_, working_path_,
                             GetParam().num_data_threshold_for_bucket_split,
                             GetParam().pre_mapping_fbv));

    // Doc id = 1: insert 2 data for "prop1", "prop2"
    Index(integer_index.get(), kPropertyPath2, /*document_id=*/1, kSectionId2,
          /*keys=*/{1});
    Index(integer_index.get(), kPropertyPath1, /*document_id=*/1, kSectionId1,
          /*keys=*/{2});

    // Doc id = 2: insert 1 data for "prop1".
    Index(integer_index.get(), kPropertyPath1, /*document_id=*/2, kSectionId1,
          /*keys=*/{3});

    // Doc id = 3: insert 2 data for "prop2"
    Index(integer_index.get(), kPropertyPath2, /*document_id=*/3, kSectionId2,
          /*keys=*/{4});

    // Doc id = 5: insert 3 data for "prop1", "prop2"
    Index(integer_index.get(), kPropertyPath2, /*document_id=*/5, kSectionId2,
          /*keys=*/{1});
    Index(integer_index.get(), kPropertyPath1, /*document_id=*/5, kSectionId1,
          /*keys=*/{2});

    // Doc id = 8: insert 1 data for "prop2".
    Index(integer_index.get(), kPropertyPath2, /*document_id=*/8, kSectionId2,
          /*keys=*/{3});

    // Doc id = 13: insert 1 data for "prop1".
    Index(integer_index.get(), kPropertyPath1, /*document_id=*/13, kSectionId1,
          /*keys=*/{4});

    // Delete doc id = 3, 5, compress and keep the rest.
    std::vector<DocumentId> document_id_old_to_new(14, kInvalidDocumentId);
    document_id_old_to_new[1] = 0;
    document_id_old_to_new[2] = 1;
    document_id_old_to_new[8] = 2;
    document_id_old_to_new[13] = 3;

    DocumentId new_last_added_document_id = 3;
    EXPECT_THAT(integer_index->Optimize(document_id_old_to_new,
                                        new_last_added_document_id),
                IsOk());
    EXPECT_THAT(integer_index->last_added_document_id(),
                Eq(new_last_added_document_id));
  }

  {
    // Reinitialize IntegerIndex and verify index and query API still work
    // normally.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<IntegerIndex> integer_index,
        IntegerIndex::Create(filesystem_, working_path_,
                             GetParam().num_data_threshold_for_bucket_split,
                             GetParam().pre_mapping_fbv));

    // Key = 1
    EXPECT_THAT(Query(integer_index.get(), kPropertyPath1, /*key_lower=*/1,
                      /*key_upper=*/1),
                IsOkAndHolds(IsEmpty()));
    EXPECT_THAT(Query(integer_index.get(), kPropertyPath2, /*key_lower=*/1,
                      /*key_upper=*/1),
                IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
                    /*document_id=*/0, std::vector<SectionId>{kSectionId2}))));

    // key = 2
    EXPECT_THAT(Query(integer_index.get(), kPropertyPath1, /*key_lower=*/2,
                      /*key_upper=*/2),
                IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
                    /*document_id=*/0, std::vector<SectionId>{kSectionId1}))));
    EXPECT_THAT(Query(integer_index.get(), kPropertyPath2, /*key_lower=*/2,
                      /*key_upper=*/2),
                IsOkAndHolds(IsEmpty()));

    // key = 3
    EXPECT_THAT(Query(integer_index.get(), kPropertyPath1, /*key_lower=*/3,
                      /*key_upper=*/3),
                IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
                    /*document_id=*/1, std::vector<SectionId>{kSectionId1}))));
    EXPECT_THAT(Query(integer_index.get(), kPropertyPath2, /*key_lower=*/3,
                      /*key_upper=*/3),
                IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
                    /*document_id=*/2, std::vector<SectionId>{kSectionId2}))));

    // key = 4
    EXPECT_THAT(Query(integer_index.get(), kPropertyPath1, /*key_lower=*/4,
                      /*key_upper=*/4),
                IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
                    /*document_id=*/3, std::vector<SectionId>{kSectionId1}))));
    EXPECT_THAT(Query(integer_index.get(), kPropertyPath2, /*key_lower=*/4,
                      /*key_upper=*/4),
                IsOkAndHolds(IsEmpty()));

    // Index new data.
    Index(integer_index.get(), kPropertyPath2, /*document_id=*/100, kSectionId2,
          /*keys=*/{123});
    Index(integer_index.get(), kPropertyPath1, /*document_id=*/100, kSectionId1,
          /*keys=*/{456});
    EXPECT_THAT(
        Query(integer_index.get(), kPropertyPath2, /*key_lower=*/123,
              /*key_upper=*/456),
        IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
            /*document_id=*/100, std::vector<SectionId>{kSectionId2}))));
    EXPECT_THAT(
        Query(integer_index.get(), kPropertyPath1, /*key_lower=*/123,
              /*key_upper=*/456),
        IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
            /*document_id=*/100, std::vector<SectionId>{kSectionId1}))));
  }
}

TEST_P(IntegerIndexTest, WildcardStorageWorksAfterOptimize) {
  // This test sets its schema assuming that max property storages == 32.
  ASSERT_THAT(IntegerIndex::kMaxPropertyStorages, Eq(32));

  PropertyConfigProto int_property_config =
      PropertyConfigBuilder()
          .SetName("otherProperty1")
          .SetCardinality(CARDINALITY_REPEATED)
          .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
          .Build();
  // Create a schema with two types:
  // - TypeA has 34 properties:
  //     'desiredProperty', 'otherProperty'*, 'undesiredProperty'
  // - TypeB has 2 properties: 'anotherProperty', 'desiredProperty'
  // 1. The 32 'otherProperty's will consume all of the individual storages
  // 2. TypeA.desiredProperty and TypeB.anotherProperty will both be assigned
  //    SectionId = 0 for their respective types.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("TypeA")
                       .AddProperty(int_property_config)
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty2"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty3"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty4"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty5"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty6"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty7"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty8"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty9"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty10"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty11"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty12"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty13"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty14"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty15"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty16"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty17"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty18"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty19"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty20"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty21"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty22"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty23"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty24"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty25"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty26"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty27"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty28"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty29"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty30"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty31"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty32"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("desiredProperty"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("undesiredProperty")))
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("TypeB")
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("anotherProperty"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("desiredProperty")))
          .Build();
  ICING_ASSERT_OK(this->schema_store_->SetSchema(
      schema,
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Ids are assigned alphabetically, so the property ids are:
  // TypeA.desiredProperty = 0
  // TypeA.otherPropertyN = N
  // TypeA.undesiredProperty = 33
  // TypeB.anotherProperty = 0
  // TypeB.desiredProperty = 1
  SectionId typea_desired_prop_id = 0;
  SectionId typea_undesired_prop_id = 33;
  SectionId typeb_another_prop_id = 0;
  SectionId typeb_desired_prop_id = 1;
  std::string desired_property = "desiredProperty";
  std::string undesired_property = "undesiredProperty";
  std::string another_property = "anotherProperty";

  // Only the hits for 'desired_prop_id' should be returned.
  std::vector<SectionId> expected_sections_typea = {typea_desired_prop_id};
  std::vector<SectionId> expected_sections_typeb = {typeb_desired_prop_id};

  // Put 11 docs of "TypeA" into the document store.
  DocumentProto doc =
      DocumentBuilder().SetKey("ns1", "uri0").SetSchema("TypeA").Build();
  ICING_ASSERT_OK(this->doc_store_->Put(doc));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri1").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri2").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri3").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri4").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri5").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri6").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri7").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri8").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri9").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri10").Build()));

  // Put 10 docs of "TypeB" into the document store.
  doc = DocumentBuilder(doc).SetUri("uri11").SetSchema("TypeB").Build();
  ICING_ASSERT_OK(this->doc_store_->Put(doc));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri12").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri13").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri14").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri15").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri16").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri17").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri18").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri19").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri20").Build()));

  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<IntegerIndex> integer_index,
        IntegerIndex::Create(filesystem_, working_path_,
                             GetParam().num_data_threshold_for_bucket_split,
                             GetParam().pre_mapping_fbv));

    // Index numeric content for other properties to force our property into the
    // wildcard storage.
    std::string other_property_path = "otherProperty";
    for (int i = 1; i <= IntegerIndex::kMaxPropertyStorages; ++i) {
      Index(integer_index.get(),
            absl_ports::StrCat(other_property_path, std::to_string(i)),
            /*document_id=*/0, /*section_id=*/i, /*keys=*/{i});
    }

    // Index numeric content for TypeA.desiredProperty
    Index(integer_index.get(), desired_property, /*document_id=*/0,
          typea_desired_prop_id, /*keys=*/{1});
    Index(integer_index.get(), desired_property, /*document_id=*/1,
          typea_desired_prop_id, /*keys=*/{3});
    Index(integer_index.get(), desired_property, /*document_id=*/2,
          typea_desired_prop_id, /*keys=*/{2});
    Index(integer_index.get(), desired_property, /*document_id=*/3,
          typea_desired_prop_id, /*keys=*/{0});
    Index(integer_index.get(), desired_property, /*document_id=*/4,
          typea_desired_prop_id, /*keys=*/{4});
    Index(integer_index.get(), desired_property, /*document_id=*/5,
          typea_desired_prop_id, /*keys=*/{2});

    // Index the same numeric content for TypeA.undesiredProperty
    Index(integer_index.get(), undesired_property, /*document_id=*/6,
          typea_undesired_prop_id, /*keys=*/{3});
    Index(integer_index.get(), undesired_property, /*document_id=*/7,
          typea_undesired_prop_id, /*keys=*/{2});
    Index(integer_index.get(), undesired_property, /*document_id=*/8,
          typea_undesired_prop_id, /*keys=*/{0});
    Index(integer_index.get(), undesired_property, /*document_id=*/9,
          typea_undesired_prop_id, /*keys=*/{4});
    Index(integer_index.get(), undesired_property, /*document_id=*/10,
          typea_undesired_prop_id, /*keys=*/{2});

    // Index the same numeric content for TypeB.undesiredProperty
    Index(integer_index.get(), another_property, /*document_id=*/11,
          typeb_another_prop_id, /*keys=*/{3});
    Index(integer_index.get(), another_property, /*document_id=*/12,
          typeb_another_prop_id, /*keys=*/{2});
    Index(integer_index.get(), another_property, /*document_id=*/13,
          typeb_another_prop_id, /*keys=*/{0});
    Index(integer_index.get(), another_property, /*document_id=*/14,
          typeb_another_prop_id, /*keys=*/{4});
    Index(integer_index.get(), another_property, /*document_id=*/15,
          typeb_another_prop_id, /*keys=*/{2});

    // Finally, index the same numeric content for TypeB.desiredProperty
    Index(integer_index.get(), desired_property, /*document_id=*/16,
          typeb_desired_prop_id, /*keys=*/{3});
    Index(integer_index.get(), desired_property, /*document_id=*/17,
          typeb_desired_prop_id, /*keys=*/{2});
    Index(integer_index.get(), desired_property, /*document_id=*/18,
          typeb_desired_prop_id, /*keys=*/{0});
    Index(integer_index.get(), desired_property, /*document_id=*/19,
          typeb_desired_prop_id, /*keys=*/{4});
    Index(integer_index.get(), desired_property, /*document_id=*/20,
          typeb_desired_prop_id, /*keys=*/{2});

    ICING_ASSERT_OK(doc_store_->Delete(/*document_id=*/3,
                                       clock_.GetSystemTimeMilliseconds()));
    ICING_ASSERT_OK(doc_store_->Delete(/*document_id=*/5,
                                       clock_.GetSystemTimeMilliseconds()));
    // Delete doc id = 3, 5, compress and keep the rest.
    ICING_ASSERT_OK_AND_ASSIGN(std::vector<DocumentId> document_id_old_to_new,
                               CompactDocStore());

    DocumentId new_last_added_document_id = 18;
    EXPECT_THAT(integer_index->Optimize(document_id_old_to_new,
                                        new_last_added_document_id),
                IsOk());
    EXPECT_THAT(integer_index->last_added_document_id(),
                Eq(new_last_added_document_id));

    EXPECT_THAT(
        Query(integer_index.get(), desired_property,
              /*key_lower=*/2, /*key_upper=*/2),
        IsOkAndHolds(ElementsAre(
            EqualsDocHitInfo(/*document_id=*/20 - 2, expected_sections_typeb),
            EqualsDocHitInfo(/*document_id=*/17 - 2, expected_sections_typeb),
            EqualsDocHitInfo(/*document_id=*/2, expected_sections_typea))));

    EXPECT_THAT(
        Query(integer_index.get(), desired_property,
              /*key_lower=*/1, /*key_upper=*/3),
        IsOkAndHolds(ElementsAre(
            EqualsDocHitInfo(/*document_id=*/20 - 2, expected_sections_typeb),
            EqualsDocHitInfo(/*document_id=*/17 - 2, expected_sections_typeb),
            EqualsDocHitInfo(/*document_id=*/16 - 2, expected_sections_typeb),
            EqualsDocHitInfo(/*document_id=*/2, expected_sections_typea),
            EqualsDocHitInfo(/*document_id=*/1, expected_sections_typea),
            EqualsDocHitInfo(/*document_id=*/0, expected_sections_typea))));
  }

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<IntegerIndex> integer_index,
      IntegerIndex::Create(filesystem_, working_path_,
                           GetParam().num_data_threshold_for_bucket_split,
                           GetParam().pre_mapping_fbv));

  EXPECT_THAT(integer_index->num_property_indices(), Eq(33));

  EXPECT_THAT(
      Query(integer_index.get(), desired_property,
            /*key_lower=*/2, /*key_upper=*/2),
      IsOkAndHolds(ElementsAre(
          EqualsDocHitInfo(/*document_id=*/20 - 2, expected_sections_typeb),
          EqualsDocHitInfo(/*document_id=*/17 - 2, expected_sections_typeb),
          EqualsDocHitInfo(/*document_id=*/2, expected_sections_typea))));

  EXPECT_THAT(
      Query(integer_index.get(), desired_property,
            /*key_lower=*/1, /*key_upper=*/3),
      IsOkAndHolds(ElementsAre(
          EqualsDocHitInfo(/*document_id=*/20 - 2, expected_sections_typeb),
          EqualsDocHitInfo(/*document_id=*/17 - 2, expected_sections_typeb),
          EqualsDocHitInfo(/*document_id=*/16 - 2, expected_sections_typeb),
          EqualsDocHitInfo(/*document_id=*/2, expected_sections_typea),
          EqualsDocHitInfo(/*document_id=*/1, expected_sections_typea),
          EqualsDocHitInfo(/*document_id=*/0, expected_sections_typea))));
}

// This test covers the situation where Optimize causes us to throw out some of
// the individual index storages (because they don't have any hits anymore).
// In this case, any properties that added content to the wildcard storage (even
// if all of their content was also deleted) should still be placed in the
// wildcard storage.
TEST_P(IntegerIndexTest, WildcardStorageAvailableIndicesAfterOptimize) {
  // This test sets its schema assuming that max property storages == 32.
  ASSERT_THAT(IntegerIndex::kMaxPropertyStorages, Eq(32));

  PropertyConfigProto int_property_config =
      PropertyConfigBuilder()
          .SetName("otherProperty1")
          .SetCardinality(CARDINALITY_REPEATED)
          .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
          .Build();
  // Create a schema with two types:
  // - TypeA has 34 properties:
  //     'desiredProperty', 'otherProperty'*, 'undesiredProperty'
  // - TypeB has 2 properties: 'anotherProperty', 'desiredProperty'
  // 1. The 32 'otherProperty's will consume all of the individual storages
  // 2. TypeA.desiredProperty and TypeB.anotherProperty will both be assigned
  //    SectionId = 0 for their respective types.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder()
                       .SetType("TypeA")
                       .AddProperty(int_property_config)
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty2"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty3"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty4"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty5"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty6"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty7"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty8"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty9"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty10"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty11"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty12"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty13"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty14"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty15"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty16"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty17"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty18"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty19"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty20"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty21"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty22"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty23"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty24"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty25"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty26"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty27"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty28"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty29"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty30"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty31"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("otherProperty32"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("desiredProperty"))
                       .AddProperty(PropertyConfigBuilder(int_property_config)
                                        .SetName("undesiredProperty")))
          .Build();
  ICING_ASSERT_OK(this->schema_store_->SetSchema(
      schema,
      /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Ids are assigned alphabetically, so the property ids are:
  // TypeA.desiredProperty = 0
  // TypeA.otherPropertyN = N
  // TypeA.undesiredProperty = 33
  // TypeB.anotherProperty = 0
  // TypeB.desiredProperty = 1
  SectionId typea_desired_prop_id = 0;
  SectionId typea_undesired_prop_id = 33;
  SectionId typea_other1_prop_id = 1;
  std::string desired_property = "desiredProperty";
  std::string undesired_property = "undesiredProperty";
  std::string another_property = "anotherProperty";
  std::string other_property_1 = "otherProperty1";

  // Only the hits for 'desired_prop_id' should be returned.
  std::vector<SectionId> expected_sections_typea = {typea_desired_prop_id};

  // Put 11 docs of "TypeA" into the document store.
  DocumentProto doc =
      DocumentBuilder().SetKey("ns1", "uri0").SetSchema("TypeA").Build();
  ICING_ASSERT_OK(this->doc_store_->Put(doc));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri1").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri2").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri3").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri4").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri5").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri6").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri7").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri8").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri9").Build()));
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri10").Build()));

  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<IntegerIndex> integer_index,
        IntegerIndex::Create(filesystem_, working_path_,
                             GetParam().num_data_threshold_for_bucket_split,
                             GetParam().pre_mapping_fbv));

    // Index numeric content for other properties to force our property into the
    // wildcard storage.
    std::string other_property_path = "otherProperty";
    for (int i = 1; i <= IntegerIndex::kMaxPropertyStorages; ++i) {
      Index(integer_index.get(),
            absl_ports::StrCat(other_property_path, std::to_string(i)),
            /*document_id=*/0, /*section_id=*/i, /*keys=*/{i});
    }

    // Index numeric content for TypeA.desiredProperty
    Index(integer_index.get(), desired_property, /*document_id=*/0,
          typea_desired_prop_id, /*keys=*/{1});
    Index(integer_index.get(), desired_property, /*document_id=*/1,
          typea_desired_prop_id, /*keys=*/{3});
    Index(integer_index.get(), desired_property, /*document_id=*/2,
          typea_desired_prop_id, /*keys=*/{2});
    Index(integer_index.get(), desired_property, /*document_id=*/3,
          typea_desired_prop_id, /*keys=*/{0});
    Index(integer_index.get(), desired_property, /*document_id=*/4,
          typea_desired_prop_id, /*keys=*/{4});
    Index(integer_index.get(), desired_property, /*document_id=*/5,
          typea_desired_prop_id, /*keys=*/{2});

    // Index the same numeric content for TypeA.undesiredProperty
    Index(integer_index.get(), undesired_property, /*document_id=*/6,
          typea_undesired_prop_id, /*keys=*/{3});
    Index(integer_index.get(), undesired_property, /*document_id=*/7,
          typea_undesired_prop_id, /*keys=*/{2});
    Index(integer_index.get(), undesired_property, /*document_id=*/8,
          typea_undesired_prop_id, /*keys=*/{0});
    Index(integer_index.get(), undesired_property, /*document_id=*/9,
          typea_undesired_prop_id, /*keys=*/{4});
    Index(integer_index.get(), undesired_property, /*document_id=*/10,
          typea_undesired_prop_id, /*keys=*/{2});

    // Delete all the docs that had hits in otherProperty* and
    // undesiredProperty.
    ICING_ASSERT_OK(doc_store_->Delete(/*document_id=*/0,
                                       clock_.GetSystemTimeMilliseconds()));
    ICING_ASSERT_OK(doc_store_->Delete(/*document_id=*/6,
                                       clock_.GetSystemTimeMilliseconds()));
    ICING_ASSERT_OK(doc_store_->Delete(/*document_id=*/7,
                                       clock_.GetSystemTimeMilliseconds()));
    ICING_ASSERT_OK(doc_store_->Delete(/*document_id=*/8,
                                       clock_.GetSystemTimeMilliseconds()));
    ICING_ASSERT_OK(doc_store_->Delete(/*document_id=*/9,
                                       clock_.GetSystemTimeMilliseconds()));
    ICING_ASSERT_OK(doc_store_->Delete(/*document_id=*/10,
                                       clock_.GetSystemTimeMilliseconds()));
    // Delete doc id = 0, 6, 7, 8, 9, 10. Compress and keep the rest.
    ICING_ASSERT_OK_AND_ASSIGN(std::vector<DocumentId> document_id_old_to_new,
                               CompactDocStore());

    DocumentId new_last_added_document_id = 5 - 1;
    EXPECT_THAT(integer_index->Optimize(document_id_old_to_new,
                                        new_last_added_document_id),
                IsOk());
    EXPECT_THAT(integer_index->last_added_document_id(),
                Eq(new_last_added_document_id));

    EXPECT_THAT(
        Query(integer_index.get(), desired_property,
              /*key_lower=*/2, /*key_upper=*/2),
        IsOkAndHolds(ElementsAre(
            EqualsDocHitInfo(/*document_id=*/5 - 1, expected_sections_typea),
            EqualsDocHitInfo(/*document_id=*/2 - 1, expected_sections_typea))));

    EXPECT_THAT(
        Query(integer_index.get(), desired_property,
              /*key_lower=*/1, /*key_upper=*/3),
        IsOkAndHolds(ElementsAre(
            EqualsDocHitInfo(/*document_id=*/5 - 1, expected_sections_typea),
            EqualsDocHitInfo(/*document_id=*/2 - 1, expected_sections_typea),
            EqualsDocHitInfo(/*document_id=*/1 - 1, expected_sections_typea))));
  }

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<IntegerIndex> integer_index,
      IntegerIndex::Create(filesystem_, working_path_,
                           GetParam().num_data_threshold_for_bucket_split,
                           GetParam().pre_mapping_fbv));

  EXPECT_THAT(integer_index->num_property_indices(), Eq(1));

  // Add a new doc (docid==5) and a hit for desiredProperty. This should still
  // be placed into the wildcard integer storage.
  doc = DocumentBuilder().SetKey("ns1", "uri11").SetSchema("TypeA").Build();
  ICING_ASSERT_OK(this->doc_store_->Put(doc));
  Index(integer_index.get(), desired_property, /*document_id=*/5,
        typea_desired_prop_id, /*keys=*/{12});
  EXPECT_THAT(integer_index->num_property_indices(), Eq(1));

  EXPECT_THAT(Query(integer_index.get(), desired_property,
                    /*key_lower=*/12, /*key_upper=*/12),
              IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
                  /*document_id=*/5, expected_sections_typea))));

  // Add a new doc (docid==6) and a hit for undesiredProperty. This should still
  // be placed into the wildcard integer storage.
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri12").Build()));
  Index(integer_index.get(), undesired_property, /*document_id=*/6,
        typea_undesired_prop_id, /*keys=*/{3});
  EXPECT_THAT(integer_index->num_property_indices(), Eq(1));

  expected_sections_typea = {typea_undesired_prop_id};
  EXPECT_THAT(Query(integer_index.get(), undesired_property,
                    /*key_lower=*/3, /*key_upper=*/3),
              IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
                  /*document_id=*/6, expected_sections_typea))));

  // Add a new doc (docid==7) and a hit for otherProperty1. This should be given
  // its own individual storage.
  ICING_ASSERT_OK(
      this->doc_store_->Put(DocumentBuilder(doc).SetUri("uri13").Build()));
  Index(integer_index.get(), other_property_1, /*document_id=*/7,
        typea_other1_prop_id, /*keys=*/{3});
  EXPECT_THAT(integer_index->num_property_indices(), Eq(2));

  expected_sections_typea = {typea_other1_prop_id};
  EXPECT_THAT(Query(integer_index.get(), other_property_1,
                    /*key_lower=*/3, /*key_upper=*/3),
              IsOkAndHolds(ElementsAre(EqualsDocHitInfo(
                  /*document_id=*/7, expected_sections_typea))));
}

TEST_P(IntegerIndexTest, IteratorCallStats) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<IntegerIndex> integer_index,
      IntegerIndex::Create(filesystem_, working_path_,
                           GetParam().num_data_threshold_for_bucket_split,
                           GetParam().pre_mapping_fbv));

  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
        kDefaultSectionId, /*keys=*/{1});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        kDefaultSectionId, /*keys=*/{3});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        kDefaultSectionId, /*keys=*/{2});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/3,
        kDefaultSectionId, /*keys=*/{0});

  // GetIterator for range [INT_MIN, INT_MAX] and Advance all. Those 4 keys are
  // in 1 single bucket, so there will be only 1 posting list (and 1 block).
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> iter,
      integer_index->GetIterator(
          kDefaultTestPropertyPath,
          /*key_lower=*/std::numeric_limits<int64_t>::min(),
          /*key_upper=*/std::numeric_limits<int64_t>::max(), *doc_store_,
          *schema_store_, clock_.GetSystemTimeMilliseconds()));

  // 1 block should be read even without calling Advance(), since we read the
  // posting list and put bucket into the priority queue in ctor.
  EXPECT_THAT(iter->GetCallStats(),
              EqualsDocHitInfoIteratorCallStats(
                  /*num_leaf_advance_calls_lite_index=*/0,
                  /*num_leaf_advance_calls_main_index=*/0,
                  /*num_leaf_advance_calls_integer_index=*/1,
                  /*num_leaf_advance_calls_no_index=*/0,
                  /*num_blocks_inspected=*/1));

  // 1st Advance().
  ICING_ASSERT_OK(iter->Advance());
  EXPECT_THAT(iter->GetCallStats(),
              EqualsDocHitInfoIteratorCallStats(
                  /*num_leaf_advance_calls_lite_index=*/0,
                  /*num_leaf_advance_calls_main_index=*/0,
                  /*num_leaf_advance_calls_integer_index=*/2,
                  /*num_leaf_advance_calls_no_index=*/0,
                  /*num_blocks_inspected=*/1));

  // 2nd Advance().
  ICING_ASSERT_OK(iter->Advance());
  EXPECT_THAT(iter->GetCallStats(),
              EqualsDocHitInfoIteratorCallStats(
                  /*num_leaf_advance_calls_lite_index=*/0,
                  /*num_leaf_advance_calls_main_index=*/0,
                  /*num_leaf_advance_calls_integer_index=*/3,
                  /*num_leaf_advance_calls_no_index=*/0,
                  /*num_blocks_inspected=*/1));

  // 3rd Advance().
  ICING_ASSERT_OK(iter->Advance());
  EXPECT_THAT(iter->GetCallStats(),
              EqualsDocHitInfoIteratorCallStats(
                  /*num_leaf_advance_calls_lite_index=*/0,
                  /*num_leaf_advance_calls_main_index=*/0,
                  /*num_leaf_advance_calls_integer_index=*/4,
                  /*num_leaf_advance_calls_no_index=*/0,
                  /*num_blocks_inspected=*/1));

  // 4th Advance().
  ICING_ASSERT_OK(iter->Advance());
  EXPECT_THAT(iter->GetCallStats(),
              EqualsDocHitInfoIteratorCallStats(
                  /*num_leaf_advance_calls_lite_index=*/0,
                  /*num_leaf_advance_calls_main_index=*/0,
                  /*num_leaf_advance_calls_integer_index=*/4,
                  /*num_leaf_advance_calls_no_index=*/0,
                  /*num_blocks_inspected=*/1));

  // 5th Advance().
  ASSERT_THAT(iter->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  EXPECT_THAT(iter->GetCallStats(),
              EqualsDocHitInfoIteratorCallStats(
                  /*num_leaf_advance_calls_lite_index=*/0,
                  /*num_leaf_advance_calls_main_index=*/0,
                  /*num_leaf_advance_calls_integer_index=*/4,
                  /*num_leaf_advance_calls_no_index=*/0,
                  /*num_blocks_inspected=*/1));
}

TEST_P(IntegerIndexTest, IteratorCallStatsNonExistingProperty) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<IntegerIndex> integer_index,
      IntegerIndex::Create(filesystem_, working_path_,
                           GetParam().num_data_threshold_for_bucket_split,
                           GetParam().pre_mapping_fbv));

  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/0,
        kDefaultSectionId, /*keys=*/{1});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/1,
        kDefaultSectionId, /*keys=*/{3});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/2,
        kDefaultSectionId, /*keys=*/{2});
  Index(integer_index.get(), kDefaultTestPropertyPath, /*document_id=*/3,
        kDefaultSectionId, /*keys=*/{0});

  // GetIterator for property "otherProperty1".
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<DocHitInfoIterator> iter,
      integer_index->GetIterator(
          "otherProperty1", /*key_lower=*/std::numeric_limits<int64_t>::min(),
          /*key_upper=*/std::numeric_limits<int64_t>::max(), *doc_store_,
          *schema_store_, clock_.GetSystemTimeMilliseconds()));

  EXPECT_THAT(iter->GetCallStats(),
              EqualsDocHitInfoIteratorCallStats(
                  /*num_leaf_advance_calls_lite_index=*/0,
                  /*num_leaf_advance_calls_main_index=*/0,
                  /*num_leaf_advance_calls_integer_index=*/0,
                  /*num_leaf_advance_calls_no_index=*/0,
                  /*num_blocks_inspected=*/0));

  // 1st Advance().
  ASSERT_THAT(iter->Advance(),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));
  EXPECT_THAT(iter->GetCallStats(),
              EqualsDocHitInfoIteratorCallStats(
                  /*num_leaf_advance_calls_lite_index=*/0,
                  /*num_leaf_advance_calls_main_index=*/0,
                  /*num_leaf_advance_calls_integer_index=*/0,
                  /*num_leaf_advance_calls_no_index=*/0,
                  /*num_blocks_inspected=*/0));
}

INSTANTIATE_TEST_SUITE_P(
    IntegerIndexTest, IntegerIndexTest,
    testing::Values(
        IntegerIndexTestParam(/*num_data_threshold_for_bucket_split_in=*/341,
                              /*pre_mapping_fbv_in=*/false),
        IntegerIndexTestParam(/*num_data_threshold_for_bucket_split_in=*/341,
                              /*pre_mapping_fbv_in=*/true),

        IntegerIndexTestParam(/*num_data_threshold_for_bucket_split_in=*/16384,
                              /*pre_mapping_fbv_in=*/false),
        IntegerIndexTestParam(/*num_data_threshold_for_bucket_split_in=*/32768,
                              /*pre_mapping_fbv_in=*/false),
        IntegerIndexTestParam(/*num_data_threshold_for_bucket_split_in=*/65536,
                              /*pre_mapping_fbv_in=*/false)));

}  // namespace

}  // namespace lib
}  // namespace icing
