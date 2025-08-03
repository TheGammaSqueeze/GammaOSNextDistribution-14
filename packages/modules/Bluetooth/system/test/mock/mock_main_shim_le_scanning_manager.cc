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
 *   Functions generated:3
 */

#include "include/hardware/ble_scanner.h"
#include "main/shim/le_scanning_manager.h"
#include "test/common/mock_functions.h"

::BleScannerInterface* bluetooth::shim::get_ble_scanner_instance() {
  inc_func_call_count(__func__);
  return nullptr;
}
void bluetooth::shim::init_scanning_manager() { inc_func_call_count(__func__); }

bool bluetooth::shim::is_ad_type_filter_supported() {
  inc_func_call_count(__func__);
  return false;
}

void bluetooth::shim::set_ad_type_rsi_filter(bool /* enable */) {
  inc_func_call_count(__func__);
}

void bluetooth::shim::set_empty_filter(bool /* enable */) {
  inc_func_call_count(__func__);
}

void bluetooth::shim::set_target_announcements_filter(bool /* enable */) {
  inc_func_call_count(__func__);
}
