/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef BERBERIS_RUNTIME_PRIMITIVES_HOST_STACK_H_
#define BERBERIS_RUNTIME_PRIMITIVES_HOST_STACK_H_

#include <cstddef>

#include "berberis/base/mmap.h"

namespace berberis {

constexpr size_t GetStackSizeForTranslation() {
  // Ensure thread stack is big enough for translation.
  // TODO(khim): review this when decoder gets refactored or when translation
  // goes to a separate thread.
  // TODO(levarum): Maybe better solution is required (b/30124680).
  return AlignUpPageSize(16u * 1024u);
}

inline void* GetStackTop(ScopedMmap* stack) {
  uintptr_t stack_top = reinterpret_cast<uintptr_t>(stack->data()) + stack->size() - 1;
  // We assume there is no ABI with stack alignment greater than 64.
  return reinterpret_cast<void*>(stack_top - (stack_top % 64));
}

}  // namespace berberis

#endif  // BERBERIS_RUNTIME_PRIMITIVES_HOST_STACK_H_
