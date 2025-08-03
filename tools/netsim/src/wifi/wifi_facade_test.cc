// Copyright 2022 The Android Open Source Project
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

// Unit tests for the WiFi facade.

#include "wifi/wifi_facade.h"

#include "gtest/gtest.h"

namespace netsim::wifi::facade {

class WiFiFacadeTest : public ::testing::Test {};

TEST_F(WiFiFacadeTest, AddAndGetTest) {
  Add(123);

  auto radio = Get(123);
  EXPECT_EQ(model::State::ON, radio.state());
  EXPECT_EQ(0, radio.tx_count());
  EXPECT_EQ(0, radio.rx_count());

  Remove(123);
}

TEST_F(WiFiFacadeTest, RemoveTest) {
  Add(234);

  Remove(234);

  auto radio = Get(234);
  EXPECT_EQ(model::State::UNKNOWN, radio.state());
}

TEST_F(WiFiFacadeTest, PatchTest) {
  Add(345);

  model::Chip::Radio request;
  request.set_state(model::State::OFF);
  Patch(345, request);

  auto radio = Get(345);
  EXPECT_EQ(model::State::OFF, radio.state());

  Remove(345);
}

TEST_F(WiFiFacadeTest, ResetTest) {
  Add(456);

  Reset(456);

  auto radio = Get(456);
  EXPECT_EQ(model::State::ON, radio.state());
  EXPECT_EQ(0, radio.tx_count());
  EXPECT_EQ(0, radio.rx_count());

  Remove(456);
}

}  // namespace netsim::wifi::facade