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

#include <chrono>
#include <thread>

#include "gtest/gtest.h"

#include "chre/platform/linux/task_util/task.h"

namespace {

using chre::task_manager_internal::Task;

uint32_t gVarTask = 0;

constexpr auto incrementGVar = []() { ++gVarTask; };

TEST(Task, Execute) {
  gVarTask = 0;
  std::chrono::milliseconds waitTime(100);
  Task task(incrementGVar, waitTime, 0);
  EXPECT_FALSE(task.isReadyToExecute());
  std::this_thread::sleep_for(waitTime);
  EXPECT_TRUE(task.isReadyToExecute());
  task.execute();
  EXPECT_TRUE(gVarTask == 1);
  EXPECT_TRUE(task.isRepeating());
  EXPECT_FALSE(task.isReadyToExecute());
  auto timeDiff =
      std::chrono::steady_clock::now() - task.getExecutionTimestamp();
  EXPECT_TRUE(
      std::chrono::duration_cast<std::chrono::nanoseconds>(timeDiff).count() <=
      waitTime.count());
  task.cancel();
  EXPECT_FALSE(task.isRepeating());
}

TEST(Task, ExecuteNoRepeat) {
  gVarTask = 0;
  std::chrono::nanoseconds waitTime(0);
  Task task(incrementGVar, waitTime, 0);
  EXPECT_TRUE(task.isReadyToExecute());
  task.execute();
  EXPECT_TRUE(gVarTask == 1);
  EXPECT_TRUE(task.isReadyToExecute());
  EXPECT_FALSE(task.isRepeating());
}

TEST(Task, ComparisonOperators) {
  constexpr uint32_t numTasks = 6;
  Task tasks[numTasks] = {Task(incrementGVar, std::chrono::nanoseconds(0), 0),
                          Task(incrementGVar, std::chrono::nanoseconds(10), 1),
                          Task(incrementGVar, std::chrono::nanoseconds(20), 2),
                          Task(incrementGVar, std::chrono::nanoseconds(30), 3),
                          Task(incrementGVar, std::chrono::nanoseconds(40), 4),
                          Task(incrementGVar, std::chrono::nanoseconds(50), 5)};

  for (uint32_t i = 0; i < numTasks; ++i) {
    if (i < numTasks - 1) {
      EXPECT_TRUE(tasks[i] < tasks[i + 1]);
      EXPECT_TRUE(tasks[i] <= tasks[i + 1]);
      EXPECT_FALSE(tasks[i] > tasks[i + 1]);
      EXPECT_FALSE(tasks[i] >= tasks[i + 1]);
    }
  }
}

}  // namespace
