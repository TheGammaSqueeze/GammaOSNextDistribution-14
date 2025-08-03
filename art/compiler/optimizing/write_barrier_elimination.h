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

#ifndef ART_COMPILER_OPTIMIZING_WRITE_BARRIER_ELIMINATION_H_
#define ART_COMPILER_OPTIMIZING_WRITE_BARRIER_ELIMINATION_H_

#include "base/macros.h"
#include "optimization.h"

namespace art HIDDEN {

// Eliminates unnecessary write barriers from InstanceFieldSet, StaticFieldSet, and ArraySet.
//
// We can eliminate redundant write barriers as we don't need several for the same receiver. For
// example:
//   MyObject o;
//   o.inner_obj = io;
//   o.inner_obj2 = io2;
//   o.inner_obj3 = io3;
// We can keep the write barrier for `inner_obj` and remove the other two.
//
// In order to do this, we set the WriteBarrierKind of the instruction. The instruction's kind are
// set to kEmitBeingReliedOn (if this write barrier coalesced other write barriers, we don't want to
// perform the null check optimization), or to kDontEmit (if the write barrier as a whole is not
// needed).
class WriteBarrierElimination : public HOptimization {
 public:
  WriteBarrierElimination(HGraph* graph,
                          OptimizingCompilerStats* stats,
                          const char* name = kWBEPassName)
      : HOptimization(graph, name, stats) {}

  bool Run() override;

  static constexpr const char* kWBEPassName = "write_barrier_elimination";

 private:
  DISALLOW_COPY_AND_ASSIGN(WriteBarrierElimination);
};

}  // namespace art

#endif  // ART_COMPILER_OPTIMIZING_WRITE_BARRIER_ELIMINATION_H_
