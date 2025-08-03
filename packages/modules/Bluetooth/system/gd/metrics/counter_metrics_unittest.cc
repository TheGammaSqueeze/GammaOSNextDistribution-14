/*
 * Copyright 2021 The Android Open Source Project
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

#include "metrics/counter_metrics.h"

#include <unordered_map>

#include "gtest/gtest.h"

namespace bluetooth {
namespace metrics {
namespace {

class CounterMetricsTest : public ::testing::Test {
 public:
  class TestableCounterMetrics : public CounterMetrics {
   public:
    void DrainBuffer() {
      DrainBufferedCounters();
    }
    std::unordered_map<int32_t, int64_t> test_counters_;
   private:
    bool Count(int32_t key, int64_t count) override {
      test_counters_[key] = count;
      return true;
    }
    bool IsInitialized() override {
      return true;
    }
  };
  TestableCounterMetrics testable_counter_metrics_;
};

TEST_F(CounterMetricsTest, normal_case) {
  ASSERT_TRUE(testable_counter_metrics_.CacheCount(1, 2));
  ASSERT_TRUE(testable_counter_metrics_.CacheCount(1, 3));
  ASSERT_TRUE(testable_counter_metrics_.CacheCount(2, 4));
  testable_counter_metrics_.DrainBuffer();
  ASSERT_EQ(testable_counter_metrics_.test_counters_[1], 5);
  ASSERT_EQ(testable_counter_metrics_.test_counters_[2], 4);
}

TEST_F(CounterMetricsTest, multiple_drain) {
  ASSERT_TRUE(testable_counter_metrics_.CacheCount(1, 2));
  ASSERT_TRUE(testable_counter_metrics_.CacheCount(1, 3));
  ASSERT_TRUE(testable_counter_metrics_.CacheCount(2, 4));
  testable_counter_metrics_.DrainBuffer();
  ASSERT_EQ(testable_counter_metrics_.test_counters_[1], 5);
  ASSERT_EQ(testable_counter_metrics_.test_counters_[2], 4);
  testable_counter_metrics_.test_counters_.clear();
  ASSERT_TRUE(testable_counter_metrics_.CacheCount(1, 20));
  ASSERT_TRUE(testable_counter_metrics_.CacheCount(1, 30));
  ASSERT_TRUE(testable_counter_metrics_.CacheCount(2, 40));
  ASSERT_TRUE(testable_counter_metrics_.CacheCount(3, 100));
  testable_counter_metrics_.DrainBuffer();
  ASSERT_EQ(testable_counter_metrics_.test_counters_[1], 50);
  ASSERT_EQ(testable_counter_metrics_.test_counters_[2], 40);
  ASSERT_EQ(testable_counter_metrics_.test_counters_[3], 100);
}

TEST_F(CounterMetricsTest, overflow) {
  ASSERT_TRUE(testable_counter_metrics_.CacheCount(1, LLONG_MAX));
  ASSERT_FALSE(testable_counter_metrics_.CacheCount(1, 1));
  ASSERT_FALSE(testable_counter_metrics_.CacheCount(1, 2));
  testable_counter_metrics_.DrainBuffer();
  ASSERT_EQ(testable_counter_metrics_.test_counters_[1], LLONG_MAX);
}

TEST_F(CounterMetricsTest, non_positive) {
  ASSERT_TRUE(testable_counter_metrics_.CacheCount(1, 5));
  ASSERT_FALSE(testable_counter_metrics_.CacheCount(1, 0));
  ASSERT_FALSE(testable_counter_metrics_.CacheCount(1, -1));
  testable_counter_metrics_.DrainBuffer();
  ASSERT_EQ(testable_counter_metrics_.test_counters_[1], 5);
}

}  // namespace
}  // namespace metrics
}  // namespace bluetooth
