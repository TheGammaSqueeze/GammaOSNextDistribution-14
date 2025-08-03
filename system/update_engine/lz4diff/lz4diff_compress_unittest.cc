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

static void ExtractErofsImage(const char* erofs_image,
                              const char* inode_path,
                              Blob* output) {
  struct erofs_sb_info sbi {};
  auto err = dev_open_ro(&sbi, erofs_image);
  ASSERT_EQ(err, 0);
  DEFER {
    dev_close(&sbi);
  };

  err = erofs_read_superblock(&sbi);
  ASSERT_EQ(err, 0);
  struct erofs_inode inode {
    .sbi = &sbi
  };
  err = erofs_ilookup(inode_path, &inode);
  ASSERT_EQ(err, 0) << strerror(-err);
  output->resize(inode.i_size);
  err = erofs_pread(&inode,
                    reinterpret_cast<char*>(output->data()),
                    output->size(),
                    0 /* offset */);
  ASSERT_EQ(err, 0);
}

class Lz4diffCompressTest : public ::testing::Test {};

using test_utils::GetBuildArtifactsPath;

// This test parses the sample images generated during build time with the
// "generate_image.sh" script. The expected conditions of each file in these
// images is encoded in the file name, as defined in the mentioned script.
TEST_F(Lz4diffCompressTest, ExtractElfBinary) {
  const auto build_path = GetBuildArtifactsPath("gen/erofs.img");
  auto fs = ErofsFilesystem::CreateFromFile(build_path);
  ASSERT_NE(fs, nullptr);
  ASSERT_EQ(kBlockSize, fs->GetBlockSize());

  vector<ErofsFilesystem::File> files;
  ASSERT_TRUE(fs->GetFiles(&files));

  const auto it =
      std::find_if(files.begin(), files.end(), [](const auto& file) {
        return file.name == "/delta_generator";
      });
  ASSERT_NE(it, files.end())
      << "There should be a delta_generator entry in gen/erofs.img. Is the "
         "generate_test_erofs_imgages.sh script implemented wrong?";

  const auto delta_generator = *it;
  Blob expected_blob;
  ASSERT_NO_FATAL_FAILURE(ExtractErofsImage(
      build_path.c_str(), "/delta_generator", &expected_blob));
  Blob compressed_blob;
  ASSERT_TRUE(utils::ReadExtents(
      build_path, delta_generator.extents, &compressed_blob, kBlockSize));
  auto decompressed_blob = TryDecompressBlob(
      compressed_blob,
      delta_generator.compressed_file_info.blocks,
      delta_generator.compressed_file_info.zero_padding_enabled);
  ASSERT_GT(decompressed_blob.size(), 0UL);
  ASSERT_GE(decompressed_blob.size(),
            static_cast<size_t>(delta_generator.file_stat.st_size));
  decompressed_blob.resize(delta_generator.file_stat.st_size);
  ASSERT_EQ(decompressed_blob, expected_blob);
}

}  // namespace

}  // namespace chromeos_update_engine
