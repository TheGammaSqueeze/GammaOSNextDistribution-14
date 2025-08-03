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

#include <gtest/gtest.h>

#include "code_info_table_deduper.h"

#include "arch/instruction_set.h"
#include "base/malloc_arena_pool.h"
#include "base/scoped_arena_allocator.h"
#include "base/scoped_arena_containers.h"
#include "optimizing/stack_map_stream.h"

namespace art {
namespace linker {

TEST(StackMapTest, TestDedupeBitTables) {
  constexpr static uint32_t kPcAlign = GetInstructionSetInstructionAlignment(kRuntimeISA);
  using Kind = DexRegisterLocation::Kind;

  MallocArenaPool pool;
  ArenaStack arena_stack(&pool);
  ScopedArenaAllocator allocator(&arena_stack);
  StackMapStream stream(&allocator, kRuntimeISA);
  stream.BeginMethod(/* frame_size_in_bytes= */ 32,
                     /* core_spill_mask= */ 0,
                     /* fp_spill_mask= */ 0,
                     /* num_dex_registers= */ 2,
                     /* baseline= */ false,
                     /* debuggable= */ false);

  stream.BeginStackMapEntry(0, 64 * kPcAlign);
  stream.AddDexRegisterEntry(Kind::kInStack, 0);
  stream.AddDexRegisterEntry(Kind::kConstant, -2);
  stream.EndStackMapEntry();

  stream.EndMethod(64 * kPcAlign);
  ScopedArenaVector<uint8_t> memory = stream.Encode();

  std::vector<uint8_t> out;
  CodeInfoTableDeduper deduper(&out);
  size_t deduped1 = deduper.Dedupe(memory.data());
  size_t deduped2 = deduper.Dedupe(memory.data());

  for (size_t deduped : { deduped1, deduped2 }) {
    CodeInfo code_info(out.data() + deduped);
    ASSERT_EQ(1u, code_info.GetNumberOfStackMaps());

    StackMap stack_map = code_info.GetStackMapAt(0);
    ASSERT_TRUE(stack_map.Equals(code_info.GetStackMapForDexPc(0)));
    ASSERT_TRUE(stack_map.Equals(code_info.GetStackMapForNativePcOffset(64 * kPcAlign)));
    ASSERT_EQ(0u, stack_map.GetDexPc());
    ASSERT_EQ(64u * kPcAlign, stack_map.GetNativePcOffset(kRuntimeISA));

    ASSERT_TRUE(stack_map.HasDexRegisterMap());
    DexRegisterMap dex_register_map = code_info.GetDexRegisterMapOf(stack_map);

    ASSERT_EQ(Kind::kInStack, dex_register_map[0].GetKind());
    ASSERT_EQ(Kind::kConstant, dex_register_map[1].GetKind());
    ASSERT_EQ(0, dex_register_map[0].GetStackOffsetInBytes());
    ASSERT_EQ(-2, dex_register_map[1].GetConstant());
  }

  ASSERT_GT(memory.size() * 2, out.size());
}

}  //  namespace linker
}  //  namespace art
