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

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "bluetooth_flatbuffer_test_generated.h"
#include "flatbuffers/flatbuffers.h"
#include "flatbuffers/idl.h"
#include "flatbuffers/util.h"

namespace bluetooth {
namespace dumpsys {

class BluetoothFlatbufferTest : public ::testing::Test {
 protected:
  void SetUp() override {}

  void TearDown() override {}
};

TEST_F(BluetoothFlatbufferTest, precondition) {}

TEST_F(BluetoothFlatbufferTest, BuilderTest) {
  flatbuffers::FlatBufferBuilder builder(1024);
  auto string_private = builder.CreateString("String private");
  auto string_opaque = builder.CreateString("String opaque");
  auto string_anonymized = builder.CreateString("String anonymized");
  auto string_any = builder.CreateString("String any");

  TestTableBuilder table_builder(builder);
  table_builder.add_string_private(string_private);
  table_builder.add_string_opaque(string_opaque);
  table_builder.add_string_anonymized(string_anonymized);
  table_builder.add_string_any(string_any);

  table_builder.add_int_private(123);
  table_builder.add_int_opaque(456);
  table_builder.add_int_anonymized(789);
  table_builder.add_int_any(0xabc);

  builder.Finish(table_builder.Finish());

  const TestTable* test_table = GetTestTable(builder.GetBufferPointer());

  ASSERT_EQ("String private", test_table->string_private()->str());
  ASSERT_EQ("String opaque", test_table->string_opaque()->str());
  ASSERT_EQ("String anonymized", test_table->string_anonymized()->str());
  ASSERT_EQ("String any", test_table->string_any()->str());

  ASSERT_EQ(123, test_table->int_private());
  ASSERT_EQ(456, test_table->int_opaque());
  ASSERT_EQ(789, test_table->int_anonymized());
  ASSERT_EQ(0xabc, test_table->int_any());
}

}  // namespace dumpsys
}  // namespace bluetooth
