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

#include "service.h"

#include "android-base/result-gmock.h"
#include "common_runtime_test.h"
#include "gtest/gtest.h"

namespace art {
namespace service {
namespace {

using ::android::base::testing::HasError;
using ::android::base::testing::Ok;
using ::android::base::testing::WithMessage;

using std::literals::operator""s;  // NOLINT

class ArtServiceTest : public testing::Test {};

TEST_F(ArtServiceTest, ValidatePathElementOk) {
  EXPECT_THAT(ValidatePathElement("com.android.foo", "packageName"), Ok());
  EXPECT_THAT(ValidatePathElement("...", "packageName"), Ok());
  EXPECT_THAT(ValidatePathElement("!@#$%^&*()_+-=", "packageName"), Ok());
}

TEST_F(ArtServiceTest, ValidatePathElementEmpty) {
  EXPECT_THAT(ValidatePathElement("", "packageName"),
              HasError(WithMessage("packageName is empty")));
}

TEST_F(ArtServiceTest, ValidatePathElementDot) {
  EXPECT_THAT(ValidatePathElement(".", "packageName"),
              HasError(WithMessage("Invalid packageName '.'")));
}

TEST_F(ArtServiceTest, ValidatePathElementDotDot) {
  EXPECT_THAT(ValidatePathElement("..", "packageName"),
              HasError(WithMessage("Invalid packageName '..'")));
}

TEST_F(ArtServiceTest, ValidatePathElementSlash) {
  EXPECT_THAT(ValidatePathElement("a/b", "packageName"),
              HasError(WithMessage("packageName 'a/b' has invalid character '/'")));
}

TEST_F(ArtServiceTest, ValidatePathElementNul) {
  EXPECT_THAT(ValidatePathElement("a\0b"s, "packageName"),
              HasError(WithMessage("packageName 'a\0b' has invalid character '\\0'"s)));
}

TEST_F(ArtServiceTest, ValidatePathElementSubstringOk) {
  EXPECT_THAT(ValidatePathElementSubstring("com.android.foo", "packageName"), Ok());
  EXPECT_THAT(ValidatePathElementSubstring(".", "packageName"), Ok());
  EXPECT_THAT(ValidatePathElementSubstring("..", "packageName"), Ok());
  EXPECT_THAT(ValidatePathElementSubstring("...", "packageName"), Ok());
  EXPECT_THAT(ValidatePathElementSubstring("!@#$%^&*()_+-=", "packageName"), Ok());
}

TEST_F(ArtServiceTest, ValidatePathElementSubstringEmpty) {
  EXPECT_THAT(ValidatePathElementSubstring("", "packageName"),
              HasError(WithMessage("packageName is empty")));
}

TEST_F(ArtServiceTest, ValidatePathElementSubstringSlash) {
  EXPECT_THAT(ValidatePathElementSubstring("a/b", "packageName"),
              HasError(WithMessage("packageName 'a/b' has invalid character '/'")));
}

TEST_F(ArtServiceTest, ValidatePathElementSubstringNul) {
  EXPECT_THAT(ValidatePathElementSubstring("a\0b"s, "packageName"),
              HasError(WithMessage("packageName 'a\0b' has invalid character '\\0'"s)));
}

TEST_F(ArtServiceTest, ValidateDexPathOk) { EXPECT_THAT(ValidateDexPath("/a/b.apk"), Ok()); }

TEST_F(ArtServiceTest, ValidateDexPathEmpty) {
  EXPECT_THAT(ValidateDexPath(""), HasError(WithMessage("Path is empty")));
}

TEST_F(ArtServiceTest, ValidateDexPathRelative) {
  EXPECT_THAT(ValidateDexPath("a/b.apk"),
              HasError(WithMessage("Path 'a/b.apk' is not an absolute path")));
}

TEST_F(ArtServiceTest, ValidateDexPathNonNormal) {
  EXPECT_THAT(ValidateDexPath("/a/c/../b.apk"),
              HasError(WithMessage("Path '/a/c/../b.apk' is not in normal form")));
}

TEST_F(ArtServiceTest, ValidateDexPathNul) {
  EXPECT_THAT(ValidateDexPath("/a/\0/b.apk"s),
              HasError(WithMessage("Path '/a/\0/b.apk' has invalid character '\\0'"s)));
}

class ArtServiceGcTest : public CommonRuntimeTest {};

TEST_F(ArtServiceGcTest, GetGarbageCollector) {
  EXPECT_THAT(GetGarbageCollector(), testing::HasSubstr("CollectorType"));
}

}  // namespace
}  // namespace service
}  // namespace art
