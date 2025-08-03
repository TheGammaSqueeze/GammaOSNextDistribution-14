/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "oatdump_test.h"

namespace art {

// Oat file compiled with a boot image. oatdump invoked with a boot image.
TEST_P(OatDumpTest, TestDumpOatWithRuntimeWithBootImage) {
  ASSERT_TRUE(GenerateAppOdexFile(GetParam()));
  ASSERT_TRUE(Exec(GetParam(),
                   kArgOatApp | kArgBootImage | kArgBcp | kArgIsa,
                   {},
                   kExpectOat | kExpectCode | kExpectBssMappingsForBcp));
}

// Oat file compiled without a boot image. oatdump invoked without a boot image.
TEST_P(OatDumpTest, TestDumpOatWithRuntimeWithNoBootImage) {
  TEST_DISABLED_FOR_DEBUG_BUILD();  // DCHECK failed.
  ASSERT_TRUE(GenerateAppOdexFile(GetParam(), {"--boot-image=/nonx/boot.art"}));
  ASSERT_TRUE(Exec(GetParam(),
                   kArgOatApp | kArgBcp | kArgIsa,
                   {"--boot-image=/nonx/boot.art"},
                   kExpectOat | kExpectCode | kExpectBssMappingsForBcp));
}

// Dex code cannot be found in the vdex file, and no --dex-file is specified. Dump header only.
TEST_P(OatDumpTest, TestDumpOatTryWithRuntimeDexNotFound) {
  ASSERT_TRUE(
      GenerateAppOdexFile(GetParam(), {"--dex-location=/nonx/app.jar", "--copy-dex-files=false"}));
  ASSERT_TRUE(Exec(GetParam(), kArgOatApp | kArgBootImage | kArgBcp | kArgIsa, {}, kExpectOat));
}

// Dex code cannot be found in the vdex file, but can be found in the specified dex file.
TEST_P(OatDumpTest, TestDumpOatWithRuntimeDexSpecified) {
  ASSERT_TRUE(
      GenerateAppOdexFile(GetParam(), {"--dex-location=/nonx/app.jar", "--copy-dex-files=false"}));
  ASSERT_TRUE(Exec(GetParam(),
                   kArgOatApp | kArgDexApp | kArgBootImage | kArgBcp | kArgIsa,
                   {},
                   kExpectOat | kExpectCode | kExpectBssMappingsForBcp));
}

// Oat file compiled with a boot image. oatdump invoked without a boot image.
TEST_P(OatDumpTest, TestDumpOatWithoutRuntimeBcpMismatch) {
  ASSERT_TRUE(GenerateAppOdexFile(GetParam()));
  ASSERT_TRUE(Exec(GetParam(),
                   kArgOatApp | kArgBcp | kArgIsa,
                   {"--boot-image=/nonx/boot.art"},
                   kExpectOat | kExpectCode | kExpectBssOffsetsForBcp));
}

// Bootclasspath not specified.
TEST_P(OatDumpTest, TestDumpOatWithoutRuntimeNoBcp) {
  ASSERT_TRUE(GenerateAppOdexFile(GetParam()));
  ASSERT_TRUE(Exec(GetParam(), kArgOatApp, {}, kExpectOat | kExpectCode | kExpectBssOffsetsForBcp));
}

// Dex code cannot be found in the vdex file, and no --dex-file is specified. Dump header only.
TEST_P(OatDumpTest, TestDumpOatWithoutRuntimeDexNotFound) {
  ASSERT_TRUE(
      GenerateAppOdexFile(GetParam(), {"--dex-location=/nonx/app.jar", "--copy-dex-files=false"}));
  ASSERT_TRUE(Exec(GetParam(), kArgOatApp, {}, kExpectOat));
}

// Dex code cannot be found in the vdex file, but can be found in the specified dex file.
TEST_P(OatDumpTest, TestDumpOatWithoutRuntimeDexSpecified) {
  ASSERT_TRUE(
      GenerateAppOdexFile(GetParam(), {"--dex-location=/nonx/app.jar", "--copy-dex-files=false"}));
  ASSERT_TRUE(Exec(
      GetParam(), kArgOatApp | kArgDexApp, {}, kExpectOat | kExpectCode | kExpectBssOffsetsForBcp));
}

TEST_P(OatDumpTest, TestDumpAppImageWithBootImage) {
  TEST_DISABLED_WITHOUT_BAKER_READ_BARRIERS();  // GC bug, b/126305867
  const std::string app_image_arg = "--app-image-file=" + GetAppImageName();
  ASSERT_TRUE(GenerateAppOdexFile(GetParam(), {app_image_arg}));
  ASSERT_TRUE(Exec(GetParam(),
                   kArgAppImage | kArgOatApp | kArgBootImage | kArgBcp | kArgIsa,
                   {},
                   kExpectImage | kExpectOat | kExpectCode | kExpectBssMappingsForBcp));
}

// Deprecated usage, but checked for compatibility.
TEST_P(OatDumpTest, TestDumpAppImageWithBootImageLegacy) {
  TEST_DISABLED_WITHOUT_BAKER_READ_BARRIERS();  // GC bug, b/126305867
  const std::string app_image_arg = "--app-image-file=" + GetAppImageName();
  ASSERT_TRUE(GenerateAppOdexFile(GetParam(), {app_image_arg}));
  ASSERT_TRUE(Exec(GetParam(),
                   kArgAppImage | kArgImage | kArgBcp | kArgIsa,
                   {"--app-oat=" + GetAppOdexName()},
                   kExpectImage | kExpectOat | kExpectCode | kExpectBssMappingsForBcp));
}

TEST_P(OatDumpTest, TestDumpAppImageInvalidPath) {
  TEST_DISABLED_WITHOUT_BAKER_READ_BARRIERS();  // GC bug, b/126305867
  const std::string app_image_arg = "--app-image-file=" + GetAppImageName();
  ASSERT_TRUE(GenerateAppOdexFile(GetParam(), {app_image_arg}));
  ASSERT_TRUE(Exec(GetParam(),
                   kArgOatApp | kArgBootImage | kArgBcp | kArgIsa,
                   {"--app-image=missing_app_image.art"},
                   /*expects=*/0,
                   /*expect_failure=*/true));
}

// The runtime can start, but the boot image check should fail.
TEST_P(OatDumpTest, TestDumpAppImageWithWrongBootImage) {
  TEST_DISABLED_WITHOUT_BAKER_READ_BARRIERS();  // GC bug, b/126305867
  const std::string app_image_arg = "--app-image-file=" + GetAppImageName();
  ASSERT_TRUE(GenerateAppOdexFile(GetParam(), {app_image_arg}));
  ASSERT_TRUE(Exec(GetParam(),
                   kArgAppImage | kArgOatApp | kArgBcp | kArgIsa,
                   {"--boot-image=/nonx/boot.art"},
                   /*expects=*/0,
                   /*expect_failure=*/true));
}

// Not possible.
TEST_P(OatDumpTest, TestDumpAppImageWithoutRuntime) {
  TEST_DISABLED_WITHOUT_BAKER_READ_BARRIERS();  // GC bug, b/126305867
  const std::string app_image_arg = "--app-image-file=" + GetAppImageName();
  ASSERT_TRUE(GenerateAppOdexFile(GetParam(), {app_image_arg}));
  ASSERT_TRUE(Exec(GetParam(),
                   kArgAppImage | kArgOatApp,
                   {},
                   /*expects=*/0,
                   /*expect_failure=*/true));
}

// Dex code cannot be found in the vdex file, and no --dex-file is specified. Cannot dump app image.
TEST_P(OatDumpTest, TestDumpAppImageDexNotFound) {
  TEST_DISABLED_WITHOUT_BAKER_READ_BARRIERS();  // GC bug, b/126305867
  const std::string app_image_arg = "--app-image-file=" + GetAppImageName();
  ASSERT_TRUE(GenerateAppOdexFile(
      GetParam(), {app_image_arg, "--dex-location=/nonx/app.jar", "--copy-dex-files=false"}));
  ASSERT_TRUE(Exec(GetParam(),
                   kArgAppImage | kArgOatApp | kArgBootImage | kArgBcp | kArgIsa,
                   {},
                   /*expects=*/0,
                   /*expect_failure=*/true));
}

// Dex code cannot be found in the vdex file, but can be found in the specified dex file.
TEST_P(OatDumpTest, TestDumpAppImageDexSpecified) {
  TEST_DISABLED_WITHOUT_BAKER_READ_BARRIERS();  // GC bug, b/126305867
  const std::string app_image_arg = "--app-image-file=" + GetAppImageName();
  ASSERT_TRUE(GenerateAppOdexFile(
      GetParam(), {app_image_arg, "--dex-location=/nonx/app.jar", "--copy-dex-files=false"}));
  ASSERT_TRUE(Exec(GetParam(),
                   kArgAppImage | kArgOatApp | kArgDexApp | kArgBootImage | kArgBcp | kArgIsa,
                   {},
                   kExpectImage | kExpectOat | kExpectCode | kExpectBssMappingsForBcp));
}

}  // namespace art
