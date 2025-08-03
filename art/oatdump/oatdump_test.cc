/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include <android-base/file.h>

#include "oatdump_test.h"

namespace art {

INSTANTIATE_TEST_SUITE_P(DynamicOrStatic,
                         OatDumpTest,
                         testing::Values(Flavor::kDynamic, Flavor::kStatic));

// Disable tests on arm and arm64 as they are taking too long to run. b/27824283.
#define TEST_DISABLED_FOR_ARM_AND_ARM64() \
  TEST_DISABLED_FOR_ARM();                \
  TEST_DISABLED_FOR_ARM64();

TEST_P(OatDumpTest, TestNoDumpVmap) {
  TEST_DISABLED_FOR_RISCV64();
  TEST_DISABLED_FOR_ARM_AND_ARM64();
  std::string error_msg;
  ASSERT_TRUE(Exec(GetParam(),
                   kArgImage | kArgBcp | kArgIsa,
                   {"--no-dump:vmap"},
                   kExpectImage | kExpectOat | kExpectCode));
}

TEST_P(OatDumpTest, TestNoDisassemble) {
  TEST_DISABLED_FOR_RISCV64();
  TEST_DISABLED_FOR_ARM_AND_ARM64();
  std::string error_msg;
  ASSERT_TRUE(Exec(GetParam(),
                   kArgImage | kArgBcp | kArgIsa,
                   {"--no-disassemble"},
                   kExpectImage | kExpectOat | kExpectCode));
}

TEST_P(OatDumpTest, TestListClasses) {
  TEST_DISABLED_FOR_RISCV64();
  TEST_DISABLED_FOR_ARM_AND_ARM64();
  std::string error_msg;
  ASSERT_TRUE(Exec(
      GetParam(), kArgImage | kArgBcp | kArgIsa, {"--list-classes"}, kExpectImage | kExpectOat));
}

TEST_P(OatDumpTest, TestListMethods) {
  TEST_DISABLED_FOR_RISCV64();
  TEST_DISABLED_FOR_ARM_AND_ARM64();
  std::string error_msg;
  ASSERT_TRUE(Exec(
      GetParam(), kArgImage | kArgBcp | kArgIsa, {"--list-methods"}, kExpectImage | kExpectOat));
}

TEST_P(OatDumpTest, TestSymbolize) {
  if (GetParam() == Flavor::kDynamic) {
    TEST_DISABLED_FOR_TARGET();  // Can not write files inside the apex directory.
  } else {
    TEST_DISABLED_FOR_RISCV64();
    TEST_DISABLED_FOR_ARM_AND_ARM64();
  }
  std::string error_msg;
  ASSERT_TRUE(Exec(GetParam(), kArgSymbolize, {}, /*expects=*/0));
}

TEST_P(OatDumpTest, TestExportDex) {
  if (GetParam() == Flavor::kStatic) {
    TEST_DISABLED_FOR_RISCV64();
    TEST_DISABLED_FOR_ARM_AND_ARM64();
  }
  std::string error_msg;
  ASSERT_TRUE(GenerateAppOdexFile(GetParam()));
  ASSERT_TRUE(Exec(GetParam(), kArgOatApp, {"--export-dex-to=" + tmp_dir_}, kExpectOat));
  if (GetParam() == Flavor::kDynamic) {
    const std::string dex_location =
        tmp_dir_ + "/" + android::base::Basename(GetTestDexFileName(GetAppBaseName().c_str())) +
        "_export.dex";
    const std::string dexdump = GetExecutableFilePath("dexdump",
                                                      /*is_debug=*/false,
                                                      /*is_static=*/false,
                                                      /*bitness=*/false);
    std::string output;
    auto post_fork_fn = []() { return true; };
    ForkAndExecResult res = ForkAndExec({dexdump, "-d", dex_location}, post_fork_fn, &output);
    ASSERT_TRUE(res.StandardSuccess());
  }
}

}  // namespace art
