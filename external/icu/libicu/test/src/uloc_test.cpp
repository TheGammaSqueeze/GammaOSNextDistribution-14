/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <unordered_set>
#include <vector>
#include <unicode/uloc.h>

bool is_enUS(const char* s) {
  return s != nullptr && std::strcmp("en_US", s) == 0;
}

TEST(Icu4cLocaleTest, test_uloc_getAvailable) {
  int32_t count = uloc_countAvailable();
  ASSERT_GT(uloc_countAvailable(), 0);

  std::vector<const char*> locales(count);
  for(int i = 0; i < count; i++) {
    locales.push_back(uloc_getAvailable(i));
  }

  // On Android, uloc_getAvailable must contain en_US.
  ASSERT_NE(locales.end(), std::find_if(locales.begin(), locales.end(), is_enUS));
}

TEST(Icu4cLocaleTest, test_uloc_getCountry) {
  char country[4];
  UErrorCode error;
  uloc_getCountry("en_US", country, sizeof(country), &error);
  ASSERT_EQ(U_ZERO_ERROR, error);
  EXPECT_STREQ("US", country);
}

TEST(Icu4cLocaleTest, test_uloc_getISOLanguages) {
  std::unordered_set<std::string> languages;
  const char* const* list_ptr = uloc_getISOLanguages();
  while(*list_ptr != 0) {
    languages.insert(*list_ptr);
    list_ptr++;
  }

  EXPECT_TRUE(languages.find("en") != languages.end()) << "language en must be supported.";
}

TEST(Icu4cLocaleTest, test_uloc_getISOCountries) {
  std::unordered_set<std::string> countries;
  const char* const* list_ptr = uloc_getISOCountries();
  while(*list_ptr != 0) {
    countries.insert(*list_ptr);
    list_ptr++;
  }

  EXPECT_TRUE(countries.find("US") != countries.end()) << "country US must be supported.";
}
