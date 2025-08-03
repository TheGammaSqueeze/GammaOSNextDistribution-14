//
// Copyright (C) 2020 The Android Open Source Project
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

#include <stddef.h>
#include <stdint.h>

#include <gtest/gtest.h>
#include <kver/kernel_release.h>
#include <kver/kmi_version.h>
#include <kver/utils.h>

using std::string_literals::operator""s;

namespace android::kver {

void CheckValidKmiVersion(const std::string& s) {
  auto kmi_version = KmiVersion::Parse(s);
  ASSERT_TRUE(kmi_version.has_value());
  ASSERT_EQ(s, kmi_version->string());
}

TEST(KmiVersion, Valid) {
  CheckValidKmiVersion("5.4-android12-0");
  CheckValidKmiVersion("0.0-android0-0");
  CheckValidKmiVersion("999.999-android999-999");
  CheckValidKmiVersion(
      "18446744073709551615.18446744073709551615-android18446744073709551615-18446744073709551615");
}

TEST(KmiVersion, Invalid) {
  EXPECT_FALSE(KmiVersion::Parse("5.4.42-android12-0").has_value());

  EXPECT_FALSE(KmiVersion::Parse("4-android12-0").has_value());
  EXPECT_FALSE(KmiVersion::Parse("5.4-androd12-0").has_value());
  EXPECT_FALSE(KmiVersion::Parse("5.4-android12").has_value());
  EXPECT_FALSE(KmiVersion::Parse("5.4-android12-0\n").has_value());
}

TEST(KmiVersion, Parse) {
  auto res = KmiVersion::Parse("5.4-android12-1");
  ASSERT_TRUE(res.has_value());
  EXPECT_EQ(5, res->version());
  EXPECT_EQ(4, res->patch_level());
  EXPECT_EQ(12, res->android_release());
  EXPECT_EQ(1, res->generation());
}

TEST(KmiVersion, ParseWithZero) {
  // Explicit operator""s to ensure \0 character is part of the string.
  auto res = KmiVersion::Parse("5.4-android12-1\0-something"s);
  ASSERT_FALSE(res.has_value());
}

void CheckValidKernelRelease(const std::string& s) {
  auto kernel_release = KernelRelease::Parse(s);
  ASSERT_TRUE(kernel_release.has_value());
  ASSERT_EQ(s, kernel_release->string());
}

TEST(KernelRelease, Valid) {
  CheckValidKernelRelease("5.4.42-android12-0");
  CheckValidKernelRelease("0.0.0-android0-0");
  CheckValidKernelRelease("999.999.999-android999-999");
  CheckValidKernelRelease(
      "18446744073709551615.18446744073709551615.18446744073709551615-android18446744073709551615-"
      "18446744073709551615");
}

TEST(KernelRelease, Invalid) {
  EXPECT_FALSE(KernelRelease::Parse("5.4-android12-0").has_value());

  EXPECT_FALSE(KernelRelease::Parse("4.42-android12-0").has_value());
  EXPECT_FALSE(KernelRelease::Parse("5.4.42-androd12-0").has_value());
  EXPECT_FALSE(KernelRelease::Parse("5.4.42-android12").has_value());
  EXPECT_FALSE(KernelRelease::Parse("5.4.42-android12-0\n").has_value());
}

TEST(KernelRelease, Parse) {
  auto res = KernelRelease::Parse("5.4.42-android12-1");
  ASSERT_TRUE(res.has_value());
  EXPECT_EQ(5, res->version());
  EXPECT_EQ(4, res->patch_level());
  EXPECT_EQ(42, res->sub_level());
  EXPECT_EQ(12, res->android_release());
  EXPECT_EQ(1, res->generation());

  EXPECT_EQ("5.4-android12-1", res->kmi_version().string());
}

TEST(KernelRelease, ParseWithZero) {
  // Explicit operator""s to ensure \0 character is part of the string.
  auto res = KernelRelease::Parse("5.4.42-android12-1\0-something"s, false);
  ASSERT_FALSE(res.has_value());
}

TEST(KernelRelease, ParseWithSuffixDisallowed) {
  auto res = KernelRelease::Parse("5.4.42-android12-1-something", false);
  ASSERT_FALSE(res.has_value());
}

TEST(KernelRelease, ParseWithSuffixAllowed) {
  auto res = KernelRelease::Parse("5.4.42-android12-1-something", true);
  ASSERT_TRUE(res.has_value());
  EXPECT_EQ(5, res->version());
  EXPECT_EQ(4, res->patch_level());
  EXPECT_EQ(42, res->sub_level());
  EXPECT_EQ(12, res->android_release());
  EXPECT_EQ(1, res->generation());
}

bool IsKernelUpdateValid(const std::string& old_release, const std::string& new_release) {
  return IsKernelUpdateValid(KernelRelease::Parse(old_release), KernelRelease::Parse(new_release));
}

TEST(KernelRelease, IsUpdateValid) {
  EXPECT_TRUE(IsKernelUpdateValid("5.4.42-not-gki", "")) << "Legacy update should pass";
  EXPECT_FALSE(IsKernelUpdateValid("5.4.42-android12-0", ""))
      << "From GKI to non-GKI is a downgrade";

  EXPECT_TRUE(IsKernelUpdateValid("5.4.42-not-gki", "5.4.42-android12-0"))
      << "Non-GKI to GKI should pass";
  EXPECT_TRUE(IsKernelUpdateValid("5.4.42-not-gki", "5.4.42-android12-0"))
      << "Non-GKI to GKI should pass";

  EXPECT_TRUE(IsKernelUpdateValid("5.4.42-android12-0", "5.4.42-android12-0"))
      << "Update to self should be fine";

  EXPECT_FALSE(IsKernelUpdateValid("5.4.42-android12-1", "5.4.42-android12-0"))
      << "Should detect downgrade of KMI version";
  EXPECT_FALSE(IsKernelUpdateValid("5.4.42-android13-0", "5.4.42-android12-0"))
      << "Should detect downgrade of KMI version";
  EXPECT_FALSE(IsKernelUpdateValid("5.10.42-android12-0", "5.4.42-android12-0"))
      << "Should detect downgrade of KMI version";
  EXPECT_FALSE(IsKernelUpdateValid("6.1.42-android12-0", "5.4.42-android12-0"))
      << "Should detect downgrade of KMI version";

  EXPECT_FALSE(IsKernelUpdateValid("5.4.42-android13-0", "5.10.42-android12-0"))
      << "Should detect downgrade of Android release";

  EXPECT_FALSE(IsKernelUpdateValid("5.4.43-android12-0", "5.4.42-android12-0"))
      << "Should detect downgrade of w.x.y";
  EXPECT_FALSE(IsKernelUpdateValid("5.10.5-android12-0", "5.4.42-android12-0"))
      << "Should detect downgrade of w.x.y";
  EXPECT_FALSE(IsKernelUpdateValid("6.1.10-android12-0", "5.4.42-android12-0"))
      << "Should detect downgrade of w.x.y";

  EXPECT_TRUE(IsKernelUpdateValid("5.4.42-android12-0", "5.4.42-android12-1"))
      << "Update to newer KMI is fine";
  EXPECT_TRUE(IsKernelUpdateValid("5.4.42-android12-0", "5.4.42-android13-0"))
      << "Update to newer KMI is fine";
  EXPECT_TRUE(IsKernelUpdateValid("5.4.42-android12-0", "5.10.42-android12-0"))
      << "Update to newer KMI is fine";
  EXPECT_TRUE(IsKernelUpdateValid("5.4.42-android12-0", "6.1.42-android12-0"))
      << "Update to newer KMI is fine";

  EXPECT_TRUE(IsKernelUpdateValid("5.4.42-android12-0", "5.4.42-android12-1"))
      << "Update to newer KMI is fine";
  EXPECT_TRUE(IsKernelUpdateValid("5.4.42-android12-1", "5.4.42-android13-0"))
      << "Update to newer KMI is fine";

  EXPECT_TRUE(IsKernelUpdateValid("5.4.42-android12-0", "5.4.43-android12-0"))
      << "Update to newer w.x.y is fine";
  EXPECT_TRUE(IsKernelUpdateValid("5.4.42-android12-0", "5.10.5-android12-0"))
      << "Update to newer w.x.y is fine";
  EXPECT_TRUE(IsKernelUpdateValid("5.4.42-android12-0", "6.1.5-android12-0"))
      << "Update to newer w.x.y is fine";
}

}  // namespace android::kver
