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

#include "icing/schema/schema-store.h"

#include <memory>
#include <string>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/file/mock-filesystem.h"
#include "icing/file/version-util.h"
#include "icing/portable/equals-proto.h"
#include "icing/proto/debug.pb.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/logging.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/storage.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/schema-builder.h"
#include "icing/schema/schema-util.h"
#include "icing/schema/section-manager.h"
#include "icing/schema/section.h"
#include "icing/store/document-filter-data.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/tmp-directory.h"
#include "icing/util/crc32.h"

namespace icing {
namespace lib {

namespace {

using ::icing::lib::portable_equals_proto::EqualsProto;
using ::testing::ElementsAre;
using ::testing::Eq;
using ::testing::Ge;
using ::testing::Gt;
using ::testing::HasSubstr;
using ::testing::Not;
using ::testing::Pointee;
using ::testing::Return;
using ::testing::SizeIs;
using ::testing::UnorderedElementsAre;

constexpr int64_t kDefaultTimestamp = 12345678;

class SchemaStoreTest : public ::testing::Test {
 protected:
  void SetUp() override {
    test_dir_ = GetTestTempDir() + "/icing";
    schema_store_dir_ = test_dir_ + "/schema_store";
    filesystem_.CreateDirectoryRecursively(schema_store_dir_.c_str());

    schema_ = SchemaBuilder()
                  .AddType(SchemaTypeConfigBuilder()
                               .SetType("email")
                               .AddProperty(
                                   // Add an indexed property so we generate
                                   // section metadata on it
                                   PropertyConfigBuilder()
                                       .SetName("subject")
                                       .SetDataTypeString(TERM_MATCH_EXACT,
                                                          TOKENIZER_PLAIN)
                                       .SetCardinality(CARDINALITY_OPTIONAL))
                               .AddProperty(
                                   PropertyConfigBuilder()
                                       .SetName("timestamp")
                                       .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                       .SetCardinality(CARDINALITY_OPTIONAL)))
                  .Build();
  }

  void TearDown() override {
    // Check that the schema store directory is the *only* directory in the
    // schema_store_dir_. IOW, ensure that all temporary directories have been
    // properly cleaned up.
    std::vector<std::string> sub_dirs;
    ASSERT_TRUE(filesystem_.ListDirectory(test_dir_.c_str(), &sub_dirs));
    ASSERT_THAT(sub_dirs, ElementsAre("schema_store"));

    // Finally, clean everything up.
    ASSERT_TRUE(filesystem_.DeleteDirectoryRecursively(test_dir_.c_str()));
  }

  Filesystem filesystem_;
  std::string test_dir_;
  std::string schema_store_dir_;
  SchemaProto schema_;
  FakeClock fake_clock_;
};

TEST_F(SchemaStoreTest, CreationWithNullPointerShouldFail) {
  EXPECT_THAT(SchemaStore::Create(/*filesystem=*/nullptr, schema_store_dir_,
                                  &fake_clock_),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
}

TEST_F(SchemaStoreTest, SchemaStoreMoveConstructible) {
  // Create an instance of SchemaStore.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("type_a").AddProperty(
              PropertyConfigBuilder()
                  .SetName("prop1")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 expected_checksum,
                             schema_store->ComputeChecksum());

  // Move construct an instance of SchemaStore
  SchemaStore move_constructed_schema_store(std::move(*schema_store));
  EXPECT_THAT(move_constructed_schema_store.GetSchema(),
              IsOkAndHolds(Pointee(EqualsProto(schema))));
  EXPECT_THAT(move_constructed_schema_store.ComputeChecksum(),
              IsOkAndHolds(Eq(expected_checksum)));
  SectionMetadata expected_metadata(/*id_in=*/0, TYPE_STRING, TOKENIZER_PLAIN,
                                    TERM_MATCH_EXACT, NUMERIC_MATCH_UNKNOWN,
                                    "prop1");
  EXPECT_THAT(move_constructed_schema_store.GetSectionMetadata("type_a"),
              IsOkAndHolds(Pointee(ElementsAre(expected_metadata))));
}

TEST_F(SchemaStoreTest, SchemaStoreMoveAssignment) {
  // Create an instance of SchemaStore.
  SchemaProto schema1 =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("type_a").AddProperty(
              PropertyConfigBuilder()
                  .SetName("prop1")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  ICING_ASSERT_OK(schema_store->SetSchema(
      schema1, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));
  ICING_ASSERT_OK_AND_ASSIGN(Crc32 expected_checksum,
                             schema_store->ComputeChecksum());

  // Construct another instance of SchemaStore
  SchemaProto schema2 =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("type_b").AddProperty(
              PropertyConfigBuilder()
                  .SetName("prop2")
                  .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> move_assigned_schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema2, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Move assign the first instance into the second one.
  *move_assigned_schema_store = std::move(*schema_store);
  EXPECT_THAT(move_assigned_schema_store->GetSchema(),
              IsOkAndHolds(Pointee(EqualsProto(schema1))));
  EXPECT_THAT(move_assigned_schema_store->ComputeChecksum(),
              IsOkAndHolds(Eq(expected_checksum)));
  SectionMetadata expected_metadata(/*id_in=*/0, TYPE_STRING, TOKENIZER_PLAIN,
                                    TERM_MATCH_EXACT, NUMERIC_MATCH_UNKNOWN,
                                    "prop1");
  EXPECT_THAT(move_assigned_schema_store->GetSectionMetadata("type_a"),
              IsOkAndHolds(Pointee(ElementsAre(expected_metadata))));
}

TEST_F(SchemaStoreTest, CorruptSchemaError) {
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

    // Set it for the first time
    SchemaStore::SetSchemaResult result;
    result.success = true;
    result.schema_types_new_by_name.insert(schema_.types(0).schema_type());
    EXPECT_THAT(schema_store->SetSchema(
                    schema_, /*ignore_errors_and_delete_documents=*/false,
                    /*allow_circular_schema_definitions=*/false),
                IsOkAndHolds(EqualsSetSchemaResult(result)));
    ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                               schema_store->GetSchema());
    EXPECT_THAT(*actual_schema, EqualsProto(schema_));
  }

  // "Corrupt" the  ground truth schema by adding new data to it. This will mess
  // up the checksum of the schema store

  SchemaProto corrupt_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("corrupted"))
          .Build();

  const std::string schema_file =
      absl_ports::StrCat(schema_store_dir_, "/schema.pb");
  const std::string serialized_schema = corrupt_schema.SerializeAsString();

  filesystem_.Write(schema_file.c_str(), serialized_schema.data(),
                    serialized_schema.size());

  // If ground truth was corrupted, we won't know what to do
  EXPECT_THAT(
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_),
      StatusIs(libtextclassifier3::StatusCode::INTERNAL));
}

TEST_F(SchemaStoreTest, RecoverCorruptDerivedFileOk) {
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

    // Set it for the first time
    SchemaStore::SetSchemaResult result;
    result.success = true;
    result.schema_types_new_by_name.insert(schema_.types(0).schema_type());
    EXPECT_THAT(schema_store->SetSchema(
                    schema_, /*ignore_errors_and_delete_documents=*/false,
                    /*allow_circular_schema_definitions=*/false),
                IsOkAndHolds(EqualsSetSchemaResult(result)));
    ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                               schema_store->GetSchema());
    EXPECT_THAT(*actual_schema, EqualsProto(schema_));

    EXPECT_THAT(schema_store->GetSchemaTypeId("email"), IsOkAndHolds(0));
  }

  // "Corrupt" the derived SchemaTypeIds by deleting the entire directory. This
  // will mess up the initialization of schema store, causing everything to be
  // regenerated from ground truth

  const std::string schema_type_mapper_dir =
      absl_ports::StrCat(schema_store_dir_, "/schema_type_mapper");
  filesystem_.DeleteDirectoryRecursively(schema_type_mapper_dir.c_str());

  InitializeStatsProto initialize_stats;
  fake_clock_.SetTimerElapsedMilliseconds(123);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_,
                          &initialize_stats));
  EXPECT_THAT(initialize_stats.schema_store_recovery_cause(),
              Eq(InitializeStatsProto::IO_ERROR));
  EXPECT_THAT(initialize_stats.schema_store_recovery_latency_ms(), Eq(123));

  // Everything looks fine, ground truth and derived data
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema_));
  EXPECT_THAT(schema_store->GetSchemaTypeId("email"), IsOkAndHolds(0));
}

TEST_F(SchemaStoreTest, RecoverDiscardDerivedFilesOk) {
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

    // Set it for the first time
    SchemaStore::SetSchemaResult result;
    result.success = true;
    result.schema_types_new_by_name.insert(schema_.types(0).schema_type());
    EXPECT_THAT(schema_store->SetSchema(
                    schema_, /*ignore_errors_and_delete_documents=*/false,
                    /*allow_circular_schema_definitions=*/false),
                IsOkAndHolds(EqualsSetSchemaResult(result)));
    ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                               schema_store->GetSchema());
    EXPECT_THAT(*actual_schema, EqualsProto(schema_));

    EXPECT_THAT(schema_store->GetSchemaTypeId("email"), IsOkAndHolds(0));
  }

  ICING_ASSERT_OK(
      SchemaStore::DiscardDerivedFiles(&filesystem_, schema_store_dir_));

  InitializeStatsProto initialize_stats;
  fake_clock_.SetTimerElapsedMilliseconds(123);
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_,
                          &initialize_stats));
  EXPECT_THAT(initialize_stats.schema_store_recovery_cause(),
              Eq(InitializeStatsProto::IO_ERROR));
  EXPECT_THAT(initialize_stats.schema_store_recovery_latency_ms(), Eq(123));

  // Everything looks fine, ground truth and derived data
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema_));
  EXPECT_THAT(schema_store->GetSchemaTypeId("email"), IsOkAndHolds(0));
}

TEST_F(SchemaStoreTest, RecoverBadChecksumOk) {
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

    // Set it for the first time
    SchemaStore::SetSchemaResult result;
    result.success = true;
    result.schema_types_new_by_name.insert(schema_.types(0).schema_type());
    EXPECT_THAT(schema_store->SetSchema(
                    schema_, /*ignore_errors_and_delete_documents=*/false,
                    /*allow_circular_schema_definitions=*/false),
                IsOkAndHolds(EqualsSetSchemaResult(result)));
    ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                               schema_store->GetSchema());
    EXPECT_THAT(*actual_schema, EqualsProto(schema_));

    EXPECT_THAT(schema_store->GetSchemaTypeId("email"), IsOkAndHolds(0));
  }

  // Change the SchemaStore's header combined checksum so that it won't match
  // the recalculated checksum on initialization. This will force a regeneration
  // of derived files from ground truth.
  const std::string header_file =
      absl_ports::StrCat(schema_store_dir_, "/schema_store_header");
  SchemaStore::LegacyHeader header;
  header.magic = SchemaStore::Header::kMagic;
  header.checksum = 10;  // Arbitrary garbage checksum
  filesystem_.DeleteFile(header_file.c_str());
  filesystem_.Write(header_file.c_str(), &header, sizeof(header));

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // Everything looks fine, ground truth and derived data
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema_));
  EXPECT_THAT(schema_store->GetSchemaTypeId("email"), IsOkAndHolds(0));
}

TEST_F(SchemaStoreTest, CreateNoPreviousSchemaOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // The apis to retrieve information about the schema should fail gracefully.
  EXPECT_THAT(store->GetSchema(),
              StatusIs(libtextclassifier3::StatusCode::NOT_FOUND));
  EXPECT_THAT(store->GetSchemaTypeConfig("foo"),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
  EXPECT_THAT(store->GetSchemaTypeId("foo"),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
  EXPECT_THAT(store->GetSectionMetadata(/*schema_type_id=*/0, /*section_id=*/0),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
  EXPECT_THAT(store->GetJoinablePropertyMetadata(/*schema_type_id=*/0,
                                                 /*property_path=*/"A"),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));

  // The apis to extract content from a document should fail gracefully.
  DocumentProto doc;
  PropertyProto* prop = doc.add_properties();
  prop->set_name("name");
  prop->add_string_values("foo bar baz");

  EXPECT_THAT(store->ExtractSections(doc),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));
  EXPECT_THAT(store->ExtractJoinableProperties(doc),
              StatusIs(libtextclassifier3::StatusCode::FAILED_PRECONDITION));

  // The apis to persist and checksum data should succeed.
  EXPECT_THAT(store->ComputeChecksum(), IsOkAndHolds(Crc32()));
  EXPECT_THAT(store->PersistToDisk(), IsOk());
}

TEST_F(SchemaStoreTest, CreateWithPreviousSchemaOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  SchemaStore::SetSchemaResult result;
  result.success = true;
  result.schema_types_new_by_name.insert(schema_.types(0).schema_type());
  EXPECT_THAT(schema_store->SetSchema(
                  schema_, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));

  schema_store.reset();
  EXPECT_THAT(
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_),
      IsOk());
}

TEST_F(SchemaStoreTest, MultipleCreateOk) {
  DocumentProto document;
  document.set_schema("email");
  auto subject_property = document.add_properties();
  subject_property->set_name("subject");
  subject_property->add_string_values("subject_content");
  auto timestamp_property = document.add_properties();
  timestamp_property->set_name("timestamp");
  timestamp_property->add_int64_values(kDefaultTimestamp);

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  SchemaStore::SetSchemaResult result;
  result.success = true;
  result.schema_types_new_by_name.insert(schema_.types(0).schema_type());
  EXPECT_THAT(schema_store->SetSchema(
                  schema_, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));

  // Verify that our in-memory structures are ok
  EXPECT_THAT(schema_store->GetSchemaTypeConfig("email"),
              IsOkAndHolds(Pointee(EqualsProto(schema_.types(0)))));
  ICING_ASSERT_OK_AND_ASSIGN(SectionGroup section_group,
                             schema_store->ExtractSections(document));
  EXPECT_THAT(section_group.string_sections[0].content,
              ElementsAre("subject_content"));
  EXPECT_THAT(section_group.integer_sections[0].content,
              ElementsAre(kDefaultTimestamp));

  // Verify that our persisted data is ok
  EXPECT_THAT(schema_store->GetSchemaTypeId("email"), IsOkAndHolds(0));

  schema_store.reset();
  ICING_ASSERT_OK_AND_ASSIGN(
      schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // Verify that our in-memory structures are ok
  EXPECT_THAT(schema_store->GetSchemaTypeConfig("email"),
              IsOkAndHolds(Pointee(EqualsProto(schema_.types(0)))));

  ICING_ASSERT_OK_AND_ASSIGN(section_group,
                             schema_store->ExtractSections(document));
  EXPECT_THAT(section_group.string_sections[0].content,
              ElementsAre("subject_content"));
  EXPECT_THAT(section_group.integer_sections[0].content,
              ElementsAre(kDefaultTimestamp));

  // Verify that our persisted data is ok
  EXPECT_THAT(schema_store->GetSchemaTypeId("email"), IsOkAndHolds(0));
}

TEST_F(SchemaStoreTest, SetNewSchemaOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // Set it for the first time
  SchemaStore::SetSchemaResult result;
  result.success = true;
  result.schema_types_new_by_name.insert(schema_.types(0).schema_type());
  EXPECT_THAT(schema_store->SetSchema(
                  schema_, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema_));
}

TEST_F(SchemaStoreTest, SetSameSchemaOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // Set it for the first time
  SchemaStore::SetSchemaResult result;
  result.success = true;
  result.schema_types_new_by_name.insert(schema_.types(0).schema_type());
  EXPECT_THAT(schema_store->SetSchema(
                  schema_, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema_));

  // And one more for fun
  result = SchemaStore::SetSchemaResult();
  result.success = true;
  EXPECT_THAT(schema_store->SetSchema(
                  schema_, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(actual_schema, schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema_));
}

TEST_F(SchemaStoreTest, SetIncompatibleSchemaOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // Set it for the first time
  SchemaStore::SetSchemaResult result;
  result.success = true;
  result.schema_types_new_by_name.insert(schema_.types(0).schema_type());
  EXPECT_THAT(schema_store->SetSchema(
                  schema_, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema_));

  // Make the schema incompatible by removing a type.
  schema_.clear_types();

  // Set the incompatible schema
  result = SchemaStore::SetSchemaResult();
  result.success = false;
  result.schema_types_deleted_by_name.emplace("email");
  result.schema_types_deleted_by_id.emplace(0);
  EXPECT_THAT(schema_store->SetSchema(
                  schema_, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));
}

TEST_F(SchemaStoreTest, SetSchemaWithAddedTypeOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  SchemaProto schema = SchemaBuilder()
                           .AddType(SchemaTypeConfigBuilder().SetType("email"))
                           .Build();

  // Set it for the first time
  SchemaStore::SetSchemaResult result;
  result.success = true;
  result.schema_types_new_by_name.insert("email");
  EXPECT_THAT(schema_store->SetSchema(
                  schema, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema));

  // Add a type, shouldn't affect the index or cached SchemaTypeIds
  schema = SchemaBuilder(schema)
               .AddType(SchemaTypeConfigBuilder().SetType("new_type"))
               .Build();

  // Set the compatible schema
  result = SchemaStore::SetSchemaResult();
  result.success = true;
  result.schema_types_new_by_name.insert("new_type");
  EXPECT_THAT(schema_store->SetSchema(
                  schema, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(actual_schema, schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema));
}

TEST_F(SchemaStoreTest, SetSchemaWithDeletedTypeOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("email"))
          .AddType(SchemaTypeConfigBuilder().SetType("message"))
          .Build();

  // Set it for the first time
  SchemaStore::SetSchemaResult result;
  result.success = true;
  result.schema_types_new_by_name.insert("email");
  result.schema_types_new_by_name.insert("message");
  EXPECT_THAT(schema_store->SetSchema(
                  schema, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema));

  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId old_email_schema_type_id,
                             schema_store->GetSchemaTypeId("email"));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId old_message_schema_type_id,
                             schema_store->GetSchemaTypeId("message"));

  // Remove "email" type, this also changes previous SchemaTypeIds
  schema = SchemaBuilder()
               .AddType(SchemaTypeConfigBuilder().SetType("message"))
               .Build();

  SchemaStore::SetSchemaResult incompatible_result;
  incompatible_result.success = false;
  incompatible_result.old_schema_type_ids_changed.emplace(
      old_message_schema_type_id);
  incompatible_result.schema_types_deleted_by_name.emplace("email");
  incompatible_result.schema_types_deleted_by_id.emplace(
      old_email_schema_type_id);

  // Can't set the incompatible schema
  EXPECT_THAT(schema_store->SetSchema(
                  schema, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(incompatible_result)));

  SchemaStore::SetSchemaResult force_result;
  force_result.success = true;
  force_result.old_schema_type_ids_changed.emplace(old_message_schema_type_id);
  force_result.schema_types_deleted_by_name.emplace("email");
  force_result.schema_types_deleted_by_id.emplace(old_email_schema_type_id);

  // Force set the incompatible schema
  EXPECT_THAT(schema_store->SetSchema(
                  schema, /*ignore_errors_and_delete_documents=*/true,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(force_result)));
  ICING_ASSERT_OK_AND_ASSIGN(actual_schema, schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema));
}

TEST_F(SchemaStoreTest, SetSchemaWithReorderedTypesOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("email"))
          .AddType(SchemaTypeConfigBuilder().SetType("message"))
          .Build();

  // Set it for the first time
  SchemaStore::SetSchemaResult result;
  result.success = true;
  result.schema_types_new_by_name.insert("email");
  result.schema_types_new_by_name.insert("message");
  EXPECT_THAT(schema_store->SetSchema(
                  schema, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema));

  // Reorder the types
  schema = SchemaBuilder()
               .AddType(SchemaTypeConfigBuilder().SetType("message"))
               .AddType(SchemaTypeConfigBuilder().SetType("email"))
               .Build();

  // Since we assign SchemaTypeIds based on order in the SchemaProto, this will
  // cause SchemaTypeIds to change
  result = SchemaStore::SetSchemaResult();
  result.success = true;
  result.old_schema_type_ids_changed.emplace(0);  // Old SchemaTypeId of "email"
  result.old_schema_type_ids_changed.emplace(
      1);  // Old SchemaTypeId of "message"

  // Set the compatible schema
  EXPECT_THAT(schema_store->SetSchema(
                  schema, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(actual_schema, schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema));
}

TEST_F(SchemaStoreTest, IndexedPropertyChangeRequiresReindexingOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("email").AddProperty(
              // Add an unindexed property
              PropertyConfigBuilder()
                  .SetName("subject")
                  .SetDataType(TYPE_STRING)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Set it for the first time
  SchemaStore::SetSchemaResult result;
  result.success = true;
  result.schema_types_new_by_name.insert("email");
  EXPECT_THAT(schema_store->SetSchema(
                  schema, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema));

  // Make a previously unindexed property indexed
  schema = SchemaBuilder()
               .AddType(SchemaTypeConfigBuilder().SetType("email").AddProperty(
                   PropertyConfigBuilder()
                       .SetName("subject")
                       .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                       .SetCardinality(CARDINALITY_OPTIONAL)))
               .Build();

  // Set the compatible schema
  result = SchemaStore::SetSchemaResult();
  result.success = true;
  result.schema_types_index_incompatible_by_name.insert("email");
  EXPECT_THAT(schema_store->SetSchema(
                  schema, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(actual_schema, schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema));
}

TEST_F(SchemaStoreTest, IndexNestedDocumentsChangeRequiresReindexingOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // Make two schemas. One that sets index_nested_properties to false and one
  // that sets it to true.
  SchemaTypeConfigProto email_type_config =
      SchemaTypeConfigBuilder()
          .SetType("email")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("subject")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto no_nested_index_schema =
      SchemaBuilder()
          .AddType(email_type_config)
          .AddType(SchemaTypeConfigBuilder().SetType("person").AddProperty(
              PropertyConfigBuilder()
                  .SetName("emails")
                  .SetDataTypeDocument("email",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  SchemaProto nested_index_schema =
      SchemaBuilder()
          .AddType(email_type_config)
          .AddType(SchemaTypeConfigBuilder().SetType("person").AddProperty(
              PropertyConfigBuilder()
                  .SetName("emails")
                  .SetDataTypeDocument("email",
                                       /*index_nested_properties=*/true)
                  .SetCardinality(CARDINALITY_REPEATED)))
          .Build();

  // Set schema with index_nested_properties=false to start.
  SchemaStore::SetSchemaResult result;
  result.success = true;
  result.schema_types_new_by_name.insert("email");
  result.schema_types_new_by_name.insert("person");
  EXPECT_THAT(
      schema_store->SetSchema(no_nested_index_schema,
                              /*ignore_errors_and_delete_documents=*/false,
                              /*allow_circular_schema_definitions=*/false),
      IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(no_nested_index_schema));

  // Set schema with index_nested_properties=true and confirm that the change to
  // 'person' is index incompatible.
  result = SchemaStore::SetSchemaResult();
  result.success = true;
  result.schema_types_index_incompatible_by_name.insert("person");
  EXPECT_THAT(
      schema_store->SetSchema(nested_index_schema,
                              /*ignore_errors_and_delete_documents=*/false,
                              /*allow_circular_schema_definitions=*/false),
      IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(actual_schema, schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(nested_index_schema));

  // Set schema with index_nested_properties=false and confirm that the change
  // to 'person' is index incompatible.
  result = SchemaStore::SetSchemaResult();
  result.success = true;
  result.schema_types_index_incompatible_by_name.insert("person");
  EXPECT_THAT(
      schema_store->SetSchema(no_nested_index_schema,
                              /*ignore_errors_and_delete_documents=*/false,
                              /*allow_circular_schema_definitions=*/false),
      IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(actual_schema, schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(no_nested_index_schema));
}

TEST_F(SchemaStoreTest, SetSchemaWithIncompatibleTypesOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("email").AddProperty(
              // Add a STRING property
              PropertyConfigBuilder()
                  .SetName("subject")
                  .SetDataType(TYPE_STRING)
                  .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  // Set it for the first time
  SchemaStore::SetSchemaResult result;
  result.success = true;
  result.schema_types_new_by_name.insert("email");
  EXPECT_THAT(schema_store->SetSchema(
                  schema, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema));

  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId old_email_schema_type_id,
                             schema_store->GetSchemaTypeId("email"));

  // Make a previously STRING property into DOUBLE
  schema = SchemaBuilder()
               .AddType(SchemaTypeConfigBuilder().SetType("email").AddProperty(
                   // Add a STRING property
                   PropertyConfigBuilder()
                       .SetName("subject")
                       .SetDataType(TYPE_DOUBLE)
                       .SetCardinality(CARDINALITY_OPTIONAL)))
               .Build();

  SchemaStore::SetSchemaResult incompatible_result;
  incompatible_result.success = false;
  incompatible_result.schema_types_incompatible_by_name.emplace("email");
  incompatible_result.schema_types_incompatible_by_id.emplace(
      old_email_schema_type_id);

  // Can't set the incompatible schema
  EXPECT_THAT(schema_store->SetSchema(
                  schema, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(incompatible_result)));

  SchemaStore::SetSchemaResult force_result;
  force_result.success = true;
  force_result.schema_types_incompatible_by_name.emplace("email");
  force_result.schema_types_incompatible_by_id.emplace(
      old_email_schema_type_id);

  // Force set the incompatible schema
  EXPECT_THAT(schema_store->SetSchema(
                  schema, /*ignore_errors_and_delete_documents=*/true,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(force_result)));
  ICING_ASSERT_OK_AND_ASSIGN(actual_schema, schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema));
}

TEST_F(SchemaStoreTest, SetSchemaWithIncompatibleNestedTypesOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // 1. Create a ContactPoint type with a repeated property and set that schema
  SchemaTypeConfigBuilder contact_point_repeated_label =
      SchemaTypeConfigBuilder()
          .SetType("ContactPoint")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("label")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REPEATED));
  SchemaProto old_schema =
      SchemaBuilder().AddType(contact_point_repeated_label).Build();
  ICING_EXPECT_OK(schema_store->SetSchema(
      old_schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId old_contact_point_type_id,
                             schema_store->GetSchemaTypeId("ContactPoint"));

  // 2. Create a type that references the ContactPoint type and make a backwards
  // incompatible change to ContactPoint
  SchemaTypeConfigBuilder contact_point_optional_label =
      SchemaTypeConfigBuilder()
          .SetType("ContactPoint")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("label")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL));
  SchemaTypeConfigBuilder person =
      SchemaTypeConfigBuilder().SetType("Person").AddProperty(
          PropertyConfigBuilder()
              .SetName("contactPoints")
              .SetDataTypeDocument("ContactPoint",
                                   /*index_nested_properties=*/true)
              .SetCardinality(CARDINALITY_REPEATED));
  SchemaProto new_schema = SchemaBuilder()
                               .AddType(contact_point_optional_label)
                               .AddType(person)
                               .Build();

  // 3. SetSchema should fail with ignore_errors_and_delete_documents=false and
  // the old schema should remain
  SchemaStore::SetSchemaResult expected_result;
  expected_result.success = false;
  expected_result.schema_types_incompatible_by_name.insert("ContactPoint");
  expected_result.schema_types_incompatible_by_id.insert(
      old_contact_point_type_id);
  expected_result.schema_types_new_by_name.insert("Person");
  EXPECT_THAT(
      schema_store->SetSchema(new_schema,
                              /*ignore_errors_and_delete_documents=*/false,
                              /*allow_circular_schema_definitions=*/false),
      IsOkAndHolds(EqualsSetSchemaResult(expected_result)));
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(old_schema));

  // 4. SetSchema should succeed with ignore_errors_and_delete_documents=true
  // and the new schema should be set
  expected_result.success = true;
  EXPECT_THAT(
      schema_store->SetSchema(new_schema,
                              /*ignore_errors_and_delete_documents=*/true,
                              /*allow_circular_schema_definitions=*/false),
      IsOkAndHolds(EqualsSetSchemaResult(expected_result)));
  ICING_ASSERT_OK_AND_ASSIGN(actual_schema, schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(new_schema));
}

TEST_F(SchemaStoreTest, SetSchemaWithIndexIncompatibleNestedTypesOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // 1. Create a ContactPoint type with label that matches prefix and set that
  // schema
  SchemaTypeConfigBuilder contact_point_prefix_label =
      SchemaTypeConfigBuilder()
          .SetType("ContactPoint")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("label")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REPEATED));
  SchemaProto old_schema =
      SchemaBuilder().AddType(contact_point_prefix_label).Build();
  ICING_EXPECT_OK(schema_store->SetSchema(
      old_schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // 2. Create a type that references the ContactPoint type and make a index
  // backwards incompatible change to ContactPoint
  SchemaTypeConfigBuilder contact_point_exact_label =
      SchemaTypeConfigBuilder()
          .SetType("ContactPoint")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("label")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_REPEATED));
  SchemaTypeConfigBuilder person =
      SchemaTypeConfigBuilder().SetType("Person").AddProperty(
          PropertyConfigBuilder()
              .SetName("contactPoints")
              .SetDataTypeDocument("ContactPoint",
                                   /*index_nested_properties=*/true)
              .SetCardinality(CARDINALITY_REPEATED));
  SchemaProto new_schema = SchemaBuilder()
                               .AddType(contact_point_exact_label)
                               .AddType(person)
                               .Build();

  // SetSchema should succeed, and only ContactPoint should be in
  // schema_types_index_incompatible_by_name.
  SchemaStore::SetSchemaResult expected_result;
  expected_result.success = true;
  expected_result.schema_types_index_incompatible_by_name.insert(
      "ContactPoint");
  expected_result.schema_types_new_by_name.insert("Person");
  EXPECT_THAT(
      schema_store->SetSchema(new_schema,
                              /*ignore_errors_and_delete_documents=*/false,
                              /*allow_circular_schema_definitions=*/false),
      IsOkAndHolds(EqualsSetSchemaResult(expected_result)));
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(new_schema));
}

TEST_F(SchemaStoreTest, SetSchemaWithCompatibleNestedTypesOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // 1. Create a ContactPoint type with a optional property and set that schema
  SchemaTypeConfigBuilder contact_point_optional_label =
      SchemaTypeConfigBuilder()
          .SetType("ContactPoint")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("label")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_OPTIONAL));
  SchemaProto old_schema =
      SchemaBuilder().AddType(contact_point_optional_label).Build();
  ICING_EXPECT_OK(schema_store->SetSchema(
      old_schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // 2. Create a type that references the ContactPoint type and make a backwards
  // compatible change to ContactPoint
  SchemaTypeConfigBuilder contact_point_repeated_label =
      SchemaTypeConfigBuilder()
          .SetType("ContactPoint")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("label")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REPEATED));
  SchemaTypeConfigBuilder person =
      SchemaTypeConfigBuilder().SetType("Person").AddProperty(
          PropertyConfigBuilder()
              .SetName("contactPoints")
              .SetDataTypeDocument("ContactPoint",
                                   /*index_nested_properties=*/true)
              .SetCardinality(CARDINALITY_REPEATED));
  SchemaProto new_schema = SchemaBuilder()
                               .AddType(contact_point_repeated_label)
                               .AddType(person)
                               .Build();

  // 3. SetSchema should succeed, and only ContactPoint should be in
  // schema_types_changed_fully_compatible_by_name.
  SchemaStore::SetSchemaResult expected_result;
  expected_result.success = true;
  expected_result.schema_types_changed_fully_compatible_by_name.insert(
      "ContactPoint");
  expected_result.schema_types_new_by_name.insert("Person");
  EXPECT_THAT(schema_store->SetSchema(
                  new_schema, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(expected_result)));
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(new_schema));
}

TEST_F(SchemaStoreTest, SetSchemaWithAddedIndexableNestedTypeOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // 1. Create a ContactPoint type with a optional property, and a type that
  //    references the ContactPoint type.
  SchemaTypeConfigBuilder contact_point =
      SchemaTypeConfigBuilder()
          .SetType("ContactPoint")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("label")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REPEATED));
  SchemaTypeConfigBuilder person =
      SchemaTypeConfigBuilder().SetType("Person").AddProperty(
          PropertyConfigBuilder()
              .SetName("contactPoints")
              .SetDataTypeDocument("ContactPoint",
                                   /*index_nested_properties=*/true)
              .SetCardinality(CARDINALITY_REPEATED));
  SchemaProto old_schema =
      SchemaBuilder().AddType(contact_point).AddType(person).Build();
  ICING_EXPECT_OK(schema_store->SetSchema(
      old_schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // 2. Add another nested document property to "Person" that has type
  //    "ContactPoint"
  SchemaTypeConfigBuilder new_person =
      SchemaTypeConfigBuilder()
          .SetType("Person")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("contactPoints")
                  .SetDataTypeDocument("ContactPoint",
                                       /*index_nested_properties=*/true)
                  .SetCardinality(CARDINALITY_REPEATED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("anotherContactPoint")
                  .SetDataTypeDocument("ContactPoint",
                                       /*index_nested_properties=*/true)
                  .SetCardinality(CARDINALITY_REPEATED));
  SchemaProto new_schema =
      SchemaBuilder().AddType(contact_point).AddType(new_person).Build();

  // 3. Set to new schema. "Person" should be index-incompatible since we need
  //    to index an additional property: 'anotherContactPoint.label'.
  // - "Person" is also considered join-incompatible since the added nested
  //   document property could also contain a joinable property.
  SchemaStore::SetSchemaResult expected_result;
  expected_result.success = true;
  expected_result.schema_types_index_incompatible_by_name.insert("Person");
  expected_result.schema_types_join_incompatible_by_name.insert("Person");

  EXPECT_THAT(schema_store->SetSchema(
                  new_schema, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(expected_result)));
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(new_schema));
}

TEST_F(SchemaStoreTest, SetSchemaWithAddedJoinableNestedTypeOk) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // 1. Create a ContactPoint type with a optional property, and a type that
  //    references the ContactPoint type.
  SchemaTypeConfigBuilder contact_point =
      SchemaTypeConfigBuilder()
          .SetType("ContactPoint")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("label")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                               /*propagate_delete=*/false)
                  .SetCardinality(CARDINALITY_REQUIRED));
  SchemaTypeConfigBuilder person =
      SchemaTypeConfigBuilder().SetType("Person").AddProperty(
          PropertyConfigBuilder()
              .SetName("contactPoints")
              .SetDataTypeDocument("ContactPoint",
                                   /*index_nested_properties=*/true)
              .SetCardinality(CARDINALITY_OPTIONAL));
  SchemaProto old_schema =
      SchemaBuilder().AddType(contact_point).AddType(person).Build();
  ICING_EXPECT_OK(schema_store->SetSchema(
      old_schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // 2. Add another nested document property to "Person" that has type
  //    "ContactPoint", but make it non-indexable
  SchemaTypeConfigBuilder new_person =
      SchemaTypeConfigBuilder()
          .SetType("Person")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("contactPoints")
                  .SetDataTypeDocument("ContactPoint",
                                       /*index_nested_properties=*/true)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("anotherContactPoint")
                  .SetDataTypeDocument("ContactPoint",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL));
  SchemaProto new_schema =
      SchemaBuilder().AddType(contact_point).AddType(new_person).Build();

  // 3. Set to new schema. "Person" should be join-incompatible but
  //    index-compatible.
  SchemaStore::SetSchemaResult expected_result;
  expected_result.success = true;
  expected_result.schema_types_join_incompatible_by_name.insert("Person");

  EXPECT_THAT(schema_store->SetSchema(
                  new_schema, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(expected_result)));
  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(new_schema));
}

TEST_F(SchemaStoreTest, GetSchemaTypeId) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  schema_.clear_types();

  // Add a few schema types
  const std::string first_type = "first";
  auto type = schema_.add_types();
  type->set_schema_type(first_type);

  const std::string second_type = "second";
  type = schema_.add_types();
  type->set_schema_type(second_type);

  // Set it for the first time
  SchemaStore::SetSchemaResult result;
  result.success = true;
  result.schema_types_new_by_name.insert(first_type);
  result.schema_types_new_by_name.insert(second_type);
  EXPECT_THAT(schema_store->SetSchema(
                  schema_, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));

  EXPECT_THAT(schema_store->GetSchemaTypeId(first_type), IsOkAndHolds(0));
  EXPECT_THAT(schema_store->GetSchemaTypeId(second_type), IsOkAndHolds(1));
}

TEST_F(SchemaStoreTest, ComputeChecksumDefaultOnEmptySchemaStore) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  Crc32 default_checksum;
  EXPECT_THAT(schema_store->ComputeChecksum(), IsOkAndHolds(default_checksum));
}

TEST_F(SchemaStoreTest, ComputeChecksumSameBetweenCalls) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  SchemaProto foo_schema =
      SchemaBuilder().AddType(SchemaTypeConfigBuilder().SetType("foo")).Build();

  ICING_EXPECT_OK(schema_store->SetSchema(
      foo_schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  ICING_ASSERT_OK_AND_ASSIGN(Crc32 checksum, schema_store->ComputeChecksum());

  // Calling it again doesn't change the checksum
  EXPECT_THAT(schema_store->ComputeChecksum(), IsOkAndHolds(checksum));
}

TEST_F(SchemaStoreTest, ComputeChecksumSameAcrossInstances) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  SchemaProto foo_schema =
      SchemaBuilder().AddType(SchemaTypeConfigBuilder().SetType("foo")).Build();

  ICING_EXPECT_OK(schema_store->SetSchema(
      foo_schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  ICING_ASSERT_OK_AND_ASSIGN(Crc32 checksum, schema_store->ComputeChecksum());

  // Destroy the previous instance and recreate SchemaStore
  schema_store.reset();

  ICING_ASSERT_OK_AND_ASSIGN(
      schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
  EXPECT_THAT(schema_store->ComputeChecksum(), IsOkAndHolds(checksum));
}

TEST_F(SchemaStoreTest, ComputeChecksumChangesOnModification) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  SchemaProto foo_schema =
      SchemaBuilder().AddType(SchemaTypeConfigBuilder().SetType("foo")).Build();

  ICING_EXPECT_OK(schema_store->SetSchema(
      foo_schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  ICING_ASSERT_OK_AND_ASSIGN(Crc32 checksum, schema_store->ComputeChecksum());

  // Modifying the SchemaStore changes the checksum
  SchemaProto foo_bar_schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("foo"))
          .AddType(SchemaTypeConfigBuilder().SetType("bar"))
          .Build();

  ICING_EXPECT_OK(schema_store->SetSchema(
      foo_bar_schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  EXPECT_THAT(schema_store->ComputeChecksum(), IsOkAndHolds(Not(Eq(checksum))));
}

TEST_F(SchemaStoreTest, PersistToDiskFineForEmptySchemaStore) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // Persisting is fine and shouldn't affect anything
  ICING_EXPECT_OK(schema_store->PersistToDisk());
}

TEST_F(SchemaStoreTest, PersistToDiskPreservesAcrossInstances) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  SchemaProto schema =
      SchemaBuilder().AddType(SchemaTypeConfigBuilder().SetType("foo")).Build();

  ICING_EXPECT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Persisting shouldn't change anything
  ICING_EXPECT_OK(schema_store->PersistToDisk());

  ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* actual_schema,
                             schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema));

  // Modify the schema so that something different is persisted next time
  schema = SchemaBuilder(schema)
               .AddType(SchemaTypeConfigBuilder().SetType("bar"))
               .Build();
  ICING_EXPECT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Should also persist on destruction
  schema_store.reset();

  // And we get the same schema back on reinitialization
  ICING_ASSERT_OK_AND_ASSIGN(
      schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
  ICING_ASSERT_OK_AND_ASSIGN(actual_schema, schema_store->GetSchema());
  EXPECT_THAT(*actual_schema, EqualsProto(schema));
}

TEST_F(SchemaStoreTest, SchemaStoreStorageInfoProto) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // Create a schema with two types: one simple type and one type that uses all
  // 64 sections.
  PropertyConfigProto prop =
      PropertyConfigBuilder()
          .SetName("subject")
          .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
          .SetCardinality(CARDINALITY_OPTIONAL)
          .Build();
  SchemaTypeConfigBuilder full_sections_type_builder =
      SchemaTypeConfigBuilder().SetType("fullSectionsType");
  for (int i = 0; i < 64; ++i) {
    full_sections_type_builder.AddProperty(
        PropertyConfigBuilder(prop).SetName("prop" + std::to_string(i)));
  }
  SchemaProto schema =
      SchemaBuilder()
          .AddType(SchemaTypeConfigBuilder().SetType("email").AddProperty(
              PropertyConfigBuilder(prop)))
          .AddType(full_sections_type_builder)
          .Build();

  SchemaStore::SetSchemaResult result;
  result.success = true;
  result.schema_types_new_by_name.insert("email");
  result.schema_types_new_by_name.insert("fullSectionsType");
  EXPECT_THAT(schema_store->SetSchema(
                  schema, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));

  SchemaStoreStorageInfoProto storage_info = schema_store->GetStorageInfo();
  EXPECT_THAT(storage_info.schema_store_size(), Ge(0));
  EXPECT_THAT(storage_info.num_schema_types(), Eq(2));
  EXPECT_THAT(storage_info.num_total_sections(), Eq(65));
  EXPECT_THAT(storage_info.num_schema_types_sections_exhausted(), Eq(1));
}

TEST_F(SchemaStoreTest, GetDebugInfo) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // Set schema
  ASSERT_THAT(
      schema_store->SetSchema(schema_,
                              /*ignore_errors_and_delete_documents=*/false,
                              /*allow_circular_schema_definitions=*/false),
      IsOkAndHolds(EqualsSetSchemaResult(SchemaStore::SetSchemaResult{
          .success = true,
          .schema_types_new_by_name = {schema_.types(0).schema_type()}})));

  // Check debug info
  ICING_ASSERT_OK_AND_ASSIGN(SchemaDebugInfoProto out,
                             schema_store->GetDebugInfo());
  EXPECT_THAT(out.schema(), EqualsProto(schema_));
  EXPECT_THAT(out.crc(), Gt(0));
}

TEST_F(SchemaStoreTest, GetDebugInfoForEmptySchemaStore) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // Check debug info before setting a schema
  ICING_ASSERT_OK_AND_ASSIGN(SchemaDebugInfoProto out,
                             schema_store->GetDebugInfo());
  SchemaDebugInfoProto expected_out;
  expected_out.set_crc(0);
  EXPECT_THAT(out, EqualsProto(expected_out));
}

TEST_F(SchemaStoreTest, InitializeRegenerateDerivedFilesFailure) {
  // This test covers the first point that RegenerateDerivedFiles could fail.
  // This should simply result in SetSchema::Create returning an INTERNAL error.

  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    SchemaProto schema = SchemaBuilder()
                             .AddType(SchemaTypeConfigBuilder().SetType("Type"))
                             .Build();
    ICING_ASSERT_OK(schema_store->SetSchema(
        std::move(schema), /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));
  }

  auto mock_filesystem = std::make_unique<MockFilesystem>();
  ON_CALL(*mock_filesystem,
          CreateDirectoryRecursively(HasSubstr("key_mapper_dir")))
      .WillByDefault(Return(false));
  {
    EXPECT_THAT(SchemaStore::Create(mock_filesystem.get(), schema_store_dir_,
                                    &fake_clock_),
                StatusIs(libtextclassifier3::StatusCode::INTERNAL));
  }
}

TEST_F(SchemaStoreTest, SetSchemaRegenerateDerivedFilesFailure) {
  // This test covers the second point that RegenerateDerivedFiles could fail.
  // If handled correctly, the schema store and section manager should still be
  // in the original, valid state.
  SchemaTypeConfigProto type =
      SchemaTypeConfigBuilder()
          .SetType("Type")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("intProp1")
                           .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("stringProp1")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  {
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    SchemaProto schema = SchemaBuilder().AddType(type).Build();
    ICING_ASSERT_OK(schema_store->SetSchema(
        std::move(schema), /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));
  }

  {
    auto mock_filesystem = std::make_unique<MockFilesystem>();
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(mock_filesystem.get(), schema_store_dir_,
                            &fake_clock_));

    ON_CALL(*mock_filesystem,
            CreateDirectoryRecursively(HasSubstr("key_mapper_dir")))
        .WillByDefault(Return(false));
    SchemaProto schema =
        SchemaBuilder()
            .AddType(type)
            .AddType(SchemaTypeConfigBuilder().SetType("Type2"))
            .Build();
    EXPECT_THAT(
        schema_store->SetSchema(std::move(schema),
                                /*ignore_errors_and_delete_documents=*/false,
                                /*allow_circular_schema_definitions=*/false),
        StatusIs(libtextclassifier3::StatusCode::INTERNAL));
    DocumentProto document =
        DocumentBuilder()
            .SetSchema("Type")
            .AddInt64Property("intProp1", 1, 2, 3)
            .AddStringProperty("stringProp1", "foo bar baz")
            .Build();
    SectionMetadata expected_int_prop1_metadata(
        /*id_in=*/0, TYPE_INT64, TOKENIZER_NONE, TERM_MATCH_UNKNOWN,
        NUMERIC_MATCH_RANGE, "intProp1");
    SectionMetadata expected_string_prop1_metadata(
        /*id_in=*/1, TYPE_STRING, TOKENIZER_PLAIN, TERM_MATCH_EXACT,
        NUMERIC_MATCH_UNKNOWN, "stringProp1");
    ICING_ASSERT_OK_AND_ASSIGN(SectionGroup section_group,
                               schema_store->ExtractSections(document));
    ASSERT_THAT(section_group.string_sections, SizeIs(1));
    EXPECT_THAT(section_group.string_sections.at(0).metadata,
                Eq(expected_string_prop1_metadata));
    EXPECT_THAT(section_group.string_sections.at(0).content,
                ElementsAre("foo bar baz"));
    ASSERT_THAT(section_group.integer_sections, SizeIs(1));
    EXPECT_THAT(section_group.integer_sections.at(0).metadata,
                Eq(expected_int_prop1_metadata));
    EXPECT_THAT(section_group.integer_sections.at(0).content,
                ElementsAre(1, 2, 3));
  }
}

TEST_F(SchemaStoreTest, CanCheckForPropertiesDefinedInSchema) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // Set it for the first time
  SchemaStore::SetSchemaResult result;
  result.success = true;
  result.schema_types_new_by_name.insert(schema_.types(0).schema_type());

  // Don't use schema_ defined in the test suite, as we want to make sure that
  // the test is written correctly without referring to what the suite has
  // defined.
  SchemaProto schema =
      SchemaBuilder()
          .AddType(
              SchemaTypeConfigBuilder()
                  .SetType("email")
                  .AddProperty(
                      // Add an indexed property so we generate
                      // section metadata on it
                      PropertyConfigBuilder()
                          .SetName("subject")
                          .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                          .SetCardinality(CARDINALITY_OPTIONAL))
                  .AddProperty(PropertyConfigBuilder()
                                   .SetName("timestamp")
                                   .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                                   .SetCardinality(CARDINALITY_OPTIONAL)))
          .Build();

  EXPECT_THAT(schema_store->SetSchema(
                  schema, /*ignore_errors_and_delete_documents=*/false,
                  /*allow_circular_schema_definitions=*/false),
              IsOkAndHolds(EqualsSetSchemaResult(result)));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId schema_id,
                             schema_store->GetSchemaTypeId("email"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(schema_id, "subject"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(schema_id, "timestamp"));
  EXPECT_FALSE(schema_store->IsPropertyDefinedInSchema(schema_id, "foobar"));
}

TEST_F(SchemaStoreTest, GetSchemaTypeIdsWithChildren) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // Create a schema with the following inheritance relation:
  //       A
  //     /   \
  //    B     E
  //   /  \
  //  C    D
  //       |
  //       F
  SchemaTypeConfigProto type_a = SchemaTypeConfigBuilder().SetType("A").Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder().SetType("B").AddParentType("A").Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder().SetType("C").AddParentType("B").Build();
  SchemaTypeConfigProto type_d =
      SchemaTypeConfigBuilder().SetType("D").AddParentType("B").Build();
  SchemaTypeConfigProto type_e =
      SchemaTypeConfigBuilder().SetType("E").AddParentType("A").Build();
  SchemaTypeConfigProto type_f =
      SchemaTypeConfigBuilder().SetType("F").AddParentType("D").Build();
  SchemaProto schema = SchemaBuilder()
                           .AddType(type_a)
                           .AddType(type_b)
                           .AddType(type_c)
                           .AddType(type_d)
                           .AddType(type_e)
                           .AddType(type_f)
                           .Build();
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Get schema type id for each type.
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId type_a_id,
                             schema_store->GetSchemaTypeId("A"));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId type_b_id,
                             schema_store->GetSchemaTypeId("B"));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId type_c_id,
                             schema_store->GetSchemaTypeId("C"));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId type_d_id,
                             schema_store->GetSchemaTypeId("D"));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId type_e_id,
                             schema_store->GetSchemaTypeId("E"));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId type_f_id,
                             schema_store->GetSchemaTypeId("F"));

  // Check the results from GetSchemaTypeIdsWithChildren
  EXPECT_THAT(
      schema_store->GetSchemaTypeIdsWithChildren("A"),
      IsOkAndHolds(Pointee(UnorderedElementsAre(
          type_a_id, type_b_id, type_c_id, type_d_id, type_e_id, type_f_id))));
  EXPECT_THAT(schema_store->GetSchemaTypeIdsWithChildren("B"),
              IsOkAndHolds(Pointee(UnorderedElementsAre(
                  type_b_id, type_c_id, type_d_id, type_f_id))));
  EXPECT_THAT(schema_store->GetSchemaTypeIdsWithChildren("C"),
              IsOkAndHolds(Pointee(UnorderedElementsAre(type_c_id))));
  EXPECT_THAT(
      schema_store->GetSchemaTypeIdsWithChildren("D"),
      IsOkAndHolds(Pointee(UnorderedElementsAre(type_d_id, type_f_id))));
  EXPECT_THAT(schema_store->GetSchemaTypeIdsWithChildren("E"),
              IsOkAndHolds(Pointee(UnorderedElementsAre(type_e_id))));
  EXPECT_THAT(schema_store->GetSchemaTypeIdsWithChildren("F"),
              IsOkAndHolds(Pointee(UnorderedElementsAre(type_f_id))));
}

TEST_F(SchemaStoreTest, DiamondGetSchemaTypeIdsWithChildren) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

  // Create a schema with the following inheritance relation:
  //       A
  //     /   \
  //    B     E
  //   /  \  /
  //  C    D
  //   \  /
  //     F
  SchemaTypeConfigProto type_a = SchemaTypeConfigBuilder().SetType("A").Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder().SetType("B").AddParentType("A").Build();
  SchemaTypeConfigProto type_c =
      SchemaTypeConfigBuilder().SetType("C").AddParentType("B").Build();
  SchemaTypeConfigProto type_d = SchemaTypeConfigBuilder()
                                     .SetType("D")
                                     .AddParentType("B")
                                     .AddParentType("E")
                                     .Build();
  SchemaTypeConfigProto type_e =
      SchemaTypeConfigBuilder().SetType("E").AddParentType("A").Build();
  SchemaTypeConfigProto type_f = SchemaTypeConfigBuilder()
                                     .SetType("F")
                                     .AddParentType("C")
                                     .AddParentType("D")
                                     .Build();
  SchemaProto schema = SchemaBuilder()
                           .AddType(type_a)
                           .AddType(type_b)
                           .AddType(type_c)
                           .AddType(type_d)
                           .AddType(type_e)
                           .AddType(type_f)
                           .Build();
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/false));

  // Get schema type id for each type.
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId type_a_id,
                             schema_store->GetSchemaTypeId("A"));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId type_b_id,
                             schema_store->GetSchemaTypeId("B"));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId type_c_id,
                             schema_store->GetSchemaTypeId("C"));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId type_d_id,
                             schema_store->GetSchemaTypeId("D"));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId type_e_id,
                             schema_store->GetSchemaTypeId("E"));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId type_f_id,
                             schema_store->GetSchemaTypeId("F"));

  // Check the results from GetSchemaTypeIdsWithChildren
  EXPECT_THAT(
      schema_store->GetSchemaTypeIdsWithChildren("A"),
      IsOkAndHolds(Pointee(UnorderedElementsAre(
          type_a_id, type_b_id, type_c_id, type_d_id, type_e_id, type_f_id))));
  EXPECT_THAT(schema_store->GetSchemaTypeIdsWithChildren("B"),
              IsOkAndHolds(Pointee(UnorderedElementsAre(
                  type_b_id, type_c_id, type_d_id, type_f_id))));
  EXPECT_THAT(
      schema_store->GetSchemaTypeIdsWithChildren("C"),
      IsOkAndHolds(Pointee(UnorderedElementsAre(type_c_id, type_f_id))));
  EXPECT_THAT(
      schema_store->GetSchemaTypeIdsWithChildren("D"),
      IsOkAndHolds(Pointee(UnorderedElementsAre(type_d_id, type_f_id))));
  EXPECT_THAT(schema_store->GetSchemaTypeIdsWithChildren("E"),
              IsOkAndHolds(Pointee(
                  UnorderedElementsAre(type_e_id, type_d_id, type_f_id))));
  EXPECT_THAT(schema_store->GetSchemaTypeIdsWithChildren("F"),
              IsOkAndHolds(Pointee(UnorderedElementsAre(type_f_id))));
}

TEST_F(SchemaStoreTest, IndexableFieldsAreDefined) {
  SchemaTypeConfigProto email_type =
      SchemaTypeConfigBuilder()
          .SetType("Email")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("subject")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("senderQualifiedId")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                               /*propagate_delete=*/true)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("recipients")
                           .SetDataTypeString(TERM_MATCH_EXACT, TOKENIZER_PLAIN)
                           .SetCardinality(CARDINALITY_REPEATED))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("recipientIds")
                           .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                           .SetCardinality(CARDINALITY_REPEATED))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("timestamp")
                           .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .Build();

  SchemaProto schema = SchemaBuilder().AddType(email_type).Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/true));
  constexpr SchemaTypeId kTypeEmailSchemaId = 0;

  // Indexables.
  EXPECT_TRUE(
      schema_store->IsPropertyDefinedInSchema(kTypeEmailSchemaId, "subject"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeEmailSchemaId,
                                                      "senderQualifiedId"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeEmailSchemaId,
                                                      "recipients"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeEmailSchemaId,
                                                      "recipientIds"));
  EXPECT_TRUE(
      schema_store->IsPropertyDefinedInSchema(kTypeEmailSchemaId, "timestamp"));
}

TEST_F(SchemaStoreTest, JoinableFieldsAreDefined) {
  SchemaTypeConfigProto email_type =
      SchemaTypeConfigBuilder()
          .SetType("Email")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("tagQualifiedId")
                           .SetDataType(TYPE_STRING)
                           .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                        /*propagate_delete=*/true)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("senderQualifiedId")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                               /*propagate_delete=*/true)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .Build();

  SchemaProto schema = SchemaBuilder().AddType(email_type).Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/true));
  constexpr SchemaTypeId kTypeEmailSchemaId = 0;

  // Joinables.
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeEmailSchemaId,
                                                      "tagQualifiedId"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeEmailSchemaId,
                                                      "senderQualifiedId"));
}

TEST_F(SchemaStoreTest, NonIndexableFieldsAreDefined) {
  SchemaTypeConfigProto email_type =
      SchemaTypeConfigBuilder()
          .SetType("Email")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("attachment")
                           .SetDataType(TYPE_BYTES)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("nonindexableInteger")
                           .SetDataType(TYPE_INT64)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .Build();

  SchemaProto schema = SchemaBuilder().AddType(email_type).Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/true));
  constexpr SchemaTypeId kTypeEmailSchemaId = 0;

  // Non-indexables.
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeEmailSchemaId,
                                                      "attachment"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeEmailSchemaId,
                                                      "nonindexableInteger"));
  EXPECT_TRUE(
      schema_store->IsPropertyDefinedInSchema(kTypeEmailSchemaId, "text"));
}

TEST_F(SchemaStoreTest, NonExistentFieldsAreUndefined) {
  SchemaTypeConfigProto email_type =
      SchemaTypeConfigBuilder()
          .SetType("Email")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("subject")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("senderQualifiedId")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                               /*propagate_delete=*/true)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("timestamp")
                           .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("nonindexableInteger")
                           .SetDataType(TYPE_INT64)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .Build();

  SchemaProto schema = SchemaBuilder().AddType(email_type).Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/true));
  constexpr SchemaTypeId kTypeEmailSchemaId = 0;

  // Non-existents.
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeEmailSchemaId, "foobar"));
  EXPECT_FALSE(schema_store->IsPropertyDefinedInSchema(kTypeEmailSchemaId,
                                                       "timestamp.foo"));
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeEmailSchemaId, "time"));
}

TEST_F(SchemaStoreTest, NestedIndexableFieldsAreDefined) {
  SchemaTypeConfigProto email_type =
      SchemaTypeConfigBuilder()
          .SetType("Email")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("tagQualifiedId")
                           .SetDataType(TYPE_STRING)
                           .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                        /*propagate_delete=*/true)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("subject")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("timestamp")
                           .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .Build();

  SchemaTypeConfigProto conversation_type =
      SchemaTypeConfigBuilder()
          .SetType("Conversation")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("emails")
                           .SetDataTypeDocument(
                               "Email", /*index_nested_properties=*/true)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("nestedNonIndexable")
                  .SetDataTypeDocument("Email",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto schema =
      SchemaBuilder().AddType(email_type).AddType(conversation_type).Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/true));
  constexpr SchemaTypeId kTypeConversationSchemaId = 1;

  // Indexables.
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeConversationSchemaId,
                                                      "emails.subject"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeConversationSchemaId,
                                                      "emails.timestamp"));
}

TEST_F(SchemaStoreTest, NestedJoinableFieldsAreDefined) {
  SchemaTypeConfigProto email_type =
      SchemaTypeConfigBuilder()
          .SetType("Email")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("tagQualifiedId")
                           .SetDataType(TYPE_STRING)
                           .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                        /*propagate_delete=*/true)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("subject")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("timestamp")
                           .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .Build();

  SchemaTypeConfigProto conversation_type =
      SchemaTypeConfigBuilder()
          .SetType("Conversation")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("emails")
                           .SetDataTypeDocument(
                               "Email", /*index_nested_properties=*/true)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("nestedNonIndexable")
                  .SetDataTypeDocument("Email",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto schema =
      SchemaBuilder().AddType(email_type).AddType(conversation_type).Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/true));
  constexpr SchemaTypeId kTypeConversationSchemaId = 1;

  // Joinables.
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeConversationSchemaId,
                                                      "emails.tagQualifiedId"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(
      kTypeConversationSchemaId, "nestedNonIndexable.tagQualifiedId"));
}

TEST_F(SchemaStoreTest, NestedNonIndexableFieldsAreDefined) {
  SchemaTypeConfigProto email_type =
      SchemaTypeConfigBuilder()
          .SetType("Email")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("tagQualifiedId")
                           .SetDataType(TYPE_STRING)
                           .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                        /*propagate_delete=*/true)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("subject")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("timestamp")
                           .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .Build();

  SchemaTypeConfigProto conversation_type =
      SchemaTypeConfigBuilder()
          .SetType("Conversation")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("emails")
                           .SetDataTypeDocument(
                               "Email", /*index_nested_properties=*/true)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("nestedNonIndexable")
                  .SetDataTypeDocument("Email",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto schema =
      SchemaBuilder().AddType(email_type).AddType(conversation_type).Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/true));
  constexpr SchemaTypeId kTypeConversationSchemaId = 1;

  // Non-indexables.
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeConversationSchemaId,
                                                      "emails.text"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(
      kTypeConversationSchemaId, "nestedNonIndexable.subject"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(
      kTypeConversationSchemaId, "nestedNonIndexable.text"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(
      kTypeConversationSchemaId, "nestedNonIndexable.timestamp"));
}

TEST_F(SchemaStoreTest, NestedNonExistentFieldsAreUndefined) {
  SchemaTypeConfigProto email_type =
      SchemaTypeConfigBuilder()
          .SetType("Email")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("tagQualifiedId")
                           .SetDataType(TYPE_STRING)
                           .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                        /*propagate_delete=*/true)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("subject")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("timestamp")
                           .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .Build();

  SchemaTypeConfigProto conversation_type =
      SchemaTypeConfigBuilder()
          .SetType("Conversation")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("emails")
                           .SetDataTypeDocument(
                               "Email", /*index_nested_properties=*/true)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("nestedNonIndexable")
                  .SetDataTypeDocument("Email",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto schema =
      SchemaBuilder().AddType(email_type).AddType(conversation_type).Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/true));
  constexpr SchemaTypeId kTypeConversationSchemaId = 1;

  // Non-existents.
  EXPECT_FALSE(schema_store->IsPropertyDefinedInSchema(
      kTypeConversationSchemaId, "emails.foobar"));
  EXPECT_FALSE(schema_store->IsPropertyDefinedInSchema(
      kTypeConversationSchemaId, "nestedNonIndexable.foobar"));
  EXPECT_FALSE(schema_store->IsPropertyDefinedInSchema(
      kTypeConversationSchemaId, "emails.timestamp.foo"));
  EXPECT_FALSE(schema_store->IsPropertyDefinedInSchema(
      kTypeConversationSchemaId, "emails.time"));
}

TEST_F(SchemaStoreTest, IntermediateDocumentPropertiesAreDefined) {
  SchemaTypeConfigProto email_type =
      SchemaTypeConfigBuilder()
          .SetType("Email")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("tagQualifiedId")
                           .SetDataType(TYPE_STRING)
                           .SetJoinable(JOINABLE_VALUE_TYPE_QUALIFIED_ID,
                                        /*propagate_delete=*/true)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("subject")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("text")
                  .SetDataTypeString(TERM_MATCH_UNKNOWN, TOKENIZER_NONE)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(PropertyConfigBuilder()
                           .SetName("timestamp")
                           .SetDataTypeInt64(NUMERIC_MATCH_RANGE)
                           .SetCardinality(CARDINALITY_REQUIRED))
          .Build();

  SchemaTypeConfigProto conversation_type =
      SchemaTypeConfigBuilder()
          .SetType("Conversation")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("emails")
                           .SetDataTypeDocument(
                               "Email", /*index_nested_properties=*/true)
                           .SetCardinality(CARDINALITY_OPTIONAL))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("nestedNonIndexable")
                  .SetDataTypeDocument("Email",
                                       /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto schema =
      SchemaBuilder().AddType(email_type).AddType(conversation_type).Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/true));
  constexpr SchemaTypeId kTypeConversationSchemaId = 1;

  // Intermediate documents props.
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeConversationSchemaId,
                                                      "emails"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeConversationSchemaId,
                                                      "nestedNonIndexable"));
}

TEST_F(SchemaStoreTest, CyclePathsAreDefined) {
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("subject")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();

  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetDataTypeDocument("A", /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/true));
  constexpr SchemaTypeId kTypeASchemaId = 0;
  constexpr SchemaTypeId kTypeBSchemaId = 1;

  // A's top-level properties
  EXPECT_TRUE(
      schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "subject"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "b"));

  // A's nested properties in B
  EXPECT_TRUE(
      schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "b.body"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "b.a"));

  // A's nested properties in B's nested property in A
  EXPECT_TRUE(
      schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "b.a.subject"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "b.a.b"));

  // B's top-level properties
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "body"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "a"));

  // B's nested properties in A
  EXPECT_TRUE(
      schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "a.subject"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "a.b"));

  // B's nested properties in A's nested property in B
  EXPECT_TRUE(
      schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "a.b.body"));
  EXPECT_TRUE(schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "a.b.a"));
}

TEST_F(SchemaStoreTest, WrongTypeCyclePathsAreUndefined) {
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("subject")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();

  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetDataTypeDocument("A", /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/true));
  constexpr SchemaTypeId kTypeASchemaId = 0;
  constexpr SchemaTypeId kTypeBSchemaId = 1;

  // The same paths as above, but we check the wrong types instead.
  // A's top-level properties
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "subject"));
  EXPECT_FALSE(schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "b"));

  // A's nested properties in B
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "b.body"));
  EXPECT_FALSE(schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "b.a"));

  // A's nested properties in B's nested property in A
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "b.a.subject"));
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "b.a.b"));

  // B's top-level properties
  EXPECT_FALSE(schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "body"));
  EXPECT_FALSE(schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "a"));

  // B's nested properties in A
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "a.subject"));
  EXPECT_FALSE(schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "a.b"));

  // B's nested properties in A's nested property in B
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "a.b.body"));
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "a.b.a"));
}

TEST_F(SchemaStoreTest, CyclePathsNonexistentPropertiesAreUndefined) {
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("A")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("subject")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("b")
                  .SetDataTypeDocument("B", /*index_nested_properties=*/true)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();

  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("B")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("body")
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN)
                  .SetCardinality(CARDINALITY_REQUIRED))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("a")
                  .SetDataTypeDocument("A", /*index_nested_properties=*/false)
                  .SetCardinality(CARDINALITY_OPTIONAL))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SchemaStore> schema_store,
      SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
  ICING_ASSERT_OK(schema_store->SetSchema(
      schema, /*ignore_errors_and_delete_documents=*/false,
      /*allow_circular_schema_definitions=*/true));
  constexpr SchemaTypeId kTypeASchemaId = 0;
  constexpr SchemaTypeId kTypeBSchemaId = 1;

  // Undefined paths in A
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "b.subject"));
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "b.a.body"));
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "b.a.a"));
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeASchemaId, "b.a.subject.b"));

  // Undefined paths in B
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "a.body"));
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "a.b.subject"));
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "a.b.b"));
  EXPECT_FALSE(
      schema_store->IsPropertyDefinedInSchema(kTypeBSchemaId, "a.b.body.a"));
}

TEST_F(SchemaStoreTest, LoadsOverlaySchemaOnInit) {
  // Create a schema that is rollback incompatible and will trigger us to create
  // an overlay schema.
  PropertyConfigBuilder indexed_string_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN);
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_RFC822))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("type_b")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();

  {
    // Create an instance of the schema store and set the schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  {
    // Create a new of the schema store and check that the same schema is
    // present.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));

    // The overlay should exist
    std::string overlay_schema_path = schema_store_dir_ + "/overlay_schema.pb";
    ASSERT_TRUE(filesystem_.FileExists(overlay_schema_path.c_str()));

    // The base schema should hold a compatible schema
    SchemaTypeConfigProto modified_type_a =
        SchemaTypeConfigBuilder()
            .SetType("type_a")
            .AddProperty(indexed_string_property_builder.SetName("prop0"))
            .AddProperty(PropertyConfigBuilder()
                             .SetName("propRfc")
                             .SetCardinality(CARDINALITY_OPTIONAL)
                             .SetDataType(TYPE_STRING))
            .Build();
    SchemaProto expected_base_schema =
        SchemaBuilder().AddType(modified_type_a).AddType(type_b).Build();
    std::string base_schema_path = schema_store_dir_ + "/schema.pb";
    auto base_schema_file_ = std::make_unique<FileBackedProto<SchemaProto>>(
        filesystem_, base_schema_path);
    ICING_ASSERT_OK_AND_ASSIGN(const SchemaProto* base_schema,
                               base_schema_file_->Read());
    EXPECT_THAT(*base_schema, EqualsProto(expected_base_schema));
  }
}

TEST_F(SchemaStoreTest, LoadsBaseSchemaWithNoOverlayOnInit) {
  // Create a normal schema that won't require an overlay.
  PropertyConfigBuilder indexed_string_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN);
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("type_b")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();

  {
    // Create an instance of the schema store and set the schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  {
    // Create a new instance of the schema store and check that the same schema
    // is present.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));

    // Additionally, the overlay should not exist
    std::string overlay_schema_path = schema_store_dir_ + "/overlay_schema.pb";
    ASSERT_FALSE(filesystem_.FileExists(overlay_schema_path.c_str()));
  }
}

TEST_F(SchemaStoreTest, LoadSchemaBackupSchemaMissing) {
  // Create a schema that is rollback incompatible and will trigger us to create
  // a backup schema.
  PropertyConfigBuilder indexed_string_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN);
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_RFC822))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("type_b")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();

  {
    // Create an instance of the schema store and set the schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  // Delete the backup schema.
  std::string backup_schema_path = schema_store_dir_ + "/schema.pb";
  ASSERT_TRUE(filesystem_.DeleteFile(backup_schema_path.c_str()));

  {
    // Create a new instance of the schema store and check that it fails because
    // the backup schema is not available.
    EXPECT_THAT(
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_),
        StatusIs(libtextclassifier3::StatusCode::INTERNAL));
  }
}

TEST_F(SchemaStoreTest, LoadSchemaOverlaySchemaMissing) {
  // Create a schema that is rollback incompatible and will trigger us to create
  // a backup schema.
  PropertyConfigBuilder indexed_string_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN);
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_RFC822))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("type_b")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();

  {
    // Create an instance of the schema store and set the schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  // Delete the overlay schema.
  std::string overlay_schema_path = schema_store_dir_ + "/overlay_schema.pb";
  ASSERT_TRUE(filesystem_.DeleteFile(overlay_schema_path.c_str()));

  {
    // Create a new instance of the schema store and check that it fails because
    // the overlay schema is not available when we expected it to be.
    EXPECT_THAT(
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_),
        StatusIs(libtextclassifier3::StatusCode::INTERNAL));
  }
}

TEST_F(SchemaStoreTest, LoadSchemaHeaderMissing) {
  // Create a schema that is rollback incompatible and will trigger us to create
  // a backup schema.
  PropertyConfigBuilder indexed_string_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN);
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_RFC822))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("type_b")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();

  {
    // Create an instance of the schema store and set the schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  // Delete the overlay schema.
  std::string schema_header_path = schema_store_dir_ + "/schema_store_header";
  ASSERT_TRUE(filesystem_.DeleteFile(schema_header_path.c_str()));

  {
    // Create a new of the schema store and check that the same schema is
    // present.
    EXPECT_THAT(
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_),
        StatusIs(libtextclassifier3::StatusCode::INTERNAL));
  }
}

TEST_F(SchemaStoreTest, LoadSchemaNoOverlayHeaderMissing) {
  // Create a normal schema that won't require a backup.
  PropertyConfigBuilder indexed_string_property_builder =
      PropertyConfigBuilder()
          .SetCardinality(CARDINALITY_OPTIONAL)
          .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN);
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN))
          .Build();
  SchemaTypeConfigProto type_b =
      SchemaTypeConfigBuilder()
          .SetType("type_b")
          .AddProperty(indexed_string_property_builder.SetName("prop0"))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).AddType(type_b).Build();

  {
    // Create an instance of the schema store and set the schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  // Delete the schema header.
  std::string schema_header_path = schema_store_dir_ + "/schema_store_header";
  ASSERT_TRUE(filesystem_.DeleteFile(schema_header_path.c_str()));

  {
    // Create a new instance of the schema store and check that it fails because
    // the schema header (which is now a part of the ground truth) is not
    // available.
    EXPECT_THAT(
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_),
        StatusIs(libtextclassifier3::StatusCode::INTERNAL));
  }
}

TEST_F(SchemaStoreTest, MigrateSchemaCompatibleNoChange) {
  // Create a schema that is rollback incompatible and will trigger us to create
  // a backup schema.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_RFC822))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).Build();

  {
    // Create an instance of the schema store and set the schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  ICING_EXPECT_OK(SchemaStore::MigrateSchema(
      &filesystem_, schema_store_dir_, version_util::StateChange::kCompatible,
      version_util::kVersion));

  {
    // Create a new of the schema store and check that the same schema is
    // present.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }
}

TEST_F(SchemaStoreTest, MigrateSchemaUpgradeNoChange) {
  // Create a schema that is rollback incompatible and will trigger us to create
  // a backup schema.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_RFC822))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).Build();

  {
    // Create an instance of the schema store and set the schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  ICING_EXPECT_OK(SchemaStore::MigrateSchema(
      &filesystem_, schema_store_dir_, version_util::StateChange::kUpgrade,
      version_util::kVersion + 1));

  {
    // Create a new of the schema store and check that the same schema is
    // present.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }
}

TEST_F(SchemaStoreTest, MigrateSchemaVersionZeroUpgradeNoChange) {
  // Because we are upgrading from version zero, the schema must be compatible
  // with version zero.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_PLAIN))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).Build();

  {
    // Create an instance of the schema store and set the schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  ICING_EXPECT_OK(
      SchemaStore::MigrateSchema(&filesystem_, schema_store_dir_,
                                 version_util::StateChange::kVersionZeroUpgrade,
                                 version_util::kVersion + 1));

  {
    // Create a new of the schema store and check that the same schema is
    // present.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }
}

TEST_F(SchemaStoreTest,
       MigrateSchemaRollbackDiscardsIncompatibleOverlaySchema) {
  // Because we are upgrading from version zero, the schema must be compatible
  // with version zero.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_RFC822))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).Build();

  {
    // Create an instance of the schema store and set the schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  // Rollback to a version before kVersionOne. The schema header will declare
  // that the overlay is compatible with any version starting with kVersionOne.
  // So kVersionOne - 1 is incompatible and will throw out the schema.
  ICING_EXPECT_OK(SchemaStore::MigrateSchema(
      &filesystem_, schema_store_dir_, version_util::StateChange::kRollBack,
      version_util::kVersionOne - 1));

  {
    // Create a new of the schema store and check that we fell back to the
    // base schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

    SchemaTypeConfigProto other_type_a =
        SchemaTypeConfigBuilder()
            .SetType("type_a")
            .AddProperty(PropertyConfigBuilder()
                             .SetName("propRfc")
                             .SetCardinality(CARDINALITY_OPTIONAL)
                             .SetDataType(TYPE_STRING))
            .Build();
    SchemaProto base_schema = SchemaBuilder().AddType(other_type_a).Build();
    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(base_schema))));
  }
}

TEST_F(SchemaStoreTest, MigrateSchemaRollbackKeepsCompatibleOverlaySchema) {
  // Because we are upgrading from version zero, the schema must be compatible
  // with version zero.
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_RFC822))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).Build();

  {
    // Create an instance of the schema store and set the schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  // Rollback to kVersion. The schema header will declare that the overlay is
  // compatible with any version starting with kVersion. So we will be
  // compatible and retain the overlay schema.
  ICING_EXPECT_OK(SchemaStore::MigrateSchema(
      &filesystem_, schema_store_dir_, version_util::StateChange::kRollBack,
      version_util::kVersion));

  {
    // Create a new of the schema store and check that the same schema is
    // present.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }
}

TEST_F(SchemaStoreTest, MigrateSchemaRollforwardRetainsBaseSchema) {
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_RFC822))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).Build();
  {
    // Create an instance of the schema store and set the schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  // Rollback to a version before kVersionOne. The schema header will declare
  // that the overlay is compatible with any version starting with kVersionOne.
  // So kVersionOne - 1 is incompatible and will throw out the schema.
  ICING_EXPECT_OK(SchemaStore::MigrateSchema(
      &filesystem_, schema_store_dir_, version_util::StateChange::kRollBack,
      version_util::kVersionOne - 1));

  SchemaTypeConfigProto other_type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("propRfc")
                           .SetCardinality(CARDINALITY_OPTIONAL)
                           .SetDataType(TYPE_STRING))
          .Build();
  SchemaProto base_schema = SchemaBuilder().AddType(other_type_a).Build();

  {
    // Create a new of the schema store and check that we fell back to the
    // base schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(base_schema))));
  }

  // Now rollforward to a new version. This should accept whatever schema is
  // present (currently base schema)
  ICING_EXPECT_OK(SchemaStore::MigrateSchema(
      &filesystem_, schema_store_dir_, version_util::StateChange::kRollForward,
      version_util::kVersion));
  {
    // Create a new of the schema store and check that we fell back to the
    // base schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(base_schema))));
  }
}

TEST_F(SchemaStoreTest, MigrateSchemaRollforwardRetainsOverlaySchema) {
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_RFC822))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).Build();
  {
    // Create an instance of the schema store and set the schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  // Rollback to kVersion. The schema header will declare that the overlay is
  // compatible with any version starting with kVersion. So we will be
  // compatible and retain the overlay schema.
  ICING_EXPECT_OK(SchemaStore::MigrateSchema(
      &filesystem_, schema_store_dir_, version_util::StateChange::kRollBack,
      version_util::kVersion));

  {
    // Create a new of the schema store and check that the same schema is
    // present.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  // Now rollforward to a new version. This should accept whatever schema is
  // present (currently overlay schema)
  ICING_EXPECT_OK(SchemaStore::MigrateSchema(
      &filesystem_, schema_store_dir_, version_util::StateChange::kRollForward,
      version_util::kVersion));
  {
    // Create a new of the schema store and check that the same schema is
    // present.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }
}

TEST_F(SchemaStoreTest,
       MigrateSchemaVersionZeroRollforwardDiscardsOverlaySchema) {
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_RFC822))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).Build();
  {
    // Create an instance of the schema store and set the schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  // A VersionZeroRollforward will always discard the overlay schema because it
  // could be stale.
  ICING_EXPECT_OK(SchemaStore::MigrateSchema(
      &filesystem_, schema_store_dir_,
      version_util::StateChange::kVersionZeroRollForward,
      version_util::kVersion));

  SchemaTypeConfigProto other_type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("propRfc")
                           .SetCardinality(CARDINALITY_OPTIONAL)
                           .SetDataType(TYPE_STRING))
          .Build();
  SchemaProto base_schema = SchemaBuilder().AddType(other_type_a).Build();

  {
    // Create a new of the schema store and check that we fell back to the
    // base schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(base_schema))));
  }
}

TEST_F(SchemaStoreTest, MigrateSchemaVersionUndeterminedDiscardsOverlaySchema) {
  SchemaTypeConfigProto type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(
              PropertyConfigBuilder()
                  .SetName("propRfc")
                  .SetCardinality(CARDINALITY_OPTIONAL)
                  .SetDataTypeString(TERM_MATCH_PREFIX, TOKENIZER_RFC822))
          .Build();
  SchemaProto schema = SchemaBuilder().AddType(type_a).Build();
  {
    // Create an instance of the schema store and set the schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));
    ICING_ASSERT_OK(schema_store->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(schema))));
  }

  // An Undetermined will always discard the overlay schema because it doesn't
  // know which state we're in and so it fallback to the base schema because
  // it should always be valid.
  ICING_EXPECT_OK(SchemaStore::MigrateSchema(
      &filesystem_, schema_store_dir_, version_util::StateChange::kUndetermined,
      version_util::kVersion));

  SchemaTypeConfigProto other_type_a =
      SchemaTypeConfigBuilder()
          .SetType("type_a")
          .AddProperty(PropertyConfigBuilder()
                           .SetName("propRfc")
                           .SetCardinality(CARDINALITY_OPTIONAL)
                           .SetDataType(TYPE_STRING))
          .Build();
  SchemaProto base_schema = SchemaBuilder().AddType(other_type_a).Build();

  {
    // Create a new of the schema store and check that we fell back to the
    // base schema.
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<SchemaStore> schema_store,
        SchemaStore::Create(&filesystem_, schema_store_dir_, &fake_clock_));

    EXPECT_THAT(schema_store->GetSchema(),
                IsOkAndHolds(Pointee(EqualsProto(base_schema))));
  }
}

}  // namespace

}  // namespace lib
}  // namespace icing
