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

#include "loop_optimization.h"

#include "arch/arm/instruction_set_features_arm.h"
#include "arch/arm64/instruction_set_features_arm64.h"
#include "arch/instruction_set.h"
#include "arch/x86/instruction_set_features_x86.h"
#include "arch/x86_64/instruction_set_features_x86_64.h"
#include "code_generator.h"
#include "driver/compiler_options.h"
#include "linear_order.h"
#include "mirror/array-inl.h"
#include "mirror/string.h"

namespace art HIDDEN {

// Enables vectorization (SIMDization) in the loop optimizer.
static constexpr bool kEnableVectorization = true;

//
// Static helpers.
//

// Base alignment for arrays/strings guaranteed by the Android runtime.
static uint32_t BaseAlignment() {
  return kObjectAlignment;
}

// Hidden offset for arrays/strings guaranteed by the Android runtime.
static uint32_t HiddenOffset(DataType::Type type, bool is_string_char_at) {
  return is_string_char_at
      ? mirror::String::ValueOffset().Uint32Value()
      : mirror::Array::DataOffset(DataType::Size(type)).Uint32Value();
}

// Remove the instruction from the graph. A bit more elaborate than the usual
// instruction removal, since there may be a cycle in the use structure.
static void RemoveFromCycle(HInstruction* instruction) {
  instruction->RemoveAsUserOfAllInputs();
  instruction->RemoveEnvironmentUsers();
  instruction->GetBlock()->RemoveInstructionOrPhi(instruction, /*ensure_safety=*/ false);
  RemoveEnvironmentUses(instruction);
  ResetEnvironmentInputRecords(instruction);
}

// Detect a goto block and sets succ to the single successor.
static bool IsGotoBlock(HBasicBlock* block, /*out*/ HBasicBlock** succ) {
  if (block->GetPredecessors().size() == 1 &&
      block->GetSuccessors().size() == 1 &&
      block->IsSingleGoto()) {
    *succ = block->GetSingleSuccessor();
    return true;
  }
  return false;
}

// Detect an early exit loop.
static bool IsEarlyExit(HLoopInformation* loop_info) {
  HBlocksInLoopReversePostOrderIterator it_loop(*loop_info);
  for (it_loop.Advance(); !it_loop.Done(); it_loop.Advance()) {
    for (HBasicBlock* successor : it_loop.Current()->GetSuccessors()) {
      if (!loop_info->Contains(*successor)) {
        return true;
      }
    }
  }
  return false;
}

// Forward declaration.
static bool IsZeroExtensionAndGet(HInstruction* instruction,
                                  DataType::Type type,
                                  /*out*/ HInstruction** operand);

// Detect a sign extension in instruction from the given type.
// Returns the promoted operand on success.
static bool IsSignExtensionAndGet(HInstruction* instruction,
                                  DataType::Type type,
                                  /*out*/ HInstruction** operand) {
  // Accept any already wider constant that would be handled properly by sign
  // extension when represented in the *width* of the given narrower data type
  // (the fact that Uint8/Uint16 normally zero extend does not matter here).
  int64_t value = 0;
  if (IsInt64AndGet(instruction, /*out*/ &value)) {
    switch (type) {
      case DataType::Type::kUint8:
      case DataType::Type::kInt8:
        if (IsInt<8>(value)) {
          *operand = instruction;
          return true;
        }
        return false;
      case DataType::Type::kUint16:
      case DataType::Type::kInt16:
        if (IsInt<16>(value)) {
          *operand = instruction;
          return true;
        }
        return false;
      default:
        return false;
    }
  }
  // An implicit widening conversion of any signed expression sign-extends.
  if (instruction->GetType() == type) {
    switch (type) {
      case DataType::Type::kInt8:
      case DataType::Type::kInt16:
        *operand = instruction;
        return true;
      default:
        return false;
    }
  }
  // An explicit widening conversion of a signed expression sign-extends.
  if (instruction->IsTypeConversion()) {
    HInstruction* conv = instruction->InputAt(0);
    DataType::Type from = conv->GetType();
    switch (instruction->GetType()) {
      case DataType::Type::kInt32:
      case DataType::Type::kInt64:
        if (type == from && (from == DataType::Type::kInt8 ||
                             from == DataType::Type::kInt16 ||
                             from == DataType::Type::kInt32)) {
          *operand = conv;
          return true;
        }
        return false;
      case DataType::Type::kInt16:
        return type == DataType::Type::kUint16 &&
               from == DataType::Type::kUint16 &&
               IsZeroExtensionAndGet(instruction->InputAt(0), type, /*out*/ operand);
      default:
        return false;
    }
  }
  return false;
}

// Detect a zero extension in instruction from the given type.
// Returns the promoted operand on success.
static bool IsZeroExtensionAndGet(HInstruction* instruction,
                                  DataType::Type type,
                                  /*out*/ HInstruction** operand) {
  // Accept any already wider constant that would be handled properly by zero
  // extension when represented in the *width* of the given narrower data type
  // (the fact that Int8/Int16 normally sign extend does not matter here).
  int64_t value = 0;
  if (IsInt64AndGet(instruction, /*out*/ &value)) {
    switch (type) {
      case DataType::Type::kUint8:
      case DataType::Type::kInt8:
        if (IsUint<8>(value)) {
          *operand = instruction;
          return true;
        }
        return false;
      case DataType::Type::kUint16:
      case DataType::Type::kInt16:
        if (IsUint<16>(value)) {
          *operand = instruction;
          return true;
        }
        return false;
      default:
        return false;
    }
  }
  // An implicit widening conversion of any unsigned expression zero-extends.
  if (instruction->GetType() == type) {
    switch (type) {
      case DataType::Type::kUint8:
      case DataType::Type::kUint16:
        *operand = instruction;
        return true;
      default:
        return false;
    }
  }
  // An explicit widening conversion of an unsigned expression zero-extends.
  if (instruction->IsTypeConversion()) {
    HInstruction* conv = instruction->InputAt(0);
    DataType::Type from = conv->GetType();
    switch (instruction->GetType()) {
      case DataType::Type::kInt32:
      case DataType::Type::kInt64:
        if (type == from && from == DataType::Type::kUint16) {
          *operand = conv;
          return true;
        }
        return false;
      case DataType::Type::kUint16:
        return type == DataType::Type::kInt16 &&
               from == DataType::Type::kInt16 &&
               IsSignExtensionAndGet(instruction->InputAt(0), type, /*out*/ operand);
      default:
        return false;
    }
  }
  return false;
}

// Detect situations with same-extension narrower operands.
// Returns true on success and sets is_unsigned accordingly.
static bool IsNarrowerOperands(HInstruction* a,
                               HInstruction* b,
                               DataType::Type type,
                               /*out*/ HInstruction** r,
                               /*out*/ HInstruction** s,
                               /*out*/ bool* is_unsigned) {
  DCHECK(a != nullptr && b != nullptr);
  // Look for a matching sign extension.
  DataType::Type stype = HVecOperation::ToSignedType(type);
  if (IsSignExtensionAndGet(a, stype, r) && IsSignExtensionAndGet(b, stype, s)) {
    *is_unsigned = false;
    return true;
  }
  // Look for a matching zero extension.
  DataType::Type utype = HVecOperation::ToUnsignedType(type);
  if (IsZeroExtensionAndGet(a, utype, r) && IsZeroExtensionAndGet(b, utype, s)) {
    *is_unsigned = true;
    return true;
  }
  return false;
}

// As above, single operand.
static bool IsNarrowerOperand(HInstruction* a,
                              DataType::Type type,
                              /*out*/ HInstruction** r,
                              /*out*/ bool* is_unsigned) {
  DCHECK(a != nullptr);
  // Look for a matching sign extension.
  DataType::Type stype = HVecOperation::ToSignedType(type);
  if (IsSignExtensionAndGet(a, stype, r)) {
    *is_unsigned = false;
    return true;
  }
  // Look for a matching zero extension.
  DataType::Type utype = HVecOperation::ToUnsignedType(type);
  if (IsZeroExtensionAndGet(a, utype, r)) {
    *is_unsigned = true;
    return true;
  }
  return false;
}

// Compute relative vector length based on type difference.
static uint32_t GetOtherVL(DataType::Type other_type, DataType::Type vector_type, uint32_t vl) {
  DCHECK(DataType::IsIntegralType(other_type));
  DCHECK(DataType::IsIntegralType(vector_type));
  DCHECK_GE(DataType::SizeShift(other_type), DataType::SizeShift(vector_type));
  return vl >> (DataType::SizeShift(other_type) - DataType::SizeShift(vector_type));
}

// Detect up to two added operands a and b and an acccumulated constant c.
static bool IsAddConst(HInstruction* instruction,
                       /*out*/ HInstruction** a,
                       /*out*/ HInstruction** b,
                       /*out*/ int64_t* c,
                       int32_t depth = 8) {  // don't search too deep
  int64_t value = 0;
  // Enter add/sub while still within reasonable depth.
  if (depth > 0) {
    if (instruction->IsAdd()) {
      return IsAddConst(instruction->InputAt(0), a, b, c, depth - 1) &&
             IsAddConst(instruction->InputAt(1), a, b, c, depth - 1);
    } else if (instruction->IsSub() &&
               IsInt64AndGet(instruction->InputAt(1), &value)) {
      *c -= value;
      return IsAddConst(instruction->InputAt(0), a, b, c, depth - 1);
    }
  }
  // Otherwise, deal with leaf nodes.
  if (IsInt64AndGet(instruction, &value)) {
    *c += value;
    return true;
  } else if (*a == nullptr) {
    *a = instruction;
    return true;
  } else if (*b == nullptr) {
    *b = instruction;
    return true;
  }
  return false;  // too many operands
}

// Detect a + b + c with optional constant c.
static bool IsAddConst2(HGraph* graph,
                        HInstruction* instruction,
                        /*out*/ HInstruction** a,
                        /*out*/ HInstruction** b,
                        /*out*/ int64_t* c) {
  // We want an actual add/sub and not the trivial case where {b: 0, c: 0}.
  if (IsAddOrSub(instruction) && IsAddConst(instruction, a, b, c) && *a != nullptr) {
    if (*b == nullptr) {
      // Constant is usually already present, unless accumulated.
      *b = graph->GetConstant(instruction->GetType(), (*c));
      *c = 0;
    }
    return true;
  }
  return false;
}

// Detect a direct a - b or a hidden a - (-c).
static bool IsSubConst2(HGraph* graph,
                        HInstruction* instruction,
                        /*out*/ HInstruction** a,
                        /*out*/ HInstruction** b) {
  int64_t c = 0;
  if (instruction->IsSub()) {
    *a = instruction->InputAt(0);
    *b = instruction->InputAt(1);
    return true;
  } else if (IsAddConst(instruction, a, b, &c) && *a != nullptr && *b == nullptr) {
    // Constant for the hidden subtraction.
    *b = graph->GetConstant(instruction->GetType(), -c);
    return true;
  }
  return false;
}

// Detect reductions of the following forms,
//   x = x_phi + ..
//   x = x_phi - ..
static bool HasReductionFormat(HInstruction* reduction, HInstruction* phi) {
  if (reduction->IsAdd()) {
    return (reduction->InputAt(0) == phi && reduction->InputAt(1) != phi) ||
           (reduction->InputAt(0) != phi && reduction->InputAt(1) == phi);
  } else if (reduction->IsSub()) {
    return (reduction->InputAt(0) == phi && reduction->InputAt(1) != phi);
  }
  return false;
}

// Translates vector operation to reduction kind.
static HVecReduce::ReductionKind GetReductionKind(HVecOperation* reduction) {
  if (reduction->IsVecAdd()  ||
      reduction->IsVecSub() ||
      reduction->IsVecSADAccumulate() ||
      reduction->IsVecDotProd()) {
    return HVecReduce::kSum;
  }
  LOG(FATAL) << "Unsupported SIMD reduction " << reduction->GetId();
  UNREACHABLE();
}

// Test vector restrictions.
static bool HasVectorRestrictions(uint64_t restrictions, uint64_t tested) {
  return (restrictions & tested) != 0;
}

// Insert an instruction at the end of the block, with safe checks.
inline HInstruction* Insert(HBasicBlock* block, HInstruction* instruction) {
  DCHECK(block != nullptr);
  DCHECK(instruction != nullptr);
  block->InsertInstructionBefore(instruction, block->GetLastInstruction());
  return instruction;
}

// Check that instructions from the induction sets are fully removed: have no uses
// and no other instructions use them.
static bool CheckInductionSetFullyRemoved(ScopedArenaSet<HInstruction*>* iset) {
  for (HInstruction* instr : *iset) {
    if (instr->GetBlock() != nullptr ||
        !instr->GetUses().empty() ||
        !instr->GetEnvUses().empty() ||
        HasEnvironmentUsedByOthers(instr)) {
      return false;
    }
  }
  return true;
}

// Tries to statically evaluate condition of the specified "HIf" for other condition checks.
static void TryToEvaluateIfCondition(HIf* instruction, HGraph* graph) {
  HInstruction* cond = instruction->InputAt(0);

  // If a condition 'cond' is evaluated in an HIf instruction then in the successors of the
  // IF_BLOCK we statically know the value of the condition 'cond' (TRUE in TRUE_SUCC, FALSE in
  // FALSE_SUCC). Using that we can replace another evaluation (use) EVAL of the same 'cond'
  // with TRUE value (FALSE value) if every path from the ENTRY_BLOCK to EVAL_BLOCK contains the
  // edge HIF_BLOCK->TRUE_SUCC (HIF_BLOCK->FALSE_SUCC).
  //     if (cond) {               if(cond) {
  //       if (cond) {}              if (1) {}
  //     } else {        =======>  } else {
  //       if (cond) {}              if (0) {}
  //     }                         }
  if (!cond->IsConstant()) {
    HBasicBlock* true_succ = instruction->IfTrueSuccessor();
    HBasicBlock* false_succ = instruction->IfFalseSuccessor();

    DCHECK_EQ(true_succ->GetPredecessors().size(), 1u);
    DCHECK_EQ(false_succ->GetPredecessors().size(), 1u);

    const HUseList<HInstruction*>& uses = cond->GetUses();
    for (auto it = uses.begin(), end = uses.end(); it != end; /* ++it below */) {
      HInstruction* user = it->GetUser();
      size_t index = it->GetIndex();
      HBasicBlock* user_block = user->GetBlock();
      // Increment `it` now because `*it` may disappear thanks to user->ReplaceInput().
      ++it;
      if (true_succ->Dominates(user_block)) {
        user->ReplaceInput(graph->GetIntConstant(1), index);
      } else if (false_succ->Dominates(user_block)) {
        user->ReplaceInput(graph->GetIntConstant(0), index);
      }
    }
  }
}

// Peel the first 'count' iterations of the loop.
static void PeelByCount(HLoopInformation* loop_info,
                        int count,
                        InductionVarRange* induction_range) {
  for (int i = 0; i < count; i++) {
    // Perform peeling.
    LoopClonerSimpleHelper helper(loop_info, induction_range);
    helper.DoPeeling();
  }
}

// Returns the narrower type out of instructions a and b types.
static DataType::Type GetNarrowerType(HInstruction* a, HInstruction* b) {
  DataType::Type type = a->GetType();
  if (DataType::Size(b->GetType()) < DataType::Size(type)) {
    type = b->GetType();
  }
  if (a->IsTypeConversion() &&
      DataType::Size(a->InputAt(0)->GetType()) < DataType::Size(type)) {
    type = a->InputAt(0)->GetType();
  }
  if (b->IsTypeConversion() &&
      DataType::Size(b->InputAt(0)->GetType()) < DataType::Size(type)) {
    type = b->InputAt(0)->GetType();
  }
  return type;
}

// Returns whether the loop is of a diamond structure:
//
//                header <----------------+
//                  |                     |
//             diamond_hif                |
//                /   \                   |
//     diamond_true  diamond_false        |
//                \   /                   |
//              back_edge                 |
//                  |                     |
//                  +---------------------+
static bool HasLoopDiamondStructure(HLoopInformation* loop_info) {
  HBasicBlock* header = loop_info->GetHeader();
  if (loop_info->NumberOfBackEdges() != 1 || header->GetSuccessors().size() != 2) {
    return false;
  }
  HBasicBlock* header_succ_0 = header->GetSuccessors()[0];
  HBasicBlock* header_succ_1 = header->GetSuccessors()[1];
  HBasicBlock* diamond_top = loop_info->Contains(*header_succ_0) ?
                                  header_succ_0 :
                                  header_succ_1;
  if (!diamond_top->GetLastInstruction()->IsIf()) {
    return false;
  }

  HIf* diamond_hif = diamond_top->GetLastInstruction()->AsIf();
  HBasicBlock* diamond_true = diamond_hif->IfTrueSuccessor();
  HBasicBlock* diamond_false = diamond_hif->IfFalseSuccessor();

  if (diamond_true->GetSuccessors().size() != 1 || diamond_false->GetSuccessors().size() != 1) {
    return false;
  }

  HBasicBlock* back_edge = diamond_true->GetSingleSuccessor();
  if (back_edge != diamond_false->GetSingleSuccessor() ||
      back_edge != loop_info->GetBackEdges()[0]) {
    return false;
  }

  DCHECK_EQ(loop_info->GetBlocks().NumSetBits(), 5u);
  return true;
}

static bool IsPredicatedLoopControlFlowSupported(HLoopInformation* loop_info) {
  size_t num_of_blocks = loop_info->GetBlocks().NumSetBits();
  return num_of_blocks == 2 || HasLoopDiamondStructure(loop_info);
}

//
// Public methods.
//

HLoopOptimization::HLoopOptimization(HGraph* graph,
                                     const CodeGenerator& codegen,
                                     HInductionVarAnalysis* induction_analysis,
                                     OptimizingCompilerStats* stats,
                                     const char* name)
    : HOptimization(graph, name, stats),
      compiler_options_(&codegen.GetCompilerOptions()),
      simd_register_size_(codegen.GetSIMDRegisterWidth()),
      induction_range_(induction_analysis),
      loop_allocator_(nullptr),
      global_allocator_(graph_->GetAllocator()),
      top_loop_(nullptr),
      last_loop_(nullptr),
      iset_(nullptr),
      reductions_(nullptr),
      simplified_(false),
      predicated_vectorization_mode_(codegen.SupportsPredicatedSIMD()),
      vector_length_(0),
      vector_refs_(nullptr),
      vector_static_peeling_factor_(0),
      vector_dynamic_peeling_candidate_(nullptr),
      vector_runtime_test_a_(nullptr),
      vector_runtime_test_b_(nullptr),
      vector_map_(nullptr),
      vector_permanent_map_(nullptr),
      vector_external_set_(nullptr),
      predicate_info_map_(nullptr),
      vector_mode_(kSequential),
      vector_preheader_(nullptr),
      vector_header_(nullptr),
      vector_body_(nullptr),
      vector_index_(nullptr),
      arch_loop_helper_(ArchNoOptsLoopHelper::Create(codegen, global_allocator_)) {
}

bool HLoopOptimization::Run() {
  // Skip if there is no loop or the graph has irreducible loops.
  // TODO: make this less of a sledgehammer.
  if (!graph_->HasLoops() || graph_->HasIrreducibleLoops()) {
    return false;
  }

  // Phase-local allocator.
  ScopedArenaAllocator allocator(graph_->GetArenaStack());
  loop_allocator_ = &allocator;

  // Perform loop optimizations.
  const bool did_loop_opt = LocalRun();
  if (top_loop_ == nullptr) {
    graph_->SetHasLoops(false);  // no more loops
  }

  // Detach allocator.
  loop_allocator_ = nullptr;

  return did_loop_opt;
}

//
// Loop setup and traversal.
//

bool HLoopOptimization::LocalRun() {
  // Build the linear order using the phase-local allocator. This step enables building
  // a loop hierarchy that properly reflects the outer-inner and previous-next relation.
  ScopedArenaVector<HBasicBlock*> linear_order(loop_allocator_->Adapter(kArenaAllocLinearOrder));
  LinearizeGraph(graph_, &linear_order);

  // Build the loop hierarchy.
  for (HBasicBlock* block : linear_order) {
    if (block->IsLoopHeader()) {
      AddLoop(block->GetLoopInformation());
    }
  }
  DCHECK(top_loop_ != nullptr);

  // Traverse the loop hierarchy inner-to-outer and optimize. Traversal can use
  // temporary data structures using the phase-local allocator. All new HIR
  // should use the global allocator.
  ScopedArenaSet<HInstruction*> iset(loop_allocator_->Adapter(kArenaAllocLoopOptimization));
  ScopedArenaSafeMap<HInstruction*, HInstruction*> reds(
      std::less<HInstruction*>(), loop_allocator_->Adapter(kArenaAllocLoopOptimization));
  ScopedArenaSet<ArrayReference> refs(loop_allocator_->Adapter(kArenaAllocLoopOptimization));
  ScopedArenaSafeMap<HInstruction*, HInstruction*> map(
      std::less<HInstruction*>(), loop_allocator_->Adapter(kArenaAllocLoopOptimization));
  ScopedArenaSafeMap<HInstruction*, HInstruction*> perm(
      std::less<HInstruction*>(), loop_allocator_->Adapter(kArenaAllocLoopOptimization));
  ScopedArenaSet<HInstruction*> ext_set(loop_allocator_->Adapter(kArenaAllocLoopOptimization));
  ScopedArenaSafeMap<HBasicBlock*, BlockPredicateInfo*> pred(
      std::less<HBasicBlock*>(), loop_allocator_->Adapter(kArenaAllocLoopOptimization));
  // Attach.
  iset_ = &iset;
  reductions_ = &reds;
  vector_refs_ = &refs;
  vector_map_ = &map;
  vector_permanent_map_ = &perm;
  vector_external_set_ = &ext_set;
  predicate_info_map_ = &pred;
  // Traverse.
  const bool did_loop_opt = TraverseLoopsInnerToOuter(top_loop_);
  // Detach.
  iset_ = nullptr;
  reductions_ = nullptr;
  vector_refs_ = nullptr;
  vector_map_ = nullptr;
  vector_permanent_map_ = nullptr;
  vector_external_set_ = nullptr;
  predicate_info_map_ = nullptr;

  return did_loop_opt;
}

void HLoopOptimization::AddLoop(HLoopInformation* loop_info) {
  DCHECK(loop_info != nullptr);
  LoopNode* node = new (loop_allocator_) LoopNode(loop_info);
  if (last_loop_ == nullptr) {
    // First loop.
    DCHECK(top_loop_ == nullptr);
    last_loop_ = top_loop_ = node;
  } else if (loop_info->IsIn(*last_loop_->loop_info)) {
    // Inner loop.
    node->outer = last_loop_;
    DCHECK(last_loop_->inner == nullptr);
    last_loop_ = last_loop_->inner = node;
  } else {
    // Subsequent loop.
    while (last_loop_->outer != nullptr && !loop_info->IsIn(*last_loop_->outer->loop_info)) {
      last_loop_ = last_loop_->outer;
    }
    node->outer = last_loop_->outer;
    node->previous = last_loop_;
    DCHECK(last_loop_->next == nullptr);
    last_loop_ = last_loop_->next = node;
  }
}

void HLoopOptimization::RemoveLoop(LoopNode* node) {
  DCHECK(node != nullptr);
  DCHECK(node->inner == nullptr);
  if (node->previous != nullptr) {
    // Within sequence.
    node->previous->next = node->next;
    if (node->next != nullptr) {
      node->next->previous = node->previous;
    }
  } else {
    // First of sequence.
    if (node->outer != nullptr) {
      node->outer->inner = node->next;
    } else {
      top_loop_ = node->next;
    }
    if (node->next != nullptr) {
      node->next->outer = node->outer;
      node->next->previous = nullptr;
    }
  }
}

bool HLoopOptimization::TraverseLoopsInnerToOuter(LoopNode* node) {
  bool changed = false;
  for ( ; node != nullptr; node = node->next) {
    // Visit inner loops first. Recompute induction information for this
    // loop if the induction of any inner loop has changed.
    if (TraverseLoopsInnerToOuter(node->inner)) {
      induction_range_.ReVisit(node->loop_info);
      changed = true;
    }

    CalculateAndSetTryCatchKind(node);
    if (node->try_catch_kind == LoopNode::TryCatchKind::kHasTryCatch) {
      // The current optimizations assume that the loops do not contain try/catches.
      // TODO(solanes, 227283906): Assess if we can modify them to work with try/catches.
      continue;
    }

    DCHECK(node->try_catch_kind == LoopNode::TryCatchKind::kNoTryCatch)
        << "kind: " << static_cast<int>(node->try_catch_kind)
        << ". LoopOptimization requires the loops to not have try catches.";

    // Repeat simplifications in the loop-body until no more changes occur.
    // Note that since each simplification consists of eliminating code (without
    // introducing new code), this process is always finite.
    do {
      simplified_ = false;
      SimplifyInduction(node);
      SimplifyBlocks(node);
      changed = simplified_ || changed;
    } while (simplified_);
    // Optimize inner loop.
    if (node->inner == nullptr) {
      changed = OptimizeInnerLoop(node) || changed;
    }
  }
  return changed;
}

void HLoopOptimization::CalculateAndSetTryCatchKind(LoopNode* node) {
  DCHECK(node != nullptr);
  DCHECK(node->try_catch_kind == LoopNode::TryCatchKind::kUnknown)
      << "kind: " << static_cast<int>(node->try_catch_kind)
      << ". SetTryCatchKind should be called only once per LoopNode.";

  // If a inner loop has a try catch, then the outer loop has one too (as it contains `inner`).
  // Knowing this, we could skip iterating through all of the outer loop's parents with a simple
  // check.
  for (LoopNode* inner = node->inner; inner != nullptr; inner = inner->next) {
    DCHECK(inner->try_catch_kind != LoopNode::TryCatchKind::kUnknown)
        << "kind: " << static_cast<int>(inner->try_catch_kind)
        << ". Should have updated the inner loop before the outer loop.";

    if (inner->try_catch_kind == LoopNode::TryCatchKind::kHasTryCatch) {
      node->try_catch_kind = LoopNode::TryCatchKind::kHasTryCatch;
      return;
    }
  }

  for (HBlocksInLoopIterator it_loop(*node->loop_info); !it_loop.Done(); it_loop.Advance()) {
    HBasicBlock* block = it_loop.Current();
    if (block->GetTryCatchInformation() != nullptr) {
      node->try_catch_kind = LoopNode::TryCatchKind::kHasTryCatch;
      return;
    }
  }

  node->try_catch_kind = LoopNode::TryCatchKind::kNoTryCatch;
}

//
// This optimization applies to loops with plain simple operations
// (I.e. no calls to java code or runtime) with a known small trip_count * instr_count
// value.
//
bool HLoopOptimization::TryToRemoveSuspendCheckFromLoopHeader(LoopAnalysisInfo* analysis_info,
                                                              bool generate_code) {
  if (!graph_->SuspendChecksAreAllowedToNoOp()) {
    return false;
  }

  int64_t trip_count = analysis_info->GetTripCount();

  if (trip_count == LoopAnalysisInfo::kUnknownTripCount) {
    return false;
  }

  int64_t instruction_count = analysis_info->GetNumberOfInstructions();
  int64_t total_instruction_count = trip_count * instruction_count;

  // The inclusion of the HasInstructionsPreventingScalarOpts() prevents this
  // optimization from being applied to loops that have calls.
  bool can_optimize =
      total_instruction_count <= HLoopOptimization::kMaxTotalInstRemoveSuspendCheck &&
      !analysis_info->HasInstructionsPreventingScalarOpts();

  if (!can_optimize) {
    return false;
  }

  // If we should do the optimization, disable codegen for the SuspendCheck.
  if (generate_code) {
    HLoopInformation* loop_info = analysis_info->GetLoopInfo();
    HBasicBlock* header = loop_info->GetHeader();
    HSuspendCheck* instruction = header->GetLoopInformation()->GetSuspendCheck();
    // As other optimizations depend on SuspendCheck
    // (e.g: CHAGuardVisitor::HoistGuard), disable its codegen instead of
    // removing the SuspendCheck instruction.
    instruction->SetIsNoOp(true);
  }

  return true;
}

//
// Optimization.
//

void HLoopOptimization::SimplifyInduction(LoopNode* node) {
  HBasicBlock* header = node->loop_info->GetHeader();
  HBasicBlock* preheader = node->loop_info->GetPreHeader();
  // Scan the phis in the header to find opportunities to simplify an induction
  // cycle that is only used outside the loop. Replace these uses, if any, with
  // the last value and remove the induction cycle.
  // Examples: for (int i = 0; x != null;   i++) { .... no i .... }
  //           for (int i = 0; i < 10; i++, k++) { .... no k .... } return k;
  for (HInstructionIterator it(header->GetPhis()); !it.Done(); it.Advance()) {
    HPhi* phi = it.Current()->AsPhi();
    if (TrySetPhiInduction(phi, /*restrict_uses*/ true) &&
        TryAssignLastValue(node->loop_info, phi, preheader, /*collect_loop_uses*/ false)) {
      // Note that it's ok to have replaced uses after the loop with the last value, without
      // being able to remove the cycle. Environment uses (which are the reason we may not be
      // able to remove the cycle) within the loop will still hold the right value. We must
      // have tried first, however, to replace outside uses.
      if (CanRemoveCycle()) {
        simplified_ = true;
        for (HInstruction* i : *iset_) {
          RemoveFromCycle(i);
        }
        DCHECK(CheckInductionSetFullyRemoved(iset_));
      }
    }
  }
}

void HLoopOptimization::SimplifyBlocks(LoopNode* node) {
  // Iterate over all basic blocks in the loop-body.
  for (HBlocksInLoopIterator it(*node->loop_info); !it.Done(); it.Advance()) {
    HBasicBlock* block = it.Current();
    // Remove dead instructions from the loop-body.
    RemoveDeadInstructions(block->GetPhis());
    RemoveDeadInstructions(block->GetInstructions());
    // Remove trivial control flow blocks from the loop-body.
    if (block->GetPredecessors().size() == 1 &&
        block->GetSuccessors().size() == 1 &&
        block->GetSingleSuccessor()->GetPredecessors().size() == 1) {
      simplified_ = true;
      block->MergeWith(block->GetSingleSuccessor());
    } else if (block->GetSuccessors().size() == 2) {
      // Trivial if block can be bypassed to either branch.
      HBasicBlock* succ0 = block->GetSuccessors()[0];
      HBasicBlock* succ1 = block->GetSuccessors()[1];
      HBasicBlock* meet0 = nullptr;
      HBasicBlock* meet1 = nullptr;
      if (succ0 != succ1 &&
          IsGotoBlock(succ0, &meet0) &&
          IsGotoBlock(succ1, &meet1) &&
          meet0 == meet1 &&  // meets again
          meet0 != block &&  // no self-loop
          meet0->GetPhis().IsEmpty()) {  // not used for merging
        simplified_ = true;
        succ0->DisconnectAndDelete();
        if (block->Dominates(meet0)) {
          block->RemoveDominatedBlock(meet0);
          succ1->AddDominatedBlock(meet0);
          meet0->SetDominator(succ1);
        }
      }
    }
  }
}

// Checks whether the loop has exit structure suitable for InnerLoopFinite optimization:
//  - has single loop exit.
//  - the exit block has only single predecessor - a block inside the loop.
//
// In that case returns single exit basic block (outside the loop); otherwise nullptr.
static HBasicBlock* GetInnerLoopFiniteSingleExit(HLoopInformation* loop_info) {
  HBasicBlock* exit = nullptr;
  for (HBlocksInLoopIterator block_it(*loop_info);
       !block_it.Done();
       block_it.Advance()) {
    HBasicBlock* block = block_it.Current();

    // Check whether one of the successor is loop exit.
    for (HBasicBlock* successor : block->GetSuccessors()) {
      if (!loop_info->Contains(*successor)) {
        if (exit != nullptr) {
          // The loop has more than one exit.
          return nullptr;
        }
        exit = successor;

        // Ensure exit can only be reached by exiting loop.
        if (successor->GetPredecessors().size() != 1) {
          return nullptr;
        }
      }
    }
  }
  return exit;
}

bool HLoopOptimization::TryOptimizeInnerLoopFinite(LoopNode* node) {
  HBasicBlock* header = node->loop_info->GetHeader();
  HBasicBlock* preheader = node->loop_info->GetPreHeader();
  // Ensure loop header logic is finite.
  int64_t trip_count = 0;
  if (!induction_range_.IsFinite(node->loop_info, &trip_count)) {
    return false;
  }
  // Check loop exits.
  HBasicBlock* exit = GetInnerLoopFiniteSingleExit(node->loop_info);
  if (exit == nullptr) {
    return false;
  }

  HBasicBlock* body = (header->GetSuccessors()[0] == exit)
    ? header->GetSuccessors()[1]
    : header->GetSuccessors()[0];
  // Detect either an empty loop (no side effects other than plain iteration) or
  // a trivial loop (just iterating once). Replace subsequent index uses, if any,
  // with the last value and remove the loop, possibly after unrolling its body.
  HPhi* main_phi = nullptr;
  size_t num_of_blocks = header->GetLoopInformation()->GetBlocks().NumSetBits();

  if (num_of_blocks == 2 && TrySetSimpleLoopHeader(header, &main_phi)) {
    bool is_empty = IsEmptyBody(body);
    if (reductions_->empty() &&  // TODO: possible with some effort
        (is_empty || trip_count == 1) &&
        TryAssignLastValue(node->loop_info, main_phi, preheader, /*collect_loop_uses*/ true)) {
      if (!is_empty) {
        // Unroll the loop-body, which sees initial value of the index.
        main_phi->ReplaceWith(main_phi->InputAt(0));
        preheader->MergeInstructionsWith(body);
      }
      body->DisconnectAndDelete();
      exit->RemovePredecessor(header);
      header->RemoveSuccessor(exit);
      header->RemoveDominatedBlock(exit);
      header->DisconnectAndDelete();
      preheader->AddSuccessor(exit);
      preheader->AddInstruction(new (global_allocator_) HGoto());
      preheader->AddDominatedBlock(exit);
      exit->SetDominator(preheader);
      RemoveLoop(node);  // update hierarchy
      return true;
    }
  }
  // Vectorize loop, if possible and valid.
  if (!kEnableVectorization ||
      // Disable vectorization for debuggable graphs: this is a workaround for the bug
      // in 'GenerateNewLoop' which caused the SuspendCheck environment to be invalid.
      // TODO: b/138601207, investigate other possible cases with wrong environment values and
      // possibly switch back vectorization on for debuggable graphs.
      graph_->IsDebuggable()) {
    return false;
  }

  if (IsInPredicatedVectorizationMode()) {
    return TryVectorizePredicated(node, body, exit, main_phi, trip_count);
  } else {
    return TryVectorizedTraditional(node, body, exit, main_phi, trip_count);
  }
}

bool HLoopOptimization::TryVectorizePredicated(LoopNode* node,
                                               HBasicBlock* body,
                                               HBasicBlock* exit,
                                               HPhi* main_phi,
                                               int64_t trip_count) {
  if (!IsPredicatedLoopControlFlowSupported(node->loop_info) ||
      !ShouldVectorizeCommon(node, main_phi, trip_count)) {
    return false;
  }

  // Currently we can only generate cleanup loops for loops with 2 basic block.
  //
  // TODO: Support array disambiguation tests for CF loops.
  if (NeedsArrayRefsDisambiguationTest() &&
      node->loop_info->GetBlocks().NumSetBits() != 2) {
    return false;
  }

  VectorizePredicated(node, body, exit);
  MaybeRecordStat(stats_, MethodCompilationStat::kLoopVectorized);
  graph_->SetHasPredicatedSIMD(true);  // flag SIMD usage
  return true;
}

bool HLoopOptimization::TryVectorizedTraditional(LoopNode* node,
                                                HBasicBlock* body,
                                                HBasicBlock* exit,
                                                HPhi* main_phi,
                                                int64_t trip_count) {
  HBasicBlock* header = node->loop_info->GetHeader();
  size_t num_of_blocks = header->GetLoopInformation()->GetBlocks().NumSetBits();

  if (num_of_blocks != 2 || !ShouldVectorizeCommon(node, main_phi, trip_count)) {
    return false;
  }
  VectorizeTraditional(node, body, exit, trip_count);
  MaybeRecordStat(stats_, MethodCompilationStat::kLoopVectorized);
  graph_->SetHasTraditionalSIMD(true);  // flag SIMD usage
  return true;
}

bool HLoopOptimization::OptimizeInnerLoop(LoopNode* node) {
  return TryOptimizeInnerLoopFinite(node) || TryLoopScalarOpts(node);
}

//
// Scalar loop peeling and unrolling: generic part methods.
//

bool HLoopOptimization::TryUnrollingForBranchPenaltyReduction(LoopAnalysisInfo* analysis_info,
                                                              bool generate_code) {
  if (analysis_info->GetNumberOfExits() > 1) {
    return false;
  }

  uint32_t unrolling_factor = arch_loop_helper_->GetScalarUnrollingFactor(analysis_info);
  if (unrolling_factor == LoopAnalysisInfo::kNoUnrollingFactor) {
    return false;
  }

  if (generate_code) {
    // TODO: support other unrolling factors.
    DCHECK_EQ(unrolling_factor, 2u);

    // Perform unrolling.
    HLoopInformation* loop_info = analysis_info->GetLoopInfo();
    LoopClonerSimpleHelper helper(loop_info, &induction_range_);
    helper.DoUnrolling();

    // Remove the redundant loop check after unrolling.
    HIf* copy_hif =
        helper.GetBasicBlockMap()->Get(loop_info->GetHeader())->GetLastInstruction()->AsIf();
    int32_t constant = loop_info->Contains(*copy_hif->IfTrueSuccessor()) ? 1 : 0;
    copy_hif->ReplaceInput(graph_->GetIntConstant(constant), 0u);
  }
  return true;
}

bool HLoopOptimization::TryPeelingForLoopInvariantExitsElimination(LoopAnalysisInfo* analysis_info,
                                                                   bool generate_code) {
  HLoopInformation* loop_info = analysis_info->GetLoopInfo();
  if (!arch_loop_helper_->IsLoopPeelingEnabled()) {
    return false;
  }

  if (analysis_info->GetNumberOfInvariantExits() == 0) {
    return false;
  }

  if (generate_code) {
    // Perform peeling.
    LoopClonerSimpleHelper helper(loop_info, &induction_range_);
    helper.DoPeeling();

    // Statically evaluate loop check after peeling for loop invariant condition.
    const SuperblockCloner::HInstructionMap* hir_map = helper.GetInstructionMap();
    for (auto entry : *hir_map) {
      HInstruction* copy = entry.second;
      if (copy->IsIf()) {
        TryToEvaluateIfCondition(copy->AsIf(), graph_);
      }
    }
  }

  return true;
}

bool HLoopOptimization::TryFullUnrolling(LoopAnalysisInfo* analysis_info, bool generate_code) {
  // Fully unroll loops with a known and small trip count.
  int64_t trip_count = analysis_info->GetTripCount();
  if (!arch_loop_helper_->IsLoopPeelingEnabled() ||
      trip_count == LoopAnalysisInfo::kUnknownTripCount ||
      !arch_loop_helper_->IsFullUnrollingBeneficial(analysis_info)) {
    return false;
  }

  if (generate_code) {
    // Peeling of the N first iterations (where N equals to the trip count) will effectively
    // eliminate the loop: after peeling we will have N sequential iterations copied into the loop
    // preheader and the original loop. The trip count of this loop will be 0 as the sequential
    // iterations are executed first and there are exactly N of them. Thus we can statically
    // evaluate the loop exit condition to 'false' and fully eliminate it.
    //
    // Here is an example of full unrolling of a loop with a trip count 2:
    //
    //                                           loop_cond_1
    //                                           loop_body_1        <- First iteration.
    //                                               |
    //                             \                 v
    //                            ==\            loop_cond_2
    //                            ==/            loop_body_2        <- Second iteration.
    //                             /                 |
    //               <-                              v     <-
    //     loop_cond   \                         loop_cond   \      <- This cond is always false.
    //     loop_body  _/                         loop_body  _/
    //
    HLoopInformation* loop_info = analysis_info->GetLoopInfo();
    PeelByCount(loop_info, trip_count, &induction_range_);
    HIf* loop_hif = loop_info->GetHeader()->GetLastInstruction()->AsIf();
    int32_t constant = loop_info->Contains(*loop_hif->IfTrueSuccessor()) ? 0 : 1;
    loop_hif->ReplaceInput(graph_->GetIntConstant(constant), 0u);
  }

  return true;
}

bool HLoopOptimization::TryLoopScalarOpts(LoopNode* node) {
  HLoopInformation* loop_info = node->loop_info;
  int64_t trip_count = LoopAnalysis::GetLoopTripCount(loop_info, &induction_range_);
  LoopAnalysisInfo analysis_info(loop_info);
  LoopAnalysis::CalculateLoopBasicProperties(loop_info, &analysis_info, trip_count);

  if (analysis_info.HasInstructionsPreventingScalarOpts() ||
      arch_loop_helper_->IsLoopNonBeneficialForScalarOpts(&analysis_info)) {
    return false;
  }

  if (!TryFullUnrolling(&analysis_info, /*generate_code*/ false) &&
      !TryPeelingForLoopInvariantExitsElimination(&analysis_info, /*generate_code*/ false) &&
      !TryUnrollingForBranchPenaltyReduction(&analysis_info, /*generate_code*/ false) &&
      !TryToRemoveSuspendCheckFromLoopHeader(&analysis_info, /*generate_code*/ false)) {
    return false;
  }

  // Try the suspend check removal even for non-clonable loops. Also this
  // optimization doesn't interfere with other scalar loop optimizations so it can
  // be done prior to them.
  bool removed_suspend_check = TryToRemoveSuspendCheckFromLoopHeader(&analysis_info);

  // Run 'IsLoopClonable' the last as it might be time-consuming.
  if (!LoopClonerHelper::IsLoopClonable(loop_info)) {
    return false;
  }

  return TryFullUnrolling(&analysis_info) ||
         TryPeelingForLoopInvariantExitsElimination(&analysis_info) ||
         TryUnrollingForBranchPenaltyReduction(&analysis_info) || removed_suspend_check;
}

//
// Loop vectorization. The implementation is based on the book by Aart J.C. Bik:
// "The Software Vectorization Handbook. Applying Multimedia Extensions for Maximum Performance."
// Intel Press, June, 2004 (http://www.aartbik.com/).
//


bool HLoopOptimization::CanVectorizeDataFlow(LoopNode* node,
                                             HBasicBlock* header,
                                             bool collect_alignment_info) {
  // Reset vector bookkeeping.
  vector_length_ = 0;
  vector_refs_->clear();
  vector_static_peeling_factor_ = 0;
  vector_dynamic_peeling_candidate_ = nullptr;
  vector_runtime_test_a_ =
  vector_runtime_test_b_ = nullptr;

  // Traverse the data flow of the loop, in the original program order.
  for (HBlocksInLoopReversePostOrderIterator block_it(*header->GetLoopInformation());
       !block_it.Done();
       block_it.Advance()) {
    HBasicBlock* block = block_it.Current();

    if (block == header) {
      // The header is of a certain structure (TrySetSimpleLoopHeader) and doesn't need to be
      // processed here.
      continue;
    }

    // Phis in the loop-body prevent vectorization.
    // TODO: Enable vectorization of CF loops with Phis.
    if (!block->GetPhis().IsEmpty()) {
      return false;
    }

    // Scan the loop-body instructions, starting a right-hand-side tree traversal at each
    // left-hand-side occurrence, which allows passing down attributes down the use tree.
    for (HInstructionIterator it(block->GetInstructions()); !it.Done(); it.Advance()) {
      if (!VectorizeDef(node, it.Current(), /*generate_code*/ false)) {
        return false;  // failure to vectorize a left-hand-side
      }
    }
  }

  // Prepare alignment analysis:
  // (1) find desired alignment (SIMD vector size in bytes).
  // (2) initialize static loop peeling votes (peeling factor that will
  //     make one particular reference aligned), never to exceed (1).
  // (3) variable to record how many references share same alignment.
  // (4) variable to record suitable candidate for dynamic loop peeling.
  size_t desired_alignment = GetVectorSizeInBytes();
  ScopedArenaVector<uint32_t> peeling_votes(desired_alignment, 0u,
      loop_allocator_->Adapter(kArenaAllocLoopOptimization));

  uint32_t max_num_same_alignment = 0;
  const ArrayReference* peeling_candidate = nullptr;

  // Data dependence analysis. Find each pair of references with same type, where
  // at least one is a write. Each such pair denotes a possible data dependence.
  // This analysis exploits the property that differently typed arrays cannot be
  // aliased, as well as the property that references either point to the same
  // array or to two completely disjoint arrays, i.e., no partial aliasing.
  // Other than a few simply heuristics, no detailed subscript analysis is done.
  // The scan over references also prepares finding a suitable alignment strategy.
  for (auto i = vector_refs_->begin(); i != vector_refs_->end(); ++i) {
    uint32_t num_same_alignment = 0;
    // Scan over all next references.
    for (auto j = i; ++j != vector_refs_->end(); ) {
      if (i->type == j->type && (i->lhs || j->lhs)) {
        // Found same-typed a[i+x] vs. b[i+y], where at least one is a write.
        HInstruction* a = i->base;
        HInstruction* b = j->base;
        HInstruction* x = i->offset;
        HInstruction* y = j->offset;
        if (a == b) {
          // Found a[i+x] vs. a[i+y]. Accept if x == y (loop-independent data dependence).
          // Conservatively assume a loop-carried data dependence otherwise, and reject.
          if (x != y) {
            return false;
          }
          // Count the number of references that have the same alignment (since
          // base and offset are the same) and where at least one is a write, so
          // e.g. a[i] = a[i] + b[i] counts a[i] but not b[i]).
          num_same_alignment++;
        } else {
          // Found a[i+x] vs. b[i+y]. Accept if x == y (at worst loop-independent data dependence).
          // Conservatively assume a potential loop-carried data dependence otherwise, avoided by
          // generating an explicit a != b disambiguation runtime test on the two references.
          if (x != y) {
            // To avoid excessive overhead, we only accept one a != b test.
            if (vector_runtime_test_a_ == nullptr) {
              // First test found.
              vector_runtime_test_a_ = a;
              vector_runtime_test_b_ = b;
            } else if ((vector_runtime_test_a_ != a || vector_runtime_test_b_ != b) &&
                       (vector_runtime_test_a_ != b || vector_runtime_test_b_ != a)) {
              return false;  // second test would be needed
            }
          }
        }
      }
    }
    // Update information for finding suitable alignment strategy:
    // (1) update votes for static loop peeling,
    // (2) update suitable candidate for dynamic loop peeling.
    Alignment alignment = ComputeAlignment(i->offset, i->type, i->is_string_char_at);
    if (alignment.Base() >= desired_alignment) {
      // If the array/string object has a known, sufficient alignment, use the
      // initial offset to compute the static loop peeling vote (this always
      // works, since elements have natural alignment).
      uint32_t offset = alignment.Offset() & (desired_alignment - 1u);
      uint32_t vote = (offset == 0)
          ? 0
          : ((desired_alignment - offset) >> DataType::SizeShift(i->type));
      DCHECK_LT(vote, 16u);
      ++peeling_votes[vote];
    } else if (BaseAlignment() >= desired_alignment &&
               num_same_alignment > max_num_same_alignment) {
      // Otherwise, if the array/string object has a known, sufficient alignment
      // for just the base but with an unknown offset, record the candidate with
      // the most occurrences for dynamic loop peeling (again, the peeling always
      // works, since elements have natural alignment).
      max_num_same_alignment = num_same_alignment;
      peeling_candidate = &(*i);
    }
  }  // for i

  if (collect_alignment_info) {
    // Update the info on alignment strategy.
    SetAlignmentStrategy(peeling_votes, peeling_candidate);
  }

  // Success!
  return true;
}

bool HLoopOptimization::ShouldVectorizeCommon(LoopNode* node,
                                              HPhi* main_phi,
                                              int64_t trip_count) {
  HBasicBlock* header = node->loop_info->GetHeader();
  HBasicBlock* preheader = node->loop_info->GetPreHeader();

  bool enable_alignment_strategies = !IsInPredicatedVectorizationMode();
  if (!TrySetSimpleLoopHeader(header, &main_phi) ||
      !CanVectorizeDataFlow(node, header, enable_alignment_strategies) ||
      !IsVectorizationProfitable(trip_count) ||
      !TryAssignLastValue(node->loop_info, main_phi, preheader, /*collect_loop_uses*/ true)) {
    return false;
  }

  return true;
}

void HLoopOptimization::VectorizePredicated(LoopNode* node,
                                            HBasicBlock* block,
                                            HBasicBlock* exit) {
  DCHECK(IsInPredicatedVectorizationMode());

  HBasicBlock* header = node->loop_info->GetHeader();
  HBasicBlock* preheader = node->loop_info->GetPreHeader();

  // Adjust vector bookkeeping.
  HPhi* main_phi = nullptr;
  bool is_simple_loop_header = TrySetSimpleLoopHeader(header, &main_phi);  // refills sets
  DCHECK(is_simple_loop_header);
  vector_header_ = header;
  vector_body_ = block;

  // Loop induction type.
  DataType::Type induc_type = main_phi->GetType();
  DCHECK(induc_type == DataType::Type::kInt32 || induc_type == DataType::Type::kInt64)
      << induc_type;

  // Generate loop control:
  // stc = <trip-count>;
  // vtc = <vector trip-count>
  HInstruction* stc = induction_range_.GenerateTripCount(node->loop_info, graph_, preheader);
  HInstruction* vtc = stc;
  vector_index_ = graph_->GetConstant(induc_type, 0);
  bool needs_disambiguation_test = false;
  // Generate runtime disambiguation test:
  // vtc = a != b ? vtc : 0;
  if (NeedsArrayRefsDisambiguationTest()) {
    HInstruction* rt = Insert(
        preheader,
        new (global_allocator_) HNotEqual(vector_runtime_test_a_, vector_runtime_test_b_));
    vtc = Insert(preheader,
                 new (global_allocator_)
                 HSelect(rt, vtc, graph_->GetConstant(induc_type, 0), kNoDexPc));
    needs_disambiguation_test = true;
  }

  // Generate vector loop:
  // for ( ; i < vtc; i += vector_length)
  //    <vectorized-loop-body>
  HBasicBlock* preheader_for_vector_loop =
      graph_->TransformLoopForVectorization(vector_header_, vector_body_, exit);
  vector_mode_ = kVector;
  GenerateNewLoopPredicated(node,
                            preheader_for_vector_loop,
                            vector_index_,
                            vtc,
                            graph_->GetConstant(induc_type, vector_length_));

  // Generate scalar loop, if needed:
  // for ( ; i < stc; i += 1)
  //    <loop-body>
  if (needs_disambiguation_test) {
    vector_mode_ = kSequential;
    HBasicBlock* preheader_for_cleanup_loop =
        graph_->TransformLoopForVectorization(vector_header_, vector_body_, exit);
    // Use "Traditional" version for the sequential loop.
    GenerateNewLoopScalarOrTraditional(node,
                                       preheader_for_cleanup_loop,
                                       vector_index_,
                                       stc,
                                       graph_->GetConstant(induc_type, 1),
                                       LoopAnalysisInfo::kNoUnrollingFactor);
  }

  FinalizeVectorization(node);

  // Assign governing predicates for the predicated instructions inserted during vectorization
  // outside the loop.
  for (auto it : *vector_external_set_) {
    DCHECK(it->IsVecOperation());
    HVecOperation* vec_op = it->AsVecOperation();

    HVecPredSetAll* set_pred = new (global_allocator_) HVecPredSetAll(global_allocator_,
                                                                      graph_->GetIntConstant(1),
                                                                      vec_op->GetPackedType(),
                                                                      vec_op->GetVectorLength(),
                                                                      0u);
    vec_op->GetBlock()->InsertInstructionBefore(set_pred, vec_op);
    vec_op->SetMergingGoverningPredicate(set_pred);
  }
}

void HLoopOptimization::VectorizeTraditional(LoopNode* node,
                                             HBasicBlock* block,
                                             HBasicBlock* exit,
                                             int64_t trip_count) {
  DCHECK(!IsInPredicatedVectorizationMode());

  HBasicBlock* header = node->loop_info->GetHeader();
  HBasicBlock* preheader = node->loop_info->GetPreHeader();

  // Pick a loop unrolling factor for the vector loop.
  uint32_t unroll = arch_loop_helper_->GetSIMDUnrollingFactor(
      block, trip_count, MaxNumberPeeled(), vector_length_);
  uint32_t chunk = vector_length_ * unroll;

  DCHECK(trip_count == 0 || (trip_count >= MaxNumberPeeled() + chunk));

  // A cleanup loop is needed, at least, for any unknown trip count or
  // for a known trip count with remainder iterations after vectorization.
  bool needs_cleanup =
      (trip_count == 0 || ((trip_count - vector_static_peeling_factor_) % chunk) != 0);

  // Adjust vector bookkeeping.
  HPhi* main_phi = nullptr;
  bool is_simple_loop_header = TrySetSimpleLoopHeader(header, &main_phi);  // refills sets
  DCHECK(is_simple_loop_header);
  vector_header_ = header;
  vector_body_ = block;

  // Loop induction type.
  DataType::Type induc_type = main_phi->GetType();
  DCHECK(induc_type == DataType::Type::kInt32 || induc_type == DataType::Type::kInt64)
      << induc_type;

  // Generate the trip count for static or dynamic loop peeling, if needed:
  // ptc = <peeling factor>;
  HInstruction* ptc = nullptr;
  if (vector_static_peeling_factor_ != 0) {
    // Static loop peeling for SIMD alignment (using the most suitable
    // fixed peeling factor found during prior alignment analysis).
    DCHECK(vector_dynamic_peeling_candidate_ == nullptr);
    ptc = graph_->GetConstant(induc_type, vector_static_peeling_factor_);
  } else if (vector_dynamic_peeling_candidate_ != nullptr) {
    // Dynamic loop peeling for SIMD alignment (using the most suitable
    // candidate found during prior alignment analysis):
    // rem = offset % ALIGN;    // adjusted as #elements
    // ptc = rem == 0 ? 0 : (ALIGN - rem);
    uint32_t shift = DataType::SizeShift(vector_dynamic_peeling_candidate_->type);
    uint32_t align = GetVectorSizeInBytes() >> shift;
    uint32_t hidden_offset = HiddenOffset(vector_dynamic_peeling_candidate_->type,
                                          vector_dynamic_peeling_candidate_->is_string_char_at);
    HInstruction* adjusted_offset = graph_->GetConstant(induc_type, hidden_offset >> shift);
    HInstruction* offset = Insert(preheader, new (global_allocator_) HAdd(
        induc_type, vector_dynamic_peeling_candidate_->offset, adjusted_offset));
    HInstruction* rem = Insert(preheader, new (global_allocator_) HAnd(
        induc_type, offset, graph_->GetConstant(induc_type, align - 1u)));
    HInstruction* sub = Insert(preheader, new (global_allocator_) HSub(
        induc_type, graph_->GetConstant(induc_type, align), rem));
    HInstruction* cond = Insert(preheader, new (global_allocator_) HEqual(
        rem, graph_->GetConstant(induc_type, 0)));
    ptc = Insert(preheader, new (global_allocator_) HSelect(
        cond, graph_->GetConstant(induc_type, 0), sub, kNoDexPc));
    needs_cleanup = true;  // don't know the exact amount
  }

  // Generate loop control:
  // stc = <trip-count>;
  // ptc = min(stc, ptc);
  // vtc = stc - (stc - ptc) % chunk;
  // i = 0;
  HInstruction* stc = induction_range_.GenerateTripCount(node->loop_info, graph_, preheader);
  HInstruction* vtc = stc;
  if (needs_cleanup) {
    DCHECK(IsPowerOfTwo(chunk));
    HInstruction* diff = stc;
    if (ptc != nullptr) {
      if (trip_count == 0) {
        HInstruction* cond = Insert(preheader, new (global_allocator_) HAboveOrEqual(stc, ptc));
        ptc = Insert(preheader, new (global_allocator_) HSelect(cond, ptc, stc, kNoDexPc));
      }
      diff = Insert(preheader, new (global_allocator_) HSub(induc_type, stc, ptc));
    }
    HInstruction* rem = Insert(
        preheader, new (global_allocator_) HAnd(induc_type,
                                                diff,
                                                graph_->GetConstant(induc_type, chunk - 1)));
    vtc = Insert(preheader, new (global_allocator_) HSub(induc_type, stc, rem));
  }
  vector_index_ = graph_->GetConstant(induc_type, 0);

  // Generate runtime disambiguation test:
  // vtc = a != b ? vtc : 0;
  if (NeedsArrayRefsDisambiguationTest()) {
    HInstruction* rt = Insert(
        preheader,
        new (global_allocator_) HNotEqual(vector_runtime_test_a_, vector_runtime_test_b_));
    vtc = Insert(preheader,
                 new (global_allocator_)
                 HSelect(rt, vtc, graph_->GetConstant(induc_type, 0), kNoDexPc));
    needs_cleanup = true;
  }

  // Generate alignment peeling loop, if needed:
  // for ( ; i < ptc; i += 1)
  //    <loop-body>
  //
  // NOTE: The alignment forced by the peeling loop is preserved even if data is
  //       moved around during suspend checks, since all analysis was based on
  //       nothing more than the Android runtime alignment conventions.
  if (ptc != nullptr) {
    vector_mode_ = kSequential;
    HBasicBlock* preheader_for_peeling_loop =
        graph_->TransformLoopForVectorization(vector_header_, vector_body_, exit);
    GenerateNewLoopScalarOrTraditional(node,
                                       preheader_for_peeling_loop,
                                       vector_index_,
                                       ptc,
                                       graph_->GetConstant(induc_type, 1),
                                       LoopAnalysisInfo::kNoUnrollingFactor);
  }

  // Generate vector loop, possibly further unrolled:
  // for ( ; i < vtc; i += chunk)
  //    <vectorized-loop-body>
  vector_mode_ = kVector;
  HBasicBlock* preheader_for_vector_loop =
      graph_->TransformLoopForVectorization(vector_header_, vector_body_, exit);
  GenerateNewLoopScalarOrTraditional(node,
                                     preheader_for_vector_loop,
                                     vector_index_,
                                     vtc,
                                     graph_->GetConstant(induc_type, vector_length_),  // per unroll
                                     unroll);

  // Generate cleanup loop, if needed:
  // for ( ; i < stc; i += 1)
  //    <loop-body>
  if (needs_cleanup) {
    vector_mode_ = kSequential;
    HBasicBlock* preheader_for_cleanup_loop =
        graph_->TransformLoopForVectorization(vector_header_, vector_body_, exit);
    GenerateNewLoopScalarOrTraditional(node,
                                       preheader_for_cleanup_loop,
                                       vector_index_,
                                       stc,
                                       graph_->GetConstant(induc_type, 1),
                                       LoopAnalysisInfo::kNoUnrollingFactor);
  }

  FinalizeVectorization(node);
}

void HLoopOptimization::FinalizeVectorization(LoopNode* node) {
  HBasicBlock* header = node->loop_info->GetHeader();
  HBasicBlock* preheader = node->loop_info->GetPreHeader();
  HLoopInformation* vloop = vector_header_->GetLoopInformation();
  // Link reductions to their final uses.
  for (auto i = reductions_->begin(); i != reductions_->end(); ++i) {
    if (i->first->IsPhi()) {
      HInstruction* phi = i->first;
      HInstruction* repl = ReduceAndExtractIfNeeded(i->second);
      // Deal with regular uses.
      for (const HUseListNode<HInstruction*>& use : phi->GetUses()) {
        induction_range_.Replace(use.GetUser(), phi, repl);  // update induction use
      }
      phi->ReplaceWith(repl);
    }
  }

  // Remove the original loop.
  for (HBlocksInLoopPostOrderIterator it_loop(*node->loop_info);
       !it_loop.Done();
       it_loop.Advance()) {
    HBasicBlock* cur_block = it_loop.Current();
    if (cur_block == node->loop_info->GetHeader()) {
      continue;
    }
    cur_block->DisconnectAndDelete();
  }

  while (!header->GetFirstInstruction()->IsGoto()) {
    header->RemoveInstruction(header->GetFirstInstruction());
  }

  // Update loop hierarchy: the old header now resides in the same outer loop
  // as the old preheader. Note that we don't bother putting sequential
  // loops back in the hierarchy at this point.
  header->SetLoopInformation(preheader->GetLoopInformation());  // outward
  node->loop_info = vloop;
}

HPhi* HLoopOptimization::InitializeForNewLoop(HBasicBlock* new_preheader, HInstruction* lo) {
  DataType::Type induc_type = lo->GetType();
  // Prepare new loop.
  vector_preheader_ = new_preheader,
  vector_header_ = vector_preheader_->GetSingleSuccessor();
  vector_body_ = vector_header_->GetSuccessors()[1];
  HPhi* phi = new (global_allocator_) HPhi(global_allocator_,
                                           kNoRegNumber,
                                           0,
                                           HPhi::ToPhiType(induc_type));
  vector_header_->AddPhi(phi);
  vector_index_ = phi;
  vector_permanent_map_->clear();
  vector_external_set_->clear();
  predicate_info_map_->clear();

  return phi;
}

void HLoopOptimization::GenerateNewLoopScalarOrTraditional(LoopNode* node,
                                                           HBasicBlock* new_preheader,
                                                           HInstruction* lo,
                                                           HInstruction* hi,
                                                           HInstruction* step,
                                                           uint32_t unroll) {
  DCHECK(unroll == 1 || vector_mode_ == kVector);
  DataType::Type induc_type = lo->GetType();
  HPhi* phi = InitializeForNewLoop(new_preheader, lo);

  // Generate loop exit check.
  HInstruction* cond = new (global_allocator_) HAboveOrEqual(phi, hi);
  vector_header_->AddInstruction(cond);
  vector_header_->AddInstruction(new (global_allocator_) HIf(cond));

  for (uint32_t u = 0; u < unroll; u++) {
    GenerateNewLoopBodyOnce(node, induc_type, step);
  }

  FinalizePhisForNewLoop(phi, lo);
}

void HLoopOptimization::GenerateNewLoopPredicated(LoopNode* node,
                                                  HBasicBlock* new_preheader,
                                                  HInstruction* lo,
                                                  HInstruction* hi,
                                                  HInstruction* step) {
  DCHECK(IsInPredicatedVectorizationMode());
  DCHECK_EQ(vector_mode_, kVector);
  DataType::Type induc_type = lo->GetType();
  HPhi* phi = InitializeForNewLoop(new_preheader, lo);

  // Generate loop exit check.
  HVecPredWhile* pred_while =
      new (global_allocator_) HVecPredWhile(global_allocator_,
                                            phi,
                                            hi,
                                            HVecPredWhile::CondKind::kLO,
                                            DataType::Type::kInt32,
                                            vector_length_,
                                            0u);

  HInstruction* cond =
      new (global_allocator_) HVecPredToBoolean(global_allocator_,
                                                pred_while,
                                                HVecPredToBoolean::PCondKind::kNFirst,
                                                DataType::Type::kInt32,
                                                vector_length_,
                                                0u);

  vector_header_->AddInstruction(pred_while);
  vector_header_->AddInstruction(cond);
  vector_header_->AddInstruction(new (global_allocator_) HIf(cond));

  PreparePredicateInfoMap(node);
  GenerateNewLoopBodyOnce(node, induc_type, step);
  InitPredicateInfoMap(node, pred_while);

  // Assign governing predicates for instructions in the loop; the traversal order doesn't matter.
  for (HBlocksInLoopIterator block_it(*node->loop_info);
       !block_it.Done();
       block_it.Advance()) {
    HBasicBlock* cur_block = block_it.Current();

    for (HInstructionIterator it(cur_block->GetInstructions()); !it.Done(); it.Advance()) {
      auto i = vector_map_->find(it.Current());
      if (i != vector_map_->end()) {
        HInstruction* instr = i->second;

        if (!instr->IsVecOperation()) {
          continue;
        }
        // There are cases when a vector instruction, which corresponds to some instruction in the
        // original scalar loop, is located not in the newly created vector loop but
        // in the vector loop preheader (and hence recorded in vector_external_set_).
        //
        // Governing predicates will be set for such instructions separately.
        bool in_vector_loop = vector_header_->GetLoopInformation()->Contains(*instr->GetBlock());
        DCHECK_IMPLIES(!in_vector_loop,
                        vector_external_set_->find(instr) != vector_external_set_->end());

        if (in_vector_loop &&
            !instr->AsVecOperation()->IsPredicated()) {
          HVecOperation* op = instr->AsVecOperation();
          HVecPredSetOperation* pred = predicate_info_map_->Get(cur_block)->GetControlPredicate();
          op->SetMergingGoverningPredicate(pred);
        }
      }
    }
  }

  FinalizePhisForNewLoop(phi, lo);
}

void HLoopOptimization::GenerateNewLoopBodyOnce(LoopNode* node,
                                                DataType::Type induc_type,
                                                HInstruction* step) {
  // Generate instruction map.
  vector_map_->clear();
  HLoopInformation* loop_info = node->loop_info;

  // Traverse the data flow of the loop, in the original program order.
  for (HBlocksInLoopReversePostOrderIterator block_it(*loop_info);
      !block_it.Done();
      block_it.Advance()) {
    HBasicBlock* cur_block = block_it.Current();

    if (cur_block == loop_info->GetHeader()) {
      continue;
    }

    for (HInstructionIterator it(cur_block->GetInstructions()); !it.Done(); it.Advance()) {
      bool vectorized_def = VectorizeDef(node, it.Current(), /*generate_code*/ true);
      DCHECK(vectorized_def);
    }
  }

  // Generate body from the instruction map, in the original program order.
  HEnvironment* env = vector_header_->GetFirstInstruction()->GetEnvironment();
  for (HBlocksInLoopReversePostOrderIterator block_it(*loop_info);
        !block_it.Done();
        block_it.Advance()) {
    HBasicBlock* cur_block = block_it.Current();

    if (cur_block == loop_info->GetHeader()) {
      continue;
    }

    for (HInstructionIterator it(cur_block->GetInstructions()); !it.Done(); it.Advance()) {
      auto i = vector_map_->find(it.Current());
      if (i != vector_map_->end() && !i->second->IsInBlock()) {
        Insert(vector_body_, i->second);
        // Deal with instructions that need an environment, such as the scalar intrinsics.
        if (i->second->NeedsEnvironment()) {
          i->second->CopyEnvironmentFromWithLoopPhiAdjustment(env, vector_header_);
        }
      }
    }
  }
  // Generate the induction.
  vector_index_ = new (global_allocator_) HAdd(induc_type, vector_index_, step);
  Insert(vector_body_, vector_index_);
}

void HLoopOptimization::FinalizePhisForNewLoop(HPhi* phi, HInstruction* lo) {
  // Finalize phi inputs for the reductions (if any).
  for (auto i = reductions_->begin(); i != reductions_->end(); ++i) {
    if (!i->first->IsPhi()) {
      DCHECK(i->second->IsPhi());
      GenerateVecReductionPhiInputs(i->second->AsPhi(), i->first);
    }
  }
  // Finalize phi inputs for the loop index.
  phi->AddInput(lo);
  phi->AddInput(vector_index_);
  vector_index_ = phi;
}

bool HLoopOptimization::VectorizeDef(LoopNode* node,
                                     HInstruction* instruction,
                                     bool generate_code) {
  // Accept a left-hand-side array base[index] for
  // (1) supported vector type,
  // (2) loop-invariant base,
  // (3) unit stride index,
  // (4) vectorizable right-hand-side value.
  uint64_t restrictions = kNone;
  // Don't accept expressions that can throw.
  if (instruction->CanThrow()) {
    return false;
  }
  if (instruction->IsArraySet()) {
    DataType::Type type = instruction->AsArraySet()->GetComponentType();
    HInstruction* base = instruction->InputAt(0);
    HInstruction* index = instruction->InputAt(1);
    HInstruction* value = instruction->InputAt(2);
    HInstruction* offset = nullptr;
    // For narrow types, explicit type conversion may have been
    // optimized way, so set the no hi bits restriction here.
    if (DataType::Size(type) <= 2) {
      restrictions |= kNoHiBits;
    }
    if (TrySetVectorType(type, &restrictions) &&
        node->loop_info->IsDefinedOutOfTheLoop(base) &&
        induction_range_.IsUnitStride(instruction->GetBlock(), index, graph_, &offset) &&
        VectorizeUse(node, value, generate_code, type, restrictions)) {
      if (generate_code) {
        GenerateVecSub(index, offset);
        GenerateVecMem(instruction, vector_map_->Get(index), vector_map_->Get(value), offset, type);
      } else {
        vector_refs_->insert(ArrayReference(base, offset, type, /*lhs*/ true));
      }
      return true;
    }
    return false;
  }
  // Accept a left-hand-side reduction for
  // (1) supported vector type,
  // (2) vectorizable right-hand-side value.
  auto redit = reductions_->find(instruction);
  if (redit != reductions_->end()) {
    DataType::Type type = instruction->GetType();
    // Recognize SAD idiom or direct reduction.
    if (VectorizeSADIdiom(node, instruction, generate_code, type, restrictions) ||
        VectorizeDotProdIdiom(node, instruction, generate_code, type, restrictions) ||
        (TrySetVectorType(type, &restrictions) &&
         VectorizeUse(node, instruction, generate_code, type, restrictions))) {
      DCHECK(!instruction->IsPhi());
      if (generate_code) {
        HInstruction* new_red_vec_op = vector_map_->Get(instruction);
        HInstruction* original_phi = redit->second;
        DCHECK(original_phi->IsPhi());
        vector_permanent_map_->Put(new_red_vec_op, vector_map_->Get(original_phi));
        vector_permanent_map_->Overwrite(original_phi, new_red_vec_op);
      }
      return true;
    }
    return false;
  }
  // Branch back okay.
  if (instruction->IsGoto()) {
    return true;
  }

  if (instruction->IsIf()) {
    return VectorizeIfCondition(node, instruction, generate_code, restrictions);
  }
  // Otherwise accept only expressions with no effects outside the immediate loop-body.
  // Note that actual uses are inspected during right-hand-side tree traversal.
  return !IsUsedOutsideLoop(node->loop_info, instruction)
         && !instruction->DoesAnyWrite();
}

bool HLoopOptimization::VectorizeUse(LoopNode* node,
                                     HInstruction* instruction,
                                     bool generate_code,
                                     DataType::Type type,
                                     uint64_t restrictions) {
  // Accept anything for which code has already been generated.
  if (generate_code) {
    if (vector_map_->find(instruction) != vector_map_->end()) {
      return true;
    }
  }
  // Continue the right-hand-side tree traversal, passing in proper
  // types and vector restrictions along the way. During code generation,
  // all new nodes are drawn from the global allocator.
  if (node->loop_info->IsDefinedOutOfTheLoop(instruction)) {
    // Accept invariant use, using scalar expansion.
    if (generate_code) {
      GenerateVecInv(instruction, type);
    }
    return true;
  } else if (instruction->IsArrayGet()) {
    // Deal with vector restrictions.
    bool is_string_char_at = instruction->AsArrayGet()->IsStringCharAt();

    if (is_string_char_at && (HasVectorRestrictions(restrictions, kNoStringCharAt))) {
      return false;
    }
    // Accept a right-hand-side array base[index] for
    // (1) matching vector type (exact match or signed/unsigned integral type of the same size),
    // (2) loop-invariant base,
    // (3) unit stride index,
    // (4) vectorizable right-hand-side value.
    HInstruction* base = instruction->InputAt(0);
    HInstruction* index = instruction->InputAt(1);
    HInstruction* offset = nullptr;
    if (HVecOperation::ToSignedType(type) == HVecOperation::ToSignedType(instruction->GetType()) &&
        node->loop_info->IsDefinedOutOfTheLoop(base) &&
        induction_range_.IsUnitStride(instruction->GetBlock(), index, graph_, &offset)) {
      if (generate_code) {
        GenerateVecSub(index, offset);
        GenerateVecMem(instruction, vector_map_->Get(index), nullptr, offset, type);
      } else {
        vector_refs_->insert(ArrayReference(base, offset, type, /*lhs*/ false, is_string_char_at));
      }
      return true;
    }
  } else if (instruction->IsPhi()) {
    // Accept particular phi operations.
    if (reductions_->find(instruction) != reductions_->end()) {
      // Deal with vector restrictions.
      if (HasVectorRestrictions(restrictions, kNoReduction)) {
        return false;
      }
      // Accept a reduction.
      if (generate_code) {
        GenerateVecReductionPhi(instruction->AsPhi());
      }
      return true;
    }
    // TODO: accept right-hand-side induction?
    return false;
  } else if (instruction->IsTypeConversion()) {
    // Accept particular type conversions.
    HTypeConversion* conversion = instruction->AsTypeConversion();
    HInstruction* opa = conversion->InputAt(0);
    DataType::Type from = conversion->GetInputType();
    DataType::Type to = conversion->GetResultType();
    if (DataType::IsIntegralType(from) && DataType::IsIntegralType(to)) {
      uint32_t size_vec = DataType::Size(type);
      uint32_t size_from = DataType::Size(from);
      uint32_t size_to = DataType::Size(to);
      // Accept an integral conversion
      // (1a) narrowing into vector type, "wider" operations cannot bring in higher order bits, or
      // (1b) widening from at least vector type, and
      // (2) vectorizable operand.
      if ((size_to < size_from &&
           size_to == size_vec &&
           VectorizeUse(node, opa, generate_code, type, restrictions | kNoHiBits)) ||
          (size_to >= size_from &&
           size_from >= size_vec &&
           VectorizeUse(node, opa, generate_code, type, restrictions))) {
        if (generate_code) {
          if (vector_mode_ == kVector) {
            vector_map_->Put(instruction, vector_map_->Get(opa));  // operand pass-through
          } else {
            GenerateVecOp(instruction, vector_map_->Get(opa), nullptr, type);
          }
        }
        return true;
      }
    } else if (to == DataType::Type::kFloat32 && from == DataType::Type::kInt32) {
      DCHECK_EQ(to, type);
      // Accept int to float conversion for
      // (1) supported int,
      // (2) vectorizable operand.
      if (TrySetVectorType(from, &restrictions) &&
          VectorizeUse(node, opa, generate_code, from, restrictions)) {
        if (generate_code) {
          GenerateVecOp(instruction, vector_map_->Get(opa), nullptr, type);
        }
        return true;
      }
    }
    return false;
  } else if (instruction->IsNeg() || instruction->IsNot() || instruction->IsBooleanNot()) {
    // Accept unary operator for vectorizable operand.
    HInstruction* opa = instruction->InputAt(0);
    if (VectorizeUse(node, opa, generate_code, type, restrictions)) {
      if (generate_code) {
        GenerateVecOp(instruction, vector_map_->Get(opa), nullptr, type);
      }
      return true;
    }
  } else if (instruction->IsAdd() || instruction->IsSub() ||
             instruction->IsMul() || instruction->IsDiv() ||
             instruction->IsAnd() || instruction->IsOr()  || instruction->IsXor()) {
    // Deal with vector restrictions.
    if ((instruction->IsMul() && HasVectorRestrictions(restrictions, kNoMul)) ||
        (instruction->IsDiv() && HasVectorRestrictions(restrictions, kNoDiv))) {
      return false;
    }
    // Accept binary operator for vectorizable operands.
    HInstruction* opa = instruction->InputAt(0);
    HInstruction* opb = instruction->InputAt(1);
    if (VectorizeUse(node, opa, generate_code, type, restrictions) &&
        VectorizeUse(node, opb, generate_code, type, restrictions)) {
      if (generate_code) {
        GenerateVecOp(instruction, vector_map_->Get(opa), vector_map_->Get(opb), type);
      }
      return true;
    }
  } else if (instruction->IsShl() || instruction->IsShr() || instruction->IsUShr()) {
    // Recognize halving add idiom.
    if (VectorizeHalvingAddIdiom(node, instruction, generate_code, type, restrictions)) {
      return true;
    }
    // Deal with vector restrictions.
    HInstruction* opa = instruction->InputAt(0);
    HInstruction* opb = instruction->InputAt(1);
    HInstruction* r = opa;
    bool is_unsigned = false;
    if ((HasVectorRestrictions(restrictions, kNoShift)) ||
        (instruction->IsShr() && HasVectorRestrictions(restrictions, kNoShr))) {
      return false;  // unsupported instruction
    } else if (HasVectorRestrictions(restrictions, kNoHiBits)) {
      // Shifts right need extra care to account for higher order bits.
      // TODO: less likely shr/unsigned and ushr/signed can by flipping signess.
      if (instruction->IsShr() &&
          (!IsNarrowerOperand(opa, type, &r, &is_unsigned) || is_unsigned)) {
        return false;  // reject, unless all operands are sign-extension narrower
      } else if (instruction->IsUShr() &&
                 (!IsNarrowerOperand(opa, type, &r, &is_unsigned) || !is_unsigned)) {
        return false;  // reject, unless all operands are zero-extension narrower
      }
    }
    // Accept shift operator for vectorizable/invariant operands.
    // TODO: accept symbolic, albeit loop invariant shift factors.
    DCHECK(r != nullptr);
    if (generate_code && vector_mode_ != kVector) {  // de-idiom
      r = opa;
    }
    int64_t distance = 0;
    if (VectorizeUse(node, r, generate_code, type, restrictions) &&
        IsInt64AndGet(opb, /*out*/ &distance)) {
      // Restrict shift distance to packed data type width.
      int64_t max_distance = DataType::Size(type) * 8;
      if (0 <= distance && distance < max_distance) {
        if (generate_code) {
          GenerateVecOp(instruction, vector_map_->Get(r), opb, type);
        }
        return true;
      }
    }
  } else if (instruction->IsAbs()) {
    // Deal with vector restrictions.
    HInstruction* opa = instruction->InputAt(0);
    HInstruction* r = opa;
    bool is_unsigned = false;
    if (HasVectorRestrictions(restrictions, kNoAbs)) {
      return false;
    } else if (HasVectorRestrictions(restrictions, kNoHiBits) &&
               (!IsNarrowerOperand(opa, type, &r, &is_unsigned) || is_unsigned)) {
      return false;  // reject, unless operand is sign-extension narrower
    }
    // Accept ABS(x) for vectorizable operand.
    DCHECK(r != nullptr);
    if (generate_code && vector_mode_ != kVector) {  // de-idiom
      r = opa;
    }
    if (VectorizeUse(node, r, generate_code, type, restrictions)) {
      if (generate_code) {
        GenerateVecOp(instruction,
                      vector_map_->Get(r),
                      nullptr,
                      HVecOperation::ToProperType(type, is_unsigned));
      }
      return true;
    }
  }
  return false;
}

uint32_t HLoopOptimization::GetVectorSizeInBytes() {
  return simd_register_size_;
}

bool HLoopOptimization::TrySetVectorType(DataType::Type type, uint64_t* restrictions) {
  const InstructionSetFeatures* features = compiler_options_->GetInstructionSetFeatures();
  switch (compiler_options_->GetInstructionSet()) {
    case InstructionSet::kArm:
    case InstructionSet::kThumb2:
      // Allow vectorization for all ARM devices, because Android assumes that
      // ARM 32-bit always supports advanced SIMD (64-bit SIMD).
      *restrictions |= kNoIfCond;
      switch (type) {
        case DataType::Type::kBool:
        case DataType::Type::kUint8:
        case DataType::Type::kInt8:
          *restrictions |= kNoDiv | kNoReduction | kNoDotProd;
          return TrySetVectorLength(type, 8);
        case DataType::Type::kUint16:
        case DataType::Type::kInt16:
          *restrictions |= kNoDiv | kNoStringCharAt | kNoReduction | kNoDotProd;
          return TrySetVectorLength(type, 4);
        case DataType::Type::kInt32:
          *restrictions |= kNoDiv | kNoWideSAD;
          return TrySetVectorLength(type, 2);
        default:
          break;
      }
      return false;
    case InstructionSet::kArm64:
      if (IsInPredicatedVectorizationMode()) {
        // SVE vectorization.
        CHECK(features->AsArm64InstructionSetFeatures()->HasSVE());
        size_t vector_length = simd_register_size_ / DataType::Size(type);
        DCHECK_EQ(simd_register_size_ % DataType::Size(type), 0u);
        switch (type) {
          case DataType::Type::kBool:
            *restrictions |= kNoDiv |
                             kNoSignedHAdd |
                             kNoUnsignedHAdd |
                             kNoUnroundedHAdd |
                             kNoSAD |
                             kNoIfCond;
            return TrySetVectorLength(type, vector_length);
          case DataType::Type::kUint8:
          case DataType::Type::kInt8:
            *restrictions |= kNoDiv |
                             kNoSignedHAdd |
                             kNoUnsignedHAdd |
                             kNoUnroundedHAdd |
                             kNoSAD;
            return TrySetVectorLength(type, vector_length);
          case DataType::Type::kUint16:
          case DataType::Type::kInt16:
            *restrictions |= kNoDiv |
                             kNoStringCharAt |   // TODO: support in predicated mode.
                             kNoSignedHAdd |
                             kNoUnsignedHAdd |
                             kNoUnroundedHAdd |
                             kNoSAD |
                             kNoDotProd;
            return TrySetVectorLength(type, vector_length);
          case DataType::Type::kInt32:
            *restrictions |= kNoDiv | kNoSAD;
            return TrySetVectorLength(type, vector_length);
          case DataType::Type::kInt64:
            *restrictions |= kNoDiv | kNoSAD | kNoIfCond;
            return TrySetVectorLength(type, vector_length);
          case DataType::Type::kFloat32:
            *restrictions |= kNoReduction | kNoIfCond;
            return TrySetVectorLength(type, vector_length);
          case DataType::Type::kFloat64:
            *restrictions |= kNoReduction | kNoIfCond;
            return TrySetVectorLength(type, vector_length);
          default:
            break;
        }
        return false;
      } else {
        // Allow vectorization for all ARM devices, because Android assumes that
        // ARMv8 AArch64 always supports advanced SIMD (128-bit SIMD).
        *restrictions |= kNoIfCond;
        switch (type) {
          case DataType::Type::kBool:
          case DataType::Type::kUint8:
          case DataType::Type::kInt8:
            *restrictions |= kNoDiv;
            return TrySetVectorLength(type, 16);
          case DataType::Type::kUint16:
          case DataType::Type::kInt16:
            *restrictions |= kNoDiv;
            return TrySetVectorLength(type, 8);
          case DataType::Type::kInt32:
            *restrictions |= kNoDiv;
            return TrySetVectorLength(type, 4);
          case DataType::Type::kInt64:
            *restrictions |= kNoDiv | kNoMul;
            return TrySetVectorLength(type, 2);
          case DataType::Type::kFloat32:
            *restrictions |= kNoReduction;
            return TrySetVectorLength(type, 4);
          case DataType::Type::kFloat64:
            *restrictions |= kNoReduction;
            return TrySetVectorLength(type, 2);
          default:
            break;
        }
        return false;
      }
    case InstructionSet::kX86:
    case InstructionSet::kX86_64:
      // Allow vectorization for SSE4.1-enabled X86 devices only (128-bit SIMD).
      *restrictions |= kNoIfCond;
      if (features->AsX86InstructionSetFeatures()->HasSSE4_1()) {
        switch (type) {
          case DataType::Type::kBool:
          case DataType::Type::kUint8:
          case DataType::Type::kInt8:
            *restrictions |= kNoMul |
                             kNoDiv |
                             kNoShift |
                             kNoAbs |
                             kNoSignedHAdd |
                             kNoUnroundedHAdd |
                             kNoSAD |
                             kNoDotProd;
            return TrySetVectorLength(type, 16);
          case DataType::Type::kUint16:
            *restrictions |= kNoDiv |
                             kNoAbs |
                             kNoSignedHAdd |
                             kNoUnroundedHAdd |
                             kNoSAD |
                             kNoDotProd;
            return TrySetVectorLength(type, 8);
          case DataType::Type::kInt16:
            *restrictions |= kNoDiv |
                             kNoAbs |
                             kNoSignedHAdd |
                             kNoUnroundedHAdd |
                             kNoSAD;
            return TrySetVectorLength(type, 8);
          case DataType::Type::kInt32:
            *restrictions |= kNoDiv | kNoSAD;
            return TrySetVectorLength(type, 4);
          case DataType::Type::kInt64:
            *restrictions |= kNoMul | kNoDiv | kNoShr | kNoAbs | kNoSAD;
            return TrySetVectorLength(type, 2);
          case DataType::Type::kFloat32:
            *restrictions |= kNoReduction;
            return TrySetVectorLength(type, 4);
          case DataType::Type::kFloat64:
            *restrictions |= kNoReduction;
            return TrySetVectorLength(type, 2);
          default:
            break;
        }  // switch type
      }
      return false;
    default:
      return false;
  }  // switch instruction set
}

bool HLoopOptimization::TrySetVectorLengthImpl(uint32_t length) {
  DCHECK(IsPowerOfTwo(length) && length >= 2u);
  // First time set?
  if (vector_length_ == 0) {
    vector_length_ = length;
  }
  // Different types are acceptable within a loop-body, as long as all the corresponding vector
  // lengths match exactly to obtain a uniform traversal through the vector iteration space
  // (idiomatic exceptions to this rule can be handled by further unrolling sub-expressions).
  return vector_length_ == length;
}

void HLoopOptimization::GenerateVecInv(HInstruction* org, DataType::Type type) {
  if (vector_map_->find(org) == vector_map_->end()) {
    // In scalar code, just use a self pass-through for scalar invariants
    // (viz. expression remains itself).
    if (vector_mode_ == kSequential) {
      vector_map_->Put(org, org);
      return;
    }
    // In vector code, explicit scalar expansion is needed.
    HInstruction* vector = nullptr;
    auto it = vector_permanent_map_->find(org);
    if (it != vector_permanent_map_->end()) {
      vector = it->second;  // reuse during unrolling
    } else {
      // Generates ReplicateScalar( (optional_type_conv) org ).
      HInstruction* input = org;
      DataType::Type input_type = input->GetType();
      if (type != input_type && (type == DataType::Type::kInt64 ||
                                 input_type == DataType::Type::kInt64)) {
        input = Insert(vector_preheader_,
                       new (global_allocator_) HTypeConversion(type, input, kNoDexPc));
      }
      vector = new (global_allocator_)
          HVecReplicateScalar(global_allocator_, input, type, vector_length_, kNoDexPc);
      vector_permanent_map_->Put(org, Insert(vector_preheader_, vector));
      vector_external_set_->insert(vector);
    }
    vector_map_->Put(org, vector);
  }
}

void HLoopOptimization::GenerateVecSub(HInstruction* org, HInstruction* offset) {
  if (vector_map_->find(org) == vector_map_->end()) {
    HInstruction* subscript = vector_index_;
    int64_t value = 0;
    if (!IsInt64AndGet(offset, &value) || value != 0) {
      subscript = new (global_allocator_) HAdd(DataType::Type::kInt32, subscript, offset);
      if (org->IsPhi()) {
        Insert(vector_body_, subscript);  // lacks layout placeholder
      }
    }
    vector_map_->Put(org, subscript);
  }
}

void HLoopOptimization::GenerateVecMem(HInstruction* org,
                                       HInstruction* opa,
                                       HInstruction* opb,
                                       HInstruction* offset,
                                       DataType::Type type) {
  uint32_t dex_pc = org->GetDexPc();
  HInstruction* vector = nullptr;
  if (vector_mode_ == kVector) {
    // Vector store or load.
    bool is_string_char_at = false;
    HInstruction* base = org->InputAt(0);
    if (opb != nullptr) {
      vector = new (global_allocator_) HVecStore(
          global_allocator_, base, opa, opb, type, org->GetSideEffects(), vector_length_, dex_pc);
    } else  {
      is_string_char_at = org->AsArrayGet()->IsStringCharAt();
      vector = new (global_allocator_) HVecLoad(global_allocator_,
                                                base,
                                                opa,
                                                type,
                                                org->GetSideEffects(),
                                                vector_length_,
                                                is_string_char_at,
                                                dex_pc);
    }
    // Known (forced/adjusted/original) alignment?
    if (vector_dynamic_peeling_candidate_ != nullptr) {
      if (vector_dynamic_peeling_candidate_->offset == offset &&  // TODO: diffs too?
          DataType::Size(vector_dynamic_peeling_candidate_->type) == DataType::Size(type) &&
          vector_dynamic_peeling_candidate_->is_string_char_at == is_string_char_at) {
        vector->AsVecMemoryOperation()->SetAlignment(  // forced
            Alignment(GetVectorSizeInBytes(), 0));
      }
    } else {
      vector->AsVecMemoryOperation()->SetAlignment(  // adjusted/original
          ComputeAlignment(offset, type, is_string_char_at, vector_static_peeling_factor_));
    }
  } else {
    // Scalar store or load.
    DCHECK(vector_mode_ == kSequential);
    if (opb != nullptr) {
      DataType::Type component_type = org->AsArraySet()->GetComponentType();
      vector = new (global_allocator_) HArraySet(
          org->InputAt(0), opa, opb, component_type, org->GetSideEffects(), dex_pc);
    } else  {
      bool is_string_char_at = org->AsArrayGet()->IsStringCharAt();
      vector = new (global_allocator_) HArrayGet(
          org->InputAt(0), opa, org->GetType(), org->GetSideEffects(), dex_pc, is_string_char_at);
    }
  }
  vector_map_->Put(org, vector);
}

void HLoopOptimization::GenerateVecReductionPhi(HPhi* orig_phi) {
  DCHECK(reductions_->find(orig_phi) != reductions_->end());
  DCHECK(reductions_->Get(orig_phi->InputAt(1)) == orig_phi);
  HInstruction* vector = nullptr;
  if (vector_mode_ == kSequential) {
    HPhi* new_phi = new (global_allocator_) HPhi(
        global_allocator_, kNoRegNumber, 0, orig_phi->GetType());
    vector_header_->AddPhi(new_phi);
    vector = new_phi;
  } else {
    // Link vector reduction back to prior unrolled update, or a first phi.
    auto it = vector_permanent_map_->find(orig_phi);
    if (it != vector_permanent_map_->end()) {
      vector = it->second;
    } else {
      HPhi* new_phi = new (global_allocator_) HPhi(
          global_allocator_, kNoRegNumber, 0, HVecOperation::kSIMDType);
      vector_header_->AddPhi(new_phi);
      vector = new_phi;
    }
  }
  vector_map_->Put(orig_phi, vector);
}

void HLoopOptimization::GenerateVecReductionPhiInputs(HPhi* phi, HInstruction* reduction) {
  HInstruction* new_phi = vector_map_->Get(phi);
  HInstruction* new_init = reductions_->Get(phi);
  HInstruction* new_red = vector_map_->Get(reduction);
  // Link unrolled vector loop back to new phi.
  for (; !new_phi->IsPhi(); new_phi = vector_permanent_map_->Get(new_phi)) {
    DCHECK(new_phi->IsVecOperation());
  }
  // Prepare the new initialization.
  if (vector_mode_ == kVector) {
    // Generate a [initial, 0, .., 0] vector for add or
    // a [initial, initial, .., initial] vector for min/max.
    HVecOperation* red_vector = new_red->AsVecOperation();
    HVecReduce::ReductionKind kind = GetReductionKind(red_vector);
    uint32_t vector_length = red_vector->GetVectorLength();
    DataType::Type type = red_vector->GetPackedType();
    if (kind == HVecReduce::ReductionKind::kSum) {
      new_init = Insert(vector_preheader_,
                        new (global_allocator_) HVecSetScalars(global_allocator_,
                                                               &new_init,
                                                               type,
                                                               vector_length,
                                                               1,
                                                               kNoDexPc));
    } else {
      new_init = Insert(vector_preheader_,
                        new (global_allocator_) HVecReplicateScalar(global_allocator_,
                                                                    new_init,
                                                                    type,
                                                                    vector_length,
                                                                    kNoDexPc));
    }
    vector_external_set_->insert(new_init);
  } else {
    new_init = ReduceAndExtractIfNeeded(new_init);
  }
  // Set the phi inputs.
  DCHECK(new_phi->IsPhi());
  new_phi->AsPhi()->AddInput(new_init);
  new_phi->AsPhi()->AddInput(new_red);
  // New feed value for next phi (safe mutation in iteration).
  reductions_->find(phi)->second = new_phi;
}

HInstruction* HLoopOptimization::ReduceAndExtractIfNeeded(HInstruction* instruction) {
  if (instruction->IsPhi()) {
    HInstruction* input = instruction->InputAt(1);
    if (HVecOperation::ReturnsSIMDValue(input)) {
      DCHECK(!input->IsPhi());
      HVecOperation* input_vector = input->AsVecOperation();
      uint32_t vector_length = input_vector->GetVectorLength();
      DataType::Type type = input_vector->GetPackedType();
      HVecReduce::ReductionKind kind = GetReductionKind(input_vector);
      HBasicBlock* exit = instruction->GetBlock()->GetSuccessors()[0];
      // Generate a vector reduction and scalar extract
      //    x = REDUCE( [x_1, .., x_n] )
      //    y = x_1
      // along the exit of the defining loop.
      HVecReduce* reduce = new (global_allocator_) HVecReduce(
          global_allocator_, instruction, type, vector_length, kind, kNoDexPc);
      exit->InsertInstructionBefore(reduce, exit->GetFirstInstruction());
      vector_external_set_->insert(reduce);
      instruction = new (global_allocator_) HVecExtractScalar(
          global_allocator_, reduce, type, vector_length, 0, kNoDexPc);
      exit->InsertInstructionAfter(instruction, reduce);

      vector_external_set_->insert(instruction);
    }
  }
  return instruction;
}

#define GENERATE_VEC(x, y) \
  if (vector_mode_ == kVector) { \
    vector = (x); \
  } else { \
    DCHECK(vector_mode_ == kSequential); \
    vector = (y); \
  } \
  break;

HInstruction* HLoopOptimization::GenerateVecOp(HInstruction* org,
                                               HInstruction* opa,
                                               HInstruction* opb,
                                               DataType::Type type) {
  uint32_t dex_pc = org->GetDexPc();
  HInstruction* vector = nullptr;
  DataType::Type org_type = org->GetType();
  switch (org->GetKind()) {
    case HInstruction::kNeg:
      DCHECK(opb == nullptr);
      GENERATE_VEC(
        new (global_allocator_) HVecNeg(global_allocator_, opa, type, vector_length_, dex_pc),
        new (global_allocator_) HNeg(org_type, opa, dex_pc));
    case HInstruction::kNot:
      DCHECK(opb == nullptr);
      GENERATE_VEC(
        new (global_allocator_) HVecNot(global_allocator_, opa, type, vector_length_, dex_pc),
        new (global_allocator_) HNot(org_type, opa, dex_pc));
    case HInstruction::kBooleanNot:
      DCHECK(opb == nullptr);
      GENERATE_VEC(
        new (global_allocator_) HVecNot(global_allocator_, opa, type, vector_length_, dex_pc),
        new (global_allocator_) HBooleanNot(opa, dex_pc));
    case HInstruction::kTypeConversion:
      DCHECK(opb == nullptr);
      GENERATE_VEC(
        new (global_allocator_) HVecCnv(global_allocator_, opa, type, vector_length_, dex_pc),
        new (global_allocator_) HTypeConversion(org_type, opa, dex_pc));
    case HInstruction::kAdd:
      GENERATE_VEC(
        new (global_allocator_) HVecAdd(global_allocator_, opa, opb, type, vector_length_, dex_pc),
        new (global_allocator_) HAdd(org_type, opa, opb, dex_pc));
    case HInstruction::kSub:
      GENERATE_VEC(
        new (global_allocator_) HVecSub(global_allocator_, opa, opb, type, vector_length_, dex_pc),
        new (global_allocator_) HSub(org_type, opa, opb, dex_pc));
    case HInstruction::kMul:
      GENERATE_VEC(
        new (global_allocator_) HVecMul(global_allocator_, opa, opb, type, vector_length_, dex_pc),
        new (global_allocator_) HMul(org_type, opa, opb, dex_pc));
    case HInstruction::kDiv:
      GENERATE_VEC(
        new (global_allocator_) HVecDiv(global_allocator_, opa, opb, type, vector_length_, dex_pc),
        new (global_allocator_) HDiv(org_type, opa, opb, dex_pc));
    case HInstruction::kAnd:
      GENERATE_VEC(
        new (global_allocator_) HVecAnd(global_allocator_, opa, opb, type, vector_length_, dex_pc),
        new (global_allocator_) HAnd(org_type, opa, opb, dex_pc));
    case HInstruction::kOr:
      GENERATE_VEC(
        new (global_allocator_) HVecOr(global_allocator_, opa, opb, type, vector_length_, dex_pc),
        new (global_allocator_) HOr(org_type, opa, opb, dex_pc));
    case HInstruction::kXor:
      GENERATE_VEC(
        new (global_allocator_) HVecXor(global_allocator_, opa, opb, type, vector_length_, dex_pc),
        new (global_allocator_) HXor(org_type, opa, opb, dex_pc));
    case HInstruction::kShl:
      GENERATE_VEC(
        new (global_allocator_) HVecShl(global_allocator_, opa, opb, type, vector_length_, dex_pc),
        new (global_allocator_) HShl(org_type, opa, opb, dex_pc));
    case HInstruction::kShr:
      GENERATE_VEC(
        new (global_allocator_) HVecShr(global_allocator_, opa, opb, type, vector_length_, dex_pc),
        new (global_allocator_) HShr(org_type, opa, opb, dex_pc));
    case HInstruction::kUShr:
      GENERATE_VEC(
        new (global_allocator_) HVecUShr(global_allocator_, opa, opb, type, vector_length_, dex_pc),
        new (global_allocator_) HUShr(org_type, opa, opb, dex_pc));
    case HInstruction::kAbs:
      DCHECK(opb == nullptr);
      GENERATE_VEC(
        new (global_allocator_) HVecAbs(global_allocator_, opa, type, vector_length_, dex_pc),
        new (global_allocator_) HAbs(org_type, opa, dex_pc));
    case HInstruction::kEqual: {
        // Special case.
        if (vector_mode_ == kVector) {
          vector = new (global_allocator_) HVecCondition(
              global_allocator_, opa, opb, type, vector_length_, dex_pc);
        } else {
          DCHECK(vector_mode_ == kSequential);
          UNREACHABLE();
        }
      }
      break;
    default:
      break;
  }  // switch
  CHECK(vector != nullptr) << "Unsupported SIMD operator";
  vector_map_->Put(org, vector);
  return vector;
}

#undef GENERATE_VEC

//
// Vectorization idioms.
//

// Method recognizes the following idioms:
//   rounding  halving add (a + b + 1) >> 1 for unsigned/signed operands a, b
//   truncated halving add (a + b)     >> 1 for unsigned/signed operands a, b
// Provided that the operands are promoted to a wider form to do the arithmetic and
// then cast back to narrower form, the idioms can be mapped into efficient SIMD
// implementation that operates directly in narrower form (plus one extra bit).
// TODO: current version recognizes implicit byte/short/char widening only;
//       explicit widening from int to long could be added later.
bool HLoopOptimization::VectorizeHalvingAddIdiom(LoopNode* node,
                                                 HInstruction* instruction,
                                                 bool generate_code,
                                                 DataType::Type type,
                                                 uint64_t restrictions) {
  // Test for top level arithmetic shift right x >> 1 or logical shift right x >>> 1
  // (note whether the sign bit in wider precision is shifted in has no effect
  // on the narrow precision computed by the idiom).
  if ((instruction->IsShr() ||
       instruction->IsUShr()) &&
      IsInt64Value(instruction->InputAt(1), 1)) {
    // Test for (a + b + c) >> 1 for optional constant c.
    HInstruction* a = nullptr;
    HInstruction* b = nullptr;
    int64_t       c = 0;
    if (IsAddConst2(graph_, instruction->InputAt(0), /*out*/ &a, /*out*/ &b, /*out*/ &c)) {
      // Accept c == 1 (rounded) or c == 0 (not rounded).
      bool is_rounded = false;
      if (c == 1) {
        is_rounded = true;
      } else if (c != 0) {
        return false;
      }
      // Accept consistent zero or sign extension on operands a and b.
      HInstruction* r = nullptr;
      HInstruction* s = nullptr;
      bool is_unsigned = false;
      if (!IsNarrowerOperands(a, b, type, &r, &s, &is_unsigned)) {
        return false;
      }
      // Deal with vector restrictions.
      if ((is_unsigned && HasVectorRestrictions(restrictions, kNoUnsignedHAdd)) ||
          (!is_unsigned && HasVectorRestrictions(restrictions, kNoSignedHAdd)) ||
          (!is_rounded && HasVectorRestrictions(restrictions, kNoUnroundedHAdd))) {
        return false;
      }
      // Accept recognized halving add for vectorizable operands. Vectorized code uses the
      // shorthand idiomatic operation. Sequential code uses the original scalar expressions.
      DCHECK(r != nullptr && s != nullptr);
      if (generate_code && vector_mode_ != kVector) {  // de-idiom
        r = instruction->InputAt(0);
        s = instruction->InputAt(1);
      }
      if (VectorizeUse(node, r, generate_code, type, restrictions) &&
          VectorizeUse(node, s, generate_code, type, restrictions)) {
        if (generate_code) {
          if (vector_mode_ == kVector) {
            vector_map_->Put(instruction, new (global_allocator_) HVecHalvingAdd(
                global_allocator_,
                vector_map_->Get(r),
                vector_map_->Get(s),
                HVecOperation::ToProperType(type, is_unsigned),
                vector_length_,
                is_rounded,
                kNoDexPc));
            MaybeRecordStat(stats_, MethodCompilationStat::kLoopVectorizedIdiom);
          } else {
            GenerateVecOp(instruction, vector_map_->Get(r), vector_map_->Get(s), type);
          }
        }
        return true;
      }
    }
  }
  return false;
}

// Method recognizes the following idiom:
//   q += ABS(a - b) for signed operands a, b
// Provided that the operands have the same type or are promoted to a wider form.
// Since this may involve a vector length change, the idiom is handled by going directly
// to a sad-accumulate node (rather than relying combining finer grained nodes later).
// TODO: unsigned SAD too?
bool HLoopOptimization::VectorizeSADIdiom(LoopNode* node,
                                          HInstruction* instruction,
                                          bool generate_code,
                                          DataType::Type reduction_type,
                                          uint64_t restrictions) {
  // Filter integral "q += ABS(a - b);" reduction, where ABS and SUB
  // are done in the same precision (either int or long).
  if (!instruction->IsAdd() ||
      (reduction_type != DataType::Type::kInt32 && reduction_type != DataType::Type::kInt64)) {
    return false;
  }
  HInstruction* acc = instruction->InputAt(0);
  HInstruction* abs = instruction->InputAt(1);
  HInstruction* a = nullptr;
  HInstruction* b = nullptr;
  if (abs->IsAbs() &&
      abs->GetType() == reduction_type &&
      IsSubConst2(graph_, abs->InputAt(0), /*out*/ &a, /*out*/ &b)) {
    DCHECK(a != nullptr && b != nullptr);
  } else {
    return false;
  }
  // Accept same-type or consistent sign extension for narrower-type on operands a and b.
  // The same-type or narrower operands are called r (a or lower) and s (b or lower).
  // We inspect the operands carefully to pick the most suited type.
  HInstruction* r = a;
  HInstruction* s = b;
  bool is_unsigned = false;
  DataType::Type sub_type = GetNarrowerType(a, b);
  if (reduction_type != sub_type &&
      (!IsNarrowerOperands(a, b, sub_type, &r, &s, &is_unsigned) || is_unsigned)) {
    return false;
  }
  // Try same/narrower type and deal with vector restrictions.
  if (!TrySetVectorType(sub_type, &restrictions) ||
      HasVectorRestrictions(restrictions, kNoSAD) ||
      (reduction_type != sub_type && HasVectorRestrictions(restrictions, kNoWideSAD))) {
    return false;
  }
  // Accept SAD idiom for vectorizable operands. Vectorized code uses the shorthand
  // idiomatic operation. Sequential code uses the original scalar expressions.
  DCHECK(r != nullptr && s != nullptr);
  if (generate_code && vector_mode_ != kVector) {  // de-idiom
    r = s = abs->InputAt(0);
  }
  if (VectorizeUse(node, acc, generate_code, sub_type, restrictions) &&
      VectorizeUse(node, r, generate_code, sub_type, restrictions) &&
      VectorizeUse(node, s, generate_code, sub_type, restrictions)) {
    if (generate_code) {
      if (vector_mode_ == kVector) {
        vector_map_->Put(instruction, new (global_allocator_) HVecSADAccumulate(
            global_allocator_,
            vector_map_->Get(acc),
            vector_map_->Get(r),
            vector_map_->Get(s),
            HVecOperation::ToProperType(reduction_type, is_unsigned),
            GetOtherVL(reduction_type, sub_type, vector_length_),
            kNoDexPc));
        MaybeRecordStat(stats_, MethodCompilationStat::kLoopVectorizedIdiom);
      } else {
        // "GenerateVecOp()" must not be called more than once for each original loop body
        // instruction. As the SAD idiom processes both "current" instruction ("instruction")
        // and its ABS input in one go, we must check that for the scalar case the ABS instruction
        // has not yet been processed.
        if (vector_map_->find(abs) == vector_map_->end()) {
          GenerateVecOp(abs, vector_map_->Get(r), nullptr, reduction_type);
        }
        GenerateVecOp(instruction, vector_map_->Get(acc), vector_map_->Get(abs), reduction_type);
      }
    }
    return true;
  }
  return false;
}

// Method recognises the following dot product idiom:
//   q += a * b for operands a, b whose type is narrower than the reduction one.
// Provided that the operands have the same type or are promoted to a wider form.
// Since this may involve a vector length change, the idiom is handled by going directly
// to a dot product node (rather than relying combining finer grained nodes later).
bool HLoopOptimization::VectorizeDotProdIdiom(LoopNode* node,
                                              HInstruction* instruction,
                                              bool generate_code,
                                              DataType::Type reduction_type,
                                              uint64_t restrictions) {
  if (!instruction->IsAdd() || reduction_type != DataType::Type::kInt32) {
    return false;
  }

  HInstruction* const acc = instruction->InputAt(0);
  HInstruction* const mul = instruction->InputAt(1);
  if (!mul->IsMul() || mul->GetType() != reduction_type) {
    return false;
  }

  HInstruction* const mul_left = mul->InputAt(0);
  HInstruction* const mul_right = mul->InputAt(1);
  HInstruction* r = mul_left;
  HInstruction* s = mul_right;
  DataType::Type op_type = GetNarrowerType(mul_left, mul_right);
  bool is_unsigned = false;

  if (!IsNarrowerOperands(mul_left, mul_right, op_type, &r, &s, &is_unsigned)) {
    return false;
  }
  op_type = HVecOperation::ToProperType(op_type, is_unsigned);

  if (!TrySetVectorType(op_type, &restrictions) ||
      HasVectorRestrictions(restrictions, kNoDotProd)) {
    return false;
  }

  DCHECK(r != nullptr && s != nullptr);
  // Accept dot product idiom for vectorizable operands. Vectorized code uses the shorthand
  // idiomatic operation. Sequential code uses the original scalar expressions.
  if (generate_code && vector_mode_ != kVector) {  // de-idiom
    r = mul_left;
    s = mul_right;
  }
  if (VectorizeUse(node, acc, generate_code, op_type, restrictions) &&
      VectorizeUse(node, r, generate_code, op_type, restrictions) &&
      VectorizeUse(node, s, generate_code, op_type, restrictions)) {
    if (generate_code) {
      if (vector_mode_ == kVector) {
        vector_map_->Put(instruction, new (global_allocator_) HVecDotProd(
            global_allocator_,
            vector_map_->Get(acc),
            vector_map_->Get(r),
            vector_map_->Get(s),
            reduction_type,
            is_unsigned,
            GetOtherVL(reduction_type, op_type, vector_length_),
            kNoDexPc));
        MaybeRecordStat(stats_, MethodCompilationStat::kLoopVectorizedIdiom);
      } else {
        // "GenerateVecOp()" must not be called more than once for each original loop body
        // instruction. As the DotProd idiom processes both "current" instruction ("instruction")
        // and its MUL input in one go, we must check that for the scalar case the MUL instruction
        // has not yet been processed.
        if (vector_map_->find(mul) == vector_map_->end()) {
          GenerateVecOp(mul, vector_map_->Get(r), vector_map_->Get(s), reduction_type);
        }
        GenerateVecOp(instruction, vector_map_->Get(acc), vector_map_->Get(mul), reduction_type);
      }
    }
    return true;
  }
  return false;
}

bool HLoopOptimization::VectorizeIfCondition(LoopNode* node,
                                             HInstruction* hif,
                                             bool generate_code,
                                             uint64_t restrictions) {
  DCHECK(hif->IsIf());
  HInstruction* if_input = hif->InputAt(0);

  if (!if_input->HasOnlyOneNonEnvironmentUse()) {
    // Avoid the complications of the condition used as materialized boolean.
    return false;
  }

  if (!if_input->IsEqual()) {
    // TODO: Support other condition types.
    return false;
  }

  HCondition* cond = if_input->AsCondition();
  HInstruction* opa = cond->InputAt(0);
  HInstruction* opb = cond->InputAt(1);
  DataType::Type type = GetNarrowerType(opa, opb);

  if (!DataType::IsIntegralType(type)) {
    return false;
  }

  bool is_unsigned = false;
  HInstruction* opa_promoted = opa;
  HInstruction* opb_promoted = opb;
  bool is_int_case = DataType::Type::kInt32 == opa->GetType() &&
                     DataType::Type::kInt32 == opb->GetType();

  // Condition arguments should be either both int32 or consistently extended signed/unsigned
  // narrower operands.
  if (!is_int_case &&
      !IsNarrowerOperands(opa, opb, type, &opa_promoted, &opb_promoted, &is_unsigned)) {
    return false;
  }
  type = HVecOperation::ToProperType(type, is_unsigned);

  // For narrow types, explicit type conversion may have been
  // optimized way, so set the no hi bits restriction here.
  if (DataType::Size(type) <= 2) {
    restrictions |= kNoHiBits;
  }

  if (!TrySetVectorType(type, &restrictions) ||
      HasVectorRestrictions(restrictions, kNoIfCond)) {
    return false;
  }

  if (generate_code && vector_mode_ != kVector) {  // de-idiom
    opa_promoted = opa;
    opb_promoted = opb;
  }

  if (VectorizeUse(node, opa_promoted, generate_code, type, restrictions) &&
      VectorizeUse(node, opb_promoted, generate_code, type, restrictions)) {
    if (generate_code) {
      HInstruction* vec_cond = GenerateVecOp(cond,
                                             vector_map_->Get(opa_promoted),
                                             vector_map_->Get(opb_promoted),
                                             type);

      if (vector_mode_ == kVector) {
          HInstruction* vec_pred_not = new (global_allocator_) HVecPredNot(
              global_allocator_, vec_cond, type, vector_length_, hif->GetDexPc());

          vector_map_->Put(hif, vec_pred_not);
          BlockPredicateInfo* pred_info = predicate_info_map_->Get(hif->GetBlock());
          pred_info->SetControlFlowInfo(vec_cond->AsVecPredSetOperation(),
                                        vec_pred_not->AsVecPredSetOperation());
        } else {
          DCHECK(vector_mode_ == kSequential);
          UNREACHABLE();
      }
    }
    return true;
  }

  return false;
}

//
// Vectorization heuristics.
//

Alignment HLoopOptimization::ComputeAlignment(HInstruction* offset,
                                              DataType::Type type,
                                              bool is_string_char_at,
                                              uint32_t peeling) {
  // Combine the alignment and hidden offset that is guaranteed by
  // the Android runtime with a known starting index adjusted as bytes.
  int64_t value = 0;
  if (IsInt64AndGet(offset, /*out*/ &value)) {
    uint32_t start_offset =
        HiddenOffset(type, is_string_char_at) + (value + peeling) * DataType::Size(type);
    return Alignment(BaseAlignment(), start_offset & (BaseAlignment() - 1u));
  }
  // Otherwise, the Android runtime guarantees at least natural alignment.
  return Alignment(DataType::Size(type), 0);
}

void HLoopOptimization::SetAlignmentStrategy(const ScopedArenaVector<uint32_t>& peeling_votes,
                                             const ArrayReference* peeling_candidate) {
  // Current heuristic: pick the best static loop peeling factor, if any,
  // or otherwise use dynamic loop peeling on suggested peeling candidate.
  uint32_t max_vote = 0;
  for (size_t i = 0; i < peeling_votes.size(); i++) {
    if (peeling_votes[i] > max_vote) {
      max_vote = peeling_votes[i];
      vector_static_peeling_factor_ = i;
    }
  }
  if (max_vote == 0) {
    vector_dynamic_peeling_candidate_ = peeling_candidate;
  }
}

uint32_t HLoopOptimization::MaxNumberPeeled() {
  if (vector_dynamic_peeling_candidate_ != nullptr) {
    return vector_length_ - 1u;  // worst-case
  }
  return vector_static_peeling_factor_;  // known exactly
}

bool HLoopOptimization::IsVectorizationProfitable(int64_t trip_count) {
  // Current heuristic: non-empty body with sufficient number of iterations (if known).
  // TODO: refine by looking at e.g. operation count, alignment, etc.
  // TODO: trip count is really unsigned entity, provided the guarding test
  //       is satisfied; deal with this more carefully later
  uint32_t max_peel = MaxNumberPeeled();
  // Peeling is not supported in predicated mode.
  DCHECK_IMPLIES(IsInPredicatedVectorizationMode(), max_peel == 0u);
  if (vector_length_ == 0) {
    return false;  // nothing found
  } else if (trip_count < 0) {
    return false;  // guard against non-taken/large
  } else if ((0 < trip_count) && (trip_count < (vector_length_ + max_peel))) {
    return false;  // insufficient iterations
  }
  return true;
}

//
// Helpers.
//

bool HLoopOptimization::TrySetPhiInduction(HPhi* phi, bool restrict_uses) {
  // Start with empty phi induction.
  iset_->clear();

  // Special case Phis that have equivalent in a debuggable setup. Our graph checker isn't
  // smart enough to follow strongly connected components (and it's probably not worth
  // it to make it so). See b/33775412.
  if (graph_->IsDebuggable() && phi->HasEquivalentPhi()) {
    return false;
  }

  // Lookup phi induction cycle.
  ArenaSet<HInstruction*>* set = induction_range_.LookupCycle(phi);
  if (set != nullptr) {
    for (HInstruction* i : *set) {
      // Check that, other than instructions that are no longer in the graph (removed earlier)
      // each instruction is removable and, when restrict uses are requested, other than for phi,
      // all uses are contained within the cycle.
      if (!i->IsInBlock()) {
        continue;
      } else if (!i->IsRemovable()) {
        return false;
      } else if (i != phi && restrict_uses) {
        // Deal with regular uses.
        for (const HUseListNode<HInstruction*>& use : i->GetUses()) {
          if (set->find(use.GetUser()) == set->end()) {
            return false;
          }
        }
      }
      iset_->insert(i);  // copy
    }
    return true;
  }
  return false;
}

bool HLoopOptimization::TrySetPhiReduction(HPhi* phi) {
  DCHECK(phi->IsLoopHeaderPhi());
  // Only unclassified phi cycles are candidates for reductions.
  if (induction_range_.IsClassified(phi)) {
    return false;
  }
  // Accept operations like x = x + .., provided that the phi and the reduction are
  // used exactly once inside the loop, and by each other.
  HInputsRef inputs = phi->GetInputs();
  if (inputs.size() == 2) {
    HInstruction* reduction = inputs[1];
    if (HasReductionFormat(reduction, phi)) {
      HLoopInformation* loop_info = phi->GetBlock()->GetLoopInformation();
      DCHECK(loop_info->Contains(*reduction->GetBlock()));
      const bool single_use_inside_loop =
          // Reduction update only used by phi.
          reduction->GetUses().HasExactlyOneElement() &&
          !reduction->HasEnvironmentUses() &&
          // Reduction update is only use of phi inside the loop.
          std::none_of(phi->GetUses().begin(),
                       phi->GetUses().end(),
                       [loop_info, reduction](const HUseListNode<HInstruction*>& use) {
                         HInstruction* user = use.GetUser();
                         return user != reduction && loop_info->Contains(*user->GetBlock());
                       });
      if (single_use_inside_loop) {
        // Link reduction back, and start recording feed value.
        reductions_->Put(reduction, phi);
        reductions_->Put(phi, phi->InputAt(0));
        return true;
      }
    }
  }
  return false;
}

bool HLoopOptimization::TrySetSimpleLoopHeader(HBasicBlock* block, /*out*/ HPhi** main_phi) {
  // Start with empty phi induction and reductions.
  iset_->clear();
  reductions_->clear();

  // Scan the phis to find the following (the induction structure has already
  // been optimized, so we don't need to worry about trivial cases):
  // (1) optional reductions in loop,
  // (2) the main induction, used in loop control.
  HPhi* phi = nullptr;
  for (HInstructionIterator it(block->GetPhis()); !it.Done(); it.Advance()) {
    if (TrySetPhiReduction(it.Current()->AsPhi())) {
      continue;
    } else if (phi == nullptr) {
      // Found the first candidate for main induction.
      phi = it.Current()->AsPhi();
    } else {
      return false;
    }
  }

  // Then test for a typical loopheader:
  //   s:  SuspendCheck
  //   c:  Condition(phi, bound)
  //   i:  If(c)
  if (phi != nullptr && TrySetPhiInduction(phi, /*restrict_uses*/ false)) {
    HInstruction* s = block->GetFirstInstruction();
    if (s != nullptr && s->IsSuspendCheck()) {
      HInstruction* c = s->GetNext();
      if (c != nullptr &&
          c->IsCondition() &&
          c->GetUses().HasExactlyOneElement() &&  // only used for termination
          !c->HasEnvironmentUses()) {  // unlikely, but not impossible
        HInstruction* i = c->GetNext();
        if (i != nullptr && i->IsIf() && i->InputAt(0) == c) {
          iset_->insert(c);
          iset_->insert(s);
          *main_phi = phi;
          return true;
        }
      }
    }
  }
  return false;
}

bool HLoopOptimization::IsEmptyBody(HBasicBlock* block) {
  if (!block->GetPhis().IsEmpty()) {
    return false;
  }
  for (HInstructionIterator it(block->GetInstructions()); !it.Done(); it.Advance()) {
    HInstruction* instruction = it.Current();
    if (!instruction->IsGoto() && iset_->find(instruction) == iset_->end()) {
      return false;
    }
  }
  return true;
}

bool HLoopOptimization::IsUsedOutsideLoop(HLoopInformation* loop_info,
                                          HInstruction* instruction) {
  // Deal with regular uses.
  for (const HUseListNode<HInstruction*>& use : instruction->GetUses()) {
    if (use.GetUser()->GetBlock()->GetLoopInformation() != loop_info) {
      return true;
    }
  }
  return false;
}

bool HLoopOptimization::IsOnlyUsedAfterLoop(HLoopInformation* loop_info,
                                            HInstruction* instruction,
                                            bool collect_loop_uses,
                                            /*out*/ uint32_t* use_count) {
  // Deal with regular uses.
  for (const HUseListNode<HInstruction*>& use : instruction->GetUses()) {
    HInstruction* user = use.GetUser();
    if (iset_->find(user) == iset_->end()) {  // not excluded?
      if (loop_info->Contains(*user->GetBlock())) {
        // If collect_loop_uses is set, simply keep adding those uses to the set.
        // Otherwise, reject uses inside the loop that were not already in the set.
        if (collect_loop_uses) {
          iset_->insert(user);
          continue;
        }
        return false;
      }
      ++*use_count;
    }
  }
  return true;
}

bool HLoopOptimization::TryReplaceWithLastValue(HLoopInformation* loop_info,
                                                HInstruction* instruction,
                                                HBasicBlock* block) {
  // Try to replace outside uses with the last value.
  if (induction_range_.CanGenerateLastValue(instruction)) {
    HInstruction* replacement = induction_range_.GenerateLastValue(instruction, graph_, block);
    // Deal with regular uses.
    const HUseList<HInstruction*>& uses = instruction->GetUses();
    for (auto it = uses.begin(), end = uses.end(); it != end;) {
      HInstruction* user = it->GetUser();
      size_t index = it->GetIndex();
      ++it;  // increment before replacing
      if (iset_->find(user) == iset_->end()) {  // not excluded?
        if (kIsDebugBuild) {
          // We have checked earlier in 'IsOnlyUsedAfterLoop' that the use is after the loop.
          HLoopInformation* other_loop_info = user->GetBlock()->GetLoopInformation();
          CHECK(other_loop_info == nullptr || !other_loop_info->IsIn(*loop_info));
        }
        user->ReplaceInput(replacement, index);
        induction_range_.Replace(user, instruction, replacement);  // update induction
      }
    }
    // Deal with environment uses.
    const HUseList<HEnvironment*>& env_uses = instruction->GetEnvUses();
    for (auto it = env_uses.begin(), end = env_uses.end(); it != end;) {
      HEnvironment* user = it->GetUser();
      size_t index = it->GetIndex();
      ++it;  // increment before replacing
      if (iset_->find(user->GetHolder()) == iset_->end()) {  // not excluded?
        // Only update environment uses after the loop.
        HLoopInformation* other_loop_info = user->GetHolder()->GetBlock()->GetLoopInformation();
        if (other_loop_info == nullptr || !other_loop_info->IsIn(*loop_info)) {
          user->RemoveAsUserOfInput(index);
          user->SetRawEnvAt(index, replacement);
          replacement->AddEnvUseAt(user, index);
        }
      }
    }
    return true;
  }
  return false;
}

bool HLoopOptimization::TryAssignLastValue(HLoopInformation* loop_info,
                                           HInstruction* instruction,
                                           HBasicBlock* block,
                                           bool collect_loop_uses) {
  // Assigning the last value is always successful if there are no uses.
  // Otherwise, it succeeds in a no early-exit loop by generating the
  // proper last value assignment.
  uint32_t use_count = 0;
  return IsOnlyUsedAfterLoop(loop_info, instruction, collect_loop_uses, &use_count) &&
      (use_count == 0 ||
       (!IsEarlyExit(loop_info) && TryReplaceWithLastValue(loop_info, instruction, block)));
}

void HLoopOptimization::RemoveDeadInstructions(const HInstructionList& list) {
  for (HBackwardInstructionIterator i(list); !i.Done(); i.Advance()) {
    HInstruction* instruction = i.Current();
    if (instruction->IsDeadAndRemovable()) {
      simplified_ = true;
      instruction->GetBlock()->RemoveInstructionOrPhi(instruction);
    }
  }
}

bool HLoopOptimization::CanRemoveCycle() {
  for (HInstruction* i : *iset_) {
    // We can never remove instructions that have environment
    // uses when we compile 'debuggable'.
    if (i->HasEnvironmentUses() && graph_->IsDebuggable()) {
      return false;
    }
    // A deoptimization should never have an environment input removed.
    for (const HUseListNode<HEnvironment*>& use : i->GetEnvUses()) {
      if (use.GetUser()->GetHolder()->IsDeoptimize()) {
        return false;
      }
    }
  }
  return true;
}

void HLoopOptimization::PreparePredicateInfoMap(LoopNode* node) {
  HLoopInformation* loop_info = node->loop_info;

  DCHECK(IsPredicatedLoopControlFlowSupported(loop_info));

  for (HBlocksInLoopIterator block_it(*loop_info);
      !block_it.Done();
      block_it.Advance()) {
    HBasicBlock* cur_block = block_it.Current();
    BlockPredicateInfo* pred_info = new (loop_allocator_) BlockPredicateInfo();

    predicate_info_map_->Put(cur_block, pred_info);
  }
}

void HLoopOptimization::InitPredicateInfoMap(LoopNode* node,
                                             HVecPredSetOperation* loop_main_pred) {
  HLoopInformation* loop_info = node->loop_info;
  HBasicBlock* header = loop_info->GetHeader();
  BlockPredicateInfo* header_info = predicate_info_map_->Get(header);
  // Loop header is a special case; it doesn't have a false predicate because we
  // would just exit the loop then.
  header_info->SetControlFlowInfo(loop_main_pred, loop_main_pred);

  size_t blocks_in_loop = header->GetLoopInformation()->GetBlocks().NumSetBits();
  if (blocks_in_loop == 2) {
    for (HBasicBlock* successor : header->GetSuccessors()) {
      if (loop_info->Contains(*successor)) {
        // This is loop second block - body.
        BlockPredicateInfo* body_info = predicate_info_map_->Get(successor);
        body_info->SetControlPredicate(loop_main_pred);
        return;
      }
    }
    UNREACHABLE();
  }

  // TODO: support predicated vectorization of CF loop of more complex structure.
  DCHECK(HasLoopDiamondStructure(loop_info));
  HBasicBlock* header_succ_0 = header->GetSuccessors()[0];
  HBasicBlock* header_succ_1 = header->GetSuccessors()[1];
  HBasicBlock* diamond_top = loop_info->Contains(*header_succ_0) ?
                             header_succ_0 :
                             header_succ_1;

  HIf* diamond_hif = diamond_top->GetLastInstruction()->AsIf();
  HBasicBlock* diamond_true = diamond_hif->IfTrueSuccessor();
  HBasicBlock* diamond_false = diamond_hif->IfFalseSuccessor();
  HBasicBlock* back_edge = diamond_true->GetSingleSuccessor();

  BlockPredicateInfo* diamond_top_info = predicate_info_map_->Get(diamond_top);
  BlockPredicateInfo* diamond_true_info = predicate_info_map_->Get(diamond_true);
  BlockPredicateInfo* diamond_false_info = predicate_info_map_->Get(diamond_false);
  BlockPredicateInfo* back_edge_info = predicate_info_map_->Get(back_edge);

  diamond_top_info->SetControlPredicate(header_info->GetTruePredicate());

  diamond_true_info->SetControlPredicate(diamond_top_info->GetTruePredicate());
  diamond_false_info->SetControlPredicate(diamond_top_info->GetFalsePredicate());

  back_edge_info->SetControlPredicate(header_info->GetTruePredicate());
}

}  // namespace art
