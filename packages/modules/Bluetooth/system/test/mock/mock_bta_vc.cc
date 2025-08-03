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

#include <base/functional/bind.h>
#include <base/logging.h>
#include <base/strings/string_number_conversions.h>
#include <hardware/bt_vc.h>

#include "bta/include/bta_gatt_queue.h"
#include "bta/include/bta_vc_api.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

void VolumeControl::AddFromStorage(const RawAddress& address) {
  inc_func_call_count(__func__);
}
void VolumeControl::CleanUp() { inc_func_call_count(__func__); }
void VolumeControl::DebugDump(int fd) { inc_func_call_count(__func__); }
VolumeControl* VolumeControl::Get(void) {
  inc_func_call_count(__func__);
  return nullptr;
}
bool VolumeControl::IsVolumeControlRunning() {
  inc_func_call_count(__func__);
  return false;
}
void VolumeControl::Initialize(bluetooth::vc::VolumeControlCallbacks* callbacks,
                               const base::Closure& initCb) {
  inc_func_call_count(__func__);
}
