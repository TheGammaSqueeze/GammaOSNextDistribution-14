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

#include "chre/platform/linux/task_util/task.h"

namespace chre {
namespace task_manager_internal {

Task::Task(const TaskFunction &func, std::chrono::nanoseconds intervalOrDelay,
           uint32_t id, bool isOneShot)
    : mExecutionTimestamp(std::chrono::steady_clock::now() + intervalOrDelay),
      mRepeatInterval(isOneShot ? std::chrono::nanoseconds(0)
                                : intervalOrDelay),
      mFunc(func),
      mId(id),
      mHasExecuted(false) {}

Task::Task(const Task &rhs)
    : mExecutionTimestamp(rhs.mExecutionTimestamp),
      mRepeatInterval(rhs.mRepeatInterval),
      mFunc(rhs.mFunc),
      mId(rhs.mId),
      mHasExecuted(rhs.mHasExecuted) {}

Task &Task::operator=(const Task &rhs) {
  if (this != &rhs) {
    Task rhsCopy(rhs);
    std::swap(mExecutionTimestamp, rhsCopy.mExecutionTimestamp);
    std::swap(mRepeatInterval, rhsCopy.mRepeatInterval);

    {
      std::lock_guard lock(mExecutionMutex);
      std::swap(mFunc, rhsCopy.mFunc);
    }

    std::swap(mId, rhsCopy.mId);
    std::swap(mHasExecuted, rhsCopy.mHasExecuted);
  }
  return *this;
}

void Task::cancel() {
  std::lock_guard lock(mExecutionMutex);
  mRepeatInterval = std::chrono::nanoseconds(0);
  mFunc.reset();
}

void Task::execute() {
  TaskFunction func;
  {
    std::lock_guard lock(mExecutionMutex);
    if (!mFunc.has_value()) {
      return;
    }

    func = mFunc.value();
  }
  func();
  mHasExecuted = true;
  if (isRepeating()) {
    mExecutionTimestamp = std::chrono::steady_clock::now() + mRepeatInterval;
  }
}

}  // namespace task_manager_internal
}  // namespace chre
