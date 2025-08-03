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
 *   Functions generated:14
 */

#define LOG_TAG "bt_shim"

#include "main/shim/shim.h"
#include "test/common/mock_functions.h"

bool bluetooth::shim::is_classic_discovery_only_enabled() {
  inc_func_call_count(__func__);
  return false;
}
namespace test {
namespace mock {
bool bluetooth_shim_is_gd_stack_started_up = false;
}
}  // namespace test
bool bluetooth::shim::is_gd_stack_started_up() {
  inc_func_call_count(__func__);
  return test::mock::bluetooth_shim_is_gd_stack_started_up;
}
