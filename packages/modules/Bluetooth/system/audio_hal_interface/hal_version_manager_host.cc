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

#include "hal_version_manager.h"

namespace bluetooth {
namespace audio {

const BluetoothAudioHalVersion BluetoothAudioHalVersion::VERSION_UNAVAILABLE =
    BluetoothAudioHalVersion();
const BluetoothAudioHalVersion BluetoothAudioHalVersion::VERSION_2_0 =
    BluetoothAudioHalVersion(BluetoothAudioHalTransport::HIDL, 2, 0);
const BluetoothAudioHalVersion BluetoothAudioHalVersion::VERSION_2_1 =
    BluetoothAudioHalVersion(BluetoothAudioHalTransport::HIDL, 2, 1);
const BluetoothAudioHalVersion BluetoothAudioHalVersion::VERSION_AIDL_V1 =
    BluetoothAudioHalVersion(BluetoothAudioHalTransport::AIDL, 1, 0);
const BluetoothAudioHalVersion BluetoothAudioHalVersion::VERSION_AIDL_V2 =
    BluetoothAudioHalVersion(BluetoothAudioHalTransport::AIDL, 2, 0);
const BluetoothAudioHalVersion BluetoothAudioHalVersion::VERSION_AIDL_V3 =
    BluetoothAudioHalVersion(BluetoothAudioHalTransport::AIDL, 3, 0);
const BluetoothAudioHalVersion BluetoothAudioHalVersion::VERSION_AIDL_V4 =
    BluetoothAudioHalVersion(BluetoothAudioHalTransport::AIDL, 4, 0);

std::unique_ptr<HalVersionManager> HalVersionManager::instance_ptr = nullptr;

BluetoothAudioHalVersion HalVersionManager::GetHalVersion() {
  return BluetoothAudioHalVersion::VERSION_UNAVAILABLE;
}

BluetoothAudioHalTransport HalVersionManager::GetHalTransport() {
  return BluetoothAudioHalTransport::UNKNOWN;
}

android::sp<IBluetoothAudioProvidersFactory_2_1>
HalVersionManager::GetProvidersFactory_2_1() {
  return nullptr;
}

android::sp<IBluetoothAudioProvidersFactory_2_0>
HalVersionManager::GetProvidersFactory_2_0() {
  return nullptr;
}

HalVersionManager::HalVersionManager() {
  hal_version_ = BluetoothAudioHalVersion::VERSION_UNAVAILABLE;
  hal_transport_ = BluetoothAudioHalTransport::UNKNOWN;
}

}  // namespace audio
}  // namespace bluetooth
