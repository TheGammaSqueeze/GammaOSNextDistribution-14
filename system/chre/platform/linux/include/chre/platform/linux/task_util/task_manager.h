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

#ifndef CHRE_PLATFORM_LINUX_TASK_UTIL_TASK_MANAGER_H_
#define CHRE_PLATFORM_LINUX_TASK_UTIL_TASK_MANAGER_H_

#include <chrono>
#include <condition_variable>
#include <mutex>
#include <optional>
#include <thread>

#include "chre/platform/linux/task_util/task.h"
#include "chre/util/non_copyable.h"
#include "chre/util/priority_queue.h"
#include "chre/util/singleton.h"

namespace chre {

using task_manager_internal::Task;

/**
 * A class to manage a thread that executes arbitrary tasks. These tasks can
 * repeat or be a singular execution. The manager will always execute the next
 * task in chronological order.
 */
class TaskManager : public NonCopyable {
 public:
  /**
   * Construct a new Task Manager object.
   */
  TaskManager();

  /**
   * Destroy the Task Manager object.
   */
  ~TaskManager();

  /**
   * Adds a task to the queue for execution. The manager calls the function func
   * during execution. If intervalOrDelay > 0 and isOneShot is false, the task
   * will repeat every intervalOrDelay nanoseconds. If intervalOrDelay is > 0
   * and isOneShot is true, the task will be executed only once after a delay of
   * intervalOrDelay. If intervalOrDelay == 0, the task will be executed only
   * once with no delay.
   *
   * @param func                     the function to call.
   * @param intervalOrDelay          the interval to repeat.
   * @param isOneShot                if true, the task should be executed only
   * once with a delay of intervalOrDelay.
   * @return                         the ID of the Task object or an empty
   * Optional<> when there is an error.
   */
  std::optional<uint32_t> addTask(
      const Task::TaskFunction &func,
      std::chrono::nanoseconds intervalOrDelay = std::chrono::nanoseconds(0),
      bool isOneShot = false);

  /**
   * Cancels the task with the taskId.
   *
   * @param taskId                   the ID of the task.
   * @return bool                    success.
   */
  bool cancelTask(uint32_t taskId);

  /**
   * Empties the task queue without execution. This call is blocking.
   */
  void flushTasks();

 private:
  /**
   * The run function for the execution thread.
   */
  void run();

  /**
   * The queue of tasks.
   *
   * We use a chre::PriorityQueue here instead of a std::priority_queue because
   * the chre::PriorityQueue allows container iterator access and the other does
   * not.
   */
  PriorityQueue<Task, std::greater<Task>> mQueue;

  /**
   * The current task being executed.
   */
  Task *mCurrentTask;

  /**
   * The execution thread.
   */
  std::thread mThread;

  /**
   * If true, continue executing in the thread. If false, stop executing in the
   * thread.
   */
  bool mContinueRunningThread;

  /**
   * The ID counter for Tasks; keeps the Task's ID unique.
   */
  uint32_t mCurrentId;

  /**
   * The mutex to protect access to the queue.
   */
  std::mutex mMutex;

  /**
   * The condition variable to signal to the execution thread to process more
   * tasks (the queue is not empty).
   */
  std::condition_variable mConditionVariable;
};

// Provide an alias to the TaskManager singleton.
typedef Singleton<TaskManager> TaskManagerSingleton;

}  // namespace chre

#endif  // CHRE_PLATFORM_LINUX_TASK_UTIL_TASK_MANAGER_H_
