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

#include "hearing_aid_software_encoding.h"

#include "aidl/hearing_aid_software_encoding_aidl.h"
#include "hal_version_manager.h"
#include "hidl/hearing_aid_software_encoding_hidl.h"

namespace bluetooth {
namespace audio {
namespace hearing_aid {

// Check if new bluetooth_audio is enabled
bool is_hal_enabled() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return hidl::hearing_aid::is_hal_2_0_enabled();
  }
  return aidl::hearing_aid::is_hal_enabled();
}

// Initialize BluetoothAudio HAL: openProvider
bool init(StreamCallbacks stream_cb,
          bluetooth::common::MessageLoopThread* message_loop) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return hidl::hearing_aid::init(stream_cb, message_loop);
  }
  return aidl::hearing_aid::init(stream_cb, message_loop);
}

// Clean up BluetoothAudio HAL
void cleanup() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::hearing_aid::cleanup();
    return;
  }
  aidl::hearing_aid::cleanup();
}

// Send command to the BluetoothAudio HAL: StartSession, EndSession
void start_session() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::hearing_aid::start_session();
    return;
  }
  aidl::hearing_aid::start_session();
}

void end_session() {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::hearing_aid::end_session();
    return;
  }
  aidl::hearing_aid::end_session();
}

void set_remote_delay(uint16_t delay_report_ms) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    hidl::hearing_aid::set_remote_delay(delay_report_ms);
    return;
  }
  aidl::hearing_aid::set_remote_delay(delay_report_ms);
}

// Read from the FMQ of BluetoothAudio HAL
size_t read(uint8_t* p_buf, uint32_t len) {
  if (HalVersionManager::GetHalTransport() ==
      BluetoothAudioHalTransport::HIDL) {
    return hidl::hearing_aid::read(p_buf, len);
  }
  return aidl::hearing_aid::read(p_buf, len);
}

}  // namespace hearing_aid
}  // namespace audio
}  // namespace bluetooth
