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

#include "audio_hal_interface/hal_version_manager.h"
#include "bta_le_audio_api.h"

bool LeAudioHalVerifier::SupportsLeAudio() {
  return bluetooth::audio::HalVersionManager::GetHalTransport() ==
             bluetooth::audio::BluetoothAudioHalTransport::AIDL ||
         bluetooth::audio::HalVersionManager::GetHalVersion() >=
             bluetooth::audio::BluetoothAudioHalVersion::VERSION_2_1;
}

bool LeAudioHalVerifier::SupportsLeAudioHardwareOffload() {
  return bluetooth::audio::HalVersionManager::GetHalTransport() ==
         bluetooth::audio::BluetoothAudioHalTransport::AIDL;
}

bool LeAudioHalVerifier::SupportsLeAudioBroadcast() {
  return bluetooth::audio::HalVersionManager::GetHalTransport() ==
         bluetooth::audio::BluetoothAudioHalTransport::AIDL;
}

bool LeAudioHalVerifier::SupportsStreamActiveApi() {
  return bluetooth::audio::HalVersionManager::GetHalVersion() >=
         bluetooth::audio::BluetoothAudioHalVersion::VERSION_AIDL_V3;
}
