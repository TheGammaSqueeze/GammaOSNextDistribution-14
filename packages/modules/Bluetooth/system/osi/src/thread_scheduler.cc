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

#include <sched.h>
#include <sys/types.h>

namespace {
constexpr int kRealTimeFifoSchedulingPriority = 1;
}  // namespace

bool thread_scheduler_enable_real_time(pid_t linux_tid) {
  struct sched_param rt_params = {.sched_priority =
                                      kRealTimeFifoSchedulingPriority};
  return sched_setscheduler(linux_tid, SCHED_FIFO, &rt_params) == 0;
}

bool thread_scheduler_get_priority_range(int& min, int& max) {
  min = sched_get_priority_min(SCHED_FIFO);
  max = sched_get_priority_max(SCHED_FIFO);
  return (min != -1 && max != -1) ? true : false;
}
