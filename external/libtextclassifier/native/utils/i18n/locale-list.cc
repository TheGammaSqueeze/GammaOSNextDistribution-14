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

#include <string>

namespace libtextclassifier3 {

LocaleList LocaleList::ParseFrom(const std::string& locale_tags) {
  std::vector<StringPiece> split_locales = strings::Split(locale_tags, ',');
  std::string reference_locale;
  if (!split_locales.empty()) {
    // Assigns the first parsed locale to reference_locale.
    reference_locale = split_locales[0].ToString();
  } else {
    reference_locale = "";
  }
  std::vector<Locale> locales;
  for (const StringPiece& locale_str : split_locales) {
    const Locale locale = Locale::FromBCP47(locale_str.ToString());
    if (!locale.IsValid()) {
      TC3_LOG(WARNING) << "Failed to parse the detected_text_language_tag: "
                       << locale_str.ToString();
    }
    locales.push_back(locale);
  }
  return LocaleList(locales, split_locales, reference_locale);
}

}  // namespace libtextclassifier3
