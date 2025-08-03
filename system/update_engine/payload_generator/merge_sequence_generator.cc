//
// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include "update_engine/payload_generator/merge_sequence_generator.h"

#include <algorithm>
#include <limits>

#include "update_engine/payload_generator/extent_ranges.h"
#include "update_engine/payload_generator/extent_utils.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {

CowMergeOperation CreateCowMergeOperation(const Extent& src_extent,
                                          const Extent& dst_extent,
                                          CowMergeOperation::Type op_type,
                                          uint32_t src_offset) {
  CowMergeOperation ret;
  ret.set_type(op_type);
  *ret.mutable_src_extent() = src_extent;
  *ret.mutable_dst_extent() = dst_extent;
  ret.set_src_offset(src_offset);
  return ret;
}

std::ostream& operator<<(std::ostream& os,
                         const CowMergeOperation& merge_operation) {
  os << "CowMergeOperation src extent: "
     << ExtentsToString({merge_operation.src_extent()})
     << ", dst extent: " << ExtentsToString({merge_operation.dst_extent()});
  if (merge_operation.has_src_offset()) {
    os << ", src offset: " << merge_operation.src_offset();
  }
  os << " op_type: ";
  if (merge_operation.type() == CowMergeOperation::COW_COPY) {
    os << "COW_COPY";
  } else if (merge_operation.type() == CowMergeOperation::COW_XOR) {
    os << "COW_XOR";
  } else {
    os << merge_operation.type();
  }
  return os;
}

// The OTA generation guarantees that all blocks in the dst extent will be
// written only once. So we can use it to order the CowMergeOperation.
bool operator<(const CowMergeOperation& op1, const CowMergeOperation& op2) {
  return op1.dst_extent().start_block() < op2.dst_extent().start_block();
}

bool operator==(const CowMergeOperation& op1, const CowMergeOperation& op2) {
  return op1.type() == op2.type() && op1.src_extent() == op2.src_extent() &&
         op1.dst_extent() == op2.dst_extent();
}

template <typename T>
constexpr T GetDifference(T first, T second) {
  T abs_diff = (first > second) ? (first - second) : (second - first);
  return abs_diff;
}

CowMergeOperation::Type GetCowOpType(InstallOperation::Type install_op_type) {
  switch (install_op_type) {
    case InstallOperation::SOURCE_COPY:
      return CowMergeOperation::COW_COPY;
    case InstallOperation::SOURCE_BSDIFF:
    case InstallOperation::BROTLI_BSDIFF:
    case InstallOperation::PUFFDIFF:
      return CowMergeOperation::COW_XOR;
    default:
      CHECK(false) << "Unknown install op type: " << install_op_type;
      return CowMergeOperation::COW_REPLACE;
  }
}

void SplitSelfOverlapping(const Extent& src_extent,
                          const Extent& dst_extent,
                          std::vector<CowMergeOperation>* sequence) {
  CHECK_EQ(src_extent.num_blocks(), dst_extent.num_blocks());
  if (src_extent.start_block() == dst_extent.start_block()) {
    sequence->emplace_back(CreateCowMergeOperation(
        src_extent, dst_extent, CowMergeOperation::COW_COPY));
    return;
  }

  const size_t diff =
      GetDifference(src_extent.start_block(), dst_extent.start_block());
  for (size_t i = 0; i < src_extent.num_blocks(); i += diff) {
    auto num_blocks = std::min<size_t>(diff, src_extent.num_blocks() - i);
    sequence->emplace_back(CreateCowMergeOperation(
        ExtentForRange(i + src_extent.start_block(), num_blocks),
        ExtentForRange(i + dst_extent.start_block(), num_blocks),
        CowMergeOperation::COW_COPY));
  }
}

static bool ProcessXorOps(std::vector<CowMergeOperation>* sequence,
                          const AnnotatedOperation& aop) {
  const auto size_before = sequence->size();
  sequence->insert(sequence->end(), aop.xor_ops.begin(), aop.xor_ops.end());
  std::for_each(
      sequence->begin() + size_before,
      sequence->end(),
      [](CowMergeOperation& op) {
        CHECK_EQ(op.type(), CowMergeOperation::COW_XOR);
        // If |src_offset| is greater than 0, then we are reading 1
        // extra block at the end of src_extent. This dependency must
        // be honored during merge sequence generation, or we can end
        // up with a corrupted device after merge.
        if (op.src_offset() > 0) {
          if (op.src_extent().num_blocks() == op.dst_extent().num_blocks()) {
            op.mutable_src_extent()->set_num_blocks(
                op.src_extent().num_blocks() + 1);
          }
          CHECK_EQ(op.src_extent().num_blocks(),
                   op.dst_extent().num_blocks() + 1);
        }
        CHECK_NE(op.src_extent().start_block(),
                 std::numeric_limits<uint64_t>::max());
      });
  return true;
}

static bool ProcessCopyOps(std::vector<CowMergeOperation>* sequence,
                           const AnnotatedOperation& aop) {
  CHECK_EQ(GetCowOpType(aop.op.type()), CowMergeOperation::COW_COPY);
  if (aop.op.dst_extents().size() != 1) {
    std::vector<Extent> out_extents;
    ExtentsToVector(aop.op.dst_extents(), &out_extents);
    LOG(ERROR)
        << "The dst extents for source_copy are expected to be contiguous,"
        << " dst extents: " << ExtentsToString(out_extents);
    return false;
  }
  // Split the source extents.
  size_t used_blocks = 0;
  for (const auto& src_extent : aop.op.src_extents()) {
    // The dst_extent in the merge sequence will be a subset of
    // InstallOperation's dst_extent. This will simplify the OTA -> COW
    // conversion when we install the payload.
    Extent dst_extent =
        ExtentForRange(aop.op.dst_extents(0).start_block() + used_blocks,
                       src_extent.num_blocks());
    // Self-overlapping operation, must split into multiple non
    // self-overlapping ops
    if (ExtentRanges::ExtentsOverlap(src_extent, dst_extent)) {
      SplitSelfOverlapping(src_extent, dst_extent, sequence);
    } else {
      sequence->emplace_back(CreateCowMergeOperation(
          src_extent, dst_extent, CowMergeOperation::COW_COPY));
    }
    used_blocks += src_extent.num_blocks();
  }

  if (used_blocks != aop.op.dst_extents(0).num_blocks()) {
    LOG(ERROR) << "Number of blocks in src extents doesn't equal to the"
               << " ones in the dst extents, src blocks " << used_blocks
               << ", dst blocks " << aop.op.dst_extents(0).num_blocks();
    return false;
  }
  return true;
}

std::unique_ptr<MergeSequenceGenerator> MergeSequenceGenerator::Create(
    const std::vector<AnnotatedOperation>& aops,
    std::string_view partition_name) {
  std::vector<CowMergeOperation> sequence;

  for (const auto& aop : aops) {
    if (aop.op.type() == InstallOperation::SOURCE_COPY) {
      if (!ProcessCopyOps(&sequence, aop)) {
        return nullptr;
      }
    } else if (!aop.xor_ops.empty()) {
      if (!ProcessXorOps(&sequence, aop)) {
        return nullptr;
      }
    }
  }

  return std::unique_ptr<MergeSequenceGenerator>(
      new MergeSequenceGenerator(sequence, partition_name));
}

template <typename T>
CowMergeOperation MaxOutDegree(
    const T& nodes,
    const std::map<CowMergeOperation, std::set<CowMergeOperation>>&
        merge_after) {
  // Rationale for this algorithm:
  // We only need to remove nodes from the graph if the graph contains a cycle.
  // Any graph of N nodes has cycle iff number of edges >= N.
  // So, to restore the graph back to an acyclic state, we need to keep removing
  // edges until we have <N edges left. To minimize the number of nodes removed,
  // we always remove the node with maximum out degree.
  CowMergeOperation best;
  size_t max_out_degree = 0;
  const auto has_xor =
      std::any_of(nodes.begin(),
                  nodes.end(),
                  [&merge_after](const CowMergeOperation& node) {
                    if (node.type() != CowMergeOperation::COW_XOR) {
                      return false;
                    }
                    auto it = merge_after.find(node);
                    if (it == merge_after.end()) {
                      return false;
                    }
                    return it->second.size() > 0;
                  });
  for (const auto& op : nodes) {
    if (has_xor && op.type() != CowMergeOperation::COW_XOR) {
      continue;
    }
    const auto out_degree = merge_after.at(op).size();
    if (out_degree > max_out_degree) {
      best = op;
      max_out_degree = out_degree;
    } else if (out_degree == max_out_degree) {
      if (op.src_extent().num_blocks() < best.src_extent().num_blocks()) {
        best = op;
      }
    }
  }
  CHECK_NE(max_out_degree, 0UL);
  return best;
}

template <typename T>
struct MapKeyIterator {
  MapKeyIterator<T>& operator++() {
    ++it;
    return *this;
  }
  MapKeyIterator<T>& operator--() {
    --it;
    return *this;
  }
  bool operator==(const MapKeyIterator<T>& rhs) const { return it == rhs.it; }
  bool operator!=(const MapKeyIterator<T>& rhs) const { return it != rhs.it; }
  auto&& operator->() const { return it->first; }
  auto&& operator*() const { return it->first; }
  T it;
};

template <typename T, typename U>
struct MapKeyRange {
  auto begin() const {
    return MapKeyIterator<typename std::map<T, U>::const_iterator>{map.begin()};
  }
  auto end() const {
    return MapKeyIterator<typename std::map<T, U>::const_iterator>{map.end()};
  }
  std::map<T, U> map;
};

CowMergeOperation MaxOutDegree(
    const std::map<CowMergeOperation, int>& incoming_edges,
    const std::map<CowMergeOperation, std::set<CowMergeOperation>>&
        merge_after) {
  return MaxOutDegree(MapKeyRange<CowMergeOperation, int>{incoming_edges},
                      merge_after);
}

// Given a potentially cyclic graph, return a node to remove to break cycles
// |incoming_edges| stores nodes' in degree, and |merge_after| is an outgoing
// edge list. For example, |merge_after[a]| returns all nodes which `a` has an
// out going edge to.
// The only requirement of this function is to return a node which is in
// |incoming_edges| . As long as this is satisfied, merge sequence generation
// will work. Caller will keep removing nodes returned by this function until
// the graph has no cycles. However, the choice of which node to remove can
// greatly impact COW sizes. Nodes removed from the graph will be converted to a
// COW_REPLACE operation, taking more disk space. So this function should try to
// pick a node which minimizes number of nodes we have to remove. (Modulo the
// weight of each node, which is how many blocks a CowMergeOperation touches)
CowMergeOperation PickConvertToRaw(
    const std::map<CowMergeOperation, int>& incoming_edges,
    const std::map<CowMergeOperation, std::set<CowMergeOperation>>&
        merge_after) {
  return MaxOutDegree(incoming_edges, merge_after);
}

std::map<CowMergeOperation, std::set<CowMergeOperation>>
MergeSequenceGenerator::FindDependency(
    const std::vector<CowMergeOperation>& operations) {
  LOG(INFO) << "Finding dependencies";

  // Since the OTA operation may reuse some source blocks, use the binary
  // search on sorted dst extents to find overlaps.
  std::map<CowMergeOperation, std::set<CowMergeOperation>> merge_after;
  for (const auto& op : operations) {
    // lower bound (inclusive): dst extent's end block >= src extent's start
    // block.
    const auto lower_it = std::lower_bound(
        operations.begin(),
        operations.end(),
        op,
        [](const CowMergeOperation& it, const CowMergeOperation& op) {
          auto dst_end_block =
              it.dst_extent().start_block() + it.dst_extent().num_blocks() - 1;
          return dst_end_block < op.src_extent().start_block();
        });
    // upper bound: dst extent's start block > src extent's end block
    const auto upper_it = std::upper_bound(
        lower_it,
        operations.end(),
        op,
        [](const CowMergeOperation& op, const CowMergeOperation& it) {
          auto src_end_block =
              op.src_extent().start_block() + op.src_extent().num_blocks() - 1;
          return src_end_block < it.dst_extent().start_block();
        });

    // TODO(xunchang) skip inserting the empty set to merge_after.
    if (lower_it == upper_it) {
      merge_after.insert({op, {}});
    } else {
      std::set<CowMergeOperation> operations(lower_it, upper_it);
      auto it = operations.find(op);
      if (it != operations.end()) {
        LOG(INFO) << "Self overlapping " << op;
        operations.erase(it);
      }
      auto ret = merge_after.emplace(op, std::move(operations));
      // Check the insertion indeed happens.
      CHECK(ret.second) << op;
    }
  }

  return merge_after;
}

bool MergeSequenceGenerator::Generate(
    std::vector<CowMergeOperation>* sequence) const {
  sequence->clear();

  LOG(INFO) << "Generating sequence";

  // Use the non-DFS version of the topology sort. So we can control the
  // operations to discard to break cycles; thus yielding a deterministic
  // sequence.
  std::map<CowMergeOperation, int> incoming_edges;
  for (const auto& it : merge_after_) {
    for (const auto& blocked : it.second) {
      // Value is default initialized to 0.
      incoming_edges[blocked] += 1;
    }
  }

  // Technically, we can use std::unordered_set or just a std::vector. but
  // std::set gives the benefit where operations are sorted by dst blocks. This
  // will ensure that operations that do not have dependency constraints appear
  // in increasing block order. Such order would help snapuserd batch merges and
  // improve boot time, but isn't strictly needed for correctness.
  std::set<CowMergeOperation> free_operations;
  for (const auto& op : operations_) {
    if (incoming_edges.find(op) == incoming_edges.end()) {
      free_operations.insert(op);
    }
  }

  std::vector<CowMergeOperation> merge_sequence;
  std::set<CowMergeOperation> convert_to_raw;
  while (!incoming_edges.empty()) {
    if (!free_operations.empty()) {
      merge_sequence.insert(
          merge_sequence.end(), free_operations.begin(), free_operations.end());
    } else {
      auto to_convert = PickConvertToRaw(incoming_edges, merge_after_);
      // The operation we pick must be one of the nodes not already in merge
      // sequence.
      CHECK(incoming_edges.find(to_convert) != incoming_edges.end());

      free_operations.insert(to_convert);
      convert_to_raw.insert(to_convert);
      LOG(INFO) << "Converting operation to raw " << to_convert;
    }

    std::set<CowMergeOperation> next_free_operations;
    for (const auto& op : free_operations) {
      incoming_edges.erase(op);

      // Now that this particular operation is merged, other operations
      // blocked by this one may be free. Decrement the count of blocking
      // operations, and set up the free operations for the next iteration.
      for (const auto& blocked : merge_after_.at(op)) {
        auto it = incoming_edges.find(blocked);
        if (it == incoming_edges.end()) {
          continue;
        }

        auto blocking_transfer_count = &it->second;
        if (*blocking_transfer_count <= 0) {
          LOG(ERROR) << "Unexpected count in merge after map "
                     << blocking_transfer_count;
          return false;
        }
        // This operation is no longer blocked by anyone. Add it to the merge
        // sequence in the next iteration.
        *blocking_transfer_count -= 1;
        if (*blocking_transfer_count == 0) {
          next_free_operations.insert(blocked);
        }
      }
    }

    LOG(INFO) << "Remaining transfers " << incoming_edges.size()
              << ", free transfers " << free_operations.size()
              << ", merge_sequence size " << merge_sequence.size();
    free_operations = std::move(next_free_operations);
  }

  if (!free_operations.empty()) {
    merge_sequence.insert(
        merge_sequence.end(), free_operations.begin(), free_operations.end());
  }

  CHECK_EQ(operations_.size(), merge_sequence.size() + convert_to_raw.size());

  size_t blocks_in_sequence = 0;
  for (const CowMergeOperation& transfer : merge_sequence) {
    blocks_in_sequence += transfer.dst_extent().num_blocks();
  }

  size_t blocks_in_raw = 0;
  for (const CowMergeOperation& transfer : convert_to_raw) {
    blocks_in_raw += transfer.dst_extent().num_blocks();
  }

  LOG(INFO) << "Blocks in merge sequence " << blocks_in_sequence
            << ", blocks in raw " << blocks_in_raw << ", partition "
            << partition_name_;
  if (!ValidateSequence(merge_sequence)) {
    LOG(ERROR) << "Invalid Sequence";
    return false;
  }

  *sequence = std::move(merge_sequence);
  return true;
}

bool MergeSequenceGenerator::ValidateSequence(
    const std::vector<CowMergeOperation>& sequence) {
  LOG(INFO) << "Validating merge sequence";
  ExtentRanges visited;
  for (const auto& op : sequence) {
    // If |src_offset| is greater than zero, dependency should include 1 extra
    // block at end of src_extent, as the OP actually references data past
    // original src_extent.
    if (op.src_offset() > 0) {
      CHECK_EQ(op.src_extent().num_blocks(), op.dst_extent().num_blocks() + 1)
          << op;
    } else {
      CHECK_EQ(op.src_extent().num_blocks(), op.dst_extent().num_blocks())
          << op;
    }
    if (visited.OverlapsWithExtent(op.src_extent())) {
      LOG(ERROR) << "Transfer violates the merge sequence " << op
                 << "Visited extent ranges: ";
      visited.Dump();
      return false;
    }

    CHECK(!visited.OverlapsWithExtent(op.dst_extent()))
        << "dst extent should write only once.";
    visited.AddExtent(op.dst_extent());
  }

  return true;
}

}  // namespace chromeos_update_engine
