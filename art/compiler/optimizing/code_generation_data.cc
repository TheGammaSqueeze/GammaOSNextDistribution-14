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

#include "class_linker.h"
#include "code_generation_data.h"
#include "code_generator.h"
#include "intern_table.h"
#include "mirror/object-inl.h"
#include "runtime.h"

namespace art HIDDEN {

void CodeGenerationData::EmitJitRoots(
    /*out*/std::vector<Handle<mirror::Object>>* roots) {
  DCHECK(roots->empty());
  roots->reserve(GetNumberOfJitRoots());
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  size_t index = 0;
  for (auto& entry : jit_string_roots_) {
    // Update the `roots` with the string, and replace the address temporarily
    // stored to the index in the table.
    uint64_t address = entry.second;
    roots->emplace_back(reinterpret_cast<StackReference<mirror::Object>*>(address));
    DCHECK(roots->back() != nullptr);
    DCHECK(roots->back()->IsString());
    entry.second = index;
    // Ensure the string is strongly interned. This is a requirement on how the JIT
    // handles strings. b/32995596
    class_linker->GetInternTable()->InternStrong(roots->back()->AsString());
    ++index;
  }
  for (auto& entry : jit_class_roots_) {
    // Update the `roots` with the class, and replace the address temporarily
    // stored to the index in the table.
    uint64_t address = entry.second;
    roots->emplace_back(reinterpret_cast<StackReference<mirror::Object>*>(address));
    DCHECK(roots->back() != nullptr);
    DCHECK(roots->back()->IsClass());
    entry.second = index;
    ++index;
  }
}

}  // namespace art
