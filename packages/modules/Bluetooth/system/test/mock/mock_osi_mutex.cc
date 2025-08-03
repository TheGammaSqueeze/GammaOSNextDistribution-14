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
#include "test/mock/mock_osi_mutex.h"

#include "test/common/mock_functions.h"

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace osi_mutex {

// Function state capture and return values, if needed
struct mutex_global_lock mutex_global_lock;
struct mutex_global_unlock mutex_global_unlock;

}  // namespace osi_mutex
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace osi_mutex {}  // namespace osi_mutex
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void mutex_global_lock(void) {
  inc_func_call_count(__func__);
  test::mock::osi_mutex::mutex_global_lock();
}
void mutex_global_unlock(void) {
  inc_func_call_count(__func__);
  test::mock::osi_mutex::mutex_global_unlock();
}
// Mocked functions complete
// END mockcify generation
