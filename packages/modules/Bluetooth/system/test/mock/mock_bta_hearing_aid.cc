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
 *   Functions generated:8
 */

#include <base/functional/bind.h>
#include <base/functional/callback.h>
#include <base/strings/string_number_conversions.h>

#include <cstdint>

#include "bta/include/bta_gatt_queue.h"
#include "bta/include/bta_hearing_aid_api.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

int HearingAid::GetDeviceCount() {
  inc_func_call_count(__func__);
  return 0;
}

void HearingAid::AddFromStorage(const HearingDevice& dev_info,
                                bool is_acceptlisted) {
  inc_func_call_count(__func__);
}

void HearingAid::DebugDump(int fd) { inc_func_call_count(__func__); }

bool HearingAid::IsHearingAidRunning() {
  inc_func_call_count(__func__);
  return false;
}

void HearingAid::CleanUp() { inc_func_call_count(__func__); }

void HearingAid::Initialize(
    bluetooth::hearing_aid::HearingAidCallbacks* callbacks,
    base::Closure initCb) {
  inc_func_call_count(__func__);
}

void HearingAid::Connect(const RawAddress& address) {
  inc_func_call_count(__func__);
}

void HearingAid::Disconnect(const RawAddress& address) {
  inc_func_call_count(__func__);
}

void HearingAid::AddToAcceptlist(const RawAddress& address) {
  inc_func_call_count(__func__);
}

void HearingAid::SetVolume(int8_t volume) { inc_func_call_count(__func__); }
