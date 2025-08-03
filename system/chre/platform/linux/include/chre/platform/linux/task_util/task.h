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

#ifndef CHRE_PLATFORM_LINUX_TASK_UTIL_TASK_H_
#define CHRE_PLATFORM_LINUX_TASK_UTIL_TASK_H_

#include <chrono>
#include <cstdint>
#include <functional>
#include <mutex>
#include <optional>

namespace chre {
namespace task_manager_internal {

/**
 * Represents a task to execute (a function to call) that can be executed once
 * or repeatedly with interval: intervalOrDelay in nanoseconds until
 * cancel() is called.
 *
 * Note: The Task class is not thread-safe nor synchronized properly. It is
 * meant to be externally synchronized (see TaskManager).
 */
class Task {
 public:
  using TaskFunction = std::function<void()>;

  /**
   * Construct an empty Task object.
   */
  Task()
      : mExecutionTimestamp(std::chrono::steady_clock::now()),
        mRepeatInterval(0),
        mId(0),
        mHasExecuted(false) {}

  /**
   * Construct a new Task object.
   *
   * @param func              the function to execute.
   * @param intervalOrDelay   the interval in which to repeat execution or the
   * delay for a one-shot Task.
   * @param id                the unique ID for use with the Task Manager.
   * @param isOneShot         if true, the task should only be executed once
   * after a delay of intervalOrDelay.
   */
  Task(const TaskFunction &func, std::chrono::nanoseconds intervalOrDelay,
       uint32_t id, bool isOneShot = false);

  /**
   * Construct a new Task object.
   *
   * @param rhs               copy constructor args.
   */
  Task(const Task &rhs);

  /**
   * Assignment operator.
   *
   * @param rhs               rhs arg.
   * @return                  this.
   */
  Task &operator=(const Task &rhs);

  /**
   * Stops the task from repeating.
   */
  void cancel();

  /**
   * Executes the function.
   */
  void execute();

  /**
   * Gets the next time the task should execute.
   */
  inline std::chrono::time_point<std::chrono::steady_clock>
  getExecutionTimestamp() const {
    return mExecutionTimestamp;
  }

  /**
   * Gets the ID of the task.
   */
  inline uint32_t getId() const {
    return mId;
  }

  /**
   * Returns true if the task has executed at least once, false if otherwise.
   *
   * @return true             if the task has executed at least once.
   * @return false            if the task has not executed at least once.
   */
  inline bool hasExecuted() const {
    return mHasExecuted;
  }

  /**
   * Returns true if the task is ready to execute (time now is >= task
   * timestamp).
   *
   * @return true             the task can be executed.
   * @return false            do not yet execute the task.
   */
  inline bool isReadyToExecute() const {
    return mExecutionTimestamp <= std::chrono::steady_clock::now();
  }

  /**
   * Returns true if the task is a repeating task - if it has has a
   * intervalOrDelay > 0.
   *
   * @return true             if the task is a repeating task.
   * @return false            otherwise.
   */
  inline bool isRepeating() const {
    return mRepeatInterval.count() > 0;
  }

  /*
   * The following relational operators are comparing execution timestamps.
   */
  inline bool operator<(const Task &rhs) const {
    return mExecutionTimestamp < rhs.mExecutionTimestamp;
  }

  inline bool operator>(const Task &rhs) const {
    return mExecutionTimestamp > rhs.mExecutionTimestamp;
  }

  inline bool operator<=(const Task &rhs) const {
    return mExecutionTimestamp <= rhs.mExecutionTimestamp;
  }

  inline bool operator>=(const Task &rhs) const {
    return mExecutionTimestamp >= rhs.mExecutionTimestamp;
  }

 private:
  /**
   * Time timestamp of when the task should be executed.
   */
  std::chrono::time_point<std::chrono::steady_clock> mExecutionTimestamp;

  /**
   * The amount of time to wait in between repeating the task.
   */
  std::chrono::nanoseconds mRepeatInterval;

  /**
   * The function to execute.
   */
  std::optional<TaskFunction> mFunc;

  /**
   * The ID of the task.
   */
  uint32_t mId;

  /**
   * If the task has executed at least once.
   */
  bool mHasExecuted;

  /**
   * Mutex for execution.
   */
  std::mutex mExecutionMutex;
};

}  // namespace task_manager_internal
}  // namespace chre

#endif  // CHRE_PLATFORM_LINUX_TASK_UTIL_TASK_H_
