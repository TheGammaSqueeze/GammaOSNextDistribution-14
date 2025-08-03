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

#include "utils/grammar/utils/locale-shard-map.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace libtextclassifier3::grammar {
namespace {

using ::testing::SizeIs;

TEST(LocaleShardMapTest, HandlesSimpleShard) {
  LocaleShardMap locale_shard_map = LocaleShardMap::CreateLocaleShardMap(
      {"ar-EG", "bn-BD", "cs-CZ", "da-DK", "de-DE", "en-US", "es-ES", "fi-FI",
       "fr-FR", "gu-IN", "id-ID", "it-IT", "ja-JP", "kn-IN", "ko-KR", "ml-IN",
       "mr-IN", "nl-NL", "no-NO", "pl-PL", "pt-BR", "ru-RU", "sv-SE", "ta-IN",
       "te-IN", "th-TH", "tr-TR", "uk-UA", "ur-PK", "vi-VN", "zh-TW"});

  EXPECT_EQ(locale_shard_map.GetNumberOfShards(), 31);
  for (int i = 0; i < 31; i++) {
    EXPECT_THAT(locale_shard_map.GetLocales(i), SizeIs(1));
  }
  EXPECT_EQ(locale_shard_map.GetLocales(0)[0], Locale::FromBCP47("ar-EG"));
  EXPECT_EQ(locale_shard_map.GetLocales(8)[0], Locale::FromBCP47("fr-FR"));
  EXPECT_EQ(locale_shard_map.GetLocales(16)[0], Locale::FromBCP47("mr-IN"));
  EXPECT_EQ(locale_shard_map.GetLocales(24)[0], Locale::FromBCP47("te-IN"));
  EXPECT_EQ(locale_shard_map.GetLocales(30)[0], Locale::FromBCP47("zh-TW"));
}

TEST(LocaleTagShardTest, HandlesWildCard) {
  LocaleShardMap locale_shard_map = LocaleShardMap::CreateLocaleShardMap({"*"});
  EXPECT_EQ(locale_shard_map.GetNumberOfShards(), 1);
  EXPECT_THAT(locale_shard_map.GetLocales(0), SizeIs(1));
}

TEST(LocaleTagShardTest, HandlesMultipleLocalePerShard) {
  LocaleShardMap locale_shard_map =
      LocaleShardMap::CreateLocaleShardMap({"ar-EG,bn-BD,cs-CZ", "en-*"});
  EXPECT_EQ(locale_shard_map.GetNumberOfShards(), 2);
  EXPECT_EQ(locale_shard_map.GetLocales(0)[0], Locale::FromBCP47("ar-EG"));
  EXPECT_EQ(locale_shard_map.GetLocales(0)[1], Locale::FromBCP47("bn-BD"));
  EXPECT_EQ(locale_shard_map.GetLocales(0)[2], Locale::FromBCP47("cs-CZ"));
  EXPECT_EQ(locale_shard_map.GetLocales(1)[0], Locale::FromBCP47("en"));

  EXPECT_EQ(locale_shard_map.GetShard("ar-EG,bn-BD,cs-CZ"), 0);
  EXPECT_EQ(locale_shard_map.GetShard("bn-BD,cs-CZ,ar-EG"), 0);
  EXPECT_EQ(locale_shard_map.GetShard("bn-BD,ar-EG,cs-CZ"), 0);
  EXPECT_EQ(locale_shard_map.GetShard("ar-EG,cs-CZ,bn-BD"), 0);
}

TEST(LocaleTagShardTest, HandlesEmptyLocaleTag) {
  LocaleShardMap locale_shard_map =
      LocaleShardMap::CreateLocaleShardMap({"", "en-US"});
  EXPECT_EQ(locale_shard_map.GetNumberOfShards(), 2);
  EXPECT_THAT(locale_shard_map.GetLocales(0), SizeIs(0));
  EXPECT_THAT(locale_shard_map.GetLocales(1), SizeIs(1));
  EXPECT_EQ(locale_shard_map.GetLocales(1)[0], Locale::FromBCP47("en-US"));
}

}  // namespace
}  // namespace libtextclassifier3::grammar
