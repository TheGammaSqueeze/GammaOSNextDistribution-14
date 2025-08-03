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

#ifndef BERBERIS_BASE_MMAP_POOL_H_
#define BERBERIS_BASE_MMAP_POOL_H_

#include <array>
#include <cstddef>

#include "berberis/base/checks.h"
#include "berberis/base/lock_free_stack.h"
#include "berberis/base/mmap.h"
#include "berberis/base/page_size.h"

namespace berberis {

// Pool of memory mappings to be used by berberis runtime.
// Thread-safe, signal-safe, reentrant.
// Singleton interface (no non-static members).
template <size_t kBlockSize, size_t kSizeLimit>
class MmapPool {
  static_assert(kBlockSize % kMaxPageSize == 0);
  static_assert(kBlockSize <= kSizeLimit);

 public:
  static void* Alloc() {
    Node* node = g_nodes_with_available_blocks_.Pop();
    if (!node) {
      return MmapOrDie(kBlockSize);
    }
    // Memorize the block before releasing the node since it may be immediately overwritten.
    void* block = node->block;
    g_nodes_without_blocks_.Push(node);
    return block;
  }

  static void Free(void* block) {
    Node* node = g_nodes_without_blocks_.Pop();
    if (!node) {
      return MunmapOrDie(block, kBlockSize);
    }
    node->block = block;
    g_nodes_with_available_blocks_.Push(node);
  }

 private:
  struct Node {
    Node* next;
    void* block;
  };

  static_assert(kSizeLimit % kBlockSize == 0);
  using NodesArray = std::array<Node, kSizeLimit / kBlockSize>;

  // Helper wrapper to add a constructor from std::array which can be used for
  // static member initialization.
  class FreeNodes : public LockFreeStack<Node> {
   public:
    explicit FreeNodes(NodesArray& nodes_arr) {
      for (auto& node : nodes_arr) {
        LockFreeStack<Node>::Push(&node);
      }
    }
  };

  // Attention: we cannot use blocks as nodes since a thread may unmap block while another thread
  // still tries to dereference (node->next) it inside LockFreeStack. So instead we use permanent
  // array of nodes.
  static NodesArray g_nodes_;
  static LockFreeStack<Node> g_nodes_with_available_blocks_;
  static FreeNodes g_nodes_without_blocks_;

  MmapPool() = delete;

  friend class MmapPoolTest;
};

template <size_t kBlockSize, size_t kSizeLimit>
std::array<typename MmapPool<kBlockSize, kSizeLimit>::Node, kSizeLimit / kBlockSize>
    MmapPool<kBlockSize, kSizeLimit>::g_nodes_;

template <size_t kBlockSize, size_t kSizeLimit>
LockFreeStack<typename MmapPool<kBlockSize, kSizeLimit>::Node>
    MmapPool<kBlockSize, kSizeLimit>::g_nodes_with_available_blocks_;

template <size_t kBlockSize, size_t kSizeLimit>
typename MmapPool<kBlockSize, kSizeLimit>::FreeNodes
    MmapPool<kBlockSize, kSizeLimit>::g_nodes_without_blocks_(
        MmapPool<kBlockSize, kSizeLimit>::g_nodes_);

}  // namespace berberis

#endif  // BERBERIS_BASE_MMAP_POOL_H_
