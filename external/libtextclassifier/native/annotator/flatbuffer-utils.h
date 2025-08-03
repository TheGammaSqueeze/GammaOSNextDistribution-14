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

// Utility functions for working with FlatBuffers in the annotator model.

#ifndef LIBTEXTCLASSIFIER_ANNOTATOR_FLATBUFFER_UTILS_H_
#define LIBTEXTCLASSIFIER_ANNOTATOR_FLATBUFFER_UTILS_H_

#include <string>

#include "annotator/model_generated.h"
#include "annotator/types.h"

namespace libtextclassifier3 {

// Resolves field lookups by name to the concrete field offsets in the regex
// rules of the model.
bool SwapFieldNamesForOffsetsInPath(ModelT* model);

// Same as above but for a serialized model.
std::string SwapFieldNamesForOffsetsInPathInSerializedModel(
    const std::string& model);

std::string CreateDatetimeSerializedEntityData(
    const DatetimeParseResult& parse_result);

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ANNOTATOR_FLATBUFFER_UTILS_H_
