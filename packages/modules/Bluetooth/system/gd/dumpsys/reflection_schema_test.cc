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

#include "dumpsys/reflection_schema.h"

#include <gtest/gtest.h>

#include "dumpsys/dumpsys_test_data.h"

namespace bluetooth {
namespace dumpsys {
extern const unsigned char* data;
extern const size_t data_size;
const std::string& GetBundledSchemaData();
}  // namespace dumpsys
}  // namespace bluetooth

namespace testing {

using namespace bluetooth;

class ReflectionSchemaTest : public Test {
 protected:
  void SetUp() override {}

  void TearDown() override {}
};

TEST_F(ReflectionSchemaTest, verify_test_content) {
  dumpsys::ReflectionSchema reflection_schema(testing::GetBundledSchemaData());
  ASSERT_TRUE(reflection_schema.GetNumberOfBundledSchemas() == 5);
  ASSERT_TRUE(reflection_schema.FindInReflectionSchema("testing.DumpsysTestDataRoot") != nullptr);
  ASSERT_TRUE(reflection_schema.FindInReflectionSchema("testing.BarTestSchema") != nullptr);
  ASSERT_TRUE(reflection_schema.FindInReflectionSchema("testing.BazTestSchema") != nullptr);
  ASSERT_TRUE(reflection_schema.FindInReflectionSchema("testing.FooTestSchema") != nullptr);
  ASSERT_TRUE(reflection_schema.FindInReflectionSchema("testing.QuxTestSchema") != nullptr);
  ASSERT_TRUE(reflection_schema.FindInReflectionSchema("DoesNotExist") == nullptr);
}

TEST_F(ReflectionSchemaTest, verify_test_schema) {
  dumpsys::ReflectionSchema reflection_schema(testing::GetBundledSchemaData());
  ASSERT_TRUE(reflection_schema.VerifyReflectionSchema());
}

TEST_F(ReflectionSchemaTest, verify_production_schema) {
  dumpsys::ReflectionSchema reflection_schema(bluetooth::dumpsys::GetBundledSchemaData());
  ASSERT_TRUE(reflection_schema.VerifyReflectionSchema());
}

}  // namespace testing
