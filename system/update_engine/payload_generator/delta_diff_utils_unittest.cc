//
// Copyright (C) 2015 The Android Open Source Project
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

#include "update_engine/payload_generator/delta_diff_utils.h"

#include <algorithm>
#include <random>
#include <string>
#include <vector>

#include <base/files/scoped_file.h>
#include <base/format_macros.h>
#include <base/strings/stringprintf.h>
#include <bsdiff/patch_writer.h>
#include <gtest/gtest.h>

#include "payload_generator/filesystem_interface.h"
#include "update_engine/common/test_utils.h"
#include "update_engine/common/utils.h"
#include "update_engine/payload_generator/delta_diff_generator.h"
#include "update_engine/payload_generator/extent_ranges.h"
#include "update_engine/payload_generator/extent_utils.h"
#include "update_engine/payload_generator/fake_filesystem.h"

using std::string;
using std::vector;

namespace chromeos_update_engine {

namespace {

// Writes the |data| in the blocks specified by |extents| on the partition
// |part_path|. The |data| size could be smaller than the size of the blocks
// passed.
bool WriteExtents(const string& part_path,
                  const vector<Extent>& extents,
                  off_t block_size,
                  const brillo::Blob& data) {
  uint64_t offset = 0;
  base::ScopedFILE fp(fopen(part_path.c_str(), "r+"));
  TEST_AND_RETURN_FALSE(fp.get());

  for (const Extent& extent : extents) {
    if (offset >= data.size())
      break;
    TEST_AND_RETURN_FALSE(
        fseek(fp.get(), extent.start_block() * block_size, SEEK_SET) == 0);
    uint64_t to_write =
        std::min(static_cast<uint64_t>(extent.num_blocks()) * block_size,
                 static_cast<uint64_t>(data.size()) - offset);
    TEST_AND_RETURN_FALSE(fwrite(data.data() + offset, 1, to_write, fp.get()) ==
                          to_write);
    offset += extent.num_blocks() * block_size;
  }
  return true;
}

// Create a fake filesystem of the given |size| and initialize the partition
// holding it in the PartitionConfig |part|.
void CreatePartition(PartitionConfig* part,
                     ScopedTempFile* part_file,
                     uint64_t block_size,
                     off_t size) {
  part->path = part_file->path();
  ASSERT_EQ(0, ftruncate(part_file->fd(), size));
  part_file->CloseFd();
  part->fs_interface.reset(new FakeFilesystem(block_size, size / block_size));
  part->size = size;
}

// Writes to the |partition| path blocks such they are all different and they
// include the tag passed in |tag|, making them also different to any other
// block on a partition initialized with this function with a different tag.
// The |block_size| should be a divisor of the partition size.
// Returns whether the function succeeded writing the partition.
bool InitializePartitionWithUniqueBlocks(const PartitionConfig& part,
                                         uint64_t block_size,
                                         uint64_t tag) {
  TEST_AND_RETURN_FALSE(part.size % block_size == 0);
  size_t num_blocks = part.size / block_size;
  brillo::Blob file_data(part.size);
  for (size_t i = 0; i < num_blocks; ++i) {
    string prefix = base::StringPrintf(
        "block tag 0x%.16" PRIx64 ", block number %16" PRIuS " ", tag, i);
    brillo::Blob block_data(prefix.begin(), prefix.end());
    TEST_AND_RETURN_FALSE(prefix.size() <= block_size);
    block_data.resize(block_size, 'X');
    std::copy(block_data.begin(),
              block_data.end(),
              file_data.begin() + i * block_size);
  }
  return test_utils::WriteFileVector(part.path, file_data);
}

}  // namespace

class DeltaDiffUtilsTest : public ::testing::Test {
 protected:
  const uint64_t kDefaultBlockCount = 128;

  void SetUp() override {
    CreatePartition(&old_part_,
                    &old_part_file_,
                    block_size_,
                    block_size_ * kDefaultBlockCount);
    CreatePartition(&new_part_,
                    &new_part_file_,
                    block_size_,
                    block_size_ * kDefaultBlockCount);
  }

  // Helper function to call DeltaMovedAndZeroBlocks() using this class' data
  // members. This simply avoids repeating all the arguments that never change.
  bool RunDeltaMovedAndZeroBlocks(ssize_t chunk_blocks,
                                  uint32_t minor_version) {
    BlobFileWriter blob_file(tmp_blob_file_.fd(), &blob_size_);
    PayloadVersion version(kBrilloMajorPayloadVersion, minor_version);
    ExtentRanges old_zero_blocks;
    return diff_utils::DeltaMovedAndZeroBlocks(&aops_,
                                               old_part_.path,
                                               new_part_.path,
                                               old_part_.size / block_size_,
                                               new_part_.size / block_size_,
                                               chunk_blocks,
                                               {.version = version},
                                               &blob_file,
                                               &old_visited_blocks_,
                                               &new_visited_blocks_,
                                               &old_zero_blocks);
  }

  // Old and new temporary partitions used in the tests. These are initialized
  // with
  PartitionConfig old_part_{"part"};
  PartitionConfig new_part_{"part"};
  ScopedTempFile old_part_file_{"DeltaDiffUtilsTest-old_part-XXXXXX", true};
  ScopedTempFile new_part_file_{"DeltaDiffUtilsTest-new_part-XXXXXX", true};

  // The file holding the output blob from the various diff utils functions.
  ScopedTempFile tmp_blob_file_{"DeltaDiffUtilsTest-blob-XXXXXX", true};
  off_t blob_size_{0};

  size_t block_size_{kBlockSize};

  // Default input/output arguments used when calling DeltaMovedAndZeroBlocks().
  vector<AnnotatedOperation> aops_;
  ExtentRanges old_visited_blocks_;
  ExtentRanges new_visited_blocks_;
};

TEST_F(DeltaDiffUtilsTest, SkipVerityExtentsTest) {
  new_part_.verity.hash_tree_extent = ExtentForRange(20, 30);
  new_part_.verity.fec_extent = ExtentForRange(40, 50);

  BlobFileWriter blob_file(tmp_blob_file_.fd(), &blob_size_);
  ASSERT_TRUE(diff_utils::DeltaReadPartition(
      &aops_,
      old_part_,
      new_part_,
      -1,
      -1,
      {.version = PayloadVersion(kMaxSupportedMajorPayloadVersion,
                                 kVerityMinorPayloadVersion)},
      &blob_file));
  for (const auto& aop : aops_) {
    new_visited_blocks_.AddRepeatedExtents(aop.op.dst_extents());
  }
  for (const auto& extent : new_visited_blocks_.extent_set()) {
    ASSERT_FALSE(ExtentRanges::ExtentsOverlap(
        extent, new_part_.verity.hash_tree_extent));
    ASSERT_FALSE(
        ExtentRanges::ExtentsOverlap(extent, new_part_.verity.fec_extent));
  }
}

TEST_F(DeltaDiffUtilsTest, ReplaceSmallTest) {
  // The old file is on a different block than the new one.
  vector<Extent> old_extents = {ExtentForRange(1, 1)};
  vector<Extent> new_extents = {ExtentForRange(2, 1)};

  // Make a blob that's just 1's that will compress well.
  brillo::Blob ones(kBlockSize, 1);

  // Make a blob with random data that won't compress well.
  brillo::Blob random_data;
  std::mt19937 gen(12345);
  std::uniform_int_distribution<uint16_t> dis(0, 255);
  for (uint32_t i = 0; i < kBlockSize; i++) {
    random_data.push_back(static_cast<uint8_t>(dis(gen)));
  }

  for (int i = 0; i < 2; i++) {
    brillo::Blob data_to_test = i == 0 ? random_data : ones;
    // The old_extents will be initialized with 0.
    ASSERT_TRUE(
        WriteExtents(new_part_.path, new_extents, kBlockSize, data_to_test));

    brillo::Blob data;
    AnnotatedOperation aop;
    InstallOperation& op = aop.op;
    ASSERT_TRUE(diff_utils::ReadExtentsToDiff(
        old_part_.path,
        new_part_.path,
        old_extents,
        new_extents,
        {},  // old_file
        {},  // new_file
        {.version = PayloadVersion(kBrilloMajorPayloadVersion,
                                   kSourceMinorPayloadVersion)},
        &data,
        &aop));
    ASSERT_FALSE(data.empty());

    ASSERT_TRUE(op.has_type());
    const InstallOperation::Type expected_type =
        (i == 0 ? InstallOperation::REPLACE : InstallOperation::REPLACE_BZ);
    ASSERT_EQ(expected_type, op.type());
    ASSERT_FALSE(op.has_data_offset());
    ASSERT_FALSE(op.has_data_length());
    ASSERT_EQ(0, op.src_extents_size());
    ASSERT_FALSE(op.has_src_length());
    ASSERT_EQ(1, op.dst_extents_size());
    ASSERT_FALSE(op.has_dst_length());
    ASSERT_EQ(1U, utils::BlocksInExtents(op.dst_extents()));
  }
}

TEST_F(DeltaDiffUtilsTest, SourceCopyTest) {
  // Makes sure SOURCE_COPY operations are emitted whenever src_ops_allowed
  // is true. It is the same setup as MoveSmallTest, which checks that
  // the operation is well-formed.
  brillo::Blob data_blob(kBlockSize);
  test_utils::FillWithData(&data_blob);

  // The old file is on a different block than the new one.
  vector<Extent> old_extents = {ExtentForRange(11, 1)};
  vector<Extent> new_extents = {ExtentForRange(1, 1)};

  ASSERT_TRUE(WriteExtents(old_part_.path, old_extents, kBlockSize, data_blob));
  ASSERT_TRUE(WriteExtents(new_part_.path, new_extents, kBlockSize, data_blob));

  brillo::Blob data;
  AnnotatedOperation aop;
  ASSERT_TRUE(diff_utils::ReadExtentsToDiff(
      old_part_.path,
      new_part_.path,
      old_extents,
      new_extents,
      {},  // old_deflates
      {},  // new_deflates
      {.version = PayloadVersion(kBrilloMajorPayloadVersion,
                                 kSourceMinorPayloadVersion)},
      &data,
      &aop));
  InstallOperation& op = aop.op;
  ASSERT_TRUE(data.empty());

  ASSERT_TRUE(op.has_type());
  ASSERT_EQ(InstallOperation::SOURCE_COPY, op.type());
}

TEST_F(DeltaDiffUtilsTest, SourceBsdiffTest) {
  // Makes sure SOURCE_BSDIFF operations are emitted whenever src_ops_allowed
  // is true. It is the same setup as BsdiffSmallTest, which checks
  // that the operation is well-formed.
  brillo::Blob data_blob(kBlockSize);
  test_utils::FillWithData(&data_blob);

  // The old file is on a different block than the new one.
  vector<Extent> old_extents = {ExtentForRange(1, 1)};
  vector<Extent> new_extents = {ExtentForRange(2, 1)};

  ASSERT_TRUE(WriteExtents(old_part_.path, old_extents, kBlockSize, data_blob));
  // Modify one byte in the new file.
  data_blob[0]++;
  ASSERT_TRUE(WriteExtents(new_part_.path, new_extents, kBlockSize, data_blob));

  brillo::Blob data;
  AnnotatedOperation aop;
  ASSERT_TRUE(diff_utils::ReadExtentsToDiff(
      old_part_.path,
      new_part_.path,
      old_extents,
      new_extents,
      {},  // old_deflates
      {},  // new_deflates
      {.version = PayloadVersion(kBrilloMajorPayloadVersion,
                                 kSourceMinorPayloadVersion)},
      &data,
      &aop));
  auto& op = aop.op;
  ASSERT_FALSE(data.empty());
  ASSERT_TRUE(op.has_type());
  ASSERT_EQ(InstallOperation::SOURCE_BSDIFF, op.type());
}

TEST_F(DeltaDiffUtilsTest, BrotliBsdiffTest) {
  // Makes sure SOURCE_BSDIFF operations are emitted whenever src_ops_allowed
  // is true. It is the same setup as BsdiffSmallTest, which checks
  // that the operation is well-formed.
  brillo::Blob data_blob(kBlockSize);
  test_utils::FillWithData(&data_blob);

  // The old file is on a different block than the new one.
  vector<Extent> old_extents = {ExtentForRange(1, 1)};
  vector<Extent> new_extents = {ExtentForRange(2, 1)};

  ASSERT_TRUE(WriteExtents(old_part_.path, old_extents, kBlockSize, data_blob));
  // Modify one byte in the new file.
  data_blob[0]++;
  ASSERT_TRUE(WriteExtents(new_part_.path, new_extents, kBlockSize, data_blob));

  brillo::Blob data;
  AnnotatedOperation aop;

  std::vector<puffin::BitExtent> empty;
  PayloadGenerationConfig config{
      .version = PayloadVersion(kBrilloMajorPayloadVersion,
                                kBrotliBsdiffMinorPayloadVersion)};
  ASSERT_TRUE(diff_utils::ReadExtentsToDiff(old_part_.path,
                                            new_part_.path,
                                            old_extents,
                                            new_extents,
                                            {},  // old_file
                                            {},  // new_file
                                            config,
                                            &data,
                                            &aop));
  auto& op = aop.op;
  ASSERT_FALSE(data.empty());
  ASSERT_TRUE(op.has_type());
  ASSERT_EQ(InstallOperation::BROTLI_BSDIFF, op.type());
}

TEST_F(DeltaDiffUtilsTest, GenerateBestDiffOperation_Zucchini) {
  // Makes sure SOURCE_BSDIFF operations are emitted whenever src_ops_allowed
  // is true. It is the same setup as BsdiffSmallTest, which checks
  // that the operation is well-formed.
  brillo::Blob dst_data_blob(kBlockSize);
  test_utils::FillWithData(&dst_data_blob);

  // The old file is on a different block than the new one.
  vector<Extent> old_extents = {ExtentForRange(1, 1)};
  vector<Extent> new_extents = {ExtentForRange(2, 1)};

  ASSERT_TRUE(
      WriteExtents(old_part_.path, old_extents, kBlockSize, dst_data_blob));
  // Modify one byte in the new file.
  brillo::Blob src_data_blob = dst_data_blob;
  src_data_blob[0]++;
  ASSERT_TRUE(
      WriteExtents(new_part_.path, new_extents, kBlockSize, src_data_blob));

  brillo::Blob data = dst_data_blob;  // Fake the full operation
  AnnotatedOperation aop;
  // Zucchini is only enabled on files with certain extensions
  aop.name = "data.so";

  const FilesystemInterface::File empty;
  PayloadGenerationConfig config{
      .version = PayloadVersion(kBrilloMajorPayloadVersion,
                                kZucchiniMinorPayloadVersion)};
  diff_utils::BestDiffGenerator best_diff_generator(src_data_blob,
                                                    dst_data_blob,
                                                    old_extents,
                                                    new_extents,
                                                    empty,
                                                    empty,
                                                    config);
  ASSERT_TRUE(best_diff_generator.GenerateBestDiffOperation(
      {{InstallOperation::ZUCCHINI, 1024 * 1024}}, &aop, &data));

  auto& op = aop.op;
  ASSERT_FALSE(data.empty());
  ASSERT_TRUE(op.has_type());
  ASSERT_EQ(InstallOperation::ZUCCHINI, op.type());
}

TEST_F(DeltaDiffUtilsTest, GenerateBestDiffOperation_FullOperationBetter) {
  // Makes sure SOURCE_BSDIFF operations are emitted whenever src_ops_allowed
  // is true. It is the same setup as BsdiffSmallTest, which checks
  // that the operation is well-formed.
  brillo::Blob dst_data_blob(kBlockSize);
  test_utils::FillWithData(&dst_data_blob);

  // The old file is on a different block than the new one.
  vector<Extent> old_extents = {ExtentForRange(1, 1)};
  vector<Extent> new_extents = {ExtentForRange(2, 1)};

  ASSERT_TRUE(
      WriteExtents(old_part_.path, old_extents, kBlockSize, dst_data_blob));
  // Modify one byte in the new file.
  brillo::Blob src_data_blob = dst_data_blob;
  src_data_blob[0]++;
  ASSERT_TRUE(
      WriteExtents(new_part_.path, new_extents, kBlockSize, src_data_blob));

  brillo::Blob data(1);
  test_utils::FillWithData(&data);  // Fake the full operation
  AnnotatedOperation aop;
  aop.op.set_type(InstallOperation::REPLACE_XZ);

  const FilesystemInterface::File empty;
  PayloadGenerationConfig config{
      .version = PayloadVersion(kBrilloMajorPayloadVersion,
                                kZucchiniMinorPayloadVersion)};
  diff_utils::BestDiffGenerator best_diff_generator(src_data_blob,
                                                    dst_data_blob,
                                                    old_extents,
                                                    new_extents,
                                                    empty,
                                                    empty,
                                                    config);
  ASSERT_TRUE(best_diff_generator.GenerateBestDiffOperation(
      {{InstallOperation::ZUCCHINI, 1024 * 1024}}, &aop, &data));

  auto& op = aop.op;
  ASSERT_EQ(1u, data.size());
  ASSERT_TRUE(op.has_type());
  ASSERT_EQ(InstallOperation::REPLACE_XZ, op.type());
}

TEST_F(DeltaDiffUtilsTest, PreferReplaceTest) {
  brillo::Blob data_blob(kBlockSize);
  vector<Extent> extents = {ExtentForRange(1, 1)};

  // Write something in the first 50 bytes so that REPLACE_BZ will be slightly
  // larger than BROTLI_BSDIFF.
  std::iota(data_blob.begin(), data_blob.begin() + 50, 0);
  ASSERT_TRUE(WriteExtents(old_part_.path, extents, kBlockSize, data_blob));
  // Shift the first 50 bytes in the new file by one.
  std::iota(data_blob.begin(), data_blob.begin() + 50, 1);
  ASSERT_TRUE(WriteExtents(new_part_.path, extents, kBlockSize, data_blob));

  brillo::Blob data;
  AnnotatedOperation aop;

  const FilesystemInterface::File empty;
  PayloadGenerationConfig config{
      .version = PayloadVersion(kMaxSupportedMajorPayloadVersion,
                                kMaxSupportedMinorPayloadVersion)};
  ASSERT_TRUE(diff_utils::ReadExtentsToDiff(old_part_.path,
                                            new_part_.path,
                                            extents,
                                            extents,
                                            empty,  // old_file
                                            empty,  // new_file
                                            config,
                                            &data,
                                            &aop));
  auto& op = aop.op;
  ASSERT_FALSE(data.empty());
  ASSERT_TRUE(op.has_type());
  ASSERT_EQ(InstallOperation::REPLACE_BZ, op.type());
}

// Test the simple case where all the blocks are different and no new blocks are
// zeroed.
TEST_F(DeltaDiffUtilsTest, NoZeroedOrUniqueBlocksDetected) {
  InitializePartitionWithUniqueBlocks(old_part_, block_size_, 5);
  InitializePartitionWithUniqueBlocks(new_part_, block_size_, 42);

  ASSERT_TRUE(RunDeltaMovedAndZeroBlocks(-1,  // chunk_blocks
                                         kSourceMinorPayloadVersion));

  ASSERT_EQ(0U, old_visited_blocks_.blocks());
  ASSERT_EQ(0U, new_visited_blocks_.blocks());
  ASSERT_EQ(0, blob_size_);
  ASSERT_TRUE(aops_.empty());
}

// Test that when the partitions have identical blocks in the same positions
// MOVE operation is performed and all the blocks are handled.
TEST_F(DeltaDiffUtilsTest, IdenticalBlocksAreCopiedFromSource) {
  // We use a smaller partition for this test.
  old_part_.size = kBlockSize * 50;
  new_part_.size = kBlockSize * 50;

  InitializePartitionWithUniqueBlocks(old_part_, block_size_, 42);
  InitializePartitionWithUniqueBlocks(new_part_, block_size_, 42);

  // Mark some of the blocks as already visited.
  vector<Extent> already_visited = {ExtentForRange(5, 5),
                                    ExtentForRange(25, 7)};
  old_visited_blocks_.AddExtents(already_visited);
  new_visited_blocks_.AddExtents(already_visited);

  // Override some of the old blocks with different data.
  vector<Extent> different_blocks = {ExtentForRange(40, 5)};
  ASSERT_TRUE(WriteExtents(old_part_.path,
                           different_blocks,
                           kBlockSize,
                           brillo::Blob(5 * kBlockSize, 'a')));

  ASSERT_TRUE(RunDeltaMovedAndZeroBlocks(10,  // chunk_blocks
                                         kSourceMinorPayloadVersion));

  ExtentRanges expected_ranges;
  expected_ranges.AddExtent(ExtentForRange(0, 50));
  expected_ranges.SubtractExtents(different_blocks);

  ASSERT_EQ(expected_ranges.extent_set(), old_visited_blocks_.extent_set());
  ASSERT_EQ(expected_ranges.extent_set(), new_visited_blocks_.extent_set());
  ASSERT_EQ(0, blob_size_);

  // We expect all the blocks that we didn't override with |different_blocks|
  // and that we didn't mark as visited in |already_visited| to match and have a
  // SOURCE_COPY operation chunked at 10 blocks.
  vector<Extent> expected_op_extents = {
      ExtentForRange(0, 5),
      ExtentForRange(10, 10),
      ExtentForRange(20, 5),
      ExtentForRange(32, 8),
      ExtentForRange(45, 5),
  };

  ASSERT_EQ(expected_op_extents.size(), aops_.size());
  for (size_t i = 0; i < aops_.size() && i < expected_op_extents.size(); ++i) {
    SCOPED_TRACE(base::StringPrintf("Failed on operation number %" PRIuS, i));
    const AnnotatedOperation& aop = aops_[i];
    ASSERT_EQ(InstallOperation::SOURCE_COPY, aop.op.type());
    ASSERT_EQ(1, aop.op.src_extents_size());
    ASSERT_EQ(expected_op_extents[i], aop.op.src_extents(0));
    ASSERT_EQ(1, aop.op.dst_extents_size());
    ASSERT_EQ(expected_op_extents[i], aop.op.dst_extents(0));
  }
}

TEST_F(DeltaDiffUtilsTest, IdenticalBlocksAreCopiedInOder) {
  // We use a smaller partition for this test.
  old_part_.size = block_size_ * 50;
  new_part_.size = block_size_ * 50;

  // Create two identical partitions with 5 copies of the same unique "file".
  brillo::Blob file_data(block_size_ * 10, 'a');
  for (size_t offset = 0; offset < file_data.size(); offset += block_size_)
    file_data[offset] = 'a' + offset / block_size_;

  brillo::Blob partition_data(old_part_.size);
  for (size_t offset = 0; offset < partition_data.size();
       offset += file_data.size()) {
    std::copy(
        file_data.begin(), file_data.end(), partition_data.begin() + offset);
  }
  ASSERT_TRUE(test_utils::WriteFileVector(old_part_.path, partition_data));
  ASSERT_TRUE(test_utils::WriteFileVector(new_part_.path, partition_data));

  ASSERT_TRUE(RunDeltaMovedAndZeroBlocks(-1,  // chunk_blocks
                                         kSourceMinorPayloadVersion));

  // There should be only one SOURCE_COPY, for the whole partition and the
  // source extents should cover only the first copy of the source file since
  // we prefer to re-read files (maybe cached) instead of continue reading the
  // rest of the partition.
  ASSERT_EQ(1U, aops_.size());
  const AnnotatedOperation& aop = aops_[0];
  ASSERT_EQ(InstallOperation::SOURCE_COPY, aop.op.type());
  ASSERT_EQ(5, aop.op.src_extents_size());
  for (int i = 0; i < aop.op.src_extents_size(); ++i) {
    ASSERT_EQ(ExtentForRange(0, 10), aop.op.src_extents(i));
  }

  ASSERT_EQ(1, aop.op.dst_extents_size());
  ASSERT_EQ(ExtentForRange(0, 50), aop.op.dst_extents(0));

  ASSERT_EQ(0, blob_size_);
}

// Test that all blocks with zeros are handled separately using REPLACE_BZ
// operations unless they are not moved.
TEST_F(DeltaDiffUtilsTest, ZeroBlocksUseReplaceBz) {
  InitializePartitionWithUniqueBlocks(old_part_, block_size_, 42);
  InitializePartitionWithUniqueBlocks(new_part_, block_size_, 5);

  // We set four ranges of blocks with zeros: a single block, a range that fits
  // in the chunk size, a range that doesn't and finally a range of zeros that
  // was also zeros in the old image.
  vector<Extent> new_zeros = {
      ExtentForRange(10, 1),
      ExtentForRange(20, 4),
      // The last range is split since the old image has zeros in part of it.
      ExtentForRange(30, 20),
  };
  brillo::Blob zeros_data(utils::BlocksInExtents(new_zeros) * block_size_,
                          '\0');
  ASSERT_TRUE(WriteExtents(new_part_.path, new_zeros, block_size_, zeros_data));

  vector<Extent> old_zeros = vector<Extent>{ExtentForRange(43, 7)};
  ASSERT_TRUE(WriteExtents(old_part_.path, old_zeros, block_size_, zeros_data));

  ASSERT_TRUE(RunDeltaMovedAndZeroBlocks(5,  // chunk_blocks
                                         kSourceMinorPayloadVersion));

  // Zeroed blocks from |old_visited_blocks_| were copied over.
  ASSERT_EQ(old_zeros,
            old_visited_blocks_.GetExtentsForBlockCount(
                old_visited_blocks_.blocks()));

  // All the new zeroed blocks should be used with REPLACE_BZ.
  ASSERT_EQ(new_zeros,
            new_visited_blocks_.GetExtentsForBlockCount(
                new_visited_blocks_.blocks()));

  vector<Extent> expected_op_extents = {
      ExtentForRange(10, 1),
      ExtentForRange(20, 4),
      // This range should be split.
      ExtentForRange(30, 5),
      ExtentForRange(35, 5),
      ExtentForRange(40, 5),
      ExtentForRange(45, 5),
  };

  ASSERT_EQ(expected_op_extents.size(), aops_.size());
  for (size_t i = 0; i < aops_.size() && i < expected_op_extents.size(); ++i) {
    SCOPED_TRACE(base::StringPrintf("Failed on operation number %" PRIuS, i));
    const AnnotatedOperation& aop = aops_[i];
    ASSERT_EQ(InstallOperation::REPLACE_BZ, aop.op.type());
    ASSERT_EQ(0, aop.op.src_extents_size());
    ASSERT_EQ(1, aop.op.dst_extents_size());
    ASSERT_EQ(expected_op_extents[i], aop.op.dst_extents(0));
  }
  ASSERT_NE(0, blob_size_);
}

TEST_F(DeltaDiffUtilsTest, ShuffledBlocksAreTracked) {
  vector<uint64_t> permutation = {0, 1, 5, 6, 7, 2, 3, 4, 9, 10, 11, 12, 8};
  vector<Extent> perm_extents;
  for (uint64_t x : permutation)
    AppendBlockToExtents(&perm_extents, x);

  // We use a smaller partition for this test.
  old_part_.size = block_size_ * permutation.size();
  new_part_.size = block_size_ * permutation.size();
  InitializePartitionWithUniqueBlocks(new_part_, block_size_, 123);

  // We initialize the old_part_ with the blocks from new_part but in the
  // |permutation| order. Block i in the old_part_ will contain the same data
  // as block permutation[i] in the new_part_.
  brillo::Blob new_contents;
  ASSERT_TRUE(utils::ReadFile(new_part_.path, &new_contents));
  ASSERT_TRUE(
      WriteExtents(old_part_.path, perm_extents, block_size_, new_contents));

  ASSERT_TRUE(RunDeltaMovedAndZeroBlocks(-1,  // chunk_blocks
                                         kSourceMinorPayloadVersion));

  ASSERT_EQ(permutation.size(), old_visited_blocks_.blocks());
  ASSERT_EQ(permutation.size(), new_visited_blocks_.blocks());

  // There should be only one SOURCE_COPY, with a complicate list of extents.
  ASSERT_EQ(1U, aops_.size());
  const AnnotatedOperation& aop = aops_[0];
  ASSERT_EQ(InstallOperation::SOURCE_COPY, aop.op.type());
  vector<Extent> aop_src_extents;
  ExtentsToVector(aop.op.src_extents(), &aop_src_extents);
  ASSERT_EQ(perm_extents, aop_src_extents);

  ASSERT_EQ(1, aop.op.dst_extents_size());
  ASSERT_EQ(ExtentForRange(0, permutation.size()), aop.op.dst_extents(0));

  ASSERT_EQ(0, blob_size_);
}

TEST_F(DeltaDiffUtilsTest, IsExtFilesystemTest) {
  ASSERT_TRUE(diff_utils::IsExtFilesystem(
      test_utils::GetBuildArtifactsPath("gen/disk_ext2_1k.img")));
  ASSERT_TRUE(diff_utils::IsExtFilesystem(
      test_utils::GetBuildArtifactsPath("gen/disk_ext2_4k.img")));
}

TEST_F(DeltaDiffUtilsTest, GetOldFileEmptyTest) {
  ASSERT_TRUE(diff_utils::GetOldFile({}, "filename").name.empty());
}

TEST_F(DeltaDiffUtilsTest, GetOldFileTest) {
  std::map<string, FilesystemInterface::File> old_files_map;
  auto file_list = {
      "filename",
      "filename.zip",
      "version1.1",
      "version2.0",
      "version",
      "update_engine",
      "delta_generator",
  };
  for (const auto& name : file_list) {
    FilesystemInterface::File file;
    file.name = name;
    old_files_map.emplace(name, file);
  }

  // Always return exact match if possible.
  for (const auto& name : file_list)
    ASSERT_EQ(diff_utils::GetOldFile(old_files_map, name).name, name);

  ASSERT_EQ(diff_utils::GetOldFile(old_files_map, "file_name").name,
            "filename");
  ASSERT_EQ(diff_utils::GetOldFile(old_files_map, "filename_new.zip").name,
            "filename.zip");
  ASSERT_EQ(diff_utils::GetOldFile(old_files_map, "version1.2").name,
            "version1.1");
  ASSERT_EQ(diff_utils::GetOldFile(old_files_map, "version3.0").name,
            "version2.0");
  ASSERT_EQ(diff_utils::GetOldFile(old_files_map, "_version").name, "version");
  ASSERT_EQ(
      diff_utils::GetOldFile(old_files_map, "update_engine_unittest").name,
      "update_engine");
  ASSERT_EQ(diff_utils::GetOldFile(old_files_map, "bin/delta_generator").name,
            "delta_generator");
  // Check file name with minimum size.
  ASSERT_EQ(diff_utils::GetOldFile(old_files_map, "a").name, "filename");
}

TEST_F(DeltaDiffUtilsTest, XorOpsSourceNotAligned) {
  ScopedTempFile patch_file;
  bsdiff::BsdiffPatchWriter writer{patch_file.path()};
  ASSERT_TRUE(writer.Init(kBlockSize * 10));
  ASSERT_TRUE(writer.AddControlEntry(ControlEntry(0, 0, 123 + kBlockSize)));
  ASSERT_TRUE(writer.AddControlEntry(ControlEntry(kBlockSize, 0, 0)));
  ASSERT_TRUE(writer.Close());

  std::string patch_data;
  utils::ReadFile(patch_file.path(), &patch_data);

  AnnotatedOperation aop;
  *aop.op.add_src_extents() = ExtentForRange(50, 10);
  *aop.op.add_dst_extents() = ExtentForRange(500, 10);

  diff_utils::PopulateXorOps(
      &aop,
      reinterpret_cast<const uint8_t*>(patch_data.data()),
      patch_data.size());
  for (const auto& op : aop.xor_ops) {
    ASSERT_EQ(op.type(), CowMergeOperation::COW_XOR);
  }
  ASSERT_EQ(aop.xor_ops.size(), 1UL) << "Only 1 block can possibly be XORed";
  ASSERT_EQ(aop.xor_ops[0].src_extent().num_blocks(), 1UL);
  ASSERT_EQ(aop.xor_ops[0].src_extent().start_block(), 51UL);
  ASSERT_EQ(aop.xor_ops[0].src_offset(), 123UL);

  ASSERT_EQ(aop.xor_ops[0].dst_extent().num_blocks(), 1UL);
  ASSERT_EQ(aop.xor_ops[0].dst_extent().start_block(), 500UL);
}

TEST_F(DeltaDiffUtilsTest, XorOpsTargetNotAligned) {
  ScopedTempFile patch_file;
  bsdiff::BsdiffPatchWriter writer{patch_file.path()};
  ASSERT_TRUE(writer.Init(kBlockSize * 10));
  ASSERT_TRUE(writer.AddControlEntry(
      ControlEntry(0, kBlockSize - 456, 123 + kBlockSize)));
  ASSERT_TRUE(writer.AddControlEntry(ControlEntry(kBlockSize + 456, 0, 0)));
  ASSERT_TRUE(writer.Close());

  std::string patch_data;
  utils::ReadFile(patch_file.path(), &patch_data);

  AnnotatedOperation aop;
  *aop.op.add_src_extents() = ExtentForRange(50, 10);
  *aop.op.add_dst_extents() = ExtentForRange(500, 10);

  diff_utils::PopulateXorOps(
      &aop,
      reinterpret_cast<const uint8_t*>(patch_data.data()),
      patch_data.size());
  for (const auto& op : aop.xor_ops) {
    ASSERT_EQ(op.type(), CowMergeOperation::COW_XOR);
  }
  ASSERT_EQ(aop.xor_ops.size(), 1UL) << "Only 1 block can possibly be XORed";
  ASSERT_EQ(aop.xor_ops[0].src_extent().num_blocks(), 1UL);
  ASSERT_EQ(aop.xor_ops[0].src_extent().start_block(), 51UL);
  ASSERT_EQ(aop.xor_ops[0].src_offset(), 123UL + 456UL);

  ASSERT_EQ(aop.xor_ops[0].dst_extent().num_blocks(), 1UL);
  ASSERT_EQ(aop.xor_ops[0].dst_extent().start_block(), 501UL);
}

TEST_F(DeltaDiffUtilsTest, XorOpsStrided) {
  ScopedTempFile patch_file;
  bsdiff::BsdiffPatchWriter writer{patch_file.path()};
  ASSERT_TRUE(writer.Init(kBlockSize * 10));
  ASSERT_TRUE(writer.AddControlEntry(ControlEntry(0, kBlockSize - 456, 123)));
  ASSERT_TRUE(
      writer.AddControlEntry(ControlEntry(kBlockSize * 10 + 456, 0, 0)));
  ASSERT_TRUE(writer.Close());

  std::string patch_data;
  utils::ReadFile(patch_file.path(), &patch_data);

  AnnotatedOperation aop;
  *aop.op.add_src_extents() = ExtentForRange(50, 5);
  *aop.op.add_src_extents() = ExtentForRange(60, 5);

  *aop.op.add_dst_extents() = ExtentForRange(500, 2);
  *aop.op.add_dst_extents() = ExtentForRange(600, 2);
  *aop.op.add_dst_extents() = ExtentForRange(700, 7);

  diff_utils::PopulateXorOps(
      &aop,
      reinterpret_cast<const uint8_t*>(patch_data.data()),
      patch_data.size());
  ASSERT_EQ(aop.xor_ops.size(), 4UL);
  for (const auto& op : aop.xor_ops) {
    ASSERT_EQ(op.type(), CowMergeOperation::COW_XOR);
  }
  for (const auto& op : aop.xor_ops) {
    ASSERT_EQ(op.src_offset(), 123UL + 456UL);
    LOG(INFO) << op.src_extent() << ", " << op.dst_extent();
  }
  ASSERT_EQ(aop.xor_ops[0].src_extent().num_blocks(), 2UL);
  ASSERT_EQ(aop.xor_ops[0].src_extent().start_block(), 50UL);

  ASSERT_EQ(aop.xor_ops[0].dst_extent().num_blocks(), 1UL);
  ASSERT_EQ(aop.xor_ops[0].dst_extent().start_block(), 501UL);

  ASSERT_EQ(aop.xor_ops[1].src_extent().num_blocks(), 3UL);
  ASSERT_EQ(aop.xor_ops[1].src_extent().start_block(), 51UL);

  ASSERT_EQ(aop.xor_ops[1].dst_extent().num_blocks(), 2UL);
  ASSERT_EQ(aop.xor_ops[1].dst_extent().start_block(), 600UL);

  ASSERT_EQ(aop.xor_ops[2].src_extent().num_blocks(), 3UL);
  ASSERT_EQ(aop.xor_ops[2].src_extent().start_block(), 53UL);

  ASSERT_EQ(aop.xor_ops[2].dst_extent().num_blocks(), 2UL);
  ASSERT_EQ(aop.xor_ops[2].dst_extent().start_block(), 700UL);

  ASSERT_EQ(aop.xor_ops[3].src_extent().num_blocks(), 6UL);
  ASSERT_EQ(aop.xor_ops[3].src_extent().start_block(), 60UL);

  ASSERT_EQ(aop.xor_ops[3].dst_extent().num_blocks(), 5UL);
  ASSERT_EQ(aop.xor_ops[3].dst_extent().start_block(), 702UL);
}

}  // namespace chromeos_update_engine
