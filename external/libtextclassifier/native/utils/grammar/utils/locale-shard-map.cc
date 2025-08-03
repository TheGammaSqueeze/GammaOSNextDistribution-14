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

#include <algorithm>
#include <string>
#include <utility>
#include <vector>

#include "utils/i18n/locale-list.h"
#include "utils/i18n/locale.h"
#include "utils/strings/append.h"

namespace libtextclassifier3::grammar {
namespace {

// For latency we put sub-rules on the first shard which must be any match
// i.e. '*' rules are always included while parsing the tree as it is only
// on shard one hence will be deduped correctly.
const char kAnyMatchLanguageTag[] = "*";

std::vector<Locale> LocaleTagsToLocaleList(const std::string& locale_tags) {
  std::vector<Locale> locale_list;
  for (const Locale& locale : LocaleList::ParseFrom(locale_tags).GetLocales()) {
    if (locale.IsValid()) {
      locale_list.emplace_back(locale);
    }
  }
  std::stable_sort(locale_list.begin(), locale_list.end(),
                   [](const Locale& a, const Locale& b) { return a < b; });
  return locale_list;
}

}  // namespace

LocaleShardMap LocaleShardMap::CreateLocaleShardMap(
    const std::vector<std::string>& locale_tags,
    const bool include_any_match_language_tag_shard) {
  LocaleShardMap locale_shard_map;

  if (include_any_match_language_tag_shard) {
    // Make sure "*" [AnyMatch] is at shard 0.
    locale_shard_map.AddLocalTags(kAnyMatchLanguageTag);
  }

  for (const std::string& locale_tag : locale_tags) {
    locale_shard_map.AddLocalTags(locale_tag);
  }
  return locale_shard_map;
}

std::vector<Locale> LocaleShardMap::GetLocales(const int shard) const {
  auto locale_it = shard_to_locale_data_.find(shard);
  if (locale_it != shard_to_locale_data_.end()) {
    return locale_it->second;
  }
  return std::vector<Locale>();
}

int LocaleShardMap::GetNumberOfShards() const {
  return shard_to_locale_data_.size();
}

int LocaleShardMap::GetShard(const std::vector<Locale> locales) const {
  for (const auto& [shard, locale_list] : shard_to_locale_data_) {
    if (std::equal(locales.begin(), locales.end(), locale_list.begin())) {
      return shard;
    }
  }
  return 0;
}

int LocaleShardMap::GetShard(const std::string& locale_tags) const {
  std::vector<Locale> locale_list = LocaleTagsToLocaleList(locale_tags);
  return GetShard(locale_list);
}

void LocaleShardMap::AddLocalTags(const std::string& locale_tags) {
  std::vector<Locale> locale_list = LocaleTagsToLocaleList(locale_tags);
  int shard_id = shard_to_locale_data_.size();
  shard_to_locale_data_.insert({shard_id, locale_list});
}

}  // namespace libtextclassifier3::grammar
