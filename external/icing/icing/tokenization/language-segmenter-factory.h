// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef ICING_TOKENIZATION_LANGUAGE_SEGMENTER_FACTORY_H_
#define ICING_TOKENIZATION_LANGUAGE_SEGMENTER_FACTORY_H_

#include <memory>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/jni/jni-cache.h"
#include "icing/tokenization/language-segmenter.h"

namespace icing {
namespace lib {

namespace language_segmenter_factory {

struct SegmenterOptions {
  explicit SegmenterOptions(std::string locale,
                            const JniCache* jni_cache = nullptr)
      : locale(std::move(locale)), jni_cache(jni_cache) {}

  std::string locale;

  // Does not hold ownership.
  const JniCache* jni_cache;
};

// Creates a language segmenter with the given locale.
//
// Returns:
//   A LanguageSegmenter on success
//   INVALID_ARGUMENT if locale string is invalid
libtextclassifier3::StatusOr<std::unique_ptr<LanguageSegmenter>> Create(
    SegmenterOptions options);

}  // namespace language_segmenter_factory

}  // namespace lib
}  // namespace icing

#endif  // ICING_TOKENIZATION_LANGUAGE_SEGMENTER_FACTORY_H_
