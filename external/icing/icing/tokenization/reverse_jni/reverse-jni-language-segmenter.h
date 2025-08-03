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

#ifndef ICING_TOKENIZATION_REVERSE_JNI_REVERSE_JNI_LANGUAGE_SEGMENTER_H_
#define ICING_TOKENIZATION_REVERSE_JNI_REVERSE_JNI_LANGUAGE_SEGMENTER_H_

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/jni/jni-cache.h"
#include "icing/tokenization/language-segmenter.h"

namespace icing {
namespace lib {

class ReverseJniLanguageSegmenter : public LanguageSegmenter {
 public:
  ReverseJniLanguageSegmenter(std::string locale, const JniCache* jni_cache)
      : locale_(std::move(locale)), jni_cache_(jni_cache) {}

  libtextclassifier3::StatusOr<std::unique_ptr<LanguageSegmenter::Iterator>>
  Segment(std::string_view text) const override;

  libtextclassifier3::StatusOr<std::vector<std::string_view>> GetAllTerms(
      std::string_view text) const override;

 private:
  std::string locale_;

  const JniCache* jni_cache_;  // does not own!
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TOKENIZATION_REVERSE_JNI_REVERSE_JNI_LANGUAGE_SEGMENTER_H_
