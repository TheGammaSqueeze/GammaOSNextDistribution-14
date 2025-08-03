/*
 * Copyright 2020 The Android Open Source Project
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
 *   Functions generated:27
 */

#include <base/functional/bind.h>

#include <cstdint>

#include "btif/include/btif_common.h"
#include "include/hardware/bluetooth.h"
#include "test/common/jni_thread.h"
#include "test/common/mock_functions.h"

bool is_on_jni_thread() {
  inc_func_call_count(__func__);
  return false;
}
bt_status_t btif_transfer_context(tBTIF_CBACK* /* p_cback */,
                                  uint16_t /* event */, char* /* p_params */,
                                  int /* param_len */,
                                  tBTIF_COPY_CBACK* /* p_copy_cback */) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}
bt_status_t do_in_jni_thread(base::OnceClosure task) {
  inc_func_call_count(__func__);
  do_in_jni_thread_task_queue.push(std::move(task));
  return BT_STATUS_SUCCESS;
}
bt_status_t do_in_jni_thread(const base::Location& /* from_here */,
                             base::OnceClosure task) {
  inc_func_call_count(__func__);
  do_in_jni_thread_task_queue.push(std::move(task));
  return BT_STATUS_SUCCESS;
}
