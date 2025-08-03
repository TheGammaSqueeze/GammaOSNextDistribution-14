/* Copyright (C) 2023 The Android Open Source Project
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

#include <android-base/test_utils.h>
#include <gtest/gtest.h>

#include "Common.h"

TEST(SyspropTest, CamelToSnakeTest) {
  EXPECT_EQ(CamelCaseToSnakeCase("CurrentAPIVersion"), "current_api_version");
  EXPECT_EQ(CamelCaseToSnakeCase("CurrentAPI"), "current_api");
  EXPECT_EQ(CamelCaseToSnakeCase("APIVersion"), "api_version");
  EXPECT_EQ(CamelCaseToSnakeCase("API"), "api");
  EXPECT_EQ(CamelCaseToSnakeCase("API2"), "api2");
  EXPECT_EQ(CamelCaseToSnakeCase("SomeRandomCamelCase"),
            "some_random_camel_case");
  EXPECT_EQ(CamelCaseToSnakeCase("snake_case"), "snake_case");
  EXPECT_EQ(CamelCaseToSnakeCase("Strange_Camel_Case"), "strange_camel_case");
}

TEST(SyspropTest, SnakeToCamelTest) {
  EXPECT_EQ(SnakeCaseToCamelCase("current_api_version"), "CurrentApiVersion");
  EXPECT_EQ(SnakeCaseToCamelCase("random"), "Random");
  EXPECT_EQ(SnakeCaseToCamelCase("SCREAMING_SNAKE_CASE_100"),
            "ScreamingSnakeCase100");
  EXPECT_EQ(SnakeCaseToCamelCase("double__underscore"), "DoubleUnderscore");
}