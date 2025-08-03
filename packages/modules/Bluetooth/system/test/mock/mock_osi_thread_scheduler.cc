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

/*
 * Generated mock file from original source file
 *   Functions generated:2
 *
 *  mockcify.pl ver 0.3.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_osi_thread_scheduler.h"

#include "test/common/mock_functions.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace osi_thread_scheduler {

// Function state capture and return values, if needed
struct thread_scheduler_enable_real_time thread_scheduler_enable_real_time;
struct thread_scheduler_get_priority_range thread_scheduler_get_priority_range;

}  // namespace osi_thread_scheduler
}  // namespace mock
}  // namespace test

// Mocked functions, if any
bool thread_scheduler_enable_real_time(pid_t linux_tid) {
  inc_func_call_count(__func__);
  return test::mock::osi_thread_scheduler::thread_scheduler_enable_real_time(
      linux_tid);
}
bool thread_scheduler_get_priority_range(int& min, int& max) {
  inc_func_call_count(__func__);
  return test::mock::osi_thread_scheduler::thread_scheduler_get_priority_range(
      min, max);
}
// Mocked functions complete
// END mockcify generation
