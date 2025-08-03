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

#include "utils/grammar/rules-utils.h"

namespace libtextclassifier3::grammar {

std::vector<std::vector<Locale>> ParseRulesLocales(const RulesSet* rules) {
  if (rules == nullptr || rules->rules() == nullptr) {
    return {};
  }
  std::vector<std::vector<Locale>> locales(rules->rules()->size());
  for (int i = 0; i < rules->rules()->size(); i++) {
    const grammar::RulesSet_::Rules* rules_shard = rules->rules()->Get(i);
    if (rules_shard->locale() == nullptr) {
      continue;
    }
    for (const LanguageTag* tag : *rules_shard->locale()) {
      locales[i].push_back(Locale::FromLanguageTag(tag));
    }
  }
  return locales;
}

std::vector<const grammar::RulesSet_::Rules*> SelectLocaleMatchingShards(
    const RulesSet* rules,
    const std::vector<std::vector<Locale>>& shard_locales,
    const std::vector<Locale>& locales) {
  std::vector<const grammar::RulesSet_::Rules*> shards;
  if (rules->rules() == nullptr) {
    return shards;
  }
  for (int i = 0; i < shard_locales.size(); i++) {
    if (shard_locales[i].empty() ||
        Locale::IsAnyLocaleSupported(locales,
                                     /*supported_locales=*/shard_locales[i],
                                     /*default_value=*/false)) {
      shards.push_back(rules->rules()->Get(i));
    }
  }
  return shards;
}

}  // namespace libtextclassifier3::grammar
