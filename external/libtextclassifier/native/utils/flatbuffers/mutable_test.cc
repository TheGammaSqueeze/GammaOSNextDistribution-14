/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "utils/flatbuffers/mutable.h"

#include <map>
#include <memory>
#include <string>

#include "utils/flatbuffers/flatbuffers.h"
#include "utils/flatbuffers/flatbuffers_generated.h"
#include "utils/flatbuffers/flatbuffers_test_generated.h"
#include "utils/flatbuffers/test-utils.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "flatbuffers/flatbuffers.h"
#include "flatbuffers/reflection.h"
#include "flatbuffers/reflection_generated.h"

namespace libtextclassifier3 {
namespace {

using ::testing::ElementsAre;
using ::testing::SizeIs;

class MutableFlatbufferTest : public testing::Test {
 public:
  explicit MutableFlatbufferTest()
      : schema_(LoadTestMetadata()), builder_(schema_.get()) {}

 protected:
  OwnedFlatbuffer<reflection::Schema, std::string> schema_;
  MutableFlatbufferBuilder builder_;
};

TEST_F(MutableFlatbufferTest, PrimitiveFieldsAreCorrectlySet) {
  std::unique_ptr<MutableFlatbuffer> buffer = builder_.NewRoot();
  EXPECT_TRUE(buffer != nullptr);
  EXPECT_TRUE(buffer->Set("an_int_field", 42));
  EXPECT_TRUE(buffer->Set("a_long_field", int64{84}));
  EXPECT_TRUE(buffer->Set("a_bool_field", true));
  EXPECT_TRUE(buffer->Set("a_float_field", 1.f));
  EXPECT_TRUE(buffer->Set("a_double_field", 1.0));

  // Try to parse with the generated code.
  std::unique_ptr<test::EntityDataT> entity_data =
      LoadAndVerifyMutableFlatbuffer<test::EntityData>(buffer->Serialize());
  ASSERT_NE(entity_data, nullptr);
  EXPECT_EQ(entity_data->an_int_field, 42);
  EXPECT_EQ(entity_data->a_long_field, 84);
  EXPECT_EQ(entity_data->a_bool_field, true);
  EXPECT_NEAR(entity_data->a_float_field, 1.f, 1e-4);
  EXPECT_NEAR(entity_data->a_double_field, 1.f, 1e-4);
}

TEST_F(MutableFlatbufferTest, EnumValuesCanBeSpecifiedByName) {
  std::unique_ptr<MutableFlatbuffer> buffer = builder_.NewRoot();
  EXPECT_TRUE(buffer != nullptr);

  EXPECT_TRUE(IsEnum(buffer->GetFieldOrNull("enum_value")->type()));

  EXPECT_TRUE(buffer->SetFromEnumValueName("enum_value", "VALUE_1"));

  std::unique_ptr<test::EntityDataT> entity_data =
      LoadAndVerifyMutableFlatbuffer<test::EntityData>(buffer->Serialize());
  EXPECT_EQ(entity_data->enum_value,
            libtextclassifier3::test::EnumValue_VALUE_1);
}

TEST_F(MutableFlatbufferTest, HandlesUnknownFields) {
  std::unique_ptr<MutableFlatbuffer> buffer = builder_.NewRoot();
  EXPECT_TRUE(buffer != nullptr);

  // Add a field that is not known to the (statically generated) code.
  EXPECT_TRUE(buffer->Set("mystic", "this is an unknown field."));

  OwnedFlatbuffer<flatbuffers::Table, std::string> extra(buffer->Serialize());
  EXPECT_EQ(extra
                ->GetPointer<const flatbuffers::String*>(
                    buffer->GetFieldOrNull("mystic")->offset())
                ->str(),
            "this is an unknown field.");
}

TEST_F(MutableFlatbufferTest, HandlesNestedFields) {
  OwnedFlatbuffer<FlatbufferFieldPath, std::string> path =
      CreateFieldPath({"flight_number", "carrier_code"});
  std::unique_ptr<MutableFlatbuffer> buffer = builder_.NewRoot();

  MutableFlatbuffer* parent = nullptr;
  reflection::Field const* field = nullptr;
  EXPECT_TRUE(buffer->GetFieldWithParent(path.get(), &parent, &field));
  EXPECT_EQ(parent, buffer->Mutable("flight_number"));
  EXPECT_EQ(field,
            buffer->Mutable("flight_number")->GetFieldOrNull("carrier_code"));
}

TEST_F(MutableFlatbufferTest, HandlesMultipleNestedFields) {
  std::unique_ptr<MutableFlatbuffer> buffer = builder_.NewRoot();
  MutableFlatbuffer* flight_info = buffer->Mutable("flight_number");
  flight_info->Set("carrier_code", "LX");
  flight_info->Set("flight_code", 38);

  MutableFlatbuffer* contact_info = buffer->Mutable("contact_info");
  EXPECT_TRUE(contact_info->Set("first_name", "Barack"));
  EXPECT_TRUE(contact_info->Set("last_name", "Obama"));
  EXPECT_TRUE(contact_info->Set("phone_number", "1-800-TEST"));
  EXPECT_TRUE(contact_info->Set("score", 1.f));

  // Try to parse with the generated code.
  std::unique_ptr<test::EntityDataT> entity_data =
      LoadAndVerifyMutableFlatbuffer<test::EntityData>(buffer->Serialize());
  ASSERT_NE(entity_data, nullptr);
  EXPECT_EQ(entity_data->flight_number->carrier_code, "LX");
  EXPECT_EQ(entity_data->flight_number->flight_code, 38);
  EXPECT_EQ(entity_data->contact_info->first_name, "Barack");
  EXPECT_EQ(entity_data->contact_info->last_name, "Obama");
  EXPECT_EQ(entity_data->contact_info->phone_number, "1-800-TEST");
  EXPECT_NEAR(entity_data->contact_info->score, 1.f, 1e-4);
}

TEST_F(MutableFlatbufferTest, HandlesFieldsSetWithNamePath) {
  FlatbufferFieldPathT path;
  path.field.emplace_back(new FlatbufferFieldT);
  path.field.back()->field_name = "flight_number";
  path.field.emplace_back(new FlatbufferFieldT);
  path.field.back()->field_name = "carrier_code";
  flatbuffers::FlatBufferBuilder path_builder;
  path_builder.Finish(FlatbufferFieldPath::Pack(path_builder, &path));

  std::unique_ptr<MutableFlatbuffer> buffer = builder_.NewRoot();
  // Test setting value using Set function.
  buffer->Mutable("flight_number")->Set("flight_code", 38);
  // Test setting value using FlatbufferFieldPath.
  buffer->Set(flatbuffers::GetRoot<FlatbufferFieldPath>(
                  path_builder.GetBufferPointer()),
              "LX");

  // Try to parse with the generated code.
  std::unique_ptr<test::EntityDataT> entity_data =
      LoadAndVerifyMutableFlatbuffer<test::EntityData>(buffer->Serialize());
  ASSERT_NE(entity_data, nullptr);
  EXPECT_EQ(entity_data->flight_number->carrier_code, "LX");
  EXPECT_EQ(entity_data->flight_number->flight_code, 38);
}

TEST_F(MutableFlatbufferTest, HandlesFieldsSetWithOffsetPath) {
  FlatbufferFieldPathT path;
  path.field.emplace_back(new FlatbufferFieldT);
  path.field.back()->field_offset = 14;
  path.field.emplace_back(new FlatbufferFieldT);
  path.field.back()->field_offset = 4;
  flatbuffers::FlatBufferBuilder path_builder;
  path_builder.Finish(FlatbufferFieldPath::Pack(path_builder, &path));

  std::unique_ptr<MutableFlatbuffer> buffer = builder_.NewRoot();
  // Test setting value using Set function.
  buffer->Mutable("flight_number")->Set("flight_code", 38);
  // Test setting value using FlatbufferFieldPath.
  buffer->Set(flatbuffers::GetRoot<FlatbufferFieldPath>(
                  path_builder.GetBufferPointer()),
              "LX");

  // Try to parse with the generated code.
  std::unique_ptr<test::EntityDataT> entity_data =
      LoadAndVerifyMutableFlatbuffer<test::EntityData>(buffer->Serialize());
  ASSERT_NE(entity_data, nullptr);
  EXPECT_EQ(entity_data->flight_number->carrier_code, "LX");
  EXPECT_EQ(entity_data->flight_number->flight_code, 38);
}

TEST_F(MutableFlatbufferTest, PartialBuffersAreCorrectlyMerged) {
  std::unique_ptr<MutableFlatbuffer> buffer = builder_.NewRoot();
  buffer->Set("an_int_field", 42);
  buffer->Set("a_long_field", int64{84});
  MutableFlatbuffer* flight_info = buffer->Mutable("flight_number");
  flight_info->Set("carrier_code", "LX");
  flight_info->Set("flight_code", 38);
  auto* reminders = buffer->Repeated("reminders");
  MutableFlatbuffer* reminder1 = reminders->Add();
  reminder1->Set("title", "reminder1");
  auto* reminder1_notes = reminder1->Repeated("notes");
  reminder1_notes->Add("note1");
  reminder1_notes->Add("note2");

  // Create message to merge.
  test::EntityDataT additional_entity_data;
  additional_entity_data.an_int_field = 43;
  additional_entity_data.flight_number.reset(new test::FlightNumberInfoT);
  additional_entity_data.flight_number->flight_code = 39;
  additional_entity_data.contact_info.reset(new test::ContactInfoT);
  additional_entity_data.contact_info->first_name = "Barack";
  additional_entity_data.reminders.push_back(
      std::unique_ptr<test::ReminderT>(new test::ReminderT));
  additional_entity_data.reminders[0]->notes.push_back("additional note1");
  additional_entity_data.reminders[0]->notes.push_back("additional note2");
  additional_entity_data.numbers.push_back(9);
  additional_entity_data.numbers.push_back(10);
  additional_entity_data.strings.push_back("str1");
  additional_entity_data.strings.push_back("str2");

  // Merge it.
  EXPECT_TRUE(buffer->MergeFromSerializedFlatbuffer(
      PackFlatbuffer<test::EntityData>(&additional_entity_data)));

  // Try to parse it with the generated code.
  std::unique_ptr<test::EntityDataT> entity_data =
      LoadAndVerifyMutableFlatbuffer<test::EntityData>(buffer->Serialize());
  ASSERT_NE(entity_data, nullptr);
  EXPECT_EQ(entity_data->an_int_field, 43);
  EXPECT_EQ(entity_data->a_long_field, 84);
  EXPECT_EQ(entity_data->flight_number->carrier_code, "LX");
  EXPECT_EQ(entity_data->flight_number->flight_code, 39);
  EXPECT_EQ(entity_data->contact_info->first_name, "Barack");
  ASSERT_THAT(entity_data->reminders, SizeIs(2));
  EXPECT_THAT(entity_data->reminders[1]->notes,
              ElementsAre("additional note1", "additional note2"));
  EXPECT_THAT(entity_data->numbers, ElementsAre(9, 10));
  EXPECT_THAT(entity_data->strings, ElementsAre("str1", "str2"));
}

TEST_F(MutableFlatbufferTest, MergesNestedFields) {
  std::unique_ptr<MutableFlatbuffer> buffer = builder_.NewRoot();

  // Set a multiply nested field.
  OwnedFlatbuffer<FlatbufferFieldPath, std::string> field_path =
      CreateFieldPath({"nested", "nestedb", "nesteda", "nestedb", "nesteda"});
  buffer->Mutable(field_path.get())->Set("value", "le value");

  std::unique_ptr<test::EntityDataT> entity_data =
      LoadAndVerifyMutableFlatbuffer<test::EntityData>(buffer->Serialize());
  ASSERT_NE(entity_data, nullptr);
  EXPECT_EQ(entity_data->nested->nestedb->nesteda->nestedb->nesteda->value,
            "le value");
}

TEST_F(MutableFlatbufferTest, PrimitiveAndNestedFieldsAreCorrectlyFlattened) {
  std::unique_ptr<MutableFlatbuffer> buffer = builder_.NewRoot();
  buffer->Set("an_int_field", 42);
  buffer->Set("a_long_field", int64{84});
  MutableFlatbuffer* flight_info = buffer->Mutable("flight_number");
  flight_info->Set("carrier_code", "LX");
  flight_info->Set("flight_code", 38);

  std::map<std::string, Variant> entity_data_map = buffer->AsFlatMap();
  EXPECT_EQ(4, entity_data_map.size());
  EXPECT_EQ(42, entity_data_map["an_int_field"].Value<int>());
  EXPECT_EQ(84, entity_data_map["a_long_field"].Value<int64>());
  EXPECT_EQ("LX", entity_data_map["flight_number.carrier_code"]
                      .ConstRefValue<std::string>());
  EXPECT_EQ(38, entity_data_map["flight_number.flight_code"].Value<int>());
}

TEST_F(MutableFlatbufferTest, ToTextProtoWorks) {
  std::unique_ptr<MutableFlatbuffer> buffer = builder_.NewRoot();
  buffer->Set("an_int_field", 42);
  buffer->Set("a_long_field", int64{84});
  MutableFlatbuffer* flight_info = buffer->Mutable("flight_number");
  flight_info->Set("carrier_code", "LX");
  flight_info->Set("flight_code", 38);

  // Add non primitive type.
  auto reminders = buffer->Repeated("reminders");
  auto foo_reminder = reminders->Add();
  foo_reminder->Set("title", "foo reminder");
  auto bar_reminder = reminders->Add();
  bar_reminder->Set("title", "bar reminder");

  // Add primitive type.
  EXPECT_TRUE(buffer->Repeated("numbers")->Add(static_cast<int>(111)));
  EXPECT_TRUE(buffer->Repeated("numbers")->Add(static_cast<int>(222)));
  EXPECT_TRUE(buffer->Repeated("numbers")->Add(static_cast<int>(333)));

  EXPECT_EQ(buffer->ToTextProto(),
            "a_long_field: 84, an_int_field: 42, numbers:  [111, 222, 333] , "
            "reminders:  [{title: 'foo reminder'}, {title: 'bar reminder'}] , "
            "flight_number {flight_code: 38, carrier_code: 'LX'}");
}

TEST_F(MutableFlatbufferTest, RepeatedFieldSetThroughReflectionCanBeRead) {
  std::unique_ptr<MutableFlatbuffer> buffer = builder_.NewRoot();

  auto reminders = buffer->Repeated("reminders");
  {
    auto reminder = reminders->Add();
    reminder->Set("title", "test reminder");
    auto notes = reminder->Repeated("notes");
    notes->Add("note A");
    notes->Add("note B");
  }
  {
    auto reminder = reminders->Add();
    reminder->Set("title", "test reminder 2");
    reminder->Add("notes", "note i");
    reminder->Add("notes", "note ii");
    reminder->Add("notes", "note iii");
  }

  std::unique_ptr<test::EntityDataT> entity_data =
      LoadAndVerifyMutableFlatbuffer<test::EntityData>(buffer->Serialize());
  ASSERT_NE(entity_data, nullptr);
  EXPECT_THAT(entity_data->reminders, SizeIs(2));
  EXPECT_EQ(entity_data->reminders[0]->title, "test reminder");
  EXPECT_THAT(entity_data->reminders[0]->notes,
              ElementsAre("note A", "note B"));
  EXPECT_EQ(entity_data->reminders[1]->title, "test reminder 2");
  EXPECT_THAT(entity_data->reminders[1]->notes,
              ElementsAre("note i", "note ii", "note iii"));
}

TEST_F(MutableFlatbufferTest, RepeatedFieldAddMethodWithIncompatibleValues) {
  std::unique_ptr<MutableFlatbuffer> buffer = builder_.NewRoot();
  EXPECT_FALSE(buffer->Repeated("numbers")->Add(static_cast<int64>(123)));
  EXPECT_FALSE(buffer->Repeated("numbers")->Add(static_cast<int8>(9)));
  EXPECT_TRUE(buffer->Repeated("numbers")->Add(static_cast<int>(999)));

  // Try to parse it with the generated code.
  std::unique_ptr<test::EntityDataT> entity_data =
      LoadAndVerifyMutableFlatbuffer<test::EntityData>(buffer->Serialize());
  ASSERT_NE(entity_data, nullptr);
  ASSERT_NE(entity_data, nullptr);
  ASSERT_EQ(entity_data->numbers.size(), 1);
  EXPECT_EQ(entity_data->numbers[0], 999);
}

TEST_F(MutableFlatbufferTest, RepeatedFieldGetAndSizeMethods) {
  std::unique_ptr<MutableFlatbuffer> buffer = builder_.NewRoot();
  EXPECT_TRUE(buffer->Repeated("numbers")->Add(1));
  EXPECT_TRUE(buffer->Repeated("numbers")->Add(2));
  EXPECT_TRUE(buffer->Repeated("numbers")->Add(3));

  EXPECT_EQ(buffer->Repeated("numbers")->Size(), 3);
  EXPECT_EQ(buffer->Repeated("numbers")->Get<int>(0), 1);
  EXPECT_EQ(buffer->Repeated("numbers")->Get<int>(1), 2);
  EXPECT_EQ(buffer->Repeated("numbers")->Get<int>(2), 3);
}

TEST_F(MutableFlatbufferTest, GetsRepeatedFieldFromPath) {
  std::unique_ptr<MutableFlatbuffer> buffer = builder_.NewRoot();
  OwnedFlatbuffer<FlatbufferFieldPath, std::string> notes =
      CreateFieldPath({"nested", "repeated_str"});

  EXPECT_TRUE(buffer->Repeated(notes.get())->Add("a"));
  EXPECT_TRUE(buffer->Repeated(notes.get())->Add("test"));

  std::unique_ptr<test::EntityDataT> entity_data =
      LoadAndVerifyMutableFlatbuffer<test::EntityData>(buffer->Serialize());
  ASSERT_NE(entity_data, nullptr);
  EXPECT_THAT(entity_data->nested->repeated_str, SizeIs(2));
  EXPECT_THAT(entity_data->nested->repeated_str, ElementsAre("a", "test"));
}

}  // namespace
}  // namespace libtextclassifier3
