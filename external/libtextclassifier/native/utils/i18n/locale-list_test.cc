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

#include "utils/i18n/locale-list.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"

using ::testing::SizeIs;

namespace libtextclassifier3 {
namespace {

TEST(LocaleTest, ParsedLocalesSanityCheck) {
  LocaleList locale_list = LocaleList::ParseFrom("en-US,zh-CN,ar,en");
  EXPECT_THAT(locale_list.GetLocales(), SizeIs(4));
  EXPECT_THAT(locale_list.GetLocaleTags(), SizeIs(4));
  EXPECT_EQ(locale_list.GetReferenceLocale(), "en-US");
}

TEST(LocaleTest, ParsedLocalesEmpty) {
  LocaleList locale_list = LocaleList::ParseFrom("");
  EXPECT_THAT(locale_list.GetLocales(), SizeIs(0));
  EXPECT_THAT(locale_list.GetLocaleTags(), SizeIs(0));
  EXPECT_EQ(locale_list.GetReferenceLocale(), "");
}

TEST(LocaleTest, ParsedLocalesIvalid) {
  LocaleList locale_list = LocaleList::ParseFrom("en,invalid");
  EXPECT_THAT(locale_list.GetLocales(), SizeIs(2));
  EXPECT_THAT(locale_list.GetLocaleTags(), SizeIs(2));
  EXPECT_EQ(locale_list.GetReferenceLocale(), "en");
  EXPECT_TRUE(locale_list.GetLocales()[0].IsValid());
  EXPECT_FALSE(locale_list.GetLocales()[1].IsValid());
}

}  // namespace
}  // namespace libtextclassifier3
