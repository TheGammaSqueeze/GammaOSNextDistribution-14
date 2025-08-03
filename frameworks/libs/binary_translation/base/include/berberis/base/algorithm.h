/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef BERBERIS_BASE_ALGORITHM_H_
#define BERBERIS_BASE_ALGORITHM_H_

#include <algorithm>

namespace berberis {

//
// Non-const container versions.
//

template <class Container, class Value>
auto Find(Container& container, const Value& value) {
  return std::find(container.begin(), container.end(), value);
}

//
// Const container versions.
//

template <class Container, class Value>
auto Find(const Container& container, const Value& value) {
  return std::find(container.begin(), container.end(), value);
}

template <class Container, class Value>
bool Contains(const Container& container, const Value& value) {
  return Find(container, value) != container.end();
}

template <class Container, class Predicate>
auto FindIf(const Container& container, Predicate predicate) {
  return std::find_if(container.begin(), container.end(), predicate);
}

template <class Container, class Predicate>
bool ContainsIf(const Container& container, Predicate predicate) {
  return FindIf(container, predicate) != container.end();
}

}  // namespace berberis

#endif  // BERBERIS_BASE_ALGORITHM_H_
