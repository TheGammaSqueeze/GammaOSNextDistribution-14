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

#ifndef LIBTEXTCLASSIFIER_UTILS_I18N_LOCALE_LIST_H_
#define LIBTEXTCLASSIFIER_UTILS_I18N_LOCALE_LIST_H_

#include <string>

#include "utils/i18n/locale.h"
#include "utils/strings/split.h"

namespace libtextclassifier3 {

// Parses and hold data about locales (combined by delimiter ',').
class LocaleList {
 public:
  // Constructs the
  //  - Collection of locale tag from local_tags
  //  - Collection of Locale objects from a valid BCP47 tag. (If the tag is
  //    invalid, an object is created but return false for IsInvalid() call.
  //  - Assigns the first parsed locale to reference_locale.
  static LocaleList ParseFrom(const std::string& locale_tags);

  std::vector<Locale> GetLocales() const { return locales_; }
  std::vector<StringPiece> GetLocaleTags() const { return split_locales_; }
  std::string GetReferenceLocale() const { return reference_locale_; }

 private:
  LocaleList(const std::vector<Locale>& locales,
             const std::vector<StringPiece>& split_locales,
             const StringPiece& reference_locale)
      : locales_(locales),
        split_locales_(split_locales),
        reference_locale_(reference_locale.ToString()) {}

  const std::vector<Locale> locales_;
  const std::vector<StringPiece> split_locales_;
  const std::string reference_locale_;
};
}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_UTILS_I18N_LOCALE_LIST_H_
