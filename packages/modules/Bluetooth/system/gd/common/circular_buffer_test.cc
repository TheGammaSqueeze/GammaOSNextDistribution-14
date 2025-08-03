/*
 * Copyright 2020 The Android Open Source Project
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

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <limits>
#include <string>

#include "common/circular_buffer.h"
#include "os/log.h"

namespace testing {

long long timestamp_{0};
struct TestTimestamper : public bluetooth::common::Timestamper {
  virtual long long GetTimestamp() const override {
    return timestamp_++;
  }
};

TEST(CircularBufferTest, simple) {
  bluetooth::common::TimestampedCircularBuffer<std::string> buffer(10);

  buffer.Push(std::string("One"));
  buffer.Push(std::string("Two"));
  buffer.Push(std::string("Three"));

  auto vec = buffer.Pull();

  ASSERT_STREQ("One", vec[0].entry.c_str());
  ASSERT_STREQ("Two", vec[1].entry.c_str());
  ASSERT_STREQ("Three", vec[2].entry.c_str());

  auto vec2 = buffer.Pull();

  ASSERT_FALSE(vec2.empty());
}

TEST(CircularBufferTest, simple_drain) {
  bluetooth::common::TimestampedCircularBuffer<std::string> buffer(10);

  buffer.Push(std::string("One"));
  buffer.Push(std::string("Two"));
  buffer.Push(std::string("Three"));

  auto vec = buffer.Drain();

  ASSERT_STREQ("One", vec[0].entry.c_str());
  ASSERT_STREQ("Two", vec[1].entry.c_str());
  ASSERT_STREQ("Three", vec[2].entry.c_str());

  auto vec2 = buffer.Pull();

  ASSERT_TRUE(vec2.empty());
}

TEST(CircularBufferTest, test_timestamps) {
  timestamp_ = 0;
  bluetooth::common::TimestampedCircularBuffer<std::string> buffer(10, std::make_unique<TestTimestamper>());

  buffer.Push(std::string("One"));
  buffer.Push(std::string("Two"));
  buffer.Push(std::string("Three"));

  auto vec = buffer.Pull();
  long long timestamp = 0;
  for (auto v : vec) {
    ASSERT_EQ(timestamp, v.timestamp);
    timestamp++;
  }
}

TEST(CircularBufferTest, max_timestamps) {
  bluetooth::common::TimestampedCircularBuffer<std::string> buffer(10);

  std::vector<std::string> test_data;
  for (int i = 0; i < 10 + 1; i++) {
    char buf[255];
    snprintf(buf, 255, "value:%d", i);
    test_data.push_back(std::string(buf));
    buffer.Push(std::string(buf));
  }

  auto vec = buffer.Pull();
  ASSERT_EQ(10ul, vec.size());

  int i = 0 + 1;
  for (auto v : vec) {
    ASSERT_EQ(test_data[i], v.entry.c_str());
    i++;
  }
}

}  // namespace testing
