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

#ifndef ART_RUNTIME_JIT_PROFILING_INFO_H_
#define ART_RUNTIME_JIT_PROFILING_INFO_H_

#include <vector>

#include "base/macros.h"
#include "base/value_object.h"
#include "gc_root.h"
#include "interpreter/mterp/nterp.h"
#include "offsets.h"

namespace art HIDDEN {

class ArtMethod;
class CompilerOptions;
class ProfilingInfo;

namespace jit {
class Jit;
class JitCodeCache;
}  // namespace jit

namespace mirror {
class Class;
}  // namespace mirror

// Structure to store the classes seen at runtime for a specific instruction.
// Once the classes_ array is full, we consider the INVOKE to be megamorphic.
class InlineCache {
 public:
  // This is hard coded in the assembly stub art_quick_update_inline_cache.
  static constexpr uint8_t kIndividualCacheSize = 5;

  static constexpr MemberOffset ClassesOffset() {
    return MemberOffset(OFFSETOF_MEMBER(InlineCache, classes_));
  }

  // Encode the list of `dex_pcs` to fit into an uint32_t.
  static uint32_t EncodeDexPc(ArtMethod* method,
                              const std::vector<uint32_t>& dex_pcs,
                              uint32_t inline_max_code_units)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Return the maximum inlining depth that we support to encode a list of dex
  // pcs.
  static uint32_t MaxDexPcEncodingDepth(ArtMethod* method,
                                        uint32_t inline_max_code_units)
      REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  uint32_t dex_pc_;
  GcRoot<mirror::Class> classes_[kIndividualCacheSize];

  friend class jit::JitCodeCache;
  friend class ProfilingInfo;

  DISALLOW_COPY_AND_ASSIGN(InlineCache);
};

class BranchCache {
 public:
  static constexpr MemberOffset FalseOffset() {
    return MemberOffset(OFFSETOF_MEMBER(BranchCache, false_));
  }

  static constexpr MemberOffset TrueOffset() {
    return MemberOffset(OFFSETOF_MEMBER(BranchCache, true_));
  }

  uint32_t GetExecutionCount() const {
    return true_ + false_;
  }

  uint16_t GetTrue() const {
    return true_;
  }

  uint16_t GetFalse() const {
    return false_;
  }

 private:
  uint32_t dex_pc_;
  uint16_t false_;
  uint16_t true_;

  friend class ProfilingInfo;

  DISALLOW_COPY_AND_ASSIGN(BranchCache);
};

/**
 * Profiling info for a method, created and filled by the interpreter once the
 * method is warm, and used by the compiler to drive optimizations.
 */
class ProfilingInfo {
 public:
  // Create a ProfilingInfo for 'method'.
  EXPORT static ProfilingInfo* Create(Thread* self,
                                      ArtMethod* method,
                                      const std::vector<uint32_t>& inline_cache_entries)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Add information from an executed INVOKE instruction to the profile.
  void AddInvokeInfo(uint32_t dex_pc, mirror::Class* cls)
      // Method should not be interruptible, as it manipulates the ProfilingInfo
      // which can be concurrently collected.
      REQUIRES(Roles::uninterruptible_)
      REQUIRES_SHARED(Locks::mutator_lock_);

  ArtMethod* GetMethod() const {
    return method_;
  }

  InlineCache* GetInlineCache(uint32_t dex_pc);
  BranchCache* GetBranchCache(uint32_t dex_pc);

  InlineCache* GetInlineCaches() {
    return reinterpret_cast<InlineCache*>(
        reinterpret_cast<uintptr_t>(this) + sizeof(ProfilingInfo));
  }
  BranchCache* GetBranchCaches() {
    return reinterpret_cast<BranchCache*>(
        reinterpret_cast<uintptr_t>(this) + sizeof(ProfilingInfo) +
        number_of_inline_caches_ * sizeof(InlineCache));
  }

  static size_t ComputeSize(uint32_t number_of_inline_caches, uint32_t number_of_branch_caches) {
    return sizeof(ProfilingInfo) +
        number_of_inline_caches * sizeof(InlineCache) +
        number_of_branch_caches * sizeof(BranchCache);
  }

  // Increments the number of times this method is currently being inlined.
  // Returns whether it was successful, that is it could increment without
  // overflowing.
  bool IncrementInlineUse() {
    if (current_inline_uses_ == std::numeric_limits<uint16_t>::max()) {
      return false;
    }
    current_inline_uses_++;
    return true;
  }

  void DecrementInlineUse() {
    DCHECK_GT(current_inline_uses_, 0);
    current_inline_uses_--;
  }

  bool IsInUseByCompiler() const {
    return current_inline_uses_ > 0;
  }

  static constexpr MemberOffset BaselineHotnessCountOffset() {
    return MemberOffset(OFFSETOF_MEMBER(ProfilingInfo, baseline_hotness_count_));
  }

  void ResetCounter() {
    baseline_hotness_count_ = GetOptimizeThreshold();
  }

  bool CounterHasChanged() const {
    return baseline_hotness_count_ != GetOptimizeThreshold();
  }

  uint16_t GetBaselineHotnessCount() const {
    return baseline_hotness_count_;
  }

  static uint16_t GetOptimizeThreshold();

 private:
  ProfilingInfo(ArtMethod* method,
                const std::vector<uint32_t>& inline_cache_entries,
                const std::vector<uint32_t>& branch_cache_entries);

  // Hotness count for methods compiled with the JIT baseline compiler. Once
  // a threshold is hit (currentily the maximum value of uint16_t), we will
  // JIT compile optimized the method.
  uint16_t baseline_hotness_count_;

  // Method this profiling info is for.
  // Not 'const' as JVMTI introduces obsolete methods that we implement by creating new ArtMethods.
  // See JitCodeCache::MoveObsoleteMethod.
  ArtMethod* method_;

  // Number of invokes we are profiling in the ArtMethod.
  const uint32_t number_of_inline_caches_;

  // Number of branches we are profiling in the ArtMethod.
  const uint32_t number_of_branch_caches_;

  // When the compiler inlines the method associated to this ProfilingInfo,
  // it updates this counter so that the GC does not try to clear the inline caches.
  uint16_t current_inline_uses_;

  // Memory following the object:
  // - Dynamically allocated array of `InlineCache` of size `number_of_inline_caches_`.
  // - Dynamically allocated array of `BranchCache of size `number_of_branch_caches_`.
  friend class jit::JitCodeCache;

  DISALLOW_COPY_AND_ASSIGN(ProfilingInfo);
};

class ScopedProfilingInfoUse : public ValueObject {
 public:
  ScopedProfilingInfoUse(jit::Jit* jit, ArtMethod* method, Thread* self);
  ~ScopedProfilingInfoUse();

  ProfilingInfo* GetProfilingInfo() const { return profiling_info_; }

 private:
  jit::Jit* const jit_;
  ArtMethod* const method_;
  Thread* const self_;
  ProfilingInfo* const profiling_info_;

  DISALLOW_COPY_AND_ASSIGN(ScopedProfilingInfoUse);
};

}  // namespace art

#endif  // ART_RUNTIME_JIT_PROFILING_INFO_H_
