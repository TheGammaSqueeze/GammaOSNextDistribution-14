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
#include <gtest/gtest.h>

#include <list>
#include <vector>

#include "bundler.h"
#include "bundler_schema_generated.h"
#include "flatbuffers/flatbuffers.h"

// Must be run from the same directory as the test data 'test.bfbs'.
// This is how the presubmit unit test pipeline functions.
constexpr char kTestFilename[] = "test.bfbs";

bool LoadBinarySchema(const char* filename, std::string* binary_schema);
bool VerifyBinarySchema(const std::vector<uint8_t>& raw_schema);
bool CreateBinarySchemaBundle(
    flatbuffers::FlatBufferBuilder* builder,
    const std::vector<std::string>& filenames,
    std::vector<flatbuffers::Offset<bluetooth::dumpsys::BundledSchemaMap>>* vector_map,
    std::list<std::string>* bundled_names);
int WriteHeaderFile(FILE* fp, const uint8_t* data, size_t data_len);

class BundlerTest : public ::testing::Test {
 public:
  void SetUp() override {}

  void TearDown() override {}
};

TEST_F(BundlerTest, LoadBinarySchema) {
  std::string string_schema;
  ASSERT_FALSE(LoadBinarySchema(nullptr, &string_schema));
  ASSERT_DEATH(LoadBinarySchema(kTestFilename, nullptr), "");
  ASSERT_TRUE(LoadBinarySchema(kTestFilename, &string_schema));
  ASSERT_FALSE(LoadBinarySchema("does_not_exist.bfbs", &string_schema));
}

TEST_F(BundlerTest, VerifyBinarySchema) {
  std::string string_schema;
  ASSERT_TRUE(LoadBinarySchema(kTestFilename, &string_schema));
  std::vector<uint8_t> raw_schema(string_schema.begin(), string_schema.end());
  ASSERT_TRUE(VerifyBinarySchema(raw_schema));

  std::vector<uint8_t> bogus_raw_schema(string_schema.begin() + 1, string_schema.end());
  ASSERT_FALSE(VerifyBinarySchema(bogus_raw_schema));
}

TEST_F(BundlerTest, CreateBinarySchemaBundle) {
  flatbuffers::FlatBufferBuilder builder;
  std::vector<std::string> filenames;
  std::vector<flatbuffers::Offset<bluetooth::dumpsys::BundledSchemaMap>> vector_map;
  std::list<std::string> bundled_names;
  ASSERT_TRUE(CreateBinarySchemaBundle(&builder, filenames, &vector_map, &bundled_names));
  ASSERT_EQ((unsigned int)0, vector_map.size());
}

TEST_F(BundlerTest, WriteHeaderFile) {
  std::vector<uint8_t> data;
  data.push_back(0x10);
  data.push_back(0x11);
  data.push_back(0x12);
  data.push_back(0x13);
  ASSERT_DEATH(WriteHeaderFile(nullptr, data.data(), data.size()), "");
  FILE* fp = fopen("/tmp/test.h", "w+");
  ASSERT_NE(fp, nullptr);
  WriteHeaderFile(fp, data.data(), data.size());
  fseek(fp, 0L, SEEK_SET);
  char buf[16];
  fread(buf, 1, 15, fp);
  buf[12] = '\0';
  std::string s(buf);
  ASSERT_EQ("// Generated", s);
  fclose(fp);
  unlink("/tmp/test.h");
}
