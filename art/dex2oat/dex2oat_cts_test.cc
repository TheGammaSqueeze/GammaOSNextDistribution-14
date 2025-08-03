/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "base/file_utils.h"
#include "dex2oat_environment_test.h"

namespace art {

class Dex2oatCtsTest : public CommonArtTest, public Dex2oatScratchDirs {
 public:
  void SetUp() override {
    CommonArtTest::SetUp();
    Dex2oatScratchDirs::SetUp(android_data_);
  }

  void TearDown() override {
    Dex2oatScratchDirs::TearDown();
    CommonArtTest::TearDown();
  }

 protected:
  // Stripped down counterpart to Dex2oatEnvironmentTest::Dex2Oat that only adds
  // enough arguments for our purposes.
  int Dex2Oat(const std::vector<std::string>& dex2oat_args,
              std::string* output,
              std::string* error_msg) {
    // This command line should work regardless of bitness, ISA, etc.
    std::vector<std::string> argv = {std::string(kAndroidArtApexDefaultPath) + "/bin/dex2oat"};
    argv.insert(argv.end(), dex2oat_args.begin(), dex2oat_args.end());

    // We must set --android-root.
    const char* android_root = getenv("ANDROID_ROOT");
    CHECK(android_root != nullptr);
    argv.push_back("--android-root=" + std::string(android_root));

    // We need dex2oat to actually log things.
    auto post_fork_fn = []() { return setenv("ANDROID_LOG_TAGS", "*:d", 1) == 0; };
    ForkAndExecResult res = ForkAndExec(argv, post_fork_fn, output);
    if (res.stage != ForkAndExecResult::kFinished) {
      *error_msg = strerror(errno);
      ::testing::AssertionFailure() << "Failed to finish dex2oat invocation: " << *error_msg;
    }

    if (!res.StandardSuccess()) {
      // We cannot use ASSERT_TRUE since the method returns an int and not void.
      ::testing::AssertionFailure() << "dex2oat fork/exec failed: " << *error_msg;
    }

    return res.status_code;
  }
};

// Run dex2oat with --force-palette-compilation-hooks to force calls to
// PaletteNotify{Start,End}Dex2oatCompilation.
TEST_F(Dex2oatCtsTest, CompilationHooks) {
  const std::string dex_location = GetTestDexFileName("Main");
  const std::string oat_location = GetScratchDir() + "/base.oat";
  const std::string vdex_location = GetScratchDir() + "/base.vdex";

  std::vector<std::string> args;
  args.emplace_back("--dex-file=" + dex_location);

  std::unique_ptr<File> oat_file(OS::CreateEmptyFile(oat_location.c_str()));
  ASSERT_NE(oat_file, nullptr) << oat_location;
  args.emplace_back("--oat-fd=" + std::to_string(oat_file->Fd()));
  args.emplace_back("--oat-location=" + oat_location);

  std::unique_ptr<File> vdex_file(OS::CreateEmptyFile(vdex_location.c_str()));
  ASSERT_NE(vdex_file, nullptr) << vdex_location;
  args.emplace_back("--output-vdex-fd=" + std::to_string(vdex_file->Fd()));

  args.emplace_back("--force-palette-compilation-hooks");

  std::string output = "";
  std::string error_msg;
  int res = Dex2Oat(args, &output, &error_msg);
  EXPECT_EQ(res, 0) << error_msg;
  EXPECT_EQ(oat_file->FlushCloseOrErase(), 0);
  EXPECT_EQ(vdex_file->FlushCloseOrErase(), 0);
}

}  // namespace art
