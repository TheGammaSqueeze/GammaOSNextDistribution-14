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

#include <algorithm>
#include <vector>

#include <android-base/file.h>
#include <gtest/gtest.h>

#include "update_engine/common/test_utils.h"
#include "update_engine/common/utils.h"
#include "update_engine/payload_generator/extent_ranges.h"
#include "update_engine/payload_generator/extent_utils.h"
#include "update_engine/payload_generator/merge_sequence_generator.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {
using test_utils::GetBuildArtifactsPath;

CowMergeOperation CreateCowMergeOperation(const Extent& src_extent,
                                          const Extent& dst_extent) {
  return CreateCowMergeOperation(
      src_extent, dst_extent, CowMergeOperation::COW_COPY);
}
class MergeSequenceGeneratorTest : public ::testing::Test {
 protected:
  void VerifyTransfers(MergeSequenceGenerator* generator,
                       const std::vector<CowMergeOperation>& expected) {
    ASSERT_EQ(expected, generator->operations_);
  }

  void FindDependency(
      std::vector<CowMergeOperation> transfers,
      std::map<CowMergeOperation, std::set<CowMergeOperation>>* result) {
    std::sort(transfers.begin(), transfers.end());
    *result = MergeSequenceGenerator::FindDependency(transfers);
  }

  void GenerateSequence(std::vector<CowMergeOperation> transfers) {
    std::sort(transfers.begin(), transfers.end());
    MergeSequenceGenerator generator(std::move(transfers), "");
    std::vector<CowMergeOperation> sequence;
    ASSERT_TRUE(generator.Generate(&sequence));
  }
};

TEST_F(MergeSequenceGeneratorTest, Create) {
  std::vector<AnnotatedOperation> aops{{"file1", {}, {}}, {"file2", {}, {}}};
  aops[0].op.set_type(InstallOperation::SOURCE_COPY);
  *aops[0].op.add_src_extents() = ExtentForRange(10, 10);
  *aops[0].op.add_dst_extents() = ExtentForRange(30, 10);

  aops[1].op.set_type(InstallOperation::SOURCE_COPY);
  *aops[1].op.add_src_extents() = ExtentForRange(20, 10);
  *aops[1].op.add_dst_extents() = ExtentForRange(40, 10);

  auto generator = MergeSequenceGenerator::Create(aops);
  ASSERT_TRUE(generator);
  std::vector<CowMergeOperation> expected = {
      CreateCowMergeOperation(ExtentForRange(10, 10), ExtentForRange(30, 10)),
      CreateCowMergeOperation(ExtentForRange(20, 10), ExtentForRange(40, 10))};
  VerifyTransfers(generator.get(), expected);

  *aops[1].op.add_src_extents() = ExtentForRange(30, 5);
  *aops[1].op.add_dst_extents() = ExtentForRange(50, 5);
  generator = MergeSequenceGenerator::Create(aops);
  ASSERT_FALSE(generator);
}

TEST_F(MergeSequenceGeneratorTest, Create_SplitSource) {
  InstallOperation op;
  op.set_type(InstallOperation::SOURCE_COPY);
  *(op.add_src_extents()) = ExtentForRange(2, 3);
  *(op.add_src_extents()) = ExtentForRange(6, 1);
  *(op.add_src_extents()) = ExtentForRange(8, 4);
  *(op.add_dst_extents()) = ExtentForRange(10, 8);

  AnnotatedOperation aop{"file1", op, {}};
  auto generator = MergeSequenceGenerator::Create({aop});
  ASSERT_TRUE(generator);
  std::vector<CowMergeOperation> expected = {
      CreateCowMergeOperation(ExtentForRange(2, 3), ExtentForRange(10, 3)),
      CreateCowMergeOperation(ExtentForRange(6, 1), ExtentForRange(13, 1)),
      CreateCowMergeOperation(ExtentForRange(8, 4), ExtentForRange(14, 4))};
  VerifyTransfers(generator.get(), expected);
}

TEST_F(MergeSequenceGeneratorTest, FindDependency) {
  std::vector<CowMergeOperation> transfers = {
      CreateCowMergeOperation(ExtentForRange(10, 10), ExtentForRange(15, 10)),
      CreateCowMergeOperation(ExtentForRange(40, 10), ExtentForRange(50, 10)),
  };

  std::map<CowMergeOperation, std::set<CowMergeOperation>> merge_after;
  FindDependency(transfers, &merge_after);
  ASSERT_EQ(std::set<CowMergeOperation>(), merge_after.at(transfers[0]));
  ASSERT_EQ(std::set<CowMergeOperation>(), merge_after.at(transfers[1]));

  transfers = {
      CreateCowMergeOperation(ExtentForRange(10, 10), ExtentForRange(25, 10)),
      CreateCowMergeOperation(ExtentForRange(24, 5), ExtentForRange(35, 5)),
      CreateCowMergeOperation(ExtentForRange(30, 10), ExtentForRange(15, 10)),
  };

  FindDependency(transfers, &merge_after);
  ASSERT_EQ(std::set<CowMergeOperation>({transfers[2]}),
            merge_after.at(transfers[0]));
  ASSERT_EQ(std::set<CowMergeOperation>({transfers[0], transfers[2]}),
            merge_after.at(transfers[1]));
  ASSERT_EQ(std::set<CowMergeOperation>({transfers[0], transfers[1]}),
            merge_after.at(transfers[2]));
}

TEST_F(MergeSequenceGeneratorTest, FindDependencyEdgeCase) {
  std::vector<CowMergeOperation> transfers = {
      CreateCowMergeOperation(ExtentForRange(10, 10), ExtentForRange(15, 10)),
      CreateCowMergeOperation(ExtentForRange(40, 10), ExtentForRange(50, 10)),
      CreateCowMergeOperation(ExtentForRange(59, 10), ExtentForRange(60, 10)),
  };

  std::map<CowMergeOperation, std::set<CowMergeOperation>> merge_after;
  FindDependency(transfers, &merge_after);
  ASSERT_EQ(std::set<CowMergeOperation>(), merge_after.at(transfers[0]));
  ASSERT_EQ(std::set<CowMergeOperation>(), merge_after.at(transfers[1]));
  ASSERT_EQ(merge_after[transfers[2]].size(), 1U);
}

TEST_F(MergeSequenceGeneratorTest, FindDependency_ReusedSourceBlocks) {
  std::vector<CowMergeOperation> transfers = {
      CreateCowMergeOperation(ExtentForRange(5, 10), ExtentForRange(15, 10)),
      CreateCowMergeOperation(ExtentForRange(6, 5), ExtentForRange(30, 5)),
      CreateCowMergeOperation(ExtentForRange(50, 5), ExtentForRange(5, 5)),
  };

  std::map<CowMergeOperation, std::set<CowMergeOperation>> merge_after;
  FindDependency(transfers, &merge_after);
  ASSERT_EQ(std::set<CowMergeOperation>({transfers[2]}),
            merge_after.at(transfers[0]));
  ASSERT_EQ(std::set<CowMergeOperation>({transfers[2]}),
            merge_after.at(transfers[1]));
}

TEST_F(MergeSequenceGeneratorTest, ValidateSequence) {
  std::vector<CowMergeOperation> transfers = {
      CreateCowMergeOperation(ExtentForRange(10, 10), ExtentForRange(15, 10)),
      CreateCowMergeOperation(ExtentForRange(30, 10), ExtentForRange(40, 10)),
  };

  // Self overlapping
  ASSERT_TRUE(MergeSequenceGenerator::ValidateSequence(transfers));

  transfers = {
      CreateCowMergeOperation(ExtentForRange(30, 10), ExtentForRange(20, 10)),
      CreateCowMergeOperation(ExtentForRange(15, 10), ExtentForRange(10, 10)),
  };
  ASSERT_FALSE(MergeSequenceGenerator::ValidateSequence(transfers));
}

TEST_F(MergeSequenceGeneratorTest, GenerateSequenceNoCycles) {
  std::vector<CowMergeOperation> transfers = {
      CreateCowMergeOperation(ExtentForRange(10, 10), ExtentForRange(15, 10)),
      // file3 should merge before file2
      CreateCowMergeOperation(ExtentForRange(40, 5), ExtentForRange(25, 5)),
      CreateCowMergeOperation(ExtentForRange(25, 10), ExtentForRange(30, 10)),
  };

  GenerateSequence(transfers);
}

TEST_F(MergeSequenceGeneratorTest, GenerateSequenceWithCycles) {
  std::vector<CowMergeOperation> transfers = {
      CreateCowMergeOperation(ExtentForRange(15, 10), ExtentForRange(30, 10)),
      CreateCowMergeOperation(ExtentForRange(30, 10), ExtentForRange(40, 10)),
      CreateCowMergeOperation(ExtentForRange(40, 10), ExtentForRange(15, 10)),
      CreateCowMergeOperation(ExtentForRange(10, 10), ExtentForRange(5, 10)),
  };

  GenerateSequence(transfers);
}

TEST_F(MergeSequenceGeneratorTest, GenerateSequenceMultipleCycles) {
  std::vector<CowMergeOperation> transfers = {
      // cycle 1
      CreateCowMergeOperation(ExtentForRange(10, 10), ExtentForRange(25, 10)),
      CreateCowMergeOperation(ExtentForRange(24, 5), ExtentForRange(35, 5)),
      CreateCowMergeOperation(ExtentForRange(30, 10), ExtentForRange(15, 10)),
      // cycle 2
      CreateCowMergeOperation(ExtentForRange(50, 10), ExtentForRange(60, 10)),
      CreateCowMergeOperation(ExtentForRange(60, 10), ExtentForRange(70, 10)),
      CreateCowMergeOperation(ExtentForRange(70, 10), ExtentForRange(50, 10)),
  };

  GenerateSequence(transfers);
}

void ValidateSplitSequence(const Extent& src_extent, const Extent& dst_extent) {
  std::vector<CowMergeOperation> sequence;
  SplitSelfOverlapping(src_extent, dst_extent, &sequence);
  ExtentRanges src_extent_set;
  src_extent_set.AddExtent(src_extent);
  ExtentRanges dst_extent_set;
  dst_extent_set.AddExtent(dst_extent);

  size_t src_block_count = 0;
  size_t dst_block_count = 0;
  std::cout << "src_extent: " << src_extent << " dst_extent: " << dst_extent
            << '\n';
  for (const auto& merge_op : sequence) {
    src_extent_set.SubtractExtent(merge_op.src_extent());
    dst_extent_set.SubtractExtent(merge_op.dst_extent());
    src_block_count += merge_op.src_extent().num_blocks();
    dst_block_count += merge_op.dst_extent().num_blocks();
    std::cout << merge_op.src_extent() << " -> " << merge_op.dst_extent()
              << '\n';
    ASSERT_FALSE(ExtentRanges::ExtentsOverlap(merge_op.src_extent(),
                                              merge_op.dst_extent()));
  }
  std::cout << '\n';
  // Check that all blocks are covered
  ASSERT_EQ(src_extent_set.extent_set().size(), 0UL);
  ASSERT_EQ(dst_extent_set.extent_set().size(), 0UL);

  // Check that the split didn't cover extra blocks
  ASSERT_EQ(src_block_count, src_extent.num_blocks());
  ASSERT_EQ(dst_block_count, dst_extent.num_blocks());
}

TEST_F(MergeSequenceGeneratorTest, SplitSelfOverlappingTest) {
  auto a = ExtentForRange(25, 16);
  auto b = ExtentForRange(30, 16);
  ValidateSplitSequence(a, b);
  ValidateSplitSequence(b, a);
}

TEST_F(MergeSequenceGeneratorTest, GenerateSequenceWithXor) {
  std::vector<CowMergeOperation> transfers = {
      // cycle 1
      CreateCowMergeOperation(ExtentForRange(10, 10),
                              ExtentForRange(25, 10),
                              CowMergeOperation::COW_XOR),
      CreateCowMergeOperation(ExtentForRange(24, 5), ExtentForRange(35, 5)),
      CreateCowMergeOperation(ExtentForRange(30, 10),
                              ExtentForRange(15, 10),
                              CowMergeOperation::COW_XOR),
      // cycle 2
      CreateCowMergeOperation(ExtentForRange(50, 10), ExtentForRange(60, 10)),
      CreateCowMergeOperation(ExtentForRange(60, 10),
                              ExtentForRange(70, 10),
                              CowMergeOperation::COW_XOR),
      CreateCowMergeOperation(ExtentForRange(70, 10), ExtentForRange(50, 10)),
  };

  GenerateSequence(transfers);
}

TEST_F(MergeSequenceGeneratorTest, CreateGeneratorWithXor) {
  std::vector<AnnotatedOperation> aops;
  auto& aop = aops.emplace_back();
  aop.op.set_type(InstallOperation::SOURCE_BSDIFF);
  *aop.op.mutable_src_extents()->Add() = ExtentForRange(10, 5);
  *aop.op.mutable_dst_extents()->Add() = ExtentForRange(20, 5);
  auto& xor_map = aop.xor_ops;
  {
    // xor_map[i] = i * kBlockSize + 123;
    auto& op = xor_map.emplace_back();
    *op.mutable_src_extent() = ExtentForRange(10, 5);
    *op.mutable_dst_extent() = ExtentForRange(20, 5);
    op.set_src_offset(123);
    op.set_type(CowMergeOperation::COW_XOR);
  }
  auto generator = MergeSequenceGenerator::Create(aops);
  ASSERT_NE(generator, nullptr);
  std::vector<CowMergeOperation> sequence;
  ASSERT_TRUE(generator->Generate(&sequence));
  ASSERT_EQ(sequence.size(), 1UL);
  ASSERT_EQ(sequence[0].src_extent().start_block(), 10UL);
  ASSERT_EQ(sequence[0].dst_extent().start_block(), 20UL);
  ASSERT_EQ(sequence[0].src_extent().num_blocks(), 6UL);
  ASSERT_EQ(sequence[0].dst_extent().num_blocks(), 5UL);
  ASSERT_EQ(sequence[0].type(), CowMergeOperation::COW_XOR);
  ASSERT_EQ(sequence[0].src_offset(), 123UL);

  ASSERT_TRUE(generator->ValidateSequence(sequence));
}

TEST_F(MergeSequenceGeneratorTest, CreateGeneratorWithXorMultipleExtents) {
  std::vector<AnnotatedOperation> aops;
  auto& aop = aops.emplace_back();
  aop.op.set_type(InstallOperation::SOURCE_BSDIFF);
  *aop.op.mutable_src_extents()->Add() = ExtentForRange(10, 10);
  *aop.op.mutable_dst_extents()->Add() = ExtentForRange(30, 5);
  *aop.op.mutable_dst_extents()->Add() = ExtentForRange(45, 5);
  auto& xor_map = aop.xor_ops;
  {
    // xor_map[i] = i * kBlockSize + 123;
    auto& op = xor_map.emplace_back();
    *op.mutable_src_extent() = ExtentForRange(10, 5);
    *op.mutable_dst_extent() = ExtentForRange(30, 5);
    op.set_src_offset(123);
    op.set_type(CowMergeOperation::COW_XOR);
  }
  {
    // xor_map[i] = i * kBlockSize + 123;
    auto& op = xor_map.emplace_back();
    *op.mutable_src_extent() = ExtentForRange(15, 5);
    *op.mutable_dst_extent() = ExtentForRange(45, 5);
    op.set_src_offset(123);
    op.set_type(CowMergeOperation::COW_XOR);
  }
  auto generator = MergeSequenceGenerator::Create(aops);
  ASSERT_NE(generator, nullptr);
  std::vector<CowMergeOperation> sequence;
  ASSERT_TRUE(generator->Generate(&sequence));
  ASSERT_EQ(sequence.size(), 2UL);
  ASSERT_EQ(sequence[0].src_extent().start_block(), 10UL);
  ASSERT_EQ(sequence[0].dst_extent().start_block(), 30UL);
  ASSERT_EQ(sequence[0].src_extent().num_blocks(), 6UL);
  ASSERT_EQ(sequence[0].dst_extent().num_blocks(), 5UL);
  ASSERT_EQ(sequence[0].type(), CowMergeOperation::COW_XOR);
  ASSERT_EQ(sequence[0].src_offset(), 123UL);

  ASSERT_EQ(sequence[1].src_extent().start_block(), 15UL);
  ASSERT_EQ(sequence[1].dst_extent().start_block(), 45UL);
  ASSERT_EQ(sequence[1].src_extent().num_blocks(), 6UL);
  ASSERT_EQ(sequence[1].dst_extent().num_blocks(), 5UL);
  ASSERT_EQ(sequence[1].type(), CowMergeOperation::COW_XOR);
  ASSERT_EQ(sequence[1].src_offset(), 123UL);

  ASSERT_TRUE(generator->ValidateSequence(sequence));
}

TEST_F(MergeSequenceGeneratorTest, CreateGeneratorXorAppendBlock) {
  std::vector<AnnotatedOperation> aops;
  auto& aop = aops.emplace_back();
  aop.op.set_type(InstallOperation::SOURCE_BSDIFF);
  *aop.op.mutable_src_extents()->Add() = ExtentForRange(10, 10);
  *aop.op.mutable_dst_extents()->Add() = ExtentForRange(20, 10);
  auto& xor_map = aop.xor_ops;
  {
    auto& op = xor_map.emplace_back();
    *op.mutable_src_extent() = ExtentForRange(10, 5);
    *op.mutable_dst_extent() = ExtentForRange(20, 5);
    op.set_type(CowMergeOperation::COW_XOR);
  }
  {
    auto& op = xor_map.emplace_back();
    *op.mutable_src_extent() = ExtentForRange(15, 5);
    *op.mutable_dst_extent() = ExtentForRange(25, 5);
    op.set_src_offset(123);
    op.set_type(CowMergeOperation::COW_XOR);
  }
  auto generator = MergeSequenceGenerator::Create(aops);
  ASSERT_NE(generator, nullptr);
  std::vector<CowMergeOperation> sequence;
  ASSERT_TRUE(generator->Generate(&sequence));
  ASSERT_EQ(sequence.size(), 2UL);
  ASSERT_EQ(sequence[0].src_extent().start_block(), 15UL);
  ASSERT_EQ(sequence[0].dst_extent().start_block(), 25UL);
  ASSERT_EQ(sequence[0].src_extent().num_blocks(), 6UL);
  ASSERT_EQ(sequence[0].dst_extent().num_blocks(), 5UL);
  ASSERT_EQ(sequence[0].type(), CowMergeOperation::COW_XOR);
  ASSERT_EQ(sequence[0].src_offset(), 123UL);

  ASSERT_EQ(sequence[1].src_extent().start_block(), 10UL);
  ASSERT_EQ(sequence[1].dst_extent().start_block(), 20UL);
  ASSERT_EQ(sequence[1].src_extent().num_blocks(), 5UL);
  ASSERT_EQ(sequence[1].dst_extent().num_blocks(), 5UL);
  ASSERT_EQ(sequence[1].type(), CowMergeOperation::COW_XOR);

  ASSERT_TRUE(generator->ValidateSequence(sequence));
}

TEST_F(MergeSequenceGeneratorTest, CreateGeneratorXorAlreadyPlusOne) {
  std::vector<AnnotatedOperation> aops;
  auto& aop = aops.emplace_back();
  aop.op.set_type(InstallOperation::SOURCE_BSDIFF);
  *aop.op.mutable_src_extents()->Add() = ExtentForRange(10, 10);
  *aop.op.mutable_dst_extents()->Add() = ExtentForRange(20, 10);
  auto& xor_map = aop.xor_ops;
  {
    auto& op = xor_map.emplace_back();
    *op.mutable_src_extent() = ExtentForRange(15, 6);
    *op.mutable_dst_extent() = ExtentForRange(25, 5);
    op.set_src_offset(123);
    op.set_type(CowMergeOperation::COW_XOR);
  }
  auto generator = MergeSequenceGenerator::Create(aops);
  ASSERT_NE(generator, nullptr);
  std::vector<CowMergeOperation> sequence;
  ASSERT_TRUE(generator->Generate(&sequence));
  ASSERT_EQ(sequence.size(), 1UL);
  ASSERT_EQ(sequence[0].src_extent().start_block(), 15UL);
  ASSERT_EQ(sequence[0].dst_extent().start_block(), 25UL);
  ASSERT_EQ(sequence[0].src_extent().num_blocks(), 6UL);
  ASSERT_EQ(sequence[0].dst_extent().num_blocks(), 5UL);
  ASSERT_EQ(sequence[0].type(), CowMergeOperation::COW_XOR);
  ASSERT_EQ(sequence[0].src_offset(), 123UL);

  ASSERT_TRUE(generator->ValidateSequence(sequence));
}

TEST_F(MergeSequenceGeneratorTest, ActualPayloadTest) {
  auto payload_path =
      GetBuildArtifactsPath("testdata/cycle_nodes_product_no_xor.bin");
  ASSERT_FALSE(payload_path.empty());
  ASSERT_TRUE(utils::FileExists(payload_path.c_str()));
  PartitionUpdate part;
  std::string payload;
  android::base::ReadFileToString(payload_path, &payload);
  part.ParseFromString(payload);
  part.set_partition_name("product");
  std::vector<CowMergeOperation> ops;
  ops.reserve(part.merge_operations_size());
  for (const auto& op : part.merge_operations()) {
    ops.emplace_back(op);
  }
  MergeSequenceGenerator generator(ops, part.partition_name());
  std::vector<CowMergeOperation> sequence;
  ASSERT_TRUE(generator.Generate(&sequence));
}

}  // namespace chromeos_update_engine
