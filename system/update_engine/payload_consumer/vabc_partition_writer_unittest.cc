//
// Copyright (C) 2021 The Android Open Source Project
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

#include <unistd.h>

#include <android-base/file.h>
#include <android-base/mapped_file.h>
#include <android-base/properties.h>
#include <bsdiff/bsdiff.h>
#include <gtest/gtest.h>
#include <libsnapshot/cow_writer.h>
#include <libsnapshot/mock_cow_writer.h>

#include "update_engine/common/error_code.h"
#include "update_engine/common/hash_calculator.h"
#include "update_engine/common/mock_dynamic_partition_control.h"
#include "update_engine/common/utils.h"
#include "update_engine/payload_consumer/vabc_partition_writer.h"
#include "update_engine/payload_generator/delta_diff_generator.h"
#include "update_engine/payload_generator/extent_ranges.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {

using android::snapshot::CowOptions;
using testing::_;
using testing::Args;
using testing::ElementsAreArray;
using testing::Invoke;
using testing::Return;
using testing::Sequence;
using utils::GetReadonlyZeroBlock;

namespace {

static constexpr auto& fake_part_name = "fake_part";
static constexpr size_t FAKE_PART_SIZE = 4096 * 50;
class VABCPartitionWriterTest : public ::testing::Test {
 public:
  void SetUp() override {
    ftruncate(source_part_.fd, FAKE_PART_SIZE);
    ON_CALL(dynamic_control_, GetVirtualAbCompressionXorFeatureFlag())
        .WillByDefault(Return(FeatureFlag(FeatureFlag::Value::NONE)));
  }

 protected:
  void AddBlockTest(bool xor_enabled);
  CowMergeOperation* AddMergeOp(PartitionUpdate* partition,
                                std::array<size_t, 2> src_extent,
                                std::array<size_t, 2> dst_extent,
                                CowMergeOperation_Type type) {
    auto merge_op = partition->add_merge_operations();
    auto src = merge_op->mutable_src_extent();
    src->set_start_block(src_extent[0]);
    src->set_num_blocks(src_extent[1]);
    auto dst = merge_op->mutable_dst_extent();
    dst->set_start_block(dst_extent[0]);
    dst->set_num_blocks(dst_extent[1]);
    merge_op->set_type(type);
    return merge_op;
  }

  android::snapshot::CowOptions options_ = {
      .block_size = static_cast<uint32_t>(kBlockSize)};
  android::snapshot::MockCowWriter cow_writer_;
  MockDynamicPartitionControl dynamic_control_;
  PartitionUpdate partition_update_;
  InstallPlan install_plan_;
  TemporaryFile source_part_;
  InstallPlan::Partition install_part_{.name = fake_part_name,
                                       .source_path = source_part_.path,
                                       .source_size = FAKE_PART_SIZE};
};

TEST_F(VABCPartitionWriterTest, MergeSequenceWriteTest) {
  AddMergeOp(&partition_update_, {5, 1}, {10, 1}, CowMergeOperation::COW_COPY);
  AddMergeOp(&partition_update_, {12, 2}, {13, 2}, CowMergeOperation::COW_XOR);
  AddMergeOp(&partition_update_, {15, 1}, {20, 1}, CowMergeOperation::COW_COPY);
  AddMergeOp(&partition_update_, {20, 1}, {25, 1}, CowMergeOperation::COW_COPY);
  AddMergeOp(&partition_update_, {42, 5}, {40, 5}, CowMergeOperation::COW_XOR);
  VABCPartitionWriter writer_{
      partition_update_, install_part_, &dynamic_control_, kBlockSize};
  EXPECT_CALL(dynamic_control_, OpenCowWriter(fake_part_name, _, _))
      .WillOnce(Invoke([](const std::string&,
                          const std::optional<std::string>&,
                          std::optional<uint64_t>) {
        auto cow_writer = std::make_unique<android::snapshot::MockCowWriter>();
        auto expected_merge_sequence = {10, 14, 13, 20, 25, 40, 41, 42, 43, 44};
        EXPECT_CALL(*cow_writer, AddSequenceData(_, _))
            .With(Args<1, 0>(ElementsAreArray(expected_merge_sequence)))
            .WillOnce(Return(true));
        ON_CALL(*cow_writer, AddCopy(_, _, _)).WillByDefault(Return(true));
        ON_CALL(*cow_writer, AddLabel(_)).WillByDefault(Return(true));
        return cow_writer;
      }));
  EXPECT_CALL(dynamic_control_, GetVirtualAbCompressionXorFeatureFlag())
      .WillRepeatedly(Return(FeatureFlag(FeatureFlag::Value::LAUNCH)));
  ASSERT_TRUE(writer_.Init(&install_plan_, true, 0));
}

TEST_F(VABCPartitionWriterTest, MergeSequenceXorSameBlock) {
  AddMergeOp(&partition_update_, {19, 4}, {19, 3}, CowMergeOperation::COW_XOR)
      ->set_src_offset(1);
  VABCPartitionWriter writer_{
      partition_update_, install_part_, &dynamic_control_, kBlockSize};
  EXPECT_CALL(dynamic_control_, OpenCowWriter(fake_part_name, _, _))
      .WillOnce(
          Invoke([](const std::string&,
                    const std::optional<std::string>&,
                    std::optional<uint64_t>) {
            auto cow_writer =
                std::make_unique<android::snapshot::MockCowWriter>();
            auto expected_merge_sequence = {19, 20, 21};
            EXPECT_CALL(*cow_writer, AddSequenceData(_, _))
                .With(Args<1, 0>(ElementsAreArray(expected_merge_sequence)))
                .WillOnce(Return(true));
            ON_CALL(*cow_writer, AddCopy(_, _, _)).WillByDefault(Return(true));
            ON_CALL(*cow_writer, AddLabel(_)).WillByDefault(Return(true));
            return cow_writer;
          }));
  EXPECT_CALL(dynamic_control_, GetVirtualAbCompressionXorFeatureFlag())
      .WillRepeatedly(Return(FeatureFlag(FeatureFlag::Value::LAUNCH)));
  ASSERT_TRUE(writer_.Init(&install_plan_, true, 0));
}

TEST_F(VABCPartitionWriterTest, AddBlockTestXor) {
  return AddBlockTest(true);
}

TEST_F(VABCPartitionWriterTest, AddBlockTestNoXor) {
  return AddBlockTest(false);
}

void VABCPartitionWriterTest::AddBlockTest(bool xor_enabled) {
  if (xor_enabled) {
    ON_CALL(dynamic_control_, GetVirtualAbCompressionXorFeatureFlag())
        .WillByDefault(Return(FeatureFlag(FeatureFlag::Value::LAUNCH)));
  } else {
    ON_CALL(dynamic_control_, GetVirtualAbCompressionXorFeatureFlag())
        .WillByDefault(Return(FeatureFlag(FeatureFlag::Value::NONE)));
  }
  InstallOperation& install_op = *partition_update_.add_operations();
  install_op.set_type(InstallOperation::SOURCE_COPY);
  *install_op.add_src_extents() = ExtentForRange(5, 1);
  *install_op.add_src_extents() = ExtentForRange(10, 1);
  *install_op.add_src_extents() = ExtentForRange(15, 2);
  *install_op.add_src_extents() = ExtentForRange(20, 2);

  *install_op.add_dst_extents() = ExtentForRange(10, 1);
  *install_op.add_dst_extents() = ExtentForRange(15, 1);
  *install_op.add_dst_extents() = ExtentForRange(20, 2);
  *install_op.add_dst_extents() = ExtentForRange(25, 2);
  AddMergeOp(&partition_update_, {5, 1}, {10, 1}, CowMergeOperation::COW_COPY);
  AddMergeOp(&partition_update_, {10, 1}, {15, 1}, CowMergeOperation::COW_COPY);
  AddMergeOp(&partition_update_, {15, 2}, {20, 2}, CowMergeOperation::COW_COPY);
  AddMergeOp(&partition_update_, {20, 1}, {25, 1}, CowMergeOperation::COW_COPY);
  VABCPartitionWriter writer_{
      partition_update_, install_part_, &dynamic_control_, kBlockSize};
  EXPECT_CALL(dynamic_control_, OpenCowWriter(fake_part_name, _, _))
      .WillOnce(Invoke([xor_enabled](const std::string&,
                                     const std::optional<std::string>&,
                                     std::optional<uint64_t>) {
        auto cow_writer = std::make_unique<android::snapshot::MockCowWriter>();
        ON_CALL(*cow_writer, AddCopy(_, _, _)).WillByDefault(Return(true));
        ON_CALL(*cow_writer, AddLabel(_)).WillByDefault(Return(true));
        if (xor_enabled) {
          EXPECT_CALL(*cow_writer, AddSequenceData(_, _))
              .WillOnce(Return(true));
          EXPECT_CALL(*cow_writer, AddCopy(10, 5, 1));
          EXPECT_CALL(*cow_writer, AddCopy(15, 10, 1));
          // libsnapshot want blocks in reverser order, so 21 goes before 20
          EXPECT_CALL(*cow_writer, AddCopy(20, 15, 2));

          EXPECT_CALL(*cow_writer, AddCopy(25, 20, 1));
          EXPECT_CALL(*cow_writer, AddRawBlocks(26, _, 4096))
              .WillOnce(Return(true));
          EXPECT_CALL(*cow_writer, Finalize());
        } else {
          Sequence s;
          EXPECT_CALL(*cow_writer, AddCopy(10, 5, 1)).InSequence(s);
          EXPECT_CALL(*cow_writer, AddCopy(15, 10, 1)).InSequence(s);
          // libsnapshot want blocks in reverser order, so 21 goes before 20
          EXPECT_CALL(*cow_writer, AddCopy(20, 15, 2)).InSequence(s);

          EXPECT_CALL(*cow_writer, AddCopy(25, 20, 1)).InSequence(s);
          EXPECT_CALL(*cow_writer, AddRawBlocks(26, _, 4096))
              .InSequence(s)
              .WillOnce(Return(true));
        }
        return cow_writer;
      }));
  ASSERT_TRUE(writer_.Init(&install_plan_, true, 0));
  ErrorCode error{};
  ASSERT_TRUE(writer_.PerformSourceCopyOperation(install_op, &error));
}

std::string GetNoopBSDIFF(size_t data_size) {
  auto zeros = GetReadonlyZeroBlock(data_size);
  TemporaryFile patch_file;
  int error = bsdiff::bsdiff(reinterpret_cast<const uint8_t*>(zeros->data()),
                             zeros->size(),
                             reinterpret_cast<const uint8_t*>(zeros->data()),
                             zeros->size(),
                             patch_file.path,
                             nullptr);
  if (error) {
    LOG(ERROR) << "Failed to generate BSDIFF patch " << error;
    return {};
  }
  std::string patch_data;
  if (!utils::ReadFile(patch_file.path, &patch_data)) {
    return {};
  }
  return patch_data;
}

TEST_F(VABCPartitionWriterTest, StreamXORBlockTest) {
  AddMergeOp(&partition_update_, {5, 2}, {10, 2}, CowMergeOperation::COW_XOR);
  AddMergeOp(&partition_update_, {8, 2}, {13, 2}, CowMergeOperation::COW_XOR);
  auto install_op = partition_update_.add_operations();
  *install_op->add_src_extents() = ExtentForRange(5, 5);
  *install_op->add_dst_extents() = ExtentForRange(10, 5);
  install_op->set_type(InstallOperation::SOURCE_BSDIFF);
  auto data_hash = install_op->mutable_src_sha256_hash();
  auto zeros = GetReadonlyZeroBlock(kBlockSize * 5);
  brillo::Blob expected_hash;
  truncate64(source_part_.path, kBlockSize * 20);
  HashCalculator::RawHashOfBytes(zeros->data(), zeros->size(), &expected_hash);
  data_hash->assign(reinterpret_cast<const char*>(expected_hash.data()),
                    expected_hash.size());

  EXPECT_CALL(dynamic_control_, OpenCowWriter(fake_part_name, _, _))
      .WillOnce(Invoke([](const std::string&,
                          const std::optional<std::string>&,
                          std::optional<uint64_t>) {
        auto cow_writer = std::make_unique<android::snapshot::MockCowWriter>();
        ON_CALL(*cow_writer, AddLabel(_)).WillByDefault(Return(true));
        auto expected_merge_sequence = {10, 11, 13, 14};
        auto expected_merge_sequence_rev = {11, 10, 14, 13};
        const bool is_ascending = android::base::GetBoolProperty(
            "ro.virtual_ab.userspace.snapshots.enabled", false);
        if (!is_ascending) {
          EXPECT_CALL(*cow_writer, AddSequenceData(_, _))
              .With(Args<1, 0>(ElementsAreArray(expected_merge_sequence_rev)))
              .WillOnce(Return(true));
        } else {
          EXPECT_CALL(*cow_writer, AddSequenceData(_, _))
              .With(Args<1, 0>(ElementsAreArray(expected_merge_sequence)))
              .WillOnce(Return(true));
        }
        EXPECT_CALL(*cow_writer, AddCopy(_, _, _)).Times(0);
        EXPECT_CALL(*cow_writer, AddRawBlocks(_, _, _)).WillOnce(Return(true));
        EXPECT_CALL(*cow_writer, AddXorBlocks(10, _, kBlockSize * 2, 5, 0))
            .WillOnce(Return(true));
        EXPECT_CALL(*cow_writer, AddXorBlocks(13, _, kBlockSize * 2, 8, 0))
            .WillOnce(Return(true));
        return cow_writer;
      }));
  EXPECT_CALL(dynamic_control_, GetVirtualAbCompressionXorFeatureFlag())
      .WillRepeatedly(Return(FeatureFlag(FeatureFlag::Value::LAUNCH)));
  VABCPartitionWriter writer_{
      partition_update_, install_part_, &dynamic_control_, kBlockSize};
  ASSERT_TRUE(writer_.Init(&install_plan_, true, 0));
  const auto patch_data = GetNoopBSDIFF(kBlockSize * 5);
  ASSERT_GT(patch_data.size(), 0UL);
  ASSERT_TRUE(writer_.PerformDiffOperation(
      *install_op, nullptr, patch_data.data(), patch_data.size()));
}

}  // namespace

}  // namespace chromeos_update_engine
