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

#include "code_sinking.h"

#include <sstream>

#include "android-base/logging.h"
#include "base/arena_bit_vector.h"
#include "base/array_ref.h"
#include "base/bit_vector-inl.h"
#include "base/globals.h"
#include "base/logging.h"
#include "base/scoped_arena_allocator.h"
#include "base/scoped_arena_containers.h"
#include "common_dominator.h"
#include "nodes.h"

namespace art HIDDEN {

bool CodeSinking::Run() {
  if (graph_->GetExitBlock() == nullptr) {
    // Infinite loop, just bail.
    return false;
  }

  UncommonBranchSinking();
  ReturnSinking();
  return true;
}

void CodeSinking::UncommonBranchSinking() {
  HBasicBlock* exit = graph_->GetExitBlock();
  DCHECK(exit != nullptr);
  // TODO(ngeoffray): we do not profile branches yet, so use throw instructions
  // as an indicator of an uncommon branch.
  for (HBasicBlock* exit_predecessor : exit->GetPredecessors()) {
    HInstruction* last = exit_predecessor->GetLastInstruction();

    // TryBoundary instructions are sometimes inserted between the last instruction (e.g. Throw,
    // Return) and Exit. We don't want to use that instruction for our "uncommon branch" heuristic
    // because they are not as good an indicator as throwing branches, so we skip them and fetch the
    // actual last instruction.
    if (last->IsTryBoundary()) {
      // We have an exit try boundary. Fetch the previous instruction.
      DCHECK(!last->AsTryBoundary()->IsEntry());
      if (last->GetPrevious() == nullptr) {
        DCHECK(exit_predecessor->IsSingleTryBoundary());
        exit_predecessor = exit_predecessor->GetSinglePredecessor();
        last = exit_predecessor->GetLastInstruction();
      } else {
        last = last->GetPrevious();
      }
    }

    // Any predecessor of the exit that does not return, throws an exception.
    if (!last->IsReturn() && !last->IsReturnVoid()) {
      SinkCodeToUncommonBranch(exit_predecessor);
    }
  }
}

static bool IsInterestingInstruction(HInstruction* instruction) {
  // Instructions from the entry graph (for example constants) are never interesting to move.
  if (instruction->GetBlock() == instruction->GetBlock()->GetGraph()->GetEntryBlock()) {
    return false;
  }
  // We want to move moveable instructions that cannot throw, as well as
  // heap stores and allocations.

  // Volatile stores cannot be moved.
  if (instruction->IsInstanceFieldSet()) {
    if (instruction->AsInstanceFieldSet()->IsVolatile()) {
      return false;
    }
  }

  // Check allocations and strings first, as they can throw, but it is safe to move them.
  if (instruction->IsNewInstance() || instruction->IsNewArray() || instruction->IsLoadString()) {
    return true;
  }

  // Check it is safe to move ConstructorFence.
  // (Safe to move ConstructorFence for only protecting the new-instance but not for finals.)
  if (instruction->IsConstructorFence()) {
    HConstructorFence* ctor_fence = instruction->AsConstructorFence();

    // A fence with "0" inputs is dead and should've been removed in a prior pass.
    DCHECK_NE(0u, ctor_fence->InputCount());

    // TODO: this should be simplified to 'return true' since it's
    // potentially pessimizing any code sinking for inlined constructors with final fields.
    // TODO: double check that if the final field assignments are not moved,
    // then the fence is not moved either.

    return ctor_fence->GetAssociatedAllocation() != nullptr;
  }

  // All other instructions that can throw cannot be moved.
  if (instruction->CanThrow()) {
    return false;
  }

  // We can only store on local allocations. Other heap references can
  // be escaping. Note that allocations can escape too, but we only move
  // allocations if their users can move too, or are in the list of
  // post dominated blocks.
  if (instruction->IsInstanceFieldSet()) {
    if (!instruction->InputAt(0)->IsNewInstance()) {
      return false;
    }
  }

  if (instruction->IsArraySet()) {
    if (!instruction->InputAt(0)->IsNewArray()) {
      return false;
    }
  }

  // Heap accesses cannot go past instructions that have memory side effects, which
  // we are not tracking here. Note that the load/store elimination optimization
  // runs before this optimization, and should have removed interesting ones.
  // In theory, we could handle loads of local allocations, but this is currently
  // hard to test, as LSE removes them.
  if (instruction->IsStaticFieldGet() ||
      instruction->IsInstanceFieldGet() ||
      instruction->IsArrayGet()) {
    return false;
  }

  if (instruction->IsInstanceFieldSet() ||
      instruction->IsArraySet() ||
      instruction->CanBeMoved()) {
    return true;
  }
  return false;
}

static void AddInstruction(HInstruction* instruction,
                           const ArenaBitVector& processed_instructions,
                           const ArenaBitVector& discard_blocks,
                           ScopedArenaVector<HInstruction*>* worklist) {
  // Add to the work list if the instruction is not in the list of blocks
  // to discard, hasn't been already processed and is of interest.
  if (!discard_blocks.IsBitSet(instruction->GetBlock()->GetBlockId()) &&
      !processed_instructions.IsBitSet(instruction->GetId()) &&
      IsInterestingInstruction(instruction)) {
    worklist->push_back(instruction);
  }
}

static void AddInputs(HInstruction* instruction,
                      const ArenaBitVector& processed_instructions,
                      const ArenaBitVector& discard_blocks,
                      ScopedArenaVector<HInstruction*>* worklist) {
  for (HInstruction* input : instruction->GetInputs()) {
    AddInstruction(input, processed_instructions, discard_blocks, worklist);
  }
}

static void AddInputs(HBasicBlock* block,
                      const ArenaBitVector& processed_instructions,
                      const ArenaBitVector& discard_blocks,
                      ScopedArenaVector<HInstruction*>* worklist) {
  for (HInstructionIterator it(block->GetPhis()); !it.Done(); it.Advance()) {
    AddInputs(it.Current(), processed_instructions, discard_blocks, worklist);
  }
  for (HInstructionIterator it(block->GetInstructions()); !it.Done(); it.Advance()) {
    AddInputs(it.Current(), processed_instructions, discard_blocks, worklist);
  }
}

static bool ShouldFilterUse(HInstruction* instruction,
                            HInstruction* user,
                            const ArenaBitVector& post_dominated) {
  if (instruction->IsNewInstance()) {
    return (user->IsInstanceFieldSet() || user->IsConstructorFence()) &&
        (user->InputAt(0) == instruction) &&
        !post_dominated.IsBitSet(user->GetBlock()->GetBlockId());
  } else if (instruction->IsNewArray()) {
    return (user->IsArraySet() || user->IsConstructorFence()) &&
        (user->InputAt(0) == instruction) &&
        !post_dominated.IsBitSet(user->GetBlock()->GetBlockId());
  }
  return false;
}

// Find the ideal position for moving `instruction`. If `filter` is true,
// we filter out store instructions to that instruction, which are processed
// first in the step (3) of the sinking algorithm.
// This method is tailored to the sinking algorithm, unlike
// the generic HInstruction::MoveBeforeFirstUserAndOutOfLoops.
static HInstruction* FindIdealPosition(HInstruction* instruction,
                                       const ArenaBitVector& post_dominated,
                                       bool filter = false) {
  DCHECK(!instruction->IsPhi());  // Makes no sense for Phi.

  // Find the target block.
  CommonDominator finder(/* block= */ nullptr);
  for (const HUseListNode<HInstruction*>& use : instruction->GetUses()) {
    HInstruction* user = use.GetUser();
    if (!(filter && ShouldFilterUse(instruction, user, post_dominated))) {
      HBasicBlock* block = user->GetBlock();
      if (user->IsPhi()) {
        // Special case phis by taking the incoming block for regular ones,
        // or the dominator for catch phis.
        block = user->AsPhi()->IsCatchPhi()
            ? block->GetDominator()
            : block->GetPredecessors()[use.GetIndex()];
      }
      finder.Update(block);
    }
  }
  for (const HUseListNode<HEnvironment*>& use : instruction->GetEnvUses()) {
    DCHECK(!use.GetUser()->GetHolder()->IsPhi());
    DCHECK_IMPLIES(filter,
                   !ShouldFilterUse(instruction, use.GetUser()->GetHolder(), post_dominated));
    finder.Update(use.GetUser()->GetHolder()->GetBlock());
  }
  HBasicBlock* target_block = finder.Get();
  if (target_block == nullptr) {
    // No user we can go next to? Likely a LSE or DCE limitation.
    return nullptr;
  }

  // Move to the first dominator not in a loop, if we can. We only do this if we are trying to hoist
  // `instruction` out of a loop it wasn't a part of.
  const HLoopInformation* loop_info = instruction->GetBlock()->GetLoopInformation();
  while (target_block->IsInLoop() && target_block->GetLoopInformation() != loop_info) {
    if (!post_dominated.IsBitSet(target_block->GetDominator()->GetBlockId())) {
      break;
    }
    target_block = target_block->GetDominator();
    DCHECK(target_block != nullptr);
  }

  if (instruction->CanThrow()) {
    // Consistency check: We shouldn't land in a loop if we weren't in one before traversing up the
    // dominator tree regarding try catches.
    const bool was_in_loop = target_block->IsInLoop();

    // We cannot move an instruction that can throw into a try that said instruction is not a part
    // of already, as that would mean it will throw into a different catch block. In short, for
    // throwing instructions:
    // * If the throwing instruction is part of a try, they should only be sunk into that same try.
    // * If the throwing instruction is not part of any try, they shouldn't be sunk to any try.
    if (instruction->GetBlock()->IsTryBlock()) {
      const HTryBoundary& try_entry =
          instruction->GetBlock()->GetTryCatchInformation()->GetTryEntry();
      while (!(target_block->IsTryBlock() &&
               try_entry.HasSameExceptionHandlersAs(
                   target_block->GetTryCatchInformation()->GetTryEntry()))) {
        target_block = target_block->GetDominator();
        if (!post_dominated.IsBitSet(target_block->GetBlockId())) {
          // We couldn't find a suitable block.
          return nullptr;
        }
      }
    } else {
      // Search for the first block also not in a try block
      while (target_block->IsTryBlock()) {
        target_block = target_block->GetDominator();
        if (!post_dominated.IsBitSet(target_block->GetBlockId())) {
          // We couldn't find a suitable block.
          return nullptr;
        }
      }
    }

    DCHECK_IMPLIES(target_block->IsInLoop(), was_in_loop);
  }

  // Find insertion position. No need to filter anymore, as we have found a
  // target block.
  HInstruction* insert_pos = nullptr;
  for (const HUseListNode<HInstruction*>& use : instruction->GetUses()) {
    if (use.GetUser()->GetBlock() == target_block &&
        (insert_pos == nullptr || use.GetUser()->StrictlyDominates(insert_pos))) {
      insert_pos = use.GetUser();
    }
  }
  for (const HUseListNode<HEnvironment*>& use : instruction->GetEnvUses()) {
    HEnvironment* env = use.GetUser();
    HInstruction* user = env->GetHolder();
    if (user->GetBlock() == target_block &&
        (insert_pos == nullptr || user->StrictlyDominates(insert_pos))) {
      if (target_block->IsCatchBlock() && target_block->GetFirstInstruction() == user) {
        // We can sink the instructions past the environment setting Nop. If we do that, we have to
        // remove said instruction from the environment. Since we know that we will be sinking the
        // instruction to this block and there are no more instructions to consider, we can safely
        // remove it from the environment now.
        DCHECK(target_block->GetFirstInstruction()->IsNop());
        env->RemoveAsUserOfInput(use.GetIndex());
        env->SetRawEnvAt(use.GetIndex(), /*instruction=*/ nullptr);
      } else {
        insert_pos = user;
      }
    }
  }
  if (insert_pos == nullptr) {
    // No user in `target_block`, insert before the control flow instruction.
    insert_pos = target_block->GetLastInstruction();
    DCHECK(insert_pos->IsControlFlow());
    // Avoid splitting HCondition from HIf to prevent unnecessary materialization.
    if (insert_pos->IsIf()) {
      HInstruction* if_input = insert_pos->AsIf()->InputAt(0);
      if (if_input == insert_pos->GetPrevious()) {
        insert_pos = if_input;
      }
    }
  }
  DCHECK(!insert_pos->IsPhi());
  return insert_pos;
}


void CodeSinking::SinkCodeToUncommonBranch(HBasicBlock* end_block) {
  // Local allocator to discard data structures created below at the end of this optimization.
  ScopedArenaAllocator allocator(graph_->GetArenaStack());

  size_t number_of_instructions = graph_->GetCurrentInstructionId();
  ScopedArenaVector<HInstruction*> worklist(allocator.Adapter(kArenaAllocMisc));
  ArenaBitVector processed_instructions(&allocator, number_of_instructions, /* expandable= */ false);
  processed_instructions.ClearAllBits();
  ArenaBitVector post_dominated(&allocator, graph_->GetBlocks().size(), /* expandable= */ false);
  post_dominated.ClearAllBits();

  // Step (1): Visit post order to get a subset of blocks post dominated by `end_block`.
  // TODO(ngeoffray): Getting the full set of post-dominated should be done by
  // computing the post dominator tree, but that could be too time consuming. Also,
  // we should start the analysis from blocks dominated by an uncommon branch, but we
  // don't profile branches yet.
  bool found_block = false;
  for (HBasicBlock* block : graph_->GetPostOrder()) {
    if (block == end_block) {
      found_block = true;
      post_dominated.SetBit(block->GetBlockId());
    } else if (found_block) {
      bool is_post_dominated = true;
      DCHECK_NE(block, graph_->GetExitBlock())
          << "We shouldn't encounter the exit block after `end_block`.";

      // BasicBlock that are try entries look like this:
      //   BasicBlock i:
      //     instr 1
      //     ...
      //     instr N
      //     TryBoundary kind:entry ---Try begins here---
      //
      // Due to how our BasicBlocks are structured, BasicBlock i will have an xhandler successor
      // since we are starting a try. If we use `GetSuccessors` for this case, we will check if
      // the catch block is post_dominated.
      //
      // However, this catch block doesn't matter: when we sink the instruction into that
      // BasicBlock i, we do it before the TryBoundary (i.e. outside of the try and outside the
      // catch's domain). We can ignore catch blocks using `GetNormalSuccessors` to sink code
      // right before the start of a try block.
      //
      // On the other side of the coin, BasicBlock that are try exits look like this:
      //   BasicBlock j:
      //     instr 1
      //     ...
      //     instr N
      //     TryBoundary kind:exit ---Try ends here---
      //
      // If we sink to these basic blocks we would be sinking inside of the try so we would like
      // to check the catch block for post dominance.
      const bool ends_with_try_boundary_entry =
          block->EndsWithTryBoundary() && block->GetLastInstruction()->AsTryBoundary()->IsEntry();
      ArrayRef<HBasicBlock* const> successors =
          ends_with_try_boundary_entry ? block->GetNormalSuccessors() :
                                         ArrayRef<HBasicBlock* const>(block->GetSuccessors());
      for (HBasicBlock* successor : successors) {
        if (!post_dominated.IsBitSet(successor->GetBlockId())) {
          is_post_dominated = false;
          break;
        }
      }
      if (is_post_dominated) {
        post_dominated.SetBit(block->GetBlockId());
      }
    }
  }

  // Now that we have found a subset of post-dominated blocks, add to the worklist all inputs
  // of instructions in these blocks that are not themselves in these blocks.
  // Also find the common dominator of the found post dominated blocks, to help filtering
  // out un-movable uses in step (2).
  CommonDominator finder(end_block);
  for (size_t i = 0, e = graph_->GetBlocks().size(); i < e; ++i) {
    if (post_dominated.IsBitSet(i)) {
      finder.Update(graph_->GetBlocks()[i]);
      AddInputs(graph_->GetBlocks()[i], processed_instructions, post_dominated, &worklist);
    }
  }
  HBasicBlock* common_dominator = finder.Get();

  // Step (2): iterate over the worklist to find sinking candidates.
  ArenaBitVector instructions_that_can_move(
      &allocator, number_of_instructions, /* expandable= */ false);
  instructions_that_can_move.ClearAllBits();
  ScopedArenaVector<ScopedArenaVector<HInstruction*>> instructions_to_move(
      graph_->GetBlocks().size(),
      ScopedArenaVector<HInstruction*>(allocator.Adapter(kArenaAllocMisc)),
      allocator.Adapter(kArenaAllocMisc));
  while (!worklist.empty()) {
    HInstruction* instruction = worklist.back();
    if (processed_instructions.IsBitSet(instruction->GetId())) {
      // The instruction has already been processed, continue. This happens
      // when the instruction is the input/user of multiple instructions.
      worklist.pop_back();
      continue;
    }
    bool all_users_in_post_dominated_blocks = true;
    bool can_move = true;
    // Check users of the instruction.
    for (const HUseListNode<HInstruction*>& use : instruction->GetUses()) {
      HInstruction* user = use.GetUser();
      if (!post_dominated.IsBitSet(user->GetBlock()->GetBlockId()) &&
          !instructions_that_can_move.IsBitSet(user->GetId())) {
        all_users_in_post_dominated_blocks = false;
        // If we've already processed this user, or the user cannot be moved, or
        // is not dominating the post dominated blocks, bail.
        // TODO(ngeoffray): The domination check is an approximation. We should
        // instead check if the dominated blocks post dominate the user's block,
        // but we do not have post dominance information here.
        if (processed_instructions.IsBitSet(user->GetId()) ||
            !IsInterestingInstruction(user) ||
            !user->GetBlock()->Dominates(common_dominator)) {
          can_move = false;
          break;
        }
      }
    }

    // Check environment users of the instruction. Some of these users require
    // the instruction not to move.
    if (all_users_in_post_dominated_blocks) {
      for (const HUseListNode<HEnvironment*>& use : instruction->GetEnvUses()) {
        HEnvironment* environment = use.GetUser();
        HInstruction* user = environment->GetHolder();
        if (!post_dominated.IsBitSet(user->GetBlock()->GetBlockId())) {
          if (graph_->IsDebuggable() ||
              user->IsDeoptimize() ||
              user->CanThrowIntoCatchBlock() ||
              (user->IsSuspendCheck() && graph_->IsCompilingOsr())) {
            can_move = false;
            break;
          }
        }
      }
    }
    if (!can_move) {
      // Instruction cannot be moved, mark it as processed and remove it from the work
      // list.
      processed_instructions.SetBit(instruction->GetId());
      worklist.pop_back();
    } else if (all_users_in_post_dominated_blocks) {
      // Instruction is a candidate for being sunk. Mark it as such, remove it from the
      // work list, and add its inputs to the work list.
      instructions_that_can_move.SetBit(instruction->GetId());
      instructions_to_move[instruction->GetBlock()->GetBlockId()].push_back(instruction);
      processed_instructions.SetBit(instruction->GetId());
      worklist.pop_back();
      AddInputs(instruction, processed_instructions, post_dominated, &worklist);
      // Drop the environment use not in the list of post-dominated block. This is
      // to help step (3) of this optimization, when we start moving instructions
      // closer to their use.
      for (const HUseListNode<HEnvironment*>& use : instruction->GetEnvUses()) {
        HEnvironment* environment = use.GetUser();
        HInstruction* user = environment->GetHolder();
        if (!post_dominated.IsBitSet(user->GetBlock()->GetBlockId())) {
          environment->RemoveAsUserOfInput(use.GetIndex());
          environment->SetRawEnvAt(use.GetIndex(), nullptr);
        }
      }
    } else {
      // The information we have on the users was not enough to decide whether the
      // instruction could be moved.
      // Add the users to the work list, and keep the instruction in the work list
      // to process it again once all users have been processed.
      for (const HUseListNode<HInstruction*>& use : instruction->GetUses()) {
        AddInstruction(use.GetUser(), processed_instructions, post_dominated, &worklist);
      }
    }
  }

  // We want to process the instructions in reverse dominated order. This is required for heap
  // stores. To guarantee this (including the transitivity of incomparability) we have some extra
  // bookkeeping.
  ScopedArenaVector<HInstruction*> instructions_to_move_sorted(allocator.Adapter(kArenaAllocMisc));
  for (HBasicBlock* block : graph_->GetPostOrder()) {
    const int block_id = block->GetBlockId();

    // Order the block itself first.
    std::sort(instructions_to_move[block_id].begin(),
              instructions_to_move[block_id].end(),
              [&block](HInstruction* a, HInstruction* b) {
                return block->GetInstructions().FoundBefore(b, a);
              });

    for (HInstruction* instruction : instructions_to_move[block_id]) {
      instructions_to_move_sorted.push_back(instruction);
    }
  }

  if (kIsDebugBuild) {
    // We should have ordered the instructions in reverse dominated order. This means that
    // instructions shouldn't dominate instructions that come after it in the vector.
    for (size_t i = 0; i < instructions_to_move_sorted.size(); ++i) {
      for (size_t j = i + 1; j < instructions_to_move_sorted.size(); ++j) {
        if (instructions_to_move_sorted[i]->StrictlyDominates(instructions_to_move_sorted[j])) {
          std::stringstream ss;
          graph_->Dump(ss, nullptr);
          ss << "\n"
             << "{";
          for (HInstruction* instr : instructions_to_move_sorted) {
            ss << *instr << " in block: " << instr->GetBlock() << ", ";
          }
          ss << "}\n";
          ss << "i = " << i << " which is " << *instructions_to_move_sorted[i]
             << "strictly dominates j = " << j << " which is " << *instructions_to_move_sorted[j]
             << "\n";
          LOG(FATAL) << "Unexpected ordering of code sinking instructions: " << ss.str();
        }
      }
    }
  }

  // Step (3): Try to move sinking candidates.
  for (HInstruction* instruction : instructions_to_move_sorted) {
    HInstruction* position = nullptr;
    if (instruction->IsArraySet()
            || instruction->IsInstanceFieldSet()
            || instruction->IsConstructorFence()) {
      if (!instructions_that_can_move.IsBitSet(instruction->InputAt(0)->GetId())) {
        // A store can trivially move, but it can safely do so only if the heap
        // location it stores to can also move.
        // TODO(ngeoffray): Handle allocation/store cycles by pruning these instructions
        // from the set and all their inputs.
        continue;
      }
      // Find the position of the instruction we're storing into, filtering out this
      // store and all other stores to that instruction.
      position = FindIdealPosition(instruction->InputAt(0), post_dominated, /* filter= */ true);

      // The position needs to be dominated by the store, in order for the store to move there.
      if (position == nullptr || !instruction->GetBlock()->Dominates(position->GetBlock())) {
        continue;
      }
    } else {
      // Find the ideal position within the post dominated blocks.
      position = FindIdealPosition(instruction, post_dominated);
      if (position == nullptr) {
        continue;
      }
    }
    // Bail if we could not find a position in the post dominated blocks (for example,
    // if there are multiple users whose common dominator is not in the list of
    // post dominated blocks).
    if (!post_dominated.IsBitSet(position->GetBlock()->GetBlockId())) {
      continue;
    }
    MaybeRecordStat(stats_, MethodCompilationStat::kInstructionSunk);
    instruction->MoveBefore(position, /* do_checks= */ false);
  }
}

void CodeSinking::ReturnSinking() {
  HBasicBlock* exit = graph_->GetExitBlock();
  DCHECK(exit != nullptr);

  int number_of_returns = 0;
  bool saw_return = false;
  for (HBasicBlock* pred : exit->GetPredecessors()) {
    // TODO(solanes): We might have Return/ReturnVoid->TryBoundary->Exit. We can theoretically
    // handle them and move them out of the TryBoundary. However, it is a border case and it adds
    // codebase complexity.
    if (pred->GetLastInstruction()->IsReturn() || pred->GetLastInstruction()->IsReturnVoid()) {
      saw_return |= pred->GetLastInstruction()->IsReturn();
      ++number_of_returns;
    }
  }

  if (number_of_returns < 2) {
    // Nothing to do.
    return;
  }

  // `new_block` will coalesce the Return instructions into Phi+Return, or the ReturnVoid
  // instructions into a ReturnVoid.
  HBasicBlock* new_block = new (graph_->GetAllocator()) HBasicBlock(graph_, exit->GetDexPc());
  if (saw_return) {
    HPhi* new_phi = nullptr;
    for (size_t i = 0; i < exit->GetPredecessors().size(); /*++i in loop*/) {
      HBasicBlock* pred = exit->GetPredecessors()[i];
      if (!pred->GetLastInstruction()->IsReturn()) {
        ++i;
        continue;
      }

      HReturn* ret = pred->GetLastInstruction()->AsReturn();
      if (new_phi == nullptr) {
        // Create the new_phi, if we haven't done so yet. We do it here since we need to know the
        // type to assign to it.
        new_phi = new (graph_->GetAllocator()) HPhi(graph_->GetAllocator(),
                                                    kNoRegNumber,
                                                    /*number_of_inputs=*/0,
                                                    ret->InputAt(0)->GetType());
        new_block->AddPhi(new_phi);
      }
      new_phi->AddInput(ret->InputAt(0));
      pred->ReplaceAndRemoveInstructionWith(ret,
                                            new (graph_->GetAllocator()) HGoto(ret->GetDexPc()));
      pred->ReplaceSuccessor(exit, new_block);
      // Since we are removing a predecessor, there's no need to increment `i`.
    }
    new_block->AddInstruction(new (graph_->GetAllocator()) HReturn(new_phi, exit->GetDexPc()));
  } else {
    for (size_t i = 0; i < exit->GetPredecessors().size(); /*++i in loop*/) {
      HBasicBlock* pred = exit->GetPredecessors()[i];
      if (!pred->GetLastInstruction()->IsReturnVoid()) {
        ++i;
        continue;
      }

      HReturnVoid* ret = pred->GetLastInstruction()->AsReturnVoid();
      pred->ReplaceAndRemoveInstructionWith(ret,
                                            new (graph_->GetAllocator()) HGoto(ret->GetDexPc()));
      pred->ReplaceSuccessor(exit, new_block);
      // Since we are removing a predecessor, there's no need to increment `i`.
    }
    new_block->AddInstruction(new (graph_->GetAllocator()) HReturnVoid(exit->GetDexPc()));
  }

  new_block->AddSuccessor(exit);
  graph_->AddBlock(new_block);

  // Recompute dominance since we added a new block.
  graph_->ClearDominanceInformation();
  graph_->ComputeDominanceInformation();
}

}  // namespace art
