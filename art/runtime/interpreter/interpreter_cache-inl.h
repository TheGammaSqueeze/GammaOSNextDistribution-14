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

#ifndef ART_RUNTIME_INTERPRETER_INTERPRETER_CACHE_INL_H_
#define ART_RUNTIME_INTERPRETER_INTERPRETER_CACHE_INL_H_

#include "interpreter_cache.h"

#include "thread.h"

namespace art HIDDEN {

inline bool InterpreterCache::Get(Thread* self, const void* key, /* out */ size_t* value) {
  DCHECK(self->GetInterpreterCache() == this) << "Must be called from owning thread";
  Entry& entry = data_[IndexOf(key)];
  if (LIKELY(entry.first == key)) {
    *value = entry.second;
    return true;
  }
  return false;
}

inline void InterpreterCache::Set(Thread* self, const void* key, size_t value) {
  DCHECK(self->GetInterpreterCache() == this) << "Must be called from owning thread";
  // Simple store works here as the cache is always read/written by the owning
  // thread only (or in a stop-the-world pause).
  data_[IndexOf(key)] = Entry{key, value};
}

}  // namespace art

#endif  // ART_RUNTIME_INTERPRETER_INTERPRETER_CACHE_INL_H_
