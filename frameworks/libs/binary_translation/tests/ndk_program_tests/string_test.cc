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

#include <string.h>

TEST(String, Strcmp) {
  char s0[] = "aaaaa";
  char s1[] = "aaaaa";
  char s2[] = "aaaab";

  EXPECT_EQ(strcmp(s0, s1), 0);
  EXPECT_LT(strcmp(s1, s2), 0);
  EXPECT_GT(strcmp(s2, s1), 0);
}

TEST(String, Strdup) {
  char s[] = "test string";
  char* s_dup = strdup(s);
  EXPECT_NE(s_dup, s);
  EXPECT_EQ(strcmp(s, s_dup), 0);
  free(s_dup);
}

TEST(String, Strsep) {
  char* null_string = nullptr;
  char* s1 = strsep(&null_string, " ");
  EXPECT_EQ(s1, nullptr);

  const char test_string[] = "Lorem ipsum \ndolor sit\tamet";
  const char* tokens[] = {"Lorem", "ipsum", "", "dolor", "sit", "amet"};
  {
    char* test = strdup(test_string);
    char* s2 = strsep(&test, "Z");
    EXPECT_STREQ(s2, test_string);
    free(test);
  }
  {
    char* test = strdup(test_string);
    for (size_t i = 0; i < sizeof(tokens) / sizeof(tokens[0]); ++i) {
      char* s2 = strsep(&test, " \n\t");
      EXPECT_STREQ(s2, tokens[i]);
    }
    free(test);
  }
}
