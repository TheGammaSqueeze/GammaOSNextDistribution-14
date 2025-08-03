/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef ART_COMPILER_OPTIMIZING_GRAPH_CHECKER_H_
#define ART_COMPILER_OPTIMIZING_GRAPH_CHECKER_H_

#include <ostream>

#include "base/arena_bit_vector.h"
#include "base/bit_vector-inl.h"
#include "base/macros.h"
#include "base/scoped_arena_containers.h"
#include "nodes.h"

namespace art HIDDEN {

class CodeGenerator;

// A control-flow graph visitor performing various checks.
class GraphChecker final : public HGraphDelegateVisitor {
 public:
  explicit GraphChecker(HGraph* graph,
                        CodeGenerator* codegen = nullptr,
                        const char* dump_prefix = "art::GraphChecker: ")
      : HGraphDelegateVisitor(graph),
        errors_(graph->GetAllocator()->Adapter(kArenaAllocGraphChecker)),
        dump_prefix_(dump_prefix),
        allocator_(graph->GetArenaStack()),
        seen_ids_(&allocator_, graph->GetCurrentInstructionId(), false, kArenaAllocGraphChecker),
        uses_per_instruction_(allocator_.Adapter(kArenaAllocGraphChecker)),
        instructions_per_block_(allocator_.Adapter(kArenaAllocGraphChecker)),
        phis_per_block_(allocator_.Adapter(kArenaAllocGraphChecker)),
        codegen_(codegen) {
    seen_ids_.ClearAllBits();
  }

  // Check the whole graph. The pass_change parameter indicates whether changes
  // may have occurred during the just executed pass. The default value is
  // conservatively "true" (something may have changed). The last_size parameter
  // and return value pass along the observed graph sizes.
  size_t Run(bool pass_change = true, size_t last_size = 0);

  void VisitBasicBlock(HBasicBlock* block) override;

  void VisitInstruction(HInstruction* instruction) override;
  void VisitPhi(HPhi* phi) override;

  void VisitArraySet(HArraySet* instruction) override;
  void VisitInstanceFieldSet(HInstanceFieldSet* instruction) override;
  void VisitStaticFieldSet(HStaticFieldSet* instruction) override;
  void VisitBinaryOperation(HBinaryOperation* op) override;
  void VisitBooleanNot(HBooleanNot* instruction) override;
  void VisitBoundType(HBoundType* instruction) override;
  void VisitBoundsCheck(HBoundsCheck* check) override;
  void VisitCheckCast(HCheckCast* check) override;
  void VisitCondition(HCondition* op) override;
  void VisitConstant(HConstant* instruction) override;
  void VisitDeoptimize(HDeoptimize* instruction) override;
  void VisitIf(HIf* instruction) override;
  void VisitInstanceOf(HInstanceOf* check) override;
  void VisitInvoke(HInvoke* invoke) override;
  void VisitInvokeStaticOrDirect(HInvokeStaticOrDirect* invoke) override;
  void VisitLoadClass(HLoadClass* load) override;
  void VisitLoadException(HLoadException* load) override;
  void VisitMonitorOperation(HMonitorOperation* monitor_operation) override;
  void VisitNeg(HNeg* instruction) override;
  void VisitPackedSwitch(HPackedSwitch* instruction) override;
  void VisitReturn(HReturn* ret) override;
  void VisitReturnVoid(HReturnVoid* ret) override;
  void VisitSelect(HSelect* instruction) override;
  void VisitTryBoundary(HTryBoundary* try_boundary) override;
  void VisitTypeConversion(HTypeConversion* instruction) override;

  void VisitVecOperation(HVecOperation* instruction) override;

  void CheckTypeCheckBitstringInput(HTypeCheckInstruction* check,
                                    size_t input_pos,
                                    bool check_value,
                                    uint32_t expected_value,
                                    const char* name);
  void HandleTypeCheckInstruction(HTypeCheckInstruction* instruction);
  void HandleLoop(HBasicBlock* loop_header);
  void HandleBooleanInput(HInstruction* instruction, size_t input_index);

  template <typename GetWriteBarrierKind>
  void CheckWriteBarrier(HInstruction* instruction, GetWriteBarrierKind&& get_write_barrier_kind);

  // Was the last visit of the graph valid?
  bool IsValid() const {
    return errors_.empty();
  }

  // Get the list of detected errors.
  const ArenaVector<std::string>& GetErrors() const {
    return errors_;
  }

  // Print detected errors on output stream `os`.
  void Dump(std::ostream& os) const {
    for (size_t i = 0, e = errors_.size(); i < e; ++i) {
      os << dump_prefix_ << errors_[i] << std::endl;
    }
  }

 private:
  // Report a new error.
  void AddError(const std::string& error) {
    errors_.push_back(error);
  }

  // The block currently visited.
  HBasicBlock* current_block_ = nullptr;
  // Errors encountered while checking the graph.
  ArenaVector<std::string> errors_;

  void VisitReversePostOrder();

  // Checks that the graph's flags are set correctly.
  void CheckGraphFlags();

  // Checks if `instruction` is in its block's instruction/phi list. To do so, it searches
  // instructions_per_block_/phis_per_block_ which are set versions of that. If the set to
  // check hasn't been populated yet, it does so now.
  bool ContainedInItsBlockList(HInstruction* instruction);

  // String displayed before dumped errors.
  const char* const dump_prefix_;
  ScopedArenaAllocator allocator_;
  ArenaBitVector seen_ids_;

  // As part of VisitInstruction, we verify that the instruction's input_record is present in the
  // corresponding input's GetUses. If an instruction is used in many places (e.g. 200K+ uses), the
  // linear search through GetUses is too slow. We can use bookkeeping to search in a set, instead
  // of a list.
  ScopedArenaSafeMap<int, ScopedArenaSet<const art::HUseListNode<art::HInstruction*>*>>
      uses_per_instruction_;

  // Extra bookkeeping to increase GraphChecker's speed while asking if an instruction is contained
  // in a list of instructions/phis.
  ScopedArenaSafeMap<HBasicBlock*, ScopedArenaHashSet<HInstruction*>> instructions_per_block_;
  ScopedArenaSafeMap<HBasicBlock*, ScopedArenaHashSet<HInstruction*>> phis_per_block_;

  // Used to access target information.
  CodeGenerator* codegen_;

  struct FlagInfo {
    bool seen_try_boundary = false;
    bool seen_monitor_operation = false;
    bool seen_loop = false;
    bool seen_irreducible_loop = false;
    bool seen_SIMD = false;
    bool seen_bounds_checks = false;
    bool seen_always_throwing_invokes = false;
  };
  FlagInfo flag_info_;

  DISALLOW_COPY_AND_ASSIGN(GraphChecker);
};

}  // namespace art

#endif  // ART_COMPILER_OPTIMIZING_GRAPH_CHECKER_H_
