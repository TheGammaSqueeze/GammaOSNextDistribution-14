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

#include <algorithm>
#include <memory>

#include <unistd.h>

#include <android-base/file.h>
#include <gtest/gtest.h>
#include <libsnapshot/mock_cow_writer.h>

#include "update_engine/common/utils.h"
#include "update_engine/payload_consumer/extent_map.h"
#include "update_engine/payload_consumer/file_descriptor.h"
#include "update_engine/payload_consumer/xor_extent_writer.h"
#include "update_engine/payload_generator/delta_diff_generator.h"
#include "update_engine/payload_generator/extent_ranges.h"
#include "update_engine/payload_generator/merge_sequence_generator.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {

using testing::_;
using testing::Args;
using testing::Return;

class XorExtentWriterTest : public ::testing::Test {
 public:
  static constexpr size_t NUM_BLOCKS = 50;
  void SetUp() override {
    ASSERT_EQ(ftruncate64(source_part_.fd, kBlockSize * NUM_BLOCKS), 0);
    ASSERT_EQ(ftruncate64(target_part_.fd, kBlockSize * NUM_BLOCKS), 0);

    // Fill source part with arbitrary data, as we are computing XOR between
    // source and target data later.
    ASSERT_EQ(lseek(source_part_.fd, 0, SEEK_SET), 0);
    brillo::Blob buffer(kBlockSize);
    for (size_t i = 0; i < kBlockSize; i++) {
      buffer[i] = i & 0xFF;
    }
    for (size_t i = 0; i < NUM_BLOCKS; i++) {
      ASSERT_EQ(write(source_part_.fd, buffer.data(), buffer.size()),
                static_cast<ssize_t>(buffer.size()));
    }
    ASSERT_EQ(fsync(source_part_.fd), 0);
    ASSERT_EQ(fsync(target_part_.fd), 0);
    ASSERT_TRUE(source_fd_->Open(source_part_.path, O_RDONLY | O_CREAT, 0644));
  }
  InstallOperation op_;
  FileDescriptorPtr source_fd_ = std::make_shared<EintrSafeFileDescriptor>();
  ExtentMap<const CowMergeOperation*> xor_map_;
  android::snapshot::MockCowWriter cow_writer_;
  TemporaryFile source_part_;
  TemporaryFile target_part_;
};

MATCHER_P2(BytesEqual,
           bytes,
           size,
           "Check if args match expected value byte for byte") {
  return std::get<1>(arg) == size && std::get<0>(arg) != nullptr &&
         memcmp(std::get<0>(arg), bytes, size) == 0;
}

TEST_F(XorExtentWriterTest, StreamTest) {
  constexpr auto COW_XOR = CowMergeOperation::COW_XOR;
  ON_CALL(cow_writer_, AddXorBlocks(_, _, _, _, _)).WillByDefault(Return(true));
  const auto op1 = CreateCowMergeOperation(
      ExtentForRange(5, 2), ExtentForRange(5, 2), COW_XOR);
  ASSERT_TRUE(xor_map_.AddExtent(op1.dst_extent(), &op1));
  *op_.add_src_extents() = op1.src_extent();
  *op_.add_dst_extents() = op1.dst_extent();

  const auto op2 = CreateCowMergeOperation(
      ExtentForRange(45, 2), ExtentForRange(456, 2), COW_XOR);
  ASSERT_TRUE(xor_map_.AddExtent(op2.dst_extent(), &op2));
  *op_.add_src_extents() = ExtentForRange(45, 3);
  *op_.add_dst_extents() = ExtentForRange(455, 3);

  const auto op3 = CreateCowMergeOperation(
      ExtentForRange(12, 2), ExtentForRange(321, 2), COW_XOR, 777);
  ASSERT_TRUE(xor_map_.AddExtent(op3.dst_extent(), &op3));
  *op_.add_src_extents() = ExtentForRange(12, 4);
  *op_.add_dst_extents() = ExtentForRange(320, 4);
  XORExtentWriter writer_{
      op_, source_fd_, &cow_writer_, xor_map_, NUM_BLOCKS * kBlockSize};

  // OTA op:
  // [5-6] => [5-6], [45-47] => [455-457], [12-15] => [320-323]

  // merge op:
  // [5-6] => [5-6], [45-46] => [456-457], [12-13] => [321-322]

  // Expected result:
  // [5-7], [45-47], [12-14] should be XOR blocks
  // [320], [323], [455] should be regular replace blocks

  auto zeros = utils::GetReadonlyZeroBlock(kBlockSize * 10);
  EXPECT_CALL(cow_writer_,
              AddRawBlocks(455, zeros->data() + 2 * kBlockSize, kBlockSize))
      .With(Args<1, 2>(BytesEqual(zeros->data(), kBlockSize)))
      .WillOnce(Return(true));
  EXPECT_CALL(cow_writer_,
              AddRawBlocks(320, zeros->data() + 5 * kBlockSize, kBlockSize))
      .With(Args<1, 2>(BytesEqual(zeros->data(), kBlockSize)))
      .WillOnce(Return(true));
  EXPECT_CALL(cow_writer_,
              AddRawBlocks(323, zeros->data() + 8 * kBlockSize, kBlockSize))
      .With(Args<1, 2>(BytesEqual(zeros->data(), kBlockSize)))
      .WillOnce(Return(true));

  EXPECT_CALL(cow_writer_, AddXorBlocks(5, _, kBlockSize * 2, 5, 0))
      .WillOnce(Return(true));
  EXPECT_CALL(cow_writer_, AddXorBlocks(456, _, kBlockSize * 2, 45, 0))
      .WillOnce(Return(true));
  EXPECT_CALL(cow_writer_, AddXorBlocks(321, _, kBlockSize * 2, 12, 777))
      .WillOnce(Return(true));

  ASSERT_TRUE(writer_.Init(op_.dst_extents(), kBlockSize));
  ASSERT_TRUE(writer_.Write(zeros->data(), 9 * kBlockSize));
}

TEST_F(XorExtentWriterTest, SubsetExtentTest) {
  constexpr auto COW_XOR = CowMergeOperation::COW_XOR;
  ON_CALL(cow_writer_, AddXorBlocks(_, _, _, _, _)).WillByDefault(Return(true));

  const auto op3 = CreateCowMergeOperation(
      ExtentForRange(12, 4), ExtentForRange(320, 4), COW_XOR, 777);
  ASSERT_TRUE(xor_map_.AddExtent(op3.dst_extent(), &op3));

  *op_.add_src_extents() = ExtentForRange(12, 3);
  *op_.add_dst_extents() = ExtentForRange(320, 3);
  *op_.add_src_extents() = ExtentForRange(20, 3);
  *op_.add_dst_extents() = ExtentForRange(420, 3);
  *op_.add_src_extents() = ExtentForRange(15, 1);
  *op_.add_dst_extents() = ExtentForRange(323, 1);
  XORExtentWriter writer_{
      op_, source_fd_, &cow_writer_, xor_map_, NUM_BLOCKS * kBlockSize};

  // OTA op:
  // [12-14] => [320-322], [20-22] => [420-422], [15-16] => [323-324]

  // merge op:
  // [12-16] => [321-322]

  // Expected result:
  // [12-16] should be XOR blocks
  // [420-422] should be regular replace blocks

  auto zeros = utils::GetReadonlyZeroBlock(kBlockSize * 7);
  EXPECT_CALL(cow_writer_,
              AddRawBlocks(420, zeros->data() + 3 * kBlockSize, kBlockSize * 3))
      .WillOnce(Return(true));

  EXPECT_CALL(cow_writer_, AddXorBlocks(320, _, kBlockSize * 3, 12, 777))
      .WillOnce(Return(true));
  EXPECT_CALL(cow_writer_, AddXorBlocks(323, _, kBlockSize, 15, 777))
      .WillOnce(Return(true));

  ASSERT_TRUE(writer_.Init(op_.dst_extents(), kBlockSize));
  ASSERT_TRUE(writer_.Write(zeros->data(), zeros->size()));
}

TEST_F(XorExtentWriterTest, LastBlockTest) {
  constexpr auto COW_XOR = CowMergeOperation::COW_XOR;
  ON_CALL(cow_writer_, AddXorBlocks(_, _, _, _, _)).WillByDefault(Return(true));

  const auto op3 = CreateCowMergeOperation(
      ExtentForRange(NUM_BLOCKS - 1, 1), ExtentForRange(2, 1), COW_XOR, 777);
  ASSERT_TRUE(xor_map_.AddExtent(op3.dst_extent(), &op3));

  *op_.add_src_extents() = ExtentForRange(12, 3);
  *op_.add_dst_extents() = ExtentForRange(320, 3);

  *op_.add_src_extents() = ExtentForRange(20, 3);
  *op_.add_dst_extents() = ExtentForRange(420, 3);

  *op_.add_src_extents() = ExtentForRange(NUM_BLOCKS - 3, 3);
  *op_.add_dst_extents() = ExtentForRange(2, 3);
  XORExtentWriter writer_{
      op_, source_fd_, &cow_writer_, xor_map_, NUM_BLOCKS * kBlockSize};

  // OTA op:
  // [12-14] => [320-322], [20-22] => [420-422], [NUM_BLOCKS-3] => [2-5]

  // merge op:
  // [NUM_BLOCKS-1] => [2]

  // Expected result:
  // [320-322] should be REPLACE blocks
  // [420-422] should be REPLACE blocks
  // [2] should be XOR blocks, with 0 offset to avoid out of bound read
  // [3-5] should be REPLACE BLOCKS

  auto zeros = utils::GetReadonlyZeroBlock(kBlockSize * 9);
  EXPECT_CALL(cow_writer_, AddRawBlocks(320, zeros->data(), kBlockSize * 3))
      .WillOnce(Return(true));
  EXPECT_CALL(cow_writer_,
              AddRawBlocks(420, zeros->data() + 3 * kBlockSize, kBlockSize * 3))
      .WillOnce(Return(true));

  EXPECT_CALL(cow_writer_, AddXorBlocks(2, _, kBlockSize, NUM_BLOCKS - 1, 0))
      .WillOnce(Return(true));
  EXPECT_CALL(cow_writer_,
              AddRawBlocks(3, zeros->data() + kBlockSize * 7, kBlockSize * 2))
      .WillOnce(Return(true));

  ASSERT_TRUE(writer_.Init(op_.dst_extents(), kBlockSize));
  ASSERT_TRUE(writer_.Write(zeros->data(), zeros->size()));
}

TEST_F(XorExtentWriterTest, LastMultiBlockTest) {
  constexpr auto COW_XOR = CowMergeOperation::COW_XOR;

  const auto op3 = CreateCowMergeOperation(
      ExtentForRange(NUM_BLOCKS - 4, 4), ExtentForRange(2, 4), COW_XOR, 777);
  ASSERT_TRUE(xor_map_.AddExtent(op3.dst_extent(), &op3));

  *op_.add_src_extents() = ExtentForRange(NUM_BLOCKS - 4, 4);
  *op_.add_dst_extents() = ExtentForRange(2, 4);
  XORExtentWriter writer_{
      op_, source_fd_, &cow_writer_, xor_map_, NUM_BLOCKS * kBlockSize};

  // OTA op:
  // [NUM_BLOCKS-4] => [2-5]

  // merge op:
  // [NUM_BLOCKS-4] => [2-5]

  // Expected result:
  // [12-16] should be REPLACE blocks
  // [420-422] should be REPLACE blocks
  // [2-3] should be XOR blocks
  // [4] should be XOR blocks with 0 offset to avoid out of bound read

  // Send arbitrary data, just to confirm that XORExtentWriter did XOR the
  // source data with target data
  std::vector<uint8_t> op_data(kBlockSize * 4);
  for (size_t i = 0; i < op_data.size(); i++) {
    if (i % kBlockSize == 0) {
      op_data[i] = 1;
    } else {
      op_data[i] = (op_data[i - 1] * 3) & 0xFF;
    }
  }
  auto&& verify_xor_data = [source_fd_(source_fd_), &op_data](
                               uint32_t new_block_start,
                               const void* data,
                               size_t size,
                               uint32_t old_block,
                               uint16_t offset) -> bool {
    std::vector<uint8_t> source_data(size);
    ssize_t bytes_read{};
    TEST_AND_RETURN_FALSE_ERRNO(utils::PReadAll(source_fd_,
                                                source_data.data(),
                                                source_data.size(),
                                                old_block * kBlockSize + offset,
                                                &bytes_read));
    TEST_EQ(bytes_read, static_cast<ssize_t>(source_data.size()));
    std::transform(source_data.begin(),
                   source_data.end(),
                   static_cast<const uint8_t*>(data),
                   source_data.begin(),
                   std::bit_xor<uint8_t>{});
    if (memcmp(source_data.data(), op_data.data(), source_data.size()) != 0) {
      LOG(ERROR) << "XOR data received does not appear to be an XOR between "
                    "source and target data";
      return false;
    }
    return true;
  };
  EXPECT_CALL(cow_writer_,
              AddXorBlocks(2, _, kBlockSize * 3, NUM_BLOCKS - 4, 777))
      .WillOnce(verify_xor_data);
  EXPECT_CALL(cow_writer_, AddXorBlocks(5, _, kBlockSize, NUM_BLOCKS - 1, 0))
      .WillOnce(verify_xor_data);

  ASSERT_TRUE(writer_.Init(op_.dst_extents(), kBlockSize));
  ASSERT_TRUE(writer_.Write(op_data.data(), op_data.size()));
}

}  // namespace chromeos_update_engine
