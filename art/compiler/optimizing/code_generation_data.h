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

#ifndef ART_COMPILER_OPTIMIZING_CODE_GENERATION_DATA_H_
#define ART_COMPILER_OPTIMIZING_CODE_GENERATION_DATA_H_

#include <memory>

#include "arch/instruction_set.h"
#include "base/scoped_arena_allocator.h"
#include "base/scoped_arena_containers.h"
#include "code_generator.h"
#include "dex/string_reference.h"
#include "dex/type_reference.h"
#include "handle.h"
#include "mirror/class.h"
#include "mirror/object.h"
#include "mirror/string.h"
#include "stack_map_stream.h"

namespace art HIDDEN {

class CodeGenerationData : public DeletableArenaObject<kArenaAllocCodeGenerator> {
 public:
  static std::unique_ptr<CodeGenerationData> Create(ArenaStack* arena_stack,
                                                    InstructionSet instruction_set) {
    ScopedArenaAllocator allocator(arena_stack);
    void* memory = allocator.Alloc<CodeGenerationData>(kArenaAllocCodeGenerator);
    return std::unique_ptr<CodeGenerationData>(
        ::new (memory) CodeGenerationData(std::move(allocator), instruction_set));
  }

  ScopedArenaAllocator* GetScopedAllocator() {
    return &allocator_;
  }

  void AddSlowPath(SlowPathCode* slow_path) {
    slow_paths_.emplace_back(std::unique_ptr<SlowPathCode>(slow_path));
  }

  ArrayRef<const std::unique_ptr<SlowPathCode>> GetSlowPaths() const {
    return ArrayRef<const std::unique_ptr<SlowPathCode>>(slow_paths_);
  }

  StackMapStream* GetStackMapStream() { return &stack_map_stream_; }

  void ReserveJitStringRoot(StringReference string_reference, Handle<mirror::String> string) {
    jit_string_roots_.Overwrite(string_reference,
                                reinterpret_cast64<uint64_t>(string.GetReference()));
  }

  uint64_t GetJitStringRootIndex(StringReference string_reference) const {
    return jit_string_roots_.Get(string_reference);
  }

  size_t GetNumberOfJitStringRoots() const {
    return jit_string_roots_.size();
  }

  void ReserveJitClassRoot(TypeReference type_reference, Handle<mirror::Class> klass) {
    jit_class_roots_.Overwrite(type_reference, reinterpret_cast64<uint64_t>(klass.GetReference()));
  }

  uint64_t GetJitClassRootIndex(TypeReference type_reference) const {
    return jit_class_roots_.Get(type_reference);
  }

  size_t GetNumberOfJitClassRoots() const {
    return jit_class_roots_.size();
  }

  size_t GetNumberOfJitRoots() const {
    return GetNumberOfJitStringRoots() + GetNumberOfJitClassRoots();
  }

  void EmitJitRoots(/*out*/std::vector<Handle<mirror::Object>>* roots)
      REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  CodeGenerationData(ScopedArenaAllocator&& allocator, InstructionSet instruction_set)
      : allocator_(std::move(allocator)),
        stack_map_stream_(&allocator_, instruction_set),
        slow_paths_(allocator_.Adapter(kArenaAllocCodeGenerator)),
        jit_string_roots_(StringReferenceValueComparator(),
                          allocator_.Adapter(kArenaAllocCodeGenerator)),
        jit_class_roots_(TypeReferenceValueComparator(),
                         allocator_.Adapter(kArenaAllocCodeGenerator)) {
    slow_paths_.reserve(kDefaultSlowPathsCapacity);
  }

  static constexpr size_t kDefaultSlowPathsCapacity = 8;

  ScopedArenaAllocator allocator_;
  StackMapStream stack_map_stream_;
  ScopedArenaVector<std::unique_ptr<SlowPathCode>> slow_paths_;

  // Maps a StringReference (dex_file, string_index) to the index in the literal table.
  // Entries are initially added with a pointer in the handle zone, and `EmitJitRoots`
  // will compute all the indices.
  ScopedArenaSafeMap<StringReference, uint64_t, StringReferenceValueComparator> jit_string_roots_;

  // Maps a ClassReference (dex_file, type_index) to the index in the literal table.
  // Entries are initially added with a pointer in the handle zone, and `EmitJitRoots`
  // will compute all the indices.
  ScopedArenaSafeMap<TypeReference, uint64_t, TypeReferenceValueComparator> jit_class_roots_;
};

}  // namespace art

#endif  // ART_COMPILER_OPTIMIZING_CODE_GENERATION_DATA_H_
