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

#include "odr_common.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace art {
namespace odrefresh {

namespace {

using ::android::base::Result;

}

TEST(OdrCommonTest, ParseSecurityPatchStr) {
  Result<int> result = ParseSecurityPatchStr("2022-03-08");
  EXPECT_TRUE(result.ok());
  EXPECT_EQ(result.value(), 20220308);
  EXPECT_FALSE(ParseSecurityPatchStr("").ok());
  EXPECT_FALSE(ParseSecurityPatchStr("20-2203-08").ok());
  EXPECT_FALSE(ParseSecurityPatchStr("20220308").ok());
}

TEST(OdrCommonTest, ShouldDisablePartialCompilation) {
  EXPECT_TRUE(ShouldDisablePartialCompilation("2021-03-05"));
  EXPECT_TRUE(ShouldDisablePartialCompilation("2022-02-05"));
  EXPECT_TRUE(ShouldDisablePartialCompilation("2022-03-04"));
  EXPECT_FALSE(ShouldDisablePartialCompilation("2022-03-05"));
  EXPECT_FALSE(ShouldDisablePartialCompilation("2022-03-06"));
  EXPECT_FALSE(ShouldDisablePartialCompilation("2022-04-04"));
  EXPECT_FALSE(ShouldDisablePartialCompilation("2023-03-04"));
}

TEST(OdrCommonTest, ShouldDisableRefresh) {
  EXPECT_TRUE(ShouldDisableRefresh("32"));
  EXPECT_TRUE(ShouldDisableRefresh("33"));
  EXPECT_FALSE(ShouldDisableRefresh("31"));
  EXPECT_FALSE(ShouldDisableRefresh(""));
  EXPECT_FALSE(ShouldDisableRefresh("invalid"));
}

TEST(OdrCommonTest, CheckBuildUserfaultFdGc) {
  EXPECT_TRUE(CheckBuildUserfaultFdGc(
      /*build_enable_uffd_gc=*/false, /*is_at_most_u=*/false, /*kernel_supports_uffd=*/false));
  EXPECT_FALSE(CheckBuildUserfaultFdGc(
      /*build_enable_uffd_gc=*/true, /*is_at_most_u=*/false, /*kernel_supports_uffd=*/false));
  EXPECT_TRUE(CheckBuildUserfaultFdGc(
      /*build_enable_uffd_gc=*/false, /*is_at_most_u=*/true, /*kernel_supports_uffd=*/false));
  EXPECT_FALSE(CheckBuildUserfaultFdGc(
      /*build_enable_uffd_gc=*/true, /*is_at_most_u=*/true, /*kernel_supports_uffd=*/false));
  EXPECT_TRUE(CheckBuildUserfaultFdGc(
      /*build_enable_uffd_gc=*/false, /*is_at_most_u=*/false, /*kernel_supports_uffd=*/true));
  EXPECT_TRUE(CheckBuildUserfaultFdGc(
      /*build_enable_uffd_gc=*/true, /*is_at_most_u=*/false, /*kernel_supports_uffd=*/true));
  EXPECT_FALSE(CheckBuildUserfaultFdGc(
      /*build_enable_uffd_gc=*/false, /*is_at_most_u=*/true, /*kernel_supports_uffd=*/true));
  EXPECT_TRUE(CheckBuildUserfaultFdGc(
      /*build_enable_uffd_gc=*/true, /*is_at_most_u=*/true, /*kernel_supports_uffd=*/true));
}

}  // namespace odrefresh
}  // namespace art
