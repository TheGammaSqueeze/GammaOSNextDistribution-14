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

#include "utils/resources.h"

#include "utils/base/logging.h"
#include "utils/zlib/buffer_generated.h"

namespace libtextclassifier3 {
namespace {
bool isWildcardMatch(const flatbuffers::String* left,
                     const std::string& right) {
  return (left == nullptr || right.empty());
}

bool isExactMatch(const flatbuffers::String* left, const std::string& right) {
  if (left == nullptr) {
    return right.empty();
  }
  return left->str() == right;
}

std::string NormalizeLanguageCode(const std::string& language_code) {
  if (language_code == "id") {
    return "in";
  } else if (language_code == "iw") {
    return "he";
  } else if (language_code == "no") {
    return "nb";
  } else if (language_code == "tl") {
    return "fil";
  }
  return language_code;
}

}  // namespace

int Resources::LocaleMatch(const Locale& locale,
                           const LanguageTag* entry_locale) const {
  int match = LOCALE_NO_MATCH;
  if (isExactMatch(entry_locale->language(),
                   NormalizeLanguageCode(locale.Language()))) {
    match |= LOCALE_LANGUAGE_MATCH;
  } else if (isWildcardMatch(entry_locale->language(), locale.Language())) {
    match |= LOCALE_LANGUAGE_WILDCARD_MATCH;
  }

  if (isExactMatch(entry_locale->script(), locale.Script())) {
    match |= LOCALE_SCRIPT_MATCH;
  } else if (isWildcardMatch(entry_locale->script(), locale.Script())) {
    match |= LOCALE_SCRIPT_WILDCARD_MATCH;
  }

  if (isExactMatch(entry_locale->region(), locale.Region())) {
    match |= LOCALE_REGION_MATCH;
  } else if (isWildcardMatch(entry_locale->region(), locale.Region())) {
    match |= LOCALE_REGION_WILDCARD_MATCH;
  }

  return match;
}

const ResourceEntry* Resources::FindResource(
    const StringPiece resource_name) const {
  if (resources_ == nullptr || resources_->resource_entry() == nullptr) {
    TC3_LOG(ERROR) << "No resources defined.";
    return nullptr;
  }
  const ResourceEntry* entry =
      resources_->resource_entry()->LookupByKey(resource_name.data());
  if (entry == nullptr) {
    TC3_LOG(ERROR) << "Resource " << resource_name.ToString() << " not found";
    return nullptr;
  }
  return entry;
}

int Resources::BestResourceForLocales(
    const ResourceEntry* resource, const std::vector<Locale>& locales) const {
  // Find best match based on locale.
  int resource_id = -1;
  int locale_match = LOCALE_NO_MATCH;
  const auto* resources = resource->resource();
  for (int user_locale = 0; user_locale < locales.size(); user_locale++) {
    if (!locales[user_locale].IsValid()) {
      continue;
    }
    for (int i = 0; i < resources->size(); i++) {
      for (const int locale_id : *resources->Get(i)->locale()) {
        const int candidate_match = LocaleMatch(
            locales[user_locale], resources_->locale()->Get(locale_id));

        // Only consider if at least the language matches.
        if ((candidate_match & LOCALE_LANGUAGE_MATCH) == 0 &&
            (candidate_match & LOCALE_LANGUAGE_WILDCARD_MATCH) == 0) {
          continue;
        }

        if (candidate_match > locale_match) {
          locale_match = candidate_match;
          resource_id = i;
        }
      }
    }

    // If the language matches exactly, we are already finished.
    // We found an exact language match.
    if (locale_match & LOCALE_LANGUAGE_MATCH) {
      return resource_id;
    }
  }
  return resource_id;
}

bool Resources::GetResourceContent(const std::vector<Locale>& locales,
                                   const StringPiece resource_name,
                                   std::string* result) const {
  const ResourceEntry* entry = FindResource(resource_name);
  if (entry == nullptr || entry->resource() == nullptr) {
    return false;
  }

  int resource_id = BestResourceForLocales(entry, locales);
  if (resource_id < 0) {
    return false;
  }
  const auto* resource = entry->resource()->Get(resource_id);
  if (resource->content() != nullptr) {
    *result = resource->content()->str();
    return true;
  }
  return false;
}

}  // namespace libtextclassifier3
