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

#ifndef LIBTEXTCLASSIFIER_UTILS_GRAMMAR_UTILS_LOCALE_SHARD_MAP_H_
#define LIBTEXTCLASSIFIER_UTILS_GRAMMAR_UTILS_LOCALE_SHARD_MAP_H_

#include <string>
#include <unordered_map>
#include <vector>

#include "utils/grammar/types.h"
#include "utils/i18n/locale-list.h"
#include "utils/i18n/locale.h"
#include "utils/optional.h"

namespace libtextclassifier3::grammar {

// Grammar rules are associated with Locale which serve as a filter during rule
// application. The class holds shard’s information for Locale which is used
// when the Aqua rules are compiled into internal rules.proto flatbuffer.
class LocaleShardMap {
 public:
  static LocaleShardMap CreateLocaleShardMap(
      const std::vector<std::string>& locale_tags,
      const bool include_any_match_language_tag_shard = false);

  std::vector<Locale> GetLocales(const int shard) const;

  int GetShard(const std::vector<Locale> locales) const;
  int GetShard(const std::string& locale_tags) const;

  int GetNumberOfShards() const;

 private:
  explicit LocaleShardMap() {}
  void AddLocalTags(const std::string& locale_tag);

  std::unordered_map<int, std::vector<Locale>> shard_to_locale_data_;
};

}  // namespace libtextclassifier3::grammar

#endif  // LIBTEXTCLASSIFIER_UTILS_GRAMMAR_UTILS_LOCALE_SHARD_MAP_H_
