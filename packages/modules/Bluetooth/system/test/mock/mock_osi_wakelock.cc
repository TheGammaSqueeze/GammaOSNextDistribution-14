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
 *   Functions generated:6
 *
 *  mockcify.pl ver 0.3.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_osi_wakelock.h"

#include "test/common/mock_functions.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace osi_wakelock {

// Function state capture and return values, if needed
struct wakelock_acquire wakelock_acquire;
struct wakelock_cleanup wakelock_cleanup;
struct wakelock_debug_dump wakelock_debug_dump;
struct wakelock_release wakelock_release;
struct wakelock_set_os_callouts wakelock_set_os_callouts;
struct wakelock_set_paths wakelock_set_paths;

}  // namespace osi_wakelock
}  // namespace mock
}  // namespace test

// Mocked functions, if any
bool wakelock_acquire(void) {
  inc_func_call_count(__func__);
  return test::mock::osi_wakelock::wakelock_acquire();
}
void wakelock_cleanup(void) {
  inc_func_call_count(__func__);
  test::mock::osi_wakelock::wakelock_cleanup();
}
void wakelock_debug_dump(int fd) {
  inc_func_call_count(__func__);
  test::mock::osi_wakelock::wakelock_debug_dump(fd);
}
bool wakelock_release(void) {
  inc_func_call_count(__func__);
  return test::mock::osi_wakelock::wakelock_release();
}
void wakelock_set_os_callouts(bt_os_callouts_t* callouts) {
  inc_func_call_count(__func__);
  test::mock::osi_wakelock::wakelock_set_os_callouts(callouts);
}
void wakelock_set_paths(const char* lock_path, const char* unlock_path) {
  inc_func_call_count(__func__);
  test::mock::osi_wakelock::wakelock_set_paths(lock_path, unlock_path);
}
// Mocked functions complete
// END mockcify generation
