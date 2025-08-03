/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef ART_COMPILER_OPTIMIZING_SCHEDULER_ARM_H_
#define ART_COMPILER_OPTIMIZING_SCHEDULER_ARM_H_

#include "base/macros.h"
#include "scheduler.h"

namespace art HIDDEN {

class CodeGenerator;

namespace arm {

class HSchedulerARM final : public HScheduler {
 public:
  HSchedulerARM(SchedulingNodeSelector* selector, CodeGenerator* codegen)
      : HScheduler(selector), codegen_(codegen) {}
  ~HSchedulerARM() override {}

  bool IsSchedulable(const HInstruction* instruction) const override;

 protected:
  std::pair<SchedulingGraph, ScopedArenaVector<SchedulingNode*>> BuildSchedulingGraph(
      HBasicBlock* block,
      ScopedArenaAllocator* allocator,
      const HeapLocationCollector* heap_location_collector) override;

 private:
  DISALLOW_COPY_AND_ASSIGN(HSchedulerARM);

  CodeGenerator* const codegen_;
};

}  // namespace arm
}  // namespace art

#endif  // ART_COMPILER_OPTIMIZING_SCHEDULER_ARM_H_
