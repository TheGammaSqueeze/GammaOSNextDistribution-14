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

#include "profiling_info.h"

#include "art_method-inl.h"
#include "dex/dex_instruction.h"
#include "jit/jit.h"
#include "jit/jit_code_cache.h"
#include "scoped_thread_state_change-inl.h"
#include "thread.h"

namespace art HIDDEN {

ProfilingInfo::ProfilingInfo(ArtMethod* method,
                             const std::vector<uint32_t>& inline_cache_entries,
                             const std::vector<uint32_t>& branch_cache_entries)
      : baseline_hotness_count_(GetOptimizeThreshold()),
        method_(method),
        number_of_inline_caches_(inline_cache_entries.size()),
        number_of_branch_caches_(branch_cache_entries.size()),
        current_inline_uses_(0) {
  InlineCache* inline_caches = GetInlineCaches();
  memset(inline_caches, 0, number_of_inline_caches_ * sizeof(InlineCache));
  for (size_t i = 0; i < number_of_inline_caches_; ++i) {
    inline_caches[i].dex_pc_ = inline_cache_entries[i];
  }

  BranchCache* branch_caches = GetBranchCaches();
  memset(branch_caches, 0, number_of_branch_caches_ * sizeof(BranchCache));
  for (size_t i = 0; i < number_of_branch_caches_; ++i) {
    branch_caches[i].dex_pc_ = branch_cache_entries[i];
  }
}

uint16_t ProfilingInfo::GetOptimizeThreshold() {
  return Runtime::Current()->GetJITOptions()->GetOptimizeThreshold();
}

ProfilingInfo* ProfilingInfo::Create(Thread* self,
                                     ArtMethod* method,
                                     const std::vector<uint32_t>& inline_cache_entries) {
  // Walk over the dex instructions of the method and keep track of
  // instructions we are interested in profiling.
  DCHECK(!method->IsNative());

  std::vector<uint32_t> branch_cache_entries;
  for (const DexInstructionPcPair& inst : method->DexInstructions()) {
    switch (inst->Opcode()) {
      case Instruction::IF_EQ:
      case Instruction::IF_EQZ:
      case Instruction::IF_NE:
      case Instruction::IF_NEZ:
      case Instruction::IF_LT:
      case Instruction::IF_LTZ:
      case Instruction::IF_LE:
      case Instruction::IF_LEZ:
      case Instruction::IF_GT:
      case Instruction::IF_GTZ:
      case Instruction::IF_GE:
      case Instruction::IF_GEZ:
        branch_cache_entries.push_back(inst.DexPc());
        break;

      default:
        break;
    }
  }

  // We always create a `ProfilingInfo` object, even if there is no instruction we are
  // interested in. The JIT code cache internally uses it for hotness counter.

  // Allocate the `ProfilingInfo` object int the JIT's data space.
  jit::JitCodeCache* code_cache = Runtime::Current()->GetJit()->GetCodeCache();
  return code_cache->AddProfilingInfo(self, method, inline_cache_entries, branch_cache_entries);
}

InlineCache* ProfilingInfo::GetInlineCache(uint32_t dex_pc) {
  // TODO: binary search if array is too long.
  InlineCache* caches = GetInlineCaches();
  for (size_t i = 0; i < number_of_inline_caches_; ++i) {
    if (caches[i].dex_pc_ == dex_pc) {
      return &caches[i];
    }
  }
  return nullptr;
}

BranchCache* ProfilingInfo::GetBranchCache(uint32_t dex_pc) {
  // TODO: binary search if array is too long.
  BranchCache* caches = GetBranchCaches();
  for (size_t i = 0; i < number_of_branch_caches_; ++i) {
    if (caches[i].dex_pc_ == dex_pc) {
      return &caches[i];
    }
  }
  // Currently, only if instructions are profiled. The compiler will see other
  // branches, like switches.
  return nullptr;
}

void ProfilingInfo::AddInvokeInfo(uint32_t dex_pc, mirror::Class* cls) {
  InlineCache* cache = GetInlineCache(dex_pc);
  if (cache == nullptr) {
    return;
  }
  for (size_t i = 0; i < InlineCache::kIndividualCacheSize; ++i) {
    mirror::Class* existing = cache->classes_[i].Read<kWithoutReadBarrier>();
    mirror::Class* marked = ReadBarrier::IsMarked(existing);
    if (marked == cls) {
      // Receiver type is already in the cache, nothing else to do.
      return;
    } else if (marked == nullptr) {
      // Cache entry is empty, try to put `cls` in it.
      // Note: it's ok to spin on 'existing' here: if 'existing' is not null, that means
      // it is a stalled heap address, which will only be cleared during SweepSystemWeaks,
      // *after* this thread hits a suspend point.
      GcRoot<mirror::Class> expected_root(existing);
      GcRoot<mirror::Class> desired_root(cls);
      auto atomic_root = reinterpret_cast<Atomic<GcRoot<mirror::Class>>*>(&cache->classes_[i]);
      if (!atomic_root->CompareAndSetStrongSequentiallyConsistent(expected_root, desired_root)) {
        // Some other thread put a class in the cache, continue iteration starting at this
        // entry in case the entry contains `cls`.
        --i;
      } else {
        // We successfully set `cls`, just return.
        return;
      }
    }
  }
  // Unsuccessfull - cache is full, making it megamorphic. We do not DCHECK it though,
  // as the garbage collector might clear the entries concurrently.
}

ScopedProfilingInfoUse::ScopedProfilingInfoUse(jit::Jit* jit, ArtMethod* method, Thread* self)
    : jit_(jit),
      method_(method),
      self_(self),
      // Fetch the profiling info ahead of using it. If it's null when fetching,
      // we should not call JitCodeCache::DoneCompilerUse.
      profiling_info_(jit == nullptr
                          ? nullptr
                          : jit->GetCodeCache()->NotifyCompilerUse(method, self))
    {}

ScopedProfilingInfoUse::~ScopedProfilingInfoUse() {
  if (profiling_info_ != nullptr) {
    jit_->GetCodeCache()->DoneCompilerUse(method_, self_);
  }
}

uint32_t InlineCache::EncodeDexPc(ArtMethod* method,
                                  const std::vector<uint32_t>& dex_pcs,
                                  uint32_t inline_max_code_units) {
  if (kIsDebugBuild) {
    // Make sure `inline_max_code_units` is always the same.
    static uint32_t global_max_code_units = inline_max_code_units;
    CHECK_EQ(global_max_code_units, inline_max_code_units);
  }
  if (dex_pcs.size() - 1 > MaxDexPcEncodingDepth(method, inline_max_code_units)) {
    return -1;
  }
  uint32_t size = dex_pcs.size();
  uint32_t insns_size = method->DexInstructions().InsnsSizeInCodeUnits();

  uint32_t dex_pc = dex_pcs[size - 1];
  uint32_t shift = MinimumBitsToStore(insns_size - 1);
  for (uint32_t i = size - 1; i > 0; --i) {
    DCHECK_LT(shift, BitSizeOf<uint32_t>());
    dex_pc += ((dex_pcs[i - 1] + 1) << shift);
    shift += MinimumBitsToStore(inline_max_code_units);
  }
  return dex_pc;
}

uint32_t InlineCache::MaxDexPcEncodingDepth(ArtMethod* method, uint32_t inline_max_code_units) {
  uint32_t insns_size = method->DexInstructions().InsnsSizeInCodeUnits();
  uint32_t num_bits = MinimumBitsToStore(insns_size - 1);
  uint32_t depth = 0;
  do {
    depth++;
    num_bits += MinimumBitsToStore(inline_max_code_units);
  } while (num_bits <= BitSizeOf<uint32_t>());
  return depth - 1;
}

}  // namespace art
