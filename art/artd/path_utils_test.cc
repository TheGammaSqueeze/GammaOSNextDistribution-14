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

#include "path_utils.h"

#include "aidl/com/android/server/art/BnArtd.h"
#include "android-base/result-gmock.h"
#include "base/common_art_test.h"
#include "gtest/gtest.h"

namespace art {
namespace artd {
namespace {

using ::aidl::com::android::server::art::ArtifactsPath;
using ::aidl::com::android::server::art::DexMetadataPath;
using ::aidl::com::android::server::art::ProfilePath;
using ::android::base::testing::HasError;
using ::android::base::testing::HasValue;
using ::android::base::testing::WithMessage;

using PrebuiltProfilePath = ProfilePath::PrebuiltProfilePath;
using PrimaryCurProfilePath = ProfilePath::PrimaryCurProfilePath;
using PrimaryRefProfilePath = ProfilePath::PrimaryRefProfilePath;
using SecondaryCurProfilePath = ProfilePath::SecondaryCurProfilePath;
using SecondaryRefProfilePath = ProfilePath::SecondaryRefProfilePath;
using TmpProfilePath = ProfilePath::TmpProfilePath;

using std::literals::operator""s;  // NOLINT

class PathUtilsTest : public CommonArtTest {};

TEST_F(PathUtilsTest, BuildArtBinPath) {
  auto scratch_dir = std::make_unique<ScratchDir>();
  auto art_root_env = ScopedUnsetEnvironmentVariable("ANDROID_ART_ROOT");
  setenv("ANDROID_ART_ROOT", scratch_dir->GetPath().c_str(), /*overwrite=*/1);
  EXPECT_THAT(BuildArtBinPath("foo"), HasValue(scratch_dir->GetPath() + "/bin/foo"));
}

TEST_F(PathUtilsTest, BuildOatPath) {
  EXPECT_THAT(
      BuildOatPath(ArtifactsPath{.dexPath = "/a/b.apk", .isa = "arm64", .isInDalvikCache = false}),
      HasValue("/a/oat/arm64/b.odex"));
}

TEST_F(PathUtilsTest, BuildOatPathDalvikCache) {
  EXPECT_THAT(
      BuildOatPath(ArtifactsPath{.dexPath = "/a/b.apk", .isa = "arm64", .isInDalvikCache = true}),
      HasValue(android_data_ + "/dalvik-cache/arm64/a@b.apk@classes.dex"));
}

TEST_F(PathUtilsTest, BuildOatPathInvalidDexPath) {
  EXPECT_THAT(
      BuildOatPath(ArtifactsPath{.dexPath = "a/b.apk", .isa = "arm64", .isInDalvikCache = false}),
      HasError(WithMessage("Path 'a/b.apk' is not an absolute path")));
}

TEST_F(PathUtilsTest, BuildOatPathInvalidIsa) {
  EXPECT_THAT(BuildOatPath(
                  ArtifactsPath{.dexPath = "/a/b.apk", .isa = "invalid", .isInDalvikCache = false}),
              HasError(WithMessage("Instruction set 'invalid' is invalid")));
}

TEST_F(PathUtilsTest, OatPathToVdexPath) {
  EXPECT_EQ(OatPathToVdexPath("/a/oat/arm64/b.odex"), "/a/oat/arm64/b.vdex");
}

TEST_F(PathUtilsTest, OatPathToArtPath) {
  EXPECT_EQ(OatPathToArtPath("/a/oat/arm64/b.odex"), "/a/oat/arm64/b.art");
}

TEST_F(PathUtilsTest, BuildPrimaryRefProfilePath) {
  EXPECT_THAT(BuildPrimaryRefProfilePath(PrimaryRefProfilePath{.packageName = "com.android.foo",
                                                               .profileName = "primary"}),
              HasValue(android_data_ + "/misc/profiles/ref/com.android.foo/primary.prof"));
}

TEST_F(PathUtilsTest, BuildPrimaryRefProfilePathPackageNameOk) {
  EXPECT_THAT(BuildPrimaryRefProfilePath(
                  PrimaryRefProfilePath{.packageName = "...", .profileName = "primary"}),
              HasValue(android_data_ + "/misc/profiles/ref/.../primary.prof"));
}

TEST_F(PathUtilsTest, BuildPrimaryRefProfilePathPackageNameWrong) {
  EXPECT_THAT(BuildPrimaryRefProfilePath(
                  PrimaryRefProfilePath{.packageName = "..", .profileName = "primary"}),
              HasError(WithMessage("Invalid packageName '..'")));
  EXPECT_THAT(BuildPrimaryRefProfilePath(
                  PrimaryRefProfilePath{.packageName = "a/b", .profileName = "primary"}),
              HasError(WithMessage("packageName 'a/b' has invalid character '/'")));
}

TEST_F(PathUtilsTest, BuildPrimaryRefProfilePathProfileNameOk) {
  EXPECT_THAT(BuildPrimaryRefProfilePath(
                  PrimaryRefProfilePath{.packageName = "com.android.foo", .profileName = ".."}),
              HasValue(android_data_ + "/misc/profiles/ref/com.android.foo/...prof"));
}

TEST_F(PathUtilsTest, BuildPrimaryRefProfilePathProfileNameWrong) {
  EXPECT_THAT(BuildPrimaryRefProfilePath(
                  PrimaryRefProfilePath{.packageName = "com.android.foo", .profileName = "a/b"}),
              HasError(WithMessage("profileName 'a/b' has invalid character '/'")));
}

TEST_F(PathUtilsTest, BuildFinalProfilePathForPrimary) {
  EXPECT_THAT(BuildFinalProfilePath(TmpProfilePath{
                  .finalPath = PrimaryRefProfilePath{.packageName = "com.android.foo",
                                                     .profileName = "primary"},
                  .id = "12345"}),
              HasValue(android_data_ + "/misc/profiles/ref/com.android.foo/primary.prof"));
}

TEST_F(PathUtilsTest, BuildFinalProfilePathForSecondary) {
  EXPECT_THAT(BuildFinalProfilePath(TmpProfilePath{
                  .finalPath = SecondaryRefProfilePath{.dexPath = android_data_ +
                                                                  "/user/0/com.android.foo/a.apk"},
                  .id = "12345"}),
              HasValue(android_data_ + "/user/0/com.android.foo/oat/a.apk.prof"));
}

TEST_F(PathUtilsTest, BuildTmpProfilePathForPrimary) {
  EXPECT_THAT(
      BuildTmpProfilePath(TmpProfilePath{
          .finalPath =
              PrimaryRefProfilePath{.packageName = "com.android.foo", .profileName = "primary"},
          .id = "12345"}),
      HasValue(android_data_ + "/misc/profiles/ref/com.android.foo/primary.prof.12345.tmp"));
}

TEST_F(PathUtilsTest, BuildTmpProfilePathForSecondary) {
  EXPECT_THAT(BuildTmpProfilePath(TmpProfilePath{
                  .finalPath = SecondaryRefProfilePath{.dexPath = android_data_ +
                                                                  "/user/0/com.android.foo/a.apk"},
                  .id = "12345"}),
              HasValue(android_data_ + "/user/0/com.android.foo/oat/a.apk.prof.12345.tmp"));
}

TEST_F(PathUtilsTest, BuildTmpProfilePathIdWrong) {
  EXPECT_THAT(BuildTmpProfilePath(TmpProfilePath{
                  .finalPath = PrimaryRefProfilePath{.packageName = "com.android.foo",
                                                     .profileName = "primary"},
                  .id = "123/45"}),
              HasError(WithMessage("id '123/45' has invalid character '/'")));
}

TEST_F(PathUtilsTest, BuildPrebuiltProfilePath) {
  EXPECT_THAT(BuildPrebuiltProfilePath(PrebuiltProfilePath{.dexPath = "/a/b.apk"}),
              HasValue("/a/b.apk.prof"));
}

TEST_F(PathUtilsTest, BuildPrimaryCurProfilePath) {
  EXPECT_THAT(BuildPrimaryCurProfilePath(PrimaryCurProfilePath{
                  .userId = 1, .packageName = "com.android.foo", .profileName = "primary"}),
              HasValue(android_data_ + "/misc/profiles/cur/1/com.android.foo/primary.prof"));
}

TEST_F(PathUtilsTest, BuildSecondaryRefProfilePath) {
  EXPECT_THAT(BuildSecondaryRefProfilePath(SecondaryRefProfilePath{
                  .dexPath = android_data_ + "/user/0/com.android.foo/a.apk"}),
              HasValue(android_data_ + "/user/0/com.android.foo/oat/a.apk.prof"));
}

TEST_F(PathUtilsTest, BuildSecondaryCurProfilePath) {
  EXPECT_THAT(BuildSecondaryCurProfilePath(SecondaryCurProfilePath{
                  .dexPath = android_data_ + "/user/0/com.android.foo/a.apk"}),
              HasValue(android_data_ + "/user/0/com.android.foo/oat/a.apk.cur.prof"));
}

TEST_F(PathUtilsTest, BuildDexMetadataPath) {
  EXPECT_THAT(BuildDexMetadataPath(DexMetadataPath{.dexPath = "/a/b.apk"}), HasValue("/a/b.dm"));
}

TEST_F(PathUtilsTest, BuildProfilePath) {
  EXPECT_THAT(BuildProfileOrDmPath(PrimaryRefProfilePath{.packageName = "com.android.foo",
                                                         .profileName = "primary"}),
              HasValue(android_data_ + "/misc/profiles/ref/com.android.foo/primary.prof"));
  EXPECT_THAT(
      BuildProfileOrDmPath(TmpProfilePath{
          .finalPath =
              PrimaryRefProfilePath{.packageName = "com.android.foo", .profileName = "primary"},
          .id = "12345"}),
      HasValue(android_data_ + "/misc/profiles/ref/com.android.foo/primary.prof.12345.tmp"));
  EXPECT_THAT(BuildProfileOrDmPath(PrebuiltProfilePath{.dexPath = "/a/b.apk"}),
              HasValue("/a/b.apk.prof"));
  EXPECT_THAT(BuildProfileOrDmPath(PrimaryCurProfilePath{
                  .userId = 1, .packageName = "com.android.foo", .profileName = "primary"}),
              HasValue(android_data_ + "/misc/profiles/cur/1/com.android.foo/primary.prof"));
  EXPECT_THAT(BuildProfileOrDmPath(DexMetadataPath{.dexPath = "/a/b.apk"}), HasValue("/a/b.dm"));
}

TEST_F(PathUtilsTest, BuildVdexPath) {
  EXPECT_THAT(
      BuildVdexPath(ArtifactsPath{.dexPath = "/a/b.apk", .isa = "arm64", .isInDalvikCache = false}),
      HasValue("/a/oat/arm64/b.vdex"));
}

TEST_F(PathUtilsTest, PathStartsWith) {
  EXPECT_TRUE(PathStartsWith("/a/b", "/a"));
  EXPECT_TRUE(PathStartsWith("/a/b", "/a/"));

  EXPECT_FALSE(PathStartsWith("/a/c", "/a/b"));
  EXPECT_FALSE(PathStartsWith("/ab", "/a"));

  EXPECT_TRUE(PathStartsWith("/a", "/a"));
  EXPECT_TRUE(PathStartsWith("/a/", "/a"));
  EXPECT_TRUE(PathStartsWith("/a", "/a/"));

  EXPECT_TRUE(PathStartsWith("/a", "/"));
  EXPECT_TRUE(PathStartsWith("/", "/"));
  EXPECT_FALSE(PathStartsWith("/", "/a"));
}

}  // namespace
}  // namespace artd
}  // namespace art
