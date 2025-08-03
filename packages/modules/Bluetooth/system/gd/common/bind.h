/*
 * Copyright 2019 The Android Open Source Project
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

#pragma once

#include "base/functional/bind.h"

namespace bluetooth {
namespace common {

using base::Bind;
using base::BindOnce;
using base::IgnoreResult;
using base::Owned;
using base::Passed;
using base::RetainedRef;
using base::Unretained;

template <typename T, typename Functor, typename... Args>
inline auto BindOn(T* obj, Functor&& functor, Args&&... args) {
  return common::Bind(std::forward<Functor>(functor), common::Unretained(obj), std::forward<Args>(args)...);
}

}  // namespace common
}  // namespace bluetooth
