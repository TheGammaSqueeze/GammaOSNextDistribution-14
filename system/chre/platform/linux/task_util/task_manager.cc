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

#include <cassert>
#include <iostream>
#include <limits>
#include <memory>

#include "chre/platform/linux/task_util/task_manager.h"

namespace chre {

TaskManager::TaskManager()
    : mQueue(std::greater<Task>()),
      mCurrentTask(nullptr),
      mContinueRunningThread(true),
      mCurrentId(0) {
  mThread = std::thread(&TaskManager::run, this);
}

TaskManager::~TaskManager() {
  flushTasks();

  {
    std::lock_guard<std::mutex> lock(mMutex);
    mContinueRunningThread = false;
    mConditionVariable.notify_all();
  }

  if (mThread.joinable()) {
    mThread.join();
  }
}

std::optional<uint32_t> TaskManager::addTask(
    const Task::TaskFunction &func, std::chrono::nanoseconds intervalOrDelay,
    bool isOneShot) {
  std::lock_guard<std::mutex> lock(mMutex);
  bool success = false;

  uint32_t returnId;
  if (!mContinueRunningThread) {
    LOGW("Execution thread is shutting down. Cannot add a task.");
  } else {
    // select the next ID
    assert(mCurrentId < std::numeric_limits<uint32_t>::max());
    returnId = mCurrentId++;
    Task task(func, intervalOrDelay, returnId, isOneShot);
    success = mQueue.push(task);
  }

  if (success) {
    mConditionVariable.notify_all();
    return returnId;
  } else {
    return std::optional<uint32_t>();
  }
}

bool TaskManager::cancelTask(uint32_t taskId) {
  std::lock_guard<std::mutex> lock(mMutex);

  bool success = false;
  if (!mContinueRunningThread) {
    LOGW("Execution thread is shutting down. Cannot cancel a task.");
  } else if (mCurrentTask != nullptr && mCurrentTask->getId() == taskId) {
    // The currently executing task may want to cancel itself.
    mCurrentTask->cancel();
    success = true;
  } else {
    for (auto iter = mQueue.begin(); iter != mQueue.end(); ++iter) {
      if (iter->getId() == taskId) {
        iter->cancel();
        success = true;
        break;
      }
    }
  }

  return success;
}

void TaskManager::flushTasks() {
  std::lock_guard<std::mutex> lock(mMutex);
  while (!mQueue.empty()) {
    mQueue.pop();
  }
}

void TaskManager::run() {
  while (true) {
    Task task;
    {
      std::unique_lock<std::mutex> lock(mMutex);
      mConditionVariable.wait(lock, [this]() {
        return !mContinueRunningThread || !mQueue.empty();
      });
      if (!mContinueRunningThread) {
        return;
      }

      task = mQueue.top();
      if (!task.isReadyToExecute()) {
        auto waitTime =
            task.getExecutionTimestamp() - std::chrono::steady_clock::now();
        if (waitTime.count() > 0) {
          mConditionVariable.wait_for(lock, waitTime);
        }

        /**
         * We continue here instead of executing the same task because we are
         * not guaranteed that the condition variable was not spuriously woken
         * up, and another task with a timestamp < the current task could have
         * been added in the current time.
         */
        continue;
      }

      mQueue.pop();
      mCurrentTask = &task;
    }
    task.execute();
    {
      std::lock_guard<std::mutex> lock(mMutex);
      mCurrentTask = nullptr;

      if (task.isRepeating() && !mQueue.push(task)) {
        LOGE("TaskManager: Could not push task to priority queue");
      }
    }
  }
}

}  // namespace chre
