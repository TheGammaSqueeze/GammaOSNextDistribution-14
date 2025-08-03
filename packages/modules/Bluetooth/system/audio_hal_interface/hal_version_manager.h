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

#pragma once

#include <android/hardware/bluetooth/audio/2.1/IBluetoothAudioProvidersFactory.h>
#include <android/hardware/bluetooth/audio/2.1/types.h>

namespace bluetooth {
namespace audio {

using ::android::hardware::hidl_vec;

using IBluetoothAudioProvidersFactory_2_0 = ::android::hardware::bluetooth::
    audio::V2_0::IBluetoothAudioProvidersFactory;
using IBluetoothAudioProvidersFactory_2_1 = ::android::hardware::bluetooth::
    audio::V2_1::IBluetoothAudioProvidersFactory;

constexpr char kFullyQualifiedInterfaceName_2_0[] =
    "android.hardware.bluetooth.audio@2.0::IBluetoothAudioProvidersFactory";
constexpr char kFullyQualifiedInterfaceName_2_1[] =
    "android.hardware.bluetooth.audio@2.1::IBluetoothAudioProvidersFactory";

/**
 * The type of HAL transport, it's important to have
 * BluetoothAudioHalTransport::HIDL value defined smaller than
 * BluetoothAudioHalTransport::AIDL.
 */
enum class BluetoothAudioHalTransport : uint8_t {
  // Uninit, default value
  UNKNOWN,
  HIDL,
  AIDL,
};

std::string toString(BluetoothAudioHalTransport transport);

/**
 * A hal version class with built-in comparison operators.
 */
class BluetoothAudioHalVersion {
 public:
  BluetoothAudioHalVersion(BluetoothAudioHalTransport transport =
                               BluetoothAudioHalTransport::UNKNOWN,
                           uint16_t major = 0, uint16_t minor = 0)
      : mTransport(transport), mMajor(major), mMinor(minor) {}

  bool isHIDL() const { return mTransport == BluetoothAudioHalTransport::HIDL; }
  bool isAIDL() const { return mTransport == BluetoothAudioHalTransport::AIDL; }

  BluetoothAudioHalTransport getTransport() const { return mTransport; }

  inline bool operator!=(const BluetoothAudioHalVersion& rhs) const {
    return std::tie(mTransport, mMajor, mMinor) !=
           std::tie(rhs.mTransport, rhs.mMajor, rhs.mMinor);
  }
  inline bool operator<(const BluetoothAudioHalVersion& rhs) const {
    return std::tie(mTransport, mMajor, mMinor) <
           std::tie(rhs.mTransport, rhs.mMajor, rhs.mMinor);
  }
  inline bool operator<=(const BluetoothAudioHalVersion& rhs) const {
    return std::tie(mTransport, mMajor, mMinor) <=
           std::tie(rhs.mTransport, rhs.mMajor, rhs.mMinor);
  }
  inline bool operator==(const BluetoothAudioHalVersion& rhs) const {
    return std::tie(mTransport, mMajor, mMinor) ==
           std::tie(rhs.mTransport, rhs.mMajor, rhs.mMinor);
  }
  inline bool operator>(const BluetoothAudioHalVersion& rhs) const {
    return std::tie(mTransport, mMajor, mMinor) >
           std::tie(rhs.mTransport, rhs.mMajor, rhs.mMinor);
  }
  inline bool operator>=(const BluetoothAudioHalVersion& rhs) const {
    return std::tie(mTransport, mMajor, mMinor) >=
           std::tie(rhs.mTransport, rhs.mMajor, rhs.mMinor);
  }

  inline std::string toString() const {
    std::ostringstream os;
    os << "BluetoothAudioHalVersion: {";
    os << "transport: " << bluetooth::audio::toString(mTransport);
    os << ", major: " << std::to_string(mMajor);
    os << ", minor: " << std::to_string(mMinor);
    os << "}";
    return os.str();
  }

  /* Known HalVersion definitions */
  static const BluetoothAudioHalVersion VERSION_UNAVAILABLE;
  static const BluetoothAudioHalVersion VERSION_2_0;
  static const BluetoothAudioHalVersion VERSION_2_1;
  static const BluetoothAudioHalVersion VERSION_AIDL_V1;
  static const BluetoothAudioHalVersion VERSION_AIDL_V2;
  static const BluetoothAudioHalVersion VERSION_AIDL_V3;
  static const BluetoothAudioHalVersion VERSION_AIDL_V4;

 private:
  BluetoothAudioHalTransport mTransport = BluetoothAudioHalTransport::UNKNOWN;
  uint16_t mMajor = 0;
  uint16_t mMinor = 0;
};

class HalVersionManager {
 public:
  static BluetoothAudioHalVersion GetHalVersion();

  static BluetoothAudioHalTransport GetHalTransport();

  static android::sp<IBluetoothAudioProvidersFactory_2_1>
  GetProvidersFactory_2_1();

  static android::sp<IBluetoothAudioProvidersFactory_2_0>
  GetProvidersFactory_2_0();

  HalVersionManager();

 private:
  static std::unique_ptr<HalVersionManager> instance_ptr;
  std::mutex mutex_;

  BluetoothAudioHalVersion hal_version_;
  BluetoothAudioHalTransport hal_transport_;
};

}  // namespace audio
}  // namespace bluetooth
