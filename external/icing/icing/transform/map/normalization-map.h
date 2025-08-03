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

#ifndef ICING_TRANSFORM_MAP_NORMALIZATION_MAP_H_
#define ICING_TRANSFORM_MAP_NORMALIZATION_MAP_H_

#include <unordered_map>

namespace icing {
namespace lib {

// Returns a map containing normalization mappings. A mapping (A -> B) means
// that we'll transform every character 'A' into 'B'. See normalization-map.cc
// for mapping details.
const std::unordered_map<char16_t, char16_t>* GetNormalizationMap();

}  // namespace lib
}  // namespace icing

#endif  // ICING_TRANSFORM_MAP_NORMALIZATION_MAP_H_
