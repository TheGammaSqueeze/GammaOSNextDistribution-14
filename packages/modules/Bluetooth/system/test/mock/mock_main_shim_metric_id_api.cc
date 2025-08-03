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
 *   Functions generated:7
 */

#include "main/shim/metric_id_api.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace shim {

bool InitMetricIdAllocator(
    const std::unordered_map<RawAddress, int>& /* paired_device_map */,
    CallbackLegacy /* save_id_callback */,
    CallbackLegacy /* forget_device_callback */) {
  inc_func_call_count(__func__);
  return false;
}
bool CloseMetricIdAllocator() {
  inc_func_call_count(__func__);
  return false;
}
bool IsEmptyMetricIdAllocator() {
  inc_func_call_count(__func__);
  return false;
}
bool IsValidIdFromMetricIdAllocator(const int /* id */) {
  inc_func_call_count(__func__);
  return false;
}
bool SaveDeviceOnMetricIdAllocator(const RawAddress& /* raw_address */) {
  inc_func_call_count(__func__);
  return false;
}
int AllocateIdFromMetricIdAllocator(const RawAddress& /* raw_address */) {
  inc_func_call_count(__func__);
  return 0;
}
void ForgetDeviceFromMetricIdAllocator(const RawAddress& /* raw_address */) {
  inc_func_call_count(__func__);
}

}  // namespace shim
}  // namespace bluetooth
