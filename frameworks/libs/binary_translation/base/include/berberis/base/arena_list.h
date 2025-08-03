/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef BERBERIS_BASE_ARENA_LIST_H_
#define BERBERIS_BASE_ARENA_LIST_H_

#include <list>

#include "berberis/base/arena_alloc.h"

namespace berberis {

template <class T>
using ArenaList = std::list<T, ArenaAllocator<T> >;

}  // namespace berberis

#endif  // BERBERIS_BASE_ARENA_LIST_H_
