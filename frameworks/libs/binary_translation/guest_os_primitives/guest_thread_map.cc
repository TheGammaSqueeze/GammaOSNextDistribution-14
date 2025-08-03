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

#include <sys/types.h>  // pid_t
#include <mutex>

#include "berberis/base/checks.h"
#include "berberis/guest_os_primitives/guest_thread.h"

#include "guest_thread_map.h"

namespace berberis {

void GuestThreadMap::ResetThreadTable(pid_t tid, GuestThread* thread) {
  std::lock_guard<std::mutex> lock(mutex_);
  map_.clear();
  map_[tid] = thread;
}

void GuestThreadMap::InsertThread(pid_t tid, GuestThread* thread) {
  std::lock_guard<std::mutex> lock(mutex_);
  auto result = map_.insert({tid, thread});
  CHECK(result.second);
}

GuestThread* GuestThreadMap::RemoveThread(pid_t tid) {
  std::lock_guard<std::mutex> lock(mutex_);
  auto it = map_.find(tid);
  CHECK(it != map_.end());
  GuestThread* thread = it->second;
  map_.erase(it);
  return thread;
}

GuestThread* GuestThreadMap::FindThread(pid_t tid) {
  std::lock_guard<std::mutex> lock(mutex_);
  auto it = map_.find(tid);
  if (it == map_.end()) {
    return nullptr;
  }
  return it->second;
}

}  // namespace berberis