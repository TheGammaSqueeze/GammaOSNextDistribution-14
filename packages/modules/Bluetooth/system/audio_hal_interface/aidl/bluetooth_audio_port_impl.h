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

#include "audio_aidl_interfaces.h"
#include "transport_instance.h"

namespace bluetooth {
namespace audio {
namespace aidl {

using ::aidl::android::hardware::audio::common::SinkMetadata;
using ::aidl::android::hardware::audio::common::SourceMetadata;
using ::aidl::android::hardware::bluetooth::audio::BnBluetoothAudioPort;
using ::aidl::android::hardware::bluetooth::audio::CodecType;
using ::aidl::android::hardware::bluetooth::audio::IBluetoothAudioProvider;
using ::aidl::android::hardware::bluetooth::audio::LatencyMode;
using ::aidl::android::hardware::bluetooth::audio::PresentationPosition;

class BluetoothAudioPortImpl : public BnBluetoothAudioPort {
 public:
  BluetoothAudioPortImpl(
      IBluetoothTransportInstance* transport_instance,
      const std::shared_ptr<IBluetoothAudioProvider>& provider);

  ndk::ScopedAStatus startStream(bool is_low_latency) override;

  ndk::ScopedAStatus suspendStream() override;

  ndk::ScopedAStatus stopStream() override;

  ndk::ScopedAStatus getPresentationPosition(
      PresentationPosition* _aidl_return) override;

  ndk::ScopedAStatus updateSourceMetadata(
      const SourceMetadata& source_metadata) override;

  ndk::ScopedAStatus updateSinkMetadata(
      const SinkMetadata& sink_metadata) override;

  ndk::ScopedAStatus setLatencyMode(LatencyMode latency_mode) override;

 protected:
  virtual ~BluetoothAudioPortImpl();

  IBluetoothTransportInstance* transport_instance_;
  const std::shared_ptr<IBluetoothAudioProvider> provider_;
  PresentationPosition::TimeSpec timespec_convert_to_hal(const timespec& ts);

 private:
  ndk::ScopedAStatus switchCodec(bool isLowLatency);
};

}  // namespace aidl
}  // namespace audio
}  // namespace bluetooth