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

#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

#include <base/format_macros.h>
#include <base/logging.h>
#include <base/strings/string_number_conversions.h>
#include <base/strings/string_util.h>
#include <base/strings/stringprintf.h>
#include <gtest/gtest.h>
#include <erofs/internal.h>
#include <erofs/io.h>

#include "lz4diff/lz4diff.h"
#include "lz4diff/lz4patch.h"
#include "update_engine/common/test_utils.h"
#include "update_engine/common/utils.h"
#include "update_engine/lz4diff/lz4diff_compress.h"
#include "update_engine/payload_generator/delta_diff_generator.h"
#include "update_engine/payload_generator/erofs_filesystem.h"
#include "update_engine/payload_generator/extent_utils.h"

using std::string;
using std::vector;

namespace chromeos_update_engine {

namespace {
class Lz4diffTest : public ::testing::Test {};

using test_utils::GetBuildArtifactsPath;

// This test parses the sample images generated during build time with the
// "generate_image.sh" script. The expected conditions of each file in these
// images is encoded in the file name, as defined in the mentioned script.
TEST_F(Lz4diffTest, DiffElfBinary) {
  const auto old_img = GetBuildArtifactsPath("gen/erofs.img");
  const auto new_img = GetBuildArtifactsPath("gen/erofs_new.img");
  auto old_fs = ErofsFilesystem::CreateFromFile(old_img);
  ASSERT_NE(old_fs, nullptr);
  ASSERT_EQ(kBlockSize, old_fs->GetBlockSize());
  auto new_fs = ErofsFilesystem::CreateFromFile(new_img);
  ASSERT_NE(new_fs, nullptr);
  ASSERT_EQ(kBlockSize, new_fs->GetBlockSize());

  vector<ErofsFilesystem::File> old_files;
  ASSERT_TRUE(old_fs->GetFiles(&old_files));
  vector<ErofsFilesystem::File> new_files;
  ASSERT_TRUE(new_fs->GetFiles(&new_files));

  const auto it =
      std::find_if(old_files.begin(), old_files.end(), [](const auto& file) {
        return file.name == "/delta_generator";
      });
  ASSERT_NE(it, old_files.end())
      << "There should be a delta_generator entry in gen/erofs.img. Is the "
         "generate_test_erofs_imgages.sh script implemented wrong?";
  const auto new_it =
      std::find_if(new_files.begin(), new_files.end(), [](const auto& file) {
        return file.name == "/delta_generator";
      });
  ASSERT_NE(new_it, new_files.end())
      << "There should be a delta_generator entry in gen/erofs_new.img. Is the "
         "generate_test_erofs_imgages.sh script implemented wrong?";

  const auto old_delta_generator = *it;
  auto new_delta_generator = *new_it;
  Blob old_data;
  ASSERT_TRUE(utils::ReadExtents(
      old_img, old_delta_generator.extents, &old_data, kBlockSize));
  Blob new_data;
  ASSERT_TRUE(utils::ReadExtents(
      new_img, new_delta_generator.extents, &new_data, kBlockSize));
  // New image is actually generated with compression level 7, we use a
  // different compression level so that recompressed blob is different. This
  // way we can test the postfix functionality.
  new_delta_generator.compressed_file_info.algo.set_level(5);
  Blob diff_blob;
  ASSERT_TRUE(Lz4Diff(old_data,
                      new_data,
                      old_delta_generator.compressed_file_info,
                      new_delta_generator.compressed_file_info,
                      &diff_blob));
  Blob patched_new_data;
  ASSERT_TRUE(Lz4Patch(old_data, diff_blob, &patched_new_data));
  ASSERT_EQ(patched_new_data, new_data);
}

}  // namespace

}  // namespace chromeos_update_engine
