/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "berberis/backend/x86_64/machine_ir_analysis.h"

#include <algorithm>

#include "berberis/backend/common/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/base/algorithm.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/base/arena_vector.h"
#include "berberis/base/logging.h"

namespace berberis::x86_64 {

namespace {

class LoopBuilder {
 public:
  LoopBuilder(MachineIR* ir, Loop* loop, MachineBasicBlock* loop_head)
      : loop_(loop), is_bb_in_loop_(ir->NumBasicBlocks(), false, ir->arena()) {
    CHECK_EQ(loop_->size(), 0u);
    loop_->reserve(ir->NumBasicBlocks());
    loop_->push_back(loop_head);
    is_bb_in_loop_[loop_head->id()] = true;
  }

  // Appends bb to loop (bb-vector) unless bb is already in loop.
  // Returns whether bb is appended.
  bool PushBackIfNotInLoop(MachineBasicBlock* bb) {
    if (is_bb_in_loop_[bb->id()]) {
      return false;
    }
    loop_->push_back(bb);
    is_bb_in_loop_[bb->id()] = true;
    return true;
  }

 private:
  Loop* loop_;
  ArenaVector<bool> is_bb_in_loop_;
};

void PostOrderTraverseBBListRecursive(MachineBasicBlock* bb,
                                      ArenaVector<bool>& is_visited,
                                      MachineBasicBlockList& result) {
  is_visited[bb->id()] = true;
  for (auto* edge : bb->out_edges()) {
    auto* dst = edge->dst();
    if (!is_visited[dst->id()]) {
      PostOrderTraverseBBListRecursive(dst, is_visited, result);
    }
  }
  // We push to front so that the post order list is automatically reversed.
  result.push_front(bb);
}

bool CompareBackEdges(const MachineEdge* left, const MachineEdge* right) {
  return left->dst()->id() < right->dst()->id();
}

Loop* CollectLoop(MachineIR* ir, const MachineEdgeVector& back_edges, size_t begin, size_t end) {
  Arena* arena = ir->arena();
  auto* loop = NewInArena<Loop>(arena, arena);
  auto* head_bb = back_edges[begin]->dst();

  LoopBuilder builder(ir, loop, head_bb);

  for (size_t edge_no = begin; edge_no < end; ++edge_no) {
    auto* back_branch_bb = back_edges[edge_no]->src();
    // All back-edges must be to the same head.
    CHECK_EQ(back_edges[edge_no]->dst(), head_bb);

    if (!builder.PushBackIfNotInLoop(back_branch_bb)) {
      // We have already processed this basic-block (and consequently
      // all its predecessors) while processing another back-edge.
      continue;
    }

    // Go from back-branching bb to (tentatively) dominating
    // loop head collecting all passed bbs.
    for (size_t bb_no = loop->size() - 1; bb_no < loop->size(); ++bb_no) {
      auto* bb = loop->at(bb_no);

      if (bb->in_edges().size() == 0) {
        // Reached start-bb: head doesn't dominate back_branch_bb.
        // Loop is irreducible - ignore it.
        return nullptr;
      }

      for (auto in_edge : bb->in_edges()) {
        builder.PushBackIfNotInLoop(in_edge->src());
      }
    }  // Walk new loop-bbs
  }    // Walk back
  return loop;
}

}  // namespace

MachineBasicBlockList GetReversePostOrderBBList(MachineIR* ir) {
  if (ir->bb_order() == MachineIR::BasicBlockOrder::kReversePostOrder) {
    return ir->bb_list();
  }
  MachineBasicBlock* entry_bb = ir->bb_list().front();
  CHECK_EQ(entry_bb->in_edges().size(), 0);

  ArenaVector<bool> is_visited(ir->NumBasicBlocks(), false, ir->arena());
  MachineBasicBlockList rpo_list(ir->arena());
  PostOrderTraverseBBListRecursive(entry_bb, is_visited, rpo_list);
  return rpo_list;
}

LoopVector FindLoops(MachineIR* ir) {
  Arena* arena = ir->arena();
  ArenaVector<bool> is_visited(ir->NumBasicBlocks(), false, arena);
  LoopVector loops_vector(arena);

  const size_t kMaxBackEdgesExpected = 16;
  loops_vector.reserve(kMaxBackEdgesExpected);

  ArenaVector<MachineEdge*> back_edges(arena);
  back_edges.reserve(kMaxBackEdgesExpected);

  // Collects back-edges.
  // Traversal relies on the reverse post order of basic-blocks.
  for (auto* bb : GetReversePostOrderBBList(ir)) {
    is_visited[bb->id()] = true;

    for (auto* edge : bb->out_edges()) {
      MachineBasicBlock* succ_bb = edge->dst();

      if (is_visited[succ_bb->id()]) {
        back_edges.push_back(edge);
      }
    }  // Walk bb-succs
  }    // Walk basic-blocks

  // Pull back-edges with the same target (loop head) together.
  std::sort(back_edges.begin(), back_edges.end(), CompareBackEdges);

  // Guard which makes the following loop-body simpler.
  auto empty_edge = MachineEdge(arena, nullptr, nullptr);
  back_edges.push_back(&empty_edge);

  size_t begin_edge_no = 0;
  // Collect loops for back-edges with the same target.
  for (size_t edge_no = 1; edge_no < back_edges.size(); ++edge_no) {
    if (back_edges[begin_edge_no]->dst() == back_edges[edge_no]->dst()) {
      continue;
    }
    // Encountered new head - collect loop for the previous one.
    // Guard (being the last) doesn't require loop collection.
    auto* loop = CollectLoop(ir, back_edges, begin_edge_no, edge_no);
    if (loop) {
      loops_vector.push_back(loop);
    }
    begin_edge_no = edge_no;
  }
  return loops_vector;
}

bool LoopTree::TryInsertLoopAtNode(LoopTreeNode* node, Loop* loop) {
  if (node->loop() != nullptr && !Contains(*node->loop(), loop->at(0))) {
    return false;
  }

  for (size_t i = 0; i < node->NumInnerloops(); i++) {
    auto* innerloop_node = node->GetInnerloopNode(i);
    if (TryInsertLoopAtNode(innerloop_node, loop)) {
      return true;
    }
  }

  LoopTreeNode* innerloop_node = NewInArena<LoopTreeNode>(ir_->arena(), ir_, loop);
  node->AddInnerloopNode(innerloop_node);
  return true;
}

LoopTree BuildLoopTree(MachineIR* ir) {
  auto loops = FindLoops(ir);
  std::sort(loops.begin(), loops.end(), [](auto* loop1, auto* loop2) {
    return loop1->size() > loop2->size();
  });

  LoopTree loop_tree(ir);
  for (auto* loop : loops) {
    loop_tree.InsertLoop(loop);
  }

  return loop_tree;
}

}  // namespace berberis::x86_64
