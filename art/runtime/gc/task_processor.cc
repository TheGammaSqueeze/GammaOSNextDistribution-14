/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "task_processor.h"

#include "base/time_utils.h"
#include "scoped_thread_state_change-inl.h"

namespace art HIDDEN {
namespace gc {

TaskProcessor::TaskProcessor()
    : lock_("Task processor lock", kReferenceProcessorLock),
      cond_("Task processor condition", lock_),
      is_running_(false),
      running_thread_(nullptr) {
}

TaskProcessor::~TaskProcessor() {
  if (!tasks_.empty()) {
    LOG(WARNING) << "TaskProcessor: Finalizing " << tasks_.size() << " unprocessed tasks.";
    for (HeapTask* task : tasks_) {
      task->Finalize();
    }
  }
}

void TaskProcessor::AddTask(Thread* self, HeapTask* task) {
  ScopedThreadStateChange tsc(self, ThreadState::kWaitingForTaskProcessor);
  MutexLock mu(self, lock_);
  tasks_.insert(task);
  cond_.Signal(self);
}

HeapTask* TaskProcessor::GetTask(Thread* self) {
  ScopedThreadStateChange tsc(self, ThreadState::kWaitingForTaskProcessor);
  MutexLock mu(self, lock_);
  while (true) {
    if (tasks_.empty()) {
      if (!is_running_) {
        return nullptr;
      }
      cond_.Wait(self);  // Empty queue, wait until we are signalled.
    } else {
      // Non empty queue, look at the top element and see if we are ready to run it.
      const uint64_t current_time = NanoTime();
      HeapTask* task = *tasks_.begin();
      // If we are shutting down, return the task right away without waiting. Otherwise return the
      // task if it is late enough.
      uint64_t target_time = task->GetTargetRunTime();
      if (!is_running_ || target_time <= current_time) {
        tasks_.erase(tasks_.begin());
        return task;
      }
      DCHECK_GT(target_time, current_time);
      // Wait until we hit the target run time.
      const uint64_t delta_time = target_time - current_time;
      const uint64_t ms_delta = NsToMs(delta_time);
      const uint64_t ns_delta = delta_time - MsToNs(ms_delta);
      cond_.TimedWait(self, static_cast<int64_t>(ms_delta), static_cast<int32_t>(ns_delta));
    }
  }
  UNREACHABLE();
}

void TaskProcessor::UpdateTargetRunTime(Thread* self, HeapTask* task, uint64_t new_target_time) {
  MutexLock mu(self, lock_);
  // Find the task.
  auto range = tasks_.equal_range(task);
  for (auto it = range.first; it != range.second; ++it) {
    if (*it == task) {
      // Check if the target time was updated, if so re-insert then wait.
      if (new_target_time != task->GetTargetRunTime()) {
        tasks_.erase(it);
        task->SetTargetRunTime(new_target_time);
        tasks_.insert(task);
        // If we became the first task then we may need to signal since we changed the task that we
        // are sleeping on.
        if (*tasks_.begin() == task) {
          cond_.Signal(self);
        }
        return;
      }
    }
  }
}

bool TaskProcessor::IsRunning() const {
  MutexLock mu(Thread::Current(), lock_);
  return is_running_;
}

bool TaskProcessor::WaitForThread(Thread* self) {
  // Waiting for too little time here may cause us to fail to get stack traces, since we can't
  // safely do so without identifying a HeapTaskDaemon to avoid it. Waiting too long could
  // conceivably deadlock if we somehow try to get a stack trace on the way to starting the
  // HeapTaskDaemon. Under normal circumstances. this should terminate immediately, since
  // HeapTaskDaemon should normally be running.
  constexpr int kTotalWaitMillis = 100;
  for (int i = 0; i < kTotalWaitMillis; ++i) {
    if (is_running_) {
      return true;
    }
    cond_.TimedWait(self, 1 /*msecs*/, 0 /*nsecs*/);
  }
  LOG(ERROR) << "No identifiable HeapTaskDaemon; unsafe to get thread stacks.";
  return false;
}

bool TaskProcessor::IsRunningThread(Thread* t, bool wait) {
  Thread* self = Thread::Current();
  MutexLock mu(self, lock_);
  if (wait && !WaitForThread(self)) {
    // If Wait failed, either answer may be correct; in our case, true is safer.
    return true;
  }
  return running_thread_ == t;
}

void TaskProcessor::Stop(Thread* self) {
  MutexLock mu(self, lock_);
  is_running_ = false;
  running_thread_ = nullptr;
  cond_.Broadcast(self);
}

void TaskProcessor::Start(Thread* self) {
  MutexLock mu(self, lock_);
  is_running_ = true;
  running_thread_ = self;
}

void TaskProcessor::RunAllTasks(Thread* self) {
  while (true) {
    // Wait and get a task, may be interrupted.
    HeapTask* task = GetTask(self);
    if (task != nullptr) {
      task->Run(self);
      task->Finalize();
    } else if (!IsRunning()) {
      break;
    }
  }
}

}  // namespace gc
}  // namespace art
