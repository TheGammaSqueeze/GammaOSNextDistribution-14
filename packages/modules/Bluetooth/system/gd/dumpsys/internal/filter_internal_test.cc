/*
 * Copyright 2020 The Android Open Source Project
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

#include "dumpsys/internal/filter_internal.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "dumpsys/internal/test_data/float_bfbs.h"
#include "dumpsys/internal/test_data/float_generated.h"
#include "dumpsys/internal/test_data/integer_bfbs.h"
#include "dumpsys/internal/test_data/integer_generated.h"
#include "dumpsys/internal/test_data/string_bfbs.h"
#include "dumpsys/internal/test_data/string_generated.h"
#include "dumpsys/internal/test_data/struct_bfbs.h"
#include "dumpsys/internal/test_data/struct_generated.h"
#include "os/log.h"

namespace testing {

class DumpsysFilterInternalTest : public Test {
 protected:
  void SetUp() override {}
  void TearDown() override {}

  flatbuffers::Table* GetMutableTable() const {
    return flatbuffers::GetMutableRoot<flatbuffers::Table>(fb_builder_.GetBufferPointer());
  }

  void ParseReflectionSchema(unsigned char* bfbs, unsigned int bfbs_len) {
    ASSERT_TRUE(reflection_schema_.empty());
    reflection_schema_ = std::move(std::vector<const uint8_t>(bfbs, bfbs + bfbs_len));
    flatbuffers::Verifier verifier(reflection_schema_.data(), reflection_schema_.size());
    ASSERT_TRUE(reflection::VerifySchemaBuffer(verifier));
    schema_ = reflection::GetSchema(reflection_schema_.data());
    ASSERT_TRUE(schema_ != nullptr);
  }

  const reflection::Schema* schema_{nullptr};
  flatbuffers::FlatBufferBuilder fb_builder_ = std::move(flatbuffers::FlatBufferBuilder(1024));

 private:
  std::vector<const uint8_t> reflection_schema_;
};

class DumpsysFilterInternalIntegerTest : public DumpsysFilterInternalTest {
 protected:
  void SetUp() override {
    this->ParseReflectionSchema(integer_bfbs, integer_bfbs_len);
  }

  const testing::TestTableInteger* CreateInteger(int32_t value) {
    TestTableIntegerBuilder builder(fb_builder_);
    builder.add_test_int(value);
    fb_builder_.Finish(builder.Finish());
    return GetTestTableInteger(fb_builder_.GetBufferPointer());
  }
};

class DumpsysFilterInternalFloatTest : public DumpsysFilterInternalTest {
 protected:
  void SetUp() override {
    this->ParseReflectionSchema(float_bfbs, float_bfbs_len);
  }

  const testing::TestTableFloat* CreateFloat(double value) {
    TestTableFloatBuilder builder(fb_builder_);
    builder.add_test_float(value);
    fb_builder_.Finish(builder.Finish());
    return GetTestTableFloat(fb_builder_.GetBufferPointer());
  }
};

class DumpsysFilterInternalStringTest : public DumpsysFilterInternalTest {
 protected:
  void SetUp() override {
    this->ParseReflectionSchema(string_bfbs, string_bfbs_len);
  }

  const testing::TestTableString* CreateString(std::string string) {
    auto test_string = fb_builder_.CreateString(string);
    TestTableStringBuilder builder(fb_builder_);
    builder.add_test_string(test_string);
    fb_builder_.Finish(builder.Finish());
    return GetTestTableString(fb_builder_.GetBufferPointer());
  }
};

class DumpsysFilterInternalStructTest : public DumpsysFilterInternalTest {
 protected:
  void SetUp() override {
    this->ParseReflectionSchema(struct_bfbs, struct_bfbs_len);
  }

  flatbuffers::Offset<TestSubTable> CreateSubTable(int val) {
    TestSubTableBuilder builder(fb_builder_);
    builder.add_placeholder(val);
    return builder.Finish();
  }

  const testing::TestTableStruct* CreateStruct(int val) {
    auto sub_table = CreateSubTable(val);

    TestTableStructBuilder builder(fb_builder_);
    builder.add_sub_table(sub_table);
    fb_builder_.Finish(builder.Finish());
    return GetTestTableStruct(fb_builder_.GetBufferPointer());
  }
};

TEST_F(DumpsysFilterInternalIntegerTest, filter_type_integer_any) {
  const testing::TestTableInteger* test_table = CreateInteger(123);
  ASSERT_EQ(123, test_table->test_int());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeInteger(**it, table, bluetooth::dumpsys::internal::PrivacyLevel::kAny);
  }
  ASSERT_EQ(123, test_table->test_int());
}

TEST_F(DumpsysFilterInternalIntegerTest, filter_type_integer_anonymized) {
  const testing::TestTableInteger* test_table = CreateInteger(123);
  ASSERT_EQ(123, test_table->test_int());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeInteger(
        **it, table, bluetooth::dumpsys::internal::PrivacyLevel::kAnonymized);
  }
  ASSERT_NE(123, test_table->test_int());
}

TEST_F(DumpsysFilterInternalIntegerTest, filter_type_integer_opaque) {
  const testing::TestTableInteger* test_table = CreateInteger(123);
  ASSERT_EQ(123, test_table->test_int());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeInteger(**it, table, bluetooth::dumpsys::internal::PrivacyLevel::kOpaque);
  }
  ASSERT_EQ(0, test_table->test_int());
}

TEST_F(DumpsysFilterInternalIntegerTest, filter_type_integer_privacy) {
  const testing::TestTableInteger* test_table = CreateInteger(123);
  ASSERT_EQ(123, test_table->test_int());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeInteger(**it, table, bluetooth::dumpsys::internal::PrivacyLevel::kPrivate);
  }
  ASSERT_EQ(0, test_table->test_int());
}

TEST_F(DumpsysFilterInternalFloatTest, filter_type_float_any) {
  const testing::TestTableFloat* test_table = CreateFloat(1.23);
  ASSERT_FLOAT_EQ(1.23, test_table->test_float());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeFloat(**it, table, bluetooth::dumpsys::internal::PrivacyLevel::kAny);
  }
  ASSERT_FLOAT_EQ(1.23, test_table->test_float());
}

TEST_F(DumpsysFilterInternalFloatTest, filter_type_float_anonymized) {
  const testing::TestTableFloat* test_table = CreateFloat(1.23);
  ASSERT_FLOAT_EQ(1.23, test_table->test_float());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeFloat(**it, table, bluetooth::dumpsys::internal::PrivacyLevel::kAnonymized);
  }
  ASSERT_THAT(test_table->test_float(), Not(FloatEq(1.23)));
}

TEST_F(DumpsysFilterInternalFloatTest, filter_type_float_opaque) {
  const testing::TestTableFloat* test_table = CreateFloat(1.23);
  ASSERT_FLOAT_EQ(1.23, test_table->test_float());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeFloat(**it, table, bluetooth::dumpsys::internal::PrivacyLevel::kOpaque);
  }
  ASSERT_FLOAT_EQ(0.0, test_table->test_float());
}

TEST_F(DumpsysFilterInternalFloatTest, filter_type_float_private) {
  const testing::TestTableFloat* test_table = CreateFloat(1.23);
  ASSERT_FLOAT_EQ(1.23, test_table->test_float());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeFloat(**it, table, bluetooth::dumpsys::internal::PrivacyLevel::kPrivate);
  }
  ASSERT_FLOAT_EQ(0.0, test_table->test_float());
}

TEST_F(DumpsysFilterInternalStringTest, filter_type_string_any) {
  const testing::TestTableString* test_table = CreateString("This is a string");
  ASSERT_STREQ("This is a string", test_table->test_string()->c_str());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeString(**it, table, bluetooth::dumpsys::internal::PrivacyLevel::kAny);
  }
  ASSERT_STREQ("This is a string", test_table->test_string()->c_str());
}

TEST_F(DumpsysFilterInternalStringTest, filter_type_string_anonymous) {
  const testing::TestTableString* test_table = CreateString("This is a string");
  ASSERT_STREQ("This is a string", test_table->test_string()->c_str());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeString(
        **it, table, bluetooth::dumpsys::internal::PrivacyLevel::kAnonymized);
  }
  ASSERT_NE("This is a string", test_table->test_string()->c_str());
}

TEST_F(DumpsysFilterInternalStringTest, filter_type_string_anonymous_small) {
  const testing::TestTableString* test_table = CreateString("A");
  ASSERT_STREQ("A", test_table->test_string()->c_str());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeString(
        **it, table, bluetooth::dumpsys::internal::PrivacyLevel::kAnonymized);
  }
  ASSERT_NE("A", test_table->test_string()->c_str());
}

TEST_F(DumpsysFilterInternalStringTest, filter_type_string_anonymous_large) {
  const testing::TestTableString* test_table = CreateString("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
  ASSERT_STREQ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz", test_table->test_string()->c_str());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeString(
        **it, table, bluetooth::dumpsys::internal::PrivacyLevel::kAnonymized);
  }
  ASSERT_NE("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz", test_table->test_string()->c_str());
}

TEST_F(DumpsysFilterInternalStringTest, filter_type_string_opaque) {
  const testing::TestTableString* test_table = CreateString("This is a string");
  ASSERT_STREQ("This is a string", test_table->test_string()->c_str());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeString(**it, table, bluetooth::dumpsys::internal::PrivacyLevel::kOpaque);
  }

  std::string opaque_expected(strlen("This is a string"), '*');
  ASSERT_STREQ(opaque_expected.c_str(), test_table->test_string()->c_str());
}

TEST_F(DumpsysFilterInternalStringTest, filter_type_string_private) {
  const testing::TestTableString* test_table = CreateString("This is a string");
  ASSERT_STREQ("This is a string", test_table->test_string()->c_str());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeString(**it, table, bluetooth::dumpsys::internal::PrivacyLevel::kPrivate);
  }
  ASSERT_EQ(nullptr, test_table->test_string());
}

TEST_F(DumpsysFilterInternalStringTest, filter_type_string_private_small) {
  const testing::TestTableString* test_table = CreateString("A");
  ASSERT_STREQ("A", test_table->test_string()->c_str());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeString(**it, table, bluetooth::dumpsys::internal::PrivacyLevel::kPrivate);
  }
  ASSERT_EQ(nullptr, test_table->test_string());
}

TEST_F(DumpsysFilterInternalStructTest, filter_type_struct_any) {
  const testing::TestTableStruct* test_table = CreateStruct(456);
  ASSERT_EQ(456, test_table->sub_table()->placeholder());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeStruct(**it, table, bluetooth::dumpsys::internal::PrivacyLevel::kAny);
  }
  ASSERT_EQ(456, test_table->sub_table()->placeholder());
}

TEST_F(DumpsysFilterInternalStructTest, filter_type_struct_anonymous) {
  const testing::TestTableStruct* test_table = CreateStruct(456);
  ASSERT_EQ(456, test_table->sub_table()->placeholder());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeStruct(
        **it, table, bluetooth::dumpsys::internal::PrivacyLevel::kAnonymized);
  }
  ASSERT_EQ(nullptr, test_table->sub_table());
}

TEST_F(DumpsysFilterInternalStructTest, filter_type_struct_opaque) {
  const testing::TestTableStruct* test_table = CreateStruct(456);
  ASSERT_EQ(456, test_table->sub_table()->placeholder());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeStruct(**it, table, bluetooth::dumpsys::internal::PrivacyLevel::kOpaque);
  }
  ASSERT_EQ(nullptr, test_table->sub_table());
}

TEST_F(DumpsysFilterInternalStructTest, filter_type_struct_private) {
  const testing::TestTableStruct* test_table = CreateStruct(456);
  ASSERT_EQ(456, test_table->sub_table()->placeholder());

  flatbuffers::Table* table = GetMutableTable();

  const reflection::Object* object = schema_->root_table();
  ASSERT_TRUE(object != nullptr);

  for (auto it = object->fields()->cbegin(); it != object->fields()->cend(); ++it) {
    bluetooth::dumpsys::internal::FilterTypeStruct(**it, table, bluetooth::dumpsys::internal::PrivacyLevel::kPrivate);
  }
  ASSERT_EQ(nullptr, test_table->sub_table());
}

}  // namespace testing
