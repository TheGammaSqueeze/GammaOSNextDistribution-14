/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "gtest/gtest.h"

#include "chre/util/nanoapp/string.h"

using ::chre::copyString;

TEST(StringDeathTest, InvalidInput) {
  char destination[100];
  ASSERT_DEATH(copyString(nullptr, nullptr, 0), ".*");
  ASSERT_DEATH(copyString(nullptr, destination, 1), ".*");
  ASSERT_DEATH(copyString(destination, nullptr, 2), ".*");
}

TEST(String, ZeroCharsToCopy) {
  const char *source = "hello world";
  constexpr size_t destinationLength = 100;
  char destination[destinationLength];
  char fillValue = 123;

  memset(destination, fillValue, destinationLength);

  copyString(destination, source, 0);
  for (size_t i = 0; i < destinationLength; ++i) {
    ASSERT_EQ(destination[i], fillValue);
  }
}

TEST(String, EmptyStringPadsWithZeroes) {
  const char *source = "";
  constexpr size_t destinationLength = 100;
  char destination[destinationLength];
  char fillValue = 123;

  memset(destination, fillValue, destinationLength);

  copyString(destination, source, destinationLength);
  for (size_t i = 0; i < destinationLength; ++i) {
    ASSERT_EQ(destination[i], 0);
  }
}

TEST(String, NormalCopyOneChar) {
  const char *source = "hello world";
  constexpr size_t destinationLength = 100;
  char destination[destinationLength];
  char fillValue = 123;

  memset(destination, fillValue, destinationLength);

  copyString(destination, source, 2);  // one char + '\0'
  ASSERT_EQ(destination[0], source[0]);
  ASSERT_EQ(destination[1], 0);
  for (size_t i = 2; i < destinationLength; ++i) {
    ASSERT_EQ(destination[i], fillValue);
  }
}

TEST(String, NormalCopyAllChars) {
  const char *source = "hello world";
  constexpr size_t sourceLength = 11;
  constexpr size_t destinationLength = 100;
  char destination[destinationLength];
  char fillValue = 123;

  memset(destination, fillValue, destinationLength);

  copyString(destination, source, sourceLength + 1);  // account for '\0'
  size_t i = 0;
  for (; i < sourceLength; ++i) {
    ASSERT_EQ(destination[i], source[i]);
  }

  ASSERT_EQ(destination[i], 0);
  ++i;

  for (; i < destinationLength; ++i) {
    ASSERT_EQ(destination[i], fillValue);
  }
}

TEST(String, NormalCopyGreaterThanSourceLength) {
  const char *source = "hello world";
  constexpr size_t sourceLength = 11;
  constexpr size_t destinationLength = 100;
  char destination[destinationLength];
  char fillValue = 123;

  memset(destination, fillValue, destinationLength);

  copyString(destination, source, destinationLength);
  size_t i = 0;
  for (; i < sourceLength; ++i) {
    ASSERT_EQ(destination[i], source[i]);
  }

  for (; i < destinationLength; ++i) {
    ASSERT_EQ(destination[i], 0);
  }
}

TEST(String, NormalCopyLessThanSourceLength) {
  const char *source = "hello world";
  constexpr size_t sourceLength = 11;
  constexpr size_t destinationLength = 5;
  char destination[destinationLength];
  char fillValue = 123;

  memset(destination, fillValue, destinationLength);

  copyString(destination, source, destinationLength);
  size_t i = 0;
  for (; i < destinationLength - 1; ++i) {
    ASSERT_EQ(destination[i], source[i]);
  }
  ASSERT_EQ(destination[i], 0);
}
