// Copyright (C) 2019 Google LLC
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

#include "icing/file/file-backed-bitmap.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {
namespace {

using ::testing::_;
using ::testing::HasSubstr;

class FileBackedBitmapTest : public testing::Test {
 protected:
  static int GetBlockCapacity(int num_blocks) {
    return FileBackedBitmap::GetBlockCapacity(num_blocks);
  }

  void SetUp() override { file_path_ = GetTestTempDir() + "/bitmap"; }

  void TearDown() override { filesystem_.DeleteFile(file_path_.c_str()); }

  std::string file_path_;
  Filesystem filesystem_;
  MemoryMappedFile::Strategy mmap_strategy_ =
      MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC;
};

// TODO(cassiewang) Add tests for other corruption scenarios where the
// file has an invalid checksum or is dirty on init.
TEST_F(FileBackedBitmapTest, InvalidFile) {
  ASSERT_THAT(FileBackedBitmap::Create(&filesystem_, "", mmap_strategy_)
                  .status()
                  .error_message(),
              HasSubstr("Unable to create"));

  std::string bad_data = "Bad bitmap file content";
  filesystem_.Write(file_path_.c_str(), bad_data.data(), bad_data.size());

  ASSERT_THAT(FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_)
                  .status()
                  .error_message(),
              HasSubstr("corrupted"));
}

TEST_F(FileBackedBitmapTest, CreateNewBitMap) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedBitmap> bitmap,
      FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_));
  // Even a new bitmap will have 1 block with pre-allocated bits.
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(1));
  EXPECT_THAT(bitmap->Get(0), IsOkAndHolds(false));
  ICING_EXPECT_OK(bitmap->PersistToDisk());
}

TEST_F(FileBackedBitmapTest, CanReadAfterWrite) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedBitmap> bitmap,
      FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_));

  // Can Grow bitmap to store both true and false.
  ICING_EXPECT_OK(bitmap->Set(100000, true));
  ICING_EXPECT_OK(bitmap->Set(200000, false));

  EXPECT_THAT(bitmap->Get(0), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(100000), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(200000), IsOkAndHolds(false));

  // Can write new data without growing the bitmap.
  ICING_EXPECT_OK(bitmap->Set(50, true));
  EXPECT_THAT(bitmap->Get(50), IsOkAndHolds(true));

  // Can modify the value of a previously written bit.
  ICING_EXPECT_OK(bitmap->Set(100000, false));
  ICING_EXPECT_OK(bitmap->Set(200000, true));
  EXPECT_THAT(bitmap->Get(100000), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(200000), IsOkAndHolds(true));
}

// Make sure that the growth of the bitmap is in multiples of 4KiB blocks.
// This is required to keep our memory mapping efficient.
TEST_F(FileBackedBitmapTest, BitMapGrowsInMultipleOfBlocks) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedBitmap> bitmap,
      FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(1));
  EXPECT_THAT(bitmap->Get(GetBlockCapacity(1) - 1), IsOkAndHolds(false));

  // 100K bits need four 4KiB sized blocks.
  ICING_EXPECT_OK(bitmap->Set(100000, true));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(4));

  // 200K bits need seven 4KiB sized blocks.
  ICING_EXPECT_OK(bitmap->Set(200000, false));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(7));

  // Reusing pre-allocated bits doesn't require any growth.
  ICING_EXPECT_OK(bitmap->Set(0, false));
  ICING_EXPECT_OK(bitmap->Set(1000, false));
  ICING_EXPECT_OK(bitmap->Set(GetBlockCapacity(7) - 1, false));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(7));
}

TEST_F(FileBackedBitmapTest, CanPersistBitmapToDiskRegularly) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedBitmap> bitmap,
      FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_));

  // Can read older data after PersistToDisk.
  ICING_EXPECT_OK(bitmap->Set(100, true));
  ICING_EXPECT_OK(bitmap->PersistToDisk());
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(1));
  EXPECT_THAT(bitmap->Get(100), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(50), IsOkAndHolds(false));

  // Can grow the bitmap to write new data after PersistToDisk.
  ICING_EXPECT_OK(bitmap->Set(50000, false));
  ICING_EXPECT_OK(bitmap->PersistToDisk());
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(2));
  EXPECT_THAT(bitmap->Get(100), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(50000), IsOkAndHolds(false));

  // Can write new data without growing the bitmap after PersistToDisk.
  ICING_EXPECT_OK(bitmap->Set(50, true));
  ICING_EXPECT_OK(bitmap->PersistToDisk());
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(2));
  EXPECT_THAT(bitmap->Get(50), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(100), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(50000), IsOkAndHolds(false));
}

TEST_F(FileBackedBitmapTest, BitmapUsableAcrossMultipleInstances) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedBitmap> bitmap,
      FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_));
  ICING_EXPECT_OK(bitmap->Set(100, true));

  // Persist all data and reset the bitmap instance.
  ICING_EXPECT_OK(bitmap->PersistToDisk());
  bitmap.reset();
  ICING_ASSERT_OK_AND_ASSIGN(
      bitmap,
      FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(1));
  EXPECT_THAT(bitmap->Get(0), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(100), IsOkAndHolds(true));

  // Reset the bitmap instance without explicitly persisting data.
  // Even here, the system should flush the data, unless the device reboots.
  bitmap.reset();
  ICING_ASSERT_OK_AND_ASSIGN(
      bitmap,
      FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(1));
  EXPECT_THAT(bitmap->Get(0), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(100), IsOkAndHolds(true));

  // We can continue to read/write bits on an existing data.
  ICING_EXPECT_OK(bitmap->Set(200, false));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(1));
  EXPECT_THAT(bitmap->Get(200), IsOkAndHolds(false));
}

TEST_F(FileBackedBitmapTest, HandleOutOfRangeReads) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedBitmap> bitmap,
      FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_));
  // The bitmap initially has 4K bits pre-allocated.
  EXPECT_THAT(bitmap->Get(0), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(GetBlockCapacity(1) - 1), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(GetBlockCapacity(1)),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(bitmap->Get(GetBlockCapacity(2)),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  // Expand bitmap to use 2 blocks.
  ICING_EXPECT_OK(bitmap->Set(GetBlockCapacity(1) + 100, true));
  EXPECT_THAT(bitmap->Get(GetBlockCapacity(1) + 1), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(GetBlockCapacity(2) - 1), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(GetBlockCapacity(2) + 1),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(bitmap->Get(GetBlockCapacity(5)),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  ICING_EXPECT_OK(bitmap->Set(100000, true));
  EXPECT_THAT(bitmap->Get(100000), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(GetBlockCapacity(5)),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
}

TEST_F(FileBackedBitmapTest, TruncateBitmap) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedBitmap> bitmap,
      FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_));

  // NOTE: Set uses zero-based index. So, Set(99) sets the 100th bit.
  ICING_EXPECT_OK(bitmap->Set(99, true));
  ICING_EXPECT_OK(bitmap->Set(100, true));
  ICING_EXPECT_OK(bitmap->Set(101, true));

  // Bitmap has fewer bits. So, nothing to truncate.
  ICING_EXPECT_OK(bitmap->TruncateTo(200));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(1));
  EXPECT_THAT(bitmap->Get(99), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(100), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(101), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(199), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(200), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(201), IsOkAndHolds(false));

  // Bitmap size doesn't have any set bits beyond the requested size.
  // So, nothing to truncate.
  ICING_EXPECT_OK(bitmap->TruncateTo(102));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(1));
  EXPECT_THAT(bitmap->Get(99), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(100), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(101), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(102), IsOkAndHolds(false));

  // TruncateTo(100) should reduce the size of the bitmap to 100.
  ICING_EXPECT_OK(bitmap->TruncateTo(100));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(1));
  EXPECT_THAT(bitmap->Get(99), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(100), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(101), IsOkAndHolds(false));
}

TEST_F(FileBackedBitmapTest, TruncateBitmapAcrossMultipleBlocks) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedBitmap> bitmap,
      FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_));
  ICING_EXPECT_OK(bitmap->Set(100, true));
  ICING_EXPECT_OK(bitmap->Set(1000, true));
  ICING_EXPECT_OK(bitmap->Set(100000, true));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(4));

  // Bitmap only has 100K bits. So, nothing to truncate.
  ICING_EXPECT_OK(bitmap->TruncateTo(200000));
  EXPECT_THAT(bitmap->Get(100), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(1000), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(100000), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(4));

  // Bitmap only has 100K bits. So, nothing to truncate.
  ICING_EXPECT_OK(bitmap->TruncateTo(100001));
  EXPECT_THAT(bitmap->Get(100), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(1000), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(100000), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(4));

  // Truncate to 50K should chop off block#3 and block#4.
  // It should also clear bits beyond 50K.
  ICING_EXPECT_OK(bitmap->Set(49999, true));
  ICING_EXPECT_OK(bitmap->Set(50000, true));
  ICING_EXPECT_OK(bitmap->Set(50001, true));
  ICING_EXPECT_OK(bitmap->TruncateTo(50000));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(2));
  EXPECT_THAT(bitmap->Get(100), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(1000), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(49999), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(50000), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(50001), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(100000),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  // Truncate to 2K should chop off the 2nd block.
  ICING_EXPECT_OK(bitmap->TruncateTo(2000));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(1));
  EXPECT_THAT(bitmap->Get(100), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(1000), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(50000),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(bitmap->Get(100000),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  // Truncate to 500 not chop any blocks off, but clear subsequent bits.
  ICING_EXPECT_OK(bitmap->TruncateTo(500));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(1));
  EXPECT_THAT(bitmap->Get(100), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(1000), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(50000),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(bitmap->Get(100000),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
}

TEST_F(FileBackedBitmapTest, TruncateBitmapAcrossInstances) {
  // Instance#1
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedBitmap> bitmap,
      FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_));
  ICING_EXPECT_OK(bitmap->Set(100, true));
  ICING_EXPECT_OK(bitmap->Set(1000, true));
  ICING_EXPECT_OK(bitmap->Set(100000, true));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(4));

  // Instance#2
  bitmap.reset();
  ICING_ASSERT_OK_AND_ASSIGN(
      bitmap,
      FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_));
  ICING_EXPECT_OK(bitmap->TruncateTo(50000));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(2));
  EXPECT_THAT(bitmap->Get(100), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(1000), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(49999), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(50000), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(100000),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  // Instance#3
  bitmap.reset();
  ICING_ASSERT_OK_AND_ASSIGN(
      bitmap,
      FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_));
  ICING_EXPECT_OK(bitmap->TruncateTo(500));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(1));
  EXPECT_THAT(bitmap->Get(100), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(500), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(1000), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(100000),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
}

// Make sure that a bitmap can both grow and be truncated many times.
TEST_F(FileBackedBitmapTest, TruncateAndGrowBitmap) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedBitmap> bitmap,
      FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_));

  // Grow#1
  ICING_EXPECT_OK(bitmap->Set(1000, true));
  ICING_EXPECT_OK(bitmap->Set(100000, true));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(4));

  // Truncate#1
  ICING_EXPECT_OK(bitmap->TruncateTo(50000));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(2));
  EXPECT_THAT(bitmap->Get(1000), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(100000),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  // Grow#2
  ICING_EXPECT_OK(bitmap->Set(200000, true));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(7));
  EXPECT_THAT(bitmap->Get(1000), IsOkAndHolds(true));
  EXPECT_THAT(bitmap->Get(100000), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(200000), IsOkAndHolds(true));

  // Truncate#2
  ICING_EXPECT_OK(bitmap->TruncateTo(1000));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(1));
  EXPECT_THAT(bitmap->Get(1000), IsOkAndHolds(false));
  EXPECT_THAT(bitmap->Get(100000),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(bitmap->Get(200000),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
}

TEST_F(FileBackedBitmapTest, BitMapCantGrowTooBigInSize) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedBitmap> bitmap,
      FileBackedBitmap::Create(&filesystem_, file_path_, mmap_strategy_));
  // Set a bit is within the 2M bit limit.
  ICING_EXPECT_OK(bitmap->Set(1000, true));

  // Go beyond the 2M bit limit.
  EXPECT_THAT(bitmap->Set(3 * 1024 * 1024, true),
              StatusIs(libtextclassifier3::StatusCode::RESOURCE_EXHAUSTED));

  // Subsequent calls work fine.
  ICING_EXPECT_OK(bitmap->Set(2000, true));
  EXPECT_THAT(bitmap->NumBits(), GetBlockCapacity(1));
}

}  // namespace
}  // namespace lib
}  // namespace icing
