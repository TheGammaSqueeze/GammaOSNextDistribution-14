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

#include "chre/util/system/stats_container.h"
#include "gtest/gtest.h"

TEST(StatsContainer, MeanBasicTest) {
  chre::StatsContainer<uint8_t> testContainer;

  ASSERT_EQ(testContainer.getMean(), 0);

  testContainer.addValue(10);
  testContainer.addValue(20);
  ASSERT_EQ(testContainer.getMean(), 15);

  testContainer.addValue(40);
  ASSERT_EQ(testContainer.getMean(), (10 + 20 + 40) / 3);
}

TEST(StatsContainer, UINTMeanOverflowTest) {
  chre::StatsContainer<uint8_t> testContainer;

  testContainer.addValue(200);
  testContainer.addValue(100);
  ASSERT_EQ(testContainer.getMean(), 150);
}

TEST(StatsContainer, AddSmallerValueThanMeanCheck) {
  chre::StatsContainer<uint16_t> testContainer;

  testContainer.addValue(10);
  testContainer.addValue(20);
  testContainer.addValue(30);
  ASSERT_EQ(testContainer.getMean(), 20);

  testContainer.addValue(4);
  ASSERT_EQ(testContainer.getMean(), 16);
}

TEST(StatsContainer, AddBiggerValueThanMeanCheck) {
  chre::StatsContainer<uint16_t> testContainer;

  testContainer.addValue(10);
  testContainer.addValue(20);
  testContainer.addValue(30);
  ASSERT_EQ(testContainer.getMean(), 20);

  testContainer.addValue(40);
  ASSERT_EQ(testContainer.getMean(), 25);
}

TEST(StatsContainer, OverAverageWindowCheck) {
  uint64_t maxCount = 3;
  chre::StatsContainer<uint16_t> testContainer(maxCount);

  testContainer.addValue(10);
  testContainer.addValue(20);
  testContainer.addValue(30);
  ASSERT_EQ(testContainer.getMean(), 20);

  testContainer.addValue(40);

  /**
   * Only check if StatsContainer still works after have more element than its
   * averageWindow. Does not check the correctness of the estimated value
   */
  ASSERT_GT(testContainer.getMean(), 20);
}