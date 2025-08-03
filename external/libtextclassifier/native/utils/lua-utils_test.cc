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

#include "utils/lua-utils.h"

#include <memory>
#include <string>

#include "utils/flatbuffers/flatbuffers.h"
#include "utils/flatbuffers/mutable.h"
#include "utils/lua_utils_tests_generated.h"
#include "utils/strings/stringpiece.h"
#include "utils/test-data-test-utils.h"
#include "utils/testing/test_data_generator.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {
namespace {

using testing::DoubleEq;
using testing::ElementsAre;
using testing::Eq;
using testing::FloatEq;

class LuaUtilsTest : public testing::Test, protected LuaEnvironment {
 protected:
  LuaUtilsTest()
      : schema_(GetTestFileContent("utils/lua_utils_tests.bfbs")),
        flatbuffer_builder_(schema_.get()) {
    EXPECT_THAT(RunProtected([this] {
                  LoadDefaultLibraries();
                  return LUA_OK;
                }),
                Eq(LUA_OK));
  }

  void RunScript(StringPiece script) {
    EXPECT_THAT(luaL_loadbuffer(state_, script.data(), script.size(),
                                /*name=*/nullptr),
                Eq(LUA_OK));
    EXPECT_THAT(
        lua_pcall(state_, /*nargs=*/0, /*num_results=*/1, /*errfunc=*/0),
        Eq(LUA_OK));
  }

  OwnedFlatbuffer<reflection::Schema, std::string> schema_;
  MutableFlatbufferBuilder flatbuffer_builder_;
  TestDataGenerator test_data_generator_;
};

template <typename T>
class TypedLuaUtilsTest : public LuaUtilsTest {};

using testing::Types;
using LuaTypes =
    ::testing::Types<int64, uint64, int32, uint32, int16, uint16, int8, uint8,
                     float, double, bool, std::string>;
TYPED_TEST_SUITE(TypedLuaUtilsTest, LuaTypes);

TYPED_TEST(TypedLuaUtilsTest, HandlesVectors) {
  std::vector<TypeParam> elements(5);
  std::generate_n(elements.begin(), 5, [&]() {
    return this->test_data_generator_.template generate<TypeParam>();
  });

  this->PushVector(elements);

  EXPECT_THAT(this->template ReadVector<TypeParam>(),
              testing::ContainerEq(elements));
}

TYPED_TEST(TypedLuaUtilsTest, HandlesVectorIterators) {
  std::vector<TypeParam> elements(5);
  std::generate_n(elements.begin(), 5, [&]() {
    return this->test_data_generator_.template generate<TypeParam>();
  });

  this->PushVectorIterator(&elements);

  EXPECT_THAT(this->template ReadVector<TypeParam>(),
              testing::ContainerEq(elements));
}

TEST_F(LuaUtilsTest, IndexCallback) {
  test::TestDataT input_data;
  input_data.repeated_byte_field = {1, 2};
  input_data.repeated_ubyte_field = {1, 2};
  input_data.repeated_int_field = {1, 2};
  input_data.repeated_uint_field = {1, 2};
  input_data.repeated_long_field = {1, 2};
  input_data.repeated_ulong_field = {1, 2};
  input_data.repeated_bool_field = {true, false};
  input_data.repeated_float_field = {1, 2};
  input_data.repeated_double_field = {1, 2};
  input_data.repeated_string_field = {"1", "2"};

  flatbuffers::FlatBufferBuilder builder;
  builder.Finish(test::TestData::Pack(builder, &input_data));
  const flatbuffers::DetachedBuffer input_buffer = builder.Release();
  PushFlatbuffer(schema_.get(),
                 flatbuffers::GetRoot<flatbuffers::Table>(input_buffer.data()));
  lua_setglobal(state_, "arg");
  // A Lua script that reads the vectors and return the first value of them.
  // This should trigger the __index callback.
  RunScript(R"lua(
    return {
        byte_field = arg.repeated_byte_field[1],
        ubyte_field = arg.repeated_ubyte_field[1],
        int_field = arg.repeated_int_field[1],
        uint_field = arg.repeated_uint_field[1],
        long_field = arg.repeated_long_field[1],
        ulong_field = arg.repeated_ulong_field[1],
        bool_field = arg.repeated_bool_field[1],
        float_field = arg.repeated_float_field[1],
        double_field = arg.repeated_double_field[1],
        string_field = arg.repeated_string_field[1],
    }
  )lua");

  // Read the flatbuffer.
  std::unique_ptr<MutableFlatbuffer> buffer = flatbuffer_builder_.NewRoot();
  ReadFlatbuffer(/*index=*/-1, buffer.get());
  const std::string serialized_buffer = buffer->Serialize();
  std::unique_ptr<test::TestDataT> test_data =
      LoadAndVerifyMutableFlatbuffer<test::TestData>(buffer->Serialize());

  EXPECT_THAT(test_data->byte_field, 1);
  EXPECT_THAT(test_data->ubyte_field, 1);
  EXPECT_THAT(test_data->int_field, 1);
  EXPECT_THAT(test_data->uint_field, 1);
  EXPECT_THAT(test_data->long_field, 1);
  EXPECT_THAT(test_data->ulong_field, 1);
  EXPECT_THAT(test_data->bool_field, true);
  EXPECT_THAT(test_data->float_field, FloatEq(1));
  EXPECT_THAT(test_data->double_field, DoubleEq(1));
  EXPECT_THAT(test_data->string_field, "1");
}

TEST_F(LuaUtilsTest, PairCallback) {
  test::TestDataT input_data;
  input_data.repeated_byte_field = {1, 2};
  input_data.repeated_ubyte_field = {1, 2};
  input_data.repeated_int_field = {1, 2};
  input_data.repeated_uint_field = {1, 2};
  input_data.repeated_long_field = {1, 2};
  input_data.repeated_ulong_field = {1, 2};
  input_data.repeated_bool_field = {true, false};
  input_data.repeated_float_field = {1, 2};
  input_data.repeated_double_field = {1, 2};
  input_data.repeated_string_field = {"1", "2"};

  flatbuffers::FlatBufferBuilder builder;
  builder.Finish(test::TestData::Pack(builder, &input_data));
  const flatbuffers::DetachedBuffer input_buffer = builder.Release();
  PushFlatbuffer(schema_.get(),
                 flatbuffers::GetRoot<flatbuffers::Table>(input_buffer.data()));
  lua_setglobal(state_, "arg");

  // Iterate the pushed repeated fields by using the pair API and check
  // if the value is correct. This should trigger the __pair callback.
  RunScript(R"lua(
    function equal(table1, table2)
      for key, value in pairs(table1) do
          if value ~= table2[key] then
              return false
          end
      end
      return true
    end

    local valid = equal(arg.repeated_byte_field, {[1]=1,[2]=2})
    valid = valid and equal(arg.repeated_ubyte_field, {[1]=1,[2]=2})
    valid = valid and equal(arg.repeated_int_field, {[1]=1,[2]=2})
    valid = valid and equal(arg.repeated_uint_field, {[1]=1,[2]=2})
    valid = valid and equal(arg.repeated_long_field, {[1]=1,[2]=2})
    valid = valid and equal(arg.repeated_ulong_field, {[1]=1,[2]=2})
    valid = valid and equal(arg.repeated_bool_field, {[1]=true,[2]=false})
    valid = valid and equal(arg.repeated_float_field, {[1]=1,[2]=2})
    valid = valid and equal(arg.repeated_double_field, {[1]=1,[2]=2})
    valid = valid and equal(arg.repeated_string_field, {[1]="1",[2]="2"})

    return {
        bool_field = valid
    }
  )lua");

  // Read the flatbuffer.
  std::unique_ptr<MutableFlatbuffer> buffer = flatbuffer_builder_.NewRoot();
  ReadFlatbuffer(/*index=*/-1, buffer.get());
  const std::string serialized_buffer = buffer->Serialize();
  std::unique_ptr<test::TestDataT> test_data =
      LoadAndVerifyMutableFlatbuffer<test::TestData>(buffer->Serialize());

  EXPECT_THAT(test_data->bool_field, true);
}

TEST_F(LuaUtilsTest, PushAndReadsFlatbufferRoundTrip) {
  // Setup.
  test::TestDataT input_data;
  input_data.byte_field = 1;
  input_data.ubyte_field = 2;
  input_data.int_field = 10;
  input_data.uint_field = 11;
  input_data.long_field = 20;
  input_data.ulong_field = 21;
  input_data.bool_field = true;
  input_data.float_field = 42.1;
  input_data.double_field = 12.4;
  input_data.string_field = "hello there";
  // Nested field.
  input_data.nested_field = std::make_unique<test::TestDataT>();
  input_data.nested_field->float_field = 64;
  input_data.nested_field->string_field = "hello nested";
  // Repeated fields.
  input_data.repeated_byte_field = {1, 2, 1};
  input_data.repeated_byte_field = {1, 2, 1};
  input_data.repeated_ubyte_field = {2, 4, 2};
  input_data.repeated_int_field = {1, 2, 3};
  input_data.repeated_uint_field = {2, 4, 6};
  input_data.repeated_long_field = {4, 5, 6};
  input_data.repeated_ulong_field = {8, 10, 12};
  input_data.repeated_bool_field = {true, false, true};
  input_data.repeated_float_field = {1.23, 2.34, 3.45};
  input_data.repeated_double_field = {1.11, 2.22, 3.33};
  input_data.repeated_string_field = {"a", "bold", "one"};
  // Repeated nested fields.
  input_data.repeated_nested_field.push_back(
      std::make_unique<test::TestDataT>());
  input_data.repeated_nested_field.back()->string_field = "a";
  input_data.repeated_nested_field.push_back(
      std::make_unique<test::TestDataT>());
  input_data.repeated_nested_field.back()->string_field = "b";
  input_data.repeated_nested_field.push_back(
      std::make_unique<test::TestDataT>());
  input_data.repeated_nested_field.back()->repeated_string_field = {"nested",
                                                                    "nested2"};
  flatbuffers::FlatBufferBuilder builder;
  builder.Finish(test::TestData::Pack(builder, &input_data));
  const flatbuffers::DetachedBuffer input_buffer = builder.Release();
  PushFlatbuffer(schema_.get(),
                 flatbuffers::GetRoot<flatbuffers::Table>(input_buffer.data()));
  lua_setglobal(state_, "arg");

  RunScript(R"lua(
    return {
        byte_field = arg.byte_field,
        ubyte_field = arg.ubyte_field,
        int_field = arg.int_field,
        uint_field = arg.uint_field,
        long_field = arg.long_field,
        ulong_field = arg.ulong_field,
        bool_field = arg.bool_field,
        float_field = arg.float_field,
        double_field = arg.double_field,
        string_field = arg.string_field,
        nested_field = {
          float_field = arg.nested_field.float_field,
          string_field = arg.nested_field.string_field,
        },
        repeated_byte_field = arg.repeated_byte_field,
        repeated_ubyte_field = arg.repeated_ubyte_field,
        repeated_int_field = arg.repeated_int_field,
        repeated_uint_field = arg.repeated_uint_field,
        repeated_long_field = arg.repeated_long_field,
        repeated_ulong_field = arg.repeated_ulong_field,
        repeated_bool_field = arg.repeated_bool_field,
        repeated_float_field = arg.repeated_float_field,
        repeated_double_field = arg.repeated_double_field,
        repeated_string_field = arg.repeated_string_field,
        repeated_nested_field = {
          { string_field = arg.repeated_nested_field[1].string_field },
          { string_field = arg.repeated_nested_field[2].string_field },
          { repeated_string_field = arg.repeated_nested_field[3].repeated_string_field },
        },
    }
  )lua");

  // Read the flatbuffer.
  std::unique_ptr<MutableFlatbuffer> buffer = flatbuffer_builder_.NewRoot();
  ReadFlatbuffer(/*index=*/-1, buffer.get());
  const std::string serialized_buffer = buffer->Serialize();
  std::unique_ptr<test::TestDataT> test_data =
      LoadAndVerifyMutableFlatbuffer<test::TestData>(buffer->Serialize());

  EXPECT_THAT(test_data->byte_field, 1);
  EXPECT_THAT(test_data->ubyte_field, 2);
  EXPECT_THAT(test_data->int_field, 10);
  EXPECT_THAT(test_data->uint_field, 11);
  EXPECT_THAT(test_data->long_field, 20);
  EXPECT_THAT(test_data->ulong_field, 21);
  EXPECT_THAT(test_data->bool_field, true);
  EXPECT_THAT(test_data->float_field, FloatEq(42.1));
  EXPECT_THAT(test_data->double_field, DoubleEq(12.4));
  EXPECT_THAT(test_data->string_field, "hello there");
  EXPECT_THAT(test_data->repeated_byte_field, ElementsAre(1, 2, 1));
  EXPECT_THAT(test_data->repeated_ubyte_field, ElementsAre(2, 4, 2));
  EXPECT_THAT(test_data->repeated_int_field, ElementsAre(1, 2, 3));
  EXPECT_THAT(test_data->repeated_uint_field, ElementsAre(2, 4, 6));
  EXPECT_THAT(test_data->repeated_long_field, ElementsAre(4, 5, 6));
  EXPECT_THAT(test_data->repeated_ulong_field, ElementsAre(8, 10, 12));
  EXPECT_THAT(test_data->repeated_bool_field, ElementsAre(true, false, true));
  EXPECT_THAT(test_data->repeated_float_field, ElementsAre(1.23, 2.34, 3.45));
  EXPECT_THAT(test_data->repeated_double_field, ElementsAre(1.11, 2.22, 3.33));
  EXPECT_THAT(test_data->repeated_string_field,
              ElementsAre("a", "bold", "one"));
  // Nested fields.
  EXPECT_THAT(test_data->nested_field->float_field, FloatEq(64));
  EXPECT_THAT(test_data->nested_field->string_field, "hello nested");
  // Repeated nested fields.
  EXPECT_THAT(test_data->repeated_nested_field[0]->string_field, "a");
  EXPECT_THAT(test_data->repeated_nested_field[1]->string_field, "b");
  EXPECT_THAT(test_data->repeated_nested_field[2]->repeated_string_field,
              ElementsAre("nested", "nested2"));
}

TEST_F(LuaUtilsTest, HandlesRepeatedNestedFlatbufferFields) {
  // Create test flatbuffer.
  std::unique_ptr<MutableFlatbuffer> buffer = flatbuffer_builder_.NewRoot();
  RepeatedField* repeated_field = buffer->Repeated("repeated_nested_field");
  repeated_field->Add()->Set("string_field", "hello");
  repeated_field->Add()->Set("string_field", "my");
  MutableFlatbuffer* nested = repeated_field->Add();
  nested->Set("string_field", "old");
  RepeatedField* nested_repeated = nested->Repeated("repeated_string_field");
  nested_repeated->Add("friend");
  nested_repeated->Add("how");
  nested_repeated->Add("are");
  repeated_field->Add()->Set("string_field", "you?");
  const std::string serialized_buffer = buffer->Serialize();
  PushFlatbuffer(schema_.get(), flatbuffers::GetRoot<flatbuffers::Table>(
                                    serialized_buffer.data()));
  lua_setglobal(state_, "arg");

  RunScript(R"lua(
    result = {}
    for _, nested in pairs(arg.repeated_nested_field) do
      result[#result + 1] = nested.string_field
      for _, nested_string in pairs(nested.repeated_string_field) do
        result[#result + 1] = nested_string
      end
    end
    return result
  )lua");

  EXPECT_THAT(
      ReadVector<std::string>(),
      ElementsAre("hello", "my", "old", "friend", "how", "are", "you?"));
}

TEST_F(LuaUtilsTest, CorrectlyReadsTwoFlatbuffersSimultaneously) {
  // The first flatbuffer.
  std::unique_ptr<MutableFlatbuffer> buffer = flatbuffer_builder_.NewRoot();
  buffer->Set("string_field", "first");
  const std::string serialized_buffer = buffer->Serialize();
  PushFlatbuffer(schema_.get(), flatbuffers::GetRoot<flatbuffers::Table>(
                                    serialized_buffer.data()));
  lua_setglobal(state_, "arg");
  // The second flatbuffer.
  std::unique_ptr<MutableFlatbuffer> buffer2 = flatbuffer_builder_.NewRoot();
  buffer2->Set("string_field", "second");
  const std::string serialized_buffer2 = buffer2->Serialize();
  PushFlatbuffer(schema_.get(), flatbuffers::GetRoot<flatbuffers::Table>(
                                    serialized_buffer2.data()));
  lua_setglobal(state_, "arg2");

  RunScript(R"lua(
    return {arg.string_field, arg2.string_field}
  )lua");

  EXPECT_THAT(ReadVector<std::string>(), ElementsAre("first", "second"));
}

}  // namespace
}  // namespace libtextclassifier3
