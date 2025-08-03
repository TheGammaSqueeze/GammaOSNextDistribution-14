/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef ART_RUNTIME_STARTUP_COMPLETED_TASK_H_
#define ART_RUNTIME_STARTUP_COMPLETED_TASK_H_

#include "base/macros.h"
#include "gc/task_processor.h"

namespace art HIDDEN {

class Thread;

class StartupCompletedTask : public gc::HeapTask {
 public:
  explicit StartupCompletedTask(uint64_t target_run_time) : gc::HeapTask(target_run_time) {}

  void Run(Thread* self) override;
  static void DeleteStartupDexCaches(Thread* self, bool called_by_gc)
      REQUIRES_SHARED(Locks::mutator_lock_);
};

}  // namespace art

#endif  // ART_RUNTIME_STARTUP_COMPLETED_TASK_H_
