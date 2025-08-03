//
// Copyright (C) 2023 The Android Open Source Project
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

#include "update_engine/payload_consumer/block_extent_writer.h"

#include <fcntl.h>

#include <algorithm>
#include <memory>
#include <string>
#include <vector>

#include <gtest/gtest.h>
#include <gmock/gmock.h>

#include "update_engine/common/test_utils.h"
#include "update_engine/payload_generator/delta_diff_generator.h"
#include "update_engine/payload_generator/extent_utils.h"
#include "update_engine/common/utils.h"
#include "update_engine/payload_generator/extent_ranges.h"

using std::min;
using std::string;
using std::vector;
using testing::_;
using testing::Return;

namespace chromeos_update_engine {

class BlockExtentWriterTest : public ::testing::Test {
 protected:
  void SetUp() override {}
  void TearDown() override {}
};

class MockBlockExtentWriter : public BlockExtentWriter {
 public:
  MOCK_METHOD(bool,
              WriteExtent,
              (const void*, const Extent&, size_t),
              (override));
};

TEST_F(BlockExtentWriterTest, LongExtentTest) {
  google::protobuf::RepeatedPtrField<Extent> extents;
  *extents.Add() = ExtentForRange(0, 1);
  *extents.Add() = ExtentForRange(2, 1);
  *extents.Add() = ExtentForRange(4, 1);
  // A single large extent which doesn't fit in 1 buffer
  static constexpr auto BLOCKS_PER_BUFFER =
      BlockExtentWriter::BUFFER_SIZE / kBlockSize;
  *extents.Add() = ExtentForRange(10, BLOCKS_PER_BUFFER * 2);
  MockBlockExtentWriter writer;
  ASSERT_TRUE(writer.Init(extents, kBlockSize));
  std::string buffer;
  buffer.resize(BlockExtentWriter::BUFFER_SIZE * 2);
  ON_CALL(writer, WriteExtent(_, _, _)).WillByDefault(Return(true));
  EXPECT_CALL(writer,
              WriteExtent(buffer.data(), ExtentForRange(0, 1), kBlockSize));
  EXPECT_CALL(writer,
              WriteExtent(static_cast<void*>(buffer.data() + kBlockSize),
                          ExtentForRange(2, 1),
                          kBlockSize));
  EXPECT_CALL(writer,
              WriteExtent(static_cast<void*>(buffer.data() + kBlockSize * 2),
                          ExtentForRange(4, 1),
                          kBlockSize));
  // The last chunk should be split up into multiple chunks, each chunk is 1
  // BUFFR_SIZE
  EXPECT_CALL(writer,
              WriteExtent(static_cast<void*>(buffer.data()),
                          ExtentForRange(10, BLOCKS_PER_BUFFER),
                          kBlockSize));
  EXPECT_CALL(
      writer,
      WriteExtent(
          static_cast<void*>(buffer.data() + BlockExtentWriter::BUFFER_SIZE),
          ExtentForRange(10 + BLOCKS_PER_BUFFER, BLOCKS_PER_BUFFER),
          kBlockSize));
  ASSERT_TRUE(writer.Write(buffer.data(), kBlockSize * 3));
  ASSERT_TRUE(writer.Write(buffer.data(), BlockExtentWriter::BUFFER_SIZE * 2));
}

TEST_F(BlockExtentWriterTest, LongExtentMultiCall) {
  google::protobuf::RepeatedPtrField<Extent> extents;
  static constexpr auto BLOCKS_PER_BUFFER =
      BlockExtentWriter::BUFFER_SIZE / kBlockSize;
  *extents.Add() = ExtentForRange(10, BLOCKS_PER_BUFFER * 5);
  MockBlockExtentWriter writer;
  ASSERT_TRUE(writer.Init(extents, kBlockSize));
  std::string buffer;
  buffer.resize(BlockExtentWriter::BUFFER_SIZE * 2);
  ON_CALL(writer, WriteExtent(_, _, _)).WillByDefault(Return(true));
  // The last chunk should be split up into multiple chunks, each chunk is 1
  // BUFFR_SIZE
  EXPECT_CALL(writer,
              WriteExtent(static_cast<void*>(buffer.data()),
                          ExtentForRange(10, BLOCKS_PER_BUFFER),
                          kBlockSize));
  EXPECT_CALL(
      writer,
      WriteExtent(static_cast<void*>(buffer.data()),
                  ExtentForRange(10 + BLOCKS_PER_BUFFER, BLOCKS_PER_BUFFER),
                  kBlockSize));
  EXPECT_CALL(
      writer,
      WriteExtent(static_cast<void*>(buffer.data()),
                  ExtentForRange(10 + BLOCKS_PER_BUFFER * 2, BLOCKS_PER_BUFFER),
                  kBlockSize));
  ASSERT_TRUE(writer.Write(buffer.data(), BlockExtentWriter::BUFFER_SIZE));
  ASSERT_TRUE(writer.Write(buffer.data(), BlockExtentWriter::BUFFER_SIZE));
  ASSERT_TRUE(writer.Write(buffer.data(), BlockExtentWriter::BUFFER_SIZE));
}

void FillArbitraryData(std::string* buffer) {
  for (size_t i = 0; i < buffer->size(); i++) {
    (*buffer)[i] = i;
  }
}

TEST_F(BlockExtentWriterTest, SingleBufferMultiCall) {
  google::protobuf::RepeatedPtrField<Extent> extents;
  static constexpr auto BLOCKS_PER_BUFFER =
      BlockExtentWriter::BUFFER_SIZE / kBlockSize;
  *extents.Add() = ExtentForRange(10, BLOCKS_PER_BUFFER);
  MockBlockExtentWriter writer;
  ASSERT_TRUE(writer.Init(extents, kBlockSize));
  std::string buffer;
  buffer.resize(BlockExtentWriter::BUFFER_SIZE);
  FillArbitraryData(&buffer);

  ON_CALL(writer, WriteExtent(_, _, _)).WillByDefault(Return(true));
  EXPECT_CALL(writer,
              WriteExtent(_, ExtentForRange(10, BLOCKS_PER_BUFFER), kBlockSize))
      .WillOnce([&buffer](const void* data, const Extent& extent, size_t) {
        return memcmp(data, buffer.data(), buffer.size()) == 0;
      });

  ASSERT_TRUE(
      writer.Write(buffer.data(), BlockExtentWriter::BUFFER_SIZE - kBlockSize));
  ASSERT_TRUE(writer.Write(
      buffer.data() + BlockExtentWriter::BUFFER_SIZE - kBlockSize, kBlockSize));
}

TEST_F(BlockExtentWriterTest, MultiBufferMultiCall) {
  google::protobuf::RepeatedPtrField<Extent> extents;
  static constexpr auto BLOCKS_PER_BUFFER =
      BlockExtentWriter::BUFFER_SIZE / kBlockSize;
  *extents.Add() = ExtentForRange(10, BLOCKS_PER_BUFFER + 1);
  MockBlockExtentWriter writer;
  ASSERT_TRUE(writer.Init(extents, kBlockSize));
  std::string buffer;
  buffer.resize(BlockExtentWriter::BUFFER_SIZE);
  FillArbitraryData(&buffer);

  ON_CALL(writer, WriteExtent(_, _, _)).WillByDefault(Return(true));
  EXPECT_CALL(writer,
              WriteExtent(_, ExtentForRange(10, BLOCKS_PER_BUFFER), kBlockSize))
      .WillOnce([&buffer](const void* data, const Extent& extent, size_t) {
        return memcmp(data, buffer.data(), extent.num_blocks() * kBlockSize) ==
               0;
      });
  EXPECT_CALL(
      writer,
      WriteExtent(_, ExtentForRange(10 + BLOCKS_PER_BUFFER, 1), kBlockSize));

  ASSERT_TRUE(writer.Write(buffer.data(), BlockExtentWriter::BUFFER_SIZE));
  ASSERT_TRUE(writer.Write(buffer.data(), kBlockSize));
}

}  // namespace chromeos_update_engine
