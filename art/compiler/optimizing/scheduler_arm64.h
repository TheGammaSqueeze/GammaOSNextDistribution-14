/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef ART_COMPILER_OPTIMIZING_SCHEDULER_ARM64_H_
#define ART_COMPILER_OPTIMIZING_SCHEDULER_ARM64_H_

#include "base/macros.h"
#include "scheduler.h"

namespace art HIDDEN {
namespace arm64 {

class HSchedulerARM64 : public HScheduler {
 public:
  explicit HSchedulerARM64(SchedulingNodeSelector* selector)
      : HScheduler(selector) {}
  ~HSchedulerARM64() override {}

  bool IsSchedulable(const HInstruction* instruction) const override;

  // Treat as scheduling barriers those vector instructions whose live ranges exceed the vectorized
  // loop boundaries. This is a workaround for the lack of notion of SIMD register in the compiler;
  // around a call we have to save/restore all live SIMD&FP registers (only lower 64 bits of
  // SIMD&FP registers are callee saved) so don't reorder such vector instructions.
  //
  // TODO: remove this when a proper support of SIMD registers is introduced to the compiler.
  bool IsSchedulingBarrier(const HInstruction* instr) const override {
    return HScheduler::IsSchedulingBarrier(instr) ||
           instr->IsVecReduce() ||
           instr->IsVecExtractScalar() ||
           instr->IsVecSetScalars() ||
           instr->IsVecReplicateScalar();
  }

 protected:
  std::pair<SchedulingGraph, ScopedArenaVector<SchedulingNode*>> BuildSchedulingGraph(
      HBasicBlock* block,
      ScopedArenaAllocator* allocator,
      const HeapLocationCollector* heap_location_collector) override;

 private:
  DISALLOW_COPY_AND_ASSIGN(HSchedulerARM64);
};

}  // namespace arm64
}  // namespace art

#endif  // ART_COMPILER_OPTIMIZING_SCHEDULER_ARM64_H_
