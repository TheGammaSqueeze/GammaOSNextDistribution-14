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

#ifndef LIBTEXTCLASSIFIER_UTILS_SENTENCEPIECE_TEST_UTILS_H_
#define LIBTEXTCLASSIFIER_UTILS_SENTENCEPIECE_TEST_UTILS_H_

#include <string>
#include <vector>

#include "utils/sentencepiece/normalizer.h"
#include "utils/strings/stringpiece.h"

namespace libtextclassifier3 {

SentencePieceNormalizer NormalizerFromSpec(StringPiece spec,
                                           bool add_dummy_prefix,
                                           bool remove_extra_whitespaces,
                                           bool escape_whitespaces);

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_UTILS_SENTENCEPIECE_TEST_UTILS_H_
