// Copyright (C) 2022 Google LLC
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

#include "icing/file/memory-mapped-file.h"

#include <cstdint>
#include <limits>
#include <string>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "icing/file/mock-filesystem.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

using ::testing::DoDefault;
using ::testing::Eq;
using ::testing::Gt;
using ::testing::IsNull;
using ::testing::Le;
using ::testing::Not;
using ::testing::NotNull;
using ::testing::Return;

namespace icing {
namespace lib {

namespace {

class MemoryMappedFileTest : public ::testing::Test {
 protected:
  void SetUp() override { file_path_ = GetTestTempDir() + "/mmap_test_file"; }

  void TearDown() override { filesystem_.DeleteFile(file_path_.c_str()); }

  const Filesystem& filesystem() const { return filesystem_; }

  Filesystem filesystem_;
  std::string file_path_;
};

TEST_F(MemoryMappedFileTest, Create) {
  constexpr int max_file_size = 8192;
  MemoryMappedFile::Strategy stragegy =
      MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC;
  // Create MemoryMappedFile
  ICING_ASSERT_OK_AND_ASSIGN(MemoryMappedFile mmapped_file,
                             MemoryMappedFile::Create(filesystem_, file_path_,
                                                      stragegy, max_file_size));

  EXPECT_THAT(mmapped_file.strategy(), Eq(stragegy));
  EXPECT_THAT(mmapped_file.max_file_size(), Eq(max_file_size));
  EXPECT_THAT(mmapped_file.region(), IsNull());
  EXPECT_THAT(mmapped_file.mutable_region(), IsNull());
  EXPECT_THAT(mmapped_file.file_offset(), Eq(0));
  EXPECT_THAT(mmapped_file.region_size(), Eq(0));
  EXPECT_THAT(mmapped_file.available_size(), Eq(0));
}

TEST_F(MemoryMappedFileTest, CreateFromExistingFile) {
  int init_file_size = 100;
  {
    // Initialize file
    ScopedFd sfd(filesystem_.OpenForWrite(file_path_.c_str()));
    ASSERT_TRUE(sfd.is_valid());
    auto buf = std::make_unique<char[]>(init_file_size);
    ASSERT_TRUE(filesystem_.Write(sfd.get(), buf.get(), init_file_size));
  }

  constexpr int max_file_size = 8192;
  MemoryMappedFile::Strategy stragegy =
      MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC;
  // Create MemoryMappedFile from an existing file
  ICING_ASSERT_OK_AND_ASSIGN(MemoryMappedFile mmapped_file,
                             MemoryMappedFile::Create(filesystem_, file_path_,
                                                      stragegy, max_file_size));

  EXPECT_THAT(mmapped_file.strategy(), Eq(stragegy));
  EXPECT_THAT(mmapped_file.max_file_size(), Eq(max_file_size));
  EXPECT_THAT(mmapped_file.region(), IsNull());
  EXPECT_THAT(mmapped_file.mutable_region(), IsNull());
  EXPECT_THAT(mmapped_file.file_offset(), Eq(0));
  EXPECT_THAT(mmapped_file.region_size(), Eq(0));
  EXPECT_THAT(mmapped_file.available_size(), Eq(0));
}

TEST_F(MemoryMappedFileTest, CreateWithInvalidMaxFileSize) {
  EXPECT_THAT(
      MemoryMappedFile::Create(filesystem_, file_path_,
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               /*max_file_size=*/-1),
      StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(MemoryMappedFile::Create(
                  filesystem_, file_path_,
                  MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                  /*max_file_size=*/MemoryMappedFile::kMaxFileSize + 1),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  EXPECT_THAT(MemoryMappedFile::Create(
                  filesystem_, file_path_,
                  MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                  /*max_file_size=*/-1, /*pre_mapping_file_offset=*/0,
                  /*pre_mapping_mmap_size=*/8192),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(
      MemoryMappedFile::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
          /*max_file_size=*/MemoryMappedFile::kMaxFileSize + 1,
          /*pre_mapping_file_offset=*/0, /*pre_mapping_mmap_size=*/8192),
      StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
}

TEST_F(MemoryMappedFileTest, CreateWithPreMappingInfo) {
  constexpr int max_file_size = 8192;
  constexpr int pre_mapping_file_offset = 99;
  constexpr int pre_mapping_mmap_size = 2000;
  MemoryMappedFile::Strategy stragegy =
      MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC;
  // Create MemoryMappedFile with pre-mapping file_offset and mmap_size
  ICING_ASSERT_OK_AND_ASSIGN(
      MemoryMappedFile mmapped_file,
      MemoryMappedFile::Create(filesystem_, file_path_, stragegy, max_file_size,
                               pre_mapping_file_offset, pre_mapping_mmap_size));

  EXPECT_THAT(mmapped_file.strategy(), Eq(stragegy));
  EXPECT_THAT(mmapped_file.max_file_size(), Eq(max_file_size));
  EXPECT_THAT(mmapped_file.region(), NotNull());
  EXPECT_THAT(mmapped_file.mutable_region(), NotNull());
  EXPECT_THAT(mmapped_file.file_offset(), Eq(pre_mapping_file_offset));
  EXPECT_THAT(mmapped_file.region_size(), Eq(pre_mapping_mmap_size));
  EXPECT_THAT(mmapped_file.available_size(), Eq(0));

  // Manually grow the file externally and mutate region. There should be no
  // memory error.
  {
    ScopedFd sfd(filesystem_.OpenForAppend(file_path_.c_str()));
    ASSERT_TRUE(sfd.is_valid());
    int grow_size = 4096;
    auto buf = std::make_unique<char[]>(grow_size);
    ASSERT_TRUE(filesystem_.Write(sfd.get(), buf.get(), grow_size));
  }
  mmapped_file.mutable_region()[0] = 'a';
  ICING_EXPECT_OK(mmapped_file.PersistToDisk());

  {
    ScopedFd sfd(filesystem_.OpenForRead(file_path_.c_str()));
    ASSERT_TRUE(sfd.is_valid());
    int buf_size = 10;
    auto buf = std::make_unique<char[]>(buf_size);
    ASSERT_TRUE(filesystem_.PRead(sfd.get(), buf.get(), buf_size,
                                  pre_mapping_file_offset));
    EXPECT_THAT(buf.get()[0], Eq('a'));
  }
}

TEST_F(MemoryMappedFileTest, CreateWithInvalidPreMappingInfo) {
  int page_size = MemoryMappedFile::system_page_size();
  int max_file_size = page_size * 2;

  // Negative file_offset
  EXPECT_THAT(
      MemoryMappedFile::Create(filesystem_, file_path_,
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               max_file_size,
                               /*pre_mapping_file_offset=*/-1,
                               /*pre_mapping_mmap_size=*/page_size),
      StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  // Negative mmap_size
  EXPECT_THAT(
      MemoryMappedFile::Create(filesystem_, file_path_,
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               max_file_size, /*pre_mapping_file_offset=*/0,
                               /*pre_mapping_mmap_size=*/-1),
      StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  // pre_mapping_file_offset + pre_mapping_mmap_size > max_file_size.
  int pre_mapping_file_offset = 99;
  int pre_mapping_mmap_size = max_file_size - pre_mapping_file_offset + 1;
  EXPECT_THAT(
      MemoryMappedFile::Create(filesystem_, file_path_,
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               max_file_size, pre_mapping_file_offset,
                               pre_mapping_mmap_size),
      StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  // Edge cases to make sure the implementation of range check won't have
  // integer overflow bug.
  EXPECT_THAT(
      MemoryMappedFile::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, max_file_size,
          /*pre_mapping_file_offset=*/99,
          /*pre_mapping_mmap_size=*/std::numeric_limits<int64_t>::max()),
      StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(MemoryMappedFile::Create(
                  filesystem_, file_path_,
                  MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                  max_file_size, /*pre_mapping_file_offset=*/0,
                  /*pre_mapping_mmap_size=*/INT64_C(-1) *
                      (std::numeric_limits<int64_t>::max() - max_file_size)),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(
      MemoryMappedFile::Create(
          filesystem_, file_path_,
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, max_file_size,
          /*pre_mapping_file_offset=*/INT64_C(-1) *
              (std::numeric_limits<int64_t>::max() - max_file_size),
          /*pre_mapping_mmap_size=*/page_size),
      StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
}

// TODO(b/247671531): remove this test after deprecating Remap
TEST_F(MemoryMappedFileTest, RemapZeroMmapSizeShouldUnmap) {
  // Create MemoryMappedFile
  ICING_ASSERT_OK_AND_ASSIGN(
      MemoryMappedFile mmapped_file,
      MemoryMappedFile::Create(filesystem_, file_path_,
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               MemoryMappedFile::kDefaultMaxFileSize));

  int page_size = MemoryMappedFile::system_page_size();
  int file_offset = 99;
  int mmap_size = page_size * 2 - file_offset;
  ICING_ASSERT_OK(mmapped_file.Remap(file_offset, mmap_size));
  ASSERT_THAT(mmapped_file.region(), NotNull());

  // Call GrowAndRemapIfNecessary with any file_offset and new_mmap_size = 0.
  // The original mmapped region should be unmapped.
  ICING_EXPECT_OK(mmapped_file.Remap(file_offset, /*mmap_size=*/0));
  EXPECT_THAT(mmapped_file.region(), IsNull());
}

TEST_F(MemoryMappedFileTest, GrowAndRemapIfNecessary) {
  int page_size = MemoryMappedFile::system_page_size();
  int pre_mapping_file_offset = 99;
  int pre_mapping_mmap_size = page_size * 2 - pre_mapping_file_offset;
  {
    // Create MemoryMappedFile with pre-mapping file_offset and mmap_size
    // without growing the file.
    ICING_ASSERT_OK_AND_ASSIGN(
        MemoryMappedFile mmapped_file,
        MemoryMappedFile::Create(
            filesystem_, file_path_,
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
            MemoryMappedFile::kDefaultMaxFileSize, pre_mapping_file_offset,
            pre_mapping_mmap_size));
    ASSERT_THAT(filesystem_.GetFileSize(file_path_.c_str()), Eq(0));
    const char* original_region = mmapped_file.region();

    // Call GrowAndRemapIfNecessary with same file_offset and new mmap_size that
    // doesn't exceed pre_mapping_mmap_size. The underlying file size should
    // grow correctly, but there should be no remap.
    int new_mmap_size1 = page_size - pre_mapping_file_offset;
    ICING_EXPECT_OK(mmapped_file.GrowAndRemapIfNecessary(
        pre_mapping_file_offset, new_mmap_size1));

    EXPECT_THAT(filesystem_.GetFileSize(file_path_.c_str()),
                Eq(pre_mapping_file_offset + new_mmap_size1));
    EXPECT_THAT(mmapped_file.region(), Eq(original_region));
    EXPECT_THAT(mmapped_file.mutable_region(), Eq(original_region));
    EXPECT_THAT(mmapped_file.file_offset(), Eq(pre_mapping_file_offset));
    EXPECT_THAT(mmapped_file.region_size(), Eq(pre_mapping_mmap_size));
    EXPECT_THAT(mmapped_file.available_size(), Eq(new_mmap_size1));

    // Test it with new_mmap_size2 = pre_mapping_mmap_size
    int new_mmap_size2 = pre_mapping_mmap_size;
    ICING_EXPECT_OK(mmapped_file.GrowAndRemapIfNecessary(
        pre_mapping_file_offset, new_mmap_size2));

    EXPECT_THAT(filesystem_.GetFileSize(file_path_.c_str()),
                Eq(pre_mapping_file_offset + new_mmap_size2));
    EXPECT_THAT(mmapped_file.region(), Eq(original_region));
    EXPECT_THAT(mmapped_file.mutable_region(), Eq(original_region));
    EXPECT_THAT(mmapped_file.file_offset(), Eq(pre_mapping_file_offset));
    EXPECT_THAT(mmapped_file.region_size(), Eq(pre_mapping_mmap_size));
    EXPECT_THAT(mmapped_file.available_size(), Eq(new_mmap_size2));

    // Write some bytes to region()[0]. It should write the underlying file at
    // file_offset.
    mmapped_file.mutable_region()[0] = 'a';
    ICING_ASSERT_OK(mmapped_file.PersistToDisk());
  }

  ScopedFd sfd(filesystem_.OpenForRead(file_path_.c_str()));
  ASSERT_TRUE(sfd.is_valid());
  int buf_size = 1;
  auto buf = std::make_unique<char[]>(buf_size);
  ASSERT_TRUE(filesystem_.PRead(sfd.get(), buf.get(), buf_size,
                                pre_mapping_file_offset));
  EXPECT_THAT(buf.get()[0], Eq('a'));
}

TEST_F(MemoryMappedFileTest,
       GrowAndRemapIfNecessaryExceedingPreMappingMmapSize) {
  int page_size = MemoryMappedFile::system_page_size();
  int pre_mapping_file_offset = 99;
  int pre_mapping_mmap_size = page_size * 2 - pre_mapping_file_offset;
  // Create MemoryMappedFile with pre-mapping file_offset and mmap_size without
  // growing the file.
  ICING_ASSERT_OK_AND_ASSIGN(
      MemoryMappedFile mmapped_file,
      MemoryMappedFile::Create(filesystem_, file_path_,
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               MemoryMappedFile::kDefaultMaxFileSize,
                               pre_mapping_file_offset, pre_mapping_mmap_size));
  const char* original_region = mmapped_file.region();

  // Call GrowAndRemapIfNecessary with same file offset and new mmap_size that
  // exceeds pre_mapping_mmap_size (but still below max_file_size). The
  // underlying file size should grow correctly and the region should be
  // remapped.
  int new_mmap_size = page_size * 3 - pre_mapping_file_offset;
  ICING_EXPECT_OK(mmapped_file.GrowAndRemapIfNecessary(pre_mapping_file_offset,
                                                       new_mmap_size));

  EXPECT_THAT(filesystem_.GetFileSize(file_path_.c_str()),
              Eq(pre_mapping_file_offset + new_mmap_size));
  EXPECT_THAT(mmapped_file.region(), Not(Eq(original_region)));
  EXPECT_THAT(mmapped_file.file_offset(), Eq(pre_mapping_file_offset));
  EXPECT_THAT(mmapped_file.region_size(), Eq(new_mmap_size));
  EXPECT_THAT(mmapped_file.available_size(), Eq(new_mmap_size));
}

TEST_F(MemoryMappedFileTest, GrowAndRemapIfNecessaryDecreasingMmapSize) {
  int page_size = MemoryMappedFile::system_page_size();
  int pre_mapping_file_offset = 99;
  int pre_mapping_mmap_size = page_size * 2 - pre_mapping_file_offset;
  // Create MemoryMappedFile with pre-mapping file_offset and mmap_size, and
  // call GrowAndRemapIfNecessary to grow the underlying file.
  ICING_ASSERT_OK_AND_ASSIGN(
      MemoryMappedFile mmapped_file,
      MemoryMappedFile::Create(filesystem_, file_path_,
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               MemoryMappedFile::kDefaultMaxFileSize,
                               pre_mapping_file_offset, pre_mapping_mmap_size));
  ICING_ASSERT_OK(mmapped_file.GrowAndRemapIfNecessary(pre_mapping_file_offset,
                                                       pre_mapping_mmap_size));

  const char* original_region = mmapped_file.region();
  int original_file_size = filesystem_.GetFileSize(file_path_.c_str());
  ASSERT_THAT(original_file_size,
              Eq(pre_mapping_file_offset + pre_mapping_mmap_size));
  ASSERT_THAT(mmapped_file.region_size(), Eq(pre_mapping_mmap_size));
  ASSERT_THAT(mmapped_file.available_size(), Eq(pre_mapping_mmap_size));

  // Call GrowAndRemapIfNecessary with same file offset and new mmap_size
  // smaller than pre_mapping_mmap_size. There should be no file growth/truncate
  // or remap.
  int new_mmap_size = page_size - pre_mapping_file_offset;
  ICING_EXPECT_OK(mmapped_file.GrowAndRemapIfNecessary(pre_mapping_file_offset,
                                                       new_mmap_size));

  EXPECT_THAT(filesystem_.GetFileSize(file_path_.c_str()),
              Eq(original_file_size));
  EXPECT_THAT(mmapped_file.region(), Eq(original_region));
  EXPECT_THAT(mmapped_file.file_offset(), Eq(pre_mapping_file_offset));
  EXPECT_THAT(mmapped_file.region_size(), Eq(pre_mapping_mmap_size));
  EXPECT_THAT(mmapped_file.available_size(), Eq(pre_mapping_mmap_size));
}

TEST_F(MemoryMappedFileTest, GrowAndRemapIfNecessaryZeroMmapSizeShouldUnmap) {
  int page_size = MemoryMappedFile::system_page_size();
  int pre_mapping_file_offset = 99;
  int pre_mapping_mmap_size = page_size * 2 - pre_mapping_file_offset;
  // Create MemoryMappedFile with pre-mapping file_offset and mmap_size, and
  // call GrowAndRemapIfNecessary to grow the underlying file.
  ICING_ASSERT_OK_AND_ASSIGN(
      MemoryMappedFile mmapped_file,
      MemoryMappedFile::Create(filesystem_, file_path_,
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               MemoryMappedFile::kDefaultMaxFileSize,
                               pre_mapping_file_offset, pre_mapping_mmap_size));
  ICING_ASSERT_OK(mmapped_file.GrowAndRemapIfNecessary(pre_mapping_file_offset,
                                                       pre_mapping_mmap_size));

  int original_file_size = filesystem_.GetFileSize(file_path_.c_str());
  ASSERT_THAT(original_file_size,
              Eq(pre_mapping_file_offset + pre_mapping_mmap_size));
  ASSERT_THAT(mmapped_file.region(), NotNull());
  ASSERT_THAT(mmapped_file.region_size(), Eq(pre_mapping_mmap_size));
  ASSERT_THAT(mmapped_file.available_size(), Eq(pre_mapping_mmap_size));

  // Call GrowAndRemapIfNecessary with any file_offset and new_mmap_size = 0.
  // There should be no file growth/truncate, but the original mmapped region
  // should be unmapped.
  ICING_EXPECT_OK(mmapped_file.GrowAndRemapIfNecessary(pre_mapping_file_offset,
                                                       /*new_mmap_size=*/0));

  EXPECT_THAT(filesystem_.GetFileSize(file_path_.c_str()),
              Eq(original_file_size));
  EXPECT_THAT(mmapped_file.region(), IsNull());
  EXPECT_THAT(mmapped_file.file_offset(), Eq(0));
  EXPECT_THAT(mmapped_file.region_size(), Eq(0));
  EXPECT_THAT(mmapped_file.available_size(), Eq(0));
}

TEST_F(MemoryMappedFileTest, GrowAndRemapIfNecessaryChangeOffset) {
  int page_size = MemoryMappedFile::system_page_size();
  int pre_mapping_file_offset = 99;
  int pre_mapping_mmap_size = page_size * 2 - pre_mapping_file_offset;
  // Create MemoryMappedFile with pre-mapping file_offset and mmap_size, and
  // call GrowAndRemapIfNecessary to grow the underlying file.
  ICING_ASSERT_OK_AND_ASSIGN(
      MemoryMappedFile mmapped_file,
      MemoryMappedFile::Create(filesystem_, file_path_,
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               MemoryMappedFile::kDefaultMaxFileSize,
                               pre_mapping_file_offset, pre_mapping_mmap_size));
  ICING_ASSERT_OK(mmapped_file.GrowAndRemapIfNecessary(pre_mapping_file_offset,
                                                       pre_mapping_mmap_size));

  const char* original_region = mmapped_file.region();
  int original_file_size = filesystem_.GetFileSize(file_path_.c_str());
  ASSERT_THAT(original_file_size,
              Eq(pre_mapping_file_offset + pre_mapping_mmap_size));
  ASSERT_THAT(mmapped_file.region_size(), Eq(pre_mapping_mmap_size));
  ASSERT_THAT(mmapped_file.available_size(), Eq(pre_mapping_mmap_size));

  // Call GrowAndRemapIfNecessary with different file_offset and new mmap_size
  // that doesn't require to grow the underlying file. The region should still
  // be remapped since offset has been changed.
  int new_file_offset = pre_mapping_file_offset + page_size;
  int new_mmap_size = page_size * 2 - new_file_offset;
  ASSERT_THAT(new_file_offset + new_mmap_size, Le(original_file_size));
  ICING_EXPECT_OK(
      mmapped_file.GrowAndRemapIfNecessary(new_file_offset, new_mmap_size));

  EXPECT_THAT(filesystem_.GetFileSize(file_path_.c_str()),
              Eq(original_file_size));
  EXPECT_THAT(mmapped_file.region(), Not(Eq(original_region)));
  EXPECT_THAT(mmapped_file.file_offset(), Eq(new_file_offset));
  EXPECT_THAT(mmapped_file.region_size(), Eq(new_mmap_size));
  EXPECT_THAT(mmapped_file.available_size(), Eq(new_mmap_size));
}

TEST_F(MemoryMappedFileTest, GrowAndRemapIfNecessaryInvalidMmapRegionInfo) {
  int page_size = MemoryMappedFile::system_page_size();
  int max_file_size = page_size * 2;
  // Create MemoryMappedFile with pre-mapping file_offset and mmap_size, and
  // call GrowAndRemapIfNecessary to grow the underlying file.
  ICING_ASSERT_OK_AND_ASSIGN(
      MemoryMappedFile mmapped_file,
      MemoryMappedFile::Create(filesystem_, file_path_,
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               max_file_size,
                               /*pre_mapping_file_offset=*/0,
                               /*pre_mapping_mmap_size=*/page_size * 2));

  // Negative new_file_offset.
  EXPECT_THAT(mmapped_file.GrowAndRemapIfNecessary(
                  /*new_file_offset=*/-1,
                  /*new_mmap_size=*/page_size),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  // Negative new_mmap_size
  EXPECT_THAT(mmapped_file.GrowAndRemapIfNecessary(
                  /*new_file_offset=*/0,
                  /*new_mmap_size=*/-1),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  // new_file_offset + new_mmap_size > max_file_size.
  int new_file_offset = 99;
  int new_mmap_size = max_file_size - new_file_offset + 1;
  EXPECT_THAT(
      mmapped_file.GrowAndRemapIfNecessary(new_file_offset, new_mmap_size),
      StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));

  // Edge cases to make sure the implementation of range check won't have
  // integer overflow bug.
  EXPECT_THAT(mmapped_file.GrowAndRemapIfNecessary(
                  /*new_file_offset=*/99,
                  /*new_mmap_size=*/std::numeric_limits<int64_t>::max()),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(mmapped_file.GrowAndRemapIfNecessary(
                  /*new_file_offset=*/0,
                  /*new_mmap_size=*/INT64_C(-1) *
                      (std::numeric_limits<int64_t>::max() - max_file_size)),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
  EXPECT_THAT(mmapped_file.GrowAndRemapIfNecessary(
                  /*new_file_offset=*/INT64_C(-1) *
                      (std::numeric_limits<int64_t>::max() - max_file_size),
                  /*new_mmap_size=*/page_size),
              StatusIs(libtextclassifier3::StatusCode::OUT_OF_RANGE));
}

TEST_F(MemoryMappedFileTest, RemapFailureStillValidInstance) {
  auto mock_filesystem = std::make_unique<MockFilesystem>();
  int page_size = MemoryMappedFile::system_page_size();
  int max_file_size = page_size * 10;

  // 1. Create MemoryMappedFile with pre-mapping offset=0 and
  //    mmap_size=page_size. Also call GrowAndRemapIfNecessary to grow the file
  //    size to page_size.
  ICING_ASSERT_OK_AND_ASSIGN(
      MemoryMappedFile mmapped_file,
      MemoryMappedFile::Create(*mock_filesystem, file_path_,
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               max_file_size,
                               /*pre_mapping_file_offset=*/0,
                               /*pre_mapping_mmap_size=*/page_size));
  ICING_ASSERT_OK(
      mmapped_file.GrowAndRemapIfNecessary(/*new_file_offset=*/0,
                                           /*new_mmap_size=*/page_size));
  ASSERT_THAT(filesystem_.GetFileSize(file_path_.c_str()), Eq(page_size));
  ASSERT_THAT(mmapped_file.region(), NotNull());
  ASSERT_THAT(mmapped_file.mutable_region(), NotNull());
  ASSERT_THAT(mmapped_file.file_offset(), Eq(0));
  ASSERT_THAT(mmapped_file.region_size(), Eq(page_size));
  ASSERT_THAT(mmapped_file.available_size(), Eq(page_size));
  mmapped_file.mutable_region()[page_size - 1] = 'a';

  const char* original_region = mmapped_file.region();

  // 2. Call GrowAndRemapIfNecessary with different offset and greater
  //    mmap_size. Here we're testing the case when file growth succeeds but
  //    remap (RemapImpl) fails.
  //    To make RemapImpl fail, mock OpenForWrite to fail. Note that we use
  //    OpenForAppend when growing the file, so it is ok to make OpenForWrite
  //    fail without affecting file growth.
  ON_CALL(*mock_filesystem, OpenForWrite(_)).WillByDefault(Return(-1));
  EXPECT_THAT(
      mmapped_file.GrowAndRemapIfNecessary(/*new_file_offset=*/1,
                                           /*new_mmap_size=*/page_size * 2 - 1),
      StatusIs(libtextclassifier3::StatusCode::INTERNAL));

  // 3. Verify the result. The file size should be grown, but since remap fails,
  //    mmap related fields should remain unchanged.
  EXPECT_THAT(filesystem_.GetFileSize(file_path_.c_str()), Eq(page_size * 2));
  EXPECT_THAT(mmapped_file.region(), Eq(original_region));
  EXPECT_THAT(mmapped_file.mutable_region(), Eq(original_region));
  EXPECT_THAT(mmapped_file.file_offset(), Eq(0));
  EXPECT_THAT(mmapped_file.region_size(), Eq(page_size));
  EXPECT_THAT(mmapped_file.available_size(), Eq(page_size));
  // We should still be able to get the correct content via region.
  EXPECT_THAT(mmapped_file.region()[page_size - 1], Eq('a'));
}

TEST_F(MemoryMappedFileTest, BadFileSizeDuringGrowReturnsError) {
  auto mock_filesystem = std::make_unique<MockFilesystem>();
  int page_size = MemoryMappedFile::system_page_size();
  int max_file_size = page_size * 10;
  ICING_ASSERT_OK_AND_ASSIGN(
      MemoryMappedFile mmapped_file,
      MemoryMappedFile::Create(*mock_filesystem, file_path_,
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               max_file_size,
                               /*pre_mapping_file_offset=*/0,
                               /*pre_mapping_mmap_size=*/page_size));
  ICING_ASSERT_OK(
      mmapped_file.GrowAndRemapIfNecessary(/*new_file_offset=*/0,
                                           /*new_mmap_size=*/page_size));
  ASSERT_THAT(filesystem_.GetFileSize(file_path_.c_str()), Eq(page_size));
  ASSERT_THAT(mmapped_file.region(), NotNull());
  ASSERT_THAT(mmapped_file.mutable_region(), NotNull());
  ASSERT_THAT(mmapped_file.file_offset(), Eq(0));
  ASSERT_THAT(mmapped_file.region_size(), Eq(page_size));
  ASSERT_THAT(mmapped_file.available_size(), Eq(page_size));
  mmapped_file.mutable_region()[page_size - 1] = 'a';

  const char* original_region = mmapped_file.region();

  // Calling GrowAndRemapIfNecessary with larger size will cause file growth.
  // During file growth, we will attempt to sync the underlying file size via
  // GetFileSize to see if growing is actually necessary. Mock GetFileSize to
  // return an error.
  ON_CALL(*mock_filesystem, GetFileSize(A<const char*>()))
      .WillByDefault(Return(Filesystem::kBadFileSize));

  // We should fail gracefully and return an INTERNAL error to indicate that
  // there was an issue retrieving the file size. The underlying file size and
  // mmap info should remain unchanged.
  EXPECT_THAT(
      mmapped_file.GrowAndRemapIfNecessary(/*new_file_offset=*/0,
                                           /*new_mmap_size=*/page_size * 2),
      StatusIs(libtextclassifier3::StatusCode::INTERNAL));
  EXPECT_THAT(filesystem_.GetFileSize(file_path_.c_str()), Eq(page_size));
  EXPECT_THAT(mmapped_file.region(), Eq(original_region));
  EXPECT_THAT(mmapped_file.mutable_region(), Eq(original_region));
  EXPECT_THAT(mmapped_file.file_offset(), Eq(0));
  EXPECT_THAT(mmapped_file.region_size(), Eq(page_size));
  EXPECT_THAT(mmapped_file.available_size(), Eq(page_size));
  // We should still be able to get the correct content via region.
  EXPECT_THAT(mmapped_file.region()[page_size - 1], Eq('a'));
}

TEST_F(MemoryMappedFileTest, WriteSucceedsPartiallyAndFailsDuringGrow) {
  auto mock_filesystem = std::make_unique<MockFilesystem>();
  int page_size = MemoryMappedFile::system_page_size();
  int max_file_size = page_size * 10;
  ICING_ASSERT_OK_AND_ASSIGN(
      MemoryMappedFile mmapped_file,
      MemoryMappedFile::Create(*mock_filesystem, file_path_,
                               MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC,
                               max_file_size,
                               /*pre_mapping_file_offset=*/0,
                               /*pre_mapping_mmap_size=*/max_file_size));

  // 1. Initially the underlying file size is 0. When calling
  //    GrowAndRemapIfNecessary first time with new_mmap_size = page_size * 2,
  //    Write() should be called 2 times and each time should grow the
  //    underlying file by page_size bytes.
  //    Mock the 2nd Write() to write partially (1 byte) and fail, so the file
  //    will only be grown by page_size + 1 bytes in total.
  auto open_lambda = [this](int fd, const void* data,
                            size_t data_size) -> bool {
    EXPECT_THAT(data_size, Gt(1));
    EXPECT_THAT(this->filesystem_.Write(fd, data, 1), Eq(1));
    return false;
  };
  EXPECT_CALL(*mock_filesystem, Write(A<int>(), A<const void*>(), A<size_t>()))
      .WillOnce(DoDefault())
      .WillOnce(open_lambda);

  // 2. Call GrowAndRemapIfNecessary and expect to fail. The actual file size
  //    should be page_size + 1, but the (cached) file_size_ should be page_size
  //    since it fails to update that partially written byte of the 2nd Write().
  EXPECT_THAT(
      mmapped_file.GrowAndRemapIfNecessary(/*new_file_offset=*/0,
                                           /*new_mmap_size=*/page_size * 2),
      StatusIs(libtextclassifier3::StatusCode::INTERNAL));
  EXPECT_THAT(filesystem_.GetFileSize(file_path_.c_str()), Eq(page_size + 1));
  EXPECT_THAT(mmapped_file.available_size(), Eq(page_size));

  // 3. Call GrowAndRemapIfNecessary again with new_mmap_size = page_size + 1.
  //    Even though file_size_ only caches page_size and excludes the partially
  //    written byte(s) due to failure of the previous round of grow, the next
  //    round should sync the actual file size to file_size_ via system call and
  //    skip Write() since the actual file size is large enough for the new
  //    mmap_size.
  //    Note: WillOnce() above will ensure that Write() won't be called for
  //    another time.
  ICING_EXPECT_OK(
      mmapped_file.GrowAndRemapIfNecessary(/*new_file_offset=*/0,
                                           /*new_mmap_size=*/page_size + 1));
  EXPECT_THAT(mmapped_file.available_size(), Eq(page_size + 1));

  // 4. Call GrowAndRemapIfNecessary again with new_mmap_size = page_size * 2.
  //    Even though the current file size is page_size + 1, the next round of
  //    grow should automatically calibrate the file size back to a multiple of
  //    page_size instead of just simply appending page_size bytes to the file.
  EXPECT_CALL(*mock_filesystem, Write(A<int>(), A<const void*>(), A<size_t>()))
      .WillOnce(DoDefault());
  ICING_EXPECT_OK(
      mmapped_file.GrowAndRemapIfNecessary(/*new_file_offset=*/0,
                                           /*new_mmap_size=*/page_size * 2));
  EXPECT_THAT(filesystem_.GetFileSize(file_path_.c_str()), Eq(page_size * 2));
  EXPECT_THAT(mmapped_file.available_size(), Eq(page_size * 2));
}

}  // namespace

}  // namespace lib
}  // namespace icing
