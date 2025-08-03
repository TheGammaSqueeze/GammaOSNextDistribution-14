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

#include "induction_var_analysis.h"

#include "base/scoped_arena_containers.h"
#include "induction_var_range.h"

namespace art HIDDEN {

/**
 * Returns true if the from/to types denote a narrowing, integral conversion (precision loss).
 */
static bool IsNarrowingIntegralConversion(DataType::Type from, DataType::Type to) {
  switch (from) {
    case DataType::Type::kInt64:
      return to == DataType::Type::kUint8 ||
             to == DataType::Type::kInt8 ||
             to == DataType::Type::kUint16 ||
             to == DataType::Type::kInt16 ||
             to == DataType::Type::kInt32;
    case DataType::Type::kInt32:
      return to == DataType::Type::kUint8 ||
             to == DataType::Type::kInt8 ||
             to == DataType::Type::kUint16 ||
             to == DataType::Type::kInt16;
    case DataType::Type::kUint16:
    case DataType::Type::kInt16:
      return to == DataType::Type::kUint8 || to == DataType::Type::kInt8;
    default:
      return false;
  }
}

/**
 * Returns result of implicit widening type conversion done in HIR.
 */
static DataType::Type ImplicitConversion(DataType::Type type) {
  switch (type) {
    case DataType::Type::kBool:
    case DataType::Type::kUint8:
    case DataType::Type::kInt8:
    case DataType::Type::kUint16:
    case DataType::Type::kInt16:
      return DataType::Type::kInt32;
    default:
      return type;
  }
}

/**
 * Returns true if loop is guarded by "a cmp b" on entry.
 */
static bool IsGuardedBy(const HLoopInformation* loop,
                        IfCondition cmp,
                        HInstruction* a,
                        HInstruction* b) {
  // Chase back through straightline code to the first potential
  // block that has a control dependence.
  // guard:   if (x) bypass
  //              |
  // entry: straightline code
  //              |
  //           preheader
  //              |
  //            header
  HBasicBlock* guard = loop->GetPreHeader();
  HBasicBlock* entry = loop->GetHeader();
  while (guard->GetPredecessors().size() == 1 &&
         guard->GetSuccessors().size() == 1) {
    entry = guard;
    guard = guard->GetSinglePredecessor();
  }
  // Find guard.
  HInstruction* control = guard->GetLastInstruction();
  if (!control->IsIf()) {
    return false;
  }
  HIf* ifs = control->AsIf();
  HInstruction* if_expr = ifs->InputAt(0);
  if (if_expr->IsCondition()) {
    IfCondition other_cmp = ifs->IfTrueSuccessor() == entry
        ? if_expr->AsCondition()->GetCondition()
        : if_expr->AsCondition()->GetOppositeCondition();
    if (if_expr->InputAt(0) == a && if_expr->InputAt(1) == b) {
      return cmp == other_cmp;
    } else if (if_expr->InputAt(1) == a && if_expr->InputAt(0) == b) {
      switch (cmp) {
        case kCondLT: return other_cmp == kCondGT;
        case kCondLE: return other_cmp == kCondGE;
        case kCondGT: return other_cmp == kCondLT;
        case kCondGE: return other_cmp == kCondLE;
        default: LOG(FATAL) << "unexpected cmp: " << cmp;
      }
    }
  }
  return false;
}

/* Finds first loop header phi use. */
HInstruction* FindFirstLoopHeaderPhiUse(const HLoopInformation* loop, HInstruction* instruction) {
  for (const HUseListNode<HInstruction*>& use : instruction->GetUses()) {
    if (use.GetUser()->GetBlock() == loop->GetHeader() &&
        use.GetUser()->IsPhi() &&
        use.GetUser()->InputAt(1) == instruction) {
      return use.GetUser();
    }
  }
  return nullptr;
}

/**
 * Relinks the Phi structure after break-loop rewriting.
 */
static bool FixOutsideUse(const HLoopInformation* loop,
                          HInstruction* instruction,
                          HInstruction* replacement,
                          bool rewrite) {
  // Deal with regular uses.
  const HUseList<HInstruction*>& uses = instruction->GetUses();
  for (auto it = uses.begin(), end = uses.end(); it != end; ) {
    HInstruction* user = it->GetUser();
    size_t index = it->GetIndex();
    ++it;  // increment prior to potential removal
    if (user->GetBlock()->GetLoopInformation() != loop) {
      if (replacement == nullptr) {
        return false;
      } else if (rewrite) {
        user->ReplaceInput(replacement, index);
      }
    }
  }
  // Deal with environment uses.
  const HUseList<HEnvironment*>& env_uses = instruction->GetEnvUses();
  for (auto it = env_uses.begin(), end = env_uses.end(); it != end;) {
    HEnvironment* user = it->GetUser();
    size_t index = it->GetIndex();
    ++it;  // increment prior to potential removal
    if (user->GetHolder()->GetBlock()->GetLoopInformation() != loop) {
      if (replacement == nullptr) {
        return false;
      } else if (rewrite) {
        user->ReplaceInput(replacement, index);
      }
    }
  }
  return true;
}

/**
 * Test and rewrite the loop body of a break-loop. Returns true on success.
 */
static bool RewriteBreakLoopBody(const HLoopInformation* loop,
                                 HBasicBlock* body,
                                 HInstruction* cond,
                                 HInstruction* index,
                                 HInstruction* upper,
                                 bool rewrite) {
  // Deal with Phis. Outside use prohibited, except for index (which gets exit value).
  for (HInstructionIterator it(loop->GetHeader()->GetPhis()); !it.Done(); it.Advance()) {
    HInstruction* exit_value = it.Current() == index ? upper : nullptr;
    if (!FixOutsideUse(loop, it.Current(), exit_value, rewrite)) {
      return false;
    }
  }
  // Deal with other statements in header.
  for (HInstruction* m = cond->GetPrevious(); m && !m->IsSuspendCheck();) {
    HInstruction* p = m->GetPrevious();
    if (rewrite) {
      m->MoveBefore(body->GetFirstInstruction(), false);
    }
    if (!FixOutsideUse(loop, m, FindFirstLoopHeaderPhiUse(loop, m), rewrite)) {
      return false;
    }
    m = p;
  }
  return true;
}

//
// Class members.
//

struct HInductionVarAnalysis::NodeInfo {
  explicit NodeInfo(uint32_t d) : depth(d), done(false) {}
  uint32_t depth;
  bool done;
};

struct HInductionVarAnalysis::StackEntry {
  StackEntry(HInstruction* insn, NodeInfo* info, size_t link = std::numeric_limits<size_t>::max())
      : instruction(insn),
        node_info(info),
        user_link(link),
        num_visited_inputs(0u),
        low_depth(info->depth) {}

  HInstruction* instruction;
  NodeInfo* node_info;
  size_t user_link;  // Stack index of the user that is visiting this input.
  size_t num_visited_inputs;
  size_t low_depth;
};

HInductionVarAnalysis::HInductionVarAnalysis(HGraph* graph,
                                             OptimizingCompilerStats* stats,
                                             const char* name)
    : HOptimization(graph, name, stats),
      induction_(std::less<const HLoopInformation*>(),
                 graph->GetAllocator()->Adapter(kArenaAllocInductionVarAnalysis)),
      cycles_(std::less<HPhi*>(), graph->GetAllocator()->Adapter(kArenaAllocInductionVarAnalysis)) {
}

bool HInductionVarAnalysis::Run() {
  // Detects sequence variables (generalized induction variables) during an outer to inner
  // traversal of all loops using Gerlek's algorithm. The order is important to enable
  // range analysis on outer loop while visiting inner loops.

  if (IsPathologicalCase()) {
    MaybeRecordStat(stats_, MethodCompilationStat::kNotVarAnalyzedPathological);
    return false;
  }

  for (HBasicBlock* graph_block : graph_->GetReversePostOrder()) {
    // Don't analyze irreducible loops.
    if (graph_block->IsLoopHeader() && !graph_block->GetLoopInformation()->IsIrreducible()) {
      VisitLoop(graph_block->GetLoopInformation());
    }
  }
  return !induction_.empty();
}

void HInductionVarAnalysis::VisitLoop(const HLoopInformation* loop) {
  ScopedArenaAllocator local_allocator(graph_->GetArenaStack());
  ScopedArenaSafeMap<HInstruction*, NodeInfo> visited_instructions(
      std::less<HInstruction*>(), local_allocator.Adapter(kArenaAllocInductionVarAnalysis));

  // Find strongly connected components (SSCs) in the SSA graph of this loop using Tarjan's
  // algorithm. Due to the descendant-first nature, classification happens "on-demand".
  size_t global_depth = 0;
  for (HBlocksInLoopIterator it_loop(*loop); !it_loop.Done(); it_loop.Advance()) {
    HBasicBlock* loop_block = it_loop.Current();
    DCHECK(loop_block->IsInLoop());
    if (loop_block->GetLoopInformation() != loop) {
      continue;  // Inner loops visited later.
    }
    // Visit phi-operations and instructions.
    for (HInstructionIterator it(loop_block->GetPhis()); !it.Done(); it.Advance()) {
      global_depth = TryVisitNodes(loop, it.Current(), global_depth, &visited_instructions);
    }
    for (HInstructionIterator it(loop_block->GetInstructions()); !it.Done(); it.Advance()) {
      global_depth = TryVisitNodes(loop, it.Current(), global_depth, &visited_instructions);
    }
  }

  // Determine the loop's trip-count.
  VisitControl(loop);
}

size_t HInductionVarAnalysis::TryVisitNodes(
    const HLoopInformation* loop,
    HInstruction* start_instruction,
    size_t global_depth,
    /*inout*/ ScopedArenaSafeMap<HInstruction*, NodeInfo>* visited_instructions) {
  // This is recursion-free version of the SCC search algorithm. We have limited stack space,
  // so recursion with the depth dependent on the input is undesirable as such depth is unlimited.
  auto [it, inserted] =
      visited_instructions->insert(std::make_pair(start_instruction, NodeInfo(global_depth + 1u)));
  if (!inserted) {
    return global_depth;
  }
  NodeInfo* start_info = &it->second;
  ++global_depth;
  DCHECK_EQ(global_depth, start_info->depth);

  ScopedArenaVector<StackEntry> stack(visited_instructions->get_allocator());
  stack.push_back({start_instruction, start_info});

  size_t current_entry = 0u;
  while (!stack.empty()) {
    StackEntry& entry = stack[current_entry];

    // Look for unvisited inputs (also known as "descentants").
    bool visit_input = false;
    auto inputs = entry.instruction->GetInputs();
    while (entry.num_visited_inputs != inputs.size()) {
      HInstruction* input = inputs[entry.num_visited_inputs];
      ++entry.num_visited_inputs;
      // If the definition is either outside the loop (loop invariant entry value)
      // or assigned in inner loop (inner exit value), the input is not visited.
      if (input->GetBlock()->GetLoopInformation() != loop) {
        continue;
      }
      // Try visiting the input. If already visited, update `entry.low_depth`.
      auto [input_it, input_inserted] =
          visited_instructions->insert(std::make_pair(input, NodeInfo(global_depth + 1u)));
      NodeInfo* input_info = &input_it->second;
      if (input_inserted) {
        // Push the input on the `stack` and visit it now.
        ++global_depth;
        DCHECK_EQ(global_depth, input_info->depth);
        stack.push_back({input, input_info, current_entry});
        current_entry = stack.size() - 1u;
        visit_input = true;
        break;
      } else if (!input_info->done && input_info->depth < entry.low_depth) {
        entry.low_depth = input_it->second.depth;
      }
      continue;
    }
    if (visit_input) {
      continue;  // Process the new top of the stack.
    }

    // All inputs of the current node have been visited.
    // Check if we have found an input below this entry on the stack.
    DCHECK(!entry.node_info->done);
    size_t previous_entry = entry.user_link;
    if (entry.node_info->depth > entry.low_depth) {
      DCHECK_LT(previous_entry, current_entry) << entry.node_info->depth << " " << entry.low_depth;
      entry.node_info->depth = entry.low_depth;
      if (stack[previous_entry].low_depth > entry.low_depth) {
        stack[previous_entry].low_depth = entry.low_depth;
      }
    } else {
      // Classify the SCC we have just found.
      ArrayRef<StackEntry> stack_tail = ArrayRef<StackEntry>(stack).SubArray(current_entry);
      for (StackEntry& tail_entry : stack_tail) {
        tail_entry.node_info->done = true;
      }
      if (current_entry + 1u == stack.size() && !entry.instruction->IsLoopHeaderPhi()) {
        ClassifyTrivial(loop, entry.instruction);
      } else {
        ClassifyNonTrivial(loop, ArrayRef<const StackEntry>(stack_tail));
      }
      stack.erase(stack.begin() + current_entry, stack.end());
    }
    current_entry = previous_entry;
  }

  return global_depth;
}

/**
 * Since graph traversal may enter a SCC at any position, an initial representation may be rotated,
 * along dependences, viz. any of (a, b, c, d), (d, a, b, c)  (c, d, a, b), (b, c, d, a) assuming
 * a chain of dependences (mutual independent items may occur in arbitrary order). For proper
 * classification, the lexicographically first loop-phi is rotated to the front. We do that
 * as we extract the SCC instructions.
 */
void HInductionVarAnalysis::ExtractScc(ArrayRef<const StackEntry> stack_tail,
                                       ScopedArenaVector<HInstruction*>* scc) {
  // Find very first loop-phi.
  HInstruction* phi = nullptr;
  size_t split_pos = 0;
  const size_t size = stack_tail.size();
  for (size_t i = 0; i != size; ++i) {
    const StackEntry& entry = stack_tail[i];
    HInstruction* instruction = entry.instruction;
    if (instruction->IsLoopHeaderPhi()) {
      // All loop Phis in SCC come from the same loop header.
      HBasicBlock* block = instruction->GetBlock();
      DCHECK(block->GetLoopInformation()->GetHeader() == block);
      DCHECK(phi == nullptr || phi->GetBlock() == block);
      if (phi == nullptr || block->GetPhis().FoundBefore(instruction, phi)) {
        phi = instruction;
        split_pos = i + 1u;
      }
    }
  }

  // Extract SCC in two chunks.
  DCHECK(scc->empty());
  scc->reserve(size);
  for (const StackEntry& entry : ReverseRange(stack_tail.SubArray(/*pos=*/ 0u, split_pos))) {
    scc->push_back(entry.instruction);
  }
  for (const StackEntry& entry : ReverseRange(stack_tail.SubArray(/*pos=*/ split_pos))) {
    scc->push_back(entry.instruction);
  }
  DCHECK_EQ(scc->size(), stack_tail.size());
}

void HInductionVarAnalysis::ClassifyTrivial(const HLoopInformation* loop,
                                            HInstruction* instruction) {
  const HBasicBlock* context = instruction->GetBlock();
  DataType::Type type = instruction->GetType();
  InductionInfo* info = nullptr;
  if (instruction->IsPhi()) {
    info = TransferPhi(loop, instruction, /*input_index*/ 0, /*adjust_input_size*/ 0);
  } else if (instruction->IsAdd()) {
    info = TransferAddSub(context,
                          loop,
                          LookupInfo(loop, instruction->InputAt(0)),
                          LookupInfo(loop, instruction->InputAt(1)),
                          kAdd,
                          type);
  } else if (instruction->IsSub()) {
    info = TransferAddSub(context,
                          loop,
                          LookupInfo(loop, instruction->InputAt(0)),
                          LookupInfo(loop, instruction->InputAt(1)),
                          kSub,
                          type);
  } else if (instruction->IsNeg()) {
    info = TransferNeg(context, loop, LookupInfo(loop, instruction->InputAt(0)), type);
  } else if (instruction->IsMul()) {
    info = TransferMul(context,
                       loop,
                       LookupInfo(loop, instruction->InputAt(0)),
                       LookupInfo(loop, instruction->InputAt(1)),
                       type);
  } else if (instruction->IsShl()) {
    HInstruction* mulc = GetShiftConstant(loop, instruction, /*initial*/ nullptr);
    if (mulc != nullptr) {
      info = TransferMul(context,
                         loop,
                         LookupInfo(loop, instruction->InputAt(0)),
                         LookupInfo(loop, mulc),
                         type);
    }
  } else if (instruction->IsSelect()) {
    info = TransferPhi(loop, instruction, /*input_index*/ 0, /*adjust_input_size*/ 1);
  } else if (instruction->IsTypeConversion()) {
    info = TransferConversion(LookupInfo(loop, instruction->InputAt(0)),
                              instruction->AsTypeConversion()->GetInputType(),
                              instruction->AsTypeConversion()->GetResultType());
  } else if (instruction->IsBoundsCheck()) {
    info = LookupInfo(loop, instruction->InputAt(0));  // Pass-through.
  }

  // Successfully classified?
  if (info != nullptr) {
    AssignInfo(loop, instruction, info);
  }
}

void HInductionVarAnalysis::ClassifyNonTrivial(const HLoopInformation* loop,
                                               ArrayRef<const StackEntry> stack_tail) {
  const size_t size = stack_tail.size();
  DCHECK_GE(size, 1u);
  DataType::Type type = stack_tail.back().instruction->GetType();

  ScopedArenaAllocator local_allocator(graph_->GetArenaStack());
  ScopedArenaVector<HInstruction*> scc(local_allocator.Adapter(kArenaAllocInductionVarAnalysis));
  ExtractScc(ArrayRef<const StackEntry>(stack_tail), &scc);

  // Analyze from loop-phi onwards.
  HInstruction* phi = scc[0];
  if (!phi->IsLoopHeaderPhi()) {
    return;
  }

  // External link should be loop invariant.
  InductionInfo* initial = LookupInfo(loop, phi->InputAt(0));
  if (initial == nullptr || initial->induction_class != kInvariant) {
    return;
  }

  // Store interesting cycle in each loop phi.
  for (size_t i = 0; i < size; i++) {
    if (scc[i]->IsLoopHeaderPhi()) {
      AssignCycle(scc[i]->AsPhi(), ArrayRef<HInstruction* const>(scc));
    }
  }

  // Singleton is wrap-around induction if all internal links have the same meaning.
  if (size == 1) {
    InductionInfo* update = TransferPhi(loop, phi, /*input_index*/ 1, /*adjust_input_size*/ 0);
    if (update != nullptr) {
      AssignInfo(loop, phi, CreateInduction(kWrapAround,
                                            kNop,
                                            initial,
                                            update,
                                            /*fetch*/ nullptr,
                                            type));
    }
    return;
  }

  // Inspect remainder of the cycle that resides in `scc`. The `cycle` mapping assigns
  // temporary meaning to its nodes, seeded from the phi instruction and back.
  ScopedArenaSafeMap<HInstruction*, InductionInfo*> cycle(
      std::less<HInstruction*>(), local_allocator.Adapter(kArenaAllocInductionVarAnalysis));
  for (size_t i = 1; i < size; i++) {
    HInstruction* instruction = scc[i];
    InductionInfo* update = nullptr;
    if (instruction->IsPhi()) {
      update = SolvePhiAllInputs(loop, phi, instruction, cycle, type);
    } else if (instruction->IsAdd()) {
      update = SolveAddSub(loop,
                           phi,
                           instruction,
                           instruction->InputAt(0),
                           instruction->InputAt(1),
                           kAdd,
                           cycle,
                           type);
    } else if (instruction->IsSub()) {
      update = SolveAddSub(loop,
                           phi,
                           instruction,
                           instruction->InputAt(0),
                           instruction->InputAt(1),
                           kSub,
                           cycle,
                           type);
    } else if (instruction->IsMul()) {
      update = SolveOp(
          loop, phi, instruction, instruction->InputAt(0), instruction->InputAt(1), kMul, type);
    } else if (instruction->IsDiv()) {
      update = SolveOp(
          loop, phi, instruction, instruction->InputAt(0), instruction->InputAt(1), kDiv, type);
    } else if (instruction->IsRem()) {
      update = SolveOp(
          loop, phi, instruction, instruction->InputAt(0), instruction->InputAt(1), kRem, type);
    } else if (instruction->IsShl()) {
      HInstruction* mulc = GetShiftConstant(loop, instruction, /*initial*/ nullptr);
      if (mulc != nullptr) {
        update = SolveOp(loop, phi, instruction, instruction->InputAt(0), mulc, kMul, type);
      }
    } else if (instruction->IsShr() || instruction->IsUShr()) {
      HInstruction* divc = GetShiftConstant(loop, instruction, initial);
      if (divc != nullptr) {
        update = SolveOp(loop, phi, instruction, instruction->InputAt(0), divc, kDiv, type);
      }
    } else if (instruction->IsXor()) {
      update = SolveOp(
          loop, phi, instruction, instruction->InputAt(0), instruction->InputAt(1), kXor, type);
    } else if (instruction->IsEqual()) {
      update = SolveTest(loop, phi, instruction, /*opposite_value=*/ 0, type);
    } else if (instruction->IsNotEqual()) {
      update = SolveTest(loop, phi, instruction, /*opposite_value=*/ 1, type);
    } else if (instruction->IsSelect()) {
      // Select acts like Phi.
      update = SolvePhi(instruction, /*input_index=*/ 0, /*adjust_input_size=*/ 1, cycle);
    } else if (instruction->IsTypeConversion()) {
      update = SolveConversion(loop, phi, instruction->AsTypeConversion(), cycle, &type);
    }
    if (update == nullptr) {
      return;
    }
    cycle.Put(instruction, update);
  }

  // Success if all internal links received the same temporary meaning.
  InductionInfo* induction = SolvePhi(phi, /*input_index=*/ 1, /*adjust_input_size=*/ 0, cycle);
  if (induction != nullptr) {
    switch (induction->induction_class) {
      case kInvariant:
        // Construct combined stride of the linear induction.
        induction = CreateInduction(kLinear, kNop, induction, initial, /*fetch*/ nullptr, type);
        FALLTHROUGH_INTENDED;
      case kPolynomial:
      case kGeometric:
      case kWrapAround:
        // Classify first phi and then the rest of the cycle "on-demand".
        // Statements are scanned in order.
        AssignInfo(loop, phi, induction);
        for (size_t i = 1; i < size; i++) {
          ClassifyTrivial(loop, scc[i]);
        }
        break;
      case kPeriodic:
        // Classify all elements in the cycle with the found periodic induction while
        // rotating each first element to the end. Lastly, phi is classified.
        // Statements are scanned in reverse order.
        for (size_t i = size - 1; i >= 1; i--) {
          AssignInfo(loop, scc[i], induction);
          induction = RotatePeriodicInduction(induction->op_b, induction->op_a, type);
        }
        AssignInfo(loop, phi, induction);
        break;
      default:
        break;
    }
  }
}

HInductionVarAnalysis::InductionInfo* HInductionVarAnalysis::RotatePeriodicInduction(
    InductionInfo* induction,
    InductionInfo* last,
    DataType::Type type) {
  // Rotates a periodic induction of the form
  //   (a, b, c, d, e)
  // into
  //   (b, c, d, e, a)
  // in preparation of assigning this to the previous variable in the sequence.
  if (induction->induction_class == kInvariant) {
    return CreateInduction(kPeriodic,
                           kNop,
                           induction,
                           last,
                           /*fetch*/ nullptr,
                           type);
  }
  return CreateInduction(kPeriodic,
                         kNop,
                         induction->op_a,
                         RotatePeriodicInduction(induction->op_b, last, type),
                         /*fetch*/ nullptr,
                         type);
}

HInductionVarAnalysis::InductionInfo* HInductionVarAnalysis::TransferPhi(
    const HLoopInformation* loop,
    HInstruction* phi,
    size_t input_index,
    size_t adjust_input_size) {
  // Match all phi inputs from input_index onwards exactly.
  HInputsRef inputs = phi->GetInputs();
  DCHECK_LT(input_index, inputs.size());
  InductionInfo* a = LookupInfo(loop, inputs[input_index]);
  for (size_t i = input_index + 1, n = inputs.size() - adjust_input_size; i < n; i++) {
    InductionInfo* b = LookupInfo(loop, inputs[i]);
    if (!InductionEqual(a, b)) {
      return nullptr;
    }
  }
  return a;
}

HInductionVarAnalysis::InductionInfo* HInductionVarAnalysis::TransferAddSub(
    const HBasicBlock* context,
    const HLoopInformation* loop,
    InductionInfo* a,
    InductionInfo* b,
    InductionOp op,
    DataType::Type type) {
  // Transfer over an addition or subtraction: any invariant, linear, polynomial, geometric,
  // wrap-around, or periodic can be combined with an invariant to yield a similar result.
  // Two linear or two polynomial inputs can be combined too. Other combinations fail.
  if (a != nullptr && b != nullptr) {
    if (IsNarrowingLinear(a) || IsNarrowingLinear(b)) {
      return nullptr;  // no transfer
    } else if (a->induction_class == kInvariant && b->induction_class == kInvariant) {
      return CreateInvariantOp(context, loop, op, a, b);  // direct invariant
    } else if ((a->induction_class == kLinear && b->induction_class == kLinear) ||
               (a->induction_class == kPolynomial && b->induction_class == kPolynomial)) {
      // Rule induc(a, b) + induc(a', b') -> induc(a + a', b + b').
      InductionInfo* new_a = TransferAddSub(context, loop, a->op_a, b->op_a, op, type);
      InductionInfo* new_b = TransferAddSub(context, loop, a->op_b, b->op_b, op, type);
      if (new_a != nullptr && new_b != nullptr) {
        return CreateInduction(a->induction_class, a->operation, new_a, new_b, a->fetch, type);
      }
    } else if (a->induction_class == kInvariant) {
      // Rule a + induc(a', b') -> induc(a', a + b') or induc(a + a', a + b').
      InductionInfo* new_a = b->op_a;
      InductionInfo* new_b = TransferAddSub(context, loop, a, b->op_b, op, type);
      if (b->induction_class == kWrapAround || b->induction_class == kPeriodic) {
        new_a = TransferAddSub(context, loop, a, new_a, op, type);
      } else if (op == kSub) {  // Negation required.
        new_a = TransferNeg(context, loop, new_a, type);
      }
      if (new_a != nullptr && new_b != nullptr) {
        return CreateInduction(b->induction_class, b->operation, new_a, new_b, b->fetch, type);
      }
    } else if (b->induction_class == kInvariant) {
      // Rule induc(a, b) + b' -> induc(a, b + b') or induc(a + b', b + b').
      InductionInfo* new_a = a->op_a;
      InductionInfo* new_b = TransferAddSub(context, loop, a->op_b, b, op, type);
      if (a->induction_class == kWrapAround || a->induction_class == kPeriodic) {
        new_a = TransferAddSub(context, loop, new_a, b, op, type);
      }
      if (new_a != nullptr && new_b != nullptr) {
        return CreateInduction(a->induction_class, a->operation, new_a, new_b, a->fetch, type);
      }
    }
  }
  return nullptr;
}

HInductionVarAnalysis::InductionInfo* HInductionVarAnalysis::TransferNeg(
    const HBasicBlock* context,
    const HLoopInformation* loop,
    InductionInfo* a,
    DataType::Type type) {
  // Transfer over a unary negation: an invariant, linear, polynomial, geometric (mul),
  // wrap-around, or periodic input yields a similar but negated induction as result.
  if (a != nullptr) {
    if (IsNarrowingLinear(a)) {
      return nullptr;  // no transfer
    } else if (a->induction_class == kInvariant) {
      return CreateInvariantOp(context, loop, kNeg, nullptr, a);  // direct invariant
    } else if (a->induction_class != kGeometric || a->operation == kMul) {
      // Rule - induc(a, b) -> induc(-a, -b).
      InductionInfo* new_a = TransferNeg(context, loop, a->op_a, type);
      InductionInfo* new_b = TransferNeg(context, loop, a->op_b, type);
      if (new_a != nullptr && new_b != nullptr) {
        return CreateInduction(a->induction_class, a->operation, new_a, new_b, a->fetch, type);
      }
    }
  }
  return nullptr;
}

HInductionVarAnalysis::InductionInfo* HInductionVarAnalysis::TransferMul(
    const HBasicBlock* context,
    const HLoopInformation* loop,
    InductionInfo* a,
    InductionInfo* b,
    DataType::Type type) {
  // Transfer over a multiplication: any invariant, linear, polynomial, geometric (mul),
  // wrap-around, or periodic can be multiplied with an invariant to yield a similar
  // but multiplied result. Two non-invariant inputs cannot be multiplied, however.
  if (a != nullptr && b != nullptr) {
    if (IsNarrowingLinear(a) || IsNarrowingLinear(b)) {
      return nullptr;  // no transfer
    } else if (a->induction_class == kInvariant && b->induction_class == kInvariant) {
      return CreateInvariantOp(context, loop, kMul, a, b);  // direct invariant
    } else if (a->induction_class == kInvariant && (b->induction_class != kGeometric ||
                                                    b->operation == kMul)) {
      // Rule a * induc(a', b') -> induc(a * a', b * b').
      InductionInfo* new_a = TransferMul(context, loop, a, b->op_a, type);
      InductionInfo* new_b = TransferMul(context, loop, a, b->op_b, type);
      if (new_a != nullptr && new_b != nullptr) {
        return CreateInduction(b->induction_class, b->operation, new_a, new_b, b->fetch, type);
      }
    } else if (b->induction_class == kInvariant && (a->induction_class != kGeometric ||
                                                    a->operation == kMul)) {
      // Rule induc(a, b) * b' -> induc(a * b', b * b').
      InductionInfo* new_a = TransferMul(context, loop, a->op_a, b, type);
      InductionInfo* new_b = TransferMul(context, loop, a->op_b, b, type);
      if (new_a != nullptr && new_b != nullptr) {
        return CreateInduction(a->induction_class, a->operation, new_a, new_b, a->fetch, type);
      }
    }
  }
  return nullptr;
}

HInductionVarAnalysis::InductionInfo* HInductionVarAnalysis::TransferConversion(
    InductionInfo* a,
    DataType::Type from,
    DataType::Type to) {
  if (a != nullptr) {
    // Allow narrowing conversion on linear induction in certain cases:
    // induction is already at narrow type, or can be made narrower.
    if (IsNarrowingIntegralConversion(from, to) &&
        a->induction_class == kLinear &&
        (a->type == to || IsNarrowingIntegralConversion(a->type, to))) {
      return CreateInduction(kLinear, kNop, a->op_a, a->op_b, a->fetch, to);
    }
  }
  return nullptr;
}

HInductionVarAnalysis::InductionInfo* HInductionVarAnalysis::SolvePhi(
    HInstruction* phi,
    size_t input_index,
    size_t adjust_input_size,
    const ScopedArenaSafeMap<HInstruction*, InductionInfo*>& cycle) {
  // Match all phi inputs from input_index onwards exactly.
  HInputsRef inputs = phi->GetInputs();
  DCHECK_LT(input_index, inputs.size());
  auto ita = cycle.find(inputs[input_index]);
  if (ita != cycle.end()) {
    for (size_t i = input_index + 1, n = inputs.size() - adjust_input_size; i < n; i++) {
      auto itb = cycle.find(inputs[i]);
      if (itb == cycle.end() ||
          !HInductionVarAnalysis::InductionEqual(ita->second, itb->second)) {
        return nullptr;
      }
    }
    return ita->second;
  }
  return nullptr;
}

HInductionVarAnalysis::InductionInfo* HInductionVarAnalysis::SolvePhiAllInputs(
    const HLoopInformation* loop,
    HInstruction* entry_phi,
    HInstruction* phi,
    const ScopedArenaSafeMap<HInstruction*, InductionInfo*>& cycle,
    DataType::Type type) {
  // Match all phi inputs.
  InductionInfo* match = SolvePhi(phi, /*input_index=*/ 0, /*adjust_input_size=*/ 0, cycle);
  if (match != nullptr) {
    return match;
  }

  // Otherwise, try to solve for a periodic seeded from phi onward.
  // Only tight multi-statement cycles are considered in order to
  // simplify rotating the periodic during the final classification.
  if (phi->IsLoopHeaderPhi() && phi->InputCount() == 2) {
    InductionInfo* a = LookupInfo(loop, phi->InputAt(0));
    if (a != nullptr && a->induction_class == kInvariant) {
      if (phi->InputAt(1) == entry_phi) {
        InductionInfo* initial = LookupInfo(loop, entry_phi->InputAt(0));
        return CreateInduction(kPeriodic, kNop, a, initial, /*fetch*/ nullptr, type);
      }
      InductionInfo* b = SolvePhi(phi, /*input_index=*/ 1, /*adjust_input_size=*/ 0, cycle);
      if (b != nullptr && b->induction_class == kPeriodic) {
        return CreateInduction(kPeriodic, kNop, a, b, /*fetch*/ nullptr, type);
      }
    }
  }
  return nullptr;
}

HInductionVarAnalysis::InductionInfo* HInductionVarAnalysis::SolveAddSub(
    const HLoopInformation* loop,
    HInstruction* entry_phi,
    HInstruction* instruction,
    HInstruction* x,
    HInstruction* y,
    InductionOp op,
    const ScopedArenaSafeMap<HInstruction*, InductionInfo*>& cycle,
    DataType::Type type) {
  const HBasicBlock* context = instruction->GetBlock();
  auto main_solve_add_sub = [&]() -> HInductionVarAnalysis::InductionInfo* {
    // Solve within a cycle over an addition or subtraction.
    InductionInfo* b = LookupInfo(loop, y);
    if (b != nullptr) {
      if (b->induction_class == kInvariant) {
        // Adding or subtracting an invariant value, seeded from phi,
        // keeps adding to the stride of the linear induction.
        if (x == entry_phi) {
          return (op == kAdd) ? b : CreateInvariantOp(context, loop, kNeg, nullptr, b);
        }
        auto it = cycle.find(x);
        if (it != cycle.end()) {
          InductionInfo* a = it->second;
          if (a->induction_class == kInvariant) {
            return CreateInvariantOp(context, loop, op, a, b);
          }
        }
      } else if (b->induction_class == kLinear && b->type == type) {
        // Solve within a tight cycle that adds a term that is already classified as a linear
        // induction for a polynomial induction k = k + i (represented as sum over linear terms).
        if (x == entry_phi &&
            entry_phi->InputCount() == 2 &&
            instruction == entry_phi->InputAt(1)) {
          InductionInfo* initial = LookupInfo(loop, entry_phi->InputAt(0));
          InductionInfo* new_a = op == kAdd ? b : TransferNeg(context, loop, b, type);
          if (new_a != nullptr) {
            return CreateInduction(kPolynomial, kNop, new_a, initial, /*fetch*/ nullptr, type);
          }
        }
      }
    }
    return nullptr;
  };
  HInductionVarAnalysis::InductionInfo* result = main_solve_add_sub();
  if (result == nullptr) {
    // Try some alternatives before failing.
    if (op == kAdd) {
      // Try the other way around for an addition.
      std::swap(x, y);
      result = main_solve_add_sub();
    } else if (op == kSub) {
      // Solve within a tight cycle that is formed by exactly two instructions,
      // one phi and one update, for a periodic idiom of the form k = c - k.
      if (y == entry_phi && entry_phi->InputCount() == 2 && instruction == entry_phi->InputAt(1)) {
        InductionInfo* a = LookupInfo(loop, x);
        if (a != nullptr && a->induction_class == kInvariant) {
          InductionInfo* initial = LookupInfo(loop, entry_phi->InputAt(0));
          result = CreateInduction(kPeriodic,
                                   kNop,
                                   CreateInvariantOp(context, loop, kSub, a, initial),
                                   initial,
                                   /*fetch*/ nullptr,
                                   type);
        }
      }
    }
  }
  return result;
}

HInductionVarAnalysis::InductionInfo* HInductionVarAnalysis::SolveOp(const HLoopInformation* loop,
                                                                     HInstruction* entry_phi,
                                                                     HInstruction* instruction,
                                                                     HInstruction* x,
                                                                     HInstruction* y,
                                                                     InductionOp op,
                                                                     DataType::Type type) {
  // Solve within a tight cycle for a binary operation k = k op c or, for some op, k = c op k.
  if (entry_phi->InputCount() == 2 && instruction == entry_phi->InputAt(1)) {
    InductionInfo* c = nullptr;
    InductionInfo* b = LookupInfo(loop, y);
    if (b != nullptr && b->induction_class == kInvariant && entry_phi == x) {
      c = b;
    } else if (op != kDiv && op != kRem) {
      InductionInfo* a = LookupInfo(loop, x);
      if (a != nullptr && a->induction_class == kInvariant && entry_phi == y) {
        c = a;
      }
    }
    // Found suitable operand left or right?
    if (c != nullptr) {
      const HBasicBlock* context = instruction->GetBlock();
      InductionInfo* initial = LookupInfo(loop, entry_phi->InputAt(0));
      switch (op) {
        case kMul:
        case kDiv:
          // Restrict base of geometric induction to direct fetch.
          if (c->operation == kFetch) {
            return CreateInduction(kGeometric,
                                   op,
                                   initial,
                                   CreateConstant(0, type),
                                   c->fetch,
                                   type);
          }
          break;
        case kRem:
          // Idiomatic MOD wrap-around induction.
          return CreateInduction(kWrapAround,
                                 kNop,
                                 initial,
                                 CreateInvariantOp(context, loop, kRem, initial, c),
                                 /*fetch*/ nullptr,
                                 type);
        case kXor:
          // Idiomatic XOR periodic induction.
          return CreateInduction(kPeriodic,
                                 kNop,
                                 CreateInvariantOp(context, loop, kXor, initial, c),
                                 initial,
                                 /*fetch*/ nullptr,
                                 type);
        default:
          LOG(FATAL) << op;
          UNREACHABLE();
      }
    }
  }
  return nullptr;
}

HInductionVarAnalysis::InductionInfo* HInductionVarAnalysis::SolveTest(const HLoopInformation* loop,
                                                                       HInstruction* entry_phi,
                                                                       HInstruction* instruction,
                                                                       int64_t opposite_value,
                                                                       DataType::Type type) {
  // Detect hidden XOR construction in x = (x == false) or x = (x != true).
  const HBasicBlock* context = instruction->GetBlock();
  HInstruction* x = instruction->InputAt(0);
  HInstruction* y = instruction->InputAt(1);
  int64_t value = -1;
  if (IsExact(context, loop, LookupInfo(loop, x), &value) && value == opposite_value) {
    return SolveOp(loop, entry_phi, instruction, graph_->GetIntConstant(1), y, kXor, type);
  } else if (IsExact(context, loop, LookupInfo(loop, y), &value) && value == opposite_value) {
    return SolveOp(loop, entry_phi, instruction, x, graph_->GetIntConstant(1), kXor, type);
  }
  return nullptr;
}

HInductionVarAnalysis::InductionInfo* HInductionVarAnalysis::SolveConversion(
    const HLoopInformation* loop,
    HInstruction* entry_phi,
    HTypeConversion* conversion,
    const ScopedArenaSafeMap<HInstruction*, InductionInfo*>& cycle,
    /*inout*/ DataType::Type* type) {
  DataType::Type from = conversion->GetInputType();
  DataType::Type to = conversion->GetResultType();
  // A narrowing conversion is allowed as *last* operation of the cycle of a linear induction
  // with an initial value that fits the type, provided that the narrowest encountered type is
  // recorded with the induction to account for the precision loss. The narrower induction does
  // *not* transfer to any wider operations, however, since these may yield out-of-type values
  if (entry_phi->InputCount() == 2 && conversion == entry_phi->InputAt(1)) {
    int64_t min = DataType::MinValueOfIntegralType(to);
    int64_t max = DataType::MaxValueOfIntegralType(to);
    int64_t value = 0;
    const HBasicBlock* context = conversion->GetBlock();
    InductionInfo* initial = LookupInfo(loop, entry_phi->InputAt(0));
    if (IsNarrowingIntegralConversion(from, to) &&
        IsAtLeast(context, loop, initial, &value) && value >= min &&
        IsAtMost(context, loop, initial, &value)  && value <= max) {
      auto it = cycle.find(conversion->GetInput());
      if (it != cycle.end() && it->second->induction_class == kInvariant) {
        *type = to;
        return it->second;
      }
    }
  }
  return nullptr;
}

//
// Loop trip count analysis methods.
//

void HInductionVarAnalysis::VisitControl(const HLoopInformation* loop) {
  HInstruction* control = loop->GetHeader()->GetLastInstruction();
  if (control->IsIf()) {
    HIf* ifs = control->AsIf();
    HBasicBlock* if_true = ifs->IfTrueSuccessor();
    HBasicBlock* if_false = ifs->IfFalseSuccessor();
    HInstruction* if_expr = ifs->InputAt(0);
    // Determine if loop has following structure in header.
    // loop-header: ....
    //              if (condition) goto X
    if (if_expr->IsCondition()) {
      HCondition* condition = if_expr->AsCondition();
      const HBasicBlock* context = condition->GetBlock();
      InductionInfo* a = LookupInfo(loop, condition->InputAt(0));
      InductionInfo* b = LookupInfo(loop, condition->InputAt(1));
      DataType::Type type = ImplicitConversion(condition->InputAt(0)->GetType());
      // Determine if the loop control uses a known sequence on an if-exit (X outside) or on
      // an if-iterate (X inside), expressed as if-iterate when passed into VisitCondition().
      if (a == nullptr || b == nullptr) {
        return;  // Loop control is not a sequence.
      } else if (if_true->GetLoopInformation() != loop && if_false->GetLoopInformation() == loop) {
        VisitCondition(context, loop, if_false, a, b, type, condition->GetOppositeCondition());
      } else if (if_true->GetLoopInformation() == loop && if_false->GetLoopInformation() != loop) {
        VisitCondition(context, loop, if_true, a, b, type, condition->GetCondition());
      }
    }
  }
}

void HInductionVarAnalysis::VisitCondition(const HBasicBlock* context,
                                           const HLoopInformation* loop,
                                           HBasicBlock* body,
                                           InductionInfo* a,
                                           InductionInfo* b,
                                           DataType::Type type,
                                           IfCondition cmp) {
  if (a->induction_class == kInvariant && b->induction_class == kLinear) {
    // Swap condition if induction is at right-hand-side (e.g. U > i is same as i < U).
    switch (cmp) {
      case kCondLT: VisitCondition(context, loop, body, b, a, type, kCondGT); break;
      case kCondLE: VisitCondition(context, loop, body, b, a, type, kCondGE); break;
      case kCondGT: VisitCondition(context, loop, body, b, a, type, kCondLT); break;
      case kCondGE: VisitCondition(context, loop, body, b, a, type, kCondLE); break;
      case kCondNE: VisitCondition(context, loop, body, b, a, type, kCondNE); break;
      default: break;
    }
  } else if (a->induction_class == kLinear && b->induction_class == kInvariant) {
    // Analyze condition with induction at left-hand-side (e.g. i < U).
    InductionInfo* lower_expr = a->op_b;
    InductionInfo* upper_expr = b;
    InductionInfo* stride_expr = a->op_a;
    // Test for constant stride and integral condition.
    int64_t stride_value = 0;
    if (!IsExact(context, loop, stride_expr, &stride_value)) {
      return;  // unknown stride
    } else if (type != DataType::Type::kInt32 && type != DataType::Type::kInt64) {
      return;  // not integral
    }
    // Since loops with a i != U condition will not be normalized by the method below, first
    // try to rewrite a break-loop with terminating condition i != U into an equivalent loop
    // with non-strict end condition i <= U or i >= U if such a rewriting is possible and safe.
    if (cmp == kCondNE && RewriteBreakLoop(context, loop, body, stride_value, type)) {
      cmp = stride_value > 0 ? kCondLE : kCondGE;
    }
    // If this rewriting failed, try to rewrite condition i != U into strict end condition i < U
    // or i > U if this end condition is reached exactly (tested by verifying if the loop has a
    // unit stride and the non-strict condition would be always taken).
    if (cmp == kCondNE &&
        ((stride_value == +1 && IsTaken(context, loop, lower_expr, upper_expr, kCondLE)) ||
         (stride_value == -1 && IsTaken(context, loop, lower_expr, upper_expr, kCondGE)))) {
      cmp = stride_value > 0 ? kCondLT : kCondGT;
    }
    // A mismatch between the type of condition and the induction is only allowed if the,
    // necessarily narrower, induction range fits the narrower control.
    if (type != a->type &&
        !FitsNarrowerControl(context, loop, lower_expr, upper_expr, stride_value, a->type, cmp)) {
      return;  // mismatched type
    }
    // Normalize a linear loop control with a nonzero stride:
    //   stride > 0, either i < U or i <= U
    //   stride < 0, either i > U or i >= U
    if ((stride_value > 0 && (cmp == kCondLT || cmp == kCondLE)) ||
        (stride_value < 0 && (cmp == kCondGT || cmp == kCondGE))) {
      VisitTripCount(context, loop, lower_expr, upper_expr, stride_expr, stride_value, type, cmp);
    }
  }
}

void HInductionVarAnalysis::VisitTripCount(const HBasicBlock* context,
                                           const HLoopInformation* loop,
                                           InductionInfo* lower_expr,
                                           InductionInfo* upper_expr,
                                           InductionInfo* stride_expr,
                                           int64_t stride_value,
                                           DataType::Type type,
                                           IfCondition cmp) {
  // Any loop of the general form:
  //
  //    for (i = L; i <= U; i += S) // S > 0
  // or for (i = L; i >= U; i += S) // S < 0
  //      .. i ..
  //
  // can be normalized into:
  //
  //    for (n = 0; n < TC; n++) // where TC = (U + S - L) / S
  //      .. L + S * n ..
  //
  // taking the following into consideration:
  //
  // (1) Using the same precision, the TC (trip-count) expression should be interpreted as
  //     an unsigned entity, for example, as in the following loop that uses the full range:
  //     for (int i = INT_MIN; i < INT_MAX; i++) // TC = UINT_MAX
  // (2) The TC is only valid if the loop is taken, otherwise TC = 0, as in:
  //     for (int i = 12; i < U; i++) // TC = 0 when U <= 12
  //     If this cannot be determined at compile-time, the TC is only valid within the
  //     loop-body proper, not the loop-header unless enforced with an explicit taken-test.
  // (3) The TC is only valid if the loop is finite, otherwise TC has no value, as in:
  //     for (int i = 0; i <= U; i++) // TC = Inf when U = INT_MAX
  //     If this cannot be determined at compile-time, the TC is only valid when enforced
  //     with an explicit finite-test.
  // (4) For loops which early-exits, the TC forms an upper bound, as in:
  //     for (int i = 0; i < 10 && ....; i++) // TC <= 10
  InductionInfo* trip_count = upper_expr;
  const bool is_taken = IsTaken(context, loop, lower_expr, upper_expr, cmp);
  const bool is_finite = IsFinite(context, loop, upper_expr, stride_value, type, cmp);
  const bool cancels = (cmp == kCondLT || cmp == kCondGT) && std::abs(stride_value) == 1;
  if (!cancels) {
    // Convert exclusive integral inequality into inclusive integral inequality,
    // viz. condition i < U is i <= U - 1 and condition i > U is i >= U + 1.
    if (cmp == kCondLT) {
      trip_count = CreateInvariantOp(context, loop, kSub, trip_count, CreateConstant(1, type));
    } else if (cmp == kCondGT) {
      trip_count = CreateInvariantOp(context, loop, kAdd, trip_count, CreateConstant(1, type));
    }
    // Compensate for stride.
    trip_count = CreateInvariantOp(context, loop, kAdd, trip_count, stride_expr);
  }
  trip_count = CreateInvariantOp(context, loop, kSub, trip_count, lower_expr);
  trip_count = CreateInvariantOp(context, loop, kDiv, trip_count, stride_expr);
  // Assign the trip-count expression to the loop control. Clients that use the information
  // should be aware that the expression is only valid under the conditions listed above.
  InductionOp tcKind = kTripCountInBodyUnsafe;  // needs both tests
  if (is_taken && is_finite) {
    tcKind = kTripCountInLoop;  // needs neither test
  } else if (is_finite) {
    tcKind = kTripCountInBody;  // needs taken-test
  } else if (is_taken) {
    tcKind = kTripCountInLoopUnsafe;  // needs finite-test
  }
  InductionOp op = kNop;
  switch (cmp) {
    case kCondLT: op = kLT; break;
    case kCondLE: op = kLE; break;
    case kCondGT: op = kGT; break;
    case kCondGE: op = kGE; break;
    default:      LOG(FATAL) << "CONDITION UNREACHABLE";
  }
  // Associate trip count with control instruction, rather than the condition (even
  // though it's its use) since former provides a convenient use-free placeholder.
  HInstruction* control = loop->GetHeader()->GetLastInstruction();
  InductionInfo* taken_test = CreateInvariantOp(context, loop, op, lower_expr, upper_expr);
  DCHECK(control->IsIf());
  AssignInfo(loop, control, CreateTripCount(tcKind, trip_count, taken_test, type));
}

bool HInductionVarAnalysis::IsTaken(const HBasicBlock* context,
                                    const HLoopInformation* loop,
                                    InductionInfo* lower_expr,
                                    InductionInfo* upper_expr,
                                    IfCondition cmp) {
  int64_t lower_value;
  int64_t upper_value;
  switch (cmp) {
    case kCondLT:
      return IsAtMost(context, loop, lower_expr, &lower_value)
          && IsAtLeast(context, loop, upper_expr, &upper_value)
          && lower_value < upper_value;
    case kCondLE:
      return IsAtMost(context, loop, lower_expr, &lower_value)
          && IsAtLeast(context, loop, upper_expr, &upper_value)
          && lower_value <= upper_value;
    case kCondGT:
      return IsAtLeast(context, loop, lower_expr, &lower_value)
          && IsAtMost(context, loop, upper_expr, &upper_value)
          && lower_value > upper_value;
    case kCondGE:
      return IsAtLeast(context, loop, lower_expr, &lower_value)
          && IsAtMost(context, loop, upper_expr, &upper_value)
          && lower_value >= upper_value;
    default:
      LOG(FATAL) << "CONDITION UNREACHABLE";
      UNREACHABLE();
  }
}

bool HInductionVarAnalysis::IsFinite(const HBasicBlock* context,
                                     const HLoopInformation* loop,
                                     InductionInfo* upper_expr,
                                     int64_t stride_value,
                                     DataType::Type type,
                                     IfCondition cmp) {
  int64_t min = DataType::MinValueOfIntegralType(type);
  int64_t max = DataType::MaxValueOfIntegralType(type);
  // Some rules under which it is certain at compile-time that the loop is finite.
  int64_t value;
  switch (cmp) {
    case kCondLT:
      return stride_value == 1 ||
          (IsAtMost(context, loop, upper_expr, &value) && value <= (max - stride_value + 1));
    case kCondLE:
      return (IsAtMost(context, loop, upper_expr, &value) && value <= (max - stride_value));
    case kCondGT:
      return stride_value == -1 ||
          (IsAtLeast(context, loop, upper_expr, &value) && value >= (min - stride_value - 1));
    case kCondGE:
      return (IsAtLeast(context, loop, upper_expr, &value) && value >= (min - stride_value));
    default:
      LOG(FATAL) << "CONDITION UNREACHABLE";
      UNREACHABLE();
  }
}

bool HInductionVarAnalysis::FitsNarrowerControl(const HBasicBlock* context,
                                                const HLoopInformation* loop,
                                                InductionInfo* lower_expr,
                                                InductionInfo* upper_expr,
                                                int64_t stride_value,
                                                DataType::Type type,
                                                IfCondition cmp) {
  int64_t min = DataType::MinValueOfIntegralType(type);
  int64_t max = DataType::MaxValueOfIntegralType(type);
  // Inclusive test need one extra.
  if (stride_value != 1 && stride_value != -1) {
    return false;  // non-unit stride
  } else if (cmp == kCondLE) {
    max--;
  } else if (cmp == kCondGE) {
    min++;
  }
  // Do both bounds fit the range?
  int64_t value = 0;
  return IsAtLeast(context, loop, lower_expr, &value) && value >= min &&
         IsAtMost(context, loop, lower_expr, &value)  && value <= max &&
         IsAtLeast(context, loop, upper_expr, &value) && value >= min &&
         IsAtMost(context, loop, upper_expr, &value)  && value <= max;
}

bool HInductionVarAnalysis::RewriteBreakLoop(const HBasicBlock* context,
                                             const HLoopInformation* loop,
                                             HBasicBlock* body,
                                             int64_t stride_value,
                                             DataType::Type type) {
  // Only accept unit stride.
  if (std::abs(stride_value) != 1) {
    return false;
  }
  // Simple terminating i != U condition, used nowhere else.
  HIf* ifs = loop->GetHeader()->GetLastInstruction()->AsIf();
  HInstruction* cond = ifs->InputAt(0);
  if (ifs->GetPrevious() != cond || !cond->HasOnlyOneNonEnvironmentUse()) {
    return false;
  }
  int c = LookupInfo(loop, cond->InputAt(0))->induction_class == kLinear ? 0 : 1;
  HInstruction* index = cond->InputAt(c);
  HInstruction* upper = cond->InputAt(1 - c);
  // Safe to rewrite into i <= U?
  IfCondition cmp = stride_value > 0 ? kCondLE : kCondGE;
  if (!index->IsPhi() ||
      !IsFinite(context, loop, LookupInfo(loop, upper), stride_value, type, cmp)) {
    return false;
  }
  // Body consists of update to index i only, used nowhere else.
  if (body->GetSuccessors().size() != 1 ||
      body->GetSingleSuccessor() != loop->GetHeader() ||
      !body->GetPhis().IsEmpty() ||
      body->GetInstructions().IsEmpty() ||
      body->GetFirstInstruction() != index->InputAt(1) ||
      !body->GetFirstInstruction()->HasOnlyOneNonEnvironmentUse() ||
      !body->GetFirstInstruction()->GetNext()->IsGoto()) {
    return false;
  }
  // Always taken or guarded by enclosing condition.
  if (!IsTaken(context, loop, LookupInfo(loop, index)->op_b, LookupInfo(loop, upper), cmp) &&
      !IsGuardedBy(loop, cmp, index->InputAt(0), upper)) {
    return false;
  }
  // Test if break-loop body can be written, and do so on success.
  if (RewriteBreakLoopBody(loop, body, cond, index, upper, /*rewrite*/ false)) {
    RewriteBreakLoopBody(loop, body, cond, index, upper, /*rewrite*/ true);
  } else {
    return false;
  }
  // Rewrite condition in HIR.
  if (ifs->IfTrueSuccessor() != body) {
    cmp = (cmp == kCondLE) ? kCondGT : kCondLT;
  }
  HInstruction* rep = nullptr;
  switch (cmp) {
    case kCondLT: rep = new (graph_->GetAllocator()) HLessThan(index, upper); break;
    case kCondGT: rep = new (graph_->GetAllocator()) HGreaterThan(index, upper); break;
    case kCondLE: rep = new (graph_->GetAllocator()) HLessThanOrEqual(index, upper); break;
    case kCondGE: rep = new (graph_->GetAllocator()) HGreaterThanOrEqual(index, upper); break;
    default: LOG(FATAL) << cmp; UNREACHABLE();
  }
  loop->GetHeader()->ReplaceAndRemoveInstructionWith(cond, rep);
  return true;
}

//
// Helper methods.
//

void HInductionVarAnalysis::AssignInfo(const HLoopInformation* loop,
                                       HInstruction* instruction,
                                       InductionInfo* info) {
  auto it = induction_.find(loop);
  if (it == induction_.end()) {
    it = induction_.Put(loop,
                        ArenaSafeMap<HInstruction*, InductionInfo*>(
                            std::less<HInstruction*>(),
                            graph_->GetAllocator()->Adapter(kArenaAllocInductionVarAnalysis)));
  }
  it->second.Put(instruction, info);
}

HInductionVarAnalysis::InductionInfo* HInductionVarAnalysis::LookupInfo(
    const HLoopInformation* loop,
    HInstruction* instruction) {
  auto it = induction_.find(loop);
  if (it != induction_.end()) {
    auto loop_it = it->second.find(instruction);
    if (loop_it != it->second.end()) {
      return loop_it->second;
    }
  }
  if (loop->IsDefinedOutOfTheLoop(instruction)) {
    InductionInfo* info = CreateInvariantFetch(instruction);
    AssignInfo(loop, instruction, info);
    return info;
  }
  return nullptr;
}

HInductionVarAnalysis::InductionInfo* HInductionVarAnalysis::CreateConstant(int64_t value,
                                                                            DataType::Type type) {
  HInstruction* constant;
  switch (type) {
    case DataType::Type::kFloat64: constant = graph_->GetDoubleConstant(value); break;
    case DataType::Type::kFloat32: constant = graph_->GetFloatConstant(value);  break;
    case DataType::Type::kInt64:   constant = graph_->GetLongConstant(value);   break;
    default:                       constant = graph_->GetIntConstant(value);    break;
  }
  return CreateInvariantFetch(constant);
}

HInductionVarAnalysis::InductionInfo* HInductionVarAnalysis::CreateSimplifiedInvariant(
    const HBasicBlock* context,
    const HLoopInformation* loop,
    InductionOp op,
    InductionInfo* a,
    InductionInfo* b) {
  // Perform some light-weight simplifications during construction of a new invariant.
  // This often safes memory and yields a more concise representation of the induction.
  // More exhaustive simplifications are done by later phases once induction nodes are
  // translated back into HIR code (e.g. by loop optimizations or BCE).
  int64_t value = -1;
  if (IsExact(context, loop, a, &value)) {
    if (value == 0) {
      // Simplify 0 + b = b, 0 ^ b = b, 0 * b = 0.
      if (op == kAdd || op == kXor) {
        return b;
      } else if (op == kMul) {
        return a;
      }
    } else if (op == kMul) {
      // Simplify 1 * b = b, -1 * b = -b
      if (value == 1) {
        return b;
      } else if (value == -1) {
        return CreateSimplifiedInvariant(context, loop, kNeg, nullptr, b);
      }
    }
  }
  if (IsExact(context, loop, b, &value)) {
    if (value == 0) {
      // Simplify a + 0 = a, a - 0 = a, a ^ 0 = a, a * 0 = 0, -0 = 0.
      if (op == kAdd || op == kSub || op == kXor) {
        return a;
      } else if (op == kMul || op == kNeg) {
        return b;
      }
    } else if (op == kMul || op == kDiv) {
      // Simplify a * 1 = a, a / 1 = a, a * -1 = -a, a / -1 = -a
      if (value == 1) {
        return a;
      } else if (value == -1) {
        return CreateSimplifiedInvariant(context, loop, kNeg, nullptr, a);
      }
    }
  } else if (b->operation == kNeg) {
    // Simplify a + (-b) = a - b, a - (-b) = a + b, -(-b) = b.
    if (op == kAdd) {
      return CreateSimplifiedInvariant(context, loop, kSub, a, b->op_b);
    } else if (op == kSub) {
      return CreateSimplifiedInvariant(context, loop, kAdd, a, b->op_b);
    } else if (op == kNeg) {
      return b->op_b;
    }
  } else if (b->operation == kSub) {
    // Simplify - (a - b) = b - a.
    if (op == kNeg) {
      return CreateSimplifiedInvariant(context, loop, kSub, b->op_b, b->op_a);
    }
  }
  return new (graph_->GetAllocator()) InductionInfo(
      kInvariant, op, a, b, nullptr, ImplicitConversion(b->type));
}

HInstruction* HInductionVarAnalysis::GetShiftConstant(const HLoopInformation* loop,
                                                      HInstruction* instruction,
                                                      InductionInfo* initial) {
  DCHECK(instruction->IsShl() || instruction->IsShr() || instruction->IsUShr());
  const HBasicBlock* context = instruction->GetBlock();
  // Shift-rights are only the same as division for non-negative initial inputs.
  // Otherwise we would round incorrectly.
  if (initial != nullptr) {
    int64_t value = -1;
    if (!IsAtLeast(context, loop, initial, &value) || value < 0) {
      return nullptr;
    }
  }
  // Obtain the constant needed to treat shift as equivalent multiplication or division.
  // This yields an existing instruction if the constant is already there. Otherwise, this
  // has a side effect on the HIR. The restriction on the shift factor avoids generating a
  // negative constant (viz. 1 << 31 and 1L << 63 set the sign bit). The code assumes that
  // generalization for shift factors outside [0,32) and [0,64) ranges is done earlier.
  InductionInfo* b = LookupInfo(loop, instruction->InputAt(1));
  int64_t value = -1;
  if (IsExact(context, loop, b, &value)) {
    DataType::Type type = instruction->InputAt(0)->GetType();
    if (type == DataType::Type::kInt32 && 0 <= value && value < 31) {
      return graph_->GetIntConstant(1 << value);
    }
    if (type == DataType::Type::kInt64 && 0 <= value && value < 63) {
      return graph_->GetLongConstant(1L << value);
    }
  }
  return nullptr;
}

void HInductionVarAnalysis::AssignCycle(HPhi* phi, ArrayRef<HInstruction* const> scc) {
  ArenaSet<HInstruction*>* set = &cycles_.Put(phi, ArenaSet<HInstruction*>(
      graph_->GetAllocator()->Adapter(kArenaAllocInductionVarAnalysis)))->second;
  for (HInstruction* i : scc) {
    set->insert(i);
  }
}

ArenaSet<HInstruction*>* HInductionVarAnalysis::LookupCycle(HPhi* phi) {
  auto it = cycles_.find(phi);
  if (it != cycles_.end()) {
    return &it->second;
  }
  return nullptr;
}

bool HInductionVarAnalysis::IsExact(const HBasicBlock* context,
                                    const HLoopInformation* loop,
                                    InductionInfo* info,
                                    /*out*/int64_t* value) {
  InductionVarRange range(this);
  return range.IsConstant(context, loop, info, InductionVarRange::kExact, value);
}

bool HInductionVarAnalysis::IsAtMost(const HBasicBlock* context,
                                     const HLoopInformation* loop,
                                     InductionInfo* info,
                                     /*out*/int64_t* value) {
  InductionVarRange range(this);
  return range.IsConstant(context, loop, info, InductionVarRange::kAtMost, value);
}

bool HInductionVarAnalysis::IsAtLeast(const HBasicBlock* context,
                                      const HLoopInformation* loop,
                                      InductionInfo* info,
                                      /*out*/int64_t* value) {
  InductionVarRange range(this);
  return range.IsConstant(context, loop, info, InductionVarRange::kAtLeast, value);
}

bool HInductionVarAnalysis::IsNarrowingLinear(InductionInfo* info) {
  return info != nullptr &&
      info->induction_class == kLinear &&
      (info->type == DataType::Type::kUint8 ||
       info->type == DataType::Type::kInt8 ||
       info->type == DataType::Type::kUint16 ||
       info->type == DataType::Type::kInt16 ||
       (info->type == DataType::Type::kInt32 && (info->op_a->type == DataType::Type::kInt64 ||
                                                 info->op_b->type == DataType::Type::kInt64)));
}

bool HInductionVarAnalysis::InductionEqual(InductionInfo* info1,
                                           InductionInfo* info2) {
  // Test structural equality only, without accounting for simplifications.
  if (info1 != nullptr && info2 != nullptr) {
    return
        info1->induction_class == info2->induction_class &&
        info1->operation       == info2->operation       &&
        info1->fetch           == info2->fetch           &&
        info1->type            == info2->type            &&
        InductionEqual(info1->op_a, info2->op_a)         &&
        InductionEqual(info1->op_b, info2->op_b);
  }
  // Otherwise only two nullptrs are considered equal.
  return info1 == info2;
}

std::string HInductionVarAnalysis::FetchToString(HInstruction* fetch) {
  DCHECK(fetch != nullptr);
  if (fetch->IsIntConstant()) {
    return std::to_string(fetch->AsIntConstant()->GetValue());
  } else if (fetch->IsLongConstant()) {
    return std::to_string(fetch->AsLongConstant()->GetValue());
  }
  return std::to_string(fetch->GetId()) + ":" + fetch->DebugName();
}

std::string HInductionVarAnalysis::InductionToString(InductionInfo* info) {
  if (info != nullptr) {
    if (info->induction_class == kInvariant) {
      std::string inv = "(";
      inv += InductionToString(info->op_a);
      switch (info->operation) {
        case kNop:   inv += " @ ";  break;
        case kAdd:   inv += " + ";  break;
        case kSub:
        case kNeg:   inv += " - ";  break;
        case kMul:   inv += " * ";  break;
        case kDiv:   inv += " / ";  break;
        case kRem:   inv += " % ";  break;
        case kXor:   inv += " ^ ";  break;
        case kLT:    inv += " < ";  break;
        case kLE:    inv += " <= "; break;
        case kGT:    inv += " > ";  break;
        case kGE:    inv += " >= "; break;
        case kFetch: inv += FetchToString(info->fetch); break;
        case kTripCountInLoop:       inv += " (TC-loop) ";        break;
        case kTripCountInBody:       inv += " (TC-body) ";        break;
        case kTripCountInLoopUnsafe: inv += " (TC-loop-unsafe) "; break;
        case kTripCountInBodyUnsafe: inv += " (TC-body-unsafe) "; break;
      }
      inv += InductionToString(info->op_b);
      inv += ")";
      return inv;
    } else {
      if (info->induction_class == kLinear) {
        DCHECK(info->operation == kNop);
        return "(" + InductionToString(info->op_a) + " * i + " +
                     InductionToString(info->op_b) + "):" +
                     DataType::PrettyDescriptor(info->type);
      } else if (info->induction_class == kPolynomial) {
        DCHECK(info->operation == kNop);
        return "poly(sum_lt(" + InductionToString(info->op_a) + ") + " +
                                InductionToString(info->op_b) + "):" +
                                DataType::PrettyDescriptor(info->type);
      } else if (info->induction_class == kGeometric) {
        DCHECK(info->operation == kMul || info->operation == kDiv);
        DCHECK(info->fetch != nullptr);
        return "geo(" + InductionToString(info->op_a) + " * " +
                        FetchToString(info->fetch) +
                        (info->operation == kMul ? " ^ i + " : " ^ -i + ") +
                        InductionToString(info->op_b) + "):" +
                        DataType::PrettyDescriptor(info->type);
      } else if (info->induction_class == kWrapAround) {
        DCHECK(info->operation == kNop);
        return "wrap(" + InductionToString(info->op_a) + ", " +
                         InductionToString(info->op_b) + "):" +
                         DataType::PrettyDescriptor(info->type);
      } else if (info->induction_class == kPeriodic) {
        DCHECK(info->operation == kNop);
        return "periodic(" + InductionToString(info->op_a) + ", " +
                             InductionToString(info->op_b) + "):" +
                             DataType::PrettyDescriptor(info->type);
      }
    }
  }
  return "";
}

void HInductionVarAnalysis::CalculateLoopHeaderPhisInARow(
    HPhi* initial_phi,
    ScopedArenaSafeMap<HPhi*, int>& cached_values,
    ScopedArenaAllocator& allocator) {
  DCHECK(initial_phi->IsLoopHeaderPhi());
  ScopedArenaQueue<HPhi*> worklist(allocator.Adapter(kArenaAllocInductionVarAnalysis));
  worklist.push(initial_phi);
  // Used to check which phis are in the current chain we are checking.
  ScopedArenaSet<HPhi*> phis_in_chain(allocator.Adapter(kArenaAllocInductionVarAnalysis));
  while (!worklist.empty()) {
    HPhi* current_phi = worklist.front();
    DCHECK(current_phi->IsLoopHeaderPhi());
    if (cached_values.find(current_phi) != cached_values.end()) {
      // Already processed.
      worklist.pop();
      continue;
    }

    phis_in_chain.insert(current_phi);
    int max_value = 0;
    bool pushed_other_phis = false;
    for (size_t index = 0; index < current_phi->InputCount(); index++) {
      // If the input is not a loop header phi, we only have 1 (current_phi).
      int current_value = 1;
      if (current_phi->InputAt(index)->IsLoopHeaderPhi()) {
        HPhi* loop_header_phi = current_phi->InputAt(index)->AsPhi();
        auto it = cached_values.find(loop_header_phi);
        if (it != cached_values.end()) {
          current_value += it->second;
        } else if (phis_in_chain.find(current_phi) == phis_in_chain.end()) {
          // Push phis which aren't in the chain already to be processed.
          pushed_other_phis = true;
          worklist.push(loop_header_phi);
        }
        // Phis in the chain will get processed later. We keep `current_value` as 1 to avoid
        // double counting `loop_header_phi`.
      }
      max_value = std::max(max_value, current_value);
    }

    if (!pushed_other_phis) {
      // Only finish processing after all inputs were processed.
      worklist.pop();
      phis_in_chain.erase(current_phi);
      cached_values.FindOrAdd(current_phi, max_value);
    }
  }
}

bool HInductionVarAnalysis::IsPathologicalCase() {
  ScopedArenaAllocator local_allocator(graph_->GetArenaStack());
  ScopedArenaSafeMap<HPhi*, int> cached_values(
      std::less<HPhi*>(), local_allocator.Adapter(kArenaAllocInductionVarAnalysis));

  // Due to how our induction passes work, we will take a lot of time compiling if we have several
  // loop header phis in a row. If we have more than 15 different loop header phis in a row, we
  // don't perform the analysis.
  constexpr int kMaximumLoopHeaderPhisInARow = 15;

  for (HBasicBlock* block : graph_->GetReversePostOrder()) {
    if (!block->IsLoopHeader()) {
      continue;
    }

    for (HInstructionIterator it(block->GetPhis()); !it.Done(); it.Advance()) {
      DCHECK(it.Current()->IsLoopHeaderPhi());
      HPhi* phi = it.Current()->AsPhi();
      CalculateLoopHeaderPhisInARow(phi, cached_values, local_allocator);
      DCHECK(cached_values.find(phi) != cached_values.end())
          << " we should have a value for Phi " << phi->GetId()
          << " in block " << phi->GetBlock()->GetBlockId();
      if (cached_values.find(phi)->second > kMaximumLoopHeaderPhisInARow) {
        return true;
      }
    }
  }

  return false;
}

}  // namespace art
