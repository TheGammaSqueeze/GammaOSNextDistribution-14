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

#pragma once

namespace bluetooth {
namespace audio {
namespace aidl {

using ::aidl::android::hardware::bluetooth::audio::BluetoothAudioStatus;

enum class BluetoothAudioCtrlAck : uint8_t {
  SUCCESS_FINISHED = 0,
  SUCCESS_RECONFIGURATION,
  PENDING,
  FAILURE_UNSUPPORTED,
  FAILURE_BUSY,
  FAILURE_DISCONNECTING,
  FAILURE
};

std::ostream& operator<<(std::ostream& os, const BluetoothAudioCtrlAck& ack);

inline BluetoothAudioStatus BluetoothAudioCtrlAckToHalStatus(
    const BluetoothAudioCtrlAck& ack) {
  switch (ack) {
    case BluetoothAudioCtrlAck::SUCCESS_FINISHED:
      return BluetoothAudioStatus::SUCCESS;
    case BluetoothAudioCtrlAck::FAILURE_UNSUPPORTED:
      return BluetoothAudioStatus::UNSUPPORTED_CODEC_CONFIGURATION;
    case BluetoothAudioCtrlAck::PENDING:
      return BluetoothAudioStatus::FAILURE;
    case BluetoothAudioCtrlAck::FAILURE_BUSY:
      return BluetoothAudioStatus::FAILURE;
    case BluetoothAudioCtrlAck::FAILURE_DISCONNECTING:
      return BluetoothAudioStatus::FAILURE;
    case BluetoothAudioCtrlAck::SUCCESS_RECONFIGURATION:
      return BluetoothAudioStatus::RECONFIGURATION;
    default:
      return BluetoothAudioStatus::FAILURE;
  }
}

}  // namespace aidl
}  // namespace audio
}  // namespace bluetooth
