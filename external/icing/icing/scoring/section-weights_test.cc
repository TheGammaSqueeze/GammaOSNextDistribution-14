// Copyright (C) 2021 Google LLC
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

#include "icing/scoring/section-weights.h"

#include <cfloat>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/scoring.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/schema-builder.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/fake-clock.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {
using ::testing::Eq;

class SectionWeightsTest : public testing::Test {
 protected:
  SectionWeightsTest()
      : test_dir_(GetTestTempDir() + "/icing"),
        schema_store_dir_(test_dir_ + "/schema_store") {}

  void SetUp() override {
    // Creates file directories
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
    filesystem_.CreateDirectoryRecursively(schema_store_dir_.c_str());

    ICING_ASSERT_OK_AND_ASSIGN(
        schema_store_,
        SchemaStore::Create(&filesystem_, test_dir_, &fake_clock_));

    SchemaTypeConfigProto sender_schema =
        SchemaTypeConfigBuilder()
            .SetType("sender")
            .AddProperty(
                PropertyConfigBuilder()
                    .SetName("name")
                    .SetDataTypeString(
                        TermMatchType::PREFIX,
                        StringIndexingConfig::TokenizerType::PLAIN)
                    .SetCardinality(PropertyConfigProto::Cardinality::OPTIONAL))
            .Build();
    SchemaTypeConfigProto email_schema =
        SchemaTypeConfigBuilder()
            .SetType("email")
            .AddProperty(
                PropertyConfigBuilder()
                    .SetName("subject")
                    .SetDataTypeString(
                        TermMatchType::PREFIX,
                        StringIndexingConfig::TokenizerType::PLAIN)
                    .SetDataType(PropertyConfigProto::DataType::STRING)
                    .SetCardinality(PropertyConfigProto::Cardinality::OPTIONAL))
            .AddProperty(
                PropertyConfigBuilder()
                    .SetName("body")
                    .SetDataTypeString(
                        TermMatchType::PREFIX,
                        StringIndexingConfig::TokenizerType::PLAIN)
                    .SetDataType(PropertyConfigProto::DataType::STRING)
                    .SetCardinality(PropertyConfigProto::Cardinality::OPTIONAL))
            .AddProperty(
                PropertyConfigBuilder()
                    .SetName("sender")
                    .SetDataTypeDocument("sender",
                                         /*index_nested_properties=*/true)
                    .SetCardinality(PropertyConfigProto::Cardinality::OPTIONAL))
            .Build();
    SchemaProto schema =
        SchemaBuilder().AddType(sender_schema).AddType(email_schema).Build();

    ICING_ASSERT_OK(schema_store_->SetSchema(
        schema, /*ignore_errors_and_delete_documents=*/false,
        /*allow_circular_schema_definitions=*/false));
  }

  void TearDown() override {
    schema_store_.reset();
    filesystem_.DeleteDirectoryRecursively(test_dir_.c_str());
  }

  SchemaStore *schema_store() { return schema_store_.get(); }

 private:
  const std::string test_dir_;
  const std::string schema_store_dir_;
  Filesystem filesystem_;
  FakeClock fake_clock_;
  std::unique_ptr<SchemaStore> schema_store_;
};

TEST_F(SectionWeightsTest, ShouldNormalizeSinglePropertyWeight) {
  ScoringSpecProto spec_proto;

  TypePropertyWeights *type_property_weights =
      spec_proto.add_type_property_weights();
  type_property_weights->set_schema_type("sender");

  PropertyWeight *property_weight =
      type_property_weights->add_property_weights();
  property_weight->set_weight(5.0);
  property_weight->set_path("name");

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SectionWeights> section_weights,
      SectionWeights::Create(schema_store(), spec_proto));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId sender_schema_type_id,
                             schema_store()->GetSchemaTypeId("sender"));

  // section_id 0 corresponds to property "name".
  // We expect 1.0 as there is only one property in the "sender" schema type
  // so it should take the max normalized weight of 1.0.
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(sender_schema_type_id,
                                                          /*section_id=*/0),
              Eq(1.0));
}

TEST_F(SectionWeightsTest, ShouldAcceptMaxWeightValue) {
  ScoringSpecProto spec_proto;

  TypePropertyWeights *type_property_weights =
      spec_proto.add_type_property_weights();
  type_property_weights->set_schema_type("sender");

  PropertyWeight *property_weight =
      type_property_weights->add_property_weights();
  property_weight->set_weight(DBL_MAX);
  property_weight->set_path("name");

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SectionWeights> section_weights,
      SectionWeights::Create(schema_store(), spec_proto));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId sender_schema_type_id,
                             schema_store()->GetSchemaTypeId("sender"));

  // section_id 0 corresponds to property "name".
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(sender_schema_type_id,
                                                          /*section_id=*/0),
              Eq(1.0));
}

TEST_F(SectionWeightsTest, ShouldFailWithNegativeWeights) {
  ScoringSpecProto spec_proto;

  TypePropertyWeights *type_property_weights =
      spec_proto.add_type_property_weights();
  type_property_weights->set_schema_type("email");

  PropertyWeight *body_propery_weight =
      type_property_weights->add_property_weights();
  body_propery_weight->set_weight(-100.0);
  body_propery_weight->set_path("body");

  EXPECT_THAT(SectionWeights::Create(schema_store(), spec_proto).status(),
              StatusIs(libtextclassifier3::StatusCode::INVALID_ARGUMENT));
}

TEST_F(SectionWeightsTest, ShouldAcceptZeroWeight) {
  ScoringSpecProto spec_proto;

  TypePropertyWeights *type_property_weights =
      spec_proto.add_type_property_weights();
  type_property_weights->set_schema_type("email");

  PropertyWeight *body_property_weight =
      type_property_weights->add_property_weights();
  body_property_weight->set_weight(2.0);
  body_property_weight->set_path("body");

  PropertyWeight *subject_property_weight =
      type_property_weights->add_property_weights();
  subject_property_weight->set_weight(0.0);
  subject_property_weight->set_path("subject");

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SectionWeights> section_weights,
      SectionWeights::Create(schema_store(), spec_proto));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId email_schema_type_id,
                             schema_store()->GetSchemaTypeId("email"));

  // Normalized weight for "body" property.
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/0),
              Eq(1.0));
  // Normalized weight for "subject" property.
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/2),
              Eq(0.0));
}

TEST_F(SectionWeightsTest, ShouldNormalizeToZeroWhenAllWeightsZero) {
  ScoringSpecProto spec_proto;

  TypePropertyWeights *type_property_weights =
      spec_proto.add_type_property_weights();
  type_property_weights->set_schema_type("email");

  PropertyWeight *body_property_weight =
      type_property_weights->add_property_weights();
  body_property_weight->set_weight(0.0);
  body_property_weight->set_path("body");

  PropertyWeight *sender_property_weight =
      type_property_weights->add_property_weights();
  sender_property_weight->set_weight(0.0);
  sender_property_weight->set_path("sender.name");

  PropertyWeight *subject_property_weight =
      type_property_weights->add_property_weights();
  subject_property_weight->set_weight(0.0);
  subject_property_weight->set_path("subject");

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SectionWeights> section_weights,
      SectionWeights::Create(schema_store(), spec_proto));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId email_schema_type_id,
                             schema_store()->GetSchemaTypeId("email"));

  // Normalized weight for "body" property.
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/0),
              Eq(0.0));
  // Normalized weight for "sender.name" property (the nested property).
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/1),
              Eq(0.0));
  // Normalized weight for "subject" property.
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/2),
              Eq(0.0));
}

TEST_F(SectionWeightsTest, ShouldReturnDefaultIfTypePropertyWeightsNotSet) {
  ScoringSpecProto spec_proto;

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SectionWeights> section_weights,
      SectionWeights::Create(schema_store(), spec_proto));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId email_schema_type_id,
                             schema_store()->GetSchemaTypeId("email"));

  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/0),
              Eq(kDefaultSectionWeight));
}

TEST_F(SectionWeightsTest, ShouldSetNestedPropertyWeights) {
  ScoringSpecProto spec_proto;

  TypePropertyWeights *type_property_weights =
      spec_proto.add_type_property_weights();
  type_property_weights->set_schema_type("email");

  PropertyWeight *body_property_weight =
      type_property_weights->add_property_weights();
  body_property_weight->set_weight(1.0);
  body_property_weight->set_path("body");

  PropertyWeight *subject_property_weight =
      type_property_weights->add_property_weights();
  subject_property_weight->set_weight(100.0);
  subject_property_weight->set_path("subject");

  PropertyWeight *nested_property_weight =
      type_property_weights->add_property_weights();
  nested_property_weight->set_weight(50.0);
  nested_property_weight->set_path("sender.name");

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SectionWeights> section_weights,
      SectionWeights::Create(schema_store(), spec_proto));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId email_schema_type_id,
                             schema_store()->GetSchemaTypeId("email"));

  // Normalized weight for "body" property.
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/0),
              Eq(0.01));
  // Normalized weight for "sender.name" property (the nested property).
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/1),
              Eq(0.5));
  // Normalized weight for "subject" property.
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/2),
              Eq(1.0));
}

TEST_F(SectionWeightsTest, ShouldNormalizeIfAllWeightsBelowOne) {
  ScoringSpecProto spec_proto;

  TypePropertyWeights *type_property_weights =
      spec_proto.add_type_property_weights();
  type_property_weights->set_schema_type("email");

  PropertyWeight *body_property_weight =
      type_property_weights->add_property_weights();
  body_property_weight->set_weight(0.1);
  body_property_weight->set_path("body");

  PropertyWeight *sender_name_weight =
      type_property_weights->add_property_weights();
  sender_name_weight->set_weight(0.2);
  sender_name_weight->set_path("sender.name");

  PropertyWeight *subject_property_weight =
      type_property_weights->add_property_weights();
  subject_property_weight->set_weight(0.4);
  subject_property_weight->set_path("subject");

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SectionWeights> section_weights,
      SectionWeights::Create(schema_store(), spec_proto));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId email_schema_type_id,
                             schema_store()->GetSchemaTypeId("email"));

  // Normalized weight for "body" property.
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/0),
              Eq(1.0 / 4.0));
  // Normalized weight for "sender.name" property (the nested property).
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/1),
              Eq(2.0 / 4.0));
  // Normalized weight for "subject" property.
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/2),
              Eq(1.0));
}

TEST_F(SectionWeightsTest, ShouldSetNestedPropertyWeightSeparatelyForTypes) {
  ScoringSpecProto spec_proto;

  TypePropertyWeights *email_type_property_weights =
      spec_proto.add_type_property_weights();
  email_type_property_weights->set_schema_type("email");

  PropertyWeight *body_property_weight =
      email_type_property_weights->add_property_weights();
  body_property_weight->set_weight(1.0);
  body_property_weight->set_path("body");

  PropertyWeight *subject_property_weight =
      email_type_property_weights->add_property_weights();
  subject_property_weight->set_weight(100.0);
  subject_property_weight->set_path("subject");

  PropertyWeight *sender_name_property_weight =
      email_type_property_weights->add_property_weights();
  sender_name_property_weight->set_weight(50.0);
  sender_name_property_weight->set_path("sender.name");

  TypePropertyWeights *sender_type_property_weights =
      spec_proto.add_type_property_weights();
  sender_type_property_weights->set_schema_type("sender");

  PropertyWeight *sender_property_weight =
      sender_type_property_weights->add_property_weights();
  sender_property_weight->set_weight(25.0);
  sender_property_weight->set_path("sender");

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SectionWeights> section_weights,
      SectionWeights::Create(schema_store(), spec_proto));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId email_schema_type_id,
                             schema_store()->GetSchemaTypeId("email"));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId sender_schema_type_id,
                             schema_store()->GetSchemaTypeId("sender"));

  // Normalized weight for "sender.name" property (the nested property)
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/1),
              Eq(0.5));
  // Normalized weight for "name" property for "sender" schema type. As it is
  // the only property of the type, it should take the max normalized weight of
  // 1.0.
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(sender_schema_type_id,
                                                          /*section_id=*/2),
              Eq(1.0));
}

TEST_F(SectionWeightsTest, ShouldSkipNonExistentPathWhenSettingWeights) {
  ScoringSpecProto spec_proto;

  TypePropertyWeights *type_property_weights =
      spec_proto.add_type_property_weights();
  type_property_weights->set_schema_type("email");

  // If this property weight isn't skipped, then the max property weight would
  // be set to 100.0 and all weights would be normalized against the max.
  PropertyWeight *non_valid_property_weight =
      type_property_weights->add_property_weights();
  non_valid_property_weight->set_weight(100.0);
  non_valid_property_weight->set_path("sender.organization");

  PropertyWeight *subject_property_weight =
      type_property_weights->add_property_weights();
  subject_property_weight->set_weight(10.0);
  subject_property_weight->set_path("subject");

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<SectionWeights> section_weights,
      SectionWeights::Create(schema_store(), spec_proto));
  ICING_ASSERT_OK_AND_ASSIGN(SchemaTypeId email_schema_type_id,
                             schema_store()->GetSchemaTypeId("email"));

  // Normalized weight for "body" property. Because the weight is not explicitly
  // set, it is set to the default of 1.0 before being normalized.
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/0),
              Eq(0.1));
  // Normalized weight for "sender.name" property (the nested property). Because
  // the weight is not explicitly set, it is set to the default of 1.0 before
  // being normalized.
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/1),
              Eq(0.1));
  // Normalized weight for "subject" property. Because the invalid property path
  // is skipped when assigning weights, subject takes the max normalized weight
  // of 1.0 instead.
  EXPECT_THAT(section_weights->GetNormalizedSectionWeight(email_schema_type_id,
                                                          /*section_id=*/2),
              Eq(1.0));
}

}  // namespace

}  // namespace lib
}  // namespace icing
