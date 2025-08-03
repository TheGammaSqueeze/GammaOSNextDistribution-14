/*
 * Copyright 2022 The Android Open Source Project
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

#include <base/bind_helpers.h>
#include <base/functional/bind.h>

#include "bta/include/bta_has_api.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

namespace le_audio {
namespace has {

void HasClient::Initialize(bluetooth::has::HasClientCallbacks*,
                           base::RepeatingCallback<void()>) {
  inc_func_call_count(__func__);
}
void HasClient::CleanUp() { inc_func_call_count(__func__); }
void HasClient::DebugDump(int) { inc_func_call_count(__func__); }
bool HasClient::IsHasClientRunning() {
  inc_func_call_count(__func__);
  return false;
}
void HasClient::AddFromStorage(RawAddress const&, unsigned char,
                               unsigned short) {
  inc_func_call_count(__func__);
}
HasClient* HasClient::Get() {
  inc_func_call_count(__func__);
  return nullptr;
}

}  // namespace has
}  // namespace le_audio
