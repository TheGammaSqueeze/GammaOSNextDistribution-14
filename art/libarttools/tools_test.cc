/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "tools/tools.h"

#include <algorithm>
#include <filesystem>
#include <iterator>

#include "android-base/file.h"
#include "base/common_art_test.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace art {
namespace tools {
namespace {

using ::android::base::WriteStringToFile;
using ::testing::UnorderedElementsAre;

void CreateFile(const std::string& filename) {
  std::filesystem::path path(filename);
  std::filesystem::create_directories(path.parent_path());
  ASSERT_TRUE(WriteStringToFile(/*content=*/"", filename));
}

class ArtToolsTest : public CommonArtTest {
 protected:
  void SetUp() override {
    CommonArtTest::SetUp();
    scratch_dir_ = std::make_unique<ScratchDir>();
    scratch_path_ = scratch_dir_->GetPath();
    // Remove the trailing '/';
    scratch_path_.resize(scratch_path_.length() - 1);
  }

  void TearDown() override {
    scratch_dir_.reset();
    CommonArtTest::TearDown();
  }

  std::unique_ptr<ScratchDir> scratch_dir_;
  std::string scratch_path_;
};

TEST_F(ArtToolsTest, Glob) {
  CreateFile(scratch_path_ + "/abc/def/000.txt");
  CreateFile(scratch_path_ + "/abc/def/ghi/123.txt");
  CreateFile(scratch_path_ + "/abc/def/ghi/456.txt");
  CreateFile(scratch_path_ + "/abc/def/ghi/456.pdf");
  CreateFile(scratch_path_ + "/abc/def/ghi/jkl/456.txt");
  CreateFile(scratch_path_ + "/789.txt");
  CreateFile(scratch_path_ + "/abc/789.txt");
  CreateFile(scratch_path_ + "/abc/aaa/789.txt");
  CreateFile(scratch_path_ + "/abc/aaa/bbb/789.txt");
  CreateFile(scratch_path_ + "/abc/mno/123.txt");
  CreateFile(scratch_path_ + "/abc/aaa/mno/123.txt");
  CreateFile(scratch_path_ + "/abc/aaa/bbb/mno/123.txt");
  CreateFile(scratch_path_ + "/abc/aaa/bbb/mno/ccc/123.txt");
  CreateFile(scratch_path_ + "/pqr/123.txt");
  CreateFile(scratch_path_ + "/abc/pqr/123.txt");
  CreateFile(scratch_path_ + "/abc/aaa/pqr/123.txt");
  CreateFile(scratch_path_ + "/abc/aaa/bbb/pqr/123.txt");
  CreateFile(scratch_path_ + "/abc/aaa/bbb/pqr/ccc/123.txt");
  CreateFile(scratch_path_ + "/abc/aaa/bbb/pqr/ccc/ddd/123.txt");

  // This symlink will cause infinite recursion. It should not be followed.
  std::filesystem::create_directory_symlink(scratch_path_ + "/abc/aaa/bbb/pqr",
                                            scratch_path_ + "/abc/aaa/bbb/pqr/lnk");

  // This is a directory. It should not be included in the results.
  std::filesystem::create_directory(scratch_path_ + "/abc/def/ghi/000.txt");

  std::vector<std::string> patterns = {
      scratch_path_ + "/abc/def/000.txt",
      scratch_path_ + "/abc/def/ghi/*.txt",
      scratch_path_ + "/abc/**/789.txt",
      scratch_path_ + "/abc/**/mno/*.txt",
      scratch_path_ + "/abc/**/pqr/**",
  };

  EXPECT_THAT(Glob(patterns, scratch_path_),
              UnorderedElementsAre(scratch_path_ + "/abc/def/000.txt",
                                   scratch_path_ + "/abc/def/ghi/123.txt",
                                   scratch_path_ + "/abc/def/ghi/456.txt",
                                   scratch_path_ + "/abc/789.txt",
                                   scratch_path_ + "/abc/aaa/789.txt",
                                   scratch_path_ + "/abc/aaa/bbb/789.txt",
                                   scratch_path_ + "/abc/mno/123.txt",
                                   scratch_path_ + "/abc/aaa/mno/123.txt",
                                   scratch_path_ + "/abc/aaa/bbb/mno/123.txt",
                                   scratch_path_ + "/abc/pqr/123.txt",
                                   scratch_path_ + "/abc/aaa/pqr/123.txt",
                                   scratch_path_ + "/abc/aaa/bbb/pqr/123.txt",
                                   scratch_path_ + "/abc/aaa/bbb/pqr/ccc/123.txt",
                                   scratch_path_ + "/abc/aaa/bbb/pqr/ccc/ddd/123.txt"));
}

TEST_F(ArtToolsTest, EscapeGlob) {
  CreateFile(scratch_path_ + "/**");
  CreateFile(scratch_path_ + "/*.txt");
  CreateFile(scratch_path_ + "/?.txt");
  CreateFile(scratch_path_ + "/[a-z].txt");
  CreateFile(scratch_path_ + "/**.txt");
  CreateFile(scratch_path_ + "/??.txt");
  CreateFile(scratch_path_ + "/[a-z[a-z]][a-z].txt");

  // Paths that shouldn't be matched if the paths above are escaped.
  CreateFile(scratch_path_ + "/abc/b.txt");
  CreateFile(scratch_path_ + "/b.txt");
  CreateFile(scratch_path_ + "/*b.txt");
  CreateFile(scratch_path_ + "/?b.txt");
  CreateFile(scratch_path_ + "/[a-zb]b.txt");

  // Verifies that the escaped path only matches the given path.
  auto verify_escape = [this](const std::string& file) {
    EXPECT_THAT(Glob({EscapeGlob(file)}, scratch_path_), UnorderedElementsAre(file));
  };

  verify_escape(scratch_path_ + "/**");
  verify_escape(scratch_path_ + "/*.txt");
  verify_escape(scratch_path_ + "/?.txt");
  verify_escape(scratch_path_ + "/[a-z].txt");
  verify_escape(scratch_path_ + "/**.txt");
  verify_escape(scratch_path_ + "/**");
  verify_escape(scratch_path_ + "/??.txt");
  verify_escape(scratch_path_ + "/[a-z[a-z]][a-z].txt");
}

}  // namespace
}  // namespace tools
}  // namespace art
