// Copyright (C) 2019 Google LLC
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

#include "icing/testing/fake-clock.h"

#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace icing {
namespace lib {

namespace {

using ::testing::Eq;

TEST(FakeClockTest, GetSetSystemTimeOk) {
  FakeClock fake_clock;
  EXPECT_THAT(fake_clock.GetSystemTimeMilliseconds(), Eq(0));

  fake_clock.SetSystemTimeMilliseconds(10);
  EXPECT_THAT(fake_clock.GetSystemTimeMilliseconds(), Eq(10));

  fake_clock.SetSystemTimeMilliseconds(-1);
  EXPECT_THAT(fake_clock.GetSystemTimeMilliseconds(), Eq(-1));
}

TEST(FakeClockTest, GetSetTimerElapsedTimeOk) {
  FakeClock fake_clock;
  EXPECT_THAT(fake_clock.GetNewTimer()->GetElapsedMilliseconds(), Eq(0));

  fake_clock.SetTimerElapsedMilliseconds(10);
  EXPECT_THAT(fake_clock.GetNewTimer()->GetElapsedMilliseconds(), Eq(10));

  fake_clock.SetTimerElapsedMilliseconds(-1);
  EXPECT_THAT(fake_clock.GetNewTimer()->GetElapsedMilliseconds(), Eq(-1));
}

}  // namespace

}  // namespace lib
}  // namespace icing
