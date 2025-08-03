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

#include "tools/cmdline_builder.h"

#include <utility>

#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace art {
namespace tools {
namespace {

using ::testing::ElementsAre;
using ::testing::IsEmpty;

class CmdlineBuilderTest : public testing::Test {
 protected:
  CmdlineBuilder args_;
};

TEST_F(CmdlineBuilderTest, ContainsOneFormatSpecifier) {
  EXPECT_TRUE(internal::ContainsOneFormatSpecifier("--flag=%s", 's'));
  EXPECT_TRUE(internal::ContainsOneFormatSpecifier("--flag=[%s]", 's'));
  EXPECT_TRUE(internal::ContainsOneFormatSpecifier("--flag=%s%%", 's'));
  EXPECT_TRUE(internal::ContainsOneFormatSpecifier("--flag=[%s%%]", 's'));
  EXPECT_TRUE(internal::ContainsOneFormatSpecifier("--flag=%%%s", 's'));
  EXPECT_FALSE(internal::ContainsOneFormatSpecifier("--flag=", 's'));
  EXPECT_FALSE(internal::ContainsOneFormatSpecifier("--flag=%s%s", 's'));
  EXPECT_FALSE(internal::ContainsOneFormatSpecifier("--flag=%s%", 's'));
  EXPECT_FALSE(internal::ContainsOneFormatSpecifier("--flag=%d", 's'));
  EXPECT_FALSE(internal::ContainsOneFormatSpecifier("--flag=%s%d", 's'));
  EXPECT_FALSE(internal::ContainsOneFormatSpecifier("--flag=%%s", 's'));
}

TEST_F(CmdlineBuilderTest, Add) {
  args_.Add("--flag");
  EXPECT_THAT(args_.Get(), ElementsAre("--flag"));
}

TEST_F(CmdlineBuilderTest, AddRuntime) {
  args_.AddRuntime("--flag");
  EXPECT_THAT(args_.Get(), ElementsAre("--runtime-arg", "--flag"));
}

TEST_F(CmdlineBuilderTest, AddString) {
  args_.Add("--flag=[%s]", "foo");
  EXPECT_THAT(args_.Get(), ElementsAre("--flag=[foo]"));
}

TEST_F(CmdlineBuilderTest, AddRuntimeString) {
  args_.AddRuntime("--flag=[%s]", "foo");
  EXPECT_THAT(args_.Get(), ElementsAre("--runtime-arg", "--flag=[foo]"));
}

TEST_F(CmdlineBuilderTest, AddInt) {
  args_.Add("--flag=[%d]", 123);
  EXPECT_THAT(args_.Get(), ElementsAre("--flag=[123]"));
}

TEST_F(CmdlineBuilderTest, AddRuntimeInt) {
  args_.AddRuntime("--flag=[%d]", 123);
  EXPECT_THAT(args_.Get(), ElementsAre("--runtime-arg", "--flag=[123]"));
}

TEST_F(CmdlineBuilderTest, AddIfNonEmpty) {
  args_.AddIfNonEmpty("--flag=[%s]", "foo");
  EXPECT_THAT(args_.Get(), ElementsAre("--flag=[foo]"));
}

TEST_F(CmdlineBuilderTest, AddIfNonEmptyEmpty) {
  args_.AddIfNonEmpty("--flag=[%s]", "");
  EXPECT_THAT(args_.Get(), IsEmpty());
}

TEST_F(CmdlineBuilderTest, AddRuntimeIfNonEmpty) {
  args_.AddRuntimeIfNonEmpty("--flag=[%s]", "foo");
  EXPECT_THAT(args_.Get(), ElementsAre("--runtime-arg", "--flag=[foo]"));
}

TEST_F(CmdlineBuilderTest, AddRuntimeIfNonEmptyEmpty) {
  args_.AddRuntimeIfNonEmpty("--flag=[%s]", "");
  EXPECT_THAT(args_.Get(), IsEmpty());
}

TEST_F(CmdlineBuilderTest, AddIfTrue) {
  args_.AddIf(true, "--flag");
  EXPECT_THAT(args_.Get(), ElementsAre("--flag"));
}

TEST_F(CmdlineBuilderTest, AddIfFalse) {
  args_.AddIf(false, "--flag");
  EXPECT_THAT(args_.Get(), IsEmpty());
}

TEST_F(CmdlineBuilderTest, AddRuntimeIfTrue) {
  args_.AddRuntimeIf(true, "--flag");
  EXPECT_THAT(args_.Get(), ElementsAre("--runtime-arg", "--flag"));
}

TEST_F(CmdlineBuilderTest, AddRuntimeIfFalse) {
  args_.AddRuntimeIf(false, "--flag");
  EXPECT_THAT(args_.Get(), IsEmpty());
}

TEST_F(CmdlineBuilderTest, Concat) {
  args_.Add("--flag1");
  args_.Add("--flag2");

  CmdlineBuilder other;
  other.Add("--flag3");
  other.Add("--flag4");

  args_.Concat(std::move(other));
  EXPECT_THAT(args_.Get(), ElementsAre("--flag1", "--flag2", "--flag3", "--flag4"));
  // NOLINTNEXTLINE - checking all args have been moved from other to args_
  EXPECT_THAT(other.Get(), IsEmpty());
}

}  // namespace
}  // namespace tools
}  // namespace art
