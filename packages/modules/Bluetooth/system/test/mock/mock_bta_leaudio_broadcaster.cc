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
#include <hardware/bt_le_audio.h>

#include "bta/include/bta_le_audio_broadcaster_api.h"
#include "test/common/mock_functions.h"

void LeAudioBroadcaster::DebugDump(int) { inc_func_call_count(__func__); }
void LeAudioBroadcaster::Initialize(
    bluetooth::le_audio::LeAudioBroadcasterCallbacks*,
    base::RepeatingCallback<bool()>) {
  inc_func_call_count(__func__);
}
void LeAudioBroadcaster::Stop() { inc_func_call_count(__func__); }
void LeAudioBroadcaster::Cleanup() { inc_func_call_count(__func__); }
LeAudioBroadcaster* LeAudioBroadcaster::Get() {
  inc_func_call_count(__func__);
  return nullptr;
}
