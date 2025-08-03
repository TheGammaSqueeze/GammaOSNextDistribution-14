// Copyright (C) 2022 The Android Open Source Project
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

#include <gtest/gtest.h>

#include "ServiceConfig.h"

using namespace audio_proxy::service;

TEST(ServiceConfigTest, GoodConfig) {
  char* argv[] = {"command", "--name", "service", "--stream", "A:1:2"};
  auto config =
      parseServiceConfigFromCommandLine(sizeof(argv) / sizeof(argv[0]), argv);

  ASSERT_TRUE(config);
  EXPECT_EQ(config->name, "service");
  EXPECT_EQ(config->streams.size(), 1);
  EXPECT_EQ(config->streams.begin()->first, "A");
  EXPECT_EQ(config->streams.begin()->second.bufferSizeMs, 1u);
  EXPECT_EQ(config->streams.begin()->second.latencyMs, 2u);
}

TEST(ServiceConfigTest, MultipleStreams) {
  char* argv[] = {"command", "--name",   "service", "--stream",
                  "A:1:2",   "--stream", "B:3:4"};
  auto config =
      parseServiceConfigFromCommandLine(sizeof(argv) / sizeof(argv[0]), argv);

  ASSERT_TRUE(config);
  EXPECT_EQ(config->name, "service");
  EXPECT_EQ(config->streams.size(), 2);

  ASSERT_TRUE(config->streams.count("A"));
  const auto& streamA = config->streams["A"];
  EXPECT_EQ(streamA.bufferSizeMs, 1u);
  EXPECT_EQ(streamA.latencyMs, 2u);

  ASSERT_TRUE(config->streams.count("B"));
  const auto& streamB = config->streams["B"];
  EXPECT_EQ(streamB.bufferSizeMs, 3u);
  EXPECT_EQ(streamB.latencyMs, 4u);
}

TEST(ServiceConfigTest, NoStreamConfig) {
  char* argv[] = {"command", "--name", "service"};
  auto config =
      parseServiceConfigFromCommandLine(sizeof(argv) / sizeof(argv[0]), argv);

  EXPECT_FALSE(config);
}
