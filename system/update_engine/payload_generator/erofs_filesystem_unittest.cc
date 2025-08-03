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

#include "update_engine/payload_generator/erofs_filesystem.h"

#include <unistd.h>

#include <string>
#include <vector>

#include <base/format_macros.h>
#include <base/logging.h>
#include <base/strings/string_number_conversions.h>
#include <base/strings/string_util.h>
#include <base/strings/stringprintf.h>
#include <gtest/gtest.h>

#include "payload_generator/delta_diff_generator.h"
#include "update_engine/common/test_utils.h"
#include "update_engine/common/utils.h"
#include "update_engine/payload_generator/extent_utils.h"

using std::string;
using std::unique_ptr;
using std::vector;

namespace {

class ErofsFilesystemTest : public ::testing::Test {};

}  // namespace

namespace chromeos_update_engine {

using test_utils::GetBuildArtifactsPath;

TEST_F(ErofsFilesystemTest, InvalidFilesystem) {
  ScopedTempFile fs_filename_{"ErofsFilesystemTest-XXXXXX"};
  ASSERT_EQ(0, truncate(fs_filename_.path().c_str(), kBlockSize));
  unique_ptr<ErofsFilesystem> fs =
      ErofsFilesystem::CreateFromFile(fs_filename_.path());
  ASSERT_EQ(nullptr, fs.get());

  fs = ErofsFilesystem::CreateFromFile("/path/to/invalid/file");
  ASSERT_EQ(nullptr, fs.get());
}

TEST_F(ErofsFilesystemTest, EmptyFilesystem) {
  unique_ptr<ErofsFilesystem> fs = ErofsFilesystem::CreateFromFile(
      GetBuildArtifactsPath("gen/erofs_empty.img"));

  ASSERT_NE(nullptr, fs);
  ASSERT_EQ(kBlockSize, fs->GetBlockSize());

  vector<FilesystemInterface::File> files;
  ASSERT_TRUE(fs->GetFiles(&files));
  ASSERT_EQ(files.size(), 0UL);
}

// This test parses the sample images generated during build time with the
// "generate_image.sh" script. The expected conditions of each file in these
// images is encoded in the file name, as defined in the mentioned script.
TEST_F(ErofsFilesystemTest, ParseGeneratedImages) {
  const auto build_path = GetBuildArtifactsPath("gen/erofs.img");
  auto fs = ErofsFilesystem::CreateFromFile(build_path);
  ASSERT_NE(fs, nullptr);
  ASSERT_EQ(kBlockSize, fs->GetBlockSize());

  vector<ErofsFilesystem::File> files;
  ASSERT_TRUE(fs->GetFiles(&files));

  std::sort(files.begin(), files.end(), [](const auto& a, const auto& b) {
    return a.name < b.name;
  });
  vector<string> filenames;
  filenames.resize(files.size());
  std::transform(
      files.begin(), files.end(), filenames.begin(), [](const auto& file) {
        return file.name;
      });
  const std::vector<std::string> expected_filenames = {
      "/delta_generator",
      "/dir1/dir2/dir123/chunks_of_zero",
      // Empty files are ignored
      // "/dir1/dir2/dir123/empty",
      "/dir1/dir2/file0",
      "/dir1/dir2/file1",
      "/dir1/dir2/file2",
      "/dir1/dir2/file4",
      "/dir1/file0",
      "/dir1/file2",
      "/etc/update_engine.conf",
      "/file1",
      // Files < 4K are stored inline, and therefore ignored, as they are often
      // stored not on block boundary.
      "/generate_test_erofs_images.sh"};
  ASSERT_EQ(filenames, expected_filenames);
  const auto delta_generator = files[0];
  ASSERT_GT(delta_generator.compressed_file_info.blocks.size(), 0UL);
  size_t compressed_size = 0;
  size_t uncompressed_size = 0;
  for (const auto& block : delta_generator.compressed_file_info.blocks) {
    compressed_size += block.compressed_length;
    uncompressed_size += block.uncompressed_length;
  }
  ASSERT_GE(uncompressed_size,
            static_cast<size_t>(delta_generator.file_stat.st_size))
      << "Uncompressed data should be at least as big as original file, plus "
         "possible trailing data.";
  const auto total_blocks = utils::BlocksInExtents(delta_generator.extents);
  ASSERT_EQ(compressed_size, total_blocks * kBlockSize);
}

}  // namespace chromeos_update_engine
