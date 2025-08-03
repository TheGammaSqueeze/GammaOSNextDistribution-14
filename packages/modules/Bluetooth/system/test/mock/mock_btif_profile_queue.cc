/*
 * Copyright 2023 The Android Open Source Project
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
 *   Functions generated:5
 *
 *  mockcify.pl ver 0.6.0
 */

// Mock include file to share data between tests and mock
#include "test/mock/mock_btif_profile_queue.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace btif_profile_queue {

// Function state capture and return values, if needed
struct btif_queue_advance btif_queue_advance;
struct btif_queue_cleanup btif_queue_cleanup;
struct btif_queue_connect btif_queue_connect;
struct btif_queue_connect_next btif_queue_connect_next;
struct btif_queue_release btif_queue_release;

}  // namespace btif_profile_queue
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace btif_profile_queue {

bt_status_t btif_queue_connect::return_value = BT_STATUS_SUCCESS;
bt_status_t btif_queue_connect_next::return_value = BT_STATUS_SUCCESS;

}  // namespace btif_profile_queue
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void btif_queue_advance() {
  inc_func_call_count(__func__);
  test::mock::btif_profile_queue::btif_queue_advance();
}
void btif_queue_cleanup(uint16_t uuid) {
  inc_func_call_count(__func__);
  test::mock::btif_profile_queue::btif_queue_cleanup(uuid);
}
bt_status_t btif_queue_connect(uint16_t uuid, const RawAddress* bda,
                               btif_connect_cb_t connect_cb) {
  inc_func_call_count(__func__);
  return test::mock::btif_profile_queue::btif_queue_connect(uuid, bda,
                                                            connect_cb);
}
bt_status_t btif_queue_connect_next(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_profile_queue::btif_queue_connect_next();
}
void btif_queue_release() {
  inc_func_call_count(__func__);
  test::mock::btif_profile_queue::btif_queue_release();
}
// Mocked functions complete
// END mockcify generation
